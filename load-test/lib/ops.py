"""Operation wrappers for Phase 2 load test.

Each op is an async function taking an authenticated AlteranteClient plus
operation-specific args, and returning an OpResult. OpResult has:

  op_type:   stable short name ('search', 'open_image', etc.) used as the
             key in metrics histograms
  ok:        True if the op met its success criteria (status 200, non-empty
             body where applicable, JSON parse ok, etc.)
  latency_ms: wall-clock duration
  status:    HTTP status of the last request (or -1 on exception)
  error:     short diagnostic string when ok=False; None otherwise
  bytes:     payload bytes transferred (useful for upload/download throughput)

Design notes
------------
- Ops are *thin* wrappers: they don't pick arguments from a corpus or sleep
  for think time. Callers (Stage 2b worker loop) do that. Keeping ops pure
  makes them easy to unit-test and easy to reuse in bench.py for manual
  poking.
- Every op catches its own exceptions and turns them into ok=False results.
  The metrics collector should never have to try/except around ops.
- `open_image` / `open_pdf` / `video_stream` differ only in params and
  headers. They're separate functions so per-op metrics stay meaningful.
"""

from __future__ import annotations

import os
import sys
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Optional

from .client import AlteranteClient


# ---------- failure debug log ----------
# When enabled (via set_fail_log_path or LOAD_TEST_FAIL_LOG env var), every
# failing op appends a timestamped line with full context (headers, args,
# body snippet). Lets us root-cause intermittent failures without polluting
# stdout during --silent-ops runs.
#
# Two activation modes:
#   1. set_fail_log_path(path) — called directly by run-phase2.py. Preferred.
#   2. LOAD_TEST_FAIL_LOG env var — fallback for ad-hoc use (e.g. bench.py).
_FAIL_LOG_PATH: Optional[str] = os.environ.get("LOAD_TEST_FAIL_LOG")
_FAIL_LOG_LOCK = threading.Lock()


def set_fail_log_path(path: Optional[str]) -> None:
    """Enable or disable failure logging to the given path. Pass None to
    disable. Takes precedence over the env var."""
    global _FAIL_LOG_PATH
    _FAIL_LOG_PATH = path

_LOG_FAILURE_CALL_COUNT = 0  # diagnostic: how many times _log_failure has been invoked
_LOG_FAILURE_SKIPPED = 0      # how many times skipped due to _FAIL_LOG_PATH being None


def log_failure_counters() -> tuple[int, int]:
    """Debug helper: returns (calls_made, calls_skipped_no_path)."""
    return _LOG_FAILURE_CALL_COUNT, _LOG_FAILURE_SKIPPED


def _log_failure(op_type: str, reason: str, *,
                 status: Optional[int] = None,
                 headers: Optional[dict] = None,
                 body_snippet: Optional[str] = None,
                 extra: Optional[dict] = None) -> None:
    global _LOG_FAILURE_CALL_COUNT, _LOG_FAILURE_SKIPPED
    _LOG_FAILURE_CALL_COUNT += 1
    if not _FAIL_LOG_PATH:
        _LOG_FAILURE_SKIPPED += 1
        # Print first skip to stderr so misconfiguration is visible
        if _LOG_FAILURE_SKIPPED == 1:
            print(f"[fail-log] WARN: _log_failure called for op={op_type} but "
                  f"_FAIL_LOG_PATH is None — suppressing further warnings",
                  file=sys.stderr)
        return
    ts = datetime.now(timezone.utc).strftime("%H:%M:%S.%f")[:-3]
    parts = [f"[{ts}] op={op_type} reason={reason!r}"]
    if status is not None:
        parts.append(f"status={status}")
    if extra:
        for k, v in extra.items():
            parts.append(f"{k}={v!r}")
    if headers:
        # Only keep headers likely to explain empty-body failures
        interesting = {
            k: v for k, v in headers.items()
            if k.lower() in ("content-length", "content-type", "content-range",
                             "content-encoding", "transfer-encoding",
                             "server", "connection")
        }
        parts.append(f"headers={interesting}")
    if body_snippet is not None:
        parts.append(f"body[:120]={body_snippet!r}")
    line = "  ".join(parts) + "\n"
    with _FAIL_LOG_LOCK:
        try:
            with open(_FAIL_LOG_PATH, "a") as f:
                f.write(line)
        except Exception as ex:
            # Never let logging break the op. Print once to stderr.
            print(f"[fail-log] write error: {ex}", file=sys.stderr)


@dataclass
class OpResult:
    op_type: str
    ok: bool
    latency_ms: float
    status: int = 0
    error: Optional[str] = None
    bytes: int = 0
    # HTTP method of the request that dominated this op's wall-clock time.
    # Separating GET vs POST in metrics matters for alt-core because the
    # server's SO_TIMEOUT-based POST read loop pins every POST at ~timeout
    # value regardless of real processing time. Aggregate p95 is misleading
    # when POST and GET are mixed. See CLAUDE.md "POST Request Read Loop"
    # for the underlying behavior.
    http_method: str = "GET"

    def __str__(self) -> str:
        state = "OK" if self.ok else "FAIL"
        base = f"{self.op_type:14s} {state:4s} {int(self.latency_ms):5d}ms"
        if self.status:
            base += f" http={self.status}"
        if self.bytes:
            base += f" bytes={self.bytes}"
        if self.error:
            base += f" err={self.error}"
        return base


# ---------- helpers ----------

def _timer_start() -> float:
    return time.monotonic()

def _elapsed_ms(start: float) -> float:
    return (time.monotonic() - start) * 1000.0

def _ok(op_type: str, start: float, *, status: int = 200, nbytes: int = 0,
        method: str = "GET") -> OpResult:
    return OpResult(op_type=op_type, ok=True, latency_ms=_elapsed_ms(start),
                    status=status, bytes=nbytes, http_method=method)

def _fail(op_type: str, start: float, err: str, *, status: int = 0,
          method: str = "GET") -> OpResult:
    return OpResult(op_type=op_type, ok=False, latency_ms=_elapsed_ms(start),
                    status=status, error=err, http_method=method)

def _safe_error(ex: Exception) -> str:
    s = f"{type(ex).__name__}: {ex}"
    return s if len(s) < 120 else s[:117] + "..."


# ---------- ops ----------

async def op_search(client: AlteranteClient, foo: str = ".all", numobj: int = 50) -> OpResult:
    """Search/list files. Matches uiv5's main Files-view query."""
    t0 = _timer_start()
    try:
        resp = await client.query(foo=foo, numobj=numobj)
    except Exception as ex:
        return _fail("search", t0, _safe_error(ex))
    if "fighters" not in resp:
        return _fail("search", t0, "missing fighters[]")
    return _ok("search", t0, nbytes=len(str(resp)))


async def op_browse_folders(client: AlteranteClient) -> OpResult:
    """Fetch folder tree sidebar data."""
    t0 = _timer_start()
    try:
        status, body, _ = await client.get("/cass/getfolders-json.fn")
    except Exception as ex:
        return _fail("browse_folders", t0, _safe_error(ex))
    if status != 200:
        return _fail("browse_folders", t0, f"http {status}", status=status)
    # Empty array is valid (no custom folders); just confirm JSON-ish
    if not body.startswith(b"[") and not body.startswith(b"{"):
        return _fail("browse_folders", t0, "non-json body", status=status)
    return _ok("browse_folders", t0, status=status, nbytes=len(body))


async def _open_file_full(client: AlteranteClient, op_type: str, md5: str,
                          filename: str, file_ext: str) -> OpResult:
    """Shared helper: GET full file via getfile.fn."""
    t0 = _timer_start()
    try:
        status, body, headers = await client.get(
            "/cass/getfile.fn",
            params={"sNamer": md5, "sFileExt": file_ext, "sFileName": filename},
        )
    except Exception as ex:
        _log_failure(op_type, _safe_error(ex), extra={"md5": md5, "filename": filename})
        return _fail(op_type, t0, _safe_error(ex))
    if status != 200:
        _log_failure(op_type, f"http {status}", status=status, headers=headers,
                     extra={"md5": md5, "filename": filename})
        return _fail(op_type, t0, f"http {status}", status=status)
    if len(body) == 0:
        # Empty 200 is the canonical failure signature for getfile.fn
        _log_failure(op_type, "empty body", status=status, headers=headers,
                     extra={"md5": md5, "filename": filename})
        return _fail(op_type, t0, "empty body", status=status)
    return _ok(op_type, t0, status=status, nbytes=len(body))


async def op_open_image(client: AlteranteClient, md5: str, filename: str, file_ext: str = "jpg") -> OpResult:
    return await _open_file_full(client, "open_image", md5, filename, file_ext)


async def op_open_pdf(client: AlteranteClient, md5: str, filename: str) -> OpResult:
    return await _open_file_full(client, "open_pdf", md5, filename, "pdf")


async def op_download(client: AlteranteClient, md5: str, filename: str, file_ext: str,
                      chunk_size: int = 1 * 1024 * 1024) -> OpResult:
    """Chunked download via filechunk_size/filechunk_offset. Issues one
    chunk request; callers drive multiple calls if they want to download
    a whole file. Measures per-chunk latency."""
    t0 = _timer_start()
    try:
        status, body, headers = await client.get(
            "/cass/getfile.fn",
            params={
                "sNamer": md5,
                "sFileExt": file_ext,
                "sFileName": filename,
                "filechunk_size": str(chunk_size),
                "filechunk_offset": "0",
            },
        )
    except Exception as ex:
        _log_failure("download", _safe_error(ex),
                     extra={"md5": md5, "filename": filename})
        return _fail("download", t0, _safe_error(ex))
    if status != 200:
        _log_failure("download", f"http {status}", status=status, headers=headers,
                     extra={"md5": md5, "filename": filename})
        return _fail("download", t0, f"http {status}", status=status)
    if len(body) == 0:
        _log_failure("download", "empty body", status=status, headers=headers,
                     extra={"md5": md5, "filename": filename})
        return _fail("download", t0, "empty body", status=status)
    return _ok("download", t0, status=status, nbytes=len(body))


async def op_video_stream(client: AlteranteClient, md5: str, filename: str, file_ext: str = "mp4",
                          n_segments: int = 3) -> OpResult:
    """Simulate a video player start via the real HLS flow used by uiv5:

      1) GET /cass/getvideo.m3u8?md5=<md5>&uuid=<uuid>
      2) Parse the manifest for .ts segment URLs (all begin with /cass/getts.fn)
      3) GET the first `n_segments` segments (3 ≈ first ~30s of playback)

    n_segments=3 roughly covers the initial buffer an HLS.js player would
    fetch before starting playback. Bigger numbers exercise sustained
    streaming; default keeps the op bounded so one virtual user doesn't
    dominate a load test ramp.

    Important: HLS endpoints authenticate via the `uuid=` URL parameter,
    NOT the session cookie. We bypass client.get() to avoid our default
    Cookie header overriding that. Latency measured is total for manifest
    + all n_segments, bytes is the sum.
    """
    t0 = _timer_start()
    base = client._cfg.base_url
    try:
        # --- 1) manifest ---
        manifest_url = f"{base}/cass/getvideo.m3u8"
        manifest_params = {"md5": md5, "uuid": client.uuid}
        async with client._session.get(manifest_url, params=manifest_params) as resp:
            if resp.status != 200:
                _log_failure("video_stream", f"manifest http {resp.status}",
                             status=resp.status, headers=dict(resp.headers),
                             extra={"md5": md5, "filename": filename})
                return _fail("video_stream", t0, f"manifest http {resp.status}",
                             status=resp.status)
            body = await resp.text()
            manifest_headers = dict(resp.headers)

        segment_paths = [
            ln.strip() for ln in body.splitlines()
            if ln.strip() and not ln.startswith("#")
        ]
        if not segment_paths:
            _log_failure("video_stream", "empty manifest (no .ts segments)",
                         status=200, headers=manifest_headers,
                         body_snippet=body[:120],
                         extra={"md5": md5, "filename": filename,
                                "manifest_len": len(body)})
            return _fail("video_stream", t0, "empty manifest (no .ts segments)",
                         status=200)

        # --- 2) fetch first N segments ---
        total_bytes = 0
        segs_to_fetch = segment_paths[:max(1, n_segments)]
        for seg_idx, seg_path in enumerate(segs_to_fetch):
            # Manifest segments are absolute paths (/cass/getts.fn?...); they
            # already include a uuid=, md5=, and ts=. We send them as-is.
            seg_url = f"{base}{seg_path}" if seg_path.startswith("/") else seg_path
            async with client._session.get(seg_url) as seg_resp:
                if seg_resp.status != 200:
                    _log_failure("video_stream", f"segment http {seg_resp.status}",
                                 status=seg_resp.status, headers=dict(seg_resp.headers),
                                 extra={"md5": md5, "filename": filename,
                                        "seg_idx": seg_idx, "seg_path": seg_path})
                    return _fail("video_stream", t0,
                                 f"segment http {seg_resp.status}",
                                 status=seg_resp.status)
                seg_body = await seg_resp.read()
                if len(seg_body) == 0:
                    _log_failure("video_stream", "empty segment body",
                                 status=200, headers=dict(seg_resp.headers),
                                 extra={"md5": md5, "filename": filename,
                                        "seg_idx": seg_idx, "seg_path": seg_path})
                    return _fail("video_stream", t0, "empty segment body",
                                 status=200)
                total_bytes += len(seg_body)

        return _ok("video_stream", t0, status=200, nbytes=total_bytes)
    except Exception as ex:
        _log_failure("video_stream", _safe_error(ex),
                     extra={"md5": md5, "filename": filename})
        return _fail("video_stream", t0, _safe_error(ex))


async def op_tag(client: AlteranteClient, md5: str, tag: str) -> OpResult:
    """Apply a tag to a file. Uses applytags.fn with the uiv5 param shape:
    `?tag=<name>&<md5>=on`. Server returns 'true' on success."""
    t0 = _timer_start()
    try:
        status, body, _ = await client.get(
            "/cass/applytags.fn",
            params={"tag": tag, md5: "on"},
        )
    except Exception as ex:
        return _fail("tag", t0, _safe_error(ex))
    if status != 200:
        return _fail("tag", t0, f"http {status}", status=status)
    # Response is the string "true" on success; anything else is suspect
    if not body.strip().startswith(b"true"):
        snippet = body[:40].decode("utf-8", "replace").strip()
        return _fail("tag", t0, f"unexpected body: {snippet!r}", status=status)
    return _ok("tag", t0, status=status, nbytes=len(body))


async def op_upload(client: AlteranteClient, filename: str, data: bytes) -> OpResult:
    """Upload a file through the 8081 → Netty .p-chunk relay.
    Only POST op in the load-test. The server's SO_TIMEOUT-based POST
    read loop pins every upload at ~timeout value — recorded so metrics
    can segregate GET vs POST (see OpResult.http_method)."""
    t0 = _timer_start()
    try:
        total, ok, last_status = await client.upload_file(filename, data)
    except Exception as ex:
        return _fail("upload", t0, _safe_error(ex), method="POST")
    if ok != total:
        return _fail("upload", t0, f"{ok}/{total} chunks ok",
                     status=last_status, method="POST")
    return _ok("upload", t0, status=last_status, nbytes=len(data), method="POST")


async def op_login(client: AlteranteClient, username: str, password: str) -> OpResult:
    """Session churn op: login → grab UUID. Paired with op_logout in
    scripted scenarios."""
    t0 = _timer_start()
    try:
        ok = await client.login(username, password)
    except Exception as ex:
        return _fail("login", t0, _safe_error(ex))
    if not ok:
        return _fail("login", t0, "no session uuid in response")
    return _ok("login", t0)


async def op_logout(client: AlteranteClient) -> OpResult:
    t0 = _timer_start()
    try:
        await client.logout()
    except Exception as ex:
        return _fail("logout", t0, _safe_error(ex))
    return _ok("logout", t0)


# ---------- corpus helpers ----------

VIDEO_EXTS = {"mp4", "mov", "m4v", "webm", "mkv", "avi", "wmv", "mpg", "mpeg"}


def _is_video(f: dict) -> bool:
    """Conservative check: extension must actually be a video extension.

    The server's own classifier groups `foo.mp4.zip` as `file_group='movie'`
    based on filename substring — it's a zip, not a video, and has no HLS
    streaming assets generated. Trusting group alone caused 100% failures
    on such files in the LAN 128u run. Extension is the authoritative check.
    """
    ext = (f.get("file_ext") or "").lower()
    return ext in VIDEO_EXTS


async def pick_file(client: AlteranteClient, ftype: str = ".all", numobj: int = 200) -> Optional[dict]:
    """Fetch files and return the first result, or None. Used by bench.py
    when no md5 is supplied on the CLI."""
    try:
        resp = await client.query(ftype=ftype, numobj=numobj)
    except Exception:
        return None
    files = resp.get("fighters") or []
    return files[0] if files else None


async def pick_video(client: AlteranteClient, numobj: int = 200) -> Optional[dict]:
    """Fetch files and return the first one the server classifies as a
    movie (or whose extension is on the known-video list). Returns None
    when the corpus has no videos."""
    try:
        resp = await client.query(numobj=numobj)
    except Exception:
        return None
    files = resp.get("fighters") or []
    for f in files:
        if _is_video(f):
            return f
    return None

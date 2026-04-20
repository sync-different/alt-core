"""Stage 2b: single virtual user worker loop.

A VirtualUser owns one aiohttp session and drives ops according to a mode:

  random-mix: loop forever, pick an op by weight, execute, sleep 5-20s
  scripted:   login → run scenario A/B/C → logout → sleep 5-20s → repeat

Both modes emit OpResult instances via an async queue (Stage 2c hooks into
that); for 2b we just print one line per op to stdout.

Design notes
------------
- Each worker has its OWN aiohttp session. Sharing a ClientSession across
  virtual users would serialize them behind aiohttp's connection pool and
  defeat the whole point of load testing. force_close=True keeps us
  immune to alt-core's HTTP/1.0 reply style (see lib/client.py notes).

- File-centric ops need a corpus of (md5, filename, ext) tuples. We do one
  search at worker startup to populate `self.file_cache`, then refresh
  when empty or every CORPUS_TTL seconds. This matches what a real user
  would see — they search once and then click around results.

- Regular load-test users see 0 files in query results because the index
  is admin-scoped (discovered in Stage 2a). For 2b each worker uses the
  admin UUID for file-scoped ops. The scripted modes' login/logout still
  use the per-worker user (to exercise login churn realistically), but
  file ops switch to admin UUID transparently. This is an acknowledged
  shortcut; proper fix is to share files to loadtest_* users in Phase 1.

- Think time: random 5-20s uniform. Logout→login interval in scripted
  mode: random 5-20s.

- The worker loop is cancellable at any point via asyncio.CancelledError
  (raised by the caller's timeout). We let it propagate rather than
  catching it, so the shutdown is clean.
"""

from __future__ import annotations

import asyncio
import random
import time
from dataclasses import dataclass, field
from typing import Awaitable, Callable, Optional

import aiohttp

from .client import AlteranteClient, ClientConfig, make_timeout
from . import ops
from .ops import OpResult


CORPUS_TTL_SECONDS = 300  # refresh file cache every 5 min per worker

# Extensions that are safe to send a Range request against. Used as a
# fallback when file_group isn't set to "movie". Kept inline so we don't
# depend on the server's own classifier agreeing with ours on every file.
VIDEO_EXTS = {"mp4", "mov", "m4v", "webm", "mkv", "avi", "wmv", "mpg", "mpeg"}

# Upload payload sizing for the random-.txt generator used by op_upload.
# Workers don't re-upload real corpus files — that would duplicate content
# and grow the indexed set arbitrarily. Instead we generate unique
# timestamped .txt payloads each call. Range covers "chat snippet" to
# "short doc" — enough to exercise the upload path without creating
# multi-MB files on every tick.
UPLOAD_TXT_MIN_BYTES = 200
UPLOAD_TXT_MAX_BYTES = 8_000


def _is_video(f: dict) -> bool:
    """Conservative check: extension must actually be a video extension.

    The server's own classifier groups `foo.mp4.zip` as `file_group='movie'`
    based on filename substring — it's a zip, not a video, and has no HLS
    streaming assets generated. Trusting group alone caused 100% failures
    on such files in the LAN 128u run. Extension is the authoritative check.
    """
    ext = (f.get("file_ext") or "").lower()
    return ext in VIDEO_EXTS

# Random-mix weights (spec table, normalized to sum=100)
RANDOM_WEIGHTS: dict[str, int] = {
    "search": 30,
    "browse_folders": 20,
    "open_image": 15,
    "download": 10,
    "open_pdf": 5,
    "tag": 5,
    "video_stream": 5,
    "upload": 5,
    "login": 5,   # "login/logout" paired — counted together in spec
}
# `login` in random-mix is interpreted as "session churn": we logout then
# login on the next tick with the same creds. That exercises auth without
# permanently unseating the worker from its session.

THINK_MIN = 5.0
THINK_MAX = 20.0


@dataclass
class WorkerConfig:
    base_url: str
    username: str
    password: str
    session_uuid: str          # fresh UUID or from sessions.json
    admin_uuid: str            # used for file-scoped ops (see module docstring)
    timeout_seconds: float = 30.0


@dataclass
class WorkerStats:
    ops_run: int = 0
    ops_ok: int = 0
    ops_failed: int = 0
    per_op_ok: dict[str, int] = field(default_factory=dict)
    per_op_fail: dict[str, int] = field(default_factory=dict)

    def record(self, r: OpResult) -> None:
        self.ops_run += 1
        if r.ok:
            self.ops_ok += 1
            self.per_op_ok[r.op_type] = self.per_op_ok.get(r.op_type, 0) + 1
        else:
            self.ops_failed += 1
            self.per_op_fail[r.op_type] = self.per_op_fail.get(r.op_type, 0) + 1


def _generate_upload_payload() -> tuple[str, bytes]:
    """Fresh random-ish .txt payload for an upload op. Timestamp +
    random-ish noise guarantees a unique filename and content so the
    server actually indexes it. Extension is `.txt` because bin/dat
    aren't indexed by alt-core (confirmed 2026-04-19)."""
    now_ns = time.monotonic_ns()
    size = random.randint(UPLOAD_TXT_MIN_BYTES, UPLOAD_TXT_MAX_BYTES)
    # Content: random tokens separated by spaces — realistic-looking text
    # for the tokenizer rather than binary noise. Hyphens avoided because
    # the search tokenizer splits on them (see CLAUDE.md).
    words = ["alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta",
             "theta", "iota", "kappa", "lambda", "mu", "nu", "xi"]
    parts: list[str] = []
    total = 0
    while total < size:
        w = random.choice(words)
        parts.append(w)
        total += len(w) + 1
    body = " ".join(parts).encode("utf-8")[:size]
    filename = f"loadtest_{now_ns}_{random.randint(1000, 9999)}.txt"
    return filename, body


# ---------- shared corpus cache ----------
# Originally each VirtualUser kept its own cache. At 128 concurrent users,
# that meant 128 simultaneous admin queries at cache-expiry, plus every
# worker was vulnerable to its own "query returned empty" transient. The
# client failures.log for a 128u run showed ~75 false failures clustered
# in millisecond bursts — classic per-worker cache-race.
#
# Fix: one cache shared by all workers in the process. Protected by a
# single asyncio.Lock so only ONE worker triggers a refresh at a time;
# others wait and then read the freshly-populated cache.
_SHARED_CORPUS: list[dict] = []
_SHARED_CORPUS_TS: float = 0.0
_SHARED_CORPUS_LOCK: Optional[asyncio.Lock] = None  # initialized lazily (needs running event loop)


def _get_corpus_lock() -> asyncio.Lock:
    global _SHARED_CORPUS_LOCK
    if _SHARED_CORPUS_LOCK is None:
        _SHARED_CORPUS_LOCK = asyncio.Lock()
    return _SHARED_CORPUS_LOCK


def weighted_pick(weights: dict[str, int]) -> str:
    total = sum(weights.values())
    roll = random.uniform(0, total)
    acc = 0
    for k, w in weights.items():
        acc += w
        if roll <= acc:
            return k
    return next(iter(weights))  # unreachable, satisfies type checker


def pick_think_time() -> float:
    return random.uniform(THINK_MIN, THINK_MAX)


class VirtualUser:
    def __init__(
        self,
        cfg: WorkerConfig,
        on_result: Optional[Callable[[str, OpResult], None]] = None,
    ):
        self.cfg = cfg
        self.on_result = on_result or _default_printer(cfg.username)
        self.stats = WorkerStats()

    async def _ensure_corpus(self, client: AlteranteClient) -> bool:
        """Populate the SHARED process-wide file cache if empty/stale.

        Uses a single-flight lock so only one worker at a time refreshes
        the cache. Other workers wait and read the updated value. Prevents
        the stampede race that produced spurious "empty file corpus"
        failures in the 128u run.

        Returns True if the shared cache has at least one file after.
        """
        global _SHARED_CORPUS, _SHARED_CORPUS_TS
        now = time.monotonic()
        # Fast path: shared cache is fresh
        if _SHARED_CORPUS and (now - _SHARED_CORPUS_TS) < CORPUS_TTL_SECONDS:
            return True
        # Slow path: acquire lock, re-check (someone else may have refreshed
        # while we were waiting), then query if needed.
        async with _get_corpus_lock():
            now = time.monotonic()
            if _SHARED_CORPUS and (now - _SHARED_CORPUS_TS) < CORPUS_TTL_SECONDS:
                return True
            # Use admin UUID for the probe (regular users see nothing)
            save = client.uuid
            client.uuid = self.cfg.admin_uuid
            try:
                # Fetch a big corpus slice (500) + a separate MOVIE-typed
                # slice. Reason: during a run, the test's own .txt uploads
                # get auto-indexed via the mobilebackup scandir. At 128u for
                # 10+ min that's hundreds of .txt files pushed to the top of
                # the recency-sorted results, which can evict videos from a
                # small numobj=100 window and produce spurious
                # "no video files in corpus" failures for op_video_stream.
                all_files = (await client.query(numobj=500)).get("fighters") or []
                videos = (await client.query(ftype=".video", numobj=50)).get("fighters") or []
                # Merge, preserving uniqueness by md5
                merged: dict = {}
                for f in all_files + videos:
                    md5 = f.get("nickname")
                    if md5 and md5 not in merged:
                        merged[md5] = f
                files = list(merged.values())
            except Exception:
                files = []
            finally:
                client.uuid = save
            if files:
                _SHARED_CORPUS = files
                _SHARED_CORPUS_TS = now
            # If query returned empty AND existing cache is empty, return False;
            # if existing cache is non-empty, keep it (don't stomp with empty).
            return bool(_SHARED_CORPUS)

    def _pick_file(self) -> Optional[dict]:
        if not _SHARED_CORPUS:
            return None
        return random.choice(_SHARED_CORPUS)

    def _pick_video(self) -> Optional[dict]:
        """Pick a real video file from the shared cache. Returns None if
        there are none — caller decides whether that's a skip or failure."""
        vids = [f for f in _SHARED_CORPUS if _is_video(f)]
        if not vids:
            return None
        return random.choice(vids)

    async def _dispatch_random(self, client: AlteranteClient, op_name: str) -> OpResult:
        """Execute one randomly-picked op. File-scoped ops use admin UUID."""
        if op_name == "search":
            return await self._as_admin(client, lambda c: ops.op_search(c))
        if op_name == "browse_folders":
            return await self._as_admin(client, lambda c: ops.op_browse_folders(c))
        if op_name == "login":
            # "session churn": logout + fresh login with same creds
            await ops.op_logout(client)
            r = await ops.op_login(client, self.cfg.username, self.cfg.password)
            if r.ok:
                # update saved uuid so future calls keep working
                self.cfg.session_uuid = client.uuid or ""
            return r
        if op_name == "upload":
            # Generate a fresh unique .txt payload rather than re-uploading
            # real seed files (which would inflate the indexed set on every
            # run). The server indexes .txt, so the uploaded file will show
            # up in subsequent queries within ~30-45s.
            filename, data = _generate_upload_payload()
            return await self._as_admin(client, lambda c: ops.op_upload(c, filename, data))

        # Remaining ops require a file selection
        if not await self._ensure_corpus(client):
            from .ops import OpResult as _R, _log_failure
            reason = "empty file corpus"
            _log_failure(op_name, reason, extra={"stage": "ensure_corpus"})
            return _R(op_name, False, 0.0, error=reason)

        # video_stream needs a real video (spec intent: exercise Range on
        # a video file). If the corpus has no videos, fail with a clear
        # reason rather than Range-requesting a text file.
        if op_name == "video_stream":
            v = self._pick_video()
            if not v:
                from .ops import OpResult as _R, _log_failure
                reason = "no video files in corpus (need mp4/mov/etc.)"
                _log_failure("video_stream", reason, extra={"stage": "pick_video"})
                return _R("video_stream", False, 0.0, error=reason)
            return await self._as_admin(
                client,
                lambda c: ops.op_video_stream(c, v["nickname"], v["name"], v.get("file_ext", "mp4")),
            )

        f = self._pick_file()
        if not f:
            from .ops import OpResult as _R, _log_failure
            reason = "corpus empty on pick"
            _log_failure(op_name, reason, extra={"stage": "pick_file"})
            return _R(op_name, False, 0.0, error=reason)
        md5 = f["nickname"]
        name = f["name"]
        ext = f.get("file_ext", "bin")

        if op_name == "open_image":
            return await self._as_admin(client, lambda c: ops.op_open_image(c, md5, name, ext))
        if op_name == "open_pdf":
            return await self._as_admin(client, lambda c: ops.op_open_pdf(c, md5, name))
        if op_name == "download":
            return await self._as_admin(client, lambda c: ops.op_download(c, md5, name, ext))
        if op_name == "tag":
            tag = f"loadtest_{random.randint(0, 9)}"
            return await self._as_admin(client, lambda c: ops.op_tag(c, md5, tag))

        from .ops import OpResult as _R
        return _R(op_name, False, 0.0, error=f"unknown op {op_name}")

    async def _as_admin(
        self,
        client: AlteranteClient,
        fn: Callable[[AlteranteClient], Awaitable[OpResult]],
    ) -> OpResult:
        """Run fn with client.uuid temporarily set to the admin UUID."""
        save = client.uuid
        client.uuid = self.cfg.admin_uuid
        try:
            return await fn(client)
        finally:
            client.uuid = save

    # ---------- public entry points ----------

    async def run_random_mix(self, duration_seconds: float) -> WorkerStats:
        """Loop for duration_seconds, picking ops by weight."""
        deadline = time.monotonic() + duration_seconds
        connector = aiohttp.TCPConnector(limit_per_host=0, force_close=True)
        async with aiohttp.ClientSession(
            connector=connector,
            timeout=make_timeout(self.cfg.timeout_seconds),
            cookie_jar=aiohttp.DummyCookieJar(),  # see note in lib/client.py
        ) as session:
            client = AlteranteClient(
                ClientConfig(base_url=self.cfg.base_url, timeout_seconds=self.cfg.timeout_seconds),
                session,
            )
            client.username = self.cfg.username
            client.uuid = self.cfg.session_uuid

            while time.monotonic() < deadline:
                op_name = weighted_pick(RANDOM_WEIGHTS)
                r = await self._dispatch_random(client, op_name)
                self.stats.record(r)
                self.on_result(self.cfg.username, r)

                # sleep but clamp to remaining budget
                think = min(pick_think_time(), max(0.0, deadline - time.monotonic()))
                if think > 0:
                    await asyncio.sleep(think)

        return self.stats

    async def run_scripted(self, duration_seconds: float) -> WorkerStats:
        """Scripted mode: login → A/B/C → logout → delay → repeat."""
        deadline = time.monotonic() + duration_seconds
        connector = aiohttp.TCPConnector(limit_per_host=0, force_close=True)
        async with aiohttp.ClientSession(
            connector=connector,
            timeout=make_timeout(self.cfg.timeout_seconds),
            cookie_jar=aiohttp.DummyCookieJar(),  # see note in lib/client.py
        ) as session:
            client = AlteranteClient(
                ClientConfig(base_url=self.cfg.base_url, timeout_seconds=self.cfg.timeout_seconds),
                session,
            )

            while time.monotonic() < deadline:
                # login
                r_in = await ops.op_login(client, self.cfg.username, self.cfg.password)
                self.stats.record(r_in)
                self.on_result(self.cfg.username, r_in)
                if not r_in.ok:
                    await asyncio.sleep(min(pick_think_time(), max(0.0, deadline - time.monotonic())))
                    continue

                # scenario
                scenario = random.choice(("A", "B", "C"))
                for r in await self._run_scenario(client, scenario, deadline):
                    self.stats.record(r)
                    self.on_result(self.cfg.username, r)
                    if time.monotonic() >= deadline:
                        break

                # logout
                r_out = await ops.op_logout(client)
                self.stats.record(r_out)
                self.on_result(self.cfg.username, r_out)

                # delay between iterations
                delay = min(pick_think_time(), max(0.0, deadline - time.monotonic()))
                if delay > 0:
                    await asyncio.sleep(delay)

        return self.stats

    async def _run_scenario(self, client: AlteranteClient, scenario: str, deadline: float) -> list[OpResult]:
        """Execute one scenario. Returns the list of OpResults (already
        dispatched — caller only needs to record + emit)."""
        results: list[OpResult] = []

        async def run(name: str) -> OpResult:
            return await self._dispatch_random(client, name)

        async def think() -> None:
            t = min(pick_think_time(), max(0.0, deadline - time.monotonic()))
            if t > 0:
                await asyncio.sleep(t)

        if scenario == "A":
            # browsing
            for op in ("search", "browse_folders", "open_image", "tag"):
                results.append(await run(op))
                if time.monotonic() >= deadline:
                    return results
                await think()
        elif scenario == "B":
            # uploading
            for op in ("upload", "search", "tag", "open_image"):
                results.append(await run(op))
                if time.monotonic() >= deadline:
                    return results
                await think()
        elif scenario == "C":
            # downloading
            for op in ("search", "open_image", "open_pdf", "download"):
                results.append(await run(op))
                if time.monotonic() >= deadline:
                    return results
                await think()
        return results


def _default_printer(username: str) -> Callable[[str, OpResult], None]:
    """Pretty-print one op per line. Prefixed with [username]."""
    def _p(_who: str, r: OpResult) -> None:
        print(f"[{_who:>14s}] {r}")
    return _p

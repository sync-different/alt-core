# alt-core load test

Simulates concurrent users against a running Alterante server via the public
API. Goal: find the user count at which performance degrades or errors spike.

See [internal/SPEC_LOAD_TEST.md](../internal/SPEC_LOAD_TEST.md) for the
full specification.

## Status

- **Phase 1 (setup)**: implemented — admin login, seed check, user-pool creation.
- **Phase 2 (ramp load)**: implemented — 2a/2b/2c/2d all complete.
- **Phase 3 (HTML report)**: implemented — `summary.html` generated alongside `summary.md` and `ticks.csv`.

## Findings (2026-04-19 run)

### Capacity on localhost

Tested at `workers=10`, `timeout=500` (production defaults) on macOS dev machine, Apple M3 Max. **128 users sustained with 0% error rate.** We haven't probed the ceiling yet.

| Users | Err % | ops/s | GET p50 | GET p95 | POST p50 | Notes |
|------:|------:|------:|--------:|--------:|---------:|-------|
| 5 | 0.00 % | 0.5 | 15 ms | 70 ms | ~500 ms | Trivial load |
| 16 | 0.00 % | 1.3 | 20 ms | 60 ms | ~500 ms | Baseline clean |
| 32 | 0.00 % | 2.8 | 14 ms | 55 ms | ~520 ms | Fast |
| 64 | 0.00 % | 5.0 | 21 ms | 60 ms | ~530 ms | Clean with fixes |
| **128** | **0.00 %** | **10.0** | **15 ms** | **56 ms** | **~530 ms** | Prod-equivalent validated |

Scaling is linear from 5u→128u (roughly 0.08 ops/s per user, driven by 5-20s think time between ops).

**POST latency pinned to `timeout` (SO_TIMEOUT)**: every `upload` op records ~`timeout` ms wall time regardless of real upload cost, because the server's POST read loop waits SO_TIMEOUT for EOF. This is a server quirk, not a load-test artifact. See CLAUDE.md → "POST Request Read Loop — no Content-Length parsing" for the underlying cause.

### Server bugs discovered and fixed

The 128-user run initially showed 78% video_stream failure rate. Root-cause work surfaced **one real server bug**:

**HLS `getts.fn` tmp-file race (`WebServer.java` ~line 7114)**
- The `getts.fn` handler was the last remaining endpoint using the legacy "write segment to tmp file, then serve via printHeaders/sendFile" pattern. `getfile.fn` got a zero-copy fix on 2026-04-17; `getts.fn` did not.
- Under concurrent load the 6-digit random tmp-filename had a birthday-paradox collision probability; two threads could truncate-while-serving or delete-before-send on the same tmp path. The failure mode was HTTP 200 with 0-byte body.
- **Fix**: `targ = fh2; genFile = false;` — repoint `targ` at the `.ts` source file, skip the tmp-file write, let `sendFile()` kernel-sendfile directly from the source.
- **Validated**: eliminated the 78% video fail rate at 128u. 624 HLS requests (manifest + segments) in the validation run, zero failures.

### Client-side lessons learned (load-test framework)

- **aiohttp `DummyCookieJar` is required.** Default cookie jar overrides explicit Cookie headers — breaks admin UUID swapping for file-scoped ops.
- **`force_close=True` + `Connection: close`.** The server returns HTTP/1.0 responses without keep-alive, so aiohttp's pool reuse sees stale sockets. Force-close sidesteps "Connection reset by peer" on reused sockets.
- **`Expect: ""` header.** aiohttp sends `Expect: 100-continue` on large multipart bodies; the server doesn't respond to it, hangs the request.
- **Shared corpus cache with `asyncio.Lock` single-flight.** First-pass implementation gave each VirtualUser its own file cache. At 128u, the stampede of simultaneous "cache expired" queries produced 75 false-fail events in millisecond bursts. Fix: one module-level cache, refreshed under a single-flight lock.
- **HLS endpoints authenticate via URL param, not Cookie.** `getvideo.m3u8` and `getts.fn` ignore the session cookie — must pass `uuid=` in the URL. Opposite of every other endpoint.

### Server behavior worth knowing

- **Server's POST read loop has no Content-Length parsing.** It reads until EOF or SO_TIMEOUT. For a client that doesn't TCP-half-close after sending the body (aiohttp, browsers), the server always waits the full `timeout` value before exiting the read loop. Per-POST wall time ≈ `timeout` setting. At `timeout=500` this is ~500ms per upload; at `timeout=30000` this was ~30 seconds per upload.
- **Auto-subscribe to upload destination directory.** When a file is uploaded, `ProcessorService.addMobileBackupFolder()` rewrites `scan1.txt` to include `scrubber/mobilebackup/`. Subsequent uploads get auto-indexed. Intentional product behavior — noted here because it affects load-test reproducibility (corpus grows during test).
- **Scanner vs Worker contention at extreme load was NOT the bottleneck.** We initially hypothesized `workers=10` was too few; the data doesn't support it. The real issue was the tmp-file race, not thread pool exhaustion.

## Reports

Every ramp run writes to `reports/run-<ISO-ts>/`:

| File | What it is |
|------|-----------|
| `summary.md` | Human-readable summary (CLI, ramp plan, per-level table with GET/POST split, final snapshot with by-method + per-op tables) |
| `summary.html` | Same content + 5 time-series charts (users, ops/s, GET latency, POST latency, error %) and a per-op bar chart. Self-contained, opens in any browser. Chart.js loaded from CDN at view time. |
| `ticks.csv` | One row per 5s reporter sample — includes both aggregate and per-method (GET/POST) columns |
| `failures.log` | Per-failure diagnostic (op, reason, headers, md5, filename). Empty if run had no failures. |

### GET vs POST split

All latency metrics are split by HTTP method because the server's POST read loop pins every upload at SO_TIMEOUT (see CLAUDE.md → "POST Request Read Loop"). Aggregate p95 would mix real GET performance (~50ms) with timeout-pinned POST wall time (~500ms), producing misleading numbers.

- **GET latency = real server performance** (search, open_image, download, video_stream, tag, etc.)
- **POST latency = timeout-pinned wall time** (only `upload` — worth tracking as a separate knob)

Live reporter line format:
```
t= 170s users=  4 ops=  0.4/s GET p50=  12 p95=  76ms | POST p50= 523 p95= 523ms err= 0.0%
```

### Known limitations

- Aggregate-only time series per-op. Per-op trends across the ramp (e.g. "open_image slowed at level 5 but search stayed fine") aren't visible because `TickRecord` stores overall p50/p95 only, now split by GET/POST. Further per-op time-series is a potential future improvement.
- HTML needs network access at view time to load Chart.js from CDN. If offline viewability is needed, Chart.js (~200 KB minified) can be inlined.

## Install

Python 3.10+.

```bash
cd test-load
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Seed files

The default corpus is [../test-files/](../test-files/) at the repo root
— already contains mixed formats (JPG, PNG, PDF, DOCX, XLSX, PPTX, MP4,
MP3). No action needed for standard runs.

Override with `--test-files-dir <path>` on `load-test.py` if you want
to seed from a different directory.

**Note:** only file formats alt-core recognizes will be indexed. `.bin`
/ `.dat` / other raw-binary extensions are uploaded successfully but
**not indexed** — they won't appear in `query.fn` results and can't be
picked by read ops. Stick to real formats (jpg/png/pdf/docx/mp4/txt/etc.)
for anything you want exercised by Phase 2 read ops.

Uploads go through the web server port **8081** using the `.p`-chunk
multipart path, which the server detects and relays to the Netty upload
handler internally (same path uiv5 uses over HTTPS — see smoke test 6.13).
Files are chunked client-side to 512 KB pieces, so size is not a constraint.

### Runtime-generated upload payloads

During Phase 2, the `upload` op does **not** re-upload real corpus files
— it generates a fresh unique `.txt` per call (timestamped filename,
200-8000 bytes of realistic text tokens). This avoids duplicating real
content while still exercising the full upload → index → query → delete
pipeline.

Files are uploaded only if the server's indexed count is below
`max(20, users * 2)`.

## Phase 1: Setup

```bash
python load-test.py \
  --url http://localhost:8081 \
  --users 10 \
  --admin-pass valid \
  --seed-if-missing
```

What it does:

1. Logs in as admin.
2. If `--seed-if-missing` and index is sparse, uploads `test-files/*`.
3. Creates `loadtest_001` ... `loadtest_N` via `adduser.fn` (idempotent —
   already-existing users are reused).
4. Logs each user in, collects session UUIDs.
5. Writes `sessions.json` — consumed by Phase 2 later.

### Credentials

| Field    | Format                              |
|----------|-------------------------------------|
| Username | `loadtest_001` ... `loadtest_N`     |
| Password | `LoadTest!001` ... `LoadTest!N`     |
| Email    | `loadtest_001@test.invalid`         |

Zero-padding width is `max(3, len(str(N)))`.

### Exit codes

| Code | Meaning                             |
|------|-------------------------------------|
| 0    | All users created/existing, all logged in |
| 1    | Admin login failed                  |
| 2    | Seed required but `test-files/` empty/missing |
| 3    | Some users failed to create or log in |

## Cleanup

Deletes every `loadtest_*` user via `deluser.fn` (admin-gated, added
2026-04-19). By default reads `sessions.json`:

```bash
python cleanup.py --admin-pass valid
```

To clean up without `sessions.json` (e.g., after a cancelled run), probe a
range:

```bash
python cleanup.py --url http://localhost:8081 --admin-pass valid --scan --max 100
```

## sessions.json

Schema:

```json
{
  "created_at": "2026-04-19T14:30:00+00:00",
  "base_url": "http://localhost:8081",
  "netty_url": "http://localhost:8087",
  "admin_uuid": "...",
  "users": [
    {"username": "loadtest_001", "password": "...", "email": "...",
     "uuid": "...", "login_ok": true, "create_status": "success"}
  ]
}
```

Gitignored. Safe to delete at any time (just re-run Phase 1).

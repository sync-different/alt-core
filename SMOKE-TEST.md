# Smoke Test — alt-core API Regression Suite

Automated regression suite for alt-core's HTTP API. Run after any code change to confirm nothing is broken. 12 phases, 168 tests, ~4 minutes end-to-end on DEV.

## Quick Start

```bash
# Prerequisites: server running, CLI built
./run.sh &                 # Start server (if not already running)
./build-cli.sh             # Build CLI (if not already built)

# Run all phases
./smoke-test.sh

# Run a single phase (faster for targeted testing)
./smoke-test-phase6.sh     # Just the upload/download tests
```

## Usage

```bash
# Run all 168 tests across 12 phases
./smoke-test.sh

# Individual phases (counts shown match what each script contains)
./smoke-test-phase1.sh       # Core API endpoints (26 tests)
./smoke-test-phase2.sh       # Security + uiv5 API (24 tests)
./smoke-test-phase3.sh       # Security hardening (15 tests)
./smoke-test-phase4.sh       # Upload security (10 tests)
./smoke-test-phase5.sh       # Fuzz testing (15 tests)
./smoke-test-phase6.sh       # Upload/download functional (14 tests)
./smoke-test-phase7.sh       # Index lifecycle (12 tests)
./smoke-test-phase8.sh       # Upload content & policy (12 tests)
./smoke-test-phase9.sh       # Session & auth lifecycle (14 tests)
./smoke-test-phase10.sh      # HTTP protocol hardening (13 tests)
./smoke-test-phase11.sh      # DoS resistance (7 tests)
./smoke-test-phase12.sh      # Scanner regression corpus (10 tests)

# Flags (work with all scripts)
./smoke-test-phase1.sh --verbose    # Show response body snippets
./smoke-test-phase1.sh --no-color   # Plain output (for CI/logs)

# REMOTE mode — target a non-local server
SMOKE_URL=https://alt.example.com SMOKE_USER=admin SMOKE_PASS='...' ./smoke-test.sh

# Skip the test that locks out the IP for 5 minutes
PHASE9_SKIP_RATELIMIT=1 ./smoke-test.sh
```

**Phase 9 runs last.** Test 9.14 exercises the login rate limiter which per-IP-locks the host for 5 minutes after N failed attempts. Running Phase 9 last means earlier phases get fresh auth; restart the server after the suite completes to clear the lockout.

## Phase Inventory

| Phase | Tests | Scope | Runtime |
|------:|------:|-------|---------|
| 1 | 26 | Core API endpoints — auth, search, files, tags, folders, chat, NPE regression | ~5s |
| 2 | 24 | Security gates + uiv5 API — sharing, transcript, folder traversal, UUID probing | ~15s |
| 3 | 15 | Security hardening — shutdown, config, file oracle, info disclosure, Base64 path traversal | ~5s |
| 4 | 10 | Upload security — path traversal, null bytes, empty filenames, dotfiles, chunked metadata | ~3s |
| 5 | 15 | Fuzz / malformed inputs — oversized params, malformed JSON, XSS, null bytes, unicode | ~5s |
| 6 | 14 | Upload/download functional — 3 upload paths, content integrity, chunked download, X-Chunk-MD5 | ~2 min |
| 7 | 12 | Index lifecycle — upload → index → query → delete | ~2 min |
| 8 | 12 | Upload content & policy validation — auth gates, scanner payloads, extension tricks, header injection | ~90s |
| 9 | 14 | Session & authentication lifecycle — UUID entropy, logout invalidation, cookie flags, rate limit | ~60s (+5min IP lockout) |
| 10 | 13 | HTTP protocol & response hardening — CRLF, CORS, smuggling, security headers | ~10s |
| 11 | 7 | DoS resistance — concurrent load, parser DoS, orphan cleanup, unbounded result sets | ~45s |
| 12 | 10 | Scanner regression corpus — replay real-world attack payloads | ~5s |
| **Total** | **168** | | **~4 min** |

Phase 11 shows 7 (not 8) because test 11.7 was removed 2026-04-17 after being superseded by 9.14.

## File Structure

| File | Purpose |
|------|---------|
| `smoke-test.sh` | Master runner — sequences phases 1-8, 10, 11, 12, 9 (Phase 9 runs last) |
| `smoke-common.sh` | Shared helpers: auth, env detection (DEV/PROD/REMOTE), WAF awareness, colored output |
| `smoke-test-phase<N>.sh` | One script per phase, runnable standalone |

## Environment Variables

Runtime is configured via env vars — nothing hardcoded beyond sensible defaults.

| Var | Default | Effect |
|-----|---------|--------|
| `SMOKE_URL` | `http://localhost:8081` | Target server. Non-localhost URL enables REMOTE mode |
| `SMOKE_USER` | `admin` | Admin username |
| `SMOKE_PASS` | `valid` | Admin password |
| `SMOKE_NONADMIN_USER` | `user1` | Non-admin user for test 9.12 (role-check) |
| `SMOKE_KNOWN_MD5` | — | Pin a specific file MD5 for test 11.5 instead of discovering via `query.fn` |
| `PHASE9_SKIP_RATELIMIT` | `0` | Skip test 9.14 so running the suite doesn't lock the IP for 5 min |
| `PHASE11_RATELIMIT` | `0` | Enable deprecated 11.7 (superseded by 9.14; left for history) |
| `SMOKE_MIN_SIZE` | `1048576` | Min file size (bytes) for Phase 11.5 test-file discovery |
| `SMOKE_MAX_SIZE` | `524288000` | Max file size for Phase 11.5 — avoids multi-GB files |
| `SMOKE_FORCE_PHASE` | `0` | Force-run phases 4/6/7/8 in REMOTE mode (disabled by default since they need local FS) |

Flags:

| Flag | Effect |
|------|--------|
| `--verbose` | Print extra debug on select tests (JSON response bodies on SKIP, etc.) |
| `--no-color` | Plain output (for CI/logs) |
| `--long` | Phase 11: doubles concurrency and orphan counts for stress testing |

## REMOTE / WAF Mode

When `SMOKE_URL` points to a non-localhost target, the suite enters REMOTE mode:

- **Phases 4, 6, 7, 8 are skipped by default** — they depend on local filesystem checks (incoming folder, direct port 8087 access). Force-run with `SMOKE_FORCE_PHASE=1` if the target is a LAN box you control.
- **Phase 5 becomes WAF-aware** — `check_fuzz_status()` treats HTTP 400/403/406 as pass. If Cloudflare/AWS WAF blocks a malicious payload before it reaches the origin, that's a safe outcome. LOCAL mode still requires HTTP 200.
- **Phase 12 payloads trigger WAF rules too** — those tests use HTTP-level safety checks (404 or no code execution) that work whether or not a WAF intercepted.

## Phase 6: Upload/Download Functional — detail

End-to-end round-trip verification across three upload paths:

| # | Test | What It Verifies |
|---|------|-----------------|
| P6-001 | Full upload HTTP 200 | Netty upload handler accepts file (single-chunk mode) |
| P6-002 | ProcessorService moves file | File arrives at `mobilebackup/upload/` destination |
| P6-003 | Content integrity at destination | Uploaded bytes match exactly |
| P6-004 | 3-chunk upload all HTTP 200 | Chunked upload metadata handled correctly |
| P6-005 | Chunk reassembly + delivery | ProcessorService merges .p files → .b → destination |
| P6-006 | Chunked content integrity | Merged chunks match original concatenated content |
| P6-007 | Full upload getfile.fn download | FileScanner indexes file, getfile.fn serves correct content |
| P6-008 | Chunked getfile.fn download | Reassembled file downloadable with correct content |
| P6-009 | Full upload chunked download | 2-chunk download via `filechunk_size`/`filechunk_offset`, X-Chunk-MD5 verified |
| P6-010 | Chunked upload chunked download | 3-chunk download via `filechunk_size`/`filechunk_offset`, X-Chunk-MD5 verified |
| P6-011 | uiv5-style upload (Netty 8087) | 3 chunks via form field name `upload.<name>.<total>.<idx>.p` |
| P6-012 | uiv5-style Netty reassembly | ProcessorService reassembles .p files, integrity verified |
| P6-013 | uiv5-style upload (8081→Netty) | 3 chunks via multipart POST to port 8081, forwarded to Netty via `connectToNetty()` |
| P6-014 | 8081→Netty reassembly | ProcessorService reassembles .p files, integrity verified |

**Timing:** Phase 6 polls for ProcessorService (~10-20s) and FileScanner (~30-45s). Total runtime: ~2-3 min.

**Three upload paths tested:**
- **Dropzone-style via Netty** (P6-001 to P6-008) — `dzchunkindex`/`dztotalchunkcount` metadata fields with `file` as the form field name. Netty chunked code path
- **uiv5-style direct to Netty** (P6-011/012) — form field name IS the chunk filename (`upload.<name>.<total>.<idx>.p`). Non-chunked path where `getName()` returns the field name — the React frontend's HTTP-mode upload
- **uiv5-style via 8081→Netty** (P6-013/014) — same uiv5 protocol, POSTed to 8081. `processPost()` detects `.p` and forwards to Netty. This is the HTTPS production path when a reverse proxy only exposes 8081

## Phase 7: Index Lifecycle — detail

Upload → verify indexed → delete → verify removed.

| # | Test | What It Verifies |
|---|------|-----------------|
| P7-001 | Full upload — getfile.fn serves content | Uploaded file downloadable with correct content |
| P7-002 | Full upload — getfileinfo.fn returns metadata | `getfileinfo.fn?md5=` returns file metadata from index |
| P7-003 | Full upload — query.fn returns MD5 | File MD5 appears in `query.fn` results |
| P7-004 | Chunked upload — getfile.fn serves content | Reassembled chunked file downloadable |
| P7-005 | Chunked upload — getfileinfo.fn returns metadata | Metadata present in index |
| P7-006 | Chunked upload — query.fn returns MD5 | Chunked file MD5 in search results |
| P7-007 | Full upload deletion — notification created | `.D_` notification placed in `incoming/` |
| P7-008 | Full upload deletion — getfile.fn stops serving | Download no longer serves deleted file |
| P7-009 | Full upload deletion — query.fn no longer returns | Search no longer returns deleted MD5 |
| P7-010 | Chunked upload deletion — notification created | `.D_` notification placed in `incoming/` |
| P7-011 | Chunked upload deletion — getfile.fn stops serving | Download no longer serves deleted file |
| P7-012 | Chunked upload deletion — query.fn no longer returns | Search no longer returns deleted MD5 |

**Requirements:** Uber JAR must be built (`scrubber/target/my-app-1.0-SNAPSHOT.jar`) for compiling the `CreateDeleteNotification` helper.

**Timing:** uploads + indexing ~60s, deletion ~10s. Total runtime: ~2-3 min.

## Phase 8–12: Security Hardening (added 2026-04)

Phases 8-12 were added 2026-04-12 through 2026-04-17 in response to a demo-server incident where automated scanners landed 22 exploit payloads over 48 hours. The tests pair with backend fixes landed in the same commits (upload auth gates, HttpOnly/SameSite cookies, X-Content-Type-Options, X-Frame-Options).

- **Phase 8** — Upload auth gates (AUDIT #2/#3), magic-byte / polyglot validation, extension tricks (`photo.jpg.exe`), header injection in filenames, scanner-payload regression
- **Phase 9** — UUID entropy, session invalidation on logout, cookie flags (HttpOnly, SameSite), query-param UUID precedence, concurrent sessions, privilege escalation, stale tokens, login rate limit (5 attempts / 5 min)
- **Phase 10** — CRLF / header injection, CORS origin validation, request smuggling, oversized headers, error-message info disclosure, fingerprinting surface, security response headers
- **Phase 11** — High concurrent requests (connection exhaustion), parser DoS (deep JSON, amplifying inputs), orphan chunk cleanup (disk-fill), result-set DoS. Phase 11.5 exposed a real server concurrency bug and led to nine WebServer performance fixes — see AUDIT.md
- **Phase 12** — Real-world scanner corpus: Next.js prototype-pollution (CVE-2025-29927 variant), VMware vSphere SOAP, Sonatype Nexus JEXL, Log4Shell, Spring4Shell, Struts OGNL, PHP / WordPress / cgi-bin probes. Payloads are inlined as heredocs — when a new payload shows up in logs, add a test here directly

## Verification Status

| Environment | Date | Result | Notes |
|-------------|------|--------|-------|
| DEV (localhost) | 2026-04-17 | 168/168 PASS | 12 phases, full suite, REMOTE-mode aware |
| DEV (localhost) | 2026-03-28 | 116/116 PASS | 7 phases, pre-security-expansion baseline |
| PROD (Application Support) | 2026-02-21 | 112/112 PASS | Phase 7 standalone: 12/12 in 1m 03s |

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | All tests passed |
| `1` | One or more tests failed |

## Requirements

- Server running on `localhost:8081` (or `SMOKE_URL`)
- Upload server on port `8087` (DEV only — skipped in REMOTE mode)
- `alt-core-cli` built (`alt-core-cli/target/alt-core-cli.jar`)
- Default credentials: `admin` / `valid`
- `curl`, `java`, `lsof`, `python3` in PATH

## When to Run

- After modifying `WebServer.java` or `HttpUploadServerHandler.java`
- After rebuilding the uber JAR
- Before committing bug fixes
- After merging branches
- When onboarding a new environment (REMOTE mode against a staging target)

## Typical Workflow

```bash
# 1. Make code changes
vim rtserver-maven/src/main/java/javaapplication1/WebServer.java

# 2. Build
./build-rt.sh          # Builds rtserver + uber JAR

# 3. Restart server
lsof -ti:8081 | xargs kill 2>/dev/null
./run.sh &
sleep 5

# 4. Smoke test (all phases)
./smoke-test.sh

# Or just the phase you care about
./smoke-test-phase6.sh
```

## Adding New Tests

Add tests to the appropriate phase script. Phase 12 is the right home for any new scanner/exploit payload observed in logs:

```bash
# Authenticated endpoint
RESP=$(curl_auth "$SERVER/cass/newendpoint.fn?view=json")
if echo "$RESP" | grep -q '"expectedKey"'; then
    pass "newendpoint.fn — description"
else
    fail "newendpoint.fn" "what went wrong"
fi
```

All phase scripts source `smoke-common.sh` which provides:
- `pass()`, `fail()`, `skip()` — result functions
- `curl_auth()`, `curl_noauth()` — authenticated/unauthenticated curl
- `cli()` — alt-core-cli wrapper
- `check_fuzz_status()` — WAF-aware HTTP status check for Phase 5
- `skip_phase_if_remote()` — skip FS-dependent phases in REMOTE mode
- `$UUID`, `$SERVER`, `$SCRIPT_DIR` — shared variables
- `print_summary()` — per-phase summary output

## Related Documentation

| File | Description |
|------|-------------|
| `INDEXING_PIPELINE.md` | Upload → index → query → download → delete pipeline reference |
| `internal/SMOKE-TEST-IMPLEMENTED.md` | Full per-test detail, all 168 tests across 12 phases |
| `internal/SMOKE-TEST-HISTORY.md` | How the suite grew from 116 → 168 tests, incident-driven timeline |
| `internal/SMOKE-TEST-PLAN.md` | Open coverage gaps (symlinks/TOCTOU, etc.) — candidate Phase 13 |
| `internal/AUDIT.md` | Security audit summary across all phases |
| `internal/AUDIT_ISSUES.md` | Open issues tracker with severity and fix status |

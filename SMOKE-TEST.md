# Smoke Test — alt-core API Regression Suite

Automated regression test for all core API endpoints. Run this after any code change to confirm nothing is broken.

## Quick Start

```bash
# Prerequisites: server running, CLI built
./run.sh &                 # Start server (if not already running)
./build-cli.sh             # Build CLI (if not already built)

# Run all tests
./smoke-test.sh

# Run a single phase (faster for targeted testing)
./smoke-test-phase6.sh     # Just the upload/download tests
```

## Usage

```bash
# Run all 112 tests across all 7 phases
./smoke-test.sh

# Run individual phases
./smoke-test-phase1.sh       # Core API endpoints (26 tests)
./smoke-test-phase2.sh       # Security + uiv5 API (24 tests)
./smoke-test-phase3.sh       # Security hardening (11 tests)
./smoke-test-phase4.sh       # Upload security (10 tests)
./smoke-test-phase5.sh       # Fuzz testing (15 tests)
./smoke-test-phase6.sh       # Upload/download functional (14 tests)
./smoke-test-phase7.sh       # Index tests (12 tests)

# Flags (work with all scripts)
./smoke-test-phase1.sh --verbose    # Show response body snippets
./smoke-test-phase1.sh --no-color   # Plain output (for CI/logs)
```

## File Structure

| File | Purpose |
|------|---------|
| `smoke-test.sh` | Master runner — executes all phases sequentially |
| `smoke-common.sh` | Shared infrastructure: helpers, colors, preflight checks, auth |
| `smoke-test-phase1.sh` | Phase 1: Core API endpoints |
| `smoke-test-phase2.sh` | Phase 2: Security + uiv5 API coverage |
| `smoke-test-phase3.sh` | Phase 3: Security hardening |
| `smoke-test-phase4.sh` | Phase 4: Upload security |
| `smoke-test-phase5.sh` | Phase 5: Fuzz testing |
| `smoke-test-phase6.sh` | Phase 6: Upload/download functional |
| `smoke-test-phase7.sh` | Phase 7: Index tests |

## Test Summary — 112 Tests

### Phase 1: Core API Endpoints (26 tests)
| Section | Tests | Description |
|---------|-------|-------------|
| Auth | 2 | Login, session |
| Search | 3 | query.fn, sidebar.fn, suggest.fn |
| Files | 3 | File info, path traversal, no-param crash |
| Tags | 2 | Tag list, XSS tag |
| Folders | 2 | Folder list, permissions |
| Chat | 2 | Pull, push |
| System | 4 | Node info, property, cluster, users |
| NPE checks | 7 | 7 endpoints without auth — must return 200 |
| Param parsing | 1 | Parameter collision regression |

### Phase 2: Security + uiv5 API Coverage (24 tests)
| Section | Tests | Description |
|---------|-------|-------------|
| Security | 11 | Transcript auth/traversal, config write, sharing noauth, folder traversal, UUID probe |
| uiv5 API | 13 | Chat clear, folder perms, user admin, sharing auth, ShareTypes crash |

### Phase 3: Security Hardening (11 tests)
Tests 11 admin-only endpoints to verify they block unauthenticated requests:
`shutdown.fn`, `setconfig.htm`, `fileexist.fn`, `getfolders.fn`, `getnodes.fn`, `getextensions.fn`, `getfileextensions.fn`, `getemailandgroups.fn`, `getinvitationmodal.fn`, `getsharesettingstag.fn`

### Phase 4: Upload Security (10 tests)
Tests upload endpoints on ports 8087 (Netty multipart) and 8081 (WebServer POST):
- Path traversal in filename and targetFolder
- Null bytes, empty filenames, dotfiles
- POST path traversal
- Newline injection in targetFolder
- Chunked upload metadata handling

### Phase 5: Fuzz Testing (15 tests)
Sends malformed, oversized, or unexpected input to verify the server doesn't crash:
- 2000-char search strings, 1000-char MD5s, 500-char tags, 10KB messages
- Empty params, malformed JSON, deeply nested JSON
- Negative/non-numeric values, null bytes, unicode/emoji
- Server alive check after all fuzz tests

### Phase 6: Upload/Download Functional (14 tests)
End-to-end upload-to-download round trip verification:

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
| P6-009 | Full upload chunked download | Download in 2 chunks via `filechunk_size`/`filechunk_offset`, reassemble, verify |
| P6-010 | Chunked upload chunked download | Download in 3 chunks via `filechunk_size`/`filechunk_offset`, reassemble, verify |
| P6-011 | uiv5-style upload (Netty 8087) | 3 chunks via form field name `upload.<name>.<total>.<idx>.p` — HTTP 200 |
| P6-012 | uiv5-style Netty reassembly | ProcessorService reassembles .p files, content integrity verified |
| P6-013 | uiv5-style upload (8081→Netty) | 3 chunks via multipart POST to port 8081, forwarded to Netty via `connectToNetty()` |
| P6-014 | 8081→Netty reassembly | ProcessorService reassembles .p files, content integrity verified |

**Timing notes:** Phase 6 tests poll for ProcessorService (~10-20s) and FileScanner (~30-45s) to process files. Total phase runtime: ~2-3 minutes.

**Key implementation details:**
- Chunk metadata fields (`dzchunkindex`, `dztotalchunkcount`) must be sent BEFORE the file field in multipart form data — Netty processes fields in order
- Single-chunk mode (`dztotalchunkcount=1`) uses the chunked code path which correctly uses `getFilename()` instead of `getName()`
- Download verification computes MD5 from the destination file, then polls `getfile.fn` until the content matches
- Chunked download uses the server's custom `filechunk_size`/`filechunk_offset` query parameters (same protocol as `alt-core-cli`)

**Three upload paths tested:**
- **Dropzone-style via Netty** (P6-001 to P6-008): Uses `dzchunkindex`/`dztotalchunkcount` metadata fields with `file` as the form field name. Netty takes the chunked code path
- **uiv5-style direct to Netty** (P6-011/012): Form field name IS the chunk filename (`upload.<name>.<total>.<idx>.p`). Netty takes the non-chunked path where `getName()` returns the field name — this is how the React frontend uploads in HTTP mode
- **uiv5-style via 8081→Netty** (P6-013/014): Same uiv5 multipart protocol, but POSTed to port 8081. `processPost()` detects the `.p` extension and forwards the entire HTTP request to Netty via `connectToNetty()` — this is the HTTPS production path where a reverse proxy only exposes port 8081

### Phase 7: Index Tests (12 tests)
Tests the full file lifecycle: upload → verify indexed → delete → verify removed. Confirms files appear in `getfile.fn`, `getfileinfo.fn`, and `query.fn` after indexing, and disappear from `getfile.fn` and `query.fn` after deletion. Phase 7 is fully self-contained.

**Part A — Verify files are in the index after upload:**

| # | Test | What It Verifies |
|---|------|-----------------|
| P7-001 | Full upload — getfile.fn serves content | Uploaded file is downloadable with correct content |
| P7-002 | Full upload — getfileinfo.fn returns metadata | `getfileinfo.fn?md5=` returns file metadata from index |
| P7-003 | Full upload — query.fn returns MD5 | File's MD5 appears in `query.fn` JSON results |
| P7-004 | Chunked upload — getfile.fn serves content | Reassembled chunked file is downloadable with correct content |
| P7-005 | Chunked upload — getfileinfo.fn returns metadata | `getfileinfo.fn?md5=` returns file metadata from index |
| P7-006 | Chunked upload — query.fn returns MD5 | Chunked file's MD5 appears in `query.fn` JSON results |

**Part B — Delete files and verify removal from index:**

| # | Test | What It Verifies |
|---|------|-----------------|
| P7-007 | Full upload deletion — notification created | `.D_` notification created and placed in `incoming/` |
| P7-008 | Full upload deletion — getfile.fn stops serving | `getfile.fn` no longer serves the deleted file |
| P7-009 | Full upload deletion — query.fn no longer returns | `query.fn` no longer returns deleted file's MD5 |
| P7-010 | Chunked upload deletion — notification created | `.D_` notification created and placed in `incoming/` |
| P7-011 | Chunked upload deletion — getfile.fn stops serving | `getfile.fn` no longer serves the deleted file |
| P7-012 | Chunked upload deletion — query.fn no longer returns | `query.fn` no longer returns deleted file's MD5 |

**Requirements:** Uber JAR must be built (`scrubber/target/my-app-1.0-SNAPSHOT.jar`) for compiling the `CreateDeleteNotification` helper.

**Timing notes:** Phase 7 uploads files and waits for indexing (~60s), then deletion is typically processed within ~10s. Total phase runtime: ~2-3 minutes.

## Verification Status

| Environment | Date | Result | Notes |
|-------------|------|--------|-------|
| DEV (localhost) | 2026-02-21 | 112/112 PASS | Full suite including Phase 7 index lifecycle |
| PROD (Application Support) | 2026-02-21 | 112/112 PASS | Phase 7 standalone: 12/12 in 1m 03s |

**Processor logs (both environments):** Zero errors across all cycles. Phase 7 files indexed (40 inserts, 24 hashes per upload pair) and deleted (2 deletions) cleanly.

**Error logs (both environments):** Only "Error log started" initialization messages — no real errors.

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | All tests passed |
| `1` | One or more tests failed |

## Requirements

- Server running on `localhost:8081`
- Upload server running on port `8087`
- `alt-core-cli` built (`alt-core-cli/target/alt-core-cli.jar`)
- Default credentials: `admin` / `valid`
- `curl`, `java`, `lsof`, `python3` available in PATH

## When to Run

- After modifying `WebServer.java` or `HttpUploadServerHandler.java`
- After rebuilding the uber JAR
- Before committing bug fixes
- After merging branches
- Anytime you want confidence the APIs are healthy

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

Add tests to the appropriate phase script (e.g., `smoke-test-phase2.sh`):

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
- `pass()`, `fail()`, `skip()` — test result functions
- `curl_auth()`, `curl_noauth()` — authenticated/unauthenticated curl
- `cli()` — alt-core-cli wrapper
- `$UUID`, `$SERVER`, `$SCRIPT_DIR` — shared variables
- `print_summary()` — per-phase summary output

## Related Documentation

| File | Description |
|------|-------------|
| `INDEXING_PIPELINE.md` | Complete technical reference for the upload → index → query → download → delete pipeline |
| `AUDIT.md` | Security audit summary covering all phases |
| `AUDIT_ISSUES.md` | Open issues tracker with severity and fix status |

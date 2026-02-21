#!/bin/bash
#
# SMOKE TEST — Phase 6: Upload/Download Functional Tests
#
# Tests: Full upload, ProcessorService, content integrity, chunked upload, reassembly,
#        download via getfile.fn, chunked download (filechunk_size/filechunk_offset),
#        uiv5-style upload via Netty (8087) and WebServer (8081)
# Total: 14 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 6: Upload/Download Functional Tests${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

printf "${BOLD}── Phase 6: Upload/Download Functional ──${RESET}\n"

UPLOAD_PORT=8087

if ! lsof -ti:$UPLOAD_PORT > /dev/null 2>&1; then
    skip "Upload/Download functional" "port $UPLOAD_PORT not running — skipping Phase 6 tests"
else

# Create unique test content per run
# Use a single-token ID (no hyphens) because the search engine tokenizes by hyphens
FUNC_TEST_ID="smktest$(date +%s)"
FUNC_TEST_DIR=$(mktemp -d)
FUNC_TEST_CONTENT="smoke-test-functional-${FUNC_TEST_ID}"
echo -n "$FUNC_TEST_CONTENT" > "$FUNC_TEST_DIR/fullupload-${FUNC_TEST_ID}.txt"

# Create chunk files for chunked upload test
echo -n "CHUNK_ONE_" > "$FUNC_TEST_DIR/chunk1.dat"
echo -n "CHUNK_TWO_" > "$FUNC_TEST_DIR/chunk2.dat"
echo -n "CHUNK_THREE" > "$FUNC_TEST_DIR/chunk3.dat"
CHUNKED_EXPECTED="CHUNK_ONE_CHUNK_TWO_CHUNK_THREE"

# INCOMING and MOBILEBACKUP are set by smoke-common.sh (auto-detects dev vs production)

# Ensure mobilebackup/upload directory exists
mkdir -p "$MOBILEBACKUP"

# --- P6-001: Full upload via port 8087 (single-chunk mode) ---
# Uses chunked path with dztotalchunkcount=1 so Netty uses getFilename() (correct name).
# Metadata fields MUST come before file field (Netty processes multipart in order).
test_start
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@$FUNC_TEST_DIR/fullupload-${FUNC_TEST_ID}.txt;filename=fullupload-${FUNC_TEST_ID}.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
if [ "$STATUS" = "200" ]; then
    pass "6.1 full upload — HTTP 200 via Netty"
else
    fail "6.1 full upload" "HTTP $STATUS (expected 200)"
fi

# --- P6-002: Wait for ProcessorService to move file to destination ---
test_start
FULL_DEST="$MOBILEBACKUP/fullupload-${FUNC_TEST_ID}.txt"
FULL_MOVED=false
for i in $(seq 1 45); do
    if [ -f "$FULL_DEST" ]; then
        FULL_MOVED=true
        break
    fi
    wait_msg "Waiting for ProcessorService to deliver full upload" "$i"
    sleep 1
done
wait_done
if $FULL_MOVED; then
    pass "6.2 full upload — ProcessorService delivered file ($i sec)"
else
    fail "6.2 full upload — ProcessorService" "file not at destination after 45s"
fi

# --- P6-003: Verify file content at destination matches upload ---
test_start
if $FULL_MOVED; then
    DEST_CONTENT=$(cat "$FULL_DEST" 2>/dev/null)
    if [ "$DEST_CONTENT" = "$FUNC_TEST_CONTENT" ]; then
        pass "6.3 full upload — content integrity verified (${#DEST_CONTENT} bytes)"
    else
        fail "6.3 full upload — content integrity" "expected ${#FUNC_TEST_CONTENT} bytes, got ${#DEST_CONTENT} bytes"
    fi
else
    fail "6.3 full upload — content integrity" "skipped (file not delivered)"
fi

# --- P6-004: Chunked upload (3 chunks) via port 8087 ---
# IMPORTANT: chunk metadata fields (-F dzchunkindex, dztotalchunkcount) MUST come
# BEFORE the file field because Netty processes multipart fields in order.
# The file handler checks formData for "dzchunkindex" to decide chunked vs non-chunked.
test_start
CHUNKED_OK=true
for CIDX in 0 1 2; do
    CFILE="$FUNC_TEST_DIR/chunk$((CIDX+1)).dat"
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
        -F "dzchunkindex=$CIDX" \
        -F "dztotalchunkcount=3" \
        -F "file=@${CFILE};filename=chunkedfile-${FUNC_TEST_ID}.txt" \
        "http://localhost:$UPLOAD_PORT/formpost" || true)
    if [ "$STATUS" != "200" ]; then
        CHUNKED_OK=false
    fi
done
if $CHUNKED_OK; then
    pass "6.4 chunked upload — all 3 chunks HTTP 200"
else
    fail "6.4 chunked upload" "one or more chunks failed"
fi

# --- P6-005: Wait for ProcessorService to reassemble and move chunked file ---
test_start
CHUNKED_DEST="$MOBILEBACKUP/chunkedfile-${FUNC_TEST_ID}.txt"
CHUNKED_MOVED=false
if $CHUNKED_OK; then
    # Chunked needs: ProcessorService merge .p→.b (sweep 1), then move .b (sweep 2)
    # Each sweep is ~10s, so worst case ~20s. Use 45s timeout for safety.
    for i in $(seq 1 45); do
        if [ -f "$CHUNKED_DEST" ]; then
            CHUNKED_MOVED=true
            break
        fi
        wait_msg "Waiting for ProcessorService to reassemble chunked upload" "$i"
        sleep 1
    done
    wait_done
fi
if $CHUNKED_MOVED; then
    pass "6.5 chunked upload — reassembled and delivered ($i sec)"
else
    fail "6.5 chunked upload — reassembly" "file not at destination after 45s"
fi

# --- P6-006: Verify reassembled file content matches concatenated chunks ---
test_start
if $CHUNKED_MOVED; then
    CHUNKED_CONTENT=$(cat "$CHUNKED_DEST" 2>/dev/null)
    if [ "$CHUNKED_CONTENT" = "$CHUNKED_EXPECTED" ]; then
        pass "6.6 chunked upload — content integrity verified (${#CHUNKED_CONTENT} bytes)"
    else
        fail "6.6 chunked upload — content integrity" "expected '$CHUNKED_EXPECTED', got '$(echo "$CHUNKED_CONTENT" | head -c 40)'"
    fi
else
    fail "6.6 chunked upload — content integrity" "skipped (file not delivered)"
fi

# --- P6-007 through P6-010: FileScanner indexing + download tests ---
# These tests require FileScanner to index the mobilebackup/upload directory.
# If the scan config doesn't include the mobilebackup path, skip these tests.
#
# NOTE: In production, the FileScanner cycle can take 3+ minutes because it scans
# backup-icloud/ and hivebot/ directories before reaching mobilebackup/.
# We use a 240-second timeout to accommodate this. If scan dirs are very large,
# the scanner may not complete a full cycle within the timeout.

INDEXING_TIMEOUT=240   # seconds to wait for FileScanner indexing (4 min)
FULL_DL_OK=false
CHUNKED_DL_OK=false

if ! $MOBILEBACKUP_SCANNABLE; then
    skip "6.7 full upload — getfile.fn download" "mobilebackup not in scan config"
    skip "6.8 chunked upload — getfile.fn download" "mobilebackup not in scan config"
    skip "6.9 full upload — chunked download" "mobilebackup not in scan config"
    skip "6.10 chunked upload — chunked download" "mobilebackup not in scan config"
else

# --- P6-007: Full upload — compute MD5, poll getfile.fn until indexed, verify content ---
# FileScanner indexes new files within ~30-45s. We compute the MD5 from the destination
# file, then poll getfile.fn until the download returns the correct content.
test_start
FULL_MD5=""
if $FULL_MOVED; then
    FULL_MD5=$(md5 -q "$FULL_DEST" 2>/dev/null || md5sum "$FULL_DEST" 2>/dev/null | cut -d' ' -f1)
    if [ -n "$FULL_MD5" ]; then
        for i in $(seq 1 $INDEXING_TIMEOUT); do
            DL_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$SERVER/cass/getfile.fn?sNamer=$FULL_MD5")
            if [ "$DL_RESP" = "$FUNC_TEST_CONTENT" ]; then
                FULL_DL_OK=true
                break
            fi
            wait_msg "Waiting for FileScanner to index full upload" "$i"
            sleep 1
        done
        wait_done
    fi
fi
if $FULL_DL_OK; then
    pass "6.7 full upload — getfile.fn download verified ($i sec, md5: ${FULL_MD5:0:8}...)"
else
    fail "6.7 full upload — getfile.fn download" "file not downloadable after ${INDEXING_TIMEOUT}s (md5: ${FULL_MD5:-unknown})"
fi

# --- P6-008: Chunked upload — getfile.fn serves correct content ---
# Compute MD5 from destination file, then poll getfile.fn until the download
# returns the correct content.
test_start
CHUNKED_MD5=""
if $CHUNKED_MOVED; then
    CHUNKED_MD5=$(md5 -q "$CHUNKED_DEST" 2>/dev/null || md5sum "$CHUNKED_DEST" 2>/dev/null | cut -d' ' -f1)
    if [ -n "$CHUNKED_MD5" ]; then
        for i in $(seq 1 $INDEXING_TIMEOUT); do
            DL_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$SERVER/cass/getfile.fn?sNamer=$CHUNKED_MD5")
            if [ "$DL_RESP" = "$CHUNKED_EXPECTED" ]; then
                CHUNKED_DL_OK=true
                break
            fi
            wait_msg "Waiting for FileScanner to index chunked upload" "$i"
            sleep 1
        done
        wait_done
    fi
fi
if $CHUNKED_DL_OK; then
    pass "6.8 chunked upload — getfile.fn download verified ($i sec, md5: ${CHUNKED_MD5:0:8}...)"
else
    fail "6.8 chunked upload — getfile.fn download" "file not downloadable after ${INDEXING_TIMEOUT}s (md5: ${CHUNKED_MD5:-unknown})"
fi

# --- P6-009: Chunked download — download full-uploaded file in 2 chunks, reassemble, verify ---
# Uses the server's custom filechunk_size/filechunk_offset query parameters (same as alt-core-cli).
# We split the file into 2 chunks: first half and second half.
test_start
FULL_CHUNK_DL_OK=false
if $FULL_DL_OK && [ -n "$FULL_MD5" ]; then
    FULL_FILE_SIZE=${#FUNC_TEST_CONTENT}
    CHUNK1_SIZE=$(( FULL_FILE_SIZE / 2 ))
    CHUNK2_SIZE=$(( FULL_FILE_SIZE - CHUNK1_SIZE ))

    CHUNK1_DATA=$(curl -s -H "Cookie: uuid=$UUID" \
        "$SERVER/cass/getfile.fn?sNamer=$FULL_MD5&filechunk_size=$CHUNK1_SIZE&filechunk_offset=0")
    CHUNK2_DATA=$(curl -s -H "Cookie: uuid=$UUID" \
        "$SERVER/cass/getfile.fn?sNamer=$FULL_MD5&filechunk_size=$CHUNK2_SIZE&filechunk_offset=$CHUNK1_SIZE")

    REASSEMBLED="${CHUNK1_DATA}${CHUNK2_DATA}"
    if [ "$REASSEMBLED" = "$FUNC_TEST_CONTENT" ]; then
        FULL_CHUNK_DL_OK=true
    fi
fi
if $FULL_CHUNK_DL_OK; then
    pass "6.9 full upload — chunked download (2 chunks) content verified"
else
    if ! $FULL_DL_OK; then
        fail "6.9 full upload — chunked download" "skipped (file not indexed)"
    else
        fail "6.9 full upload — chunked download" "reassembled content mismatch (got ${#REASSEMBLED:-0} bytes)"
    fi
fi

# --- P6-010: Chunked download — download chunked-uploaded file in 3 chunks, reassemble, verify ---
# Downloads the reassembled chunked file in 3 separate range requests, then verifies
# the concatenation matches the original CHUNK_ONE_CHUNK_TWO_CHUNK_THREE content.
test_start
CHUNKED_CHUNK_DL_OK=false
if $CHUNKED_DL_OK && [ -n "$CHUNKED_MD5" ]; then
    CHUNKED_FILE_SIZE=${#CHUNKED_EXPECTED}
    # Split into 3 chunks of ~equal size
    C1_SIZE=$(( CHUNKED_FILE_SIZE / 3 ))
    C2_SIZE=$(( CHUNKED_FILE_SIZE / 3 ))
    C3_SIZE=$(( CHUNKED_FILE_SIZE - C1_SIZE - C2_SIZE ))

    C1_DATA=$(curl -s -H "Cookie: uuid=$UUID" \
        "$SERVER/cass/getfile.fn?sNamer=$CHUNKED_MD5&filechunk_size=$C1_SIZE&filechunk_offset=0")
    C2_DATA=$(curl -s -H "Cookie: uuid=$UUID" \
        "$SERVER/cass/getfile.fn?sNamer=$CHUNKED_MD5&filechunk_size=$C2_SIZE&filechunk_offset=$C1_SIZE")
    C3_DATA=$(curl -s -H "Cookie: uuid=$UUID" \
        "$SERVER/cass/getfile.fn?sNamer=$CHUNKED_MD5&filechunk_size=$C3_SIZE&filechunk_offset=$(( C1_SIZE + C2_SIZE ))")

    REASSEMBLED_CHUNKED="${C1_DATA}${C2_DATA}${C3_DATA}"
    if [ "$REASSEMBLED_CHUNKED" = "$CHUNKED_EXPECTED" ]; then
        CHUNKED_CHUNK_DL_OK=true
    fi
fi
if $CHUNKED_CHUNK_DL_OK; then
    pass "6.10 chunked upload — chunked download (3 chunks) content verified"
else
    if ! $CHUNKED_DL_OK; then
        fail "6.10 chunked upload — chunked download" "skipped (file not indexed)"
    else
        fail "6.10 chunked upload — chunked download" "reassembled content mismatch (got ${#REASSEMBLED_CHUNKED:-0} bytes)"
    fi
fi

fi  # end MOBILEBACKUP_SCANNABLE check for P6-007..P6-010

# ── uiv5-style upload tests ──────────────────────────────────────────────────
# The uiv5 React frontend uses a DIFFERENT upload protocol than Dropzone:
#   - No dzchunkindex/dztotalchunkcount metadata fields
#   - Form field name IS the chunk filename: upload.<name>.<totalChunks>.<chunkIndex+1>.p
#   - Netty handler takes the non-chunked path, getName() returns the field name
#   - File lands in incoming/ as-is (e.g., upload.doc.txt.3.1.p)
#   - ProcessorService recognizes .p files and reassembles them
#
# For HTTPS, uiv5 POSTs to port 8081 (via reverse proxy) with the .p filename in the URL.
# WebServer.processPost() routes .p files directly to incoming/ (line 9511-9513).

# Create chunk content for uiv5-style tests
echo -n "UIV5_PART_A_" > "$FUNC_TEST_DIR/uiv5chunk1.dat"
echo -n "UIV5_PART_B_" > "$FUNC_TEST_DIR/uiv5chunk2.dat"
echo -n "UIV5_PART_C" > "$FUNC_TEST_DIR/uiv5chunk3.dat"
UIV5_EXPECTED="UIV5_PART_A_UIV5_PART_B_UIV5_PART_C"

# --- P6-011: uiv5-style chunked upload via Netty (port 8087) ---
# Mimics how the React frontend uploads: form field name = upload.<name>.<total>.<idx>.p
# No dzchunkindex metadata — Netty takes the non-chunked path, getName() returns field name.
test_start
UIV5_NETTY_FNAME="uiv5netty-${FUNC_TEST_ID}.txt"
UIV5_NETTY_OK=true
for CIDX in 1 2 3; do
    PARAM_NAME="upload.${UIV5_NETTY_FNAME}.3.${CIDX}.p"
    CFILE="$FUNC_TEST_DIR/uiv5chunk${CIDX}.dat"
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
        -F "${PARAM_NAME}=@${CFILE};filename=${PARAM_NAME}" \
        "http://localhost:$UPLOAD_PORT/formpost" || true)
    if [ "$STATUS" != "200" ]; then
        UIV5_NETTY_OK=false
    fi
done
if $UIV5_NETTY_OK; then
    pass "6.11 uiv5-style upload (Netty 8087) — all 3 chunks HTTP 200"
else
    fail "6.11 uiv5-style upload (Netty 8087)" "one or more chunks failed"
fi

# --- P6-012: uiv5-style Netty upload — ProcessorService reassembly + content verify ---
test_start
UIV5_NETTY_DEST="$MOBILEBACKUP/${UIV5_NETTY_FNAME}"
UIV5_NETTY_MOVED=false
if $UIV5_NETTY_OK; then
    for i in $(seq 1 45); do
        if [ -f "$UIV5_NETTY_DEST" ]; then
            UIV5_NETTY_MOVED=true
            break
        fi
        wait_msg "Waiting for ProcessorService to reassemble uiv5 Netty upload" "$i"
        sleep 1
    done
    wait_done
fi
if $UIV5_NETTY_MOVED; then
    UIV5_NETTY_CONTENT=$(cat "$UIV5_NETTY_DEST" 2>/dev/null)
    if [ "$UIV5_NETTY_CONTENT" = "$UIV5_EXPECTED" ]; then
        pass "6.12 uiv5-style upload (Netty 8087) — reassembled + content verified ($i sec)"
    else
        fail "6.12 uiv5-style upload (Netty 8087) — content" "expected '$UIV5_EXPECTED', got '$(echo "$UIV5_NETTY_CONTENT" | head -c 40)'"
    fi
else
    fail "6.12 uiv5-style upload (Netty 8087) — reassembly" "file not at destination after 45s"
fi

# --- P6-013: uiv5-style chunked upload via WebServer (port 8081) → Netty forwarding ---
# In HTTPS production mode, the reverse proxy forwards to port 8081 only.
# processPost() detects .p files and forwards the entire HTTP request to Netty
# via connectToNetty() (WebServer.java line 9542). Netty handles the multipart
# parsing and writes the .p file to incoming/. This tests that full round trip.
test_start
UIV5_WEB_FNAME="uiv5web-${FUNC_TEST_ID}.txt"
UIV5_WEB_OK=true
for CIDX in 1 2 3; do
    PARAM_NAME="upload.${UIV5_WEB_FNAME}.3.${CIDX}.p"
    CFILE="$FUNC_TEST_DIR/uiv5chunk${CIDX}.dat"
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
        -F "${PARAM_NAME}=@${CFILE};filename=${PARAM_NAME}" \
        "$SERVER/${PARAM_NAME}" || true)
    if [ "$STATUS" != "200" ]; then
        UIV5_WEB_OK=false
    fi
done
if $UIV5_WEB_OK; then
    pass "6.13 uiv5-style upload (8081→Netty) — all 3 chunks HTTP 200"
else
    fail "6.13 uiv5-style upload (8081→Netty)" "one or more chunks failed"
fi

# --- P6-014: uiv5-style 8081→Netty upload — ProcessorService reassembly + content verify ---
test_start
UIV5_WEB_DEST="$MOBILEBACKUP/${UIV5_WEB_FNAME}"
UIV5_WEB_MOVED=false
if $UIV5_WEB_OK; then
    for i in $(seq 1 45); do
        if [ -f "$UIV5_WEB_DEST" ]; then
            UIV5_WEB_MOVED=true
            break
        fi
        wait_msg "Waiting for ProcessorService to reassemble uiv5 WebServer upload" "$i"
        sleep 1
    done
    wait_done
fi
if $UIV5_WEB_MOVED; then
    UIV5_WEB_CONTENT=$(cat "$UIV5_WEB_DEST" 2>/dev/null)
    if [ "$UIV5_WEB_CONTENT" = "$UIV5_EXPECTED" ]; then
        pass "6.14 uiv5-style upload (8081→Netty) — reassembled + content verified ($i sec)"
    else
        fail "6.14 uiv5-style upload (8081→Netty) — content" "expected '$UIV5_EXPECTED', got '$(echo "$UIV5_WEB_CONTENT" | head -c 40)'"
    fi
else
    fail "6.14 uiv5-style upload (8081→Netty) — reassembly" "file not at destination after 45s"
fi

# Cleanup: remove test files from destination and incoming
rm -f "$MOBILEBACKUP/fullupload-${FUNC_TEST_ID}.txt" 2>/dev/null
rm -f "$MOBILEBACKUP/chunkedfile-${FUNC_TEST_ID}.txt" 2>/dev/null
rm -f "$MOBILEBACKUP/${UIV5_NETTY_FNAME}" 2>/dev/null
rm -f "$MOBILEBACKUP/${UIV5_WEB_FNAME}" 2>/dev/null
rm -f "$INCOMING"/upload.fullupload-${FUNC_TEST_ID}* 2>/dev/null
rm -f "$INCOMING"/upload.chunkedfile-${FUNC_TEST_ID}* 2>/dev/null
rm -f "$INCOMING"/upload.${UIV5_NETTY_FNAME}* 2>/dev/null
rm -f "$INCOMING"/upload.${UIV5_WEB_FNAME}* 2>/dev/null
rm -f "$INCOMING"/*${FUNC_TEST_ID}*.meta 2>/dev/null
rm -rf "$FUNC_TEST_DIR"

fi  # end upload port check for Phase 6

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 6"

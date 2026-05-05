#!/bin/bash
#
# SMOKE TEST — Phase 7: Index Tests
#
# Tests the full file lifecycle: upload → verify indexed → delete → verify removed.
# Confirms that files appear in both getfile.fn and getfileinfo.fn after indexing,
# and disappear from both after deletion.
#
# This phase is fully self-contained: it uploads its own files, waits for
# indexing, verifies presence, then deletes and verifies removal.
#
# Total: 12 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 7: Index Tests${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

skip_phase_if_remote "requires direct port 8087 access and local \$INCOMING/\$MOBILEBACKUP polling" "PHASE 7"

printf "${BOLD}── Phase 7: Index Tests ──${RESET}\n"

UPLOAD_PORT=8087
# INCOMING and MOBILEBACKUP are set by smoke-common.sh (auto-detects dev vs production)
UBER_JAR="$REPO_ROOT/scrubber/target/my-app-1.0-SNAPSHOT.jar"
DELETE_HELPER_SRC="$REPO_ROOT/test-files/CreateDeleteNotification.java"
DELETE_HELPER_CLASS="$REPO_ROOT/test-files"

if ! lsof -ti:$UPLOAD_PORT > /dev/null 2>&1; then
    skip "Index tests" "port $UPLOAD_PORT not running — skipping Phase 7 tests"
elif [ ! -f "$UBER_JAR" ]; then
    # Missing build artifact is a real problem, not an environment state — FAIL.
    fail "Phase 7 setup" "uber JAR not found at $UBER_JAR — run ./build-uber.sh"
    print_summary "PHASE 7"
    exit 1
elif [ ! -f "$DELETE_HELPER_SRC" ]; then
    fail "Phase 7 setup" "CreateDeleteNotification.java not found at $DELETE_HELPER_SRC — test repo incomplete"
    print_summary "PHASE 7"
    exit 1
elif ! $MOBILEBACKUP_SCANNABLE; then
    skip "Index tests" "mobilebackup not in scan config — skipping Phase 7 tests (legitimate env gap)"
else

# Compile the deletion helper if not already compiled
if [ ! -f "$DELETE_HELPER_CLASS/CreateDeleteNotification.class" ]; then
    COMPILE_ERR=$(javac -cp "$UBER_JAR" -d "$DELETE_HELPER_CLASS" "$DELETE_HELPER_SRC" 2>&1)
    if [ $? -ne 0 ]; then
        # Compilation failure is a real error — FAIL so it's visible.
        fail "Phase 7 setup" "failed to compile CreateDeleteNotification.java: $(echo "$COMPILE_ERR" | head -c 200)"
        print_summary "PHASE 7"
        exit 1
    fi
fi

# Ensure mobilebackup/upload directory exists
mkdir -p "$MOBILEBACKUP"

# Unique test ID per run (no hyphens — search engine tokenizes on hyphens)
IDX_TEST_ID="idxtest$(date +%s)"
IDX_TEST_DIR=$(mktemp -d)

# Create two test files: one for full upload, one for chunked
FULL_CONTENT="index-test-full-${IDX_TEST_ID}"
echo -n "$FULL_CONTENT" > "$IDX_TEST_DIR/idxfull-${IDX_TEST_ID}.txt"

CHUNKED_CONTENT="IDXCHUNK_A_IDXCHUNK_B_IDXCHUNK_C"
echo -n "IDXCHUNK_A_" > "$IDX_TEST_DIR/idxchunk1.dat"
echo -n "IDXCHUNK_B_" > "$IDX_TEST_DIR/idxchunk2.dat"
echo -n "IDXCHUNK_C" > "$IDX_TEST_DIR/idxchunk3.dat"

FULL_FNAME="idxfull-${IDX_TEST_ID}.txt"
CHUNKED_FNAME="idxchunked-${IDX_TEST_ID}.txt"

# ── Upload test files and wait for delivery ──────────────────────────────

printf "  ${CYAN}Uploading test files...${RESET}\n"

# Upload full file (single-chunk mode)
curl -s -o /dev/null --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@$IDX_TEST_DIR/idxfull-${IDX_TEST_ID}.txt;filename=${FULL_FNAME}" \
    "http://localhost:$UPLOAD_PORT/formpost" || true

# Upload chunked file (3 chunks)
for CIDX in 0 1 2; do
    curl -s -o /dev/null --max-time 5 \
        -H "Cookie: uuid=$UUID" \
        -F "dzchunkindex=$CIDX" \
        -F "dztotalchunkcount=3" \
        -F "file=@$IDX_TEST_DIR/idxchunk$((CIDX+1)).dat;filename=${CHUNKED_FNAME}" \
        "http://localhost:$UPLOAD_PORT/formpost" || true
done

# Wait for both files to be delivered by ProcessorService
FULL_DEST="$MOBILEBACKUP/${FULL_FNAME}"
CHUNKED_DEST="$MOBILEBACKUP/${CHUNKED_FNAME}"
FULL_READY=false
CHUNKED_READY=false

for i in $(seq 1 45); do
    [ -f "$FULL_DEST" ] && FULL_READY=true
    [ -f "$CHUNKED_DEST" ] && CHUNKED_READY=true
    $FULL_READY && $CHUNKED_READY && break
    wait_msg "Waiting for ProcessorService to deliver uploads" "$i"
    sleep 1
done
wait_done

if ! $FULL_READY || ! $CHUNKED_READY; then
    printf "  ${YELLOW}Warning: files not delivered after 45s — tests may fail${RESET}\n"
fi

# Compute MD5s for both files
FULL_MD5=""
CHUNKED_MD5=""
if $FULL_READY; then
    FULL_MD5=$(md5 -q "$FULL_DEST" 2>/dev/null || md5sum "$FULL_DEST" 2>/dev/null | cut -d' ' -f1)
fi
if $CHUNKED_READY; then
    CHUNKED_MD5=$(md5 -q "$CHUNKED_DEST" 2>/dev/null || md5sum "$CHUNKED_DEST" 2>/dev/null | cut -d' ' -f1)
fi

# Wait for FileScanner to index both files (poll getfile.fn)
# In production, the FileScanner cycle can take 3+ minutes (scans backup-icloud/
# and hivebot/ before reaching mobilebackup/). Use 240s timeout.
INDEXING_TIMEOUT=240
FULL_INDEXED=false
CHUNKED_INDEXED=false

if [ -n "$FULL_MD5" ]; then
    for i in $(seq 1 $INDEXING_TIMEOUT); do
        DL_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$SERVER/cass/getfile.fn?sNamer=$FULL_MD5")
        if [ "$DL_RESP" = "$FULL_CONTENT" ]; then
            FULL_INDEXED=true
            break
        fi
        wait_msg "Waiting for FileScanner to index full upload" "$i"
        sleep 1
    done
    wait_done
fi

if [ -n "$CHUNKED_MD5" ]; then
    for i in $(seq 1 $INDEXING_TIMEOUT); do
        DL_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$SERVER/cass/getfile.fn?sNamer=$CHUNKED_MD5")
        if [ "$DL_RESP" = "$CHUNKED_CONTENT" ]; then
            CHUNKED_INDEXED=true
            break
        fi
        wait_msg "Waiting for FileScanner to index chunked upload" "$i"
        sleep 1
    done
    wait_done
fi

printf "  ${CYAN}Setup complete — full:%s chunked:%s${RESET}\n" \
    "$(if $FULL_INDEXED; then echo "indexed"; else echo "NOT indexed"; fi)" \
    "$(if $CHUNKED_INDEXED; then echo "indexed"; else echo "NOT indexed"; fi)"

# ═════════════════════════════════════════════════════════════════════════
# PART A: Verify files ARE in the index after upload
# ═════════════════════════════════════════════════════════════════════════

# ── P7-001: Full upload — getfile.fn serves correct content ──────────────
test_start "7.1 full upload — getfile.fn serves correct content"

if $FULL_INDEXED; then
    ENDPOINT="$SERVER/cass/getfile.fn?sNamer=$FULL_MD5"
    printf "  ${DIM}→ GET $ENDPOINT${RESET}\n"
    DL_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$ENDPOINT")
    if [ "$DL_RESP" = "$FULL_CONTENT" ]; then
        pass "7.1 full upload — getfile.fn serves correct content"
    else
        fail "7.1 full upload — getfile.fn content" "content mismatch"
    fi
else
    fail "7.1 full upload — getfile.fn content" "file not indexed (md5: ${FULL_MD5:-unknown})"
fi

# ── P7-002: Full upload — getfileinfo.fn returns file metadata ────────────
test_start "7.2 full upload — getfileinfo.fn returns file in index"

if $FULL_INDEXED; then
    ENDPOINT="$SERVER/cass/getfileinfo.fn?md5=$FULL_MD5"
    printf "  ${DIM}→ GET $ENDPOINT${RESET}\n"
    INFO_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$ENDPOINT")
    if echo "$INFO_RESP" | grep -q "$FULL_MD5"; then
        pass "7.2 full upload — getfileinfo.fn returns file in index"
    else
        fail "7.2 full upload — getfileinfo.fn index" "MD5 $FULL_MD5 not found in getfileinfo.fn response"
    fi
else
    fail "7.2 full upload — getfileinfo.fn index" "file not indexed (md5: ${FULL_MD5:-unknown})"
fi

# ── P7-003: Full upload — query.fn returns MD5 in JSON array ──────────────
test_start "7.3 full upload — query.fn returns MD5 in results"

if $FULL_INDEXED; then
    ENDPOINT="$SERVER/cass/query.fn?view=json&ftype=.all&foo=$FULL_FNAME&days=0&numobj=10"
    printf "  ${DIM}→ GET $ENDPOINT${RESET}\n"
    FULL_QUERY_FOUND=false
    for i in $(seq 1 30); do
        QUERY_RESP=$(curl_auth "$ENDPOINT")
        if echo "$QUERY_RESP" | grep -q "$FULL_MD5"; then
            FULL_QUERY_FOUND=true
            break
        fi
        wait_msg "Waiting for query.fn to return full upload MD5" "$i"
        sleep 1
    done
    wait_done
    if $FULL_QUERY_FOUND; then
        pass "7.3 full upload — query.fn returns MD5 in results ($i sec)"
    else
        fail "7.3 full upload — query.fn index" "MD5 $FULL_MD5 not found in query.fn response"
    fi
else
    fail "7.3 full upload — query.fn index" "file not indexed (md5: ${FULL_MD5:-unknown})"
fi

# ── P7-004: Chunked upload — getfile.fn serves correct content ───────────
test_start "7.4 chunked upload — getfile.fn serves correct content"

if $CHUNKED_INDEXED; then
    ENDPOINT="$SERVER/cass/getfile.fn?sNamer=$CHUNKED_MD5"
    printf "  ${DIM}→ GET $ENDPOINT${RESET}\n"
    DL_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$ENDPOINT")
    if [ "$DL_RESP" = "$CHUNKED_CONTENT" ]; then
        pass "7.4 chunked upload — getfile.fn serves correct content"
    else
        fail "7.4 chunked upload — getfile.fn content" "content mismatch"
    fi
else
    fail "7.4 chunked upload — getfile.fn content" "file not indexed (md5: ${CHUNKED_MD5:-unknown})"
fi

# ── P7-005: Chunked upload — getfileinfo.fn returns file metadata ─────────
test_start "7.5 chunked upload — getfileinfo.fn returns file in index"

if $CHUNKED_INDEXED; then
    ENDPOINT="$SERVER/cass/getfileinfo.fn?md5=$CHUNKED_MD5"
    printf "  ${DIM}→ GET $ENDPOINT${RESET}\n"
    INFO_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$ENDPOINT")
    if echo "$INFO_RESP" | grep -q "$CHUNKED_MD5"; then
        pass "7.5 chunked upload — getfileinfo.fn returns file in index"
    else
        fail "7.5 chunked upload — getfileinfo.fn index" "MD5 $CHUNKED_MD5 not found in getfileinfo.fn response"
    fi
else
    fail "7.5 chunked upload — getfileinfo.fn index" "file not indexed (md5: ${CHUNKED_MD5:-unknown})"
fi

# ── P7-006: Chunked upload — query.fn returns MD5 in JSON array ──────────
test_start "7.6 chunked upload — query.fn returns MD5 in results"

if $CHUNKED_INDEXED; then
    ENDPOINT="$SERVER/cass/query.fn?view=json&ftype=.all&foo=$CHUNKED_FNAME&days=0&numobj=10"
    printf "  ${DIM}→ GET $ENDPOINT${RESET}\n"
    CHUNKED_QUERY_FOUND=false
    for i in $(seq 1 30); do
        QUERY_RESP=$(curl_auth "$ENDPOINT")
        if echo "$QUERY_RESP" | grep -q "$CHUNKED_MD5"; then
            CHUNKED_QUERY_FOUND=true
            break
        fi
        wait_msg "Waiting for query.fn to return chunked upload MD5" "$i"
        sleep 1
    done
    wait_done
    if $CHUNKED_QUERY_FOUND; then
        pass "7.6 chunked upload — query.fn returns MD5 in results ($i sec)"
    else
        fail "7.6 chunked upload — query.fn index" "MD5 $CHUNKED_MD5 not found in query.fn response"
    fi
else
    fail "7.6 chunked upload — query.fn index" "file not indexed (md5: ${CHUNKED_MD5:-unknown})"
fi

# ═════════════════════════════════════════════════════════════════════════
# PART B: Delete files and verify removal from index
# ═════════════════════════════════════════════════════════════════════════

# ── P7-007: Full upload deletion — .D_ notification created ──────────────
test_start "7.7 full upload deletion — .D_ notification created"

if $FULL_INDEXED; then
    FULL_PATH="$FULL_DEST"

    # Delete the physical file
    rm -f "$FULL_DEST"

    # Create .D_ notification
    DEL_NOTIF_FILE="$INCOMING/${UUID}.${FULL_MD5}.D_$(date +%s)000"
    java -cp "$UBER_JAR:$DELETE_HELPER_CLASS" CreateDeleteNotification \
        "$FULL_MD5" "$UUID" "$FULL_PATH" "$DEL_NOTIF_FILE" > /dev/null 2>&1

    pass "7.7 full upload deletion — .D_ notification created"
else
    fail "7.7 full upload deletion — setup" "file not indexed (md5: ${FULL_MD5:-unknown})"
fi

# ── P7-008: Full upload deletion — getfile.fn stops serving ──────────────
test_start "7.8 full upload deletion — getfile.fn no longer serves co"

if $FULL_INDEXED; then
    ENDPOINT="$SERVER/cass/getfile.fn?sNamer=$FULL_MD5"
    printf "  ${DIM}→ GET $ENDPOINT${RESET}\n"
    FULL_REMOVED=false
    for i in $(seq 1 60); do
        DL_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$ENDPOINT")
        if [ "$DL_RESP" != "$FULL_CONTENT" ]; then
            FULL_REMOVED=true
            break
        fi
        wait_msg "Waiting for getfile.fn to stop serving deleted full upload" "$i"
        sleep 1
    done
    wait_done
    if $FULL_REMOVED; then
        pass "7.8 full upload deletion — getfile.fn no longer serves content ($i sec)"
    else
        fail "7.8 full upload deletion — getfile.fn removal" "still serves file after 60s"
    fi
else
    fail "7.8 full upload deletion — getfile.fn removal" "skipped (file not indexed)"
fi

# ── P7-009: Full upload deletion — getfile.fn response differs from original ──
# After deletion, getfile.fn stops serving the original file content.  The response
# may be empty (len=0) or contain a short residual (e.g. 32-byte MD5 hash) before
# fully flushing.  This test verifies the response is NOT the original file content.
test_start "7.9 full upload deletion — getfile.fn response differs fr"

if $FULL_INDEXED && ${FULL_REMOVED:-false}; then
    ENDPOINT="$SERVER/cass/getfile.fn?sNamer=$FULL_MD5"
    printf "  ${DIM}→ GET $ENDPOINT${RESET}\n"
    DL_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$ENDPOINT")
    DL_LEN=${#DL_RESP}
    ORIG_LEN=${#FULL_CONTENT}
    if [ "$DL_RESP" != "$FULL_CONTENT" ]; then
        pass "7.9 full upload deletion — getfile.fn response differs from original (resp_len=$DL_LEN, orig_len=$ORIG_LEN)"
    else
        fail "7.9 full upload deletion — content check" "response still matches original content (len=$DL_LEN)"
    fi
else
    fail "7.9 full upload deletion — content check" "skipped (file not indexed or not removed from getfile)"
fi

# ── P7-010: Chunked upload deletion — .D_ notification created ───────────
test_start "7.10 chunked upload deletion — .D_ notification created"

if $CHUNKED_INDEXED; then
    CHUNKED_PATH="$CHUNKED_DEST"

    # Delete the physical file
    rm -f "$CHUNKED_DEST"

    # Create .D_ notification
    DEL_NOTIF_FILE="$INCOMING/${UUID}.${CHUNKED_MD5}.D_$(date +%s)000"
    java -cp "$UBER_JAR:$DELETE_HELPER_CLASS" CreateDeleteNotification \
        "$CHUNKED_MD5" "$UUID" "$CHUNKED_PATH" "$DEL_NOTIF_FILE" > /dev/null 2>&1

    pass "7.10 chunked upload deletion — .D_ notification created"
else
    fail "7.10 chunked upload deletion — setup" "file not indexed (md5: ${CHUNKED_MD5:-unknown})"
fi

# ── P7-011: Chunked upload deletion — getfile.fn stops serving ───────────
test_start "7.11 chunked upload deletion — getfile.fn no longer serve"

if $CHUNKED_INDEXED; then
    ENDPOINT="$SERVER/cass/getfile.fn?sNamer=$CHUNKED_MD5"
    printf "  ${DIM}→ GET $ENDPOINT${RESET}\n"
    CHUNKED_REMOVED=false
    for i in $(seq 1 60); do
        DL_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$ENDPOINT")
        if [ "$DL_RESP" != "$CHUNKED_CONTENT" ]; then
            CHUNKED_REMOVED=true
            break
        fi
        wait_msg "Waiting for getfile.fn to stop serving deleted chunked upload" "$i"
        sleep 1
    done
    wait_done
    if $CHUNKED_REMOVED; then
        pass "7.11 chunked upload deletion — getfile.fn no longer serves content ($i sec)"
    else
        fail "7.11 chunked upload deletion — getfile.fn removal" "still serves file after 60s"
    fi
else
    fail "7.11 chunked upload deletion — getfile.fn removal" "skipped (file not indexed)"
fi

# ── P7-012: Chunked upload deletion — getfile.fn response differs from original
# Same verification as 7.9 but for the chunked upload.
test_start "7.12 chunked upload deletion — getfile.fn response differ"

if $CHUNKED_INDEXED && ${CHUNKED_REMOVED:-false}; then
    ENDPOINT="$SERVER/cass/getfile.fn?sNamer=$CHUNKED_MD5"
    printf "  ${DIM}→ GET $ENDPOINT${RESET}\n"
    DL_RESP=$(curl -s -H "Cookie: uuid=$UUID" "$ENDPOINT")
    DL_LEN=${#DL_RESP}
    ORIG_LEN=${#CHUNKED_CONTENT}
    if [ "$DL_RESP" != "$CHUNKED_CONTENT" ]; then
        pass "7.12 chunked upload deletion — getfile.fn response differs from original (resp_len=$DL_LEN, orig_len=$ORIG_LEN)"
    else
        fail "7.12 chunked upload deletion — content check" "response still matches original content (len=$DL_LEN)"
    fi
else
    fail "7.12 chunked upload deletion — content check" "skipped (file not indexed or not removed from getfile)"
fi

# ── Cleanup ───────────────────────────────────────────────────────────────

# Remove any remaining test files
rm -f "$MOBILEBACKUP/${FULL_FNAME}" 2>/dev/null
rm -f "$MOBILEBACKUP/${CHUNKED_FNAME}" 2>/dev/null
rm -f "$INCOMING"/upload.${FULL_FNAME}* 2>/dev/null
rm -f "$INCOMING"/upload.${CHUNKED_FNAME}* 2>/dev/null
rm -f "$INCOMING"/*${IDX_TEST_ID}*.meta 2>/dev/null
rm -f "$INCOMING"/*.D_* 2>/dev/null
rm -rf "$IDX_TEST_DIR"

fi  # end prerequisites check

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 7"

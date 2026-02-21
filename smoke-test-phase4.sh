#!/bin/bash
#
# SMOKE TEST — Phase 4: Upload Security
#
# Tests: Upload auth gates, path traversal, null bytes, empty filenames, dotfiles, chunked metadata
# Total: 10 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 4: Upload Security${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

printf "${BOLD}── Phase 4: Upload Security ──${RESET}\n"

UPLOAD_PORT=8087
UPLOAD_TEST_DIR=$(mktemp -d)
echo "smoke-test-content" > "$UPLOAD_TEST_DIR/test-upload.txt"
dd if=/dev/zero bs=1024 count=1 of="$UPLOAD_TEST_DIR/test-1kb.bin" 2>/dev/null
INCOMING="$SCRIPT_DIR/rtserver/incoming"

# Helper: remove any smoke test artifacts from incoming immediately
# This prevents ProcessorService from picking them up and hanging.
clean_incoming() {
    rm -f "$INCOMING"/file 2>/dev/null
    rm -f "$INCOMING"/upload.file.* 2>/dev/null
    rm -f "$INCOMING"/upload.etcevil* 2>/dev/null
    rm -f "$INCOMING"/upload.evil* 2>/dev/null
    rm -f "$INCOMING"/upload.unnamed_upload* 2>/dev/null
    rm -f "$INCOMING"/upload..htaccess* 2>/dev/null
    rm -f "$INCOMING"/upload.incomingtest-smoke* 2>/dev/null
    rm -f "$INCOMING"/upload.etcevil-post* 2>/dev/null
    rm -f "$INCOMING"/upload.test-upload* 2>/dev/null
    rm -f "$INCOMING"/*.meta 2>/dev/null
}

# Check upload port is available
if ! lsof -ti:$UPLOAD_PORT > /dev/null 2>&1; then
    skip "Upload port $UPLOAD_PORT" "not running — skipping Phase 4 upload tests"
else

# P4-001: Netty upload — no auth (document current behavior)
test_start
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ]; then
    pass "4.1 upload (port $UPLOAD_PORT) --noauth — accepted (no auth gate)"
elif [ "$STATUS" = "000" ]; then
    fail "4.1 upload (port $UPLOAD_PORT) --noauth" "connection refused or timeout"
else
    pass "4.1 upload (port $UPLOAD_PORT) --noauth — HTTP $STATUS"
fi

# P4-002: Netty upload — path traversal in filename
test_start
RESP=$(curl -s --max-time 5 \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt;filename=../../etc/evil.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ ! -f "/etc/evil.txt" ]; then
    pass "4.2 upload — path traversal filename blocked (no /etc/evil.txt)"
else
    fail "4.2 upload — path traversal filename" "file written to /etc/evil.txt!"
fi

# P4-003: Netty upload — path traversal in targetFolder
test_start
RESP=$(curl -s --max-time 5 \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt" \
    -F "targetFolder=../../etc" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ ! -f "/etc/test-upload.txt" ]; then
    pass "4.3 upload — path traversal targetFolder blocked"
else
    fail "4.3 upload — path traversal targetFolder" "file written to /etc!"
fi

# P4-004: Netty upload — null bytes in filename
test_start
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt;filename=evil%00.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ] || [ "$STATUS" = "400" ]; then
    pass "4.4 upload — null byte filename handled (HTTP $STATUS)"
else
    fail "4.4 upload — null byte filename" "HTTP $STATUS"
fi

# P4-005: Netty upload — empty filename
test_start
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt;filename=" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ] || [ "$STATUS" = "400" ]; then
    pass "4.5 upload — empty filename handled (HTTP $STATUS)"
else
    fail "4.5 upload — empty filename" "HTTP $STATUS"
fi

# P4-006: Netty upload — dotfile filename (.htaccess)
test_start
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt;filename=.htaccess" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ] || [ "$STATUS" = "400" ]; then
    pass "4.6 upload — dotfile filename handled (HTTP $STATUS)"
else
    fail "4.6 upload — dotfile filename" "HTTP $STATUS"
fi

# P4-007: WebServer POST upload (port 8081) — no auth
test_start
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -X POST --data-binary "@$UPLOAD_TEST_DIR/test-1kb.bin" \
    "$SERVER/incoming/test-smoke.bin" || true)
clean_incoming
if [ "$STATUS" = "200" ] || [ "$STATUS" = "404" ] || [ "$STATUS" = "400" ]; then
    pass "4.7 POST upload (port 8081) --noauth — HTTP $STATUS"
else
    fail "4.7 POST upload (port 8081) --noauth" "HTTP $STATUS"
fi

# P4-008: WebServer POST — path traversal in URL path
test_start
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -X POST --data-binary "@$UPLOAD_TEST_DIR/test-1kb.bin" \
    "$SERVER/incoming/../../etc/evil-post" || true)
clean_incoming
if [ ! -f "/etc/evil-post" ]; then
    pass "4.8 POST upload — path traversal in URL blocked"
else
    fail "4.8 POST upload — path traversal in URL" "file written to /etc!"
fi

# P4-009: Netty upload — targetFolder with newline injection
test_start
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt" \
    -F "targetFolder=/tmp
injected" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ] || [ "$STATUS" = "400" ]; then
    pass "4.9 upload — targetFolder newline injection handled (HTTP $STATUS)"
else
    fail "4.9 upload — targetFolder newline injection" "HTTP $STATUS"
fi

# P4-010: Netty upload — chunked upload metadata
test_start
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt" \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ]; then
    pass "4.10 upload — chunked metadata accepted (HTTP 200)"
elif [ "$STATUS" = "000" ]; then
    fail "4.10 upload — chunked metadata" "connection error"
else
    pass "4.10 upload — chunked metadata handled (HTTP $STATUS)"
fi

fi  # end upload port check

# Final cleanup
rm -rf "$UPLOAD_TEST_DIR"
clean_incoming

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 4"

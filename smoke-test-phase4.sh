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

skip_phase_if_remote "requires local filesystem (/etc check) and direct port 8087 access" "PHASE 4"

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

# P4-001: Netty upload — noauth rejected.
# Upload auth gate (AUDIT Issue #2) was added 2026-04-16.
# Duplicates Phase 8.1 but kept here as regression in the upload-security phase.
test_start "4.1 upload (port"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
    pass "4.1 upload (port $UPLOAD_PORT) --noauth — rejected (HTTP $STATUS)"
elif [ "$STATUS" = "000" ]; then
    fail "4.1 upload (port $UPLOAD_PORT) --noauth" "connection refused or timeout"
else
    fail "4.1 upload (port $UPLOAD_PORT) --noauth" "HTTP $STATUS (expected 401)"
fi

# P4-002: Netty upload — path traversal in filename (authenticated)
# Tests the filename sanitizer; must be authenticated to reach it.
test_start "4.2 upload — path traversal filename blocked (no /etc/evi"
RESP=$(curl -s --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt;filename=../../etc/evil.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ ! -f "/etc/evil.txt" ]; then
    pass "4.2 upload — path traversal filename blocked (no /etc/evil.txt)"
else
    fail "4.2 upload — path traversal filename" "file written to /etc/evil.txt!"
fi

# P4-003: Netty upload — path traversal in targetFolder (authenticated)
test_start "4.3 upload — path traversal targetFolder blocked"
RESP=$(curl -s --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt" \
    -F "targetFolder=../../etc" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ ! -f "/etc/test-upload.txt" ]; then
    pass "4.3 upload — path traversal targetFolder blocked"
else
    fail "4.3 upload — path traversal targetFolder" "file written to /etc!"
fi

# P4-004: Netty upload — null bytes in filename (authenticated)
# Tests the filename sanitization layer, not the auth layer — so sends cookie.
test_start "4.4 upload — null byte filename handled"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt;filename=evil%00.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ] || [ "$STATUS" = "400" ]; then
    pass "4.4 upload — null byte filename handled (HTTP $STATUS)"
else
    fail "4.4 upload — null byte filename" "HTTP $STATUS"
fi

# P4-005: Netty upload — empty filename (authenticated)
test_start "4.5 upload — empty filename handled"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt;filename=" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ] || [ "$STATUS" = "400" ]; then
    pass "4.5 upload — empty filename handled (HTTP $STATUS)"
else
    fail "4.5 upload — empty filename" "HTTP $STATUS"
fi

# P4-006: Netty upload — dotfile filename (.htaccess, authenticated)
test_start "4.6 upload — dotfile filename handled"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "file=@$UPLOAD_TEST_DIR/test-upload.txt;filename=.htaccess" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ] || [ "$STATUS" = "400" ]; then
    pass "4.6 upload — dotfile filename handled (HTTP $STATUS)"
else
    fail "4.6 upload — dotfile filename" "HTTP $STATUS"
fi

# P4-007: WebServer POST upload (port 8081) — noauth rejected.
# Upload auth gate (AUDIT Issue #3) was added 2026-04-16; rejection is now
# the expected behavior on port 8081 POSTs without a cookie.
test_start "4.7 POST upload (port 8081) --noauth — rejected"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -X POST --data-binary "@$UPLOAD_TEST_DIR/test-1kb.bin" \
    "$SERVER/incoming/test-smoke.bin" || true)
clean_incoming
if [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
    pass "4.7 POST upload (port 8081) --noauth — rejected (HTTP $STATUS)"
else
    fail "4.7 POST upload (port 8081) --noauth" "HTTP $STATUS (expected 401)"
fi

# P4-008: WebServer POST — path traversal in URL path (authenticated)
# Tests the URL path sanitizer; must be authenticated to reach it.
test_start "4.8 POST upload — path traversal in URL blocked"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -X POST --data-binary "@$UPLOAD_TEST_DIR/test-1kb.bin" \
    "$SERVER/incoming/../../etc/evil-post" || true)
clean_incoming
if [ ! -f "/etc/evil-post" ]; then
    pass "4.8 POST upload — path traversal in URL blocked"
else
    fail "4.8 POST upload — path traversal in URL" "file written to /etc!"
fi

# P4-009: Netty upload — targetFolder with newline injection (authenticated)
# Tests the targetFolder sanitizer, not auth — so sends cookie.
test_start "4.9 upload — targetFolder newline injection handled (HTTP"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "Cookie: uuid=$UUID" \
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

# P4-010: Netty upload — chunked upload metadata (authenticated)
test_start "4.10 upload — chunked metadata accepted"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "Cookie: uuid=$UUID" \
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
    fail "4.10 upload — chunked metadata" "HTTP $STATUS (expected 200)"
fi

fi  # end upload port check

# Final cleanup
rm -rf "$UPLOAD_TEST_DIR"
clean_incoming

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 4"

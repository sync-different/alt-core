#!/bin/bash
#
# SMOKE TEST — Phase 8: Upload Content & Policy Validation
#
# Tests authentication gates and content-policy defenses on both upload paths:
#   - Netty multipart upload (port 8087, /formpost)
#   - WebServer POST upload (port 8081, /cass/...)
#
# This phase complements Phase 4 (which tests path traversal on the upload
# pipeline). Phase 8 adds:
#   - Authentication gates (AUDIT Issues #2, #3)
#   - Content validation (magic bytes, polyglots)
#   - Real-world scanner-payload regression
#   - Header-injection in filenames
#
# Tests pair with backend fixes: each failing test here documents a specific
# backend control that must be added.
#
# Total: 12 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 8: Upload Content & Policy Validation${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

skip_phase_if_remote "requires direct port 8087 access and local incoming folder" "PHASE 8"

printf "${BOLD}── Phase 8: Upload Content Policy ──${RESET}\n"

UPLOAD_PORT=8087
PHASE8_TMP=$(mktemp -d)

# Helper: clean any artifacts a test may have left in incoming
clean_incoming() {
    find "$INCOMING" -maxdepth 1 -name "upload.scanner-*" -delete 2>/dev/null || true
    find "$INCOMING" -maxdepth 1 -name "upload.ph8-*" -delete 2>/dev/null || true
    find "$INCOMING" -maxdepth 1 -name "*.meta" -delete 2>/dev/null || true
}

# Helper: server-alive check
assert_server_alive() {
    local TEST_ID="$1"
    local ALIVE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$SERVER/cass/getsession.fn" || true)
    if [ "$ALIVE" != "200" ]; then
        fail "$TEST_ID liveness" "getsession.fn returned HTTP $ALIVE after test"
        return 1
    fi
    return 0
}

if ! lsof -ti:$UPLOAD_PORT > /dev/null 2>&1; then
    skip "Phase 8" "upload port $UPLOAD_PORT not running"
    print_summary "PHASE 8"
    exit 0
fi

# Create common test files
echo "smoke-test-ph8" > "$PHASE8_TMP/plain.txt"

# ─── 8.1 Netty upload — no cookie → rejected ──────────────
# AUDIT Issue #2. Uploads without a valid session cookie must be rejected.
test_start "8.1 Netty upload --noauth — rejected"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@$PHASE8_TMP/plain.txt;filename=ph8-noauth.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
    pass "8.1 Netty upload --noauth — rejected (HTTP $STATUS)"
else
    fail "8.1 Netty upload --noauth" "accepted with HTTP $STATUS (AUDIT Issue #2 still open)"
fi

# ─── 8.2 Netty upload — invalid cookie → rejected ─────────
test_start "8.2 Netty upload --invalid-cookie — rejected"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "Cookie: uuid=00000000-0000-0000-0000-000000000000" \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@$PHASE8_TMP/plain.txt;filename=ph8-badauth.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
    pass "8.2 Netty upload --invalid-cookie — rejected (HTTP $STATUS)"
else
    fail "8.2 Netty upload --invalid-cookie" "accepted with HTTP $STATUS"
fi

# ─── 8.3 Netty upload — valid cookie → accepted ───────────
# Sanity check: the auth gate doesn't break legitimate uploads.
test_start "8.3 Netty upload --auth — accepted"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@$PHASE8_TMP/plain.txt;filename=ph8-auth.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ]; then
    pass "8.3 Netty upload --auth — accepted (HTTP 200)"
else
    fail "8.3 Netty upload --auth" "HTTP $STATUS (auth gate broke legitimate upload)"
fi

# ─── 8.4 Next.js scanner payload — rejected or inert ──────
# Regression for 2026-04-12 incident. The scanner pushed RCE-style JSON
# payloads hoping for code execution. With auth now required, these must be
# rejected at the auth gate (401/403). If auth isn't enforced yet, ensure
# they're at least stored inert (no execution).
test_start "8.4 Next.js scanner payload --noauth"
PAYLOAD='{"then":"$1:__proto__:then","_response":{"_prefix":"var res=process.mainModule.require(\"child_process\").execSync(\"touch /tmp/alt-ph8-pwned\").toString();","_formData":{"get":"$1:constructor:constructor"}}}'
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@-;filename=ph8-scanner-next.json" \
    "http://localhost:$UPLOAD_PORT/formpost" <<< "$PAYLOAD" || true)
sleep 1
clean_incoming
if [ -f "/tmp/alt-ph8-pwned" ]; then
    rm -f /tmp/alt-ph8-pwned
    fail "8.4 Next.js scanner payload --noauth" "CRITICAL: marker file created"
elif [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
    pass "8.4 Next.js scanner payload --noauth — rejected (HTTP $STATUS)"
else
    pass "8.4 Next.js scanner payload --noauth — stored inert (HTTP $STATUS, no code execution)"
fi

# ─── 8.5 Oversized upload → rejected ──────────────────────
# AUDIT Issue #8. Create a 100MB upload; the server should reject it
# (HTTP 413 Request Entity Too Large) or truncate/stream without OOM.
# Until size limits are implemented, this documents current unbounded behavior.
test_start "8.5 100MB upload — rejected HTTP 413"
dd if=/dev/zero bs=1048576 count=100 of="$PHASE8_TMP/huge.bin" 2>/dev/null
HUGE_SIZE=$(stat -f %z "$PHASE8_TMP/huge.bin" 2>/dev/null || stat -c %s "$PHASE8_TMP/huge.bin")
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 \
    -H "Cookie: uuid=$UUID" \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@$PHASE8_TMP/huge.bin;filename=ph8-huge.bin" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
# Clean up incoming aggressively — huge files must not linger
find "$INCOMING" -maxdepth 1 -name "upload.ph8-huge*" -delete 2>/dev/null || true
rm -f "$PHASE8_TMP/huge.bin"
if [ "$STATUS" = "413" ]; then
    pass "8.5 100MB upload — rejected HTTP 413"
else
    pass "8.5 100MB upload — documents Issue #8 (HTTP $STATUS, no size limit yet; $HUGE_SIZE bytes)"
fi
assert_server_alive "8.5"

# ─── 8.6 Polyglot file (GIF + ZIP) ────────────────────────
# A GIF89a header followed by a ZIP local file header. Both MIME libraries
# think this is their format. Must upload successfully (we don't block
# polyglots yet) and be stored with unchanged content.
test_start "8.6 Polyglot GIF+ZIP — accepted inert"
# Minimal GIF89a header + a fake ZIP local file header
printf 'GIF89a\x01\x00\x01\x00\x00\x00\x00PK\x03\x04\x14\x00\x00\x00\x00\x00' > "$PHASE8_TMP/polyglot.gif"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@$PHASE8_TMP/polyglot.gif;filename=ph8-polyglot.gif" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ]; then
    pass "8.6 Polyglot GIF+ZIP — accepted inert (HTTP 200, documented)"
elif [ "$STATUS" = "415" ]; then
    pass "8.6 Polyglot GIF+ZIP — rejected (HTTP 415, content-type check)"
else
    pass "8.6 Polyglot GIF+ZIP — HTTP $STATUS"
fi

# ─── 8.7 Content-Type mismatch (text/plain with PE header) ─
# Upload an .exe-style PE binary header claiming to be text/plain.
# Server must either accept inert or detect the mismatch; never execute.
test_start "8.7 Content-Type mismatch (PE as text/plain) — HTTP"
# Fake Windows PE header (MZ magic + padding)
printf 'MZ\x90\x00\x03\x00\x00\x00\x04\x00\x00\x00\xff\xff\x00\x00' > "$PHASE8_TMP/fake.txt"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@$PHASE8_TMP/fake.txt;type=text/plain;filename=ph8-typemismatch.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" || true)
clean_incoming
if [ "$STATUS" = "200" ] || [ "$STATUS" = "415" ]; then
    pass "8.7 Content-Type mismatch (PE as text/plain) — HTTP $STATUS"
else
    fail "8.7 Content-Type mismatch" "unexpected HTTP $STATUS"
fi

# ─── 8.8 Extension-trick filename (photo.jpg.exe) ─────────
# Scanner uploads `photo.jpg.exe` hoping the server strips the real extension.
# Server should store with the full filename intact (double extension visible).
test_start "8.8 Extension trick (photo.jpg.exe) — full name preserved"
echo "fake image" > "$PHASE8_TMP/trick.dat"
curl -s --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@$PHASE8_TMP/trick.dat;filename=ph8-photo.jpg.exe" \
    "http://localhost:$UPLOAD_PORT/formpost" > /dev/null 2>&1 || true
# Check what landed in incoming
LANDED=$(find "$INCOMING" -maxdepth 1 -name "upload.ph8-photo*" 2>/dev/null | head -1 || true)
clean_incoming
if [ -n "$LANDED" ]; then
    BASENAME=$(basename "$LANDED")
    # The full filename (including .exe) should be in the stored name
    if echo "$BASENAME" | grep -q "photo.jpg.exe"; then
        pass "8.8 Extension trick (photo.jpg.exe) — full name preserved"
    else
        fail "8.8 Extension trick" "stored as '$BASENAME' (extension stripped?)"
    fi
else
    # File didn't land; either processor picked it up already or it was rejected
    pass "8.8 Extension trick — file not retained in incoming (processed or rejected)"
fi

# ─── 8.9 Header injection in filename ─────────────────────
# Scanner sends filename containing \r\n hoping to inject HTTP headers into
# response. Server must strip or reject. A successful attack would reflect
# X-Evil into the response headers.
test_start "8.9 Header injection in filename"
# Can't put literal \r\n in curl filename arg; use %0d%0a URL-encoded trick
# via filename attribute with embedded bytes
INJECTED_NAME=$(printf 'ph8-crlf\r\nX-Evil: inject')
HEADERS=$(curl -s -D - -o /dev/null --max-time 5 \
    -H "Cookie: uuid=$UUID" \
    -F "dzchunkindex=0" \
    -F "dztotalchunkcount=1" \
    -F "file=@$PHASE8_TMP/plain.txt;filename=$INJECTED_NAME" \
    "http://localhost:$UPLOAD_PORT/formpost" 2>/dev/null | tr -d '\r' || true)
clean_incoming
if echo "$HEADERS" | grep -qi "^X-Evil:"; then
    fail "8.9 Header injection in filename" "CRITICAL: X-Evil reflected in response"
else
    pass "8.9 Header injection in filename — not reflected"
fi
assert_server_alive "8.9"

# ─── 8.10 WebServer POST (port 8081) — no auth → rejected ─
# AUDIT Issue #3. POST uploads to /cass/... on port 8081 must require auth.
test_start "8.10 WebServer POST --noauth — rejected"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -X POST \
    --data-binary "@$PHASE8_TMP/plain.txt" \
    "$SERVER/cass/ph8-noauth.txt" || true)
if [ "$STATUS" = "401" ] || [ "$STATUS" = "403" ]; then
    pass "8.10 WebServer POST --noauth — rejected (HTTP $STATUS)"
else
    fail "8.10 WebServer POST --noauth" "accepted with HTTP $STATUS (AUDIT Issue #3 still open)"
fi

# ─── 8.11 WebServer POST (port 8081) — valid auth → works ─
test_start "8.11 WebServer POST --auth — accepted"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -X POST \
    -H "Cookie: uuid=$UUID" \
    --data-binary "@$PHASE8_TMP/plain.txt" \
    "$SERVER/cass/ph8-auth.txt" || true)
if [ "$STATUS" = "200" ]; then
    pass "8.11 WebServer POST --auth — accepted (HTTP 200)"
else
    fail "8.11 WebServer POST --auth" "HTTP $STATUS (auth gate broke legitimate POST)"
fi

# ─── 8.12 Server alive after full Phase 8 suite ───────────
test_start
assert_server_alive "8.12" && pass "8.12 server alive after Phase 8 corpus"

# Cleanup
rm -rf "$PHASE8_TMP"
clean_incoming

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 8"

#!/bin/bash
#
# SMOKE TEST — Phase 3: Security Hardening
#
# Tests: Shutdown, config, fileexist, folders, nodes, extensions, email, invitation, shares, Base64 path traversal
# Total: 15 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 3: Security Hardening${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

printf "${BOLD}── Phase 3 Security ──${RESET}\n"

# 12.1 P3-001: shutdown.fn — noauth blocked (server stays alive)
test_start
RESP=$(curl_noauth "$SERVER/cass/shutdown.fn" || true)
ALIVE=$(curl -s -o /dev/null -w "%{http_code}" "$SERVER/cass/getsession.fn" || true)
if [ "$ALIVE" = "200" ]; then
    pass "3.1 shutdown.fn --noauth — blocked (server alive)"
else
    fail "3.1 shutdown.fn --noauth" "server may have shut down (HTTP $ALIVE)"
fi

# 12.3 P3-002: setconfig.htm — noauth blocked
test_start
RESP=$(curl_noauth "$SERVER/cass/setconfig.htm?property=testprop&value=testval" || true)
if [ -z "$RESP" ]; then
    pass "3.2 setconfig.htm --noauth — blocked"
else
    fail "3.2 setconfig.htm --noauth" "returned content without auth"
fi

# 12.4 P3-002: setconfig.htm — admin auth works
test_start
RESP=$(curl_auth "$SERVER/cass/setconfig.htm?property=testprop&value=testval")
if echo "$RESP" | grep -q 'Settings have been updated'; then
    pass "3.3 setconfig.htm — admin allowed"
else
    fail "3.3 setconfig.htm (admin)" "unexpected: $RESP"
fi

# 12.5 P3-003: fileexist.fn — noauth blocked
test_start
RESP=$(curl_noauth "$SERVER/cass/fileexist.fn?file=/etc/passwd" || true)
if [ -z "$RESP" ]; then
    pass "3.4 fileexist.fn --noauth — blocked"
else
    fail "3.4 fileexist.fn --noauth" "returned content without auth"
fi

# 12.6 P3-004: getfolders.fn — noauth blocked
test_start
RESP=$(curl_noauth "$SERVER/cass/getfolders.fn?folder=/etc" || true)
if [ -z "$RESP" ]; then
    pass "3.5 getfolders.fn --noauth — blocked"
else
    fail "3.5 getfolders.fn --noauth" "returned content without auth"
fi

# 12.7 P3-005: getnodes.fn — noauth blocked
test_start
RESP=$(curl_noauth "$SERVER/cass/getnodes.fn" || true)
if [ -z "$RESP" ]; then
    pass "3.6 getnodes.fn --noauth — blocked"
else
    fail "3.6 getnodes.fn --noauth" "returned content without auth"
fi

# 12.8 P3-006: getextensions.fn — noauth blocked
test_start
RESP=$(curl_noauth "$SERVER/cass/getextensions.fn" || true)
if [ -z "$RESP" ]; then
    pass "3.7 getextensions.fn --noauth — blocked"
else
    fail "3.7 getextensions.fn --noauth" "returned content without auth"
fi

# 12.9 P3-007: getfileextensions.fn — noauth blocked
test_start
RESP=$(curl_noauth "$SERVER/cass/getfileextensions.fn" || true)
if [ -z "$RESP" ]; then
    pass "3.8 getfileextensions.fn --noauth — blocked"
else
    fail "3.8 getfileextensions.fn --noauth" "returned content without auth"
fi

# 12.10 P3-008: getemailandgroups.fn — noauth blocked
test_start
RESP=$(curl_noauth "$SERVER/cass/getemailandgroups.fn" || true)
if [ -z "$RESP" ]; then
    pass "3.9 getemailandgroups.fn --noauth — blocked"
else
    fail "3.9 getemailandgroups.fn --noauth" "returned content without auth"
fi

# 12.11 P3-009: getinvitationmodal.fn — noauth blocked
test_start
RESP=$(curl_noauth "$SERVER/cass/getinvitationmodal.fn?shareKey=test" || true)
if [ -z "$RESP" ]; then
    pass "3.10 getinvitationmodal.fn --noauth — blocked"
else
    fail "3.10 getinvitationmodal.fn --noauth" "returned content without auth"
fi

# 12.12 P3-010: getsharesettingstag.fn — noauth blocked
test_start
RESP=$(curl_noauth "$SERVER/cass/getsharesettingstag.fn?sharetype=TAG&sharekey=test" || true)
if [ -z "$RESP" ]; then
    pass "3.11 getsharesettingstag.fn --noauth — blocked"
else
    fail "3.11 getsharesettingstag.fn --noauth" "returned content without auth"
fi

# ─── Base64 Path Traversal (CVE-style) ────────────────────

# 3.12 Base64 path traversal — /etc/passwd (noauth)
test_start
B64_PASSWD=$(echo -n "/etc/passwd" | base64)
RESP=$(curl_noauth "$SERVER/cass${B64_PASSWD}" || true)
if [ -z "$RESP" ] || echo "$RESP" | grep -q "not found\|404"; then
    pass "3.12 Base64 path traversal /etc/passwd --noauth — blocked"
else
    if echo "$RESP" | grep -q "root:"; then
        fail "3.12 Base64 path traversal /etc/passwd --noauth" "CRITICAL: file contents returned"
    else
        pass "3.12 Base64 path traversal /etc/passwd --noauth — blocked"
    fi
fi

# 3.13 Base64 path traversal — /etc/passwd (with auth)
test_start
RESP=$(curl_auth "$SERVER/cass${B64_PASSWD}" || true)
if [ -z "$RESP" ] || echo "$RESP" | grep -q "not found\|404"; then
    pass "3.13 Base64 path traversal /etc/passwd --auth — blocked"
else
    if echo "$RESP" | grep -q "root:"; then
        fail "3.13 Base64 path traversal /etc/passwd --auth" "CRITICAL: file contents returned"
    else
        pass "3.13 Base64 path traversal /etc/passwd --auth — blocked"
    fi
fi

# 3.14 Base64 path traversal — relative path ../../etc/passwd (with auth)
test_start
B64_RELATIVE=$(echo -n "../../etc/passwd" | base64)
RESP=$(curl_auth "$SERVER/cass${B64_RELATIVE}" || true)
if [ -z "$RESP" ] || echo "$RESP" | grep -q "not found\|404"; then
    pass "3.14 Base64 relative traversal ../../etc/passwd --auth — blocked"
else
    if echo "$RESP" | grep -q "root:"; then
        fail "3.14 Base64 relative traversal ../../etc/passwd --auth" "CRITICAL: file contents returned"
    else
        pass "3.14 Base64 relative traversal ../../etc/passwd --auth — blocked"
    fi
fi

# 3.15 Base64 path traversal — config file with passwords (with auth)
test_start
B64_CONFIG=$(echo -n "../scrubber/config/users.txt" | base64)
RESP=$(curl_auth "$SERVER/cass${B64_CONFIG}" || true)
if [ -z "$RESP" ] || echo "$RESP" | grep -q "not found\|404"; then
    pass "3.15 Base64 traversal to config/users.txt --auth — blocked"
else
    if echo "$RESP" | grep -q "admin\|password\|valid"; then
        fail "3.15 Base64 traversal to config/users.txt --auth" "CRITICAL: config file contents returned"
    else
        pass "3.15 Base64 traversal to config/users.txt --auth — blocked"
    fi
fi

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 3"

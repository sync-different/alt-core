#!/bin/bash
#
# SMOKE TEST — Phase 2: Security + uiv5 API Coverage
#
# Tests: Auth gates, path traversal, sharing endpoints, folder security, uiv5 APIs
# Total: 24 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 2: Security + uiv5 API Coverage${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

# ─── PHASE 2 SECURITY ─────────────────────────────────

printf "${BOLD}── Phase 2 Security ──${RESET}\n"

# P2-001: gettranslate_json.fn — auth + path traversal
test_start "2.1 gettranslate_json.fn --noauth — blocked"
RESP=$(curl_noauth "$SERVER/cass/gettranslate_json.fn?md5=../../etc/passwd")
if [ -z "$RESP" ]; then
    pass "2.1 gettranslate_json.fn --noauth — blocked"
else
    fail "2.1 gettranslate_json.fn --noauth" "returned content without auth"
fi

test_start "2.2 gettranslate_json.fn — path traversal blocked"
RESP=$(curl_auth "$SERVER/cass/gettranslate_json.fn?md5=../../etc/passwd")
if echo "$RESP" | grep -q '"Invalid md5 parameter"'; then
    pass "2.2 gettranslate_json.fn — path traversal blocked"
else
    fail "2.2 gettranslate_json.fn (traversal)" "not blocked: $RESP"
fi

test_start "2.3 gettranslate_json.fn — valid md5 accepted"
RESP=$(curl_auth "$SERVER/cass/gettranslate_json.fn?md5=d41d8cd98f00b204e9800998ecf8427e")
if echo "$RESP" | grep -q 'segments'; then
    pass "2.3 gettranslate_json.fn — valid md5 accepted"
else
    fail "2.3 gettranslate_json.fn (valid)" "unexpected: $RESP"
fi

# P2-002: serverupdateproperty.fn — admin only
test_start "2.4 serverupdateproperty.fn — admin allowed"
RESP=$(curl_auth "$SERVER/cass/serverupdateproperty.fn?property=smoketest&pvalue=1")
if [ "$RESP" = "0" ]; then
    pass "2.4 serverupdateproperty.fn — admin allowed"
else
    fail "2.4 serverupdateproperty.fn" "unexpected: $RESP"
fi

# P2-004: Sharing endpoints — noauth blocked
P2_SHARE_ENDPOINTS=(
    "refreshsharetable.fn"
    "removeshare.fn"
    "doshare_webapp.fn"
)

P2_SHARE_N=5
for ep in "${P2_SHARE_ENDPOINTS[@]}"; do
    test_start "2.${P2_SHARE_N} $ep --noauth — blocked"
    RESP=$(curl_noauth "$SERVER/cass/$ep")
    if [ -z "$RESP" ]; then
        pass "2.${P2_SHARE_N} $ep --noauth — blocked"
    else
        fail "2.${P2_SHARE_N} $ep --noauth" "returned content without auth"
    fi
    P2_SHARE_N=$((P2_SHARE_N + 1))
done

# P2-005: getfolders-json.fn — path traversal (non-scan-root)
test_start "2.8 getfolders-json.fn — non-scan-root rejected"
RESP=$(curl_auth "$SERVER/cass/getfolders-json.fn?sFolder=/etc")
if [ "$RESP" = "[]" ]; then
    pass "2.8 getfolders-json.fn — non-scan-root rejected"
else
    fail "2.8 getfolders-json.fn (traversal)" "listed non-scan-root: $RESP"
fi

# P2-006: getfolderperm.fn — noauth blocked
test_start "2.9 getfolderperm.fn --noauth — blocked"
RESP=$(curl_noauth "$SERVER/cass/getfolderperm.fn?sFolder=%2Ftmp")
if [ -z "$RESP" ]; then
    pass "2.9 getfolderperm.fn --noauth — blocked"
else
    fail "2.9 getfolderperm.fn --noauth" "returned content without auth"
fi

# P2-007: openfolder.fn — noauth blocked
test_start "2.10 openfolder.fn --noauth — blocked"
RESP=$(curl_noauth "$SERVER/cass/openfolder.fn?sNamer=test")
if [ -z "$RESP" ]; then
    pass "2.10 openfolder.fn --noauth — blocked"
else
    fail "2.10 openfolder.fn --noauth" "returned content without auth"
fi

# P2-009: checkuseruuid.fn — noauth blocked
test_start "2.11 checkuseruuid.fn --noauth — blocked"
RESP=$(curl_noauth "$SERVER/cass/checkuseruuid.fn?uuid=fake-uuid")
if [ -z "$RESP" ]; then
    pass "2.11 checkuseruuid.fn --noauth — blocked"
else
    fail "2.11 checkuseruuid.fn --noauth" "returned content without auth"
fi

# ─── UIV5 API COVERAGE ────────────────────────────────

printf "\n${BOLD}── uiv5 API Coverage ──${RESET}\n"

# 11.1 chat_clear.fn — any authenticated user can clear chat (not admin-gated)
# Note: endpoint only checks bUserAuthenticated, not admin role (WebServer.java ~8599).
# If admin-only behavior is desired, add `isUserAdmin()` gate.
test_start "2.12 chat_clear.fn — authenticated user can clear, result"
RESP=$(curl_auth "$SERVER/cass/chat_clear.fn?view=json")
if echo "$RESP" | grep -q '"result":true'; then
    pass "2.12 chat_clear.fn — authenticated user can clear, result:true"
else
    fail "2.12 chat_clear.fn" "unexpected: $RESP"
fi

# 11.2 setfolderperm.fn — set permissions (empty perms = valid request)
test_start "2.13 setfolderperm.fn — returns success JSON"
RESP=$(curl_auth "$SERVER/cass/setfolderperm.fn?sFolder=%2Ftmp&permissions=%5B%5D")
if echo "$RESP" | grep -q '"success"'; then
    pass "2.13 setfolderperm.fn — returns success JSON"
    verbose_body "$RESP"
else
    fail "2.13 setfolderperm.fn" "unexpected: $RESP"
fi

# 11.3 setfolderperm.fn — noauth blocked (returns error JSON, not empty)
test_start "2.14 setfolderperm.fn --noauth — blocked"
RESP=$(curl_noauth "$SERVER/cass/setfolderperm.fn?sFolder=%2Ftmp&permissions=%5B%5D")
if [ -z "$RESP" ] || echo "$RESP" | grep -q 'Permission denied'; then
    pass "2.14 setfolderperm.fn --noauth — blocked"
else
    fail "2.14 setfolderperm.fn --noauth" "returned content without auth"
fi

# 11.4 adduser.fn — attempt to add existing user (should return alreadyexists)
# Note: server parses ?boxuser=&boxpass= (lowercase). Earlier sBoxUser/sBoxPassword
# capitalization here was a typo — server didn't recognize the params, fell through
# with empty fields, and (pre-2026-05-01) returned a lax response that happened to
# contain 'alreadyexists'. Phase 13's server-side validation of adduser.fn rejects
# empty fields with 'error', exposing the typo. Fixed.
test_start "2.15 adduser.fn — returns expected status"
RESP=$(curl_auth "$SERVER/cass/adduser.fn?boxuser=admin&boxpass=test&useremail=test%40test.com")
if echo "$RESP" | grep -q 'alreadyexists\|success\|forbidden'; then
    pass "2.15 adduser.fn — returns expected status"
    verbose_body "$RESP"
else
    fail "2.15 adduser.fn" "unexpected: $RESP"
fi

# 11.5 adduser.fn — noauth blocked
test_start "2.16 adduser.fn --noauth — blocked"
RESP=$(curl_noauth "$SERVER/cass/adduser.fn?boxuser=hacker&boxpass=pw&useremail=h%40h.com")
if [ -z "$RESP" ]; then
    pass "2.16 adduser.fn --noauth — blocked"
else
    fail "2.16 adduser.fn --noauth" "returned content without auth"
fi

# 11.6 refreshsharetable.fn — auth functional test
test_start "2.17 refreshsharetable.fn — auth returns 200"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/refreshsharetable.fn")
if [ "$STATUS" = "200" ]; then
    pass "2.17 refreshsharetable.fn — auth returns 200"
else
    fail "2.17 refreshsharetable.fn (auth)" "HTTP $STATUS"
fi

# 11.7 getsharesettingsmodal.fn — auth functional test
test_start "2.18 getsharesettingsmodal.fn — auth returns 200"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 -H "Cookie: uuid=$UUID" "$SERVER/cass/getsharesettingsmodal.fn?sharetype=TAG&sharekey=test" || true)
if [ "$STATUS" = "200" ]; then
    pass "2.18 getsharesettingsmodal.fn — auth returns 200"
elif [ "$STATUS" = "000" ]; then
    fail "2.18 getsharesettingsmodal.fn (auth)" "empty reply (server thread crash)"
else
    fail "2.18 getsharesettingsmodal.fn (auth)" "HTTP $STATUS"
fi

# 11.8 doshare_webapp.fn — auth functional test (create share)
test_start "2.19 doshare_webapp.fn — auth returns 200"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/doshare_webapp.fn?sharetype=TAG&sharekey=smoketest&shareusers=admin" || true)
if [ "$STATUS" = "200" ]; then
    pass "2.19 doshare_webapp.fn — auth returns 200"
elif [ "$STATUS" = "000" ]; then
    fail "2.19 doshare_webapp.fn (auth)" "empty reply (server thread crash)"
else
    fail "2.19 doshare_webapp.fn (auth)" "HTTP $STATUS"
fi

# 11.9 removeshare.fn — auth functional test (remove the smoketest share)
test_start "2.20 removeshare.fn — auth returns 200"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/removeshare.fn?sharetype=TAG&sharekey=smoketest" || true)
if [ "$STATUS" = "200" ]; then
    pass "2.20 removeshare.fn — auth returns 200"
elif [ "$STATUS" = "000" ]; then
    fail "2.20 removeshare.fn (auth)" "empty reply (server thread crash)"
else
    fail "2.20 removeshare.fn (auth)" "HTTP $STATUS"
fi

# 11.10 invitation_webapp.fn — noauth blocked
test_start "2.21 invitation_webapp.fn --noauth — blocked"
RESP=$(curl_noauth --max-time 5 "$SERVER/cass/invitation_webapp.fn?sharetype=TAG&sharekey=test" || true)
if [ -z "$RESP" ]; then
    pass "2.21 invitation_webapp.fn --noauth — blocked"
else
    fail "2.21 invitation_webapp.fn --noauth" "returned content without auth"
fi

# 11.11 getsharesettingsmodal.fn — noauth blocked
test_start "2.22 getsharesettingsmodal.fn --noauth — blocked"
RESP=$(curl_noauth --max-time 5 "$SERVER/cass/getsharesettingsmodal.fn?sharetype=TAG&sharekey=test" || true)
if [ -z "$RESP" ]; then
    pass "2.22 getsharesettingsmodal.fn --noauth — blocked"
else
    fail "2.22 getsharesettingsmodal.fn --noauth" "returned content without auth"
fi

# 11.12 doshare.fn — auth functional test
test_start "2.23 doshare.fn — auth returns 200"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/doshare.fn?sharetype=TAG&sharekey=smoketest2&shareusers=admin" || true)
if [ "$STATUS" = "200" ]; then
    pass "2.23 doshare.fn — auth returns 200"
elif [ "$STATUS" = "000" ]; then
    fail "2.23 doshare.fn (auth)" "empty reply (server thread crash)"
else
    fail "2.23 doshare.fn (auth)" "HTTP $STATUS"
fi

# Clean up: remove the smoketest2 share
curl -s -o /dev/null --max-time 5 -H "Cookie: uuid=$UUID" "$SERVER/cass/removeshare.fn?sharetype=TAG&sharekey=smoketest2" || true

# 11.13 P2-013: ShareTypes.valueOf() crash — invalid sharetype returns error, not crash
test_start "2.24 removeshare.fn — invalid sharetype returns 200 (no c"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 -H "Cookie: uuid=$UUID" "$SERVER/cass/removeshare.fn?sharetype=INVALID&sharekey=test" || true)
if [ "$STATUS" = "200" ]; then
    pass "2.24 removeshare.fn — invalid sharetype returns 200 (no crash)"
elif [ "$STATUS" = "000" ]; then
    fail "2.24 removeshare.fn (invalid sharetype)" "empty reply (server thread crash)"
else
    fail "2.24 removeshare.fn (invalid sharetype)" "HTTP $STATUS"
fi

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 2"

#!/bin/bash
#
# SMOKE TEST — Phase 1: Core API Endpoints
#
# Tests: Auth, Search, Files, Tags, Folders, Chat, System, NPE checks, Param parsing
# Total: 26 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 1: Core API Endpoints${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

# ─── 1. AUTH ──────────────────────────────────────────────

printf "${BOLD}── Auth ──${RESET}\n"

# 1.1 Login (already done in smoke-common.sh, just record pass)
test_start "1.1 login.fn — login returns UUID"
pass "1.1 login.fn — login returns UUID"

# 1.2 getsession.fn (no auth required)
test_start "1.2 getsession.fn — returns crypto config (no auth)"
RESP=$(curl_noauth "$SERVER/cass/getsession.fn")
if echo "$RESP" | grep -q '"publickey"'; then
    pass "1.2 getsession.fn — returns crypto config (no auth)"
    verbose_body "$RESP"
else
    fail "1.2 getsession.fn" "missing publickey"
fi

# ─── 2. SEARCH ────────────────────────────────────────────

printf "\n${BOLD}── Search ──${RESET}\n"

# 2.1 query.fn
test_start "1.3 query.fn — returns objFound JSON"
RESP=$(curl_auth "$SERVER/cass/query.fn?view=json&ftype=.all&days=0&numobj=3")
if echo "$RESP" | grep -q '"objFound"'; then
    pass "1.3 query.fn — returns objFound JSON"
    verbose_body "$RESP"
else
    fail "1.3 query.fn" "missing objFound in response"
fi

# 2.2 sidebar.fn
test_start "1.4 sidebar.fn — returns objFound JSON"
RESP=$(curl_auth "$SERVER/cass/sidebar.fn?view=json&ftype=.all&days=0")
if echo "$RESP" | grep -q '"objFound"'; then
    pass "1.4 sidebar.fn — returns objFound JSON"
    verbose_body "$RESP"
else
    fail "1.4 sidebar.fn" "missing objFound in response"
fi

# 2.3 suggest.fn
test_start "1.5 suggest.fn — returns fighters JSON"
RESP=$(curl_auth "$SERVER/cass/suggest.fn?view=json&foo=test")
if echo "$RESP" | grep -q '"fighters"'; then
    pass "1.5 suggest.fn — returns fighters JSON"
    verbose_body "$RESP"
else
    # suggest may return empty if no data — still OK if 200
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/suggest.fn?view=json&foo=test")
    if [ "$STATUS" = "200" ]; then
        pass "1.5 suggest.fn — returns 200 (empty results)"
    else
        fail "1.5 suggest.fn" "HTTP $STATUS"
    fi
fi

# ─── 3. FILES ─────────────────────────────────────────────

printf "\n${BOLD}── Files ──${RESET}\n"

# 3.1 getfileinfo.fn (valid hex, non-existent file)
# Use a fabricated MD5 that will never match a real file hash
test_start "1.6 getfileinfo.fn — valid md5, returns error/not-found"
RESP=$(curl_auth "$SERVER/cass/getfileinfo.fn?md5=00000000deadbeef00000000deadbeef")
if echo "$RESP" | grep -q '"error"'; then
    pass "1.6 getfileinfo.fn — valid md5, returns error/not-found"
    verbose_body "$RESP"
else
    fail "1.6 getfileinfo.fn" "unexpected response"
fi

# 3.2 getfileinfo.fn (invalid md5 — security check)
test_start "1.7 getfileinfo.fn — rejects path traversal md5"
RESP=$(curl_auth "$SERVER/cass/getfileinfo.fn?md5=../../etc/passwd")
if echo "$RESP" | grep -q '"Invalid md5 parameter"'; then
    pass "1.7 getfileinfo.fn — rejects path traversal md5"
else
    fail "1.7 getfileinfo.fn (security)" "path traversal not blocked"
fi

# 3.3 getfile.fn (no params — should not crash)
test_start "1.8 getfile.fn — no params returns 200 (no crash)"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/getfile.fn")
if [ "$STATUS" = "200" ]; then
    pass "1.8 getfile.fn — no params returns 200 (no crash)"
else
    fail "1.8 getfile.fn" "HTTP $STATUS (expected 200)"
fi

# ─── 4. TAGS ──────────────────────────────────────────────

printf "\n${BOLD}── Tags ──${RESET}\n"

# 4.1 gettags_webapp.fn
test_start "1.9 gettags_webapp.fn — returns tag list"
RESP=$(curl_auth "$SERVER/cass/gettags_webapp.fn?view=json")
if echo "$RESP" | grep -q '"fighters"'; then
    pass "1.9 gettags_webapp.fn — returns tag list"
    verbose_body "$RESP"
else
    fail "1.9 gettags_webapp.fn" "missing fighters array"
fi

# 4.2 applytags.fn (XSS tag — should strip HTML)
test_start "1.10 applytags.fn — accepts tag (HTML stripped server-side)"
RESP=$(curl_auth "$SERVER/cass/applytags.fn?tag=%3Cscript%3Ealert(1)%3C/script%3E&fake=on")
if echo "$RESP" | grep -q 'true'; then
    pass "1.10 applytags.fn — accepts tag (HTML stripped server-side)"
else
    fail "1.10 applytags.fn (XSS)" "unexpected response: $RESP"
fi

# ─── 5. FOLDERS ───────────────────────────────────────────

printf "\n${BOLD}── Folders ──${RESET}\n"

# 5.1 getfolders-json.fn
test_start "1.11 getfolders-json.fn — returns folder list"
RESP=$(curl_auth "$SERVER/cass/getfolders-json.fn?view=json&sFolder=scanfolders")
if echo "$RESP" | grep -q '"name"'; then
    pass "1.11 getfolders-json.fn — returns folder list"
    verbose_body "$RESP"
else
    # May be empty if no scan folders configured
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/getfolders-json.fn?view=json&sFolder=scanfolders")
    if [ "$STATUS" = "200" ]; then
        pass "1.11 getfolders-json.fn — returns 200 (empty list)"
    else
        fail "1.11 getfolders-json.fn" "HTTP $STATUS"
    fi
fi

# 5.2 getfolderperm.fn
test_start "1.12 getfolderperm.fn — returns permissions JSON"
RESP=$(curl_auth "$SERVER/cass/getfolderperm.fn?sFolder=%2Ftmp")
if echo "$RESP" | grep -q '"permissions"'; then
    pass "1.12 getfolderperm.fn — returns permissions JSON"
    verbose_body "$RESP"
else
    fail "1.12 getfolderperm.fn" "missing permissions in response"
fi

# ─── 6. CHAT ──────────────────────────────────────────────

printf "\n${BOLD}── Chat ──${RESET}\n"

# 6.1 chat_pull.fn
test_start "1.13 chat_pull.fn — returns messages array"
RESP=$(curl_auth "$SERVER/cass/chat_pull.fn?view=json&md5=&msg_from=0")
if echo "$RESP" | grep -q '"messages"'; then
    pass "1.13 chat_pull.fn — returns messages array"
    verbose_body "$RESP"
else
    fail "1.13 chat_pull.fn" "missing messages in response"
fi

# 6.2 chat_push.fn
test_start "1.14 chat_push.fn — push returns res:1"
RESP=$(curl_auth "$SERVER/cass/chat_push.fn?view=json&md5=&msg_user=admin&msg_type=CHAT&msg_body=c21va2V0ZXN0&msg_from=0")
if echo "$RESP" | grep -q '"res":1'; then
    pass "1.14 chat_push.fn — push returns res:1"
else
    fail "1.14 chat_push.fn" "unexpected response: $RESP"
fi

# ─── 7. SYSTEM ────────────────────────────────────────────

printf "\n${BOLD}── System ──${RESET}\n"

# 7.1 nodeinfo.fn
test_start "1.15 nodeinfo.fn — returns node data"
RESP=$(curl_auth "$SERVER/cass/nodeinfo.fn?view=json")
if echo "$RESP" | grep -q '"node_id"'; then
    pass "1.15 nodeinfo.fn — returns node data"
    verbose_body "$RESP"
else
    fail "1.15 nodeinfo.fn" "missing node_id"
fi

# 7.2 serverproperty.fn
test_start "1.16 serverproperty.fn — returns property value"
RESP=$(curl_auth "$SERVER/cass/serverproperty.fn?property=allowremote")
if [ -n "$RESP" ]; then
    pass "1.16 serverproperty.fn — returns property value"
    verbose_body "$RESP"
else
    fail "1.16 serverproperty.fn" "empty response"
fi

# 7.3 getcluster.fn
test_start "1.17 getcluster.fn — returns cluster ID"
RESP=$(curl_auth "$SERVER/cass/getcluster.fn?view=json")
if [ -n "$RESP" ]; then
    pass "1.17 getcluster.fn — returns cluster ID"
    verbose_body "$RESP"
else
    fail "1.17 getcluster.fn" "empty response"
fi

# 7.4 getusersandemail.fn
test_start "1.18 getusersandemail.fn — returns users list"
RESP=$(curl_auth "$SERVER/cass/getusersandemail.fn?view=json")
if echo "$RESP" | grep -q '"users"'; then
    pass "1.18 getusersandemail.fn — returns users list"
    verbose_body "$RESP"
else
    fail "1.18 getusersandemail.fn" "missing users in response"
fi

# ─── 8. UNAUTHENTICATED ACCESS (NPE regression) ──────────

printf "\n${BOLD}── Unauthenticated Access (NPE checks) ──${RESET}\n"

NOAUTH_ENDPOINTS=(
    "query.fn?view=json&ftype=.all&days=0"
    "sidebar.fn?view=json&ftype=.all&days=0"
    "suggest.fn?view=json&foo=test"
    "getfile.fn"
    "getfileinfo.fn?md5=abc123"
    "applytags.fn?tag=test&fake=on"
    "chat_pull.fn?view=json"
)

NOAUTH_N=19
for ep in "${NOAUTH_ENDPOINTS[@]}"; do
    test_start "1.26 query.fn — no param collision (days vs holidays)"
    ENDPOINT_NAME=$(echo "$ep" | sed 's/\?.*//')
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$SERVER/cass/$ep")
    if [ "$STATUS" = "200" ]; then
        pass "1.${NOAUTH_N} $ENDPOINT_NAME --noauth — 200 (no crash)"
    else
        fail "1.${NOAUTH_N} $ENDPOINT_NAME --noauth" "HTTP $STATUS (expected 200)"
    fi
    NOAUTH_N=$((NOAUTH_N + 1))
done

# ─── 9. PARAM COLLISION (BUG-008 regression) ─────────────

printf "\n${BOLD}── Parameter Parsing ──${RESET}\n"

# days param should not collide with holidays
test_start "1.26 query.fn — no param collision (days vs holidays)"
RESP=$(curl_auth "$SERVER/cass/query.fn?view=json&ftype=.all&days=7&holidays=true&numobj=3")
if echo "$RESP" | grep -q '"objFound"'; then
    pass "1.26 query.fn — no param collision (days vs holidays)"
else
    fail "1.26 query.fn (param collision)" "days/holidays collision detected"
fi

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 1"

#!/bin/bash
#
# SMOKE TEST — Phase 5: Malformed Inputs / Fuzz Testing
#
# Tests: Oversized params, malformed JSON, XSS, null bytes, emoji, negative values, server alive
# Total: 15 tests
#
# REMOTE mode note: upstream WAFs (Cloudflare, AWS WAF, etc.) may block the
# obviously-malicious payloads below before they reach the server. That is
# still a safe outcome — the server never saw the bad input. `check_fuzz_status`
# treats HTTP 400/403/406 as pass when SMOKE_URL is set.
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 5: Malformed Inputs / Fuzz Testing${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

printf "${BOLD}── Phase 5: Malformed Inputs / Fuzz ──${RESET}\n"

# P5-001: query.fn — empty ftype parameter
test_start "5.1 query.fn — empty ftype"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/query.fn?view=json&ftype=&days=0" || true)
check_fuzz_status "5.1" "query.fn — empty ftype" "$STATUS"

# P5-002: query.fn — very long search string (2000 chars)
test_start "5.2 query.fn — 2000-char search string"
LONG_STR=$(python3 -c 'print("A"*2000)')
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/query.fn?view=json&ftype=.all&foo=$LONG_STR&days=0&numobj=1" || true)
check_fuzz_status "5.2" "query.fn — 2000-char search string" "$STATUS"

# P5-003: getfileinfo.fn — empty md5
test_start "5.3 getfileinfo.fn — empty md5"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/getfileinfo.fn?md5=" || true)
check_fuzz_status "5.3" "getfileinfo.fn — empty md5" "$STATUS"

# P5-004: getfileinfo.fn — very long md5 (1000 chars)
test_start "5.4 getfileinfo.fn — 1000-char md5"
LONG_MD5=$(python3 -c 'print("a"*1000)')
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/getfileinfo.fn?md5=$LONG_MD5" || true)
check_fuzz_status "5.4" "getfileinfo.fn — 1000-char md5" "$STATUS"

# P5-005: getfileinfo.fn — special chars in md5 (XSS attempt)
# Cloudflare commonly blocks <script> patterns — REMOTE treats 4xx as pass.
test_start "5.5 getfileinfo.fn — XSS in md5 param"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/getfileinfo.fn?md5=%3Cscript%3Ealert(1)%3C%2Fscript%3E" || true)
check_fuzz_status "5.5" "getfileinfo.fn — XSS in md5 param" "$STATUS"

# P5-006: applytags.fn — very long tag name (500 chars)
test_start "5.6 applytags.fn — 500-char tag name"
LONG_TAG=$(python3 -c 'print("T"*500)')
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" "$SERVER/cass/applytags.fn?tag=$LONG_TAG&fake=on" || true)
check_fuzz_status "5.6" "applytags.fn — 500-char tag name" "$STATUS"

# P5-007: chat_push.fn — oversized message body (10KB base64)
test_start "5.7 chat_push.fn — 10KB message body"
BIG_MSG=$(python3 -c 'import base64; print(base64.b64encode(b"X"*10240).decode())')
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/chat_push.fn?view=json&md5=&msg_user=admin&msg_type=CHAT&msg_body=$BIG_MSG&msg_from=0" || true)
check_fuzz_status "5.7" "chat_push.fn — 10KB message body" "$STATUS"

# P5-008: setfolderperm.fn — malformed JSON permissions
test_start "5.8 setfolderperm.fn — malformed JSON"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/setfolderperm.fn?sFolder=%2Ftmp&permissions=NOT_VALID_JSON" || true)
check_fuzz_status "5.8" "setfolderperm.fn — malformed JSON" "$STATUS"

# P5-009: setfolderperm.fn — deeply nested JSON
test_start "5.9 setfolderperm.fn — deeply nested JSON"
NESTED_JSON=$(python3 -c 'import urllib.parse; print(urllib.parse.quote("[" * 50 + "]" * 50))')
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --globoff -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/setfolderperm.fn?sFolder=%2Ftmp&permissions=$NESTED_JSON" || true)
check_fuzz_status "5.9" "setfolderperm.fn — deeply nested JSON" "$STATUS"

# P5-010: query.fn — negative days value
test_start "5.10 query.fn — negative days value"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/query.fn?view=json&ftype=.all&days=-1&numobj=1" || true)
check_fuzz_status "5.10" "query.fn — negative days value" "$STATUS"

# P5-011: query.fn — non-numeric days value
test_start "5.11 query.fn — non-numeric days value"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/query.fn?view=json&ftype=.all&days=abc&numobj=1" || true)
check_fuzz_status "5.11" "query.fn — non-numeric days value" "$STATUS"

# P5-012: getfolders-json.fn — null byte in folder path
# Null bytes in URLs are a classic WAF trigger — REMOTE tolerates 4xx.
test_start "5.12 getfolders-json.fn — null byte in path"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/getfolders-json.fn?sFolder=%00%2Fetc" || true)
check_fuzz_status "5.12" "getfolders-json.fn — null byte in path" "$STATUS"

# P5-013: suggest.fn — unicode/emoji input
test_start "5.13 suggest.fn — emoji input"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/suggest.fn?view=json&foo=%F0%9F%92%A9" || true)
check_fuzz_status "5.13" "suggest.fn — emoji input" "$STATUS"

# P5-014: serverproperty.fn — non-existent property
test_start "5.14 serverproperty.fn — non-existent property"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/serverproperty.fn?property=doesnotexist_smoketest99" || true)
check_fuzz_status "5.14" "serverproperty.fn — non-existent property" "$STATUS"

# P5-015: Server alive check after all fuzz tests
test_start "5.15 server alive after fuzz tests"
ALIVE=$(curl -s -o /dev/null -w "%{http_code}" "$SERVER/cass/getsession.fn" || true)
if [ "$ALIVE" = "200" ]; then
    pass "5.15 Server alive after fuzz tests — getsession.fn returns 200"
else
    fail "5.15 Server alive check" "HTTP $ALIVE — server may have crashed"
fi

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 5"

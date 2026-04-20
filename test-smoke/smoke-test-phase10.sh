#!/bin/bash
#
# SMOKE TEST — Phase 10: HTTP Protocol & Response Hardening
#
# Tests HTTP-layer defenses: header injection, CORS origin validation,
# request smuggling resistance, oversized headers, error-message info
# disclosure, fingerprinting surface, and missing security headers.
#
# Some tests document CURRENT behavior rather than a fixed endpoint —
# these are marked in the test description so failures drive code
# changes (e.g., add X-Content-Type-Options: nosniff header).
#
# Total: 13 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 10: HTTP Protocol & Response Hardening${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

printf "${BOLD}── Phase 10: HTTP Hardening ──${RESET}\n"

UPLOAD_PORT=8087

# Helper: fetch response headers. Returns headers with dos line endings stripped.
# Usage: get_headers URL [extra-curl-args...]
get_headers() {
    curl -s -D - -o /dev/null --max-time 5 "$@" 2>/dev/null | tr -d '\r' || true
}

# Helper: check server is alive after a probe
assert_server_alive() {
    local TEST_ID="$1"
    local ALIVE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$SERVER/cass/getsession.fn" || true)
    if [ "$ALIVE" != "200" ]; then
        fail "$TEST_ID liveness" "getsession.fn returned HTTP $ALIVE after test"
        return 1
    fi
    return 0
}

# ─── 10.1 CRLF injection in query parameter ───────────────
# Scanner injects %0d%0a into a query param hoping it reflects into response
# headers. The server builds response headers by hand, so any reflection is a
# header-splitting vuln. Must NOT see X-Evil in response headers.
test_start "10.1 CRLF injection in query param"
HEADERS=$(get_headers -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/getfileinfo.fn?md5=foo%0d%0aX-Evil:%20injected")
if echo "$HEADERS" | grep -qi "^X-Evil:"; then
    fail "10.1 CRLF injection in query param" "CRITICAL: X-Evil header reflected"
else
    pass "10.1 CRLF injection in query param — not reflected"
fi
assert_server_alive "10.1"

# ─── 10.2 Host header — not reflected in redirects ────────
# Attacker sends Host: evil.com hoping the app builds URLs from Host.
# The login flow doesn't issue a redirect, but many endpoints build URLs
# from request context. Fetch with spoofed Host, confirm no Location header
# or body references evil.com as a redirect target.
test_start "10.2 Host header reflection"
HEADERS=$(get_headers -H "Host: evil.com" \
    -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/getsession.fn")
# Look for Location: ... evil.com or Refresh: ... evil.com
if echo "$HEADERS" | grep -iE "^(Location|Refresh):" | grep -qi "evil.com"; then
    fail "10.2 Host header reflection" "CRITICAL: evil.com in redirect header"
else
    pass "10.2 Host header — not reflected in redirects"
fi
assert_server_alive "10.2"

# ─── 10.3 CORS Origin — port 8081 doesn't echo arbitrary origin ─
# ACAO should be a fixed allow-list, never reflect the request's Origin.
test_start "10.3 CORS Origin reflection (port 8081)"
HEADERS=$(get_headers -H "Origin: https://evil.com" \
    "$SERVER/cass/getsession.fn")
ACAO=$(echo "$HEADERS" | grep -i "^Access-Control-Allow-Origin:" | head -1 | sed 's/.*: *//I' || true)
if echo "$ACAO" | grep -qi "evil.com"; then
    fail "10.3 CORS Origin reflection (port 8081)" "CRITICAL: ACAO echoed evil.com"
elif echo "$ACAO" | grep -qi "^\*$"; then
    fail "10.3 CORS Origin (port 8081)" "ACAO is wildcard * (should be fixed allow-list)"
else
    pass "10.3 CORS Origin (port 8081) — ACAO='$ACAO' (not reflecting evil.com)"
fi
assert_server_alive "10.3"

# ─── 10.4 CORS Origin: null — rejected on port 8081 ──────
# `Origin: null` comes from sandboxed iframes and file:// pages.
# Treating it as a trusted origin is a known bypass.
test_start "10.4 CORS Origin: null (port 8081)"
HEADERS=$(get_headers -H "Origin: null" \
    "$SERVER/cass/getsession.fn")
ACAO=$(echo "$HEADERS" | grep -i "^Access-Control-Allow-Origin:" | head -1 | sed 's/.*: *//I' || true)
if [ "$ACAO" = "null" ]; then
    fail "10.4 CORS Origin: null (port 8081)" "ACAO echoed 'null'"
else
    pass "10.4 CORS Origin: null — not echoed (ACAO='$ACAO')"
fi

# ─── 10.5 CORS on port 8087 upload — allowlist enforcement ─
# AUDIT Issue #18: port 8087 originally sent `Access-Control-Allow-Origin: *`.
# Fixed 2026-04-16 to mirror request Origin so credentialed uploads work; then
# further hardened 2026-04-17 to mirror only allowlisted origins (same-host,
# same-hostname-different-port, or well-known dev origins like localhost:5173).
#
# Evil.com is NOT in the allowlist. The server should fall back to same-Host
# mirror (http://localhost:8087 for this test) — NOT echo evil.com.
test_start "10.5 port 8087 CORS"
if $SMOKE_REMOTE; then
    skip "10.5 port 8087 CORS" "REMOTE mode — direct port 8087 access not available"
elif ! lsof -ti:$UPLOAD_PORT > /dev/null 2>&1; then
    skip "10.5 port 8087 CORS" "upload port not running"
else
    HEADERS=$(curl -s -D - -o /dev/null --max-time 3 \
        -H "Origin: https://evil.com" -X OPTIONS \
        "http://localhost:$UPLOAD_PORT/formpost" 2>/dev/null | tr -d '\r' || true)
    ACAO=$(echo "$HEADERS" | grep -i "^Access-Control-Allow-Origin:" | head -1 | sed 's/.*: *//I' || true)
    if echo "$ACAO" | grep -qi "evil.com"; then
        fail "10.5 port 8087 CORS" "CRITICAL: non-allowlisted Origin echoed (ACAO='$ACAO')"
    elif [ "$ACAO" = "*" ]; then
        fail "10.5 port 8087 CORS" "ACAO=* with Allow-Credentials:true is unsafe — should be same-Host mirror"
    else
        pass "10.5 port 8087 CORS — evil.com NOT echoed (ACAO='$ACAO', allowlist working)"
    fi
fi

# ─── 10.6 HTTP Request Smuggling probe ────────────────────
# Send conflicting Content-Length and Transfer-Encoding headers. Netty 4.1.68
# (AUDIT Issue #9 / CVE-2021-43797) is vulnerable to smuggling. Until the
# upgrade lands, this test confirms the server at least doesn't crash and
# returns a single coherent response.
test_start "10.6 HTTP smuggling probe"
# Send TE: chunked + CL: 4 with a body. Vulnerable parsers disagree on boundary.
# Uses raw TCP via nc — can't do HTTPS, so skip REMOTE HTTPS targets.
if $SMOKE_REMOTE && echo "$SMOKE_URL" | grep -q "^https://"; then
    skip "10.6 HTTP smuggling probe" "REMOTE HTTPS — nc cannot do TLS"
else
    if $SMOKE_REMOTE; then
        SMK_HOST_PORT=$(echo "$SMOKE_URL" | sed -E 's|^https?://||;s|/.*||')
        SMK_NC_HOST=$(echo "$SMK_HOST_PORT" | cut -d: -f1)
        SMK_NC_PORT=$(echo "$SMK_HOST_PORT" | awk -F: 'NF>1{print $2; exit} {print 80}')
    else
        SMK_NC_HOST="localhost"
        SMK_NC_PORT="8081"
    fi
RESP=$(printf 'POST /cass/getsession.fn HTTP/1.1\r\nHost: %s:%s\r\nContent-Length: 4\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\nGET /admin HTTP/1.1\r\nHost: %s:%s\r\n\r\n' "$SMK_NC_HOST" "$SMK_NC_PORT" "$SMK_NC_HOST" "$SMK_NC_PORT" \
    | nc -w 3 "$SMK_NC_HOST" "$SMK_NC_PORT" 2>/dev/null | head -3 || true)
    RESP_COUNT=$(echo "$RESP" | grep -cE "^HTTP/1\.[01]" || true)
    if [ -z "$RESP" ]; then
        pass "10.6 HTTP smuggling probe — server closed connection (safe)"
    elif echo "$RESP" | grep -qE "^HTTP/1\.[01] (4|5)"; then
        pass "10.6 HTTP smuggling probe — rejected with 4xx/5xx"
    elif [ "$RESP_COUNT" = "1" ]; then
        FIRST_LINE=$(echo "$RESP" | head -1)
        pass "10.6 HTTP smuggling probe — single response ($FIRST_LINE)"
    else
        fail "10.6 HTTP smuggling probe" "multiple HTTP responses in output — possible smuggling"
    fi
    assert_server_alive "10.6"
fi

# ─── 10.7 Oversized request header ────────────────────────
# 8KB User-Agent. Must be handled gracefully (reject or truncate) without crash.
test_start "10.7 8KB User-Agent — handled"
BIG_UA=$(python3 -c 'print("U"*8192)')
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    -H "User-Agent: $BIG_UA" \
    "$SERVER/cass/getsession.fn" || true)
if [ "$STATUS" = "200" ] || [ "$STATUS" = "400" ] || [ "$STATUS" = "413" ] || [ "$STATUS" = "431" ]; then
    pass "10.7 8KB User-Agent — handled (HTTP $STATUS)"
else
    fail "10.7 8KB User-Agent" "unexpected HTTP $STATUS"
fi
assert_server_alive "10.7"

# ─── 10.8 500-trigger request — no stack trace in body ────
# Send inputs that tend to produce internal errors, then confirm the response
# body doesn't leak Java stack traces. We probe a few endpoints with inputs
# known to be unusual; any 500 response must be sanitized.
test_start "10.8 stack trace in response"
FOUND_TRACE=false
for PROBE in \
    "getfile.fn?sNamer=\$\$\$not-hex\$\$\$" \
    "getfileinfo.fn?md5=NULL%00" \
    "query.fn?view=xml&ftype=.all&days=-999999999&numobj=abc" \
    "chat_push.fn?msg_type=\$\$&msg_body=%FF%FE%00" ; do
    BODY=$(curl -s --max-time 3 -H "Cookie: uuid=$UUID" "$SERVER/cass/$PROBE" 2>/dev/null || true)
    if echo "$BODY" | grep -qE "(java\.|at [a-z]+\.[A-Z][a-zA-Z]+\.|Caused by:|Exception in thread|\.java:[0-9]+\))"; then
        FOUND_TRACE=true
        FOUND_PROBE="$PROBE"
        break
    fi
done
if $FOUND_TRACE; then
    fail "10.8 stack trace in response" "probe '$FOUND_PROBE' leaked Java trace"
else
    pass "10.8 stack trace in response — not leaked on 4 probes"
fi
assert_server_alive "10.8"

# ─── 10.9 500-trigger — no absolute filesystem path in body ─
# Confirms error responses don't leak /Users/... or /home/... filesystem paths.
test_start "10.9 absolute path in response"
FOUND_PATH=false
for PROBE in \
    "getfile.fn?sNamer=doesnotexist" \
    "getfileinfo.fn?md5=00000000000000000000000000000000" \
    "getfolders-json.fn?sFolder=/doesnotexist/foo" ; do
    BODY=$(curl -s --max-time 3 -H "Cookie: uuid=$UUID" "$SERVER/cass/$PROBE" 2>/dev/null || true)
    # Look for absolute paths: /Users/..., /home/..., /Applications/..., C:\...
    if echo "$BODY" | grep -qE "(/Users/[a-zA-Z]|/home/[a-zA-Z]|/Applications/[a-zA-Z]|[A-Z]:\\\\[a-zA-Z])"; then
        FOUND_PATH=true
        FOUND_PROBE="$PROBE"
        break
    fi
done
if $FOUND_PATH; then
    fail "10.9 absolute path in response" "probe '$FOUND_PROBE' leaked filesystem path"
else
    pass "10.9 absolute path in response — not leaked on 3 probes"
fi
assert_server_alive "10.9"

# ─── 10.10 Server fingerprinting — minimal Server: header ──
# Current value is "Simple java" which is acceptable (no version/OS).
# This test alerts if someone adds Java version to the header.
test_start "10.10 Server header fingerprinting"
HEADERS=$(get_headers "$SERVER/cass/getsession.fn")
SERVER_HDR=$(echo "$HEADERS" | grep -i "^Server:" | head -1 | sed 's/.*: *//I' || true)
if echo "$SERVER_HDR" | grep -qiE "(java/[0-9]|jvm|jdk|openjdk|oracle|darwin|linux|windows|[0-9]+\.[0-9]+\.[0-9]+)"; then
    fail "10.10 Server header fingerprinting" "leaks version info: '$SERVER_HDR'"
else
    pass "10.10 Server header — minimal ('$SERVER_HDR')"
fi

# ─── 10.11 X-Content-Type-Options: nosniff ────────────────
# Prevents browsers from MIME-sniffing responses (mitigates XSS on
# user-uploaded content). Currently MISSING — this test drives a backend
# change to add the header.
test_start "10.11 X-Content-Type-Options: nosniff — present"
HEADERS=$(get_headers -H "Cookie: uuid=$UUID" "$SERVER/cass/getsession.fn")
if echo "$HEADERS" | grep -qi "^X-Content-Type-Options: *nosniff"; then
    pass "10.11 X-Content-Type-Options: nosniff — present"
else
    fail "10.11 X-Content-Type-Options: nosniff" "missing (add to printHeaders())"
fi

# ─── 10.12 X-Frame-Options or CSP frame-ancestors ─────────
# Prevents clickjacking. Either X-Frame-Options: DENY/SAMEORIGIN or
# CSP frame-ancestors directive is acceptable.
test_start "10.12 X-Frame-Options — present"
HEADERS=$(get_headers -H "Cookie: uuid=$UUID" "$SERVER/cass/getsession.fn")
if echo "$HEADERS" | grep -qiE "^X-Frame-Options: *(DENY|SAMEORIGIN)"; then
    pass "10.12 X-Frame-Options — present"
elif echo "$HEADERS" | grep -qi "^Content-Security-Policy:.*frame-ancestors"; then
    pass "10.12 CSP frame-ancestors — present"
else
    fail "10.12 clickjacking protection" "neither X-Frame-Options nor CSP frame-ancestors set"
fi

# ─── 10.13 OPTIONS preflight — specific methods only ──────
# Preflight must return a specific method list, not *. Overly permissive
# preflight can enable CORS-based attacks on non-CORS-safe methods.
test_start "10.13 OPTIONS preflight methods"
HEADERS=$(curl -s -D - -o /dev/null --max-time 3 -X OPTIONS \
    -H "Origin: http://localhost:5173" \
    -H "Access-Control-Request-Method: DELETE" \
    "$SERVER/cass/login.fn" 2>/dev/null | tr -d '\r' || true)
METHODS=$(echo "$HEADERS" | grep -i "^Access-Control-Allow-Methods:" | head -1 | sed 's/.*: *//I' || true)
if [ "$METHODS" = "*" ]; then
    fail "10.13 OPTIONS preflight methods" "wildcard * in Access-Control-Allow-Methods"
elif [ -z "$METHODS" ]; then
    # No ACAM header means OPTIONS isn't supported or browser uses safe defaults — that's OK
    pass "10.13 OPTIONS preflight — no ACAM header (safe)"
else
    pass "10.13 OPTIONS preflight methods — explicit list: '$METHODS'"
fi

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 10"

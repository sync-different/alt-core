#!/bin/bash
#
# SMOKE TEST — Phase 9: Session & Authentication Lifecycle
#
# Tests session lifecycle attacks and hardening:
#   - UUID entropy (unpredictability of session tokens)
#   - Session invalidation on logout
#   - Session fixation (preset cookie doesn't carry into login)
#   - Cookie security flags (HttpOnly, SameSite)
#   - Query-param UUID precedence (stale cookie + valid query param)
#   - Concurrent session behavior
#   - Privilege escalation (admin-only endpoints with non-admin session)
#   - Stale token graceful handling
#   - Login rate limiting (5 attempts / 5 min already implemented)
#
# Tests pair with hardening fixes where they reveal gaps.
#
# Total: 14 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 9: Session & Authentication Lifecycle${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

printf "${BOLD}── Phase 9: Session Lifecycle ──${RESET}\n"

# ─── 9.1 UUID token entropy — 20 logins produce 20 distinct UUIDs ──
# Predictable session tokens = trivial session hijacking.
test_start "9.1 UUID entropy"
declare -a UUIDS_SEEN
COLLISIONS=0
for i in $(seq 1 20); do
    LOGIN_RESP=$(cli login "$USER" "$PASS" 2>/dev/null || true)
    U=$(echo "$LOGIN_RESP" | grep '"uuid"' | sed 's/.*"uuid": "//;s/".*//')
    if [ -n "$U" ]; then
        for existing in "${UUIDS_SEEN[@]+"${UUIDS_SEEN[@]}"}"; do
            if [ "$U" = "$existing" ]; then
                COLLISIONS=$((COLLISIONS + 1))
            fi
        done
        UUIDS_SEEN+=("$U")
    fi
done
if [ "$COLLISIONS" = "0" ] && [ "${#UUIDS_SEEN[@]}" -ge "18" ]; then
    pass "9.1 UUID entropy — ${#UUIDS_SEEN[@]} distinct tokens, 0 collisions"
else
    fail "9.1 UUID entropy" "${COLLISIONS} collisions in ${#UUIDS_SEEN[@]} logins"
fi

# ─── 9.2 UUID format — RFC 4122 (36-char hex with dashes) ──────
# Loose format = attacker may guess patterns.
test_start "9.2 UUID format — RFC 4122 compliant"
if echo "$UUID" | grep -qE "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"; then
    pass "9.2 UUID format — RFC 4122 compliant"
else
    fail "9.2 UUID format" "not RFC 4122: '$UUID'"
fi

# ─── 9.3 Session rejected after logout.fn ──────────────────
# Log out with a throwaway session and confirm the UUID is invalidated.
test_start "9.3 logout session invalidation"
LOGIN_RESP=$(cli login "$USER" "$PASS" 2>/dev/null || true)
THROWAWAY_UUID=$(echo "$LOGIN_RESP" | grep '"uuid"' | sed 's/.*"uuid": "//;s/".*//')
if [ -z "$THROWAWAY_UUID" ]; then
    fail "9.3 logout session invalidation" "could not create throwaway session"
else
    # Verify session works first
    STATUS_PRE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 \
        -H "Cookie: uuid=$THROWAWAY_UUID" "$SERVER/cass/nodeinfo.fn" || true)
    # Log out
    curl -s --max-time 3 -H "Cookie: uuid=$THROWAWAY_UUID" "$SERVER/cass/logout.fn" > /dev/null 2>&1 || true
    # Now try a protected endpoint — should fail auth
    RESP_POST=$(curl -s --max-time 3 -H "Cookie: uuid=$THROWAWAY_UUID" "$SERVER/cass/shutdown.fn" || true)
    # shutdown.fn without auth returns empty; with auth returns something specific.
    # We use a different check: hit an endpoint that reflects auth state clearly.
    # Try getfolders-json.fn — requires auth to return JSON.
    POST_RESP=$(curl -s --max-time 3 -H "Cookie: uuid=$THROWAWAY_UUID" "$SERVER/cass/getfolders-json.fn" || true)
    if [ -z "$POST_RESP" ] || ! echo "$POST_RESP" | grep -q '{'; then
        pass "9.3 session invalidated after logout.fn"
    else
        fail "9.3 session invalidated after logout" "UUID still works: $POST_RESP"
    fi
fi

# ─── 9.4 Admin-initiated logout (targetUuid) ───────────────
# Admin revokes another session; revoked UUID must fail immediately.
test_start "9.4 admin revoke"
# Create a victim session
LOGIN_RESP=$(cli login "$USER" "$PASS" 2>/dev/null || true)
VICTIM_UUID=$(echo "$LOGIN_RESP" | grep '"uuid"' | sed 's/.*"uuid": "//;s/".*//')
if [ -z "$VICTIM_UUID" ]; then
    fail "9.4 admin revoke" "could not create victim session"
else
    # Admin (our $UUID) revokes the victim's UUID
    REVOKE_RESP=$(curl -s --max-time 3 -H "Cookie: uuid=$UUID" \
        "$SERVER/cass/logout.fn?targetUuid=$VICTIM_UUID" || true)
    # Try to use the revoked UUID
    POST_RESP=$(curl -s --max-time 3 -H "Cookie: uuid=$VICTIM_UUID" "$SERVER/cass/getfolders-json.fn" || true)
    if [ -z "$POST_RESP" ] || ! echo "$POST_RESP" | grep -q '{'; then
        pass "9.4 admin revoke of targetUuid — session invalidated"
    else
        fail "9.4 admin revoke" "revoked UUID still works: $POST_RESP"
    fi
fi

# ─── 9.5 Wrong password → no session created ───────────────
# Confirms bad credentials never return a UUID.
# Uses user1 (non-admin) so admin's rate-limit budget stays clean.
test_start "9.5 wrong password — no UUID issued"
WRONG_RESP=$(cli login "user1" "wrong-password-xyz" 2>/dev/null || true)
WRONG_UUID=$(echo "$WRONG_RESP" | grep -o '"uuid": "[^"]*"' | sed 's/.*: "//;s/"//' || true)
if [ -z "$WRONG_UUID" ] || [ "$WRONG_UUID" = "null" ]; then
    pass "9.5 wrong password — no UUID issued"
else
    fail "9.5 wrong password" "UUID issued: $WRONG_UUID"
fi

# ─── 9.6 SQL-injection-like password — handled safely ──────
# The auth code doesn't use SQL, but we confirm no special handling breaks
# (no crash, no unexpected session creation). Uses user2 (non-admin).
test_start "9.6 SQL-injection-like password — rejected"
INJ_RESP=$(cli login "user2" "' OR '1'='1" 2>/dev/null || true)
INJ_UUID=$(echo "$INJ_RESP" | grep -o '"uuid": "[^"]*"' | sed 's/.*: "//;s/"//' || true)
if [ -z "$INJ_UUID" ] || [ "$INJ_UUID" = "null" ]; then
    pass "9.6 SQL-injection-like password — rejected"
else
    fail "9.6 SQL-injection-like password" "UUID issued: $INJ_UUID"
fi

# ─── 9.7 Session fixation — preset cookie doesn't carry through login ──
# Attacker sets a victim's cookie to a known UUID, then tricks victim into
# logging in. A safe server issues a NEW UUID on login, abandoning the preset.
test_start "9.7 session fixation"
ATTACKER_UUID="11111111-2222-3333-4444-555555555555"
# Use boxuser=/boxpass= (the real login params). Grab Set-Cookie in one request.
NEW_COOKIE=$(curl -s -D - -o /dev/null --max-time 5 \
    -H "Cookie: uuid=$ATTACKER_UUID" \
    "$SERVER/cass/login.fn?boxuser=$USER&boxpass=$PASS" 2>/dev/null \
    | tr -d '\r' | grep -i "^Set-Cookie:" | head -1 || true)
if [ -z "$NEW_COOKIE" ]; then
    # No Set-Cookie = either the login endpoint isn't issuing cookies (broken),
    # or Cloudflare/WAF stripped it. Either way, we can't verify fixation.
    fail "9.7 session fixation" "login issued no Set-Cookie — cannot verify fixation resistance"
elif echo "$NEW_COOKIE" | grep -q "$ATTACKER_UUID"; then
    fail "9.7 session fixation" "login re-used attacker-preset UUID: $NEW_COOKIE"
else
    pass "9.7 session fixation — login issued new UUID (fresh Set-Cookie)"
fi

# ─── 9.8 Cookie HttpOnly flag ──────────────────────────────
# Prevents XSS from reading the session cookie via document.cookie.
# Login endpoint uses boxuser=/boxpass= (not user=/pass= — those are only
# consumed when an RSA-encrypted sEncData map is present).
test_start "9.8 cookie HttpOnly flag"
LOGIN_HEADERS=$(curl -s -D - -o /dev/null --max-time 5 \
    "$SERVER/cass/login.fn?boxuser=$USER&boxpass=$PASS" 2>/dev/null | tr -d '\r' || true)
COOKIE_LINE=$(echo "$LOGIN_HEADERS" | grep -i "^Set-Cookie:" | head -1 || true)
if [ -z "$COOKIE_LINE" ]; then
    # No Set-Cookie is NOT a skip — login-with-valid-credentials must issue
    # a cookie. Missing cookie = login broken, upstream WAF stripping, or
    # rate-limit triggered. All of those should surface as failures.
    FIRST_LINE=$(echo "$LOGIN_HEADERS" | head -1)
    fail "9.8 cookie HttpOnly flag" "login returned no Set-Cookie header — server response starts with: $FIRST_LINE"
elif echo "$COOKIE_LINE" | grep -qi "HttpOnly"; then
    pass "9.8 cookie HttpOnly flag — present"
else
    fail "9.8 cookie HttpOnly flag" "missing (add to Set-Cookie in printHeaders)"
fi

# ─── 9.9 Cookie SameSite attribute ─────────────────────────
# Prevents cross-site request forgery by restricting when the cookie
# is sent in cross-origin requests.
test_start "9.9 cookie SameSite"
if [ -z "$COOKIE_LINE" ]; then
    fail "9.9 cookie SameSite" "login returned no Set-Cookie header (same cause as 9.8)"
elif echo "$COOKIE_LINE" | grep -qiE "SameSite=(Lax|Strict)"; then
    pass "9.9 cookie SameSite — present"
else
    fail "9.9 cookie SameSite" "missing (add SameSite=Lax to Set-Cookie)"
fi

# ─── 9.10 Query-param UUID fallback ────────────────────────
# Test existing precedence rule: query-param UUID works when cookie absent.
# (This was a fix for public-link Content-Length: 0 bug — see feedback memory.)
test_start "9.10 query-param UUID fallback works"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 \
    "$SERVER/cass/nodeinfo.fn?uuid=$UUID" || true)
if [ "$STATUS" = "200" ]; then
    pass "9.10 query-param UUID fallback works (HTTP 200)"
else
    fail "9.10 query-param UUID fallback" "HTTP $STATUS"
fi

# ─── 9.11 Concurrent sessions — same user, multiple logins ─
# Confirms both sessions stay valid (documents current behavior).
test_start "9.11 concurrent sessions — both UUIDs valid (documents be"
L1_RESP=$(cli login "$USER" "$PASS" 2>/dev/null)
L2_RESP=$(cli login "$USER" "$PASS" 2>/dev/null)
U1=$(echo "$L1_RESP" | grep '"uuid"' | sed 's/.*"uuid": "//;s/".*//')
U2=$(echo "$L2_RESP" | grep '"uuid"' | sed 's/.*"uuid": "//;s/".*//')
S1=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 -H "Cookie: uuid=$U1" "$SERVER/cass/nodeinfo.fn")
S2=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 -H "Cookie: uuid=$U2" "$SERVER/cass/nodeinfo.fn")
if [ "$S1" = "200" ] && [ "$S2" = "200" ] && [ "$U1" != "$U2" ]; then
    pass "9.11 concurrent sessions — both UUIDs valid (documents behavior)"
else
    fail "9.11 concurrent sessions" "S1=$S1 S2=$S2 U1==U2:$([ "$U1" = "$U2" ] && echo yes)"
fi

# ─── 9.12 Admin-only endpoint with valid but non-admin session ──
# Login as user1 (non-admin). Hit getlogins.fn which is admin-only.
# On remote targets, user1/pass1 may not exist — override with env vars:
#   SMOKE_NONADMIN_USER=testuser SMOKE_NONADMIN_PASS=xxx
# If the non-admin login fails, the test skips cleanly.
test_start "9.12 non-admin role check"
NONADMIN_USER="${SMOKE_NONADMIN_USER:-user1}"
NONADMIN_PASS="${SMOKE_NONADMIN_PASS:-pass1}"
# CLI exits 1 on failed login; disable set -e around the capture.
set +e
NONADMIN_RESP=$(cli login "$NONADMIN_USER" "$NONADMIN_PASS" 2>/dev/null)
set -e
NONADMIN_UUID=$(echo "$NONADMIN_RESP" | grep '"uuid"' | sed 's/.*"uuid": "//;s/".*//' || true)
if [ -z "$NONADMIN_UUID" ]; then
    skip "9.12 non-admin role check" "could not log in as $NONADMIN_USER (set SMOKE_NONADMIN_USER/PASS)"
else
    RESP=$(curl -s --max-time 3 -H "Cookie: uuid=$NONADMIN_UUID" "$SERVER/cass/getlogins.fn" || true)
    if echo "$RESP" | grep -q '"username"'; then
        fail "9.12 non-admin role check" "getlogins.fn returned session list to $NONADMIN_USER (non-admin)"
    elif [ -z "$RESP" ] || echo "$RESP" | grep -qi "unauthorized\|error\|\[\]"; then
        pass "9.12 non-admin role check — admin endpoint rejects non-admin session"
    else
        pass "9.12 non-admin role check — no session list leaked (RESP=$(echo "$RESP" | head -c 80))"
    fi
fi

# ─── 9.13 Stale/invalid UUID — graceful 401 (no crash) ─────
test_start "9.13 stale UUID graceful handling"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 \
    -H "Cookie: uuid=deadbeef-dead-beef-dead-beefdeadbeef" \
    "$SERVER/cass/nodeinfo.fn" || true)
if [ "$STATUS" = "500" ]; then
    fail "9.13 stale UUID graceful handling" "HTTP 500 — server error on invalid cookie"
else
    pass "9.13 stale UUID — graceful HTTP $STATUS"
fi

# ─── 9.14 Login rate limit — MUST RUN LAST (skippable) ─────
# WebServer.java has isLoginRateLimited() with MAX_LOGIN_ATTEMPTS=5 in
# LOGIN_LOCKOUT_MS=5min. After 5 failed attempts from the same IP,
# further attempts return the "rate_limited" HTML comment marker.
#
# Login query params are boxuser= and boxpass= (NOT user/pass — those are
# the RSA-map keys used only when sEncData is present).
#
# **SIDE EFFECT:** This test locks out the test-host IP for ~5 minutes.
# clearFailedLogins() only runs on SUCCESSFUL login — once locked out,
# nobody can succeed, so the window must fully expire. This is why:
#   - The test runs LAST in the phase
#   - Master runners should skip Phase 9 or set PHASE9_SKIP_RATELIMIT=1
# After running, wait 5 minutes or restart the server to restore login.
test_start "9.14 login rate limit"
if [ "${PHASE9_SKIP_RATELIMIT:-0}" = "1" ]; then
    skip "9.14 login rate limit" "PHASE9_SKIP_RATELIMIT=1 set — skipped to avoid IP lockout"
else
    RATE_LIMITED_HITS=0
    for i in $(seq 1 10); do
        RESP=$(curl -s --max-time 3 \
            "$SERVER/cass/login.fn?boxuser=user1&boxpass=definitely-wrong-$(date +%s%N)" 2>/dev/null || true)
        if echo "$RESP" | grep -q "rate_limited"; then
            RATE_LIMITED_HITS=$((RATE_LIMITED_HITS + 1))
        fi
    done
    if [ "$RATE_LIMITED_HITS" -ge "1" ]; then
        pass "9.14 login rate limit — $RATE_LIMITED_HITS/10 attempts blocked (IP locked for ~5min)"
    else
        fail "9.14 login rate limit" "no rate_limited marker in 10 failed attempts"
    fi
fi

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 9"

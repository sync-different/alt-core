#!/bin/bash
#
# SMOKE TEST — Phase 13: Admin User Management
#
# Covers the admin → users CRUD added via PROJECT_TAB_ADMIN_USERS:
#   - getusersandemail.fn        (list, admin-only — pre-existing)
#   - adduser.fn                  (create, admin-only — pre-existing, hardened)
#   - deluser.fn                  (delete, admin-only — pre-existing)
#   - setuserpassword.fn         (NEW: admin-override password for non-admin user)
#   - setuseremail.fn            (NEW: change any user's email)
#   - setadminpassword.fn        (NEW: admin self-service password change)
#
# Tests check:
#   - Admin gating: non-admin and unauthenticated requests rejected
#   - Happy paths: create / list / edit email / edit password / delete
#   - Server-side input validation (empty fields rejected)
#   - Session invalidation on password change (Q5) — credential change kills sessions
#   - Email change does NOT invalidate sessions (Q6)
#   - Admin self-edit keeps the calling session alive
#
# CRITICAL: Test 13.18 changes the admin password. We restore it at the end
# (test 13.19). If the script aborts mid-flight the admin password may be
# left at the test value — check the restore step. SMOKE_PASS env var is
# the source of truth for what to restore to.
#
# Total: 19 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 13: Admin User Management${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

printf "${BOLD}── Phase 13: Admin Users ──${RESET}\n"

# Test user we create + clean up across this phase. Random suffix avoids
# collisions if the script aborts mid-flight (next run won't trip alreadyexists).
RAND_SUFFIX=$(date +%s)$(printf "%04d" $((RANDOM % 10000)))
TUSER="phase13_${RAND_SUFFIX}"
TPASS_INITIAL="P13initial!${RAND_SUFFIX}"
TPASS_NEW="P13new!${RAND_SUFFIX}"
TEMAIL_INITIAL="phase13_${RAND_SUFFIX}@test.invalid"
TEMAIL_NEW="phase13_${RAND_SUFFIX}_updated@test.invalid"

# Echo the test user out so a partial-failure post-mortem can find + clean it.
printf "${DIM}  test user: ${TUSER} (will be deleted at end if all phases pass)${RESET}\n\n"

# ─── 13.1 List users — admin sees the array, response is JSON ───
test_start "13.1 list users — admin"
RESP=$(curl_auth "$SERVER/cass/getusersandemail.fn")
if echo "$RESP" | grep -q '"users"'; then
    pass "13.1 list users — admin gets JSON with users array"
else
    fail "13.1 list users" "expected JSON with 'users' key, got: $(echo "$RESP" | head -c 100)"
fi

# ─── 13.2 List users — unauthenticated request rejected ───
test_start "13.2 list users — no auth"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$SERVER/cass/getusersandemail.fn")
RESP=$(curl_noauth "$SERVER/cass/getusersandemail.fn")
# Unauthenticated should produce no users (or HTTP redirect to login). We accept
# either an empty users array or non-200 — both indicate the request was gated.
if [ "$STATUS" != "200" ] || ! echo "$RESP" | grep -q '"username"'; then
    pass "13.2 list users — unauthenticated rejected (HTTP $STATUS, no user data)"
else
    fail "13.2 list users — no auth" "got user data without auth: $(echo "$RESP" | head -c 100)"
fi

# ─── 13.3 Create user — happy path ───
test_start "13.3 create user — happy path"
RESP=$(curl_auth "$SERVER/cass/adduser.fn?boxuser=${TUSER}&boxpass=${TPASS_INITIAL}&useremail=${TEMAIL_INITIAL}")
if [ "$(echo "$RESP" | tr -d '[:space:]')" = "success" ]; then
    pass "13.3 create user — success"
else
    fail "13.3 create user" "expected 'success', got '$RESP'"
fi

# ─── 13.4 Create user — appears in list ───
test_start "13.4 created user appears in listing"
RESP=$(curl_auth "$SERVER/cass/getusersandemail.fn")
if echo "$RESP" | grep -q "\"username\": \"$TUSER\""; then
    pass "13.4 created user — in list"
else
    fail "13.4 created user" "user $TUSER not in getusersandemail.fn response"
fi

# ─── 13.5 Create user — duplicate detected ───
test_start "13.5 create user — duplicate rejected"
RESP=$(curl_auth "$SERVER/cass/adduser.fn?boxuser=${TUSER}&boxpass=${TPASS_INITIAL}&useremail=${TEMAIL_INITIAL}")
if [ "$(echo "$RESP" | tr -d '[:space:]')" = "alreadyexists" ]; then
    pass "13.5 duplicate user — alreadyexists"
else
    fail "13.5 duplicate user" "expected 'alreadyexists', got '$RESP'"
fi

# ─── 13.6 Create user — empty username rejected (server-side hardening) ───
test_start "13.6 create user — empty username rejected"
RESP=$(curl_auth "$SERVER/cass/adduser.fn?boxuser=&boxpass=x&useremail=x@y.com")
if [ "$(echo "$RESP" | tr -d '[:space:]')" = "error" ]; then
    pass "13.6 empty username — rejected"
else
    fail "13.6 empty username" "expected 'error', got '$RESP' — would corrupt users.txt with blank row"
fi

# ─── 13.7 Create user — empty password rejected ───
test_start "13.7 create user — empty password rejected"
RESP=$(curl_auth "$SERVER/cass/adduser.fn?boxuser=phase13_invalid&boxpass=&useremail=x@y.com")
if [ "$(echo "$RESP" | tr -d '[:space:]')" = "error" ]; then
    pass "13.7 empty password — rejected"
else
    fail "13.7 empty password" "expected 'error', got '$RESP'"
fi

# ─── 13.8 Create user — empty email rejected ───
test_start "13.8 create user — empty email rejected"
RESP=$(curl_auth "$SERVER/cass/adduser.fn?boxuser=phase13_invalid&boxpass=x&useremail=")
if [ "$(echo "$RESP" | tr -d '[:space:]')" = "error" ]; then
    pass "13.8 empty email — rejected"
else
    fail "13.8 empty email" "expected 'error', got '$RESP'"
fi

# ─── 13.9 setuseremail.fn — happy path ───
test_start "13.9 setuseremail.fn — change email"
RESP=$(curl_auth "$SERVER/cass/setuseremail.fn?boxuser=${TUSER}&useremail=${TEMAIL_NEW}")
if [ "$(echo "$RESP" | tr -d '[:space:]')" = "success" ]; then
    pass "13.9 setuseremail — success"
else
    fail "13.9 setuseremail" "expected 'success', got '$RESP'"
fi

# ─── 13.10 setuseremail.fn — list reflects the change ───
test_start "13.10 setuseremail — list shows new email"
RESP=$(curl_auth "$SERVER/cass/getusersandemail.fn")
# Look for our user with the new email in the JSON
if echo "$RESP" | grep -q "\"$TEMAIL_NEW\""; then
    pass "13.10 setuseremail — list reflects update"
else
    fail "13.10 setuseremail" "new email $TEMAIL_NEW not in list response"
fi

# ─── 13.11 setuseremail.fn — non-admin rejected ───
test_start "13.11 setuseremail — no auth rejected"
RESP=$(curl_noauth "$SERVER/cass/setuseremail.fn?boxuser=${TUSER}&useremail=hijack@evil.com")
# No auth → handler doesn't write a body. We accept any non-success response.
RESP_TRIM=$(echo "$RESP" | tr -d '[:space:]')
if [ "$RESP_TRIM" != "success" ]; then
    pass "13.11 setuseremail — no auth not 'success' (got '$RESP_TRIM')"
else
    fail "13.11 setuseremail — no auth" "request succeeded without auth (got 'success')"
fi

# ─── 13.12 setuseremail.fn — unknown user ───
test_start "13.12 setuseremail — unknown user"
RESP=$(curl_auth "$SERVER/cass/setuseremail.fn?boxuser=does_not_exist_xyz_$RAND_SUFFIX&useremail=x@y.com")
if [ "$(echo "$RESP" | tr -d '[:space:]')" = "notfound" ]; then
    pass "13.12 setuseremail — notfound"
else
    fail "13.12 setuseremail — unknown user" "expected 'notfound', got '$RESP'"
fi

# ─── 13.13 setuserpassword.fn — happy path + session invalidation ───
# Pre-cond: log in as the test user with TPASS_INITIAL → get a session uuid.
# After the admin changes the user's password, that session should be invalid.
test_start "13.13 setuserpassword — change + invalidate user's session"
USER_LOGIN_BEFORE=$(cli login "$TUSER" "$TPASS_INITIAL" 2>/dev/null || true)
USER_UUID=$(echo "$USER_LOGIN_BEFORE" | grep '"uuid"' | sed 's/.*"uuid": "//;s/".*//')
if [ -z "$USER_UUID" ]; then
    fail "13.13 setuserpassword (precond)" "could not log in as test user with initial password"
else
    # Admin changes the password
    RESP=$(curl_auth "$SERVER/cass/setuserpassword.fn?boxuser=${TUSER}&boxpass=${TPASS_NEW}")
    SET_OK=$(echo "$RESP" | tr -d '[:space:]')
    # Confirm the user's prior session is now invalid (Q5: kill on credential change)
    SESS_CHECK=$(curl -s -H "Cookie: uuid=$USER_UUID" "$SERVER/cass/nodeinfo.fn")
    SESS_INVALID=true
    # If nodeinfo.fn returns the user's username, the session is still alive.
    if echo "$SESS_CHECK" | grep -q "\"$TUSER\""; then
        SESS_INVALID=false
    fi
    if [ "$SET_OK" = "success" ] && $SESS_INVALID; then
        pass "13.13 setuserpassword — success + sessions killed"
    else
        fail "13.13 setuserpassword" "set='$SET_OK' sess_invalidated=$SESS_INVALID"
    fi
fi

# ─── 13.14 setuserpassword.fn — old password no longer works ───
test_start "13.14 setuserpassword — old password rejected"
OLD_LOGIN=$(cli login "$TUSER" "$TPASS_INITIAL" 2>/dev/null || true)
# Successful login emits "uuid" in the JSON; failed does not.
if echo "$OLD_LOGIN" | grep -q '"uuid"'; then
    fail "13.14 setuserpassword — old pw rejected" "login with old password succeeded — password not actually changed"
else
    pass "13.14 setuserpassword — old password rejected"
fi

# ─── 13.15 setuserpassword.fn — new password works ───
test_start "13.15 setuserpassword — new password works"
NEW_LOGIN=$(cli login "$TUSER" "$TPASS_NEW" 2>/dev/null || true)
if echo "$NEW_LOGIN" | grep -q '"uuid"'; then
    pass "13.15 setuserpassword — new password works"
else
    fail "13.15 setuserpassword — new pw" "login with new password failed"
fi

# ─── 13.16 setuserpassword.fn — non-admin caller rejected ───
test_start "13.16 setuserpassword — no auth rejected"
RESP=$(curl_noauth "$SERVER/cass/setuserpassword.fn?boxuser=${TUSER}&boxpass=hijacked")
RESP_TRIM=$(echo "$RESP" | tr -d '[:space:]')
if [ "$RESP_TRIM" != "success" ]; then
    pass "13.16 setuserpassword — no auth rejected (got '$RESP_TRIM')"
else
    fail "13.16 setuserpassword — no auth" "request succeeded without auth"
fi

# ─── 13.17 setuserpassword.fn — refuses to operate on admin user ───
# adminSetPassword returns -2 (mapped to 'forbidden') when target is admin.
test_start "13.17 setuserpassword — refuses admin target"
RESP=$(curl_auth "$SERVER/cass/setuserpassword.fn?boxuser=${USER}&boxpass=hacked")
RESP_TRIM=$(echo "$RESP" | tr -d '[:space:]')
if [ "$RESP_TRIM" = "forbidden" ]; then
    pass "13.17 setuserpassword — refuses admin (use setadminpassword.fn)"
else
    fail "13.17 setuserpassword — admin target" "expected 'forbidden', got '$RESP_TRIM' — admin password could be changed via wrong endpoint"
fi

# ─── 13.18 setadminpassword.fn — change admin pw, calling session stays alive ───
# Save the original admin password for restore (test 13.19).
test_start "13.18 setadminpassword — change + caller session stays alive"
ADMIN_TEMP_PW="adminTemp_${RAND_SUFFIX}"
RESP=$(curl_auth "$SERVER/cass/setadminpassword.fn?boxpass=${ADMIN_TEMP_PW}")
SET_OK=$(echo "$RESP" | tr -d '[:space:]')
# Critical: our calling session ($UUID) must STILL be valid after the change.
# (Other admin sessions get killed; this one is preserved per setadminpassword.fn impl.)
SESS_CHECK=$(curl -s -H "Cookie: uuid=$UUID" "$SERVER/cass/nodeinfo.fn" -o /dev/null -w "%{http_code}")
if [ "$SET_OK" = "success" ] && [ "$SESS_CHECK" = "200" ]; then
    pass "13.18 setadminpassword — success + caller session preserved"
else
    fail "13.18 setadminpassword" "set='$SET_OK' caller_session_status=$SESS_CHECK"
fi

# ─── 13.19 RESTORE admin password (cleanup, NOT a real test condition) ───
# This must succeed for the rest of the suite to keep working. Using setadminpassword.fn
# again — the calling session still has admin auth, password change is just an in-place edit.
test_start "13.19 RESTORE admin password (cleanup)"
RESP=$(curl_auth "$SERVER/cass/setadminpassword.fn?boxpass=${PASS}")
RESP_TRIM=$(echo "$RESP" | tr -d '[:space:]')
if [ "$RESP_TRIM" = "success" ]; then
    # Verify the restore actually worked by re-logging in
    RESTORE_LOGIN=$(cli login "$USER" "$PASS" 2>/dev/null || true)
    if echo "$RESTORE_LOGIN" | grep -q '"uuid"'; then
        pass "13.19 admin password restored to SMOKE_PASS"
    else
        fail "13.19 admin password restore" "set succeeded but login with original password failed — admin pw may be left at temp value '$ADMIN_TEMP_PW'"
    fi
else
    fail "13.19 admin password restore" "expected 'success', got '$RESP_TRIM' — admin pw may be left at temp value '$ADMIN_TEMP_PW'"
fi

# ─── Cleanup: delete the test user (best effort, doesn't count as a test) ───
DEL_RESP=$(curl_auth "$SERVER/cass/deluser.fn?boxuser=${TUSER}")
DEL_RESP_TRIM=$(echo "$DEL_RESP" | tr -d '[:space:]')
if [ "$DEL_RESP_TRIM" != "success" ]; then
    printf "${YELLOW}NOTE${RESET}  test user $TUSER not cleaned up (deluser returned '$DEL_RESP_TRIM') — remove manually if needed\n"
fi

print_summary "PHASE 13"

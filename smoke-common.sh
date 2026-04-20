#!/bin/bash
#
# SMOKE TEST — Shared infrastructure for all phase scripts
#
# Sources: variables, helpers, preflight checks, and authentication.
# Each phase script sources this file first.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLI_JAR="$SCRIPT_DIR/alt-core-cli/target/alt-core-cli.jar"

# Server URL: override with SMOKE_URL env var to run against a remote/prod box.
#   SMOKE_URL=https://alt.example.com ./smoke-test.sh
#   SMOKE_URL=http://192.168.1.50:8081 ./smoke-test-phase3.sh
# When unset, defaults to localhost (DEV/PROD local-bundle mode).
SERVER="${SMOKE_URL:-http://localhost:8081}"

# Credentials can be overridden too (useful for per-env test accounts).
USER="${SMOKE_USER:-admin}"
PASS="${SMOKE_PASS:-valid}"

# Parse flags
VERBOSE=false
COLOR=true
for arg in "$@"; do
    case "$arg" in
        --verbose) VERBOSE=true ;;
        --no-color) COLOR=false ;;
    esac
done

# Colors
if $COLOR; then
    GREEN="\033[32m"
    RED="\033[31m"
    YELLOW="\033[33m"
    CYAN="\033[36m"
    DIM="\033[2m"
    BOLD="\033[1m"
    RESET="\033[0m"
else
    GREEN="" RED="" YELLOW="" CYAN="" DIM="" BOLD="" RESET=""
fi

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
RESULTS=()

# ─── Timing ───────────────────────────────────────────────

# Returns current epoch time in milliseconds (uses perl for sub-second precision).
_now_ms() {
    perl -MTime::HiRes=time -e 'printf "%d\n", time()*1000'
}

PHASE_START=$(_now_ms)
TEST_START=$PHASE_START

# Call before each test to mark its start time.
# Prints an in-line "running..." status that gets overwritten by pass/fail.
# Pass an optional label (typically the test ID + short description) so the
# operator can tell which step is in-flight when things hang or take a while:
#   test_start "11.5 concurrent getfile.fn (1.3GB × 10)"
# If omitted, falls back to generic "running..." (backward-compatible).
test_start() {
    TEST_START=$(_now_ms)
    local LABEL="${1:-}"
    if [ -n "$LABEL" ]; then
        printf "\r  ${CYAN}▶ running: %s${RESET}%30s\r" "$LABEL" ""
    else
        printf "\r  ${CYAN}▶ running...${RESET}%60s\r" ""
    fi
}

# Compute elapsed milliseconds since TEST_START.
_test_elapsed_ms() {
    local NOW=$(_now_ms)
    echo $((NOW - TEST_START))
}

# Format milliseconds as human-readable duration: "0.123s", "4.567s", "1m 23s"
_fmt_duration() {
    local MS=$1
    local SECS=$((MS / 1000))
    local FRAC=$((MS % 1000))
    if [ "$SECS" -ge 60 ]; then
        printf "%dm %02ds" $((SECS / 60)) $((SECS % 60))
    else
        printf "%d.%03ds" "$SECS" "$FRAC"
    fi
}

# ─── Helpers ──────────────────────────────────────────────

pass() {
    local ELAPSED=$(_test_elapsed_ms)
    local DUR=$(_fmt_duration "$ELAPSED")
    PASS_COUNT=$((PASS_COUNT + 1))
    RESULTS+=("${GREEN}PASS${RESET}  $1  ${CYAN}${DUR}${RESET}")
    printf "\r%80s\r" ""
    printf "${GREEN}PASS${RESET}  %s  ${CYAN}%s${RESET}\n" "$1" "$DUR"
}

fail() {
    local ELAPSED=$(_test_elapsed_ms)
    local DUR=$(_fmt_duration "$ELAPSED")
    FAIL_COUNT=$((FAIL_COUNT + 1))
    RESULTS+=("${RED}FAIL${RESET}  $1 — $2  ${CYAN}${DUR}${RESET}")
    printf "\r%80s\r" ""
    printf "${RED}FAIL${RESET}  %s — %s  ${CYAN}%s${RESET}\n" "$1" "$2" "$DUR"
}

skip() {
    SKIP_COUNT=$((SKIP_COUNT + 1))
    RESULTS+=("${YELLOW}SKIP${RESET}  $1 — $2")
    printf "${YELLOW}SKIP${RESET}  %s — %s\n" "$1" "$2"
}

cli() {
    java -jar "$CLI_JAR" "$@" 2>&1
}

# Call from a phase script after sourcing smoke-common.sh to skip the phase
# entirely when running in REMOTE mode (SMOKE_URL set). Use this in phases
# that require local filesystem observation or direct access to the upload
# port 8087. Exits 0 so the master runner treats it as pass-through.
#
# Override: set SMOKE_FORCE_PHASE=1 to bypass the skip. Useful when pointing
# at a LAN server where port 8087 is reachable and local filesystem checks
# (e.g., "no file written to /etc") are tautologically safe because the
# target isn't on this machine.
skip_phase_if_remote() {
    local REASON="${1:-requires local filesystem / direct 8087 access}"
    if $SMOKE_REMOTE && [ "${SMOKE_FORCE_PHASE:-0}" != "1" ]; then
        local PHASE_LABEL="${2:-PHASE}"
        printf "\n${BOLD}${YELLOW}  %s: SKIPPED in REMOTE mode${RESET} — %s\n" "$PHASE_LABEL" "$REASON"
        printf "${DIM}       (set SMOKE_FORCE_PHASE=1 to run anyway — useful for LAN targets)${RESET}\n\n"
        # Write a zero-count summary line so the master runner knows this phase ran.
        # We can't know the phase's "real" test count from here, so report 0/0/0.
        if [ -n "${SMOKE_TOTALS_FILE:-}" ] && [ -w "$(dirname "$SMOKE_TOTALS_FILE")" ]; then
            printf "%s\t0\t0\t0\t0\t0\tPHASE_SKIPPED\n" "$PHASE_LABEL" >> "$SMOKE_TOTALS_FILE"
        fi
        exit 0
    fi
}

# Evaluate a fuzz-test HTTP status code with WAF-awareness.
# Phase 5 tests verify the server doesn't crash on malformed inputs.
# For REMOTE targets behind a WAF (Cloudflare, etc.), the WAF often blocks
# suspicious payloads before they reach the server — returning 403, 406, 400.
# That's still a safe outcome: the server never saw the payload.
#
# Usage: check_fuzz_status "test-id" "description" "$STATUS"
#   Passes on: 200 (server handled), 400/403/406 (WAF blocked, REMOTE only)
#   Fails on: 500 (server error), 000 (network issue), other 5xx
check_fuzz_status() {
    local TID="$1"
    local DESC="$2"
    local STATUS="$3"
    if [ "$STATUS" = "200" ]; then
        pass "$TID $DESC (HTTP 200, no crash)"
    elif $SMOKE_REMOTE && { [ "$STATUS" = "403" ] || [ "$STATUS" = "406" ] || [ "$STATUS" = "400" ]; }; then
        pass "$TID $DESC blocked upstream (HTTP $STATUS, likely WAF)"
    else
        fail "$TID $DESC" "HTTP $STATUS (expected 200; REMOTE tolerates 400/403/406 as WAF block)"
    fi
}

# Curl with auth cookie
curl_auth() {
    curl -s -H "Cookie: uuid=$UUID" "$@"
}

# Curl without auth
curl_noauth() {
    curl -s "$@"
}

verbose_body() {
    if $VERBOSE; then
        printf "       %s\n" "$1" | head -5
    fi
}

# Print an in-line waiting status that overwrites itself.
# Usage: wait_msg "Waiting for ProcessorService..." 5
#   $1 = message, $2 = current seconds elapsed
# Call wait_done after the loop finishes to clear the line.
wait_msg() {
    printf "\r  ${YELLOW}⏳ %s (%ds)${RESET}  " "$1" "$2"
}

wait_done() {
    printf "\r%80s\r" ""
}

print_summary() {
    local PHASE_NAME="${1:-SMOKE TEST}"
    local PHASE_END=$(_now_ms)
    local PHASE_ELAPSED=$((PHASE_END - PHASE_START))
    local PHASE_DUR=$(_fmt_duration "$PHASE_ELAPSED")
    TOTAL=$((PASS_COUNT + FAIL_COUNT + SKIP_COUNT))
    printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
    printf "${BOLD}  %s: ${GREEN}%d passed${RESET}, ${RED}%d failed${RESET}, ${YELLOW}%d skipped${RESET} / %d total  ${CYAN}[%s]${RESET}\n" \
        "$PHASE_NAME" "$PASS_COUNT" "$FAIL_COUNT" "$SKIP_COUNT" "$TOTAL" "$PHASE_DUR"
    printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

    # If the master runner set SMOKE_TOTALS_FILE, append this phase's counts
    # so the master can compute a grand total at the end. Tab-separated line:
    #   phase_name<TAB>pass<TAB>fail<TAB>skip<TAB>total<TAB>elapsed_ms
    if [ -n "${SMOKE_TOTALS_FILE:-}" ] && [ -w "$(dirname "$SMOKE_TOTALS_FILE")" ]; then
        printf "%s\t%d\t%d\t%d\t%d\t%d\n" \
            "$PHASE_NAME" "$PASS_COUNT" "$FAIL_COUNT" "$SKIP_COUNT" "$TOTAL" "$PHASE_ELAPSED" \
            >> "$SMOKE_TOTALS_FILE"
    fi

    if [ "$FAIL_COUNT" -gt 0 ]; then
        printf "${RED}${BOLD}%s FAILED${RESET}\n\n" "$PHASE_NAME"
        return 1
    else
        printf "${GREEN}${BOLD}%s PASSED${RESET}\n\n" "$PHASE_NAME"
        return 0
    fi
}

# ─── Preflight checks ────────────────────────────────────

# Check CLI jar exists
if [ ! -f "$CLI_JAR" ]; then
    printf "${RED}ERROR${RESET}: CLI jar not found at %s\n" "$CLI_JAR"
    printf "       Run ./build-cli.sh first.\n"
    exit 1
fi

# Check server is running.
# - Local (default): verify port 8081 is bound locally.
# - Remote (SMOKE_URL set): verify the URL responds to a basic request.
if [ -n "${SMOKE_URL:-}" ]; then
    PROBE_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$SMOKE_URL/cass/getsession.fn" 2>/dev/null || echo "000")
    if [ "$PROBE_CODE" = "000" ]; then
        printf "${RED}ERROR${RESET}: Cannot reach %s\n" "$SMOKE_URL"
        printf "       Check the URL, network, and TLS cert if HTTPS.\n"
        exit 1
    fi
else
    if ! lsof -ti:8081 > /dev/null 2>&1; then
        printf "${RED}ERROR${RESET}: No server running on port 8081.\n"
        printf "       Start the server first: ./run.sh\n"
        printf "       Or target a remote server with: SMOKE_URL=<url> %s\n" "$0"
        exit 1
    fi
fi

# ─── Mode detection (dev vs production vs remote) ────────
# Three modes:
#   REMOTE — SMOKE_URL set; target is a URL we don't share a filesystem with.
#            Tests that rely on local filesystem observation are skipped.
#   PROD   — app bundle at /Applications/alt-core.app with the PROD appendage
#            (~/Library/Application Support/hivebot/scrubber/).
#   DEV    — running from the repo; paths relative to SCRIPT_DIR.
#
# PROD paths reflect the appendage prefix used by Netty/ProcessorService:
#   incoming:     appendage + "../rtserver/incoming"
#   mobilebackup: appendage + "mobilebackup/upload"
#   config:       appendage + "config/"

SMOKE_REMOTE=false
PROD_APP_SUPPORT="$HOME/Library/Application Support/hivebot"

# REMOTE mode only applies when the SMOKE_URL host is NOT local — because the
# whole purpose is to skip tests that need filesystem access. If SMOKE_URL
# points at localhost/127.0.0.1, we're still local; just treat it like DEV/PROD
# so filesystem-dependent tests run.
SMK_IS_LOCAL=false
if [ -n "${SMOKE_URL:-}" ]; then
    case "$SMOKE_URL" in
        http://localhost:*|http://localhost/*|http://localhost \
        |http://127.0.0.1:*|http://127.0.0.1/*|http://127.0.0.1 \
        |http://[::1]:*|http://[::1]/*|http://[::1] \
        |https://localhost:*|https://localhost/*|https://localhost \
        |https://127.0.0.1:*|https://127.0.0.1/*|https://127.0.0.1)
            SMK_IS_LOCAL=true ;;
    esac
fi

if [ -n "${SMOKE_URL:-}" ] && ! $SMK_IS_LOCAL; then
    SMOKE_ENV="REMOTE"
    SMOKE_REMOTE=true
    # Filesystem paths aren't observable from here — set to empty strings.
    # Tests that use them must check $SMOKE_REMOTE and skip cleanly.
    INCOMING=""
    MOBILEBACKUP=""
    SCAN_CONFIG_DIR=""
elif [ -d "/Applications/alt-core.app" ] && [ -d "$PROD_APP_SUPPORT/scrubber" ]; then
    SMOKE_ENV="PROD"
    INCOMING="$PROD_APP_SUPPORT/rtserver/incoming"
    MOBILEBACKUP="$PROD_APP_SUPPORT/scrubber/mobilebackup/upload"
    SCAN_CONFIG_DIR="$PROD_APP_SUPPORT/scrubber/config"
else
    SMOKE_ENV="DEV"
    INCOMING="$SCRIPT_DIR/rtserver/incoming"
    MOBILEBACKUP="$SCRIPT_DIR/scrubber/mobilebackup/upload"
    SCAN_CONFIG_DIR="$SCRIPT_DIR/scrubber/config"
fi

printf "${BOLD}Mode:${RESET} ${CYAN}%s${RESET}  " "$SMOKE_ENV"
printf "${BOLD}Server:${RESET} %s\n" "$SERVER"
if ! $SMOKE_REMOTE; then
    printf "${BOLD}Incoming:${RESET} %s\n" "$INCOMING"
    printf "${BOLD}Mobilebackup:${RESET} %s\n" "$MOBILEBACKUP"
else
    printf "${BOLD}${YELLOW}Remote mode — filesystem-dependent tests will be skipped${RESET}\n"
fi

# Check if MOBILEBACKUP is in the FileScanner scan path.
# FileScanner only indexes files in directories listed in scan1.txt.
# If mobilebackup isn't listed, upload tests that depend on indexing will be skipped.
# In REMOTE mode, we can't read the remote filesystem — assume it's configured correctly
# and let the smoke tests themselves report missing indexing behavior.
MOBILEBACKUP_SCANNABLE=false
if $SMOKE_REMOTE; then
    MOBILEBACKUP_SCANNABLE=true
    printf "${BOLD}Scan config:${RESET} ${YELLOW}remote mode — assuming server is configured${RESET}\n"
elif [ -f "$SCAN_CONFIG_DIR/scan1.txt" ]; then
    # URL-decode the scan1.txt scandir value and check for mobilebackup path
    MOBILEBACKUP_CANONICAL=$(cd "$MOBILEBACKUP" 2>/dev/null && pwd || echo "$MOBILEBACKUP")
    SCAN_CONTENT=$(grep '^scandir=' "$SCAN_CONFIG_DIR/scan1.txt" 2>/dev/null | sed 's/^scandir=//')
    # URL-decode: %2F → /, %20 → space
    SCAN_DECODED=$(printf '%b' "$(echo "$SCAN_CONTENT" | sed 's/%/\\x/g')")
    if echo "$SCAN_DECODED" | grep -q "mobilebackup"; then
        MOBILEBACKUP_SCANNABLE=true
    fi
fi

if ! $SMOKE_REMOTE; then
    if $MOBILEBACKUP_SCANNABLE; then
        printf "${BOLD}Scan config:${RESET} ${GREEN}mobilebackup found in scan1.txt${RESET}\n"
    else
        printf "${BOLD}Scan config:${RESET} ${YELLOW}mobilebackup NOT in scan1.txt — index tests will be skipped${RESET}\n"
    fi
fi
printf "\n"

# ─── Authentication ───────────────────────────────────────

# Always set the CLI config to match $SERVER before logging in. The CLI
# persists its config to ~/.alt-core-cli — without this step, running
# REMOTE then DEV (or vice versa) would pick up stale credentials against
# the wrong target.
#
# Parse $SERVER (which is either SMOKE_URL or the default http://localhost:8081)
# into protocol/host/port for `cli config`.
SMK_PROTO=$(echo "$SERVER" | sed -E 's|^(https?)://.*|\1|')
SMK_REST=$(echo "$SERVER" | sed -E 's|^https?://||')
SMK_HOST=$(echo "$SMK_REST" | sed -E 's|[:/].*||')
SMK_PORT=$(echo "$SMK_REST" | sed -nE 's|^[^:/]+:([0-9]+).*|\1|p')
if [ -z "$SMK_PORT" ]; then
    if [ "$SMK_PROTO" = "https" ]; then SMK_PORT=443; else SMK_PORT=80; fi
fi
cli config --protocol "$SMK_PROTO" --server "$SMK_HOST" --port "$SMK_PORT" > /dev/null

# CLI exits 1 on failed login; catch it here so we print a useful error
# instead of aborting silently under `set -e`.
set +e
LOGIN_RESP=$(cli login "$USER" "$PASS")
LOGIN_EXIT=$?
set -e
if [ "$LOGIN_EXIT" = "0" ] && echo "$LOGIN_RESP" | grep -q '"success": true'; then
    UUID=$(echo "$LOGIN_RESP" | grep '"uuid"' | sed 's/.*"uuid": "//;s/".*//')
else
    printf "${RED}ABORT${RESET}: Login failed for user '%s' on %s\n" "$USER" "$SERVER"

    # Diagnose the likely cause by doing a direct probe — the CLI login
    # failure doesn't tell us WHY, so we ask login.fn directly with the same
    # creds and inspect the server's actual response.
    DIAG_RESP=$(curl -s --max-time 5 \
        "$SERVER/cass/login.fn?boxuser=$(printf '%s' "$USER" | sed 's/[^a-zA-Z0-9._-]/%2B/g')&boxpass=$(printf '%s' "$PASS" | sed 's/[^a-zA-Z0-9._-]/%2B/g')" 2>/dev/null || true)

    if echo "$DIAG_RESP" | grep -q "rate_limited"; then
        printf "\n${YELLOW}${BOLD}Diagnosis: login rate limit active${RESET}\n"
        printf "${YELLOW}The test-host IP has been rate-limited by the server (Phase 9.14 or\n"
        printf "repeated failed logins accumulate 5 attempts / 5 min / IP).${RESET}\n\n"
        printf "${BOLD}To recover:${RESET}\n"
        printf "  1. ${CYAN}Wait 5 minutes${RESET} for the lockout window to expire, OR\n"
        printf "  2. ${CYAN}Restart the server${RESET} (in-memory rate-limit table is cleared), OR\n"
        printf "  3. ${CYAN}Set PHASE9_SKIP_RATELIMIT=1${RESET} to skip test 9.14 in future runs:\n"
        printf "     ${DIM}PHASE9_SKIP_RATELIMIT=1 ./smoke-test.sh${RESET}\n"
    elif echo "$DIAG_RESP" | grep -q "Invalid Username or Password\|Invalid"; then
        printf "\n${YELLOW}${BOLD}Diagnosis: credentials rejected${RESET}\n"
        printf "${YELLOW}The server accepted the login request but rejected the username or password.${RESET}\n\n"
        printf "${BOLD}To recover:${RESET}\n"
        printf "  ${CYAN}Override credentials${RESET}: SMOKE_USER=<user> SMOKE_PASS='<pass>' ./smoke-test.sh\n"
        printf "  ${DIM}Tip: quote password with single-quotes if it contains \$ — e.g. 'Valid\$\$26'${RESET}\n"
    elif [ -z "$DIAG_RESP" ]; then
        printf "\n${YELLOW}${BOLD}Diagnosis: server unreachable or empty response${RESET}\n"
        printf "${YELLOW}login.fn returned nothing — server may be down, firewalled, or behind a WAF blocking the request.${RESET}\n\n"
        printf "${BOLD}To verify:${RESET}\n"
        printf "  ${CYAN}curl -sv %s/cass/getsession.fn${RESET}\n" "$SERVER"
    else
        printf "\n${YELLOW}${BOLD}Diagnosis: unexpected response shape${RESET}\n"
        printf "${YELLOW}login.fn returned an unrecognized response — may indicate a server change or a WAF page.${RESET}\n"
        printf "${DIM}First 200 chars of response body:${RESET}\n"
        printf "${DIM}%s${RESET}\n\n" "$(echo "$DIAG_RESP" | head -c 200)"
    fi

    printf "\n${DIM}CLI login response: %s${RESET}\n" "$(echo "$LOGIN_RESP" | head -c 200)"
    exit 1
fi

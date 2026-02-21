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
SERVER="http://localhost:8081"
USER="admin"
PASS="valid"

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
# Prints an in-line "running..." message that gets overwritten by pass/fail.
test_start() {
    TEST_START=$(_now_ms)
    printf "\r  ${CYAN}▶ running...${RESET}%60s\r" ""
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

# Check server is running
if ! lsof -ti:8081 > /dev/null 2>&1; then
    printf "${RED}ERROR${RESET}: No server running on port 8081.\n"
    printf "       Start the server first: ./run.sh\n"
    exit 1
fi

# ─── Path detection (dev vs production) ──────────────────
# In production, the app bundle is at /Applications/alt-core.app/ but
# appendage points to ~/Library/Application Support/hivebot/scrubber/.
# Paths used by Netty/ProcessorService are relative to appendage:
#   incoming:     appendage + "../rtserver/incoming"
#   mobilebackup: appendage + "mobilebackup/upload"
#   config:       appendage + "config/"
# In dev mode, SCRIPT_DIR is the project root and paths are relative.

PROD_APP_SUPPORT="$HOME/Library/Application Support/hivebot"
if [ -d "/Applications/alt-core.app" ] && [ -d "$PROD_APP_SUPPORT/scrubber" ]; then
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
printf "${BOLD}Incoming:${RESET} %s\n" "$INCOMING"
printf "${BOLD}Mobilebackup:${RESET} %s\n" "$MOBILEBACKUP"

# Check if MOBILEBACKUP is in the FileScanner scan path.
# FileScanner only indexes files in directories listed in scan1.txt.
# If mobilebackup isn't listed, upload tests that depend on indexing will be skipped.
MOBILEBACKUP_SCANNABLE=false
if [ -f "$SCAN_CONFIG_DIR/scan1.txt" ]; then
    # URL-decode the scan1.txt scandir value and check for mobilebackup path
    MOBILEBACKUP_CANONICAL=$(cd "$MOBILEBACKUP" 2>/dev/null && pwd || echo "$MOBILEBACKUP")
    SCAN_CONTENT=$(grep '^scandir=' "$SCAN_CONFIG_DIR/scan1.txt" 2>/dev/null | sed 's/^scandir=//')
    # URL-decode: %2F → /, %20 → space
    SCAN_DECODED=$(printf '%b' "$(echo "$SCAN_CONTENT" | sed 's/%/\\x/g')")
    if echo "$SCAN_DECODED" | grep -q "mobilebackup"; then
        MOBILEBACKUP_SCANNABLE=true
    fi
fi

if $MOBILEBACKUP_SCANNABLE; then
    printf "${BOLD}Scan config:${RESET} ${GREEN}mobilebackup found in scan1.txt${RESET}\n"
else
    printf "${BOLD}Scan config:${RESET} ${YELLOW}mobilebackup NOT in scan1.txt — index tests will be skipped${RESET}\n"
fi
printf "\n"

# ─── Authentication ───────────────────────────────────────

LOGIN_RESP=$(cli login "$USER" "$PASS")
if echo "$LOGIN_RESP" | grep -q '"success": true'; then
    UUID=$(echo "$LOGIN_RESP" | grep '"uuid"' | sed 's/.*"uuid": "//;s/".*//')
else
    printf "${RED}ABORT${RESET}: Login failed. Cannot continue without auth.\n"
    exit 1
fi

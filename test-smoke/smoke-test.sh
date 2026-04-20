#!/bin/bash
#
# SMOKE TEST — alt-core API regression test (master runner)
#
# Runs all phase test scripts. Each phase can also be run independently.
#
# Usage:
#   ./smoke-test.sh              # Run all phases
#   ./smoke-test.sh --verbose    # Show full response bodies
#   ./smoke-test.sh --no-color   # Plain text output (for CI/logs)
#
# Individual phases:
#   ./smoke-test-phase1.sh       # Core API endpoints (26 tests)
#   ./smoke-test-phase2.sh       # Security + uiv5 (24 tests)
#   ./smoke-test-phase3.sh       # Security hardening (15 tests)
#   ./smoke-test-phase4.sh       # Upload security (10 tests)
#   ./smoke-test-phase5.sh       # Fuzz testing (15 tests)
#   ./smoke-test-phase6.sh       # Upload/download functional (14 tests)
#   ./smoke-test-phase7.sh       # File deletion (12 tests)
#   ./smoke-test-phase8.sh       # Upload content & policy validation (12 tests)
#   ./smoke-test-phase9.sh       # Session & authentication lifecycle (14 tests)
#   ./smoke-test-phase10.sh      # HTTP protocol & response hardening (13 tests)
#   ./smoke-test-phase11.sh      # DoS resistance & rate limiting (8 tests)
#   ./smoke-test-phase12.sh      # Scanner regression corpus (10 tests)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Forward flags to phase scripts
ARGS="$*"

printf "\n\033[1m\033[36m═══════════════════════════════════════════════════\033[0m\n"
printf "\033[1m\033[36m  alt-core SMOKE TEST — All Phases\033[0m\n"
printf "\033[1m\033[36m═══════════════════════════════════════════════════\033[0m\n"

ALL_PASSED=true
_now_ms() {
    perl -MTime::HiRes=time -e 'printf "%d\n", time()*1000'
}

SUITE_START=$(_now_ms)

# Each phase writes its pass/fail/skip counts to this file via print_summary()
# in smoke-common.sh. We aggregate at the end for a grand total.
SMOKE_TOTALS_FILE=$(mktemp)
export SMOKE_TOTALS_FILE
# Clean up on exit (even if something goes wrong mid-suite)
trap 'rm -f "$SMOKE_TOTALS_FILE"' EXIT

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

run_phase() {
    local PHASE_SCRIPT="$1"
    local PHASE_NAME="$2"

    if [ ! -f "$SCRIPT_DIR/$PHASE_SCRIPT" ]; then
        printf "\033[31mERROR\033[0m: %s not found\n" "$PHASE_SCRIPT"
        ALL_PASSED=false
        return
    fi

    # Run the phase script and capture its output
    if bash "$SCRIPT_DIR/$PHASE_SCRIPT" $ARGS; then
        : # Phase passed
    else
        ALL_PASSED=false
    fi
}

# Phase 9 runs LAST because its rate-limit test (9.14) intentionally locks
# out the test-host IP for ~5 minutes. Every phase script sources
# smoke-common.sh which starts with `cli login admin valid` — if the IP is
# locked out, all subsequent phases would fail at the login step.
#
# Restart the server after the suite to clear the lockout for the next run.
# Set PHASE9_SKIP_RATELIMIT=1 in the environment to skip 9.14 instead.

# Run all phases sequentially — Phase 9 deferred to end (see comment above)
run_phase "smoke-test-phase1.sh" "Phase 1"
run_phase "smoke-test-phase2.sh" "Phase 2"
run_phase "smoke-test-phase3.sh" "Phase 3"
run_phase "smoke-test-phase4.sh" "Phase 4"
run_phase "smoke-test-phase5.sh" "Phase 5"
run_phase "smoke-test-phase6.sh" "Phase 6"
run_phase "smoke-test-phase7.sh" "Phase 7"
run_phase "smoke-test-phase8.sh" "Phase 8"
run_phase "smoke-test-phase10.sh" "Phase 10"
run_phase "smoke-test-phase11.sh" "Phase 11"
run_phase "smoke-test-phase12.sh" "Phase 12"
run_phase "smoke-test-phase9.sh" "Phase 9"

# Final summary — aggregate from the totals file each phase wrote to.
SUITE_END=$(_now_ms)
SUITE_ELAPSED=$((SUITE_END - SUITE_START))
SUITE_DUR=$(_fmt_duration "$SUITE_ELAPSED")

TOTAL_PASS=0
TOTAL_FAIL=0
TOTAL_SKIP=0
TOTAL_TESTS=0
PHASES_RAN=0
PHASES_SKIPPED=0

printf "\n\033[1m\033[36m═══════════════════════════════════════════════════\033[0m\n"
printf "\033[1m\033[36m  ALL PHASES COMPLETE  \033[0m\033[36m[%s total]\033[0m\n" "$SUITE_DUR"
printf "\033[1m\033[36m═══════════════════════════════════════════════════\033[0m\n\n"

if [ -s "$SMOKE_TOTALS_FILE" ]; then
    # Per-phase breakdown
    printf "\033[1mPer-phase breakdown:\033[0m\n"
    while IFS=$'\t' read -r NAME PASS FAIL SKIP TOTAL ELAPSED_MS MARKER; do
        if [ "${MARKER:-}" = "PHASE_SKIPPED" ]; then
            printf "  \033[33m%-10s\033[0m  \033[2m(entirely skipped)\033[0m\n" "$NAME"
            PHASES_SKIPPED=$((PHASES_SKIPPED + 1))
        else
            PHASE_DUR=$(_fmt_duration "$ELAPSED_MS")
            printf "  \033[1m%-10s\033[0m  \033[32m%3d pass\033[0m  \033[31m%3d fail\033[0m  \033[33m%3d skip\033[0m  / %3d  \033[36m[%s]\033[0m\n" \
                "$NAME" "$PASS" "$FAIL" "$SKIP" "$TOTAL" "$PHASE_DUR"
            TOTAL_PASS=$((TOTAL_PASS + PASS))
            TOTAL_FAIL=$((TOTAL_FAIL + FAIL))
            TOTAL_SKIP=$((TOTAL_SKIP + SKIP))
            TOTAL_TESTS=$((TOTAL_TESTS + TOTAL))
            PHASES_RAN=$((PHASES_RAN + 1))
        fi
    done < "$SMOKE_TOTALS_FILE"

    # Grand total line
    printf "\n\033[1m  %-10s  \033[32m%3d pass\033[0m  \033[31m%3d fail\033[0m  \033[33m%3d skip\033[0m  / %3d  \033[36m[%s]\033[0m\n\n" \
        "GRAND TOTAL" "$TOTAL_PASS" "$TOTAL_FAIL" "$TOTAL_SKIP" "$TOTAL_TESTS" "$SUITE_DUR"

    if [ "$PHASES_SKIPPED" -gt 0 ]; then
        printf "\033[2m  (%d phase(s) skipped entirely — typically REMOTE mode excluding filesystem-dependent phases)\033[0m\n\n" "$PHASES_SKIPPED"
    fi
fi

if $ALL_PASSED && [ "$TOTAL_FAIL" -eq 0 ]; then
    printf "\033[32m\033[1mALL SMOKE TESTS PASSED\033[0m  \033[36m(%s)\033[0m\n\n" "$SUITE_DUR"
    exit 0
else
    printf "\033[31m\033[1mSOME PHASES FAILED\033[0m  \033[36m(%s)\033[0m\n\n" "$SUITE_DUR"
    exit 1
fi

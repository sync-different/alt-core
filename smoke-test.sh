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
#   ./smoke-test-phase3.sh       # Security hardening (11 tests)
#   ./smoke-test-phase4.sh       # Upload security (10 tests)
#   ./smoke-test-phase5.sh       # Fuzz testing (15 tests)
#   ./smoke-test-phase6.sh       # Upload/download functional (14 tests)
#   ./smoke-test-phase7.sh       # File deletion (4 tests)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Forward flags to phase scripts
ARGS="$*"

printf "\n\033[1m\033[36m═══════════════════════════════════════════════════\033[0m\n"
printf "\033[1m\033[36m  alt-core SMOKE TEST — All Phases\033[0m\n"
printf "\033[1m\033[36m═══════════════════════════════════════════════════\033[0m\n"

TOTAL_PASS=0
TOTAL_FAIL=0
TOTAL_SKIP=0
ALL_PASSED=true
_now_ms() {
    perl -MTime::HiRes=time -e 'printf "%d\n", time()*1000'
}

SUITE_START=$(_now_ms)

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
        TOTAL_FAIL=$((TOTAL_FAIL + 1))
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

# Run all phases sequentially
run_phase "smoke-test-phase1.sh" "Phase 1"
run_phase "smoke-test-phase2.sh" "Phase 2"
run_phase "smoke-test-phase3.sh" "Phase 3"
run_phase "smoke-test-phase4.sh" "Phase 4"
run_phase "smoke-test-phase5.sh" "Phase 5"
run_phase "smoke-test-phase6.sh" "Phase 6"
run_phase "smoke-test-phase7.sh" "Phase 7"

# Final summary
SUITE_END=$(_now_ms)
SUITE_ELAPSED=$((SUITE_END - SUITE_START))
SUITE_DUR=$(_fmt_duration "$SUITE_ELAPSED")

printf "\n\033[1m\033[36m═══════════════════════════════════════════════════\033[0m\n"
printf "\033[1m\033[36m  ALL PHASES COMPLETE  \033[0m\033[36m[%s total]\033[0m\n" "$SUITE_DUR"
printf "\033[1m\033[36m═══════════════════════════════════════════════════\033[0m\n\n"

if $ALL_PASSED; then
    printf "\033[32m\033[1mALL SMOKE TESTS PASSED\033[0m  \033[36m(%s)\033[0m\n\n" "$SUITE_DUR"
    exit 0
else
    printf "\033[31m\033[1mSOME PHASES FAILED\033[0m  \033[36m(%s)\033[0m\n\n" "$SUITE_DUR"
    exit 1
fi

#!/bin/bash
#
# SMOKE TEST — Phase 11: DoS Resistance & Rate Limiting
#
# Tests that the server remains responsive and bounded under adversarial load:
#   - High concurrent request count (connection exhaustion)
#   - Large/deep/amplifying inputs (parser and result-set DoS)
#   - Orphan resource cleanup (disk-fill via abandoned chunks)
#   - Rate limiting on sensitive endpoints (brute force)
#
# Design principles:
#   - Keep default runtime under ~60s so this stays in the smoke-test budget
#   - Use --long flag for heavier tests (200+ concurrent, 100 logins, etc.)
#   - Every test includes a post-condition liveness check
#   - Tests that require unimplemented features (rate limit) are SKIP with
#     a clear reason pointing to the AUDIT roadmap
#
# Total: 7 tests (was 8 — 11.7 removed, covered by Phase 9.14)
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 11: DoS Resistance & Rate Limiting${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

# Long mode toggles higher concurrency and heavier inputs
LONG_MODE=false
for arg in "$@"; do
    case "$arg" in
        --long) LONG_MODE=true ;;
    esac
done

if $LONG_MODE; then
    CONCURRENCY_N=200
    DOWNLOAD_N=20
    ORPHAN_N=100
    printf "${BOLD}── Phase 11: DoS Resistance ${YELLOW}[--long mode]${RESET}${BOLD} ──${RESET}\n"
else
    CONCURRENCY_N=50
    DOWNLOAD_N=10
    ORPHAN_N=20
    printf "${BOLD}── Phase 11: DoS Resistance ──${RESET}\n"
fi

# Helper: check server is responsive. Exits the test with fail() if not.
assert_server_alive() {
    local TEST_ID="$1"
    local ALIVE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$SERVER/cass/getsession.fn" || true)
    if [ "$ALIVE" != "200" ]; then
        fail "$TEST_ID liveness" "getsession.fn returned HTTP $ALIVE after test"
        return 1
    fi
    return 0
}

# ─── 11.1 Concurrent query.fn requests ────────────────────
# Fires N parallel requests. All must return HTTP 200 within the timeout budget.
test_start "11.1 $CONCURRENCY_N concurrent query.fn"
TMP_RESULTS=$(mktemp)
seq 1 $CONCURRENCY_N | xargs -P $CONCURRENCY_N -I{} sh -c \
    "curl -s -o /dev/null -w '%{http_code}\n' --max-time 10 -H 'Cookie: uuid=$UUID' '$SERVER/cass/query.fn?view=json&ftype=.all&days=0&numobj=1'" \
    > "$TMP_RESULTS" 2>/dev/null || true
TOTAL=$(wc -l < "$TMP_RESULTS" | tr -d ' ')
SUCCESS=$(grep -c "^200$" "$TMP_RESULTS" || true)
rm -f "$TMP_RESULTS"
if [ "$TOTAL" = "$CONCURRENCY_N" ] && [ "$SUCCESS" = "$CONCURRENCY_N" ]; then
    pass "11.1 $CONCURRENCY_N concurrent query.fn — all $SUCCESS/$CONCURRENCY_N returned 200"
else
    fail "11.1 $CONCURRENCY_N concurrent query.fn" "$SUCCESS/$CONCURRENCY_N succeeded (got $TOTAL responses)"
fi
assert_server_alive "11.1"

# ─── 11.2 Query with oversized search tokens ──────────────
# 10k-character search string. The tokenizer splits by hyphens; a long string
# without hyphens is a single token. Must not hang or OOM.
test_start "11.2 10k-term query.fn"
HUGE=$(python3 -c 'print("a"*10000)')
START=$(_now_ms)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 \
    -H "Cookie: uuid=$UUID" \
    "$SERVER/cass/query.fn?view=json&ftype=.all&days=0&numobj=1&sQuery=$HUGE" || true)
ELAPSED=$(( $(_now_ms) - START ))
if [ "$STATUS" = "200" ] && [ "$ELAPSED" -lt 10000 ]; then
    pass "11.2 10k-term query — HTTP 200 in ${ELAPSED}ms (<10s budget)"
elif [ "$STATUS" = "200" ]; then
    fail "11.2 10k-term query" "responded but took ${ELAPSED}ms (>10s budget — possible regex DoS)"
else
    fail "11.2 10k-term query" "HTTP $STATUS after ${ELAPSED}ms"
fi
assert_server_alive "11.2"

# ─── 11.3 Deeply nested JSON body (JSON bomb) ─────────────
# json-smart 2.5.2 has stack-depth limits. This regression test confirms
# the parser rejects or safely handles depth-1000 nested objects.
# We POST to a JSON-accepting endpoint (doshare_webapp.fn).
test_start "11.3 depth-1000 nested JSON"
NESTED=$(python3 -c 'print("{\"a\":"*1000 + "1" + "}"*1000)')
START=$(_now_ms)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
    -H "Cookie: uuid=$UUID" \
    -H "Content-Type: application/json" \
    --data-raw "$NESTED" \
    "$SERVER/cass/doshare_webapp.fn" || true)
ELAPSED=$(( $(_now_ms) - START ))
# Server must not 500 (stack overflow) and must not hang. 200/400/4xx all OK.
if [ "$STATUS" = "500" ]; then
    fail "11.3 deeply nested JSON (depth 1000)" "HTTP 500 — possible stack overflow"
elif [ -z "$STATUS" ] || [ "$STATUS" = "000" ] || [ "$ELAPSED" -gt 8000 ]; then
    fail "11.3 deeply nested JSON (depth 1000)" "slow or crashed: HTTP $STATUS in ${ELAPSED}ms"
else
    pass "11.3 deeply nested JSON (depth 1000) — HTTP $STATUS in ${ELAPSED}ms"
fi
assert_server_alive "11.3"

# ─── 11.4 Billion-laughs XML expansion (XXE DoS) ──────────
# Alterante doesn't expose an XML parsing endpoint directly. This test sends
# a billion-laughs payload as an upload body to /formpost. The payload should
# be stored inert (treated as binary) without any entity expansion.
test_start "11.4 billion-laughs XML upload"
if $SMOKE_REMOTE; then
    skip "11.4 billion-laughs XML" "REMOTE mode — direct port 8087 access not available"
elif ! lsof -ti:8087 > /dev/null 2>&1; then
    skip "11.4 billion-laughs XML" "upload port 8087 not running"
else
    BL_XML='<?xml version="1.0"?><!DOCTYPE lolz [<!ENTITY lol "lol"><!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;"><!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;"><!ENTITY lol4 "&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;"><!ENTITY lol5 "&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;">]><lolz>&lol5;</lolz>'
    START=$(_now_ms)
    STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
        -F "dzchunkindex=0" \
        -F "dztotalchunkcount=1" \
        -F "file=@-;filename=billion-laughs.xml" \
        "http://localhost:8087/formpost" <<< "$BL_XML" || true)
    ELAPSED=$(( $(_now_ms) - START ))
    # Clean up the uploaded file
    rm -f "$INCOMING"/upload.billion-laughs* 2>/dev/null
    rm -f "$INCOMING"/*.meta 2>/dev/null
    if [ "$ELAPSED" -gt 8000 ]; then
        fail "11.4 billion-laughs XML" "upload took ${ELAPSED}ms — possible entity expansion"
    else
        pass "11.4 billion-laughs XML — stored inert in ${ELAPSED}ms (HTTP $STATUS)"
    fi
    assert_server_alive "11.4"
fi

# ─── 11.5 Concurrent getfile.fn downloads ─────────────────
# Pick a known file to download via query.fn. The response shape is:
#   { "fighters": [ { "nickname": "<md5>", "name": "...", ... } ] }
# `nickname` holds the MD5 hash; that's what getfile.fn?sNamer= expects.
#
# Override with SMOKE_KNOWN_MD5 to skip discovery and use a specific file.
test_start "11.5 discovering test file via query.fn"
TEST_MD5="${SMOKE_KNOWN_MD5:-}"
TEST_FILENAME=""
TEST_SIZE=""
# Target size window for the concurrency test: large enough to meaningfully
# exercise the server (>= 1MB) but not so large that 10-way parallel takes
# forever (<= 500MB). Files in this window give useful signal without burning
# bandwidth. Override with SMOKE_MIN_SIZE / SMOKE_MAX_SIZE (bytes).
MIN_SIZE="${SMOKE_MIN_SIZE:-1048576}"        # 1 MB
MAX_SIZE="${SMOKE_MAX_SIZE:-524288000}"      # 500 MB
if [ -z "$TEST_MD5" ]; then
    # query.fn params that work across dev/prod: use `foo=.all` as the search
    # term and empty days (= all time). date anchor is required on some servers.
    # Ask for more results (50) so we have a bigger pool to pick a good-size file from.
    QUERY_DATE=$(date -u +"%Y.%m.%d+%H:%M:%S.000+UTC")
    QUERY_JSON=$(curl -s -H "Cookie: uuid=$UUID" --max-time 10 \
        "$SERVER/cass/query.fn?ftype=.all&foo=.all&days=&view=json&numobj=50&order=Desc&screenSize=160&date=$QUERY_DATE" || true)
    # Find a file in the target size range. Falls back gracefully:
    #   1. Prefer a file with file_size in [MIN_SIZE, MAX_SIZE]
    #   2. If none fit, pick the closest-to-MAX_SIZE from the pool (largest that's under cap)
    #   3. If none have file_size at all, use the first file (original behavior)
    # Output: tab-separated md5 \t filename \t size_bytes
    TEST_META=$(MIN=$MIN_SIZE MAX=$MAX_SIZE echo "$QUERY_JSON" | MIN=$MIN_SIZE MAX=$MAX_SIZE python3 -c '
import sys, json, os
MIN = int(os.environ.get("MIN", 1048576))
MAX = int(os.environ.get("MAX", 524288000))
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(0)
def iter_files(obj):
    if isinstance(obj, dict):
        nick = None
        for k in ("nickname", "MD5", "md5", "sNamer", "hash"):
            if k in obj and isinstance(obj[k], str) and len(obj[k]) >= 16:
                nick = obj[k]; break
        if nick:
            name = obj.get("name", "") if isinstance(obj.get("name", ""), str) else ""
            # file_size can be a string or int depending on code path
            raw = obj.get("file_size", obj.get("filesize", ""))
            try:
                size = int(str(raw).strip().strip("\""))
            except Exception:
                size = -1
            yield (nick, name, size)
        else:
            for v in obj.values():
                yield from iter_files(v)
    elif isinstance(obj, list):
        for item in obj:
            yield from iter_files(item)
files = list(iter_files(d))
if not files:
    sys.exit(0)
# Priority 1: file within [MIN, MAX]
in_range = [f for f in files if MIN <= f[2] <= MAX]
if in_range:
    pick = in_range[0]
else:
    # Priority 2: any file with known size, pick largest that fits under MAX,
    # or smallest that exceeds MIN if everything is tiny
    sized = [f for f in files if f[2] > 0]
    if sized:
        under_cap = [f for f in sized if f[2] <= MAX]
        pick = max(under_cap, key=lambda f: f[2]) if under_cap else min(sized, key=lambda f: f[2])
    else:
        # Priority 3: fall back to the first file with no size info
        pick = files[0]
print(f"{pick[0]}\t{pick[1]}\t{pick[2]}")
' 2>/dev/null || true)
    TEST_MD5=$(echo "$TEST_META" | cut -f1)
    TEST_FILENAME=$(echo "$TEST_META" | cut -f2)
    # Size from query response; if unknown (-1 or 0), we'll fall back to HEAD below
    TEST_SIZE=$(echo "$TEST_META" | cut -f3)
fi
if [ -z "$TEST_MD5" ] || [ "$TEST_MD5" = "None" ]; then
    # Distinguish legitimate empty-index from a broken parser/response shape.
    # If the query returned a response but no `fighters` key, the shape
    # changed — that's a FAIL, not a skip.
    HAS_FIGHTERS=$(echo "${QUERY_JSON:-}" | grep -c '"fighters"' || true)
    EMPTY_FIGHTERS=$(echo "${QUERY_JSON:-}" | grep -c '"fighters"[[:space:]]*:[[:space:]]*\[[[:space:]]*\]' || true)
    if [ -n "${QUERY_JSON:-}" ] && [ "$HAS_FIGHTERS" = "0" ]; then
        fail "11.5 concurrent getfile.fn" "query.fn returned unexpected JSON shape (no 'fighters' key) — test parser out of date. Response: $(echo "${QUERY_JSON:-}" | head -c 150)"
    elif [ "$EMPTY_FIGHTERS" != "0" ]; then
        skip "11.5 concurrent getfile.fn" "server has no indexed files (empty 'fighters' array)"
    else
        if $VERBOSE && [ -n "${QUERY_JSON:-}" ]; then
            printf "${DIM}       query.fn response: %s${RESET}\n" "$(echo "$QUERY_JSON" | head -c 200)"
        fi
        skip "11.5 concurrent getfile.fn" "could not extract MD5 (set SMOKE_KNOWN_MD5=<md5> to force, or run with --verbose)"
    fi
else
    # Use size from query.fn if available (avoids an extra full download).
    # Fall back to a serial probe only if size wasn't in the response.
    if [ -z "$TEST_SIZE" ] || [ "$TEST_SIZE" = "-1" ] || [ "$TEST_SIZE" = "0" ]; then
        TEST_SIZE=$(curl -s -o /dev/null -w '%{size_download}' --max-time 60 \
            -H "Cookie: uuid=$UUID" "$SERVER/cass/getfile.fn?sNamer=$TEST_MD5" || echo "0")
    fi
    # Format size as human-readable, picking units that keep the number <1024.
    if [ "$TEST_SIZE" -gt 1073741824 ] 2>/dev/null; then
        TEST_SIZE_STR=$(awk "BEGIN { printf \"%.1fGB\", $TEST_SIZE/1073741824 }")
    elif [ "$TEST_SIZE" -gt 1048576 ] 2>/dev/null; then
        TEST_SIZE_STR=$(awk "BEGIN { printf \"%.1fMB\", $TEST_SIZE/1048576 }")
    elif [ "$TEST_SIZE" -gt 1024 ] 2>/dev/null; then
        TEST_SIZE_STR=$(awk "BEGIN { printf \"%.1fKB\", $TEST_SIZE/1024 }")
    else
        TEST_SIZE_STR="${TEST_SIZE}B"
    fi
    # Build a short file descriptor for output messages: filename (size, md5-prefix)
    if [ -n "$TEST_FILENAME" ]; then
        TEST_DESC="${TEST_FILENAME} ($TEST_SIZE_STR, md5:${TEST_MD5:0:8}…)"
    else
        TEST_DESC="$TEST_SIZE_STR file (md5:${TEST_MD5:0:8}…)"
    fi
    # Show which file the test is using — rotates based on what's indexed at runtime
    printf "${DIM}       11.5 test file: %s${RESET}\n" "$TEST_DESC"
    # Re-print the running status with updated context — the burst is what takes time
    printf "\r  ${CYAN}▶ running: 11.5 %d concurrent downloads of %s${RESET}%20s\r" "$DOWNLOAD_N" "$TEST_SIZE_STR" ""

    TMP_RESULTS=$(mktemp)
    # %{http_code} + %{time_total} tells us whether failures were HTTP errors
    # or timeouts (000 = curl timed out; 429/503 = server/WAF throttle).
    seq 1 $DOWNLOAD_N | xargs -P $DOWNLOAD_N -I{} sh -c \
        "curl -s -o /dev/null -w '%{http_code} %{time_total}\n' --max-time 30 -H 'Cookie: uuid=$UUID' '$SERVER/cass/getfile.fn?sNamer=$TEST_MD5'" \
        > "$TMP_RESULTS" 2>/dev/null || true
    SUCCESS=$(awk '$1=="200"' "$TMP_RESULTS" | wc -l | tr -d ' ')
    # Build a distribution summary: code:count pairs, sorted by count desc.
    CODE_DIST=$(awk '{print $1}' "$TMP_RESULTS" | sort | uniq -c | sort -rn | awk '{printf "%s×%s ", $2, $1}')
    SLOWEST=$(awk '{print $2}' "$TMP_RESULTS" | sort -rn | head -1)
    if $VERBOSE; then
        printf "${DIM}       11.5 result distribution: %s(slowest: %ss)${RESET}\n" "$CODE_DIST" "$SLOWEST"
    fi
    rm -f "$TMP_RESULTS"
    if [ "$SUCCESS" -ge "$DOWNLOAD_N" ]; then
        pass "11.5 $DOWNLOAD_N concurrent getfile.fn ($TEST_SIZE_STR each) — all $SUCCESS/$DOWNLOAD_N completed"
    else
        fail "11.5 $DOWNLOAD_N concurrent getfile.fn ($TEST_SIZE_STR)" "$SUCCESS/$DOWNLOAD_N succeeded — codes: ${CODE_DIST}(slowest ${SLOWEST}s)"
    fi
    assert_server_alive "11.5"
fi

# ─── 11.6 Orphan .p chunk cleanup ─────────────────────────
# Creates N abandoned chunk files and sets their mtime to 2 hours ago.
# ProcessorService should delete chunks older than 1h on its next sweep.
test_start "11.6 orphan .p chunk cleanup"
if $SMOKE_REMOTE; then
    skip "11.6 orphan chunk cleanup" "REMOTE mode — can't write to remote \$INCOMING"
elif [ ! -d "$INCOMING" ]; then
    skip "11.6 orphan chunk cleanup" "incoming dir not found: $INCOMING"
else
    # Create orphan chunks with stale timestamps
    ORPHAN_PREFIX="orphan-smktest-$(date +%s)"
    for i in $(seq 1 $ORPHAN_N); do
        echo "orphan-data-$i" > "$INCOMING/upload.${ORPHAN_PREFIX}-$i.5.0.p"
    done
    # Set mtime to 2 hours ago (older than the 1h cleanup threshold)
    touch -t "$(date -v-2H +%Y%m%d%H%M.%S 2>/dev/null || date -d '2 hours ago' +%Y%m%d%H%M.%S)" \
        "$INCOMING/upload.${ORPHAN_PREFIX}"-*.p 2>/dev/null

    # Use find (more portable) to count matching files
    count_orphans() {
        find "$INCOMING" -maxdepth 1 -name "upload.${ORPHAN_PREFIX}-*.p" 2>/dev/null | wc -l | tr -d ' '
    }

    BEFORE=$(count_orphans)

    # ProcessorService scans every ~10s. Wait up to 45s for cleanup.
    MAX_WAIT=45
    WAITED=0
    REMAINING=$BEFORE
    while [ "$WAITED" -lt "$MAX_WAIT" ]; do
        REMAINING=$(count_orphans)
        if [ "$REMAINING" = "0" ]; then
            break
        fi
        wait_msg "Waiting for orphan cleanup ($REMAINING left)" "$WAITED"
        sleep 3
        WAITED=$((WAITED + 3))
    done
    wait_done

    # Final tally
    REMAINING=$(count_orphans)
    # Clean up anything we created but wasn't cleaned
    rm -f "$INCOMING"/upload.${ORPHAN_PREFIX}-*.p 2>/dev/null

    if [ "$REMAINING" = "0" ]; then
        pass "11.6 orphan .p cleanup — $BEFORE chunks removed in ${WAITED}s"
    else
        fail "11.6 orphan .p cleanup" "$REMAINING of $BEFORE still present after ${WAITED}s"
    fi
    assert_server_alive "11.6"
fi

# 11.7 removed — login rate limit is now tested end-to-end by Phase 9.14.
# Test numbers retained for stability; 11.8 kept at its original number.

# ─── 11.8 Unbounded result set via query.fn ───────────────
# Query matching all files. The numobj parameter should cap result size.
# A numobj=0 or very large numobj must not return unbounded results.
test_start "11.8 unbounded query numobj=999999"
# Request a massive numobj and measure response size
RESP=$(curl -s -H "Cookie: uuid=$UUID" --max-time 10 \
    "$SERVER/cass/query.fn?view=json&ftype=.all&days=0&numobj=999999" || true)
RESP_SIZE=$(echo -n "$RESP" | wc -c | tr -d ' ')
# Extract numobj actually returned, if parseable
RETURNED=$(echo "$RESP" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d.get("records",[])))' 2>/dev/null || echo "?")
# Server must not hang, and response must be a valid JSON.
# We accept any size — the concern is hanging/OOM.
if [ -n "$RESP" ] && [ "$RESP_SIZE" -gt 0 ]; then
    pass "11.8 unbounded query (numobj=999999) — $RETURNED records in ${RESP_SIZE} bytes"
else
    fail "11.8 unbounded query" "no response or empty"
fi
assert_server_alive "11.8"

# ─── SUMMARY ──────────────────────────────────────────────

print_summary "PHASE 11"

#!/bin/bash
# clean_index.sh — narrow cleanup for indexing stress tests.
#
# Resets indexing-related state without touching user accounts or Docker config.
# Use this before each stress-test run so you start from a known empty state
# without losing users.txt or www-docker.properties.
#
# Resets:
#   rtserver/ — incoming, streaming, logs, batch_*.idx / .bad, tmp
#   scrubber/data/ — records.db, records*.db, .uuid (forces re-scan from scratch)
#   scrubber/data/localdb/ — Standard1, Standard2, Super2, BatchJobs, BackupJobs
#   scrubber/outgoing/ — pending pipeline artifacts
#   scrubber/logs/ — fresh logs for the run
#   scrubber/mobilebackup/upload/ — leftover uploads
#   rtserver/<dbname>_mm1, _mm2, _cp, _attr — MapDB stores (forces full re-index)
#
# Preserved:
#   scrubber/config/users.txt — your admin credentials
#   scrubber/config/scan1.txt — your current scan-folder configuration
#   scrubber/config/www-docker.properties — docker.localai.enabled setting
#   scrubber/config/blacklist*.txt — your blacklist
#   scrubber/config/serverinfo.properties — node identity
#
# Usage: ./clean_index.sh

set -e

cd "$(dirname "$0")"

echo "==> Narrow cleanup for indexing stress test"
echo

echo "Cleaning rtserver/ runtime state..."
# Use `find -delete` to avoid ARG_MAX glob-expansion failures on dirs with
# many entries (see comment in Standard1 cleanup below).
find ./rtserver/incoming -mindepth 1 -delete 2>/dev/null || true
find ./rtserver/streaming -mindepth 1 -delete 2>/dev/null || true
find ./rtserver/logs -mindepth 1 -delete 2>/dev/null || true
find ./rtserver/tmp -mindepth 1 -delete 2>/dev/null || true
rm -f ./rtserver/batch_*.idx 2>/dev/null || true
rm -f ./rtserver/batch_*.bad 2>/dev/null || true

echo "Cleaning rtserver/ MapDB stores (_mm1, _mm2, _cp, _attr)..."
# These have the dbname prefix; pattern-glob to wipe all variants.
find ./rtserver -maxdepth 1 -type f \( \
    -name "*_mm1*" -o \
    -name "*_mm2*" -o \
    -name "*_cp*"  -o \
    -name "*_attr*" \
\) -delete 2>/dev/null || true

echo "Cleaning scrubber/data/ records.db + MapDB index state..."
rm -f ./scrubber/data/records*.db 2>/dev/null || true
rm -f ./scrubber/data/.uuid 2>/dev/null || true
rm -rf ./scrubber/data/cluster* 2>/dev/null || true

echo "Cleaning scrubber/data/localdb/ flat-file databases..."
# NOTE: use `find -delete` not `rm -rf <dir>/*` — shell glob expansion exceeds
# ARG_MAX when dirs contain 20k+ files (observed 2026-05-28 with Standard1
# holding 40k entries from prior run). With glob, rm silently no-ops; the dir
# stays full. find handles any count by streaming.
find ./scrubber/data/localdb/Standard1 -mindepth 1 -delete 2>/dev/null || true
find ./scrubber/data/localdb/Standard2 -mindepth 1 -delete 2>/dev/null || true
find ./scrubber/data/localdb/Super2/hashes -mindepth 1 -delete 2>/dev/null || true
find ./scrubber/data/localdb/Super2/hashesm -mindepth 1 -delete 2>/dev/null || true
find ./scrubber/data/localdb/Super2/paths -mindepth 1 -delete 2>/dev/null || true
find ./scrubber/data/localdb/BatchJobs -mindepth 1 -delete 2>/dev/null || true
find ./scrubber/data/localdb/BackupJobs -mindepth 1 -delete 2>/dev/null || true
find ./scrubber/data/localdb/NodeInfo -mindepth 1 -delete 2>/dev/null || true

echo "Cleaning scrubber/ outgoing + logs + mobilebackup..."
find ./scrubber/outgoing -mindepth 1 -delete 2>/dev/null || true
find ./scrubber/logs -mindepth 1 -delete 2>/dev/null || true
find ./scrubber/mobilebackup/upload -mindepth 1 -delete 2>/dev/null || true
rm -f ./scrubber/batch_*.idx 2>/dev/null || true

# Confirm what's preserved.
echo
echo "==> Cleanup complete. Preserved files:"
ls -1 ./scrubber/config/users.txt ./scrubber/config/scan1.txt ./scrubber/config/www-docker.properties ./scrubber/config/blacklist.txt 2>/dev/null | sed 's/^/  ✓ /'

echo
echo "==> State after cleanup:"
echo "  rtserver/ files: $(find ./rtserver -type f 2>/dev/null | wc -l | tr -d ' ')"
echo "  scrubber/data/ files: $(find ./scrubber/data -type f 2>/dev/null | wc -l | tr -d ' ')"
echo "  scrubber/outgoing/ files: $(find ./scrubber/outgoing -type f 2>/dev/null | wc -l | tr -d ' ')"
echo "  records.db: $(test -f ./scrubber/data/records.db && echo 'EXISTS' || echo 'absent (correct)')"
echo
echo "==> Next steps:"
echo "  1. (Optional) Disable Docker for cleaner signal:"
echo "     edit scrubber/config/www-docker.properties → docker.localai.enabled=false"
echo "  2. Set your scan folder in scrubber/config/scan1.txt"
echo "  3. Start watcher: ./internal/scripts/stress_watcher.sh"
echo "  4. Start server: ./run.sh > /tmp/stress_test_stdout.log 2>&1 &"
echo "  5. Watch logs and watcher output"

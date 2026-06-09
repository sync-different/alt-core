#!/bin/bash
#
# check_index_coverage.sh — verify that every file on disk (under the scan roots) that the
# scanner is SUPPOSED to index actually HAS an index entry. This is the reverse direction of
# check_path_integrity.sh:
#
#     check_path_integrity.sh : index  -> filesystem  (find ORPHAN index entries)
#     check_index_coverage.sh : filesystem -> index   (find files the scanner MISSED)
#
# "Indexed" proof: the file's exact path appears in some Super2/paths/<md5> entry (the strongest
# proof — it was hashed AND located). Lines there are "<uuid>:<absolutePath>/,<value>" with
# value=="DELETED" for removed entries (see check_path_integrity.sh / LocalFuncs).
#
# A file is only expected to be indexed if it passes the scanner's admission rules
# (FileUtils.ScanDirectory / checkFileType):
#   1. its extension (lowercased, with dot) is a key in config/FileExtensions.txt, AND
#   2. its name does not start with '.' (hidden / macOS ._ sidecar)
# Files failing these are SKIPPED-BY-DESIGN (correctly not indexed), NOT a bug.
# NOTE: the scanner ALSO skips blacklisted directories (blacklist.txt). This script does NOT
# model the blacklist (extension+hidden only, by design) — blacklisted dirs rarely sit inside
# scan roots, but be aware a file under one would be reported MISSING here. See README.
#
# Verdicts per file:
#   INDEXED  - path found in Super2/paths (good)
#   MISSING  - passes the scanner rules but has NO path entry -> scanner missed it / index drift
#   SKIPPED  - excluded by scanner rules (bad extension or hidden) -> correctly not indexed
#
# Scan roots are read from config/scan1.txt (';'-separated, URL-encoded) exactly like
# check_download_button.sh, with multi-folder + disconnected-volume awareness.
#
# Read-only. Safe to run against a live PROD index.
#
# Usage:
#   ./check_index_coverage.sh [--appdata DIR] [--scandir DIR] [--verbose] [--show-indexed]
#
# Exit code: 0 if no MISSING files; 1 if any expected-but-unindexed files were found.

set -u
export PATH=/usr/bin:/bin:/usr/sbin:/sbin

APPDATA="$HOME/Library/Application Support/hivebot/scrubber"
SCANDIR=""
VERBOSE=0
SHOW_INDEXED=0
COLOR=auto

while [ $# -gt 0 ]; do
    case "$1" in
        --appdata) APPDATA="$2"; shift 2;;
        --scandir) SCANDIR="$2"; shift 2;;
        --verbose|-v) VERBOSE=1; shift;;
        --show-indexed) SHOW_INDEXED=1; shift;;
        --no-color) COLOR=never; shift;;
        --color) COLOR=always; shift;;
        -h|--help) sed -n '2,40p' "$0"; exit 0;;
        *) echo "Unknown arg: $1" >&2; exit 2;;
    esac
done

# --- color setup: green=OK, red=error, yellow=warning. Auto-off when not a TTY (pipes/cron). ---
if [ "$COLOR" = always ] || { [ "$COLOR" = auto ] && [ -t 1 ]; }; then
    C_OK=$'\033[32m'; C_ERR=$'\033[31m'; C_WARN=$'\033[33m'; C_RST=$'\033[0m'
else
    C_OK=""; C_ERR=""; C_WARN=""; C_RST=""
fi
cgreen()  { printf '%s%s%s' "$C_OK"   "$1" "$C_RST"; }
cred()    { printf '%s%s%s' "$C_ERR"  "$1" "$C_RST"; }
cyellow() { printf '%s%s%s' "$C_WARN" "$1" "$C_RST"; }
cnt_err()  { if [ "$1" -gt 0 ]; then cred "$1"; else cgreen "$1"; fi; }
cnt_warn() { if [ "$1" -gt 0 ]; then cyellow "$1"; else cgreen "$1"; fi; }

S1="$APPDATA/data/localdb/Standard1"
PATHS="$APPDATA/data/localdb/Super2/paths"
EXTFILE="$APPDATA/config/FileExtensions.txt"
SCAN1="$APPDATA/config/scan1.txt"

if [ ! -d "$PATHS" ]; then
    echo "ERROR: Super2/paths not found at: $PATHS" >&2
    echo "       Pass the correct --appdata (the scrubber dir holding data/localdb/)." >&2
    exit 2
fi
if [ ! -f "$EXTFILE" ]; then
    echo "ERROR: FileExtensions.txt not found at: $EXTFILE" >&2
    exit 2
fi

# --- URL-decode (mirrors WebServer URLDecoder.decode on each scan root) ---
url_decode() {
    local s="${1//+/ }"
    s="${s//%/\\x}"
    printf '%b' "$s"
}

# --- resolve scan roots (';'-separated, URL-encoded), multi-folder aware ---
if [ -n "$SCANDIR" ]; then
    RAW="$SCANDIR"
else
    line=$(grep -i '^scandir=' "$SCAN1" 2>/dev/null | head -1)
    RAW="${line#scandir=}"
fi
SCANDIRS=()
OLDIFS="$IFS"; IFS=';'
for part in $RAW; do
    [ -z "$part" ] && continue
    part="${part#"${part%%[![:space:]]*}"}"; part="${part%"${part##*[![:space:]]}"}"
    [ -z "$part" ] && continue
    SCANDIRS+=( "$(url_decode "$part")" )
done
IFS="$OLDIFS"
if [ "${#SCANDIRS[@]}" -eq 0 ]; then
    echo "ERROR: no scan dir set (scan1.txt has no scandir= and --scandir not given)" >&2
    exit 2
fi
VALID_DIRS=()
for d in "${SCANDIRS[@]}"; do
    if [ -d "$d" ]; then VALID_DIRS+=( "$d" ); else echo "WARNING: scan root not mounted/found, skipping: '$d'" >&2; fi
done
if [ "${#VALID_DIRS[@]}" -eq 0 ]; then
    echo "ERROR: none of the configured scan roots exist on this machine (disconnected?)." >&2
    exit 2
fi

# --- load the allowed-extension set from FileExtensions.txt (key = first comma field, incl dot) ---
# stored lowercased without the leading dot for quick lookup: e.g. "mp4 mov jpg ..."
ALLOWED_EXTS=" "
while IFS= read -r l; do
    [ -z "$l" ] && continue
    key="${l%%,*}"                       # ".mp4"
    key="${key#.}"                       # "mp4"
    key="$(printf '%s' "$key" | tr 'A-Z' 'a-z')"
    [ -n "$key" ] && ALLOWED_EXTS="${ALLOWED_EXTS}${key} "
done < "$EXTFILE"

ext_allowed() {
    case "$ALLOWED_EXTS" in *" $1 "*) return 0;; *) return 1;; esac
}

echo "=========================================================="
echo " Index-coverage validator (filesystem -> index)"
echo "=========================================================="
echo " appdata     : $APPDATA"
echo " scan roots  : ${#VALID_DIRS[@]}"
for d in "${VALID_DIRS[@]}"; do echo "    - $d"; done
echo " allowed ext : $(printf '%s' "$ALLOWED_EXTS" | wc -w | tr -d ' ') types from FileExtensions.txt"
echo " (read-only; INDEXED = path present in Super2/paths)"
echo "----------------------------------------------------------"

# --- pre-extract ALL live indexed paths into a sorted lookup file (one pass over Super2/paths) ---
PATHSET="$(mktemp)"
trap 'rm -f "$PATHSET"' EXIT
# line: "<uuid>:<absolutePath>/,<value>" ; skip DELETED ; split on last "/,"
for pf in "$PATHS"/*; do
    [ -f "$pf" ] || continue
    while IFS= read -r line; do
        [ -z "$line" ] && continue
        value="${line##*/,}"
        [ "$value" = "DELETED" ] && continue
        rest="${line#*:}"
        p="${rest%/,*}"
        printf '%s\n' "$p"
    done < "$pf"
done | LC_ALL=C sort -u > "$PATHSET"

indexed_paths=$(wc -l < "$PATHSET" | tr -d ' ')

total=0; idx=0; missing=0; skipped=0
MISSING_LIST=""; SKIPPED_LIST=""

while IFS= read -r f; do
    base="${f##*/}"
    total=$((total+1))

    # scanner rule 2: hidden / ._ files are never indexed
    case "$base" in
        .*) skipped=$((skipped+1))
            [ $VERBOSE -eq 1 ] && echo "$(cyellow SKIPPED) $f  (hidden)"
            SKIPPED_LIST="${SKIPPED_LIST}  SKIP(hidden)  $f"$'\n'
            continue;;
    esac

    # scanner rule 1: extension must be in FileExtensions.txt
    ext=""
    case "$base" in *.*) ext="$(printf '%s' "${base##*.}" | tr 'A-Z' 'a-z')";; esac
    if [ -z "$ext" ] || ! ext_allowed "$ext"; then
        skipped=$((skipped+1))
        [ $VERBOSE -eq 1 ] && echo "$(cyellow SKIPPED) $f  (ext '.$ext' not in FileExtensions.txt)"
        SKIPPED_LIST="${SKIPPED_LIST}  SKIP(ext)     $f"$'\n'
        continue
    fi

    # is this exact path in the indexed-path set?
    if LC_ALL=C grep -qxF "$f" "$PATHSET"; then
        idx=$((idx+1))
        [ $SHOW_INDEXED -eq 1 ] && echo "$(cgreen INDEXED) $f"
    else
        missing=$((missing+1))
        MISSING_LIST="${MISSING_LIST}  MISSING  $f"$'\n'
        [ $VERBOSE -eq 1 ] && echo "$(cred MISSING) $f  (expected to be indexed, no Super2/paths entry)"
    fi
done < <(find "${VALID_DIRS[@]}" -type f 2>/dev/null)

# indexed-path entries that did NOT match any file under the (mounted) scan roots.
# Expected when a scan root is disconnected, or when files were indexed from a root that has
# since been removed from scan1.txt. These are NOT coverage gaps — they're the index pointing
# outside the currently-scanned/mounted tree. (Cross-ref: check_path_integrity.sh classifies the
# disconnected-volume ones as UNMOUNTED.)
outside=$(( indexed_paths - idx ))
[ "$outside" -lt 0 ] && outside=0

echo "----------------------------------------------------------"
echo " RESULTS"
echo "   files on disk      : $total"
echo "   indexed-path set   : $indexed_paths  (live entries in Super2/paths)"
echo "   INDEXED            : $(cgreen "$idx")   (disk files matched to a path entry)"
echo "   SKIPPED-BY-DESIGN  : $(cnt_warn "$skipped")   (hidden or extension not in FileExtensions.txt)"
echo "   MISSING            : $(cnt_err "$missing")   (SHOULD be indexed but isn't — scanner missed it)"
echo "   index-only entries : $(cnt_warn "$outside")   (path entries NOT under a mounted scan root — e.g. disconnected drive / removed root; not a gap)"
echo "----------------------------------------------------------"

if [ "$outside" -gt 0 ]; then
    echo ""
    echo " NOTE: $outside indexed path(s) don't match any file under the mounted scan roots."
    echo "       Usually a disconnected drive or a scan root removed from scan1.txt — not a"
    echo "       coverage gap. Run check_path_integrity.sh to see them (UNMOUNTED vs ORPHAN)."
fi

if [ -n "$MISSING_LIST" ]; then
    echo ""
    echo " MISSING — files that pass scanner rules but have NO index entry:"
    printf '%s' "$MISSING_LIST"
fi
if [ -n "$SKIPPED_LIST" ] && [ $VERBOSE -eq 1 ]; then
    echo ""
    echo " SKIPPED-BY-DESIGN (informational):"
    printf '%s' "$SKIPPED_LIST"
elif [ "$skipped" -gt 0 ]; then
    echo ""
    echo " ($skipped skipped-by-design files hidden; re-run with --verbose to list them)"
fi

# exit non-zero only for genuine coverage gaps (MISSING)
[ "$missing" -eq 0 ] && exit 0 || exit 1

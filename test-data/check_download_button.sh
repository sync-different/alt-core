#!/bin/bash
#
# check_download_button.sh — validate which indexed files would NOT show a Download button
# in the uiv5 Folder View (or would show one pointing at the WRONG content).
#
# It mirrors the server's resolution logic in WebServer.getFileMD5(name, fullPath)
# (see internal/SPEC_FOLDER_FILELOOKUP.md):
#   1. candidates = md5 rows in Standard1/<filename> whose name == filename
#      (fall back to the per-extension aggregate Standard1/.<ext> only if the
#       per-filename key file does not exist)
#   2. 0 candidates  -> EMPTY    : Folder View shows NO download button
#   3. 1 candidate   -> OK       : button works
#   4. 2+ candidates -> disambiguate by matching the file's full path against
#      Super2/paths/<md5>:
#        - a path matches -> OK (resolves to the correct copy)
#        - none match     -> AMBIGUOUS: button appears but uses the FIRST md5,
#                            which may be the wrong file's content
#
# Read-only. Safe to run against a live PROD index.
#
# Usage:
#   ./check_download_button.sh [--appdata DIR] [--scandir DIR] [--ext mp4,mov,...] [--verbose]
#
# Defaults (macOS PROD layout):
#   --appdata  ~/Library/Application Support/hivebot/scrubber
#   --scandir  read from <appdata>/config/scan1.txt (scandir=...)
#
# Exit code: 0 if all files resolve OK; 1 if any EMPTY or AMBIGUOUS were found.

set -u
export PATH=/usr/bin:/bin:/usr/sbin:/sbin

APPDATA="$HOME/Library/Application Support/hivebot/scrubber"
SCANDIR=""
EXT_FILTER=""          # empty = all files; else comma list like "mp4,mov"
VERBOSE=0
COLOR=auto             # auto | always | never

while [ $# -gt 0 ]; do
    case "$1" in
        --appdata) APPDATA="$2"; shift 2;;
        --scandir) SCANDIR="$2"; shift 2;;
        --ext)     EXT_FILTER="$2"; shift 2;;
        --verbose|-v) VERBOSE=1; shift;;
        --no-color) COLOR=never; shift;;
        --color) COLOR=always; shift;;
        -h|--help)
            sed -n '2,40p' "$0"; exit 0;;
        *) echo "Unknown arg: $1" >&2; exit 2;;
    esac
done

# --- color setup: green=OK, red=error, yellow=warning. Auto-off when not a TTY (pipes/cron). ---
if [ "$COLOR" = always ] || { [ "$COLOR" = auto ] && [ -t 1 ]; }; then
    C_OK=$'\033[32m'; C_ERR=$'\033[31m'; C_WARN=$'\033[33m'; C_DIM=$'\033[2m'; C_RST=$'\033[0m'
else
    C_OK=""; C_ERR=""; C_WARN=""; C_DIM=""; C_RST=""
fi
cgreen()  { printf '%s%s%s'  "$C_OK"   "$1" "$C_RST"; }
cred()    { printf '%s%s%s'  "$C_ERR"  "$1" "$C_RST"; }
cyellow() { printf '%s%s%s'  "$C_WARN" "$1" "$C_RST"; }

S1="$APPDATA/data/localdb/Standard1"
PATHS="$APPDATA/data/localdb/Super2/paths"

if [ ! -d "$S1" ]; then
    echo "ERROR: Standard1 index not found at: $S1" >&2
    echo "       Pass the correct --appdata (the scrubber dir holding data/localdb/)." >&2
    exit 2
fi

# URL-decode (%XX hex + '+' -> space), mirroring the server's URLDecoder.decode on each scan root.
url_decode() {
    local s="${1//+/ }"          # '+' -> space (application/x-www-form-urlencoded)
    s="${s//%/\\x}"              # %XX -> \xXX  (e.g. %2F -> \x2F, %20 -> \x20)
    printf '%b' "$s"             # %b interprets the \xXX escapes; literal text passes through
}

# Resolve scan roots. The server stores scandir as a ';'-separated list of URL-encoded
# absolute paths (WebServer.java:3744 -> split(";") then URLDecoder.decode each).
# Support multiple folders here too. --scandir may also be given (one path, or ';'-separated).
SCANDIRS=()
if [ -n "$SCANDIR" ]; then
    RAW="$SCANDIR"
else
    line=$(grep -i '^scandir=' "$APPDATA/config/scan1.txt" 2>/dev/null | head -1)
    RAW="${line#scandir=}"
fi

# split on ';' and decode each entry
OLDIFS="$IFS"; IFS=';'
for part in $RAW; do
    [ -z "$part" ] && continue
    # trim whitespace
    part="${part#"${part%%[![:space:]]*}"}"
    part="${part%"${part##*[![:space:]]}"}"
    [ -z "$part" ] && continue
    dec="$(url_decode "$part")"
    SCANDIRS+=( "$dec" )
done
IFS="$OLDIFS"

if [ "${#SCANDIRS[@]}" -eq 0 ]; then
    echo "ERROR: no scan dir set (scan1.txt has no scandir= or --scandir not given)" >&2
    exit 2
fi

# validate each root exists; warn (don't abort) on a missing one so the rest still get checked
VALID_DIRS=()
for d in "${SCANDIRS[@]}"; do
    if [ -d "$d" ]; then
        VALID_DIRS+=( "$d" )
    else
        echo "WARNING: scan root not found, skipping: '$d'" >&2
    fi
done
if [ "${#VALID_DIRS[@]}" -eq 0 ]; then
    echo "ERROR: none of the configured scan roots exist on this machine." >&2
    for d in "${SCANDIRS[@]}"; do echo "        - '$d'" >&2; done
    exit 2
fi

echo "=========================================================="
echo " Download-button validator (SPEC_FOLDER_FILELOOKUP)"
echo "=========================================================="
echo " appdata     : $APPDATA"
echo " scan roots  : ${#VALID_DIRS[@]}"
for d in "${VALID_DIRS[@]}"; do echo "    - $d"; done
echo " ext         : ${EXT_FILTER:-<all>}"
echo " (read-only; mirrors WebServer.getFileMD5)"
echo "----------------------------------------------------------"

# get the file extension lowercased (mirrors getFileExtension + toLowerCase)
file_ext_lower() {
    local n="$1" base="${1##*/}"
    case "$base" in
        *.*) printf '%s' "${base##*.}" | tr 'A-Z' 'a-z' ;;
        *)   printf '' ;;
    esac
}

# collect distinct md5s from an index file whose 3rd field (name) == wantname.
# Index line format: "date,md5,name" — split on first two commas (name may contain commas).
collect_md5s() {
    local idxfile="$1" wantname="$2"
    [ -f "$idxfile" ] || return 0
    awk -v want="$wantname" '
        {
            c1 = index($0, ",");            if (c1 == 0) next;
            rest = substr($0, c1+1);
            c2 = index(rest, ",");          if (c2 == 0) next;
            md5  = substr(rest, 1, c2-1);
            name = substr(rest, c2+1);
            if (name == want && !(md5 in seen)) { seen[md5]=1; print md5 }
        }
    ' "$idxfile"
}

# does Super2/paths/<md5> contain a (non-DELETED) entry whose absolute path == wantpath?
# Path line format: "uuid:<absolutePath>/,name"
path_matches_md5() {
    local md5="$1"
    local wantpath="$2"
    local pf="$PATHS/$md5"
    [ -f "$pf" ] || return 1
    awk -v want="$wantpath" '
        {
            colon = index($0, ":");         if (colon == 0) next;
            rest = substr($0, colon+1);     # "<absolutePath>/,name"
            if (rest ~ /^DELETED/ || index(rest, ",DELETED") > 0) next;
            lc = 0;                         # find last comma
            for (i = length(rest); i > 0; i--) { if (substr(rest,i,1) == ",") { lc = i; break } }
            stored = (lc > 0) ? substr(rest, 1, lc-1) : rest;   # "<absolutePath>/"
            if (substr(stored, length(stored), 1) == "/") stored = substr(stored, 1, length(stored)-1);
            if (stored == want) { found=1; exit }
        }
        END { exit (found ? 0 : 1) }
    ' "$pf"
}

total=0; ok=0; empty=0; ambig=0; notidx=0
EMPTY_LIST=""; AMBIG_LIST=""; NOTIDX_LIST=""

ALL_AGG="$S1/.all"
# is this filename present anywhere in the index? The per-filename key Standard1/<name> is written
# for EVERY physical copy (above the dedup gate), so its existence = indexed. As a secondary probe,
# a name appearing in the .all catch-all also means indexed. If NEITHER, the file was never scanned.
name_known_to_index() {
    local base="$1"
    [ -f "$S1/$base" ] && return 0
    grep -qF ",$base" "$ALL_AGG" 2>/dev/null && return 0
    return 1
}

# build find expression for extension filter (always restrict to regular files)
if [ -n "$EXT_FILTER" ]; then
    FIND_EXPR=( "-type" "f" "(" )
    first=1
    IFS=',' read -ra exts <<< "$EXT_FILTER"
    for e in "${exts[@]}"; do
        [ $first -eq 0 ] && FIND_EXPR+=( "-o" )
        FIND_EXPR+=( "-iname" "*.${e}" )
        first=0
    done
    FIND_EXPR+=( ")" )
else
    FIND_EXPR=( "-type" "f" )
fi

while IFS= read -r f; do
    base="${f##*/}"
    case "$base" in
        .*) continue ;;        # skip hidden / macOS ._ sidecar files (not indexed)
    esac
    total=$((total+1))

    ext="$(file_ext_lower "$base")"
    byName="$S1/$base"
    byExt="$S1/.$ext"

    if [ -f "$byName" ]; then
        cands="$(collect_md5s "$byName" "$base")"
    else
        cands="$(collect_md5s "$byExt" "$base")"
    fi

    ncand=$(printf '%s\n' "$cands" | grep -c .)

    if [ "$ncand" -eq 0 ]; then
        if name_known_to_index "$base"; then
            # file IS in the index but md5 won't resolve -> the real download-button bug
            empty=$((empty+1))
            EMPTY_LIST="${EMPTY_LIST}  BUG     $f"$'\n'
            [ $VERBOSE -eq 1 ] && echo "$(cred BUG)     $f  (indexed but md5 unresolvable)"
        else
            # never scanned/indexed -> transient, informational (not a bug)
            notidx=$((notidx+1))
            NOTIDX_LIST="${NOTIDX_LIST}  NOIDX   $f"$'\n'
            [ $VERBOSE -eq 1 ] && echo "$(cyellow NOIDX)   $f  (not indexed yet)"
        fi
    elif [ "$ncand" -eq 1 ]; then
        ok=$((ok+1))
        [ $VERBOSE -eq 1 ] && echo "$(cgreen OK)      $f  ($cands)"
    else
        # 2+ candidates: try path disambiguation
        canon="$(cd "$(dirname "$f")" 2>/dev/null && printf '%s/%s' "$(pwd -P)" "$base")"
        [ -z "$canon" ] && canon="$f"
        matched=""
        while IFS= read -r m; do
            [ -z "$m" ] && continue
            if path_matches_md5 "$m" "$canon"; then matched="$m"; break; fi
        done <<< "$cands"
        if [ -n "$matched" ]; then
            ok=$((ok+1))
            [ $VERBOSE -eq 1 ] && echo "$(cgreen 'OK*')     $f  (disambiguated -> $matched among $ncand)"
        else
            ambig=$((ambig+1))
            AMBIG_LIST="${AMBIG_LIST}  AMBIG   $f  ($ncand md5s, none matched path)"$'\n'
            [ $VERBOSE -eq 1 ] && echo "$(cred AMBIG)   $f  ($ncand candidates, none matched path)"
        fi
    fi
done < <(find "${VALID_DIRS[@]}" "${FIND_EXPR[@]}" 2>/dev/null)

# color a count: green if 0-is-good metric, red if a defect count is >0, yellow if informational >0
cnt_err()  { if [ "$1" -gt 0 ]; then cred "$1"; else cgreen "$1"; fi; }
cnt_warn() { if [ "$1" -gt 0 ]; then cyellow "$1"; else cgreen "$1"; fi; }

echo "----------------------------------------------------------"
echo " RESULTS"
echo "   files checked  : $total"
echo "   OK (button)    : $(cgreen "$ok")"
echo "   NOT-INDEXED    : $(cnt_warn "$notidx")   (not scanned yet — informational, not a bug)"
echo "   BUG (no btn)   : $(cnt_err "$empty")   (INDEXED but md5 won't resolve — real defect)"
echo "   AMBIGUOUS      : $(cnt_err "$ambig")   (button shows but may be wrong content)"
echo "----------------------------------------------------------"

if [ -n "$EMPTY_LIST" ]; then
    echo ""
    echo " BUG — indexed files whose md5 won't resolve (no download button):"
    printf '%s' "$EMPTY_LIST"
fi
if [ -n "$AMBIG_LIST" ]; then
    echo ""
    echo " AMBIGUOUS — same name, different content, no path match (button may serve wrong bytes):"
    printf '%s' "$AMBIG_LIST"
fi
if [ -n "$NOTIDX_LIST" ] && [ $VERBOSE -eq 1 ]; then
    echo ""
    echo " NOT-INDEXED (informational — run a scan to index these):"
    printf '%s' "$NOTIDX_LIST"
fi
[ -n "$NOTIDX_LIST" ] && [ $VERBOSE -eq 0 ] && \
    echo "" && echo " ($notidx not-indexed files hidden; re-run with --verbose to list them)"

# exit non-zero only for genuine defects (BUG / AMBIGUOUS); NOT-INDEXED is informational
[ "$empty" -eq 0 ] && [ "$ambig" -eq 0 ] && exit 0 || exit 1

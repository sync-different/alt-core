#!/bin/bash
#
# check_duplicates.sh — list content-duplicate files in the index: a single MD5 (content hash)
# that exists at MORE THAN ONE live location on disk.
#
# The index in Super2/paths/ has one file per MD5 (filename == md5). Each line is:
#     <uuid>:<absolutePath>/,<value>
# where <value> is the file's name normally, or the literal "DELETED" once the entry has been
# removed (edit_SuperColumn marks it DELETED rather than deleting the line).
# See internal/SPEC_FOLDER_FILELOOKUP.md and check_path_integrity.sh.
#
# An MD5 is a DUPLICATE when it has 2+ DISTINCT, NON-DELETED paths that ACTUALLY EXIST on disk:
#   - skip ,DELETED entries (tombstoned)
#   - de-dupe identical path strings (a path listed twice is a re-index artifact, not a 2nd copy)
#   - require the file to exist on disk right now (a path on an unmounted /Volumes/<NAME> can't be
#     verified, so it does NOT count toward the duplicate — avoids false positives on disconnected
#     drives; such entries are reported separately as UNVERIFIABLE, not as duplicates)
#
# Output: one block per duplicate MD5 with its N existing paths. Optional CSV (--csv FILE):
# columns  md5,copies,path  (one row PER path, so a 2-copy file = 2 rows sharing the md5).
#
# Read-only. Safe to run against a live PROD index.
#
# Usage:
#   ./check_duplicates.sh [--appdata DIR] [--verbose] [--csv FILE] [--no-color|--color]
#
# Defaults (macOS PROD layout):
#   --appdata  ~/Library/Application Support/hivebot/scrubber
#
# Exit code: 0 if no duplicates found; 1 if any duplicate MD5 exists.

set -u
export PATH=/usr/bin:/bin:/usr/sbin:/sbin

APPDATA="$HOME/Library/Application Support/hivebot/scrubber"
VERBOSE=0
COLOR=auto
CSV=""

while [ $# -gt 0 ]; do
    case "$1" in
        --appdata) APPDATA="$2"; shift 2;;
        --verbose|-v) VERBOSE=1; shift;;
        --csv) CSV="$2"; shift 2;;
        --no-color) COLOR=never; shift;;
        --color) COLOR=always; shift;;
        -h|--help) sed -n '2,30p' "$0"; exit 0;;
        *) echo "Unknown arg: $1" >&2; exit 2;;
    esac
done

if [ "$COLOR" = always ] || { [ "$COLOR" = auto ] && [ -t 1 ]; }; then
    C_OK=$'\033[32m'; C_ERR=$'\033[31m'; C_WARN=$'\033[33m'; C_RST=$'\033[0m'
else
    C_OK=""; C_ERR=""; C_WARN=""; C_RST=""
fi
cgreen()  { printf '%s%s%s' "$C_OK"   "$1" "$C_RST"; }
cred()    { printf '%s%s%s' "$C_ERR"  "$1" "$C_RST"; }
cyellow() { printf '%s%s%s' "$C_WARN" "$1" "$C_RST"; }
cnt_err() { if [ "$1" -gt 0 ]; then cred "$1"; else cgreen "$1"; fi; }

PATHS="$APPDATA/data/localdb/Super2/paths"

if [ ! -d "$PATHS" ]; then
    echo "ERROR: Super2/paths not found at: $PATHS" >&2
    echo "       Pass the correct --appdata (the scrubber dir holding data/localdb/)." >&2
    exit 2
fi

# validate the CSV target dir up-front (don't fail at the end after a long run)
if [ -n "$CSV" ]; then
    csv_dir="$(dirname "$CSV")"
    if [ ! -d "$csv_dir" ]; then
        echo "ERROR: CSV output dir does not exist: $csv_dir" >&2
        exit 2
    fi
fi

echo "=========================================================="
echo " Duplicate-files validator (content MD5 at 2+ live paths)"
echo "=========================================================="
echo " appdata : $APPDATA"
echo " paths   : $PATHS"
echo " (read-only; a duplicate = 1 MD5 with 2+ distinct paths that exist on disk)"
echo "----------------------------------------------------------"

md5files=0
dup_md5=0           # MD5s with 2+ existing distinct paths
dup_copies=0        # total existing copies across all duplicate MD5s
unverifiable=0      # MD5s with 2+ live paths but some/all on unmounted volumes (can't confirm)
redundant_bytes=0   # reclaimable bytes = sum over dups of size*(copies-1)

[ -n "$CSV" ] && printf 'md5,copies,bytes,path\r\n' > "$CSV"

# size of a file in bytes (portable: stat -f%z on macOS/BSD, stat -c%s on GNU)
filesize() {
    stat -f%z "$1" 2>/dev/null || stat -c%s "$1" 2>/dev/null || echo 0
}

# human-readable bytes
human() {
    local b=$1
    if [ "$b" -lt 1024 ]; then echo "${b} B"; return; fi
    awk -v b="$b" 'BEGIN{u[1]="KB";u[2]="MB";u[3]="GB";u[4]="TB";v=b/1024;i=1;
        while(v>=1024 && i<4){v/=1024;i++} printf (v<10?"%.1f %s":"%.0f %s"), v, u[i]}'
}

# extract the absolute path from a line "uuid:<absPath>/,<value>" — strip uuid prefix + trailing /,<value>
extract_path() {
    local line="$1" rest stored
    rest="${line#*:}"          # "<absPath>/,<value>"
    stored="${rest%/,*}"       # "<absPath>"  (split on last "/,", comma-in-name safe)
    printf '%s' "$stored"
}

for f in "$PATHS"/*; do
    [ -f "$f" ] || continue
    md5="${f##*/}"
    md5files=$((md5files+1))

    # collect distinct, non-DELETED paths that EXIST on disk; track unmounted separately
    existing_paths=()
    seen_paths=""
    had_unmounted=0
    while IFS= read -r line; do
        [ -z "$line" ] && continue
        case "$line" in *",DELETED"*) continue;; esac    # tombstoned
        case "$line" in *:*) ;; *) continue;; esac        # malformed (no uuid:)
        p="$(extract_path "$line")"
        [ -z "$p" ] && continue
        # de-dupe identical path strings
        case "$seen_paths" in *"|$p|"*) continue;; esac
        seen_paths="${seen_paths}|$p|"
        if [ -e "$p" ]; then
            existing_paths+=( "$p" )
        else
            # path missing — is its volume just unmounted? (/Volumes/<NAME> gone)
            case "$p" in
                /Volumes/*)
                    vol="/Volumes/$(printf '%s' "${p#/Volumes/}" | cut -d/ -f1)"
                    [ ! -d "$vol" ] && had_unmounted=1
                    ;;
            esac
        fi
    done < "$f"

    n=${#existing_paths[@]}
    if [ "$n" -ge 2 ]; then
        dup_md5=$((dup_md5+1))
        dup_copies=$((dup_copies+n))
        # all copies are identical content -> same size; take the first existing copy's size
        sz=$(filesize "${existing_paths[0]}")
        redundant_bytes=$(( redundant_bytes + sz * (n - 1) ))
        if [ $VERBOSE -eq 1 ]; then
            echo "$(cred DUP)  $md5  ($n copies, $(human "$sz") each):"
            for p in "${existing_paths[@]}"; do echo "       -> $p"; done
        fi
        if [ -n "$CSV" ]; then
            for p in "${existing_paths[@]}"; do
                # quote path (may contain commas/spaces); double internal quotes
                q="\"${p//\"/\"\"}\""
                printf '%s,%s,%s,%s\r\n' "$md5" "$n" "$sz" "$q" >> "$CSV"
            done
        fi
    elif [ "$n" -eq 1 ] && [ "$had_unmounted" -eq 1 ]; then
        # 1 confirmed + 1+ on disconnected drives — possible dup we can't verify
        unverifiable=$((unverifiable+1))
        [ $VERBOSE -eq 1 ] && echo "$(cyellow UNVERIFIABLE)  $md5  (1 on disk + path(s) on unmounted volume)"
    fi
done

echo "----------------------------------------------------------"
echo " RESULTS"
echo "   md5 files scanned : $md5files"
echo "   DUPLICATE md5     : $(cnt_err "$dup_md5")   (1 content hash at 2+ existing paths)"
echo "   duplicate copies  : $dup_copies   (total existing files across those md5s)"
echo "   reclaimable space : $(human "$redundant_bytes")   ($redundant_bytes bytes — size*(copies-1) summed)"
echo "   unverifiable      : $unverifiable   (1 on disk + path on unmounted volume — reconnect to confirm)"
[ -n "$CSV" ] && echo "   CSV written       : $CSV"
echo "=========================================================="

[ "$dup_md5" -gt 0 ] && exit 1 || exit 0

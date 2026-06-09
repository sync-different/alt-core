#!/bin/bash
#
# check_path_integrity.sh — verify that every file path recorded in the index actually
# exists on the filesystem (orphan-index + path-integrity check, combined).
#
# The index in Super2/paths/ has one file per MD5 (filename == md5). Each line is:
#     <uuid>:<absolutePath>/,<value>
# where <value> is the file's name normally, or the literal "DELETED" once the entry has
# been removed (edit_SuperColumn marks it DELETED rather than deleting the line).
# See LocalFuncs.edit_SuperColumn / removeFromPaths (cass-server) and
# internal/SPEC_FOLDER_FILELOOKUP.md.
#
# For each non-DELETED path entry this script stats the path and reports:
#   OK        - the file exists on disk
#   ORPHAN    - the index references a path that is NOT on disk, AND its volume IS mounted
#               (stale entry / file moved or deleted out-of-band without the app's delete flow)
#   UNMOUNTED - the path is on an external volume (/Volumes/<NAME>) that is NOT currently
#               mounted, so we CANNOT tell if the file exists. Informational, NOT a bug —
#               reconnect the drive and re-run. (Avoids falsely flagging disconnected drives.)
#   DELETED   - entry already marked DELETED in the index (informational; skipped from OK/ORPHAN)
#
# Also flags MD5 path-files that have ZERO live entries (all DELETED or empty) as EMPTYMD5.
#
# macOS volume-mount detection: a path under /Volumes/<NAME>/... is on an external volume whose
# mount point is /Volumes/<NAME>. If that mount point directory does not exist, the volume is
# disconnected (macOS removes the /Volumes/<NAME> entry when a drive unmounts). The boot volume
# (e.g. "Macintosh HD") keeps its /Volumes/<NAME> firmlink, so internal paths classify normally.
#
# Read-only. Safe to run against a live PROD index.
#
# Usage:
#   ./check_path_integrity.sh [--appdata DIR] [--verbose] [--show-ok]
#
# Defaults (macOS PROD layout):
#   --appdata  ~/Library/Application Support/hivebot/scrubber
#
# Exit code: 0 if no ORPHAN entries found; 1 if any orphaned paths exist.

set -u
export PATH=/usr/bin:/bin:/usr/sbin:/sbin

APPDATA="$HOME/Library/Application Support/hivebot/scrubber"
VERBOSE=0
SHOW_OK=0
COLOR=auto

while [ $# -gt 0 ]; do
    case "$1" in
        --appdata) APPDATA="$2"; shift 2;;
        --verbose|-v) VERBOSE=1; shift;;
        --show-ok) SHOW_OK=1; shift;;
        --no-color) COLOR=never; shift;;
        --color) COLOR=always; shift;;
        -h|--help) sed -n '2,33p' "$0"; exit 0;;
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

PATHS="$APPDATA/data/localdb/Super2/paths"

if [ ! -d "$PATHS" ]; then
    echo "ERROR: Super2/paths not found at: $PATHS" >&2
    echo "       Pass the correct --appdata (the scrubber dir holding data/localdb/)." >&2
    exit 2
fi

echo "=========================================================="
echo " Path-integrity / orphan-index validator"
echo "=========================================================="
echo " appdata : $APPDATA"
echo " paths   : $PATHS"
echo " (read-only; stats each indexed path against the filesystem)"
echo "----------------------------------------------------------"

md5files=0
total_entries=0
ok=0
orphan=0
unmounted=0
deleted=0
emptymd5=0
ORPHAN_LIST=""
UNMOUNTED_LIST=""
EMPTYMD5_LIST=""
declare -a UNMOUNTED_VOLS=()   # distinct disconnected volume names seen

# Is the external volume that contains _path currently NOT mounted?
# Returns 0 (true = unmounted) only for /Volumes/<NAME>/... whose /Volumes/<NAME> dir is absent.
volume_unmounted() {
    local p="$1"
    case "$p" in
        /Volumes/*)
            local rest="${p#/Volumes/}"
            local vol="${rest%%/*}"
            [ -z "$vol" ] && return 1
            if [ -d "/Volumes/$vol" ]; then
                return 1          # mount point present -> volume is mounted
            else
                LAST_UNMOUNTED_VOL="$vol"
                return 0          # mount point absent -> disconnected
            fi
            ;;
        *) return 1 ;;            # not an external-volume path
    esac
}

# Parse one path line -> prints "STATUS<TAB>path" where STATUS is LIVE or DELETED.
# Line: "<uuid>:<absolutePath>/,<value>"  (value == "DELETED" means removed)
# value is the substring after the LAST comma; path is between the first ':' and the "/,<value>".
for pf in "$PATHS"/*; do
    [ -f "$pf" ] || continue
    md5="${pf##*/}"
    md5files=$((md5files+1))
    live_in_file=0

    while IFS= read -r line; do
        [ -z "$line" ] && continue
        total_entries=$((total_entries+1))

        # Line: "<uuid>:<absolutePath>/,<value>". The stored path ALWAYS ends in '/', so the
        # field delimiter is the LAST "/," — split there, NOT on the last comma. (The <value>
        # field, and even the path, can themselves contain commas, e.g. a display name like
        # "Steve Jobs and Bill Gates Together in 2007, at D5..mov" — a last-comma split would
        # corrupt the path. Same comma-in-name hazard handled in check_download_button.sh.)
        rest="${line#*:}"                    # "<absolutePath>/,<value>"
        path="${rest%/,*}"                   # "<absolutePath>"  (everything before the last "/,")
        value="${rest##*/,}"                 # "<value>"         (everything after the last "/,")

        if [ "$value" = "DELETED" ]; then
            deleted=$((deleted+1))
            [ $VERBOSE -eq 1 ] && echo "$(cyellow DELETED) $md5  $path"
            continue
        fi

        live_in_file=$((live_in_file+1))

        if [ -e "$path" ]; then
            ok=$((ok+1))
            [ $SHOW_OK -eq 1 ] && echo "$(cgreen OK)      $md5  $path"
        elif volume_unmounted "$path"; then
            # path's external volume is disconnected -> can't determine existence; not a bug
            unmounted=$((unmounted+1))
            UNMOUNTED_LIST="${UNMOUNTED_LIST}  UNMOUNT md5=$md5  $path"$'\n'
            # remember the distinct volume name (guard empty-array expansion under set -u)
            local_dup=0
            if [ "${#UNMOUNTED_VOLS[@]}" -gt 0 ]; then
                for v in "${UNMOUNTED_VOLS[@]}"; do [ "$v" = "$LAST_UNMOUNTED_VOL" ] && local_dup=1 && break; done
            fi
            [ $local_dup -eq 0 ] && UNMOUNTED_VOLS+=( "$LAST_UNMOUNTED_VOL" )
            [ $VERBOSE -eq 1 ] && echo "$(cyellow UNMOUNT) $md5  $path"
        else
            orphan=$((orphan+1))
            ORPHAN_LIST="${ORPHAN_LIST}  ORPHAN  md5=$md5  $path"$'\n'
            [ $VERBOSE -eq 1 ] && echo "$(cred ORPHAN)  $md5  $path"
        fi
    done < "$pf"

    if [ "$live_in_file" -eq 0 ]; then
        emptymd5=$((emptymd5+1))
        EMPTYMD5_LIST="${EMPTYMD5_LIST}  EMPTYMD5  $md5  (no live paths; all DELETED or empty)"$'\n'
        [ $VERBOSE -eq 1 ] && echo "$(cyellow EMPTYMD5) $md5  (no live paths)"
    fi
done

echo "----------------------------------------------------------"
echo " RESULTS"
echo "   md5 path-files : $md5files"
echo "   path entries   : $total_entries"
echo "   OK (on disk)   : $(cgreen "$ok")"
echo "   ORPHAN         : $(cnt_err "$orphan")   (index path NOT on disk, volume mounted — stale entry)"
echo "   UNMOUNTED      : $(cnt_warn "$unmounted")   (path on a disconnected volume — can't verify, informational)"
echo "   DELETED        : $(cnt_warn "$deleted")   (already marked deleted — informational)"
echo "   EMPTYMD5       : $(cnt_warn "$emptymd5")   (md5 with no live paths — informational)"
echo "----------------------------------------------------------"

if [ "$unmounted" -gt 0 ]; then
    echo ""
    echo " NOTE: $unmounted entries are on disconnected volume(s): ${UNMOUNTED_VOLS[*]}"
    echo "       Reconnect and re-run to verify those. They are NOT counted as orphans."
fi

if [ -n "$ORPHAN_LIST" ]; then
    echo ""
    echo " ORPHAN — index references files that are NOT on disk (volume IS mounted):"
    printf '%s' "$ORPHAN_LIST"
fi
if [ -n "$UNMOUNTED_LIST" ] && [ $VERBOSE -eq 1 ]; then
    echo ""
    echo " UNMOUNTED — entries on disconnected volumes (not verifiable):"
    printf '%s' "$UNMOUNTED_LIST"
fi
if [ -n "$EMPTYMD5_LIST" ] && [ $VERBOSE -eq 1 ]; then
    echo ""
    echo " EMPTYMD5 — md5 path-files with no live entries:"
    printf '%s' "$EMPTYMD5_LIST"
elif [ -n "$EMPTYMD5_LIST" ]; then
    echo ""
    echo " ($emptymd5 EMPTYMD5 path-files hidden; re-run with --verbose to list them)"
fi

# exit non-zero only for genuine orphans (a real integrity problem)
[ "$orphan" -eq 0 ] && exit 0 || exit 1

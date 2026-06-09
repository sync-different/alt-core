#!/bin/bash
#
# check_streaming_artifacts.sh — for each video's streaming/<md5>/ folder, verify the three
# generated artifacts are present (and non-empty where that matters):
#
#   THUMBNAIL     thumbnail.jpg                  (ffmpeg.txt line 2)
#   HLS           OUTPUT.m3u8 + >=1 OUTPUT-*.ts  (ffmpeg.txt line 1)
#   TRANSCRIPTION audio.json (>0 bytes)          (ffmpeg.txt line 4, whisper)
#
# Each folder is checked independently and gets an OK / MISSING verdict PER CATEGORY, so an
# incomplete folder (e.g. transcription done but HLS encode failed) is reported precisely.
#
# Rules (validated against the live PROD index):
#   - HLS is all-or-nothing in practice: OK requires BOTH the .m3u8 manifest AND at least one
#     OUTPUT-NNNNN.ts segment (a manifest with no segments is broken).
#   - TRANSCRIPTION: audio.json present AND >0 bytes = OK. A 0-byte audio.json is a failed/hung
#     transcription (e.g. the whisper-curl hang) -> MISSING. NOTE: a small non-empty json such as
#     {"text":"[BLANK_AUDIO]"} is a VALID result for a silent/no-speech video -> OK (not MISSING).
#   - THUMBNAIL: thumbnail.jpg present (>0 bytes) = OK.
#
# Folders with NONE of the three artifacts are bucketed separately as EMPTY (likely a stub or a
# folder whose generation never produced anything) so they don't inflate per-category MISSING.
#
# Location: rtserver/streaming/<md5>/  (PROD: <appdata-rtserver>/streaming). See ffmpeg.txt /
# FfmpegExecutor (cass-server) and internal/SPEC_FOLDER_FILELOOKUP.md neighbours.
#
# Read-only. Safe to run against a live PROD index.
#
# Usage:
#   ./check_streaming_artifacts.sh [--appdata-rt DIR] [--appdata DIR] [--verbose] [--show-ok]
#                                  [--csv [FILE]] [--issues-only]
#
# Defaults (macOS PROD layout):
#   --appdata-rt  ~/Library/Application Support/hivebot/rtserver   (holds streaming/)
#   --appdata     <sibling scrubber dir>                           (holds Super2/paths for md5->path)
#
# --csv [FILE]   write one row PER streaming folder for spreadsheet import:
#                  md5,filename,path,status,thumbnail,hls,transcription,thumb_reason,hls_reason,transcript_reason
#                With no FILE (or "-"), CSV goes to stdout and the human report is suppressed.
#                With a FILE, CSV is written there AND the human report still prints.
# --issues-only  with --csv: exclude status=OK folders (only INCOMPLETE + EMPTY rows).
#
# Exit code: 0 if no folder is MISSING any category (EMPTY folders don't fail it); else 1.

set -u
export PATH=/usr/bin:/bin:/usr/sbin:/sbin

APPDATA_RT="$HOME/Library/Application Support/hivebot/rtserver"
APPDATA_SC=""    # scrubber dir (for Super2/paths md5->path lookup); defaults next to rtserver
VERBOSE=0
SHOW_OK=0
COLOR=auto
CSV_OUT=""       # "" = off; "-" = stdout; else a file path
ISSUES_ONLY=0    # 1 = CSV excludes status=OK rows (only INCOMPLETE + EMPTY)

# allow "--csv" with an OPTIONAL filename arg (defaults to stdout "-")
takes_optarg() { case "${1:-}" in --*|"") return 1;; *) return 0;; esac; }

while [ $# -gt 0 ]; do
    case "$1" in
        --appdata-rt) APPDATA_RT="$2"; shift 2;;
        --appdata)    APPDATA_SC="$2"; shift 2;;
        --verbose|-v) VERBOSE=1; shift;;
        --show-ok) SHOW_OK=1; shift;;
        --no-color) COLOR=never; shift;;
        --color) COLOR=always; shift;;
        --csv)  shift; if takes_optarg "${1:-}"; then CSV_OUT="$1"; shift; else CSV_OUT="-"; fi;;
        --issues-only) ISSUES_ONLY=1; shift;;
        -h|--help) sed -n '2,40p' "$0"; exit 0;;
        *) echo "Unknown arg: $1" >&2; exit 2;;
    esac
done

# When emitting CSV to STDOUT, suppress the human report so the stream is clean.
QUIET=0
[ "$CSV_OUT" = "-" ] && QUIET=1

# scrubber dir holds Super2/paths (md5 -> original file path). Default: sibling of rtserver.
[ -z "$APPDATA_SC" ] && APPDATA_SC="$(dirname "$APPDATA_RT")/scrubber"
PATHS_DIR="$APPDATA_SC/data/localdb/Super2/paths"

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

STREAM="$APPDATA_RT/streaming"

if [ ! -d "$STREAM" ]; then
    echo "ERROR: streaming dir not found at: $STREAM" >&2
    echo "       Pass the correct --appdata-rt (the rtserver dir holding streaming/)." >&2
    exit 2
fi

# Validate the --csv target up front (before the scan) so we don't do all the work and then fail
# the redirect. A target file is OK if its parent directory exists and is writable.
if [ -n "$CSV_OUT" ] && [ "$CSV_OUT" != "-" ]; then
    csv_dir="$(dirname "$CSV_OUT")"
    if [ ! -d "$csv_dir" ]; then
        echo "ERROR: cannot write CSV — directory does not exist: $csv_dir" >&2
        exit 2
    fi
    if [ ! -w "$csv_dir" ]; then
        echo "ERROR: cannot write CSV — directory not writable: $csv_dir" >&2
        exit 2
    fi
    if [ -e "$CSV_OUT" ] && [ ! -w "$CSV_OUT" ]; then
        echo "ERROR: cannot write CSV — file exists but is not writable: $CSV_OUT" >&2
        exit 2
    fi
fi

if [ $QUIET -eq 0 ]; then
    echo "=========================================================="
    echo " Streaming-artifacts validator (per-video HLS/thumb/transcript)"
    echo "=========================================================="
    echo " streaming : $STREAM"
    echo " (read-only; checks thumbnail / HLS / transcription per md5 folder)"
    echo "----------------------------------------------------------"
fi

# does this folder have at least one HLS .ts segment?
has_ts() {
    find "$1" -maxdepth 1 -name 'OUTPUT-*.ts' -print -quit 2>/dev/null | grep -q .
}
# file exists and is non-empty (>0 bytes)?
nonempty() { [ -s "$1" ]; }

# Resolve a video's ORIGINAL file path from its md5 + streaming folder.
# Prefers Super2/paths/<md5> (the canonical indexed path); falls back to the source path embedded
# in the folder's ffmpegscript.sh (-i '<path>'), which is always present even if the index was
# cleaned. Returns "" if neither is available. (Path may be on a now-disconnected volume — we just
# report the recorded string, we don't stat it.)
resolve_path() {
    local md5="$1" folder="$2" pf="$PATHS_DIR/$1" line rest p
    if [ -f "$pf" ]; then
        line=$(grep -v ',DELETED' "$pf" 2>/dev/null | head -1)   # first live entry
        if [ -n "$line" ]; then
            rest="${line#*:}"        # "<absolutePath>/,<value>"
            p="${rest%/,*}"          # "<absolutePath>"  (split on last "/,", comma-in-name safe)
            [ -n "$p" ] && { printf '%s' "$p"; return; }
        fi
    fi
    # fallback: ffmpegscript.sh line 1 has "-i '<source>'"
    p=$(sed -n '1p' "$folder/ffmpegscript.sh" 2>/dev/null | grep -oE "\-i '[^']*'" | head -1 | sed "s/-i '//; s/'\$//")
    printf '%s' "$p"
}

# Classify WHY the thumbnail is missing by parsing log.txt (the per-video ffmpeg run log).
# The thumbnail step is ffmpeg.txt line 2: "-ss 00:00:01.00 -i <video> ... -vframes 1 thumbnail.jpg".
# Prints one of: SHORT | CORRUPT-OPEN | CORRUPT-ENC | NOFRAME | NOLOG | UNKNOWN
# Signatures (verified against the live index, 84 failures classified with 0 unknown):
#   CORRUPT-OPEN : "Invalid data found when processing input" — ffmpeg can't even open the file
#                  (truly corrupt source; no Duration line). Bad input, not a bug.
#   SHORT        : Duration < 1s (00:00:00.xx) — the hardcoded "-ss 00:00:01.00" seeks past EOF,
#                  so no frame is produced. THIS IS A FIXABLE BUG in ffmpeg.txt line 2 (seek 0 or
#                  use the 'thumbnail' filter instead of a fixed 1s seek). See README / TODO_BUGS.
#   CORRUPT-ENC  : opens but "Could not open encoder before EOF" / "Invalid argument" — degenerate
#                  stream (e.g. 4 kb/s). Bad input.
#   NOFRAME      : "Nothing was written" without the above — other no-frame case.
#   NOLOG/UNKNOWN: no log.txt, or no recognized signature.
classify_thumb_fail() {
    local log="$1/log.txt"
    [ -f "$log" ] || { echo NOLOG; return; }
    if grep -q 'Invalid data found when processing input' "$log" 2>/dev/null; then echo CORRUPT-OPEN; return; fi
    local dur
    dur=$(grep -m1 'Duration:' "$log" 2>/dev/null | sed -E 's/.*Duration: ([0-9:.]+).*/\1/')
    case "$dur" in 00:00:00.*) echo SHORT; return;; esac
    if grep -qE 'Could not open encoder before EOF|Invalid argument' "$log" 2>/dev/null; then echo CORRUPT-ENC; return; fi
    if grep -q 'Nothing was written' "$log" 2>/dev/null; then echo NOFRAME; return; fi
    echo UNKNOWN
}

# Classify WHY HLS is missing (no OUTPUT.m3u8 or no OUTPUT-*.ts). ffmpeg.txt line 1 (libx264 ssegment).
# Prints: CORRUPT-OPEN | CORRUPT-ENC | NOFRAME | NOLOG | UNKNOWN
classify_hls_fail() {
    local log="$1/log.txt"
    [ -f "$log" ] || { echo NOLOG; return; }
    if grep -q 'Invalid data found when processing input' "$log" 2>/dev/null; then echo CORRUPT-OPEN; return; fi
    if grep -qE 'Could not open encoder|Error while opening encoder|Invalid argument' "$log" 2>/dev/null; then echo CORRUPT-ENC; return; fi
    if grep -q 'Nothing was written' "$log" 2>/dev/null; then echo NOFRAME; return; fi
    echo UNKNOWN
}

# Classify WHY transcription is missing (audio.json absent or 0-byte). ffmpeg.txt lines 3 (extract
# audio.aac) + 4 (curl whisper). Prints: CORRUPT-OPEN | NO-AUDIO | WHISPER-FAIL | NOLOG | UNKNOWN
#   CORRUPT-OPEN : source couldn't be opened to extract audio ("Invalid data found").
#   NO-AUDIO     : audio.aac absent/0-byte -> no audio stream to transcribe; curl then logs
#                  "(26) Failed to open/read local data" because it had no input. Expected for
#                  silent / no-audio-track videos. Not a whisper problem.
#   WHISPER-FAIL : audio.aac IS present & non-empty, but transcription still produced no json —
#                  a real LocalAI/whisper failure (hang, timeout, crash, empty reply). THIS is the
#                  class worth investigating (cf. the whisper-curl-no-timeout issue, B21/TODO_BUGS).
classify_transcript_fail() {
    local d="$1" log="$1/log.txt"
    [ -f "$log" ] || { echo NOLOG; return; }
    if grep -q 'Invalid data found when processing input' "$log" 2>/dev/null; then echo CORRUPT-OPEN; return; fi
    if [ -s "$d/audio.aac" ]; then echo WHISPER-FAIL; return; fi   # had audio but no transcript
    echo NO-AUDIO
}

# --- structured-output record buffer (one TAB-separated row per folder; fields never contain TABs) ---
# columns: md5 filename path status thumbnail hls transcription thumb_reason hls_reason transcript_reason
REC_TSV="$(mktemp)"
trap 'rm -f "$REC_TSV"' EXIT
add_record() {
    # $1..$10 = the column values; join with TAB
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$@" >> "$REC_TSV"
}
# CSV-quote a field: wrap in quotes, double internal quotes
csv_q() { printf '"%s"' "$(printf '%s' "$1" | sed 's/"/""/g')"; }

folders=0
thumb_ok=0; thumb_miss=0
hls_ok=0;   hls_miss=0
tr_ok=0;    tr_miss=0
empty=0
MISS_LIST=""
EMPTY_LIST=""
# thumbnail-failure reason buckets
tf_short=0; tf_corrupt_open=0; tf_corrupt_enc=0; tf_noframe=0; tf_nolog=0; tf_unknown=0
TF_SHORT_LIST=""
# HLS-failure reason buckets
hf_corrupt_open=0; hf_corrupt_enc=0; hf_noframe=0; hf_nolog=0; hf_unknown=0
# transcription-failure reason buckets
xf_corrupt_open=0; xf_noaudio=0; xf_whisper=0; xf_nolog=0; xf_unknown=0
XF_WHISPER_LIST=""

for d in "$STREAM"/*/; do
    [ -d "$d" ] || continue
    md5="${d%/}"; md5="${md5##*/}"
    folders=$((folders+1))

    # --- thumbnail (classify the failure reason from log.txt when missing) ---
    t_thumb_reason=""
    if nonempty "$d/thumbnail.jpg"; then
        t_thumb=OK; thumb_ok=$((thumb_ok+1))
    else
        t_thumb=MISSING; thumb_miss=$((thumb_miss+1))
        t_thumb_reason="$(classify_thumb_fail "$d")"
        case "$t_thumb_reason" in
            SHORT)        tf_short=$((tf_short+1)); TF_SHORT_LIST="${TF_SHORT_LIST}  $md5  $(resolve_path "$md5" "$d")"$'\n';;
            CORRUPT-OPEN) tf_corrupt_open=$((tf_corrupt_open+1));;
            CORRUPT-ENC)  tf_corrupt_enc=$((tf_corrupt_enc+1));;
            NOFRAME)      tf_noframe=$((tf_noframe+1));;
            NOLOG)        tf_nolog=$((tf_nolog+1));;
            *)            tf_unknown=$((tf_unknown+1));;
        esac
    fi

    # --- HLS: m3u8 + >=1 ts (classify the failure reason from log.txt when missing) ---
    t_hls_reason=""
    if [ -f "$d/OUTPUT.m3u8" ] && has_ts "$d"; then
        t_hls=OK; hls_ok=$((hls_ok+1))
    else
        t_hls=MISSING; hls_miss=$((hls_miss+1))
        t_hls_reason="$(classify_hls_fail "$d")"
        case "$t_hls_reason" in
            CORRUPT-OPEN) hf_corrupt_open=$((hf_corrupt_open+1));;
            CORRUPT-ENC)  hf_corrupt_enc=$((hf_corrupt_enc+1));;
            NOFRAME)      hf_noframe=$((hf_noframe+1));;
            NOLOG)        hf_nolog=$((hf_nolog+1));;
            *)            hf_unknown=$((hf_unknown+1));;
        esac
    fi

    # --- transcription: audio.json >0 bytes (classify the failure reason when missing) ---
    t_tr_reason=""
    if nonempty "$d/audio.json"; then
        t_tr=OK; tr_ok=$((tr_ok+1))
    else
        t_tr=MISSING; tr_miss=$((tr_miss+1))
        t_tr_reason="$(classify_transcript_fail "$d")"
        case "$t_tr_reason" in
            CORRUPT-OPEN) xf_corrupt_open=$((xf_corrupt_open+1));;
            NO-AUDIO)     xf_noaudio=$((xf_noaudio+1));;
            WHISPER-FAIL) xf_whisper=$((xf_whisper+1)); XF_WHISPER_LIST="${XF_WHISPER_LIST}  $md5  $(resolve_path "$md5" "$d")"$'\n';;
            NOLOG)        xf_nolog=$((xf_nolog+1));;
            *)            xf_unknown=$((xf_unknown+1));;
        esac
    fi

    # determine status; resolve the original file path.
    # Resolve for EVERY folder when CSV is requested (we emit a row per folder); otherwise only when
    # there's an issue (avoids 1200+ lookups on a clean run).
    if [ "$t_thumb" = MISSING ] && [ "$t_hls" = MISSING ] && [ "$t_tr" = MISSING ]; then
        status=EMPTY
    elif [ "$t_thumb" = MISSING ] || [ "$t_hls" = MISSING ] || [ "$t_tr" = MISSING ]; then
        status=INCOMPLETE
    else
        status=OK
    fi
    # resolve path for any issue folder; also for OK folders only if CSV needs them
    # (CSV requested AND not issues-only). Skips ~1100 lookups under --issues-only.
    vidpath=""
    if [ "$status" != OK ] || { [ -n "$CSV_OUT" ] && [ "$ISSUES_ONLY" -eq 0 ]; }; then
        vidpath="$(resolve_path "$md5" "$d")"
        [ "$status" != OK ] && [ -z "$vidpath" ] && vidpath="(path unknown — not in Super2/paths or ffmpegscript.sh)"
    fi

    # structured record (one row per folder; --issues-only excludes status=OK rows)
    if [ -n "$CSV_OUT" ] && { [ "$ISSUES_ONLY" -eq 0 ] || [ "$status" != OK ]; }; then
        add_record "$md5" "${vidpath##*/}" "$vidpath" "$status" \
                   "$t_thumb" "$t_hls" "$t_tr" \
                   "${t_thumb_reason:-}" "${t_hls_reason:-}" "${t_tr_reason:-}"
    fi

    # bucket folders with NONE of the three as EMPTY (don't double-count in MISSING lists)
    if [ "$status" = EMPTY ]; then
        empty=$((empty+1))
        EMPTY_LIST="${EMPTY_LIST}  EMPTY  $md5  $vidpath"$'\n'
        [ $VERBOSE -eq 1 ] && echo "$(cyellow EMPTY)   $md5  (no thumbnail / HLS / transcription)  -> $vidpath"
        continue
    fi

    # per-folder verbose line with each category colored (+ failure reason tags + source path)
    if [ $VERBOSE -eq 1 ]; then
        col() { [ "$1" = OK ] && cgreen "$2=OK" || cred "$2=MISSING"; }
        tt=""; [ -n "$t_thumb_reason" ] && tt=" [$t_thumb_reason]"
        th=""; [ -n "$t_hls_reason" ]   && th=" [$t_hls_reason]"
        tx=""; [ -n "$t_tr_reason" ]    && tx=" [$t_tr_reason]"
        ptag=""; [ -n "$vidpath" ] && ptag="  -> $vidpath"
        echo "  $md5  $(col "$t_thumb" thumb)$tt  $(col "$t_hls" hls)$th  $(col "$t_tr" transcript)$tx$ptag"
    fi

    # record folders that are missing at least one category (but have some output)
    if [ "$t_thumb" = MISSING ] || [ "$t_hls" = MISSING ] || [ "$t_tr" = MISSING ]; then
        miss=""
        [ "$t_thumb" = MISSING ] && miss="${miss}thumb[$t_thumb_reason] "
        [ "$t_hls"   = MISSING ] && miss="${miss}HLS[$t_hls_reason] "
        [ "$t_tr"    = MISSING ] && miss="${miss}transcript[$t_tr_reason] "
        MISS_LIST="${MISS_LIST}  $md5  MISSING: ${miss}"$'\n'"      -> $vidpath"$'\n'
    elif [ $SHOW_OK -eq 1 ]; then
        echo "$(cgreen OK)      $md5  (all artifacts present)"
    fi
done

# count INCOMPLETE folders by the per-entry "MISSING:" marker (each entry now spans 2 lines: md5 + path)
nmiss=$( [ -z "$MISS_LIST" ] && echo 0 || printf '%s' "$MISS_LIST" | grep -c 'MISSING:' )

# --- emit CSV (all folders) if requested ---
if [ -n "$CSV_OUT" ]; then
    {
        echo "md5,filename,path,status,thumbnail,hls,transcription,thumb_reason,hls_reason,transcript_reason"
        while IFS=$'\t' read -r c_md5 c_name c_path c_status c_th c_hl c_tr c_thr c_hlr c_trr; do
            printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
                "$(csv_q "$c_md5")" "$(csv_q "$c_name")" "$(csv_q "$c_path")" "$(csv_q "$c_status")" \
                "$(csv_q "$c_th")" "$(csv_q "$c_hl")" "$(csv_q "$c_tr")" \
                "$(csv_q "$c_thr")" "$(csv_q "$c_hlr")" "$(csv_q "$c_trr")"
        done < "$REC_TSV"
    } > "$( [ "$CSV_OUT" = "-" ] && echo /dev/stdout || echo "$CSV_OUT" )"
    [ "$CSV_OUT" != "-" ] && echo "CSV written: $CSV_OUT  ($(wc -l < "$REC_TSV" | tr -d ' ') rows)"
fi

if [ $QUIET -eq 1 ]; then
    # structured output to stdout: skip the human report, but still exit with the right code
    [ "$nmiss" -eq 0 ] && exit 0 || exit 1
fi

echo "----------------------------------------------------------"
echo " RESULTS  ($folders streaming folders)"
echo "   THUMBNAIL    OK: $(cgreen "$thumb_ok")   MISSING: $(cnt_err "$thumb_miss")"
if [ "$thumb_miss" -gt 0 ]; then
    echo "      thumbnail MISSING breakdown (parsed from log.txt):"
    echo "        SHORT (<1s, -ss 1s past EOF — FIXABLE bug) : $(cnt_err "$tf_short")"
    echo "        CORRUPT cannot-open-input (bad source)     : $(cnt_warn "$tf_corrupt_open")"
    echo "        CORRUPT encoder-failed (bad source)        : $(cnt_warn "$tf_corrupt_enc")"
    [ "$tf_noframe" -gt 0 ] && echo "        NOFRAME (other no-frame)                   : $(cnt_warn "$tf_noframe")"
    [ "$tf_nolog"   -gt 0 ] && echo "        NOLOG (no log.txt)                         : $(cnt_warn "$tf_nolog")"
    [ "$tf_unknown" -gt 0 ] && echo "        UNKNOWN (unrecognized signature)           : $(cnt_err "$tf_unknown")"
fi
echo "   HLS          OK: $(cgreen "$hls_ok")   MISSING: $(cnt_err "$hls_miss")"
if [ "$hls_miss" -gt 0 ]; then
    echo "      HLS MISSING breakdown (parsed from log.txt):"
    echo "        CORRUPT cannot-open-input (bad source) : $(cnt_warn "$hf_corrupt_open")"
    echo "        CORRUPT encoder-failed (bad source)    : $(cnt_warn "$hf_corrupt_enc")"
    [ "$hf_noframe" -gt 0 ] && echo "        NOFRAME (other no-frame)               : $(cnt_warn "$hf_noframe")"
    [ "$hf_nolog"   -gt 0 ] && echo "        NOLOG (no log.txt)                     : $(cnt_warn "$hf_nolog")"
    [ "$hf_unknown" -gt 0 ] && echo "        UNKNOWN (unrecognized signature)       : $(cnt_err "$hf_unknown")"
fi
echo "   TRANSCRIPTION OK: $(cgreen "$tr_ok")   MISSING: $(cnt_err "$tr_miss")"
if [ "$tr_miss" -gt 0 ]; then
    echo "      TRANSCRIPTION MISSING breakdown (parsed from log.txt):"
    echo "        CORRUPT cannot-open-input (bad source)        : $(cnt_warn "$xf_corrupt_open")"
    echo "        NO-AUDIO (no audio.aac — silent/no-track)     : $(cnt_warn "$xf_noaudio")"
    echo "        WHISPER-FAIL (had audio, transcript failed)   : $(cnt_err "$xf_whisper")"
    [ "$xf_nolog"   -gt 0 ] && echo "        NOLOG (no log.txt)                            : $(cnt_warn "$xf_nolog")"
    [ "$xf_unknown" -gt 0 ] && echo "        UNKNOWN (unrecognized signature)              : $(cnt_err "$xf_unknown")"
fi
echo "   EMPTY folders   : $(cnt_warn "$empty")   (none of the 3 artifacts — likely stub/non-video; not counted as a per-category gap)"
echo "----------------------------------------------------------"

if [ "$tf_short" -gt 0 ]; then
    echo ""
    echo " FIXABLE — thumbnails that failed only because the video is <1s (the -ss 00:00:01.00 seek):"
    printf '%s' "$TF_SHORT_LIST"
    echo "   -> fix ffmpeg.txt line 2 (seek 0 or use the 'thumbnail' filter). See TODO_BUGS."
fi
if [ "$xf_whisper" -gt 0 ]; then
    echo ""
    echo " WHISPER-FAIL — had audio.aac but transcription produced no json (real LocalAI/whisper failure):"
    printf '%s' "$XF_WHISPER_LIST"
    echo "   -> investigate LocalAI / whisper-curl (hang/timeout/empty reply). See B21/TODO_BUGS."
fi

# folders missing >=1 category (excluding fully-EMPTY)
if [ -n "$MISS_LIST" ]; then
    echo ""
    echo " INCOMPLETE — folders with some output but missing >=1 artifact ($nmiss):"
    printf '%s' "$MISS_LIST"
fi
if [ -n "$EMPTY_LIST" ] && [ $VERBOSE -eq 1 ]; then
    echo ""
    echo " EMPTY folders (no artifacts):"
    printf '%s' "$EMPTY_LIST"
elif [ "$empty" -gt 0 ]; then
    echo ""
    echo " ($empty EMPTY folders hidden; re-run with --verbose to list them)"
fi

# exit non-zero if ANY category is missing in a non-empty folder (a real generation gap)
[ "$nmiss" -eq 0 ] && exit 0 || exit 1

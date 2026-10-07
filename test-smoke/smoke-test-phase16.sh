#!/bin/bash
#
# SMOKE TEST — Phase 16: File types, end to end (scan → index → transcode → stream)
#
# Functional half of PROJECT_TAB_ADMIN_FILETYPES (epic #3270, M6b). Phase 15 tests the endpoints; this
# phase proves the scanner and the rest of the pipeline obey them:
#   - a type added to the catalog's "Video Files" group (.mxf) is indexed, grouped as video, transcoded to
#     browser-playable HLS (H.264 4:2:0) and streams via getvideo.m3u8 + getts.fn — same for .avi/.webm/.wmv
#   - .braw (no stock ffmpeg decoder) is indexed but never transcoded and never grouped as video
#   - an admin-added type in "Other" is indexed; after removing it from the catalog the indexed file still
#     renders in query.fn (null-safe get_thumb), and a NEW file of that type is no longer indexed
#
# Test media is generated with ffmpeg (testsrc) and dropped straight into a scan root ($MOBILEBACKUP), so the
# scanner sees it on its next pass. The FileExtensions config is snapshotted and restored on exit, and the
# generated files + their streaming folders are removed. Skipped in REMOTE mode.
#
# Total: 10 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

skip_phase_if_remote "needs the server's scan root and scanner config on this machine" "PHASE 16"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 16: File types end to end${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

printf "${BOLD}── Phase 16: scan → index → transcode → stream ──${RESET}\n"

INDEX_TIMEOUT="${PHASE16_INDEX_TIMEOUT:-150}"   # seconds to wait for a scan pass + indexing batch
FF="$REPO_ROOT/ffmpeg"; [ -x "$FF" ] || FF="$(command -v ffmpeg || true)"
STREAMING_DIR="$(cd "$INCOMING/.." 2>/dev/null && pwd)/streaming"

CFG="$SCAN_CONFIG_DIR"
F_SEL="$CFG/FileExtensions.txt"; F_ALL="$CFG/FileExtensions_All.txt"; F_CUS="$CFG/FileExtensions_Custom.txt"
SNAP="$(mktemp -d "${TMPDIR:-/tmp}/smoke16.XXXXXX")"
cp "$F_SEL" "$SNAP/sel" && cp "$F_ALL" "$SNAP/all"
HAD_CUSTOM=false; [ -f "$F_CUS" ] && { cp "$F_CUS" "$SNAP/cus"; HAD_CUSTOM=true; }

RAND_SUFFIX=$(date +%s)$(printf "%04d" $((RANDOM % 10000)))
DROP="$MOBILEBACKUP"
mkdir -p "$DROP"
TAG="smoke16v${RAND_SUFFIX}"        # unique filename stem -> exact query.fn lookups
CUSTOM_EXT=".s16t${RAND_SUFFIX: -6}" # admin-added "Other" type, unique per run
CREATED=()
MD5S=()

cleanup() {
    local f m
    for f in "${CREATED[@]+"${CREATED[@]}"}"; do rm -f "$f"; done
    for m in "${MD5S[@]+"${MD5S[@]}"}"; do [ -n "$m" ] && [ -d "$STREAMING_DIR/$m" ] && rm -rf "$STREAMING_DIR/$m"; done
    cmp -s "$SNAP/sel" "$F_SEL" || cp "$SNAP/sel" "$F_SEL"
    cmp -s "$SNAP/all" "$F_ALL" || cp "$SNAP/all" "$F_ALL"
    if $HAD_CUSTOM; then cmp -s "$SNAP/cus" "$F_CUS" || cp "$SNAP/cus" "$F_CUS"; else rm -f "$F_CUS"; fi
    rm -rf "$SNAP"
}
trap cleanup EXIT

jget() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1]); v=eval(sys.argv[2])
    print(json.dumps(v) if isinstance(v,(list,dict,bool)) or v is None else v)
except Exception as e:
    print("ERR:"+str(e))' "$1" "$2"
}
save() { curl -s -H "Cookie: uuid=$UUID" -H "X-Alt-Request: 1" "$SERVER/cass/setfiletypes-json.fn?ftpayload=$(urlenc "$1")"; }
get_cfg() { curl -s -H "Cookie: uuid=$UUID" "$SERVER/cass/getfiletypes-json.fn?_t=$RANDOM$RANDOM"; }
# query_file <exact filename> -> the query.fn record (JSON) or empty
query_file() {
    curl -s -H "Cookie: uuid=$UUID" "$SERVER/cass/query.fn?view=json&ftype=.all&foo=$(urlenc "$1")&days=0&numobj=10" \
      | python3 -c 'import sys,json
try:
    d=json.load(sys.stdin); n=sys.argv[1]
    m=[x for x in d.get("fighters",[]) if x.get("name")==n]
    print(json.dumps(m[0]) if m else "")
except Exception: print("")' "$1"
}
# wait_indexed <filename...> -> sets WAIT_SECS, returns 0 when all are in query.fn
wait_indexed() {
    local i n ok
    for i in $(seq 1 "$INDEX_TIMEOUT"); do
        ok=true
        for n in "$@"; do [ -n "$(query_file "$n")" ] || { ok=false; break; }; done
        $ok && { wait_done; WAIT_SECS=$i; return 0; }
        wait_msg "Waiting for scan + index of $# file(s)" "$i"; sleep 1
    done
    wait_done; WAIT_SECS=$INDEX_TIMEOUT; return 1
}

# ─── 16.1 setup: generate media + configure types through the admin API ───
test_start "16.1 setup — test media + file-type config"
SETUP_ERR=""
[ -n "$FF" ] || SETUP_ERR="no ffmpeg (expected \$REPO_ROOT/ffmpeg or on PATH)"
[ -d "$DROP" ] || SETUP_ERR="${SETUP_ERR:+$SETUP_ERR; }scan-root drop dir missing: $DROP"
gen() {   # gen <file> <ffmpeg output args...>
    local out="$1"; shift
    "$FF" -hide_banner -loglevel error -y -f lavfi -i testsrc=size=320x240:rate=25 -f lavfi -i sine=frequency=440 -t 3 "$@" "$out" \
        && CREATED+=("$out")
}
if [ -z "$SETUP_ERR" ]; then
    gen "$DROP/$TAG.avi"  -c:v mpeg4 -c:a mp3 || SETUP_ERR="avi"
    gen "$DROP/$TAG.webm" -c:v libvpx -b:v 300k -c:a libvorbis || SETUP_ERR="${SETUP_ERR} webm"
    gen "$DROP/$TAG.wmv"  -c:v wmv2 -c:a wmav2 || SETUP_ERR="${SETUP_ERR} wmv"
    # 10-bit 4:2:2 H.264 in MXF, like pro camera footage — exercises the -pix_fmt yuv420p normalisation
    gen "$DROP/$TAG.mxf"  -c:v libx264 -pix_fmt yuv422p10le -c:a pcm_s16le -ar 48000 2>/dev/null \
        || gen "$DROP/$TAG.mxf" -c:v mpeg2video -pix_fmt yuv422p -b:v 20M -c:a pcm_s16le -ar 48000 || SETUP_ERR="${SETUP_ERR} mxf"
    head -c 65536 /dev/urandom > "$DROP/$TAG.braw" && CREATED+=("$DROP/$TAG.braw")   # BRAW stand-in: ffmpeg can't read real BRAW either
    printf 'phase16 custom type %s\n' "$RAND_SUFFIX" > "$DROP/$TAG$CUSTOM_EXT" && CREATED+=("$DROP/$TAG$CUSTOM_EXT")
fi
if [ -z "$SETUP_ERR" ]; then
    CFGJ=$(get_cfg "")
    VER=$(jget "$CFGJ" 'd["version"]')
    PAYLOAD=$(python3 - "$CFGJ" "$VER" "$CUSTOM_EXT" <<'EOF'
import sys,json
cfg=json.loads(sys.argv[1]); ver=sys.argv[2]; cext=sys.argv[3]
known={t["ext"] for g in cfg["groups"] for t in g["types"]}
sel={t["ext"] for g in cfg["groups"] for t in g["types"] if t["selected"]}
add=[]
for ext,desc,grp in [(".mxf","MXF video","video"),(".braw","Blackmagic RAW","video"),(".avi","AVI video","video"),
                     (".webm","WebM video","video"),(".wmv","Windows Media Video","video"),(cext,"Smoke test type","others")]:
    if ext not in known: add.append({"ext":ext,"description":desc,"group":grp})
    sel.add(ext)
print(json.dumps({"selected":sorted(sel),"add":add,"remove":[],"version":ver}))
EOF
)
    RESP=$(save "$PAYLOAD")
    [ "$(jget "$RESP" 'd.get("success")')" = "true" ] || SETUP_ERR="config save: $(echo "$RESP" | head -c 120)"
fi
if [ -z "$SETUP_ERR" ]; then
    pass "16.1 generated avi/webm/wmv/mxf/braw/$CUSTOM_EXT in scan root; types selected via setfiletypes-json.fn"
else
    fail "16.1 setup" "$SETUP_ERR"
    print_summary "PHASE 16"
    exit 1
fi

# ─── 16.2 every new type is picked up by the scanner and indexed ───
test_start "16.2 scanner indexes the newly selected types"
ALL_NAMES=("$TAG.avi" "$TAG.webm" "$TAG.wmv" "$TAG.mxf" "$TAG.braw" "$TAG$CUSTOM_EXT")
if wait_indexed "${ALL_NAMES[@]}"; then
    pass "16.2 all 6 files indexed (${WAIT_SECS}s)"
else
    MISSING=""; for n in "${ALL_NAMES[@]}"; do [ -n "$(query_file "$n")" ] || MISSING="$MISSING $n"; done
    fail "16.2 indexing" "not indexed after ${INDEX_TIMEOUT}s:$MISSING"
fi

for n in "${ALL_NAMES[@]}"; do MD5S+=("$(jget "$(query_file "$n")" 'd.get("nickname","")')"); done

# ─── 16.3 catalog video types are grouped as video with a stream URL ───
test_start "16.3 video types → file_group movie + video_url_webapp"
BAD=""
for n in "$TAG.avi" "$TAG.webm" "$TAG.wmv" "$TAG.mxf"; do
    R=$(query_file "$n")
    [ "$(jget "$R" 'd.get("file_group")')" = "movie" ] && jget "$R" 'd.get("video_url_webapp","")' | grep -q 'getvideo.m3u8' || BAD="$BAD $n"
done
if [ -z "$BAD" ]; then pass "16.3 avi/webm/wmv/mxf are videos with getvideo.m3u8 URLs"; else fail "16.3 video grouping" "wrong:$BAD"; fi

# ─── 16.4 they are transcoded to browser-playable HLS ───
test_start "16.4 transcoded to H.264 4:2:0 HLS"
BAD=""
for n in "$TAG.avi" "$TAG.webm" "$TAG.wmv" "$TAG.mxf"; do
    M=$(jget "$(query_file "$n")" 'd.get("nickname","")'); OK=false
    for i in $(seq 1 90); do
        [ -s "$STREAMING_DIR/$M/OUTPUT.m3u8" ] && [ -s "$STREAMING_DIR/$M/OUTPUT-00000.ts" ] && { OK=true; break; }
        wait_msg "Waiting for transcode of $n" "$i"; sleep 1
    done
    wait_done
    if $OK; then
        # `ffmpeg -i` with no output always exits 1; under pipefail that would abort the phase
        PF=$("$FF" -hide_banner -i "$STREAMING_DIR/$M/OUTPUT-00000.ts" 2>&1 | grep -m1 'Video:' | grep -oE 'yuvj?4[0-9]{2}p[0-9a-z]*' | head -1 || true)
        case "$PF" in yuv420p|yuvj420p) ;; *) BAD="$BAD $n(pix=$PF)";; esac
    else
        BAD="$BAD $n(no HLS)"
    fi
done
if [ -z "$BAD" ]; then pass "16.4 avi/webm/wmv/mxf → OUTPUT.m3u8 + 4:2:0 8-bit segments (10-bit 4:2:2 MXF normalised)"; else fail "16.4 transcode" "$BAD"; fi

# ─── 16.5 the MXF streams through getvideo.m3u8 + getts.fn ───
test_start "16.5 MXF streams via getvideo.m3u8 + getts.fn"
M=$(jget "$(query_file "$TAG.mxf")" 'd.get("nickname","")')
MAN=$(curl -s "$SERVER/cass/getvideo.m3u8?md5=$M&uuid=$UUID")
SEG=$(echo "$MAN" | grep -m1 'getts.fn')
BYTES=0; [ -n "$SEG" ] && BYTES=$(curl -s "$SERVER/cass${SEG#/cass}" | wc -c | tr -d ' ')
if echo "$MAN" | grep -q '#EXTINF' && [ "${BYTES:-0}" -gt 1000 ]; then
    pass "16.5 manifest has $(echo "$MAN" | grep -c '#EXTINF') segment(s); first segment ${BYTES} bytes"
else
    fail "16.5 MXF stream" "manifest/segment missing (segment bytes=$BYTES)"
fi

# ─── 16.6 .braw: indexed, but not video and never sent to ffmpeg ───
test_start "16.6 .braw indexed but never transcoded"
R=$(query_file "$TAG.braw"); M=$(jget "$R" 'd.get("nickname","")')
sleep 5   # give a scan pass the chance to (wrongly) start a transcode
if [ -n "$R" ] && [ "$(jget "$R" 'd.get("file_group")')" != "movie" ] && [ "$(jget "$R" 'd.get("video_url_webapp","")')" = "" ] \
   && [ ! -e "$STREAMING_DIR/$M" ]; then
    pass "16.6 .braw: file_group=$(jget "$R" 'd.get("file_group")'), no stream URL, no streaming folder"
else
    fail "16.6 braw" "group=$(jget "$R" 'd.get("file_group")') url=$(jget "$R" 'd.get("video_url_webapp","")') streaming=$([ -e "$STREAMING_DIR/$M" ] && echo yes || echo no)"
fi

# ─── 16.7 folder listing flags videos (and not .braw) ───
test_start "16.7 getfolders-json video flag"
FL=$(curl -s -H "Cookie: uuid=$UUID" "$SERVER/cass/getfolders-json.fn?sFolder=$(urlenc "$DROP")")
FLAGS=$(python3 -c 'import sys,json
d=json.loads(sys.argv[1]); t=sys.argv[2]
print(" ".join("%s=%s" % (x["name"].split(".")[-1], str(x.get("video")).lower()) for x in d if x.get("name","").startswith(t)))' "$FL" "$TAG" 2>/dev/null || true)
if echo "$FLAGS" | grep -q 'mxf=true' && echo "$FLAGS" | grep -q 'avi=true' && echo "$FLAGS" | grep -q 'braw=false'; then
    pass "16.7 folder view flags: $FLAGS"
else
    fail "16.7 folder video flag" "$FLAGS"
fi

# ─── 16.8 remove the custom type: the indexed file still renders in query.fn ───
test_start "16.8 removed type: indexed file still renders (null-safe get_thumb)"
CFGJ=$(get_cfg ""); VER=$(jget "$CFGJ" 'd["version"]')
SEL=$(jget "$CFGJ" '[t["ext"] for g in d["groups"] for t in g["types"] if t["selected"]]')
RESP=$(save "{\"selected\":$SEL,\"remove\":[\"$CUSTOM_EXT\"],\"version\":\"$VER\"}")
R=$(query_file "$TAG$CUSTOM_EXT")
if [ "$(jget "$RESP" 'd.get("success")')" = "true" ] && [ -n "$R" ]; then
    pass "16.8 $CUSTOM_EXT removed from the catalog; its indexed file still returned by query.fn"
else
    fail "16.8 removed type renders" "save=$(echo "$RESP" | head -c 80) query=$(echo "$R" | head -c 80)"
fi

# ─── 16.9 a NEW file of the removed type is not indexed ───
test_start "16.9 removed type: new files are no longer indexed"
NEWF="$DROP/${TAG}b$CUSTOM_EXT"
printf 'phase16 after removal\n' > "$NEWF" && CREATED+=("$NEWF")
# a control file of a still-selected type proves a scan pass completed
CTRL="$DROP/${TAG}ctrl.wmv"
gen "$CTRL" -c:v wmv2 -c:a wmav2
if wait_indexed "${TAG}ctrl.wmv"; then
    sleep 3
    if [ -z "$(query_file "${TAG}b$CUSTOM_EXT")" ]; then
        pass "16.9 control .wmv indexed (${WAIT_SECS}s) but the new $CUSTOM_EXT file was not"
    else
        fail "16.9 removed type" "new $CUSTOM_EXT file was indexed after its type was removed"
    fi
else
    fail "16.9 removed type" "control file never indexed — can't tell"
fi
MD5S+=("$(jget "$(query_file "${TAG}ctrl.wmv")" 'd.get("nickname","")')")

# ─── 16.10 config restored ───
test_start "16.10 restore file-type config"
cleanup; trap - EXIT
CHECK=$(get_cfg "")
if [ "$(jget "$CHECK" 'd.get("success")')" = "true" ] && ! grep -q "^$CUSTOM_EXT," "$F_ALL"; then
    pass "16.10 FileExtensions config restored from snapshot; test media + streams removed"
else
    fail "16.10 restore" "$(echo "$CHECK" | head -c 100)"
fi

print_summary "PHASE 16"

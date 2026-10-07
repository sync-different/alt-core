#!/bin/bash
#
# SMOKE TEST — Phase 15: Admin File Types (uiv5 Admin "File types" tab)
#
# Covers the endpoints added by PROJECT_TAB_ADMIN_FILETYPES (epic #3270, M1/M2):
#   - getfiletypes-json.fn   catalog + selection, admin only
#   - setfiletypes-json.fn   save selection + add/remove admin-added types; admin only AND
#                            requires the "X-Alt-Request: 1" header (CSRF guard)
#
# The scanner indexes only the types in FileExtensions.txt (reloaded every scan pass), so this
# phase WRITES the server's scanner config. It snapshots FileExtensions.txt / FileExtensions_All.txt /
# FileExtensions_Custom.txt first and restores them on exit (trap), even if a test fails.
# Skipped in REMOTE mode: never rewrite a remote server's scan config.
#
# Total: 18 tests
#

source "$(cd "$(dirname "$0")" && pwd)/smoke-common.sh"

skip_phase_if_remote "rewrites the server's scanner file-type config" "PHASE 15"

printf "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n"
printf "${BOLD}${CYAN}  PHASE 15: Admin File Types${RESET}\n"
printf "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}\n\n"

printf "${BOLD}── Phase 15: File types ──${RESET}\n"

CFG="$SCAN_CONFIG_DIR"
F_SEL="$CFG/FileExtensions.txt"
F_ALL="$CFG/FileExtensions_All.txt"
F_CUS="$CFG/FileExtensions_Custom.txt"
SNAP="$(mktemp -d "${TMPDIR:-/tmp}/smoke15.XXXXXX")"
cp "$F_SEL" "$SNAP/sel" && cp "$F_ALL" "$SNAP/all"
HAD_CUSTOM=false
[ -f "$F_CUS" ] && { cp "$F_CUS" "$SNAP/cus"; HAD_CUSTOM=true; }

restore_config() {
    cmp -s "$SNAP/sel" "$F_SEL" || cp "$SNAP/sel" "$F_SEL"
    cmp -s "$SNAP/all" "$F_ALL" || cp "$SNAP/all" "$F_ALL"
    if $HAD_CUSTOM; then cmp -s "$SNAP/cus" "$F_CUS" || cp "$SNAP/cus" "$F_CUS"; else rm -f "$F_CUS"; fi
    rm -f "$F_SEL.tmp" "$F_ALL.tmp" "$F_CUS.tmp"
    rm -rf "$SNAP"
}
trap restore_config EXIT

# JSON field helper: jq-free, python3 is already a suite requirement.
#   jget '<json>' 'expr'   — expr is python over `d` (the parsed object)
jget() {
    python3 -c 'import sys,json
try:
    d=json.loads(sys.argv[1])
    v=eval(sys.argv[2])
    print(json.dumps(v) if isinstance(v,(list,dict,bool)) or v is None else v)
except Exception as e:
    print("ERR:"+str(e))' "$1" "$2"
}

# save <uuid> <payload-json> [noheader]
save() {
    local HDR=(-H "X-Alt-Request: 1")
    [ "${3:-}" = "noheader" ] && HDR=()
    curl -s -H "Cookie: uuid=$1" "${HDR[@]+"${HDR[@]}"}" \
        "$SERVER/cass/setfiletypes-json.fn?ftpayload=$(urlenc "$2")"
}
get_cfg() { curl -s -H "Cookie: uuid=$1" "$SERVER/cass/getfiletypes-json.fn?_t=$(date +%s%N)"; }
selected_keys() { cut -d, -f1 "$F_SEL" | tr -d '\r' | grep -v '^$'; }
invariant_ok() {   # every selected key is in the catalog
    local k; for k in $(selected_keys); do grep -q "^${k}," "$F_ALL" || return 1; done; return 0
}

# Non-admin test user (created + deleted here; random suffix avoids collisions on aborted runs)
RAND_SUFFIX=$(date +%s)$(printf "%04d" $((RANDOM % 10000)))
NUSER="phase15_${RAND_SUFFIX}"
NPASS="P15user!${RAND_SUFFIX}"
curl_auth "$SERVER/cass/adduser.fn?boxuser=${NUSER}&boxpass=$(urlenc "$NPASS")&useremail=${NUSER}%40test.invalid" >/dev/null
NUUID=$(curl -s -D - -o /dev/null "$SERVER/cass/login.fn?boxuser=${NUSER}&boxpass=$(urlenc "$NPASS")" \
        | grep -i '^set-cookie: *uuid=' | sed -E 's/.*uuid=([^;]+).*/\1/' | tr -d '\r')

# ─── 15.1 admin GET: shape matches the files on disk ───
test_start "15.1 getfiletypes — admin"
CFGJ=$(get_cfg "$UUID")
VER0=$(jget "$CFGJ" 'd["version"]')
N_CAT=$(grep -c '^\.' "$F_ALL"); N_SEL=$(selected_keys | wc -l | tr -d ' ')
J_CAT=$(jget "$CFGJ" 'sum(len(g["types"]) for g in d["groups"])')
J_SEL=$(jget "$CFGJ" 'sum(t["selected"] for g in d["groups"] for t in g["types"])')
J_GRP=$(jget "$CFGJ" 'len(d["groups"])'); N_GRP=$(grep -c '^@,' "$F_ALL")
if [ "$(jget "$CFGJ" 'd["success"]')" = "true" ] && [ "$J_CAT" = "$N_CAT" ] && [ "$J_SEL" = "$N_SEL" ] && [ "$J_GRP" = "$N_GRP" ] && [ ${#VER0} -eq 16 ]; then
    pass "15.1 admin gets catalog ($J_GRP groups, $J_CAT types, $J_SEL selected, version $VER0)"
else
    fail "15.1 getfiletypes admin" "groups=$J_GRP/$N_GRP types=$J_CAT/$N_CAT selected=$J_SEL/$N_SEL version='$VER0' body=$(echo "$CFGJ" | head -c 120)"
fi

# ─── 15.2 anon GET denied ───
test_start "15.2 getfiletypes — no auth"
RESP=$(curl_noauth "$SERVER/cass/getfiletypes-json.fn")
if echo "$RESP" | grep -q '"success":false' && ! echo "$RESP" | grep -q '"groups"'; then
    pass "15.2 unauthenticated request denied"
else
    fail "15.2 getfiletypes no auth" "$(echo "$RESP" | head -c 120)"
fi

# ─── 15.3 non-admin GET denied ───
test_start "15.3 getfiletypes — non-admin"
RESP=$(get_cfg "$NUUID")
if [ -n "$NUUID" ] && echo "$RESP" | grep -q 'Permission denied'; then
    pass "15.3 non-admin session denied"
else
    fail "15.3 getfiletypes non-admin" "nuuid='${NUUID:0:8}…' $(echo "$RESP" | head -c 120)"
fi

# ─── 15.4 public-link token denied ───
test_start "15.4 getfiletypes — public-link token"
TOK=$(curl_auth "$SERVER/cass/gen_public.fn?boxuser=${NUSER}" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("uuid",""))' 2>/dev/null)
RESP=$(curl_noauth "$SERVER/cass/getfiletypes-json.fn?uuid=${TOK}")
if [ -n "$TOK" ] && echo "$RESP" | grep -q '"success":false'; then
    pass "15.4 public-link token treated as anonymous"
elif [ -z "$TOK" ]; then
    skip "15.4 public-link token" "could not mint a token for $NUSER"
else
    fail "15.4 getfiletypes public link" "$(echo "$RESP" | head -c 120)"
fi

SEL_JSON=$(jget "$CFGJ" '[t["ext"] for g in d["groups"] for t in g["types"] if t["selected"]]')

# ─── 15.5 save without X-Alt-Request header rejected, file unchanged ───
test_start "15.5 setfiletypes — missing CSRF header"
RESP=$(save "$UUID" "{\"selected\":[\".docx\"],\"version\":\"$VER0\"}" noheader)
if echo "$RESP" | grep -q 'X-Alt-Request' && cmp -s "$SNAP/sel" "$F_SEL"; then
    pass "15.5 rejected without X-Alt-Request; FileExtensions.txt unchanged"
else
    fail "15.5 missing header" "$(echo "$RESP" | head -c 120)"
fi

# ─── 15.6 non-admin save (with header) denied ───
test_start "15.6 setfiletypes — non-admin"
RESP=$(save "$NUUID" "{\"selected\":[\".docx\"],\"version\":\"$VER0\"}")
if echo "$RESP" | grep -q 'Permission denied' && cmp -s "$SNAP/sel" "$F_SEL"; then
    pass "15.6 non-admin save denied; file unchanged"
else
    fail "15.6 non-admin save" "$(echo "$RESP" | head -c 120)"
fi

# ─── 15.7 stale version → 409 ───
test_start "15.7 setfiletypes — stale version"
RESP=$(save "$UUID" "{\"selected\":$SEL_JSON,\"version\":\"0000000000000000\"}")
if [ "$(jget "$RESP" 'd.get("status")')" = "409" ] && [ "$(jget "$RESP" 'd.get("version")')" = "$VER0" ]; then
    pass "15.7 stale version rejected with 409 + current version"
else
    fail "15.7 stale version" "$(echo "$RESP" | head -c 120)"
fi

# ─── 15.8 empty selection → 400 ───
test_start "15.8 setfiletypes — empty selection"
RESP=$(save "$UUID" "{\"selected\":[],\"version\":\"$VER0\"}")
if [ "$(jget "$RESP" 'd.get("status")')" = "400" ] && cmp -s "$SNAP/sel" "$F_SEL"; then
    pass "15.8 empty selection rejected (at least one type required)"
else
    fail "15.8 empty selection" "$(echo "$RESP" | head -c 120)"
fi

# ─── 15.9 unknown type → 400 ───
test_start "15.9 setfiletypes — unknown type"
RESP=$(save "$UUID" "{\"selected\":[\".doc\",\".notatype\"],\"version\":\"$VER0\"}")
if [ "$(jget "$RESP" 'd.get("status")')" = "400" ] && cmp -s "$SNAP/sel" "$F_SEL"; then
    pass "15.9 unknown type rejected"
else
    fail "15.9 unknown type" "$(echo "$RESP" | head -c 120)"
fi

# ─── 15.10 payload text naming other endpoints → a single JSON reply ───
test_start "15.10 setfiletypes — payload can't trigger other handlers"
RESP=$(save "$UUID" "{\"selected\":[\"getfolders.fn\",\"query.fn\",\"nodeinfo.fn\"],\"version\":\"$VER0\"}")
if [ "$(jget "$RESP" 'd.get("status")')" = "400" ]; then
    pass "15.10 only setfiletypes-json.fn answered (fname scrubbed)"
else
    fail "15.10 fname scrub" "reply is not one JSON 400: $(echo "$RESP" | head -c 160)"
fi

# ─── 15.11 substring regression: only .docx selected must NOT enable .doc ───
test_start "15.11 setfiletypes — exact keys (docx ≠ doc)"
RESP=$(save "$UUID" "{\"selected\":[\".docx\"],\"version\":\"$VER0\"}")
if [ "$(jget "$RESP" 'd.get("success")')" = "true" ] && [ "$(selected_keys | tr '\n' ' ')" = ".docx " ]; then
    pass "15.11 selection is exactly .docx (legacy scan_docx→doc bug not present)"
else
    fail "15.11 exact keys" "file='$(selected_keys | tr '\n' ' ')' resp=$(echo "$RESP" | head -c 100)"
fi
V=$(jget "$RESP" 'd.get("version")')
RESP=$(save "$UUID" "{\"selected\":$SEL_JSON,\"version\":\"$V\"}")   # restore selection for the next tests
V=$(jget "$RESP" 'd.get("version")')

# ─── 15.12 add validation ───
test_start "15.12 setfiletypes — add validation"
BAD=0; BADLIST=""
for ADD in '{"ext":".pdf","description":"dup","group":"office"}' \
           '{"ext":".a/b","description":"bad ext","group":"others"}' \
           '{"ext":".waytoolongextension1","description":"long","group":"others"}' \
           '{"ext":".s15a","description":"a,b","group":"others"}' \
           '{"ext":".s15b","description":"query.fn","group":"others"}' \
           '{"ext":".s15c","description":"<script>","group":"others"}' \
           '{"ext":".s15d","description":"line\nbreak","group":"others"}' \
           '{"ext":".s15e","description":"no group","group":"nogroup"}'; do
    R=$(save "$UUID" "{\"selected\":$SEL_JSON,\"add\":[$ADD],\"version\":\"$V\"}")
    [ "$(jget "$R" 'd.get("status")')" = "400" ] || { BAD=$((BAD+1)); BADLIST="$BADLIST $ADD=>$(echo "$R" | head -c 60)"; }
done
if [ $BAD -eq 0 ] && cmp -s "$SNAP/all" "$F_ALL"; then
    pass "15.12 8 invalid additions rejected (dup, bad/long ext, ',' '.' '<>' CRLF in description, unknown group)"
else
    fail "15.12 add validation" "$BAD accepted:$BADLIST"
fi

# ─── 15.13 add a custom type (ticked by default) ───
test_start "15.13 setfiletypes — add custom type"
ST="smoke15${RANDOM}"
SEL_PLUS=$(python3 -c 'import sys,json;l=json.loads(sys.argv[1]);l.append(sys.argv[2]);print(json.dumps(l))' "$SEL_JSON" ".$ST")
RESP=$(save "$UUID" "{\"selected\":$SEL_PLUS,\"add\":[{\"ext\":\".$ST\",\"description\":\"Smoke test type\",\"group\":\"others\"}],\"version\":\"$V\"}")
V=$(jget "$RESP" 'd.get("version")')
LAST_OTHER=$(awk -F, '/^@,/{g=$3} /^\./{if(g~/others/) last=$1} END{print last}' "$F_ALL" | tr -d '\r')
if [ "$(jget "$RESP" 'd.get("catalogAdded")')" = "[\".$ST\"]" ] && [ "$LAST_OTHER" = ".$ST" ] \
   && grep -qx ".$ST" "$F_CUS" && selected_keys | grep -qx ".$ST" && invariant_ok; then
    pass "15.13 .$ST added at end of 'Other', recorded as custom, selected"
else
    fail "15.13 add custom" "lastOther=$LAST_OTHER resp=$(echo "$RESP" | head -c 120)"
fi

# ─── 15.14 remove a shipped type → 400 ───
test_start "15.14 setfiletypes — remove shipped type"
RESP=$(save "$UUID" "{\"selected\":$SEL_PLUS,\"remove\":[\".doc\"],\"version\":\"$V\"}")
if [ "$(jget "$RESP" 'd.get("status")')" = "400" ] && grep -q '^\.doc,' "$F_ALL"; then
    pass "15.14 shipped type can't be removed"
else
    fail "15.14 remove shipped" "$(echo "$RESP" | head -c 120)"
fi

# ─── 15.15 removing the only selected type → 400 (selection would be empty) ───
test_start "15.15 setfiletypes — remove last selected type"
R1=$(save "$UUID" "{\"selected\":[\".$ST\"],\"version\":\"$V\"}"); V=$(jget "$R1" 'd.get("version")')
RESP=$(save "$UUID" "{\"selected\":[\".$ST\"],\"remove\":[\".$ST\"],\"version\":\"$V\"}")
if [ "$(jget "$RESP" 'd.get("status")')" = "400" ] && grep -q "^\.$ST," "$F_ALL"; then
    pass "15.15 removal that would leave nothing selected is rejected"
else
    fail "15.15 remove last" "$(echo "$RESP" | head -c 120)"
fi
R2=$(save "$UUID" "{\"selected\":$SEL_PLUS,\"version\":\"$V\"}"); V=$(jget "$R2" 'd.get("version")')

# ─── 15.16 remove the custom type → gone from catalog, custom list and selection ───
test_start "15.16 setfiletypes — remove custom type"
RESP=$(save "$UUID" "{\"selected\":$SEL_PLUS,\"remove\":[\".$ST\"],\"version\":\"$V\"}")
V=$(jget "$RESP" 'd.get("version")')
if [ "$(jget "$RESP" 'd.get("catalogRemoved")')" = "[\".$ST\"]" ] && ! grep -q "^\.$ST," "$F_ALL" \
   && ! grep -qx ".$ST" "$F_CUS" && ! selected_keys | grep -qx ".$ST" && invariant_ok; then
    pass "15.16 .$ST removed from catalog, custom list and selection"
else
    fail "15.16 remove custom" "$(echo "$RESP" | head -c 120)"
fi

# ─── 15.17 round trip is byte-exact and the version is back ───
test_start "15.17 round trip byte-exact"
if cmp -s "$SNAP/sel" "$F_SEL" && cmp -s "$SNAP/all" "$F_ALL" && [ "$V" = "$VER0" ]; then
    pass "15.17 FileExtensions.txt + _All.txt identical to the snapshot; version $V restored"
else
    fail "15.17 round trip" "sel=$(cmp -s "$SNAP/sel" "$F_SEL" && echo same || echo DIFF) all=$(cmp -s "$SNAP/all" "$F_ALL" && echo same || echo DIFF) version=$V/$VER0"
fi

# ─── 15.18 invariant holds and no temp files left behind ───
test_start "15.18 invariant + no temp files"
if invariant_ok && [ ! -e "$F_SEL.tmp" ] && [ ! -e "$F_ALL.tmp" ] && [ ! -e "$F_CUS.tmp" ]; then
    pass "15.18 every selected type is in the catalog; no .tmp files"
else
    fail "15.18 invariant" "selected-not-in-catalog or leftover .tmp"
fi

# ─── Cleanup: delete the non-admin test user (best effort) ───
DEL=$(curl_auth "$SERVER/cass/deluser.fn?boxuser=${NUSER}" | tr -d '[:space:]')
[ "$DEL" = "success" ] || printf "${YELLOW}NOTE${RESET}  test user $NUSER not cleaned up (deluser returned '$DEL')\n"

print_summary "PHASE 15"

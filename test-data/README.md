# test-data — PROD data validation scripts

Standalone **shell scripts** (pure bash + `awk`/`find`/`grep`, no Java, no deps, no compile)
for validating that an alt-core index is internally consistent. Designed to be **copied to any
PROD machine** and run against its live `localdb` — all are **read-only** and safe to run on a
running server.

To use on a PROD box: copy the `.sh` file over (e.g. `scp`), `chmod +x`, run. Each script
auto-detects the macOS PROD layout (`~/Library/Application Support/hivebot/scrubber`) and reads
the scan root from `config/scan1.txt`, or you can override paths with flags.

## Scripts

### check_download_button.sh
Reports which indexed files would **not** show a Download button in the uiv5 Folder View
(or would show one pointing at the **wrong** content). Mirrors the server's
`WebServer.getFileMD5(name, fullPath)` resolution exactly — see
[`internal/SPEC_FOLDER_FILELOOKUP.md`](../internal/SPEC_FOLDER_FILELOOKUP.md).

Verdicts per file:
- **OK** — md5 resolves (button works). `OK*` in verbose = resolved by path-disambiguation among multiple same-name candidates.
- **NOT-INDEXED** — the file is not in the index at all (no `Standard1/<name>` key and not in `.all`). It simply hasn't been scanned yet — **informational, not a bug**, and does NOT affect the exit code. Run a scan, then re-check.
- **BUG** — the file IS in the index but its md5 won't resolve → Folder View shows no download button. This is the real defect class.
- **AMBIGUOUS** — same filename maps to 2+ different md5s and none matched the file's path → button appears but may serve the wrong content.

Multi-folder `scan1.txt` is supported: the script splits the `scandir` value on `;`, URL-decodes each
root (`%2F`, `%20`, `+`), warns-and-skips any root missing on this machine, and scans all valid roots
(mirrors `WebServer.java`'s `split(";")` + `URLDecoder.decode`).

Exit code: `0` if every file is OK or NOT-INDEXED; `1` only if a real **BUG** or **AMBIGUOUS** was found.

```bash
# default: macOS PROD layout, scan root from scan1.txt, all file types
./check_download_button.sh

# only video, show every file's verdict
./check_download_button.sh --ext mp4,mov --verbose

# point at a non-default install / scan root
./check_download_button.sh --appdata "/path/to/hivebot/scrubber" --scandir "/Volumes/Drive/media"
```

Exit code: `0` if every file resolves OK, `1` if any EMPTY or AMBIGUOUS were found
(so it can gate CI / cron alerts).

### check_path_integrity.sh
Orphan-index + path-integrity check, combined. Walks every MD5 path-file in `Super2/paths/`
(filename == md5; each line is `<uuid>:<absolutePath>/,<value>`, `value=="DELETED"` once removed)
and stats each live path against the filesystem.

Verdicts:
- **OK** — the indexed path exists on disk.
- **ORPHAN** — the index references a path that is **not** on disk **and its volume is mounted** (stale entry: file deleted/moved out-of-band, never went through the app's delete flow). The real integrity defect.
- **UNMOUNTED** — the path is on an external volume (`/Volumes/<NAME>`) that is **not currently mounted**, so existence can't be verified. **Informational, not a bug** — reconnect the drive and re-run. Prevents falsely flagging a disconnected drive's files as orphans. Detection: macOS removes the `/Volumes/<NAME>` mount-point dir when a drive unmounts; the boot volume keeps its firmlink so internal paths classify normally.
- **DELETED** — entry already marked `DELETED` in the index (informational, skipped).
- **EMPTYMD5** — an md5 path-file with no live entries (all DELETED/empty) (informational).

```bash
./check_path_integrity.sh                 # default PROD layout
./check_path_integrity.sh --verbose       # list every ORPHAN/DELETED/EMPTYMD5 + per-entry
./check_path_integrity.sh --show-ok       # also print every OK path
./check_path_integrity.sh --appdata "/path/to/hivebot/scrubber"
```

Exit code: `0` if no ORPHAN entries (UNMOUNTED/DELETED/EMPTYMD5 do not fail it); `1` if any orphaned paths exist.

**Parser note:** path lines are split on the **last `/,`** (the stored path always ends in `/`),
NOT the last comma — because the `<value>` field (a display name) can contain commas
(e.g. `Steve Jobs and Bill Gates Together in 2007, at D5..mov`). Splitting on the last comma
produces false ORPHANs. Same comma-in-name hazard as `check_download_button.sh`.

### check_index_coverage.sh
The **reverse** of `check_path_integrity.sh`: walks the scan-root folders and checks every on-disk
file that the scanner is *supposed* to index actually HAS an index entry. Catches files the scanner
silently missed (the complement of orphan detection).

- `check_path_integrity.sh` : index → filesystem (find ORPHAN index entries)
- `check_index_coverage.sh` : filesystem → index (find MISSING files)

"Indexed" proof: the file's exact path appears in some `Super2/paths/<md5>` entry. Scan roots are
read from `scan1.txt` (multi-folder + disconnected-volume aware, same as `check_download_button.sh`).

Verdicts:
- **INDEXED** — path present in `Super2/paths`.
- **MISSING** — the file passes the scanner's admission rules but has **no** path entry → the scanner missed it / index drift. The real coverage gap.
- **SKIPPED-BY-DESIGN** — correctly not indexed because it fails a scanner rule: extension not in `FileExtensions.txt`, or hidden / `._*`. Informational.

The scanner's admission rules mirrored here (from `FileUtils.ScanDirectory` / `checkFileType`):
1. extension (lowercased, with dot) must be a key in `config/FileExtensions.txt`, **and**
2. filename must not start with `.`.

**Scope note:** the scanner also skips **blacklisted directories** (`blacklist.txt`); this script does
**not** model the blacklist (extension+hidden only). Blacklisted dirs rarely sit inside scan roots, but
a file under one would show as MISSING here — triage accordingly.

```bash
./check_index_coverage.sh                  # default PROD layout, roots from scan1.txt
./check_index_coverage.sh --verbose        # list MISSING + every SKIPPED file
./check_index_coverage.sh --show-indexed   # also print every INDEXED file
./check_index_coverage.sh --scandir "/Volumes/Drive/media"
```

Exit code: `0` if no MISSING files (SKIPPED does not fail it); `1` if any expected-but-unindexed files exist.

### check_streaming_artifacts.sh
For each video's `rtserver/streaming/<md5>/` folder, verifies the three generated artifacts, with an
independent OK/MISSING verdict **per category** so an incomplete folder is pinpointed:

- **THUMBNAIL** — `thumbnail.jpg` present & non-empty (ffmpeg.txt line 2)
- **HLS** — `OUTPUT.m3u8` **and** ≥1 `OUTPUT-NNNNN.ts` segment (ffmpeg.txt line 1; all-or-nothing in practice)
- **TRANSCRIPTION** — `audio.json` present & **>0 bytes** (ffmpeg.txt line 4, whisper). A 0-byte `audio.json` = failed/hung transcription (e.g. the whisper-curl hang) → MISSING. A small non-empty `{"text":"[BLANK_AUDIO]"}` is a **valid** silent-video result → OK.

Folders with **none** of the three artifacts are bucketed as **EMPTY** (stub / failed-before-any-output), so they don't inflate the per-category MISSING counts. Folders with *some* output but ≥1 gap are listed as **INCOMPLETE** with exactly which categories are missing.

**All three categories' failures are classified by parsing `log.txt`**, so fixable/real issues are separated from bad input:

*THUMBNAIL* (ffmpeg.txt line 2):
- **SHORT** — video `<1s`, so the hardcoded `-ss 00:00:01.00` seek lands past EOF → no frame. **FIXABLE bug** (seek 0 or use the `thumbnail` filter). Listed under "FIXABLE".
- **CORRUPT-OPEN** — `Invalid data found`: source can't be opened. Bad input.
- **CORRUPT-ENC** — opens but encoder fails / degenerate stream. Bad input.
- NOFRAME / NOLOG / UNKNOWN — fallbacks.

*HLS* (ffmpeg.txt line 1):
- **CORRUPT-OPEN** / **CORRUPT-ENC** — same as above; almost all HLS failures are corrupt sources.
- NOFRAME / NOLOG / UNKNOWN — fallbacks.

*TRANSCRIPTION* (ffmpeg.txt lines 3+4):
- **CORRUPT-OPEN** — source couldn't be opened to extract audio.
- **NO-AUDIO** — `audio.aac` absent/0-byte → no audio stream to transcribe (silent / no-audio-track video). curl then logs `(26)`. Expected, not a whisper problem.
- **WHISPER-FAIL** — `audio.aac` **is** present & non-empty but transcription still produced no json → a **real LocalAI/whisper failure** (hang/timeout/crash/empty reply). Listed separately (cf. whisper-curl-no-timeout, B21/TODO_BUGS).
- NOLOG / UNKNOWN — fallbacks.

On the reference index (1247 folders): THUMBNAIL 84 = 24 SHORT + 55 CORRUPT-OPEN + 5 CORRUPT-ENC; HLS 56 = 55 CORRUPT-OPEN + 1 CORRUPT-ENC; TRANSCRIPTION 83 = 41 CORRUPT-OPEN + 42 NO-AUDIO + **0 WHISPER-FAIL** (no real whisper failures on this index — all transcription gaps are upstream: bad source or no audio). 0 unknown in any category.

```bash
./check_streaming_artifacts.sh                 # default PROD layout
./check_streaming_artifacts.sh --verbose       # per-folder colored thumb/hls/transcript verdicts
./check_streaming_artifacts.sh --show-ok       # also print fully-complete folders
./check_streaming_artifacts.sh --appdata-rt "/path/to/hivebot/rtserver"

# CSV export for spreadsheets — one row PER folder (all folders, incl. OK):
./check_streaming_artifacts.sh --csv report.csv    # write file + still print the human report
./check_streaming_artifacts.sh --csv > report.csv  # CSV to stdout (human report suppressed)
./check_streaming_artifacts.sh --csv report.csv --issues-only   # only INCOMPLETE + EMPTY rows (skip OK)
```

`--issues-only` (with `--csv`) drops the `status=OK` rows so the CSV contains just the problem folders
(e.g. 123 issue rows instead of all 1247 on the reference index). Also writes an early error and exits 2
if the `--csv` target directory doesn't exist or isn't writable (fails before the scan, not after).

CSV columns: `md5, filename, path, status, thumbnail, hls, transcription, thumb_reason, hls_reason, transcript_reason`
where `status` ∈ {OK, INCOMPLETE, EMPTY}, each category ∈ {OK, MISSING}, and the `*_reason` columns carry
the parsed failure reason (SHORT / CORRUPT-OPEN / NO-AUDIO / WHISPER-FAIL / …) or empty when OK. Fields are
quoted, so paths with spaces/commas import cleanly. Filter `status="INCOMPLETE"` in the spreadsheet to focus
on the issues, or pivot on the reason columns.

Every reported issue shows the video's **original filename + path** next to the md5, resolved from
`Super2/paths/<md5>` (canonical) with a fallback to the source path embedded in the folder's
`ffmpegscript.sh` (`-i '<path>'`) — so it works even for streaming variants (e.g. `<md5>-ori`) or when
the index was cleaned. (Path is the recorded string; not stat'd, so it shows even for disconnected drives.)

Note: streaming output lives under **rtserver/** (not scrubber/), so this script takes `--appdata-rt`
(the rtserver dir). It also reads `Super2/paths` from the scrubber dir for the md5→path lookup; that
defaults to the sibling `scrubber/` of `--appdata-rt`, override with `--appdata`.

Exit code: `0` if no non-EMPTY folder is missing a category; `1` if any INCOMPLETE folders exist.

## The four checks together

| Script | Looks at | Finds |
|--------|-----------|-------|
| `check_download_button.sh`    | disk → can the UI resolve an md5? | files with no / wrong download button |
| `check_path_integrity.sh`     | index → does each indexed path exist? | ORPHAN index entries |
| `check_index_coverage.sh`     | disk → is each file indexed?         | files the scanner MISSED |
| `check_streaming_artifacts.sh`| streaming/<md5> → are HLS/thumb/transcript generated? | incomplete video processing |

Cross-check: on a healthy index with **all volumes mounted**, `check_path_integrity` OK-count ==
`check_index_coverage` INDEXED-count == live entries in `Super2/paths` (1973 on the reference index —
all three agreed).

When a scan-root volume is **disconnected**, the counts legitimately diverge and the scripts stay
consistent with each other:
- `check_index_coverage` reports `index-only entries: N` (path entries not under a mounted root) — informational, not a gap.
- `check_path_integrity` reports those same N as `UNMOUNTED` (not ORPHAN).
- e.g. on a PROD Mini with `/Volumes/Expansion` unplugged: coverage showed `indexed-path set 800 / INDEXED 795 / index-only 5`, and path-integrity showed those 5 as UNMOUNTED. Both exit 0.

## Color output

All three scripts color their verdicts and summary counts:
- **green** — OK / INDEXED, and any defect count that is `0`
- **red** — real defects: BUG, ORPHAN, MISSING, AMBIGUOUS (count `> 0`)
- **yellow** — informational/warnings: NOT-INDEXED, UNMOUNTED, DELETED, EMPTYMD5, SKIPPED-BY-DESIGN, index-only, and "scan root not mounted" warnings

Color is **auto-enabled only when stdout is a terminal**, so piping to a file or cron stays plain text.
Force with `--color`, disable with `--no-color`.

## Conventions for new scripts here
- Read-only against `localdb` — never write to it.
- Auto-detect PROD layout; allow `--appdata` / `--scandir` overrides.
- Mirror the relevant server logic and cite the spec/source it mirrors.
- Print a clear summary + an exit code usable for alerting.
- Color: green=OK, red=defect, yellow=informational; auto-off when not a TTY (`--color`/`--no-color` to override).

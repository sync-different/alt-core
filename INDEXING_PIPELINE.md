# Indexing Pipeline — Technical Reference

This document describes the complete data flow from file upload to searchable/downloadable content, including the deletion pipeline.

## System Overview

```
Upload (port 8087, Netty)
  → rtserver/incoming/          upload.<filename>.b   (full)
                                upload.<filename>.N.M.p (chunked)
                                upload.<filename>.meta  (folder target)

ProcessorService (every ~10s)
  → Merges .p chunks → .b
  → Moves .b → destination folder (mobilebackup/upload/ or targetFolder)
  → Processes .D_ deletion notifications

FileScanner/ScannerService (every ~10s, but cycle takes ~3 min in PROD)
  → Scans directories listed in scan1.txt
  → Detects new/deleted files
  → Creates DatabaseEntry records (.a for new, .D_ for deleted)
  → ProcessorService picks these up for indexing

API Endpoints
  → query.fn      — searches Standard2 (autocomplete tokens) + Standard1 (keyword indexes)
  → getfile.fn    — resolves MD5 → file path via Super2/paths, serves file
  → getfileinfo.fn — reads file metadata from Standard1/<md5>
```

## Database Tables

All stored as flat files under `scrubber/data/localdb/`.

### Standard1 — File Metadata

```
Location: data/localdb/Standard1/
Structure: One file per row key, lines are name,value pairs

Per-file entries (row key = MD5 hash):
  name,<filename>
  ext,<extension>
  size,<bytes>
  date_added,<yyyy.MM.dd HH:mm:ss.SSS z>
  date_modified,<yyyy.MM.dd HH:mm:ss.SSS z>
  uuid_ori,<UUID of originating node>

Index entries (special row keys):
  .all          — global file index: date_modified,<md5>,<filepath>
  .ext          — by extension
  .photo        — photo files (.jpg, .jpeg, .gif, .png)
  .music        — music files (.mp3, .m4a, .m4p, .wma)
  .video        — video files (.mov, .mts, .m4v, .mp4, .m2ts)
  .document     — office files (.doc, .xls, .ppt, .pdf)
  batch@<id>    — files in a processing batch
```

**Key fact:** Standard1 per-file metadata (name, ext, size, etc.) is **NOT removed** during deletion. It persists indefinitely as an audit trail.

### Standard2 — Search/Autocomplete Tokens

```
Location: data/localdb/Standard2/
Structure: One file per search token (lowercase), lines are name,value pairs

Example: Standard2/pictures contains:
  Pictures,Pictures
  my-pictures.zip,my-pictures.zip

Populated by: ProcessorService.processFileRecordData_exp()
  - Tokenizes filename using configurable delimiters
  - Generates all substrings (min length = mMinSubstrLen, default 3)
  - Each substring gets a file mapping: token → filename
```

**Key fact:** Standard2 tokens are **NOT cleaned** during deletion. The `removeFromKeywordIndexes()` method only cleans Standard1 keyword index files, not Standard2.

### Super2/paths — File Locations

```
Location: data/localdb/Super2/paths/
Structure: One file per MD5 hash, lines are UUID:path,status

Example: Super2/paths/abc123def456...
  f47ac10b-58cc-4372-a567-0e02b2c3d479:/Users/name/Documents/file.txt/,/Users/name/Documents/file.txt/

After deletion:
  f47ac10b-58cc-4372-a567-0e02b2c3d479:/Users/name/Documents/file.txt/,DELETED
```

**Key fact:** Paths are **marked as DELETED**, not removed. The `isFileDeleted()` method checks if ALL entries in the file are marked DELETED.

**Used by:** `getfile.fn` → `read_view_link()` / `get_file_path()` — resolves MD5 to filesystem path. When path is marked DELETED, the file can no longer be served.

### Super2/hashes — Batch Tags

```
Location: data/localdb/Super2/hashes/
Structure: One file per MD5 hash, lines are batch tags

Example: Super2/hashes/abc123def456...
  batch@42,batch@42
```

### BatchJobs — Batch Tracking

```
Location: data/localdb/BatchJobs/
Row keys: "batchid" (latest ID counter), date keys "yyyy#MM#dd"
```

## Upload Flow

**Source:** `rtserver-maven/.../netty/HttpUploadServerHandler.java`

### Full Upload (non-chunked)
1. Client sends multipart POST to `http://host:8087/formpost`
2. Handler sanitizes filename (removes null bytes, `..`, `/`, `\`)
3. File saved as `incoming/upload.<sanitized_filename>.b`
4. If `targetFolder` form field present, creates `incoming/upload.<sanitized_filename>.meta` with `targetFolder=<path>`

### Chunked Upload (Dropzone.js)
1. Client sends chunks with `dzchunkindex` and `dztotalchunkcount` form fields
2. Each chunk saved as `incoming/upload.<filename>.<totalChunks>.<chunkIndex+1>.p`
3. Example: 3-chunk upload produces:
   - `upload.myfile.txt.3.1.p` (chunk 0 → index 1)
   - `upload.myfile.txt.3.2.p` (chunk 1 → index 2)
   - `upload.myfile.txt.3.3.p` (chunk 2 → index 3)
4. `.meta` file created on first chunk if targetFolder specified

## ProcessorService — File Processing

**Source:** `scrubber-maven/.../services/ProcessorService.java`
**Method:** `ScanProcessingDir()` (line ~463)
**Cycle:** Runs every `mPeriodMs` milliseconds (configurable, typically 10s)

### Processing .p Files (Chunk Merge)

Triggered when the last chunk arrives (`nropart == totalparts`):

1. Verify all chunks 1..totalparts exist in `incoming/`
2. Create merged output file: `upload.<filename>.b`
3. Read each chunk sequentially and append to output
4. Delete individual `.p` chunk files
5. Orphaned chunks (>1 hour old, incomplete set) are deleted

### Processing .b Files (Move to Destination)

1. Read `.meta` file if present → extract `targetFolder`
2. Validate `targetFolder` is within scan directories (security check)
3. Move file to destination:
   - With targetFolder: `<targetFolder>/<filename>`
   - Without targetFolder: `scrubber/mobilebackup/upload/<filename>`
4. Handle filename collisions by appending timestamp
5. Delete `.meta` file after processing

### Processing .a/.d Files (DatabaseEntry Records)

Serialized `DatabaseEntry` Java objects created by FileScanner.

**For NEW files** → calls `processFileRecordData_exp()`:
- See "Indexing Flow" below

**For DELETE files** → calls `processDeleteFileRecordData_p2p()`:
- See "Deletion Flow" below

## Indexing Flow (processFileRecordData_exp)

**Source:** `ProcessorService.java` line ~1053
**Triggered by:** FileScanner discovering a new file

### On first occurrence of an MD5 (new file):

1. **Standard1 metadata** — 6 columns inserted:
   - `name`, `ext`, `size`, `date_added`, `date_modified`, `uuid_ori`

2. **Super2/hashes** — batch tag:
   - `batch@<batchid>` associated with MD5

3. **Standard1 batch index:**
   - `Standard1/batch@<batchid>` → `date_modified,<md5>,<path>`

4. **Standard1/.all global index:**
   - `Standard1/.all` → `date_modified,<md5>,<filename>`

5. **Standard1 extension indexes:**
   - `Standard1/.ext`, `.photo`, `.music`, `.video`, `.document` as appropriate

6. **Standard2 autocomplete tokens** (if `mAutoComplete=true`):
   - Tokenizes filename path using delimiters
   - For each token, generates all substrings ≥ `mMinSubstrLen` chars
   - Inserts: `Standard2/<substring>` → `<token>,<token>`
   - Also maps substring to full filename if it's a substring of the filename

### On subsequent occurrences (same MD5, different path):
- Skips Standard1 metadata (already exists)
- Still adds batch and index entries

## Deletion Flow

### Step 1: Physical File Deletion + .D_ Notification

Either:
- **FileScanner** detects a missing file → creates `.D_` serialized DatabaseEntry
- **Smoke tests / manual** → create `.D_` file in `incoming/` using `CreateDeleteNotification.java`

.D_ file naming: `<uuid>.<md5>.D_<timestamp>`

### Step 2: ProcessorService Picks Up .D_ (processDeleteFileRecordData_p2p)

**Source:** `ProcessorService.java` line ~978

1. **Super2/paths** — Mark path as DELETED:
   ```
   c8.deleteObject(md5, "paths", uuid, absolutepath)
   ```
   This calls `edit_SuperColumn()` which changes the column value to `DELETED`

2. **isFileDeleted() check** — If ALL paths for this MD5 are now DELETED:
   - **Standard1/.all** — Remove MD5 from global index (`removeFromAllIndex`)
   - **Standard1 keyword files** — Remove MD5 from all Standard1 index files (`removeFromKeywordIndexes`)

3. **BatchJobs** — Create deletion batch entry for audit trail

4. **Super2/hashes** — Add batch tag to deleted file

### What Gets Cleaned During Deletion

| Table | Cleaned? | Method | Notes |
|-------|----------|--------|-------|
| Super2/paths | Marked DELETED | `edit_SuperColumn()` | Value changed to "DELETED", not removed |
| Standard1/.all | Yes | `removeFromAllIndex()` | Only if ALL paths are DELETED |
| Standard1 keyword indexes | Yes | `removeFromKeywordIndexes()` | Scans all Standard1/ files |
| Standard1 per-file metadata | **NO** | — | name, ext, size, dates persist forever |
| Standard2 autocomplete tokens | **NO** | — | Search substrings persist forever |
| Super2/hashes | **NO** (adds to it) | — | Batch tag added for audit |

### Timing After Deletion

| What | When | Why |
|------|------|-----|
| `getfile.fn` stops serving | Immediately after ProcessorService processes .D_ | Super2/paths marked DELETED → `read_view_link()` can't resolve path |
| `getfile.fn` returns empty (len=0) | After a brief transient period | May return 32-byte residual (MD5 hash) briefly after deletion |
| `query.fn` stops returning MD5 | After Standard1 keyword cleanup | Happens during same ProcessorService cycle, but `isFileDeleted()` must return true first |
| `getfileinfo.fn` still returns data | **Indefinitely** | Standard1 per-file metadata is never cleaned |
| Standard2 search tokens | **Indefinitely** | Standard2 autocomplete tokens are never cleaned |

### Practical Implications for Testing

- **Reliable deletion check:** `getfile.fn` — will stop serving file content within seconds
- **Unreliable deletion check:** `getfileinfo.fn` — Standard1 metadata persists, always returns info
- **Unreliable deletion check:** `query.fn` with Standard2 tokens — tokens persist, may still find MD5
- **Reliable if patient:** `query.fn` with Standard1 keyword indexes — cleaned during deletion, but may take a full scanner cycle

## API Endpoint Details

### getfile.fn

**Source:** `WebServer.java` line ~6412, calls `WebFuncs.getfile_mobile()` → `LocalFuncs.read_view_link()`

1. Receives `sNamer=<md5>` parameter
2. Reads `Super2/paths/<md5>` flat file
3. For each non-DELETED path entry:
   - Extracts UUID and file path
   - Looks up node IP/port from `NodeInfo/<uuid>`
   - Checks if node is available (for remote) or file exists (for local)
4. If valid path found: serves file content with appropriate Content-Type
5. If no valid path: returns empty response or error

### getfileinfo.fn

**Source:** `WebServer.java` line ~6390, calls `WebFuncs.getFileInfo()`
**Data source:** `Standard1/<md5>` (6 columns: name, ext, size, date_added, date_modified, uuid_ori)

Returns JSON:
```json
{
  "nickname": "<md5>",
  "name": "<url-encoded filename>",
  "file_ext": "<extension>",
  "file_group": "<photo|music|movie|document|other>",
  "file_size": <bytes>,
  "file_date": "<date_modified>",
  "file_date_epoch": <millis>,
  "file_path_webapp": "/cass/getfile.fn?sNamer=<md5>&...",
  "file_remote_webapp": "/cass/openfile.fn?sNamer=<md5>&...",
  "file_folder_webapp": "/cass/openfolder.fn?sNamer=<md5>&..."
}
```

Returns `{"error": "File not found"}` only if Standard1 has no `name` column for this MD5.

### query.fn

**Source:** `WebServer.java` → `WebFuncs.echoh2mobileac()` → `LocalFuncs` search methods

1. Receives `foo=<search term>` parameter
2. Searches Standard2 for autocomplete token matches
3. Searches Standard1 keyword indexes for matching files
4. Applies `isFileDeleted()` filter as backup check during result building
5. Returns JSON array of matching files with MD5, name, dates, etc.

## FileScanner/ScannerService

**Source:** `scrubber-maven/.../services/ScannerService.java`

### Configuration
- **Scan directories:** `scrubber/config/scan1.txt` — URL-encoded paths, semicolon-separated
- **Blacklist:** `scrubber/config/blacklist.txt` — paths to exclude (substring match)
- **Timing:** `delay_thread` property (default 10000ms between cycles)
- **Cycle duration in PROD:** ~3 minutes (dominated by `backup-icloud/` scan)

### Blacklist Behavior
- `isBlacklisted()` and `isBlacklistedContains()` in `FileUtils.java`
- Both do case-insensitive substring matching: `path.toLowerCase().contains(blacklistEntry.toLowerCase())`
- **Special exemption:** Paths containing `hivebot/` are never blacklisted (added to support `~/Library/Application Support/hivebot/` data directory despite `"library/application support"` being in the blacklist)

### Scan Cycle
1. Load scan directories from `scan1.txt`
2. For each directory:
   - Recursively scan files using `FileUtils.ScanDirectory()`
   - Apply blacklist filters
   - Calculate MD5 hashes (on every Nth cycle, controlled by `mScansBeforeMD5Check`)
   - Store `FileDatabaseEntry` records in persistent HashMap
3. Detect deleted files: compare current scan against stored records
4. Create DatabaseEntry records for new/deleted files
5. Save to `incoming/` for ProcessorService to pick up

## MapDB Keyword Indexing

MapDB is used as a secondary index layer for keyword search, file copy tracking, and file attribute storage. It runs on the **rtserver** side, processing batch results from the ProcessorService.

### Architecture Overview

```
ProcessorService (scrubber)
  → Processes new files into Standard1/batch@<id> entries
  → Creates rtserver/batch_<id>.idx signal file when batch is done

RTServer (WebFuncs.CheckforIDX)
  → Discovers .idx files in rtserver/
  → Calls LocalFuncs.update_occurences_copies(batchId)
  → Reads Standard1/batch@<id> to get list of files
  → For each file: updates MapDB with keywords, copy counts, attributes
  → On error: renames .idx → .bad (5+ errors = shutdown)
```

### MapDB Databases

Four separate MapDB database files, all stored under `rtserver/`:

| Database | File Suffix | Type | Contents |
|----------|------------|------|----------|
| mm1 | `_mm1` | `TreeSet<Tuple2<String,String>>` | Autocomplete: substring → token |
| mm2 | `_mm2` | `TreeSet<Tuple2<String,String>>` | Search: substring → `md5,timestamp,filename` |
| cp | `_cp` | `TreeMap<String,String>` | Copy count: md5 → replication info |
| attr | `_attr` | `TreeMap<String,String>` | Attributes: md5 → `date,title,artist` or `date,height,width` |

**File location:** `rtserver/<dbname>_mm1`, `_mm2`, `_cp`, `_attr`
- Database name read from `rtserver/dbname.txt` (e.g., `testdb20260121120000`)
- Falls back to `rtserver/testdb` if no dbname.txt

**Configuration** (LocalFuncs.java):
```
bUseMapDBTx = false     // Direct mode (no ACID transactions) — default
FLUSH_DELAY = 10000     // 10s flush delay
COMMIT_TIMER = 60000    // Commit every 1 minute
COMMIT_PUTS = 10000     // Or every 10,000 inserts
NUM_FOLDERS = 3         // Index up to 3 folder levels from path
NUM_KEYWORDS = 10       // Max keywords per document
```

### IDX File Lifecycle

```
1. ProcessorService finishes batch → writes rtserver/batch_<id>.idx
2. WebFuncs.CheckforIDX() scans rtserver/ for .idx files
3. Extracts batch ID from filename: "batch_123.idx" → "123"
4. Calls update_occurences_copies("123")
5. On success (return > 0): adds to mapBatches (skip on next scan)
6. On failure (return ≤ 0): renames to batch_123.bad
7. After 5+ failures: System.exit(-1)
```

### update_occurences_copies() Flow

**Source:** `LocalFuncs.java` line ~1006

1. **Open MapDB** — creates TxMaker sessions if null
2. **Read batch file** — `Standard1/batch@<id>` contains CSV lines: `date,md5,filepath`
3. **For each file in batch:**
   - Get number of copies via `getNumberofCopies()` → store in `cp` TreeMap
   - Extract file attributes (music: title/artist, photo: height/width) → store in `attr` TreeMap
   - Call `update_index()` to tokenize and index keywords → store in `mm1` and `mm2` TreeSets
4. **On success (0 errors):** return 1
5. **On errors:** rollback all 4 databases, close, return -1

### Keyword Indexing (update_index)

**Source:** `LocalFuncs.java` line ~319

For each file, indexes keywords from multiple sources:

1. **Filename** — tokenized by delimiters (`!@#$%^&*()-=_+[]{}|;':,./<>?` and space)
2. **Path components** — up to 3 folder levels from the file path
3. **Music metadata** — title and artist from Standard1 attributes
4. **Document keywords** — extracted text keywords (top 10 by frequency)

### Substring Generation (index_token)

**Source:** `LocalFuncs.java` line ~557

For each token, generates **all substrings** of length 4–25 and inserts into MapDB:

```
Token: "hello"
Substrings: "hell", "hello", "ello"  (min length 4)

mm1 inserts: Fun.t2("hell", "hello"), Fun.t2("hello", "hello"), Fun.t2("ello", "hello")
mm2 inserts: Fun.t2("hell", "abc123,1708901234,hello.txt"), ...
```

- **mm1 (autocomplete):** `Tuple2(substring, original_token)` — maps partial text to full token
- **mm2 (search results):** `Tuple2(substring, "md5,timestamp,filename")` — maps partial text to file info
- Hex strings >8 chars and MD5-named files are skipped
- Thread-safe with synchronized blocks, retries up to 3 times on AssertionError

### Commit and Flush

**Source:** `LocalFuncs.check_commit()` line ~708

Commit triggers (whichever comes first):
- Timer: every 60 seconds (`COMMIT_TIMER`)
- Insert count: every 10,000 puts (`COMMIT_PUTS`)

Each commit:
1. Commits all 4 databases (mm1, mm2, cp, attr)
2. Retries up to 5 times on `IllegalAccessError`
3. Resets put counter and timer

### Error Recovery

| Error Type | Action |
|-----------|--------|
| `AssertionError` | Close/reopen MapDB, retry up to 3 times |
| `IllegalAccessError` | Retry with 500ms delay, reopen database |
| `OutOfMemoryError` | Increment error counter |
| `TxRollbackException` | Rollback all databases |
| `InternalError` | Increment error counter |

When `update_occurences_copies()` returns -1 (errors occurred):
- All 4 databases are rolled back and closed
- The `.idx` file is renamed to `.bad`
- After 5 `.bad` files, the rtserver shuts down

### MapDB in Deletion

MapDB keyword entries (mm1, mm2) are **NOT cleaned** during deletion. The deletion pipeline only affects:
- Flat file indexes (Standard1, Super2/paths)
- MapDB `cp` (copy counts) may become stale for deleted files

## Known Bugs (Fixed)

### Duplicate MapDB Files / Broken Keyword Search (Fixed 2026-02-21)

**Symptom:** MapDB database files (`_mm1`, `_mm2`, `_cp`, `_attr` and their `.p`/`.t` companions) appeared in BOTH `~/Library/Application Support/hivebot/scrubber/` and `~/Library/Application Support/hivebot/rtserver/`. Keyword search via `query.fn` was unreliable — some searches returned no results despite files being indexed.

**Root cause:** `loadIndexMapDB()` and `open_mapdb()` used different path construction for the same databases:
- `open_mapdb()` correctly used `appendage + "../rtserver/" + sFile` → resolves to `rtserver/`
- `loadIndexMapDB()` incorrectly used `appendageRW + sFile` (missing `"../rtserver/"`) → resolves to `scrubber/`

**Split-brain scenario that broke keyword search:**
1. At startup, `loadIndexMapDB()` opens `tx_mm1`/`tx_mm2`/`tx_cp`/`tx_attr` pointing to `scrubber/` files
2. First batch of keywords gets written to `scrubber/` files (wrong location)
3. After commit, `bNullMapDBTx=true` (default) causes tx handles to be nulled
4. On next use, `open_mapdb()` reopens tx handles pointing to `rtserver/` files (correct location)
5. Subsequent batches write to correct files, but keywords from the first batch are permanently lost in the wrong `scrubber/` files
6. `readDoc_mapdb()` reads from `tx_mm2` which after the first commit cycle points to `rtserver/` — so first-batch keywords are invisible to queries

**Impact:** Keywords indexed in the first batch after each server restart were written to wrong files and invisible to `query.fn` searches. This affected `readDoc_mapdb()` (line ~7265) which uses `tx_mm2.makeTx()` to search MapDB.

**Fix:** Added `"../rtserver/"` to the path in `loadIndexMapDB()` at LocalFuncs.java line 1961:
```java
String sRtPath = sAppend + appendageRW + "../rtserver/";
```

**Verification:** After fix, full smoke test suite (112 tests) passed in both DEV and PROD environments. Phase 7 index tests (12 tests) confirmed the complete upload → index → query → download → delete pipeline works correctly. Stale MapDB files in `scrubber/` can be safely deleted.

### Missing `appendage` Prefix on DB_PATH (Fixed 2026-02-21)

**Symptom:** `.bad` IDX files appearing in `rtserver/` directory; batch files not found during processing.

**Root cause:** 6 locations in `LocalFuncs.java` used `DB_PATH + "/Standard1/..."` without the `appendage` prefix. In PROD, `DB_PATH` is relative (`../scrubber/data/localdb/`) and doesn't resolve correctly without `appendage`.

**Fix:** Added `appendage +` prefix at lines 1033, 1547, 1549, 2360, 3623, 6091, 7866.

**Verification:** After fix, IDX files process successfully (no `.bad` files) in both DEV and PROD.

## Key Source Files

| File | Role |
|------|------|
| `rtserver-maven/.../netty/HttpUploadServerHandler.java` | Upload handler (port 8087) |
| `scrubber-maven/.../services/ProcessorService.java` | File processing, indexing, deletion |
| `scrubber-maven/.../services/ScannerService.java` | File discovery and scanning |
| `cass-server-maven/.../processor/FileUtils.java` | Blacklist, file scanning utilities |
| `cass-server-maven/.../utils/LocalFuncs.java` | Database read/write operations |
| `cass-server-maven/.../utils/WebFuncs.java` | API endpoint implementations |
| `rtserver-maven/.../javaapplication1/WebServer.java` | HTTP request routing |
| `processor/DatabaseEntry.java` | Serializable record for file events |

## End-to-End Verification

The complete indexing and deletion pipeline is verified by smoke test Phases 6 and 7 (`test-smoke/smoke-test-phase6.sh`, `test-smoke/smoke-test-phase7.sh`).

### Phase 6 — Upload/Download Functional (14 tests)
Tests the upload → ProcessorService → FileScanner → download round trip:
- Full upload and chunked upload delivery to `mobilebackup/upload/`
- Content integrity verification (uploaded bytes == destination bytes)
- `getfile.fn` download with content matching
- Chunked download reassembly via `filechunk_size`/`filechunk_offset`
- Three upload paths: Dropzone-style Netty, uiv5-style Netty, uiv5-style 8081→Netty

### Phase 7 — Index Tests (12 tests)
Tests the full file lifecycle including deletion:
- Upload → wait for indexing → verify in `getfile.fn`, `getfileinfo.fn`, `query.fn`
- Create `.D_` deletion notification → wait for processing → verify removal from `getfile.fn` and `query.fn`
- Uses `CreateDeleteNotification.java` helper compiled from uber JAR

### Verified Environments (2026-02-21)
- **DEV:** 112/112 tests passed (Phase 7: 12/12 passed)
- **PROD:** 112/112 tests passed (Phase 7: 12/12 passed)
- **Processor logs:** Zero errors in both environments
- **Error logs:** No errors (only "Error log started" initialization messages)

## Configuration Reference

| File | Purpose |
|------|---------|
| `scrubber/config/www-server.properties` | Root path, ports, dbmode |
| `scrubber/config/www-rtbackup.properties` | Scanner config, UUID path, delay_thread |
| `scrubber/config/scan1.txt` | Scan directories (URL-encoded, semicolon-separated) |
| `scrubber/config/blacklist.txt` | Directory exclusion patterns |
| `scrubber/config/users.txt` | User credentials |
| `scrubber/config/delimiters.txt` | Search tokenization delimiters |

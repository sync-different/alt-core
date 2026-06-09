/**
 * IntegrityChecker — Java port of the data-integrity checks that also exist as shell scripts in
 * test-data/. Used by the uiv5 Admin "Data Integrity" tab via the integritycheck.fn endpoint.
 *
 * The shell scripts (test-data/*.sh) remain the reference spec; this Java implementation must
 * reproduce their verdicts (validated by diffing outputs on the live index). See
 * internal/PROJECT_INTEGRITY_UI.md.
 *
 * Phase 1: checkPaths() — mirrors test-data/check_path_integrity.sh
 *   index -> filesystem: every Super2/paths/<md5> entry should point to a file that exists on disk.
 *   Verdicts: OK / ORPHAN / UNMOUNTED / DELETED / EMPTYMD5
 *
 * Read-only. Safe to run against a live index while scanning is in progress.
 */
package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IntegrityChecker {

    // Postmortem-grade summary line for a completed check, written to the PROD diag log
    // (<appendage>logs/<date>_rtserver.log via LocalFuncs.pw, or pe on error). ONE line per run —
    // no per-issue/per-folder spam (avoids the B18/B20 diag-log flood). Call from the job thread's
    // finally so it fires on success and exception alike. _elapsedMs = wall time of the check.
    public static void logSummary(Result r, long _elapsedMs) {
        if (r == null) return;
        StringBuilder b = new StringBuilder("[INTEGRITY] ");
        b.append(r.check).append(r.error != null ? " ERROR" : " done")
         .append(" in ").append(_elapsedMs).append("ms");
        if ("paths".equals(r.check)) {
            b.append(" — md5files=").append(r.md5Files).append(" entries=").append(r.pathEntries)
             .append(" ok=").append(r.ok).append(" orphan=").append(r.orphan)
             .append(" unmounted=").append(r.unmounted).append(" deleted=").append(r.deleted)
             .append(" emptymd5=").append(r.emptymd5);
        } else if ("coverage".equals(r.check)) {
            b.append(" — files=").append(r.filesOnDisk).append(" indexed=").append(r.indexed)
             .append(" missing=").append(r.missing).append(" skipped=").append(r.skipped)
             .append(" indexOnly=").append(r.indexOnly).append(" roots=").append(r.scanRoots.size());
        } else if ("downloadbtn".equals(r.check)) {
            b.append(" — files=").append(r.filesOnDisk).append(" ok=").append(r.dlOk)
             .append(" notindexed=").append(r.dlNotIndexed).append(" bug=").append(r.dlBug)
             .append(" ambiguous=").append(r.dlAmbiguous);
        } else if ("streaming".equals(r.check)) {
            b.append(" — folders=").append(r.strFolders)
             .append(" thumbMiss=").append(r.strThumbMiss).append(" hlsMiss=").append(r.strHlsMiss)
             .append(" trMiss=").append(r.strTrMiss).append(" empty=").append(r.strEmpty)
             .append(" incomplete=").append(r.strIncomplete).append(" whisperFail=").append(r.xfWhisper);
        }
        if (r.error != null) { b.append(" error='").append(r.error).append("'"); LocalFuncs.pe(b.toString()); }
        else LocalFuncs.pw(b.toString());
    }

    // One result row per path entry (mirrors the script's per-entry verdict + the CSV columns).
    public static class Row {
        public String md5;
        public String path;
        public String status;   // OK | ORPHAN | UNMOUNTED | DELETED
        public Row(String md5, String path, String status) {
            this.md5 = md5; this.path = path; this.status = status;
        }
    }

    // Full result of a check: counts + the rows + a list of distinct unmounted volume names.
    public static class Result {
        public String check = "paths";
        public int md5Files = 0;
        public int pathEntries = 0;
        public int ok = 0;
        public int orphan = 0;
        public int unmounted = 0;
        public int deleted = 0;
        public int emptymd5 = 0;
        public List<Row> rows = new ArrayList<Row>();
        public List<StreamRow> streamRows = new ArrayList<StreamRow>();  // streaming check (per-category rows)
        public List<String> unmountedVols = new ArrayList<String>();
        public List<String> emptyMd5s = new ArrayList<String>();
        public String error = null;
        // explicit completion flag — set true when the check method returns (success OR error).
        // Status polling MUST key off this, not (scanned >= totalToScan), because some items are
        // skipped without bumping scanned (e.g. non-directory entries in streaming/), which would
        // otherwise leave the job perpetually "running" and freeze the UI.
        public volatile boolean done = false;
        // progress (updated as the check runs, for the async status poll)
        public volatile int scanned = 0;     // items processed so far (md5 files for paths; disk files for coverage)
        public volatile int totalToScan = 0; // total items

        // --- coverage-check fields (check=coverage) ---
        public int filesOnDisk = 0;
        public int indexed = 0;
        public int missing = 0;
        public int skipped = 0;       // skipped-by-design (bad ext or hidden)
        public int indexOnly = 0;     // indexed-path entries not under a mounted scan root
        public int indexedPathSet = 0;// live entries in Super2/paths (the lookup set size)
        public List<String> scanRoots = new ArrayList<String>();   // mounted roots actually scanned
        public List<String> missingRoots = new ArrayList<String>();// configured roots not mounted/found

        // --- downloadbtn-check fields (check=downloadbtn) ---
        public int dlOk = 0;        // md5 resolves -> download button works
        public int dlNotIndexed = 0;// file not in index at all (transient / not scanned)
        public int dlBug = 0;       // indexed but md5 won't resolve -> no button (real defect)
        public int dlAmbiguous = 0; // 2+ md5s for the name, none matched path -> may serve wrong content

        // --- streaming-check fields (check=streaming) ---
        public int strFolders = 0;     // total streaming/<md5> folders
        public int strThumbOk = 0, strThumbMiss = 0;
        public int strHlsOk = 0, strHlsMiss = 0;
        public int strTrOk = 0, strTrMiss = 0;
        public int strEmpty = 0;       // folders with none of the 3 artifacts
        public int strIncomplete = 0;  // folders with some output but >=1 missing
        // thumbnail-failure reason buckets
        public int tfShort = 0, tfCorruptOpen = 0, tfCorruptEnc = 0, tfNoframe = 0, tfNolog = 0, tfUnknown = 0;
        // HLS-failure reason buckets
        public int hfCorruptOpen = 0, hfCorruptEnc = 0, hfNoframe = 0, hfNolog = 0, hfUnknown = 0;
        // transcription-failure reason buckets
        public int xfCorruptOpen = 0, xfNoAudio = 0, xfWhisper = 0, xfNolog = 0, xfUnknown = 0;

        // --- duplicates-check fields (check=duplicates) ---
        public int dupMd5Files = 0;     // total md5 path-files scanned
        public int dupMd5 = 0;          // md5s with 2+ distinct existing paths
        public int dupCopies = 0;       // total existing copies across all duplicate md5s
        public int dupUnverifiable = 0; // 1 on disk + path(s) on unmounted volume (can't confirm)
        public long dupRedundantBytes = 0; // reclaimable bytes = sum over dups of size*(copies-1)
        public List<DupRow> dupRows = new ArrayList<DupRow>();
    }

    // Duplicate-file row: one content MD5 found at 2+ live, on-disk locations.
    public static class DupRow {
        public String md5;
        public int copies;
        public long bytes;   // size of one copy (all copies of an md5 are identical content)
        public List<String> paths = new ArrayList<String>();
        public DupRow(String md5) { this.md5 = md5; }
    }

    // Streaming per-folder row (richer than the generic Row — carries per-category status + reason).
    public static class StreamRow {
        public String md5;
        public String path;       // resolved original video path
        public String status;     // OK | INCOMPLETE | EMPTY
        public String thumb;      // OK | MISSING
        public String hls;        // OK | MISSING
        public String transcript; // OK | MISSING
        public String thumbReason = "";
        public String hlsReason = "";
        public String trReason = "";
        public StreamRow(String md5, String path) { this.md5 = md5; this.path = path; }
    }

    /**
     * Path-integrity check. Walks Super2/paths/<md5>, parses each "<uuid>:<absPath>/,<value>" line,
     * and stats the path. progress (if non-null) receives live counts so the async runner can report
     * a progress bar. Pure read-only.
     */
    public static Result checkPaths(Result progress) {
        Result r = (progress != null) ? progress : new Result();
        try {
            // Match the backend's own path construction (LocalFuncs:7912).
            // DB_PATH is a static set by LocalFuncs.loadProps(); in the live server it's already
            // populated, but ensure it (loadProps is package-private — we're in utils too).
            if (LocalFuncs.DB_PATH == null || LocalFuncs.DB_PATH.length() == 0
                    || LocalFuncs.DB_PATH.startsWith("c:/temp")) {
                try { new LocalFuncs().loadProps(); } catch (Exception ignore) {}
            }
            Appendage app = new Appendage();
            String appendage = app.getAppendage();
            String pathsDir = appendage + LocalFuncs.DB_PATH + File.separator
                    + "Super2" + File.separator + "paths";

            File dir = new File(pathsDir);
            if (!dir.isDirectory()) {
                r.error = "Super2/paths not found at: " + pathsDir;
                return r;
            }
            File[] files = dir.listFiles();
            if (files == null) files = new File[0];
            r.totalToScan = files.length;

            for (File pf : files) {
                if (!pf.isFile()) continue;
                String md5 = pf.getName();
                r.md5Files++;
                int liveInFile = 0;

                BufferedReader br = null;
                try {
                    br = new BufferedReader(new FileReader(pf));
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.length() == 0) continue;
                        r.pathEntries++;

                        // Line: "<uuid>:<absolutePath>/,<value>". The stored path ALWAYS ends in '/',
                        // so split on the LAST "/," — NOT the last comma (value/path may contain commas,
                        // e.g. "...Together in 2007, at D5..mov"). Mirrors the script + read_view_link.
                        int colon = line.indexOf(':');
                        if (colon < 0) continue;
                        String rest = line.substring(colon + 1);     // "<absolutePath>/,<value>"
                        int lastSlashComma = rest.lastIndexOf("/,");
                        String path, value;
                        if (lastSlashComma >= 0) {
                            path = rest.substring(0, lastSlashComma); // "<absolutePath>"
                            value = rest.substring(lastSlashComma + 2); // "<value>"
                        } else {
                            // no "/," — fall back to last comma (defensive; shouldn't happen)
                            int lc = rest.lastIndexOf(',');
                            if (lc < 0) continue;
                            path = rest.substring(0, lc);
                            value = rest.substring(lc + 1);
                            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
                        }

                        if ("DELETED".equals(value)) {
                            r.deleted++;
                            r.rows.add(new Row(md5, path, "DELETED"));
                            continue;
                        }

                        liveInFile++;

                        if (new File(path).exists()) {
                            r.ok++;
                            // NOTE: OK rows are counted but NOT added to r.rows — on a large index
                            // that's thousands of rows of noise. Like the other 3 checks, paths reports
                            // ISSUES only (ORPHAN/UNMOUNTED/DELETED). The OK count shows in the summary chip.
                        } else {
                            String vol = unmountedVolume(path);
                            if (vol != null) {
                                r.unmounted++;
                                r.rows.add(new Row(md5, path, "UNMOUNTED"));
                                if (!r.unmountedVols.contains(vol)) r.unmountedVols.add(vol);
                            } else {
                                r.orphan++;
                                r.rows.add(new Row(md5, path, "ORPHAN"));
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try { if (br != null) br.close(); } catch (Exception ignore) {}
                }

                if (liveInFile == 0) {
                    r.emptymd5++;
                    r.emptyMd5s.add(md5);
                }
                r.scanned++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            r.error = "checkPaths exception: " + e.getMessage();
        }
        return r;
    }

    /**
     * If _path is under /Volumes/<NAME>/... and /Volumes/<NAME> does NOT exist (drive disconnected),
     * return <NAME>; else null. Mirrors volume_unmounted() in check_path_integrity.sh: macOS removes
     * the /Volumes/<NAME> mount point when a drive unmounts; the boot volume keeps its firmlink so
     * internal paths classify normally. (On Linux/Windows there is no /Volumes, so this returns null
     * and a missing file is a real ORPHAN — matching the script's behavior on those platforms.)
     */
    private static String unmountedVolume(String _path) {
        if (_path == null) return null;
        final String prefix = "/Volumes/";
        if (!_path.startsWith(prefix)) return null;
        String rest = _path.substring(prefix.length());
        int slash = rest.indexOf('/');
        String vol = (slash >= 0) ? rest.substring(0, slash) : rest;
        if (vol.length() == 0) return null;
        File mount = new File(prefix + vol);
        return mount.isDirectory() ? null : vol;   // dir present = mounted -> not unmounted
    }

    /**
     * Duplicate-files check — mirrors test-data/check_duplicates.sh.
     * Walks Super2/paths/<md5>; an MD5 is a DUPLICATE when it has 2+ DISTINCT, non-DELETED paths
     * that ACTUALLY EXIST on disk (identical path strings de-duped; unmounted-volume paths can't be
     * verified so they don't count — tracked as dupUnverifiable instead). r.dupRows holds one row per
     * duplicate md5 with its existing copy paths. Pure read-only.
     */
    public static Result checkDuplicates(Result progress) {
        Result r = (progress != null) ? progress : new Result();
        r.check = "duplicates";
        BufferedReader br = null;
        try {
            if (LocalFuncs.DB_PATH == null || LocalFuncs.DB_PATH.length() == 0
                    || LocalFuncs.DB_PATH.startsWith("c:/temp")) {
                try { new LocalFuncs().loadProps(); } catch (Exception ignore) {}
            }
            Appendage app = new Appendage();
            String appendage = app.getAppendage();
            String pathsDir = appendage + LocalFuncs.DB_PATH + File.separator
                    + "Super2" + File.separator + "paths";
            File dir = new File(pathsDir);
            if (!dir.isDirectory()) { r.error = "Super2/paths not found at: " + pathsDir; return r; }

            File[] files = dir.listFiles();
            if (files == null) files = new File[0];
            r.totalToScan = files.length;

            for (File f : files) {
                if (!f.isFile()) { r.scanned++; continue; }
                r.dupMd5Files++;
                String md5 = f.getName();

                List<String> existing = new ArrayList<String>();
                java.util.HashSet<String> seenPaths = new java.util.HashSet<String>();
                boolean hadUnmounted = false;
                try {
                    br = new BufferedReader(new FileReader(f));
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.length() == 0) continue;
                        if (line.contains(",DELETED")) continue;        // tombstoned
                        int colon = line.indexOf(':');
                        if (colon < 0) continue;                         // malformed
                        String rest = line.substring(colon + 1);         // "<absPath>/,<value>"
                        int lsc = rest.lastIndexOf("/,");
                        String p = (lsc >= 0) ? rest.substring(0, lsc) : rest;
                        if (p.length() == 0) continue;
                        if (!seenPaths.add(p)) continue;                 // de-dupe identical path strings
                        if (new File(p).exists()) {
                            existing.add(p);
                        } else if (unmountedVolume(p) != null) {
                            hadUnmounted = true;                         // can't verify (disconnected drive)
                        }
                    }
                } finally { try { if (br != null) br.close(); } catch (Exception ignore) {} br = null; }

                if (existing.size() >= 2) {
                    r.dupMd5++;
                    r.dupCopies += existing.size();
                    DupRow row = new DupRow(md5);
                    row.copies = existing.size();
                    row.paths = existing;
                    // all copies are identical content -> same size; take the first existing copy's size
                    long sz = 0;
                    try { sz = new File(existing.get(0)).length(); } catch (Exception ignore) {}
                    row.bytes = sz;
                    r.dupRedundantBytes += sz * (long) (existing.size() - 1);  // reclaimable = extras
                    r.dupRows.add(row);
                } else if (existing.size() == 1 && hadUnmounted) {
                    r.dupUnverifiable++;
                }
                r.scanned++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            r.error = "checkDuplicates exception: " + e.getMessage();
        } finally { try { if (br != null) br.close(); } catch (Exception ignore) {} }
        return r;
    }

    /**
     * Index-coverage check — mirrors test-data/check_index_coverage.sh.
     * filesystem -> index: every on-disk file under the scan roots that the scanner is SUPPOSED to
     * index should have a Super2/paths entry. Verdicts:
     *   INDEXED  - file's exact path appears in some Super2/paths/<md5> entry
     *   MISSING  - passes scanner admission rules (ext in FileExtensions.txt, not hidden) but has no
     *              path entry -> scanner missed it / index drift  (the actionable rows)
     *   SKIPPED  - fails a scanner rule (bad extension or hidden) -> correctly not indexed
     * Plus indexOnly = live path entries not under any mounted scan root (disconnected drive / removed
     * root; not a gap). Skip-rule fidelity matches the script: extension allowlist + hidden only (NO
     * blacklist). r.rows holds the MISSING rows (the gaps); SKIPPED/INDEXED are counted, not listed.
     */
    public static Result checkCoverage(Result progress) {
        Result r = (progress != null) ? progress : new Result();
        r.check = "coverage";
        try {
            if (LocalFuncs.DB_PATH == null || LocalFuncs.DB_PATH.length() == 0
                    || LocalFuncs.DB_PATH.startsWith("c:/temp")) {
                try { new LocalFuncs().loadProps(); } catch (Exception ignore) {}
            }
            Appendage app = new Appendage();
            String appendage = app.getAppendage();
            String configDir = appendage + "../scrubber/config";

            // 1) allowed extensions from FileExtensions.txt (key = first comma field, with dot, lowercased)
            Set<String> allowedExts = new HashSet<String>();
            File extFile = new File(configDir + File.separator + "FileExtensions.txt");
            if (!extFile.isFile()) { r.error = "FileExtensions.txt not found at: " + extFile.getPath(); return r; }
            BufferedReader ebr = null;
            try {
                ebr = new BufferedReader(new FileReader(extFile));
                String l;
                while ((l = ebr.readLine()) != null) {
                    if (l.length() == 0) continue;
                    int c = l.indexOf(',');
                    String key = (c >= 0) ? l.substring(0, c) : l;   // ".mp4"
                    if (key.startsWith(".")) key = key.substring(1); // "mp4"
                    key = key.toLowerCase();
                    if (key.length() > 0) allowedExts.add(key);
                }
            } finally { try { if (ebr != null) ebr.close(); } catch (Exception ignore) {} }

            // 2) resolve scan roots from scan1.txt (scandir= line; ';'-separated, URL-encoded each)
            List<String> roots = resolveScanRoots(configDir);
            if (roots.isEmpty()) { r.error = "no scan dir set (scan1.txt has no scandir=)"; return r; }
            for (String d : roots) {
                if (new File(d).isDirectory()) r.scanRoots.add(d);
                else r.missingRoots.add(d);
            }
            if (r.scanRoots.isEmpty()) { r.error = "none of the configured scan roots exist on this machine"; return r; }

            // 3) build the live indexed-path lookup set from Super2/paths (one pass)
            Set<String> indexedPaths = new HashSet<String>();
            String pathsDir = appendage + LocalFuncs.DB_PATH + File.separator
                    + "Super2" + File.separator + "paths";
            File pd = new File(pathsDir);
            File[] pfiles = pd.isDirectory() ? pd.listFiles() : null;
            if (pfiles != null) {
                for (File pf : pfiles) {
                    if (!pf.isFile()) continue;
                    BufferedReader br = null;
                    try {
                        br = new BufferedReader(new FileReader(pf));
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.length() == 0) continue;
                            // "<uuid>:<absPath>/,<value>" ; skip DELETED ; split on last "/,"
                            int lastSlashComma = line.lastIndexOf("/,");
                            if (lastSlashComma < 0) continue;
                            String value = line.substring(lastSlashComma + 2);
                            if ("DELETED".equals(value)) continue;
                            int colon = line.indexOf(':');
                            if (colon < 0 || colon > lastSlashComma) continue;
                            String p = line.substring(colon + 1, lastSlashComma);
                            indexedPaths.add(p);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally { try { if (br != null) br.close(); } catch (Exception ignore) {} }
                }
            }
            r.indexedPathSet = indexedPaths.size();

            // 4) walk each scan root; classify each file
            for (String root : r.scanRoots) {
                walkAndClassify(new File(root), allowedExts, indexedPaths, r);
            }
            r.indexOnly = r.indexedPathSet - r.indexed;
            if (r.indexOnly < 0) r.indexOnly = 0;
            // mark done for the async poll (coverage has no md5-file total; use filesOnDisk)
            if (r.totalToScan == 0) r.totalToScan = r.filesOnDisk;
            r.scanned = r.filesOnDisk;
        } catch (Exception e) {
            e.printStackTrace();
            r.error = "checkCoverage exception: " + e.getMessage();
        }
        return r;
    }

    // Recursive file walk (depth-first), classifying each regular file. Skips nothing structurally —
    // hidden/ext filtering is the SKIPPED-BY-DESIGN classification, matching the script's `find`.
    private static void walkAndClassify(File dir, Set<String> allowedExts,
                                        Set<String> indexedPaths, Result r) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                walkAndClassify(f, allowedExts, indexedPaths, r);
            } else if (f.isFile()) {
                r.filesOnDisk++;
                r.scanned = r.filesOnDisk;
                String name = f.getName();
                // scanner rule 2: hidden / ._ files never indexed
                if (name.startsWith(".")) { r.skipped++; continue; }
                // scanner rule 1: extension must be in FileExtensions.txt
                int dot = name.lastIndexOf('.');
                String ext = (dot >= 0 && dot < name.length() - 1)
                        ? name.substring(dot + 1).toLowerCase() : "";
                if (ext.length() == 0 || !allowedExts.contains(ext)) { r.skipped++; continue; }
                // indexed? exact path present in Super2/paths set
                String path = f.getPath();
                if (indexedPaths.contains(path)) {
                    r.indexed++;
                } else {
                    r.missing++;
                    r.rows.add(new Row("", path, "MISSING"));  // md5 unknown (it's not indexed)
                }
            }
        }
    }

    /**
     * Download-button check — mirrors test-data/check_download_button.sh (which mirrors
     * WebServer.getFileMD5). For each on-disk file under the scan roots, can the Folder View resolve
     * an md5 (and the right one)? Verdicts:
     *   OK          - md5 resolves (1 candidate, or path-disambiguated among several)
     *   NOT-INDEXED - file not in the index at all (no Standard1/<name>, not in .all) -> transient
     *   BUG         - file IS in the index but md5 won't resolve -> no download button (real defect)
     *   AMBIGUOUS   - 2+ md5s for the name, none matched the file's path -> button may serve wrong bytes
     * r.rows holds the non-OK rows (BUG/AMBIGUOUS/NOT-INDEXED); OK is counted only.
     */
    public static Result checkDownloadBtn(Result progress) {
        Result r = (progress != null) ? progress : new Result();
        r.check = "downloadbtn";
        try {
            if (LocalFuncs.DB_PATH == null || LocalFuncs.DB_PATH.length() == 0
                    || LocalFuncs.DB_PATH.startsWith("c:/temp")) {
                try { new LocalFuncs().loadProps(); } catch (Exception ignore) {}
            }
            Appendage app = new Appendage();
            String appendage = app.getAppendage();
            String configDir = appendage + "../scrubber/config";
            String s1Dir = appendage + LocalFuncs.DB_PATH + File.separator + "Standard1";
            String pathsBase = appendage + LocalFuncs.DB_PATH + File.separator + "Super2" + File.separator + "paths";

            List<String> roots = resolveScanRoots(configDir);
            if (roots.isEmpty()) { r.error = "no scan dir set (scan1.txt has no scandir=)"; return r; }
            for (String d : roots) {
                if (new File(d).isDirectory()) r.scanRoots.add(d);
                else r.missingRoots.add(d);
            }
            if (r.scanRoots.isEmpty()) { r.error = "none of the configured scan roots exist on this machine"; return r; }

            File allAgg = new File(s1Dir + File.separator + ".all");
            for (String root : r.scanRoots) {
                walkDownloadBtn(new File(root), s1Dir, pathsBase, allAgg, r);
            }
            if (r.totalToScan == 0) r.totalToScan = r.filesOnDisk;
            r.scanned = r.filesOnDisk;
        } catch (Exception e) {
            e.printStackTrace();
            r.error = "checkDownloadBtn exception: " + e.getMessage();
        }
        return r;
    }

    private static void walkDownloadBtn(File dir, String s1Dir, String pathsBase, File allAgg, Result r) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                walkDownloadBtn(f, s1Dir, pathsBase, allAgg, r);
            } else if (f.isFile()) {
                String base = f.getName();
                if (base.startsWith(".")) continue;   // hidden / ._ sidecars: not listed (match script)
                r.filesOnDisk++;
                r.scanned = r.filesOnDisk;

                int dot = base.lastIndexOf('.');
                String ext = (dot >= 0 && dot < base.length() - 1) ? base.substring(dot + 1).toLowerCase() : "";
                File byName = new File(s1Dir + File.separator + base);
                File byExt = new File(s1Dir + File.separator + "." + ext);

                // collect candidate md5s: per-filename key first, else the .<ext> aggregate
                List<String> cands = byName.isFile()
                        ? collectMd5sFromIndex(byName, base)
                        : collectMd5sFromIndex(byExt, base);

                if (cands.isEmpty()) {
                    if (nameKnownToIndex(base, s1Dir, allAgg)) {
                        r.dlBug++;
                        r.rows.add(new Row("", f.getPath(), "BUG"));
                    } else {
                        r.dlNotIndexed++;
                        r.rows.add(new Row("", f.getPath(), "NOT-INDEXED"));
                    }
                } else if (cands.size() == 1) {
                    r.dlOk++;
                } else {
                    // path-disambiguate among the candidates
                    String canon;
                    try { canon = f.getCanonicalPath(); } catch (Exception e) { canon = f.getPath(); }
                    String matched = null;
                    for (String m : cands) {
                        if (pathMatchesMd5(pathsBase, m, canon)) { matched = m; break; }
                    }
                    if (matched != null) {
                        r.dlOk++;
                    } else {
                        r.dlAmbiguous++;
                        r.rows.add(new Row("", f.getPath(), "AMBIGUOUS"));
                    }
                }
            }
        }
    }

    // collect distinct md5s from a Standard1 index file (lines "date,md5,name") whose name == wantName.
    // Split on the FIRST two commas (name may itself contain commas). Mirrors collect_md5s in the script.
    private static List<String> collectMd5sFromIndex(File idxFile, String wantName) {
        List<String> out = new ArrayList<String>();
        if (!idxFile.isFile()) return out;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(idxFile));
            String line;
            while ((line = br.readLine()) != null) {
                int c1 = line.indexOf(',');
                if (c1 < 0) continue;
                int c2 = line.indexOf(',', c1 + 1);
                if (c2 < 0) continue;
                String md5 = line.substring(c1 + 1, c2);
                String name = line.substring(c2 + 1);
                if (name.equals(wantName) && !out.contains(md5)) out.add(md5);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally { try { if (br != null) br.close(); } catch (Exception ignore) {} }
        return out;
    }

    // Is this filename present anywhere in the index? per-filename key exists, OR name appears in .all.
    // Mirrors name_known_to_index in the script.
    private static boolean nameKnownToIndex(String base, String s1Dir, File allAgg) {
        if (new File(s1Dir + File.separator + base).isFile()) return true;
        if (!allAgg.isFile()) return false;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(allAgg));
            String line;
            String needle = "," + base;   // ",name" appears in "date,md5,name"
            while ((line = br.readLine()) != null) {
                if (line.endsWith(needle) || line.contains(needle)) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally { try { if (br != null) br.close(); } catch (Exception ignore) {} }
        return false;
    }

    // does Super2/paths/<md5> have a (non-DELETED) entry whose absolute path == wantPath?
    // Mirrors path_matches_md5 in the script: strip uuid prefix + trailing "/,name", compare.
    private static boolean pathMatchesMd5(String pathsBase, String md5, String wantPath) {
        File pf = new File(pathsBase + File.separator + md5);
        if (!pf.isFile()) return false;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(pf));
            String line;
            while ((line = br.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String rest = line.substring(colon + 1);          // "<absolutePath>/,name"
                if (rest.startsWith("DELETED") || rest.contains(",DELETED")) continue;
                int lastSlashComma = rest.lastIndexOf("/,");
                String stored;
                if (lastSlashComma >= 0) {
                    stored = rest.substring(0, lastSlashComma);   // "<absolutePath>"
                } else {
                    int lc = rest.lastIndexOf(',');
                    stored = (lc >= 0) ? rest.substring(0, lc) : rest;
                    if (stored.endsWith("/")) stored = stored.substring(0, stored.length() - 1);
                }
                if (stored.equals(wantPath)) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally { try { if (br != null) br.close(); } catch (Exception ignore) {} }
        return false;
    }

    /**
     * Streaming-artifacts check — mirrors test-data/check_streaming_artifacts.sh.
     * For each rtserver/streaming/<md5>/ folder, verifies thumbnail / HLS / transcription, with a
     * per-category OK/MISSING verdict and a log.txt-parsed failure reason. EMPTY = none of the 3;
     * INCOMPLETE = some output but >=1 missing. streamRows holds non-OK folders.
     */
    public static Result checkStreaming(Result progress) {
        Result r = (progress != null) ? progress : new Result();
        r.check = "streaming";
        try {
            if (LocalFuncs.DB_PATH == null || LocalFuncs.DB_PATH.length() == 0
                    || LocalFuncs.DB_PATH.startsWith("c:/temp")) {
                try { new LocalFuncs().loadProps(); } catch (Exception ignore) {}
            }
            Appendage app = new Appendage();
            String appendage = app.getAppendage();
            String streamDir = appendage + "../rtserver/streaming";   // streaming lives under rtserver/
            String pathsBase = appendage + LocalFuncs.DB_PATH + File.separator + "Super2" + File.separator + "paths";

            File sd = new File(streamDir);
            if (!sd.isDirectory()) { r.error = "streaming dir not found at: " + streamDir; return r; }
            File[] folders = sd.listFiles();
            if (folders == null) folders = new File[0];
            // count only directories for the progress total (non-dir entries are skipped below)
            int dirCount = 0;
            for (File d : folders) if (d.isDirectory()) dirCount++;
            r.totalToScan = dirCount;

            for (File d : folders) {
                if (!d.isDirectory()) continue;
                r.strFolders++;
                String md5 = d.getName();

                // thumbnail.jpg >0
                boolean tThumb = isNonEmpty(new File(d, "thumbnail.jpg"));
                // HLS = OUTPUT.m3u8 present AND >=1 OUTPUT-*.ts
                boolean tHls = new File(d, "OUTPUT.m3u8").isFile() && hasTs(d);
                // transcription = audio.json >0
                boolean tTr = isNonEmpty(new File(d, "audio.json"));

                String thumbReason = "", hlsReason = "", trReason = "";
                if (!tThumb) { thumbReason = classifyThumbFail(d); r.strThumbMiss++; bumpThumb(r, thumbReason); } else r.strThumbOk++;
                if (!tHls)   { hlsReason   = classifyHlsFail(d);   r.strHlsMiss++;   bumpHls(r, hlsReason); }   else r.strHlsOk++;
                if (!tTr)    { trReason     = classifyTranscriptFail(d); r.strTrMiss++; bumpTr(r, trReason); }   else r.strTrOk++;

                String status;
                if (!tThumb && !tHls && !tTr) status = "EMPTY";
                else if (!tThumb || !tHls || !tTr) status = "INCOMPLETE";
                else status = "OK";

                if (status.equals("EMPTY")) r.strEmpty++;
                else if (status.equals("INCOMPLETE")) r.strIncomplete++;

                if (!status.equals("OK")) {
                    String path = resolveStreamPath(md5, d, pathsBase);
                    StreamRow row = new StreamRow(md5, path);
                    row.status = status;
                    row.thumb = tThumb ? "OK" : "MISSING";
                    row.hls = tHls ? "OK" : "MISSING";
                    row.transcript = tTr ? "OK" : "MISSING";
                    row.thumbReason = thumbReason; row.hlsReason = hlsReason; row.trReason = trReason;
                    r.streamRows.add(row);
                }
                r.scanned++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            r.error = "checkStreaming exception: " + e.getMessage();
        }
        return r;
    }

    private static boolean isNonEmpty(File f) { return f.isFile() && f.length() > 0; }
    private static boolean hasTs(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) return false;
        for (File k : kids) {
            String n = k.getName();
            if (k.isFile() && n.startsWith("OUTPUT-") && n.endsWith(".ts")) return true;
        }
        return false;
    }

    // --- failure classifiers (mirror the script's classify_* via log.txt grep) ---
    private static boolean logContains(File log, String needle) {
        if (!log.isFile()) return false;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(log));
            String l;
            while ((l = br.readLine()) != null) if (l.contains(needle)) return true;
        } catch (Exception e) { /* ignore */ }
        finally { try { if (br != null) br.close(); } catch (Exception ignore) {} }
        return false;
    }
    // first Duration: hh:mm:ss.xx — return true if < 1 second (00:00:00.*)
    private static boolean durationUnderOneSecond(File log) {
        if (!log.isFile()) return false;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(log));
            String l;
            while ((l = br.readLine()) != null) {
                int i = l.indexOf("Duration:");
                if (i >= 0) {
                    String after = l.substring(i + 9).trim();   // "00:00:00.93, start:..."
                    return after.startsWith("00:00:00.");
                }
            }
        } catch (Exception e) { /* ignore */ }
        finally { try { if (br != null) br.close(); } catch (Exception ignore) {} }
        return false;
    }

    private static String classifyThumbFail(File d) {
        File log = new File(d, "log.txt");
        if (!log.isFile()) return "NOLOG";
        if (logContains(log, "Invalid data found when processing input")) return "CORRUPT-OPEN";
        if (durationUnderOneSecond(log)) return "SHORT";
        if (logContains(log, "Could not open encoder before EOF") || logContains(log, "Invalid argument")) return "CORRUPT-ENC";
        if (logContains(log, "Nothing was written")) return "NOFRAME";
        return "UNKNOWN";
    }
    private static String classifyHlsFail(File d) {
        File log = new File(d, "log.txt");
        if (!log.isFile()) return "NOLOG";
        if (logContains(log, "Invalid data found when processing input")) return "CORRUPT-OPEN";
        if (logContains(log, "Could not open encoder") || logContains(log, "Error while opening encoder") || logContains(log, "Invalid argument")) return "CORRUPT-ENC";
        if (logContains(log, "Nothing was written")) return "NOFRAME";
        return "UNKNOWN";
    }
    private static String classifyTranscriptFail(File d) {
        File log = new File(d, "log.txt");
        if (!log.isFile()) return "NOLOG";
        if (logContains(log, "Invalid data found when processing input")) return "CORRUPT-OPEN";
        if (isNonEmpty(new File(d, "audio.aac"))) return "WHISPER-FAIL";   // had audio but no transcript
        return "NO-AUDIO";
    }

    private static void bumpThumb(Result r, String reason) {
        if (reason.equals("SHORT")) r.tfShort++;
        else if (reason.equals("CORRUPT-OPEN")) r.tfCorruptOpen++;
        else if (reason.equals("CORRUPT-ENC")) r.tfCorruptEnc++;
        else if (reason.equals("NOFRAME")) r.tfNoframe++;
        else if (reason.equals("NOLOG")) r.tfNolog++;
        else r.tfUnknown++;
    }
    private static void bumpHls(Result r, String reason) {
        if (reason.equals("CORRUPT-OPEN")) r.hfCorruptOpen++;
        else if (reason.equals("CORRUPT-ENC")) r.hfCorruptEnc++;
        else if (reason.equals("NOFRAME")) r.hfNoframe++;
        else if (reason.equals("NOLOG")) r.hfNolog++;
        else r.hfUnknown++;
    }
    private static void bumpTr(Result r, String reason) {
        if (reason.equals("CORRUPT-OPEN")) r.xfCorruptOpen++;
        else if (reason.equals("NO-AUDIO")) r.xfNoAudio++;
        else if (reason.equals("WHISPER-FAIL")) r.xfWhisper++;
        else if (reason.equals("NOLOG")) r.xfNolog++;
        else r.xfUnknown++;
    }

    // Resolve a video's original path: Super2/paths/<md5> first (non-DELETED, split last "/,"),
    // else the "-i '<src>'" from the folder's ffmpegscript.sh. Mirrors resolve_path in the script.
    private static String resolveStreamPath(String md5, File folder, String pathsBase) {
        File pf = new File(pathsBase + File.separator + md5);
        if (pf.isFile()) {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(pf));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains(",DELETED")) continue;
                    int colon = line.indexOf(':');
                    if (colon < 0) continue;
                    String rest = line.substring(colon + 1);
                    int lsc = rest.lastIndexOf("/,");
                    String p = (lsc >= 0) ? rest.substring(0, lsc) : rest;
                    if (p.length() > 0) return p;
                }
            } catch (Exception e) { /* ignore */ }
            finally { try { if (br != null) br.close(); } catch (Exception ignore) {} }
        }
        // fallback: ffmpegscript.sh line 1 "-i '<src>'"
        File script = new File(folder, "ffmpegscript.sh");
        if (script.isFile()) {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(script));
                String first = br.readLine();
                if (first != null) {
                    int i = first.indexOf("-i '");
                    if (i >= 0) {
                        int start = i + 4;
                        int end = first.indexOf('\'', start);
                        if (end > start) return first.substring(start, end);
                    }
                }
            } catch (Exception e) { /* ignore */ }
            finally { try { if (br != null) br.close(); } catch (Exception ignore) {} }
        }
        return "";
    }

    /**
     * Resolve scan roots: read scandir= from <configDir>/scan1.txt, ';'-split, URL-decode each.
     * Mirrors check_index_coverage.sh (and the server's own scandir parsing).
     */
    private static List<String> resolveScanRoots(String configDir) {
        List<String> out = new ArrayList<String>();
        File scan1 = new File(configDir + File.separator + "scan1.txt");
        if (!scan1.isFile()) return out;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(scan1));
            String line, raw = null;
            while ((line = br.readLine()) != null) {
                String t = line.trim();
                if (t.toLowerCase().startsWith("scandir=")) { raw = t.substring("scandir=".length()); break; }
            }
            if (raw != null) {
                for (String part : raw.split(";")) {
                    String p = part.trim();
                    if (p.length() == 0) continue;
                    String dec;
                    try { dec = URLDecoder.decode(p, "UTF-8"); } catch (Exception e) { dec = p; }
                    out.add(dec);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally { try { if (br != null) br.close(); } catch (Exception ignore) {} }
        return out;
    }
}

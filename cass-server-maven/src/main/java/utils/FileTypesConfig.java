/**
 * FileTypesConfig — the scanner's file-type configuration, for the uiv5 Admin "File types" tab.
 *
 * Three files under <appendage>../scrubber/config/ (see internal/PROJECT_TAB_ADMIN_FILETYPES.md):
 *   FileExtensions_All.txt     CATALOG   — "@,<label>,<groupId>" group headers + ".<ext>,<description>,<icon>" lines
 *   FileExtensions.txt         SELECTION — catalog lines copied verbatim; the ONLY list the scanner admits
 *                                          (FileUtils.loadFileExtensions → checkFileType, reloaded every pass)
 *   FileExtensions_Custom.txt  CUSTOM    — one key per line: types an admin added (only these can be removed)
 *
 * Parsing is deliberately tolerant: an install may carry a hand-edited or older catalog. Unparseable
 * lines are skipped and reported in `warnings`, never thrown.
 */
package utils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

public class FileTypesConfig {

    public static final String CATALOG_FILE   = "FileExtensions_All.txt";
    public static final String SELECTION_FILE = "FileExtensions.txt";
    public static final String CUSTOM_FILE    = "FileExtensions_Custom.txt";

    /** A catalog line `.ext,description,icon`. `line` is kept verbatim so the selection can copy it. */
    public static class FileType {
        public final String ext;          // ".doc" — lowercased, with dot
        public final String description;
        public final String icon;         // legacy glyph entity; uiv5 ignores it
        public final String groupId;
        public final String line;         // the exact catalog line
        FileType(String ext, String description, String icon, String groupId, String line) {
            this.ext = ext; this.description = description; this.icon = icon; this.groupId = groupId; this.line = line;
        }
    }

    public static class Group {
        public final String id;
        public final String label;
        public final String line;         // the exact "@,..." header line
        public final List<FileType> types = new ArrayList<FileType>();
        Group(String id, String label, String line) { this.id = id; this.label = label; this.line = line; }
    }

    // ---- loaded state ----
    public final List<Group> groups = new ArrayList<Group>();
    public final Map<String, FileType> catalog = new LinkedHashMap<String, FileType>();  // ext -> type, catalog order
    public final Set<String> selected = new LinkedHashSet<String>();                     // keys in FileExtensions.txt
    public final Set<String> custom = new LinkedHashSet<String>();                       // keys in FileExtensions_Custom.txt
    public final List<String> warnings = new ArrayList<String>();
    public String version = "";
    public final File configDir;

    public FileTypesConfig(File configDir) {
        this.configDir = configDir;
    }

    /** <appendage>../scrubber/config — the same directory the scanner's ./config resolves to. */
    public static File defaultConfigDir() {
        String appendage = new Appendage().getAppendage();
        return new File(appendage + "../scrubber/config");
    }

    public static FileTypesConfig load() throws Exception {
        FileTypesConfig c = new FileTypesConfig(defaultConfigDir());
        c.reload();
        return c;
    }

    public void reload() throws Exception {
        groups.clear(); catalog.clear(); selected.clear(); custom.clear(); warnings.clear();

        File catalogFile = new File(configDir, CATALOG_FILE);
        if (!catalogFile.isFile()) throw new Exception(CATALOG_FILE + " not found");

        Group current = null;
        int n = 0;
        for (String raw : readLines(catalogFile)) {
            n++;
            String line = stripBom(raw).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("@,")) {
                String[] f = line.split(",", 3);
                String label = f.length > 1 ? f[1].trim() : "";
                String id = f.length > 2 ? f[2].trim().toLowerCase() : "";
                if (id.isEmpty()) { warnings.add(CATALOG_FILE + ":" + n + " group header without id"); id = "group" + n; }
                current = new Group(id, label.isEmpty() ? id : label, line);
                groups.add(current);
                continue;
            }
            String[] f = line.split(",", 3);
            String ext = f[0].trim().toLowerCase();
            if (!ext.startsWith(".") || ext.length() < 2) { warnings.add(CATALOG_FILE + ":" + n + " skipped (not an extension line)"); continue; }
            if (catalog.containsKey(ext)) { warnings.add(CATALOG_FILE + ":" + n + " duplicate " + ext + " ignored"); continue; }
            if (current == null) {   // extension before any header: park it in an implicit group
                current = new Group("others", "Other", null);
                groups.add(current);
            }
            FileType t = new FileType(ext, f.length > 1 ? f[1].trim() : "", f.length > 2 ? f[2].trim() : "", current.id, line);
            current.types.add(t);
            catalog.put(ext, t);
        }

        File selectionFile = new File(configDir, SELECTION_FILE);
        if (selectionFile.isFile()) {
            for (String raw : readLines(selectionFile)) {
                String line = stripBom(raw).trim();
                if (line.isEmpty() || line.startsWith("@") || line.startsWith("#")) continue;
                int c = line.indexOf(',');
                String ext = (c >= 0 ? line.substring(0, c) : line).trim().toLowerCase();
                if (ext.startsWith(".") && ext.length() > 1) selected.add(ext);
            }
        } else {
            warnings.add(SELECTION_FILE + " not found (nothing is scanned)");
        }

        File customFile = new File(configDir, CUSTOM_FILE);
        if (customFile.isFile()) {
            for (String raw : readLines(customFile)) {
                String ext = stripBom(raw).trim().toLowerCase();
                if (ext.startsWith(".") && ext.length() > 1) custom.add(ext);
            }
        }

        version = computeVersion();
    }

    /** Selected keys that are not in the catalog. The scanner still admits them; reported, never dropped. */
    public List<String> orphans() {
        List<String> o = new ArrayList<String>();
        for (String e : selected) if (!catalog.containsKey(e)) o.add(e);
        return o;
    }

    /** First 16 hex of sha256 over all three files (a missing file hashes as empty). */
    public String computeVersion() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String name : new String[] { CATALOG_FILE, SELECTION_FILE, CUSTOM_FILE }) {
            File f = new File(configDir, name);
            md.update(name.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            if (f.isFile()) md.update(Files.readAllBytes(f.toPath()));
            md.update((byte) 0);
        }
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
        return sb.toString();
    }

    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        root.put("success", true);
        root.put("version", version);
        JSONArray ga = new JSONArray();
        for (Group g : groups) {
            JSONObject go = new JSONObject();
            go.put("id", g.id);
            go.put("label", g.label);
            JSONArray ta = new JSONArray();
            for (FileType t : g.types) {
                JSONObject to = new JSONObject();
                to.put("ext", t.ext);
                to.put("description", t.description);
                to.put("selected", selected.contains(t.ext));
                to.put("custom", custom.contains(t.ext));
                ta.add(to);
            }
            go.put("types", ta);
            ga.add(go);
        }
        root.put("groups", ga);
        JSONArray oa = new JSONArray();
        oa.addAll(orphans());
        root.put("orphans", oa);
        JSONArray wa = new JSONArray();
        wa.addAll(warnings);
        root.put("warnings", wa);
        return root;
    }

    // =====================================================================================
    // Video types (M4.4): the catalog's "video" group is the single source of truth for
    // "is this a video" — transcode (FileUtils.is_video), search grouping + Videos filter
    // (Cass7Funcs.is_movie) and the folder listing's video flag all read it from here.
    // =====================================================================================

    /** Video containers stock ffmpeg cannot decode: indexed and downloadable, never transcoded or shown
     *  as video. BRAW needs Blackmagic's proprietary SDK (plan Q7). */
    public static final Set<String> NON_TRANSCODABLE =
            new java.util.HashSet<String>(java.util.Arrays.asList(".braw"));

    static final String VIDEO_GROUP = "video";
    private static volatile Set<String> videoCache = java.util.Collections.emptySet();
    private static long videoMtime = Long.MIN_VALUE;
    private static long videoChecked = 0;

    /** Extensions in the catalog's video group. is_movie() runs per search result, so the catalog
     *  is re-stat'ed at most every 2s and re-parsed only when its mtime changes. */
    public static Set<String> catalogVideoExtensions() {
        long now = System.currentTimeMillis();
        synchronized (FileTypesConfig.class) {
            if (now - videoChecked < 2000) return videoCache;
            videoChecked = now;
            try {
                File f = new File(defaultConfigDir(), CATALOG_FILE);
                long m = f.isFile() ? f.lastModified() : -1L;
                if (m != videoMtime) {
                    Set<String> s = new LinkedHashSet<String>();
                    if (m >= 0) {
                        FileTypesConfig c = new FileTypesConfig(f.getParentFile());
                        c.reload();
                        for (Group g : c.groups) if (VIDEO_GROUP.equals(g.id)) for (FileType t : g.types) s.add(t.ext);
                    }
                    videoCache = java.util.Collections.unmodifiableSet(s);
                    videoMtime = m;
                }
            } catch (Exception e) {
                // keep the previous set: a transient read error must not flip every video to "other"
            }
            return videoCache;
        }
    }

    /** ".mxf" for "/a/b/clip.MXF" (lowercased); "" when there is no extension. */
    public static String extensionOf(String nameOrExt) {
        if (nameOrExt == null) return "";
        String s = nameOrExt;
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) s = s.substring(slash + 1);
        int dot = s.lastIndexOf('.');
        if (dot < 0) return s.isEmpty() ? "" : "." + s.toLowerCase();   // a bare "mxf" means the extension
        return s.substring(dot).toLowerCase();
    }

    public static boolean isCatalogVideo(String nameOrExt) {
        return catalogVideoExtensions().contains(extensionOf(nameOrExt));
    }

    public static boolean isNonTranscodable(String nameOrExt) {
        return NON_TRANSCODABLE.contains(extensionOf(nameOrExt));
    }

    // =====================================================================================
    // Save (M2): selection + catalog add/remove, validated up front, written atomically.
    // =====================================================================================

    /** A rejected save. `status` is the HTTP-ish code the endpoint reports (400 invalid, 409 stale). */
    public static class SaveException extends Exception {
        public final int status;
        public final String currentVersion;
        public SaveException(int status, String msg, String currentVersion) {
            super(msg); this.status = status; this.currentVersion = currentVersion;
        }
    }

    /** One requested catalog addition. */
    public static class NewType {
        public final String ext, description, groupId;
        public NewType(String ext, String description, String groupId) {
            this.ext = ext; this.description = description; this.groupId = groupId;
        }
    }

    // ext: ".heic", ".braw" ... lowercase, dot + 1-16 of [a-z0-9+_-]
    static final java.util.regex.Pattern EXT_RE = java.util.regex.Pattern.compile("^\\.[a-z0-9][a-z0-9+_-]{0,15}$");
    // description: no ',' (field separator), no CR/LF (line separator), no '<'/'>' (legacy pages render it as
    // HTML) and no '.' (the endpoint name check is fname.contains(...) over the raw URL) — see the plan, §2.
    static final java.util.regex.Pattern DESC_RE = java.util.regex.Pattern.compile("^[A-Za-z0-9 ()+/_'&-]{1,60}$");

    /** Serialises every save in this JVM: read-check-write must not interleave. */
    static final Object SAVE_LOCK = new Object();

    /**
     * Apply a save. `selected` is the complete desired selection; `add`/`remove` edit the catalog.
     * Removing a type also unticks it. Existing orphans (selected keys not in the catalog) are kept.
     * Returns the reloaded config. Throws SaveException(400/409) before writing anything when invalid.
     */
    public static FileTypesConfig save(File configDir, List<String> selected, List<NewType> add, List<String> remove,
                                       String expectedVersion, String user) throws Exception {
        synchronized (SAVE_LOCK) {
            FileTypesConfig cur = new FileTypesConfig(configDir);
            cur.reload();

            if (expectedVersion == null || !expectedVersion.equals(cur.version)) {
                throw new SaveException(409, "stale: the file types were changed since you loaded them", cur.version);
            }

            // ---- normalise + validate (nothing is written until all of this passes) ----
            Set<String> removeSet = new LinkedHashSet<String>();
            for (String r : remove) {
                String e = norm(r);
                if (!cur.catalog.containsKey(e)) throw new SaveException(400, "unknown type: " + e, cur.version);
                if (!cur.custom.contains(e)) throw new SaveException(400, "cannot remove shipped type " + e, cur.version);
                removeSet.add(e);
            }
            Map<String, NewType> addMap = new LinkedHashMap<String, NewType>();
            Set<String> groupIds = new LinkedHashSet<String>();
            for (Group g : cur.groups) groupIds.add(g.id);
            for (NewType a : add) {
                String e = norm(a.ext);
                if (!EXT_RE.matcher(e).matches()) throw new SaveException(400, "invalid extension: " + a.ext, cur.version);
                if (cur.catalog.containsKey(e) || addMap.containsKey(e)) throw new SaveException(400, "already exists: " + e, cur.version);
                if (removeSet.contains(e)) throw new SaveException(400, "cannot add and remove " + e, cur.version);
                String d = a.description == null ? "" : a.description.trim();
                if (!DESC_RE.matcher(d).matches()) throw new SaveException(400, "invalid description for " + e + " (1-60 letters, digits, spaces, ( ) + / _ ' & -)", cur.version);
                String gid = a.groupId == null ? "" : a.groupId.trim().toLowerCase();
                if (!groupIds.contains(gid)) throw new SaveException(400, "unknown group: " + a.groupId, cur.version);
                addMap.put(e, new NewType(e, d, gid));
            }

            Set<String> finalSel = new LinkedHashSet<String>();
            for (String s : selected) {
                String e = norm(s);
                if (removeSet.contains(e)) continue;                         // removal implies untick
                boolean known = (cur.catalog.containsKey(e)) || addMap.containsKey(e) || cur.orphans().contains(e);
                if (!known) throw new SaveException(400, "unknown type: " + e, cur.version);
                finalSel.add(e);
            }
            for (String o : cur.orphans()) if (cur.selected.contains(o)) finalSel.add(o); // never drop orphans silently
            if (finalSel.isEmpty()) throw new SaveException(400, "at least one file type must be selected", cur.version);

            // ---- build new file contents ----
            String sep = detectSeparator(new File(configDir, CATALOG_FILE));
            List<String> newCatalog = cur.catalogLinesWith(addMap, removeSet);
            Map<String, String> lineByExt = parseExtLines(newCatalog);

            Set<String> newCustom = new LinkedHashSet<String>(cur.custom);
            newCustom.removeAll(removeSet);
            newCustom.addAll(addMap.keySet());

            // step a: selection without removed keys and WITHOUT new keys (all in old AND new catalog)
            List<String> selA = new ArrayList<String>();
            // step d: final selection
            List<String> selD = new ArrayList<String>();
            for (Map.Entry<String, String> en : lineByExt.entrySet()) {
                if (!finalSel.contains(en.getKey())) continue;
                selD.add(en.getValue());
                if (!addMap.containsKey(en.getKey())) selA.add(en.getValue());
            }
            Map<String, String> oldSelLines = cur.selectionLinesByExt();
            for (String o : cur.orphans()) {
                if (finalSel.contains(o) && oldSelLines.containsKey(o)) { selA.add(oldSelLines.get(o)); selD.add(oldSelLines.get(o)); }
            }

            boolean catalogChanges = !addMap.isEmpty() || !removeSet.isEmpty();
            File selFile = new File(configDir, SELECTION_FILE);
            // Invariant at every instant: every key in FileExtensions.txt is in FileExtensions_All.txt
            // (Cass7Funcs.get_thumb would otherwise see a selected-and-indexed type it can't look up).
            if (catalogChanges) {
                atomicWrite(selFile, join(selA, sep));                                            // a
                atomicWrite(new File(configDir, CATALOG_FILE), join(newCatalog, sep));            // b
                atomicWrite(new File(configDir, CUSTOM_FILE), join(new ArrayList<String>(newCustom), sep)); // c
            }
            atomicWrite(selFile, join(selD, sep));                                                // d

            FileTypesConfig after = new FileTypesConfig(configDir);
            after.reload();

            List<String> selAdded = new ArrayList<String>(), selRemoved = new ArrayList<String>();
            for (String e : after.selected) if (!cur.selected.contains(e)) selAdded.add(e);
            for (String e : cur.selected) if (!after.selected.contains(e)) selRemoved.add(e);
            after.lastDiff = new String[][] {
                selAdded.toArray(new String[0]), selRemoved.toArray(new String[0]),
                addMap.keySet().toArray(new String[0]), removeSet.toArray(new String[0]) };
            LocalFuncs.pw("[FileTypes] saved by " + user + " version " + cur.version + " -> " + after.version
                    + " selected+" + selAdded + " selected-" + selRemoved
                    + " catalog+" + addMap.keySet() + " catalog-" + removeSet);
            return after;
        }
    }

    /** Set by save(): {selectedAdded, selectedRemoved, catalogAdded, catalogRemoved}. */
    public String[][] lastDiff = null;

    public JSONObject saveResultJson() {
        JSONObject o = toJson();
        if (lastDiff != null) {
            String[] keys = { "selectedAdded", "selectedRemoved", "catalogAdded", "catalogRemoved" };
            for (int i = 0; i < keys.length; i++) {
                JSONArray a = new JSONArray();
                for (String s : lastDiff[i]) a.add(s);
                o.put(keys[i], a);
            }
        }
        return o;
    }

    static String norm(String e) {
        String s = e == null ? "" : e.trim().toLowerCase();
        if (!s.isEmpty() && !s.startsWith(".")) s = "." + s;
        return s;
    }

    /** The catalog's raw lines (verbatim, comments kept) with additions inserted and removals dropped. */
    List<String> catalogLinesWith(Map<String, NewType> addMap, Set<String> removeSet) throws Exception {
        List<String> in = readLines(new File(configDir, CATALOG_FILE));
        while (!in.isEmpty() && in.get(in.size() - 1).trim().isEmpty()) in.remove(in.size() - 1);
        // group id -> index of the last line belonging to that group's section
        List<String> out = new ArrayList<String>();
        Map<String, Integer> lastIdx = new LinkedHashMap<String, Integer>();
        String curGroup = null;
        for (String raw : in) {
            String line = stripBom(raw).trim();
            if (line.startsWith("@,")) {
                String[] f = line.split(",", 3);
                curGroup = f.length > 2 ? f[2].trim().toLowerCase() : null;
                out.add(raw);
                if (curGroup != null) lastIdx.put(curGroup, out.size() - 1);
                continue;
            }
            int c = line.indexOf(',');
            String ext = (c >= 0 ? line.substring(0, c) : line).trim().toLowerCase();
            if (removeSet.contains(ext)) continue;
            out.add(raw);
            if (curGroup != null && !line.isEmpty()) lastIdx.put(curGroup, out.size() - 1);
        }
        // insert additions at the end of their group's section (later inserts shift earlier indexes, so go
        // group by group from the bottom of the file up)
        List<Map.Entry<String, Integer>> sections = new ArrayList<Map.Entry<String, Integer>>(lastIdx.entrySet());
        java.util.Collections.sort(sections, new java.util.Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) { return b.getValue() - a.getValue(); }
        });
        for (Map.Entry<String, Integer> sec : sections) {
            List<String> ins = new ArrayList<String>();
            for (NewType t : addMap.values()) {
                if (t.groupId.equals(sec.getKey())) ins.add(t.ext + "," + t.description + "," + defaultIcon(t.groupId));
            }
            out.addAll(sec.getValue() + 1, ins);
        }
        return out;
    }

    /** The legacy glyph for a group, borrowed from the first type in that group (uiv5 ignores it). */
    String defaultIcon(String groupId) {
        for (Group g : groups) if (g.id.equals(groupId) && !g.types.isEmpty()) return g.types.get(0).icon;
        return "";
    }

    Map<String, String> selectionLinesByExt() throws Exception {
        Map<String, String> m = new LinkedHashMap<String, String>();
        File f = new File(configDir, SELECTION_FILE);
        if (!f.isFile()) return m;
        for (String raw : readLines(f)) {
            String line = stripBom(raw).trim();
            if (line.isEmpty() || line.startsWith("@") || line.startsWith("#")) continue;
            int c = line.indexOf(',');
            m.put((c >= 0 ? line.substring(0, c) : line).trim().toLowerCase(), line);
        }
        return m;
    }

    static Map<String, String> parseExtLines(List<String> lines) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (String raw : lines) {
            String line = stripBom(raw).trim();
            if (line.isEmpty() || line.startsWith("@") || line.startsWith("#")) continue;
            int c = line.indexOf(',');
            String ext = (c >= 0 ? line.substring(0, c) : line).trim().toLowerCase();
            if (ext.startsWith(".") && !m.containsKey(ext)) m.put(ext, line);
        }
        return m;
    }

    static String detectSeparator(File f) {
        try {
            byte[] b = Files.readAllBytes(f.toPath());
            for (int i = 0; i < b.length; i++) if (b[i] == '\n') return (i > 0 && b[i - 1] == '\r') ? "\r\n" : "\n";
        } catch (Exception ignore) {}
        return System.getProperty("line.separator");
    }

    static String join(List<String> lines, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append(sep);
        return sb.toString();
    }

    /** tmp + fsync + ATOMIC_MOVE (fallback: plain replace), as UserCollection.saveUserCollection. */
    static void atomicWrite(File target, String content) throws Exception {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp, false);
        try {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.getFD().sync();
        } finally {
            fos.close();
        }
        try {
            Files.move(tmp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static List<String> readLines(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            String s = new String(readAll(in), StandardCharsets.UTF_8);
            List<String> out = new ArrayList<String>();
            for (String l : s.split("\r\n|\n|\r", -1)) out.add(l);
            return out;
        } finally {
            in.close();
        }
    }

    static byte[] readAll(FileInputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        return bo.toByteArray();
    }

    static String stripBom(String s) {
        return (s.length() > 0 && s.charAt(0) == '﻿') ? s.substring(1) : s;
    }
}

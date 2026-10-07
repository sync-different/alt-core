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

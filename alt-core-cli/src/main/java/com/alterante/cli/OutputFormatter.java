package com.alterante.cli;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

import java.net.URLDecoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.*;

/**
 * Human-readable output formatting for CLI responses.
 * When --json flag is used, falls back to raw JSON output via HttpSession.printResponse().
 */
public class OutputFormatter {

    private static boolean jsonMode = false;

    public static void setJsonMode(boolean mode) {
        jsonMode = mode;
    }

    public static boolean isJsonMode() {
        return jsonMode;
    }

    /**
     * Print response — delegates to raw JSON or human-readable based on mode.
     */
    public static void print(HttpResponse<String> response) {
        if (jsonMode) {
            HttpSession.printResponse(response);
            return;
        }

        int status = response.statusCode();
        String body = response.body().trim();

        if (status != 200) {
            System.err.println("Error: HTTP " + status);
            if (!body.isEmpty()) System.err.println(body);
            return;
        }

        if (body.isEmpty()) {
            System.out.println("(empty response)");
            return;
        }

        // Try to parse as JSON and format
        try {
            JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
            Object parsed = parser.parse(body);
            if (parsed instanceof JSONObject obj) {
                printJsonObject(obj);
            } else if (parsed instanceof JSONArray arr) {
                printJsonArray(arr);
            } else {
                System.out.println(body);
            }
        } catch (Exception e) {
            // Not JSON — print as-is
            System.out.println(body);
        }
    }

    private static void printJsonObject(JSONObject obj) {
        // Detect response type by keys and format accordingly
        if (obj.containsKey("objFound") && obj.containsKey("fighters")) {
            printQueryResults(obj);
        } else if (obj.containsKey("objFound") && !obj.containsKey("fighters")) {
            printSidebarStats(obj);
        } else if (obj.containsKey("fighters") && !obj.containsKey("objFound")) {
            printFightersGeneric(obj);
        } else if (obj.containsKey("nodes")) {
            printNodes(obj);
        } else if (obj.containsKey("users")) {
            printUsers(obj);
        } else if (obj.containsKey("messages")) {
            printChat(obj);
        } else {
            // Generic key-value display
            printGenericObject(obj);
        }
    }

    // --- Sidebar ---

    private static void printSidebarStats(JSONObject obj) {
        JSONArray objFound = (JSONArray) obj.get("objFound");
        if (objFound == null || objFound.isEmpty()) {
            System.out.println("No data.");
            return;
        }

        JSONObject counts = (JSONObject) objFound.get(0);
        System.out.println("File Counts");
        System.out.println("-----------");
        printRow("Total", counts, "nTotal");
        printRow("Photos", counts, "nPhoto");
        printRow("Videos", counts, "nVideo");
        printRow("Music", counts, "nMusic");
        printRow("Documents", counts, "nDocuments");
        printRow("  PDF", counts, "nPdf");
        printRow("  DOC", counts, "nDoc");
        printRow("  XLS", counts, "nXls");
        printRow("  PPT", counts, "nPpt");

        if (objFound.size() > 1) {
            JSONObject timeline = (JSONObject) objFound.get(1);
            System.out.println();
            System.out.println("Timeline");
            System.out.println("--------");
            printRow("Past 24h", timeline, "nPast24h");
            printRow("Past 3 days", timeline, "nPast3d");
            printRow("Past 7 days", timeline, "nPast7d");
            printRow("Past 14 days", timeline, "nPast14d");
            printRow("Past 30 days", timeline, "nPast30d");
            printRow("Past year", timeline, "nPast365d");
            printRow("All time", timeline, "nAllTime");
        }
    }

    // --- Query results ---

    private static void printQueryResults(JSONObject obj) {
        JSONArray fighters = (JSONArray) obj.get("fighters");
        JSONArray objFound = (JSONArray) obj.get("objFound");

        // Print summary counts first
        if (objFound != null && !objFound.isEmpty()) {
            JSONObject counts = (JSONObject) objFound.get(0);
            String current = str(counts, "nCurrent");
            String total = str(counts, "nTotal");
            System.out.println("Results: " + current + " of " + total + " files");
            System.out.println();
        }

        if (fighters == null || fighters.isEmpty()) {
            System.out.println("No files found.");
            return;
        }

        // Print files as a table
        // Columns: #  Name  Type  Size  Date  Tags
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"#", "Name", "Type", "Size", "Date", "Tags"});

        int i = 1;
        for (Object item : fighters) {
            JSONObject f = (JSONObject) item;
            String name = str(f, "name");
            String ext = str(f, "file_ext");
            String size = formatSize(str(f, "file_size"));
            String date = formatDate(str(f, "file_date"));
            String tags = str(f, "file_tags").replaceAll(",$", "");
            String md5 = str(f, "nickname");

            rows.add(new String[]{String.valueOf(i++), name, ext, size, date, tags.isEmpty() ? "-" : tags});
        }

        printTable(rows);

        // Print MD5 hashes below the table for reference
        System.out.println();
        int j = 1;
        for (Object item : fighters) {
            JSONObject f = (JSONObject) item;
            System.out.println("  [" + j++ + "] md5: " + str(f, "nickname"));
        }
    }

    // --- Suggest results ---

    public static void printSuggest(HttpResponse<String> response) {
        if (jsonMode) {
            HttpSession.printResponse(response);
            return;
        }

        int status = response.statusCode();
        String body = response.body().trim();

        if (status != 200) {
            System.err.println("Error: HTTP " + status);
            if (!body.isEmpty()) System.err.println(body);
            return;
        }

        try {
            JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
            Object parsed = parser.parse(body);
            if (parsed instanceof JSONObject obj) {
                printSuggestResults(obj);
            } else {
                System.out.println(body);
            }
        } catch (Exception e) {
            System.out.println(body);
        }
    }

    private static void printSuggestResults(JSONObject obj) {
        JSONArray fighters = (JSONArray) obj.get("fighters");

        if (fighters == null || fighters.isEmpty()) {
            System.out.println("No suggestions.");
            return;
        }

        System.out.println("Suggestions:");
        System.out.println();

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"#", "Name", "Type"});

        int i = 1;
        for (Object item : fighters) {
            JSONObject f = (JSONObject) item;
            String name = str(f, "name");
            String type = str(f, "type");
            rows.add(new String[]{String.valueOf(i++), name, type});
        }

        printTable(rows);
        System.out.println();
        System.out.println(fighters.size() + " suggestion(s)");
    }

    // --- Tags ---

    private static void printFightersGeneric(JSONObject obj) {
        JSONArray fighters = (JSONArray) obj.get("fighters");

        // Detect if these are tags (have tagname/tagcnt) or suggestions
        if (fighters == null || fighters.isEmpty()) {
            System.out.println("No results.");
            return;
        }

        JSONObject first = (JSONObject) fighters.get(0);
        if (first.containsKey("tagname")) {
            printTags(obj, fighters);
        } else {
            // Generic list
            for (Object item : fighters) {
                if (item instanceof JSONObject o) {
                    printGenericObject(o);
                    System.out.println();
                } else {
                    System.out.println("  " + item);
                }
            }
        }
    }

    private static void printTags(JSONObject obj, JSONArray fighters) {
        String user = str(obj, "username");
        String admin = str(obj, "isAdmin");
        if (!user.isEmpty()) {
            System.out.println("User: " + user + (admin.equals("true") ? " (admin)" : ""));
            System.out.println();
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Tag", "Count"});
        for (Object item : fighters) {
            JSONObject t = (JSONObject) item;
            rows.add(new String[]{str(t, "tagname"), str(t, "tagcnt")});
        }
        printTable(rows);
        System.out.println();
        System.out.println(fighters.size() + " tags total");
    }

    // --- Nodes ---

    private static void printNodes(JSONObject obj) {
        JSONArray nodes = (JSONArray) obj.get("nodes");
        if (nodes == null || nodes.isEmpty()) {
            System.out.println("No nodes found.");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Machine", "Type", "IP", "Port", "Index %", "Last Ping"});

        for (Object item : nodes) {
            JSONObject n = (JSONObject) item;
            String machine = urlDecode(str(n, "node_machine"));
            String type = str(n, "node_type");
            String ip = str(n, "node_ip");
            String port = str(n, "node_port");
            String idxPct = str(n, "node_idx_percent");
            String lastPing = urlDecode(str(n, "node_lastping"));

            rows.add(new String[]{machine, type, ip, port,
                    idxPct.isEmpty() ? "-" : idxPct + "%", lastPing});
        }
        printTable(rows);
        System.out.println();
        System.out.println(nodes.size() + " node(s)");
    }

    // --- Users ---

    private static void printUsers(JSONObject obj) {
        JSONArray users = (JSONArray) obj.get("users");
        if (users == null || users.isEmpty()) {
            System.out.println("No users found.");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Username", "Email", "Has Perm"});
        for (Object item : users) {
            JSONObject u = (JSONObject) item;
            rows.add(new String[]{str(u, "username"), str(u, "email"), str(u, "hasPerm")});
        }
        printTable(rows);
    }

    // --- Chat ---

    private static void printChat(JSONObject obj) {
        JSONArray messages = (JSONArray) obj.get("messages");
        if (messages == null || messages.isEmpty()) {
            System.out.println("No messages.");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Object item : messages) {
            JSONObject m = (JSONObject) item;
            String dateStr = str(m, "msg_date");
            String user = str(m, "msg_user");
            String type = str(m, "msg_type");
            String body = str(m, "msg_body");

            // Decode message body
            body = decodeChatBody(body, type);

            String time = dateStr;
            try {
                long ts = Long.parseLong(dateStr);
                time = sdf.format(new Date(ts));
            } catch (NumberFormatException ignored) {}

            System.out.println("[" + time + "] " + user + ": " + body);
        }

        Object likes = obj.get("likes");
        if (likes != null) {
            System.out.println();
            System.out.println("Likes: " + likes);
        }
    }

    /**
     * Decode chat message body. CHAT messages are Base64-encoded.
     * EVENT messages use '#' quoting for JSON-like structures.
     */
    private static String decodeChatBody(String body, String type) {
        if (body == null || body.isEmpty()) return "";

        if ("CHAT".equalsIgnoreCase(type)) {
            // Base64 decode
            try {
                return new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return body; // Not valid Base64, return as-is
            }
        }

        // EVENT messages: replace '#' quoting → real quotes, then extract msg value
        if (body.contains("'#'")) {
            String cleaned = body.replace("'#'", "\"");
            // Try to extract the "msg" value from the pseudo-JSON
            try {
                JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
                Object parsed = parser.parse(cleaned);
                if (parsed instanceof JSONObject eventObj) {
                    String msg = str(eventObj, "msg");
                    if (!msg.isEmpty()) return msg;
                }
            } catch (Exception ignored) {}
            return cleaned;
        }

        return body;
    }

    // --- JSON Array (e.g. folders) ---

    private static void printJsonArray(JSONArray arr) {
        if (arr.isEmpty()) {
            System.out.println("(empty list)");
            return;
        }

        // If array of objects, try table format
        if (arr.get(0) instanceof JSONObject) {
            JSONObject first = (JSONObject) arr.get(0);
            Set<String> keys = first.keySet();
            List<String[]> rows = new ArrayList<>();
            rows.add(keys.toArray(new String[0]));
            for (Object item : arr) {
                JSONObject o = (JSONObject) item;
                String[] row = new String[keys.size()];
                int i = 0;
                for (String k : keys) {
                    row[i++] = str(o, k);
                }
                rows.add(row);
            }
            printTable(rows);
        } else {
            // Simple list
            for (Object item : arr) {
                System.out.println("  " + item);
            }
        }
    }

    // --- Generic object ---

    private static void printGenericObject(JSONObject obj) {
        int maxKeyLen = 0;
        for (String key : obj.keySet()) {
            if (key.length() > maxKeyLen) maxKeyLen = key.length();
        }
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            String val = entry.getValue() != null ? entry.getValue().toString() : "";
            // Skip huge base64 thumbnails
            if (val.length() > 200) val = val.substring(0, 80) + "... [truncated]";
            System.out.printf("  %-" + (maxKeyLen + 2) + "s %s%n", entry.getKey() + ":", val);
        }
    }

    // --- Table rendering ---

    private static void printTable(List<String[]> rows) {
        if (rows.isEmpty()) return;

        int cols = rows.get(0).length;
        int[] widths = new int[cols];

        for (String[] row : rows) {
            for (int i = 0; i < cols && i < row.length; i++) {
                if (row[i] != null && row[i].length() > widths[i]) {
                    widths[i] = row[i].length();
                }
            }
        }

        // Print header
        String[] header = rows.get(0);
        StringBuilder headerLine = new StringBuilder();
        StringBuilder separatorLine = new StringBuilder();
        for (int i = 0; i < cols; i++) {
            if (i > 0) {
                headerLine.append("  ");
                separatorLine.append("  ");
            }
            headerLine.append(pad(header[i], widths[i]));
            separatorLine.append("-".repeat(widths[i]));
        }
        System.out.println(headerLine);
        System.out.println(separatorLine);

        // Print data rows
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < cols; i++) {
                if (i > 0) line.append("  ");
                String val = (i < row.length && row[i] != null) ? row[i] : "";
                line.append(pad(val, widths[i]));
            }
            System.out.println(line);
        }
    }

    // --- Helpers ---

    private static String str(JSONObject obj, String key) {
        Object val = obj.get(key);
        return val != null ? val.toString() : "";
    }

    private static void printRow(String label, JSONObject obj, String key) {
        System.out.printf("  %-14s %s%n", label + ":", str(obj, key));
    }

    private static String pad(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private static String formatSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) return "-";
        try {
            long bytes = Long.parseLong(sizeStr);
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        } catch (NumberFormatException e) {
            return sizeStr;
        }
    }

    private static String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "-";
        // Input format: "2022.12.15 19:00:26.304 PST"
        // Output: "2022-12-15 19:00"
        try {
            String[] parts = dateStr.split(" ");
            String date = parts[0].replace('.', '-');
            String time = parts.length > 1 ? parts[1] : "";
            if (time.contains(":")) {
                // Truncate to HH:mm
                String[] tp = time.split(":");
                time = tp[0] + ":" + tp[1];
            }
            return date + " " + time;
        } catch (Exception e) {
            return dateStr;
        }
    }

    private static String urlDecode(String s) {
        if (s == null || s.isEmpty()) return s;
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}

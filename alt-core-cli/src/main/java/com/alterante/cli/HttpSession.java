package com.alterante.cli;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/**
 * Manages server configuration, session state, and HTTP communication.
 * Config and session are persisted to /tmp/alt-core-cli-config.
 */
public class HttpSession {

    private static final String CONFIG_FILE = System.getProperty("user.home") + "/.alt-core-cli";

    private String protocol = "http";
    private String server = "localhost";
    private int port = 8081;
    private String uuid = null;

    private final HttpClient client;

    public HttpSession() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        loadConfig();
    }

    // --- Config ---

    public String getProtocol() { return protocol; }
    public String getServer() { return server; }
    public int getPort() { return port; }
    public String getUuid() { return uuid; }

    public void setProtocol(String protocol) { this.protocol = protocol; }
    public void setServer(String server) { this.server = server; }
    public void setPort(int port) { this.port = port; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getBaseUrl() {
        if ((protocol.equals("https") && port == 443) || (protocol.equals("http") && port == 80)) {
            return protocol + "://" + server;
        }
        return protocol + "://" + server + ":" + port;
    }

    // --- Persistence ---

    public void loadConfig() {
        File f = new File(CONFIG_FILE);
        if (!f.exists()) return;
        try (FileInputStream fis = new FileInputStream(f)) {
            Properties props = new Properties();
            props.load(fis);
            if (props.containsKey("protocol")) protocol = props.getProperty("protocol");
            if (props.containsKey("server")) server = props.getProperty("server");
            if (props.containsKey("port")) port = Integer.parseInt(props.getProperty("port"));
            if (props.containsKey("uuid")) uuid = props.getProperty("uuid");
        } catch (Exception e) {
            System.err.println("Warning: Could not load config: " + e.getMessage());
        }
    }

    public void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            Properties props = new Properties();
            props.setProperty("protocol", protocol);
            props.setProperty("server", server);
            props.setProperty("port", String.valueOf(port));
            if (uuid != null) props.setProperty("uuid", uuid);
            props.store(fos, "alt-core-cli configuration");
        } catch (Exception e) {
            System.err.println("Warning: Could not save config: " + e.getMessage());
        }
    }

    // --- HTTP Methods ---

    /**
     * Make a GET request. Path should start with /cass/.
     * Returns the response body as a String.
     */
    public HttpResponse<String> get(String path, boolean includeAuth) throws Exception {
        String url = getBaseUrl() + appendUuid(path, includeAuth);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30));

        if (includeAuth && uuid != null) {
            builder.header("Cookie", "uuid=" + uuid);
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> get(String path) throws Exception {
        return get(path, true);
    }

    /**
     * Make a POST request.
     */
    public HttpResponse<String> post(String url, String contentType, String body, boolean includeAuth) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", contentType)
                .timeout(Duration.ofSeconds(30));

        if (includeAuth && uuid != null) {
            builder.header("Cookie", "uuid=" + uuid);
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Download a file (binary GET) and save to disk.
     */
    public HttpResponse<byte[]> getBytes(String path, boolean includeAuth) throws Exception {
        String url = getBaseUrl() + appendUuid(path, includeAuth);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(60));

        if (includeAuth && uuid != null) {
            builder.header("Cookie", "uuid=" + uuid);
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    // --- Utility ---

    /**
     * Append uuid= query parameter to path when authenticated.
     * The server parses UUID from both cookies and query params;
     * sending both ensures compatibility (matches uiv5 frontend behavior).
     */
    private String appendUuid(String path, boolean includeAuth) {
        if (includeAuth && uuid != null) {
            String separator = path.contains("?") ? "&" : "?";
            return path + separator + "uuid=" + encode(uuid);
        }
        return path;
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Print a JSON response summary to stdout.
     */
    public static void printResponse(HttpResponse<String> response) {
        System.out.println("{");
        System.out.println("  \"status\": " + response.statusCode() + ",");
        System.out.println("  \"url\": \"" + response.uri() + "\",");
        String body = response.body();
        // If body looks like JSON, print it directly; otherwise wrap in quotes
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            System.out.println("  \"body\": " + trimmed);
        } else {
            // Escape for JSON string
            String escaped = trimmed
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            if (escaped.length() > 2000) {
                escaped = escaped.substring(0, 2000) + "... [truncated]";
            }
            System.out.println("  \"body\": \"" + escaped + "\"");
        }
        System.out.println("}");
    }
}

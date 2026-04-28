package com.mycompany.app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches the update manifest (latest.json) from hivebot and exposes a
 * lightweight {@link ManifestInfo} result. No parsing library — the manifest
 * has 5 fields of known shape, regex is enough.
 *
 * See internal/PROJECT_TRAY_UPDATE_SPEC.md for format + field semantics.
 */
final class UpdateManager {

    static final String MANIFEST_URL =
            "https://hivebot.co/download/alt-core/latest.json";

    private static final Pattern FIELD_RE =
            Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");

    private UpdateManager() {}

    /** Immutable parsed manifest. Any field may be null if absent from JSON. */
    static final class ManifestInfo {
        final String version;
        final String downloadUrl;
        final String sha256;
        final String releaseNotesUrl;
        final String releasedAt;

        ManifestInfo(String version, String downloadUrl, String sha256,
                     String releaseNotesUrl, String releasedAt) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
            this.releaseNotesUrl = releaseNotesUrl;
            this.releasedAt = releasedAt;
        }
    }

    /**
     * Fetch and parse the manifest. Retries on transient 5xx errors
     * (observed 2026-04-21: hivebot's CDN returned a 502 between two 200s).
     *
     * @return ManifestInfo on success, null on any failure (caller decides
     *         whether to surface the failure to the user)
     */
    static ManifestInfo fetchManifest() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(MANIFEST_URL))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "alt-core-updater/1")
                .GET()
                .build();

        // 3 attempts with 1s / 2s backoff after failures
        int[] backoffMs = {0, 1000, 2000};
        for (int attempt = 0; attempt < backoffMs.length; attempt++) {
            if (backoffMs[attempt] > 0) {
                try {
                    Thread.sleep(backoffMs[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            try {
                HttpResponse<String> resp = client.send(
                        req, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code == 200) {
                    return parse(resp.body());
                }
                // 5xx: retry. 4xx: don't retry (our client bug or missing file).
                if (code < 500) {
                    System.err.println("update: manifest HTTP " + code + " (not retrying)");
                    return null;
                }
                System.err.println("update: manifest HTTP " + code
                        + " (attempt " + (attempt + 1) + ")");
            } catch (Exception e) {
                System.err.println("update: manifest fetch failed — "
                        + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + " (attempt " + (attempt + 1) + ")");
            }
        }
        return null;
    }

    private static ManifestInfo parse(String json) {
        String version = null, downloadUrl = null, sha256 = null,
                releaseNotesUrl = null, releasedAt = null;
        Matcher m = FIELD_RE.matcher(json);
        while (m.find()) {
            String k = m.group(1);
            String v = m.group(2);
            switch (k) {
                case "version":           version = v;         break;
                case "download_url":      downloadUrl = v;     break;
                case "sha256":            sha256 = v;          break;
                case "release_notes_url": releaseNotesUrl = v; break;
                case "released_at":       releasedAt = v;      break;
                default: /* ignore unknown fields (min_macos_version, etc.) */
            }
        }
        if (version == null || version.isEmpty()) {
            System.err.println("update: manifest missing 'version' field");
            return null;
        }
        return new ManifestInfo(version, downloadUrl, sha256, releaseNotesUrl, releasedAt);
    }

    /**
     * Lexical compare — the YYYYMMDDNN format in update.last is naturally
     * sortable as a string. "??" or empty strings are treated as "can't compare".
     * See PROJECT_TRAY_UPDATE_SPEC.md decision #8: never offer downgrades.
     */
    static boolean isNewer(String candidate, String current) {
        if (candidate == null || candidate.isEmpty()) return false;
        if (current == null || current.isEmpty()) return false;
        if ("??".equals(current) || "…".equals(current)) return false;
        return candidate.compareTo(current) > 0;
    }
}

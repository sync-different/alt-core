package com.mycompany.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * Download + verify + install an alt-core update.
 *
 * Called from the tray "Update now" action. See
 * internal/PROJECT_TRAY_UPDATE_SPEC.md for the full install-flow spec,
 * including the detached-helper rationale and Gatekeeper handling.
 *
 * This class only handles steps 1-7 (pre-flight through writing the helper).
 * Step 8 (launch helper + exit) happens in the caller so the caller controls
 * the JVM shutdown.
 */
final class Installer {

    private static final long MIN_FREE_BYTES = 500L * 1024 * 1024; // 500 MB
    static final Path APPLICATIONS = Path.of("/Applications");
    static final Path INSTALLED_APP = APPLICATIONS.resolve("alt-core.app");

    /** Progress / failure callback. Called from the installer thread. */
    interface Callback {
        void onProgress(String message);
        void onFailure(String message);
    }

    /** Result of {@link #prepare(UpdateManager.ManifestInfo, Callback, boolean)}. */
    static final class PreparedInstall {
        final Path unzippedApp;     // ~/Library/.../updates/alt-core.app
        final Path helperScript;    // ~/Library/.../updates/apply-update.sh
        final Path lockFile;        // ~/Library/.../updates/.install-in-progress
        final Path logFile;         // ~/Library/.../updates/apply-update.log
        final boolean dryRun;       // true in DEV (not a packaged app)

        PreparedInstall(Path unzippedApp, Path helperScript, Path lockFile,
                        Path logFile, boolean dryRun) {
            this.unzippedApp = unzippedApp;
            this.helperScript = helperScript;
            this.lockFile = lockFile;
            this.logFile = logFile;
            this.dryRun = dryRun;
        }
    }

    private Installer() {}

    static Path updatesDir() {
        return Path.of(System.getProperty("user.home"),
                "Library", "Application Support", "hivebot", "updates");
    }

    /**
     * True if we're running from a packaged /Applications/*.app bundle.
     * False in DEV (running from maven target/). Used to gate the final
     * mv-and-relaunch step of the install — DEV does a dry-run that stops
     * after unzip + codesign verify.
     */
    static boolean isPackagedApp() {
        try {
            URI jarUri = App.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            String path = jarUri.getPath();
            return path != null && path.contains(".app/Contents/");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Full install prep: pre-flight → download → verify → unzip → codesign →
     * write helper script. Returns the prepared paths on success; null on
     * any failure (with the callback already notified).
     *
     * The caller is responsible for the final step: launching the helper
     * and exiting the JVM. See {@link #launchHelper(PreparedInstall, Callback)}.
     *
     * @param allowDryRun if true and we're in DEV, the flow stops after
     *                    codesign verify and returns PreparedInstall{dryRun=true}
     */
    static PreparedInstall prepare(UpdateManager.ManifestInfo info,
                                   Callback cb, boolean allowDryRun) {
        Path updates = updatesDir();
        Path lock = updates.resolve(".install-in-progress");
        Path logFile = updates.resolve("apply-update.log");
        boolean dryRun = !isPackagedApp();
        if (dryRun && !allowDryRun) {
            cb.onFailure("Update only works from the packaged /Applications/ build.");
            return null;
        }

        // --- 1. Pre-flight ---
        try {
            Files.createDirectories(updates);
        } catch (IOException e) {
            cb.onFailure("Could not create updates directory: " + e.getMessage());
            return null;
        }

        // Stale lock cleanup: if a prior install died mid-flight, the lock
        // file may be left behind. Treat locks older than 10 min as stale.
        if (Files.exists(lock)) {
            try {
                long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(lock).toMillis();
                if (ageMs < 10 * 60 * 1000) {
                    cb.onFailure("Another update is already in progress.");
                    return null;
                }
                Files.deleteIfExists(lock);
            } catch (IOException ignored) {}
        }

        if (!dryRun) {
            if (!Files.isWritable(APPLICATIONS)) {
                cb.onFailure("Update requires admin privileges — "
                        + "download manually from hivebot.co");
                return null;
            }
        }

        try {
            long freeBytes = Files.getFileStore(updates).getUsableSpace();
            if (freeBytes < MIN_FREE_BYTES) {
                cb.onFailure("Not enough disk space for update ("
                        + (freeBytes / (1024 * 1024)) + " MB available, "
                        + (MIN_FREE_BYTES / (1024 * 1024)) + " MB required)");
                return null;
            }
        } catch (IOException ignored) {
            // Non-fatal: if we can't check free space, proceed and let the
            // download fail naturally if disk is full
        }

        // Claim the lock
        try {
            Files.writeString(lock, String.valueOf(System.currentTimeMillis()));
        } catch (IOException e) {
            cb.onFailure("Could not claim install lock: " + e.getMessage());
            return null;
        }

        try {
            return runPrepare(info, updates, lock, logFile, dryRun, cb);
        } catch (Exception e) {
            cb.onFailure("Install failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            try { Files.deleteIfExists(lock); } catch (IOException ignored) {}
            return null;
        }
    }

    private static PreparedInstall runPrepare(UpdateManager.ManifestInfo info,
                                              Path updates, Path lock, Path logFile,
                                              boolean dryRun, Callback cb) throws Exception {
        String zipName = "alt-core-" + info.version + ".zip";
        Path zipPath = updates.resolve(zipName);
        Path appPath = updates.resolve("alt-core.app");

        logMark(logFile, "=== install prepare started at " + Instant.now()
                + " (version=" + info.version + ", dryRun=" + dryRun + ") ===");

        // Clean any prior unzipped bundle so the new one lands on a clean slate
        if (Files.exists(appPath)) {
            logMark(logFile, "--- cleaning prior unzipped bundle ---");
            deleteRecursively(appPath);
        }

        // --- 2. Download ---
        cb.onProgress("Downloading alt-core v" + info.version + "…");
        logMark(logFile, "--- download: " + info.downloadUrl + " -> " + zipPath + " ---");
        downloadTo(info.downloadUrl, zipPath);
        logMark(logFile, "  download OK (" + Files.size(zipPath) + " bytes)");

        // --- 3. Verify SHA-256 ---
        cb.onProgress("Verifying download…");
        logMark(logFile, "--- sha256 verify ---");
        String actualSha = sha256(zipPath);
        logMark(logFile, "  expected: " + info.sha256);
        logMark(logFile, "  actual:   " + actualSha);
        if (info.sha256 != null && !info.sha256.isEmpty()
                && !actualSha.equalsIgnoreCase(info.sha256)) {
            Files.deleteIfExists(zipPath);
            logMark(logFile, "  FAIL: sha256 mismatch, zip deleted");
            cb.onFailure("Update verification failed (SHA-256 mismatch). "
                    + "Expected " + info.sha256 + ", got " + actualSha);
            try { Files.deleteIfExists(lock); } catch (IOException ignored) {}
            return null;
        }
        logMark(logFile, "  sha256 OK");

        // --- 4. Unzip ---
        cb.onProgress("Unpacking…");
        logMark(logFile, "--- unzip (ditto -x -k) ---");
        int rc = runExec(logFile,
                "/usr/bin/ditto", "-x", "-k",
                zipPath.toString(), updates.toString());
        if (rc != 0) {
            logMark(logFile, "  FAIL: ditto exit " + rc);
            cb.onFailure("Failed to unzip update (ditto exit " + rc + ")");
            try { Files.deleteIfExists(lock); } catch (IOException ignored) {}
            return null;
        }
        if (!Files.isDirectory(appPath)) {
            logMark(logFile, "  FAIL: unzipped bundle not found at " + appPath);
            cb.onFailure("Unzipped bundle not found at " + appPath);
            try { Files.deleteIfExists(lock); } catch (IOException ignored) {}
            return null;
        }
        logMark(logFile, "  unzip OK: " + appPath);

        // --- 5. codesign verify ---
        cb.onProgress("Verifying code signature…");
        logMark(logFile, "--- codesign --verify --deep --strict ---");
        rc = runExec(logFile,
                "/usr/bin/codesign", "--verify", "--deep", "--strict",
                appPath.toString());
        if (rc != 0) {
            logMark(logFile, "  FAIL: codesign exit " + rc);
            deleteRecursively(appPath);
            Files.deleteIfExists(zipPath);
            cb.onFailure("Downloaded update failed code signature check.");
            try { Files.deleteIfExists(lock); } catch (IOException ignored) {}
            return null;
        }
        logMark(logFile, "  codesign OK");

        if (dryRun) {
            logMark(logFile, "=== dry run complete ===");
            cb.onProgress("Dry run complete — real install only runs from /Applications/");
            try { Files.deleteIfExists(lock); } catch (IOException ignored) {}
            return new PreparedInstall(appPath, null, lock, logFile, true);
        }

        // --- 6. Write helper script ---
        Path helper = updates.resolve("apply-update.sh");
        logMark(logFile, "--- write helper script: " + helper + " ---");
        String script = helperScript(appPath, logFile, lock);
        Files.writeString(helper, script);
        // Make executable
        helper.toFile().setExecutable(true, true);
        logMark(logFile, "  helper script written");

        logMark(logFile, "=== prepare complete, handing off to helper ===");
        cb.onProgress("Installing — alt-core will restart…");
        return new PreparedInstall(appPath, helper, lock, logFile, false);
    }

    /** Append a timestamped marker line to the install log. */
    private static void logMark(Path logFile, String msg) {
        try {
            Files.writeString(logFile, msg + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Non-fatal: logging must not break installs
        }
    }

    /**
     * Launch the apply-update.sh helper as a detached process, then signal the
     * caller via callback that the JVM should exit. The caller is responsible
     * for calling System.exit(0) shortly after this returns.
     */
    static boolean launchHelper(PreparedInstall prep, Callback cb) {
        if (prep == null || prep.dryRun || prep.helperScript == null) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "/bin/bash", prep.helperScript.toString());
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(prep.logFile.toFile()));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(prep.logFile.toFile()));
            pb.start();  // fire-and-forget
            return true;
        } catch (Exception e) {
            cb.onFailure("Could not launch install helper: " + e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static void downloadTo(String url, Path dest) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();
        HttpResponse<Path> resp = client.send(req,
                HttpResponse.BodyHandlers.ofFile(dest,
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.WRITE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING));
        if (resp.statusCode() != 200) {
            throw new IOException("Download HTTP " + resp.statusCode());
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 unavailable: " + e.getMessage());
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static int runExec(Path logFile, String... argv) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        Process p = pb.start();
        return p.waitFor();
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        Files.walkFileTree(p, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Generate the apply-update.sh shell script. All paths are absolute
     * and hard-coded at write time — no variable interpolation from user
     * input, no eval, no PATH dependency (uses /bin/bash explicitly).
     */
    private static String helperScript(Path newApp, Path logFile, Path lock) {
        return "#!/bin/bash\n"
                + "# Generated by alt-core at " + java.time.Instant.now() + "\n"
                + "exec >> \"" + logFile + "\" 2>&1\n"
                + "echo \"=== apply-update started at $(date) ===\"\n"
                + "\n"
                + "# Let the dying JVM flush file handles before we touch its bundle.\n"
                + "sleep 3\n"
                + "\n"
                + "# Safety net: the WebServer shutdown hook has a 5s halt, but in\n"
                + "# case something went wrong, force-quit any surviving alt-core.\n"
                + "/usr/bin/pkill -f \"/Applications/alt-core.app\" || true\n"
                + "sleep 1\n"
                + "\n"
                + "echo \"removing old bundle\"\n"
                + "/bin/rm -rf \"/Applications/alt-core.app\"\n"
                + "if [ $? -ne 0 ]; then\n"
                + "    echo \"ERROR: failed to remove old bundle\"\n"
                + "    /bin/rm -f \"" + lock + "\"\n"
                + "    exit 1\n"
                + "fi\n"
                + "\n"
                + "echo \"moving new bundle into place\"\n"
                + "/bin/mv \"" + newApp + "\" \"/Applications/alt-core.app\"\n"
                + "if [ $? -ne 0 ]; then\n"
                + "    echo \"ERROR: failed to move new bundle — it remains at " + newApp + "\"\n"
                + "    echo \"User can drag it to /Applications/ manually.\"\n"
                + "    /bin/rm -f \"" + lock + "\"\n"
                + "    exit 1\n"
                + "fi\n"
                + "\n"
                + "# Strip com.apple.quarantine so macOS doesn't prompt or translocate.\n"
                + "# See PROJECT_TRAY_UPDATE_SPEC.md → Gatekeeper handling.\n"
                + "/usr/bin/xattr -cr \"/Applications/alt-core.app\" || true\n"
                + "\n"
                + "# Update the uiv5 web UI at the appendage path\n"
                + "# (~/Library/Application Support/hivebot/web/cass/uiv5/dist/)\n"
                + "# using update_web.sh bundled inside the new app. Script uses a\n"
                + "# relative source path so we must cd into Contents/app/ first.\n"
                + "# Non-fatal: if web update fails, the new server still works with\n"
                + "# the prior UI until the next update.\n"
                + "echo \"updating web UI\"\n"
                + "WEB_SCRIPT=\"/Applications/alt-core.app/Contents/app/update_web.sh\"\n"
                + "if [ -f \"$WEB_SCRIPT\" ]; then\n"
                + "    ( cd \"/Applications/alt-core.app/Contents/app\" && /bin/bash \"$WEB_SCRIPT\" ) \\\n"
                + "        || echo \"WARNING: update_web.sh failed (non-fatal)\"\n"
                + "else\n"
                + "    echo \"WARNING: update_web.sh not found in new bundle\"\n"
                + "fi\n"
                + "\n"
                + "echo \"launching new version\"\n"
                + "/usr/bin/open \"/Applications/alt-core.app\"\n"
                + "\n"
                + "/bin/rm -f \"" + lock + "\"\n"
                + "echo \"=== apply-update complete at $(date) ===\"\n";
    }
}

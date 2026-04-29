package com.mycompany.app;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hello world!
 */


public class App {

    private static TrayIcon trayIcon;
    private static MenuItem versionItem;
    private static MenuItem updateAvailableItem;
    private static MenuItem updateNowItem;
    private static PopupMenu trayMenu;
    private static volatile String currentVersion = "…";
    private static volatile UpdateManager.ManifestInfo latestManifest;
    private static final Pattern VERSION_RE =
            Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

    public static void main(String[] args) {
        // Updater path: alt-core-updater.exe (manifested requireAdministrator)
        // invokes us with "--update-from <msi-path>". We're elevated; do the
        // install + relaunch and exit. No tray, no first-run, no server.
        // See Installer.launchHelper Windows path + build_msi.bat mt.exe inject.
        if (args != null && args.length >= 2 && "--update-from".equals(args[0])) {
            runUpdate(args[1]);
            return;
        }
        redirectStdioToFile();
        ensureFirstRunSetup();
        ensureWebFresh();
        installTrayIcon();

        System.out.println("Hello World!1");

        System.out.println("Hello World!2");

        if (args != null && args.length > 0) {
            for (int i=0; i<args.length; i++)
                System.out.println("args[" + i + "]: '" + args[i] + "' " + args[i].length());
        }

        if (args.length > 0) {
            if (args[0].equals("1")) {
            System.out.println("launch RT");
            RTServerService rts = new RTServerService();
            } else {
                System.out.println("skip RT: arg[0] = " + args[0]);
            }
            System.out.println("launch SC");
            ScrubberService scs = new ScrubberService(args);
        } else {
            //case no params
            System.out.println("launch RT");
            RTServerService rts = new RTServerService();

            System.out.println("launch SC");
            ScrubberService scs = new ScrubberService(args);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Windows-only: jpackage's launcher is a Windows-subsystem .exe that has no
     * console attached, so System.out / System.err are silently discarded. Mirror
     * stdout+stderr into a file in TEMP so first-run failures are diagnosable
     * without rebuilding with --win-console.
     *
     * No-op on Mac — macOS launchd captures stdout/stderr from .app bundles
     * (visible via Console.app), and redirecting would break ./run.sh terminal
     * output for DEV use.
     */
    private static void redirectStdioToFile() {
        if (!isWindows()) return;
        try {
            String tempDir = System.getenv("TEMP");
            if (tempDir == null) tempDir = System.getProperty("java.io.tmpdir");
            File logFile = new File(tempDir, "alt-core-jvm.log");
            java.io.PrintStream ps = new java.io.PrintStream(
                    new java.io.FileOutputStream(logFile, true), true);
            System.setOut(ps);
            System.setErr(ps);
            System.out.println("=== alt-core JVM started " + new java.util.Date() + " ===");
        } catch (Exception ignored) {
            // Already silent without a console — keep going.
        }
    }

    private static String getAppDataDir() {
        if (isWindows()) {
            String appdata = System.getenv("APPDATA");
            if (appdata == null) {
                appdata = System.getProperty("user.home") + "\\AppData\\Roaming";
            }
            return appdata + File.separator + "hivebot";
        }
        return System.getProperty("user.home") + "/Library/Application Support/hivebot";
    }

    /**
     * First-run bootstrap: on packaged PROD installs, if the appendage
     * directory doesn't have scrubber/ populated yet, run the bundled
     * install_mac.sh / install_win.bat to copy scrubber/, rtserver/, web/
     * (and ffmpeg on Mac) from the app bundle into the platform's app-data
     * dir (~/Library/Application Support/hivebot/ on Mac, %APPDATA%\hivebot\
     * on Windows).
     *
     * No-op in DEV (not running from a packaged install) and no-op on
     * subsequent launches (once scrubber/ exists at the appendage path).
     */
    private static void ensureFirstRunSetup() {
        final boolean win = isWindows();

        // Resolve the bundle's app dir. The JAR can be at a nested location inside app/
        // (jpackage with --main-jar scrubber/target/foo.jar preserves the nesting), so we
        // can't just use jarFile.getParentFile(). Use java.home on Windows (jpackage points
        // it at <install>/runtime) and the legacy hardcoded path on Mac.
        File appDir;
        try {
            if (win) {
                File runtimeDir = new File(System.getProperty("java.home"));
                File installRoot = runtimeDir.getParentFile();
                if (installRoot == null) return;
                File launcher = new File(installRoot, "alt-core.exe");
                if (!launcher.isFile()) return;  // DEV — not running from a packaged install
                appDir = new File(installRoot, "app");
            } else {
                // Mac: DEV check via jarPath; hardcode the bundle's app dir as before.
                java.net.URI jarUri = App.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI();
                String jarPath = jarUri.getPath();
                if (jarPath == null || !jarPath.contains(".app/Contents/")) {
                    return;  // DEV
                }
                appDir = new File("/Applications/alt-core.app/Contents/app");
            }
        } catch (Exception e) {
            return;
        }

        // Already initialized?
        File scrubberDir = new File(getAppDataDir(), "scrubber");
        if (scrubberDir.isDirectory()) {
            return;
        }

        String scriptName = win ? "install_win.bat" : "install_mac.sh";
        File installScript = new File(appDir, scriptName);
        if (!installScript.isFile()) {
            System.err.println("first-run: " + scriptName + " not found at "
                    + installScript + " — continuing anyway (server may fail)");
            return;
        }

        System.out.println("first-run: hivebot/ not populated, running " + scriptName);
        try {
            File logFile = win
                    ? new File(System.getenv("TEMP"), "alt-core-first-run.log")
                    : new File("/tmp/alt-core-first-run.log");
            ProcessBuilder pb = win
                    ? new ProcessBuilder("cmd", "/c", installScript.getAbsolutePath())
                    : new ProcessBuilder("/bin/bash", installScript.getAbsolutePath());
            pb.directory(appDir);  // script uses relative paths
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
            Process p = pb.start();
            int rc = p.waitFor();
            System.out.println("first-run: " + scriptName + " exit=" + rc
                    + " (log at " + logFile + ")");
        } catch (Exception e) {
            System.err.println("first-run: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    /**
     * Resolve the install's app/ dir, or null if running in DEV (not from a
     * packaged install). Mac: /Applications/alt-core.app/Contents/app.
     * Windows: <java.home>/../app (jpackage layout).
     */
    private static File resolvePackagedAppDir() {
        try {
            if (isWindows()) {
                File runtimeDir = new File(System.getProperty("java.home"));
                File installRoot = runtimeDir.getParentFile();
                if (installRoot == null) return null;
                File launcher = new File(installRoot, "alt-core.exe");
                if (!launcher.isFile()) return null;
                return new File(installRoot, "app");
            }
            java.net.URI jarUri = App.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            String jarPath = jarUri.getPath();
            if (jarPath == null || !jarPath.contains(".app/Contents/")) return null;
            return new File("/Applications/alt-core.app/Contents/app");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * If the bundled uiv5 dist (under <install>/app/web/) is newer than the
     * APPDATA copy, run update_web.{bat,sh} to refresh APPDATA's copy.
     *
     * Triggered by: user-clicked-MSI updates (which don't auto-run the helper
     * after install), and any other path where the install dir is newer than
     * APPDATA. The helper scripts (update_web.{bat,sh}) handle the actual
     * swap-and-backup; ensureWebFresh just decides whether to invoke them.
     *
     * Version source-of-truth: <install>/app/update.last. APPDATA marker:
     * <APPDATA>/hivebot/.web-version, written by install_*.* and update_web.*
     * helpers after they finish. No-op in DEV (resolvePackagedAppDir → null).
     */
    private static void ensureWebFresh() {
        File appDir = resolvePackagedAppDir();
        if (appDir == null) return;

        File bundledVersionFile = new File(appDir, "update.last");
        String bundledVersion = readFileTrimmed(bundledVersionFile);
        if (bundledVersion == null || bundledVersion.isEmpty()) return;

        File appdataVersionFile = new File(getAppDataDir(), ".web-version");
        String appdataVersion = readFileTrimmed(appdataVersionFile);

        if (appdataVersion != null && appdataVersion.compareTo(bundledVersion) >= 0) {
            return;  // in sync (or APPDATA is somehow newer — leave alone)
        }

        String scriptName = isWindows() ? "update_web.bat" : "update_web.sh";
        File script = new File(appDir, scriptName);
        if (!script.isFile()) {
            System.err.println("ensureWebFresh: " + scriptName + " not found at " + script);
            return;
        }

        System.out.println("ensureWebFresh: bundled=" + bundledVersion
                + " appdata=" + appdataVersion + " — running " + scriptName);
        try {
            File logFile = isWindows()
                    ? new File(System.getenv("TEMP"), "alt-core-web-refresh.log")
                    : new File("/tmp/alt-core-web-refresh.log");
            ProcessBuilder pb = isWindows()
                    ? new ProcessBuilder("cmd", "/c", script.getAbsolutePath())
                    : new ProcessBuilder("/bin/bash", script.getAbsolutePath());
            pb.directory(appDir);  // script uses ./web/cass/uiv5/dist relative path
            if (isWindows()) {
                pb.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("NUL")));
            }
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
            Process p = pb.start();
            int rc = p.waitFor();
            System.out.println("ensureWebFresh: " + scriptName + " exit=" + rc
                    + " (log at " + logFile + ")");
        } catch (Exception e) {
            System.err.println("ensureWebFresh: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static String readFileTrimmed(File f) {
        try {
            return java.nio.file.Files.readString(f.toPath()).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Windows updater entrypoint: invoked as "alt-core-updater.exe --update-from <msi>".
     * Runs elevated via the requireAdministrator manifest baked into
     * alt-core-updater.exe at build time.
     *
     * Sequence:
     *   1. Write a self-contained install bat to %TEMP%\alt-core-elevated-install.bat
     *      (timeout 8s + msiexec /i + update_web.bat + relaunch via explorer + cleanup)
     *   2. Register the bat as a one-time scheduled task via `schtasks /create /rl HIGHEST`
     *   3. Trigger it immediately with `schtasks /run`, then exit the JVM
     *
     * Why schtasks instead of `cmd /c bat` direct? Empirically, any cmd.exe
     * child of our elevated JVM dies the moment our JVM exits — likely a Job
     * Object kill-on-close inherited from the jpackage launcher. Task Scheduler
     * hosts the bat under its own service (svchost), completely outside our
     * process tree, so the bat survives the JVM exit and runs to completion.
     *
     * Logs to %TEMP%\alt-core-updater.log (Java side) and the same file from
     * the bat (so both halves of the flow show up in one log). msiexec's
     * verbose log is at %TEMP%\alt-core-updater-msi.log.
     */
    private static void runUpdate(String msiPath) {
        File logFile = new File(System.getenv("TEMP"), "alt-core-updater.log");
        try {
            java.io.PrintStream ps = new java.io.PrintStream(
                    new java.io.FileOutputStream(logFile, true), true);
            System.setOut(ps);
            System.setErr(ps);
        } catch (Exception ignored) {}

        System.out.println();
        System.out.println("=== alt-core-updater started " + new java.util.Date() + " ===");
        System.out.println("msiPath: " + msiPath);

        // Strategy: write a self-contained .bat (sleep + msiexec + relaunch +
        // cleanup) and spawn it as a detached cmd.exe child, then exit this JVM
        // immediately. The bat runs in its own process and survives even if
        // our elevated updater JVM is somehow killed when alt-core.exe dies.
        // (Empirically the JVM was dying around 5s in — root cause unclear,
        // possibly a Job Object link between jpackage launchers and AIS-
        // spawned elevated children. Decoupling via a shell script side-steps it.)
        try {
            File installRoot = new File(System.getProperty("java.home")).getParentFile();
            File launcher = new File(installRoot, "alt-core.exe");
            File appDir = new File(installRoot, "app");
            File updateWebBat = new File(appDir, "update_web.bat");
            File msiLog = new File(System.getenv("TEMP"), "alt-core-updater-msi.log");
            File lockFile = new File(System.getenv("APPDATA"), "hivebot\\updates\\.install-in-progress");
            File batFile = new File(System.getenv("TEMP"), "alt-core-elevated-install.bat");
            String CRLF = "\r\n";

            StringBuilder bat = new StringBuilder();
            bat.append("@echo off").append(CRLF);
            bat.append("set LOG=").append(logFile.getAbsolutePath()).append(CRLF);
            bat.append(">> \"%LOG%\" echo.").append(CRLF);
            bat.append(">> \"%LOG%\" echo === bat started %date% %time%").append(CRLF);
            bat.append("REM 8s gives alt-core's System.exit(0) + 5s halt-killer plenty of time.").append(CRLF);
            bat.append("timeout /t 8 /nobreak >nul").append(CRLF);
            bat.append(">> \"%LOG%\" echo running msiexec /i ").append(msiPath).append(CRLF);
            bat.append("msiexec.exe /i \"").append(msiPath).append("\" /qb /norestart /l*v \"")
                    .append(msiLog.getAbsolutePath()).append("\"").append(CRLF);
            bat.append("set RC=%errorlevel%").append(CRLF);
            bat.append(">> \"%LOG%\" echo msiexec exit=%RC%").append(CRLF);
            bat.append("if not %RC%==0 (").append(CRLF);
            bat.append("    >> \"%LOG%\" echo msiexec failed; aborting").append(CRLF);
            bat.append("    del \"").append(lockFile.getAbsolutePath()).append("\" >nul 2>&1").append(CRLF);
            bat.append("    exit /b %RC%").append(CRLF);
            bat.append(")").append(CRLF);
            bat.append("if exist \"").append(updateWebBat.getAbsolutePath()).append("\" (").append(CRLF);
            bat.append("    >> \"%LOG%\" echo running update_web.bat").append(CRLF);
            bat.append("    pushd \"").append(appDir.getAbsolutePath()).append("\"").append(CRLF);
            bat.append("    call \"").append(updateWebBat.getAbsolutePath())
                    .append("\" >> \"%LOG%\" 2>&1").append(CRLF);
            bat.append("    popd").append(CRLF);
            bat.append(")").append(CRLF);
            bat.append(">> \"%LOG%\" echo relaunching alt-core via explorer.exe").append(CRLF);
            bat.append("REM Explorer launches as user-level so alt-core drops elevation.").append(CRLF);
            bat.append("start \"\" explorer.exe \"").append(launcher.getAbsolutePath()).append("\"").append(CRLF);
            bat.append("del \"").append(lockFile.getAbsolutePath()).append("\" >nul 2>&1").append(CRLF);
            bat.append(">> \"%LOG%\" echo === bat complete %date% %time%").append(CRLF);

            // Append a final line so the bat removes its own scheduled task.
            String taskName = "alt-core-update-" + System.currentTimeMillis();
            bat.append("schtasks /delete /tn \"").append(taskName).append("\" /f >nul 2>&1").append(CRLF);

            try (java.io.FileWriter w = new java.io.FileWriter(batFile)) {
                w.write(bat.toString());
            }
            System.out.println("wrote install bat: " + batFile);

            // Use Task Scheduler to run the bat. This is the reliable way to
            // escape any Job Object linkage on Windows — schtasks-launched
            // processes live under the Scheduler service, not in our process
            // tree, so they survive our JVM exit. ProcessBuilder("cmd /c bat")
            // empirically dies mid-timeout because cmd is a child of our JVM
            // and inherits whatever job kills when we exit.
            //
            // schtasks /st minimum granularity is 1 minute, so we schedule a
            // task at 23:59 (placeholder) and immediately /run it.
            System.out.println("creating scheduled task: " + taskName);
            Process pCreate = new ProcessBuilder("schtasks",
                    "/create", "/tn", taskName,
                    "/tr", batFile.getAbsolutePath(),
                    "/sc", "once", "/st", "23:59",
                    "/f", "/rl", "HIGHEST")
                    .redirectErrorStream(true)
                    .start();
            String createOut = new String(pCreate.getInputStream().readAllBytes()).trim();
            int createRc = pCreate.waitFor();
            System.out.println("  schtasks /create exit=" + createRc + " out=[" + createOut + "]");
            if (createRc != 0) {
                System.err.println("  failed to create task; aborting");
                return;
            }

            System.out.println("running scheduled task...");
            Process pRun = new ProcessBuilder("schtasks", "/run", "/tn", taskName)
                    .redirectErrorStream(true)
                    .start();
            String runOut = new String(pRun.getInputStream().readAllBytes()).trim();
            int runRc = pRun.waitFor();
            System.out.println("  schtasks /run exit=" + runRc + " out=[" + runOut + "]");
            System.out.println("install task launched. updater exiting.");
        } catch (Exception e) {
            System.err.println("alt-core-updater failed to spawn install bat: " + e);
            e.printStackTrace();
        }
    }

    private static void installTrayIcon() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("tray: headless environment — skipping");
            return;
        }
        if (!SystemTray.isSupported()) {
            System.out.println("tray: SystemTray not supported — skipping");
            return;
        }
        try {
            Image icon = Toolkit.getDefaultToolkit().getImage(
                    App.class.getResource("/icon-tray.png"));

            trayMenu = new PopupMenu();

            versionItem = new MenuItem("alt-core v" + currentVersion);
            versionItem.setEnabled(false);

            MenuItem openItem = new MenuItem("Open alt-core");
            openItem.addActionListener(e -> openBrowser());

            MenuItem logsItem = new MenuItem("View logs");
            logsItem.addActionListener(e -> openLogsFolder());

            MenuItem checkUpdatesItem = new MenuItem("Check for updates");
            checkUpdatesItem.addActionListener(e -> checkForUpdates(true));

            MenuItem quitItem = new MenuItem("Quit alt-core");
            quitItem.addActionListener(e -> quitApp());

            trayMenu.add(versionItem);
            trayMenu.addSeparator();
            trayMenu.add(openItem);
            trayMenu.add(logsItem);
            trayMenu.addSeparator();
            trayMenu.add(checkUpdatesItem);
            trayMenu.addSeparator();
            trayMenu.add(quitItem);

            trayIcon = new TrayIcon(icon, "alt-core", trayMenu);
            trayIcon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(trayIcon);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (trayIcon != null) {
                    SystemTray.getSystemTray().remove(trayIcon);
                }
            }, "tray-cleanup"));

            System.out.println("tray: installed");

            startVersionPoller();
        } catch (Exception e) {
            System.err.println("tray: install failed — " + e.getMessage());
        }
    }

    // Polls getsession.fn until the server returns a usable version string,
    // then updates the tray menu item in place. Runs on a daemon thread so
    // it doesn't block JVM exit. Caps at ~1h of polling — first-run bootstrap
    // on Windows can take 30-45s before the server even starts, which the
    // original 60-iteration cap couldn't survive on slower machines.
    private static void startVersionPoller() {
        Thread t = new Thread(() -> {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            int port = resolveServerPort();
            String url = "http://localhost:" + port + "/cass/getsession.fn";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            for (int i = 0; i < 3600; i++) {
                try {
                    HttpResponse<String> resp = client.send(
                            req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        Matcher m = VERSION_RE.matcher(resp.body());
                        if (m.find()) {
                            String v = m.group(1);
                            if (!v.isEmpty() && !"??".equals(v)) {
                                currentVersion = v;
                                if (versionItem != null) {
                                    versionItem.setLabel("alt-core v" + v);
                                }
                                if (trayIcon != null) {
                                    trayIcon.setToolTip("alt-core v" + v);
                                }
                                System.out.println("tray: version " + v);
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Server not ready yet — retry
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            System.err.println("tray: version poll timed out");
        }, "tray-version-poller");
        t.setDaemon(true);
        t.start();
    }

    private static void quitApp() {
        // WebServer's shutdown hook installs a 5s timeout-halt that catches
        // any blocking non-daemon thread (Netty, RT workers, Scanner, etc.),
        // so we just trigger exit and let the hook guarantee termination.
        System.out.println("tray: quit requested");
        new Thread(() -> System.exit(0), "tray-quit-trigger").start();
    }

    /**
     * Fetch the manifest and surface update state in the tray.
     *
     * @param manual true if triggered by "Check for updates" menu click,
     *               false for background checks. Per spec decision #7,
     *               manual checks show a banner either way; automatic
     *               checks stay silent when up-to-date.
     */
    private static void checkForUpdates(boolean manual) {
        Thread t = new Thread(() -> {
            UpdateManager.ManifestInfo info = UpdateManager.fetchManifest();
            if (info == null) {
                if (manual && trayIcon != null) {
                    trayIcon.displayMessage("alt-core",
                            "Update check failed — please try again later",
                            TrayIcon.MessageType.WARNING);
                }
                return;
            }
            if (UpdateManager.isNewer(info.version, currentVersion)) {
                latestManifest = info;
                showUpdateAvailable(info.version);
                if (trayIcon != null) {
                    trayIcon.displayMessage("alt-core",
                            "Version " + info.version + " is available — "
                                    + "click the tray icon to install",
                            TrayIcon.MessageType.INFO);
                }
            } else if (manual && trayIcon != null) {
                trayIcon.displayMessage("alt-core",
                        "alt-core v" + currentVersion + " — up to date",
                        TrayIcon.MessageType.INFO);
            }
        }, "tray-update-check");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Add (or update) "Update available: vX →" and "Update now" items in the
     * tray menu. If they already exist, just update labels.
     */
    private static void showUpdateAvailable(String newVersion) {
        if (trayMenu == null) return;
        if (updateAvailableItem == null) {
            updateAvailableItem = new MenuItem("Update available: v" + newVersion + " →");
            updateAvailableItem.addActionListener(e -> openReleaseNotes());
            updateNowItem = new MenuItem("Update now");
            updateNowItem.addActionListener(e -> installUpdate());
            // Insert right after the version item (index 0) + separator (index 1)
            trayMenu.insert(updateAvailableItem, 2);
            trayMenu.insert(updateNowItem, 3);
        } else {
            updateAvailableItem.setLabel("Update available: v" + newVersion + " →");
        }
        if (updateNowItem != null) {
            updateNowItem.setEnabled(true);
        }
    }

    /**
     * Trigger the install flow on a background thread. Disables the menu
     * item while running so double-clicks are debounced.
     */
    private static void installUpdate() {
        UpdateManager.ManifestInfo info = latestManifest;
        if (info == null) {
            if (trayIcon != null) {
                trayIcon.displayMessage("alt-core",
                        "No update available — please check again",
                        TrayIcon.MessageType.WARNING);
            }
            return;
        }
        if (updateNowItem != null) updateNowItem.setEnabled(false);

        Installer.Callback cb = new Installer.Callback() {
            @Override public void onProgress(String message) {
                if (trayIcon != null) {
                    trayIcon.displayMessage("alt-core", message, TrayIcon.MessageType.INFO);
                }
                System.out.println("install: " + message);
            }
            @Override public void onFailure(String message) {
                if (trayIcon != null) {
                    trayIcon.displayMessage("alt-core", message, TrayIcon.MessageType.ERROR);
                }
                System.err.println("install: " + message);
                if (updateNowItem != null) updateNowItem.setEnabled(true);
            }
        };

        Thread t = new Thread(() -> {
            Installer.PreparedInstall prep = Installer.prepare(info, cb, true);
            if (prep == null) return;  // failure already reported via callback
            if (prep.dryRun) {
                // DEV: unzip + verify passed, but no /Applications/ to replace
                if (updateNowItem != null) updateNowItem.setEnabled(true);
                return;
            }
            // PROD: hand off to detached helper, then exit
            if (!Installer.launchHelper(prep, cb)) {
                if (updateNowItem != null) updateNowItem.setEnabled(true);
                return;
            }
            // Give the banner a moment to render, then exit so the helper
            // (which is sleep-3ing) can take over.
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            System.exit(0);
        }, "tray-installer");
        t.setDaemon(true);
        t.start();
    }

    private static void openReleaseNotes() {
        UpdateManager.ManifestInfo info = latestManifest;
        if (info == null || info.releaseNotesUrl == null) return;
        try {
            Desktop.getDesktop().browse(new URI(info.releaseNotesUrl));
        } catch (Exception ex) {
            System.err.println("tray: release notes open failed — " + ex.getMessage());
        }
    }

    private static int resolveServerPort() {
        String[] candidates = {
            "scrubber/config/www-server.properties",
            "../scrubber/config/www-server.properties",
            getAppDataDir() + "/scrubber/config/www-server.properties"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (!f.isFile()) continue;
            try (FileInputStream in = new FileInputStream(f)) {
                Properties p = new Properties();
                p.load(in);
                String port = p.getProperty("port");
                if (port != null) return Integer.parseInt(port.trim());
            } catch (Exception ignored) {
            }
        }
        return 8081;
    }

    private static void openBrowser() {
        int port = resolveServerPort();
        String url = "http://localhost:" + port + "/cass/uiv5/dist/i.html";
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            System.err.println("tray: open failed — " + ex.getMessage());
            if (trayIcon != null) {
                trayIcon.displayMessage("alt-core",
                        "Open " + url + " manually", TrayIcon.MessageType.INFO);
            }
        }
    }

    private static void openLogsFolder() {
        String[] candidates = {
            getAppDataDir() + "/scrubber/logs",
            "scrubber/logs",
            "../scrubber/logs"
        };
        for (String path : candidates) {
            File dir = new File(path);
            if (dir.isDirectory()) {
                try {
                    Desktop.getDesktop().open(dir);
                    return;
                } catch (Exception ex) {
                    System.err.println("tray: open logs failed — " + ex.getMessage());
                }
            }
        }
        System.err.println("tray: logs folder not found");
    }
}

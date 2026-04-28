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
        ensureFirstRunSetup();
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

    /**
     * First-run bootstrap: on packaged PROD installs, if the appendage
     * directory doesn't have scrubber/ populated yet, run the bundled
     * install_mac.sh to copy scrubber/, rtserver/, web/, and ffmpeg from
     * the app bundle to ~/Library/Application Support/hivebot/.
     *
     * No-op in DEV (not running from a .app) and no-op on subsequent launches
     * (once scrubber/ exists at the appendage path).
     */
    private static void ensureFirstRunSetup() {
        // DEV check: skip if we're not running from a packaged .app
        try {
            java.net.URI jarUri = App.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            String jarPath = jarUri.getPath();
            if (jarPath == null || !jarPath.contains(".app/Contents/")) {
                return;  // DEV — nothing to set up
            }
        } catch (Exception e) {
            return;  // can't determine — play it safe, skip
        }

        // Already initialized?
        File scrubberDir = new File(System.getProperty("user.home"),
                "Library/Application Support/hivebot/scrubber");
        if (scrubberDir.isDirectory()) {
            return;
        }

        System.out.println("first-run: hivebot/ not populated, running install_mac.sh");
        File contentsApp = new File("/Applications/alt-core.app/Contents/app");
        File installScript = new File(contentsApp, "install_mac.sh");
        if (!installScript.isFile()) {
            System.err.println("first-run: install_mac.sh not found at "
                    + installScript + " — continuing anyway (server may fail)");
            return;
        }
        try {
            File logFile = new File("/tmp/alt-core-first-run.log");
            ProcessBuilder pb = new ProcessBuilder(
                    "/bin/bash", installScript.getAbsolutePath());
            pb.directory(contentsApp);  // script uses relative paths
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
            Process p = pb.start();
            int rc = p.waitFor();
            System.out.println("first-run: install_mac.sh exit=" + rc
                    + " (log at " + logFile + ")");
        } catch (Exception e) {
            System.err.println("first-run: install_mac.sh failed — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
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
    // it doesn't block JVM exit. Gives up after 60 tries (~60s).
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

            for (int i = 0; i < 60; i++) {
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
            System.getProperty("user.home") + "/Library/Application Support/hivebot/scrubber/config/www-server.properties"
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
            System.getProperty("user.home") + "/Library/Application Support/hivebot/scrubber/logs",
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

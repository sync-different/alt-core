package utils;

import java.io.File;

public class Appendage {

    public static String appendage = "";
    public static String appendageRW = "";
    public static boolean firstTime = true;
    public Appendage() {
        if (firstTime) {
            firstTime = false;
            setAppendage();
        }
    }
    
    public String getAppendage() {
        return appendage;
    }
    public String getAppendageRW() {
        return appendageRW;
    }

    static void p(String s) {
        System.out.println(s);
    }

    static void setAppendage() {
        boolean result = false;
        String userHome = System.getProperty("user.home");
        String appSupportPath = userHome + "/Library/Application Support/hivebot/scrubber/";
        String osName = System.getProperty("os.name").toLowerCase();

        // Windows packaged install: jpackage layout has <install>\runtime\, <install>\app\,
        // and <install>\alt-core.exe. DEV (running from build-uber output) doesn't.
        if (osName.contains("win")) {
            File runtimeDir = new File(System.getProperty("java.home"));
            File installRoot = runtimeDir.getParentFile();
            if (installRoot != null
                    && new File(installRoot, "app").isDirectory()
                    && new File(installRoot, "alt-core.exe").isFile()) {
                String appdata = System.getenv("APPDATA");
                if (appdata == null) {
                    appdata = userHome + "\\AppData\\Roaming";
                }
                String winAppendage = appdata.replace('\\', '/') + "/hivebot/scrubber/";
                p("[CS.Appendage] Found Windows packaged install. Using APPDATA for data.");
                result = (System.setProperty("user.dir", installRoot.getAbsolutePath()) != null);
                appendage = winAppendage;
                appendageRW = winAppendage;
                p("appendage  = " + appendage);
                p("appendageRW  = " + appendageRW);
            } else {
                p("[CS.Appendage] Windows DEV mode (no packaged install detected).");
            }
            return;
        }

        File directory = new File("/Applications/hivebot.localized/hivebot.app/Contents/MacOS").getAbsoluteFile();
        if (directory.exists())
        {
            p("[CS.Localfuncs] Found hivebotlocalized app directory. Using Application Support for data.");
            result = (System.setProperty("user.dir", directory.getAbsolutePath()) != null);
            appendage = appSupportPath;
            appendageRW = appSupportPath;
            p("appendage  = " + appendage);
            p("appendageRW  = " + appendageRW);
        } else {
            directory = new File("/Applications/alt-core.app/Contents/MacOS").getAbsoluteFile();
            if (directory.exists())
            {
                p("[CS.Localfuncs] Found alt-core app directory. Using Application Support for data.");
                result = (System.setProperty("user.dir", directory.getAbsolutePath()) != null);
                appendage = appSupportPath;
                appendageRW = appSupportPath;
                p("appendage  = " + appendage);
                p("appendageRW  = " + appendageRW);
            } else {
                p("[CS.Cass7Funcs] alt-core app directory not found.");
            }
        }
    }

      void setAppendage_ori() {
        boolean result = false;
        File directory = new File("/Applications/Alterante.app/Contents/AlteranteJava.app/Contents/MacOS").getAbsoluteFile();
        //File directory = new File("../app/projects/rtserver").getAbsoluteFile();
        if (directory.exists())
        {
            p("[loadfuncs] Found app directory. Setting working dir to it");
            result = (System.setProperty("user.dir", directory.getAbsolutePath()) != null);
            
            appendage = "/Applications/Alterante.app/Contents/AlteranteJava.app/Contents/app/projects/rtserver/";
            p("appendage  = " + appendage);
            //appendage = "../app/projects/rtserver/";        
        }
        
        String username = System.getProperty("user.name");
        p("username: " + username);
        File directoryRW = new File("/Users/" + username + "/Library/Containers/com.alterante.desktopapp1j");
        if (directoryRW.exists()) {
            p("[Cass7Funcs] Found container directory. checking folders.");
            appendageRW = "/Users/" + username + "/Library/Containers/com.alterante.desktopapp1j/Data/app/projects/rtserver/";
            File dir = new File("/Users" + username + "/Library/Containers/com.alterante.desktopapp1j/Data/app/projects/rtserver");
            if (dir.exists()) {
                p("appendageRW rtserver exists.");
            } else {
                boolean res = new File(appendageRW).mkdirs();
                p("appendageRW rtserver create = " + res);
                res = new File(appendageRW + "/logs/").mkdirs();
                p("appendageRW rtserver create logs = " + res);
            }               
        } else {
            p("[Cass7Funcs] Container directory not found.");
        }        
    }    
}

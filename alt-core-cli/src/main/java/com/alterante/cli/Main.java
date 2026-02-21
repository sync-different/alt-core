package com.alterante.cli;

import com.alterante.cli.commands.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    private static final Map<String, String> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("config",       "Configure server connection (protocol, server, port)");
        COMMANDS.put("login",        "Login to server: login <user> <pass>");
        COMMANDS.put("logout",       "Logout and clear session");
        COMMANDS.put("raw",          "Raw GET request: raw --path /cass/endpoint.fn?params");
        COMMANDS.put("query",        "Search files: query --ftype .all --foo test --days 7");
        COMMANDS.put("sidebar",      "File type counts: sidebar --ftype .all --days 0");
        COMMANDS.put("suggest",      "Search suggestions: suggest --foo test");
        COMMANDS.put("fileinfo",     "File metadata: fileinfo --md5 <hash>");
        COMMANDS.put("getfile",      "Download file: getfile --md5 <hash> --filename <name>");
        COMMANDS.put("downloadmulti","Download multiple as ZIP: downloadmulti --md5 h1,h2,h3");
        COMMANDS.put("tags",         "List tags: tags [--md5 <hash>]");
        COMMANDS.put("addtag",       "Add tag: addtag --tag <name> --md5 <hash>");
        COMMANDS.put("removetag",    "Remove tag: removetag --tag <name> --md5 <hash>");
        COMMANDS.put("folders",      "List folders: folders [--folder scanfolders]");
        COMMANDS.put("folderperm",   "Get folder permissions: folderperm --folder <path>");
        COMMANDS.put("setfolderperm","Set folder permissions: setfolderperm --folder <path> --perms <json>");
        COMMANDS.put("chat",         "Chat: chat pull [--md5 X] | chat push --msg X | chat clear");
        COMMANDS.put("share",        "Shares: share list | share create | share remove | share settings");
        COMMANDS.put("nodeinfo",     "Device/node information");
        COMMANDS.put("serverprop",   "Server property: serverprop --property <name>");
        COMMANDS.put("users",        "Users: users list | users add --user X --pass Y");
        COMMANDS.put("cluster",      "Cluster information");
        COMMANDS.put("transcript",   "Video transcript: transcript --md5 <hash>");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        // Check for --json flag (global)
        OutputFormatter.setJsonMode(hasFlag(args, "json"));

        String command = args[0].toLowerCase();
        String[] commandArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        HttpSession session = new HttpSession();

        try {
            switch (command) {
                case "config"        -> new ConfigCommand().run(session, commandArgs);
                case "login"         -> new LoginCommand().run(session, commandArgs);
                case "logout"        -> new LogoutCommand().run(session, commandArgs);
                case "raw"           -> new RawCommand().run(session, commandArgs);
                case "query"         -> new QueryCommand().run(session, commandArgs);
                case "sidebar"       -> new SidebarCommand().run(session, commandArgs);
                case "suggest"       -> new SuggestCommand().run(session, commandArgs);
                case "fileinfo"      -> new GetFileInfoCommand().run(session, commandArgs);
                case "getfile"       -> new GetFileCommand().run(session, commandArgs);
                case "downloadmulti" -> new DownloadMultiCommand().run(session, commandArgs);
                case "tags"          -> new TagsCommand().run(session, commandArgs);
                case "addtag"        -> new TagsCommand().runAdd(session, commandArgs);
                case "removetag"     -> new TagsCommand().runRemove(session, commandArgs);
                case "folders"       -> new FoldersCommand().run(session, commandArgs);
                case "folderperm"    -> new FolderPermCommand().run(session, commandArgs);
                case "setfolderperm" -> new FolderPermCommand().runSet(session, commandArgs);
                case "chat"          -> new ChatCommand().run(session, commandArgs);
                case "share"         -> new ShareCommand().run(session, commandArgs);
                case "nodeinfo"      -> new NodeInfoCommand().run(session, commandArgs);
                case "serverprop"    -> new ServerPropertyCommand().run(session, commandArgs);
                case "users"         -> new UsersCommand().run(session, commandArgs);
                case "cluster"       -> new ClusterCommand().run(session, commandArgs);
                case "transcript"    -> new TranscriptCommand().run(session, commandArgs);
                case "version"       -> printVersion();
                case "help"          -> printUsage();
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    public static String getVersion() {
        String version = Main.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    private static void printVersion() {
        System.out.println("alt-core-cli " + getVersion());
    }

    private static void printUsage() {
        System.out.println("alt-core-cli " + getVersion() + " - Alterante REST API Client");
        System.out.println();
        System.out.println("Usage: alt <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        for (var entry : COMMANDS.entrySet()) {
            System.out.printf("  %-16s %s%n", entry.getKey(), entry.getValue());
        }
        System.out.println();
        System.out.println("Global options:");
        System.out.println("  --noauth         Skip authentication cookie");
        System.out.println("  --json           Output raw JSON instead of formatted text");
        System.out.println("  version          Show version");
        System.out.println("  help             Show this help message");
    }

    /**
     * Parse named arguments from command args. Returns value for --name or null.
     */
    public static String getArg(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--" + name)) {
                return args[i + 1];
            }
        }
        return null;
    }

    /**
     * Check if a flag is present (e.g., --noauth).
     */
    public static boolean hasFlag(String[] args, String name) {
        for (String arg : args) {
            if (arg.equals("--" + name)) return true;
        }
        return false;
    }
}

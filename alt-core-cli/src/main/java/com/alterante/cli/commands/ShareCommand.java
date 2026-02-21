package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class ShareCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: share list");
            System.err.println("       share create --tag <tag> --user <user> [--perms rw]");
            System.err.println("       share remove --id <shareId>");
            System.err.println("       share settings --id <shareId>");
            System.err.println("       share invite --tag <tag> --user <email>");
            System.exit(1);
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "list"     -> runList(session, args);
            case "create"   -> runCreate(session, args);
            case "remove"   -> runRemove(session, args);
            case "settings" -> runSettings(session, args);
            case "invite"   -> runInvite(session, args);
            default -> {
                System.err.println("Unknown share subcommand: " + subcommand);
                System.exit(1);
            }
        }
    }

    private void runList(HttpSession session, String[] args) throws Exception {
        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get("/cass/refreshsharetable.fn?view=json", !noAuth);
        OutputFormatter.print(response);
    }

    private void runCreate(HttpSession session, String[] args) throws Exception {
        String tag = Main.getArg(args, "tag");
        String user = Main.getArg(args, "user");

        if (tag == null || user == null) {
            System.err.println("Usage: share create --tag <tag> --user <user> [--perms rw]");
            System.exit(1);
        }

        String perms = Main.getArg(args, "perms");

        StringBuilder path = new StringBuilder("/cass/doshare_webapp.fn?tag=" + HttpSession.encode(tag)
                + "&user=" + HttpSession.encode(user));
        if (perms != null) path.append("&perms=").append(HttpSession.encode(perms));

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path.toString(), !noAuth);
        OutputFormatter.print(response);
    }

    private void runRemove(HttpSession session, String[] args) throws Exception {
        String id = Main.getArg(args, "id");
        if (id == null) {
            System.err.println("Usage: share remove --id <shareId>");
            System.exit(1);
        }

        String path = "/cass/removeshare.fn?share_id=" + HttpSession.encode(id);

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }

    private void runSettings(HttpSession session, String[] args) throws Exception {
        String id = Main.getArg(args, "id");
        if (id == null) {
            System.err.println("Usage: share settings --id <shareId>");
            System.exit(1);
        }

        String path = "/cass/getsharesettingsmodal.fn?share_id=" + HttpSession.encode(id);

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }

    private void runInvite(HttpSession session, String[] args) throws Exception {
        String tag = Main.getArg(args, "tag");
        String user = Main.getArg(args, "user");

        if (tag == null || user == null) {
            System.err.println("Usage: share invite --tag <tag> --user <email>");
            System.exit(1);
        }

        String path = "/cass/invitation_webapp.fn?tag=" + HttpSession.encode(tag)
                     + "&user=" + HttpSession.encode(user);

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }
}

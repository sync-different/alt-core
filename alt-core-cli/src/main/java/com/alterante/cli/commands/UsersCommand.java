package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class UsersCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: users list");
            System.err.println("       users add --user <username> --pass <password>");
            System.exit(1);
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "list" -> runList(session, args);
            case "add"  -> runAdd(session, args);
            default -> {
                System.err.println("Unknown users subcommand: " + subcommand);
                System.exit(1);
            }
        }
    }

    private void runList(HttpSession session, String[] args) throws Exception {
        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get("/cass/getusersandemail.fn?view=json", !noAuth);
        OutputFormatter.print(response);
    }

    private void runAdd(HttpSession session, String[] args) throws Exception {
        String user = Main.getArg(args, "user");
        String pass = Main.getArg(args, "pass");

        if (user == null || pass == null) {
            System.err.println("Usage: users add --user <username> --pass <password>");
            System.exit(1);
        }

        String path = "/cass/adduser.fn?user=" + HttpSession.encode(user)
                     + "&pass=" + HttpSession.encode(pass);

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }
}

package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;
import java.util.Base64;

public class ChatCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: chat pull [--md5 X] [--from 0]");
            System.err.println("       chat push --msg <text> [--md5 X] [--user admin]");
            System.err.println("       chat clear");
            System.exit(1);
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "pull" -> runPull(session, args);
            case "push" -> runPush(session, args);
            case "clear" -> runClear(session, args);
            default -> {
                System.err.println("Unknown chat subcommand: " + subcommand);
                System.exit(1);
            }
        }
    }

    private void runPull(HttpSession session, String[] args) throws Exception {
        StringBuilder path = new StringBuilder("/cass/chat_pull.fn?view=json");

        String md5 = Main.getArg(args, "md5");
        if (md5 != null) path.append("&md5=").append(HttpSession.encode(md5));
        else path.append("&md5=");

        String from = Main.getArg(args, "from");
        path.append("&msg_from=").append(from != null ? from : "0");

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path.toString(), !noAuth);
        OutputFormatter.print(response);
    }

    private void runPush(HttpSession session, String[] args) throws Exception {
        String msg = Main.getArg(args, "msg");
        if (msg == null) {
            System.err.println("Usage: chat push --msg <text> [--md5 X] [--user admin] [--type CHAT]");
            System.exit(1);
        }

        String msgBase64 = Base64.getEncoder().encodeToString(msg.getBytes());

        StringBuilder path = new StringBuilder("/cass/chat_push.fn?view=json");

        String md5 = Main.getArg(args, "md5");
        if (md5 != null) path.append("&md5=").append(HttpSession.encode(md5));
        else path.append("&md5=");

        String user = Main.getArg(args, "user");
        path.append("&msg_user=").append(HttpSession.encode(user != null ? user : "admin"));

        String type = Main.getArg(args, "type");
        path.append("&msg_type=").append(type != null ? type : "CHAT");

        path.append("&msg_body=").append(HttpSession.encode(msgBase64));
        path.append("&msg_from=0");

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path.toString(), !noAuth);
        OutputFormatter.print(response);
    }

    private void runClear(HttpSession session, String[] args) throws Exception {
        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get("/cass/chat_clear.fn?view=json", !noAuth);
        OutputFormatter.print(response);
    }
}

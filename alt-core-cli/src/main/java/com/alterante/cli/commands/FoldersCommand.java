package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class FoldersCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        // Check if first arg is "open" for openfolder subcommand
        if (args.length > 0 && args[0].equals("open")) {
            runOpen(session, args);
            return;
        }

        StringBuilder path = new StringBuilder("/cass/getfolders-json.fn?view=json");

        String folder = Main.getArg(args, "folder");
        if (folder != null) path.append("&sFolder=").append(HttpSession.encode(folder));

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path.toString(), !noAuth);
        OutputFormatter.print(response);
    }

    private void runOpen(HttpSession session, String[] args) throws Exception {
        String name = Main.getArg(args, "name");
        String md5 = Main.getArg(args, "md5");

        if (name == null) {
            System.err.println("Usage: folders open --name <folderName> [--md5 <hash>]");
            System.exit(1);
        }

        StringBuilder path = new StringBuilder("/cass/openfolder.fn?sFileName=" + HttpSession.encode(name));
        if (md5 != null) path.append("&sNamer=").append(HttpSession.encode(md5));

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path.toString(), !noAuth);
        OutputFormatter.print(response);
    }
}

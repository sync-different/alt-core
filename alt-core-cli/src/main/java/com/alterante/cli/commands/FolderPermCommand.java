package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class FolderPermCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        String folder = Main.getArg(args, "folder");
        if (folder == null) {
            System.err.println("Usage: folderperm --folder <path>");
            System.exit(1);
        }

        String path = "/cass/getfolderperm.fn?sFolder=" + HttpSession.encode(folder);

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }

    public void runSet(HttpSession session, String[] args) throws Exception {
        String folder = Main.getArg(args, "folder");
        String perms = Main.getArg(args, "perms");

        if (folder == null || perms == null) {
            System.err.println("Usage: setfolderperm --folder <path> --perms <json>");
            System.exit(1);
        }

        String path = "/cass/setfolderperm.fn?sFolder=" + HttpSession.encode(folder)
                     + "&permissions=" + HttpSession.encode(perms);

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }
}

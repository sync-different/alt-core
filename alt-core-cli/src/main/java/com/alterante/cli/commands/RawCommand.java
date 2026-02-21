package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class RawCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        String path = Main.getArg(args, "path");
        if (path == null) {
            System.err.println("Usage: raw --path /cass/endpoint.fn?params");
            System.exit(1);
        }

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }
}

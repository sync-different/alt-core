package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class ServerPropertyCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        String property = Main.getArg(args, "property");
        if (property == null) {
            System.err.println("Usage: serverprop --property <name>");
            System.exit(1);
        }

        String path = "/cass/serverproperty.fn?property=" + HttpSession.encode(property);

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }
}

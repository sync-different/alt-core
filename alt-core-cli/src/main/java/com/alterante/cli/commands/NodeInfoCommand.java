package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class NodeInfoCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get("/cass/nodeinfo.fn?view=json", !noAuth);
        OutputFormatter.print(response);
    }
}

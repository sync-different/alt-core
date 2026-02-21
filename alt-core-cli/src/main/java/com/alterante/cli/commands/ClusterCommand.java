package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class ClusterCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get("/cass/getcluster.fn?view=json", !noAuth);
        OutputFormatter.print(response);
    }
}

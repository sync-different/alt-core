package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class SidebarCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        StringBuilder path = new StringBuilder("/cass/sidebar.fn?view=json");

        String ftype = Main.getArg(args, "ftype");
        path.append("&ftype=").append(HttpSession.encode(ftype != null ? ftype : ".all"));

        String foo = Main.getArg(args, "foo");
        path.append("&foo=").append(HttpSession.encode(foo != null ? foo : ".all"));

        String days = Main.getArg(args, "days");
        path.append("&days=").append(days != null ? days : "0");

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path.toString(), !noAuth);
        OutputFormatter.print(response);
    }
}

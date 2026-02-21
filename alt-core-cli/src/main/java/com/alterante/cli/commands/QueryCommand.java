package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class QueryCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        StringBuilder path = new StringBuilder("/cass/query.fn?view=json");

        String ftype = Main.getArg(args, "ftype");
        path.append("&ftype=").append(HttpSession.encode(ftype != null ? ftype : ".all"));

        // Support positional: "query test" == "query --foo test"
        String foo = Main.getArg(args, "foo");
        if (foo == null && args.length > 0 && !args[0].startsWith("--")) {
            foo = args[0];
        }
        path.append("&foo=").append(HttpSession.encode(foo != null ? foo : ".all"));

        String days = Main.getArg(args, "days");
        path.append("&days=").append(days != null ? days : "0");

        String numobj = Main.getArg(args, "numobj");
        path.append("&numobj=").append(numobj != null ? numobj : "10");

        String order = Main.getArg(args, "order");
        if (order != null) path.append("&order=").append(HttpSession.encode(order));

        String date = Main.getArg(args, "date");
        if (date != null) path.append("&date=").append(HttpSession.encode(date));

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path.toString(), !noAuth);
        OutputFormatter.print(response);
    }
}

package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class SuggestCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        StringBuilder path = new StringBuilder("/cass/suggest.fn?view=json");

        String foo = Main.getArg(args, "foo");
        // Support positional: suggest <query>
        if (foo == null && args.length > 0 && !args[0].startsWith("--")) {
            foo = args[0];
        }
        if (foo != null) path.append("&foo=").append(HttpSession.encode(foo));

        String ftype = Main.getArg(args, "ftype");
        if (ftype != null) path.append("&ftype=").append(HttpSession.encode(ftype));

        String days = Main.getArg(args, "days");
        if (days != null) path.append("&days=").append(days);

        String numobj = Main.getArg(args, "numobj");
        if (numobj != null) path.append("&numobj=").append(numobj);

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path.toString(), !noAuth);
        OutputFormatter.printSuggest(response);
    }
}

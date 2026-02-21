package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class TranscriptCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        String md5 = Main.getArg(args, "md5");
        if (md5 == null) {
            System.err.println("Usage: transcript --md5 <hash>");
            System.exit(1);
        }

        String path = "/cass/gettranslate_json.fn?md5=" + HttpSession.encode(md5);

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }
}

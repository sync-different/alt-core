package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.OutputFormatter;
import com.alterante.cli.Main;

import java.net.http.HttpResponse;

public class TagsCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        StringBuilder path = new StringBuilder("/cass/gettags_webapp.fn?view=json");

        String md5 = Main.getArg(args, "md5");
        if (md5 != null) path.append("&multiclusterid=").append(HttpSession.encode(md5));

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path.toString(), !noAuth);
        OutputFormatter.print(response);
    }

    public void runAdd(HttpSession session, String[] args) throws Exception {
        String tag = Main.getArg(args, "tag");
        String md5 = Main.getArg(args, "md5");

        if (tag == null || md5 == null) {
            System.err.println("Usage: addtag --tag <name> --md5 <hash>");
            System.exit(1);
        }

        String path = "/cass/applytags.fn?tag=" + HttpSession.encode(tag)
                     + "&" + HttpSession.encode(md5) + "=on";

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }

    public void runRemove(HttpSession session, String[] args) throws Exception {
        String tag = Main.getArg(args, "tag");
        String md5 = Main.getArg(args, "md5");

        if (tag == null || md5 == null) {
            System.err.println("Usage: removetag --tag <name> --md5 <hash>");
            System.exit(1);
        }

        String path = "/cass/applytags.fn?tag=" + HttpSession.encode(tag)
                     + "&DeleteTag=" + HttpSession.encode(md5);

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<String> response = session.get(path, !noAuth);
        OutputFormatter.print(response);
    }
}

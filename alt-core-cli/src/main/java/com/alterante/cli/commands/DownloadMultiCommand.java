package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.Main;

import java.io.File;
import java.io.FileOutputStream;
import java.net.http.HttpResponse;

public class DownloadMultiCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        String md5 = Main.getArg(args, "md5");
        String output = Main.getArg(args, "output");

        if (md5 == null) {
            System.err.println("Usage: downloadmulti --md5 hash1,hash2,hash3 [--output <path>]");
            System.exit(1);
        }

        StringBuilder path = new StringBuilder("/cass/downloadmulti.fn?multiclusterid=" + HttpSession.encode(md5));
        if (session.getUuid() != null) {
            path.append("&uuid=").append(HttpSession.encode(session.getUuid()));
        }

        boolean noAuth = Main.hasFlag(args, "noauth");
        HttpResponse<byte[]> response = session.getBytes(path.toString(), !noAuth);

        if (output != null) {
            File outFile = new File(output);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(response.body());
            }
            System.out.println("{");
            System.out.println("  \"status\": " + response.statusCode() + ",");
            System.out.println("  \"savedTo\": \"" + outFile.getAbsolutePath() + "\",");
            System.out.println("  \"bytes\": " + response.body().length);
            System.out.println("}");
        } else {
            System.out.println("{");
            System.out.println("  \"status\": " + response.statusCode() + ",");
            System.out.println("  \"bytes\": " + response.body().length + ",");
            System.out.println("  \"contentType\": \"" + response.headers().firstValue("content-type").orElse("unknown") + "\"");
            System.out.println("}");
        }
    }
}

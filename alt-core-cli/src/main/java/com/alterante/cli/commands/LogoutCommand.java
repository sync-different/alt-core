package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;

import java.net.http.HttpResponse;

public class LogoutCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        if (session.getUuid() == null) {
            System.out.println("Not logged in.");
            return;
        }

        // Invalidate session on server
        HttpResponse<String> response = session.get("/cass/logout.fn", true);
        String body = response.body().trim();

        // Clear local session regardless of server response
        String server = session.getBaseUrl();
        session.setUuid(null);
        session.saveConfig();

        if (body.contains("\"ok\"")) {
            System.out.println("Logged out from " + server);
        } else {
            // Session may have already expired server-side
            System.out.println("Session cleared (server: " + body + ")");
        }
    }
}

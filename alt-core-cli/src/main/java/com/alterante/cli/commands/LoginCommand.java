package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.Main;

import java.io.Console;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginCommand {

    public void run(HttpSession session, String[] args) throws Exception {
        // Support both: login <user> <pass> and login --user <user> --pass <pass>
        String user = Main.getArg(args, "user");
        String pass = Main.getArg(args, "pass");

        if (user == null && args.length >= 1 && !args[0].startsWith("--")) {
            user = args[0];
            if (args.length >= 2) {
                pass = args[1];
            }
        }

        // Prompt for password if user is provided but password is missing
        if (user != null && pass == null) {
            Console console = System.console();
            if (console != null) {
                char[] passChars = console.readPassword("Password: ");
                if (passChars != null) {
                    pass = new String(passChars);
                }
            } else {
                // No console (e.g. piped input) — read from stdin
                System.out.print("Password: ");
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
                pass = reader.readLine();
            }
        }

        if (user == null || pass == null) {
            System.err.println("Usage: login <username> [<password>]");
            System.err.println("       login --user <username> --pass <password>");
            System.err.println("If password is omitted, you will be prompted.");
            System.exit(1);
        }

        String path = "/cass/login.fn?boxuser=" + HttpSession.encode(user)
                     + "&boxpass=" + HttpSession.encode(pass);

        HttpResponse<String> response = session.get(path, false);
        String body = response.body();

        // Extract UUID from: <input type='hidden' id='session-uuid' value='UUID'/>
        Pattern pattern = Pattern.compile("id='session-uuid'\\s+value='([^']+)'");
        Matcher matcher = pattern.matcher(body);

        if (matcher.find()) {
            String uuid = matcher.group(1);
            session.setUuid(uuid);
            session.saveConfig();

            System.out.println("{");
            System.out.println("  \"success\": true,");
            System.out.println("  \"uuid\": \"" + uuid + "\",");
            System.out.println("  \"user\": \"" + user + "\",");
            System.out.println("  \"server\": \"" + session.getBaseUrl() + "\"");
            System.out.println("}");
        } else {
            System.out.println("{");
            System.out.println("  \"success\": false,");
            System.out.println("  \"message\": \"Login failed - no UUID found in response\",");
            System.out.println("  \"status\": " + response.statusCode());
            System.out.println("}");
            System.exit(1);
        }
    }
}

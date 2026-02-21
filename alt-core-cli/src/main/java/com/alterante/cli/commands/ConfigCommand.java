package com.alterante.cli.commands;

import com.alterante.cli.HttpSession;
import com.alterante.cli.Main;

public class ConfigCommand {

    public void run(HttpSession session, String[] args) {
        if (Main.hasFlag(args, "help")) {
            System.out.println("Usage: config [options]");
            System.out.println();
            System.out.println("Configure server connection. With no options, shows current config.");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --protocol <http|https>  Set protocol (default: http)");
            System.out.println("  --server <host>          Set server hostname or IP (default: localhost)");
            System.out.println("  --port <number>          Set server port (default: 8081)");
            System.out.println("  --help                   Show this help message");
            System.out.println();
            System.out.println("Examples:");
            System.out.println("  config                                  Show current config");
            System.out.println("  config --server 192.168.1.50            Connect to remote server");
            System.out.println("  config --protocol https --port 443      Use HTTPS");
            System.out.println("  config --server myhost --port 9090      Custom host and port");
            return;
        }

        boolean changed = false;

        String protocol = Main.getArg(args, "protocol");
        if (protocol != null) {
            session.setProtocol(protocol);
            changed = true;
        }

        String server = Main.getArg(args, "server");
        if (server != null) {
            session.setServer(server);
            changed = true;
        }

        String port = Main.getArg(args, "port");
        if (port != null) {
            session.setPort(Integer.parseInt(port));
            changed = true;
        }

        if (changed) {
            session.saveConfig();
        }

        // Always show current config
        System.out.println("{");
        System.out.println("  \"protocol\": \"" + session.getProtocol() + "\",");
        System.out.println("  \"server\": \"" + session.getServer() + "\",");
        System.out.println("  \"port\": " + session.getPort() + ",");
        System.out.println("  \"baseUrl\": \"" + session.getBaseUrl() + "\",");
        System.out.println("  \"uuid\": " + (session.getUuid() != null ? "\"" + session.getUuid() + "\"" : "null"));
        System.out.println("}");
    }
}

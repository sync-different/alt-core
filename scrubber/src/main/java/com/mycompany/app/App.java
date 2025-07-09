package com.mycompany.app;

/**
 * Hello world!
 */


public class App {


    public static void main(String[] args) {
        System.out.println("Hello World!1");

        System.out.println("Hello World!2");

        if (args != null && args.length > 0) {
            for (int i=0; i<args.length; i++) 
                System.out.println("args[" + i + "]: '" + args[i] + "' " + args[i].length());
        }

        if (args.length > 0) {
            if (args[0].equals("1")) {
            System.out.println("launch RT");
            RTServerService rts = new RTServerService();
            } else {
                System.out.println("skip RT: arg[0] = " + args[0]);                
            }
            System.out.println("launch SC");
            ScrubberService scs = new ScrubberService(args); 
        } else {
            //case no params
            System.out.println("launch RT");
            RTServerService rts = new RTServerService();

            System.out.println("launch SC");
            ScrubberService scs = new ScrubberService(args); 
        }
    }
}

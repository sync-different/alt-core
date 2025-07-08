package com.mycompany.app;

import main.Main;

public class ScrubberService implements Runnable {
        
        private Boolean mTerminated = false;
        private String[] arguments;
        private String params = "";

        public ScrubberService(String args[]) {
            arguments = args;
            if (args != null) {
                System.out.println("args len = " + args.length);
                if (args.length > 1) params = args[1]; 
            }
            Thread t;
            t = new Thread(this,"scrubber");
            t.start();
        }

        public void run () {
            while (!mTerminated) {
                Main mymain = new Main();
                System.out.println("scrubber launch flags = " + params);
                String[] myargs;
                if (params.length() > 0) {
                    myargs = new String[]{"data/records.db","localhost","8081","/Users/","../rtserver/incoming","macprd","config/www-rtbackup.properties",params};
                } else {
                    myargs = new String[]{"data/records.db","localhost","8081","/Users/","../rtserver/incoming","macprd","config/www-rtbackup.properties"};                    
                }
                try {
                    mymain.main(myargs);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
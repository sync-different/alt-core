package com.mycompany.app;

import main.Main;

public class ScrubberService implements Runnable {
        
        private Boolean mTerminated = false;

        public ScrubberService() {
            Thread t;
            t = new Thread(this,"scrubber");
            t.start();
        }

        public void run () {
            while (!mTerminated) {
                Main mymain = new Main();
                String[] myargs = new String[]{"data/records.db","localhost","8081","/Users/","../rtserver/incoming","macprd","config/www-rtbackup.properties"};

                try {
                    mymain.main(myargs);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
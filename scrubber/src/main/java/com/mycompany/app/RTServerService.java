package com.mycompany.app;

import javaapplication1.WebServer;

public class RTServerService implements Runnable {
        
        private Boolean mTerminated = false;

        public RTServerService() {
            Thread t;
            t = new Thread(this,"rtserver");
            t.start();
        }

        public void run () {
            while (!mTerminated) {
                WebServer mymain = new WebServer();
                String[] myargs = new String[]{"8081"};

                try {
                    mymain.main(myargs);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
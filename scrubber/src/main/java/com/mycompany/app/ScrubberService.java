package com.mycompany.app;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import main.Main;

public class ScrubberService implements Runnable {
        
        private Boolean mTerminated = false;
        private String[] arguments;
        private String params = "";
        protected static Properties props = new Properties();


        String mLocalPort = "8081";

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
            try {
                loadProps();
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!mTerminated) {
                Main mymain = new Main();
                System.out.println("scrubber launch flags = " + params);
                String[] myargs;
                if (params.length() > 0) {
                    myargs = new String[]{"data/records.db","localhost",mLocalPort,"/Users/","../rtserver/incoming","macprd","config/www-rtbackup.properties",params};
                } else {
                    myargs = new String[]{"data/records.db","localhost",mLocalPort,"/Users/","../rtserver/incoming","macprd","config/www-rtbackup.properties"};                    
                }
                try {
                    mymain.main(myargs);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        void loadProps() throws IOException {
            try {
                props = new Properties();
                System.out.println("loadProps()");
                File f = new File
                        (
                        ".."+
                        File.separator+
                        "rtserver"+
                        File.separator+
                        "config"+
                        File.separator+
                        "www-server.properties");
                if (f.exists()) {
                        InputStream is =new BufferedInputStream(new
                                    FileInputStream(f));
                        props.load(is);
                        is.close();
                        String r = props.getProperty("port");
                        if (r != null) {
                            mLocalPort = r;
                        } else {
                           System.out.println("**** ScrubberService: port not found. Using default."); 
                        }
                        System.out.println("**** ScrubberService: loaded port "+mLocalPort);
                    }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
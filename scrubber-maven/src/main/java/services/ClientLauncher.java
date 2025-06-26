/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package services;

import java.io.PrintStream;
import java.util.Properties;

public class ClientLauncher implements Runnable {

    Thread t;
    static String  mServername;
    static private String mPortRT;
    long mDelay = 10000;
    static private String mSignature;
    boolean bTerminated = false;
    boolean mHostFound = false;
    
    ClientService cs = null;
    String mCONFIG_PATH = "";
    int mLogLevel = 0;
    
  
    public ClientLauncher(String _server, 
            String _server_port, 
            Long _delay, 
            String _signature, 
            boolean _hostfound, 
            String _configpath,
            int _loglevel) {
        try {
            
            System.out.println("*** server launcher name = " + _server);
            mServername =  _server;
            mPortRT = _server_port;
            mDelay = _delay;
            mSignature = _signature;
            mHostFound = _hostfound;
            mCONFIG_PATH = _configpath;
            mLogLevel = _loglevel;

            // Create a new, second thread
            t = new Thread(this, "Demo Thread");
            System.out.println("Child thread: " + t);
            t.start(); // Start the thread
       } catch (Exception e) {
           e.printStackTrace();
       }
    }
    
    public void terminate() {
        bTerminated = true; 
        if (cs != null) {
            cs.terminate();
        }
    }
    
    public void run() {
        while (!bTerminated) {
            System.out.println("ClientService launched.");
            cs = new ClientService(mServername, mPortRT, mDelay, mSignature, false, mHostFound, mCONFIG_PATH, mLogLevel);
            cs.run();
            cs = null;
            System.out.println("ClientService completed.");
            System.out.append("All done. Forcing a GC");
            System.gc();
        }
    }
    
}

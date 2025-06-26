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

public class TransferLauncher implements Runnable {
    Thread t;
    boolean mTerminated = false;
    
    private String mScanDirectory;
    private Long mPeriodMs;
    static String mHostName = "";
    static String mHostPort = "";        
    static String mSignature = "";   
    static String mCONFIG_PATH = "";
    
    boolean bHostFound = false;
    
    TransferService ts = null;
    static int mLogLevel = 0;

    public TransferLauncher(String _scan_directory, 
            String _hostname, 
            String _hostport, 
            Long _period_ms, 
            String _signature, 
            boolean _hostfound, 
            String _configpath,
            int _loglevel) {
        try {
            
                mScanDirectory = _scan_directory;
                mPeriodMs = _period_ms;
                mHostName = _hostname;
                mHostPort = _hostport;
                mSignature = _signature;
                bHostFound = _hostfound;
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
        mTerminated = true; 
        if (ts != null) {
            ts.terminate();
        }
    }
    
    public void run() {
        while (!mTerminated) {
            System.out.println("TransferService launched. Hostfound = " + bHostFound);
            ts = new TransferService(mScanDirectory,
                    mHostName,
                    mHostPort,
                    mPeriodMs,
                    mSignature,
                    false,
                    bHostFound,
                    mCONFIG_PATH,
                    mLogLevel);
            ts.run();
            ts = null;
            System.out.println("TransferService completed.");
            System.out.append("All done. Forcing a GC");
            System.gc();
             
        }
    }
        
}

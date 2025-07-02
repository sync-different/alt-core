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
import static services.ScannerLauncher.getConfig;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

public class BackupClientLauncher implements Runnable {

    Thread t;
    static String  mServername;
    static private String mPortRT;
    static private String mSignature;
    boolean bTerminated = false;
    boolean mHostFound = false;
    int mLogLevel = 0;
    
    BackupClientService cs = null;
    String mCONFIG_PATH = "";
    long mDelay = 0;

    /* print to stdout */
    protected void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [backup_client_launcher_" + threadID + "] " + s);
    }

    public BackupClientLauncher(String _server, 
            String _server_port, 
            String _signature, 
            boolean _hostfound, 
            String _configpath, 
            Long _mdelay,
            int _loglevel) {
        try {
            mServername =  _server;
            mPortRT = _server_port;
            mSignature = _signature;
            mHostFound = _hostfound;
            mCONFIG_PATH = _configpath;
            mDelay = _mdelay;
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
            String sBackupNode = getConfig("backupnode", mCONFIG_PATH);
            String sSyncNode = getConfig("syncnode", mCONFIG_PATH);
            if (sBackupNode.equals("yes") || sSyncNode.equals("yes")) {
                System.out.println("BackupClientService launched.");
                cs = new BackupClientService(false, mSignature, mHostFound, mServername, mPortRT, mCONFIG_PATH, mLogLevel);
                cs.run();
                cs = null;
                System.out.println("BackupClientService completed.");                
                System.out.append("All done. Forcing a GC");
                System.gc();
            } else {
                p("***SKIPPING BACKUP SERVICE ****");
                try {
                   Thread.sleep(mDelay);
                } catch (Exception e) {
                   e.printStackTrace();
                }
                
            }
            

        }
    }
    
}

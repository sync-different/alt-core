/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package services;

import static services.BackupClientService.ANSI_RESET;
import static services.TransferService.ANSI_YELLOW;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.util.Properties;
import processor.FileDatabase;
import utils.Appendage;

import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

public class ScannerLauncher implements Runnable{

    Thread t;
    boolean mTerminated = false;
    
   static String mRECORDS_FILE_PATH         = "./data/.records.db";
   static String mSCAN_PATH         = "";
   static String mBACKUP_PATH         = "";
   static String mCONFIG_PATH = "";
   static String mUUID = "";
   static String mHostName = "";
   static Boolean mDEBUG_MODE = false;
   
   long mDelay = 10000;
   protected static Properties props = new Properties();

   static String mUUIDPath = "";
   
   static String mSignature = "";

   static String LOG_NAME = "logs/scanner.txt";
   static PrintStream log= null;

   static FileDatabase   mFileRecords;  /*Database of previously process files*/
   static int mScansBeforeScrub = 10;
   static int mScansBeforeMD5Check = 10;
   
   static boolean bHostFound = false;

   static ScannerService ss = null;
   
   static int mLogLevel = 0;

   static boolean bConsole = true;

    static String appendage = "";
    static String appendageRW = "";

    /* print to stdout */
    static protected void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate+ " [DEBUG] [ScannerLauncher_" + threadID + "] " + s);
    }

     protected static void pw(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [SC.ScannerService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    public ScannerLauncher( String _recordspath, 
                            String _hostname, 
                            long _delay, 
                            String _scanpath, 
                            String _signature, 
                            boolean _hostfound, 
                            String _configpath,
                            int _loglevel) {

       try {
            Appendage app = new Appendage();
            appendage = app.getAppendage();
            appendageRW = app.getAppendageRW();

            mRECORDS_FILE_PATH = _recordspath;
            //mUUID = _uuid;
            mHostName = _hostname;
            mDelay = _delay;
            mSCAN_PATH = _scanpath;
            mSignature = _signature;
            bHostFound = _hostfound;
            mCONFIG_PATH = _configpath;
            mLogLevel = _loglevel;
            
            // Create a new, second thread
            t = new Thread(this, "scanner");
            p("Child thread: " + t);
            t.start(); // Start the thread
       } catch (Exception e) {
           e.printStackTrace();
       }
      
      
   }
    
    public void terminate() {
        mTerminated = true; 
        if (ss != null) {
            ss.terminate();
        }
    }

    static String getConfig(String _name, String _config) {
        
        try {
            File f = new File(_config);
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();
                String r = props.getProperty(_name);
                if (r != null ) {
                    p("Old value = " + r);
                    return r;
                } else {
                    return "";
                }
            } else {
                pw("File not found. exiting.... File: "+ f.getAbsolutePath());
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }              
    }
    
    public void run() {
        while (!mTerminated) {
            p("CONFIG PATH = " + mCONFIG_PATH);
            
            String sScanNode = getConfig("scannode", appendage + mCONFIG_PATH);       
            if (sScanNode.equals("on")) {
                p("ScannerService(core) launched.");
                ss = new ScannerService(mRECORDS_FILE_PATH, 
                                mHostName, 
                                mDelay, 
                                "", 
                                mSignature,
                                false,
                                bHostFound,
                                appendage + mCONFIG_PATH,
                                mLogLevel);
                ss.run();
                p("ScannerService(core) completed.");
                ss.cleanup();
                p("ScannerService(core) cleaned up.");
                ss = null;                
            } else {
                p("**** SKIPPING SCAN ****");
                try {
                    Thread.sleep((mDelay));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }                        
            
            String sBackupNode = getConfig("backupnode", mCONFIG_PATH);            
            if (sBackupNode.equals("yes")) {
                mBACKUP_PATH = "";
                try {
                    mBACKUP_PATH = URLDecoder.decode(getConfig("backuppath", mCONFIG_PATH),"UTF-8");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (mBACKUP_PATH.length() > 0) {
                    p("ScannerService (backup) launched for PATH: " + mBACKUP_PATH);
                    ss = new ScannerService(mRECORDS_FILE_PATH, 
                                            mHostName, 
                                            mDelay, 
                                            mBACKUP_PATH,       
                                            mSignature,
                                            false,
                                            bHostFound,
                                            mCONFIG_PATH,
                                            mLogLevel);             
                    ss.run();
                    p("ScannerService(backup) completed.");
                    ss.cleanup();
                    p("ScannerService(backup) cleaned up.");
                    ss = null;
                }
            } else {
                    p("**** SKIPPING SCAN FOR BACKUP ****");
            }
            
            String sSyncNode = getConfig("syncnode", mCONFIG_PATH);            
            if (sSyncNode.equals("yes")) {
                String mSYNC_PATH = "";
                try {
                    mSYNC_PATH = URLDecoder.decode(getConfig("syncpath", mCONFIG_PATH),"UTF-8");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (mSYNC_PATH.length() > 0) {
                    p("ScannerService (sync) launched for PATH: " + mSYNC_PATH);
                    ss = new ScannerService(mRECORDS_FILE_PATH, 
                                            mHostName, 
                                            mDelay, 
                                            mSYNC_PATH,       
                                            mSignature,
                                            false,
                                            bHostFound,
                                            mCONFIG_PATH,
                                            mLogLevel);             
                    ss.run();
                    p("ScannerService(sync) completed.");
                    ss.cleanup();
                    p("ScannerService(sync) cleaned up.");
                    ss = null;
                }
            } else {
                    p("**** SKIPPING SCAN FOR SYNC ****");
            }
            
            p("All done. Forcing a GC");
            System.gc();
        }
    }
    
    
}

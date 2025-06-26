/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package services;

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
       
    public ScannerLauncher( String _recordspath, 
                            String _hostname, 
                            long _delay, 
                            String _scanpath, 
                            String _signature, 
                            boolean _hostfound, 
                            String _configpath,
                            int _loglevel) {

       try {

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
            System.out.println("Child thread: " + t);
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
                    System.out.println("Old value = " + r);   
                    return r;
                } else {
                    return "";
                }
            } else {
                System.out.println("File not found. exiting...");
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }              
    }
    
    public void run() {
        while (!mTerminated) {
            String sScanNode = getConfig("scannode", mCONFIG_PATH);       
            if (sScanNode.equals("on")) {
                System.out.println("ScannerService(core) launched.");
                ss = new ScannerService(mRECORDS_FILE_PATH, 
                                mHostName, 
                                mDelay, 
                                "", 
                                mSignature,
                                false,
                                bHostFound,
                                mCONFIG_PATH,
                                mLogLevel);
                ss.run();
                System.out.println("ScannerService(core) completed.");
                ss.cleanup();
                System.out.println("ScannerService(core) cleaned up.");
                ss = null;                
            } else {
                System.out.println("**** SKIPPING SCAN ****");
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
                    System.out.println("ScannerService (backup) launched for PATH: " + mBACKUP_PATH);
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
                    System.out.println("ScannerService(backup) completed.");
                    ss.cleanup();
                    System.out.println("ScannerService(backup) cleaned up.");
                    ss = null;
                }
            } else {
                    System.out.println("**** SKIPPING SCAN FOR BACKUP ****");
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
                    System.out.println("ScannerService (sync) launched for PATH: " + mSYNC_PATH);
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
                    System.out.println("ScannerService(sync) completed.");
                    ss.cleanup();
                    System.out.println("ScannerService(sync) cleaned up.");
                    ss = null;
                }
            } else {
                    System.out.println("**** SKIPPING SCAN FOR SYNC ****");
            }
            
            System.out.append("All done. Forcing a GC");
            System.gc();
        }
    }
    
    
}

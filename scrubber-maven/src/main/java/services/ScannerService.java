/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package services;

import processor.FileUtils;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.BindException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.Scanner;
import processor.FileDatabase;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.StringTokenizer;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import utils.NetUtils;
import java.net.URLDecoder;

//import static processor.FileUtils.loadBlacklistMap;

public class ScannerService implements Runnable {
   Thread t;
 
   String mRECORDS_FILE_PATH         = "./data/.records.db";
   String mSCAN_PATH         = "";
   String mSCAN_FILE = "";
    

   String mCONFIG_PATH = "";
   String mUUID = "";
   String mHostName = "";
   Boolean mDEBUG_MODE = false;
   
   long mDelay = 10000;
   boolean mTerminated = false;
   protected Properties props = new Properties();

   String mUUIDPath = "";
   
   String mSignature = "";

   String LOG_PATH = "logs/";
   String LOG_NAME = "scanner.txt";
   PrintStream log= null;

   FileDatabase   mFileRecords;  /*Database of previously process files*/
   int mScansBeforeScrub = 1000;
   static int mScanRunSoFar = 0;
   int mScansBeforeMD5Check = 10;
   
   static boolean bHostFound = false;
   static int mLogLevel = 0;
         
   public void terminate() {
       
       log("Recieved Termination request.", 0);
       mTerminated = true;       
   }
   
   public void cleanup() {
       
               
       mRECORDS_FILE_PATH = null;
       mSCAN_PATH = null;
       mUUID = null;
       mHostName = null;
       props = null;
       mUUIDPath = null;
       mSignature = null;
       LOG_NAME = null;
       log = null;
       mFileRecords = null;
   }
      
   public ScannerService(String _recordspath, 
                        String _hostname, 
                        long _delay, 
                        String _scanpath, 
                        String _signature, 
                        boolean _launchthread, 
                        boolean _hostfound, 
                        String _configpath,
                        int _loglevel) {

      mRECORDS_FILE_PATH = _recordspath;
      //mUUID = _uuid;
      mHostName = _hostname;
      mDelay = _delay;
      mSCAN_PATH = _scanpath;
      mSignature = _signature;
      bHostFound = _hostfound;
      mCONFIG_PATH = _configpath;
      mLogLevel = _loglevel;
   
       try {
            loadBackupProps();
            String sFilename = LOG_PATH + LOG_NAME;
            log = new PrintStream(new BufferedOutputStream(
                            new FileOutputStream(sFilename,true)));
            log("opened log file: " + sFilename, 0);
      
            // Create a new, second thread
            if (_launchthread) {
                t = new Thread(this, "sc_s");
                System.out.println("Launched thread: " + t);
                t.start(); // Start the thread                
            }
            
       } catch (Exception e) {
           e.printStackTrace();
       }
      
      
   }
   
   public void getServerAddressPort() throws InterruptedException {
           int PORT = 1234;
           byte[] recieveData = new byte[100];
           
           DatagramSocket clientSocket = null;
           DatagramPacket recievePacket = null;
           
           while (!bHostFound) {                
               p("before try");  

               try {
                    p("[before new socket]");  
                    clientSocket = new DatagramSocket(PORT);
                    p("[before new packet]");  
                    recievePacket = new DatagramPacket(recieveData, recieveData.length);       

                    mSignature = NetUtils.getSignature();
                    p("Waiting for probe: " + mSignature);  
                    clientSocket.setSoTimeout(10000);
                    clientSocket.receive(recievePacket);

                    String s = new String(recieveData);
                    System.out.println("packet data:" + s);

                    StringTokenizer st = new StringTokenizer(s,",", true);
                    String sCount = st.nextToken();
                    st.nextToken();
                    String sSignature = st.nextToken();
                    st.nextToken();
                    String sHostIP = st.nextToken();
                    st.nextToken();
                    String sHostPort = st.nextToken().trim();

                    p("signature: '" + sSignature + "' host: '" + sHostIP + "' port: '" + sHostPort + "'");
                    if (sSignature.equals(mSignature)) {
                        p("signatures match. updating server/ip");
                        mHostName = sHostIP;
                        //mPortRT = sHostPort;
                        bHostFound = true;
                    } else {
                        p("signatures don't match. dismissing this probe.");
                    }  
                    p("normal exit1");
                                       
                } catch (BindException e) {
                    p("socket in use. Waiting 1s..."); 
                    Thread.sleep(1000);
                    e.printStackTrace();
                } catch (SocketTimeoutException e) {
                    p("socket timeout. carrying on...");
                    e.printStackTrace();
                } catch (Exception e) {
                    p("there was some kind of exception");    
                    e.printStackTrace();                    
                } finally {
                    p("finally1...");

                    if (clientSocket != null) {
                        p("finally2a...");
                        if (clientSocket.isConnected()) {
                            p("isconnected...");
                            clientSocket.disconnect();                        
                        }
                        p("finally2b...");
                        if (clientSocket.isBound()) {
                            p("isbound...");
                            clientSocket.close();                        
                        }                        
                    }
                    p("finally3...");
                }
           }
           p("normal exit2");
    }
   
    void loadBackupProps() throws IOException {
    
        //System.out.println(System.getProperty("java.home"));
        p("loadBackkupProps()");
        File f = new File(mCONFIG_PATH);
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();

       
            String r = props.getProperty("uuidpath");
            if (r != null) {
                mUUIDPath = r;
            }

            r = props.getProperty("scrubcount");
            if (r != null) {
                mScansBeforeScrub = Integer.parseInt(r);
            }

            r = props.getProperty("md5count");
            if (r != null) {
                mScansBeforeMD5Check = Integer.parseInt(r);
            }

            r = props.getProperty("debugmode");
            if (r != null) {
                mDEBUG_MODE = Boolean.parseBoolean(r);
            }

           if (mSCAN_PATH.length() == 0) {
                //get scan path from config 
                System.out.println(">>> Retrieving paths from config file.");
                r = props.getProperty("scandir");
                if (r != null) {
                    mSCAN_FILE = r;
                }                
            } else {
                System.out.println(">>> Retrieving path from param.");
            }
           
            r = props.getProperty("logpath");
            if (r != null) {
                LOG_PATH = r;
            }

        }

    }

    void printProps() {
        p("uuidpath=" + mUUIDPath);
        p("scrubcount=" + mScansBeforeScrub);
        p("md5count=" + mScansBeforeMD5Check);
        p("debugmode=" + mDEBUG_MODE);
        p("scandir=" + mSCAN_PATH);
        //System.out.println("maxstrlen=" + mMaxStrLen);
        //System.out.println("autocomplete=" + mAutoComplete);
        //System.out.println("forceindex=" + mForceIndex);
        
    }

    
   /* print to stdout */
    protected void p(String s) {

        long threadID = Thread.currentThread().getId();
        System.out.println("[scanner_" + threadID + "] " + s);
    }

    /* print to the log file */
    protected void log(String s, int _loglevel) {

        if (_loglevel <= mLogLevel) {
            Date ts_start = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
            String sDate = sdf.format(ts_start);

            //p(sDate + " " + s);
            synchronized (log) {
                log.println(sDate + " " + _loglevel + " " + s);
                log.flush();
            }
            p(sDate + " " + _loglevel + " " + s);            
        }
    }
   
    String getConfig(String _name, String _config) {
        
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
    
   // This is the entry point for the second thread.
   public void run() {
      try {
            int j = 0;
            
            String sState = getConfig("state", "../rtserver/config/www-setup.properties");
            String sWindowsServer = getConfig("winserver", "../rtserver/config/www-server.properties");
            Boolean bWindowsServer = true;
            if (!"".equals(sWindowsServer)){
                bWindowsServer = Boolean.parseBoolean(sWindowsServer);
            }
            
            System.out.println("Setup State = " + sState);
            if (sState.equals("NEW")) {
                while (sState.equals("NEW")) {
                    System.out.println("State=NEW. Waiting for user to complete Wizard setup. Sleeping for " + mDelay + "ms.");
                    Thread.sleep(mDelay);                
                    sState = getConfig("state", "../rtserver/config/www-setup.properties");
                }                
            }

            while (!mTerminated) {                
                log("Scanner started", 0);
                
                try {
                    loadBackupProps();
                    printProps();
                    
                    //String sIDPath = "../../../crawler/branches/dev/data/.uuid";
                    FileInputStream bf2 = new FileInputStream(mUUIDPath);
                    Scanner scanner2 = new Scanner(bf2);
                    mUUID = scanner2.nextLine();
                    
                    bf2.close();
                    scanner2 = null;
                                        
                    p("uuid = '" + mUUID + "'");                    

                } catch (Exception e) {
                    
                }

                FileUtils pf = new FileUtils(mRECORDS_FILE_PATH, mDEBUG_MODE, mCONFIG_PATH, mLogLevel);
                Boolean bres = pf.loadPendingFiles();
                System.out.println("load res = " + bres);
                System.out.println("count = " + pf.printFileCount());

                boolean bCheck = false;
                j++;
                if (j > mScansBeforeMD5Check) {
                    j=0;
                    bCheck = true;
                }
                
                Runtime r = Runtime.getRuntime();
                long freemem1 = r.freeMemory();
                System.out.println("freemem1 = " + freemem1);
                
                //load hashmap (blacklisted paths)
                pf.loadBlacklistMap();                             

                System.out.println("********** SCAN PATH = " + mSCAN_PATH);
                if (mSCAN_PATH.length() > 0) {
                    pf.ScanDirectory(mSCAN_PATH, bCheck);
                } else {
                    System.out.println("********** SCAN CONFIG FILE = " + mSCAN_FILE);
                    
                    File f = new File(mSCAN_FILE);
                    if (f.exists()) {
                        InputStream is =new BufferedInputStream(new
                                       FileInputStream(f));
                        props.load(is);
                        is.close();

                        String sScanDirs = props.getProperty("scandir");
                        String[] sd = sScanDirs.split(";");
                        for (int k=0;k<sd.length;k++) {
                            String sdu = URLDecoder.decode(sd[k], "UTF-8");
                            log("START SCAN DIR: " + sdu, 1);                    
                            int res = pf.ScanDirectory(sdu, bCheck);
                            if (res < 0) {
                                log("There was an error scanning path: '" + sdu + "'", 0);                                 
                                if (pf.oomcase) {
                                    System.out.println("There was an OOM. Time to exit...");                    
                                    System.exit(-1);
                                }                                
                            }
                        }
                    } else {
                        log("ERROR: scan file does not exist: " + mSCAN_FILE, 0);
                    }
                }
                
                log("********** SCAN FOR DELETED FILES", 2);
                pf.ScanDeletedFiles(bWindowsServer);                 
                
                long freemem2 = r.freeMemory();
                p("freemem2 = " + freemem1);
                long freememdelta = freemem2 - freemem1;
                p("DELTA = " + freememdelta);
                
                p("countrecords[1] = " + pf.countrecords);
                pf.countrecords = 0;                
                p("countrecords[2] = " + pf.countrecords);
                
                log("file record count = " + pf.printFileCount(),2);
                bres = pf.savePendingFiles();
                log("save records res = " + bres, 1);
                                
                System.out.println("pf cleanup");
                pf.cleanup();

                System.out.println("Setting pf to Null");
                pf = null;                

                //Scrubber
                //ScrubService scrub = new ScrubService(mRECORDS_FILE_PATH, mUUID, mHostName);
                
                mScanRunSoFar++;
                if (mScanRunSoFar > mScansBeforeScrub) {
                    log("[Scanner completed.  " + mScansBeforeScrub + " runs done. Time to Scrubber.]", 1);                    
                    mScanRunSoFar=0;
                    
                    int nRetry = 0;
                    while (!bHostFound && nRetry < 100) {
                        p("*** Looking for Probe #:" + nRetry);
                        getServerAddressPort();
                        if (!bHostFound) {
                            //wait for 3s before retry
                            nRetry++;
                            p("Probe Retry#" + nRetry);
                            Thread.sleep(3000);
                        }
                    }
                    
                    if (bHostFound) {
                        System.out.println("********** SYNC");
                        mFileRecords = new FileDatabase(mRECORDS_FILE_PATH);
                        log("count before scrub = " + mFileRecords.count(), 1);
                        mFileRecords.sync(mHostName,mUUID);
                        log("count after scrub = " + mFileRecords.count(), 1);
                        boolean res = mFileRecords.save();
                        System.out.println("res save file = " + res);                                            
                        System.out.println("Setting fr to Null");
                        mFileRecords = null;
                    } else {
                        log("[WARNING: Skipped scrubber because server was not found.]", 0);   
                    }
                } else {
                    log("[Scanner completed: Count : " + mScanRunSoFar + "]", 1);
                }

                p("Time to sleep for " + mDelay);
                try {
                    Thread.sleep(mDelay);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //Run only once.              
                mTerminated = true;
                
                //set hostfound to false in case the Server IP changed
                //bHostFound = false;
                
            }
         
          
     } catch (Exception e) {
         System.out.println("Child interrupted.");
         e.printStackTrace();
     }
     System.out.println("Exiting child thread.");
   }
}

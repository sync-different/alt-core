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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.StringTokenizer;
import utils.Cass7Funcs;
import processor.FileUtils;

import utils.HTTPRequestPoster;
import utils.NetUtils;

import java.util.Random;

/**
 *
 * @author agf
 */
public class BackupClientService implements Runnable {
    
    String mHostName = "";
    String mHostPort = "";        
    String mSignature = "";

    boolean bHostFound = false;
    
    PrintStream log = null;
    String LOG_PATH = "logs/";
    String LOG_NAME = "backup_client.txt";
    Thread t;
    
    int mDelayFile = 10;   //sleep every 10 files
    long mDelayTime = 250; //number of miliseconds to sleep
    
    static String sUUID;
    static String sUUIDpath;
    static String sMode;
    static String sServerPath;
    //static String sLocalPath;
    static String sBackupPath;
    static String sBackupNode;
    static String sSyncPath;
    static String sSyncNode = "no";
    static String sSleep = "3000";
    static String sSleepServerLong = "3600000";
    static String sSleepServerShort = "15000";
    static String sRoot;
    static String sNewseq = "false";
    static String sTestRangeStart = "";
    static String sTestRangeEnd = "";
    static String sSleepPending = "300";

    protected static Properties props = new Properties();
    static boolean shutdown = false;
    
    static Cass7Funcs c7 = new Cass7Funcs();
    static String mCassIP = "localhost";
    /**
     * @param args the command line arguments
     */
    static HashMap PendingFiles  = new HashMap <String,String>();
    static HashMap NodeAddr = new HashMap <String, String>();
    
    static String mStorage = "./data/pending.db";
    static String mRouter = "./data/router.txt";
    
    public boolean mTerminated = false;
    static boolean bNodesLoaded = false;
    static boolean bKeepOrigFileName = true;
    
    static int mLogLevel = 0;
    /* mapping of file extensions to content-types */
    static java.util.HashMap map = new java.util.HashMap();
    
    static void setSuffix(String k, String v) {
        map.put(k, v);
    }
    
    int nBatchLIDLast = 0;
    
    String mCONFIG_PATH = "";

    static boolean bConsole = true;

    public BackupClientService(boolean _dothread, 
            String _signature, 
            Boolean _hostfound, 
            String _hostname, 
            String _hostport, 
            String _configpath,
            int _loglevel) {
        
        try {

            mLogLevel = _loglevel;
            mSignature = _signature;
            bHostFound = _hostfound;
            
            mHostName = _hostname;
            mHostPort = _hostport;
            mCONFIG_PATH = _configpath;
            
            loadProps();

            String sFileName = LOG_PATH + File.separator + LOG_NAME;
            log = new PrintStream(new BufferedOutputStream(
                            new FileOutputStream(sFileName,true)));
            log("opened log file: " + sFileName, 0);
            
            if (_dothread) {
              // Create a new, second thread
              t = new Thread(this, "sc_b");
              p("Child thread: " + t);
              t.start(); // Start the thread          
            }
      } catch (Exception e) {
          e.printStackTrace();
      }
        
    }
    
    
    
    public void run() {
        

        try {
            
            
            loadPendingFiles();
            int nFilesPending = PendingFiles.size();
            log("Pending files loaded. #Records: " + nFilesPending, 1);
            long timestamp1 = System.currentTimeMillis();
            while (!mTerminated) {
                
                p("Starting backup client run. host found=" + bHostFound);

                loadProps();
                
                while (!bHostFound) {
                    getServerAddressPort();
                    if (!bHostFound) {
                        //wait for 3s before retry
                        Thread.sleep(3000);
                    }
                }
                sServerPath = "http://" + mHostName + ":" + mHostPort + "/cass/backups/";

                printProps();
                main_client();
                //check if time to process pending files               
                long timestamp2 = System.currentTimeMillis();
                long delta = timestamp2 - timestamp1;
                int nSeconds  = (int) delta / 1000;
                p("--- seconds elapsed:" + nSeconds);
                if (nSeconds > Integer.parseInt(sSleepPending)) {
                    log("--- time to process pending files-----------------", 1);
                    processPendingFiles();
                    timestamp1 = System.currentTimeMillis();
                } 

                ///if there are new pending files after the run, save them to disk for future.
                p("db_size_stored: " + nFilesPending);
                p("db_size_now: " + PendingFiles.size());

                if (PendingFiles.size() != nFilesPending) {
                    log("Saving pending files due to record updates.", 1);
                    savePendingFiles(); 
                    nFilesPending = PendingFiles.size();
                } else {
                    p("Skipping db save, size not changed.");
                }
                                
            }            
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
    }
    
    /* print to the log file */
    protected void log(String s, int _loglevel) {

        if (_loglevel <= mLogLevel) {            
            Date ts_start = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
            String sDate = sdf.format(ts_start);

            synchronized (log) {
                log.println(sDate + " " + _loglevel + " " + s);
                log.flush();
            }
            p(sDate + " " + _loglevel + " " + s);
        }
    }

    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_RESET = "\u001B[0m";

    protected static void pw(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [BackupClientService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO] [BackupClientService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [BackupClientService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [backup_client_" + threadID + "] " + s);
    }
    
    /* load www-server.properties from java.home */
    void loadProps() throws IOException {

        String r;

        p(System.getProperty("java.home"));
        File f = new File(mCONFIG_PATH);
        
        System.out.println("CONFIG PATH = " + mCONFIG_PATH);
        
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();


            r = props.getProperty("uuidpath");
            if (r != null) {
                sUUIDpath = r;
            }

            sUUID = NetUtils.getUUID(sUUIDpath);
            
            
            r = props.getProperty("mode");
            if (r != null) {
                sMode = r;
            }

            r = props.getProperty("root");
            if (r != null) {
                sRoot = r;
            }

            r = props.getProperty("serverpath");
            if (r != null) {
                sServerPath = r;
            }

//            r = props.getProperty("localpath");
//            if (r != null) {
//                sLocalPath = r;
//            }

            r = props.getProperty("backuppath");
            if (r != null) {
                sBackupPath = URLDecoder.decode(r, "UTF-8");
            }
            
            r = props.getProperty("backupnode");
            if (r != null) {
                sBackupNode = r;
            }

            r = props.getProperty("syncpath");
            if (r != null) {
                sSyncPath = URLDecoder.decode(r, "UTF-8");
            }
            
            r = props.getProperty("syncnode");
            if (r != null) {
                sSyncNode = r;
            }

            r = props.getProperty("sleep_client");
            if (r != null) {
                sSleep = r;
            }

            r = props.getProperty("sleep_server_short");
            if (r != null) {
                sSleepServerShort = r;
            }

            r = props.getProperty("sleep_server_long");
            if (r != null) {
                sSleepServerLong = r;
            }

            r = props.getProperty("sleep_pending");
            if (r != null) {
                sSleepPending = r;
            }

            r = props.getProperty("newseq");
            if (r != null) {
                sNewseq = r;
            }

            r = props.getProperty("logpath");
            if (r != null) {
                LOG_PATH = r;
            }

            r = props.getProperty("pending");
            if (r != null) {
                mStorage = r;
            }
            
            r = props.getProperty("router");
            if (r != null) {
                mRouter = r;
            }
            
            r = props.getProperty("keepname");
            if (r != null) {
                bKeepOrigFileName = Boolean.parseBoolean(r);
            }
            
            r = props.getProperty(("delay_file_b"));
            if (r != null) {                
                mDelayFile = Integer.parseInt(r);
            }

            r = props.getProperty(("delay_time_b"));
            if (r != null) {
                mDelayTime = Long.parseLong(r);
            }


        }
    }

    void printProps() {
        p("uuidpath = '" + sUUIDpath + "'");
        p("uuid = '" + sUUID + "'");
        p("mode = '" + sMode + "'");
        p("serverpath = '" + sServerPath + "'");
        //p("localpath = '" + sLocalPath + "'");
        p("backuppath = '" + sBackupPath + "'");
        p("syncpath = '" + sSyncPath + "'");
        p("syncnode = '" + sSyncNode + "'");
        p("sleep_client = '" + sSleep + "'");
        p("root = '" + sRoot + "'");
        p("sleep_server_long = '" + sSleepServerLong + "'");
        p("sleep_server_short = '" + sSleepServerShort + "'");
        p("newseq = '" + sNewseq + "'");
        p("sleep_pending = '" + sSleepPending + "'");
        p("logpath = '" + LOG_PATH + "'");
        
    }
    
    public void main_client() throws IOException {

        try {
            
        p("running client mode");
        fillMap();
        Integer nSeqStore = 0;
        Scanner scanner2 = null;
        Integer nSequenceLID = 0;
        Integer nBatchLID = 0;
        long lSleep = Long.parseLong(sSleep);            

        String sTempPath = "";
        if (sBackupPath.length() > 0) {
            sTempPath = sBackupPath;
        } else {
            if (sSyncPath.length() > 0) {       
                sTempPath = sSyncPath;
            }     
        }
        p("sTempPath = " + sTempPath);
        
        String sIDPath = sTempPath + "lastid.tmp";
        
        boolean bFoundLID = false;
        File lid = new File(sIDPath);
        if (lid.exists() && lid.length() > 0) {
            bFoundLID = true;
            FileInputStream bf2 = new FileInputStream(sIDPath);
            scanner2 = new Scanner(bf2);

            String nLine2 = scanner2.nextLine();
            p("(lastid.txt) BatchId: " + nLine2);
            String nLine3 = scanner2.nextLine();
            p("(lastid.txt) SeqId: " + nLine3);

            nSequenceLID = Integer.parseInt(nLine3);
            nBatchLID = Integer.parseInt(nLine2) + 1;

            p("(lastid.txt) Sequence ID: " + nSequenceLID);            
            p("(lastid.txt) Batch ID: " + nBatchLID);            
        } else {
            log("WARNING: (lastid.txt) file not found or file len = 0.", 0);
        }
       
        int nRes = 1;
        String sGetPath = sServerPath + "nodeinfo.php?foo=nodes";
        String sFullName = sTempPath + "nodeinfo.tmp";
        
        LoadNodes(sGetPath, sFullName);
        
        //get backupinfo from server
        sGetPath = sServerPath + "backupinfo.php";
        sFullName = sTempPath + "backupinfo.tmp";
        
        p("Source = " + sGetPath);
        p("Dest = " + sFullName);
        
        File tmp = new File(sFullName);
        if(tmp.exists()){
            boolean res = tmp.delete();
            if (!res){
                log("WARNING: Failed to delete backupinfo.tmp -- " + sFullName ,0);
            }
        }
        
        nRes = getfile(sGetPath, sFullName, 2, 500, 10000);
        
        Integer nNextBatch = 0;
        Integer nLastBackupID = 0;
                
        if (nRes > 0) {
            FileInputStream bf3 = new FileInputStream(sFullName);
            p("[a]");
            scanner2 = new Scanner(bf3);
            String sLine = scanner2.nextLine();
            String sElements[] = sLine.split(",");
            String sSequence = sElements[0];
            //scanner2.useDelimiter(",");
            //String sSequence = scanner2.next();
            log("nSequence = " + sSequence, 2);
            String sLastBatch = sElements[1];
            //String sLastBatch = scanner2.next();
            log("nLastBatch = " + sLastBatch, 2);
            String sLastBackup = sElements[2];
            //String sLastBackup = scanner2.next();
            log("nLastBackup = " + sLastBackup, 2);
            
            nNextBatch = Integer.parseInt(sLastBatch);
            Integer nSequence = Integer.parseInt(sSequence);
            scanner2.close();
            bf3.close();
            
            nLastBackupID = Integer.parseInt(sLastBackup);
            
            if (bFoundLID) {
                //if the batch number local exceeds db value, override with db value
                /*if (nBatchLID > nNextBatch) {
                    nBatchLID = nNextBatch;
                }*/
                //set sequence to what's in the db
                if (nSequence != nSequenceLID) {
                    nSequenceLID = nSequence;
                    nBatchLID = 1;
                }                
            } else {
                //if the lastid file is missing, use sequence number from db
                nSequenceLID = nSequence;
                nBatchLID = 0;
                log("WARNING: (lastid.tmp) file not found. Restarting sequence: " + nSequenceLID, 0);
                
                FileWriter fw = new FileWriter(sIDPath);
                BufferedWriter out = new BufferedWriter(fw);
                String sOut = nBatchLID.toString() + "\n";
                out.write(sOut);
                sOut = nSequenceLID.toString() + "\n";
                out.write(sOut);
                out.close();
            }
        }
        
        
        sGetPath = sServerPath + "backup_" + sUUID + "_" + nSequenceLID + "_" + nBatchLID;
        sFullName = sTempPath + "backup_" + sUUID + "_" + nSequenceLID + "_" + nBatchLID;
        
        log("nBatchLID " + nBatchLID + " nLastBackupID: " + nLastBackupID, 2);
        if (nBatchLID > 0 && (nBatchLID <= nLastBackupID)) {
            //delete old version of local backup file if it exists
            File fCheck = new File(sFullName);
            if (fCheck.exists()){
                boolean bdel = fCheck.delete();
                log("deleting old backup file = " + fCheck.getCanonicalPath() + " res:" + bdel, 2);
                try {
                    Thread.sleep(50);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            //get the file from the server
            nRes = getfile(sGetPath, sFullName, 2, 500, 10000);
            log("getfile: " + sGetPath + " nres: " + nRes, 2);
        } else{
            //not a valid batch ID
            nRes = -1;
        }
                
        boolean bFound = false;
        if (nRes > 0) {
            //found file we're looking for...
            bFound = true;            
        } else {
            if (nRes == -2) {
                //case where file exists but size = 0
                log("found file with size 0. ", 1);
                log("Storing New batchnum: " + nBatchLID + "Sequence: " + nSequenceLID, 1);
                
                FileWriter fw = new FileWriter(sIDPath);
                BufferedWriter out = new BufferedWriter(fw);
                String sOut = nBatchLID.toString() + "\n";
                out.write(sOut);
                sOut = nSequenceLID.toString() + "\n";
                out.write(sOut);
                out.close();
            }
        }

        nSeqStore = nSequenceLID; //last valid sequence found

        sFullName = sTempPath + "backup_" + sUUID + "_" + nSeqStore + "_" + nBatchLID;
        
        int nTotal = 0;
        int nPendingFile = 0;
        int nSkipped = 0;
        int nPendingNode = 0;
        int nNotFound = 0;
        int nFound = 0;
        int nWriteError = 0;
        int nSyncFound = 0;
        int nSyncNotFound = 0;
        int nSyncError = 0;
        int nSyncWriteError =0;
        int nSyncSkipped = 0;
        int nSyncSkippedBackup = 0;
        

        if (nRes > 0 /*nBatchLID > nBatchLIDLast*/) {
            //log("Curr:" + nBatchLID + " Last: " + nBatchLIDLast);
            log("***** THERE IS A NEW BATCH TO PROCESS ****", 0);            
            log("local file to process: '" + sFullName + "'", 0);
        } else {
            System.out.println("***** THERE IS NO NEW BATCH TO PROCESS ****");            
            bFound = false;
        }
       
        if (bFound) {
            try {
                    FileInputStream bf = new FileInputStream(sFullName);
                    Scanner scanner = new Scanner(bf);
                    
                    nTotal = 0;
                    nSkipped = 0;
                    nPendingNode = 0;
                    nPendingFile = 0;
                    
                    InetAddress clientIP = NetUtils.getLocalAddressNonLoopback2();
                    //InetAddress clientIP = InetAddress.getLocalHost();
                    String LocalIP = clientIP.getHostAddress();
                    log("Local IP address:" + LocalIP, 1);

                    while (scanner.hasNextLine()) {
                        String nLine = scanner.nextLine();
                        p("---------------------------------------------------------");
                        Thread.sleep(10);
                        p("line: '" + nLine + "'");
                        if (!nLine.equals("") && !nLine.equals("0,0,0")) {                            
                            //process record
                            nTotal++;
                            if (nTotal % mDelayTime == 0) {
                                p("time to sleep a little bit...");
                                try {
                                    Thread.sleep(mDelayTime);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            log("-----processing record: ---------------------------------" + nTotal, 2);

                            String sOrderType = "";
                            String sIPPort_file = "";
                            String delimiters = ",";
                            StringTokenizer st = new StringTokenizer(nLine.trim(), delimiters, true);

                            Integer nTokens = st.countTokens();
                            p("ntokens: " + nTokens);
                            
                            String sFileName = "";
                            String sFileMD5 = "";
                            String sUUID_file = "";
                            String sFileExt = "";
                            
                            try {
                                //field 1 - md5
                                sFileName = st.nextToken();
                                sFileMD5 = sFileName;
                                p("sFileName = '" + sFileName + "'");
                                st.nextToken();

                                //field 2 - extension
                                sFileExt = st.nextToken();
                                p("sFileExt = '" + sFileExt + "'");
                                st.nextToken();

                                //field3 - URL
                                sGetPath = st.nextToken();
                                p("sGetPath (ori) = '" + sGetPath + "'");
                                st.nextToken();    

                                //field4 - uuid
                                sUUID_file = st.nextToken();
                                p("sUUID_file = '" + sUUID_file + "'");

                                if (nTokens > 7) {
                                    st.nextToken();
                                    sOrderType = st.nextToken();
                                } else {
                                    //assume it's a backup/sync order if not specified (legacy)
                                    sOrderType = "backupsync";
                                }    
                            } catch (Exception e) {
                                e.printStackTrace();
                                sOrderType = "blank";
                            }
                                                        
                            p("order type : " + sOrderType);
                            
                            //Update the IP address and port in the Getpath if update is available
                            String sIPPort = "";
                            sIPPort = (String)NodeAddr.get(sUUID_file);
                                                        
                            if (sIPPort != null) {
                                log("sUUID ip/port = '" + sIPPort + "'",2);
                                //override ip/port info with latest
                                if (sIPPort.length() > 0) {
                                    String sGetPath4 = "";
                                    String delimiters4 = "/";
                                    StringTokenizer st4 = new StringTokenizer(sGetPath.trim(),delimiters4, true);
                                    st4.nextToken(); //http://
                                    st4.nextToken(); // first /
                                    st4.nextToken(); // second /
                                    sIPPort_file = st4.nextToken(); //
                                    st4.nextToken(); // third /
                                    String sFilePath = "";
                                    while (st4.hasMoreTokens()) {
                                        sFilePath += st4.nextToken(); //
                                    }

                                    log("sUUID ip/port file = '" + sIPPort_file + "'",2);
                                    String sGetPath_new = "http://" + sIPPort + "/" + sFilePath;
                                    p("sGetPath_new = '" + sGetPath_new + "'");

                                    sGetPath = sGetPath_new;                                
                                } else {
                                   p("table ip/port empty. keeping same Getpath"); 
                                }
                            } else {
                                log("table ip/port empty. keeping same Getpath",2);
                            }
                            p("sGetPath (new) = '" + sGetPath + "'");
                            
                            String sGetPath2 = "";
                            String sGetPath3 = "";
                            try {
                                sGetPath2 = sGetPath.trim().substring(sGetPath.trim().indexOf("/",8)+1, sGetPath.trim().length());
                                p("sGetPath2 (new) = '" + sGetPath2 + "'");
                                sGetPath3 = sGetPath2.substring(0,sGetPath2.indexOf("."));                                
                                p("sGetPath3 (new) = '" + sGetPath3 + "'");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }                      
                            
                            //p("sGetPath2 = '" + sGetPath2+ "'");
                            //p("sGetPath3 = '" + sGetPath3+ "'");
                                                        
                            //SYNC                            
                            if (sSyncNode.equals("yes") && sOrderType.contains("sync")) {                                
                                p("[SYNC]");
                                
                                byte[] s2 = Base64.decode(sGetPath3);
                                String sPathDec = new String(s2);
                                String sPathDec3 = sPathDec.substring(0,sPathDec.length()); /*le saqué el -1 VER!!*/

                                p("sPathDec3 = '" + sPathDec3 + "'");

                                String sPathDec2;
                                try {
                                    sPathDec2 = URLDecoder.decode(sPathDec3, "UTF-8");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    sPathDec2 = sPathDec3;
                                }

                                log("Filename = '" + sPathDec2 + "'", 2);
                                
                                Iterator it = map.entrySet().iterator();
                                FileUtils fu = new FileUtils();
                                while (it.hasNext()) {
                                    Map.Entry pairs = (Map.Entry)it.next();
                                    String sKey = (String)pairs.getKey();
                                    String sValue = (String)pairs.getValue();
                                    p("***" + sKey + " -?- " + sValue);

                                    if (sPathDec2.trim().toLowerCase().contains(sKey.toLowerCase())) {
                                        p("***" + sKey + " ---> " + sValue);
                                        String sFileName2 = "";
                                        
                                        String delimiters2 = "/";
                                        StringTokenizer st2 = new StringTokenizer(sPathDec2.trim(),delimiters2,true);
                                        while (st2.hasMoreTokens()) {
                                            sFileName2 = st2.nextToken();
                                        }
                                        p("sFileName2 = '" + sFileName2 + "'");
                                        
                                        
                                        if (sBackupNode.equals("yez") && isHashFile(sFileName2.toUpperCase())) {
                                            //skip sync of hash files , when backup is turned on.
                                            log("    >>>SYNC: Skipping, this is a HEX file and backup already enabled on this drive: '" + sFileName2 + "'", 2);
                                            nSyncSkippedBackup++;
                                        } else {
                                            if (sSyncPath.equals(sValue)) {
                                                p("CASE: sync to root folder.");
                                                sValue = "";
                                            }
                                            String sStorePath = sSyncPath + sValue + sFileName2;
                                            p("SYNC sStorePath = '" + sStorePath + "'");
                                            //boolean exists = checkfile(sStorePath);
                                            if (checknode(sGetPath)) {
                                                File f = new File(sStorePath);
                                                
                                                boolean bCopy = false;
                                                if (f.exists()) {
                                                    if (fu.calcMD5(sStorePath).equals(sFileName)) {
                                                       log("    >>>SYNC: File exists with same Name/MD5. skipping...", 2);
                                                       nSyncSkipped++;
                                                       bCopy = false;                                                        
                                                    } else {
                                                       bCopy = true;
                                                       int nSeries = 2;
                                                       boolean bCont = true;
                                                       while (bCont) {
                                                           sStorePath = sSyncPath + sValue + sFileName2.substring(0,sFileName2.lastIndexOf(".")) + "_" + Integer.toString(nSeries) + sFileName2.substring(sFileName2.lastIndexOf("."),sFileName2.length());                                                           
                                                           f = new File (sStorePath);
                                                           if (f.exists()) {
                                                               if (fu.calcMD5(sStorePath).equals(sFileName)) {                                                                   
                                                                    log("    >>>SYNC: DUP File exists with same Name/MD5. Skipping...", 2);   
                                                                    bCopy = false;
                                                                    bCont = false;
                                                               } else {
                                                                   log("    >>>SYNC: DUP File exists with same Name, but a different MD5. Try next...", 2);   
                                                                   nSeries++;
                                                               }
                                                           } else {
                                                               log("    >>>SYNC: File with same Name/MD5 doesn't exist. Syncing...", 2);   
                                                               bCont = false;
                                                           }
                                                       }
                                                       log("    >>>SYNC: New Name:" + sStorePath, 2);
                                                    }
                                                } else {
                                                    //not exists, do copy                                                    
                                                    bCopy = true;
                                                }
                                                if (bCopy){                                                                                                        

                                                    p("IPPort '" + sIPPort + "'");
                                                    String sIP = "";
                                                    if (sIPPort.contains(":")) {
                                                        sIP = sIPPort.substring(0,sIPPort.indexOf(":"));                                            
                                                    } else {
                                                        sIP = "";
                                                    }

                                                    p("IPPort_file '" + sIPPort_file + "'");
                                                    String sIP_File = "";
                                                    if (sIPPort_file.contains(":")) {
                                                        sIP_File = sIPPort_file.substring(0,sIPPort_file.indexOf(":"));                                            
                                                    } else {
                                                        sIP_File = "";
                                                    }
                                                    
                                                    log("sIP: " + sIP + " sIP_File: " + sIP_File + " LocalIP: "+ LocalIP, 2);  
                                                    int nres = 0;
                                                    if (sIP.equals(LocalIP)) {
                                                        log("SYNC via file copy", 2);                                                        
                                                        sGetPath2 = sGetPath.trim().substring(sGetPath.trim().indexOf("/",8)+1, sGetPath.trim().length());
                                                        sGetPath3 = sGetPath2.substring(0,sGetPath2.indexOf("."));                                            
                                                        s2 = Base64.decode(sGetPath3);
                                                        sPathDec = new String(s2);
                                                        sPathDec3 = sPathDec.substring(0,sPathDec.length()); /*le saqué el -1 VER!!*/
                                                        
                                                        try {
                                                            sPathDec2 = URLDecoder.decode(sPathDec3, "UTF-8");
                                                        } catch (Exception e) {
                                                            e.printStackTrace();
                                                            sPathDec2 = sPathDec3;
                                                        }
                                                        p("Local file copy.");
                                                        log("SYNC Source = " + sPathDec2,2);
                                                        log("SYNC Dest = " + sStorePath, 2);
                                                        nres = NetUtils.copyfile(sPathDec2, sStorePath);                                                        
                                                    } else {
                                                        log("SYNC via HTTP",2);
                                                        nres = getfile(sGetPath, sStorePath, 2, 500, 10000);                                                        
                                                    }                                                                                                                                                           
                                                    if (nres == 1) {
                                                        log("    >>>SYNC: file get OK.", 2);
                                                        nSyncFound++;
                                                    } 
                                                    if (nres == -1) {
                                                        log("    >>>SYNC: source file not found.", 2);
                                                        //PendingFiles.put(sFileName, sGetPath);
                                                        nSyncNotFound++;  
                                                    }
                                                    if (nres == -2) {
                                                        log("    >>>SYNC: there was an error retrieving file. keeping for later.", 2);
                                                        nSyncError++;                                
                                                    }        
                                                    if (nres == -3) {
                                                        log("    >>>SYNC: dest file write error", 2);
                                                        PendingFiles.put(sFileName, sGetPath);
                                                        nSyncWriteError++;                                
                                                    }
                                                } else {
                                                }
                                            } else {
                                                log("    >>>SYNC: Node unvailable. skipping...", 2);
                                                nSyncSkipped++;
                                            }
                                            
                                        }   
                                    } else {
                                        log("sKey not match = " + sKey, 2);
                                    }
                                    
                                }                                                               
                            } else {
                                if (!sSyncNode.equals("yes")) {
                                    log("Skipping SYNC , disabled for this node.", 2);                                    
                                }
                                if (!sOrderType.contains("sync")) {
                                    log("Skipping SYNC , not a sync order.", 2);                                    
                                }
                            }
                                                        
                            //BACKUP                           
                            if (sBackupNode.equals("yes") && sOrderType.contains("backup")) {                                
                                p("[BACKUP]");

                                //int npos = nLine.indexOf(",");
                                //sFullPath = nLine.substring(npos+1,nLine.length());
                                //sStorePath = "/home/shared/backup/" + nLine.substring(0,npos);
                                
                                String sStorePath = "";
                                if (bKeepOrigFileName) {
                                    byte[] s2 = Base64.decode(sGetPath3);
                                    String sPathDec = new String(s2);
                                    String sPathDec3 = sPathDec.substring(0,sPathDec.length()); /*le saqué el -1 VER!!*/

                                    p("sPathDec3 = '" + sPathDec3 + "'");

                                    String sPathDec2;
                                    try {
                                        sPathDec2 = URLDecoder.decode(sPathDec3, "UTF-8");
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        sPathDec2 = sPathDec3;
                                    }

                                    String sFilePath = sUUID_file + "/";
                                    
                                    String delimiters2 = "/:";
                                    StringTokenizer st2 = new StringTokenizer(sPathDec2.trim(),delimiters2,true);
                                    while (st2.hasMoreTokens()) {
                                        sFileName = st2.nextToken();
                                        if (st2.hasMoreTokens()) {
                                            String sTmp = sFileName.replaceAll(":", "");
                                            sFilePath = sFilePath + "/" + sTmp;
                                        }
                                    }
                                    log("sFilePath = '" + sFilePath + "'", 2);                                  
                                    log("sFileName = '" + sFileName + "'", 2);                                  
                                    
                                    sStorePath = sBackupPath + sFilePath + "/" + sFileName;
                                    File fh = new File(sBackupPath + sFilePath);
                                    fh.mkdirs();
                                    
                                    boolean exists = checkfile(sStorePath);
                                    log(sStorePath + " exists = " + exists ,2 );
                                    if (exists) {
                                        FileUtils fu = new FileUtils();
                                        String sMD5 = fu.calcMD5(sStorePath);
                                        log(">>>BACKUP file exists. Md5 = '" + sMD5 + "' vs. new MD5: '" + sFileMD5 + "'", 2);
                                        if (fu.calcMD5(sStorePath).equalsIgnoreCase(sFileMD5)) {
                                            log(">>>BACKUP: File exists with same MD5, skipping file rename.", 2);
                                        } else {
                                            Date ts_start = Calendar.getInstance().getTime();
                                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                                            String sDate = sdf.format(ts_start);
                                            String sFileName2 = sFileName.substring(0,sFileName.lastIndexOf("."));
                                            sStorePath = sBackupPath + sFilePath + "/" + sFileName2 + "_" + sDate + sFileExt;                                            
                                        }                                        
                                    }
                                } else {
                                    sStorePath = sBackupPath + sFileName + sFileExt;
                                }
                                log("sStorePath = '" + sStorePath + "'", 2);                                
                                //p(sStorePath);
                                //p("checkfile");
                                boolean exists = checkfile(sStorePath);
                                if (!exists) {
                                    boolean nodeavail = checknode(sGetPath);
                                    if (nodeavail)  {
                                        
                                        p("IPPort '" + sIPPort + "'");
                                        String sIP = "";
                                        if (sIPPort.contains(":")) {
                                            sIP = sIPPort.substring(0,sIPPort.indexOf(":"));                                            
                                        } else {
                                            sIP = "";
                                        }

                                        log("IPPort_file '" + sIPPort_file + "'", 2);
                                        String sIP_File = "";
                                        if (sIPPort_file.contains(":")) {
                                            sIP_File = sIPPort_file.substring(0,sIPPort_file.indexOf(":"));                                            
                                        } else {
                                            sIP_File = "";
                                        }
                                        log("sIP: " + sIP + " sIP_File: " + sIP_File + " LocalIP: "+ LocalIP, 2);  
                                        int nres = 0;
                                        if (sIP.equals(LocalIP)) {
                                            log("BACKUP via file copy", 2);                                                        
                                            //local, just COPY                                            
                                            try {
                                                sGetPath2 = sGetPath.trim().substring(sGetPath.trim().indexOf("/",8)+1, sGetPath.trim().length());
                                                sGetPath3 = sGetPath2.substring(0,sGetPath2.indexOf("."));                                            
                                                byte[] s2 = Base64.decode(sGetPath3);                                                    
                                                String sPathDec = new String(s2);
                                                String sPathDec3 = sPathDec.substring(0,sPathDec.length()); /*le saqué el -1 VER!!*/
                                                String sPathDec2 = "";
                                                try {
                                                    sPathDec2 = URLDecoder.decode(sPathDec3, "UTF-8");
                                                } catch (Exception e) {
                                                    sPathDec2 = sPathDec3;
                                                }
                                                File f = new File(sPathDec2);
                                                if (!f.exists()) {
                                                    String sWindows = NetUtils.getConfig("winserver", "../rtserver/config/www-server.properties");
                                                    boolean bWindows = Boolean.parseBoolean(sWindows);
                                                    if (!bWindows && !sPathDec2.startsWith("/")) {
                                                        sPathDec2 = "/" + sPathDec2;                                                                                                            
                                                    }
                                                }                                                                                               
                                                log("BACKUP Source = " + sPathDec2, 2);
                                                log("BACKUP Dest = " + sStorePath, 2);
                                                nres = NetUtils.copyfile(sPathDec2, sStorePath);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                nres = -1;
                                            }                                                                     
                                        } else {
                                            //remote HTTP GET
                                            log("BACKUP via HTTP",2);
                                            nres = getfile(sGetPath, sStorePath, 2, 500, 10000);
                                        }
                                        
                                        if (nres == 1) {
                                            log("    >>>BACKUP:file get OK.",2);
                                            nFound++;
                                        } 
                                        if (nres == -1) {
                                            log("    >>>BACKUP:source file not found.",2);
                                            nNotFound++;  
                                        }
                                        if (nres == -2) {
                                            log("    >>>BACKUP:there was an error retrieving file. keeping for later.",2);
                                            PendingFiles.put(sFileName, sGetPath);
                                            nPendingFile++;                                
                                        }        
                                        if (nres == -3) {
                                            log("    >>>BACKUP:dest file write error",2);
                                            PendingFiles.put(sFileName, sGetPath);
                                            nWriteError++;                                
                                        }        
                                    } else {
                                        log("    >>>BACKUP:node currently unvailable. storing for later.", 2);    
                                        PendingFiles.put(sFileName + ":" + sUUID_file, sGetPath);
                                        nPendingNode++;
                                    }
                                } else {
                                    log("    >>>BACKUP:File exists, skipping...", 2);
                                    nSkipped++;
                                }
                            } else {
                                if (!sBackupNode.equals("yes")) {
                                    log("Skipping BACKUP , disabled for this node.", 2);                                    
                                }
                                if (!sOrderType.contains("backup")) {
                                    log("Skipping BACKUP , not a BACKUP order.", 2);                                    
                                }
                            }
                        }
                    }
                    scanner.close();

                    FileWriter fw = new FileWriter(sIDPath);
                    BufferedWriter out = new BufferedWriter(fw);
                    String sOut = nBatchLID.toString() + "\n";
                    out.write(sOut);
                    sOut = nSeqStore.toString() + "\n";
                    out.write(sOut);
                    out.close();
                } catch (IOException e) {
                    log(e.getLocalizedMessage(), 0);
                    e.printStackTrace();
                }
        
            log("--------------------------------------", 1);
            log("#Total Files processed: " + nTotal, 1);

            log("#SYNC Files Processed OK: " + nSyncFound, 1);
            log("#SYNC Files skipped (Backup file): " + nSyncSkippedBackup, 1);
            log("#SYNC Files skipped (Dest file exists): " + nSyncSkipped, 1);
            log("#SYNC Source Files not found: " + nSyncNotFound, 1);
            log("#SYNC Dest Write Error: " + nSyncWriteError, 1);

            log("#BACKUP Processed OK: " + nFound, 1);
            log("#BACKUP Files skipped (Dest file exists): " + nSkipped, 1);
            log("#BACKUP File Source not found: " + nNotFound, 1);
            log("#BACKUP Dest Write Error: " + nWriteError, 1);

            log("#Pending Files (file): " + nPendingFile, 1);
            log("#Pending Files (node): " + nPendingNode, 1);
            log("#Pending Files(db): " + PendingFiles.size(), 1);
            log("--------------------------------------", 1);


            //System.out.println("Curr:" + nBatchLID + " Last: " + nBatchLIDLast);

            //if new file was just processed, then shorten sleep to 1s to speed up the backup process.
            lSleep = 1000;
            System.out.println("Short Sleep...");
            /*if (nBatchLID > nBatchLIDLast) {
                lSleep = 1000;
                System.out.println("Short Sleep...");
                nBatchLIDLast = nBatchLID;                
            } else {
                System.out.println("Long Sleep...");
            }*/
        }
        try {
            Thread.sleep(lSleep);
        } catch (Exception ex) {
            
        }
    } catch (Exception e) {
        log("WARNING: There was an exception in main_client", 0);
        e.printStackTrace();
    }
}
    
    public void terminate() {
       
       log("Recieved Termination request.", 0);
       mTerminated = true;       
   }
    
    public int getfile(String sFullPath, String sStorePath, int _tries, long _timer, int _timeout) throws IOException {

        p("source=" + sFullPath);
        p("dest=" + sStorePath);
     
        try {
            boolean gotfile = false;
            int nres = 0;

            while (_tries > 0 && !gotfile) {
                p("try # " + _tries);
                nres = HTTPRequestPoster.sendGetRequest(sFullPath, null, sStorePath + ".tmp", _timeout);
                if (nres > 0) {
                    gotfile = true;
                    
                    File fr = new File(sStorePath + ".tmp");
                    boolean bRes = fr.renameTo(new File(sStorePath));
                    p("res rename = "+ bRes);
                    if (!bRes) {
                        p("ori file exists. deleting = '" + sStorePath + "'");
                        File fro = new File(sStorePath);
                        fro.delete();
                        bRes = fr.renameTo(new File(sStorePath));    
                        p("res rename2 = "+ bRes);
                    }
                    
                } else {
                    _tries--;
                    _timer += 500;
                    Thread.sleep(_timer);
                }
            }
            return nres;
        } catch (Exception e) {
            log("exception getfile(). exiting...", 0);
            e.printStackTrace();
            return -1;
        }

    }
    
    public boolean checkfile(String sStorePath) throws IOException {

        try {
            File nFile = new File(sStorePath);
            if (nFile.exists()) {
                    long nlen = new File(sStorePath).length();
                    p("file length: " + nlen);
                    if (nlen > 0) {
                        return true;
                    } else {
                        return false;
                    }
            } else {
                return false;
            }
        } catch (Exception e) {
            p("error, exiting...");
            return false;
        }

    }
    
    public boolean checknode(String sGetPath) throws IOException {

        p("sGetPath: '" + sGetPath + "'");
        
        String sGetPath2 = sGetPath.substring(7,sGetPath.length());
        int npos = sGetPath2.indexOf(":");
        String sNode = sGetPath2.substring(0, npos);
        String sPort = sGetPath2.substring(npos+1, sGetPath2.indexOf("/"));
        p("Node: '" + sNode + "'");
        p("Port: '" + sPort+ "'");
        return isNodeAvailable(sNode, sPort);
        
    }
    
    public boolean isNodeAvailable(String _ipaddress, String _port) {
        try {
            //InetAddress addr = InetAddress.getByName(_ipaddress);
            //return addr.isReachable(500);
            InetAddress addr = InetAddress.getByName(_ipaddress);
            float portf = Float.parseFloat(_port);
            int port = (int)portf;
            InetSocketAddress sockaddr = new InetSocketAddress(addr , port);
            Socket sock = new Socket();
            int timeout = 500;
            sock.connect(sockaddr, timeout);
            sock.close();
            return true;
        } catch (SocketTimeoutException ex) {
            p("isNodeAvailable timeout");
        } catch (Exception ex) {
            p("Exception ex isNodeAvailabil()");
        }
        return false;
    }
    
    void fillMap() {
        
        try {
            String strLine = "";
            FileInputStream fstream = new FileInputStream(mRouter);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            while ((strLine = br.readLine()) != null) {
                p(strLine);
                String delimiters2 = ",";
                StringTokenizer st2 = new StringTokenizer(strLine.trim(),delimiters2,true);
                String sNode = stripLeadingAndTrailingQuotes(st2.nextToken());
                st2.nextToken();
                String sKey = stripLeadingAndTrailingQuotes(st2.nextToken());
                st2.nextToken();
                String sPath = stripLeadingAndTrailingQuotes(st2.nextToken());
                p(sNode + " - " + sKey + " - " + sPath);
                setSuffix(sKey, sPath);
                }     
        } catch (Exception ex) {
            log("WARNING: Error loading Router file: " + mRouter, 0);
        }
    }
    
    static String stripLeadingAndTrailingQuotes(String str)
          {
              if (str.startsWith("\""))
              {
                  str = str.substring(1, str.length());
              }
              if (str.endsWith("\""))
              {
                  str = str.substring(0, str.length() - 1);
              }
              return str;
          }
    
    /**
     * Save from memory to storage
     */
    public synchronized boolean savePendingFiles() {
        try {
            p("---- Saving Pending Files(db): " + PendingFiles.size());
            
            FileOutputStream fileOut = new FileOutputStream(mStorage);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(PendingFiles);
            out.close();
            fileOut.close();
            return true;
        } catch (FileNotFoundException ex) {
            p("exception> " + ex.toString());
        } catch (IOException ex) {
            p("exception> " + ex.toString());
        }
        return false;
    }

    void printPendingFiles() {
        
        Iterator it = PendingFiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            p(pairs.getKey() + " = " + pairs.getValue());
        }        
    }
    
    
    void processPendingFiles() {

        int nPendingNode = 0;
        int nPendingFile = 0;
        int nFound = 0;
        int nTotal = 0;
        int nNotFound = 0;
        int nWriteError = 0;
        int nSkipped = 0;
        
        try {
            Iterator it = PendingFiles.entrySet().iterator();
            
            while (it.hasNext()) {
                nTotal++;
                log("-----processing pending record: ---------------------------------" + nTotal + "/" + PendingFiles.size(), 1);
                //p("ZZZzzz...[backupclientservice10ms]");
                Thread.sleep(10);
                Map.Entry pairs = (Map.Entry)it.next();
                
                String sKeyTmp = (String)pairs.getKey();
                String sGetPath = (String)pairs.getValue();
                                                
                String sFileName = "";
                String sUUID_pending = "";
                
                p(sKeyTmp + " - " + sGetPath);
                
                int nIndex = sKeyTmp.indexOf(":");
                if (nIndex > 0) {
                    sFileName = sKeyTmp.substring(0,sKeyTmp.indexOf(":"));                
                    sUUID_pending = sKeyTmp.substring(sKeyTmp.indexOf(":")+1,sKeyTmp.length());
                } else {
                    sFileName = sKeyTmp;
                    sUUID_pending = "";
                }
                p("[a]");

                p(sFileName + ":" + sUUID_pending + " = " + sGetPath);

                String sFileExt = sGetPath.substring(sGetPath.lastIndexOf("."),sGetPath.length());
                p("extension = '" + sFileExt + "'");
                
                //Update the IP address and port in the Getpath if update is available
                String sIPPort = "";
                
                p("[b]");

                String sGetPath4 = "";
                String delimiters4 = "/";
                StringTokenizer st4 = new StringTokenizer(sGetPath.trim(),delimiters4, true);
                log("sGetPath: " + sGetPath, 1);
                st4.nextToken(); //http://
                st4.nextToken(); // first /
                st4.nextToken(); // second /
                String sIPPort_file = st4.nextToken(); //
                st4.nextToken(); // third /
                String sFilePath = "";
                while (st4.hasMoreTokens()) {
                    sFilePath += st4.nextToken(); //
                }

                if (sUUID_pending.length() > 0) {

                    sIPPort = (String)NodeAddr.get(sUUID_pending);
                    p("sUUID ip/port = '" + sIPPort + "'");
                    
                    p("sUUID ip/port file = '" + sIPPort_file + "'");
                    String sGetPath_new = "http://" + sIPPort + "/" + sFilePath;
                    p("sGetPath_new = '" + sGetPath_new + "'");
                    sGetPath = sGetPath_new;                                
                }

                p("sGetPath before encode = " + sGetPath);
                sGetPath = sGetPath.replaceAll("\\+", "%2B");
                p("sGetPath after encode = " + sGetPath);
                
                p("sFilePath = " + sFilePath);
                String sFilePath2 = sFilePath.substring(0,sFilePath.lastIndexOf("."));                                

                byte[] sFilePathDecb = Base64.decode(sFilePath2);                
                String sFilePathDec = new String(sFilePathDecb);
                p("sFilePathDec = " + sFilePathDec);
                
                sFilePath = sUUID_pending + "/";
                      
                if (bKeepOrigFileName) {
                    String delimiters2 = "/:";
                    StringTokenizer st2 = new StringTokenizer(sFilePathDec.trim(),delimiters2,true);
                    while (st2.hasMoreTokens()) {
                        sFileName = st2.nextToken();
                        if (st2.hasMoreTokens()) {
                            String sTmp = sFileName.replaceAll(":", "");
                            sFilePath = sFilePath + "/" + sTmp;
                        }
                    }                                                        
                } 
                
                String sStorePath = sBackupPath + sFilePath + "/" + sFileName;
                
                p("sStorePath = " + sStorePath);
                
                p("checkfile");
                boolean exists = checkfile(sStorePath);
                if (!exists) {
                    boolean nodeavail = checknode(sGetPath);
                    if (nodeavail)  {
                        int nres = getfile(sGetPath, sStorePath, 2, 500, 10000);
                        if (nres == 1) {
                            log("file recieved OK. removing from table", 1);
                            //PendingFiles.remove(sFileName);
                            it.remove();
                            nFound++;                            
                        }
                        if (nres == -1) {
                            log("    >>>file not found " + sGetPath, 1);
                            //PendingFiles.put(sFileName, sGetPath);
                            nNotFound++;  
                            it.remove();
                        } 
                        if (nres == -2) {
                            log("    >>>there was an error retrieving file. keeping for later.", 1);
                            //PendingFiles.put(sFileName, sGetPath);
                            nPendingFile++;                                
                        }
                        if (nres == -3) {
                            log("    >>>there was an error writing dest file",1 );
                            //PendingFiles.put(sFileName, sGetPath);
                            nWriteError++;                                
                        }
                    } else {
                        log(">>> node " + sGetPath + " is currently unvailable. keeping for later.",1 );    
                        //PendingFiles.put(sFileName, sGetPath);
                        nPendingNode++;
                    }
                } else {
                    log("file exists now. Skipping and removing from table", 1);
                    //PendingFiles.remove(sFileName);
                    it.remove();
                    nSkipped++;
                }
            }
            
            log("--------------------------------------",1);
            log("#Total Files processed: " + nTotal,1);
            log("#Files processed OK: " + nFound,1);
            log("#Files skipped (file exists): " + nSkipped,1);
            log("#Source Files not found: " + nNotFound,1);
            log("#Dest Write Errors: " + nWriteError,1);
            log("#Pending Files (file): " + nPendingFile,1);
            log("#Pending Files (node): " + nPendingNode,1);
            log("#Pending Files(db): " + PendingFiles.size(),1);
            log("--------------------------------------",1);
            
            
        } catch (Exception ex) {
            p(ex.toString());
            log(ex.getMessage(), 0);
        }
    }
    
    int LoadNodes(String sGetPath, String sFullName) {
        try {            
            int nRes = 0;
            Scanner scanner2 = null;

            bNodesLoaded = false;
            
            if (!bNodesLoaded) {
                //get nodeinfo from server
                p("Source = " + sGetPath);
                p("Dest = " + sFullName);
                //log("loading nodes...",1);
                nRes = getfile(sGetPath, sFullName, 2, 500, 10000);
                if (nRes > 0) bNodesLoaded = true;            
            } else {
                log("#Nodes already loaded: " + NodeAddr.size(),1);
            }

            if (nRes > 0) {            
                FileInputStream bf3 = new FileInputStream(sFullName);
                scanner2 = new Scanner(bf3);
                while (scanner2.hasNextLine()) {    

                    String nLine = scanner2.nextLine();
                    //log("processing Line: '" + nLine + "'", 2);


                    String delimiters = ",";
                    StringTokenizer st = new StringTokenizer(nLine.trim(), delimiters, true);

                    int nTokens = st.countTokens();
                    //log("#tokens: " + nTokens, 2);

                    if (nTokens >= 13) {
                        String sNodeUUID = st.nextToken();
                        st.nextToken();
                        String sNodeIP = st.nextToken();
                        st.nextToken();
                        String sNodePort = st.nextToken();
                        st.nextToken();
                        String sBackup = st.nextToken();
                        st.nextToken();
                        String sFree = st.nextToken();
                        //p("nNodePort = " + sNodePort);
                        log(sNodeUUID + " ---> " + sNodeIP + ":" + sNodePort, 2);
                        NodeAddr.put(sNodeUUID, sNodeIP + ":" + sNodePort);
                    }

                }
                //log("#Nodes loaded: " + NodeAddr.size(), 1);
            }
            return 0;
        } catch (Exception e) {
            return -1;
        }
        
    }
    /**
     * Load from storage into memory
     */
    public boolean loadPendingFiles() {
        try {
            p(mStorage);
            File storage = new File(mStorage);            
            if (!storage.exists())
                return false;
            FileInputStream fileIn = new FileInputStream(mStorage);
            ObjectInputStream in = new ObjectInputStream(fileIn);            
            PendingFiles = (HashMap<String, String>) in.readObject();
            in.close();
            fileIn.close();
            return true;
        } catch (ClassNotFoundException ex) {
            p("exception> " + ex.toString());
        } catch (FileNotFoundException ex) {
            p("exception> " + ex.toString());
        } catch (IOException ex) {
            p("exception> " + ex.toString());
        }
        return false;
    }  
    
    private boolean isHashFile(String sFileName) {
           if (sFileName.indexOf(".") > 0) {
                String sFileName2 = sFileName.substring(0, sFileName.indexOf("."));
                System.out.println("len = " + sFileName2.length());
                if (sFileName2.length() == 32) {                    
                   boolean isHex = sFileName2.matches("[0-9A-F]+");
                   if (isHex) {
                       return true;
                   } else {
                       return false;
                    }
                } else {
                    return false;
                }                            
           } else {
               //not a file with extension
               return false;
           }
       }
    
    public void getServerAddressPort() throws InterruptedException {
           int PORT = 1234;
           byte[] recieveData = new byte[100];
           
           DatagramSocket clientSocket = null;
           DatagramPacket recievePacket = null;
           
           int nRetry = 0;
           
           while (!bHostFound && nRetry <= 5) {                
               boolean doWait = false;
               p("try: " + nRetry);  

               try {
                    //p("[before new socket]");  
                    clientSocket = new DatagramSocket(PORT);
                    //p("[before new packet]");  
                    recievePacket = new DatagramPacket(recieveData, recieveData.length);       

                    mSignature = NetUtils.getSignature();

                    //p("Waiting for probe: " + mSignature);  
                    clientSocket.setSoTimeout(10000);
                    clientSocket.receive(recievePacket);

                    String s = new String(recieveData);
                    //System.out.println("packet data:" + s);

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
                        mHostPort = sHostPort;
                        bHostFound = true;
                    } else {
                        p("signatures don't match. dismissing this probe.");
                        nRetry++;
                    }  
                    p("normal exit1");
                                       
                } catch (BindException e) {
                    p("socket in use. Waiting 1s..."); 
                    Thread.sleep(1000);
                    e.printStackTrace();
                    nRetry++;
                    doWait = true;
                } catch (SocketTimeoutException e) {
                    p("socket timeout. carrying on...");
                    e.printStackTrace();
                    nRetry++;
                    doWait = true;
                } catch (Exception e) {
                    p("there was some kind of exception");    
                    e.printStackTrace();
                    nRetry++;
                    doWait = true;
                } finally {
                    //p("finally1...");
                                        
                    if (doWait) {
                        Random generator = new Random();
                        int roll = generator.nextInt(1000) + 1;                   
                        p("Waiting " + (1000+roll) + "ms..."); 
                        Thread.sleep(1000 + roll);
                    }

                    if (clientSocket != null) {
                        //p("finally2a...");
                        if (clientSocket.isConnected()) {
                            //p("isconnected...");
                            clientSocket.disconnect();                        
                        }
                        //p("finally2b...");
                        if (clientSocket.isBound()) {
                            //p("isbound...");
                            clientSocket.close();                        
                        }                        
                    }
                    //p("finally3...");
                }
           }
           //p("normal exit2");
    }
    
}

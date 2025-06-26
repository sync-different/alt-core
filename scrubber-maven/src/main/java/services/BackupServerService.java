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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.Scanner;
import utils.Cass7Funcs;
import static utils.Cass7Funcs.keyspace;
import utils.LocalFuncs;
import static utils.LocalFuncs.get_row_attribute;
import utils.NetUtils;

public class BackupServerService implements Runnable  {

    static String DB_MODE = "cass";    
    PrintStream log = null;
    String LOG_NAME = "logs/backup_server.txt";
    Thread t;
    
    static String sMode;
    static String sServerPath;
    static String sLocalPath;
    static String sBackupPath;
    static String sSyncPath;
    static String sSleep = "3000";
    static String sSleepServerLong = "3600000";
    static String sSleepServerShort = "15000";
    static String sRoot;
    static String sNewseq = "false";
    static String sTestRangeStart = "";
    static String sTestRangeEnd = "";
    static String sSleepPending = "300";
    static Integer mREPLICATION_FACTOR = 3;  //keep 3 copies of files by default

    protected static Properties props = new Properties();
    static boolean shutdown = false;
    
    static Cass7Funcs c7 = new Cass7Funcs();
    static LocalFuncs c8 = new LocalFuncs();
    
    static String mCassIP = "localhost";
    /**
     * @param args the command line arguments
     */
    static HashMap PendingFiles  = new HashMap <String,String>();
    static HashMap NodeAddr = new HashMap <String, String>();
    
    static String mStorage = "./pending.db";
    static int mLogLevel = 0;
    
    public boolean mTerminated = false;
    
    static int COUNT_INCOMING = 100;
    static int COUNT_IDX = 3;
    static int COUNT_NTIMES = 4;    // 4 samples
    static int COUNT_DELAY = 10000; //10 seconds delay between samples
    
    public BackupServerService(Boolean _dothread, int _loglevel) {
        try {
            log = new PrintStream(new BufferedOutputStream(
                            new FileOutputStream(LOG_NAME,true)));
            log("opening log file: " + LOG_NAME, 0);
            
            mLogLevel = _loglevel;

            if (_dothread) {
              // Create a new, second thread
              t = new Thread(this, "sc_a");
              p("Child thread: " + t);
              t.start(); // Start the thread          
            }
      } catch (Exception e) {
          e.printStackTrace();
      }
   }    
    
     public void terminate() {
       
       System.out.println("Recieved Termination request.");
       mTerminated = true;       
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
    
    /* print to stdout */
    protected void p(String s) {

        long threadID = Thread.currentThread().getId();
        System.out.println("[backup_server_" + threadID + "] " + s);
    }
    
    
    public void run() {
        
        try {
            
            while (!mTerminated) {
                
                p("loadProps()");
                loadProps();
                p("loadPropsDB()");
                loadPropsDB();
                p("printProps()");
                printProps();

                //int nTimes = 8;
                int i = 0;
                int nIdx = 0;
                //int nIdxTotal = 0;
                int nIncoming = 0;
                //int nIncomingTotal = 0; 
                int nIdxMax = 0;
                int nIncomingMax = 0;
                boolean bRun = false;
                
                nIdx = getNumberOfIDXFiles();    
                nIncoming = getNumberOfIncomingFiles();
                
                int nSamples = (nIdx / 3) + (nIncoming / 100) + COUNT_NTIMES;
                
                log("Sample rate: " + nSamples, 2);
                
                while (i < nSamples ) {
                    nIdx = getNumberOfIDXFiles();    
                    nIncoming = getNumberOfIncomingFiles();
                    nIdxMax = Math.max(nIdx, nIdxMax);
                    nIncomingMax = Math.max(nIncoming, nIncomingMax);
                    //nIdxTotal += nIdx;
                    //nIncomingTotal += nIncoming;
                    p("Idx Sample(" + i + ") idx:" + nIdx + " incoming:"  + nIncoming + " idxmax:" + nIdxMax + " incomingMax: " + nIncomingMax);
                    Thread.sleep(COUNT_DELAY);
                    i++;
                }
                if ((nIdxMax) <= COUNT_IDX && (nIncomingMax <= COUNT_INCOMING)) {
                    bRun = true;
                } else {
                    log("Waiting for " + sSleepServerShort + "ms. #IDX: " + nIdxMax + " #Incoming: " + nIncomingMax, 2);
                    try {                        
                        Thread.sleep(Long.parseLong(sSleepServerShort));                        
                    } catch (Exception e) {
                       e.printStackTrace();
                    }
                }

                if (bRun) {
                    p("main_server()");
                    main_server();
                    p("checkGenFiles()");
                    checkGenFiles();   
                    long lSleep = Long.parseLong(sSleepServerShort);
                    p("going to short sleep for: " + lSleep);
                    try {
                        Thread.sleep(lSleep);
                    } catch (Exception ex) {
                    }

                }
            }            
            
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    int getNumberOfIncomingFiles() {
        try {
            File tf = new File("../rtserver/incoming/");
            File[] files = tf.listFiles();
            int nIncoming = 0;
            for (File f:files) {
                if (f.isFile()) nIncoming++;
            }
            return nIncoming;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    
    boolean isBackupBatch(String _filename) {
        
        p("----------------------isbackupbatch-----?: " + _filename);
        HashMap<String, Integer> occurences_uuid = new HashMap<String, Integer>();
        c8.set_backupnodes(keyspace, "NodeInfo","nodes", occurences_uuid, "backup");

        p("Nodes loaded: " + occurences_uuid.size());       
        String sIdx = _filename.substring(_filename.indexOf("_")+1, _filename.indexOf("."));        
        
        p("sIdx = " + sIdx);
        
        String sUUID = c8.get_row_attribute("keyspace", "BatchJobs", sIdx, "uuid", null);
        if (sUUID.length() > 0) {
            Object Got = null;
            if ((Got = occurences_uuid.get(sUUID)) != null) {
                p(sUUID + " is a backup UUID.");
                return true;
            } else {
                p(sUUID + " is NOT a backup UUID.");
                return false;
            }            
        } else {
            p("empty sUUID");
            return false;
        }
    }
    
    int getNumberOfIDXFiles() {
        try {
            File tf = new File("../rtserver/");
            File[] files = tf.listFiles();
            int nIDX = 0;
            for (File f:files) {
                if (f.isFile() && f.getName().contains(".idx") && !isBackupBatch(f.getName())) nIDX++;
            }
            return nIDX;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    
    public void checkGenFiles(){
        try{
            int res = 0;

            if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {
                connectCassandra();                
            } else {
                System.out.println("Skip cassandra connect. P22 mode");
            }

            File dir = new File(sRoot);

            if (dir.exists()){
                File[] files = dir.listFiles();
                for (File file: files){
                    if (file.isDirectory())
                        continue;
                    if (file.getName().contains(".gen")) {                        
                        //Check if the file has already been generated.
                        File tmpFile1 = new File(sRoot + file.getName().substring(0, file.getName().length() - 4));
                        if (tmpFile1.exists()) {
                            if (tmpFile1.exists()) {
                                log("backup file already exists:" + tmpFile1.getCanonicalPath(),2);
                            }
                            log("Already processed file, deleting .gen" + file.getCanonicalPath(),2);
                            file.delete();
                        } else { 
                            //Check if tmp file exists, and if so delete it.
                            File tmpFile2 = new File(sRoot + file.getName().substring(0, file.getName().length() - 4) + ".tmp");
                            if (tmpFile2.exists()) {
                                log("tmp file exists:" + tmpFile2.getCanonicalPath(),2);
                                boolean bres = tmpFile2.delete();
                                log("tmp file delete: " + bres,2);
                            }

                            //Now process the .gen file
                            log("Processing GEN file " + file.getName(),2);
                            
                            String sBatchId = "";                            
                            try {
                                sBatchId = file.getName().substring(file.getName().lastIndexOf("_") + 1, file.getName().lastIndexOf(".gen"));
                            } catch (Exception e) {
                                log("WARNING: Exception when trying to parse batchid:" + file.getName(),2);
                            }                            
                            
                            String sSeqId = "0";
                            try {
                                sSeqId = file.getName().substring(file.getName().indexOf("_",8) +1, file.getName().lastIndexOf("_"));
                            } catch (Exception e) {
                                log("WARNING: Exception when trying to parse seqid:" + file.getName(),2);
                            }
                            
                            log("batch:" + sBatchId + " seq:" + sSeqId, 2);
                            
                            if (sBatchId.length() > 0) {
                                //NEW batch File to process
                                String sBatch = "batch:" + sBatchId;

                                String dbBatchid = "";
                                if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {
                                    dbBatchid = c7.get_batch_id("batchid","BatchJobs", "idx");                                
                                } else {
                                    dbBatchid = c8.get_batch_id("batchid","BatchJobs", "idx");                                                                
                                }
                                
                                log("db batch id:" + dbBatchid, 2);
                                
                                String sBatchUUID = c8.get_row_attribute("keyspace", "BatchJobs", sBatchId, "uuid", null);
                                
                                log("[gen] Batch UUID: " + sBatchUUID, 2);

                                if (Integer.parseInt("0" + dbBatchid) >= Integer.parseInt("0" + sBatchId)) {
                                    if (DB_MODE.equals("cass") || DB_MODE.equals("both")) { 
                                        res = c7.backupObjects(sBatch, Long.parseLong(sBatchId), sRoot, sSeqId, mREPLICATION_FACTOR);                                   
                                    } else {                                    
                                        res = c8.backupObjects(sBatch, Long.parseLong(sBatchId), sRoot, sSeqId, mREPLICATION_FACTOR, mLogLevel, sBatchUUID);                                   
                                    }
                                    log("res backupobjects[1]:" + res, 2);
                                } else {
                                    p("GEN Nothing to process. dbBatchid=" + dbBatchid + " sBatchId=" + sBatchId);
                                }    
                            }
                            log("res backupobjects[2]:" + res, 2);
                            //If there was an error generating file requested by .GEN , generate blank file.
                            if (res < 0) {        
                                String sFileName = sRoot + tmpFile1.getName();
                                log("Generating blank filer: " + sFileName, 2);
                                String sRow = "0,0,0";
                                try { 
                                    FileWriter fw = new FileWriter(sFileName, false);
                                    BufferedWriter out = new BufferedWriter(fw);
                                    out.write(sRow);
                                    out.close();                                
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    log("WARNING: There was an exception trying to save the filer: " + sFileName,2);
                                }
                            } 
                            
                            //Delete the .GEN file if it still exists.
                            if (file.exists()) {
                                boolean bRes = file.delete();
                                log("Deleting GEN file " + file.getName() + " status deleted: " + bRes, 2);
                            }                           
                        } // else / process .gen file
                    } // check if .gen
                } // for 
            } // if dir exists
        } //try
        catch (Exception ex) {
            ex.printStackTrace();
            log("WARNING: Exception GEN checkGenFiles error: " + ex.getMessage(), 2);
        }
    }
    
    public void main_server() throws IOException {

        String keyspace = "Keyspace1b";


        p("running server mode");

        try {
            Long nStart;
            Long nEnd;
            Long nSeq;

            String _hashkey = "paths";
            Integer batchnum = 1;
            String batchid = "";
            String backupid = "";
            String seqid = "";
            
            if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {
                connectCassandra();
                batchid = c7.get_batch_id("batchid","BatchJobs", "idx");
                backupid = c7.get_batch_id("backupid","BackupJobs", "idx2");
                seqid = c7.get_batch_id("backupid","BackupJobs", "idx3");
            } else {
                //P2P mode
                batchid = c8.get_batch_id("batchid","BatchJobs", "idx");
                backupid = c8.get_batch_id("backupid2","BackupJobs", "idx2");
                seqid = c8.get_batch_id("backupid3","BackupJobs", "idx3");                
            }

            p("last id batch stored : " + batchid);
            p("last backup id batch stored : " + backupid);
            p("last sequence stored : " + seqid);
            
            if (batchid.length() == 0 || batchid.equals("ERROR")) {
                batchid = "0";
            }

            if (backupid.length() == 0 || backupid.equals("ERROR")) {
                backupid = "0";
            }
            
            if (seqid.length() == 0 || seqid.equals("ERROR")) {
                seqid = "0";
            }
                                    
            nStart = Long.parseLong(backupid) + 1;           
            nEnd = Long.parseLong(batchid);                        
            nSeq = Long.parseLong(seqid);

            p("start: " + nStart);
            p("end: " + nEnd);
            
            long nLast = 0;
            
            boolean bWork = false;
            for (long i=nStart;i<=nEnd;i++) {
                String sBatch = "batch:" + i;
                p("----backup batch:" + sBatch);
                if (i < nEnd) {
                    log("----backup batch:" + sBatch + " " + i, 1);
                    int res = 0;
                    if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {
                        res = c7.backupObjects(sBatch,i,sRoot, seqid, mREPLICATION_FACTOR);                        
                    } else {
                        String sBatchUUID = c8.get_row_attribute("keyspace", "BatchJobs", String.valueOf(i), "uuid", null);
                        log("[main] Batch UUID: " + sBatchUUID, 2);
                        res = c8.backupObjects(sBatch,i,sRoot, seqid, mREPLICATION_FACTOR, mLogLevel, sBatchUUID);                                                
                    }
                    p("BackupObjects res = " + res);
                    if (res < 0) {
                        log("WARNING: BackupObjects returned an error: " + res, 2);                        
                    }
                    //bWork = true;
                    nLast = i;
                    
                    log("***** DONE with Batch - Storing batch#" + nLast, 1);
                            
                    // new *** if stores succesfully, store the last point so next time we can start here. 
                    String batchid_store = Long.toString(nLast);
                    p("claiming batch id# " + batchid_store);
                    
                    if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {
                        backupid = c7.get_batch_id("backupid", "BackupJobs", "idx2");                        
                    } else {
                        backupid = c8.get_batch_id("backupid2", "BackupJobs", "idx2");                                                
                    }
                    p("last backup id batch stored : " + backupid);
                    
                    p("----storing id of last batch:" + batchid_store);
                    if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {
                        int nRes = c7.insert_column(keyspace, "BackupJobs", "backupid", "idx2", batchid_store);
                    } else {
                        int nRes = c8.insert_column(keyspace, "BackupJobs", "backupid2", "idx2", batchid_store, false);                        
                    }

                    if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {
                        backupid = c7.get_batch_id("backupid", "BackupJobs", "idx2");
                    } else {
                        backupid = c8.get_batch_id("backupid2", "BackupJobs", "idx2");                        
                    }
                    p("last backup id batch stored : " + backupid);

                    
                }  else {
                    //if it's the last one, check if finished
                    if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {                    
                        boolean bBatchFinished = c7.checkBatch(i);
                        if (bBatchFinished) {
                            int res = c7.backupObjects(sBatch,i,sRoot, seqid, mREPLICATION_FACTOR);
                            p("BackupObjects res = " + res);
                            if (res < 0) return;
                            bWork = true;
                            nLast = i;
                        } else {
                            p("last batch is not finished:"  + i);     
                        }
                    } else {
                        //P2P
                        boolean bBatchFinished = c8.checkBatch(i);
                        if (bBatchFinished) {
                            String sBatchUUID = c8.get_row_attribute("keyspace", "BatchJobs", String.valueOf(i), "uuid", null);
                            log("[main] Batch UUID[b]: " + sBatchUUID, 2);
                            int res = c8.backupObjects(sBatch,i,sRoot, seqid, mREPLICATION_FACTOR, mLogLevel, sBatchUUID);
                            p("BackupObjects res = " + res);
                            if (res < 0) return;
                            bWork = true;
                            nLast = i;
                        } else {
                            p("last batch is not finished:"  + i);     
                        }

                    }
                }
            }

            if (bWork) {
                
                p("***** DONE with LAST Batch - Storing batch#" + nLast);

                String batchid_store = Long.toString(nLast);
                p("claiming batch id# " + batchid_store);

                if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {                    
                    backupid = c7.get_batch_id("backupid", "BackupJobs", "idx2");
                } else {
                    backupid = c8.get_batch_id("backupid2", "BackupJobs", "idx2");                    
                }
                p("last backup id batch stored : " + backupid);

                p("----storing id of last batch:" + batchid_store);
                
                int nRes = 0;
                if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {                    
                    nRes = c7.insert_column(keyspace, "BackupJobs", "backupid", "idx2", batchid_store);
                } else {                    
                    nRes = c8.insert_column(keyspace, "BackupJobs", "backupid2", "idx2", batchid_store, false);
                }

                if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {                    
                    backupid = c7.get_batch_id("backupid", "BackupJobs", "idx2");
                } else {
                    backupid = c8.get_batch_id("backupid2", "BackupJobs", "idx2");                    
                }
                p("last backup id batch stored : " + backupid);

                p("nres:" + nRes);
            } else {
                p("skipping there was no work.");       
            }
            
            p("__backupid: " + backupid);
            p("__batchid: " + batchid);
            int nbackupid = Integer.parseInt(backupid);
            int nbatchid = Integer.parseInt(batchid);
            p("__nbackupid: " + nbackupid);
            p("__nbatchid: " + nbatchid);

            if (nbackupid >= nbatchid) {
                p("reached end of batches. ");
                if (sNewseq.equals("true")) {
                    
                    //sleep before updating batch squence
                    long lSleep = Long.parseLong(sSleepServerLong);
                    p("going to long sleep for: " + lSleep);
                    try {
                        Thread.sleep(lSleep);
                    } catch (Exception ex) {
                    }
                    
                    //update sequence
                    int nRes;
                    nSeq += 1;
                    p("updating sequence to: " + nSeq);
                    String seqid_store = Long.toString(nSeq);
                    if (DB_MODE.equals("cass") || DB_MODE.equals("both")) {                    
                        nRes = c7.insert_column(keyspace, "BackupJobs", "backupid", "idx3", seqid_store);
                        nRes = c7.insert_column(keyspace, "BackupJobs", "backupid", "idx2", "0");                        
                    } else {
                        //P2P
                        nRes = c8.insert_column(keyspace, "BackupJobs", "backupid3", "idx3", seqid_store, false);
                        nRes = c8.insert_column(keyspace, "BackupJobs", "backupid2", "idx2", "0", false);                        
                    }
                    
                }
            } else {
                    p("pending batch in progress. ");
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
            log(ex.getMessage(),0);
        }
    }
    
    /**
     * Connect to Cassandra Server
     */
    private void connectCassandra() {
        try {
            boolean bConnect = false;
            while (!bConnect) {
                p("Connecting to Cassandra...");
                bConnect = c7.connect(mCassIP,9160);
                p("connect result = " + bConnect);
                if (!bConnect) {
                    long lSleep = 10000;
                    p("connect failed. going to sleep for " + lSleep + " ms");
                    Thread.sleep(lSleep);
                }
            }
        } catch (Exception ex) {
            p("Exception connecting to cassandra:" + ex.getMessage());
            //System.exit(-1); pp
        }
    }
    
    
    void loadPropsDB() throws IOException {

        String r;

        File f = new File
                ("config"+
                File.separator+
                "www-processor.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
            
            r = props.getProperty("dbmode");
            if (r != null) {
                DB_MODE = r;
            }
            
        }
   
    }
    
    /* load www-server.properties from java.home */
    void loadProps() throws IOException {

        String r;

        p(System.getProperty("java.home"));
        File f = new File
                ("config"+
                File.separator+
                "www-rtbackup.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
            
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

            r = props.getProperty("localpath");
            if (r != null) {
                sLocalPath = r;
            }

            r = props.getProperty("backuppath");
            if (r != null) {
                sBackupPath = r;
            }

            r = props.getProperty("syncpath");
            if (r != null) {
                sSyncPath = r;
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

            r = props.getProperty("rfactor");
            if (r != null) {
               try {
                   mREPLICATION_FACTOR = Integer.parseInt(r);
               } catch (Exception e) {
                   log("WARNING: Exception converting rfactor.",0);
               }
            }

            r = props.getProperty("pending");
            if (r != null) {
                mStorage = r;
            }
            r = props.getProperty("count_idx");
            if (r != null) {
                COUNT_IDX = Integer.parseInt(r);
            }

            r = props.getProperty("count_ntimes");
            if (r != null) {
                COUNT_NTIMES = Integer.parseInt(r);
            }

            r = props.getProperty("count_delay");
            if (r != null) {
                COUNT_DELAY = Integer.parseInt(r);
            }

            r = props.getProperty("count_incoming");
            if (r != null) {
                COUNT_INCOMING = Integer.parseInt(r);
            }
            
            
        }
    }

    void printProps() {
        p("mode = '" + sMode + "'");
        p("root = '" + sRoot + "'");
        p("serverpath = '" + sServerPath + "'");
        p("localpath = '" + sLocalPath + "'");
        p("backuppath = '" + sBackupPath + "'");
        p("syncpath = '" + sSyncPath + "'");
        p("sleep_client = '" + sSleep + "'");
        p("sleep_server_short = '" + sSleepServerShort + "'");
        p("sleep_server_long = '" + sSleepServerLong + "'");
        p("sleep_pending = '" + sSleepPending + "'");
        p("newseq = '" + sNewseq + "'");
        p("rfactor = '" + mREPLICATION_FACTOR + "'"); 
        p("pending = '" + mStorage + "'"); 
        p("count_idx = '" + COUNT_IDX + "'"); 
        p("count_incoming = '" + COUNT_INCOMING + "'"); 
        p("loglevel = '" + mLogLevel + "'");
    }


}

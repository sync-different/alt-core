/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package services;

//import communication.DiscoveryClient;
//import communication.TCPCrawlerClient;

//import io.Log;
//import io.FileFunctions;

//import thread.ThreadCustom;
//import timing.TimeConstant;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.text.SimpleDateFormat;

import utils.Cass7Funcs;
import utils.sURLPack;
import utils.LocalFuncs;
import utils.WebFuncs;

//import application.AppVariables;
//import app.Settings;
//import data.FileRecord;
//import data.FileRecordIO;

import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import java.io.FileWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;

import java.util.Scanner;
import java.util.Properties;

import java.util.StringTokenizer;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.NotFoundException;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.thrift.TException;


import processor.RecordStats;
import processor.DatabaseEntry;

import processor.ZipFolder;
import static utils.LocalFuncs.get_row_attribute;
import utils.NetUtils;

public class ProcessorService implements Runnable{

    static boolean bConsole = true;

    Cass7Funcs c7 = new Cass7Funcs();
    LocalFuncs c8 = null;
    static WebFuncs wf = null;
    static String LocalIP = "";
    static String mServerIP = "localhost";
    static String mServerport = "";
    public static String keyspace = "Keyspace1b";

    protected static Properties props = new Properties();
    private static int mMinSubstrLen = 4;
    private static int mMaxStrLen = 40;
    private static boolean mAutoComplete = true;
    private static boolean mForceIndex = false;
    
    /*Fields*/
    private long mPeriodMs;
    private String mScanDirectory;
    
    private long mFiles;
    private long mDelay;
   
    //private static RecordStats mStatistics;
    private Date ts_start;
    private String batchid_store;
    private static final SimpleDateFormat DateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
    private static String THUMBNAIL_OUTPUT_DIR = "../cass/pic"; //Settings.getString(AppVariables.PROPERTY_SERVER_OUTPUT_THUMBNAIL);
    private String sUUID;
    private Boolean newbatch = false;
    private Boolean mTerminated = false;

    public static String columnFamilyBatch = "BatchJobs";
    
    static String LOG_NAME = "processor.txt";
    static String LOG_PATH = "logs/";
    static PrintStream log= null;
    
    static String mUUID = "";
    static String mUUID2 = "";
    static String mUUID3 = "";
    static String mUUID4 = "";
    
    static String mUUIDPath = "";
    
    static String DB_MODE = "cass"; //assume cassandra db by default     
    
    static String delimiters = " !@#$%^&*()-=_+[]\\{}|;':\",./<>?";
    
    static int RECORDS = 100;

    static int mLogLevel = 0;
    String mCONFIG_PATH = "";
    
    public void SetShutdown() {
        mTerminated = true;
    }       
    
    static void loadWebFuncs() {
        if (wf == null) {
            InetAddress clientIP = null;
            try {
                clientIP = InetAddress.getLocalHost();                
            } catch (Exception e) {
                e.printStackTrace();
            }
            LocalIP = clientIP.getHostAddress();

            p("LocalIP: " + LocalIP);                        
            wf = new utils.WebFuncs(LocalIP);                  
        } else {
            p("wf already loaded.");
        }
   }

    /**
     * Constructor
     */
    public ProcessorService(long _period_ms, 
            String _scan_directory, 
            long _nfiles, 
            long _ndelayms, 
            String _hostname, 
            String _server_port,
            int _loglevel, 
            String _configpath) {
        //setName("Client Service");
        mPeriodMs = _period_ms;
        mScanDirectory = _scan_directory;
        mFiles = _nfiles;
        mDelay = _ndelayms;
        mServerIP = _hostname;
        mServerport = _server_port;
        mLogLevel = _loglevel;
        mCONFIG_PATH = _configpath;
        
        Thread t;

        try {
          loadBackupProps();
          String sLog = LOG_PATH + LOG_NAME;
          log = new PrintStream(new BufferedOutputStream(
                            new FileOutputStream(sLog,true)));
          log("opening log file: " + sLog, 0);
          
          loadDelimiters();          
          loadProps();
          loadLocalUUID();
          printProps();
          
          //mUUID = NetUtils.getUUID(mUUIDPath);
          p("Server UUID = " + mUUID);          
          p("Server UUID2 = " + mUUID2);
          p("Server UUID3 = " + mUUID3);
          p("Server UUID4 = " + mUUID4);

          if (DB_MODE.equals("p2p") || (DB_MODE.equals("both"))) {
              p("Calling c8 constructor...");
              c8 = new LocalFuncs();     
              //c8.loadIndexMapDB(false,true);
          }                
              
        //loadWebFuncs();
        
        t = new Thread(this, "Demo Thread");
        System.out.println("Child thread: " + t);
        t.start(); // Start the thread
        
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    public void terminate() {
       
       log("Recieved Termination request.", 0);
       mTerminated = true;       
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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [ProcessorService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO] [ProcessorService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [ProcessorService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected static void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate+ "[DEBUG] [processor_" + threadID + "] " + s);
    }

      /* print to the log file */
        protected static void log(String s, int _loglevel) {

        if (_loglevel <= mLogLevel) {
            Date ts_start = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
            String sDate = sdf.format(ts_start);

            try {
                if (log!=null)
                    synchronized (log) {
                        log.println(sDate + " " + _loglevel + " " + s);
                        log.flush();
                    } 
                else
                    pw("Log is null. Skipping Log Entry...");
                pi(_loglevel + " " + s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }        
    }

/**
     * Run Service
     */
    //@Override
    //@SuppressWarnings("SleepWhileHoldingLock")
    public void run() {
        try {
            while (!mTerminated) {
            //-Start scanning
            log("[PROCESSOR] Start scanning directory.", 0);
            //RecordStats mStatistics = new RecordStats();
            ScanProcessingDir();

            //-Store batch info
            //System.out.println("StoreBatchinfo");
            //int nres = storeBatchInfo(mStatistics);
            //System.out.println("nres StoreBatchinfo:" + nres);

            //CheckforIDX();
            
            //-Wait for next run
            p("[PROCESSOR] Waiting for next sweep (processor)");
            Thread.sleep(mPeriodMs);
            log("run Sleep for " + mPeriodMs, 2);
            //mTerminated = true;                
            }
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
        log("Processor completed.", 0);
    }
    
    
    static void printProps() {
        p("minsubstr=" + mMinSubstrLen);
        p("maxstrlen=" + mMaxStrLen);
        p("autocomplete=" + mAutoComplete);
        p("forceindex=" + mForceIndex);
        p("thumbnaildir=" + THUMBNAIL_OUTPUT_DIR);
        p("dbmode=" + DB_MODE);   
        p("delimiters= " + delimiters);
        p("uuidpath= " + mUUIDPath);        
        p("logpath= " + LOG_PATH);
    }

    void loadDelimiters() {
        try {
            File f = new File("config/delimiters.txt");
            if (f.exists()) {
                Scanner sc = new Scanner(f);
                delimiters = sc.nextLine();
                sc.close();
            } else {
                p("Delimiter file does not exist. Assuming defaults: " + delimiters);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean isLocalUUID(String _uuid) {
        if (_uuid.equalsIgnoreCase(mUUID) 
                || _uuid.equalsIgnoreCase(mUUID2) 
                || _uuid.equalsIgnoreCase(mUUID3)
                || _uuid.equalsIgnoreCase(mUUID4)) {
            return true;
        } else {
            return false;            
        }
    }
    
    void loadLocalUUID() {
        mUUIDPath = NetUtils.getConfig("uuidpath","./config/www-rtbackup.properties");
        mUUID = NetUtils.getUUID(mUUIDPath);
        mUUIDPath = NetUtils.getConfig("uuidpath","./config/www-rtbackup2.properties");
        mUUID2 = NetUtils.getUUID(mUUIDPath);
        mUUIDPath = NetUtils.getConfig("uuidpath","./config/www-rtbackup3.properties");
        mUUID3 = NetUtils.getUUID(mUUIDPath);
        mUUIDPath = NetUtils.getConfig("uuidpath","./config/www-rtbackup4.properties");
        mUUID4 = NetUtils.getUUID(mUUIDPath);
    }
    
    void loadBackupProps() throws IOException {  
        //System.out.println(System.getProperty("java.home"));
        System.out.println("loadBackkupProps()");
        File f = new File(mCONFIG_PATH);
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
        
            //String r = props.getProperty("uuidpath");
            //if (r != null) {
            //    mUUIDPath = r;
            //}            
            
            String r = props.getProperty("logpath");
            if (r != null) {
                LOG_PATH = r;
            }
            
        }

    }
    
    void loadProps() throws IOException {
        System.out.println(System.getProperty("java.home"));
        File f = new File
                (
                "config"+
                File.separator+
                "www-processor.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
            String r = props.getProperty("minsubstr");
            if (r != null) {
                mMinSubstrLen = Integer.parseInt(r);
            }
            r = props.getProperty("maxstrlen");
            if (r != null) {
                mMaxStrLen = Integer.parseInt(r);
            }
            r = props.getProperty("autocomplete");
            if (r != null) {
                mAutoComplete = Boolean.valueOf(r);
            }
            r = props.getProperty("forceindex");
            if (r != null) {
                mForceIndex = Boolean.valueOf(r);
            }

            r = props.getProperty("thumbnaildir");
            if (r != null) {
                THUMBNAIL_OUTPUT_DIR = r;
            }

            r = props.getProperty("dbmode");
            if (r != null) {
                DB_MODE = r;
            }
            
            r = props.getProperty("records");
            if (r != null) {
                RECORDS = Integer.parseInt(r);
            }
        }
    }
    
    
    /**
     * Scan processing directory
     */
    private void ScanProcessingDir() {
        RecordStats mStatistics = new RecordStats();
        
        int process_files = 0;
        String sUUIDprocess = "";

        try { 
            loadProps();
            printProps();

        } catch (Exception IOException) {
            
        }
        
        p("directory: " + mScanDirectory);
        File processing_dir = new File(mScanDirectory);
        if (processing_dir.exists()){
            File[] files = processing_dir.listFiles();
            for (File file: files){
                if (file.isDirectory())
                    continue;
                final String filename = file.getName();
                if (filename.contains(".zip")) {
                    
                    //unzip packed file.
                    try {
                        p("Processing ZIP file " + filename);
                        p("Processing ZIP file " + file.getAbsolutePath());
                        p("Processing ZIP file " + file.getCanonicalPath());

                        ZipFolder zipper = new ZipFolder();
                        int res = zipper.unzipFile(file.getCanonicalPath(), mScanDirectory);                                                
                        p("Zip res:" + res);
                        if (res > 0) {
                            p("Unzip OK. deleting...");
                            file.delete();
                        } else {
                            log("WARNING : Unzip error file:" + filename, 0);
                            try {
                                if (file.delete()) {
                                    p("file corrupt zip delete OK");                                    
                                } else {                                    
                                    p("file corrupt zip delete FAIL");                                    
                                }
                            } catch (OutOfMemoryError oome) {
                                oome.printStackTrace();
                                if (mStatistics.nFiles == RECORDS) {
                                    storeBatchInfo(mStatistics);
                                    mStatistics = new RecordStats();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }                      
                } else if (filename.endsWith(".b") || filename.endsWith(".p")) {//.b
                    File dMobileBackup = new File("../scrubber/mobilebackup");
                    if(!dMobileBackup.exists()){
                        dMobileBackup.mkdir();
                        addMobileBackupFolder();
                    }
                    String[] filenametokens = filename.split("\\.");
                    String deviceid = filenametokens[0];

                    String name = "";
                    p("size of array:" + filenametokens.length);
                    name = filenametokens[1];
                    int ign = 1;
                    if (filename.endsWith(".p")) {
                        ign = 3;
                    }
                    for (int i = 2; i < filenametokens.length-ign; i++) {
                        p("filenametokens[" + i + "] = " + filenametokens[i]);
                        name = name += "." + filenametokens[i];
                    }

                    p("Name of file = " + name);
                    //String name = filenametokens[1] + "." + filenametokens[2];

                    if (filename.endsWith(".b")) {
                        try {
                            //5F18AA9A-9425-45D4-8B30-546AB347F7C3.IMG_0004.JPG.24C1066A-A0AD-44F8-801E-83B615161CB3.JPG
                            File folderDevice = new File(dMobileBackup.getAbsolutePath() + "/" + deviceid);
                            if(!folderDevice.exists()){
                                folderDevice.mkdir();
                            }                               
                            Files.move(file.toPath(), (new File(folderDevice.getAbsolutePath() + "/" + name)).toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException ex) {
                            Logger.getLogger(ProcessorService.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }else{
                        // case .p
                        p("Case Partial");
                        p("filenametokens len: " + filenametokens.length);
                        int totalparts = 0;
                        int nropart = 0;
                        try {
                            totalparts = Integer.parseInt(filenametokens[filenametokens.length-3]);
                            nropart = Integer.parseInt(filenametokens[filenametokens.length-2]);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if(totalparts == nropart){
                            //final part arrived, time to build large file
                            File ofile = new File(mScanDirectory + "/" + deviceid + "." + name + ".b");

                            p("output large file = " + ofile.getAbsolutePath());
                            p("name = '" + name + "'");

                            FileOutputStream fos;
                            FileInputStream fis;
                            byte[] fileBytes;
                            try {
                                fos = new FileOutputStream(ofile,true);
                                for (int i = 1; i<=totalparts; i++) {
                                    String filePartName = mScanDirectory + "/" + deviceid + "." + name + "." + totalparts + "." + i + ".p";
                                    p("file part name: " + filePartName);
                                    File filePart = new File(filePartName);
                                    if (filePart.exists()) {
                                        p("file exists: " + filePart.getAbsolutePath());
                                        fis = new FileInputStream(filePart);
                                        fileBytes = new byte[(int) filePart.length()];
                                        fis.read(fileBytes, 0,(int)  filePart.length());
                                        fos.write(fileBytes);
                                        fos.flush();
                                        fileBytes = null;
                                        fis.close();
                                        fis = null;
                                        filePart.delete();
                                    } else {
                                        pw("WARNING: file part does not exist: " + filePart.getAbsolutePath());
                                    }
                                }
                                p("Finalizing file merge. Full file at: " + ofile.getAbsolutePath());
                                fos.close();
                                fos = null;
                            }catch (Exception exception){                                
                                exception.printStackTrace();
                                pw("WARNING - there was an exception merging the file." + ofile.getAbsolutePath());
                            }
                        }
                    }
                }else {//if (filename.endsWith(".a") || filename.endsWith(".d") ) {//.a.d
                    p("Processing .a or .d file " + filename);
                    //process regular file
                    String sUUID = filename.substring(0,filename.indexOf("."));
                    p("UUID:'" + sUUID + "'");
                    if (process_files == 0) {
                        //first time, store UUID
                        sUUIDprocess = sUUID;
                    }
                    if (sUUID.equals(sUUIDprocess)) {
                            //if file belongs to batch, process it

                            //FileRecord file_record= FileRecordIO.load(file);

                            DatabaseEntry file_record = null;
                            try {
                                FileInputStream fileIn = new FileInputStream(file);
                                ObjectInputStream in = new ObjectInputStream(fileIn);
                                file_record = (DatabaseEntry) in.readObject();
                                in.close();
                            } catch (OutOfMemoryError oome) {
                                oome.printStackTrace();
                                if (mStatistics.nFiles == RECORDS) {
                                    storeBatchInfo(mStatistics);
                                    mStatistics = new RecordStats();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            if (file_record != null){
                                //there are files, start processing a new batch
                                newbatch = true;
                                
                                boolean res = false;
                                
                                if ("DELETE".equals(file_record.dbe_action)) {
                                    //TODO: falta implementar delete para caso P2P
                                    if (DB_MODE.equals("cass")) {
                                        res = processDeleteFileRecordData_cass(file_record, mStatistics);
                                    }
                                    if (DB_MODE.equals("p2p")) {
                                        res = processDeleteFileRecordData_p2p(file_record, mStatistics);
                                    }
                                    if (DB_MODE.equals("both")) {
                                        res = processDeleteFileRecordData_cass(file_record, mStatistics);
                                        res = processDeleteFileRecordData_p2p(file_record, mStatistics);
                                    }
                                }
                                else {
                                    String key = file_record.dbe_md5;
                                    System.out.println("key(md5): " + key);
                                    
                                    if (DB_MODE.equals("cass")) {
                                        res = processFileRecordData_cass(file_record, mStatistics);                                        
                                    }
                                    if (DB_MODE.equals("p2p")) {
                                        res = processFileRecordData_exp(file_record, mStatistics);
                                    }
                                    if (DB_MODE.equals("both")) {
                                        res = processFileRecordData_cass(file_record, mStatistics);                                        
                                        res = processFileRecordData_exp(file_record, mStatistics);
                                    }
                                }                                

                                if (res) {
                                    p("File processed OK: " + file_record.toString());
                                    file.delete();
                                } else {
                                    p("ERROR processing file: " + file_record.toString());
                                }
                                ++process_files;
                                
                                if (mStatistics.nFiles == RECORDS) {
                                    int nres = storeBatchInfo(mStatistics);
                                    System.out.println("nres StoreBatchinfo:" + nres);
                                    mStatistics = new RecordStats();
                                }
                            }

                     } else {
                            p("Skipping file belongs to another batch.");
                     }   
                }
            }
        }
        if (process_files != 0){
            p("Files processed --> " + process_files);
        }
        log(mStatistics.toString(), 1);
        
        System.out.println("StoreBatchinfo");
        int nres = storeBatchInfo(mStatistics);
        System.out.println("nres StoreBatchinfo:" + nres);
    }
    
    public static boolean isWindows() {
        return (System.getProperty("os.name").toLowerCase().contains("win"));
    }
    public static boolean isMac() {
        return (System.getProperty("os.name").toLowerCase().contains("mac"));
    }

    int addMobileBackupFolder() {
        try {
            
            String sScanFile = NetUtils.getConfig("scandir", "../scrubber/config/www-rtbackup.properties");            
            if (sScanFile.length() > 0) {
                File f = new File(sScanFile);
                if (f.exists()) {
                    InputStream is =new BufferedInputStream(new FileInputStream(f));    
                    props.clear();
                    props.load(is);
                    
                    String r = props.getProperty("scandir");                
                    if (r != null){
                        String s = "../scrubber/mobilebackup/";
                        File f2 = new File(s);
                        f2.mkdir();                       
                        String sPathw = f2.getCanonicalPath() + File.separator;                        
                        String sPath = sPathw;
                        if (isMac()) {
                            File volumes = new File("/Volumes/");
                            if (volumes.exists()) {
                                for (File f3 : volumes.listFiles()) {
                                    if (f3.isDirectory()) {
                                        sPath = f3.getPath() + sPathw;
                                        File f4 = new File(sPath);
                                        if (f4.exists()) {
                                            p("path: '" + sPath + "' exists");
                                            break;
                                        } else {
                                            p("path: " + sPath + "' NOT exists");
                                        }
                                    }
                                }
                            }
                        } else {
                            if (isWindows()) {
                                p("**** Case Windows *********");
                                p("sPath = '" + sPath + "'");
                                p("**** End Case Windows *****");
                                //case Windows
                            } else {
                                //case Linux and others
                                p("**** Case Linux *********");
                                p("sPath = '" + sPath + "'");
                                p("**** End Case Linux *****");
                            }
                        }
                        
                        char c[] = sPath.toCharArray();
                        c[0] = Character.toLowerCase(c[0]);
                        sPath = new String(c);
                        
                        p("**** adding inbox path: " + sPath); 
                        String path = URLEncoder.encode(sPath, "UTF-8").replaceAll("\\+", "%20");
                        if (r.trim().length() == 0){
                            r = path;
                        }
                        else {
                            if (!r.trim().equals(path) && !r.contains(";"+path)){
                                r += ";" + path;
                            }
                        }
                            
                    }
                    props.setProperty("scandir", r);
                    OutputStream os =new BufferedOutputStream(new FileOutputStream(f));  
                    props.store(os, "os");                   
                    os.close();
                }
            }            
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
    
    
    

    private boolean processDeleteFileRecordData_cass(DatabaseEntry _record, RecordStats mStatistics) {
        boolean res = false;
        
        try {
            ++mStatistics.nFiles;
                        
            long l = 0;

            connectCassandra();
            
            if (mStatistics.nFiles == 1) {

                //determine batch ID so we can insert as Hashtag
                ts_start = Calendar.getInstance().getTime();
                //Date ts_date = Calendar.getInstance().getTime();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                String sDate = sdf.format(ts_start);
                p("***id sDate #: " + sDate);

                String batchid = c7.get_batch_id("batchid", columnFamilyBatch, "idx");
                p("last id batch stored : " + batchid);


                //claim new batch id and insert it
                l = Long.parseLong(batchid);
                l++;

                batchid_store = Long.toString(l);
                p("claiming batch id# " + batchid_store);
                c7.insert_column(keyspace, "BatchJobs", "batchid", "idx", batchid_store);
                c7.insert_column(keyspace, "BatchJobs", sDate, batchid_store, batchid_store);
            }
            
            res = c7.deleteObject(_record.dbe_md5, "paths", _record.dbe_uuid.toString(), _record.dbe_absolutepath);
            ++mStatistics.nDeleted;

            String strDateModified = c7.get_row_attribute(keyspace, "Standard1", _record.dbe_md5, "date_modified");

            //associated the deleted files to the current batch
            sUUID = _record.dbe_uuid.toString();
            String key = _record.dbe_md5;
            String sBatch = "batch:" + batchid_store;
            int ret_code = 0;
            
            p("DELCASE*** Adding to index '" + strDateModified + "' -> '" + key + "'");
            ret_code += c7.insert_column(keyspace, "Standard1", sBatch, strDateModified, key);
            p("ret_code '" + ret_code);
            ++mStatistics.nInsert;
            
            //insert batch id as hashtag for the deleted object
            p("DELCASE*** Adding Batch to hashes for key '" + key + "' -> '" + sBatch + "'");
            ret_code += c7.insertSuperColumn(keyspace, "Super2", key, "hashes", sBatch, sBatch);
            ++mStatistics.nInsertHash;
                        
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(ProcessorService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRequestException ex) {
            Logger.getLogger(ProcessorService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnavailableException ex) {
            Logger.getLogger(ProcessorService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimedOutException ex) {
            Logger.getLogger(ProcessorService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(ProcessorService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NotFoundException ex) {
            Logger.getLogger(ProcessorService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (OutOfMemoryError ex) {
            Logger.getLogger(ProcessorService.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return res;
    }
    
    private boolean processDeleteFileRecordData_p2p(DatabaseEntry _record, RecordStats mStatistics) {
        boolean res = false;
        
        try {
            ++mStatistics.nFiles;
                        
            long l;

            if (mStatistics.nFiles == 1) {

                //determine batch ID so we can insert as Hashtag
                ts_start = Calendar.getInstance().getTime();
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                String sDate = sdf.format(ts_start);
                p("***id sDate #: " + sDate);
                
                sDate = sDate.replaceAll(":", "@");
                sDate = sDate.replaceAll("/", "#");

                String batchid = c8.get_batch_id("batchid", columnFamilyBatch, "idx");
                p("last id batch stored : " + batchid);

                //claim new batch id and insert it
                l = 0;
                if (batchid.length() > 0) {
                    l = Long.parseLong(batchid);
                }
                l++;

                batchid_store = Long.toString(l);
                p("claiming batch id# " + batchid_store);
                c8.insert_column(keyspace, "BatchJobs", "batchid", "idx", batchid_store, false);
                                
                p("date:" + sDate);
                try {
                    Thread.sleep(5000);
                    log("processDeleteFileRecordData_p2p Sleep 5000", 2);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                c8.insert_column(keyspace, "BatchJobs", sDate, batchid_store, batchid_store, true);
            }
            
            res = c8.deleteObject(_record.dbe_md5, "paths", _record.dbe_uuid.toString(), _record.dbe_absolutepath);
            ++mStatistics.nDeleted;

            String strDateModified = c8.get_row_attribute(keyspace, "Standard1", _record.dbe_md5, "date_modified", null);

            //associated the deleted files to the current batch
            sUUID = _record.dbe_uuid.toString();
            String key = _record.dbe_md5;
            String sBatch = "batch@" + batchid_store;
            int ret_code = 0;
            
            p("DELCASE*** Adding to index '" + strDateModified + "' -> '" + key + "'");
            String sAdder = key + "," + _record.dbe_absolutepath.substring(_record.dbe_absolutepath.lastIndexOf("\\") + 1,_record.dbe_absolutepath.length());
            ret_code += c8.insert_column(keyspace, "Standard1", sBatch, strDateModified, sAdder, true);
            p("ret_code '" + ret_code);
            ++mStatistics.nInsert;
            
            //updateNumberOfCopies(key);
            
            //insert batch id as hashtag for the deleted object
            p("DELCASE*** Adding Batch to hashes for key '" + key + "' -> '" + sBatch + "'");
            ret_code += c8.insertSuperColumn(keyspace, "Super2", key, "hashes", sBatch, sBatch);
            ++mStatistics.nInsertHash;

        } catch (OutOfMemoryError ex) {
            Logger.getLogger(ProcessorService.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return res;
    }
         
    private boolean processFileRecordData_exp(DatabaseEntry _record, RecordStats mStatistics) {

        ++mStatistics.nFiles;
        String keyspace = "Keyspace1b";
        String key = _record.dbe_md5;

        Boolean insert_ok = false;
        int nRetries = 0;
        long l = 0;

               
        String dbe_md5 = _record.dbe_md5;
        //String strDateModified = DateFormat.format(_record.dbe_lastmodified);
       
        log("[EXP]processing experimental file #" + mStatistics.nFiles + "<'" + _record.dbe_filename + "'> with name,value <" + dbe_md5 + ">", 2);
        
        p("\t processing file #" + mStatistics.nFiles + "<'" + _record.dbe_filename + "'> with name,value <" + key + ">");

        if (mStatistics.nFiles % mFiles == 0) {
            try {
                System.out.println(mFiles + " files scanned. Time to sleep for " + mDelay + " ms.");
                log("processFileRecordData_exp 1 Sleep for " + mDelay + " ms.", 2);
                Thread.sleep(mDelay);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                //Logger.getLogger(ClientService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        if (mStatistics.nFiles == 1) {

            //determine batch ID so we can insert as Hashtag
            ts_start = Calendar.getInstance().getTime();
            //Date ts_date = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            String sDate = sdf.format(ts_start);
            p("***id sDate #: " + sDate);
            
            sDate = sDate.replaceAll(":", "@");
            sDate = sDate.replaceAll("/", "#");

            String batchid = c8.get_batch_id("batchid", columnFamilyBatch, "idx");
            p("last id batch stored : " + batchid);

            //claim new batch id and insert it
            l = 0;
            if (batchid.length() > 0) {
                l = Long.parseLong(batchid);
            }
            l++;

            batchid_store = Long.toString(l);
            p("claiming batch id# " + batchid_store);
            c8.insert_column(keyspace, "BatchJobs", "batchid", "idx", batchid_store, false);
            
            p("date:" + sDate);
            try {
                Thread.sleep(5000);
                log("processFileRecordData_exp 2 Sleep 5000", 2);
            } catch (Exception e) {
                e.printStackTrace();
            }
            c8.insert_column(keyspace, "BatchJobs", sDate, batchid_store, batchid_store, true);

        }

        boolean bExists = false;
        String sName = "";
        try {
            sName = c8.get_row_attribute(keyspace, "Standard1", key, "name", null);
            if (!sName.equals("")) {
                bExists = true;
                p("Object exists. name = '" + sName + "'");
            }
        } catch (Exception e) {
        }

        //-Insert multiple columns into one row
        //  MD5 (row key)
        //     (column name) | (value)
        //      "name"| filename
        //      "ext" | extension
        //      size (bytes)
        //      date registered (onto the system)
        //      date modified



        String strDateAddedTime = DateFormat.format(Calendar.getInstance().getTime());
        String strDateModified = DateFormat.format(_record.dbe_lastmodified);

        int ret_code = 0;
        //TODO handle insertion errors (handle timout - retry with delay)
        sUUID = _record.dbe_uuid.toString();

        if (!bExists) {
            log("inserting name: " + _record.dbe_filename, 2);
            ret_code += c8.insert_column(keyspace, "Standard1", key, "name", _record.dbe_filename, true);
            ret_code += c8.insert_column(keyspace, "Standard1", key, "ext", _record.dbe_filetype.toLowerCase(), true);
            ret_code += c8.insert_column(keyspace, "Standard1", key, "size", String.valueOf(_record.dbe_filesize), true);
            ret_code += c8.insert_column(keyspace, "Standard1", key, "date_added", strDateAddedTime, true);
            ret_code += c8.insert_column(keyspace, "Standard1", key, "date_modified", strDateModified, true);                
            ret_code += c8.insert_column(keyspace, "Standard1", key, "uuid_ori", _record.dbe_uuid.toString(), true);

            mStatistics.nInsert += 6;
        } else {
            log("Skipping object data insert, since object already exists.", 2);
        }

        //- Analyze substrings for each folder
        //- Create
        String full_path = "";
        String sub_string = "";
        String sBatch = "batch@" + batchid_store;

        //insert batch id as hashtag for the object
        ret_code += c8.insertSuperColumn(keyspace, "Super2", key, "hashes", sBatch, sBatch);

        //also associate the object to the "batch" hierarchy so it shows in search results
        //ret_code += c7.insert_column(keyspace, "Standard1", sBatch, key, key);

        p("strDateModifiedA = " + strDateModified);
        strDateModified = c8.getValidDateModified(strDateModified);
        p("strDateModifiedB = " + strDateModified);

        String sPath = "";
        //For each folder      
        for (String sub_folder_full : _record.dbe_folders) {            
            //System.out.println("sub_folder_full: " + sub_folder_full);
            if (!sub_folder_full.isEmpty()) {
                    sPath += sub_folder_full + "/";
            }
        }
        p("full path = " + sPath);
        
        String sAdder = key + "," + sPath;
        
        p("*** Adding to BATCH '" + sBatch + "' " + strDateModified + "' -> '" + sAdder + "'");
        ret_code += c8.insert_column(keyspace, "Standard1", sBatch, strDateModified, sAdder, true);        
        ++mStatistics.nInsert;

        sAdder = key + "," + _record.dbe_filename;

        //updateNumberOfCopies(key);
        
        if (!bExists) {            
               //insert the object to ".all" so that's it's later found in broad search case
               p("*** Adding to index [.all] '" + strDateModified + "' -> '" + key + "'");
               ret_code += c8.insert_column(keyspace, "Standard1", ".all", strDateModified, sAdder, true);
               ++mStatistics.nInsert;

               //this is to verify that it was inserted and can be read
   //            while(!c8.objectAlreadyExist(strDateModified)) {
   //                try {
   //                    Thread.sleep(100);
   //                } catch (Exception ex) {
   //                    ex.printStackTrace();
   //                }
   //                log("Dirty read in file = " + _record.dbe_filename + " and date = " + strDateModified);
   //            }

               //also insert the object's extension so that's it's later found in broader search
               String sExt = "." + _record.dbe_filetype.toLowerCase();
               p("*** Adding to index [" + sExt + "] '" + strDateModified + "' -> '" + key + "'");
               ret_code += c8.insert_column(keyspace, "Standard1", sExt, strDateModified, sAdder, true);
               ++mStatistics.nInsert;           

               //office files
               if (sExt.contains(".doc") || sExt.contains(".xls") || sExt.contains(".ppt") || sExt.contains(".pdf")) {
                   p("*** Adding Office to index [" + ".document" + "] '" + strDateModified + "' -> '" + key + "'");
                   ret_code += c8.insert_column(keyspace, "Standard1", ".document", strDateModified, sAdder, true);
                   ++mStatistics.nInsert;                               
               }

               //new office files
               if (sExt.contains(".docx") || sExt.contains(".xlsx") || sExt.contains(".pptx")) {                
                  p("*** Adding New Office to index [" + sExt.substring(0, sExt.length()-1) + "] '" + strDateModified + "' -> '" + key + "'");
                   ret_code += c8.insert_column(keyspace, "Standard1", sExt.substring(0, sExt.length()-1), strDateModified, sAdder, true);
                   ++mStatistics.nInsert;                                                          
               }

               //photos
               if (sExt.contains(".jpg") || sExt.contains(".jpeg") || sExt.contains(".gif") || sExt.contains(".png")) {
                   p("*** Adding photo to index [" + ".photo" + "] '" + strDateModified + "' -> '" + key + "'");
                   ret_code += c8.insert_column(keyspace, "Standard1", ".photo", strDateModified, sAdder, true);
                   ++mStatistics.nInsert;                               
               }

               //music
               if (sExt.contains(".mp3") || sExt.contains(".m4a") || sExt.contains(".m4p") || sExt.contains(".wma")) {
                   p("*** Adding song to index [" + ".music" + "] '" + strDateModified + "' -> '" + key + "'");
                   ret_code += c8.insert_column(keyspace, "Standard1", ".music", strDateModified, sAdder, true);
                   ++mStatistics.nInsert;                               
               }

               //videos
               if (sExt.contains(".mov") || sExt.contains(".mts") || sExt.contains(".m4v") || sExt.contains(".mp4") || sExt.contains(".m2ts")) {
                   p("*** Adding video to index [" + ".video" + "] '" + strDateModified + "' -> '" + key + "'");
                   ret_code += c8.insert_column(keyspace, "Standard1", ".video", strDateModified, sAdder, true);
                   ++mStatistics.nInsert;                               
               }                         
        } else {
            log("Skipping obj extension insert, since object already exists.", 2);                  
        }
                                              
        //For each folder             
        for (String sub_folder_full : _record.dbe_folders) {
            
            //System.out.println("sub_folder_full: " + sub_folder_full);
            if (!sub_folder_full.isEmpty()) {
                    full_path += sub_folder_full + "/";
                    
                    // (1)Add path compoenents (folders) as hashtags
                    //- Insert Hash Tags
                    /// + Column Family (Table Name): Super2
                    //        - Row (key)
                    //              + Supercolumn: "hashes"
                    //                      - column(name,value)
                    ret_code += c8.insertSuperColumn(keyspace, "Super2", key, "hashes", sub_folder_full, sub_folder_full);
                    mStatistics.nInsertHash += 1;
                    
                    StringTokenizer st = new StringTokenizer(sub_folder_full, delimiters, true);

                    while (st.hasMoreTokens()) {
                        
                        String sub_folder = st.nextToken();
                                                
                        //- Add path name subtring into autocomplete & object table
                        //- TODO move max-length-sub-folder to config setting
                        if (sub_folder.length() >= mMinSubstrLen && sub_folder.length() < mMaxStrLen) {

                            boolean bCont = true;
                            int nlen = sub_folder.length();
                            if (isHashFile(sub_folder)) {
                                System.out.println("Skipping HASHFILE:" + sub_folder);
                                bCont = false;
                            }
                            if (isHex(sub_folder)) {
                                System.out.println("Skipping HEXFILE:" + sub_folder);
                                bCont = false;
                            }
                            
                            //if the file is local, don't need to index the substrings since we have the info in records.db
                            if (isLocalUUID(_record.dbe_uuid.toString())) {
                                bCont = false;
                            }
                            
                            if (bCont) {                                                                                
                                //store full name in index so the file can be found in search.
                                System.out.println("adding standard1: '" + _record.dbe_filename + "' name:" + strDateModified + " value: " + sAdder);
                                ret_code = c8.insert_column(keyspace, "Standard1", _record.dbe_filename, strDateModified, sAdder, true);
                                //ret_code = c8.insert_column(keyspace, "Standard1", _record.dbe_filename, strDateModified, dbe_md5, true);
                                //ret_code = c8.insert_local_index(keyspace, "Standard1", _record.dbe_filename, strDateModified, dbe_md5, _record.dbe_filename);
                                ++mStatistics.nInsert;

                                //-Generate substring (ie: documents do doc d... documents oc ocu...
                                for (int idx = 0; idx < sub_folder.length() + 1; ++idx) {
                                    for (int i = idx + 1; i < sub_folder.length() + 1; i++) {
                                    
                                        sub_string = sub_folder.substring(idx, i);

                                        //- TODO move to configuartion min-length-substring
                                        if (sub_string.length() >= mMinSubstrLen) {
                                            //System.out.println("adding sub_string: '" + sub_string + "'");
                                            if (mAutoComplete) {
                                                System.out.println("adding standard2: '" + sub_string.toLowerCase() + "' name:" + sub_folder.toLowerCase() + " value: " + sub_folder.toLowerCase());

                                                //-Add substring in autocomplete table (e.g. key: 'P' name/value: 'Pictures')
                                                //ret_code += c7.insert_column(keyspace, "Standard2", sub_string.toLowerCase(), sub_folder.toLowerCase(), sub_folder.toLowerCase());
                                                ret_code = c8.insert_column(keyspace, "Standard2", sub_string.toLowerCase(), sub_folder.toLowerCase(), sub_folder.toLowerCase(), true);
                                                
                                                //If substring is part of the filename, associate substring to the full filename as well.
                                                if (_record.dbe_filename.toLowerCase().contains(sub_string.toLowerCase())) {
                                                    ret_code = c8.insert_column(keyspace, "Standard2", sub_string.toLowerCase(), _record.dbe_filename, _record.dbe_filename, true);                                                    
                                                }
                                                
                                                ++mStatistics.nInsertAutoComplete;
                                            }

                                            //-Also associate hash to subtring (e.g. key:'P'  name/value:file_hash
                                            //ret_code += c7.insert_column(keyspace, "Standard1", sub_string.toLowerCase(), strDateModified, key);
                                            //ret_code = c8.insert_local_index(keyspace, "Standard1", sub_string.toLowerCase(), strDateModified, dbe_md5, _record.dbe_filename);
                                            System.out.println("adding standard1: '" + sub_string.toLowerCase() + "' name:" + strDateModified + " value: " + sAdder);
                                            ret_code = c8.insert_column(keyspace, "Standard1", sub_string.toLowerCase(), strDateModified, sAdder, true);
                                            ++mStatistics.nInsert;
                                        } else {
                                            //-Skip substring
                                            ++mStatistics.nSkipSubStrings;
                                        }
                                    }  
                                }    
                                
                            } else {
                                p("[EXP]skipping index of sub folder'" + sub_folder + "'");
                            } //hash/hex/local test
                        } else {
                            //p("[EXP]skipping sub folder'" + sub_folder + "' due to length: " + sub_folder.length());
                            ++mStatistics.nSkipSubFolders;
                            if (sub_folder.length() >= mMaxStrLen) {
                                //-Since it's long substring, just link the obj to the entire string                                
                                ret_code = c8.insert_column(keyspace, "Standard1", sub_folder, strDateModified, sAdder, true);
                            }
                            //ret_code += c7.insert_column(keyspace, "Standard1", sub_folder, strDateModified, key);
                            //ret_code = c8.insert_local_index(keyspace, "Standard1", sub_folder, strDateModified, dbe_md5, _record.dbe_filename);
                        } // valid length
                    } // while more tokens
                } // if !empty
        } //for dbe folders
        
        if (!bExists) {
            if (_record.dbe_mp3_artist != null) {
                ret_code += c8.insert_column(keyspace, "Standard1", key, "artist", _record.dbe_mp3_artist, true);                
            }
            if (_record.dbe_mp3_title != null) {
                ret_code += c8.insert_column(keyspace, "Standard1", key, "title", _record.dbe_mp3_title, true);                
            }            
        } else {
                log("Skipping MP3 data insert, since object already exists.", 2);  
        }
 
        //- Add path of this file into the supercolumn
        /// + Column Family (Table Name): Super2
        //        - Row (key)  --- MD5 ***
        //              + Supercolumn: "paths" ***
        //                      - column1(name=UUID,value=path)
        //                      - column2(name=UUID,value=path)
        
        String abspath = _record.dbe_absolutepath;
        abspath = abspath.replace("\\", "/");
        abspath = abspath + "/";
        
        ret_code += c8.insertSuperColumn(keyspace, "Super2", key, "paths", _record.dbe_uuid.toString() + ":" + abspath, _record.dbe_filename);
        ++mStatistics.nInsertHash;

        //System.out.println("inserting path: '" + str2 +"'");
        //c7.insert_column(keyspace,"Standard1", key, "path", full_path);
        //mStatistics.nInsert += 1;

        //-Save thumbnail
        if (_record.dbe_img_thumbnail != null) {
            log("Image dimenstions  h: " + _record.dbe_img_height + " w:" + _record.dbe_img_width, 2); 
            ret_code += c8.insert_column(keyspace, "Standard1", key, "img_height", Integer.toString(_record.dbe_img_height), true);
            ret_code += c8.insert_column(keyspace, "Standard1", key, "img_width", Integer.toString(_record.dbe_img_width), true);

            
            //store the real thumbnail
            File fh = new File(THUMBNAIL_OUTPUT_DIR, key + ".jpg");
            if (!fh.exists()) {                        
                log("Storing thumbnail at location: '" + fh.getAbsolutePath() + "'", 2);                      
                boolean success = filewrite(fh, _record.dbe_img_thumbnail);
                if (!success) {
                    log("WARNING: There was a problem saving the thumbnail", 0);
                }

            } else {
                log("Skipping thumbnail creation, since file already exists.", 2);                      
            }
            
            //store the thumbnail encoded in Base64 as well
            File fh64= new File(THUMBNAIL_OUTPUT_DIR, key + ".alt64");
            if (!fh64.exists()) {
                log("Storing thumbnail(base64) at location: '" + fh64.getAbsolutePath() + "'", 2);  
                boolean success = filewrite64(fh, fh64);
                if (!success) {
                    log("WARNING: There was a problem saving the thumbnail64", 0);
                }                
            } else {
                log("Skipping thumbnail64 creation, since file already exists.", 2);                      
            }
            
        }
        
        if (_record.dbe_keywords != null && _record.dbe_keywords.length() > 1) {
            //store keywords if they come in the payload
            p("Storing keywords: " + _record.dbe_keywords);
            ret_code += c8.insert_column(keyspace, "Standard1", key, "keywords", _record.dbe_keywords, true);  
            
        }
        if(abspath.contains("/scrubber/mobilebackup")) {
            //DateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
            //String sDateFile = sdf.format(new Date());                     
            //c8.insert_hashtag(keyspace, key, "mobilebackup", true, sdf.format(new Date()));            
            strDateModified = c8.get_row_attribute(keyspace, "Standard1" , key, "date_modified", null);
            c8.insert_hashtag(keyspace, key, "mobilebackup", true, strDateModified);            
        }
        
        return true;
    }
    
    private boolean processFileRecordData_cass(DatabaseEntry _record, RecordStats mStatistics) {

        
        ++mStatistics.nFiles;
        String keyspace = "Keyspace1b";
        String key = _record.dbe_md5;

        Boolean insert_ok = false;
        int nRetries = 0;
        long l = 0;
        
        connectCassandra();

        log("\t processing file #" + mStatistics.nFiles + "<'" + _record.dbe_filename + "'> with name,value <" + key + ">", 2);
        if (_record.dbe_filename.length() == 36 && _record.dbe_folders.contains("cassbackup")) {
            //name has the MD5
            String sName = _record.dbe_filename.substring(0,32);
            p("backup file: " + sName);
            p("md5: " + _record.dbe_md5);
            if (!sName.equals(_record.dbe_md5)) {
                p("don't match, replacing key...");
                key = sName;
            }
        }

        while (!insert_ok) {

            p("\t processing file #" + mStatistics.nFiles + "<'" + _record.dbe_filename + "'> with name,value <" + key + ">");

            if (mStatistics.nFiles % mFiles == 0) {
                try {
                    System.out.println(mFiles + " files scanned. Time to sleep for " + mDelay + " ms.");
                    log("processFileRecordData_cass SLEEP  -  " + mFiles + " files scanned. Time to sleep for " + mDelay + " ms.", 2);
                    Thread.sleep(mDelay);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                    //Logger.getLogger(ClientService.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            if (mStatistics.nFiles == 1) {

                //determine batch ID so we can insert as Hashtag
                ts_start = Calendar.getInstance().getTime();
                //Date ts_date = Calendar.getInstance().getTime();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                String sDate = sdf.format(ts_start);
                p("***id sDate #: " + sDate);

                String batchid = c7.get_batch_id("batchid", columnFamilyBatch, "idx");
                p("last id batch stored : " + batchid);
                
                
                //claim new batch id and insert it
                l = Long.parseLong(batchid);
                l++;
                
                batchid_store = Long.toString(l);
                p("claiming batch id# " + batchid_store);
                c7.insert_column(keyspace, "BatchJobs", "batchid", "idx", batchid_store);
                c7.insert_column(keyspace, "BatchJobs", sDate, batchid_store, batchid_store);

            }

            boolean bExists = false;
            String sName = "";
            try {
                sName = c7.get_row_attribute(keyspace, "Standard1", key, "name");
                if (!sName.equals("")) {
                    bExists = true;
                    p("Object exists. name = '" + sName + "'");
                }
            } catch (Exception e) {
            }

            //-Insert multiple columns into one row
            //  MD5 (row key)
            //     (column name) | (value)
            //      "name"| filename
            //      "ext" | extension
            //      size (bytes)
            //      date registered (onto the system)
            //      date modified



            String strDateAddedTime = DateFormat.format(Calendar.getInstance().getTime());
            String strDateModified = DateFormat.format(_record.dbe_lastmodified);
            
            int ret_code = 0;
            //TODO handle insertion errors (handle timout - retry with delay)
            sUUID = _record.dbe_uuid.toString();
                
            if (!bExists) {
                ret_code += c7.insert_column(keyspace, "Standard1", key, "name", _record.dbe_filename);
                ret_code += c7.insert_column(keyspace, "Standard1", key, "ext", _record.dbe_filetype.toLowerCase());
                ret_code += c7.insert_column(keyspace, "Standard1", key, "size", String.valueOf(_record.dbe_filesize));
                ret_code += c7.insert_column(keyspace, "Standard1", key, "date_added", strDateAddedTime);
                ret_code += c7.insert_column(keyspace, "Standard1", key, "date_modified", strDateModified);                
                ret_code += c7.insert_column(keyspace, "Standard1", key, "uuid_ori", _record.dbe_uuid.toString());

                mStatistics.nInsert += 6;
            } else {
                log("Skipping object data insert, since object already exists.", 2);
            }

            //- Analyze substrings for each folder
            //- Create
            String full_path = "";
            String sub_string = "";
            String sBatch = "batch:" + batchid_store;
            //String sBatchKW = "batch@" + batchid_store;

            //insert batch id as hashtag for the object
            ret_code += c7.insertSuperColumn(keyspace, "Super2", key, "hashes", sBatch, sBatch);

            //also associate the object to the "batch" hierarchy so it shows in search results
            //ret_code += c7.insert_column(keyspace, "Standard1", sBatch, key, key);
            
            p("strDateModifiedA = " + strDateModified);
            strDateModified = c7.getValidDateModified(strDateModified);
            p("strDateModifiedB = " + strDateModified);
                
            p("*** Adding to index '" + strDateModified + "' -> '" + key + "'");
            ret_code += c7.insert_column(keyspace, "Standard1", sBatch, strDateModified, key);
            ++mStatistics.nInsert;

            if (!bExists) {
                //also insert the object to ".all" so that's it's later found in broad search case
                //log("strDateModified1 = " + strDateModified);
                //strDateModified = c7.getValidDateModified(strDateModified);
                //log("strDateModified2 = " + strDateModified);
                
                p("*** Adding to index [.all] '" + strDateModified + "' -> '" + key + "'");
                ret_code += c7.insert_column(keyspace, "Standard1", ".all", strDateModified, key);
                ++mStatistics.nInsert;
                
                //this is to verify that it was inserted and can be read
                while(!c7.objectAlreadyExist(strDateModified)) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    log("Dirty read in file = " + _record.dbe_filename + " and date = " + strDateModified, 2);
                }

                //also insert the object's extension so that's it's later found in broader search
                String sExt = "." + _record.dbe_filetype.toLowerCase();
                p("*** Adding to index [" + sExt + "] '" + strDateModified + "' -> '" + key + "'");
                ret_code += c7.insert_column(keyspace, "Standard1", sExt, strDateModified, key);
                ++mStatistics.nInsert;           

                if (sExt.contains(".jpg") || sExt.contains(".jpeg") || sExt.contains(".gif") || sExt.contains(".png")) {
                    p("*** Adding photo to index [" + ".photo" + "] '" + strDateModified + "' -> '" + key + "'");
                    ret_code += c7.insert_column(keyspace, "Standard1", ".photo", strDateModified, key);
                    ++mStatistics.nInsert;                               
                }

            } else {
                log("Skipping obj extension insert, since object already exists.", 2);                  
            }

            //-For each folder
            for (String sub_folder : _record.dbe_folders) {
                //-TODO consider spaces in hash tags
                if (!sub_folder.isEmpty()) {
                    full_path += sub_folder + "/";

                    //System.out.println("\t processing <'" + str + "'> with length" + str.length());
                    //c7.insert_hashtag(keyspace,key, str, "hashes");

                    // (1)Add path compoenents (folders) as hashtags
                    //- Insert Hash Tags
                    /// + Column Family (Table Name): Super2
                    //        - Row (key)
                    //              + Supercolumn: "hashes"
                    //                      - column(name,value)
                    ret_code += c7.insertSuperColumn(keyspace, "Super2", key, "hashes", sub_folder, sub_folder);
                    mStatistics.nInsertHash += 1;

                    //- Add path name subtring into autocomplete & object table
                    //- TODO move max-length-sub-folder to config setting
                    if (sub_folder.length() < mMaxStrLen) {

                        boolean bCont = true;
                        int nlen = sub_folder.length();
                        if (isHashFile(sub_folder)) {
                            bCont = false;
                        }
                        if (bCont) {
                        //-Generate substring (ie: documents do doc d... documents oc ocu...
                            for (int idx = 0; idx < sub_folder.length() + 1; ++idx) {
                                for (int i = idx + 1; i < sub_folder.length() + 1; i++) {
                                    sub_string = sub_folder.substring(idx, i);

                                    //- TODO move to configuartion min-length-substring
                                    if (sub_string.length() >= mMinSubstrLen) {

                                        if (mAutoComplete) {
                                            //-Add substring in autocomplete table (e.g. key: 'P' name/value: 'Pictures')
                                            ret_code += c7.insert_column(keyspace, "Standard2", sub_string.toLowerCase(), sub_folder.toLowerCase(), sub_folder.toLowerCase());
                                            ++mStatistics.nInsertAutoComplete;
                                        }

                                        //-Also associate hash to subtring (e.g. key:'P'  name/value:file_hash
                                        //ret_code += c7.insert_column(keyspace, "Standard1", sub_string.toLowerCase(), strDateModified, key);
                                        //++mStatistics.nInsert;
                                        
                                    } else {
                                        //-Skip substring
                                        ++mStatistics.nSkipSubStrings;
                                    }
                                }
                            }                            
                        } else {
                            log("skipping sub folder'" + sub_folder + "' due to hashfile: '" + sub_folder + "'", 2);
                        } 
                    } else {
                        //-Since it's long substring, just link the obj to the entire string
                        log("skipping sub folder'" + sub_folder + "' due to length: " + sub_folder.length(), 2);
                        ++mStatistics.nSkipSubFolders;
                        ret_code += c7.insert_column(keyspace, "Standard1", sub_folder, strDateModified, key);
                    }
                }
            }

            if (!bExists) {
                int n = 0;
                n = addMP3Info_cass(_record.dbe_mp3_artist, mStatistics, key, keyspace, strDateModified);
                if (n > 0) {
                    ret_code += c7.insert_column(keyspace, "Standard1", key, "artist", _record.dbe_mp3_artist);
                }
                n = addMP3Info_cass(_record.dbe_mp3_title, mStatistics, key, keyspace, strDateModified);
                if (n > 0) {
                    ret_code += c7.insert_column(keyspace, "Standard1", key, "title", _record.dbe_mp3_title);
                }              
            } else {
                log("Skipping MP3 data insert, since object already exists.", 2);  
            }
 
            //- Add path of this file into the supercolumn
            /// + Column Family (Table Name): Super2
            //        - Row (key)  --- MD5 ***
            //              + Supercolumn: "paths" ***
            //                      - column1(name=UUID,value=path)
            //                      - column2(name=UUID,value=path)
            ret_code += c7.insertSuperColumn(keyspace, "Super2", key, "paths", _record.dbe_uuid.toString() + ":" + full_path, _record.dbe_filename);
            ++mStatistics.nInsertHash;

            //System.out.println("inserting path: '" + str2 +"'");
            //c7.insert_column(keyspace,"Standard1", key, "path", full_path);
            //mStatistics.nInsert += 1;

            //-Save thumbnail
            if (_record.dbe_img_thumbnail != null) {
                log("Image dimenstions  h: " + _record.dbe_img_height + " w:" + _record.dbe_img_width, 2); 
                ret_code += c7.insert_column(keyspace, "Standard1", key, "img_height", Integer.toString(_record.dbe_img_height));
                ret_code += c7.insert_column(keyspace, "Standard1", key, "img_width", Integer.toString(_record.dbe_img_width));
                                
                //store the real thumbnail
                File fh = new File(THUMBNAIL_OUTPUT_DIR, key + ".jpg");
                if (!fh.exists()) {                        
                    log("Storing thumbnail at location: '" + fh.getAbsolutePath() + "'", 2);                      
                    boolean success = filewrite(fh, _record.dbe_img_thumbnail);
                    if (!success) {
                        log("WARNING: There was a problem saving the thumbnail", 0);
                    }

                } else {
                    log("Skipping thumbnail creation, since file already exists.", 2);                      
                }
            
                //store the thumbnail encoded in Base64 as well
                File fh64= new File(THUMBNAIL_OUTPUT_DIR, key + ".alt64");
                if (!fh64.exists()) {
                    log("Storing thumbnail(base64) at location: '" + fh64.getAbsolutePath() + "'", 2);  
                    boolean success = filewrite64(fh, fh64);
                    if (!success) {
                        log("WARNING: There was a problem saving the thumbnail64", 0);
                    }                
                } else {
                    log("Skipping thumbnail64 creation, since file already exists.", 2);                      
                }

            }

            int nres = insert_remote_link(key);
            System.out.print(mStatistics);

            if (ret_code != 0) {
                log("Warning: there was an Error on Cassandra insertion.", 0);
                mStatistics.nRetry++;
                nRetries++;
                if (nRetries <= 3) {
                    try {
                        log("time to big sleep for 300sec", 2);
                        Thread.sleep(300000L);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                        //Logger.getLogger(ClientService.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                else {
                    log("FATAL: RETRY attempts exceeded", 0);
                    return false;
                }
            } else {
                //System.out.println("insert ok, continue...");
                insert_ok = true;
            }                     
        }

        return true;
    }
    
    public static boolean filewrite(File _file, byte[] _data) {
        FileOutputStream fo;
        try {            
            fo = new FileOutputStream(_file,false);
            fo.write(_data);
            fo.flush();
            fo.close();
        } catch (FileNotFoundException ex) {
            return false;
        } catch (IOException ex) {
            return false;
        }
        return true;
    }

    public static boolean filewrite64(File _file, File _file64) {

        FileInputStream is = null;
        FileWriter writer = null;
        ByteArrayOutputStream out = null;
        byte[] buf = null;
        
        try {
            is=new FileInputStream(_file);
            out=new ByteArrayOutputStream();
            writer=new FileWriter(_file64);
            int n;

             buf =new byte[1024*1024];
             while ((n = is.read(buf)) > 0) {
                 out.write(buf, 0, n);
             }
             String srcPic = utils.Base64.encodeToString(out.toByteArray(), false);
             writer.write(srcPic.toCharArray());      
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                is.close();
                writer.close();
                out.close(); 
                buf = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }
    }
    
 
      private int insert_remote_link(String _key) {
        try {
            
                String sViewURL = "";
                int nError = 0;
                
                try {
                    //System.out.println("   [B]");
                    sViewURL = c7.get_row_attribute(keyspace, "Standard1", _key, "ViewLink3");
                } catch (Exception ex) {
                    System.out.println("   [Be]");
                    nError += 1;
                }
                        
                //System.out.println("   [C]");

                String sPlayURL = "";
                try {
                    sPlayURL = c7.get_row_attribute(keyspace, "Standard1", _key, "PlayLink3");
                } catch (Exception ex) {
                    System.out.println("   [Ce]");
                    nError += 1;
                }            

                if (sViewURL.length() < 1 || mForceIndex) {
                    sURLPack sURLpack = new sURLPack();
                    sURLpack = c7.get_remote_link2(_key,"paths", false, false, "127.0.0.1", true, false);

                    System.out.println("Viewlink ---> '" + sURLpack.sViewURL + "'");
                    if (sURLpack.sViewURL.length() > 0 && !sURLpack.sViewURL.equals("ERROR")) {
                        sViewURL = sURLpack.sViewURL;
                        int ires = c7.insert_column("Keyspace1b","Standard1", _key, "ViewLink3", sViewURL); 

                    }

                    System.out.println("Playlink ---> '" + sURLpack.sPlayURL + "'");
                    if (sURLpack.sPlayURL.length() > 0 && !sURLpack.sPlayURL.equals("ERROR")) {
                        sPlayURL = sURLpack.sPlayURL;
                        int ires = c7.insert_column("Keyspace1b","Standard1", _key, "PlayLink3", sPlayURL);  
                    }                    
                } else {
                    System.out.println("Skipping remote link insert. They already exist.");
                }
                return 0;
           } catch (Exception ex) {
                ex.printStackTrace();
                return -1;
           }    
      }
      
      
      private int addMP3Info_exp(String _mp3info, RecordStats mStatistics, String _key, String _keyspace, String _date, String _adder) {
                        
                if (_mp3info != null && _mp3info.length() > 0) {
                    System.out.println("mp3 info found '" + _mp3info + "'");
          
                    //String strDateAddedTime = DateFormat.format(Calendar.getInstance().getTime());
          
                    //String delimiters = "() ";
                    StringTokenizer st = new StringTokenizer(_mp3info, delimiters, true);
                    String ret_code = "";
                    Integer nTokens = st.countTokens();
                    String sub_string = "";

                    while (st.hasMoreTokens()) {
                        String w = st.nextToken();
                        if (w.length() >= mMinSubstrLen) {
                            System.out.println("adding hash for mp3 '" + _key + "','" + w + "'");    
                            ret_code += c8.insertSuperColumn(keyspace, "Super2", _key, "hashes", w, w);
                        }                        
                        for (int idx = 0; idx < w.length() + 1; ++idx) {
                            for (int i = idx + 1; i < w.length() + 1; i++) {
                                sub_string = w.substring(idx, i);

                                    //- TODO move to configuartion min-length-substring
                                    if (sub_string.length() >= mMinSubstrLen) {
                                            System.out.println("adding: '" + sub_string.toLowerCase() + "' to key " + _key);
                                            ret_code += c8.insert_column(_keyspace, "Standard1", sub_string.toLowerCase(), _date, _adder, true);
                                            ++mStatistics.nInsert;
                                            //System.out.println("adding string: '" + sub_string + "' to autocomplete '" + _mp3info.toLowerCase());
                                            if (mAutoComplete) {
                                                ret_code += c8.insert_column(_keyspace, "Standard2", sub_string.toLowerCase(), _mp3info.toLowerCase(), _mp3info.toLowerCase(), true);
                                                ++mStatistics.nInsertAutoComplete;
                                            }
                                    }
                            }
                        }
                    }
                    return 1;
                } else {
                    return 0;
                }   
      }
      
      private int addMP3Info_cass(String _mp3info, RecordStats mStatistics, String _key, String _keyspace, String _date) {
              
          
                if (_mp3info != null && _mp3info.length() > 0) {
                    System.out.println("mp3 info found '" + _mp3info + "'");
          
                    //String strDateAddedTime = DateFormat.format(Calendar.getInstance().getTime());
          
                    //String delimiters = "() ";
                    StringTokenizer st = new StringTokenizer(_mp3info, delimiters, true);
                    String ret_code = "";
                    Integer nTokens = st.countTokens();
                    String sub_string = "";

                    while (st.hasMoreTokens()) {
                        String w = st.nextToken();
                        if (w.length() >= mMinSubstrLen) {
                            System.out.println("adding hash for mp3 '" + _key + "','" + w + "'");    
                            ret_code += c7.insertSuperColumn(keyspace, "Super2", _key, "hashes", w, w);
                        }                        
                        for (int idx = 0; idx < w.length() + 1; ++idx) {
                            for (int i = idx + 1; i < w.length() + 1; i++) {
                                sub_string = w.substring(idx, i);

                                    //- TODO move to configuartion min-length-substring
                                    if (sub_string.length() >= mMinSubstrLen) {
                                            //System.out.println("adding: '" + sub_string.toLowerCase() + "' to key " + _key);
                                            ret_code += c7.insert_column(_keyspace, "Standard1", sub_string.toLowerCase(), _date, _key);
                                            ++mStatistics.nInsert;
                                            //System.out.println("adding string: '" + sub_string + "' to autocomplete '" + _mp3info.toLowerCase());
                                            if (mAutoComplete) {
                                                ret_code += c7.insert_column(_keyspace, "Standard2", sub_string.toLowerCase(), _mp3info.toLowerCase(), _mp3info.toLowerCase());
                                                ++mStatistics.nInsertAutoComplete;
                                            }
                                    }
                            }
                        }
                    }
                    return 1;
                } else {
                    return 0;
                }   
      }
    
       private int addHashStrings(DatabaseEntry _record, RecordStats mStatistics, String key, String _date) {
                int ret_code = 0;
                String sub_string = "";
                String keyspace = "Keyspace1b";
                
                //-For each folder
                for (String sub_folder : _record.dbe_folders) {
                    //-TODO consider spaces in hash tags
                    if (!sub_folder.isEmpty()) {
                        //full_path += sub_folder + "/";

                        //System.out.println("\t processing <'" + str + "'> with length" + str.length());
                        //c7.insert_hashtag(keyspace,key, str, "hashes");

                        // (1)Add path compoenents (folders) as hashtags
                        //- Insert Hash Tags
                        /// + Column Family (Table Name): Super2
                        //        - Row (key)
                        //              + Supercolumn: "hashes"
                        //                      - column(name,value)
                        ret_code += c7.insertSuperColumn(keyspace, "Super2", key, "hashes", sub_folder, sub_folder);
                        mStatistics.nInsertHash += 1;

                        //- Add path name subtring into autocomplete & object table
                        //- TODO move max-length-sub-folder to config setting
                        if (sub_folder.length() < mMaxStrLen) {
                            boolean bCont = true;
                            int nlen = sub_folder.length();
                            if (isHashFile(sub_folder)) {
                                bCont = false;
                            }
                            if (bCont) {
                                //-Generate substring (ie: documents do doc d... documents oc ocu...
                                for (int idx = 0; idx < sub_folder.length() + 1; ++idx) {
                                    for (int i = idx + 1; i < sub_folder.length() + 1; i++) {
                                        sub_string = sub_folder.substring(idx, i);

                                        //- TODO move to configuartion min-length-substring
                                        if (sub_string.length() >= mMinSubstrLen) {

                                            if (mAutoComplete) {
                                                //-Add substring in autocomplete table (e.g. key: 'P' name/value: 'Pictures')
                                                ret_code += c7.insert_column(keyspace, "Standard2", sub_string.toLowerCase(), sub_folder.toLowerCase(), sub_folder.toLowerCase());
                                                ++mStatistics.nInsertAutoComplete;
                                            }

                                            //-Also associate hash to subtring (e.g. key:'P'  name/value:file_hash
                                            ret_code += c7.insert_column(keyspace, "Standard1", sub_string.toLowerCase(), _date, key);
                                            ++mStatistics.nInsert;
                                        } else {
                                            //-Skip substring
                                            ++mStatistics.nSkipSubStrings;
                                        }
                                    }
                                }  
                            } else {
                                System.out.println("skipping sub folder'" + sub_folder + "' due to hashfile: '" + sub_folder + "'");
                            }
                        } else {
                            //-Since it's long substring, just link the obj to the entire string
                            System.out.println("skipping sub folder'" + sub_folder + "' due to length: " + sub_folder.length());
                            ++mStatistics.nSkipSubFolders;
                            ret_code += c7.insert_column(keyspace, "Standard1", sub_folder, key, key);
                        }
                    }
                }
                return ret_code;
           
       }
       
       private boolean isHashFile(String sFileName) {           
            //System.out.println("IsHashFile: '" + sFileName + "'");
            String sFileName2 = sFileName;
            if (sFileName.indexOf(".") > 0) {
                 sFileName2 = sFileName.substring(0, sFileName.indexOf("."));
                 System.out.println("len1 = " + sFileName2.length());
            }           
            if (isHex(sFileName2) && !isNumeric(sFileName2)) {
               //System.out.println("true1");
               return true;
            } else {
               //System.out.println("false1");
               return false;
            } 
       }
       
       
       private boolean isHex(String _string) {
           return _string.matches("[0-9A-Fa-f]+");           
       }
       
       private boolean isNumeric(String _string) {
           return _string.matches("[0-9]+");           
       }

       private int storeBatchInfo(RecordStats mStatistics) {
           if (DB_MODE.equals("cass")) {
               int nres =  storeBatchInfo_cass(mStatistics);               
           } 

           if (DB_MODE.equals("both")) {
               int nres =  storeBatchInfo_cass(mStatistics);               
               nres +=  storeBatchInfo_exp(mStatistics);               
               return nres;              
           } 
           
            if (DB_MODE.equals("p2p")) {
               int nres =  storeBatchInfo_exp(mStatistics);               
               return nres;
           } 

           return -1;
           
       }
       
       private int storeBatchInfo_exp(RecordStats mStatistics) {

        //once done, store information for the batch
        String keyspace = "Keyspace1b";

        if (newbatch) {
            Date ts_end = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            String sDate = sdf.format(ts_end);
            System.out.println("id sDate #: " + sDate);

            //String batchid = c8.get_batch_id(sDate);
            //System.out.println("id batch last entry #: " + batchid);

            sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            String sTimeStart = sdf.format(ts_start);
            System.out.println("time_start: " + sTimeStart);

            String sTimeEnd = sdf.format(ts_end);
            System.out.println("time_end: " + sTimeEnd);

            int retcode = 0;
            //c8.insert_column(keyspace, "BatchJobs", sDate, batchid, batchid);
            System.out.println("batchid_store:" + batchid_store);

            String stats = mStatistics.toString();
            
            System.out.println(stats);
        
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "ts_start", sTimeStart, true);
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "ts_end", sTimeEnd, true);
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "uuid", sUUID, true);
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "nFiles", Long.toString(mStatistics.nFiles), true);
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "nInsert", Long.toString(mStatistics.nInsert), true);
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "nInsertAutoComplete", Long.toString(mStatistics.nInsertAutoComplete), true);
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "nInsertHash", Long.toString(mStatistics.nInsertHash), true);
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "nRetry", Long.toString(mStatistics.nRetry), true);
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "nSkipSubFolders", Long.toString(mStatistics.nSkipSubFolders), true);
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "nSkipSubStrings", Long.toString(mStatistics.nSkipSubStrings), true);
            retcode += c8.insert_column(keyspace, "BatchJobs", batchid_store, "nDeleted", Long.toString(mStatistics.nDeleted), true);

            //long newbatch = Long.parseLong(batchid.trim());
            //newbatch++;
            //System.out.println("newbatch: " + newbatch);
            //c7.insert_column(keyspace, "BatchJobs", "batchid", "id", Long.toString(newbatch));

            if (retcode != 0) {
                log("WARNING: there was an Error on Cassandra Batch Data insertion.", 2);
            }
            newbatch = false;
            
            updateNumberOfCopies(batchid_store);
        } else {
            System.out.println("Skip batch storage.");
        }
        return 0;
    }
       
       private int storeBatchInfo_cass(RecordStats mStatistics) {

        //once done, store information for the batch
        String keyspace = "Keyspace1b";

        if (newbatch) {
            Date ts_end = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            String sDate = sdf.format(ts_end);
            System.out.println("id sDate #: " + sDate);

            //String batchid = c7.get_batch_id(sDate);
            //System.out.println("id batch last entry #: " + batchid);

            sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            String sTimeStart = sdf.format(ts_start);
            System.out.println("time_start: " + sTimeStart);

            String sTimeEnd = sdf.format(ts_end);
            System.out.println("time_end: " + sTimeEnd);

            int retcode = 0;
            //c7.insert_column(keyspace, "BatchJobs", sDate, batchid, batchid);
            System.out.println("batchid_store:" + batchid_store);

            String stats = mStatistics.toString();
            
            System.out.println(stats);
        
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "ts_start", sTimeStart);
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "ts_end", sTimeEnd);
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "uuid", sUUID);
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "nFiles", Long.toString(mStatistics.nFiles));
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "nInsert", Long.toString(mStatistics.nInsert));
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "nInsertAutoComplete", Long.toString(mStatistics.nInsertAutoComplete));
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "nInsertHash", Long.toString(mStatistics.nInsertHash));
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "nRetry", Long.toString(mStatistics.nRetry));
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "nSkipSubFolders", Long.toString(mStatistics.nSkipSubFolders));
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "nSkipSubStrings", Long.toString(mStatistics.nSkipSubStrings));
            retcode += c7.insert_column(keyspace, "BatchJobs", batchid_store, "nDeleted", Long.toString(mStatistics.nDeleted));

            //long newbatch = Long.parseLong(batchid.trim());
            //newbatch++;
            //System.out.println("newbatch: " + newbatch);
            //c7.insert_column(keyspace, "BatchJobs", "batchid", "id", Long.toString(newbatch));

            if (retcode != 0) {
                log("WARNING: there was an Error on Cassandra Batch Data insertion.", 0);
            }
            newbatch = false;
        } else {
            System.out.println("Skip batch storage.");
        }
        return 0;
    }
       
     /**
     * Connect to Cassandra Server
     */
    private void connectCassandra() {
        try {
            boolean bConnect = false;
            while (!bConnect) {
                if (mServerIP == null || mServerIP.length() == 0) {
                    mServerIP = "localhost";
                    System.out.println("fix cassandra host");
                }
                System.out.println("Connecting to Cassandra. Hostname: " + mServerIP);
                bConnect = c7.connect(mServerIP,9160);
                System.out.println("connect result = " + bConnect);
                if (!bConnect) {
                    long lSleep = 10000;
                    log("connect failed. going to sleep for " + lSleep + " ms", 2);
                    Thread.sleep(lSleep);
                }
            }
        } catch (Exception ex) {
            StringWriter sWriter = new StringWriter();
            ex.printStackTrace(new PrintWriter(sWriter));
            log(sWriter.getBuffer().toString(), 0) ;

        }
    }
    
    public boolean updateNumberOfCopies(String _batchid) {
        try {
            
            byte[] data;
        
            if (mServerIP.length() == 0) {
                mServerIP = "localhost";
            }

            String urlStr = "http://" + mServerIP + ":" + mServerport + "/cass/updatenumcopies.fn?" + "batchid=" + _batchid;

            p("updateNumberOfCopies" + urlStr);
            String sStorePath = "updateNumberofCopies.txt";
            //int nres = NetUtils.getfile(urlStr, sStorePath, 1, 500, 10000);  //1 try, timeout10s    
            
            File fh = new File("../scrubber/batch_" + _batchid + ".idx");
            FileWriter fw = new FileWriter(fh, true);
            fw.write("done");
            fw.close();
            
            //int nres = wf.remove_occurrences_copies(_batchid);            
            //p("updateNumberOfCopies nres = " + nres);
            
//            URL url = new URL(urlStr);
//            URLConnection conn = url.openConnection ();
//            
//            InputStream rd = conn.getInputStream();
//            String outfileName = "inputstream.txt";
//
//            FileOutputStream outFile = new FileOutputStream(outfileName);
//
//            int numRead = 0;
//            data = new byte[16384];
//
//             while ((numRead = rd.read(data)) >= 0) {
//                outFile.write(data,0,numRead);
//            }
//
//            rd.close();
//            outFile.close();
            
            return true;
        } catch (Exception e) {
                StringWriter sWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(sWriter));
                log(sWriter.getBuffer().toString(), 0) ;
            return false;
        }
    }

}

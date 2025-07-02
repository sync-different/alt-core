/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */



package processor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.security.MessageDigest;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import javax.swing.text.AbstractDocument;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import processor.FileDatabaseEntry;
import processor.DatabaseEntry;
//import static services.BackupClientService.sUUIDpath;
import utils.NetUtils;

import java.nio.charset.CharsetDecoder;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.codec.digest.DigestUtils;

import utils.Stopwatch;


public class FileUtils {
    
    
    
    static class Key implements Serializable {
        private String key;

        public Key(String key) {
            p("adding key: " + key) ;
            this.key = key;
        }

        @Override
        public boolean equals(Object obj) {

            if (obj instanceof Key) {
                p("key: " + key + " vs " + ((Key) obj ).key) ;
                return key.equals(((Key) obj).key);                
            }
            else
                return false;
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }
    
    /* mapping of file extensions to content-types */
    static java.util.Hashtable mapFileExtensions = new java.util.Hashtable();

    static java.util.Hashtable mapBlack = new java.util.Hashtable();
    static java.util.Hashtable mapBlackC = new java.util.Hashtable();
    
    //private Map<Key, FileDatabaseEntry> mFilesDatabase = new HashMap <Key, FileDatabaseEntry>();
    private HashMap mFilesDatabase = new HashMap <String, FileDatabaseEntry>();
    
    String mStorage = ""; //./data/records.db";
    String mDatabaseEntryPath = "./outgoing";
    protected Properties props = new Properties();
    boolean mDEBUG_MODE = false;
    
    UUID node_UUID;
    
    String LOG_NAME = "scanner.txt";
    String LOG_PATH = "logs/";
    PrintStream log= null;
    
    
    String mUUID = "";
    String mUUIDPath = "";
    
    int mDelayFile = 500;  
    long mDelayTime = 1000;
    int mRecordsCount = 0;
    boolean bSkipHidden = true;
    
    static int count = 0;
    public static int countrecords = 0;
    public static boolean oomcase = false;   
    
    public static boolean mTerminated = false;
    public static boolean bCheckRecordCount = false;
    
    String mCONFIG_PATH = "";
    
    static int mLogLevel = 0;
    
    int mMD5Method = 2;  //1=Java implementation  2=Apache Commons
    long mDelaySleep = 10; //Time to sleep between file scans
    long mDelayThumb = 100; //Time to sleep if a thumbnail was created

    static boolean bConsole = true;
    
    public void cleanup() {
        long freeMem = 0;
        
        p("before: " + mFilesDatabase.size());
        Runtime r = Runtime.getRuntime();
        freeMem = r.freeMemory();
        p("* free mem: " + freeMem);

        mFilesDatabase.clear();
        p("after: " + mFilesDatabase.size());
        freeMem = r.freeMemory();
        p("* free mem: " + freeMem);
        mFilesDatabase = null;
        mStorage = null;
        mDatabaseEntryPath = null;
        props = null;
        LOG_NAME = null;
        log = null;
        mUUID = null;
        mUUIDPath = null;
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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.FileUtils-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.FileUtils-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [CS.FileUtils-" + threadID + "] " + s + ANSI_RESET);
        }
    }


    /* print to stdout */
    static protected void p(String s) {

        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);
        
        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.FileUtils_" + threadID + "] " + s);
    }
    
    protected void pdebug(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (mDEBUG_MODE) {
            long threadID = Thread.currentThread().getId();
            System.out.println(sDate + "[DEBUG] [CS.FileUtils_" + threadID + "] " + s);
        }
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
            pi(sDate + " " + _loglevel + " " + s);
        }
    }

    public FileUtils() {
        
    }
    
    public FileUtils(String _path, Boolean _debugmode, String _configpath, int _loglevel) {
        try {
            mStorage = _path;
            mDEBUG_MODE = _debugmode;
            mCONFIG_PATH = _configpath;
            mLogLevel = _loglevel;
                                  
            loadBackupProps();

            String sLog = LOG_PATH + LOG_NAME;
            log = new PrintStream(new BufferedOutputStream(
                                new FileOutputStream(sLog,true)));
            log("opening log file: " + sLog + " loglevel: " + mLogLevel, 0);
            
            p("uuidpath = " + mUUIDPath);                    
            p("outgoing = " + mDatabaseEntryPath);                    

            mUUID = NetUtils.getUUID(mUUIDPath);

            p("uuid = '" + mUUID + "'");        
            p("delay_file = '" + mDelayFile + "'");    
            p("delay_time= '" + mDelayTime + "'");    
            p("nrecords='" + mRecordsCount + "'");
            p("checkrecords='" + bCheckRecordCount + "'");
            p("skiphidden=" + bSkipHidden);
            
            //load hashmap (file extension)
            loadFileExtensions();
            
            //load hashmap (blacklisted paths)
            p("loadblacklist");
            loadBlacklistMap();
            p("loadblacklist-contains");
            loadBlacklistContainsMap();
            
            Thread.sleep(5000);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    
    }
    
    void loadBackupProps() throws IOException {
    
        //p(System.getProperty("java.home"));
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
            
            r = props.getProperty("outgoing");
            if (r != null) {
                mDatabaseEntryPath = r;
            }
            
            r = props.getProperty(("delay_file"));
            if (r != null) {                
                mDelayFile = Integer.parseInt(r);
            }

            r = props.getProperty(("delay_time"));
            if (r != null) {
                mDelayTime = Long.parseLong(r);
            }
            
            r = props.getProperty("nrecords");
            if (r != null) {
                mRecordsCount = Integer.parseInt(r);
                bCheckRecordCount = true;
            }
            
            r = props.getProperty(("skip_hidden"));
            if (r != null) {
                bSkipHidden = Boolean.parseBoolean(r);
            }
            
            r = props.getProperty(("delay_sleep"));
            if (r != null) {
                mDelaySleep = Long.parseLong(r);
            }
            
            r = props.getProperty(("delay_thumb"));
            if (r != null) {
                mDelayThumb = Long.parseLong(r);
            }
            
            r = props.getProperty(("md5method"));
            if (r != null) {                
                mMD5Method = Integer.parseInt(r);
            }
            
            r = props.getProperty("logpath");
            if (r != null) {
                LOG_PATH = r;
            }
        
        }

    }
    
    public boolean IsNodeAvailable(String _sPath, boolean _bWindowsServer) {
        if (_bWindowsServer){
            File[] roots = File.listRoots();
            for(int k=0;k<roots.length;k++){                
                if (_sPath.toLowerCase().startsWith(roots[k].toString().toLowerCase())){
                    return true;
                }
            }
        } else{
            p("Checking path: " + _sPath);
            
            if (_sPath.startsWith("/Volumes/")){
                String sPath = _sPath.substring(0, _sPath.indexOf("/", 9)+1);
                p("Checking volume: " + sPath);
                File file1 = new File(sPath);                
                p("exists: " + file1.exists());
                p("canread: " + file1.canRead());
                if(file1.exists()){
                    p("returing true...[1]");
                    return true;
                }
            } else{
                File file1 = new File("/");
                if(file1.exists()){
                    p("returing true...[2]");
                    return true;
                }
            }
        }
        //p("returning false...[isnodeavailable]");
        return false;
    }
    
    public void ScanDeletedFiles(boolean _bWindowsServer) {
        try{
            if (mFilesDatabase != null) {
                p("ScanDeletedFiles");
                log("#entries in hash : " + mFilesDatabase.size(), 2);
                java.util.Hashtable hDeletedObject = new java.util.Hashtable();

                Iterator it = mFilesDatabase.entrySet().iterator();
                int i = 0;
                int cnt_ex = 0;
                int cnt_nex = 0;
                
                while (it.hasNext()) {
                    try {
                        Map.Entry pairs = (Map.Entry)it.next();
                        String sKey = (String)pairs.getKey();
                        FileDatabaseEntry sValue = (FileDatabaseEntry)pairs.getValue();
                        String sFilePath = URLDecoder.decode(sKey,"UTF-8");

                        i++;
                        //log("   looking for file '" + i + sFilePath + "'", 2);
                        
                        File tf = new File(sFilePath);                                                
                        if (!tf.exists()) {                           
                            cnt_nex++;
                            log("   file not exist '" + tf.getAbsolutePath() + "'", 2);
                            if (IsNodeAvailable(sFilePath, _bWindowsServer)){
                                //Generate Delete entry
                                
                                log("   adding new entry (DELETE) for file '" + tf.getAbsolutePath() + "' with MD5: " + sValue.mMD5, 2);
                                node_UUID = UUID.fromString(mUUID);                                   
                               
                                DatabaseEntry dbe = new DatabaseEntry(sValue.mMD5, node_UUID, tf.getAbsolutePath());

                                Boolean bres = saveDatabaseEntry(dbe, "D");

                                hDeletedObject.put(sKey, "");

                                p("bres = " + bres);

                                dbe = null;
                            }
                        } else {
                            cnt_ex++;
                        }
                        
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(FileUtils.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (Exception e) {
                        log("WARNING: Exception IN ScanDeletedFiles.[2]", 0);
                    }
                }
                log("   tot:" + i + " ex: " + cnt_ex + " nex: " + cnt_nex, 2);

                Iterator itDeletedObject = hDeletedObject.entrySet().iterator();
                while (itDeletedObject.hasNext()) {
                    Map.Entry meEntry = (Map.Entry)itDeletedObject.next();

                    p("key del HASH = " + meEntry.getKey().toString());
                    mFilesDatabase.remove(meEntry.getKey().toString());
                }
            }
         } catch (Exception e) {
            log("WARNING: Exception IN ScanDeletedFiles.", 0);
         }
    }
    
    
    public int ScanDirectory(String _sPath, Boolean _checkMD5) {
        
        Charset systemCharset = Charset.forName(System.getProperty("file.encoding"));
        CharsetDecoder systemDecoder = systemCharset.newDecoder();
        
        CharBuffer cbufP = null;
        try {
            cbufP = systemDecoder.decode(ByteBuffer.wrap(_sPath.getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        String sPath = cbufP.toString();                
        //p("Dir1 " + _sPath);
        //p("Dir2 " + sPath);       
        
        File tf = new File(sPath);

        if (tf.exists()) {
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                e.printStackTrace();
            }
            p("Scanning directory '" + sPath + "'  MD5Check = " + _checkMD5);
            File[] files = tf.listFiles();
                        
            try {
                
                if(files!=null){
                    for (File f : files) {

                        //check #records processes so far. exit if exceeds recordcount from file
                        if (bCheckRecordCount) {
                            if ((countrecords % mRecordsCount == 0) && (countrecords > 0)) {
                                log(countrecords + " files have been created. exiting...", 1);
                                return 1;                                                            
                            }                            
                        } 
                        
//                        //check for termination signal
//                        if (mTerminated) {
//                            log("exiting due to termination signal...");
//                            return 2;                                                            
//                        } 
                        

                        if (f.isDirectory()) {
                            if (!isBlacklistedContains(f.getCanonicalPath().toLowerCase()) && !isBlacklisted(f.getCanonicalPath().toLowerCase(), true)) {
                                p("evaluating: '" + f.getName() + "'");
                                if (f.getName().startsWith(".") || f.isHidden()) {
                                    p("Hidden folder found: " + f.getName());
                                    if (bSkipHidden) {
                                        p("skipping hidden folder");
                                    } else {
                                        p("Found a directory to scan: " + f.getPath() + " " + f.getCanonicalPath());
                                        int nres = ScanDirectory(f.getPath(), _checkMD5);                            
                                        if (nres < 0) {
                                            log("WARNING: There was an error processing subdirectory: " + f.getPath(), 0);
                                            return -1;
                                        }                                                                    
                                    } 
                                } else {
                                    p("Found a directory to scan: " + f.getPath() + " " + f.getCanonicalPath());
                                    int nres = ScanDirectory(f.getPath(), _checkMD5);                            
                                    if (nres < 0) {
                                        log("WARNING: There was an error processing subdirectory: " + f.getPath(), 0);
                                        return -1;
                                    }                                                                    
                                }
                            } else {
                                //p("Skipping directory: " + f.getCanonicalPath());
                            }
                        }
                        
                        if (f.isFile()) {
                                p("Found a file: " + f.getPath() + " " + f.getCanonicalPath());
                                count++;
                                
                                //p("ZZZzzz...[scannerservice10ms]");
                                Thread.sleep(mDelaySleep);
                                if (count % mDelayFile == 0) {
                                    p(count + " files have been scanned. Sleeping for " + mDelayTime + " ms...");
                                    Thread.sleep(mDelayTime);
                                }

    //                            if (count % 1000 == 0) {
    //                                log(count + " files have been scanned. GC + Sleeping for " + mDelayTime + " ms...");
    //                                System.gc();                                
    //                                Thread.sleep(mDelayTime);
    //                            }

                                //p("File Name: '" + f.getName() + "'");
                                //p("File AbsolutePath" + f.getAbsolutePath());
                                if ( checkFileType(f.getName().toLowerCase()) ) {
                                    Date date_lastmodified = new Date(f.lastModified());
                                    String sMD5 = "";
                                    String sMS5_ = "";
                                    String sFileEncoded = URLEncoder.encode(f.getAbsolutePath(),"UTF-8");
                                    //String sFileEncoded = f.getAbsolutePath();

                                    p("MD5Check: " + _checkMD5);
                                    if (!_checkMD5) {
                                        pdebug(
                                                f.getAbsolutePath() + "," + 
                                                date_lastmodified + "," + 
                                                f.length());                                
                                    }

                                    boolean bGenerateRecord = false;                          
                                    if (mFilesDatabase == null) {
                                        p("**** Records file is empty.");     
                                        if (sMD5.equals("")) {
                                            //calculate MD5 if it has not been calculated yet.
                                            sMD5 = calcMD5(f.getAbsolutePath());                                               
                                        }
                                        log("   adding new entry for file '" + f.getAbsolutePath() + "' with MD5: " + sMD5, 2);
                                        FileDatabaseEntry entry = new FileDatabaseEntry(f.lastModified(), sMD5);
                                        //Key kp = new Key(sFileEncoded);
                                        //mFilesDatabase.put(kp, entry);  
                                        //mFilesDatabase.put(sFileEncoded.toLowerCase(), entry);
                                        mFilesDatabase.put(sFileEncoded, entry);
                                        bGenerateRecord = true;
                                    } else {
                                        Object entry = mFilesDatabase.get(sFileEncoded);
                                        if (entry == null) {
                                            entry = mFilesDatabase.get(sFileEncoded.toLowerCase());
                                        }                                        

                                        if (entry != null) {
                                            boolean bModified = false;
                                            FileDatabaseEntry fde = (FileDatabaseEntry)entry;
                                            if (_checkMD5) {

                                                //deep scan, calculate and compare the MD5 of the file as well.
                                                sMD5 = calcMD5(f.getAbsolutePath());
                                                pdebug(
                                                    f.getAbsolutePath() + "," + 
                                                    date_lastmodified + "," + 
                                                    f.length() + "," +
                                                    sMD5);                                

                                                if (!sMD5.equals(fde.mMD5)) {
                                                    log("  *** FILE '" + f.getAbsolutePath() + "' HAS BEEN ALTERED! " + fde.mMD5 + " vs " + sMD5, 2);    
                                                    bModified = true;
                                                }
                                            }

                                            if (!(fde.mDateModified == f.lastModified())) {
                                                log("  *** FILE '" + f.getAbsolutePath() + "' DATE HAS BEEN ALTERED! " + fde.mDateModified + " vs " + f.lastModified(), 2);    
                                                bModified = true;
                                            }

                                            if (bModified) {
                                                if (sMD5.equals("")) {
                                                    //calculate MD5 if it has not been calculated yet.
                                                    sMD5 = calcMD5(f.getAbsolutePath());    
                                                }
                                                
                                                //Delete old entry
                                                node_UUID = UUID.fromString(mUUID);
                                                //DatabaseEntry dbe = new DatabaseEntry(fde.mMD5, node_UUID, URLEncoder.encode(f.getAbsolutePath(),"UTF-8").toLowerCase());
                                                DatabaseEntry dbe = new DatabaseEntry(fde.mMD5, node_UUID, URLEncoder.encode(f.getAbsolutePath(),"UTF-8"));
                                                Boolean bres = saveDatabaseEntry(dbe, "D");
                                                dbe = null;
                                                
                                                //Update new data
                                                fde.mDateModified = f.lastModified();
                                                fde.mMD5 = sMD5;

                                                //mFilesDatabase.put(URLEncoder.encode(f.getAbsolutePath(),"UTF-8").toLowerCase(), entry);
                                                mFilesDatabase.put(URLEncoder.encode(f.getAbsolutePath(),"UTF-8"), entry);
                                                bGenerateRecord = true;
                                                
                                                //mFilesDatabase.remove(URLEncoder.encode(f.getAbsolutePath(),"UTF-8").toLowerCase());
                                            } else {
                                                //nothing to do, file has not changed.
                                            }
                                        } else {
                                            // is null, file doesn't exist yet
                                            sMD5 = calcMD5(f.getAbsolutePath());
                                            if (!sMD5.equals("ERROR")) {
                                                log("   adding new entry #" + countrecords + " for file '" + f.getAbsolutePath() + "' with MD5: " + sMD5, 2);
                                                entry = new FileDatabaseEntry(f.lastModified(),sMD5);
                                                //Key kv = new Key(URLEncoder.encode(f.getAbsolutePath(),"UTF-8"));
                                                //mFilesDatabase.put(kv, (FileDatabaseEntry)entry); 
                                                //mFilesDatabase.put(URLEncoder.encode(f.getAbsolutePath(),"UTF-8").toLowerCase(), entry);
                                                mFilesDatabase.put(URLEncoder.encode(f.getAbsolutePath(),"UTF-8"), entry);
                                                bGenerateRecord = true;                                                                               
                                            } else {
                                                log("   skipping new entry due to MD5 error '" + f.getAbsolutePath() + "'", 2); 
                                                bGenerateRecord = false;
                                                p("forcing count from " + countrecords);
                                                //countrecords=mRecordsCount;
                                                mTerminated = true;
                                            }
                                        }                             
                                    }
                                    if (bGenerateRecord) {
                                            node_UUID = UUID.fromString(mUUID);                                   

                                            DatabaseEntry dbe = new DatabaseEntry(sMD5, node_UUID, f, (float)0.9, mDelayThumb);

                                            if (dbe.dbe_action.equals("NEW")) {
                                                Boolean bres = saveDatabaseEntry(dbe, "A");
                                                p("bres = " + bres);
                                            } 
                                            if (dbe.dbe_action.equals("OOM-PDF")) {
                                                Boolean bres = saveDatabaseEntry(dbe, "A");
                                               p("bres = " + bres);
                                                String sFile = URLEncoder.encode(f.getAbsolutePath());
                                                log("WARNING: There was an OOM processing PDF file: '" + sFile + "'", 0);                                                        
                                            }
                                            if (dbe.dbe_action.equals("EXC")) {
                                                String sFile = URLEncoder.encode(f.getAbsolutePath());
                                                log("WARNING: There was an EXC processing file: '" + sFile + "'", 0);                                                        
                                            }
                                            if (dbe.dbe_action.equals("OOM")) {
                                                String sFile = URLEncoder.encode(f.getAbsolutePath());
                                                log("WARNING: There was an OOM processing file: '" + sFile + "'", 0);
                                                //mFilesDatabase.remove(URLEncoder.encode(f.getAbsolutePath(),"UTF-8"));
                                                //countrecords=mRecordsCount;
                                                mTerminated=true;
                                                p("EXITING THE FUNCTION at n=" + countrecords);
                                                oomcase = true;
                                                return -1;
                                            }

                                            dbe = null;
                                    }
                                    

                                    String projectsFolderPath;
                                    String OS = System.getProperty("os.name").toLowerCase();
                                    boolean isWin = OS.contains("win");
                                    if (isWin) {
                                        projectsFolderPath = "..\\";
                                    } else {
                                        projectsFolderPath = "../";
                                    }

                                    File altfolder = new File(projectsFolderPath);
                                    p("ALT_PATH " + altfolder.getCanonicalPath());
                                    String filename = f.getName();
                                    String filetype = filename.substring(filename.lastIndexOf(".") + 1, filename.length());
                                    if (is_video(filetype.toLowerCase())){
                                        //check if ffmpeg video exists
                                        
                                        sFileEncoded = URLEncoder.encode(f.getAbsolutePath(),"UTF-8");
                                        //Object entry = mFilesDatabase.get(sFileEncoded.toLowerCase());
                                        
                                        Object entry = mFilesDatabase.get(sFileEncoded);
                                        if (entry == null) {
                                            entry = mFilesDatabase.get(sFileEncoded.toLowerCase());                                            
                                        }
                                        
                                        if (entry != null) {
                                            //try to fetch MD5 from DB first, to avoid MD5 calc
                                            FileDatabaseEntry fde = (FileDatabaseEntry)entry;
                                            sMD5 = fde.mMD5;
                                            log("MD5 from DB: " + sMD5, 2);
                                        } else {
                                            sMD5 = calcMD5(f.getAbsolutePath());
                                            log("MD5 from file: " + sMD5, 2);
                                        }
                                        
                                        String outputfolderPath;
                                        if (isWin) {
                                            outputfolderPath = projectsFolderPath + "\\rtserver\\streaming\\" + sMD5;
                                        }else{
                                            outputfolderPath = projectsFolderPath + "rtserver/streaming/" + sMD5;
                                        }
                                        
                                        log("outputfolder is " + outputfolderPath, 2);
                                        
                                        String streamingFolderPath;
                                        if (isWin) {
                                            streamingFolderPath = projectsFolderPath + "\\rtserver\\streaming\\";
                                        }else{
                                            streamingFolderPath = projectsFolderPath + "/rtserver/streaming/";
                                        }
                                        File streamingFolder = new File(streamingFolderPath);
                                        log("streamingFolder is " + streamingFolder.getCanonicalPath(), 2);
                                        if(!streamingFolder.exists()){
                                            log("mkdir " + streamingFolder.getCanonicalPath(), 0);                                            
                                            streamingFolder.mkdirs();                                        
                                        } else {
                                            p("skip mkdir. folder already exists: " + streamingFolder.getCanonicalPath());                                            
                                        }
                                        File folder = new File(outputfolderPath);
                                        if(!folder.exists()){
                                            FfmpegExecutor fe = new FfmpegExecutor();
                                            fe.execute(f, sMD5, outputfolderPath, projectsFolderPath, isWin);
                                        } else {
                                            p("skip encode. folder already exists: " + folder.getCanonicalPath());
                                        }
                                    }
                                                           
                        } 

                        f = null;
                    } // end if file
                  } // for
                } //files !=null
                
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
                //p("forcing count from " + countrecords);
                //countrecords=mRecordsCount;
                log("Exiting due to OOM.", 0);
                oomcase = true;                
                return -1;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (files != null) {
                    //p("clearning var 'files'");
                    files = null;
                }
                return 0;
            }
        } else {
            p("ERROR: Directory " + tf.getAbsolutePath() + " doesn't exist or unavailable.");
            
        }
        if (tf != null) {
            p("clearning var 'tf'");
            tf = null;
        }
        return 0;
    }

    private boolean is_video(String _string) {
        if (
                _string.toLowerCase().contains("mov") ||
                _string.toLowerCase().contains("mp4") ||
                _string.toLowerCase().contains("mmv") ||
                _string.toLowerCase().contains("mpg") 
            )
                return true;
    else
        return false;
    }
    
    boolean isWhitelisted(String _directory) {
        p("checking whitelist for directory: " + _directory);
        if (_directory.contains("pictures") ||
                _directory.contains("documents")) {
            return true;
        } else {
            p("Directory is not whitelisted: " + _directory);
            return false;
        }
    }
    
    boolean isBlacklistedContains(String _filepath) {
        boolean bBlack = false;
        boolean bCont = true;
        
        Iterator it = mapBlackC.entrySet().iterator();
                
        String _directory = "";
        while (it.hasNext() && bCont) {
            Map.Entry pairs = (Map.Entry)it.next();

            String theKey = (String)pairs.getKey();
            theKey = theKey.replace("\\", "/");
            
            _filepath = _filepath.replace("\\", "/");
            _directory = _filepath.substring(0,_filepath.lastIndexOf("/")+1);     
            
            log("[contains] comparing '" + _directory + "' vs '" + theKey + "'",3);
            if (_directory.trim().toLowerCase().contains(theKey.trim().toLowerCase())) {
                bCont = false;
                bBlack = true;
                log("[contains] Directory '" + _directory + "' CONTAINS '" + theKey + "'", 3); 
            } else {
                //p("[blc] Directory '" + _directory + "' NOT CONTAINS '" + theKey + "'");  
            }
        }
        
        if (bBlack) {
            log("[contains] Directory '" + _directory + "' is blacklisted.", 3);            
        } else {
            log("[contains] Directory '" + _directory + "' is NOT blacklisted.", 3);                        
        }
        return bBlack;
        
    }
    
    boolean isBlacklisted(String _filepath, boolean _isdirectory) {
        boolean bBlack = false;
        boolean bCont = true;
        
        Iterator it = mapBlack.entrySet().iterator();
        
        //p("#blacklisted directories: " + mapBlack.size());
        
        String _directory = "";
        while (it.hasNext() && bCont) {
            Map.Entry pairs = (Map.Entry)it.next();           
            //p(pairs.getKey() + " = " + pairs.getValue() + " = " + pairs.toString());
            
            String theKey = (String)pairs.getKey();
            theKey = theKey.replace("\\", "/");
            
            _filepath = _filepath.replace("\\", "/");
            if (!_isdirectory) {
                //directory = false
                _directory = _filepath.substring(0,_filepath.lastIndexOf("/")+1);                   
            } else {
                //directory = true
                _directory = _filepath + "/";
            }            
            
            log("comparing '" + _directory + "' vs '" + theKey + "'",3); 
            if (_directory.toLowerCase().contains(theKey.toLowerCase())) {
                log("Directory '" + _directory + "' CONTAINS '" + theKey + "'", 3);  
                bCont = false;
                bBlack = true;
            } else {
                //p("[bl] Directory '" + _directory + "' NOT EQUALS '" + theKey + "'");  
            }
        }
        if (bBlack) {
            log("Directory '" + _directory + "' is blacklisted.",3);            
        } else {
            log("Directory '" + _directory + "' is NOT blacklisted.",3);                        
        }
        return bBlack;
        
    }
    
    boolean isBlacklisted2(String _directory) {
        
        //p("checking blacklist for directory: " + _directory);
        if (//windows
             _directory.contains("temporary internet files") || 
            (_directory.contains("appdata" + File.separator + "roaming")) ||
            (_directory.contains("agf" + File.separator + "cass" + File.separator + "cass" + File.separator + "pic")) ||
            (_directory.contains("appdata" + File.separator + "local")) ||
            //mac
            (_directory.contains(".trash")) ||
            (_directory.contains("library" + File.separator + "containers")) ||
            (_directory.contains("library" + File.separator + "application support")) ||
            (_directory.contains("library" + File.separator + "caches")) ||
            (_directory.contains("library" + File.separator + "developer")) ||
            (_directory.contains("library" + File.separator + "preferences"))) {
                p("Directory is blacklisted: " + _directory);
                return true;
        } else {
                return false;
        }
    }
    
    
//    String calcMD5(String _filename) {
//        return "0123456789ABCDEF0123456789ABCDEF";
//    }
    
    public String calcMD5(String _filename) {
            String hashtext = "";

            Stopwatch timer = new Stopwatch();
            timer.start();
            if (mMD5Method == 1) {
                hashtext = calcMD5_1(_filename);
            } else {
                hashtext = calcMD5_2(_filename);
            }
            timer.stop();
            p("method=" + mMD5Method + " md5=" + hashtext + " took:" + timer.getElapsedTime() + "ms");
            return hashtext;
    }
    
    public String calcMD5_2(String _filename) {
        try {            
            FileInputStream fis = new FileInputStream(_filename);
            
            String hashtext = DigestUtils.md5Hex(fis);                        
            return hashtext;
            
        } catch (Exception e) {
            return "";
        }
        
        
    }
    
    public String calcMD5_1(String _filename) {
        InputStream fis = null;
        byte[] buffer = null;
        MessageDigest md = null;
        String sRet = "";
        
        String signature = "";
        try {
            fis = new FileInputStream(_filename);
            buffer = new byte[262144];
            md = MessageDigest.getInstance("MD5");
            int numRead;
            
            do {
                numRead = fis.read(buffer);
                if (numRead > 0) {
                    md.update(buffer, 0, numRead);
                }
            } while (numRead != -1);
                       
            signature = new BigInteger(1, md.digest()).toString(16);
            
            sRet = String.format("%32s", signature).replace(" ","0");
            
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            log("Exiting due to OOM in calcMD5.", 0);
            System.exit(-1);
            sRet = "ERROR";                        
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            sRet = "ERROR";            
        } catch (IOException e) {
            e.printStackTrace();
            sRet = "ERROR";            
        } catch (Exception e) {
            e.printStackTrace();
            sRet = "ERROR";            
        } finally {
            close(fis);
            buffer = null;
            fis = null;
            md = null;
            return sRet;            
        }
        
        
    }
    
    public static void close_out(OutputStream c) {
     if (c == null) return; 
     try {
         c.close();
     } catch (IOException e) {
         e.printStackTrace();
     }
    }
     
    public static void close(InputStream c) {
     if (c == null) return; 
     try {
         c.close();
     } catch (IOException e) {
         e.printStackTrace();
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
            //mFilesDatabase = (HashMap<Key, FileDatabaseEntry>) in.readObject();
            mFilesDatabase = (HashMap<String, FileDatabaseEntry>) in.readObject();
            in.close();
            fileIn.close();
            
            storage = null;
            fileIn = null;
            in = null;
                    
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
    
    
          
    public boolean saveDatabaseEntry(DatabaseEntry dbe, String _type) {
    
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        File ft = null;
                  
        try {
            if (!(mDatabaseEntryPath.endsWith("/") || mDatabaseEntryPath.endsWith("\\"))){
                mDatabaseEntryPath += File.separator;
            }
            
            String sFilename = mDatabaseEntryPath +  dbe.dbe_uuid.toString() + "." + dbe.dbe_md5 + "." + _type;
            p("Saving database record #" + countrecords + " for file " + dbe.dbe_absolutepath + " file: '" + sFilename + "'");

            sFilename += "_" + System.currentTimeMillis();                
            
            fos = new FileOutputStream(sFilename);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(dbe);
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {

            close_out(fos);
            close_out(oos);
            
            ft = null;
            fos = null;
            oos = null;

            countrecords++;
            return true;
        }
        
    }
    
    
    public synchronized boolean savePendingFiles() {
        try {
            p("---- Saving Pending Files(db): " + mFilesDatabase.size());
            p("path for save: " + mStorage);
                    
            FileOutputStream fileOut = new FileOutputStream(mStorage);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(mFilesDatabase);
            out.close();
            fileOut.close();
            
            fileOut = null;
            out = null;
            
            return true;
        } catch (FileNotFoundException ex) {
            p("exception> " + ex.toString());
            return false;
        } catch (IOException ex) {
            p("exception> " + ex.toString());
            return false;
        }        
    }
    
    
    public int printFileCount() {
        
        if (mFilesDatabase != null) {
            return mFilesDatabase.size();            
        } else {
            return 0;
        }
    }
    
    static void loadBlacklistContainsMap() {
        try {
            FileInputStream bf2 = new FileInputStream("../scrubber/config/blacklist_contains.txt");
            Scanner scanner2 = new Scanner(bf2);
            
            mapBlackC.clear();
            
            while (scanner2.hasNext()) {                
                String spath = scanner2.nextLine();
                
                String replaced = spath.replace("\"", "");
                
                p("Adding token '" + replaced + "'");
                mapBlackC.put(replaced, "");                                    
            }
            
            bf2.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
 

    public static void loadBlacklistMap() {
        try {
            FileInputStream bf2 = new FileInputStream("./config/blacklist.txt");
            Scanner scanner2 = new Scanner(bf2);
            
            mapBlack.clear();
            
            while (scanner2.hasNext()) {                
                String spath = scanner2.nextLine();
                
                String replaced = spath.replace("\"", "");
                
                p("Adding token '" + replaced + "'");
                mapBlack.put(replaced, "");                                    
            }
            
            bf2.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    
    static void loadFileExtensions() {
        try {
            FileInputStream fileExtensions = new FileInputStream("./config/FileExtensions.txt");
            Scanner objScanner = new Scanner(fileExtensions);
            
            mapFileExtensions.clear();
            
            while (objScanner.hasNext()) {                
                String line = objScanner.nextLine();
                String key = line.substring(0, line.indexOf(","));
                String value = line.substring(line.indexOf(",") + 1, line.length());
                
                mapFileExtensions.put(key, value);
            }
            
            fileExtensions.close();
            
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    boolean checkFileType(String _filename) {
        
        boolean bRes = false;

        int nPos = _filename.lastIndexOf(".");
        if (nPos > 0) {
            String sExtension = _filename.substring(_filename.lastIndexOf("."), _filename.length());
            String ct = (String) mapFileExtensions.get(sExtension);
            if (ct != null) {
                bRes = true;
            }
        }

        return bRes;
    }
    
}

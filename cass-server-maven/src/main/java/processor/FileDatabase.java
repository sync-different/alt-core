/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package processor;

//import io.Log;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
//import java.io.Serializable;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.Iterator;
import utils.Cass7Funcs;
import java.util.StringTokenizer;

import java.io.InputStream;
import java.io.BufferedInputStream;
import java.util.Properties;
import java.util.UUID;

//import services.FileUtils;
import utils.LocalFuncs;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

public final class FileDatabase {
    /*Fields*/
    private String mStorage;                                    //- Storage file
    private HashMap<String, FileDatabaseEntry> mFilesDatabase;  //- Keep track of path and date of modified

    static Cass7Funcs c7 = new Cass7Funcs();
    static LocalFuncs c8 = new LocalFuncs();
    
    static String LOG_NAME = "logs/scrubber.txt";
    static PrintStream log= null;
    
    protected Properties props = new Properties();

    int mDelayFile = 500;
    long mDelayTime = 1000;
    long mDelayThumb = 100;
    
    static boolean bConnect = false;

    static String dbmode = "cass"; //assume cassandra db by default
    
    void loadDbModeProp() throws IOException {
        //p(System.getProperty("java.home"));
        p("loadProps()");
        File f = new File (".." + File.separator + "rtserver" + File.separator + "config" + File.separator + "www-server.properties");
        if (f.exists()) {
            InputStream is = new BufferedInputStream(new FileInputStream(f));
            props.load(is);
            is.close();
            String prop = props.getProperty("dbmode");
            if (prop != null) {
                dbmode = prop;
            }
        }

    }
    
    /**
     * Constructor
     * @param _storage String containing path to storage
     */
    public FileDatabase(String _storage) {        
        mStorage = _storage;
        if (!load()){
            mFilesDatabase = new HashMap<String, FileDatabaseEntry>();
        }
        try {
            log = new PrintStream(new BufferedOutputStream(
                                new FileOutputStream(LOG_NAME,true)));
            log("opening log file: " + LOG_NAME);            
        } catch (Exception e) {
            e.printStackTrace();
        }            
            
    }

    static boolean bConsole = true;

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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.FileDatabase-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.FileDatabase-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected static void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + "[DEBUG] [CS.FileDatabase_" + threadID + "] " + s);
    }

    /* print to the log file */
    protected static void log(String s) {

        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        //p(sDate + " " + s);
        synchronized (log) {
            log.println(sDate + " " + s);
            log.flush();
        }
        p(s);
    }

    void loadBackupProps() throws IOException {
    
        //p(System.getProperty("java.home"));
        p("loadBackkupProps()");
        File f = new File(
                "config"+
                File.separator+
                "www-rtbackup.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();

       
            String r;

            r = props.getProperty(("delay_file"));
            if (r != null) {                
                mDelayFile = Integer.parseInt(r);
            }

            r = props.getProperty(("delay_time"));
            if (r != null) {
                mDelayTime = Long.parseLong(r);
            }
            
            r = props.getProperty(("delay_thumb"));
            if (r != null) {
                mDelayThumb = Long.parseLong(r);
            }
            
            p("mDelayFiles: " + mDelayFile);
            p("mDelayTime: " + mDelayFile);
            p("mDelayThumb: " + mDelayThumb);

        }
    }
    
    public void sync(String sNodeName, String sUUID){
        
        log("Scrubber started. Record size: " + mFilesDatabase.size());
        
        try {
            loadBackupProps();
            loadDbModeProp();
            
        } catch (Exception e) {
            log("Scrubber exception!");
        }
        
        int i = 0;
        
        int[] nCase = new int[9];
        int nInsertOK = 0;
        int nInsertErr = 0;
        
        FileUtils fu = new FileUtils();
        
        try {
            ArrayList mHashList = new ArrayList();
            
            Iterator It = mFilesDatabase.keySet().iterator();
            while (It.hasNext()) {
                i++;
                if (i % 1000 == 0) {
                    log("processed records : " + i + " of " + mFilesDatabase.size());                        
                    log("nCase[3] = " + nCase[3]);
                    log("nCase[4] = " + nCase[4]);
                    log("nCase[7] = " + nCase[7]);
                    log("nCase[8] = " + nCase[8]);
                    log("nInsertOK = " + nInsertOK + " nInsertErr = " + nInsertErr);
                }
                if (i % mDelayFile == 0) {
                    p(i + " objects processed. Time for sleep " + mDelayTime + " ms.");
                    Thread.sleep(mDelayTime);
                }
                String sNamer = (String)It.next();
                FileDatabaseEntry record = mFilesDatabase.get(sNamer);                
                boolean exist = checkfile(sNamer);
                //p("existe = " + exist);

                if (exist) {
                    String sNamer2 = URLDecoder.decode(sNamer, "UTF-8");
                    boolean existcass = checkfileCass(record.mMD5, sNamer2, sUUID);
                    if (existcass) {
                        //CASE 8: File=1; LocalDB=1; Cass=1 Action: none
                        nCase[8]++;
                    } else {
                        //CASE 7: File=1; LocalDB=1; Cass=0   Action: add object to Cassandra DB
                        nCase[7]++;
                        p("-case 7---------------------------------");
                        p("FOUND FILE: '" + sNamer + "' " + record.mMD5 + " " + record.mDateModified);
                        p("MISSING OBJECT: " + record.mMD5);
                        
                        File f = new File(URLDecoder.decode(sNamer, "UTF-8"));
                        DatabaseEntry dbe = new DatabaseEntry(record.mMD5, UUID.fromString(sUUID), f, (float)0.9, mDelayThumb);

                        Boolean bres = false;
                        log("Sync: Adding " + sNamer2);
                        if (dbe.dbe_action != "OOM") {
                            bres = fu.saveDatabaseEntry(dbe, "A");
                            p("bres = " + bres);
                        } else {
                            String sFile = URLEncoder.encode(f.getAbsolutePath());
                            log("There was an OOM. Removing entry '" + sFile + "'");
                            mFilesDatabase.remove(URLEncoder.encode(f.getAbsolutePath(),"UTF-8"));
                        }

                        dbe = null;

                        p("INSERTING RECORD RES: " + bres);     
                        if (bres) {
                            nInsertOK++;
                        } else {
                            nInsertErr++;
                        }                        
                    }
                } else {
                    boolean existcass = checkfileCass(record.mMD5, sNamer, sUUID);
                    if (existcass) {
                        //CASE 4: File=0; LocalDB=1; Cass=1  Action: delete object from Cassandra DB & local index
                        nCase[4]++;
                        p("-case 4---------------------------------");
                        p("MISSING FILE : " + sNamer + " " + record.mMD5 + " " + record.mDateModified);
                        p("FOUND OBJECT: " + record.mMD5);
                        if (sNamer.substring(0,1).equals("/")) {
                            p("unix trim case");
                            sNamer = sNamer.substring(1,sNamer.length());
                        }

                        DatabaseEntry dbe = new DatabaseEntry(record.mMD5, UUID.fromString(sUUID), URLDecoder.decode(sNamer, "UTF-8"));
                        Boolean bres = fu.saveDatabaseEntry(dbe, "D");
                        p("bres = " + bres);
                        log("Sync: Deleting " + sNamer);
                        
                        mHashList.add(URLEncoder.encode(sNamer, "UTF8"));
                    } else {
                        //CASE 3: File=0; LocalDB=1; Cass=0  Action: delete object from local index
                        nCase[3]++;
                        p("-case 3---------------------------------");
                        p("MISSING FILE: " + sNamer + " " + record.mMD5 + " " + record.mDateModified);
                        p("MISSING OBJECT: " + record.mMD5);

                        mHashList.add(URLEncoder.encode(sNamer, "UTF8"));
                    }
                }
            }

            It = mHashList.iterator();
            while (It.hasNext()) {
                String sNamer3 = (String)It.next();
                String sNamer = URLDecoder.decode(sNamer3, "UTF8");
                p("REMOVING: '" + sNamer + "'");
                FileDatabaseEntry record2 = mFilesDatabase.remove(sNamer);                
            }
            log("Scrubber completed. Record size: " + mFilesDatabase.size());
            
            log("nCase[3] = " + nCase[3]);
            log("nCase[4] = " + nCase[4]);
            log("nCase[7] = " + nCase[7]);
            log("nCase[8] = " + nCase[8]);
            log("nInsertOK = " + nInsertOK + " nInsertErr = " + nInsertErr);

        } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                    log("Warning: FileNotFoundException");
        } catch (IOException ex) {
                    ex.printStackTrace();
                    log("Warning: IOException");
        } catch (Exception ex) {
            p("exception");
            p(ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static boolean checkfileCass(String _key, String _path, String _uuid) throws IOException {
        String sPath = _path.replace("\\", "/");
        try {
            if (dbmode.equals("cass")) {
                return c7.syncObject(_key, "paths", _uuid, sPath);
            } else {
                return c8.syncObject(_key, _uuid, sPath);
            }
            
        } catch (Exception ex) {
            p("Read error, exiting...");
            return false;
        }
    }

    public static boolean checkfile(String sStorePath) throws IOException {

        try {
            //p("checkfile '" + sStorePath + "'");            
            String sStorePath2 = URLDecoder.decode(sStorePath, "UTF-8");
            //p("checkfile2 '" + sStorePath2 + "'");
            
            File nFile = new File(sStorePath2);
            if (nFile.exists()) {
                    long nlen = new File(sStorePath2).length();
                    //p("file length: " + nlen);
                    if (nlen > 0) {
                        return true;
                    } else {
                        return true;
                        //return false;
                    }
            } else {
                p("file doesn't exist" + sStorePath2);
                return false;
            }
        } catch (Exception e) {
            p("error, exiting...");
            return false;
        }

    }

    
    public int count(){
        return mFilesDatabase.size();
    }
    
    public void add(String _canonical_path, long _modified, String _md5){
        FileDatabaseEntry record = mFilesDatabase.put(_canonical_path, new FileDatabaseEntry(_modified,_md5));
    }

    /**
     * Test of the file entry has changed, at the same time inserts the new entry
     * @param _canonical_path The canonical identifier of the path
     * @param _modified The date of modification of the file
     * @return Boolean indicating if the entry has changed
     */
    public boolean has_changed(String _canonical_path, long _modified){
        FileDatabaseEntry record = mFilesDatabase.get(_canonical_path);
        if (record == null || record.mDateModified != _modified){
            return true;
        }else{
            return false;
        }
    }

    /**
     * Return the set of files entries (canonical paths)
     * @return
     */
    public Set<String> listFiles(){
        return mFilesDatabase.keySet();
    }

    /**
     * Save from memory to storage
     */
    public synchronized boolean save() {
        try {
            FileOutputStream fileOut = new FileOutputStream(mStorage);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(mFilesDatabase);
            out.close();
            fileOut.close();
            return true;
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            log("Warning: FileNotFoundException");
        } catch (IOException ex) {
            ex.printStackTrace();
            log("Warning: IOException");
        }
        return false;
    }

    /**
     * Load from storage into memory
     */
    public boolean load() {
        try {
            p("mStorage = " + mStorage);
            File storage = new File(mStorage);            
            if (!storage.exists())
                return false;
            FileInputStream fileIn = new FileInputStream(mStorage);
            ObjectInputStream in = new ObjectInputStream(fileIn);            
            mFilesDatabase = (HashMap<String, FileDatabaseEntry>) in.readObject();
            in.close();
            fileIn.close();
            return true;
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            log("Warning: ClassNotFoundException");
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            log("Warning: FileNotFoundException");
        } catch (IOException ex) {
            ex.printStackTrace();
            log("Warning: IOException");
        } catch (Exception ex) {
            ex.printStackTrace();
            log("Warning: Exception");
        }
        return false;
    }    
    
    
    /**
     * Connect to Cassandra Server
     */
    private static void connectCassandra(String _hostname) {
        try {
            int nRetries = 0;
            
            while (!bConnect && nRetries < 10) {
                log("Connecting to Cassandra node: '" + _hostname + "'");
                bConnect = c7.connect(_hostname,9160);
                log("connect result = " + bConnect);
                if (!bConnect) {
                    long lSleep = 10000;
                    log("connect failed. going to sleep for " + lSleep + " ms");
                    Thread.sleep(lSleep);
                    nRetries++;
                }
            }
            if (!bConnect && nRetries >= 10) {
                log("WARNING: connect to cassandra failed after 10 retries...");
            }
        } catch (Exception ex) {
            log("Exception connecting to cassandra:" + ex.getMessage());
            //System.exit(-1); pp
        }
    }
}

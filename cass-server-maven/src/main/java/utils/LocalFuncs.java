package utils;

import org.mapdb.*;

import java.text.DateFormat;
import java.text.ParseException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Scanner;
import java.util.Map;
import java.util.Collections;
import java.util.NavigableSet;
import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.ColumnParent;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.NotFoundException;
import org.apache.cassandra.thrift.SlicePredicate;
import org.apache.cassandra.thrift.SliceRange;
import org.apache.cassandra.thrift.SuperColumn;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.thrift.TException;
import static utils.Cass7Funcs.UTF8;
import static utils.Cass7Funcs.getConfig;
import static utils.Cass7Funcs.keyspace;
//import static utils.Cass7Funcs.lf;
//import static utils.Cass7Funcs.p;

import static utils.Cass7Funcs.isNodeAvailable;
import static utils.Cass7Funcs.getLocalAddress;
//import static utils.Cass7Funcs.occurences_copies;
//import static utils.Cass7Funcs.p;
import static utils.NetUtils.getfile;

import processor.FileDatabaseEntry;
import java.util.concurrent.*;

import java.util.NoSuchElementException;
import java.util.UUID;
//import static utils.Cass7Funcs.lf;
//import static utils.Cass7Funcs.occurences_hash;

import java.io.FilenameFilter;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author agf
 */
public class LocalFuncs {
    
    private static String THUMBNAIL_OUTPUT_DIR = "../../cass/pic";

    public boolean sn = false;
    public static String slast = "";
    public static String DB_PATH = "c:/temp/db2/";
    protected static Properties props = new Properties();
    static String LOG_NAME_BACKUP_DEBUG_PATH = "logs/";
    static PrintStream log = null;    
    static HashMap deadnodes_uuid = new HashMap<String, Integer>();
    static public boolean bNodesLoaded = false;
    static public boolean bRecordsLoaded = false;
    static public boolean bCacheValid = false;
    
    static String sLastMD5 = "";
   
    /* mapping of file extensions to content-types */
    static java.util.HashMap map = new java.util.HashMap();

    /* mapping of objects */
    static Map<String, Integer> occurences_uuid = Collections.synchronizedMap(new HashMap<String, Integer>());
    //public static Map<String, String> occurences_copies = Collections.synchronizedMap(new HashMap<String, String>());    
    public static Map<String, String> occurences_hidden = Collections.synchronizedMap(new HashMap<String, String>());  
    //public static Map<String, String> occurences_attr = Collections.synchronizedMap(new HashMap<String, String>());  
    
    public static Map<String, List<String>> occurences_index = Collections.synchronizedMap(new HashMap<String, List<String>>());
    public static Map<String, String> occurences_files = Collections.synchronizedMap(new HashMap<String, String>());  
    
    static DB db_r = null;
    
    static DB db_mm1 = null;
    static DB db_mm1_oc = null;
    
    static DB db_mm2 = null;
    static DB db_mm2_oc = null;
    
    static DB db_attr = null;
    static DB db_attr_oc = null;

    static DB db_cp = null;
    static DB db_cp_oc = null;

    static TxMaker tx_mm1 = null;
    static TxMaker tx_mm2 = null;
    static TxMaker tx_attr = null;
    static TxMaker tx_cp = null;
    
    //static TxMaker tx_mm1_oc = null;
    //static TxMaker tx_mm2_oc = null;
    //static TxMaker tx_attr_oc = null;
    //static TxMaker tx_cp_oc = null;
        
    //static NavigableSet<Fun.Tuple2<String,String>> mm1 = null;
    //static NavigableSet<Fun.Tuple2<String,String>> mm2 = null;
    //static ConcurrentNavigableMap<String,String> occurences_copies_w = null;    
    //static ConcurrentNavigableMap<String,String> occurences_attr_w = null;
    
    //static ConcurrentNavigableMap<String,List<String>> map = db.getTreeMap("collectionName");
    static NavigableSet<Fun.Tuple2<String,String>> multiMap = null;
    static NavigableSet<Fun.Tuple2<String,String>> multiMap2 = null;
    static ConcurrentNavigableMap<String,String> occurences_copies = null;
    static ConcurrentNavigableMap<String,String> occurences_attr = null;
    //static NavigableSet<Fun.Tuple2<String,String>> occurences_attr = null;   
    
    private static HashMap<String, FileDatabaseEntry> mFilesDatabase = new HashMap<String, FileDatabaseEntry>();
    
    public int nPhoto = 0;
    public int nMusic = 0;
    public int nTotal = 0;
    public int nVideo = 0;
    public int nOfficeDoc = 0;
    public int nOfficeXls = 0;
    public int nOfficePpt = 0;
    public int nOfficePdf = 0;
    public int nSize = 0;
    
    public int nAllTime = 0;
    public int nPast24h = 0;
    public int nPast3d = 0;
    public int nPast7d = 0;
    public int nPast14d = 0;
    public int nPast30d = 0;
    public int nPast365d = 0;   
    
    public long currentTime = 0;
        
    static public String previous_query = "xyzzy";
    static public String previous_filetype = "xyzzy";
    static public String previous_timerange = "xyzzy";
    static boolean bNewQuery = false;
       
    static boolean bLoadingCopies = false;
    static boolean bClosingDB = false;
    static boolean bUseMapDBTx = false;   //disable transactions by default   
    static boolean bNullMapDBTx = true;   //null transactions after commit
    
    PrintStream logPerfIndex = null;
    PrintStream logIndexError = null;
    String LOG_NAME_PERFORMANCE_INDEX_PATH = "logs/";

    String Filer = "";
    
    
    SortableValueMapLong<String, Long> occurences_cache_all = new SortableValueMapLong<String, Long>();

    long occurences_cache_all_ts = 0;
    
    static java.util.Hashtable mapBlack = new java.util.Hashtable();
    static java.util.Hashtable mapBlackC = new java.util.Hashtable();
    static java.util.HashMap mapNetty = new java.util.HashMap();

    
    static int NUM_FOLDERS = 3; //#of subfolders to index (default=3)
    static int FLUSH_DELAY = 10000; //flush delay MapDB (default=10second)
    
    static int COMMIT_TIMER = 60000; //commit every 1min
    static int COMMIT_PUTS = 10000; //commit every 10000 inserts
    
    static int NUM_KEYWORDS = 10; //number of keywords to index per doc   
    
    long time1 = 0;
    
    static void setSuffix(String k, String v) {
        map.put(k, v);
    }
    static int mLogLevel = 0;
    
    static int nput = 0;
    
    String _key = "";
    String sCopyInfo = "";
    String sStore = "";
    //String sub_string = "";
    //String __token = "";
    //String sAdder = "";
    
    //declare variables here so we don't need to set them as final.
    int nerror = 0;
    String _date = "";
    String _filename = "";
    String _filenamepath = "";
    boolean bBreak = false;
    
    static String appendage = "";
    static String appendageRW = "";

    public static boolean bConsole = true;

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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.LocalFuncs-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.LocalFuncs-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [LocalFuncs-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected static void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.localfuncs_" + threadID + "] " + s);
    }


    public LocalFuncs() {
        //constructor 
        Appendage app = new Appendage();
        appendage = app.getAppendage();
        appendageRW = app.getAppendageRW();
        loadProps();
        printProps();
        time1 = System.currentTimeMillis();
    }
       
    boolean isUUID(String _stmp) {
        try {
            
            int count = _stmp.split("\\-",-1).length-1;
            if (_stmp.length() == 36 && count == 4) {
                try {
                    UUID u = UUID.fromString(_stmp);
                    //p("UUID conv = " + u.toString());
                    return true;
                } catch (Exception e) {
                    //p("UUID conv failed");
                    return false;
                }                
            } else {
                //p(_stmp + " is not UUID.");
                return false;
            }
        } catch (Exception e) {
            return false;
        }        
    }
    
    int update_index(String _key, String _date, 
            String _filename, String _attributes, 
            String _fullpath,
            NavigableSet<Fun.Tuple2<String,String>> mm1,
            NavigableSet<Fun.Tuple2<String,String>> mm2,
            int _mode
            ) {
        try {
                             
            Thread.sleep(10);
            String fullpath = "";
            try {                
                fullpath = URLDecoder.decode(_fullpath, "UTF-8");
            } catch (Exception e) {
                fullpath = _fullpath;
            }
            
            //String filename = get_row_attribute("Keyspace1b","Standard1",_key, "name", null);
            //String datestr = get_row_attribute("Keyspace1b","Standard1",_key, "date_modified", null);

            DateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
            long regTime = 0;
            Date date = format.parse(_date);
            regTime = date.getTime();
            
            String sub_string = "";
            String delimiters = "";
            StringTokenizer st;
            int nerror =0;
            int n = 0;
            
            String sAdder = _key + "," + regTime + "," + _filename; 
            boolean bSkip = true;
            String filename_noext = "";
            if (_filename.contains(".")) {
                filename_noext = _filename.substring(0, _filename.indexOf("."));
                if (isHex(filename_noext) && filename_noext.length() == 32) {
                    p(">>Found a HEX file. Skipping.");
                } else {
                    String sFile32 = String.format("%32s", filename_noext).replace(" ","0");
                    if (sFile32.equalsIgnoreCase(_key)) {   
                        p(">>Filename is MD5. Skipping." + sFile32);                        
                    } else {
                        bSkip = false;                        
                    }
                }
            } else {
                p(">>Skipped file: '" + _filename + "'");
            }
            
            //index the filename
            if (!bSkip) {
                p(">>> Indexing filename string: '" + _filename + "'");
                n = index_string(_filename, _filename, sAdder, _key, regTime, mm1, mm2, _mode);
                if (n >= 0) {
                    nerror += n;
                } else {
                    return -1;
                }
            } else {
                p(">>>Skipped indexing of file: " + _filename);
            }
            
            //index the full path, if provided
            if (!fullpath.equals(_filename)) {
                
                if (!bSkip) {
                    String delim = "\\/ ";
                    StringTokenizer st2 = new StringTokenizer(fullpath, delim, true);
                    String adder = "";
                    while (st2.hasMoreTokens()) {
                        String stmp = st2.nextToken();
                        if (stmp.length() > 1 && !stmp.equals(_filename) && !isUUID(stmp)) {
                            adder = stmp + "," + adder;
                        }
                    }

                    p("adder = " + adder);
                    delim = ",";
                    st2 = new StringTokenizer(adder, delim, true);
                    int i = 0;
                    while (st2.hasMoreTokens() && i < NUM_FOLDERS) {
                        String stoken = st2.nextToken(); 
                        if (stoken.length() > 1) {
                            p("indexing file path token: " + stoken);
                            n = index_token(stoken, _filename, sAdder, _key, regTime, mm1, mm2, _mode);
                            i++;
                            if (n >= 0)  {
                                nerror += n;
                            } else {
                                return -1;
                            }
                        }
                    }                                    
                } else {
                    p("SKIP: full path index. HEX file found : " + _fullpath);                    
                }
            } else {                                
                p("SKIP: full path index. path same as filename : " + _fullpath);
            }                
                                          
            if (Cass7Funcs.is_music(_filename)) {
                
                delimiters = ",";
                st = new StringTokenizer(_attributes, delimiters, true);
                String sDate = "";
                String sTitle = "";
                String sArtist = "";
                try {
                    sDate = st.nextToken();
                    st.nextToken();
                    sTitle = st.nextToken();
                    st.nextToken();
                    sArtist = st.nextToken();                    
                } catch (NoSuchElementException e) {
                    p("[Exception]NoSuchElementException");
                }

                if (sTitle.length() > 0) {
                    n = index_string(sTitle, _filename, sAdder, _key, regTime, mm1, mm2, _mode);                                    
                    if (n >= 0)  {
                        nerror += n;
                    } else {
                        return -1;
                    }
                    //n = index_token(sTitle, _filename, sAdder, _key, regTime, mm1, mm2, _mode);                                    
                    //if (n >= 0) {
                    //    nerror += n;                        
                    //} else {
                    //    return -1;
                    //}
                }
                if (sArtist.length() > 0) {
                    n = index_string(sArtist, _filename, sAdder, _key, regTime, mm1, mm2, _mode);                    
                    if (n >= 0) {
                        nerror += n;
                    } else {
                        return -1;
                    }
                    //n = index_token(sArtist, _filename, sAdder, _key, regTime, mm1, mm2, _mode);  
                    //if (n >= 0) {
                    //    nerror += n;
                    //} else {
                    //    return -1;
                    //}
                }
            } 
            
            if (_filename.endsWith(".doc") || _filename.endsWith(".docx")) {
                String sKeywords = get_row_attribute("Keyspace1b","Standard1",_key, "keywords", null);  
                p("sKeywords = '" + sKeywords + "'");
                if (sKeywords.length() > 0) {
                    delimiters = "@";
                    st = new StringTokenizer(sKeywords, delimiters, true);
                    
                    SortableValueMapLong<String, Long> ftable = new SortableValueMapLong<String, Long>();
                    
                    while (st.hasMoreTokens()) {
                        String sToken = st.nextToken();
                        if (sToken.length() >= 4) {
                            //p("indexing keyword: " + sToken);
                            Long ncount = ftable.get(sToken);
                            
                            Object Got2;
                            int was = 0;
                            if ((Got2 = ftable.get(sToken)) != null) was = (((Long) Got2).intValue());                            
                            ftable.put(sToken, new Long(was + 1));                                    
                           
                        } // if len > 4                     
                    } // while
                    ftable.sortByValueDesc();
                                        
                    int i = 0;
                    p("NUM_KEYWORDS = " + NUM_KEYWORDS);
                    
                    for (Map.Entry entry : ftable.entrySet()) {
                        if (i < NUM_KEYWORDS) {
                            p(entry.getKey() + " " + entry.getValue());
                            n = index_token((String)entry.getKey(), _filename, sAdder, _key, regTime, mm1, mm2, _mode);
                            i++;
                        } else {
                            break;
                        }
                    }
                    
                    
                }
            }
            
            if (nerror > 0) {
                p("total update_index nerror:" + nerror);
            } 
            
            return nerror;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    
    private int index_string(String _string, 
            String _filename, 
            String _adder, 
            String _key, 
            long _regtime,
            NavigableSet<Fun.Tuple2<String,String>> mm1,
            NavigableSet<Fun.Tuple2<String,String>> mm2,
            int _mode) { 
        try {
            String sToken = "";           
            String delimiters = " !@#$%^&*()-=_+[]\\{}|;':\",./<>?";
            StringTokenizer st = new StringTokenizer(_string, delimiters, true);
            int ntotal = 0;
            while (st.hasMoreTokens()) {
                sToken = st.nextToken();
                Boolean bSkip = false;
                if (isHex(sToken) && sToken.length() > 8) bSkip = true;
                if (!bSkip) {
                    //p("Indexing token: " + sToken);
                    int n = index_token(sToken, _filename, _adder, _key, _regtime, mm1, mm2, _mode);             
                    if (n < 0) {
                        return -1;
                    } else {
                        ntotal += n;                        
                    }
                } else {
                    p("Skipping token: " + sToken);    
                }                              
            }       
            if (ntotal > 0) p("#tokens w/error total:" + ntotal);
            return ntotal;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }                         
    }
    
    
    private int index_token(String _token, 
            String _filename, 
            String _adder, 
            String _key, 
            long _regtime,
            NavigableSet<Fun.Tuple2<String,String>> mm1,
            NavigableSet<Fun.Tuple2<String,String>> mm2,
            int mode)
 {  
     try {
             
        int nok = 0;
        int nerr = 0;
        
                for (int idx = 0; idx < _token.length() + 1; ++idx) {
                  for (int j = idx + 1; j < _token.length() + 1; j++) {
                        String sub_string = _token.substring(idx, j).toLowerCase();                        
                         //sub_string = _filename;                            
                         if (sub_string.length() >= 4 && sub_string.length() <= 25) {
                             
                            if (mode == 1 || mode == 3) {
                                synchronized(mm1) {
                                        if (!bClosingDB) {
                                            Boolean add = false;
                                            int ntry = 0;
                                            while (!add && ntry < 3) {
                                                //p("ATTEMPT mm1: " + sub_string + " " + _token + " " + sub_string.length() + " " + _token.length());    
                                                Boolean bAssert=false;
                                                try {
                                                    mm1.add(Fun.t2(sub_string, _token));
                                                    nok++;    
                                                    nput++;   
                                                    add = true;
                                                } catch (AssertionError e) {
                                                    log("WARNING AssertionError mm1: " + sub_string + " " + _token + " " + sub_string.length() + " " + _token.length(), 2);                                    
                                                    e.printStackTrace();
                                                    nerr++;        
                                                    bAssert=true;
                                                } catch (InternalError e) {
                                                    log("WARNING InternalError mm1: " + sub_string + " " + _token + " " + sub_string.length() + " " + _token.length(), 2);                                    
                                                    e.printStackTrace();
                                                    nerr++;
                                                    bAssert=true;
                                                } catch (Exception e) {
                                                    log("WARNING Exception mm1: " + sub_string + " " + _token + " " + sub_string.length() + " " + _token.length(), 2);                                    
                                                    e.printStackTrace();
                                                    nerr++;
                                                    bAssert=true;
                                                } finally {
                                                    if (bAssert) {
                                                        ntry++;
                                                        p("*** mm1.add try #" + ntry);
                                                        p("[try-commit]");
                                                        try {
                                                            if (db_mm1 != null) {
                                                                db_mm1.commit();
                                                            }
                                                        } catch (Exception ef) {
                                                            ef.printStackTrace();
                                                        }
                                                        p("[close]");
                                                        closeMapDB();
                                                        try {
                                                            Thread.sleep(1000);
                                                        } catch (Exception e) {
                                                            
                                                        }
                                                        p("[open]");
                                                        open_mapdb();
                                                        db_mm1_oc = tx_mm1.makeTx();
                                                        mm1 = db_mm1_oc.getTreeSet("autocomplete");
                                                        nerror++;                                                    
                                                    }
                                                }                                               
                                            }
                                        }                                                             
                            }
                            }
                            
                            if (mode == 2 || mode == 3) {
                                synchronized(mm2) {
                                    String sAdder = _key + "," + _regtime + "," + _filename;                            
                                    
                                        if (!bClosingDB) {
                                            Boolean add = false;
                                            int ntry = 0;
                                            boolean bAssert = false;
                                            while (!add && ntry < 3) {
                                                //p("ATTEMPT mm2: " + sub_string + " " + sAdder + " " + sub_string.length() + " " + sAdder.length());                                    
                                                try {
                                                    mm2.add(Fun.t2(sub_string, sAdder));
                                                    nok++;    
                                                    nput++;   
                                                    add = true;                                                 
                                                } catch (AssertionError e) {
                                                    log("WARNING AssertionError mm2: " + sub_string + " " + sAdder + " " + sub_string.length() + " " + sAdder.length(), 2);                                    
                                                    e.printStackTrace();
                                                    nerr++;
                                                    bAssert = true;
                                                } catch (InternalError e) {
                                                    log("WARNING InternalError mm2: " + sub_string + " " + sAdder + " " + sub_string.length() + " " + sAdder.length(), 2);
                                                    e.printStackTrace();
                                                    nerr++;
                                                    bAssert = true;
                                                } catch (Exception e) {
                                                    log("WARNING Exception mm2: " + sub_string + " " + sAdder + " " + sub_string.length() + " " + sAdder.length(), 2);                                    
                                                    e.printStackTrace();
                                                    nerr++;                                                                                          
                                                    bAssert = true;
                                                } finally {
                                                    if (bAssert) {
                                                        ntry++;
                                                        p("*** mm2.add try #" + ntry);
                                                        p("[try-commit2]");
                                                        try {
                                                            if (db_mm2 != null) {
                                                                db_mm2.commit();
                                                            }
                                                        } catch (Exception ef) {
                                                            ef.printStackTrace();
                                                        }
                                                        closeMapDB();
                                                        Thread.sleep(1000);
                                                        open_mapdb();
                                                        db_mm2_oc = tx_mm2.makeTx();
                                                        mm2 = db_mm2_oc.getTreeSet("md5");
                                                        nerror++;                                                        
                                                    }
                                                }
                                            } //while                                 
                                        } //if
                                } // synchronized
                            } // if mode                                                        
                        }             
                } //for #2
            } //for#1        
            if (nerr > 0) p("nok: " + nok + " nerr:" + nerr);
            return nerr;
     } catch (Exception e) {
            e.printStackTrace();
            try {
                p("exception index_token.");                
                Thread.sleep(2000);
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            return -1;         
     }
 }
        
                            
    public int check_commit(Map<String,String>MapBatches) {
        try {
            long time2 = System.currentTimeMillis();
            p("nput: " + nput + " time diff: " + (time2 - time1));  
            
            boolean bCommit = false;            
            if ((time2 - time1) > COMMIT_TIMER) {
                p("timer expired.");
                bCommit = true;
            }
            
            if (nput > COMMIT_PUTS) {
                p("timer has not expired, but there are sufficient puts.");
                bCommit = true;    
            }

            if (bCommit) {
                if (nput > 0) {
                    //there is something to commit.
                    log("TIME TO COMMIT -- nput: " + nput + " time diff: " + (time2 - time1),0);  
                    int i = 0;
                    while (bClosingDB) {
                        p("Waiting for DB close...: try#" + i);
                        Thread.sleep(100);
                        i++;
                    }
                    open_mapdb();
                    p("commit[a]");
                    nput = 0;
                    try {
                        if (!bClosingDB) {
                            if (db_mm1_oc == null) {                               
                                p("*** Null case: db_mm1_oc");
                                db_mm1_oc = tx_mm1.makeTx();
                            }
                            Boolean com = false;
                            int ntry = 0;
                            while (!com && ntry <=5) {
                                try {
                                    db_mm1_oc.commit();    
                                    com = true;
                                } catch (IllegalAccessError e) {
                                    ntry++;
                                    p("WARNING: ILLEGAL ACCESS ERROR ON MM1_COMMIT #" + ntry);
                                    open_mapdb();
                                    db_mm1_oc = tx_mm1.makeTx();
                                    Thread.sleep(500);
                                }
                            }
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        e.printStackTrace();
                        log("WARNING: ArrayIndexOutOfBoundsException in commit mm1",0);                        
                    } catch (OutOfMemoryError  e) {
                        e.printStackTrace();
                        log("WARNING: OutOfMemoryError in commit mm1",0);
                    } catch (Exception e) {
                        e.printStackTrace();
                        log("WARNING: Exception in commit mm1",0);                        
                    }
                    try {
                        if (!bClosingDB) {
                            if (db_mm2_oc == null) {
                                p("*** Null case: db_mm2_oc");
                                db_mm2_oc = tx_mm2.makeTx();
                            }
                            Boolean com = false;
                            int ntry = 0;
                            while (!com && ntry <=5) {
                                try {
                                    db_mm2_oc.commit();   
                                    com = true;
                                } catch (IllegalAccessError e) {
                                    ntry++;
                                    p("WARNING: ILLEGAL ACCESS ERROR ON MM2_COMMIT #" + ntry);
                                    open_mapdb();
                                    db_mm2_oc = tx_mm2.makeTx();
                                    Thread.sleep(500);
                                }
                            }
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        e.printStackTrace();
                        log("WARNING: ArrayIndexOutOfBoundsException in commit mm2",0);                        
                    } catch (OutOfMemoryError  e) {
                        e.printStackTrace();
                        log("WARNING: OutOfMemoryError in commit mm2",0);
                    } catch (Exception e) {
                        e.printStackTrace();
                        log("WARNING: Exception in commit mm2",0);                        
                    }
                    try {
                        if (!bClosingDB) {
                            if (db_cp_oc == null) {
                                p("*** Null case: db_cp_oc");
                                db_cp_oc = tx_cp.makeTx();
                            }
                            Boolean com = false;
                            int ntry = 0;
                            while (!com && ntry <=5) {
                                try {
                                    db_cp_oc.commit();   
                                    com = true;
                                } catch (IllegalAccessError e) {
                                    ntry++;
                                    p("WARNING: ILLEGAL ACCESS ERROR ON CP_COMMIT #" + ntry);
                                    open_mapdb();
                                    db_cp_oc = tx_cp.makeTx();
                                    Thread.sleep(500);
                                }
                            }
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        e.printStackTrace();
                        log("WARNING: ArrayIndexOutOfBoundsException in commit cp",0);                        
                    } catch (OutOfMemoryError  e) {
                        e.printStackTrace();
                        log("WARNING: OutOfMemoryError in commit cp",0);
                    } catch (Exception e) {
                        e.printStackTrace();
                        log("WARNING: Exception in commit cp",0);                        
                    }
                    try {
                        if (!bClosingDB) {
                            if (db_attr_oc == null) {
                                p("*** Null case: db_attr_oc");
                                db_attr_oc = tx_attr.makeTx();
                            }
                            Boolean com = false;
                            int ntry = 0;
                            while (!com && ntry <=5) {
                                try {
                                    db_attr_oc.commit();   
                                    com = true;
                                } catch (IllegalAccessError e) {
                                    ntry++;
                                    p("WARNING: ILLEGAL ACCESS ERROR ON ATTR_COMMIT #" + ntry);
                                    open_mapdb();
                                    db_attr_oc = tx_attr.makeTx();
                                    Thread.sleep(500);
                                }
                            }
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        e.printStackTrace();
                        log("WARNING: ArrayIndexOutOfBoundsException in commit attr",0);                        
                    } catch (OutOfMemoryError  e) {
                        e.printStackTrace();
                        log("WARNING: OutOfMemoryError in commit attr",0);
                    } catch (Exception e) {
                        e.printStackTrace();
                        log("WARNING: Exception in commit attr",0);                        
                    }
                } else {
                    p("Skip commit. There are no puts.");
                }
            
                p("commit[b]");                    
                if (db_mm1_oc != null && !db_mm1_oc.isClosed() && !bClosingDB) {
                    try {
                        db_mm1_oc.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        log("WARNING: Exception in close mm1",0);                        
                    }
                }
                if (db_mm2_oc != null && !db_mm2_oc.isClosed() && !bClosingDB) {
                    try {
                        db_mm2_oc.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        log("WARNING: Exception in close mm2",0);                                
                    }
                }
                if (db_cp_oc != null && !db_cp_oc.isClosed() && !bClosingDB) {
                    try {
                        db_cp_oc.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        log("WARNING: Exception in close cp",0);                                                        
                    }
                }
                if (db_attr_oc != null && !db_attr_oc.isClosed() && !bClosingDB) {
                    try {
                        db_attr_oc.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        log("WARNING: Exception in close attr",0);                                
                    }
                }

                p("commit[c]");
                db_mm1_oc = null;
                db_mm2_oc = null;
                db_cp_oc = null;
                db_attr_oc = null;

                p("commit[d]");
                //mm1 = null;
                //mm2 = null;
                //occurences_copies_w = null;
                //occurences_attr_w = null;

                if (bNullMapDBTx) {
                    p("commit[e]-tx close");
                    if (tx_mm1 != null) tx_mm1.close();
                    if (tx_mm2 != null) tx_mm2.close();
                    if (tx_cp != null) tx_cp.close();
                    if (tx_attr != null) tx_attr.close();

                    p("commit[f]-tx null");
                    tx_mm1 = null;
                    tx_mm2 = null;
                    tx_cp = null;
                    tx_attr = null;                    
                } else {
                    p("skipp null tx.");
                }

                //p("commit[g]-gc");
                //Runtime.getRuntime().gc();

                p("commit[h]");
                                
                log("deleting IDX files.",2);
                
                Iterator it = MapBatches.keySet().iterator();                    
                while (it.hasNext()) {
                    String sFilePath = (String)it.next();

                    File f = new File(sFilePath);
                    if (f.delete()) {
                        log(f.getCanonicalPath() + " delete OK",0);                        
                    } else {
                        log(f.getCanonicalPath() + " delete FAIL",0);                            
                    }
                    it.remove();
                }
                
                //reset timer
                time1 = System.currentTimeMillis();                                                

                return 1;
            } 
            return 0;
    } catch (Exception e) {
        e.printStackTrace();
        try {
            Thread.sleep(5000);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return -1;
    }
    }
       
    public void open_mapdb() {
        
        try {
            p("open db");                                        
            String sFile = appendageRW + "../rtserver/testdb";
            String sDBName = readDoc(appendageRW + "../rtserver/dbname.txt");
            if (sDBName.length() > 0) {
                sFile = sDBName.trim();
            }
            p("[LocalFuncs.open_mapdb()] DBName: '" + sFile + "'");
            
            String sAppend = "./";
            if (appendage.length() > 0) sAppend = "";
            
            if (tx_mm1 == null) {     
                //.asyncWriteEnable()
                tx_mm1 = DBMaker.newFileDB(new File(sAppend + sFile + "_mm1")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                            
            } else {
                p("skip open tx_mm1");
            }
            if (tx_mm2 == null) {
                tx_mm2 = DBMaker.newFileDB(new File(sAppend + sFile + "_mm2")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();
            } else {
                p("skip open tx_mm2");                
            }                            
            if (tx_cp == null) {
                tx_cp = DBMaker.newFileDB(new File(sAppend + sFile + "_cp")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();
            } else {
                p("skip open tx_cp");                                
            }                          
            if (tx_attr == null) {
                tx_attr = DBMaker.newFileDB(new File(sAppend + sFile + "_attr")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();
            } else {
                p("skip open tx_attr");                                
            }                                                                               
        } catch (Exception e) {
            e.printStackTrace();
        }                   
    }
    
    public int update_occurences_copies(String _batchid) {
        try {              
            
            open_mapdb();       
            
           
            if (db_mm1_oc == null) {
                p("make tx ");                                        
                db_mm1_oc = tx_mm1.makeTx();
                db_mm2_oc = tx_mm2.makeTx();
                db_cp_oc = tx_cp.makeTx();
                db_attr_oc = tx_attr.makeTx();
                
                p("get tree");
                //mm1 = db_mm1_oc.getTreeSet("autocomplete"); 
                //mm2 = db_mm2_oc.getTreeSet("md5"); 
                //occurences_copies_w = db_cp_oc.getTreeMap("numberofcopies");
                //occurences_attr_w = db_attr_oc.getTreeMap("attributes");                
            } else {
                p("skip make tx / get tree");
            }           
                    
            bLoadingCopies = true;
            bNodesLoaded = false;  //reload nodes just in case.           
            
            log("Updating number of copies for Batch id: " +_batchid, 2);
            String _name = "batch@" + _batchid;
            String filename = DB_PATH + File.separator + "Standard1" + File.separator + _name;
            File fh = new File(filename);
            if (fh.exists()) {
                
                InetAddress clientIP = NetUtils.getLocalAddressNonLoopback2();
                //InetAddress clientIP = InetAddress.getLocalHost();
                String sLocalIP = "127.0.0.1";
                if (clientIP != null) {
                    sLocalIP = clientIP.getHostAddress();
                }

                FileInputStream fis = new FileInputStream(filename);
                Scanner scanner = new Scanner(fis);                       
                int i = 0;   
                //int nerror = 0;
                
                bBreak = false;
                while (scanner.hasNext() && !bBreak) {
                    String sCurrentLine = scanner.nextLine();                    
                    String delimiters = ",";
                    StringTokenizer st = new StringTokenizer(sCurrentLine, delimiters, true);
                    //String _date = "";
                    
                    //String _filenamepath = "";
                    try {
                        _date = st.nextToken();
                        st.nextToken();
                        _key = st.nextToken();                         
                        st.nextToken();
                        
                        String fp = st.nextToken();

                        try {                
                            _filenamepath = URLDecoder.decode(fp, "UTF-8");
                        } catch (Exception e) {
                            _filenamepath = fp;
                        }
                        
                    } catch (Exception e) {
                        log("WARNING: Exception Parsing Token. Batch: " + _batchid + " Line: '" + sCurrentLine +  "'",0);
                        e.printStackTrace();
                    }
                    sCopyInfo = getNumberofCopies("paths", _key, sLocalIP, sLocalIP, false);                        
                    //synchronized(occurences_copies_w) {
//                        for(String s: Bind.findSecondaryKeys(occurences_copies, _key)){
//                            p("Removing NumCopies entry: " + _key + " " + s);
//                            occurences_copies.remove(Fun.t2(_key, s));                            
//                        }
                        p("Adding NumCopies entry: " + _key + " " + sCopyInfo);
                        //occurences_copies.add(Fun.t2(_key, sCopyInfo)); 
                        
                        while (bClosingDB) {
                            p("Waiting for DB close...: try#" + i);
                            Thread.sleep(100);
                            i++;
                        }
                        open_mapdb();

                        try {
                            if (!bClosingDB) { 
                                Boolean bput = false;
                                if (bUseMapDBTx) {
                                    p("**** Using TX *****[copy]");
                                    tx_cp.execute(new TxBlock() {
                                        @Override public void tx(DB db) throws TxRollbackException {
                                            Map occurences_copies_w = db.getTreeMap("numberofcopies");
                                            occurences_copies_w.put(_key, sCopyInfo);
                                        }
                                    });
                                    bput = true;
                                } else {
                                    int j = 0;
                                    while (!bput && j<5) {
                                        try {
                                            p("**** Not Using TX[copy] *****");
                                            Map occurences_copies_w = db_cp_oc.getTreeMap("numberofcopies");
                                            occurences_copies_w.put(_key, sCopyInfo);
                                            bput = true;
                                            nput++;
                                        } catch (IllegalAccessError e) {
                                            j++;
                                            p("*** IllegalAccessError[copy]: Try reopendb #" + j );
                                            e.printStackTrace();
                                            Thread.sleep(500);
                                            open_mapdb();
                                            db_cp_oc = tx_cp.makeTx();                                          
                                        }
                                    }
                                    if (!bput) nerror++;
                                }
                            } else {
                                p("***Closing DB - Skipping PUT[copies]");
                            }
                        } catch (TxRollbackException e) {
                            log("WARNING: There was a TxRollbackException[copies]: " + _key + "," + sCopyInfo, 0);
                            nerror++;
                        } catch (OutOfMemoryError  e) {
                            e.printStackTrace();
                            p("WARNING: OUT OF MEMORY ERROR when adding #copies: " + _key + "," + sCopyInfo);
                            nerror++;
                        } catch (InternalError e) {
                            e.printStackTrace();
                            nerror++;                            
                        } catch (Exception e) {
                            //p("EXCEPTION: multimap2 " + sub_string + " " + sAdder + " " + sub_string.length() + " " + sAdder.length());
                            //e.printStackTrace();
                            nerror++;
                        }                         
                    //}                    
                    i++;

                    //String _filename = "";
                    if (_filenamepath.contains("/")) {                        
                        delimiters = "/";
                        st = new StringTokenizer(_filenamepath, delimiters, true);
                        while (st.hasMoreTokens()) {                                                  
                            String token = st.nextToken();
                            if (token.length() > 1) {
                                _filename = token;
                            }                            
                        }
                    } else {
                        _filename = _filenamepath;
                    }
                    p("[a]filename = " + _filename);                    
                    p("[a]filenamepath = " + _filenamepath);
                                        
                    String sAttr = ",";
                    if (Cass7Funcs.is_music(_filename)) {
                        sAttr = get_row_attributes(keyspace, "Standard1", _key, "title", "artist");                            
                    }
                    if (Cass7Funcs.is_photo(_filename)) {
                        sAttr = get_row_attributes(keyspace, "Standard1", _key, "img_height", "img_width");                            
                    }
                    if (Cass7Funcs.is_pdf(_filename)) {
                        sAttr = get_row_attributes(keyspace, "Standard1", _key, "img_height", "img_width");                            
                    }

                    sStore =  _date + "," + sAttr;
                    p("Storing attributes: " + _key + " = '" + sStore + "'");
                    //synchronized(occurences_attr_w) {
                        try {
                            if (!bClosingDB) {
                                if (bUseMapDBTx) {
                                    p("**** Using TX[attr]*****");
                                    tx_attr.execute(new TxBlock() {
                                        @Override public void tx(DB db) throws TxRollbackException {
                                            Map occurences_attr_w = db.getTreeMap("attributes");
                                            occurences_attr_w.put(_key, sStore);
                                        }
                                    });                                    
                                } else {                                    
                                    Boolean bput = false;
                                    int j = 0;
                                    while (!bput && j<5) {
                                        try {
                                            p("**** Not Using TX[attr]*****");
                                            Map occurences_attr_w = db_attr_oc.getTreeMap("attributes");    
                                            occurences_attr_w.put(_key, sStore);
                                            bput = true;
                                            nput++;
                                        } catch (IllegalAccessError e) {
                                            j++;
                                            p("*** IllegalAccessError[attr]: Try reopendb #" + j );
                                            e.printStackTrace();
                                            Thread.sleep(500);
                                            open_mapdb();
                                            db_attr_oc = tx_attr.makeTx();                                          
                                        }
                                    }
                                    if (!bput) nerror++;
                                }
                            } else {
                                p("***Closing DB - Skipping PUT[attr]");    
                            }
                        } catch (TxRollbackException e) {
                            log("WARNING: There was a TxRollbackException[attr]: " + _key + "," + sStore, 0);
                            nerror++;
                        } catch (OutOfMemoryError  e) {
                            e.printStackTrace();
                            p("WARNING: OUT OF MEMORY ERROR when adding ocurrences: " + _key + "," + sStore);
                            nerror++;
                        } catch (InternalError e) {
                            e.printStackTrace();
                            nerror++;                        
                        } catch (Exception e) {
                            //p("EXCEPTION: multimap2 " + sub_string + " " + sAdder + " " + sub_string.length() + " " + sAdder.length());
                            //e.printStackTrace();
                            nerror++;
                        }                        
                        //occurences_attr.add(Fun.t2(_key, sStore));
                    //}                                        
                    
                    p("!!Updating index #" + i + " " + _key + " " + _date + " " + _filename + " batch id: " + _batchid + "filenamepath: " + _filenamepath);

                    if (!bUseMapDBTx) {
                        p("**** Not Using TX *****");
                        Boolean bput = false;
                        int j = 0;
                        while (!bput && j<5) {
                            boolean bAssert = false;
                            try {
                                p("**** Not Using TX[mm1]*****");
                                NavigableSet<Fun.Tuple2<String,String>> mm1 = db_mm1_oc.getTreeSet("autocomplete"); 
                                NavigableSet<Fun.Tuple2<String,String>> mm2 = db_mm2_oc.getTreeSet("md5"); 
                                int n = update_index(_key, _date, _filename, sStore, _filenamepath, mm1, mm2, 3);   
                                if (n >= 0)  {
                                    nerror += n;
                                } else {
                                    p("break");
                                    //break;
                                    bBreak = true;
                                }
                                bput = true;
                            } catch (IllegalAccessError e) {
                                j++;
                                p("*** IllegalAccessError[mm1]: Try reopendb #" + j );
                                e.printStackTrace();
                                bAssert = true;
                            } catch (AssertionError e) {
                                j++;
                                p("*** AssertionError[mm1]: Try reopendb #" + j );
                                e.printStackTrace();
                                bAssert = true;
                            } finally {
                                if (bAssert) {
                                    Thread.sleep(500);
                                    open_mapdb();
                                    db_mm1_oc = tx_mm1.makeTx();
                                    db_mm2_oc = tx_mm2.makeTx();
                                }
                            }
                        }                    
                    } else {
                            p("**** Using TX *****");                       
                            try {
                                tx_mm1.execute(new TxBlock() {
                                @Override public void tx(DB db) throws TxRollbackException {

                                p("**TX1: >>Updating index #" + _key + " " + _date + " " + _filename + "filenamepath: " + _filenamepath);
                                
                                //db_mm1_oc = tx_mm1.makeTx();
                                NavigableSet<Fun.Tuple2<String,String>> mm1 = db.getTreeSet("autocomplete"); 

                                //db_mm2_oc = tx_mm2.makeTx();
                                //NavigableSet<Fun.Tuple2<String,String>> mm2 = db.getTreeSet("md5"); 
                                
                                int n = update_index(_key, _date, _filename, sStore, _filenamepath, mm1, null, 1);      
                                
                                p("TX1 res: " + n);
                                
                                if (n >= 0)  {
                                    nerror += n;
                                } else {
                                    p("break");
                                    //break;
                                    bBreak = true;
                                }
                            }
                        });                        
                            } catch (TxRollbackException e) {
                                log("WARNING: There has been a Rollback exception in TX1. Updating index #" + i + " " + _key + " " + _date + " " + _filename + " batch id: " + _batchid + "filenamepath: " + _filenamepath, 0);

                                e.printStackTrace();                        
                                bBreak = true;
                                Thread.sleep(5000);
                            } 
                    
                    try {
                        tx_mm2.execute(new TxBlock() {
                            @Override public void tx(DB db) throws TxRollbackException {

                                p("**TX2: >>Updating index #" + _key + " " + _date + " " + _filename + "filenamepath: " + _filenamepath);
                                
                                //db_mm1_oc = tx_mm1.makeTx();
                                //NavigableSet<Fun.Tuple2<String,String>> mm1 = db.getTreeSet("autocomplete"); 

                                //db_mm2_oc = tx_mm2.makeTx();
                                NavigableSet<Fun.Tuple2<String,String>> mm2 = db.getTreeSet("md5"); 
                                
                                int n = update_index(_key, _date, _filename, sStore, _filenamepath, null, mm2, 2);    
                                
                                p("TX2 res: " + n);

                                if (n >= 0)  {
                                    nerror += n;
                                } else {
                                    p("break");
                                    //break;
                                    bBreak = true;
                                }
                            }
                        });                        
                    } catch (TxRollbackException e) {
                        log("WARNING: There has been a Rollback exception in TX2. Updating index #" + i + " " + _key + " " + _date + " " + _filename + " batch id: " + _batchid + "filenamepath: " + _filenamepath, 0);

                        e.printStackTrace();                        
                        bBreak = true;
                        Thread.sleep(5000);
                    }
                }


                    p("ZZZzzz...[10ms]");
                    Thread.sleep(10);   
                }
                bCacheValid = false; //invalidate the cache
                bLoadingCopies = false; //no longer updating copies 
                p("#errors:" + nerror + " nput: " + nput);
                if (nerror == 0) {
                    log("DONE OK - Processed Batch id: " +_batchid + " " + i + " elements. nput=" + nput, 0);
                    return 1;                    
                } else {
                    log("WARNING: Need to ROLLBACK - number of copies for batch id " +_batchid + " " + i + " elements.", 0);
                    
                    if (bUseMapDBTx) {
                            try {
                                p("rollback mm1");
                                db_mm1_oc.rollback();
                            } catch (Exception e) {
                                e.printStackTrace();
                                log("WARNING: Exception during rollback db_mm1_oc", 0);
                            }                 

                            try {
                                p("rollback mm2");
                                db_mm2_oc.rollback();
                            } catch (Exception e) {
                                e.printStackTrace();
                                log("WARNING: Exception during rollback db_mm2_oc", 0);
                            }                 

                            try {
                                p("rollback cp");
                                db_cp_oc.rollback();
                            } catch (Exception e) {
                                e.printStackTrace();
                                log("WARNING: Exception during rollback db_cp_oc", 0);
                            }                 

                            try {
                                p("rollback attr");
                                db_attr_oc.rollback();
                            } catch (Exception e) {
                                e.printStackTrace();
                                log("WARNING: Exception during rollback db_attr_oc", 0);
                            }                         
                    } else {
                        p("Skip rollback. Not using TX...");
                    }
                    
                    try {
                        p("close mm1");
                        db_mm1_oc.close();
                    } catch (IllegalAccessError e) {
                        e.printStackTrace();
                    }
                    try {
                        p("close mm2");
                        db_mm2_oc.close();
                    } catch (IllegalAccessError e) {
                        e.printStackTrace();
                    }
                    try {
                        p("close cpp");
                        db_cp_oc.close();
                    } catch (IllegalAccessError e) {
                        e.printStackTrace();
                    }
                    try {
                        p("close attr");
                        db_attr_oc.close();
                    } catch (IllegalAccessError e) {
                        e.printStackTrace();
                    }
                    
                    //p("tx null");
                    //tx_mm1 = null;
                    //tx_mm2 = null;
                    //tx_cp = null;
                    //tx_attr = null;
                    
                    return -1;
                }                
            } else {
                log("WARNING: Batch file does not exist.", 0);
                return -1;
            }      
        } catch (Exception e) {
            e.printStackTrace();
            log("WARNING : There was an error processing Batch id: " +_batchid, 0);
            return -1;
        } 
        
        
    }
        
    public void remove_hidden(String _key) {
        p("Removing hidden object: " + _key);
        synchronized(occurences_hidden) {
            occurences_hidden.remove(_key);
        }
    }
    
    public void insert_hidden(String _key, String _value) {
        p("Hiding object: " + _key + " " + _value);
        synchronized(occurences_hidden) {
            occurences_hidden.put(_key, _value);
        }
    }
    
    public HashMap<String, FileDatabaseEntry> loadRecords(String _mStorage) {
        try {     
            
            HashMap<String, FileDatabaseEntry> tmpHash = new HashMap<String, FileDatabaseEntry>();
            
            p("loadRecords()");
            p(_mStorage);
            File storage = new File(_mStorage);            
            if (!storage.exists())
                return null;
            FileInputStream fileIn = new FileInputStream(_mStorage);
            ObjectInputStream in = new ObjectInputStream(fileIn);            
            tmpHash = (HashMap<String, FileDatabaseEntry>) in.readObject();            
            
            p("Size = " + tmpHash.size());
            
            in.close();
            fileIn.close();                                        
            return tmpHash;
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            log("Warning: ClassNotFoundException",0);
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            log("Warning: FileNotFoundException",0);
        } catch (IOException ex) {
            ex.printStackTrace();
            log("Warning: IOException", 0);
        } catch (Exception ex) {
            ex.printStackTrace();
            log("Warning: Exception",0);
        }
        return null;
    } 
    
    public void UpdateBlacklistedFiles(String _ServerIP) {
            
        try {            
            
            DB db_cp = tx_cp.makeTx();            
            ConcurrentNavigableMap<String,String> occurences_copies_w = db_cp.getTreeMap("numberofcopies");

            p("UpdateBlacklistedFiles!!!");
//            String mStorage = "../scrubber/data/records.db";
              String sLocalIP = _ServerIP;
//            mFilesDatabase = loadRecords(mStorage);
//            p("size = " + mFilesDatabase.size());
            
            if (!bRecordsLoaded) {
                    loadRecords();
                    bRecordsLoaded = true;                
            }                
                       
            if (!bNodesLoaded) {
                            p("Loading Blacklist");
                            loadBlacklistMap();
                            loadBlacklistContainsMap();
                            p("Loading Nodes");
                            int r = loadNodes("nodes", "NodeInfo");                            
                            bNodesLoaded = true;    
            } else {
                //p("Skipping loadNodes(). Already loaded.");
            }
            
            Iterator It = mFilesDatabase.keySet().iterator();
            int i = 0;
            while (It.hasNext()) {
                i++;
                String sFilePath = (String)It.next();                
                String sFilePath2 = URLDecoder.decode(sFilePath, UTF8);
                String sFilePath3 = sFilePath2.replace("\\", "/");
                
                Object entry = mFilesDatabase.get(sFilePath);
                FileDatabaseEntry fde = (FileDatabaseEntry)entry;                
                
                //p(sFilePath3 + " " + isBlacklisted(sFilePath3));
                        
                if (isBlacklisted(sFilePath3)) {                                        
                    String sCopyInfo = getNumberofCopies("paths", fde.mMD5, sLocalIP, _ServerIP, true);
                    p(i + sFilePath2 + " " + sFilePath2 + " blacklisted:" + fde.mMD5 + " " + sCopyInfo); 
                    //Thread.sleep(500);
                    synchronized(occurences_copies_w) {
                        //occurences_copies.add(Fun.t2(fde.mMD5, sCopyInfo));  
                        occurences_copies_w.put(fde.mMD5, sCopyInfo);
                    }
                    
                } else {
                    //p(i + sFilePath2 + " " + sFilePath3 + " OK:" + fde.mMD5);                    
                }
            }
            db_cp.commit();
            db_cp.close();
            
        } catch (Exception e) {
            p("Exception: [UpdateBlacklistedFiles]");
        }
        
        
    }
    
    public void loadHiddenFiles() {
        //occurences_copies = Collections.synchronizedMap(occurences_copies);
            
        File f = new File(DB_PATH + File.separator + "Standard1" + File.separator + "hidden@");
        if (f.exists()) {
            Filer = readDoc(DB_PATH + File.separator + "Standard1" + File.separator + "hidden@");
            Scanner scanner = new Scanner(Filer);
            
            int i = 0;
                while (scanner.hasNext()) {
                                        
                    i++;
                    if (i % 100 == 0) {
                        p("Loaded: " + i);
                    }
                    String sCurrentLine = scanner.nextLine();
                    //p("line" + i + ":" + sCurrentLine);

                    String delimiters = ",";
                    StringTokenizer st = new StringTokenizer(sCurrentLine, delimiters, true);

                    String name = "";
                    String value = "";
                    boolean bOK = false;
                    try {
                        name = st.nextToken();  //name=MD5
                        st.nextToken();
                        value = st.nextToken();  //value=hidden tag
                        bOK = true;

                        synchronized(occurences_hidden) {
                            occurences_hidden.put(name, value);
                        }
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    }                    
                }
                p("Loaded Hidden: " + i);
        }
    }
    
    void do_index_mapdb() {
        String valfilename = "";
        String sub_string = "";
        try {
            //mFilesDatabase = loadRecords("../scrubber/data/records.db");
            loadRecords();
            Iterator It = mFilesDatabase.keySet().iterator();
            int i = 0;
            while (It.hasNext()) {
                i++;

                if (i % 500 == 0) {
                    log("Index Processed: " + i, 0);   
                    //Thread.sleep(3000);
                    //db.commit();  //persist changes into disk
                    Thread.sleep(50);
                }
                
                String sFilePath = (String)It.next();                
                String sFilePath2 = URLDecoder.decode(sFilePath, "UTF-8");
                String sFilePath3 = sFilePath2.replace("\\", "/");
                valfilename = sFilePath3.substring(sFilePath3.lastIndexOf("/")+1, sFilePath3.length());
                Object entry = mFilesDatabase.get(sFilePath);
                FileDatabaseEntry fde = (FileDatabaseEntry)entry;

                String valfilename2 = valfilename.substring(0,valfilename.indexOf(".")).toLowerCase();
                boolean bSkip = false;
                if (isHex(valfilename2)) bSkip = true;
                if (valfilename2.length() > 40) bSkip = true;
                p("indexing file #" + i + ": " + valfilename + " skip=" + bSkip);
                if (!bSkip) {                     
                    DateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
                    String sDate = sdf.format(fde.mDateModified);                     
                    
                    String sAttr = ",";
                    if (Cass7Funcs.is_music(valfilename)) {
                        sAttr = get_row_attributes(keyspace, "Standard1", fde.mMD5, "title", "artist");                            
                    }
                    if (Cass7Funcs.is_photo(valfilename)) {
                        sAttr = get_row_attributes(keyspace, "Standard1", fde.mMD5, "img_height", "img_width");                            
                    }
                    String sStore = sDate + "," + sAttr;
                    p("updating index " + fde.mMD5 + " " + sDate + " " + valfilename + " '" + sStore + "' " + sFilePath3);
                    //update_index(fde.mMD5, sDate, valfilename, sStore, sFilePath3);    
                }
                                                                                                                       
            } 
            log("Total Index Processed: " + i, 0);  
            if (i > 0) {
                p("commit to disk");
                //db.commit(); //persist changes into disk
                p("DONE commit to disk");
            } else {
                p("SKIP commit to disk.");
            }

            
        } catch (Exception e) {
            p("error trying to write" + sub_string + " " + valfilename);
            e.printStackTrace();
        }        
    }
    
    private static boolean isHex(String _string) {
           return _string.matches("[0-9A-Fa-f]+");           
    }
      
    
    int closeMapDB() {
        try {
            bClosingDB = true;
            if (db_mm1_oc != null)  {
                p("isclosed DB-mm1oc: " + db_mm1_oc.isClosed());
                if (!db_mm1_oc.isClosed()) {
                    try {                        
                        db_mm1_oc.rollback();
                        db_mm1_oc.close();
                    } catch (IllegalAccessError e) {
                        p("*** EXCEPTION: IllegalAccessError[1]");
                    }
                }
            } else {
                p("close DB-mm1oc-null");                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (db_mm2_oc != null) {
                p("isclosed DB-mm2oc: " + db_mm2_oc.isClosed());
                if (!db_mm2_oc.isClosed()) {
                    try {
                        db_mm2_oc.rollback();
                        db_mm2_oc.close();
                    } catch (IllegalAccessError e) {
                        p("*** EXCEPTION: IllegalAccessError[2]");    
                    }
                }
            } else {
                p("close DB-mm2oc-null");                                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {   
            if (db_cp_oc != null) {
                p("isclosed DB-cpoc: " + db_cp_oc.isClosed());
                if (!db_cp_oc.isClosed()) {
                    try {
                        db_cp_oc.rollback();
                        db_cp_oc.close();                
                    } catch (IllegalAccessError e) {
                        p("*** EXCEPTION: IllegalAccessError[3]");    
                    }
                }
            } else {
                p("close DB-cp_oc-null");                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {  
            if (db_attr_oc != null) {
                p("isclosed DB-attroc: " + db_attr_oc.isClosed());
                if (!db_attr_oc.isClosed()) {
                    try {
                        db_attr_oc.rollback();
                        db_attr_oc.close();                
                    } catch (IllegalAccessError e) {
                        p("*** EXCEPTION: IllegalAccessError[3]");     
                    }
                }
            } else {
                p("close DB-attroc-null");                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            if (db_mm1 != null)  {
                p("isclosed DB-mm1: " + db_mm1.isClosed());
                if (!db_mm1.isClosed()) {
                    try {
                        db_mm1.rollback();
                        db_mm1.close();
                    } catch (IllegalAccessError e) {
                        p("*** EXCEPTION: IllegalAccessError[5]");                        
                    }
                } 
            } else {
                p("close DB-mm1-null");                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            if (db_mm2 != null) {
                p("isclosed DB-mm2: " + db_mm2.isClosed());
                if (!db_mm2.isClosed()) {
                    try {
                        db_mm2.rollback();
                        db_mm2.close();
                    } catch (IllegalAccessError e) {
                        p("*** EXCEPTION: IllegalAccessError[6]");    
                    }
                }
            } else {
                p("close DB-mm2-null");                                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {   
            if (db_cp != null) {
                p("isclosed DB-cp: " + db_cp.isClosed());
                if (!db_cp.isClosed()) {
                    try {
                        db_cp.rollback();
                        db_cp.close();                
                    } catch (IllegalAccessError e) {
                        p("*** EXCEPTION: IllegalAccessError[7]");    
                    }
                }
            } else {
                p("close DB-cp-null");                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {  
            if (db_attr != null) {
                p("isclosed DB-attr: " + db_attr.isClosed());
                if (!db_attr.isClosed()) {
                    try {
                        db_attr.rollback();
                        db_attr.close();                
                    } catch (IllegalAccessError e) {
                        p("*** EXCEPTION: IllegalAccessError[8]");   
                    }
                }
            } else {
                p("close DB-attr-null");                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {  
            if (tx_mm1 != null) {
                p("close tx_mm1");
                tx_mm1.close();
                tx_mm1 = null;
            } else {
                p("close tx_mm1-null");                
            }
        } catch (IllegalAccessError e) {
            p("*** EXCEPTION: IllegalAccessError[9]");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {  
            if (tx_mm2 != null) {
                p("close tx_mm2");
                tx_mm2.close();
                tx_mm2 = null;
            } else {
                p("close tx_mm-null");                
            }
        } catch (IllegalAccessError e) {
            p("*** EXCEPTION: IllegalAccessError[10]");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {  
            if (tx_attr != null) {
                p("close tx_attr");
                tx_attr.close();
                tx_attr = null;
            } else {
                p("close tx_attr-null");                
            }
        } catch (IllegalAccessError e) {
            p("*** EXCEPTION: IllegalAccessError[11]");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {  
            if (tx_cp != null) {
                p("close tx_cp");
                tx_cp.close();
                tx_cp = null;
            } else {
                p("close tx_cp-null");                
            }
        } catch (IllegalAccessError e) {
            p("*** EXCEPTION: IllegalAccessError[12]");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        bClosingDB = false;
        return 0;
    }
    
    public String getCurrentMapDBVersion() {
        File dir = new File(appendage + "./dist/lib");
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept (File dir, String name) {
                return name.startsWith("mapdb");
            }
        };
        String [] children = dir.list(filter);
        if (children == null) {
            pw("WARNING : could not find lib dir");
            return "";
        } else {
            String version = "";
            for (int i = 0; i<children.length;i++) {
                String filename = children[i];
                p(filename);
                version = filename.substring(6, filename.indexOf(".jar"));
                p(version);
            }
            return version;
        }
        
    }
    
    public boolean isBackwardCompatible(String sDBCurrent, String sDBVersion) {
        
        //0.9.8 is not backward compatible with 0.9.9        
        //0.9.9 is not backward compatible with 0.9.10        
        //0.9.10 is not backward compatible with 1.0.3      
        if (sDBVersion.equals("0.9.8") || sDBVersion.equals("0.9.9") || sDBVersion.equals("0.9.10")) {
            return false;
        } else {
            return true;            
        }
        
    }
    
    public int loadIndexMapDB(boolean bReadOnly, boolean bCreateifNotExist) {
        try {
            p("LoadIndexMapDB()");  
            
            String sFile = appendageRW + "../rtserver/testdb";
            
            String sDB = appendageRW + "../rtserver/dbname.txt";
            File fbd = new File(sDB);

            p("[LocalFuncs.loadIndexMapDB()] file '" + fbd.getAbsoluteFile() + "' exists: " + fbd.exists());
            
            String sDBName = readDoc(appendageRW + "../rtserver/dbname.txt");
            if (sDBName.length() > 0) {
                sFile = sDBName.trim();
            }
            p("[LocalFuncs.loadIndexMapDB()] DBName: '" + sFile + "'");
            
            String sDBVersion = readDoc(appendageRW + "../rtserver/dbver.txt");
            if (sDBVersion.length() > 0) {
                sDBVersion = sDBVersion.trim();
            } else {
                //assume v0.9.8 if file doesn't exist (beta user legacy)
                sDBVersion = "0.9.8";
            }
            p("DBVersion: '" + sDBVersion + "'");
            
            String sDBCurrent = getCurrentMapDBVersion();
            p("DBCurrent: '" + sDBCurrent + "'");
            if (sDBCurrent.length() == 0) {
                pw("WARNING: Could not determine current MapDB version.");
                sDBCurrent = sDBVersion;
            }
                        
            String sAppend = "./";
            if (appendage.length() > 0) sAppend = "";
            File fh = new File(sAppend + appendageRW + "../rtserver/" + sFile + "_mm1");
            
            p("[LocalFuncs.LoadIndexMapDB] MapDB File '" + fh.getAbsolutePath() + "' exists: " + fh.exists());
            
            if (fh.exists()) {
                File parent = fh.getParentFile();
                
                if(!parent.exists() || !parent.isDirectory())
                    throw new IOException("Parent folder does not exist: "+fh);
                if(!parent.canRead())
                    throw new IOException("Parent folder is not readable: "+fh);
                if(!parent.canWrite())
                    throw new IOException("Parent folder is not writeable: "+fh);                
            }
            
            p("[LocalFuncs] File  : '" + fh.getAbsolutePath() + "' exists: " + fh.exists());
            p("[LocalFuncs] Compat: '" + isBackwardCompatible(sDBCurrent, sDBVersion));

            boolean bCreate = false;           
            if (fh.exists() && isBackwardCompatible(sDBCurrent, sDBVersion)) {
                p("Index : DB Exists...");
                //db = DBMaker.newMemoryDB().make();
                try {   
                    boolean bOK = false;
                    p("Exist[1]");
                    try {
                        sAppend = "./";
                        if (appendage.length() > 0) sAppend = "";
                        
                        if (bReadOnly) {
                            p("MAP DB READ ONLY");
                            db_r = DBMaker.newFileDB(new File(sAppend + appendageRW + sFile)).closeOnJvmShutdown().readOnly().make();                            
                        } else {
                            p("MAP DB READ/WRITE");
                            //.asyncWriteEnable()
                            tx_mm1 = DBMaker.newFileDB(new File(sAppend + appendageRW + sFile + "_mm1")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                            
                            tx_mm2 = DBMaker.newFileDB(new File(sAppend + appendageRW + sFile + "_mm2")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                            
                            tx_cp = DBMaker.newFileDB(new File(sAppend + appendageRW + sFile + "_cp")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                            
                            tx_attr = DBMaker.newFileDB(new File(sAppend + appendageRW + sFile + "_attr")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                            
                            //db = tx.makeTx();
                        }
                        bOK = true;
                    } catch (InternalError e) {
                        p("INTERNAL ERROR during DBMaker.newFileDB.");                       
                        e.printStackTrace();
                        bOK = false;
                    } catch (Throwable e) {
                        p("Other error");
                        e.printStackTrace();
                        bOK = false;
                    }
                    p("Exist[2]");
                    if (bOK) {
                        p("MapDB load OK.");                    
                        //multiMap = db.getTreeSet("autocomplete");   
                        //multiMap2 = db.getTreeSet("md5");
                        //occurences_copies = db.getTreeMap("numberofcopies");
                        //occurences_attr = db.getTreeMap("attributes");
                        //occurences_attr = db.getTreeSet("attributes");
                    } else {
                        p("MapDB db OPEN ERROR. Forcing Create."); 
                        bCreate = true;
                        p("Attempting to close DB{1}.");
                        try {                                       
                            //db.close();                            
                        } catch (Exception e3) {
                            e3.printStackTrace();
                        }            
                    }
                } catch (Exception e) {
                    bCreate = true;
                    e.printStackTrace();
                    p("WARNING: There was an error while loading MAPDB. Setting create=true");
                    try {
                        p("Attempting to close DB{2}.");
                        db_mm1 = tx_mm1.makeTx();
                        db_mm1.close();
                        db_mm2 = tx_mm2.makeTx();
                        db_mm2.close();
                        db_cp = tx_cp.makeTx();
                        db_cp.close();
                        db_attr = tx_attr.makeTx();
                        db_attr.close();
                    } catch (Exception e2) {
                        e2.printStackTrace();
                        p("Exception during CLOSE");
                    }
                }
            } else {
                if (!fh.exists()) {
                    p("db does not exist. creating...");
                } else {
                    //file exists, but not backward compatible
                    if (!isBackwardCompatible(sDBCurrent, sDBVersion)) {
                        p("db not backward compatible. re-creating...");                    
                    }                    
                }
                bCreate = true;                    
            }
            
            p("Create = " + bCreate);

            if (bCreate && bCreateifNotExist) {
                try {
                    p("waiting 5s");
                    Thread.sleep(5000);
                                        
                    //int i = DeleteMapDB(sFile + "_attr");
                    //i += DeleteMapDB(sFile + "_cp");
                    //i += DeleteMapDB(sFile + "_mm1");
                    //i += DeleteMapDB(sFile + "_mm2");                                        

                    //p("#failed deletes: " + i);
                    Boolean bCreateNewDB = true;
                    
                    if (bCreateNewDB) {
                        Date ts_start = Calendar.getInstance().getTime();
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
                        String sDate = sdf.format(ts_start);

                        sDBName = "testdb" + sDate;

                        PrintStream ps = new PrintStream(new BufferedOutputStream(
                                new FileOutputStream(appendageRW + "../rtserver/dbname.txt", false)));
                        ps.println(sDBName);
                        ps.close();   

                        //store new version
                        PrintStream ps2 = new PrintStream(new BufferedOutputStream(
                        new FileOutputStream(appendageRW + "../rtserver/dbver.txt", false)));
                        ps2.println(sDBCurrent);
                        ps2.close();   

                        sFile = appendageRW + "../rtserver/" + sDBName;
                    }  
                    p("Index: Creating new DB at: " + sFile);
                    
                    sAppend = "./";
                    if (appendage.length() > 0) sAppend = "";
                        
                    //db = DBMaker.newMemoryDB().make();
                    if (bReadOnly) {
                        db_r = DBMaker.newFileDB(new File(sAppend + sFile)).closeOnJvmShutdown().readOnly().make();                        
                    } else {     
                        //asyncWriteEnable
                        tx_mm1 = DBMaker.newFileDB(new File(sAppend + sFile + "_mm1")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                        
                        db_mm1 = tx_mm1.makeTx();
                        tx_mm2 = DBMaker.newFileDB(new File(sAppend + sFile + "_mm2")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                        
                        db_mm2 = tx_mm2.makeTx();
                        tx_cp = DBMaker.newFileDB(new File(sAppend + sFile + "_cp")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                        
                        db_cp = tx_cp.makeTx();
                        tx_attr = DBMaker.newFileDB(new File(sAppend + sFile + "_attr")).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                        
                        db_attr = tx_attr.makeTx();
                    }
                    
                    multiMap = db_mm1.createTreeSet("autocomplete").serializer(BTreeKeySerializer.TUPLE2).nodeSize(64).make();
                    multiMap2 = db_mm2.createTreeSet("md5").serializer(BTreeKeySerializer.TUPLE2).nodeSize(64).make();
                    
                    occurences_copies = db_cp.createTreeMap("numberofcopies").valuesOutsideNodesEnable().nodeSize(64).make();
                    occurences_attr = db_attr.createTreeMap("attributes").valuesOutsideNodesEnable().nodeSize(64).make();                    
                                       
                    //multiMap = db.createTreeSet("autocomplete",32,false, BTreeKeySerializer.TUPLE2, null);
                    //multiMap2 = db.createTreeSet("md5",16,false, BTreeKeySerializer.TUPLE2, null);
                    //occurences_copies = db.createTreeSet("numberofcopies",32,false, BTreeKeySerializer.TUPLE2, null);
                    //occurences_copies = db.getTreeMap("numberofcopies");
                    //occurences_attr = db.getTreeMap("attributes");                    
                    //occurences_copies.put("pepe", "pepe");                               
                    db_mm1.commit();
                    db_mm2.commit();
                    db_cp.commit();
                    db_attr.commit();
                    
                    //occurences_attr = db.createTreeSet("attributes",32,false, BTreeKeySerializer.TUPLE2, null);
                    log("Index: Indexing...", 0);
                    Stopwatch timer = new Stopwatch();
                    timer.start();
                    bLoadingCopies = true;
                    //do_index_mapdb();
                    bLoadingCopies = false;
                    timer.stop();
                    p("loadIndexMapDB() took " + timer.getElapsedTime() + "ms."); 
                    return 2;
                } catch (Exception e) {
                    e.printStackTrace();
                    return -1;
                }
            } else {
                p("here!!!");
                Thread.sleep(1000);
                return 1;
            }           
        } catch (InternalError e) {
            e.printStackTrace();
            p("INTERNAL ERROR.");
            return -1;
        } catch (Exception e) {
            p("Exception during OPEN");
            e.printStackTrace();
            return -1;
        }
    }
    
    int DeleteMapDB(String _filename) {
        try {
            int i = 0;
            File fh;
            fh = new File(_filename);
            if (fh.exists()) {
                if (fh.delete())  {
                    p("Index: Deleted old DB: " + _filename);
                } else {
                    p("WARNING: Failed delete old DB: " + _filename);                    
                    i++;
                }
            } else {
                p("File not found: " + fh.getCanonicalPath());
            }

            fh = new File(_filename + ".p");
            if (fh.exists()) {
                if (fh.delete())  {
                    p("Index: Deleted old DB: " + _filename);
                } else {
                    p("WARNING: Failed delete old DB: " + _filename);                    
                    i++;
                }
            } else {
                p("File not found: " + fh.getCanonicalPath());
            }

            fh = new File(_filename + ".t");
            if (fh.exists()) {
                p("Index: Deleting old DB(t):" + _filename);
                if (fh.delete()) {
                    p("Index: Deleted old DB: " + _filename);
                } else {
                    p("WARNING: Failed delete old DB: " + _filename);                    
                    i++;
                }
            } else {
                p("File not found: " + fh.getCanonicalPath());
            }
           
            return i;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    
    void loadIndex() {
        try {
            Stopwatch Timer = new Stopwatch();
            Timer.start();
                    
            loadRecords();
            p("LoadIndex!!!");

            Iterator It = mFilesDatabase.keySet().iterator();
            int i = 0;
            while (It.hasNext()) {
                i++;

                if (i % 100 == 0) {
                    p("Processed: " + i);   
                    //Thread.sleep(3000);
                }

                String sFilePath = (String)It.next();                
                String sFilePath2 = URLDecoder.decode(sFilePath, UTF8);
                String sFilePath3 = sFilePath2.replace("\\", "/");
                String valfilename = sFilePath3.substring(sFilePath3.lastIndexOf("/")+1, sFilePath3.length());
                Object entry = mFilesDatabase.get(sFilePath);
                FileDatabaseEntry fde = (FileDatabaseEntry)entry;
                
                occurences_files.put(fde.mMD5, valfilename);
                                
                //p("indexing file: " + valfilename);
                ArrayList<String> l1 = null;
                l1 = (ArrayList<String>)occurences_index.get(valfilename);
                if (l1 == null) {
                    //p("doesn't exist yet");
                    l1 = new ArrayList<String>();                    
                    l1.add(fde.mMD5);
                    occurences_index.put(valfilename.toLowerCase(), l1);
                } else {
                    //p("already contains: " + l1.size());
                    l1.add(fde.mMD5);                    
                    //occurences_index.put(sub_string, l1);
                }   

//                String sub_string = "";
//                String delimiters = " !@#$%^&*()-=_+[]\\{}|;':\",./<>?";                
//                StringTokenizer st = new StringTokenizer(sFilePath3, delimiters, true);
//                while (st.hasMoreTokens()) {
//                        sub_string = st.nextToken();
//                        if (sub_string.length() > 5) {
//                            //p("indexing: " + sub_string);
//                            ArrayList<String> l1 = null;
//                            l1 = (ArrayList<String>)occurences_index.get(sub_string);
//                            if (l1 == null) {
//                                //p("doesn't exist yet");
//                                l1 = new ArrayList<String>();                    
//                                l1.add(fde.mMD5);
//                                occurences_index.put(sub_string, l1);
//                            } else {
//                                //p("already contains: " + l1.size());
//                                l1.add(fde.mMD5);                    
//                                //occurences_index.put(sub_string, l1);
//                            }                               
//                        } 
//                }                                                                                       
            }   
            
//            It = occurences_index.keySet().iterator();
//            i = 0;
//            while (It.hasNext()) {
//                String key = (String)It.next();
//                ArrayList<String> l1 = (ArrayList<String>)occurences_index.get(key);                
//                p(key + ":" + l1.size());
//                Iterator It2 = l1.iterator();
//                while (It2.hasNext()) {
//                    String val = (String)It2.next();
//                    p(val);
//                }                
//            }       
            Timer.stop();
            p("LoadIndex - COMPLETE!: Processed : " + i + " took " + Timer.getElapsedTime() + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    
    }
    
    void loadRecords() {
        HashMap<String, FileDatabaseEntry> mFilesDatabase1 = new HashMap<String, FileDatabaseEntry>();
        HashMap<String, FileDatabaseEntry> mFilesDatabase2 = new HashMap<String, FileDatabaseEntry>();
        HashMap<String, FileDatabaseEntry> mFilesDatabase3 = new HashMap<String, FileDatabaseEntry>();
        HashMap<String, FileDatabaseEntry> mFilesDatabase4 = new HashMap<String, FileDatabaseEntry>();
        
        try {
            String mStorage = "../scrubber/data/records.db";
            File fh = new File(mStorage);
            if (fh.exists()) {
                mFilesDatabase1 = loadRecords(mStorage);
                p("size1 = " + mFilesDatabase1.size());                
                mFilesDatabase.putAll(mFilesDatabase1);                    
            } else {
                p("not found:" + mStorage);
            }    
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            String mStorage = "../scrubber/data/records2.db";
            File fh = new File(mStorage);
            if (fh.exists()) {
                mFilesDatabase2 = loadRecords(mStorage);
                p("size2 = " + mFilesDatabase2.size());                    
                mFilesDatabase.putAll(mFilesDatabase2);
            } else {
                p("not found:" + mStorage);
            }    
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            String mStorage = "../scrubber/data/records3.db";
            File fh = new File(mStorage);
            if (fh.exists()) {
                mFilesDatabase3 = loadRecords(mStorage);
                p("size3 = " + mFilesDatabase3.size());                    
                mFilesDatabase.putAll(mFilesDatabase3);
            } else {
                p("not found:" + mStorage);
            }    
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            String mStorage = "../scrubber/data/records4.db";
            File fh = new File(mStorage);
            if (fh.exists()) {
                mFilesDatabase4 = loadRecords(mStorage);
                p("size4 = " + mFilesDatabase4.size());                    
                mFilesDatabase.putAll(mFilesDatabase4);
            } else {
                p("not found:" + mStorage);
            }            
        } catch (Exception e) {
            e.printStackTrace();
        }
                            
        p("total size = " + mFilesDatabase.size());
        
    }
    
    public void loadNumberOfCopies_File(String _ServerIP, boolean _forceload, boolean _interactive) {
            try {          
                DB db_mm1_nc = tx_mm1.makeTx();
                NavigableSet<Fun.Tuple2<String,String>> mm1 = db_mm1.getTreeSet("autocomplete"); 
                DB db_mm2_nc = tx_mm2.makeTx();
                NavigableSet<Fun.Tuple2<String,String>> mm2 = db_mm2.getTreeSet("md5"); 
                DB db_cp_nc = tx_cp.makeTx();
                ConcurrentNavigableMap<String,String> occurences_copies_w = db_cp.getTreeMap("numberofcopies");
                DB db_attr_nc = tx_attr.makeTx();
                ConcurrentNavigableMap<String,String> occurences_attr_w = db_attr.getTreeMap("attributes");

                                               
                bNodesLoaded = false;
                previous_query = "xyzzy";
                bLoadingCopies = true;

                if (!bNodesLoaded) {
                                p("Loading Blacklist");
                                loadBlacklistMap();
                                loadBlacklistContainsMap();
                                p("Loading Nodes");
                                int r = loadNodes("nodes", "NodeInfo");                            
                                bNodesLoaded = true;    
                } else {
                    //p("Skipping loadNodes(). Already loaded.");
                }
                
                String filename = DB_PATH + File.separator + "Standard1" + File.separator + ".all";
                FileInputStream bf2 = new FileInputStream(filename);
                Scanner scanner2 = new Scanner(bf2);
                
                int i = 0;
                int j = 0;
                int k = 0;
                while (scanner2.hasNextLine()) {
                    
                    if (i % 50 == 0 && _interactive) {
                        p("lncf() ZZZzzz...");
                        Thread.sleep(500);
                    }
                    if (i > 0 && i % 500 == 0) {
                        log("Processed File Records: " + i + " - added: " + j + " attr:" + k, 0);   
                        Thread.sleep(50);
                        //db2.commit();
                    }
                    i++;
                    String sCurrentLine = scanner2.nextLine();
                    
                    String delimiters = ",";
                    StringTokenizer st = new StringTokenizer(sCurrentLine, delimiters, true);
                    String _date = st.nextToken();
                    st.nextToken();
                    String _key = st.nextToken();                         
                    st.nextToken();
                    String _filename = st.nextToken();                         
                    
                    String sCopyInfo2 = null;
//                    for(String s: Bind.findSecondaryKeys(occurences_copies, _key)){                        
//                        sCopyInfo = s;
//                    }
                    
//                    synchronized (occurences_copies_w) {
//                    sCopyInfo = occurences_copies_w.get(_key);                        
//                    }
                    
                    if (sCopyInfo2 == null || _forceload) {      
                        //p("missing getNumberofCopies(): " + _key); 
                        String sLocalIP = _ServerIP;
                        String sCopyInfo = getNumberofCopies("paths", _key, sLocalIP, _ServerIP, false);
                        synchronized(occurences_copies_w) {
                            p("storing numcopies " + _key + " CopyInfo = " + sCopyInfo);
                            //occurences_copies.add(Fun.t2(_key, sCopyInfo));  
                            occurences_copies_w.put(_key, sCopyInfo); 
                        }
                        j++;
                    }                                                

                    String sAttr = null;
//                    for(String s: Bind.findSecondaryKeys(occurences_attr, _key)){                        
//                        sAttr = s;
//                    }
                    
//                    synchronized(occurences_attr_w) {
//                        sAttr = occurences_attr_w.get(_key);
//                    }
                    
                    if (sAttr == null || _forceload) {  
                        sAttr= ",";
                        if (Cass7Funcs.is_music(_filename)) {
                            sAttr = get_row_attributes(keyspace, "Standard1", _key, "title", "artist");                            
                        }
                        if (Cass7Funcs.is_photo(_filename)) {
                            sAttr = get_row_attributes(keyspace, "Standard1", _key, "img_height", "img_width");                            
                        }
                        //DateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
                        //String sDate = sdf.format(fde.mDateModified);

                        String sStore =  _date + "," + sAttr;
                        //p("Storing attributes: " + _key + " = '" + sStore + "'");
                        synchronized(occurences_attr_w) {
                            occurences_attr_w.put(_key, sStore);
                            //occurences_attr.add(Fun.t2(_key, sStore));
                        }
                        k++;
                    }                    
                } //while
                log("LoadNumberCopiesF - Total Processed Records: " + i, 0);
                log("LoadNumberOfCopiesF - New Entries (copies) Loaded into index: " + j, 0);
                log("LoadNumberOfCopiesF - New Entries (attributes) Loaded into index: " + k, 0);
                bLoadingCopies = false;
                db_mm1_nc.commit();
                db_mm2_nc.commit();
                db_cp_nc.commit();
                db_attr_nc.commit();
                
                db_mm1_nc.close();
                db_mm2_nc.close();
                db_cp_nc.close();
                db_attr_nc.close();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
    }
            
    boolean isPathInString(String _path, String _string) {
        
        try {
            Scanner scanner = new Scanner(_string);
            boolean bFound = false;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().toLowerCase();
                //p("comparing with line: '" + line + "'");                
                if (line.length() > 0 && _path.toLowerCase().contains(line)) {
                    bFound = true;
                    break;
                }
            }
            return bFound;
        } catch (Exception e)  {
            e.printStackTrace();
            return false;
        }            
    }
    
    public int loadNumberOfCopies(String _ServerIP, boolean _forceload, boolean _interactive, String _b1, String _b2) {
 
        try {                
            
                String b1r = _b1.replace("\\", "/");
                String b2r = _b2.replace("\\", "/");
                //p("********* b1r = " + b1r);              
                //p("********* b2r = " + b2r);
                //p("********* b1r = " + b1r.length());              
                //p("********* b2r = " + b2r.length());

                                                                            
                //DB db_mm1_nc = tx_mm1_nc.makeTx();
                //NavigableSet<Fun.Tuple2<String,String>> mm1 = db_mm1_nc.getTreeSet("autocomplete"); 
                //DB db_mm2_nc = tx_mm2_nc.makeTx();
                //NavigableSet<Fun.Tuple2<String,String>> mm2 = db_mm2_nc.getTreeSet("md5"); 
                
                if (tx_cp == null) {
                    open_mapdb();                                    
                }
                DB db_cp_nc = tx_cp.makeTx();
                ConcurrentNavigableMap<String,String> occurences_copies_w = db_cp_nc.getTreeMap("numberofcopies");
                DB db_attr_nc = tx_attr.makeTx();
                ConcurrentNavigableMap<String,String> occurences_attr_w = db_attr_nc.getTreeMap("attributes");
                            
                bNodesLoaded = false;
                previous_query = "xyzzy";
                bLoadingCopies = true;               
                bNewQuery = true;
                                
                //if (!bRecordsLoaded) {
                //    loadRecords();
                //    bRecordsLoaded = true;                
                //}               
                
                loadRecords();
                
                //p("occurences_copies.size() = " + occurences_copies_w.size());
                p("mFilesDatabase.size() = " + mFilesDatabase.size());                                               

                if (!bNodesLoaded) {
                                p("Loading Blacklist");
                                loadBlacklistMap();
                                loadBlacklistContainsMap();
                                p("Loading Nodes");
                                int r = loadNodes("nodes", "NodeInfo");                            
                                bNodesLoaded = true;    
                } else {
                    //p("Skipping loadNodes(). Already loaded.");
                }

                Iterator It = mFilesDatabase.keySet().iterator();
                int i = 0;
                int j = 0;
                int k = 0;
                int errnum_cp_put_err = 0;
                int errnum_cp_put_ok = 0;
                int errnum_attr_put_err = 0;
                int errnum_attr_put_ok = 0;
                int errnum_attr_get_err = 0;
                int errnum_attr_get_ok = 0;
                while (It.hasNext()) {
                    
                    if (i % 500 == 0 && _interactive) {
                        p("lnc() ZZZzzz...");
                        Thread.sleep(500);
                    }     

                    if (i > 0 && i % 500 == 0) {
                        log("Processed record.db: " + i + " of " + mFilesDatabase.size() + " added: " + j + " attr:" + k, 0);   
                        //Thread.sleep(50);
                        //db2.commit();
                    }                    
                    p("Processing Entry # " + i);
                    i++;
                    String sFilePath = (String)It.next();                
                    String sFilePath2 = URLDecoder.decode(sFilePath, UTF8);
                    String sFilePath3 = sFilePath2.replace("\\", "/");
                    String valfilename = sFilePath3.substring(sFilePath3.lastIndexOf("/"), sFilePath3.length());
                    Object entry = mFilesDatabase.get(sFilePath);
                    FileDatabaseEntry fde = (FileDatabaseEntry)entry;                

                    boolean bRecalc = false;                    
                    p(sFilePath3 + " " + isBlacklisted(sFilePath3));
                    
                    String _path = sFilePath3.substring(0,sFilePath3.lastIndexOf("/")+1);
                    p("_path: " + _path);                                                            
                    p("isPathB2 = " + isPathInString(_path, b2r));
                    p("isPathB1 = " + isPathInString(_path, b1r));
                    
                    if(isPathInString(_path, b2r) && !isPathInString(_path, b1r)) {
                        p("-- RECALC[1]: " + sFilePath3);
                        bRecalc = true;
                    }
                    if(!isPathInString(_path, b2r) && isPathInString(_path, b1r)) {
                        p("-- RECALC[2]: " + sFilePath3);
                        bRecalc = true;
                    }

                    if (bRecalc || _forceload) {    
                        
//                        for(String s: Bind.findSecondaryKeys(occurences_copies, fde.mMD5)){                        
//                            sCopyInfo = s;
//                        }
                                               
                        String sCopyInfo = null;
//                        synchronized (occurences_copies_w) {
//                            if (occurences_copies_w != null) {
//                                p("get" + fde.mMD5);
//                                try {
//                                    sCopyInfo = occurences_copies_w.get(fde.mMD5);
//                                } catch (NullPointerException n) {
//                                    p("null!");
//                                }                             
//                            } else {
//                                p("null!");
//                            }
//                        }
                                                
                        if (sCopyInfo == null || _forceload) {      
                            //p("missing getNumberofCopies(): " + fde.mMD5); 
                            String sLocalIP = _ServerIP;
                            sCopyInfo = getNumberofCopies("paths", fde.mMD5, sLocalIP, _ServerIP, true);
                            synchronized(occurences_copies_w) {
                                p("storing numcopies " + fde.mMD5 + " CopyInfo = " + sCopyInfo);
                                //occurences_copies.add(Fun.t2(fde.mMD5, sCopyInfo));  
                                try {
                                    if (tx_cp == null || db_cp_nc.isClosed()) {
                                        p("***reopening DB - tx_cp");                                        
                                        open_mapdb();                                    
                                        db_cp_nc = tx_cp.makeTx();
                                        occurences_copies_w = db_cp_nc.getTreeMap("numberofcopies");
                                    }
                                    occurences_copies_w.put(fde.mMD5, sCopyInfo);
                                } catch (IllegalAccessError e) {
                                    log("WARNING: There was an IllegalAccessError doing a PUT in #copies. Trying again", 0);                                
                                    open_mapdb();                                    
                                    db_cp_nc = tx_attr.makeTx();  
                                    occurences_copies_w = db_cp_nc.getTreeMap("numberofcopies");
                                    occurences_copies_w.put(fde.mMD5, sCopyInfo);
                                    log("Success write " + fde.mMD5 + " " + sCopyInfo, 0);
                                    errnum_cp_put_ok++;
                                } catch (Exception e) {
                                    log("WARNING: There was an exception doing a put in copies #copies " + fde.mMD5 + " " + sCopyInfo, 0);                                                                       
                                    errnum_cp_put_err++;
                                }                             
                            }
                            j++;
                        }                                                
                        
                        String sAttr = null;
//                        for(String s: Bind.findSecondaryKeys(occurences_attr, fde.mMD5)){                        
//                            sAttr = s;
//                        }
                        
                        synchronized(occurences_attr_w) {
                            try {
                                if (tx_attr == null || db_attr_nc.isClosed()) {
                                    p("***reopening DB - tx_attr[1]");                                        
                                    open_mapdb();                                    
                                    db_attr_nc = tx_attr.makeTx();  
                                    occurences_attr_w = db_attr_nc.getTreeMap("attributes");
                                }
                                sAttr = occurences_attr_w.get(fde.mMD5);                            
                            } catch (IllegalAccessError e) {
                                log("WARNING: There was an IllegalAccessError doing a GET in attr #copies. Trying again", 0);                                
                                open_mapdb();                                    
                                db_attr_nc = tx_attr.makeTx();  
                                occurences_attr_w = db_attr_nc.getTreeMap("attributes");
                                sAttr = occurences_attr_w.get(fde.mMD5);                            
                                if (sAttr != null) log("success read = " + fde.mMD5 + " " + sAttr, 0);
                                errnum_attr_get_ok++;;
                            } catch (Exception e) {
                                log("WARNING: There was an exception doing a GET in attr #copies", 0);
                                errnum_attr_get_err++;
                            }
                        }
                        
                        if (sAttr == null || _forceload) {  
                            sAttr= ",";
                            if (Cass7Funcs.is_music(valfilename)) {
                                sAttr = get_row_attributes(keyspace, "Standard1", fde.mMD5, "title", "artist");                            
                            }
                            if (Cass7Funcs.is_photo(valfilename)) {
                                sAttr = get_row_attributes(keyspace, "Standard1", fde.mMD5, "img_height", "img_width");                            
                            }
                            DateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
                            String sDate = sdf.format(fde.mDateModified);

                            String sStore =  sDate + "," + sAttr;
                            synchronized(occurences_attr_w) {
                                //p("Storing attributes: " + fde.mMD5 + " = '" + sStore + "'");
                                try {
                                    if (tx_attr == null || db_attr_nc.isClosed()) {
                                        p("***reopening DB - tx_attr[2]");                                        
                                        open_mapdb();                                    
                                        db_attr_nc = tx_attr.makeTx();  
                                        occurences_attr_w = db_attr_nc.getTreeMap("attributes");
                                    }
                                    occurences_attr_w.put(fde.mMD5, sStore);
                                    //occurences_attr.add(Fun.t2(fde.mMD5, sStore)); 
                                } catch (IllegalAccessError e) {
                                    log("WARNING: There was an IllegalAccessError doing a PUT in attr #copies. Trying again", 0);                                
                                    open_mapdb();                                    
                                    db_attr_nc = tx_attr.makeTx();  
                                    occurences_attr_w = db_attr_nc.getTreeMap("attributes");
                                    occurences_attr_w.put(fde.mMD5, sStore);
                                    log("success write " + fde.mMD5 + " " + sStore, 0);
                                    errnum_attr_put_ok++;
                                } catch (Exception e) {
                                    log("WARNING: Exception doing a PUT in attr #copies: " + fde.mMD5 + " " + sStore, 0);                                                                                                           
                                    errnum_attr_put_err++;
                                }                                                               
                            }
                            k++;
                        }
                    } else {
                        p(sFilePath3 + " does not need to be recalculated.");
                    }
                }
                log("LoadNumberCopies - Total Processed Records: " + i, 0);
                log("LoadNumberOfCopies - New Entries (copies) Loaded into index: " + j, 0);
                log("LoadNumberOfCopies - New Entries (attributes) Loaded into index: " + k, 0);
                log("LoadNumberOfCopies - cp_put_ok: " + errnum_cp_put_ok, 0);
                log("LoadNumberOfCopies - attr_put_ok: " + errnum_attr_put_ok, 0);
                log("LoadNumberOfCopies - attr_get_ok: " + errnum_attr_get_ok, 0);

                log("LoadNumberOfCopies - cp_put_err: " + errnum_cp_put_err, 0);
                log("LoadNumberOfCopies - attr_put_err: " + errnum_attr_put_err, 0);
                log("LoadNumberOfCopies - attr_get_err: " + errnum_attr_get_err, 0);
                bLoadingCopies = false;
                
                boolean bError = false;
                boolean bCont = true;
                int nTries = 0;
                while (bCont && nTries <= 5) {
                    try {
                      db_cp_nc.commit();
                      bCont = false;  
                    } catch (TxRollbackException e) {
                        log("WARNING: TxRollbackException in commit cp_nc!!!!", 0);
                        Thread.sleep(500);
                        nTries++;                        
                    } catch (NullPointerException e) {
                        log("WARNING: NullPointer Exception in commit cp_nc!!!!", 0);
                        Thread.sleep(500);
                        nTries++;
                    } catch (Exception e) {                       
                        log("WARNING: Other Exception in commit cp_nc!!!!", 0);
                        Thread.sleep(500);
                        nTries++;
                    }
                }
                if (nTries == 6) bError = true;
                
                bCont = true;
                nTries = 0;
                while (bCont && nTries <= 5) {
                    try {
                      db_attr_nc.commit();
                      bCont = false;  
                    } catch (TxRollbackException e) {
                      log("WARNING: TxRollbackException in commit cp_attr!!!!", 0);
                      Thread.sleep(500);
                      nTries++;
                    } catch (NullPointerException e) {
                      log("WARNING: NullPointer in commit cp_attr!!!!",0);
                      Thread.sleep(500);
                      nTries++;
                    } catch (Exception e) {
                      log("WARNING: TxRollbackException in commit cp_attr!!!!", 0);
                      Thread.sleep(500);
                      nTries++;
                    }
                }
                
                if (nTries == 6) bError = true;

                //db_mm1_nc.close();
                //db_mm2_nc.close();
                db_cp_nc.close();
                db_attr_nc.close();
                
                //tx_cp_nc.close();
                //tx_attr_nc.close();
                if (bError) {
                    return -2;
                } else {
                    return 1;                    
                }
                
        } catch (Exception e) {
                p("EXCEPTION!!!!");
                e.printStackTrace();
                try {
                    Thread.sleep(10000);
                } catch (Exception e1) {
                    e.printStackTrace();
                }
                return -1;
        } 
    }
          
    static void printProps() {
        p("************************************");
        p("dbpath=" + DB_PATH);        
        p("idxfolder=" + NUM_FOLDERS);        
        p("flushdelay=" + FLUSH_DELAY);        
        p("commit_timer=" + COMMIT_TIMER);        
        p("commit_puts=" + COMMIT_PUTS); 
        p("loglevel=" + mLogLevel); 
        p("keywords=" + NUM_KEYWORDS);
        p("usetx=" + bUseMapDBTx);
        p("nulltx=" + bNullMapDBTx);
    }
    
    void loadProps() {
        try {
            String sConfig = 
                appendage + 
                ".."+
                File.separator+
                "scrubber"+
                File.separator+
                "config"+
                File.separator+
                "www-processor.properties";
            p("Localfuncs.loadProps() appendage: " + appendage);
            p("Localfuncs.loadProps() looking for config: " + sConfig);
            File f = new File(sConfig);
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
                props.load(is);
                is.close();
            
                String r = props.getProperty("dbpath");
                if (r != null) {
                    DB_PATH = r;
                }   

                r = props.getProperty("idxfolder");
                if (r != null) {
                    NUM_FOLDERS = Integer.parseInt(r);
                }   

                r = props.getProperty("flushdelay");
                if (r != null) {
                    FLUSH_DELAY = Integer.parseInt(r);
                }   
                
                r = props.getProperty("commit_timer");
                if (r != null) {
                    COMMIT_TIMER = Integer.parseInt(r);
                } 

                r = props.getProperty("commit_puts");
                if (r != null) {
                    COMMIT_PUTS = Integer.parseInt(r);
                } 
                
                r = props.getProperty("loglevel");
                if (r != null) {
                    mLogLevel = Integer.parseInt(r);
                } 
                
                r = props.getProperty("keywords");
                if (r != null) {
                    NUM_KEYWORDS = Integer.parseInt(r);
                }

                //bUseMapDBTx
                r = props.getProperty("usetx");
                if (r != null) {
                    bUseMapDBTx = Boolean.parseBoolean(r);
                }
                
                //bUseMapDBTx
                r = props.getProperty("nulltx");
                if (r != null) {
                    bNullMapDBTx = Boolean.parseBoolean(r);
                }
                
                r = props.getProperty("thumbnaildir");
                if (r != null) {
                    THUMBNAIL_OUTPUT_DIR = r;
                }


            } else {
                p("WARNING: config file does not exist: " + sConfig);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }        
    }
    
    public boolean checkBatch(long _batchnum) {
        
        BufferedReader br = null;

        try {
            String sPath = appendage + DB_PATH;
            String _cf = "BatchJobs";
            String sCurrentLine = "";
            String _key = Long.toString(_batchnum);

            String filename = sPath + File.separator + _cf + File.separator + _key;
            File ft = new File(filename);
            //p("filename = " + filename);
            if (ft.exists()) { 
                String ts_end = get_row_attribute("keyspace", "BatchJobs", _key, "ts_end", null);
                if (ts_end.length() > 0) {
                    p("Batch " + _batchnum + " has finished at: " + ts_end);
                    return true;
                } else {                    
                    p("Batch " + _batchnum + " has not finished.");
                    return false;
                }
            } else {
                p("Batch " + _batchnum + " does not exist.");
                return false;                
            }


            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }   
    
    
    /* print to the log file */
    protected static void log(String s, int _loglevel) {
        
        try {
            if (_loglevel <= mLogLevel) {
                Date ts_start = Calendar.getInstance().getTime();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                String sDate = sdf.format(ts_start);

                if (log == null) {
                    String sFilename = appendageRW + LOG_NAME_BACKUP_DEBUG_PATH + sDate + "_rtserver.log";
                    log = new PrintStream(new BufferedOutputStream(
                                new FileOutputStream(sFilename, true)));
                    log("opening debug log file: " + sFilename, 0);            
                }

                sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
                sDate = sdf.format(ts_start);

                synchronized (log) {
                    log.println(sDate + " " + _loglevel + " " + s);
                    log.flush();
                }                            
                pi(_loglevel + " " + s);
            }
        } catch (Exception e) {
            e.printStackTrace();           
        }
    }
    
    boolean is_backup_node(String _nodeID, String _name) {
        String value = get_row_attribute("keyspace", "NodeInfo", _nodeID, _name, null);
        if (value.equals("yes")) {
            return true;
        } else {
            return false;            
        }
    }
    
    
    public static boolean  get_deadnode(String sUUID) {
        Object Got;

        if ((Got = deadnodes_uuid.get(sUUID)) != null) {
            return true;
        } else {
            return false;
        }

    }
    
    public String set_backupnodes(String _keyspace, String _cf, String _key, HashMap _occurences_uuid_files, String _columnname) {
    
        BufferedReader br = null;

        try {
            
            String sPath = appendage + DB_PATH;
            String sres = "";
        
            if (_key.equals("nodes")) {
                //info for all nodes
                String filename = sPath + File.separator + _cf;                
                File ft = new File(filename);
                if (ft.exists()) {
                    //directory exists
                    
                    File[] files = ft.listFiles();
                    for (File fn: files) {    
                        //p("Getting configs for" + fn.getName());
                        if (is_backup_node(fn.getName(), _columnname)) {
                            //p("Node BACKUP: " + fn.getName());
                            _occurences_uuid_files.put(fn.getName(), 0);
                        } else {
                            //p("Skipped Node: " + fn.getName());                            
                        }
                    }
                    
                    return sres;
                }  else {
                    //file not found
                    p("File not found: " + filename);
                    return "ERROR";
                }                
            } else {
                return "@@@WIP@@@";
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }    
        
        
    }
    
    public boolean isUnix(String _uuid) {
        return true;
        
    }
    
    public int backupObject(final String _key, 
             final String superColumnName, 
             long nBatch, 
             HashMap occurences_uuid_files, 
             String sRoot, 
             String sSeqID,
             Boolean bAppend,
             int _REPLICATION_FACTOR,
             String sBatchUUID) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {
        
        BufferedReader br = null;

         try {
                    Thread.sleep(10);
                    HashMap<String, Integer> occurences_uuid = new HashMap<String, Integer>();
                    HashMap<String, Integer> occurences_uuid_sync = new HashMap<String, Integer>();

                    set_backupnodes(keyspace, "NodeInfo","nodes", occurences_uuid, "backup");
                    set_backupnodes(keyspace, "NodeInfo","nodes", occurences_uuid_sync, "sync");
                    set_deadnodes("NodeInfo", "nodes");
                    
                    //occurences_uuid.put("0ce78da1-fe24-4875-9ff0-476a6ef4883d", 0);
                    //occurences_uuid.put("98bf3e62-e23d-4754-af80-6d09a79e8817", 0);
                    
                    String sPath = appendage + DB_PATH;
                    String _cf = "Super2";
                    String _sc = "paths";
                    String sCurrentLine = "";
                    String sRow = "";

                    String filename = sPath + File.separator + _cf + File.separator + _sc + File.separator + _key;
                    File ft = new File(filename);
                    //p("filename = " + filename);
                    if (ft.exists()) {                       
                            FileReader f = new FileReader(filename);            
                            br = new BufferedReader(f);   
                            while (((sCurrentLine = br.readLine()) != null)) {                                
                                try {
                                    //p("Currentline = '" + sCurrentLine  + "'");
                                    String colName = sCurrentLine.substring(0, sCurrentLine.indexOf("/,"));
                                    String colValue = sCurrentLine.substring(sCurrentLine.indexOf("/,")+2, sCurrentLine.length());     

                                    //p("colName = " + colName);
                                    //p("colValue = " + colValue);

                                    //p("\ncolName: " + colName);
                                    int npos =  colName.indexOf(":");
                                    //p("npos: " + npos);
                                    int nlen = colName.length() - npos;
                                    //p("length: " + nlen);
                                    String sUUID = colName.substring(0,npos);
                                    //p(sUUID);

                                    int was = 0;
                                    Object Got;
                                    if ((Got = occurences_uuid.get(sUUID)) != null) {
                                        was = (((Integer) Got).intValue());
                                    }

                                    if (!colValue.equals("DELETED")) {
                                        boolean bNodeDead = get_deadnode(sUUID);
                                        if (!bNodeDead) {
                                            occurences_uuid.put(sUUID, new Integer(was + 1));
                                            String sAdd = "";
                                            String sIP = "";
                                            String sPort = "";
                                            
                                            String sIPPort = (String) map.get(sUUID);                                                                                                    
                                            if (sIPPort != null) {
                                                sIP = sIPPort.substring(0,sIPPort.indexOf(":"));
                                                sPort = sIPPort.substring (sIPPort.indexOf(":")+1, sIPPort.length());
                                                log("sIPPort found in Map: " + sIP + " " + sPort, 2);                                                
                                            } else {
                                                log("sIPPort NOT found. reading from file",2);
                                                //Not found in memory, try read from file. 
                                                int nTries = 0;
                                                while (sIP.length() == 0 && nTries < 5) {
                                                    sIP = get_row_attribute("Keyspace1b","NodeInfo",sUUID, "ipaddress", null);                                                    
                                                    if (sIP.length() == 0) {
                                                        log("sIP blank. Retry #" + nTries, 2);                                                        
                                                        Thread.sleep(1000);
                                                        nTries++;
                                                    } else {
                                                        log("sIP found in file = " + sIP, 2);
                                                    }
                                                }
                                                nTries = 0;
                                                while (sPort.length() == 0 && nTries < 5) {
                                                    sPort = get_row_attribute("Keyspace1b","NodeInfo",sUUID, "port", null);  
                                                    if (sPort.length() == 0) {
                                                        log("sPort blank. Retry #" + nTries, 2);                                                        
                                                        Thread.sleep(1000);
                                                        nTries++;
                                                    } else {
                                                        log("sPort found in file = " + sPort, 2);                                                        
                                                    }
                                                }
                                            }
                                            
                                            //boolean bAvail = isNodeAvailable(sIP, sPort);
                                            //p("ip=" + sIP + " port=" + sPort);

                                            /*if (isUnix(sUUID)) {
                                                sAdd = "/";
                                            }*/

                                            String sPathDec = sAdd + colName.substring(npos + 1, colName.length());
                                            int lastdot = colName.lastIndexOf(".");
                                            String sExt = colName.substring(lastdot, colName.length());
                                            //p(sPathDec);
                                            String sPathEnc = Base64.encodeToString(sPathDec.getBytes(), false);

                                            String sField1 = _key;
                                            String sField2 = sExt;
                                            String sField3 = "http://" + sIP + ":" + sPort + "/" + sPathEnc + sExt;
                                            String sField4 = sUUID;

                                            //store path of original file
                                            String sUUIDOrig = get_row_attribute(keyspace, "Standard1", _key, "uuid_ori", null);
                                            log("Original row: " + sUUIDOrig, 2);
                                            if (sUUIDOrig.equals(sUUID)) {
                                                log("Found original row: " + sUUID, 2);
                                                sRow = sField1 + "," + sField2 + "," + sField3 + "," + sField4;                                                
                                            } else {
                                                log("Not original row: " + sUUID, 2);                                                
                                            }
                                            //p(sRow);    
                                        } else {
                                            log("Skipped - Node is DEAD: " + sUUID + " " + _key + " : " + colName + " " + colValue, 2);
                                        }

                                    } else {
                                        log("Skipped - File Deleted: " + _key + " : " + colName + " " + colValue, 2);
                                    } 
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    log("Skipped - File line ERROR: '" + sCurrentLine + "'", 2);
                                }                                   
                            }
                    } else {
                        p("ERROR - file does not exist: " + filename);
                        return -1;
                    }           

                    
                    log("Counting replicass...", 2);
                    
                    Iterator bit = occurences_uuid.keySet().iterator();
                    int nReplicas = 0;  //number of nodes with at least one copy
                    while (bit.hasNext()) {
                        String sUUID = (String) bit.next();
                        Integer nCopies = (Integer) occurences_uuid.get(sUUID);

                        if (nCopies > 0) {
                            nReplicas++;
                        }
                    }


                    //BACKUP 
                    //check how many nodes already have a copy. 
                    //if #nodes < replication factor, then proceed with backup
                    //else skip the backup for this file.

                    //number of nodes with at least one copy.
                    log("Number of nodes with replicas of this file: " + nReplicas, 2);                        
                    
                    String sUUIDOrig = get_row_attribute(keyspace, "Standard1", _key, "uuid_ori", null);
                    log("UUID Batch: " + sBatchUUID + " UUID Orig: " + sUUIDOrig, 2);                                               
                                                                                       
                    if (nReplicas < _REPLICATION_FACTOR && sUUIDOrig.equals(sBatchUUID)) {                        

                        Integer nCopies = _REPLICATION_FACTOR - nReplicas;                       

                        Iterator It = occurences_uuid.keySet().iterator();
                        while (It.hasNext()) {
                            log("Number of additional copies needed: " + nCopies, 2);

                            String sNamer = (String) It.next();
                            Integer nCount3 = (Integer) occurences_uuid.get(sNamer);
                            //p(sNamer + " - " + nCount3);                                                        
                            if ((nCount3 == 0) && (nCopies > 0)) {
                                //try to do backup in this node, since this node has 0 copies of the file.
                                try {
                                    int was = 0;
                                    Object Got2;
                                    if ((Got2 = occurences_uuid_files.get(sNamer)) != null) {
                                        was = (((Integer) Got2).intValue());
                                    }
                                    occurences_uuid_files.put(sNamer, new Integer(was + 1));

                                    boolean bNodeDead = get_deadnode(sNamer);
                                    if (!bNodeDead) {
                                        log("backup added in Node: " + sNamer, 2);

                                        //write to temp file
                                        String sFileName = sRoot + "backup_" + sNamer + "_" + sSeqID + "_" + nBatch + ".tmp";
                                        log("Storing in file: " + sFileName, 2);
                                        String sRow2 = sRow + ",backup" + "\n";
                                        log("Storing row: '" + sRow2 + "'", 2);
                                        FileWriter fw = new FileWriter(sFileName, bAppend);
                                        BufferedWriter out = new BufferedWriter(fw);
                                        try {
                                            out.write(sRow2);
                                        } catch (Exception e) {
                                            StringWriter sWriter = new StringWriter();
                                            e.printStackTrace(new PrintWriter(sWriter));
                                            log("WARNING: Exception writing to TMP file: '" + sFileName + "' row: '" + sRow2 + "'", 0);
                                            log(sWriter.getBuffer().toString(), 0);
                                        }
                                        out.close();   
                                        log("Stored row OK.", 2);
                                        //decrease #copies counter since we succesffully added a backup
                                        nCopies--;
                                    } else {
                                        //skip this node since it's declared as dead.
                                        log("Skipping backup for dead node: " + sNamer, 2);
                                    }

                                } catch (Exception ex) {
                                    log("ERROR: writing register", 0);
                                }
                            } else {
                                if (nCount3 == 0) {
                                    log("backup skipped for Node: " + sNamer, 2);                                    
                                }      
                                if (!sUUIDOrig.equals(sBatchUUID)) {
                                    log("backup skipped for Node: " + sNamer, 2);                                                                        
                                }
                            }
                        }

                    } else {
                        if (nReplicas >= _REPLICATION_FACTOR) {
                            log("Number of nodes with replicas exceeds or equals Replication Factor. Skipping backup for this file." + _REPLICATION_FACTOR, 2);                                                
                        }
                        if (sUUIDOrig.equals(sBatchUUID)) {
                            log("Skipping BACKUP for this file. This is not the original batch for this file.", 2);                        
                        } 
                    }

                    //SYNC
                    //generate registers for sync
                    
                    if (sUUIDOrig.equals(sBatchUUID)) {      
                        Iterator It2 = occurences_uuid_sync.keySet().iterator();
                        while (It2.hasNext()) {               
                            String sNodeName = (String) It2.next();   
                            boolean bNodeDead = get_deadnode(sNodeName);
                             if (!bNodeDead) {
                                log("sync added for Node: " + sNodeName, 2);

                                int was = 0;
                                Object Got2;
                                if ((Got2 = occurences_uuid_files.get(sNodeName)) != null) {
                                    was = (((Integer) Got2).intValue());
                                }
                                occurences_uuid_files.put(sNodeName, new Integer(was + 1));

                                //write to temp file
                                String sFileName = sRoot + "backup_" + sNodeName + "_" + sSeqID + "_" + nBatch + ".tmp";
                                //p("Storing in file: " + sFileName);
                                //p("Storing row: " + sRow);
                                String sRow2 = sRow + ",sync" + "\n";
                                FileWriter fw = new FileWriter(sFileName, bAppend);
                                BufferedWriter out = new BufferedWriter(fw);
                                out.write(sRow2);
                                out.close();   
                             }
                        }                         
                    } else {
                        log("Skipping SYNC for this file. This is not the original batch for this file.", 2);                        
                    }                                                                                   
            
            return 0;
         } catch (Exception e) {
             e.printStackTrace();
             log("Warning: There was an exception in BackupObject", 0);
             log(e.getMessage(), 0);
             return -1;
         }
    }
    
    public int backupObjects(String _keyin, 
            long _batchnum, String sRoot, 
            String sSeqID, 
            int _REPLICATION_FACTOR, 
            int _loglevel,
            String _sBatchUUID) {
        
        BufferedReader br = null;
        mLogLevel = _loglevel;

        try {
                        
            Date ts_start = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String sDate = sdf.format(ts_start);

            String sFilename = appendageRW + LOG_NAME_BACKUP_DEBUG_PATH + sDate + "_backup_server_debug.log";
            log = new PrintStream(new BufferedOutputStream(
                        new FileOutputStream(sFilename, true)));
            log("opening log file: " + sFilename, 0);            
            log("----Backup Batch: '" + _batchnum + "' -- of UUID: " + _sBatchUUID, 1);
            
            int r = loadNodes("nodes", "NodeInfo");                            
            log("LoadNodes res '" + r + "'", 1);
            
            HashMap<String, Integer> occurences_uuid_files = new HashMap<String, Integer>();
            set_backupnodes("keyspace", "NodeInfo","nodes", occurences_uuid_files, "backup");
                                    
            String sPath = appendage + DB_PATH;
            String _cf = "Standard1";
            String sCurrentLine = "";
            String _key = "batch@" + Long.toString(_batchnum);

            String filename = sPath + File.separator + _cf + File.separator + _key;
            File ft = new File(filename);
            if (ft.exists()) { 
                
                    FileReader f = new FileReader(filename);
                    br = new BufferedReader(f);

                    int nCount = 0;
                    int nCount3 = 0;
                    boolean bAppend = true;
                    
                    boolean bError = false;
                    while (((sCurrentLine = br.readLine()) != null)) {
                        //p(sCurrentLine);
 
                        String delimiters = ",";
                        StringTokenizer st = new StringTokenizer(sCurrentLine, delimiters, true);

                        try {                            
                            String name = st.nextToken();
                            st.nextToken();
                            String value = st.nextToken();
                            st.nextToken();
                            String _filename = st.nextToken();
                            //String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
                            //String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());     
                            //p("'" + name + "' , '" + value + "'");

                            String sNamer = value;
                            log("Processing: '" + sNamer + "' - " + nCount3, 2);                                                
                            nCount3++;
                            if (nCount3 % 25 == 0) {
                                p("ZZZzzz [BackupObjects: 250ms]");
                                try {
                                    Thread.sleep(250);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            int nres = backupObject(sNamer, "paths", _batchnum, occurences_uuid_files, sRoot, sSeqID, bAppend, _REPLICATION_FACTOR, _sBatchUUID);                
                            //p("nres backupobject= " + nres);

                            if (nres < 0) {
                                log("WARNING: backupobject returned -1 : " + _batchnum + " " + sNamer, 0);
                                //return -1;
                            }
                            
                            if (nCount3 == 1) {
                                bAppend = true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            bError = true;
                        }                       
                    }

                    if (bError) {
                        log("WARNING: There was an exception parsing the batch file: " + filename, 0);
                    }
                    Iterator It2 = occurences_uuid_files.keySet().iterator();
                    while (It2.hasNext()) {
                        String sNamer = (String) It2.next();
                        nCount3 = (Integer) occurences_uuid_files.get(sNamer);
                        p(sNamer + " - " + nCount3);
                        String sFileName = sRoot + "backup_" + sNamer + "_" + sSeqID + "_" + _batchnum;
                        if (nCount3 == 0) {
                            log("blank batch: " + _batchnum + " needed for Node: " + sNamer, 2);

                            log("filename: " + sFileName, 2);
                            String sRow = "0,0,0";
                            try { FileWriter fw = new FileWriter(sFileName, false);
                            BufferedWriter out = new BufferedWriter(fw);
                            out.write(sRow);
                            out.close();
                            } catch (Exception ex) {
                                log("ERROR: writing blank file: " + sFilename, 0);
                                return -1;
                            }
                            log("done write blank batch", 2);
                        } else {
                            log("renaming tmp file '" + sFileName + ".tmp" + "'", 2);
                            try {
                                File fr = new File(sFileName + ".tmp");
                                boolean bRes = fr.renameTo(new File(sFileName));
                                log("res rename = "+ bRes, 2);
                                if (!bRes) {
                                    log("ori file exists. deleting = '" + sFileName + "'", 2);
                                    File fro = new File(sFileName);
                                    fro.delete();
                                    bRes = fr.renameTo(new File(sFileName));    
                                    log("res rename2 = "+ bRes, 2);
                                }
                            } catch (Exception e) {
                                log("Exception renaming tmp file", 0);
                            }
                        }
                    }

                
            } else {
                log("WARNING: batch filename not found: = " + filename, 0); 
                return -1;
            }
           
            return 0;

        } catch (Exception ex) {
            ex.printStackTrace();
            return -1;
        }
        
    }   
    
    
    public int set_deadnodes(String _cf, String _key) {
        
        BufferedReader br = null;

        try {
            
            String sPath = appendage + DB_PATH;
            String sres = "";
        
            if (_key.equals("nodes")) {
                //info for all nodes
                String filename = sPath + File.separator + _cf;                
                File ft = new File(filename);
                if (ft.exists()) {
                    //directory exists
                    
                    File[] files = ft.listFiles();
                    for (File fn: files) {    
                        
                        //p("Getting lastping for" + fn.getName());
                                                
                        String node_lastping = get_row_attribute("Keyspace1b","NodeInfo", fn.getName(), "lastping", null);
                        
                        //p("lastping: " + fn.getName());
                        if (node_lastping.length() > 0) {
                            long timestamp = System.currentTimeMillis();
                            long timestampDiff = timestamp - Long.parseLong(node_lastping);
                            int diffDays = (int)(timestampDiff / (24 * 60 * 60 * 1000));

                            //if the last ping was more than 30 days ago, set as dead node
                            if(diffDays > 30){
                                deadnodes_uuid.put(fn.getName(), true);
                            }                    
                        }
                    }
                    return 0;
                }  else {
                    //file not found
                    p("File not found: " + filename);
                    return -1;
                }                
            } else {
                return -1;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
               
    }
    
    public String get_node_cols(String _name) {
        
            BufferedReader br = null;
                       
            try {
                String filename = appendage + DB_PATH + File.separator + "NodeInfo" + File.separator + _name;
                
                File fh = new File(filename);
                if (fh.exists()) {
                    FileReader f = new FileReader(filename);
                    br = new BufferedReader(f);

                    String sCurrentLine = "";

                    String node_name = _name;
                    String node_ip = "";
                    String node_port = "";
                    String node_nettyport = "";
                    String node_backup = "";
                    String node_free = "";
                    String node_sync = "";
                    String node_lastbat = "";
                    String node_lastseq = "";
                    String node_lastping = "";
                    String node_machine = "";

                    while (((sCurrentLine = br.readLine()) != null)) {
                        //p(sCurrentLine);
                        String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
                        String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());     
                        //p("'" + name + "' , '" + value + "'");

                        if (name.equals("ipaddress")) node_ip = get_row_attribute("Keyspace1b","NodeInfo",node_name, "ipaddress", null);
                        if (name.equals("port"))node_port = get_row_attribute("Keyspace1b","NodeInfo",node_name, "port", null);
                        if (name.equals("nettyport"))node_nettyport = get_row_attribute("Keyspace1b","NodeInfo",node_name, "nettyport", null);
                        if (name.equals("backup"))node_backup = get_row_attribute("Keyspace1b","NodeInfo",node_name, "backup",null);
                        if (name.equals("free"))node_free = get_row_attribute("Keyspace1b","NodeInfo",node_name, "free", null);
                        if (name.equals("sync"))node_sync = get_row_attribute("Keyspace1b","NodeInfo",node_name, "sync", null);
                        if (name.equals("lastbat"))node_lastbat = get_row_attribute("Keyspace1b","NodeInfo",node_name, "lastbat", null);
                        if (name.equals("lastseq"))node_lastseq = get_row_attribute("Keyspace1b","NodeInfo",node_name, "lastseq", null);
                        if (name.equals("lastping"))node_lastping = get_row_attribute("Keyspace1b","NodeInfo",node_name, "lastping", null);
                        if (name.equals("machine"))node_machine = get_row_attribute("Keyspace1b","NodeInfo",node_name, "machine", null);
                    }

                    String sLine =  node_name + "," + 
                                    node_ip + "," + 
                                    node_port + "," + 
                                    node_nettyport + "," +
                                    node_backup + "," + 
                                    node_free + "," +
                                    node_sync + "," +
                                    node_lastbat + "," +
                                    node_lastseq + "," +
                                    node_lastping + "," +
                                    node_machine + "," +
                                    "\n";
                    return sLine;                    
                } else {
                    p("ERROR: FILE DOES NOT EXIST: " + filename);
                    return "ERROR";
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                return "ERROR";
            }            
            
    }
    
    public static int loadNodes(String _key, String _cf) {
        
        BufferedReader br = null;

        try {
            
            String sPath = appendage + DB_PATH;
            String sres = "";
        
            if (_key.equals("nodes")) {
                //info for all nodes
                String filename = sPath + File.separator + _cf;                
                File ft = new File(filename);
                if (ft.exists()) {
                    //directory exists
                    
                    File[] files = ft.listFiles();
                    for (File fn: files) {    
                        //p("Getting configs for" + fn.getName());
                        String sIP = get_row_attribute("Keyspace1b","NodeInfo",fn.getName(), "ipaddress", null);
                        String sPort = get_row_attribute("Keyspace1b","NodeInfo",fn.getName(), "port", null);                        
                        //p("sres = '" + sres + "'");                        
                        String sIPPort = sIP + ":" + sPort;
                        //p("loading '" + fn.getName() + "' into map -> " + sIPPort);
                        setSuffix(fn.getName(), sIPPort);
                    }                                    
                    return 0;
                }  else {
                    //file not found
                    p("File not found: " + filename);
                    return -1;
                }                
            } else {
                return -1;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        
        
    }
    
    public ArrayList<Node> getNodes(String _key, String _cf){
        ArrayList<Node> nodes = new ArrayList<Node>();
        String sPath = appendage + DB_PATH;
        String filename = sPath + File.separator + _cf;                
        File ft = new File(filename);
        
        if (ft.exists()) {
            File[] files = ft.listFiles();
            for (File fn: files) {    
                p("Getting configs for" + fn.getName());

                BufferedReader br = null;
                       
                try {
                    String filename2 = DB_PATH + File.separator + "NodeInfo" + File.separator + fn.getName();

                    File fh = new File(filename2);
                    if (fh.exists()) {
                        FileReader f = new FileReader(filename2);
                        br = new BufferedReader(f);

                        String sCurrentLine = "";

                        String node_name = fn.getName();
                        String node_ip = "";
                        String node_port = "";
                        String node_machine = "";
                        String node_lastping = "";

                        while (((sCurrentLine = br.readLine()) != null)) {
                            p(sCurrentLine);
                            String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
                            String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());     
                            p("'" + name + "' , '" + value + "'");

                            if (name.equals("ipaddress")) node_ip = get_row_attribute("Keyspace1b","NodeInfo",node_name, "ipaddress", null);
                            if (name.equals("port"))node_port = get_row_attribute("Keyspace1b","NodeInfo",node_name, "port", null);
                            if (name.equals("machine"))node_machine = get_row_attribute("Keyspace1b","NodeInfo",node_name, "machine", null);
                            if (name.equals("lastping"))node_lastping = get_row_attribute("Keyspace1b","NodeInfo",node_name, "lastping", null);
                        }
                        
                        Node n = new Node(node_machine,node_ip, node_port, "pc", node_lastping, node_name);
                        nodes.add(n);
                        
                    } 
                
                } catch (Exception e) {
                    e.printStackTrace();
                }            
            }

        }  else {
            p("File not found: " + filename);
        }                

        return nodes;
    }
            
    public String get_node_info(String _key, String _cf) {
        
        BufferedReader br = null;

        try {
            Appendage app = new Appendage();
            appendage = app.getAppendage();
            appendageRW = app.getAppendageRW();
            p("GET node info appendage = " + appendage);
            String sPath = appendage + DB_PATH;
            p("sPath = " + sPath);

            String sres = "";
        
            if (_key.equals("nodes")) {
                //info for all nodes
                String filename = sPath + File.separator + _cf;                
                File ft = new File(filename);
                if (ft.exists()) {
                    //directory exists
                    
                    File[] files = ft.listFiles();
                    for (File fn: files) {    
                        //p("Getting configs for node: '" + fn.getName() + "'");
                        sres += get_node_cols(fn.getName());   
                        //p("sres = '" + sres + "'");
                    }
                    
                    return sres;
                }  else {
                    //file not found
                    p("File not found: " + filename);
                    return "ERROR";
                }                
            } else {
                return "@@@WIP@@@";
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
        
        
    }
    
    public String get_batches(String _key, String _password, String _View, String _FileType, String _DaysBack, String _NumCol, String _NumObj) {

        BufferedReader br = null;

        try {
            
            String sheader = "";
            String sres = "";

            sheader += "<table class=\"table table-striped\">";            
            sheader += "<tr>";
            sheader += "<th>id</th>";
            sheader += "<th>nDeleted</th>";
            sheader += "<th>nFiles</th>";
            sheader += "<th>nInsert</th>";
            sheader += "<th>nAutoComplete</th>";
            sheader += "<th>nInsertHash</th>";
            sheader += "<th>nRetry</th>";
            sheader += "<th>nSkipSubFolders</th>";
            sheader += "<th>nSkipSubStrings</th>";
            sheader += "<th>ts_end</th>";
            sheader += "<th>ts_start</th>";
            sheader += "<th>ts_uuid</th>";
            sheader += "</tr>";
            
            int nCount = 0;
            boolean stopheader = false;

                        
            p("key1:" + _key);            
            _key = _key.replaceAll(":", "@");
            _key = _key.replaceAll("/", "#");            
            p("key2:" + _key);            
            
            String sPath = appendage + DB_PATH;
            String _cf = "BatchJobs";
            
        
            String filename = sPath + File.separator + _cf + File.separator + _key;
            File ft = new File(filename);
            if (ft.exists()) {
                FileReader f = new FileReader(sPath + File.separator + _cf + File.separator + _key);            
                br = new BufferedReader(f);

                String sCurrentLine = "";
                while (((sCurrentLine = br.readLine()) != null)) {
                    //p(sCurrentLine);
                    String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
                    String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());     
                    //p("'" + name + "' , '" + value + "'");
                    
                    String[] sCol = new String[12];
                    
                    sCol[0] = name;
                    sCol[1] = get_row_attribute("keyspace", "BatchJobs", name, "nDeleted", null);
                    sCol[2] = get_row_attribute("keyspace", "BatchJobs", name, "nFiles", null);
                    sCol[3] = get_row_attribute("keyspace", "BatchJobs", name, "nInsert", null);
                    sCol[4] = get_row_attribute("keyspace", "BatchJobs", name, "nInsertAutoComplete", null);
                    sCol[5] = get_row_attribute("keyspace", "BatchJobs", name, "nInsertHash", null);
                    sCol[6] = get_row_attribute("keyspace", "BatchJobs", name, "nRetry", null);
                    sCol[7] = get_row_attribute("keyspace", "BatchJobs", name, "nSkipSubFolders", null);
                    sCol[8] = get_row_attribute("keyspace", "BatchJobs", name, "nSkipSubStrings", null);
                    sCol[9] = get_row_attribute("keyspace", "BatchJobs", name, "ts_end", null);
                    sCol[10] = get_row_attribute("keyspace", "BatchJobs", name, "ts_start", null);
                    sCol[11] = get_row_attribute("keyspace", "BatchJobs", name, "uuid", null);                    
                                        
                    sres += "<tr>";
                    for (int i=0;i<12;i++) {
                        sres += "<td>" + sCol[i] + "</td>";
                    }              
                    sres += "</tr>";
                }
                return sheader + sres + "</table>";
            }  else {
                //file not found
                p("File not found: " + filename);
                return "";
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
                
    }
    public static String getNumberofCopies(String _superColumnName, String _key, String _clientIP, String _serverIP, boolean _forceload) {
         
         int nCopies = 0;
         int nNodes = 0;
         int nCopiesLocal = 0;
         
         BufferedReader br = null;
         
         try {
                String sPath = appendage + DB_PATH;
                String _cf = "Super2";
                String _sc = "paths";       
                String sCurrentLine = "";
                String sIP = _serverIP;

                String filename = sPath + File.separator + _cf + File.separator + _sc + File.separator + _key;
                File ft = new File(filename);
                
                
//NOTA: El loadblacklistmap hay que correrlo solo la primera vez, sino lo corre para cada elemento.               
                
                if (ft.exists()) {                 
                        if (!bNodesLoaded || _forceload) {
                            p("Loading Blacklist");
                            loadBlacklistMap();
                            loadBlacklistContainsMap();
                            p("Loading Nodes");
                            int r = loadNodes("nodes", "NodeInfo");                            
                            bNodesLoaded = true;    
                        } else {
                            //p("Skipping loadNodes(). Already loaded.");
                        }
                        occurences_uuid.clear();

                        HashMap FileRecords = new HashMap();
                        
                        FileReader f = new FileReader(filename);            
                        br = new BufferedReader(f);   
                        while (((sCurrentLine = br.readLine()) != null)) { 
                            
                            try {
                                boolean bDUP = false;
                                Object entry = FileRecords.get(sCurrentLine);
                                if (entry == null && sCurrentLine.trim().length() > 0) {
                                    FileRecords.put(sCurrentLine,"");
                                } else {
                                    if (sCurrentLine.trim().length() > 0) {
                                        //p("DUP record: " + _key + " " + entry);
                                        bDUP = true;                                        
                                    }
                                }
                                
                                if (!bDUP && sCurrentLine.trim().length() > 0) {
                                    String colName = sCurrentLine.substring(0, sCurrentLine.indexOf(","));                                                                   
                                    String colValue = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());   

                                    int npos =  colName.indexOf(":");
                                    String sUUID = "";
                                    if (npos > 0) {
                                        sUUID = colName.substring(0,npos);
                                    } 

                                    int was = 0;
                                    Object Got;
                                    if ((Got = occurences_uuid.get(sUUID)) != null) {
                                        was = (((Integer) Got).intValue());
                                    }

                                    if (!colValue.equals("DELETED")) {
                                        boolean bNodeDead = get_deadnode(sUUID);
                                        String sFilePath = colName.substring(npos+1,colName.length()-1);
                                        //p("getnumberofcopies sFilePath = '" + sFilePath + "'");
                                        //p("isblacklisted = " + isBlacklisted(sFilePath));
                                        //p("isblacklistedcontains = " + isBlacklistedContains(sFilePath));
                                        //p("isparentblacklisted = " + isParentBlacklisted(sFilePath));
                                        if (!bNodeDead && !isBlacklisted(sFilePath) && !isBlacklistedContains(sFilePath) && !isParentBlacklisted(sFilePath)) {
                                            //p("addding case for UUID = " + sUUID);
                                            synchronized(occurences_uuid) {
                                                occurences_uuid.put(sUUID, new Integer(was + 1));
                                            }
                                        } else {
                                            if (bNodeDead) {
                                                p("Skipped - Node is DEAD: " + sUUID + " " + _key + " : " + colName + " " + colValue);                                            
                                            }
                                            if (isBlacklisted(sFilePath)) {
                                                //p("Skipped - Path is blacklisted: " + sFilePath);
                                            }
                                            if (isBlacklistedContains(sFilePath)) {
                                                //p("Skipped - Path is blacklisted(contains): " + sFilePath);
                                            }
                                        }
                                    } else {
                                        //p("Skipped - File Deleted: " + sUUID + " " + _key + " : " + colName);
                                    }                                    
                                } else {
                                    //if (sCurrentLine.trim().length() > 0) p("Skipped blank line for file:" + filename);
                                    //if (bDUP) p("Skipped dup line for file:" + filename);
                                }
                            } catch (Exception e) {
                                p("ERROR: Parsing path: '" + sCurrentLine + "' len:" + sCurrentLine.length());
                                e.printStackTrace();
                            }                                                        
                        }
                        br.close();
                        f.close();
                        
                        if (_clientIP.equals("0:0:0:0:0:0:0:1") || 
                            _clientIP.equals("0:0:0:0:0:0:0:1%0") ||
                            _clientIP.equals("127.0.0.1")) {
                            _clientIP = sIP;
                        }

                        synchronized (occurences_uuid) {
                            Iterator bit = occurences_uuid.keySet().iterator();                            
                                                
                            while (bit.hasNext()) {                           
                                String sUUID = (String) bit.next();

                                if ((Integer)occurences_uuid.get(sUUID) > 0) {
                                    nNodes++;
                                }
                                nCopies += (Integer) occurences_uuid.get(sUUID);

                                String sIPPort = (String) map.get(sUUID);                                                        
                                sIP = "";
                                if (sIPPort != null) {
                                    sIP = sIPPort.substring(0,sIPPort.indexOf(":"));

                                    if (sIP.equals(_clientIP)) {
                                        nCopiesLocal += (Integer) occurences_uuid.get(sUUID);
                                    } else {
                                        //p("Not local: " + sIP + " vs " + _clientIP);
                                    }
                                } else {
                                    p("NOT Found " + sUUID + " ipport = " + sIPPort);                                                                        
                                }
                        } //while
                     } //sychrnonized
                } else {
                    p("FILE NOT FOUND: " + ft.getCanonicalPath());
                }
                    //if exist          
                return nCopies + "," + nNodes + "," + nCopiesLocal;
                
         } catch (Exception e) {
             e.printStackTrace();
             return "999,999,999";
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
    
    static void loadBlacklistMap() {
        try {
            FileInputStream bf2 = new FileInputStream("../scrubber/config/blacklist.txt");
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
    
    static boolean isBlacklistedContains(String _filepath) {
        boolean bBlack = false;
        boolean bCont = true;
        
        Iterator it = mapBlackC.entrySet().iterator();
                
        String _path = "";
        while (it.hasNext() && bCont) {
            Map.Entry pairs = (Map.Entry)it.next();

            String theKey = (String)pairs.getKey();
            theKey = theKey.replace("\\", "/");
            _path = _filepath.substring(0,_filepath.lastIndexOf("/")+1);            
            if (_path.trim().toLowerCase().contains(theKey.trim().toLowerCase())) {
                bCont = false;
                bBlack = true;
                //p("Directory '" + _path + "' CONTAINS '" + theKey + "'"); 
            } else {
                //p("Directory '" + _path + "' NOT CONTAINS '" + theKey + "'");  
            }
        }
        
        if (bBlack) {
            //p("Filepath '" + _filepath + "' is blacklistedcontains.");            
        } else {
            //p("Filepath '" + _filepath + "' is NOT blacklistedcontains.");                        
        }
        return bBlack;
        
    }
    
    static boolean isParentBlacklisted(String _filepath) {
        boolean bBlack = false;
        boolean bCont = true;
        
        Iterator it = mapBlack.entrySet().iterator();
                
        String _path = "";
        while (it.hasNext() && bCont) {
            Map.Entry pairs = (Map.Entry)it.next();

            String theKey = (String)pairs.getKey();
            theKey = theKey.replace("\\", "/");
            _filepath = _filepath.replace("\\", "/");
            
            _path = _filepath.substring(0,_filepath.lastIndexOf("/")+1);
            
            if (theKey.startsWith("/") && !_path.startsWith("/")) {
                _path = "/" + _path;
            }
            //p("Filepath: " + _filepath + " Path: " +_path);
            
            if (_path.trim().contains(theKey.trim())) {
                //p("Directory '" + _path + "' IS '" + theKey + "'");  
                bCont = false;
                bBlack = true;
            } else {
                //p("Directory '" + _path + "' NOT '" + theKey + "'");  
            }
        }
        
        if (bBlack) {
            //p("Filepath '" + _filepath + "' is ParentBlacklisted.");            
        } else {
            //p("Filepath '" + _filepath + "' is NOT Parentblacklisted.");                        
        }
        return bBlack;
        
    }
    
    static boolean isBlacklisted(String _filepath) {
        boolean bBlack = false;
        boolean bCont = true;
        
        Iterator it = mapBlack.entrySet().iterator();
                
        String _path = "";
        while (it.hasNext() && bCont) {
            Map.Entry pairs = (Map.Entry)it.next();

            String theKey = (String)pairs.getKey();
            theKey = theKey.replace("\\", "/");
            _filepath = _filepath.replace("\\", "/");
            
            _path = _filepath.substring(0,_filepath.lastIndexOf("/")+1);
            
            if (theKey.startsWith("/") && !_path.startsWith("/")) {
                _path = "/" + _path;
            }
            
            //p("Filepath: " + _filepath + " Path: " +_path);
            
            if (_path.trim().equalsIgnoreCase(theKey.trim())) {
                //p("Directory '" + _path + "' IS '" + theKey + "'");  
                bCont = false;
                bBlack = true;
            } else {
                //p("Directory '" + _path + "' NOT '" + theKey + "'");  
            }
        }
        
        if (bBlack) {
            //p("FilePath '" + _filepath + "' is blacklisted.");            
        } else {
            //p("FilePath '" + _filepath + "' is NOT blacklisted.");                        
        }
        return bBlack;
        
    }
    
    public sURLPack get_remote_link2 (final String _key, 
            final String superColumnName, 
            boolean _skipIP, 
            boolean _cloudhosted, 
            String _clientIP, 
            boolean _bMatchUUID,
            boolean _bCheckAvail,
            String _host) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {
        
            BufferedReader br = null;
            sURLPack up = new sURLPack();                       

            try {                
                
                if (!bNodesLoaded) {
                    p("Loading Nodes");
                    int r = loadNodes("nodes", "NodeInfo");                            
                    bNodesLoaded = true;
                }
                
                String sPath = appendage + DB_PATH;
                String _cf = "Super2";
                String _sc = "paths";       
                String sCurrentLine = "";

                String filename = sPath + File.separator + _cf + File.separator + _sc + File.separator + _key;
                File ft = new File(filename);
                //p("filename = " + filename);
                if (ft.exists()) {       
                        FileReader f = new FileReader(filename);            
                        br = new BufferedReader(f);
            
                        String uri = "";
                        //String uri2 = "";
                        String sFolderURL = "";
                        String sOpenURL = "";
                        String sViewURL = "";
                        String sSendURL = "";
                        String sViewURLJSON_IP = "";
                        String sViewURLJSON_Port = "";
                        String sViewURLJSON_Path = "";
                        String sViewURLJSON_Extension = "";
                        String sRemoteURLJSON_Path = "";
                        
                        String sUUIDOrig = "";
                        if (_bMatchUUID) {
                            sUUIDOrig = get_row_attribute(keyspace, "Standard1", _key, "uuid_ori", null);
                        }
                        
                        boolean bFound = false;
                        while (((sCurrentLine = br.readLine()) != null) && !bFound) {                            
                            if (sCurrentLine.length() > 0) {
                                String sNodeUri = sCurrentLine.substring(0,sCurrentLine.lastIndexOf(","));
                                String sFilePath = sNodeUri.substring(sNodeUri.indexOf(":")+1, sNodeUri.length());

                                //view URL
                                //String sExtension = get_row_attribute("", "Standard1", _key, "ext");     
                                String sEnc = "";
                                if(sFilePath.endsWith("/")){
                                    sEnc = sFilePath.substring(0, sFilePath.length()-1);
                                } else {
                                    sEnc = sFilePath;
                                }
                                //p("path to enc = " + sEnc);
                                
                                String sPathB64 = Base64.encodeToString(sEnc.getBytes(), false);
                                //p("path sPathB64 = " + sPathB64);
                                
                                //p("uri = " + sNodeUri);
                                String sUUID = sNodeUri.substring(0,sNodeUri.indexOf(":"));                                                        
                                if (!_bMatchUUID) {
                                    //if we don't need to look for Original UUID.
                                    sUUIDOrig = sUUID;
                                }
                                //String sIP = get_row_attribute("Keyspace1b","NodeInfo",sUUID, "ipaddress");
                                //String sPort = get_row_attribute("Keyspace1b","NodeInfo",sUUID, "port");

                                if ((sUUIDOrig.length() > 0 && sUUIDOrig.equals(sUUID)) || !_bMatchUUID) {

                                    //String sIPPort = get_row_attributes("Keyspace1b", "NodeInfo", sUUID, "ipaddress", "port");
                                    String sIPPort = (String) map.get(sUUID);
                                    //p(sUUID + " ipport = " + sIPPort);

                                    if (sIPPort != null) {
                                        String sIP = sIPPort.substring(0, sIPPort.indexOf(":"));
                                        String sPort = sIPPort.substring (sIPPort.indexOf(":")+1, sIPPort.length());
                                        
                                        Boolean bAvailable = true;
                                        if (_bCheckAvail) {
                                            bAvailable = isNodeAvailable(sIP, sPort);                                            
                                        } else {
                                            p("Skip node check.");
                                        }
                                        if (bAvailable) {
                                            
                                            //we can exit if we don't need to keep looking for remote copy.
                                            if (_host.length() == 0) bFound = true;
                                            
                                            //open URL
                                            String sPathEnc = URLEncoder.encode(sFilePath, "UTF-8");    
                                            if (_clientIP.equals(sIP)) {
                                                sOpenURL = "http://" + sIP + ":" + sPort + "/cass/redir.php?foo=" + sPathEnc + "&rem=0";                                        
                                            } else {                                        
                                                sOpenURL = "http://" + sIP + ":" + sPort + "/cass/redir.php?foo=" + sPathEnc + "&rem=1";                                        
                                            }
                                            String sExtension = get_row_attribute("", "Standard1", _key, "ext",null);     
                                            sViewURLJSON_IP = sIP;
                                            sViewURLJSON_Port = sPort;
                                            sViewURLJSON_Path = "http://" + sIP + ":" + sPort + "/" + sPathB64 + "." + sExtension;
                                            sViewURLJSON_Extension = sExtension;  
                                            
                                            p("COMP *** " + _host.length() + " " + _host + " " + sIP);
                                            File f2 = new File(sFilePath);
                                            if ((_host.length() > 0) && _host.equals(sIP) && sRemoteURLJSON_Path.length() == 0) {
                                               
                                                if (f2.exists()) {
                                                    p("FILE exists in server: " + sFilePath);
                                                    sRemoteURLJSON_Path = sPathEnc;                                                    
                                                } else {
                                                    p("FILE NOT exists in server: " + sFilePath);                                                    
                                                }
                                            } else {
                                                if (f2.exists()) {
                                                    p("FILE exists in server[2]: " + sFilePath);
                                                    sRemoteURLJSON_Path = sPathEnc;                                                                                                        
                                                }
                                            }
                                        } else {
                                            p("Node is not available: " + sIP + ":" + sPort);
                                        }                                        
                                    }

                                    //String sHeight = get_row_attribute(keyspace, "Standard1", _key, "img_height");
                                    //String sWidth = get_row_attribute(keyspace, "Standard1", _key, "img_width");
                                    //String sHeightWidth = get_row_attributes(keyspace, "Standard1", _key, "img_height", "img_width");
                                    //String sHeight = sHeightWidth.substring(0, sHeightWidth.indexOf(","));
                                    //String sWidth = sHeightWidth.substring (sHeightWidth.indexOf(","), sHeightWidth.length());

                                    sViewURL = "/cass/viewimg2.htm?sNamer=" + _key + "&sFileName=" + sPathB64;
                                    //sViewURL = "http://" + sIP + ":" + sPort + "/" + sPathB64 + "." + sExtension;  
                                    //sViewURL = "vewimg.htm?file=" + sPathB64 + "&host=" + sIP + "&port=" + sPort + "&height=" + sHeight + "&width="+sWidth;

                                    //send URL
                                    String sPathB64Enc = URLEncoder.encode(sPathB64, "UTF-8");                             
                                    sSendURL = "/cass/sendform.htm?file=" + sPathB64Enc; //+ "&host=" + sIP + "&port=" + sPort + "&height=" + sHeight + "&width=" + sWidth;

                                    

                                    //folder URL
                                    String sFolderURLtmp = sOpenURL;                            
                                    sFolderURLtmp = sFolderURLtmp.replaceAll("%5C", "%2F");                                    
                                    String sFileName = "";
                                    if (sFolderURLtmp.contains("%2F")) {
                                        sFolderURLtmp = sFolderURLtmp.substring(0,sFolderURLtmp.lastIndexOf("%2F"));
                                        sFileName = sFolderURLtmp.substring(sFolderURLtmp.lastIndexOf("%2F")+3, sFolderURLtmp.length());
                                        if (sFolderURLtmp.contains("%2F")) {
                                            sFolderURLtmp = sFolderURLtmp.substring(0, sFolderURLtmp.lastIndexOf("%2F"));                                                                                                                   
                                            sFolderURL =  sFolderURLtmp;                                                              
                                        } else {
                                            sFolderURL = "";
                                        }
                                    } else {
                                        sFolderURL = "";
                                    }
                                    sFolderURL = sFolderURL + "&filename=" + sFileName;                                
                                } else {
                                    //p("Skipping this copy (not original). UUID: " + sUUID);
                                }                                 
                            } else {
                                //p("Skipping blank line.");    
                            }
                                
                        }
                        up.sOpenURL = sOpenURL;
                        up.sViewURL = sViewURL;
                        up.sFolderURL = sFolderURL;
                        up.sSendURL = sSendURL;
                        up.sViewURLJSON_IP = sViewURLJSON_IP;
                        up.sViewURLJSON_Port = sViewURLJSON_Port;
                        up.sViewURLJSON_Path = sViewURLJSON_Path;
                        up.sViewURLJSON_Extension = sViewURLJSON_Extension;
                        up.sRemoteURLJSON = sRemoteURLJSON_Path;
                        
                        //p("sOpenURL = " + up.sOpenURL);
                        //p("sViewURL = " + up.sViewURL);
                        //p("sFolderURL = " + up.sFolderURL);
                        
                        //Thread.sleep(5000);
                        
                        return up;
                } else {
                    return up;
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                up.sOpenURL = "ERROR";
                return up;
            }
     }
        
    public String read_openFolder_link(String sNamer, String sFileName, String _keyin, String _host, String _port, Boolean _cloudhosted, String _clientIP) {
            try {
                sURLPack up = new sURLPack();
                String sFolderURL = "";
                up = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false, "");    

                if (up.sFolderURL.length() > 0 && !up.sFolderURL.equals("ERROR")) {
                    sFolderURL = up.sFolderURL;
                } else {
                    p("*******************************************");
                    p("************ sURLPack is empty ************");
                    p("*******************************************");
                }
                return sFolderURL;                
            } catch (Exception e) {
                e.printStackTrace();
                return "ERROR";
            }

    }    
    
    public String read_open_link(String sNamer, String sFileName, String _keyin, String _host, String _port, Boolean _cloudhosted, String _clientIP) {

            try {
                sURLPack up = new sURLPack();
                String sOpenURL = "";
                up = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false, "");    

                if (up.sOpenURL.length() > 0 && !up.sOpenURL.equals("ERROR")) {
                    sOpenURL = up.sOpenURL;
                } else {
                    p("*******************************************");
                    p("************ sURLPack is empty ************");
                    p("*******************************************");
                }
                return sOpenURL;                
            } catch (Exception e) {
                e.printStackTrace();
                return "ERROR";
            }

    }
       
     public String get_file_path(String _key, boolean _encode) {

            BufferedReader br = null;
            
            
            try {
                String sPath = appendage + DB_PATH;
                String _cf = "Super2";
                String _sc = "paths";       
                String sCurrentLine = "";
                String sUUID = "";
                String sPathEnc = "";
                
                String filename = sPath + File.separator + _cf + File.separator + _sc + File.separator + _key;         
                File ft = new File(filename);
                if (ft.exists()) {
                            FileReader f = new FileReader(filename);            
                            br = new BufferedReader(f);

                            String uri = "";
                            while (((sCurrentLine = br.readLine()) != null)) {  
                                String sNodeUri = sCurrentLine.substring(0,sCurrentLine.lastIndexOf(","));
                                String sFilePath = sNodeUri.substring(sNodeUri.indexOf(":")+1, sNodeUri.length()) + "/";
                            
                                if (_encode) {                                
                                    String sPathEnv = Base64.encodeToString(sFilePath.getBytes(), false);
                                    sPathEnc = URLEncoder.encode(sPathEnv, "UTF-8");   
                                } else {
                                    sPathEnc = sFilePath;
                                }
                            }          
                }

                return sPathEnc;
                
            } catch (Exception e) {
                return "ERROR";
            }
     }
     
     public String[] read_view_link(String _key, Boolean _mobile, boolean optimized) {
         
            BufferedReader br = null;

            try {
                String sPath = appendage + DB_PATH;
                String _cf = "Super2";
                String _sc = "paths";       
                String sCurrentLine = "";
                String sUUID = "";

                String filename = sPath + File.separator + _cf + File.separator + _sc + File.separator + _key;
                File ft = new File(filename);
                //p("filename = " + filename);
                if (ft.exists()) {       
                        FileReader f = new FileReader(filename);            
                        br = new BufferedReader(f);
            
                        String uri = "";
                        while (((sCurrentLine = br.readLine()) != null)) {
                            //p(sCurrentLine);
                            
                            String sNodeUri = sCurrentLine.substring(0,sCurrentLine.lastIndexOf(","));
                            String sFilePath = sNodeUri.substring(sNodeUri.indexOf(":")+1, sNodeUri.length()) + "/";
                            
                            String sPathEnv = Base64.encodeToString(sFilePath.getBytes(), false);
                            String sPathEnc = URLEncoder.encode(sPathEnv, "UTF-8");                            
                            
                            String sExtension = get_row_attribute("", "Standard1", _key, "ext", null);
                            
                            
                            //p("uri = " + sNodeUri);
                            sUUID = sNodeUri.substring(0,sNodeUri.indexOf(":"));
                            
                            String sIP = get_row_attribute("Keyspace1b","NodeInfo", sUUID, "ipaddress", null);                            
                            String sPort = get_row_attribute("Keyspace1b","NodeInfo", sUUID, "port", null);
                    
                            boolean bAvail_node = isUUIDNodeAvailable(optimized, sUUID, sIP, sPort);
                            
                            if (bAvail_node) {
                                String sAdd = "";
                                String colName = sNodeUri;
                                int npos =  colName.indexOf(":");
                                String sPathDec = sAdd + sNodeUri.substring(npos + 1, colName.lastIndexOf("."));
                                p("spathdec = " + sPathDec);
                                int lastdot = colName.lastIndexOf(".");
                                p("lastdot = " + lastdot);
                                String sExt = colName.substring(lastdot, colName.length()-1);
                                String sPathEnc1 = URLEncoder.encode(sPathDec,  "UTF-8");
                                String sFullPath ="http://"+sIP+ ":" + sPort + "/fileexist.fn?sfileexist=" + sPathEnc1 + sExt ;

                                Boolean bAvail_file = false;
                                if (!optimized) {
                                    int resultGetFileExist = getfile(sFullPath, appendage + "fileexist.fnres", 1, 10,2000);                                
                                    if (resultGetFileExist==1) {
                                        FileReader reader=new FileReader(new File(appendage + "fileexist.fnres"));
                                        char[ ] cbuf =new char[150];
                                        int i=reader.read(cbuf);
                                        reader.close();
                                        File fAux2=new File(appendage + "fileexist.fnres");
                                        if(fAux2.exists()){
                                            fAux2.delete();
                                        }
                                        String sAux= "";
                                        for(int j=0; j<i;j++){
                                            sAux+=cbuf[j];
                                        }

                                        String resFileExist=sAux.split(",")[0];
                                        //String resFileSize=sAux.split(",")[1]; Queda comentado por si en alg�n momento se usa                                        
                                        if (resFileExist!=null && resFileExist.equalsIgnoreCase("s")) {
                                            p("-----bavail");
                                            bAvail_file = true;
                                        } 
                                        if (resFileExist!=null && resFileExist.equalsIgnoreCase("n")) {
                                            p("-----file not found");
                                            uri = "FILENOTFOUND";
                                            bAvail_file = false;
                                        } 
                                    } else {
                                        //some kind of error getting the file.
                                        uri = "ERROR";
                                    }
                                } else {
                                    //optimized case
                                    bAvail_file = true;
                                }
                                                                
                                if (bAvail_file) {
                                    //file is available
                                    String sRedirLink = "";
                                    if (Cass7Funcs.is_photo(sFilePath)) {
                                            /*double dHeightNew = 1000 / nImgRatio;      
                                            int nHeightNew = (int)dHeightNew;
                                            sHeightNew = Integer.toString(nHeightNew);*/
                                            String sHeight = get_row_attribute(keyspace, "Standard1", _key, "img_height", null);
                                            String sWidth = get_row_attribute(keyspace, "Standard1", _key, "img_width", null);

                                            //uri = "http://" + sIP + ":" + sPort + "/" + sPathEnv + "." + sExtension;                                          
                                            if (!_mobile) {
                                                uri = "viewimg.htm?file=" + "" + "&host=" + sIP + "&port=" + sPort + "&height=" + sHeight + "&width="+sWidth + "&md5=" + _key;                                                
                                            } else {
                                                uri = "http://" + sIP + ":" + sPort + "/" + sPathEnv + "." + sExtension;                                                                                          
                                            }                                     
                                    } else {
                                            uri = "http://" + sIP + ":" + sPort + "/" + sPathEnv + "." + sExtension;                                          
                                    }  
                                    //found one! exit...
                                    break;
                                } else {
                                    uri = "FILENOTFOUND";
                                } // if file available
                            } else {
                                uri = "TIMEOUT";
                            } // if node available                          
                        }
                        //p("uri = " + uri);                                
                        if (uri.length() > 0) {
                            String[] res = new String[2];
                            res[0] = uri;
                            res[1] = sUUID;
                            
                            return res;                                                       
                        } else {
                            //not found in any of the nodes
                            String[] res = new String[2];
                            res[0] = "TIMEOUT";
                            res[1] = "TIMEOUT";
                            return res;
                        }
                } else {
                    String[] res = new String[2];
                    res[0] = "ERROR";
                    res[1] = "ERROR";
                    return res;
                }
                
            } catch (Exception e) {
                pw("Exception in read_view_link: " + e.toString());
                String[] res = new String[2];
                res[0] = "ERROR";
                res[1] = "ERROR";
                return res;
            }
     }

    private boolean isUUIDNodeAvailable(boolean optimized, String sUUID, String sIP, String sPort) throws NumberFormatException {
        boolean checkIfNodeAvail = true;
        
        p("isUUIDNodeAvailable ----> : " + sUUID);
        
        if(optimized){
            String node_lastping = get_row_attribute("Keyspace1b","NodeInfo", sUUID, "lastping", null);
            if(node_lastping != null){
                long timestamp = System.currentTimeMillis();
                long timestampDiff = timestamp - Long.parseLong(node_lastping);
                if(timestampDiff < (5*60*1000)){ //5 min
                    checkIfNodeAvail = false;
                }
            }
        }
        boolean bAvail_node;
        if(checkIfNodeAvail){
            bAvail_node = isNodeAvailable(sIP, sPort);
        }else{
            bAvail_node = true;
        }
        return bAvail_node;
    }
     
      String replace_chars(String w) {
            w = w.replace("ñ", "&#241;");
            w = w.replace("á", "&#225;");
            w = w.replace("é", "&#233;");
            w = w.replace("í", "&#237;");
            w = w.replace("ó", "&#243;");
            w = w.replace("ú", "&#250;");

            w = w.replace("Ñ", "&#209;");
            w = w.replace("Á", "&#193;");
            w = w.replace("É", "&#201;");
            w = w.replace("Í", "&#205;");
            w = w.replace("Ó", "&#211;");
            w = w.replace("Ú", "&#218;");    
            return w;
      }
      
      public String read_row_autocomplete_mobile (String _w, 
              String _clientIP, 
              String _filetype, 
              String _numobj, 
              String _host, 
              String _port, 
              String _daysback,
              Boolean _cloudhosted,
              Boolean _awshosted,
              String _user) {           
        try {
            
        SortableValueMapLong<String, Long> occurences = new SortableValueMapLong<String, Long>();

        HashMap map_ac_tags = new java.util.HashMap();   
        //HashMap map_ac_idx = new java.util.HashMap();   
        //HashMap map_ac = new java.util.HashMap();   
        
                
        open_mapdb();

        //DB db_mm1 = tx_mm1.makeTx();
        //DB db_mm2r = tx_mm2.makeTx();        
        DB db_attr = tx_attr.makeTx();                                   
        
        ConcurrentNavigableMap<String,String> occurences_attr_r = db_attr.getTreeMap("attributes");
        
        //NavigableSet<Fun.Tuple2<String,String>> mm1r = db_mm1.getTreeSet("autocomplete"); 
        //NavigableSet<Fun.Tuple2<String,String>> mm2r = db_mm2r.getTreeSet("md5"); 
                
        int i = 1;
        int nObj = 25;
        try {
            nObj = Integer.parseInt(_numobj);
        } catch (Exception e) {
            p("WARNING: Exception parsing numobj: " + _numobj);
        }

        p("SUGGEST : " + _w  + " FILETYPE: " + _filetype + " NUMOBJ: " + nObj);
        String w = _w.toLowerCase();        
                        
//        //from mapdb            
//        for(String s: Bind.findSecondaryKeys(mm2r, w)){
//            p("mapdb value for key " + w + ": " +s);            
//            String[] lineArray = s.split(",");            
//            String filename = lineArray[2];
//            //p("filename = " + filename);
//                        
//            boolean bAdd = false;
//            if (_filetype.equals(".all")) {
//                bAdd = true;
//            }
//            if (_filetype.equals(".photo") && Cass7Funcs.is_photo(filename)) {
//                bAdd = true;
//            }
//            if (_filetype.equals(".music") && Cass7Funcs.is_music(filename)) {
//                bAdd = true;
//            }
//            if (_filetype.equals(".video") && Cass7Funcs.is_movie(filename)) {
//                bAdd = true;
//            }
//            if (_filetype.equals(".document") && Cass7Funcs.is_document(filename)) {
//                bAdd = true;
//            }
//            if (_filetype.equals(".doc") || _filetype.equals(".xls") || _filetype.equals(".ppt") || _filetype.equals(".pdf")) {
//                if (filename.contains(_filetype)) bAdd = true;
//            }
//                       
//            if (bAdd){
//                map_ac_idx.put(filename.toLowerCase(), "filename");                
//            } 
//        } 
//            
//        p("COUNT(MAPDB): " + map_ac_idx.size());

        //from file            
        BufferedReader br = null;

        String sPath = appendage + DB_PATH;
        String _ks = "Standard2";

        w = w.replace(":", "@");
        File fh = new File(sPath + File.separator + _ks + File.separator + w);
        if (fh.exists() && fh.isFile()) {
            FileReader f = new FileReader(sPath + File.separator + _ks + File.separator + w);                        

            br = new BufferedReader(f);
            String sCurrentLine = "";
            //Integer nObj = 25;            

            ShareController sc = ShareController.getInstance();
            while (((sCurrentLine = br.readLine()) != null)) {
                    //p(sCurrentLine);
                    String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
                    String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());     
                    p("'" + name + "' , '" + value + "'");
                    //String value2 = value.replace("ó", "\\u00F3");
                    //String value2 = value;
                    //value2 = value2.replace("ñ", "&#241;");
                    //value2 = value2.replace("á", "&#225;");
                    //value2 = value2.replace("é", "&#233;");
                    //value2 = value2.replace("í", "&#237;");
                    //value2 = value2.replace("ó", "&#243;");
                    //value2 = value2.replace("ú", "&#250;");

                    //value2 = value2.replace("Ñ", "&#209;");
                    //value2 = value2.replace("Á", "&#193;");
                    //value2 = value2.replace("É", "&#201;");
                    //value2 = value2.replace("Í", "&#205;");
                    //value2 = value2.replace("Ó", "&#211;");
                    //value2 = value2.replace("Ú", "&#218;");                       

                    //p("'" + name + "' , '" + value2 + "'");

                    if (isCurrentUserAdmin(_user) || sc.getClusterPermission(_user) != null || 
                            ((sc.allUsers(value) || sc.getPermissionByUser(value, _user) != null ))){
                        map_ac_tags.put(value, "tag");
                    }
                }                        
        }
        
        p("COUNT(TAGS): " + map_ac_tags.size());

        StringBuilder res = new StringBuilder();

        res.append("{\n");
        res.append("\"fighters\": [\n");
        
        p("SUGGEST : " + _w  + " FILETYPE: " + _filetype + " NUMOBJ: " + nObj + " DAYSBACK: " + _daysback);

        
        Iterator It = map_ac_tags.keySet().iterator();
        while (It.hasNext() && (i <= nObj)) {
            String sName = (String) It.next();
            String sType = (String) map_ac_tags.get(sName);

            p(i + " TAG " + sName + " " + sType);
            
            res.append("{\n");
            res.append("\"name\": \"" + sName + "\",\n");                       
            res.append("\"type\": \"" + sType + "\"\n");

            i++;
            if (It.hasNext() && i <= nObj) {
                res.append("},\n");                                            
            } else {
                res.append("}\n");                                                                    
            }
            p("JSON " + sName + " " + sType);  
        }
        

        clear_counters();        
        if (_daysback.length() < 1) _daysback = "0";
        int ndaysback = 0;
        try {
            ndaysback = Integer.parseInt(_daysback);
        } catch (Exception e) {
            e.printStackTrace();
        }

        HashMap<String, String> occurences_names = new HashMap<String, String>();
        SortableValueMap<String, Integer> occurences_hashes = new SortableValueMap<String, Integer>();
        SliceRange sliceRange = new SliceRange();
        SlicePredicate predicate = new SlicePredicate();
        String sLast = "";
        boolean bShowNext = true;
        String sLocalIP = _host;
        boolean _writeLog = false;
        int nTokens = 1;
        boolean wQuotes = false;
        
        String sDebug = get_objects_sorted(w, 
                occurences, 
                occurences_hashes, 
                occurences_names, 
                sliceRange, 
                _numobj, 
                _filetype, 
                _key, 
                predicate, 
                sLast, 
                bShowNext, 
                _clientIP, 
                sLocalIP, 
                _writeLog, 
                _daysback, 
                nTokens, 
                wQuotes, 
                _user,
                true);

        p("map_ac_tags = " + map_ac_tags.size());
        p("occurences_hashes = " + occurences_hashes.size());
        p("i = " + i);
        p("nObj = " + nObj);
        
        if (map_ac_tags.size() > 0 && occurences_hashes.size() > 0 && i <= nObj) res.append(",");            

//        It = map_ac_idx.keySet().iterator();
//        while (It.hasNext()) {
//            String sName = (String) It.next();
//            String sType = (String) map_ac_idx.get(sName);
//
//            p(i + " FILE " + sName + " " + sType);
//            
//            if (sType.equals("filename")) {
//                for(String sData: Bind.findSecondaryKeys(mm2r, sName.toLowerCase())){
//                    
//                    String[] lineArray = sData.split(",");            
//                    String sNamer = lineArray[0];
//                    String sTime = lineArray[1];
//
//                    p("value for key2 " + sName + ": " + sNamer + ":" + sTime); 
//                    
//                    increment_counter_ac(sNamer, sName, ndaysback, _filetype, Long.parseLong(sTime));                    
//                }
//            }
//        }
        
        occurences_hashes.sortByValue();

                
        //firsttime = true;
        //nCurrent = 0;
        It = occurences_hashes.keySet().iterator();
                            
        //It = map_ac_idx.keySet().iterator();
        while (It.hasNext() && (i <= nObj)) {
            String sMD5 = (String) It.next();
            String sName = get_row_attribute("Keyspace1b","Standard1", sMD5, "name", null);
            
            //String sName = (String) It.next();
            
            //String sName = occurences_names.get(sName2);
            
            //String sType = (String) map_ac_idx.get(sName.toLowerCase());
            String sType = "filename";

            p(i + " FILENAME " + sName + " " + sType);
            
            res.append("{\n");
            res.append("\"name\": \"" + sName + "\",\n");
            res.append("\"nickname\": \"" + sMD5 + "\",\n");
                                   
            if (sType.equals("filename")) {
                
                p("TYPE FILENAME!!!");
                //sURLPack sURLpack = new sURLPack();    
                         
                //boolean bFound = false;
                //for(String sData: Bind.findSecondaryKeys(mm2r, sName.toLowerCase())){
                    //bFound = true;
                    //String sNamer = sData.substring(0,sData.indexOf(","));
                    //p("sData: '" + sData + "'");
                    //String[] lineArray = sData.split(",");            
                    //String sNamer = lineArray[0];
                    //String sTime = lineArray[1];
                    
                    String sNamer = sMD5;
                    String sTime = String.valueOf(occurences.get(sNamer));

                    p("value for key2 " + sName + ": " + sNamer + ":" + sTime); 
                    
                    //long stime = occurences.get(sNamer);                    
                    //p("stime = " + stime);                    
                    
                    //increment_counter_ac(sNamer, sName, ndaysback, _filetype, Long.parseLong(sTime));

                    //boolean _cloudhosted = false;
                    //sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, false, false);                    
                    //String sView = lf.read_view_link(sNamer, sName, _key, _host, _port, _cloudhosted, _clientIP, true);                                    
                    String sView = "";
                    String sRemote = "";
                    if (_cloudhosted) {
                        _host = "ec2-54-242-171-20.compute-1.amazonaws.com";
                        _port = "80";
                        sURLPack sURLpack = new sURLPack();
                        sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false, ""); 

                        //sOpenURL = sURLpack.sOpenURL;     
                        //sFolderURL = sURLpack.sFolderURL;
                        String sViewURL = sURLpack.sViewURL;
                        p("**cloud*********** viewurl = " + sViewURL);

                        //String sFileNameOri = sFileName;
                        Boolean bHaveServerLink = false;

                        //String sViewURL2 = gen_view_link2(_host, _port, sViewURL, bHaveServerLink, _cloudhosted, sNamer, sURLpack, sFileNameOri, _key, nImgRatio, sLocalIP);
                        
                        if (sViewURL != null) {
                            int nPos = sViewURL.indexOf(".");
                            int nPos2 = sViewURL.indexOf("/") + 1;                                    
                            String sRedirLink = "cassvault/" + sViewURL.substring(nPos2,nPos);
                            sView = "http://" + _host + ":" + _port + "/" + sRedirLink;
                        }
                        
                    } else {
                        sView = "http://" + _host + ":" + _port + "/cass/getfile.fn?sNamer=" + sNamer;                   
                        sRemote = "http://" + _host + ":" + _port + "/cass/openfile.fn?sNamer=" + sNamer;                                            
                    }
 
                    //String sRemote = read_open_link(sNamer, sName, _key, _host, _port, _cloudhosted,_clientIP);                
                    //String sRemote = sURLpack.sOpenURL;

                    File fh64= new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".alt64");
                    if(fh64.exists()){
                        p("Thumbnail ALT64 exists" + fh64.getAbsolutePath());
                        FileInputStream is=new FileInputStream(fh64);
                        ByteArrayOutputStream out=new ByteArrayOutputStream();
                        byte[] buf =new byte[1024*1024];
                        try {
                             int n;

                             while ((n = is.read(buf)) > 0) {
                                 out.write(buf, 0, n);
                             }


                         } finally {
                              is.close();

                         }          
                        res.append("\"file_thumbnail\": \"" + out.toString() + "\",\n");      
                        is.close();
                        out.close();
                    } else {
                        p("Thumbnail ALT64 NOT exists" + fh64.getAbsolutePath());
                       String srcPic = "";
                       //Si existe en pic armo el base64
                       File fhpic= new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
                       if (fhpic.exists()) {
                            p("Thumbnail JPG exists" + fhpic.getAbsolutePath());
                            FileInputStream is=new FileInputStream(fhpic);
                            ByteArrayOutputStream out=new ByteArrayOutputStream();
                            FileWriter writer=new FileWriter(fh64);

                            byte[] buf =new byte[1024*1024];
                            try {
                                 int n;

                                 while ((n = is.read(buf)) > 0) {
                                     out.write(buf, 0, n);
                                 }
                                 srcPic = Base64.encodeToString(out.toByteArray(), false);
                                 writer.write(srcPic.toCharArray());
                             } finally {
                                  is.close();
                                  writer.close();
                                  out.close();
                                  buf = null;  //clear buffer
                             }          
                             //srcPic="data:image/jpg;base64,"+ srcPic;
                             p("Vista Tile:Se genera imagen base64");
                             res.append("\"file_thumbnail\": \"" + srcPic + "\",\n");                           
                       } else {
                            p("Thumbnail JPG NOT exists" + fhpic.getAbsolutePath());
                       }                                               
                    }
                    //res.append("\"file_ip\": \"" + sURLpack.sViewURLJSON_IP + "\",\n");
                    //res.append("\"file_port\": \"" + sURLpack.sViewURLJSON_Port + "\",\n");
                    //res.append("\"file_path\": \"" + sURLpack.sViewURLJSON_Path + "\",\n");
                    //res.append("\"file_ext\": \"" + sURLpack.sViewURLJSON_Extension + "\",\n");   
                    
                    Date d = new Date(Long.parseLong(sTime));
                    SimpleDateFormat sdff = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");                    
                    String sDateFile = sdff.format(d);
                    
                    String sDate = "";
                    String sSongTitle = "";
                    String sSongArtist = "";
                    
                    String sAttr =  occurences_attr_r.get(sNamer);                       
                    try {
                        if (sAttr!= null && sAttr.length() > 0) {
                              String delimiters = ",";
                              StringTokenizer st = new StringTokenizer(sAttr, delimiters, true);

                              if (st.countTokens() == 5) {
                                 sDate = st.nextToken();    //  date 
                                 st.nextToken();            //  ,
                                 sSongTitle = st.nextToken();  //  title
                                 st.nextToken();            //  ,
                                 sSongArtist = st.nextToken();   //  artist                                     
                              } else {
                                 sDate = st.nextToken();                                     
                              }
                              //p(sDate + " " + sHeight + " " + sWidth);
                        }                             
                    } catch (Exception e) {
                        p("*** WARNING: Exception parsing: " + sNamer + " = '" + sAttr + "'");
                        e.printStackTrace();
                    }        
                    
                    sSongTitle = sSongTitle.replace("\\", " ");
                    if (Cass7Funcs.is_music(sName)) {
                        if (sSongTitle.length() > 0){
                            String encodedURL = URLEncoder.encode(sSongTitle, "UTF-8");
//                            encodedURL = encodedURL.replaceAll("\\+", "%20");
                            res.append("\"song_title\": \"" + encodedURL + "\",\n");
                        }                    
                        if (sSongArtist.length() > 0){
                            String encodedURL = URLEncoder.encode(sSongArtist, "UTF-8");
//                            encodedURL = encodedURL.replaceAll("\\+", "%20");
                            res.append("\"song_artist\": \"" + encodedURL + "\",\n");
                        }          
                    }

                    res.append("\"file_date\": \"" + sDateFile + "\",\n");
                    res.append("\"file_path\": \"" + sView + "\",\n");
                    res.append("\"file_remote\": \"" + sRemote + "\",\n");
                    String ext =  sName.substring(sName.lastIndexOf(".")+1, sName.length()).toLowerCase();
                    res.append("\"file_ext\": \"" + ext + "\",\n");      
                    
                    if(ext.equalsIgnoreCase("mov") || ext.equalsIgnoreCase("mpg") || ext.equalsIgnoreCase("mmv")
                            || ext.equalsIgnoreCase("mp4")){ //if video
                        String videolink = getMediaURL(sNamer, "video", _awshosted);
                        if(videolink != null){
                            res.append("\"video_url\": \"" +  videolink + "\",\n");
                        }
                    }
                    if(ext.equalsIgnoreCase("mp3") || ext.equalsIgnoreCase("m4a")
                            || ext.equalsIgnoreCase("m4p") || ext.equalsIgnoreCase("wav")){ //if video
                        String audiolink = getMediaURL(sNamer, "audio", _awshosted);
                        if(audiolink != null){
                            res.append("\"audio_url\": \"" +  audiolink + "\",\n");
                        }
                    }
                    
                    
                //}
                
                //handle case where filename is a hex file (md5)                
//                if (!bFound) {
//                    p("Sname len: " + sName.length());
//                    if (sName.length() == 36) {
//                        sNamer = sName.substring(0,32);                        
//                        if (isHex(sNamer)) {                            
//                            sAttr = get_row_attributes(keyspace, "Standard1", sNamer, "title", "artist");                                                        
//                            p(sAttr);
//                            if (Cass7Funcs.is_music(sName) && sAttr.length() > 1) {
//                                String[] lineArray = sAttr.split(",");                                  
//                                sSongTitle = "";
//                                sSongArtist = "";
//                                try {
//                                    sSongTitle = lineArray[0];
//                                    sSongArtist = lineArray[1];
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                }                           
//                                if (sSongTitle.length() > 0) res.append("\"song_title\": \"" + sSongTitle+ "\",\n");  
//                                if (sSongArtist.length() > 0) res.append("\"song_artist\": \"" + sSongArtist+ "\",\n");                                                                 
//                            }                            
//                            
//                            String datestr = get_row_attribute("Keyspace1b","Standard1",sNamer, "date_modified", null);
//                            res.append("\"file_date\": \"" + datestr + "\",\n");
//                            res.append("\"file_ext\": \"" + sName.substring(sName.lastIndexOf(".")+1, sName.length()).toLowerCase() + "\",\n");
//                            
//                            sView = "http://" + _host + ":" + _port + "/cass/getfile.fn?sNamer=" + sNamer;
//                            res.append("\"file_path\": \"" + sView + "\",\n");
//
//                        }
//                    }
//                }
            }

            res.append("\"type\": \"" + sType + "\"\n");

            i++;
            if (It.hasNext() && (i <= nObj)) {
                res.append("},\n");                                            
            } else {
                res.append("}\n");                                                                    
            }
            p(i + ". JSON " + sName + " " + sType);  
        }
        
        res.append("],\n");
        
        res.append("\"objFound\": [\n");
        res.append("{\n");
        int nOffice = nOfficeDoc + nOfficePdf + nOfficePpt + nOfficeXls;
        
        int nCurrentView = 0;
        if (_filetype.equalsIgnoreCase(".all")) nCurrentView = nTotal;
        if (_filetype.equalsIgnoreCase(".photo")) nCurrentView = nPhoto;
        if (_filetype.equalsIgnoreCase(".music")) nCurrentView = nMusic;
        if (_filetype.equalsIgnoreCase(".video")) nCurrentView = nVideo;
        if (_filetype.equalsIgnoreCase(".document")) nCurrentView = nOffice;
        if (_filetype.equalsIgnoreCase(".doc")) nCurrentView = nOfficeDoc;
        if (_filetype.equalsIgnoreCase(".xls")) nCurrentView = nOfficeXls;
        if (_filetype.equalsIgnoreCase(".ppt")) nCurrentView = nOfficePpt;
        if (_filetype.equalsIgnoreCase(".pdf")) nCurrentView = nOfficePdf;

        //if (_daysback.equals("")) nCurrentView = nTotal;
        if (_daysback.equals("1")) nCurrentView = nPast24h;
        if (_daysback.equals("3")) nCurrentView = nPast3d;
        if (_daysback.equals("7")) nCurrentView = nPast7d;
        if (_daysback.equals("14")) nCurrentView = nPast14d;                
        if (_daysback.equals("30")) nCurrentView = nPast30d;
        if (_daysback.equals("365")) nCurrentView = nPast365d;
                
        res.append("\"nCurrent\": \"" + nCurrentView + "\",\n");

        res.append("\"nTags\": \"" + map_ac_tags.size() + "\",\n");
        res.append("\"nTotal\": \"" + nTotal + "\",\n");
        res.append("\"nPhoto\": \"" + nPhoto + "\",\n");
        res.append("\"nMusic\": \"" + nMusic + "\",\n");
        res.append("\"nVideo\": \"" + nVideo + "\",\n");
        res.append("\"nDocuments\": \"" + nOffice + "\",\n");
        res.append("\"nDoc\": \"" + nOfficeDoc + "\",\n");
        res.append("\"nXls\": \"" + nOfficeXls + "\",\n");
        res.append("\"nPpt\": \"" + nOfficePpt + "\",\n");
        res.append("\"nPdf\": \"" + nOfficePdf + "\"},\n");    
        
        res.append("{\"nPast24h\": \"" + nPast24h + "\",\n");
        res.append("\"nPast3d\": \"" + nPast3d + "\",\n");
        res.append("\"nPast7d\": \"" + nPast7d + "\",\n");
        res.append("\"nPast14d\": \"" + nPast14d + "\",\n");
        res.append("\"nPast30d\": \"" + nPast30d + "\",\n");
        res.append("\"nPast365d\": \"" + nPast365d + "\",\n");
        res.append("\"nAllTime\": \"" + nAllTime + "\"}\n");                
        
        //res.append("\"nTotal\": \"" + nTotal + "\"\n");        
        //res.append("}\n");                                                                    
        res.append("]\n");
        
        res.append("}\n");
        
        //db_mm2r.close();
        db_attr.close();
        
        return res.toString();                  
      } catch (Exception e) {
        e.printStackTrace();
        return "";
      }
}
      
      public String read_row_autocomplete_mem(String w, String _user) {
          try {
            HashMap map_ac = new java.util.HashMap();   
            int i = 0;
            String res = "";

            open_mapdb();
            
            DB db_mm1 = tx_mm1.makeTx();
            NavigableSet<Fun.Tuple2<String,String>> mm1r = db_mm1.getTreeSet("autocomplete"); 
                                                
            //from mapdb  
            for(String s: Fun.filter(mm1r, w)){
                //p("value for key " + w + ": " +s);
                String s2 = replace_chars(s);
                map_ac.put(s2, s2);
            } 
                       
            db_mm1.close();
            
//            //from records.db(memory)
//            int nFound = 0;
//            if (w.length() >= 3) {
//                if (mFilesDatabase == null) {
//                    //String mStorage = "../scrubber/data/records.db";
//                    //mFilesDatabase = loadRecords(mStorage);
//                    loadRecords();
//                    bRecordsLoaded = true;
//                } else {
//                    p("mStorage already loaded.");
//                }                        
//                
//                Iterator It = mFilesDatabase.keySet().iterator();
//                while (It.hasNext() && nFound <= 20) {                     
//                        String sFilePath = (String)It.next();                
//                        String sFilePath2 = URLDecoder.decode(sFilePath, UTF8);                    
//                        sFilePath2 = sFilePath2.replace("\\", "/");
//                        if (sFilePath2.toLowerCase().contains(w.toLowerCase()) && !isBlacklisted(sFilePath2)) {
//                            //p(sFilePath2 + " vs " + w + " YES");
//                            sFilePath2 = sFilePath2.replace("\\","/");
//                            String value2 = sFilePath2.substring(sFilePath2.lastIndexOf("/")+1, sFilePath2.length());
//                            value2 = value2.replace("ñ", "&#241;");
//                            value2 = value2.replace("á", "&#225;");
//                            value2 = value2.replace("é", "&#233;");
//                            value2 = value2.replace("í", "&#237;");
//                            value2 = value2.replace("ó", "&#243;");
//                            value2 = value2.replace("ú", "&#250;");
//
//                            value2 = value2.replace("Ñ", "&#209;");
//                            value2 = value2.replace("Á", "&#193;");
//                            value2 = value2.replace("É", "&#201;");
//                            value2 = value2.replace("Í", "&#205;");
//                            value2 = value2.replace("Ó", "&#211;");
//                            value2 = value2.replace("Ú", "&#218;"); 
//                            map_ac.put(value2, value2); 
//                            nFound++;
//                        }
//                }                                    
//            }                     
            
            //from file            
            BufferedReader br = null;
            
            String sPath = appendage + DB_PATH;
            String _ks = "Standard2";

            w = w.replace(":", "@");
            File fh = new File(sPath + File.separator + _ks + File.separator + w);
            if (fh.exists()) {
                FileReader f = new FileReader(sPath + File.separator + _ks + File.separator + w);                        

                br = new BufferedReader(f);
                String sCurrentLine = "";
                Integer nObj = 25;            

                ShareController sc = ShareController.getInstance();
                while (((sCurrentLine = br.readLine()) != null) && (i < nObj)) {
                        //p(sCurrentLine);
                        String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
                        String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());     
                        p("'" + name + "' , '" + value + "'");
                        //String value2 = value.replace("ó", "\\u00F3");
                        String value2 = value;
                        value2 = value2.replace("ñ", "&#241;");
                        value2 = value2.replace("á", "&#225;");
                        value2 = value2.replace("é", "&#233;");
                        value2 = value2.replace("í", "&#237;");
                        value2 = value2.replace("ó", "&#243;");
                        value2 = value2.replace("ú", "&#250;");

                        value2 = value2.replace("Ñ", "&#209;");
                        value2 = value2.replace("Á", "&#193;");
                        value2 = value2.replace("É", "&#201;");
                        value2 = value2.replace("Í", "&#205;");
                        value2 = value2.replace("Ó", "&#211;");
                        value2 = value2.replace("Ú", "&#218;");                       

                        //p("'" + name + "' , '" + value2 + "'");                        
                        if (isCurrentUserAdmin(_user) || sc.getClusterPermission(_user) != null || 
                            ((sc.allUsers(value) || sc.getPermissionByUser(value, _user) != null ))){

                            map_ac.put(value2, value2);
                        }

                        i++;
                    }                 
            }
           
            res += "Found " + map_ac.size() + " matches.";            
            Iterator bit = map_ac.keySet().iterator();
            int j = 0;
            while (bit.hasNext() && j <=20) {
                String sAdd = (String) bit.next();
                boolean addQuotes = (sAdd.contains(" ")) && (sAdd.charAt(0) != '\"') && (sAdd.charAt(sAdd.length()-1) != '\"');
                String sAddFill = addQuotes ? "&quot;"+sAdd +"&quot;" : sAdd;
                
                res += "<li onClick=\"clearFilters(); clearFiltersVar(); fill('" + sAddFill + "');\">" + sAdd + "</li>";            
                j++;
            }                              
            if (map_ac.size() > 20) {
                res += "<li onClick=\"clearFilters(); clearFiltersVar();fill('" + w + "');\">See All</li>";
            }
            
            if (res.length() == 0) {
                res = "<li>Nothing found.</li>";
            }
            return res;            
          } catch (Exception e) {
              e.printStackTrace(); 
              return "";
          }
            
      }
     
      public String read_row_autocomplete(String w) {
        
        BufferedReader br = null;
        try {
        String res = "";
        
        String sPath = appendage + DB_PATH;
        String _ks = "Standard2";
            
        w = w.replace(":", "@");

        FileReader f = new FileReader(sPath + File.separator + _ks + File.separator + w);            
        br = new BufferedReader(f);

        String sCurrentLine = "";

        Integer nObj = 25;
        
        int i = 0;
        
        HashMap map_ac = new java.util.HashMap();        

        while (((sCurrentLine = br.readLine()) != null) && (i < nObj)) {
            //p(sCurrentLine);
            String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
            String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());     
            p("'" + name + "' , '" + value + "'");
            //String value2 = value.replace("ó", "\\u00F3");
            String value2 = value;
            value2 = value2.replace("ñ", "&#241;");
            value2 = value2.replace("á", "&#225;");
            value2 = value2.replace("é", "&#233;");
            value2 = value2.replace("í", "&#237;");
            value2 = value2.replace("ó", "&#243;");
            value2 = value2.replace("ú", "&#250;");
            
            value2 = value2.replace("Ñ", "&#209;");
            value2 = value2.replace("Á", "&#193;");
            value2 = value2.replace("É", "&#201;");
            value2 = value2.replace("Í", "&#205;");
            value2 = value2.replace("Ó", "&#211;");
            value2 = value2.replace("Ú", "&#218;");                       
            
            p("'" + name + "' , '" + value2 + "'");
            
            map_ac.put(value2, value2);
            
            i++;
        }        
        
        Iterator bit = map_ac.keySet().iterator();
        while (bit.hasNext()) {
            String sAdd = (String) bit.next();
            res += "<li onClick=\"clearFilters(); clearFiltersVar();fill('" + sAdd + "');\">" + sAdd + "</li>";            
        }        
        
        return res;
        
        } catch (Exception e)  {
            e.printStackTrace();
            return "";
        }
    }
      
    public String get_batch_id(String _key, String _cf, String _name) {
        return get_row_attribute("", _cf, _key, _name, null);        
    }
    
    public static  String get_row_attributes (String _ks, String _cf, String _key, String _name, String _name2) {
        
        //p("get_row_attributes:" + " " + _cf + " " + _key + " " + _name + " " + _name2);
        BufferedReader br = null;
        
        try {
            String sPath = appendage + DB_PATH;
            
            String filename = sPath + File.separator + _cf + File.separator + _key;
            File ft = new File(filename);
            if (ft.exists()) {
                FileReader f = new FileReader(sPath + File.separator + _cf + File.separator + _key);            
                br = new BufferedReader(f);

                String sCurrentLine = "";
                String sVal1 = "";
                String sVal2 = "";
                int nFound = 0;
                while (((sCurrentLine = br.readLine()) != null) && nFound < 2) {
                    //p(sCurrentLine);
                    try {
                        String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
                        String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());     
                        //p("'" + name + "' , '" + value + "'");
                        if (name.equals(_name)) {
                            //p("FOUND: " + name + "' , '" + value + "'");
                            sVal1 = value;
                            nFound++;
                        }                
                        if (name.equals(_name2)) {
                            //p("FOUND: " + name + "' , '" + value + "'");
                            sVal2 = value;
                            nFound++;
                        } 
                    } catch (Exception e) {
                        p("ERROR: Parsing file: " + filename + " line: " + sCurrentLine);
                        e.printStackTrace();                       
                    }
                                   
                }                
                br.close();
                f.close();
                return sVal1 + "," + sVal2;                
            } else {
                //file not found
                return "";
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }
        
    public static String get_row_attribute (String _ks, String _cf, String _key, String _name, ArrayList<String> cache) {
        //p("get_row_attribute" + _cf + _key + _name);
        BufferedReader br = null;
        try {
            String sPath = appendage + DB_PATH;
            String filename = sPath + File.separator + _cf + File.separator + _key;
            File ft = new File(filename);
            if (ft.exists()) {
                Iterator<String> it = null;
                FileReader f = null;
                String sCurrentLine = "";
                if (cache != null)
                     it = cache.iterator();
                else {
                    f = new FileReader(sPath + File.separator + _cf + File.separator + _key);            
                    br = new BufferedReader(f);
                }
                while (((cache == null) && (((sCurrentLine = br.readLine()) != null))) || ((cache != null) && (it.hasNext()))) {
                    //p(sCurrentLine);
                    try {
                        if (cache != null)
                            sCurrentLine = it.next();
                        String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
                        String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());     
                        //p("'" + name + "' , '" + value + "'");
                        if (name.equals(_name)) {
                            //p("FOUND: " + name + "' , '" + value + "'");
                            if (cache == null){
                                br.close();
                                f.close();
                            }
                            return value;
                        }                         
                    } catch (Exception e) {
                        p("ERROR: Parsing file: " + filename + " line: " + sCurrentLine);
                        e.printStackTrace();                       
                    }          
                }
                //not found.
                if (cache == null){
                    br.close();
                    f.close();
                }
                return "";                
            } else {
                //file not found
                return "";
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }
    
    public boolean objectAlreadyExist(String strDateModified) {
        return true;        
    }
    public String getValidDateModified(String strDateModified) {
        return strDateModified;
    }
    
    
     public String getSuperColumn(final String _key, 
            final String _sc, 
            int nMode,
            String _password,
            String _clientip, 
            String _viewmode, 
            String _daysBack, 
            String _numCol, 
            String _numObj, 
            String _last,
            String _filetype) {

         BufferedReader br = null;

            try {
                String sPath = appendage + DB_PATH;
                String _cf = "Super2";                      
                String sCurrentLine = "";

                String filename = sPath + File.separator + _cf + File.separator + _sc + File.separator + _key;
                
                File ft = new File(filename);
                //p("filename = " + filename);
                if (ft.exists()) {       
                        FileReader f = new FileReader(filename);            
                        br = new BufferedReader(f);
                        
                        String res = "";

                        while (((sCurrentLine = br.readLine()) != null)) {
                            
                                String colName = sCurrentLine.substring(0,sCurrentLine.lastIndexOf(","));
                                String colValue = sCurrentLine.substring(sCurrentLine.lastIndexOf(",")+1, sCurrentLine.length());   
                                
                                switch (nMode) {
                                    case 1:
                                        //res += "<a target=MAIN href='echoClient5.htm?foo=" + colName + "&pw=" + _password + "'>" + colName + "</a> ";
                                        String sRedirLink = "echoClient5.htm?foo=" + colName + 
                                                "&view=" + _viewmode +
                                                "&ftype=" + _filetype +
                                                "&days=" + _daysBack + 
                                                "&numcol=" + _numCol +
                                                "&numobj=" + _numObj +
                                                "&pw=" + _password;

                                        res += "<td nowrap style='padding-left:5px;'>";
                                        res += "<INPUT TYPE=button value=\"" + colName + "\" id='tag"+colName+_key+"' "+_key+"="+_key+" onclick=\"showLoading(); golink('" + sRedirLink + "','" + colName + "')\" " + 
                                                "style=\"color:#000;background-color:lightyellow;font-size:100%\"/>";
                                        res += "<INPUT TYPE='submit' style=\"color:#000;background-color:lightyellow;\" id='tagDel"+colName+_key+"' "+_key+"="+_key+" value='x' onclick='getElementById(\"tag\").value=\"" + 
                                                colName + "\";getElementById(\"DeleteTag\").value=\"" + _key + "\";' />";
                                        res += "</td>";

                                        break;
                                    case 2:
                                        int npos =  colName.indexOf(":");
                                        p("npos: " + npos);
                                        int nlen = colName.length() - npos;
                                        p("length: " + nlen);
                                        String sUUID = colName.substring(0,npos);
                                        p(sUUID);
                                        String sAdd = "";
                                        String sIP = "";
                                        String sPort = "";
                                        
                                        sIP = get_row_attribute("Keyspace1b","NodeInfo",sUUID, "ipaddress", null);
                                        sPort = get_row_attribute("Keyspace1b","NodeInfo",sUUID, "port", null);

                                        if (isUnix(sUUID)) {
                                            sAdd = "/";
                                        }
                                        String sPathDec = sAdd + colName.substring(npos + 1, colName.length());
                                        int lastdot = colName.lastIndexOf(".");
                                        String sExt = colName.substring(lastdot, colName.length()-1).toLowerCase();
                                        p("sPathDec = '" + sPathDec + "'");
                                        //else {
                                        //    res += colName + "<sp><a href='http://" + sIP + ":8080/" + sPathEnc + sExt + "' target='_blank'>view</a><br>";
                                        //}
                                        String sPathEnc = Base64.encodeToString(sPathDec.getBytes(), false);
                                        p("sPathEnd = '" + sPathEnc + "'");

                                        boolean bAvail = isNodeAvailable(sIP, sPort);
                                        boolean bShowplay = true;

                                        String sAnchor = "<a href='http://" + sIP + ":" + sPort + "/" + sPathEnc + sExt + "'>view</a>";
                                        String sAnchor2 = "";
                                        String sAnchor3 = "";
                                        try {
                                            //InetAddress clientIP = InetAddress.getLocalHost();
                                            InetAddress clientIP = NetUtils.getLocalAddressNonLoopback2(); //getLocalAddress();
                                            String LocalIP = clientIP.getHostAddress();

                                            p("Client IP = '" + _clientip + "' ");
                                            p("Local IP = '" + LocalIP + "' ");
                                            p("sIP = '" + sIP + "'");

                                            //if server and file coincide, also allow user to open file "locally" on the server.

                                            String sViewText = "";
                                            String sIP_link = "";
                                            String sRemote = "";
                                            if (_clientip.equals(sIP)) {
                                                sViewText = "viewlocal";
                                                sIP_link = _clientip;
                                                sRemote = "0";
                                            } else {
                                                sViewText = "viewRemote";
                                                sIP_link = sIP;
                                                sRemote = "1";
                                            }
                                            p("sIP_link = '" + sIP_link + "'");

                                            //URI _uri = new URI (sPathDec);
                                            //p("_uri = '" + _uri.toString() + "' ");
                                            //p("_uri2 = '" + _uri.toASCIIString() + "' ");
                                            //p("_uri3 = '" + _uri.toURL() + "' ");

                                            String _uri = URLEncoder.encode(sPathDec, "UTF-8"); 
                                            p("_uri = '" + _uri + "' ");

                                            //_uri = _uri.replaceAll(":","%3A");
                                            //_uri = _uri.replaceAll("/","%2F");
                                            //_uri = _uri.replaceAll(".", "%2E");
                                            //_uri = _uri.replaceAll("\\", "%5C");

                                            sAnchor2 = "<a href='http://" + sIP_link + ":" + sPort + "/cass/redir.php?foo=" + _uri + "&rem=" +sRemote + "'>" + sViewText + "</a>";

                                            if (_clientip.equals(sIP)) {
                                                String sFolder = "";
                                                String delimiters2 = "/";
                                                StringTokenizer st2 = new StringTokenizer(sPathDec.trim(),delimiters2,true);
                                                //for (int i=0; i <= st2.countTokens() + 1 ;i++) {
                                                //   sFolder += st2.nextToken();
                                                //}
                                                int i = st2.countTokens() - 2;
                                                int x = 0;
                                                while (st2.hasMoreTokens()) {
                                                    String sTmp = st2.nextToken();
                                                    if (x < i) {
                                                        sFolder += sTmp;
                                                        x++;
                                                    }
                                                }



                                                String _urif = URLEncoder.encode(sFolder, "UTF-8"); 
                                                p("_urif = '" + _urif + "' ");

                                                sAnchor3 = "<a href='http://" + _clientip + ":" + sPort + "/cass/redir.php?foo=" + _urif + "'>Open local folder</a>";
                                            }
                                        } catch (Exception e) {
                                            p("[Exception] There was an exception when trying to find the local IP.");
                                            p(e.getMessage());
                                        }


                                        if (colValue.equals("DELETED") || !bAvail) {
                                            sAnchor = "";
                                            //sAnchor2 = "";
                                            bShowplay = false;
                                        }
                                        res += "<br>";
                                        res += colName + "<sp>" + "[" + colValue + "]" + "<sp>" + sAnchor + "   " + sAnchor2 + " " + sAnchor3 + "<br>";
                                        res += "sIP = '" + sIP + "'";
                                        res += "sPort = '" + sPort + "'";
                                        String sAvail = "UNAVAILABLE";
                                        if (bAvail) sAvail = "AVAILABLE";
                                        res += "Node '" + sUUID + "' is currently " + sAvail + "<br>";

                                        if (bShowplay) {
                                            if (sPathDec.contains("mp3")) {
                                                res += colName + "<audio id=\"audio\" src =\"http://" + sIP + ":" + sPort + "/" + sPathEnc + sExt + "\" preload=\"none\" controls></audio>";
                                            } 
                                            if (sPathDec.contains("mp4")) {
                                                res += colName + "<video controls autobuffer>" ;
                                                res += "<source src=\"" + "http://" + sIP + ":" + sPort + "/" + sPathEnc + sExt + "\" type='video/mp4; codecs=\"avc1.42E01E, mp4a.40.2\"'" + "></source></video>";
                                            } 
                                        }

                                        break;
                                      case 3:
                                        npos =  colName.indexOf(":");
                                        sUUID = colName.substring(0,npos);
                                        lastdot = colName.lastIndexOf(".");
                                        sExt = colName.substring(lastdot, colName.length()-1).toLowerCase();
                                        
                                        
//                                        sAdd = "";
//                                        if (isUnix(sUUID)) {
//                                            sAdd = "/";
//                                        }
                                        sPathDec = colName.substring(npos + 1, colName.length());  
                                        sPathEnc = Base64.encodeToString(sPathDec.getBytes(), false);
                                        
                                        sIP = get_row_attribute("Keyspace1b","NodeInfo",sUUID, "ipaddress", null);
                                        sPort = get_row_attribute("Keyspace1b","NodeInfo",sUUID, "port", null);

                                        //InetAddress clientIP = getLocalAddress();
                                        InetAddress clientIP = NetUtils.getLocalAddressNonLoopback2();
                                        String LocalIP = clientIP.getHostAddress();

                                        String sres = "";
                                        if (sIP.equals(LocalIP)) {
                                            sres = sPathEnc;
                                        } else {
                                            sres = "http://" + sIP + ":" + sPort + "/" + sPathEnc + sExt;
                                        }
                                        return sres;                                      
                                }                            
                        }
                        res += "</tr></table>";
                        return res;
                } else {
                    p("file not found" + filename);
                    return "";
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                return "ERROR";
            }
             
        }

    
    public String getHashes(final String _key, 
            final String superColumnName, 
            int nMode,
            String _password,
            String _clientip, 
            String _viewmode, 
            String _daysBack, 
            String _numCol, 
            String _numObj, 
            String _last,
            String _filetype) {
        
            BufferedReader br = null;

            try {
                String sPath = appendage + DB_PATH;
                String _cf = "Super2";
                String _sc = "hashesm";       
                String sCurrentLine = "";

                String filename = sPath + File.separator + _cf + File.separator + _sc + File.separator + _key;
                File ft = new File(filename);
                //p("filename = " + filename);
                if (ft.exists()) {       
                        FileReader f = new FileReader(filename);            
                        br = new BufferedReader(f);
                        
                        String res = "";
                       // res += "<table css=css style='width: 100%'><tr>";

                        while (((sCurrentLine = br.readLine()) != null)) {
                            //p(sCurrentLine);
                            
                            String colName = sCurrentLine.substring(0,sCurrentLine.indexOf(","));
                            
                            String foo = colName;
                            if (colName.trim().indexOf(" ") > 0) {
                                foo = "\"" + colName + "\"";
                                foo = URLEncoder.encode(foo, "UTF-8");
                            }
                            
                            String sRedirLink = "echoClient5.htm?foo=" + foo + 
                                    "&view=" + _viewmode +
                                    "&ftype=" + _filetype +
                                    "&days=" + _daysBack + 
                                    "&numcol=" + _numCol +
                                    "&numobj=" + _numObj +
                                    "&pw=" + _password;
                            
                            //res += "<td nowrap style='padding-left:5px;'>";
                            res += "<div style='margin-left: 0.3em; float: left; margin-top: 0.2em'><INPUT TYPE=button value=\"" + colName + "\" id='tag"+colName+_key+"' "+_key+"="+_key+" onclick=\"showLoading(); golink('" + sRedirLink + "','" + colName + "')\" " + 
                                    "style=\"border-radius:5px;color:#000;height: 25px;background-color:#FCF0AD;font-size:100%\"/>";
                            res += "<INPUT TYPE='submit' style=\"border-radius:5px;height: 25px;color:#000;background-color:#FCF0AD;\" id='tagDel"+colName+_key+"' "+_key+"="+_key+" value='x' onclick='getElementById(\"tag\").value=\"" + 
                                    colName + "\";getElementById(\"DeleteTag\").value=\"" + _key + "\";' /></div>";
                           // res += "</td>";                            
                        }       
                       // res += "</tr></table> ";
                        return res.replace("hidden@", "hidden:");
                } else {
                    return "";
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "ERROR";
            }                        
    }

    
    public int insertSuperColumn(String _keyspace, String _cf, String _key, String _sc, String _name, String _value) {
        
        bNewQuery = true;
        String sPath = appendage + DB_PATH;
        BufferedReader br = null;
        BufferedWriter bw = null;
        String sCurrentLine = "";
        
        try {
            String sReg = _name + "," + _value + "\n";
            Boolean bAlreadyExists = false;
            
            File fh = new File(sPath + File.separator + _cf + File.separator + _sc + File.separator + _key);
            if (fh.exists()) {
                //Check if register already exists
                FileReader r = new FileReader(sPath + File.separator + _cf + File.separator + _sc + File.separator + _key);
                br = new BufferedReader(r);

                while ((sCurrentLine = br.readLine()) != null)
                {
                    if (sCurrentLine.trim().equals(sReg.trim())) {
                        bAlreadyExists = true;
                        break;
                    }
                }
                br.close();                
            }                    
            
            //If register not exists, insert it
            if (!bAlreadyExists) {
                FileWriter f = new FileWriter(sPath + File.separator + _cf + File.separator + _sc + File.separator + _key, true);            
                bw = new BufferedWriter(f);

                bw.write(sReg);
                bw.close();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            try {
                if (br != null)br.close();
                if (bw != null)bw.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        
        return 0;
    }
    
    public int edit_SuperColumn(String _keyspace, String _cf, String _key, String _sc, String _namex, String _value) {
        
        bNewQuery = true;
        String sPath = appendage + DB_PATH;
        BufferedReader br = null;
        BufferedWriter bw = null;
        String sFilename = "";
        
        try {
            sFilename = sPath + File.separator + _cf + File.separator + _sc + File.separator + _key;
            FileReader r = new FileReader(sFilename);
            br = new BufferedReader(r);
            String sCurrentLine = "";
            String sContent = "";

            String _name_dec = "";
            try {
                _name_dec = URLDecoder.decode(_namex, "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
                _name_dec = _namex;
            }
            _name_dec = _name_dec.replace("\\", "/");
            
            //_name = URLDecoder.decode(_name, "UTF-8").replace("\\", "/");
            
            String sReg = _name_dec + "," + _value + "\n";

            while ((sCurrentLine = br.readLine()) != null)
            {
                if (!sCurrentLine.trim().equals(sReg.trim())) {
                    if (sCurrentLine.toLowerCase().startsWith(_name_dec.toLowerCase() + ",")) {
                        sContent += sReg;
                    } else{
                        sContent += sCurrentLine + "\n";
                    }
                }
            }
            br.close();
            
            if ("".equals(sContent)) {
                File f = new File(sPath + File.separator + _cf + File.separator + _sc + File.separator + _key);
                f.delete();
            } else {
                FileWriter f = new FileWriter(sPath + File.separator + _cf + File.separator + _sc + File.separator + _key, false);
                bw = new BufferedWriter(f);
                bw.write(sContent);
                bw.close();
            }
            
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            p("File not found exception: " + sFilename);
            return 0;
        }
        catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            try {
                if (br != null)br.close();
                if (bw != null)bw.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }       
        }
        
        return 0;
    }
    
    public int delete_SuperColumn(String _keyspace, String _cf, String _key, String _sc, String _name, String _value) {
        //since we deleted an object, mark as new query
        bNewQuery = true;
        
        String sPath = appendage + DB_PATH;
        BufferedReader br = null;
        BufferedWriter bw = null;
        
        try {
            FileReader r = new FileReader(sPath + File.separator + _cf + File.separator + _sc + File.separator + _key);
            br = new BufferedReader(r);
            String sCurrentLine = "";
            String sContent = "";
            
            String sReg = _name + "," + _value;
            
            while ((sCurrentLine = br.readLine()) != null)
            {
                if (!sCurrentLine.equals(sReg)) {
                    sContent += sCurrentLine + "\n";
                }
            }
            br.close();
            
            if ("".equals(sContent)) {
                File f = new File(sPath + File.separator + _cf + File.separator + _sc + File.separator + _key);
                f.delete();
            } else {
                FileWriter f = new FileWriter(sPath + File.separator + _cf + File.separator + _sc + File.separator + _key, false);
                bw = new BufferedWriter(f);
                bw.write(sContent);
                bw.close();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            try {
                if (br != null)br.close();
                if (bw != null)bw.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }       
        }
        
        return 0;
    }
    
    public int insert_column(String _ks, String _cf, String _key, String _name, String _value, boolean _doappend) {
     
        Appendage app = new Appendage();
        appendage = app.getAppendage();
        appendageRW = app.getAppendageRW();
        p("Appendage set to : " + appendage);
        p("DB_PATH set to   : " + DB_PATH);
        p("_cf              : " + _cf);
        p("_key             : " + _key);
        p("name             : " + _name);

        bNewQuery = true;

        String sPath =  appendage + DB_PATH;

        BufferedReader br = null;
        BufferedWriter bw = null;
        String sCurrentLine = "";
        
        try {
            
            String sReg = _name + "," + _value + "\n";
            Boolean bAlreadyExists = false;
            
            File fh = new File(sPath + File.separator + _cf + File.separator + _key);
            if (fh.exists()) {
                //Check if register already exists
                FileReader r = new FileReader(sPath + File.separator + _cf + File.separator + _key);
                br = new BufferedReader(r);

                while ((sCurrentLine = br.readLine()) != null)
                {
                    if (sCurrentLine.trim().equals(sReg.trim())) {
                        bAlreadyExists = true;
                        break;
                    }
                }
                br.close();                
            } else {
                pw("WARNING [LocalFuncs.insert_column]: File does not exist: " + fh.getAbsolutePath());
            }                    
            
            //If register not exists, insert it
            if (!bAlreadyExists || !_doappend) {
                FileWriter f = new FileWriter(sPath + File.separator + _cf + File.separator + _key, _doappend);
                bw = new BufferedWriter(f);

                bw.write(sReg);
                bw.close();                       
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            try {
                if (br != null)br.close();
                if (bw != null)bw.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        
        return 0;
    }
    
    public int edit_column(String _ks, String _cf, String _key, String _name, String _value) {
     
        bNewQuery = true;
        String sPath = appendage + DB_PATH;
        BufferedReader br = null;
        BufferedWriter bw = null;
        
        try {
            FileReader r = new FileReader(sPath + File.separator + _cf + File.separator + _key);
            br = new BufferedReader(r);
            String sCurrentLine = "";
            String sContent = "";
            
            while ((sCurrentLine = br.readLine()) != null)
            {
                if (sCurrentLine.startsWith(_name + ",")) {
                    sContent += _name + "," + _value + "\n";
                } else{
                    sContent += sCurrentLine + "\n";
                }
            }
            br.close();
            
            if ("".equals(sContent)) {
                File f = new File(sPath + File.separator + _cf + File.separator + _key);
                f.delete();
            } else {
                FileWriter f = new FileWriter(sPath + File.separator + _cf + File.separator + _key, false);
                bw = new BufferedWriter(f);
                bw.write(sContent);
                bw.close();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            try {
                if (br != null)br.close();
                if (bw != null)bw.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }       
        }
        
        return 0;
    }
    
    public int delete_column(String _ks, String _cf, String _key, String _name, String _value) {
     
        //since we deleted an object, mark as new query
        bNewQuery = true;

        String sPath = appendage + DB_PATH;
        BufferedReader br = null;
        BufferedWriter bw = null;
        
        try {
            FileReader r = new FileReader(sPath + File.separator + _cf + File.separator + _key);
            br = new BufferedReader(r);
            String sCurrentLine = "";
            String sContent = "";
            
            String sReg = _name + "," + _value;
            
            while ((sCurrentLine = br.readLine()) != null)
            {
                if ("".equals(_value)) {
                    if (!sCurrentLine.startsWith(_name + ",")) {
                        sContent += sCurrentLine + "\n";
                    }
                } else{
                p(sCurrentLine + " vs " + sReg);
                if (!sCurrentLine.equals(sReg)) {
                    sContent += sCurrentLine + "\n";
                    }
                } 
            }

            br.close();
            
            if ("".equals(sContent)) {
                File f = new File(sPath + File.separator + _cf + File.separator + _key);
                f.delete();
            } else {
                FileWriter f = new FileWriter(sPath + File.separator + _cf + File.separator + _key, false);
                bw = new BufferedWriter(f);
                bw.write(sContent);
                bw.close();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            try {
                if (br != null)br.close();
                if (bw != null)bw.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }       
        }
        
        return 0;
    }

    public String insert_column_auto_complete(String _keyspace, String _key, String _name, String _value) {
        bNewQuery = true;
        
        int res = -1;
        
        String sValue = get_row_attribute("", "Standard2", _key, _name, null).toLowerCase(); //this is to know if it was already inserted
        
        if ("".equals(sValue)) {
            res = insert_column(_keyspace, "Standard2", _key, _name, _value, true);
        }
        
        return String.valueOf(res);
    }
    
    public String delete_column_auto_complete(String _keyspace, String _key, String _name, String _value) {
        //since we deleted an object, mark as new query
        bNewQuery = true;

        int res = delete_column(_keyspace, "Standard2", _key, _name, _value);
        return String.valueOf(res);
    }
    
    public boolean existsHashes(String _ks, String _cf, String _key) {
        File f = new File(DB_PATH + File.separator + _cf + File.separator + _key);
        
        return !f.exists();
    }
    
    public String insert_hashtag(String _keyspace, String _key, String _hashkey, boolean _autocomplete, String _datemodified) {
        try {
            bNewQuery = true;

            _hashkey = _hashkey.replace(":","@");

            String delimiters = "+ ";
            
            String _filename = get_row_attribute("", "Standard1", _key, "name", null).toLowerCase();
            String sAdder = _key + "," + _filename;
            int res_code = 0;
            
            boolean bLongString = false;

            if (_hashkey.startsWith("\"") && _hashkey.endsWith("\"")) {

                _hashkey = _hashkey.replace("\"","");
                
                bLongString = true;
                
                res_code += insertSuperColumn(_keyspace, "Super2",_key, "hashesm", _hashkey, _hashkey);
                res_code += insert_column(_keyspace,"Standard1",_hashkey, _datemodified, sAdder, true );
                
                if (_autocomplete) {
                    for (int idx=1; idx<_hashkey.length();idx++) {
                        for (int idx2=0; idx2<=_hashkey.length()-1;idx2++) {
                            if ((idx + idx2) <= _hashkey.length()) {
                                String str = _hashkey.substring(idx2, idx + idx2);
                                insert_column_auto_complete("Keyspace1b", str, _hashkey, _hashkey);
                                insert_column(_keyspace,"Standard1", str, _datemodified, sAdder, true);
                            }
                        }
                    }
                }
            }
                        

            StringTokenizer st = new StringTokenizer(_hashkey.trim(), delimiters, false);
            while (st.hasMoreTokens()) {
                String w = st.nextToken();
                
                if (!bLongString) {
                    //insert manual hashes
                    res_code = insertSuperColumn(_keyspace, "Super2",_key, "hashesm", w, w);
                    p("RET: '" + res_code);                
                }

                insert_column_auto_complete("Keyspace1b", w, w, w);
                
                res_code += insert_column(_keyspace,"Standard1",w, _datemodified, sAdder, true );
                p("RET2: '" + res_code);

                if (_autocomplete) {
                    for (int idx=1; idx<w.length();idx++) {
                        for (int idx2=0; idx2<=w.length()-1;idx2++) {
                            if ((idx + idx2) <= w.length()) {
                                String str = w.substring(idx2, idx + idx2);
                                
                                //add substring in autocomplete table (e.g. key: 'P' name/value: 'Pictures')
                                insert_column_auto_complete("Keyspace1b", str, w, w);
                                //also associate hash to subtring (e.g. key:'P'  name/value:file1_hash
                                insert_column(_keyspace,"Standard1", str, _datemodified, sAdder, true);
                            }
                        }
                    }
                }
            }

            return String.valueOf(res_code);

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
        
    }
          
    
    long get_time_millis(Integer _daysback) {
        if (currentTime == 0)
            currentTime = System.currentTimeMillis();    
        return currentTime - ((long)86400000*_daysback);
    }
    
    protected void logPerfIndex(String timestamp, String jar, String routine, String subroutine, String key, String time, boolean writeLog) {
        if (writeLog){
            synchronized (logPerfIndex) {
                logPerfIndex.println(timestamp + "," + jar + "," + routine + "," + subroutine + "," + key + "," + time);
                logPerfIndex.flush();
            }
        }
    }
    
    protected void logPerfIndexWriteHeader(String path) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path));
            if (br.readLine() == null) {
                logPerfIndex.println("timestamp,jar,routine,subroutine,key,time");
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    protected void logErrors(String timestamp, String error, boolean writeLog){
        if (writeLog){
            synchronized (logIndexError) {
                logIndexError.println(timestamp+": "+error);
                logIndexError.flush();
            }
        }
    }
    
    boolean isBroadQuery(String w) {
        if (
                w.equals(".all") ||
                w.equals(".photo") ||
                w.equals(".music") ||
                w.equals(".video") ||
                w.equals(".document") ||
                w.equals(".ppt") ||
                w.equals(".doc") ||
                w.equals(".xls") ||
                w.equals(".pdf")
                ) return true;
        else return false;
    }   
    
    public String get_objects_sorted(String w, 
            SortableValueMapLong<String, Long> occurences,
            SortableValueMap<String, Integer> occurences_hash, 
            HashMap<String, String> occurences_names,
            SliceRange sliceRange, 
            String _numobj,
            String _filetype,
            String _key,
            SlicePredicate predicate,
            String _last,
            Boolean bShowNext,
            String _clientIP,
            String _serverIP,
            boolean _writeLog,
            String _daysback,
            int nToken,
            boolean _wQuotes,
            String _user,
            boolean _bOrderAsc) {
        
        //SortableValueMapLong<String, Long> occurences = new SortableValueMapLong<String, Long>();
        
        Stopwatch timer = null;   
        timer = new Stopwatch().start(); 
        
        String sDebug = "";
        long Time[] = new long[10];        
        
        p("----- begin get_object_sorted ----");
                       
       
        String sFile = appendageRW + "/" + "../rtserver/testdb";
        String sDBName = readDoc(appendageRW + "/" + "../rtserver/dbname.txt");
        if (sDBName.length() > 0) {
            sFile = sDBName.trim();
        }
        p("DBName: '" + sFile + "'");
       
//        open_mapdb();        
//        if (tx_cp == null) {
//            while (tx_cp == null) {
//                log("WARNING: tx_cp = NULL. Retry open DB.", 2);                
//                open_mapdb();                
//            }
//        }

        DB db_cp_r = null;
        boolean bLoop = true;
        int nTry = 0;
        ConcurrentNavigableMap<String,String> occurences_copies_r = null;
        while (bLoop && nTry < 5) {
            try {                
                open_mapdb();
                if (tx_cp != null) {
                    db_cp_r = tx_cp.makeTx();
                    if (db_cp_r != null) {
                        occurences_copies_r = db_cp_r.getTreeMap("numberofcopies");
                        bLoop = false;
                    } else {
                        nTry++;
                        log("WARNING: NULL db_cp_r = NULL", 2);                                    
                        Thread.sleep(500);                        
                    }
                } else {
                    nTry++;
                    log("WARNING: NULL tx_cp_r = NULL", 2);                                    
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                nTry++;
                log("WARNING: Exception during makeTx()", 2);                
                e.printStackTrace();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }            
        }
        
        //DB db2 = DBMaker.newFileDB(new File(sFile)).closeOnJvmShutdown().make();                            
        
        //ConcurrentNavigableMap<String,String> occurences_attr_r = db2.getTreeMap("attributes");
        //NavigableSet<Fun.Tuple2<String,String>> mm1r = db2.getTreeSet("autocomplete"); 
        //NavigableSet<Fun.Tuple2<String,String>> mm2r = db2.getTreeSet("md5"); 

        //BufferedReader br = null;
        Scanner scanner = null;        
        StringWriter sWriter = null;
        
        String timestampPerf = (new Date()).toGMTString();
        
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdff = new SimpleDateFormat("yyyyMMdd");
        String sDateFile = sdff.format(ts_start);
        //Initialize perf log
            if (_writeLog) {                
                try {
                    String sFilename = appendageRW + LOG_NAME_PERFORMANCE_INDEX_PATH + sDateFile + "_performance_index_server_debug.csv";

                    logPerfIndex = new PrintStream(new BufferedOutputStream(
                                  new FileOutputStream(sFilename, true)));   
                    logPerfIndexWriteHeader(sFilename);                    
                } catch (Exception e) {
                    sWriter = new StringWriter();
                    e.printStackTrace(new PrintWriter(sWriter));
                    logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
                    _writeLog = false;
                }
            }
            
        timer.stop();
        logPerfIndex(timestampPerf,"casss_server","get_objects_sorted", "Initialize","", timer.getElapsedTime()+"", _writeLog);
        sDebug += " Initialize=" + String.valueOf(timer.getElapsedTime()) + "ms ";

        int nDBPut = 0;

        try {
            
            
            timer = new Stopwatch().start();   
            
            

            currentTime = System.currentTimeMillis();
            
            //open error log
            String sFilenameErrors = appendageRW + LOG_NAME_PERFORMANCE_INDEX_PATH + sDateFile + "_performance_index_server_errors.txt";
            logIndexError = new PrintStream(new BufferedOutputStream(
                          new FileOutputStream(sFilenameErrors, true)));   
            logErrors(timestampPerf,"Error log started", true);
            
            
            
            
            
            String sPath = appendage + DB_PATH;
            String _ks = "Standard1";
            
            w = w.replace(":", "@");
            
            String sCurrentLine = "";
            
            Integer nObj = Integer.parseInt(_numobj);
            
            if (!bNodesLoaded) {
                p("Loading Nodes");
                int r = loadNodes("nodes", "NodeInfo");                            
                bNodesLoaded = true;
            }
            
            String delim = " ";
            StringTokenizer st1 = new StringTokenizer(_key.trim(), delim, true);
            
            boolean bCompoundQuery = false;
            String sLastToken = "";
            int countTokens = 0;
            
            if ((st1.countTokens() > 1) && (!_wQuotes)) {
                bCompoundQuery = true;
                while (st1.hasMoreTokens()) {
                    sLastToken = st1.nextToken();
                    if (!sLastToken.equals(delim))
                        countTokens++;
                }
            }
            
            p("bOrderAsc      = " + _bOrderAsc);
            p("bCompoundQuery = " + bCompoundQuery);
            p("sLastToken     = " + sLastToken);

            int nIDXFiles = getNumberofIDXFiles();
            p("nIDXFiles : " + nIDXFiles);
            if (nIDXFiles > 0) bLoadingCopies = true;               			   
            
            p("bloadingcopies1    = " + bLoadingCopies);      
            p("previous query    = " + previous_query);
            p("previous filetype = " + previous_filetype);
            p("previous timerange = " + previous_timerange);
            p("w                 = " + w);
            p("filetype = " + _filetype);
            p("_last = " + _last);
            p("key               = " + _key);
            p("newquery1         = " + bNewQuery);            
            p("bloadingcopies2    = " + bLoadingCopies);      
            p("bcachevalid   = " + bCacheValid); 
            
            

            sn = false;
            nSize = 0;
              
            
            p("clear counters");
            clear_counters();
            bNewQuery = true;
            

            p("NEW QUERY!!!");
            previous_query = w + _key;
            previous_filetype = _filetype;
            previous_timerange = _last;

            bNodesLoaded = false;                   

            if (_key.replace(":", "@").startsWith(w)) {
                nTotal = 0;
                nPhoto = 0;
                nMusic = 0;
                nVideo = 0;
                nOfficeDoc = 0;
                nOfficeXls = 0;
                nOfficePpt = 0;
                nOfficePdf = 0;
                nAllTime = 0;
                nPast24h = 0;
                nPast3d = 0;
                nPast7d = 0;
                nPast14d = 0;
                nPast30d = 0;
                nPast365d = 0;                        
                //occurences_names.clear();
            }
            //p("clear occurences");
            occurences.clear();

            //p("clear copies");
            //occurences_copies.clear();

            timer.stop();
            logPerfIndex(timestampPerf,"casss_server","get_objects_sorted", "LoadNodes","", timer.getElapsedTime()+"", _writeLog);
            sDebug += " LoadNodes=" + String.valueOf(timer.getElapsedTime()) + "ms ";

                                      
//                    p("-------------[1]");
//                    ArrayList<String> l1 = null;
//                    l1 = (ArrayList<String>)occurences_index.get(w);
//                    if (l1 == null) {
//                        p("no index match . using Filer.");
//                        //no file match case
//                        //int nres = readDoc_mem(w, occurences_names, _filetype);   
//                        Filer = readDoc(sPath + File.separator + _ks + File.separator + w);                        
//                    } else {
//                        p("index match case!!!");
//                        int nres = readIndex_mem(w,l1, occurences_names);
//                        //Thread.sleep(5000);
//                    }
//                    timerAux.stop();
//                    p("-------------[2]: took" + timerAux.getElapsedTime() + "ms");

            Stopwatch timerAux = new Stopwatch().start();                    

            if (!isBroadQuery(w)) {
                int nFiles = getNumberOfFilesInTag(w);
                if (nFiles > 0) {
                    p("Skipping MapDB. There are " + nFiles + " files in this tag.");                    
                } else {
                    timerAux.start();
                    Filer = "";
                    int nres = readDoc_mapdb(w, occurences_names, _filetype, occurences);       
                    timerAux.stop();
                    p("-------------[1]: mapdb took" + timerAux.getElapsedTime() + "ms . res = " + nres);
                    sDebug += "mapdb=" + String.valueOf(timerAux.getElapsedTime()) + "ms ";
                    timerAux.start();
                    p("cache size:" + occurences_cache_all.size());
                    p("cache ts:" + occurences_cache_all_ts);
                    if (isBroadQuery(w) && occurences_cache_all.size() > 0) {
                        long now = System.currentTimeMillis();
                        p("cache diff:" + (now - occurences_cache_all_ts) );
                        bCacheValid = false;
                        if (((now - occurences_cache_all_ts) < 60000) && bCacheValid) {   ///TTL for cache is 30s
                            p("Cache: HIT");
                            occurences.putAll(occurences_cache_all);
                            bNewQuery = false;
                        } else {
                            if (bCacheValid) {
                                p("Cache: OLD");
                            } else {
                                p("Cache: INVALID");
                            }
                        }                              
                    } else { 
                        p("Cache: EMPTY");
                    }                                    
                }
            }           

            if (bNewQuery) {
                String wbNewQuery = w;
                if (isBroadQuery(w))
                    wbNewQuery = ".all";

              p("reading filer...: " + occurences.size());
              Filer = readDoc(sPath + File.separator + _ks + File.separator + wbNewQuery);   
              p("len: " + Filer.length());
            }

            timerAux.stop();
            p("-------------[2]: filer took" + timerAux.getElapsedTime() + "ms");
            sDebug += "filer=" + String.valueOf(timerAux.getElapsedTime()) + "ms ";                                                  
            
            
            //p("newquery2         = " + bNewQuery);            
            p("FILE SIZE " + Filer.length());
                                              
            scanner = new Scanner(Filer);            
            if (bNewQuery) {
                
                timer = new Stopwatch().start();
                int nRecord = 0;
                while (scanner.hasNext()) {
                    sCurrentLine = scanner.nextLine();
                    nRecord++;
                    //p(nRecord + "'" + sCurrentLine + "'");

                    String delimiters = ",";
                    StringTokenizer st = new StringTokenizer(sCurrentLine, delimiters, true);

                    String name = "";
                    String value = "";
                    String valfilename = "";
                    boolean bOK = false;
                    try {
                        name = st.nextToken();
                        st.nextToken();
                        value = st.nextToken();
                        st.nextToken();
                        valfilename = st.nextToken();
                        bOK = true;
                    } catch (Exception e) {
                        sWriter = new StringWriter();
                        e.printStackTrace(new PrintWriter(sWriter));
                        logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
                        p("WARNING: There was an parsing the line:" + sCurrentLine);                        
                    }
                    
// NOTE: LA FUNCION GETLOCALADDRESS MATA EL PERF, Y SI LO DEJAS ACA CORRE PARA CADA ELEMENTO!!!     YA LO PASAMOS POR PARAMETRO              
//                    //Get number of copies
//                    InetAddress clientIP = getLocalAddress();
//                    String sLocalIP = "127.0.0.1";
//                    if (clientIP != null) {
//                        sLocalIP = clientIP.getHostAddress();;                            
//                    }

// NOTE: ESTO MATA EL PERF, YA QUE CALCULA NUMERO DE COPIAS PARA CADA OBJETO DEL FILE. LO METI MAS ABAJO.
                    
                    //String sCopyInfo = getNumberofCopies("paths", value, _clientIP, _clientIP);                    
                    //occurences_copies.put(value, sCopyInfo);                    

// NOTE: ESTO MATA EL PERF, YA QUE CALCULA NUMERO DE COPIAS PARA CADA OBJETO DEL FILE. LO METI MAS ABAJO.
                    
                    //String sHidden = "";
                    //sHidden = get_row_attribute(keyspace, "Standard1", value, "hidden");
                    
                    //Check if not Hidden
                    if (bOK) {
                        if (!occurences.containsKey(value)) {

                            DateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
                            if (name.length() > 27) {
                                //format = new SimpleDateFormat("EEE MMM dd hh:mm:ss zzz yyyy");           
                                name = get_row_attribute(keyspace, "Standard1", value, "date_modified", null);
                                p("***FIXED DATE MODIFIED: '" + name + "'");
                            }
                            long regTime = 0;
                            try {
                                Date date = format.parse(name);
                                regTime = date.getTime();
                            } catch (ParseException e) {
                                sWriter = new StringWriter();
                                e.printStackTrace(new PrintWriter(sWriter));
                                logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
                            }
                            
                            synchronized(occurences) {
                                //p("adding : " + value + " " + regTime);
                                occurences.put(value, regTime);
                            }
                            
                            String _filename = "";
                            if (valfilename.contains("/")) {                        
                                delimiters = "/";
                                st = new StringTokenizer(valfilename, delimiters, true);
                                while (st.hasMoreTokens()) {                        
                                    String token = st.nextToken();
                                    if (token.length() > 1) {
                                        _filename = token;
                                    }
                                }
                            } else {
                                _filename = valfilename;
                            }
                            
                            synchronized(occurences_names) {
                                occurences_names.put(value, _filename);
                            }
                        }
                    }
                    
                }

                timer.stop();
                logPerfIndex(timestampPerf,"casss_server","get_objects_sorted", "While","", timer.getElapsedTime()+"", _writeLog);
                sDebug += " while=" + String.valueOf(timer.getElapsedTime()) + "ms ";
                    
                timer = new Stopwatch().start();
                
                synchronized (occurences) {
                    if (_bOrderAsc) {
                        p("***** SORT ORDER: ascending");
                        occurences.sortByValue();                                              
                    } else {
                        p("***** SORT ORDER: descending");
                        occurences.sortByValueDesc();
                    }
                }
                
                timer.stop();
                logPerfIndex(timestampPerf,"casss_server","get_objects_sorted", "SortByValue","", timer.getElapsedTime()+"", _writeLog);
                sDebug += " SortByValue=" + String.valueOf(timer.getElapsedTime()) + "ms ";

            } else {
                p("*** Skipping file load, same query.");
            } 
           
            timer = new Stopwatch().start();
            
            //store occurences cache for next time
            if (bNewQuery) {
                if (w.equals(".all")) {
                    occurences_cache_all.clear();
                    occurences_cache_all.putAll(occurences);
                    occurences_cache_all_ts = System.currentTimeMillis();
                    bCacheValid = true;
                }
            }
            
            timer.stop();
            logPerfIndex(timestampPerf,"casss_server","get_objects_sorted", "IsBNewQuery","", timer.getElapsedTime()+"", _writeLog);
            sDebug += " IsBNewQuery=" + String.valueOf(timer.getElapsedTime()) + "ms ";

            timer = new Stopwatch().start();

            String sLast = "";
            if (_last.length() > 0) {
                sLast = URLDecoder.decode(_last, "UTF-8");
            }
            
            p("occurences.size():" + occurences.size());
            
            timer.stop();
            logPerfIndex(timestampPerf,"casss_server","get_objects_sorted", "BeforeFor","", timer.getElapsedTime()+"", _writeLog);
            sDebug += " beforefor=" + String.valueOf(timer.getElapsedTime()) + "ms ";
   
            boolean bSkipFirst = true;
            ArrayList<String> cache = null;
            
            int nDBPutTotal = 0;
            synchronized (occurences) {
                
            
            long[] arrayFor = {0,0,0,0}; 
             
            ShareController sc = ShareController.getInstance();
            //Show tag if not hidden
            if (isCurrentUserAdmin(_user) || sc.getClusterPermission(_user) != null ||
                sc.allUsers(_key) || sc.getPermissionByUser(_key, _user) != null )
            for (String key : occurences.keySet()) {
                
                timer = new Stopwatch().start();
                cache = null;
                boolean bSkip = false;
                
                int ndaysback = 0;
                try {
                    ndaysback = Integer.parseInt(_daysback);
                } catch (Exception e) {
                    //unable to parse integer
                }             
                
                //increment_counter(key, occurences_names.get(key), ndaysback, _filetype);                    
                
                
                //String sCopyInfo = null;
                //synchronized(occurences_copies_r) {
                //    sCopyInfo = occurences_copies_r.get(key);
                //}
                String sCopyInfo = "1,1,1";
               
                boolean decrementIncrementCounter = false;
                if (!bCompoundQuery) {
                    decrementIncrementCounter = true;
                } else {      
                    if (nToken == countTokens) {
                        decrementIncrementCounter = true;
                    }
                }             
                      
                if (sCopyInfo == null) {
                        sCopyInfo = "1,1,1";
                        //sCopyInfo = getNumberofCopies("paths", key, _clientIP, _serverIP);
                        p("WARNING: missing getNumberofCopies() for key: " + key + " #copies:" + sCopyInfo); 
//                        synchronized(occurences_copies) {                            
//                            //occurences_copies.add(Fun.t2(key, sCopyInfo));  
//                            occurences_copies.put(key, sCopyInfo);
//                            nDBPut++;
//                      }
                } else {
                    //p("Already have it!!!: key:" + key + " #copies: " + sCopyInfo); 
                    if (sCopyInfo.equals("0,0,0")) {
                        sCopyInfo = getNumberofCopies("paths", key, _serverIP, _serverIP, false);
                        if (sCopyInfo != null && !sCopyInfo.equals("0,0,0")) {
                            synchronized(occurences_copies_r) {                            
                                occurences_copies_r.put(key, sCopyInfo);
                                nDBPut++;
                            }  
                        }
                        p("Already2: " + key + " " + sCopyInfo);
                    }
                }
                boolean bSkipHidden = false;
                if ((sCopyInfo.equals("0,0,0")) && (decrementIncrementCounter)) {
                        //skip, no active local copies
                        //p("decrement due to copyinfo.");
                        bSkip = true;
                        bSkipHidden = true;
                        //decrement_counter(occurences_names, key, _filetype, ndaysback);
                } else {
                    //p("Object OK[copycheck-nlc]" + key);
                }                             
                
                timer.stop();
                arrayFor[0] += timer.getElapsedTime();
                
                timer = new Stopwatch().start();
//                if (!bSkip) {
//                    ShareController sc = ShareController.getInstance();
//                    //Show tag if not hidden
//                    if (!isCurrentUserAdmin(_user) && sc.getClusterPermission(_user) == null &&
//                            !sc.allUsers(_key) && sc.getPermissionByUser(_key, _user) == null ){
//                        bSkip = true;
//                    }
//                   
//                }
                
                
                
                if (!bSkip) {
                    //cache = fileToArrayList(sPath + File.separator + "Standard1" + File.separator + key);
                    //String sHidden = get_row_attribute(keyspace, "Standard1", key, "hidden", cache);
                    String sHiddenTagObj = occurences_hidden.get(key);
                    //p("sHiddenTagObj = " + sHiddenTagObj);
                    
                    if ((!bLoadingCopies && sHiddenTagObj != null && sHiddenTagObj.length() > 0) &&(decrementIncrementCounter)) {
                        if (_key.contains("hidden:")) {
                            String sHiddenTag = _key.substring(_key.indexOf(":")+1, w.length());
                            p("'" + sHiddenTag + "' vs '" + sHiddenTagObj + "'");
                            if (sHiddenTag.equals(sHiddenTagObj)) {
                                p("hidden tag match");
                            } else {
                                p("hidden tag no match");
                                bSkipHidden = true;   
                                bSkip = true;
                                //decrement_counter(occurences_names, key, _filetype, ndaysback);    
                            }
                        } else {
                            p("Skipping. Object is hidden: " + key);
                            //decrement_counter(occurences_names, key, _filetype, ndaysback); 
                            bSkipHidden = true;    
                            bSkip = true;
                        }
                    } else {
                        //p("Object OK[hiddencheck]" + key);
                        if (bLoadingCopies) {
                            //p("Loading Copies. Assuming object is not hidden.");
                        }
                        
                    }
                    
                } else {
                     
                }

                timer.stop();
                arrayFor[1] += timer.getElapsedTime();
                
                timer = new Stopwatch().start();
                
                if (!bSkip && sLast.length() > 0) {
                    

                    DateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
                    long futureTime = 0;
                    try {
                        Date date = format.parse(sLast);
                        futureTime = date.getTime();
                    } catch (ParseException e) {
                        sWriter = new StringWriter();
                        e.printStackTrace(new PrintWriter(sWriter));
                        logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
                    }

                    if (_bOrderAsc) {
                        if (occurences.get(key) > futureTime) {
                            bSkip = false;
                        } else {
                            if (occurences.get(key) < futureTime) {
                                bSkip = true;                            
                            } else {                           
                                if (bSkipFirst) {
                                    if (!sLastMD5.equals(key)) {
                                        bSkip = true;
                                    } else {
                                        bSkip = true;
                                        bSkipFirst = false;
                                    }
                                } else {
                                    bSkip = false;
                                }
                            }
                        }                          
                    } else {
                        if (occurences.get(key) < futureTime) {
                            bSkip = false;
                        } else {
                            if (occurences.get(key) > futureTime) {
                                bSkip = true;                            
                            } else {                           
                                if (bSkipFirst) {
                                    if (!sLastMD5.equals(key)) {
                                        bSkip = true;
                                    } else {
                                        bSkip = true;
                                        bSkipFirst = false;
                                    }
                                } else {
                                    bSkip = false;
                                }
                            }
                        }                                                  
                    }
                } else {
                    //p("Object OK[datecheck]" + key);                    
                }
                
                timer.stop();
                arrayFor[2] += timer.getElapsedTime();
                
                timer = new Stopwatch().start();
                
                if (!bSkip) {
                    
                    //p("adding #: " + occurences_hash.size());
                    //Thread.sleep(100);
           
                    boolean bAdd = false;
                    if (bCompoundQuery) {
                        //N token case - always add, since we will count outside of this routine
                        bAdd = true;
                    } else {
                        //1 token case - only add if we haven't exceeded the limit
                        if (occurences_hash.size() < nObj) {
                            bAdd = true;
                        }                        
                    }
                    
                    if (bAdd) {
                        String sfilename = occurences_names.get(key);      
                        read_row_hash(key, occurences_hash, occurences_names, _filetype, cache, sfilename);
                    } else {
                        //p("skip add: " + key);
                    }
                    
                    /*if (!bCompoundQuery) {
                        increment_counter(key, occurences_names.get(key), ndaysback, _filetype);
                        nSize++;
                    } else {                            
                        int was =0;
                        Object Got;
                        if ((Got = occurences_hash.get(key)) != null) {
                            was = (((Integer) Got).intValue());
                        }
                        if (was > 1) {
                            increment_counter(key, occurences_names.get(key), ndaysback, _filetype);
                            nSize++;
                        }                            
                    }*/
                                
                    //p("hashsize: " + occurences_hash.size() + " nobj: " + nObj);
                    if (!bShowNext && occurences_hash.size() == nObj) {
                        DateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
                        slast = sdf.format(occurences.get(key));
                        sLastMD5 = key;
                        bShowNext = true;  
                        sn = true;
                        //break;
                    }
                    
                } //if bskip     

                
                if ((decrementIncrementCounter) && (!bSkipHidden)) {
                    if (!bCompoundQuery){
                        increment_counter(key, occurences_names.get(key), ndaysback, _filetype, occurences);
                        nSize++;
                    } else {
                        int was = 0;
                        Object Got;
                        if ((Got = occurences_hash.get(key)) != null) {
                            was = (Integer) Got;
                        }
                        if (was == countTokens) {
                            increment_counter(key, occurences_names.get(key), ndaysback, _filetype, occurences);
                            nSize++;
                            sn = true;
                        }
                    }
                } else {
                    //p("Skipped increment counter: " + key);
                }              
                
                timer.stop();
                arrayFor[3] += timer.getElapsedTime();
            } //for     
            
            long sumaFor = arrayFor[0] + arrayFor[1] + arrayFor[2] + arrayFor[3];
            logPerfIndex(timestampPerf,"casss_server","get_objects_sorted", "For", "", sumaFor+"", _writeLog);
            
            sDebug += "For-CopyCheck=" + arrayFor[0] + "ms ";
            sDebug += "For-HiddenCheck=" + arrayFor[1] + "ms ";
            sDebug += "For-Datacheck=" + arrayFor[2] + "ms ";
            sDebug += "For-ReadRowHash=" + arrayFor[3] + "ms ";

        } //synchronized
            
        if (!bCompoundQuery) nSize = occurences_hash.size();
            
            timer = new Stopwatch().start();

            bNewQuery = false;
                                   
            timer.stop();
            logPerfIndex(timestampPerf,"casss_server","get_objects_sorted", "AfterFor", "", timer.getElapsedTime()+"", _writeLog);
            sDebug += "afterfor=" + String.valueOf(timer.getElapsedTime()) + "ms ";
                                                    
        } catch (Exception e) {
            e.printStackTrace();
            sWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(sWriter));
            logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
            return "ERROR";            
        } finally {
            try {
                if (nDBPut > 0) {
                    p("----- commit db ----");
                    db_cp_r.commit();                    
                }
                p("----- close db ----");
                db_cp_r.close();
                db_cp_r = null;
                //if (br != null)br.close();
                if (scanner != null)scanner.close();
                p("----- end get_object_sorted ----");
                return sDebug;
            } catch (Exception ex) {
                sWriter = new StringWriter();
                ex.printStackTrace(new PrintWriter(sWriter));
                logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
                return "ERROR";
            }
        }
        
    }
    
    public int get_objects(String w, 
            SortableValueMap<String, Integer> occurences_hash, 
            HashMap<String, String> occurences_names, 
            SliceRange sliceRange, 
            String _numobj,
            String _filetype,
            String _key,
            SlicePredicate predicate,
            String _last,
            Boolean bShowNext) {
        
        BufferedReader br = null;
        
        try {
            
            String sPath = appendage + DB_PATH;
            String _ks = "Standard1";
            
            w = w.replace(":", "@");
            
            FileReader f = new FileReader(sPath + File.separator + _ks + File.separator + w);            
            br = new BufferedReader(f);

            String sCurrentLine = "";
            String res = "";
            
            Integer nObj = Integer.parseInt(_numobj);
            
            int i = 0;
            
            p("slast = " + _last);
            String sLast = "";
            if (_last.length() > 0) {
                sLast = URLDecoder.decode(_last, "UTF-8");
                p("looking for = " + sLast); 
            } else {               
            }

            boolean Found = false;
            if (sLast.length() > 0) {              
                while (((sCurrentLine = br.readLine()) != null) && !Found) {                    
                    String delimiters = ",";
                    StringTokenizer st = new StringTokenizer(sCurrentLine, delimiters, true);

                    String name = st.nextToken();
                    st.nextToken();
                    String value = st.nextToken();
                    
                    p("'" + name + "' vs '" + sLast + "'");
                    if (name.equals(sLast)) {
                        Found = true;
                    } else {
                        p("skipped " + name + " " + value);
                        //Thread.sleep(100);
                    }                    
                }
            }
            
            while (((sCurrentLine = br.readLine()) != null) && (i < nObj)) {
                //p(sCurrentLine);
                
                String delimiters = ",";
                StringTokenizer st = new StringTokenizer(sCurrentLine, delimiters, true);
                
                String name = st.nextToken();
                st.nextToken();
                String value = st.nextToken();
                st.nextToken();                
                String _filename = st.nextToken();
                
                //String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
                //String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());     
                //p("'" + name + "' , '" + value + "' , '" + "'");
                res += read_row_hash(value, occurences_hash, occurences_names, _filetype, null, _filename);
                slast = name;                
                i++;                    
            }
            if ((sCurrentLine = br.readLine()) != null) {
                bShowNext = true;  
                sn = true;
            }

            return 0; 
                
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            try {
                    if (br != null)br.close();
            } catch (IOException ex) {
                    ex.printStackTrace();
            }       
        }
        
    }
    
    
    public String read_row_hash(String _key, HashMap numbers, HashMap names, String _extension, ArrayList<String> cache, String _filename) {
        String res = "";

        //p("readhow hash filename: " + _filename);
        //p("readhow hash extension: " + _extension);
        if (_filename == null) {
            if (!bLoadingCopies) {
                _filename = get_row_attribute("", "Standard1", _key, "name", cache).toLowerCase();
            } else {
                _filename = "(Loading...)";
            }           
        } else {
            //p("already have it: " + _filename);
        }
        
        boolean bAdd = false;
        if (_extension.equalsIgnoreCase(".all")) {
            bAdd = true;
        }
        if (_extension.equalsIgnoreCase(".photo") && Cass7Funcs.is_photo(_filename)) {
            bAdd = true;
        }
        if (_extension.equalsIgnoreCase(".music") && Cass7Funcs.is_music(_filename)) {
            bAdd = true;
        }
        if (_extension.equalsIgnoreCase(".video") && Cass7Funcs.is_movie(_filename)) {
            bAdd = true;
        }
        if (_extension.equalsIgnoreCase(".document") && Cass7Funcs.is_document(_filename)) {
            bAdd = true;
        }
        if (_extension.equalsIgnoreCase(".doc") && _filename.toLowerCase().contains(".doc")) {
            bAdd = true;
        }
        if (_extension.equalsIgnoreCase(".xls") && _filename.toLowerCase().contains(".xls")) {
            bAdd = true;
        }
        if (_extension.equalsIgnoreCase(".ppt") && _filename.toLowerCase().contains(".ppt")) {
            bAdd = true;
        }
        if (_extension.equalsIgnoreCase(".pdf") && _filename.toLowerCase().contains(".pdf")) {
            bAdd = true;
        }
        
        //p("extension " + _extension + " filename " + _filename + " badd " + bAdd);
        if (bAdd) {
            //p("extension " + _extension + " filename " + _filename + " badd " + bAdd);
            //nSize++;
            int was = 0;
            Object Got;
            if ((Got = numbers.get(_key)) != null) {
                was = (Integer) Got;
            }
            numbers.put(_key, new Integer(was + 1)); 
                
            //p("**** FILENAME:" + _filename);
            names.put(_key, _filename);            
        } else {
            //p("skipping object. " + _filename + " doesn't match extension requested");
        }
        
                            
        return res;
    }
    
    
    public String read_row_info(String _key, String _cf) {
        
        BufferedReader br = null;
        
        try {
            String sPath = appendage + DB_PATH;
            
            String res = "<table>";
            
            String filename = sPath + File.separator + _cf + File.separator + _key;
            File ft = new File(filename);
            if (ft.exists()) {
                FileReader f = new FileReader(sPath + File.separator + _cf + File.separator + _key);            
                br = new BufferedReader(f);

                String sCurrentLine = "";
                while (((sCurrentLine = br.readLine()) != null)) {
                    //p(sCurrentLine);
                    String name = sCurrentLine.substring(0, sCurrentLine.indexOf(","));
                    String value = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length());
                    res += "<tr><td>" + name + "</td><td>" + value + "</td></tr>";
                }
                res += "</table>";                
                return res;                
            } else {
                p("File not found: " + filename);
                return "ERROR";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
        
    }
    
    int readIndex_mem(String w, ArrayList<String> l1, HashMap<String, String> occurences_names, SortableValueMapLong<String, Long> occurences) {
        
        try {
            
            p("mfilesdatabase size: " + mFilesDatabase.size());
            bRecordsLoaded = false;
            if (!bRecordsLoaded) {
                loadRecords();
                bRecordsLoaded = true;                
            } else {
                p("Already loaded");
            }
            
            Iterator It = l1.iterator();
            while (It.hasNext()) {
                String key = (String)It.next();
                      
                //p("key: " + key);
                //get the date of modification
                String sData = "";
//                for(String s: Bind.findSecondaryKeys(occurences_attr, key)){                        
//                        sData = s;
//                }
                //sData = occurences_attr.get(key);
                //p("sData:" + sData);
                
                DateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
                Date date = format.parse(sData.substring(0,sData.indexOf(",")));
                long regTime = date.getTime();
                                                
                //p("adding" + key + "," + regTime);
                occurences.put(key, regTime);
                                
                String filename = occurences_files.get(key);
                
                occurences_names.put(key, filename);

            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    int readDoc_mapdb(String w, HashMap<String, String> occurences_names, String _filetype, SortableValueMapLong<String, Long> occurences) {

        try {
            
            DB db_mm2 = tx_mm2.makeTx();
            
            NavigableSet<Fun.Tuple2<String,String>> mm2 = db_mm2.getTreeSet("md5");
            
            //from mapdb  
            for(String s: Fun.filter(mm2, w)){
                //p("md5 for key " + w + ": " +s);                
                String delimiters = ",";
                StringTokenizer st = new StringTokenizer(s, delimiters, true);

                String sMD5 = st.nextToken();
                st.nextToken();
                String sDate = st.nextToken();
                st.nextToken();
                String sName = st.nextToken();

                synchronized(occurences) {
                    occurences.put(sMD5, Long.parseLong(sDate));
                }
                synchronized(occurences_names) {
                    occurences_names.put(sMD5, sName);
                }        
            }     
            db_mm2.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            return 0;            
        }
         
    }
    
    int readDoc_mem(String w, HashMap<String, String> occurences_names, String _filetype, SortableValueMapLong<String, Long> occurences) {
        try {
                     
            HashMap<String, FileDatabaseEntry> mFilesDatabase1 = new HashMap<String, FileDatabaseEntry>();
            HashMap<String, FileDatabaseEntry> mFilesDatabase2 = new HashMap<String, FileDatabaseEntry>();
            
            HashMap map_ac = new java.util.HashMap();   
            int i = 0;
            //String res = "";
            StringBuilder res = new StringBuilder();
                         
            p("mfilesdatabase size: " + mFilesDatabase.size());
            bRecordsLoaded = false;
            if (!bRecordsLoaded) {
                loadRecords();
                bRecordsLoaded = true;                
            } else {
                p("Already loaded");
            }
            
            if (!bNodesLoaded) {
                            p("Loading Blacklist");
                            loadBlacklistMap();
                            loadBlacklistContainsMap();
                            p("Loading Nodes");
                            int r = loadNodes("nodes", "NodeInfo");                            
                            bNodesLoaded = true;    
            } else {
                //p("Skipping loadNodes(). Already loaded.");
            }                
                            
            p("mfilesdatabase size2: " + mFilesDatabase.size());
            
            Iterator It = mFilesDatabase.keySet().iterator();
            while (It.hasNext()) {                     
                    String sFilePath = (String)It.next();                
                    String sFilePath2 = URLDecoder.decode(sFilePath, UTF8);                    
                    sFilePath2 = sFilePath2.replace("\\","/");
                    String sFileName = sFilePath2.substring(sFilePath2.lastIndexOf("/")+1,sFilePath2.length());
                    boolean bAdd = false;
                    
                    if (isBlacklisted(sFilePath2)) {
                        bAdd = false;
                    } else {
                        //if it's a generic query
                        if (_filetype.equals(".all") && w.equals(".all")) {                            
                            bAdd = true;
                        } else {
                            if (_filetype.equals(".photo") && Cass7Funcs.is_photo(sFileName)) {
                                bAdd = true;
                            } else {
                                if (_filetype.equals(".movie") && Cass7Funcs.is_movie(sFileName)) {
                                    bAdd = true;
                                } else {
                                    if (_filetype.equals(".music") && Cass7Funcs.is_music(sFileName)) {
                                        bAdd = true;
                                    } else {
                                        if (_filetype.equals(".document") && Cass7Funcs.is_document(sFileName)) {
                                            bAdd = true;
                                        } else {
                                            if (_filetype.contains(".doc") && sFileName.contains(".doc")) {
                                                bAdd = true;
                                            } else {
                                                if (_filetype.contains(".xls") && sFileName.contains(".xls")) {
                                                    bAdd = true;
                                                } else {
                                                    if (_filetype.contains(".ppt") && sFileName.contains(".ppt")) {
                                                        bAdd = true;
                                                    } else {
                                                        if (_filetype.toLowerCase().contains(".pdf") && sFileName.toLowerCase().contains(".pdf")) {                                                        
                                                            bAdd = true;                                                        
                                                        } else {                                                           
                                                            if (sFilePath2.toLowerCase().contains(w.toLowerCase())) {
                                                                bAdd = true;
                                                            }                                                                                                                                                                                    
                                                        }                                                    
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } 
                        }
                    }                    
                    
                    Object entry = mFilesDatabase.get(sFilePath);
                    FileDatabaseEntry fde = (FileDatabaseEntry)entry;                
                    
                    if (bAdd) {                                                
                        
                        synchronized(occurences) {
                            occurences.put(fde.mMD5, fde.mDateModified);
                        }
                        synchronized(occurences_names) {
                            occurences_names.put(fde.mMD5, sFileName);
                        }
                        //increment_counter_mem(fde.mMD5, sFileName, fde.mDateModified);                                                                    
                    }
            }
            //return res.toString();            
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            //return "";
            return -1;
        }               
    }
    
    public String readDoc(String file) {
                
        BufferedInputStream in = null;
        byte[] buf = new byte[1024*1024];
        ByteBuffer buffer = new ByteBuffer();
        try {
            p("readDoc()");
            File fh = new File(file);
            if (fh.exists()) {
                p("file exists: " + fh.getAbsolutePath());
                in = new BufferedInputStream(new FileInputStream(file));                                
                int len;
                while ((len = in.read(buf)) != -1) {
                    buffer.put(buf, len);
                }
                in.close();                
                return new String(buffer.buffer, 0, buffer.write);               
            } else {
                p("file NOT exists: " + fh.getAbsolutePath());
                return "";
            }
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            return "";
        } catch (IOException ex) {
            ex.printStackTrace();
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }        
    }
    
    class ByteBuffer {

       public byte[] buffer = new byte[256];

       public int write;

       public void put(byte[] buf, int len) {
          ensure(len);
          System.arraycopy(buf, 0, buffer, write, len);
          write += len;
       }

       private void ensure(int amt) {
          int req = write + amt;
          if (buffer.length <= req) {
             byte[] temp = new byte[req * 2];
             System.arraycopy(buffer, 0, temp, 0, write);
             buffer = temp;
          }
       }

    }
    public boolean deleteObject(final String _key, final String superColumnName, String sUUID, String sPath) {

        //since we deleted an object, mark as new query
        bNewQuery = true;
        
        String sPath2 = sPath.replace("\\", "/");
        String sColumn = sUUID + ":" + sPath2 + "/";
        p("marking entry as deleted: key '" + _key + "' sColumn: '" + sColumn +"'");
        
        int ret_code = edit_SuperColumn(keyspace, "Super2", _key, "paths", sColumn, "DELETED");
        if (ret_code == 0) {
            return true;
        } else {
            return false;
        }
    }
    
    public void decrement_counter(HashMap<String, String> occurences_names, String _key, String _filetype, int _daysback, SortableValueMapLong<String, Long> occurences) {
        //if (bNewQuery) {
        
        
            String sfilename = occurences_names.get(_key);
            //p("decrement: " + _key + " " + sfilename);
            
            long regTime = occurences.get(_key);

            if (isTimeRange(regTime, _daysback) && Cass7Funcs.is_photo(sfilename)) {
                nPhoto--;
            }
            if (isTimeRange(regTime, _daysback) && Cass7Funcs.is_music(sfilename)) {
                nMusic--;                        
            }
            if (isTimeRange(regTime, _daysback) &&Cass7Funcs.is_movie(sfilename)) {
                nVideo--;                        
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".doc")) {
                nOfficeDoc--;
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".xls")) {
                nOfficeXls--;                   
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".ppt")) {
                nOfficePpt--;
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".pdf")) {
                nOfficePdf--;
            }

            //update totals
            if (isTimeRange(regTime, _daysback)) nTotal--;           

            //update dates
            if (isFileType(sfilename, _filetype)) nAllTime--;
            
            if (regTime >= get_time_millis(1) && isFileType(sfilename, _filetype)) {
                nPast24h--;
            }                        
            if (regTime >= get_time_millis(3) && isFileType(sfilename, _filetype)) {
                nPast3d--;
            }                       
            if (regTime >= get_time_millis(7) && isFileType(sfilename, _filetype)) {
                nPast7d--;
            }
            if (regTime >= get_time_millis(14) && isFileType(sfilename, _filetype)) {
                nPast14d--;
            }
            if (regTime >= get_time_millis(30) && isFileType(sfilename, _filetype)) {
                nPast30d--;
            }
            if (regTime >= get_time_millis(365) && isFileType(sfilename, _filetype)) {
                nPast365d--;
            }
        //}
    }
    
    void clear_counters() {
        nTotal = 0;
        nPhoto = 0;
        nMusic = 0;
        nVideo = 0;
        nOfficeDoc = 0;
        nOfficeXls = 0;
        nOfficePpt = 0;
        nOfficePdf = 0;
        nAllTime = 0;
        nPast24h = 0;
        nPast3d = 0;
        nPast7d = 0;
        nPast14d = 0;
        nPast30d = 0;
        nPast365d = 0; 
    }
      
    public void increment_counter(String _key, String sfilename, int _daysback, String _filetype, SortableValueMapLong<String, Long> occurences) {
        try {
            //p(_key + " " + sfilename + " " + _daysback + " " + _filetype);
            long regTime = occurences.get(_key);
           
            if (isTimeRange(regTime, _daysback) && Cass7Funcs.is_photo(sfilename)) {
                nPhoto++;
            }
            if (isTimeRange(regTime, _daysback) && Cass7Funcs.is_music(sfilename)) {
                nMusic++;                        
            }
            if (isTimeRange(regTime, _daysback) && Cass7Funcs.is_movie(sfilename)) {
                nVideo++;                        
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".doc")) {
                nOfficeDoc++;
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".xls")) {
                nOfficeXls++;                   
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".ppt")) {
                nOfficePpt++;
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".pdf")) {
                nOfficePdf++;
            }

            //update totals
            if (isTimeRange(regTime, _daysback)) nTotal++;

            //update dates
            if (isFileType(sfilename, _filetype)) nAllTime++;

            if (regTime >= get_time_millis(1) && isFileType(sfilename, _filetype)) {
                nPast24h++;
            }                        
            if (regTime >= get_time_millis(3) && isFileType(sfilename, _filetype)) {
                nPast3d++;
            }                       
            if (regTime >= get_time_millis(7) && isFileType(sfilename, _filetype)) {
                nPast7d++;
            }
            if (regTime >= get_time_millis(14) && isFileType(sfilename, _filetype)) {
                nPast14d++;
            }
            if (regTime >= get_time_millis(30) && isFileType(sfilename, _filetype)) {
                nPast30d++;
            }
            if (regTime >= get_time_millis(365) && isFileType(sfilename, _filetype)) {
                nPast365d++;
            } 
            
        } catch (Exception e) {
            e.printStackTrace();
        }               
    }
    
    public void increment_counter_ac(String _key, String sfilename, int _daysback, String _filetype, long regTime) {
        try {
            //p(_key + " " + sfilename + " " + _daysback + " " + _filetype);
            //long regTime = occurences.get(_key);
           
            if (isTimeRange(regTime, _daysback) && Cass7Funcs.is_photo(sfilename)) {
                nPhoto++;
            }
            if (isTimeRange(regTime, _daysback) && Cass7Funcs.is_music(sfilename)) {
                nMusic++;                        
            }
            if (isTimeRange(regTime, _daysback) && Cass7Funcs.is_movie(sfilename)) {
                nVideo++;                        
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".doc")) {
                nOfficeDoc++;
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".xls")) {
                nOfficeXls++;                   
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".ppt")) {
                nOfficePpt++;
            }
            if (isTimeRange(regTime, _daysback) && sfilename.toLowerCase().contains(".pdf")) {
                nOfficePdf++;
            }

            //update totals
            if (isTimeRange(regTime, _daysback)) nTotal++;

            //update dates
            if (isFileType(sfilename, _filetype)) nAllTime++;

            if (regTime >= get_time_millis(1) && isFileType(sfilename, _filetype)) {
                nPast24h++;
            }                        
            if (regTime >= get_time_millis(3) && isFileType(sfilename, _filetype)) {
                nPast3d++;
            }                       
            if (regTime >= get_time_millis(7) && isFileType(sfilename, _filetype)) {
                nPast7d++;
            }
            if (regTime >= get_time_millis(14) && isFileType(sfilename, _filetype)) {
                nPast14d++;
            }
            if (regTime >= get_time_millis(30) && isFileType(sfilename, _filetype)) {
                nPast30d++;
            }
            if (regTime >= get_time_millis(365) && isFileType(sfilename, _filetype)) {
                nPast365d++;
            } 
            
        } catch (Exception e) {
            e.printStackTrace();
        }               
    }
    
    boolean isTimeRange(long regTime, int _daysback) {
        if (_daysback == 0) {
            return true;
        } else {
            if (regTime >= get_time_millis(_daysback)) {
                return true;
            } else {
                return false;
            }
        }
    }
    
    boolean isFileType(String _filename, String _filetype) {
        if ((Cass7Funcs.is_music(_filename) && _filetype.toLowerCase().equals(".music")) ||
            (Cass7Funcs.is_document(_filename) && _filetype.toLowerCase().equals(".document")) ||
            (Cass7Funcs.is_movie(_filename) && _filetype.toLowerCase().equals(".video")) ||
            (Cass7Funcs.is_photo(_filename) && _filetype.toLowerCase().equals(".photo")) ||
            (_filename.toLowerCase().contains(".doc") && _filetype.toLowerCase().equals(".doc")) ||
            (_filename.toLowerCase().contains(".xls") && _filetype.toLowerCase().equals(".xls")) ||
            (_filename.toLowerCase().contains(".ppt") && _filetype.toLowerCase().equals(".ppt")) ||
            (_filename.toLowerCase().contains(".pdf") && _filetype.toLowerCase().equals(".pdf")) ||
            (_filetype.toLowerCase().equals(".all")) ) {
            return true;
        } else {
            return false;
        }
    }
    
    public boolean syncObject(final String _key, String _UUID, String _Path) {
        
        String _cf = "Super2";
        String _sc = "paths";
        
        BufferedReader br = null;
        
        try {
            FileReader r = new FileReader(DB_PATH + File.separator + _cf + File.separator + _sc + File.separator + _key);

            br = new BufferedReader(r);
            String sCurrentLine;
            
            while ((sCurrentLine = br.readLine()) != null)
            {
                String colName = sCurrentLine.substring(0, sCurrentLine.indexOf("/,"));
                int npos =  colName.indexOf(":");
                String sUUID = colName.substring(0, npos);

                if (_UUID.equals(sUUID)) {
                    String sPath = colName.substring(npos + 1, colName.length());

                    String s1 = "";
                    try {
                        s1 = URLDecoder.decode(sPath, "UTF-8").toLowerCase();
                    } catch (Exception e) {
                        e.printStackTrace();
                        p("Exception decoding s1: '" + sPath + "'");
                        s1 = sPath.toLowerCase();
                    }

                    String s2 = "";
                    try {
                        s2 = URLDecoder.decode(_Path, "UTF-8").toLowerCase();                                                
                    } catch (Exception e) {
                        e.printStackTrace();
                        p("Exception decoding s2: '" + _Path + "'");
                        s2 = _Path.toLowerCase();
                    }

                    int s1ln = s1.length();
                    int s2ln = s2.length();
                    
                    if ((s2ln - s1ln) == 1) {
                        p("fixing mac path before compare...");
                        s1 = "/" + s1;
                    }
                           
                    p("\nComparing: '" + s1 + "' vs '" + s2 + "'");
                    
                    if (s1.equals(s2)) {
                        return true;
                    }
                }
            }
            br.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (br != null)br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }       
        }

        return false;
    }
    
    private ArrayList<String> fileToArrayList(String path){
        BufferedReader br = null;
        ArrayList<String> file = new ArrayList<String>();
        try {
            FileReader f = new FileReader(path);            
            br = new BufferedReader(f);
            String sCurrentLine;
            while (((sCurrentLine = br.readLine()) != null)) {
                try {
                    file.add(sCurrentLine);          
                } catch (Exception e) {
                    e.printStackTrace();                           
                }             
            }
            br.close();
            f.close();
           return file;                
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    } 
    
    int getNumberofIDXFiles() {
        try {
            File tf = new File(".");
            File[] files = tf.listFiles();
            int nIDX = 0;
            for (File f:files) {
                if (f.isFile() && f.getName().contains(".idx")) nIDX++;
            }
            return nIDX;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    public int getNumberOfFilesInTag(String _tag) {
    
        try {
            String sPath = appendage + DB_PATH;
            String _cf = "Standard1";
            BufferedReader br = null;
            String sCurrentLine = "";
            
            String fileName = sPath + File.separator + _cf + File.separator + _tag;
            File fn = new File(fileName);
            if (fn.exists()) {
                
                FileReader f = new FileReader(fn.getPath());            
                br = new BufferedReader(f);
                int i = 0;
                
                while (((sCurrentLine = br.readLine()) != null)) {
                        p(sCurrentLine);
                        i++;
                }                
                return i;                
            } else {
                return -1;
            }
            
        } catch (Exception e) {
            return -1;
        }
        
        
    }
    
    String getTagsForFile(String _md5, String _currentUser) {
       
        try {
            String sTags = "";

            String sPath = appendage + DB_PATH;

            String _cf = "Super2";
            String _sc = "hashesm";

            String fileName = sPath + File.separator + _cf + File.separator + _sc + File.separator + _md5;
            String sCurrentLine = "";

            File file = new File(fileName);
            if (file.exists() && file.isFile()) {
                FileReader f = new FileReader(file.getPath());      
                BufferedReader br = null;
                br = new BufferedReader(f);

                while (((sCurrentLine = br.readLine()) != null)) {
                    String colName = sCurrentLine.substring(0,sCurrentLine.indexOf(","));
                    if (colName.startsWith("hidden@"))
                            colName = colName.replaceFirst("@",":");
                        String colValue = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length()); 
                        ShareController sc = ShareController.getInstance();
                        //Show tag if not hidden
                        if (isCurrentUserAdmin(_currentUser) || (!colName.startsWith("hidden:")  && sc.getClusterPermission(_currentUser) != null) || 
                            (!colName.startsWith("hidden:")  && (sc.allUsers(colName)) || sc.getPermissionByUser(colName, _currentUser) != null )){
                            sTags += colName + ",";
                        }                                
                }
            }


            return sTags;
            
        } catch (Exception e) {
            e.printStackTrace();
            return "ERRROR";
        }
    }
    
    public SortableValueMap<String,Integer> getTags(String _currentUser){
    
        String sPath = appendage + DB_PATH;
        
        String _cf = "Super2";
        String _sc = "hashesm";

        String directoryName = sPath + File.separator + _cf + File.separator + _sc + File.separator;

        BufferedReader br = null;
        String sCurrentLine = "";
        
        SortableValueMap<String,Integer> tags = new SortableValueMap<String,Integer>();
        
        File directory = new File(directoryName);
        try {
            File[] fList = directory.listFiles();
            for (File file : fList) {
                
                if (file.isFile()) {
                    FileReader f = new FileReader(file.getPath());            
                    br = new BufferedReader(f);

                    while (((sCurrentLine = br.readLine()) != null)) {
                        //p("currentline = " + sCurrentLine);
                        String colName = sCurrentLine.substring(0,sCurrentLine.indexOf(","));
                        if (colName.startsWith("hidden@"))
                            colName = colName.replaceFirst("@",":");
                        String colValue = sCurrentLine.substring(sCurrentLine.indexOf(",")+1, sCurrentLine.length()); 
                        ShareController sc = ShareController.getInstance();
                        //Show tag if not hidden
                        if (isCurrentUserAdmin(_currentUser) || (!colName.startsWith("hidden:")  && sc.getClusterPermission(_currentUser) != null) || 
                            (!colName.startsWith("hidden:")  && (sc.allUsers(colName)) || sc.getPermissionByUser(colName, _currentUser) != null )){
                            if (tags.get(colName) == null){
                                tags.put(colName, 1);
                            }
                            else {
                                tags.put(colName, tags.get(colName) + 1);
                            }
                        }
                    }
                    br.close();
                    f.close();
                } 
            }
        } catch (Exception e) {
            e.printStackTrace();
            p("Hubo un error al listar los archivos del directorio");
        }
        
        tags.sortByValue();
        return tags;
    }
    
    public String getTagsLeftNavBar(String _currentUser, boolean _ismobile, boolean _iswebapp, boolean isAdmin){
        
        SortableValueMap<String,Integer> tags = getTags(_currentUser);
        
        String results = "";
        if (_ismobile) {
            results += "{\n";
            if (_iswebapp) {
                results += "\"username\": \"" + _currentUser + "\",\n";
                results += "\"isAdmin\": \"" + isAdmin + "\",\n";
            }
            results += "\"fighters\": [\n";
        }

        boolean bfirst = true;
        for (Map.Entry entry : tags.entrySet()) {
            //results += "<button type=button><span class=\"label label-warning\" style=\"margin-bottom: 0.2em\" onclick=\"search_query('"+entry.getKey()+"');\">"+entry.getKey()+" ("+entry.getValue()+")</span></button><br>";
            if (_ismobile) {
                if (bfirst) {
                    results += "{\n";
                    bfirst = false;
                } else {
                    results += ",{\n";                    
                }
                results += "\"tagname\": \"" + entry.getKey() + "\",\n";
                results += "\"tagcnt\": \"" + entry.getValue()+ "\"\n";                
                results += "}\n";
            } else {
                results += "<INPUT TYPE=button value=\"" + entry.getKey() +" ("+ entry.getValue()+")"+ "\"  onclick=\"clearFilters(); search_query(&#39;"+entry.getKey()+"&#39;);\"/>";                
            }
        
        }
        
        if (_ismobile) {
            results += "]\n";
            results += "}\n";            
        }
        return results;
    }
    
    public boolean isCurrentUserAdmin(String currentUser){
        FileInputStream bf2 = null;
        boolean isAdmin = false;
        try {
            bf2 = new FileInputStream(appendage + "../rtserver/config/users.txt");
            Scanner scanner2 = new Scanner(bf2);
            while (scanner2.hasNext()) {                
                String line = scanner2.nextLine();
                String[] lineArray = line.split(",");
                if (lineArray.length == 3){
                    if (lineArray[0].equals(currentUser) && lineArray[2].toLowerCase().equals("admin")){
                        isAdmin = true;
                    }
                }                           
            }
            
        } catch (FileNotFoundException ex) {
            Logger.getLogger(LocalFuncs.class.getName()).log(Level.SEVERE, null, ex);                                    
        } finally {
            try {
                bf2.close();
            } catch (IOException ex) {
                Logger.getLogger(LocalFuncs.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return isAdmin;
    }
    
    
     public TxMaker loadShareMapDB() {
        try {
            
            TxMaker tx_share;
            
            pw("LoadShareMapDB()");  
            
            String sFile = appendageRW + "../rtserver/sharesdb";
            String sDBName = readDoc(appendageRW + "../rtserver/dbsharename.txt");
            if (sDBName.length() > 0) {
                sFile = sDBName.trim();
            }
            pw("DBName: '" + sFile + "'");
            
            String sDBVersion = readDoc(appendageRW + "../rtserver/dbver.txt");
            if (sDBVersion.length() > 0) {
                sDBVersion = sDBVersion.trim();
            } else {
                //assume v0.9.8 if file doesn't exist (beta user legacy)
                sDBVersion = "0.9.8";
            }
            pw("DBVersion: '" + sDBVersion + "'");
            
            String sDBCurrent = getCurrentMapDBVersion();
            pw("DBCurrent: '" + sDBCurrent + "'");
                     
            String sAppend = "../rtserver/";
            if (appendage.length() > 0) sAppend = "";
            
            File fh = new File(sAppend + sFile);
            
            pw("mapDB File '" + fh.getAbsolutePath() + "' exists: " + fh.exists());
            
            if (fh.exists()) {
                File parent = fh.getParentFile();
                
                if(!parent.exists() || !parent.isDirectory())
                    throw new IOException("Parent folder does not exist: "+fh);
                if(!parent.canRead())
                    throw new IOException("Parent folder is not readable: "+fh);
                if(!parent.canWrite())
                    throw new IOException("Parent folder is not writeable: "+fh);                
            }
            
            boolean bCreate = false;           
            if (fh.exists() && isBackwardCompatible(sDBCurrent, sDBVersion)) {
                pw("Index : DB Exists...");
                    boolean bOK = false;
                    p("Exist[1]");
                    try {
                        p("MAP DB READ/WRITE");
                        tx_share = DBMaker.newFileDB(new File(sAppend + sFile)).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                                                     
                        //sharedb = tx_share.makeTx();
                        bOK = true;
                    if (bOK) {
                        p("MapDB load OK.");      
                        return tx_share;
                    } else {
                        p("MapDB db OPEN ERROR. Forcing Create."); 
                        bCreate = true;          
                    }
                } catch (Exception e) {
                    bCreate = true;
                    e.printStackTrace();
                }
            } else {
                if (!fh.exists()) {
                    pw("db does not exist. creating...");
                } else {
                    //file exists, but not backward compatible
                    if (!isBackwardCompatible(sDBCurrent, sDBVersion)) {
                        pw("db not backward compatible. re-creating...");                    
                    }                    
                }
                bCreate = true;                    
            }
            p("Create = " + bCreate);
            if (bCreate) {
                try {

                    Date ts_start = Calendar.getInstance().getTime();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
                    String sDate = sdf.format(ts_start);

                    sDBName = "sharedb" + sDate;

                    sFile = appendageRW + "../rtserver/" + sDBName;

                    PrintStream ps = new PrintStream(new BufferedOutputStream(
                    new FileOutputStream(appendageRW + "../rtserver/dbsharename.txt", false)));
                    ps.println(sDBName);
                    ps.close();
                    
                    p("Index: Creating new DB at: " + sFile);
                    
                    sAppend = "./";
                    if (appendage.length() > 0) sAppend = "";
                    
                    tx_share = DBMaker.newFileDB(new File(sAppend + sFile)).closeOnJvmShutdown().cacheLRUEnable().mmapFileEnableIfSupported().makeTxMaker();                        
                    //sharedb = tx_share.makeTx();
                    
                    return tx_share;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            } else {
                p("here!!!");
                Thread.sleep(1000);
                return null;
            }           
        } catch (InternalError e) {
            e.printStackTrace();
            p("INTERNAL ERROR.");
            return null;
        } catch (Exception e) {
            p("Exception during OPEN");
            e.printStackTrace();
            return null;
        }
    }

   
     public String getMediaURL(String sNamer, String mediaType, boolean _awshosted) {
        String[] fileurl = read_view_link(sNamer, true, true);
        if(!fileurl[0].equals("ERROR") && !fileurl[0].equals("TIMEOUT") && !fileurl[0].equals("FILENOTFOUND")){
            if(fileurl[0].contains("/")){
                String[] splited = fileurl[0].split("\\/");
                if(splited.length >= 2){
                    String[] hostNport = splited[2].split("\\:");
                    p("*** UUID = " + fileurl[1]);
                    String sNettyPort = (String)mapNetty.get(fileurl[1]);
                    if (sNettyPort == null) {
                        sNettyPort = get_row_attribute("Keyspace1b","NodeInfo", fileurl[1], "nettyport", null);                        
                        p("*** Get NettyPort = '" + sNettyPort + "'");
                        mapNetty.put(fileurl[1], sNettyPort);
                    } else {
                        p("*** Cache NettyPort = '" + sNettyPort + "'");
                    }
                    if (sNettyPort.length() == 0) sNettyPort = "8084";
                    p("*** NettyPort = '" + sNettyPort + "'");
                    String function = null;
                    if(mediaType.equals("audio")){
                        function = "/getaudio.fn?md5=";
                    }else if(mediaType.equals("video")){
                        function = "/getvideo.m3u8?md5=";
                    }else{
                        return null;
                    }
                    String sHost = hostNport[0];
                    if (_awshosted) {
                    //if (hostNport[0].startsWith("10.")) {
                        sHost = "cloud.alterante.com";
                    }
                    return splited[0] + "//" + sHost +  ":" + sNettyPort + function + sNamer;
                }
            }
            
        }
        return null;
    }
    
    
}

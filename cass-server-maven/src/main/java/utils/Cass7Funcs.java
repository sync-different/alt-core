/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */

package utils;

import utils.LocalFuncs;

import java.awt.image.BufferedImage;
import java.lang.OutOfMemoryError;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.URI;

import java.text.SimpleDateFormat;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.SuperColumn;

import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.ColumnParent;
import org.apache.cassandra.thrift.ColumnPath;
import org.apache.cassandra.thrift.Column;

import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.NotFoundException;
import org.apache.cassandra.thrift.SlicePredicate;
import org.apache.cassandra.thrift.SliceRange;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TFramedTransport;


import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.concurrent.ConcurrentNavigableMap;
import javax.imageio.ImageIO;
import org.mapdb.Bind;
import org.mapdb.DB;
//import static utils.LocalFuncs.occurences_copies;
import static utils.WebFuncs.loadProps;


public class Cass7Funcs {

    protected static Properties props = new Properties();

    private static final SimpleDateFormat DateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
    
    private static String superColumnFamily = "Super2";
    public static final String UTF8 = "UTF8";
    public static String keyspace = "Keyspace1b";
    public static String columnFamily = "Standard1";
    public static String columnFamilyAC = "Standard2";
    public static String columnFamilyBatch = "BatchJobs";
    public static String columnFamilyNode = "NodeInfo";

    //public static String keyUserID = "1";
    private TTransport mTransport;
    private TProtocol mProtocol;
    private static Cassandra.Client mCassandraClient;
    private ColumnPath mColPath;
    private String sNames[];
    private int nCount;
    
    private boolean bNodesLoaded = false;
    private static boolean bConnected = false;
    
    public String dbmode = "cass";  // or p2p
        
    HashMap deadnodes_uuid = new HashMap<String, Integer>();

    static HashMap dates_prev = new HashMap<String, String>();
    
    static java.util.Hashtable mapFileExtensions = new java.util.Hashtable();
    
    String LOG_NAME_BACKUP_DEBUG_PATH = "logs/";    
    String LOG_NAME_PERFORMANCE_PATH = "logs/";
    
    private static String THUMBNAIL_OUTPUT_DIR = "../cass/pic";
    
    PrintStream log = null;
    PrintStream logPerf = null;
    PrintStream logServerError;

    LocalFuncs lf = new LocalFuncs();

    //NOTA: Subi estos a definicion de la clase para que no se limpien cada vez que corre la rutina read_row_list2,
    //hay que mantenerlos para el caso cache (SAME QUERY)
    //static HashMap<String, String> occurences_copies = new HashMap<String, String>();
    static HashMap<String, String> occurences_names = new HashMap<String, String>();


    static String appendage = "";
    static String appendageRW = "";
    
    /**
     *
     * @param args
     * @throws UnsupportedEncodingException
     * @throws InvalidRequestException
     * @throws UnavailableException
     * @throws TimedOutException
     * @throws TException
     * @throws NotFoundException
     */
    /**
     * Connect to Cassandra
     * @param host The IP of the Cassandra server
     * @param port The port where Cassandra server is listening (default = 9160)
     * @throws UnsupportedEncodingException
     * @throws InvalidRequestException
     * @throws UnavailableException
     * @throws TimedOutException
     * @throws TException
     * @throws NotFoundException
     * 
     * 
     */
    
    /* print to the log file */
    protected void log(String s) {

        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (log == null) {
            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd");
            String sDateFile = sdf2.format(ts_start);

            String sFilename = appendageRW + LOG_NAME_PERFORMANCE_PATH + sDateFile + "__rtserver_log.txt";
            try {
                log = new PrintStream(new BufferedOutputStream(new FileOutputStream(sFilename, true)));
            } catch (Exception e) {
                e.printStackTrace();
            }                       
        }
        synchronized (log) {
            p(sDate + " " + s);
            log.println(sDate + " " + s);
            log.flush();
        }
    }
    
    protected void logErrors(String timestamp, String error, boolean writeLog){
        if (writeLog){
            synchronized (logServerError) {
                logServerError.println(timestamp+": "+error);
                logServerError.flush();
            }
        }
    }
    
    
    protected void logPerfWriteHeader(String path) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path));
            if (br.readLine() == null) {
                logPerf.println("timestamp,jar,routine,subroutine,time");
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
    
    protected void logPerf(String _timestamp, String jar, String routine, String subroutine, String time, boolean writeLog) {
        if (writeLog){
            synchronized (logPerf) {
                logPerf.println(_timestamp + "," + jar + "," + routine + "," + subroutine + "," + time);
                logPerf.flush();
            }
        }
    }

    // ***** BEGIN ANSI *****

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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.Cass7Funcs-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.Cass7Funcs-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [CS.Cass7Funcs-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    static public void p(String s) {

        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.Cass7Funcs_" + threadID + "] " + s);
    }

    public boolean connect(String host, int port) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {

        try {
          if (!bConnected) {
              p("*** Connecting to Cassandra..." + host + ":" + port);
              mTransport = new TFramedTransport(new TSocket(host, port));
                mProtocol = new TBinaryProtocol(mTransport);
                mCassandraClient = new Cassandra.Client(mProtocol);
                mTransport.open();  
                bConnected = true;
                return true;
          } else {
              p("*** Already connected. skipping connect()");
              return true;
          }
        } catch (Exception ex) {
            p("*** Exception Connecting to Cassandra..." + ex.getMessage());
            return false;
        }
        
        
        //mColPath = new ColumnPath(columnFamily);

//        //declaracion columnas
//        String sFullName = "fullname";
//        String sAge = "age";
//
//
//        keyUserID = "3";
//        read_column(client, colPath, sFullName);
//        read_column(client, colPath, sAge);
//
//        keyUserID = "1";
//        read_row(client, keyUserID);
//        keyUserID = "2";
//        read_row(client, keyUserID);
//        keyUserID = "3";
//        read_row(client, keyUserID);
//
        //keyUserID = "378";
//        read_row(client, keyUserID);

    }

    public int close() {
        try {
            if (bConnected) {
                //p("Disconnecting from cassandra...");
                bConnected = false;
                mTransport.close();                
            } else {
                //p("Not connected to Cass. Skip Disconnecting...");
            }
            return 0;
        } catch (Exception ex) {
            p("Hit an exception when trying to disconnect Cassandra.");
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }
    }

    public static void read_column(String _key, ColumnPath colPathName, String sFullName) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {

        // read single column


        String sPrint = "\n----\nread_column: <'" + sFullName + "'>";
        p(sPrint);


        colPathName.setColumn(sFullName.getBytes(UTF8));

        //Column col = mClient.get(keyspace, _key, colPathName,
        //        ConsistencyLevel.ONE).getColumn();

        //p("column name: " + new String(col.name, UTF8));
        //p("column value: " + new String(col.value, UTF8));
        //p("column timestamp: " + new Date(col.timestamp));

    }

   
    
    
    public String read_row_autocomplete(String _key)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {

            String res = "";
            
        try {
            
                mCassandraClient.set_keyspace(keyspace);

                // read entire row
                SlicePredicate predicate = new SlicePredicate();
                SliceRange sliceRange = new SliceRange();
                sliceRange.setStart(new byte[0]);
                sliceRange.setFinish(new byte[0]);
                predicate.setSlice_range(sliceRange);

                ColumnParent parent = new ColumnParent(columnFamilyAC);
                byte[] kk = _key.getBytes();

                List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);              

                String name2 = "";
                String val2 = "";

                for (ColumnOrSuperColumn result : results) {

                    Column column = result.column;
                    name2 = URLDecoder.decode(new String(column.getName(), UTF8), UTF8);
                    val2 = URLDecoder.decode(new String(column.getValue(), UTF8), UTF8);
                    
                    p(name2 + " -> " + val2);
                    res += "<li onClick=\"clearFilters(); clearFiltersVar();fill('" + name2 + "');\">" + val2 + "</li>";
                }                    
                    
                
                //p("\n Length RES:" + res.length()); 
                return res;
        
        } catch (IOException OutofMemoryError) {
            p("***TApplicationException: ");
            res = "ERROR_MEM";
        } catch (Exception TApplicationException) {
            p("***TApplicationException: ");
            res = "ERROR_THR";
        }
        return res;
    }


    public String get_batches(String _key, String _password, String _View, String _FileType, String _DaysBack, String _NumCol, String _NumObj, String _screenSize) {
        try {
            mCassandraClient.set_keyspace(keyspace);

            // read entire row
            SlicePredicate predicate = new SlicePredicate();
            SliceRange sliceRange = new SliceRange();
            sliceRange.setStart(new byte[0]);
            sliceRange.setFinish(new byte[0]);
            predicate.setSlice_range(sliceRange);
            sliceRange.setCount(50000);

            ColumnParent parent = new ColumnParent(columnFamilyBatch);
            //String _key = "batchid";
            byte[] kk = _key.getBytes();
            List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);

            String name2 = "";
            String val2 = "";
            String sres = "";
            String sheader = "";
            long nCurrent = 0;

            sheader += "<table border=" + "\"1\"" + "><tr>";
            sheader += "<td>id</td>";
            
            int nCount = 0;
            boolean stopheader = false;


            for (ColumnOrSuperColumn result : results) {
                sres += "<tr>";
                Column column = result.column;
                name2 = URLDecoder.decode(new String(column.getName(), UTF8), UTF8);
                val2 = URLDecoder.decode(new String(column.getValue(), UTF8), UTF8);

                p(name2 + " -> '" + val2 + "'");
                
                //sres += "<td><a href='echoClient5.php?foo=batch:" + name2 + "&pw=" + _password + "'>" + name2 + "</a></td>";
                
                String sRedirLink = "echoClient5.php?foo=batch:" + name2 + "&pw=" + _password +
                                    "&view=" + _View + 
                                    "&ftype=" + _FileType +  
                                    "&days=" + _DaysBack + 
                                    "&numcol=" + _NumCol +
                                    "&numobj=" + _NumObj +
                                    "&screenSize=" + _screenSize;

                sres += "<td><INPUT TYPE=button value=\""+ name2 + "\" onclick=\"golink('" + sRedirLink + "','" + "batch:" + name2 + "')\"/></td>";

                kk = name2.getBytes();
                List<ColumnOrSuperColumn> results2 = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
                for (ColumnOrSuperColumn result2 : results2) {
                    Column column2 = result2.column;
                    String name3 = URLDecoder.decode(new String(column2.getName(), UTF8), UTF8);
                    String val3 = URLDecoder.decode(new String(column2.getValue(), UTF8), UTF8);
                    
                    sres += "<td>" + val3 + "</td>";
                    if (nCount < 11) {
                        nCount ++;
                        sheader += "<td>" + name3 + "</td>";
                    } else {
                        stopheader = true;
                    }
                }
                sres += "</tr>";
            }
            sres += "</table>";
            p("returning -> '" + sres + "'");
            return sheader + "</tr>" + sres;

        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
            p("HIT EXCEPTION: " + ex.getMessage());
            //for next time,  open a new connection
            bConnected = false;
        } catch (UnsupportedEncodingException ex) {
            p("InvalidRequestException: " + ex.getMessage());
        } catch (InvalidRequestException ex) {
            p("InvalidRequestException: " + ex.getMessage());
        } catch (UnavailableException ex) {
            p("UnavailableException: " + ex.getMessage());
        } catch (TimedOutException ex) {
            p("TimedOutException: " + ex.getMessage());
        }
        return "ERROR_GETBATCHES";
    }



    public String get_batch_id(String _key, String _column_family, String _column_name) {
        try {
            mCassandraClient.set_keyspace(keyspace);

            // read entire row
            SlicePredicate predicate = new SlicePredicate();
            SliceRange sliceRange = new SliceRange();
            sliceRange.setStart(new byte[0]);
            sliceRange.setFinish(new byte[0]);
            predicate.setSlice_range(sliceRange);

            ColumnParent parent = new ColumnParent(_column_family);
            //String _key = "batchid";
            byte[] kk = _key.getBytes();
            List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);

            String name2 = "";
            String val2 = "";
            long nValue = 0;

            for (ColumnOrSuperColumn result : results) {
                Column column = result.column;
                name2 = URLDecoder.decode(new String(column.getName(), UTF8), UTF8);
                val2 = URLDecoder.decode(new String(column.getValue(), UTF8), UTF8);

                //p(name2 + " -> '" + val2 + "'");
                if (name2.equals(_column_name)) {
                    nValue = Long.parseLong(val2);
                    //p("value -> " + nValue );
                }

            }
            //nCurrent++;
            //p("returning -> '" + nValue + "'");
            return Long.toString(nValue);
            

        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            p("InvalidRequestException: " + ex.getMessage());
        } catch (InvalidRequestException ex) {
            p("InvalidRequestException: " + ex.getMessage());
        } catch (UnavailableException ex) {
            p("UnavailableException: " + ex.getMessage());
        } catch (TimedOutException ex) {
            p("TimedOutException: " + ex.getMessage());
        }
        return "ERROR";
    }
    
    private boolean isHashFile(String sFileName) {
            p("len = " + sFileName.length());
            if (sFileName.length() == 32) {
               int nLower = 0;
               for (int i = 0; i< sFileName.length(); i++) {
                    char chr = sFileName.charAt(i);
                    if (Character.isLowerCase(sFileName.charAt(i))) {
                       nLower++;
                    }
               }
               p("nlower = " + nLower);
               if (nLower > 0) {
                   return false;
               } else {
                   return true;
               }
            } else {
               return false;
            }           
       }
 
    public boolean objectAlreadyExist(String _columnName) {
        try {
            String dateEncoded = URLEncoder.encode(_columnName, UTF8);                    
            mCassandraClient.set_keyspace(keyspace);
            
            ColumnPath colPath = new ColumnPath(columnFamily);
            colPath.setColumn(dateEncoded.getBytes());
            ColumnOrSuperColumn get = mCassandraClient.get(ByteBuffer.wrap(".all".getBytes()), colPath, ConsistencyLevel.ONE);
            
        } catch (NotFoundException ex) {
            return false;
        } catch (UnavailableException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimedOutException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRequestException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return true;
    }
    
    //Get a date that not exist in the DB
    public String getValidDateModified(String _dateModified) {
                
        String result = _dateModified;
        
        while (objectAlreadyExist(result)) {
            try {
                
                Calendar c = Calendar.getInstance();
                c.setTime(DateFormat.parse(result));
                
                Random rand = new Random();
                int x = rand.nextInt(10);
                c.add(Calendar.MILLISECOND, x);
                
                result = DateFormat.format(c.getTime());
                
            } catch (ParseException ex) {
                Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        
        return result;
    }
    
    
    
    
    public int get_objects_cass(String w, 
            SortableValueMap<String, Integer> occurences_hash, 
            HashMap<String, String> occurences_names, 
            SliceRange sliceRange, 
            String _numobj,
            String _filetype,
            String _key,
            SlicePredicate predicate,
            String sLast,
            Boolean bShowNext) {
        
        try {
            SortableValueMapLong<String, Long> occurences = new SortableValueMapLong<String, Long>();

            p("--------------------");
            p("FILETYPE: " + _filetype);
                p("connected cassandra: " + bConnected);
                if (!bConnected) connect("localhost", 9160);
                
                mCassandraClient.set_keyspace(keyspace);

                String sFirst = "";
                //String sLast = "";
                String sNextLink = "";

                int nCount = 0;
                int nCountHidden = 0;
                int nCountVisible = 0;
                int nCountSkipped = 0;
                //boolean bShowNext = false;
                
                String res = "";

                //p("token: " + w);

                byte[] kk = w.getBytes();

                ColumnParent parent = new ColumnParent(columnFamily);

                int nTries = 0;
                int nIteration = 0;
                boolean bContinue = true;
                int nObject = 0;
                
                
                while (bContinue) {
                    nIteration++;
                    
                    p("Iteration #" + nIteration);
                    
                    if (nIteration > 1) {
                        p("starting at date: " + sLast);   
                        sliceRange.setStart(ByteBuffer.wrap(sLast.getBytes()));
                    }
                    

                    List<ColumnOrSuperColumn> results = null;
                    
                    int nRetry = 0;
                    boolean bTryRead = true;
                    boolean bSuccess = false;
                    while (bTryRead) {
                        try {
                            results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
                            bTryRead = false;
                            bSuccess = true;
                        } catch (Exception TimedOutException) {
                            p("TimeoutException. Retry #: " + nRetry);
                            Thread.sleep(3000);
                            nRetry++;
                            if (nRetry > 3) {
                                bTryRead = false;
                            }
                        }                        
                    }

                    if (bSuccess) {
                        sLast = "";
                        nCount = results.size();

                        p("\nResult set size:" + results.size());
                        p("\n _numobj: " + _numobj);

                        for (ColumnOrSuperColumn result : results) {
                                
                                if (occurences_names.size() < Integer.valueOf(_numobj)) {
                                    String sName = new String (result.column.getName(), UTF8);
                                    String sValue = new String (result.column.getValue(), UTF8);
                                    p(sName + " " + sValue);
                                    
                                    DateFormat format = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
                                    long regTime = 0;
                                    Date date = format.parse(sName);
                                    regTime = date.getTime();
                                    
                                    occurences.put(sValue, regTime);
                                    
                                    String sHidden = "";
                                    sHidden = get_row_attribute(keyspace, "Standard1", sValue, "hidden");
                                    if ((sHidden.length() > 0) && !_key.contains("hidden:")) {
                                        nCountHidden++;
                                    } else {
                                        
                                        String sFileType = "." + get_row_attribute(keyspace, "Standard1", sValue, "ext");
                                        p("Filetype = " + sFileType);
                                        if ((_filetype.equalsIgnoreCase(".photo") && is_photo(sFileType)) || sFileType.contains(_filetype) || _filetype.equalsIgnoreCase(".all")) {
                                            nCountVisible++;    
                                            res += read_row_hash_cass(new String(result.column.getValue(), "UTF8"), occurences_hash, occurences_names);
                                            //String sName = new String(result.column.getName(), UTF8);
                                            p(nCountVisible + " '" + sName + "' -> '" + sValue + "'");
                                            sLast = sName;
                                            if ("".equals(sFirst)) {
                                                sFirst = sName;
                                            }
                                        } else {
                                            p("'" + sFileType + "' vs '" + _filetype + "'");
                                            //p("not passed");
                                            nCountSkipped++;
                                        }
                                    }                            
                                } else {
                                    p("I have enough objects. Exiting.");
                                    bContinue = false;
                                    bShowNext = true;
                                    break;
                                }
                            }  

                        p("\nRequested size       :" + _numobj);
                        p("\nResults(visible)     :" + nCountVisible);
                        p("\nResults(hidden)      :" + nCountHidden);
                        p("\nResults(dedup/unique):" + occurences_names.size());


                        if (results.size() < Integer.valueOf(_numobj)) {
                            p("No more objects in DB");
                            bContinue = false;
                        } 
                        if (occurences_names.size() >= Integer.valueOf(_numobj)) {
                            //we have enough objects to show
                            p("we have enough objects. Exiting!");
                            bContinue = false;
                            bShowNext = true;
                        }
                        if (nIteration > 10) {
                            p("too many iterations. exiting.");
                            bContinue = false;
                        }
                    } else {
                        p("[WARNING] There was an error reading the data for token: '" + w + "'");
                    }
                } 
                return nCountVisible;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } 
    }
    
      void setAppendage() {
        boolean result = false;
        File directory = new File("/Applications/Alterante.app/Contents/AlteranteJava.app/Contents/MacOS").getAbsoluteFile();
        //File directory = new File("../app/projects/rtserver").getAbsoluteFile();
        if (directory.exists())
        {
            p("[loadfuncs] Found app directory. Setting working dir to it");
            result = (System.setProperty("user.dir", directory.getAbsolutePath()) != null);
            
            appendage = "/Applications/Alterante.app/Contents/AlteranteJava.app/Contents/app/projects/rtserver/";
            p("appendage  = " + appendage);
            //appendage = "../app/projects/rtserver/";        
        }
        
        String username = System.getProperty("user.name");
        p("username: " + username);
        File directoryRW = new File("/Users/" + username + "/Library/Containers/com.alterante.desktopapp1j");
        if (directoryRW.exists()) {
            p("[Cass7Funcs] Found container directory. checking folders.");
            appendageRW = "/Users/" + username + "/Library/Containers/com.alterante.desktopapp1j/Data/app/projects/rtserver/";
            File dir = new File("/Users" + username + "/Library/Containers/com.alterante.desktopapp1j/Data/app/projects/rtserver");
            if (dir.exists()) {
                p("appendageRW rtserver exists.");
            } else {
                boolean res = new File(appendageRW).mkdirs();
                p("appendageRW rtserver create = " + res);
                res = new File(appendageRW + "/logs/").mkdirs();
                p("appendageRW rtserver create logs = " + res);
            }               
        } else {
            p("[Cass7Funcs] Container directory not found.");
        }
        
    }
    
        public String read_row_list2(String _keyin, 
                                int nMode, 
                                String _root, 
                                String _numobj, 
                                String _filetype, 
                                String _numcol, 
                                String _password,
                                String _pid, 
                                String _daysback,
                                String _datestart,
                                String _host,
                                String _port,
                                Boolean _cloudhosted,
                                Boolean _awshosted,
                                String _clientIP,
                                String _user,
                                boolean _writeLog,
                                boolean _dobase64,
                                String _screenSize,
                                boolean _bOrderAsc)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {

        SortableValueMap<String, Integer> occurences_hash = new SortableValueMap<String, Integer>();
        SortableValueMapLong<String, Long> occurences = new SortableValueMapLong<String, Long>();

            
        String sDebug = "";
        StringBuilder res = new StringBuilder();
        String sMode = "";
        String dateUTF = "";
        String sRedirLink = "";
        
        Long[] Time = new Long[23];
        for (int i=0; i<23; i++) Time[i] = 0L;
                
        Date ts_start = Calendar.getInstance().getTime();
        String timestampPerf = (new Date()).toGMTString();        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String sDateFile = sdf.format(ts_start);
        
        try {
            
            setAppendage();
                    
            //open error log
            String sFilename = appendageRW + LOG_NAME_PERFORMANCE_PATH + sDateFile + "_performance_server_error.txt";
            logServerError = new PrintStream(new BufferedOutputStream(
            new FileOutputStream(sFilename, true)));               
            logErrors(timestampPerf,"Error log started", true);
                 
            //open perf CSV
            if (_writeLog) {                        
                try {
                sFilename = appendageRW + LOG_NAME_PERFORMANCE_PATH + sDateFile + "_performance_server_debug.csv";
                logPerf = new PrintStream(new BufferedOutputStream(
                          new FileOutputStream(sFilename, true)));   
                logPerfWriteHeader(sFilename);
            } catch (Exception e) {
                StringWriter sWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(sWriter));
                logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
                _writeLog = false;
            }
          }
          
          Stopwatch timer_all = new Stopwatch().start();
          Stopwatch timer_index = null;
          Stopwatch timerAux = null;         

          //Start Block [AntesSwitch]
          timerAux = new Stopwatch().start();
          loadProps();
          loadDBMode();
          
            // read entire row
          SlicePredicate predicate = new SlicePredicate();
          SliceRange sliceRange = new SliceRange();
            
          //p("datestart: '" + _datestart + "'");
          //p("datestart length: '" + _datestart.length() + "'");
          //p("daysback: '" + _daysback + "'");
          
          String sStartDate = "";

          if (_daysback.equals("") && _datestart.equals("")) {
                //blank case
                sliceRange.setStart(new byte[0]);
                sliceRange.setFinish(new byte[0]);
          } else {
                //String sStartDate = "";
                String sFinishDate = DateFormat.format(Calendar.getInstance().getTime());
                sFinishDate = URLEncoder.encode(sFinishDate, "UTF-8");

                if ( _datestart.length() == 0) {
                    //start date blank, ndays exists
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DATE, -(Integer.valueOf(_daysback)));
                    sStartDate = DateFormat.format(cal.getTime());                    
                    sStartDate = URLEncoder.encode(sStartDate, "UTF-8");
                } else {
                    //start date exists, use it
                    //p("datestart: '" + _datestart + "'");
                    sStartDate = URLDecoder.decode(_datestart, "UTF-8");
                }               

                //p("start: " + sStartDate);
                //p("end: " + sFinishDate);                
                
                sliceRange.setStart(ByteBuffer.wrap(sStartDate.getBytes()));
                
                if (isHashFile(sStartDate)) {
                    //case where start param is a UUID, in which case we set end date to blank
                    sliceRange.setFinish(new byte[0]);    
                } else {
                    //start param is an actual date, so current time/date is the end date
                    sliceRange.setFinish(ByteBuffer.wrap(sFinishDate.getBytes()));    
                }
                
          }

        timerAux.stop();

        logPerf(timestampPerf, "casss_server","read_row_list2", "AntesSwitch", timerAux.getElapsedTime()+"", _writeLog);
        Time[0] += timerAux.getElapsedTime();
        
        timerAux = new Stopwatch().start();
        
          //sliceRange.setCount(Integer.valueOf(_numobj) + 1);
          sliceRange.setCount(Integer.valueOf(_numobj) + 1);
          predicate.setSlice_range(sliceRange);

          //sNames = new String[100];
          nCount = 0;


          String _key = _keyin.toLowerCase();

          //p("\nKey: '" + _key + "'");

          String delimiters = " ";
          boolean wQuotes = false;
          
          _key = URLDecoder.decode(_key, "UTF-8");
                  
          if (_key.startsWith("\"") && _key.endsWith("\"")) {
              delimiters = "";
              _key = _key.replace("\"", "");
              wQuotes = true;
          }
          
          StringTokenizer st = new StringTokenizer(_key.trim(), delimiters, true);
            
          String sFirst = "";
          String sLast = "";
          String sNextLink = "";

          int nCount = 0;
          int nCountHidden = 0;
          int nCountVisible = 0;
          int nCountSkipped = 0;
          boolean bShowNext = false;
          
          loadFileExtensions();
          
          String cnt_total = "";
          String cnt_photo = "";
          String cnt_music = "";
          String cnt_video = "";
          String cnt_doc = "";
          String cnt_xls = "";
          String cnt_ppt = "";
          String cnt_pdf = "";          
          String cnt_office = "";       
          String cnt_size = "";
          String cnt_alltime = "";
          String cnt_past24h = "";
          String cnt_past3d = "";
          String cnt_past7d = "";
          String cnt_past14d = "";
          String cnt_past30d = "";
          String cnt_past365d = "";
          
          boolean bFirstTime = true;
          
        logPerf(timestampPerf, "casss_server","read_row_list2", "LoadFileExtensions", timerAux.getElapsedTime()+"", _writeLog);
        Time[1] += timerAux.getElapsedTime();
        
        timerAux = new Stopwatch().start();
        
          //------------------ 2-GetLocalAddress                          
          InetAddress clientIP = getLocalAddress();
          String sLocalIP = "127.0.0.1";
          if (clientIP != null) {
              sLocalIP = clientIP.getHostAddress();;                            
          }          
          
        timerAux.stop();

        logPerf(timestampPerf, "casss_server","read_row_list2", "GetLocalAddress", timerAux.getElapsedTime()+"", _writeLog);
        Time[2] += timerAux.getElapsedTime();
        
        timerAux = new Stopwatch().start();
        int cnt_alltime_int = 0;
        int nTokens = 0;
        
          while (st.hasMoreTokens()) {
                if (bFirstTime) {
                    occurences_hash.clear();
                    if (dbmode.equals("cass")) {
                        occurences_names.clear();
                        //occurences_copies.clear();                        
                    }
                    bFirstTime = false;
                }
                //nTokens = nTokens + 1;
                String w = st.nextToken();
                
                if (!delimiters.contains(w)){
                    
                nTokens++;
                
                //if (st.hasMoreTokens()) {
                //    st.nextToken();   
                //}
                                
                if (dbmode.equals("p2p") && (w.length() > 0)) {                    
                    sLast = sStartDate;
                    timer_index = new Stopwatch().start();
                    sDebug = lf.get_objects_sorted(w, 
                            occurences, 
                            occurences_hash, 
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
                            _bOrderAsc);
                    timer_index.stop();
                    int nres = 1;
                    if (nres > 0){
                        if (lf.nAllTime > 0) {
                            cnt_alltime = String.valueOf(lf.nAllTime);                            
                        }
                        if (lf.nPast24h > 0) {
                            cnt_past24h = String.valueOf(lf.nPast24h);
                        }
                        if (lf.nPast3d > 0) {
                            cnt_past3d = String.valueOf(lf.nPast3d);
                        }
                        if (lf.nPast7d > 0) {
                            cnt_past7d = String.valueOf(lf.nPast7d);
                        }
                        if (lf.nPast14d > 0) {
                            cnt_past14d = String.valueOf(lf.nPast14d);
                        }
                        if (lf.nPast30d > 0) {
                            cnt_past30d = String.valueOf(lf.nPast30d);
                        }
                        if (lf.nPast365d > 0) {
                            cnt_past365d = String.valueOf(lf.nPast365d);
                        }
                        if (lf.nTotal > 0) {
                            cnt_total = String.valueOf(lf.nTotal);                            
                        }
                        if (lf.nPhoto > 0) {
                            cnt_photo = String.valueOf(lf.nPhoto);                            
                        }
                        if (lf.nMusic > 0) {                        
                            cnt_music = String.valueOf(lf.nMusic);
                        }
                        if (lf.nVideo > 0) {                        
                            cnt_video = String.valueOf(lf.nVideo);
                        }
                        int nOffice = 0;
                        if (lf.nOfficeDoc > 0) {                        
                            cnt_doc = String.valueOf(lf.nOfficeDoc);
                            nOffice += lf.nOfficeDoc;
                        }
                        if (lf.nOfficeXls > 0) {                        
                            cnt_xls = String.valueOf(lf.nOfficeXls);
                            nOffice += lf.nOfficeXls;
                        }
                        if (lf.nOfficePpt > 0) {                        
                            cnt_ppt = String.valueOf(lf.nOfficePpt);
                            nOffice += lf.nOfficePpt;
                        }
                        if (lf.nOfficePdf > 0) {                        
                            cnt_pdf = String.valueOf(lf.nOfficePdf);
                            nOffice += lf.nOfficePdf;
                        }
                        if (nOffice > 0) {                        
                            cnt_office = String.valueOf(nOffice);
                        }
                        if (lf.nSize > 0) {                        
                            cnt_size = String.valueOf(lf.nSize);
                        }
                    }
                    p("bShowNext = " + bShowNext);
                    p("sn = " + lf.sn);
                    p("slast = " + lf.slast);     
                    if (lf.sn) {
                        bShowNext = lf.sn;
                        sLast = lf.slast;
                    }      
                } 
                
                if (dbmode.equals("cass") || dbmode.equals("both")) {
                    timer_index = new Stopwatch().start();
                    int nres = get_objects_cass(w, occurences_hash, occurences_names, sliceRange, _numobj, _filetype, _key, predicate, sLast, bShowNext);                    
                    nCountVisible = nres;
                    timer_index.stop();
                    
                    cnt_size = String.valueOf(occurences_hash.size());
                    cnt_total = cnt_size;                    
                }                 
                }
        }
        
        p("nTokens:" + nTokens);
        p("Results names(dedup/unique):" + occurences_names.size());
        p("Results hash(dedup/unique):" + occurences_hash.size());
        p("**** Results(visible)     :" + nCountVisible);
        p("Results(hidden)      :" + nCountHidden);
        p("Results(skipped)     :" + nCountSkipped);
        p("Requested size       :" + _numobj);
        p("Query set site       :" + nCount);
        p("Shownext             :" + bShowNext);
        
        //p("   *** MODE: " + nMode);
        switch (nMode) {
           case 4:
               sMode = "tile";
               break;
           case 7:
               sMode = "detail";
               break;
           default:
               sMode = String.valueOf(nMode);
               break;
        }
        
        
        timerAux.stop();

        logPerf(timestampPerf, "casss_server","read_row_list2", "AntesSwitch", timerAux.getElapsedTime()+"", _writeLog);
        Time[3] += timerAux.getElapsedTime();
        
        timerAux = new Stopwatch().start();
        
        //if result set was larger than #objects we want to show, then prepare next page
        if (bShowNext) {
            //p("It's time for nexter...");
            //p("Last date: '" + sLast + "'");
            dateUTF = URLEncoder.encode(sLast, "UTF-8");

            sNextLink = "<a href=\"echoClient5.htm?" + 
                        "ftype=" + _filetype + "&" + 
                        "days=" + _daysback + "&" + 
                        "foo=" + _keyin.replaceAll("\"","&quot;") + "&" + 
                        "view=" + sMode + "&" + 
                        "numobj=" + _numobj + "&" + 
                        "numcol=" + _numcol +"&" + 
                        "pw=" + _password +"&" + 
                        "screenSize=" + _screenSize +"&" + 
                        "date=" + dateUTF + 
                        "\" target=MAIN>Show next " + _numobj + " results</a>"
                        ;
            //sNextLink = "";
            
            sRedirLink = "echoClient5.htm?" + 
                        "ftype=" + _filetype + "&" + 
                        "days=" + _daysback + "&" + 
                        "foo=" + _keyin.replaceAll("\"","&quot;") + "&" + 
                        "view=" + sMode + "&" + 
                        "numobj=" + _numobj + "&" + 
                        "numcol=" + _numcol +"&" + 
                        "pw=" + _password +"&" + 
                        "screenSize=" + _screenSize +"&" + 
                        "date=" + dateUTF +"&";
            
            p("dateutf = " + dateUTF);
            p("datestart = " + _datestart);
            
            if (!dateUTF.equals(_datestart)) dates_prev.put(dateUTF, _datestart);            
            
        }
               
        
        boolean bShowPrev = false;
        String sRedirLinkPrev = "";
        String sPrevious = (String)dates_prev.get(_datestart);
                
        if (sPrevious !=null) {
            p("found prev = " + sPrevious);
            bShowPrev = true;
            
            sRedirLinkPrev = "echoClient5.htm?" + 
                        "ftype=" + _filetype + "&" + 
                        "days=" + _daysback + "&" + 
                        "foo=" + _keyin.replaceAll("\"","&quot;") + "&" + 
                        "view=" + sMode + "&" + 
                        "numobj=" + _numobj + "&" + 
                        "numcol=" + _numcol +"&" + 
                        "pw=" + _password +"&" + 
                        "screenSize=" + _screenSize +"&" + 
                        "date=" + sPrevious;            
        }
                

        Boolean firsttime = true;
        double nImgRatio = 1;
        int nCurrent = 0;
        Iterator It;        
        String sHeightNew = "200";

        int nCount2 = 0;

        timerAux.stop();

        logPerf(timestampPerf, "casss_server","read_row_list2", "AntesSwitch", timerAux.getElapsedTime()+"", _writeLog);
        Time[4] += timerAux.getElapsedTime();
        
        
        switch (nMode) {

            case 2:
                res.append("<table width='100%';border='0' cellpadding='0' cellspacing='4' style='table-layout:fixed;height:100;'>");
                res.append("<tr style='text-align:center; vertical-align:middle'>");
                for (int i = 0; i < nCount; i++) {
                    res.append("<td><a href ='pic/" + sNames[i] + "'><img border='0' href='pic/" + sNames[i] + "' src='pic/" + sNames[i] + "' width='100%'></a></td>");
                    nCount2++;
                    if (nCount2 > 3) {
                        res.append("</tr>");
                        res.append("<tr style='text-align:center; vertical-align:middle'>");
                        nCount2 = 0;
                    }
                    res.append("</tr></table>");
                }
                break;
            case 1:
                p("Mode1 - Slideshow. Count =" + nCount);

                p("nTokens:" + nTokens);

                p("nTokens2:" + nTokens);
                occurences_hash.sortByValue();
                int npid = Integer.parseInt(_pid);
                p("npid = '" + npid + "'");
                if (npid >= 0) {
                    It = occurences_hash.keySet().iterator();

                    boolean bFirst = true;
                    while (It.hasNext()) {
                        String sNamer = (String) It.next();
                        Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                        String sFileName = occurences_names.get(sNamer);
                        p(sNamer + " - " + nCount3 + " - " + sFileName);

                        String sPath = getSuperColumn2(sNamer, "paths", 2);

                        if (sPath.length() > 0) {
                            String sComma = "," + "\n";
                            if (bFirst) {
                                sComma = "";
                                bFirst = false;
                            }
                            res.append(sComma + "new Array(\"" + sPath + "\",\"1024\",\"768\",\"hello\"" + ")");                            
                        }                   
                    }    
                }
               
                break;
            case 3:
                p("Mode3!!!" + nCount);
                res.append("<table>");
                res.append("<tr style='text-align:center; vertical-align:middle'>");
                
                for (int i = 0; i < nCount; i++) {
                    res.append("<td><a href ='test4.php?foo=" + sNames[i] + "'><img border='0' href='pic/" + sNames[i] + "' src='pic/" + sNames[i] + "' width='100%'></a></td>");
                    nCount2++;
                    if (nCount2 > 7) {
                        res.append("</tr>");
                        res.append("<tr style='text-align:center; vertical-align:middle'>");
                        nCount2 = 0;
                    }
                }
                res.append("</tr></table>");
                break;
            case 4:
                DB db_attr = null;
                DB db_cp = null;
                ConcurrentNavigableMap<String,String> occurences_attr_r = null;
                ConcurrentNavigableMap<String,String> occurences_copies_r = null;
                
                if (dbmode.equals("p2p") || dbmode.equals("both")) {
                    lf.open_mapdb();

                    db_attr = lf.tx_attr.makeTx();                                   
                    db_cp = lf.tx_cp.makeTx();                                   
                    occurences_attr_r = db_attr.getTreeMap("attributes");
                    occurences_copies_r = db_cp.getTreeMap("numberofcopies");                    
                }
                
                //tile view
                
//------------------ 1-PrincipioSwitchAntesWhile                
                timerAux = new Stopwatch().start();
                
                p("Mode 4 (Tile view): " + nCount);
                                                               
                res.append("<script type=\"text/javascript\">\n");
                res.append("function updatecounters() {\n");                
                res.append("$('#n_alltime', window.parent.SIDEBAR.document).html('" + cnt_alltime + "');\n");                
                res.append("$('#n_past24h', window.parent.SIDEBAR.document).html('" + cnt_past24h + "');\n");                
                res.append("$('#n_past3d', window.parent.SIDEBAR.document).html('" + cnt_past3d + "');\n");                
                res.append("$('#n_past7d', window.parent.SIDEBAR.document).html('" + cnt_past7d + "');\n");                
                res.append("$('#n_past14d', window.parent.SIDEBAR.document).html('" + cnt_past14d + "');\n");                
                res.append("$('#n_past30d', window.parent.SIDEBAR.document).html('" + cnt_past30d + "');\n");                
                res.append("$('#n_past365d', window.parent.SIDEBAR.document).html('" + cnt_past365d + "');\n");                
                res.append("$('#n_total', window.parent.SIDEBAR.document).html('" + cnt_total + "');\n");                
                res.append("$('#n_photo', window.parent.SIDEBAR.document).html('" + cnt_photo + "');\n");                
                res.append("$('#n_music', window.parent.SIDEBAR.document).html('" + cnt_music + "');\n");                
                res.append("$('#n_video', window.parent.SIDEBAR.document).html('" + cnt_video + "');\n");                
                res.append("$('#n_docu', window.parent.SIDEBAR.document).html('" + cnt_office + "');\n");                
                res.append("$('#n_doc', window.parent.SIDEBAR.document).html('" + cnt_doc + "');\n");                
                res.append("$('#n_xls', window.parent.SIDEBAR.document).html('" + cnt_xls + "');\n");                
                res.append("$('#n_ppt', window.parent.SIDEBAR.document).html('" + cnt_ppt + "');\n");                
                res.append("$('#n_pdf', window.parent.SIDEBAR.document).html('" + cnt_pdf + "');\n");                
                res.append("$('#inputString', window.parent.SIDEBAR.document).val('" + _keyin + "');\n");  
                res.append("$('#date').val('" + _datestart + "');\n");  
                res.append("}\n");     
                res.append("bindEnterSearchBar();\n");                
                res.append("</script>\n");
                                                                                               
                res.append("<span class=\"affix\" style=\"z-index: 200; background-color:#EEEEEE;color:black;border-bottom:1px solid lightgrey\">");

                res.append("<form class=\"form-search\" style=\"z-index:100\" id=\"frm1\" action=\"echoClient5.htm\" method=\"get\" onsubmit=\"showLoading();\">");

                res.append("<INPUT TYPE=\"hidden\" NAME=\"ftype\" id=\"ftype\" VALUE=\"" + _filetype + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"days\" id=\"ndays\" VALUE=\"" + _daysback + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"foo\" VALUE=\"" + _key + "\">");
                res.append("<INPUT id=\"formView\" TYPE=\"hidden\" NAME=\"view\" VALUE=\"" + sMode + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"numobj\" VALUE=\"" + _numobj + "\">");
                res.append("<INPUT id=\"formCol\" TYPE=\"hidden\" NAME=\"numcol\" VALUE=\"" + _numcol + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"pw\" VALUE=\"" + _password + "\">");
                res.append("<input type=\"hidden\" name=\"dosubmit\" value=\"1\" id=\"dosubmit\"/>");
                res.append("<input type=\"hidden\" name=\"screenSize\" id=\"screenSize\"/>");
                                                
                String cnt_show = cnt_total;
                if (_filetype.equals(".photo")) cnt_show = cnt_photo;
                if (_filetype.equals(".music")) cnt_show = cnt_music;
                if (_filetype.equals(".video")) cnt_show = cnt_video;
                if (_filetype.equals(".document")) cnt_show = cnt_office;
                if (_filetype.equals(".doc")) cnt_show = cnt_doc;
                if (_filetype.equals(".xls")) cnt_show = cnt_xls;
                if (_filetype.equals(".ppt")) cnt_show = cnt_ppt;
                if (_filetype.equals(".pdf")) cnt_show = cnt_pdf;
                
                int n_cnt_size = 0;
                int n_numobj = 0;
                int n_cnt_show = 0;
                try {
                    n_cnt_size = Integer.parseInt(cnt_size);
                    n_numobj = Integer.parseInt(_numobj);
                    n_cnt_show = Integer.parseInt(cnt_show);
                } catch (Exception e) {
                    p("EXCEPTION: parsing int.");                    
                }
                if (n_cnt_size > n_numobj) cnt_size = _numobj;
                                                    
                                
                //res.append("<div style=\"font-size:10px;\"><br></div>");
                //res.append("&nbsp&nbsp");
                
                int nPages = 0;
                boolean bCont = true;
                String sStart = _datestart;
                while (bCont) {
                    String sPrev = (String)dates_prev.get(sStart);
                    if (sPrev != null) {
                        nPages++;
                        sStart = sPrev;
                    } else {
                        bCont = false;
                    }
                }
                int nStart = nPages * n_numobj;
                int nEnd = nStart + n_cnt_size;
                
                p("start = " + nStart);
                p("end = " + nEnd);
                p("cnt_show = " + n_cnt_show);
                p("_numobj = " + _numobj);
                                
                if ((nEnd - nStart - 1) > n_numobj) {
                    nEnd = n_numobj;
                    bShowNext = true;
                }
                
                if (n_cnt_show > n_numobj) {
                    bShowNext = true;
                    nEnd = nStart + n_numobj;
                }
                
               
//affix7 = navigation < > 
                
                res.append("<span class=\"affix7\">");                                     
                res.append("&nbsp");
                
                if (bShowPrev) {
                    res.append("<INPUT TYPE=button value=\" < \" onclick=\"showLoading(); golink('" + sRedirLinkPrev + "','" + _key + "',1)\"/>");
                }
                               
                bShowNext = nEnd < n_cnt_show;
                if (bShowNext) {
                    //res.append("<div class=\"\">");
                    res.append("<INPUT TYPE=button value=\" > \" onclick=\"showLoading(); golink('" + sRedirLink + "','" + _key + "',1)\"/>");
                    //res.append("</div>");                
                }                    
                
                res.append("</span>");
                
                
//affix3 = combo boxes (#columns, #results)
                
                res.append("<span class=\"affix3\">");
                
                res.append("<select class=span2 style=\"display:none;\" onchange=\"searchclick();\" name=\"view\" id=\"inputView\">");
                    res.append("<option value=\"detail\">Detailed View</option>");
                    res.append("<option value=\"show\">Slideshow</option>");
                    res.append("<option value=\"show2\">Slideshow2</option>");
                    res.append("<option value=\"tile\" selected>Tile View</option>");
                    res.append("<option value=\"polar\">Polar View</option>");
                    res.append("<option value=\"caro\">Carousel View</option>");
                res.append("</select>");

                //res.append("&nbsp&nbsp&nbsp&nbsp");
                //res.append("<i class=\"icon-th\"></i>&nbsp#Columns:&nbsp");
                res.append("&nbsp#Columns:&nbsp");
                
                res.append("<select style=\"top:5px; font-size:10px;width:45px;\" onchange=\"searchclick();\" name=\"numcol\" id=\"inputNumCol\">");
                    res.append("<option value=\"1\">1</option>");
                    res.append("<option value=\"2\">2</option>");
                    res.append("<option value=\"3\">3</option>");
                    res.append("<option value=\"4\">4</option>");
                    res.append("<option value=\"5\">5</option>");
                    res.append("<option value=\"7\">7</option>");
                    res.append("<option value=\"9\">9</option>");
                res.append("</select>");
                
                res.append("&nbsp&nbsp");
                //res.append("<i class=\"icon-book\"></i>&nbspResults:&nbsp");
                res.append("&nbspResults:&nbsp");
                
                res.append("<select style=\"font-size:10px;width:60px;\" class=span1 onchange=\"searchclick();\" name=\"numobj\" id=\"inputNumObj\">");
                    res.append("<option value=\"25\">25</option>");
                    res.append("<option value=\"50\">50</option>");
                    res.append("<option value=\"100\">100</option>");
                    res.append("<option value=\"250\">250</option>");
                    res.append("<option value=\"500\">500</option>");
                res.append("</select>");

                res.append("</span>");
                                                                           

// search box
                

                //res.append("<b>Search:&nbsp</b>");
                
                //res.append("<br><br>");

                res.append("<span style=\"top:5px; margin-left: 1em\" class=\"\">");                              
                res.append("<div style=\"font-size:11px;margin-right: 1em;   width: 60%\" class=\"input-append\">");  
                
                res.append("<input style=\"font-size:9px; margin-left: 1em; \" type=\"checkbox\" id=\"checkk\" onClick=\"togglesel(this.checked);\"/>");
                res.append("<label onclick=\"var chek = document.getElementById('checkk'); togglesel(chek.checked);\">");
                res.append("&nbspSelect All&nbsp&nbsp");
                res.append("</label>");                
                
                res.append("<div style=\"font-size:11px; top:5px; position:relative; width:40%\" class=\"input-append\">");
                //res.append("<input type=\"text\" class=\"search-query span4\" name=\"foo\" id=\"inputString\" onKeypress=\"\" onkeyup=\"lookup(this.value);\" autocomplete=\"off\" placeholder=\"Searchxxxx\"/>");
                  res.append("<INPUT style=\"font-size:11px; position: relative; width: 40% \"  TYPE=\"text\" class=\"search-query\" NAME=\"tag\" ID=\"tag-search\" oninput=\"$('#tag').val($('#tag-search').val());\"  autocomplete=\"off\" placeholder=\"Enter tags here.\"/>");
                //res.append("<INPUT style=\"font-size:11px;\" TYPE=\"submit\" NAME=\"hide selected\" VALUE=\"Apply\">");
                  res.append("<button style=\"font-size:11px;\" type=button class=\"btn btn-primary\" onClick=\"submit_tag();\"><i class=\"icon-white icon-tags  \"></i>&nbspApply </button>");
                //res.append("<button class=\"btn btn-primary\" onclick=\"searchclick();clearFilters(); clearFiltersVar();\"><i class=\"icon-search icon-white\"></i>&nbspSearch</button>");
                res.append("</div>");
                  
                res.append("<script type=\"text/javascript\">");
                res.append("$('#tag-search').keydown(function (e) {");
                res.append("if (e.keyCode == 13) {");
                res.append(" e.preventDefault();");
                res.append("$('#frm2').submit();");
                res.append("}");
                res.append("}); ");
                res.append("</script>");
                  
                res.append("<div class=\"input-append\" style=\"top:5px; margin;left: 1em; font-size:13px; position: relative; width: 65% \">");
                res.append("<input type=\"text\" class=\"search-query\" style=\"margin;left: 1em; font-size:13px; position: relative; width: 65% \"  name =\"foo\" id=\"inputString\" onKeypress=\"\" onkeyup=\"lookup(this.value);\" autocomplete=\"off\" placeholder=\"Search\"/>");
                //res.append("<span class=\"add-on btn btn-primary\" style=\"top:5px;\"><i class=\"icon-search icon-white\" onclick=\"searchclick();\"></i></span>");
                //res.append("<div class=\"input-append-btn\">");
                res.append("<button class=\"btn btn-primary\" onclick=\"searchclick();clearFilters(); clearFiltersVar();\"><i class=\"icon-search icon-white\" ></i>&nbspSearch</button>");
                res.append("</div>");
                
                res.append("<div>");
                res.append("<div style=\"width: 210px; float:left;\">&nbsp</div>");
                res.append("<div class=\"suggestionsBox\" id=\"suggestions\" style=\"font-size:13px;float:left;margin-left: 10%; width: 40%; display: none; overflow: visible\">");
                        res.append("<div class=\"suggestionList\" id=\"autoSuggestionsList\">");
                        res.append("&nbsp;");
                    res.append("</div>");
                res.append("</div>"); 
                
                res.append("</div>");

                res.append("<div class=\"affix6 pull-center\">");
                res.append("<br>Displaying " + (nStart+1) + "-" + Math.min(nEnd,n_cnt_show) + " of " + n_cnt_show + " results.");
                res.append("</div>");
                
                res.append("</div>");


                        

//affix6 = displaying
                
                //res.append("<span class=\"affix6 pull-center\">");
                //res.append("<br>");
                //res.append("Displaying " + (nStart+1) + "-" + Math.min(nEnd,n_cnt_show) + " of " + n_cnt_show);
                //res.append("</span>");
                
                //res.append("<br>");

//affix4 = view buttons
                
                res.append("<span class=\"affix4\">");
                
                //res.append("<i class=\"icon-eye-open\"></i>&nbspView Mode:&nbsp&nbsp");                              
                
                res.append("<button type=\"submit\" class=\"btn btn-primary\" onclick=\"view_tile();\">");
                res.append("<i class=\"icon-th icon-white\"></i>");
                res.append("</button>");
                                               
                res.append("<button type=\"submit\" class=\"btn\" onclick=\"view_detail();\">");
                res.append("<i class=\"icon-list\"></i>");
                res.append("</button>");

                res.append("<button type=\"submit\" class=\"btn\" onclick=\"view_show();\">");
                res.append("<i class=\"icon-picture\"></i>");
                res.append("</button>");

                res.append("</span>");
                
                
                res.append("</span>");

                res.append("</form>");
                res.append("</span>");                
                 
                //res.append("<br><br><br>");

                res.append("<form class=\"form-search\"   id=\"frm2\" action=\"bulker.php\" method=get autocomplete=\"off\" onsubmit=\"showLoading();\" >");
                
                res.append("<INPUT style=\"font-size:11px; position: relative; width: 20% \"  TYPE=\"hidden\" class=\"search-query\" NAME=\"tag\" ID=\"tag\" autocomplete=\"off\" placeholder=\"Enter tags here.\"/>");
                
                
//affix2 = view buttons               
                          
                
                res.append("<span class=\"affix2\">");
                
                res.append("<INPUT TYPE=\"hidden\" NAME=\"ftype\" id=\"ftype\" VALUE=\"" + _filetype + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"days\" id=\"ndays\" VALUE=\"" + _daysback + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"foo\" VALUE=\"" + _key + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"view\" VALUE=\"" + sMode + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"numobj\" VALUE=\"" + _numobj + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"numcol\" VALUE=\"" + _numcol + "\">");
                res.append("<INPUT TYPE=\"hidden\" ID=\"date\" NAME=\"date\" VALUE=\"" + URLEncoder.encode(sFirst, "UTF-8") + "\">");
                res.append("<INPUT TYPE=\"hidden\" ID=\"DeleteTag\" NAME=\"DeleteTag\" VALUE=\"\">");
                res.append("<input type=\"hidden\" name=\"screenSize\" id=\"screenSize\"/>");
                
                //res.append("<input style=\"font-size:9px;\" type=\"button\" onClick=\"togglesel(this.checked);\" value=\"Select All\"/>");

                //res.append("<i class=\"icon-tags\"></i><b></b>");

//                res.append("<div class=\"input-append\">");  
//                res.append("<input type=\"text\" class=\"search-query span4\" name =\"foo\" id=\"inputString\" onKeypress=\"\" onkeyup=\"lookup(this.value);\" autocomplete=\"off\" placeholder=\"Search\"/>");
//                //res.append("<span class=\"add-on btn btn-primary\" style=\"top:5px;\"><i class=\"icon-search icon-white\" onclick=\"searchclick();\"></i></span>");
//                //res.append("<div class=\"input-append-btn\">");
//                res.append("<button class=\"btn btn-primary\" onclick=\"searchclick();clearFilters(); clearFiltersVar();\"><i class=\"icon-search icon-white\" ></i>&nbspSearch</button>");
//                res.append("</div>");
                


                //res.append("<INPUT TYPE=\"submit\" style=\"position:absolute; height:0px;width:0px;border:none;padding:0px;\" NAME=\"hide selected\" VALUE=\"Apply\">");
                //res.append("<input type=\"button\" onClick=\"togglechk2(this.checked);\" value=\"Clear\"/>");
                res.append("<input id=\"chk0\" style=\"display:none\" type=\"checkbox\" onClick=\"togglechk(this.checked);\">");
                res.append("&nbsp");
                res.append("</span>");  
                                

                res.append("<br><br><br><br>");
                
                res.append("<div id=\"resultElements\">");

                
                ArrayList<ResultElement> resultElements = new ArrayList<ResultElement>();
                //p("nTokens:" + nTokens);
                //p("nTokens2:" + nTokens);
                float widthAcum = 0;
                int currentRow = 1;
                int currentColumn = 0;
                
                
                float screenWidth;
                try {
                    screenWidth = Float.parseFloat(_screenSize);
                }
                catch(Exception e){
                    screenWidth = 1000;
                }
                
                occurences_hash.sortByValue();

                
                firsttime = true;
                nCurrent = 0;
                It = occurences_hash.keySet().iterator();
                
                set_deadnodes();
                
                timerAux.stop();
                logPerf(timestampPerf, "casss_server","read_row_list2", "1-PrincipioSwitchAntesWhile", timerAux.getElapsedTime()+"", _writeLog);
                Time[5] += timerAux.getElapsedTime();
                

                int xn = 0;                
                boolean bContinue = true;
                while (It.hasNext() && bContinue) {     
//------------------ 3-Principio While                    
                    timerAux = new Stopwatch().start();

                    xn++;
                    //p("processing: " + xn);
                    
                    String sNamer = "";
                    Integer nCount3 = 0;
                    synchronized (occurences_hash) {
                        sNamer = (String) It.next();
                        nCount3 = (Integer) occurences_hash.get(sNamer);                    
                    }
                                        
                    if (!nCount3.equals(nTokens) && _key.indexOf("&") < 0) {
                        continue;
                    }
                    if (xn >= Integer.parseInt(_numobj)) {
                        bContinue = false;                                                
                    }
                    
                    String sFileName = "";
                    synchronized (occurences_names) {
                        sFileName = occurences_names.get(sNamer);                        
                    }
                    String sFileNameOri = sFileName;
                    
                    //p(sNamer + " - " + nCount3 + " - " + sFileName);
                    if (firsttime) {
                        nCurrent = nCount3;
                        firsttime = false;
                    }

                    if (!nCount3.equals(nCurrent)) {
                        nCurrent = nCount3;
                    }

                    String sPic = sNamer + ".jpg";
                   
                    
                    boolean bVector = false;
                    String sVector = "";

                                    
                    timerAux.stop();
                    logPerf(timestampPerf, "casss_server","read_row_list2", "3-PrincipioWhile", timerAux.getElapsedTime()+"", _writeLog);
                    Time[6] += timerAux.getElapsedTime();

//------------------ 4-IsMusic                    
                    timerAux = new Stopwatch().start();

                    if (is_music(sFileName)) {
                        String sFile = _root + File.separatorChar + "cass" + File.separatorChar + "pic" + File.separatorChar + sPic;
                        //p("*** looking for file: " + sFile);
                        File f = new File(sFile);

                        if (!f.exists()) {
                            sPic = get_thumb(sFileName);
                            bVector=true;
                            sVector = get_vector(sFileName, "100");
                        }
                        //p("sPic: " + sPic);
                        
                        //String sSongTitle = get_row_attribute(keyspace, "Standard1", sNamer, "title");
                        //String sSongArtist = get_row_attribute(keyspace, "Standard1", sNamer, "artist");
                        
                        //*08/20
                        //String sSongTitleArtist = get_row_attributes(keyspace, "Standard1", sNamer, "title", "artist");                        
                        //String sSongTitle = sSongTitleArtist.substring(0, sSongTitleArtist.indexOf(","));
                        //String sSongArtist = sSongTitleArtist.substring(sSongTitleArtist.indexOf(",")+1,sSongTitleArtist.length());
                        
                       String sAttr = "";
                       String sSongTitle = "";
                       String sSongArtist = "";
                       String sDate = "";
                       sAttr =  occurences_attr_r.get(sNamer);                       
                       try {
                           if (sAttr!= null && sAttr.length() > 0) {
                                 delimiters = ",";
                                 st = new StringTokenizer(sAttr, delimiters, true);
                                 
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
                        
                        if (sSongTitle.length() > 40) {
                            sSongTitle = sSongTitle.substring(0, 39);
                        }
                        if (sSongArtist.length() > 40) {
                            sSongArtist = sSongArtist.substring(0, 39);
                        }
                        if (sSongTitle.length() > 0) {
                            if (sSongArtist.length() > 0) {
                                sFileName = sSongTitle + " <br> " + sSongArtist;    
                            } else {
                                sFileName = sSongTitle;
                            }
                        } else {
                            if (sSongArtist.length() > 0) {
                                sFileName += " <br> " + sSongArtist;
                            }
                            if (sFileName.length() > 40) {
                                sFileName = sFileName.substring(0, 39);
                            }
                        }                        
                    }
                    
                                    
                    timerAux.stop();
                    logPerf(timestampPerf, "casss_server","read_row_list2", "4-IsMusic", timerAux.getElapsedTime()+"", _writeLog);
                    Time[7] += timerAux.getElapsedTime();

                    
//------------------ 5-IsPhoto                    
                    timerAux = new Stopwatch().start();

                    if (!is_photo(sFileNameOri) && !is_music(sFileNameOri)) {
                        bVector = true;
                        sVector = get_vector(sFileNameOri, "100");
                    }
                    
                    if (is_pdf(sFileNameOri)) {
                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
                        if (fh.exists()) {
                            //p("PDF exist:" + fh.getCanonicalPath());
                            bVector = false;
                        } else {
                            p("PDF Not exist:" + fh.getCanonicalPath());
                        }
                    }
                    
                    timerAux.stop();
                    logPerf(timestampPerf, "casss_server","read_row_list2", "5-IsPhoto", timerAux.getElapsedTime()+"", _writeLog);
                    Time[8] += timerAux.getElapsedTime();

                    
                    String srcPic="pic/"+sPic;
                                     
//------------------ 5b-IsPhoto                    
                    if(_dobase64 && (is_photo(sFileNameOri) || (is_pdf(sFileNameOri) && !bVector))){
                        timerAux = new Stopwatch().start();
                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
                        if (fh.exists()) { 
                           File fh64= new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".alt64");
                           if(fh64.exists()){
                               //Si ya existe la version base64 la leo
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
                                srcPic="data:image/jpg;base64,"+out.toString();
                                //p("Vista Tile:Se carga imagen desde pic folder");
                           } else{
                                //Si existe en pic armo el base64
                               FileInputStream is=new FileInputStream(fh);
                               ByteArrayOutputStream out=new ByteArrayOutputStream();
                               FileWriter writer=new FileWriter(fh64);
                                    
                               byte[] buf =new byte[1024*1024];
                               try {
                                    int n;

                                    while ((n = is.read(buf)) > 0) {
                                        out.write(buf, 0, n);
                                    }
                                     srcPic= Base64.encodeToString(out.toByteArray(), false);
                                    writer.write(srcPic.toCharArray());
                                } finally {
                                     is.close();
                                     writer.close();
                                     out.close();
                                }          
                                srcPic="data:image/jpg;base64,"+ srcPic;
                                //p("Vista Tile:Se genera imagen base64");
                           }
                            
                        }else{
                            //p("Vista Tile:No se encuentra imagen en PIC, se deja enlace a la imagen");
                        }
                    timerAux.stop();
                    logPerf(timestampPerf, "casss_server","read_row_list2", "6-IsPhotoB64", timerAux.getElapsedTime()+"", _writeLog);   
                    Time[9] += timerAux.getElapsedTime();
                    }
                    
//------------------ 7-checkifshow                    
                    timerAux = new Stopwatch().start();

                    boolean bShow;
                    if (_filetype.equals(".all")) {
                        bShow = true;
                    } else {
                        String ftype2 = _filetype.trim();
                        bShow = checkifshow(sFileNameOri, ftype2);
                    }

                    timerAux.stop();
                    logPerf(timestampPerf, "casss_server","read_row_list2", "7-CheckIfShow", timerAux.getElapsedTime()+"", _writeLog);
                    Time[10] += timerAux.getElapsedTime();

                    
                    if (bShow) {
//------------------ 8-bShowInit_1
                       timerAux = new Stopwatch().start();
                
                        //String sHeight = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                        //String sWidth = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");
                        
                        //optimization 8/2
                        //String sHeightWidth = get_row_attributes(keyspace, "Standard1", sNamer, "img_height", "img_width");
                        //String sHeight = sHeightWidth.substring(0, sHeightWidth.indexOf(","));
                        //String sWidth = sHeightWidth.substring(sHeightWidth.indexOf(",")+1,sHeightWidth.length());
                       
                       String sHeightDiv = "200";
                       String sWidthDiv = "200";
                       String sHeightOrig = "200";
                       String sWidthOrig = "200";
                       String sWidthThumb = "200";
                       String sHeightThumb = "200";     
                       String sWidthAux = "";
                       String sHeightAux = "";
                       
                       String sDate = "";
                       //String sAttr = LocalFuncs.occurences_attr.get(sNamer);
                       String sAttr = "";
//                       for(String s: Bind.findSecondaryKeys(LocalFuncs.occurences_attr, sNamer)){                        
//                            sAttr = s;
//                       }
                       
                       if (dbmode.equals("p2p") || dbmode.equals("both")) {
                            synchronized(occurences_attr_r) {
                                try {
                                    sAttr = occurences_attr_r.get(sNamer);                                                           
                                } catch (IllegalAccessError e) {
                                    log("WARNING: IllegalAccess error attr_r: " + sNamer);
                                    lf.open_mapdb();                
                                    db_cp = lf.tx_attr.makeTx();                                   
                                    occurences_attr_r = db_cp.getTreeMap("attributes");
                                    sAttr = occurences_attr_r.get(sNamer);                                                           
                                }
                            }                           
                       }                                               
                       
                       try {                    
                           if (sAttr!= null && sAttr.length() > 0) {
                                 delimiters = ",";
                                 st = new StringTokenizer(sAttr, delimiters, true);
                                 
                                 if (st.countTokens() == 5)  {
                                     if (is_music(sFileNameOri)){
                                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg"); 
                                        if (fh.exists()) { 
                                            BufferedImage bimg = ImageIO.read(fh);
                                            sHeightDiv = ""+bimg.getHeight();
                                            sWidthDiv = ""+bimg.getWidth();
                                        }
                                     }else {
                                        sDate = st.nextToken();    //  date 
                                        st.nextToken();            //  ,
                                        sHeightDiv = st.nextToken();  //  height
                                        st.nextToken();            //  ,
                                        sWidthDiv = st.nextToken();   //  width    
                                     }
                                    
                                 } 
                                 else {
                                     if (is_pdf(sFileNameOri)){
                                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg"); 
                                        if (fh.exists()) { 
                                            BufferedImage bimg = ImageIO.read(fh);
                                            sHeightDiv = ""+bimg.getHeight();
                                            sWidthDiv = ""+bimg.getWidth();
                                        }
                                     }
                                     else {
                                        sDate = st.nextToken();  
                                     }
                                 }
                                 //p(sDate + " " + sHeight + " " + sWidth);
                           } else {
                               File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg"); 
                                if (fh.exists()) { 
                                    BufferedImage bimg = ImageIO.read(fh);
                                    sHeightDiv = ""+bimg.getHeight();
                                    sWidthDiv = ""+bimg.getWidth();
                                }
                           }
                       } catch (Exception e) {
                           p("*** WARNING: Exception parsing: " + sNamer + " = '" + sAttr + "'");
                           e.printStackTrace();
                       }
                                                                    
           
                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "8-bShowInit_1", timerAux.getElapsedTime()+"", _writeLog);
                        Time[11] += timerAux.getElapsedTime();


//------------------ 10-bShowInit_3                        
                        timerAux = new Stopwatch().start();              
                        
                        //p("height new = " + sHeightNew);
                        //p("width new = " + sWidthNew);
                        
                        
                        //res.append("<td valign='top' align='center' class='jquerycursorpointer' style='cursor:pointer;' id='box"+sNamer+"' sel='N'><div><div class='jquerycursorpointer' style='cursor:pointer;' ck='1' sNamer='"+sNamer+"'><table id='img"+sNamer+"' cellspacing=0 onmouseout='hideButtonsTile(\""+sNamer+ "\", "+(bVector? "true" : "false")+");' onmouseover='showButtonsTile(\""+sNamer+ "\", "+(bVector? "true" : "false")+");'>");
                        
                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "10-bShowInit_3", timerAux.getElapsedTime()+"", _writeLog);
                        Time[13] += timerAux.getElapsedTime();

//------------------ 11-bShowInit_4           
                        timerAux = new Stopwatch().start(); 

                        
                        //
                                 
                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "11-bShowInit_4", timerAux.getElapsedTime()+"", _writeLog);
                        Time[14] += timerAux.getElapsedTime();

//------------------ 12-bShowInit_5                        
                        timerAux = new Stopwatch().start();              
                        
                        //String sDate = get_row_attribute(keyspace, "Standard1", sNamer, "date_modified");

                        int nError = 0;
                       
//                        try {
//                            sPlayURL = get_row_attribute(keyspace, "Standard1", sNamer, "PlayLink3");
//                        } catch (Exception ex) {
//                            p("   [Ce]");
//                            nError += 1;
//                        }
                        
          
                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "12-bShowInit_5", timerAux.getElapsedTime()+"", _writeLog);
                        Time[15] += timerAux.getElapsedTime();
                        
//------------------ 13-getPlayLink                     
                        timerAux = new Stopwatch().start();
                
                
                        sURLPack sURLpack = new sURLPack();
                        boolean bHaveServerLink = false;
                        
                        //p("   [E]");
                        String sOpenURL = sURLpack.sOpenURL;     
                        String sFolderURL = sURLpack.sFolderURL;
                        String sViewURL = sURLpack.sViewURL;
                        
                        //p("openurl = " + sOpenURL);
                        //p("folderurl = " + sFolderURL);

                        //InetAddress clientIP = InetAddress.getLocalHost();
//                        InetAddress clientIP = getLocalAddress();
//                        String sLocalIP = "127.0.0.1";
//                        if (clientIP != null) {
//                            sLocalIP = clientIP.getHostAddress();;                            
//                        }

                        String sPlayURL = "";                  
                        if (nError < 1 || _cloudhosted) {
                            //PLAY LINK
                            sPlayURL = gen_play_link(sPlayURL, sLocalIP, _key);
                        } else {
                            sPlayURL = "ERROR";
                        }
                                      
                        
                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "13-GenPlayLink", timerAux.getElapsedTime()+"", _writeLog);
                        Time[16] += timerAux.getElapsedTime();

//------------------ 14-getHashes
                        timerAux = new Stopwatch().start();
                        String _hashkey = "hashesm";
                        String _clientip = _host;                       
                        
                        ///TODO***
                        String sHashLink = "";
                        if (dbmode.equals("cass")) {
                            sHashLink =  getSuperColumn(sNamer, _hashkey, 1, _password, _clientip, sMode, _daysback, _numcol, _numobj, sLast, _filetype);                            
                        } else {                            
                            sHashLink =  lf.getHashes(sNamer, _hashkey, 1, _password, _clientip, sMode, _daysback, _numcol, _numobj, sLast, _filetype);                            
                        }

                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "14-getHashes", timerAux.getElapsedTime()+"", _writeLog);
                        Time[17] += timerAux.getElapsedTime();


                        sPlayURL=sPlayURL.replace("id_","id='serverlnk"+sNamer+"' class='jqueryhidden'");

//------------------ 15-getNumberofCopies
                        timerAux = new Stopwatch().start();

                        String sCopyInfo = null;
//                        for(String s: Bind.findSecondaryKeys(occurences_copies, sNamer)){                        
//                            sCopyInfo = s;
//                        }
                        
                        //if the client is local, make its IP address equal to server IP address
                        //p(_clientIP + " " + sLocalIP);
                        if (_clientIP.equals("0:0:0:0:0:0:0:1") || 
                                _clientIP.equals("0:0:0:0:0:0:0:1%0") ||
                                _clientIP.equals("127.0.0.1")) {
                            _clientIP = sLocalIP;
                        }
                        
                        if (dbmode.equals("p2p") || dbmode.equals("both")) {
                            synchronized (occurences_copies_r) {                            
                               if (_clientIP.equals(sLocalIP)) {
                                   try {
                                       sCopyInfo = occurences_copies_r.get(sNamer);
                                   } catch (IllegalAccessError e) {
                                       log("WARNING: IllegalAccess error copies_r: " + sNamer);
                                       lf.open_mapdb();                
                                       db_cp = lf.tx_cp.makeTx();                                   
                                       occurences_copies_r = db_cp.getTreeMap("numberofcopies");
                                       sCopyInfo = occurences_copies_r.get(sNamer);
                                   }
                                   if (sCopyInfo == null) {
                                       sCopyInfo = "1,1,1";
                                   }                                
                               } else {
                                   //p("REMOTE CLIENT - gnp");
                                   sCopyInfo = lf.getNumberofCopies("paths", sNamer, _clientIP,sLocalIP, false);
                               }
                           }   
                        } else {
                            sCopyInfo = "1,1,1";
                        }
                        
                        
                        if (sCopyInfo.equals("0,0,0")) {
                            sCopyInfo = lf.getNumberofCopies("paths", sNamer, _clientIP,sLocalIP, false);   
                            p("****NUMCOPIES2****: " + sNamer + " = " + sCopyInfo);
                        }
                        
                        int nCopies = 0;
                        int nNodes = 0;
                        int nCopiesLocal = 0;

                        try {
                            if (sCopyInfo != null && !sCopyInfo.equals("ERROR")) {                           

                            String sCopies = "";
                            String sNodes = "";
                            String sCopiesLocal = "";
                            delimiters = ",";
                            st = new StringTokenizer(sCopyInfo, delimiters, true);
                            while (st.hasMoreTokens()) {
                                sCopies = st.nextToken();
                                st.nextToken();
                                sNodes = st.nextToken();
                                st.nextToken();
                                sCopiesLocal = st.nextToken();
                            }

                            nCopies = Integer.parseInt(sCopies);                        
                            nNodes = Integer.parseInt(sNodes); 
                            nCopiesLocal = Integer.parseInt(sCopiesLocal); 
                        }
                            
                        } catch (Exception e) {
                            StringWriter sWriter = new StringWriter();
                            e.printStackTrace(new PrintWriter(sWriter));
                            logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
                        }
                        
                        
                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "15-GetNumberOfCopies", timerAux.getElapsedTime()+"", _writeLog);
                        Time[18] += timerAux.getElapsedTime();

//------------------ 16-AfterGetNumberofCopies                        
                        timerAux = new Stopwatch().start();

                
                        
                        
                        //p("There are " + nCopies + " copies of file " + sNamer);
                        //p("There are " + nNodes + " nodes that has a copy of file " + sNamer);
                        //p("There are " + nCopiesLocal + " local copies of file " + sNamer);
                        
                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "16-AfterGetNumberOfCopies", timerAux.getElapsedTime()+"", _writeLog);
                        Time[19] += timerAux.getElapsedTime();
                                                
//------------------ 17-isPhoto2                        
                        timerAux = new Stopwatch().start();
                        
                        String sViewURL2 = "";
                        String sOpenURL2 = "";
                        String sSendURL2 = "";
                        String sFolderURL2 = "";
                        
                        if(is_photo(sFileNameOri)) {
                            sWidthOrig = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");
                            sHeightOrig = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                        }
                        else {
                            sWidthOrig = sWidthDiv;
                            sHeightOrig = sHeightDiv;
                        }
                        if (sPlayURL.length() > 0) {
                            //If there is a copy of file in server, show Send Link
                            sSendURL2 = "<INPUT TYPE=button value=\"Send 2\" id_ onclick=\"golink('sendfile.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"','" + _key + "',1,1);\" />";                                                             
                        }
                        
                        //p("openurl = " + sOpenURL);
                        //p("There are " + nCopies + " copies of file " + sNamer);

                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "17-isPhoto2", timerAux.getElapsedTime()+"", _writeLog);
                        Time[20] += timerAux.getElapsedTime();
                        
//------------------ 18-GetLinks                        
                        timerAux = new Stopwatch().start();
            
                        if (nCopies > 0) { 
                            
                            if (_cloudhosted) {
                                

                                //VIEW LINK
                                sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false); 
                                
                                sOpenURL = sURLpack.sOpenURL;     
                                sFolderURL = sURLpack.sFolderURL;
                                sViewURL = sURLpack.sViewURL;
                                
                                p("**cloud*********** viewurl = " + sViewURL);
                                
                                sViewURL2 = gen_view_link2(_host, _port, sViewURL, bHaveServerLink, _cloudhosted, sNamer, sURLpack, sFileNameOri, _key, nImgRatio, sLocalIP, false);

                            } else {
                                //VIEW
                                String sButtonText = "Save";
                                String sGoLink = "golink";
                                if (is_music(sFileNameOri) || is_movie(sFileNameOri)) sButtonText = "Play";
                                if (is_photo(sFileNameOri) || si_inline(sFileNameOri)) sButtonText = "View";                                
                                if (is_music(sFileNameOri)) sGoLink = "golink_music";

                                //sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, false); 
                                                                
                                if(is_photo(sFileNameOri) || is_movie(sFileNameOri) || is_musicToPlay(sFileNameOri) || si_inline(sFileNameOri)){

                                    //sViewURL2 = ("<button class=\"btn btn-primary\" onclick=\"golink('" + sURLpack.sViewURL + "','" + _key + "',1,1);\">");
                                    //sViewURL2 += sButtonText;
                                    //sViewURL2 += "<i class=\"icon-eye-open icon-white\"></i>";
                                    //sViewURL2 += "</button>"; 
                                    if (is_photo(sFileNameOri)) {
                                        sViewURL = "/cass/viewimg2.htm?sNamer=" + sNamer;
                                    } else {
                                        sViewURL = "/cass/getfile.fn?sNamer=" + sNamer;
                                    }
                                    sViewURL2 = "<BUTTON style=\"\" type=\"button\" class=\"buttonElement\" id_ onclick=\"" + sGoLink + "('" + sViewURL + "','" + _key + "',1,1);\">" + "<i class=\"icon-eye-open icon-white\"></i>&nbsp;" + sButtonText + "</BUTTON>";                             

                                    //sViewURL2 = "<INPUT TYPE=button value=\"" + sButtonText + "\" id_ onclick=\"golink('" + sURLpack.sViewURL + "','" + _key + "',1,1);\">" + "<i class=\"icon-eye-open icon-white\"></i>" + "</INPUT>";                             
                                    if (is_photo(sFileNameOri)) {
                                        //sSendURL2 = "<INPUT TYPE=button value=\"Send\" id_ onclick=\"golink('" + sURLpack.sSendURL + "','" + _key + "',1,1);\" />";                                                                 
                                        String sSendURL = "/cass/sendimg2.htm?sNamer=" + sNamer;
                                        sSendURL2 = "<BUTTON style=\"\" type=\"button\" class=\"buttonElement\" id_ onclick=\"golink('" + sSendURL + "','" + _key + "',1,1);\">" + "<i class=\"icon-envelope icon-white\"></i>&nbsp;" + "Send" + "</BUTTON>";                             
                                    }
                                } else {
                                    sViewURL2 = "<BUTTON style=\"\" type=\"button\" class=\"buttonElement\" id_ onclick=\"newTabCommand('getfile.fn?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\"/>" + "<i class=\"icon-file icon-white\"></i>&nbsp;" + sButtonText + "</BUTTON>";                                                                 
                                }                                
                            }
                                                    
                            if (nCopiesLocal > 0) {
                                //OPEN & FOLDER
                                sOpenURL2 = "<INPUT TYPE=button value=\"Open\" id_ onclick=\"silentCommand('openfile.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\" />";                                                                 
                                sFolderURL2= "<INPUT TYPE=button value=\"Folder\" id_ onclick=\"silentCommand('openfolder.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\" />";                             
                                
                                sOpenURL2 = "<BUTTON TYPE=button style=\" type=\"button\" class=\"buttonElement\" id_ onclick=\"silentCommand('openfile.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\">" + "<i class=\"icon-download-alt icon-white\"></i>&nbsp;" + "Open" + "</BUTTON>";                             
                                sFolderURL2 = "<BUTTON TYPE=button style=\" type=\"button\" class=\"buttonElement\" id_ onclick=\"silentCommand('openfolder.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\">" + "<i class=\"icon-folder-open icon-white\"></i>&nbsp;" + "Folder" + "</BUTTON>";                             
                                //sOpenURL2 = "<BUTTON TYPE=button style=\"font-size:13px; type=\"button\" class=\"btn btn-primary\" id_ onclick=\"golink('" + sURLpack.sOpenURL + "','" + _key + "',1,1);\">" + "<i class=\"icon-download-alt icon-white\"></i>" + "Open" + "</BUTTON>";                             
                                //sFolderURL2 = "<BUTTON TYPE=button style=\"font-size:13px; type=\"button\" class=\"btn btn-primary\" id_ onclick=\"golink('" + sURLpack.sFolderURL + "','" + _key + "',1,1);\">" + "<i class=\"icon-folder-open icon-white\"></i>" + "Folder" + "</BUTTON>";                             
                            } else {
                                sOpenURL2 = "<BUTTON TYPE=button style=\" type=\"button\" class=\"buttonElement\" id_ onclick=\"silentCommand('openfile.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\">" + "<i class=\"icon-download-alt icon-white\"></i>&nbsp;" + "Remote" + "</BUTTON>";                             
                                sFolderURL2 = "";                                
                            }
                            
                        }
                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "18-GetLinks", timerAux.getElapsedTime()+"", _writeLog);
                        Time[21] += timerAux.getElapsedTime();

                        
//------------------ 19-UltimaParteW                                        
                        timerAux = new Stopwatch().start();
                        /*
                        int lineasEtiquetas=0;    
                        if(sHashLink.trim().length()>0){     
                                //Cambio la estructura de la tabla generada para que quede en 1 columna
                                int count=0;
                                StringBuilder sHashLinkAux=new StringBuilder(sHashLink);
                                boolean entre=false;
                                int i=0;
                                while(sHashLinkAux.substring(i).contains("</td>")){
                                    i=sHashLinkAux.indexOf("</td>",i)+"</td>".length();

                                    count++;
                                  
                                    if(count==1){
                                        count=0;
                                        entre=false;
                                        sHashLinkAux.insert(i,"</tr><tr>");
                                        lineasEtiquetas++;
                                    }else{
                                        entre=true;
                                        lineasEtiquetas++;
                                    }
                                }
                                
                                if(entre ){
                                    sHashLinkAux.insert(sHashLinkAux.toString().indexOf("</table>"),"</tr><tr>");
                                }
                                
                                if(lineasEtiquetas>1){
                                       sHashLink="<table><tr valign='top'><td  align='right'><div  class='jquerycursorpointer' style='cursor:pointer;color:blue;float:left;witdh:100%;aling:right;z-index=5000;' id='tagsTblExpCont"+sNamer+"' onclick=\"expCont('"+sNamer+"','tagsTbl"+sNamer+"','tagsTblExpCont"+sNamer+"',25);\">+</div>"+
                                               "</td><td align='center'>"+sHashLinkAux.toString()+"</td></tr></table>";
                                       
                                }else{
                                     sHashLink=sHashLinkAux.toString();
                                }
                                //FIN Cambio la estructura de la tabla generada
                        }     
                        */
                        
                        //Esto es para los nombres largos en los archivos
                        String sFileNameAux = sFileName;
                        try {
                            sFileNameAux=URLDecoder.decode(sFileName,"UTF-8").trim();                                                        
                        } catch (Exception e) {
                            //error parsing the file.
                        }
                        
                        if(sFileNameAux.trim().length()>36){
                            String sExt = "";
                            if (sFileNameAux.contains(".")) {
                                sExt = sFileNameAux.substring(sFileNameAux.indexOf("."));
                            }
                            if (!sFileNameAux.contains("<")){
                                sFileNameAux=sFileNameAux.substring(0, 32) + "..." + sExt;
                            }
                        }
//                        String sColor = "red";
//                        if (nCopies > 1) {
//                            sColor = "green";
//                        }
                        String sColor = "#08c";
                        String sColorLocal = "#314";
                        
                        //default replication factor 3
                        int REPLICATION_FACTOR = 3;
                        String sFactor = getConfig("rfactor", "../scrubber/config/www-rtbackup.properties");
                        if (sFactor.length() > 0 ) {
                            REPLICATION_FACTOR = Integer.parseInt(sFactor);
                        }
                                                
                        String sColorNodes = "red";
                        if (nNodes >= REPLICATION_FACTOR) {
                            sColorNodes = "green";
                        }
                        
                        String sLocalCopies = "<font class='jquerycursorpointer'  style='cursor:pointer;background-color:" + sColorLocal + ";color:white;'>&nbsp" + nCopiesLocal + "&nbsp</font>";
                        String sCopies = "<font class='jquerycursorpointer'    style='cursor:pointer;background-color:" + sColor + ";color:white;'>&nbsp" + nCopies + "&nbsp</font>";                        
                        String sNodes = "<font class='jquerycursorpointer'  style='cursor:pointer;background-color:" + sColorNodes + ";color:white;'>&nbsp" + nNodes + "&nbsp</font>";
                        

                        DecimalFormat df = new DecimalFormat("0.0");
                        
                        //Check if image rotated by thumbnailtor
                        
                        try {
                            if (is_photo(sFileNameOri)){
                                File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
                                
                                 if (fh.exists()) { 
                                     BufferedImage bimg = ImageIO.read(fh);
                                     sHeightThumb = ""+bimg.getHeight();
                                     sWidthThumb = ""+bimg.getWidth();
                                 }
                                          
                                 if (Float.parseFloat(sWidthOrig) > Float.parseFloat(sHeightOrig)){
                                    sHeightAux = ((200.0/Float.parseFloat(sWidthOrig)))*Float.parseFloat(sHeightOrig)+"";
                                    sWidthAux = "200";
                                 }
                                 else {
                                    sWidthAux = ((200.0/Float.parseFloat(sHeightOrig)))*Float.parseFloat(sWidthOrig)+"";
                                    sHeightAux = "200";
                                 }
                                 
                                sWidthAux = Math.round(Float.parseFloat(sWidthAux))+"";
                                sHeightAux = Math.round(Float.parseFloat(sHeightAux))+"";
                                 
                                float ratioOrig = Float.parseFloat(sWidthAux) / Float.parseFloat(sHeightAux);
                                float ratioThumb = Float.parseFloat(sWidthThumb) / Float.parseFloat(sHeightThumb);

                                String sWidthThumbAux = sWidthDiv;
                                String sWidthOrigAux = sWidthOrig;

                                //p("sWidthAux = "+sWidthAux);
                                //p("sHeightAux = "+sHeightAux);
                                //p("sWidthThumb = "+sWidthThumb);
                                //p("sHeightThumb = "+sHeightThumb);
                                
                                //p("Ratio orig = "+ratioOrig);
                                //p("Ratio thumb = "+ratioThumb);
                                       
                                
                                if (Float.parseFloat(df.format(ratioOrig)) != Float.parseFloat(df.format(ratioThumb))){
                                    sWidthDiv = sHeightDiv;
                                    sHeightDiv = sWidthThumbAux;
                                    sWidthOrig = sHeightOrig;
                                    sHeightOrig = sWidthOrigAux;
                                }
                            }
                        }catch (Exception e) { }
                        
                        // end check
                        
                        if (sHeightOrig.length() > 0 && Float.parseFloat(sHeightOrig) < 200){ 
                            sHeightDiv = "200";
                        }
                        if (sWidthOrig.length() > 0 && Float.parseFloat(sWidthOrig) < 200){
                            sWidthDiv = "200";
                        }
                        
                        String sWidthNew = null;
                        if (!bVector) {
                            if (Float.parseFloat(sHeightDiv) == 200.0){
                                sWidthNew = sWidthDiv;
                                sHeightNew = sHeightDiv;
                            }
                            else {
                                sWidthNew = ((200.0/Float.parseFloat(sHeightDiv)))*Float.parseFloat(sWidthDiv)+"";
                                sHeightNew = "200";
                            }
                        }
                        else {
                            sWidthNew = sWidthDiv;
                            sHeightNew = sHeightDiv;
                        }
                        

                        
                        
                        if ((widthAcum + Float.parseFloat(sWidthNew) + 2) > screenWidth){
                            widthAcum = Float.parseFloat(sWidthNew) + 2;
                            currentRow++;
                            currentColumn = 1;
                        }
                        else {
                            widthAcum += Float.parseFloat(sWidthNew) + 2;
                            currentColumn ++;
                        }
                        
                        ResultElement re = new ResultElement(bVector,sFileNameOri,sNamer,_password,sMode,_filetype,_daysback,_numcol,_numobj,sVector,sHeightNew,sWidthNew,srcPic,sHashLink,sViewURL2,sPlayURL,sSendURL2,sOpenURL2,sFolderURL2,sFileNameAux,sLocalCopies,sCopies,sNodes,sDate, currentRow, currentColumn, sWidthOrig, sHeightOrig);
                        
                        resultElements.add(re);
                        
                        
                        nCount2++;
                        int nNumCol = Integer.parseInt(_numcol);
                        //p("nNumCol = '" + nNumCol + "'");
                        
                        /*if (nCount2 >= nNumCol) {
                            res.append("<td width='40px'>&nbsp&nbsp</td></tr>");
                            res.append("<tr style='text-align:center; vertical-align:middle'>");
                            nCount2 = 0;
                        }
                        */
                        timerAux.stop();
                        logPerf(timestampPerf, "casss_server","read_row_list2", "19-UltimaParteW", timerAux.getElapsedTime()+"", _writeLog);
                        Time[22] += timerAux.getElapsedTime();
                    
                    }
                    
                }
                
                //Resize all elements
                for (ResultElement e : resultElements){
                    e.resize(resultElements, screenWidth, currentRow);
                }
                //Generate html element
                for (ResultElement e : resultElements){
                    res.append(e.getHtml(screenWidth));
                }
                
                res.append("</form>");
                res.append("</div>");
                
                if (dbmode.equals("p2p") || dbmode.equals("both")) {
                    db_cp.close();
                    db_attr.close();                    
                }
                
                break;
                
            case 5:
                occurences_hash.sortByValue();
                Iterator It2 = occurences_hash.keySet().iterator();
                while (It2.hasNext()) {
                    String sNamer = (String) It2.next();
                    Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                    String sFileName = occurences_names.get(sNamer);
                    p(sNamer + " - " + nCount3 + " - " + sFileName);
                                        String sPic = sNamer + ".jpg";
                    if (sFileName.toLowerCase().contains("mp3") || sFileName.toLowerCase().contains("m4a")) {
                        String sFile = _root + File.separatorChar + "cass" + File.separatorChar + "pic" + File.separatorChar + sPic;
                        p("*** looking for file: " + sFile);
                        File f = new File(sFile);
                        if (!f.exists()) {
                            sPic = get_thumb(sFileName);
                        }
                        p("sPic: " + sPic);
                    }
                    
                    if (!is_photo(sFileName) && !is_music(sFileName)) {
                        sPic = get_thumb(sFileName);
                    }
                    
                    res.append("<li><a href ='test4.php?foo=" + sNamer + "'><img border='0' width=200 height=200 src='pic/" + sPic + "'>" + "<font color='black'>" + sFileName + "</font></a></li>");
                }
                
            case 6:
                //mode polar
                occurences_hash.sortByValue();
                Iterator It3 = occurences_hash.keySet().iterator();
                while (It3.hasNext()) {
                    String sNamer = (String) It3.next();
                    Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                    String sFileName = occurences_names.get(sNamer);
                    p(sNamer + " - " + nCount3 + " - " + sFileName);
                                        String sPic = sNamer + ".jpg";
                    if (sFileName.toLowerCase().contains("mp3") || sFileName.toLowerCase().contains("m4a")) {
                        String sFile = _root + File.separatorChar + "cass" + File.separatorChar + "pic" + File.separatorChar + sPic;
                        p("*** looking for file: " + sFile);
                        File f = new File(sFile);
                        if (!f.exists()) {
                            sPic = get_thumb(sFileName);
                        }
                        p("sPic: " + sPic);
                    }
                    
                    if (!is_photo(sFileName)) {
                        sPic = get_thumb(sFileName);                        
                    }
                    
                    res.append("<div class=\"polaroid\"><img src=\"pic/" + sPic+ "\" alt=\"" + sNamer + "\" /><p>" + sNamer + "</p></div>");
                }
                
            case 7:
                //detail view
                p("Mode7 (detail)!!!" + nCount);
                db_attr = LocalFuncs.tx_attr.makeTx();                                   
                db_cp = LocalFuncs.tx_cp.makeTx();    
                occurences_attr_r = db_attr.getTreeMap("attributes");
                occurences_copies_r = db_cp.getTreeMap("numberofcopies");
                              
                res.append("<script type=\"text/javascript\">\n");
                res.append("function updatecounters() {\n");                
                res.append("$('#n_alltime', window.parent.SIDEBAR.document).html('" + cnt_alltime + "');\n");                
                res.append("$('#n_past24h', window.parent.SIDEBAR.document).html('" + cnt_past24h + "');\n");                
                res.append("$('#n_past3d', window.parent.SIDEBAR.document).html('" + cnt_past3d + "');\n");                
                res.append("$('#n_past7d', window.parent.SIDEBAR.document).html('" + cnt_past7d + "');\n");                
                res.append("$('#n_past14d', window.parent.SIDEBAR.document).html('" + cnt_past14d + "');\n");                
                res.append("$('#n_past30d', window.parent.SIDEBAR.document).html('" + cnt_past30d + "');\n");                
                res.append("$('#n_past365d', window.parent.SIDEBAR.document).html('" + cnt_past365d + "');\n");                
                res.append("$('#n_total', window.parent.SIDEBAR.document).html('" + cnt_total + "');\n");                
                res.append("$('#n_photo', window.parent.SIDEBAR.document).html('" + cnt_photo + "');\n");                
                res.append("$('#n_music', window.parent.SIDEBAR.document).html('" + cnt_music + "');\n");                
                res.append("$('#n_video', window.parent.SIDEBAR.document).html('" + cnt_video + "');\n");                
                res.append("$('#n_docu', window.parent.SIDEBAR.document).html('" + cnt_office + "');\n");                
                res.append("$('#n_doc', window.parent.SIDEBAR.document).html('" + cnt_doc + "');\n");                
                res.append("$('#n_xls', window.parent.SIDEBAR.document).html('" + cnt_xls + "');\n");                
                res.append("$('#n_ppt', window.parent.SIDEBAR.document).html('" + cnt_ppt + "');\n");                
                res.append("$('#n_pdf', window.parent.SIDEBAR.document).html('" + cnt_pdf + "');\n");                
                res.append("$('#inputString', window.parent.SIDEBAR.document).val('" + _keyin + "');\n");                
                res.append("}\n");     
                res.append("bindEnterSearchBar();\n");                
                res.append("</script>\n");
                                                                                               
                res.append("<span class=\"affix\" style=\"background-color:#EEEEEE;color:black;border-bottom:1px solid lightgrey\">");

                res.append("<form class=\"form-search\" id=\"frm1\" action=\"echoClient5.htm\" method=\"get\" onsubmit=\"showLoading();\">");

                res.append("<INPUT TYPE=\"hidden\" NAME=\"ftype\" id=\"ftype\" VALUE=\"" + _filetype + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"days\" id=\"ndays\" VALUE=\"" + _daysback + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"foo\" VALUE=\"" + _key + "\">");
                res.append("<INPUT id=\"formView\" TYPE=\"hidden\" NAME=\"view\" VALUE=\"" + sMode + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"numobj\" VALUE=\"" + _numobj + "\">");
                res.append("<INPUT id=\"formCol\" TYPE=\"hidden\" NAME=\"numcol\" VALUE=\"" + _numcol + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"pw\" VALUE=\"" + _password + "\">");
                res.append("<input type=\"hidden\" name=\"dosubmit\" value=\"1\" id=\"dosubmit\"/>");
                res.append("<input type=\"hidden\" name=\"screenSize\" id=\"screenSize\"/>");
                                                
                cnt_show = cnt_total;
                if (_filetype.equals(".photo")) cnt_show = cnt_photo;
                if (_filetype.equals(".music")) cnt_show = cnt_music;
                if (_filetype.equals(".video")) cnt_show = cnt_video;
                if (_filetype.equals(".document")) cnt_show = cnt_office;
                if (_filetype.equals(".doc")) cnt_show = cnt_doc;
                if (_filetype.equals(".xls")) cnt_show = cnt_xls;
                if (_filetype.equals(".ppt")) cnt_show = cnt_ppt;
                if (_filetype.equals(".pdf")) cnt_show = cnt_pdf;
                
                n_cnt_size = 0;
                n_numobj = 0;
                n_cnt_show = 0;
                try {
                    n_cnt_size = Integer.parseInt(cnt_size);
                    n_numobj = Integer.parseInt(_numobj);
                    n_cnt_show = Integer.parseInt(cnt_show);
                } catch (Exception e) {
                    p("EXCEPTION: parsing int.");                    
                }
                if (n_cnt_size > n_numobj) cnt_size = _numobj;
                                                    
                                
                //res.append("<div style=\"font-size:10px;\"><br></div>");
                //res.append("&nbsp&nbsp");
                
                nPages = 0;
                bCont = true;
                sStart = _datestart;
                while (bCont) {
                    String sPrev = (String)dates_prev.get(sStart);
                    if (sPrev != null) {
                        nPages++;
                        sStart = sPrev;
                    } else {
                        bCont = false;
                    }
                }
                nStart = nPages * n_numobj;
                nEnd = nStart + n_cnt_size;
                
                p("start = " + nStart);
                p("end = " + nEnd);
                p("cnt_show = " + n_cnt_show);
                p("_numobj = " + _numobj);
                                
                if ((nEnd - nStart - 1) > n_numobj) {
                    nEnd = n_numobj;
                    bShowNext = true;
                }
                
                if (n_cnt_show > n_numobj) {
                    bShowNext = true;
                    nEnd = nStart + n_numobj;
                }
                
                res.append("<span class=\"affix6 pull-center\">");
                res.append("<br>");
                res.append("Displaying " + (nStart+1) + "-" + Math.min(nEnd,n_cnt_show) + " of " + n_cnt_show);
                res.append("&nbsp;");
                
                res.append("</span>");
                res.append("<span class=\"affix7\">");
                if (bShowPrev) {
                    res.append("<INPUT TYPE=button value=\" < \" onclick=\"showLoading(); golink('" + sRedirLinkPrev + "','" + _key + "',1)\"/>");
                }
                               
                bShowNext = nEnd < n_cnt_show;
                if (bShowNext) {
                    //res.append("<div class=\"\">");
                    res.append("<INPUT TYPE=button value=\" > \" onclick=\"showLoading(); golink('" + sRedirLink + "','" + _key + "',1)\"/>");
                    //res.append("</div>");                
                }                    
                
                res.append("</span>");
                
                res.append("<span class=\"affix3\">");
                
                res.append("<select class=span2 style=\"display:none;\" onchange=\"searchclick();\" name=\"view\" id=\"inputView\">");
                    res.append("<option value=\"detail\">Detailed View</option>");
                    res.append("<option value=\"show\">Slideshow</option>");
                    res.append("<option value=\"show2\">Slideshow2</option>");
                    res.append("<option value=\"tile\" selected>Tile View</option>");
                    res.append("<option value=\"polar\">Polar View</option>");
                    res.append("<option value=\"caro\">Carousel View</option>");
                res.append("</select>");

                //res.append("&nbsp&nbsp&nbsp&nbsp");
                //res.append("<i class=\"icon-th\"></i>&nbsp#Columns:&nbsp");
                res.append("&nbsp#Columns:&nbsp");
                
                res.append("<select style=\"top:5px; font-size:10px;width:45px;\" onchange=\"searchclick();\" name=\"numcol\" id=\"inputNumCol\">");
                    res.append("<option value=\"1\">1</option>");
                    res.append("<option value=\"2\">2</option>");
                    res.append("<option value=\"3\">3</option>");
                    res.append("<option value=\"4\">4</option>");
                    res.append("<option value=\"5\">5</option>");
                    res.append("<option value=\"7\">7</option>");
                    res.append("<option value=\"9\">9</option>");
                res.append("</select>");
                
                res.append("&nbsp&nbsp");
                //res.append("<i class=\"icon-book\"></i>&nbspResults:&nbsp");
                res.append("&nbspResults:&nbsp");
                
                res.append("<select style=\"font-size:10px;width:60px;\" class=span1 onchange=\"searchclick();\" name=\"numobj\" id=\"inputNumObj\">");
                    res.append("<option value=\"25\">25</option>");
                    res.append("<option value=\"50\">50</option>");
                    res.append("<option value=\"100\">100</option>");
                    res.append("<option value=\"250\">250</option>");
                    res.append("<option value=\"500\">500</option>");
                res.append("</select>");

                res.append("</span>");
                                                                           
                
                //res.append("<b>Search:&nbsp</b>");

                res.append("<span class=\"pull-center affix5\">");
                               
                res.append("<div class=\"input-append\">");  
                res.append("<input type=\"text\" class=\"search-query span4\" name =\"foo\" id=\"inputString\" onKeypress=\"\" onkeyup=\"lookup(this.value);\" autocomplete=\"off\" placeholder=\"Search\"/>");
                //res.append("<span class=\"add-on btn btn-primary\" style=\"top:5px;\"><i class=\"icon-search icon-white\" onclick=\"searchclick();\"></i></span>");
                //res.append("<div class=\"input-append-btn\">");
                res.append("<button class=\"btn btn-primary\" onclick=\"searchclick();clearFilters(); clearFiltersVar();\"><i class=\"icon-search icon-white\" ></i>&nbspSearch</button>");
                res.append("</div>");
                
                res.append("<div class=\"suggestionsBox\" id=\"suggestions\" style=\"display: none; overflow: visible\">");
                    res.append("<div class=\"suggestionList\" id=\"autoSuggestionsList\">");
                        res.append("&nbsp;");
                    res.append("</div>");
                res.append("</div>"); 
                
                res.append("<br>");
                
                res.append("<span class=\"affix4\">");
                
                //res.append("<i class=\"icon-eye-open\"></i>&nbspView Mode:&nbsp&nbsp");                              
                
                res.append("<button type=\"submit\" class=\"btn\" onclick=\"view_tile();\">");
                res.append("<i class=\"icon-th\"></i>");
                res.append("</button>");
                                               
                res.append("<button type=\"submit\" class=\"btn  btn-primary\" onclick=\"view_detail();\">");
                res.append("<i class=\"icon-list icon-white\"></i>");
                res.append("</button>");

                res.append("<button type=\"submit\" class=\"btn\" onclick=\"view_show();\">");
                res.append("<i class=\"icon-picture\"></i>");
                res.append("</button>");

                res.append("</span>");
                
                
                res.append("</span>");

                res.append("</form>");
                res.append("</span>");                
                 
                //res.append("<br><br><br>");

                res.append("<form class=\"form-search\" id=\"frm2\" action=\"bulker.php\" method=get autocomplete=\"off\" onsubmit=\"showLoading();\" >");
                
                res.append("<span class=\"affix2\">");
                
                res.append("<INPUT TYPE=\"hidden\" NAME=\"ftype\" id=\"ftype\" VALUE=\"" + _filetype + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"days\" id=\"ndays\" VALUE=\"" + _daysback + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"foo\" VALUE=\"" + _key + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"view\" VALUE=\"" + sMode + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"numobj\" VALUE=\"" + _numobj + "\">");
                res.append("<INPUT TYPE=\"hidden\" NAME=\"numcol\" VALUE=\"" + _numcol + "\">");
                res.append("<INPUT TYPE=\"hidden\" ID=\"date\" NAME=\"date\" VALUE=\"" + URLEncoder.encode(sFirst, "UTF-8") + "\">");
                res.append("<INPUT TYPE=\"hidden\" ID=\"DeleteTag\" NAME=\"DeleteTag\" VALUE=\"\">");
                res.append("<input type=\"hidden\" name=\"screenSize\" id=\"screenSize\"/>");
                
                //res.append("<input style=\"font-size:9px;\" type=\"button\" onClick=\"togglesel(this.checked);\" value=\"Select All\"/>");

                //res.append("<i class=\"icon-tags\"></i><b></b>");

//                res.append("<div class=\"input-append\">");  
//                res.append("<input type=\"text\" class=\"search-query span4\" name =\"foo\" id=\"inputString\" onKeypress=\"\" onkeyup=\"lookup(this.value);\" autocomplete=\"off\" placeholder=\"Search\"/>");
//                //res.append("<span class=\"add-on btn btn-primary\" style=\"top:5px;\"><i class=\"icon-search icon-white\" onclick=\"searchclick();\"></i></span>");
//                //res.append("<div class=\"input-append-btn\">");
//                res.append("<button class=\"btn btn-primary\" onclick=\"searchclick();clearFilters(); clearFiltersVar();\"><i class=\"icon-search icon-white\" ></i>&nbspSearch</button>");
//                res.append("</div>");


                res.append("<div style=\"font-size:11px;\" class=\"input-append\">");  
                    res.append("<input style=\"font-size:9px;\" type=\"checkbox\" id=\"checkk\" onClick=\"togglesel(this.checked);\"/>");
                    res.append("<label onclick=\"var chek = document.getElementById('checkk'); togglesel(chek.checked);\">");
                    res.append("&nbspSelect All&nbsp&nbsp");
                    res.append("</label>");
                //res.append("<input type=\"text\" class=\"search-query span4\" name=\"foo\" id=\"inputString\" onKeypress=\"\" onkeyup=\"lookup(this.value);\" autocomplete=\"off\" placeholder=\"Searchxxxx\"/>");
                  res.append("<INPUT style=\"font-size:11px;\" TYPE=\"text\" class=\"search-query span2\" NAME=\"tag\" ID=\"tag\" autocomplete=\"off\" placeholder=\"Enter tags here.\"/>");
                //res.append("<INPUT style=\"font-size:11px;\" TYPE=\"submit\" NAME=\"hide selected\" VALUE=\"Apply\">");
                  res.append("<button style=\"font-size:11px;\" class=\"btn btn-primary\" onClick=\"submit_tag();\"><i class=\"icon-white icon-tags  \"></i>&nbspApply </button>");
                //res.append("<button class=\"btn btn-primary\" onclick=\"searchclick();clearFilters(); clearFiltersVar();\"><i class=\"icon-search icon-white\"></i>&nbspSearch</button>");
                res.append("</div>");
                
                //res.append("<INPUT TYPE=\"submit\" style=\"position:absolute; height:0px;width:0px;border:none;padding:0px;\" NAME=\"hide selected\" VALUE=\"Apply\">");
                //res.append("<input type=\"button\" onClick=\"togglechk2(this.checked);\" value=\"Clear\"/>");
                res.append("<input id=\"chk0\" style=\"display:none\" type=\"checkbox\" onClick=\"togglechk(this.checked);\">");
                res.append("&nbsp");
                res.append("</span>");  
                                

                res.append("<br><br><br><br>");
                res.append("<table>");
                res.append("<tr style='text-align:center; vertical-align:middle'>");

                //p("\nnTokens:" + nTokens);
                //p("\nnTokens2:" + nTokens);
                occurences_hash.sortByValue();


                firsttime = true;
                nCurrent = 0;
                It = occurences_hash.keySet().iterator();

                p("# items to process: " + occurences_hash.size());
                
                clientIP = getLocalAddress();
                sLocalIP = "127.0.0.1";
                if (clientIP != null) {
                    sLocalIP = clientIP.getHostAddress();;                            
                } 

                while (It.hasNext()) {
                    String sNamer = (String) It.next();
                    Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                    String sFileName = occurences_names.get(sNamer);
                    String sFileNameOri = sFileName;
                    p(sNamer + " - " + nCount3 + " - " + sFileName);
                    if (firsttime) {
                        nCurrent = nCount3;
                        firsttime = false;
                        //res += "<td>nTokens: " + nCurrent + "<br></td>";
                    }

                    if (!nCount3.equals(nCurrent)) {
                        //res += "<td>nTokens: " + nCount3 + "<br></td>";
                        nCurrent = nCount3;
                    }
                    //if (nCount3.equals(nTokens)) {
                    String sPic = sNamer + ".jpg";
                    
                    p("Filename: '" + sFileName + "'");
                    
                    boolean bVector = false;
                    String sVector = "";
                    if (is_music(sFileName)) {
                        //music case
                        String sFile = _root + File.separatorChar + "cass" + File.separatorChar + "pic" + File.separatorChar + sPic;
                        p("*** looking for file: " + sFile);
                        File f = new File(sFile);
                        if (!f.exists()) {
                            sPic = get_thumb(sFileName);
                            sVector = get_vector(sFileName, "40");
                            bVector = true;
                        }
                        p("sPic: " + sPic);
                        
                       String sAttr = "";
                       String sSongTitle = "";
                       String sSongArtist = "";
                       String sDate = "";
                       try {
                           sAttr = LocalFuncs.occurences_attr.get(sNamer);
                           if (sAttr!= null && sAttr.length() > 0) {
                                 delimiters = ",";
                                 st = new StringTokenizer(sAttr, delimiters, true);
                                 
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
                        
//                        String sSongTitle = "ERROR_";
//                        while (sSongTitle.contains("ERROR_")) {
//                            sSongTitle = get_row_attribute(keyspace, "Standard1", sNamer, "title");    
//                        }
//
//                        String sSongArtist = "ERROR_";
//                        while (sSongArtist.contains("ERROR_")) {
//                            sSongArtist = get_row_attribute(keyspace, "Standard1", sNamer, "artist");
//                        }

                        if (sSongTitle.length() > 40) {
                            sSongTitle = sSongTitle.substring(0, 39);
                        }
                        if (sSongArtist.length() > 40) {
                            sSongArtist = sSongArtist.substring(0, 39);
                        }
                        if (sSongTitle.length() > 0) {
                            if (sSongArtist.length() > 0) {
                                sFileName = sSongTitle + " <br> " + sSongArtist;    
                            } else {
                                sFileName = sSongTitle;
                            }
                        } else {
                            if (sSongArtist.length() > 0) {
                                sFileName += " <br> " + sSongArtist;
                            }
                            if (sFileName.length() > 40) {
                                sFileName = sFileName.substring(0, 39);
                            }
                        }                        
                    } else {
                        
                    }
                                        
                    if (!is_photo(sFileNameOri) && !is_music(sFileNameOri)) {
                        //sPic = get_thumb(sFileNameOri);
                        bVector = true;
                        sVector = get_vector(sFileNameOri, "40");
                    }
                       
                    String srcPic="pic/"+sPic;
                    
                    if(is_photo(sFileNameOri)){
                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
                        if (fh.exists()) { 
                           File fh64= new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".alt64");
                           if(fh64.exists()){
                               //Si ya existe la version base64 la leo
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
                                srcPic="data:image/jpg;base64,"+out.toString();
                                p("Vista Tile:Se carga imagen desde pic folder");
                           } else{
                                //Si existe en pic armo el base64
                               FileInputStream is=new FileInputStream(fh);
                               ByteArrayOutputStream out=new ByteArrayOutputStream();
                               FileWriter writer=new FileWriter(fh64);
                                    
                               byte[] buf =new byte[1024*1024];
                               try {
                                    int n;

                                    while ((n = is.read(buf)) > 0) {
                                        out.write(buf, 0, n);
                                    }
                                    srcPic=Base64.encodeToString(out.toByteArray(), false);
                                    writer.write(srcPic.toCharArray() );
                                } finally {
                                     is.close();
                                     writer.close();

                                }          
                                srcPic="data:image/jpg;base64,"+ srcPic;
                                p("Vista Tile:Se genera imagen base64");
                                
                                out.close();
                           }
                            
                        }else{
                            p("Vista Tile:No se encuentra imagen en PIC, se deja enlace a la imagen");
                        }
                    }

                    boolean bShow = false;
                    if (_filetype.equals(".all")) {
                        bShow = true;
                        //p(".all case");
                    } else {
                        //p("[1]");
                        String ftype2 = _filetype.trim();
                        //p("[2]");
                        //p("ftype2 = '" + ftype2 + "'");                    
                        //p("ftype2 mov = '" + ftype2.contains(".mov") + "'");                                            
                        //p("ftype2 mov = '" + ftype2.equals(".mov") + "'");  
                        bShow = checkifshow(sFileNameOri, ftype2);
                    }              

                    p("bShow = " + bShow);

                    //p("\nKey: '" + _key + "'");
                    if (_key.contains("hidden:")) {
                        p("\n *** Checking for Hidden Key.");
                        String sHidden = get_row_attribute(keyspace, "Standard1", sNamer, "hidden");
                        if (sHidden.length() > 0) {
                            p("Object is hidden: " + sNamer);
                            String sHiddenPw = "hidden:" + sHidden;
                            p("Obj pw = '" + sHiddenPw + "'");
                            p("_keyin = '" + _keyin + "'");
                            bShow = false;
                            if (sHiddenPw.equals(_keyin)) {
                                bShow = true;
                            }
                        }                        
                    } 

                    
                    //p("bshow = " + bShow + "'");
                    if (bShow) {
                        
                       String sHeight = "200";
                       String sWidth = "200";
                       String sDate = "";
                       String sAttr = "";
                       synchronized(occurences_attr_r) {
                            sAttr = occurences_attr_r.get(sNamer);                           
                       }
                       try {
                           if (sAttr!= null && sAttr.length() > 0) {
                                 delimiters = ",";
                                 st = new StringTokenizer(sAttr, delimiters, true);
                                 
                                 if (st.countTokens() == 5) {
                                    sDate = st.nextToken();    //  date 
                                    st.nextToken();            //  ,
                                    sHeight = st.nextToken();  //  height
                                    st.nextToken();            //  ,
                                    sWidth = st.nextToken();   //  width                                     
                                 } else {
                                    sDate = st.nextToken();                                     
                                 }
                                 //p(sDate + " " + sHeight + " " + sWidth);
                           }                             
                       } catch (Exception e) {
                           p("*** WARNING: Exception parsing: " + sNamer + " = '" + sAttr + "'");
                           e.printStackTrace();
                       }
                       
                       //String sHeight = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                       //String sWidth = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");

                        p("height = '" + sHeight + "' " + sHeight.length());
                        p("width = " + sWidth + "' " + sWidth.length());

                        sHeightNew = sHeight.toString();
                        String sWidthNew = sWidth.toString();
                        
                        double nHeight = 0;
                        double nWidth = 0;
                        try {
                            nHeight = Double.parseDouble(sHeight);
                            nWidth = Double.parseDouble(sWidth);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        
                        if (nHeight > 0 || nWidth > 0) {

                            if (nHeight > 50 || nWidth> 50 ) {
                                double hImgRatio = 50 / nHeight;
                                double wImgRatio = 50 / nWidth;
                                nImgRatio= Math.min(hImgRatio, wImgRatio);

                                Double dHeightNew = nHeight*nImgRatio;      
                                //int nHeightNew = (int)dHeightNew;
                                //sHeightNew = Integer.toString(nHeightNew);
                                sHeightNew = dHeightNew.toString();

                                Double dWidthNew = nWidth*nImgRatio;      
                                sWidthNew = dWidthNew.toString();

                            } 

                        } else{
                              sHeightNew = "50";
                              sWidthNew = "50";
                        }

                        
                        p("height new = " + sHeightNew);
                        p("width new = " + sWidthNew);
                        
                        
                        
                        res.append("<td><table  onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'>");

                        if (!bVector) {
                            res.append("<tr><td><a href ='test4.php?foo=" + sNamer + "&pw=" + _password + "'><img border='0' width="+sWidthNew+" height="+sHeightNew+" src='" + srcPic + "' ></a>" + 
                                    "<input type =\"checkbox\" class=\"checkbox\" name=\"" + sNamer + "\">" +                              
                                    "</td>");                            
                        } else {                            
                            res.append("<tr><td height=80 width=80><a href ='test4.php?foo=" + sNamer + "&pw=" + _password + "'>" + sVector + "</a>" + 
                                    "<input type =\"checkbox\" class=\"checkbox\" name=\"" + sNamer + "\">" +                              
                                    "</td>");                            
                        }

                        //p("   [A]");
                        //String sDate = get_row_attribute(keyspace, "Standard1", sNamer, "date_modified");
                        

                        sURLPack sURLpack = new sURLPack();                        
                        sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false); 
                                
                        String sOpenURL = sURLpack.sOpenURL;     
                        String sFolderURL = sURLpack.sFolderURL;
                        String sViewURL = sURLpack.sViewURL;
                        String sPlayURL = sURLpack.sPlayURL;
                        String sSendURL = sURLpack.sSendURL;
//                                                
                        String _hashkey = "hashesm";
                        String _clientip = _host;
                        
                        String sHashLink = "";
                        if (dbmode.equals("cass")) {
                            sHashLink =  getSuperColumn(sNamer, _hashkey, 1, _password, _clientip, sMode, _daysback, _numcol, _numobj, sLast, _filetype);                            
                        } else {
                            sHashLink = lf.getSuperColumn(sNamer, _hashkey, 1, _password, _clientip, sMode, _daysback, _numcol, _numobj, sLast, _filetype);                                            
                        }
                        
                        if (sPlayURL != null) {
                            sPlayURL=sPlayURL.replace("id_","id='serverlnk"+sNamer+"' class='jqueryhidden'"); 
                            sPlayURL =sPlayURL.replace("class='jqueryhidden'", "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");                            
                        }
                        
                        sHashLink =sHashLink.replace(sNamer+"="+sNamer, "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                        sViewURL=sViewURL.replace("class='jqueryhidden'", "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                        sSendURL =sSendURL.replace("class='jqueryhidden'", "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                        sOpenURL =sOpenURL.replace("class='jqueryhidden'", "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                        sFolderURL =sFolderURL.replace("class='jqueryhidden'", "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                     
                        String sViewURL2 = "";
                        String sSendURL2 = "";
                        String sOpenURL2 = "";
                        String sFolderURL2 = "";
                        String sPlayURL2 = "";
                        
                        String sCopyInfo = "";
                        synchronized (occurences_copies_r) {                            
                            if (_clientIP.equals(sLocalIP)) {
                                sCopyInfo = occurences_copies_r.get(sNamer); 
                                if (sCopyInfo == null) {
                                    sCopyInfo = "1,1,1";
                                }                                
                            } else {
                                //p("REMOTE CLIENT - gnp");
                                sCopyInfo = lf.getNumberofCopies("paths", sNamer, _clientIP,sLocalIP, false);
                            }
                        }
                        
                        int nCopies = 0;
                        int nNodes = 0;
                        int nCopiesLocal = 0;

                        try {
                            if (sCopyInfo != null & !sCopyInfo.equals("ERROR")) {                           

                            String sCopies = "";
                            String sNodes = "";
                            String sCopiesLocal = "";
                            delimiters = ",";
                            st = new StringTokenizer(sCopyInfo, delimiters, true);
                            while (st.hasMoreTokens()) {
                                sCopies = st.nextToken();
                                st.nextToken();
                                sNodes = st.nextToken();
                                st.nextToken();
                                sCopiesLocal = st.nextToken();
                            }

                            nCopies = Integer.parseInt(sCopies);                        
                            nNodes = Integer.parseInt(sNodes); 
                            nCopiesLocal = Integer.parseInt(sCopiesLocal); 
                        }
                            
                        } catch (Exception e) {
                            StringWriter sWriter = new StringWriter();
                            e.printStackTrace(new PrintWriter(sWriter));
                            logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
                        }
                        
                        
                        if (nCopies > 0) {                             
                            if (_cloudhosted) {                                
                                //VIEW LINK
                                sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false); 
                                
                                sOpenURL = sURLpack.sOpenURL;     
                                sFolderURL = sURLpack.sFolderURL;
                                sViewURL = sURLpack.sViewURL;
                                
                                p("**cloud*********** viewurl = " + sViewURL);
                                
                                Boolean bHaveServerLink = false;
                                sViewURL2 = gen_view_link2(_host, _port, sViewURL, bHaveServerLink, _cloudhosted, sNamer, sURLpack, sFileNameOri, _key, nImgRatio, sLocalIP, false);
                            } else {
                                //VIEW
                                String sButtonText = "View";
                                if (is_music(sFileNameOri) || is_movie(sFileNameOri)) sButtonText = "Play";
                                
                                
                                
                                if(is_office(sFileNameOri)){
                                    sViewURL2 = "<INPUT TYPE=button value=\"View 2\" id_ onclick=\"newTabCommand('getfile.fn?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\"/>";                                                                                         
                                } else {
                                    if(is_photo(sFileNameOri) || is_movie(sFileNameOri) || is_musicToPlay(sFileNameOri) || si_inline(sFileNameOri)){ 
                                        String sLink = "getfile.fn?sNamer="+sNamer+"&sFileName="+sFileNameOri;
                                        sViewURL2 = "<INPUT TYPE=button value=\"" + sButtonText + "\" id_ onclick=\"golink('" + sLink + "','" + _key + "',1,1)\"/>";                             
                                        if (is_photo(sFileNameOri)) {
                                            sSendURL2 = "<INPUT TYPE=button value=\"Send\" id_ onclick=\"golink('" + sURLpack.sSendURL + "','" + _key + "',1,1);\" />";                                                                 
                                        }
                                    }else{
                                        sViewURL2 = "<INPUT TYPE=button value=\"View\" id_ onclick=\"silentCommand('getfile.fn?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\"/>";                             
                                    } 
                                }
                            }                            
                        
                            if (nCopiesLocal > 0) {
                                //OPEN & FOLDER
                                sOpenURL2 = "<INPUT TYPE=button value=\"Open\" id_ onclick=\"silentCommand('openfile.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\" />";                                                                 
                                sFolderURL2= "<INPUT TYPE=button value=\"Folder\" id_ onclick=\"silentCommand('openfolder.htm?sNamer="+sNamer+"&sFileName="+sFileNameOri+"');\" />";                             
                            }
                            
                        }
                        
                        res.append("<td><font size=2>" + sFileName +"</font></td>" + 
                                "<td><font size=2>" + sDate + "</font>" + 
                                sViewURL2 + "&nbsp&nbsp" + 
                                sPlayURL2 + "&nbsp&nbsp" + 
                                sSendURL2 + "&nbsp&nbsp" +
                                sOpenURL2 + "&nbsp&nbsp" +
                                sFolderURL2 + "</td>" +
                                "<td><div id='tags"+sNamer+"' class='jqueryhidden'><font size=1 style=\"background-color: yellow\"  >" + sHashLink + "</font></div></td>");

                        res.append("</tr>" + 
                            "</table></td></div>");

                        p("nCount2  ---> '" + nCount2 + "'");
                        
                        nCount2++;

                        int nNumCol = Integer.parseInt(_numcol);
                        //p("nNumCol = '" + nNumCol + "'");
                        if (nCount2 >= nNumCol) {
                            res.append("</tr>");
                            res.append("<tr style='text-align:center; vertical-align:middle'>");
                            nCount2 = 0;
                        }  
                    }
                }
                res.append("</tr></form></table>");
                
                db_cp.close();
                db_attr.close();

                break;
            case 8:
                p("Mode1 - Slideshow. Count =" + nCount);

                p("\nnTokens:" + nTokens);

                p("\nnTokens2:" + nTokens);
                p("occurences hash size: " + occurences_hash.size());
                occurences_hash.sortByValue();
                int npid2 = Integer.parseInt(_pid);
                p("npid = '" + npid2 + "'");
                if (npid2 >= 0) {
                    It = occurences_hash.keySet().iterator();
                    while (It.hasNext()) {
                        String sNamer = (String) It.next();
                        Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                        String sFileName = occurences_names.get(sNamer);
                        String sFileNameOri = sFileName;
                        p(sNamer + " - " + nCount3 + " - " + sFileName);

                        String _clientip = "localhost";                
                        //String sPath = lf.getSuperColumn2(sNamer, "paths", 2);
                        String sPath = lf.getSuperColumn(sNamer, "paths", 3, _password, _clientip, sMode, _daysback, _numcol, _numobj, sLast, _filetype);                                            
                        String srcImage=sPath;
                       
                        
                        //sNamer = "L0M6L1VzZXJzL0FsZWphbmRyby9QaWN0dXJlcy9QaG90byBTdHJlYW0vTXkgUGhvdG8gU3RyZWFtL0lNR18wMDEzLkpQRy8=";
                        if (sPath.length() > 0) {
                            //res += "<li><img src=\"" + sPath + "\" alt=\"Marsa Alam underawter close up\" /></li>\n";
                            
                            String sHeight = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                            String sWidth = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");

                            p("height = " + sHeight);
                            p("width = " + sWidth);
                                                        
                            res.append("<li><img src=\"" + srcImage + "\" carrousel=1 originalWidth=\"" +sWidth + "\" width=\"" +sWidth + "\" originalHeight=\"" + sHeight + "\" height=\"" + sHeight + "\" alt=\"Alterante.\" /></li>\n");

                        
                        
                        }                   
                    }
                    
                   
                }
               

                //for (int i = 0; i < nCount; i++) {
                    //p(sNames[i]);
                    //res += "new Array(\"" + sNames[i] + "\",\"1024\",\"768\",\"hello\"" + ")";
                    //if (i < nCount - 1) {
                    //    res += ",";
                    //}
                //}
                break;
                
            case 9:                
                It = occurences_hash.keySet().iterator();
                    while (It.hasNext()) {
                        String sNamer = (String) It.next();
                        res.append(sNamer + ",");
                }
                break;
                
            case 10:                
                boolean bLoop = true;
                int nTry = 0;
                db_attr = null;
                occurences_attr_r = null;
                
                while (bLoop && nTry < 5) {
                    try {
                        lf.open_mapdb();               
                        if (lf.tx_attr != null) {
                           db_attr = lf.tx_attr.makeTx();
                           if (db_attr != null)  {
                               occurences_attr_r = db_attr.getTreeMap("attributes");
                               bLoop = false;
                           } else {
                                nTry++;
                                log("WARNING: Null db_attr. Try #" + nTry); 
                                Thread.sleep(500);
                           }
                        }  else {
                           nTry++;
                           log("WARNING: Null tr_attr. Try #" + nTry);
                           Thread.sleep(500);
                        }
                    } catch (IllegalAccessError e ) {
                        nTry++;
                        log("WARNING: There was ILLEGAL ACCESS ERROR. Try #" + nTry);
                        Thread.sleep(500);
                    }
                }

                //query.fn - JSON case (mobile)
                
                //sURLPack sURLpack = new sURLPack();              
                
                res.append("{\n");
                
                
                res.append("\"fighters\": [\n");
                It = occurences_hash.keySet().iterator();
                String sNamer = "";
                if (_cloudhosted) {  
                    lf.clear_counters();                            
                }
                while (It.hasNext()) {
                    sNamer = (String) It.next();
                    String sFileName = occurences_names.get(sNamer);
                    res.append("{\n");
                    //res.append("\"name\": \"" + sFileName + "\",\n");
                    res.append("\"name\": \"" + URLEncoder.encode(sFileName, "UTF-8") + "\",\n");
                    res.append("\"nickname\": \"" + sNamer + "\",\n");
                    if (_cloudhosted) {
                        res.append("\"type\": \"filename\",\n");                        
                    }
                    
                    //sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, false, false); 
                    //String sView = lf.read_view_link(sNamer, sFileName, _key, _host, _port, _cloudhosted, _clientIP, true);                                    
                    String sView = "";
                    String sRemote = "";
                    if (_cloudhosted) {
                        _host = "cloud.alterante.com";
                        _port = "80";
                        sURLPack sURLpack = new sURLPack();
                        sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, false, false); 

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
                            sRedirLink = "cassvault/" + sViewURL.substring(nPos2,nPos);
                            sView = "http://" + _host + ":" + _port + "/" + sRedirLink;
                        }
                                                
                    } else {
                        String sHost = _host;
                        String sPort = ":" + _port;
                        if (_awshosted) {
                        //if (_host.startsWith("10.")) {
                            sHost = "cloud.alterante.com";
                            sPort = ""; 
                        }
                        sView = "http://" + sHost + sPort + "/cass/getfile.fn?sNamer=" + sNamer;                    
                        sRemote = "http://" + sHost + sPort + "/cass/openfile.fn?sNamer=" + sNamer + "&ftype=" + sFileName.substring(sFileName.lastIndexOf(".")+1, sFileName.length()).toLowerCase();                        
                    }                                           
                    //String sRemote = read_open_link(sNamer, sFileName, _key, _host, _port, _cloudhosted,_clientIP);                
                    //String sRemote = sURLpack.sOpenURL;
                           
                    //check if video thumbnail available and if so generate the alt64 file
                    if (is_video(sFileName)) {
                        File fthumb = new File("../rtserver/streaming/" + sNamer + "/" + "thumbnail.jpg");
                        p("looking for thumbnail video: " + fthumb.getAbsolutePath());
                        if (fthumb.exists()) {
                            p("found the thumnail in JPG format");

                            //store the thumbnail encoded in Base64 as well
                            File fh64= new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".alt64");
                            if (!fh64.exists()) {
                                p("did not find the ALT64 file : " + fh64.getAbsolutePath());
                                p("generating new ALT64 for JPG: " + fthumb.getAbsolutePath());
                                boolean success = filewrite64(fthumb, fh64);
                                if (!success) {
                                    log("WARNING: There was a problem saving the thumbnail64");
                                }                
                            } else {
                                log("Skipping thumbnail64 creation, since file already exists.");                      
                            }
                        }
                    }

                    //only check for thumbnail if it's a photo , music, or PDF
                    if (is_video(sFileName) || is_photo(sFileName) || is_pdf(sFileName) || is_music(sFileName)) {
                        p("THUMBNAIL DIR = " + THUMBNAIL_OUTPUT_DIR);
                        File fh64= new File(THUMBNAIL_OUTPUT_DIR, sNamer.toLowerCase() + ".alt64");
                        p("@@@@THUMBNAIL DIR2 = " + fh64.getCanonicalPath());
                        if(fh64.exists()){
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
                            
                            //p("alt64 = '" + out.toString() + "'");
                            
                            is.close();
                            out.close();

                            String sHeight = "";
                            String sWidth = "";
                            if (is_video(sNamer)) {
                                sHeight = "320";
                                sHeight = "200";
                            } else {
                                sHeight = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                                sWidth = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");
                            }
                            res.append("\"img_height\": \"" + sHeight + "\",\n");
                            res.append("\"img_width\": \"" + sWidth + "\",\n");                                
                        
                        } else {
                            pw("WARNING: Thumbnail 64 not found: " + fh64.getCanonicalPath() );
                        }                        
                    }
                    
                    //res.append("\"file_ip\": \"" + sURLpack.sViewURLJSON_IP + "\",\n");
                    //res.append("\"file_port\": \"" + sURLpack.sViewURLJSON_Port + "\",\n");
                    //res.append("\"file_path\": \"" + sURLpack.sViewURLJSON_Path + "\",\n");
                    //res.append("\"file_ext\": \"" + sURLpack.sViewURLJSON_Extension + "\"\n");
                    res.append("\"file_path\": \"" + sView + "\",\n");
                    res.append("\"file_remote\": \"" + sRemote + "\",\n");
                    String ext = sFileName.substring(sFileName.lastIndexOf(".")+1, sFileName.length()).toLowerCase();
                    res.append("\"file_ext\": \"" + ext + "\",\n");
                                        
                    String fsize = get_row_attribute(keyspace, "Standard1", sNamer, "size");
                    res.append("\"file_size\": \"" + fsize + "\",\n");

                    String ftags = lf.getTagsForFile(sNamer, _user);
                    res.append("\"file_tags\": \"" + ftags + "\",\n");
                    
                    res.append("\"file_path_webapp\": \"/cass/getfile.fn?sNamer=" + sNamer + "&sFileExt=" + sFileName.substring(sFileName.lastIndexOf(".")+1, sFileName.length()).toLowerCase() + "&sFileName=" + URLEncoder.encode(sFileName, "UTF-8") + "\",\n"); 
                    res.append("\"file_remote_webapp\": \"/cass/openfile.fn?sNamer=" + sNamer + "&ftype=" + sFileName.substring(sFileName.lastIndexOf(".")+1, sFileName.length()).toLowerCase() + "\",\n");                        
                    res.append("\"file_folder_webapp\": \"/cass/openfolder.fn?sNamer=" + sNamer + "&sFileName=" + URLEncoder.encode(sFileName, "UTF-8") + "\",\n");                        

                    //file group
                    String sFileGroup = "";
                    if (is_music(sFileName)) sFileGroup = "music";
                    if (is_movie(sFileName)) sFileGroup = "movie";
                    if (is_document(sFileName) || is_office(sFileName)) sFileGroup = "document";
                    if (is_photo(sFileName)) sFileGroup = "photo";
                    if (is_textfile(sFileName)) sFileGroup = "textfile";
                    if (sFileGroup.length() == 0) {
                        sFileGroup = "other";                        
                    }                    
                    res.append("\"file_group\": \"" + sFileGroup + "\",\n");                    
                    
                    //if video
                    if(ext.equalsIgnoreCase("mov") || ext.equalsIgnoreCase("mpg") || ext.equalsIgnoreCase("mmv")
                            || ext.equalsIgnoreCase("mp4")){ 
                        String videolink = lf.getMediaURL(sNamer, "video", _awshosted);
                        if(videolink != null){
                            res.append("\"video_url\": \"" +  videolink + "\",\n");
                            String sVideoLink = "getvideo.m3u8?md5=" + sNamer;
                            res.append("\"video_url_webapp\": \"" +  sVideoLink + "\",\n");
                            String sVideoLinkRemote = "https://abc.alterante.com/cass/getvideo.m3u8?md5=" + sNamer;
                            res.append("\"video_url_remote\": \"" +  sVideoLinkRemote + "\",\n");
                        }
                    }
                    
                    //if audio
                    if(ext.equalsIgnoreCase("mp3") || ext.equalsIgnoreCase("m4a")
                            || ext.equalsIgnoreCase("m4p") || ext.equalsIgnoreCase("wav")){ 
                        String audiolink = lf.getMediaURL(sNamer, "audio", _awshosted);
                        if(audiolink != null){
                            res.append("\"audio_url\": \"" +  audiolink + "\",\n");
                            String sAudioLinkRemote = "https://abc.alterante.com/cass/getaudio.fn?md5=" + sNamer;
                            res.append("\"audio_url_remote\": \"" +  sAudioLinkRemote + "\",\n");
                        }
                    }
                    
                    String sDate = "";                    
                    if (is_music(sFileName)) {

                        String sSongTitle = "";
                        String sSongArtist = "";

                        bLoop = true;
                        nTry = 0;
                        String sAttr = "";
                        while (bLoop && nTry < 5) {
                            try {
                                lf.open_mapdb();
                                if (lf.tx_attr != null) {
                                    db_attr = lf.tx_attr.makeTx();
                                    if (db_attr != null) {
                                        occurences_attr_r = db_attr.getTreeMap("attributes");
                                        sAttr =  occurences_attr_r.get(sNamer);
                                        bLoop = false;
                                    } else {
                                        nTry++;
                                        log("WARNING: Null db_attr [b]. Try #" + nTry);                                                                    
                                        Thread.sleep(500);
                                    }
                                } else {
                                    nTry++;
                                    log("WARNING: Null tx_attr [b]. Try #" + nTry);                                
                                    Thread.sleep(500);                               
                                }
                            } catch (IllegalAccessError e ) {
                                nTry++;
                                log("WARNING: There was ILLEGAL ACCESS ERROR[b]. Try #" + nTry);
                                Thread.sleep(500);
                            }
                        }                    

                        try {
                            if (sAttr!= null && sAttr.length() > 0) {
                                  delimiters = ",";
                                  st = new StringTokenizer(sAttr, delimiters, true);

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
                        
                        if (sSongTitle.length() > 0){
                            String encodedURL = URLEncoder.encode(sSongTitle, "UTF-8");
                            encodedURL = encodedURL.replaceAll("\\+", "%20");
                            res.append("\"song_title\": \"" + encodedURL + "\",\n");
                        }                    
                        if (sSongArtist.length() > 0){
                            String encodedURL = URLEncoder.encode(sSongArtist, "UTF-8");
                            encodedURL = encodedURL.replaceAll("\\+", "%20");
                            res.append("\"song_artist\": \"" + encodedURL + "\",\n");
                        }                                                                                                          
                    }
                    
                    sDate = get_row_attribute(keyspace, "Standard1", sNamer, "date_modified");                    
                    res.append("\"file_date\": \"" + sDate + "\",\n");   
                    
                    try {
                        SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS z");
                        Date d = f.parse(sDate);
                        long milliseconds = d.getTime();
                        res.append("\"file_date_long\": \"" + milliseconds + "\"\n");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    
                    if (It.hasNext()) {
                        res.append("},\n");                                            
                    } else {
                        res.append("}\n");                                                                    
                    }
                    //p("JSON " + sFileName + " " + sNamer + " " + sURLpack.sViewURLJSON_Path);                
                    p("JSON -> " + sFileName + " " + sNamer + " " + sView + " " + _filetype);                

                    if (_daysback.length() < 1) _daysback = "0";
                    int ndaysback = 0;
                    try {
                        ndaysback = Integer.parseInt(_daysback);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (dbmode.equals("cass")) {
                        long regTime = occurences.get(sNamer);                        
                        p(sNamer + " " + regTime);
                        lf.increment_counter_ac(sNamer, sFileName, ndaysback, _filetype, regTime);
                    }
                }

                res.append("],\n");
        
                res.append("\"objFound\": [\n");
                int nCurrentView = 0;
                int nOffice = lf.nOfficeDoc + lf.nOfficePdf + lf.nOfficePpt + lf.nOfficeXls;

                if (_cloudhosted || _host.startsWith("ec2-")) {
                    if (nCountVisible > lf.nTotal) lf.nTotal = nCountVisible;
                    //lf.nTotal = nCountVisible;  //occurences_hash.size();                 
                }
                
                if (_filetype.equalsIgnoreCase(".all")) nCurrentView = lf.nTotal;
                if (_filetype.equalsIgnoreCase(".photo")) nCurrentView = lf.nPhoto;
                if (_filetype.equalsIgnoreCase(".music")) nCurrentView = lf.nMusic;
                if (_filetype.equalsIgnoreCase(".video")) nCurrentView = lf.nVideo;
                if (_filetype.equalsIgnoreCase(".document")) nCurrentView = nOffice;
                if (_filetype.equalsIgnoreCase(".doc")) nCurrentView = lf.nOfficeDoc;
                if (_filetype.equalsIgnoreCase(".xls")) nCurrentView = lf.nOfficeXls;
                if (_filetype.equalsIgnoreCase(".ppt")) nCurrentView = lf.nOfficePpt;
                if (_filetype.equalsIgnoreCase(".pdf")) nCurrentView = lf.nOfficePdf;

                //if (_daysback.equals("")) nCurrentView = lf.nTotal;
                if (_daysback.equals("1")) nCurrentView = lf.nPast24h;
                if (_daysback.equals("3")) nCurrentView = lf.nPast3d;
                if (_daysback.equals("7")) nCurrentView = lf.nPast7d;
                if (_daysback.equals("14")) nCurrentView = lf.nPast14d;                
                if (_daysback.equals("30")) nCurrentView = lf.nPast30d;
                if (_daysback.equals("365")) nCurrentView = lf.nPast365d;
                
                res.append("{\"nCurrent\": \"" + nCurrentView + "\",\n");
                res.append("\"nTotal\": \"" + lf.nTotal + "\",\n");
                res.append("\"nPhoto\": \"" + lf.nPhoto + "\",\n");
                res.append("\"nMusic\": \"" + lf.nMusic + "\",\n");
                res.append("\"nVideo\": \"" + lf.nVideo + "\",\n");
                res.append("\"nDocuments\": \"" + nOffice + "\",\n");
                res.append("\"nDoc\": \"" + lf.nOfficeDoc + "\",\n");
                res.append("\"nXls\": \"" + lf.nOfficeXls + "\",\n");
                res.append("\"nPpt\": \"" + lf.nOfficePpt + "\",\n");
                res.append("\"nPdf\": \"" + lf.nOfficePdf + "\"},\n");

                res.append("{\"nPast24h\": \"" + lf.nPast24h + "\",\n");
                res.append("\"nPast3d\": \"" + lf.nPast3d + "\",\n");
                res.append("\"nPast7d\": \"" + lf.nPast7d + "\",\n");
                res.append("\"nPast14d\": \"" + lf.nPast14d + "\",\n");
                res.append("\"nPast30d\": \"" + lf.nPast30d + "\",\n");
                res.append("\"nPast365d\": \"" + lf.nPast365d + "\",\n");
                res.append("\"nAllTime\": \"" + lf.nAllTime + "\"}\n");                
                
                res.append("]\n");

                res.append("}\n");
                
                //p(res.toString());

                break;
            case 11:        
                //getcounters.fn - JSON mobile
                
                res.append("{\n");
                res.append("\"objFound\": [\n");
                res.append("{\"nTotal\": \"" + lf.nTotal + "\",\n");
                res.append("\"nPhoto\": \"" + lf.nPhoto + "\",\n");
                res.append("\"nMusic\": \"" + lf.nMusic + "\",\n");
                res.append("\"nVideo\": \"" + lf.nVideo + "\",\n");
                nOffice = lf.nOfficeDoc + lf.nOfficePdf + lf.nOfficePpt + lf.nOfficeXls;
                res.append("\"nDocuments\": \"" + nOffice + "\",\n");
                res.append("\"nDoc\": \"" + lf.nOfficeDoc + "\",\n");
                res.append("\"nXls\": \"" + lf.nOfficeXls + "\",\n");
                res.append("\"nPpt\": \"" + lf.nOfficePpt + "\",\n");
                res.append("\"nPdf\": \"" + lf.nOfficePdf + "\"},\n");

                res.append("{\"nPast24h\": \"" + lf.nPast24h + "\",\n");
                res.append("\"nPast3d\": \"" + lf.nPast3d + "\",\n");
                res.append("\"nPast7d\": \"" + lf.nPast7d + "\",\n");
                res.append("\"nPast14d\": \"" + lf.nPast14d + "\",\n");
                res.append("\"nPast30d\": \"" + lf.nPast30d + "\",\n");
                res.append("\"nPast365d\": \"" + lf.nPast365d + "\",\n");
                res.append("\"nAllTime\": \"" + lf.nAllTime + "\"}\n");                
                
                res.append("]\n");

                res.append("}\n");
                
                break;
            default:
                break;

        }
        
        timer_all.stop();
        //if (true) {
        if (nMode < 9) {
            String sDebug2 = "";
            for (int i=0;i<23;i++) {
                if (Time[i] > 0) sDebug2 += "t" + i + "=" + Time[i] + "ms ";
            }
            String sTime = "<div style='clear:both; margin-top: 3em'>**Total time: " + timer_all.getElapsedTime() + " ms " +
                            "Index time: = " + timer_index.getElapsedTime() + " ms " +
                            sDebug2 +
                            sDebug +
                            "</div>";
            
            res.append(sTime);
            p(sTime);
        
            //Update tags  
            
            
            String tags = lf.getTagsLeftNavBar(_user, false, false,false);
            String result = tags.trim().equals("") ? "<span style=\"color: black\">No tags</span>" : tags;
                            
            res.append ("<script type='text/javascript'>\n");
            res.append ("$('#loop_tags', window.parent.SIDEBAR.document).html('xxx');\n");
            res.append ("$('#tags_all', window.parent.SIDEBAR.document).html('" + result + "');\n");
            res.append ("</script>");

            //res.append("<script type='text/javascript'>setTimeout('updateTags()', 5000);</script>");

        }

        p("\n lenth RES:" + res.length());
        return res.toString();
        
        } catch (IOException OutofMemoryError) {
            p("***OutofMemoryError xception: ");
            OutofMemoryError.printStackTrace();
            //res = "ERROR_MEM";
            StringWriter sWriter = new StringWriter();
            OutofMemoryError.printStackTrace(new PrintWriter(sWriter));
            logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
        } catch (Exception e) {            
            p("***xception: ");
            e.printStackTrace();
            StringWriter sWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(sWriter));
            logErrors(timestampPerf, sWriter.getBuffer().toString(),_writeLog);
        }
        //catch (Exception TApplicationException) {
        //    p("***TApplicationException: ");
        //    p(TApplicationException.getMessage());
        //    //res = "ERROR_THR";
        //}
        return res.toString();
    }
         
    public String read_row_list(String _keyin, 
                                int nMode, 
                                String _root, 
                                String _numobj, 
                                String _filetype, 
                                String _numcol, 
                                String _password,
                                String _pid, 
                                String _daysback,
                                String _datestart,
                                String _host,
                                String _port,
                                Boolean _cloudhosted,
                                String _clientIP)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {

        String res = "";
        String sMode = "";
        String dateUTF = "";
        String sRedirLink = "";

        try {
            
                      
          Stopwatch timer = new Stopwatch().start();

          loadProps();
          
          mCassandraClient.set_keyspace(keyspace);

            // read entire row
          SlicePredicate predicate = new SlicePredicate();
          SliceRange sliceRange = new SliceRange();
            
          p("datestart: '" + _datestart + "'");
          p("datestart length: '" + _datestart.length() + "'");
          p("daysback: '" + _daysback + "'");

          if (_daysback.equals("") && _datestart.equals("")) {
                //blank case
                sliceRange.setStart(new byte[0]);
                sliceRange.setFinish(new byte[0]);
          } else {
                String sStartDate = "";
                String sFinishDate = DateFormat.format(Calendar.getInstance().getTime());
                sFinishDate = URLEncoder.encode(sFinishDate, "UTF-8");

                if ( _datestart.length() == 0) {
                    //start date blank, ndays exists
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DATE, -(Integer.valueOf(_daysback)));
                    sStartDate = DateFormat.format(cal.getTime());                    
                    sStartDate = URLEncoder.encode(sStartDate, "UTF-8");
                } else {
                    //start date exists, use it
                    p("datestart: '" + _datestart + "'");
                    sStartDate = URLDecoder.decode(_datestart, "UTF-8");
                }

               p("start: " + sStartDate);
               p("end: " + sFinishDate);                

                sStartDate = URLEncoder.encode(sStartDate, "UTF-8");
                sFinishDate = URLEncoder.encode(sFinishDate, "UTF-8");

                
                sliceRange.setStart(ByteBuffer.wrap(sStartDate.getBytes()));
                
                if (isHashFile(sStartDate)) {
                    //case where start param is a UUID, in which case we set end date to blank
                    sliceRange.setFinish(new byte[0]);    
                } else {
                    //start param is an actual date, so current time/date is the end date
                    sliceRange.setFinish(ByteBuffer.wrap(sFinishDate.getBytes()));    
                }
                
          }

          //sliceRange.setCount(Integer.valueOf(_numobj) + 1);
          sliceRange.setCount(Integer.valueOf(_numobj) + 1);
          predicate.setSlice_range(sliceRange);

          //sNames = new String[100];
          nCount = 0;

          SortableValueMap<String, Integer> occurences_hash = new SortableValueMap<String, Integer>();
          HashMap<String, String> occurences_names = new HashMap<String, String>();

          String _key = _keyin.toLowerCase();

          //p("\nKey: '" + _key + "'");

          String delimiters = "+ ";
          StringTokenizer st = new StringTokenizer(_key.trim(), delimiters, true);
            
          Integer nTokens = st.countTokens();
          String sFirst = "";
          String sLast = "";
          String sNextLink = "";

          int nCount = 0;
          int nCountHidden = 0;
          int nCountVisible = 0;
          int nCountSkipped = 0;
          boolean bShowNext = false;
          
          loadFileExtensions();
          
          while (st.hasMoreTokens()) {
                //nTokens = nTokens + 1;

                String w = st.nextToken();
                //p("token: " + w);

                byte[] kk = w.getBytes();

                ColumnParent parent = new ColumnParent(columnFamily);

                int nTries = 0;
                int nIteration = 0;
                boolean bContinue = true;
                int nObject = 0;
                
                
                while (bContinue) {
                    nIteration++;
                    
                    p("Iteration #" + nIteration);
                    
                    if (nIteration > 1) {
                        p("starting at date: " + sLast);   
                        sliceRange.setStart(ByteBuffer.wrap(sLast.getBytes()));
                    }
                    

                    List<ColumnOrSuperColumn> results = null;
                    
                    int nRetry = 0;
                    boolean bTryRead = true;
                    boolean bSuccess = false;
                    while (bTryRead) {
                        try {
                            results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
                            bTryRead = false;
                            bSuccess = true;
                        } catch (Exception TimedOutException) {
                            p("TimeoutException. Retry #: " + nRetry);
                            Thread.sleep(3000);
                            nRetry++;
                            if (nRetry > 3) {
                                bTryRead = false;
                            }
                        }                        
                    }

                    if (bSuccess) {
                        sLast = "";
                        nCount = results.size();

                        p("\nResult set size:" + results.size());
                        p("\n _numobj: " + _numobj);

                        for (ColumnOrSuperColumn result : results) {
                                if (occurences_names.size() < Integer.valueOf(_numobj)) {
                                    String sValue = new String (result.column.getValue(), UTF8);
                                    String sHidden = "";
                                    sHidden = get_row_attribute(keyspace, "Standard1", sValue, "hidden");
                                    if ((sHidden.length() > 0) && !_key.contains("hidden:")) {
                                        nCountHidden++;
                                    } else {
                                        String sFileType = "." + get_row_attribute(keyspace, "Standard1", sValue, "ext");

                                        if (sFileType.contains(_filetype) || _filetype.equalsIgnoreCase(".all")) {
                                            nCountVisible++;    
                                            res += read_row_hash_cass(new String(result.column.getValue(), "UTF8"), occurences_hash, occurences_names);
                                            String sName = new String(result.column.getName(), UTF8);
                                            p(nCountVisible + " '" + sName + "' -> '" + sValue + "'");
                                            sLast = sName;
                                            if ("".equals(sFirst)) {
                                                sFirst = sName;
                                            }
                                        } else {
                                            p("'" + sFileType + "' vs '" + _filetype + "'");
                                            //p("not passed");
                                            nCountSkipped++;
                                        }
                                    }                            
                                } else {
                                    p("I have enough objects. Exiting.");
                                    bContinue = false;
                                    bShowNext = true;
                                    break;
                                }
                            }  

                        p("\nRequested size       :" + _numobj);
                        p("\nResults(visible)     :" + nCountVisible);
                        p("\nResults(hidden)      :" + nCountHidden);
                        p("\nResults(dedup/unique):" + occurences_names.size());


                        if (results.size() < Integer.valueOf(_numobj)) {
                            p("No more objects in DB");
                            bContinue = false;
                        } 
                        if (occurences_names.size() >= Integer.valueOf(_numobj)) {
                            //we have enough objects to show
                            p("we have enough objects. Exiting!");
                            bContinue = false;
                            bShowNext = true;
                        }
                        if (nIteration > 10) {
                            p("too many iterations. exiting.");
                            bContinue = false;
                        }
                    } else {
                        p("[WARNING] There was an error reading the data for token: '" + w + "'");
                    }
                }                 
        }
        
        p("\nnTokens:" + nTokens);
        p("\nResults(dedup/unique):" + occurences_names.size());
        p("\nResults(visible)     :" + nCountVisible);
        p("\nResults(hidden)      :" + nCountHidden);
        p("\nResults(skipped)     :" + nCountSkipped);
        p("\nRequested size       :" + _numobj);
        p("\nQuery set site       : " + nCount);
        
        p("   *** MODE: " + nMode);
        switch (nMode) {
           case 4:
               sMode = "tile";
               break;
           case 7:
               sMode = "detail";
               break;
           default:
               sMode = String.valueOf(nMode);
               break;
        }
        
        //if result set was larger than #objects we want to show, then prepare next page
        if (bShowNext) {
            p("It's time for nexter...");
            p("Last date: '" + sLast + "'");
            dateUTF = URLEncoder.encode(sLast, "UTF-8");

            sNextLink = "<a href=\"echoClient5.htm?" + 
                        "ftype=" + _filetype + "&" + 
                        "days=" + _daysback + "&" + 
                        "foo=" + _keyin.replaceAll("\"","&quot;") + "&" + 
                        "view=" + sMode + "&" + 
                        "numobj=" + _numobj + "&" + 
                        "numcol=" + _numcol +"&" + 
                        "pw=" + _password +"&" + 
                        "date=" + dateUTF + 
                        "\" target=MAIN>Show next " + _numobj + " results</a>"
                        ;
            sNextLink = "";
            
            sRedirLink = "echoClient5.htm?" + 
                        "ftype=" + _filetype + "&" + 
                        "days=" + _daysback + "&" + 
                        "foo=" + _keyin.replaceAll("\"","&quot;") + "&" + 
                        "view=" + sMode + "&" + 
                        "numobj=" + _numobj + "&" + 
                        "numcol=" + _numcol +"&" + 
                        "pw=" + _password +"&" + 
                        "date=" + dateUTF;
            
        }

        //Iterator It = numbers.keySet().iterator();
        //while (It.hasNext()) {
        //    String sNamer = (String)It.next();
        //    p(sNamer + " - " + numbers.get(sNamer));
        //}

        Boolean firsttime = true;
        double nImgRatio = 1;
        int nCurrent = 0;
        Iterator It;        
        String sHeightNew = "200";

        int nCount2 = 0;
        switch (nMode) {

            case 2:
                res += "<table width='100%';border='0' cellpadding='0' cellspacing='4' style='table-layout:fixed;height:100;'>";
                //res += "<table>";
                //int nCount2 = 0;
                res += "<tr style='text-align:center; vertical-align:middle'>";
                for (int i = 0; i < nCount; i++) {
                    res += "<td><a href ='pic/" + sNames[i] + "'><img border='0' href='pic/" + sNames[i] + "' src='pic/" + sNames[i] + "' width='100%'></a></td>";
                    nCount2++;
                    if (nCount2 > 3) {
                        res += "</tr>";
                        res += "<tr style='text-align:center; vertical-align:middle'>";
                        nCount2 = 0;
                    }
                    res += "</tr></table>";
                }
                break;
            case 1:
                p("Mode1 - Slideshow. Count =" + nCount);

                p("\nnTokens:" + nTokens);
                if (nTokens > 1) {
                    nTokens = nTokens - 1;
                }
                p("\nnTokens2:" + nTokens);
                occurences_hash.sortByValue();
                int npid = Integer.parseInt(_pid);
                p("npid = '" + npid + "'");
                if (npid >= 0) {
                    It = occurences_hash.keySet().iterator();
                    //String sPathFix = "photo.jpg";
                    //res += "new Array(\"" + sPathFix + "\",\"1024\",\"768\",\"hello\"" + ")" + ",\n";
                    boolean bFirst = true;
                    while (It.hasNext()) {
                        String sNamer = (String) It.next();
                        Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                        String sFileName = occurences_names.get(sNamer);
                        String sFileNameOri = sFileName;
                        p(sNamer + " - " + nCount3 + " - " + sFileName);

                        String _clientip = "localhost";                
                        String sPath = getSuperColumn2(sNamer, "paths", 2);

                        //sNamer = "L0M6L1VzZXJzL0FsZWphbmRyby9QaWN0dXJlcy9QaG90byBTdHJlYW0vTXkgUGhvdG8gU3RyZWFtL0lNR18wMDEzLkpQRy8=";
                        if (sPath.length() > 0) {
                            String sComma = "," + "\n";
                            if (bFirst) {
                                sComma = "";
                                bFirst = false;
                            }
                            res += sComma + "new Array(\"" + sPath + "\",\"1024\",\"768\",\"hello\"" + ")";                            
                        }                   
                    }    
                }
               
                //for (int i = 0; i < nCount; i++) {
                    //p(sNames[i]);
                    //res += "new Array(\"" + sNames[i] + "\",\"1024\",\"768\",\"hello\"" + ")";
                    //if (i < nCount - 1) {
                    //    res += ",";
                    //}
                //}
                break;
            case 3:
                p("Mode3!!!" + nCount);
                res += "<table>";
                //res += "<table width='100%';border='0' cellpadding='0' cellspacing='4' style='table-layout:fixed;height:100;'>";
                //int nCount2 = 0;
                res += "<tr style='text-align:center; vertical-align:middle'>";
                for (int i = 0; i < nCount; i++) {
                    res += "<td><a href ='test4.php?foo=" + sNames[i] + "'><img border='0' href='pic/" + sNames[i] + "' src='pic/" + sNames[i] + "' width='100%'></a></td>";
                    nCount2++;
                    if (nCount2 > 7) {
                        res += "</tr>";
                        res += "<tr style='text-align:center; vertical-align:middle'>";
                        nCount2 = 0;
                    }
                }
                res += "</tr></table>";
                break;
            case 4:
                //tile view
                p("Mode4!!! tile view: " + nCount);

                res += "<tr>Displaying " + occurences_names.size() + " results." + sNextLink + "</tr>";
            
                res += "<form action=\"bulker.php\" method=get autocomplete=\"off\" onsubmit=\"showLoading();\">";
                
                if (bShowNext) {
                    res += "<INPUT TYPE=button value=\"Next " + _numobj + " results\" onclick=\"golink('" + sRedirLink + "','" + _key + "',1)\"/>";
                }
                
                res += "<INPUT TYPE=\"hidden\" NAME=\"ftype\" id=\"ftype\" VALUE=\"" + _filetype + "\">";
                res += "<INPUT TYPE=\"hidden\" NAME=\"days\" id=\"ndays\" VALUE=\"" + _daysback + "\">";
                res += "<INPUT TYPE=\"hidden\" NAME=\"foo\" VALUE=\"" + _key + "\">";
                res += "<INPUT TYPE=\"hidden\" NAME=\"view\" VALUE=\"" + sMode + "\">";
                res += "<INPUT TYPE=\"hidden\" NAME=\"numobj\" VALUE=\"" + _numobj + "\">";
                res += "<INPUT TYPE=\"hidden\" NAME=\"numcol\" VALUE=\"" + _numcol + "\">";
                res += "<INPUT TYPE=\"hidden\" ID=\"date\" NAME=\"date\" VALUE=\"" + URLEncoder.encode(sFirst, "UTF-8") + "\">";
                res += "<INPUT TYPE=\"hidden\" ID=\"DeleteTag\" NAME=\"DeleteTag\" VALUE=\"\">";
                
                res += "<INPUT TYPE=\"text\" SIZE=15 NAME=\"tag\" ID=\"tag\">";
                res += "<INPUT TYPE=\"submit\" NAME=\"hide selected\" VALUE=\"apply tag\">";
                res += "<input type=\"checkbox\" onClick=\"togglechk(this.checked)\" /> Toggle All<br/>";
                
                res += "<table>";
                res += "<tr style='text-align:center; vertical-align:middle'>";

                
                p("\nnTokens:" + nTokens);
                if (nTokens > 1) {
                    nTokens = nTokens - 1;
                }
                p("\nnTokens2:" + nTokens);
                occurences_hash.sortByValue();


                firsttime = true;
                nCurrent = 0;
                It = occurences_hash.keySet().iterator();
                
                while (It.hasNext()) {
                    String sNamer = (String) It.next();
                    Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                    String sFileName = occurences_names.get(sNamer);
                    String sFileNameOri = sFileName;
                    
                    //p(sNamer + " - " + nCount3 + " - " + sFileName);
                    if (firsttime) {
                        nCurrent = nCount3;
                        firsttime = false;
                        //res += "<td>nTokens: " + nCurrent + "<br></td>";
                    }

                    if (!nCount3.equals(nCurrent)) {
                        //res += "<td>nTokens: " + nCount3 + "<br></td>";
                        nCurrent = nCount3;
                    }
                    //if (nCount3.equals(nTokens)) {
                    String sPic = sNamer + ".jpg";
                   
                    
                    boolean bVector = false;
                    String sVector = "";

                    if (is_music(sFileName)) {
                        String sFile = _root + File.separatorChar + "cass" + File.separatorChar + "pic" + File.separatorChar + sPic;
                        //p("*** looking for file: " + sFile);
                        File f = new File(sFile);

                        if (!f.exists()) {
                            sPic = get_thumb(sFileName);
                            bVector=true;
                            sVector = get_vector(sFileName, "100");
                        }
                        //p("sPic: " + sPic);
                        String sSongTitle = get_row_attribute(keyspace, "Standard1", sNamer, "title");
                        String sSongArtist = get_row_attribute(keyspace, "Standard1", sNamer, "artist");
                        if (sSongTitle.length() > 40) {
                            sSongTitle = sSongTitle.substring(0, 39);
                        }
                        if (sSongArtist.length() > 40) {
                            sSongArtist = sSongArtist.substring(0, 39);
                        }
                        if (sSongTitle.length() > 0) {
                            if (sSongArtist.length() > 0) {
                                sFileName = sSongTitle + " <br> " + sSongArtist;    
                            } else {
                                sFileName = sSongTitle;
                            }
                        } else {
                            if (sSongArtist.length() > 0) {
                                sFileName += " <br> " + sSongArtist;
                            }
                            if (sFileName.length() > 40) {
                                sFileName = sFileName.substring(0, 39);
                            }
                        }                        
                    }
                    
                    if (!is_photo(sFileNameOri) && !is_music(sFileNameOri)) {
                        //sPic = get_thumb(sFileNameOri);
                        bVector = true;
                        sVector = get_vector(sFileNameOri, "100");
                    }
                    
                    String srcPic="pic/"+sPic;
                    
                    if(is_photo(sFileNameOri)){
                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
                        if (fh.exists()) { 
                           File fh64= new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".alt64");
                           if(fh64.exists()){
                               //Si ya existe la version base64 la leo
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
                                srcPic="data:image/jpg;base64,"+out.toString();
                                p("Vista Tile:Se carga imagen desde pic folder");
                           } else{
                                //Si existe en pic armo el base64
                               FileInputStream is=new FileInputStream(fh);
                               ByteArrayOutputStream out=new ByteArrayOutputStream();
                               FileWriter writer=new FileWriter(fh64);
                                    
                               byte[] buf =new byte[1024*1024];
                               try {
                                    int n;

                                    while ((n = is.read(buf)) > 0) {
                                        out.write(buf, 0, n);
                                    }
                                     srcPic= Base64.encodeToString(out.toByteArray(), false);
                                    writer.write(srcPic.toCharArray());
                                } finally {
                                     is.close();
                                     writer.close();
                                     out.close();

                                }          
                                srcPic="data:image/jpg;base64,"+ srcPic;
                                p("Vista Tile:Se genera imagen base64");
                                
                               
                           }
                            
                        }else{
                            p("Vista Tile:No se encuentra imagen en PIC, se deja enlace a la imagen");
                        }
                    }
                    
                    //p("ftype = '" + _filetype + "'");
                    //p("sFileNameOri = '" + sFileNameOri + "'");
                    //p("sFileNametest = '" + sFileNameOri.contains(_filetype) + "'");
                    //int npos = sFileNameOri.indexOf(_filetype);
                    //p("sFileNamepos = '" + npos + "'");
                    boolean bShow = false;
                    if (_filetype.equals(".all")) {
                        bShow = true;
                        //p(".all case");
                    } else {
                        //p("[1]");
                        String ftype2 = _filetype.trim();
                        //p("[2]");
                        //p("ftype2 = '" + ftype2 + "'");                    
                        //p("ftype2 mov = '" + ftype2.contains(".mov") + "'");                                            
                        //p("ftype2 mov = '" + ftype2.equals(".mov") + "'");                                            
                        bShow = checkifshow(sFileNameOri, ftype2);
                    }
                    
                    //String sHidden = get_row_attribute(keyspace, "Standard1", sNamer, "hidden");
                    //p("\nKey: '" + _key + "'");
                    
                    if (_key.contains("hidden:")) {
                        p("\n *** Checking for Hidden Key.");
                        String sHidden = get_row_attribute(keyspace, "Standard1", sNamer, "hidden");
                        if (sHidden.length() > 0) {
                            p("Object is hidden: " + sNamer);
                            String sHiddenPw = "hidden:" + sHidden;
                            p("Obj pw = '" + sHiddenPw + "'");
                            p("_keyin = '" + _keyin + "'");
                            bShow = false;
                            if (sHiddenPw.equals(_keyin)) {
                                bShow = true;
                            }
                        }                        
                    } 
                    
                    if (bShow) {
                        
                        String sHeight = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                        String sWidth = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");

                        p("height = '" + sHeight + "' " + sHeight.length());
                        p("width = " + sWidth + "' " + sWidth.length());

                        sHeightNew = sHeight.toString();
                        String sWidthNew = sWidth.toString();
                        
                        double nHeight = 0;
                        double nWidth = 0;
                        try {
                            nHeight = Double.parseDouble(sHeight);
                            nWidth = Double.parseDouble(sWidth);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        
                        if (nHeight > 0 || nWidth > 0) {

                            if (nHeight > 200 || nWidth> 200 ) {
                                double hImgRatio = 200 / nHeight;
                                double wImgRatio = 200 / nWidth;
                                nImgRatio= Math.min(hImgRatio, wImgRatio);

                                Double dHeightNew = nHeight*nImgRatio;      
                                //int nHeightNew = (int)dHeightNew;
                                //sHeightNew = Integer.toString(nHeightNew);
                                sHeightNew = dHeightNew.toString();

                                Double dWidthNew = nWidth*nImgRatio;      
                                sWidthNew = dWidthNew.toString();

                            } 

                        } else{
                              sHeightNew = "200";
                              sWidthNew = "200";
                        }

                        
                        p("height new = " + sHeightNew);
                        p("width new = " + sWidthNew);
                        
                        res += "<td valign='top' align='center'><div><table id='img"+sNamer+"' cellspacing=0 onmouseout='hideButtonsTile(\""+sNamer+ "\");' onmouseover='showButtonsTile(\""+sNamer+ "\");'>";
                        
                        if (!bVector) {
                            //<td valign=center align=center><input type =\"checkbox\" class=\"checkbox\" name=\"" + sNamer + "\">" + "</td>
                            res += "<tr><td  height=200 width=200  valign=center align=center><a href ='test4.php?foo=" + sNamer + "&pw=" + _password + "'><img border='0'  width="+sWidthNew+" height=" + sHeightNew + " src='" + srcPic + "'></a>" + 
                                    "</td></tr></table>";                            
                        } else {
                            res += "<tr><td  height=200 width=200  valign=center align=center><a href ='test4.php?foo=" + sNamer + "&pw=" + _password + "'>" + sVector + "</a>" + 
                                    "</td></tr></table>";                                                        
                        }
                        
                        String sDate = get_row_attribute(keyspace, "Standard1", sNamer, "date_modified");

                        int nError = 0;
                        String sViewURL = "";
                        try {
                            //p("   [B]");
                            sViewURL = get_row_attribute(keyspace, "Standard1", sNamer, "ViewLink3");
                        } catch (Exception ex) {
                            p("   [Be]");
                            nError += 1;
                        }
                        
                        
                        //p("   [C]");
                        
                        String sPlayURL = "";
                        try {
                            sPlayURL = get_row_attribute(keyspace, "Standard1", sNamer, "PlayLink3");
                        } catch (Exception ex) {
                            p("   [Ce]");
                            nError += 1;
                        }
                       
                        //p("   [D]");
                        sURLPack sURLpack = new sURLPack();
                        boolean bHaveServerLink = false;

                        String sOpenURL = "";
                        
                        if (sViewURL.length() < 1) {
                            bHaveServerLink = false;
                            try {
                                sURLpack = get_remote_link(sNamer,"paths", true, _cloudhosted, _clientIP);    
                                
                                //p("Viewlink ---> '" + sURLpack.sViewURL + "'");
                                if (sURLpack.sViewURL.length() > 0 && !sURLpack.sViewURL.equals("ERROR")) {
                                    sViewURL = sURLpack.sViewURL;
                                    //int ires = insert_column("Keyspace1b","Standard1", sNamer, "ViewLink3", sViewURL); 
                                }
                                else {
                                    sViewURL = "";
                                }
                                    

                                //p("Playlink ---> '" + sURLpack.sPlayURL + "'");
                                if (sURLpack.sPlayURL.length() > 0 && !sURLpack.sPlayURL.equals("ERROR")) {
                                    sPlayURL = sURLpack.sPlayURL;
                                    //int ires = insert_column("Keyspace1b","Standard1", sNamer, "PlayLink3", sPlayURL);  
                                }

                                //p("Openlink ---> '" + sURLpack.sOpenURL + "'");
                                if (sURLpack.sOpenURL.length() > 0 && !sURLpack.sOpenURL.equals("ERROR")) {
                                    sOpenURL = sURLpack.sOpenURL;
                                    //int ires = insert_column("Keyspace1b","Standard1", sNamer, "PlayLink3", sPlayURL);  
                                }

                            } catch (Exception ex) {
                                nError += 1;
                            }
                        } else {
                            bHaveServerLink = true;
                        }
                        
                        //InetAddress clientIP = InetAddress.getLocalHost();
                        InetAddress clientIP = getLocalAddress();
                        String sLocalIP = "127.0.0.1";
                        if (clientIP != null) {
                            sLocalIP = clientIP.getHostAddress();;                            
                        }
                        
                        //sViewURL = "";
                        String sSendURL = "";
                        String sFolderURL = "";
                        String sHostIP = "";
                        String sFile = "";
                        
                        if (nError < 1 || _cloudhosted) {
                                                        
                            if (!bHaveServerLink && !_cloudhosted) {
                                sViewURL = sURLpack.sViewURL;                                    
                            }
                            String sViewURLOri = sViewURL;    
                            
                            sURLPack tmpURLpack = new sURLPack();                                        
                            
                            //VIEW LINK
                            sViewURL = gen_view_link(_host, _port, sViewURL, bHaveServerLink, _cloudhosted, sNamer, sURLpack, sFileName, _key, nImgRatio, sLocalIP);
                            
                            //SEND LINK
                            tmpURLpack = gen_send_link(sViewURL, sViewURLOri, _cloudhosted, sNamer, sURLpack, bHaveServerLink, _clientIP, sHeightNew, _key, _host, _port);
                            _host = tmpURLpack.sHostIP;
                            _port = tmpURLpack.sHostPort;
                            sSendURL = tmpURLpack.sSendURL;
                                                        
                            //PLAY LINK
                            sPlayURL = gen_play_link(sPlayURL, sLocalIP, _key);

                            //OPEN AND FOLDER LINK
                            tmpURLpack = gen_open_link(sOpenURL, sURLpack, sNamer, _cloudhosted, _clientIP, _key);                            
                            sOpenURL = tmpURLpack.sOpenURL;
                            sFolderURL = tmpURLpack.sFolderURL;                                                       
                            
                        } else {
                            sViewURL = "ERROR";
                            sPlayURL = "ERROR";
                        }
                        
                        String _hashkey = "hashesm";
                        String _clientip = _host;

                        String sHashLink =  getSuperColumn(sNamer, _hashkey, 1, _password, _clientip, sMode, _daysback, _numcol, _numobj, sLast, _filetype);

                        sPlayURL=sPlayURL.replace("id_","id='serverlnk"+sNamer+"' class='jqueryhidden'");
                         
                        sViewURL=sViewURL.replace("class='jqueryhidden'", "class='jqueryhidden'   onmouseover='showButtonsTile(\""+sNamer+ "\");'");
                        sPlayURL =sPlayURL.replace("class='jqueryhidden'", "class='jqueryhidden'  onmouseover='showButtonsTile(\""+sNamer+ "\");'");
                        sSendURL =sSendURL.replace("class='jqueryhidden'", "class='jqueryhidden'  onmouseover='showButtonsTile(\""+sNamer+ "\");'");
                        sOpenURL =sOpenURL.replace("class='jqueryhidden'", "class='jqueryhidden'   onmouseover='showButtonsTile(\""+sNamer+ "\");'");
                        sFolderURL =sFolderURL.replace("class='jqueryhidden'", "class='jqueryhidden'   onmouseover='showButtonsTile(\""+sNamer+ "\");'");
                        
                        String sCopyInfo = getNumberofCopies("paths", sNamer, _clientIP,sLocalIP);
                        
                        p("CopyInfo = '" + sCopyInfo + "'");

                        
                        int nCopies = Integer.parseInt(sCopyInfo.substring(0, sCopyInfo.indexOf(",")));                        
                        int nNodes = Integer.parseInt(sCopyInfo.substring(sCopyInfo.indexOf(",")+1,sCopyInfo.length())); 
                                               
                        p("There are " + nCopies + " copies of file " + sNamer);
                        p("There are " + nNodes + " that has a copy of file " + sNamer);
                        

                        int lineasEtiquetas=0;    
                        if(sHashLink.trim().length()>0){     
                                //Cambio la estructura de la tabla generada para que quede en 1 columna
                                int count=0;
                                StringBuilder sHashLinkAux=new StringBuilder(sHashLink);
                                boolean entre=
                                        false;
                                int i=0;
                                while(sHashLinkAux.substring(i).contains("</td>")){
                                    i=sHashLinkAux.indexOf("</td>",i)+"</td>".length();

                                    count++;
                                  
                                    if(count==1){
                                        count=0;
                                        entre=false;
                                        sHashLinkAux.insert(i,"</tr><tr>");
                                        lineasEtiquetas++;
                                    }else{
                                        entre=true;
                                        lineasEtiquetas++;
                                    }
                                   
                                }
                                
                                if(entre ){
                                    sHashLinkAux.insert(sHashLinkAux.toString().indexOf("</table>"),"</tr><tr>");
                                }
                                
                                if(lineasEtiquetas>1){
                                       sHashLink="<table><tr valign='top'><td align='right'><div class='jquerycursorpointer' style='cursor:pointer;color:blue;float:left;witdh:100%;aling:right;' id='tagsTblExpCont"+sNamer+"' onclick=\"expCont('"+sNamer+"','tagsTbl"+sNamer+"','tagsTblExpCont"+sNamer+"',25);\">+</div>"+
                                               "</td><td align='center'>"+sHashLinkAux.toString()+"</td></tr></table>";
                                       
                                }else{
                                     sHashLink=sHashLinkAux.toString();
                                }
                                //FIN Cambio la estructura de la tabla generada
                                
                        }     
                        
                        //Esto es para los nombres largos en los archivos
                        String sFileNameAux=URLDecoder.decode(sFileName,"UTF-8").trim();
                        if(sFileNameAux.trim().length()>36){
                            String sExt = "";
                            if (sFileNameAux.contains(".")) {
                                sExt = sFileNameAux.substring(sFileNameAux.indexOf("."));
                            }
                            sFileNameAux=sFileNameAux.substring(0, 32) + "..." + sExt;
                        }
                        String sColor = "red";
                        if (nCopies > 1) {
                            sColor = "green";
                        }
                        String sCopies = "<font style='background-color:" + sColor + ";color:white;'>&nbsp" + nCopies + "&nbsp</font>";
                        res+="<table style='width=150px;text-overflow:ellipsis;white-space:nowrap;' ><tr>" + 
                        "<td><font size=1 style=\"background-color: white;'\"><div style='width=150px;text-overflow:ellipsis;white-space:nowrap;'>" + sFileNameAux + " " + sCopies + "</div></font></td>" + 
                        "</tr>"+
                        "<tr>" + 
                        "<td><font size=1 style=\"background-color: white;width=150px;text-overflow:ellipsis;\">" + sDate +"</font>&nbsp&nbsp&nbsp<input type ='checkbox' class='checkbox' name='" + sNamer + "'></td>" + 
                        "</tr>" ;

                        res+="<table  cellspacing=0 > <tr><td></td></tr><tr><td ><div expCont='OFF' id='tagsTbl"+sNamer+"' style='width:auto;overflow:hidden;height:25px'><font size=1 style=\"background-color: yellow\">" + 
                        sHashLink + 
                        "</font></div></td>" +                                
                        "</tr></table>";

                        res += "<table cellspacing=0 style='max-width:200px' id='bnts"+sNamer+"' class='jqueryhidden' onmouseout='hideButtonsTile(\""+sNamer+ "\");' onmouseover='showButtonsTile(\""+sNamer+ "\");'>"+
                                "<tr>" +
                                "<td><font size=2>" + 
                                    sViewURL + "&nbsp&nbsp" +
                                    sPlayURL + "&nbsp&nbsp" +
                                    sSendURL + "&nbsp&nbsp" +
                                  "</font></td></tr><tr>" +  
                                  "<td><font size=2>" + 
                                    sOpenURL + "&nbsp&nbsp" +
                                    sFolderURL + "&nbsp&nbsp" +
                                   
                                    "</font></td>" +     
                                "</tr>" +
                                "<tr><td height=20>&nbsp&nbsp</td></tr>"+
                                
                                "</table></td>";
                        nCount2++;
                        int nNumCol = Integer.parseInt(_numcol);
                        //p("nNumCol = '" + nNumCol + "'");
                        if (nCount2 >= nNumCol) {
                            res += "<td width='40px'>&nbsp&nbsp</td></tr>";
                            res += "<tr style='text-align:center; vertical-align:middle'>";
                            nCount2 = 0;
                        }
                    }

                }
                res += "</tr></form></table>";
                break;
                
            case 5:
                occurences_hash.sortByValue();
                Iterator It2 = occurences_hash.keySet().iterator();
                while (It2.hasNext()) {
                    String sNamer = (String) It2.next();
                    Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                    String sFileName = occurences_names.get(sNamer);
                    p(sNamer + " - " + nCount3 + " - " + sFileName);
                                        String sPic = sNamer + ".jpg";
                    if (sFileName.toLowerCase().contains("mp3") || sFileName.toLowerCase().contains("m4a")) {
                        String sFile = _root + File.separatorChar + "cass" + File.separatorChar + "pic" + File.separatorChar + sPic;
                        p("*** looking for file: " + sFile);
                        File f = new File(sFile);
                        if (!f.exists()) {
                            sPic = get_thumb(sFileName);
                        }
                        p("sPic: " + sPic);
                    }
                    
                    if (!is_photo(sFileName) && !is_music(sFileName)) {
                        sPic = get_thumb(sFileName);
                    }
                    
                    
                    res += "<li><a href ='test4.php?foo=" + sNamer + "'><img border='0' width=200 height=200 src='pic/" + sPic + "'>" + "<font color='black'>" + sFileName + "</font></a></li>";
                }
                
            case 6:
                //mode polar
                occurences_hash.sortByValue();
                Iterator It3 = occurences_hash.keySet().iterator();
                while (It3.hasNext()) {
                    String sNamer = (String) It3.next();
                    Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                    String sFileName = occurences_names.get(sNamer);
                    p(sNamer + " - " + nCount3 + " - " + sFileName);
                                        String sPic = sNamer + ".jpg";
                    if (sFileName.toLowerCase().contains("mp3") || sFileName.toLowerCase().contains("m4a")) {
                        String sFile = _root + File.separatorChar + "cass" + File.separatorChar + "pic" + File.separatorChar + sPic;
                        p("*** looking for file: " + sFile);
                        File f = new File(sFile);
                        if (!f.exists()) {
                            sPic = get_thumb(sFileName);
                        }
                        p("sPic: " + sPic);
                    }
                    
                    if (!is_photo(sFileName)) {
                        sPic = get_thumb(sFileName);                        
                    }
                    
                    res += "<div class=\"polaroid\"><img src=\"pic/" + sPic+ "\" alt=\"" + sNamer + "\" /><p>" + sNamer + "</p></div>";
                }
                
            case 7:
                //detail view
                p("Mode7 (detail)!!!" + nCount);
                res += "<table>";
                res += "<tr>Displaying " + occurences_names.size() + " results." + sNextLink + "</tr>";
                res += "<form action=\"bulker.php\" method=get autocomplete=\"off\" onsubmit=\"showLoading();\">";

                if (bShowNext) {
                    res += "<INPUT TYPE=button value=\"Next" + _numobj + " results\" onclick=\"golink('" + sRedirLink + "','',1)\"/>";
                }

                res += "<INPUT TYPE=\"hidden\" NAME=\"ftype\" id=\"ftype\" VALUE=\"" + _filetype + "\">";
                res += "<INPUT TYPE=\"hidden\" NAME=\"days\" id=\"ndays\" VALUE=\"" + _daysback + "\">";
                res += "<INPUT TYPE=\"hidden\" NAME=\"foo\" VALUE=\"" + _key + "\">";
                res += "<INPUT TYPE=\"hidden\" NAME=\"view\" VALUE=\"" + sMode + "\">";
                res += "<INPUT TYPE=\"hidden\" NAME=\"numobj\" VALUE=\"" + _numobj + "\">";
                res += "<INPUT TYPE=\"hidden\" NAME=\"numcol\" VALUE=\"" + _numcol + "\">";
                res += "<INPUT TYPE=\"hidden\" ID=\"DeleteTag\" NAME=\"DeleteTag\" VALUE=\"\">";

                res += "<INPUT TYPE=\"text\" SIZE=15 NAME=\"tag\" ID=\"tag\">";
                res += "<INPUT TYPE=\"submit\" NAME=\"hide selected\" VALUE=\"apply tag\">";
                res += "<input type=\"checkbox\" onClick=\"togglechk(this.checked)\" /> Toggle All<br/>";
                res += "<tr><th>Type</th><th>FileName</th><th>Date</th></tr>";
                res += "<tr style='text-align:center; vertical-align:middle'>";

                //p("\nnTokens:" + nTokens);
                if (nTokens > 1) {
                    nTokens = nTokens - 1;
                }
                //p("\nnTokens2:" + nTokens);
                occurences_hash.sortByValue();


                firsttime = true;
                nCurrent = 0;
                It = occurences_hash.keySet().iterator();

                p("# items to process: " + occurences_hash.size());

                while (It.hasNext()) {
                    String sNamer = (String) It.next();
                    Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                    String sFileName = occurences_names.get(sNamer);
                    String sFileNameOri = sFileName;
                    p(sNamer + " - " + nCount3 + " - " + sFileName);
                    if (firsttime) {
                        nCurrent = nCount3;
                        firsttime = false;
                        //res += "<td>nTokens: " + nCurrent + "<br></td>";
                    }

                    if (!nCount3.equals(nCurrent)) {
                        //res += "<td>nTokens: " + nCount3 + "<br></td>";
                        nCurrent = nCount3;
                    }
                    //if (nCount3.equals(nTokens)) {
                    String sPic = sNamer + ".jpg";
                    
                    p("Filename: '" + sFileName + "'");
                    
                    boolean bVector = false;
                    String sVector = "";
                    if (is_music(sFileName)) {
                        //music case
                        String sFile = _root + File.separatorChar + "cass" + File.separatorChar + "pic" + File.separatorChar + sPic;
                        p("*** looking for file: " + sFile);
                        File f = new File(sFile);
                        if (!f.exists()) {
                            sPic = get_thumb(sFileName);
                            sVector = get_vector(sFileName, "40");
                            bVector = true;
                        }
                        p("sPic: " + sPic);
                        
                        String sSongTitle = "ERROR_";
                        while (sSongTitle.contains("ERROR_")) {
                            sSongTitle = get_row_attribute(keyspace, "Standard1", sNamer, "title");    
                        }

                        String sSongArtist = "ERROR_";
                        while (sSongArtist.contains("ERROR_")) {
                            sSongArtist = get_row_attribute(keyspace, "Standard1", sNamer, "artist");
                        }

                        if (sSongTitle.length() > 40) {
                            sSongTitle = sSongTitle.substring(0, 39);
                        }
                        if (sSongArtist.length() > 40) {
                            sSongArtist = sSongArtist.substring(0, 39);
                        }
                        if (sSongTitle.length() > 0) {
                            if (sSongArtist.length() > 0) {
                                sFileName = sSongTitle + " <br> " + sSongArtist;    
                            } else {
                                sFileName = sSongTitle;
                            }
                        } else {
                            if (sSongArtist.length() > 0) {
                                sFileName += " <br> " + sSongArtist;
                            }
                            if (sFileName.length() > 40) {
                                sFileName = sFileName.substring(0, 39);
                            }
                        }                        
                    } else {
                        
                    }
                                        
                    if (!is_photo(sFileNameOri) && !is_music(sFileNameOri)) {
                        //sPic = get_thumb(sFileNameOri);
                        bVector = true;
                        sVector = get_vector(sFileNameOri, "40");
                    }
                       
                    String srcPic="pic/"+sPic;
                    
                    if(is_photo(sFileNameOri)){
                        File fh = new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".jpg");
                        if (fh.exists()) { 
                           File fh64= new File(THUMBNAIL_OUTPUT_DIR, sNamer + ".alt64");
                           if(fh64.exists()){
                               //Si ya existe la version base64 la leo
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
                                srcPic="data:image/jpg;base64,"+out.toString();
                                p("Vista Tile:Se carga imagen desde pic folder");
                           } else{
                                //Si existe en pic armo el base64
                               FileInputStream is=new FileInputStream(fh);
                               ByteArrayOutputStream out=new ByteArrayOutputStream();
                               FileWriter writer=new FileWriter(fh64);
                                    
                               byte[] buf =new byte[1024*1024];
                               try {
                                    int n;

                                    while ((n = is.read(buf)) > 0) {
                                        out.write(buf, 0, n);
                                    }
                                    srcPic=Base64.encodeToString(out.toByteArray(), false);
                                    writer.write(srcPic.toCharArray() );
                                } finally {
                                     is.close();
                                     writer.close();

                                }          
                                srcPic="data:image/jpg;base64,"+ srcPic;
                                p("Vista Tile:Se genera imagen base64");
                                
                                out.close();
                           }
                            
                        }else{
                            p("Vista Tile:No se encuentra imagen en PIC, se deja enlace a la imagen");
                        }
                    }

                    boolean bShow = false;
                    if (_filetype.equals(".all")) {
                        bShow = true;
                        //p(".all case");
                    } else {
                        //p("[1]");
                        String ftype2 = _filetype.trim();
                        //p("[2]");
                        //p("ftype2 = '" + ftype2 + "'");                    
                        //p("ftype2 mov = '" + ftype2.contains(".mov") + "'");                                            
                        //p("ftype2 mov = '" + ftype2.equals(".mov") + "'");  
                        bShow = checkifshow(sFileNameOri, ftype2);
                    }              

                    p("bShow = " + bShow);

                    //p("\nKey: '" + _key + "'");
                    if (_key.contains("hidden:")) {
                        p("\n *** Checking for Hidden Key.");
                        String sHidden = get_row_attribute(keyspace, "Standard1", sNamer, "hidden");
                        if (sHidden.length() > 0) {
                            p("Object is hidden: " + sNamer);
                            String sHiddenPw = "hidden:" + sHidden;
                            p("Obj pw = '" + sHiddenPw + "'");
                            p("_keyin = '" + _keyin + "'");
                            bShow = false;
                            if (sHiddenPw.equals(_keyin)) {
                                bShow = true;
                            }
                        }                        
                    } 

                    
                    //p("bshow = " + bShow + "'");
                    if (bShow) {
                        
                        String sHeight = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                        String sWidth = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");

                        p("height = '" + sHeight + "' " + sHeight.length());
                        p("width = " + sWidth + "' " + sWidth.length());

                        sHeightNew = sHeight.toString();
                        String sWidthNew = sWidth.toString();
                        
                        double nHeight = 0;
                        double nWidth = 0;
                        try {
                            nHeight = Double.parseDouble(sHeight);
                            nWidth = Double.parseDouble(sWidth);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        
                        if (nHeight > 0 || nWidth > 0) {

                            if (nHeight > 50 || nWidth> 50 ) {
                                double hImgRatio = 50 / nHeight;
                                double wImgRatio = 50 / nWidth;
                                nImgRatio= Math.min(hImgRatio, wImgRatio);

                                Double dHeightNew = nHeight*nImgRatio;      
                                //int nHeightNew = (int)dHeightNew;
                                //sHeightNew = Integer.toString(nHeightNew);
                                sHeightNew = dHeightNew.toString();

                                Double dWidthNew = nWidth*nImgRatio;      
                                sWidthNew = dWidthNew.toString();

                            } 

                        } else{
                              sHeightNew = "50";
                              sWidthNew = "50";
                        }

                        
                        p("height new = " + sHeightNew);
                        p("width new = " + sWidthNew);
                        
                        
                        
                        res += "<td><table  onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'>";

                        if (!bVector) {
                            res += "<tr><td><a href ='test4.php?foo=" + sNamer + "&pw=" + _password + "'><img border='0' width="+sWidthNew+" height="+sHeightNew+" src='" + srcPic + "' ></a>" + 
                                    "<input type =\"checkbox\" class=\"checkbox\" name=\"" + sNamer + "\">" +                              
                                    "</td>";                            
                        } else {                            
                            res += "<tr><td height=80 width=80><a href ='test4.php?foo=" + sNamer + "&pw=" + _password + "'>" + sVector + "</a>" + 
                                    "<input type =\"checkbox\" class=\"checkbox\" name=\"" + sNamer + "\">" +                              
                                    "</td>";                            
                        }

                        //p("   [A]");
                        String sDate = get_row_attribute(keyspace, "Standard1", sNamer, "date_modified");
                        
                        //InetAddress clientIP = InetAddress.getLocalHost();
                        InetAddress clientIP = getLocalAddress();
                        String sLocalIP = "127.0.0.1";
                        if (clientIP != null) {
                            sLocalIP = clientIP.getHostAddress();;                            
                        } else {
                            
                        }

                        int nError = 0;                        
                        String sViewURL = "";
                        try {
                            //p("   [B]");
                            sViewURL = get_row_attribute(keyspace, "Standard1", sNamer, "ViewLink3");
                        } catch (Exception ex) {
                            p("   [Be]");
                            nError += 1;
                        }
                        
                        //p("   [C]");
                        
                        String sPlayURL = "";
                        try {
                            sPlayURL = get_row_attribute(keyspace, "Standard1", sNamer, "PlayLink3");
                        } catch (Exception ex) {
                            p("   [Ce]");
                            nError += 1;
                        }
                        
                        //p("   [D]");
                        sURLPack sURLpack = new sURLPack();
                        boolean bHaveServerLink = false;

                        String sOpenURL = "";

                        String sSendURL = "";
                        String sFolderURL = "";
                        String sHostIP = "";

                        if (sViewURL.length() < 1) {
                            bHaveServerLink = false;

                            try {
                                sURLpack = get_remote_link(sNamer,"paths", true, _cloudhosted, _clientIP);    

                                //p("Viewlink ---> '" + sURLpack.sViewURL + "'");
                                if (sURLpack.sViewURL.length() > 0 && !sURLpack.sViewURL.equals("ERROR")) {
                                    sViewURL = sURLpack.sViewURL;
                                    //int ires = insert_column("Keyspace1b","Standard1", sNamer, "ViewLink3", sViewURL); 

                                }

                                //p("Playlink ---> '" + sURLpack.sPlayURL + "'");
                                if (sURLpack.sPlayURL.length() > 0 && !sURLpack.sPlayURL.equals("ERROR")) {
                                    sPlayURL = sURLpack.sPlayURL;
                                    //int ires = insert_column("Keyspace1b","Standard1", sNamer, "PlayLink3", sPlayURL);  
                                }

                                //p("Openlink ---> '" + sURLpack.sOpenURL + "'");
                                if (sURLpack.sOpenURL.length() > 0 && !sURLpack.sOpenURL.equals("ERROR")) {
                                    sOpenURL = sURLpack.sOpenURL;
                                    //int ires = insert_column("Keyspace1b","Standard1", sNamer, "PlayLink3", sPlayURL);  
                                }

                                
                            } catch (Exception ex) {
                                nError += 1;
                            }
                        } else {
                            bHaveServerLink = true;
                        }
                        
                        clientIP = getLocalAddress();
                        sLocalIP = "127.0.0.1";
                        if (clientIP != null) {
                            sLocalIP = clientIP.getHostAddress();;                            
                        }
                        
                        //sViewURL = "";
                        sSendURL = "";
                        sFolderURL = "";
                        sHostIP = "";
                        String sFile = "";
                        
                        if (nError < 1 || _cloudhosted) {
                                                        
                            if (!bHaveServerLink && !_cloudhosted) {
                                sViewURL = sURLpack.sViewURL;                                    
                            }
                            String sViewURLOri = sViewURL;    
                            
                            sURLPack tmpURLpack = new sURLPack();                                        
                            
                            //VIEW LINK
                            sViewURL = gen_view_link(_host, _port, sViewURL, bHaveServerLink, _cloudhosted, sNamer, sURLpack, sFileName, _key, nImgRatio, sLocalIP);
                            
                            //SEND LINK
                            tmpURLpack = gen_send_link(sViewURL, sViewURLOri, _cloudhosted, sNamer, sURLpack, bHaveServerLink, _clientIP, sHeightNew, _key, _host, _port);
                            _host = tmpURLpack.sHostIP;
                            _port = tmpURLpack.sHostPort;
                            sSendURL = tmpURLpack.sSendURL;
                                                        
                            //PLAY LINK
                            sPlayURL = gen_play_link(sPlayURL, sLocalIP, _key);

                            //OPEN AND FOLDER LINK
                            tmpURLpack = gen_open_link(sOpenURL, sURLpack, sNamer, _cloudhosted, _clientIP, _key);                            
                            sOpenURL = tmpURLpack.sOpenURL;
                            sFolderURL = tmpURLpack.sFolderURL;                                                      
                           
                            
                        } else {
                            sViewURL = "ERROR";
                            sPlayURL = "ERROR";
                        }

                        
                        String _hashkey = "hashesm";
                        String _clientip = _host;
                        
                        String sHashLink =  getSuperColumn(sNamer, _hashkey, 1, _password, _clientip, sMode, _daysback, _numcol, _numobj, sLast, _filetype);
                        
                        //p("   [F]");
                        //p("ViewLink  ---> '" + sViewURL + "'");
                        //p("PlayLink  ---> '" + sPlayURL + "'");
                        sPlayURL=sPlayURL.replace("id_","id='serverlnk"+sNamer+"' class='jqueryhidden'"); 
                        sHashLink =sHashLink.replace(sNamer+"="+sNamer, "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                        sViewURL=sViewURL.replace("class='jqueryhidden'", "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                        sPlayURL =sPlayURL.replace("class='jqueryhidden'", "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                        sSendURL =sSendURL.replace("class='jqueryhidden'", "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                        sOpenURL =sOpenURL.replace("class='jqueryhidden'", "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                        sFolderURL =sFolderURL.replace("class='jqueryhidden'", "class='jqueryhidden' onmouseout='hideButtons(\""+sNamer+ "\");' onmouseover='showButtons(\""+sNamer+ "\");'");
                     
                        
                        res += "<td><font size=2>" + sFileName +"</font></td>" + 
                                "<td><font size=2>" + sDate + "</font>" + 
                                sViewURL + "&nbsp&nbsp" + 
                                sPlayURL + "&nbsp&nbsp" + 
                                sSendURL + "&nbsp&nbsp" +
                                sOpenURL + "&nbsp&nbsp" +
                                sFolderURL + "</td>" +
                                "<td><div id='tags"+sNamer+"' class='jqueryhidden'><font size=1 style=\"background-color: yellow\"  >" + sHashLink + "</font></div></td>";

                        res += "</tr>" + 
                            "</table></td></div>";

                        p("nCount2  ---> '" + nCount2 + "'");
                        
                        nCount2++;

                        int nNumCol = Integer.parseInt(_numcol);
                        //p("nNumCol = '" + nNumCol + "'");
                        if (nCount2 >= nNumCol) {
                            res += "</tr>";
                            res += "<tr style='text-align:center; vertical-align:middle'>";
                            nCount2 = 0;
                        }  
                    }
                }
                res += "</tr></form></table>";
                break;
            case 8:
                p("Mode1 - Slideshow. Count =" + nCount);

                p("\nnTokens:" + nTokens);
                if (nTokens > 1) {
                    nTokens = nTokens - 1;
                }
                p("\nnTokens2:" + nTokens);
                occurences_hash.sortByValue();
                int npid2 = Integer.parseInt(_pid);
                p("npid = '" + npid2 + "'");
                if (npid2 >= 0) {
                    It = occurences_hash.keySet().iterator();
                    while (It.hasNext()) {
                        String sNamer = (String) It.next();
                        Integer nCount3 = (Integer) occurences_hash.get(sNamer);
                        String sFileName = occurences_names.get(sNamer);
                        String sFileNameOri = sFileName;
                        p(sNamer + " - " + nCount3 + " - " + sFileName);

                        String _clientip = "localhost";                
                        String sPath = getSuperColumn2(sNamer, "paths", 2);
                        String srcImage=sPath;
                       
                        
                        //sNamer = "L0M6L1VzZXJzL0FsZWphbmRyby9QaWN0dXJlcy9QaG90byBTdHJlYW0vTXkgUGhvdG8gU3RyZWFtL0lNR18wMDEzLkpQRy8=";
                        if (sPath.length() > 0) {
                            //res += "<li><img src=\"" + sPath + "\" alt=\"Marsa Alam underawter close up\" /></li>\n";
                            
                            String sHeight = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                            String sWidth = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");

                            p("height = " + sHeight);
                            p("width = " + sWidth);
                                                        
                            res += "<li><img src=\"" + srcImage + "\" carrousel=1 originalWidth=\"" +sWidth + "\" width=\"" +sWidth + "\" originalHeight=\"" + sHeight + "\" height=\"" + sHeight + "\" alt=\"Alterante.\" /></li>\n";

                        
                        
                        }                   
                    }
                    
                   
                }
               

                //for (int i = 0; i < nCount; i++) {
                    //p(sNames[i]);
                    //res += "new Array(\"" + sNames[i] + "\",\"1024\",\"768\",\"hello\"" + ")";
                    //if (i < nCount - 1) {
                    //    res += ",";
                    //}
                //}
                break;
            default:
                break;

        }

        timer.stop();
        String sTime = "<br><br>**Query time: " + timer.getElapsedTime() + " ms";
        if (true) {
        //if (nMode != 4 && nMode !=1 && nMode!=8) {
            res += sTime;
        }
        p(sTime);

        p("\n lenth RES:" + res.length());
        return res;
        
        } catch (IOException OutofMemoryError) {
            p("***OutofMemoryError xception: ");
            OutofMemoryError.printStackTrace();
            //res = "ERROR_MEM";
        } catch (Exception e) {
            
            p("***xception: ");
            e.printStackTrace();
        }
        //catch (Exception TApplicationException) {
        //    p("***TApplicationException: ");
        //    p(TApplicationException.getMessage());
        //    //res = "ERROR_THR";
        //}
        return res;
    }
    
    public static InetAddress getLocalAddress() throws SocketException {
    Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
    while (ifaces.hasMoreElements()) {
        NetworkInterface iface = ifaces.nextElement();
        Enumeration<InetAddress> addresses = iface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                return addr;
            }
        }
    }
    return null;
}


    private boolean checkifshow(String _filename, String _filetype) {

            //p("checkifshow = '" + _filetype + "' filename '" + _filename + "'");
            boolean bShow = false;            
            int npos = _filename.toLowerCase().indexOf(_filetype.toLowerCase());
            //p("sFileNamepos = '" + npos + "'");
            if (npos > 1) bShow = true;

            if (_filetype.equals(".mov") && is_movie(_filename)) bShow = true;      
            if (_filetype.equals(".mp3") && is_music(_filename)) bShow = true;
            if (_filetype.equals(".jpg") && is_photo(_filename)) bShow = true;
            if (_filetype.equals(".jpeg") && is_photo(_filename)) bShow = true;
            if (_filetype.equals(".png") && is_photo(_filename)) bShow = true;
            if (_filetype.equals(".gif") && is_photo(_filename)) bShow = true;
            if (_filetype.equals(".music") && is_music(_filename)) bShow = true;
            if (_filetype.equals(".photo") && is_photo(_filename)) bShow = true;
            if (_filetype.equals(".video") && is_movie(_filename)) bShow = true;
            if (_filetype.equals(".document") && is_document(_filename)) bShow = true;

            //p("bShow = " + bShow);
//            try {
//                Thread.sleep(100);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
            return bShow;
    }

    public static boolean is_textfile(String _string) {
        if (
                _string.toLowerCase().contains(".txt") ||
                _string.toLowerCase().contains(".java") ||
                _string.toLowerCase().contains(".htm")
            )
                return true;
    else
        return false;
    }
    
    
    public static boolean is_document(String _string) {
        if (
                _string.toLowerCase().contains(".doc") ||
                _string.toLowerCase().contains(".ppt") ||
                _string.toLowerCase().contains(".xls") ||
                _string.toLowerCase().contains(".pdf")
            )
                return true;
    else
        return false;
    }
    
    public static boolean is_pdf(String _string) {
        if (
                _string.toLowerCase().contains(".pdf")
            )
                return true;
    else
        return false;
    }

    public static boolean is_video(String _string) {
        if (
                _string.toLowerCase().contains(".mov") ||
                _string.toLowerCase().contains(".mp4")
            )
                return true;
    else
        return false;
    }

    
    public static boolean is_photo(String _string) {
        if (
                _string.toLowerCase().contains(".jpg") ||
                _string.toLowerCase().contains(".png") ||
                _string.toLowerCase().contains(".gif") ||
                _string.toLowerCase().contains(".jpeg")
            )
                return true;
    else
        return false;
    }
    
    public boolean is_office(String _string) {
        if (
                _string.toLowerCase().contains(".doc") ||
                _string.toLowerCase().contains(".docx") ||
                _string.toLowerCase().contains(".docm") ||
                  _string.toLowerCase().contains(".dotx") ||
                  _string.toLowerCase().contains(".dotm") ||
                  _string.toLowerCase().contains(".xlsx") ||
                  _string.toLowerCase().contains(".xls") ||  
                 _string.toLowerCase().contains(".xlsm") ||
                  _string.toLowerCase().contains(".xltx") ||
                  _string.toLowerCase().contains(".xltm") ||
                  _string.toLowerCase().contains(".xlsb") ||
                  _string.toLowerCase().contains(".xlam") ||
                  _string.toLowerCase().contains(".pptx") ||
                  _string.toLowerCase().contains(".ppt") ||
                _string.toLowerCase().contains(".pptm") ||
                _string.toLowerCase().contains(".potx") ||
                _string.toLowerCase().contains(".potm") ||
                _string.toLowerCase().contains(".ppam") ||
                _string.toLowerCase().contains(".ppsx") ||
                _string.toLowerCase().contains(".ppsm") ||
                _string.toLowerCase().contains(".sldx") ||
                _string.toLowerCase().contains(".sldm") ||
                _string.toLowerCase().contains(".thmx") 
            )
                return true;
    else
        return false;
    }
    
    private boolean is_musicToPlay(String _string) {
        //p("music test:" + _string);
        if (
                _string.toLowerCase().contains(".mp3") ||
                _string.toLowerCase().contains(".m4a"))
                return true;
    else
        return false;
    }
    
    public static boolean is_music(String _string) {
        //p("music test:" + _string);
        if (
                _string.toLowerCase().contains(".wma") ||
                _string.toLowerCase().contains(".mp3") ||
                _string.toLowerCase().contains(".m4a") ||
                _string.toLowerCase().contains(".wav") ||
                _string.toLowerCase().contains(".m4p")
            )
                return true;
    else
        return false;
    }
    public static boolean is_movie(String _string) {
        if (
                _string.toLowerCase().contains(".avi") ||
                _string.toLowerCase().contains(".mov") ||
                _string.toLowerCase().contains(".mts") ||
                _string.toLowerCase().contains(".m4v") ||
                _string.toLowerCase().contains(".wmv") ||
                _string.toLowerCase().contains(".mp4") ||
                _string.toLowerCase().contains(".ogv") ||
                _string.toLowerCase().contains(".webm") ||
                _string.toLowerCase().contains(".mpg") ||
                _string.toLowerCase().contains(".m2ts")
            )
                return true;
    else
        return false;
    }
                            
    public Integer getHashCount(String _key) throws UnsupportedEncodingException
    {
        // read entire row
        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();
        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        ColumnParent parent = new ColumnParent(columnFamily);
        
        try {
            List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(_key.getBytes()), parent, predicate, ConsistencyLevel.ONE);
            
            return results.size();
            
        } catch (InvalidRequestException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnavailableException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimedOutException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return -1;
    }
    
    
    
    public String read_row_hash_cass(String _key, HashMap numbers, HashMap names)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {

        // read entire row
        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();
        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        ColumnParent parent = new ColumnParent(columnFamily);

        byte[] kk = _key.getBytes();

        String res = "";
        try {
            
            List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
            
            for (ColumnOrSuperColumn result : results) {
                Column column = result.column;
                String name2 = URLDecoder.decode(new String(column.getName(), UTF8), UTF8);
                String val2 = URLDecoder.decode(new String(column.getValue(), UTF8), UTF8);
                
                p(_key + "->" + name2 + " -> " + val2);
                if (name2.equals("ext") && isExtensionOk("." + val2))
                {
                    int was = 0;
                    Object Got;
                    if ((Got = numbers.get(_key)) != null) {
                        was = (((Integer) Got).intValue());
                    }
                    //numbers.put(_key, new Integer(was + 1));
                    numbers.put(_key, Integer.valueOf(was+1));
                    //p("added" + _key + "->" + name2 + " -> " + val2);

                } else {
                    //p("skipped" + _key + "->" + name2 + " -> " + val2);
                }
                if (name2.equals("name")) {
                    //store name in the hashmap
                    names.put(_key, val2);
                }
            }
        } catch (Exception TimedOutException) {
            p("hit timeout exception!!!!");
        }       

        return res;
    }

public void set_deadnodes() {

        if (dbmode.equals("cass") || dbmode.equals("both")) { 
            set_deadnodes_cass();
        } else {
            int res = lf.set_deadnodes("NodeInfo", "nodes");
        }
                
}

public void set_deadnodes_cass() {
        try {
            mCassandraClient.set_keyspace(keyspace);

            SlicePredicate predicate = new SlicePredicate();
            SliceRange sliceRange = new SliceRange();
            sliceRange.setStart(new byte[0]);
            sliceRange.setFinish(new byte[0]);
            predicate.setSlice_range(sliceRange);

            ColumnParent parent = new ColumnParent("NodeInfo");
            byte[] kk = "nodes".getBytes();
            List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);

            for (ColumnOrSuperColumn result : results) {
                Column column = result.column;
                String node_name = new String(column.getName(), UTF8);                
                String node_lastping = get_row_attribute("Keyspace1b","NodeInfo",node_name,"lastping");
                
                if (node_lastping.length() > 0) {
                    long timestamp = System.currentTimeMillis();
                    long timestampDiff = timestamp - Long.parseLong(node_lastping);
                    int diffDays = (int)(timestampDiff / (24 * 60 * 60 * 1000));

                    //if the last ping was more than 30 days ago, set as dead node
                    if(diffDays > 30){
                        deadnodes_uuid.put(node_name, true);
                    }                    
                }
            }
        } catch (NotFoundException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (OutOfMemoryError ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRequestException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnavailableException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimedOutException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
}

public boolean  get_deadnode(String sUUID) {
        Object Got;

        if ((Got = deadnodes_uuid.get(sUUID)) != null) {
            return true;
        } else {
            return false;
        }

}

            



public String set_backupnodes(String _keyspace, String _columnfamily, String _key, HashMap _occurences_uuid_files, String _columnname)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {

        //p("keyspace = " + _keyspace);
        //p("columnfamily = " + _columnfamily);
        //p("key = " + _key);

        mCassandraClient.set_keyspace(_keyspace);

        // read entire row
        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();
        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        //p("\nrow:");
        ColumnParent parent = new ColumnParent(_columnfamily);
        byte[] kk = _key.getBytes();
        List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
        String res = "";
        for (ColumnOrSuperColumn result : results) {
            Column column = result.column;
            String name2 = URLDecoder.decode(new String(column.getName(), UTF8), UTF8);
            String val2 = URLDecoder.decode(new String(column.getValue(), UTF8), UTF8);
            //p(name2 + " -> " + val2);

            String sValue = "";
            sValue = get_row_attribute(keyspace,"NodeInfo",name2,_columnname);
            if (sValue.equals("yes")) {
                //p(name2 + " is a backup node.");
                _occurences_uuid_files.put(name2, 0);
            //occurences_uuid_files.put("98bf3e62-e23d-4754-af80-6d09a79e8817", 0);

            }

            //if (name2.equals(_name)) {
                //p("found name" + _name );
                //p("value = " + val2 );
                //res = val2;
            //}
        }
        return res;
    }

   public String get_row_attributes(String _keyspace, String _columnfamily, String _key, String _name, String _name2) {
        if (dbmode.equals("p2p")) { 
            return lf.get_row_attributes(_keyspace, _columnfamily, _key, _name, _name2);
        } else {
            return "";
        }
}   
        
    public String get_row_attribute(String _keyspace, String _columnfamily, String _key, String _name) 
            throws NotFoundException, 
            UnavailableException,
            TimedOutException,
            UnsupportedEncodingException,
            InvalidRequestException,
            TException {
        
        if (dbmode.equals("p2p")) {
            return lf.get_row_attribute(_keyspace, _columnfamily, _key, _name, null);                    
        } 
        if (dbmode.equals("cass") || dbmode.equals("both")) {           
            return get_row_attribute_cass(_keyspace, _columnfamily, _key, _name);                    
        }        
        return "ERROR";
    }

    public String get_row_attribute_cass(String _keyspace, String _columnfamily, String _key, String _name)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException,
            OutOfMemoryError {

            String res = "";
            
            try {

                mCassandraClient.set_keyspace(_keyspace);

                // read entire row
                SlicePredicate predicate = new SlicePredicate();
                SliceRange sliceRange = new SliceRange();
                sliceRange.setStart(new byte[0]);
                sliceRange.setFinish(new byte[0]);
                predicate.setSlice_range(sliceRange);

                //p("\nrow:");
                ColumnParent parent = new ColumnParent(_columnfamily);
                byte[] kk = _key.getBytes();
                List<ColumnOrSuperColumn> results = null;
                results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
                for (ColumnOrSuperColumn result : results) {
                    Column column = result.column;
                    String name2 = URLDecoder.decode(new String(column.getName(), UTF8), UTF8);
                    String val2 = URLDecoder.decode(new String(column.getValue(), UTF8), UTF8);
                
                    //p(name2 + " -> " + val2);
                    if (name2.equals(_name)) {
                        //p("found name" + _name );
                        //p("value = " + val2 );
                        res = val2;
                    }
                }
                //p("res: '" + res + "'");
                return res;
          } catch (IOException OutOfMemoryError) {
                p("***OutOfMemory get_row_attribute");
                res = "ERROR4";
          } catch (Exception TApplicationException) {
                p("***TApplicationException get_row_attribute");
                res = "ERROR3";
          }
          return res;

    }


    public String read_row(String _key)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {

        mCassandraClient.set_keyspace(keyspace);

        // read entire row
        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();
        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        //p("\nrow:");
        ColumnParent parent = new ColumnParent(columnFamily);
        byte[] kk = _key.getBytes();
        List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
        String res = "";
        for (ColumnOrSuperColumn result : results) {
            Column column = result.column;
            String name2 = URLDecoder.decode(new String(column.getName(), UTF8), UTF8);
            String val2 = URLDecoder.decode(new String(column.getValue(), UTF8), UTF8);
                
            //p(name2 + " -> " + val2);
            if (name2.equals("ext") && val2.equals("jpg")) {
                p("\nrow:" + nCount);
                sNames[nCount] = _key;
                nCount++;
            }
        }

        return res;
    }
    
    public ArrayList<Node> getNodes(String _key, String _columnFamily) {
        return new ArrayList<Node>();   
    }
    
    //This routine obtains the information for all nodes in the system.
        
    public String get_node_info(String _key, String _columnFamily)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {
        
        if (_key.equals("nodes")) {
            
            mCassandraClient.set_keyspace(keyspace);

            // read entire row
            SlicePredicate predicate = new SlicePredicate();
            SliceRange sliceRange = new SliceRange();
            sliceRange.setStart(new byte[0]);
            sliceRange.setFinish(new byte[0]);
            predicate.setSlice_range(sliceRange);

            //p("\nrow:");
            ColumnParent parent = new ColumnParent(_columnFamily);
            byte[] kk = _key.getBytes();
            List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
            String res = "";
            
            for (ColumnOrSuperColumn result : results) {
                Column column = result.column;
                String node_name = new String(column.getName(), UTF8);                
                String node_ip = get_row_attribute("Keyspace1b","NodeInfo",node_name, "ipaddress");
                String node_port = get_row_attribute("Keyspace1b","NodeInfo",node_name, "port");
                String node_backup = get_row_attribute("Keyspace1b","NodeInfo",node_name, "backup");
                String node_free = get_row_attribute("Keyspace1b","NodeInfo",node_name, "free");
                
                String node_sync = get_row_attribute("Keyspace1b","NodeInfo",node_name, "sync");
                String node_lastbat = get_row_attribute("Keyspace1b","NodeInfo",node_name, "lastbat");
                String node_lastseq = get_row_attribute("Keyspace1b","NodeInfo",node_name, "lastseq");
                
                String node_lastping = get_row_attribute("Keyspace1b","NodeInfo",node_name, "lastping");
                
                String sLine =  node_name + "," + 
                                node_ip + "," + 
                                node_port + "," + 
                                node_backup + "," + 
                                node_free + "," +
                                node_sync + "," +
                                node_lastbat + "," +
                                node_lastseq + "," +
                                node_lastping + "," +
                                "\n";
                res += sLine;
            }
            return res; 
        } else {
            return read_row_info(_key, _columnFamily);
        }
    }
    

    public String read_row_info(String _key, String _columnFamily)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {

        mCassandraClient.set_keyspace(keyspace);

        // read entire row
        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();
        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        //p("\nrow:");
        ColumnParent parent = new ColumnParent(_columnFamily);
        byte[] kk = _key.getBytes();
        List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
        String res = "";
        String imgres = "";
        res += "<table>\n";
        for (ColumnOrSuperColumn result : results) {
            Column column = result.column;
            String name2 = new String(column.getName(), UTF8);
            String val2 = new String(column.getValue(), UTF8);
            //p(name2 + " -> " + val2);
            res += "<tr>";
            res += "<td>" + name2 + "</td><td>" + val2 + "</td>";

            //jpg file handler
            if (name2.equals("ext") && val2.equals("jpg")) {
                p("\n found jpeg");
                //imgres += "<td><a href ='pic/" + _key + "'><img border='0' href='pic/" + _key + "' src='pic/" + _key + "' height ='300' width='300'></a></td>";
                imgres += "<td><a href ='pic/" + _key + ".jpg" + "'><img border='0' src='pic/" + _key + ".jpg" + "'></a></td>";
            }

            //mp3 file handler

            if (name2.equals("ext") && (val2.equals("mp3") || val2.equals("m4a"))) {
                p("\n found mp3");
                //image for albumart goes here
                imgres += "<td><a href ='pic/" + _key + ".jpg" + "'><img border='0' src='pic/" + _key + ".jpg" + "'></a></td>";
                //imgres += "<td><audio id=\"audio\" src=\"snd/" + _key + ".mp3\" controls></audio>";
            }


            res += "</tr>\n";
        }
        res += "</table>\n";
        res += imgres;
        //p("\nRES:" + res);
        return res;
    }

//    public static String insert_hashtag(String _keyspace, String _key, String _hashkey, String _scolumn)
//            throws UnsupportedEncodingException,
//            InvalidRequestException, UnavailableException, TimedOutException,
//            TException, NotFoundException {
//
//        mCassandraClient.set_keyspace(_keyspace);
//
//        // p("insert_hashtag: '" + _hashkey + "'into key <'" + _key + "'>");
//        // insert data
//
//        //long timestamp = System.currentTimeMillis();
//        //ColumnPath colPathName = new ColumnPath(columnFamily);
//        //colPath.setColumn(name.getBytes(UTF8));
//        //client.insert(keyspace, _key, colPath, _value.getBytes(UTF8), timestamp, ConsistencyLevel.ONE);
//
//        insertSuperColumn(_key, _scolumn, _hashkey, _hashkey);
//
//        String res = "INSERT HASH OK";
//        return res;
//    }

    
    public String delete_column_auto_complete(String _keyspace, String _key, String sname) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {
       
        mCassandraClient.set_keyspace(_keyspace);

        ColumnPath colPath = new ColumnPath(columnFamilyAC);
        colPath.setColumn(URLEncoder.encode(sname, "UTF8").getBytes());

        mCassandraClient.remove(ByteBuffer.wrap(_key.getBytes()), colPath, System.currentTimeMillis(), ConsistencyLevel.ONE);
        
        String res = "DELETE COLUMN OK";
        return res;
    }
    
    public String insert_column_auto_complete(String _keyspace,String _key, String sname, String _value) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {
       
        mCassandraClient.set_keyspace(_keyspace);
        ColumnParent cp = new ColumnParent(columnFamilyAC);
        //cp.setColumn(name.getBytes(UTF8));
        byte[] kk = _key.getBytes();
        
        //Column co = new Column(ByteBuffer.wrap(sname.getBytes()), ByteBuffer.wrap(_value.getBytes()), System.currentTimeMillis());
        Column co = new Column();
        co.setName(URLEncoder.encode(sname,"UTF-8").getBytes());
        co.setValue(URLEncoder.encode(_value,"UTF-8").getBytes());
        co.setTimestamp(System.currentTimeMillis());
        mCassandraClient.insert(ByteBuffer.wrap(kk), 
                cp, 
                co,
                ConsistencyLevel.ONE);

        String res = "INSERT COLUMN OK";
        return res;
    }
    
    public int delete_column(String _keyspace, String _column_family, String _key, String _column_name){
        try {
            mCassandraClient.set_keyspace(_keyspace);

            ColumnPath colPath = new ColumnPath(_column_family);
            colPath.setColumn(URLEncoder.encode(_column_name, "UTF8").getBytes());
            
            mCassandraClient.remove(ByteBuffer.wrap(_key.getBytes()), colPath, System.currentTimeMillis(), ConsistencyLevel.ONE);
            
            return 0;
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRequestException ex) {
            p("InvalidRequestException: " + ex.getMessage());
        } catch (UnavailableException ex) {
            p("UnavailableException: " + ex.getMessage());
        } catch (TimedOutException ex) {
            p("TimedOutException: " + ex.getMessage());
        }        
        return -1;
    }

    /**
     * Insert column
     * @param _keyspace Keyspace
     * @param _column_name
     * @param _name
     * @param _value
     * @return
     * @throws UnsupportedEncodingException
     * @throws InvalidRequestException
     * @throws UnavailableException
     * @throws TimedOutException
     * @throws TException
     * @throws NotFoundException
     */
    public int insert_column(String _keyspace, String _column_family, String _key, String _column_name, String _column_value){
        try {
            mCassandraClient.set_keyspace(_keyspace);
            String sPrint = "keyspace: '" + _keyspace + "' column_family '" + _column_family + "'";
            //p(sPrint);
            sPrint = "insert_column: '" + _key + "'<'" + _column_name + "','" + _column_value + "'>";
            //p(sPrint);
            //p(sPrint);
            // insert data
            ColumnParent column_parent = new ColumnParent(_column_family);
            //colPath.setColumn(name.getBytes(UTF8));
            //mClient.insert(keyspace, _key, colPath, _value.getBytes(UTF8), timestamp, ConsistencyLevel.ONE);
            byte[] column_name_bytes = _key.getBytes();

            Column co = new Column();
            co.setName(URLEncoder.encode(_column_name,"UTF-8").getBytes());
            co.setValue(URLEncoder.encode(_column_value,"UTF-8").getBytes());
            co.setTimestamp(System.currentTimeMillis());

            mCassandraClient.insert(ByteBuffer.wrap(column_name_bytes), 
                    column_parent, 
                    co, 
                    ConsistencyLevel.ONE);
            return 0;
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRequestException ex) {
            p("InvalidRequestException: " + ex.getMessage());
        } catch (UnavailableException ex) {
            p("UnavailableException: " + ex.getMessage());
        } catch (TimedOutException ex) {
            p("TimedOutException: " + ex.getMessage());
        }        
        return -1;

    }

    /**
     * Insert Cuper Column
     * @param _keyspace
     * @param _super_column_family
     * @param key
     * @param superColumnName
     * @param columnName
     * @param _value
     * @throws UnsupportedEncodingException
     * @throws InvalidRequestException
     * @throws UnavailableException
     * @throws TimedOutException
     * @throws TException
     * @throws NotFoundException
     */

public static boolean isUnix(String sUUID) {

            if (sUUID.equals("0ce78da1-fe24-4875-9ff0-476a6ef4883d")||sUUID.equals("41831773-9ef0-40c2-9631-6b5fe94565ee")) {
                //windows machines
                return false;
            } else {
                //all else mac or ubuntu
                return true;
            }
    }


    public static boolean isNodeAvailable(String _ipaddress, String _port) {
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
            return true;
        } catch (NoRouteToHostException ex) {
            p("Exception: No route to host...");            
        } catch (SocketTimeoutException ex) {
            p("Exception: isNodeAvailable timeout");
        } catch (Exception ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

public String getPort(String _uuid) {
        try {
            return get_row_attribute("Keyspace1b","NodeInfo",_uuid, "port");
        } catch (NotFoundException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
            return "NOT_FOUND";
        } catch (UnavailableException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimedOutException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRequestException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";

    }


    public String getIP(String _uuid) {
        try {
            return get_row_attribute("Keyspace1b","NodeInfo",_uuid, "ipaddress");
        } catch (NotFoundException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
            return "NOT_FOUND";
        } catch (UnavailableException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimedOutException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRequestException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";

    }

    public int deleteSuperColumn(String _keyspace, String _super_column_family, final String _key, final String _superColumnName, final String _columnName){
        try {
            mCassandraClient.set_keyspace(_keyspace);
            
            ColumnPath colPath = new ColumnPath(_super_column_family);
            colPath.setSuper_column(_superColumnName.getBytes(UTF8));
            colPath.setColumn(URLEncoder.encode(_columnName, "UTF-8").getBytes());
            
            mCassandraClient.remove(ByteBuffer.wrap(_key.getBytes()), colPath, System.currentTimeMillis(), ConsistencyLevel.ONE);
            
            return 0;
        } catch (UnavailableException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimedOutException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRequestException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return -1;
    }
    
    public int insertSuperColumn(String _keyspace, String _super_column_family, final String _key, final String _superColumnName, final String _columnName, final String _value){
        try {
            //String sColumnName = URLDecoder.decode(_columnName, "UTF-8").replace("\\", "/");
            String sColumnName = _columnName.replace("\\", "/");
            
            mCassandraClient.set_keyspace(_keyspace);
            ColumnParent column_parent = new ColumnParent(_super_column_family);
            column_parent.setSuper_column(_superColumnName.getBytes(UTF8));

            Column co = new Column();
            co.setName(URLEncoder.encode(sColumnName,"UTF-8").getBytes());
            co.setValue(URLEncoder.encode(_value,"UTF-8").getBytes());
            co.setTimestamp(System.currentTimeMillis());

            //Column co = new Column(ByteBuffer.wrap(columnName.getBytes()), ByteBuffer.wrap(_value.getBytes()), System.currentTimeMillis());
            mCassandraClient.insert(ByteBuffer.wrap(_key.getBytes()), 
                    column_parent, 
                    co, 
                    ConsistencyLevel.ONE);
            return 0;
        } catch (UnavailableException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimedOutException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRequestException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return -1;
    }

    public String getSuperColumn2(final String _key, 
            final String superColumnName, 
            int nMode
            ) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {

        mCassandraClient.set_keyspace(keyspace);

        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();

        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        //p("keyspace:" + keyspace);
        //p("key:" + _key);
        //p("columnparent:" + superColumnFamily);
        //p("supercolumn:" + superColumnName);

        ColumnParent parent = new ColumnParent(superColumnFamily);
        //parent.setSuper_column(superColumnName.getBytes(UTF8));
        //p("[1]");
        byte[] kk = _key.getBytes();
        List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
        //p("[2]");

        String res = "";
        for (ColumnOrSuperColumn result : results) {
            SuperColumn sc = result.super_column;
            String scName = new String(sc.getName(), UTF8);
            //p("\nscName: " + scName);
            if (scName.equals(superColumnName)) {
                List<Column> colsInSc = sc.columns;
                for (Column c : colsInSc) {
                    String colName = URLDecoder.decode(new String(c.getName(), UTF8), UTF8);
                    String colValue = URLDecoder.decode(new String(c.getValue(), UTF8), UTF8);
                
                    //p("colName: " + colName);
                    switch (nMode) {
                        case 2:
                            int npos =  colName.indexOf(":");
                            //p("npos: " + npos);
                            int nlen = colName.length() - npos;
                            //p("length: " + nlen);
                            String sUUID = colName.substring(0,npos);
                            //p(sUUID);
                            String sAdd = "";
                            String sIP = "";
                            String sPort = "";
                            sIP = getIP(sUUID);
                            sPort = getPort(sUUID);
                            if (isUnix(sUUID)) {
                                sAdd = "/";
                            }
                            String sPathDec = sAdd + colName.substring(npos + 1, colName.length());
                            int lastdot = colName.lastIndexOf(".");
                            String sExt = colName.substring(lastdot, colName.length()-1).toLowerCase();
                            //p("sPathDec = '" + sPathDec + "'");
                            //else {
                            //    res += colName + "<sp><a href='http://" + sIP + ":8080/" + sPathEnc + sExt + "' target='_blank'>view</a><br>";
                            //}
                            String sPathEnc = Base64.encodeToString(sPathDec.getBytes(), false);
                            //p("sPathEnc = '" + sPathEnc + "'");
                            
                            //boolean bAvail = isNodeAvailable(sIP, sPort);
                            
                            String LocalIP = "";
                            try {
                                //InetAddress clientIP = InetAddress.getLocalHost();
                                InetAddress clientIP = getLocalAddress();
                                LocalIP = clientIP.getHostAddress();
                            } catch (Exception e) {
                                
                            }
                            
                            //p("LocalIP = '" + LocalIP + "'");
                            //p("sIP = '" + sIP + "'");

                            //if (LocalIP.equals(sIP) && sExt.contains(".jpg")) {
                            if (sExt.contains(".jpg")) {
                                if (LocalIP.equals(sIP)) {
                                    return sPathEnc;
                                //} else {
                                //    return "http://" + sIP + ":" + sPort + "/cass/" + sPathEnc;                                    
                                }
                            }
                            break;
                    }
                }
            }

        }
        //p("\nRES: '" + res + "'");
        return res;
    }

    public String getSuperColumn(final String _key, 
            final String superColumnName, 
            int nMode,
            String _password,
            String _clientip, 
            String _viewmode, 
            String _daysBack, 
            String _numCol, 
            String _numObj, 
            String _last,
            String _filetype) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {

        mCassandraClient.set_keyspace(keyspace);

        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();

        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        //p("\nkeyspace:" + keyspace);
        //p("\nkey:" + _key);
        //p("\ncolumnparent:" + superColumnFamily);
        //p("\nsupercolumn:" + superColumnName);

        ColumnParent parent = new ColumnParent(superColumnFamily);
        //parent.setSuper_column(superColumnName.getBytes(UTF8));
        //p("\n[1]");
        byte[] kk = _key.getBytes();
        List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
        //p("\n[2]");

        String res = "";
        for (ColumnOrSuperColumn result : results) {
            SuperColumn sc = result.super_column;
            String scName = new String(sc.getName(), UTF8);
            //p("\nscName: " + scName);
            if (scName.equals(superColumnName)) {
                List<Column> colsInSc = sc.columns;
                
                res += "<table css=css><tr>";
                        
                for (Column c : colsInSc) {
                    String colName = URLDecoder.decode(new String(c.getName(), UTF8), UTF8);
                    String colValue = URLDecoder.decode(new String(c.getValue(), UTF8), UTF8);
                    
                    //p("\ncolName: " + colName);
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
                            sIP = getIP(sUUID);
                            sPort = getPort(sUUID);
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
                                InetAddress clientIP = getLocalAddress();
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
                    }
                }
                
                res += "</tr></table> ";
            }

        }
        //p("\nRES:" + res);
        return res;
    }
    
    /* mapping of file extensions to content-types */
    static java.util.HashMap map = new java.util.HashMap();
    
    static void setSuffix(String k, String v) {
        map.put(k, v);
    }
    
    public int loadNodes (String _key, String _columnFamily)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {

        mCassandraClient.set_keyspace(keyspace);

        // read entire row
        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();
        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        //p("\nrow:");
        ColumnParent parent = new ColumnParent(_columnFamily);
        byte[] kk = _key.getBytes();
        List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
        String res = "";
        String imgres = "";
        res += "<table>\n";
        for (ColumnOrSuperColumn result : results) {
            Column column = result.column;
            String _uuid = new String(column.getName(), UTF8);
            String _uuidvalue = new String(column.getValue(), UTF8);
            String sIP = get_row_attribute("Keyspace1b","NodeInfo",_uuid, "ipaddress");
            String sPort = get_row_attribute("Keyspace1b","NodeInfo",_uuid, "port");
            String sIPPort = sIP + ":" + sPort;
            p("loading '" + _uuid + "' into map -> " + sIPPort);
            setSuffix(_uuid, sIPPort);
        }
        bNodesLoaded = true;
        return 0;
    }

//    public static String insert_hashtag(String _keyspace, String _key, String _hashkey, String _scolumn)
//            throws UnsupportedEncodingException,
//            InvalidRequestException, UnavailableException, TimedOutException,
//            TException, NotFoundException {
//
//        mCassandraClient.set_keyspace(_keyspace);
//
//        // p("insert_hashtag: '" + _hashkey + "'into key <'" + _key + "'>");
//        // insert data
//
//        //long timestamp = System.currentTimeMillis();
//        //ColumnPath colPathName = new ColumnPath(columnFamily);
//        //colPath.setColumn(name.getBytes(UTF8));
//        //client.insert(keyspace, _key, colPath, _value.getBytes(UTF8), timestamp, ConsistencyLevel.ONE);
//
//        insertSuperColumn(_key, _scolumn, _hashkey, _hashkey);
//
//        String res = "INSERT HASH OK";
//        return res;
//    }
    
    
    public sURLPack get_remote_link(final String _key, 
            final String superColumnName, boolean _skipIP, boolean _cloudhosted, String _clientIP) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {

        mCassandraClient.set_keyspace(keyspace);

        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();

        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        //p("\nkeyspace:" + keyspace);
        //p("\nkey:" + _key);
        //p("\ncolumnparent:" + superColumnFamily);
        //p("\nsupercolumn:" + superColumnName);

        ColumnParent parent = new ColumnParent(superColumnFamily);
        //parent.setSuper_column(superColumnName.getBytes(UTF8));
        //p("\n[1]");
        byte[] kk = _key.getBytes();
        List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
        //p("\n[2]");
        
        if (!bNodesLoaded) {
            int r = loadNodes("nodes", "NodeInfo");
        } else {
            p("*** Nodes already loaded. Skipping load.");
        }

        sURLPack res = new sURLPack(); 
        res.sOpenURL = "";
        res.sPlayURL = "";
        res.sViewURL = "";
        res.sFolderURL = "";
        
        boolean bFound = false;
        
        for (ColumnOrSuperColumn result : results) {
            SuperColumn sc = result.super_column;
            String scName = new String(sc.getName(), UTF8);
            //p("\nscName: " + scName);
            if (scName.equals(superColumnName)) {
                List<Column> colsInSc = sc.columns;
                for (Column c : colsInSc) {
                    String colName = URLDecoder.decode(new String(c.getName(), UTF8), UTF8);
                    String colValue = URLDecoder.decode(new String(c.getValue(), UTF8), UTF8);
                    
                    
                    try {
                        //InetAddress clientIP = InetAddress.getLocalHost();
                        
                        //get server IP address
                        InetAddress clientIP = getLocalAddress();  
                        String LocalIP = "127.0.0.1";
                        if (clientIP != null) {
                            LocalIP = clientIP.getHostAddress();                            
                        }
                        
                        int npos =  colName.indexOf(":");
                        //p("npos: " + npos);
                        int nlen = colName.length() - npos;
                        //p("length: " + nlen);
                        String sUUID = colName.substring(0,npos);
                        //p(sUUID);
                        String sAdd = "";
                        String sIP = "";
                        String sPort = "";
                        
                        //sIP = getIP(sUUID);
                        //sPort = getPort(sUUID);
                        String sIPPort = (String) map.get(sUUID);
                        
                        p("found " + sUUID + "->" + sIPPort);
                        if (sIPPort != null) {
                            sIP = sIPPort.substring(0,sIPPort.indexOf(":"));
                            p("ip = " + sIP);

                            sPort = sIPPort.substring(sIPPort.indexOf(":")+1,sIPPort.length());
                            //p("port = " + sPort);

                        } else {
                            sIP = "0";
                        }

                        p("sIP = '" + sIP + "'");
                        p("LocalIP = '" + LocalIP + "'");
                        p("Skip IP = " + _skipIP);
                        
                        //if the client is local, make its IP address equal to server IP address
                        if (_clientIP.equals("0:0:0:0:0:0:0:1") || 
                                _clientIP.equals("0:0:0:0:0:0:0:1%0") ||
                                _clientIP.equals("127.0.0.1")) {
                            _clientIP = sIP;
                        }

                        p("Client IP = " + _clientIP);

                        if ( !sIP.equals("0") && _clientIP.equals(sIP)) {
                            p("Found OpenURL for IP = '" + sIP + "'");
                            
                            String sUUIDOrig = get_row_attribute(keyspace, "Standard1", _key, "uuid_ori");
                            if (sUUIDOrig.equals("")) {
                                sUUIDOrig = sUUID;
                            }
                            
                            p("sUUIDOrig= '" + sUUIDOrig + "' vs " + sUUID);
                            
                            if (sUUID.equals(sUUIDOrig)) {
                                p("UUID match. Found original");
                                String sPathDec = sAdd + colName.substring(npos + 1, colName.length());
                                String _uri = URLEncoder.encode(sPathDec, "UTF-8"); 
                                res.sOpenURL =  ":" + sPort + "/cass/redir.php?foo=" + _uri + "&rem=0'>play" + "</a>";
                            }
                        }
                        
                        if (!sIP.equals("0") && (LocalIP.equals(sIP) || _skipIP)) {
                            
                            Boolean bAvail = false;
                            if (_skipIP) {
                                //if any node is accepted, need to check the node is up.
                                bAvail = isNodeAvailable(sIP, sPort);
                            } else {
                                //this is server case, so assume available.
                                bAvail = true;
                            }
                            
                            //check that is not a DELETED object
                            if ("DELETED".equals(colValue)) {
                                bAvail = false;
                                res.sOpenURL = "";
                            }
                            
                            if ((bAvail || _cloudhosted) && !bFound) {           
                                if (isUnix(sUUID)) {
                                    sAdd = "/";
                                }   

                                p("[1]");
                                String sPathDec = sAdd + colName.substring(npos + 1, colName.length());
                                p("spathdec = " + sPathDec);
                                int lastdot = colName.lastIndexOf(".");
                                p("lastdot = " + lastdot);
                                String sExt = colName.substring(lastdot, colName.length()-1).toLowerCase();
                                //p("sPathDec = '" + sPathDec + "'");
                                //else {
                                //    res += colName + "<sp><a href='http://" + sIP + ":8080/" + sPathEnc + sExt + "' target='_blank'>view</a><br>";
                                //}
                                String sPathEnc = Base64.encodeToString(sPathDec.getBytes(), false);
                                //p("sPathEnd = '" + sPathEnc + "'");
                                p("sExt = " + sExt);
                                p("[2]");
                                //boolean bShowplay = true;
                                res.sViewURL =  ":" + sPort + "/" + sPathEnc + sExt + "'>view</a>";
                                res.sSendURL =  ":" + sPort + "/" + "sendform.htm?file=" + sPathEnc + "'>send</a>";
                                p("[3]");                            
                                String _uri = URLEncoder.encode(sPathDec, "UTF-8"); 
                                p("[4]");                            
                                //p("_uri = '" + _uri + "' ");
                                
                                if (LocalIP.equals(sIP)) {
                                    res.sPlayURL =  ":" + sPort + "/cass/redir.php?foo=" + _uri + "&rem=1'>play" + "</a>";                                    
                                }
                                res.sHostIP = sIP;
                                res.sHostPort = sPort;
                                bFound = true;
                                //return res;                                  
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        res.sPlayURL = "ERROR";
                        res.sViewURL = "ERROR";
                        res.sOpenURL = "ERROR";
                        return res;
                    }
                 
                     if(bFound) return res;
                }
            }
        }
        //p("\nRES:" + res);
        
        //p("sOpenURL -> '" + res.sOpenURL);
        //p("sPlayURL -> '" + res.sPlayURL);
        //p("sViewURL -> '" + res.sViewURL);
        //p("sHostIP -> '" + res.sHostIP);


        return res;
    }
    
public sURLPack get_remote_link2 (final String _key, 
            final String superColumnName, 
            boolean _skipIP, 
            boolean _cloudhosted, 
            String _clientIP, 
            boolean _matchuuid,
            boolean _checkavail) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {

    //p("dbmode = " + dbmode);
    if (dbmode.equals("cass")) {
        p("cass mode");
        return get_remote_link2_cass(_key, superColumnName, _skipIP, _cloudhosted, _clientIP);
    } else {
        //p("p2p mode");
        sURLPack up = new sURLPack();
        up = lf.get_remote_link2(_key, superColumnName, _skipIP, _cloudhosted, _clientIP, _matchuuid, _checkavail, "");
        //p("view link = " + up.sViewURL);
        try {
            //Thread.sleep(10000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return up;
        
    }
}

public sURLPack get_remote_link2_cass(final String _key, 
            final String superColumnName, boolean _skipIP, boolean _cloudhosted, String _clientIP) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {

    
        p("**** BEGIN get_remote_link2 ****");
        mCassandraClient.set_keyspace(keyspace);

        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();

        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        p("_key:" + _key);
        p("_supercolumnname:" + superColumnName);
        p("_skipIP:" + _skipIP);
        p("_cloudhosted:" + _cloudhosted);
        p("_clientIP:" + _clientIP);

        ColumnParent parent = new ColumnParent(superColumnFamily);
        //parent.setSuper_column(superColumnName.getBytes(UTF8));
        //p("\n[1]");
        byte[] kk = _key.getBytes();
        List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
        //p("\n[2]");
        
        if (!bNodesLoaded) {
            int r = loadNodes("nodes", "NodeInfo");
        } else {
            p("*** Nodes already loaded. Skipping load.");
        }

        sURLPack res = new sURLPack(); 
        res.sOpenURL = "";
        res.sPlayURL = "";
        res.sViewURL = "";
        res.sStatus = "";
        
        boolean bFound = false;
        
        for (ColumnOrSuperColumn result : results) {
            SuperColumn sc = result.super_column;
            String scName = new String(sc.getName(), UTF8);
            //p("\nscName: " + scName);
            if (scName.equals(superColumnName)) {
                List<Column> colsInSc = sc.columns;
                for (Column c : colsInSc) {
                    String colName = URLDecoder.decode(new String(c.getName(), UTF8), UTF8);
                    String colValue = URLDecoder.decode(new String(c.getValue(), UTF8), UTF8);

                    
                    p("\ncolName: " + colName);
                    p("\ncolValue: " + colValue);
                    
                    try {
                        //InetAddress clientIP = InetAddress.getLocalHost();
                        String sPortServer= getConfig("port",  "../rtserver/config/www-server.properties");
                        //get server IP address
                        InetAddress clientIP = NetUtils.getLocalAddressNonLoopback(sPortServer);  
                        String LocalIP = "127.0.0.1";
                        if (clientIP != null) {
                            LocalIP = clientIP.getHostAddress();                            
                        }
                        
                        int npos =  colName.indexOf(":");
                        //p("npos: " + npos);
                        int nlen = colName.length() - npos;
                        //p("length: " + nlen);
                        String sUUID = colName.substring(0,npos);
                        //p(sUUID);
                        String sAdd = "";
                        String sIP = "";
                        String sPort = "";
                        
                        //sIP = getIP(sUUID);
                        //sPort = getPort(sUUID);
                        String sIPPort = (String) map.get(sUUID);
                        
                        p("found " + sUUID + "->" + sIPPort);
                        if (sIPPort != null) {
                            sIP = sIPPort.substring(0,sIPPort.indexOf(":"));
                            p("ip = " + sIP);

                            sPort = sIPPort.substring(sIPPort.indexOf(":")+1,sIPPort.length());
                            //p("port = " + sPort);

                        } else {
                            sIP = "0";
                        }

                        
                        //if the client is local, make its IP address equal to server IP address
                        if (_clientIP.equals("0:0:0:0:0:0:0:1") || 
                                _clientIP.equals("0:0:0:0:0:0:0:1%0") ||
                                _clientIP.equals("127.0.0.1")) {
                            _clientIP = LocalIP;
                        }

                        p("file sIP = '" + sIP + "'");   //file IP address
                        p("Server IP = '" + LocalIP + "'");  //web server IP address
                        p("Client IP = " + _clientIP); //client IP address
                        p("Skip IP = " + _skipIP);


                        if ( !sIP.equals("0") && _clientIP.equals(sIP)) {
                            p("Found OpenURL for local IP = '" + sIP + "'");                                                        
                            if (res.sOpenURL.length() > 0) {
                                String sUUIDOrig = get_row_attribute(keyspace, "Standard1", _key, "uuid_ori");
                                if (sUUIDOrig.equals("")) {
                                    sUUIDOrig = sUUID;
                                }
                            
                                p("sUUIDOrig= '" + sUUIDOrig + "' vs " + sUUID);

                                if (sUUID.equals(sUUIDOrig)) {
                                    //if there are multiple local files, use the original
                                    p("UUID match. Found original");
                                    String sPathDec = sAdd + colName.substring(npos + 1, colName.length());
                                    String _uri = URLEncoder.encode(sPathDec, "UTF-8"); 
                                    res.sOpenURL =  ":" + sPort + "/cass/redir.php?foo=" + _uri + "&rem=0";
                                }                                
                            } else {
                                //if openURL is not set, then load it.
                                String sPathDec = sAdd + colName.substring(npos + 1, colName.length());
                                String _uri = URLEncoder.encode(sPathDec, "UTF-8"); 
                                res.sOpenURL =  ":" + sPort + "/cass/redir.php?foo=" + _uri + "&rem=0";
                            }
                        }
                        
                        p("OPEN URL ----> " + res.sOpenURL);
                        
                        if (!sIP.equals("0") && (LocalIP.equals(sIP) || _skipIP)) {
                            
                            Boolean bAvail = false;
                            if (_skipIP) {
                                //if any node is accepted, need to check the node is up.
                                if (!_cloudhosted) {
                                    bAvail = isNodeAvailable(sIP, sPort);
                                } else {
                                    bAvail = true;
                                }
                                
                                p("bAvail = " + bAvail);
                                
                                if(bAvail)  {//Este IF controla que el archivo realmente exista
                                            //Se le envia al nodo la solicitud del archivo 
                                            //Y este verifica si existe en el filesystem
                                            //Retorna un file con un string X,Y donde X indica si existe o no e Y su 
                                            //tamanio
                                    String sPathDec = sAdd + colName.substring(npos + 1, colName.lastIndexOf("."));
                                    p("spathdec = " + sPathDec);
                                    int lastdot = colName.lastIndexOf(".");
                                    p("lastdot = " + lastdot);
                                    String sExt = colName.substring(lastdot, colName.length()-1);
                                    String sPathEnc = URLEncoder.encode(sPathDec,  "UTF-8");
                                    String sFullPath ="http://"+sIP+ ":" + sPort + "/fileexist.fn?sfileexist=" + sPathEnc + sExt ;

                                    if (!_cloudhosted) {
                                        int resultGetFileExist=getfile(sFullPath, "fileexist.fnres", 1, 10,2000);                                
                                        bAvail = resultGetFileExist==1;                                                                                
                                        if(bAvail)  {                                        
                                            //Si logr� descargar el archivo que indica si existe o no lo reviso

                                            FileReader reader=new FileReader(new File("fileexist.fnres"));
                                            char[ ] cbuf =new char[150];
                                            int i=reader.read(cbuf);
                                            reader.close();
                                            File fAux2=new File("fileexist.fnres");
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
                                                bAvail = true;
                                            } 
                                            if (resFileExist!=null && resFileExist.equalsIgnoreCase("n")) {
                                                p("-----file not found");
                                                res.sStatus = "FILENOTFOUND";
                                                bAvail = false;
                                            }                                         
                                        }
                                    } else {
                                        p("---bavail cloud");
                                        bAvail = true;
                                    }                                                                     
                                } else {
                                    //case where node is not available
                                    res.sStatus = "TIMEOUT";
                                }
                            } else {
                                //this is server case, so assume available.
                                bAvail = true;
                            }
                            
                            //check that is not a DELETED object
                            if ("DELETED".equals(colValue)) {
                                bAvail = false;
                                p("OPEN URL[2] ----> " + res.sOpenURL);
                                //res.sOpenURL = "";
                                //res.sStatus = "DELETED";
                            }
                            
                            p("bavail = " + bAvail);
                            
                            if ((bAvail || _cloudhosted) && !bFound) {           
                                if (isUnix(sUUID)) {
                                    sAdd = "/";
                                }   

                                p("[1]");
                                String sPathDec = sAdd + colName.substring(npos + 1, colName.length()-1);
                                p("spathdec = " + sPathDec);
                                int lastdot = colName.lastIndexOf(".");
                                p("lastdot = " + lastdot);
                                String sExt = colName.substring(lastdot, colName.length()-1);
                                //p("sPathDec = '" + sPathDec + "'");
                                //else {
                                //    res += colName + "<sp><a href='http://" + sIP + ":8080/" + sPathEnc + sExt + "' target='_blank'>view</a><br>";
                                //}
                                String sPathEnc = Base64.encodeToString(sPathDec.getBytes(), false);
                                //p("sPathEnd = '" + sPathEnc + "'");
                                p("sExt = " + sExt);
                                p("[2]");
                                //boolean bShowplay = true;
                                res.sViewURL =  ""
                                        + ":" + sPort + "/" + sPathEnc + sExt ;
                                res.sSendURL =  ":" + sPort + "/" + "sendform.htm?file=" + sPathEnc;
                                p("[3]");                            
                                String _uri = URLEncoder.encode(sPathDec, "UTF-8"); 
                                p("[4]");                            
                                //p("_uri = '" + _uri + "' ");
                                
                                if (LocalIP.equals(sIP)) {
                                    res.sPlayURL =  ":" + sPort + "/cass/redir.php?foo=" + _uri + "&rem=1";                                    
                                }
                                res.sHostIP = sIP;
                                res.sHostPort = sPort;
                                bFound = true;
                                //return res;                                  
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        p("Exception get_remote_link2");
                        res.sPlayURL = "ERROR";
                        res.sViewURL = "ERROR";
                        res.sOpenURL = "ERROR";
                        return res;
                    }
                 
                    if(bFound) {
                        p("sOpenURL -> '" + res.sOpenURL + "'");
                        p("sPlayURL -> '" + res.sPlayURL + "'");
                        p("sViewURL -> '" + res.sViewURL + "'");
                        p("sHostIP -> '" + res.sHostIP + "'");

                        //return res;
                    }
                }
            }
        }
        //p("\nRES:" + res);
        
        p("sOpenURL -> '" + res.sOpenURL + "'");
        p("sPlayURL -> '" + res.sPlayURL + "'");
        p("sViewURL -> '" + res.sViewURL + "'");
        p("sHostIP -> '" + res.sHostIP + "'");

        p("this case...");
        
        
        p("**** END get_remote_link2 ****");
        
        return res;
    }    
    
public boolean deleteObject(final String _key, final String superColumnName, String sUUID, String sPath) {

        //String sPath2 = URLDecoder.decode(sPath, "UTF-8").replace("\\", "/");
        String sPath2 = sPath.replace("\\", "/");
        String sColumn = sUUID + ":" + sPath2 + "/";
        p("marking entry as deleted: key '" + _key + "' sColumn: '" + sColumn +"'");
        
        insert_column(keyspace,"Standard1", _key, "ViewLink3", ""); 
        insert_column(keyspace, "Standard1", _key, "PlayLink3", "");
        int ret_code = insertSuperColumn(keyspace, "Super2", _key, "paths", sColumn, "DELETED");
        if (ret_code == 0) {
            return true;
        } else {
            return false;
        }
    }

    
public boolean syncObject(final String _key, final String superColumnName, String sUUID, String sPath) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {

        mCassandraClient.set_keyspace(keyspace);
        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();
        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        predicate.setSlice_range(sliceRange);

        //p("\nkeyspace:" + keyspace);
        //p("\nkey: '" + _key + "'");
        //p("\ncolumnparent:" + superColumnFamily);
        //p("\nsupercolumn:" + superColumnName);
        //p("\nsPath: '" + sPath + "'");
        

        ColumnParent parent = new ColumnParent(superColumnFamily);
        //parent.setSuper_column(superColumnName.getBytes(UTF8));
        //p("\n[1]");
        byte[] kk = _key.getBytes();
        List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
        //p("\n[2]");

        String res = "";
        String sRow = "";
        

        for (ColumnOrSuperColumn result : results) {
            SuperColumn sc = result.super_column;
            String scName = new String(sc.getName(), UTF8);
            //p("scName: " + scName);
            if (scName.equals(superColumnName)) {
                List<Column> colsInSc = sc.columns;
                
                for (Column c : colsInSc) {
                    String colName = URLDecoder.decode(new String(c.getName(), UTF8), UTF8);
                    String colValue = URLDecoder.decode(new String(c.getValue(), UTF8), UTF8);
                    
                    //p("'" + colName + "' = '" + colValue + "'");
                    int npos =  colName.indexOf(":");
                    //p("npos: " + npos);
                    int nlen = colName.length() - npos;
                    //p("length: " + nlen);
                    String sUUIDc = colName.substring(0,npos);
                    if (sUUID.equals(sUUIDc)) {
                        String sPathc = colName.substring(npos + 1, colName.length()-1);
                        p("SPathc: '" + sPathc + "'");
                        String sPathc2 = "";
                        //p("SPathc2: '" + sPathc2 + "'"); 

                        sPathc2 = sPathc;
/*                        if (sPath.substring(0,1).equals("/")) {
                            //unix mode
                            p("\nUnix mode");
                            sPathc2 = sPathc;
                        } else {
                            //windows mode
                            p("\nWindows mode");
                            sPathc2 = sPathc.replace("/", "\\");    
                        }
*/                        
                        String s1 = URLDecoder.decode(sPathc2, "UTF-8").toLowerCase();
                        String s2 = URLDecoder.decode(sPath, "UTF-8").toLowerCase();                        

                        p("\nComparing: '" + s1 + "' vs '" + s2 + "'");
                        
                        if (s1.equals(s2)) {
                            return true;
                        } else {
                            p("\n---warning---------------------------------");
                            p("key = '" + _key + "' colName: '" + colName + "' = '" + colValue + "'");
                            p("\nPath mismatch, pathc: '" + s1 + "' vs '" + s2 + "'");
                            return false;
                        }    
                    }
                }
            }
        }
        //p("\nRES:" + res);
        return false;
    }

    public String getNumberofCopies(String _superColumnName, String _key, String _clientIP, String _serverIP) {
        if (dbmode.equals("cass")) {
            return getNumberofCopies_cass(_superColumnName, _key, _clientIP);
        } else {
            return lf.getNumberofCopies(_superColumnName, _key, _clientIP,_serverIP, false);
        }
    }
    
     

     public String getNumberofCopies_cass(String _superColumnName, String _key, String _clientIP) {
        
        try {
            p("----------------------------------------------------------------");
            int nCopies = 0;
            int nNodes = 0;
            int nCopiesLocal = 0;
            mCassandraClient.set_keyspace(keyspace);

            SlicePredicate predicate = new SlicePredicate();
            SliceRange sliceRange = new SliceRange();

            sliceRange.setStart(new byte[0]);
            sliceRange.setFinish(new byte[0]);
            predicate.setSlice_range(sliceRange);

            //p("\nkeyspace:" + keyspace);
            //p("\nkey:" + _key);
            //p("\ncolumnparent:" + superColumnFamily);
            //p("\nsupercolumn:" + _superColumnName);

            ColumnParent parent = new ColumnParent(superColumnFamily);

            byte[] kk = _key.getBytes();
            List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
            //p("\n[2]");

            String res = "";
            String sRow = "";

            for (ColumnOrSuperColumn result : results) {
                SuperColumn sc = result.super_column;
                String scName = new String(sc.getName(), UTF8);
                //p("scName: " + scName);
                if (scName.equals(_superColumnName)) {
                    List<Column> colsInSc = sc.columns;
                    HashMap<String, Integer> occurences_uuid = new HashMap<String, Integer>();

                    set_backupnodes(keyspace, "NodeInfo","nodes", occurences_uuid, "backup");

                    //occurences_uuid.put("0ce78da1-fe24-4875-9ff0-476a6ef4883d", 0);
                    //occurences_uuid.put("98bf3e62-e23d-4754-af80-6d09a79e8817", 0);

                    for (Column c : colsInSc) {
                            String colName = "";
                            try {
                                colName = URLDecoder.decode(new String(c.getName(), UTF8), UTF8);
                            } catch (Exception e) {
                                e.printStackTrace();
                                colName = new String(c.getName(), UTF8);
                            }

                            String colValue = "";
                            try {   
                                colValue = URLDecoder.decode(new String(c.getValue(), UTF8), UTF8);                                
                            } catch (Exception e) {
                                e.printStackTrace();
                                colValue = new String(c.getValue(), UTF8);
                            }
                    
                            p("colName: " + colName);
                            p("colValue: " + colValue);
                            int npos =  colName.indexOf(":");
                            //p("npos: " + npos);
                            //int nlen = colName.length() - npos;
                            //p("length: " + nlen);
                            String sUUID = "";
                            if (npos > 0) {
                                sUUID = colName.substring(0,npos);
                            } 
                            //p(sUUID);

                            int was = 0;
                            Object Got;
                            if ((Got = occurences_uuid.get(sUUID)) != null) {
                                was = (((Integer) Got).intValue());
                            }

                            if (!colValue.equals("DELETED")) {
                                boolean bNodeDead = get_deadnode(sUUID);
                                if (!bNodeDead) {
                                    //p("addding case for UUID = " + sUUID);
                                    occurences_uuid.put(sUUID, Integer.valueOf(was + 1));
                                } else {
                                    p("Skipped - Node is DEAD: " + sUUID + " " + _key + " : " + colName + " " + colValue);
                                }
                            } else {
                                p("Skipped - File Deleted: " + sUUID + " " + _key + " : " + colName);
                            }    
                        }
                    Iterator bit = occurences_uuid.keySet().iterator();
                    
                    
                    if (!bNodesLoaded) {
                        int r = loadNodes("nodes", "NodeInfo");
                    } else {
                        p("NODES ALREADY LOADED.");
                    }
                    
                    
                    //if the client is local, make its IP address equal to server IP address
                    String sLocalPort = getConfig("port", "config/www-server.properties");
                    InetAddress ServerIP = NetUtils.getLocalAddressNonLoopback(sLocalPort);
                    String sIP = ServerIP.getHostAddress();
                    p("Server IP address = " + sIP);
                    if (_clientIP.equals("0:0:0:0:0:0:0:1") || 
                        _clientIP.equals("0:0:0:0:0:0:0:1%0") ||
                        _clientIP.equals("127.0.0.1")) {
                        _clientIP = sIP;
                    }
                                        
                    while (bit.hasNext()) {
                        String sUUID = (String) bit.next();
                        p(sUUID + " " + (Integer)occurences_uuid.get(sUUID));
                        if ((Integer)occurences_uuid.get(sUUID) > 0) {
                            nNodes++;
                        }
                        nCopies += (Integer) occurences_uuid.get(sUUID);
                        
                        String sIPPort = (String) map.get(sUUID);                        
                        p("found " + sUUID + "->" + sIPPort);
                        
                        sIP = "";
                        if (sIPPort != null) {
                            sIP = sIPPort.substring(0,sIPPort.indexOf(":"));
                            p("ip = " + sIP);
                            p("client ip = " + _clientIP);
                                                        
                            if (sIP.equals(_clientIP)) {
                                nCopiesLocal += (Integer) occurences_uuid.get(sUUID);
                            }
                        }
                        

                    }                   
                }
            }        
            return nCopies + "," + nNodes + "," + nCopiesLocal;
            
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
     }




         
         
     
     public int backupObject(final String _key, 
             final String superColumnName, 
             long nBatch, 
             HashMap occurences_uuid_files, 
             String sRoot, 
             String sSeqID,
             Boolean bAppend,
             int _REPLICATION_FACTOR) throws UnsupportedEncodingException,
            InvalidRequestException, UnavailableException, TimedOutException,
            TException, NotFoundException {

         try {
                      
            mCassandraClient.set_keyspace(keyspace);

            SlicePredicate predicate = new SlicePredicate();
            SliceRange sliceRange = new SliceRange();

            sliceRange.setStart(new byte[0]);
            sliceRange.setFinish(new byte[0]);
            predicate.setSlice_range(sliceRange);

            //p("\nkeyspace:" + keyspace);
            //p("\nkey:" + _key);
            //p("\ncolumnparent:" + superColumnFamily);
            //p("\nsupercolumn:" + superColumnName);

            ColumnParent parent = new ColumnParent(superColumnFamily);
            //parent.setSuper_column(superColumnName.getBytes(UTF8));
            //p("\n[1]");
            byte[] kk = _key.getBytes();
            List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
            //p("\n[2]");

            String sRow = "";

            for (ColumnOrSuperColumn result : results) {
                SuperColumn sc = result.super_column;
                String scName = new String(sc.getName(), UTF8);
                //p("scName: " + scName);
                if (scName.equals(superColumnName)) {
                    List<Column> colsInSc = sc.columns;
                    HashMap<String, Integer> occurences_uuid = new HashMap<String, Integer>();
                    HashMap<String, Integer> occurences_uuid_sync = new HashMap<String, Integer>();

                    set_backupnodes(keyspace, "NodeInfo","nodes", occurences_uuid, "backup");
                    set_backupnodes(keyspace, "NodeInfo","nodes", occurences_uuid_sync, "sync");

                    //occurences_uuid.put("0ce78da1-fe24-4875-9ff0-476a6ef4883d", 0);
                    //occurences_uuid.put("98bf3e62-e23d-4754-af80-6d09a79e8817", 0);

                    for (Column c : colsInSc) {
                            String colName = URLDecoder.decode(new String(c.getName(), UTF8), UTF8);
                            String colValue = URLDecoder.decode(new String(c.getValue(), UTF8), UTF8);

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
                                    occurences_uuid.put(sUUID, Integer.valueOf(was + 1));
                                    String sAdd = "";
                                    String sIP = "";
                                    String sPort = "";
                                    sIP = getIP(sUUID);
                                    sPort = getPort(sUUID);
                                    //boolean bAvail = isNodeAvailable(sIP, sPort);
                                    //p("ip=" + sIP + " port=" + sPort + " avail=" + bAvail);

                                    if (isUnix(sUUID)) {
                                        sAdd = "/";
                                    }
                                    String sPathDec = sAdd + colName.substring(npos + 1, colName.length());
                                    int lastdot = colName.lastIndexOf(".");
                                    String sExt = colName.substring(lastdot, colName.length()-1);
                                    //p(sPathDec);
                                    String sPathEnc = Base64.encodeToString(sPathDec.getBytes(), false);

                                    String sField1 = _key;
                                    String sField2 = sExt;
                                    String sField3 = "http://" + sIP + ":" + sPort + "/" + sPathEnc + sExt;
                                    String sField4 = sUUID;

                                    sRow += sField1 + "," + sField2 + "," + sField3 + "," + sField4 + "\n";
                                    //p(sRow);    
                                } else {
                                    log("Skipped - Node is DEAD: " + sUUID + " " + _key + " : " + colName + " " + colValue);
                                }

                            } else {
                                log("Skipped - File Deleted: " + _key + " : " + colName + " " + colValue);
                            }    
                        }

                        Iterator bit = occurences_uuid.keySet().iterator();
                        int nReplicas = 0;  //number of nodes with at least one copy
                        while (bit.hasNext()) {
                            String sUUID = (String) bit.next();
                            Integer nCopies = (Integer) occurences_uuid.get(sUUID);

                            if (nCopies > 0) {
                                nReplicas++;
                            }
                        }

                        //number of nodes with at least one copy.
                        log("Number of nodes with replicas for this file: " + nReplicas);                        

                        //BACKUP 
                        //check how many nodes already have a copy. 
                        //if #nodes < replication factor, then proceed with backup
                        //else skip the backup for this file.

                        if (nReplicas < _REPLICATION_FACTOR) {                        

                            Integer nCopies = _REPLICATION_FACTOR - nReplicas;                       

                            Iterator It = occurences_uuid.keySet().iterator();
                            while (It.hasNext()) {
                                log("Number of additional copies needed: " + nCopies);

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
                                        occurences_uuid_files.put(sNamer, Integer.valueOf(was + 1));

                                        boolean bNodeDead = get_deadnode(sNamer);
                                        if (!bNodeDead) {
                                            log("backup added in Node: " + sNamer);

                                            //write to temp file
                                            String sFileName = sRoot + "backup_" + sNamer + "_" + sSeqID + "_" + nBatch + ".tmp";
                                            //p("Storing in file: " + sFileName);
                                            //p("Storing row: " + sRow);
                                            FileWriter fw = new FileWriter(sFileName, bAppend);
                                            BufferedWriter out = new BufferedWriter(fw);
                                            out.write(sRow);
                                            out.close();   

                                            //decrease #copies counter since we succesffully added a backup
                                            nCopies--;
                                        } else {
                                            //skip this node since it's declared as dead.
                                            log("Skipping backup for dead node: " + sNamer);
                                        }

                                    } catch (Exception ex) {
                                        log("ERROR: writing register");
                                    }
                                } else {
                                    log("backup skipped for Node: " + sNamer);
                                }
                            }

                        } else {
                            log("Number of nodes with replicas exceeds or equals Replication Factor. Skipping backup for this file." + _REPLICATION_FACTOR);                        
                        }
                        
                        //SYNC
                        //generate register for all sync nodes
                        Iterator It2 = occurences_uuid_sync.keySet().iterator();
                        while (It2.hasNext()) {               
                            String sNodeName = (String) It2.next();   
                            boolean bNodeDead = get_deadnode(sNodeName);
                             if (!bNodeDead) {
                                log("sync added for Node: " + sNodeName);
                                
                                String sNamer = sNodeName;
                                                                
                                int was = 0;
                                Object Got2;
                                if ((Got2 = occurences_uuid_files.get(sNamer)) != null) {
                                    was = (((Integer) Got2).intValue());
                                }
                                occurences_uuid_files.put(sNamer, Integer.valueOf(was + 1));

                                //write to temp file
                                String sFileName = sRoot + "backup_" + sNodeName + "_" + sSeqID + "_" + nBatch + ".tmp";
                                //p("Storing in file: " + sFileName);
                                //p("Storing row: " + sRow);
                                FileWriter fw = new FileWriter(sFileName, bAppend);
                                BufferedWriter out = new BufferedWriter(fw);
                                out.write(sRow);
                                out.close();   

                             }
                        }
                        
                    }
            }

            //p("\nRES:" + res);
            return 0;
         } catch (Exception e) {
             e.printStackTrace();
             log("Warning: There was an exception in BackupObject");
             return -1;
         }
    }

    public boolean checkBatch(long _batchnum)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {


        try {
            mCassandraClient.set_keyspace(keyspace);

            // read entire row
            SlicePredicate predicate = new SlicePredicate();
            SliceRange sliceRange = new SliceRange();
            sliceRange.setStart(new byte[0]);
            sliceRange.setFinish(new byte[0]);
            predicate.setSlice_range(sliceRange);

            String _key = Long.toString(_batchnum);
            p("checkbatch:" + _key);
            ColumnParent parent = new ColumnParent(columnFamilyBatch);
            byte[] kk = _key.getBytes();
            List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);
            String res = "";
            for (ColumnOrSuperColumn result : results) {
                Column column = result.column;
                String name2 = new String(column.getName(), UTF8);
                String val2 = new String(column.getValue(), UTF8);
                //p(name2 + " -> " + val2);
                if (name2.equals("ts_end")) {
                    p("ts_end: '" + val2 + "'");
                    //long nVal = Long.parseLong(val2);
                    //if (nVal > 0) return true;
                    if (val2.length() > 0) return true;
                }
            }
            return false;
        } catch (InvalidRequestException ex) {
            p("ex InvalidRequestException");
            return false;
        } catch (UnavailableException ex) {
            p("ex UnavailableException");
            return false;
        } catch (TimedOutException ex) {
            p("ex TimedOutException");
            return false;
        } catch (TException ex) {
            p("ex TException");
            return false;
        }
 }

 
    public int backupObjects(String _keyin, long nBatchNum, String sRoot, String sSeqID, int _REPLICATION_FACTOR)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {

        try {
            Date ts_start = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String sDate = sdf.format(ts_start);

            String sFilename = appendageRW + LOG_NAME_BACKUP_DEBUG_PATH + sDate + "_backup_server_debug.log";
            log = new PrintStream(new BufferedOutputStream(
                        new FileOutputStream(sFilename, true)));
            log("opening log file: " + sFilename);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        Stopwatch timer = new Stopwatch().start();

        mCassandraClient.set_keyspace(keyspace);

        // read entire row
        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();
        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        sliceRange.setCount(50000);
        predicate.setSlice_range(sliceRange);

        //sNames = new String[100];
        nCount = 0;

        SortableValueMap<String, Integer> occurences_hash = new SortableValueMap<String, Integer>();
        HashMap<String, String> occurences_names = new HashMap<String, String>();

        String _key = _keyin.toLowerCase();

        log("----Backup Batch: '" + nBatchNum + "'");

        
        HashMap<String, Integer> occurences_uuid_files = new HashMap<String, Integer>();
        set_backupnodes(keyspace, "NodeInfo","nodes", occurences_uuid_files, "backup");

        byte[] kk = _key.getBytes();

        mCassandraClient.set_keyspace("Keyspace1b");
        ColumnParent parent = new ColumnParent(columnFamily);
        List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);

        set_deadnodes();
        
        int nCount3 = 0;
        boolean bAppend = false;
        for (ColumnOrSuperColumn result : results) {
            String sNamer = new String(result.column.getValue(), UTF8);
            //***Integer nCount3 = (Integer) occurences_hash.get(sNamer);
            ///String sFileName = occurences_names.get(sNamer);
            log("Processing: '" + sNamer + "' - " + nCount3);
            nCount3++;
            int nres = 0;
            nres = backupObject(sNamer, "paths", nBatchNum, occurences_uuid_files, sRoot, sSeqID, bAppend, _REPLICATION_FACTOR);                
            if (nCount3 == 1) {
                bAppend = true;
            }
            if (nres < 0) return -1;
        }
        
        
        Iterator It2 = occurences_uuid_files.keySet().iterator();
        while (It2.hasNext()) {
            String sNamer = (String) It2.next();
            nCount3 = (Integer) occurences_uuid_files.get(sNamer);
            p(sNamer + " - " + nCount3);
            String sFileName = sRoot + "backup_" + sNamer + "_" + sSeqID + "_" + nBatchNum;
            if (nCount3 == 0) {
                log("blank batch: " + nBatchNum + " need for Node: " + sNamer);
                
                log("filename: " + sFileName);
                String sRow = "0,0,0";
                try { FileWriter fw = new FileWriter(sFileName, false);
                BufferedWriter out = new BufferedWriter(fw);
                out.write(sRow);
                out.close();
                } catch (Exception ex) {
                    p("ERROR: writing blank file");
                    return -1;
                }
            } else {
                log("renaming tmp file '" + sFileName + ".tmp" + "'");
                try {
                    File fr = new File(sFileName + ".tmp");
                    boolean bRes = fr.renameTo(new File(sFileName));
                    log("res rename = "+ bRes);
                    if (!bRes) {
                        log("ori file exists. deleting = '" + sFileName + "'");
                        File fro = new File(sFileName);
                        fro.delete();
                        bRes = fr.renameTo(new File(sFileName));    
                        log("res rename2 = "+ bRes);
                    }
                } catch (Exception e) {
                    log("Exception renaming tmp file");
                }
            }
            
            //Delete .gen if exist
            File tmpFile = new File(sFileName + ".gen");
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
        }
        
        log("Processed# objects: " + nCount3);
        return 0;
    }
    
    
    public int backupObjects_ori(String _keyin, long nBatchNum, String sRoot, String sSeqID, int _REPLICATION_FACTOR)
            throws UnsupportedEncodingException,
            InvalidRequestException,
            UnavailableException,
            TimedOutException,
            TException,
            NotFoundException {


        Stopwatch timer = new Stopwatch().start();

        mCassandraClient.set_keyspace(keyspace);

        // read entire row
        SlicePredicate predicate = new SlicePredicate();
        SliceRange sliceRange = new SliceRange();
        sliceRange.setStart(new byte[0]);
        sliceRange.setFinish(new byte[0]);
        sliceRange.setCount(50000);
        predicate.setSlice_range(sliceRange);

        //sNames = new String[100];
        nCount = 0;

        SortableValueMap<String, Integer> occurences_hash = new SortableValueMap<String, Integer>();
        HashMap<String, String> occurences_names = new HashMap<String, String>();

        String _key = _keyin.toLowerCase();

        p("----Backup Batch: '" + nBatchNum + "'");

        String delimiters = "+ ";
        StringTokenizer st = new StringTokenizer(_key.trim(), delimiters, true);
        String res = "";
        Integer nTokens = st.countTokens();

        while (st.hasMoreTokens()) {
            //nTokens = nTokens + 1;

            String w = st.nextToken();
            //p("token: " + w);

            byte[] kk = w.getBytes();

            mCassandraClient.set_keyspace("Keyspace1b");
            ColumnParent parent = new ColumnParent(columnFamily);
            List<ColumnOrSuperColumn> results = mCassandraClient.get_slice(ByteBuffer.wrap(kk), parent, predicate, ConsistencyLevel.ONE);

            for (ColumnOrSuperColumn result : results) {
                res += read_row_hash_cass(new String(result.column.getValue(), "UTF8"), occurences_hash, occurences_names);
            }
            //p("\ncount:" + nCount);

        }

        //p("\nnTokens:" + nTokens);

        //Iterator It = numbers.keySet().iterator();
        //while (It.hasNext()) {
        //    String sNamer = (String)It.next();
        //    p(sNamer + " - " + numbers.get(sNamer));
        //}

        //nt nCount2 = 0;

        
        ///res += "<table>";
        //res += "<tr style='text-align:center; vertical-align:middle'>";

        //p("\nnTokens:" + nTokens);
        //if (nTokens > 1) {
        //    nTokens = nTokens - 1;
        //}
        //p("\nnTokens2:" + nTokens);
        
        //***no need to sort
        //occurences_hash.sortByValue();

        HashMap<String, Integer> occurences_uuid_files = new HashMap<String, Integer>();

        set_backupnodes(keyspace, "NodeInfo","nodes", occurences_uuid_files, "backup");

        //boolean firsttime = true;
        //int nCurrent = 0;
        set_deadnodes();
        
        Iterator It = occurences_hash.keySet().iterator();
        while (It.hasNext()) {
            String sNamer = (String) It.next();
            //***Integer nCount3 = (Integer) occurences_hash.get(sNamer);
            ///String sFileName = occurences_names.get(sNamer);
            //p(sNamer + " - " + nCount3);
            int nres = backupObject(sNamer, "paths", nBatchNum, occurences_uuid_files, sRoot, sSeqID, true, _REPLICATION_FACTOR);
            if (nres < 0) return -1;
        }

        //p("Mode4!!!" + nCount);
        
        Iterator It2 = occurences_uuid_files.keySet().iterator();
        while (It2.hasNext()) {
            String sNamer = (String) It2.next();
            Integer nCount3 = (Integer) occurences_uuid_files.get(sNamer);
            p(sNamer + " - " + nCount3);
            if (nCount3 == 0) {
                p("blank batch: " + nBatchNum + " need for Node: " + sNamer);
                String sFileName = sRoot + "backup_" + sNamer + "_" + sSeqID + "_" + nBatchNum;
                p("filename: " + sFileName);
                String sRow = "0,0,0";
                try { FileWriter fw = new FileWriter(sFileName, true);
                BufferedWriter out = new BufferedWriter(fw);
                out.write(sRow);
                out.close();
                } catch (Exception ex) {
                    p("ERROR: writing blank file");
                    return -1;
                }
            }
        }

        return 0;
    }
    
    String get_thumb(String _fileName) {
            String fileExtension = (String) mapFileExtensions.get((_fileName.substring(_fileName.lastIndexOf("."), _fileName.length())).toLowerCase());
            String sPic = fileExtension.substring(fileExtension.lastIndexOf(",") + 1, fileExtension.length());

            return sPic;
    }
            
    public String get_vector(String _fileName, String _fontsize) {
        try {
            String sVector = "";
            if (_fileName != null && _fileName.length() > 0) {                
                String fileExtension = (String) mapFileExtensions.get((_fileName.substring(_fileName.lastIndexOf("."), _fileName.length())).toLowerCase());
                String sPic = fileExtension.substring(fileExtension.lastIndexOf(",") + 1, fileExtension.length());            
                sVector = "<font class='jquerycursorpointer'  style=\"display:table-cell; vertical-align:middle; font-family: 'My Font'; font-size:" + _fontsize + "px\">" + sPic + "</font>";              
            }
            return sVector;            
        } catch (Exception e) {
            String timestampPerf = (new Date()).toGMTString();    
            logErrors(timestampPerf,"[EXCEPTION] get_vector: " + _fileName + " " + _fontsize, true);
            return "";
        }
    }
    
    boolean isExtensionOk(String _extension) {
            String objExtension = (String) mapFileExtensions.get(_extension);

            if (objExtension == null) {
               return false;
            }
            else {
                return true;
            }
    }
    
    String gen_play_link (String sPlayURL, String sLocalIP, String _key) {
        
        String sRedirLink;
        
        //PLAY LINK (Server)
            //p(sPlayURL);
            String sPlayURL_ori = sPlayURL;
            if (sPlayURL.length() > 0) {
                int n = sPlayURL.indexOf("'");
                if (n > 0) {
                    sRedirLink = "http://" + sLocalIP + sPlayURL.substring(0, n);                                                    
                } else {                    
                    sRedirLink = "http://" + sLocalIP + sPlayURL;                                                    
                }
                //p(sRedirLink);
                sPlayURL = "<INPUT TYPE=button value=\"Server\" id_ onclick=\"golink('" + sRedirLink + "','" + _key + "',1)\"/>";
            } else {
                sPlayURL = "";
            }
             
             
             
            return sPlayURL;
        
    } 
    
    public  String read_send_link(String sNamer, String sFileName, String _keyin, String _host, String _port, Boolean _cloudhosted, String _clientIP) {
              String sSendURL = "";
              try{
                        String _key = _keyin.toLowerCase();
                        int nError = 0;
                        String sViewURL = "";
                        try {
                            //p("   [B]");
                            sViewURL = get_row_attribute(keyspace, "Standard1", sNamer, "ViewLink3");
                        } catch (Exception ex) {
                            p("   [Be]");
                            nError += 1;
                        }
                        
                        
                     
                        //p("   [D]");
                        sURLPack sURLpack = new sURLPack();
                        boolean bHaveServerLink = false;

                       
                        if (sViewURL.length() < 1) {
                            bHaveServerLink = false;
                            try {
                                sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false);    
                                
                                //p("Viewlink ---> '" + sURLpack.sViewURL + "'");
                                if (sURLpack.sViewURL.length() > 0 && !sURLpack.sViewURL.equals("ERROR")) {
                                    sViewURL = sURLpack.sViewURL;
                                    //int ires = insert_column("Keyspace1b","Standard1", sNamer, "ViewLink3", sViewURL); 
                                }
                                else {
                                    sViewURL = "";
                                }

                            } catch (Exception ex) {
                                nError += 1;
                            }
                        } else {
                            bHaveServerLink = true;
                        }
                        
                        //InetAddress clientIP = InetAddress.getLocalHost();
                        InetAddress clientIP = getLocalAddress();
                        String sLocalIP = "127.0.0.1";
                        if (clientIP != null) {
                            sLocalIP = clientIP.getHostAddress();;                            
                        }
                        
                        if (nError < 1 || _cloudhosted) {
                                                        
                            if (!bHaveServerLink && !_cloudhosted) {
                                sViewURL = sURLpack.sViewURL;                                    
                            }
                            String sViewURLOri = sViewURL;    
                            
                            sURLPack tmpURLpack = null;                                        
                            
                            //VIEW LINK
                            sViewURL = gen_view_link2(_host, _port, sViewURL, bHaveServerLink, _cloudhosted, sNamer, sURLpack, sFileName, _key, 1.0, sLocalIP, false); 
                            
                            //SEND LINK
                            tmpURLpack = gen_send_link2(sViewURL, sViewURLOri, _cloudhosted, sNamer, sURLpack, bHaveServerLink, _clientIP, "200", _key, _host, _port);
                            sSendURL = tmpURLpack.sSendURL;
                            
                        } else {
                            sSendURL = "ERROR";
                        }
              }catch(Throwable th){
                  th.printStackTrace();
                  sSendURL = "ERROR";
              }
              
              return sSendURL;

    }
    
    public String read_openFolder_link(String sNamer, String sFileName, String _keyin, String _host, String _port, Boolean _cloudhosted, String _clientIP) {
        String sOpenURL = "";
        try{
                int nError=0;
                String sViewURL = "";
                String _key = _keyin.toLowerCase();
                try {
                    //p("   [B]");
                    sViewURL = get_row_attribute(keyspace, "Standard1", sNamer, "ViewLink3");
                } catch (Exception ex) {
                    p("   [Be]");
                    nError += 1;
                }


                sURLPack sURLpack = new sURLPack();
                boolean bHaveServerLink = false;

                if (sViewURL.length() < 1) {
                    bHaveServerLink = false;
                    try {
                        sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false);    

                        if (sURLpack.sOpenURL.length() > 0 && !sURLpack.sOpenURL.equals("ERROR")) {
                            sOpenURL = sURLpack.sFolderURL;
                        } else {
                            p("******* SURLPack returned empty.");
                        }

                    } catch (Exception ex) {
                        nError += 1;
                    }
                } else {
                    bHaveServerLink = true;
                }

                InetAddress clientIP = getLocalAddress();

                if (nError < 1 || _cloudhosted) {
                   
                    sURLPack tmpURLpack = new sURLPack();                                        
 
                    //OPEN AND FOLDER LINK
                    tmpURLpack = gen_open_link2(sOpenURL, sURLpack, sNamer, _cloudhosted, _clientIP, _key);                            
                    sOpenURL = tmpURLpack.sFolderURL;
              
                } else {
                    sOpenURL = "ERROR";
                }
        }catch(Throwable th){
            th.printStackTrace();
            sOpenURL = "ERROR";
        }
        
        return sOpenURL;
        
    }
    
    public String read_open_link(String sNamer, String sFileName, String _keyin, String _host, String _port, Boolean _cloudhosted, String _clientIP) {
        String sOpenURL = "";
        try{
                int nError=0;
                String sViewURL = "";
                String _key = _keyin.toLowerCase();
                try {
                    //p("   [B]");
                    sViewURL = get_row_attribute(keyspace, "Standard1", sNamer, "ViewLink3");
                } catch (Exception ex) {
                    p("   [Be]");
                    nError += 1;
                }


                sURLPack sURLpack = new sURLPack();
                boolean bHaveServerLink = false;
               
                if (sViewURL.length() < 1) {
                    bHaveServerLink = false;
                    try {
                        sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false);    

                        if (sURLpack.sOpenURL.length() > 0 && !sURLpack.sOpenURL.equals("ERROR")) {
                            sOpenURL = sURLpack.sOpenURL;
                        } else {
                            p("*******************************************");
                            p("************ sURLPack is empty ************");
                            p("*******************************************");
                        }

                    } catch (Exception ex) {
                        nError += 1;
                    }
                } else {
                    bHaveServerLink = true;
                }

                InetAddress clientIP = getLocalAddress();

                if (nError < 1 || _cloudhosted) {
                   
                    sURLPack tmpURLpack = new sURLPack();                                        
 
                    //OPEN AND FOLDER LINK
                    tmpURLpack = gen_open_link2(sOpenURL, sURLpack, sNamer, _cloudhosted, _clientIP, _key);                            
                    sOpenURL = tmpURLpack.sOpenURL;
                    
                    p("*******************************************");
                    p("sOpenURL = " + sOpenURL);
                    p("*******************************************");

              
                } else {
                    sOpenURL = "ERROR";
                }
        }catch(Throwable th){
            th.printStackTrace();
            sOpenURL = "ERROR";
        }
        
        return sOpenURL;
        
    }
    
    public String read_file_path (String sNamer) {
   
        return "";
    } 
    
    public String read_view_link(String sNamer,String sFileName,String _keyin,
                                String _host,
                                String _port,
                                Boolean _cloudhosted,
                                String _clientIP){
              
            String sViewURL = "";
        try{
            int nError = 0;
           
            String _key = _keyin.toLowerCase();
            
            try {
                //p("   [B]");
                sViewURL = get_row_attribute(keyspace, "Standard1", sNamer, "ViewLink3");
            } catch (Exception ex) {
                p("   [Be]");
                nError += 1;
            }

            //p("   [D]");
            sURLPack sURLpack = new sURLPack();
            boolean bHaveServerLink = false;

            String sOpenURL = "";

            if(sViewURL.length()>=1){
                //Controlo que exista realmente en el directorio local
                //Si ViewLink3 tiene algo es porque el archivo est� local
                int nPos=0;
                int nPos2=0;
                
                p("@@@@@@@@@@");
                p("@@@@@@@@ sViewURL = '" + sViewURL + "'");
                
                if (sViewURL != null && !sViewURL.contains("ERROR")) {
                    nPos = sViewURL.indexOf(".");
                    nPos2 = sViewURL.indexOf("/") + 1;                                    
                    String sFile =  sViewURL.substring(nPos2,nPos);
                    File fFileAux=new File(sFile);
                    if(!fFileAux.exists()||fFileAux.length()<=0){
                        sViewURL="";
                    }
                } else {
                    p("caso error...");
                    sViewURL = "";
                }
                               
            }
            
            if (sViewURL.length() < 1) {
                bHaveServerLink = false;
                try {
                    sURLpack = get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, true, false);    

                    //p("Viewlink ---> '" + sURLpack.sViewURL + "'");
                    if (sURLpack.sViewURL.length() > 0 && !sURLpack.sViewURL.equals("ERROR")) {
                        sViewURL = sURLpack.sViewURL;
                        //int ires = insert_column("Keyspace1b","Standard1", sNamer, "ViewLink3", sViewURL); 
                    }
                    else {
                        p("---------");
                        p("returning status: " + sURLpack.sStatus);
                        sViewURL = sURLpack.sStatus;
                        return sViewURL;
                    }
                } catch (Exception ex) {
                    nError += 1;
                }
            } else {
                bHaveServerLink = true;
             
            }

            //InetAddress clientIP = InetAddress.getLocalHost();
          

            if (nError < 1 || _cloudhosted) {

                if (!bHaveServerLink && !_cloudhosted) {
                    sViewURL = sURLpack.sViewURL;                                    
                }
             
                //VIEW LINK
                try{
                    InetAddress clientIP = getLocalAddress();
                    String sLocalIP = "127.0.0.1";
                    if (clientIP != null) {
                        sLocalIP = clientIP.getHostAddress();;                            
                    } 
                    
                    sViewURL = gen_view_link2(_host, _port, sViewURL, bHaveServerLink, _cloudhosted, sNamer, sURLpack, sFileName, _key, 0, sLocalIP, false);
                }catch(Throwable ue){
                    ue.printStackTrace();
                    log("ERROR:"+ue.getMessage());
                    
                }
            } else {
                sViewURL = "ERROR";
           }
            
        }catch(Throwable th){
            th.printStackTrace();
            sViewURL="";
        }
            
        return sViewURL;
}
     
String gen_view_link2(String _host, String _port, String sViewURL, boolean bHaveServerLink, boolean _cloudhosted, String sNamer, sURLPack sURLpack, String sFileName, String _key, double nImgRatio, String sLocalIP, boolean _mobile) {
                            
                  try {
                      
                        String sHostIP;
                        String sFile;
                        String sRedirLink;           
                        String sHeightNew;
                        
                            //PREPARE VIEW LINK
                            
                            //sURLPack sURLpack = new sURLPack();
                            //sURLpack = get_remote_link(sNamer,"paths", true);

                            p("sViewURL[1] = '" + sViewURL + "'");
                            p("sNamer = '" + sNamer + "'");
                            p("sFileName = '" + sFileName + "'");

                            if (!bHaveServerLink && !_cloudhosted) {
                                sHostIP = sURLpack.sHostIP;
                                _host = sHostIP;
                                _port = sURLpack.sHostPort;                                
                                sLocalIP = sHostIP;
                                sViewURL = sURLpack.sViewURL;                                
                            }

                            int nPos = 0;
                            int nPos2 = 0;

                            if (sViewURL != null) {
                                nPos = sViewURL.indexOf(".");
                                nPos2 = sViewURL.indexOf("/") + 1;                                    
                            }
                            
                            p("sViewURL[2] = '" + sViewURL + "'");
                            
                            String sViewURLOri = sViewURL;
                            
                            if (sViewURL != null) {                                
                                sFile = URLEncoder.encode(sViewURL.substring(nPos2,nPos), "UTF-8");
                                String sPort = sViewURL.substring(0,sViewURL.indexOf("/"));
                            } else {
                                sFile = "cass%2Fpic%2F" + sNamer;
                            } 
                            
                            if (is_photo(sFileName)) {
                                    /*double dHeightNew = 1000 / nImgRatio;      
                                    int nHeightNew = (int)dHeightNew;
                                    sHeightNew = Integer.toString(nHeightNew);*/
                                    String sHeight = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                                    String sWidth = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");
                            
                                    sRedirLink = "http://" + sLocalIP +":"+_port+"/cass/viewimg.htm?file=" + sFile + "&host=" + _host + "&port=" + _port + "&height=" + sHeight + "&width="+sWidth;                                        
                            } else {
                                    sRedirLink = "";
                                    if (sViewURL != null) {
                                        sRedirLink = "http://" + sLocalIP+ sViewURL;                                       
                                    }
                            }
                            
                            //CHECK VAULT VIEW
                            if (_cloudhosted) {
                                    sRedirLink = "cassvault/" + sViewURLOri.substring(nPos2,nPos);
                                    if (!_mobile) {
                                        sViewURL = "<INPUT TYPE=button value=\"VaultView\" onclick=\"golink('" + sRedirLink + "','" + _key + "',1)\"/>";
                                    } else {                                        
                                        sViewURL = sRedirLink;                                        
                                    }                                  
                                
                            } else {
                                if (sRedirLink.length() > 0) {
                                    sViewURL = sRedirLink ;
                                } else {
                                    sViewURL = "";                                                               
                                }
                            }                            
                          
                } catch (Exception e) {
                    
                }
                  
                return sViewURL;

    }

sURLPack gen_open_link2(String sOpenURL, 
                    sURLPack sURLpack, 
                    String sNamer, 
                    boolean _cloudhosted, 
                    String _clientIP,
                    String _key) {
        
                    sURLPack tmpURLpack = new sURLPack();
                    
                    String sFolderURL;
                    String sRedirLink;
                    
                    try {
                        p("************* clientIP: " + _clientIP);
                        if (_clientIP.equals("0:0:0:0:0:0:0:1")) {
                                    _clientIP = "127.0.0.1";
                        }
        
                    //OPEN LINK (local Client)
                            
                            if (sOpenURL == null || sOpenURL.length() == 0) {
                                if (sOpenURL != null)
                                    p("open url1 == " + sOpenURL + "[" + sOpenURL.length() + "]");
                                else {
                                    p("open url1 == null");
                                }
                                
                                sURLpack = get_remote_link2(sNamer,"paths", false, _cloudhosted, _clientIP, true, false);    
                           
                                sOpenURL = sURLpack.sOpenURL;
                                p("************* open url2 == " + sOpenURL);                                                                
                               
                                if (sOpenURL.length() > 0) {
                                    sRedirLink = "http://" + _clientIP + sOpenURL;                                
                                    sOpenURL =  sRedirLink ;     
                                  
                                    String sOpenURL2 = sURLpack.sOpenURL;                                        
                                    sFolderURL = sOpenURL2;
                                    sFolderURL = sFolderURL.replaceAll("%5C", "%2F");                                    
                                    if (sFolderURL.contains("%2F")) {
                                        sFolderURL = sFolderURL.substring(0,sFolderURL.lastIndexOf("%2F"));
                                        if (sFolderURL.contains("%2F")) {
                                            sRedirLink = "http://" + _clientIP + sFolderURL.substring(0, sFolderURL.lastIndexOf("%2F"));                                                                                                                   
                                            sFolderURL =  sRedirLink ;                                                                
                                        } else {
                                            sFolderURL = "";
                                        }
                                    } else {
                                        sFolderURL = "";
                                    }
                                } else {
                                    sOpenURL = "";
                                    sFolderURL = "";                                    
                                }
                            } else {
                                sRedirLink = "http://" + _clientIP + sOpenURL;                                
                                p("redirlink1 == " + sRedirLink);
                                sOpenURL =sRedirLink;
                                
                                String sOpenURL2 = sURLpack.sOpenURL;                                        
                                p("open url3 == " + sOpenURL2);                                  
                                sFolderURL = sOpenURL2.substring(0,sOpenURL2.lastIndexOf("%2F"));     
                                if (sFolderURL.contains("%2F")) {                                    
                                    sRedirLink = "http://" + _clientIP + sFolderURL.substring(0, sFolderURL.lastIndexOf("%2F"));                                   
                                } else {
                                    sRedirLink = "http://" + _clientIP + sFolderURL;                                                                       
                                }
                                sFolderURL = sRedirLink;
                            }
                            
                            tmpURLpack.sFolderURL = sFolderURL.replace("id_","id='folderlnk"+sNamer+"' class='jqueryhidden'");
                            tmpURLpack.sOpenURL = sOpenURL.replace("id_","id='openlnk"+sNamer+"' class='jqueryhidden'");
                            
                    } catch (Exception e) {
                            e.printStackTrace();
                            p("****** exception in gen_open_link2");
                            tmpURLpack.sFolderURL = "";
                            tmpURLpack.sOpenURL = "";
                    }
                    
                    return tmpURLpack;
    }

 sURLPack gen_send_link2(String sViewURL, 
            String sViewURLOri, 
            boolean _cloudhosted, 
            String sNamer, 
            sURLPack sURLpack,
            boolean bHaveServerLink,
            String _clientIP,
            String sHeightNew,
            String _key,
            String _host,
            String _port) {
        
        int nPos;
        int nPos2;
        String sFile;
        String sHostIP;
        String sRedirLink;
        String sSendURL = "";
        
        //PREPARE SEND  LINK
        //p(sViewURL);

        try {
            
            //p("sViewURL = '" + sViewURL + "'");
            if (sViewURL != null) {   
                nPos = sViewURLOri.indexOf(".");
                nPos2 = sViewURLOri.indexOf("/") + 1;    
                sFile = URLEncoder.encode(sViewURLOri.substring(nPos2,nPos), "UTF-8");

            } else {
                sFile = "cass%2Fpic%2F" + sNamer;
            } 

            if (_cloudhosted) {
                sFile = "cass%2Fpic%2F" + sNamer;
            } else {
                //sURLPack sURLpack = new sURLPack();

                if (!bHaveServerLink) {
                    sURLpack = get_remote_link2(sNamer,"paths", false, _cloudhosted, _clientIP, true, false);

                    sHostIP = sURLpack.sHostIP;
                    _host = sHostIP;
                    _port = sURLpack.sHostPort;   

                    String sViewURL2 = sURLpack.sViewURL;

                    if (sViewURL2 != null) {
                        nPos = sViewURL2.indexOf(".");
                        nPos2 = sViewURL2.indexOf("/") + 1;    
                        sFile = URLEncoder.encode(sViewURL2.substring(nPos2,nPos), "UTF-8");                                        
                    }
                }
            }

            if (_host != null) {
                if(sFile!=null && sFile.trim().length()>0){
                    sRedirLink = "sendform.htm?file=" + sFile + "&host=" + _host + "&port=" + _port + "&height=" + sHeightNew + "&width=200";                                
                    //sSendURL = "<a href='sendform.htm?file=" + sFile + "&host=" + _host + "&port=" + _port + "'>share</a>";
                    sSendURL =sRedirLink;
                }
            } else {
                sSendURL = "";
            }
          
        } catch (Exception e) {
            
        }
           
        sURLPack tmpURLpack = new sURLPack();
        tmpURLpack.sSendURL = sSendURL;
        tmpURLpack.sHostIP = _host;
        tmpURLpack.sHostPort = _port;
        
        return tmpURLpack;
    }

String gen_view_link(String _host, String _port, String sViewURL, boolean bHaveServerLink, boolean _cloudhosted, String sNamer, sURLPack sURLpack, String sFileName, String _key, double nImgRatio, String sLocalIP) {
                            
                  try {
                      
                        String sHostIP;
                        String sFile;
                        String sRedirLink;           
                        String sHeightNew;
                        
                            //PREPARE VIEW LINK
                            
                            //sURLPack sURLpack = new sURLPack();
                            //sURLpack = get_remote_link(sNamer,"paths", true);

                            p("sViewURL1 = '" + sViewURL + "'");

                            if (!bHaveServerLink && !_cloudhosted) {
                                sHostIP = sURLpack.sHostIP;
                                _host = sHostIP;
                                _port = sURLpack.sHostPort;                                
                                sLocalIP = sHostIP;
                                sViewURL = sURLpack.sViewURL;                                
                            }

                            int nPos = 0;
                            int nPos2 = 0;

                            if (sViewURL != null) {
                                nPos = sViewURL.indexOf(".");
                                nPos2 = sViewURL.indexOf("/") + 1;                                    
                            }
                            
                            p("sViewURL2 = '" + sViewURL + "'");
                            
                            String sViewURLOri = sViewURL;
                            
                            if (sViewURL != null) {                                
                                sFile = URLEncoder.encode(sViewURL.substring(nPos2,nPos), "UTF-8");
                                String sPort = sViewURL.substring(0,sViewURL.indexOf("/"));
                            } else {
                                sFile = "cass%2Fpic%2F" + sNamer;
                            } 
                            
                            if (sFileName.toLowerCase().contains(".jpg")) {
                                    /*double dHeightNew = 1000 / nImgRatio;      
                                    int nHeightNew = (int)dHeightNew;
                                    sHeightNew = Integer.toString(nHeightNew);*/
                                    String sHeight = get_row_attribute(keyspace, "Standard1", sNamer, "img_height");
                                    String sWidth = get_row_attribute(keyspace, "Standard1", sNamer, "img_width");
                            
                                    sRedirLink = "viewimg.htm?file=" + sFile + "&host=" + _host + "&port=" + _port + "&height=" + sHeight + "&width="+sWidth;                                        
                            } else {
                                    sRedirLink = "";
                                    if (sViewURL != null) {
                                        sRedirLink = "http://" + sLocalIP + sViewURL.substring(0, sViewURL.indexOf("'"));                                       
                                    }
                            }
                            
                            if (sRedirLink.length() > 0) {
                                if (!_cloudhosted) {
                                    sViewURL = "<INPUT TYPE=button value=\"View\" id_ onclick=\"golink('" + sRedirLink + "','" + _key + "',1)\"/>"; 
                                } else {
                                    //sViewURL = "<INPUT TYPE=button value=\"ViewAWS\" onclick=\"golink('" + sRedirLink + "','" + _key + "')\"/>"; 
                                    //String sFileType = sFileName.substring(sFileName.indexOf("."), sFileName.length());
                                    //sRedirLink = "cass/cassvault/" + _key + "." + sFileType;
                                    sRedirLink = "cassvault/" + sViewURLOri.substring(nPos2,nPos);
                                    sViewURL = "<INPUT TYPE=button value=\"VaultView\" onclick=\"golink('" + sRedirLink + "','" + _key + "',1)\"/>"; 
                                }
                            } else {
                                sViewURL = "";
                            }
                 sViewURL=sViewURL.replace("id_","id='viewlnk"+sNamer+"' class='jqueryhidden'");           
                } catch (Exception e) {
                    
                }
                  
                return sViewURL;

    }
    
    
    
            
    sURLPack gen_send_link(String sViewURL, 
            String sViewURLOri, 
            boolean _cloudhosted, 
            String sNamer, 
            sURLPack sURLpack,
            boolean bHaveServerLink,
            String _clientIP,
            String sHeightNew,
            String _key,
            String _host,
            String _port) {
        
        int nPos;
        int nPos2;
        String sFile;
        String sHostIP;
        String sRedirLink;
        String sSendURL = "";
        String sOpenURL;
        
        //PREPARE SEND  LINK
        //p(sViewURL);

        try {
            
            //p("sViewURL = '" + sViewURL + "'");
            if (sViewURL != null) {   
                nPos = sViewURLOri.indexOf(".");
                nPos2 = sViewURLOri.indexOf("/") + 1;    
                sFile = URLEncoder.encode(sViewURLOri.substring(nPos2,nPos), "UTF-8");

            } else {
                sFile = "cass%2Fpic%2F" + sNamer;
            } 

            if (_cloudhosted) {
                sFile = "cass%2Fpic%2F" + sNamer;
            } else {
                //sURLPack sURLpack = new sURLPack();

                if (!bHaveServerLink) {
                    sURLpack = get_remote_link(sNamer,"paths", false, _cloudhosted, _clientIP);

                    sHostIP = sURLpack.sHostIP;
                    _host = sHostIP;
                    _port = sURLpack.sHostPort;   

                    String sViewURL2 = sURLpack.sViewURL;

                    if (sViewURL2 != null) {
                        nPos = sViewURL2.indexOf(".");
                        nPos2 = sViewURL2.indexOf("/") + 1;    
                        sFile = URLEncoder.encode(sViewURL2.substring(nPos2,nPos), "UTF-8");                                        
                    }

                    if (sURLpack.sOpenURL != null) {
                        sOpenURL = sURLpack.sOpenURL;
                    }
                }
            }

            if (_host != null) {
                sRedirLink = "sendform.htm?file=" + sFile + "&host=" + _host + "&port=" + _port + "&height=" + sHeightNew + "&width=200";                                
                //sSendURL = "<a href='sendform.htm?file=" + sFile + "&host=" + _host + "&port=" + _port + "'>share</a>";
                sSendURL = "<INPUT TYPE=button value=\"Send\" id_ onclick=\"golink('" + sRedirLink + "','" + _key + "',1)\"/>";                                                            
            } else {
                sSendURL = "";
            }
           sSendURL=sSendURL.replace("id_","id='sendlnk"+sNamer+"' class='jqueryhidden'"); 
        } catch (Exception e) {
            
        }
           
        sURLPack tmpURLpack = new sURLPack();
        tmpURLpack.sSendURL = sSendURL;
        tmpURLpack.sHostIP = _host;
        tmpURLpack.sHostPort = _port;
        
        return tmpURLpack;
    }
    
    public static String getConfig(String _name, String _config) {
        
        try {
            File f = new File(_config);
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();
                String r = props.getProperty(_name);
                if (r != null ) {
                    //p("Old value = " + r);   
                    return r;
                } else {
                    return "";
                }
            } else {
                p("File not found. exiting...");
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }              
    } 
    
    
    
    sURLPack gen_open_link(String sOpenURL, 
                    sURLPack sURLpack, 
                    String sNamer, 
                    boolean _cloudhosted, 
                    String _clientIP,
                    String _key) {
        
        
                    p("WARNING :RUTINA VIEJA!!!!");
        
                    sURLPack tmpURLpack = new sURLPack();
                    
                    String sFolderURL;
                    String sRedirLink;
                    
                    try {
                        if (_clientIP.equals("0:0:0:0:0:0:0:1")) {
                                    _clientIP = "127.0.0.1";
                         }
        
                    //OPEN LINK (local Client)
                            //p("open url1 == " + sOpenURL);
                            if (sOpenURL.length() == 0) {
                                
                                sURLpack = get_remote_link(sNamer,"paths", false, _cloudhosted, _clientIP);    
                           
                                sOpenURL = sURLpack.sOpenURL;
                                //p("open url2 == " + sOpenURL);
                                
                                
                               
                                if (sOpenURL.length() > 0) {
                                    sRedirLink = "http://" + _clientIP + sOpenURL.substring(0, sOpenURL.indexOf("'"));                                
                                    //p("redirlink1 == " + sRedirLink);
                                    sOpenURL = "<INPUT TYPE=button id_  value=\"Open\" onclick=\"golink('" + sRedirLink + "','" + _key + "',1)\"/>";                                    
                                    //p("sOpenURL == " + sOpenURL);

                                    String sOpenURL2 = sURLpack.sOpenURL.substring(0, sURLpack.sOpenURL.indexOf("'"));;                                        
                                    //p("open url3 == " + sOpenURL2);                                  
                                    //String sChar = "";
                                    sFolderURL = sOpenURL2;
                                    sFolderURL = sFolderURL.replaceAll("%5C", "%2F");                                    
                                    //p("open url4 == " + sFolderURL);                                  
                                    if (sFolderURL.contains("%2F")) {
                                        sFolderURL = sFolderURL.substring(0,sFolderURL.lastIndexOf("%2F"));
                                        if (sFolderURL.contains("%2F")) {
                                            sRedirLink = "http://" + _clientIP + sFolderURL.substring(0, sFolderURL.lastIndexOf("%2F"));                                                                                                                   
                                            //p("redirlink2 == " + sRedirLink);                                    
                                            sFolderURL = "<INPUT TYPE=button id_ value=\"Folder\" onclick=\"golink('" + sRedirLink + "','" + _key + "',1)\"/>";                                                                    
                                            //p("sFolderUrl == " + sFolderURL);                                            
                                        } else {
                                            sFolderURL = "";
                                        }
                                    } else {
                                        sFolderURL = "";
                                    }
                                } else {
                                    sOpenURL = "";
                                    sFolderURL = "";                                    
                                }
                            } else {
                                sRedirLink = "http://" + _clientIP + sOpenURL.substring(0, sOpenURL.indexOf("'"));                                
                                //p("redirlink1 == " + sRedirLink);
                                sOpenURL = "<INPUT TYPE=button id_ value=\"Open\" onclick=\"golink('" + sRedirLink + "','" + _key + "',1)\"/>";    
                                
                                String sOpenURL2 = sURLpack.sOpenURL.substring(0, sURLpack.sOpenURL.indexOf("'"));;                                        
                                //p("open url3 == " + sOpenURL2);                                  
                                sFolderURL = sOpenURL2.substring(0,sOpenURL2.lastIndexOf("%2F"));     
                                if (sFolderURL.contains("%2F")) {                                    
                                    sRedirLink = "http://" + _clientIP + sFolderURL.substring(0, sFolderURL.lastIndexOf("%2F"));                                   
                                } else {
                                    sRedirLink = "http://" + _clientIP + sFolderURL;                                                                       
                                }
                                //p("redirlink2 == " + sRedirLink);                                    
                                sFolderURL = "<INPUT TYPE=button id_ value=\"Folder\" onclick=\"golink('" + sRedirLink + "','" + _key + "',1)\"/>";                                                                    
                                //p("sfolderurl == " + sFolderURL);                                
                            }
                            
                            tmpURLpack.sFolderURL = sFolderURL.replace("id_","id='folderlnk"+sNamer+"' class='jqueryhidden'");
                            tmpURLpack.sOpenURL = sOpenURL.replace("id_","id='openlnk"+sNamer+"' class='jqueryhidden'");
                            
                    } catch (Exception e) {
                        
                            tmpURLpack.sFolderURL = "";
                            tmpURLpack.sOpenURL = "";
                    }
                    
                    return tmpURLpack;
    }
    
    public static void loadFileExtensions() {
        try {
            FileInputStream fileExtensions = new FileInputStream(appendage + "../scrubber/config/FileExtensions_All.txt");
            Scanner objScanner = new Scanner(fileExtensions);
            
            mapFileExtensions.clear();
            
            while (objScanner.hasNext()) {                
                String line = objScanner.nextLine();
                String key = line.substring(0, line.indexOf(","));
                String value = line.substring(line.indexOf(",") + 1, line.length()).toLowerCase();
                
                mapFileExtensions.put(key, value);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    
    void loadProps() throws IOException {
        File f = new File
                (
                appendage +
                ".."+
                File.separator+
                "scrubber"+
                File.separator+
                "config"+
                File.separator+
                "www-processor.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
            String r = props.getProperty("thumbnaildir");
            if (r != null) {
                THUMBNAIL_OUTPUT_DIR = r;
            }
            p("Thumbnail dir = " + THUMBNAIL_OUTPUT_DIR);

        } else {
           p("WARNING config/www.processor-properties does not exist");
        }
    }
    
    void loadDBMode() throws IOException {
        File f = new File
                (
                appendage + 
                ".."+
                File.separator+
                "rtserver"+
                File.separator+
                "config"+
                File.separator+
                "www-server.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
            String r = props.getProperty("dbmode");
            if (r != null) {
                dbmode = r;
            }
            p("dbmode=" + dbmode);

        } else {
           p("WARNING config/www.server-properties does not exist");
        }
    }
 
    
    public int getfile(String sFullPath, String sStorePath, int _tries, long _timer, int _timeout) throws IOException {

        p("source=" + sFullPath);
        p("dest=" + sStorePath);
     
        try {
            boolean gotfile = false;
            //int tries = 5;
            int nres = 0;
            //long timer = 500;
            
            while (_tries > 0 && !gotfile) {
                p("try # " + _tries);
 
                nres = HTTPRequestPoster.sendGetRequest(sFullPath, null, sStorePath, _timeout);
 
                if (nres > 0) {
                    gotfile = true;
                } else {
                    _tries--;
                    if(_tries > 0){
                        _timer += 500;
                        Thread.sleep(_timer);
                    }
                }
            }
            return nres;
        } catch (Exception e) {
            log("exception getfile(). exiting...");
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

    private boolean si_inline(String _sFileNameOri) {
        String sFileNameOri = _sFileNameOri.toLowerCase();
        return  sFileNameOri.endsWith(".pdf") || sFileNameOri.endsWith(".txt") || sFileNameOri.endsWith(".html")|| sFileNameOri.endsWith(".htm") ;
    }
    
    
    
    public String insert_hashtag(String _keyspace, String _key, String _hashkey, boolean _autocomplete, String _datemodified) {
        try {
            String delimiters = "+ ";
            int res_code = 0;
            boolean bLongString = false;
            
            
            if (_hashkey.startsWith("\"") && _hashkey.endsWith("\"")) {
                _hashkey = _hashkey.replace("\"","");
                
                res_code = insertSuperColumn(_keyspace, "Super2",_key, "hashesm", _hashkey, _hashkey);
                res_code += insert_column(_keyspace,"Standard1",_hashkey, _datemodified, _key );
                
                if (_autocomplete) {
                    for (int idx=1; idx<_hashkey.length();idx++) {
                        for (int idx2=0; idx2<=_hashkey.length()-1;idx2++) {
                            if ((idx + idx2) <= _hashkey.length()) {
                                String str = _hashkey.substring(idx2, idx + idx2);
                                insert_column_auto_complete("Keyspace1b", str, _hashkey, _hashkey);
                                insert_column(_keyspace,"Standard1", str, _datemodified, _key);
                            }
                        }
                    }
                }
            }
            
            StringTokenizer st = new StringTokenizer(_hashkey.trim(), delimiters, false);
            while (st.hasMoreTokens()) {
                String w = st.nextToken();
                p("Inserting hashkey '" + w + "'");  

                if (!bLongString) {
                    //insert manual hashes
                    res_code = insertSuperColumn(_keyspace, "Super2",_key, "hashesm", w, w);
                    p("RET: '" + res_code);                
                }
                
                res_code += insert_column(_keyspace,"Standard1",w, _datemodified, _key );
                p("RET2: '" + res_code);

                if (_autocomplete) {
                    for (int idx=1; idx<_hashkey.length();idx++) {
                        for (int idx2=0; idx2<=_hashkey.length()-1;idx2++) {
                            if ((idx + idx2) <= _hashkey.length()) {
                                String str = _hashkey.substring(idx2, idx + idx2);
                                
                                //add substring in autocomplete table (e.g. key: 'P' name/value: 'Pictures')
                                insert_column_auto_complete("Keyspace1b", str, w, w);
                                //also associate hash to subtring (e.g. key:'P'  name/value:file1_hash
                                insert_column(_keyspace,"Standard1", str, _datemodified, _key);
                            }
                        }
                    }
                }
            }

            return String.valueOf(res_code);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRequestException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnavailableException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimedOutException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NotFoundException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
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

}

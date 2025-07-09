/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */

package utils;

import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.NotFoundException;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.thrift.TException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;

import static utils.LocalFuncs.bConsole;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Properties;
import utils.Stopwatch;

import java.util.StringTokenizer;
//import static utils.Cass7Funcs.p;

import java.util.Iterator;
import java.util.concurrent.ConcurrentNavigableMap;

import org.apache.hc.core5.http.ClassicHttpRequest;

import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;

import java.util.Map;

public class WebFuncs {

        static Map mapBatches = new java.util.HashMap();
    
        static String mCassIP = "localhost";
        protected static Properties props = new Properties();
        Cass7Funcs c7 = new Cass7Funcs();
        static LocalFuncs c8 = new LocalFuncs();
        
        static int nErrors = 0;

    
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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [SC.WebFuncs-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [SC.WebFuncs-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [SC.WebFuncs-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected static void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate+ " [DEBUG] [SC.webfuncs_" + threadID + "] " + s);
    }


    class MailProcessor implements Runnable {
    
        MailProcessor() {
            Thread t = new Thread(this, "MailProcessor");
            t.start(); // Start the thread   
        }
        
        public void run() {
            MailerFuncs mf = new MailerFuncs();
            mf.mailer_loop();
        }
}

class UpdateCheck implements Runnable {

    /* print to stdout */
    protected void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate+ " [DEBUG] [WebFuncsCS_" + threadID + "] " + s);
    }


    String sVersionRunning = "";
      boolean bWindows = true;
      
      UpdateCheck() {
            String IsWindows = NetUtils.getConfig("winserver", "config/www-server.properties");
            bWindows = Boolean.parseBoolean(IsWindows);
            Thread t = new Thread(this, "UpdateCheck");
            t.start(); // Start the thread
      }
      
      public void run() {
          while (true) {
              CheckForUpdate();
              try {
                  Thread.sleep(10000);
              } catch (Exception e) {
                  e.printStackTrace();
              }
         }
      }
      
      void CheckForUpdate() {
        try {
            String sFile = "../../update.old";
            if (!bWindows) sFile = "../../update.last";
            p("windows = " + bWindows);
            p("sfile = " + sFile);
            File f = new File(sFile);
            if (f.exists()) {
                String sVersionFile = NetUtils.readFileIntoString(f.getCanonicalPath()).trim();                
                p("Installed version: '" + sVersionFile + "'");            
                if (sVersionRunning.equals("")) {
                    sVersionRunning = sVersionFile;
                } else {
                    p("Running version: '" + sVersionFile + "'");            
                    if (sVersionRunning.equals(sVersionFile)) {
                        p("Same version running / installed.");                       
                    } else {
                        p("New version is available: " + sVersionFile);
                        p("Init Shutdown...");
                        Shutdown(0);
                    }
                }
                File f2 = new File("../../shutdown");
                if (f2.exists()) {
                    p("forced shutdown detected");
                    Shutdown(1);
                } else {
                    p("shutdown not detected.");
                }                    
            } else {
                pw("WARNING: file not found:" + f.getCanonicalPath());
            }                
        } catch (Exception e) {
            pw("Warning: There was an exception checking update.");
            e.printStackTrace();
        }
    }

    void Shutdown(int returncode) {
        try {
            p("*** SHUTDOWN DETECTED ***");
            closeDB();       
            System.exit(returncode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

  }
        
  static class BListLoader implements Runnable {
    
    public static int refcount = 0;
    
    String mLocalIP = "";
    String b1 = "";
    String b2 = "";
    
    BListLoader(String _localIP, String _b1, String _b2) {
        try {
            p("new BListLoader()");
            mLocalIP = _localIP;
            b1 = _b1;
            b2 = _b2;

            p("refcount = " + refcount);
            if (refcount == 0) {
            } else {
                while (refcount > 0) {
                    p("waiting for completion...");
                    Thread.sleep(500);
                }
            }

            Thread t = new Thread(this, "BListLoader");
            t.start(); // Start the thread
        } catch (Exception e) {
            
        }
    }
    
    public void run() {     
        try {      
            refcount++;
            Stopwatch tt = new Stopwatch();
            tt.start();
            p("b1 length = " + b1.length());
            p("b2 length = " + b2.length());
            
            int nres = -1;
            int nTries = 0;
            while (nres < 0 && nTries <= 5) {
                nres = c8.loadNumberOfCopies(mLocalIP, false, true, b1, b2);                
                p("nres loadnumberofcopies = " + nres);
                if (nres < 0) {
                    pw("WARNING: There was an ERROR loading number. Retry after 10s...");
                    nTries++;
                    Thread.sleep(10000);
                }
            }
            
            //c8.UpdateBlacklistedFiles(mLocalIP);       
            tt.stop();
            p("**** launchBL took " + tt.getElapsedTime() + "ms");
            refcount--;
        } catch (Exception e) {
            e.printStackTrace();
        }

    }        
  }
  
  class CopyLoader implements Runnable {
    
    String mLocalIP = "";
    int res = 0; 
    boolean skipopen = false;
        
    CopyLoader(String _localIP) {
        p("new CopyLoader()");
        mLocalIP = _localIP;
        
        Thread t = new Thread(this, "CopyLoader");
        //p("Child thread: " + t);
        t.setUncaughtExceptionHandler(h);
        t.setPriority(Thread.NORM_PRIORITY);
        t.start(); // Start the thread
    }
    
Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
    public void uncaughtException(Thread th, Throwable ex) {
        p("UNCAUGHT EXCEPTION ERROR!!! Closing DB and Shutting down RT.");
        ex.printStackTrace();
        p("closedb");
        c8.closeMapDB();  
        System.exit(-1);
    }
};


      public void run() {
        try {
            p("copyloader run");
            //wf = new utils.WebFuncs(mLocalIP); 
            int nres = -1;
            try {
                if (!skipopen) {
                    nres = c8.loadIndexMapDB(false,true);                    
                    p("load nres: " + nres);
                } else {
                    p("skip open. nres=1");
                    nres = 1;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (nres < 0) {
                pw("WARNING: There was an error loading MapDB. Waiting 5s.");
            } else {
                if (nres == 2) {
                    //new db was created, reindex
                    //c8.loadNumberOfCopies_File(mLocalIP, false, false);     ///forceload=false, interactive=false 
                    //c8.loadNumberOfCopies(mLocalIP, false, false);  
                    
                    String batchid = c8.get_batch_id("batchid","BatchJobs", "idx");
                    if (batchid.length() > 0) {
                        int lastbatch = Integer.parseInt(batchid);
                        p("last batch = " + lastbatch);
                        Thread.sleep(2000);
                        for (int i=1;i<=lastbatch;i++) {
                            String _batchid = String.valueOf(i);
                            File fh = new File("../rtserver/batch_" + _batchid + ".idx");
                            FileWriter fw = new FileWriter(fh, true);
                            fw.write("done");
                            fw.close();

                            //nres = c8.update_occurences_copies(String.valueOf(i));    
                            //nres = c8.update_occurences_copies("1");
                        }                        
                    }

                } else {
                    //index exists, skip load
                    //c8.loadNumberOfCopies(mLocalIP, false, false);     ///forceload=false, interactive=false 
                    //c8.loadNumberOfCopies_File(mLocalIP, false, false);     ///forceload=false, interactive=false 
                }                
            }
            c8.loadHiddenFiles();        
            //c8.loadIndex();     
            
            while (true) {
                CheckforIDX();
                CheckforCommit();
                Thread.sleep(5000);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            p("here2!!!!");
        }             
    }    
}
  
  int CheckforCommit() {
      try {          
          int nres = c8.check_commit(mapBatches);
          p("res check_commit:" + nres);
          if (nres < 0) {
            pw("WARNING: There was an ERROR in the commit. Shutting down RT.");
            pw("closedb");
            c8.closeMapDB();  
            System.exit(-1);  
            return -1;
          } else {
            return 0;              
          }
        
      } catch (Exception e) {
          e.printStackTrace();          
          return -1;
      }
  }
  void CheckforIDX() {
      try {
          File tf = new File("../rtserver/");

            if (tf.exists()) {
                File[] files = tf.listFiles();
                for (File f:files) {
                    //p(f.getName());
                    if (f.getName().contains(".idx")) {
                        p("checking for commit first.");
                        CheckforCommit();
                        
                        String sValue = null;
                        synchronized (mapBatches) {
                            sValue = (String)mapBatches.get(f.getCanonicalPath());
                        }
                        if (sValue==null) {
                            String sBatchID = f.getName().substring(f.getName().indexOf("_")+1,f.getName().indexOf("."));
                            p("time to process batch:" + sBatchID);
                            int nres = c8.update_occurences_copies(sBatchID);
                            p("res occurences_copies:" + nres);  
                            if (nres > 0) {                            
                                p("Processed Batch #" + sBatchID + " OK. Adding to map: " + f.getCanonicalPath());
                                synchronized (mapBatches) {
                                    mapBatches.put(f.getCanonicalPath(), "");
                                }                           
                                //boolean res = f.delete();
                                //p("res delete:" + res);
                            } else {
                                pw("WARNING BATCH #" + nErrors + ": There was an error processing batch: " + sBatchID);
                                String sNewFileName = f.getName();
                                sNewFileName = sNewFileName.replace(".idx", ".bad");
                                p("Renaming to : " + sNewFileName);
                                boolean bres = f.renameTo(new File(sNewFileName));
                                p("Rename res = " + bres);
                                nErrors++;
                                if (nErrors > 5) {
                                    pw("WARNING: Too many errors. Exiting RT.");
                                    c8.closeMapDB();
                                    System.exit(-1);
                                }
                            }                                                
                            Thread.sleep(5000);
                        } else {
                            p("Skipping IDX. Already processed: " + f.getCanonicalPath());
                        }                                                 
                    }
                }
            }         
      } catch (Exception e) {
          e.printStackTrace();
      }
                            
  }    

  
        public void closeDB() {
            int nres = c8.closeMapDB();
            p("res close: " + nres);
        }
    
        public WebFuncs(String _ServerIP) {
            try {
                if(!"".equals(_ServerIP)) {
                    p("WebFuncs constructor. Copyloader.");
                    Thread.sleep(3000);
                    Stopwatch tt = new Stopwatch();
                    tt.start();
                    CopyLoader cl = new CopyLoader(_ServerIP);
                    tt.stop();
                    p("loadNumberOfCopies took " + tt.getElapsedTime() + "ms");
                    
                    UpdateCheck uc = new UpdateCheck();                   
                    MailProcessor mp = new MailProcessor();
                    
                } else {                
                    //p("WebFuncs constructor. Skipped Copyloader.");
                    //Thread.sleep(3000);
                }                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        public int launchBL(String _ServerIP, String _b1, String _b2) {
            BListLoader bll = new BListLoader(_ServerIP, _b1, _b2);
            return 1;
        }

        public static void main(String[] args) {
            //p("hello!!!!");
            //connectCassandra();
            //String str = getp("7791B34496CE2235071A086EA84ABE4E","paths");
            //String str = echoh2("hello");
            int nres = 1;

//            nres = WebFuncs.insert_node_attribute("0ce78da1-fe24-4875-9ff0-476a6ef4883d","ipaddress","192.168.1.4");
//            p(nres);
//            nres = WebFuncs.insert_node("0ce78da1-fe24-4875-9ff0-476a6ef4883d");
//            p(nres);
//
//            nres = WebFuncs.insert_node_attribute("9e8b5dc1-1e9f-4883-83ee-e2e0f772b19c","ipaddress","192.168.1.19");
//            p(nres);
//            nres = WebFuncs.insert_node("9e8b5dc1-1e9f-4883-83ee-e2e0f772b19c");
//            p(nres);
//
//            nres = WebFuncs.insert_node_attribute("98bf3e62-e23d-4754-af80-6d09a79e8817","ipaddress","192.168.1.9");
//            p(nres);
//            nres = WebFuncs.insert_node("98bf3e62-e23d-4754-af80-6d09a79e8817");
//            p(nres);
//
//            String res = WebFuncs.get_node_attribute("98bf3e62-e23d-4754-af80-6d09a79e8817","ipaddress");
//            p(res);

              String _port = "8080";
              String _address = "192.168.56.1";
              boolean avail;
              avail = isNodeAvailable(_address,_port);
              p("isNodeavail = " + avail);
    }

public String getTagsForFile(String sMD5, String _user){
    return c8.getTagsForFile(sMD5, _user);
}

        
public String getFileGroup(String sFileName){
    String sFileGroup = "";
    if (c7.is_music(sFileName)) sFileGroup = "music";
    if (c7.is_movie(sFileName)) sFileGroup = "movie";
    if (c7.is_document(sFileName) || c7.is_office(sFileName)) sFileGroup = "document";
    if (c7.is_photo(sFileName)) sFileGroup = "photo";
    if (c7.is_textfile(sFileName)) sFileGroup = "textfile";
    if (sFileGroup.length() == 0) {
         sFileGroup = "other";                        
    }                    

    return sFileGroup;
}        
        
public String getThumbnails(String sMD5, String THUMBNAIL_OUTPUT_DIR) {
    String sThumbnails="";
    try {
        p("THUMBNAIL DIR = " + THUMBNAIL_OUTPUT_DIR);
        File fh64= new File(THUMBNAIL_OUTPUT_DIR, sMD5.toLowerCase() + ".alt64");
        p("THUMBNAIL DIR2 = " + fh64.getCanonicalPath());
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
            sThumbnails=out.toString();      
            is.close();
            out.close();
        }
    }catch(Exception ex){
        ex.printStackTrace();
    }
  
    return sThumbnails;
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
        } catch (Exception ex) {
            Logger.getLogger(Cass7Funcs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }


static void loadProps() throws IOException {
        //p(System.getProperty("java.home"));
        File f = new File
                (System.getProperty("java.home")+File.separator+
                    "lib"+File.separator+"www-cassandra.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
            String r = props.getProperty("server");
            if (r != null) {
                p("using server = " + r);
                mCassIP = r;
            }
        }

    }

    public String echo(String _key, String _numobj) {
        try {
            String _filetype = "";
            String _numrow = "";
            String _password = "";
            loadProps();
            //Cass7Funcs.connect(mCassIP,9160);
            c7.connect(mCassIP,9160);
            return c7.read_row_list(_key,1, "", _numobj, _filetype, _numrow, _password, "", "", "", "", "", false, "");

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

    public int insert_hidden(String _key, String _hiddenpw) {
        c8.insert_hidden(_key, _hiddenpw);
        return 0;
    }
    
    public int remove_hidden(String _key) {
        c8.remove_hidden(_key);
        return 0;
    }
    
    public String get_file_path(String _key) {
        return c8.get_file_path(_key, true);
    }
    
    public String get_file_path_decoded(String _key) {
        return c8.get_file_path(_key, false);
    }
    
    public String echoh(String _key, String _numobj) {
        try {
            String _filetype = "";
            String _numrow = "";
            String _password = "";
            loadProps();
            //Cass7Funcs.connect(mCassIP,9160);
            c7.connect(mCassIP,9160);
            return c7.read_row_list(_key,2, "", _numobj, _filetype, _numrow, _password, "", "", "", "", "", false, "");

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public int insert_node(String _uuid) {
        try {
            loadProps();
            //Cass7Funcs.connect(mCassIP,9160);
            c7.connect(mCassIP,9160);
            return c7.insert_column("Keyspace1b","NodeInfo","Node",_uuid,_uuid);

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return -1;
    }

public int delete_column(String _keyspace, String _column_family, String _key, String _column_name, String _dbmode){
        try {
            loadProps();
            
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                c7.connect(mCassIP,9160);
                return c7.delete_column(_keyspace,_column_family,_key,_column_name);
            } else {
                return c8.delete_column(_keyspace,_column_family,_key,_column_name, "");
            }
            
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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return -1;
}

public String get_row_attribute(String _keyspace, String _columnfamily, String _uuid, String _name, String _dbmode) {
        try {
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                loadProps();
                //Cass7Funcs.connect(mCassIP,9160);
                c7.connect(mCassIP,9160);
                return c7.get_row_attribute(_keyspace,_columnfamily,_uuid,_name);
            } else {
                return c8.get_row_attribute(_keyspace,_columnfamily,_uuid,_name, null);
            }
                
            

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String get_node_attribute(String _uuid, String _name) {
        try {
            loadProps();
            //Cass7Funcs.connect(mCassIP,9160);
            c7.connect(mCassIP,9160);
            return c7.get_row_attribute("Keyspace1b","NodeInfo",_uuid,_name);

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public int insert_name_value(String _keyspace, String _columnfamily, String _key, String _name, String _value, String _dbmode) {
        try {
            loadProps();
            
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                boolean bres = c7.connect(mCassIP,9160);
                p("insert_node_attribute.connect res:" + bres);
                if (bres) {
                    int ires = c7.insert_column(_keyspace,_columnfamily,_key,_name,_value);
                    c7.close();
                    return ires;
                } else {
                    return -1;
                }
            } else {
                return c8.insert_column(_keyspace,_columnfamily,_key,_name,_value, true);
            }
            
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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return -1;    
}
public int insert_node_attribute(String _uuid, String _name, String _value, String _dbmode, boolean _append) {
        try {
            
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                loadProps();
                //Cass7Funcs.connect(mCassIP,9160);
                boolean bres = c7.connect(mCassIP,9160);
                //p("insert_node_attribute.connect res:" + bres);
                if (bres) {
                    int ires = c7.insert_column("Keyspace1b","NodeInfo",_uuid,_name,_value);
                    c7.close();
                    return ires;
                } else {
                    return -1;
                }                
            } else {
                int ires = c8.insert_column("Keyspace1b","NodeInfo",_uuid,_name,_value, _append);
            }

                            

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return -1;
    }

public String insertac(String _key, String _name, String _value) {
        try {
            loadProps();
            //Cass7Funcs.connect(mCassIP,9160);
            c7.connect(mCassIP,9160);
            return c7.insert_column_auto_complete("Keyspace1b",_key,_name,_value);

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }


public String echoac(String _key, String _dbmode, String _user) {
        try {
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                loadProps();
                //Cass7Funcs.connect(mCassIP,9160);
                c7.connect(mCassIP,9160);
                return c7.read_row_autocomplete(_key);                
            } else {                
                return c8.read_row_autocomplete_mem(_key, _user);                
            }

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public  String echoh2(boolean bButtonModelNew,String _key, 
            String _root, 
            String _numobj, 
            String _filetype, 
            String _numrow, 
            String _password, 
            String _daysback, 
            String _datestart,
            String _host,
            String _port,
            Boolean _cloudhosted,
            Boolean _awshosted,
            String _clientIP,
            String _user,
            boolean _writeLog,
            boolean _base64pic,
            String _screenSize) {
        try {
            loadProps();
            String res ="";
            if(bButtonModelNew) {
                res=c7.read_row_list2(_key, 
                                4, 
                                _root, 
                                _numobj, 
                                _filetype, 
                                _numrow, 
                                _password, "", 
                                _daysback, 
                                _datestart, 
                                _host, 
                                _port, 
                                _cloudhosted,
                                _awshosted,
                                _clientIP,
                                _user,
                                _writeLog,
                                _base64pic,
                                _screenSize,
                                true);

            }else {
                c7.connect(mCassIP,9160);
                res=c7.read_row_list(_key, 
                                4, 
                                _root, 
                                _numobj, 
                                _filetype, 
                                _numrow, 
                                _password, "", 
                                _daysback, 
                                _datestart, 
                                _host, 
                                _port, 
                                _cloudhosted,
                                _clientIP);
            }
            return res;

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

  public String echoSendh2(String sNamer,String sFileName,String _key,String _host,String _port,Boolean _cloudhosted,String _clientIP) {
       try {
            loadProps();
            c7.connect(mCassIP,9160);

           
            String res = c7.read_send_link(sNamer,sFileName,_key,_host, _port, _cloudhosted,_clientIP);
            
            return res;

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String echoOpenFolderh2(String sNamer,String sFileName,String _key,String _host,String _port,Boolean _cloudhosted,String _clientIP, String _dbmode) {
         try {
            String res = "";
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                loadProps();
                c7.connect(mCassIP,9160);           
                res = c7.read_openFolder_link(sNamer,sFileName,_key,_host, _port, _cloudhosted,_clientIP);                            
            } else {
                //peer to peer
                res = c8.read_openFolder_link(sNamer,sFileName,_key,_host, _port, _cloudhosted,_clientIP);                
            }
            return res;

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }catch(Throwable th){
            th.printStackTrace();
        }
        return "ERROR";
}

  
public  String openfile_mobile(String sNamer,String sFileName,String _key,String _host,String _port,Boolean _cloudhosted,String _clientIP, String _dbmode) {
        try {
            String res = "";
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                loadProps();
                c7.connect(mCassIP,9160);           
                res = c7.read_open_link(sNamer,sFileName,_key,_host, _port, _cloudhosted,_clientIP);                            
            } else {
                //peer to peer
                //res = c8.read_open_link(sNamer,sFileName,_key,_host, _port, _cloudhosted,_clientIP);    
                
                sURLPack up = new sURLPack();
                String sOpenURL = "";
                up = c8.get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, false, true, _host); 
                res = up.sRemoteURLJSON;                
                p("REMOTE JSON URL = " + res);
            }
            return res;

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }catch(Throwable th){
            th.printStackTrace();
        }
        return "ERROR";
    }

public  String echoOpenh2(String sNamer,String sFileName,String _key,String _host,String _port,Boolean _cloudhosted,String _clientIP, String _dbmode) {
        try {
            String res = "";
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                loadProps();
                c7.connect(mCassIP,9160);           
                res = c7.read_open_link(sNamer,sFileName,_key,_host, _port, _cloudhosted,_clientIP);                            
            } else {
                //peer to peer
                sURLPack up = new sURLPack();
                String sOpenURL = "";
                
                up = c8.get_remote_link2(sNamer,"paths", true, _cloudhosted, _clientIP, false, true, _host);    
                //res = c8.read_open_link(sNamer,sFileName,_key,_host, _port, _cloudhosted,_clientIP);                
                res = up.sOpenURL;
            }
            return res;

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }catch(Throwable th){
            th.printStackTrace();
        }
        return "ERROR";
    }

public String getlocalfilepath(String sNamer) {
    String res = c8.get_file_path(sNamer, false);
    return res;    
}

public String getfile_mobile(String sNamer, String sFileName, String _key, String _host, String _port, Boolean _cloudhosted, String _clientIP, String _dbmode) {
    String res = "";
    if (_dbmode.equals("cass") || _dbmode.equals("both")) {        
        res = c7.read_view_link(sNamer,sFileName,_key,_host, _port, _cloudhosted,_clientIP);    
    } else {
        String[] res2 = new String[2];
        res2 = c8.read_view_link(sNamer, true, false);
        res = res2[0];
    }  
    return res;
}

public  String echoViewh2(String sNamer, String sFileName, String _key, String _host, String _port, Boolean _cloudhosted, String _clientIP, String _dbmode) {
        try {
            
            String res = "";
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                loadProps();
                c7.connect(mCassIP,9160);           
                res = c7.read_view_link(sNamer,sFileName,_key,_host, _port, _cloudhosted,_clientIP);                            
            } else {
                //peer to peer
                String res2[] = new String[2];
                res2 = c8.read_view_link(sNamer, false, false); 
                res = res2[0];
            }
            return res;

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String echoh2p(String _key, String _root, String _numobj, String _password) {
        try {
            String _filetype = "";
            String _numrow = "";
            loadProps();
            c7.connect(mCassIP,9160);
            String res = c7.read_row_list(_key,6, _root, _numobj, _filetype, _numrow, _password, "", "", "", "", "", false, "");
            return res;

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String echoh2m(String _key, 
            String _root, 
            String _numobj, 
            String _filetype, 
            String _numrow, 
            String _password, 
            String _daysback, 
            String _datestart, 
            String _host, 
            String _port, 
            Boolean _cloudhosted,
            Boolean _awshosted,
            String _clientIP,
            String  _dbmode,
            String _user,
            boolean _writeLog,
            boolean _base64pic,
            String _screenSize) {
        try {
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                loadProps();
                c7.connect(mCassIP,9160);
                String res = c7.read_row_list2(_key,
                        9, 
                        _root, 
                        _numobj, 
                        _filetype, 
                        _numrow, 
                        _password, 
                        "", 
                        _daysback, 
                        _datestart, 
                        _host, 
                        _port, 
                        _cloudhosted,
                        _awshosted,
                        _clientIP,
                        _user,
                        _writeLog,
                        _base64pic,
                        _screenSize,
                        true);
                return res;                
            } else {
                //c7.connect(mCassIP,9160);
                String res = c7.read_row_list2(_key,
                        9, 
                        _root, 
                        _numobj, 
                        _filetype, 
                        _numrow, 
                        _password, 
                        "", 
                        _daysback, 
                        _datestart, 
                        _host, 
                        _port, 
                        _cloudhosted,
                        _awshosted,
                        _clientIP,
                        _user,
                        _writeLog,
                        _base64pic,
                        _screenSize,
                        true);
                return res;                
            }

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String echoh2mobileac(String _key,
            String _dbmode, 
            String _clientIP,
            String _filetype,
            String _numobj,
            String _host, 
            String _port,
            String _daysback,
            Boolean _cloudhosted, 
            Boolean _awshosted,
            String _user
        ) {
        try {   
            return c8.read_row_autocomplete_mobile(_key, _clientIP, _filetype, _numobj, _host, _port, _daysback, _cloudhosted, _awshosted, _user);                          
        } catch (Exception ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String echoh2mobile(String _key, 
            String _root, 
            String _numobj, 
            String _filetype, 
            String _numrow, 
            String _password, 
            String _daysback, 
            String _datestart, 
            String _host, 
            String _port, 
            Boolean _cloudhosted,
            Boolean _awshosted,
            String _clientIP,
            String _dbmode,
            String _user,
            boolean _writeLog,
            boolean _base64pic,
            int _mode,
            String _screenSize,
            boolean _bOrderAsc
        ) {
        try {
            
            loadProps();
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                c7.connect(mCassIP,9160);
            }
            String res = c7.read_row_list2(_key,
                                    _mode, 
                                    _root, 
                                    _numobj, 
                                    _filetype, 
                                    _numrow, 
                                    _password, 
                                    "", 
                                    _daysback, 
                                    _datestart, 
                                    _host, 
                                    _port, 
                                    _cloudhosted,
                                    _awshosted,
                                    _clientIP,
                                    _user,
                                    _writeLog,
                                    _base64pic,
                                    _screenSize,
                                    _bOrderAsc);  
            return res;

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String echoh2l(String _key, 
            String _root, 
            String _numobj, 
            String _filetype, 
            String _numrow, 
            String _password, 
            String _daysback, 
            String _datestart, 
            String _host, 
            String _port, 
            Boolean _cloudhosted,
            Boolean _awshosted,
            String _clientIP,
            String _dbmode,
            String _user,
            boolean _writeLog,
            boolean _base64pic,
            String _screenSize
        ) {
        try {
            
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                loadProps();
                c7.connect(mCassIP,9160);
                String res = c7.read_row_list(_key,
                        7, 
                        _root, 
                        _numobj, 
                        _filetype, 
                        _numrow, 
                        _password, 
                        "", 
                        _daysback, 
                        _datestart, 
                        _host, 
                        _port, 
                        _cloudhosted,
                        _clientIP);
                return res;                
            } else {
                String res = c7.read_row_list2(_key,
                                        7, 
                                        _root, 
                                        _numobj, 
                                        _filetype, 
                                        _numrow, 
                                        _password, 
                                        "", 
                                        _daysback, 
                                        _datestart, 
                                        _host, 
                                        _port, 
                                        _cloudhosted,
                                        _awshosted,
                                        _clientIP,
                                        _user,
                                        _writeLog,
                                        _base64pic,
                                        _screenSize,
                                        true);  
                return res;
            }


//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String echoh2s(String _key, String _root, String _numobj, String _password, String _pid, String _user) {
        try {
            String _filetype = "";
            String _numrow = "";
            
            loadProps();
            c7.connect(mCassIP,9160);
            String res = c7.read_row_list(_key, 1, _root, _numobj, _filetype, _numrow, _password, _pid, "", "", "", "", false, "");
            return res;

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String echoh2s2(String _key, String _root, String _numobj, String _password, String _pid, String _daysback, String _dbmode, String _user, String _screenSize) {
        try {
            
            String _filetype = ".photo";
            String _numrow = "";
            loadProps();
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                c7.connect(mCassIP,9160);
                String res = c7.read_row_list(_key, 8, _root, _numobj, _filetype, _numrow, _password, _pid, _daysback, "", "", "", false, "");
                return res;                
            } else {
                String res = c7.read_row_list2(_key, 8, _root, _numobj, _filetype, _numrow, _password, _pid, _daysback, "", "", "", false, false, "",_user, false, false,_screenSize, true);
                return res;                                
            }                                    

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String echoh2c(String _key, String _root, String _numobj, String _password) {
        try {
            String _filetype = "";
            String _numrow = "";
            
            loadProps();
            c7.connect(mCassIP,9160);
            String res = c7.read_row_list(_key,5, _root, _numobj, _filetype, _numrow, _password, "", "", "", "", "", false, "");
            return res;

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String getbatches(String _key, String _password, String _View, String _FileType, String _DaysBack, String _NumCol, String _NumObj, String _dbmode, String _screenSize) {
        try {
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                loadProps();
                boolean bConnect = c7.connect(mCassIP,9160);
                if (bConnect) {
                    String res = c7.get_batches(_key, _password, _View, _FileType, _DaysBack, _NumCol, _NumObj, _screenSize);
                    return res;
                } else {
                    return "ERROR_CONNECT";
                }                
            } else {
                //p2p mode               
                String res = c8.get_batches(_key, _password, _View, _FileType, _DaysBack, _NumCol, _NumObj);
                return res;
            }

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String printbatch(String _key) {
        try {
            loadProps();
            c7.connect(mCassIP,9160);
            String res = c7.read_row_info(_key, "BatchJobs");
            return res;

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

        /**
     * Connect to Cassandra Server
     */
public void connectCassandra() {
        try {
            loadProps();
            //Cass7Funcs.connect(mCassIP,9160);
            c7.connect(mCassIP,9160);
        } catch (UnsupportedEncodingException ex) {
            //Log.logException(ex);
        } catch (InvalidRequestException ex) {
            //Log.logException(ex);
        } catch (UnavailableException ex) {
            //Log.logException(ex);
        } catch (TimedOutException ex) {
            //Log.logException(ex);
        } catch (TException ex) {
            //Log.logException(ex);
        } catch (NotFoundException ex) {
            //Log.logException(ex);
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

public int disconnectCassandra() {
        try {
            int res = c7.close();
            return res;
        } catch (Exception ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
            return -1;
        }
    }

public int getBatchId(String _key, String _dbmode) {
    String batchid = "0";
    if (_dbmode.equals("cass") || _dbmode.equals("both")) {
        batchid = c7.get_batch_id("batchid","BatchJobs", "idx");
    }
    else {
        batchid = c8.get_batch_id("batchid","BatchJobs", "idx");
    }
    return Integer.parseInt(batchid);
}

public String echobackup(String _key, String _dbmode) {
        try {
            String batchid = "";
            String backupid = "";
            String seqid = "";
            if (_dbmode.equals("cass") || _dbmode.equals("both")) {
                //p("[1]");
                connectCassandra();
                //p("[2]");
                batchid = c7.get_batch_id("batchid","BatchJobs", "idx");
                backupid = c7.get_batch_id("backupid","BackupJobs", "idx2");
                seqid = c7.get_batch_id("backupid","BackupJobs", "idx3");
            } else {
                batchid = c8.get_batch_id("batchid","BatchJobs", "idx");
                backupid = c8.get_batch_id("backupid2","BackupJobs", "idx2");
                seqid = c8.get_batch_id("backupid3","BackupJobs", "idx3");               
            }
            
            if (seqid.length() == 0) {
                seqid = "0";
            }
            
            if (batchid.length() == 0) {
                batchid = "0";
            }
            
            if (backupid.length() == 0) {
                backupid = "0";
            }
            
            //p("last id batch stored : " + batchid);
            //p("last backup id batch stored : " + backupid);
            //p("last sequence stored : " + seqid);
            
            String res = seqid + "," + batchid + "," + backupid;
            return res;
            
//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();
        } catch (Exception ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }



public ArrayList<Node> getNodes(String _key, String _dbmode){
    ArrayList<Node> nodes = new ArrayList<Node>(); 

    if (_dbmode.equals("cass")) {
        connectCassandra();
        nodes = c7.getNodes(_key, "NodeInfo");
    } else {
        nodes = c8.getNodes(_key, "NodeInfo");
    }

    return nodes;
}

// HTML generator for "nodestats.htm" request
public String echonode(String _key, String _dbmode) {
        try {
            if (_dbmode.equals("cass")) {
                //p("[1]");
                connectCassandra();
                //p("[2]");
                String res = c7.get_node_info(_key, "NodeInfo");
                //p("[3]");
                return res;
            } else {
                //p2p
                String res = c8.get_node_info(_key, "NodeInfo");
                p("[3]: '" + res + "'");
                return res;
            }

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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


public String echoh3(String _key, String _dbmode) {
        try {
            if (_dbmode.equals("cass")) {

                p("[1]");
                connectCassandra();
                p("[2]");
                String res = c7.read_row_info(_key, "Standard1");
                p("[3]");
                return res;
            } else {
                //P2P
                String res = c8.read_row_info(_key, "Standard1");
                p("[3]");
                return res;
            }

//            StringBuffer buf = new StringBuffer();
//            for (int i = 0; i < args.length; i++) {
//                buf.append(args[i]);
//                buf.append("\n");
//            }
//            buf.append("KAKA");
//            return buf.toString();

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

public String insert_hash(String _keyspace, String _key, String _hashkey, boolean _autocomplete, String _datemodified, String _dbmode) {
        try {
            
            String ret = "";
            if (_dbmode.equals("cass")) {
                loadProps();
                //Cass7Funcs.connect(mCassIP,9160);
                c7.connect(mCassIP,9160);

                ret = c7.insert_hashtag(_keyspace, _key, _hashkey, _autocomplete, _datemodified);
            } else {
                //P2P
                ret = c8.insert_hashtag(_keyspace, _key, _hashkey, _autocomplete, _datemodified);                
            }
            
            return ret;
            
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
        
    }

public String delete_hash(String _keyspace, String _key, String _hashkey, String _datemodified, String _dbmode) {
    try {
        String ret = "";
        if (_dbmode.equals("cass")) {
            ret = delete_hash_cass(_keyspace, _key, _hashkey, _datemodified);
        } else {
            //P2P
            ret = delete_hash_P2P(_keyspace, _key, _hashkey, _datemodified);
        }

        return ret;

    } catch (Exception e) {
        e.printStackTrace();
        return "ERROR";
    }
}

public String delete_hash_cass(String _keyspace, String _key, String _hashkey, String _datemodified) {
        try {
            loadProps();
            int res_code = 0;

            c7.connect(mCassIP,9160);

            //Delete entire tag text
            res_code = c7.deleteSuperColumn(_keyspace, "Super2" ,_key, "hashesm", _hashkey);
            res_code += c7.delete_column(_keyspace, "Standard1", _hashkey, _datemodified);
            
            Integer cant = c7.getHashCount(_hashkey);
            boolean bDeleteAutcomplete = (cant == 0);
                        
            for (int idx=1; idx<_hashkey.length();idx++) {
                for (int idx2=0; idx2<=_hashkey.length()-1;idx2++) {
                    if ((idx + idx2) <= _hashkey.length()) {
                        String str = _hashkey.substring(idx2, idx + idx2);

                        if (bDeleteAutcomplete) {
                            c7.delete_column_auto_complete("Keyspace1b", str, _hashkey);
                        }
                        c7.delete_column(_keyspace,"Standard1", str, _datemodified);
                    }
                }
            }
            
            
            //Delete tag text substrings (delimited by "+ ")
            StringTokenizer st = new StringTokenizer(_hashkey, "", false);
            while (st.hasMoreTokens()) {
                String w = st.nextToken();
                p("Deleting hashkey '" + w + "'");
                
                //delete manual hashes
                res_code = c7.deleteSuperColumn(_keyspace, "Super2" ,_key, "hashesm", w);
                p("RET: '" + res_code);

                res_code += c7.delete_column(_keyspace, "Standard1", w, _datemodified);
                p("RET2: '" + res_code);
                

                //this is to know if exist some object with the Tag, if not, delete all the substrings
                cant = c7.getHashCount(w);
                bDeleteAutcomplete = (cant == 0);
                
                for (int idx=1; idx<w.length()+1;idx++) {
                    String str3 = w.substring(0, idx);
                    p("str3: '" + str3 + "'");

                    if (bDeleteAutcomplete) {
                        //delete substring in autocomplete table (e.g. key: 'P' name/value: 'Pictures')
                        c7.delete_column_auto_complete("Keyspace1b", str3, w);
                    }
                    //also delete associate hash to subtring (e.g. key:'P'  name/value:file1_hash
                    c7.delete_column(_keyspace,"Standard1", str3, _datemodified);
                }
                for (int idx=1; idx<_hashkey.length();idx++) {
                    for (int idx2=0; idx2<=_hashkey.length()-1;idx2++) {
                        if ((idx + idx2) <= _hashkey.length()) {
                            String str = _hashkey.substring(idx2, idx + idx2);

                            if (bDeleteAutcomplete) {
                                //delete substring in autocomplete table (e.g. key: 'P' name/value: 'Pictures')
                                c7.delete_column_auto_complete("Keyspace1b", str, w);
                            }
                            //also delete associate hash to subtring (e.g. key:'P'  name/value:file1_hash
                            c7.delete_column(_keyspace,"Standard1", str, _datemodified);
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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String delete_hash_P2P(String _keyspace, String _key, String _hashkey, String _datemodified) {
        try {
            loadProps();

            _hashkey = _hashkey.replace(":", "@");
            
            int res_code;
            
            String sFilename = c8.get_row_attribute("", "Standard1", _key, "name", null).toLowerCase();
            String sAdder = _key + "," + sFilename;

            //Delete entire tag text
            res_code = c8.delete_SuperColumn(_keyspace, "Super2" ,_key, "hashesm", _hashkey, _hashkey);
            res_code += c8.delete_column(_keyspace,"Standard1",_hashkey, _datemodified, sAdder);
            
            boolean bDeleteAutcomplete = c8.existsHashes(_keyspace,"Standard1",_hashkey);

            for (int idx=1; idx<_hashkey.length();idx++) {
                for (int idx2=0; idx2<=_hashkey.length()-1;idx2++) {
                    if ((idx + idx2) <= _hashkey.length()) {
                        String str = _hashkey.substring(idx2, idx + idx2);

                        if (bDeleteAutcomplete) {
                            c8.delete_column_auto_complete("Keyspace1b", str, _hashkey, _hashkey);
                        }
                        c8.delete_column(_keyspace,"Standard1", str, _datemodified, sAdder);
                    }
                }
            }
            

            //Delete tag text substrings (delimited by "+ ")
            StringTokenizer st = new StringTokenizer(_hashkey, "+ ", false);
            while (st.hasMoreTokens()) {
                String w = st.nextToken();
                p("Deleting hashkey '" + w + "'");

                //delete manual hashes
                res_code = c8.delete_SuperColumn(_keyspace, "Super2" ,_key, "hashesm", w, w);
                p("RET: '" + res_code);
    
                res_code += c8.delete_column(_keyspace,"Standard1",w, _datemodified, sAdder);
                p("RET2: '" + res_code);
                

                //this is to know if exist some object with the Tag, if not, delete all the substrings
                bDeleteAutcomplete = c8.existsHashes(_keyspace,"Standard1",w);
                
                for (int idx=1; idx<_hashkey.length();idx++) {
                    for (int idx2=0; idx2<=_hashkey.length()-1;idx2++) {
                        if ((idx + idx2) <= _hashkey.length()) {
                            String str = _hashkey.substring(idx2, idx + idx2);

                            if (bDeleteAutcomplete) {
                                //delete substring in autocomplete table (e.g. key: 'P' name/value: 'Pictures')
                                c8.delete_column_auto_complete("Keyspace1b", str, w, w);
                            }
                            //also delete associate hash to subtring (e.g. key:'P'  name/value:file1_hash
                            c8.delete_column(_keyspace,"Standard1", str, _datemodified, sAdder);
                        }
                    }
                }
            }

            return String.valueOf(res_code);

        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }

public String geth(String _key, String _hashkey, String _password, String _clientip, String _viewmode, String _daysBack, String _numCol, String _numObj, String _filetype, String _dbmode) {
        try {
            if (_dbmode.equals("cass")) {
                loadProps();
                c7.connect(mCassIP,9160);            
                return c7.getSuperColumn(_key, _hashkey, 1, _password, _clientip, _viewmode, _daysBack, _numCol, _numObj, "", _filetype);
            } else {
                return c8.getSuperColumn(_key, _hashkey, 1, _password, _clientip, _viewmode, _daysBack, _numCol, _numObj, "", _filetype);                
            }

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }


public String getp(String _key, String _hashkey, String _password, String _clientip, String _dbmode) {
        try {
            if (_dbmode.equals("cass")) {
                loadProps();
                //Cass7Funcs.connect(mCassIP,9160);
                c7.connect(mCassIP,9160);
                return c7.getSuperColumn(_key, _hashkey, 2, _password, _clientip, "", "", "", "", "", "");                
            } else {
                return c8.getSuperColumn(_key, _hashkey, 2, _password, _clientip, "", "", "", "", "", "");                                
            }

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
        } catch (IOException ex) {
            Logger.getLogger(WebFuncs.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "ERROR";
    }
    
    public int remove_occurrences_copies(String _batchid) {
        try {
            return c8.update_occurences_copies(_batchid);
        } catch (Exception e) {
            return -1;
        }
    }
    
    public String getTagsLeftNavBar(String _currentUser, boolean _ismobile, boolean _iswebapp, boolean isAdmin) {
        try {
            return c8.getTagsLeftNavBar(_currentUser, _ismobile, _iswebapp,isAdmin);
        } catch (Exception e) {
            return "ERROR";
        }
    }
    
    public SortableValueMap<String, Integer> getTags(String _currentUser) {
        try {
            return c8.getTags(_currentUser);
        } catch (Exception e) {
            return null;
        }
    }
    
    public boolean is_photo(String _extension) {
        return c7.is_photo(_extension);
    }

    public boolean is_video(String _extension) {
        return c7.is_movie(_extension);
    }

    public boolean is_document(String _extension) {
        return c7.is_document(_extension);
    }

    public boolean is_office(String _extension) {
        return c7.is_office(_extension);
    }

    public boolean is_music(String _extension) {
        return c7.is_music(_extension);
    }

}

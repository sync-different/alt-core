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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;
import processor.DatabaseEntry;
import processor.RecordStats;
import java.util.Random;

import utils.HTTPRequestPoster;

import java.net.BindException;
import java.net.InetAddress;
import java.util.zip.ZipOutputStream;

import processor.ZipFolder;
import static services.ClientService.p;
import utils.NetUtils;

/**
 *
 * @author agf
 */
public class TransferService implements Runnable {
    protected Properties props = new Properties();
    private String mScanDirectory;
    String LOG_NAME = "transfer.txt";
    String LOG_PATH = "logs/";
    private Boolean mTerminated = false;
    PrintStream log= null;
    private Long mPeriodMs;
    String mHostName = "";
    String mHostPort = "";        
    String mSignature = "";
    String mUUIDPath = "";
    String mUUID = "";
    int mZipMax = 2 * 1024 * 1024; //max bytes default=2MB
    String mCONFIG_PATH = "";
    String mMode = ""; //machine mode (Server, Client)
    String mDBMode = ""; //machine mode (Server, Client)
    static private String mLocalPort;
    
    static boolean bHostFound = false;
    static int mLogLevel = 0;

    static boolean bConsole = true;

    /*
     * Scan processing directory
     */
    public TransferService(String _scan_directory, 
            String _hostname, 
            String _hostport, 
            Long _period_ms, 
            String _signature, 
            boolean _dothread, 
            boolean _hostfound, 
            String _configpath,
            int _loglevel) {
        //setName("Client Service");
        mScanDirectory = _scan_directory;
        mPeriodMs = _period_ms;
        mHostName = _hostname;
        mHostPort = _hostport;
        mSignature = _signature;
        bHostFound = _hostfound;
        mCONFIG_PATH = _configpath;
        mLogLevel = _loglevel;
        
        Thread t;

        try {
            
          loadBackupProps();
          String sLog = LOG_PATH + LOG_NAME;
          log = new PrintStream(new BufferedOutputStream(
                            new FileOutputStream(sLog,true)));
          
          p("log file = '" + sLog + "'");
          log("opening log file: " + sLog, 2);
      
        if (_dothread) {
            t = new Thread(this, "sc_t");
            System.out.println("Child thread: " + t);
            t.start(); // Start the thread            
        }
        
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    public void terminate() {
       
       log("Recieved Termination request.", 0);
       mTerminated = true;       
   }
    
   /**
     * Run Service
     */
    //@Override
    //@SuppressWarnings("SleepWhileHoldingLock")
    public void run() {
        try {
            while (!mTerminated) {
                
                p("transfer service run. hostfound = " + bHostFound);
                loadServerProps();
                loadBackupProps();
                mDBMode = getConfig("dbmode", "config/www-processor.properties");
                printBackupProps();                                
                
                mUUID =  NetUtils.getUUID(mUUIDPath);
                
                if (bHostFound) {
                        p("Server already Found = " + mHostName);                        
                } else {
                        mSignature = NetUtils.getSignature();
                        p("looking for signature: " + mSignature);
                        getServerAddressPort();                        
                        if (bHostFound) {
                            p("Server Found = " + mHostName);
                        }                        
                }
                
                //-Start scanning
                log("Started scanning directory: '" + mScanDirectory + "'", 2);
                RecordStats mStatistics = new RecordStats();
                ScanProcessingDir();                                                                                      
                
                //-Wait for next run
                Thread.sleep(mPeriodMs);
                
                //Run once only
                //mTerminated = true;
                    
                //set hostfound to false, in case the ServerIP address changed
                if (!mHostName.contains("amazon")) {
                    bHostFound = false;
                } else {
                    p("amazon mode... keeeping hostfound");
                }
                
            }
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        log("Transfer completed.", 2);
    }

    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_RESET = "\u001B[0m";

    protected static void pw(String s) {
        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_YELLOW + "[WARNING] [TransferService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + "[INFO] [TramsferService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + "[ERROR] [TransferService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected void p(String s) {

        long threadID = Thread.currentThread().getId();
        System.out.println("[transfer_" + threadID + "] " + s);
    }

    /* print to the log file */
    protected void log(String s, int _loglevel) {

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
                pi(sDate + " " + _loglevel + " " + s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }                   
    }
        
    private void ScanProcessingDir() {
        int process_files = 0;
        String sUUIDprocess = "";

        try { 
            loadProps();
            printProps();
        } catch (Exception IOException) {
            
        }
        
        log("Scanning directory: " + mScanDirectory, 2);
        File processing_dir = new File(mScanDirectory);
        if (processing_dir.exists()) {
            
            SimpleDateFormat DateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            String destZipDate = DateFormat.format(Calendar.getInstance().getTime());
            
            String destZipFile = "";
            
            long lBytesZipped = 0;
            Integer nFilesZipped = 0; 
            
            try {
                File ppp = new File(mScanDirectory);
                p("CanonicalPath: '" + ppp.getCanonicalPath() + "'");
                //destZipFile = ppp.getCanonicalPath() + File.separator + destZipDate + ".ppp";
                //destZipFile = destZipDate + ".zap";
                destZipFile = "temp_" + mUUID + ".zap";
            } catch (Exception e) {
                
            }         

            p("ZIP NAME-@: " + destZipFile); 
            
            ZipOutputStream zip = null;
            FileOutputStream fileWriter = null;
            try {
                fileWriter = new FileOutputStream(destZipFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
            zip = new ZipOutputStream(fileWriter);                   
            ZipFolder zipper = new ZipFolder();

            
                    
            File[] files = processing_dir.listFiles();
            
            String sClient = "";
            try {
                InetAddress clientIP = NetUtils.getLocalAddressNonLoopback2();
                if (clientIP == null) {
                    clientIP = InetAddress.getLocalHost();
                }
                if (clientIP != null) {
                    sClient = clientIP.getHostAddress();                                
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
                    
            for (File file: files){
                if (file.isDirectory())
                    continue;
                
                log("Server IP = " + mHostName + " Client IP = " + sClient, 2);
                log("Processing file " + file.getAbsolutePath(), 2);                               
                
                if (file.getName().contains(".zip")) {                    
                    //transmit ZIP file to Server
                    try {                                                                                             
                        if (bHostFound) {  
                            boolean bres = false;
                            if (sClient.equals(mHostName)) {
                                log("Copying ZIP file locally: " + file.getCanonicalPath(), 2);
                                int n = copyfile(file,"../rtserver/incoming/");
                                if (n == 0) bres = true;
                            } else {
                                //transfer via HTTP
                                //p("*** override hardcoding netty port mHostPort = " + mHostPort);
                                //mHostPort = "8085";
                                String sHostFile = "http://" + mHostName + ":" + mHostPort + "/" + file.getName();
                                log("Sending HTTP POST '" + sHostFile + "'", 2);                            
                                HTTPRequestPoster htrp = new HTTPRequestPoster();
                                Writer writer = new FileWriter("file-output.txt");
                                //Reader reader = new FileReader(file.getAbsolutePath());
                                File fh = new File(file.getAbsolutePath());
                                InputStream fis = new FileInputStream(fh); 
                                URL oracle = new URL(sHostFile);
                                //bres = htrp.postData(fis,oracle,writer);
                                //int res = htrp.postData_new(fis,sHostFile,writer);
                                int res = htrp.postData_new2(file,sHostFile,writer);
                                p("res postdata new = " + res);
                                if (res > 0) bres = true;
                                fis.close();                                   
                            }
                            if (bres) {
                                log("ZIP post/copy OK. deleting ZIP file '"  + file.getAbsolutePath() + "'", 2);
                                try {
                                    bres = file.delete();
                                    p("bres delete" + bres);
                                    if (!bres) log("WARNING: there was an error deleting ZIP file:" + file.getAbsolutePath(),2);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                log("WARNING: there was an error in the POST.", 0);
                            }                                    
                                                            
                        } else {
                            p("Server is not available. Will try to send ZIP later...");
                        }                                             
                    
                    } catch (Exception e) {
                        e.printStackTrace();
                        log("WARNING: There was an error processing the ZIP file.", 0);
                    } 
                } else {
                    //add files to zip                    
                    try {                                                                      
                          if (!file.getName().contains(".zap")) {
                              
                            //1. copy file locally
                            log("copying record file to local incoming: " + file.getCanonicalPath(),2);
                            int n = copyfile(file,"../rtserver/incoming/");
                                 
                            //2. add file to ZIP if we need to send it to server.
                            if (bHostFound && !sClient.equals(mHostName) || !bHostFound) {
                                if ((lBytesZipped + file.length()) < mZipMax) {  //unpacked not to exceed x MB
                                    lBytesZipped += file.length();
                                    nFilesZipped++;
   
                                    log("adding file to ZIP: " + file.getCanonicalPath(), 2);                                
                                    int nres = zipper.addFileToZip("", file.getCanonicalPath(), zip);  
                                    log("nres addzip = " + nres, 2);
                                    zip.flush();                               
                                  } else {
                                    //System.out.println("leaving file for next round...");
                                  }    
                            } else {
                                log("Skipped ZIP creation. Client==SERVER",2);
                            }                            
                            
                            //3. finally, delete the file.
                            File filer = new File(mScanDirectory + file.getName());
                            if (filer.exists()) {
                                if (filer.delete()) {
                                    p("Delete OK");
                                } else {                                  
                                    log("WARNING: Delete FAIL: " + filer.getCanonicalPath(), 0);
                                }
                            } else {
                                log("WARNING: record file does not exist: " + filer.getCanonicalPath(), 0);
                            }                                

                        } else {
                            p("deleting an old .zap file...: " + file.getName());
                            //delete old zap files
                            file.delete();     
                        }                          
                    } catch (Exception e) {
                        e.printStackTrace();
                    }                                       
                } //end add file to ZIP
            }
            
            log("zipped #files: " + nFilesZipped + " bytes " + lBytesZipped, 2);

        
            try {
                p("destZipFile" + destZipFile);                
                File zap = new File(destZipFile);
                if (zap.length() > 0) {
                    zip.close();
                    p("Time to rename the file at " + zap.getCanonicalPath());
                    File zapn = new File(mScanDirectory + mUUID + "_" + destZipDate + ".zip");
                    zap.renameTo(zapn);
                } else {
                    p("skip len = 0");                    
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            
        } else {
            p("scan directory does not exist");
        }
        
    }
    
    void loadServerProps() throws IOException {
        p("loadProps()");
        File f = new File
                (
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
            String r = props.getProperty("port");
            if (r != null) {
                mLocalPort = r;
            }
            
        }

    }
    
    void loadProps() throws IOException {
        System.out.println(System.getProperty("java.home"));
        File f = new File
                (
                "config"+
                File.separator+
                "www-scrubber.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
//            r = props.getProperty("forceindex");
//            if (r != null) {
//                mForceIndex = Boolean.valueOf(r);
//            }
        }
    }
    
    void printProps() {
        System.out.println("outgoing=" + mScanDirectory);        

    }
    
    void loadBackupProps() throws IOException {
        //System.out.println(System.getProperty("java.home"));
        System.out.println("loadProps()");
        File f = new File(mCONFIG_PATH);
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
            String r = props.getProperty("outgoing");
            if (r != null) {
                mScanDirectory = r;
            }

            r = props.getProperty("uuidpath");
            if (r != null) {
                mUUIDPath = r;
            }

            r = props.getProperty("zipmax");
            if (r != null) {
                mZipMax = Integer.parseInt(r);
            }
            
            r = props.getProperty("mode");
            if (r != null) {
                mMode = r;
            }
            
            r = props.getProperty("logpath");
            if (r != null) {
                LOG_PATH = r;
            }
            
            r = props.getProperty("loglevel");
            if (r != null) {
                mLogLevel = Integer.parseInt(r);
            }

        }

    }
    
    void printBackupProps() {
        p("outgoing = '" + mScanDirectory + "'");
        p("uuidpath = '" + mUUIDPath + "'");
        p("zipmax = '" + mZipMax + "'");
        p("mode = '" + mMode + "'");
        p("dbmode = '" + mDBMode + "'");
        p("logpath = '" + LOG_PATH + "'");
        p("loglevel = '" + mLogLevel + "'");
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
                    p("Old value = " + r);   
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
    
    int copyfile (File f, String _destpath) {
        
        try {
            InputStream is = new FileInputStream(f);
            File fd = new File(_destpath + "/" + f.getName());
            OutputStream os = new FileOutputStream(fd);
            
            byte[] buff = new byte[1024*1024];
            int nlen;
            BufferedInputStream bis = new BufferedInputStream(is);
            while ((nlen = bis.read(buff)) > 0) {
                os.write(buff, 0, nlen);
            }
            is.close();
            os.flush();
            os.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            return 0;
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

                    p("Waiting for probe: " + mSignature);  
                    clientSocket.setSoTimeout(10000);
                    clientSocket.receive(recievePacket);

                    String s = new String(recieveData);
                    //p("packet data:" + s);

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
                    //p("normal exit1");
                    
                                       
                } catch (BindException e) {
                    p("socket in use. attempt: " + nRetry);
                    e.printStackTrace();
                    nRetry++;
                    doWait = true;
                } catch (SocketTimeoutException e) {
                    p("socket timeout. attempt: " + nRetry);
                    e.printStackTrace();
                    nRetry++;
                    doWait = true;
                } catch (Exception e) {
                    p("WARNING: there was some kind of exception");    
                    e.printStackTrace();
                    nRetry++;
                    doWait = true;
                } finally {
                    if (doWait) {
                        Random generator = new Random();
                        int roll = generator.nextInt(1000) + 1;                   
                        p("Waiting " + (1000+roll) + "ms..."); 
                        Thread.sleep(1000 + roll);
                    }

                    //p("finally1...");

                    if (clientSocket != null) {
                        //p("finally2a...");
                        if (clientSocket.isConnected()) {
                            //log("isconnected...");
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

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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Scanner;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;

import java.util.StringTokenizer;
import static services.BackupClientService.sUUIDpath;

import utils.Appendage;
import utils.HTTPRequestPoster;
//import static services.BroadcastService.mLocalPort;
//import static services.BroadcastService.p;

import java.util.Random;
import static services.TransferService.bHostFound;

import utils.NetUtils;

public class ClientService implements Runnable {
   Thread t;
   String  mServername;
   private String mLocalPort;
   private String mSignature;
   private String mBackupNode = "no";
   private String mSyncNode = "no";
   private String mLastID = "";
   private String mLastBatch = "";
   private String mLastSeq = "";
   private String mBackupPath;
   private String mSyncPath = "";
   private String mUUIDPath;
   private String mPortRT;
   private String mMode = "";
   protected Properties props = new Properties();
   boolean mTerminated = false;
   long mDelay = 10000;
   String LOG_NAME = "client.txt";
   String LOG_PATH = "logs/";
   int mLogLevel = 0;
   
   PrintStream log= null;
   
   public boolean bHostFound = false;
   String mCONFIG_PATH = "";
    static String appendage = "";
    static String appendageRW = "";
       
   public void terminate() {
       
       log("Recieved Termination request.", 0);
       mTerminated = true;       
   }

   static boolean bConsole = true;
      
   public ClientService(String _server, 
           String _server_port, 
           Long _delay, 
           String _signature, 
           boolean _dothread, 
           boolean _hostfound, 
           String _configpath,
           int _loglevel) {

      p("*** server name = " + _server);
      mServername =  _server;
      mPortRT = _server_port;
      mDelay = _delay;
      mSignature = _signature;
      bHostFound = _hostfound;
      mCONFIG_PATH = _configpath;
      mLogLevel = _loglevel;

      try {
            Appendage app = new Appendage();
            appendage = app.getAppendage();
            appendageRW = app.getAppendageRW();
            p("CONFIG PATH = " + _configpath);
            loadBackupProps();
            
            String sLog = appendage + LOG_PATH + LOG_NAME;

            p("LOG PATH = " + sLog);


            log = new PrintStream(new BufferedOutputStream(
                            new FileOutputStream(sLog,true)));
            log("opening log file: " + sLog, 1);

            if (_dothread) {
              // Create a new, second thread
              t = new Thread(this, "sc_c");
              p("Child thread: " + t);
              t.start(); // Start the thread          
            }
      } catch (Exception e) {
          e.printStackTrace();
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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [SC.ClientService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [SC.ClientService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [SC.ClientService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    static protected void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [SC.client_" + threadID + "] " + s);
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
            p(_loglevel + " " + s);
        }
    }

    public void getServerAddressPort() throws InterruptedException {
        
        try {
            p("local port = " + mLocalPort);
            InetAddress clientIP = NetUtils.getLocalAddressNonLoopback2();
            if (clientIP != null) {
                log("local client ip[1] = " + clientIP.getHostAddress(),2);                                
            } else {
                try {
                    clientIP = InetAddress.getLocalHost();
                    if (clientIP != null) log("local client ip[2] = " + clientIP.getHostAddress(),2);                
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (clientIP != null) {
                getServerAddressPortProbe();
            } else {
                log("Loopback mode...",2);
                clientIP = NetUtils.getLocalAddressLoopback();
                mServername = clientIP.getHostAddress();
                mPortRT = mLocalPort;
                p("mServername " + mServername);
                p("mPortRT: " + mPortRT);
                bHostFound = true;            
            }
            
            UpdateConfig("serverip", mServername, appendage + "../scrubber/config/serverinfo.properties");
            UpdateConfig("serverport", mPortRT, appendage + "../scrubber/config/serverinfo.properties");

            
        } catch (SocketException e) {
            
        }
        
        
    } 
    
    public void getServerAddressPortProbe() throws InterruptedException {
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
                        log("signatures match. updating server/ip", 2);
                        mServername = sHostIP;
                        mPortRT = sHostPort;
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
                    log("WARNING: there was some kind of exception", 0);    
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
    

    public void run() {
            try {
                
                                
                while (!mTerminated) { 

                    //obtain local IP address
                    InetAddress clientIP = NetUtils.getLocalAddressNonLoopback2();
                    if (clientIP == null) {
                        clientIP = InetAddress.getLocalHost();                          
                    }
                    log("local IP = " + clientIP, 2);

                    p("Starting client run. host found=" + bHostFound);
                    
                    loadProps();
                    printProps();
                    loadBackupProps();
                    printBackupProps();

                    log("Client thread started", 2);

                    if (bHostFound) {
                        p("Server already Found = " + mServername);                        
                    } else {
                        mSignature = NetUtils.getSignature();
                        p("looking for signature: " + mSignature);
                        getServerAddressPort();                        
                        if (bHostFound) {
                            p("Server Found = " + mServername);
                        }                        
                    }                        

                    //update server with local ip address and port
                    
                    //String LocalIP = "127.0.0.1";
                    if (clientIP != null) {
                        String LocalIP = clientIP.getHostAddress();                    
                        p("client hostname = " + clientIP.getHostName());
                        p("client canonical = " + clientIP.getCanonicalHostName());
                        p("client = " + LocalIP);
 
                        //String ServerIP = mServername;
                        p("***server = " + mServername);

                        loadProps();
                        loadBackupProps();
                        p("uuidpath = " + mUUIDPath);                    

                        String sUUID = NetUtils.getUUID(appendage + mUUIDPath);

                        mLastSeq = "-";
                        mLastBatch = "-";
                        if (mSyncNode.equals("yes") || mBackupNode.equals("yes")) {
                            int nres = getLastID();
                            if (nres > 0) {
                                p("mLastSeq = " + mLastSeq);
                                p("mLastBatch= " + mLastBatch);
                            } else {
                                    mLastSeq="error";
                                    mLastBatch="error";                                
                            }                            
                        } 

                        p("uuid = '" + sUUID + "'");                    
                        p("mLocalPort = '" + mLocalPort + "'");
                        p("mBackupNode = '" + mBackupNode + "'");
                        p("mSyncNode = '" + mBackupNode + "'");
                        p("mBackupPath = '" + mBackupPath + "'");
                        p("RTPort = '" + mPortRT+ "'");

                        //find local HD space
                        long mFreeSpace = 0;
                        long mTotalSpace = 0;
                        File file = null;
                        
                        if (mBackupNode.equals("yes")) {                            
                            file = new File(mBackupPath);
                        } else {
                            if (mSyncNode.equals("yes")) {
                                file = new File(mSyncPath);                                
                            }
                        }
                        if (file != null) {
                            mFreeSpace = file.getFreeSpace();
                            mTotalSpace = file.getTotalSpace();
                        }
                        
                        p("Total space = " + mTotalSpace);
                        p("Free space = " + mFreeSpace);
                        float percfree = (float) mFreeSpace / (float) mTotalSpace * 100;
                        p("% free = " + percfree);                            
                                                
                        boolean bRes = false;
                        boolean bNotifyClient = true;
                        //send to server
                        if (bHostFound) {
                            bRes = setnode(mServername, mPortRT, sUUID, LocalIP, mLocalPort,mBackupNode,Long.toString(mFreeSpace));     
                            if (mServername.equals(LocalIP)) bNotifyClient = false;
                        }

                        //send the notification to the local client RT
                        if (bNotifyClient) {
                            p("Notify the local RT");
                            bRes = setnode(LocalIP, mLocalPort, sUUID, LocalIP, mLocalPort,mBackupNode,Long.toString(mFreeSpace));                            
                        }
                    } else {
                        log("WARNING: Skip notify the server, since the interfaces are null.", 0);
                    }
                                         
                    //run only once 
                    //p("Ending client run...");
                    //mTerminated = true;
                    
                    //set hostfound to false, in case server IP address changed                    
                    bHostFound = false;
                    
                    p("Time to sleep for " + mDelay + " ms.");
                    try {
                        Thread.sleep(mDelay);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
 
            } catch (Exception e) {
                e.printStackTrace();
                p("Hit an unexpected exception when attempting to load config.");
            }       
   }
   
             
   
    void printBackupProps() {
        p("backuppath = '" + mBackupPath + "'");
        p("syncpath = '" + mSyncPath + "'");
        p("uuidpath = '" + mUUIDPath + "'");
        p("backupnode = '" + mBackupNode + "'");
        p("syncnode = '" + mSyncNode + "'");
        p("mode = '" + mMode + "'");
    }
    
   
   void loadBackupProps() throws IOException {
    
        //p(System.getProperty("java.home"));
        p("loadBackkupProps()");
        File f = new File(appendage + mCONFIG_PATH);
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();

            String r = props.getProperty("backuppath");
            if (r != null) {
                mBackupPath = URLDecoder.decode(r, "UTF-8");
            }

            r = props.getProperty("syncpath");
            if (r != null) {
                mSyncPath = URLDecoder.decode(r, "UTF-8");
            }

            r = props.getProperty("uuidpath");
            if (r != null) {
                mUUIDPath = r;
            }
            
            r = props.getProperty("backupnode");
            if (r != null) {
                mBackupNode = r;
            }
            r = props.getProperty("syncnode");
            if (r != null) {
                mSyncNode = r;
            }
            
            r = props.getProperty("logpath");
            if (r != null) {
                LOG_PATH = r;
            }

            r = props.getProperty("mode");
            if (r != null) {
                mMode = r;
            }

        } else {
            pw("WARNING: config file not found: " + f.getAbsolutePath());
        }

    }

   void printProps() {
        p("port = '" + mLocalPort + "'");
    }
   
void loadProps() throws IOException {
        //p(System.getProperty("java.home"));
        p("loadProps()");
        File f = new File
                (
                appendage+
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
        } else {
            pw("[SC.lientService] WARNING: config file not found: " + f.getAbsolutePath());
        }

    }

public int getLastID() {
    
    try {
        String sTmpPath = "";
        if (mBackupPath.length() > 0) {
            sTmpPath = mBackupPath;
        } else {
            if (mSyncPath.length() > 0) {
                sTmpPath = mSyncPath;
            }            
        }
        
        String sFile = sTmpPath + File.separator + "lastid.tmp";
        
        p("sFile = " + sFile);
        
        File fh = new File(sFile);
        if (fh.exists()) {
            FileInputStream bf2 = new FileInputStream(sFile);        
            Scanner scanner2 = new Scanner(bf2);
            mLastBatch = scanner2.nextLine();                        
            mLastSeq = scanner2.nextLine();            
            bf2.close();           
            return 1;
        } else {
            p("file not found: " + sFile);
            return 0;
        }
    } catch (Exception e) {
        e.printStackTrace();
        return -1;        
    }

    
}




public boolean setnode(String _serverIP, String _portRT, String _uuid, String _ipaddress, String _port, String _backupnode, String _freespace) {
    byte[] data;

    try {        
        p("Setnode...");
        p("mMode  : " + mMode);
        String sMachine = "";
        if (mMode.equals("server")) {
            sMachine = mSignature;
        } else {
            String sWindows = NetUtils.getConfig("winserver", appendage + "../rtserver/config/www-server.properties");
            boolean bWindows = Boolean.parseBoolean(sWindows);
            sMachine = NetUtils.getComputerName(bWindows);        
        }
        
        String nettyport = NetUtils.getConfig("nettyport", appendage + "../rtserver/config/www-server.properties");

        p("    [setnode()] nettyport  : " + nettyport);

        String urlStr = "http://"
            + _serverIP + ":" +_portRT +
            "/cass/setnode.php?" + 
            "uuid=" + _uuid +
            "&ipaddress=" + _ipaddress +
            "&port=" + _port +
            "&backup=" + _backupnode +
            "&free=" + _freespace +
            "&sync=" + mSyncNode +
            "&lastseq=" + mLastSeq +
            "&lastbat=" + mLastBatch +
            "&machine=" + sMachine  +
            "&netty=" + nettyport    
            ;

        p("    [setnode()] urlStr  : " + urlStr);

        log("sending string=" + urlStr, 2);

        URL url = new URL(urlStr);
        URLConnection conn = url.openConnection ();
        p("done");
        InputStream rd = conn.getInputStream();
        String outfileName = "inputstream.txt";
        p("source file exists, dest file = '" + outfileName + "'");
        FileOutputStream outFile = new FileOutputStream(outfileName);

        int numRead = 0;
        int total = 0;
        data = new byte[16384];
        int n;
         while ((numRead = rd.read(data)) >= 0) {
            outFile.write(data,0,numRead);
            total += numRead;
        }
        p("numRead = '" + numRead);
        p("total = '" + total);
        rd.close();
        outFile.close();
        return true;
    } catch (IOException e) {
        StringWriter sWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(sWriter));
        log(sWriter.getBuffer().toString(), 0) ;
        pw("WARNING: There was an exception in setnode.");
        return false;
    }
}

   static int UpdateConfig(String _name, String _value, String _config) {
        
        try {
            File f = new File(_config);
            boolean create = false;
            Properties props = new Properties();
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();
                String r = props.getProperty(_name);
                if (r != null ) {
                    //p("Old value = " + r);                                        
                    if (!r.equals(_value)) {
                        props.setProperty(_name, _value);
                        //p("New value = " + props.getProperty(_name));                                                                                    
                        if (_name.length() > 0) {
                            OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
                            props.store(os, "comments");                            
                            os.close();                        
                        } else {
                            p("Skipping blank value.");
                        }
                    } else {
                        //p("same as current value. skipping config write.");
                    }
                } else {
                    create = true;
                }
            } else {
                p("File not found. exiting...");
                create = true;
            }
            
            if(create){
                p("Setting doesn't exist in file. Adding it...");
                if (_name.length() > 0) {                        
                    props.setProperty(_name, _value);
                    p("New value = " + props.getProperty(_name));                                                                                    
                    OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
                    props.store(os, "comments");                            
                    os.close();                        
                } else {
                    p("Skipping blank value.");
                }
            }
            return 0;
   
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }    
        
    }
                
}

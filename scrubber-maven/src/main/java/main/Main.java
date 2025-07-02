/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */

package main;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
//import services.ScrubService;
//import services.ScannerService;
import services.ProcessorService;
//import services.ClientService;
//import services.TransferService;
import services.BroadcastService;
import services.BackupServerService;

import services.ScannerLauncher;
import services.ClientLauncher;
import services.TransferLauncher;
import services.BackupClientLauncher;
import services.VaultLauncher;

import org.boris.winrun4j.AbstractService;
import org.boris.winrun4j.EventLog;
import org.boris.winrun4j.ServiceException;

import utils.NetUtils;

import amazon.AmazonDrive;

import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;

public class Main extends AbstractService {
    
    static boolean mTerminated = false;
    
    static ClientLauncher cl = null;
    static ScannerLauncher sl = null;
    static TransferLauncher tl = null;
    static BackupClientLauncher bcl = null;
    static VaultLauncher vl = null;
    
    
    static ProcessorService ps = null;
    static BroadcastService bs = null;
    static BackupServerService bss = null;
    
    static AmazonDrive acd = null;
    
    static boolean _hostfound = false;
    
    static String _server = "";
    static String _serverport = "";
    static String _signature = "";
    
    static private String mLocalPort;
    static protected Properties props = new Properties();
    
    static String sVersionRunning = "";

    static long DELAY_MAIN = 5;
    static long DELAY_UPDATE = 15;
    
    static String sModeAtLoad = "";
    
    static int mLogLevel = 1;  //defaut loglevel

        
    public int serviceRequest(int request) throws ServiceException {
    switch (request) {
        case SERVICE_CONTROL_STOP:
        case SERVICE_CONTROL_SHUTDOWN:
            mTerminated = true;
            
            if (cl != null) {
                cl.terminate();                
            }

            if (sl != null) {
                sl.terminate();                
            }
            
            if (tl != null) {
                tl.terminate();                
            }

            if (bcl != null) {
                bcl.terminate();                
            }

            if (ps != null) {
                ps.terminate();                
            }

            if (bs != null) {
                bs.terminate();                
            }

            if (bss != null) {
                bss.terminate();                
            }

            System.exit(0);
            break;
        }
        return 0;
    }
   public int serviceMain(String[] args) throws ServiceException {
        int count = 0;
        while (!mTerminated) {
            try {
                //String[] a = new String[0];
                //a[0] = "8080";
                EventLog.report("WinRun4J - Scrubber",
                      EventLog.INFORMATION, "CASS_SCRUBBER_STARTED");    
                main(args);
            } catch (InterruptedException e) {
            } catch (Exception e) {
                
            }
        }
        EventLog.report("WinRun4J - Scrubber",
              EventLog.INFORMATION, "CASS_CASS_SCRUBBER_SHUTDOWN");    
        return 0;
    }

    
   public static void Shutdown(int errorlevel) {
       try {
            if (cl != null) {
                p("SHUTDOWN: client");
                cl.terminate();                
            }

            if (sl != null) {
                p("SHUTDOWN: scanner");
                sl.terminate();                
            }
            
            if (tl != null) {
                p("SHUTDOWN: transfer");
                tl.terminate();                
            }

            if (bcl != null) {
                p("SHUTDOWN: backup client");
                bcl.terminate();                
            }

            if (ps != null) {
                p("SHUTDOWN: processor");
                ps.terminate();                
            }

            if (bs != null) {
                p("SHUTDOWN: probe");
                bs.terminate();                
            }

            if (bss != null) {
                p("SHUTDOWN: backup server");
                bss.terminate();                
            }
            Thread.sleep(5000);
            System.exit(errorlevel);
    } catch (Exception e) {
        e.printStackTrace();
    }
   }
   public static void CheckForUpdate() {
        try {
            
            p("-------------------CHECK FOR UPDATE-----------------");
            String IsWindows = NetUtils.getConfig("winserver", "../rtserver/config/www-server.properties");
            boolean bWindows = Boolean.parseBoolean(IsWindows);

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
                }
            } else {
                p("WARNING: file not found:" + f.getCanonicalPath());
            }                
        } catch (Exception e) {
            p("Warning: There was an exception checking update.");
            e.printStackTrace();
        }
    }
    
    
    public static void WaitForSetup() {
        try {
            long mDelay = 5000;
            
            String sState = getConfig("state", "../rtserver/config/www-setup.properties");
            p("Setup State = " + sState);
            if (sState.equals("NEW")) {
                while (sState.equals("NEW")) {
                    CheckForUpdate();
                    p("State=NEW. Waiting for user to complete Wizard setup. Sleeping for " + mDelay + "ms.");
                    Thread.sleep(mDelay);                
                    sState = getConfig("state", "../rtserver/config/www-setup.properties");
                }                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {

        long mDelay = 15000;   //15 seconds default for delay thread
        long mDelayC = 30000;  //30 seconds for client heartbeat
        long mDelayV = 3000;   //3 seconds for vault
        
        String _configpath = "";

        p("args.length = " + args.length);
        if (args.length < 4 || args.length > 8) {
            p("Usage: scrubber <0:recordsfile> <1:servername> <2:serverport> <3:scanpath> <4:incomingpath> <5:signature> <6:config> <7:flags>");
        } else {
            for (int i=0; i<args.length; i++) {
                p("arg[" + i + "] = '" + args[i] + "'");                
            }
            //String sLocalPort = getConfig("port", "../rtserver/config/www-server.properties");
            //InetAddress ServerIP = NetUtils.getLocalAddressNonLoopback(sLocalPort);
            //String sIP = ServerIP.getHostAddress();
                                
            String _recordsfile = args[0];
            //_server = sIP;
            //_serverport = sLocalPort;
            String _scanpath = args[3];            
            String _incoming = args[4];

            //signature (from rtbackup.properties).
            String sSignatureFromFile = getConfig("signature", "config/www-rtbackup.properties");
            if (sSignatureFromFile.length() > 0) {                   
                //Use signature from config if exists
                _signature = sSignatureFromFile;
            } else {
                _signature = args[5];
            }
            p("signature: "+ _signature);

            //configpath
            _configpath = args[6];
            p("config path: " + _configpath);
            
            CheckForUpdate();
            WaitForSetup();

            if (_incoming.equals("amazon")) {
                _hostfound = true;
                _server = args[1];
                _serverport = args[2];
                mDelayC = 6 * 60 * 60 * 1000; //set heartbeat to 6hours 
            }      

            String sDelay = getConfig("delay_thread", _configpath);
            if (sDelay.length() > 0) {
                mDelay = Long.parseLong(sDelay);
            }
            p("delay_thread: " + mDelay);
            
            String sLogLevel = getConfig("loglevel", _configpath);
            if (sLogLevel.length() > 0) {
                mLogLevel = Integer.parseInt(sLogLevel);
            }
            p("loglevel: " + mLogLevel);
                
            String sMode = getConfig("mode", _configpath);
            sModeAtLoad = sMode;
                
            String _flags = "cstbvpra";
            if (args.length >= 8 ) {
                _flags = args[7];
            } else {
                if (sMode.equals("client")) {
                    String sDBMode = getConfig("dbmode", "config/www-processor.properties");
                    if (sDBMode.equals("p2p") || (sDBMode.equals("both"))) {
                        //P2p mode
                        p("P2P MODE");
                        _flags = "cstbvp";                                                
                    } else {
                        //client
                        p("CLIENT MODE");
                        _flags = "cstbv";                        
                    }
                }
                if (sMode.equals("server")) {
                    //server
                    p("SERVER+CLIENT MODE");
                    _flags = "cstbvpraz";
                }
            }
            
            p("incoming: "+ _incoming);
            p("flags: "+ _flags);
            p("mDelay (client): "+ mDelayC);
            p("mDelay (vault): "+ mDelayV);
                                                          
            if (args.length  >= 6) {                
                //_incoming = args[4];
                //if (!(_incoming.equals("client") || _incoming.equals("amazon"))) {
                    
                    if (_flags.contains("p")) {
                        //Server - Processor
                        p("[spawning a processor]");
                        ps = new ProcessorService(mDelay,_incoming,50,1000, _server, _serverport, mLogLevel, _configpath);   
                    }
 
                    if (_flags.contains("r")) {
                        p("[spawning a probe]");
                        bs = new BroadcastService(_signature);
                    }

                    if (_flags.contains("a")) { 
                        p("[spawning a backup server]");
                        bss = new BackupServerService(true, mLogLevel);
                    }
                //}  
            }
            
            if ( _incoming.equals("amazon") ) {
                p("[Amazon mode] - host found: " + _server + ":" + _serverport);
                _hostfound = true;
            } else {
                try {
                    getServerAddressPort();
                } catch (Exception e) {
                    e.printStackTrace();
                }                
                p("[Client / Server mode] - host found: " + _hostfound + " " + _server + ":" + _serverport);
            }                       
            
            if (_flags.contains("c")) {
                //Client - Poller
                p("[spawning client] server:" + _server);
                //new ClientService(_server,_serverport, mDelay, _signature, true);
                cl = new ClientLauncher(_server,_serverport, mDelayC, _signature, _hostfound, _configpath, mLogLevel);                
            }

            if (_flags.contains("s")) {
                //Client - Scanner
                p("[spawning a scanner]");
                //new ScannerService(_recordsfile, _server, mDelay, _scanpath, _signature, true);
                sl = new ScannerLauncher(_recordsfile, _server, mDelay, _scanpath, _signature, _hostfound, _configpath, mLogLevel);
            }
            
            if (_flags.contains("t")) {
                //Client - Transfer
                p("[spawning a file transfer]");
                //new TransferService("outgoing/",_server,_serverport, mDelay, _signature, true);   
                tl = new TransferLauncher("outgoing/",_server,_serverport, mDelay, _signature, _hostfound, _configpath, mLogLevel);   
            }
            
            if (_flags.contains("b")) {
                //Client - Transfer
                p("[spawning a backup client]");
                //new TransferService("outgoing/",_server,_serverport, mDelay, _signature, true);   
                    bcl = new BackupClientLauncher(_server,_serverport, _signature, _hostfound, _configpath, mDelay, mLogLevel);   
            }
            
            if (_flags.contains("z")) {
                //Client - Transfer
                p("[spawning amazon cloud drive]");
                //new TransferService("outgoing/",_server,_serverport, mDelay, _signature, true);   
                    acd = new AmazonDrive(2);   
            }
            
            
        }
        
        long checkTimeout = DELAY_UPDATE / DELAY_MAIN;
        while (!mTerminated) {
            p("Main thread#: " + checkTimeout);
            if (checkTimeout-- == 0) {
                //check for updates is checked every 60 seconds
                CheckForUpdate();
                checkTimeout = DELAY_UPDATE / DELAY_MAIN;
            }
            //changes for vault settings is checked every second (Server Only)
            String sMode = getConfig("mode", _configpath);
            if (sMode.equals("server")) vaultCheckSettings();
            //check for mode change (from client to server)
            checkModeChange();
            Thread.sleep(DELAY_MAIN*1000);           
        }
        
        p("Main thread exiting.");
    }
    
   /* spawn a probe thread if the user switches from client to server */
   private static void checkModeChange() {
       String sMode = NetUtils.getMode();
       p("Mode: '" + sMode + "' ModeAtLoad: '" + sModeAtLoad + "'");
       if (sMode.equals("server") && bs == null && !sModeAtLoad.equals(sMode) && sModeAtLoad.length() > 0) {
            p("[spawning a probe - chkmodechange]");
            bs = new BroadcastService(_signature);           
       }
   }
   
    /*
        Checks if vault settings have changed in order to register or unregister
        the current cluster. (RelayBridge process requests only runs for registered
        clusters)
    */
    private static void vaultCheckSettings() {
        String allowRemote = getConfig("allowremote", "../rtserver/config/www-server.properties");
        
        String bridgeHost = getConfig("bridge-host", "config/www-bridge.properties");
        String bridgePort = getConfig("bridge-port", "config/www-bridge.properties");
        String clusterHost = getConfig("cluster-host", "config/www-bridge.properties");
        String clusterPort = getConfig("port", "../rtserver/config/www-server.properties");
        String clusterName = getConfig("signature", "config/www-rtbackup.properties");
        String secure = getConfig("secure", "config/www-bridge.properties");
        String clusterId = NetUtils.readFileIntoString("data/clusterid").replaceAll("(\\r|\\n)", "");
        String clusterToken = NetUtils.readFileIntoString("data/clusterToken").replaceAll("(\\r|\\n)", "");
        
        p("clusterPort = " + clusterPort);
        
        p("vlnull= " + (vl == null ? true: false));
        if (vl != null) {
            p("vsnull= " + vl.isnull());     
            if (!vl.isnull()) {
                p("isalive = " + vl.isalive());
                if (!vl.isalive()) {
                    p("heartbeat stopped. terminating vl");
                    vl.terminate();
                    vl = null;
                }
            }
        }
        
        //if for some reason the launcher stopped
        //clear reference
        if (vl != null && vl.finished()) {
            vl = null;
        }
        
        if (allowRemote.equals("true")) {
            if (!clusterId.equals("")) {
                if (vl == null) {
                    vl = new VaultLauncher(bridgeHost,bridgePort, secure.equals("true"), clusterHost, clusterPort, clusterId, clusterName, clusterToken, mLogLevel);  
                    vl.start();
                }
            } else {
                p("Allow remote is set but cluster identifier is empty, please check there's a file in scrubber/data/clusterid with the cluster identifier");
            }
        } else {
            if (vl != null) {
                //vl.unregister();
                vl.terminate();
                vl = null;
            } else {
                if (!clusterToken.equals("")) {
                    //If I have a disallowed access with a token set, unregistration
                    //is pending, create a launcher only to unregister the cluster but do not run
                    vl = new VaultLauncher(bridgeHost, bridgePort, secure.equals("true"), clusterHost, clusterPort, clusterId, clusterName, clusterToken, mLogLevel);
                    //vl.unregister();
                    vl = null;
                }
            }
        }
    }
    
    public static void getServerAddressPort() throws InterruptedException {
        
        
        try {
            loadProps();
            InetAddress clientIP = NetUtils.getLocalAddressNonLoopback2();
            if (clientIP != null) {
                p("Attempting to find probe.");
                getServerAddressPortProbe(5);
            } else {
                p("Loopback case...");
                
                clientIP = NetUtils.getLocalAddressLoopback();
                //clientIP = getLocalAddress();
                _server = clientIP.getHostAddress();
                _serverport = mLocalPort;
                p("mServername " + _server);
                p("mPortRT: " + _serverport);
                _hostfound = true;            
            }
            
        } catch (SocketException e) {
                e.printStackTrace();
        } catch (IOException e) {
                e.printStackTrace();            
        }
        
        
    } 
    
    public static void getServerAddressPortProbe(int _retries) throws InterruptedException {
           int PORT = 1234;
           byte[] recieveData = new byte[100];
           DatagramSocket clientSocket = null;
           DatagramPacket recievePacket = null;
           int nRetries = 0;

   
           while (!_hostfound && nRetries < _retries) {
                nRetries++;
                try {
                    
                    clientSocket = new DatagramSocket(PORT);
                    recievePacket = new DatagramPacket(recieveData, recieveData.length);

                    p("Checking for probe. Attempt #" + nRetries);       
                    clientSocket.setSoTimeout(10000);
                    clientSocket.receive(recievePacket);

                    String s = new String(recieveData);
                    p("packet data:" + s);

                    StringTokenizer st = new StringTokenizer(s,",", true);
                    String sCount = st.nextToken();
                    st.nextToken();
                    String sSignature = st.nextToken();
                    st.nextToken();
                    String sHostIP = st.nextToken();
                    st.nextToken();
                    String sHostPort = st.nextToken().trim();

                    p("signature: '" + sSignature + "' host: '" + sHostIP + "' port: '" + sHostPort + "'");
                    if (sSignature.equals(_signature)) {
                        p("signatures match. updating server/ip");
                        _server = sHostIP;
                        _serverport = sHostPort;
                        _hostfound = true;
                    } else {
                        p("'" + _signature + "' vs '" + sSignature + "'");
                        p("signatures don't match. dismissing this probe.");
                    }
                    
 
                } catch (BindException e) {
                    p("socket in use.");
                } catch (SocketTimeoutException e) {
                    p("socket timeout.");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    p("finally...");
                    
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
                }
            }
            if (_hostfound) {
                p("Host found. Exiting...");
            } else {
                p("Host search exceeded retries. Exiting...");                
            }

        }
      
    /* print to stdout */
    static protected void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [SC.main_" + threadID + "] " + s);
    }
    
   static void loadProps() throws IOException {
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
   
   static String getConfig(String _name, String _config) {
        
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
    
    
}

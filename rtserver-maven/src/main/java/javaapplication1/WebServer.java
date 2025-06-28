/**
 * TEST wizard
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */

package javaapplication1;

//import mailer.Mailer;
        
//import java.awt.Desktop;
import netty.HttpStaticFileServer;
import netty.HttpUploadServer;
import utils.PasswordHash;
import utils.UserCollection;
import utils.User;
import utils.ShareToken;
import utils.ShareTypes;
import utils.ShareController;
import utils.Stopwatch;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.*;
//import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
//import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import static javaapplication1.HttpConstants.HTTP_BAD_METHOD;
import static javaapplication1.HttpConstants.HTTP_NOT_FOUND;
import static javaapplication1.WebServer.LocalIP;
import static javaapplication1.WebServer.bBase64Pic;
import static javaapplication1.WebServer.bCloudHosted;
import static javaapplication1.WebServer.bIsPrevious;

import static javaapplication1.WebServer.bWindowsServer;
import static javaapplication1.WebServer.blackList;
import static javaapplication1.WebServer.dbmode;
import static javaapplication1.WebServer.log;
import static javaapplication1.WebServer.p;




import org.boris.winrun4j.AbstractService;
import org.boris.winrun4j.EventLog;
import org.boris.winrun4j.ServiceException;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
//import javax.mail.Address;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import javax.mail.BodyPart;
import javax.mail.Multipart;

//import javax.activation.*;
import jakarta.activation.*;

import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;

//import java.io.UnsupportedEncodingException;
//import static javaapplication1.WebServer.props;
import static javaapplication1.WebServer.root;
import static javaapplication1.WebServer.sAllowOtherUsers;
import static javaapplication1.WebServer.sAllowPeer;
import static javaapplication1.WebServer.sMailAllowMail;
import static javaapplication1.WebServer.sMailFrom;
import static javaapplication1.WebServer.sMailHost;
import static javaapplication1.WebServer.sMailPort;
import static javaapplication1.WebServer.sMailPortPOP;
import static javaapplication1.WebServer.scanTreeMode;
import static javaapplication1.WebServer.scanTreeVariant;
//import static javaapplication1.WebServer.wf;
import utils.CacheMetadataWeb;
import utils.FolderMetaData;
import utils.FolderUtils;
import utils.HTTPRequestPoster;
import static utils.LocalFuncs.DB_PATH;
import utils.NetUtils;
import utils.ScannerRecursive;

import utils.WebFuncs;
//import javax.mail.Flags;
//import javax.mail.Flags.Flag;

import utils.sURLPack;

import utils.MailerFuncs;
import utils.Node;
import utils.SortableValueMap;

import javaapplication1.StatHat;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import utils.DateUtil;
import utils.FacebookUtil;
import utils.LocalFuncs;
import utils.UserMessage;
import utils.UserMessageCollection;

import org.apache.hc.core5.http.ClassicHttpRequest;

public class WebServer extends AbstractService {

    static UUID gaUUID = null;
    
    public static String keyspace = "Keyspace1b";
    static boolean shutdown = false;
    static boolean bAgreeEULA = false;
    
    public static WebFuncs wf = null;
    
    static java.util.Hashtable<String,UserSession> uuidmap = new java.util.Hashtable<String,UserSession>();

    static HashMap<String,String> probes = new HashMap<String, String>();

    boolean remote = true;
    
    static UserMessageCollection chats = UserMessageCollection.getInstance();
    
    /* static class data/methods */

    public int serviceRequest(int request) throws ServiceException {
    switch (request) {
        case SERVICE_CONTROL_STOP:
        case SERVICE_CONTROL_SHUTDOWN:
            shutdown = true;
            System.exit(0);
            break;
        }
        return 0;
    }
   public int serviceMain(String[] args) throws ServiceException {
        int count = 0;
        while (!shutdown) {
            try {
                //String[] a = new String[0];
                //a[0] = "8080";
                EventLog.report("WinRun4J - Client",
                      EventLog.INFORMATION, "CASS_STARTED");    
                main(args);
            } catch (InterruptedException e) {
            } catch (Exception e) {
                
            }
        }
        EventLog.report("WinRun4J - Client",
              EventLog.INFORMATION, "CASS_SHUTDOWN");    
        return 0;
    }

    
    /* print to stdout */
    protected static void p(String s) {

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println("[WebServer-" + threadID + "] " + s);
        }
    }

    /* print to the log file */
    protected static void log(String s, int _loglevel) {

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

    //log
    static PrintStream log = null;
    static String logpath = "";
    
    /* our server's configuration information is stored
     * in these properties
     */
    //protected static Properties props = new Properties();

    /* Where worker threads stand idle */
    static Vector threads = new Vector();

    /* the web server's virtual root */
    static File root;
    static String root_path;
    static File incoming;

    /* timeout on client connections */
    static int timeout = 0;

    /* max # worker threads */
    static int workers = 5;

    /* port */
    static int port = 0;
    
    static int nettyport = 0;
    static int nettyport_post = 0;
    
    static String password;
    
    static int timeshut = 60;
    static boolean bExitAfterTimeshut = false;
    
    static boolean bCloudHosted = false;
    static boolean bAWSHosted = false;
    static boolean bLogUserPw = false;

    static String sMailFromPassword = "";
    static String sMailFrom = "";
    static String sMailHost = "";
    static String sMailPort = "";
    static String LocalIP = "";
    static HashMap<String,String> blackList=new HashMap<String, String>();
   
    
    static String sMailHostPOP = "";
    static String sMailPortPOP = "";
    
    static String sMailAllowMail = "";
    static String sMailSendMail = "";
    static String sMailScanMail = "";
    static String sMailNotify = "";
    
    
    boolean bRedirectBulker = false;
    String sRedirectBulkerURL = "";
    
    
    String sMode = "";
    
    String sScanDirectory11 = "";
    String sScanDirectory12 = "";
    String sScanDirectory13 = "";
    
    String sScanDirectory21 = "";
    String sScanDirectory22 = "";
    String sScanDirectory23 = "";
    
    String sScanDirectory31 = "";
    String sScanDirectory32 = "";
    String sScanDirectory33 = "";
    
    String sScanDirectory41 = "";
    String sScanDirectory42 = "";
    String sScanDirectory43 = "";
    
    String sScanMode = "";
    String sScanMode2 = "";
    String sScanMode3 = "";
    String sScanMode4 = "";
    
    String sBackupDirectory = "";
    String sBackupDirectory2 = "";
    String sBackupDirectory3 = "";
    String sBackupDirectory4 = "";
    
    String sBackupMode1 = "";
    String sBackupMode2 = "";
    String sBackupMode3 = "";
    String sBackupMode4 = "";
    
    String sSyncDirectory1 = "";
    String sSyncDirectory2 = "";
    String sSyncDirectory3 = "";
    String sSyncDirectory4 = "";

    String sSyncMode1 = "";
    String sSyncMode2 = "";
    String sSyncMode3 = "";
    String sSyncMode4 = "";
    
    
    //String sSyncMode = "";
    //String sSyncDirectory = "";
    
    String serveros="win";
    
    String qAccounts="";
    String sendAccounts="";
    String mailGroups="";
    
    String sRepFactor = "3";  //Replication Factor
    
    static int mLogLevel = 1; //default loglevel=1
    
    static boolean bButtonModelNew=true;
    
    static boolean bAllowPeer = false;  //allow connections from peers
    static boolean bWindowsServer = false;    
    static boolean bConsole = true; //turn on console output
    
    static boolean bIsPrevious = false; //user clicked on previous button (setup wizard)
    static boolean bIsExpressSetup = false;
    static String sAllowPeer = ""; //setup wizard - are peers allowed 
    static String sAllowOtherUsers = "";
     static String sAllowRemote = "";
    //0-OFF,1-Scan local only,2-Scan on click folder, 3-Scan complete
    static int scanTreeMode=0;
    static int scanTreeVariant=1;
    static int scanTreeVelocity=20;
    
    String sSignature = ""; //Server Signature
    String adminuser = "";
    String adminpw1 = "";
    String useraccounts = "";
    String syncrules1 = "";
    String syncrules2 = "";
    String syncrules3 = "";
    String syncrules4 = "";        
    
    
    String sFileNameFolder = "";
    
    static String dbmode = "cass"; //assume cassandra db by default
  
    static boolean bWritePerfLog = false;
    static boolean bBase64Pic = true; //default use base64
    
    static String sMD5Blacklist = ""; //MD5 of blacklist file
    static String sBlackListOld = ""; //Old blacklist file (before changes)    

    
    static String sProperty = "";
    static String sPropertyValue = "";
    
    static String sAuxBoxUser="";
    static String sAuxBoxPassword="";
    static String sAuxCluster="";
   
    
    String useremail = "";
    
    String retries = "3";
    
    static Boolean bAnalyticsGA = false;
    static Boolean bAnalyticsSH = false;
    static String sAnalyticsKeyGA = "";
    static String sAnalyticsKeySH = "";
    
    static Boolean bCloudAmazon = false;
    static Boolean bDriveAmazonURL = false;
    
    String msg_date = "";
    String msg_body = "";
    String msg_type = "";
    String msg_user = "";
        
    static String THUMBNAIL_OUTPUT_DIR="";
    static String DB_PATH="";
    
    static String appendage = "";
    static String appendageRW = "";
    
    
    /* load www-server.properties from java.home */
    static void loadProps() throws IOException {
        
        Properties props = new Properties();
        
        //p(System.getProperty("java.home"));
        String sPath = appendage + "config/" + "www-server.properties";
        System.out.println("looking for config: " + sPath);
        File f = new File
                (appendage + "config/" + "www-server.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
            String r = props.getProperty("root");
            if (r != null) {
                root = new File(appendage + r);
                if (!root.exists()) {
                    throw new Error(root + " doesn't exist as server root");
                }
            }

            r = props.getProperty("incoming");
            if (r != null) {
                incoming = new File(appendage + r);
                if (!incoming.exists()) {
                    throw new Error(incoming + " doesn't exist as incoming folder");
                }
            }

            
            r = props.getProperty("timeout");
            if (r != null) {
                timeout = Integer.parseInt(r);
            }
            
            r = props.getProperty("buttonmodel");
            if (r != null) {
                bButtonModelNew = Integer.parseInt(r)==2;
            }
            r = props.getProperty("workers");
            if (r != null) {
                workers = Integer.parseInt(r);
            }

            r = props.getProperty("scantreevelocity");
            if (r != null) {
                scanTreeVelocity = Integer.parseInt(r);
            }
            r = props.getProperty("port");
            if (r != null) {
                port = Integer.parseInt(r);
            }
            
            r = props.getProperty("nettyport");
            if (r != null) {
                nettyport = Integer.parseInt(r);
            }

            r = props.getProperty("nettyport_post");
            if (r != null) {
                nettyport_post = Integer.parseInt(r);
            }

            r = props.getProperty("password");
            if (r != null) {
                password = r;
            }

            r = props.getProperty("timeshut");
            if (r != null) {
                timeshut = Integer.parseInt(r);
                bExitAfterTimeshut = true;
            }

            r = props.getProperty("cloudhosted");
            if (r != null) {
                bCloudHosted = Boolean.parseBoolean(r);
            }

            r = props.getProperty("awshosted");
            if (r != null) {
                bAWSHosted = Boolean.parseBoolean(r);
            }

            r = props.getProperty("logpw");
            if (r != null) {
                bLogUserPw = Boolean.parseBoolean(r);
            }

            r = props.getProperty("writeperflog");
            if (r != null) {
                bWritePerfLog = Boolean.parseBoolean(r);
            }
            
            r = props.getProperty("dobase64");
            if (r != null) {
                bBase64Pic = Boolean.parseBoolean(r);
            }

            r = props.getProperty("log");
            if (r != null) {
                if (appendageRW.length() > 0) {
                    logpath = appendageRW + "/" + r;                    
                } else {
                    logpath = r;
                }
                File logFile = new File(logpath);
                if (logFile.exists()) {
                    log = new PrintStream(new BufferedOutputStream(
                                          new FileOutputStream(logpath,true)));                    
                } else {
                    p("[WebServer] WARNING: Log file could not be opened. try create. " + logFile.getAbsolutePath());
                    log = new PrintStream(new BufferedOutputStream(
                                          new FileOutputStream(logpath,true)));                    
                }
                if (log!= null) log("opened log file: " + logpath, 1);
            }
            
            r = props.getProperty("allowpeer");
                if (r != null) {
                    bAllowPeer = Boolean.parseBoolean(r);
            }
                
            r = props.getProperty("winserver");
                if (r != null) {
                    bWindowsServer = Boolean.parseBoolean(r);
            }
            
            r = props.getProperty("scantreemode");
            if (r != null && scanTreeMode==0) {
                scanTreeMode = Integer.parseInt(r);
            }
            
            r = props.getProperty("scantreevariant");
            if (r != null) {
                if(bWindowsServer){
                    scanTreeVariant = Integer.parseInt(r);
                }
            }
            if(!bWindowsServer){
                scanTreeVariant = 0;
            }

            r = props.getProperty("dbmode");
                if (r != null) {
                    dbmode = r;
            }
                
            r = props.getProperty("console");
                if (r != null) {
                    bConsole = Boolean.parseBoolean(r);
            }
                
            r = props.getProperty("loglevel");
                if (r != null) {
                    mLogLevel = Integer.parseInt(r);
            }
                
        } else {
            System.out.println("WARNING: file not found " + f.getAbsolutePath());
        }

        /* if no properties were specified, choose defaults */
        if (root == null) {
            root = new File(System.getProperty("user.dir"));
        }
        //if (timeout <= 1000) {
        //    timeout = 5000;
        //}
        //if (workers < 25) {
        //    workers = 5;
        //}
        if (log == null) {
            p("logging to stdout");
            log = System.out;
        }
    }
    
    public void loadPropsProcessor() throws IOException {
        
        String sPath = appendage + "../scrubber/config/" + "www-processor.properties";
        System.out.println("looking for config: " + sPath);

        
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
        
        Properties props=new Properties();
        
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

            r = props.getProperty("dbpath");
            if (r != null) {
                DB_PATH = r;
            }
            p("DB_PATH = " + DB_PATH);

        } else {
           p("[loadPropsProcessor()] WARNING config/www.processor-properties does not exist");
        }
    }
       
    static void loadPropsMailer() throws IOException {
        
        Properties props = new Properties();
        
        String r = "";
        //p(System.getProperty("java.home"));
        
        String sPath = "";
        if (bCloudHosted) {
            sPath = "config/www-mailer.properties";
        } else {
            //sPath = "../../../mailer/config/www-mailer.properties";
            sPath = appendage + "config/www-mailer.properties";
        }
        
        try {
            File f = new File(sPath);
            //p("config path: " + sPath);
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new
                               FileInputStream(f));
                props.load(is);
                is.close();

                r = props.getProperty("smtphost");
                if (r != null) {
                    sMailHost = r;
                }
                r = props.getProperty("smtpport");
                if (r != null) {
                    sMailPort = r;
                }
                r = props.getProperty("pop3host");
                if (r != null) {
                    sMailHostPOP = r;
                }
                r = props.getProperty("pop3port");
                if (r != null) {
                    sMailPortPOP = r;
                }
                r = props.getProperty("allowmail");
                if (r != null) {
                    sMailAllowMail = r;
                }
                r = props.getProperty("sendmail");
                if (r != null) {
                    sMailSendMail = r;
                }
                r = props.getProperty("scanmail");
                if (r != null) {
                    sMailScanMail = r;
                }
                r = props.getProperty("notifymail");
                if (r != null) {
                    sMailNotify = r;
                }
                r = props.getProperty("pop3user");
                if (r != null) {
                    sMailFrom = r;
                }
                r = props.getProperty("pop3pw");
                if (r != null) {
                    sMailFromPassword = r;
                }
            } else {
                p("WARNING: Mailer config file does not exist: " + f.getAbsolutePath() );
            }
            
        } catch (IOException e) {
            p("WARNING: mailer config file not found.");
        } catch (Exception e) {
            p("WARNING: mailer config file not found.");
        }


    }
    
    static void loadPropsCloud() throws IOException {
        String r = "";
        String sPath = appendage + "config/www-cloud.properties";
        Properties props = new Properties();
    
        try {
                boolean sExists = new File(sPath).exists();
                //p("config path: " + sPath);
                if (sExists) {
                    File f = new File(sPath);
                    InputStream is =new BufferedInputStream(new
                                   FileInputStream(f));
                    props.load(is);
                    is.close();

                    r = props.getProperty("drive_amazon");
                    if (r != null) {
                        bCloudAmazon = Boolean.parseBoolean(r);
                    }
                } else {
                        p("WARNING: Cloud config file does not exist: " + sPath);    
                }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    static void loadPropsAnalytics() throws IOException {
        
        Properties props = new Properties();
        
        String r = "";
        //p(System.getProperty("java.home"));
        
        String sPath = "";
        if (bCloudHosted) {
            sPath = "config/www-analytics.properties";
        } else {
            //sPath = "../../../mailer/config/www-mailer.properties";
            sPath = appendage + "config/www-analytics.properties";
        }
        
        try {
            boolean sExists = new File(sPath).exists();
            //p("config path: " + sPath);
            if (sExists) {
                File f = new File(sPath);
                InputStream is =new BufferedInputStream(new
                               FileInputStream(f));
                props.load(is);
                is.close();

                r = props.getProperty("events_ga");
                if (r != null) {
                    bAnalyticsGA = Boolean.parseBoolean(r);
                }

                r = props.getProperty("events_sh");
                if (r != null) {
                    bAnalyticsSH = Boolean.parseBoolean(r);
                }

                r = props.getProperty("key_ga");
                if (r != null) {
                    sAnalyticsKeyGA = r;
                }

                r = props.getProperty("key_sh");
                if (r != null) {
                    sAnalyticsKeySH = r;
                }

            } else {
                p("WARNING: Analytics config file does not exist: " + sPath);
            }
            
        } catch (IOException e) {
            p("WARNING: mailer config file not found.");
        } catch (Exception e) {
            p("WARNING: mailer config file not found.");
        }
    }

    static void printProps() {
        log("root="+root, 1);
        log("incoming="+incoming, 1);
        log("timeout="+timeout, 1);
        log("buttonmodel="+bButtonModelNew, 1);
        log("workers="+workers, 1);
        log("port="+port, 1);
        log("nettyport="+nettyport, 1);
        log("nettyport_post="+nettyport_post, 1);
        log("password="+password, 1);
        log("timeshut="+timeshut, 1);
        log("exitaftertimeshut="+bExitAfterTimeshut,1);
        log("cloudhosted="+bCloudHosted,1);
        log("awshosted="+bAWSHosted,1);
        log("log="+logpath,1);
        
        log("writeperflog="+bWritePerfLog,1);
        log("dobase64="+bBase64Pic,1);
        log("allowpeer="+bAllowPeer,1);
        log("winserver="+bWindowsServer,1);
        log("console="+bConsole,1);

        log("scantreemode="+scanTreeMode,1);
        log("scantreevariant="+scanTreeVariant,1);
        log("dbmode="+dbmode,1);
        //mailer

        log("smtphost="+sMailHost,1);
        log("smtpport="+sMailPort,1);
        log("pop3host="+sMailHostPOP,1);
        log("pop3port="+sMailPortPOP,1);
        log("pop3user="+sMailFrom,1);
        log("pop3pw="+sMailFromPassword,1);

        log("allowmail="+sMailAllowMail,1);
        log("sendwmail="+sMailSendMail,1);
        log("scanmail="+sMailScanMail,1);
        
        log("events_ga="+bAnalyticsGA,1);
        log("events_sh="+bAnalyticsSH,1);
        log("key_ga="+sAnalyticsKeyGA,1);
        log("key_sh="+sAnalyticsKeySH,1);
        
    }
    
    static int UpdateConfigProp(String _name, String _value, String _config) {
        
        Properties props = new Properties();
        
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
            } else {
                p("File not found. exiting...");
            }
            return 0;
   
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }    
        
    }

    static void setAppendage() {
        boolean result = false;
        File directory = new File("/Applications/Alterante.app/Contents/AlteranteJava.app/Contents/MacOS").getAbsoluteFile();
        //File directory = new File("../app/projects/rtserver").getAbsoluteFile();
        if (directory.exists())
        {
            System.out.println("[WebServer] Found app directory. Setting working dir to it");
            result = (System.setProperty("user.dir", directory.getAbsolutePath()) != null);
            
            appendage = "/Applications/Alterante.app/Contents/AlteranteJava.app/Contents/app/projects/rtserver/";
            System.out.println("appendage  = " + appendage);            
            //appendage = "../app/projects/rtserver/";        
        }
        
        String username = System.getProperty("user.name");
        System.out.println("username: " + username);
        File directoryRW = new File("/Users/" + username + "/Library/Containers/com.alterante.desktopapp1j");
        if (directoryRW.exists()) {
            System.out.println("[WebServer] Found container directory. checking folders.");
            appendageRW = "/Users/" + username + "/Library/Containers/com.alterante.desktopapp1j/Data/app/projects/rtserver/";
            System.out.println("appendageRW: " + appendageRW); 


            //rtserver setup
            File dir = new File("/Users/" + username + "/Library/Containers/com.alterante.desktopapp1j/Data/app/projects/rtserver/");
            if (dir.exists()) {
                System.out.println("[WebServer] appendageRW rtserver exists.");            
            } else {
                boolean res = new File(appendageRW).mkdirs();
                System.out.println("[WebServer] appendageRW rtserver create = " + res);                            
                res = new File(appendageRW + "/logs/").mkdirs();
                System.out.println("[WebServer] appendageRW rtserver create logs = " + res);                            
                res = new File(appendageRW + "/tmp/").mkdirs();
                System.out.println("[WebServer] appendageRW rtserver create tmp = " + res);                            
            }               

            //scrubber setup
            String sScrubberPath = "/Users/" + username + "/Library/Containers/com.alterante.desktopapp1j/Data/app/projects/scrubber/";
            dir = new File(sScrubberPath);
            if (dir.exists()) {
                System.out.println("[WebServer] appendageRW scrubber exists.");            
            } else {
                System.out.println("[WebServer] scrubber path not exist: " + sScrubberPath);  
                boolean res = new File(sScrubberPath).mkdirs();
                System.out.println("[WebServer] appendageRW scrubber create = " + res);                            
                res = new File(sScrubberPath + "/data/").mkdirs();
                System.out.println("[WebServer] appendageRW scrubber create data = " + res);                            
                res = new File(sScrubberPath + "/config/").mkdirs();
                System.out.println("[WebServer] appendageRW scrubber create config = " + res);                            
            }               

        } else {
            System.out.println("[WebServer] Container directory not found.");
        }
    }
    
    public static void main(String[] a) throws Exception {

        boolean result = false;
                
        System.out.println("-----------------------------------------");
        System.out.println("Working Directory[1] = " + System.getProperty("user.dir"));   
        
        Path currentRelativePath = Paths.get(".");
        String sPath = currentRelativePath.toAbsolutePath().toString();
        System.out.println("Current absolute[1]  = " + sPath);
        
        //File directory = new File("../app/projects/rtserver").getAbsoluteFile();
        File directory = new File("/Applications/Alterante.app/Contents/AlteranteJava.app/Contents/MacOS").getAbsoluteFile();
        if (directory.exists())
        {
            System.out.println("[init] Found app directory. Setting working dir to it");
            result = (System.setProperty("user.dir", directory.getAbsolutePath()) != null);
            System.out.println("set user.dir  = " + result);            
            
            appendage = "/Applications/Alterante.app/Contents/AlteranteJava.app/Contents/app/projects/rtserver/";
            System.out.println("appendage  = " + appendage);            
                    
        } else {
            System.out.println("[init] Not Found app directory.");
        }
        
        setAppendage();

        System.out.println("Working Directory[2] = " + System.getProperty("user.dir"));   

        System.out.println("-----------------------------------------");
        
        
        System.setProperty("java.net.preferIPv4Stack" , "true");
        loadProps();
        loadPropsMailer();
        loadPropsAnalytics();
        loadPropsCloud();
        printProps();
        
        if(nettyport == 0){//set nettyport 8084 by default
            nettyport = 8084;
            UpdateConfigProp("nettyport", String.valueOf(nettyport), "config/www-server.properties");        
        }
        (new Thread(new HttpStaticFileServer(nettyport, uuidmap))).start();
        
        if(nettyport_post == 0){//set nettyport 8085 by default 
            nettyport_post = 8085;
        }
        (new Thread(new HttpUploadServer(nettyport_post))).start();
        
        //int port = 8080;
        p("*********** a = " + a[0].length());
        if (a[0] != null && a[0].length() > 0) {
            port = Integer.parseInt(a[0]);
        }
        
        File pubkey = new File(RSACrypto.PUBIC_KEY_PATH);
        File privkey = new File(RSACrypto.PRIVATE_KEY_PATH);
        
        if(!pubkey.exists()||!privkey.exists()){
            RSACrypto rc = new RSACrypto();
            rc.generateKeys();
        }

        printProps();

        int nTries = 0;
        InetAddress clientIP = null;
        while (clientIP == null && nTries < 10) {
            try {
                nTries++;
                System.out.println("Try getLocalhost #" + nTries);
                clientIP = NetUtils.getLocalAddressNonLoopback2();
            } catch (Exception e) {
                e.printStackTrace();
                Thread.sleep(6000);                
            }
        }
        if (clientIP != null) {
            LocalIP = clientIP.getHostAddress();
        } else {
            log("WARNING: getLocalHost() failed. Going to Loopback mode.", 0);
            try {
                clientIP = InetAddress.getLocalHost();
                LocalIP = clientIP.getHostAddress();
            } catch (Exception e) {
                log("WARNING: getLocalHost failed in LOOPback. Exiting...", 0);
                System.exit(-1);                
            } 
        }
        log("Server IP = " + LocalIP, 0);
        
        //LocalFuncs c8 = new LocalFuncs();
        //c8.loadNumberOfCopies(LocalIP);
                
        wf = new utils.WebFuncs(LocalIP);
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                log("*** SHUTDOWN DETECTED ***", 0);
                wf.closeDB();            
            }        
        });

                
        /* start worker threads */
        for (int i = 0; i < workers; ++i) {
            Worker w = new Worker();
            Thread t = new Thread(w, "worker #"+1);
            t.setPriority(Thread.MAX_PRIORITY);
            t.start();
            threads.addElement(w);
            
        }

        ServerSocket ss = new ServerSocket(port);
        while (!shutdown) {
            
            loadProps();
            loadPropsMailer();
            
            Socket s = ss.accept();
                       
            boolean bAllow = false;
            if (bAllowPeer) {
                //allow calls from anywhere
                bAllow = true;
            } else {
                //test if server IP == client IP
                String sCaller = s.getInetAddress().getHostAddress();
                p("**************------- > scaller = " + sCaller);
                if (sCaller.equals(LocalIP)) {
                    bAllow = true;
                } else {
                    if (sCaller.equals("127.0.0.1") || sCaller.equals("0:0:0:0:0:0:0:1%0") || sCaller.equals("0:0:0:0:0:0:0:1")) {
                        bAllow = true;
                    } else {
                        log("****Blocked scaller = " + sCaller, 1);                   
                        s.close();                                            
                    }
                }
            }
            
            if (bAllow) {
                Worker w = null;
                synchronized (threads) {
                    if (threads.isEmpty()) {
                        Worker ws = new Worker();
                        ws.setSocket(s);
                        (new Thread(ws, "additional worker")).start();
                    } else {
                        w = (Worker) threads.elementAt(0);
                        threads.removeElementAt(0);
                        w.setSocket(s);
                    }
                }                
            }
        } //while
    } //main
} //class webserver







class Worker extends WebServer implements HttpConstants, Runnable {
    final static int BUF_SIZE = 1 * 1024 * 1024;
    //final static int BUF_SIZE_POST = 8 * 1024 * 1024;
                                  
    static final byte[] EOL = {(byte)'\r', (byte)'\n' };

    /* buffer to use for requests */
    byte[] buf;
    /* Socket to client we're handling */
    private Socket s;
    long timestamp1 = 0; 
    long timestamp2 = 0;
    String sPathDec;
    

    Worker() {
        //buf = new byte[BUF_SIZE];
        s = null;
        timestamp1 = System.currentTimeMillis();
        
    }

    synchronized void setSocket(Socket s) {
        this.s = s;
        notify();
    }

    public synchronized void run() {
        while(true) {
            if (s == null) {
                /* nothing to do */
                try {
                    wait();
                } catch (InterruptedException e) {
                    /* should not happen */
                    continue;
                }
            }   
            try {
                //p("handleclient");
                buf = new byte[BUF_SIZE];
                //p("sizeof buf[] #1" + buf.length);
                handleClient();
                buf = null;

            } catch (Exception e) {
                e.printStackTrace();
            }
            /* go back in wait queue if there's fewer
             * than numHandler connections.
             */
            s = null;
            Vector pool = WebServer.threads;
            synchronized (pool) {
                if (pool.size() >= WebServer.workers) {
                    /* too many threads, exit this one */
                    return;
                } else {
                    pool.addElement(this);
                }
            }
        }
    }

    void handleClient() throws IOException {

          //Facebook Post
        String sFBToken="";
        String sFBText="";
        
         //User Multi Cluster   
        String sMultiClusterName="";
        String sMultiClusterUser="";
        String sMultiClusterPassword="";
        String sMultiClusterID="";

        boolean bPasswordValid = false;
        boolean bRedirect = false;
        String sRedirectURI = "";
        boolean bWindows = false;
        boolean bLinux = false;
        boolean bMobile = false;
        boolean bIpad = false;
        boolean bIphone = false;
        boolean bAndroid = false;
        boolean bJakarta = false;
        String sRemote = "";
        boolean bAuth = false;
        String sAuthUUID = "";
        boolean bUserAuthenticated = false;
        
       

      

        String sBoxUser = "";
        String sKeyPassword = "";
        String sIV = "";
        String sEncData = "";
        
        String uuid = null;
        String cluster = "";
        boolean aesEncryptSession = false;
        int aesSizeSession = 0;
        
        String sGetFileExt = "";
        String sFileExt = "";
        String sGetFileName = "";
        bRedirectBulker = false;
       
        //Agregados para no usar redirectbolker porque usa variables globales que pueden ser modificadas
        //al atender otra solicitud.
        boolean b2Redirect = false;
        String s2RedirectURL="";
                
        WebFuncs wf = new utils.WebFuncs("");

        String sNamer = "";
        
        int filechunk_offset = 0;
        int filechunk_size = 0;
        
        //p("--handleclient()----------------------");

        InputStream is = new BufferedInputStream(s.getInputStream());
        PrintStream ps = new PrintStream(s.getOutputStream());
        /* we will only block in read for this many milliseconds
         * before we fail with java.io.InterruptedIOException,
         * at which point we will abandon the connection.
         */

        //p("entered loop1");
        s.setSoTimeout(WebServer.timeout);
        p("[handleclient()] before tcpnodelay");
        //s.setTcpNoDelay(true);
        p("[handleclient()] after tcpnodelay");
        /* zero out the buffer from last time */
        for (int i = 0; i < BUF_SIZE; i++) {
            buf[i] = 0;
        }
        try {
            /* We only support HTTP GET/HEAD, and don't
             * support any fancy HTTP options,
             * so we're only interested really in
             * the first line.
             */

            int nread = 0, r = 0;
            int offset = 0;
            int nBytes = 0;

            boolean keepread = true;
            
            int count=10;
            while(is.available()<=0 && count>0){
                Thread.sleep(1000);
                count--;
            }
            
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            while (keepread) {
                try {
                    
                    nBytes = is.read(buf,0,BUF_SIZE);
                    //p("bytes read = " + nBytes);
                    if (nBytes < 0){
                        keepread = false;
                    }else{
                        out.write(buf,0 , nBytes);
                    }
                   
                    if (keepread) {
                        nread += nBytes;

                        //p("new offset = " + nread);
                        if (buf[0] == (byte)'G' &&
                            buf[1] == (byte)'E' &&
                            buf[2] == (byte)'T' &&
                            buf[3] == (byte)' ') {
                            keepread = false;
                        }                        
                    }

                } catch (Exception e) {
                    p("exception.1*");
                    e.printStackTrace();
                    p(e.getMessage());
                    keepread = false;
                    p("sizeof buf[] #2" + buf.length);
                }
            }
            
            buf= out.toByteArray();
            out.close();
            
            if (nread < 0) {
                p("nread = " + nread + " . Setting to 0.");
                nread = 0;
            }
            
            char[] data = new char[nread];
            
            //System.out.print("buffer: '");
            for (int i=0;i<nread;i++) {
                //System.out.print((char)buf[i]);
                data[i] = (char)buf[i];
            }
            String text = String.valueOf(data);
            
            //p("buffer = " + text);
            boolean bFull = true;
            
            String delim = "\r\n";
            String sFileRange = "";
            int nFileStart = 0;
            int nFileEnd = 0;
            StringTokenizer st2 = new StringTokenizer(text, delim);
            while (st2.hasMoreTokens()) {
                String text2 = st2.nextToken();
                //p("token: '" + text2 + "'");
                if (text2.contains("Range:")) {
                        int npos = text2.indexOf("bytes=");
                        String text3 = text2.substring(npos+6,text2.length());
                        p("Partial range detected: '" + text3 + "'");
                        sFileRange = text3;
                        delim = "-";
                        StringTokenizer st3 = new StringTokenizer(text3, delim);
                        if (st3.countTokens() > 1) {
                            bFull = false;
                            nFileStart = Integer.parseInt(st3.nextToken());
                            p("start: '" + nFileStart + "'");
                            nFileEnd = Integer.parseInt(st3.nextToken());
                            p("nFileEnd: '" + nFileEnd + "'");                            
                        } else {
                            p("Full Range detected: '" + text3 + "'");
                        }
   
                }
               
                if (text2.contains("User-Agent:")) {
                    //p("agent: '" + text2 + "'");
                    bWindows = false;
                    bLinux=false;
                    if(text2.contains("Linux")){
                        bLinux=true;
                    }
                    else if (!text2.contains("Macintosh") ) {
                        bWindows = true;
                    } 
                    p(text2);
                    if(text2.contains("iPhone") || text2.contains("iPad") || text2.contains("Android") || text2.contains("Jakarta")){
                         bMobile = true;
                         if (text2.contains("iPad")) bIpad = true;
                         if (text2.contains("iPhone") && !bIpad) bIphone = true;
                         if (text2.contains("Android")) bAndroid = true;
                         if (text2.contains("Jakarta")) bJakarta = true;
                    }
                    
                }
                
                if (text2.contains("Cookie:") && text2.contains("uuid")) {
                    p("Cookie values = '" + text2 + "'");
                    String delim3 = " ;";
                    StringTokenizer s3 = new StringTokenizer(text2, delim3);
                    while (s3.hasMoreTokens()) {
                        String s3b = s3.nextToken();
                        //p("cookie = " + s3b);
                        if (s3b.startsWith("uuid=")) {
                            sAuthUUID = s3b.substring(5, s3b.length());
                            //p("Auth UUID = " + sAuthUUID);                            
                            if (isUUIDValid(sAuthUUID)) {
                                bUserAuthenticated = true;
                            } else {
                                //bUserAuthenticated = true;
                                p("WARNING: Invalid UUID detected.");
                            }
                        }
                    }
                    
                }
                
                
            }
            
            
            
            /* are we doing a GET or just a HEAD */
            boolean doingGet = false;
            boolean doingPost = false;
            /* beginning of file name */
            int index = 0;
            if (buf.length > 0) {
                    if (buf[0] == (byte)'G' &&
                        buf[1] == (byte)'E' &&
                        buf[2] == (byte)'T' &&
                        buf[3] == (byte)' ') {
                        doingGet = true;
                        index = 4;
                    } else if (buf[0] == (byte)'H' &&
                               buf[1] == (byte)'E' &&
                               buf[2] == (byte)'A' &&
                               buf[3] == (byte)'D' &&
                               buf[4] == (byte)' ') {
                        doingGet = false;
                        index = 5;
                    } else if (buf[0] == (byte)'P' &&
                               buf[1] == (byte)'O' &&
                               buf[2] == (byte)'S' &&
                               buf[3] == (byte)'T' &&
                               buf[4] == (byte)' ') {
                        doingPost = true;
                        index = 5;
                    } else {
                        /* we don't support this method */
                        ps.print("HTTP/1.0 " + HTTP_BAD_METHOD +
                                   " unsupported method type: ");
                        ps.write(buf, 0, 5);
                        ps.write(EOL);
                        ps.flush();
                        s.close();
                        return;
                    }                                
            }
            

            //p("getpost = " + doingPost);
            //p("getget = " + doingGet);

            int i = 0;
            /* find the file name, from:
             * GET /foo/bar.html HTTP/1.0
             * extract "/foo/bar.html"
             */
            for (i = index; i < nread; i++) {
                if (buf[i] == (byte)' ') {
                    break;
                }
            }
            String fname = (new String(buf, 0, index,
                      i-index)).replace('/', File.separatorChar);
            
            
            
            if (fname.startsWith(File.separator)) {
                fname = fname.substring(1);
            }


            //File targ = new File(WebServer.root, fname);
            boolean vaultMode = false;
            boolean genFile = false;

            File targ= null;
            if (doingGet) {
                //p("doingGet = true");
                p("incoming: '" + fname + "'");
                
                //String sFoo = "";
                //String sHash = "";
                boolean bProcess = false;


                if (fname.contains("fileexist.fn")||fname.contains("cass") && !fname.contains("cassvault")) {
                    
                    if (!fname.contains(".") && !fname.contains("backup")) { 
                        String fname2 = fname.substring(5,fname.length());
                        p("fname2: '" + fname2 + "'");   
                        byte[] s2 = Base64.decode(fname2.toCharArray());
                        sPathDec = new String(s2);
                        p("req_decrypted: '" + sPathDec + "'");                            
                    } else {
                        fname = root + File.separator + fname;
                        int nPos = fname.indexOf("?");
    //                    int nPos2 = fname.indexOf("&");
    //                    if (nPos2 < 0) {
    //                        nPos2 = fname.length();
    //                    }
                        sPathDec = fname;
                        if (nPos > 0) {
                            sPathDec = fname.substring(0, nPos);
    //                        sFoo = fname.substring(nPos+5,nPos2);
    //                        p("sfoo = '" + sFoo + "'");
    //                        p("len = '" + fname.length() + "'");
    //                        if (nPos2 < fname.length()) {
    //                            sHash = fname.substring(nPos2 + 6, fname.length());
    //                            p("hash = '" + sHash + "'");
    //                        }
                        }
                        
                            
                        
                        if (fname.contains("login.fn") ||
                                fname.contains("query.fn") ||
                                fname.contains("getfile.fn") ||
                                fname.contains("applytags.fn") ||
                                fname.contains("suggest.fn") ||
                                fname.contains("echoClient5.htm") ||
                                fname.contains("viewimg2.htm") ||
                                fname.contains("sendimg2.htm") ||
                                fname.contains("welcome.htm") ||
                                fname.contains("about.htm") ||
                                fname.contains("shares.htm") ||
                                fname.contains("eula.htm") ||
                                fname.contains("bulker.php") ||
                                fname.contains("setup.htm")) {
                            String sName = fname;  
                            if (fname.contains("?")) sName = fname.substring(0,fname.indexOf("?"));
                            String sType = "";
                            if (bAWSHosted) {
                                sType = "aws";
                                if (bMobile) {
                                    sType = "aws_mobile";
                                    if (bIpad) sType = "aws_ipad";
                                    if (bIphone) sType = "aws_iphone";
                                    if (bAndroid) sType = "aws_android";
                                    if (bJakarta) sType = "aws_jakarta";
                                }                               
                            } else {
                                if (bWindows) {
                                    sType = "win";
                                } else {
                                    if (bLinux) {
                                        sType = "linux";
                                    } else {
                                        sType = "mac";
                                    }
                                }                                
                                if (bMobile) sType = "mobile";
                                if (bIpad) sType = "ipad";
                                if (bIphone) sType = "iphone";
                                if (bAndroid) sType = "android";
                                if (bJakarta) sType = "jakarta";
                            }                        
                           
                            String sNameSep = "/";
                            if (sName.contains("\\")) sNameSep = "\\";
                            
                            if (bAnalyticsGA) {
                                String sNameGA = sName.substring(sName.lastIndexOf(sNameSep)+1, sName.length()); 
                                if (gaUUID == null) gaUUID = UUID.randomUUID();
                                int res = StatHat.ezPostCount_GA("1", sAnalyticsKeyGA, gaUUID.toString(), "event", sType, "page", sNameGA, "1", "Alterante");                                                             
                            }
                            if (bAnalyticsSH) {
                                String sNameSH = sType + "_" + sName.substring(sName.lastIndexOf(sNameSep)+1, sName.length());
                                StatHat.ezPostCount(sAnalyticsKeySH, sNameSH, 1.0);                                
                            }
                        }
                        
                        if (fname.contains(".php") || 
                                fname.contains(".fn") ||
                                fname.contains("nodestats.htm") ||
                                fname.contains("shares.htm") ||
                                fname.contains("echoClient5.htm") ||
                                fname.contains("getconfig.htm") ||                                
                                fname.contains("setconfig.htm") ||                                
                                fname.contains("auth.htm") ||  
                                fname.contains("setup.htm") ||
                                fname.contains("about.htm") ||
                                fname.contains("welcome.htm")||
                                fname.contains("viewimg2.htm") ||
                                fname.contains("sendimg2.htm") ||
                                fname.contains("fileexist.fn")||
                                fname.contains("initscan.fn")||
                                fname.contains("startscan.fn")||
                                fname.contains("getfolders.fn")||
                                fname.contains("getfolders-json.fn")||
                                fname.contains("getfolders_json.fn")||
                                fname.contains("getscannews.fn")||
                                fname.contains("getscannewsack.fn")||
                                fname.contains("getscanmode.fn") ||
                                fname.contains("query.fn") ||                                
                                fname.contains("openfile.htm") ||
                                fname.contains("sendfile.htm")||
                                fname.contains("openfolder.htm")||
                                fname.contains("openfolder.fn")||
                                fname.contains("navbar2")||
                                fname.contains("eula.htm")||
                                fname.contains("getvideo.m3u8")||
                                fname.contains("getts.fn")||
                                fname.contains("nodeinfo.fn")||
                                fname.contains("invitation_webapp.fn")||
                                fname.contains("index.htm")){
                            genFile = true;
                        }
                    }
                } else {
                    if (fname.contains("cassvault")) {
                        p("*** VAULT MODE **** file rquested: '" + fname + "'"); 
                        
                        String delimiters = "\\/";
                        StringTokenizer st = new StringTokenizer(fname.trim(), delimiters, true);
                        String w = "";
                        while (st.hasMoreTokens()) {
                            w = st.nextToken();
                        }
                        sPathDec = w;                      
                        p("*** VAULT MODE **** trimmed filename: '" + w + "'"); 
                        
                        vaultMode = true;
                    } else {
                        p("fname = '" + fname + "'");
                        String fname2 = fname.substring(0,fname.indexOf(".")>0?fname.indexOf("."):0);
                        p("fname2 = '" + fname2 + "'");
                        if (fname.equals("favicon.ico")) {
                            sPathDec = root + File.separator + "cass" + File.separator + fname;
                        } else {
                            fname2 = fname2.replace("\\","/");
                            String fname3 = URLDecoder.decode(fname2, "UTF-8");
                            p("fname3 = '" + fname3 + "'");
                            byte[] s2 = Base64.decode(fname3.toCharArray());                           
                            String sPathDec_test = new String(s2);                            
                            p("block decode: " + sPathDec_test);
                            if (CheckPathValid(sPathDec_test)) {
                                String sCaller = s.getInetAddress().getHostAddress();
                                p("scaller = " + sCaller);
                                String sMode = GetConfig("mode", appendage + "../scrubber/config/" + "www-rtbackup.properties");           
                                if (sMode.equals("server")) {
                                    if (CheckIPValid(sCaller)) {
                                        sPathDec = sPathDec_test;                                    
                                    } else {
                                        sPathDec = "invalid1";
                                    }                                    
                                } else {
                                    if (CheckIPValid_Server(sCaller)) {
                                        sPathDec = sPathDec_test;
                                    } else {
                                        sPathDec = "invalid1b";
                                    }
                                }
                            } else {
                                sPathDec = "invalid2";
                            }
                        }
                        p("req_decrypted: '" + sPathDec + "'");                        
                    }
                }

                //p("Process tokens.");
                
                //p("fname = " + fname + "'");
                String delimiters = "?&";
                StringTokenizer st = new StringTokenizer(fname.trim(), delimiters, true);
                Integer nTokens = st.countTokens();
                String sFoo2 = "";
                String sHash2 = "";
                String sPort = "";
                String sIPAddress = "";
                String sBackup = "";
                String sFree = "";
                String sUUID = "";
                String sView = "";
                String sFileType = "";
                String sNumObj = "50"; //default to 50 objects
                String sNumCol = "1"; //default to 1 row
                String sPassword = "";
                String sPID = "0";
                String sDaysBack = "";
                String sDateStart = "";
                String sMailFromURL = "";
                String sMailFromPasswordURL = "";
                String sMailTo = "";
                String sMailMessage = "";
                String sMailSubject = "";
                String sMailFile = "";
                String sMD5 = "";
                String sTS = "";
                String sBatchID = "";
                String sScreenSize = "1000";
                String sPage = "0";
                String previousDate = "";
                
                //String sBoxUser = "";
                String sBoxPassword = "";
                
                String cSetupPage = "";
                String sSetupPage = "";
                
                String sLastSeq = "";
                String sLastBatch = "";
                String sSync = "";
                //String sNamer = "";
                String sFileName = "";
                String sFile = "";
                String sFolder="";
                String sFolderSel="";
                String sBlacklist="";
                
                String shareType = "";
                String shareKey = "";
                String shareUsers = "";
                boolean shareHtml = false;
                String shareOpenmodal = "false";
                boolean debug = false;
                
                String clientNettyPort = "";
                String sMachine = "";

                String sSortOrder = "";
                
                sBackupMode1 = "no"; 
                sBackupMode2 = "no"; 
                sBackupMode3 = "no"; 
                sBackupMode4 = "no"; 
                
                sSyncMode1 = "no"; 
                sSyncMode2 = "no"; 
                sSyncMode3 = "no"; 
                sSyncMode4 = "no"; 
                
                sMailAllowMail = "no";
                sMailSendMail = "no";
                sMailScanMail = "no";
                sMailNotify = "no";
                
                String analytics = "";
                String code = "";
                String drive_amazon = "";
                
                boolean bLegacyUI = false;
                                           
                //p("----begin URL tokens ----");
                while (st.hasMoreTokens()) {
                    //nTokens = nTokens + 1;

                    String w = st.nextToken();
                    //p("token: " + w);
                    if (w.contains("foo")) {
                        String sFoo2tmp = w.substring(4,w.length());
                        p("foo: " + sFoo2tmp);
                        sFoo2 = sFoo2tmp;
                        sFoo2 = sFoo2.replace("%F1", "ñ"); //0241
                        sFoo2 = sFoo2.replace("%E1", "á"); //0225
                        sFoo2 = sFoo2.replace("%E9", "é"); //0233
                        sFoo2 = sFoo2.replace("%ED", "í"); //0237
                        sFoo2 = sFoo2.replace("%F3", "ó"); //0243
                        sFoo2 = sFoo2.replace("%FA", "ú"); //0250
                        
                        sFoo2 = sFoo2.replace("%D1", "Ñ"); //0209
                        sFoo2 = sFoo2.replace("%C1", "Á"); //0193
                        sFoo2 = sFoo2.replace("%C9", "É"); //0201
                        sFoo2 = sFoo2.replace("%CD", "Í"); //0205
                        sFoo2 = sFoo2.replace("%D3", "Ó"); //0211
                        sFoo2 = sFoo2.replace("%AA", "Ú"); //0218
                        p("foo2: " + sFoo2);                        
                    }
                    if (w.contains("hash")) {
                        sHash2 = w.substring(5,w.length());
                        p("hash: " + sHash2);
                    }
                    if (w.contains("uuid")) {
                        sUUID = w.substring(5,w.length());
                        p("uuid: " + sUUID);
                        if(sAuthUUID==null || sAuthUUID.trim().length()==0){
                            sAuthUUID=sUUID;
                        }
                    }
                    if (w.contains("port")) {
                        sPort = w.substring(5,w.length());
                        p("port: " + sPort);
                    }
                    if (w.contains("ipaddress")) {
                        sIPAddress = w.substring(10,w.length());
                        p("ipaddress: " + sIPAddress);
                    }
                    if (w.contains("backup")) {
                        sBackup = w.substring(7,w.length());
                        //p("backup: " + sBackup);
                    }
                    if (w.contains("free")) {
                        sFree = w.substring(5,w.length());
                        p("free: " + sFree);
                    }
                    if (w.contains("view")) {
                        sView = w.substring(5,w.length());
                        p("view: " + sView);
                    }
                    if (w.contains("numobj")) {
                        sNumObj = w.substring(7,w.length());
                        p("numobj: " + sNumObj);
                    }
                    if (w.contains("ftype")) {
                        sFileType = w.substring(6,w.length());
                        p("ftype: " + sFileType);
                    }
                    if (w.contains("screenSize")) {
                        sScreenSize = w.substring(11,w.length());
                        p("screenSize: " + sScreenSize);
                    }
                    if (w.contains("page")) {
                        sPage = w.substring(5,w.length());
                        p("sPage: " + sPage);
                    }
                    if (w.contains("previousDate")) {
                        previousDate = w.substring(13,w.length());
                        p("previousDate: " + previousDate);
                    }
                    if (w.contains("numcol")) {
                        sNumCol = w.substring(7,w.length());
                        p("sNumCol: " + sFileType);
                    }                    
                    if (w.contains("pw")) {
                        sPassword = w.substring(3,w.length());
                        p("pw: " + sPassword);
                    }
                    if (w.contains("pid")) {
                        sPID = w.substring(4,w.length());
                        p("pid: " + sPID);
                    }
                    if (w.contains("rem")) {
                        sRemote = w.substring(4,w.length());
                        p("rem: " + sRemote);
                    }
                    if (w.contains("days")) {
                        sDaysBack = w.substring(5,w.length());
                        p("days: " + sDaysBack);
                    }
                    if (w.contains("date")) {
                        //if the postback is from the sidebar (it contains submit), the start date should be reset
                        if (!fname.contains("submit")) {
                            sDateStart = w.substring(5,w.length());
                        }
                        p("date: " + sDateStart);
                    }
                    if (w.contains("sendtoemails")) {
                        sMailTo = w.substring(13,w.length());
                        p("mailto: " + sMailTo);
                    }
                    if (w.contains("mailfrom")) {
                        sMailFromURL = w.substring(9,w.length());
                        p("mailfrom: " + sMailFrom);
                    }
                    if (w.contains("mailpass")) {
                        sMailFromPasswordURL = w.substring(9,w.length());
                        p("mailpass: " + sMailFromPassword);
                    }
                    if (w.contains("msubject")) {
                        sMailSubject = w.substring(9,w.length());
                        p("msubject: " + sMailSubject);
                    }
                    if (w.contains("mmsg")) {
                        sMailMessage = w.substring(5,w.length());
                        p("mmsg: " + sMailMessage);
                    }
                    if (w.contains("mfile")) {
                        sMailFile = w.substring(6,w.length());
                        p("mfile: " + sMailFile);
                    }
                    
                    if (w.contains("encdata")) {
                        sEncData = w.substring(8,w.length());
                        p("encdata: " + sEncData);
                        //sEncData = URLDecoder.decode(sEncData,"UTF-8");
                        sEncData = sEncData.replace("%21", "!");
                        sEncData = sEncData.replace("%23", "#");
                        sEncData = sEncData.replace("%24", "$");
                        sEncData = sEncData.replace("%26", "&");
                        sEncData = sEncData.replace("%27", "'");
                        sEncData = sEncData.replace("%28", "(");
                        sEncData = sEncData.replace("%29", ")");
                        sEncData = sEncData.replace("%2A", "*");
                        sEncData = sEncData.replace("%2B", "+");
                        sEncData = sEncData.replace("%2C", ",");
                        sEncData = sEncData.replace("%2F", "/");
                        sEncData = sEncData.replace("%3A", ":");
                        sEncData = sEncData.replace("%3B", ";");
                        sEncData = sEncData.replace("%3D", "=");
                        sEncData = sEncData.replace("%3F", "?");
                        sEncData = sEncData.replace("%40", "@");
                        sEncData = sEncData.replace("%5B", "[");
                        sEncData = sEncData.replace("%5D", "]");
                        p("encdata(decoded): " + sEncData);
                        p("length encdata: " + sEncData.length());
                    }
                    if (w.contains("boxuser")) {
                        sBoxUser = w.substring(8,w.length());
                        p("boxuser: " + sBoxUser);
                        sBoxUser = URLDecoder.decode(sBoxUser,"UTF-8");
                    }
                    if (w.contains("sFileExt")) {
                        sFileExt = w.substring(9,w.length());
                        p("sExt: " + sFileExt);
                        sFileExt = URLDecoder.decode(sFileExt,"UTF-8");
                    }

                    if (w.contains("boxpass")) {
                        sBoxPassword = w.substring(8,w.length());
                        p("boxpass: " + sBoxPassword);
                        sBoxPassword = URLDecoder.decode(sBoxPassword,"UTF-8");
                    }
                    
                    if (w.contains("passwordkey")) {
                        sKeyPassword = w.substring(12,w.length());
                        p("sKeyPassword: " + sKeyPassword);
                        sKeyPassword = URLDecoder.decode(sKeyPassword,"UTF-8");
                    }
                    
                    if (w.contains("iv=")) {
                        sIV = w.substring(3,w.length());
                        p("sIV: " + sIV);
                        sIV = URLDecoder.decode(sIV,"UTF-8");
                    }

                    if (w.contains("mfile")) {
                        sMailFile = w.substring(6,w.length());
                        p("mfile: " + sMailFile);
                    }
                    
                    if (w.contains("spage")) {
                        sSetupPage = w.substring(6,w.length());
                        p("spage: " + sSetupPage);
                        
                    }
                    if (w.contains("cpage")) {
                        cSetupPage = w.substring(6,w.length());
                        p("spage: " + cSetupPage);
                        
                    }
                    if (w.contains("sync")) {
                        sSync = w.substring(5,w.length());
                        p("sync: " + sSync);
                    }
                    if (w.contains("lastbat")) {
                        sLastBatch = w.substring(8,w.length());
                        p("lastbat: " + sLastBatch);
                    }
                    if (w.contains("lastseq")) {
                        sLastSeq = w.substring(8,w.length());
                        p("lastseq: " + sLastSeq);
                    }

                    if (w.contains("mode1")) {
                        sMode = w.substring(6,w.length());
                        p("mode: " + sMode);
                    }

                    if (w.contains("sFolder")) {
                        String sTmp = w.substring(8,w.length());                       
                        sFolder = URLDecoder.decode(sTmp, "UTF-8");
                        p("sFolder: " + sFolder);
                    }
                     if (w.contains("bFolderSel")) {
                        String sTmp = w.substring(11,w.length());                       
                        sFolderSel = URLDecoder.decode(sTmp, "UTF-8");
                        p("sFolderSel: " + sFolderSel);
                    }
                     
                    if (w.contains("blacklist")) {
                        String sTmp = w.substring(10,w.length());                       
                        sBlacklist = URLDecoder.decode(sTmp, "UTF-8");
                        p("sBlacklist: " + sBlacklist);
                    }

                    if (w.contains("scan1")) {
                        String sTmp = w.substring(6,w.length());                       
                        sScanDirectory11 = URLDecoder.decode(sTmp, "UTF-8");
                        p("scan1: " + sScanDirectory11);
                    }

                    

                    if (w.contains("scan2")) {
                         String sTmp = w.substring(6,w.length());                       
                        sScanDirectory21 = URLDecoder.decode(sTmp, "UTF-8");
                        p("scan1: " + sScanDirectory21);
                       
                    }

                  if (w.contains("scan3")) {
                         String sTmp = w.substring(6,w.length());                       
                        sScanDirectory31 = URLDecoder.decode(sTmp, "UTF-8");
                        p("scan3: " + sScanDirectory31);
                       
                    }

                                                      
                    if (w.contains("scan4")) {
                         String sTmp = w.substring(6,w.length());                       
                        sScanDirectory41 = URLDecoder.decode(sTmp, "UTF-8");
                        p("scan4: " + sScanDirectory41);
                       
                    }
                    

                    if (w.contains("backuppath1")) {
                        String sTmp = w.substring(12,w.length());                       
                        sBackupDirectory = URLDecoder.decode(sTmp, "UTF-8");
                       
                        p("backuppath1: " + sBackupDirectory);
                    }

                    if (w.contains("backuppath2")) {
                        String sTmp = w.substring(12,w.length());                       
                        sBackupDirectory2 = URLDecoder.decode(sTmp, "UTF-8");
                        
                        p("backuppath2: " + sBackupDirectory2);
                    }

                    if (w.contains("backuppath3")) {
                        String sTmp = w.substring(12,w.length());                       
                        sBackupDirectory3 = URLDecoder.decode(sTmp, "UTF-8");
                        
                        p("backuppath3: " + sBackupDirectory3);
                    }

                    if (w.contains("backuppath4")) {
                        String sTmp = w.substring(12,w.length());                       
                        sBackupDirectory4 = URLDecoder.decode(sTmp, "UTF-8");
                        
                        p("backuppath4: " + sBackupDirectory4);
                    }
                    
                    //
                    if (w.contains("sncpath1")) {
                        String sTmp = w.substring(9,w.length());                       
                        sSyncDirectory1 = URLDecoder.decode(sTmp, "UTF-8");
                       
                        p("sncpath1: " + sSyncDirectory1);
                    }

                    if (w.contains("sncpath2")) {
                        String sTmp = w.substring(9,w.length());                       
                        sSyncDirectory2 = URLDecoder.decode(sTmp, "UTF-8");
                        
                        p("sncpath2: " + sSyncDirectory2);
                    }

                    if (w.contains("sncpath3")) {
                        String sTmp = w.substring(9,w.length());                       
                        sSyncDirectory3 = URLDecoder.decode(sTmp, "UTF-8");
                         
                        p("sncpath3: " + sSyncDirectory3);
                    }

                    if (w.contains("sncpath4")) {
                        String sTmp = w.substring(9,w.length());                       
                        sSyncDirectory4 = URLDecoder.decode(sTmp, "UTF-8");
                        
                        p("sncpath4: " + sSyncDirectory4);
                    }

                    //
                    
                    
                    if (w.contains("backupnode1")) {
                        String sTmp = w.substring(12,w.length());                       
                        sBackupMode1 = URLDecoder.decode(sTmp, "UTF-8");
                        p("backupnode1: " + sBackupMode1);
                    }                     
                    
                    if (w.contains("backupnode2")) {
                        String sTmp = w.substring(12,w.length());                       
                        sBackupMode2 = URLDecoder.decode(sTmp, "UTF-8");
                        p("backupnode2: " + sBackupMode2);
                    }                           
                    
                    if (w.contains("backupnode3")) {
                        String sTmp = w.substring(12,w.length());                       
                        sBackupMode3 = URLDecoder.decode(sTmp, "UTF-8");
                        p("backupmode3: " + sBackupMode3);
                    }                     
                    
                    if (w.contains("backupnode4")) {
                        String sTmp = w.substring(12,w.length());                       
                        sBackupMode4 = URLDecoder.decode(sTmp, "UTF-8");
                        p("backupmode4: " + sBackupMode4);
                    } 
                    
                    if (w.contains("sncnode1")) {
                        String sTmp = w.substring(9,w.length());                       
                        sSyncMode1 = URLDecoder.decode(sTmp, "UTF-8");
                        p("syncnode1: " + sSyncMode1);
                    }                     

                    if (w.contains("sncnode2")) {
                        String sTmp = w.substring(9,w.length());                       
                        sSyncMode2 = URLDecoder.decode(sTmp, "UTF-8");
                        p("syncnode2: " + sSyncMode2);
                    }                   

                    if (w.contains("sncnode3")) {
                        String sTmp = w.substring(9,w.length());                       
                        sSyncMode3 = URLDecoder.decode(sTmp, "UTF-8");
                        p("syncnode3: " + sSyncMode3);
                    }                     

                    if (w.contains("sncnode4")) {
                        String sTmp = w.substring(9,w.length());                       
                        sSyncMode4 = URLDecoder.decode(sTmp, "UTF-8");
                        p("syncnode4: " + sSyncMode4);
                    }                                                         

                    if (w.contains("scannode1")) {
                        String sTmp = w.substring(10,w.length());                       
                        sScanMode = URLDecoder.decode(sTmp, "UTF-8");
                        p("scannode1: " + sScanMode);
                    }
                    
                    if (w.contains("scannode2")) {
                        String sTmp = w.substring(10,w.length());                       
                        sScanMode2 = URLDecoder.decode(sTmp, "UTF-8");
                        p("scannode2: " + sScanMode2);
                    }
                    
                    if (w.contains("scannode3")) {
                        String sTmp = w.substring(10,w.length());                       
                        sScanMode3 = URLDecoder.decode(sTmp, "UTF-8");
                        p("scannode3: " + sScanMode3);
                    }

                    if (w.contains("scannode4")) {
                        String sTmp = w.substring(10,w.length());                       
                        sScanMode4 = URLDecoder.decode(sTmp, "UTF-8");
                        p("scannode4: " + sScanMode4);
                    }



                    if (w.contains("smtphost")) {
                        String sTmp = w.substring(9,w.length());                       
                        sMailHost = URLDecoder.decode(sTmp, "UTF-8");
                        p("smtphost: " + sMailHost);
                    }
                    
                    if (w.contains("smtpprt")) {
                        String sTmp = w.substring(8,w.length());                       
                        sMailPort = URLDecoder.decode(sTmp, "UTF-8");
                        p("smtpport: " + sMailPort);
                    }

                    if (w.contains("pop3user")) {
                        String sTmp = w.substring(9,w.length());                       
                        sMailFrom = URLDecoder.decode(sTmp, "UTF-8");
                        p("pop3user: " + sMailFrom);
                    }
                    
                    if (w.contains("pop3pass")) {
                        String sTmp = w.substring(9,w.length());
                        if(!sTmp.isEmpty()){
                            sMailFromPassword = URLDecoder.decode(sTmp, "UTF-8");
                            p("pop3pass: " + sMailFromPassword);
                        }
                    }
                    
                    if (w.contains("pop3host")) {
                        String sTmp = w.substring(9,w.length());                       
                        sMailHostPOP = URLDecoder.decode(sTmp, "UTF-8");
                        p("pop3host: " + sMailHostPOP);
                    }
                    
                    if (w.contains("pop3prt")) {
                        String sTmp = w.substring(8,w.length());                       
                        sMailPortPOP = URLDecoder.decode(sTmp, "UTF-8");
                        p("pop3port: " + sMailPortPOP);
                    }
                    
                    if (w.contains("allowmail")) {
                        String sTmp = w.substring(10,w.length());                       
                        sMailAllowMail = URLDecoder.decode(sTmp, "UTF-8");
                        p("allowmail: " + sMailAllowMail);
                    }     
                    if (w.contains("sendmail")) {
                        String sTmp = w.substring(9,w.length());                       
                        sMailSendMail = URLDecoder.decode(sTmp, "UTF-8");
                        p("sendmail: " + sMailSendMail);
                    }
                     if (w.contains("scanmail")) {
                        String sTmp = w.substring(9,w.length());                       
                        sMailScanMail = URLDecoder.decode(sTmp, "UTF-8");
                        p("scanmail: " + sMailScanMail);
                    }
                    if (w.contains("notifymail")) {
                        String sTmp = w.substring(11,w.length());                       
                        sMailNotify = URLDecoder.decode(sTmp, "UTF-8");
                        p("notifymail: " + sMailNotify);
                    }
                     if (w.contains("sNamer")) {
                        String sTmp = w.substring(7,w.length());                       
                        sNamer = URLDecoder.decode(sTmp, "UTF-8");
                        p("sNamer: " + sNamer);
                    }
                      if (w.contains("sFileName")) {
                        String sTmp = w.substring(10,w.length());                       
                        try {
                            sFileName = URLDecoder.decode(sTmp, "UTF-8");
                        } catch (Exception e) {
                            e.printStackTrace();
                            sFileName = sTmp;
                        }
                        p("sFileName: " + sFileName);
                    }
                    
                    if (w.contains("sfileexist")) {
                        String sTmp = w.substring(11,w.length());                       
                        sFile = URLDecoder.decode(sTmp, "UTF-8");
                        p("sFile: " + sFile);
                    }
 
                    if (w.contains("rfactor")) {
                        String sTmp = w.substring(8,w.length());                       
                        sRepFactor = URLDecoder.decode(sTmp, "UTF-8");
                        p("rfactor: " + sRepFactor);
                    }
                     
                    if (w.contains("isprev")) {
                        String sTmp = w.substring(7,w.length());                       
                        bIsPrevious = Boolean.parseBoolean(sTmp);
                        p("isprev: " + bIsPrevious);
                    } else {
                        bIsPrevious = false;
                    }
                                       
                    if (w.contains("expresssetup")) {
                        String sTmp = w.substring(13,w.length());                       
                        bIsExpressSetup = Boolean.parseBoolean(sTmp);
                        p("expresssetup: " + bIsExpressSetup);
                    } else {
                        bIsExpressSetup = false;
                    }
                    
                    if (w.contains("qaccounts")) {
                        String sTmp = w.substring(10,w.length());                       
                        qAccounts = URLDecoder.decode(sTmp, "UTF-8");
                        p("qAccounts: " + qAccounts);
                    }
                    
                    if (w.contains("sdaccounts")) {
                        String sTmp = w.substring(11,w.length());                       
                        sendAccounts = URLDecoder.decode(sTmp, "UTF-8");
                        p("sendAccounts: " + sendAccounts);
                    }             
                    if (w.contains("mailgroups")) {
                        String sTmp = w.substring(11,w.length());                       
                        mailGroups = URLDecoder.decode(sTmp, "UTF-8");
                        p("mailGroups: " + mailGroups);
                    }
                    if (w.contains("allowpeer")) {
                        String sTmp = w.substring(10,w.length());                       
                        sAllowPeer = URLDecoder.decode(sTmp, "UTF-8");
                        p("allowpeer: " + sAllowPeer);
                    }
                    if (w.contains("allowotherusers")) {
                        String sTmp = w.substring(16,w.length());                       
                        sAllowOtherUsers = URLDecoder.decode(sTmp, "UTF-8");
                        p("allowotherusers: " + sAllowOtherUsers);
                    }
                    
                    if (w.contains("allowremote")) {
                        String sTmp = w.substring(12,w.length());                       
                        sAllowRemote = URLDecoder.decode(sTmp, "UTF-8");
                        p("allowremote: " + sAllowRemote);
                    }
                    
                    if (w.contains("signature")) {
                        String sTmp = w.substring(10,w.length());                       
                        sSignature = URLDecoder.decode(sTmp, "UTF-8");
                        p("signature: " + sSignature);
                    }
                    
                   if (w.contains("adminuser")) {
                        String sTmp = w.substring(10,w.length());                       
                        adminuser = URLDecoder.decode(sTmp, "UTF-8");
                        p("adminuser: " + adminuser);
                    }
                   
                    if (w.contains("adminpw1")) {
                        String sTmp = w.substring(9,w.length());                       
                        adminpw1 = URLDecoder.decode(sTmp, "UTF-8");
                        p("adminpw1: " + adminpw1);
                    }
                    
                                       
                    if (w.contains("useraccounts")) {
                        String sTmp = w.substring(13,w.length());                       
                        useraccounts = URLDecoder.decode(sTmp, "UTF-8");
                        p("useraccounts: " + useraccounts);
                    }
                    
                    
                    if (w.contains("syncrules1")) {
                        String sTmp = w.substring(11,w.length());                       
                        syncrules1 = URLDecoder.decode(sTmp, "UTF-8");
                        p("syncrules1: " + syncrules1);
                    }
                    
                    
                    if (w.contains("syncrules2")) {
                        String sTmp = w.substring(11,w.length());                       
                        syncrules2 = URLDecoder.decode(sTmp, "UTF-8");
                        p("syncrules2: " + syncrules2);
                    }
                    
                    
                    if (w.contains("syncrules3")) {
                        String sTmp = w.substring(11,w.length());                       
                        syncrules3 = URLDecoder.decode(sTmp, "UTF-8");
                        p("syncrules3: " + syncrules3);
                    }
                    
                    
                    if (w.contains("syncrules4")) {
                        String sTmp = w.substring(11,w.length());                       
                        syncrules4 = URLDecoder.decode(sTmp, "UTF-8");
                        p("syncrules4: " + syncrules4);
                    }                    
                    
                    if (w.contains("filename")) {
                        String sTmp = w.substring(9,w.length());                       
                        sFileNameFolder = URLDecoder.decode(sTmp, "UTF-8");
                        p("filename: " + sFileNameFolder);
                    }
                    
                    if (w.contains("sscantreemode")) {
                        String sTmp = w.substring("sscantreemode".length()+1,w.length());                       
                        int scanTreeModeNew=Integer.valueOf(sTmp);
                        if(scanTreeMode==2 && scanTreeModeNew==0){
                            CacheMetadataWeb.getInstance().clearScan();
                        }
                        scanTreeMode=scanTreeModeNew;
                        UpdateConfig("scantreemode", String.valueOf(scanTreeMode), "config/" + "www-server.properties");
                        p("scantreemode: " + scanTreeMode);
                       
                    }
                    
                    if (w.contains("md5")) {
                        String sTmp = w.substring(4,w.length());
                        sMD5 = URLDecoder.decode(sTmp, "UTF-8");
                        p("MD5: " + sMD5);
                    }

                    if (w.startsWith("ts=")) {
                        String sTmp = w.substring(3,w.length());
                        sTS = URLDecoder.decode(sTmp, "UTF-8");
                        p("TS: " + sTS);
                    }

                    if (w.contains("batchid")) {
                        String sTmp = w.substring(8,w.length());
                        sBatchID = URLDecoder.decode(sTmp, "UTF-8");
                        p("batchid: " + sBatchID);
                    }

                    if (w.contains("retries")) {
                        String sTmp = w.substring(8,w.length());
                        retries = URLDecoder.decode(sTmp, "UTF-8");
                        p("retries: " + retries);
                    }

                    if (w.contains("machine")) {
                        String sTmp = w.substring(8,w.length());
                        sMachine = URLDecoder.decode(sTmp, "UTF-8");
                        p("machine: " + sMachine);
                    }

                    if (w.contains("netty")) {
                        String sTmp = w.substring(6,w.length());
                        clientNettyPort = URLDecoder.decode(sTmp, "UTF-8");
                        p("clientNettyPort: " + clientNettyPort);
                    }
                                        
                    if (w.contains("property")) {
                        String sTmp = w.substring(9,w.length());
                        sProperty = URLDecoder.decode(sTmp, "UTF-8");
                        p("sProperty: " + sProperty);
                    }
                    
                    if (w.contains("pvalue")) {
                        String sTmp = w.substring(7,w.length());
                        sPropertyValue = URLDecoder.decode(sTmp, "UTF-8");
                        p("sPropertyValue: " + sPropertyValue);
                    }
                    
                    if (w.contains("fbtoken")) {
                        String sTmp = w.substring(8,w.length());
                        sFBToken = URLDecoder.decode(sTmp, "UTF-8");
                        p("sFBToken: " + sFBToken);
                    }
                    
                    
                    if (w.contains("access_token")) {
                        String sTmp = w.substring(13,w.length());
                        sFBToken = URLDecoder.decode(sTmp, "UTF-8");
                        p("sFBToken: " + sFBToken);
                    }
                    
                    if (w.contains("multiclusteruser")) {
                        String sTmp = w.substring("multiclusteruser".length()+1,w.length());
                        sMultiClusterUser = URLDecoder.decode(sTmp, "UTF-8");
                        p("sMultiClusterUser: " + sMultiClusterUser);
                    }
                    
                    if (w.contains("multiclusterpassword")) {
                        String sTmp = w.substring("multiclusterpassword".length()+1,w.length());
                        sMultiClusterPassword = URLDecoder.decode(sTmp, "UTF-8");
                        p("sMultiClusterPassword length: " + sMultiClusterPassword.length());
                    }
                    
                    if (w.contains("multiclusterid")) {
                        String sTmp = w.substring("multiclusterid".length()+1,w.length());
                        sMultiClusterID = URLDecoder.decode(sTmp, "UTF-8");
                        p("sMultiClusterID: " + sMultiClusterID);
                    }
                    
                    if (w.contains("multiclustername")) {
                        String sTmp = w.substring("multiclustername".length()+1,w.length());
                        sMultiClusterName= URLDecoder.decode(sTmp, "UTF-8");
                        p("sMultiClusterName: " + sMultiClusterName);
                    }
                    
                    
                    
                     if (w.contains("fbtext")) {
                        String sTmp = w.substring(7,w.length());
                        sFBText = URLDecoder.decode(sTmp, "UTF-8");
                        p("sFBText: " + sFBText);
                    }
                    
                    if (w.contains("sharetype")) {
                        String sTmp = w.substring(10,w.length());
                        shareType = URLDecoder.decode(sTmp, "UTF-8");
                        p("shareType: " + shareType);
                    }
                    if (w.contains("sharekey")) {
                        String sTmp = w.substring(9,w.length());
                        shareKey = URLDecoder.decode(sTmp, "UTF-8");
                        p("shareKey: " + shareKey);
                    }
                    if (w.contains("shareusers")) {
                        String sTmp = w.substring(11,w.length());
                        shareUsers = URLDecoder.decode(sTmp, "UTF-8");
                        p("shareUsers: " + shareUsers);
                    }
                    if (w.contains("shareopenmodal")) {
                        String sTmp = w.substring(15,w.length());
                        shareOpenmodal = URLDecoder.decode(sTmp, "UTF-8");
                        p("shareOpenmodal: " + shareOpenmodal);
                    }
                    if (w.contains("sharehtml")) {
                        String sTmp = w.substring(10,w.length());
                        shareHtml = Boolean.valueOf(URLDecoder.decode(sTmp, "UTF-8"));
                        p("shareHtml: " + shareHtml);
                    }
                    if (w.contains("debug")) {
                        String sTmp = w.substring(6,w.length());
                        debug = Boolean.valueOf(URLDecoder.decode(sTmp, "UTF-8"));
                        p("debug: " + debug);
                    }
                    if (w.contains("useremail")) {
                        String sTmp = w.substring(10,w.length());
                        useremail = URLDecoder.decode(sTmp, "UTF-8");
                        p("useremail: " + useremail);
                    }
                    
                    if (w.contains("analytics")) {
                        String sTmp = w.substring(10,w.length());
                        analytics = URLDecoder.decode(sTmp, "UTF-8");
                        p("analytics: " + analytics);
                    }

                    if (w.contains("cluster")) {
                        String sTmp = w.substring(8,w.length());
                        cluster = URLDecoder.decode(sTmp, "UTF-8");
                        p("cluster: " + cluster);
                    }
                    
                    //amazon cloud drive code
                    if (w.contains("code")) {
                        String sTmp = w.substring(5,w.length());
                        code = URLDecoder.decode(sTmp, "UTF-8");
                        p("code: " + code);
                    }
                    
                    if (w.contains("drive_amazon")) {
                        String sTmp = w.substring(13,w.length());
                        drive_amazon = URLDecoder.decode(sTmp, "UTF-8");
                        p("drive_amazon: " + drive_amazon);
                        bDriveAmazonURL = true;
                    } else {
                        bDriveAmazonURL = false;
                    }

                    if (w.contains("order")) {
                        String sTmp = w.substring(6,w.length());
                        sSortOrder = URLDecoder.decode(sTmp, "UTF-8");
                        p("Sort Order[1]: " + sSortOrder);
                    }
                    
                    if (w.contains("uiver")) {
                        String sTmp = w.substring(6,w.length());
                        if (sTmp.equals("1")) {
                            bLegacyUI = true;
                        } else {
                            bLegacyUI = false;
                        }
                        p("bLegacyUI = " + bLegacyUI);
                    }
                    
                    if (w.contains("msg_user")) {
                       String sTmp = w.substring(9,w.length());
                       msg_user = URLDecoder.decode(sTmp, "UTF-8");
                       p("msg_user: " + msg_user);                         
                    }

                    if (w.contains("msg_from")) {
                       String sTmp = w.substring(9,w.length());
                       msg_date = URLDecoder.decode(sTmp, "UTF-8");
                       p("msg_date: " + msg_date);                         
                    }

                    if (w.contains("msg_type")) {
                       String sTmp = w.substring(9,w.length());
                       msg_type = URLDecoder.decode(sTmp, "UTF-8");
                       p("msg_type: " + msg_type);                         
                    }

                    if (w.contains("msg_body")) {
                       String sTmp = w.substring(9,w.length());
                       msg_body = URLDecoder.decode(sTmp, "UTF-8");
                       p("msg_body: " + msg_body);                         
                    }

                    if (w.contains("filechunk_offset")) {
                        String sTmp = w.substring(w.indexOf("=")+1,w.length());
                        filechunk_offset = Integer.parseInt(URLDecoder.decode(sTmp, "UTF-8"));
                        p("filechunk_offset = " + filechunk_offset);
                    } 
                    
                    if (w.contains("filechunk_size")) {
                        String sTmp = w.substring(w.indexOf("=")+1,w.length());
                        filechunk_size = Integer.parseInt(URLDecoder.decode(sTmp, "UTF-8"));
                        p("filechunk_size = " + filechunk_size);
                    }


                }
                //p("----end URL tokens ----");
                //p("sPathDec: '" + sPathDec + "'");
                String sPathDec2 = sPathDec;
                if (sPathDec.indexOf("?") > 0) {
                    sPathDec2 = sPathDec.substring(1,sPathDec.indexOf("?"));
                }
                //p("sPathDec2: '" + sPathDec2 + "'");
                
                if (!bWindowsServer && !sPathDec2.startsWith("/")) {                
                    p("appendage len " + appendage.length());
                    if (appendage.length() == 0)
                        sPathDec2 = "/" + sPathDec2;
                    else 
                        p("not appending, appendage exists.");
                }
                //p("sPathDec3: '" + sPathDec2 + "'");
                
                targ = new File(sPathDec2);
                if (targ.exists()) {
                    //p("FILE EXISTS");
                } else {                    
                    p("FILE NOT EXISTS getAbs() : " + targ.getAbsolutePath());
                    p("FILE NOT EXISTS getCan() : " + targ.getCanonicalPath());
                    p("FILE NOT EXISTS getPath(): " + targ.getPath());
                    p("FILE NOT EXISTS getName(): " + targ.getName());
                }
                
               bPasswordValid = false;
               if (sPassword.equals(password)) {
                   bPasswordValid = true;
               }    

                //p("Sbackupmode1[b]: " + sBackupMode1);

                if (genFile) {
                    p("genFile = true");
                    
                    Random generator = new Random();
                    int roll = generator.nextInt(1000000) + 1;                                      

                    String sFileNew = "";
                    if (appendageRW.length() > 0) {
                        sFileNew = appendageRW + "/tmp/" + targ.getName() + "_" + roll + ".tmp";                        
                    } else {
                        sFileNew = targ.getAbsolutePath() + "_" + roll + ".tmp";                                                                       
                    }
                           
                    //***
                    
                    targ = new File(sFileNew);
                    
                    p("Filename:" + sFileNew);
                    p("Targ: " + targ.getAbsolutePath());
                    
                    new FileOutputStream(sFileNew, false).close();
                    
                    FileOutputStream outFile = new FileOutputStream(sFileNew, false);

                    if (fname.contains("getscannewsack.fn")){
                      
                       for(String path: sFolder.split(";")){
                           path=URLEncoder.encode(path, "UTF-8").replaceAll("\\+", "%20");
                           if(CacheMetadataWeb.getInstance().getScanMap().containsKey(path)){
                               CacheMetadataWeb.getInstance().getScanMap().get(path).setNotificada(true);
                           }
                       }
                    
                    }
                    
                    if (fname.contains("index.htm")){
                        if (bLegacyUI) {
                            File f = new File (root + File.separator + "cass/index_static.htm"); 
                            String sBuffer = loadFileStr(f);
                            String res;
                            String sState = GetConfig("state", "config/www-setup.properties");
                            if (sState.equals("NEW")) {
                                res = "<FRAME SRC=\"setup.htm?spage=7\" id='MAIN' NAME=MAIN1 FRAMESPACING=\"0\" BORDER=\"0\" FRAMEBORDER=\"0\">";
                            }else{
                                res = "<FRAME SRC=\"main_bs.htm\" id='MAIN' NAME=MAIN1 FRAMESPACING=\"0\" BORDER=\"0\" FRAMEBORDER=\"0\">";
                            }
                            sBuffer = sBuffer.replace("***REP1***", res);

                            if(cluster.isEmpty()){
                                res = "<FRAME SRC=\"navbar2_bs.htm\" id=\"NAVBAR\" NAME=NAVBAR FRAMESPACING=\"0\" BORDER=\"0\" FRAMEBORDER=\"0\">";
                            }else{
                                res = "<FRAME SRC=\"navbar2_bs.htm?cluster=" + cluster + "\" id=\"NAVBAR\" NAME=NAVBAR FRAMESPACING=\"0\" BORDER=\"0\" FRAMEBORDER=\"0\">";
                            }
                            sBuffer = sBuffer.replace("***REP2***", res);

                            byte[] kk = sBuffer.getBytes();
                            outFile.write(kk);
                            outFile.close();
                        } else {
                            String sState = GetConfig("state", "config/www-setup.properties");
                            String sIndex = "cass/redir.htm";
                            String res;
                            
                            if (sState.equals("NEW")) {
                                sIndex = "cass/index_static.htm";
                            }
                            File f = new File (root + File.separator + sIndex); 
                            String sBuffer = loadFileStr(f);
                            if (sState.equals("NEW")) {
                                res = "<FRAME SRC=\"setup.htm?spage=7\" id='MAIN' NAME=MAIN1 FRAMESPACING=\"0\" BORDER=\"0\" FRAMEBORDER=\"0\">";                                
                                sBuffer = sBuffer.replace("***REP1***", res);
                                if(cluster.isEmpty()){
                                    res = "<FRAME SRC=\"navbar2_bs.htm\" id=\"NAVBAR\" NAME=NAVBAR FRAMESPACING=\"0\" BORDER=\"0\" FRAMEBORDER=\"0\">";
                                }else{
                                    res = "<FRAME SRC=\"navbar2_bs.htm?cluster=" + cluster + "\" id=\"NAVBAR\" NAME=NAVBAR FRAMESPACING=\"0\" BORDER=\"0\" FRAMEBORDER=\"0\">";
                                }
                                sBuffer = sBuffer.replace("***REP2***", res);
                            }
                            
                            byte[] kk = sBuffer.getBytes();
                            outFile.write(kk);
                            outFile.close();                            
                        }
                        
                     
                    }
                        
// ***************************************************
// Se recuperan las novedades del scan, los directorios que se van completando en el
// scan se informan a la UI para actualizar los datos ya mostrados en pantalla.
// ***************************************************
                    
                    if (fname.contains("getscannews.fn")) {
                        String result="";
                        try{
                           synchronized(CacheMetadataWeb.getInstance().getScanMap()){ 
                            ArrayList<String> paths=new ArrayList<String>();
                            ArrayList<String> pathsRecommended=new ArrayList<String>();
                            if(scanTreeMode==2 || scanTreeMode==3){
                                
                                if(CacheMetadataWeb.getInstance().getScanPathNews().size()>0){
                                    paths.addAll(CacheMetadataWeb.getInstance().getScanPathNews());
                                    // CacheMetadataWeb.getInstance().getScanPathNews().clear();
                                }else{
                                    //CacheMetadataWeb.getInstance().getScanPathNews().wait();
                                    //paths.addAll(CacheMetadataWeb.getInstance().getScanPathNews());
                                }
                                if(CacheMetadataWeb.getInstance().getScanPathRecommended().size()>0){
                                    pathsRecommended.addAll(CacheMetadataWeb.getInstance().getScanPathRecommended());
                                }
                                
                                if(CacheMetadataWeb.getInstance().getScanObject().keySet().size()>0){
                                    ScannerRecursive scanner= CacheMetadataWeb.getInstance().getScanObject().values().iterator().next();
                                    if(scanner.getEjecutanto()){
                                        result+=" setScannerRunOn(); ";
                                    }else{
                                        result+=" setScannerRunOff();";                                        
                                    }
                                    
                                    result+=" setScannerTiming('"+scanner.getTiming()+"');";
                                  
                                    
                                }
                               
                            }else{
                                result="scanoff";
                            }

                            if(pathsRecommended.size()>0){
                                result+=" clearRecommended(); ";
                                
                                
                                for(String pathEnc:pathsRecommended){
                                    if(CacheMetadataWeb.getInstance().getScanMap().containsKey(pathEnc)){
                                         FolderMetaData meta=CacheMetadataWeb.getInstance().getScanMap().get(pathEnc);
                                         for(FolderMetaData.RecommendedMetaData rm:meta.getRecommendedMetaData().values()){
                                             result+=" addRecommended('"+pathEnc+"','"+rm.getSeccion()+"',"+rm.getCount()+"); ";
                                         }
                                    }
                                }
                            }
                            //BufferedWriter  writer=new BufferedWriter(new FileWriter("C:\\Users\\fcarriquiry\\Desktop\\test.txt",true));

                            for(String pathEnc: paths){
                              // writer.write("Scanned:"+pathEnc);
                              // writer.newLine();

                                if(CacheMetadataWeb.getInstance().getScanMap().containsKey(pathEnc)){
                                     FolderMetaData meta=CacheMetadataWeb.getInstance().getScanMap().get(pathEnc);
                                     if(!meta.getNotificada()){

                                        int folders= meta.getFolders();

                                        String path=URLDecoder.decode( meta.getPath(),"UTF-8");
                                        if(!path.endsWith(File.separator)){
                                            path+= File.separator;
                                        }

                                        path=URLEncoder.encode(path,"UTF-8").replaceAll("\\+", "%20");;

                                        File fileExt=new File(appendage + "../scrubber/config/FileExtensions_All.txt");
                                        BufferedReader reader=new BufferedReader(new FileReader(fileExt));
                                        String s=reader.readLine();
                                        StringBuilder objectMetadata=new StringBuilder("");
                                        boolean br=false;
                                        boolean br2=false;

                                        result+=" addMetadataObjectR($.parseJSON('{\"Path\":\""+path+"\",\"Recursive\":\"1\",\"Renderizado\":\"1\",\"Folders\":\""+folders+"\",\"Metadatos\":[] }')); ";

                                        while( s!=null && s.split(",").length>0) 
                                        {
                                            if(s.split(",")[0].equals("@")){
                                                if(br) objectMetadata.append("]}'));  ");
                                                objectMetadata.append(" addMetadata('"+path+"',$.parseJSON('{\"Sec\":\""+s.split(",")[1]+"\",\"Exts\":[");
                                                br2=false;
                                            }else if(s.split(",").length>1){
                                                int countFiles=0;
                                                String ext=s.split(",")[0];
                                                countFiles+=meta.getMetadata().get(ext).getCount();

                                                if(br2)objectMetadata.append(",");
                                                br2=true;
                                                objectMetadata.append("{\"Ext\":\""+s.split(",")[0]+"\",\"Dsc\":\""+s.split(",")[1]+"\",\"Count\":\""+countFiles+"\"}");
                                            }

                                            br=true;
                                            s=reader.readLine();

                                        }
                                        reader.close();
                                        objectMetadata.append("]}'));");

                                        result+= objectMetadata.toString();

                                   }
                                }
                           }
                           
                           // writer.close();
                           }
                        }catch(Throwable th){
                            th.printStackTrace();
                        }
                        
                       outFile.write(result.getBytes());    
                    }
//********************************
//  Retorna el modo de scan tree                    
//********************************                    
              if(fname.contains("getscanmode.fn")){
                  outFile.write(String.valueOf(scanTreeMode).getBytes());
                  outFile.close();
              }      
// ***************************************************
// Se inicializan variables globales para scan
// ***************************************************
             if (fname.contains("initscan.fn")) {
                  CacheMetadataWeb.getInstance().clearScan();
                  outFile.close();
             }

// *******
// shutdown 
// *******

             if (fname.contains("shutdown.fn")) {
                  wf.closeDB();
                  outFile.close();
                  System.exit(0);
             }
       

// ***************************************************
// Se inicia scan del disco duro                    
// ***************************************************
             if (fname.contains("startscan.fn")) {
                    sFolder = URLDecoder.decode(sFolder, "UTF-8");
                    CacheMetadataWeb.getInstance().startScan(sFolder,scanTreeVelocity, scanTreeVariant, scanTreeMode, bWindowsServer);
                    outFile.close();
             }

             
             
// ***************************************************
// fileexist.fn Función que controla si existe un archivo determinado en el nodo
// recive sNamer que es el MD5 y sFileName que es el nombre del archivo que se conoce.
// Esta función sirve para controlar si efectivamente existe un arcvhivo determinado en 
// un nodo determinado.
// ***************************************************
             if (fname.contains("fileexist.fn")) {
                   
                    p("INICIO FN FILEEXIST, file="+sFile);
                    String resFileExist="E,0";
                    if(!bWindowsServer && !sFile.startsWith("/")){
                        sFile="/"+sFile;
                    }
                    File fAux=new File(sFile);
                    if(fAux.exists()){
                        if(fAux.isDirectory()){
                            resFileExist="S,0,0";
                        }else
                        if(fAux.length()>0){
                            resFileExist="S,"+fAux.length()+",1";
                        }else{
                            resFileExist="N,"+fAux.length()+",2";
                        }
                    }else{
                            resFileExist="N,"+fAux.length()+",3";
                    }

                    byte[] kk = resFileExist.getBytes();                          
                    outFile.write(kk);
                    outFile.close();
                    p("FIN FN FILEEXIST: "+resFileExist);
             }


// ***************************************************
// getcluster.fn (mobile)
// ***************************************************
             
             if (fname.contains("getcluster.fn")) {
                    String clusterid = getClusterID();
                    outFile.write(clusterid.getBytes());
                    outFile.close();
             }
             
// ***************************************************
// getauthtoken.fn (mobile)
// ***************************************************
             
             if (fname.contains("getauthtoken.fn")) {
                 if(bUserAuthenticated){
                    outFile.write(sAuthUUID.getBytes());
                    outFile.close();
                 }
             }
              
             
// ***************************************************
// serverproperty.fn (mobile)
// ***************************************************
             
                if (fname.contains("serverproperty.fn")) {
                 if (bUserAuthenticated){
                    
                    String result = GetConfig(sProperty, "config/www-server.properties");
                    
                    outFile.write(result.getBytes());
                    outFile.close();
                 }
                 
             }

// ***************************************************
// serverupdateproperty.fn  
// ***************************************************
                
            if (fname.contains("serverupdateproperty.fn")) {
                    if (bUserAuthenticated){

                       Integer result = UpdateConfig(sProperty,sPropertyValue, "config/www-server.properties");
                       
                       outFile.write(result.toString().getBytes());
                       outFile.close();
                    }
                 
             }
            
// ***************************************************
// getmulticlusters.fn  
// ***************************************************
                
            if (fname.contains("getmulticlusters.fn")) {
                    if (bUserAuthenticated){

                       UserSession us = uuidmap.get(sAuthUUID);
                       String clusterid = getClusterID();
                       String result=MultiClusterManager.getInstance().getClusters(us.getUsername());
                       outFile.write(result.toString().getBytes());
                       outFile.close();
                    }
                 
            }            

//***************************************************
// saveloginmulticluster.fn
//***************************************************            
             if (fname.contains("saveloginmulticluster.fn")) {
                    if (bUserAuthenticated){
                       UserSession us = uuidmap.get(sAuthUUID);
                       MultiClusterManager.getInstance().addCluster(sMultiClusterUser, sAuxCluster,sAuxBoxUser ,sAuxBoxPassword, sMultiClusterName);
                       sAuxCluster="";
                       sAuxBoxUser="";
                       sAuxBoxPassword="";
                       outFile.write("{\"reslut\":true}".getBytes());
                       outFile.close();
                    }
            }
//***************************************************
// getpropertymulticluster.fn
//***************************************************            
             if (fname.contains("getpropertymulticluster.fn")) {
                    if (bUserAuthenticated){
                       UserSession us = uuidmap.get(sAuthUUID);
                       String res=MultiClusterManager.getInstance().getPropertyMulticluster(us.getUsername());
                       outFile.write(res.getBytes());
                       outFile.close();

                    }
            }
             
//***************************************************
// savepropertymulticluster.fn
//***************************************************            
             if (fname.contains("savepropertymulticluster.fn")) {
                    if (bUserAuthenticated){
                       UserSession us = uuidmap.get(sAuthUUID);
                       //Se reutiliza el param sMultiClusterName para no agregar mas parametros
                       MultiClusterManager.getInstance().savePropertyMulticluster(us.getUsername(), sMultiClusterName);
                       outFile.write("{\"reslut\":true}".getBytes());
                       outFile.close();

                    }
            }
             
             
// ***************************************************
// addmulticluster.fn  
// ***************************************************
                
            if (fname.contains("addmulticluster.fn")) {
                    if (bUserAuthenticated){
                       UserSession us = uuidmap.get(sAuthUUID);
                       String res=MultiClusterManager.getInstance().testCluster(us.getUsername(), sMultiClusterID,sMultiClusterUser ,sMultiClusterPassword, sMultiClusterName);
                       if(res.equalsIgnoreCase("LOGININVALID")){
                           outFile.write("{\"result\":false}".getBytes());
                           outFile.close();
                       }else{
                           MultiClusterManager.getInstance().addCluster(us.getUsername(), sMultiClusterID,sMultiClusterUser ,sMultiClusterPassword, sMultiClusterName);
                           outFile.write("{\"result\":true}".getBytes());
                           outFile.close();
                       }
                       
                    }
            }

// ***************************************************
// addmulticluster.fn  
// ***************************************************
                
            if (fname.contains("querymulticluster.fn")) {
                    if (bUserAuthenticated){
                       UserSession us = uuidmap.get(sAuthUUID);
                       String res=MultiClusterManager.getInstance().queryRemote(us.getUsername(), sMultiClusterID, sFoo2,sDateStart,sFileType, sDaysBack);
                       outFile.write(res.getBytes());
                       outFile.close();
                    }
            }
            
// ***************************************************
// removemulticluster.fn  
// ***************************************************
                
            if (fname.contains("removemulticluster.fn")) {
                    if (bUserAuthenticated){
                       UserSession us = uuidmap.get(sAuthUUID);
                       MultiClusterManager.getInstance().removeCluster(us.getUsername(), sMultiClusterID);
                       outFile.write("{\"reslut\":true}".getBytes());
                       outFile.close();
                    }
                 
            }
            
// ***************************************************
// fbpublish.fn 
// ***************************************************
                
                 if (fname.contains("fbpublish.fn")) {
                    if (bUserAuthenticated){
 
                       UserSession us = uuidmap.get(sAuthUUID);
                       
                       FacebookUtil facebook=new FacebookUtil(us.getUsername());
                       if(sFBToken!=null && sFBToken.trim().length()>0){
                            facebook.setUserToken(sFBToken);
                       }
                       
                       String urlgetfile = wf.getfile_mobile(sMD5, "", "", "", "",false, "", "");
                       if (!urlgetfile.equals("ERROR") && !urlgetfile.equals("FILENOTFOUND") && !urlgetfile.equals("TIMEOUT")) {   
                            String sTmpFileName = "tmp/" + sMD5;
                            File fh = new File("tmp/");
                            fh.mkdirs();

                            int gf = NetUtils.getfile(urlgetfile, sTmpFileName, 3, 500, 10000);
                            Boolean res=facebook.publishPhoto(sFBText, new File(sTmpFileName));
                            outFile.write(("{\"result\":"+res+"}").getBytes());
                            outFile.close();
                            
                       }else{
                            outFile.write(("{\"result\":false}").getBytes());
                            outFile.close();
                       }             
                      
                    }
                 
             }

                
// ***************************************************
// getremoteeula.fn (mobile)
// ***************************************************
                
                 if (fname.contains("getremoteeula.fn")) {
                    if (bUserAuthenticated){

                        File f2 = new File (root + File.separator + "cass/remoteeula.txt"); 
                        String sEULABuffer = loadFileStr(f2);
                       
                       outFile.write(("{ \"licence\":\""+ URLEncoder.encode(sEULABuffer,"UTF-8")+"\" }").getBytes());
                       outFile.close();
                    }
                 
             }
              
// ***************************************************
// gettags.fn (mobile)
// ***************************************************
             
             if (fname.contains("gettags_m.fn") || fname.contains("gettags_webapp.fn")) {
                 if (bUserAuthenticated){                     
                    String result = "";
                    UserSession us = uuidmap.get(sAuthUUID);                                      
                    if (us.isRemote() || (isValidMultiClusterID(sMultiClusterID))) {
                        System.out.println("REMOTE GETTAGS");
                        RemoteAccess ra =null;
                        if(isValidMultiClusterID(sMultiClusterID)){
                            ra=MultiClusterManager.getInstance().getRA(us.getUsername(),sMultiClusterID);
                        }else{
                            ra=new RemoteAccess(us.getRemoteCluster());
                            ra.setUuid(us.getUuid());
                        }
                        
                        result = ra.remoteGetTags_webapp();
                        outFile.write(result.getBytes());
                        outFile.close();                                                
                    } else {
                        String sUser;
                        boolean isAdmin=false;
                        if(us != null){
                            sUser = us.getUsername();
                            UserCollection uc = UserCollection.getInstance();
                            User user = uc.getUsersByName(sUser);
                            if(user.getRole().equals("admin")){
                                isAdmin = true;
                            }
                            
                        }else{
                            sUser = null;
                        }
                        result += wf.getTagsLeftNavBar(sUser, true, fname.contains("gettags_webapp.fn"),isAdmin);

                        outFile.write(result.getBytes());
                        outFile.close();                        
                    }
                 }
             }
             
             
             
// ***************************************************
// gettags.fn (desktop)
// ***************************************************
             
             if (fname.contains("gettags.fn")) {
                 String result = "";
                 if (bUserAuthenticated){
                    UserSession us = uuidmap.get(sAuthUUID);
                    String sUser;
                    if(us != null){
                        sUser = us.getUsername();
                    }else{
                        sUser = null;
                    }                        
                    if(!us.isRemote()){

                        result += wf.getTagsLeftNavBar(sUser, false, false,false);
                        if (result.trim().equals("")){
                            result = "<span style=\"color: black\">No tags</span>";
                        }


                    }else{

                        RemoteAccess ra = new RemoteAccess(us.getRemoteCluster());
                        ra.setUuid(us.getUuid());
                        result = HtmlFromJSON.getTagsRemote(ra.remoteGetTags());
                    }
                 }
                 else 
                     result = "";
                 outFile.write(result.getBytes());
                 outFile.close();
             }
             
// ***************************************************
// getfolders.htm Dada una carpeta de entrada retorna 
// la lista de directorios a que contiene la carpeta.   
// se utiliza para el treeview de carpetas en el setup 
// wizard web
// ***************************************************
             if (fname.contains("getfolders-json.fn") || fname.contains("getfolders_json.fn")) {
                p("   ***   -----getfolders-json.fn - sFolder: '" + sFolder+ "'");
                p("   ***   bUserAuthenticated          : " + bUserAuthenticated);
                p("   ***   bMobile                     : " + bMobile);
                p("   ***   scanTreeVariant             : " + scanTreeVariant);
                p("   ***   scanTreeMode                : " + scanTreeMode);

                sFolder= URLDecoder.decode(sFolder,"UTF-8");
                
                if(sFolder.equals("scanfolders")){ 

                        File f = new File(appendage + "../scrubber/config/www-rtbackup.properties");
                        if (f.exists() ) {
                            Properties props = new Properties();

                            InputStream  isscan =new BufferedInputStream(new FileInputStream(f));
                            props.clear();
                            props.load(isscan);
                            isscan.close();
                                            
                            String p = props.getProperty("scandir"); //Path al archivo
                            p("scandir: " + p);
                            File f2 = new File(appendage + p);
                            if (f2.exists()) {
                                props.clear();
                                isscan =new BufferedInputStream(new FileInputStream(f2));
                                props.load(isscan);
                                isscan.close();
                                String p2 = props.getProperty("scandir"); //Path al archivo   
                                 p("scandir2: " + p2);
                                 String result = "";
                                 if (p2.length() > 0) {
                                    result +="[";
                                    String delimitersgf = ";";
                                    StringTokenizer stgf = new StringTokenizer(p2,delimitersgf, true);
                                    boolean bFirst = true;
                                    String sType = "folder";

                                    while (stgf.hasMoreTokens()) {
                                        String sScanPath = stgf.nextToken(); 
                                        if (sScanPath.equalsIgnoreCase(delimitersgf)) {
                                            p("skip delimiter");
                                        } else {
                                            if (!bFirst) 
                                                result += ","; 
                                            else 
                                                bFirst = false;
                                            result += "{" + 
                                                        "\"" + "name\"" + ":" + "\""+ sScanPath + "\"" + 
                                                        "," + 
                                                        "\"" + "type\"" + ":" + "\"" + sType + "\"" + "}";                                            
                                        }
                                      } //end while                                       
                                    result +="]";
                                    outFile.write(result.getBytes());   
                                    }
                                    p("FIN getfolders-json.fn: "+result);                    
                            } else {
                                p("file not found f2: " + f2.getAbsolutePath());
                            }
                         } else {
                                p("file not found f1: " + f.getAbsolutePath());                            
                        }                                                          
                }

                //case root units
                if(sFolder.equals("units")){
                    //root folder case
                     String result="[";
                     if(bWindowsServer){                        
                         boolean coma=false;
                         
                         for(File f:File.listRoots()){
                            if(coma) result+=",";
                            result+="\""+f.getAbsolutePath()+"\\\"";
                            coma=true;
                         }
                         
                     }else{
                         //Linux Unix root /
                        //result+="\"/\""; 
                        boolean firstDrive = true;
                        File volumes=new File("/Volumes/");
                        if(volumes.exists()){
                            for(File f:volumes.listFiles()){
                                if(f.isDirectory()){
                                    result+= (firstDrive ? "\"" : ",\"")+f.getAbsolutePath()+"\"";
                                    if (firstDrive) { firstDrive = false; }
                                }
                            }
                        }
                        
                        File media=new File("/media/");
                        if(media.exists()){
                            for(File f:media.listFiles()){
                                if(f.isDirectory()){
                                    result+=(firstDrive ? "\"" : ",\"")+f.getAbsolutePath()+"\"";
                                    if (firstDrive) { firstDrive = false; }
                                }
                            }
                        }
                     }
                     
                     result+="]";
                     outFile.write(result.getBytes());   
                     p("FIN getfolders-json.fn: "+result);                    
                } // end case units
                
                //case 3- specific path              
                if(sFolder!=null && !sFolder.equals("units") && !sFolder.equals("scanfolders")){ 
                    //folder case     
                    File folder = new File(sFolder);
                    p("folder          : '" + sFolder + "'");
                    p("isDirectory()   : " + folder.isDirectory());
                    p("exists()        : " + folder.exists());
                    if (folder.listFiles() != null) p("listfiles size : " + folder.listFiles().length);
                    
                    if(folder.isDirectory() && folder.exists() && folder.listFiles()!=null) {
                        File[] filesRoot=null;
                        filesRoot = folder.listFiles();
                        
                        String result="[";
                        boolean bFirst = true;
                        String sType = "";
                        String sMD5gf = "";
                        for(File f:filesRoot){
                            if (!bFirst) 
                                result += ","; 
                            else 
                                bFirst = false;
                            if(f.isDirectory()){
                                p("folder: '" + f.getName() + "'");
                                sType = "folder";
                            } else {
                                p("  file: '" + f.getName() + "'");                                
                                sType = "file";
                            }
                            String sExtension = getFileExtension(f.getName());
                            if (sType == "file") sMD5gf = getFileMD5(f.getName());
                            result += "{" + 
                                        "\"" + "name\"" + ":" + "\""+ f.getName() + "\"" + 
                                        "," + 
                                        "\"" + "type\"" + ":" + "\"" + sType + "\"" +
                                        "," + 
                                        "\"" + "md5\"" + ":" + "\"" + sMD5gf + "\"" +
                                        "," + 
                                        "\"" + "extension\"" + ":" + "\"" + sExtension + "\"" + "}";
                            sMD5gf = ""; //clear after use
                        }
                      result+="]";
                      outFile.write(result.getBytes());   
                      p("FIN getfolders-json.fn: "+result);                    
                    } else {
                        //empty folder case
                        String result="[]";
                        outFile.write(result.getBytes());   
                    }
                }  // end case scanfolders
                
             } // end getfolders json

             if (fname.contains("getfolders.fn")) {

                p("   ***   -----getfolders.fn - sFolder: '" + sFolder+ "'");
                p("   ***   bUserAuthenticated          : " + bUserAuthenticated);
                p("   ***   bMobile                     : " + bMobile);
                p("   ***   scanTreeVariant             : " + scanTreeVariant);
                p("   ***   scanTreeMode                : " + scanTreeMode);

                 
                 sFolder= URLDecoder.decode(sFolder,"UTF-8");
                 if(sFolder!=null && !sFolder.equals("units")){ 
                    String result="<ul class='jqueryFileTree' style='display: none;'>";
                    boolean selParent=sFolderSel!=null && sFolderSel.equalsIgnoreCase("on");
                    File folder = new File(sFolder);
                    boolean notScanned=true;
                    
                    CacheMetadataWeb.getInstance().initAutoScan(sFolder,scanTreeVelocity, scanTreeVariant, scanTreeMode,bWindowsServer);
                                        
                    if(folder.isDirectory() && folder.exists() && folder.listFiles()!=null)
                    synchronized(CacheMetadataWeb.getInstance().getScanMap()){
                             File[] filesRoot=null;
                             String[] filesRootString=null;
                             if(scanTreeVariant==1 && scanTreeMode==0){
                                 filesRootString= FolderUtils.GetOSWinFoldersAsStringArray(folder.getAbsolutePath(),false);
                             }else if(scanTreeVariant==1 && scanTreeMode==2){
                                 filesRootString= FolderUtils.GetOSWinFoldersAsStringArrayFilter(folder.getAbsolutePath(),false);
                             }else{
                                 filesRoot=folder.listFiles();
                             }
                             
                             if(scanTreeVariant==1){
                                 ///
                                 p("scantreevariant 1");
                                 for(String fn:filesRootString) {
                                    try{

                                         int folders=0;
                                         String fileName=replaceSpecialCharacters(fn.substring(fn.lastIndexOf("\\")+1));

                                         //folders= FolderUtils.GetOSWinFoldersAsStringArray(fn,true).length;
                                         String path=fn;
                                         if(!path.endsWith(File.separator)){
                                             path+=File.separator;
                                         }
                                         String pathDec=path;
                                         path= URLEncoder.encode(path,"UTF-8").replaceAll("\\+","%20");

                                         result+="<script type='text/javascript'>addMetadataObject($.parseJSON('{\"Path\":\""+path+"\",\"Recursive\":\"0\",\"Renderizado\":\"0\",\"Folders\":\""+folders+"\",\"Metadatos\":[] }'));</script>";

                                        
                                         result+="<li id='li"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"' class='directory subfolderscollapsed'>" ;
                                         
                                         result+= "<input type='checkbox' style='display: inline;' onmouseover='displayMetadata(this);' onmouseout='disposeMetadata();' pathParent='"+URLEncoder.encode(sFolder,"UTF-8").replaceAll("\\+","%20")+"' path='"+path+"' ck=10 onchange='clearSelectedPath(this);' "+((selParent && ! blackList.containsKey(pathDec))?"checked":"")+" />"+
                                                 " <a style='display: inline;' onmouseover='displayMetadata(this);' onclick='"+( scanTreeMode==2? "InitScan(\""+path+"\");":"")+"' onmouseout='disposeMetadata(this);' path='"+path+"'  id='a"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"'  href='#' rel='"+path+"'>"+
                                                     fileName +"</a>"+(CacheMetadataWeb.getInstance().isCheckScanMode(sFolder,scanTreeMode,bWindows) ? "<img id='sc"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"' src='ajax-loader.gif' style='display:inline-block;'/>":"");
                                         /*if(selParent && ! blackList.containsKey(pathDec)){
                                            //Padre seleccionado para scan e hijo no está en blacklist, ej: folder nuevo
                                             result+="<script type='text/javascript'>"
                                                     + "if(blMap.indexOf('"+path+"')==-1)dirMap.push('"+path+"');</script>";
                                        }//Demas casos en los que está en blacklist ya esta considerado al inicializar la lista de blacklist 
                                        */
                                        result+="</li>";
                                    


                                }catch(Throwable th){
                                    th.printStackTrace();
                                }
                                 }
                             } else {
                                p("scantreevariant - other");

                                for(File fn: filesRoot){
                                 //Caso unix y linux se filtran los directorios Vomunes(Mac) y media(Unix) para que no se pueda seleccionar
                                 //en dos unidades distintas porque el / es una unidad y lus subdirectorios de Volumes y Medios son otras unidades
                                  if(scanTreeVariant==1 ||(fn.listFiles()!=null && fn.isDirectory() && !fn.getAbsolutePath().equalsIgnoreCase("/Volumes") && !fn.getAbsolutePath().equalsIgnoreCase("/media"))){
                                      String pathEnc=fn.getAbsolutePath();
                                      if(!pathEnc.endsWith(File.separator)){
                                                pathEnc+=File.separator;
                                       }
                                       pathEnc =URLEncoder.encode(pathEnc, "UTF-8").replaceAll("\\+","%20");

                                     
                                     if(CacheMetadataWeb.getInstance().getScanMap().containsKey(pathEnc)){
                                                notScanned=false;
                                     
                                                FolderMetaData meta=CacheMetadataWeb.getInstance().getScanMap().get(pathEnc);
                                                int folders= meta.getFolders();
                                                String path=fn.getAbsolutePath();
                                                if(!path.endsWith(File.separator)){
                                                    path+=File.separator;
                                                }
                                                String pathDec=path;
                                                path= URLEncoder.encode(path,"UTF-8").replaceAll("\\+","%20");

                                               File fileExt=new File(appendage + "../scrubber/config/FileExtensions_All.txt");
                                               BufferedReader reader=new BufferedReader(new FileReader(fileExt));
                                               String s=reader.readLine();
                                               StringBuilder objectMetadata=new StringBuilder("");
                                               boolean br=false;
                                               boolean br2=false;

                                               result+="<script type='text/javascript'>addMetadataObjectR($.parseJSON('{\"Path\":\""+path+"\",\"Recursive\":\"1\",\"Renderizado\":\"1\",\"Folders\":\""+folders+"\",\"Metadatos\":[] }'));</script>";
                                               int fileSec=0;
                                               int secWithFiles=0;
                                               String secTxt="";
                                               String secAux="";
                                               while( s!=null && s.split(",").length>0) 
                                               {
                                                   if(s.split(",")[0].equals("@")){
                                                       if(br) objectMetadata.append("]}'));  ");
                                                       objectMetadata.append("addMetadata('"+path+"',$.parseJSON('{\"Sec\":\""+s.split(",")[1]+"\",\"Exts\":[");
                                                       br2=false;
                                                       if(fileSec>0){
                                                           ++secWithFiles;
                                                           secTxt=secAux;
                                                       }
                                                       secAux=s.split(",")[2];
                                                       fileSec=0;
                                                   }else if(s.split(",").length>1){
                                                       int countFiles=0;
                                                       String ext=s.split(",")[0];
                                                       if(meta.getMetadata().containsKey(ext)){
                                                            countFiles+=meta.getMetadata().get(ext).getCount();
                                                       }else{
                                                           String g="";
                                                       }
                                                       fileSec+=countFiles;
                                                       if(br2)objectMetadata.append(",");
                                                       br2=true;
                                                       objectMetadata.append("{\"Ext\":\""+s.split(",")[0]+"\",\"Dsc\":\""+s.split(",")[1]+"\",\"Count\":\""+countFiles+"\"}");
                                                   }




                                                   br=true;
                                                   s=reader.readLine();

                                               }
                                               reader.close();
                                               objectMetadata.append("]}'));");

                                               result+= "<script>"+objectMetadata.toString()+"</script>";
                                               if(fileSec>0){
                                                 ++secWithFiles;
                                                 secTxt=secAux;
                                               }
                                               if(secWithFiles>1){
                                                   secTxt="varios";
                                               }

                                               if(secWithFiles>0){
                                                    result+="<li id='li"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"' class='directory subfolderscollapsed"+secTxt+(folders>0?"":"_ss")+"'>" ;
                                               } else
                                               if(folders==0){
                                                     result+="<li id='li"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"' class='directory collapsed'>" ;
                                               }else{
                                                    result+="<li id='li"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"' class='directory subfolderscollapsed'>" ;
                                               }
                                                result+= "<input type='checkbox' style='display: inline;' onmouseover='displayMetadata(this);' onmouseout='disposeMetadata();' pathParent='"+URLEncoder.encode(sFolder,"UTF-8").replaceAll("\\+","%20")+"' path='"+path+"' ck=10 onchange='clearSelectedPath(this);' "+((selParent && ! blackList.containsKey(pathDec))?"checked":"")+" />"+
                                                        " <a id='a"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"' style='display: inline;' onmouseover='displayMetadata(this);' onmouseout='disposeMetadata(this);' path='"+path+"' href='#' rel='"+path+"'>"+
                                                            replaceSpecialCharacters(fn.getName()) +"</a>";
                                               /*if(selParent && ! blackList.containsKey(pathDec)){
                                                   //Padre seleccionado para scan e hijo no está en blacklist, ej: folder nuevo
                                                    result+="<script type='text/javascript'>"
                                                            + "if(blMap.indexOf('"+path+"')==-1)dirMap.push('"+path+"');</script>";
                                               }//Demas casos en los que está en blacklist ya esta considerado al inicializar la lista de blacklist 
                                               */
                                               result+="</li>";
                                     }
                                     else{
                                         try{
                                            
                                             
                                                
                                                    if(scanTreeVariant==1||( fn.isDirectory() && !fn.getAbsolutePath().equalsIgnoreCase("/Volumes") && !fn.getAbsolutePath().equalsIgnoreCase("/media"))){
                                                         p("   ***   entered here...");
                                                         int folders=0;
                                                         try{
                                                           File[] files=null;
                                                           
                                                           if(scanTreeVariant==1 && scanTreeMode==0){
                                                               folders= FolderUtils.GetOSWinFoldersAsStringArray(fn.getAbsolutePath(),true).length;
                                                           }else if(scanTreeVariant==1 && scanTreeMode==0){
                                                               folders= FolderUtils.GetOSWinFoldersAsStringArrayFilter(fn.getAbsolutePath(),true).length;
                                                           }else{
                                                               files=fn.listFiles();
                                                           }
                                                           
                                                           if(scanTreeVariant==0){ 
                                                                for(File fol:files){
                                                                    if(fol.isDirectory()){
                                                                        folders++;
                                                                        if(scanTreeMode==0 ){
                                                                            break;
                                                                        }
                                                                    }
                                                                }
                                                           }
                                                         }catch(Throwable th){
                                                             th.printStackTrace();
                                                         }
                                                        String path=fn.getAbsolutePath();
                                                        if(!path.endsWith(File.separator)){
                                                            path+=File.separator;
                                                        }
                                                        String pathDec=path;
                                                        path= URLEncoder.encode(path,"UTF-8").replaceAll("\\+","%20");
                                                        boolean br=false;
                                                        boolean br2=false;
                                                        int fileSec=0;
                                                        int secWithFiles=0;
                                                        String secTxt="";
                                                        String secAux="";
                                                        result+="<script type='text/javascript'>addMetadataObject($.parseJSON('{\"Path\":\""+path+"\",\"Recursive\":\"0\",\"Renderizado\":\"0\",\"Folders\":\""+folders+"\",\"Metadatos\":[] }'));</script>";
                                                        
                                                        //Si no está apagado el scan o está en modo inteligente
                                                        if(scanTreeMode!=0 && scanTreeMode!=2){   
                                                            File fileExt=new File(appendage + "../scrubber/config/FileExtensions_All.txt");
                                                            BufferedReader reader=new BufferedReader(new FileReader(fileExt));
                                                            String s=reader.readLine();
                                                            StringBuilder objectMetadata=new StringBuilder("");
                                                          
                                                             
                                                            while( s!=null && s.split(",").length>0) 
                                                            {
                                                                if(s.split(",")[0].equals("@")){
                                                                    if(br) objectMetadata.append("]}'));  ");
                                                                    objectMetadata.append("addMetadata('"+path+"',$.parseJSON('{\"Sec\":\""+s.split(",")[1]+"\",\"Exts\":[");
                                                                    br2=false;
                                                                    if(fileSec>0){
                                                                        ++secWithFiles;
                                                                        secTxt=secAux;
                                                                    }
                                                                    secAux=s.split(",")[2];
                                                                    fileSec=0;
                                                                }else if(s.split(",").length>1){
                                                                    int countFiles=0;
                                                                    final String ext=s.split(",")[0];
                                                                    try{
                                                                      for(File fnIn:fn.listFiles()){
                                                                          if( fnIn.getName().toLowerCase().endsWith(ext) ){
                                                                              countFiles++; 
                                                                          }

                                                                      }
                                                                      fileSec+=countFiles;
                                                                    }catch(Throwable th){
                                                                        th.printStackTrace();
                                                                    }
                                                                    if(br2)objectMetadata.append(",");
                                                                    br2=true;
                                                                    objectMetadata.append("{\"Ext\":\""+s.split(",")[0]+"\",\"Dsc\":\""+s.split(",")[1]+"\",\"Count\":\""+countFiles+"\"}");
                                                                }




                                                                br=true;
                                                                s=reader.readLine();

                                                            }
                                                            reader.close();
                                                            objectMetadata.append("]}'));");

                                                            result+= "<script>"+objectMetadata.toString()+"</script>";
                                                        }
                                                       if(fileSec>0){
                                                         ++secWithFiles;
                                                         secTxt=secAux;
                                                       }
                                                       if(secWithFiles>1){
                                                           secTxt="varios";
                                                       }
                                                         
                                                       if(secWithFiles>0){
                                                            result+="<li id='li"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"' class='directory subfolderscollapsed"+secTxt+(folders>0?"":"_ss")+"'>" ;
                                                       } else
                                                       if(folders==0){
                                                             result+="<li id='li"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"' class='directory collapsed'>" ;
                                                       }else{
                                                            result+="<li id='li"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"' class='directory subfolderscollapsed'>" ;
                                                       }
                                                        result+= "<input type='checkbox' style='display: inline;' onmouseover='displayMetadata(this);' onmouseout='disposeMetadata();' pathParent='"+URLEncoder.encode(sFolder,"UTF-8").replaceAll("\\+","%20")+"' path='"+path+"' ck=10 onchange='clearSelectedPath(this);' "+((selParent && ! blackList.containsKey(pathDec))?"checked":"")+" />"+
                                                                " <a style='display: inline;' onmouseover='displayMetadata(this);' onclick='"+( scanTreeMode==2? "InitScan(\""+path+"\");":"")+"' onmouseout='disposeMetadata(this);' path='"+path+"'  id='a"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"'  href='#' rel='"+path+"'>"+
                                                                    replaceSpecialCharacters(fn.getName()) +"</a>"+(CacheMetadataWeb.getInstance().isCheckScanMode(sFolder,scanTreeMode,bWindows) ? "<img id='sc"+path.replaceAll("%", "_").replaceAll("\\+", "_")+"' src='ajax-loader.gif' style='display:inline-block;'/>":"");
                                                       /*if(selParent && ! blackList.containsKey(pathDec)){
                                                           //Padre seleccionado para scan e hijo no está en blacklist, ej: folder nuevo
                                                            result+="<script type='text/javascript'>"
                                                                    + "if(blMap.indexOf('"+path+"')==-1)dirMap.push('"+path+"');</script>";
                                                       }//Demas casos en los que está en blacklist ya esta considerado al inicializar la lista de blacklist 
                                                       */
                                                       result+="</li>";
                                                    }
                                                
                                         
                                         }catch(Throwable th){
                                             th.printStackTrace();
                                         }
                                     }
                                  }
                             }
                    }
                                result = result +  "</ul>";                               
                                outFile.write(result.getBytes());    
                             
                        
                       } 
                     p("FIN getfolders.fn: "+result);
                 } else{
                     //Retorna unidades
                     //Cargo lista black list
                     blackList.clear();
                     BufferedReader reader=new BufferedReader(new FileReader(appendage + "../scrubber/config/blacklist.txt"));
                     String s=reader.readLine();
                     while(s!=null){
                         blackList.put(s,s);
                         s=reader.readLine();
                     }
                     reader.close();
                     String  result="[";
                     if(bWindowsServer){
                        
                         boolean coma=false;
                         
                         for(File f:File.listRoots()){
                            if(coma) result+=",";
                            result+="\""+f.getAbsolutePath()+"\\\"";
                            coma=true;
                         }
                         
                     }else{
                         //Linux Unix root /
                        //result+="\"/\""; 
                        boolean firstDrive = true;
                        File volumes=new File("/Volumes/");
                        if(volumes.exists()){
                            for(File f:volumes.listFiles()){
                                if(f.isDirectory()){
                                    result+= (firstDrive ? "\"" : ",\"")+f.getAbsolutePath()+"\"";
                                    if (firstDrive) { firstDrive = false; }
                                }
                            }
                        }
                        
                        File media=new File("/media/");
                        if(media.exists()){
                            for(File f:media.listFiles()){
                                if(f.isDirectory()){
                                    result+=(firstDrive ? "\"" : ",\"")+f.getAbsolutePath()+"\"";
                                    if (firstDrive) { firstDrive = false; }
                                }
                            }
                        }
                     }
                     
                     result+="]";
                      outFile.write(result.getBytes());   
                     p("FIN getfolders.fn: "+result);
                 }
                 outFile.close();
             } 
               

// ***************************************************
// openfolder.htm
// ***************************************************
             if (fname.contains("openfolder.htm") || fname.contains("openfolder.fn")) {
                if (sFoo2.equals("")) {
                    sFoo2 = sFileType;
                    p("Foo blank. Replacing with filetype...: '" + sFoo2 + "'");
                }
                              

                try {
                    String ClientIP = s.getInetAddress().getHostAddress();
                    p("@@@ClientIP=" + ClientIP);                
                    p("LocalIP1=" + LocalIP);
                    if (ClientIP == null) {
                    InetAddress clientIP = NetUtils.getLocalAddressNonLoopback(Integer.toString(port));  
                        String LocalIP = "127.0.0.1";
                        if (clientIP != null) {
                            LocalIP = clientIP.getHostAddress();                            
                        }
                        p("LocalIP2=" + LocalIP);                                            
                    }
                    p("INICIO REDIR a openfolder, sNamer="+sNamer+",sFileName="+sFileName);

                    bRedirectBulker = true;
                    sRedirectBulkerURL = wf.echoOpenFolderh2(sNamer, sFileName, sFoo2, LocalIP, Integer.toString(port), bCloudHosted, ClientIP, dbmode);
                    if(sRedirectBulkerURL==null || sRedirectBulkerURL.trim().length()==0 || sRedirectBulkerURL.endsWith("ERROR")){
                        sRedirectBulkerURL="msg_filenotfound.html";
                    }
                    p("FIN REDIR a openfolder: "+sRedirectBulkerURL);
                    outFile.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //get server IP address
                
             } 
                       

// ***************************************************
// sendfile.htm
// ***************************************************
             if (fname.contains("sendfile.htm")) {
                if (sFoo2.equals("")) {
                    sFoo2 = sFileType;
                    p("Foo blank. Replacing with filetype...: '" + sFoo2 + "'");
                }
                              
                String ClientIP = s.getInetAddress().getHostAddress();
                p("ClientIP=" + ClientIP);                
                p("LocalIP1=" + LocalIP);

                //get server IP address
                InetAddress clientIP = NetUtils.getLocalAddressNonLoopback(Integer.toString(port));  
                String LocalIP = "127.0.0.1";
                if (clientIP != null) {
                    LocalIP = clientIP.getHostAddress();                            
               }
                p("LocalIP2=" + LocalIP);
                
                p("INICIO REDIR a sendfile.htm, sNamer="+sNamer+",sFileName="+sFileName);

                bRedirectBulker = true;
                sRedirectBulkerURL = wf.echoSendh2(sNamer, sFileName, sFoo2, LocalIP, Integer.toString(port), bCloudHosted, ClientIP);
                if(sRedirectBulkerURL==null || sRedirectBulkerURL.trim().length()==0 || sRedirectBulkerURL.endsWith("ERROR")){
                    sRedirectBulkerURL="msg_filenotfound.html";
                }
                p("FIN REDIR a sendfile.htm: "+sRedirectBulkerURL);
             } 
                       
             
// ***************************************************
// openfile.fn
// ***************************************************
             if (fname.contains("openfile.fn")) {
                if (bUserAuthenticated){
                    if (sFoo2.equals("")) {
                        sFoo2 = sFileType;
                        p("Foo blank. Replacing with filetype...: '" + sFoo2 + "'");
                    }

                    try {
                        String ClientIP = s.getInetAddress().getHostAddress();
                        p("ClientIP=" + ClientIP);                
                        p("LocalIP1=" + LocalIP);

                        if (ClientIP == null) {
                            //get server IP address
                            InetAddress clientIP = NetUtils.getLocalAddressNonLoopback(Integer.toString(port));  
                            String LocalIP = "127.0.0.1";
                            if (clientIP != null) {
                                LocalIP = clientIP.getHostAddress();                            
                            }
                            p("LocalIP2=" + LocalIP);                            
                        }
                        
                        p("INICIO openfile.fn, sNamer="+sNamer+" sFileName="+sFileName + " sFileType=" + sFileType);
                        String sPath = wf.openfile_mobile(sNamer, sFileName, sMD5, LocalIP, sPort, bCloudHosted, ClientIP, dbmode);

                        if (sPath.length() > 0) {
                            p("Local copy found in Server!");
                        } else {                    
                            p("Local copy NOT found in Server. Retrieving file...");
                            String urlgetfile = wf.getfile_mobile(sNamer, sFileName, sFoo2, LocalIP, Integer.toString(port), bCloudHosted, ClientIP, dbmode);                      
                            if (!urlgetfile.equals("ERROR")) {
                                   String sTmpFileName = "tmp/" + sNamer + "." + sFileType;
                                   File fh = new File("tmp/");
                                   fh.mkdirs();

                                   int gf = NetUtils.getfile(urlgetfile, sTmpFileName, 3, 500, 10000);
                                   p("res getfile = " + gf); 
                                   File fh2 = new File(sTmpFileName);                           
                                   sPath = fh2.getCanonicalPath();
                            }                       
                        }
                        p("openfile.fn, sPath= '"+sPath + "'");
                        boolean bres = openFile(sPath, bWindows, "1", bLinux);   
                        outFile.write("openfile.fn".getBytes());
                        outFile.close();
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                 }
             }
// ***************************************************
// openfile.htm
// ***************************************************
             if (fname.contains("openfile.htm")) {
                if (sFoo2.equals("")) {
                    sFoo2 = sFileType;
                    p("Foo blank. Replacing with filetype...: '" + sFoo2 + "'");
                }
                              
                String ClientIP = s.getInetAddress().getHostAddress();
                p("ClientIP=" + ClientIP);                
                p("LocalIP1=" + LocalIP);

                //get server IP address
                InetAddress clientIP = NetUtils.getLocalAddressNonLoopback(Integer.toString(port));  
                String LocalIP = "127.0.0.1";
                if (clientIP != null) {
                    LocalIP = clientIP.getHostAddress();                            
               }
                p("LocalIP2=" + LocalIP);
                
                p("INICIO REDIR a openfile.htm, sNamer="+sNamer+",sFileName="+sFileName);

                bRedirectBulker = true;
                sRedirectBulkerURL = wf.echoOpenh2(sNamer, sFileName, sFoo2, LocalIP, Integer.toString(port), bCloudHosted, ClientIP, dbmode);
                if(sRedirectBulkerURL==null || sRedirectBulkerURL.trim().length()==0 || sRedirectBulkerURL.endsWith("ERROR")){
                    sRedirectBulkerURL="msg_filenotfound.html";
                }
                p("FIN REDIR a openfile.htm: "+sRedirectBulkerURL);
                sRedirectBulkerURL = sRedirectBulkerURL.replaceAll("0:0:0:0:0:0:0:1%0", "localhost");
                p("FIN REDIR a openfile.htm2: "+ sRedirectBulkerURL);
                
                outFile.close();
             } 

             
// ***************************************************
// viewimg2.htm
// ***************************************************
             if (fname.contains("viewimg2.htm") || fname.contains("sendimg2.htm")) {
                UserSession us = uuidmap.get(sAuthUUID);
                if(us.isRemote()){
                    sRedirectBulkerURL = "viewimg.htm?md5=" + sNamer;
                    bRedirectBulker = true;
                }else{

                if (sFoo2.equals("")) {
                    sFoo2 = sFileType;
                    p("Foo blank. Replacing with filetype...: '" + sFoo2 + "'");
                }
                              
                String ClientIP = s.getInetAddress().getHostAddress();
                p("ClientIP=" + ClientIP);                
                p("LocalIP1=" + LocalIP);

                //get server IP address
                InetAddress clientIP = NetUtils.getLocalAddressNonLoopback(Integer.toString(port));  
                String LocalIP = "127.0.0.1";
                if (clientIP != null) {
                    LocalIP = clientIP.getHostAddress();                            
               }
                p("LocalIP2=" + LocalIP);
                
                p("INICIO REDIR a Viewimg.htm, sNamer="+sNamer+",sFileName="+sFileName);

                bRedirectBulker = true;
                String sRes = wf.echoViewh2(sNamer, sFileName, sFoo2, LocalIP, Integer.toString(port), bCloudHosted, ClientIP, dbmode);
                sRedirectBulkerURL = sRes;
                p("echoViewh2 = " + sRes);
                if(sRes==null || sRes.trim().length()==0 || sRes.endsWith("ERROR")){
                    sRedirectBulkerURL="msg_error.html";
                }
                if (sRes.equals("FILENOTFOUND")) {
                    sRedirectBulkerURL="msg_filenotfound.html";
                }
                if (sRes.equals("TIMEOUT")) {
                    sRedirectBulkerURL="msg_nodenotavailable.html";
                }
                p("REDIR Viewimg.htm: "+sRedirectBulkerURL);
                if (fname.contains("send")) {
                    sRedirectBulkerURL = sRedirectBulkerURL.replace("viewimg", "sendform");
                    p("REDIR sendimg.htm: "+sRedirectBulkerURL);
                    
                }
                }
                outFile.close();
             }
            
// ***************************************************
// getemailandgroups.fn
// ***************************************************            
            if (fname.contains("getemailandgroups.fn")) {
                String res = "";
                                
                String sendMailProp = GetConfig("sendmail", "config/www-mailer.properties");  //props.getProperty("sendmail");                
                if (sendMailProp != null && sendMailProp.equals("on")){
                    res = LoadGroupsWithAccounts();
                    res += LoadAccountsInArray();           
                }

               byte[] kk = res.getBytes();
               outFile.write(kk);
               outFile.close();
           }
// ***************************************************
// about.htm
// ***************************************************
    if (fname.contains("about.htm")) {
        
             File f = new File (root + File.separator + "cass/about_static.htm");    
             String sBuffer = loadFileStr(f);
             String sBuild = getVersion();
             String sBuffer2 = sBuffer.replace("***REP***", sBuild); 

             byte[] kk = sBuffer2.getBytes();
             outFile.write(kk);  
             outFile.close();
       
    }
    
// ***************************************************
// welcome.htm
// ***************************************************

                    if (fname.contains("welcome.htm")) {
                       is = new FileInputStream(root + File.separator + "cass/welcome_header.htm");
                       try {
                            int n;
                            while ((n = is.read(buf)) > 0) {
                                outFile.write(buf, 0, n);
                            }
                       } finally {
                            is.close();
                       }                       

                        String res2 = "";
                        if (bUserAuthenticated) {
                            
                            UserSession us = uuidmap.get(sAuthUUID);
                            String sUser;
                            if(us != null){
                                sUser = us.getUsername();
                            }else{
                                sUser = null;
                            }                        
                            
                            res2 += "<div style=\"position: absolute;top: 90px;width: 100%\">";
                            res2 += Home.getWelcomeDiv(sUser, uuidmap.size(), wf.echobackup(sFoo2, dbmode));
                            //res2 += Home.getAlertsDiv();
                            //res2 += Home.getNetworkUsersDiv();
                            res2 += Home.getFileScanIndexDiv();
                            //res2 += PrintStateInfo();
                            res2 += "</div>";
                            
                            
                            if(!us.isRemote()){

                                String result = wf.getTagsLeftNavBar(sUser, false, false,false);

                                res2 += "<script type='text/javascript'>\n";
                                res2 += "$('#loop_tags', window.parent.SIDEBAR.document).html('xxx');\n";
                                res2 += "$('#tags_all', window.parent.SIDEBAR.document).html('" + result + "');\n";
                                res2 += "setTimeout('updateTags()', 3000);";
                                res2 += "</script>";

                                //res2 += "<script type='text/javascript'>setTimeout('updateTags()', 5000);</script>";


                            }else{

                                RemoteAccess ra = new RemoteAccess(us.getRemoteCluster());
                                ra.setUuid(us.getUuid());
                                res2 += HtmlFromJSON.getTagsRemote(ra.remoteGetTags());
                            }
                            

                            
                        } else {      
                            File f = new File (root + File.separator + "cass/login_static.htm");   
                            res2 = "";
                            if (f.exists()) {
                                res2 = loadFileStr(f);                                                        
                            }
                            res2 += "<br><br><br><p>" + "Please login! </p>";                               
                        }                                                                       
                        
                        byte[] kk = res2.getBytes();                          
                        outFile.write(kk);
                        outFile.close();
                    }

// ***************************************************
// auth.htm deprecated
// ***************************************************

//                    if (fname.contains("auth.htm")) {
//                        
//                        
//                       is = new FileInputStream(root + File.separator + "cass/welcome_header.htm");
//                       try {
//                            int n;
//                            
//                            while ((n = is.read(buf)) > 0) {
//                                outFile.write(buf, 0, n);
//                            }
//                          
//                                
//                       } finally {
//                            is.close();
//                       }                       
//
//
//                          //String res2 = "User:" + sBoxUser;
//                          //res2 += "Password:" + sBoxPassword;
//                       String res2 = "";
//                        
//                          //authenticate here
//                        UserCollection userCollection = new UserCollection();
//                        
//                          if (userCollection.isUserPasswordValid(sBoxUser, sBoxPassword) && 
//                                  (userCollection.getUserAdmin().getUsername().equals(sBoxUser) ||
//                                      GetConfig("allowotherusers", "config/www-server.properties").equals("true"))){
//                              
//                              bAuth = true;
//                              res2 = "<p>" + "Welcome back, " + sBoxUser + ".<br><br>";
//    
//                               //Solicitan una busqueda desde plugin entonces pedimos que se haga la 
//                              // busqueda search
//                              if(sFoo2!=null && sFoo2.trim().length()>0){
//                                  res2+= "<h1>" + "Wait for query result... </h1><br><br>";
//                                  
//                                       
//                                    res2+="<script type='text/javascript'>\n"+
//                                              "    window.parent.SIDEBAR.searchclick();" +
//                                              "</script>";
//                               }
//                                      
//                              res2 += PrintStateInfo();
//
//                          } else {
//                              res2 = "<p>" + "Password invalid.";                              
//                          }
//                          
//                          
//                          
//                          byte[] kk = res2.getBytes();                          
//                          outFile.write(kk);
//                    }
                    
                    
                    
                   if ((fname.contains("cass7.php")
                           || fname.contains("echoClient5.php")
                           || fname.contains("test4.php")
                           || (fname.contains("echoClient5.htm") )) 
                           ) {
                       if (bPasswordValid && bUserAuthenticated) {
                            is = new FileInputStream(root + File.separator + "cass/query3.htm");
                            try {
                                 int n;
                                 while ((n = is.read(buf)) > 0) {
                                     outFile.write(buf, 0, n);
                                 }
                            } finally {
                                 is.close();
                            }                                                  
                       } else {
                            String res2 = "";
                            res2 += "Login required(1).</p>";  
                            
                            //Si solicitaron una consulta desde el plugin
                            //Se tienen que validar entonces les saco la ruedita
                            //Y quito el Loading...
                            if(sFoo2!=null && sFoo2.trim().length()>0){
                             res2 += "<script type=\"text/javascript\" src=\"jquery.min.js\"></script>"+
                                     "<script>  $('#plw', window.parent.SIDEBAR.document).css('background','lightgrey');\n"+
                                        "$('#plw', window.parent.SIDEBAR.document).css('color', 'lightgrey');\n"+
                                        "$('*', window.parent.parent.NAVBAR.document).css('cursor', 'auto');\n"+
                                        "$('*', window.parent.SIDEBAR.document).css('cursor', 'auto');\n"+
                                        "$('*', window.parent.MAIN.document).css('cursor', 'auto');\n"+
                                        "$('*', window.parent.parent.MAIN1.document).css('cursor', 'auto');\n"+
                                     "</script>\n";
                            }
                
                            byte[] kk = res2.getBytes();                          
                            outFile.write(kk);                           
                       }
                   } 

// ***************************************************
// getconfig.htm
// ***************************************************

                   if (fname.contains("getconfig.htm")) {
                       
                       String res2 = "";    
                       
                       String sPathRT = "";
                       String sPathScrub = "";
                       String sInstance = "";
                       if (fname.contains("inst=1")) {
                           sInstance = "1";
                           sPathRT = "./";
                           sPathScrub = "../";
                       }
                       
                       if (fname.contains("inst=2")) {
                           sInstance = "2";
                           sPathRT = "../../client2/rtserver/";
                           sPathScrub = "../../client2/";
                       }

                       if (fname.contains("inst=3")) {
                          sInstance = "3";
                           sPathRT = "../../client3/rtserver/";
                           sPathScrub = "../../client3/";
                       }

                       if (fname.contains("inst=4")) {
                           sInstance = "4";
                           sPathRT = "../../client4/rtserver/";
                           sPathScrub = "../../client4/";
                       }
                       if (fname.contains("inst=5")) {
                           sInstance = "5";
                           sPathRT = "../../projects/rtserver/";
                           sPathScrub = "../../projects/";
                       }

                       
                       res2 += "<b>Configuration for node : " + LocalIP + "</b><br><br>";
                       
                       res2 += "<b>rtserver</b><br>";                     
                       res2 += read_config(sPathRT + "config/www-server.properties","rtserver", sInstance);
                       
                       res2 += "<b>scanner</b><br>";                     
                       res2 += read_config(sPathScrub + "scrubber/config/www-rtbackup.properties", "rtbackup", sInstance);

                       res2 += "<b>mailer</b><br>";                     
                       res2 += read_config(sPathRT + "config/www-mailer.properties", "mailer", sInstance);

                       byte[] kk = res2.getBytes();
                       outFile.write(kk);
                       
                   }
                   
                   if (fname.contains("navbar2")) {
                        p("navbar");
                        File f = new File (root + File.separator + "cass/navbar2_bs_static.htm");    
                        String sBuffer = loadFileStr(f);

                        UserSession us = uuidmap.get(sAuthUUID);
                        String sUser;
                        if(us != null){
                            sUser = us.getUsername();
                        }else{
                            sUser = null;
                        }
                        
                        String res = getNavbarMenu(bUserAuthenticated, sUser, sBoxPassword, false, false);
                        
                        sBuffer = sBuffer.replace("***REP1***",res);
                        
                        sBuffer = sBuffer.replace("***REP2***",cluster);

                        byte[] kk = sBuffer.getBytes();
                        outFile.write(kk);
                        outFile.close();
                   }

                   if (fname.contains("logout.fn")) {                       
                       p("call logout.fn");
                       String result = "";
                       if (sAuthUUID.length() > 0) {
                           UserSession us = uuidmap.get(sAuthUUID);
                           if (us != null) {
                               uuidmap.remove(sAuthUUID);                               
                               result = "{" + "\"" + "status" + "\"" + ":" + "\"" + "ok" + "\"" + "}";
                           } else {
                               result = "{" + "\"" + "status" + "\"" + ":" + "\"" + "invalid" + "\"" + "}";                               
                           }
                       } else {
                           result = "{" + "\"" + "status" + "\"" + ":" + "\"" + "error-nouuid" + "\"" + "}";
                       }
                       outFile.write(result.getBytes());   
                   }

                    if (fname.contains("getsession.fn")) {
                        String version =  getVersion();
                        
                        RSACrypto rc = new RSACrypto();
                        String publickey = rc.getPublicKey();
                        String aesencrypt = GetConfig("aesencrypt", "config/www-server.properties");
                        if(aesencrypt == null || aesencrypt.isEmpty()){
                            aesencrypt = "true";
                            UpdateConfig("aesencrypt", aesencrypt, "config/www-server.properties");
                        }
//                        String aessizeString = GetConfig("aessize", "config/www-server.properties");
                        String aessizeString = "128";
//                        if(aessizeString == null || aessizeString.isEmpty()){
//                            aessizeString = "128";
//                            UpdateConfig("aessize", aessizeString, "config/www-server.properties");
//                            
//                        }

                        
                        String returningJson = "{\"publickey\": \"" + publickey.replace("\n", "").replace("\r", "") +  "\", "
                                + "\"aesencrypt\":\"" + aesencrypt +  "\", "
                                + "\"aessize\":\"" + aessizeString +  "\", "
                                + "\"version\":\"" + version + "\""
                                + "}";
                        byte[] kk = returningJson.getBytes();
                        outFile.write(kk);
                        outFile.close();
                   }
                                    
                   if (fname.contains("login.fn")) {
                       
                        String aesencrypt = GetConfig("aesencrypt", "config/www-server.properties");
                        boolean AES = (aesencrypt != null && aesencrypt.equals("true"));
                        boolean RSA = (sEncData != null && !sEncData.isEmpty());
                        
                        p("bMobile   :" + bMobile);
                        p("AES       :" + AES);
                        p("RSA       :" + RSA);
                        
                        if(bMobile || !bMobile || (AES && RSA) || !AES) {                       
 //                       if(!bMobile || (AES && RSA) || !AES) {                       
                            if(RSA){
                                RSACrypto rc = new RSACrypto();
                                String decryptedText = rc.decrypt(sEncData);

                                String[] tokens = decryptedText.split("&");  
                                Map<String, String> map = new HashMap<String, String>();  
                                for (String param : tokens)  
                                {  
                                    int n = param.split("=").length;
                                    System.out.println("#tokens -> " + n);
                                    String name = param.split("=")[0];  
                                    System.out.println("name -> " + name);
                                    String value = "";
                                    if (n > 2) {
                                        value = param.split("=")[1] + "=";
                                    } else {
                                        value = param.split("=")[1];
                                    }
                                    System.out.println("adding map '" + name + "' value '" + value + "'");
                                    map.put(name, value);  
                                }

                                sBoxUser = map.get("user");
                                sBoxUser = URLDecoder.decode(sBoxUser,"UTF-8");
                                
                                sBoxPassword = map.get("pass");
                                sBoxPassword = URLDecoder.decode(sBoxPassword,"UTF-8");

                                sKeyPassword = map.get("key");
                                sIV = map.get("iv");

                            }


                            //authenticate here
                            UserCollection userCollection = UserCollection.getInstance();
                            String res = "";

                            if (bLogUserPw) log("Username/password:  '" + sBoxUser + "' password: '" + sBoxPassword + "'", 0);

                            
                            boolean loggedIn;
                            boolean isUserAdmin = false;
                            
                            p("cluster = " + cluster);
                            
                            if(!cluster.isEmpty()){
                                p("cluster not empty = " + cluster);
                            
                                RemoteAccess ra = new RemoteAccess(cluster);//"c2acf0de-2406-4528-9b48-52afd57eabef"
                                String returned = ra.remoteSession();

                                JSONObject jtoken = (JSONObject)JSONValue.parse(returned);
                                String publicKey = jtoken.get("publickey").toString();
                                if(jtoken.get("aesencrypt") != null && jtoken.get("aessize") != null){
                                    aesEncryptSession = Boolean.parseBoolean(jtoken.get("aesencrypt").toString());
                                    aesSizeSession = Integer.parseInt(jtoken.get("aessize").toString());
                                }
                               
                                RSACrypto rc = new RSACrypto();
                                    
                                sKeyPassword = "";
                                String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
                                int strlength2 = 16;
                                for (int i2=0; i2<strlength2; i2++) {
                                    int randomNumber = (int) Math.floor(Math.random() * chars.length());
                                    sKeyPassword += chars.substring(randomNumber,randomNumber+1);
                                }
                                System.out.println("***** Gen sKeySPassword = " + sKeyPassword);

                                //sIV = rc.generateRandomIV();
                                
                                sIV = "";
                                chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
                                strlength2 = 16;
                                for (int i2=0; i2<strlength2; i2++) {
                                    int randomNumber = (int) Math.floor(Math.random() * chars.length());
                                    sIV += chars.substring(randomNumber,randomNumber+1);
                                }
                                
                                System.out.println("***** Gen sKeySPassword = " + sKeyPassword);

                                loggedIn = ra.remoteLogin(sBoxUser, sBoxPassword, publicKey, sKeyPassword, sIV);
                                uuid = ra.getUuid();
                                //Se guarda el ra para saber en el Manager que tiene que pedir y guardar los properties al nodo remoto
                                MultiClusterManager.getInstance().setRa(sBoxUser,ra);
                                //Se guardan temporalmente para usarlos si el usuario decide guardar 
                                //el cluster
                                sAuxBoxUser=sBoxUser;
                                sAuxBoxPassword=sBoxPassword;
                                sAuxCluster=cluster;
                            
                            }else{
                                p("case cluster empty");
                                System.out.println("**** before decode= "  + sBoxPassword);
                                try {
                                    sBoxPassword = URLDecoder.decode(sBoxPassword, "UTF-8");
                                } catch (Exception e) {                                        
                                    System.out.println("password contains actual % , skipping decode..."  + sBoxPassword);
                                }
                                System.out.println("**** after try decode ="  + sBoxPassword);
                                
                                final boolean userPasswordValid = userCollection.isUserPasswordValid(sBoxUser, sBoxPassword);
                                isUserAdmin = userCollection.getUserAdmin().getUsername().equalsIgnoreCase(sBoxUser);
                                final boolean allowOthersUsers = GetConfig("allowotherusers", "config/www-server.properties").equals("true");

            
                                p("[1] " + userPasswordValid);
                                p("[2] " + isUserAdmin);
                                p("[3] " + allowOthersUsers);
                                
                                loggedIn = (userPasswordValid && (isUserAdmin ||allowOthersUsers));
                                
                            }

                            if(loggedIn){
                                p("login-fn: CASE AUTH OK");
                                bAuth = true;
                                
                                MultiClusterManager.getInstance().saveUserAndPassword(sBoxUser,sBoxPassword);
                                
                                if(bMobile){
                                    res+= isUserAdmin;
                                    Date utcDate=DateUtil.getUTCDate();
                                    chats.addMessage(utcDate.getTime(), "EVENT", sBoxUser, "{'#'play'#':false,'#'msg'#':'#'Logged In'#'}");
                                }else{
                                    res = getNavbarMenu(true, sBoxUser, sBoxPassword, false, !cluster.isEmpty());
                                    Date utcDate=DateUtil.getUTCDate();
                                    chats.addMessage(utcDate.getTime(), "EVENT", sBoxUser, "{'#'play'#':false,'#'msg'#':'#'Logged In'#'}");
                                    res+= "<script type='name/javascript'>startAjaxCallTags();</script>";
                                }
                              } else{
                                  p("login-fn: CASE ELSE");
                                  res = getNavbarMenu(false, sBoxUser, sBoxPassword, true, !cluster.isEmpty());
                              }
                            byte[] kk = res.getBytes();
                            outFile.write(kk);
                            outFile.close();
                        }
                   } //end login.fn

// ***************************************************
// eula.htm
// ***************************************************
                   if (fname.contains("eula.htm")) {
                        File f = new File (root + File.separator + "cass/eula_static.htm");    
                        String sBuffer = loadFileStr(f);

                        File f2 = new File (root + File.separator + "cass/eula.txt"); 
                        String sEULABuffer = loadFileStr(f2);

                        String sBuffer3 = sBuffer.replace("***REP***", sEULABuffer); 

                        byte[] kk = sBuffer3.getBytes();
                        outFile.write(kk);                       
                   }
                   
// ***************************************************
// setup.htm
// ***************************************************
                                      
                   if (fname.contains("setup.htm")) {

                    String sState = GetConfig("state", "config/www-setup.properties");
                    boolean bServeStatic = true;
                    boolean isAdmin = false;
                    if (bUserAuthenticated){
                        UserSession us = uuidmap.get(sAuthUUID);
                        String sUser;
                        if(us != null){
                            sUser = us.getUsername();
                        }else{
                            sUser = null;
                        }       
                        UserCollection uc = UserCollection.getInstance();
                        User user = uc.getUsersByName(sUser);
                        if(user.getRole().equals("admin")){
                            isAdmin = true;
                        }
                    }                        
                    if (sState.equals("NEW") || isAdmin) {
                       
                       p("backupmode[c]: " + sBackupMode1);
                       p("backupmode[c]: " + sBackupMode2);
                       p("backupmode[c]: " + sBackupMode3);
                       p("backupmode[c]: " + sBackupMode4);
                       String res2 = "";
                       
                       p("Setup Page1 is: " + sSetupPage);                         
                       p("EULA: " + bAgreeEULA);  
                       
                       if (sState.equals("NEW")) {
                           if (sSetupPage.equals("0b")) {
                                //Agreed to EULA, Show page 1.
                                sSetupPage = "7";     
                                bAgreeEULA = true;
                           }
                           if (sSetupPage.equals("7") && !bAgreeEULA) {
                               //Requesting page 1, but hasn't agreed to EULA. Show EULA. 
                               sSetupPage = "0";     
                           }                               
                       } else {
                           if (sSetupPage.equals("0")) {
                               //wizard has already run at least once, show page 1.
                                sSetupPage = "7";                                                                                        
                           }                           
                       }                     
                                
                       Boolean bForcedSetup = false;                       
                       if (cSetupPage == null || cSetupPage.isEmpty()) {
                           if (sSetupPage.equals("10")) {
                                cSetupPage = "10";
                           }
                           bForcedSetup = true;
                       }
                       
                       //Thread.sleep(10000);
                       if(cSetupPage != null && !cSetupPage.isEmpty()) {    
                       
                        Integer currentSetupPage = Integer.parseInt(cSetupPage);                       
                        if (bIsPrevious) {
                            p("Prevois button ppressed, skipping store");
                        } else {
                            switch (currentSetupPage) {
                                case 1: {
                                    updateFromPage1();
                                    } break;    
                                case 7: {
                                    
                                    if(bIsExpressSetup){ 
                                       sMode = "server";
                                        sAllowPeer = "true";
                                        sSignature = getComputerName();
                                        sAllowOtherUsers = "false";
                                        sAllowRemote = "false";
                                        useraccounts = "";

                                        updateFromPage1();


                                        InputStream isscan=null;
                                        File f=null;

                                        int nRes = UpdateConfig("scannode", "on", appendage + "../scrubber/config/" + "www-rtbackup.properties");

                                        f = new File(appendage + "../scrubber/config/www-rtbackup.properties");
                                        if (f.exists() ) {
                                            Properties props = new Properties();
                                            
                                            isscan =new BufferedInputStream(new FileInputStream(f));
                                            props.clear();
                                            props.load(isscan);
                                            isscan.close();

                                            String p = props.getProperty("scandir"); //Path al archivo

                                            String pathUserHome = "";
                                            if(bWindowsServer){
                                                pathUserHome = System.getProperty("user.home");
                                                if(pathUserHome!=null && !pathUserHome.isEmpty()){
                                                    char[] stringArray = pathUserHome.toCharArray();
                                                    stringArray[0] = Character.toLowerCase(stringArray[0]);
                                                    pathUserHome = new String(stringArray);
                                                    pathUserHome = pathUserHome + "\\";
                                                    String sPathUserDocuments = pathUserHome + "Documents\\";
                                                    File f2 = new File(sPathUserDocuments);
                                                    if (f2.isDirectory() && f2.exists()) {
                                                        pathUserHome = sPathUserDocuments;                                          
                                                    }
                                                    log("pathUserHome(Win) = '" + pathUserHome + "'",1);
                                                }
                                            }else{
                                                File volumes=new File("/Volumes/");
                                                for(File volume:volumes.listFiles()){
                                                    if(volume.isDirectory()){
                                                        for (File folder:volume.listFiles()){
                                                            if (folder.isDirectory()){
                                                                if (folder.getName().equals("Users")){
                                                                   pathUserHome = folder.getAbsolutePath() + "/"; 
                                                                   log("pathUserHome(Mac) = '" + pathUserHome + "'",1); 
                                                                   log("user.home(Mac) = '" + System.getProperty("user.home") + "'",1);
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }                                                
                                            if(pathUserHome!=null && !pathUserHome.isEmpty()){
                                                nRes = UpdateConfig("scandir", URLEncoder.encode(pathUserHome,"UTF-8").replaceAll("\\+","%20"), p);
                                            }      
                                        }

                                        String sDoBackup = "no";
                                        p("sBackupMode = " + sBackupMode1);
                                        if (sBackupMode1.equals("on")) {
                                            sDoBackup = "yes";
                                            nRes = UpdateConfig("backuppath", sBackupDirectory, appendage + "../scrubber/config/" + "www-rtbackup.properties");
                                            p("nres = " + nRes);       
                                        }else{
                                            nRes = UpdateConfig("backuppath", "", "scrubber/config/" + "www-rtbackup.properties");
                                            p("nres = " + nRes);       
                                        }

                                        nRes = UpdateConfig("backupnode", sDoBackup, appendage + "../scrubber/config/" + "www-rtbackup.properties");
                                        p("nres = " + nRes);  

                                    }
                                } break;      
                                case 2: {
                                    updateFrompage2(sBlacklist);
                                } break;
                                case 8: {
                                    updateFrompage8();
                                } break;                                    
                                case 3: {
                                    //store settings that came from page 3
                                    int nRes = StoreFileExtensions(fname);
                                    p("nres = ");                                        
                                } break;
                                    
                                case 10: {
                                    //store setting that came from page 10
                                    System.out.println("---------------");
                                    System.out.println("---------------bCloudAmazon '" + bCloudAmazon );                                 
                                    System.out.println("---------------bDriveAmazonURL '" + bDriveAmazonURL );                                 
                                    System.out.println("---------------bForcedSetup '" + bForcedSetup );                                 
                                    System.out.println("---------------cloud drive: '" + code + "' " + code.length() );                                 
                                    System.out.println("---------------drive amazon: '" + drive_amazon + "' " + drive_amazon.length() );                                 
                                    System.out.println("---------------");
                                    
//                                    if (bForcedSetup && !bDriveAmazonURL && drive_amazon.length() == 0 && code.length() == 0) {
//                                        String sMode = GetConfig("drive_amazon", "config/www-cloud.properties");
//                                        if (sMode.equals("true")) {
//                                            p("drive_amazon / code not present in URL. setting to config value (true)...");
//                                            drive_amazon = "on";
//                                        } else {
//                                            p("drive_amazon / code not present in URL. setting to config value (false)...");
//                                            drive_amazon = "";
//                                        }                          
//                                    }
                                    
                                    if (drive_amazon.equals("") && bCloudAmazon && !bForcedSetup) {
                                        System.out.println("Turning OFF Amazon Cloud.");
                                        bCloudAmazon = false;
                                        String sFalse = "false";
                                        int nRes = NetUtils.UpdateConfig("drive_amazon", sFalse, "config/www-cloud.properties");
                                    }
                                    if (drive_amazon.equals("on") && !bCloudAmazon && !bForcedSetup) {
                                        System.out.println("Turning ON Amazon Cloud.");
                                        bCloudAmazon = true;
                                        String sFalse = "true";
                                        int nRes = NetUtils.UpdateConfig("drive_amazon", sFalse, "config/www-cloud.properties");
                                    }
                                    
                                    if (code.length() > 0) {
                                        int nRes = NetUtils.UpdateConfig("amazon_code", code, "config/www-cloud.properties");                                        
                                        bCloudAmazon = true;
                                        String sFalse = "true";
                                        nRes = NetUtils.UpdateConfig("drive_amazon", sFalse, "config/www-cloud.properties");
                                        //get a new code, so throw out the old tokens
                                        nRes = NetUtils.UpdateConfig("amazon_token", "", "config/www-cloud.properties");
                                        nRes = NetUtils.UpdateConfig("amazon_token_refresh", "", "config/www-cloud.properties");
                                        //also clear the batch# from previous backupo
                                        nRes = NetUtils.UpdateConfig("amazon_batch", "", "config/www-cloud-batches.properties");                                        
                                    }
                                    
                                } break;
                                    
                                case 9: {
                                    //store setting that came from page 9
                                    System.out.println("---------------");
                                    System.out.println("---------------analytics: '" + analytics + "' " + analytics.length() );                                 
                                    System.out.println("---------------");
                                    if (analytics.equals("") && bAnalyticsGA) {
                                        System.out.println("Turning OFF Analytics.");
                                        bAnalyticsGA = false;
                                        String sFalse = "false";
                                        int nRes = UpdateConfig("events_ga", sFalse, "config/www-analytics.properties");
                                    }
                                    if (analytics.equals("on") && !bAnalyticsGA) {
                                        System.out.println("Turning ON Analytics.");
                                        bAnalyticsGA = true;
                                        String sFalse = "true";
                                        int nRes = UpdateConfig("events_ga", sFalse, "config/www-analytics.properties");
                                    }
                                    
                                }
                                case 4: {
                                     
                                } break;
                                case 5: {
                                        //store setting that came from page 5

                                        int nRes = UpdateConfig("smtphost", sMailHost, "config/www-mailer.properties");
                                        p("nres = " + nRes);    

                                        nRes = UpdateConfig("smtpport", sMailPort, "config/www-mailer.properties");
                                        p("nres = " + nRes);    

                                        nRes = UpdateConfig("pop3user", sMailFrom, "config/www-mailer.properties");
                                        p("nres = " + nRes);    

                                        nRes = UpdateConfig("pop3pw", sMailFromPassword, "config/www-mailer.properties");
                                        p("nres = " + nRes);    

                                        nRes = UpdateConfig("pop3host", sMailHostPOP, "config/www-mailer.properties");
                                        p("nres = " + nRes);    

                                        nRes = UpdateConfig("pop3port", sMailPortPOP, "config/www-mailer.properties");
                                        p("nres = " + nRes);    

                                        nRes = UpdateConfig("allowmail", sMailAllowMail, "config/www-mailer.properties");
                                        p("nres = " + nRes); 
                                        nRes = UpdateConfig("sendmail", sMailSendMail, "config/www-mailer.properties");
                                        p("nres = " + nRes);
                                        nRes = UpdateConfig("scanmail", sMailScanMail, "config/www-mailer.properties");
                                        p("nres = " + nRes);
                                        nRes = UpdateConfig("notifymail", sMailNotify, "config/www-mailer.properties");
                                        p("nres = " + nRes);
                                        
                                        if (sMailScanMail.equals("on")) {
                                            int res = AddInbox("");
                                            p("res ADD INBOX: " + res);
                                        }
                                        
                                        BufferedWriter writer=new BufferedWriter(new FileWriter("../rtserver/config/allow.txt"));
                                        for(String account : qAccounts.split(";")){
                                            if(!account.isEmpty()){
                                                p("Setup[5] Allow:"+account);
                                                writer.write(account.startsWith("=")?account.substring(1):account);
                                                writer.newLine();
                                            }
                                        }
                                        writer.close();

                                        writer=new BufferedWriter(new FileWriter("../mailer/config/sendaccounts.txt"));
                                        for(String account : sendAccounts.split(";")){
                                            if(!account.isEmpty()){
                                                p("Setup[5] Send:"+account);
                                                writer.write(account.startsWith("=")?account.substring(1):account);
                                                writer.newLine();    
                                            }                                                
                                        }
                                        writer.close();
                                        
                                        writer=new BufferedWriter(new FileWriter("../mailer/config/mailgroups.txt"));
                                        for(String account : mailGroups.split(";")){
                                            if(!account.isEmpty()){
                                                p("Setup[5] Mail group:"+account);
                                                writer.write(account.startsWith("=")?account.substring(1):account);
                                                writer.newLine();
                                            }
                                        }
                                        writer.close();
                                    }break;
                                }
                            }
                        } else {
                           p("WARNING: cSetupPage is empty.");
                       }
                       
                        if(!sSetupPage.isEmpty()){
                            Integer nSetupPage = Integer.parseInt(sSetupPage);                       
                            p("Setup Page2 is: " + nSetupPage);    
                            
                            switch (nSetupPage) {

                                    case 0: {  
                                        File f = new File (root + File.separator + "cass/setup" + sSetupPage + ".htm");    
                                        String sBuffer = loadFileStr(f);

                                        File f2 = new File (root + File.separator + "cass/eula.txt"); 
                                        String sEULABuffer = loadFileStr(f2);

                                        String sBuffer3 = sBuffer.replace("***REP***", sEULABuffer); 

                                        byte[] kk = sBuffer3.getBytes();
                                        outFile.write(kk);

                                        bServeStatic = false;
                                    } break;    

                                case 7: {
                                        UserCollection userCollection = UserCollection.getInstance();
                                        User admin = userCollection.getUserAdmin();                                            

                                        String token = admin.getUsername();
                                        String res = "<div class=\"controls controls-row\"><span class=\"span2\">Username:</span><input class=\"span2\" id=\"adminuser\" name=\"adminuser\" type=\"text\" value=\"" + token + "\"></div>";

                                        res += "<div id=\"passdiv\">";
                                        res += "<div class=\"controls controls-row\"><span class=\"span2\">Password:</span><input class=\"span2\" id=\"adminpw1\" name=\"adminpw1\" type=\"password\" ></div>";
                                        res += "<div class=\"controls controls-row\"><span class=\"span2\">Confirm Password:</span> <input class=\"span2\" id=\"adminpw2\" name=\"adminpw2\" type=\"password\" ></div>";
    //                                            passdiv += "&nbsp;<a id=\"linktodispw\" href=\"javascript:toggleDisplayPassword();\">Display</a>";
                                        res += "</div>";

                                        File f = new File (root + File.separator + "cass/setup" + sSetupPage + ".htm");    

                                        String sBuffer = loadFileStr(f);
                                        sBuffer = sBuffer.replace("***REP6A***", res);
                                        byte[] kk = sBuffer.getBytes();
                                        outFile.write(kk);

                                        bServeStatic = false;

                                    }break; 

                                    case 1: {
                                            String sMode = GetConfig("mode", appendage + "../scrubber/config/" + "www-rtbackup.properties");
                                            File f = new File (root + File.separator + "cass/setup" + sSetupPage + ".htm");    

                                            String sBuffer = loadFileStr(f);                                    
                                            String newChar1a = "";
                                            String newChar1b = "";
                                            if (sMode.equals("server")) {
                                                //server mode
                                                newChar1a = "selected";                                        
                                            } else {
                                                //client mode
                                                newChar1b = "selected";
                                            }                                   
                                            sBuffer = sBuffer.replace("***REP1A***", newChar1a);                                    
                                            sBuffer = sBuffer.replace("***REP1B***", newChar1b);

                                            String sSignature = GetConfig("signature", appendage + "../scrubber/config/www-rtbackup.properties");

                                            String sAllow = GetConfig("allowpeer", appendage + "config/www-server.properties");
                                            String newChar2a = "";                                    
                                            String newChar2b = "";
                                            String newChar2c = "";
                                            if (sAllow.equals("true")) {
                                                newChar2a = "selected";
                                            }
                                            if (sAllow.equals("false")) {
                                                newChar2b = "selected";
                                            }
                                            if (sAllow.equals("ask")) {
                                                newChar2c = "selected";                                                                                
                                            }

                                            sBuffer = sBuffer.replace("***REP2A***", newChar2a);                                    
                                            sBuffer = sBuffer.replace("***REP2B***", newChar2b);                                    
                                            //String sBuffer6 = sBuffer5.replace("***REP2C***", newChar2c);                                    

                                            String computername = getComputerName();


                                            if(sState.equals("NEW")){                                            
                                                sBuffer = sBuffer.replace("***REP3***", computername);                                                
                                            }else{
                                                 sBuffer = sBuffer.replace("***REP3***", sSignature);
                                            }

                                            sBuffer = sBuffer.replace("***REP4***", sState);


                                            sBuffer = sBuffer.replace("***REP5***", computername);

    //                                            String serversJS = "";
    //                                            if(sState.equals("NEW")){
    //                                                serversJS = getAlteranteServers();                                                
    //                                            }
    //                                            sBuffer = sBuffer.replace("***REP5***", serversJS);

                                            UserCollection userCollection = UserCollection.getInstance();
                                            User admin = userCollection.getUserAdmin();                                            

                                            String token = admin.getUsername();
                                            String res;
                                            if(sState.equals("NEW")){
                                                res = "<div class=\"controls controls-row\"><span class=\"span2\">Username:</span><input class=\"span2\" id=\"adminuser\" name=\"adminuser\" type=\"text\" value=\"" + token + "\"></div>";
                                            }else{
                                                res = "<div class=\"controls controls-row\"><span class=\"span2\">Username:</span> <input class=\"span2\" style=\"display:none\" id=\"adminuser\" name=\"adminuser\" type=\"text\" value=\"" + token + "\"><span id=\"adminuserlabel\" class=\"span1\">  "
                                                        + token + "</span>&nbsp;&nbsp;&nbsp;<a id='linktoshowuser' href='#' onclick='showUsername();return false;'>Change admin username</a></div>";
                                            }

                                            token = admin.getPassword();
                                            String passdiv = "<div class=\"controls controls-row\"><span class=\"span2\">Password:</span><input class=\"span2\" id=\"adminpw1\" name=\"adminpw1\" type=\"password\" ></div>";
                                            passdiv += "<div class=\"controls controls-row\"><span class=\"span2\">Confirm Password:</span> <input class=\"span2\" id=\"adminpw2\" name=\"adminpw2\" type=\"password\" ></div>";
    //                                            passdiv += "&nbsp;<a id=\"linktodispw\" href=\"javascript:toggleDisplayPassword();\">Display</a>";
                                            if(sState.equals("NEW")){
                                                res += "<div id=\"passdiv\">";     
                                            }else if (token != null && !token.isEmpty()) {
                                                res += "&nbsp;&nbsp;&nbsp;<a id=\"linktoshowpw\" href=\"javascript:showPassword();\">Change admin password</a>";
                                                res += "<div id=\"passdiv\" style=\"display:none\">";
                                                passdiv += "&nbsp;&nbsp;&nbsp;<a id=\"linktohidepw\"  href=\"javascript:hidePassword();\">Cancel change password</a>";
                                            }else{
                                                res += "<div id=\"passdiv\">";
                                            }
                                            res += passdiv;
                                            res += "</div>";

                                            sBuffer = sBuffer.replace("***REP6A***", res);

                                            ArrayList<User> users = userCollection.getUsersByRole("user");
                                            String re = "";
                                            String rejs = "";
                                            for (User user : users) {
                                                re+="<option value='"+user.getUsername()+"'>"+user.getUsername()+"</option>" ;
                                                rejs+="userPass['"+user.getUsername()+"'] = '';";  
                                            }

                                            sBuffer = sBuffer.replace("***REP8A***", re);

                                            sBuffer = sBuffer.replace("***REP8B***", rejs);

                                            String allowotherusers = GetConfig("allowotherusers", "config/www-server.properties");
                                            if(allowotherusers.isEmpty()){
                                                allowotherusers = "false";
                                            }
                                            sBuffer = sBuffer.replace("***REP7***", allowotherusers);                                         


                                            String allowremote = GetConfig("allowremote", "config/www-server.properties");
                                            String newChar9a = "";                                    
                                            String newChar9b = "";
                                            String newChar10 = "";
                                            if (allowremote.equals("true")) {
                                                newChar9a = "selected";
                                                String sLink = "https://web.alterante.com/cass/index.htm?cluster=" + getClusterID();
                                                newChar10 = "<br>" + "Use this link to access this computer remotely:" + "<br>" + 
                                                            "<a target=\"blank\" href=\"" + sLink + "\">" + sLink + "</a>" + "<br><br>" +
                                                            "<button id=\"getmodalbutton\" type=\"button\" class=\"btn\" onclick=\"getmodal();\">Share All Files on this Computer</button>";
                                            }else{
                                                newChar9b = "selected";
                                            }

                                            sBuffer = sBuffer.replace("***REP9A***", newChar9a);                                    
                                            sBuffer = sBuffer.replace("***REP9B***", newChar9b);

                                            sBuffer = sBuffer.replace("***REP10***", newChar10);
                                            
                                            File f2 = new File (root + File.separator + "cass/remoteeula.txt"); 
                                            String sEULABuffer = loadFileStr(f2);

                                            sBuffer = sBuffer.replace("***REPEULA***", sEULABuffer); 


                                            byte[] kk = sBuffer.getBytes();
                                            outFile.write(kk);

                                            bServeStatic = false;     

                                    }break; 


                                    case 2:
                                    case 8: {


                                        String sMD5BlacklistTMP = NetUtils.calcMD5(appendage + "../scrubber/config/blacklist.txt");
                                        if (!sMD5BlacklistTMP.equals("ERROR")) {
                                            sMD5Blacklist = sMD5BlacklistTMP;
                                            p("MD5 = " + sMD5Blacklist);
                                            sBlackListOld = NetUtils.readFileIntoString(appendage + "../scrubber/config/blacklist.txt");                                      
                                        }                                    

                                        //generate page 2 dynamically

                                        String sRepFactor = GetConfig("rfactor", appendage + "../scrubber/config/www-rtbackup.properties");
                                        if (sRepFactor.length() == 0) {
                                            sRepFactor = "3"; //set default if param not available
                                        }

                                        String sScanNode = GetConfig("scannode", appendage + "../scrubber/config/www-rtbackup.properties");                                    
                                        String sScanNode1Checked = "";                                    
                                        p("scannode from file = " + sScanNode);
                                        if (sScanNode.equals("on")) {
                                            sScanNode1Checked="checked";
                                        }                                     

                                        String sScanNode2 = GetConfig("scannode", appendage + "../scrubber/config/www-rtbackup2.properties");                                    
                                        String sScanNode2Checked = "";                                    
                                        p("scannode2 from file = " + sScanNode2);
                                        if (sScanNode2.equals("on")) {
                                            sScanNode2Checked="checked";
                                        }                                     

                                        String sScanNode3 = GetConfig("scannode", appendage + "../scrubber/config/www-rtbackup3.properties");                                    
                                        String sScanNode3Checked = "";                                    
                                        p("scannode3 from file = " + sScanNode3);
                                        if (sScanNode3.equals("on")) {
                                            sScanNode3Checked="checked";
                                        }                                     

                                        String sScanNode4 = GetConfig("scannode", appendage + "../scrubber/config/www-rtbackup4.properties");                                    
                                        String sScanNode4Checked = "";                                    
                                        p("scannode4 from file = " + sScanNode4);
                                        if (sScanNode4.equals("on")) {
                                            sScanNode4Checked="checked";
                                        }                                     

                                        String sBackupMode1 = GetConfig("backupnode", appendage + "../scrubber/config/www-rtbackup.properties");
                                        String sBackup1Checked = "";                                    
                                        p("sBackupMode from file = " + sBackupMode1);
                                        if (sBackupMode1.equals("yes")) {
                                            sBackup1Checked="checked";
                                        } 

                                        String sBackupMode2 = GetConfig("backupnode", appendage + "../scrubber/config/www-rtbackup2.properties");
                                        String sBackup2Checked = "";                                    
                                        p("sBackupMode2 from file = " + sBackupMode2);
                                        if (sBackupMode2.equals("yes")) {
                                            sBackup2Checked="checked";
                                        } 

                                        String sBackupMode3 = GetConfig("backupnode", appendage + "../scrubber/config/www-rtbackup3.properties");
                                        String sBackup3Checked = "";                                    
                                        p("sBackupMode3 from file = " + sBackupMode3);
                                        if (sBackupMode3.equals("yes")) {
                                            sBackup3Checked="checked";
                                        } 

                                        String sBackupMode4 = GetConfig("backupnode", appendage + "../scrubber/config/www-rtbackup4.properties");
                                        String sBackup4Checked = "";                                    
                                        p("sBackupMode4 from file = " + sBackupMode4);
                                        if (sBackupMode4.equals("yes")) {
                                            sBackup4Checked="checked";
                                        } 

                                        String sSyncMode1 = GetConfig("syncnode", appendage + "../scrubber/config/www-rtbackup.properties");
                                        String sSync1Checked = "";                                    
                                        p("sSyncMode from file = " + sSyncMode1);
                                        if (sSyncMode1.equals("yes")) {
                                            sSync1Checked="checked";
                                        }

                                        String syncRules1 = LoadSyncRules(appendage + "../scrubber/config/router1.txt", "1");


                                        String sSyncMode2 = GetConfig("syncnode", appendage + "../scrubber/config/www-rtbackup2.properties");
                                        String sSync2Checked = "";                                    
                                        p("sSyncMode from file = " + sSyncMode2);
                                        if (sSyncMode2.equals("yes")) {
                                            sSync2Checked="checked";
                                        } 

                                        String syncRules2 = LoadSyncRules(appendage + "../scrubber/config/router2.txt", "2");

                                        String sSyncMode3 = GetConfig("syncnode", appendage + "../scrubber/config/www-rtbackup3.properties");
                                        String sSync3Checked = "";                                    
                                        p("sSyncMode from file = " + sSyncMode3);
                                        if (sSyncMode3.equals("yes")) {
                                            sSync3Checked="checked";
                                        } 

                                        String syncRules3 = LoadSyncRules(appendage + "../scrubber/config/router3.txt", "3");

                                        String sSyncMode4 = GetConfig("syncnode", appendage + "../scrubber/config/www-rtbackup4.properties");
                                        String sSync4Checked = "";                                    
                                        p("sSyncMode from file = " + sSyncMode4);
                                        if (sSyncMode4.equals("yes")) {
                                            sSync4Checked="checked";
                                        } 

                                        String syncRules4 = LoadSyncRules(appendage + "../scrubber/config/router4.txt", "4");



                                        File f = new File (root + File.separator + "cass/setup" + sSetupPage + ".htm");    

                                        String sBuffer = loadFileStr(f);
    //                                    BufferedReader reader = new BufferedReader(new FileReader(f));
    //                                    StringBuilder result = new StringBuilder();
    //                                    try {
    //                                        char[]buf = new char[1024];
    //                                        int x = 0;
    //                                        while ((x = reader.read(buf)) != -1) {
    //                                            result.append(buf,0,x);
    //                                        }
    //                                    } finally {
    //                                        reader.close();
    //                                    }
    //                                    
    //                                    String sBuffer = result.toString();
    //                                    p("String length = " + sBuffer.length());

                                        String sScanDir1 = GetScanDirectories("");
                                        String sScanDir2 = GetScanDirectories("2");
                                        String sScanDir3 = GetScanDirectories("3");
                                        String sScanDir4 = GetScanDirectories("4");

                                        String sBackupDir1 = GetBackupDirectories("");
                                        String sBackupDir2 = GetBackupDirectories("2");
                                        String sBackupDir3 = GetBackupDirectories("3");
                                        String sBackupDir4 = GetBackupDirectories("4");                                    

                                        String sSyncDir1 = GetSyncDirectories("");
                                        String sSyncDir2 = GetSyncDirectories("2");
                                        String sSyncDir3 = GetSyncDirectories("3");
                                        String sSyncDir4 = GetSyncDirectories("4");                                    

                                        String sBlackList1 = GetBlackList("");


                                        //unit 1 (scan)
                                        String sBuffer2 = sBuffer.replace("***REP***", sScanDir1);
                                        String sBuffer3 = sBuffer2.replace("***REP2***", sScanNode1Checked);

                                        String sMode = GetConfig("mode", appendage + "../scrubber/config/" + "www-rtbackup.properties");
                                        String slider = "";
                                        if (sMode.equals("server")) {
                                            slider =   "<span class=\"help-block\" >Please select how many copies of your files you would like stored accross your storage pool. </span>\n" +
                                            "<input type=\"text\" class=\"span2\" value=\""+ sRepFactor +"\" name=\"rfactor\" id=\"slsvelocidad\" style='margin:0 auto auto 50px;' />\n" +
                                            "<span id='speedtxt' class='active'>"+ sRepFactor +" copies</span><br>";
                                        }

                                        String sBuffer4 = sBuffer3.replace("***REP3***", slider);

                                        //unit 2 (scan)
                                        String sBuffer5 = sBuffer4.replace("***REPb***", sScanDir2);
                                        String sBuffer6 = sBuffer5.replace("***REP2b***", sScanNode2Checked);

                                        //unit 3 (scan)
                                        String sBuffer7 = sBuffer6.replace("***REPc***", sScanDir3);
                                        String sBuffer8 = sBuffer7.replace("***REP2c***", sScanNode3Checked);

                                        //unit 4 (scan)
                                        String sBuffer9 = sBuffer8.replace("***REPd***", sScanDir4);
                                        String sBuffer10 = sBuffer9.replace("***REP2d***", sScanNode4Checked);

                                        //unit 1 (backup)
                                        String sBuffer11 = sBuffer10.replace("***BAK***", sBackupDir1);
                                        String sBuffer12 = sBuffer11.replace("***BAK2***", sBackup1Checked);

                                        //unit 2 (backup)
                                        String sBuffer13 = sBuffer12.replace("***BAKb***", sBackupDir2);
                                        String sBuffer14 = sBuffer13.replace("***BAK2b***", sBackup2Checked);

                                        //unit 3 (backup)
                                        String sBuffer15 = sBuffer14.replace("***BAKc***", sBackupDir3);
                                        String sBuffer16 = sBuffer15.replace("***BAK2c***", sBackup3Checked);

                                        //unit 4 (backup)
                                        String sBuffer17 = sBuffer16.replace("***BAKd***", sBackupDir4);
                                        String sBuffer18 = sBuffer17.replace("***BAK2d***", sBackup4Checked);

                                        //unit 1 (sync)
                                        String sBuffer19 = sBuffer18.replace("***SNC***", sSyncDir1);
                                        String sBuffer20 = sBuffer19.replace("***SNC2***", sSync1Checked);
                                        sBuffer20 = sBuffer20.replace("***SNC3***", syncRules1);
                                        if(syncRules1.isEmpty()){
                                            sBuffer20 = sBuffer20.replace("***SNC4***", "style=\"display: none\"");
                                        }else{
                                            sBuffer20 = sBuffer20.replace("***SNC4***", "");
                                        }

                                        //unit 2 (sync)
                                        String sBuffer21 = sBuffer20.replace("***SNCb***", sSyncDir2);
                                        String sBuffer22 = sBuffer21.replace("***SNC2b***", sSync2Checked);
                                        sBuffer22 = sBuffer22.replace("***SNC3b***", syncRules2);
                                        if(syncRules2.isEmpty()){
                                            sBuffer22 = sBuffer22.replace("***SNC4b***", "style=\"display: none\"");
                                        }else{
                                            sBuffer22 = sBuffer22.replace("***SNC4b***", "");
                                        }

                                        //unit 3 (sync)
                                        String sBuffer23 = sBuffer22.replace("***SNCc***", sSyncDir3);
                                        String sBuffer24 = sBuffer23.replace("***SNC2c***", sSync3Checked);
                                        sBuffer24 = sBuffer24.replace("***SNC3c***", syncRules3);
                                        if(syncRules3.isEmpty()){
                                            sBuffer24 = sBuffer24.replace("***SNC4c***", "style=\"display: none\"");
                                        }else{
                                            sBuffer24 = sBuffer24.replace("***SNC4C***", "");
                                        }                                    

                                        //unit 4 (sync)
                                        String sBuffer25 = sBuffer24.replace("***SNCd***", sSyncDir4);
                                        String sBuffer26 = sBuffer25.replace("***SNC2d***", sSync4Checked);
                                        sBuffer26 = sBuffer26.replace("***SNC3d***", syncRules4);
                                        if(syncRules4.isEmpty()){
                                            sBuffer26 = sBuffer26.replace("***SNC4d***", "style=\"display: none\"");
                                        }else{
                                            sBuffer26 = sBuffer26.replace("***SNC4d***", "");
                                        }

                                        //Blacklist 
                                        String sBuffer27 = sBuffer26.replace("***BL1***", sBlackList1);

                                        sBuffer27 = sBuffer27.replace("***REPSTATE***", sState);



                                        String recommend = GetConfig("recommend", "config/www-server.properties");

                                        String recomendhtml = "";
                                        if(recommend.equals("true")){
                                            recomendhtml = "<legend>Wizard options</legend>\n" +
                                            "<label class=\"checkbox\"><input id='ckstm' type=\"checkbox\" name=\"scantreemode\" onclick=\"scanTreeModeStartStop(this);\"  >Scan folders automatically and make recommendations for me</label>\n" +
                                            "<br><br>";
                                        }

                                        sBuffer27 = sBuffer27.replace("***REPRECOM***", recomendhtml);

                                        String ndrives = GetConfig("ndrives", "config/www-server.properties");

                                        if(ndrives.isEmpty()){
                                            ndrives = "2";
                                        }

                                        sBuffer27 = sBuffer27.replace("***REPNDRIVES***", ndrives);


                                        String optionnodes = "";
                                        if(sMode.equals("client")){
                                            String sSignature = GetConfig("signature", appendage + "../scrubber/config/www-rtbackup.properties");
                                            String urlgetnodes = "/cass/getnodes.fn";
                                            String urlserver  = getAlteranteServerURL(sSignature);
                                            urlgetnodes = urlserver + urlgetnodes;
                                            NetUtils.getfile(urlgetnodes, "getnodesext.fn", 3, 500, 10000);
                                            optionnodes = loadFileStr(new File("getnodesext.fn"));
                                        }else{
                                           optionnodes = getnodesfn();
                                        }

                                       sBuffer27 = sBuffer27.replace("***SNCNODES***", optionnodes);

                                        FileInputStream fileExtensions = new FileInputStream(appendage + "../scrubber/config/FileExtensions_All.txt");
                                        Scanner objScanner = new Scanner(fileExtensions);
                                        String res = "";

                                        while (objScanner.hasNext()) {                
                                            String line = objScanner.nextLine();


                                            StringTokenizer stl = new StringTokenizer(line.trim(), ",", true);

                                            String key = stl.nextToken();

                                            if (!key.equals("@")) {
                                                res+=" <option value=\""+key+"\">"+key+"</option>";
                                            }
                                        }

                                        sBuffer27 = sBuffer27.replace("***SNCFILEEXT***", res);

                                        byte[] kk = sBuffer27.getBytes();
                                        outFile.write(kk);

                                        bServeStatic = false;


                                    } break;            
                                    case 3: {                                                                     
                                        //generate page 3 dynamically
                                        File f = new File (root + File.separator + "cass/setup" + sSetupPage + ".htm");    
                                        String sBuffer = loadFileStr(f);

                                        String newChar = GetFileExtensions();
                                        String sBuffer2 = sBuffer.replace("***REP***", newChar);

                                        sBuffer2 = sBuffer2.replace("***REPSTATE***", sState);

                                        byte[] kk = sBuffer2.getBytes();
                                        outFile.write(kk);

                                        bServeStatic = false;

                                    }  break;     

                                    case 10: {
                                        //generate page 10 dynamically (cloud drive)
                                        
                                        String sMode = NetUtils.getConfig("drive_amazon", "config/www-cloud.properties");
                                        String sChecked = "";
                                        p("Smode from file = " + sMode);
                                        if (sMode.equals("true")) {
                                            sChecked="checked";
                                        }    
                                        
                                        String sCode = NetUtils.getConfig("amazon_code", "config/www-cloud.properties");
                                                                               
                                        File f = new File (root + File.separator + "cass/setup" + sSetupPage + ".htm");    
                                        String sBuffer = loadFileStr(f);
                                        String sBuffer2 = sBuffer.replace("***REPC***", sChecked);
                                        String sBuffer3 = sBuffer2.replace("***REP1***", sCode);
                                        
                                        String sRep = "Save";
                                        if (code.length() > 0) {
                                            sRep = "Home";
                                        } 
                                        String sBuffer4 = sBuffer3.replace("***REP2***", sRep);
                                        
                                        String sBuffer5 = sBuffer4.replace("***REP3***", Integer.toString(port));
                                        
                                        byte[] kk = sBuffer5.getBytes();
                                        outFile.write(kk);

                                        bServeStatic = false;
                                        
                                        break;
                                    }
                                    case 9: {
                                        //generate page 9 dynamically (privacy)

                                        String sMode = GetConfig("events_ga", "config/www-analytics.properties");
                                        String sChecked = "";
                                        
                                        p("Smode from file = " + sMode);
                                        if (sMode.equals("true")) {
                                            sChecked="checked";
                                        } 
                                        
                                        File f = new File (root + File.separator + "cass/setup" + sSetupPage + ".htm");    

                                        String sBuffer = loadFileStr(f);
                                        
                                        String sBuffer2 = sBuffer.replace("***REPC***", sChecked);
                                        
                                        byte[] kk = sBuffer2.getBytes();
                                        outFile.write(kk);

                                        bServeStatic = false;
                                        break;
                                        
                                    }
                                    case 4: {

                                        //generate page 4 dynamically

                                        String sMode = GetConfig("backupnode", appendage + "../scrubber/config/www-rtbackup.properties");
                                        String sChecked = "";

                                        p("Smode from file = " + sMode);
                                        if (sMode.equals("yes")) {
                                            sChecked="checked";
                                        } 

                                        sMode = GetConfig("syncnode", appendage + "../scrubber/config/www-rtbackup.properties");
                                        String sChecked2 = "";

                                        p("Syncmode from file = " + sMode);
                                        if (sMode.equals("yes")) {
                                            sChecked2="checked";
                                        } 



                                        File f = new File (root + File.separator + "cass/setup" + sSetupPage + ".htm");    

                                        String sBuffer = loadFileStr(f);
    //                                    BufferedReader reader = new BufferedReader(new FileReader(f));
    //                                    StringBuilder result = new StringBuilder();
    //                                    try {
    //                                        char[]buf = new char[1024];
    //                                        int x = 0;
    //                                        while ((x = reader.read(buf)) != -1) {
    //                                            result.append(buf,0,x);
    //                                        }
    //                                    } finally {
    //                                        reader.close();
    //                                    }
    //                                    
    //                                    String sBuffer = result.toString();
    //                                    p("String length = " + sBuffer.length());

                                        String newCharBackup = GetBackupDirectories("");                                    
                                        String newCharSync = GetSyncDirectories();

                                        String sBuffer2 = sBuffer.replace("***REP***", newCharBackup);                                    
                                        String sBuffer3 = sBuffer2.replace("***REP2***", sChecked);
                                        String sBuffer4 = sBuffer3.replace("***REP3***", newCharSync);
                                        String sBuffer5 = sBuffer4.replace("***REP4***", sChecked2);

                                        byte[] kk = sBuffer5.getBytes();
                                        outFile.write(kk);

                                        bServeStatic = false;
                                    } break;

                                    case 5: {

                                        p(shareKey);
                                        p(shareType);
                                        
                                                
                                        //generate page 5 dynamically

                                        File f = new File (root + File.separator + "cass/setup" + sSetupPage + ".htm");                                        
                                        String sBuffer = loadFileStr(f);




                                        sBuffer = GetEmailInfo(sBuffer);

                                        sBuffer = GetEmailDomainsInfo(sBuffer);


                                        String newChar = LoadAccounts("../rtserver/config/allow.txt");//GetEmailInfo();
                                        sBuffer = sBuffer.replace("***REPA1***", newChar);

                                        sBuffer = LoadAccountsWithGroups(sBuffer);//GetEmailInfo();

                                        newChar = LoadMailGroups();
                                        sBuffer = sBuffer.replace("***REPB2***", newChar);

                                        sBuffer = sBuffer.replace("***REPSTATE***", sState);

                                        sBuffer = sBuffer.replace("***REPOPENMODAL***", shareOpenmodal);
                                        sBuffer = sBuffer.replace("***REPTYPE***", shareType);
                                        sBuffer = sBuffer.replace("***REPKEY***", shareKey);
                                        if(!shareType.isEmpty() && !shareKey.isEmpty()){
                                            String link = "alterante://access?token=" + ShareController.getInstance().getStoredShareToken(shareKey).getShare_token();
                                            sBuffer = sBuffer.replace("***REPLINK***", link);
                                        }

                                        byte[] kk = sBuffer.getBytes();
                                        outFile.write(kk);

                                        bServeStatic = false;


                                    } break;   
                                    case 6: {
                                        //page 6

                                        UpdateConfig("state", "SCAN", "config/www-setup.properties");


                                    } break;
                                                                   
                            }
                        }
                    }else {
                             String res = "<p>" + "Please login! </p>";
                             byte[] kk = res.getBytes();
                             outFile.write(kk);
                             bServeStatic = false;

                    }
                       String filename = root + File.separator + "cass/setup" + sSetupPage + ".htm";
                       is = new FileInputStream(filename);                       
                       
                       if (bServeStatic) {
                            try {
                                 int n;
                                 while ((n = is.read(buf)) > 0) {
                                     outFile.write(buf, 0, n);
                                 }
                            } finally {
                                 is.close();
                            }                                                  
                       } else {
                           p("Skip Serve static.");
                       }
                       outFile.close();
                   }
// ***************************************************
// testemail.fn
// ***************************************************
                   if (fname.contains("testemail.fn")) { 
                                            
                       MailerFuncs mf = new MailerFuncs();

                       String res ="";
                        try{
                            
                            mf.justConnectSMTP(sMailHost, sMailPort, sMailFrom,sMailFromPassword);
                                res = "SMTP configuration is OK. ";
                        }catch (MessagingException e) {
                            p("smtp: connection fails");
                            p(e.getMessage());
                            res = "Your SMTP configuration is NOT OK. Please check. ";
                        }
                       
                       
                       try{
                           
                            mf.justConnectPop3(sMailHostPOP, sMailFrom, sMailFromPassword, sMailPortPOP);
                            res += "POP3 configuration is OK.";
                       }catch(Exception e){
                            p("pop3: connection fails");
                            p(e.getMessage());
                            res += "Your POP3 configuration is NOT OK. Please check.";                            
                       }
                       
                        byte[] kk = res.getBytes();
                        outFile.write(kk);
                        outFile.close();
                        
                   }

                    if (fname.contains("getalteranteservers.fn")) { 
                        String serversJS = getAlteranteServers();
                        
                        byte[] kk = serversJS.getBytes();
                        outFile.write(kk);
                        outFile.close();
                        
                   }
                   
                   
// ***************************************************
// setconfig.htm
// ***************************************************

                   if (fname.contains("setconfig.htm")) {  
                       
                       String res2 = "";
                       res2 += write_config(fname);
 
                       p(res2);
                       
                       //byte[] kk = res2.getBytes();
                       
                       String resp = "Settings have been updated.";
                       byte[] kk = resp.getBytes();
                       outFile.write(kk);
                       
                   }
                   
// ***************************************************
// nodestats.htm
// ***************************************************

                   if (fname.contains("nodestats.htm")) {
                            if (bPasswordValid && bUserAuthenticated) {


                               String res = "";
                               String res2 = "";
                               res = wf.echonode("nodes", dbmode);

                               res2 += "<html>";
                               res2 += "<link href=\"bootstrap/css/bootstrap.min.css\" rel=\"stylesheet\">";
                               res2 += "<table class=\"table table-striped\">";

                               res2 += "<tr>" + 
                                             "<th>MachineName</th>" + 
                                             "<th>UUID</th>" + 
                                             "<th>IP Address</th>" + 
                                             "<th>IP Port</th>" + 
                                             "<th>Netty Port</th>" + 
                                             "<th>Free Space (GB)</th>" + 
                                             "<th>Backup?</th>" + 
                                             "<th>LastBat</th>" + 
                                             "<th>LastSeq</th>" + 
                                             "<th>LastPing</th>" + 
                                             "<th>Sync?</th>" + 
                                             "<th>Scan?</th>" + 
                                             "<th>Settings</th>" + 
                                             "<th>Setup</th>" + 
                                             "</tr>";

                               Scanner scan = new Scanner(res);
                               while (scan.hasNextLine()) {
                                   String nLine = scan.nextLine();
                                   p("processing line '" + nLine + "'");
                                   String delimiter = ",";
                                   StringTokenizer stl = new StringTokenizer(nLine.trim(), delimiter, true);

                                   String sNodeID = "";
                                   String sNodeIP = "";
                                   String sNodePort = "";
                                   String sNodeNettyPort = "";
                                   String sNodeBackup = "";
                                   String sNodeFree = "";
                                   String sNodeSync = "";
                                   String sNodeLastBatch = "";
                                   String sNodeLastSeq = "";
                                   String sNodeLastPing = "";
                                   String sNodeScan = "";
                                   String sNodeMachine = "";

                                   try {
                                     sNodeID = stl.nextToken();
                                     stl.nextToken();
                                     sNodeIP = stl.nextToken();
                                     stl.nextToken();
                                     sNodePort = stl.nextToken();
                                     stl.nextToken();
                                     sNodeNettyPort = stl.nextToken();
                                     stl.nextToken();
                                     sNodeBackup = stl.nextToken();
                                     stl.nextToken();
                                     sNodeFree = stl.nextToken();
                                     stl.nextToken();
                                     sNodeSync = stl.nextToken();
                                     stl.nextToken();
                                     sNodeLastBatch = stl.nextToken();
                                     stl.nextToken();
                                     sNodeLastSeq = stl.nextToken();
                                     stl.nextToken();
                                     sNodeLastPing = stl.nextToken();
                                     stl.nextToken();
                                     sNodeMachine = stl.nextToken();
                                     
                                     SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss aaa");
                                     sNodeLastPing = sdf.format(Long.valueOf(sNodeLastPing).longValue());

                                   } catch (Exception e) {
                                     e.printStackTrace();
                                   }

                                   double lFree = 0;
                                   DecimalFormat df = new DecimalFormat("###,###.##");
                                   try {
                                       lFree = Double.parseDouble(sNodeFree) / 1024 / 1024 / 1024;
                                   } catch (Exception e) {
                                       e.printStackTrace();
                                   }
                                   p("lFree: '" + lFree + "'");

                                   String sLink = "http://" + sNodeIP + ":" + sNodePort + "/cass/getconfig.htm?inst=1";
                                   String sLink2 = "http://" + sNodeIP + ":" + sNodePort + "/cass/getconfig.htm?inst=2";
                                   String sLink3 = "http://" + sNodeIP + ":" + sNodePort + "/cass/getconfig.htm?inst=3";
                                   String sLink4 = "http://" + sNodeIP + ":" + sNodePort + "/cass/getconfig.htm?inst=4";
                                   String sLink5 = "http://" + sNodeIP + ":" + sNodePort + "/cass/getconfig.htm?inst=5";
                                   
                                   String sLinkWiz = "http://" + sNodeIP + ":" + sNodePort + "/cass/setup.htm?spage=1";
                                   res2 += "<tr>" + 
                                             "<td>" + sNodeMachine + "</td>" + 
                                             "<td>" + sNodeID + "</td>" + 
                                             "<td>" + sNodeIP + "</td>" + 
                                             "<td>" + sNodePort + "</td>" + 
                                             "<td>" + sNodeNettyPort + "</td>" + 
                                             "<td>" + df.format(lFree) + "</td>" + 
                                             "<td>" + sNodeBackup + "</td>" + 
                                             "<td>" + sNodeLastBatch + "</td>" +
                                             "<td>" + sNodeLastSeq + "</td>" +
                                             "<td>" + sNodeLastPing + "</td>" +
                                             "<td>" + sNodeSync + "</td>" +
                                             "<td>" + sNodeScan + "</td>" +
                                             "<td>" + "<a href=" + "\"" + sLink + "\"" + ">Config</a>" + "&nbsp" +
                                             "<a href=" + "\"" + sLink2 + "\"" + ">Client2</a>" + "&nbsp" +
                                             "<a href=" + "\"" + sLink3 + "\"" + ">Client3</a>" + "&nbsp" +
                                             "<a href=" + "\"" + sLink4 + "\"" + ">Client4</a>" + "&nbsp" +
                                             "<a href=" + "\"" + sLink5 + "\"" + ">Server</a>" + "</td>" +
                                             "<td>" + "<a href=" + "\"" + sLinkWiz + "\"" + ">Config</a>" + "</td>" +
                                           "</tr>";
                                   
                               }

                               res2 += "</table>";
                               res2 += "<html>";
                               byte[] kk = res2.getBytes();                          
                               outFile.write(kk);                          
                        } else {
                             String res2 = "";
                             res2 += "Login required(nodestats).</p>";                        
                             byte[] kk = res2.getBytes();                          
                             outFile.write(kk);                                                 
                        } 
                        outFile.close();
                   }

                   
                   if (fname.contains("nodeinfo.fn")) {
                        if (bUserAuthenticated) {
                            p("Processing NodeInfo()");                            
                            String res2 = getNodeInfo();
                            System.out.println("res2 len = " + res2.length());
                            byte[] kk = res2.getBytes();                          
                            outFile.write(kk);                         
                        }
                   }

                   if (fname.contains("getnodes.fn")) {
                        String res2 = getnodesfn();                               
                        byte[] kk = res2.getBytes();                          
                        outFile.write(kk);                         
                   }
                    
            if (fname.contains("getextensions.fn")) {
                String res2 = getExtensions();                               
                byte[] kk = res2.getBytes();                          
                outFile.write(kk);                                         
            }   
            
            if (fname.contains("getfileextensions.fn")) {
                    
                FileInputStream fileExtensions = new FileInputStream(appendage + "../scrubber/config/FileExtensions_All.txt");
                Scanner objScanner = new Scanner(fileExtensions);
                String res = "";
                
                while (objScanner.hasNext()) {                
                    String line = objScanner.nextLine();


                    StringTokenizer stl = new StringTokenizer(line.trim(), ",", true);

                    String key = stl.nextToken();

                    if (!key.equals("@")) {
                        res += "extensions.push('" + key + "');";
                    }
                }
                byte[] kk = res.getBytes();                          
                outFile.write(kk);
            }
// ***************************************************
// Batch stats (cass7.php)
// ***************************************************

                   // Batch stats
                   if (fname.contains("cass7.php")) {
                            if (bPasswordValid && bUserAuthenticated) {

                               String res = "";
                               //Date ts_start = Calendar.getInstance().getTime();
                               for (i=0;i<8;i++) {
                                   Calendar cal = Calendar.getInstance();
                                   cal.add(Calendar.DATE, -i);
                                   SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                                   String sDate = sdf.format(cal.getTime());
                                   //p("***id sDate #: " + sDate);

                                   boolean bRetry = true;
                                   res = "";
                                   while (bRetry) {
                                     res = "<b>" + sDate + "</b><br>";
                                     res += wf.getbatches(sDate, sPassword, sView, sFileType, sDaysBack, sNumCol, sNumObj, dbmode, sScreenSize);
                                     res += "<br>";
                                     if (!res.contains("ERROR_")) {
                                         bRetry = false;
                                     } else {
                                         //error;
                                         p(res + " SLEEPING BEFORE RETRY");
                                         long lSleep = 100;
                                         try {
                                             Thread.sleep(lSleep);
                                         } catch (Exception ex) {

                                         }                                    
                                     }
                                   }
                                   byte[] kk = res.getBytes();
                                   outFile.write(kk);
                               }

                               String sEnd  = "\n</body></html>\n"; 
                               outFile.write(sEnd.getBytes());

                               outFile.close();
                        } else {
                             String res2 = "";
                             res2 += "Login required(batchstats).</p>";                        
                             byte[] kk = res2.getBytes();                          
                             outFile.write(kk);                                                 
                        } 
                   }
                   
                   //query.fn (mobile device)
                   if (fname.contains("query.fn")) {
                       UserSession us = uuidmap.get(sAuthUUID);

                       if (bUserAuthenticated && !us.isRemote()){
                            InetAddress clientIP = NetUtils.getLocalAddressNonLoopback2();  
                            String LocalIP = "127.0.0.1";
                            if (clientIP != null) {
                                 LocalIP = clientIP.getHostAddress();                            
                            }

                            //UserSession us = uuidmap.get(sAuthUUID);
                            String sUser;
                            if(us != null){
                                sUser = us.getUsername();
                            }else{
                                sUser = null;
                            }
                            String ClientIP = s.getInetAddress().getHostAddress();
                            boolean bOrderAsc = true;
                            p("sSortOrder[2]: '" + sSortOrder + "'");
                            if (sSortOrder.equalsIgnoreCase("desc")) bOrderAsc = false;
                            p("bOrderAsc[2]: " + bOrderAsc);

                            String res = wf.echoh2mobile(sFoo2, 
                                             root.toString(), 
                                             sNumObj, 
                                             sFileType, 
                                             sNumCol, 
                                             sPassword, 
                                             sDaysBack, 
                                             sDateStart, 
                                             LocalIP, 
                                             Integer.toString(port),
                                             bCloudHosted,
                                             bAWSHosted,
                                             ClientIP,
                                             dbmode,
                                             sUser,
                                             bWritePerfLog,
                                             bBase64Pic,
                                             10,
                                             sScreenSize,
                                             bOrderAsc);    

                            byte[] kk = res.getBytes();
                                   outFile.write(kk);
                                   outFile.close();
                       }else{
                           
                           //try to see if a remote session
                           if (us.isRemote()) {
                                 System.out.println("@@@@@******* REMOTE QUERY.FN!!!!");
                                 RemoteAccess ra = new RemoteAccess(us.getRemoteCluster());
                                 ra.setUuid(us.getUuid());

                                 String returned = ra.remoteQuery(sFileType, sDaysBack, sFoo2, "json", sNumObj, sDateStart);
                                
                                 System.out.println("@@@@@******* len returned = " + returned.length());

                                 byte[] kk = returned.getBytes();
                                 outFile.write(kk);
                                 outFile.close();
                                 
//                                 if(!returned.startsWith("ERROR")){
//                                    StringBuilder sb = HtmlFromJSON.getHTML(returned, sFoo2, sDateStart, sFileType, sDaysBack, sNumObj, sNumCol, password, sView, sScreenSize, us,  Integer.parseInt(sPage), previousDate);
//                                    sb.append(HtmlFromJSON.getTagsRemote(ra.remoteGetTags()));
//
//                                    res = sb.toString();
//                                 } else{
//                                    res = returned;
//                                 } 
                              
                           } else {
                                byte[] kk = "".getBytes();
                                outFile.write(kk);
                                outFile.close();                               
                           }

                       }
                       
                       
                   }
                   
                   //sidebar.fn (mobile device)
                   if (fname.contains("sidebar.fn")) {
                       if (bUserAuthenticated){
                            UserSession us = uuidmap.get(sAuthUUID);
                            if (us.isRemote()) {
                                System.out.println("REMOTE SIDEBAR");
                                RemoteAccess ra = new RemoteAccess(us.getRemoteCluster());
                                ra.setUuid(us.getUuid());
                                String param = fname.split("sidebar.fn\\?")[1];
                                System.out.println("REMOTE SIDEBAR PARAM: '" + param + "'");
                                String json = ra.remoteSidebar();
                                outFile.write(json.getBytes());
                                outFile.close();                                                                                                
                            } else {
                                String sUser;
                                if(us != null){
                                    sUser = us.getUsername();
                                }else{
                                    sUser = null;
                                }  
                                String ClientIP = s.getInetAddress().getHostAddress();
                                String res = wf.echoh2mobile(sFoo2, 
                                                 root.toString(), 
                                                 sNumObj, 
                                                 sFileType, 
                                                 sNumCol, 
                                                 sPassword, 
                                                 sDaysBack, 
                                                 sDateStart, 
                                                 LocalIP, 
                                                 Integer.toString(port),
                                                 bCloudHosted,
                                                 bAWSHosted,
                                                 ClientIP,
                                                 dbmode,
                                                 sUser,
                                                 bWritePerfLog,
                                                 bBase64Pic,
                                                 11,
                                                 sScreenSize,
                                                 true);    
                                byte[] kk = res.getBytes();
                                       outFile.write(kk);
                                       outFile.close();                       
                            }       
                                
                        }
                   }
                    if (fname.contains("checkuseruuid.fn")) {
                        UserSession userSession = uuidmap.get(sUUID);
                        if(userSession != null){
                            byte[] kk = userSession.getUsername().getBytes();
                            outFile.write(kk);
                            outFile.close();
                        }
                    }
                    if (fname.contains("getfile.fn") || fname.contains("getfilepart.fn")) {
                       p("-----------------getfile.fn");
                       int chunk_size = filechunk_size;
                       int chunk_offset = filechunk_offset;
                       boolean file_partial = false;
                       if ((fname.contains("getfilepart.fn")) || (fname.contains("getfile.fn") && chunk_size > 0)) {
                           file_partial = true;
                       }
                       
                       if (bUserAuthenticated){
                            
                            UserSession us = uuidmap.get(sAuthUUID);
                            if(us.isRemote() || isValidMultiClusterID(sMultiClusterID)){
                                System.out.println("@@@@!!!! REMOTE GET FILE");
                                RemoteAccess ra =null;
                                if(isValidMultiClusterID(sMultiClusterID)){
                                    ra=MultiClusterManager.getInstance().getRA(us.getUsername(), sMultiClusterID);
                                }else{
                                    ra=new RemoteAccess(us.getRemoteCluster());
                                    ra.setUuid(us.getUuid());
                                }
                                
                                String sRemoteFile = "";
                                if (file_partial) {
                                    sRemoteFile = sNamer + "&filechunk_size=" + filechunk_size + "&filechunk_offset=" + filechunk_offset;
                                } else {
                                    sRemoteFile = sNamer;
                                }
                                p(">>>>>>>> Remote GetFile sNamer = '" + sNamer + "'");
                                byte[] sMessage = ra.remoteFile(sRemoteFile);
                                if (sFileExt.length() > 0) {
                                    sGetFileExt = "." + sFileExt;  
                                    sGetFileName = sFileName;
                                } else {
                                    sGetFileExt = ".jpeg";
                                }                       
                                System.out.println("Extension = " + sGetFileExt);                                
                                System.out.println("isAesEncrypt = " + us.isAesecrypt());
                                System.out.println("sMessage len = " + sMessage.length);
                                if(isValidMultiClusterID(sMultiClusterID)){
                                    FileOutputStream encFile = new FileOutputStream("tmp/enc" + sNamer, false);
                                    encFile.write(sMessage);
                                    encFile.close();

                                            
                                    CryptLib _crypt = new CryptLib(ra.getAssize());
                                    //String key = CryptLib.SHA256(ra.getKeyPassword(), ra.getAssize());
                                    String key = ra.getKeyPassword();

                                    _crypt.decryptFile("tmp/enc" + sNamer, "tmp/dec" + sNamer, key, ra.getIv()); //decrypt
                                    
                                    FileInputStream decFile = new FileInputStream("tmp/dec" + sNamer);
                                    decFile.read(sMessage);
                                }else
                                if(us.isAesecrypt()){   
                                    FileOutputStream encFile = new FileOutputStream("tmp/enc" + sNamer, false);
                                    encFile.write(sMessage);
                                    encFile.close();

                                            
                                    CryptLib _crypt = new CryptLib(us.getAessize());
                                    
                                    //String key = CryptLib.SHA256(us.getPasswordkey(), us.getAessize());                                    
                                    String key = us.getPasswordkey();
                                                                        
                                    System.out.println("passwordkey = '" + us.getPasswordkey() + "' " + us.getPasswordkey().length());
                                    System.out.println("aesize      = '" + us.getAessize());
                                    System.out.println("key         = '" + key + "' " + key.length());
                                    

                                    _crypt.decryptFile("tmp/enc" + sNamer, "tmp/dec" + sNamer, key, us.getIv()); //decrypt
                                    
                                    FileInputStream decFile = new FileInputStream("tmp/dec" + sNamer);
                                    decFile.read(sMessage);

                                }
                                System.out.println("Len file = " + sMessage.length);
                                
                                outFile.write(sMessage);
                                outFile.close();                            

                            }else{
                                
                                System.out.println("@@@@@@@@@@@@ --- !!!! LOCAL GET FILE");

                                String ClientIP = s.getInetAddress().getHostAddress();
                                //String res = wf.echoh2mobileac(sFoo2, dbmode, ClientIP, sFileType, sNumObj, LocalIP, Integer.toString(port));    
                                
                                p(" **** sNamer       : " + sNamer);
                                p(" **** sFileName    : " + sFileName);
                                p(" **** sFoo2        : " + sFoo2);
                                p(" **** LocalIP      : " + LocalIP);
                                p(" **** port         : " + port);
                                p(" **** bCloudHosted :" + bCloudHosted);
                                p(" **** ClientIP     :" + ClientIP);
                                p(" **** dbMode       :" + dbmode);
                                
                                      
                                String urlgetfile = wf.getfile_mobile(sNamer, sFileName, sFoo2, LocalIP, Integer.toString(port), bCloudHosted, ClientIP, dbmode);

                                p("url getfile = " + urlgetfile);

                                if (!urlgetfile.equals("ERROR") && !urlgetfile.equals("FILENOTFOUND") && !urlgetfile.equals("TIMEOUT")) {   
                                    String sTmpFileName = "tmp/" + sNamer;
                                    File fh = new File("tmp/");
                                    fh.mkdirs();
                                    
                                    String pathgetfile = wf.getlocalfilepath(sNamer);
                                    
                                    p("******************************");
                                    p("******************************");
                                    p("******************************");
                                    p("********pathgetfile**********:" + pathgetfile);
                                    p("******************************");
                                    p("******************************");
                                    p("******************************");
                                    
                                    int gf = 1;
                                    File f = new File(pathgetfile);
                                    if (f.exists()) {
                                        //if local file exists, use it as the temp file instead of remote http get
                                        p("file exists locally... skipping http/get");
                                        sTmpFileName = pathgetfile;
                                    } else {
                                        p("local file not exist. doing get....");
                                        gf = NetUtils.getfile(urlgetfile, sTmpFileName, 3, 500, 10000);
                                        p("res getfile = " + gf);                                        
                                    }

                                    sGetFileExt = urlgetfile.substring(urlgetfile.lastIndexOf("."), urlgetfile.length());
                                    p("sGetFileExt = " + sGetFileExt);

                                    String aesencrypt = GetConfig("aesencrypt", "config/www-server.properties");

                                    System.out.println("@@@@@@@@@@@@ ---- aesencrypt : " + aesencrypt);
                                    System.out.println("@@@@@@@@@@@@ ---- bMobile    : " + bMobile);
                                    System.out.println("@@@@@@@@@@@@ ---- aes pwLength    : " + us.getPasswordkey().length());

                                    if (us.getPasswordkey().length() == 0) aesencrypt = "false";
                                    if (!bMobile) aesencrypt = "false";

                                    System.out.println("@@@@@@@@@@@@ ---- aencrypt   : " + aesencrypt);

        //                            String aessizeString = GetConfig("aessize", "config/www-server.properties");
        //                            int aessize = -1;
        //                            if(aessizeString != null && !aessizeString.isEmpty())
        //                                aessize = new Integer(aessizeString);
                                    int aessize = 128;  

                                    if(aesencrypt != null && aesencrypt.equals("true") && aessize > -1){

                                        try {
                                            System.out.println("encrypt file ");
                                            System.out.println("@@@@@@@@@@@@ ---- bMobile   : " + bMobile);
                                            System.out.println("@@@@@@@@@@@@ ---- key size:     : " + aessize);
                                            System.out.println("@@@@@@@@@@@@ ---- password key  : '" + us.getPasswordkey() + "'");
                                            
                                            CryptLib _crypt = new CryptLib(aessize);
                                            if(us != null){

                                                //String key = CryptLib.SHA256(us.getPasswordkey(), aessize);
                                                String key = us.getPasswordkey();
                                               

                                                String iv = us.getIv();
                                                System.out.println("key = '" + key + "'");
                                                System.out.println("iv  = '" + iv + "'");
                                                
                                                String sTmpFileNameEnc = "tmp/enc" + sNamer + "." + us.getUuid();
                                                File fe = new File(sTmpFileNameEnc);
                                                if (fe.exists()) {
                                                    p("File Enc exists. skipping enc: " + sTmpFileNameEnc);
                                                } else {
                                                    p("start enc");
                                                    Stopwatch sw = new Stopwatch();
                                                    sw.start();
                                                    _crypt.encryptFile(sTmpFileName, sTmpFileNameEnc, key, iv); //encrypt                                                    
                                                    sw.stop();
                                                    p("end enc: "+ sw.getElapsedTime());
                                                }
                                                
                                                sTmpFileName = sTmpFileNameEnc;
                                                sNamer = "enc" + sNamer + "." + us.getUuid();
                                            }

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    p("sTmpFileName: " + sTmpFileName);
                                    File fh2 = new File (sTmpFileName);
                                    p("Exists      : " + fh2.exists());
                                           
                                    if (gf >=0 && fh2.exists()) {
                                        
                                        p("[getfile.fn] filechunk_size: " + chunk_size);
                                        p("[getfile.fn] filechunk_offset: " + chunk_offset);
                                        
                                        is = new FileInputStream(sTmpFileName);

                                        if (chunk_offset > 0) {
                                            p("[getfile.fn] skipping offset: " + chunk_offset);
                                            is.skip(chunk_offset);
                                        }
                                          
                                        if (file_partial) {
                                            p("********************* PARTIAL FILE CASE ***************************");
                                            p("[getfilepart.fn] partial file case. chunk size = " + chunk_size);
                                            //partial file case
                                            byte[] buffer = new byte[chunk_size];
                                            int bytesRead = 0;
                                            int bytesReadTotal = 0;
                                            while ((bytesRead = is.read(buffer)) != -1 && bytesReadTotal < chunk_size) {
                                                bytesReadTotal += bytesRead;                                                
                                                p("[getfile.fn] bytesRead: " + bytesRead + " totalread: " + bytesReadTotal);
                                                outFile.write(buffer, 0, bytesRead);
                                            }
                                        } else {
                                            p("[getfile.fn] FULL FILE CASE...");
                                            //full file case
                                             try {
                                                  int n;
                                                  while ((n = is.read(buf)) > 0) {
                                                      outFile.write(buf, 0, n);
                                                  }
                                             } finally {
                                                  is.close();
                                             }                                            
                                        }
                                        
                                         outFile.close();    

                                         //dispose tmp file after using it
                                         //fh2.delete();                               
                                    } else {
                                         String sMessage = sNamer + "\n";
                                         byte[] kk = sMessage.getBytes();
                                         outFile.write(kk);
                                         outFile.close();                                                  
                                    }                            
                                } else {
                                    String sMessage = sNamer + "\n";
                                    byte[] kk = sMessage.getBytes();
                                    outFile.write(kk);
                                    outFile.close();                   
                                    }

                                }

                            
                        }
                       
                   }

                   if (fname.contains("suggest.fn")) {
                       if (bUserAuthenticated){
                            String res = "";
                            UserSession us = uuidmap.get(sAuthUUID);
                            
                            if (us.isRemote()) {
                                System.out.println("REMOTE SUGGEST");
                                RemoteAccess ra = new RemoteAccess(us.getRemoteCluster());
                                ra.setUuid(us.getUuid());
                                String param = fname.split("suggest.fn\\?")[1];
                                System.out.println("REMOTE SUGGEST PARAM: '" + param + "'");
                                //public String remoteSuggest(String filetype, String days, String foo, String numobj){
                                String json = ra.remoteSuggest(sFileType, sDaysBack, sFoo2, sNumObj);
                                outFile.write(json.getBytes());
                                outFile.close();                                                                                                                                
                            } else {
                                String sUser;
                                if(us != null){
                                     sUser = us.getUsername();
                                }else{
                                     sUser = null;
                                }  
                                String ClientIP = s.getInetAddress().getHostAddress();
                                if (dbmode.equals("cass") || dbmode.equals("both")) {
                                    res = wf.echoh2mobile(sFoo2, 
                                                    root.toString(), 
                                                    sNumObj, 
                                                    sFileType, 
                                                    sNumCol, 
                                                    sPassword, 
                                                    sDaysBack, 
                                                    sDateStart, 
                                                    LocalIP, 
                                                    Integer.toString(port),
                                                    bCloudHosted,
                                                    bAWSHosted,
                                                    ClientIP,
                                                    dbmode,
                                                    sUser,
                                                    bWritePerfLog,
                                                    bBase64Pic,
                                                    10,
                                                    sScreenSize,
                                                    true);                            
                                } else {
                                    res = wf.echoh2mobileac(sFoo2, dbmode, ClientIP, sFileType, sNumObj, LocalIP, Integer.toString(port), sDaysBack, bCloudHosted, bAWSHosted, sUser);                            
                                }                       

                                byte[] kk = res.getBytes();
                                outFile.write(kk);
                                outFile.close();    
                            }
                           
                       }
                   }
                   
                   //getts.fn
                    if ((fname.contains("getts.fn"))) {

                       System.out.println("------------getts.fn");
                       System.out.println("   sAuthUUID: " + sAuthUUID);
                       System.out.println("   sMultiClusterID: " + sMultiClusterID);
                       System.out.println("   uuid: " + uuid);
                       System.out.println("  sUUID: " + sUUID);
                        
                       UserSession us = uuidmap.get(sAuthUUID);
                       if(us.isRemote() || (isValidMultiClusterID(sMultiClusterID))){
                           //remote getts

                            String auxSAuthUUID=sAuthUUID;
                            if(isValidMultiClusterID(sMultiClusterID)){
                               auxSAuthUUID= MultiClusterManager.getInstance().getRA(us.getUsername(), sMultiClusterID).getUuid();
                            } else {
                               System.out.println("WARNING: isValidMultiClusterID invalid for cluster: " + sMultiClusterID);
                            }
                            
                            if (auxSAuthUUID == null) {
                                System.out.println("  Fixing null sAuthUUID to :" + sAuthUUID);
                                auxSAuthUUID = sAuthUUID;
                            }
                            
                           //remote getvideo
                            String sTmpFileName = "tmp/" + sMD5 + "." + sTS;
                            File fh = new File("tmp/");
                            fh.mkdirs();
                            String urlgetfile = "https://abc.alterante.com/cass/getts.fn?md5=" + sMD5 + "&ts=" + sTS + "&uuid=" + auxSAuthUUID;
                                                        
                            System.out.println("   url getts: " + urlgetfile);
                           
                            GetMethod getFile = new GetMethod(urlgetfile);
                            HttpClient httpclient = new HttpClient();
            
                            if(uuid != null){
                                getFile.setRequestHeader("Cookie", "uuid=" + uuid);
                            }
                            int re = httpclient.executeMethod(getFile);            
                              
                            System.out.println("res remote getts = " + re);
                                                        
                            if (re == 200) outFile.write(getFile.getResponseBody());
                         
                           
                       } else {
                           //local getts
                            if (isUUIDValid(sUUID)) {
                                String sTmpFileName = "../rtserver/streaming/" + sMD5 + "/" + sTS;

                                System.out.println("Looking for TS file: " + sTmpFileName);
                                File fh2 = new File(sTmpFileName);
                                if (fh2.exists()) {
                                    System.out.println("Found TS file: " + fh2.getCanonicalPath());
                                     is = new FileInputStream(sTmpFileName);
                                      try {
                                           int n;
                                           while ((n = is.read(buf)) > 0) {
                                               outFile.write(buf, 0, n);
                                           }
                                      } finally {
                                           is.close();
                                      }
                                     outFile.close();                                                  
                                } else {
                                    System.out.print("getts -- file not found");                                
                                }                           
                           } else {
                                System.out.print("getts-- not authenticated");
                           }
                       }
                   }
                    
                   //getvideo.fn
                   if ((fname.contains("getvideo.m3u8"))) {
                       
                       System.out.println("------------getvideo.m3u8");
                       System.out.println("   sAuthUUID: " + sAuthUUID);
                       System.out.println("   sMultiClusterID: " + sMultiClusterID);
                       System.out.println("   uuid: " + uuid);
                       
                       UserSession us = uuidmap.get(sAuthUUID);
                       if(us.isRemote() || isValidMultiClusterID(sMultiClusterID)){
                           //remote getvideo
                            String auxSAuthUUID=sAuthUUID;
                            if(isValidMultiClusterID(sMultiClusterID)){
                               auxSAuthUUID= MultiClusterManager.getInstance().getRA(us.getUsername(), sMultiClusterID).getUuid();
                            }
                            String sTmpFileName = "tmp/" + sMD5;
                            File fh = new File("tmp/");
                            fh.mkdirs();
                            String urlgetfile = "https://abc.alterante.com/cass/getvideo.m3u8?md5=" + sMD5 + "&uuid=" + auxSAuthUUID;
                                                        
                            System.out.println("   url getvideo: " + urlgetfile);
 
                            GetMethod getFile = new GetMethod(urlgetfile);
                            HttpClient httpclient = new HttpClient();
            
                            if(uuid != null){
                                getFile.setRequestHeader("Cookie", "uuid=" + uuid);
                            }
                            int re = httpclient.executeMethod(getFile);            
                              
                            System.out.println("res remote getvideo = " + re);
                            
                            if (re == 200) outFile.write(getFile.getResponseBody());
                                                                                         
                       } else {
                           //local getvideo
                            if (isUUIDValid(sUUID)) {
                                String sTmpFileName = "../rtserver/streaming/" + sMD5 + "/" + "OUTPUT.m3u8";

                                System.out.println("Looking for M3u8 file: " + sTmpFileName);
                                File fh2 = new File(sTmpFileName);
                                if (fh2.exists()) {
                                    System.out.println("Found M3u8 file: " + fh2.getCanonicalPath());

                                    byte[] encoded = Files.readAllBytes(Paths.get(fh2.getCanonicalPath()));
                                    String body = new String(encoded, "UTF-8");

                                    body = body.replaceAll("\\.ts", ".ts&uuid=" + sUUID);
                                    body = body.replaceAll("\\/getts.fn", "/cass/getts.fn");

                                    byte[] kk = body.getBytes();
                                    outFile.write(kk);
                                    outFile.close();

    //                                 is = new FileInputStream(sTmpFileName);
    //                                  try {
    //                                       int n;
    //                                       while ((n = is.read(buf)) > 0) {
    //                                           outFile.write(buf, 0, n);
    //                                       }
    //                                  } finally {
    //                                       is.close();
    //                                  }
    //                                 outFile.close();     

                                } else {
                                    System.out.print("getvideo -- file not found");                                
                                }                           
                           } else {
                                System.out.print("getvideo -- not authenticated");
                           }
                       }                       
                   }
                   
                   //echoclient5 (main page)
                   if ((fname.contains("echoClient5.php") || fname.contains("echoClient5.htm") || fname.contains("echoClient4.php")) && bPasswordValid && bUserAuthenticated) {
                      //p("sfoo_encoded = '" + sFoo2 + "'");
                      sFoo2 = URLDecoder.decode(sFoo2, "UTF-8");
                      //p("sfoo_decoded = '" + sFoo2 + "'");

                      String ClientIP = s.getInetAddress().getHostAddress();

                      if (sView.equals("") || sView.equals("detail")) {
                              if(sFileType.equals("")) {
                                  sFileType = ".all";
                              }
                              if (sFoo2.equals("")) {
                                  sFoo2 = sFileType;
                                  p("Foo blank. Replacing with filetype...: '" + sFoo2 + "'");
                              }
                              String res = "";
                              
                            UserSession us = uuidmap.get(sAuthUUID);
                            String sUser;
                            if(us != null){
                                sUser = us.getUsername();
                            }else{
                                sUser = null;
                            }  
                              
                              boolean bRetry = true;
                              while (bRetry) {
                                res = wf.echoh2l(sFoo2, 
                                        root.toString(), 
                                        sNumObj, 
                                        sFileType, 
                                        sNumCol, 
                                        sPassword, 
                                        sDaysBack, 
                                        sDateStart, 
                                        LocalIP, 
                                        Integer.toString(port),
                                        bCloudHosted,
                                        bAWSHosted,
                                        ClientIP,
                                        dbmode,
                                        sUser,
                                        bWritePerfLog,
                                        bBase64Pic,
                                        sScreenSize);    
                                if (!res.contains("ERROR_")) {
                                    bRetry = false;
                                } else {
                                    //error;
                                    p(res + " SLEEPING BEFORE RETRY");
                                    long lSleep = 100;
                                    try {
                                        Thread.sleep(lSleep);
                                    } catch (Exception ex) {
                                        
                                    }
                                }
                              }
                              
                              
                              byte[] kk = res.getBytes();
                              outFile.write(kk);
                              outFile.close();
                          }  
                        if (sView.equals("tile")) {
                              if (sFoo2.equals("")) {
                                  sFoo2 = sFileType;
                                  p("Foo blank. Replacing with filetype...: '" + sFoo2 + "'");
                              }
                              UserSession us = uuidmap.get(sAuthUUID);
                              String sUser;
                              if(us != null){
                                sUser = us.getUsername();
                              }else{
                                sUser = null;
                              }  
                              
                              String res;
                              if(us.isRemote()){
                                RemoteAccess ra = new RemoteAccess(us.getRemoteCluster());
                                ra.setUuid(us.getUuid());

                                String returned = ra.remoteQuery(sFileType, sDaysBack, sFoo2, "json", sNumObj, sDateStart);
                                
                                if(!returned.startsWith("ERROR")){
                                    StringBuilder sb = HtmlFromJSON.getHTML(returned, sFoo2, sDateStart, sFileType, sDaysBack, sNumObj, sNumCol, password, sView, sScreenSize, us,  Integer.parseInt(sPage), previousDate);
                                    sb.append(HtmlFromJSON.getTagsRemote(ra.remoteGetTags()));

                                    res = sb.toString();
                                }else{
                                    res = returned;
                                }

                              }else{
                                  
                                res = wf.echoh2(bButtonModelNew, sFoo2, 
                                      root.toString(), 
                                      sNumObj, 
                                      sFileType, 
                                      sNumCol, 
                                      sPassword, 
                                      sDaysBack, 
                                      sDateStart, 
                                      LocalIP, 
                                      Integer.toString(port),
                                      bCloudHosted,
                                      bAWSHosted,
                                      ClientIP,
                                      sUser,
                                      bWritePerfLog,
                                      bBase64Pic,
                                      sScreenSize);
                              
                              }
                              res += "</div></body></html>";
                              byte[] kk = res.getBytes();
                              outFile.write(kk);
                              outFile.close();
                          } 

                          if (sView.equals("polar")) {
                             is = new FileInputStream(root + File.separator + "cass/polaroid.htm");
                               try {
                                    int n;
                                    while ((n = is.read(buf)) > 0) {
                                        outFile.write(buf, 0, n);
                                    }
                               } finally {
                                    is.close();
                               }

                              String res = wf.echoh2p(sFoo2, root.toString(), sNumObj, sPassword);
                              byte[] kk = res.getBytes();
                              outFile.write(kk);

                              String sEnd  = "\n</body></html>\n"; 
                              outFile.write(sEnd.getBytes());

                              outFile.close();
                          }
                          
                          if (sView.equals("show")) {
                              is = new FileInputStream(root + File.separator + "cass/h5slideshow1.htm");
                              try {
                                    int n;
                                    while ((n = is.read(buf)) > 0) {
                                        outFile.write(buf, 0, n);
                                    }
                              } finally {
                                    is.close();
                              }
                              
                              if (sFoo2.equals("")) {
                                  sFoo2 = sFileType;
                                  p("Foo blank. Replacing with filetype...: '" + sFoo2 + "'");
                              }

                              UserSession us = uuidmap.get(sAuthUUID);
                            String sUser;
                            if(us != null){
                                sUser = us.getUsername();
                            }else{
                                sUser = null;
                            }  
                              
                              String res = wf.echoh2s2(sFoo2, 
                                      root.toString(), 
                                      sNumObj, 
                                      sPassword, 
                                      sPID, 
                                      sDaysBack, 
                                      dbmode,
                                      sUser,
                                      sScreenSize);                              
                              
                              byte[] kk = res.getBytes();
                              outFile.write(kk);

                              is = new FileInputStream(root + File.separator + "cass/h5slideshow2.htm");
                              try {
                                    int n;
                                    while ((n = is.read(buf)) > 0) {
                                        outFile.write(buf, 0, n);
                                    }
                              } finally {
                                    is.close();
                              }

                              String sEnd  = "\n</body></html>\n"; 
                              outFile.write(sEnd.getBytes());

                              outFile.close();
                          }
                          
                          if (sView.equals("show2")) {
                              is = new FileInputStream(root + File.separator + "cass/slideshow1.htm");
                              try {
                                    int n;
                                    while ((n = is.read(buf)) > 0) {
                                        outFile.write(buf, 0, n);
                                    }
                              } finally {
                                    is.close();
                              }

                              UserSession us = uuidmap.get(sAuthUUID);
                              String sUser;
                              if(us != null){
                                  sUser = us.getUsername();
                              }else{
                                  sUser = null;
                              }  
                              String res = wf.echoh2s(sFoo2, root.toString(), sNumObj, sPassword, sPID,sUser);
                              byte[] kk = res.getBytes();
                              outFile.write(kk);

                              is = new FileInputStream(root + File.separator + "cass/slideshow2.htm");
                              try {
                                    int n;
                                    while ((n = is.read(buf)) > 0) {
                                        outFile.write(buf, 0, n);
                                    }
                              } finally {
                                    is.close();
                              }

                              String sEnd  = "\n</body></html>\n"; 
                              outFile.write(sEnd.getBytes());

                              outFile.close();
                          }
                          
                          if (sView.equals("caro")) {
                             is = new FileInputStream(root + File.separator + "cass/carousel.htm");
                               try {
                                    int n;
                                    while ((n = is.read(buf)) > 0) {
                                        outFile.write(buf, 0, n);
                                    }
                               } finally {
                                    is.close();
                               }

                              String res = wf.echoh2c(sFoo2, root.toString(), sNumObj, sPassword);
                              byte[] kk = res.getBytes();
                              outFile.write(kk);


                             is = new FileInputStream(root + File.separator + "cass/carousel2.htm");
                               try {
                                    int n;
                                    while ((n = is.read(buf)) > 0) {
                                        outFile.write(buf, 0, n);
                                    }
                               } finally {
                                    is.close();
                               }

                              //String res = utils.WebFuncs.echoh2(sFoo2, root.toString());
                              //byte[] kk = res.getBytes();
                              //outFile.write(kk);
                              //outFile.close();
                               outFile.close();

                              
                          }                          
                   }

                   //echo client 6
                   if (fname.contains("echoClient6.php") && bPasswordValid && bUserAuthenticated) {
                       
                           is = new FileInputStream(root + File.separator + "cass/carousel.htm");
                           try {
                                int n;
                                while ((n = is.read(buf)) > 0) {
                                    outFile.write(buf, 0, n);
                                }
                           } finally {
                                is.close();
                           }
                           
                          String res = wf.echoh2c(sFoo2, root.toString(), sNumObj, sPassword);
                          byte[] kk = res.getBytes();
                          outFile.write(kk);
                          

                         is = new FileInputStream(root + File.separator + "cass/carousel2.htm");
                           try {
                                int n;
                                while ((n = is.read(buf)) > 0) {
                                    outFile.write(buf, 0, n);
                                }
                           } finally {
                                is.close();
                           }

                          //String res = utils.WebFuncs.echoh2(sFoo2, root.toString());
                          //byte[] kk = res.getBytes();
                          //outFile.write(kk);
                          //outFile.close();
                           outFile.close();
                   }

                //echo client 7
                if (fname.contains("echoClient7.php") && bPasswordValid && bUserAuthenticated) {
                       
                           is = new FileInputStream(root + File.separator + "cass/polaroid.htm");
                           try {
                                int n;
                                while ((n = is.read(buf)) > 0) {
                                    outFile.write(buf, 0, n);
                                }
                           } finally {
                                is.close();
                           }
                           
                          String res = wf.echoh2p(sFoo2, root.toString(), sNumObj, sPassword);
                          byte[] kk = res.getBytes();
                          outFile.write(kk);
                          
                          String sEnd  = "\n</body></html>\n"; 
                          outFile.write(sEnd.getBytes());

                          outFile.close();
                   }

                   //test4
                   if (fname.contains("test4.php") && bPasswordValid && bUserAuthenticated) {
                        p("[Processing test4.php]");
                          String res = "";
                          String res2 = "";
                          if (sHash2.length() > 0) {
                              sHash2 = sHash2.toLowerCase();
                              sHash2 = URLDecoder.decode(sHash2, "UTF-8");
                              p("inserting hash <" + keyspace + "," + sFoo2 + "," + sHash2 + ">");
                              String sDateModified = wf.get_row_attribute(keyspace, "Standard1", sFoo2, "date_modified", dbmode);
                              p("date modified <" + sDateModified + ">");
                              res = wf.insert_hash(keyspace, sFoo2, sHash2, true, sDateModified, dbmode);
                          }
                          res = wf.echoh3(sFoo2, dbmode);
                          res2 += res;
                          
                          
                          String sClientIP = s.getInetAddress().getHostAddress();
                          
                          //get the automated hashes
                          res = wf.geth(sFoo2,"hashes", sPassword, sClientIP, sView, sDaysBack, sNumCol, sNumObj, sFileType, dbmode);
                          res2 += res;
                          //get the hashes that were added manually
                          res = wf.geth(sFoo2,"hashesm", sPassword, sClientIP, sView, sDaysBack, sNumCol, sNumObj, sFileType, dbmode);
                          res2 += "<font style=\"background-color: yellow\">" + res + "</font>";
                          
                          String sClient = s.getInetAddress().getHostAddress();
                          res = wf.getp(sFoo2,"paths", sPassword, sClient, dbmode);
                          res2 += res;
                          byte[] kk = res2.getBytes();
                          outFile.write(kk);

                          res2 = "";
                          res2 += "<FORM ACTION=\"test4.php\" METHOD=GET>";
                          res2 += "<INPUT TYPE=\"hidden\" NAME=\"foo\" VALUE=\"" + sFoo2 + "\">";
                          res2 += "<INPUT TYPE=\"hidden\" NAME=\"pw\" VALUE=\"" + sPassword + "\">";
                          res2 += "<INPUT TYPE=\"text\" NAME=\"hash\">";
                          res2 += "</FORM>";
                          kk = res2.getBytes();
                          outFile.write(kk);
                          outFile.close();
                    }
                   
                   //Update number of copies
//                   if (fname.contains("updatenumcopies.fn")) {
//                       LocalFuncs.update_occurences_copies(sBatchID);
//                   }
                   
                    //set node
                    if (fname.contains("setnode.php")) {
                          p("---[Processing setnode.php]");
                          p("sUUID = " + sUUID);
                          p("sIPAddress = " + sIPAddress);
                          p("sPort = " + sPort);
                          p("sBackup = " + sBackup);
                          p("sFree = " + sFree);
                          p("sSync = " + sSync);
                          p("sLastBatch = " + sLastBatch);
                          p("sLastSeq = " + sLastSeq);
                          p("sMachine = " + sMachine);
                          p("clientNettyPort = " + clientNettyPort);
                          
                          int res = 0;
                          res += wf.insert_node_attribute(sUUID, "ipaddress", sIPAddress, dbmode, false);
                          res += wf.insert_node_attribute(sUUID, "port", sPort, dbmode, true);
                          res += wf.insert_node_attribute(sUUID, "backup", sBackup, dbmode, true);
                          res += wf.insert_node_attribute(sUUID, "free", sFree, dbmode, true);
                          res += wf.insert_node_attribute(sUUID, "sync", sSync, dbmode, true);
                          res += wf.insert_node_attribute(sUUID, "lastbat", sLastBatch, dbmode, true);
                          res += wf.insert_node_attribute(sUUID, "lastseq", sLastSeq, dbmode, true);
                          res += wf.insert_node_attribute(sUUID, "lastping", String.valueOf(System.currentTimeMillis()), dbmode, true);
                          res += wf.insert_node_attribute(sUUID, "machine", sMachine, dbmode, true);
                          res += wf.insert_node_attribute(sUUID, "nettyport", clientNettyPort, dbmode, true);
                          
                          if (dbmode.equals("cass")) {
                            //only store the node UUID in the nodes column for cassandra case
                              res += wf.insert_node_attribute("nodes", sUUID, sUUID, dbmode, true);                              
                          }                    
                              
                          p("setnode.php res = " + res);
                          outFile.write("setnode.php".getBytes());
                          outFile.close();
                    }
                    
                    //systeminfo.fn
                    if (fname.contains("systeminfo.fn")) {
                          p("---[Processing ssysteminfo]");
                          String res = "";
                                  
                          Date ts_start = Calendar.getInstance().getTime();
                          SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
                          res = sdf.format(ts_start) + "<br>";
                          
                          int n = GetNumberofFilesInDir("incoming", "");                          
                          res += "<b>Server</b><br>";
                          res += "incoming files: " + String.valueOf(n) + "<br><br>";

                          n = GetNumberofFilesInDir(".", ".idx");                          
                          res += "IDX files: " + String.valueOf(n) + "<br><br>";

                          n = GetNumberofFilesInDir(".", ".bad");                          
                          res += "BAD IDX files: " + String.valueOf(n) + "<br><br>";
                          
                          res += "<b>Local Drives</b><br>";                         
                          res += "<table border = '1'>";
                          res += "<tr><th>Drive</th><th>outgoing</th><th>backup</th><th>sync</th></tr>";

                          String spath = "";
                          String spathdec = "";
                          for (i=1;i<=4;i++) {
                            
                            String s = "";
                            if (i > 1) {
                                s = String.valueOf(i);
                            } 
                            
                            spath = GetConfig("outgoing", appendage + "../scrubber/config/www-rtbackup" + s + ".properties");
                            spathdec = URLDecoder.decode(spath, "UTF-8");
                            n = GetNumberofFilesInDir(spathdec, "");                        
                            res += "<tr><td>" + String.valueOf(i) + "</td>";
                            res += "<td>" + String.valueOf(n) + " (" + spathdec + ") </td>";
                            spath = GetConfig("backuppath", appendage + "../scrubber/config/www-rtbackup" + s + ".properties");
                            spathdec = URLDecoder.decode(spath, "UTF-8");
                            n = GetNumberofFilesInDir(spathdec, "");                          
                            res += "<td>" + String.valueOf(n) + " (" + spathdec + ") </td>";
                            spath = GetConfig("syncpath", appendage + "../scrubber/config/www-rtbackup" + s + ".properties");
                            spathdec = URLDecoder.decode(spath, "UTF-8");
                            n = GetNumberofFilesInDir(spathdec, "");                          
                            res += "<td>" + String.valueOf(n) + " (" + spathdec + ") </td>";
                            res += "</tr>";                              
                          }                    

                          res += "</table>";

                          byte[] kk = res.getBytes();
                          outFile.write(kk);
                          outFile.close();
                    }
                    
                    //systeminfoscan.fn
                    if (fname.contains("systeminfoscan.fn")) {
                          String res = "";  
                          
                          int idxfiles = GetNumberofFilesInDir(".", ".idx"); 
                          int batches = wf.getBatchId(sFoo2, dbmode);
                          ArrayList<Node> nodes = wf.getNodes(sFoo2, dbmode);
                          
                          res += "<div> ";
                          res += Home.getScanIndexInfo(idxfiles,batches,nodes);
                          res += "</div>";
                          
                          byte[] kk = res.getBytes();
                          outFile.write(kk);
                          outFile.close();
                    }
                    
                    //nodeinfo
                    if (fname.contains("nodeinfo.php")) {
                          p("---[Processing nodeinfo.php]");
                          p("sFoo = " + sFoo2);
                          String res = "";
                          res = wf.echonode(sFoo2, dbmode);
                          byte[] kk = res.getBytes();
                          outFile.write(kk);
                          outFile.close();
                    }

                    
                    //backupinfo 
                    if (fname.contains("backupinfo.php")) {
                          p("---[Processing backupinfo.php]");
                          String res = "";
                          res = wf.echobackup(sFoo2, dbmode) + "\n";
                          p("output = '" + res + "'");
                          
                          if (bCloudAmazon) {
                            String sBatchLast = NetUtils.getConfig("amazon_batch", "../rtserver/config/www-cloud-batches.properties");
                            Float nBatchLast = (float) 0;
                            if (sBatchLast.length() > 0) nBatchLast = Float.valueOf(sBatchLast);
                            if (nBatchLast > 0) {
                                LocalFuncs c8 = new LocalFuncs();
                                float nBatchEnd = Float.parseFloat(c8.get_batch_id("batchid","BatchJobs", "idx"));                                
                                float perc = nBatchLast / nBatchEnd * 100;
                                res += "<br><b>Amazon Cloud Drive</b><br>" + 
                                        "Current: " + String.format("%.0f", nBatchLast)  + "<br>" + 
                                        "Total  : " + String.format("%.0f", nBatchEnd) + "<br>" + 
                                        String.format("%.1f", perc) + "%";
                                c8 = null;
                            }
                          }
                                 
                          
                          byte[] kk = res.getBytes();
                          outFile.write(kk);
                          outFile.close();                        
                    }
                    
                    //redir
                    if (fname.contains("redir.php")) {
                          p("---[Processing redir.php]");
                          p("sFoo = " + sFoo2);
                          String res = "hello";
                          byte[] kk = res.getBytes();
                          outFile.write(kk);
                          outFile.close();
                          bRedirect = true;
                          sRedirectURI = sFoo2;
                    }
                    
                    //send file
                    if (fname.contains("sendfile.php")) {
                        p("---[Processing sendmail.php]");
                        
                        String sPathDec4 = wf.get_file_path(sMailFile);                        
                        p("sFileDec4 '" + sPathDec4 + "'");
                        
                        String _sMailFile = sPathDec4;
                        
                        
                        String sMailFile2 = URLDecoder.decode(_sMailFile, "UTF-8");
                        p("req_decoded: '" + sMailFile2 + "'");
                        
                        if (!bCloudHosted) {
                            byte[] s2 = Base64.decode(sMailFile2.toCharArray());
                            sPathDec = new String(s2);
                            p("req_decrypted: '" + sPathDec + "'");                            
                        } else {
                            sPathDec = sMailFile2;
                        }
                        
                        String sPathDec3 = "";
                        try {
                            //sPathDec3 = URLDecoder.decode(sPathDec, "UTF-8");
                            sPathDec3 = sPathDec;
                        } catch (Exception e) {
                            e.printStackTrace();
                            sPathDec3 = sPathDec;
                        }
                        
                        
                        if (!bWindowsServer && !sPathDec3.startsWith("/")) {
                            sPathDec3 = "/" + sPathDec3;
                        }
                        
                        if (bCloudHosted) {
                            sPathDec3 += ".jpg"; 
                            sPathDec3 = root + File.separator + sPathDec3;
                        }

                        p("sFilePath '" + sPathDec3 + "'");
                        
                        
                        
                        String sMailFrom2 = "";
                        String sMailFromPassword2 = "";
                        
                        try {
                            sMailFrom2 = URLDecoder.decode(sMailFromURL, "UTF-8");
                            sMailFromPassword2 = URLDecoder.decode(sMailFromPasswordURL, "UTF-8");
                        } catch (Exception e) {
                            e.printStackTrace();
                            return;
                        }
                        p("sMailFrom2: '" + sMailFrom2 + "'");
                        p("sMailFrom2len: '" + sMailFrom2.length() + "'");
                        
                        if (sMailFrom2.length() == 0) {
                            p("replacing with sMailFrom: '" + sMailFrom + "'");
                            //if from/pw sent in the request , then we'll use it. otherwise we use properties loaded in config.
                            sMailFrom2 = sMailFrom;
                            sMailFromPassword2 = sMailFromPassword;
                        }
                        
                        p("sMailFrom2: '" + sMailFrom2 + "'");
                        
                        
                        //if (sMailFrom2.contains("hotmail")) {
                        //    sMailHost = "smtp.live.com";
                        //}
                        //if (sMailFrom2.contains("gmail")) {
                        //    sMailHost = "smtp.gmail.com";
                        //}
                        
                        //Mailer mailer = new Mailer();
                        
                        Date ts_start = Calendar.getInstance().getTime();
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");
                        
                        sFileName = "";
                        if (!bCloudHosted) {       
                            String sExt = "";
                            if (sPathDec3.contains(".jpeg")) {
                                sExt = ".jpeg";
                            } else {                                
                                p("sPathDec3 = " + sPathDec3);
                                int nPos = sPathDec3.lastIndexOf(".");                               
                                p("npos = " + nPos);
                                sExt = sPathDec3.substring(nPos, nPos + 4); 
                                p("sExt = " + sExt);
                            }
                            sFileName = sdf.format(ts_start) + sExt;                          
                        } else {
                            //thumbnail case
                            sFileName = sdf.format(ts_start) + ".jpg";                           
                        }
                        
                        p("Attachment filename: " + sFileName);
                        
                        MailerFuncs mf = new MailerFuncs();
                        String res ="";
                        try{
                            
                            mf.send_tls2(sMailTo, 
                                sMailSubject, 
                                sMailMessage, 
                                sPathDec3, 
                                sMailHost, 
                                sMailPort, 
                                sMailFrom2, 
                                sMailFromPassword2,
                                sFileName, 
                                false
                                );
                            res = "File sent.";
                        }catch (MessagingException e) {
                            res = "Oops, email sending fail. Please check your email settings";
                        }
                        
                        byte[] kk = res.getBytes();
                        outFile.write(kk);
                        outFile.close();
                    }

                    //bulker (batch checkbox)
                    if (fname.contains("bulker.php")) {
                        
                         

                          p("Processing bulker.php");
                          p("sFoo = '" + fname + "'");
                          
                          bRedirectBulker = true;
                          sRedirectBulkerURL = "echoClient5.htm?foo=" + sFoo2 + 
                                  "&view=" + sView + 
                                  "&ftype=" + sFileType +  
                                  "&days=" + sDaysBack + 
                                  "&numcol=" + sNumCol +
                                  "&numobj=" + sNumObj +
                                  "&dateOld=" + sDateStart +
                                  "&date=" + URLDecoder.decode(sDateStart, "UTF-8") +
                                  "&screenSize=" + sScreenSize +
                                  "&pw=" + password;                         
                        
                        UserSession us = uuidmap.get(sAuthUUID);
                        if(!us.isRemote()){
                            applytag(fname, wf, sAuthUUID);
                        }else{
                            RemoteAccess ra = new RemoteAccess(us.getRemoteCluster());
                            ra.setUuid(us.getUuid());
                            String param = fname.split("bulker.php\\?")[1];
                            String json = ra.remoteApplyTag(param);

                        }
                      
                      //byte[] kk = res.getBytes();
                      //outFile.write(kk);
                      outFile.close();
                    }
                    
// ***************************************************
// shares.htm
// ***************************************************

                   if (fname.contains("shares.htm")) {
                        if (bUserAuthenticated) {
                               if (isUserAdmin(sAuthUUID)) {
                                   sharepage(sAuthUUID, wf, outFile, debug);                                                         
                               } else {
                                String res2 = "";
                                res2 += "Admin required(shares).</p>";                        
                                byte[] kk = res2.getBytes();                          
                                outFile.write(kk);                                                                                    
                               }
                        } else {
                             String res2 = "";
                             res2 += "Login required(shares).</p>";                        
                             byte[] kk = res2.getBytes();                          
                             outFile.write(kk);                                                 
                        } 
                        outFile.close();
                   }
                   
                   if (fname.contains("refreshsharetable.fn")) {
                       String res = getSharesTableContent(false);
                       byte[] kk = res.getBytes();                          
                       outFile.write(kk);  
                   }
                   
                    if (fname.contains("removeshare.fn")) {
                                               
                        String key;
                        ShareTypes shareTy =  ShareTypes.valueOf(shareType);
                        if(shareTy.equals(ShareTypes.CLUSTER)){
                            key = getClusterID();
                        }else{
                            key = shareKey;
                        }
                        
                        
                        ShareController shareCollection = ShareController.getInstance();
                        ShareToken share = shareCollection.getStoredShareToken(key);
                        if(share != null){
                            shareCollection.removeUsers(key);
                        }
                        
                       String res = getSharesTableContent(false);
                       byte[] kk = res.getBytes();                          
                       outFile.write(kk);  
                   }
                    
                    if (fname.contains("doshare.fn") || fname.contains("doshare_webapp.fn")) {
                        
                        boolean bWebApp=false;
                        if(fname.contains("doshare_webapp.fn")){
                            bWebApp=true;
                        }
                        
                        String key;
                        ShareTypes shareTy =  ShareTypes.valueOf(shareType);
                        if(shareTy.equals(ShareTypes.CLUSTER)){
                            key = getClusterID();
                        }else{
                            key = shareKey;
                        }
                        
                        Collection<User> users = new ArrayList<User>();
                        for(String userstr : shareUsers.split(";")){
                            if(!userstr.isEmpty()){
                                User user = UserCollection.getInstance().getUsersByName(userstr);
                                if(user != null)
                                    users.add(user);
                            }                                                
                        }
                        
                        String allowremote = GetConfig("allowremote", "config/www-server.properties");

                        
                        ShareController shareCollection = ShareController.getInstance();
                        ShareToken share = shareCollection.getStoredShareToken(key);
                        String outputString;
                        if(users.isEmpty() && (bMobile||bWebApp)){
                            if(share != null){
                                shareCollection.removeUsers(key);
                                outputString = "deleted";
                            }else{
                                outputString = "error";
                            }
                        }else{
                            if(share == null || (share.getShare_token().equals("offline") &&  allowremote.equals("true"))){
                                String clusterToken = getClusterToken();
                                String bridgeHost = GetConfig("bridge-host", appendage + "../scrubber/config/www-bridge.properties");
                                String bridgePort = GetConfig("bridge-port", appendage + "../scrubber/config/www-bridge.properties");
                                String bridgeSecure = GetConfig("secure", appendage + "../scrubber/config/www-bridge.properties");

                                p("clusterToken: '" + clusterToken + "'");
                                p("bridgeHost: '" + bridgeHost + "'");
                                p("bridgePort: '" + bridgePort + "'");
                                p("bridgeSecure: '" + bridgeSecure + "'");
                                
                                share = shareCollection.createShare(getClusterID(), 
                                        users, 
                                        shareTy, 
                                        bridgeHost, 
                                        bridgePort, 
                                        bridgeSecure, 
                                        clusterToken, 
                                        key, 
                                        allowremote);

                            }else{
                                if(users.isEmpty())
                                    shareCollection.setToAll(key);
                                else
                                    shareCollection.updateUsers(key, users);    
                            }
                            
                            if(share != null){
                                 if(!bMobile && !bWebApp){
                                    loadPropsMailer();
                                    outputString = createInvitationModal(share);
                                 }else{//mobile
                                     if(!allowremote.equals("true")){
                                         outputString = "offline";
                                     }else{
                                         outputString = share.getShare_token();
                                     }
                                 }
                            }else{
                                outputString = "error";
                            }
                        
                               
                        }
                        p(outputString);
                        byte[] kk = outputString.getBytes();
                        outFile.write(kk);
                        outFile.close();
                        
                    }
                    if (fname.contains("getinvitationmodal.fn")) {
                        ShareController shareCollection = ShareController.getInstance();
                        ShareToken share = shareCollection.getStoredShareToken(shareKey);
                        String outputString = createInvitationModal(share);
                        byte[] kk = outputString.getBytes();
                        outFile.write(kk);
                        outFile.close();
                    }
                    
                    if (fname.contains("getsharesettingstag.fn")) {
                        ShareTypes shareTy =  ShareTypes.valueOf(shareType);
                        String key;
                        String title = "Share ";
                        if(shareTy.equals(ShareTypes.CLUSTER)){
                            key = getClusterID();
                            title += "Computer";
                        }else{
                            key = shareKey;
                            title += shareType + " " + shareKey;
                        }
                        ShareToken share = ShareController.getInstance().getStoredShareToken(key);
                        
                        boolean allusers = true;
                        boolean noone = true;
                        Collection<User> selUsers = null;
                        if(share != null){
                            allusers = ShareController.getInstance().allUsers(key);
                            noone = ShareController.getInstance().noUsers(key);
                            selUsers = ShareController.getInstance().getPermissionUsers(key);
                        }
                        Collection<User> users = UserCollection.getInstance().getUsersByRole("user");
                        String outputString = "";
                        String results = "";
                        results += "{\n";
                        results += "\"users\": [\n";
                        boolean bfirst = true;
                        for (User user : users) {
                            if (selUsers == null) {
                                p("selUsers == null");
                            } else {
                                if (selUsers.contains(user)) {
                                    if (bfirst) {
                                        results += "{\n";
                                        bfirst = false;
                                    } else {
                                        results += ",{\n";                    
                                    }
                                    results += "\"username\": \"" + user.getUsername() + "\",\n";
                                    results += "\"email\": \"" + user.getEmail() + "\"\n";                                
                                    results += "}\n";
                                }                                   
                            }
                        }
                        results += "]}\n";
                        byte[] kk = results.getBytes();
                        outFile.write(kk);
                        outFile.close();
                    }
                    
                    if (fname.contains("getsharesettingsmodal.fn")) {
                        ShareTypes shareTy =  ShareTypes.valueOf(shareType);
                        String key;
                        String title = "Share ";
                        if(shareTy.equals(ShareTypes.CLUSTER)){
                            key = getClusterID();
                            title += "Computer";
                        }else{
                            key = shareKey;
                            title += shareType + " " + shareKey;
                        }
                        ShareToken share = ShareController.getInstance().getStoredShareToken(key);
                        
                        boolean allusers = true;
                        boolean noone = true;
                        Collection<User> selUsers = null;
                        if(share != null){
                            allusers = ShareController.getInstance().allUsers(key);
                            noone = ShareController.getInstance().noUsers(key);
                            selUsers = ShareController.getInstance().getPermissionUsers(key);
                        }
                        
                       
                        String outputString = 
                               "<div class=\"modal-header\">\n" +
                                    "<h3 id=\"myModalLabel\">" + title + "</h3>\n" +
                                    "</div>\n" +
                                    "<div class=\"modal-body\">\n" +
                                        "<h5>Who has access</h3>" +
                                        "<div class=\"radio\">\n" +
                                            "<label>\n" +
                                                "<input type=\"radio\" name=\"optionsRadios\" id=\"optionsRadios\" value=\"option1\" "+ (allusers?"checked":"") +">\n" +
                                                "Any user\n" +
                                            "</label>\n" +
                                            "</div>\n" +
                                            "<div class=\"radio\">\n" +
                                                "<label>\n" +
                                                    "<input type=\"radio\" name=\"optionsRadios\" id=\"optionsRadios\" value=\"option2\" "+ (!allusers&&!noone?"checked":"") +">\n" +
                                                    "Selected users\n" +
                                                "</label>\n" +
                                        "</div>";
                        outputString += "<div id=\"checkboxes\" "+ (allusers||noone?"style=\"display: none;\"":"") + ">\n";
                        Collection<User> users = UserCollection.getInstance().getUsersByRole("user");
                        for (User user : users) {
                             outputString += "<div class=\"checkbox\" style=\"padding-left: 2em;\">\n" +
                                    "<label>\n" +
                                        "<input class=\"groupuser\" type=\"checkbox\" value=\"" + user.getUsername() + "\" " + (selUsers != null && selUsers.contains(user)?"checked":"") +">\n" +
                                        user.getUsername() +
                                    "</label>\n" +
                                "</div>";
                        }
                        outputString += "</div>\n";
                        outputString += "<button id=\"adduser\" type=\"button\" class=\"btn btn-primary\" onclick=\"openadduserModal();\" "+ (allusers||noone?"style=\"display: none;\"":"") +">Add user</button>";
                               
                        outputString +=
                                 "<script>\n" +
                                    "$('input:radio[name=\"optionsRadios\"]').change(\n" +
                                    "    function(){\n" +
                                    "        if ($(this).is(':checked') && $(this).val() == 'option1') {\n" +
                                    "            $(\"#adduser\").hide();\n" +
                                    "            $(\"#checkboxes\").hide();\n" +
                                    "        }else{\n" +
                                    "		$(\"#adduser\").show();\n" +
                                    "		$(\"#checkboxes\").show();\n" +
                                    "       }\n" +
                                    "    });\n" +
                                    "</script>";

                        
                        outputString +="</div>";
                                  
                        outputString +="<div class=\"modal-footer\">\n" +
                                "<input type='hidden' id='shareusers' name='shareusers'/>" +
                                "<input type='hidden' id='typechosen' name='typechosen' value='" + shareType + "'/>" +
                                "<input type='hidden' id='keychosen' name='keychosen' value='"+ shareKey + "'/>" +
"                                <button class=\"btn\" data-dismiss=\"modal\" aria-hidden=\"true\">Cancel</button>\n" +
                                "<button id=\"nextbutton\" type=\"button\" class=\"btn btn-primary\" onclick=\"share();\">"+ (share==null?"Share":"Update")  +"</button>" +
"                        </div>";      
                         
                        if(!shareHtml){
                            outputString = "<div class=\"modal hide\" id=\"myModal\" tabindex=\"-1\" role=\"dialog\" aria-labelledby=\"myModalLabel\" aria-hidden=\"true\">"
                                + outputString 
                                + "</div>";
                        }
                                                                        
                        p(outputString);
                        
                        
                        byte[] kk = outputString.getBytes();
                        outFile.write(kk);
                        outFile.close();
                        
                    }
                    if (fname.contains("adduser.fn")) {
                        String outputString;
                        if (bUserAuthenticated) {
                            int returnCode = UserCollection.getInstance().addUser(sBoxUser, sBoxPassword, useremail);
                            if(returnCode==1){
                                UpdateConfig("allowotherusers", "true", "config/www-server.properties");     
                                outputString = "success";       
                            }else if(returnCode==0){
                                outputString = "alreadyexists";
                            }else{
                                outputString = "error";
                            }
                            p(outputString);
                        
                            byte[] kk = outputString.getBytes();
                            outFile.write(kk);
                            outFile.close();
                        }    

                    }
                    
                    
                    if (fname.contains("invitation.fn") || fname.contains("invitation_webapp.fn")) {
                        
                        
                        boolean bWebApp = false;
                        if (fname.contains("invitation_webapp.fn")) {
                            bWebApp = true;
                        }
                        String key;
                        ShareTypes shareTy =  ShareTypes.valueOf(shareType);
                        String sharedName;
                        if(shareTy.equals(ShareTypes.CLUSTER)){
                            key = getClusterID();
                            sharedName = "the computer '" + GetConfig("signature", appendage + "../scrubber/config/www-rtbackup.properties") + "'";
                        }else{
                            key = shareKey;
                            sharedName = "the tag '" + key + "'";
                        }

                        System.out.println("Key = " + key);

                        ShareController sc = ShareController.getInstance();
                        
                        ShareToken share = sc.getStoredShareToken(key);
                        sMailTo = "";

                        
                        Collection<User> users = sc.getPermissionUsersNotNotified(key);                         
                        
                        
                        if((users != null && !users.isEmpty() || bWebApp)){
                            if (users != null) {
                                System.out.println("#users: " + users.size());
                                for (User user : users) {
                                    if(user.getEmail()!=null && !user.getEmail().isEmpty()){
                                        if(!sMailTo.isEmpty())
                                            sMailTo += ",";
                                        sMailTo += user.getEmail();
                                    }
                                }
                            }
                            
                            if(!sMailTo.isEmpty() || bWebApp){
                        
                                sMailSubject = "I've shared " + sharedName + " with you via Alterante";

                                String allowremote = GetConfig("allowremote", "config/www-server.properties");
                                String accessText;
                                if(allowremote.equals("true")){
                                    accessText = "<div>To access, click on this link:<br><br>"
                                        + "Web:<br>https://web.alterante.com/cass/index.htm?cluster=" + getClusterID() + "<br><br>"
                                        + "iPhone/iPad:<br>alterante://access?token=" + share.getShare_token() + "<br><br>"
                                        + "Android:<br>http://alterante/access?token=" + share.getShare_token() + "<br><br>"
                                        + "-<br>"
                                        + "Having trouble?<br>"
                                        + "If you already installed Alterante app on your device and you cannot access through the previous link, try to copy/paste it on your mobile browser.<br><br>";
                                }else{
                                    accessText = "<div>To access login to Alterante<br><br>";
                                }
                                sMailMessage = "<h3>" + sMailSubject + ". </h3>"
                                        + accessText
                                        + "If you don't have the Alterante app on your mobile, download it from the Apple or Google store:<br><br>"
                                        + "iPhone/iPad: https://itunes.apple.com/us/app/alterante/id785613128?mt=8<br><br>"
                                        + "Android: https://play.google.com/store/apps/details?id=com.alterante.app1a<br><br><br>"
                                        + "</div>";
                                
                                if (fname.contains("invitation_webapp.fn")) {
                                    String results = "";
                                    results += "{\n";
                                    results += "\"invitation\": \n";
                                    

                                    String sMailMessage_enc = URLEncoder.encode(sMailMessage, "UTF-8");

                                    sMailMessage_enc = sMailMessage_enc.replaceAll("%3Cbr%3E", "%0A%0D");
                                    sMailMessage_enc = sMailMessage_enc.replaceAll("%3Cdiv%3E", "");
                                    sMailMessage_enc = sMailMessage_enc.replaceAll("%3C%2Fdiv%3E", "");
                                    sMailMessage_enc = sMailMessage_enc.replaceAll("%3Ch3%3E", "");
                                    sMailMessage_enc = sMailMessage_enc.replaceAll("%3C%2Fh3%3E", "");
                                    
                                    results += "{\n";
                                    results += "\"email_to\": \"" + URLEncoder.encode(sMailTo, "UTF-8") + "\",\n";
                                    results += "\"email_subject\": \"" + URLEncoder.encode(sMailSubject, "UTF-8") + "\",\n";
                                    results += "\"email_body\": \"" + sMailMessage_enc + "\"\n";
                                    results += "}\n";
                                    results += "}\n";
                                    
                                    System.out.println("Invitation results: " + results);
                                    
                                    byte[] kk = results.getBytes();
                                    outFile.write(kk);
                                    outFile.close();

                                } else {
                                    MailerFuncs mf = new MailerFuncs();

                                    mf.send_tls2(sMailTo, 
                                        sMailSubject, 
                                        sMailMessage, 
                                        null, 
                                        sMailHost, 
                                        sMailPort, 
                                        sMailFrom, 
                                        sMailFromPassword,
                                        null,
                                        true
                                        );

                                    sc.markAllAsNotified(key);                                    
                                }
                            } else {
                                System.out.println("[***mailto empty ***]");
                            }
                        } else {
                                System.out.println("[***users empty ***]");                        
                        }                                                                    
                    }
                    
                    
                        
                    
                    //bulker (batch checkbox)
                    if (fname.contains("applytags.fn")) {
                        
                        p("   ***---------------applytags-------------");
                        p("   ***bUserAuthenticated: " + bUserAuthenticated);
                                                
                        if (bUserAuthenticated){
                            UserSession us = uuidmap.get(sAuthUUID);

                            p("   ***us.isRemote()     : " + us.isRemote());
                            
                            if (us.isRemote() || isValidMultiClusterID(sMultiClusterID)) {
                                System.out.println("REMOTE APPLYTAGS");
                                RemoteAccess ra = null;
                                if(isValidMultiClusterID(sMultiClusterID)){
                                    ra=MultiClusterManager.getInstance().getRA(us.getUsername(),sMultiClusterID);
                                }else{
                                    ra=new RemoteAccess(us.getRemoteCluster());
                                    ra.setUuid(us.getUuid());
                                }
                                String param = fname.split("applytags.fn\\?")[1];
                                System.out.println("REMOTE APPLYTAGS PARAM: '" + param + "'");
                                String json = ra.remoteApplyTag(param);
                                outFile.write(json.getBytes());
                                outFile.close();                                                                
                            } else {
                                p("   ***fname:   : " + fname);
                                p("   ***   wf:   : " + wf);
                                p("   ***sAuthUUID: " + sAuthUUID);

                                applytag(fname, wf, sAuthUUID);                             
                                String results="true";
                                outFile.write(results.getBytes());
                                outFile.close();                                
                            }

                        }
                }
                if (fname.contains("getusersandemail.fn")) {
                    
                    if (bUserAuthenticated){
                        String results = "";
                        Collection<User> users = UserCollection.getInstance().getUsersByRole("user");
                        results += "{\n";
                        results += "\"users\": [\n";
                        boolean bfirst = true;
                        for (User user : users) {
                            if (bfirst) {
                                results += "{\n";
                                bfirst = false;
                            } else {
                                results += ",{\n";                    
                            }
                            
                            boolean hasPerm = false;
                            if(ShareController.getInstance().getPermissionByUser(shareKey, user.getUsername()) != null){
                                hasPerm = true;
                            }
                            
                            results += "\"username\": \"" + user.getUsername() + "\",\n";
                            results += "\"email\": \"" + user.getEmail() + "\",\n";
                            results += "\"hasPerm\": \"" + hasPerm + "\"\n";
                            results += "}\n";

                        }

                        results += "]\n";
                        results += "}\n";
                        outFile.write(results.getBytes());
                        outFile.close();
                    }
               }
               
               if (fname.contains("getbackupconfig.fn")) {
                   if (bUserAuthenticated) {
                        String sFileBackup = appendage + "../scrubber/config/backupconfig.c";
                        File f = new File(sFileBackup);
                        if (f.exists()) {
                            is = new FileInputStream(sFileBackup);
                            try {
                                 int n;
                                 while ((n = is.read(buf)) > 0) {
                                     outFile.write(buf, 0, n);
                                 }
                            } finally {
                                 is.close();
                            }                            
                        } else {
                            String results = "[]";
                            outFile.write(results.getBytes());                            
                        }
                       outFile.close();                        
                   }
               }
               
                if (fname.contains("chat_pull.fn")) {
                    if (bUserAuthenticated) {
                        UserSession us = uuidmap.get(sAuthUUID);
                        if (us.isRemote() || isValidMultiClusterID(sMultiClusterID)) {
                            System.out.println("REMOTE CHAT PULL ");
                            RemoteAccess ra = null;
                            if(isValidMultiClusterID(sMultiClusterID)){
                                ra=MultiClusterManager.getInstance().getRA(us.getUsername(), sMultiClusterID);
                            }else{
                                ra=new RemoteAccess(us.getRemoteCluster());
                                ra.setUuid(us.getUuid());
                            }
                            
                            
                            String param = fname.split("chat_pull.fn\\?")[1];
                            System.out.println("REMOTE CHAT PULL PARAM: '" + param + "'");
                            String json = ra.remoteChatPull(param);
                            System.out.println("REMOTE CHAT PULL JSON: '" + json + "'");
                            outFile.write(json.getBytes());
                            outFile.close();                                                                                                                            
                        } else {
                            System.out.println("---------- Chat Pull(LOCAL)");
                            System.out.println("msg_date: " + msg_date);

                            String results = "";

                            results += "{\n";
                            results += "\"messages\": [\n";
                            boolean bfirst = true;
                            Integer likes=0;
                            ArrayList<UserMessage> msgs = new ArrayList<UserMessage>();
                            if(sMD5==null || sMD5.trim().length()==0){
                                msgs=chats.getMessagesbyDate(Long.valueOf(msg_date));
                            }else{
                                msgs=chats.getCommentsbyDate(sMD5,Long.valueOf(msg_date));
                                likes=chats.getLikes(sMD5);
                            }

                            for (UserMessage msg: msgs) {
                                System.out.println(msg.getTimestamp() + " " + msg.getUsername() + " " + msg.getMessage());
                                if (bfirst) {
                                    results += "{\n";
                                    bfirst = false;
                                } else {
                                    results += ",{\n";                    
                                }

                                results += "\"msg_date\": \"" + msg.getTimestamp() + "\",\n";
                                results += "\"msg_type\": \"" + msg.getMessageType() + "\",\n";
                                results += "\"msg_user\": \"" + msg.getUsername() + "\",\n";
                                results += "\"msg_body\": \"" + msg.getMessage() + "\"\n";
                                results += "}\n";

                            }

                            results += "]\n";
                            results += ",\"likes\":"+likes+"\n";
                            results += "}\n";
                            outFile.write(results.getBytes());
                            outFile.close();

                            System.out.println("---------- End Chat Pull");
                            
                        }
                    } else {
                        
                    }
               }
               
               if (fname.contains("chat_clear.fn")) {
                    if (bUserAuthenticated) {
                        UserSession us = uuidmap.get(sAuthUUID);
                        if (us.isRemote()) {
                            System.out.println("REMOTE CHAT CLEAR ");
                            RemoteAccess ra = new RemoteAccess(us.getRemoteCluster());
                            ra.setUuid(us.getUuid());
                            String param = "";
                            String json = ra.remoteChatClear(param);
                            System.out.println("REMOTE CHAT CLEAR JSON: '" + json + "'");
                            outFile.write(json.getBytes());
                            outFile.close();                                                                                                                            
                        } else {
                            System.out.println("---------- Chat Clear(LOCAL)");
                            chats.clearChats();
                            String results = "{\"result\":true}";
                            outFile.write(results.getBytes());
                            outFile.close();

                            System.out.println("---------- End Chat Pull");
                            
                        }
                    } else {
                        
                    }
               }

               if (fname.contains("chat_push.fn")) {
                   if (bUserAuthenticated) {
                        UserSession us = uuidmap.get(sAuthUUID);
                        if (us.isRemote() || isValidMultiClusterID(sMultiClusterID)) {
                            System.out.println("REMOTE CHAT PUSH ");
                            RemoteAccess ra = null;
                            if(isValidMultiClusterID(sMultiClusterID)){
                                ra=MultiClusterManager.getInstance().getRA(us.getUsername(), sMultiClusterID);
                            }else{
                                ra=new RemoteAccess(us.getRemoteCluster());
                                ra.setUuid(us.getUuid());
                            }
                           
                            String param = fname.split("chat_push.fn\\?")[1];
                            System.out.println("REMOTE CHAT PUSH PARAM: '" + param + "'");
                            String json = ra.remoteChatPush(param);
                            outFile.write(json.getBytes());
                            outFile.close();                                                                                                                            
                        } else {
                            System.out.println("---------- Chat Push");
                            System.out.println("msg_date: " + msg_date);
                            System.out.println("msg_type: " + msg_body);
                            System.out.println("msg_user: " + msg_user);
                            System.out.println("msg_body: " + msg_body);
                            if(sMD5!=null){
                                System.out.println("md5: "+sMD5);
                            }


                            int res =0;
                            if(msg_type.equalsIgnoreCase("CHAT") || msg_type.equalsIgnoreCase("EVENT")){
                               res=chats.addMessage(Long.valueOf(msg_date), msg_type, msg_user, msg_body);
                            }else if(msg_type.equalsIgnoreCase("COMMENT")){
                               res=chats.addComment(sMD5,Long.valueOf(msg_date), msg_type, msg_user, msg_body);
                            }else if(msg_type.equalsIgnoreCase("LIKE") || msg_type.equalsIgnoreCase("FB")){
                               if(msg_type.equalsIgnoreCase("LIKE")){
                                   res=chats.addLike(sMD5);
                               }else{
                                   res=1;
                               }
                              
                               Date utcDate=DateUtil.getUTCDate();
                               sFileName = wf.get_row_attribute(keyspace, "Standard1", sMD5, "name", dbmode);
                               String file_path_webapp="/cass/getfile.fn?sNamer="+sMD5;
                               String file_remote_webapp="/cass/openfile.fn?sNamer="+sMD5;
                               String file_folder_webapp="/cass/openfolder.fn?sNamer="+sMD5;
                               String ext = sFileName.substring(sFileName.lastIndexOf(".")+1, sFileName.length()).toLowerCase();
                               String video_url_webapp="/getvideo.m3u8?md5="+sMD5;
                               String tags=wf.getTagsForFile(sMD5,msg_user);

                               if(THUMBNAIL_OUTPUT_DIR==null || THUMBNAIL_OUTPUT_DIR.trim().length()==0){
                                   loadPropsProcessor();
                               }
                               String sThumbnails=wf.getThumbnails(sMD5,THUMBNAIL_OUTPUT_DIR);
                               if(sThumbnails.trim().length()==0){
                                    res=chats.addMessage(utcDate.getTime(), "EVENT", msg_user, "{'#'play'#':true,'#'file_tags'#':'#'"+tags+"'#','#'file_thumbnail'#':'#'"+sThumbnails+"'#','#'video_url_webapp'#':'#'"+video_url_webapp+"'#','#'file_ext'#':'#'"+ext+"'#','#'file_group'#':'#'"+wf.getFileGroup(sFileName)+"'#','#'nickname'#':'#'"+sMD5+"'#','#'file_folder_webapp'#':'#'"+file_folder_webapp+"'#','#'file_remote_webapp'#':'#'"+file_remote_webapp+"'#','#'file_path_webapp'#':'#'"+file_path_webapp+"'#', '#'name'#':'#'"+sFileName+"'#','#'msg'#':'#'LIKE "+sFileName+"'#'}");   
                               }else{
                                    String msg="'#'LIKE'#'";
                                    if( msg_type.equalsIgnoreCase("FB")){
                                        msg="'#'Share on Facebook'#'";
                                    }
                                    String likeTemplate="{ '#'play'#':true,'#'file_tags'#':'#'"+tags+"'#', '#'file_thumbnail'#':'#'"+sThumbnails+"'#','#'video_url_webapp'#':'#'"+video_url_webapp+"'#','#'file_ext'#':'#'"+ext+"'#', '#'file_group'#':'#'"+wf.getFileGroup(sFileName)+"'#','#'nickname'#':'#'"+sMD5+"'#','#'file_folder_webapp'#':'#'"+file_folder_webapp+"'#','#'file_remote_webapp'#':'#'"+file_remote_webapp+"'#','#'file_path_webapp'#':'#'"+file_path_webapp+"'#', '#'name'#':'#'"+sFileName+"'#','#'thumbnail'#':'#'"+sThumbnails+"'#','#'msg'#':"+msg+"}";				
                                    res=chats.addMessage(utcDate.getTime(), "EVENT", msg_user, likeTemplate);   
                               }



                            }
                            String results = "";
                            results += "{\n";
                            results += "\"res\":" + res + "\n";
                            results += "}\n";

                            outFile.write(results.getBytes());
                            outFile.close();

                            System.out.println("---------- End Chat Push");
                            
                        }
                   } else {
                       
                   }

               }

                                        
                   
                }
                
                if (targ.isDirectory()) {
                    File ind = new File(targ, "index.html");
                    if (ind.exists()) {
                        targ = ind;
                    }
                }
            }
            
            else {
            }


            //GET HANDLER (VAULT FILE)            
            if (doingGet && vaultMode) {
                get_vault_file(31300, sPathDec);

                targ = new File ("vault/" + sPathDec);
                
                boolean OK = printHeaders(targ, ps, bFull, sFileRange, true, sPathDec, false, sBoxUser, sGetFileExt, false, sKeyPassword, sIV, null, null, false, 0, "", "");
                if (OK) {
                    sendFile(targ, ps);
                }

            }
            //GET HANDLER (LOCAL FILE)            
            if (doingGet && !vaultMode) {
                //p("Print headers GET");
                //p("spathdec = '" + sPathDec + "'");
                String sPathDec2 = sPathDec;
                //p("spathdec2 decoded = '" + sPathDec2 + "'");
                
//                if (targ.exists()) {
//                    p(targ.getCanonicalPath() + "EXISTS");                    
//                } else {
//                    p(targ.getCanonicalPath() + "NOT EXISTS");                    
//                }
                
                //targ = new File ("/" + sPathDec2);
                
                if (!bRedirect) {
                    boolean OK =false;
                    boolean failMobile = false;
                    if(bMobile && !bUserAuthenticated && !fname.contains("login.fn") && !fname.contains("getcluster.fn") 
                            && !fname.contains("getsession.fn")){
                        failMobile = true;
                    }
                            
                    OK= printHeaders(targ, ps, bFull, sFileRange, false, sPathDec, bAuth, sBoxUser, sGetFileExt, failMobile, sKeyPassword, sIV, uuid, cluster, aesEncryptSession, aesSizeSession, sNamer, sGetFileName);
                    
                    if (OK) {
                        if (bFull) {
                            sendFile(targ, ps);
                        } else {
                            sendFilePartial(targ, ps, nFileStart, nFileEnd);
                        }
                    } else {
                        if (sPathDec2.contains("backup_")){
                            //Generate .gen file if not exist
                            File tmpFile = new File(sPathDec2 + ".gen");
                            if (!tmpFile.exists()) {
                                FileWriter fw = new FileWriter(sPathDec2 + ".gen", false);
                                BufferedWriter outgen = new BufferedWriter(fw);
                                outgen.close();
                                log("GEN FILE GENERATED: " + sPathDec2 + ".gen", 2);
                            }
                        }
                        send404(targ, ps);
                    }
                } else {
                    boolean OK = printHeadersRedirect(targ, ps, sRedirectURI, bWindows,bLinux, sRemote, bWindowsServer);
                }
            }
            //POST HANDLER
            if (doingPost) {
                p("doingPost = true");
                String sQueryStringTmp = processPost(ps, nread);
                
                String sQueryString = URLDecoder.decode(sQueryStringTmp, "UTF-8");
                p("Print headers POST: " + sQueryString);

                p("fname POST: '" +  fname + "'");
                if (!fname.contains("rpc.php")) {
                    fname = "temp_post.txt";
                }
                String sPathDec = root + File.separator + fname;
                //p("sPathDec: " +  sPathDec);
                targ = new File(sPathDec);
                p("POST CASE file: " +  targ.getAbsolutePath());
                String sFileNew = targ.getAbsolutePath();
                FileOutputStream outFile = new FileOutputStream(sFileNew);
                
                String res = "";
                if (!fname.contains("rpc.php")) {
                    res = "OK@@@";                    
                } else {
                    boolean bRetry = true;
                    int nCount = 3;
                    while (bRetry && nCount > 0) {
                        UserSession us = uuidmap.get(sAuthUUID);
                        String sUser;
                        if(us != null){
                            sUser = us.getUsername();
                        }else{
                            sUser = null;
                        }
                        if(!us.isRemote()){
                            res = wf.echoac(sQueryString, dbmode, sUser);
                        }else{
                            RemoteAccess ra = new RemoteAccess(us.getRemoteCluster());
                            ra.setUuid(us.getUuid());
                            String json = ra.remoteSuggest(".all", ".all", sQueryString, "6");
                            res = HtmlFromJSON.getSuggestion(sQueryString, sUser, json);
                        }
                        if (!res.contains("ERROR_")) {
                            bRetry = false;
                        } else {
                            //error;
                            nCount = nCount - 1;
                            p(res + " SLEEPING BEFORE RETRY. Count = " + nCount);
                            long lSleep = 100;
                            try {
                                Thread.sleep(lSleep);
                            } catch (Exception ex) {

                            }
                        }
                    }                    
                }

                byte[] kk = res.getBytes();
                outFile.write(kk);
                outFile.close();

                //targ = new File(sFile);
                boolean OK = printHeadersPost(targ, ps);
                if (OK) {
                    sendFilePost(targ,ps);
                } else {
                    send404(targ, ps);
                }
            }
            if (genFile) {
                p("deleting tmp file: " + targ.getAbsolutePath());
                boolean bres = targ.delete();
                p("delete res: " + bres);
            }
        } catch (Exception ex) {
            p("hit Exception: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            
            if (dbmode.contains("cass")) {
                //int res = wf.disconnectCassandra();                
                //p("disconnect: " + res);
            }
            
            //wf = null;
            
            s.close();
            long freeMem;
            Runtime r = Runtime.getRuntime();
            freeMem = r.freeMemory();
            //p("freemem1 = " + freeMem);            
            //r.gc();
            freeMem = r.freeMemory();
            //p("freemem2 = " + freeMem);
            timestamp2 = System.currentTimeMillis();
            long timestamp = timestamp2 - timestamp1;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
            String sDate = sdf.format(timestamp);
            //p("Elapsed: " + sDate);
            int nSeconds  = (int) timestamp / 1000;
            //p("Elapsed: " + nSeconds);
            if (nSeconds > timeshut*60) {
                p(timeshut + " mins have elapsed. ");
                if (bExitAfterTimeshut) {
                    p("exiting...");
                    System.exit(0);
                }
            }  
            //p("--------done--------");

        } 
    }

    private String getVersion() throws IOException {
        String sBuild = "??";
        String sFileB = "../../update.new";
        if (!bWindowsServer) {
            sFileB = "../../update.last";
        }
        File fb = new File (sFileB);
        if (fb.exists()) {
            FileReader fr = new FileReader(fb);
            BufferedReader br = new BufferedReader(fr);
            sBuild = br.readLine();
        }
        return sBuild;
    }

    private String findtag(String fname) {
        
        String delimiters = "?&";
        StringTokenizer st = new StringTokenizer(fname, delimiters, false);
        String sHash = "";
        String sHiddenPw = "";
        String res = "";
        
        try {
            while (st.hasMoreTokens()) {
                String text2 = st.nextToken();
                p("text2 = '" + text2 + "'");
                if (text2.contains("tag=")) {
                    String sHashEnc = text2.substring(4, text2.length());
                    p("sHash = '" + sHashEnc + "'");
                    sHash = URLDecoder.decode(sHashEnc, "UTF-8").toLowerCase();
                    if (sHash.contains("hidden:")) {
                        sHiddenPw = sHash.substring(7,sHash.length());
                        p("sHiddenPw = '" + sHiddenPw + "'");
                        res += "sHiddenPw: '" + sHiddenPw + "'";
                    }
                }            
            }
            p("Found tag sHash: " + sHash);
            return sHash;                    
        } catch (Exception ex) {
            ex.printStackTrace();
            return "ERROR";
        }                
    }
    
    private void applytag(String fname, WebFuncs wf, String sAuthUUID) throws UnsupportedEncodingException {
        String res = "";
        String delimiters;
        StringTokenizer st;
        delimiters = "?&";
        st = new StringTokenizer(fname, delimiters, false);
        Boolean bDeleteTag = false;
        String sHash = "";
        String sHiddenPw = "";
        String sDeleteTagValue = "";
        Boolean bSomethingDeleted = false;
        
        sHash = findtag(fname);  /// find the tag first , before loop
        
        while (st.hasMoreTokens()) {
            String text2 = st.nextToken();
            p("text2 = '" + text2 + "'");
            if (text2.contains("tag=")) {
                String sHashEnc = text2.substring(4, text2.length());
                p("sHash = '" + sHashEnc + "'");
                sHash = URLDecoder.decode(sHashEnc, "UTF-8").toLowerCase();
                res += "Hash: '" + sHash + "'";
                if (sHash.contains("hidden:")) {
                    sHiddenPw = sHash.substring(7,sHash.length());
                    p("sHiddenPw = '" + sHiddenPw + "'");
                    res += "sHiddenPw: '" + sHiddenPw + "'";
                }
            }
            
            if (text2.contains("DeleteTag=")) {
                sDeleteTagValue = text2.substring(text2.indexOf("=") + 1, text2.length());
                if (sDeleteTagValue.length() > 0) {
                    bDeleteTag = true;
                }
            }
            
            if (text2.contains("=on")) {
                
                String sObjectID = text2.substring(0, text2.indexOf("="));
                String sObjectID2 = String.format("%32s", sObjectID).replace(" ","0");
                
                p("sObjectID = " + sObjectID);
                p("sObjectID (after fix) = " + sObjectID2);
                
                if (bDeleteTag) {
                    p("deleting hash <" + keyspace + "," + sObjectID + "," + sHash + ">");
                    res += "deleting hash <" + keyspace + "," + sObjectID + "," + sHash + ">";
                    String sDateModified = wf.get_row_attribute(keyspace, "Standard1", sObjectID, "date_modified", dbmode);
                    res += "sDateModified = '" + sDateModified + "' ";
                    p("date modified <" + sDateModified + ">");
                    res += wf.delete_hash(keyspace, sObjectID, sHash, sDateModified, dbmode);
                    bSomethingDeleted = true;
                    if (sHiddenPw.length() > 0) {
                        res += wf.delete_column(keyspace, "Standard1", sObjectID, "hidden", dbmode);
                        p("deleting hash hidden@ <" + keyspace + "," + sObjectID + ">");
                        res += wf.delete_column(keyspace, "Standard1", "hidden@", sObjectID, dbmode);
                        wf.remove_hidden(sObjectID);
                    }
                }
                else {
                    p("inserting hash <" + keyspace + "," + sObjectID + "," + sHash + ">");
                    res += "inserting hash <" + keyspace + "," + sObjectID + "," + sHash + ">";
                    boolean bAutoComplete = true;
                    if (sHiddenPw.length() > 0) {
                        p("inserting hash <" + keyspace + "," + sObjectID + "," + sHiddenPw + ">");
                        int re = wf.insert_name_value(keyspace, "Standard1", sObjectID, "hidden", sHiddenPw, dbmode);
                        p("inserting hash hidden@ <" + keyspace + "," + sObjectID + "," + sHiddenPw + ">");
                        re = wf.insert_name_value(keyspace, "Standard1", "hidden@", sObjectID, sHiddenPw, dbmode);
                        wf.insert_hidden(sObjectID, sHiddenPw);
                        bAutoComplete = false;
                    }
                    String sDateModified = wf.get_row_attribute(keyspace, "Standard1", sObjectID, "date_modified", dbmode);
                    res += "sDateModified = '" + sDateModified + "' ";
                    p("date modified <" + sDateModified + ">");
                    res += wf.insert_hash(keyspace, sObjectID, sHash, bAutoComplete, sDateModified, dbmode);
                }
            }
        }
        //if no checks, then delete only the clicked tag.. this is for the case when user clicks an X, without selecting any objects.
        if (bDeleteTag && !bSomethingDeleted) {
            p("deleting clicked hash <" + keyspace + "," + sDeleteTagValue + "," + sHash + ">");
            res += "deleting clicked hash <" + keyspace + "," + sDeleteTagValue + "," + sHash + ">";
            String sDateModified = wf.get_row_attribute(keyspace, "Standard1", sDeleteTagValue, "date_modified", dbmode);
            res += "sDateModified = '" + sDateModified + "' ";
            p("date modified <" + sDateModified + ">");
            res += wf.delete_hash(keyspace, sDeleteTagValue, sHash, sDateModified, dbmode);
            
            if (sHiddenPw.length() > 0) {
                res += wf.delete_column(keyspace, "Standard1", sDeleteTagValue, "hidden", dbmode);
                p("deleting hash hidden@ <" + keyspace + "," + sDeleteTagValue + ">");
                res += wf.delete_column(keyspace, "Standard1", "hidden@", sDeleteTagValue, dbmode);
                wf.remove_hidden(sDeleteTagValue);
            }
        }
        
        if(!bDeleteTag){
             if(!sHash.isEmpty()){     
                ShareController shareCollection = ShareController.getInstance();

                UserSession us = uuidmap.get(sAuthUUID);
                String sUser;
                if(us != null){
                    sUser = us.getUsername();
                }else{
                    sUser = null;
                }
                if(!UserCollection.getInstance().getUserAdmin().getUsername().equals(sUser)){   
                    ShareToken share = shareCollection.getStoredShareToken(sHash);
                    if(share == null){
                        Collection<User> users = new ArrayList<User>();
                        users.add(UserCollection.getInstance().getUsersByName(sUser));
                        String clusterToken = getClusterToken();
                        String bridgeHost = GetConfig("bridge-host", appendage + "../scrubber/config/www-bridge.properties");
                        String bridgeSecure = GetConfig("secure", appendage + "../scrubber/config/www-bridge.properties");
                        String allowremote = GetConfig("allowremote", appendage + "config/www-server.properties");
                        String bridgePort = GetConfig("bridge-port", appendage + "../scrubber/config/www-bridge.properties");
                               
                        share = shareCollection.createShare(getClusterID(), users, ShareTypes.TAG, bridgeHost, bridgePort, bridgeSecure, clusterToken, sHash, allowremote);
                        p("sharetoken = " + share.getShare_token() + " created");
                    }else{
                        p("share already exists");
                    }
                }else{
                    p("user is admin not share token created");
                }
          }
        }
    }

    private void sharepage(String sAuthUUID, WebFuncs wf, FileOutputStream outFile, boolean debug) throws IOException {
        String res2 = "";
        
        String allowremote = GetConfig("allowremote", "config/www-server.properties");
        if (allowremote.equals("true")) {
            String sLink = "https://web.alterante.com/cass/index.htm?cluster=" + getClusterID();
            res2 += "Use this link to access this computer remotely:" + "<br>" + 
                  "<a target=\"blank\" href=\"" + sLink + "\">" + sLink + "</a><br><br>";              
        } else {
            res2 += "Remote Access is currently disabled. Go to 'Settings > Network and Users' to enable it." + "<br><br>";
        }
        
        res2 += "<table id=\"sharestable\" cellpadding=\"10\" border=" + "\"1\"" + ">";
        
        
        res2 += getSharesTableContent(debug);
        
        
        res2 += "</table>";
        
        
        File f = new File (root + File.separator + "cass/shares.htm");
        String sBuffer = loadFileStr(f);
        sBuffer = sBuffer.replace("***REP***", res2);
        
        UserSession us = uuidmap.get(sAuthUUID);
        String sUser;
        if(us != null){
            sUser = us.getUsername();
        }else{
            sUser = null;
        }
        SortableValueMap<String,Integer> tags = wf.getTags(sUser);
        Set<String> allTags = tags.keySet();
        String tagsstring = "";
        for (String string : allTags) {
            tagsstring +=  "<option>" + string + "</option>";
        }
        
        sBuffer = sBuffer.replace("***REP1***", tagsstring);
        
        
        byte[] kk = sBuffer.getBytes();
        outFile.write(kk);
    }   

    public void send_tls2(
            String _sMailTo, 
            String _sMailSubject, 
            String _sMailMessage, 
            String _sMailAttachment,
            String _sMailHost,
            String _sMailPort,
            String _sMailUser,
            String _sMailPassword,
            String _sFileName
            ) {
                
        log("send_tls", 2);
        
        final String username = _sMailUser;
        final String password = _sMailPassword;
        
        log("file: '" + _sMailAttachment + "'",2);
        log("filename: '" + _sFileName + "'",2);
        log("mailto: '" + _sMailTo + "'",2);
        log("message: '" + _sMailMessage + "'",2);
        log("user: '" + _sMailUser + "'",2);
        log("host: '" + _sMailHost + "'",2);
        log("port: '" + _sMailPort + "'",2);
        
        String _sMailTo2 = "";
        try {
            _sMailTo2 = URLDecoder.decode(_sMailTo, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        log("mailto2: '" + _sMailTo2 + "'",2);

        String _sMailMessage2 = "";
        try {
            _sMailMessage2 = URLDecoder.decode(_sMailMessage, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage(),2);
            return;
        }
        log("mailto2: '" + _sMailTo2 + "'",2);

        
        //String host = "smtp.live.com";
        //int port = 587;
        //int port = Integer.parseInt(_sMailPort);
        
        //String username = "agoyen@hotmail.com";
        //String password = "Inspiron800m";
        
        Properties props = new Properties();
        
        props.put("mail.smtp.host", _sMailHost);
        //props.put("mail.smtp.socketFactory.port", "587");
        //props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.port", _sMailPort);
 
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        
        //props.put("mail.from", "agoyen@hotmail.com");
        //props.put("mail.smtp.socketFactory.fallback", "false");       
        
        //Session session = Session.getInstance(props);
        Session session = Session.getInstance(props, 
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });
        
        try {
            log("preparing message",2);
            Message message = new MimeMessage(session);
            
            message.setFrom(new InternetAddress(username));
            
            //message.setFrom(new InternetAddress("zombie@zombies.com"));
            //Address address[] = new InternetAddress[1];
            //address[0] = new InternetAddress("agoyen@hotmail.com", "Alex");
            //message.setReplyTo(address);
            
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(_sMailTo2));
            message.setSubject(_sMailSubject);
            
            BodyPart messageBodyPart = new MimeBodyPart();
            
            // Fill the message
            messageBodyPart.setText(_sMailMessage2);
          
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            
            //String filename = "image.jpg";
            
            //part two
            messageBodyPart = new MimeBodyPart();
            DataSource source = new FileDataSource(_sMailAttachment);
            //messageBodyPart.setDataHandler(new DataHandler(source));
            
//            String delimiters = "/\\";
//            StringTokenizer st = new StringTokenizer(_sMailAttachment, delimiters, true);
//            String filename = "";
//            Integer nTokens = st.countTokens();
//
//            while (st.hasMoreTokens()) {
//                String w = "";
//                w = st.nextToken();
//                if (w.length() > 1) {
//                    log(" string name: '" + w + "'");
//                    filename = w;
//                }
//            }
                
            messageBodyPart.setFileName(_sFileName);
            
            multipart.addBodyPart(messageBodyPart);
            
            message.setContent(multipart);
            
            log("connecting",2);
            //Transport transport = session.getTransport("smtp");
            //transport.connect(host, port, username, password);
            log("sending",2);
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            Transport.send(message);
            log("done",2);
            
        } catch (MessagingException e) {
            log("-->MessagingException",0);            
            e.printStackTrace();
            log(e.getMessage(),0);
            
            StringWriter sWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(sWriter));
            log(sWriter.getBuffer().toString(), 2) ;
            
//            StackTraceElement[] stackTrace = e.getStackTrace();
//            for (StackTraceElement element : stackTrace)  {  
//              final String exceptionMsg =  
//               "Exception thrown from " + element.getMethodName()  
//                + " in class " + element.getClassName() + " [on line number "  
//                + element.getLineNumber() + " of file " + element.getFileName() + "]";  
//              log(exceptionMsg);  
//            }
            
        } catch (Exception e) {
            log("-->Exception",0);
            e.printStackTrace();
            log(e.getMessage(),0);
        }
    }

    boolean printHeadersPost(File targ, PrintStream ps) throws IOException {
        boolean ret = false;
        int rCode = 0;

        rCode = HTTP_OK;
        ps.print("HTTP/1.0 " + HTTP_OK+" OK");
        ps.write(EOL);
        ret = true;

        log("From " +s.getInetAddress().getHostAddress()+": POST " +
        "-->"+rCode, 1);
        
        ps.print("Server: Simple java");
        ps.write(EOL);
        ps.print("Date: " + (new Date()));
        ps.write(EOL);
        ps.print("Content-length: " + targ.length());
        ps.write(EOL);

        return ret;

    }

    boolean printHeadersRedirect(File targ, PrintStream ps, String _uri, boolean bWindows,boolean bLinux, String sRemote, boolean bWindowsServer) throws IOException {
        int rCode = 0;
        rCode = HTTP_NO_CONTENT;
        ps.print("HTTP/1.1 " + rCode + "No Content");
        ps.write(EOL);

        log("From " +s.getInetAddress().getHostAddress()+": GET " +
        targ.getAbsolutePath()+"-->"+rCode, 1);
        return openFile(_uri, bWindows, sRemote, bLinux);
    }
    
    boolean openFile(String _uri, boolean bWindows, String sRemote, boolean bLinux) {
        try {
                    
        p("old string = '" + _uri + "'");
        
        String uri2 = URLDecoder.decode(_uri, "UTF-8");

        //_uri = _uri.replaceAll("%3A", ":");
        //_uri = _uri.replaceAll("%2F", "/");
        //_uri = _uri.replaceAll("%2E", ".");
        //_uri = _uri.replaceAll("%5C", "\\");
        //_uri = _uri.replaceAll("+", " ");

        p("bWindows = '" + bWindows + "'");
        p("bWindowsServer = '" + bWindowsServer + "'");
        p("sRemote = '" + sRemote + "'");
        boolean bRemote = false;
        if (sRemote.equals("1")) {
            bRemote = true;
        }
      
        p("new string = '" + uri2 + "'");
        if (uri2.substring(0,1).equalsIgnoreCase("/") && (bWindows || bRemote)) {
            uri2 = uri2.substring(1, uri2.length());        
        }
                      
        p("new string2 = '" + uri2 + "'");
        if (uri2.substring (uri2.length()-1, uri2.length()) .equals("/")) {
            String uri3 = uri2.substring(0,uri2.length()-1);
            uri2 = uri3;
        }
        p("new string3 = '" + uri2 + "'");
        
        if (bRemote) {
            if (bWindowsServer) {
                //windows serve
                uri2 = uri2.replace("/", "\\");
            } else {
                //mac/linux server
                if (!uri2.startsWith("/")) {
                    uri2 = "/" + uri2;
                }
            }
            //bWindows = true;
        }
        p("new string4 = '" + uri2 + "'");
        
        //String runstring = "cmd /C start " + uri2;
        //boolean bFile = false;
        
        if ((bWindows && !bRemote) || (bRemote && bWindowsServer) ) {
            
            p("windows case (file)");

            String[] runstring;
            
            
            if ( uri2.contains(".")) {
                p("windows case (file) [1]");
                //it's a file
                //runstring = "cmd /C " + "\"" + uri2 + "\"";
                //runstring = new String[]{"explorer",uri2};
                //runstring = new String[]{"cmd", "/C", uri2};
                //runstring = new String[]{uri2};
                //runstring = new String[]{"\"",uri2,"\""};

                if(uri2.startsWith("\\") && uri2.endsWith("\\")){
                    uri2 =uri2.substring(1,uri2.length()-1);
                }
                
                uri2 = "\"" + uri2 + "\"";
                
                p("windows case (file) [2]: <" + uri2 + ">");

                log("desktop open file a = <" + uri2 + ">", 2);
                uri2 = uri2.replace("/", "\\");
                log("desktop open file b = <" + uri2 + ">", 2);

                //File f = new File(uri2);
                //Desktop.getDesktop().open(f);
                
                runstring = new String[]{"explorer",uri2};
                
                Runtime.getRuntime().exec(runstring);

            } else {
                //it's a directory
                //runstring = "cmd /C " + "\"" + "start " + uri2 + "\"";    
                //runstring = new String[]{"explorer", "\"" + uri2 + "\""};

                //uri2 = "\"" + uri2 + "\"";
                
                p("windows case (dir)");
                
                log("desktop open dir a = <" + uri2 + ">", 2);
                uri2 = uri2.replace("/", "\\");
                log("desktop open dir b = <" + uri2 + ">", 2);
                                                        
                String uri3 = "";
                if (sFileNameFolder.length() > 0) {
                    
                    String[] tokens = uri2.split("\\s");
                    
                    runstring = new String[tokens.length + 3];
                    runstring[0] = "explorer";
                    runstring[1] = "/select,";
                    int i = 2;                    
                    for (String token: tokens) {
                        runstring[i] = token;
                        i++;
                    }
                    runstring[i] = "\\" + sFileNameFolder;
                                                           
                    //uri2 = "/select," + uri2 + "\\" + sFileNameFolder;
                                        
                } else {
                    uri2 = "\"" + uri2 + "\"";
                    runstring = new String[]{"explorer",uri2};
                }
                //log("desktop open dir c = <" + uri2 + ">");

                for (int i=0;i<runstring.length;i++) {
                    p("runstring[" + i + "] = " + runstring[i]);
                }

                //runstring = new String[]{"explorer",uri2};
                //runstring = new String[]{ "explorer" , "c:\\users\\agf\\test\\space" , "balls\\"};                
                
                log("open directory = <" + uri2 + ">", 2);
                Runtime.getRuntime().exec(runstring);

            }
                        
            //File file = new File(uri2);
            //Desktop.getDesktop().open(file);
            //Desktop.getDesktop().open(new File (uri2));
        } else {
            
            
            p("--- mac/linux case");
            
            //mac case
            String runstringm;
            //String[] runstring;
            //macintosh case
            //runstring = new String[]{"open", uri2};
            //Desktop.getDesktop().open(new File (uri2));
            
            if (!uri2.startsWith("/")) {
                uri2 = "/" + uri2;
            }
            
             
            runstringm = "open " + uri2;
            
            //runstring = new String[]{"open","\"" + uri2 + "\""};

            String[] runstring = {"sudo", 
                                    "open",
                                    uri2 };
            

            log("runstring = '" + runstring + "'", 2);
            //Runtime.getRuntime().exec(runstring);
            
            
            List<String> commands = new ArrayList<String>();
            //commands.add("sudo");
            commands.add(bLinux?(new File(uri2).isDirectory() ?"nautilus": "eog"):"open");//eog(fotos) y nautilus(carpetas) testeado para Ubuntu
            commands.add(uri2);
            
            System.out.println(commands);

            //Run macro on target
            ProcessBuilder pb = new ProcessBuilder(commands);
            //pb.directory(new File("/home/narek"));
            //pb.redirectErrorStream(true);
            Process process = pb.start();
        }
        
        return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    boolean printHeaders(File targ, PrintStream ps, boolean bFull, String sFileRange, boolean _vaultmode, String sPathDec, 
            boolean _sendcookie, String _boxuser, String _sGetFileExt, boolean failMobile, String passwordkey ,String iv, 
            String _uuid, String cluster, boolean aesencrypt, int aessize, String sMD5, String _sFileName) throws IOException {
        boolean ret = false;
        int rCode = 0;
        if(failMobile){
            //PARTIAL MODE
            rCode = HTTP_UNAUTHORIZED;
            ps.print("HTTP/1.1 " + HTTP_UNAUTHORIZED+" Partial Content");
            ps.write(EOL);
            ret = true;  
        }else{
            if (!targ.exists()) {
                rCode = HTTP_NOT_FOUND;
                ps.print("HTTP/1.1 " + HTTP_NOT_FOUND + " not found");
                ps.write(EOL);
                ret = false;
            }  else {
                if (bFull) {
                    rCode = HTTP_OK;
                    ps.print("HTTP/1.1 " + HTTP_OK +" OK");
                    ps.write(EOL);
                    ret = true;                
                } else {
                    //PARTIAL MODE
                    rCode = HTTP_PARTIAL;
                    ps.print("HTTP/1.1 " + HTTP_PARTIAL+" Partial Content");
                    ps.write(EOL);
                    ret = true;                                
                }
            }
        }
        log("From " +s.getInetAddress().getHostAddress()+": GET " +
            targ.getAbsolutePath()+"-->"+rCode, 1);
        ps.print("Server: Simple java");
        ps.write(EOL);
        ps.print("Date: " + (new Date()));
        ps.write(EOL);
        
               
        p("_sendcookie = " + _sendcookie);

        if (_sendcookie) {
            
            if(_uuid == null){
                UUID mUUID = UUID.randomUUID();
                _uuid = mUUID.toString();
            }
            
            //Store UUID and username in hashmap
            p("user = " + _boxuser);
            UserSession us = new UserSession(_boxuser, _uuid, passwordkey, iv, aesencrypt, aessize);
            if(!cluster.isEmpty())
                us.setRemoteCluster(cluster);
            uuidmap.put(_uuid, us);

            String sCookieString = "Set-Cookie: uuid=" + _uuid + "; Expires=Wed, 01-Sep-2029 20:18:14 GMT";
            p("Authenticated. Sending cookie. '" + sCookieString + "'");
            ps.print(sCookieString);
            ps.write(EOL);            
        }

        if (fixName(targ.getName())) {
            
            String sFileName = targ.getName();
            
            ps.print("Content-Disposition: filename=" + sFileName);
            ps.write(EOL);            
        } 
        
        if (_vaultmode) {
            
            p("VAULT MODE");

            p("sPathDec: " + sPathDec);
            byte[] s2 = Base64.decode(sPathDec.toCharArray());
            String sPathDec2 = new String(s2);                        
            p("sPathDec2: " + sPathDec2);
            String sPathDec3 = sPathDec2.substring(0, sPathDec2.length()-1);
            p("sPathDec3: " + sPathDec3);

            String sFileName = "";
            String delimiters4 = "/";
            StringTokenizer st4 = new StringTokenizer(sPathDec3,delimiters4, true);
            while (st4.hasMoreTokens()) {
                String w = st4.nextToken();
                if (w.length() > 1) {
                    sFileName = w;
                }
            }

            p("VAULT MODE filename:" + sFileName);
            
            if (sFileName.contains(".zip")) {
                //zip files can't be handled by the browser natively so change the name of the file.
                ps.print("Content-Disposition: attachment; filename=" + sFileName);
                ps.write(EOL);                            
            }
            
        }
        
        if (bRedirectBulker) {
            ps.print("Refresh: 0; url=" + sRedirectBulkerURL);
            ps.write(EOL);
        }
        if (ret) {
            if (!targ.isDirectory()) {
                ps.print("Last Modified: " + (new
                              Date(targ.lastModified())));
                ps.write(EOL);
                String name2 = targ.getName().toLowerCase();
                String name = name2;
                if (name2.endsWith(".tmp") && name2.contains("_")) {
                    name = name2.substring(0,name2.lastIndexOf("_"));                                    
                }
                String ct = null;
                p("NAME: '" + name + "'");
                p("GETFILEXT = '" + _sGetFileExt + "'");
                if (name.equals("getfile.fn")) {
                    
                    
                    String sFileName = "";
                    if (!cluster.isEmpty()) {
                        System.out.println("REMOTE Filename:" + _sFileName);
                        sFileName = _sFileName;
                    } else {
                        sFileName = wf.get_row_attribute(keyspace, "Standard1", sMD5, "name", dbmode);                        
                        System.out.println("LOCAL Filename:" + sFileName);
                    }

                    if (_sGetFileExt.length() == 0) {
                        _sGetFileExt = sFileName.substring(sFileName.lastIndexOf("."));
                        System.out.println("Fixed ext = " + _sGetFileExt);
                    }
                    ct = (String) map.get(_sGetFileExt.toLowerCase());    

                    if (sFileName.length() > 0) {
                        p("Content Disposition for Getfile:  filename='" + sFileName + "'");
                        ps.print("Content-Disposition: filename=" + sFileName);
                        ps.write(EOL);                              
                    }
             
                } else {
                    if (name.equals("getts.fn")) {
                        ct = "video/MP2T";
                    } else {
                        int ind = name.lastIndexOf('.');
                        if (ind > 0) {
                            ct = (String) map.get(name.substring(ind));
                        }                   
                    }                    
                }
                if (ct == null) {
                    ct = "unknown/unknown";
                } 
                p("**** CONTENT TYPE: '" + ct + "'");

                ps.print("Content-type: " + ct);
                ps.write(EOL);
                ps.print("Content-length: "+targ.length());
                ps.write(EOL);
                if (!bFull) {
                    ps.print("Content-Range: bytes "+ sFileRange + "/" + targ.length());
                    ps.write(EOL);
                }
                
            } else {
                ps.print("Content-type: text/html");
                ps.write(EOL);
            }
            
            p("******************************");
            p("FIXING the access-control");
            p("******************************");
            ps.print("Access-Control-Allow-Origin: https://web.alterante.com");
            ps.write(EOL);
            ps.print("Access-Control-Allow-Methods: POST, GET, PUT, OPTIONS, DELETE");
            ps.write(EOL);
            ps.print("Access-Control-Allow-Headers: Cache-Control, X-Requested-With, Content-Type");
            ps.write(EOL);
             
        } else {
            //404 case
            //p("404 case");
            ps.print("Content-type: text/html");
            ps.write(EOL);
            String sNotFound = "Not Found\n\n" + "The requested resource was not found\n";
            ps.print("Content-length: " + sNotFound.length());
            ps.write(EOL);
        }
        return ret;
    }
    
  
    void send404(File targ, PrintStream ps) throws IOException {
        //ps.write(EOL);
        ps.write(EOL);
        String sNotFound = "Not Found\n\n" + "The requested resource was not found\n";
        ps.println(sNotFound);
        }
    
    void send404_1(File targ, PrintStream ps) throws IOException {
        //ps.write(EOL);
        //ps.write(EOL);
        p("sending 404");
        String sNotFound = "Not Found\n\n" + "The requested resource was not found\n";
        ps.println(sNotFound);
        ps.write(EOL);
    }

    String processPost(PrintStream ps, int nread) throws IOException {
        p("Processing Post");
        p("Size of buffer: " + nread);
       // p(buf.toString());
        
        int start = 0;
        int end = 0;
        int i = 0;

        Boolean foundBody = false;
        Boolean foundBridge = false;
        byte buf2[] = new byte[0];

        String sFileName = "noname.txt";
        String sQueryString = "";
        
        while (!foundBody) {
            if ((buf[i] == (byte)'\r') || (buf[i+1] == (byte)'\n')) {
                end = i+2;
                //p("Start = '" + start + "' End:" + end);

                buf2 = new byte[end-start];
                System.arraycopy(buf, start, buf2, 0, end-start);
                //buf2[end-start] = 0;
                String sString = new String(buf2);
                p("&&&&&&&& String = '" + sString + "' len:" + sString.length());
                if (sString.contains("POST /clusters")) foundBridge = true;
                if (sString.contains("HTTP/1.1")) {
                    int nPos1 = sString.indexOf("POST");
                    int nPos2 = sString.indexOf("HTTP/1.1");
                    sFileName = sString.substring(nPos1+5, nPos2);
                }
                if (sString.equals("\r\n")) {
                    foundBody = true;
                } else {
                    start = end;
                    i = end;
                }
            } else {
                i++;
            }
        }
        //end+=2;
        
        p("**********-------> .    Start = '" + start + "' End:" + end  + " i = " + i);
        
        i = end;
        
        foundBody = false;
        while (!foundBody && !foundBridge) {
            if ((buf[i] == (byte)'\r') || (buf[i+1] == (byte)'\n')) {
                end = i+2;
                //p("Start = '" + start + "' End:" + end);

                buf2 = new byte[end-start];
                System.arraycopy(buf, start, buf2, 0, end-start);
                //buf2[end-start] = 0;
                String sString = new String(buf2);
                p("&&&&&&&& String = '" + sString + "' len:" + sString.length());
              
                if (sString.contains("HTTP/1.1")) {
                    int nPos1 = sString.indexOf("POST");
                    int nPos2 = sString.indexOf("HTTP/1.1");
                    sFileName = sString.substring(nPos1+5, nPos2);
                }
                if (sString.equals("\r\n")) {
                    foundBody = true;
                } else {
                    start = end;
                    i = end;
                }
            } else {
                i++;
            }
        }
  
        p("**********-------> .    Start = '" + start + "' End:" + end);

        p("sFilename POST = '" +  sFileName + "'");
//        if (sFileName.substring(0, 1).equals("/")) {
//            p("removing slash...");
//            String sFileName2 = sFileName.substring(1, sFileName.length());
//            sFileName = sFileName2;
//            p("sFilename = " +  sFileName );
//        }
        
        String sFileNew = "";
        if (!sFileName.contains("rpc.php")) {
            //String sFileNew = "payload.txt";
            //FileOutputStream outFile = new FileOutputStream(sFileNew);
            String w2 = URLDecoder.decode(sFileName, "UTF-8");
            if (sFileName.trim().endsWith(".c")) {
                sFileNew = appendage + "../scrubber/config" + File.separator + sFileName.substring(1,sFileName.length()).trim();                                        
            } else {
                if (w2.trim().endsWith(".p")) {
                    sFileNew = incoming.getPath() + File.separator + w2.substring(1, w2.length()).trim();
                } else {
                    sFileNew = incoming.getPath() + File.separator + "upload." + w2.substring(1, w2.length()).trim() + ".b";
                }
            }
        } else {
            sFileNew = "payload.txt";
        }
       
        p("sFileNew = '" +  sFileNew + "'");
        if (foundBridge) {
            sFileNew = "bridge.clusterdummyfile";
        }
        if(sFileName.endsWith(".p")){

            String data = new String(buf, end, nread - end, "UTF-8");
            int iInitialIndex=data.indexOf("-----WebKitFormBoundary");
            int iContentType=data.substring(iInitialIndex).indexOf("Content-Type");
            int iContentTypeEnd=data.substring(iContentType).indexOf("\r\n\r\n");
            int eIndex=data.substring(iContentType,iContentTypeEnd).indexOf("\r\n------WebKitFormBoundary");

            byte[] fileContent = data.substring(iContentTypeEnd,eIndex-("\r\n------WebKitFormBoundary".length())).getBytes("UTF-8");

            FileOutputStream outFile = new FileOutputStream(sFileNew);
            outFile.write(fileContent);
            outFile.close();
        }else {
            FileOutputStream outFile = new FileOutputStream(sFileNew);
            outFile.write(buf, end, nread - end);
            outFile.close();
        }
        buf2 = new byte[BUF_SIZE< nread-end?BUF_SIZE: nread-end];
        System.arraycopy(buf, end, buf2, 0, BUF_SIZE< nread-end?BUF_SIZE: nread-end);
        String sString2 = new String(buf2);
        //p("sString2 = '" + sString2 + "'");
        if (sString2.contains("queryString")) {
            int nPos = sString2.indexOf("queryString");
            sQueryString = sString2.substring(nPos + 12, sString2.length());
            p("sQueryString = '" + sQueryString + "'");
        }

        return sQueryString;
    }

    boolean isUUIDValid(String sUUID) {
        //p("Checking UUID: " + sUUID);
        
        UserSession t = uuidmap.get(sUUID);
        if (t == null) {
            return false;
        } else {
            //p("UUID is valid.");
            return true;
        }
    }
    void sendFilePost(File targ, PrintStream ps) throws IOException {
        InputStream is = null;
        ps.write(EOL);
        if (targ.isDirectory()) {
            listDirectory(targ, ps);
            return;
        } else {
            p("sending: " + targ.getAbsolutePath());
            is = new FileInputStream(targ.getAbsolutePath());
        }

        try {
            int n;
            while ((n = is.read(buf)) > 0) {
                ps.write(buf, 0, n);
            }
        } finally {
            is.close();
        }
    }

    void sendFile(File targ, PrintStream ps) throws IOException {
        InputStream is = null;
        ps.write(EOL);
        if (targ.isDirectory()) {
            listDirectory(targ, ps);
            return;
        } else {
            //log("sendFile(): '" + targ.getAbsolutePath() + "'");
            is = new FileInputStream(targ.getAbsolutePath());
        }

        try {
            //p("BEGIN SENDFILE");
            int n;
            int chunk = 0;           
            int nbytestotal = 0;
            int nbytes = 0;
            while ((n = is.read(buf)) > 0) {
                nbytestotal = nbytestotal + n;
                nbytes = nbytes + n;
                chunk++;
                ps.write(buf, 0, n);
                if (nbytes > 1024*100) {                   
                    //p("chunk#" + chunk + " bytes so far:" +nbytestotal);
                    nbytes = 0;
                    Thread.sleep(1);
                }
            }
            //p("DONE SENDFILE. total:" + nbytestotal);
            ps.close();
        } catch (Exception e) {
            log("   *** WARNING *** Exception during sendfile: " + e.getMessage(), 0);
        } finally {
            is.close();
        }
    }

    void sendFilePartial(File targ, PrintStream ps, int nStart, int nEnd) throws IOException {
        InputStream is = null;
        ps.write(EOL);
        if (targ.isDirectory()) {
            listDirectory(targ, ps);
            return;
        } else {
            p("requested: " + targ.getAbsolutePath());
            is = new FileInputStream(targ.getAbsolutePath());
        }

        try {
                    byte[] data;
                    int nLen = nEnd - nStart + 1;
                    
                    data = new byte[nLen];
                    
                    int offset = nStart;
                    int numRead = 0;

                    while (offset < data.length && (numRead = is.read(data, offset, data.length-offset)) >= 0) {
                        offset += numRead;
                    }

                    p("   done reading  offset: " + offset);
                    p("   done reading  numRead: " + numRead);
                    p("   done reading  nLen: " + nLen);

                    ps.write(data);
                    //ps.close();

                    p("   done with write: " + data.length);
        } catch (Exception e) {
        p("   *** WARNING *** Exception during partial sendfile: " + e.getMessage());

        } finally {
            is.close();
        }
    }

    /* mapping of file extensions to content-types */
    static java.util.Hashtable map = new java.util.Hashtable();
    
    static {
        fillMap();
    }
    static void setSuffix(String k, String v) {
        map.put(k, v);
    }

    static void fillMap() {
        setSuffix("", "content/unknown");
        setSuffix(".uu", "application/octet-stream");
        setSuffix(".exe", "application/octet-stream");
        setSuffix(".ps", "application/postscript");
        setSuffix(".zip", "application/zip");
        setSuffix(".sh", "application/x-shar");
        setSuffix(".tar", "application/x-tar");
        setSuffix(".snd", "audio/basic");
        setSuffix(".au", "audio/basic");
        setSuffix(".wav", "audio/x-wav");
        setSuffix(".gif", "image/gif");
        setSuffix(".jpg", "image/jpeg");
        setSuffix(".jpeg", "image/jpeg");
        setSuffix(".png", "image/png");
        setSuffix(".htm", "text/html");
        setSuffix(".js", "text/javascript");
        setSuffix(".html", "text/html");
        setSuffix(".text", "text/plain");
        setSuffix(".c", "text/plain");
        setSuffix(".cc", "text/plain");
        setSuffix(".c++", "text/plain");
        setSuffix(".h", "text/plain");
        setSuffix(".pl", "text/plain");
        setSuffix(".txt", "text/plain");
        setSuffix(".java", "text/plain");
        setSuffix(".mp3", "audio/mpeg");
        setSuffix(".m4a", "audio/mpeg");
        setSuffix(".php", "text/html");
        setSuffix(".ogv", "video/ogg");
        setSuffix(".mp4", "video/mp4");
        setSuffix(".mov", "video/quicktime");        
        setSuffix(".webm", "video/webm");
        setSuffix(".m3u8", "application/x-mpegURL");
        setSuffix(".ts", "video/MP2T");
        setSuffix(".css", "text/css");
        setSuffix(".doc", "application/msword");
        setSuffix(".xls", "application/vnd.ms-excel");
        setSuffix(".ppt", "application/vnd.ms-powerpoint");      
        setSuffix(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        setSuffix(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        setSuffix(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        setSuffix(".pdf", "application/pdf");

    }

    void listDirectory(File dir, PrintStream ps) throws IOException {
        ps.println("<TITLE>Directory listing</TITLE><P>\n");
        ps.println("<A HREF=\"..\">Parent Directory</A><BR>\n");
        String[] list = dir.list();
        for (int i = 0; list != null && i < list.length; i++) {
            File f = new File(dir, list[i]);
            if (f.isDirectory()) {
                ps.println("<A HREF=\""+list[i]+"/\">"+list[i]+"/</A><BR>");
            } else {
                ps.println("<A HREF=\""+list[i]+"\">"+list[i]+"</A><BR");
            }
        }
        ps.println("<P><HR><BR><I>" + (new Date()) + "</I>");
    }
    
    static void get_vault_file(int _localport, String filename) throws Exception {
            
        Socket skt = null;
        ServerSocket serverSocket = null;
        
        try {
            p("Listening on port:" + _localport);
            
            InetAddress localaddr = getLocalAddress();
            p("Local address:" + localaddr.toString());
            
            boolean bTry = true;
       
            int nRetry = 0;
            while (bTry && nRetry < 5) {
                try {
                    serverSocket = new ServerSocket(_localport);
                    bTry = false;
                } catch (Exception e) {
                    e.printStackTrace();
                    p("Address in Use. Retry #" + nRetry);
                    Thread.sleep(3000);
                    nRetry++;
                }                
            }
            //DatagramSocket serverSocket = new DatagramSocket(_localport);
            
            if (serverSocket != null) {
                p("serverSocket.getAddress = " + serverSocket.getInetAddress());
                //p("serverSocket.getLocalAddress = " + serverSocket.getLocalAddress());

                byte[] receiveData = new byte[1024*1024];
                byte[] sendData = new byte[1024*1024];

                p("Waiting for client...");
                skt = serverSocket.accept();
                //DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                p("Client call accepted.");
                p("Enter request> ");

                //BufferedReader inFromUser =  new BufferedReader(new InputStreamReader(System.in));
                //String sentence = inFromUser.readLine();            
                String sentence = filename;
                sendData = sentence.getBytes();

                skt.getOutputStream().write(sendData);

                //String sentence = new String( receivePacket.getData());
                //skt.getInputStream().read(receiveData);
                //sentence = new String(receiveData);

                File f = new File("vault/" + sentence);
                storeFile(f, skt.getInputStream());

                p("RECEIVED '" + sentence + "' stored at: " + f.getAbsolutePath());
                receiveData = null;
                sendData = null;                
                //DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
                //serverSocket.send(sendPacket);                    
            }
                        
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (skt != null) {
                skt.close();
                p("SOCKET SKT CLOSED.");
            }
            if (serverSocket != null) {
                serverSocket.close();
                p("SOCKET SERVER CLOSED.");
            }
        }
            
    }
    
    static void storeFile(File targ, InputStream is) throws IOException {
        
        byte[] buf;
        final int BUF_SIZE = 8 * 1024 * 1024;
        
        
        buf = new byte[BUF_SIZE];

        OutputStream os = null;
        p("storeFile(): '" + targ.getAbsolutePath() + "'");
        os = new FileOutputStream(targ.getAbsolutePath());

        try {
            int n;
            
            while ((n = is.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            os.close();
            is.close();
            buf = null;
            
        } catch (Exception e) {
            p("   *** WARNING *** Exception during sendfile: " + e.getMessage());
        } finally {
            is.close();
        }
    }
    
    public String write_config(String sRequest) {

        String res = "";

        try {

            String delimiters = "?&";
            StringTokenizer st = new StringTokenizer(sRequest, delimiters, true);
            String w = "";

            w = st.nextToken();
            w = st.nextToken();
            String sFile = st.nextToken();    

            res += "config is '" + sFile + "'" + "<br>";
            w = st.nextToken();
            String sInstance = st.nextToken();

            res += "instance is '" + sInstance + "'" + "<br>";
            
            String sPathScrub = "";
            String sPathRT = "";
            
            if (sInstance.equals("inst=1")) {
                sPathScrub = "../";
                sPathRT = "./";
            }
            if (sInstance.equals("inst=2")) {
                sPathScrub = "../../client2/";
                sPathRT = "../../client2/rtserver/";
            }
            if (sInstance.equals("inst=3")) {
                sPathScrub = "../../client3/";
                sPathRT = "../../client3/rtserver/";
            }
            if (sInstance.equals("inst=4")) {
                sPathScrub = "../../client4/";
                sPathRT = "../../client4/rtserver/";
            }
            if (sInstance.equals("inst=5")) {
                sPathScrub = "../../projects/";
                sPathRT = "../../projects/rtserver/";
            }

            
            String sFileName = "";

            if (sFile.contains("rtbackup")) {            
                sFileName = sPathScrub + "scrubber/config/www-rtbackup.properties";
            }

            if (sFile.contains("rtserver")) {
                sFileName = sPathRT + "config/www-server.properties";                       
            }

            if (sFile.contains("mailer")) {
                sFileName = sPathRT + "config/www-mailer.properties";                       
            }

            res += "save config save for '" + sFileName + "'" + "<br>";
            
            FileWriter outFile = new FileWriter(sFileName, false);
            String eol = System.getProperty("line.separator");  

            while (st.hasMoreTokens()) {
                w = st.nextToken();            
                if (w.length() > 1) {
                    String w2 = URLDecoder.decode(w, "UTF-8");
                    res += w2 + "<br>";                
                    outFile.write(w2 + eol);
                }
            }
            
            outFile.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            res += "ERROR";
        }
        return res;
        
    }
   
    public String read_config(String sFileName, String sConfig, String sInstance) {

        try {
            Properties props = new Properties();
            
            props.clear();
            String res = "";
            
           
            p("Reading config: " + sFileName);
            File f = new File(sFileName);
            if (f.exists()) {
                
                InputStream is =new BufferedInputStream(new
                               FileInputStream(f));
                props.load(is);
                is.close();

                res += "<form action=" + "\"setconfig.htm" + "\"" + " method=get>";
                res += "<input type=hidden name=config value=" + sConfig + ">";
                res += "<input type=hidden name=inst value=" + sInstance + ">";
                
                res += "<table>";
                for(String key : props.stringPropertyNames()) {
                     String value = props.getProperty(key);
                       res += "<tr>" + 
                               "<td>" + key + "</td>" + 
                               "<td>" + "<input type=text name=" + key + " value=" + value + ">" + "</td>" +
                              "</tr>";
                }                       
                
                res += "</table><br>";
                res += "<INPUT TYPE=submit value=Save>";

                res += "</form>";
                props.clear();

            }
            
            return res;
            
        } catch (Exception e) {
            return "getconfig(" + sFileName + "+ = ERROR<br>";
        }
                
        
    }
    
    public int load_setup_props() {

        try {
            Properties props = new Properties();
            
            File f = new File
                    ("config/" + "www-setup.properties");
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new
                               FileInputStream(f));
                props.load(is);
                is.close();
                return 1;
            } else {
                return 0;
            }
            
        } catch (Exception e) {
            return -1;
        }
        
    } 
    
    public static InetAddress getLocalAddress() throws SocketException {
        InetAddress addr_res = null;

        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();

                System.out.println ("addr.getHostAddress() = " + addr.getHostAddress());
                System.out.println ("addr.getHostName() = " + addr.getHostName());
                System.out.println ("addr.isAnyLocalAddress() = " + addr.isAnyLocalAddress());
                System.out.println ("addr.isLinkLocalAddress() = " + addr.isLinkLocalAddress());
                System.out.println ("addr.isLoopbackAddress() = " + addr.isLoopbackAddress());
                System.out.println ("addr.isMulticastAddress() = " + addr.isMulticastAddress());
                System.out.println ("addr.isSiteLocalAddress() = " + addr.isSiteLocalAddress());

                if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                    addr_res = addr;
                }
            }
        }
        if (addr_res != null) {
            return addr_res;
        } else {
            return null;
        }
    }
    
    int StoreFileExtensions(String _fname) {
        
        
        try {
            
            FileInputStream fileExtensions = new FileInputStream(appendage + "../scrubber/config/FileExtensions_All.txt");
            
            String sFileName = appendage + "../scrubber/config/FileExtensions.txt";
            
            FileWriter fw = new FileWriter(sFileName, false);
            BufferedWriter out = new BufferedWriter(fw);                       

            Scanner objScanner = new Scanner(fileExtensions);
            
            while (objScanner.hasNext()) {
                
                String line = objScanner.nextLine();
                                
                String delimiters = ",";
                StringTokenizer st = new StringTokenizer(line.trim(), delimiters, true);               
                
                String key = st.nextToken();
                
                if (!key.equals("@")) {
                    String key2 = key.substring(1,key.length());

                    p("key = " + key2);
                    if (_fname.contains("scan_" + key2)) {
                        p("yes!!!");
                        
                        String sLine = line + System.getProperty("line.separator");                      
                        out.write(sLine);
                    }                                    
                }
            } 
            
            out.close();   

            
            return 0;
        } catch (Exception e) {
            return -1;
        }
       
    }
    
    
    
    String GetFileExtensions() {

        try {
            FileInputStream fileExtensions = new FileInputStream(appendage + "../scrubber/config/FileExtensions_All.txt");
            Scanner objScanner = new Scanner(fileExtensions);
            
            File f = new File (appendage + "../scrubber/config/FileExtensions.txt");    
            String sBuffer = loadFileStr(f);  
            
            String str = "";
            
            int i=0;
            str += "<tr>\n";
            while (objScanner.hasNext()) {                
                String line = objScanner.nextLine();
                
                
                String delimiters = ",";
                StringTokenizer st = new StringTokenizer(line.trim(), delimiters, true);
                
                
                String key = st.nextToken();
                String keyND = key.substring(1, key.length());
                st.nextToken();
                String value = st.nextToken();
                st.nextToken();   
                
                if (key.equals("@")) {
                    str += "</tr>\n";
                    str += "<tr><td><b>" + value + "</b></td></tr>\n";
                    str += "<tr>\n";
                    i = 0;
                } else {

                    String sChecked = "";
                    if (sBuffer.contains(key + ",")) {
                        sChecked = "checked";
                    }
                    str += "<td><label class=\"checkbox\"><input name=\"scan_" + keyND + "\" type=\"checkbox\" " + sChecked + ">" + key + " (" + value + ")</label></td>\n";
                    i++;
                    if (i % 3 == 0) {
                        i = 0;
                        str += "</tr>\n";
                        str += "<tr>\n";
                    }                                    
                }
                
            }
            str += "</tr>";

            return str;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }  
        
    String GetSyncDirectories() {
        try {
            Properties props = new Properties();
            
            File f = new File(appendage + "../scrubber/config/www-rtbackup.properties");
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();
                
                String newChar = "";
                String r = "";
                
                r = props.getProperty("syncpath");
                if (r != null ) {
                    p("syncpath value = " + r);    
                    
                    newChar += "<tr><input name=\"sncpath\" type=\"text\" value=\"" + r + "\"></tr><br>";
                }    
                return newChar;
            } else {
                p("Config file not found.");
                return "";                
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        
    }
    
    String GetSyncDirectories(String _node) {
        try {
            
            Properties props = new Properties();
            
            File f = new File(appendage + "../scrubber/config/www-rtbackup" + _node + ".properties");
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();
                if (_node.equals("")) {
                    _node = "1";
                }
                
                String newChar = "var mapSync"+_node+"=new Array();";
                String r = "";
                
                r = props.getProperty("syncpath");
                if (r != null && r.trim().length()>0 ) {
                    p("syncpath value = " + r);    
                    
                    if (_node.equals("")) {
                        _node = "1";
                    }
                    newChar += " mapSync"+_node+".push('"+r+"');";
                }  
                
                return newChar;
            } else {
                p("Config file not found.");
                return "";                
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        
    } 
    
    String GetBackupDirectories(String _node) {
        try {
            
            Properties props = new Properties();
            
            File f = new File(appendage + "../scrubber/config/www-rtbackup" + _node + ".properties");
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();

                if (_node.equals("")) {
                    _node = "1";
                }
                String newChar = "var mapBackup"+_node+"=new Array();";
                String r = "";
                
                r = props.getProperty("backuppath");
                if (r != null && r.trim().length()>0) {
                    p("backuppath value = " + r);    
                   
                    newChar += " mapBackup"+_node+".push('"+r+"');";
                }    
                return newChar;
            } else {
                p("Config file not found.");
                return "";                
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        
    }           
//                                POP3 Server (incoming) <input type="text" name="pop3host">
//                                Port <input type="text" name="pop3port" maxlength="5" size="5"><br>
//                                SMTP Server (outgoing) <input type="text" name="smtphost">
//                                Port <input type="text" name="smtpport"><br>                                
//                                Username: <input type="text" name="pop3user"><br>
//                                Password: <input type="password" name="pop3pw">
//                                Confirm Password: <input type="password" name="pop3pw2">
    
    String GetEmailInfo(String sBuffer) {
        try {
            
            Properties props = new Properties();
            
            String res = "";
            String r = "";
            
            File f = new File("config/www-mailer.properties");
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();
                
 
                r = props.getProperty("pop3user");
                if (r != null ) {
                    p("pop3user value = " + r);                        
                    res += "<tr>Username: <input name=\"pop3user\" type=\"text\" value=\"" + r + "\"></tr>";
                }    

                r = props.getProperty("pop3pw");
                String passdiv = "<br>Password: <input id=\"pw1\" name=\"pop3pass\" type=\"password\" value=\"" + r + "\">";

                passdiv += "&nbsp;<a id=\"linktodispw\" href=\"javascript:toggleDisplayPassword();\">Display</a>";

                if (r != null && !r.isEmpty()) {
                    res += "&nbsp;<a id=\"linktoshowpw\" href=\"javascript:showPassword();\">Change password</a>";
                    res += "<div id=\"passdiv\" style=\"display:none\">";
                    passdiv += "&nbsp;&nbsp;&nbsp;<a id=\"linktohidepw\"  href=\"javascript:hidePassword();\">Cancel</a>";
                    
                }else{
                    res += "<div id=\"passdiv\">";                         
                }
                res += passdiv;
                res += "</div>"; 


                res += "<br><a id=\"linktoshowadvanced\"  href=\"javascript:toggleAdvancedsettings();\">Show advanced settings</a>";

                res += "<div id=\"advancedsettings\" style=\"display:none\">";
                
                r = props.getProperty("pop3host");
                if (r != null ) {
                    p("pop3host value = " + r);                        
                    res += "POP3 Server (incoming)<input name=\"pop3host\" type=\"text\" value=\"" + r + "\">";
                }

                r = props.getProperty("pop3port");
                if (r != null ) {
                    p("pop3port value = " + r);                        
                    res += " Port: <input name=\"pop3prt\" class=\"span2\" type=\"text\" size=\"3\" value=\"" + r + "\"><br>";
                }  
               

                r = props.getProperty("smtphost");
                if (r != null ) {
                    p("smtmphost value = " + r);                        
                    res += "SMTP Server (outgoing)<input name=\"smtphost\" type=\"text\" value=\"" + r + "\">";
                }

                r = props.getProperty("smtpport");
                if (r != null ) {
                    p("smtpport value = " + r);                        
                    res += " Port: <input name=\"smtpprt\" class=\"span2\" type=\"text\" size=\"5\" value=\"" + r + "\"><br>";
                }    

                res += "</div>"; //advancedsetting
                sBuffer = sBuffer.replace("***REP***", res);

                r = props.getProperty("allowmail");
                if (r != null ) {
                    p("allowmail value = " + r);                        
                    if(r.equals("on")){
                        sBuffer = sBuffer.replace("***REPA***", "checked");
                    }
                }

                
                r = props.getProperty("sendmail");
                if (r != null ) {
                    p("sendmail value = " + r);
                    if(r.equals("on")){
                        sBuffer = sBuffer.replace("***REPB***", "checked");
                    }
                }
                
                
                r = props.getProperty("scanmail");
                if (r != null ) {
                    p("scanmail value = " + r);                        
                    if(r.equals("on")){
                        sBuffer = sBuffer.replace("***REPC***", "checked");
                    }
                }
                
                r = props.getProperty("notifymail");
                if (r != null ) {
                    p("notifymail value = " + r);                        
                    if(r.equals("on")){
                        sBuffer = sBuffer.replace("***REPD***", "checked");
                    }
                }
                
            }
            return sBuffer;
            
        }   catch (Exception e) {
            return "";
        }            
    }
    
        String GetEmailDomainsInfo(String sBuffer) {
        try {
            
            Properties props = new Properties();
            
            String res = "";
            String r = "";
            
            File dir = new File("../mailer/config/domains");
            File[] domainFiles = dir.listFiles();
            for (int i = 0; i < domainFiles.length; i++) {
                File f = domainFiles[i];
                if(i != 0){
                    res += " else ";
                }
                
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();
                

                
               r = props.getProperty("domains");
                if (r != null ) {
                    res += "if(";
                    String[] domainsArr = r.split(",");
                    for (int j = 0; j < domainsArr.length; j++) {
                        if (j != 0) {
                            res += "||";    
                        }                        
                       res += "$(this).val().indexOf('"+ domainsArr[j] + "') != -1";
                    }
                    res += ")";
                }
         
                res += "{";
           
                r = props.getProperty("pop3host");
                if (r != null ) {
                    res += " $('input[name=pop3host]').val('"+ r +"');";
                }
                
                r = props.getProperty("pop3port");
                if (r != null ) {
                    res += " $('input[name=pop3prt]').val('"+ r +"');";
                }
                r = props.getProperty("smtphost");
                if (r != null ) {
                    res += " $('input[name=smtphost]').val('"+ r +"');";
                }
                
                r = props.getProperty("smtpport");
                if (r != null ) {
                    res += " $('input[name=smtpprt]').val('"+ r +"');";
                }
                res += "}";
                
                
            }
            
            sBuffer = sBuffer.replace("***REPED***", res);
            
            return sBuffer;
            
        }   catch (Exception e) {
            return "";
        }            
    }
    
    String GetBlackList(String sNode) {
       try {
            File f = new File(appendage + "../scrubber/config/blacklist" + sNode + ".txt");
            if (!f.exists()){
                BufferedWriter writer=new BufferedWriter(new FileWriter(appendage + "../scrubber/config/blacklist" + sNode + ".txt"));
                writer.newLine();
                writer.close();
            }
            
            if (f.exists()) {
                BufferedReader is =new BufferedReader(new FileReader( f));
                
                if (sNode.equals("")) {
                        sNode = "1";
                }
                String newChar = "var mapBL"+sNode+"=new Array(); ";
                 
                String s = is.readLine();
                while(s!=null){
                      if(s.trim().length()>0){
                        // newChar += "<tr><input name=\"scandir" + sNode + ".1" + "\" type=\"text\" value=\"" + r + "\"></tr><br>";
                         newChar+="mapBL"+sNode+".push('"+URLEncoder.encode(s,"UTF-8").replaceAll("\\+", "%20")+"');"; 
                      }
                      s=is.readLine();
                }                  
                is.close();
               
                return newChar;
            } else {
                p("Config file not found.");
                return "";                
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    
    int AddInbox(String sNode) {       
        try {
            
            Properties props = new Properties();
            
            String sScanFile = GetConfig("scandir", appendage + "../scrubber/config/www-rtbackup" + sNode + ".properties");            
            if (sScanFile.length() > 0) {
                File f = new File(sScanFile);
                if (f.exists()) {
                    InputStream is =new BufferedInputStream(new FileInputStream(f));    
                    props.clear();
                    props.load(is);
                    
                    String r = props.getProperty("scandir");                
                    if (r != null){
                        String s = "../rtserver/inbox/";
                        File f2 = new File(s);
                        f2.mkdir();                       
                        String sPathw = f2.getCanonicalPath() + File.separator;                        
                        String sPath = sPathw;
                        if (!bWindowsServer) {                            
                            File volumes=new File("/Volumes/");
                            if(volumes.exists()){
                                for(File f3:volumes.listFiles()){
                                    if(f3.isDirectory()){
                                        sPath = f3.getPath() + sPathw;
                                        File f4 = new File(sPath);
                                        if (f4.exists()) {
                                            p ("path: '" + sPath + "' exists"); 
                                            break;
                                        } else {
                                            p ("path: " + sPath + "' NOT exists");                                             
                                        }
                                    }
                                }
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
    
    String GetScanDirectories(String sNode) {
               
        try {
            
            Properties props = new Properties();
            
            File f = new File(appendage + "../scrubber/config/www-rtbackup" + sNode + ".properties");
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();
                if (sNode.equals("")) {
                        sNode = "1";
                }
                String newChar = "var mapScan"+sNode+"=new Array(); ";
                String r = "";
                                
                r = props.getProperty("scandir"); //Path al archivo
                if(r==null || r.trim().length()==0 || !new File(r).exists()|| new File(r).isDirectory()){
                    //Este if es para compatibilidad con los config viejos
                    //Si no existe 
                    BufferedWriter writer=new BufferedWriter(new FileWriter(appendage + "../scrubber/config/scan"+sNode+".txt"));
                    writer.newLine();
                    writer.close();
                    UpdateConfig("scandir", appendage + "../scrubber/config/scan"+sNode+".txt",f.getAbsolutePath());

                    String s = "";
                    if ((r!= null) && (new File(r).isDirectory())) {
                        p("************** ADDING scandir directory");
                        s += URLEncoder.encode(r, "UTF-8").replaceAll("\\+", "%20");
                    } else {                        
                        p("************** SKIPPING scandir.2");
                    }
                    
                    r = props.getProperty("scandir.2"); //Path al archivo #2
                    if ((r!= null) && (new File(r).isDirectory()))  {
                        p("************** ADDING scandir.2 directory");
                        s += ";" + URLEncoder.encode(r, "UTF-8").replaceAll("\\+", "%20");
                    } else {                        
                        p("************** SKIPPING scandir.2");
                    }
                    r = props.getProperty("scandir.3"); //Path al archivo #3
                    if ((r!= null) && (new File(r).isDirectory())) {
                        p("************** ADDING scandir.3 directory");
                        s += ";" + URLEncoder.encode(r, "UTF-8").replaceAll("\\+", "%20");
                    } else {
                        p("************** SKIPPING scandir.3");
                    }

                    // substitute para que quede bien grabado                    
                    String t= s.replaceAll("%2F", "%5C");    
                    
                    UpdateConfig("scandir", t,appendage + "../scrubber/config/scan"+sNode+".txt" );
                    for(String x: t.split(";")){
                                // newChar += "<tr><input name=\"scandir" + sNode + ".1" + "\" type=\"text\" value=\"" + r + "\"></tr><br>";
                                 newChar+="mapScan"+sNode+".push('"+x+"');"; 
                    }
                    
                }else
                if (r != null && r.trim().length()>0) {
                    p("scandir value = " + r);    
                     
                    File fscan = new File(r);
                    if (fscan.exists()) {
                        InputStream isscan =new BufferedInputStream(new FileInputStream(fscan));
                        props.clear();
                        props.load(isscan);
                        isscan.close();
                        r = props.getProperty("scandir");
                        if (r != null && r.trim().length()>0){
                            for(String s: r.split(";")){
                                // newChar += "<tr><input name=\"scandir" + sNode + ".1" + "\" type=\"text\" value=\"" + r + "\"></tr><br>";
                                 newChar+="mapScan"+sNode+".push('"+s+"');"; 
                            }
                         }
                    }
                }                    

               
                return newChar;
            } else {
                p("Config file not found.");
                return "";                
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        
        
    }
    
    String loadFileStr(File f) {
          
        try {
            BufferedReader reader = new BufferedReader(new FileReader(f));
            StringBuilder result = new StringBuilder();

            char[]buf = new char[1024];
            int x = 0;
            while ((x = reader.read(buf)) != -1) {
                result.append(buf,0,x);
            }
            String sBuffer = result.toString();
            p("String length = " + sBuffer.length());
            
            reader.close();
            return sBuffer;
              
        } catch (FileNotFoundException e) {
            return "";
        } catch (IOException e) {
            return "";            
        }
}
    
    String GetConfig(String _name, String _config) {
        
        try {
            
            Properties props = new Properties();
            
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
    
    int UpdateConfig(String _name, String _value, String _config) {
        
        try {
            
            Properties props = new Properties();
            
            File f = new File(_config);
           
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
            } else {
                p("File not found. exiting...");
            }
            return 0;
   
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }    
        
    }
    
    String PrintStateInfo() {
        String res2 = "";
        String sState = "";
        int nres = load_setup_props();
        if (nres > 0) {
              String rs = GetConfig("state", "config/www-setup.properties"); //props.getProperty("state");
              if (rs != null) {
                  sState = rs;
              }
        }

        //res2 += "Setup state: " + sState;

        if (sState.equals("NEW")) {                                                                    
            String sLink = "<a href=\"setup.htm?spage=1\">here</a>";
            res2 += "Click " + sLink + " to begin the setup process.</p>";                                                              

        } 

        if (sState.equals("SCAN")) {
            res2 += "Scan in progress. Files will start appearing in your searches.</p>";                                                              
        } 

        if (sState.equals("CURATE")) {
            res2 += "Full scan has completed. You can organize your files by adding tags, and make the files public.</p>";                                                              
        } 

        if (sState.equals("INDEX")) {
            res2 += "Indexing in progress. Please try again in a few minutes. </p>";                                                              
        } 

        if (sState.equals("READY")) {
            res2 += "Index is complete. </p>";                                                              
        } 
        
        return res2;
    }
    
    //Any file not supported by the broswer natively will be downloaded by the browser. 
    //This routine determines whether we need to fix the filename, so the original filename is sent to the client.
    
    boolean fixName(String _filename) {
        if (_filename.contains(".zip") ||
                _filename.contains(".doc") ||
                _filename.contains(".xls") ||
                _filename.contains(".ppt") ||
                _filename.contains(".m4a")) {            
            return true;
        } else {
            return false;
        }
    }

    private String LoadAccounts(String filePath) {
        String r="";
        try{
            BufferedReader reader=new BufferedReader(new FileReader( filePath));
            String account="";
            while((account=reader.readLine())!=null){
                r+="<option value='"+account+"'>"+account+"</option>" ;
            }
            reader.close();
        
        }catch(Throwable th){
            th.printStackTrace();
        }
        
        return r;
    }
    
    private String LoadAccountsWithGroups(String sBuffer) {
        String r="";
        String g="";
        try{
            BufferedReader reader=new BufferedReader(new FileReader("../mailer/config/sendaccounts.txt"));
            String line="";
            while((line=reader.readLine())!=null){
                String[] lineArray = line.split("\\,");
                String account = lineArray[0];
                r+="<option value='"+account+"'>"+account+"</option>" ;

                g+="a['"+account+"'] = new Array();";
                for (int i = 1; i <= lineArray.length-1; i++) {
                    String group = lineArray[i];
                    g+="a['"+account+"'].push('"+group+"');";    
                }
                
            }
            reader.close();
        
        }catch(Exception ex){
            //th.printStackTrace();
            p("sendaccounts does not exist yet");
        }
        sBuffer = sBuffer.replace("***REPB1***", r);
        sBuffer = sBuffer.replace("***REPG***", g);
        return sBuffer;
    }
    
    
    
   private String LoadAccountsInArray() {
        String g="";
        try{
            BufferedReader reader=new BufferedReader(new FileReader("../mailer/config/sendaccounts.txt"));
            String line="";
            while((line=reader.readLine())!=null){
                String[] lineArray = line.split("\\,");
                String account = lineArray[0];
                g+="a.push('"+account +"');";    
            }
            reader.close();
        
        }catch(Throwable th){
            th.printStackTrace();
        }
        return g;
    }
    
    private String LoadGroupsWithAccounts() {
        String g="";
        try{
            
            Collection<String> groups = GetMailGroups();
            for (String groupname : groups) {
                g+="g['"+groupname+"'] = new Array();";
            }
            
            BufferedReader reader=new BufferedReader(new FileReader("../mailer/config/sendaccounts.txt"));
            String line="";
            while((line=reader.readLine())!=null){
                String[] lineArray = line.split("\\,");
                String account = lineArray[0];

                for (int i = 1; i <= lineArray.length-1; i++) {
                    String group = lineArray[i];
                    g+="g['"+group+"'].push('"+account+"');";    
                }
                
            }
            reader.close();
        
        }catch(Throwable th){
            th.printStackTrace();
        }
        return g;
    }

    private Collection<String> GetMailGroups() {
        ArrayList<String> result = new ArrayList();
        try{
            BufferedReader reader=new BufferedReader(new FileReader("../mailer/config/mailgroups.txt"));
            String groupname=reader.readLine();
            while(groupname!=null && !groupname.isEmpty()){
                result.add(groupname);
                groupname=reader.readLine();
            }
            reader.close();
        
        }catch(Throwable th){
            th.printStackTrace();
        }
        
        return result;
    }  
    
    
    
    private String LoadMailGroups() {
        String r="";
        Collection<String> groups = GetMailGroups();
        for (String groupname : groups) {
            r+="<label class='checkbox'><input type='checkbox' name='groupcheckbox' value='"+groupname+"'/>"+groupname+"</label>";
        }
        
        return r;
    }
    
    
    private String LoadSyncRules(String filePath, String disknumber) {
        
        String r="";
        try{
            BufferedReader reader=new BufferedReader(new FileReader( filePath));
            String rule="";
            while((rule=reader.readLine())!=null){
                if(!rule.isEmpty()){
                    String[] tokens  = rule.split(",",-1);
                    if (tokens.length==3) {
                        String machine = tokens[0];
                        String extension = tokens[1];
                        String folder = tokens[2];

                        String text = "Copy all files ";

                        if (machine.isEmpty()||machine.equals("\"\""))
                            machine = "any machine";           
                        if (folder.isEmpty()||folder.equals("\"\"")){
                            folder = GetConfig("syncpath", appendage + "../scrubber/config/www-rtbackup" + disknumber + ".properties");
                            folder = URLDecoder.decode(URLDecoder.decode(folder,"UTF-8"),"UTF-8");
                        }
                        text = text + extension + " from " +  machine  + " to folder "+ folder;
                        r+="<option value='"+rule+"'>"+text+"</option>" ;
                    }
                }
            }
            reader.close();
        
        }catch(Throwable th){
            th.printStackTrace();
        }
        
        return r;
    }
    
public String replaceSpecialCharacters(String str){
            String value2 = str.replace("ñ", "&#241;");
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
            
            return value2;
}    

int CheckBlacklistedDirectories(WebFuncs wf, String _b1, String _b2) {
    try {
        wf.launchBL(LocalIP, _b1, _b2);
        return 1;
    } catch (Exception e) {
        e.printStackTrace();
        return -1;
    }
}

public int GetNumberofFilesInDir(String _path, String _contains) {
    File f = new File(_path);
    if (f.exists()) {
        
        int i = 0;
        File[] files = f.listFiles();       
        for (File f2: files) {
            if (_contains.length() > 0) {
               if (f2.getName().contains(_contains)) i++;
            } else {
                i++;                
            }
        }
        
        return i;
        
        
    } else {
        return -1;
    }
    
}

    private String getAlteranteServers() {
        String serversJS = "";
        try {
            ArrayList<String> servers = NetUtils.getAllServerAddressPortProbe(Integer.parseInt(retries), 3, 6000, false);
            String sMode = GetConfig("mode", appendage + "../scrubber/config/" + "www-rtbackup.properties");
            String sSignature = GetConfig("signature", appendage + "../scrubber/config/" + "www-rtbackup.properties");
            for (String servername : servers) {
                if(sMode.equals("client") || !servername.equals(sSignature)){
                    serversJS+="servers.push('"+servername +"');";    
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return serversJS;
    }
    
    private String getAlteranteServerURL(String servername) {
        try {
            ArrayList<String> servers = NetUtils.getServerAddressPortProbe(servername, 3, 10000);
            if(servers!=null){
                String url = "http://" +  servers.get(1) + ":" + servers.get(2);
                return url;    
            }            
        } catch (InterruptedException ex) {
            Logger.getLogger(Worker.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private void updateFromPage1() {
        //store settings that came from page 1
        p("Updating config[2]");
        int nRes = UpdateConfig("mode", sMode, appendage + "../scrubber/config/" + "www-rtbackup.properties");
        p("nres = " + nRes);      

        getClusterID();
        
        p("sallowpeer = " + sAllowPeer);
        int nres = UpdateConfig("allowpeer", sAllowPeer, "config/www-server.properties");

        p("allowremote = " + sAllowRemote);
        nres = UpdateConfig("allowremote", sAllowRemote, "config/www-server.properties");  
        
        
        
        
        p("signature = " + sSignature);
        nres = UpdateConfig("signature", sSignature, appendage + "../scrubber/config/www-rtbackup.properties");
        
        UserCollection uc = UserCollection.getInstance();
        uc.changeUserAdmin(adminuser, adminpw1);
        
        p("allowotherusers = " + sAllowOtherUsers);
        nres = UpdateConfig("allowotherusers", sAllowOtherUsers, "config/www-server.properties");     
        
        ArrayList<User> newuserlist = new ArrayList<User>();
            newuserlist.add(uc.getUserAdmin());
        if(!useraccounts.equals("")){
            for(String account : useraccounts.split(";")){
                if(!account.isEmpty()){
                    try {
                        String[] accounts = account.split(",");
                        User user = uc.getUsersByName(accounts[0]);
                        if(accounts.length > 1 && !accounts[1].isEmpty()){
                            newuserlist.add(new User(accounts[0], PasswordHash.createHash(accounts[1]), "user", accounts[2]));
                        }else{
                            //if(accounts.length < 2){
                                newuserlist.add(user);
                            //}
                        }
                    } catch (Exception ex) {
                        Logger.getLogger(Worker.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        Collection<User> olduserlist = (Collection<User>) uc.getUsersByRole("user").clone();
        olduserlist.removeAll(newuserlist);
        for (User user : olduserlist) {
            p("remove share to " + user.getUsername());
            ShareController.getInstance().removeSharesOfUser(user.getUsername());
        }
        uc.replaceUserCollection(newuserlist);
    }
    
    

    private String getNavbarMenu(boolean bUserAuthenticated, String sUser, String sBoxPassword, boolean invalidLogin, boolean remote) {
        String menustaticpre = "<ul class=\"nav\" style=\"font-size:12px; background-color:#00;color:black;\">                                                          \n" +
            "<li><img src=\"img/alterante_logo.png\" style=\"width:110px;\"></li>"; 

        String formlogin = "";
        String adminmenu = "";

        String sState = GetConfig("state", "config/www-setup.properties");
        if (!sState.equals("NEW")) {
            menustaticpre = menustaticpre +
            "<li class=\"\"><a href=\"./main_bs.htm\" target=\"MAIN1\">Home</a></li>\n" +
            "<li><a href=\"./about.htm\" target=\"MAIN\">About</a></li>";
                        
            if (bUserAuthenticated) {
                    

                    formlogin =  "<div class=\"navbar-form\">"
                           + "<div class=\"pull-right\">"

                           + "<span  class=\"label label-info\"><i class=\"icon-user icon-white\"></i>" + sUser + "</span>&nbsp"
                           + "<button type=\"button\" class=\"btn btn-small\" onclick=\"logout();\" style=\"font-size:12px;\">Sign out</button>&nbsp"
        //                                   + "<a id='changepasslink' href='#' onclick='changePassword();return false;'>Change password</a>"
                           + "</div>"
                           + "</div>";
                    if(!remote){

                        
                        UserCollection uc = UserCollection.getInstance();
                        User user = uc.getUsersByName(sUser);

                        if(user.getRole().equals("admin")){
                            adminmenu = "<li class=\"\"><a href=\"./settings.htm\" target=\"SIDEBAR\" onclick=\"openNetwork();\">Settings</a></li>" +
                                 "<li class=\"\"><a href=\"./cass7.php?pw=xyzzy2011\" target=\"MAIN\"><p>Batches</p></a></li>" +
                                 "<li class=\"\"><a href=\"./nodestats.htm?pw=xyzzy2011\" target=\"MAIN\">Nodes</a></li>" +
                                 "<li class=\"\"><a href=\"./backupinfo.php?pw=xyzzy2011\" target=\"MAIN\">Backups</a></li>" + 
                                 "<li class=\"\"><a href=\"./usage.htm\" target=\"MAIN\">Usage</a></li>" +
                                 "<li class=\"\"><a href=\"./shares.htm\" target=\"MAIN\">Shares</a></li>"; 
                        }
                   }

            } else {

                    if(invalidLogin){
                        formlogin = "<div class=\"navbar-form\">" +
                                "<div class=\"control-group error pull-right\">\n" +
                                "<span style=\"color:#A80000\"> Invalid Username or Password </span><input class=\"span2\" name =\"boxuser\" value=\""+ sUser +"\" type=\"text\" placeholder=\"User\" style=\"font-size:12px;background-color:lightyellow;\">\n" +
                                "<input class=\"span2\" name =\"boxpass\" value=\""+ sBoxPassword +"\" type=\"password\" placeholder=\"Password\" style=\"font-size:12px;background-color:lightyellow;\">\n" +
                                "<input class=\"foo\" style=\"display:none\" name=\"foo\" type=\"text\" id='foo'/>			   \n" +
                                "<button type=\"button\" class=\"btn\" onclick=\"login();\" style=\"font-size:12px;\">Sign in</button>" +
                                "</div>" +
                            "</div>;";
                    } else{
                        formlogin = "<div class=\"navbar-form\">" +
                                "<div class=\"pull-right\">\n" +
                                "<input class=\"span2\" name =\"boxuser\" type=\"text\" placeholder=\"User\" style=\"font-size:12px;background-color:lightyellow;\">\n" +
                                "<input class=\"span2\" name =\"boxpass\" type=\"password\" placeholder=\"Password\" style=\"font-size:12px;background-color:lightyellow;\">\n" +
                                "<input class=\"foo\" style=\"display:none\" name=\"foo\" type=\"text\" id='foo'/>			   \n" +
                                "<button type=\"button\" class=\"btn\" onclick=\"login();\" style=\"font-size:12px;\">Sign in</button>" +
                                "</div>" +
                            "</div>;";
                    }
            }
        }
        return menustaticpre + adminmenu + "</ul>" + formlogin;
    }

    private void updateFrompage2(String sBlacklist) throws IOException {
                                                               
        //store setting that came from page 2
        p("Updating config[3]");

        ///FDisk 1
        InputStream isscan=null;
        File f=null;
         p("sScanMode = " + sScanMode);
        if (sScanMode.equals("")) {
            sScanMode = "no";
        }
        int nRes = UpdateConfig("scannode", sScanMode, appendage + "../scrubber/config/" + "www-rtbackup.properties");
        p("nres = " + nRes);  

        f = new File(appendage + "../scrubber/config/www-rtbackup.properties");
        if (f.exists() ) {
            isscan =new BufferedInputStream(new FileInputStream(f));
            Properties props = new Properties();
            props.clear();
            props.load(isscan);
            isscan.close();
            String p = "";

            p = props.getProperty("scandir"); //Path al archivo
            if(sScanMode.equalsIgnoreCase("on")){

                nRes = UpdateConfig("scandir", sScanDirectory11, p);
            }else{
                nRes = UpdateConfig("scandir", "", p);
            }
            p("nres = " + nRes);       
        }


        //Disk 2
        p("sScanMode2 = " + sScanMode2);
        if (sScanMode2.equals("")) {
            sScanMode2 = "no";
        }
        nRes = UpdateConfig("scannode", sScanMode2, appendage + "../scrubber/config/" + "www-rtbackup2.properties");
        p("nres = " + nRes);  
       f = new File(appendage + "../scrubber/config/www-rtbackup2.properties");
        if (f.exists() ) {
            isscan =new BufferedInputStream(new FileInputStream(f));
            Properties props = new Properties();
            props.clear();
            props.load(isscan);
            isscan.close();
            String p = "";

            p = props.getProperty("scandir"); //Path al archivo
           if(sScanMode2.equalsIgnoreCase("on")){
                nRes = UpdateConfig("scandir", sScanDirectory21, p);
            }else{
                nRes = UpdateConfig("scandir", "", p);
            }

            p("nres = " + nRes);       
        }     


        //Disk 3
        p("sScanMode3 = " + sScanMode3);
        if (sScanMode3.equals("")) {
            sScanMode3 = "no";
        }
        nRes = UpdateConfig("scannode", sScanMode3, appendage + "../scrubber/config/" + "www-rtbackup3.properties");
        p("nres = " + nRes);  



        f = new File(appendage + "../scrubber/config/www-rtbackup3.properties");
        if (f.exists()  ) {
            isscan =new BufferedInputStream(new FileInputStream(f));
            Properties props = new Properties();
            props.clear();
            props.load(isscan);
            isscan.close();
            String p = "";

            p = props.getProperty("scandir"); //Path al archivo

            if(sScanMode3.equalsIgnoreCase("on")){
                 nRes = UpdateConfig("scandir", sScanDirectory31, p);
            }else{
                nRes = UpdateConfig("scandir", "", p);
            }

            p("nres = " + nRes);       
        }

        //Disk 4    
        p("sScanMode4 = " + sScanMode4);
        if (sScanMode4.equals("")) {
            sScanMode4 = "no";
        }
        nRes = UpdateConfig("scannode", sScanMode4, appendage + "../scrubber/config/" + "www-rtbackup4.properties");
        p("nres = " + nRes); 
        f = new File(appendage + "../scrubber/config/www-rtbackup4.properties");
        if (f.exists()  ) {
            isscan =new BufferedInputStream(new FileInputStream(f));
            
            Properties props = new Properties();
            
            props.clear();
            props.load(isscan);
            isscan.close();
            String p = "";

            p = props.getProperty("scandir"); //Path al archivo
            if(sScanMode3.equalsIgnoreCase("on")){
                 nRes = UpdateConfig("scandir", sScanDirectory41, p);
            }else{
                nRes = UpdateConfig("scandir", "", p);
            }                                          

            p("nres = " + nRes);       
        }


        //Rep Factor
        p("rfactor = " + sRepFactor);
        nRes = UpdateConfig("rfactor", sRepFactor, appendage + "../scrubber/config/" + "www-rtbackup.properties");
        p("nres = " + nRes);    

        BufferedWriter writer=new BufferedWriter(new FileWriter(appendage + "../scrubber/config/blacklist.txt",false));
        for(String s: sBlacklist.split(";")){
            writer.write(URLDecoder.decode(s ,"UTF-8"));
            writer.newLine();
        }
        writer.close();
        
        String sMD5BlacklistNew = NetUtils.calcMD5(appendage + "../scrubber/config/blacklist.txt");
        p("MD5 = " + sMD5Blacklist);
        p("MD5New = " + sMD5BlacklistNew);
        if (!sMD5Blacklist.equals(sMD5BlacklistNew)) {
            p("DO Blacklist check!!!!");
            String sBlackListNew = NetUtils.readFileIntoString(appendage + "../scrubber/config/blacklist.txt");     
            int res = CheckBlacklistedDirectories(wf, sBlackListOld, sBlackListNew);                                        
        } else {
            p("SKIP Blacklist check.");
        }
    }

    
    private void updateFrompage8() throws IOException {
                                                               
        //store setting that came from page 2
        p("Updating config[3]");

        ///FDisk 1
        InputStream isscan=null;
        File f=null;
        int nRes = 0;

        f = new File(appendage + "../scrubber/config/www-rtbackup.properties");
        if (f.exists() ) {
            isscan =new BufferedInputStream(new FileInputStream(f));
            Properties props = new Properties();
            props.clear();
            props.load(isscan);
            isscan.close();
        }

        String sDoBackup = "no";
        p("sBackupMode = " + sBackupMode1);
        if (sBackupMode1.equals("on")) {
            sDoBackup = "yes";
            nRes = UpdateConfig("backuppath", sBackupDirectory, appendage + "../scrubber/config/" + "www-rtbackup.properties");
            p("nres = " + nRes);       
        }else{
            nRes = UpdateConfig("backuppath", "", "scrubber/config/" + "www-rtbackup.properties");
            p("nres = " + nRes);       
        }

        nRes = UpdateConfig("backupnode", sDoBackup, appendage + "../scrubber/config/" + "www-rtbackup.properties");
        p("nres = " + nRes);  

        
        BufferedWriter writer=new BufferedWriter(new FileWriter(appendage + "../scrubber/config/router1.txt"));
        for(String rule : syncrules1.split(";")){
            if(!rule.isEmpty()){
                writer.write(rule);
                writer.newLine();
            }
        }
        writer.close();
        
        //Disk 2
       f = new File(appendage + "../scrubber/config/www-rtbackup2.properties");
        if (f.exists() ) {
            isscan =new BufferedInputStream(new FileInputStream(f));
            Properties props = new Properties();
            props.clear();
            props.load(isscan);
            isscan.close();
        }     

        sDoBackup = "no";
        p("sBackupMode2 = " + sBackupMode2);
        if (sBackupMode2.equals("on")) {
            sDoBackup = "yes";                                                                                    
            nRes = UpdateConfig("backuppath", sBackupDirectory2, appendage + "../scrubber/config/" + "www-rtbackup2.properties");
            p("nres = " + nRes);       
        }else{
            nRes = UpdateConfig("backuppath", "", appendage + "../scrubber/config/" + "www-rtbackup2.properties");
            p("nres = " + nRes);       
        }

        nRes = UpdateConfig("backupnode", sDoBackup, appendage + "../scrubber/config/" + "www-rtbackup2.properties");
        p("nres = " + nRes);  

        writer=new BufferedWriter(new FileWriter(appendage + "../scrubber/config/router2.txt"));
        for(String rule : syncrules2.split(";")){
            if(!rule.isEmpty()){
                writer.write(rule);
                writer.newLine();
            }
        }
        writer.close();

        
        //Disk 3

        f = new File(appendage + "../scrubber/config/www-rtbackup3.properties");
        if (f.exists()  ) {
            isscan =new BufferedInputStream(new FileInputStream(f));
            Properties props = new Properties();
            props.clear();
            props.load(isscan);
            isscan.close();
        }
         sDoBackup = "no";
        p("sBackupMode3 = " + sBackupMode3);
        if (sBackupMode3.equals("on")) {
            sDoBackup = "yes";                                                                                    
            nRes = UpdateConfig("backuppath", sBackupDirectory3, appendage + "../scrubber/config/" + "www-rtbackup3.properties");
            p("nres = " + nRes);       
       }else{
            nRes = UpdateConfig("backuppath", "", appendage + "../scrubber/config/" + "www-rtbackup3.properties");
            p("nres = " + nRes);       
        }
        nRes = UpdateConfig("backupnode", sDoBackup, appendage + "../scrubber/config/" + "www-rtbackup3.properties");
        p("nres = " + nRes);  
        


        writer=new BufferedWriter(new FileWriter(appendage + "../scrubber/config/router3.txt"));
        for(String rule : syncrules3.split(";")){
            if(!rule.isEmpty()){
                writer.write(rule);
                writer.newLine();
            }
        }
        writer.close();        


        //Disk 4    
        f = new File(appendage + "../scrubber/config/www-rtbackup4.properties");
        if (f.exists()  ) {
            isscan =new BufferedInputStream(new FileInputStream(f));
            Properties props = new Properties();
            props.clear();
            props.load(isscan);
            isscan.close();
        }

        sDoBackup = "no";
        p("sBackupMode2 = " + sBackupMode4);
        if (sBackupMode4.equals("on")) {
            sDoBackup = "yes";  
            nRes = UpdateConfig("backuppath", sBackupDirectory4, appendage + "../scrubber/config/" + "www-rtbackup4.properties");
            p("nres = " + nRes);    
        }else{
            nRes = UpdateConfig("backuppath", "", appendage + "../scrubber/config/" + "www-rtbackup4.properties");
            p("nres = " + nRes);    
        }
        nRes = UpdateConfig("backupnode", sDoBackup, appendage + "../scrubber/config/" + "www-rtbackup4.properties");
        p("nres = " + nRes);  


        writer=new BufferedWriter(new FileWriter(appendage + "../scrubber/config/router4.txt"));
        for(String rule : syncrules4.split(";")){
            if(!rule.isEmpty()){
                writer.write(rule);
                writer.newLine();
            }
        }
        writer.close();        
        
        //SYNC
        nRes = UpdateConfig("syncpath", sSyncDirectory1, appendage + "../scrubber/config/" + "www-rtbackup.properties");
        p("nres = " + nRes);       

        nRes = UpdateConfig("syncpath", sSyncDirectory2, appendage + "../scrubber/config/" + "www-rtbackup2.properties");
        p("nres = " + nRes);       

        nRes = UpdateConfig("syncpath", sSyncDirectory3, appendage + "../scrubber/config/" + "www-rtbackup3.properties");
        p("nres = " + nRes);       

        nRes = UpdateConfig("syncpath", sSyncDirectory4, appendage + "../scrubber/config/" + "www-rtbackup4.properties");
        p("nres = " + nRes);       

        String sDoSync = "no";
        p("******* sSyncMode1 = " + sSyncMode1);
        if (sSyncMode1.equals("on")) {
            sDoSync = "yes";                                                                                    
        }
        nRes = UpdateConfig("syncnode", sDoSync, appendage + "../scrubber/config/" + "www-rtbackup.properties");
        p("nres = " + nRes);  

        sDoSync = "no";
        p("sSyncMode2 = " + sSyncMode2);
        if (sSyncMode2.equals("on")) {
            sDoSync = "yes";                                                                                    
        }
        nRes = UpdateConfig("syncnode", sDoSync, appendage + "../scrubber/config/" + "www-rtbackup2.properties");
        p("nres = " + nRes); 

        sDoSync = "no";
        p("sSyncMode3 = " + sSyncMode3);
        if (sSyncMode3.equals("on")) {
            sDoSync = "yes";                                                                                    
        }
        nRes = UpdateConfig("syncnode", sDoSync, appendage + "../scrubber/config/" + "www-rtbackup3.properties");
        p("nres = " + nRes); 

        sDoSync = "no";
        p("sSyncMode4 = " + sSyncMode4);
        if (sSyncMode4.equals("on")) {
            sDoSync = "yes";                                                                                    
        }
        nRes = UpdateConfig("syncnode", sDoSync, appendage + "../scrubber/config/" + "www-rtbackup4.properties");
        p("nres = " + nRes); 

        //Rep Factor
        p("rfactor = " + sRepFactor);
        nRes = UpdateConfig("rfactor", sRepFactor, appendage + "../scrubber/config/" + "www-rtbackup.properties");
        p("nres = " + nRes);    

    }

    
    public static boolean validIP (String ip) {
    try {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        String[] parts = ip.split( "\\." );
        if ( parts.length != 4 ) {
            return false;
        }

        for ( String s : parts ) {
            int i = Integer.parseInt( s );
            if ( (i < 0) || (i > 255) ) {
                return false;
            }
        }

        return true;
    } catch (NumberFormatException nfe) {
        return false;
    }
}
    
    private String getComputerName() {        
        String computername = "";
                                            
        try {
            if (bWindowsServer) { 
                computername = System.getenv("computername");                                                
            }

            if(computername == null || computername.isEmpty()){

                InetAddress clientIP = NetUtils.getLocalAddressNonLoopback2();
                if (clientIP != null) {
                    computername = clientIP.getHostName();
                    
                    if (computername.contains(".")) {                       
                        if (!validIP(computername)) {
                            computername = computername.substring(0,computername.indexOf("."));                            
                        }
                    }
                }    
            }                                                
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(computername == null || computername.isEmpty()){
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("MMdd");
            String sDate = sdf.format(cal.getTime());
            computername = "ALTERANTE"+sDate;
        }
        
        return computername;
        
    }

    private String getExtensions() {
        try {
            StringBuilder res = new StringBuilder();
            res.append("{\n");
            res.append("\"filetypes\": [\n");

            FileInputStream fileExtensions = new FileInputStream(appendage + "../scrubber/config/FileExtensions_All.txt");
            Scanner objScanner = new Scanner(fileExtensions);

            String group = "";
            Boolean bCloseGroup = false;
            int nGroups = 0;
            int nExts = 0;
            
            while (objScanner.hasNext()) {                
                //res.append("{\n");

                String line = objScanner.nextLine();
                String key = line.split(",")[0];
                String name = line.split(",")[1];
                
                if (key.equals("@")) {
                    //group
                    if(bCloseGroup) res.append("]}\n");
                    nGroups++;
                    if (nGroups > 1) res.append(",\n");
                    res.append("{\n");
                    bCloseGroup = true;
                    group = line.split(",")[1];
                    res.append("\"group\": \"" + group + "\",\n");
                    String sGroupIcon = "";
                    if (group.startsWith("Photos") || group.startsWith("Image")) sGroupIcon = "glyphicon glyphicon-camera";
                    if (group.startsWith("Video")) sGroupIcon = "glyphicon glyphicon-facetime-video";
                    if (group.startsWith("Audio")) sGroupIcon = "glyphicon glyphicon-music";
                    if (group.startsWith("Documents")) sGroupIcon = "glyphicon glyphicon-briefcase";
                    res.append("\"group_icon\": \"" + sGroupIcon + "\",\n");
                    res.append("\"extensions\": [\n");
                    nExts = 0;
                } else {
                    //filetype
                    nExts++;
                    if (nExts > 1) res.append(",\n");
                    res.append("  {\n");
                    res.append("  \"ext\": \"" + line.split(",")[0] + "\",\n");
                    res.append("  \"ext_name\": \"" + line.split(",")[1] + "\",\n");
                    res.append("  \"ext_group\": \"" + group + "\",\n");
                    
                    String sExtIcon = "";
                    if (wf.is_document(line.split(",")[0])) sExtIcon = "glyphicon glyphicon-briefcase";
                    if (wf.is_photo(line.split(",")[0])) sExtIcon = "glyphicon glyphicon-camera";
                    if (wf.is_video(line.split(",")[0])) sExtIcon = "glyphicon glyphicon-facetime-video";
                    if (wf.is_music(line.split(",")[0])) sExtIcon = "glyphicon glyphicon-music";
                    if (line.split(",")[0].startsWith(".doc")) sExtIcon = "glyphicons glyphicons-file";
                    if (line.split(",")[0].startsWith(".xls")) sExtIcon = "glyphicons glyphicons-charts";
                    if (line.split(",")[0].startsWith(".ppt")) sExtIcon = "glyphicons glyphicons-pie-chart";
        
                    res.append("  \"ext_icon\": \"" + sExtIcon + "\"\n");
                    res.append("  }\n");
                }
            }        
            if(bCloseGroup) res.append("]}\n");
            
            res.append("]}\n");
            return res.toString();      
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    private String getNodeInfo() {
        StringBuilder res = new StringBuilder();
        String res_nodes = "";
        
        res_nodes = wf.echonode("nodes", dbmode);   

        res.append("{\n");
        res.append("\"nodes\": [\n");

        Scanner scan = new Scanner(res_nodes);
        while (scan.hasNextLine()) {
            String nLine = scan.nextLine();
            p("processing line '" + nLine + "'");
            String delimiter = ",";
            StringTokenizer stl = new StringTokenizer(nLine.trim(), delimiter, true);

            String sNodeID = "";
            String sNodeIP = "";
            String sNodePort = "";
            String sNodeNettyPort = "";
            String sNodeBackup = "";
            String sNodeFree = "";
            String sNodeSync = "";
            String sNodeLastBatch = "";
            String sNodeLastSeq = "";
            String sNodeLastPing = "";
            String sNodeLastPingLong = "";
            String sNodeScan = "";
            String sNodeMachine = "";

            try {
                
              sNodeID = stl.nextToken();
              stl.nextToken();
              sNodeIP = stl.nextToken();
              stl.nextToken();
              sNodePort = stl.nextToken();
              stl.nextToken();
              sNodeNettyPort = stl.nextToken();
              stl.nextToken();
              sNodeBackup = stl.nextToken();
              stl.nextToken();
              sNodeFree = stl.nextToken();
              stl.nextToken();
              sNodeSync = stl.nextToken();
              stl.nextToken();
              sNodeLastBatch = stl.nextToken();
              stl.nextToken();
              sNodeLastSeq = stl.nextToken();
              stl.nextToken();
              sNodeLastPingLong = stl.nextToken();
              stl.nextToken();
              sNodeMachine = stl.nextToken();

              SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss aaa");
              if (sNodeLastPingLong.length() > 1) sNodeLastPing = sdf.format(Long.valueOf(sNodeLastPingLong).longValue());

              res.append("{\n");
              res.append("\"node_id\": \"" + URLEncoder.encode(sNodeID, "UTF-8") + "\",\n");
              res.append("\"node_ip\": \"" + URLEncoder.encode(sNodeIP, "UTF-8") + "\",\n");
              res.append("\"node_port\": \"" + URLEncoder.encode(sNodePort, "UTF-8") + "\",\n");
              res.append("\"node_nettyport\": \"" + URLEncoder.encode(sNodeNettyPort, "UTF-8") + "\",\n");
              res.append("\"node_backup\": \"" + URLEncoder.encode(sNodeBackup, "UTF-8") + "\",\n");
              res.append("\"node_free\": \"" + URLEncoder.encode(sNodeFree, "UTF-8") + "\",\n");
              res.append("\"node_sync\": \"" + URLEncoder.encode(sNodeSync, "UTF-8") + "\",\n");
              res.append("\"node_lastbatch\": \"" + URLEncoder.encode(sNodeLastBatch,"UTF-8") + "\",\n");
              res.append("\"node_lastseq\": \"" + URLEncoder.encode(sNodeLastSeq,"UTF-8") + "\",\n");
              res.append("\"node_lastping\": \"" + URLEncoder.encode(sNodeLastPing,"UTF-8") + "\",\n");
              res.append("\"node_lastping_long\": \"" + URLEncoder.encode(sNodeLastPingLong,"UTF-8") + "\",\n");
            
              String sUUID = NetUtils.getUUID(appendage + "../scrubber/data/.uuid");
              String sType = "client";
              if (sUUID.equals(sNodeID)) {
                  sType = "server";
              }
              res.append("\"node_type\": \"" + URLEncoder.encode(sType,"UTF-8") + "\",\n");
              String sBackupFile = "";
              if (sNodeID.equals(NetUtils.getUUID(appendage + "../scrubber/data/.uuid"))) sBackupFile = "www-rtbackup.properties";
              if (sNodeID.equals(NetUtils.getUUID(appendage + "../scrubber/data/.uuid2"))) sBackupFile = "www-rtbackup2.properties";
              if (sNodeID.equals(NetUtils.getUUID(appendage + "../scrubber/data/.uuid3"))) sBackupFile = "www-rtbackup3.properties";
              if (sNodeID.equals(NetUtils.getUUID(appendage + "../scrubber/data/.uuid4"))) sBackupFile = "www-rtbackup4.properties";
              if (sBackupFile.length() > 0) {
                  String sBackupPath = NetUtils.getConfig("backuppath", appendage + "../scrubber/config/" + sBackupFile);
                  res.append("\"node_backuppath\": \"" + sBackupPath + "\",\n");                    
              }

              
              //server properties
              if (sType.equals("server")) {
                int idxfiles = GetNumberofFilesInDir(".", ".idx"); 
                int batches = wf.getBatchId("", dbmode);

                res.append("\"node_idx\": \"" + URLEncoder.encode(String.valueOf(idxfiles),"UTF-8") + "\",\n");                  
                res.append("\"node_batches\": \"" + URLEncoder.encode(String.valueOf(batches),"UTF-8") + "\",\n");
                float percentage = 100 - ((idxfiles * 100) / (float) batches);
                String perc = String.format("%.2f", percentage);
                res.append("\"node_idx_percent\": \"" + URLEncoder.encode(perc,"UTF-8") + "\",\n");

                res.append("\"node_nettyport_post\": \"" + URLEncoder.encode(String.valueOf(nettyport_post), "UTF-8") + "\",\n");
              
              }
              
              //backup properties
              if (sNodeBackup.equals("yes")) {
                  int batches = wf.getBatchId("", dbmode);
                  float percentage = 0;
                  try{
                      percentage=Float.parseFloat(sNodeLastBatch) / (float) batches * 100;
                  }catch(NumberFormatException ne){
                      
                  }
                  String perc = String.format("%.2f", percentage);
                  res.append("\"node_backup_percent\": \"" + URLEncoder.encode(perc, "UTF-8") + "\",\n");
              }
              
              res.append("\"node_machine\": \"" + URLEncoder.encode(sNodeMachine,"UTF-8") + "\"\n");
              
            } catch (Exception e) {
              e.printStackTrace();
            }
        
            if (scan.hasNextLine()) {
                 res.append("},\n");                                            
            } else {
                 res.append("}\n");                                                                                
            }
     }      
            if (bCloudAmazon) {
                  try {
                    String sBatchLast = NetUtils.getConfig("amazon_batch", "../rtserver/config/www-cloud-batches.properties");
                    res.append(",{\n");
                    int batches = wf.getBatchId("", dbmode);
                    res.append("\"node_type\": \"" + URLEncoder.encode("cloud.amazon","UTF-8") + "\",\n");
                    res.append("\"node_lastbatch\": \"" + URLEncoder.encode(sBatchLast,"UTF-8") + "\",\n");
                    float percentage = 0;
                    if (sBatchLast.length() > 0) percentage =  Float.parseFloat(sBatchLast) / (float) batches * 100;
                    String perc = String.format("%.2f", percentage);
                    res.append("\"node_backup_percent\": \"" + URLEncoder.encode(perc, "UTF-8") + "\"\n");
                    res.append("}\n");
                  } catch (Exception e) {
                      e.printStackTrace();
                  }
                          
            }
            res.append("]}\n");
            return res.toString();      
    }

    private String getnodesfn() {
        String res = "";
        String res2 = "";
        res = wf.echonode("nodes", dbmode);   
        HashMap<String,String> result = new HashMap<String, String>();

        Scanner scan = new Scanner(res);
        while (scan.hasNextLine()) {
            String nLine = scan.nextLine();
            p("processing line '" + nLine + "'");
            String delimiter = ",";
            StringTokenizer stl = new StringTokenizer(nLine.trim(), delimiter, true);

            String sNodeID = "";
            String sNodeIP = "";
            String sNodePort = "";
            String sNodeBackup = "";
            String sNodeFree = "";
            String sNodeSync = "";
            String sNodeLastBatch = "";
            String sNodeLastSeq = "";
            String sNodeLastPing = "";
            String sNodeScan = "";
            String sNodeMachine = "";

            try {
              sNodeID = stl.nextToken();
              stl.nextToken();
              sNodeIP = stl.nextToken();
              stl.nextToken();
              sNodePort = stl.nextToken();
              stl.nextToken();
              sNodeBackup = stl.nextToken();
              stl.nextToken();
              sNodeFree = stl.nextToken();
              stl.nextToken();
              sNodeSync = stl.nextToken();
              stl.nextToken();
              sNodeLastBatch = stl.nextToken();
              stl.nextToken();
              sNodeLastSeq = stl.nextToken();
              stl.nextToken();
              sNodeLastPing = stl.nextToken();
              stl.nextToken();
              sNodeMachine = stl.nextToken();

              SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss aaa");
              sNodeLastPing = sdf.format(Long.valueOf(sNodeLastPing).longValue());

            } catch (Exception e) {
              e.printStackTrace();
            }

            result.put(sNodeMachine, sNodeMachine);

        }

        for (Map.Entry<String, String> entry : result.entrySet()) {
             res2+=" <option value=\""+entry.getKey() +"\">"+entry.getValue() +"</option>";
         }

        return res2;
    }

        private boolean isValidMultiClusterID(String sMultiClusterID) {
            try {
                String sClusterID = getClusterID();
                p("This cluster: " + sClusterID);
                p("Multi cluster: " + sMultiClusterID);
                if (sMultiClusterID!=null && sMultiClusterID.trim().length()>0) {
                    if (sClusterID.equals(sMultiClusterID)) {
                        //cluster ID is local, proceed to local
                        return false;
                    } else {
                        //different ID, proceed to remote
                        return true;
                    }                
                } else {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }                             
        }
        
        private String getClusterID() {
            p("[WebServer] getClusterID(). appendageRW = " + appendageRW);
            String clusteridUUIDPath = appendageRW + "../scrubber/data/clusterid";
            p("[WebServer] getClusterID(). clusterUUIDPath = " + clusteridUUIDPath);
            String clusteridUUID = NetUtils.getUUID(clusteridUUIDPath);
            p("clusteridUUID = " + clusteridUUID);
            return clusteridUUID;
        }
        
        private String getClusterToken() {
            String clusterTokenUUIDPath = appendage + "../scrubber/data/clusterToken";            
            
            File f = null;
            FileInputStream fis = null;
            FileOutputStream fos = null;
            
            String sUUID = "";
            
            try {
                
                f = new File(clusterTokenUUIDPath);

                if (f.exists()) {
                    fis = new FileInputStream(f);
                    Scanner scanner2 = new Scanner(fis);
                    sUUID = scanner2.nextLine();                                    
                    System.out.println("UUID exists = " + sUUID);
                }
            }catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (fis != null) fis.close();
                    if (fos != null) fos.close();                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
                p("clusterTokenUUID = " + sUUID);

                return sUUID;
            }
        }

        private String getSharesTableContent(boolean debug) {

                    


                    ShareController sc = ShareController.getInstance();
                    Collection<ShareToken> shares = sc.getShareTokens();
                    String res2 = "";
                    if(shares != null && !shares.isEmpty()){
                        res2 = "<tr>" + 
                                        "<th>Type</th>" + 
                                        "<th>ID</th>" + 
                                        (debug?"<th>ShareToken</th>":"") + 
                                        "<th>With</th>" + 
                                        "<th>Actions</th>" + 
                                    "</tr>";
                        for (ShareToken share : shares) {
                            String usersString = ShareController.getInstance().getPermissionUsersString(share.getKey());
                            String key = share.getKey();
                            String type = share.getType();
                            if(share.getType().equals(ShareTypes.CLUSTER.toString())){
                                key = GetConfig("signature", appendage + "../scrubber/config/www-rtbackup.properties");
                                type = "COMPUTER";
                            }
                             
                            res2 += "<tr>" + 
                                        "<td>" + type + "</td>" + 
                                        "<td>" + key + "</td>" +
                                       (debug?"<td>" + share.getShare_token() + "</td>":"") + 
                                        "<td>" + usersString + "</td>" + 
                                        "<td><button type=\"button\" class=\"btn\" onclick=\"getmodal(\'" + share.getType() + "\',\'" + share.getKey() +"\')\">Edit</button>"
                                   + "   <button type=\"button\" class=\"btn\" onclick=\"confirmremoveshare(\'" + share.getType() + "\',\'" + share.getKey() +"\')\">Remove</button>" +
                                   "</tr>";
                        }
                    }else{
                        res2 = "<tr><td>No shares created</td></tr>";
                    }
            return res2;
        }

    private String createInvitationModal(ShareToken share) {
        String link = "alterante://access?token=" + share.getShare_token();
        String emailpart;
        String title = "Share ";
        if(share.getType().equals(ShareTypes.CLUSTER.toString())){
            title += "Computer";
        }else{
            title += share.getType() + " " + share.getKey();
        }
        String notifyprop = GetConfig("notifymail", "config/www-mailer.properties"); //props.getProperty("notifymail");
        
        if(notifyprop != null && notifyprop.equals("on")){
            emailpart = "<div class=\"checkbox\"><label><input class=\"sendbymail\" type=\"checkbox\" checked=\"true\">Send share notification by email</label></div>";
        }else{
            emailpart = "<br>You have not enabled notify users via email. Click 'Email Setup' to enable it and configure yout email settings<br><button type=\"button\" class=\"btn btn-primary\" onclick=\"window.open(\'./setup.htm?spage=5&shareopenmodal=false&sharetype=" 
                    + share.getType() + "&sharekey=" + share.getKey() + "\',\'_self\')\"><i class=\"icon-cog icon-white\"></i>Email setup</button>";
        }
        String headerdiv = "<div class=\"modal hide\" id=\"sharelinkModal\">"
        + "<div class=\"modal-header\">"
        + "<h3>" + title + "</h3>"
        + "</div>";
        String bodydiv;
        String allowremote = GetConfig("allowremote", "config/www-server.properties");
        if(allowremote.equals("true")){
            bodydiv = "<div class=\"modal-body\">Send this link to a mobile device to access"
            + "<input class=\"span6\" readonly=\"readonly\" type=\"text\" value=\""+ link + "\" />"
            + emailpart
            + "</div>";
        }else{
            bodydiv = "<div class=\"modal-body\">Share successful<br>"
            + emailpart
            + "</div>";
        }
        
        String footerdiv = "<div class=\"modal-footer\"><button class=\"btn\" data-dismiss=\"modal\" aria-hidden=\"true\" onclick=\"doneinvitationmodal()\">Done</button></div></div>";
        return headerdiv + bodydiv + footerdiv;
    }
    
    boolean isPathInFile(String _Path, String _File) {
        try {
            File f = new File(_File);
            if (f.exists()) { 
                String sPaths = GetConfig("scandir", _File);
                if (sPaths.length() > 0) {                
                    StringTokenizer st = new StringTokenizer(sPaths, ";", false);
                    while (st.hasMoreTokens()) {
                        String sPath = st.nextToken();
                        String sPathDec = URLDecoder.decode(sPath, "UTF-8");
                        String sPathFix = sPathDec.replace("\\", "/");
                        p("check if '" + sPathFix + "' in '" + _Path + "'");
                        if (_Path.contains(sPathFix)) {
                            p("@@@@@@@@found '" + sPathFix + "' in '" + _Path + "'");
                            return true;
                        }                          
                    }
                    p("reached end");
                    return false;
                } else {
                    p("scandir len = 0");
                    return false;
                }
            } else {
                p("scandir file not found: " + _File);
                return false;
            }        

        } catch (Exception e) {
            e.printStackTrace();
            try {
                Thread.sleep(5000);
            } catch (Exception f) {
                f.printStackTrace();
            }
            return false;
        }
    }
    
    boolean CheckIPValid_Server(String __ip) {
        try {
            boolean bFound = false;
            String sSigIP = probes.get(__ip);
            String sSignature = GetConfig("signature", appendage + "../scrubber/config/" + "www-rtbackup.properties");                
            if (sSigIP != null) {
                p("Found IP/Signature:" + __ip + " " + sSigIP + " vs " + sSignature);
                if (sSigIP.equals(sSignature)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                //not found, do another scan
                ArrayList<String> servers = NetUtils.getAllServerAddressPortProbe(Integer.parseInt(retries), 5, 6000, true);
                p("Looking for signature: " + sSignature);
                for (String servername : servers) {
                    p("* Found Probe: " + servername);
                    probes.put(servername.split(",")[1], servername.split(",")[0]);
                    if (servername.split(",")[0].equals(sSignature)) {
                        if (servername.split(",")[1].equals(__ip)) {
                            bFound = true;
                        }                    
                    }
                }
                return bFound;
            }
           //String sMode = GetConfig("mode", appendage + "../scrubber/config/" + "www-rtbackup.properties");                       
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    boolean CheckIPValid(String __ip) {
        try {            
            String res = wf.echonode("nodes", dbmode);

            Scanner scan = new Scanner(res);
            while (scan.hasNextLine()) {
                String nLine = scan.nextLine();
                p("processing line '" + nLine + "'");
                String delimiter = ",";
                StringTokenizer stl = new StringTokenizer(nLine.trim(), delimiter, true);
                
                String sNodeID = stl.nextToken();
                stl.nextToken();
                String sNodeIP = stl.nextToken();
                
                p("Check IP:" + __ip + " " + sNodeIP);
                if (__ip.equals(sNodeIP)) {
                    return true;
                }                                  
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    boolean isPathBackup(String _path, String _file) {
        try {
            String sMode = GetConfig("backupnode", _file);
            if (sMode.equals("yes")) {
                String sPath = GetConfig("backuppath", _file);
                String sPathDec = URLDecoder.decode(sPath, "UTF-8");
                String sPathFix = sPathDec.replace("\\", "/");
                p("[backup] check if '" + sPathFix + "' in '" + _path + "'");
                if (_path.contains(sPathFix)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }        
    }
    
    boolean CheckPathValid(String _path) {
        if (isPathInFile(_path, appendage + "../scrubber/config/scan1.txt")) {
            return true;
        } else {
            if (isPathInFile(_path, appendage + "../scrubber/config/scan2.txt")) {
                return true;
            } else {
                if (isPathBackup(_path,appendage + "../scrubber/config/www-rtbackup.properties")) {
                    return true;
                } else {
                    if (isPathBackup(_path, appendage + "../scrubber/config/www-rtbackup2.properties")) {
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }
    }       
       
    String getFileMD5(String _file) {
        try {
            if (DB_PATH.length() == 0) loadPropsProcessor();
                String sPath = DB_PATH;
                String _cf = "Standard1";
                String _key = getFileExtension(_file).toLowerCase();
                String filename = sPath + File.separator + _cf + File.separator + "." + _key;
                String sMD5 = "";
                File ft = new File(filename);
                p("filename md5 = " + filename);
                if (ft.exists()) {
                    p("file found");

                    FileReader fr = new FileReader(filename);            
                    BufferedReader br = new BufferedReader(fr);
                    String sCurrentLine = "";
                    while ((sCurrentLine = br.readLine()) != null) {
                        //p("sCurrentLine:" + sCurrentLine);


                        String delimitersgf = ",";
                        StringTokenizer stgf = new StringTokenizer(sCurrentLine,delimitersgf, true);

                        String sLineDate = stgf.nextToken();
                        stgf.nextToken();
                        String sLineMD5 = stgf.nextToken();
                        stgf.nextToken();
                        String sLineName = stgf.nextToken();
                        
                        //p("sLineData   :" + sLineDate);
                        //p("sLineMD5    :" + sLineMD5);
                        //p("sLineName   :" + sLineName);
                        //p("_file       :" + _file);

                        if (sLineName.equals(_file)) {
                            p("match found: '" + sLineName + "' md5: " + sLineMD5);
                            sMD5 = sLineMD5;
                            break;
                        }
                    }
                    return sMD5;
                } else {
                    p("file not found");
                    return "";
                }
              
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }              
    }
    
    String getFileExtension(String _file) {

        int dotIndex = _file.lastIndexOf(".");
        if (dotIndex == -1 || dotIndex == _file.length() - 1) {
            return "";
        }
        return _file.substring(dotIndex + 1);
    }
    
    boolean isUserAdmin(String sAuthUUID) {
        p("----------isUserAdmin()");
        boolean isAdmin = false;
        UserSession us = uuidmap.get(sAuthUUID);
        String sUser;
        if( us != null){
            sUser = us.getUsername();
        } else{
            sUser = null;
            isAdmin = false;
        }       
        UserCollection uc = UserCollection.getInstance();
        User user = uc.getUsersByName(sUser);
        p("User: " + user);
        p("Role: " + user.getRole());
        if(user.getRole().equals("admin")){
            isAdmin = true;
        } else {
            isAdmin = false;
        }  
        p("isAdmin: " + isAdmin);
        p("-----end-----isUserAdmin()");
        return isAdmin;
    }
    
}

interface HttpConstants {
    /** 2XX: generally "OK" */
    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_ACCEPTED = 202;
    public static final int HTTP_NOT_AUTHORITATIVE = 203;
    public static final int HTTP_NO_CONTENT = 204;
    public static final int HTTP_RESET = 205;
    public static final int HTTP_PARTIAL = 206;

    /** 3XX: relocation/redirect */
    public static final int HTTP_MULT_CHOICE = 300;
    public static final int HTTP_MOVED_PERM = 301;
    public static final int HTTP_MOVED_TEMP = 302;
    public static final int HTTP_SEE_OTHER = 303;
    public static final int HTTP_NOT_MODIFIED = 304;
    public static final int HTTP_USE_PROXY = 305;

    /** 4XX: client error */
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_PAYMENT_REQUIRED = 402;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_BAD_METHOD = 405;
    public static final int HTTP_NOT_ACCEPTABLE = 406;
    public static final int HTTP_PROXY_AUTH = 407;
    public static final int HTTP_CLIENT_TIMEOUT = 408;
    public static final int HTTP_CONFLICT = 409;
    public static final int HTTP_GONE = 410;
    public static final int HTTP_LENGTH_REQUIRED = 411;
    public static final int HTTP_PRECON_FAILED = 412;
    public static final int HTTP_ENTITY_TOO_LARGE = 413;
    public static final int HTTP_REQ_TOO_LONG = 414;
    public static final int HTTP_UNSUPPORTED_TYPE = 415;

    /** 5XX: server error */
    public static final int HTTP_SERVER_ERROR = 500;
    public static final int HTTP_INTERNAL_ERROR = 501;
    public static final int HTTP_BAD_GATEWAY = 502;
    public static final int HTTP_UNAVAILABLE = 503;
    public static final int HTTP_GATEWAY_TIMEOUT = 504;
    public static final int HTTP_VERSION = 505;
}



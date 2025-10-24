package utils;

import java.io.File;
import java.net.URLDecoder;
import java.util.Properties;
import java.util.StringTokenizer;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;

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

import jakarta.activation.*;

import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import com.sun.mail.smtp.SMTPTransport;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.NoSuchProviderException;
import javax.mail.Store;
import javax.mail.util.ByteArrayDataSource;
import javax.mail.util.SharedByteArrayInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import utils.WebFuncs;
/*
 * import 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author agf
 */
public class MailerFuncs {
    
    static PrintStream log = null;
    static byte[] buffer = new byte[100000];
    static WebFuncs wf = null;
    static java.util.Hashtable mapAllow = new java.util.Hashtable();
    
    static File root = null;
    protected static Properties props = new Properties();
    static String sPOP3Host = "";
    static String sPOP3Port = "";
    static String sSMTPHost = "";
    static String sSMTPPort = "";
    static String sPOP3User = "";
    static String sPOP3Password = "";
    static String sPOP3Delay = "100000";
    static String sLogPath = "";
    static boolean shutdown = false;
    
    static String DB_MODE = "xy";

    public static final int SERVICE_CONTROL_STOP = 1;
    public static final int SERVICE_CONTROL_SHUTDOWN = 5;        
    static String LocalIP = "";

    static boolean bAllowMail = false;
    static boolean bScanMail = false;
    
    static String appendage = "";
    static String appendageRW = "";

    static void loadPropsDB() throws IOException {
        
        String r = "";
        p(System.getProperty("java.home"));
        File f = new File
            ("../rtserver/config/" + "www-server.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
            
            r = props.getProperty("dbmode");
            if (r != null) {
                DB_MODE = r;
            }
        }
    }
    
    
    static void loadProps() throws IOException {
        
        String r = "";
        p(System.getProperty("java.home"));
        File f = new File
            (appendage + "../rtserver/config/" + "www-mailer.properties");
        if (f.exists()) {
            InputStream is =new BufferedInputStream(new
                           FileInputStream(f));
            props.load(is);
            is.close();
            
            bAllowMail = false;
            r = props.getProperty("allowmail");
            if (r != null) {
                if (r.equals("on")) bAllowMail = true;
            }
            
            bScanMail = false;
            r = props.getProperty("scanmail");
            if (r != null) {
                if (r.equals("on")) bScanMail = true;
            }

            
            r = props.getProperty("smtphost");
            if (r != null) {
                sSMTPHost = r;
            }

            r = props.getProperty("smtpport");
            if (r != null) {
                sSMTPPort = r;
            }

            r = props.getProperty("pop3host");
            if (r != null) {
                sPOP3Host = r;
            }
            r = props.getProperty("pop3user");
            if (r != null) {
                sPOP3User = r;
            }
            r = props.getProperty("pop3pw");
            if (r != null) {
                sPOP3Password = r;
            }
            r = props.getProperty("pop3port");
            if (r != null) {
                sPOP3Port = r;
            }
            r = props.getProperty("pop3delay");
            if (r != null) {
                sPOP3Delay = r;
            }
            
            r = props.getProperty("log");
            if (r != null) {
                if (appendageRW.length() > 0) {
                    sLogPath = appendageRW + "/" + r;                    
                } else {
                    sLogPath = r;
                }
                File f2 = new File(sLogPath);
                if (f2.exists()) {
                    log = new PrintStream(new BufferedOutputStream(
                                      new FileOutputStream(sLogPath,true)));
                } else {
                    p("[MailerFuncs] WARNING: Log file cannot be opened. try create. " + sLogPath);
                    log = new PrintStream(new BufferedOutputStream(
                                      new FileOutputStream(sLogPath,true)));
                }
                if (log !=null) log("opening mailer log file: " + sLogPath);                    
            }
            
            r = props.getProperty("root");
            if (r != null) {
                root = new File(r);
                if (!root.exists()) {
                    throw new Error(root + " doesn't exist as server root");
                }
            }



        }

    }
    
    static void printProps() {
        p("pop3host="+sPOP3Host);
        p("pop3port="+sPOP3Port);
        p("pop3user="+sPOP3User);
        p("pop3pw= <not shown>");
        p("pop3delay="+sPOP3Delay);
        p("log="+sLogPath);
        p("dbmode="+DB_MODE);
        p("smtphost="+sSMTPHost);
        p("smtpport="+sSMTPPort);
        p("scanmail="+bScanMail);        
        p("allowmail="+bAllowMail);
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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.MailerFuncs-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected static void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.MailerFuncs_" + threadID + "] " + s);
    }
    
    protected static void log(String s) {

        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        p(sDate + " " + s);
        if (log != null) {
            synchronized (log) {
                log.println(sDate + " " + s);
                log.flush();
            }
            p(s);
        } else {
            p("[MailerFuncs] WARNING: log = null");
        }
    }
     
    static void loadWebFuncs() {
        if (wf == null) {
            InetAddress clientIP = null;
            try {
                clientIP = InetAddress.getLocalHost();                
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (clientIP != null) {
                LocalIP = clientIP.getHostAddress();
                if (LocalIP != null) {
                    p("LocalIP: " + LocalIP);
                } else {
                    p("Case Local IP Null.");
                }                                     
            } else {
                p("Case ClientIP Null.");                
            }
            wf = new utils.WebFuncs("");                  
        } else {
            p("wf already loaded.");
        }
   }
         
    public static void mailer_loop() {
        loadWebFuncs();
        Appendage app = new Appendage();
        appendage = app.getAppendage();
        appendageRW = app.getAppendageRW();
        while (true) {
            try {
                loadProps();
                loadPropsDB();
                printProps();
                if (bAllowMail || bScanMail) {
                    loadAllowMap();
                    fetch_tls(sPOP3Host, sPOP3User, sPOP3Password, sPOP3Port, true);   
                } else {
                    p("bAllowMail / sScanMail disabled.");
                }
                Long mPeriodMs = Long.valueOf(sPOP3Delay); //5min                              
                log("Waiting " + mPeriodMs/1000 + "s for next scan (email).");
                Thread.sleep(mPeriodMs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }        
    }
         
    static void loadAllowMap() {
        try {
            FileInputStream bf2 = new FileInputStream("./config/allow.txt");
            Scanner scanner2 = new Scanner(bf2);
            
            mapAllow.clear();
            
            while (scanner2.hasNext()) {                
                String spath = scanner2.nextLine();
                
                String replaced = spath.replace("\"", "");
                
                p("Adding token '" + replaced + "'");
                mapAllow.put(replaced, "");                                    
            }
            
            bf2.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    
    private static int save_image(BodyPart _bodypart, String _mailfrom) {
        
        try {
            String sFileName = _bodypart.getFileName();
            InputStream stream = _bodypart.getInputStream();
            
            log("Name '" + sFileName + "'");
            //sFileName.replace("/", "_");
            sFileName.replaceAll("/", "");

            String delimiters = "/\\";
            StringTokenizer st = new StringTokenizer(sFileName, delimiters, true);
            String filename = "";
            Integer nTokens = st.countTokens();

            while (st.hasMoreTokens()) {
                String w = "";
                w = st.nextToken();
                if (w.length() > 1) {
                    p(" string name: '" + w + "'");
                filename = w;
                }    
            }

            Date ts_start = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");
            String sDate = sdf.format(ts_start);
            
            File f = new File("inbox");                        
            f.mkdirs();

            String destination = "inbox" + File.separator + sDate + "__" + _mailfrom + "__" + filename;
            log("Writing file: " + destination); 
            
            BufferedInputStream bufferedInputStream = new BufferedInputStream (stream);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream (new FileOutputStream(destination));
            int size;
            while ((size = bufferedInputStream.read(buffer)) > -1) {
                bufferedOutputStream.write(buffer, 0, size);
            }
            bufferedOutputStream.flush();
            bufferedOutputStream.close(); 
            return 0;
        } catch (Exception e) {
            log("Exception" + e.getMessage());
            return -1;
        }
        
    }
    
    private static int scan_multipart(Multipart _multipart, String _sMailFrom2, String _subject) {

        try {
            log("Mail contains #attachments: " + _multipart.getCount());
            for (int k=0; k< _multipart.getCount();k++) {
                BodyPart bodypart = _multipart.getBodyPart(k);
                InputStream stream = bodypart.getInputStream();

                String sFileType = bodypart.getContentType();
                log("sFileType = '" + sFileType + "'");

                if ((sFileType.contains("image/jpeg") || sFileType.contains("application/octet-stream")) && bScanMail) {                
                    int nres = save_image(bodypart, _sMailFrom2);                           
                } else {
                    if (!bScanMail) {
                        p("Scan mail disabled. Skipping save_image");
                    }
                }
                if (sFileType.contains("multipart")) {
                    //nested multipart case
                    Multipart multipart2 = (Multipart) bodypart.getContent();
                    log("multipart contains #attachments: " + multipart2.getCount());

                    int res = scan_multipart(multipart2, _sMailFrom2, _subject);
                    p("res: " + res);
                }
                
                if (sFileType.contains("text/plain")) {                    
                    String sContent = bodypart.getContent().toString();
                    p("sContent = '" + sContent + "'");
//                    
//                    String delimiters = " \\n";
//                    StringTokenizer st = new StringTokenizer(sContent, delimiters, true);
//
//                    String sQuery = "";
//                    while (st.hasMoreTokens()) {                        
//                        sQuery = st.nextToken();
//                        p("query = '" + sQuery + "'");
//                    }

                    String sQuery = sContent.replaceAll("\\s","");

                    p("sQuery = '" + sQuery + "'");
                    
                    if (_subject.contains("alterante")) {
                        int nres = ProcessQuery(_sMailFrom2, sQuery);                           
                    }

                    
                    
                }
            } 
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
    
    public static boolean  isUserAllowed(String _user) {
        Object Got;

        if ((Got = mapAllow.get(_user)) != null) {
            p("User " + _user + " is allowed.");
            return true;
        } else {
            p("User " + _user + " is NOT allowed.");
            return false;
        }

    }
    
    public static int ProcessQuery(String _user, String _query) {
        try {
            if (isUserAllowed(_user)) {

                p("User allowed. sending response");

                //String sMailMessage = "hello " + _user + ". here are the results for query = " + _query;

                String sNumObj = "100";
                String sFileType = ".photo";
                String sNumCol = "3";
                String sPassword = "xyzzy2011";
                String sDaysBack = "";
                String sDateStart = "";

                int port = 0;
                boolean bCloudHosted = false;
                boolean bAWSHosted = false;
                String ClientIP = "";
                            
                p("root = " + root.toString());
                
                String sObjects = wf.echoh2m(_query, 
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
                                            DB_MODE,
                                            _user,
                                            false,
                                            false,
                                            null);

                p("Objects returned : '" + sObjects + "'");
               
                
                String sMailTo = _user;
                String sMailSubject = "alterante response";
                String sMailAttachment = "";
                String sMailHost = sSMTPHost;
                String sMailPort = sSMTPPort;
                String sMailFrom2 = sPOP3User;
                String sMailFromPassword2 = sPOP3Password;

                send_tls3(sMailTo, 
                    sMailSubject, 
                    sObjects, 
                    sMailAttachment, 
                    sMailHost, 
                    sMailPort, 
                    sMailFrom2, 
                    sMailFromPassword2,
                    _query,
                    root.toString());

                return 0;                           
            } else {
                return -1;
            }            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
                
    }

    public static void fetch_tls(
                String _host,
                String _username,
                String _password,
                String _port,
                boolean _dodelete
            ) {
        
        try {
            
            log("fetch_tls");
            
            //String host = "pop3.live.com";
            final String username = _username;
            final String password = _password;
            
            Properties props = new Properties();

            props.put("mail.pop3.port", _port);
            props.put("mail.pop3.socketFactory.port", _port);
            props.setProperty( "mail.pop3.socketFactory.fallback", "false");
            props.put("mail.pop3.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        
            props.put("mail.pop3.host", _host);
            props.put("mail.pop3.auth", "true");
            props.put("mail.pop3.starttls.enable", "true");

            log("set session");
            
            Session session = Session.getInstance(props, 
                    new javax.mail.Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password);
                        }
                    });

            log("connect to store");
            Store store = session.getStore("pop3");
            store.connect();
            //store.connect(host, username, password);
            
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            Message message[] = folder.getMessages();
            
            byte[] buffer = new byte[100000];
            
            for (int i=0,n=message.length; i<n; i++) {
                log(i + ": " + 
                message[i].getFrom()[0] + "\t" + 
                message[i].getSubject());

                String sMailFrom = message[i].getFrom()[0].toString();
                int npos = sMailFrom.indexOf("<");
                String sMailFrom2 = "";
                if (npos > 0) {
                    sMailFrom2 = sMailFrom.substring(sMailFrom.indexOf("<") +1 , sMailFrom.indexOf(">"));
                } else {
                    sMailFrom2 = sMailFrom;
                }
                
                String sType = message[i].getContentType();
                log("[0]");
                log("type: " + sType);
                            
                String sSubject = message[i].getSubject();
                if (sType.contains("text/html")) {
                    log("no attachments found.");
                } else {
                    log("content type = '" + message[i].getContentType());
                    if (message[i].isMimeType("multipart/*")) {
                        log("isMimeType multipart = true");
                    } else {
                        log("isMimeType multipart = false");                        
                    }
                    if (message[i].getContent() instanceof Multipart) {
                        log("getContent instanceof Multipart = true");
                    } else {                        
                        log("getContent instanceof Multipart = false");
                    }
                    
                    if (message[i].getContent() instanceof Multipart) {
                        log("[1]");
                        Multipart multipart = (Multipart) message[i].getContent();
                        log("[2]");
                        int nres = scan_multipart(multipart, sMailFrom2, sSubject);
                        p("nres scan_mulipart= " + nres);
                    } else {
                        //not multipart
                        log("[3]");
                        String sFileType = message[i].getContentType();
                        log("sFileType = " + sFileType + "'");
                        
                        if (message[i].getContent() instanceof SharedByteArrayInputStream) {
                            log("type sharedbytearrayinputstream");
                            InputStream is = (InputStream) message[i].getContent();
                            String contentType = message[i].getContentType(); 
                            ByteArrayDataSource byteArrayDataSource = new ByteArrayDataSource (is, contentType); 
                            //Multipart multipart = new MimeMultipart(byteArrayDataSource); 
                            //int nres = scan_multipart(multipart, sMailFrom2, sSubject);
                        }    
                        
                            String sBody = message[i].getContent().toString();
                            p("Body = '" + sBody + "'");
                            if (message[i].getSubject().contains("alterante")) {
                                int nres = ProcessQuery(sMailFrom2, sBody);                           
                            }

                    }
                }
                log("Marking mail as seen.");
                message[i].setFlag(Flags.Flag.SEEN, true);
                
                if (_dodelete) {
                    log("Marking mail as deleted.");
                    message[i].setFlag(Flags.Flag.DELETED, true);
                }
            }
        
            folder.close(true);
            store.close();
            
        } catch (MessagingException e) {
            log("[e1]");
            e.printStackTrace();
            log(e.getLocalizedMessage());
            log(e.getMessage());
        } catch (IOException e) {
            log("[e2]");
            e.printStackTrace();
            log(e.getLocalizedMessage());
            log(e.getMessage());
        } catch (Exception e) {
            log("[e3]");
            e.printStackTrace();
            log(e.getLocalizedMessage());
            log(e.getMessage());
        }
    }
    
    public static void send_tls2(
            String _sMailTo, 
            String _sMailSubject, 
            String _sMailMessage, 
            String _sMailAttachment,
            String _sMailHost,
            String _sMailPort,
            String _sMailUser,
            String _sMailPassword,
            String _sFileName,
            Boolean _sHtmlMailMessage) throws MessagingException {
                
        p("send_tls");
        
        final String username = _sMailUser;
        final String password = _sMailPassword;
        
        p("attachment: '" + _sMailAttachment + "'");
        p("mailto: '" + _sMailTo + "'");
        p("message: '" + _sMailMessage + "'");
        p("user: '" + _sMailUser + "'");
        p("host: '" + _sMailHost + "'");
        p("port: '" + _sMailPort + "'");
        
        
        String _sMailTo2 = "";
        try {
            _sMailTo2 = URLDecoder.decode(_sMailTo, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        p("mailto2: '" + _sMailTo2 + "'");

        String _sMailMessage2 = "";
        try {
            _sMailMessage2 = URLDecoder.decode(_sMailMessage, "UTF-8");
            _sMailMessage2 = _sMailMessage2 + "\n";
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        p("mailto2: '" + _sMailTo2 + "'");

        
        //String host = "smtp.live.com";
        //int port = 587;
        //int port = Integer.parseInt(_sMailPort);
        
        //String username = "agoyen@hotmail.com";
        //String password = "Inspiron800m";
        
        Properties props = new Properties();
        
        String sProtocol = "";
        
        Session session = Session.getInstance(props, null);

        if (_sMailPort.equals("465")) {
            p("-------SMTPS mode====");
            sProtocol = "smtps";
            props.put("mail.smtps.host", _sMailHost);
            //props.put("mail.transport.protocol", "smtp");
            //props.put("mail.smtp.socketFactory.port", "587");
            //props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtps.auth", "true");            
            props.put("mail.smtps.port", _sMailPort);
            props.put("mail.smtps.ssl.trust", _sMailHost);
        } else {
            p("-------SMTP mode====");
            sProtocol = "smtp";
            props.put("mail.smtp.host", _sMailHost);
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");            
            props.put("mail.smtp.port", _sMailPort);
            //props.put("mail.smtp.socketFactory.port", "587");
            //props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

            //props.put("mail.smtp.auth", "true");                        
            props.put("mail.smtp.starttls.enable", "true");
        }
        
        //props.put("mail.from", "agoyen@hotmail.com");
        //props.put("mail.smtp.socketFactory.fallback", "false");       
        
        //Session session = Session.getInstance(props);
        
        
        try {
            p("preparing message");
            Message message = new MimeMessage(session);
            
            message.setFrom(new InternetAddress(username));
            
            //message.setFrom(new InternetAddress("zombie@zombies.com"));
            //Address address[] = new InternetAddress[1];
            //address[0] = new InternetAddress("agoyen@hotmail.com", "Alex");
            //message.setReplyTo(address);
            
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(_sMailTo2));
            message.setSubject(_sMailSubject);
            
            BodyPart messageBodyPart = null;
            Multipart multipart = new MimeMultipart();
            
            new MimeBodyPart();
            
            //part two (message)
            messageBodyPart = new MimeBodyPart();
            if(_sHtmlMailMessage){
                messageBodyPart.setContent(_sMailMessage2, "text/html");
            }else{
                messageBodyPart.setText(_sMailMessage2);
            }
            multipart.addBodyPart(messageBodyPart);
                        
            //part one (image attachment)
            if(_sFileName != null){
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
    //                    p(" string name: '" + w + "'");
    //                    filename = w;
    //                }
    //            }

                messageBodyPart.setFileName(_sFileName);
                //messageBodyPart.setFileName(filename);                        
                multipart.addBodyPart(messageBodyPart);
            }
            
            //part three (footer)
            messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("Sent via Alterante. Get it now at http://www.alterante.com\n");
            multipart.addBodyPart(messageBodyPart);
            
            message.setContent(multipart);
            

            p("connecting");
            SMTPTransport t =
		(SMTPTransport)session.getTransport(sProtocol);   
            t.connect(_sMailHost, _sMailUser, password);
            p("sending...");
            
            //Transport transport = session.getTransport("smtp");
            //transport.connect(host, port, username, password);
            p("sending");
            t.sendMessage(message, message.getAllRecipients());          
            p("done");
            t.close();
            
        } catch (MessagingException e) {
            throw e;
        } //catch (UnsupportedEncodingException e) {
            //e.printStackTrace();
        //}
    }
    
    public static void send_tls3(
            String _sMailTo, 
            String _sMailSubject, 
            String _sObjects, 
            String _sMailAttachment,
            String _sHost,
            String _sPort,
            String _sUser,
            String _sMailPassword,
            String _query,
            String _root) {
                
        p("send_tls3");
        
        final String username = _sUser;
        final String password = _sMailPassword;
        
        p("attachment: '" + _sMailAttachment + "'");
        p("mailto: '" + _sMailTo + "'");
        p("objects: '" + _sObjects + "'");
        p("user: '" + _sUser + "'");
        p("host: '" + _sHost + "'");
        p("port: '" + _sPort + "'");
        
        
        String _sMailTo2 = "";
        try {
            _sMailTo2 = URLDecoder.decode(_sMailTo, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        p("mailto2: '" + _sMailTo2 + "'");

        
        Properties props = new Properties();
        
        //props.put("mail.smtps.user", _sUser);
        props.put("mail.smtps.host", _sHost);
        //props.put("mail.smtps.port", _sPort); 
        //props.put("mail.smtps.starttls.enable", "true");
        props.put("mail.smtps.auth", "true");
        props.put("mail.smtps.debug", "true");
        
        //props.put("mail.smtps.socketFactory.port", _sPort);
        //props.put("mail.smtps.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        //props.put("mail.smtps.socketFactory.fallback", "true");
        
               
        //SecurityManager security = System.getSecurityManager();
        
        //props.put("mail.from", "agoyen@hotmail.com");
        //props.put("mail.smtp.socketFactory.fallback", "false");       
        
        Session session = Session.getInstance(props, null);
//        Session session = Session.getInstance(props, 
//                new javax.mail.Authenticator() {
//                    protected PasswordAuthentication getPasswordAuthentication() {
//                        return new PasswordAuthentication(username, password);
//                    }
//                });
//        
        try {
            p("preparing message");
            Message message = new MimeMessage(session);
            
            message.setFrom(new InternetAddress(username));
            
            //message.setFrom(new InternetAddress("zombie@zombies.com"));
            //Address address[] = new InternetAddress[1];
            //address[0] = new InternetAddress("agoyen@hotmail.com", "Alex");
            //message.setReplyTo(address);
            
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(_sMailTo2));
            message.setSubject(_sMailSubject);
            
            
            // Fill the message
    
            Multipart multipart = new MimeMultipart();

            //messageBodyPart.setText(_sMailMessage2);
            MimeBodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setContent("results for " + _query, "text/html");                      
          
            multipart.addBodyPart(messageBodyPart);
            
            
            String delimiters = ",";
            StringTokenizer st = new StringTokenizer(_sObjects, delimiters, true);
            String filename = "";
            Integer nTokens = st.countTokens();
            while (st.hasMoreTokens()) {
                String sToken = st.nextToken();
                st.nextToken();
                String sFile = _root + "/cass/pic/" + sToken + ".jpg";
                p("File = '" + sFile + "'");
                File f = new File(sFile);
                if (f.exists()) {                
                    p("OK: File does exist.");
                    messageBodyPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(new File(sFile));
                    //messageBodyPart.setDataHandler(new DataHandler(source));
                    messageBodyPart.setFileName(sToken + ".jpg");
                    messageBodyPart.setDisposition(MimeBodyPart.INLINE);
                    multipart.addBodyPart(messageBodyPart);           
                } else {
                    try {
                        pw("ERROR: File does not exist: " + f.getCanonicalPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }                
            }

            
            
            //String filename = "image.jpg";
            
//            //part two (image attachment)
//            messageBodyPart = new MimeBodyPart();
//            DataSource source = new FileDataSource(_sMailAttachment);
//            messageBodyPart.setDataHandler(new DataHandler(source));
//            
//            String delimiters = "/\\";
//            StringTokenizer st = new StringTokenizer(_sMailAttachment, delimiters, true);
//            String filename = "";
//            Integer nTokens = st.countTokens();
//
//            while (st.hasMoreTokens()) {
//                String w = "";
//                w = st.nextToken();
//                if (w.length() > 1) {
//                    p(" string name: '" + w + "'");
//                    filename = w;
//                }
//            }
//                
//            messageBodyPart.setFileName(filename);            
//            multipart.addBodyPart(messageBodyPart);
            
            message.setContent(multipart);
          
            p("connecting...");
            SMTPTransport t =
		(SMTPTransport)session.getTransport("smtps");   
            t.connect(_sHost, _sUser, password);
            p("sending...");
            t.sendMessage(message, message.getAllRecipients());
            p("done!!!");
            t.close();
            
        } catch (MessagingException e) {
            e.printStackTrace();
        } //catch (UnsupportedEncodingException e) {
            //e.printStackTrace();
        //}
    }
    
    public void justConnectPop3(
                String _host,
                String _username,
                String _password,
                String _port
            ) throws NoSuchProviderException, MessagingException {
            
                final String username = _username;
                final String password = _password;

                Properties props = new Properties();

                props.put("mail.pop3.port", _port);
                props.put("mail.pop3.socketFactory.port", _port);
                props.setProperty( "mail.pop3.socketFactory.fallback", "false");
                props.put("mail.pop3.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

                props.put("mail.pop3.host", _host);
                props.put("mail.pop3.auth", "true");
                props.put("mail.pop3.starttls.enable", "true");


                Session session = Session.getInstance(props, 
                        new javax.mail.Authenticator() {
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(username, password);
                            }
                        });

                Store store = session.getStore("pop3");
                p("pop3: connecting");
                p("pop3: _host " + _host);
                p("pop3: _port " + _port);
                p("pop3: _username " + _username);
                store.connect();
                p("pop3: connected");
                store.close();
            
    }
    
    
        public void justConnectSMTP(
            String _sMailHost,
            String _sMailPort,
            String _sMailUser,
            String _sMailPassword) throws MessagingException {
                
          p("send_tls");
        
        final String username = _sMailUser;
        final String password = _sMailPassword;
        
        p("user: '" + _sMailUser + "'");
        p("host: '" + _sMailHost + "'");
        p("port: '" + _sMailPort + "'");
        
        
        
        
        Properties props = new Properties();
        
        String sProtocol = "";
        
        Session session = Session.getInstance(props, null);

        if (_sMailPort.equals("465")) {
            p("-------SMTPS mode====");
            sProtocol = "smtps";
            props.put("mail.smtps.host", _sMailHost);
            //props.put("mail.transport.protocol", "smtp");
            //props.put("mail.smtp.socketFactory.port", "587");
            //props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtps.auth", "true");            
            props.put("mail.smtps.port", _sMailPort);
            props.put("mail.smtps.ssl.trust", _sMailHost);
        } else {
            p("-------SMTP mode====");
            sProtocol = "smtp";
            props.put("mail.smtp.host", _sMailHost);
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");            
            props.put("mail.smtp.port", _sMailPort);
            props.put("mail.smtp.starttls.enable", "true");
        }
        
        
        
        try {
            
            SMTPTransport t =
		(SMTPTransport)session.getTransport(sProtocol);   
            p("smtp: connecting");
            p("smtp: _host " + _sMailHost);
            p("smtp: _port " + _sMailPort);
            p("smtp: _username " + _sMailUser);
            t.connect(_sMailHost, _sMailUser, password);
            p("smtp: connected");
            t.close();
            
        } catch (MessagingException e) {
            throw e;
        }
    }

}

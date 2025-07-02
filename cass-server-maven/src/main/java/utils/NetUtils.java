/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.UUID;
import static processor.FileUtils.close;
import static utils.Cass7Funcs.p;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import java.io.PrintWriter;

/**
 *
 * @author agf
 */
public class NetUtils {
    
    //static protected Properties props = new Properties();

    static boolean bConsole = true;

    public static InetAddress getLocalAddressNonLoopback2() throws SocketException {
    
        boolean bReachable = false;
        InetAddress addr_res = null;

        p("get interfaces");
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        p("done get interfaces");
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();                                               

                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
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
    
    public static InetAddress getLocalAddressNonLoopback(String _localport) throws SocketException {
    
    boolean bReachable = false;
    InetAddress addr_res = null;

    System.out.println("get interfaces");
    Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
    System.out.println("done get interfaces");
    while (ifaces.hasMoreElements()) {
        NetworkInterface iface = ifaces.nextElement();
        Enumeration<InetAddress> addresses = iface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            
            //System.out.println ("-main nonloopback---");
            //System.out.println ("main addr.getHostAddress() = " + addr.getHostAddress());
            //System.out.println ("main addr.getHostName() = " + addr.getHostName());
            //System.out.println ("main addr.isAnyLocalAddress() = " + addr.isAnyLocalAddress());
            //System.out.println ("main addr.isLinkLocalAddress() = " + addr.isLinkLocalAddress());
            //System.out.println ("main addr.isLoopbackAddress() = " + addr.isLoopbackAddress());
            //System.out.println ("main addr.isMulticastAddress() = " + addr.isMulticastAddress());
            //System.out.println ("main addr.isSiteLocalAddress() = " + addr.isSiteLocalAddress());
            
            try {
                //NOTE: assume reachable is BUGGY and therefore returns always true.
                bReachable = true;
                //bReachable = addr.isReachable(5000);
                //System.out.println ("main addr.isReachable() = " + bReachable);
                //System.out.println ("main addr.isSiteLocalAddress() = " + addr.isSiteLocalAddress());
            } catch (Exception e) {
                e.printStackTrace();
                bReachable = false;
            }

            if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && bReachable) {
                    //p("Now testing if address '" + addr.getHostAddress() + "' is REALLY reachable.");
                    
                    //get nodeinfo from server
                    String sServer = addr.getHostAddress();
                    String sServerPort = _localport;
                    
                    String sGetPath = "http://" + sServer + ":" + sServerPort + "/cass/welcome_header.htm";
                    String sFullName = "nodetest.tmp";

                    //p("Source = " + sGetPath);
                    //p("Dest = " + sFullName);

                    try {
                        int nRes = getfile(sGetPath, sFullName, 1, 500, 10000);

                        if (nRes > 0) {
                            p("REALLY reachable.");
                            addr_res = addr;
                        } else {
                            p("NOT REALLY reachable.");
                            addr_res = null;
                        }                        
                    } catch (Exception e) {
                        e.printStackTrace();
                        p("NOT REALLY reachable.");
                    }
            }
        }
    }
    if (addr_res != null) {
        return addr_res;
    } else {
        return null;
    }
}
   
   public static InetAddress getLocalAddressLoopback() throws SocketException {
    InetAddress addr_res = null;
    
    Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
    while (ifaces.hasMoreElements()) {
        NetworkInterface iface = ifaces.nextElement();
        Enumeration<InetAddress> addresses = iface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            
            System.out.println ("-main loopback----");
            System.out.println ("main addr.getHostAddress() = " + addr.getHostAddress());
            //System.out.println ("main addr.getHostName() = " + addr.getHostName());
            //System.out.println ("main addr.isAnyLocalAddress() = " + addr.isAnyLocalAddress());
            //System.out.println ("main addr.isLinkLocalAddress() = " + addr.isLinkLocalAddress());
            //System.out.println ("main addr.isLoopbackAddress() = " + addr.isLoopbackAddress());
            //System.out.println ("main addr.isMulticastAddress() = " + addr.isMulticastAddress());
            //System.out.println ("main addr.isSiteLocalAddress() = " + addr.isSiteLocalAddress());

            if (addr instanceof Inet4Address && addr.isLoopbackAddress()) {
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
   
   static public int getfile(String sFullPath, String sStorePath, int _tries, long _timer, int _timeout) throws IOException {

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
                    _timer += 500;
                    Thread.sleep(_timer);
                }
            }
            return nres;
        } catch (Exception e) {
            p("exception getfile(). exiting...");
            e.printStackTrace();
            return -1;
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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.NetUtils-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.NetUtils-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [CS.NetUtils-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    static protected void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.netutils_" + threadID + "] " + s);
    }
    
    static public String getMode() {
        String mode = getConfig("mode", "config/www-rtbackup.properties");
        return mode;        
    }
    
    static public String getSignature() {
        String signature = getConfig("signature", "config/www-rtbackup.properties");
        return signature;
        
    }
    
    static public String getComputerName(boolean bWindowsServer) {        
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
                        computername = computername.substring(0,computername.indexOf("."));
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
    
    public static int UpdateConfig(String _name, String _value, String _config) {
        
        try {
            
            Properties props = new Properties();
    
            File f = new File(_config);           
            if (!f.exists()) {
                PrintWriter out = new PrintWriter(_config);
                out.println(" ");
                out.close();
            }
           
            f = new File(_config);
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
    
    public static String getConfig(String _name, String _config) {
        
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
    
    static public String getUUID(String _UUIDPath) {
            File f = null;
            FileInputStream fis = null;
            FileOutputStream fos = null;
            
            String sUUID = "";
            
            try {
                f = new File(_UUIDPath);

                if (f.exists()) {
                    fis = new FileInputStream(f);
                    Scanner scanner2 = new Scanner(fis);
                    sUUID = scanner2.nextLine();                                    
                    p("UUID exists = " + sUUID);
                } else {
                    fos = new FileOutputStream(f);
                    UUID newUUID = UUID.randomUUID();
                    sUUID = newUUID.toString();
                    fos.write(sUUID.getBytes());                                        
                    p("saved new UUID = " + sUUID);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (fis != null) fis.close();
                    if (fos != null) fos.close();                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return sUUID;
            }

    }
    
    static public int copyfile (String _source, String _dest) {
        
        try {
            InputStream is = new FileInputStream(_source);
            File fd = new File(_dest);
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
    
    public static String calcMD5(String _filename) {
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
            p("Exiting due to OOM in calcMD5.");
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
    
        public static ArrayList<String> getAllServerAddressPortProbe(int _retries, int howMany, int time, boolean __addip) throws InterruptedException {
           ArrayList<String> result = new ArrayList<String>();
           int PORT = 1234;
           byte[] recieveData = new byte[100];
           DatagramSocket clientSocket = null;
           DatagramPacket recievePacket = null;
           int nRetries = 0;
           int nFound = 0;

   
           while (nRetries < _retries && nFound < howMany) {
                nRetries++;

                try {
                    
                    clientSocket = new DatagramSocket(PORT);
                    recievePacket = new DatagramPacket(recieveData, recieveData.length);

                    p("Checking for probe. Attempt #" + nRetries);       
                    clientSocket.setSoTimeout(time);
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
                    if(!result.contains(sSignature)){
                        if (!__addip) {
                            result.add(sSignature);
                        } else {
                            result.add(sSignature + "," + sHostIP);
                        }
                    }
                    nFound++;
                    

 
                } catch (BindException e) {
                    p("socket in use.");
                    Random generator = new Random();
                    int roll = generator.nextInt(1000);
                    Thread.sleep(1000 + roll);
                    
                } catch (SocketTimeoutException e) {
                    p("socket timeout.");
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
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
           
           return result;

        }
        
        
        
        public static ArrayList<String> getServerAddressPortProbe(String servername, int _retries, int time) throws InterruptedException {
           int PORT = 1234;
           byte[] recieveData = new byte[100];
           DatagramSocket clientSocket = null;
           DatagramPacket recievePacket = null;
           int nRetries = 0;

   
           while (nRetries < _retries) {
                nRetries++;

                try {
                    
                    clientSocket = new DatagramSocket(PORT);
                    recievePacket = new DatagramPacket(recieveData, recieveData.length);

                    p("Checking for probe. Attempt #" + nRetries);       
                    clientSocket.setSoTimeout(time);
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
                    if(servername.equals(sSignature)){
                        ArrayList<String> result = new ArrayList<String>();
                        result.add(sSignature);
                        result.add(sHostIP);
                        result.add(sHostPort);
                        return result;
                    }
                    

 
                } catch (BindException e) {
                    p("socket in use.");
                    Random generator = new Random();
                    int roll = generator.nextInt(1000);
                    Thread.sleep(1000 + roll);
                    
                } catch (SocketTimeoutException e) {
                    p("socket timeout.");
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
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
           
           return null;

        }
        
public static String readFileIntoString(String file) {
                
        BufferedInputStream in = null;
        byte[] buf = new byte[1024*1024];
        ByteBuffer buffer = new ByteBuffer();
        
        try {
            p("readDoc()");
            File fh = new File(file);
            if (fh.exists()) {
                p("file exists");
                in = new BufferedInputStream(new FileInputStream(file));                                
                int len;
                while ((len = in.read(buf)) != -1) {
                    buffer.put(buf, len);
                }
                in.close();                
                return new String(buffer.buffer, 0, buffer.write);               
            } else {
                p("file NOT exists: " + fh.getCanonicalPath());
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
    
    static class ByteBuffer {

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
            
}

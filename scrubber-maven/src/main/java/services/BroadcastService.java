/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package services;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Properties;
import utils.HTTPRequestPoster;
import utils.NetUtils;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

public class BroadcastService implements Runnable {
   
    Thread t;
    Thread t2;
    String mNodeSignature = "cassprod";
    static String mLocalPort = "1001";
    String mClusterId;
    protected static Properties props = new Properties();

    boolean mTerminated = false;

    static boolean bConsole = true;

    public BroadcastService(String _signature) {
        
        
      mNodeSignature = _signature;
      
      t = new Thread(this, "sc_r");
      p("Child thread: " + t);
      t.start(); // Start the thread
      t2 = new Thread(new RunProbeFromBroadcast(), "sc_r2");
      p("Child thread: " + t);
      t2.start();
        
    }
    
    public void terminate() {
       
      p("Recieved Termination request.");
       mTerminated = true;       
   }
    
    public void run() {
        try {
                        
            loadProps();
            int i = 0;

 
            while (!mTerminated) {
 
                p("looking for local IP...");
                InetAddress localIP = NetUtils.getLocalAddressNonLoopback2();
                if (localIP != null) {
                    p("local ip[1] = " + localIP.getHostAddress());                                
                } else {
                    localIP = InetAddress.getLocalHost(); 
                    if (localIP != null) {
                        p("local ip[2] = " + localIP.getHostAddress());                                                    
                    }
                }

                mNodeSignature = NetUtils.getSignature();
                String mMode = NetUtils.getMode();
                
                if (localIP != null && mMode.equals("server")) {
                      p("Doing broadcast. local address = " + localIP.getHostAddress());

                      int PORT = 1234;

                      DatagramSocket ss = new DatagramSocket();

                      ss.setBroadcast(true);
                      byte[] b = new byte[100];

                      DatagramPacket p = new DatagramPacket(b, b.length);
                      p.setAddress(InetAddress.getByAddress(new byte[] {(byte)255,(byte)255,(byte)255,(byte)255}));
                     // p.setAddress(InetAddress.getByAddress(new byte[] {(byte)192,(byte)168,(byte)1,(byte)139}));
                      p.setPort(PORT);

                      String s = new Integer(i++).toString() + "," + mNodeSignature + "," + localIP.getHostAddress() + "," + mLocalPort + "," + mClusterId + ",";
                      p("sending probe #:" + i + "'" + s + "'");
                      b = s.getBytes();
                      p.setData(b);
                      try {
                          ss.send(p);
                      } catch (IOException e) {
                          e.printStackTrace();
                          pw("WARNING: there was an error sending the probe.");
                      }
                      p("done send probe." + localIP.getHostAddress());

                  } else {
                      if (localIP == null) {
                          localIP = NetUtils.getLocalAddressLoopback();
                          p("Skipping broadcast: loopback mode. IP: " + localIP);                          
                      }
                      if (!mMode.equals("server")) {
                          p("Skipping broadcast. In Client mode");                          
                      }
                  }
                  Thread.sleep(5000);
            }           
        } catch (Exception e) {
            e.printStackTrace();
        }    
    }
    
    private class RunProbeFromBroadcast implements Runnable{

        @Override
        public void run() {
            try {
                        
                loadProps();
                int i = 0;


                while (!mTerminated) {

                    int PORT_FROM = 1233;
                    byte[] recieveData = new byte[100];

                    DatagramSocket clientSocket = null;
                    DatagramPacket recievePacket = null;

                    InetAddress ipaddress=null;

                    try {
                          //p("[before new socket]");  
                          clientSocket = new DatagramSocket(PORT_FROM);
                          //p("[before new packet]");  
                          recievePacket = new DatagramPacket(recieveData, recieveData.length);       

                          clientSocket.setSoTimeout(10000);
                          clientSocket.receive(recievePacket);

                          ipaddress = recievePacket.getAddress();
                    }catch(Throwable th){
                         pw("WARNING: there was an exception in run()");
                         th.printStackTrace();
                    }
                    
                    clientSocket.close();
                    
                    if(ipaddress!=null){
                        int sendCount=0;
                        while(sendCount<5){
                            sendCount++;
                            p("looking for local IP...");
                            InetAddress localIP = NetUtils.getLocalAddressNonLoopback2();
                            if (localIP != null) {
                                p("local ip[1] = " + localIP.getHostAddress());                                
                            } else {
                                localIP = InetAddress.getLocalHost(); 
                                if (localIP != null) {
                                    p("local ip[2] = " + localIP.getHostAddress());                                                    
                                }
                            }

                            mNodeSignature = NetUtils.getSignature();
                            String mMode = NetUtils.getMode();

                            if (localIP != null && mMode.equals("server")) {
                                  p("Doing broadcast. local address = " + localIP.getHostAddress());

                                  int PORT = 1234;

                                  DatagramSocket ss = new DatagramSocket();

                                  ss.setBroadcast(true);
                                  byte[] b = new byte[100];

                                  DatagramPacket p = new DatagramPacket(b, b.length);
                                  p.setAddress(ipaddress);
                                 // p.setAddress(InetAddress.getByAddress(new byte[] {(byte)192,(byte)168,(byte)1,(byte)139}));
                                  p.setPort(PORT);

                                  String s = new Integer(i++).toString() + "," + mNodeSignature + "," + localIP.getHostAddress() + "," + mLocalPort + "," + mClusterId + ",";
                                  p("sending probe #:" + i + "'" + s + "'");
                                  b = s.getBytes();
                                  p.setData(b);
                                  try {
                                      ss.send(p);
                                  } catch (IOException e) {
                                      e.printStackTrace();
                                      pw("WARNING: there was an error sending the probe.");
                                  }
                                  p("done send probe." + localIP.getHostAddress());

                              } else {
                                  if (localIP == null) {
                                      localIP = NetUtils.getLocalAddressLoopback();
                                      p("Skipping broadcast: loopback mode. IP: " + localIP);                          
                                  }
                                  if (!mMode.equals("server")) {
                                      p("Skipping broadcast. In Client mode");                          
                                  }
                              }
                            
                             Thread.sleep(200);
                            }   

                        }
                    }           
            } catch (Exception e) {
                e.printStackTrace();
            }    
        }
        
    }
    
    
    void loadProps() throws IOException {
        //p(System.getProperty("java.home"));
        p("loadProps()");
        File f = new File
                (
                ".." +
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
        mClusterId = getClusterID();

    }
    
    
    private String getClusterID() {
        String clusteridUUIDPath = "data/clusterid";
        String clusteridUUID = NetUtils.getUUID(clusteridUUIDPath);
        p("clusteridUUID = " + clusteridUUID);
        return clusteridUUID;
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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [BroadcastService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO] [BroadcastService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [BroadcastService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    static protected void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [BroadcastService_" + threadID + "] " + s);
    }

}

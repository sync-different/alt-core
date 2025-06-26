/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */

package services;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

public class VaultService implements Runnable {
   
    Thread t;
    String mNodeSignature = "cassprod";
    boolean mTerminated = false;
    String HostName = "";
    int HostPort;
    long lDelay;

    PrintStream log = null;
    String LOG_NAME = "logs/vault.txt";

    
    public VaultService(Boolean _dothread, String _signature, String _hostname, int _hostport, long _delay) {
        
        
      mNodeSignature = _signature;
      HostName = _hostname;
      HostPort = _hostport;
      lDelay = _delay;
      
      if (_dothread) {
        t = new Thread(this, "sc_v");
        System.out.println("Child thread: " + t);
        t.start(); // Start the thread          
      }   
      
      
    }
    
    public void terminate() {
       
       log("Recieved Termination request.");
       mTerminated = true;       
   }
    
    public void run() {
        try {
            
            log = new PrintStream(new BufferedOutputStream(
                    new FileOutputStream(LOG_NAME,true)));
            log("opening log file: " + LOG_NAME);                        
            
            while (!mTerminated) {
                go_tcp_client2(HostName, HostPort);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }    
    }
    
    void go_tcp_client2(String _hostname, int _hostport) throws Exception {
        {


            p("Connecting to " + _hostname + " : " + _hostport);
            
            
            while (true) {
                    try {
                        Socket clientSocket = new Socket(_hostname, _hostport);
                        
                        log("Connected. Waiting for request"); 
                        //DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, _hostport);
                        //clientSocket.send(sendPacket);

                        //DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        //clientSocket.receive(receivePacket);
                        //String modifiedSentence = new String(receivePacket.getData());


                        //byte[] sendData = new byte[1024];
                        byte[] receiveData = new byte[1024];

                        clientSocket.getInputStream().read(receiveData);
                        String sentence = new String(receiveData);

                        log("FROM SERVER:" + sentence);
                        log("LENGTH FROM SERVER:" + sentence.length());
                        
                        String sentence2 = sentence.substring(0,sentence.indexOf("\0"));

                        log("LENGTH FROM SERVER:" + sentence2.length());

                        log("DECRYPTING FROM SERVER:" + sentence2);
                        byte[] s2 = Base64.decode(sentence2.toCharArray());
                        String sPathDec = new String(s2);                        
                        
                        log("DECRYPTED FROM SERVER:" + sPathDec);                        
                        String s1 = URLDecoder.decode(sPathDec, "UTF-8");
                        log("URL DECODED FROM SERVER:" + s1);

                        PrintStream ps = new PrintStream(clientSocket.getOutputStream());
                        File f = new File(s1);

                        if (f.exists()) {
                            sendFile(f, ps);
                        } else {
                            sendFile (new File("outgoing/404.txt"), ps);
                        }

                        receiveData = null;

                    } catch (Exception e) {
                        p("Exception...Sleeping for " + lDelay + "ms");
                        Thread.sleep(lDelay);
                    }
                    

            }
            
            //String capitalizedSentence = sentence.toUpperCase();
            //sendData = capitalizedSentence.getBytes();            
            //System.out.println("Sending response packet.");      
            //clientSocket.getOutputStream().write(sendData);
            
            //clientSocket.close();
         }
    }
    
    void sendFile(File targ, PrintStream ps) throws IOException {
        
        byte[] buf;
        final int BUF_SIZE = 2048000;
        
        
        buf = new byte[BUF_SIZE];

        InputStream is = null;
        log("sendFile(): '" + targ.getAbsolutePath() + "'");
        is = new FileInputStream(targ.getAbsolutePath());

        try {
            int n;
            while ((n = is.read(buf)) > 0) {
                ps.write(buf, 0, n);
            }
            ps.close();
            is.close();
        } catch (Exception e) {
            p("   *** WARNING *** Exception during sendfile: " + e.getMessage());
        } finally {
            is.close();
        }
    }
    
    /* print to the log file */
    protected void log(String s) {

        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        synchronized (log) {
            p(sDate + " " + s);
            log.println(sDate + " " + s);
            log.flush();
        }
    }
    
    /* print to stdout */
    protected void p(String s) {

        long threadID = Thread.currentThread().getId();
        System.out.println("[backup_vault_" + threadID + "] " + s);
    }
    
    
   
    
}

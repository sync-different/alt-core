/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */

package javaapplication1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
//import java.io.ByteArrayOutputStream;

import java.io.FileInputStream;
import java.io.File;

import java.net.InetSocketAddress;
import java.net.URI;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Main {

  public static void main(String[] args) throws IOException {
    try {
           InetSocketAddress addr = new InetSocketAddress(8080);

            HttpServer server = HttpServer.create(addr, 0);

            //Base64 pp = new Base64();

            server.createContext("/", new MyHandler());
            server.setExecutor(Executors.newCachedThreadPool());
           //server.setExecutor(null);
            server.start();
            System.out.println("HTTP Server is listening on port 8080" );

            String sHello = "/Users/carolineloy/Pictures/iPhoto Library/Data.noindex/2007/Nov 13, 2007/IMGP0351.jpg/";
            String s = Base64.encodeToString(sHello.getBytes(),false);
            System.out.println("encoded = '" + s + "'");

            //byte[] s2 = Base64.decode(s.toCharArray());
            //String s3 = new String(s2);
            //System.out.println("decoded = '" + s3 + "'");
      }
      catch (IOException ex){
            System.out.println("IO exception in main thread. Exiting");
            System.out.println(ex.getMessage());
            System.out.println(ex.getLocalizedMessage());
            System.exit(0);
      }
      catch (Exception ex){
            System.out.println("Exception in main thread. Exiting");
            System.out.println(ex.getMessage());
            System.out.println(ex.getLocalizedMessage());
            System.exit(0);
      }
  }
}

class MyHandler implements HttpHandler {


  public void handle(HttpExchange exchange) throws IOException {

      byte[] data;
      //byte[] data2;
      long freeMem;
      Runtime r = Runtime.getRuntime();

      try {
    //Base64 pp = new Base64();
      boolean isPartial = false;
      boolean untilEOF = false;
      boolean bForcePartial = false;
      boolean bLast = false;
      boolean bIcy = false;
      boolean bIpad = false;
      boolean bModified = false;
      int nStart = 0;
      int nEnd = 0;
      String sRange = "";

    //STEP 1 - GetRequestmethod

      String requestMethod = exchange.getRequestMethod();
      System.out.println("xx------------------------------------");
      freeMem = r.freeMemory();
      System.out.println("* free mem_start: " + freeMem);
      System.out.println("1. RequestMethod: '" + requestMethod + "'");

      if (requestMethod.equalsIgnoreCase("GET")) {

      //STEP 2 - GetRequestHeaders
      System.out.println("2. getRequestHeaders");

      Headers requestHeaders = exchange.getRequestHeaders();

      Set<String> keySet = requestHeaders.keySet();
      Iterator<String> iter = keySet.iterator();

      while (iter.hasNext()) {
        String key = iter.next();
        List values = requestHeaders.get(key);
        String s = key + " = " + values.toString() + "\n";
        System.out.println(s);
        if (key.equals("If-Modified-Since")) {
            bModified = true;
            System.out.println("2a. Modified detected");
        }
        if (key.equals("User-agent")) {
            int nPos = values.toString().indexOf("iPad");
            if (nPos > 0)
                bIpad = true;
                System.out.println("2a. Ipad detected");
        }
        if (key.equals("Icy-metadata")) {
            System.out.println("2a. Requested Icy Metadata");
            bIcy = true;
        }
        if (key.equals("Range")) {
            System.out.println("2a. Requested Partial Range");
            sRange = values.toString();
            isPartial= true;
            int nPos1 = values.toString().indexOf("=");
            int nPos2 = values.toString().indexOf("-");

            String sStart = values.toString().substring(nPos1+1, nPos2);
            nStart = Integer.parseInt(sStart);

            String sEnd = values.toString().substring(nPos2+1, values.toString().length()-1);
            if (nPos2+1 == values.toString().length()-1) {
                //System.out.println("-------EOF MODE!!!");
                untilEOF = true;
            } else {
                //specific range requested
                untilEOF = false;
                nEnd = Integer.parseInt(sEnd);
            }
            
            
            System.out.println("   start = '" + nStart + "'");
            if (untilEOF) {
                System.out.println("   end = '<EOF>'");
            } else {
                System.out.println("   end = '" + nEnd + "'");
            }
            //System.out.println("untilEOF = " + untilEOF);
        }
      }


     //Step 3 - getRequest Body
      System.out.println("3. getRequestBody");

      InputStream requestBody = exchange.getRequestBody();

      int c;
      int nbody = 0;
      while ((c = requestBody.read()) != -1) {
           nbody++;
      }

      System.out.println(requestBody.toString());

      System.out.println("   Read body #bytes: '" + nbody + "'");
      requestBody.close();


      System.out.println("3b. Open File");

      URI mURI = exchange.getRequestURI();

      String sURI = mURI.getPath();
      System.out.println("Encoded URI Path: '" + sURI + "'");
      String sPathEnc = sURI.substring(1, sURI.length());
      System.out.println("Encoded Path: '" + sPathEnc + "'");

      byte[] s2 = Base64.decode(sPathEnc.toCharArray());
      String sPathDec = new String(s2);

      //String sPathDec = "/var/www/" + sPathEnc;

      System.out.println("decoded = '" + sPathDec + "'");

      String sFullPath = sPathDec;
      System.out.println("   FullPath:" + sFullPath);

      System.out.println("   [1]");
      InputStream in = new FileInputStream(sFullPath);
      System.out.println("   [2]");
      //ByteArrayOutputStream out = new ByteArrayOutputStream();
      System.out.println("   [3]");

      //int b;
      //int i = 0;
      //int ncount = 0;

      File pp = new File(sFullPath);
      long nlen = pp.length();
      System.out.println("   [file length: " + nlen + "]");

      if (untilEOF)
            nEnd = (int)nlen - 1;

      if (!isPartial) {
          //full mode
          nStart = 0;
          nEnd = (int)nlen  - 1;
      }

      System.out.println("   [offset start: " + nStart + "]");
      System.out.println("   [offset end: " + nEnd + "]");

      if (nEnd == nlen) {
          bLast = true;
      }

//      data = new b1yte[(int)nlen];

//      int nSize = 0;
//      if (isPartial)
        int nSize = nEnd - nStart + 1;
//      else
//           nSize = nEnd - nStart;

      data = new byte[nSize];
      int offset = nStart;
      int numRead = 0;

      while (offset < data.length && (numRead = in.read(data, offset, data.length-offset)) >= 0) {
          offset += numRead;
      }

      System.out.println("   done reading file #bytes: " + offset);

//      int nSize = 0;
//      if (isPartial)
//          nSize = nEnd - nStart + 1 ;
//      else
          nSize = nEnd - nStart;

//      System.out.println("   Buffer nSize: " + nSize);

      //data2 = new byte[0];
//      if (nSize == 1) {
//          bSkip = true;
//        }
//      } else {
//          if (nSize < data.length) {
//              ///there is stuff to copy
//              System.out.println("there is stuff to copy: " + nSize);
//              System.out.println("nStart: " + nStart);
//
//              data2 = new byte[nSize];
//              for (i = 0; i<nSize; i++) {
//                  //System.out.println("to" + i + "from " + nStart + " " + i);
//                  data2[i] = data[nStart+i];
//              }
//              System.out.println("-----copied #bytes: " + nSize);
//          } else {
//            // full copy
//            data2 = data;
//            System.out.println("-----size of data2 #bytes: " + data2.length);
//          }
//      }

      //data = null;
      
      //STEP 4 - GetResponse Headers
      System.out.println("4. getResponseHeaders");

      Headers responseHeaders = exchange.getResponseHeaders();
      responseHeaders.clear();
      
      if (isPartial) {
            responseHeaders.set("Accept-Ranges", "bytes");
            //responseHeaders.set("Content-Length", Integer.toString(nSize));
            responseHeaders.set("Content-Range",sRange.substring(1,sRange.length()-1) + "/" + nlen);
            responseHeaders.set("Keep-Alive","timeout=15, max=99");
            responseHeaders.set("Connection","Keep-Alive");
            responseHeaders.set("Content-Type", "audio/mpeg");
            //responseHeaders.set("Connection","close");
            responseHeaders.set("Date", "Wed, 09 Feb 2011 17:39:55 GMT");
            responseHeaders.set("Last-Modified", "Sat, 05 Feb 2011 21:52:21 GMT");
      } else {
            if (sPathDec.contains("mp3")) {
                responseHeaders.set("Content-Type", "audio/mpeg");
            }
      }

      Set<String> keySetr = responseHeaders.keySet();
      Iterator<String> iter2 = keySetr.iterator();
      while (iter2.hasNext()) {
            String key = iter2.next();
            List values = responseHeaders.get(key);
            String s = key + " = " + values.toString() + "\n";
            System.out.println(s);
      }
      
      System.out.println("----  RESPONSE HEADERS END ");


      //if (data2.length == nlen) {
      //    isPartial = false;
      //}


      //STEP 5 - Send response Headers
      System.out.println("5. setResponseHeaders");

      if (isPartial) {
          if (bLast) {
              System.out.println("-----RESPONSE: 304 nlen: " + data.length);
              exchange.sendResponseHeaders(304, data.length);
          } else {
              if (!bModified) {
                    System.out.println("-----RESPONSE: 206 nlen: " + data.length);
                    exchange.sendResponseHeaders(206, data.length);
              } else {
                    System.out.println("-----RESPONSE: 304b");
                    exchange.sendResponseHeaders(304, 0);
              }
          }
      } else {
//            if (bIpad) {
//                //try partial send
//                bForcePartial = true;
//                System.out.println("-----RESPONSE: 206 nlen: 65536");
//                exchange.sendResponseHeaders(206, 65536);
//            } else {
                //normal full mode
                System.out.println("-----RESPONSE: 200 nlen:" + data.length);
                exchange.sendResponseHeaders(200, data.length);
            //}
      }

      
      //while ((b = in.read()) != -1) {
      //    if (isPartial) {
      //          if (!untilEOF) {
      //              if (i>=nStart && i<=nEnd) {
      //                  ncount++;
      //                  out.write(b);
      //              }
      //         } else {
      //              if (i>=nStart) {
      //                  ncount++;
      //                  out.write(b);
      //              }
      //        }
      //    } else {
      //          ncount++;
      //          out.write(b);
      //    }
      //    i++;
      //}

      //System.out.println("Sent Length:" + ncount);
      //byte[] data = out.toByteArray();

      //STEP 6 - Get response Body
      System.out.println("6. getResponseBody");

      OutputStream responseBody = exchange.getResponseBody();

      if (!bForcePartial && !bModified) {
          System.out.println("   writing data length:" + data.length);
          responseBody.write(data);
          System.out.println("   done write data");
      }
//      else {
//          byte[] data2 = new byte[65536];
//          System.arraycopy(data, 0, data2, 0, 65336);
//          responseBody.write(data2);
//      }


      
      System.out.println("   before responsebody.flush");
      responseBody.flush();
      responseBody.close();
      System.out.println("   done responseBody.flush");
      //data2 = null;
      
      System.out.println("   exchange.close");
      exchange.close();

      //data = null;

      System.out.println("   done, returning.");

      freeMem = r.freeMemory();
      System.out.println("* free mem_end: " + freeMem);

      return;

      //if (bLast) {
      //  responseBody.close();
      //  exchange.close();
      //}
      //System.out.println("-----done responseBody.close");
      //in.close();
      //System.out.println("-----done file close");
      //Iterator<String> iter = keySet.iterator();
      //while (iter.hasNext()) {
      //  String key = iter.next();
      //  List values = requestHeaders.get(key);
      //  String s = key + " = " + values.toString() + "\n";
      //  responseBody.write(s.getBytes());
      //}
      //responseBody.close();
    }
  }
      catch(IOException ex) {
      System.out.println("exception");
      System.out.println(ex.getMessage());
      System.out.println(ex.getLocalizedMessage());
      data = null;
      //data2 = null;

  } catch (Exception ex) {
      System.out.println("exception!!!!");
      System.out.println(ex.getMessage());
      System.out.println(ex.toString());
      data = null;
      //data2 = null;
      
  } finally {
      freeMem = r.freeMemory();
      System.out.println("free mem1: " + freeMem);
      data = null;
      freeMem = r.freeMemory();
      System.out.println("free mem2: " + freeMem);
      r.gc();
      freeMem = r.freeMemory();
      System.out.println("free mem3: " + freeMem);
    }
  }
}

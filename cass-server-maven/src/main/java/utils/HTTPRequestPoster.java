/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package utils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.io.BufferedReader;
import java.net.HttpURLConnection;

import javax.net.ssl.HttpsURLConnection;

import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.io.IOException;

import java.io.FileWriter;
import java.io.FileReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import java.net.SocketTimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import utils.Stopwatch;

import org.apache.commons.httpclient.*;
//import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;

import org.apache.hc.client5.http.classic.methods.HttpPost;
//import org.apache.hc.client5.http.classic.methods.HttpGet;

import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;

//import org.apache.hc.client5.http.*;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ContentType;

//import org.apache.hc.core5.http.ClassicHttpRequest;


        
public class HTTPRequestPoster {
    
public static void main(String[] args) throws Exception {
    
    Writer writer = new FileWriter("file-output.txt");
    Reader reader = new FileReader("macbookair.jpeg");
    File fh = new File("macbookair.jpeg");
    InputStream fis = new FileInputStream(fh);    
    URL oracle = new URL("http://10.211.55.3:80/ppp");
    postData(fis,oracle,writer);
    
}
    
/**
* Sends an HTTP GET request to a url
*
* @param endpoint - The URL of the server. (Example: " http://www.yahoo.com/search")
* @param requestParameters - all the request parameters (Example: "param1=val1&param2=val2"). Note: This method will add the question mark (?) to the request - DO NOT add it yourself
* @return - The response from the end point
*/
public static String sendGetRequest_old(String endpoint, String requestParameters)
{
String result = null;
if (endpoint.startsWith("http://"))
{
// Send a GET request to the servlet
try
{
// Construct data
StringBuffer data = new StringBuffer();

// Send data
String urlStr = endpoint;
if (requestParameters != null && requestParameters.length () > 0)
{
urlStr += "?" + requestParameters;
}
URL url = new URL(urlStr);
URLConnection conn = url.openConnection ();

// Get the response
BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
StringBuffer sb = new StringBuffer();
String line;
while ((line = rd.readLine()) != null)
{
sb.append(line);
}
rd.close();
result = sb.toString();
} catch (Exception e)
{
e.printStackTrace();
}
}
return result;
}

/**
* Reads data from the data reader and posts it to a server via POST request.
* data - The data you want to send
* endpoint - The server's address
* output - writes the server's response to output
* @throws Exception
*/

public static int postData_new2(File fh, String endpoint, Writer output) {
    final HttpPost httpPost = new HttpPost(endpoint);
    final MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    
    builder.addBinaryBody(
        "file", new File(fh.getAbsolutePath()), ContentType.APPLICATION_OCTET_STREAM, fh.getName());
    final HttpEntity multipart = builder.build();

    httpPost.setEntity(multipart);

    try (CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response = (CloseableHttpResponse) client
            .execute(httpPost)) {

        final int statusCode = response.getCode();
        if (statusCode == HttpStatus.SC_OK) return 1;
        //assertThat(statusCode, equalTo(HttpStatus.SC_OK));
    } catch (IOException e) {
        e.printStackTrace();
        return 0;
    }
    return 0;
}


public static int postData_new(InputStream data, String endpoint, Writer output) throws Exception {
       PostMethod postFile = new PostMethod(endpoint);
       
       try {
                postFile.setRequestEntity(new InputStreamRequestEntity(data));
                //postFile.setRequestHeader("Content-type", "application/octet-stream; charset=UTF-8");
                postFile.setRequestHeader("Content-type", "application/octet-stream");

                HttpClient httpclient = new HttpClient();
                return httpclient.executeMethod(postFile);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                postFile.releaseConnection();
       }       
       return 0;
}

public static boolean postData(InputStream data, URL endpoint, Writer output) throws Exception {
    HttpURLConnection urlc = null;
    boolean bAllOK = false;
    
    try
    {        
    urlc = (HttpURLConnection) endpoint.openConnection();            
    try {
        urlc.setRequestMethod("POST");
        } catch (ProtocolException e) {
            throw new Exception("Shouldn't happen: HttpURLConnection doesn't support POST??", e);
        }
        urlc.setDoOutput(true);
        urlc.setDoInput(true);
        urlc.setUseCaches(false);
        urlc.setAllowUserInteraction(false);
        urlc.setRequestProperty("Content-type", "application/octet-stream; charset=" + "UTF-8");

        System.out.println("[0a]");
        int timeout = 30;
        
        System.out.println("timeout = " + timeout);
        urlc.setReadTimeout(timeout* 1000);  
        urlc.setConnectTimeout(timeout* 1000);  
                        
        urlc.connect();
        
        System.out.println("[0b]");
        
        OutputStream out = null;
        try {
            out = urlc.getOutputStream();
            //OutputStream writer = new OutputStreamWriter(out, "UTF-8");
            pipe(data, out);
            //writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("IOException while posting data");
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception while posting data");            
            return false;
        } finally {
            if (out != null) {
                out.close();
            }
        }

        System.out.println("[1]");

        InputStream in = null;
        try {
            System.out.println("[1a]");
            System.out.println("[1b]");
            in = urlc.getInputStream();
            System.out.println("[1c]");
            Reader reader = new InputStreamReader(in);
            System.out.println("[1d]");
            pipe2(reader, output);
            System.out.println("[1e]");
            reader.close();
        } catch (SocketTimeoutException e) {            
            System.out.println("Socket Timeout.");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            System.out.println("IOException while reading response.");
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.out.println("Unknown exception.");
            e.printStackTrace();
            return false;
        } finally {
            if (in != null) {
                in.close();
            }
        }
        System.out.println("[2]");
        bAllOK = true;

    } catch (IOException e) {
        System.out.println("Connection error (is server running at " + endpoint);
        e.printStackTrace();
        return false;
    } finally {
        if (urlc != null) {
            System.out.println("[3]");
            urlc.disconnect();
        } else {
            System.out.println("[3b]");  
        }
        if (bAllOK) {
            return true;
        } else {
            return false;
        }
            
    }
}

public boolean postDataHttps_new(InputStream data, String _relayHost, String _relayPort, boolean secure, Writer output, String _clusterId, String _clusterToken) {
    
        //PostMethod postFile = null;
        //HttpClient httpclient = null;
        
        System.out.println("postDataHttps_new --------");
        System.out.println("_relayhost: '" + _relayHost);
        System.out.println("_relayport: '" + _relayPort);
        System.out.println("_clusterID: '" + _clusterId);
        System.out.println("_cluterToken: '" + _clusterToken);
        
        try {
            String _protocol;
            if(secure){
                _protocol = "https";
            }else{
                _protocol = "http";
            }

            //String _relayHost = "abc.alterante.com";
            //String _relayPort = "443";
        
            String responseUrl = String.format("%s://%s:%s/clusters/%s/share?access-token=%s", 
                                               _protocol, _relayHost, _relayPort, _clusterId, _clusterToken); 
            
            System.out.println("ResponseURL = " + responseUrl);
            
            PostMethod postFile = new PostMethod(responseUrl);                

            postFile.setRequestEntity(new InputStreamRequestEntity(data));
            postFile.setRequestHeader("Content-type", "application/octet-stream");

            //StringRequestEntity requestEntity = new StringRequestEntity("*", "text", "UTF-8");
            //postFile.setRequestEntity(requestEntity);
            
            HttpClient httpclient = new HttpClient();
            
            int statusCode = httpclient.executeMethod(postFile);

            // Get the contents of the response
            String res = postFile.getResponseBodyAsString();
                       
            System.out.println("code = " + statusCode);
            System.out.println("res = " + res);
                                                
            if (statusCode == 200) {
                //all ok , write output 
                output.write(res);                
            }
            postFile.releaseConnection();                
            
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            } finally {
                System.out.println("FINALLY...");
                return true;                
            }            
        }

public static boolean postDataHttps(InputStream data, URL endpoint, Writer output) throws Exception {
    HttpsURLConnection urlc = null;
    boolean bAllOK = false;
    
    try
    {
    String ep = endpoint.toString();
    System.out.println("endpoint: " + ep);
    
    TrustManager[] trustAllCerts = new TrustManager[]{
    new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
        }
        public void checkClientTrusted(
            java.security.cert.X509Certificate[] certs, String authType) {
        }
        public void checkServerTrusted(
            java.security.cert.X509Certificate[] certs, String authType) {
        }
    }
    };            

    // Activate the new trust manager
    try {
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    } catch (Exception e) {
    }                   
    urlc = (HttpsURLConnection) endpoint.openConnection();

                
    try {
        urlc.setRequestMethod("POST");
        } catch (ProtocolException e) {
            throw new Exception("Shouldn't happen: HttpURLConnection doesn't support POST??", e);
        }
        urlc.setDoOutput(true);
        urlc.setDoInput(true);
        urlc.setUseCaches(false);
        urlc.setAllowUserInteraction(false);
        urlc.setRequestProperty("Content-type", "application/octet-stream; charset=" + "UTF-8");

        System.out.println("[0a]");
        int timeout = 30;
        
        System.out.println("timeout = " + timeout);
        urlc.setReadTimeout(timeout* 1000);  
        urlc.setConnectTimeout(timeout* 1000);  
                        
        urlc.connect();
        
        System.out.println("[0b]");
        
        OutputStream out = null;
        try {
            out = urlc.getOutputStream();
            //OutputStream writer = new OutputStreamWriter(out, "UTF-8");
            pipe(data, out);
            //writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("IOException while posting data");
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Exception while posting data");            
            return false;
        } finally {
            if (out != null) {
                out.close();
            }
        }

        System.out.println("[1]");

        InputStream in = null;
        try {
            System.out.println("[1a]");
            System.out.println("[1b]");
            in = urlc.getInputStream();
            System.out.println("[1c]");
            Reader reader = new InputStreamReader(in);
            System.out.println("[1d]");
            pipe2(reader, output);
            System.out.println("[1e]");
            reader.close();
        } catch (SocketTimeoutException e) {            
            System.out.println("Socket Timeout.");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            System.out.println("IOException while reading response.");
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.out.println("Unknown exception.");
            e.printStackTrace();
            return false;
        } finally {
            if (in != null) {
                in.close();
            }
        }
        System.out.println("[2]");
        bAllOK = true;

    } catch (IOException e) {
        System.out.println("Connection error (is server running at " + endpoint);
        e.printStackTrace();
        return false;
    } finally {
        if (urlc != null) {
            System.out.println("[3]");
            urlc.disconnect();
        } else {
            System.out.println("[3b]");  
        }
        if (bAllOK) {
            return true;
        } else {
            return false;
        }
            
    }
}


/**
* Pipes everything from the reader to the writer via a buffer
*/
private static void pipe(InputStream reader, OutputStream writer) throws IOException {
    byte[] buf = new byte[1024 * 1024 * 2]; // 2 MB
    int read = 0;
    
    int chunk = 0;
    
    while ((read = reader.read(buf)) >= 0) {
        writer.write(buf, 0, read);
        System.out.println("wrote chunk #: " + chunk);
        chunk++;
        try {
            Thread.sleep(200);
        } catch (Exception e) {
            
        }
    }
    writer.flush();
}
/**
* Pipes everything from the reader to the writer via a buffer
*/
private static void pipe2(Reader reader, Writer writer) throws IOException {
    char[] buf = new char[10240];
    int read = 0;
    
    System.out.println("Entering pipe2");
    while ((read = reader.read(buf)) >= 0) {
        writer.write(buf, 0, read);
    }
    writer.flush();
    buf = null;
    System.out.println("Leaving pipe2");
}

public static int sendGetRequest(String endpoint, String requestParameters, String outfileName, int nTimeout) throws FileNotFoundException
{
    byte[] data;
    boolean bSourceExists = false;
   
    System.out.println("***** sendGetRequest");
    
    String result = null;
    if (endpoint.startsWith("http://")) {
        // Send a GET request to the servlet
        try {
            // Construct data
            //StringBuffer data = new StringBuffer();
            // Send data
            String urlStr = endpoint;
            if (requestParameters != null && requestParameters.length () > 0)
                {
                urlStr += "?" + requestParameters;
                }
            URL url = new URL(urlStr);
            URLConnection conn = url.openConnection ();
            System.out.println("[a]");
            // Get the response

            try {

                Stopwatch timer = new Stopwatch().start();

                //System.out.println("(getfile) [b] timeout: " + nTimeout);
                conn.setReadTimeout(nTimeout);
                
                InputStream rd = conn.getInputStream();
                
                System.out.println("(getfile) [c]");
                
                System.out.println("(getfile) source file exists, dest file = '" + outfileName + "'");
                bSourceExists = true;

                FileOutputStream outFile = new FileOutputStream(outfileName);
                int numRead = 0;
                int total = 0;
                data = new byte[524288];

                int n;

                //System.out.println("(getfile) [0]");

                while ((numRead = rd.read(data)) >= 0) {
                    outFile.write(data,0,numRead);
                    total += numRead;
                }
                //StringBuffer sb = new StringBuffer();
                //String line;
                //while ((line = rd.readLine()) != null) {
                //    sb.append(line);
                //}
                //System.out.println("(getfile) [1]");
                rd.close();
                outFile.close();                
                result =  String.valueOf(total);
                timer.stop();
                
                data = null;

                //System.out.println("(getfile) [2]");
                if (total != 0) {
                    long elapsedtime = timer.getElapsedTime();
                    long speed = 0;
                    if (elapsedtime > 0) {
                        speed = total / (long) timer.getElapsedTime() * 1000 / 1024;
                    }
                    String sTime = "(getfile) Size: " + result + " Query time: " + timer.getElapsedTime() + " ms Speed: " + speed + "KB/s";
                    System.out.println(sTime);
                    return 1;
                } else {
                    System.out.println("(getfile) length == 0; returning error -2");
                    return -2;
                }
                
            } catch (FileNotFoundException e) {
                if (!bSourceExists) {
                    System.out.println("(getfile) Source File not found: '" + urlStr + "'");
                    return -1;
                } else {
                    System.out.println("(getfile) Unable to open dest file: " + outfileName);
                    return -3;
                }
            } catch (Exception e ) {
                System.out.println("(getfile) Other exception...");
                System.out.println(e.getMessage());
                e.printStackTrace();
                return -2;
            }

        } catch (IOException e) {
            System.out.println("(getfile) IOException");
            e.printStackTrace();
        }
    }
    return 0;
}


}
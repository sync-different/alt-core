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

import org.apache.hc.client5.http.classic.methods.HttpPost;
//import org.apache.hc.client5.http.classic.methods.HttpGet;

import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;

//import org.apache.hc.client5.http.*;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
//import org.apache.hc.core5.http.io.entity.StringRequestEntity;

//import org.apache.hc.core5.http.ClassicHttpRequest;

import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;

public class HTTPRequestPoster {

    // BEGIN ANSI
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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.HTTPRequestPoster-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.HTTPRequestPoster-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected static void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.HTTPRequestPoster_" + threadID + "] " + s);
    }

    // END ANSI
    
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

        p("[0a]");
        int timeout = 30;
        
        p("timeout = " + timeout);
        urlc.setReadTimeout(timeout* 1000);  
        urlc.setConnectTimeout(timeout* 1000);  
                        
        urlc.connect();
        
        p("[0b]");
        
        OutputStream out = null;
        try {
            out = urlc.getOutputStream();
            //OutputStream writer = new OutputStreamWriter(out, "UTF-8");
            pipe(data, out);
            //writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            pw("IOException while posting data");
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            pw("Exception while posting data");
            return false;
        } finally {
            if (out != null) {
                out.close();
            }
        }

        p("[1]");

        InputStream in = null;
        try {
            p("[1a]");
            p("[1b]");
            in = urlc.getInputStream();
            p("[1c]");
            Reader reader = new InputStreamReader(in);
            pw("[1d]");
            pipe2(reader, output);
            pw("[1e]");
            reader.close();
        } catch (SocketTimeoutException e) {            
            pw("Socket Timeout.");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            pw("IOException while reading response.");
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            pw("Unknown exception.");
            e.printStackTrace();
            return false;
        } finally {
            if (in != null) {
                in.close();
            }
        }
        p("[2]");
        bAllOK = true;
        if (bAllOK) {
            return true;
        } else {
            return false;
        }   
    } catch (IOException e) {
        pw("Connection error (is server running at " + endpoint);
        e.printStackTrace();
        return false;
    } finally {
        if (urlc != null) {
            p("[3]");
            urlc.disconnect();
        } else {
            p("[3b]");
        }            
    }
}

public boolean postDataHttps_new2(InputStream data, String _relayHost, String _relayPort, boolean secure, Writer output, String _clusterId, String _clusterToken) {
    
        p("postDataHttps_new2 --------");
        p("_relayhost: '" + _relayHost);
        p("_relayport: '" + _relayPort);
        p("_clusterID: '" + _clusterId);
        p("_cluterToken: '" + _clusterToken);
        
        try {
            String _protocol;
            if(secure){
                _protocol = "https";
            }else{
                _protocol = "http";
            }
        
            String responseUrl = String.format("%s://%s:%s/clusters/%s/share?access-token=%s", 
                                               _protocol, _relayHost, _relayPort, _clusterId, _clusterToken); 
            
            p("ResponseURL = " + responseUrl);
            
            final HttpPost httpPost = new HttpPost(responseUrl);
            
            // Use InputStreamEntity for raw binary data, not multipart
            InputStreamEntity entity = new InputStreamEntity(data, ContentType.APPLICATION_OCTET_STREAM);
            httpPost.setEntity(entity);
            httpPost.setHeader("Content-type", "application/octet-stream");

            // Use default client (SSL trust issues will need to be handled at system level)
            CloseableHttpClient client = HttpClients.createDefault();

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                final int statusCode = response.getCode();
                
                p("code = " + statusCode);
                
                // Read response body
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    String responseBody = EntityUtils.toString(responseEntity);
                    p("res = " + responseBody);
                    
                    // Write response to output
                    if (statusCode == HttpStatus.SC_OK) {
                        output.write(responseBody);
                        return true;
                    }
                }
                return false;
                
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            } finally {
                try {
                    client.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            p("FINALLY...");
        }
    }

public static boolean postDataHttps(InputStream data, URL endpoint, Writer output) throws Exception {
    HttpsURLConnection urlc = null;
    boolean bAllOK = false;
    
    try
    {
    String ep = endpoint.toString();
    p("endpoint: " + ep);
    
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

        p("[0a]");
        int timeout = 30;
        
        p("timeout = " + timeout);
        urlc.setReadTimeout(timeout* 1000);  
        urlc.setConnectTimeout(timeout* 1000);  
                        
        urlc.connect();
        
        p("[0b]");
        
        OutputStream out = null;
        try {
            out = urlc.getOutputStream();
            //OutputStream writer = new OutputStreamWriter(out, "UTF-8");
            pipe(data, out);
            //writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            pw("IOException while posting data");
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            pw("Exception while posting data");
            return false;
        } finally {
            if (out != null) {
                out.close();
            }
        }

        p("[1]");

        InputStream in = null;
        try {
            p("[1a]");
            p("[1b]");
            in = urlc.getInputStream();
            p("[1c]");
            Reader reader = new InputStreamReader(in);
            p("[1d]");
            pipe2(reader, output);
            p("[1e]");
            reader.close();
        } catch (SocketTimeoutException e) {            
            pw("Socket Timeout.");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            pw("IOException while reading response.");
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            pw("Unknown exception.");
            e.printStackTrace();
            return false;
        } finally {
            if (in != null) {
                in.close();
            }
        }
        p("[2]");
        bAllOK = true;
        if (bAllOK) {
            return true;
        } else {
            return false;
        }

    } catch (IOException e) {
        pw("Connection error (is server running at " + endpoint);
        e.printStackTrace();
        return false;
    } finally {
        if (urlc != null) {
            p("[3]");
            urlc.disconnect();
        } else {
            p("[3b]");
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
        p("wrote chunk #: " + chunk);
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
    
    p("Entering pipe2");
    while ((read = reader.read(buf)) >= 0) {
        writer.write(buf, 0, read);
    }
    writer.flush();
    buf = null;
    p("Leaving pipe2");
}

public static int sendGetRequest(String endpoint, String requestParameters, String outfileName, int nTimeout) throws FileNotFoundException
{
    byte[] data;
    boolean bSourceExists = false;
   
    p("***** sendGetRequest");
    
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
            p("[a]");
            // Get the response

            try {

                Stopwatch timer = new Stopwatch().start();

                //p("(getfile) [b] timeout: " + nTimeout);
                conn.setReadTimeout(nTimeout);
                
                InputStream rd = conn.getInputStream();
                
                p("(getfile) [c]");
                
                p("(getfile) source file exists, dest file = '" + outfileName + "'");
                bSourceExists = true;

                FileOutputStream outFile = new FileOutputStream(outfileName);
                int numRead = 0;
                int total = 0;
                data = new byte[524288];

                int n;

                //p("(getfile) [0]");

                while ((numRead = rd.read(data)) >= 0) {
                    outFile.write(data,0,numRead);
                    total += numRead;
                }
                //StringBuffer sb = new StringBuffer();
                //String line;
                //while ((line = rd.readLine()) != null) {
                //    sb.append(line);
                //}
                //p("(getfile) [1]");
                rd.close();
                outFile.close();                
                result =  String.valueOf(total);
                timer.stop();
                
                data = null;

                //p("(getfile) [2]");
                if (total != 0) {
                    long elapsedtime = timer.getElapsedTime();
                    long speed = 0;
                    if (elapsedtime > 0) {
                        speed = total / (long) timer.getElapsedTime() * 1000 / 1024;
                    }
                    String sTime = "(getfile) Size: " + result + " Query time: " + timer.getElapsedTime() + " ms Speed: " + speed + "KB/s";
                    p(sTime);
                    return 1;
                } else {
                    p("(getfile) length == 0; returning error -2");
                    return -2;
                }
                
            } catch (FileNotFoundException e) {
                if (!bSourceExists) {
                    pw("(getfile) Source File not found: '" + urlStr + "'");
                    return -1;
                } else {
                    pw("(getfile) Unable to open dest file: " + outfileName);
                    return -3;
                }
            } catch (Exception e ) {
                pw("(getfile) Other exception...");
                pw(e.getMessage());
                e.printStackTrace();
                return -2;
            }

        } catch (IOException e) {
            pw("(getfile) IOException");
            e.printStackTrace();
        }
    }
    return 0;
}


}
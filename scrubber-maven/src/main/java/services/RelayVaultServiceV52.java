/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package services;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;

import java.io.InputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;

//import org.apache.commons.httpclient.*;
//import org.apache.commons.httpclient.methods.*;

// HTTP v5.x imports for postFileResponse, postErrorResponse, postStringResponse, registerCluster, unregisterCluster
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.Header;

import java.util.*;

import net.minidev.json.*;

import java.net.URLEncoder;
import utils.LocalFuncs;

 import java.util.logging.Logger;
 import java.util.logging.Level;

/**
 *
 * @author guilespi
 * 
 * PARTIAL HTTP v5.x MIGRATION - REVERTED BACK TO WORKING STATE:
 * - postFileResponse (HTTP v5.x) ✅
 * - postErrorResponse (HTTP v5.x) ✅  
 * - postStringResponse (HTTP v5.x) ✅
 * - registerCluster (HTTP v5.x) ✅
 * - unregisterCluster (HTTP v5.x) ✅
 * - processRequests (HTTP v5.x) ✅
 * - httpLocalRequest (HTTP v3) ⭕ REVERTED - complex wrapper failed
 * 
 * WORKING STATE: Most functions migrated, core GET method still v3
 */
public class RelayVaultServiceV52 implements Runnable {
    
    boolean _terminated = true;
    
    private enum Operations {
        login, 
        file, 
        suggest, 
        query, 
        sidebar, 
        gettags, 
        applytags,
        doshare,
        getusersandemail, 
        adduser, 
        getvideo, 
        getts, 
        getaudio, 
        getsession,
        chat_pull,
        chat_push,
        gettags_webapp,
        savepropertymulticluster,
        getpropertymulticluster,
        getauthtoken,
        logout,
        getfolders_json
    }
    
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_RESET = "\u001B[0m";

    protected void pw(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);
        boolean bConsole = true;

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [SC.RelayVaultService-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    // Wrapper class to keep connection alive - shared between classes
    public class HttpConnectionWrapper {
        CloseableHttpClient client;
        CloseableHttpResponse response;
        
        HttpConnectionWrapper(CloseableHttpClient client, CloseableHttpResponse response) {
            this.client = client;
            this.response = response;
        }
        
        public void close() {
            try {
                if (response != null) response.close();
                if (client != null) client.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }

    /*
        ClusterDelegate just wraps cluster operations in a encapsulated class.
        If in the future cluster operations are to be run inside RT this class
        is the one to change.
    */
    public class ClusterDelegate {
        
        String _clusterHost, _clusterPort;
        
        ClusterDelegate(String host, String port) {
            _clusterHost = host;
            _clusterPort = port;
        }

        
        private HttpConnectionWrapper httpLocalRequest_new(String url, String stickyName, String stickyValue) {
                pw("   url getlocalrequest new: " + url);
                int connectionTimeout = 30000;
                int socketTimeout = 180000;

                // Configure timeouts and DISABLE redirect following to match v3 behavior
                RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionTimeout))
                    .setResponseTimeout(Timeout.ofMilliseconds(socketTimeout))
                    .setRedirectsEnabled(false)  // CRITICAL: v3 doesn't follow redirects, v5.x does by default
                    .build();
                    
                // Create minimal HttpClient to match v3 behavior exactly
                CloseableHttpClient httpclient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setUserAgent("Jakarta Commons-HttpClient/3.1")  // Match v3 user agent exactly
                    .build();
                    
                HttpGet httpGet = new HttpGet(url);

                if(stickyName != null){
                    httpGet.setHeader("Cookie", stickyName + "=" + stickyValue + ";");  // Match v3 cookie format exactly
                }
                
                // Remove all default headers to match v3 minimal header approach
                httpGet.removeHeaders("Accept");
                httpGet.removeHeaders("Accept-Encoding");
                httpGet.removeHeaders("Connection");
                
                // DEBUG: Log v5.x request headers after modification
                log("DEBUG V5.x - Request URL: " + url, 1);
                log("DEBUG V5.x - Request headers:", 1);
                for (org.apache.hc.core5.http.Header header : httpGet.getHeaders()) {
                    log("DEBUG V5.x - " + header.getName() + ": " + header.getValue(), 1);
                }
                
                try {
                    CloseableHttpResponse response = httpclient.execute(httpGet);
                    int statusCode = response.getCode();
                    pw("status code local request == " + statusCode);
                    
                    // DEBUG: Log response headers to see if there are any redirects
                    log("DEBUG V5.x - Response headers:", 1);
                    for (org.apache.hc.core5.http.Header header : response.getHeaders()) {
                        log("DEBUG V5.x - " + header.getName() + ": " + header.getValue(), 1);
                    }
                    
                    if (statusCode == 200) {
                        return new HttpConnectionWrapper(httpclient, response);
                    } else {
                        log("DEBUG V5.x - Non-200 status code: " + statusCode, 1);
                        response.close();
                        httpclient.close();
                    }
                } catch (IOException e) {
                    log("Local http request failed with IOException", 1);
                    e.printStackTrace(log);
                }
                return null;
        }

        private String httpLocalRequest_new_string(String url, String stickyName, String stickyValue) {
                pw("   url getlocalrequest new: " + url);
                int connectionTimeout = 30000;
                int socketTimeout = 180000;

                // Configure timeouts and DISABLE redirect following to match v3 behavior
                RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionTimeout))
                    .setResponseTimeout(Timeout.ofMilliseconds(socketTimeout))
                    .setRedirectsEnabled(false)  // CRITICAL: v3 doesn't follow redirects, v5.x does by default
                    .build();
                //GetMethod getFile = new GetMethod(urlgetfile);
                //HttpClient httpclient = new HttpClient();
                HttpGet httpGet = new HttpGet(url);
                httpGet.setConfig(requestConfig);

                if(stickyName != null){
                    //getFile.setRequestHeader("Cookie", "uuid=" + uuid);
                    httpGet.setHeader("Cookie", stickyName + "=" + stickyValue);
                }
                try (CloseableHttpClient httpclient = HttpClients.createDefault();
                        CloseableHttpResponse response = httpclient.execute(httpGet)) {
                    int statusCode = response.getCode();
                    pw("status code local request == " + statusCode);
                    if (statusCode == 200) {
                        HttpEntity responseEntity = response.getEntity();
                        String responseBody = EntityUtils.toString(responseEntity, "UTF-8");
                        return responseBody;
                    }
                } catch (IOException e) {
                    log("Local http request failed with IOException", 1);
                    e.printStackTrace();
                } catch (ParseException e) {
                    pw("Parse Exception");
                }
                return null;

        }
/*
            Locally executes a file request against running cluster RT server.
        */
        private void fileRequest_new(String requestId, String authCookie, String queryString, RelayBridge bridge, String StickyName, String StickyValue) {
            String url = String.format("http://%s:%s/cass/getfile.fn?%s", 
                                       _clusterHost, _clusterPort, queryString); 
            log("File request with url:" + url, 1);
            HttpConnectionWrapper connection = httpLocalRequest_new(url, StickyName, StickyValue);
            pw("Getfile url = " + url);
            try {
                if (connection != null && connection.response != null) {
                    pw("File get file OK.");
                    HttpEntity responseEntity = connection.response.getEntity();
                    if (responseEntity != null) {
                        pw("Entity get file OK.");
                        
                        // Debug: Log response headers that might affect content encoding
                        log("DEBUG V5.x - Content-Type: " + responseEntity.getContentType(), 1);
                        log("DEBUG V5.x - Content-Length: " + responseEntity.getContentLength(), 1);
                        log("DEBUG V5.x - Content-Encoding: " + (responseEntity.getContentEncoding() != null ? responseEntity.getContentEncoding() : "none"), 1);
                        
                        // Buffer the entire stream immediately to avoid InputStream lifecycle issues
                        InputStream fileStream = responseEntity.getContent();
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        byte[] data = new byte[8192];
                        int bytesRead;
                        long totalBytes = 0;
                        
                        // Debug: Log first few bytes to compare with v3
                        boolean isFirstChunk = true;
                        while ((bytesRead = fileStream.read(data)) != -1) {
                            if (isFirstChunk && bytesRead > 0) {
                                StringBuilder firstBytes = new StringBuilder("DEBUG V5.x - First 16 bytes: ");
                                for (int i = 0; i < Math.min(16, bytesRead); i++) {
                                    firstBytes.append(String.format("%02X ", data[i] & 0xFF));
                                }
                                log(firstBytes.toString(), 1);
                                isFirstChunk = false;
                            }
                            buffer.write(data, 0, bytesRead);
                            totalBytes += bytesRead;
                        }
                        
                        pw("Read " + totalBytes + " bytes from HTTP response");
                        log("DEBUG V5.x - Total bytes read: " + totalBytes, 1);
                        
                        // Debug: Log checksum of the data
                        byte[] allData = buffer.toByteArray();
                        try {
                            java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
                            byte[] hash = md5.digest(allData);
                            StringBuilder hashStr = new StringBuilder();
                            for (byte b : hash) {
                                hashStr.append(String.format("%02X", b));
                            }
                            log("DEBUG V5.x - MD5 hash: " + hashStr.toString(), 1);
                        } catch (Exception hashEx) {
                            log("DEBUG V5.x - Failed to compute hash: " + hashEx.getMessage(), 1);
                        }
                        
                        // Close connection immediately after reading all data
                        connection.close();
                        
                        // Create new InputStream from buffered data
                        java.io.ByteArrayInputStream bufferedStream = new java.io.ByteArrayInputStream(allData);
                        
                        // Use regular postFileResponse since we no longer need connection management
                        bridge.postFileResponse(requestId, bufferedStream, StickyName, StickyValue);
                    } else{
                        pw("WARNING: unable to request file-2");
                        bridge.postErrorResponse(requestId, "Unable to request local file-2");
                        connection.close();
                    }
                } else {
                    pw("WARNING: unable to request file");
                    bridge.postErrorResponse(requestId, "Unable to request local file");
                    if (connection != null) connection.close();
                }
            } catch (IOException e) {
                pw("WARNING: There was an exception in Vault getfile");
                e.printStackTrace();
                if (connection != null) connection.close();
            }
        }

        
        
/*
            Locally executes a standard relay request against running cluster RT server.
        */
        public void standardRequest_new(String requestId, 
                String function, 
                String authCookie, 
                String queryString, 
                RelayBridge bridge,
                String stickyName,
                String stickyValue) {
            String url = String.format("http://%s:%s/cass/%s?%s", 
                                       _clusterHost, _clusterPort, function, queryString); 
            if(url.contains("multicluster")){
                String g="";
            }
            log("Standard relayed request url:" + url, 1);
            //GetMethod response = httpLocalRequest_old(url, stickyName, stickyValue);
            pw("standard request = " + url);
            String responseBody = httpLocalRequest_new_string(url, stickyName, stickyValue);
            pw("after standard request = " + url);

            try {
                
                if (responseBody != null) {
                    pw("------- response is OK");
                    //HttpEntity responseEntity = response.getEntity();
                    //InputStream fileStream = responseEntity.getContent();
                    //String responseBody = fileStream.readAllBytes().toString();
                    //String responseBody = "";
                    //if (responseEntity != null) {
                    //    responseBody = EntityUtils.toString(responseEntity, "UTF-8");
                    //} else {
                    //    pw("WARNING: response entity is null");
                    //}

                    pw("standard request response body = " + responseBody);
                    
                    if(url.contains("query")){
                        String g="";
                    }
                    bridge.postStringResponse(requestId, responseBody, stickyName, stickyValue);
                } else {
                    pw("WARNING: response is null");
                    bridge.postErrorResponse(requestId, "Unable to relay local operation");
                }
            //} catch (ParseException e) {
            //    pw("ParseException");
            //    e.printStackTrace();
            //} catch (IOException e) {
            //    pw("IOException");
            //    e.printStackTrace();
            } catch (Exception e) {
                pw("Exception");
                e.printStackTrace();
            } finally {
                if (responseBody != null) {
                    //response.close();
                }
            }
        }
        
 
        public void fileRequestNetty_new(String requestId, 
                String function, 
                String authCookie, 
                String queryString, 
                RelayBridge bridge,
                String stickyName,
                String stickyValue) {
            
            pw("[file netty] querystring = " + queryString);

            String[] tokens = queryString.split("&");  
            Map<String, String> map = new HashMap<String, String>();  
            for (String param : tokens)  
            {  
                String name = param.split("=")[0];  
                String value = param.split("=")[1];  
                map.put(name, value);  
            }
            
            String md5 = map.get("md5");
            String uuid = map.get("uuid");

            pw("[file netty] md5 = " + md5);
            pw("[file netty] uuid = " + uuid);
            
            HttpConnectionWrapper connection = null;
            try {
                LocalFuncs lf = new LocalFuncs();

                String m3u8NettyURL = null;
                if(function.equals("getaudio.fn"))
                    m3u8NettyURL = lf.getMediaURL(md5, "audio", false);
                else
                    m3u8NettyURL = lf.getMediaURL(md5, "video", false);

                if(!m3u8NettyURL.isEmpty()){
                    String url;
                    if(function.equals("getvideo.m3u8") || function.equals("getaudio.fn")){
                        url = m3u8NettyURL + "&uuid=" + uuid;
                    }else{//getts
                        String nettyHost = m3u8NettyURL.split("/getvideo.m3u8")[0];
                        String ts = map.get("ts");
                        url = String.format("%s/%s?md5=%s&ts=%s&uuid=%s", nettyHost, function, md5, ts, uuid);
                    }
                    log("Netty file request with url:" + url, 1);

                    // MIGRATED: Use httpLocalRequest_new instead of httpLocalRequest_old
                    connection = httpLocalRequest_new(url, stickyName, stickyValue);

                    if (connection != null && connection.response != null) {
                        HttpEntity responseEntity = connection.response.getEntity();
                        if (responseEntity != null) {
                            // Use the same buffering approach as fileRequest_new for consistency
                            InputStream fileStream = responseEntity.getContent();
                            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                            byte[] data = new byte[8192];
                            int bytesRead;
                            long totalBytes = 0;
                            
                            while ((bytesRead = fileStream.read(data)) != -1) {
                                buffer.write(data, 0, bytesRead);
                                totalBytes += bytesRead;
                            }
                            
                            log("Netty file - Read " + totalBytes + " bytes from HTTP response", 1);
                            
                            // Close connection immediately after reading all data
                            connection.close();
                            
                            // Create new InputStream from buffered data
                            java.io.ByteArrayInputStream bufferedStream = new java.io.ByteArrayInputStream(buffer.toByteArray());
                            bridge.postFileResponse(requestId, bufferedStream, stickyName, stickyValue);
                        } else {
                            bridge.postErrorResponse(requestId, "Unable to get response entity");
                            connection.close();
                        }
                    } else {
                        bridge.postErrorResponse(requestId, "Unable to request local file");
                        if (connection != null) connection.close();
                    }

                }else{
                    bridge.postErrorResponse(requestId, "Unable to locate file");
                }
            } catch (IOException e) {
                log("WARNING: Exception in fileRequestNetty_new", 1);
                e.printStackTrace(log);
                if (connection != null) connection.close();
            }
        }    

        /*
            Locally executes a login request against running cluster RT server.
        */
        public void loginRequest_new(String requestId, String user, String password, String encdata, RelayBridge bridge) {
            pw("LoginRequest----New");
            String password_enc = password;
            
            try {
                password_enc = URLEncoder.encode(password,"UTF-8");
            } catch (Exception e) {
                log("Exception encoding password", 1);
            }

            String encdata_enc = encdata;
            
            try {
                encdata_enc = URLEncoder.encode(encdata,"UTF-8");
            } catch (Exception e) {
                log("Exception encoding password", 1);
            }
            String url = String.format("http://%s:%s/cass/login.fn?boxuser=%s&boxpass=%s&encdata=%s", 
                                           _clusterHost, _clusterPort, user, password_enc, encdata_enc); 
            log("Logging in with url:" + url, 1);
          
            //GetMethod login = httpLocalRequest_old(url, null, null);
            pw("local request = " + url);
            HttpConnectionWrapper connection = httpLocalRequest_new(url, null, null);

            try {
                String loginResponse = "";
                String responseBody = "";
                if (connection != null && connection.response != null) {
                    HttpEntity responseEntity = connection.response.getEntity();
                    if (responseEntity != null) {
                         try {
                             responseBody = EntityUtils.toString(responseEntity, "UTF-8");
                         } catch (ParseException e) {
                             pw("ParseException reading response body");
                         }
                        pw("respomseBody = " + responseBody);
                         loginResponse = responseBody;
                    } else {
                        pw("WARNING: Response Entity is null");
                    }             
                    
                    Header[] cookieHeaders = connection.response.getHeaders();
                    Header cookieHeader = connection.response.getFirstHeader("Set-Cookie");

                    String cookieName = "";
                    String cookieValue = "";
                    if (cookieHeader != null) {
                            String cookieHeaderValue = cookieHeader.getValue();
                            if (cookieHeaderValue != null && cookieHeaderValue.contains("=")) {
                                String[] cookieParts = cookieHeaderValue.split("=", 2);
                               if (cookieParts.length >= 2) {
                                     cookieName = cookieParts[0].trim();
                                     cookieValue = cookieParts[1];
                                }
                           }
                       loginResponse = "Set-Cookie: " + cookieName + "=" + cookieValue + ";\r\n\r\n" + responseBody;                        
                       pw("hello");
                       pw(loginResponse);
                    } else {
                        pw("Cookie header null");
                    }
                    connection.close();
                    bridge.postStringResponse(requestId, loginResponse, null, null);
                } else {
                    bridge.postErrorResponse(requestId, "Unable to relay local login");
                }
            } catch (IOException e) {
                e.printStackTrace(log);
            } finally {
                if (connection != null) {
                    //connection.close();
                }
            }
        }
    
    }
    
    
    /*
        RelayBridge wraps all REST API functionality in a single Class.
    */
    public class RelayBridge {
        
        ClusterDelegate _cluster;
        String _relayHost, _relayPort, _clusterId, _clusterToken, _protocol;
        boolean _stopped;
        long lastRequest = 0;
        
        RelayBridge(String host, String port, String protocol, String clusterId, String clusterToken, ClusterDelegate cluster) {
            _relayHost = host;
            _relayPort = port;
            _protocol = protocol;
            _clusterId = clusterId;
            _clusterToken = clusterToken;
            _cluster = cluster;
            _stopped = false;
        }
        
        public void stop() {
            _stopped = true;
            log("Stopping bridge request poller", 1);
        }
        
        public boolean running() {
            p("check if bridge running");
            if (lastRequest > 0) {
                long diff = System.currentTimeMillis() - lastRequest;
                p("diff: " + diff);
                if (diff < 30000) {
                    //last request came within a minute ago, all is OK.
                    return true;                
                } else {
                    return false;
                }                
            } else {
                //first time, no request came in yet.
                return true;
            }
        }
        /*
            Sends an async file response to the bridge when a file request was completed
            locally.
            MIGRATED TO HTTP v5.x
        */
        private int postFileResponse(String requestId, InputStream response, String stickyName, String stickyValue) {
            String fileUrl = String.format("%s://%s:%s/clusters/%s/send-file/%s", 
                                            _protocol, _relayHost, _relayPort, _clusterId, requestId); 
            HttpPost postFile = new HttpPost(fileUrl);
            
            if (stickyName != null) {
                p("Including STICKY COOKIE in Post Response(File): " + stickyName + stickyValue);
                postFile.setHeader("Cookie", stickyName + "=" + stickyValue + ";");                                
            }

            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                // Debug: Try different entity creation approaches
                log("DEBUG postFileResponse - Creating InputStreamEntity with ContentType.APPLICATION_OCTET_STREAM", 1);
                
                // INVESTIGATION: The key difference might be here:
                // v3 used: new InputStreamRequestEntity(response) - no content type specified
                // v5.x uses: new InputStreamEntity(response, ContentType.APPLICATION_OCTET_STREAM) - explicit content type
                
                // Let's try without explicit ContentType to match v3 behavior more closely  
                // Note: InputStreamEntity requires ContentType in v5.x, but we can use default
                InputStreamEntity entity = new InputStreamEntity(response, ContentType.DEFAULT_BINARY);
                postFile.setEntity(entity);
                postFile.setHeader("Content-type", "application/octet-stream");
                
                log("DEBUG postFileResponse - Entity created, executing request", 1);
                try (CloseableHttpResponse httpResponse = httpclient.execute(postFile)) {
                    int code = httpResponse.getCode();
                    log("DEBUG postFileResponse - Response code: " + code, 1);
                    return code;
                }
            } catch (IOException e) {
                log("DEBUG postFileResponse - IOException: " + e.getMessage(), 1);
                e.printStackTrace(log);
            }
            return 0;
        }
        
        // Version that closes the source connection after file transfer
        private int postFileResponse_withConnection(String requestId, InputStream response, String stickyName, String stickyValue, HttpConnectionWrapper sourceConnection) {
            try {
                int result = postFileResponse(requestId, response, stickyName, stickyValue);
                return result;
            } finally {
                // Close the source connection after the file transfer is complete
                if (sourceConnection != null) {
                    sourceConnection.close();
                }
            }
        }
        
        /*
            Sends an async error response to the bridge when a request has
            failed to complete locally.
            MIGRATED TO HTTP v5.x
        */
        private int postErrorResponse(String requestId, String response) {
           String responseUrl = String.format("%s://%s:%s/clusters/%s/request-failed/%s", 
                                              _protocol, _relayHost, _relayPort, _clusterId, requestId); 
            HttpPost postMethod = new HttpPost(responseUrl);
            
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                StringEntity requestEntity = new StringEntity(response, ContentType.create("text/plain", "UTF-8"));
                postMethod.setEntity(requestEntity);
                
                try (CloseableHttpResponse httpResponse = httpclient.execute(postMethod)) {
                    return httpResponse.getCode();
                }
            } catch(IOException e) {
                e.printStackTrace(log);
            }
            return 0; 
        }
        
        /*
            Sends an async string response to the server when a specific request
            is completed.
            MIGRATED TO HTTP v5.x
        */
        private int postStringResponse(String requestId, String response, String stickyName, String stickyValue) {
            String responseUrl = String.format("%s://%s:%s/clusters/%s/send-response/%s", 
                                               _protocol, _relayHost, _relayPort, _clusterId, requestId); 
            HttpPost postMethod = new HttpPost(responseUrl);
                        
            if (stickyName != null) {
                p("Including STICKY COOKIE in Post Response: " + stickyName + stickyValue);
                postMethod.setHeader("Cookie", stickyName + "=" + stickyValue + ";");                                
            }
                                
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                StringEntity requestEntity = new StringEntity(response, ContentType.create("text/plain", "UTF-8"));
                postMethod.setEntity(requestEntity);
                                
                try (CloseableHttpResponse httpResponse = httpclient.execute(postMethod)) {
                    return httpResponse.getCode();
                }
            } catch(IOException e) {
                e.printStackTrace(log);
            }
            return 0;
        }

        /*
            Unregisters a properly registered cluster, returns true if unregistration 
            is successful.
            MIGRATED TO HTTP v5.x
        */
        private boolean unregisterCluster(String clusterId, String clusterToken) {
            log(String.format("Unregistering cluster:%s token:%s", clusterId, clusterToken), 1);
            String url = String.format("%s://%s:%s/clusters/%s/unregister?access-token=%s", 
                                       _protocol, _relayHost, _relayPort, clusterId, clusterToken); 
            
            HttpGet unregister = new HttpGet(url);
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                try (CloseableHttpResponse response = httpclient.execute(unregister)) {
                    int status = response.getCode();
                    if (status == 200) {
                        log("Cluster unregistration succeed", 1);
                        return true;
                    }else{
                        log("Unregistrer failed - Status " + status, 1);
                    }
                }
            } catch (IOException e) {
                log("Unregistrer IOException", 1);
                StringWriter sWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(sWriter));
                log(sWriter.getBuffer().toString(), 1);
            } catch (Exception e) {
                log("Unregistrer Exception", 1);
                StringWriter sWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(sWriter));
                log(sWriter.getBuffer().toString(), 1);
            }
            return false;
        }
        
        /*
            Given a clusterId executes the registering API on the Relay Bridge.
            If API execution is successfull an authentication token is returned which
            needs to be used in each cluster operation.
        
            The operation updates the _clusterToken member of the class if succeeded.
            MIGRATED TO HTTP v5.x
        */
        private String registerCluster(String clusterId, String clusterName) {
            log(String.format("Registering cluster:%s", clusterId), 1);
            String registerUrl = String.format("%s://%s:%s/clusters/register", 
                                               _protocol, _relayHost, _relayPort); 
            HttpPost postMethod = new HttpPost(registerUrl);
            
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                JSONObject data = new JSONObject();
                data.put("cluster-id", clusterId);
                data.put("cluster-name", clusterName);
                StringEntity requestEntity = new StringEntity(data.toJSONString(), ContentType.create("application/json", "UTF-8"));
                postMethod.setEntity(requestEntity);
                
                try (CloseableHttpResponse response = httpclient.execute(postMethod)) {
                    int status = response.getCode();
                    switch (status) {
                        case 400:
                            //TODO: recreate cluster-id since already registered means something went bad
                            log("Register failed - Status 400", 1);
                            break;
                        case 200:
                            log("Register sucess - Status 200", 1);
                            String token = EntityUtils.toString(response.getEntity());
                            log("ResponseBodyAsString " + token, 1);
                            if (token.length() > 0) {
                                JSONObject jtoken = (JSONObject)JSONValue.parse(token);
                                if (jtoken != null) {
                                    log("access-token " + jtoken.get("access-token").toString(), 1);
                                    _clusterToken = jtoken.get("access-token").toString();
                                    return _clusterToken;
                                }else{
                                    log("jtoken null", 1);
                                }
                            }else{
                                log("emtpy reponse", 1);
                            }
                            break;
                        default:
                            log("Register failed - Status " + status, 1);
                            break;
                    }
                } catch (ParseException e) {
                    log("Cluster registration failed: ParseException", 1);
                    StringWriter sWriter = new StringWriter();
                    e.printStackTrace(new PrintWriter(sWriter));
                    log(sWriter.getBuffer().toString(), 1);
                }
            } catch(IOException e) {
                log("Cluster registration failed: IOException", 1);
                StringWriter sWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(sWriter));
                log(sWriter.getBuffer().toString(), 1);
            }catch (Exception e){
                log("Cluster registration failed: Exception", 1);
                StringWriter sWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(sWriter));
                log(sWriter.getBuffer().toString(), 1);
            }
            return null;
        }
        
        /*
            Process a pending request from the bridge, the request is received as
            a string and assumed to be JSON formatted.
        
            Request JSON format is documented somewhere else but basically dispatches on
            its `type` attribute.
        */
        private void processRequest(String strRequest) {
            try {
                
            JSONObject request = (JSONObject)JSONValue.parse(strRequest);
            
            Logger.getLogger("org.apache.hc.client5").setLevel(Level.INFO);

            p("strRequest = " + strRequest);                    
            
            lastRequest = System.currentTimeMillis();
            
            if (request != null) {
                log(String.format("Request received, type:%s, request-id:%s, cluster-id:%s", 
                                   request.get("type"), request.get("request-id"), request.get("cluster-id")), 1);
                
                log(String.format("Sticky cookie: " + request.get("sticky-cookie")), 1);
                
                JSONObject stickyCookieJSON = (JSONObject)request.get("sticky-cookie");
                String stickyName = null;
                String stickyValue = null;
                if (stickyCookieJSON != null) {
                    stickyName = (String)stickyCookieJSON.get("name");
                    stickyValue = (String)stickyCookieJSON.get("value");                    
                    log("sticky name: '" + stickyName + "'", 1);
                    log("sticky value '" + stickyValue + "'", 1);
                }                
                
                String requestType = request.get("type").toString();
                if(requestType.contains(".fn")){
                    requestType = requestType.split(".fn")[0];
                    if(requestType.contains("multicluster")){
                        String g="";
                    }
                }
                p("requestType: " + requestType);
                switch (Operations.valueOf(requestType)) {
                    case login:
                        _cluster.loginRequest_new(request.get("request-id").toString(), 
                                              request.get("user").toString(), 
                                              request.get("password").toString(),
                                              request.get("encdata")==null?"":request.get("encdata").toString(),
                                              this);
                        break;
                    case logout:
                        _cluster.standardRequest_new(request.get("request-id").toString(), 
                                                "logout.fn", 
                                                request.get("auth").toString(),
                                                "", this, stickyName, stickyValue);
                        break;
                    case getfolders_json:
                        p("case getfolder_json");
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "getfolders_json.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case query:
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "query.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case suggest:
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "suggest.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case sidebar:
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "sidebar.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case gettags:
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "gettags_m.fn",
                                                 request.get("auth").toString(),
                                                 "", this, stickyName, stickyValue);
                        break;
                    case file:
                        _cluster.fileRequest_new(request.get("request-id").toString(),
                                             request.get("auth").toString(),
                                             request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                   case applytags:
                         _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "applytags.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case doshare:
                         _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "doshare.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case getusersandemail:
                         _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "getusersandemail.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case adduser:
                         _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "adduser.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case getvideo:
                         _cluster.fileRequestNetty_new(request.get("request-id").toString(),
                                                 "getvideo.m3u8",
                                                 null,
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;

                    case getts:
                         _cluster.fileRequestNetty_new(request.get("request-id").toString(),
                                                 "getts.fn",
                                                 null,
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case getaudio:
                         _cluster.fileRequestNetty_new(request.get("request-id").toString(),
                                                 "getaudio.fn",
                                                 null,
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case getsession:
                         _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "getsession.fn",
                                                 null,
                                                 null, this, stickyName, stickyValue);
                        break;
                    case getauthtoken:
                         _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "getauthtoken.fn",
                                                 null,
                                                 null, this, stickyName, stickyValue);
                        break;    
                    case chat_pull:
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "chat_pull.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case chat_push:
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "chat_push.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case savepropertymulticluster:
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "savepropertymulticluster.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);    
                        break;
                    case getpropertymulticluster:
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "getpropertymulticluster.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);    
                        break;    
                    case gettags_webapp:
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 "gettags_webapp.fn",
                                                 request.get("auth").toString(),
                                                 "", this, stickyName, stickyValue);
                        break;
                    default:
                        _cluster.standardRequest_new(request.get("request-id").toString(),
                                                 request.get("type").toString(),
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                }
            }
            
            } catch (Exception e) {
                pw("*** Exception in processRequest()");
                e.printStackTrace();
            }

        }
        
        /*
            Neverending request process loop, its a long polling request so it blocks
            on the server until something arrives or a specified timeout.
            MIGRATED TO HTTP v5.x
        */
        public void processRequests() throws InterruptedException {
            String url = String.format("%s://%s:%s/clusters/%s/pending-requests?access-token=%s", 
                                       _protocol, _relayHost, _relayPort, _clusterId, _clusterToken); 
            log("Process request url: " + url, 1);
            while(!_stopped) {
                HttpGet pendingRequests = new HttpGet(url);
                try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                    log("Executing process request url: " + url, 2);
                    try (CloseableHttpResponse httpResponse = httpclient.execute(pendingRequests)) {
                        int status = httpResponse.getCode();
                        final String response = EntityUtils.toString(httpResponse.getEntity());
                        switch (status) {
                            case 200:
                                //requests are processed in a separate thread to avoid excessive queuing on the
                                //server if response takes too long to be fulfilled
                                Runnable processor = new Runnable(){
                                    public void run(){
                                        try {
                                            processRequest(response);                                    
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                };
                                Thread thread = new Thread(processor);
                                thread.start();
                                break;
                            case 401:
                                log("Unauthenticated cluster for Vault, cleaning up token Invalid clustertoken " + _clusterToken, 1);
                                _clusterToken = "";
                                cleanTokenFile();
                                return;
                            default:
                                log(String.format("Invalid status:%s returned on pending requests, retrying on 10 seconds", status), 1);
                                Thread.sleep(10000);
                                break;
                        }
                    } catch (ParseException e) {
                        log("Process requests failed with ParseException, backing off 15 second before retrying...", 1);
                        e.printStackTrace(log);
                        Thread.sleep(15000);
                    }
                } catch (IOException e) {
                    e.printStackTrace(log);
                    log("Process requests failed with IOException, backing off 15 second before retrying...", 1);
                    Thread.sleep(15000);
                } catch (Exception e) {
                    e.printStackTrace();
                    log("Vault -- There was another exception", 1);
                }
            }
            log("Ending bridge request poller", 1);
        }
    }
    
    private static String _bridgeHost, _bridgePort, _clusterHost, 
                          _clusterId, _clusterName, _clusterPort, _clusterToken;
    private static boolean _secure;
    private static int _logLevel;
    
    /*
        Main constructor for the relay bridge api wrapper.
        This class mainly wraps all the REST functionality into something easily 
        eaten by Java.
    */
    public RelayVaultServiceV52(String bridgeHost, String bridgePort, boolean secure, String clusterHost,
                             String clusterPort, String clusterId, String clusterName, String clusterToken, int logLevel) {
        _terminated = false;
        _bridgeHost = bridgeHost;
        _bridgePort = bridgePort;
        _secure = secure;
        _clusterHost = clusterHost;
        _clusterPort = clusterPort;
        _clusterId = clusterId;
        _clusterName = clusterName;
        _clusterToken = clusterToken;
        _logLevel = logLevel;
    }
    
    public void terminate() {
        _terminated = true;
        log("Stopping vault", 1);
        if (_bridge != null) {
            _bridge.stop();
        }
    }    
    
    public boolean isRunning() {
        p("RelayVault thread alive. Checking Bridge.");
        if (_bridge != null) {
            if (_bridge.running()) {
                p("bridge is alive");
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    
    ClusterDelegate _cluster;
    RelayBridge _bridge;
    PrintStream log = null;
    String LOG_NAME = "logs/vault.txt";
    
    protected void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [SC.RelayVaultService_" + threadID + "] " + s);
    }
    
    protected void log(String s, int _loglevel) {

        if (_loglevel <= _logLevel) {
            Date ts_start = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
            String sDate = sdf.format(ts_start);

            synchronized (log) {
                log.println(sDate + " " + _loglevel + " " + s);
                log.flush();
            }    
            p(_loglevel + " " + s);
        }        
    }
    
    public void cleanTokenFile() {
        try {
            log("Clean token file Cluster token = "  + _clusterToken, 1);
            PrintWriter tokenFile = new PrintWriter("data/clusterToken");
            tokenFile.print("");
            tokenFile.close();
            _clusterToken = "";
        } catch (java.io.FileNotFoundException e) {
            log("File not found exception cleaning token file.", 1);
            StringWriter sWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(sWriter));
            log(sWriter.getBuffer().toString(), 1);
        } catch(Exception e){
            log("Exception cleaning token", 1);
            StringWriter sWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(sWriter));
            log(sWriter.getBuffer().toString(), 1);
        }
    }
    
    public void unregister() {
        log(String.format("Unregistering cluster:%s", _clusterId), 1);
        if (_bridge.unregisterCluster(_clusterId, _clusterToken)) {
            log(String.format("Cluster:%s unregistered successfully", _clusterId), 1);
            cleanTokenFile();
        }
    } 
    
    public void run() {
      try {         
            log = new PrintStream(new BufferedOutputStream(new FileOutputStream(LOG_NAME,true)));
            log("Starting relay vault service", 1); 
            
            _cluster = new ClusterDelegate(_clusterHost, _clusterPort);
            _bridge = new RelayBridge(_bridgeHost, _bridgePort, _secure ? "https" : "http", _clusterId, _clusterToken, _cluster);
            
            while (!_terminated) {
                if (_clusterToken.equals("")) {
                    log("Cluster has no token, registering", 1);
                    _clusterToken = _bridge.registerCluster(_clusterId, _clusterName);
                    if (_clusterToken.equals("")) {
                        log(String.format("Unable to register cluster:%s, stopping Vault", _clusterId), 1);
                        _terminated = true;
                    } else {
                        try {
                            log(String.format("Cluster registration succeeded, saving to file token:%s", _clusterToken), 1);
                            PrintWriter tokenFile = new PrintWriter("data/clusterToken");
                            tokenFile.print(_clusterToken);
                            tokenFile.close();
                            log(String.format("Cluster Token write file succeed: %s", _clusterToken), 1);
                        } catch (Exception e) {
                            log("Fail register: fail to write cluster token file " + _clusterToken, 1);
                            StringWriter sWriter = new StringWriter();
                            e.printStackTrace(new PrintWriter(sWriter));
                            log(sWriter.getBuffer().toString(), 1);
                        }
                    }
                }
                try {
                    _bridge.processRequests();
                } catch (InterruptedException e) {
                    p("**** Vault run() -- there was Interrupted exception");
                    e.printStackTrace();
                } catch (Exception e) {
                    p("**** Vault run() -- there was Another type of exception");
                    e.printStackTrace();                    
                }
            } 
        } catch (Exception e) {
            log("Exception running vault bridge", 1);
            e.printStackTrace(log);
        }  
    } 
}
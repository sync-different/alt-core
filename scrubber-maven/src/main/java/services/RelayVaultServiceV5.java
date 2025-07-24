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

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ParseException;

import java.util.*;

import net.minidev.json.*;

import java.net.URLEncoder;
import utils.LocalFuncs;

/**
 *
 * @author guilespi
 */
public class RelayVaultServiceV5 implements Runnable {
    
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
        
        private CloseableHttpResponse httpLocalRequest(String url, String stickyName, String stickyValue) {
            HttpGet request = new HttpGet(url);
            
            if (stickyName != null) {
                request.setHeader("Cookie", stickyName + "=" + stickyValue + ";");                
            }
                        
            try {
                CloseableHttpClient httpclient = HttpClients.createDefault();
                CloseableHttpResponse response = httpclient.execute(request);
                int status = response.getCode();
                switch (status) {
                    case 200:
                        return response;
                    case 401:
                    case 500:
                    default:
                        log("Local http request failed with status:" + status, 1);
                        break;
                }
            } catch (IOException e) {
                log("Local http request failed with IOException", 1);
                e.printStackTrace(log);
            }
            return null;
        }

        
        /*
            Locally executes a file request against running cluster RT server.
        */
        private void fileRequest(String requestId, String authCookie, String queryString, RelayBridge bridge, String StickyName, String StickyValue) {
            String url = String.format("http://%s:%s/cass/getfile.fn?%s", 
                                       _clusterHost, _clusterPort, queryString); 
            log("File request with url:" + url, 1);
            CloseableHttpResponse file = httpLocalRequest(url, StickyName, StickyValue);
            try {
                if (file != null) {
                    HttpEntity entity = file.getEntity();
                    if (entity != null) {
                        InputStream fileStream = entity.getContent();
                        bridge.postFileResponse(requestId, fileStream, StickyName, StickyValue);
                    } else {
                        bridge.postErrorResponse(requestId, "Unable to request local file - no content");
                    }
                } else {
                    bridge.postErrorResponse(requestId, "Unable to request local file");
                }
            } catch (IOException e) {
                e.printStackTrace(log);
            } finally {
                if (file != null) {
                    try {
                        file.close();
                    } catch (IOException e) {
                        // Ignore close error
                    }
                }
            }
        }
        
        /*
            Locally executes a standard relay request against running cluster RT server.
        */
        public void standardRequest(String requestId, 
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
            CloseableHttpResponse response = httpLocalRequest(url, stickyName, stickyValue);
            try {
                
                if (response != null) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        String responseBody = EntityUtils.toString(entity);
                        if(url.contains("query")){
                            String g="";
                        }
                        bridge.postStringResponse(requestId, responseBody, stickyName, stickyValue);
                    } else {
                        bridge.postErrorResponse(requestId, "Unable to relay local operation - no content");
                    }
                } else {
                    bridge.postErrorResponse(requestId, "Unable to relay local operation");
                }
            } catch (IOException | ParseException e) {
                e.printStackTrace(log);
            } finally {
                if (response != null) {
                    try {
                        response.close();
                    } catch (IOException e) {
                        // Ignore close error
                    }
                }
            }
        }
 
        public void fileRequestNetty(String requestId, 
                String function, 
                String authCookie, 
                String queryString, 
                RelayBridge bridge,
                String stickyName,
                String stickyValue) {
            
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
            
            CloseableHttpResponse file = null;
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
                        //url = String.format("http://%s:%s/%s?md5=%s&uuid=%s", "127.0.0.1", "8084", function, md5, uuid);
                        url = m3u8NettyURL + "&uuid=" + uuid;
                    }else{//getts
                        String nettyHost = m3u8NettyURL.split("/getvideo.m3u8")[0];
                        String ts = map.get("ts");
                        //url = String.format("http://%s:%s/%s?md5=%s&ts=%s&uuid=%s", "127.0.0.1", "8084", function, md5, ts, uuid);
                        url = String.format("%s/%s?md5=%s&ts=%s&uuid=%s", nettyHost, function, md5, ts, uuid);
                    }
                    log("File request with url:" + url, 1);

                    file = httpLocalRequest(url, stickyName, stickyValue);
                    if (file != null) {
                        HttpEntity entity = file.getEntity();
                        if (entity != null) {
                            InputStream fileStream = entity.getContent();
                            bridge.postFileResponse(requestId, fileStream, stickyName, stickyValue);
                        } else {
                            bridge.postErrorResponse(requestId, "Unable to request local file - no content");
                        }
                    } else {
                        bridge.postErrorResponse(requestId, "Unable to request local file");
                    }

                }else{
                    bridge.postErrorResponse(requestId, "Unable to locate file");
                }
            } catch (IOException e) {
                e.printStackTrace(log);
            } finally {
                if (file != null) {
                    try {
                        file.close();
                    } catch (IOException e) {
                        // Ignore close error
                    }
                }
            }
        }    
        /*
            Locally executes a login request against running cluster RT server.
        */
        public void loginRequest(String requestId, String user, String password, String encdata, RelayBridge bridge) {
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
          
            CloseableHttpResponse login = httpLocalRequest(url, null, null);
            try {
                if (login != null) {
                    HttpEntity entity = login.getEntity();
                    if (entity != null) {
                        String responseBody = EntityUtils.toString(entity);
                        String loginResponse = responseBody;
                        Header cookieHeader = login.getFirstHeader("Set-Cookie");
                        if (cookieHeader != null) {
                            String cookieHeaderValue = cookieHeader.getValue();
                            if (cookieHeaderValue != null && cookieHeaderValue.contains("=")) {
                                String[] cookieParts = cookieHeaderValue.split("=", 2);
                                if (cookieParts.length >= 2) {
                                    String cookieName = cookieParts[0];
                                    String cookieValue = cookieParts[1].split(";")[0]; // Remove any additional attributes
                                    loginResponse = "Set-Cookie: " + cookieName + "=" + cookieValue + ";\r\n\r\n" + responseBody;
                                }
                            }
                        }
                        bridge.postStringResponse(requestId, loginResponse, null, null);
                    } else {
                        bridge.postErrorResponse(requestId, "Unable to relay local login - no content");
                    }
                } else {
                    bridge.postErrorResponse(requestId, "Unable to relay local login");
                }
            } catch (IOException | ParseException e) {
                e.printStackTrace(log);
            } finally {
                if (login != null) {
                    try {
                        login.close();
                    } catch (IOException e) {
                        // Ignore close error
                    }
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
                InputStreamEntity entity = new InputStreamEntity(response, org.apache.hc.core5.http.ContentType.APPLICATION_OCTET_STREAM);
                postFile.setEntity(entity);
                postFile.setHeader("Content-type", "application/octet-stream");
                
                try (CloseableHttpResponse httpResponse = httpclient.execute(postFile)) {
                    return httpResponse.getCode();
                }
            } catch (IOException e) {
                e.printStackTrace(log);
            }
            return 0;
        }
        
        /*
            Sends an async error response to the bridge when a request has
            failed to complete locally.
        */
        private int postErrorResponse(String requestId, String response) {
           String responseUrl = String.format("%s://%s:%s/clusters/%s/request-failed/%s", 
                                              _protocol, _relayHost, _relayPort, _clusterId, requestId); 
            HttpPost postMethod = new HttpPost(responseUrl);
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                StringEntity requestEntity = new StringEntity(response, org.apache.hc.core5.http.ContentType.create("text/plain", "UTF-8"));
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
                StringEntity requestEntity = new StringEntity(response, org.apache.hc.core5.http.ContentType.create("text/plain", "UTF-8"));
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
        */
        private boolean unregisterCluster(String clusterId, String clusterToken) {
            log(String.format("Unregistering cluster:%s token:%s", clusterId, clusterToken), 1);
            String url = String.format("%s://%s:%s/clusters/%s/unregister?access-token=%s", 
                                       _protocol, _relayHost, _relayPort, clusterId, clusterToken); 
            
            HttpGet unregister = new HttpGet(url);
            try (CloseableHttpClient httpclient = HttpClients.createDefault();
                 CloseableHttpResponse response = httpclient.execute(unregister)) {
                int status = response.getCode();
                if (status == 200) {
                    log("Cluster unregistration succeed", 1);
                    return true;
                }else{
                    log("Unregistrer failed - Status " + status, 1);
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
                StringEntity requestEntity = new StringEntity(data.toJSONString(), org.apache.hc.core5.http.ContentType.create("text/plain", "UTF-8"));
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
                            HttpEntity entity = response.getEntity();
                            if (entity != null) {
                                String token = EntityUtils.toString(entity);
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
                                    log("empty response", 1);
                                }
                            }
                            break;
                        default:
                            log("Register failed - Status " + status, 1);
                            break;
                    }
                }
            } catch(IOException | ParseException e) {
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
                        _cluster.loginRequest(request.get("request-id").toString(), 
                                              request.get("user").toString(), 
                                              request.get("password").toString(),
                                              request.get("encdata")==null?"":request.get("encdata").toString(),
                                              this);
                        break;
                    case logout:
                        _cluster.standardRequest(request.get("request-id").toString(), 
                                                "logout.fn", 
                                                request.get("auth").toString(),
                                                "", this, stickyName, stickyValue);
                        break;
                    case getfolders_json:
                        p("case getfolder_json");
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 "getfolders_json.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case query:
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 "query.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case suggest:
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 "suggest.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case sidebar:
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 "sidebar.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case gettags:
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 "gettags_m.fn",
                                                 request.get("auth").toString(),
                                                 "", this, stickyName, stickyValue);
                        break;
                    case file:
                        _cluster.fileRequest(request.get("request-id").toString(),
                                             request.get("auth").toString(),
                                             request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                   case applytags:
                         _cluster.standardRequest(request.get("request-id").toString(),
                                                 "applytags.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case doshare:
                         _cluster.standardRequest(request.get("request-id").toString(),
                                                 "doshare.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case getusersandemail:
                         _cluster.standardRequest(request.get("request-id").toString(),
                                                 "getusersandemail.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case adduser:
                         _cluster.standardRequest(request.get("request-id").toString(),
                                                 "adduser.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case getvideo:
                         _cluster.fileRequestNetty(request.get("request-id").toString(),
                                                 "getvideo.m3u8",
                                                 null,
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;

                    case getts:
                         _cluster.fileRequestNetty(request.get("request-id").toString(),
                                                 "getts.fn",
                                                 null,
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case getaudio:
                         _cluster.fileRequestNetty(request.get("request-id").toString(),
                                                 "getaudio.fn",
                                                 null,
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case getsession:
                         _cluster.standardRequest(request.get("request-id").toString(),
                                                 "getsession.fn",
                                                 null,
                                                 null, this, stickyName, stickyValue);
                        break;
                    case getauthtoken:
                         _cluster.standardRequest(request.get("request-id").toString(),
                                                 "getauthtoken.fn",
                                                 null,
                                                 null, this, stickyName, stickyValue);
                        break;    
                    case chat_pull:
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 "chat_pull.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                    case chat_push:
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 "chat_push.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                    case savepropertymulticluster:
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 "savepropertymulticluster.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);    
                        break;
                    case getpropertymulticluster:
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 "getpropertymulticluster.fn",
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);    
                        break;    
                    case gettags_webapp:
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 "gettags_webapp.fn",
                                                 request.get("auth").toString(),
                                                 "", this, stickyName, stickyValue);
                        break;
                    default:
                        _cluster.standardRequest(request.get("request-id").toString(),
                                                 request.get("type").toString(),
                                                 request.get("auth").toString(),
                                                 request.get("query-string").toString(), this, stickyName, stickyValue);
                        break;
                }
            }
            
            } catch (Exception e) {
                p("*** Exception in processRequest()");
            }

        }
        
        /*
            Neverending request process loop, its a long polling request so it blocks
            on the server until something arrives or a specified timeout.
        */
        public void processRequests() throws InterruptedException {
            String url = String.format("%s://%s:%s/clusters/%s/pending-requests?access-token=%s", 
                                       _protocol, _relayHost, _relayPort, _clusterId, _clusterToken); 
            log("Process request url: " + url, 1);
            while(!_stopped) {
                HttpGet pendingRequests = new HttpGet(url);
                try (CloseableHttpClient httpclient = HttpClients.createDefault();
                     CloseableHttpResponse httpResponse = httpclient.execute(pendingRequests)) {
                    log("Executing process request url: " + url, 2);
                    int status = httpResponse.getCode();
                    
                    String responseBody = "";
                    HttpEntity entity = httpResponse.getEntity();
                    if (entity != null) {
                        responseBody = EntityUtils.toString(entity);
                    }
                    final String response = responseBody;
                    
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
                } catch (IOException | ParseException e) {
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
    public RelayVaultServiceV5(String bridgeHost, String bridgePort, boolean secure, String clusterHost,
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

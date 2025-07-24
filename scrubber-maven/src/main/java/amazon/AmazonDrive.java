/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amazon;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.UUID;

import java.net.SocketTimeoutException;
import javax.net.ssl.SSLHandshakeException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;



import net.minidev.json.*;
import java.util.Iterator;
import java.util.Scanner;
import java.util.StringTokenizer;

import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

import utils.NetUtils;
import utils.LocalFuncs;

/**
 *
 * @author agf
 */
public class AmazonDrive implements Runnable {

    static long nUploadedFiles = 0;
    static long nUploadedBytes = 0;
    static long nUploadedDUP = 0;
    static long nUploadedBackoff = 0;
    static long nCloudDUP = 0;
    static long nErrorsUpload = 0;
    static boolean bTokenValid = false;
    
    static HashMap<String,Integer> mapRecordsMD5 = new HashMap<String, Integer>();
    static HashMap<String,List<String>> mapRecordsMD5_json = new HashMap<String, List<String>>();
    static HashMap<String,Integer> mapUploadedMD5 = new HashMap<String, Integer>();
    static HashMap<String,List<String>> mapUploadedMD5_json = new HashMap<String, List<String>>();
    static HashMap<String,List<String>> mapFolders = new HashMap<String, List<String>>();
    static HashMap<String,String> mapFolderNames = new HashMap<String, String>();
    
    static String mStorage = "amazon.db";
    static String mStorage_json = "amazon_json.db";
    static String mStorage_folders = "amazon_folders.db";
    static String mStorage_folders_names = "amazon_folders_names.db";
    
    static LocalFuncs c8 = new LocalFuncs();
    
    static String sClientID = "";
    static String sSecret = "c976a7300b41707f68508b9383d369b73573be6614d9b0bcdd89dc8d937c9547";
    
    static int mLogLevel = 2;
    
    static String LOG_NAME = "cloud_amazon.txt";
    static String LOG_PATH = "logs/";
    static PrintStream log= null;

    private Boolean mTerminated = false;

    static int nRetryCount = 0;
    
    static String sEndPoint = "";
    static long lEndpointCache = 0;
    
    static String sRootID = "";
        
/* print to stdout */
    static protected void p(String s) {

        long threadID = Thread.currentThread().getId();
        System.out.println("[client_" + threadID + "] " + s);
    }

    /* print to the log file */
    protected static void log(String s, int _loglevel) {

        if (_loglevel <= mLogLevel) {
            Date ts_start = Calendar.getInstance().getTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
            String sDate = sdf.format(ts_start);

            //p(sDate + " " + s);
            synchronized (log) {
                log.println(sDate + " " + _loglevel + " " + s);
                log.flush();
            }
            p(sDate + " " + _loglevel + " " + s);        
        }
    }    
    
    public AmazonDrive(int _logLevel) {
        try {
            mLogLevel = _logLevel;
        
            String sLog = LOG_PATH + LOG_NAME;
            log = new PrintStream(new BufferedOutputStream(
                              new FileOutputStream(sLog,true)));
            log("opening log file: " + sLog, 0);

            Thread t = new Thread(this, "amazondrive");
            p("Child thread: " + t);
            t.start(); // Start the thread
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void httpPost(String path, String data) {
                try {
                        URL url = new URL(path);
                        URLConnection conn = url.openConnection();
                        conn.setDoOutput(true);
                        OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                        wr.write(data);
                        wr.flush();

                        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String line;
                        while ((line = rd.readLine()) != null) {
                                System.out.println(line);
                        }
                        wr.close();
                        rd.close();
                }
                catch (Exception e) {
                        System.err.println(e);
                }
        }

    public static boolean doCloudUpload(String _filename) {
        if (_filename.toLowerCase().contains(".png") || 
           (_filename.toLowerCase().contains(".jpg")) || 
           (_filename.toLowerCase().contains(".gif")) || 
           (_filename.toLowerCase().contains(".jpeg"))) {
            return true;
        } else {
            return false;
        }
    }
    public static int uploadBatch(int _batchnum, String _sContentUrl, String _sAccessToken, String _sAlteranteFolderID, String _sMetaDataUrl, String _sClientID) {
        try {
            log("Batch: " + _batchnum, 1);
            
            String DB_PATH = NetUtils.getConfig("dbpath", "../scrubber/config/www-processor.properties");
            //String DB_PATH = "/Applications/alterante/projects/scrubber/data/localdb/";
            String sPath = DB_PATH;
            String _cf = "Standard1";
            String _key = Long.toString(_batchnum);

            String filename = sPath + File.separator + _cf + File.separator + "batch@" + String.valueOf(_batchnum);
            int nError = 0;
            
            p("looking for " + filename);
            File f = new File(filename);
            if (f.exists()) {
                p(f.getAbsolutePath() + " exists");
                FileInputStream fis = new FileInputStream(filename);
                Scanner scanner = new Scanner(fis);                       
                int i = 0;   
                while (scanner.hasNext()) {   
                    p("---------------------");
                    i++;
                    String sCurrentLine = scanner.nextLine();                    
                    String delimiters = ",";
                    StringTokenizer st = new StringTokenizer(sCurrentLine, delimiters, true);

                    String sDate = "";
                    String sMD5 = "";
                    String sFilePath = "";
                    
                    if (st.countTokens() == 5) {
                        sDate = st.nextToken();
                        p("sDate: " + sDate);
                        st.nextToken();
                        sMD5 = st.nextToken();
                        p("sMD5: " + sMD5);
                        st.nextToken();
                        sFilePath = st.nextToken();
                        p("sFilePath: " + sFilePath);                        
                    } else {
                        //case where date is missing
                        if (st.countTokens() == 4) {
                            st.nextToken();
                            sMD5 = st.nextToken();
                            p("sMD5: " + sMD5);
                            st.nextToken();
                            sFilePath = st.nextToken();
                            p("sFilePath: " + sFilePath);
                        }
                    }
   
                    log(_batchnum + " " + i + " " + sMD5 + " " + sFilePath, 2);
                    
                    if (doCloudUpload(sFilePath)) {

                        Integer count = mapUploadedMD5.get(sMD5);
                        if (count == null) {
                            if (!sFilePath.startsWith("/")) sFilePath = "/" + sFilePath;
                            File f2 = new File (sFilePath);           
                            if (f2.exists()) {
                                log("uploading: " + sFilePath, 2);
                                if (sFilePath.endsWith("/")) sFilePath = sFilePath.substring(0, sFilePath.length()-1);
                                String sFolderPath = sFilePath.substring(0, sFilePath.lastIndexOf("/"));
                                log("to path: " + sFolderPath, 2);
                                String sID = getFolderIDbyName(sFolderPath, _sAlteranteFolderID, _sMetaDataUrl, _sClientID, _sAccessToken);
                                log("Folder ID: " + sID, 2);
                                if (sID.length() > 0) {
                                    long lStartTime = System.currentTimeMillis();
                                    int n = uploadFile(sFilePath, _sContentUrl, _sAccessToken, sID);
                                    long lStopTime = System.currentTimeMillis();
                                    long lDelta = lStopTime - lStartTime;
                                    log("length: " + f2.length() + " lDelta: " + lDelta, 2);
                                    float bps = 0;
                                    try {
                                        if (lDelta > 0) {
                                            bps = (f2.length() / 1024) / (lDelta / 1000);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    log("res upload: " + n + " transfer: " + f2.length() + " in "  + lDelta + " ms (" + bps + " KB/s)", 2);
                                    Thread.sleep(100);
                                    if (n == 201) {
                                        nUploadedFiles++;
                                        nUploadedBytes += f2.length();
                                        mapUploadedMD5.put(sMD5, 1);
                                    }
                                    if (n == 401) {
                                        //token invalid
                                        nError++;
                                        bTokenValid = false;
                                    }
                                    if (n == 409) {
                                        nCloudDUP++;
                                    }
                                    if (n >= 500) {
                                        //some kind of service error, do backoff
                                        int waited = service_backoff();
                                        nUploadedBackoff++;
                                    }                                    
                                } else {
                                    //there was an error...
                                    nError++;
                                }
                            }                            
                        } else {
                            log("[DUP] File is already in the cloud: " + sMD5, 2);
                            nUploadedDUP++;
                            mapUploadedMD5.put(sMD5, count+1);
                        }                      
                    }
                }         
            }
            if (nError == 0) {
                return 0;
            } else {
                nErrorsUpload += nError;
                return -1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }

    }

    public static int uploadFolder(String _folder, String _filter, String _sContentUrl, String _sAccessToken, String _parentid) {
        
        int num = 0;
        try {
            File f = new File (_folder);
            
            if (f.exists()) {
                File[] ff = f.listFiles();
                for (File f1:ff) {
                    if (f1.isFile() && f1.getName().contains(_filter)) {
                        log("Uploading ... " + f1.getAbsolutePath(), 2);
                        int n = uploadFile(f1.getAbsolutePath(), _sContentUrl, _sAccessToken, _parentid);
                        num++;
                    }
                    if (f1.isDirectory()) {
                        int r = uploadFolder(f1.getCanonicalPath(), _filter, _sContentUrl, _sAccessToken, _parentid);
                        num += r;
                    }
                }
            }
            return num;
            
        } catch (Exception e) {
            e.printStackTrace();
            return num;
        }
       

    }
            
    public static int uploadFile(String _filename, String _sContentUrl, String _sAccessToken, String _parentid) {
        try {
            String sUrl = _sContentUrl + "nodes?suppress=deduplication";
            
            HttpPost httpPost = new HttpPost(sUrl);
            httpPost.setHeader("Authorization", "Bearer " + _sAccessToken);
            
            log("Uploading file: " + _filename, 2);
            File f = new File(_filename);
            
            if (f.exists()) {
                
                String sMetaData = "{\"name\":\"" + f.getName() + "\",\"kind\":\"FILE\",\"parents\":[\"" + _parentid + "\"]}";
                log("METADATA: '" + sMetaData + "'", 2);
                
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addTextBody("metadata", sMetaData, ContentType.APPLICATION_JSON);
                builder.addBinaryBody(f.getName(), f, ContentType.APPLICATION_OCTET_STREAM, f.getName());
                
                HttpEntity multipart = builder.build();
                httpPost.setEntity(multipart);
                
                try (CloseableHttpClient client = HttpClients.createDefault();
                     CloseableHttpResponse response = client.execute(httpPost)) {
                    int status = response.getCode();
                    log("Upload status = " + status, 2);
                    return status;
                }
                
            } else {
                log("FILE DOES NOT EXIST!!!!", 2);
                return -2;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }        
    }

    public static int getFolders(String _sMetaDataUrl, String _sAccessToken2, String _sRefreshToken) {
        
        try {
            String sStartToken = "";
            boolean bContinue = true;
            //String sFolderID = "";
            int i = 0;

            while (bContinue) {

                log("Start Token: " + sStartToken, 2);

                String sFiles = getFileList(_sMetaDataUrl, _sAccessToken2, "FOLDER", sStartToken);

                if (sFiles.contains("ERROR_TOKEN_INVALID")) {
                    log("MSG: Time to renew token...", 2);
                    String sNewToken = refreshToken(sClientID, _sRefreshToken, sSecret);            
                    String sNewAccessToken2 = sNewToken.split(",")[0];                
                    if (sNewAccessToken2.length() > 0) {
                        bTokenValid = true;
                        _sAccessToken2 = sNewAccessToken2;
                        sFiles = getFileList(_sMetaDataUrl, _sAccessToken2, "FOLDER", sStartToken);
                    }                                
                }

                JSONObject jtoken = (JSONObject)JSONValue.parse(sFiles);

                JSONArray companyList = (JSONArray)jtoken.get("data");

                if (jtoken.containsKey("nextToken")) {
                    sStartToken = jtoken.get("nextToken").toString();
                } else {
                    bContinue = false;
                }

                Iterator<Object> driveIterator = companyList.iterator();

                p("hasnext = " + driveIterator.hasNext());
                //int i = 0;

                while (driveIterator.hasNext()) {
                    i++;
                    JSONObject driveJSON = (JSONObject)driveIterator.next();

                    //System.out.println(i + " " + driveJSON.toJSONString());
                    JSONObject jtoken2 = (JSONObject)JSONValue.parse(driveJSON.toJSONString());

                    //JSONObject props = (JSONObject)jtoken2.get("parents");
                    String bIsRoot = "false";
                    String sName = "";
                    if (jtoken2.containsKey("isRoot")) {
                        bIsRoot = jtoken2.get("isRoot").toString();
                        sName = "<ROOT>";
                        sRootID = jtoken2.get("id").toString();
                    } else {
                        sName = jtoken2.get("name").toString();
    //                    if (sName.equals("alterante")) {
    //                        sFolderID = jtoken2.get("id").toString();
    //                    }
                    }
                    String sId =  jtoken2.get("id").toString();
                    String sParent = jtoken2.get("parents").toString();
                    log(i + " " + sName + " " + sParent + " " + bIsRoot + " " + sId, 2);

                    //map id:foldername
                    log("Adding id, Folder: " + sId + " " + sName, 2);
                    mapFolderNames.put(sId, sName);

                    //add to parent/child structure
                    JSONArray parentList = (JSONArray)jtoken2.get("parents");        
                    Iterator<Object> parentIterator = parentList.iterator();

                    while (parentIterator.hasNext()) {
                        //JSONObject parentJSON = (JSONObject)parentIterator.next(); 
                        String sParentID = (String)parentIterator.next();
                        log("Parent ID: " + sParentID, 2);

                        List<String> sChildren = mapFolders.get(sParentID);
                        if (sChildren == null) {
                            log("this ID has NO children... Adding ChildID: " + sId, 2);
                            sChildren = new ArrayList<String>();
                            sChildren.add(sId);
                            mapFolders.put(sParentID, sChildren);
                        } else {
                            log("this ID has children already. Adding ChildID: " + sId, 2);
                            sChildren.add(sId);
                            mapFolders.put(sParentID, sChildren);
                        }
                    }

                }            
            }
            return i;
        
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
                
        
         
    }
    
    public static String getFolderIDbyName(String _foldername, String _folderid, String _metadataurl, String _clientid, String _token) {
     
        log("Looking for folder: '" + _foldername + "' folderid '" + _folderid + "'", 2);
        
        String delimiters = "/";
        StringTokenizer st = new StringTokenizer(_foldername.trim(), delimiters, true);
        st.nextToken();
        String sFolder = st.nextToken();
        String sRest = "";
        while (st.hasMoreTokens()) {
            sRest += st.nextToken();
        }
        log("Looking for folder: '" + sFolder + "' rest '" + sRest + "'", 2);
        
        List<String> FolderList = mapFolders.get(_folderid);
        
        if (FolderList != null) {
            Iterator<String> Folders = FolderList.iterator();
            while (Folders.hasNext()) {
                String sID = Folders.next();
                String sName = mapFolderNames.get(sID);
                log(sName + " " + sID, 2);
                if (sName.equals(sFolder)) {
                    log("FOUND folder '" + sName + "' id: '" + sID + "'", 2);
                    if (sRest.length() > 0) {
                        return getFolderIDbyName(sRest, sID, _metadataurl, _clientid, _token);
                    } else {
                        return sID;
                    }                
                }
            }
            log("Time to create[a]: '" + sFolder + "' rest '" + sRest + "' parentid: " + _folderid, 2);
            String sFolderID = createFolder(_metadataurl, _clientid, _token, "FOLDER", sFolder, _folderid);
            log("Create folder returned ID: " + sFolderID, 2);
            if (sRest.length() > 0) {
                return getFolderIDbyName(sRest, sFolderID, _metadataurl, _clientid, _token);
            } else {
                return sFolderID;
            }
            
        } else {
            log("Time to create[a]: '" + sFolder + "' rest '" + sRest + "' parentid: " + _folderid, 2);
            String sFolderID = createFolder(_metadataurl, _clientid, _token, "FOLDER", sFolder, _folderid);
            log("Create folder returned ID: " + sFolderID, 2);
            if (sRest.length() > 0) {
                return getFolderIDbyName(sRest, sFolderID, _metadataurl, _clientid, _token);
            } else {
                return sFolderID;
            }
        }        
    }
    
    public static int browseFolder(String _sid, Integer indent) {
        
        
        List<String> FolderList = mapFolders.get(_sid);
        
        if (FolderList != null) {
            //System.out.println(FolderList);
            Iterator<String> Folders = FolderList.iterator();
            while (Folders.hasNext()) {
                String sID = Folders.next();
                String sName = mapFolderNames.get(sID);
                String spaces = (indent==0)?"":String.format("%"+indent+"s", "");
                p(spaces + " " + sName);
                browseFolder(sID, indent + 3);
            }            
        }
        
        return 0;
    }

    public static int browseFolders() {
        Iterator<String> Folders = mapFolders.keySet().iterator();
        while (Folders.hasNext()) {
            String sKey = Folders.next();
            p(sKey + " " + mapFolderNames.get(sKey));
        }
        return 0;
    }
    
    public static int showDUP_records() {
        Iterator<String> mymap = mapRecordsMD5.keySet().iterator();
        int i = 0;
        while (mymap.hasNext()) {
            String key = mymap.next();
            Integer count = mapRecordsMD5.get(key);
            if (count > 1) {
                i++;
                log(key + " " + count, 2);
                List<String> dups = mapRecordsMD5_json.get(key);
                Iterator<String> duplist = dups.iterator();
                while (duplist.hasNext()) {
                    String js = duplist.next();
                    log(js, 2);
                }
            }
        }
        log("#dup: " + i, 2);
        return i;
    }
    
    public static int showDUP() {
        Iterator<String> mymap = mapUploadedMD5.keySet().iterator();
        int i = 0;
        while (mymap.hasNext()) {
            String key = mymap.next();
            Integer count = mapUploadedMD5.get(key);
            if (count > 1) {
                i++;
                log(key + " " + count, 2);
                List<String> dups = mapUploadedMD5_json.get(key);
                Iterator<String> duplist = dups.iterator();
                while (duplist.hasNext()) {
                    String js = duplist.next();
                    log(js, 2);
                }
            }
        }
        log("#dup: " + i, 2);
        return i;
    }
    
    public static int getFiles(String _sMetaDataUrl, String _sAccessToken2, String _sRefreshToken) {
        
        try {
            String sStartToken = "";
            boolean bContinue = true;
            int i = 0;

            while (bContinue) {
                String sFiles = getFileList(_sMetaDataUrl, _sAccessToken2, "FILE", sStartToken);
                
                if (sFiles.contains("ERROR_TOKEN_INVALID")) {
                    log("MSG: Time to renew token...", 2);
                    String sNewToken = refreshToken(sClientID, _sRefreshToken, sSecret);            
                    String sNewAccessToken2 = sNewToken.split(",")[0];                
                    if (sNewAccessToken2.length() > 0) {
                        bTokenValid = true;
                        _sAccessToken2 = sNewAccessToken2;
                        sFiles = getFileList(_sMetaDataUrl, _sAccessToken2, "FOLDER", sStartToken);
                    }                                
                }                

                JSONObject jtoken =  (JSONObject)JSONValue.parse(sFiles);

                JSONArray companyList = (JSONArray)jtoken.get("data");        
                Iterator<Object> driveIterator = companyList.iterator();

                if (jtoken.containsKey("nextToken")) {
                    sStartToken = jtoken.get("nextToken").toString();
                } else {
                    bContinue = false;
                }            

                p("hasnext = " + driveIterator.hasNext());
                while (driveIterator.hasNext()) {
                    i++;
                    JSONObject driveJSON = (JSONObject)driveIterator.next();

                    //System.out.println(i + " " + driveJSON.toJSONString());
                    JSONObject jtoken2 = (JSONObject)JSONValue.parse(driveJSON.toJSONString());

                    JSONObject props = (JSONObject)jtoken2.get("contentProperties");
                    String sMD5 = props.get("md5").toString();
                    log(i + " " + jtoken2.get("name") + " " + sMD5, 2);

                    String sRecord = jtoken2.toString();
                    p("Saving record to JSON map: " + sRecord.length() + " '" + sRecord + "'");
                    
                    List<String> sChildren = mapUploadedMD5_json.get(sMD5);
                    if (sChildren == null) {
                        sChildren = new ArrayList<String>();
                        sChildren.add(sRecord);
                        mapUploadedMD5_json.put(sMD5, sChildren);
                    } else {
                        sChildren.add(sRecord);
                        mapUploadedMD5_json.put(sMD5, sChildren);
                    }
                    
                    Integer count = mapUploadedMD5.get(sMD5);
                    if (count == null) {
                        mapUploadedMD5.put(sMD5, 1);
                    } else {
                        log("Already in the cloud. #copies: " + count, 2);
                        mapUploadedMD5.put(sMD5, count+1);
                    }
                }   
            }    
            log("#files loaded: " + i, 2);
            Thread.sleep(5000);
            return i;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
  
    }
    
    
    public static String createFolder (String _url, String _clientid, String _token, String _kind, String _foldername, String _parentid ) {
        try {
            String fileUrl = _url + "nodes"; //?localId=" + _clientid;
            
            log("url: " + fileUrl, 2);
            
            String sMetaData = "{\"name\":\"" + _foldername + "\",\"kind\":\"FOLDER\",\"parents\":[\"" + _parentid + "\"]}";

            //String data = URLEncoder.encode("name", "UTF-8") + "=" + URLEncoder.encode(_foldername, "UTF-8");
            //data += "&" + URLEncoder.encode("kind", "UTF-8") + "=" + URLEncoder.encode(_kind, "UTF-8");

            log("data: " + sMetaData, 2);
            
            HttpPost httpPost = new HttpPost(fileUrl);
            httpPost.setHeader("Authorization", "Bearer " + _token);
            httpPost.setEntity(new StringEntity(sMetaData));
            
            int r = -1;
            try (CloseableHttpClient httpclient = HttpClients.createDefault();
                 CloseableHttpResponse response = httpclient.execute(httpPost)) {
                r = response.getCode();
                String resp = EntityUtils.toString(response.getEntity());
                log(r + " resp (POST) = " + resp, 2);  

                if (r == 201) {
                    JSONObject jtoken = (JSONObject)JSONValue.parse(resp);
                    String idnew = jtoken.get("id").toString();
                    
                    mapFolderNames.put(idnew, _foldername);
                    
                    List<String> sChildren = mapFolders.get(_parentid);
                    if (sChildren == null) {
                        sChildren = new ArrayList<String>();
                        sChildren.add(idnew);
                        mapFolders.put(_parentid, sChildren);
                    } else {
                        sChildren.add(idnew);
                        mapFolders.put(_parentid, sChildren);
                    }
                    return idnew;
                } 
                if (r == 401) { //token expired
                    bTokenValid = false;
                }
                if (r == 409) {
                    JSONObject jtoken = (JSONObject)JSONValue.parse(resp);
                    String msg = jtoken.get("message").toString();
                    String idnew = msg.substring(msg.lastIndexOf(" ")+1, msg.length());
                    log("Node already exists!!!: '" + idnew + "'", 2);
                    return idnew;
                }
            }

            if (r >= 500) {
                //some kind of service error, do backoff
                int waited = service_backoff();
            }

            return "";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        
    }

    private static int service_backoff() {
        try {
            nRetryCount++;
            int min = 0;
            int max = (int)Math.pow(2, Math.min(nRetryCount, 8));
            Random generator = new Random();
            int roll = generator.nextInt((max - min) + 1) + min;                    
            log("Service backoff(" + nRetryCount + ") : " + roll + " seconds. max: " + max, 2);
            Thread.sleep(roll*1000);
            return roll;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    
    static class myResp {
        int r;
        String resp;
    }
    
    public static myResp getWithTimeout(String _url, String _token) {
            myResp myr = new myResp();        

            HttpGet httpGet = new HttpGet(_url);
            httpGet.setHeader("Authorization", "Bearer " + _token);
                        
            long t1 = System.currentTimeMillis();
            System.out.println("before execute: " );
            
            int nRetries = 0;
            int r = -1;
            boolean bContinue = true;
            String resp = "";
            while (nRetries < 5 & bContinue) {
                nRetries++;
                log("try: #" + nRetries, 2);
                try (CloseableHttpClient httpclient = HttpClients.createDefault();
                     CloseableHttpResponse response = httpclient.execute(httpGet)) {
                    r = response.getCode();
                    long t2 = System.currentTimeMillis();
                    long delta = t2 - t1;
                    log("return r: " + r + " in " + delta + "ms", 2);
                    if (r > 0) {
                        resp = EntityUtils.toString(response.getEntity());
                        bContinue = false;
                    }
                } catch (Exception e) { //SocketTimeoutException
                    e.printStackTrace();
                    System.out.println("Exception on executeMethod(): try#" + nRetries + " r:" +r);
                    System.out.println("Sleeping before retry");
                    try {
                        Thread.sleep(5000);
                    } catch (Exception ef) {
                        ef.printStackTrace();
                    }
                }                                          
            }//while            
        myr.r = r;
        myr.resp = resp;
        return myr;
    }
    
    public static String getFileList(String _url, String _token, String _kind, String _starttoken) {
        try {
            String fileUrl = _url + "nodes?filters=kind:" + _kind + "&startToken=" + _starttoken;
            log("fireUrl = " + fileUrl, 2);
    
            myResp myr = getWithTimeout(fileUrl, _token);
            
            String resp = myr.resp;
            int r = myr.r;
            
            String resp_trim = "";
            if (r > 0) {
                resp_trim = resp.substring(0,20);
            }
            log(r + " resp (getFileList) [" + resp.length() + "] = " + resp_trim + "...", 2);                
            
            if (r == 200) {
                return resp;
            } else {
                if (r == 401) {
                    //token invalid
                    bTokenValid = false;
                    resp = "ERROR_TOKEN_INVALID";
                } 
                if (r >= 500) {
                    //some kind of service error, do backoff
                    int waited = service_backoff();
                }       
                log("returning r:" + r + " resp: '" + resp + "'", 2);
                return resp;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
        
    }
    
    public static String getEndpoint(String _token) {
        try {
            String fileUrl = "https://drive.amazonaws.com/drive/v1/account/endpoint";
            log("fireUrl = " + fileUrl, 2);

            HttpGet httpGet = new HttpGet(fileUrl);
            httpGet.setHeader("Authorization", "Bearer " + _token);

            try (CloseableHttpClient httpclient = HttpClients.createDefault();
                 CloseableHttpResponse response = httpclient.execute(httpGet)) {
                int r = response.getCode();
                String resp = EntityUtils.toString(response.getEntity());

                log(r + " resp (POST) = " + resp, 2);
                if (r == 200) {
                    JSONObject jtoken = (JSONObject)JSONValue.parse(resp);
                    String _metadataUrl = jtoken.get("metadataUrl").toString();
                    String _contentUrl = jtoken.get("contentUrl").toString();
                    return _contentUrl + "," + _metadataUrl;                
                } else {
                    if (r == 401) {
                        return "ERROR_TOKEN_INVALID";
                    } else {
                        if (r >= 500) {
                            //some kind of service error, do backoff
                            int waited = service_backoff();
                        }
                        return "ERROR_OTHER: '" + resp + "'";
                    }
                }
            }
            
            
        } catch (Exception e) {
            return "ERROR";
        }
    }
    
    
    public static String getAccessCode(String _clientid, String _redirect_uri) {
        try {
            
            String data = URLEncoder.encode("client_id", "UTF-8") + "=" + URLEncoder.encode(_clientid, "UTF-8");
             data += "&" + URLEncoder.encode("scope", "UTF-8") + "=" + URLEncoder.encode("clouddrive:read clouddrive:write", "UTF-8");
             data += "&" + URLEncoder.encode("response_type", "UTF-8") + "=" + URLEncoder.encode("code", "UTF-8");
             data += "&" + URLEncoder.encode("redirect_uri", "UTF-8") + "=" + URLEncoder.encode(_redirect_uri, "UTF-8");

            String fileUrl = "https://www.amazon.com/ap/oa?" + data;
           
            log("fileUrl = " + fileUrl, 2);
            
            HttpGet httpGet = new HttpGet(fileUrl);

            try (CloseableHttpClient httpclient = HttpClients.createDefault();
                 CloseableHttpResponse response = httpclient.execute(httpGet)) {
                int r = response.getCode();
                String resp = EntityUtils.toString(response.getEntity());
            
                log("resp = " + resp, 2);
                              
                return "";
            }
        } catch (Exception e) {
        return "";
        }
                        
        
    }
    
    public static String refreshToken(String _clientid, String _refresh_token, String _secret) {
                log("**** Time to renew token...", 2);
                try {
                        String data = URLEncoder.encode("grant_type", "UTF-8") + "=" + URLEncoder.encode("refresh_token", "UTF-8");
                        data += "&" + URLEncoder.encode("refresh_token", "UTF-8") + "=" + URLEncoder.encode(_refresh_token, "UTF-8");
                        data += "&" + URLEncoder.encode("client_id", "UTF-8") + "=" + URLEncoder.encode(_clientid, "UTF-8");
                        data += "&" + URLEncoder.encode("client_secret", "UTF-8") + "=" + URLEncoder.encode(_secret, "UTF-8");
                        //System.out.println("***EzPost: '" + data + "'");
                        //httpPost("http://www.google-analytics.com/collect", data);
                        
                        
                        //String fileUrl = String.format("%s://%s/collect", 
                        //                    "http", "www.google-analytics.com"); 
                        
                        
                        String fileUrl = "https://api.amazon.com/auth/o2/token";
                        log("fireUrl = " + fileUrl, 2);
                        
                        log("data = " + data, 2);
                                
                        HttpPost httpPost = new HttpPost(fileUrl);
                        httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
                        httpPost.setEntity(new StringEntity(data));
                       
                        try (CloseableHttpClient httpclient = HttpClients.createDefault();
                             CloseableHttpResponse response = httpclient.execute(httpPost)) {
                            int r = response.getCode();
                            String resp = EntityUtils.toString(response.getEntity());
                        
                            log(r + " resp (POST) = " + resp, 2);
                                                    
                            JSONObject jtoken = (JSONObject)JSONValue.parse(resp);
                            String _accessToken = jtoken.get("access_token").toString();
                            String _refreshToken = jtoken.get("refresh_token").toString();
                            
                            log(r + " access_token: '" + _accessToken + "'", 2);
                            log(r + " refresh_token: '" + _refreshToken + "'", 2);
                         
//                        String appActionToken = "04AAWiWhXbfg3yj2tTEqoSHmsecj3D";
//                        //String savedParameters = "eyJwIjoiNGoxYVphK0lBWFF4dmJOMWd6SUpBRExuY21pWlNOZFB5a2VzWHY0UUhDeUlmUGdaUGxaRHlpNkRQNUNKb055azladWd4R1lDNmoza0VoR3RUVU1JRTdJWmZYTnpUUCsxd1l2WVhFRVFMTjVmQ1B5RVk0WmdjTUZLWUZvOGsvTmVtVzBjNkhzQW9ZallLUU8rU29HQzZLV0JVeUg3cm5Cc29HKzdzbmpvSWNGd2tkQUtiUjd2dENzMzNqNmx0VUsvWTBmZTgyVG1JaGQ2VEJPNG9QbW9FWGlzOEJGa2R1VzdBOFZWUFRpdTVWcnRVZ0tjNnJUTHR6TzhmcHYyTHFlT2xES1h0bmVIazZnWDk3R1U3VkJjeklLU1ppVlRGenNQYWtMNzNBTndhRWhZREpPdGRLaEMzcFR4cGFlVzhDQnhaVElKWENUZTA3Q0puVHg4dkZJR0hHVFZRcHRsYnl3NTNYRXZSNTBOV0NIZWkwQy9jOGowV2R2SzFESkg4RTJxZzdUeTlHUUVIemVrNGVZRnowaFoxQVZWUFFDRTRjSnIrdkY4YVlwVEo0cm5BQXJTcU1ueElMbEp1dmVCbGlsVEdPWGZleHpUeVZxVE0xWSszRkhKV04rUk42SUVHKzRZSW0xcFB5WHljTUpRZk1JVGZMdjh1REgwVTJrRFBoK3VqWFp0eHZUc0FvMU44amdGeXllM21EaGJ3NVkrWEoxMWk0Sy9hcVBUV0UvMGo0VnhVL0Y1R3o0RldCNTR3ejFiVEJtNUFPNTR1eHFRMFQ5SC9PbHI2TFRlNURrbFQxaENneTRLZHBFSWV5QmNhdS84b2QwbGY2MlFSVDh4M1FOa3QweGpDVXlwT2NQVWx5bGJYdFVHYm5lTlVHenRJYWFTRWxJZHBKZ2l5YTNTMGdWbGgrcWpuRlVOYmNIMnRXK0o4RmszekpESFgrM3IvbWQ5NUd3T29PWHhjVjh5Y09vbGNxa2c1cEo0WjhZMEFpU0JiUks0NXlLZ0p4Q2dTOC95VVZiT3pKODBtaGpYQzBlSzE3V3J1TkE4SU9HeDExbHZpY3lycE1zMG9ITUtsQzZrSWRBbzY5ZHYxZXZESlF0OUhZZWowL0U5RkxNYkxXanJoWmladUlzaWM3SDRvTE4xZjNkQjlOdERNODdJZ0NnUGVlZnQzOWdPa3FQdDJRUlRCU2RTQTgwSHgyWEVrVVNLNEphWjRlelBkc1U1M1Mxd1VTakdpWWdXdEpaMzRPWjM2c1FmVW9vS0lGNm9aRnNBVTJ5S25ZWmFaQnBMeVA1MCs0UWExbzdlMFFOSE1Ndi9qMFZoU0NmLzI2aHBtdW1sbStQbDRFQ24zVjE2MDhMcGpIVTdQSjJVb294VFNsSjlPWTBoV2tlTUI4eWdXbTBJNWpseEJsZFlvdXZyYXVjQjZSbzZsMHpRQytkVllsSXlaNXFjbWZ3VFUwa0RhWGovY3NsdWR3PT0iLCJpIjoiYUZVb2pHVXhSak53bFpGZWtvaFNVUT09IiwicyI6IkVwK0dUdlprUjh6U0FrUlcwVWFYY2o4c2pIM05qUzJoR2IxK2QwS1pYbTQ9IiwiZXYiOjEsImh2IjoxfQ";
//                        String savedParameters = "eyJwIjoiMkR6ZEJSVHg4YUJLVnQ3V05CNnAzUHJqQlVJb3BNRWNIdnZjdEpRMzFvREFxTDdWNWtyVHZpTGxBNDViQWNvakgzS0FmTlVHcGFlb3Jid0xhUi85a3VNSmVMOXJYeTBDVUgxeTQ2ZnExSkVMUi83Tmd5VWtkOWdRVVFVWDdQelpVQUREVEllaUxOcHptdHYzN240QlMzWlVHUS8xZGlCY2VscHlUOTk0MmlBdTZ1Ym5nTUFaL1RHYXBMNGZxdWdPWTZHQkoxZmVYbEE1cGRtZFVrZ2FpN21aaTk1azlCbXdRYUpoU3RiMXJmWjRGVkJDYitUMDlIRUh4SzdYaUVRRGJUK1JXelBaRHMzNGRPV3BaMUtVbG5JblVEZzZkK3orWTBYbDhNMDJaTFpPR2d0WUFObmY4alcxTTMycFJqY09aUzUyUjNCVGU0aW5Zb2NtditOOE0yUkt1RWNBUnJpTUhSWm1iSG40R2lVTFJ6Z2dDN1hSZnFyN1I0QmZlb2JsaEJSTEQzeXB0SG9RWEE1aVM1Rzc0eGx2ZTVLQ3VQYU04bmFLK2E3V0poTm5JSVFrZlNhWE1Ldld0amFCMHZHYzByS2ZjN3cxam9veENDSWdzMWJQRXJ3SFF5VFA0SC90MWtheFI0RmkySkFOTGpuNVJqUEJQVWZYZVdTT1ZrZ3ZSVGtNMnJmeENuVUM5WEduYXdHTlpISi9XQVBTckdjNXRZZW83bUJENmpTcTh2eEgra3RoT1FjWjlaRVdkMk5vVTViKytIUnpwTDRZTjZOVVJvanV4cDhBenhTOUhIRG9yWWRveGNYWm1MbDUxYlFRMVRJVWp3bFBWV0YvbCs5Z05WSjJHekhUd1R2ODFzdlo3MnlMT2kyS2g5dTBYWlByeERJdXgwUVd0ZjVMbDVCaE9CNW4rVkxpYXh6VmR1NzVnZWpCT3hVTlB6QTZEVE1ZcEl5VTFjWW4zT290ZEl6Q1FNMHNQWWF0dlVRQnRGRkloY1hYeUcvaXFZNC9zZGVqNmJNTkR5T2I5V25iVzBLZk0zSW1WZUk2eXA4dWxkVDFxUHJQczZxanNzQU9VTHNQdmw1RHZNNjY4T3hlUEJXbTdRVXBIT3QzcFdjRmJhUlhhWEZydXFyUHhEY09lV3lFYWEyNHJuQ3B4ck9uZGlPSUNvWkl4dXpzancxbWh0YzFMNXhmU3FCbC8waW1rZWw5UlRoTHNlVlMzQndrems4dkN0RzFMc1Nkbkd0WmRXaGsvQk5pRVdiSUNTUGZzL0h0UDBBc2ZlMVFrdjN4OXk5TTU2b2JqaGZnQzIzTHN3WEZQWjRwMTRMVUJiUVVMSjRodTZ0SFRvWFNudVNGVTNUakZKci9JZXdnc0N4eWgyWFl4SGJjMjg4SGRBQnkvYTBFbmwvSENXczhTVUpCOFFaaW9ZNkNYWVFMVGdwVFhKYi9HU2Z1SkNUcmFmZFgrWkJ3bVVkQWtncTFLTUordFd1Vk1DU2t4UHozK05JPSIsImkiOiJqRnNmcVI3bHN0TCtYb3BhOFcyYlNBPT0iLCJzIjoiZjlvT3RhVDU5eG9oeDQwa3VPL3FzdmpKTnJHRHN5UE1BblJiT3F0Skw3VT0iLCJldiI6MSwiaHYiOjF9";
//         
//                        //data = URLEncoder.encode("client_id", "UTF-8") + "=" + URLEncoder.encode(_clientid, "UTF-8");
//                        data = URLEncoder.encode("appActionToken", "UTF-8") + "=" + URLEncoder.encode(appActionToken, "UTF-8");
//                        data += "&" + URLEncoder.encode("appAction", "UTF-8") + "=" + URLEncoder.encode("actionField", "UTF-8");
//                        data += "&" + URLEncoder.encode("savedParameters", "UTF-8") + "=" + URLEncoder.encode(savedParameters, "UTF-8");
//                        data += "&" + URLEncoder.encode("acknowledgementApproved", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8");
//
//
//                        fileUrl = "https://www.amazon.com/ap/oa?" + data;
//                        System.out.println("fireUrl2 = " + fileUrl);
//
//                        GetMethod getFile2 = new GetMethod(fileUrl);
//                        
//                        //postFile.setRequestEntity(new StringRequestEntity(data,null,null));
//
//                        //r = httpclient.executeMethod(getFile2);
//
//                        Header pp[] = getFile2.getRequestHeaders();
//                        
//                        System.out.println("Header = " + pp.toString());
//                                
//                        resp = getFile2.getResponseBodyAsString();
//                        
//                        System.out.println(r + " resp(FORM) = " + resp);

                            return _accessToken + "," + _refreshToken; 
                        }
                        
                } catch (Exception e) {
                        System.err.println("ezPostCount exception:  " + e);
                        return "";
                }
}
    
    public static String getAccessToken(String _clientid, String _code, String _redirect_uri, String _secret) {
                try {
                        String data = URLEncoder.encode("grant_type", "UTF-8") + "=" + URLEncoder.encode("authorization_code", "UTF-8");
                        data += "&" + URLEncoder.encode("code", "UTF-8") + "=" + URLEncoder.encode(_code, "UTF-8");
                        data += "&" + URLEncoder.encode("client_id", "UTF-8") + "=" + URLEncoder.encode(_clientid, "UTF-8");
                        data += "&" + URLEncoder.encode("client_secret", "UTF-8") + "=" + URLEncoder.encode(_secret, "UTF-8");
                        data += "&" + URLEncoder.encode("redirect_uri", "UTF-8") + "=" + _redirect_uri; //URLEncoder.encode(_redirect_uri, "UTF-8");
                        //System.out.println("***EzPost: '" + data + "'");
                        //httpPost("http://www.google-analytics.com/collect", data);
                        
                        
                        //String fileUrl = String.format("%s://%s/collect", 
                        //                    "http", "www.google-analytics.com"); 
                                                
                        String fileUrl = "https://api.amazon.com/auth/o2/token";
                        log("fireUrl = " + fileUrl, 2);
                        
                        log("data = " + data, 2);
                                
                        HttpPost httpPost = new HttpPost(fileUrl);
                        httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");
                        httpPost.setEntity(new StringEntity(data));
                       
                        try (CloseableHttpClient httpclient = HttpClients.createDefault();
                             CloseableHttpResponse response = httpclient.execute(httpPost)) {
                            int r = response.getCode();
                            String resp = EntityUtils.toString(response.getEntity());
                        
                            log(r + " resp (POST) = " + resp, 2);
                            
                            if (r == 200) {
                                JSONObject jtoken = (JSONObject)JSONValue.parse(resp);
                                String _accessToken = jtoken.get("access_token").toString();
                                String _refreshToken = jtoken.get("refresh_token").toString();                            
                                log(r + " access_token: '" + _accessToken + "'", 2);
                                log(r + " refresh_token: '" + _refreshToken + "'", 2);
                                return _accessToken + "," + _refreshToken; 
                            } else {
                                if (r == 400) {
                                    return "ERROR_INVALID_CODE";
                                } else {
                                    if (r >= 500) {
                                        //some kind of service error, do backoff
                                        int waited = service_backoff();
                                    }
                                    return "ERROR_OTHER: " + r + "'" + resp + "'";
                                }
                            }
                        }
                                                
                        
                         
//                        String appActionToken = "04AAWiWhXbfg3yj2tTEqoSHmsecj3D";
//                        //String savedParameters = "eyJwIjoiNGoxYVphK0lBWFF4dmJOMWd6SUpBRExuY21pWlNOZFB5a2VzWHY0UUhDeUlmUGdaUGxaRHlpNkRQNUNKb055azladWd4R1lDNmoza0VoR3RUVU1JRTdJWmZYTnpUUCsxd1l2WVhFRVFMTjVmQ1B5RVk0WmdjTUZLWUZvOGsvTmVtVzBjNkhzQW9ZallLUU8rU29HQzZLV0JVeUg3cm5Cc29HKzdzbmpvSWNGd2tkQUtiUjd2dENzMzNqNmx0VUsvWTBmZTgyVG1JaGQ2VEJPNG9QbW9FWGlzOEJGa2R1VzdBOFZWUFRpdTVWcnRVZ0tjNnJUTHR6TzhmcHYyTHFlT2xES1h0bmVIazZnWDk3R1U3VkJjeklLU1ppVlRGenNQYWtMNzNBTndhRWhZREpPdGRLaEMzcFR4cGFlVzhDQnhaVElKWENUZTA3Q0puVHg4dkZJR0hHVFZRcHRsYnl3NTNYRXZSNTBOV0NIZWkwQy9jOGowV2R2SzFESkg4RTJxZzdUeTlHUUVIemVrNGVZRnowaFoxQVZWUFFDRTRjSnIrdkY4YVlwVEo0cm5BQXJTcU1ueElMbEp1dmVCbGlsVEdPWGZleHpUeVZxVE0xWSszRkhKV04rUk42SUVHKzRZSW0xcFB5WHljTUpRZk1JVGZMdjh1REgwVTJrRFBoK3VqWFp0eHZUc0FvMU44amdGeXllM21EaGJ3NVkrWEoxMWk0Sy9hcVBUV0UvMGo0VnhVL0Y1R3o0RldCNTR3ejFiVEJtNUFPNTR1eHFRMFQ5SC9PbHI2TFRlNURrbFQxaENneTRLZHBFSWV5QmNhdS84b2QwbGY2MlFSVDh4M1FOa3QweGpDVXlwT2NQVWx5bGJYdFVHYm5lTlVHenRJYWFTRWxJZHBKZ2l5YTNTMGdWbGgrcWpuRlVOYmNIMnRXK0o4RmszekpESFgrM3IvbWQ5NUd3T29PWHhjVjh5Y09vbGNxa2c1cEo0WjhZMEFpU0JiUks0NXlLZ0p4Q2dTOC95VVZiT3pKODBtaGpYQzBlSzE3V3J1TkE4SU9HeDExbHZpY3lycE1zMG9ITUtsQzZrSWRBbzY5ZHYxZXZESlF0OUhZZWowL0U5RkxNYkxXanJoWmladUlzaWM3SDRvTE4xZjNkQjlOdERNODdJZ0NnUGVlZnQzOWdPa3FQdDJRUlRCU2RTQTgwSHgyWEVrVVNLNEphWjRlelBkc1U1M1Mxd1VTakdpWWdXdEpaMzRPWjM2c1FmVW9vS0lGNm9aRnNBVTJ5S25ZWmFaQnBMeVA1MCs0UWExbzdlMFFOSE1Ndi9qMFZoU0NmLzI2aHBtdW1sbStQbDRFQ24zVjE2MDhMcGpIVTdQSjJVb294VFNsSjlPWTBoV2tlTUI4eWdXbTBJNWpseEJsZFlvdXZyYXVjQjZSbzZsMHpRQytkVllsSXlaNXFjbWZ3VFUwa0RhWGovY3NsdWR3PT0iLCJpIjoiYUZVb2pHVXhSak53bFpGZWtvaFNVUT09IiwicyI6IkVwK0dUdlprUjh6U0FrUlcwVWFYY2o4c2pIM05qUzJoR2IxK2QwS1pYbTQ9IiwiZXYiOjEsImh2IjoxfQ";
//                        String savedParameters = "eyJwIjoiMkR6ZEJSVHg4YUJLVnQ3V05CNnAzUHJqQlVJb3BNRWNIdnZjdEpRMzFvREFxTDdWNWtyVHZpTGxBNDViQWNvakgzS0FmTlVHcGFlb3Jid0xhUi85a3VNSmVMOXJYeTBDVUgxeTQ2ZnExSkVMUi83Tmd5VWtkOWdRVVFVWDdQelpVQUREVEllaUxOcHptdHYzN240QlMzWlVHUS8xZGlCY2VscHlUOTk0MmlBdTZ1Ym5nTUFaL1RHYXBMNGZxdWdPWTZHQkoxZmVYbEE1cGRtZFVrZ2FpN21aaTk1azlCbXdRYUpoU3RiMXJmWjRGVkJDYitUMDlIRUh4SzdYaUVRRGJUK1JXelBaRHMzNGRPV3BaMUtVbG5JblVEZzZkK3orWTBYbDhNMDJaTFpPR2d0WUFObmY4alcxTTMycFJqY09aUzUyUjNCVGU0aW5Zb2NtditOOE0yUkt1RWNBUnJpTUhSWm1iSG40R2lVTFJ6Z2dDN1hSZnFyN1I0QmZlb2JsaEJSTEQzeXB0SG9RWEE1aVM1Rzc0eGx2ZTVLQ3VQYU04bmFLK2E3V0poTm5JSVFrZlNhWE1Ldld0amFCMHZHYzByS2ZjN3cxam9veENDSWdzMWJQRXJ3SFF5VFA0SC90MWtheFI0RmkySkFOTGpuNVJqUEJQVWZYZVdTT1ZrZ3ZSVGtNMnJmeENuVUM5WEduYXdHTlpISi9XQVBTckdjNXRZZW83bUJENmpTcTh2eEgra3RoT1FjWjlaRVdkMk5vVTViKytIUnpwTDRZTjZOVVJvanV4cDhBenhTOUhIRG9yWWRveGNYWm1MbDUxYlFRMVRJVWp3bFBWV0YvbCs5Z05WSjJHekhUd1R2ODFzdlo3MnlMT2kyS2g5dTBYWlByeERJdXgwUVd0ZjVMbDVCaE9CNW4rVkxpYXh6VmR1NzVnZWpCT3hVTlB6QTZEVE1ZcEl5VTFjWW4zT290ZEl6Q1FNMHNQWWF0dlVRQnRGRkloY1hYeUcvaXFZNC9zZGVqNmJNTkR5T2I5V25iVzBLZk0zSW1WZUk2eXA4dWxkVDFxUHJQczZxanNzQU9VTHNQdmw1RHZNNjY4T3hlUEJXbTdRVXBIT3QzcFdjRmJhUlhhWEZydXFyUHhEY09lV3lFYWEyNHJuQ3B4ck9uZGlPSUNvWkl4dXpzancxbWh0YzFMNXhmU3FCbC8waW1rZWw5UlRoTHNlVlMzQndrems4dkN0RzFMc1Nkbkd0WmRXaGsvQk5pRVdiSUNTUGZzL0h0UDBBc2ZlMVFrdjN4OXk5TTU2b2JqaGZnQzIzTHN3WEZQWjRwMTRMVUJiUVVMSjRodTZ0SFRvWFNudVNGVTNUakZKci9JZXdnc0N4eWgyWFl4SGJjMjg4SGRBQnkvYTBFbmwvSENXczhTVUpCOFFaaW9ZNkNYWVFMVGdwVFhKYi9HU2Z1SkNUcmFmZFgrWkJ3bVVkQWtncTFLTUordFd1Vk1DU2t4UHozK05JPSIsImkiOiJqRnNmcVI3bHN0TCtYb3BhOFcyYlNBPT0iLCJzIjoiZjlvT3RhVDU5eG9oeDQwa3VPL3FzdmpKTnJHRHN5UE1BblJiT3F0Skw3VT0iLCJldiI6MSwiaHYiOjF9";
//         
//                        //data = URLEncoder.encode("client_id", "UTF-8") + "=" + URLEncoder.encode(_clientid, "UTF-8");
//                        data = URLEncoder.encode("appActionToken", "UTF-8") + "=" + URLEncoder.encode(appActionToken, "UTF-8");
//                        data += "&" + URLEncoder.encode("appAction", "UTF-8") + "=" + URLEncoder.encode("actionField", "UTF-8");
//                        data += "&" + URLEncoder.encode("savedParameters", "UTF-8") + "=" + URLEncoder.encode(savedParameters, "UTF-8");
//                        data += "&" + URLEncoder.encode("acknowledgementApproved", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8");
//
//
//                        fileUrl = "https://www.amazon.com/ap/oa?" + data;
//                        System.out.println("fireUrl2 = " + fileUrl);
//
//                        GetMethod getFile2 = new GetMethod(fileUrl);
//                        
//                        //postFile.setRequestEntity(new StringRequestEntity(data,null,null));
//
//                        //r = httpclient.executeMethod(getFile2);
//
//                        Header pp[] = getFile2.getRequestHeaders();
//                        
//                        System.out.println("Header = " + pp.toString());
//                                
//                        resp = getFile2.getResponseBodyAsString();
//                        
//                        System.out.println(r + " resp(FORM) = " + resp);

                        
                        
                } catch (Exception e) {
                        System.err.println("ezPostCount exception:  " + e);
                        return "";
                }
}
    
    public static int SyncRecords() {
        
        Integer nBoth = 0;
        Integer nNew = 0;
        Integer nDeleted = 0;
        Iterator<String> mapAmazon = mapUploadedMD5.keySet().iterator();
        while (mapAmazon.hasNext()) {
            String sMD5Amazon = mapAmazon.next();
            if (mapRecordsMD5.containsKey(sMD5Amazon)) {
                //BOTH EXIST
                nBoth++;
            } else {
                //MISSING FROM RECORDS
                log(sMD5Amazon + " - Missing from Records. (NEW)", 2);
                List<String> newf = mapUploadedMD5_json.get(sMD5Amazon);
                Iterator<String> newfi = newf.iterator();
                while (newfi.hasNext()) {
                    String newfi_rec = newfi.next();
                    p("JSON_new: '" + newfi_rec + "'");
                } 
                  
                nNew++;
            }
        }
        
        Iterator<String> mapRecords = mapRecordsMD5.keySet().iterator();
        while (mapRecords.hasNext()) {
            String sMD5Records = mapRecords.next();
            if (mapUploadedMD5.containsKey(sMD5Records)) {
                //BOTH EXIST
            } else {
                //MISSING FROM AMAZON
                log(sMD5Records + " - Missing from Amazon. (DELETED)", 2);
                List<String> newf = mapRecordsMD5_json.get(sMD5Records);
                Iterator<String> newfi = newf.iterator();
                while (newfi.hasNext()) {
                    String newfi_rec = newfi.next();
                    log("JSON_del: '" + newfi_rec + "'", 2);
                } 
                nDeleted++;
            }
        }
        
        log("Sync Report-----", 2);
        log("Both   :" + nBoth, 2);
        log("New    :" + nNew, 2);
        log("Deleted:" + nDeleted, 2);     
        
        return 0;
    }
    
    public static int LoadCache() {
        try {
            log(mStorage, 2);
            File storage = new File(mStorage);            
            if (!storage.exists())
                return -1;
            FileInputStream fileIn = new FileInputStream(mStorage);
            ObjectInputStream in = new ObjectInputStream(fileIn);            
            //mFilesDatabase = (HashMap<Key, FileDatabaseEntry>) in.readObject();
            mapUploadedMD5 = (HashMap<String, Integer>) in.readObject();
            in.close();
            fileIn.close();
            log("CACHE Loaded # records (md5): " + mapUploadedMD5.size(), 2);

            log(mStorage_json, 2);
            storage = new File(mStorage_json);            
            if (!storage.exists())
                return -1;
            fileIn = new FileInputStream(mStorage_json);
            in = new ObjectInputStream(fileIn);            
            //mFilesDatabase = (HashMap<Key, FileDatabaseEntry>) in.readObject();
            mapUploadedMD5_json = (HashMap<String, List<String>>) in.readObject();
            in.close();
            fileIn.close();
            log("CACHE Loaded # records: (md5_json)" + mapUploadedMD5_json.size(), 2);

            log(mStorage_folders, 2);
            storage = new File(mStorage_folders);            
            if (!storage.exists())
                return -1;
            fileIn = new FileInputStream(mStorage_folders);
            in = new ObjectInputStream(fileIn);            
            //mFilesDatabase = (HashMap<Key, FileDatabaseEntry>) in.readObject();
            mapFolders = (HashMap<String, List<String>>) in.readObject();
            in.close();
            fileIn.close();
            log("CACHE Loaded # records (folders): " + mapFolders.size(), 2);

            log(mStorage_folders_names, 2);
            storage = new File(mStorage_folders_names);            
            if (!storage.exists())
                return -1;
            fileIn = new FileInputStream(mStorage_folders_names);
            in = new ObjectInputStream(fileIn);            
            //mFilesDatabase = (HashMap<Key, FileDatabaseEntry>) in.readObject();
            mapFolderNames = (HashMap<String, String>) in.readObject();
            in.close();
            fileIn.close();
            log("CACHE Loaded # records (folder names): " + mapFolderNames.size(), 2);

            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    
    public static int LoadRecords() {
        try {
            log(mStorage, 2);
            File storage = new File(mStorage);            
            if (!storage.exists())
                return -1;
            FileInputStream fileIn = new FileInputStream(mStorage);
            ObjectInputStream in = new ObjectInputStream(fileIn);            
            //mFilesDatabase = (HashMap<Key, FileDatabaseEntry>) in.readObject();
            mapRecordsMD5 = (HashMap<String, Integer>) in.readObject();
            in.close();
            fileIn.close();
            log("Loaded # records: " + mapRecordsMD5.size(), 2);

            log(mStorage_json, 2);
            storage = new File(mStorage_json);            
            if (!storage.exists())
                return -1;
            fileIn = new FileInputStream(mStorage_json);
            in = new ObjectInputStream(fileIn);            
            //mFilesDatabase = (HashMap<Key, FileDatabaseEntry>) in.readObject();
            mapRecordsMD5_json = (HashMap<String, List<String>>) in.readObject();
            in.close();
            fileIn.close();
            log("Loaded # records: " + mapRecordsMD5_json.size(), 2);
            
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    
    public static int SaveMaptoFile() {
        
        try {
            FileOutputStream fileOut = null;
            ObjectOutputStream out = null;
            
            if ((mapUploadedMD5 != null)) {
                log("Saving # records:" + mapUploadedMD5.size(), 2);
                fileOut = new FileOutputStream(mStorage);
                out = new ObjectOutputStream(fileOut);
                out.writeObject(mapUploadedMD5);
                out.close();
                fileOut.close();  
            } else {
                log("Skip Saving mapUploadedM5", 2);                
            }
            
            if ((mapUploadedMD5_json != null)) {
                log("Saving # records: (json)" + mapUploadedMD5_json.size(), 2);
                fileOut = new FileOutputStream(mStorage_json);
                out = new ObjectOutputStream(fileOut);
                out.writeObject(mapUploadedMD5_json);
                out.close();
                fileOut.close();                  
            } else {
                log("Skip Saving mapUploadedM5_json", 2);                
            }

            if ((mapFolders != null)) {
                log("Saving # records: (folders)" + mapFolders.size(), 2);
                fileOut = new FileOutputStream(mStorage_folders);
                out = new ObjectOutputStream(fileOut);
                out.writeObject(mapFolders);
                out.close();
                fileOut.close();  
            } else {
                log("Skip Saving mapFolders", 2);                
            }
            
            if ((mapFolderNames != null)) {            
                log("Saving # records: (folders_names)" + mapFolderNames.size(), 2);
                fileOut = new FileOutputStream(mStorage_folders_names);
                out = new ObjectOutputStream(fileOut);
                out.writeObject(mapFolderNames);
                out.close();
                fileOut.close();  
            } else {
                log("Skip Saving mapFolderNames", 2);                
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        
        return 0;
    }
    
    public int ProcessBatches(int nBatchStart, int nBatchEnd, String sContentUrl, String sAccessToken2, String sAlteranteFolderID, String sMetaDataUrl, String sClientID, String sRefreshToken_) {
        int j = nBatchStart;
        nUploadedFiles = 0;
        while (j <= nBatchEnd) {
        //for (int j=139; j<1000; j++) {
            log("Processing Batch # " + j, 2);
            int n = uploadBatch(j, sContentUrl, sAccessToken2, sAlteranteFolderID, sMetaDataUrl, sClientID);  
            log("n: " + n, 2);
            log("#UploadedFiles         : " + nUploadedFiles, 2);
            log("#UploadedBytes (MB)    : " + nUploadedBytes / 1024 / 1024, 2);
            log("#UploadedDUP / Skipped : " + nUploadedDUP, 2);
            log("#CloudDUP / Skipped    : " + nCloudDUP, 2);
            log("#Upload BackOffs       : " + nUploadedBackoff, 2);
            log("#Errors                : " + nErrorsUpload, 2);
            if (n < 0) {
                //there were errors, check if token valid.
                if (!bTokenValid) {
                    String sNewToken = refreshToken("amzn1.application-oa2-client.abd0735f6ece48e9a556ac2b875774b2", sRefreshToken_, "c976a7300b41707f68508b9383d369b73573be6614d9b0bcdd89dc8d937c9547");            
                    sAccessToken2 = sNewToken.split(",")[0];                
                    if (sAccessToken2.length() > 0) bTokenValid = true;
                }                
            } else {
                //all good, go to next
                int nRes = NetUtils.UpdateConfig("amazon_batch", String.valueOf(j), "../rtserver/config/www-cloud-batches.properties");
                log("Update batch nres: " + nRes, 2);
                j++;
            }
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }   
        }
            return 0;
    }
    
   public void terminate() {
       log("Recieved Termination request.", 0);
       mTerminated = true;       
   }
 
    
    public void run() {
        while (!mTerminated) {
            try {
                p("RUNNER");
                runner();
                p("EXIT RUNNER RUNNER");
                Thread.sleep(15000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            
        }
    }
    
    public void test_service_backoff() {
        int ns = service_backoff();
        log("waited : " + ns, 2);

        ns = service_backoff();
        log("waited : " + ns, 2);
        
        ns = service_backoff();
        log("waited : " + ns, 2);
        
        ns = service_backoff();
        log("waited : " + ns, 2);
        
        ns = service_backoff();
        log("waited : " + ns, 2);
        
        ns = service_backoff();
        log("waited : " + ns, 2);
        
        ns = service_backoff();
        log("waited : " + ns, 2);

        ns = service_backoff();
        log("waited : " + ns, 2);
        
    }
    
    public void clean_maps() {
            //clean up maps in memory (from previous runs)
            if (mapRecordsMD5.size() > 0) {
                log("Clean mapRecordsMD5. Size: " + mapRecordsMD5.size(), 2);
                mapRecordsMD5.clear();
                mapRecordsMD5 = null;                
            }
            if (mapRecordsMD5_json.size() > 0) {
                log("Clean mapRecordsMD5_json. Size: " + mapRecordsMD5_json.size(), 2);
                mapRecordsMD5_json.clear();
                mapRecordsMD5_json = null;
            }
            if (mapUploadedMD5_json.size() > 0) {
                log("Clean mapUploadedMD5. Size: " + mapUploadedMD5_json.size(), 2);
                mapUploadedMD5_json.clear();
                mapUploadedMD5_json = null;            
            }
            if (mapUploadedMD5.size() > 0) {
                log("Clean mapUploadedMD5. Size: " + mapUploadedMD5.size(), 2);
                mapUploadedMD5.clear();
                mapUploadedMD5 = null;                            
            }
            if (mapFolders.size() > 0) {
                log("Clean mapFolders. Size: " + mapFolders.size(), 2);
                mapFolders.clear();
                mapFolders = null;                            
            }
            if (mapFolderNames.size() > 0) {
                log("Clean mapFolderNames. Size: " + mapFolderNames.size(), 2);
                mapFolderNames.clear();
                mapFolderNames = null;                            
            }        
    }
    
    public void runner() {
        
        if (mapRecordsMD5 == null) mapRecordsMD5 = new HashMap<String, Integer>();
        if (mapRecordsMD5_json == null) mapRecordsMD5_json = new HashMap<String, List<String>>();
        if (mapUploadedMD5 == null) mapUploadedMD5 = new HashMap<String, Integer>();
        if (mapUploadedMD5_json == null) mapUploadedMD5_json = new HashMap<String, List<String>>();
        if (mapFolders == null) mapFolders = new HashMap<String, List<String>>();
        if (mapFolderNames == null) mapFolderNames = new HashMap<String, String>();
                
        boolean bRunning = true;
        boolean bTokenValid = false;
        //String sClientID = "amzn1.application-oa2-client.abd0735f6ece48e9a556ac2b875774b2";
        //String sAccessToken = "Atza|IQEBLzAtAhUAi9DBmWSfs60x-u45lHy-hO5b1WkCFD65rNvM_V4PRzlTx-HyRuXCGV7HOLfjjbIf1HJO3AVg_cAj8qbU5MH53c1T9hNzym-ozo2DLWtV87gnfACH9LmSTwRCpEJrLEcp75iRdumBIW0ZJNpiHwmmtn_ddHC9m_eLc2OyeLgeekeoVKTZ_hKkcOCeJr-xt3VRSRQX-trHli1G-lk3uN1q28JbibouVdhmzhrr3wwBcndlxmmHjDfG8jfH6IHvlsyZ2d7gCPNEKWFI5mLCWt_Qd2muOTQ_QcxKJ5YovkmPYUjcTfPXMguAeoF-RatWyb8blIy_9NeIyG8BZSd40_iw0zTucj6YkdlSGJbGRs4rB1YK3glzulzUcu-hHe_LuwbHrUrvIrzxW3PacjSbt0npueeeY2oxmGMUhx9iRq7p-9upNsvU5Oa_IB4L9w";
        //String sCode = "ANDerFflqnEuSESOiFsI"; //"ANGElaEGwZCKqlBqJDPS"; //ANSNzSEnUdvrVuRxxcea";
        //String sSecret = "c976a7300b41707f68508b9383d369b73573be6614d9b0bcdd89dc8d937c9547";
        
        String sCode = NetUtils.getConfig("amazon_code", "../rtserver/config/www-cloud.properties");
        
        if (sCode.length() == 0) {
            log("MSG: No Amazon Code...", 1);
            //bRunning = false;
        } 
        
        sClientID = NetUtils.getConfig("amazon_clientid", "../rtserver/config/www-cloud.properties");
                
        if (sClientID.length() == 0) {
            log("MSG: No Amazon Client ID...", 1);
            //bRunning = false;
            sClientID = "amzn1.application-oa2-client.abd0735f6ece48e9a556ac2b875774b2";
        } 
                
        String sAccessToken = NetUtils.getConfig("amazon_token", "../rtserver/config/www-cloud.properties");
        
        if (sAccessToken.length() == 0) {
            log("MSG: No Amazon Token...", 1);
            //bRunning = false;
        } 
        
        String sRefreshToken = NetUtils.getConfig("amazon_token_refresh", "../rtserver/config/www-cloud.properties");
        
        if (sAccessToken.length() == 0) {
            log("MSG: No Amazon Refresh Token...", 1);
            //bRunning = false;
        } 
        
        String sDriveEnabled = NetUtils.getConfig("drive_amazon", "../rtserver/config/www-cloud.properties");
        
        if (sDriveEnabled.length() == 0) {
            log("MSG: No Amazon Drive Flag...", 1);
            //bRunning = false;
        } 
        
        String sPort = NetUtils.getConfig("port", "../rtserver/config/www-server.properties");
        if (sPort.length() == 0) {
            log("MSG: No RT Port...", 1);
        }
        String sRedirectURI = "http://localhost:" + sPort + "/cass/setup.htm?spage=10";
        
        String sRedirect = NetUtils.getConfig("amazon_redirect", "../rtserver/config/www-cloud.properties");
        
        if (sRedirect.length() > 0) {
            log("MSG: new Redirect URI: " + sRedirect, 1);
            sRedirectURI = sRedirect;
        } else {
            log("MSG: No Amazon Drive Flag...", 1);
            //bRunning = false;            
        }
        
        boolean bDoSync = false;  //sync disabled by default
        String sDoSync = NetUtils.getConfig("amazon_sync", "../rtserver/config/www-cloud.properties");
        if (sDoSync.length() > 0) {
            log("MSG: new DoSync: " + sDoSync, 1);
            bDoSync = Boolean.parseBoolean(sDoSync);
        }
        
        
        boolean bUseCache= false;
        long lTime_cache = System.currentTimeMillis();
        String sFileCache = NetUtils.getConfig("amazon_cache_ts", "../rtserver/config/www-cloud-cache.properties");
        if (sFileCache.length() > 0) {
            long lFileCache = Long.parseLong(sFileCache);
            long lDelta_cache = (lTime_cache - lFileCache) / 1000;  //seconds old
            log("Cache_age: '" + lDelta_cache + "'", 2);
            String sFileCacheHours = NetUtils.getConfig("amazon_cache_days", "../rtserver/config/www-cloud.properties");
            int nDays = 7;
            if (sFileCacheHours.length() > 0) {
                nDays = Integer.parseInt(sFileCacheHours);
            }
            log("Cache_days: '" + nDays, 2);
            if (lDelta_cache < nDays * 24 * 60 * 60) {
                //cache is less than 1 day old, we can use it.
                bUseCache = true;
            }            
        } else {
            log("No Cache found.", 2);
        }
                
        //200 access_token: 'Atza|IQEBLjAsAhR-nMX4t1yNvLjp2rrOtG9qlzTOkwIUAjI2GCeUZmMCe5H7us11lSAFqmlI1jrTZe1IrPnBrXcE5gvvr4LZ2rGyxKfEfsPmhG4YEGtrDSKc_1ALjqePrJicLTS-IvrBnLFWN_CXuVtZovJmnalVGBG0Ctlvi8OtOBHg1xTqb8kdyyj-topzBy-JhTkqCCXq40j_WyFkLwFREssnLMKt_R0jY0XTJRsAAYm62HJN11HulSwgfg1LN9hwl_VlZSPBE-8_j2_w0Cj9i9nlvTIO5QQUVitouI9Sw5l4xuM0OT4YUhJMITLjjPxmO2xcTIkXM26v0jU30cHK4Vkc5a27PJ1wUv5YnEoyXelMffpYhvHNnNAeud2EbOP2rhUolqF2VTOLmxuYz5n7FWkzxPgaNVbv55hdM5TVruRUeQKO8VpIdrY8L0YTCDKesm3C'
        //200 refresh_token: 'Atzr|IQEBLjAsAhRoYYB9n5Cc1uk-Ch7Mkl8G86bWAwIUGTsJraPvZIPpfzBnTgGL2Jo8ldgF8ZzP44jfQhmvhb77klG66j-50ZBxqomsPNY-cUNY6KZHloKPdZE6kzdaOzq2amgd-HfeP4xKDDT5DScfzdMi-SzY9Z1AdplijpK49h4qnPwry7LH8tTuKyMrqmxFl-cl567MGmQWAmpQ3t389AavEz7Kx-MISiSuFreM4V0gngkM8tUycOsOvQGNDYdl5ikHCnSA2aoLcGdRX8AhNt-K_adhzSQyk_y42dFu8yT86ipPw4fjR_yMwX5SOOPu0vpt0qItf6wBl8HshYJhySWgm5MmiFTwIR4hXYusCvdGeuSa_ecMB6J3YulPWJR7wkaA_RG7OkOykzDk2N1z5pY9Gg3yfAHKbgI01jRCvp3785c'

        log("Enabled       : '" + sDriveEnabled + "'", 2);
        log("Code          : '" + sCode + "'", 2);
        log("ClientID      : length (" + sClientID.length() + ")", 2);
        log("Secret        : length (" + sSecret.length() + ")", 2);
        log("Token         : '" + sAccessToken + "'", 2);
        log("Token_Refresh : '" + sRefreshToken + "'", 2);
        log("DoSync        : '" + bDoSync + "'", 2);
        log("UseCache      : '" + bUseCache + "'", 2);
        
        if (bUseCache) {
            log("LoadCache() ...", 1);
            int n = LoadCache();
            log("LoadCache res: " + n, 1);
        }
        
        if (sDriveEnabled.equals("true")) {
            if (sAccessToken.length() > 0) {
                    //test token
                    log("Testing tokens...", 1);
                    sEndPoint = getEndpoint(sAccessToken);
                    if (sEndPoint.contains("ERROR_TOKEN_INVALID")) {
                        String sNewTokens = refreshToken(sClientID, sRefreshToken, sSecret);            
                        String sNewToken = sNewTokens.split(",")[0];
                        log("NEW TOKEN: " + sNewToken, 1);
                        sAccessToken = sNewToken; 
                        bTokenValid = true;

                        int nRes = NetUtils.UpdateConfig("amazon_token", sNewToken, "../rtserver/config/www-cloud.properties");                                        

                    } else {
                        if (!sEndPoint.contains("ERROR")) bTokenValid = true;
                        //store time when endpoint was fetched
                        lEndpointCache = System.currentTimeMillis();
                        //store endpoints in persisted cache
                        int nRes = 0;
                        nRes = NetUtils.UpdateConfig("amazon_endpoint", sEndPoint, "../rtserver/config/www-cloud-endpoint.properties");                                        
                        nRes = NetUtils.UpdateConfig("amazon_endpoint_ts", String.valueOf(lEndpointCache), "../rtserver/config/www-cloud-endpoint.properties");                                        
                    }
           }            
        } else {
            log("MSG: Amazon Drive not enabled...", 2);
            while (!sDriveEnabled.equals("true")) {
                try {
                    Thread.sleep(10000);
                    sDriveEnabled = NetUtils.getConfig("drive_amazon", "../rtserver/config/www-cloud.properties");
                } catch (Exception e) {  
                    e.printStackTrace();
                }
            }
            bRunning = true; 
            //read new code
            sCode = NetUtils.getConfig("amazon_code", "../rtserver/config/www-cloud.properties");
            
            clean_maps();

        }
        
        while (bRunning) {
            
            if (bTokenValid) {
                log("Token valid. Ready for work...", 2);
            } else {
                log("---- Tokens invalid. Obtaining Token", 2);
                String sToken = getAccessToken(sClientID,   //client id
                            sCode,   ///code
                            sRedirectURI,
                            sSecret);

                if (!sToken.contains("ERROR")) {
                    sAccessToken = sToken.split(",")[0];
                    log("accesstoken = " + sAccessToken, 2);
                    sRefreshToken = sToken.split(",")[1];
                    log("refreshToken = " + sRefreshToken, 2);
                    
                    //store new tokens           
                    int nRes = 0;
                    nRes = NetUtils.UpdateConfig("amazon_token", sAccessToken, "../rtserver/config/www-cloud.properties");                                        
                    nRes = NetUtils.UpdateConfig("amazon_token_refresh", sRefreshToken, "../rtserver/config/www-cloud.properties");   
                    bTokenValid = true;
                } else {
                    if (sToken.contains("ERROR_INVALID_CODE")) {
                        log("MSG: Invalid Code", 2);
                        bRunning = false;
                    }
                }                                
            }
            
            if (bTokenValid) {
                long lTime = System.currentTimeMillis();
                long lDelta = (lTime - lEndpointCache) / 1000;  //seconds old
                if (lDelta > 48 * 60 * 60) {
                    log("Time to re-fetch endpoints: lDelta: " + lDelta , 2);
                    sEndPoint = getEndpoint(sAccessToken);
                    //persist new endpoiints
                    if (!sEndPoint.contains("ERROR")) {
                        lEndpointCache = lTime;
                        int nRes = 0;
                        nRes = NetUtils.UpdateConfig("amazon_endpoint", sEndPoint, "../rtserver/config/www-cloud-endpoint.properties");                                        
                        nRes = NetUtils.UpdateConfig("amazon_endpoint_ts", String.valueOf(lEndpointCache), "../rtserver/config/www-cloud-endpoint.properties");                                                                
                    }
                } else {
                    //use existing endpoint, still valid
                    log("Using existing endpoint: lDelta: " + lDelta,2);
                }
                if (!sEndPoint.contains("ERROR")) {
                    log("All ok...", 2);
                    
                    String sContentUrl = sEndPoint.split(",")[0];
                    log("sContentUrl = " + sContentUrl, 2);
                    String sMetaDataUrl = sEndPoint.split(",")[1];
                    log("sMetaDataUrl = " + sMetaDataUrl, 2);

                    int n = 0;
                    if (!bUseCache) {
                        n = getFiles(sMetaDataUrl, sAccessToken, sRefreshToken);
                        log("getFiles() Number of files: " + n, 2);                        
                    } else {
                        n = mapUploadedMD5.size();
                        log("CACHED Number of files: " + n, 2);                        
                    }

                    try {
                        Thread.sleep(10000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    String sAlteranteFolderID = "";
                    
                    if (n >= 0) {
                        //n = showDUP();   
                        int m = 0;
                        if (bUseCache) {
                            sRootID = NetUtils.getConfig("amazon_cache_root", "../rtserver/config/www-cloud-cache.properties");                                                                                                   
                            log("Root ID (cache): '" + sRootID + "'", 2);
                            if (sRootID.length() > 0) {
                                m = mapFolders.size();
                            }
                            log("CACHE Number of folders: " + m, 2);
                        } else {
                            m = getFolders(sMetaDataUrl, sAccessToken, sRefreshToken);                        
                            log("getFolders() Number of folders: " + m, 2);
                            log("Root ID: '" + sRootID + "'", 2);
                            int nRes = NetUtils.UpdateConfig("amazon_cache_root", sRootID, "../rtserver/config/www-cloud-cache.properties");                                                                                                   
                        }

                        if (m >= 0) {
                           sAlteranteFolderID = getFolderIDbyName("/alterante", sRootID, sMetaDataUrl, sClientID, sAccessToken);
                           log("*** Alterante Folder ID: " + sAlteranteFolderID, 2);
                           
                           try {
                               Thread.sleep(10000);
                           } catch (Exception e) {
                               e.printStackTrace();
                           }

                           //n = browseFolders();
                           //n = browseFolder("tanpJW3XSAKvTIh6QA-BSQ", 0);
                           //n = uploadFolder("/Users/agf/Desktop", ".jpg", sContentUrl, sAccessToken2, sFolderID);
                           //n = uploadFile("/Users/agf/Desktop/IMG_0267_fullcloud.png", sContentUrl, sAccessToken2, sFolderID);

                           if (bDoSync) {
                               n = LoadRecords();
                               //n = showDUP_records();
                               n = SyncRecords();                        
                           } else {
                               log("Skipping Sync...", 2);
                           }

                           if (bUseCache) {
                               log("Using caches, so skip save...", 2);
                           } else {
                                //save new records to disk
                                n = SaveMaptoFile();
                                log("Save Map res: " + n, 2); 
                                if (n == 0) {
                                    //save ok.  store time.
                                    long lTimeCache = System.currentTimeMillis();
                                    int nRes = NetUtils.UpdateConfig("amazon_cache_ts", String.valueOf(lTimeCache), "../rtserver/config/www-cloud-cache.properties");                                                                       
                                }
                           }
 
                           //clean up memory
                           mapRecordsMD5.clear();
                           mapRecordsMD5 = null;
                           mapRecordsMD5_json.clear();
                           mapRecordsMD5_json = null;
                           
                           mapUploadedMD5_json.clear();
                           mapUploadedMD5_json = null;                     
                        } else {
                            log("There were errors getting folders. Exiting...", 2);
                            bRunning = false;                            
                        }                        
                    } else {
                        log("There were errors getting files. Exiting...", 2);
                        bRunning = false;
                    }                    
                                                
                    while(bRunning) {
                        int nBatchEnd = Integer.parseInt(c8.get_batch_id("batchid","BatchJobs", "idx"));
                        int nBatchStart = 1;

                        String sBatchStart = NetUtils.getConfig("amazon_batch", "../rtserver/config/www-cloud-batches.properties");
                        if (sBatchStart.length() > 0) {
                            nBatchStart = Integer.parseInt(sBatchStart) + 1;
                        } else {
                            String sCode2 = NetUtils.getConfig("amazon_code", "../rtserver/config/www-cloud.properties");
                            if (sCode.equals(sCode2)) {
                                log("Code has not changed (First run case)", 2);                                
                            } else {
                                log("Code was changed (blank batch). Shutting down...", 2);
                                bRunning = false;                              
                            }
                        }

                        if (bRunning) {
                            p("Batch start: " + nBatchStart);
                            p("Batch end  : " + nBatchEnd);

                            if (nBatchStart <= nBatchEnd) {
                                n = ProcessBatches(nBatchStart, nBatchEnd, sContentUrl,  sAccessToken,  sAlteranteFolderID,  sMetaDataUrl,  sClientID,  sRefreshToken);                        

                                if (nUploadedFiles > 0) {
                                    //new files were uploaded , so save latest records to disk
                                    log("Updating cache files...", 2); 
                                    n = SaveMaptoFile();
                                    log("Save Map res: " + n, 2); 
                                    if (n == 0) {
                                        //save ok.  store time.
                                        long lTimeCache = System.currentTimeMillis();
                                        int nRes = NetUtils.UpdateConfig("amazon_cache_ts", String.valueOf(lTimeCache), "../rtserver/config/www-cloud-cache.properties");                                                                       
                                    }                                    
                                } else {
                                    log("Skip update to cache files. No new files.", 2);                                 
                                }
                            } else {
                                p("No more batch work to do...");
                            }
                            try {
                               sDriveEnabled = NetUtils.getConfig("drive_amazon", "../rtserver/config/www-cloud.properties");
                               if (!sDriveEnabled.equals("true")) bRunning = false;
                               p("All done. ZZZzzz...");
                               Thread.sleep(15000);
                            } catch (Exception e) {
                               e.printStackTrace();
                            }                                                    
                        }
                    }
                                        
                } else {
                    if (sEndPoint.contains("ERROR")) {
                        log("MSG: There was an unexpected error...: " + sEndPoint, 2);
                        bRunning = false;
                    }
                }                
            }                                    
        }        
    }
    
}

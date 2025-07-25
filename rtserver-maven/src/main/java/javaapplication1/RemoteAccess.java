package javaapplication1;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.http.ParseException;


public class RemoteAccess {
    private String uuid = null;
    private String clusterid = null;
    public static String bridgeAddress = "https://abc.alterante.com/cass/";
    
    static int mLogLevel = 2;
    static PrintStream log= null;
    
    public String UNAUTHORIZED = "ERROR_UNAUTHORIZED";
    public String DISCONNECTED = "ERROR_DISCONNECTED";
    public String UNKNOWN = "ERROR_UNKNOWN";
    
    private String keyPassword;
    
    private Integer assize=0; 
    
    private String iv="";
    
    private String aux="";
    
    
    public RemoteAccess(String clusterid) {
        this.clusterid = clusterid;
    }
    
    public RemoteAccess(String clusterid, String aux) {
        this.clusterid = clusterid;
        this.aux=aux;
    }
    
    public String remoteSession(){
        return (String) doGet("getsession.fn", "", true);
    }
    
    public String remoteGetTags(){
        return (String) doGet("gettags_m.fn", "", true);
    }

    public String remoteGetTags_webapp(){
        return (String) doGet("gettags_webapp.fn", "", true);
    }

    public String remoteSidebar(){
        return (String) doGet("sidebar.fn", "", true);
    }
    
    public boolean remoteLogin(String user, String password, String publicKey, String passwordkey, String iv){
        RSACrypto rc = new RSACrypto();
        
        String dataToEnc = "user=" + user + "&pass=" + password + "&key=" + passwordkey + "&iv=" + iv;
        String cipherText = rc.encrypt(dataToEnc, publicKey);
        try {
            cipherText = URLEncoder.encode(cipherText,"UTF-8");
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(RemoteAccess.class.getName()).log(Level.SEVERE, null, ex);
        }
        String response = (String) doGet("login.fn", "boxuser=&boxpass=&encdata=" + cipherText + "&", true);
        
        if(response.startsWith("ERROR"))
            return false;
        else
            return true;
    }
    
    public String remoteQuery(String filetype, String days, String foo, String view, String numobj, String lastdate){
        if(lastdate != null && !lastdate.isEmpty())
            return (String) doGet("query.fn", "ftype=" + filetype +"&days=" + days + "&foo=" + foo + "&view=" + view + "&numobj=" + numobj + "&date=" + lastdate, true);
        else
            return (String) doGet("query.fn", "ftype=" + filetype +"&days=" + days + "&foo=" + foo + "&view=" + view + "&numobj=" + numobj, true);
    }
    
    public String remoteSuggest(String filetype, String days, String foo, String numobj){
        return (String) doGet("suggest.fn", "ftype=" + filetype +"&days=" + days + "&foo=" + foo + "&" + numobj, true);
    }
    
    private Object doGet(String _function, String _param, boolean string) {
        try {

            p("------------------------doGet------------");
            
            int connectionTimeout = 30000;
            p("---- set connectionTimeout: " + connectionTimeout);

            int socketTimeout = 180000;
            p("---- set socketTimeout: " + socketTimeout);

            // Configure timeouts
            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionTimeout))
                .setResponseTimeout(Timeout.ofMilliseconds(socketTimeout))
                .build();

            String fileUrl = bridgeAddress + _function + "?cluster-id=" + clusterid ;
            if(!_param.isEmpty())
                fileUrl = fileUrl + "&"+ _param;
            
            HttpGet httpGet = new HttpGet(fileUrl);
            httpGet.setConfig(requestConfig);
            
            if(uuid != null){
                httpGet.setHeader("Cookie", "uuid=" + uuid);
            }
            
            try (CloseableHttpClient httpclient = HttpClients.createDefault();
                 CloseableHttpResponse response = httpclient.execute(httpGet)) {
                
                int statusCode = response.getCode();
                
                Object resp = null;
                HttpEntity entity = response.getEntity();
                
                if (entity != null) {
                    try {
                        if(string) {
                            resp = EntityUtils.toString(entity);
                        } else {
                            resp = EntityUtils.toByteArray(entity);
                        }
                    } catch (ParseException e) {
                        p("ParseException while reading response: " + e.getMessage());
                        return null;
                    }
                }
                
                // Handle Set-Cookie headers
                Header[] setCookieHeaders = response.getHeaders("Set-Cookie");
                if (setCookieHeaders != null && setCookieHeaders.length > 0) {
                    for (Header header : setCookieHeaders) {
                        if(header.getValue().startsWith("uuid")){
                            String cookie = header.getValue();
                            setUuid(cookie.substring(5, 41));
                        }
                    }
                }
                
                if (statusCode == 200) {
                    return resp;
                } else {
                    if(string){
                        String error = UNKNOWN;
                        if (statusCode == 401) {
                            error = UNAUTHORIZED;
                        }
                        if (statusCode > 500) {
                            error = DISCONNECTED;
                        }
                        return error;
                    } else {
                        return null;
                    }
                }
            }
            
        } catch (IOException e) {
            return null;
        }
        
    }
    
    
    static protected void p(String s) {
        
        long threadID = Thread.currentThread().getId();
        System.out.println("[client_" + threadID + "] " + s);
    }
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
    
    public String getAux(){
        return aux;
    }
    
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    public byte[] remoteFile(String sNamer){
        return (byte[]) doGet("getfile.fn", "sNamer=" + sNamer, false);
    }
    
    public String remoteApplyTag(String param){
        return (String) doGet("applytags.fn", param, true);
    }

    public String remoteChatPull(String param){
        return (String) doGet("chat_pull.fn", param, true);
    }

    public String remoteChatPush(String param){
        return (String) doGet("chat_push.fn", param, true);
    }
    
    public String remoteChatClear(String param){
        return (String) doGet("chat_clear.fn", param, true);
    }
    
    public String remoteSavePropertyMulticluster(String param){
        return (String) doGet("savepropertymulticluster.fn", param, true);
    }
    
    public String remoteGetPropertyMulticluster() {
        return (String) doGet("getpropertymulticluster.fn", "", true);
    }

    /**
     * @return the keyPassword
     */
    public String getKeyPassword() {
        return keyPassword;
    }

    /**
     * @param keyPassword the keyPassword to set
     */
    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    /**
     * @return the assize
     */
    public Integer getAssize() {
        return assize;
    }

    /**
     * @param assize the assize to set
     */
    public void setAssize(Integer assize) {
        this.assize = assize;
    }

    /**
     * @return the iv
     */
    public String getIv() {
        return iv;
    }

    /**
     * @param iv the iv to set
     */
    public void setIv(String iv) {
        this.iv = iv;
    }

    
    
    
    
    
    
}

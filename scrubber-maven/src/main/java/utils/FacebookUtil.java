/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import com.restfb.BinaryAttachment;
import com.restfb.DefaultFacebookClient;
import com.restfb.Parameter;
import com.restfb.exception.FacebookOAuthException;
import com.restfb.types.FacebookType;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

/**
 *
 * @author fcarriqiry
 */
public class FacebookUtil {
    
    private static final String API_KEY= "447887422078794"; //"761672403966319";
    private static final String API_SEECRET= "08f6d78a601927e7b61d37e5e5eb2993"; //a91fea9b35a88ddf708861f3df1ed5e7";
    private String USER_TOKEN="";
    private String CONFIG_FILE="";
    
    public FacebookUtil(String username) throws IOException{
        CONFIG_FILE="../rtserver/config/www-facebook_"+username.toLowerCase()+".properties";
        File file=new File(CONFIG_FILE);
        if(!file.exists()){
            file.createNewFile();
        }        
        USER_TOKEN=ConfigUtil.GetConfig("token", CONFIG_FILE);
    }
    
    public void setUserToken(String token){
        try{
            USER_TOKEN=getLongLiveToken(token);
            ConfigUtil.UpdateConfig("token", USER_TOKEN,CONFIG_FILE);
            ConfigUtil.UpdateConfig("type", "token_long",CONFIG_FILE);
    }catch(Exception ex){
            ex.printStackTrace();
            USER_TOKEN=token;
            ConfigUtil.UpdateConfig("token", USER_TOKEN,CONFIG_FILE);
            ConfigUtil.UpdateConfig("type", "token_short",CONFIG_FILE);
        }
    }
    
    public boolean publishPhoto(String message, File filePhoto) throws FileNotFoundException {
        if(USER_TOKEN==null || USER_TOKEN.trim().length()==0){
            return false;
        }
        try{
            //USER_TOKEN =new DefaultFacebookClient().obtainExtendedAccessToken(API_KEY,  API_SEECRET, USER_TOKEN).getAccessToken();
            //UpdateConfig("token", USER_TOKEN,CONFIG_FILE);
          
            DefaultFacebookClient facebookClient = new DefaultFacebookClient(USER_TOKEN,API_SEECRET);
            FacebookType publishPhotoResponse = facebookClient.publish("me/photos", FacebookType.class,BinaryAttachment.with(filePhoto.getName(), new FileInputStream(filePhoto)), Parameter.with("message", message+" - shared via Alterante"));
        }catch(FacebookOAuthException oe){
            oe.printStackTrace();
            return false;
        }	   
    
        return true;
    } 
    
    private String getLongLiveToken(String tokenShort) throws Exception {
      StringBuilder result = new StringBuilder();
      URL url = new URL("https://graph.facebook.com//oauth/access_token?grant_type=fb_exchange_token&client_id="+API_KEY+"&client_secret="+API_SEECRET+"&fb_exchange_token="+tokenShort);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");      
      BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      String line;
      while ((line = rd.readLine()) != null) {
         result.append(line);
      }
      rd.close();
      String res=result.toString();
      if(res.contains("error")){
          return "";
      }
      return res.substring("access_token=".length(),res.indexOf("&expires"));
   }
    
   
    
}

package utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.minidev.json.*;
import org.mapdb.DB;
import org.mapdb.TxMaker;


    
public class ShareController {
    
   private static ShareController instance = null;

   private TxMaker txMaker;
    
    private DB makeShareTxMapDB() {
        return txMaker.makeTx();
       
    }

    private ShareController() {
        txMaker = (new LocalFuncs()).loadShareMapDB();
    }
       
   public static ShareController getInstance() {
      if(instance == null) {
         instance = new ShareController();
      }
      return instance;
   }
    
    public ShareToken createShare(String clusterId, Collection<User> users, ShareTypes type, String bridgeHost, String bridgePort, String bridgeSecure, String clusterToken, String key, String allowremote){
            
        DB db = makeShareTxMapDB();
        Map<String,String> permissions = db.getTreeMap("permissions");
        Map<String,String> sharetokens = db.getTreeMap("sharetokens");
    
        
        System.out.println("*******createShare() *****");
        
        String share_token;        
        if(allowremote.equals("true")){
            share_token = getNewShareToken(clusterId, type, bridgeHost, bridgePort, bridgeSecure, clusterToken, key);
        }else{
            share_token = "offline";
        }

        if(share_token != null){
            ShareToken newshare = new ShareToken(key, share_token, type.toString());
            sharetokens.put(key, newshare.getJSON().toJSONString());

            JSONObject objJSON = new JSONObject();
            if(users != null && !users.isEmpty()){
                JSONArray usersJSON = new JSONArray();
                for (User user : users) {
                    Permission perm = new Permission(key, user.getUsername(), false);
                    usersJSON.add(perm.getJSON());
                }
                objJSON.put("users", usersJSON);
                objJSON.put("all", "false");
            }else{
                objJSON.put("all", "true");
            }

            permissions.put(key, objJSON.toJSONString());

            db.commit();
            return newshare;
        }else{
            db.close();
            return null;
        }
    }
           
    
    
    
    public ShareToken getStoredShareToken(String key) {
        DB db = makeShareTxMapDB();
        Map<String,String> sharetokens = db.getTreeMap("sharetokens");
        
        String sharetoken = sharetokens.get(key);
        if(sharetoken != null){
            JSONObject jtoken = (JSONObject)JSONValue.parse(sharetoken);
            db.close();
            return new ShareToken(jtoken.get("key").toString(),jtoken.get("sharetoken").toString(),jtoken.get("type").toString());
        }
        db.close();
        return null;
    }
    
    public Collection<Permission> getPermissions(String key) {
        DB db = makeShareTxMapDB();
        Map<String,String> permissions = db.getTreeMap("permissions");
        
        Collection<Permission> result = new ArrayList<Permission>();
        String valuePerm = permissions.get(key);
        if(valuePerm != null){
            JSONObject jObj = (JSONObject)JSONValue.parse(valuePerm);
            JSONArray jarray = (JSONArray)jObj.get("users");
            for (Object obj : jarray) {
                JSONObject jtoken = (JSONObject)obj;
                result.add(new Permission(jtoken.get("key").toString(),jtoken.get("username").toString(),Boolean.parseBoolean(jtoken.get("notified").toString())));
            }
        }
        db.close();
        return result;
    }
    
    public Collection<User> getPermissionUsers(String key) {        
        if(noUsers(key)){
            return null;
        }else if(allUsers(key)){
            return UserCollection.getInstance().getUsersByRole("user");
        }else{
            DB db = makeShareTxMapDB();
            Map<String,String> permissions = db.getTreeMap("permissions");
        
            Collection<User> result = new ArrayList<User>();
            String valuePerm = permissions.get(key);
            if(valuePerm != null){
                JSONObject jObj = (JSONObject)JSONValue.parse(valuePerm);
                JSONArray jarray = (JSONArray)jObj.get("users");
                for (Object obj : jarray) {
                    JSONObject jtoken = (JSONObject)obj;
                    result.add(UserCollection.getInstance().getUsersByName(jtoken.get("username").toString()));
                }
            }
            db.close();
            return result;
        }
    }
    
    public Collection<User> getPermissionUsersNotNotified(String key) {
        DB db = makeShareTxMapDB();
        Map<String,String> permissions = db.getTreeMap("permissions");
        
        String valuePerm = permissions.get(key);
        if(valuePerm != null){
            JSONObject jObj = (JSONObject)JSONValue.parse(valuePerm);
            JSONArray jarray = (JSONArray)jObj.get("users");
            if(Boolean.parseBoolean(jObj.get("all").toString())){
                Collection<User> result = new ArrayList<User>();
                Collection<User> users = UserCollection.getInstance().getUsersByRole("user");
                if(jarray != null && !jarray.isEmpty()){
                    
                    
                    for (User user : users) {
                        boolean founded = false;
                            for (Object obj : jarray) {
                                JSONObject jtoken = (JSONObject)obj;
                                if(user.getUsername().equals(jtoken.get("username").toString())){
                                    if(!Boolean.parseBoolean(jtoken.get("notified").toString())){
                                        result.add(user);
                                    }
                                    founded = true;
                                    break;
                                }
                            }
                            if(!founded){
                                result.add(user);
                            }
                    }
                }else{
                    db.close();
                    return users;
                }
                db.close();
                return result;
            }else{
                
                Collection<User> result = new ArrayList<User>();
                for (Object obj : jarray) {
                    JSONObject jtoken = (JSONObject)obj;
                    if(!Boolean.parseBoolean(jtoken.get("notified").toString())){
                        result.add(UserCollection.getInstance().getUsersByName(jtoken.get("username").toString()));
                    }
                }
                db.close();
                return result;
            }
        }
        db.close();
       return null;
    }
    
    
    public Permission getPermissionByUser(String key, String username) {
        if(noUsers(key)){
            return null;
        }else if(allUsers(key)){
            return new Permission(key, username, true);
        }else {
            Collection<Permission> perms = getPermissions(key);
            for (Permission perm : perms) {
                if(perm.getUsername().equals(username)){
                    return perm;
                }
            }
        }
       return null;
    }
    
    public Permission getClusterPermission(String username) {
        String clusterid =  getClusterID();
        return getPermissionByUser(clusterid, username);
    }
    
    
    
    public String getPermissionUsersString(String key) {
        
        if(noUsers(key)){
            return "no users";
        }else if(allUsers(key)){
            return "all users";
        }else{
            DB db = makeShareTxMapDB();
            Map<String,String> permissions = db.getTreeMap("permissions");
            
            String result = "";
            String valuePerm = permissions.get(key);
            if(valuePerm != null){
                JSONObject jObj = (JSONObject)JSONValue.parse(valuePerm);
                JSONArray jarray = (JSONArray)jObj.get("users");
                for (Object obj : jarray) {
                    JSONObject jtoken = (JSONObject)obj;
                    result += jtoken.get("username").toString() + "&nbsp&nbsp&nbsp";
                }
            }
            db.close();
            return result;
        }
    }
    
    
     public String getNewShareToken(String clusterId, ShareTypes type, String bridgeHost, String bridgePort, String bridgeSecure, String clusterToken, String key) {
        InputStream fis = null;
        try {
            System.out.print("*******************");

            if (clusterToken == null) {
                clusterToken = "00000000-0000-0000-0000-000000000000";
            }
            if (clusterToken.length() == 0) {
                clusterToken = "00000000-0000-0000-0000-000000000000";
            }

            System.out.println("   ClusterID: '" + clusterId + "'");
            System.out.println("   Type: '" + type.toString() + "'");
            System.out.println("   BridgeHost: '" + bridgeHost + "'");
            System.out.println("   ClusterToken: '" + clusterToken + "'");
            System.out.println("   Key: '" + key + "'");

            //transfer via HTTP
//            String sHostFile = "https://" + bridgeHost +"/clusters/"+ clusterId + "/share?access-token=" + clusterToken;
//            System.out.println("sHostFile: " + sHostFile);            
                       
            Writer writer = new StringWriter();
            JSONObject data = new JSONObject();
            data.put("type", type.toString());
            if(type.equals(ShareTypes.TAG)){
                data.put("tag-name", key);
            }else if(type.equals(ShareTypes.FILE)){
                data.put("file-id", key);
            }
            fis = new ByteArrayInputStream(data.toJSONString().getBytes("UTF-8"));
//            URL urlBridge = new URL(sHostFile);
            //boolean bres = htrp.postDataHttps(fis,urlBridge,writer);
            HTTPRequestPoster htrp = new HTTPRequestPoster();
            boolean secure = (bridgeSecure.equals("true"));
            boolean bres = htrp.postDataHttps_new(fis, bridgeHost, bridgePort, secure, writer, clusterId, clusterToken);
            if(bres){
                String outputString = writer.toString();
                System.out.println("outputstring=" + outputString);
                String shareToken = "";
                try {
                    JSONObject jtoken = (JSONObject)JSONValue.parse(outputString);
                    if (jtoken != null) {
                        shareToken = jtoken.get("share-token").toString();
                    }
                } catch (Exception ex) {
                    System.out.println("JSON invalid for outputstring)");
                    shareToken = "00000000-0000-0000-0000-000000000000";
                    ex.printStackTrace();
                }
                System.out.println("return shareToken = '" + shareToken + "'");
                return shareToken;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }

    

    public void updateUsers(String key, Collection<User> users) {
        
        JSONArray usersJSON = new JSONArray();
        for (User user : users) {
            Permission oldPerm = getPermissionByUser(key, user.getUsername());
            boolean notified = false;
            if(oldPerm != null)
                notified = oldPerm.isNotified();
            Permission perm = new Permission(key, user.getUsername(), notified);
            usersJSON.add(perm.getJSON());
        }
        JSONObject objJSON = new JSONObject();
        objJSON.put("users", usersJSON);
        objJSON.put("all", "false");
        
        DB db = makeShareTxMapDB();
        Map<String,String> permissions = db.getTreeMap("permissions");
        permissions.put(key, objJSON.toJSONString());
        
        db.commit();
    }
    
    
    public void markAllAsNotified(String key) {
        
        Collection<User> users = getPermissionUsers(key);
        
        DB db = makeShareTxMapDB();
        Map<String,String> permissions = db.getTreeMap("permissions");
        
        String valuePerm = permissions.get(key);
        if(valuePerm != null){
            JSONObject objJSON = (JSONObject)JSONValue.parse(valuePerm);
            JSONArray usersJSON = new JSONArray();
            for (User user : users) {
                Permission perm = new Permission(key, user.getUsername(), true);
                usersJSON.add(perm.getJSON());
            }
            objJSON.put("users", usersJSON);
            
            permissions.put(key, objJSON.toJSONString());
            
            db.commit();
        }
                
        db.close();
    }
    
    public void setToAll(String key) {
        DB db = makeShareTxMapDB();
        Map<String,String> permissions = db.getTreeMap("permissions");
        
        String valuePerm = permissions.get(key);
        if(valuePerm != null){
            JSONObject objJSON;
            if(valuePerm.equals(Permission.NOONE_NAME)){
                objJSON = new JSONObject();
            }else{
                objJSON = (JSONObject)JSONValue.parse(valuePerm);
            }
            objJSON.put("all", "true");
            permissions.put(key, objJSON.toJSONString());
            
            db.commit();
        }
    }
    
    public boolean allUsers(String key) {
        DB db = makeShareTxMapDB();
        Map<String,String> permissions = db.getTreeMap("permissions");
        
        String valuePerm = permissions.get(key);
        if(valuePerm != null && !valuePerm.equals(Permission.NOONE_NAME)){
            JSONObject objJSON = (JSONObject)JSONValue.parse(valuePerm);
            db.close();
            return Boolean.parseBoolean(objJSON.get("all").toString());
        }
        db.close();
        return false;
    }
    
    public void removeUsers(String key) {
        DB db = makeShareTxMapDB();
        Map<String,String> permissions = db.getTreeMap("permissions");
        
        permissions.put(key, Permission.NOONE_NAME);
        
        db.commit();
    }
    
     public boolean noUsers(String key) {
        DB db = makeShareTxMapDB();
        Map<String,String> permissions = db.getTreeMap("permissions");
         
        
        String perm = permissions.get(key);
        if(perm != null){
            db.close();
            return perm.equals(Permission.NOONE_NAME);
        }
        
        db.close();
        return true;
    }
    

    
    public Collection<ShareToken> getShareTokens() {
        DB db = makeShareTxMapDB();
        Map<String,String> sharetokens = db.getTreeMap("sharetokens");
        
        Set<String> setkey = sharetokens.keySet();
        Collection<ShareToken> result = new ArrayList<ShareToken>();
        for (String key : setkey) {
            String sharetoken = sharetokens.get(key);
            if(sharetoken != null){
                JSONObject jtoken = (JSONObject)JSONValue.parse(sharetoken);
                ShareToken st = new ShareToken(jtoken.get("key").toString(),jtoken.get("sharetoken").toString(),jtoken.get("type").toString());
                result.add(st);
            }
        }
        
        db.close();
        return result;
       
    }
    
    private String getClusterID() {
        String clusteridUUIDPath = "../scrubber/data/clusterid";
        String clusteridUUID = NetUtils.getUUID(clusteridUUIDPath);
        return clusteridUUID;
    }
    
    
    
    public void removeSharesOfUser(String username) {
        DB db = makeShareTxMapDB();
        Map<String,String> sharetokens = db.getTreeMap("sharetokens");
        Map<String,String> permissions = db.getTreeMap("permissions");


        Collection<ShareToken> shareTokens = getShareTokens();
        Collection<User> users = UserCollection.getInstance().getUsersByRole("user");
        for (ShareToken shareToken : shareTokens) {
            JSONArray usersJSON = new JSONArray();
            for (User user : users) {
                if(!user.getUsername().equals(username)){
                    Permission oldPerm = getPermissionByUser(shareToken.getKey(), user.getUsername());
                    if(oldPerm != null){
                        usersJSON.add(oldPerm.getJSON());   
                    }             
                }
            }
            boolean alluser = allUsers(shareToken.getKey());
            if(!alluser && usersJSON.size() == 0){
                permissions.put(shareToken.getKey(), Permission.NOONE_NAME);
            }else{
                JSONObject objJSON = new JSONObject();
                objJSON.put("users", usersJSON);
                objJSON.put("all", Boolean.toString(alluser));
                
                permissions.put(shareToken.getKey(), objJSON.toJSONString()); 
            }
        }
        db.commit();
    }
   
    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javaapplication1;

import java.io.File;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import utils.ConfigUtil;
import utils.NetUtils;

/**
 *
 * @author fcarriqiry
 */
public class MultiClusterManager {
    
    private static MultiClusterManager instance;
    
    private HashMap<String, HashMap<String,MultiClusterUser>> clusters;
    
    private HashMap<String,String> usersPassword;
    
    private String CONFIG_FILE="";
    
    //Si el usuario entró indicando cluster aquí se guara su RA para poder acceder a guardar 
    //las properties en ese cluster
    private HashMap<String,RemoteAccess> ra;
    
    private MultiClusterManager(String clusterOrigin){
        
        try{
            CONFIG_FILE="../rtserver/config/"+clusterOrigin+"-multicluster.properties";
            clusters=new HashMap<String, HashMap<String, MultiClusterUser>>();
            usersPassword=new HashMap<String, String>();
            this.ra=new HashMap<String, RemoteAccess>();
            File file=new File(CONFIG_FILE);
            if(!file.exists()){
                file.createNewFile();
            }    
            
        }catch(Throwable th){
            th.printStackTrace();
        }
    }
    
    public static MultiClusterManager getInstance(){
        if(instance==null){
            String clusteridUUIDPath = "../scrubber/data/clusterid";
            String clusteridUUID = NetUtils.getUUID(clusteridUUIDPath);
            
            instance=new MultiClusterManager(clusteridUUID);
        }
        
        return instance;
    }
    
    public void setRa(String user,RemoteAccess ra){
        if(clusters.containsKey(user)){
            clusters.remove(user);
        }
        this.ra.put(user, ra);
    }
    
    public void saveUserAndPassword(String user, String password){
        usersPassword.put(user, password);
    }
    
    public void addCluster(String userOwner,String cluster, String user, String password, String name){
        MultiClusterUser userCluster=new MultiClusterUser();
        userCluster.setPassword(password);
        userCluster.setCluster(cluster);
        userCluster.setUser(user);
        userCluster.setName(name);
        initUserOwner(userOwner);
        clusters.get(userOwner).put(cluster, userCluster);
        save(userOwner);
    }
    
     public String testCluster(String userOwner,String cluster, String user, String password, String name){
        MultiClusterUser userCluster=new MultiClusterUser();
        userCluster.setPassword(password);
        userCluster.setCluster(cluster);
        userCluster.setUser(user);
        userCluster.setName(name);
        RemoteAccess ra=loginRemote(userCluster);
        return ra.getAux();
    }
    
    public void removeCluster(String userOwner,String cluster){
        if(clusters.containsKey(userOwner)){
            if(clusters.get(userOwner).containsKey(cluster)){
                clusters.get(userOwner).remove(cluster); 
                save(userOwner);
            }
        }
    }
    
    public String getClusters(String userOwner){
        initUserOwner(userOwner);
        StringBuilder result=new StringBuilder("{\"clusters\":[");
        boolean coma=false;
        for(MultiClusterUser userCluster:clusters.get(userOwner).values()){
            if(coma) result.append(",");
            
            if(userCluster.getRa()==null || userCluster.getRa().getUuid()==null || userCluster.getRa().getUuid().trim().length()==0){ //Si no esta logeado se logea
                userCluster.setRa(loginRemote(userCluster));
            }
             
            result.append(" {\"user\":\""+userCluster.getUser()+"\",\"name\":\""+userCluster.getName()+"\",\"cluster\":\""+userCluster.getCluster()+"\",\"password\":\""+userCluster.getPassword()+"\",\"uuid\":\""+userCluster.getRa().getUuid()+"\"} ");
            coma=true;
        }
        result.append("]}");
        
        return result.toString();
    }
    
    public String queryRemote(String userOwner,String cluster, String queryString, String date,String sFileType, String sDaysBack){
        initUserOwner(userOwner);
        if(clusters.containsKey(userOwner)){
            if(clusters.get(userOwner).containsKey(cluster)){
                MultiClusterUser userCluster=clusters.get(userOwner).get(cluster);
                RemoteAccess ra=userCluster.getRa();
                if(ra==null || ra.getUuid()==null){ //Si no esta logeado se logea
                    ra=loginRemote(userCluster);
                    userCluster.setRa(ra);
                    if(ra.getAux().equals("LOGININVALID")){
                        return "{\"fighters\":[],\"disabeled\":false,\"userinvalid\":true}";
                    }
                    if(ra.getAux().equals("UNRECHABLE")){
                        return "{\"fighters\":[],\"disabeled\":false}";
                    }
                }
                if(ra.getUuid()!=null){//Si pudo loguearse
                    
                    String res=ra.remoteQuery(sFileType,sDaysBack, queryString,"json" ,"30",date);
                    if(res==null || res.trim().length()==0 || res.equals("ERROR_UNKNOWN")){//Si esta deslogeado se retorna vacio 
                        ra=loginRemote(userCluster);//Se intenta loguar de nuevo
                        userCluster.setRa(ra);
                        if(ra.getAux().equals("LOGININVALID")){
                            return "{\"fighters\":[],\"disabeled\":false,\"userinvalid\":true}";
                        }
                        if(ra.getAux().equals("UNRECHABLE")){
                            return "{\"fighters\":[],\"disabeled\":false}";
                        }
                        if(ra.getUuid()!=null){//Si se pudo logear ejecuta el query
                            res=ra.remoteQuery(sFileType,sDaysBack, queryString,"json" ,"30",date);
                            if(res==null || res.trim().length()==0 || res.equals("ERROR_UNKNOWN")){//Si por algun motivo sigue deslogueado
                                return "{\"fighters\":[],\"disabeled\":false}";
                            }else{//Se retorna el query
                                if(res.equals("ERROR_DISCONNECTED")){
                                    return "{\"fighters\":[],\"disabeled\":false}";
                                }else{
                                    return res;
                                }
                            }
                        }else{
                            //No se logró loguear
                            return "{\"fighters\":[],\"disabeled\":false}";   
                        }
                    }else{
                        //Se retorna el query
                        if(res.equals("ERROR_DISCONNECTED")){
                            return "{\"fighters\":[],\"disabeled\":false}";
                        }else{
                            return res;
                        }
                            
                        
                    }
                }else{
                    return "{\"fighters\":[],\"disabeled\":false}";
                }
                
            }        
        }  
       return "{\"fighters\":[]}";
        
    }
    
     public RemoteAccess getRA(String userOwner,String cluster){
        initUserOwner(userOwner);
        if(clusters.containsKey(userOwner)){
            if(clusters.get(userOwner).containsKey(cluster)){
                MultiClusterUser userCluster=clusters.get(userOwner).get(cluster);
                RemoteAccess ra=userCluster.getRa();
                if(ra==null){ //Si no esta logeado se logea
                    ra=loginRemote(userCluster);
                    userCluster.setRa(ra);
                }
                if(ra.getUuid()==null){//Si pudo loguearse
                    ra=loginRemote(userCluster);//Se intenta loguar de nuevo
                    userCluster.setRa(ra);
                }
                return ra;
            }        
        }  
       return new RemoteAccess(cluster);
        
    }
    
    private RemoteAccess loginRemote(MultiClusterUser userCluster){
        try{
            RemoteAccess ra = new RemoteAccess(userCluster.getCluster());//"c2acf0de-2406-4528-9b48-52afd57eabef"
            String returned = ra.remoteSession();

            JSONObject jtoken = (JSONObject)JSONValue.parse(returned);
            String publicKey = jtoken.get("publickey").toString();
            Boolean aesEncryptSession=false;
            Integer aesSizeSession=0;
            if(jtoken.get("aesencrypt") != null && jtoken.get("aessize") != null){
                aesEncryptSession = Boolean.parseBoolean(jtoken.get("aesencrypt").toString());
                aesSizeSession = Integer.parseInt(jtoken.get("aessize").toString());
            }

            RSACrypto rc = new RSACrypto();

            String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz";
            String sKeyPassword="";
            int strlength2 = 40;
            for (int i2=0; i2<strlength2; i2++) {
                int randomNumber = (int) Math.floor(Math.random() * chars.length());
                sKeyPassword += chars.substring(randomNumber,randomNumber+1);
            }
            String sIV = rc.generateRandomIV();
            boolean loggedIn = ra.remoteLogin(userCluster.getUser(), userCluster.getPassword(), publicKey, sKeyPassword, sIV);
            if(loggedIn){
                ra.setAssize(aesSizeSession);
                ra.setKeyPassword(sKeyPassword);
                ra.setIv(sIV);
                return ra;
            }else{
                return new RemoteAccess(userCluster.getCluster(),"LOGININVALID");
            }
        }catch(Throwable th){
            th.printStackTrace();
        }
        
        return new RemoteAccess(userCluster.getCluster(),"UNRECHABLE");                        
    }
    
    public String getPropertyMulticluster(String userOwner){
        String stringSaved=ConfigUtil.GetConfig(userOwner, CONFIG_FILE);
        return stringSaved;
    }
    
    public void savePropertyMulticluster(String userOwner, String stringToSave){
        ConfigUtil.UpdateConfig(userOwner, stringToSave, CONFIG_FILE);
    }
    
    private void initUserOwner(String userOwner){
        if(!clusters.containsKey(userOwner)){
            clusters.put(userOwner, new HashMap<String, MultiClusterUser>());
            load(userOwner);
        }
    }

    private void save(String userOwner) {
        try{
            StringBuilder stringToSave=new StringBuilder("");
            boolean coma=false;
            for(MultiClusterUser uCluster:clusters.get(userOwner).values()){
                if(coma) stringToSave.append(",");
                stringToSave.append(uCluster.getCluster()+";"+uCluster.getName()+";"+uCluster.getPassword()+";"+uCluster.getUser());
                coma=true;
            }
            String key=userOwner+usersPassword.get(userOwner);

            CryptLib crypt=new CryptLib(128);
            String md5=CryptLib.md5(key);
            String sKeyPassword=md5.substring(0,15);

            String text=crypt.encrypt(stringToSave.toString() , sKeyPassword, sKeyPassword);
            
            if(ra.containsKey(userOwner) ){
                ra.get(userOwner).remoteSavePropertyMulticluster("multiclustername="+URLEncoder.encode(text, "UTF-8"));
            }else{
                ConfigUtil.UpdateConfig(userOwner, text, CONFIG_FILE);
            }
            
            
        }catch(Throwable th){
            th.printStackTrace();
        }
    }
    
    
    
    private void load(String userOwner) {
        try{
            String stringSaved="";
             if(ra.containsKey(userOwner) ){
                stringSaved=ra.get(userOwner).remoteGetPropertyMulticluster();
            }else{
                stringSaved=ConfigUtil.GetConfig(userOwner, CONFIG_FILE);
            }
            
            if(stringSaved!=null && stringSaved.trim().length()>0){
                String key=userOwner+usersPassword.get(userOwner);

                CryptLib crypt=new CryptLib(128);
                String md5=CryptLib.md5(key);
                String sKeyPassword=md5.substring(0,15);

                stringSaved=crypt.decrypt(stringSaved , sKeyPassword, sKeyPassword);

                String[] clustersString=stringSaved.split(",");
                for(String sCluster : clustersString){
                    String[] values=sCluster.split(";");
                    String cluster=values[0];
                    String name=values[1];
                    String password=values[2];
                    String user=values[3];
                    MultiClusterUser userCluster=new MultiClusterUser();
                    userCluster.setPassword(password);
                    userCluster.setCluster(cluster);
                    userCluster.setUser(user);
                    userCluster.setName(name);
                    clusters.get(userOwner).put(cluster, userCluster);
                }
            }
        }catch(Throwable th){
            th.printStackTrace();
        }
    }
}

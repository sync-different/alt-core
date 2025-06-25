/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package utils;

import net.minidev.json.JSONObject;


public class ShareToken {
    
    private String key;
    private String share_token;
    private String type;

    public ShareToken(String key, String share_token, String type) {
        this.key = key;
        this.share_token = share_token;
        this.type = type;
    }
    
    /**
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * @param key the key to set
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * @return the share_token
     */
    public String getShare_token() {
        return share_token;
    }

    /**
     * @param share_token the share_token to set
     */
    public void setShare_token(String share_token) {
        this.share_token = share_token;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }
    
    public JSONObject getJSON(){
        JSONObject sharetokenJSON = new JSONObject();
        sharetokenJSON.put("key", key);
        sharetokenJSON.put("sharetoken", share_token);
        sharetokenJSON.put("type", type);
        return sharetokenJSON;
    }
    
    
}

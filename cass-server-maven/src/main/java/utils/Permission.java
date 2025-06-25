/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package utils;

import net.minidev.json.JSONObject;


public class Permission{
    
    private String key;
    private String username;
    private boolean notified;
    public static final String ALL_NAME = ".all";
    public static final String NOONE_NAME = ".noone";

    public Permission(String key, String username, boolean notified) {
        this.key = key;
        this.notified = notified;
        this.username = username;
        
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
     * @return the notified
     */
    public boolean isNotified() {
        return notified;
    }

    /**
     * @param notified the notified to set
     */
    public void setNotified(boolean notified) {
        this.notified = notified;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public JSONObject getJSON(){
        JSONObject userJSON = new JSONObject();
        userJSON.put("key", key);
        userJSON.put("username", username);
        userJSON.put("notified", notified);
        return userJSON;
    }
    
    
    
    
    
}

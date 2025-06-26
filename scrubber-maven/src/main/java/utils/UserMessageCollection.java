/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author agf
 */
public class UserMessageCollection {
    
    private static ArrayList<UserMessage>  chats;
    private static HashMap<String,ArrayList<UserMessage>> comments;
    private static HashMap<String,Integer> likes;
    
    private static UserMessageCollection instance = null;

    private UserMessageCollection() {
        loadChatCollection();
    }
    
    public static UserMessageCollection getInstance() {
      if(instance == null) {
         instance = new UserMessageCollection();
      }
      return instance;
   }
    
    private void loadChatCollection(){
        chats = new ArrayList<UserMessage>();
        comments=new HashMap<String, ArrayList<UserMessage>>();
        likes=new HashMap<String, Integer>();
    }
    
    public int addMessage(Long ts, String type, String username, String message){
        try {
            UserMessage msg = new UserMessage(new Date().getTime(), type, username, message);
            chats.add(msg);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    
    
    
     public int addComment(String md5,Long ts, String type, String username, String message){
        try {
            UserMessage msg = new UserMessage(new Date().getTime(), type, username, message);
            if(!comments.containsKey(md5)){
                comments.put(md5,new ArrayList<UserMessage>());
            }
            comments.get(md5).add(msg);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
     
    public int addLike(String md5){
        try {
            if(!likes.containsKey(md5)){
                likes.put(md5,0);
            }
            likes.put(md5,likes.get(md5)+1);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    
    public ArrayList<UserMessage> getMessagesbyDate(Long ts) {
        ArrayList<UserMessage> result = new ArrayList<UserMessage>();
        for (UserMessage msg : chats) {
            System.out.println("#chats: " + chats.size());
            if(msg.getTimestamp() > ts) {               
                result.add(msg);
            }
        }
        return result;
    }
    
    public Integer getLikes(String md5) {
        if(!likes.containsKey(md5)){
              likes.put(md5,0);
        }
        return likes.get(md5);
    }
    
    public ArrayList<UserMessage> getCommentsbyDate(String md5,Long ts) {
        ArrayList<UserMessage> result = new ArrayList<UserMessage>();
        if(!comments.containsKey(md5)){
            return result;
        }
        for (UserMessage msg : comments.get(md5)) {
            System.out.println("#comments: " + comments.size());
            if(msg.getTimestamp() > ts) {               
                result.add(msg);
            }
        }
        return result;
    }

    public void clearChats() {
       ArrayList<UserMessage>  aux=new ArrayList<UserMessage>();
       for(UserMessage m:chats){
           if(!m.getMessageType().equalsIgnoreCase("CHAT")){
               aux.add(m);
           }
       }
       chats=aux;
    }

    
    
    
}

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

import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;

/**
 *
 * @author agf
 */
public class UserMessageCollection {

    // BEGIN ANSI
    static boolean bConsole = true;

    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_RESET = "\u001B[0m";

    protected static void pw(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.UserChatCollection-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.UserChatCollection-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected static void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.UserChatCollection_" + threadID + "] " + s);
    }

    // END ANSI


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
            p("#chats: " + chats.size());
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
            p("#comments: " + comments.size());
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

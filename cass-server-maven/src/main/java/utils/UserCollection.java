package utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

public class UserCollection {
    
    private static ArrayList<User>  users;
    private static UserCollection instance = null;
    static String appendage = "";

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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.UserCollection-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.UserCollection-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    protected static void p(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.UserCollection_" + threadID + "] " + s);
    }

    // END ANSI


    private UserCollection() {
        Appendage app = new Appendage();
        appendage = app.getAppendage();
        loadUserCollection();
    }
    
    public static UserCollection getInstance() {
      if(instance == null) {
         instance = new UserCollection();
      }
      return instance;
   }
    
    private void loadUserCollection(){
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(appendage + "config/users.txt"));
            String line="";
            ArrayList<User> result = new ArrayList<User>();
            while((line=reader.readLine()) != null){
                String[] lineArray = line.split("\\,");
                String email = "";
                if(lineArray.length > 3){
                    email = lineArray[3];
                }
                result.add(new User(lineArray[0], lineArray[1], lineArray[2], email));
            }
            users = result;
        } catch (FileNotFoundException ex) {
            Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }
    
    private void saveUserCollection(){
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter("config/users.txt"));
            for (User user : users) {
                writer.write(user.getUsername() + "," + user.getPassword() + "," + user.getRole() + "," + user.getEmail());
                writer.newLine();
            }
            writer.close();
        } catch (IOException ex) {
            Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                writer.close();
            } catch (IOException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
    }    
    
    public boolean isUserPasswordValid(String sUserName, String sPassword) {
        User user = getUsersByName(sUserName);
        if (user != null){
            try {
                return PasswordHash.validatePassword(sPassword, user.getPassword());
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidKeySpecException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return false;
    }
    
    public User getUsersByName(String name) {
        for (User user : users) {
            if(user.getUsername().equalsIgnoreCase(name)){
                return user;
            }
        }
        return null;
    }
    
    public ArrayList<User> getUsersByRole(String role) {
        ArrayList<User> result = new ArrayList<User>();
        for (User user : users) {
            if(user.getRole().equals(role)){
                result.add(user);
            }
        }
        return result;
    }
    
    
    
    
    public User getUserAdmin() {
        for (User user : users) {
            if(user.getRole().equals("admin")){
                return user;
            }
        }
        return null;
    }
    
    
    public void changeUserAdmin(String username, String password) {
        User admin = getUserAdmin();
        if(!username.isEmpty()){
            admin.setUsername(username);    
        }
        if(!password.isEmpty()){
            try {
                String passHash = PasswordHash.createHash(password);    
                admin.setPassword(passHash);
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidKeySpecException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }        
        saveUserCollection();
    }
    
    public void replaceUserCollection(ArrayList<User> newUsers){
        users = newUsers;
        saveUserCollection();
    }
    
    public String changeUserPassword(String name, String oldPassword, String newPassword){
        if (isUserPasswordValid(name, oldPassword)) {
            User user = getUsersByName(name);
            user.setPassword(newPassword);
            saveUserCollection();
            return "password change sucessfully";
        }else{
            return "incorrect passoword";
        }
    }
    
    public int addUser(String username, String password, String email){
        try {
            if(getUsersByName(username)==null){
                User user = new User(username, PasswordHash.createHash(password), "user", email);
                users.add(user);
                saveUserCollection();
                return 1;
            }else{
                return 0;
            }
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
        }
        return -1;
    }
    
}

package utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

public class UserCollection {

    // users is a working copy reloaded from users.txt on every public-method
    // entry. It is NOT a long-lived cache — reads always reflect on-disk state,
    // and mutations follow load → mutate → atomic-save under LOCK so manual
    // edits to users.txt are never silently clobbered by a stale write.
    private static ArrayList<User>  users;
    private static UserCollection instance = null;
    private static final Object LOCK = new Object();
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
        synchronized (LOCK) {
            if(instance == null) {
                instance = new UserCollection();
            }
            return instance;
        }
    }

    // Reload users.txt into the working copy. MUST be called with LOCK held.
    // Lines that don't have at least 3 comma-separated fields are skipped
    // (defensive: a stray blank line or hand-edited typo shouldn't crash startup).
    private void loadUserCollection(){
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(appendage + "config/users.txt"));
            String line="";
            ArrayList<User> result = new ArrayList<User>();
            while((line=reader.readLine()) != null){
                if (line.isEmpty()) {
                    continue;
                }
                String[] lineArray = line.split("\\,");
                if (lineArray.length < 3) {
                    pw("Skipping malformed users.txt line: " + line);
                    continue;
                }
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
                if (reader != null) reader.close();
            } catch (IOException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    // Atomic write: serialize to users.txt.tmp, then rename over users.txt.
    // Readers either see the old file or the new file — never a half-written
    // one. MUST be called with LOCK held.
    private void saveUserCollection(){
        Path target = Paths.get(appendage + "config/users.txt");
        Path tmp = Paths.get(appendage + "config/users.txt.tmp");
        BufferedWriter writer = null;
        try {
            pw("Saving user collection to " + target);
            writer = new BufferedWriter(new FileWriter(tmp.toFile()));
            for (User user : users) {
                writer.write(user.getUsername() + "," + user.getPassword() + "," + user.getRole() + "," + user.getEmail());
                writer.newLine();
            }
            writer.close();
            writer = null;
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicEx) {
                // Some filesystems (e.g. across mount points) don't support ATOMIC_MOVE.
                // Fall back to a plain replace — still safer than writing target directly.
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ex) {
                    Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    
    public boolean isUserPasswordValid(String sUserName, String sPassword) {
        synchronized (LOCK) {
            loadUserCollection();
            User user = getUsersByNameLocked(sUserName);
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
    }

    public User getUsersByName(String name) {
        synchronized (LOCK) {
            loadUserCollection();
            return getUsersByNameLocked(name);
        }
    }

    // Internal lookup — assumes caller already holds LOCK and has loaded.
    private User getUsersByNameLocked(String name) {
        for (User user : users) {
            if(user.getUsername().equalsIgnoreCase(name)){
                return user;
            }
        }
        return null;
    }

    public ArrayList<User> getUsersByRole(String role) {
        synchronized (LOCK) {
            loadUserCollection();
            ArrayList<User> result = new ArrayList<User>();
            for (User user : users) {
                if(user.getRole().equals(role)){
                    result.add(user);
                }
            }
            return result;
        }
    }

    public User getUserAdmin() {
        synchronized (LOCK) {
            loadUserCollection();
            return getUserAdminLocked();
        }
    }

    private User getUserAdminLocked() {
        for (User user : users) {
            if(user.getRole().equals("admin")){
                return user;
            }
        }
        return null;
    }


    public void changeUserAdmin(String username, String password) {
        synchronized (LOCK) {
            loadUserCollection();
            User admin = getUserAdminLocked();
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
    }

    public void replaceUserCollection(ArrayList<User> newUsers){
        synchronized (LOCK) {
            users = newUsers;
            saveUserCollection();
        }
    }

    public String changeUserPassword(String name, String oldPassword, String newPassword){
        synchronized (LOCK) {
            loadUserCollection();
            User user = getUsersByNameLocked(name);
            if (user == null) {
                return "incorrect passoword";
            }
            try {
                if (PasswordHash.validatePassword(oldPassword, user.getPassword())) {
                    user.setPassword(newPassword);
                    saveUserCollection();
                    return "password change sucessfully";
                }
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidKeySpecException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            }
            return "incorrect passoword";
        }
    }

    public int addUser(String username, String password, String email){
        synchronized (LOCK) {
            loadUserCollection();
            try {
                if(getUsersByNameLocked(username)==null){
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

    // Return codes: 1=removed, 0=not found, -2=admin user protected
    public int deleteUser(String username){
        synchronized (LOCK) {
            loadUserCollection();
            User user = getUsersByNameLocked(username);
            if (user == null) {
                return 0;
            }
            if ("admin".equals(user.getRole())) {
                return -2;
            }
            users.remove(user);
            saveUserCollection();
            return 1;
        }
    }

    /**
     * Admin-override password set for a non-admin user. Skips the old-password
     * check that {@link #changeUserPassword(String, String, String)} requires
     * (the admin doesn't know the user's old password).
     *
     * Refuses to operate on the admin user — admin self-edit goes through
     * {@link #changeUserAdmin(String, String)}.
     *
     * Return codes: 1=set, 0=not found, -2=target is admin (use changeUserAdmin instead)
     */
    public int adminSetPassword(String username, String newPassword) {
        synchronized (LOCK) {
            loadUserCollection();
            User user = getUsersByNameLocked(username);
            if (user == null) {
                return 0;
            }
            if ("admin".equals(user.getRole())) {
                return -2;
            }
            try {
                String passHash = PasswordHash.createHash(newPassword);
                user.setPassword(passHash);
                saveUserCollection();
                return 1;
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidKeySpecException ex) {
                Logger.getLogger(UserCollection.class.getName()).log(Level.SEVERE, null, ex);
            }
            return -1;
        }
    }

    /**
     * Admin-override email set. Works for any user including admin — email is
     * not a credential, so no special protection.
     *
     * Return codes: 1=set, 0=not found
     */
    public int adminSetEmail(String username, String newEmail) {
        synchronized (LOCK) {
            loadUserCollection();
            User user = getUsersByNameLocked(username);
            if (user == null) {
                return 0;
            }
            user.setEmail(newEmail);
            saveUserCollection();
            return 1;
        }
    }

}

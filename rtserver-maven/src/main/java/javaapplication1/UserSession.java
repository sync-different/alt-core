package javaapplication1;


public class UserSession {
    private String username;
    private String uuid;
    private String passwordkey;
    private String iv; 
    private String remoteCluster = null;
    private boolean aesencrypt;
    private int aessize;

    public UserSession(String username, String uuid, String passwordkey, String iv, boolean aesecrypt, int aessize) {
        this.username = username;
        this.uuid = uuid;
        this.passwordkey = passwordkey;
        this.iv = iv;
        this.aesencrypt = aesecrypt;
        this.aessize = aessize;
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

    /**
     * @return the uuid
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * @param uuid the uuid to set
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * @return the passwordkey
     */
    public String getPasswordkey() {
        return passwordkey;
    }

    /**
     * @param passwordkey the passwordkey to set
     */
    public void setPasswordkey(String passwordkey) {
        this.passwordkey = passwordkey;
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

    /**
     * @return the remoteCluster
     */
    public String getRemoteCluster() {
        return remoteCluster;
    }

    /**
     * @param remoteCluster the remoteCluster to set
     */
    public void setRemoteCluster(String remoteCluster) {
        this.remoteCluster = remoteCluster;
    }
    
    public boolean isRemote(){
        return (remoteCluster!=null && !remoteCluster.isEmpty());
    }

    /**
     * @return the aesecrypt
     */
    public boolean isAesecrypt() {
        return aesencrypt;
    }

    /**
     * @param aesecrypt the aesecrypt to set
     */
    public void setAesecrypt(boolean aesecrypt) {
        this.aesencrypt = aesecrypt;
    }

    /**
     * @return the aessize
     */
    public int getAessize() {
        return aessize;
    }

    /**
     * @param aessize the aessize to set
     */
    public void setAessize(int aessize) {
        this.aessize = aessize;
    }
}

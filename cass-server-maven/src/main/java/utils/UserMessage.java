/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;


public class UserMessage {
    
    private long timestamp;
    private String username;
    private String message;
    private String type;
    
  
    public UserMessage(Long _timestamp, String _type, String _username, String _message) {
        this.username = _username;
        this.timestamp = _timestamp;
        this.message = _message;
        this.type = _type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public String getMessageType() {
        return type;
    }

    public void setMessage(String message) {
        this.message = message;
    }    
    
}

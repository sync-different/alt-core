/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package utils;

/**
 *
 * @author Andres
 */
public class Node {
    private String name;
    private String ip;
    private String port;
    private String type;
    private String lastping;
    private String uuid;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLastping() {
        return lastping;
    }

    public void setLastping(String lastping) {
        this.lastping = lastping;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Node(String name, String ip, String port, String type, String lastping, String uuid) {
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.type = type;
        this.lastping = lastping;
        this.uuid = uuid;
    }

}

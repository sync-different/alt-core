/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package services;

import java.io.PrintStream;
import java.util.Properties;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import utils.NetUtils;
        
public class VaultLauncher implements Runnable {

    Thread t;
    private static String _bridgeHost, _bridgePort, _clusterHost, _clusterPort, _clusterId, _clusterName, _clusterToken;
    private static int _logLevel;
    long mDelay = 10000;    
    boolean _secure = false;
    boolean bTerminated = false;
    boolean mHostFound = false;
    
    RelayVaultService vs = null;
  
    public VaultLauncher(String bridgeHost, 
            String bridgePort, 
            boolean secure, 
            String clusterHost, 
            String clusterPort, 
            String clusterId, 
            String clusterName, 
            String clusterToken,
            int mLogLevel) { 
        _bridgeHost = bridgeHost;
        _bridgePort = bridgePort;
        _secure = secure;
        _clusterHost = clusterHost;
        _clusterPort = clusterPort;
        _clusterId = clusterId;
        _clusterName = clusterName;
        _clusterToken = clusterToken;
        _logLevel = mLogLevel;
    }
    
    public void terminate() {
        bTerminated = true; 
        if (vs != null) {
            vs.terminate();
        }
    }
    
    public void start() {
        // Create a new, second thread
        t = new Thread(this, "Vault Thread");
        System.out.println("Child thread: " + t);
        t.start(); // Start the thread
    }

    public boolean finished() {
        return bTerminated;
    }
    
    public boolean isnull() {
        return (vs == null ? true: false);
    }
    
    public boolean isalive() {
        if (vs != null) {
            return vs.isRunning();
        } else {
            return false;
        }
    }
    
    public void unregister() {
        if (vs != null) {
            vs.terminate();
            vs.unregister();
        }
    }
    
    public void run() {
        while (!bTerminated) {
            System.out.println("VaultService launched.");
            vs = new RelayVaultService(_bridgeHost, _bridgePort, _secure, _clusterHost, 
                                       _clusterPort, _clusterId, _clusterName, _clusterToken, _logLevel);
            vs.run();
            bTerminated = true;
            vs = null;
            System.out.println("VaultService completed.");
            System.out.append("All done. Forcing a GC");
            System.gc();
        }
    }
    
}

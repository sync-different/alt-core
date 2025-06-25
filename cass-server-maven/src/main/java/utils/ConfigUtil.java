/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 *
 * @author fcarriqiry
 */
public class ConfigUtil {
    
     public static  String GetConfig(String _name, String _config) {
        
        try {
            
            Properties props = new Properties();
            
            File f = new File(_config);
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();
                String r = props.getProperty(_name);
                if (r != null ) {
                    //p("Old value = " + r);   
                    return r;
                } else {
                    return "";
                }
            } else {
                
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }              
    }            
    
    public static int UpdateConfig(String _name, String _value, String _config) {
        
        try {
            
            Properties props = new Properties();
            
            File f = new File(_config);
           
            if (f.exists()) {
                InputStream is =new BufferedInputStream(new FileInputStream(f));
                props.clear();
                props.load(is);
                is.close();
                String r = props.getProperty(_name);
                if (r != null ) {
                    //p("Old value = " + r);                                        
                    if (!r.equals(_value)) {
                        props.setProperty(_name, _value);
                        //p("New value = " + props.getProperty(_name));                                                                                    
                        if (_name.length() > 0) {
                            OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
                            props.store(os, "comments");                            
                            os.close();                        
                        }  
                    } else {
                        //p("same as current value. skipping config write.");
                    }
                } else {
                    if (_name.length() > 0) {                        
                        props.setProperty(_name, _value);
                        OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
                        props.store(os, "comments");                            
                        os.close();                        
                    } 
                }
            } 
            return 0;
   
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }    
        
    }
}

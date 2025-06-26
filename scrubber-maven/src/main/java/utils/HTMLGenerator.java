/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */

package utils;

import java.util.HashMap;
import utils.SortableValueMap;


    
//
//clase que genera HTML en base a templates

//template = new HTMLGenerator;
//template.setmode("detail)"
//String sTD = Template.getTD(hashmap).
//sTD = sTD.replace("***nombre***", sFIleName)
//
class TemplateType {
    //template types
    public static final int TILE_TD_HEADER = 1;            
    public static final int TILE_TD_PHOTO = 2;            
    public static final int TILE_TD_TAGS = 3;            
    
    public TemplateType() {
        
    }
    //etc.
}

public class HTMLGenerator {

 
    public static String genHTMLFromTemplate(int _templatetype, String _mode) {
               
        //variables de entrada
        //templatename - e.g. TILE.TD

        //1. switch segun el template name
        
        //guardamos los template en cass/templates/TILE.TD.HEADER        
        //guardamos los template en cass/templates/TILE.TD.PHOTO        
        //guardamos los template en cass/templates/TILE.TD.TAGS
           
        TemplateType tt = new TemplateType();
        int i = TemplateType.TILE_TD_HEADER;
        
        switch (i) {
            case TemplateType.TILE_TD_HEADER:
                
            case TemplateType.TILE_TD_PHOTO:
                
            //etc
        }
        
        return "HTML ACA";
    }
    
    public int replaceTags(String _htmltemplate, HashMap<String, String> _h1) {
        
        //variables
        //_htmltempalte - html template
        //_h1 - hashmap que contiene todos los valores para reemplazar
        
        //1. loop con replace.
        
        return 0;
    }    
    
}

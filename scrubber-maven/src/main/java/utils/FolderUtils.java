/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

//import com.sun.java.swing.plaf.windows.resources.windows;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.Collections;

import java.util.Scanner;

/**
 *
 * @author fcarriquiry
 */
public class FolderUtils {
    
     /**
     * Busca los directoris contenidos en pathRootFolder mediante llamadas al OS
     * no utiliza java io
     * @param pathRootFolder
     * @return Lista de directorios contenidos en el folder pathRootFolder
     */
    public static String[] GetOSWinFoldersAsStringArray(String pathRootFolder, boolean soloUno) throws IOException, InterruptedException{

        
        ArrayList<String> result=new ArrayList<String>();
        
        if(!pathRootFolder.endsWith("\\")){
             pathRootFolder+="\\";
         }
        if(pathRootFolder.endsWith("e:\\i\\")){
            int d=0;
        }
       
         Process p = new ProcessBuilder("cmd.exe", "/C", "dir", "/a", "/o", "\""+pathRootFolder+"\"").redirectErrorStream(true).start();
         InputStream is = p.getInputStream();
         BufferedReader br = new BufferedReader(new InputStreamReader(is,"cp850"));
        
         
         String in;
         String sss="";
         while ((in = br.readLine()) != null) {
              sss+=in+"\n";
         }
                         
        Scanner sc = new Scanner(sss);
         
         boolean ok=true;
         while(sc.hasNext() && ok){
             String path = sc.nextLine();
             if(!(path.trim().length()==0 || path.trim().endsWith(".")||path.trim().endsWith("..")||(!path.contains("DIR") && !path.contains("SYMLINKD")))){
                 if(path.indexOf("<DIR>")>=0){
                    path=path.substring(path.indexOf("<DIR>")+"<DIR>".length(),path.length()).trim();
                 }else{
                     path=path.substring(path.indexOf("<SYMLINKD>")+"<SYMLINKD>".length(),path.indexOf("[")).trim();
                 }
                 path=pathRootFolder+path;
                 result.add((path));
                 if(soloUno){
                     ok=false;
                 }
             }
         }       
     
         return  result.toArray(new String[result.size()]);
    }
    
     /**
     * Busca los directoris contenidos en pathRootFolder mediante llamadas al OS
     * no utiliza java io
     * @param pathRootFolder
     * @return Lista de directorios contenidos en el folder pathRootFolder
     */
    public static String[] GetOSWinFoldersAsStringArrayFilter(String pathRootFolder, boolean soloUno) throws IOException, InterruptedException{

        
         ArrayList<String>  result=new ArrayList<String>();
         for(String s: GetOSWinFoldersAsStringArray(pathRootFolder,soloUno)){
             result.add(s);
         }  
         
                 
         result= filter(result);
         return  result.toArray(new String[result.size()]);
    }
    
     public static String[] GetOSWinFilesAsStringArray(String pathRootFolder) throws IOException, InterruptedException{

        
        ArrayList<String> result=new ArrayList<String>();
        
        if(!pathRootFolder.endsWith("\\")){
             pathRootFolder+="\\";
         }
        if(pathRootFolder.endsWith("e:\\i\\")){
            int d=0;
        }
       
         Process p = new ProcessBuilder("cmd.exe", "/C", "dir", "/a", "/o", "\""+pathRootFolder+"\"").redirectErrorStream(true).start();
         InputStream is = p.getInputStream();
         BufferedReader br = new BufferedReader(new InputStreamReader(is,"cp850"));
        
         
         String in;
         String sss="";
         while ((in = br.readLine()) != null) {
              sss+=in+"\n";
         }
                         
        Scanner sc = new Scanner(sss);
         
         
         while(sc.hasNext()  ){
             String path = sc.nextLine();
             if(!path.contains("<" )&& path.contains("/")){
                 path=path.trim().substring(path.trim().lastIndexOf(" ")+1,path.trim().length()).trim();
                 
                 path=pathRootFolder+path;
                 result.add((path));
                 
             }
         }       
          
         return  result.toArray(new String[result.size()]);
    }
     
       public static File[] GetOSWinFilesAsFileArrayFilter(String pathRootFolder) throws IOException, InterruptedException{
        ArrayList<File> result=new ArrayList<File>();
        
        for(String f: GetOSWinFilesAsStringArray(pathRootFolder)){
         result.add(new File(f));
        }
         return  result.toArray(new File[result.size()]);
    }
     
    /**
     * Busca los directoris contenidos en pathRootFolder mediante llamadas al OS
     * no utiliza java io
     * @param pathRootFolder
     * @return Lista de directorios contenidos en el folder pathRootFolder
     */
    public static File[] GetOSWinFoldersAsFileArrayFilter(String pathRootFolder, boolean soloUno) throws IOException, InterruptedException{
        ArrayList<File> result=new ArrayList<File>();
        
        for(String f: GetOSWinFoldersAsStringArrayFilter(pathRootFolder, soloUno)){
         result.add(new File(f));
        }
         return  result.toArray(new File[result.size()]);
    }
    
    private static ArrayList<String> filter(ArrayList<String> pathsIn){
        ArrayList<String> result = new ArrayList<String>();
       try{
            File fileExt=new File("../scrubber/config/blacklist_contains.txt");
            BufferedReader reader=new BufferedReader(new FileReader(fileExt));
            ArrayList<String> bl=new ArrayList<String>();
            String s1=null;
            while(( s1=reader.readLine())!=null){
                bl.add(s1);
            }
            
            reader.close();
            
            for(String s:pathsIn){
                boolean ok=true;
                s1=null;
                for(int i=0;i<bl.size() && ok;i++){
                    
                    if(bl.get(i).trim().length()>0 && s.toLowerCase().contains(bl.get(i).toLowerCase())){
                        ok=false;
                    }
                }
                if(ok){
                    result.add(s);
                }
            }
        }catch(Throwable th){
            th.printStackTrace();
        }
        return result;
    }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URLEncoder;
import java.util.Hashtable;

/**
 *
 * @author fcarriquiry
 */
 public class ScannerRecursive implements Runnable{

        private String path;
        protected boolean ejecutando=true;
        private int sleep=0;
        protected String timing="";
        
        public String getTiming(){
            return timing;
        }
        
        public void setPath(String p){
            this.path=p;
        }
        
        public String getPath(){
            return this.path;
        }
        
        public boolean getEjecutanto(){
            return this.ejecutando;
        }
        
        private void InitScan(String sFolder) throws InterruptedException{
            try{
              
                File f=new File(sFolder);
                String path = f.getAbsolutePath();
                if(!path.endsWith(File.separator)){
                        path+=File.separator;
                }
                path =URLEncoder.encode(path, "UTF-8");

                if(f.exists() && f.isDirectory()){
                    int countDir=0;
                    for(File subFol:f.listFiles()){
                        if(subFol.isDirectory()){
                            InitScan(subFol.getAbsolutePath());
                            countDir++;
                            
                        }
                    }
                    File fileExt=new File("../scrubber/config/FileExtensions_All.txt");
                    BufferedReader reader=new BufferedReader(new FileReader(fileExt));
                    String s=reader.readLine();
                    StringBuilder objectMetadata=new StringBuilder("");

                    
                    synchronized(CacheMetadataWeb.getInstance().getScanMap()){
                       
                        FolderMetaData meta=new FolderMetaData(path);
                        meta.setFolders(countDir);
                        CacheMetadataWeb.getInstance().getScanMap().put(path,meta);
                        p("ScannerRecursive add("+f.getAbsolutePath()+"):"+path);
                    }
                    while( s!=null && s.split(",").length>0) 
                    {
                        if(s.split(",")[0].equals("@")){

                        }else if(s.split(",").length>1){
                            String ext=s.split(",")[0];
                            int countFiles=CountFilesRecursive(f, ext);
                            synchronized(CacheMetadataWeb.getInstance().getScanMap()){
                                CacheMetadataWeb.getInstance().getScanMap().get(path).addExtMetadata(ext, countFiles);
                                p("ScannerRecursive add("+f.getAbsolutePath()+","+path+") Ext:"+ext+",Count:"+countFiles);
                            }

                         }

                        s=reader.readLine();

                    }

                    reader.close();
                    
                    synchronized(CacheMetadataWeb.getInstance().getScanPathNews()){
                        CacheMetadataWeb.getInstance().getScanPathNews().add(path);
                        CacheMetadataWeb.getInstance().getScanPathNews().notify();
                    }
                }
                
                ++sleep;
                if(sleep%100==0){
                    Thread.sleep(1000);
                }
            }catch(InterruptedException ie){
                throw ie;
            }
            catch(Throwable th){
                th.printStackTrace();
            }
        }

        public int CountFilesRecursive(File fn,String ext){
            int countFiles= 0;
            try{
                for(File fnIn:fn.listFiles()){
                    if( fnIn.isFile() && fnIn.getName().toLowerCase().endsWith(ext) ){
                        countFiles++; 
                    }else{
                        if( fnIn.isDirectory())
                        synchronized(CacheMetadataWeb.getInstance().getScanMap()){
                            String path = fnIn.getAbsolutePath();
                            if(!path.endsWith(File.separator)){
                                    path+=File.separator;
                            }
                            path =URLEncoder.encode(path, "UTF-8");

                            if(CacheMetadataWeb.getInstance().getScanMap().containsKey(path)){
                                countFiles+=CacheMetadataWeb.getInstance().getScanMap().get(path).getMetadata().get(ext).getCount();
                            }else{
                                p("CountFilesRecursive(Warning): Sub folder["+fnIn.getAbsolutePath()+"] not in scanMap");
                            }
                        }
                    }

                }
            }catch(Throwable th){
                th.printStackTrace();
                System.out.println("Error CountFilesRectursive "+th.getMessage());
            }
            return countFiles;
        }
    
        public void run() {
             try{
                InitScan(this.path);
                ejecutando=false;
             }catch(InterruptedException ie){
                 ie.printStackTrace();
             }
             
        }
        
         public Hashtable<String,String> getProcesadosProcesando(){
            return new Hashtable<String, String>();
        }
        
    protected static void p(String s) {

        long threadID = Thread.currentThread().getId();
        System.out.println("[" + threadID + "] " + s);
    }
    }
        
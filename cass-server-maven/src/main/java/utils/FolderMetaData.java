/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import java.util.HashMap;

/**
 *
 * @author fcarriquiry
 */
public class FolderMetaData {

    public static HashMap<String,Integer> TABLA_RECOMMENDED_LIMIT=new HashMap<String, Integer>();
    public static HashMap<String,String> TABLA_RECOMMENDED_EXT=new HashMap<String, String>();
    static{
        TABLA_RECOMMENDED_LIMIT.put("Documents",25);
        TABLA_RECOMMENDED_LIMIT.put("Video Files",25);
        TABLA_RECOMMENDED_LIMIT.put("Audio Files",25);
        TABLA_RECOMMENDED_LIMIT.put("Image Files",25);
        TABLA_RECOMMENDED_LIMIT.put("Other",25);
        
        TABLA_RECOMMENDED_EXT.put(".doc", "Documents");
        TABLA_RECOMMENDED_EXT.put(".docx", "Documents");
        TABLA_RECOMMENDED_EXT.put(".pdf", "Documents");
        TABLA_RECOMMENDED_EXT.put(".ppt", "Documents");
        TABLA_RECOMMENDED_EXT.put(".pptx", "Documents");
        TABLA_RECOMMENDED_EXT.put(".xls", "Documents");
        TABLA_RECOMMENDED_EXT.put(".xlsx", "Documents");
        TABLA_RECOMMENDED_EXT.put(".mov", "Video Files");
        TABLA_RECOMMENDED_EXT.put(".mpg", "Video Files");
        TABLA_RECOMMENDED_EXT.put(".mmv", "Video Files");
        TABLA_RECOMMENDED_EXT.put(".mp3", "Audio Files");
        TABLA_RECOMMENDED_EXT.put(".mp4", "Audio Files");
        TABLA_RECOMMENDED_EXT.put(".m4a", "Audio Files");
        TABLA_RECOMMENDED_EXT.put(".jpg", "Image Files");
        TABLA_RECOMMENDED_EXT.put(".jpeg","Image Files");
        TABLA_RECOMMENDED_EXT.put(".gif", "Image Files");
        TABLA_RECOMMENDED_EXT.put(".png", "Image Files");
        TABLA_RECOMMENDED_EXT.put(".zip", "Other");
        TABLA_RECOMMENDED_EXT.put(".dll", "Other");
        TABLA_RECOMMENDED_EXT.put(".exe", "Other");
        TABLA_RECOMMENDED_EXT.put(".html", "Other");
        TABLA_RECOMMENDED_EXT.put(".htm", "Other");
        TABLA_RECOMMENDED_EXT.put(".txt", "Other");
        TABLA_RECOMMENDED_EXT.put(".java", "Other");
        TABLA_RECOMMENDED_EXT.put(".pst", "Other");
        
    }
    
    public class RecommendedMetaData{
        private int count;
        private String seccion;
        
        public RecommendedMetaData(int count,String seccion){
            this.count=count;
            this.seccion=seccion;
        }
        
        /**
         * @return the count
         */
        public int getCount() {
            return count;
        }

        /**
         * @param count the count to set
         */
        public void setCount(int count) {
            this.count = count;
        }

        /**
         * @return the ext
         */
        public String getSeccion() {
            return seccion;
        }

        /**
         * @param ext the ext to set
         */
        public void setSeccion(String ext) {
            this.seccion = seccion;
        }
        
    }
    
    public class ExtMetaData{
        private int count;
        private String ext;
        
       
        
        public ExtMetaData(int count,String ext){
            this.count=count;
            this.ext=ext;
        }
        
        /**
         * @return the count
         */
        public int getCount() {
            return count;
        }

        /**
         * @param count the count to set
         */
        public void setCount(int count) {
            this.count = count;
        }

        /**
         * @return the ext
         */
        public String getExt() {
            return ext;
        }

        /**
         * @param ext the ext to set
         */
        public void setExt(String ext) {
            this.ext = ext;
        }
        
    }
    
    public HashMap<String,ExtMetaData> metadata=new HashMap<String, ExtMetaData>();
    public HashMap<String,RecommendedMetaData> metadataRecommended=new HashMap<String, RecommendedMetaData>();
    private String path;
    private int folders;
    private boolean notificada=false;
       
    public FolderMetaData(String path){
        this.path=path;
    }
    
    public void setNotificada(boolean not){
           this.notificada=not;
    }

    public boolean getNotificada(){
        return this.notificada;
    }
        
    /**
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * @param path the path to set
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * @return the folders
     */
    public int getFolders() {
        return folders;
    }

    /**
     * @param folders the folders to set
     */
    public void setFolders(int folders) {
        this.folders = folders;
    }
    
    public void addExtMetadata(String ext,int count){
        
        
        if(!metadata.containsKey(ext)){
            metadata.put(ext, new ExtMetaData(count, ext));
        }else{
            metadata.remove(ext);
            metadata.put(ext, new ExtMetaData(count, ext));
        }
        
    }
    
    public void addRecommendedMetadata(String seccion,int count){
        if(!metadataRecommended.containsKey(seccion)){
            metadataRecommended.put(seccion, new RecommendedMetaData(count, seccion));
        }else{
            metadataRecommended.remove(seccion);
            metadataRecommended.put(seccion, new RecommendedMetaData(count, seccion));
        }
    }
    
     
    
    public HashMap<String,ExtMetaData> getMetadata(){
        return metadata;
    }
    
    public HashMap<String,RecommendedMetaData> getRecommendedMetaData(){
        return metadataRecommended;
    }
    
    
}

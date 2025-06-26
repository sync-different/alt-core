/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;
import static utils.ScannerRecursive.p;

/**
 *
 * @author fcarriquiry
 */
public class ScannerInteligentStack extends ScannerRecursive {
    
    private class RecursiveAndLocalCount{
        private int countLocal;
        private int countRecirsive;
        public RecursiveAndLocalCount(int cl,int cr){
            this.countLocal=cl;
            this.countRecirsive=cr;
        }
        
        public int getLocal(){
            return countLocal;
        }
        
        public int getRecursive(){
            return countRecirsive;
        }
        
        public void addLocal(int lo){
            this.countLocal+=lo;
        }
        
        public void addRecursive(int cr){
            this.countRecirsive+=cr;
        }
    }
    
    private Vector<String> stack=new Vector<String>();
    private Hashtable<String,String> procesadosProcesando=new Hashtable<String, String>();
    private String pathNew=null;
    private Map<String,Integer> marcados=new HashMap<String,Integer>();
    private int sleep=0;
    private int scantreevariant=0;
    private int scanTreeVelocity=20;
    
    public Hashtable<String,String> getProcesadosProcesando(){
        return this.procesadosProcesando;
    }
    public void setScanTreeVariant(int variant){
        this.scantreevariant=variant;        
    }
    
    public void setScanTreeVelocity(int v){
        this.scanTreeVelocity=v;        
    }
     
    public synchronized void setPath(String sFolder){
        try{
           
            File f=new File(sFolder);
            String path = f.getAbsolutePath();
            if(!path.endsWith(File.separator)){
                    path+=File.separator;
            }
            if(!procesadosProcesando.containsKey(path)){
                path =URLEncoder.encode(path, "UTF-8");
                procesadosProcesando.put(path, path);
                synchronized(stack){
                    pathNew=path;
                } //stack.add(0, path);
            
                notify();
            }
        }catch(Throwable th){
            th.printStackTrace();
        }
    }
    
    public void InitScan() throws InterruptedException{
        Date inicio= null;
        
        while(true){
            if(stack.isEmpty() && pathNew==null){
               synchronized(this){
                   ejecutando=false;
                   if(inicio!=null){
                       Date fin=new Date();
                       Date aux=new Date(fin.getTime()- inicio.getTime());
                       SimpleDateFormat format=new SimpleDateFormat("HH:mm:ss");
                       timing=format.format(aux);
                       inicio=null;
                   }else{
                      timing="";
                   }                 
                   
                   wait();
                   
               }
            }
            
            try{
              while(!stack.isEmpty() || pathNew!=null){
                ejecutando=true;
                if(inicio==null){
                    inicio=new Date();
                    timing="";
                }
                
        
                File f=null;
                synchronized(stack){
                    
                    if(pathNew!=null){
                        stack.add(0, pathNew);
                        pathNew=null;
                    } 
                } 
                
                f=new File(URLDecoder.decode(stack.elementAt(0),"UTF-8"));
                stack.remove(0);
                String path = f.getAbsolutePath();
               
                if(!procesadosProcesando.containsKey(path)){
                    if(f.exists() && f.isDirectory()){
                        if(!path.endsWith(File.separator)){
                                path+=File.separator;
                         }
                         path =URLEncoder.encode(path, "UTF-8");

                        if(!marcados.containsKey(path)){

                            stack.add(0,path);//Agrego path para que se procese despues de sus subdirectorios

                            int countDir=0;
                            try{
                                File[] folders=null;
                                if(scantreevariant==0){
                                    folders=f.listFiles();
                                }else{
                                    folders= FolderUtils.GetOSWinFoldersAsFileArrayFilter(f.getAbsolutePath(), false);
                                }
                                
                                if(folders!=null)    
                                for(File subFol:folders){
                                    if(subFol.isDirectory()){
                                        String pathSub =subFol.getAbsolutePath();
                                        if(!pathSub.endsWith(File.separator)){
                                                pathSub+=File.separator;
                                        }
                                        pathSub =URLEncoder.encode(pathSub, "UTF-8");
                                        stack.add(0,pathSub);
                                        if(!procesadosProcesando.containsKey(pathSub)){
                                            procesadosProcesando.put(pathSub, pathSub);
                                        }
                                        countDir++;

                                    }
                                }
                            }catch(Throwable th){
                                th.printStackTrace();
                            }

                            marcados.put(path,countDir);

                        }else{



                            File fileExt=new File("../scrubber/config/FileExtensions_All.txt");
                            BufferedReader reader=new BufferedReader(new FileReader(fileExt));
                            String s=reader.readLine();
                            StringBuilder objectMetadata=new StringBuilder("");


                            synchronized(CacheMetadataWeb.getInstance().getScanMap()){

                                FolderMetaData meta=new FolderMetaData(path);
                                meta.setFolders(marcados.get(path));
                                CacheMetadataWeb.getInstance().getScanMap().put(path,meta);
                                p("ScannerRecursive add("+f.getAbsolutePath()+"):"+path);
                            
                                RecursiveAndLocalCount countSeccion=null;
                                String seccion="";
                                while( s!=null && s.split(",").length>0) 
                                {
                                    if(s.split(",")[0].equals("@")){
                                        if(countSeccion!=null){
                                            if(FolderMetaData.TABLA_RECOMMENDED_LIMIT.get(seccion)<=countSeccion.getRecursive()   ){
                                                addRecommendation(path, seccion, countSeccion);
                                            }
                                        }
                                        seccion=s.split(",")[1];
                                        countSeccion=new RecursiveAndLocalCount(0, 0);
                                    }else if(s.split(",").length>1){
                                        String ext=s.split(",")[0];
                                        RecursiveAndLocalCount count=CountFilesRecursiveAndLocal(f, ext);
                                        countSeccion.addLocal(count.getLocal()); 
                                        countSeccion.addRecursive(count.getRecursive());


                                            CacheMetadataWeb.getInstance().getScanMap().get(path).addExtMetadata(ext, count.getRecursive());
                                            p("ScannerRecursive add("+f.getAbsolutePath()+","+path+") Ext:"+ext+",Count:"+count.getRecursive());


                                     }

                                    s=reader.readLine();

                                }
                                if(countSeccion!=null){
                                    if(FolderMetaData.TABLA_RECOMMENDED_LIMIT.get(seccion)<=countSeccion.getRecursive()){
                                        addRecommendation(path, seccion, countSeccion);
                                    }
                                }
                            }
                            reader.close();

                            synchronized(CacheMetadataWeb.getInstance().getScanPathNews()){
                                CacheMetadataWeb.getInstance().getScanPathNews().add(path);
                                CacheMetadataWeb.getInstance().getScanPathNews().notify();
                            }

                            ++sleep;
                            if(sleep%scanTreeVelocity==0){
                                Thread.sleep(1000);
                            }
                         }

                    }
                  }
              }
            }catch(InterruptedException ie){
                throw ie;
            }
            catch(Throwable th){
                th.printStackTrace();
            }
             
        }
    }
    
     public RecursiveAndLocalCount CountFilesRecursiveAndLocal(File fn,String ext){
        int countFiles= 0;
        int countLocal=0;
        try{
            File[] files=null;
            
            if(scantreevariant==1){
                files= FolderUtils.GetOSWinFilesAsFileArrayFilter(fn.getAbsolutePath());
            }else{
                files= fn.listFiles();
            }
         
            if(files!=null)    
            for(File fnIn:files){
                if( fnIn.isFile() && fnIn.getName().toLowerCase().endsWith(ext) ){
                    countFiles++; 
                    countLocal++;
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
        return new RecursiveAndLocalCount(countLocal, countFiles);
    }
    
    @Override
    public void run(){
         try{
                InitScan( );
                ejecutando=false;
         }catch(InterruptedException ie){
             ie.printStackTrace();
         }catch(Throwable th){
             th.printStackTrace();
         }
    }

    private void addRecommendation(String path, String seccion, RecursiveAndLocalCount countSeccion) {
       if(!seccion.equalsIgnoreCase("other")){
        if(countSeccion.getLocal()>= FolderMetaData.TABLA_RECOMMENDED_LIMIT.get(seccion)){
            //El padre entra por si mismo, se eliminan todos sus hijos
            CacheMetadataWeb.getInstance().getScanMap().get(path).addRecommendedMetadata(seccion, countSeccion.getRecursive());
            if(!CacheMetadataWeb.getInstance().getScanPathRecommended().contains(path)){
                CacheMetadataWeb.getInstance().getScanPathRecommended().add(path);
            }
             Vector<String> aux=new Vector<String>();
             aux.addAll(CacheMetadataWeb.getInstance().getScanPathRecommended());
             for(String fKey:aux){
                if(!path.equals(fKey) && fKey.startsWith(path)){
                    if(!path.equals(fKey) && fKey.startsWith(path)){
                        
                        if(CacheMetadataWeb.getInstance().getScanMap().get(fKey).getRecommendedMetaData().containsKey(seccion)){
                           CacheMetadataWeb.getInstance().getScanMap().get(fKey).getRecommendedMetaData().remove(seccion);
                           if(CacheMetadataWeb.getInstance().getScanMap().get(fKey).getRecommendedMetaData().size()==0){
                                CacheMetadataWeb.getInstance().getScanPathRecommended().remove(fKey);
                           }
                        }
                    }
                }
            }
        }else{ 
            //El padre entro por sus hijos, me fijo cuantos hijos recomendados tiene
            int countChildRecommended=0;
           
            for(String pathR:CacheMetadataWeb.getInstance().getScanPathRecommended()){
                if(!pathR.equals(path) && pathR.startsWith(path)){
                    if( CacheMetadataWeb.getInstance().getScanMap().get(pathR).getRecommendedMetaData().containsKey(seccion)){
                        countChildRecommended++;
                    }
                }
            }
            //Puede que tenga ma sde dos hijos recomendados o puede que ninguno ya que la suma individual no supera los límites
            //pero la grupal si, en este ultimo caso entra igual
            if(countChildRecommended>2){
                //Se promueve al padre y se eliminan los hijos
                CacheMetadataWeb.getInstance().getScanMap().get(path).addRecommendedMetadata(seccion, countSeccion.getRecursive());
                if(!CacheMetadataWeb.getInstance().getScanPathRecommended().contains(path)){
                    CacheMetadataWeb.getInstance().getScanPathRecommended().add(path);
                }
    
                Vector<String> aux=new Vector<String>();
                aux.addAll(CacheMetadataWeb.getInstance().getScanPathRecommended());
                for(String fKey:aux){
                    if(!path.equals(fKey) && fKey.startsWith(path)){
                        if(CacheMetadataWeb.getInstance().getScanMap().get(fKey).getRecommendedMetaData().containsKey(seccion)){
                           CacheMetadataWeb.getInstance().getScanMap().get(fKey).getRecommendedMetaData().remove(seccion);
                           if(CacheMetadataWeb.getInstance().getScanMap().get(fKey).getRecommendedMetaData().size()==0){
                                CacheMetadataWeb.getInstance().getScanPathRecommended().remove(fKey);
                           }
                        }
                    }
                }
            }else if(countChildRecommended==0){
                //Se promueve al padre por la suma de sus hijos
                CacheMetadataWeb.getInstance().getScanMap().get(path).addRecommendedMetadata(seccion, countSeccion.getRecursive());
                if(!CacheMetadataWeb.getInstance().getScanPathRecommended().contains(path)){
                    CacheMetadataWeb.getInstance().getScanPathRecommended().add(path);
                }
            }//Si no se deja a los hijos y el padre no entra en los recomendados
        }
       }
    }
}

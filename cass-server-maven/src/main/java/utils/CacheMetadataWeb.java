/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

/**
 *
 * @author fcarriquiry
 */
public class CacheMetadataWeb {
    private static HashMap<String, FolderMetaData> scanMap=new HashMap<String, FolderMetaData>();
    private static HashMap<String,ScannerRecursive > scanObject=new HashMap<String, ScannerRecursive>() ;
    private static HashMap<String,Thread > scanObjectThread=new HashMap<String, Thread>() ;
    private static Vector<String> scanPathNews=new Vector<String>();
    private static Vector<String> scanPathRecommended=new Vector<String>();
    private static CacheMetadataWeb instance=null;
    
    public static synchronized CacheMetadataWeb getInstance(){
        if(instance==null){
            instance=new CacheMetadataWeb();
        }
        
        return instance;
    }
    
    public HashMap<String, FolderMetaData> getScanMap(){return scanMap;}
    public HashMap<String,ScannerRecursive > getScanObject(){ return scanObject;}
    public HashMap<String,Thread > getScanObjectThread(){ return scanObjectThread;};
    public Vector<String> getScanPathNews(){return scanPathNews;}
    public Vector<String> getScanPathRecommended(){return scanPathRecommended;}
     
    public void startScan(String sFolder, int velocity,int scanTreeVariant, int scanTreeMode,boolean bWindowsServer){
       
        if(scanTreeMode!=0){
            if(scanTreeMode==3){//Scan complete
                  //Controlo que el scann no esté iniciado ya para ese disco
                if(!CacheMetadataWeb.getInstance().getScanObjectThread().containsKey(sFolder)){
                    ScannerRecursive scannerR=new ScannerRecursive();
                  
                    Thread t=new Thread(scannerR);
                    t.start();
                    scannerR.setPath(sFolder);
                    CacheMetadataWeb.getInstance().getScanObjectThread().put(sFolder, t);
                    CacheMetadataWeb.getInstance().getScanObject().put(sFolder, scannerR);
                }
            }else if(scanTreeMode==2 && isCheckScanMode(sFolder,scanTreeMode,bWindowsServer)){//Scan on click folder
               boolean rootScanInit=false;
               
               if(!rootScanInit){
                    if( CacheMetadataWeb.getInstance().getScanObjectThread().isEmpty()){
                          ScannerInteligentStack scannerI=new ScannerInteligentStack();
                          scannerI.setPath(sFolder);
                          scannerI.setScanTreeVariant(scanTreeVariant);
                           scannerI.setScanTreeVelocity(velocity);
                           
                          Thread t=new Thread(scannerI);
                          
                          t.start();
                          CacheMetadataWeb.getInstance().getScanObjectThread().put(sFolder, t);
                          CacheMetadataWeb.getInstance().getScanObject().put(sFolder, scannerI);

                    }else{
                        CacheMetadataWeb.getInstance().getScanObject().get(CacheMetadataWeb.getInstance().getScanObject().keySet().iterator().next()).setPath(sFolder);
                    }
               }
             }
        }
    }
    
   public boolean isCheckScanMode(String pathDec,int scanTreeMode,boolean bWindowsServer) {
        if(scanTreeMode==1){
            return false;
        }
        boolean root=false;
        if(bWindowsServer){
            for(File f: File.listRoots()){
                if(f.getAbsolutePath().equalsIgnoreCase(pathDec)){
                    root=true;
                    break;
                }
            }
        }else{
            root= pathDec.equalsIgnoreCase("/")||pathDec.equalsIgnoreCase("/Volumes")||pathDec.equalsIgnoreCase("/media") ;
        }
        
        if(!root && scanTreeMode==2) return true;
        
        if(scanTreeMode==3) return true;
        
        return false;
    }
 
   public void clearScan(){
       try{
        for(Thread t: CacheMetadataWeb.getInstance().getScanObjectThread().values()){
                        t.interrupt();
                        t.join();
         }
         CacheMetadataWeb.getInstance().getScanObjectThread().clear();
         CacheMetadataWeb.getInstance().getScanObject().clear();
         CacheMetadataWeb.getInstance().getScanMap().clear();
         CacheMetadataWeb.getInstance().getScanPathNews().clear();
         CacheMetadataWeb.getInstance().getScanPathRecommended().clear();
       }catch(Throwable th){
           th.printStackTrace();
       }
   }

    public void initAutoScan(String sFolder,int velocity, int scanTreeVariant, int scanTreeMode,boolean bWindowsServer) {
        if(scanTreeMode==2){
        if(bWindowsServer){
            if(sFolder.equalsIgnoreCase("c:\\")){
                File file=new File("c:\\Users");
                if(file.exists()){
                    clearScan();
                    startScan(file.getAbsolutePath(),velocity,scanTreeVariant, scanTreeMode, bWindowsServer);
                }
            }
        }else{
            if(sFolder.equalsIgnoreCase("/")){
                File file=new File("/Users");
                if(file.exists()){
                    clearScan();
                    startScan(file.getAbsolutePath(),velocity,scanTreeVariant, scanTreeMode, bWindowsServer);
                }else{
                    file=new File("/home");
                    if(file.exists()){
                        clearScan();
                        startScan(file.getAbsolutePath(),velocity,scanTreeVariant, scanTreeMode, bWindowsServer);
                    }
                }
            }
        }
       }
    }


}

/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package processor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

public class ZipFolder {
    
  public void ZipFolder() {
      System.out.println("ZipFolder constructor.");
  }
  
  public void main(String[] a) throws Exception {
    System.out.println("zipping outgoing");
    zipFolder("/Applications/boxology/scrubber/outgoing", "a.zip");
    System.out.println("zipping done");
    
    System.out.println("unzipping");
    unzipFile("a.zip", "./testunzip");
    System.out.println("unzipping done");
    
  }

  public int unzipFile(String sourceZipFile, String destFolder) {

      ZipInputStream zipinputstream = null;
      int nres = 0;
      
    try {
        //String destinationname = "d:\\";
        byte[] buf = new byte[1024];
        zipinputstream = null;
        ZipEntry zipentry;
        zipinputstream = new ZipInputStream(new FileInputStream(sourceZipFile));
        zipentry = zipinputstream.getNextEntry();

        while (zipentry != null) {
          String entryName = zipentry.getName();
          
          String entryName2 = entryName.substring(entryName.lastIndexOf("/") + 1, entryName.length());
          System.out.println(entryName2);
          
          FileOutputStream fileoutputstream;
          File newFile = new File(sourceZipFile);
          String directory = newFile.getParent();

          if (directory == null) {
            if (newFile.isDirectory())
              break;
          }
          fileoutputstream = new FileOutputStream(destFolder + File.separator + entryName2);
          int n;
          while ((n = zipinputstream.read(buf, 0, 1024)) > -1){
            fileoutputstream.write(buf, 0, n);
          }
          fileoutputstream.close();
          zipinputstream.closeEntry();
          zipentry = zipinputstream.getNextEntry();
        }
        //zipinputstream.close();
        nres = 1;

    } catch (Exception e) {
        e.printStackTrace();        
        nres = -1;
    } finally {
        try {
            zipinputstream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return nres;
    }
  }
  
  public int zipFolder(String srcFolder, String destZipFile) throws Exception {
    ZipOutputStream zip = null;
    FileOutputStream fileWriter = null;

    fileWriter = new FileOutputStream(destZipFile);
    zip = new ZipOutputStream(fileWriter);

    addFolderToZip("", srcFolder, zip);
    zip.flush();
    zip.close();
    return 1;
    
  }

  public int addFileToZip(String path, String srcFile, ZipOutputStream zip)
      throws Exception {

      try {
          
        System.out.println("path = " + path);
        System.out.println("srcfile = " + srcFile);
        
        File folder = new File(srcFile);
        
        if (folder.isDirectory()) {
            addFolderToZip(path, srcFile, zip);
        } else {
            byte[] buf = new byte[1024];
            int len;
            FileInputStream in = new FileInputStream(srcFile);
            //zip.putNextEntry(new ZipEntry(path + "/" + folder.getName()));
            zip.putNextEntry(new ZipEntry(folder.getName()));
            while ((len = in.read(buf)) > 0) {
                zip.write(buf, 0, len);
            }     
            in.close();
            in = null;
                    
        }
        return 1;
         
      } catch (Exception e) {
         System.out.println("Warning. Exception with srcFile: " + srcFile); 
         return -1;
      }
  }

  public void addFolderToZip(String path, String srcFolder, ZipOutputStream zip)
      throws Exception {
    File folder = new File(srcFolder);

    int nres = 0;
    for (String fileName : folder.list()) {
      if (path.equals("")) {
        System.out.println("case1");
        System.out.println("path = " + path);
        System.out.println("srcfolder = " + srcFolder);
        nres = addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
      } else {
        System.out.println("case2");
        nres = addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
      }
    }
  }
}
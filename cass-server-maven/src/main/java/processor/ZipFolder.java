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

import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;

public class ZipFolder {

    // ***** BEGIN ANSI *****

    static boolean bConsole = true;

    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_RESET = "\u001B[0m";

    protected static void pw(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.ZipFolder-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.ZipFolder-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [CS.ZipFolder-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    static protected void p(String s) {

        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.ZipFolder_" + threadID + "] " + s);
    }

    // ****** END ANSI
    
  public void ZipFolder() {
      p("ZipFolder constructor.");
  }
  
  public void main(String[] a) throws Exception {
    p("zipping outgoing");
    zipFolder("/Applications/boxology/scrubber/outgoing", "a.zip");
    p("zipping done");
    
    p("unzipping");
    unzipFile("a.zip", "./testunzip");
    p("unzipping done");
    
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
          p(entryName2);
          
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
          
        p("path = " + path);
        p("srcfile = " + srcFile);
        
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
         pw("Warning. Exception with srcFile: " + srcFile);
         return -1;
      }
  }

  public void addFolderToZip(String path, String srcFolder, ZipOutputStream zip)
      throws Exception {
    File folder = new File(srcFolder);

    int nres = 0;
    for (String fileName : folder.list()) {
      if (path.equals("")) {
        p("case1");
        p("path = " + path);
        p("srcfolder = " + srcFolder);
        nres = addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
      } else {
        p("case2");
        nres = addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
      }
    }
  }
}
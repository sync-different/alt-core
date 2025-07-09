/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */

package processor;

//import image.ImageLib;
//import image.ThumbnailGenerator;
//import io.FileFunctions;
//import image.ImageLib;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import java.io.Serializable;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.StringTokenizer;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.io.BufferedInputStream;
import java.nio.ByteBuffer;

import org.jaudiotagger.audio.*;
import org.jaudiotagger.audio.exceptions.*;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;

import net.coobird.thumbnailator.Thumbnails;

import com.sun.pdfview.PDFFile;
import com.sun.pdfview.PDFPage;
import com.sun.pdfview.PDFParseException;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

public class DatabaseEntry implements Serializable {
    
    private static final long serialVersionUID = 8001L;

    
    public UUID   dbe_uuid;           //node UUID
    public String dbe_md5;            //md5 hash of the file
    public long   dbe_lastmodified;   //date last modified
    public String dbe_absolutepath;   //file path
    public long   dbe_filesize;       //file length
    public String dbe_filename;       //file name
    public String dbe_filetype;       //file type
    public List<String> dbe_folders = new ArrayList();           //parent folders
    
    public String dbe_mp3_artist;     /* name of artist for MP3 */
    public String dbe_mp3_title;      /* name of song of MP3 */
    
    public int    dbe_img_height;     //image height
    public int    dbe_img_width;      //image width
    public byte[] dbe_img_thumbnail;  //image thumbnail
    public String dbe_action;         //to know if it is New or Delete file
        
    public String dbe_keywords;
    
    //constructor for Delete file
    public DatabaseEntry(String _md5, UUID _uuid, String _filePath) {
        dbe_action = "DELETE";
        dbe_md5 = _md5;
        dbe_uuid = _uuid;
        dbe_absolutepath = _filePath;
    }

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
            System.out.println(ANSI_YELLOW + sDate + " [WARNING] [CS.DatabaseEntry-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pi(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_GREEN + sDate + " [INFO ] [CS.DatabaseEntry-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    protected static void pe(String s) {
        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        if (bConsole) {
            long threadID = Thread.currentThread().getId();
            System.out.println(ANSI_RED + sDate + " [ERROR] [CS.DatabaseEntry-" + threadID + "] " + s + ANSI_RESET);
        }
    }

    /* print to stdout */
    static protected void p(String s) {

        Date ts_start = Calendar.getInstance().getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        String sDate = sdf.format(ts_start);

        long threadID = Thread.currentThread().getId();
        System.out.println(sDate + " [DEBUG] [CS.DatabaseEntry_" + threadID + "] " + s);
    }

    // ****** END ANSI
    
     //constructor for New file
     public DatabaseEntry(String _md5, UUID _uuid, File _file, float _thumbnail_compression, long _delay) {
         
         try {
            dbe_action = "NEW"; 
            dbe_uuid = _uuid;
            dbe_md5 = _md5;
            dbe_lastmodified = _file.lastModified();
            dbe_absolutepath = _file.getAbsolutePath();
            dbe_filesize = _file.length();
            dbe_filename = _file.getName();
            dbe_filetype = dbe_filename.substring(dbe_filename.lastIndexOf(".") + 1, dbe_filename.length());

            String delimiters = "/\\";
            p("fullpath = '" + dbe_absolutepath + "'");
            StringTokenizer st = new StringTokenizer(dbe_absolutepath.trim(), delimiters, true); 
            while (st.hasMoreTokens()) {
                   String w = st.nextToken();
                   //p("token = '" + w + "'");
                   if (!w.equals("\\") && !w.equals("/")) {
                       try {
                           //dbe_folders.add(URLEncoder.encode(w,"UTF-8"));
                           dbe_folders.add(w);
                       } catch (Exception e) {
                           e.printStackTrace();
                       }
                   }
            }

            p("filetype = " + dbe_filetype);

            if (is_photo(dbe_filetype.toLowerCase())){
                       FileInputStream fis = null;
                       BufferedImage originalImage = null;
                       BufferedImage thumbnail_image = null;
                       Thumbnails.Builder b = null;
                       
                  //ThumbnailGenerator thumbnail_generator = new ThumbnailGenerator(_file);
                   p("is_photo = true");
                   try {
//                       boolean newFormat=false;
//                       if(dbe_filetype.toLowerCase().endsWith("png") ||dbe_filetype.toLowerCase().endsWith("gif")  ){
//                           newFormat=true;
//                       }
                       
                       //get the image dimensions
                       p("processing photo file" + _file.getAbsolutePath());
                       p("[1a]");
                       fis = new FileInputStream(_file);
                       p("[1b]");
                       originalImage = ImageIO.read(fis);
                       p("[1c]");
                       dbe_img_height = originalImage.getHeight();
                       p("[1d]");
                       dbe_img_width = originalImage.getWidth();
                                                                   
//                       image.flush();                       
//                       image = null;
//                       fis.close();

                       p("img h = '" + dbe_img_height + "'");
                       p("img w = '" + dbe_img_width + "'");

                       //generate the thumbnail
                       //BufferedImage thumbnail_image = thumbnail_generator.generate_stepped(180, 180);
                        
                       //fis = new FileInputStream(_file);
                       //BufferedImage originalImage = ImageIO.read(fis);
                       
                       //method #1 (thumbnailator)
                       //thumbnail_image = Thumbnails.of(originalImage).size(200, 200).asBufferedImage();
                       ByteArrayOutputStream baos = new ByteArrayOutputStream();  
                       p("before thumbnails");
                       Thumbnails.of(_file).size(200, 200).outputFormat("jpg").toOutputStream(baos);
                       p("after thumbnails");

                       //method #2 (thumbnailator with builder)
                       //b = Thumbnails.of(_file.getAbsoluteFile());
                       //b.height(200);
                       //b.width(200);
                       //thumbnail_image = b.asBufferedImage();
                       //b = null;
                                                                  
                       //method #3
                       //thumbnail_image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
                       //Graphics2D g = thumbnail_image.createGraphics();
                       //g.drawImage(originalImage.getScaledInstance(200, 200, Image.SCALE_SMOOTH), 0, 0, 200, 200, null);
                       //g.dispose();
                        
                                             
                       //save the image to byte array                       
                       
                       //method #1
                       //ByteArrayOutputStream baos = new ByteArrayOutputStream();                      
                       //ImageIO.write(thumbnail_image, "jpg", baos);

                       baos.flush();
                       dbe_img_thumbnail = baos.toByteArray();
                                              
                       //method #2
                       //dbe_img_thumbnail = toByteArray(thumbnail_image, "jpg", _thumbnail_compression);
                                 
                       pw("Thumbnail was created. Zzz for: " + _delay);
                       Thread.sleep(_delay);

                   } catch (java.awt.color.CMMException ex) {                           
                       pw("exception --> CMMException");
                       ex.printStackTrace();
                       dbe_img_thumbnail = null;
                       dbe_action = "EXC";
                   } catch (javax.imageio.IIOException ex) {                    
                        pw ("ssss");
                   } catch (IOException ex) {
                       pw("exception --> IOException");
                       ex.printStackTrace();
                       dbe_img_thumbnail = null;
                       dbe_action = "EXC";
                   } catch (Exception ex){
                       pw("exception --> Exception");
                       ex.printStackTrace();
                       dbe_img_thumbnail = null;
                       File dir = new File ("./corrupted/");
                       dir.mkdir();
                       copyfile(_file, new File("./corrupted/" + dbe_md5 + "_" + _file.getName()));
                       dbe_action = "EXC";
                   } catch (OutOfMemoryError ex) {
                       pw("@@@***@@@ exception --> OutofMemory[img]");
                       ex.printStackTrace();
                       dbe_action = "OOM";
                       dbe_img_thumbnail = null;                       
                                       
                   } finally {
                       p("@@@***@@@ finally...");
                       try {
                           close(fis);
                            if (b!= null) {
                                b = null;
                            }
                            if (originalImage != null) {
                               originalImage.flush();
                               originalImage = null;
                            }
                            //fis.close(); 
                            if (thumbnail_image != null) {
                                thumbnail_image.flush();
                                thumbnail_image = null;
                            }
                            System.gc();
                       } catch (Exception e) {
                           pw("WARNING: there was exception in Databaseentry constructor finally...");
                           e.printStackTrace();
                       }

                   }
               }

            if (dbe_filetype.toLowerCase().equals("mp3") || dbe_filetype.toLowerCase().equals("m4a")){
                   try {
                       AudioFile f = AudioFileIO.read(_file);
                       dbe_mp3_artist = f.getTag().getFirst(FieldKey.ARTIST);
                       dbe_mp3_title = f.getTag().getFirst(FieldKey.TITLE);

                       p("Title = '" + dbe_mp3_title + "'");
                       p("Artist = '" + dbe_mp3_artist + "'");

                       //add artist name and title to list of folders for the index

                       Artwork art = f.getTag().getFirstArtwork();                      

                       if (art != null) {
                           BufferedImage thumbnail_image = (BufferedImage)art.getImage();
                           dbe_img_thumbnail = toByteArray(thumbnail_image, "jpg", _thumbnail_compression);
                           thumbnail_image.flush();
                           thumbnail_image = null;
                       } else {
                           p("No thumbnail was found for this mp3.");
                           dbe_img_thumbnail = null;
                       }
                       f = null;
                       art = null;
                       
                   } catch (OutOfMemoryError ex) {
                       pw("@@@***@@@ exception --> OutofMemoryException[mp3]");
                       dbe_action = "OOM";
                       dbe_img_thumbnail = null;
                       ex.printStackTrace();
                       System.gc();
                   } catch (IOException ex) {
                       //error_ = true;
                       dbe_img_thumbnail = null;
                       pw("exception --> IOException");
                       ex.printStackTrace();
                       dbe_action = "EXC";
                   } catch (CannotReadException ex) {
                       dbe_img_thumbnail = null;
                       pw("exception --> CannotReadException");
                       ex.printStackTrace();
                       dbe_action = "EXC";
                   } catch (TagException ex) {
                       dbe_img_thumbnail = null;
                       pw("exception --> TagException");
                       ex.printStackTrace();
                       dbe_action = "EXC";
                   } catch (ReadOnlyFileException ex) {
                       dbe_img_thumbnail = null;
                       pw("exception --> ReadOnlyFileException");
                       ex.printStackTrace();
                       dbe_action = "EXC";
                   } catch (InvalidAudioFrameException ex) {           
                       dbe_img_thumbnail = null;
                       pw("exception --> InvalidAudioFrameException");
                       ex.printStackTrace(); 
                       //dbe_action = "EXC";
                   } catch (NullPointerException ex) {           
                       dbe_img_thumbnail = null;
                       pw("exception --> NullPointerException");
                       ex.printStackTrace(); 
                       //dbe_action = "EXC";
                   } catch (Exception ex) {
                       pw("exception --> other!");
                       pw(ex.getMessage());
                       dbe_img_thumbnail = null;
                       ex.printStackTrace();
                       dbe_action = "EXC";
                   }               
               }  
            
            if (dbe_filetype.toLowerCase().equals("pdf")) {  
                byte[] buffer = null;
                BufferedImage bi = null;
                Image img = null;
                ByteBuffer bb = null;
                PDFFile pdffile = null;
                PDFPage page = null;
                Rectangle rect = null;
                ByteArrayOutputStream baos = null;
                ByteArrayOutputStream tmpOut = null;
                InputStream is = null;
                
                try {
                    tmpOut = new ByteArrayOutputStream();    
                    is =new BufferedInputStream(new
                                       FileInputStream(_file));

                    buffer = new byte[1024*1024];
                    int len;
                    while (true) {
                        len = is.read(buffer);
                        if (len == -1) {
                            break;
                        }
                        tmpOut.write(buffer, 0, len);
                    }
                    is.close();
                    tmpOut.close();
                    buffer = null;

                    bb = ByteBuffer.wrap(tmpOut.toByteArray(), 0, tmpOut.size());

                    pdffile = new PDFFile(bb);
                    page = pdffile.getPage(1);
                    
                    //get the width and height for the doc at the default zoom
                    rect =  new Rectangle(0, 0, (int)page.getBBox().getWidth(), (int)page.getBBox().getHeight());

                    //generate the image         
                    img = page.getImage(rect.width, rect.height, //width &amp; height
                            rect, // clip rect
                            null, // null for the ImageObserver
                            true, // fill background with white
                            true) // block until drawing is done
                    ;

                    bi = (BufferedImage)img;

                    baos = new ByteArrayOutputStream();
                    Thumbnails.of(bi).size(200, 200).outputFormat("jpg").toOutputStream(baos);
                    
                    dbe_img_thumbnail = baos.toByteArray();
                    dbe_img_height = bi.getHeight();
                    dbe_img_width = bi.getWidth();
                    
                    p("Thumbnail was created(PDF). Zzz for: " + _delay);
                    Thread.sleep(_delay);
                } catch (OutOfMemoryError ex) {
                    pw("@@@***@@@ exception --> OutofMemory[pdf]");
                    ex.printStackTrace();
                    dbe_action = "OOM-PDF";
                    dbe_img_thumbnail = null;     
                } catch (PDFParseException e) {
                    pw("@@@***@@@ exception --> PDFParseException[pdf]");
                    e.printStackTrace();
                    Thread.sleep(5000);
                    //dbe_action = "EXC";                    
                } catch (NullPointerException e) {
                    pw("@@@***@@@ exception --> NullPointerException[pdf]");
                    e.printStackTrace();
                    Thread.sleep(5000);
                    //dbe_action = "EXC";                    
                } catch (Exception e) {
                    pw("@@@***@@@ exception --> Exception[pdf]");
                    e.printStackTrace();
                    Thread.sleep(5000);
                    dbe_action = "EXC";
                } finally {
                    p("finally...");
                    buffer = null;
                    if (bi != null) {
                        bi.flush();                       
                        bi = null;
                    }
                    if (img != null) {
                        img.flush();                        
                        img = null;
                    }                    
                    if (baos != null) {
                        baos.flush();
                        baos.close();
                        baos = null;
                    }
                    bb = null;
                    pdffile = null;
                    page = null;
                    rect = null;
                    tmpOut = null;
                    is = null;
                    //System.gc();
                }                
         } //end pdf file
            
        if (dbe_filetype.toLowerCase().equals("doc")) {   
            FileInputStream fis = null;
            HWPFDocument document = null;
            WordExtractor we = null;
            try {
                p("Indexing .doc file: " + _file.getCanonicalPath());
                fis = new FileInputStream(_file);   
                document = new HWPFDocument(fis);            
                we = new WordExtractor(document);

                String[] paragraphText = we.getParagraphText();
                String keywords = "";
                int nkeywords = 0;
                for (String paragraph : paragraphText) {
                    //p(paragraph);
                    paragraph = paragraph.replace("\t", "@");
                    String delim = " !@#$%^&*()-=_+[]\\{}|;':\",./<>?";    
                    //p("fullpath = '" + dbe_absolutepath + "'");
                    StringTokenizer std = new StringTokenizer(paragraph.trim(), delim, true); 
                    //p("paragraph count = " + std.countTokens());
                    if (std.countTokens() <= 10) {
                        //only index paragraphs that have <= 10 words (we can assume these are title/headings)
                        while (std.hasMoreTokens()) {
                            String sToken = std.nextToken();
                            if (sToken.length() >= 3) {
                                //minimum keywords length is 3
                                keywords = keywords + "@" + sToken.toLowerCase(); 
                                nkeywords++;
                            }
                        }
                    }
                }
                p("keywords count = " + nkeywords);
                dbe_keywords = keywords;  
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                dbe_keywords = "";
            } catch (IOException e) {
                e.printStackTrace();
                dbe_keywords = "";
            } catch (Exception e) {
                e.printStackTrace();
                Thread.sleep(5000);
                dbe_action = "EXC";                
            } finally {
                close(fis);
                we = null;
                document = null;
            }
        }
        
        if (dbe_filetype.toLowerCase().equals("docx")) {   
            //office docx keywords indexing    
            FileInputStream fis = null;
            XWPFDocument doc = null;
            XWPFWordExtractor ex = null;
            
            try {
                
                p("Indexing .docx file: " + _file.getCanonicalPath());

                fis = new FileInputStream(_file);   
                doc = new XWPFDocument(fis);
                ex = new XWPFWordExtractor(doc);

                Iterator It = doc.getParagraphsIterator();

                String keywords = "";
                int nkeywords = 0;
                while (It.hasNext()) {
                    XWPFParagraph par = (XWPFParagraph) It.next();            
                    String paragraph = par.getText();               
                    paragraph = paragraph.replace("\t", "@");
                    String delim = " !@#$%^&*()-=_+[]\\{}|;':\",./<>?";    
                    //p("fullpath = '" + dbe_absolutepath + "'");
                    StringTokenizer std = new StringTokenizer(paragraph.trim(), delim, true); 
                    //p("paragraph count = " + std.countTokens());
                    if (std.countTokens() <= 15) {
                        //only index paragraphs that have <= 10 words (we can assume these are title/headings)
                        while (std.hasMoreTokens()) {
                            String sToken = std.nextToken();
                            if (sToken.length() >= 3) {
                                //minimum keywords length is 3
                                keywords = keywords + "@" + sToken.toLowerCase(); 
                                nkeywords++;
                            }
                        }
                    }
                }
                p("keywords count = " + nkeywords);
                dbe_keywords = keywords;    

            } catch (Exception e) {
                e.printStackTrace();
                String exc = e.toString();
                pw("***Exception processing DOCX: " + exc);
                if (exc.contains("InvalidFormatException")) {
                    pw("invalid format!!!!");
                    dbe_keywords = "";
                } else {
                    dbe_action = "EXC";                                              
                }
                Thread.sleep(5000);
            } finally {
                close(fis);
                doc = null;
                ex = null;
            }
        }

             
     } catch (Exception e) {
         e.printStackTrace();
     }
     
     
     
}
     
     private boolean is_photo(String _string) {
        if (
                _string.toLowerCase().contains("jpg") ||
                _string.toLowerCase().contains("png") ||
                _string.toLowerCase().contains("gif") ||
                _string.toLowerCase().contains("jpeg") 
            )
                return true;
    else
        return false;
    }
      
//     private boolean is_photo(String _string) {
//         return false;
//     }

     
     public static boolean copyfile(File _src_file, File _dst_file) {
        try {
            InputStream in = new FileInputStream(_src_file);
            //For Overwrite the file.
            OutputStream out = new FileOutputStream(_dst_file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();            
        } catch (FileNotFoundException ex) {                        
            return false;
        } catch (IOException e) {
            return false;
        }
        return true;
    }
     
     public static byte[] toByteArray(BufferedImage _image, String _format_name, float _compression) throws IOException{
        //-Test precondition
        if (_image == null) return  null;
        
        //-Build buffer
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        //-Get image writer
        Iterator writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = (ImageWriter)writers.next();

        //-Set parameters
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(_compression);
        
        //-Write image
        writer.setOutput(ImageIO.createImageOutputStream( byteArrayOutputStream ));
        writer.write(_image);
        ImageIO.write(_image, _format_name, byteArrayOutputStream);
        byte[] res  = byteArrayOutputStream.toByteArray();
        
        byteArrayOutputStream.flush();
        byteArrayOutputStream.close();
        byteArrayOutputStream = null;
        
        writer.dispose();
        return res;
    }
     
     public static void close(InputStream c) {
        if (c == null) return; 
        try {
            c.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
     }

}
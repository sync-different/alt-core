package com.alterante.utils.extractor;

//import java.awt.*;
import java.io.FileInputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import net.coobird.thumbnailator.Thumbnails;

public class ImageIOtest {
        public static void main(String[] args) {

        System.out.println("Testing ImageIO");
        String _file = "/home/afg/alt/Savannah-Safari-1-768x512.jpg";

        FileInputStream fis = null;
        BufferedImage originalImage = null;
        BufferedImage thumbnail_image = null;
        Thumbnails.Builder b = null;

        int    dbe_img_height;     //image height
        int    dbe_img_width;      //image width
        byte[] dbe_img_thumbnail;  //image thumbnail

        //get the image dimensions
        try {
            fis = new FileInputStream(_file);
            originalImage = ImageIO.read(fis);
            dbe_img_height = originalImage.getHeight();
            dbe_img_width = originalImage.getWidth();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }

}

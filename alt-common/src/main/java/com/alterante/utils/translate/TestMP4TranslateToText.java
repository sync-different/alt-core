package com.alterante.utils.translate;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.LibVosk;

import java.io.FileInputStream;
import java.io.InputStream;

public class TestMP4TranslateToText {

    public static void main(String[] args) throws Exception{

        Model model = new Model("model");
             InputStream ais = new FileInputStream("audio.wav");
             Recognizer recognizer = new Recognizer(model, 16000);
            byte[] buffer = new byte[4096];
            int nbytes;
            while ((nbytes = ais.read(buffer)) >= 0) {
                if (recognizer.acceptWaveForm(buffer, nbytes)) {
                    System.out.println(recognizer.getResult());
                }
            }
            System.out.println(recognizer.getFinalResult());
    }
}

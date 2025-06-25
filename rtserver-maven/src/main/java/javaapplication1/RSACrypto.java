package javaapplication1;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;

/**
 *
 * @author Marce
 */
public class RSACrypto {

    final public static String PUBIC_KEY_PATH = "..\\rtserver\\id_secpu";
    final public static String PRIVATE_KEY_PATH = "..\\rtserver\\id_sec";

    public static void main(String[] args) {
//        generateKeys();
        casoAndroid();
//        casoIOS();
    }

    public static void casoAndroid() {
        try {
            //claves generadas con generateKeys(); sacando los encabezados y los enters
            byte[] encoded = Files.readAllBytes(Paths.get("id_secpu"));
//            String pub = new String(encoded, "UTF8");
//            
//            pub = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDCM+aypG0+d8gUSnNrxsm4yrsWNFtwOZwSRT/D\n" +
//"N933EkqOGT8WZqt/j0J069eQtuIvYrLpScgrdwO6kbVV8bZkiMGj2fBOI/7BjGY3N44m92pXKf/x\n" +
//"hyzWYls1FD+bv9QLQYA0+IY1kMvtdZq58a5ruDGZ0vvMfVverCLuKw5a2QIDAQAB";
            
            String pub = getPublicKey();

            String TextStream = "boxuser=admin&boxpass=valid&passwordkey=62ee059fc497136161bee2fbc2af36d9&iv=adf31e42b773d4c6";
            byte[] Cipher;
            System.out.println("input:\n" + TextStream);

            //este encriptado se debe hacer en Android
            String cipherText = encrypt(TextStream, pub);
                        
            byte[] bytesToDecr = Base64.decode(cipherText);
            

//            encoded = Files.readAllBytes(Paths.get("id_sec"));
//            String priv = new String(encoded, "UTF8");
            String priv = getPrivateKey();

            System.out.println("decrypt:\n" + decrypt(bytesToDecr, priv));
        } catch (IOException ex) {
            Logger.getLogger(RSACrypto.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void casoIOS() {
        try {
            //claves generadas con generateKeys(); sacando los encabezados y los enters
            String priv = "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBALj5sjLS0h1Xyd4LOi9Mga+RXgi45emwRFSE9DvFsHmh4vPUKLs74gxpp2vYrXsfw5ohMl2JPgPM/yhNy/vUvgI6HiC6ygLX521JzFW27luJjPebIS5mZWflUpVmm6rgrZvdJkDozKSsPyd9wyDxzY+22dCKT8fP4TbxTLl6RfnFAgMBAAECgYA1NAyfNagdrHxxk0UfCaBbgTJMy/HFL1/X943QC2Jg+cEvZJx6jhTMgS8Yg/AR8+bs1BOKd8kQisvxKb88JcqomB9FIXf8elOC9yvrvBs4Uj8gS9tnvdgGfiQWP4fDQC16q7mOaCSBNsyJuJ38EV8hcxjMFKpzaSUbujAjaIB2QQJBAN2N3Gj9AbucrDJMCgJCqwWkspu4ukpBIb0GjPd5c95obKLp1yEa4QLXpTO45ApkcqLv3OUyTHPVA+gOBOgQE40CQQDVu/QE6u/3mLff6oNjFz6UKFY7aZmMkUwFqGerKT06z6vGcS/SI6mYikvQadaRJ9Umdkfhiv9KOPM+GogEX5UZAkEAvgbV/Iq9OMCJhV5o20V5UI7RzvSje9rfaDS9Jena8vEX9KeiqDdYtUpm3LUBitRa6tvW2BLLNjKHdjrIBI79ZQJBAJlf3/MreHovwDtRuWkBRCnjbEYEOGjT1z70IziDRmoOnpCvpoZgixJoo5DyckQF1oJcPKbQLPRhO8QkG9kvkfkCQQCw8ryi49aqzFXm096cHOnDAHJqCpN1yFVQOIRjXP22sBgaZ6dtZlxRDipG0wngspmtb+PgyNPCzG0D3uShKKlP";
            String pub = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC4+bIy0tIdV8neCzovTIGvkV4IuOXpsERUhPQ7xbB5oeLz1Ci7O+IMaadr2K17H8OaITJdiT4DzP8oTcv71L4COh4gusoC1+dtScxVtu5biYz3myEuZmVn5VKVZpuq4K2b3SZA6MykrD8nfcMg8c2PttnQik/Hz+E28Uy5ekX5xQIDAQAB";

            byte[] encoded = Files.readAllBytes(Paths.get(PRIVATE_KEY_PATH));
            priv = new String(encoded, "UTF8");
            //generado con ti.rsa en ios
            String enctext = "HlsKqJFPcgsAM3sP59RlpU3lyHa2AOKEILVyG+4lPe5hU0ZVhiHErg/LHKvw5VXcy7ZNWBya3bS4dwbkRqHH2sA1FnxIZAUPLY+BlLzWrGfPYYkCIGGvpWLeq9mMBHYnUUHvNK/JJ9S6Cl3BRUhFDy6GUZwmquTT4cb3RtbfHcE=";

            System.out.println("decrypt:\n" + decrypt(Base64.decode(enctext), priv));
        } catch (IOException ex) {
            Logger.getLogger(RSACrypto.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static String getPublicKey() {
        try {

            byte[] encoded = Files.readAllBytes(Paths.get(PUBIC_KEY_PATH));
            String pub = new String(encoded, "UTF8");

            System.out.println("pubkey:\n" + pub);
            return pub;
        } catch (IOException ex) {
            Logger.getLogger(RSACrypto.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private static String getPrivateKey() {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(PRIVATE_KEY_PATH));
            String priv = new String(encoded, "UTF8");
            return priv;
        } catch (IOException ex) {
            Logger.getLogger(RSACrypto.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;

    }

    public void generateKeys() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(1024);
            KeyPair kp = kpg.genKeyPair();
            Key publicKey = kp.getPublic();
            Key privateKey = kp.getPrivate();

            System.out.println("public " + publicKey.getEncoded());
            System.out.println("private " + privateKey.getEncoded());

            KeyFactory fact = KeyFactory.getInstance("RSA");

            byte[] encoded = kp.getPublic().getEncoded();
            String base64 = Base64.encodeToString(encoded, false);
            PrintStream ps = new PrintStream(new FileOutputStream(PUBIC_KEY_PATH));
            ps.println(base64);
            ps.close();

            encoded = kp.getPrivate().getEncoded();
            
            base64 = Base64.encodeToString(encoded, false);
            ps = new PrintStream(new FileOutputStream(PRIVATE_KEY_PATH));
            ps.println(base64);
            ps.close();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(RSACrypto.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(RSACrypto.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static String encrypt(String Buffer, String publicKey) {
        try {       	
            System.out.println("Buffer=" + Buffer);
            System.out.println("publicKey=" + publicKey);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            byte[] decodedValue = Base64.decode(publicKey);

            X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(decodedValue);
            Key encryptionKey = keyFactory.generatePublic(pubSpec);

            Cipher rsa;
            rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsa.init(Cipher.ENCRYPT_MODE, encryptionKey);
            byte[] bin = rsa.doFinal(Buffer.getBytes("UTF-8"));
            return Base64.encodeToString(bin, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String decrypt(String enctext) {
        String priv = getPrivateKey();
        String decryptedText = decrypt(Base64.decode(enctext), priv);
        System.out.println("decrypt:\n" + decryptedText);
        return decryptedText;
    }

    private static String decrypt(byte[] buffer, String privateKey) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(Base64.decode(privateKey));
            Key decryptionKey = keyFactory.generatePrivate(privSpec);

            //X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(Base64.decodeBase64(PUB));
            //Key decryptionKey = keyFactory.generatePublic(pubSpec);
            Cipher rsa;
            rsa = Cipher.getInstance("RSA");
            rsa.init(Cipher.DECRYPT_MODE, decryptionKey);
            byte[] utf8 = rsa.doFinal(buffer);
            return new String(utf8, "UTF8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    	public String generateRandomIV() {
            try {
                 CryptLib _crypt = new CryptLib(0);
                 String iv = CryptLib.generateRandomIV(16); //16 bytes = 128 bit
                 System.out.println("iv=" + iv);
                 return iv;
                 } catch (Exception e) {
                         e.printStackTrace();
                 }
            return null;
        }      

}

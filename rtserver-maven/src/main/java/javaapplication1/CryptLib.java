package javaapplication1;



import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import javax.crypto.CipherOutputStream;
import org.apache.commons.codec.binary.Base64;

	/*****************************************************************
	 * CrossPlatform CryptLib
	 * 
	 * <p>
	 * This cross platform CryptLib uses AES 256 for encryption. This library can
	 * be used for encryptoion and de-cryption of string on iOS, Android and Windows
	 * platform.<br/>
	 * Features: <br/>
	 * 1. 256 bit AES encryption
	 * 2. Random IV generation. 
	 * 3. Provision for SHA256 hashing of key. 
	 * </p>
	 * 
	 * @since 1.0
	 * @author navneet
	 *****************************************************************/

public class CryptLib {

	/**
	 * Encryption mode enumeration
	 */
	private enum EncryptMode {
		ENCRYPT, DECRYPT;
	}

	// cipher to be used for encryption and decryption
	Cipher _cx;

	// encryption key and initialization vector
	byte[] _key, _iv;

	public CryptLib(int keysize) throws NoSuchAlgorithmException, NoSuchPaddingException {
		// initialize the cipher with transformation AES/CBC/PKCS5Padding
		_cx = Cipher.getInstance("AES/CBC/PKCS5Padding");
		_key = new byte[keysize/8]; //128 bit key space
		_iv = new byte[16]; //128 bit IV
	}

	/**
	 * Note: This function is no longer used. 
	 * This function generates md5 hash of the input string
	 * @param inputString
	 * @return md5 hash of the input string
	 */
	public static final String md5(final String inputString) {
	    final String MD5 = "MD5";
	    try {
	        // Create MD5 Hash
	        MessageDigest digest = java.security.MessageDigest
	                .getInstance(MD5);
	        digest.update(inputString.getBytes());
	        byte messageDigest[] = digest.digest();

	        // Create Hex String
	        StringBuilder hexString = new StringBuilder();
	        for (byte aMessageDigest : messageDigest) {
	            String h = Integer.toHexString(0xFF & aMessageDigest);
	            while (h.length() < 2)
	                h = "0" + h;
	            hexString.append(h);
	        }
	        return hexString.toString();

	    } catch (NoSuchAlgorithmException e) {
	        e.printStackTrace();
	    }
	    return "";
	}

	/**
	 * 
	 * @param _inputText
	 *            Text to be encrypted or decrypted
	 * @param _encryptionKey
	 *            Encryption key to used for encryption / decryption
	 * @param _mode
	 *            specify the mode encryption / decryption
	 * @param _initVector
	 * 	      Initialization vector
	 * @return encrypted or decrypted string based on the mode
	 * @throws UnsupportedEncodingException
	 * @throws InvalidKeyException
	 * @throws InvalidAlgorithmParameterException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 */
	 private String encryptDecrypt(String _inputText, String _encryptionKey,
			EncryptMode _mode, String _initVector) throws UnsupportedEncodingException,
			InvalidKeyException, InvalidAlgorithmParameterException,
			IllegalBlockSizeException, BadPaddingException {
		String _out = "";// output string
		//_encryptionKey = md5(_encryptionKey);
		//System.out.println("key="+_encryptionKey);

		int len = _encryptionKey.getBytes("UTF-8").length; // length of the key	provided

		if (_encryptionKey.getBytes("UTF-8").length > _key.length)
			len = _key.length;

		int ivlen = _initVector.getBytes("UTF-8").length;

		if(_initVector.getBytes("UTF-8").length > _iv.length)
			ivlen = _iv.length;

		System.arraycopy(_encryptionKey.getBytes("UTF-8"), 0, _key, 0, len);
		System.arraycopy(_initVector.getBytes("UTF-8"), 0, _iv, 0, ivlen);
		//KeyGenerator _keyGen = KeyGenerator.getInstance("AES");
		//_keyGen.init(128);

		SecretKeySpec keySpec = new SecretKeySpec(_key, "AES"); // Create a new SecretKeySpec
									// for the
									// specified key
									// data and
									// algorithm
									// name.

		IvParameterSpec ivSpec = new IvParameterSpec(_iv); // Create a new
								// IvParameterSpec
								// instance with the
								// bytes from the
								// specified buffer
								// iv used as
								// initialization
								// vector.

		// encryption
		if (_mode.equals(EncryptMode.ENCRYPT)) {
			// Potentially insecure random numbers on Android 4.3 and older.
			// Read
			// https://android-developers.blogspot.com/2013/08/some-securerandom-thoughts.html
			// for more info.
			_cx.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);// Initialize this cipher instance
			byte[] results = _cx.doFinal(_inputText.getBytes("UTF-8")); // Finish
										// multi-part
										// transformation
										// (encryption)
			_out = Base64.encodeBase64String(results); // ciphertext
										// output
		}

		// decryption
		if (_mode.equals(EncryptMode.DECRYPT)) {
			_cx.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);// Initialize this ipher instance

			byte[] decodedValue = Base64.decodeBase64(_inputText.getBytes());
			byte[] decryptedVal = _cx.doFinal(decodedValue); // Finish
									// multi-part
									// transformation
									// (decryption)
			_out = new String(decryptedVal);
		}
		//System.out.println(_out);
		return _out; // return encrypted/decrypted string
	}

	
        private void encryptDecryptFile(String _inputPath, String _outputPath, String _encryptionKey,
			EncryptMode _mode, String _initVector) throws UnsupportedEncodingException,
			InvalidKeyException, InvalidAlgorithmParameterException,
			IllegalBlockSizeException, BadPaddingException {
            InputStream _from = null;
            try {

                int len = _encryptionKey.getBytes("UTF-8").length; // length of the key	provided
                
                //_encryptionKey = "0000000000000000";
                //_initVector    = "0000000000000000";
                
                System.out.println("_encryptionkey: '" + _encryptionKey + "'");
                System.out.println("Len of key provided: " + len);
                System.out.println("_key length        : '" + _key.length + "'");
                
                               
                if (_encryptionKey.getBytes("UTF-8").length > _key.length)
                    len = _key.length;

                int ivlen = _initVector.getBytes("UTF-8").length;
                
                System.out.println("iv: '" + _initVector + "'");
                System.out.println("Len of iv provided: " + ivlen);

                if(_initVector.getBytes("UTF-8").length > _iv.length)
                    ivlen = _iv.length;
                System.arraycopy(_encryptionKey.getBytes("UTF-8"), 0, _key, 0, len);
                System.arraycopy(_initVector.getBytes("UTF-8"), 0, _iv, 0, ivlen);

                SecretKeySpec keySpec = new SecretKeySpec(_key, "AES");
                IvParameterSpec ivSpec = new IvParameterSpec(_iv);

                OutputStream _to = null;
                _from = new FileInputStream(_inputPath);
                _to = new FileOutputStream(_outputPath);
                // encryption
                if (_mode.equals(EncryptMode.ENCRYPT)) {
                    _cx.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
                    CipherOutputStream os = new CipherOutputStream(_to, _cx);
                    
                    copy(_from, os);
                    os.close();
                }
                // decryption
                if (_mode.equals(EncryptMode.DECRYPT)) {
                    _cx.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);// Initialize this ipher instance
                    CipherOutputStream os = new CipherOutputStream(_to, _cx);
                    
                    copy(_from, os);
                    os.close();
  
                }
            } catch (FileNotFoundException ex) {
                ex.printStackTrace();
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                try {
                    _from.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
	}

        public static void copy(InputStream is, OutputStream os) throws IOException {
	    int i;
	    byte[] b = new byte[1024];
	    while((i=is.read(b))!=-1) {
	      os.write(b, 0, i);
	    }
	}
	        
                
                
        /***
	 * This function computes the SHA256 hash of input string
	 * @param text input text whose SHA256 hash has to be computed
	 * @param keysize length of the text to be returned
	 * @return returns SHA256 hash of input text 
	 * @throws NoSuchAlgorithmException
	 * @throws UnsupportedEncodingException
	 */
	public static String SHA256 (String text, int keysize) throws NoSuchAlgorithmException, UnsupportedEncodingException {
            int length = keysize/8;

            System.out.println("Keysize for SHA256: '" + keysize + "'");
            System.out.println("Length for SHA256: '" + length + "'");

            String resultStr;
            MessageDigest md = MessageDigest.getInstance("SHA-256");

	    md.update(text.getBytes("UTF-8"));
	    byte[] digest = md.digest();

	    StringBuffer result = new StringBuffer();
	    for (byte b : digest) {
	        result.append(String.format("%02x", b)); //convert to hex
	    }
            
            System.out.println("Result SHA256: '" + result + "'");
	    //return result.toString();

	    if(length > result.toString().length())
	    {
	    	resultStr = result.toString();
	    }
	    else 
	    {
	    	resultStr = result.toString().substring(0, length);
	    }

	    return resultStr;

	}

	/***
	 * This function encrypts the plain text to cipher text using the key
	 * provided. You'll have to use the same key for decryption
	 * 
	 * @param _plainText
	 *            Plain text to be encrypted
	 * @param _key
	 *            Encryption Key. You'll have to use the same key for decryption
	 * @param _iv
	 * 	    initialization Vector
	 * @return returns encrypted (cipher) text
	 * @throws InvalidKeyException
	 * @throws UnsupportedEncodingException
	 * @throws InvalidAlgorithmParameterException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 */

	public String encrypt(String _plainText, String _key, String _iv)
			throws InvalidKeyException, UnsupportedEncodingException,
			InvalidAlgorithmParameterException, IllegalBlockSizeException,
			BadPaddingException {
		return encryptDecrypt(_plainText, _key, EncryptMode.ENCRYPT, _iv);
	}
        
            public void encryptFile(String _inputPath, String _outputPath, String _key, String _iv)
			throws InvalidKeyException, UnsupportedEncodingException,
			InvalidAlgorithmParameterException, IllegalBlockSizeException,
			BadPaddingException {
		encryptDecryptFile(_inputPath, _outputPath, _key, EncryptMode.ENCRYPT, _iv);
	}

	/***
	 * This funtion decrypts the encrypted text to plain text using the key
	 * provided. You'll have to use the same key which you used during
	 * encryprtion
	 * 
	 * @param _encryptedText
	 *            Encrypted/Cipher text to be decrypted
	 * @param _key
	 *            Encryption key which you used during encryption
	 * @param _iv
	 * 	    initialization Vector
	 * @return encrypted value
	 * @throws InvalidKeyException
	 * @throws UnsupportedEncodingException
	 * @throws InvalidAlgorithmParameterException
	 * @throws IllegalBlockSizeException
	 * @throws BadPaddingException
	 */
	public String decrypt(String _encryptedText, String _key, String _iv)
			throws InvalidKeyException, UnsupportedEncodingException,
			InvalidAlgorithmParameterException, IllegalBlockSizeException,
			BadPaddingException {
		return encryptDecrypt(_encryptedText, _key, EncryptMode.DECRYPT, _iv);
	}
        
        public void decryptFile(String _inputPath, String _outputPath, String _key, String _iv)
			throws InvalidKeyException, UnsupportedEncodingException,
			InvalidAlgorithmParameterException, IllegalBlockSizeException,
			BadPaddingException {
		encryptDecryptFile(_inputPath, _outputPath, _key, EncryptMode.DECRYPT, _iv);
	}

	/**
 	* this function generates random string for given length
 	* @param length
 	* 				Desired length
 	* * @return 
 	*/
	public static String generateRandomIV(int length)
	{
		SecureRandom ranGen = new SecureRandom();
		byte[] aesKey = new byte[16];
		ranGen.nextBytes(aesKey);
		StringBuffer result = new StringBuffer();
	    for (byte b : aesKey) {
	        result.append(String.format("%02x", b)); //convert to hex
	    }
	    if(length> result.toString().length())
	    {
	    	return result.toString();
	    }
	    else
	    {
	    	return result.toString().substring(0, length);
	    }
	}
}
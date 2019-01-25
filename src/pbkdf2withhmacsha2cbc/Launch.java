/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pbkdf2withhmacsha2cbc;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.security.GeneralSecurityException;
import java.util.Scanner;
import pbkdf2withhmacsha2cbc.CryptoInstance.*;
/**
 *
 * @author Illestar
 */

public class Launch {
    
    private static Algorithm getEncryptionAlgorithm() {
        int alg = 0;
        try {
            Scanner sc = new Scanner(System.in);
            if (sc.hasNextInt()) {
                alg = sc.nextInt();
            }
            if (alg == 1) {
                // 3DES
                return Algorithm.DESede;
            } else if (alg == 2) {
                // AES
                return Algorithm.AES;
            } else {
                System.err.println("Invalid number " + alg + " for algorithm option");
                return getEncryptionAlgorithm();
            }
        } catch (Exception e) {
            System.err.println("Invalid format" + e);
            return null;
        }
    }
    
    private static KeyLength getEncryptionKeyLen() {
        int keylen = 0;
        try {
            Scanner sc = new Scanner(System.in);
            if (sc.hasNextInt()) {
                keylen = sc.nextInt();
            }
            switch(keylen) {
                case 1:
                    return KeyLength.BITS_64;
                case 2:
                    return KeyLength.BITS_128;
                case 3:
                    return KeyLength.BITS_192;
                case 4:
                    return KeyLength.BITS_256;
                default:
                    System.err.println("Invalid number " + keylen + " for key length option");
                    return getEncryptionKeyLen();
            }
        } catch (Exception e) {
            System.err.println("Invalid format" + e);
            return null;
        }
    }
    
    private static int getHMACAlgorithm() {
        int hmac = 0;
        try {
            Scanner sc = new Scanner(System.in);
            if (sc.hasNextInt()) {
                hmac = sc.nextInt();
            }
            if (hmac == 1 || hmac == 2)
                return hmac;
            else {
                System.err.println("Invalid number " + hmac + " for HMAC option");
                return getHMACAlgorithm();
            }
        } catch (Exception e) {
            System.err.println("Invalid format" + e);
            return 1;
        }
    }
    
    private static int getIteration() {
        int i = 1;
        try {
            Scanner sc = new Scanner(System.in);
            if (sc.hasNextInt()) {
                i = sc.nextInt();
            }
            if (i > 0)
                return i;
            else {
                System.err.println("Invalid number " + i + " for iteration (must > 0)");
                return getIteration();
            }
        } catch (Exception e) {
            System.err.println("Invalid format" + e);
            return 1;
        }
    }
    
    private static File getFileInstance() {
        File f;
        String fname = "";
        try {
            Scanner sc = new Scanner(System.in);
            if (sc.hasNextLine()) {
                fname = sc.nextLine();
            }
            Path p = Paths.get(System.getProperty("user.home"),"javaenc", fname);
            f = p.toFile();
            return f;
        } catch (Exception e) {
            System.err.println("Exception occured: " + e);
            return getFileInstance();
        }
    }
    
    public static void main(String args[]) throws GeneralSecurityException, IOException
    {
        // Init
        int hmac, iv = 0, iterate, datatype;
        Algorithm alg;
        KeyLength keylen;
        Pbkdf pbkdf = null;
        MacAlgorithm macalg = null;
        String pwd = "password";
        File fin, fout;
        
        
        Scanner sc = new Scanner(System.in);
        
        // Show menu
        System.out.println("============= Encryption Module =============");
        System.out.println("Select the algorithm for encryption:");
        System.out.println("[1] 3DES [2] AES");
        alg = getEncryptionAlgorithm();
        if (alg == Algorithm.DESede)
            iv = 8;
        else if (alg == Algorithm.AES)
            iv = 16;
        System.out.println("Specify the key length for encryption algorithm:");
        System.out.println("[1] 64 [2] 128 [3] 192 [4] 256");
        keylen = getEncryptionKeyLen();
        System.out.println("Select the HMAC algorithm:");
        System.out.println("[1] SHA256 [2] SHA512");
        hmac = getHMACAlgorithm();
        if (hmac == 1) {
            pbkdf = Pbkdf.PBKDF_2_WITH_HMAC_SHA_256;
            macalg = MacAlgorithm.HMAC_SHA_256;
        } else if (hmac == 2) {
            pbkdf = Pbkdf.PBKDF_2_WITH_HMAC_SHA_512;
            macalg = MacAlgorithm.HMAC_SHA_512;
        }
        System.out.println("Assign number of iterations:");
        iterate = getIteration();
        System.out.println("Provide a password:");
        if (sc.hasNextLine())
            pwd = sc.nextLine();
        //System.out.println("Select type of plaintext: ");
        //System.out.println("[1] Byte String [2] File");
        //datatype = getDataType();
        System.out.println("Please put file under (user home directory)/javaenc in Linux or C:\\(user directory)\\javaenc in Windows.");
        System.out.println("Specify plaintext filename: ");
        fin = getFileInstance();
        System.out.println("Specify output filename: ");
        fout = getFileInstance();
        
        /* test code for decryption */
        Path p = Paths.get(System.getProperty("user.home"),"javaenc", "in_rev.txt");
        File fin2 = p.toFile();
        
        CryptoLib cl = new CryptoLib(new CryptoInstance(alg, Mode.CBC, Padding.PKCS5_PADDING, keylen, pbkdf, macalg, iv, iterate));

        cl.encrypt(fin, fout, pwd.toCharArray());
        
        /* test code for decryption */
        cl.decrypt(fout, fin2, pwd.toCharArray());
        
        System.out.println("Finished");
        
        /*
        // Decrypt
        UserDefinedFileAttributeView view = Files.getFileAttributeView(fin.toPath(), UserDefinedFileAttributeView.class);
        int size;
        String[] conf = {"alg", "keylen", "pbkdf", "macalg", "iv", "iterate"};
        for (String c : conf){
            size = view.size(c);
            ByteBuffer buf = ByteBuffer.allocateDirect(size);
            view.read(c, buf);
            buf.flip();
            switch(c){
                case "alg":
                        Charset.defaultCharset().decode(buf).toString();
            }
        }
        */
    }
    
}



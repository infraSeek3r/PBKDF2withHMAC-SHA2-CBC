
package pbkdf2withhmacsha2cbc;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;


/**
 * The main library for cryptography processes based on the configuration class CryptoInstance. 
 */
public class CryptoLib {
    private final CryptoInstance config;
    private final Cipher cipher;

    @SuppressWarnings("WeakerAccess")
    public CryptoLib(CryptoInstance config) {
        // Context validation
        if (config == null)
            throw new IllegalArgumentException("Context is null");
        else if (config.getAlgorithm() == null)
            throw new IllegalArgumentException("Algorithm is null");
        else if (config.getMode() == null)
            throw new IllegalArgumentException("Mode is null");
        else if (config.getPadding() == null)
            throw new IllegalArgumentException("Padding is null");
        else if (config.getKeyLength() == null)
            throw new IllegalArgumentException("Key length is null");
        else if (config.getPbkdf() == null)
            throw new IllegalArgumentException("PBKDF type is null");
        else if (config.getMacAlgorithm() == null)
            throw new IllegalArgumentException("Mac algorithm is null");
        

        // Algorithm/mode specific validation
        switch (config.getAlgorithm()) {
            case AES:
                switch (config.getMode()) {
                    case CBC:
                        if (config.getIvLength() != 16) {
                            throw new IllegalArgumentException("AES-CBC is selected but the IV length is not 16");
                        }
                        break;
                }
                break;
            case DESede:
                if (config.getIvLength() != 8) {
                    throw new IllegalArgumentException("3DES algorithm is selected but the IV length is not 8 " +
                            "(" + config.getIvLength() + ")");
                }
                break;
        }

        // PBKDF iterations validation
        switch (config.getPbkdf()) {
            case PBKDF_2_WITH_HMAC_SHA_256:
            case PBKDF_2_WITH_HMAC_SHA_512:
                if (config.getIterations() <= 0) {
                    throw new IllegalArgumentException("PBKDF is selected, but the number of iterations is invalid");
                }
                break;
        }

        this.config = config;

        try {
            cipher = Cipher.getInstance(config.getAlgorithm() + "/" + config.getMode() + "/" + config.getPadding());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Generates an AES or 3DES key from System. (not used)
     *
     * @param algorithm                 the key will be used with
     * @param keyLength                 length of key
     * @return a byte array
     * @throws GeneralSecurityException if either initialization or generation fails
     */
    @SuppressWarnings("WeakerAccess")
    public static byte[] generateKey(CryptoInstance.Algorithm algorithm, CryptoInstance.KeyLength keyLength)
            throws GeneralSecurityException {
        if (algorithm == null || keyLength == null) {
            throw new IllegalArgumentException("Algorithm or key length is null");
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm.toString());

        int actualKeyLength = keyLength.bits();

        keyGenerator.init(actualKeyLength);

        return keyGenerator.generateKey().getEncoded();
    }

    /**
     * simple char-to-byte conversion. (not used)
     */
    private static byte[] toBytes(char[] chars) {
        byte[] bytes = new byte[chars.length];

        for (int i = 0; i < chars.length; i++) {
            bytes[i] = (byte) chars[i];
        }
        return bytes;
    }

    /**
     * Initialize a javax.crypto.Mac instance
     *
     * @param macAlgorithm 
     * @param password      password for deriving key
     * @return an initialized javax.crypto.Mac
     * @throws GeneralSecurityException if MAC initialization fails
     */
    private Mac getMac(CryptoInstance.MacAlgorithm macAlgorithm, char[] password) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(macAlgorithm.toString());
        
        // generate the initialization vector
        byte[] initializationVector = generateInitializationVector();
        
        mac.init(deriveKey(password, initializationVector));
        // mac.init(new SecretKeySpec(toBytes(password), macAlgorithm.toString()));

        return mac;
    }

    /**
     * Encrypts the input file using the supplied password
     *
     * @param input     the input file
     * @param output    the output file
     * @param password  the password
     * @throws GeneralSecurityException if initialization or encryption fails
     * @throws IOException              if there's a failure to read/write from/to the input/output file
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized void encrypt(File input, File output, char[] password)
            throws GeneralSecurityException, IOException {
        if (input == null || !input.exists() || input.length() <= 0) {
            throw new IllegalArgumentException("Input file is either null or does not exist");
        }

        if (output == null) {
            throw new IllegalArgumentException("Output file is null");
        }

        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password is either null or empty");
        }

        BufferedInputStream bufferedInputStream = null;
        BufferedOutputStream bufferedOutputStream = null;

        try {
            // generate the initialization vector
            byte[] initializationVector = generateInitializationVector();

            // initialize the cipher
            cipher.init(Cipher.ENCRYPT_MODE,
                    deriveKey(password, initializationVector),
                    getAlgorithmParameterSpec(config.getMode(), initializationVector));

            // initialize the mac
            Mac mac = null;

            if (config.getMacAlgorithm() != CryptoInstance.MacAlgorithm.NONE) {
                mac = getMac(config.getMacAlgorithm(), password);
            }

            // allocate variables
            int bytesRead;
            byte[] encryptedBytes;
            byte[] inputStreamBuffer = new byte[4096];

            // setup streams
            bufferedInputStream = new BufferedInputStream(new FileInputStream(input));
            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(output));

            // write the header (encryption alg || keylen || hmackeylen || iteration), control length = 16
            String s = "";
            s += config.getAlgorithm().toString().charAt(0);
            s += String.format("%03d", config.getKeyLength().bits());
            s += config.getMacAlgorithm().toString().substring(config.getMacAlgorithm().toString().length() - 3);
            s += String.format("%09d", config.getIterations());
            
            //test: display header
            System.out.println(s.getBytes(StandardCharsets.UTF_8));
            
            bufferedOutputStream.write(s.getBytes(StandardCharsets.UTF_8));
            
            // write the initialization vector
            bufferedOutputStream.write(initializationVector);

            while ((bytesRead = bufferedInputStream.read(inputStreamBuffer)) > 0) {
                // encrypt
                encryptedBytes = cipher.update(inputStreamBuffer, 0, bytesRead);

                bufferedOutputStream.write(encryptedBytes);

                // compute the mac
                if (mac != null) {
                    mac.update(encryptedBytes);
                }
            }

            // finalize and write the cipher
            byte[] finaleEncryptedBytes = cipher.doFinal();

            bufferedOutputStream.write(finaleEncryptedBytes);

            // write the mac
            if (mac != null) {
                bufferedOutputStream.write(mac.doFinal(finaleEncryptedBytes));
            }
            
            /* deprecated due to metadata not portable when upload/download
            
            // set metadata
            FileStore store = Files.getFileStore(output.toPath());
            if (!store.supportsFileAttributeView(UserDefinedFileAttributeView.class))
                System.err.format("UserDefinedFileAttributeView not supported on %s\n", store);
        
            UserDefinedFileAttributeView view = Files.getFileAttributeView(output.toPath(), UserDefinedFileAttributeView.class);
 
            view.write("alg", Charset.defaultCharset().encode(config.getAlgorithm().toString()));
            view.write("keylen", Charset.defaultCharset().encode(config.getKeyLength().toString()));
            view.write("pbkdf", Charset.defaultCharset().encode(config.getPbkdf().toString()));
            view.write("macalg", Charset.defaultCharset().encode(config.getMacAlgorithm().toString()));
            view.write("iv", Charset.defaultCharset().encode(String.valueOf(config.getIvLength())));
            view.write("iterate", Charset.defaultCharset().encode(String.valueOf(config.getIterations())));
            
            */
            
        } finally {
            closeStream(bufferedInputStream);
            closeStream(bufferedOutputStream);
        }
    }

    /**
     * Decrypts an input file using the supplied password
     *
     * @param input     the input file
     * @param output    the output file
     * @param password  the password
     * @throws GeneralSecurityException if initialization or decryption fails
     * @throws IOException              if there's a failure to read/write from/to the input/output file
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized void decrypt(File input, File output, char[] password)
            throws GeneralSecurityException, IOException {
        // recheck just in case
        if (input == null || !input.exists() || input.length() <= 0) {
            throw new IllegalArgumentException("Input file is either null or does not exist");
        }

        if (output == null) {
            throw new IllegalArgumentException("Output file is null");
        }

        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password is either null or empty");
        }

        BufferedInputStream bufferedInputStream = null;
        BufferedOutputStream bufferedOutputStream = null;

        try {
            // setup streams
            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(output));
            bufferedInputStream = new BufferedInputStream(new FileInputStream(input));
            
            // discard header
            byte[] headerBytes = new byte[16];

            int headerBytesRead = bufferedInputStream.read(headerBytes);

            if (headerBytesRead < 16) {
                throw new IOException("File doesn't contain information for decryption");
            }
            
            // read the mac given in file
            Mac mac = null;
            byte[] recMac = null;
            
            if (config.getMacAlgorithm() != CryptoInstance.MacAlgorithm.NONE) {
                mac = getMac(config.getMacAlgorithm(), password);

                recMac = new byte[mac.getMacLength()];

                RandomAccessFile randomAccessFile = new RandomAccessFile(input, "r");

                if (randomAccessFile.length() - mac.getMacLength() <= 0) {
                    throw new IOException("File does not contain sufficient data for decryption");
                }
                
                randomAccessFile.seek(randomAccessFile.length() - mac.getMacLength());
                randomAccessFile.read(recMac);

                randomAccessFile.close();
            }
            
            // read the initialization vector
            byte[] initializationVector = new byte[config.getIvLength()];

            int ivBytesRead = bufferedInputStream.read(initializationVector);

            if (ivBytesRead < config.getIvLength()) {
                throw new IOException("File doesn't contain an IV");
            }

            // initialize the cipher
            cipher.init(Cipher.DECRYPT_MODE,
                    deriveKey(password, initializationVector),
                    getAlgorithmParameterSpec(config.getMode(), initializationVector));

            // allocate loop buffers and variables
            int bytesRead;
            long numBytesToProcess;
            byte[] inputStreamBuffer = new byte[4096];
            long bytesLeft = input.length() - 16 - config.getIvLength(); // subtract header and iv length

            // subtract the mac length 
            if (mac != null) {
                bytesLeft -= mac.getMacLength();
            }

            // set up marker in order to revisit later for decryption
            long bytesLeft_pre = bytesLeft;
            if (bufferedInputStream.markSupported()) {
                // readLimit in mark is just a suggested value, drops only when out of space in buffer
                bufferedInputStream.mark((int) bytesLeft);
            } else {
                throw new IOException("Unsupported stream type");
            }
            
            // calculate mac from given file
            while ((bytesRead = bufferedInputStream.read(inputStreamBuffer)) > 0) {

                numBytesToProcess = (bytesRead < bytesLeft_pre) ? bytesRead : bytesLeft_pre;

                // prevent exception
                if (numBytesToProcess <= 0) {
                    break;
                }
                
                //test: display buffer
                System.out.println("read_for_mac: " + inputStreamBuffer.toString());
                
                // reduce the number of bytes left
                bytesLeft_pre -= numBytesToProcess;

                // compute the mac 
                if (mac != null) {
                    // overflow should not occur. protect with safe conversion just in case
                    mac.update(inputStreamBuffer, 0, Math.toIntExact(numBytesToProcess));
                }
            }
            
            // compare the mac using java.security.MessageDigest.isEqual
            // Require versions later than Java SE 6 Update 17, prior versions are not time-constant and may subject to timing attack
            if (mac != null && !MessageDigest.isEqual(recMac, mac.doFinal())) {
                
                throw new GeneralSecurityException("Received mac is different from calculated");
                
            } else {
            
                bufferedInputStream.reset();
                
                // decrypt
                while ((bytesRead = bufferedInputStream.read(inputStreamBuffer)) > 0) {
                    numBytesToProcess = (bytesRead < bytesLeft) ? bytesRead : bytesLeft;

                    // prevent exception
                    if (numBytesToProcess <= 0) {
                        break;
                    }
                    
                    // overflow should not occur. protect with safe conversion just in case
                    bufferedOutputStream.write(cipher.update(inputStreamBuffer, 0, Math.toIntExact(numBytesToProcess)));

                    //test: display buffer
                    System.out.println("read_for_decrypt: " + inputStreamBuffer.toString());

                    // reduce the number of bytes left
                    bytesLeft -= numBytesToProcess;
                }

                // finalize the cipher
                byte[] finalDecBytes = cipher.doFinal();

                bufferedOutputStream.write(finalDecBytes);
            }

        } finally {
            closeStream(bufferedInputStream);
            closeStream(bufferedOutputStream);
        }
    }

    /**
     * Derives an AES (javax.crypto.spec.SecretKeySpec) using a password and iteration count.
     *
     * @param password              the password
     * @param initializationVector  used for PBKDF
     * @return an AES (javax.crypto.spec.SecretKeySpec)
     * @throws GeneralSecurityException if initialization, decryption, or the MAC comparison fails
     */
    private SecretKey deriveKey(char[] password, byte[] initializationVector) throws GeneralSecurityException {
        byte[] key = null;

        key = derivePbkdfKeyBytes(password, generateSalt());

        return new SecretKeySpec(key, config.getAlgorithm().toString());
    }

    private byte[] derivePbkdfKeyBytes(char[] password, byte[] salt)
            throws InvalidKeySpecException, NoSuchAlgorithmException {
        return SecretKeyFactory.getInstance(config.getPbkdf().toString())
                .generateSecret(
                        new PBEKeySpec(
                                password,
                                salt,
                                config.getIterations(),
                                config.getKeyLength().bits()))
                .getEncoded();
    }

    /**
     * Specifies an initialization vector for ciphers under feedback mode
     *
     * @return IvParameterSpec extending AlgorithmParameterSpec 
     * @throws IllegalArgumentException         On bad or unsupported mode settings
     */
    private AlgorithmParameterSpec getAlgorithmParameterSpec(CryptoInstance.Mode mode, byte[] initializationVector) {
        switch (mode) {
            case CBC:
                return new IvParameterSpec(initializationVector);
        }

        throw new IllegalArgumentException("Unknown mode");
    }

    /**
     * Generates an initialization vector using java.security.SecureRandom as RNG
     *
     * @return a byte array
     */
    private byte[] generateInitializationVector() {
        byte[] initializationVector = new byte[config.getIvLength()];

        new SecureRandom().nextBytes(initializationVector);

        return initializationVector;
    }
    
    /**
     * Generates a salt with a constant length of 256 bits using java.security.SecureRandom as RNG
     *
     * @return a byte array
     */
    private byte[] generateSalt() {
        byte[] salt = new byte[32];

        new SecureRandom().nextBytes(salt);

        return salt;
    }

    /**
     * Close streams after usage.
     */
    private void closeStream(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }
}

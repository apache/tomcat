/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.tribes.group.interceptors;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * Adds encryption using a pre-shared key.
 *
 * The length of the key (in bytes) must be acceptable for the encryption
 * algorithm being used. For example, for AES, you must use a key of either
 * 16 bytes (128 bits, 24 bytes 192 bits), or 32 bytes (256 bits).
 *
 * You can supply the raw key bytes by calling {@link #setEncryptionKey(byte[])}
 * or the hex-encoded binary bytes by calling
 * {@link #setEncryptionKey(String)}.
 */
public class EncryptInterceptor extends ChannelInterceptorBase implements EncryptInterceptorMBean {

    private static final Log log = LogFactory.getLog(EncryptInterceptor.class);
    protected static final StringManager sm = StringManager.getManager(EncryptInterceptor.class);

    private static final String DEFAULT_ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";

    private String providerName;
    private String encryptionAlgorithm = DEFAULT_ENCRYPTION_ALGORITHM;
    private byte[] encryptionKeyBytes;

    private Cipher encryptionCipher;
    private Cipher decryptionCipher;

    public EncryptInterceptor() {
    }

    @Override
    public void start(int svc) throws ChannelException {
        if(Channel.SND_TX_SEQ == (svc & Channel.SND_TX_SEQ)) {
            try {
                initCiphers();
            } catch (GeneralSecurityException gse) {
                log.fatal(sm.getString("encryptInterceptor.init.failed"));
                throw new ChannelException(sm.getString("encryptInterceptor.init.failed"), gse);
            }
        }

        super.start(svc);
    }

    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
            throws ChannelException {
        try {
            byte[] data = msg.getMessage().getBytes();

            // See #encrypt(byte[]) for an explanation of the return value
            byte[][] bytes = encrypt(data);

            XByteBuffer xbb = msg.getMessage();

            // Completely replace the message
            xbb.setLength(0);
            xbb.append(bytes[0], 0, bytes[0].length);
            xbb.append(bytes[1], 0, bytes[1].length);

            super.sendMessage(destination, msg, payload);

        } catch (IllegalBlockSizeException ibse) {
            log.error(sm.getString("encryptInterceptor.encrypt.failed"));
            throw new ChannelException(ibse);
        } catch (BadPaddingException bpe) {
            log.error(sm.getString("encryptInterceptor.encrypt.failed"));
            throw new ChannelException(bpe);
        }
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        try {
            byte[] data = msg.getMessage().getBytes();

            data = decrypt(data);

            // Remove the decrypted IV/nonce block from the front of the message
            int blockSize = getDecryptionCipher().getBlockSize();
            int trimmedSize = data.length - blockSize;
            if(trimmedSize < 0) {
                log.error(sm.getString("encryptInterceptor.decrypt.error.short-message"));
                throw new IllegalStateException(sm.getString("encryptInterceptor.decrypt.error.short-message"));
            }

            XByteBuffer xbb = msg.getMessage();

            // Completely replace the message with the decrypted one
            xbb.setLength(0);
            xbb.append(data, blockSize, data.length - blockSize);

            super.messageReceived(msg);
        } catch (IllegalBlockSizeException ibse) {
            log.error(sm.getString("encryptInterceptor.decrypt.failed"), ibse);
        } catch (BadPaddingException bpe) {
            log.error(sm.getString("encryptInterceptor.decrypt.failed"), bpe);
        }
    }

    /**
     * Sets the encryption algorithm to be used for encrypting and decrypting
     * channel messages. You must specify the <code>algorithm/mode/padding</code>.
     * Information on standard algorithm names may be found in the
     * <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html">Java
     * documentation</a>.
     *
     * Default is <code>AES/CBC/PKCS5Padding</code>.
     *
     * @param algorithm The algorithm to use.
     */
    @Override
    public void setEncryptionAlgorithm(String algorithm) {
        if(null == getEncryptionAlgorithm())
            throw new IllegalStateException(sm.getString("encryptInterceptor.algorithm.required"));

        int pos = algorithm.indexOf('/');
        if(pos < 0)
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.required"));
        pos = algorithm.indexOf('/', pos + 1);
        if(pos < 0)
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.required"));

        encryptionAlgorithm = algorithm;
    }

    /**
     * Gets the encryption algorithm being used to encrypt and decrypt channel
     * messages.
     *
     * @return The algorithm being used, including the algorithm mode and padding.
     */
    @Override
    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    /**
     * Sets the encryption key for encryption and decryption. The length of the
     * key must be appropriate for the algorithm being used.
     *
     * @param key The encryption key.
     */
    @Override
    public void setEncryptionKey(byte[] key) {
        if (null == key) {
            encryptionKeyBytes = null;
        } else {
            encryptionKeyBytes = key.clone();
        }
    }

    /**
     * Gets the encryption key being used for encryption and decryption.
     * The key is encoded using hex-encoding where e.g. the byte <code>0xab</code>
     * will be shown as "ab". The length of the string in characters will
     * be twice the length of the key in bytes.
     *
     * @param keyBytes The encryption key.
     */
    public void setEncryptionKey(String keyBytes) {
        if (null == keyBytes) {
            setEncryptionKey((byte[])null);
        } else {
            setEncryptionKey(fromHexString(keyBytes.trim()));
        }
    }

    /**
     * Gets the encryption key being used for encryption and decryption.
     *
     * @return The encryption key.
     */
    @Override
    public byte[] getEncryptionKey() {
        byte[] key = getEncryptionKeyInternal();

        if(null != key)
            key = key.clone();

        return key;
    }

    private byte[] getEncryptionKeyInternal() {
        return encryptionKeyBytes;
    }

    /**
     * Sets the JCA provider name used for cryptographic activities.
     *
     * Default is the JVM platform default.
     *
     * @param provider The name of the JCA provider.
     */
    @Override
    public void setProviderName(String provider) {
        providerName = provider;
    }

    /**
     * Gets the JCA provider name used for cryptographic activities.
     *
     * Default is the JVM platform default.
     *
     * @return The name of the JCA provider.
     */
    @Override
    public String getProviderName() {
        return providerName;
    }

    private void initCiphers() throws GeneralSecurityException {
        if(null == getEncryptionKey())
            throw new IllegalStateException(sm.getString("encryptInterceptor.key.required"));

        String algorithm = getEncryptionAlgorithm();

        String mode = getAlgorithmMode(algorithm);

        if(!"CBC".equalsIgnoreCase(mode))
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.requires-cbc-mode", mode));

        Cipher cipher;

        String providerName = getProviderName();
        if(null == providerName) {
            cipher = Cipher.getInstance(algorithm);
        } else {
            cipher = Cipher.getInstance(algorithm, getProviderName());
        }

        byte[] iv = new byte[cipher.getBlockSize()];

        // Always use a random IV For cipher setup.
        // The recipient doesn't need the (matching) IV because we will always
        // pre-pad messages with the IV as a nonce.
        new SecureRandom().nextBytes(iv);

        IvParameterSpec IV = new IvParameterSpec(iv);

        // If this is a cipher transform of the form ALGO/MODE/PAD,
        // take just the algorithm part.
        int pos = algorithm.indexOf('/');

        String bareAlgorithm;
        if(pos >= 0) {
            bareAlgorithm = algorithm.substring(0, pos);
        } else {
            bareAlgorithm = algorithm;
        }

        SecretKeySpec encryptionKey = new SecretKeySpec(getEncryptionKey(), bareAlgorithm);

        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, IV);

        encryptionCipher = cipher;

        if(null == providerName) {
            cipher = Cipher.getInstance(algorithm);
        } else {
            cipher = Cipher.getInstance(algorithm, getProviderName());
        }

        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new IvParameterSpec(iv));

        decryptionCipher = cipher;
    }

    private Cipher getEncryptionCipher() {
        return encryptionCipher;
    }

    private Cipher getDecryptionCipher() {
        return decryptionCipher;
    }

    private static String getAlgorithmMode(String algorithm) {
        int start = algorithm.indexOf('/');
        if(start < 0)
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.required"));
        int end = algorithm.indexOf('/', start + 1);
        if(start < 0)
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.required"));

        return algorithm.substring(start + 1, end);
    }

    /**
     * Encrypts the input <code>bytes</code> into two separate byte arrays:
     * one for the initial block (which will be the encrypted random IV)
     * and the second one containing the actual encrypted payload.
     *
     * This method returns a pair of byte arrays instead of a single
     * concatenated one to reduce the number of byte buffers created
     * and copied during the whole operation -- including message re-building.
     *
     * @param bytes The data to encrypt.
     *
     * @return The encrypted IV block in [0] and the encrypted data in [1].
     *
     * @throws IllegalBlockSizeException If the input data is not a multiple of
     *             the block size and no padding has been requested (for block
     *             ciphers) or if the input data cannot be encrypted
     * @throws BadPaddingException Declared but should not occur during encryption
     */
    private byte[][] encrypt(byte[] bytes) throws IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = getEncryptionCipher();

        // Adding the IV to the beginning of the encrypted data
        byte[] iv = cipher.getIV();

        byte[][] data = new byte[2][];
        data[0] = cipher.update(iv, 0, iv.length);
        data[1] = cipher.doFinal(bytes);

        return data;
    }

    /**
     * Decrypts the input <code>bytes</code>.
     *
     * @param bytes The data to decrypt.
     *
     * @return The decrypted data.
     *
     * @throws IllegalBlockSizeException If the input data cannot be encrypted
     * @throws BadPaddingException If the decrypted data does not include the
     *             expected number of padding bytes
     *
     */
    private byte[] decrypt(byte[] bytes) throws IllegalBlockSizeException, BadPaddingException {
        return getDecryptionCipher().doFinal(bytes);
    }


    // Copied from org.apache.tomcat.util.buf.HexUtils

    private static final int[] DEC = {
        00, 01, 02, 03, 04, 05, 06, 07,  8,  9, -1, -1, -1, -1, -1, -1,
        -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, 10, 11, 12, 13, 14, 15,
    };


    private static int getDec(int index) {
        // Fast for correct values, slower for incorrect ones
        try {
            return DEC[index - '0'];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return -1;
        }
    }


    private static byte[] fromHexString(String input) {
        if (input == null) {
            return null;
        }

        if ((input.length() & 1) == 1) {
            // Odd number of characters
            throw new IllegalArgumentException(sm.getString("hexUtils.fromHex.oddDigits"));
        }

        char[] inputChars = input.toCharArray();
        byte[] result = new byte[input.length() >> 1];
        for (int i = 0; i < result.length; i++) {
            int upperNibble = getDec(inputChars[2*i]);
            int lowerNibble =  getDec(inputChars[2*i + 1]);
            if (upperNibble < 0 || lowerNibble < 0) {
                // Non hex character
                throw new IllegalArgumentException(sm.getString("hexUtils.fromHex.nonHex"));
            }
            result[i] = (byte) ((upperNibble << 4) + lowerNibble);
        }
        return result;
    }
}

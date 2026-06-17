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

import java.io.Serial;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelException;
import org.apache.catalina.tribes.ChannelInterceptor;
import org.apache.catalina.tribes.ChannelMessage;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.group.ChannelInterceptorBase;
import org.apache.catalina.tribes.group.InterceptorPayload;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.util.CyclicTracker;
import org.apache.catalina.tribes.util.StringManager;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Adds encryption using a pre-shared key. The length of the key (in bytes) must be acceptable for the encryption
 * algorithm being used. For example, for AES, you must use a key of either 16 bytes (128 bits, 24 bytes 192 bits), or
 * 32 bytes (256 bits). You can supply the raw key bytes by calling {@link #setEncryptionKey(byte[])} or the hex-encoded
 * binary bytes by calling {@link #setEncryptionKey(String)}.
 */
public class EncryptInterceptor extends ChannelInterceptorBase implements EncryptInterceptorMBean {

    private static final Log log = LogFactory.getLog(EncryptInterceptor.class);
    /**
     * String manager for internationalized messages.
     */
    protected static final StringManager sm = StringManager.getManager(EncryptInterceptor.class);

    private static final String DEFAULT_ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";

    private String providerName;
    private String encryptionAlgorithm = DEFAULT_ENCRYPTION_ALGORITHM;
    private byte[] encryptionKeyBytes;
    private String encryptionKeyString;
    private int replayWindowSize = 1024;


    private BaseEncryptionManager encryptionManager;

    /**
     * Creates a new encryption interceptor with default settings.
     */
    public EncryptInterceptor() {
    }

    @Override
    public void start(int svc) throws ChannelException {
        validateChannelChain();

        if (Channel.SND_TX_SEQ == (svc & Channel.SND_TX_SEQ)) {
            try {
                encryptionManager = createEncryptionManager(getEncryptionAlgorithm(), getEncryptionKeyInternal(),
                        getProviderName(), getReplayWindowSize());
            } catch (GeneralSecurityException gse) {
                throw new ChannelException(sm.getString("encryptInterceptor.init.failed"), gse);
            }
        }

        super.start(svc);
    }

    private void validateChannelChain() throws ChannelException {
        ChannelInterceptor interceptor = getPrevious();
        while (null != interceptor) {
            if (interceptor instanceof TcpFailureDetector) {
                throw new ChannelConfigException(sm.getString("encryptInterceptor.tcpFailureDetector.ordering"));
            }

            interceptor = interceptor.getPrevious();
        }
    }

    @Override
    public void stop(int svc) throws ChannelException {
        if (Channel.SND_TX_SEQ == (svc & Channel.SND_TX_SEQ)) {
            encryptionManager.shutdown();
        }

        super.stop(svc);
    }

    @Override
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload)
            throws ChannelException {
        try {
            byte[] data = msg.getMessage().getBytes();
            byte[] message = new byte[data.length + 8];
            XByteBuffer.toBytes(encryptionManager.getAndIncrementMessageNumber(), message, 0);
            System.arraycopy(data, 0, message, 8, data.length);

            // See #encrypt(byte[]) for an explanation of the return value
            byte[][] bytes = encryptionManager.encrypt(message);

            XByteBuffer xbb = msg.getMessage();

            // Completely replace the message
            xbb.clear();
            xbb.append(bytes[0], 0, bytes[0].length);
            xbb.append(bytes[1], 0, bytes[1].length);

            super.sendMessage(destination, msg, payload);

        } catch (GeneralSecurityException gse) {
            log.error(sm.getString("encryptInterceptor.encrypt.failed"));
            throw new ChannelException(gse);
        }
    }

    @Override
    public void messageReceived(ChannelMessage msg) {
        try {
            byte[] data = msg.getMessage().getBytes();

            data = encryptionManager.decrypt(data);
            if (data.length < 8) {
                throw new GeneralSecurityException(sm.getString("encryptInterceptor.decrypt.error.short-message"));
            }
            if (!encryptionManager.checkIncomingMessageNumber(msg.getAddress(), XByteBuffer.toLong(data, 0))) {
                log.error(sm.getString("encryptInterceptor.decrypt.replay"));
                return;
            }

            XByteBuffer xbb = msg.getMessage();

            // Completely replace the message with the decrypted one
            xbb.clear();
            xbb.append(data, 8, data.length - 8);

            super.messageReceived(msg);
        } catch (GeneralSecurityException gse) {
            log.error(sm.getString("encryptInterceptor.decrypt.failed"), gse);
        }
    }

    @Override
    public void memberDisappeared(Member member) {
        if (encryptionManager != null) {
            encryptionManager.memberDisappeared(member);
        }
        super.memberDisappeared(member);
    }

    /**
     * Sets the encryption algorithm to be used for encrypting and decrypting channel messages. You must specify the
     * <code>algorithm/mode/padding</code>. Information on standard algorithm names may be found in the
     * <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html">Java
     * documentation</a>. Default is <code>AES/CBC/PKCS5Padding</code> for backwards compatibility but it is recommended
     * that <code>AES/GCM/NoPadding</code> is used.
     *
     * @param algorithm The algorithm to use.
     */
    @Override
    public void setEncryptionAlgorithm(String algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.required"));
        }
        int pos = algorithm.indexOf('/');
        if (pos < 0) {
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.required"));
        }
        pos = algorithm.indexOf('/', pos + 1);
        if (pos < 0) {
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.required"));
        }

        encryptionAlgorithm = algorithm;
    }

    /**
     * Gets the encryption algorithm being used to encrypt and decrypt channel messages.
     *
     * @return The algorithm being used, including the algorithm mode and padding.
     */
    @Override
    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    /**
     * Sets the encryption key for encryption and decryption. The length of the key must be appropriate for the
     * algorithm being used.
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
     * Sets the encryption key using a hex-encoded string. Each pair of hex characters represents one byte of the key.
     *
     * @param keyBytes The hex-encoded encryption key.
     */
    public void setEncryptionKey(String keyBytes) {
        this.encryptionKeyString = keyBytes;
        if (null == keyBytes) {
            setEncryptionKey((byte[]) null);
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

        if (null != key) {
            key = key.clone();
        }

        return key;
    }

    private byte[] getEncryptionKeyInternal() {
        return encryptionKeyBytes;
    }

    /**
     * Returns the hex-encoded encryption key string.
     *
     * @return the hex-encoded encryption key, or {@code null} if not set
     */
    public String getEncryptionKeyString() {
        return encryptionKeyString;
    }

    /**
     * Sets the hex-encoded encryption key string.
     *
     * @param encryptionKeyString the hex-encoded encryption key
     */
    public void setEncryptionKeyString(String encryptionKeyString) {
        setEncryptionKey(encryptionKeyString);
    }

    /**
     * Sets the JCA provider name used for cryptographic activities. Default is the JVM platform default.
     *
     * @param provider The name of the JCA provider.
     */
    @Override
    public void setProviderName(String provider) {
        providerName = provider;
    }

    /**
     * Gets the JCA provider name used for cryptographic activities. Default is the JVM platform default.
     *
     * @return The name of the JCA provider.
     */
    @Override
    public String getProviderName() {
        return providerName;
    }

    /**
     * Returns the number of message sequence numbers remembered for replay detection.
     *
     * @return The replay window size
     */
    @Override
    public int getReplayWindowSize() {
        return replayWindowSize;
    }

    /**
     * Sets the number of message sequence numbers remembered for replay detection.
     *
     * @param replayWindowSize The replay window size
     */
    @Override
    public void setReplayWindowSize(int replayWindowSize) {
        if (replayWindowSize < 1) {
            throw new IllegalArgumentException("replayWindowSize must be greater than zero");
        }
        this.replayWindowSize = replayWindowSize;
    }

    Long getRemovedMemberHeadValue(Member member) {
        if (encryptionManager == null) {
            return null;
        }
        return encryptionManager.getRemovedMemberHeadValue(member);
    }

    // Copied from org.apache.tomcat.util.buf.HexUtils
    // @formatter:off
    private static final int[] DEC = {
        0, 1, 2, 3, 4, 5, 6, 7,  8,  9, -1, -1, -1, -1, -1, -1,
        -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, 10, 11, 12, 13, 14, 15,
    };
    // @formatter:on


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
            int upperNibble = getDec(inputChars[2 * i]);
            int lowerNibble = getDec(inputChars[2 * i + 1]);
            if (upperNibble < 0 || lowerNibble < 0) {
                // Non hex character
                throw new IllegalArgumentException(sm.getString("hexUtils.fromHex.nonHex"));
            }
            result[i] = (byte) ((upperNibble << 4) + lowerNibble);
        }
        return result;
    }

    private static BaseEncryptionManager createEncryptionManager(String algorithm, byte[] encryptionKey,
            String providerName, int replayWindowSize)
            throws NoSuchAlgorithmException, NoSuchPaddingException, NoSuchProviderException {
        if (null == encryptionKey) {
            throw new IllegalStateException(sm.getString("encryptInterceptor.key.required"));
        }

        String algorithmName;
        String algorithmMode;
        String algorithmPadding;

        // We need to break-apart the algorithm name e.g. AES/GCM/NoPadding
        // take just the algorithm part.
        int pos = algorithm.indexOf('/');

        if (pos >= 0) {
            algorithmName = algorithm.substring(0, pos).toUpperCase(Locale.ENGLISH);
            int pos2 = algorithm.indexOf('/', pos + 1);

            if (pos2 >= 0) {
                algorithmMode = algorithm.substring(pos + 1, pos2).toUpperCase(Locale.ENGLISH);
                algorithmPadding = algorithm.substring(pos2 + 1).toUpperCase(Locale.ENGLISH);
            } else {
                algorithmMode = "GCM";
                algorithmPadding = "NOPADDING";
            }
        } else {
            algorithmName = algorithm;
            algorithmMode = "GCM";
            algorithmPadding = "NOPADDING";
        }

        /*
         * Limit the cipher algorithm modes available. The limits are based on the cipher algorithm modes listed in the
         * Java Standard Names documentation. Those modes that are not appropriate or provide no protection are blocked.
         * Where there are performance or security concerns regarding a mode, a warning is logged. Unrecognised modes,
         * such as those provided by custom JCA providers are allowed but will be rejected if there is no JCA provider
         * to support them.
         */
        if ("NONE".equals(algorithmMode) || "ECB".equals(algorithmMode) || "PCBC".equals(algorithmMode) ||
                "CTS".equals(algorithmMode) || "KW".equals(algorithmMode) || "KWP".equals(algorithmMode) ||
                "CTR".equals(algorithmMode) || ("CBC".equals(algorithmMode) && "NOPADDING".equals(algorithmPadding)) ||
                ("CFB".equals(algorithmMode) && "NOPADDING".equals(algorithmPadding)) ||
                ("GCM".equals(algorithmMode) && "PKCS5PADDING".equals(algorithmPadding)) ||
                ("OFB".equals(algorithmMode) && "NOPADDING".equals(algorithmPadding))) {
            // Insecure, unsuitable or unsupported
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.unsupported", algorithm));

        } else if (("CBC".equals(algorithmMode) && "PKCS5PADDING".equals(algorithmPadding)) ||
                ("CFB".equals(algorithmMode) && "PKCS5PADDING".equals(algorithmPadding)) ||
                ("OFB".equals(algorithmMode) && "PKCS5PADDING".equals(algorithmPadding))) {
            // Supported but not recommended as more secure modes are available
            log.warn(sm.getString("encryptInterceptor.algorithm.switch", algorithm));

        } else if (algorithmMode.startsWith("CFB") || algorithmMode.startsWith("OFB")) {
            // Using a non-default block size. Not supported as insecure and/or inefficient.
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.unsupported", algorithm));

        } else if ("GCM".equals(algorithmMode) && "NOPADDING".equals(algorithmPadding)) {
            // Needs a specialised encryption manager to handle the differences between GCM and other modes
            return new GCMEncryptionManager(algorithm, new SecretKeySpec(encryptionKey, algorithmName), providerName,
                    replayWindowSize);
        }

        // Use the default encryption manager
        try {
            return new BaseEncryptionManager(algorithm, new SecretKeySpec(encryptionKey, algorithmName), providerName,
                    replayWindowSize);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | NoSuchProviderException ex) {
            throw new IllegalArgumentException(sm.getString("encryptInterceptor.algorithm.unsupported", algorithm), ex);
        }
    }

    private static class BaseEncryptionManager {
        /**
         * The fully-specified algorithm e.g. AES/CBC/PKCS5Padding.
         */
        private final String algorithm;

        /**
         * The block size of the cipher.
         */
        private final int blockSize;

        /**
         * The cryptographic provider name.
         */
        private final String providerName;

        /**
         * The secret key to use for encryption and decryption operations.
         */
        private final SecretKeySpec secretKey;

        /**
         * A pool of Cipher objects. Ciphers are expensive to create, but not to re-initialize, so we use a pool of them
         * which grows as necessary.
         */
        private final ConcurrentLinkedQueue<Cipher> cipherPool;

        /**
         * A pool of SecureRandom objects. Each encrypt operation requires access to a source of randomness.
         * SecureRandom is thread-safe, but sharing a single instance will likely be a bottleneck.
         */
        private final ConcurrentLinkedQueue<SecureRandom> randomPool;
        private final AtomicLong messageNumberGenerator = new AtomicLong();
        private final Map<Member,CyclicTracker> receivedMessageNumbersByMember = new ConcurrentHashMap<>();
        private final Map<Member,Long> messageNumbersByRemovedMember = new ConcurrentHashMap<>();
        private final CyclicTracker receivedMessageNumbersForUnknownSender;
        private final int replayWindowSize;

        BaseEncryptionManager(String algorithm, SecretKeySpec secretKey, String providerName, int replayWindowSize)
                throws NoSuchAlgorithmException, NoSuchPaddingException, NoSuchProviderException {
            this.algorithm = algorithm;
            this.providerName = providerName;
            this.secretKey = secretKey;
            this.replayWindowSize = replayWindowSize;

            cipherPool = new ConcurrentLinkedQueue<>();
            Cipher cipher = createCipher();
            blockSize = cipher.getBlockSize();
            cipherPool.offer(cipher);
            randomPool = new ConcurrentLinkedQueue<>();
            receivedMessageNumbersForUnknownSender = new CyclicTracker(replayWindowSize);
        }

        public void shutdown() {
            // Individual Cipher and SecureRandom objects need no explicit tear down
            cipherPool.clear();
            randomPool.clear();
            receivedMessageNumbersByMember.clear();
            messageNumbersByRemovedMember.clear();
        }

        public long getAndIncrementMessageNumber() {
            return messageNumberGenerator.getAndIncrement();
        }

        public boolean checkIncomingMessageNumber(Member sender, long messageNumber) {
            if (sender == null) {
                return receivedMessageNumbersForUnknownSender.track(messageNumber);
            }
            return receivedMessageNumbersByMember.computeIfAbsent(sender, this::createTrackerForMember)
                    .track(messageNumber);
        }

        public void memberDisappeared(Member member) {
            CyclicTracker tracker = receivedMessageNumbersByMember.remove(member);
            if (tracker != null) {
                /*
                 * There is a security trade off here.
                 *
                 * Entries are only removed from this Map if the Member reappears. That means there is a potential DoS
                 * risks due to the growth of this Map. That is considered unlikely as only Members with the encryption
                 * key will be added to this Map and the size of the Map.Entry is minimal.
                 *
                 * If entries are removed from this Map based either on Map size or time, that exposes the risk of a
                 * replay attack using any message the Member may have previously sent.
                 *
                 * The replay attack is viewed as the higher risk, hence there are no limits on the size of this Map.
                 */
                messageNumbersByRemovedMember.put(member, Long.valueOf(tracker.getHeadValue()));
            }
        }

        public Long getRemovedMemberHeadValue(Member member) {
            return messageNumbersByRemovedMember.get(member);
        }

        private CyclicTracker createTrackerForMember(Member member) {
            CyclicTracker tracker = new CyclicTracker(replayWindowSize);
            Long headValue = messageNumbersByRemovedMember.remove(member);
            if (headValue != null) {
                tracker.track(headValue.longValue());
            }
            return tracker;
        }

        private String getAlgorithm() {
            return algorithm;
        }

        private SecretKeySpec getSecretKey() {
            return secretKey;
        }

        /**
         * Gets the size, in bytes, of the initialization vector for the cipher being used. The IV size is often, but
         * not always, the block size for the cipher.
         *
         * @return The size of the initialization vector for this algorithm.
         */
        protected int getIVSize() {
            return blockSize;
        }

        private String getProviderName() {
            return providerName;
        }

        private Cipher createCipher() throws NoSuchAlgorithmException, NoSuchPaddingException, NoSuchProviderException {
            String providerName = getProviderName();

            if (null == providerName) {
                return Cipher.getInstance(getAlgorithm());
            } else {
                return Cipher.getInstance(getAlgorithm(), providerName);
            }
        }

        private Cipher getCipher() throws GeneralSecurityException {
            Cipher cipher = cipherPool.poll();

            if (null == cipher) {
                cipher = createCipher();
            }

            return cipher;
        }

        private void returnCipher(Cipher cipher) {
            cipherPool.offer(cipher);
        }

        private SecureRandom getRandom() {
            SecureRandom random = randomPool.poll();

            if (null == random) {
                random = new SecureRandom();
            }

            return random;
        }

        private void returnRandom(SecureRandom random) {
            randomPool.offer(random);
        }

        /**
         * Encrypts the input <code>bytes</code> into two separate byte arrays: one for the random initialization vector
         * (IV) used for this message, and the second one containing the actual encrypted payload. This method returns a
         * pair of byte arrays instead of a single concatenated one to reduce the number of byte buffers created and
         * copied during the whole operation -- including message re-building.
         *
         * @param bytes The data to encrypt.
         *
         * @return The IV in [0] and the encrypted data in [1].
         *
         * @throws GeneralSecurityException If the input data cannot be encrypted.
         */
        private byte[][] encrypt(byte[] bytes) throws GeneralSecurityException {
            Cipher cipher = null;

            // Always use a random IV For cipher setup.
            // The recipient doesn't need the (matching) IV because we will always
            // pre-pad messages with the IV as a nonce.
            byte[] iv = generateIVBytes();

            try {
                cipher = getCipher();
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), generateIV(iv, 0, getIVSize()));

                // Prepend the IV to the beginning of the encrypted data
                byte[][] data = new byte[2][];
                data[0] = iv;
                data[1] = cipher.doFinal(bytes);

                return data;
            } finally {
                if (null != cipher) {
                    returnCipher(cipher);
                }
            }
        }

        /**
         * Decrypts the input <code>bytes</code>.
         *
         * @param bytes The data to decrypt.
         *
         * @return The decrypted data.
         *
         * @throws GeneralSecurityException If the input data cannot be decrypted.
         */
        private byte[] decrypt(byte[] bytes) throws GeneralSecurityException {
            Cipher cipher = null;

            int ivSize = getIVSize();
            AlgorithmParameterSpec IV = generateIV(bytes, 0, ivSize);

            try {
                cipher = getCipher();

                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), IV);

                // Decrypt remainder of the message.
                return cipher.doFinal(bytes, ivSize, bytes.length - ivSize);
            } finally {
                if (null != cipher) {
                    returnCipher(cipher);
                }
            }
        }

        protected byte[] generateIVBytes() {
            byte[] ivBytes = new byte[getIVSize()];

            SecureRandom random = null;

            try {
                random = getRandom();

                // Always use a random IV For cipher setup.
                // The recipient doesn't need the (matching) IV because we will always
                // pre-pad messages with the IV as a nonce.
                random.nextBytes(ivBytes);

                return ivBytes;
            } finally {
                if (null != random) {
                    returnRandom(random);
                }
            }
        }

        protected AlgorithmParameterSpec generateIV(byte[] ivBytes, int offset, int length) {
            return new IvParameterSpec(ivBytes, offset, length);
        }
    }

    /**
     * Implements an EncryptionManager for using GCM block cipher modes.
     * <p>
     * GCM works a little differently than some of the other block cipher modes supported by EncryptInterceptor. First
     * of all, it requires a different kind of AlgorithmParameterSpec object to be used, and second, it requires a
     * slightly different initialization vector and something called an "authentication tag".
     * <p>
     * The choice of IV length can be somewhat arbitrary, but there is consensus that 96-bit (12-byte) IVs for GCM are
     * the best trade-off between security and performance. For other block cipher modes, IV length is the same as the
     * block size.
     * <p>
     * The "authentication tag" is a computed authentication value based upon the message and the encryption process.
     * GCM defines these tags as the number of bits to use for the authentication tag, and it's clear that the highest
     * number of bits supported 128-bit provide the best security.
     */
    private static class GCMEncryptionManager extends BaseEncryptionManager {
        GCMEncryptionManager(String algorithm, SecretKeySpec secretKey, String providerName, int replayWindowSize)
                throws NoSuchAlgorithmException, NoSuchPaddingException, NoSuchProviderException {
            super(algorithm, secretKey, providerName, replayWindowSize);
        }

        @Override
        protected int getIVSize() {
            return 12; // See class javadoc for explanation of this magic number (12)
        }

        @Override
        protected AlgorithmParameterSpec generateIV(byte[] bytes, int offset, int length) {
            // See class javadoc for explanation of this magic number (128)
            return new GCMParameterSpec(128, bytes, offset, length);
        }
    }

    static class ChannelConfigException extends ChannelException {
        @Serial
        private static final long serialVersionUID = 1L;

        ChannelConfigException(String message) {
            super(message);
        }
    }
}

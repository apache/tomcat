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
package org.apache.catalina.realm;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;
import org.apache.tomcat.util.security.ConstantTime;

/**
 * This credential handler supports the following forms of stored passwords:
 * <ul>
 * <li><b>encodedCredential</b> - a hex encoded digest of the password digested using the configured digest</li>
 * <li><b>{MD5}encodedCredential</b> - a Base64 encoded MD5 digest of the password</li>
 * <li><b>{SHA}encodedCredential</b> - a Base64 encoded SHA1 digest of the password</li>
 * <li><b>{SSHA}encodedCredential</b> - 20 byte Base64 encoded SHA1 digest followed by variable length salt.
 *
 * <pre>
 * {SSHA}&lt;sha-1 digest:20&gt;&lt;salt:n&gt;
 * </pre>
 *
 * </li>
 * <li><b>salt$iterationCount$encodedCredential</b> - a hex encoded salt, iteration code and a hex encoded credential,
 * each separated by $</li>
 * </ul>
 * <p>
 * If the stored password form does not include an iteration count then an iteration count of 1 is used.
 * <p>
 * If the stored password form does not include salt then no salt is used.
 */
public class MessageDigestCredentialHandler extends DigestCredentialHandlerBase {

    /**
     * Default constructor.
     */
    public MessageDigestCredentialHandler() {
    }

    private static final Log log = LogFactory.getLog(MessageDigestCredentialHandler.class);

    /**
     * The default number of iterations.
     */
    public static final int DEFAULT_ITERATIONS = 1;

    private Charset encoding = StandardCharsets.UTF_8;
    private String algorithm = null;
    private boolean digestInRfc3112Order = true;


    /**
     * Get the encoding.
     * @return the encoding
     */
    public String getEncoding() {
        return encoding.name();
    }


    /**
     * Set the encoding.
     * @param encodingName the encoding name
     */
    public void setEncoding(String encodingName) {
        if (encodingName == null) {
            encoding = StandardCharsets.UTF_8;
        } else {
            try {
                this.encoding = B2CConverter.getCharset(encodingName);
            } catch (UnsupportedEncodingException e) {
                log.error(sm.getString("mdCredentialHandler.unknownEncoding", encodingName, encoding.name()));
            }
        }
    }


    @Override
    public String getAlgorithm() {
        return algorithm;
    }


    @Override
    public void setAlgorithm(String algorithm) throws NoSuchAlgorithmException {
        ConcurrentMessageDigest.init(algorithm);
        this.algorithm = algorithm;
    }


    /**
     * Get whether the digest is in RFC 3112 order.
     * @return whether the digest is in RFC 3112 order
     */
    public boolean getDigestInRfc3112Order() {
        return digestInRfc3112Order;
    }


    /**
     * Set whether the digest is in RFC 3112 order.
     * @param digestInRfc3112Order whether the digest is in RFC 3112 order
     */
    public void setDigestInRfc3112Order(boolean digestInRfc3112Order) {
        this.digestInRfc3112Order = digestInRfc3112Order;
    }


    @Override
    public boolean matches(String inputCredentials, String storedCredentials) {
        if (inputCredentials == null || storedCredentials == null) {
            return false;
        }

        if (getAlgorithm() == null) {
            // No digests, compare directly
            return ConstantTime.equals(inputCredentials, storedCredentials, false);
        } else {
            // Some directories and databases prefix the password with the hash
            // type. The string is in a format compatible with Base64.encode not
            // the normal hex encoding of the digest
            if (storedCredentials.startsWith("{MD5}") || storedCredentials.startsWith("{SHA}")) {
                // Server is storing digested passwords with a prefix indicating
                // the digest type
                String base64ServerDigest = storedCredentials.substring(5);
                byte[] userDigest = ConcurrentMessageDigest.digest(getAlgorithm(),
                        inputCredentials.getBytes(StandardCharsets.ISO_8859_1));
                String base64UserDigest = Base64.getEncoder().encodeToString(userDigest);

                return ConstantTime.equals(base64UserDigest, base64ServerDigest, false);
            } else if (storedCredentials.startsWith("{SSHA}")) {
                // "{SSHA}<sha-1 digest:20><salt:n>"
                // Need to convert the salt to bytes to apply it to the user's
                // digested password.
                String serverDigestPlusSalt = storedCredentials.substring(6);
                byte[] serverDigestPlusSaltBytes = Base64.getDecoder().decode(serverDigestPlusSalt);

                // Extract the first 20 bytes containing the SHA-1 digest
                final int digestLength = 20;
                byte[] serverDigestBytes = new byte[digestLength];
                System.arraycopy(serverDigestPlusSaltBytes, 0, serverDigestBytes, 0, digestLength);

                // the remaining bytes are the salt
                final int saltLength = serverDigestPlusSaltBytes.length - digestLength;
                byte[] serverSaltBytes = new byte[saltLength];
                System.arraycopy(serverDigestPlusSaltBytes, digestLength, serverSaltBytes, 0, saltLength);

                // Generate the digested form of the user provided password
                // using the salt
                byte[] userDigestBytes = ConcurrentMessageDigest.digest(getAlgorithm(),
                        inputCredentials.getBytes(StandardCharsets.ISO_8859_1), serverSaltBytes);

                return ConstantTime.equals(userDigestBytes, serverDigestBytes);
            } else if (storedCredentials.indexOf('$') > -1) {
                return matchesSaltIterationsEncoded(inputCredentials, storedCredentials);
            } else {
                // Hex hashes should be compared case-insensitively
                String userDigest = mutate(inputCredentials, null, 1);
                if (userDigest == null) {
                    // Failed to mutate user credentials. Automatic fail.
                    // Root cause should be logged by mutate()
                    return false;
                }
                return ConstantTime.equals(storedCredentials, userDigest, true);
            }
        }
    }


    @Override
    protected String mutate(String inputCredentials, byte[] salt, int iterations) {
        if (algorithm == null) {
            return inputCredentials;
        } else {
            byte[] inputCredentialbytes = inputCredentials.getBytes(encoding);
            byte[] userDigest;
            if (salt == null) {
                userDigest = ConcurrentMessageDigest.digest(algorithm, iterations, inputCredentialbytes);
            } else {
                if (digestInRfc3112Order) {
                    // RFC 3112 states that the input order for the digest is credentials then salt
                    userDigest = ConcurrentMessageDigest.digest(algorithm, iterations, inputCredentialbytes, salt);
                } else {
                    userDigest = ConcurrentMessageDigest.digest(algorithm, iterations, salt, inputCredentialbytes);
                }
            }
            return HexUtils.toHexString(userDigest);
        }
    }


    @Override
    protected int getDefaultIterations() {
        return DEFAULT_ITERATIONS;
    }


    @Override
    protected Log getLog() {
        return log;
    }
}

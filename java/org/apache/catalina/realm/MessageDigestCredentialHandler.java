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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.apache.catalina.CredentialHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;

/**
 * This credential handler supports the following forms of stored passwords:
 * <ul>
 * <li><b>encodedCredential</b> - a hex encoded digest of the password digested
 *     using the configured digest</li>
 * <li><b>{MD5}encodedCredential</b> - a Base64 encoded MD5 digest of the
 *     password</li>
 * <li><b>{SHA}encodedCredential</b> - a Base64 encoded SHA1 digest of the
 *     password</li>
 * <li><b>{SSHA}encodedCredential</b> - 20 character salt followed by the salted
 *     SHA1 digest Base64 encoded</li>
 * <li><b>salt$iterationCount$encodedCredential</b> - a hex encoded salt,
 *     iteration code and a hex encoded credential, each separated by $</li>
 * </ul>
 *
 * Prefixes of {MD5} and {SHA}
 * <p>
 * If the stored password form does not included an iteration count then an
 * iteration count of 1 is used.
 * <p>
 * If the stored password form does not include salt then no salt is used.
 */
public class MessageDigestCredentialHandler implements CredentialHandler {

    private static final Log log = LogFactory.getLog(MessageDigestCredentialHandler.class);
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    private Charset encoding = StandardCharsets.UTF_8;
    private String digest = null;


    public String getEncoding() {
        return encoding.name();
    }


    public void setEncoding(String encodingName) {
        if (encodingName == null) {
            encoding = StandardCharsets.UTF_8;
        } else {
            try {
                this.encoding = B2CConverter.getCharset(encodingName);
            } catch (UnsupportedEncodingException e) {
                log.warn(sm.getString("mdCredentialHandler.unknownEncoding=.unknownEncoding",
                        encodingName, encoding.name()));
            }
        }
    }


    public String getDigest() {
        return digest;
    }


    public void setDigest(String digest) {
        try {
            MessageDigest.getInstance(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
        this.digest = digest;
    }


    @Override
    public boolean matches(String inputCredentials, String storedCredentials) {

        if (inputCredentials == null || storedCredentials == null) {
            return false;
        }

        if (getDigest() == null) {
            // No digests, compare directly
            return storedCredentials.equals(inputCredentials);
        } else {
            // Some directories and databases prefix the password with the hash
            // type. The string is in a format compatible with Base64.encode not
            // the normal hex encoding of the digest
            if (storedCredentials.startsWith("{MD5}") ||
                    storedCredentials.startsWith("{SHA}")) {
                // Server is storing digested passwords with a prefix indicating
                // the digest type
                String serverDigest = storedCredentials.substring(5);
                String userDigest = Base64.encodeBase64String(ConcurrentMessageDigest.digest(
                        getDigest(), inputCredentials.getBytes(StandardCharsets.ISO_8859_1)));
                return userDigest.equals(serverDigest);

            } else if (storedCredentials.startsWith("{SSHA}")) {
                // Server is storing digested passwords with a prefix indicating
                // the digest type and the salt used when creating that digest

                String serverDigestPlusSalt = storedCredentials.substring(6);

                // Need to convert the salt to bytes to apply it to the user's
                // digested password.
                byte[] serverDigestPlusSaltBytes =
                        Base64.decodeBase64(serverDigestPlusSalt);
                final int saltPos = 20;
                byte[] serverDigestBytes = new byte[saltPos];
                System.arraycopy(serverDigestPlusSaltBytes, 0,
                        serverDigestBytes, 0, saltPos);
                final int saltLength = serverDigestPlusSaltBytes.length - saltPos;
                byte[] serverSaltBytes = new byte[saltLength];
                System.arraycopy(serverDigestPlusSaltBytes, saltPos,
                        serverSaltBytes, 0, saltLength);

                // Generate the digested form of the user provided password
                // using the salt
                byte[] userDigestBytes = ConcurrentMessageDigest.digest(getDigest(),
                        inputCredentials.getBytes(StandardCharsets.ISO_8859_1),
                        serverSaltBytes);

                return Arrays.equals(userDigestBytes, serverDigestBytes);

            } else if (storedCredentials.indexOf('$') > -1) {
                int sep1 = storedCredentials.indexOf('$');
                int sep2 = storedCredentials.indexOf('$', sep1);
                String hexSalt = storedCredentials.substring(0,  sep1);
                int iterations = Integer.parseInt(storedCredentials.substring(sep1 + 1, sep2));
                String hexEncoded = storedCredentials.substring(sep2 + 1);
                byte[] salt = HexUtils.fromHexString(hexSalt);

                String userDigest = mutate(inputCredentials, salt, iterations);

                return hexEncoded.equalsIgnoreCase(userDigest);
            } else {
                // Hex hashes should be compared case-insensitively
                String userDigest = mutate(inputCredentials, null, 1);
                return storedCredentials.equalsIgnoreCase(userDigest);
            }
        }
    }


    @Override
    public String mutate(String inputCredentials, byte[] salt, int iterations) {
        if (digest == null) {
            return inputCredentials;
        } else {
            byte[] userDigest;
            if (salt == null) {
                userDigest = ConcurrentMessageDigest.digest(digest, iterations,
                        inputCredentials.getBytes(encoding));
            } else {
                userDigest = ConcurrentMessageDigest.digest(digest, iterations,
                        salt, inputCredentials.getBytes(encoding));
            }
            return HexUtils.toHexString(userDigest);
        }
    }
}

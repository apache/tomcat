/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

import org.apache.tomcat.util.security.MD5Encoder;

/**
 * Authenticator supporting the DIGEST authentication method.
 */
public class DigestAuthenticator extends Authenticator {

    public static final String schemeName = "digest";
    private static final Object cnonceGeneratorLock = new Object();
    private static volatile SecureRandom cnonceGenerator;
    private int nonceCount = 0;
    private long cNonce;

    @Override
    public String getAuthorization(String requestUri, String authenticateHeader, String userName, String userPassword,
            String userRealm) throws AuthenticationException {

        validateUsername(userName);
        validatePassword(userPassword);

        Map<String, String> parameterMap = parseAuthenticateHeader(authenticateHeader);
        String realm = parameterMap.get("realm");

        validateRealm(userRealm, realm);

        String nonce = parameterMap.get("nonce");
        String messageQop = parameterMap.get("qop");
        String algorithm = parameterMap.get("algorithm") == null ? "MD5" : parameterMap.get("algorithm");
        String opaque = parameterMap.get("opaque");

        StringBuilder challenge = new StringBuilder();

        if (!messageQop.isEmpty()) {
            if (cnonceGenerator == null) {
                synchronized(cnonceGeneratorLock) {
                    if (cnonceGenerator == null) {
                        cnonceGenerator = new SecureRandom();
                    }
                }
            }

            cNonce = cnonceGenerator.nextLong();
            nonceCount++;
        }

        challenge.append("Digest ");
        challenge.append("username =\"" + userName + "\",");
        challenge.append("realm=\"" + realm + "\",");
        challenge.append("nonce=\"" + nonce + "\",");
        challenge.append("uri=\"" + requestUri + "\",");

        try {
            challenge.append("response=\"" + calculateRequestDigest(requestUri, userName, userPassword,
                    realm, nonce, messageQop, algorithm) + "\",");
        }

        catch (NoSuchAlgorithmException e) {
            throw new AuthenticationException(
                    "Unable to generate request digest " + e.getMessage());
        }

        challenge.append("algorithm=" + algorithm + ",");
        challenge.append("opaque=\"" + opaque + "\",");

        if (!messageQop.isEmpty()) {
            challenge.append("qop=\"" + messageQop + "\"");
            challenge.append(",cnonce=\"" + cNonce + "\",");
            challenge.append("nc=" + String.format("%08X", Integer.valueOf(nonceCount)));
        }

        return challenge.toString();

    }

    private String calculateRequestDigest(String requestUri, String userName, String password,
            String realm, String nonce, String qop, String algorithm)
            throws NoSuchAlgorithmException {

        StringBuilder preDigest = new StringBuilder();
        String A1;

        if (algorithm.equalsIgnoreCase("MD5")) {
            A1 = userName + ":" + realm + ":" + password;
        } else {
            A1 = encodeMD5(userName + ":" + realm + ":" + password) + ":" + nonce + ":" + cNonce;
        }

        /*
         * If the "qop" value is "auth-int", then A2 is: A2 = Method ":"
         * digest-uri-value ":" H(entity-body) since we do not have an entity-body, A2 =
         * Method ":" digest-uri-value for auth and auth_int
         */
        String A2 = "GET:" + requestUri;

        preDigest.append(encodeMD5(A1));
        preDigest.append(':');
        preDigest.append(nonce);

        if (qop.toLowerCase().contains("auth")) {
            preDigest.append(':');
            preDigest.append(String.format("%08X", Integer.valueOf(nonceCount)));
            preDigest.append(':');
            preDigest.append(String.valueOf(cNonce));
            preDigest.append(':');
            preDigest.append(qop);
        }

        preDigest.append(':');
        preDigest.append(encodeMD5(A2));

        return encodeMD5(preDigest.toString());

    }

    private String encodeMD5(String value) throws NoSuchAlgorithmException {
        byte[] bytesOfMessage = value.getBytes(StandardCharsets.ISO_8859_1);
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] thedigest = md.digest(bytesOfMessage);

        return MD5Encoder.encode(thedigest);
    }

    @Override
    public String getSchemeName() {
        return schemeName;
    }
}

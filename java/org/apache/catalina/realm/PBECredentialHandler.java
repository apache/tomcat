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

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.catalina.CredentialHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.res.StringManager;

public class PBECredentialHandler implements CredentialHandler {

    private static final Log log = LogFactory.getLog(PBECredentialHandler.class);

    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    public static final String DEFAULT_ALGORITHM = "PBKDF2WithHmacSHA1";
    public static final int DEFAULT_KEYLENGTH = 160;

    private SecretKeyFactory secretKeyFactory;
    private int keyLength = 160;


    public PBECredentialHandler() throws NoSuchAlgorithmException {
        setAlgorithm(DEFAULT_ALGORITHM);
    }


    public String getAlgorithm() {
        return secretKeyFactory.getAlgorithm();
    }


    public void setAlgorithm(String algorithm) throws NoSuchAlgorithmException {
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
        this.secretKeyFactory = secretKeyFactory;
    }


    public int getKeyLength() {
        return keyLength;
    }


    public void setKeyLength(int keyLength) {
        this.keyLength = keyLength;
    }


    @Override
    public boolean matches(String inputCredentials, String storedCredentials) {
        int sep1 = storedCredentials.indexOf('$');
        int sep2 = storedCredentials.indexOf('$', sep1);
        String hexSalt = storedCredentials.substring(0,  sep1);
        int iterations = Integer.parseInt(storedCredentials.substring(sep1 + 1, sep2));
        String hexEncoded = storedCredentials.substring(sep2 + 1);
        byte[] salt = HexUtils.fromHexString(hexSalt);

        String userDigest = mutate(inputCredentials, salt, iterations);

        return hexEncoded.equalsIgnoreCase(userDigest);
    }


    @Override
    public String mutate(String inputCredentials, byte[] salt, int iterations) {
        KeySpec spec = new PBEKeySpec(inputCredentials.toCharArray(), salt, iterations, getKeyLength());

        try {
            return HexUtils.toHexString(secretKeyFactory.generateSecret(spec).getEncoded());
        } catch (InvalidKeySpecException e) {
            // TODO Log a warning
            return null;
        }
    }
}

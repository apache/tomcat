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
import java.security.SecureRandom;
import java.util.Random;

import org.apache.catalina.CredentialHandler;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * Base implementation for the Tomcat provided {@link CredentialHandler}s.
 */
public abstract class DigestCredentialHandlerBase implements CredentialHandler {

    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    public static final int DEFAULT_SALT_LENGTH = 32;

    private int iterations = getDefaultIterations();
    private int saltLength = getDefaultSaltLength();
    private final Object randomLock = new Object();
    private volatile Random random = null;
    private boolean logInvalidStoredCredentials = false;


    /**
     * Return the number of iterations of the associated algorithm that will be
     * used when creating a new stored credential for a given input credential.
     */
    public int getIterations() {
        return iterations;
    }


    /**
     * Set the number of iterations of the associated algorithm that will be
     * used when creating a new stored credential for a given input credential.
     */
    public void setIterations(int iterations) {
        this.iterations = iterations;
    }


    /**
     * Return the salt length that will be used when creating a new stored
     * credential for a given input credential.
     */
    public int getSaltLength() {
        return saltLength;
    }


    /**
     * Set the salt length that will be used when creating a new stored
     * credential for a given input credential.
     */
    public void setSaltLength(int saltLength) {
        this.saltLength = saltLength;
    }


    /**
     * When checking input credentials against stored credentials will a warning
     * message be logged if invalid stored credentials are discovered?
     */
    public boolean getLogInvalidStoredCredentials() {
        return logInvalidStoredCredentials;
    }


    /**
     * Set whether a warning message will be logged if invalid stored
     * credentials are discovered while checking input credentials against
     * stored credentials?
     */
    public void setLogInvalidStoredCredentials(boolean logInvalidStoredCredentials) {
        this.logInvalidStoredCredentials = logInvalidStoredCredentials;
    }


    @Override
    public String mutate(String userCredential) {
        byte[] salt = null;
        int iterations = getIterations();
        int saltLength = getSaltLength();
        if (saltLength == 0) {
            salt = new byte[0];
        } else if (saltLength > 0) {
            // Double checked locking. OK since random is volatile.
            if (random == null) {
                synchronized (randomLock) {
                    if (random == null) {
                        random = new SecureRandom();
                    }
                }
            }
            salt = new byte[saltLength];
            // Concurrent use of this random is unlikely to be a performance
            // issue as it is only used during stored password generation.
            random.nextBytes(salt);
        }

        String serverCredential = mutate(userCredential, salt, iterations);

        if (saltLength == 0 && iterations == 1) {
            // Output the simple/old format for backwards compatibility
            return serverCredential;
        } else {
            StringBuilder result =
                    new StringBuilder((saltLength << 1) + 10 + serverCredential.length() + 2);
            result.append(HexUtils.toHexString(salt));
            result.append('$');
            result.append(iterations);
            result.append('$');
            result.append(serverCredential);

            return result.toString();
        }
    }


    /**
     * Checks whether the provided credential matches the stored credential when
     * the stored credential is in the form salt$iteration-count$credential
     *
     * @param inputCredentials  The input credential
     * @param storedCredentials The stored credential
     *
     * @return <code>true</code> if they match, otherwise <code>false</code>
     */
    protected boolean matchesSaltIterationsEncoded(String inputCredentials,
            String storedCredentials) {

        int sep1 = storedCredentials.indexOf('$');
        int sep2 = storedCredentials.indexOf('$', sep1 + 1);

        if (sep1 < 0 || sep2 < 0) {
            // Stored credentials are invalid
            // This may be expected if nested credential handlers are being used
            logInvalidStoredCredentials(storedCredentials);
            return false;
        }

        String hexSalt = storedCredentials.substring(0,  sep1);

        int iterations = Integer.parseInt(storedCredentials.substring(sep1 + 1, sep2));

        String storedHexEncoded = storedCredentials.substring(sep2 + 1);
        byte[] salt;
        try {
            salt = HexUtils.fromHexString(hexSalt);
        } catch (IllegalArgumentException iae) {
            logInvalidStoredCredentials(storedCredentials);
            return false;
        }

        String inputHexEncoded = mutate(inputCredentials, salt, iterations);

        return storedHexEncoded.equalsIgnoreCase(inputHexEncoded);
    }


    private void logInvalidStoredCredentials(String storedCredentials) {
        if (logInvalidStoredCredentials) {
            // Logging credentials could be a security concern but they are
            // invalid and that is probably a bigger problem
            getLog().warn(sm.getString("credentialHandler.invalidStoredCredential",
                    storedCredentials));
        }
    }


    /**
     * Get the default salt length used by the {@link CredentialHandler}.
     */
    protected int getDefaultSaltLength() {
        return DEFAULT_SALT_LENGTH;
    }


    /**
     * Generates the equivalent stored credentials for the given input
     * credentials, salt and iterations.
     *
     * @param inputCredentials  User provided credentials
     * @param salt              Salt, if any
     * @param iterations        Number of iterations of the algorithm associated
     *                          with this CredentialHandler applied to the
     *                          inputCredentials to generate the equivalent
     *                          stored credentials
     *
     * @return  The equivalent stored credentials for the given input
     *          credentials
     */
    protected abstract String mutate(String inputCredentials, byte[] salt, int iterations);

    /**
     * Set the algorithm used to convert input credentials to stored
     * credentials.
     */
    public abstract void setAlgorithm(String algorithm) throws NoSuchAlgorithmException;


    /**
     * Get the algorithm used to convert input credentials to stored
     * credentials.
     */
    public abstract String getAlgorithm();


    /**
     * Get the default number of iterations used by the
     * {@link CredentialHandler}.
     */
    protected abstract int getDefaultIterations();


    /**
     * Obtain the logger for the CredentialHandler instance.
     */
    protected abstract Log getLog();
}

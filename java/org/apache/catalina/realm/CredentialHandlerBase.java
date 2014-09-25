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
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * Base implementation for the Tomcat provided {@link CredentialHandler}s.
 */
public abstract class CredentialHandlerBase implements CredentialHandler {

    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private int iterations = getDefaultIterations();
    private Random random = null;


    /**
     * Return the number of iterations of the associated algorithm that will be
     * used to convert the plain text credential into the stored credential.
     */
    public int getIterations() {
        return iterations;
    }


    /**
     * Set the number of iterations of the associated algorithm that will be
     * used to convert the plain text credential into the stored credential.
     */
    public void setIterations(int iterations) {
        this.iterations = iterations;
    }


    /**
     * Generate a stored credential from the given plain text credential.
     *
     * @param saltLength        Length of random salt to be generated and used
     *                          as part of the transformation
     * @param userCredential    The plain text credential
     *
     * @return  The credential to be stored
     */
    public String generate(int saltLength, String userCredential) {
        byte[] salt = null;
        int iterations = getIterations();
        if (saltLength == 0) {
            salt = new byte[0];
        } else if (saltLength > 0) {
            if (random == null) {
                random = new SecureRandom();
            }
            salt = new byte[saltLength];
            random.nextBytes(salt);
        }

        String serverCredential = mutate(userCredential, salt, iterations);

        return HexUtils.toHexString(salt) + "$" + iterations + "$" + serverCredential;
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

        String hexSalt = storedCredentials.substring(0,  sep1);

        int iterations = Integer.parseInt(storedCredentials.substring(sep1 + 1, sep2));

        String storedHexEncoded = storedCredentials.substring(sep2 + 1);
        byte[] salt = HexUtils.fromHexString(hexSalt);

        String inputHexEncoded = mutate(inputCredentials, salt, iterations);

        return storedHexEncoded.equalsIgnoreCase(inputHexEncoded);
    }


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
}

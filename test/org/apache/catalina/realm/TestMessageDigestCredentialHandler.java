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

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.security.ConcurrentMessageDigest;

public class TestMessageDigestCredentialHandler {

    private static final String[] DIGESTS = new String[] {"MD5", "SHA-1", "SHA-512"};

    private static final String PWD = "password";

    static {
        try {
            ConcurrentMessageDigest.init("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    public void testGeneral() throws Exception {
        for (String digest : DIGESTS) {
            for (int saltLength = 0; saltLength < 20; saltLength++) {
                for (int iterations = 1; iterations < 100; iterations += 10) {
                  doTest(digest, saltLength, iterations);
                }
            }
        }
    }

    private void doTest(String digest, int saltLength, int iterations) throws NoSuchAlgorithmException {
        MessageDigestCredentialHandler mdch = new MessageDigestCredentialHandler();
        MessageDigestCredentialHandler verifier = new MessageDigestCredentialHandler();
        mdch.setAlgorithm(digest);
        mdch.setIterations(iterations);
        mdch.setSaltLength(saltLength);
        verifier.setAlgorithm(digest);
        String storedCredential = mdch.mutate(PWD);
        Assert.assertTrue(verifier.matches(PWD, storedCredential));
    }
}

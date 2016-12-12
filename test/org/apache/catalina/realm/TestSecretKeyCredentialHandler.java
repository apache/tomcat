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

public class TestSecretKeyCredentialHandler {

    private static final String[] ALGORITHMS = { "PBKDF2WithHmacSHA1", "PBEWithMD5AndDES" };
    private static final String[] PASSWORDS = { "password", "$!&#%!%@$#@*^$%&%%#!!*%$%&#@!^" };
    private static final int[] KEYLENGTHS = { 8, 111, 256 };
    private static final int[] SALTLENGTHS = { 1, 7, 12, 20 };
    private static final int[] ITERATIONS = { 1, 2111, 10000 };

    @Test
    public void testGeneral() throws Exception {
        for (String digest : ALGORITHMS) {
            for (String password : PASSWORDS) {
                for (int saltLength : SALTLENGTHS) {
                    for (int iterations : ITERATIONS) {
                        for (int keyLength : KEYLENGTHS) {
                            doTest(password, digest, saltLength, iterations, keyLength, true);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testZeroSalt() throws NoSuchAlgorithmException {
        doTest(PASSWORDS[0], ALGORITHMS[0], 0, ITERATIONS[0], KEYLENGTHS[0], false);
    }

    @Test
    public void testZeroIterations() throws NoSuchAlgorithmException {
        doTest(PASSWORDS[0], ALGORITHMS[0], SALTLENGTHS[0], 0, KEYLENGTHS[0], false);
    }

    @Test
    public void testZeroKeyLength() throws NoSuchAlgorithmException {
        doTest(PASSWORDS[0], ALGORITHMS[0], SALTLENGTHS[0], ITERATIONS[0], 0, false);
    }

    private void doTest(String password, String digest, int saltLength, int iterations,
            int keyLength, boolean expectMatch) throws NoSuchAlgorithmException {
        SecretKeyCredentialHandler pbech = new SecretKeyCredentialHandler();
        SecretKeyCredentialHandler verifier = new SecretKeyCredentialHandler();
        pbech.setAlgorithm(digest);
        pbech.setIterations(iterations);
        pbech.setSaltLength(saltLength);
        pbech.setKeyLength(keyLength);
        verifier.setAlgorithm(digest);
        String storedCredential = pbech.mutate(password);
        if (expectMatch) {
            Assert.assertTrue(
                    "[" + digest + "] [" + saltLength + "] [" + iterations + "] [" + keyLength + "] ["
                            + password + "] [" + storedCredential + "]",
                    verifier.matches(password, storedCredential));
        } else {
            Assert.assertFalse(
                    "[" + digest + "] [" + saltLength + "] [" + iterations + "] [" + keyLength + "] ["
                            + password + "] [" + storedCredential + "]",
                    verifier.matches(password, storedCredential));
        }
    }
}

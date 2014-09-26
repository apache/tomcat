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

    private static final String[] ALGORITHMS =
            new String[] {"PBKDF2WithHmacSHA1", "PBEWithMD5AndDES"};

    private static final String PWD = "password";

    @Test
    public void testGeneral() throws Exception {
        for (String digest : ALGORITHMS) {
            for (int saltLength = 1; saltLength < 20; saltLength++) {
                for (int iterations = 1; iterations < 10000; iterations += 1000)
                doTest(digest, saltLength, iterations);
            }
        }
    }

    private void doTest(String digest, int saltLength, int iterations) throws NoSuchAlgorithmException {
        SecretKeyCredentialHandler pbech = new SecretKeyCredentialHandler();
        pbech.setAlgorithm(digest);
        pbech.setIterations(iterations);
        pbech.setSaltLength(saltLength);
        String storedCredential = pbech.mutate(PWD);
        Assert.assertTrue(pbech.matches(PWD, storedCredential));
    }
}

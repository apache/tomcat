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
package org.apache.tomcat.util.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class KeyStoreUtil {

    private KeyStoreUtil() {
        // Utility class
    }

    /**
     * Loads a KeyStore from an InputStream working around the known JDK bug
     * https://bugs.openjdk.java.net/browse/JDK-8157404.
     *
     * This code can be removed once the minimum Java version for Tomcat is 13.
     *
     *
     * @param keystore The KeyStore to load from the InputStream
     * @param is The InputStream to use to populate the KeyStore
     * @param storePass The password to access the KeyStore
     *
     * @throws IOException
     *              If an I/O occurs reading from the given InputStream
     * @throws CertificateException
     *              If one or more certificates can't be loaded into the
     *              KeyStore
     * @throws NoSuchAlgorithmException
     *              If the algorithm specified to validate the integrity of the
     *              KeyStore cannot be found
     */
    public static void load(KeyStore keystore, InputStream is, char[] storePass)
            throws NoSuchAlgorithmException, CertificateException, IOException {
        if (keystore.getType().equals("PKCS12")) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int numRead;
            while ((numRead = is.read(buf)) >= 0) {
                baos.write(buf, 0, numRead);
            }
            baos.close();
            // Don't close is. That remains the callers responsibility.

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

            keystore.load(bais, storePass);
        } else {
            keystore.load(is, storePass);
        }
    }
}

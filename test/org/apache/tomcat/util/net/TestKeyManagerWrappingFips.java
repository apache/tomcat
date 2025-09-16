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

package org.apache.tomcat.util.net;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactorySpi;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.X509KeyManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.net.jsse.JSSEUtil;

/**
 * Test case for <a href="https://bz.apache.org/bugzilla/show_bug.cgi?id=64614">Bug 64614</a>.
 */
public class TestKeyManagerWrappingFips {
    private static final String FIPS_PROVIDER = "FIPS_PROVIDER";
    private static final String NON_FIPS_PROVIDER = "NON_FIPS_PROVIDER";
    private static final String DUMMY_ALGORITHM = "DUMMY_ALGORITHM";
    private static final String KEYSTORE_PROVIDER = "KEYSTORE_PROVIDER";
    private static final String DUMMY_KEYSTORE = "DUMMY_KEYSTORE";
    @After
    public void restore() {
        DummyKeyStoreSpi.wrappingOccurred = false;
        Security.removeProvider(FIPS_PROVIDER);
        Security.removeProvider(NON_FIPS_PROVIDER);
        Security.removeProvider(KEYSTORE_PROVIDER);
    }

    @Test
    public void testBug64614_01() throws Exception {
        Security.addProvider(new DummyKeyManagerFactoryProvider(FIPS_PROVIDER, "Sun JSSE provider (FIPS mode, crypto provider SunPKCS11-NSSfips", DUMMY_ALGORITHM));
        getKeyManagers();
        Assert.assertFalse(DummyKeyStoreSpi.wrappingOccurred);
    }

    @Test
    public void testBug64614_02() throws Exception {
        Security.addProvider(new DummyKeyManagerFactoryProvider(NON_FIPS_PROVIDER, "Sun JSSE provider", DUMMY_ALGORITHM));
        getKeyManagers();
        Assert.assertTrue(DummyKeyStoreSpi.wrappingOccurred);
    }
    private void getKeyManagers() throws Exception {
        Security.addProvider(new DummyKeyStoreProvider(KEYSTORE_PROVIDER, "", DUMMY_KEYSTORE));
        SSLHostConfig hostConfig = new SSLHostConfig();
        hostConfig.setKeyManagerAlgorithm(DUMMY_ALGORITHM);
        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(hostConfig, SSLHostConfigCertificate.Type.UNDEFINED);

        File keystoreFile = File.createTempFile("keystore", ".jks");

        certificate.setCertificateKeystoreProvider(KEYSTORE_PROVIDER);
        certificate.setCertificateKeystoreType(DUMMY_KEYSTORE);
        certificate.setCertificateKeystoreFile(keystoreFile.getAbsolutePath());
        new JSSEUtil(certificate).getKeyManagers();

        if (!keystoreFile.delete()) {
            keystoreFile.deleteOnExit();
        }
    }

    private static final class DummyKeyStoreProvider extends Provider {
        DummyKeyStoreProvider(String name, String info, String algorithm) {
            super(name, "", info);
            put("KeyStore." + algorithm, DummyKeyStoreSpi.class.getName());
        }
    }

    public static final class DummyKeyStoreSpi extends KeyStoreSpi {
        static volatile boolean wrappingOccurred = false;
        @Override
        public Key engineGetKey(String s, char[] chars) {
            wrappingOccurred = true;
            return null;
        }
        @Override
        public Certificate[] engineGetCertificateChain(String s) {
            return null;
        }
        @Override
        public Certificate engineGetCertificate(String s) {
            return null;
        }
        @Override
        public Date engineGetCreationDate(String s) {
            return null;
        }
        @Override
        public void engineSetKeyEntry(String s, Key key, char[] chars, Certificate[] certificates) {
        }
        @Override
        public void engineSetKeyEntry(String s, byte[] bytes, Certificate[] certificates) {
        }
        @Override
        public void engineSetCertificateEntry(String s, Certificate certificate) {
        }
        @Override
        public void engineDeleteEntry(String s) {
        }
        @Override
        public Enumeration<String> engineAliases() {
            return new Enumeration<>() {
                @Override
                public boolean hasMoreElements() {
                    return true;
                }
                @Override
                public String nextElement() {
                    return "";
                }
            };
        }
        @Override
        public boolean engineContainsAlias(String s) {
            return false;
        }
        @Override
        public int engineSize() {
            return 0;
        }
        @Override
        public boolean engineIsKeyEntry(String s) {
            return true;
        }
        @Override
        public boolean engineIsCertificateEntry(String s) {
            return false;
        }
        @Override
        public String engineGetCertificateAlias(Certificate certificate) {
            return "";
        }
        @Override
        public void engineStore(OutputStream outputStream, char[] chars) {
        }
        @Override
        public void engineLoad(InputStream inputStream, char[] chars) {
        }
    }

    private static final class DummyKeyManagerFactoryProvider extends Provider {
        DummyKeyManagerFactoryProvider(String name, String info, String algorithm) {
            super(name, "", info);
            put("KeyManagerFactory." + algorithm, DummyKeyManagerFactorySpi.class.getName());
        }
    }

    public static final class DummyKeyManagerFactorySpi extends KeyManagerFactorySpi {
        @Override
        protected void engineInit(KeyStore ks, char[] password) {
        }
        @Override
        protected void engineInit(ManagerFactoryParameters spec) {
        }
        @Override
        protected KeyManager[] engineGetKeyManagers() {
            return new KeyManager[] { new X509KeyManager() {
                @Override
                public String[] getClientAliases(String s, Principal[] principals) {
                    return new String[0];
                }

                @Override
                public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
                    return "";
                }

                @Override
                public String[] getServerAliases(String s, Principal[] principals) {
                    return new String[0];
                }

                @Override
                public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
                    return "";
                }

                @Override
                public X509Certificate[] getCertificateChain(String s) {
                    return new X509Certificate[0];
                }

                @Override
                public PrivateKey getPrivateKey(String s) {
                    return null;
                }
            } };
        }
    }
}

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
import java.util.Arrays;
import java.util.logging.Level;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

public class TestLargeClientHello extends TomcatBaseTest {

    // https://bz.apache.org/bugzilla/show_bug.cgi?id=67938
    @Test
    public void testLargeClientHelloWithSessionResumption() throws Exception {
        File keystoreFile = TesterKeystoreGenerator.generateKeystore("localhost", "tomcat",
            new String[]{"localhost", "*.localhost"},
                (keyPair, certBuilder) -> {
                JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
                certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                        extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
                certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                        extUtils.createAuthorityKeyIdentifier(keyPair.getPublic()));
                certBuilder.addExtension(Extension.basicConstraints, true,
                    new BasicConstraints(true));
                certBuilder.addExtension(Extension.keyUsage, false,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
                char[] padding = new char[16922];
                Arrays.fill(padding, 'x');
                certBuilder.addExtension(new ASN1ObjectIdentifier("2.999"), false,
                    new DERUTF8String(new String(padding)));
            });

        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();
        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        TesterSupport.initSsl(tomcat, keystoreFile.getAbsolutePath(), null, null, false);

        try (LogCapture logCapture = attachLogCapture(Level.FINE, "org.apache.tomcat.util.net.SecureNioChannel")) {

            tomcat.start();

            SSLContext sc = SSLContext.getInstance(Constants.SSL_PROTO_TLSv1_3);
            sc.init(null, new TrustManager[]{new TesterSupport.TrustAllCerts()}, null);
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            String url = "https://localhost:" + getPort() + "/";
            Assert.assertTrue(getUrl(url).toString().contains("Hello World"));
            Assert.assertTrue(getUrl(url).toString().contains("Hello World"));

            Assert.assertTrue(logCapture.containsText(
                    TomcatBaseTest.getKeyFromPropertiesFile("org.apache.tomcat.util.net",
                    "channel.nio.ssl.handshakeUnwrapBufferUnderflow")));
        }

    }
}

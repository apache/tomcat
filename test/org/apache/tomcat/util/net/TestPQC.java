/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.net;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.jni.AprStatus;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.openssl.OpenSSLStatus;

@RunWith(Parameterized.class)
public class TestPQC extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();
        parameterSets.add(new Object[] {
                "JSSE", Boolean.FALSE, "org.apache.tomcat.util.net.jsse.JSSEImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.OpenSSLImplementation"});
        parameterSets.add(new Object[] {
                "OpenSSL-FFM", Boolean.TRUE, "org.apache.tomcat.util.net.openssl.panama.OpenSSLImplementation"});
        return parameterSets;
    }

    @Parameter(0)
    public String connectorName;

    @Parameter(1)
    public boolean useOpenSSL;

    @Parameter(2)
    public String sslImplementationName;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();

        Assert.assertTrue(connector.setProperty("SSLEnabled", "true"));
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setProtocols(Constants.SSL_PROTO_TLSv1_3);
        connector.addSslHostConfig(sslHostConfig);

        TesterSupport.configureSSLImplementation(tomcat, sslImplementationName, useOpenSSL);
        if ("OpenSSL".equals(connectorName)) {
            // getOpenSSLVersion() requires that the listener has been initialised
            tomcat.getServer().findLifecycleListeners()[0].lifecycleEvent(
                    new LifecycleEvent(tomcat.getServer(), Lifecycle.BEFORE_INIT_EVENT, null));
        }

        Context ctx = getProgrammaticRootContext();
        Tomcat.addServlet(ctx, "TesterServlet", new TesterServlet());
        ctx.addServletMappingDecoded("/*", "TesterServlet");
    }

    @Test
    public void testHostMLDSA44() throws Exception {
        File[] pqcFiles = configureHostMLDSA("ML-DSA-44");
        doTestWithOpenSSLClient(pqcFiles[0].getAbsolutePath(), null, null, null);
    }


    @Test
    public void testHostMLDSA65() throws Exception {
        File[] pqcFiles = configureHostMLDSA("ML-DSA-65");
        doTestWithOpenSSLClient(pqcFiles[0].getAbsolutePath(), null, null, null);
    }


    @Test
    public void testHostMLDSA87() throws Exception {
        File[] pqcFiles = configureHostMLDSA("ML-DSA-87");
        doTestWithOpenSSLClient(pqcFiles[0].getAbsolutePath(), null, null, null);
    }

    @Test
    public void testHostRSAandMLDSA() throws Exception {
        configureHostRSA();
        configureHostMLDSA("ML-DSA-65");
        doTest();
    }

    @Test
    public void testHostECandMLDSA() throws Exception {
        configureHostEC();
        configureHostMLDSA("ML-DSA-65");
        doTest();
    }

    @Test
    public void testHostRSAwithX25519MLKEM768() throws Exception {
        configureHostRSA();
        configureHostWithGroup("X25519MLKEM768");
        doTestWithOpenSSLClient(new File(TesterSupport.CA_CERT_PEM).getAbsolutePath(),
                "X25519MLKEM768", null, null);
    }


    @Test
    public void testHostRSAwithSecP256r1MLKEM768() throws Exception {
        configureHostRSA();
        configureHostWithGroup("SecP256r1MLKEM768");
        doTestWithOpenSSLClient(new File(TesterSupport.CA_CERT_PEM).getAbsolutePath(),
                "SecP256r1MLKEM768", null, null);
    }

    @Test
    public void testHostRSAwithSecP384r1MLKEM1024() throws Exception {
        configureHostRSA();
        configureHostWithGroup("SecP384r1MLKEM1024");
        doTestWithOpenSSLClient(new File(TesterSupport.CA_CERT_PEM).getAbsolutePath(),
                "SecP384r1MLKEM1024", null, null);
    }

    @Test
    public void testHostMLDSAwithX25519MLKEM768() throws Exception {
        File[] pqcFiles = configureHostMLDSA("ML-DSA-65");
        configureHostWithGroup("X25519MLKEM768");
        doTestWithOpenSSLClient(pqcFiles[0].getAbsolutePath(), "X25519MLKEM768", null, null);
    }

    @Test
    public void testHostMLDSAwithSecP256r1MLKEM768() throws Exception {
        File[] pqcFiles = configureHostMLDSA("ML-DSA-65");
        configureHostWithGroup("SecP256r1MLKEM768");
        doTestWithOpenSSLClient(pqcFiles[0].getAbsolutePath(), "SecP256r1MLKEM768", null, null);
    }

    @Test
    public void testClientMLDSA() throws Exception {
        configureHostRSA();
        File[] clientFiles = TesterKeystoreGenerator.generatePQCCertificate("testuser", "ML-DSA-65",
                null, null);
        SSLHostConfig sslHostConfig = getTomcatInstance().getConnector().findSslHostConfigs()[0];
        sslHostConfig.setCertificateVerification("required");
        sslHostConfig.setCaCertificateFile(clientFiles[0].getAbsolutePath());
        doTestWithOpenSSLClient(new File(TesterSupport.CA_CERT_PEM).getAbsolutePath(), null,
                clientFiles[0].getAbsolutePath(), clientFiles[1].getAbsolutePath());
    }

    @Test
    public void testClientMLDSAwithMLDSAServer() throws Exception {
        File[] serverFiles = configureHostMLDSA("ML-DSA-65");
        File[] clientFiles = TesterKeystoreGenerator.generatePQCCertificate("testuser", "ML-DSA-65",
                null, null);
        SSLHostConfig sslHostConfig = getTomcatInstance().getConnector().findSslHostConfigs()[0];
        sslHostConfig.setCertificateVerification("required");
        sslHostConfig.setCaCertificateFile(clientFiles[0].getAbsolutePath());
        doTestWithOpenSSLClient(serverFiles[0].getAbsolutePath(), null,
                clientFiles[0].getAbsolutePath(), clientFiles[1].getAbsolutePath());
    }

    @Test(expected = SSLHandshakeException.class)
    public void testHostMLDSAHandshakeFailure() throws Exception {
        assumePQCSupported();
        configureHostMLDSA("ML-DSA-65");

        SSLContext sc = SSLContext.getInstance(Constants.SSL_PROTO_TLSv1_2);
        sc.init(null, new TrustManager[] { new TesterSupport.TrustAllCerts() }, null);
        TesterSupport.ClientSSLSocketFactory clientSSLSocketFactory =
                new TesterSupport.ClientSSLSocketFactory(sc.getSocketFactory());
        clientSSLSocketFactory.setProtocols(new String[] { Constants.SSL_PROTO_TLSv1_2 });
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(clientSSLSocketFactory);

        Tomcat tomcat = getTomcatInstance();
        tomcat.start();
        getUrl("https://localhost:" + getPort() + "/");
    }


    private void assumePQCSupported() {
        if ("JSSE".equals(connectorName)) {
            Assume.assumeTrue("JSSE does not yet support PQC", false);
        } else if ("OpenSSL".equals(connectorName)) {
            Assume.assumeTrue(
                    "PQC requires OpenSSL 3.5+, found version 0x" + Integer.toHexString(AprStatus.getOpenSSLVersion()),
                    AprStatus.getOpenSSLVersion() >= 0x30500000);
        } else if ("OpenSSL-FFM".equals(connectorName)) {
            Assume.assumeTrue("PQC requires OpenSSL 3.5+, found version " + OpenSSLStatus.getVersion(),
                    OpenSSLStatus.getMajorVersion() > 3 ||
                        OpenSSLStatus.getMajorVersion() == 3 && OpenSSLStatus.getMinorVersion() >= 5);
        } else {
            Assert.fail("Unknown connector");
        }
    }

    private File[] configureHostMLDSA(String algorithm) throws Exception {
        File[] pqcFiles = TesterKeystoreGenerator.generatePQCCertificate("localhost", algorithm,
                new String[] { "localhost" }, null);

        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();
        SSLHostConfig sslHostConfig = connector.findSslHostConfigs()[0];

        SSLHostConfigCertificate cert = new SSLHostConfigCertificate(sslHostConfig, Type.MLDSA);
        cert.setCertificateFile(pqcFiles[0].getAbsolutePath());
        cert.setCertificateKeyFile(pqcFiles[1].getAbsolutePath());
        sslHostConfig.addCertificate(cert);

        return pqcFiles;
    }

    private void configureHostRSA() {
        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();
        SSLHostConfig sslHostConfig = connector.findSslHostConfigs()[0];

        SSLHostConfigCertificate cert = new SSLHostConfigCertificate(sslHostConfig, Type.RSA);
        cert.setCertificateFile(new File(TesterSupport.LOCALHOST_RSA_CERT_PEM).getAbsolutePath());
        cert.setCertificateKeyFile(new File(TesterSupport.LOCALHOST_RSA_KEY_PEM).getAbsolutePath());
        cert.setCertificateKeyPassword(TesterSupport.JKS_PASS);
        sslHostConfig.addCertificate(cert);
    }

    private void configureHostEC() {
        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();
        SSLHostConfig sslHostConfig = connector.findSslHostConfigs()[0];

        SSLHostConfigCertificate cert = new SSLHostConfigCertificate(sslHostConfig, Type.EC);
        cert.setCertificateFile(new File(TesterSupport.LOCALHOST_EC_CERT_PEM).getAbsolutePath());
        cert.setCertificateKeyFile(new File(TesterSupport.LOCALHOST_EC_KEY_PEM).getAbsolutePath());
        sslHostConfig.addCertificate(cert);
    }

    private void configureHostWithGroup(String groupName) {
        Tomcat tomcat = getTomcatInstance();
        Connector connector = tomcat.getConnector();
        SSLHostConfig sslHostConfig = connector.findSslHostConfigs()[0];
        sslHostConfig.setGroups(groupName);
    }

    private void doTest() throws Exception {
        assumePQCSupported();
        SSLContext sc = SSLContext.getInstance(Constants.SSL_PROTO_TLSv1_3);
        sc.init(null, new TrustManager[] { new TesterSupport.TrustAllCerts() }, null);
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        Tomcat tomcat = getTomcatInstance();
        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() + "/");
        Assert.assertEquals("OK", res.toString());
    }

    private void doTestWithOpenSSLClient(String caFile, String groups,
            String clientCert, String clientKey) throws Exception {
        assumePQCSupported();

        Tomcat tomcat = getTomcatInstance();
        tomcat.start();

        String openSSLPath = System.getProperty("tomcat.test.openssl.path");
        String openSSLLibPath = null;
        if (openSSLPath == null || openSSLPath.length() == 0) {
            openSSLPath = "openssl";
        } else {
            openSSLLibPath = openSSLPath.substring(0, openSSLPath.lastIndexOf('/'));
            openSSLLibPath = openSSLLibPath + "/../:" + openSSLLibPath + "/../lib:" + openSSLLibPath + "/../lib64";
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(openSSLPath);
        cmd.add("s_client");
        cmd.add("-connect");
        cmd.add("localhost:" + getPort());
        cmd.add("-CAfile");
        cmd.add(caFile);
        cmd.add("-tls1_3");
        if (groups != null) {
            cmd.add("-groups");
            cmd.add(groups);
        }
        if (clientCert != null) {
            cmd.add("-cert");
            cmd.add(clientCert);
            cmd.add("-key");
            cmd.add(clientKey);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);

        if (openSSLLibPath != null) {
            Map<String,String> env = pb.environment();
            String libraryPath = env.get("LD_LIBRARY_PATH");
            if (libraryPath == null) {
                libraryPath = openSSLLibPath;
            } else {
                libraryPath = libraryPath + ":" + openSSLLibPath;
            }
            env.put("LD_LIBRARY_PATH", libraryPath);
        }

        pb.redirectErrorStream(true);
        Process p = pb.start();

        p.getOutputStream().write("GET / HTTP/1.0\r\nHost: localhost\r\n\r\n".getBytes());
        p.getOutputStream().flush();

        String output = new String(p.getInputStream().readAllBytes());

        Assert.assertTrue("Process did not complete in time", p.waitFor(10, TimeUnit.SECONDS));
        Assert.assertTrue("TLS handshake failed:\n" + output, output.contains("HTTP/1."));
        Assert.assertTrue("Unexpected response body:\n" + output, output.contains("OK"));
    }
}

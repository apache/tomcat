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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.net.openssl.OpenSSLConf;
import org.apache.tomcat.util.net.openssl.OpenSSLConfCmd;
import org.apache.tomcat.util.net.openssl.ciphers.Cipher;
import org.apache.tomcat.util.net.openssl.ciphers.MessageDigest;

public class TestSSLHostConfig {

    @Test
    public void testCipher01() {
        SSLHostConfig hc = new SSLHostConfig();
        Cipher c = Cipher.TLS_RSA_WITH_NULL_MD5;

        // Single JSSE name
        hc.setCiphers(c.getJsseNames().iterator().next());
        Assert.assertEquals(c.getOpenSSLAlias(), hc.getCiphers());
    }


    @Test
    public void testCipher02() {
        SSLHostConfig hc = new SSLHostConfig();
        Cipher c1 = Cipher.TLS_RSA_WITH_NULL_MD5;
        Cipher c2 = Cipher.TLS_RSA_WITH_NULL_SHA;

        // Two JSSE names
        hc.setCiphers(c1.getJsseNames().iterator().next() + "," +
                c2.getJsseNames().iterator().next());
        Assert.assertEquals(c1.getOpenSSLAlias() + ":" + c2.getOpenSSLAlias(), hc.getCiphers());
    }


    @Test
    public void testCipher03() {
        SSLHostConfig hc = new SSLHostConfig();
        // Single OpenSSL alias
        hc.setCiphers("ALL");
        Assert.assertEquals("ALL", hc.getCiphers());
    }


    @Test
    public void testCipher04() {
        SSLHostConfig hc = new SSLHostConfig();
        Cipher c = Cipher.TLS_RSA_WITH_NULL_MD5;

        // Single OpenSSLName name
        hc.setCiphers(c.getOpenSSLAlias());
        Assert.assertEquals(c.getOpenSSLAlias(), hc.getCiphers());
    }


    @Test
    public void testCipher05() {
        SSLHostConfig hc = new SSLHostConfig();
        Cipher c = Cipher.TLS_AES_128_CCM_SHA256;

        // Single TLSv1.3 name - should be filtered out
        hc.setCiphers(c.getOpenSSLAlias());
        Assert.assertEquals("", hc.getCiphers());
    }


    @Test
    public void testCipher06() {
        SSLHostConfig hc = new SSLHostConfig();
        Cipher c1 = Cipher.TLS_AES_128_CCM_SHA256;
        Cipher c2 = Cipher.TLS_RSA_WITH_NULL_MD5;

        // TLSv1.3 then TLSv1.2 - TLSv1.3 name should be filtered out
        hc.setCiphers(c1.getOpenSSLAlias() + ":" + c2.getOpenSSLAlias());
        Assert.assertEquals(c2.getOpenSSLAlias(), hc.getCiphers());
    }


    @Test
    public void testCipher07() {
        SSLHostConfig hc = new SSLHostConfig();
        Cipher c1 = Cipher.TLS_AES_128_CCM_SHA256;
        Cipher c2 = Cipher.TLS_RSA_WITH_NULL_MD5;

        // TLSv1.2 then TLSv1.3 - TLSv1.3 name should be filtered out
        hc.setCiphers(c2.getOpenSSLAlias() + ":" + c1.getOpenSSLAlias());
        Assert.assertEquals(c2.getOpenSSLAlias(), hc.getCiphers());
    }


    @Test
    public void testCiphersuite01() {
        SSLHostConfig hc = new SSLHostConfig();
        Cipher c = Cipher.TLS_AES_128_CCM_SHA256;

        // Single TLSv1.3 cipher suite name
        hc.setCipherSuites(c.getOpenSSLAlias());
        Assert.assertEquals(c.getOpenSSLAlias(), hc.getCipherSuites());
    }


    @Test
    public void testCiphersuite02() {
        SSLHostConfig hc = new SSLHostConfig();
        Cipher c1 = Cipher.TLS_AES_128_CCM_SHA256;
        Cipher c2 = Cipher.TLS_RSA_WITH_NULL_MD5;

        // TLSv1.3 then TLSv1.2 - TLSv1.2 name should be filtered out
        hc.setCipherSuites(c1.getOpenSSLAlias() + ":" + c2.getOpenSSLAlias());
        Assert.assertEquals(c1.getOpenSSLAlias(), hc.getCipherSuites());
    }


    @Test
    public void testCiphersuite03() {
        SSLHostConfig hc = new SSLHostConfig();
        Cipher c1 = Cipher.TLS_AES_128_CCM_SHA256;
        Cipher c2 = Cipher.TLS_RSA_WITH_NULL_MD5;

        // TLSv1.2 then TLSv1.3 - TLSv1.2 name should be filtered out
        hc.setCipherSuites(c2.getOpenSSLAlias() + ":" + c1.getOpenSSLAlias());
        Assert.assertEquals(c1.getOpenSSLAlias(), hc.getCipherSuites());
    }


    @Test
    public void testPreSharedKey() {
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigPreSharedKey preSharedKey = new SSLHostConfigPreSharedKey(sslHostConfig);
        Assert.assertEquals("SHA256", preSharedKey.getDigest());
        Assert.assertEquals(MessageDigest.SHA256, preSharedKey.getDigestInternal());
        preSharedKey.setIdentity("test");
        preSharedKey.setKey("00010203");
        preSharedKey.setDigest("SHA256");
        sslHostConfig.addPreSharedKey(preSharedKey);

        Assert.assertSame(sslHostConfig, preSharedKey.getSSLHostConfig());
        Assert.assertEquals("test", preSharedKey.getIdentity());
        Assert.assertEquals("00010203", preSharedKey.getKey());
        Assert.assertArrayEquals(new byte[] { 0, 1, 2, 3 }, preSharedKey.getKeyInternal());
        Assert.assertEquals("SHA256", preSharedKey.getDigest());
        Assert.assertEquals(MessageDigest.SHA256, preSharedKey.getDigestInternal());
        Assert.assertSame(preSharedKey, sslHostConfig.getPreSharedKeys().iterator().next());
    }


    @Test(expected = IllegalArgumentException.class)
    public void testPreSharedKeyInvalidKey() {
        SSLHostConfigPreSharedKey preSharedKey = new SSLHostConfigPreSharedKey(null);
        preSharedKey.setKey("invalid");
    }


    @Test(expected = IllegalArgumentException.class)
    public void testPreSharedKeyInvalidDigest() {
        SSLHostConfigPreSharedKey preSharedKey = new SSLHostConfigPreSharedKey(null);
        preSharedKey.setDigest("invalid");
    }


    @Test
    public void testPreSharedKeyOnly() {
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        SSLHostConfigPreSharedKey preSharedKey = new SSLHostConfigPreSharedKey(sslHostConfig);
        sslHostConfig.addPreSharedKey(preSharedKey);

        Assert.assertTrue(sslHostConfig.isPreSharedKeyOnly());

        // Creating the default certificate used to hold the SSLContext must not change the result.
        sslHostConfig.getCertificates(true);
        Assert.assertTrue(sslHostConfig.isPreSharedKeyOnly());

        SSLHostConfig withCertificate = new SSLHostConfig();
        withCertificate.addPreSharedKey(new SSLHostConfigPreSharedKey(withCertificate));
        withCertificate.addCertificate(
                new SSLHostConfigCertificate(withCertificate, SSLHostConfigCertificate.Type.UNDEFINED));
        Assert.assertFalse(withCertificate.isPreSharedKeyOnly());
    }


    @Test
    public void testSerialization() throws IOException, ClassNotFoundException {
        // Dummy OpenSSL command name/value pair
        String name = "foo";
        String value = "bar";

        // Set up the object
        SSLHostConfig sslHostConfig = new SSLHostConfig();
        OpenSSLConf openSSLConf = new OpenSSLConf();
        OpenSSLConfCmd openSSLConfCmd = new OpenSSLConfCmd();
        openSSLConfCmd.setName(name);
        openSSLConfCmd.setValue(value);
        openSSLConf.addCmd(openSSLConfCmd);
        sslHostConfig.setOpenSslConf(openSSLConf);
        SSLHostConfigPreSharedKey preSharedKey = new SSLHostConfigPreSharedKey(sslHostConfig);
        preSharedKey.setIdentity("test");
        preSharedKey.setKey("00010203");
        preSharedKey.setDigest("SHA256");
        sslHostConfig.addPreSharedKey(preSharedKey);

        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(sslHostConfig);
        oos.close();

        // Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        SSLHostConfig output = (SSLHostConfig) ois.readObject();

        // Check values
        List<OpenSSLConfCmd> commands = output.getOpenSslConf().getCommands();
        Assert.assertEquals(1, commands.size());
        OpenSSLConfCmd command = commands.get(0);
        Assert.assertEquals(name, command.getName());
        Assert.assertEquals(value, command.getValue());
        SSLHostConfigPreSharedKey outputPreSharedKey = output.getPreSharedKeys().iterator().next();
        Assert.assertSame(output, outputPreSharedKey.getSSLHostConfig());
        Assert.assertEquals("test", outputPreSharedKey.getIdentity());
        Assert.assertEquals("00010203", outputPreSharedKey.getKey());
        Assert.assertEquals("SHA256", outputPreSharedKey.getDigest());
        Assert.assertArrayEquals(new byte[] { 0, 1, 2, 3 }, outputPreSharedKey.getKeyInternal());
        Assert.assertEquals(MessageDigest.SHA256, outputPreSharedKey.getDigestInternal());
    }
}

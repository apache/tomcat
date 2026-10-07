/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.realm;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.HexUtils;

public class TestJAASRealm extends TomcatBaseTest {

    private static final String ALGORITHM = "MD5";

    private static final String CONFIG =
            "CustomLogin {\n" +
            "    org.apache.catalina.realm.TesterLoginModule\n" +
            "    sufficient;\n" +
            "};";

    private static final String CONFIG_MEMORY =
            "MemoryLogin {\n" +
            "    org.apache.catalina.realm.JAASMemoryLoginModule\n" +
            "    sufficient pathname=\"tomcat-users-lm.xml\";\n" +
            "};";

    @Test
    public void testRealm() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // Write login config to the temp path
        File loginConfFile = new File(getTemporaryDirectory(), "customLoginConfig.conf");
        try (PrintWriter writer = new PrintWriter(loginConfFile)) {
            writer.write(CONFIG);
        }
        addDeleteOnTearDown(loginConfFile);

        JAASRealm jaasRealm = new JAASRealm();
        jaasRealm.setAppName("CustomLogin");
        jaasRealm.setCredentialHandler(new MessageDigestCredentialHandler());
        jaasRealm.setUserClassNames(TesterPrincipal.class.getName());
        jaasRealm.setRoleClassNames(TesterRolePrincipal.class.getName());
        jaasRealm.setConfigFile(loginConfFile.getAbsolutePath());
        Context context = tomcat.addContext("/jaastest", null);
        context.setRealm(jaasRealm);

        tomcat.start();

        Principal p = jaasRealm.authenticate("foo", "bar");
        Assert.assertNull(p);
        p = jaasRealm.authenticate("tomcatuser", "pass");
        Assert.assertNotNull(p);
        Assert.assertTrue(p instanceof GenericPrincipal);
        GenericPrincipal gp = (GenericPrincipal) p;
        Assert.assertTrue(gp.hasRole("role1"));
    }

    @Test
    public void testMemoryLoginModule() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File tomcatUsersXml = new File(getTemporaryDirectory(), "tomcat-users-lm.xml");
        try (PrintWriter writer = new PrintWriter(tomcatUsersXml)) {
            writer.write(TestMemoryRealm.CONFIG);
        }
        addDeleteOnTearDown(tomcatUsersXml);

        // Write login config to the temp path
        File loginConfFile = new File(getTemporaryDirectory(), "memoryLoginConfig.conf");
        try (PrintWriter writer = new PrintWriter(loginConfFile)) {
            String path = tomcatUsersXml.getAbsolutePath();
            if (File.separatorChar == '\\') {
                path = path.replace("\\", "\\\\");
            }
            writer.write(CONFIG_MEMORY.replace("tomcat-users-lm.xml", path));
        }
        addDeleteOnTearDown(loginConfFile);

        JAASRealm jaasRealm = new JAASRealm();
        jaasRealm.setAppName("MemoryLogin");
        jaasRealm.setCredentialHandler(new MessageDigestCredentialHandler());
        jaasRealm.setUserClassNames(GenericPrincipal.class.getName());
        jaasRealm.setRoleClassNames(GenericPrincipal.class.getName());
        jaasRealm.setConfigFile(loginConfFile.getAbsolutePath());
        Context context = tomcat.addContext("/jaastest", null);
        context.setRealm(jaasRealm);

        tomcat.start();

        Principal p = jaasRealm.authenticate("foo", "bar");
        Assert.assertNull(p);
        p = jaasRealm.authenticate("admin", "sekr3t");
        Assert.assertNotNull(p);
        Assert.assertTrue(p instanceof GenericPrincipal);
        GenericPrincipal gp = (GenericPrincipal) p;
        Assert.assertTrue(gp.hasRole("testrole"));
        gp.logout();
    }

    @Test
    public void testMemoryLoginModuleDigestAuth() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File tomcatUsersXml = new File(getTemporaryDirectory(), "tomcat-users-lm-digest.xml");
        try (PrintWriter writer = new PrintWriter(tomcatUsersXml)) {
            writer.write(TestMemoryRealm.CONFIG);
        }
        addDeleteOnTearDown(tomcatUsersXml);

        // Write login config to the temp path
        File loginConfFile = new File(getTemporaryDirectory(), "memoryLoginConfigDigest.conf");
        try (PrintWriter writer = new PrintWriter(loginConfFile)) {
            String path = tomcatUsersXml.getAbsolutePath();
            if (File.separatorChar == '\\') {
                path = path.replace("\\", "\\\\");
            }
            writer.write(CONFIG_MEMORY.replace("tomcat-users-lm.xml", path));
        }
        addDeleteOnTearDown(loginConfFile);

        JAASRealm jaasRealm = new JAASRealm();
        jaasRealm.setAppName("MemoryLogin");
        // Use the same algorithm for the realm credential handler as used for
        // the digest authentication
        MessageDigestCredentialHandler credentialHandler = new MessageDigestCredentialHandler();
        credentialHandler.setAlgorithm(ALGORITHM);
        jaasRealm.setCredentialHandler(credentialHandler);
        jaasRealm.setUserClassNames(GenericPrincipal.class.getName());
        jaasRealm.setRoleClassNames(GenericPrincipal.class.getName());
        jaasRealm.setConfigFile(loginConfFile.getAbsolutePath());
        Context context = tomcat.addContext("/jaastest", null);
        context.setRealm(jaasRealm);

        tomcat.start();

        String nonce = "test-nonce";
        String nc = "00000001";
        String cnonce = "test-cnonce";
        String qop = "auth";
        String realmName = "test-realm";

        MessageDigest md5 = MessageDigest.getInstance(ALGORITHM);
        String digestA2 = HexUtils.toHexString(md5.digest("GET:/jaastest".getBytes(StandardCharsets.UTF_8)));
        // digestA1 as the client computes it from the stored (clear text in
        // the login module's user file) password
        String digestA1 = HexUtils.toHexString(
                md5.digest(("admin:" + realmName + ":sekr3t").getBytes(StandardCharsets.UTF_8)));
        String clientDigest = HexUtils.toHexString(md5.digest(
                (digestA1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + digestA2)
                        .getBytes(StandardCharsets.UTF_8)));

        Principal p = jaasRealm.authenticate("admin", clientDigest.toLowerCase(Locale.ENGLISH), nonce, nc, cnonce,
                qop, realmName, digestA2, ALGORITHM);
        Assert.assertNotNull(p);
        Assert.assertTrue(p instanceof GenericPrincipal);
        GenericPrincipal gp = (GenericPrincipal) p;
        Assert.assertTrue(gp.hasRole("testrole"));
        gp.logout();
    }
}

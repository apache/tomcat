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
package org.apache.catalina.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;

public class TestPropertiesRoleMappingListener extends TomcatBaseTest {

    @Test(expected = NullPointerException.class)
    public void testNullRoleMappingFile() throws Exception {
        PropertiesRoleMappingListener listener = new PropertiesRoleMappingListener();
        listener.setRoleMappingFile(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyRoleMappingFile() throws Exception {
        PropertiesRoleMappingListener listener = new PropertiesRoleMappingListener();
        listener.setRoleMappingFile("");
    }

    @Test(expected = LifecycleException.class)
    public void testNotFoundRoleMappingFile() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        PropertiesRoleMappingListener listener = new PropertiesRoleMappingListener();
        ctx.addLifecycleListener(listener);

        try {
            tomcat.start();
        } finally {
            tomcat.stop();
        }
    }

    @Test
    public void testFileFromServletContext() throws Exception {
        doTest("webapp:/WEB-INF/role-mapping.properties", null);
    }

    @Test
    public void testFileFromServletContextWithKeyPrefix() throws Exception {
        doTest("webapp:/WEB-INF/prefixed-role-mapping.properties", "app-roles.");
    }

    @Test
    public void testFileFromClasspath() throws Exception {
        doTest("classpath:/com/example/role-mapping.properties", null);
    }

    @Test
    public void testFileFromClasspathWithKeyPrefix() throws Exception {
        doTest("classpath:/com/example/prefixed-role-mapping.properties", "app-roles.");
    }

    @Test
    public void testFileFromFile() throws Exception {
        File appDir = new File("test/webapp-role-mapping");
        File file = new File(appDir, "WEB-INF/role-mapping.properties");
        doTest(file.getAbsoluteFile().toURI().toASCIIString(), null);
    }

    @Test
    public void testFileFromFileWithKeyPrefix() throws Exception {
        File appDir = new File("test/webapp-role-mapping");
        File file = new File(appDir, "WEB-INF/prefixed-role-mapping.properties");
        doTest(file.getAbsoluteFile().toURI().toASCIIString(), "app-roles.");
    }

    private void doTest(String roleMappingFile, String keyPrefix) throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp-role-mapping");
        Context ctx = tomcat.addContext("", appDir.getAbsolutePath());

        PropertiesRoleMappingListener listener = new PropertiesRoleMappingListener();
        listener.setRoleMappingFile(roleMappingFile);
        listener.setKeyPrefix(keyPrefix);
        ctx.addLifecycleListener(listener);

        Tomcat.addServlet(ctx, "default", new DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        LoginConfig loginConfig  = new LoginConfig();
        loginConfig.setAuthMethod(HttpServletRequest.BASIC_AUTH);
        ctx.setLoginConfig(loginConfig);
        ctx.getPipeline().addValve(new BasicAuthenticator());

        TesterMapRealm realm = new TesterMapRealm();
        realm.addUser("foo", "bar");
        // role 'admin'
        realm.addUserRole("foo", "de25f8f5-e534-4980-9351-e316384b1127");
        realm.addUser("waldo", "fred");
        // role 'user'
        realm.addUserRole("waldo", "13f6b886-cba8-4b5b-9a1b-06a6fe533356");
        // role 'supervisor'
        realm.addUserRole("waldo", "45071e9a-13ef-11ee-89dc-20677cd45840");
        ctx.setRealm(realm);

        for (String role : Arrays.asList("admin", "user", "unmapped")) {
            SecurityCollection securityCollection = new SecurityCollection();
            securityCollection.addPattern("/" + role + ".txt");
            SecurityConstraint constraint = new SecurityConstraint();
            constraint.addAuthRole(role);
            constraint.addCollection(securityCollection);
            ctx.addConstraint(constraint);
            ctx.addSecurityRole(role);
        }

        tomcat.start();

        testRequest("foo:bar", "/admin.txt", 200);
        testRequest("waldo:fred", "/user.txt", 200);
        testRequest("waldo:fred", "/unmapped.txt", 403);
        testRequest("bar:baz", "/user.txt", 401);
    }

    private void testRequest(String credentials, String path, int statusCode) throws IOException {
        ByteChunk out = new ByteChunk();
        Map<String, List<String>> reqHead = new HashMap<>();
        List<String> head = new ArrayList<>();
        head.add(HttpServletRequest.BASIC_AUTH + " " +
                Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.ISO_8859_1)));
        reqHead.put("Authorization", head);
        int rc = getUrl("http://localhost:" + getPort() + path, out, reqHead, null);
        Assert.assertEquals(statusCode, rc);
    }

}

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
package org.apache.catalina.startup;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.authenticator.TestBasicAuthParser.BasicAuthHeader;
import org.apache.catalina.filters.Constants;

/**
 * Tests that verify Tomcat functions correctly without clustering JARs
 * (catalina-ha.jar and catalina-tribes.jar). Uses a filtering classloader
 * on the webapp/engine to hide clustering packages from the components
 * under test.
 */
public class TestWithoutCluster extends TomcatBaseTest {

    /**
     * Verify that Catalina starts up and serves requests when clustering
     * classes are not available. The addClusterRuleSet() method should
     * catch the ClassNotFoundException and log rather than failing.
     */
    @Test
    public void testCatalinaStartsWithoutClustering() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.getEngine().setParentClassLoader(
                new NoClusterClassLoader(getClass().getClassLoader()));

        Context ctx = tomcat.addContext("", null);
        Tomcat.addServlet(ctx, "default",
                new org.apache.catalina.servlets.DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        SimpleHttpClient client = new SimpleHttpClient() {
            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(getPort());

        // @formatter:off
        client.setRequest(new String[] {
                "GET / HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        // DefaultServlet returns 404 for root with no welcome file, but the
        // key assertion is that Tomcat started successfully and can serve
        // requests - it did not fail during startup due to missing clustering.
        Assert.assertTrue("Server should respond, got: " +
                        client.getStatusCode(),
                client.getStatusCode() == HttpServletResponse.SC_OK ||
                        client.getStatusCode() ==
                                HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Verify that sessionsList.jsp compiles and renders when clustering
     * classes are not available. The JSP uses reflection to load DeltaSession
     * so it should gracefully fall back.
     */
    @Test
    public void testSessionsListJspWithoutClustering() throws Exception {
        ignoreTearDown = true;
        Tomcat tomcat = getTomcatInstance();
        tomcat.addUser("admin", "sekr3t");
        tomcat.addRole("admin", "manager-gui");

        File webappDir = new File(getBuildDirectory(), "webapps");
        File appDir = new File(webappDir, "manager");
        Context managerCtx = tomcat.addWebapp(null, "/manager",
                appDir.getAbsolutePath());
        managerCtx.setParentClassLoader(
                new NoClusterClassLoader(getClass().getClassLoader()));

        Context ctx = tomcat.addContext("/testapp", null);
        Tomcat.addServlet(ctx, "default",
                new org.apache.catalina.servlets.DefaultServlet());
        ctx.addServletMappingDecoded("/", "default");

        tomcat.start();

        SimpleHttpClient client = new SimpleHttpClient() {
            @Override
            public boolean isResponseBodyOK() {
                return true;
            }
        };
        client.setPort(getPort());
        String basicHeader = (new BasicAuthHeader("Basic", "admin",
                "sekr3t")).getHeader().toString();

        // Hit the HTML manager entry point to get a session and CSRF nonce
        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/html HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());

        String body = client.getResponseBody();
        Pattern noncePattern = Pattern.compile(
                Pattern.quote(Constants.CSRF_NONCE_REQUEST_PARAM)
                        + "=([A-F0-9]+)");
        Matcher m = noncePattern.matcher(body);
        Assert.assertTrue(
                "CSRF nonce not found in manager HTML response", m.find());
        String nonce = m.group(1);

        String sessionCookie = null;
        for (String header : client.getResponseHeaders()) {
            if (header.startsWith("Set-Cookie:")) {
                String cookieValue =
                        header.substring("Set-Cookie:".length()).trim();
                sessionCookie = cookieValue.split(";")[0];
                break;
            }
        }
        Assert.assertNotNull("Session cookie not found", sessionCookie);

        // Access sessions list page with the CSRF nonce
        // @formatter:off
        client.setRequest(new String[] {
                "GET /manager/html/sessions?path=/testapp&" +
                    Constants.CSRF_NONCE_REQUEST_PARAM + "=" + nonce +
                    " HTTP/1.1" + CRLF +
                    "Host: localhost" + CRLF +
                    "Authorization: " + basicHeader + CRLF +
                    "Cookie: " + sessionCookie + CRLF +
                    "Connection: Close" + CRLF +
                    CRLF
                });
        // @formatter:on
        client.connect();
        client.processRequest(true);
        Assert.assertEquals(HttpServletResponse.SC_OK, client.getStatusCode());
        Assert.assertTrue(client.getResponseBody()
                .contains("Sessions Administration"));
        Assert.assertTrue(client.getResponseBody()
                .contains("active Sessions"));
    }

    /**
     * Verify that StandardEngineSF loads without error when clustering
     * classes are not available. The clusterValveClass field should be null.
     */
    @Test
    public void testStandardEngineSFWithoutClustering() throws Exception {
        String classDir = System.getProperty("tomcat.test.classpath",
                "output/classes");

        ClassLoader cl = new StoreConfigIsolatingClassLoader(
                getClass().getClassLoader(), classDir);
        Class<?> engineSF = cl.loadClass(
                "org.apache.catalina.storeconfig.StandardEngineSF");

        Field f = engineSF.getDeclaredField("clusterValveClass");
        f.setAccessible(true);
        Assert.assertNull("clusterValveClass should be null without " +
                "clustering classes", f.get(null));
    }

    /**
     * Verify that StandardHostSF loads without error when clustering
     * classes are not available. The clusterValveClass field should be null.
     */
    @Test
    public void testStandardHostSFWithoutClustering() throws Exception {
        String classDir = System.getProperty("tomcat.test.classpath",
                "output/classes");

        ClassLoader cl = new StoreConfigIsolatingClassLoader(
                getClass().getClassLoader(), classDir);
        Class<?> hostSF = cl.loadClass(
                "org.apache.catalina.storeconfig.StandardHostSF");

        Field f = hostSF.getDeclaredField("clusterValveClass");
        f.setAccessible(true);
        Assert.assertNull("clusterValveClass should be null without " +
                "clustering classes", f.get(null));
    }

    /**
     * Verify that StoreRegistry.getInterfaces() returns only the 10
     * core interfaces when clustering classes are not available.
     */
    @Test
    public void testStoreRegistryWithoutClustering() throws Exception {
        String classDir = System.getProperty("tomcat.test.classpath",
                "output/classes");

        ClassLoader cl = new StoreConfigIsolatingClassLoader(
                getClass().getClassLoader(), classDir);
        Class<?> registryClass = cl.loadClass(
                "org.apache.catalina.storeconfig.StoreRegistry");

        Object registry = registryClass.getDeclaredConstructor().newInstance();

        // getInterfaces() is private static, access via reflection
        java.lang.reflect.Method getInterfaces =
                registryClass.getDeclaredMethod("getInterfaces");
        getInterfaces.setAccessible(true);
        Class<?>[] result = (Class<?>[]) getInterfaces.invoke(registry);

        Assert.assertEquals("Should have exactly 10 core interfaces " +
                "when clustering is absent", 10, result.length);

        for (Class<?> iface : result) {
            Assert.assertFalse("No clustering interface should be present: " +
                    iface.getName(),
                    iface.getName().startsWith("org.apache.catalina.ha.") ||
                    iface.getName().startsWith(
                            "org.apache.catalina.tribes."));
        }
    }

    /**
     * A classloader that loads storeconfig classes in isolation (child-first)
     * while blocking clustering packages. This causes
     * SomeClass.class.getClassLoader() to return this classloader, so
     * Class.forName calls in static initializers go through us.
     */
    private static class StoreConfigIsolatingClassLoader
            extends ClassLoader {

        private final String classDir;

        StoreConfigIsolatingClassLoader(ClassLoader parent,
                String classDir) {
            super(parent);
            this.classDir = classDir;
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith("org.apache.catalina.ha.") ||
                    name.startsWith("org.apache.catalina.tribes.")) {
                throw new ClassNotFoundException(name);
            }

            // Load storeconfig classes child-first so their
            // getClassLoader() returns this instance
            if (name.startsWith(
                    "org.apache.catalina.storeconfig.")) {
                Class<?> c = findLoadedClass(name);
                if (c != null) {
                    return c;
                }
                String path = classDir + "/" +
                        name.replace('.', '/') + ".class";
                try {
                    byte[] bytes = Files.readAllBytes(
                            Paths.get(path));
                    c = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }

            return super.loadClass(name, resolve);
        }
    }

    /**
     * A classloader that hides clustering packages to simulate a deployment
     * without catalina-ha.jar and catalina-tribes.jar.
     */
    private static class NoClusterClassLoader extends ClassLoader {

        NoClusterClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith("org.apache.catalina.ha.") ||
                    name.startsWith("org.apache.catalina.tribes.")) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}

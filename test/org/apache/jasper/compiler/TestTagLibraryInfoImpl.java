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
package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.jasper.servlet.JspServlet;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.scan.StandardJarScanFilter;
import org.apache.tomcat.util.scan.StandardJarScanner;

/**
 * Test case for {@link TagLibraryInfoImpl}.
 */
public class TestTagLibraryInfoImpl extends TomcatBaseTest {

    @Test
    public void testRelativeTldLocation() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/test.jsp", res, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=64373
     */
    @Test
    public void testTldFromExplodedWar() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();

        int rc = getUrl("http://localhost:" + getPort() + "/test/bug6nnnn/bug64373.jsp", res, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=70001
     *
     * Verify that taglib directives referencing a TLD in a JAR that is outside
     * the web application (i.e. on the classpath but not in WEB-INF/lib) produce
     * a stable, environment-independent key in the generated servlet's
     * {@code _jspx_dependants} map.
     *
     * Before the fix, the key was an absolute {@code jar:file:/...} URL that
     * encoded the build-environment-specific JAR location, making JSP compilation
     * non-deterministic.  After the fix the key must use the {@code "uri:"} prefix
     * followed by the taglib URI from the JSP directive.
     */
    @Test
    public void testExternalTaglibDependantUsesUri() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File("test/webapp");
        Context ctx = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        StandardJarScanner scanner = (StandardJarScanner) ctx.getJarScanner();
        StandardJarScanFilter filter = (StandardJarScanFilter) scanner.getJarScanFilter();
        filter.setTldSkip(filter.getTldSkip() + ",testclasses");
        filter.setPluggabilitySkip(filter.getPluggabilitySkip() + ",testclasses");

        // Add a JAR containing the test TLD to the *parent* classloader rather
        // than to WEB-INF/lib. The TLD scanner then sees it as an external JAR
        // (TldResourcePath.getWebappPath() == null), which is the code path that
        // the fix for non-deterministic _jspx_dependants addresses.
        File jar = createExternalTaglibJar();
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        ctx.setParentClassLoader(new URLClassLoader(new URL[] { jar.toURI().toURL() }, parent));

        tomcat.start();

        ByteChunk body = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/test/jsp/generator/external-taglib.jsp", body, null);
        Assert.assertEquals(body.toString(), HttpServletResponse.SC_OK, rc);

        // Retrieve the _jspx_dependants map from the compiled servlet via the
        // JspServletWrapper.
        Context webCtx = (Context) tomcat.getHost().findChild("/test");
        Wrapper jspWrapper = (Wrapper) webCtx.findChild("jsp");
        JspServlet jspServlet = (JspServlet) jspWrapper.getServlet();
        Field rctxtField = JspServlet.class.getDeclaredField("rctxt");
        rctxtField.setAccessible(true);
        JspRuntimeContext rctxt = (JspRuntimeContext) rctxtField.get(jspServlet);
        Map<String,Long> dependants = rctxt.getWrapper(
                "/jsp/generator/external-taglib.jsp").getDependants();

        Assert.assertNotNull("Expected non-null _jspx_dependants map", dependants);

        // No key in _jspx_dependants should be an absolute file/jar URL.
        // Such URLs embed environment-specific paths and make JSP compilation
        // non-deterministic.
        for (String key : dependants.keySet()) {
            Assert.assertFalse(
                    "_jspx_dependants must not contain absolute paths for external taglib JARs, got: " + key,
                    key.startsWith("jar:file:") || key.startsWith("file:"));
        }

        // The external taglib JAR and its TLD entry must each be recorded with
        // a stable "uri:" key rather than an absolute path.
        Assert.assertTrue(
                "Expected 'uri:http://tomcat.apache.org/test/external-taglib' key in _jspx_dependants",
                dependants.containsKey("uri:http://tomcat.apache.org/test/external-taglib"));
        Assert.assertTrue(
                "Expected 'uri:http://tomcat.apache.org/test/external-taglib!/META-INF/external-taglib-test.tld'" +
                        " key in _jspx_dependants",
                dependants.containsKey(
                        "uri:http://tomcat.apache.org/test/external-taglib!/META-INF/external-taglib-test.tld"));
    }

    /**
     * Creates a temporary JAR containing a minimal TLD with URI
     * {@code http://tomcat.apache.org/test/external-taglib}.  The TLD has no
     * validator and no tag-handler classes so the JAR itself is the only
     * dependency required to compile a JSP that references it.
     */
    private static File createExternalTaglibJar() throws Exception {
        String tld =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<taglib xmlns=\"http://java.sun.com/xml/ns/javaee\"\n" +
                "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "        xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee " +
                        "http://java.sun.com/xml/ns/javaee/web-jsptaglibrary_2_1.xsd\"\n" +
                "        version=\"2.1\">\n" +
                "    <tlib-version>1.0</tlib-version>\n" +
                "    <short-name>ext</short-name>\n" +
                "    <uri>http://tomcat.apache.org/test/external-taglib</uri>\n" +
                "</taglib>\n";

        File jar = File.createTempFile("external-taglib-test", ".jar");
        jar.deleteOnExit();

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            jos.putNextEntry(new JarEntry("META-INF/external-taglib-test.tld"));
            jos.write(tld.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }

        return jar;
    }
}

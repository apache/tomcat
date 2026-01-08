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

package org.apache.tomcat.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import static org.apache.catalina.startup.SimpleHttpClient.CRLF;
import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.authenticator.FormAuthenticator;
import org.apache.catalina.filters.FailedRequestFilter;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.apache.tomcat.util.http.Method;

public class TestSecurity2023 extends TomcatBaseTest {
    /*
     * https://www.cve.org/CVERecord?id=CVE-2023-24998
     *
     * Fixed in
     * 11.0.0-M3  https://github.com/apache/tomcat/commit/063e2e81ede50c287f737cc8e2915ce7217e886e
     * 10.1.5 https://github.com/apache/tomcat/commit/8a2285f13affa961cc65595aad999db5efae45ce
     * 9.0.71 https://github.com/apache/tomcat/commit/cf77cc545de0488fb89e24294151504a7432df74
     *
     * https://www.cve.org/CVERecord?id=CVE-2023-28709
     *
     * Fixed in
     * 11.0.0-M5  https://github.com/apache/tomcat/commit/d53d8e7f77042cc32a3b98f589496a1ef5088e38
     * 10.1.8 https://github.com/apache/tomcat/commit/ba848da71c523d94950d3c53c19ea155189df9dc
     * 9.0.74 https://github.com/apache/tomcat/commit/fbd81421629afe8b8a3922d59020cde81caea861
     */
    @Test
    public void testCVE_2023_24998_CVE_2023_28709() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setMaxParameterCount(2);
        Context context = tomcat.addContext("", null);
        Wrapper wrapper = Tomcat.addServlet(context, "test", new TestServlet());
        wrapper.setMultipartConfigElement(new MultipartConfigElement(""));
        context.addServletMappingDecoded("/", "test");

        FilterDef filterDef = new FilterDef();
        filterDef.setFilterName("FailedRequestFilter");
        filterDef.setFilterClass(FailedRequestFilter.class.getName());
        context.addFilterDef(filterDef);
        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(filterDef.getFilterName());
        filterMap.addURLPatternDecoded("*");
        context.addFilterMap(filterMap);

        tomcat.start();

        final String path = "http://localhost:" + getPort() + "/";
        int status = postMultipart(path, null, 1);
        Assert.assertEquals(HttpServletResponse.SC_OK, status);

        status = postMultipart(path, null, 3);
        Assert.assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, status);

        status = postMultipart(path,"x=1", 1);
        Assert.assertEquals(HttpServletResponse.SC_OK, status);

        status = postMultipart(path, "x=1&y=2", 1);
        Assert.assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, status);
    }

    /*
     * https://www.cve.org/CVERecord?id=CVE-2023-41080
     *
     * Fixed in
     * 11.0.0-M11  https://github.com/apache/tomcat/commit/e3703c9abb8fe0d5602f6ba8a8f11d4b6940815a
     * 10.1.13 https://github.com/apache/tomcat/commit/bb4624a9f3e69d495182ebfa68d7983076407a27
     * 9.0.80 https://github.com/apache/tomcat/commit/77c0ce2d169efa248b64b992e547aad549ec906b
     */
    @Test
    public void testCVE_2023_41080() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        Context context = tomcat.addContext("", null);

        LoginConfig loginConfig = new LoginConfig();
        loginConfig.setAuthMethod("FORM");
        loginConfig.setLoginPage("/login");
        context.setLoginConfig(loginConfig);
        context.getPipeline().addValve(new FormAuthenticator());

        SecurityConstraint securityConstraint = new SecurityConstraint();
        securityConstraint.addAuthRole("admin");
        SecurityCollection securityCollection = new SecurityCollection();
        securityCollection.addPattern("/secret.html");
        securityCollection.addPattern("/example.com");
        securityConstraint.addCollection(securityCollection);
        context.addConstraint(securityConstraint);

        tomcat.addUser("admin", "admin");
        tomcat.addRole("admin", "admin");

        Tomcat.addServlet(context, "login", new TestServlet());
        context.addServletMappingDecoded("/login", "login");
        Tomcat.addServlet(context, "secret", new TestServlet());
        context.addServletMappingDecoded("/secret.html", "secret");
        Tomcat.addServlet(context, "example", new TestServlet());
        context.addServletMappingDecoded("/example.com", "example");

        tomcat.start();

        String location = doFormLoginAndGetRedirectLocation("http://localhost:" + getPort(), "/secret.html;@example.com");
        Assert.assertNotNull(location);
        Assert.assertFalse(location.startsWith("//"));
        URI locationUri = new URI(location);
        Assert.assertNull(locationUri.getHost());
        Assert.assertTrue(locationUri.getPath().contains("secret.html"));

        location = doFormLoginAndGetRedirectLocation("http://localhost:" + getPort(), "//example.com");
        Assert.assertNotNull(location);
        Assert.assertFalse(location.startsWith("//"));
    }

    private static String doFormLoginAndGetRedirectLocation(String basePath, String targetPath) throws Exception {
        String url = basePath + targetPath;
        Map<String, List<String>> resHead = new HashMap<>();
        int rc = getUrl(url, new ByteChunk(), resHead);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);

        String sessionCookie = null;
        List<String> cookies = resHead.get("Set-Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("JSESSIONID=")) {
                    sessionCookie = cookie.split(";")[0];
                    break;
                }
            }
        }
        Assert.assertNotNull("JSESSIONID not found in response", sessionCookie);
        String loginUrl = basePath + "/j_security_check";
        HttpURLConnection conn = (HttpURLConnection) new URI(loginUrl).toURL().openConnection();
        conn.setRequestMethod(Method.POST);
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false); // want to check the redirect location manually
        conn.setRequestProperty("Cookie", sessionCookie);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        String params = "j_username=admin&j_password=admin";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(params.getBytes(StandardCharsets.UTF_8));
        }
        int loginRc = conn.getResponseCode();
        Assert.assertEquals(HttpServletResponse.SC_SEE_OTHER, loginRc);
        return conn.getHeaderField("Location");
    }

    private static int postMultipart(String path, String queryStringParams, int parts) throws IOException, URISyntaxException {
        String urlStr = path + (queryStringParams == null || queryStringParams.isEmpty() ? "" : "?" + queryStringParams);
        String boundary = "--simpleboundary";

        byte[] body = buildMultipartBody(boundary, parts);
        HttpURLConnection conn = (HttpURLConnection) new URI(urlStr).toURL().openConnection();
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));
        conn.setReadTimeout(1000000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int rc = conn.getResponseCode();
        InputStream inputStream = null;
        if (rc == HttpServletResponse.SC_OK) {
            inputStream = conn.getInputStream();
        } else if (rc == HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE) {
            inputStream = conn.getErrorStream();
        }
        if (inputStream != null) {
            //noinspection StatementWithEmptyBody
            while (inputStream.read() != -1) {}
            inputStream.close();
        }

        return rc;
    }

    private static byte[] buildMultipartBody(String boundary, int parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts; i++) {
            sb.append("--");
            sb.append(boundary);
            sb.append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"part\"");
            sb.append(CRLF);
            sb.append(CRLF);
            sb.append("bodyOfPart");
            sb.append(CRLF);
        }
        sb.append("--");
        sb.append(boundary);
        sb.append("--");
        sb.append(CRLF);
        return sb.toString().getBytes();
    }

    public static class TestServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
            req.getParameterMap();
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        }
    }
}

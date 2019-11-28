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

package org.apache.catalina.filters;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.unittest.TesterContext;
import org.apache.tomcat.unittest.TesterResponse;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class TestRemoteIpFilter extends TomcatBaseTest {

    /**
     * Mock {@link FilterChain} to keep a handle on the passed
     * {@link ServletRequest} and (@link ServletResponse}.
     */
    public static class MockFilterChain implements FilterChain {
        private HttpServletRequest request;
        private HttpServletResponse response;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            this.request = (HttpServletRequest) request;
            this.response = (HttpServletResponse) response;
        }

        public HttpServletRequest getRequest() {
            return request;
        }

        public HttpServletResponse getResponse() {
            return response;
        }
    }

    public static class MockHttpServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private transient HttpServletRequest request;

        public HttpServletRequest getRequest() {
            return request;
        }

        @Override
        public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            this.request = request;
            PrintWriter writer = response.getWriter();

            writer.println("request.remoteAddr=" + request.getRemoteAddr());
            writer.println("request.remoteHost=" + request.getRemoteHost());
            writer.println("request.secure=" + request.isSecure());
            writer.println("request.scheme=" + request.getScheme());
            writer.println("request.serverName=" + request.getServerName());
            writer.println("request.serverPort=" + request.getServerPort());

            writer.println();
            for (Enumeration<String> headers = request.getHeaderNames(); headers.hasMoreElements();) {
                String name = headers.nextElement().toString();
                writer.println("request.header['" + name + "']=" + Collections.list(request.getHeaders(name)));
            }
        }
    }

    /**
     * Enhanced {@link Request} to ease testing.
     */
    public static class MockHttpServletRequest extends Request {
        public MockHttpServletRequest() {
            super(new Connector());
            setCoyoteRequest(new org.apache.coyote.Request());
        }

        public void setHeader(String name, String value) {
            getCoyoteRequest().getMimeHeaders().setValue(name).setString(value);
        }

        public void addHeader(String name, String value) {
            getCoyoteRequest().getMimeHeaders().addValue(name).setString(value);
        }

        public void setScheme(String scheme) {
            getCoyoteRequest().scheme().setString(scheme);
        }

        @Override
        public void setAttribute(String name, Object value) {
            getCoyoteRequest().getAttributes().put(name, value);
        }

        @Override
        public Object getAttribute(String name) {
            return getCoyoteRequest().getAttributes().get(name);
        }

        @Override
        public String getServerName() {
            return "localhost";
        }

        @Override
        public Context getContext() {
            // Lazt init
            if (super.getContext() == null) {
                getMappingData().context = new TesterContext();
            }
            return super.getContext();
        }
    }

    public static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    @Test
    public void testCommaDelimitedListToStringArray() {
        List<String> elements = Arrays.asList("element1", "element2", "element3");
        String actual = RemoteIpFilter.listToCommaDelimitedString(elements);
        Assert.assertEquals("element1, element2, element3", actual);
    }

    @Test
    public void testCommaDelimitedListToStringArrayEmptyList() {
        List<String> elements = new ArrayList<>();
        String actual = RemoteIpFilter.listToCommaDelimitedString(elements);
        Assert.assertEquals("", actual);
    }

    @Test
    public void testCommaDelimitedListToStringArrayNullList() {
        String actual = RemoteIpFilter.listToCommaDelimitedString(null);
        Assert.assertEquals("", actual);
    }

    @Test
    public void testHeaderNamesCaseInsensitivity() {
        RemoteIpFilter.XForwardedRequest request = new RemoteIpFilter.XForwardedRequest(new MockHttpServletRequest());
        request.setHeader("myheader", "lower Case");
        request.setHeader("MYHEADER", "UPPER CASE");
        request.setHeader("MyHeader", "Camel Case");
        Assert.assertEquals(1, request.headers.size());
        Assert.assertEquals("Camel Case", request.getHeader("myheader"));
    }

    @Test
    public void testIncomingRequestIsSecuredButProtocolHeaderSaysItIsNotWithCustomValues() throws Exception {
        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("protocolHeader", "x-forwarded-proto");
        filterDef.addInitParameter("remoteIpHeader", "x-my-forwarded-for");
        filterDef.addInitParameter("httpServerPort", "8080");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.10");
        request.setSecure(true);
        request.setScheme("https");
        request.setHeader("x-my-forwarded-for", "140.211.11.130");
        request.setHeader("x-forwarded-proto", "http");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        boolean actualSecure = actualRequest.isSecure();
        Assert.assertFalse("request must be unsecured as header x-forwarded-proto said it is http", actualSecure);

        String actualScheme = actualRequest.getScheme();
        Assert.assertEquals("scheme must be http as header x-forwarded-proto said it is http", "http", actualScheme);

        int actualServerPort = actualRequest.getServerPort();
        Assert.assertEquals("wrong http server port", 8080, actualServerPort);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }

    @Test
    public void testIncomingRequestIsSecuredButProtocolHeaderSaysItIsNotWithDefaultValues() throws Exception {
        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("protocolHeader", "x-forwarded-proto");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.10");
        request.setSecure(true);
        request.setScheme("https");
        request.setHeader("x-forwarded-for", "140.211.11.130");
        request.setHeader("x-forwarded-proto", "http");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        boolean actualSecure = actualRequest.isSecure();
        Assert.assertFalse("request must be unsecured as header x-forwarded-proto said it is http", actualSecure);

        String actualScheme = actualRequest.getScheme();
        Assert.assertEquals("scheme must be http as header x-forwarded-proto said it is http", "http", actualScheme);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

    }

    @Test
    public void testInvokeAllowedRemoteAddrWithNullRemoteIpHeader() throws Exception {
        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("internalProxies", "192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        filterDef.addInitParameter("trustedProxies", "proxy1|proxy2|proxy3");
        filterDef.addInitParameter("remoteIpHeader", "x-forwarded-for");
        filterDef.addInitParameter("proxiesHeader", "x-forwarded-by");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        String actualXForwardedFor = request.getHeader("x-forwarded-for");
        Assert.assertNull("x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = request.getHeader("x-forwarded-by");
        Assert.assertNull("x-forwarded-by must be null", actualXForwardedBy);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "192.168.0.10", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "remote-host-original-value", actualRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreInternal() throws Exception {

        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("internalProxies", "192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        filterDef.addInitParameter("trustedProxies", "proxy1|proxy2|proxy3");
        filterDef.addInitParameter("remoteIpHeader", "x-forwarded-for");
        filterDef.addInitParameter("proxiesHeader", "x-forwarded-by");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.addHeader("x-forwarded-for", "140.211.11.130, 192.168.0.10, 192.168.0.11");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        String actualXForwardedFor = actualRequest.getHeader("x-forwarded-for");
        Assert.assertNull("all proxies are internal, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = actualRequest.getHeader("x-forwarded-by");
        Assert.assertNull("all proxies are internal, x-forwarded-by must be null", actualXForwardedBy);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreTrusted() throws Exception {

        // PREPARE
        RemoteIpFilter remoteIpFilter = new RemoteIpFilter();
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("internalProxies", "192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        filterDef.addInitParameter("trustedProxies", "proxy1|proxy2|proxy3");
        filterDef.addInitParameter("remoteIpHeader", "x-forwarded-for");
        filterDef.addInitParameter("proxiesHeader", "x-forwarded-by");

        filterDef.setFilter(remoteIpFilter);
        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.setHeader("x-forwarded-for", "140.211.11.130, proxy1, proxy2");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        String actualXForwardedFor = actualRequest.getHeader("x-forwarded-for");
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = actualRequest.getHeader("x-forwarded-by");
        Assert.assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1, proxy2", actualXForwardedBy);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreTrustedEmptyInternal() throws Exception {

        // PREPARE
        RemoteIpFilter remoteIpFilter = new RemoteIpFilter();
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("internalProxies", "");
        filterDef.addInitParameter("trustedProxies", "proxy1|proxy2|proxy3");
        filterDef.addInitParameter("remoteIpHeader", "x-forwarded-for");
        filterDef.addInitParameter("proxiesHeader", "x-forwarded-by");

        filterDef.setFilter(remoteIpFilter);
        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setRemoteAddr("proxy3");
        request.setRemoteHost("remote-host-original-value");
        request.setHeader("x-forwarded-for", "140.211.11.130, proxy1, proxy2");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        String actualXForwardedFor = actualRequest.getHeader("x-forwarded-for");
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = actualRequest.getHeader("x-forwarded-by");
        Assert.assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1, proxy2, proxy3", actualXForwardedBy);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreTrustedUnusedInternal() throws Exception {

        // PREPARE
        RemoteIpFilter remoteIpFilter = new RemoteIpFilter();
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("trustedProxies", "proxy1|proxy2|proxy3");
        filterDef.addInitParameter("remoteIpHeader", "x-forwarded-for");
        filterDef.addInitParameter("proxiesHeader", "x-forwarded-by");

        filterDef.setFilter(remoteIpFilter);
        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setRemoteAddr("proxy3");
        request.setRemoteHost("remote-host-original-value");
        request.setHeader("x-forwarded-for", "140.211.11.130, proxy1, proxy2");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        String actualXForwardedFor = actualRequest.getHeader("x-forwarded-for");
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = actualRequest.getHeader("x-forwarded-by");
        Assert.assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1, proxy2, proxy3", actualXForwardedBy);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreTrustedAndRemoteAddrMatchRegexp() throws Exception {

        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("internalProxies", "127\\.0\\.0\\.1|192\\.168\\..*|another-internal-proxy");
        filterDef.addInitParameter("trustedProxies", "proxy1|proxy2|proxy3");
        filterDef.addInitParameter("remoteIpHeader", "x-forwarded-for");
        filterDef.addInitParameter("proxiesHeader", "x-forwarded-by");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.addHeader("x-forwarded-for", "140.211.11.130");
        request.addHeader("x-forwarded-for", "proxy1");
        request.addHeader("x-forwarded-for", "proxy2");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        String actualXForwardedFor = actualRequest.getHeader("x-forwarded-for");
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = actualRequest.getHeader("x-forwarded-by");
        Assert.assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1, proxy2", actualXForwardedBy);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreTrustedOrInternal() throws Exception {

        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("internalProxies", "192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        filterDef.addInitParameter("trustedProxies", "proxy1|proxy2|proxy3");
        filterDef.addInitParameter("remoteIpHeader", "x-forwarded-for");
        filterDef.addInitParameter("proxiesHeader", "x-forwarded-by");

        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.setHeader("x-forwarded-for", "140.211.11.130, proxy1, proxy2, 192.168.0.10, 192.168.0.11");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        String actualXForwardedFor = actualRequest.getHeader("x-forwarded-for");
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = actualRequest.getHeader("x-forwarded-by");
        Assert.assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1, proxy2", actualXForwardedBy);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);
    }

    @Test
    public void testInvokeNotAllowedRemoteAddr() throws Exception {
        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("internalProxies", "192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        filterDef.addInitParameter("trustedProxies", "proxy1|proxy2|proxy3");
        filterDef.addInitParameter("remoteIpHeader", "x-forwarded-for");
        filterDef.addInitParameter("proxiesHeader", "x-forwarded-by");

        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setRemoteAddr("not-allowed-internal-proxy");
        request.setRemoteHost("not-allowed-internal-proxy-host");
        request.setHeader("x-forwarded-for", "140.211.11.130, proxy1, proxy2");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        String actualXForwardedFor = actualRequest.getHeader("x-forwarded-for");
        Assert.assertEquals("x-forwarded-for must be unchanged", "140.211.11.130, proxy1, proxy2", actualXForwardedFor);

        String actualXForwardedBy = actualRequest.getHeader("x-forwarded-by");
        Assert.assertNull("x-forwarded-by must be null", actualXForwardedBy);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "not-allowed-internal-proxy", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "not-allowed-internal-proxy-host", actualRemoteHost);
    }

    @Test
    public void testInvokeUntrustedProxyInTheChain() throws Exception {
        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("internalProxies", "192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        filterDef.addInitParameter("trustedProxies", "proxy1|proxy2|proxy3");
        filterDef.addInitParameter("remoteIpHeader", "x-forwarded-for");
        filterDef.addInitParameter("proxiesHeader", "x-forwarded-by");

        MockHttpServletRequest request = new MockHttpServletRequest();

        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.setHeader("x-forwarded-for", "140.211.11.130, proxy1, untrusted-proxy, proxy2");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        String actualXForwardedFor = actualRequest.getHeader("x-forwarded-for");
        Assert.assertEquals("ip/host before untrusted-proxy must appear in x-forwarded-for", "140.211.11.130, proxy1", actualXForwardedFor);

        String actualXForwardedBy = actualRequest.getHeader("x-forwarded-by");
        Assert.assertEquals("ip/host after untrusted-proxy must appear in  x-forwarded-by", "proxy2", actualXForwardedBy);

        String actualRemoteAddr = actualRequest.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "untrusted-proxy", actualRemoteAddr);

        String actualRemoteHost = actualRequest.getRemoteHost();
        Assert.assertEquals("remoteHost", "untrusted-proxy", actualRemoteHost);
    }

    @Test
    public void testInvokeXforwardedHost() throws Exception {
        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("hostHeader", "x-forwarded-host");
        filterDef.addInitParameter("portHeader", "x-forwarded-port");
        filterDef.addInitParameter("protocolHeader", "x-forwarded-proto");

        MockHttpServletRequest request = new MockHttpServletRequest();
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        // protocol
        request.setSecure(false);
        request.setServerPort(8080);
        request.setScheme("http");
        // host and port
        request.getCoyoteRequest().serverName().setString("10.0.0.1");
        request.setHeader("x-forwarded-host", "example.com");
        request.setHeader("x-forwarded-port", "8443");
        request.setHeader("x-forwarded-proto", "https");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        // protocol
        String actualServerName = actualRequest.getServerName();
        Assert.assertEquals("postInvoke serverName", "example.com", actualServerName);

        String actualScheme = actualRequest.getScheme();
        Assert.assertEquals("postInvoke scheme", "https", actualScheme);

        int actualServerPort = actualRequest.getServerPort();
        Assert.assertEquals("postInvoke serverPort", 8443, actualServerPort);

        boolean actualSecure = actualRequest.isSecure();
        Assert.assertTrue("postInvoke secure", actualSecure);
    }

    @Test
    public void testInvokeXforwardedHostAndPort() throws Exception {
        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("hostHeader", "x-forwarded-host");
        filterDef.addInitParameter("portHeader", "x-forwarded-port");
        filterDef.addInitParameter("protocolHeader", "x-forwarded-proto");

        MockHttpServletRequest request = new MockHttpServletRequest();
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        // protocol
        request.setSecure(false);
        request.setServerPort(8080);
        request.setScheme("http");
        // host and port
        request.getCoyoteRequest().serverName().setString("10.0.0.1");
        request.setHeader("x-forwarded-host", "example.com:8443");
        request.setHeader("x-forwarded-proto", "https");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        // protocol
        String actualServerName = actualRequest.getServerName();
        Assert.assertEquals("postInvoke serverName", "example.com", actualServerName);

        String actualScheme = actualRequest.getScheme();
        Assert.assertEquals("postInvoke scheme", "https", actualScheme);

        int actualServerPort = actualRequest.getServerPort();
        Assert.assertEquals("postInvoke serverPort", 443, actualServerPort);

        boolean actualSecure = actualRequest.isSecure();
        Assert.assertTrue("postInvoke secure", actualSecure);
    }

    @Test
    public void testListToCommaDelimitedString() {
        String[] actual = RemoteIpFilter.commaDelimitedListToStringArray("element1, element2, element3");
        String[] expected = new String[] { "element1", "element2", "element3" };
        Assert.assertEquals(expected.length, actual.length);
        for (int i = 0; i < actual.length; i++) {
            Assert.assertEquals(expected[i], actual[i]);
        }
    }

    @Test
    public void testListToCommaDelimitedStringMixedSpaceChars() {
        String[] actual = RemoteIpFilter.commaDelimitedListToStringArray("element1  , element2,\t element3");
        String[] expected = new String[] { "element1", "element2", "element3" };
        Assert.assertEquals(expected.length, actual.length);
        for (int i = 0; i < actual.length; i++) {
            Assert.assertEquals(expected[i], actual[i]);
        }
    }

    private MockFilterChain testRemoteIpFilter(FilterDef filterDef, Request request)
            throws LifecycleException, IOException, ServletException {
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);

        RemoteIpFilter remoteIpFilter = new RemoteIpFilter();
        filterDef.setFilterClass(RemoteIpFilter.class.getName());
        filterDef.setFilter(remoteIpFilter);
        filterDef.setFilterName(RemoteIpFilter.class.getName());
        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(RemoteIpFilter.class.getName());
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        getTomcatInstance().start();

        MockFilterChain filterChain = new MockFilterChain();

        // TEST
        TesterResponse response = new TesterResponse();
        response.setRequest(request);
        remoteIpFilter.doFilter(request, response, filterChain);
        return filterChain;
    }

    @Test
    public void testRequestAttributesForAccessLog() throws Exception {
        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("protocolHeader", "x-forwarded-proto");
        filterDef.addInitParameter("remoteIpHeader", "x-my-forwarded-for");
        filterDef.addInitParameter("httpServerPort", "8080");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.10");
        request.setHeader("x-my-forwarded-for", "140.211.11.130");
        request.setHeader("x-forwarded-proto", "http");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        Assert.assertEquals("org.apache.catalina.AccessLog.ServerPort",
                Integer.valueOf(8080),
                actualRequest.getAttribute(AccessLog.SERVER_PORT_ATTRIBUTE));

        Assert.assertEquals("org.apache.catalina.AccessLog.RemoteAddr",
                "140.211.11.130",
                actualRequest.getAttribute(AccessLog.REMOTE_ADDR_ATTRIBUTE));

        Assert.assertEquals("org.apache.catalina.AccessLog.RemoteHost",
                "140.211.11.130",
                actualRequest.getAttribute(AccessLog.REMOTE_HOST_ATTRIBUTE));
    }

    @Test
    public void testRequestForwarded() throws Exception {
        // PREPARE
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("protocolHeader", "x-forwarded-proto");
        filterDef.addInitParameter("remoteIpHeader", "x-my-forwarded-for");
        filterDef.addInitParameter("httpServerPort", "8080");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.0.10");
        request.setHeader("x-my-forwarded-for", "140.211.11.130");
        request.setHeader("x-forwarded-proto", "http");

        // TEST
        HttpServletRequest actualRequest = testRemoteIpFilter(filterDef, request).getRequest();

        // VERIFY
        Assert.assertEquals("org.apache.tomcat.request.forwarded",
                Boolean.TRUE,
                actualRequest.getAttribute(Globals.REQUEST_FORWARDED_ATTRIBUTE));
    }

    /*
     * Test {@link RemoteIpFilter} in Tomcat standalone server
     */
    @Test
    public void testWithTomcatServer() throws Exception {

        // mostly default configuration : enable "x-forwarded-proto"
        Map<String, String> remoteIpFilterParameter = new HashMap<>();
        remoteIpFilterParameter.put("protocolHeader", "x-forwarded-proto");

        // SETUP
        Tomcat tomcat = getTomcatInstance();
        Context root = tomcat.addContext("", TEMP_DIR);

        FilterDef filterDef = new FilterDef();
        filterDef.getParameterMap().putAll(remoteIpFilterParameter);
        filterDef.setFilterClass(RemoteIpFilter.class.getName());
        filterDef.setFilterName(RemoteIpFilter.class.getName());

        root.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(RemoteIpFilter.class.getName());
        filterMap.addURLPatternDecoded("*");
        root.addFilterMap(filterMap);

        MockHttpServlet mockServlet = new MockHttpServlet();

        Tomcat.addServlet(root, mockServlet.getClass().getName(), mockServlet);
        root.addServletMappingDecoded("/test", mockServlet.getClass().getName());

        getTomcatInstance().start();

        // TEST
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(
                "http://localhost:" + tomcat.getConnector().getLocalPort() +
                "/test").openConnection();
        String expectedRemoteAddr = "my-remote-addr";
        httpURLConnection.addRequestProperty("x-forwarded-for", expectedRemoteAddr);
        httpURLConnection.addRequestProperty("x-forwarded-proto", "https");

        // VALIDATE
        Assert.assertEquals(HttpURLConnection.HTTP_OK, httpURLConnection.getResponseCode());
        HttpServletRequest request = mockServlet.getRequest();
        Assert.assertNotNull(request);

        // VALIDATE X-FORWARDED-FOR
        Assert.assertEquals(expectedRemoteAddr, request.getRemoteAddr());
        Assert.assertEquals(expectedRemoteAddr, request.getRemoteHost());

        // VALIDATE X-FORWARDED-PROTO
        Assert.assertTrue(request.isSecure());
        Assert.assertEquals("https", request.getScheme());
        Assert.assertEquals(443, request.getServerPort());
    }
}

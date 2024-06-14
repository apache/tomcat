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
package org.apache.catalina.valves;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Globals;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.tomcat.util.buf.StringUtils;

/**
 * {@link RemoteIpValve} Tests
 */
public class TestRemoteIpValve {

    static class RemoteAddrAndHostTrackerValve extends ValveBase {
        private String remoteAddr;
        private String remoteHost;
        private String scheme;
        private boolean secure;
        private String serverName;
        private int serverPort;
        private String forwardedFor;
        private String forwardedBy;

        public String getRemoteAddr() {
            return remoteAddr;
        }

        public String getRemoteHost() {
            return remoteHost;
        }

        public String getScheme() {
            return scheme;
        }

        public String getServerName() {
            return serverName;
        }

        public int getServerPort() {
            return serverPort;
        }

        public boolean isSecure() {
            return secure;
        }

        public String getForwardedFor() {
            return forwardedFor;
        }

        public String getForwardedBy() {
            return forwardedBy;
        }

        @Override
        public void invoke(Request request, Response response) throws IOException, ServletException {
            this.remoteHost = request.getRemoteHost();
            this.remoteAddr = request.getRemoteAddr();
            this.scheme = request.getScheme();
            this.secure = request.isSecure();
            this.serverName = request.getServerName();
            this.serverPort = request.getServerPort();
            this.forwardedFor = request.getHeader("x-forwarded-for");
            this.forwardedBy = request.getHeader("x-forwarded-by");
        }
    }

    public static class MockRequest extends Request {

        public MockRequest(org.apache.coyote.Request coyoteRequest) {
            super(new Connector(), coyoteRequest);
        }

        @Override
        public void setAttribute(String name, Object value) {
            getCoyoteRequest().getAttributes().put(name, value);
        }

        @Override
        public Object getAttribute(String name) {
            return getCoyoteRequest().getAttribute(name);
        }
    }

    @Test
    public void testInvokeAllowedRemoteAddrWithNullRemoteIpHeader() throws Exception {
        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setInternalProxies("192\\.168\\.0\\.10, 192\\.168\\.0\\.11");
        remoteIpValve.setTrustedProxies("proxy1, proxy2, proxy3");
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProxiesHeader("x-forwarded-by");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        String actualXForwardedFor = request.getHeader("x-forwarded-for");
        Assert.assertNull("x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = request.getHeader("x-forwarded-by");
        Assert.assertNull("x-forwarded-by must be null", actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "192.168.0.10", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "remote-host-original-value", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "remote-host-original-value", actualPostInvokeRemoteHost);

    }

    @Test
    public void testInvokeAllProxiesAreTrusted() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setInternalProxies("192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        remoteIpValve.setTrustedProxies("proxy1|proxy2|proxy3");
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProxiesHeader("x-forwarded-by");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for")
                .setString("140.211.11.130, proxy1, proxy2");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = remoteAddrAndHostTrackerValve.getForwardedBy();
        Assert.assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1,proxy2",
                actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "remote-host-original-value", actualPostInvokeRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreTrustedEmptyInternal() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setInternalProxies("");
        remoteIpValve.setTrustedProxies("proxy1|proxy2|proxy3");
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProxiesHeader("x-forwarded-by");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("proxy3");
        request.setRemoteHost("remote-host-original-value");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for")
                .setString("140.211.11.130, proxy1, proxy2");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = remoteAddrAndHostTrackerValve.getForwardedBy();
        Assert.assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1,proxy2,proxy3",
                actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "proxy3", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "remote-host-original-value", actualPostInvokeRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreTrustedUnusedInternal() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setTrustedProxies("proxy1|proxy2|proxy3");
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProxiesHeader("x-forwarded-by");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("proxy3");
        request.setRemoteHost("remote-host-original-value");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for")
                .setString("140.211.11.130, proxy1, proxy2");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = remoteAddrAndHostTrackerValve.getForwardedBy();
        Assert.assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1,proxy2,proxy3",
                actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "proxy3", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "remote-host-original-value", actualPostInvokeRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreTrustedOrInternal() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setInternalProxies("192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        remoteIpValve.setTrustedProxies("proxy1|proxy2|proxy3");
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProxiesHeader("x-forwarded-by");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for")
                .setString("140.211.11.130, proxy1, proxy2, 192.168.0.10, 192.168.0.11");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = remoteAddrAndHostTrackerValve.getForwardedBy();
        Assert.assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1,proxy2",
                actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "remote-host-original-value", actualPostInvokeRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreInternal() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setInternalProxies("192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        remoteIpValve.setTrustedProxies("proxy1|proxy2|proxy3");
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProxiesHeader("x-forwarded-by");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for")
                .setString("140.211.11.130, 192.168.0.10, 192.168.0.11");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("all proxies are internal, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = request.getHeader("x-forwarded-by");
        Assert.assertNull("all proxies are internal, x-forwarded-by must be null", actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "remote-host-original-value", actualPostInvokeRemoteHost);
    }

    @Test
    public void testInvokeAllProxiesAreTrustedAndRemoteAddrMatchRegexp() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setInternalProxies("127\\.0\\.0\\.1|192\\.168\\..*|another-internal-proxy");
        remoteIpValve.setTrustedProxies("proxy1|proxy2|proxy3");
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProxiesHeader("x-forwarded-by");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("140.211.11.130");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("proxy1");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("proxy2");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = remoteAddrAndHostTrackerValve.getForwardedBy();
        Assert.assertEquals("all proxies are trusted, they must appear in x-forwarded-by", "proxy1,proxy2",
                actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "remote-host-original-value", actualPostInvokeRemoteHost);
    }

    @Test
    public void test172dash12InternalProxies() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setInternalProxies(
                "172\\.1[6-9]{1}\\.\\d{1,3}\\.\\d{1,3}|172\\.2[0-9]{1}\\.\\d{1,3}\\.\\d{1,3}|172\\.3[0-1]{1}\\.\\d{1,3}\\.\\d{1,3}");
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("172.16.0.5");
        request.setRemoteHost("remote-host-original-value");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("209.244.0.3");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-proto").setString("https");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "209.244.0.3", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "209.244.0.3", actualRemoteHost);

        String actualPostInvokeRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "209.244.0.3", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "remote-host-original-value", actualPostInvokeRemoteHost);

        boolean isSecure = remoteAddrAndHostTrackerValve.isSecure();
        Assert.assertTrue("request from internal proxy should be marked secure", isSecure);

        String scheme = remoteAddrAndHostTrackerValve.getScheme();
        Assert.assertEquals("Scheme should be marked to https.", "https", scheme);

        request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("172.25.250.250");
        request.setRemoteHost("remote-host-original-value");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("209.244.0.3");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-proto").setString("https");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("all proxies are trusted, x-forwarded-for must be null", actualXForwardedFor);

        actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "209.244.0.3", actualRemoteAddr);

        actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "209.244.0.3", actualRemoteHost);

        actualPostInvokeRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "209.244.0.3", actualPostInvokeRemoteAddr);

        actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "remote-host-original-value", actualPostInvokeRemoteHost);

        isSecure = remoteAddrAndHostTrackerValve.isSecure();
        Assert.assertTrue("request from internal proxy should be marked secure", isSecure);

        scheme = remoteAddrAndHostTrackerValve.getScheme();
        Assert.assertEquals("Scheme should be marked to https.", "https", scheme);


    }


    @Test
    public void testInvokeXforwardedProtoSaysHttpsForIncomingHttpRequest() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("140.211.11.130");
        // protocol
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-proto").setString("https");
        request.setSecure(false);
        request.setServerPort(8080);
        request.getCoyoteRequest().scheme().setString("http");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        // client ip
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("no intermediate non-trusted proxy, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = request.getHeader("x-forwarded-by");
        Assert.assertNull("no intermediate trusted proxy", actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteHost);

        // protocol
        String actualScheme = remoteAddrAndHostTrackerValve.getScheme();
        Assert.assertEquals("x-forwarded-proto says https", "https", actualScheme);

        int actualServerPort = remoteAddrAndHostTrackerValve.getServerPort();
        Assert.assertEquals("x-forwarded-proto says https", 443, actualServerPort);

        boolean actualSecure = remoteAddrAndHostTrackerValve.isSecure();
        Assert.assertTrue("x-forwarded-proto says https", actualSecure);

        boolean actualPostInvokeSecure = request.isSecure();
        Assert.assertFalse("postInvoke secure", actualPostInvokeSecure);

        int actualPostInvokeServerPort = request.getServerPort();
        Assert.assertEquals("postInvoke serverPort", 8080, actualPostInvokeServerPort);

        String actualPostInvokeScheme = request.getScheme();
        Assert.assertEquals("postInvoke scheme", "http", actualPostInvokeScheme);
    }

    @Test
    public void testInvokeXforwardedProtoIsNullForIncomingHttpRequest() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("140.211.11.130");
        // protocol
        // null "x-forwarded-proto"
        request.setSecure(false);
        request.setServerPort(8080);
        request.getCoyoteRequest().scheme().setString("http");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        // client ip
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("no intermediate non-trusted proxy, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = request.getHeader("x-forwarded-by");
        Assert.assertNull("no intermediate trusted proxy", actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteHost);

        // protocol
        String actualScheme = remoteAddrAndHostTrackerValve.getScheme();
        Assert.assertEquals("x-forwarded-proto is null", "http", actualScheme);

        int actualServerPort = remoteAddrAndHostTrackerValve.getServerPort();
        Assert.assertEquals("x-forwarded-proto is null", 8080, actualServerPort);

        boolean actualSecure = remoteAddrAndHostTrackerValve.isSecure();
        Assert.assertFalse("x-forwarded-proto is null", actualSecure);

        boolean actualPostInvokeSecure = request.isSecure();
        Assert.assertFalse("postInvoke secure", actualPostInvokeSecure);

        int actualPostInvokeServerPort = request.getServerPort();
        Assert.assertEquals("postInvoke serverPort", 8080, actualPostInvokeServerPort);

        String actualPostInvokeScheme = request.getScheme();
        Assert.assertEquals("postInvoke scheme", "http", actualPostInvokeScheme);
    }

    @Test
    public void testInvokeXforwardedProtoSaysHttpForIncomingHttpsRequest() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("140.211.11.130");
        // protocol
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-proto").setString("http");
        request.setSecure(true);
        request.setServerPort(8443);
        request.getCoyoteRequest().scheme().setString("https");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        // client ip
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("no intermediate non-trusted proxy, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = remoteAddrAndHostTrackerValve.getForwardedBy();
        Assert.assertNull("no intermediate trusted proxy", actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteHost);

        // protocol
        String actualScheme = remoteAddrAndHostTrackerValve.getScheme();
        Assert.assertEquals("x-forwarded-proto says http", "http", actualScheme);

        int actualServerPort = remoteAddrAndHostTrackerValve.getServerPort();
        Assert.assertEquals("x-forwarded-proto says http", 80, actualServerPort);

        boolean actualSecure = remoteAddrAndHostTrackerValve.isSecure();
        Assert.assertFalse("x-forwarded-proto says http", actualSecure);

        boolean actualPostInvokeSecure = request.isSecure();
        Assert.assertTrue("postInvoke secure", actualPostInvokeSecure);

        int actualPostInvokeServerPort = request.getServerPort();
        Assert.assertEquals("postInvoke serverPort", 8443, actualPostInvokeServerPort);

        String actualPostInvokeScheme = request.getScheme();
        Assert.assertEquals("postInvoke scheme", "https", actualPostInvokeScheme);
    }

    @Test
    public void testInvokeXforwardedProtoSaysMultipleHttpsForwardsForIncomingHttpsRequest() throws Exception {
        performXForwardedProtoWithMultipleForwardsTest("https,https", true, true);
    }

    @Test
    public void testInvokeXforwardedProtoSaysMultipleForwardsWithFirstBeingHttpForIncomingHttpsRequest()
            throws Exception {
        performXForwardedProtoWithMultipleForwardsTest("http,https", true, false);
    }

    @Test
    public void testInvokeXforwardedProtoSaysMultipleForwardsWithLastBeingHttpForIncomingHttpRequest()
            throws Exception {
        performXForwardedProtoWithMultipleForwardsTest("https,http", false, false);
    }

    @Test
    public void testInvokeXforwardedProtoSaysMultipleForwardsWithMiddleBeingHttpForIncomingHttpsRequest()
            throws Exception {
        performXForwardedProtoWithMultipleForwardsTest("https,http,https", true, false);
    }

    @Test
    public void testInvokeXforwardedProtoSaysMultipleHttpForwardsForIncomingHttpRequest() throws Exception {
        performXForwardedProtoWithMultipleForwardsTest("http,http", false, false);
    }

    @Test
    public void testInvokeXforwardedProtoSaysInvalidValueForIncomingHttpRequest() throws Exception {
        performXForwardedProtoWithMultipleForwardsTest(",", false, false);
    }

    private void performXForwardedProtoWithMultipleForwardsTest(String incomingHeaderValue, boolean arrivesAsSecure,
            boolean shouldBeSecure) throws Exception {

        // PREPARE
        String incomingScheme = arrivesAsSecure ? "https" : "http";
        String expectedScheme = shouldBeSecure ? "https" : "http";
        int incomingServerPort = arrivesAsSecure ? 8443 : 8080;
        int expectedServerPort = shouldBeSecure ? 443 : 80;
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("140.211.11.130");
        // protocol
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-proto").setString(incomingHeaderValue);
        request.setSecure(arrivesAsSecure);
        request.setServerPort(incomingServerPort);
        request.getCoyoteRequest().scheme().setString(incomingScheme);

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        // client ip
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("no intermediate non-trusted proxy, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = remoteAddrAndHostTrackerValve.getForwardedBy();
        Assert.assertNull("no intermediate trusted proxy", actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteHost);

        // protocol
        String actualScheme = remoteAddrAndHostTrackerValve.getScheme();
        Assert.assertEquals("x-forwarded-proto says " + expectedScheme, expectedScheme, actualScheme);

        int actualServerPort = remoteAddrAndHostTrackerValve.getServerPort();
        Assert.assertEquals("x-forwarded-proto says " + expectedScheme, expectedServerPort, actualServerPort);

        boolean actualSecure = remoteAddrAndHostTrackerValve.isSecure();
        Assert.assertEquals("x-forwarded-proto says " + expectedScheme, Boolean.valueOf(shouldBeSecure),
                Boolean.valueOf(actualSecure));

        boolean actualPostInvokeSecure = request.isSecure();
        Assert.assertEquals("postInvoke secure", Boolean.valueOf(arrivesAsSecure),
                Boolean.valueOf(actualPostInvokeSecure));

        int actualPostInvokeServerPort = request.getServerPort();
        Assert.assertEquals("postInvoke serverPort", incomingServerPort, actualPostInvokeServerPort);

        String actualPostInvokeScheme = request.getScheme();
        Assert.assertEquals("postInvoke scheme", incomingScheme, actualPostInvokeScheme);
    }

    @Test
    public void testInvokeXforwardedProtoIsNullForIncomingHttpsRequest() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("140.211.11.130");
        // protocol
        // Don't declare "x-forwarded-proto"
        request.setSecure(true);
        request.setServerPort(8443);
        request.getCoyoteRequest().scheme().setString("https");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        // client ip
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertNull("no intermediate non-trusted proxy, x-forwarded-for must be null", actualXForwardedFor);

        String actualXForwardedBy = request.getHeader("x-forwarded-by");
        Assert.assertNull("no intermediate trusted proxy", actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "140.211.11.130", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "140.211.11.130", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteHost);

        // protocol
        String actualScheme = remoteAddrAndHostTrackerValve.getScheme();
        Assert.assertEquals("x-forwarded-proto is null", "https", actualScheme);

        int actualServerPort = remoteAddrAndHostTrackerValve.getServerPort();
        Assert.assertEquals("x-forwarded-proto is null", 8443, actualServerPort);

        boolean actualSecure = remoteAddrAndHostTrackerValve.isSecure();
        Assert.assertTrue("x-forwarded-proto is null", actualSecure);

        boolean actualPostInvokeSecure = request.isSecure();
        Assert.assertTrue("postInvoke secure", actualPostInvokeSecure);

        int actualPostInvokeServerPort = request.getServerPort();
        Assert.assertEquals("postInvoke serverPort", 8443, actualPostInvokeServerPort);

        String actualPostInvokeScheme = request.getScheme();
        Assert.assertEquals("postInvoke scheme", "https", actualPostInvokeScheme);
    }

    @Test
    public void testInvokeXforwardedHost() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setHostHeader("x-forwarded-host");
        remoteIpValve.setPortHeader("x-forwarded-port");
        remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        // protocol
        request.setSecure(false);
        request.setServerPort(8080);
        request.getCoyoteRequest().scheme().setString("http");
        // host and port
        request.getCoyoteRequest().serverName().setString("10.0.0.1");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-host").setString("example.com:8443");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-port").setString("8443");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-proto").setString("https");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        // protocol
        String actualServerName = remoteAddrAndHostTrackerValve.getServerName();
        Assert.assertEquals("tracked serverName", "example.com", actualServerName);

        String actualScheme = remoteAddrAndHostTrackerValve.getScheme();
        Assert.assertEquals("tracked scheme", "https", actualScheme);

        int actualServerPort = remoteAddrAndHostTrackerValve.getServerPort();
        Assert.assertEquals("tracked serverPort", 8443, actualServerPort);

        boolean actualSecure = remoteAddrAndHostTrackerValve.isSecure();
        Assert.assertTrue("tracked secure", actualSecure);

        String actualPostInvokeServerName = request.getServerName();
        Assert.assertEquals("postInvoke serverName", "10.0.0.1", actualPostInvokeServerName);

        boolean actualPostInvokeSecure = request.isSecure();
        Assert.assertFalse("postInvoke secure", actualPostInvokeSecure);

        int actualPostInvokeServerPort = request.getServerPort();
        Assert.assertEquals("postInvoke serverPort", 8080, actualPostInvokeServerPort);

        String actualPostInvokeScheme = request.getScheme();
        Assert.assertEquals("postInvoke scheme", "http", actualPostInvokeScheme);
    }

    @Test
    public void testInvokeXforwardedHostAndPort() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setHostHeader("x-forwarded-host");
        remoteIpValve.setPortHeader("x-forwarded-port");
        remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        // protocol
        request.setSecure(false);
        request.setServerPort(8080);
        request.getCoyoteRequest().scheme().setString("http");
        // host and port
        request.getCoyoteRequest().serverName().setString("10.0.0.1");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-host").setString("example.com");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-port").setString("8443");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-proto").setString("https");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        // protocol
        String actualServerName = remoteAddrAndHostTrackerValve.getServerName();
        Assert.assertEquals("tracked serverName", "example.com", actualServerName);

        String actualScheme = remoteAddrAndHostTrackerValve.getScheme();
        Assert.assertEquals("tracked scheme", "https", actualScheme);

        int actualServerPort = remoteAddrAndHostTrackerValve.getServerPort();
        Assert.assertEquals("tracked serverPort", 8443, actualServerPort);

        boolean actualSecure = remoteAddrAndHostTrackerValve.isSecure();
        Assert.assertTrue("tracked secure", actualSecure);

        String actualPostInvokeServerName = request.getServerName();
        Assert.assertEquals("postInvoke serverName", "10.0.0.1", actualPostInvokeServerName);

        boolean actualPostInvokeSecure = request.isSecure();
        Assert.assertFalse("postInvoke secure", actualPostInvokeSecure);

        int actualPostInvokeServerPort = request.getServerPort();
        Assert.assertEquals("postInvoke serverPort", 8080, actualPostInvokeServerPort);

        String actualPostInvokeScheme = request.getScheme();
        Assert.assertEquals("postInvoke scheme", "http", actualPostInvokeScheme);
    }

    @Test
    public void testInvokeNotAllowedRemoteAddr() throws Exception {
        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setInternalProxies("192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        remoteIpValve.setTrustedProxies("proxy1|proxy2|proxy3");
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProxiesHeader("x-forwarded-by");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("not-allowed-internal-proxy");
        request.setRemoteHost("not-allowed-internal-proxy-host");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for")
                .setString("140.211.11.130, proxy1, proxy2");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        String actualXForwardedFor = request.getHeader("x-forwarded-for");
        Assert.assertEquals("x-forwarded-for must be unchanged", "140.211.11.130, proxy1, proxy2", actualXForwardedFor);

        String actualXForwardedBy = request.getHeader("x-forwarded-by");
        Assert.assertNull("x-forwarded-by must be null", actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "not-allowed-internal-proxy", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "not-allowed-internal-proxy-host", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "not-allowed-internal-proxy", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "not-allowed-internal-proxy-host", actualPostInvokeRemoteHost);
    }

    @Test
    public void testInvokeUntrustedProxyInTheChain() throws Exception {
        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setInternalProxies("192\\.168\\.0\\.10|192\\.168\\.0\\.11");
        remoteIpValve.setTrustedProxies("proxy1|proxy2|proxy3");
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProxiesHeader("x-forwarded-by");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("remote-host-original-value");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for")
                .setString("140.211.11.130, proxy1, untrusted-proxy, proxy2");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        String actualXForwardedFor = remoteAddrAndHostTrackerValve.getForwardedFor();
        Assert.assertEquals("ip/host before untrusted-proxy must appear in x-forwarded-for", "140.211.11.130,proxy1",
                actualXForwardedFor);

        String actualXForwardedBy = remoteAddrAndHostTrackerValve.getForwardedBy();
        Assert.assertEquals("ip/host after untrusted-proxy must appear in  x-forwarded-by", "proxy2",
                actualXForwardedBy);

        String actualRemoteAddr = remoteAddrAndHostTrackerValve.getRemoteAddr();
        Assert.assertEquals("remoteAddr", "untrusted-proxy", actualRemoteAddr);

        String actualRemoteHost = remoteAddrAndHostTrackerValve.getRemoteHost();
        Assert.assertEquals("remoteHost", "untrusted-proxy", actualRemoteHost);

        String actualPostInvokeRemoteAddr = request.getRemoteAddr();
        Assert.assertEquals("postInvoke remoteAddr", "192.168.0.10", actualPostInvokeRemoteAddr);

        String actualPostInvokeRemoteHost = request.getRemoteHost();
        Assert.assertEquals("postInvoke remoteAddr", "remote-host-original-value", actualPostInvokeRemoteHost);
    }

    @Test
    public void testCommaDelimitedListToStringArray() {
        String[] actual = StringUtils.splitCommaSeparated("element1, element2, element3");
        String[] expected = new String[] { "element1", "element2", "element3" };
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testCommaDelimitedListToStringArrayMixedSpaceChars() {
        String[] actual = StringUtils.splitCommaSeparated("element1  , element2,\t element3");
        String[] expected = new String[] { "element1", "element2", "element3" };
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testRequestAttributesForAccessLog() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("140.211.11.130");
        // protocol
        request.setServerPort(8080);
        request.getCoyoteRequest().scheme().setString("http");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        Assert.assertEquals("org.apache.catalina.AccessLog.ServerPort", Integer.valueOf(8080),
                request.getAttribute(AccessLog.SERVER_PORT_ATTRIBUTE));

        Assert.assertEquals("org.apache.catalina.AccessLog.RemoteAddr", "140.211.11.130",
                request.getAttribute(AccessLog.REMOTE_ADDR_ATTRIBUTE));

        Assert.assertEquals("org.apache.catalina.AccessLog.RemoteHost", "140.211.11.130",
                request.getAttribute(AccessLog.REMOTE_HOST_ATTRIBUTE));
    }

    @Test
    public void testRequestForwarded() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("140.211.11.130");
        // protocol
        request.setServerPort(8080);
        request.getCoyoteRequest().scheme().setString("http");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY
        Assert.assertEquals("org.apache.tomcat.request.forwarded", Boolean.TRUE,
                request.getAttribute(Globals.REQUEST_FORWARDED_ATTRIBUTE));
    }

    @Test
    public void testRequestForwardedForWithPortNumber() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for").setString("140.211.11.130:1234");
        // protocol
        request.setServerPort(8080);
        request.getCoyoteRequest().scheme().setString("http");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY

        Assert.assertEquals("140.211.11.130:1234", remoteAddrAndHostTrackerValve.getRemoteAddr());
    }

    @Test
    public void testRequestForwardedForWithProxyPortNumber() throws Exception {

        // PREPARE
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        // remoteIpValve.setRemoteIpHeader("x-forwarded-for");
        // remoteIpValve.setProtocolHeader("x-forwarded-proto");
        RemoteAddrAndHostTrackerValve remoteAddrAndHostTrackerValve = new RemoteAddrAndHostTrackerValve();
        remoteIpValve.setNext(remoteAddrAndHostTrackerValve);

        Request request = new MockRequest(new org.apache.coyote.Request());
        // client ip
        request.setRemoteAddr("192.168.0.10");
        request.setRemoteHost("192.168.0.10");
        // Trust c.d
        remoteIpValve.setTrustedProxies("foo\\.bar:123");
        request.getCoyoteRequest().getMimeHeaders().addValue("x-forwarded-for")
                .setString("140.211.11.130:1234, foo.bar:123");
        // protocol
        request.setServerPort(8080);
        request.getCoyoteRequest().scheme().setString("http");

        // TEST
        remoteIpValve.invoke(request, null);

        // VERIFY

        Assert.assertEquals("140.211.11.130:1234", remoteAddrAndHostTrackerValve.getRemoteAddr());
    }

    private void assertArrayEquals(String[] expected, String[] actual) {
        if (expected == null) {
            Assert.assertNull(actual);
            return;
        }
        Assert.assertNotNull(actual);
        Assert.assertEquals(expected.length, actual.length);
        List<String> e = new ArrayList<>(Arrays.asList(expected));
        List<String> a = new ArrayList<>(Arrays.asList(actual));

        for (String entry : e) {
            Assert.assertTrue(a.remove(entry));
        }
        Assert.assertTrue(a.isEmpty());
    }

    @Test
    public void testInternalProxies() throws Exception {
        RemoteIpValve remoteIpValve = new RemoteIpValve();
        Pattern internalProxiesPattern = Pattern.compile(remoteIpValve.getInternalProxies());

        doTestPattern(internalProxiesPattern, "8.8.8.8", false);
        doTestPattern(internalProxiesPattern, "100.62.0.0", false);
        doTestPattern(internalProxiesPattern, "100.63.255.255", false);
        doTestPattern(internalProxiesPattern, "100.64.0.0", true);
        doTestPattern(internalProxiesPattern, "100.65.0.0", true);
        doTestPattern(internalProxiesPattern, "100.68.0.0", true);
        doTestPattern(internalProxiesPattern, "100.72.0.0", true);
        doTestPattern(internalProxiesPattern, "100.88.0.0", true);
        doTestPattern(internalProxiesPattern, "100.95.0.0", true);
        doTestPattern(internalProxiesPattern, "100.102.0.0", true);
        doTestPattern(internalProxiesPattern, "100.110.0.0", true);
        doTestPattern(internalProxiesPattern, "100.126.0.0", true);
        doTestPattern(internalProxiesPattern, "100.127.255.255", true);
        doTestPattern(internalProxiesPattern, "100.128.0.0", false);
        doTestPattern(internalProxiesPattern, "100.130.0.0", false);
    }

    private void doTestPattern(Pattern pattern, String input, boolean expectedMatch) {
        boolean match = pattern.matcher(input).matches();
        Assert.assertEquals(input, Boolean.valueOf(expectedMatch), Boolean.valueOf(match));
    }
}

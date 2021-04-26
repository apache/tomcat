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
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.PushBuilder;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Globals;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.util.RequestUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.parser.Host;
import org.apache.tomcat.util.res.StringManager;

/**
 * <p>
 * Servlet filter to integrate "X-Forwarded-For" and "X-Forwarded-Proto" HTTP headers.
 * </p>
 * <p>
 * Most of the design of this Servlet Filter is a port of <a
 * href="https://httpd.apache.org/docs/trunk/mod/mod_remoteip.html">mod_remoteip</a>, this servlet filter replaces the apparent client remote
 * IP address and hostname for the request with the IP address list presented by a proxy or a load balancer via a request headers (e.g.
 * "X-Forwarded-For").
 * </p>
 * <p>
 * Another feature of this servlet filter is to replace the apparent scheme (http/https) and server port with the scheme presented by a
 * proxy or a load balancer via a request header (e.g. "X-Forwarded-Proto").
 * </p>
 * <p>
 * This servlet filter proceeds as follows:
 * </p>
 * <p>
 * If the incoming <code>request.getRemoteAddr()</code> matches the servlet
 * filter's list of internal or trusted proxies:
 * </p>
 * <ul>
 * <li>Loop on the comma delimited list of IPs and hostnames passed by the preceding load balancer or proxy in the given request's Http
 * header named <code>$remoteIpHeader</code> (default value <code>x-forwarded-for</code>). Values are processed in right-to-left order.</li>
 * <li>For each ip/host of the list:
 * <ul>
 * <li>if it matches the internal proxies list, the ip/host is swallowed</li>
 * <li>if it matches the trusted proxies list, the ip/host is added to the created proxies header</li>
 * <li>otherwise, the ip/host is declared to be the remote ip and looping is stopped.</li>
 * </ul>
 * </li>
 * <li>If the request http header named <code>$protocolHeader</code> (e.g. <code>x-forwarded-proto</code>) consists only of forwards that match
 * <code>protocolHeaderHttpsValue</code> configuration parameter (default <code>https</code>) then <code>request.isSecure = true</code>,
 * <code>request.scheme = https</code> and <code>request.serverPort = 443</code>. Note that 443 can be overwritten with the
 * <code>$httpsServerPort</code> configuration parameter.</li>
 * <li>Mark the request with the attribute {@link Globals#REQUEST_FORWARDED_ATTRIBUTE} and value {@code Boolean.TRUE} to indicate
 * that this request has been forwarded by one or more proxies.</li>
 * </ul>
 * <table border="1">
 * <caption>Configuration parameters</caption>
 * <tr>
 * <th>XForwardedFilter property</th>
 * <th>Description</th>
 * <th>Equivalent mod_remoteip directive</th>
 * <th>Format</th>
 * <th>Default Value</th>
 * </tr>
 * <tr>
 * <td>remoteIpHeader</td>
 * <td>Name of the Http Header read by this servlet filter that holds the list of traversed IP addresses starting from the requesting client
 * </td>
 * <td>RemoteIPHeader</td>
 * <td>Compliant http header name</td>
 * <td>x-forwarded-for</td>
 * </tr>
 * <tr>
 * <td>internalProxies</td>
 * <td>Regular expression that matches the IP addresses of internal proxies.
 * If they appear in the <code>remoteIpHeader</code> value, they will be
 * trusted and will not appear
 * in the <code>proxiesHeader</code> value</td>
 * <td>RemoteIPInternalProxy</td>
 * <td>Regular expression (in the syntax supported by
 * {@link java.util.regex.Pattern java.util.regex})</td>
 * <td>10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|
 *     169\.254\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|
 *     172\.1[6-9]{1}\.\d{1,3}\.\d{1,3}|172\.2[0-9]{1}\.\d{1,3}\.\d{1,3}|
 *     172\.3[0-1]{1}\.\d{1,3}\.\d{1,3}|
 *     0:0:0:0:0:0:0:1|::1
 *     <br>
 * By default, 10/8, 192.168/16, 169.254/16, 127/8, 172.16/12, and 0:0:0:0:0:0:0:1 are allowed.</td>
 * </tr>
 * <tr>
 * <td>proxiesHeader</td>
 * <td>Name of the http header created by this servlet filter to hold the list of proxies that have been processed in the incoming
 * <code>remoteIpHeader</code></td>
 * <td>RemoteIPProxiesHeader</td>
 * <td>Compliant http header name</td>
 * <td>x-forwarded-by</td>
 * </tr>
 * <tr>
 * <td>trustedProxies</td>
 * <td>Regular expression that matches the IP addresses of trusted proxies.
 * If they appear in the <code>remoteIpHeader</code> value, they will be
 * trusted and will appear in the <code>proxiesHeader</code> value</td>
 * <td>RemoteIPTrustedProxy</td>
 * <td>Regular expression (in the syntax supported by
 * {@link java.util.regex.Pattern java.util.regex})</td>
 * <td>&nbsp;</td>
 * </tr>
 * <tr>
 * <td>protocolHeader</td>
 * <td>Name of the http header read by this servlet filter that holds the flag that this request</td>
 * <td>N/A</td>
 * <td>Compliant http header name like <code>X-Forwarded-Proto</code>, <code>X-Forwarded-Ssl</code> or <code>Front-End-Https</code></td>
 * <td><code>null</code></td>
 * </tr>
 * <tr>
 * <td>protocolHeaderHttpsValue</td>
 * <td>Value of the <code>protocolHeader</code> to indicate that it is an Https request</td>
 * <td>N/A</td>
 * <td>String like <code>https</code> or <code>ON</code></td>
 * <td><code>https</code></td>
 * </tr>
 * <tr>
 * <td>httpServerPort</td>
 * <td>Value returned by {@link ServletRequest#getServerPort()} when the <code>protocolHeader</code> indicates <code>http</code> protocol</td>
 * <td>N/A</td>
 * <td>integer</td>
 * <td>80</td>
 * </tr>
 * <tr>
 * <td>httpsServerPort</td>
 * <td>Value returned by {@link ServletRequest#getServerPort()} when the <code>protocolHeader</code> indicates <code>https</code> protocol</td>
 * <td>N/A</td>
 * <td>integer</td>
 * <td>443</td>
 * </tr>
 * <tr>
 * <td>enableLookups</td>
 * <td>Should a DNS lookup be performed to provide a host name when calling {@link ServletRequest#getRemoteHost()}</td>
 * <td>N/A</td>
 * <td>boolean</td>
 * <td>false</td>
 * </tr>
 * </table>
 * <p>
 * <strong>Regular expression vs. IP address blocks:</strong> <code>mod_remoteip</code> allows to use address blocks (e.g.
 * <code>192.168/16</code>) to configure <code>RemoteIPInternalProxy</code> and <code>RemoteIPTrustedProxy</code> ; as the JVM doesn't have a
 * library similar to <a
 * href="https://apr.apache.org/docs/apr/1.3/group__apr__network__io.html#gb74d21b8898b7c40bf7fd07ad3eb993d">apr_ipsubnet_test</a>, we rely on
 * regular expressions.
 * </p>
 * <hr>
 * <p>
 * <strong>Sample with internal proxies</strong>
 * </p>
 * <p>
 * XForwardedFilter configuration:
 * </p>
 * <code>
 * &lt;filter&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;filter-class&gt;org.apache.catalina.filters.RemoteIpFilter&lt;/filter-class&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;internalProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;192\.168\.0\.10|192\.168\.0\.11&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-for&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpProxiesHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-by&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;protocolHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-proto&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 * &lt;/filter&gt;
 *
 * &lt;filter-mapping&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *    &lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 * &lt;/filter-mapping&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpFilter</th>
 * <th>Value After RemoteIpFilter</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, 192.168.0.10</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-proto']</td>
 * <td>https</td>
 * <td>https</td>
 * </tr>
 * <tr>
 * <td>request.scheme</td>
 * <td>http</td>
 * <td>https</td>
 * </tr>
 * <tr>
 * <td>request.secure</td>
 * <td>false</td>
 * <td>true</td>
 * </tr>
 * <tr>
 * <td>request.serverPort</td>
 * <td>80</td>
 * <td>443</td>
 * </tr>
 * </table>
 * Note : <code>x-forwarded-by</code> header is null because only internal proxies as been traversed by the request.
 * <code>x-forwarded-by</code> is null because all the proxies are trusted or internal.
 * <hr>
 * <p>
 * <strong>Sample with trusted proxies</strong>
 * </p>
 * <p>
 * RemoteIpFilter configuration:
 * </p>
 * <code>
 * &lt;filter&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;filter-class&gt;org.apache.catalina.filters.RemoteIpFilter&lt;/filter-class&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;internalProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;192\.168\.0\.10|192\.168\.0\.11&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-for&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpProxiesHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-by&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;trustedProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;proxy1|proxy2&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 * &lt;/filter&gt;
 *
 * &lt;filter-mapping&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *    &lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 * &lt;/filter-mapping&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpFilter</th>
 * <th>Value After RemoteIpFilter</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, proxy1, proxy2</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>proxy1, proxy2</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>proxy1</code> and <code>proxy2</code> are both trusted proxies that come in <code>x-forwarded-for</code> header, they both
 * are migrated in <code>x-forwarded-by</code> header. <code>x-forwarded-by</code> is null because all the proxies are trusted or internal.
 * </p>
 * <hr>
 * <p>
 * <strong>Sample with internal and trusted proxies</strong>
 * </p>
 * <p>
 * RemoteIpFilter configuration:
 * </p>
 * <code>
 * &lt;filter&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;filter-class&gt;org.apache.catalina.filters.RemoteIpFilter&lt;/filter-class&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;internalProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;192\.168\.0\.10|192\.168\.0\.11&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-for&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpProxiesHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-by&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;trustedProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;proxy1|proxy2&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 * &lt;/filter&gt;
 *
 * &lt;filter-mapping&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *    &lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 * &lt;/filter-mapping&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpFilter</th>
 * <th>Value After RemoteIpFilter</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, proxy1, proxy2, 192.168.0.10</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>proxy1, proxy2</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>proxy1</code> and <code>proxy2</code> are both trusted proxies that come in <code>x-forwarded-for</code> header, they both
 * are migrated in <code>x-forwarded-by</code> header. As <code>192.168.0.10</code> is an internal proxy, it does not appear in
 * <code>x-forwarded-by</code>. <code>x-forwarded-by</code> is null because all the proxies are trusted or internal.
 * </p>
 * <hr>
 * <p>
 * <strong>Sample with an untrusted proxy</strong>
 * </p>
 * <p>
 * RemoteIpFilter configuration:
 * </p>
 * <code>
 * &lt;filter&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;filter-class&gt;org.apache.catalina.filters.RemoteIpFilter&lt;/filter-class&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;internalProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;192\.168\.0\.10|192\.168\.0\.11&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-for&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;remoteIpProxiesHeader&lt;/param-name&gt;
 *       &lt;param-value&gt;x-forwarded-by&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 *    &lt;init-param&gt;
 *       &lt;param-name&gt;trustedProxies&lt;/param-name&gt;
 *       &lt;param-value&gt;proxy1|proxy2&lt;/param-value&gt;
 *    &lt;/init-param&gt;
 * &lt;/filter&gt;
 *
 * &lt;filter-mapping&gt;
 *    &lt;filter-name&gt;RemoteIpFilter&lt;/filter-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *    &lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
 * &lt;/filter-mapping&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpFilter</th>
 * <th>Value After RemoteIpFilter</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>untrusted-proxy</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, untrusted-proxy, proxy1</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>proxy1</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>x-forwarded-by</code> holds the trusted proxy <code>proxy1</code>. <code>x-forwarded-by</code> holds
 * <code>140.211.11.130</code> because <code>untrusted-proxy</code> is not trusted and thus, we cannot trust that
 * <code>untrusted-proxy</code> is the actual remote ip. <code>request.remoteAddr</code> is <code>untrusted-proxy</code> that is an IP
 * verified by <code>proxy1</code>.
 * </p>
 * <hr>
 */
public class RemoteIpFilter extends GenericFilter {

    private static final long serialVersionUID = 1L;

    public static class XForwardedRequest extends HttpServletRequestWrapper {

        protected final Map<String, List<String>> headers;

        protected String localName;

        protected int localPort;

        protected String remoteAddr;

        protected String remoteHost;

        protected String scheme;

        protected boolean secure;

        protected String serverName;

        protected int serverPort;

        public XForwardedRequest(HttpServletRequest request) {
            super(request);
            this.localName = request.getLocalName();
            this.localPort = request.getLocalPort();
            this.remoteAddr = request.getRemoteAddr();
            this.remoteHost = request.getRemoteHost();
            this.scheme = request.getScheme();
            this.secure = request.isSecure();
            this.serverName = request.getServerName();
            this.serverPort = request.getServerPort();

            headers = new HashMap<>();
            for (Enumeration<String> headerNames = request.getHeaderNames(); headerNames.hasMoreElements();) {
                String header = headerNames.nextElement();
                headers.put(header, Collections.list(request.getHeaders(header)));
            }
        }

        @Override
        public long getDateHeader(String name) {
            String value = getHeader(name);
            if (value == null) {
                return -1;
            }
            long date = FastHttpDateFormat.parseDate(value);
            if (date == -1) {
                throw new IllegalArgumentException(value);
            }
            return date;
        }

        @Override
        public String getHeader(String name) {
            Map.Entry<String, List<String>> header = getHeaderEntry(name);
            if (header == null || header.getValue() == null || header.getValue().isEmpty()) {
                return null;
            }
            return header.getValue().get(0);
        }

        protected Map.Entry<String, List<String>> getHeaderEntry(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry;
                }
            }
            return null;
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(headers.keySet());
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            Map.Entry<String, List<String>> header = getHeaderEntry(name);
            if (header == null || header.getValue() == null) {
                return Collections.enumeration(Collections.<String>emptyList());
            }
            return Collections.enumeration(header.getValue());
        }

        @Override
        public int getIntHeader(String name) {
            String value = getHeader(name);
            if (value == null) {
                return -1;
            }
            return Integer.parseInt(value);
        }

        @Override
        public String getLocalName() {
            return localName;
        }

        @Override
        public int getLocalPort() {
            return localPort;
        }

        @Override
        public String getRemoteAddr() {
            return this.remoteAddr;
        }

        @Override
        public String getRemoteHost() {
            return this.remoteHost;
        }

        @Override
        public String getScheme() {
            return scheme;
        }

        @Override
        public String getServerName() {
            return serverName;
        }

        @Override
        public int getServerPort() {
            return serverPort;
        }

        @Override
        public boolean isSecure() {
            return secure;
        }

        public void removeHeader(String name) {
            Map.Entry<String, List<String>> header = getHeaderEntry(name);
            if (header != null) {
                headers.remove(header.getKey());
            }
        }

        public void setHeader(String name, String value) {
            List<String> values = Collections.singletonList(value);
            Map.Entry<String, List<String>> header = getHeaderEntry(name);
            if (header == null) {
                headers.put(name, values);
            } else {
                header.setValue(values);
            }

        }

        public void setLocalName(String localName) {
            this.localName = localName;
        }

        public void setLocalPort(int localPort) {
            this.localPort = localPort;
        }

        public void setRemoteAddr(String remoteAddr) {
            this.remoteAddr = remoteAddr;
        }

        public void setRemoteHost(String remoteHost) {
            this.remoteHost = remoteHost;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
        }

        public void setServerPort(int serverPort) {
            this.serverPort = serverPort;
        }

        @Override
        public StringBuffer getRequestURL() {
            return RequestUtil.getRequestURL(this);
        }

        @Override
        public PushBuilder newPushBuilder() {
            ServletRequest current = getRequest();
            while (current instanceof ServletRequestWrapper) {
                current = ((ServletRequestWrapper) current).getRequest();
            }
            if (current instanceof RequestFacade) {
                return ((RequestFacade) current).newPushBuilder(this);
            } else {
                return null;
            }
        }
    }


    /**
     * {@link Pattern} for a comma delimited string that support whitespace characters
     */
    private static final Pattern commaSeparatedValuesPattern = Pattern.compile("\\s*,\\s*");

    protected static final String HTTP_SERVER_PORT_PARAMETER = "httpServerPort";

    protected static final String HTTPS_SERVER_PORT_PARAMETER = "httpsServerPort";

    protected static final String INTERNAL_PROXIES_PARAMETER = "internalProxies";

    // Log must be non-static as loggers are created per class-loader and this
    // Filter may be used in multiple class loaders
    private transient Log log = LogFactory.getLog(RemoteIpFilter.class);
    protected static final StringManager sm = StringManager.getManager(RemoteIpFilter.class);

    protected static final String PROTOCOL_HEADER_PARAMETER = "protocolHeader";

    protected static final String PROTOCOL_HEADER_HTTPS_VALUE_PARAMETER = "protocolHeaderHttpsValue";

    protected static final String HOST_HEADER_PARAMETER = "hostHeader";

    protected static final String PORT_HEADER_PARAMETER = "portHeader";

    protected static final String CHANGE_LOCAL_NAME_PARAMETER = "changeLocalName";

    protected static final String CHANGE_LOCAL_PORT_PARAMETER = "changeLocalPort";

    protected static final String PROXIES_HEADER_PARAMETER = "proxiesHeader";

    protected static final String REMOTE_IP_HEADER_PARAMETER = "remoteIpHeader";

    protected static final String TRUSTED_PROXIES_PARAMETER = "trustedProxies";

    protected static final String ENABLE_LOOKUPS_PARAMETER = "enableLookups";

    /**
     * Convert a given comma delimited list of regular expressions into an array of String
     *
     * @param commaDelimitedStrings The string to split
     * @return array of patterns (non <code>null</code>)
     */
    protected static String[] commaDelimitedListToStringArray(String commaDelimitedStrings) {
        return (commaDelimitedStrings == null || commaDelimitedStrings.length() == 0) ? new String[0] : commaSeparatedValuesPattern
            .split(commaDelimitedStrings);
    }

    /**
     * Convert a list of strings in a comma delimited string.
     *
     * @param stringList List of strings
     * @return concatenated string
     */
    protected static String listToCommaDelimitedString(List<String> stringList) {
        if (stringList == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Iterator<String> it = stringList.iterator(); it.hasNext();) {
            Object element = it.next();
            if (element != null) {
                result.append(element);
                if (it.hasNext()) {
                    result.append(", ");
                }
            }
        }
        return result.toString();
    }

    /**
     * @see #setHttpServerPort(int)
     */
    private int httpServerPort = 80;

    /**
     * @see #setHttpsServerPort(int)
     */
    private int httpsServerPort = 443;

    /**
     * @see #setInternalProxies(String)
     */
    private Pattern internalProxies = Pattern.compile(
            "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
            "192\\.168\\.\\d{1,3}\\.\\d{1,3}|" +
            "169\\.254\\.\\d{1,3}\\.\\d{1,3}|" +
            "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.1[6-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.2[0-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.3[0-1]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "0:0:0:0:0:0:0:1|::1");

    /**
     * @see #setProtocolHeader(String)
     */
    private String protocolHeader = "X-Forwarded-Proto";

    private String protocolHeaderHttpsValue = "https";

    private String hostHeader = null;

    private boolean changeLocalName = false;

    private String portHeader = null;

    private boolean changeLocalPort = false;

    /**
     * @see #setProxiesHeader(String)
     */
    private String proxiesHeader = "X-Forwarded-By";

    /**
     * @see #setRemoteIpHeader(String)
     */
    private String remoteIpHeader = "X-Forwarded-For";

    /**
     * @see #setRequestAttributesEnabled(boolean)
     */
    private boolean requestAttributesEnabled = true;

    /**
     * @see #setTrustedProxies(String)
     */
    private Pattern trustedProxies = null;

    private boolean enableLookups;

    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {

        boolean isInternal = internalProxies != null &&
                internalProxies.matcher(request.getRemoteAddr()).matches();

        if (isInternal || (trustedProxies != null &&
                trustedProxies.matcher(request.getRemoteAddr()).matches())) {
            String remoteIp = null;
            // In java 6, proxiesHeaderValue should be declared as a java.util.Deque
            LinkedList<String> proxiesHeaderValue = new LinkedList<>();
            StringBuilder concatRemoteIpHeaderValue = new StringBuilder();

            for (Enumeration<String> e = request.getHeaders(remoteIpHeader); e.hasMoreElements();) {
                if (concatRemoteIpHeaderValue.length() > 0) {
                    concatRemoteIpHeaderValue.append(", ");
                }

                concatRemoteIpHeaderValue.append(e.nextElement());
            }

            String[] remoteIpHeaderValue = commaDelimitedListToStringArray(concatRemoteIpHeaderValue.toString());
            int idx;
            if (!isInternal) {
                proxiesHeaderValue.addFirst(request.getRemoteAddr());
            }
            // loop on remoteIpHeaderValue to find the first trusted remote ip and to build the proxies chain
            for (idx = remoteIpHeaderValue.length - 1; idx >= 0; idx--) {
                String currentRemoteIp = remoteIpHeaderValue[idx];
                remoteIp = currentRemoteIp;
                if (internalProxies !=null && internalProxies.matcher(currentRemoteIp).matches()) {
                    // do nothing, internalProxies IPs are not appended to the
                } else if (trustedProxies != null &&
                        trustedProxies.matcher(currentRemoteIp).matches()) {
                    proxiesHeaderValue.addFirst(currentRemoteIp);
                } else {
                    idx--; // decrement idx because break statement doesn't do it
                    break;
                }
            }
            // continue to loop on remoteIpHeaderValue to build the new value of the remoteIpHeader
            LinkedList<String> newRemoteIpHeaderValue = new LinkedList<>();
            for (; idx >= 0; idx--) {
                String currentRemoteIp = remoteIpHeaderValue[idx];
                newRemoteIpHeaderValue.addFirst(currentRemoteIp);
            }

            XForwardedRequest xRequest = new XForwardedRequest(request);
            if (remoteIp != null) {

                xRequest.setRemoteAddr(remoteIp);
                if (getEnableLookups()) {
                    // This isn't a lazy lookup but that would be a little more
                    // invasive - mainly in XForwardedRequest - and if
                    // enableLookups is true is seems reasonable that the
                    // hostname will be required so look it up here.
                    try {
                        InetAddress inetAddress = InetAddress.getByName(remoteIp);
                        // We know we need a DNS look up so use getCanonicalHostName()
                        xRequest.setRemoteHost(inetAddress.getCanonicalHostName());
                    } catch (UnknownHostException e) {
                        log.debug(sm.getString("remoteIpFilter.invalidRemoteAddress", remoteIp), e);
                        xRequest.setRemoteHost(remoteIp);
                    }
                } else {
                    xRequest.setRemoteHost(remoteIp);
                }

                if (proxiesHeaderValue.size() == 0) {
                    xRequest.removeHeader(proxiesHeader);
                } else {
                    String commaDelimitedListOfProxies = listToCommaDelimitedString(proxiesHeaderValue);
                    xRequest.setHeader(proxiesHeader, commaDelimitedListOfProxies);
                }
                if (newRemoteIpHeaderValue.size() == 0) {
                    xRequest.removeHeader(remoteIpHeader);
                } else {
                    String commaDelimitedRemoteIpHeaderValue = listToCommaDelimitedString(newRemoteIpHeaderValue);
                    xRequest.setHeader(remoteIpHeader, commaDelimitedRemoteIpHeaderValue);
                }
            }

            if (protocolHeader != null) {
                String protocolHeaderValue = request.getHeader(protocolHeader);
                if (protocolHeaderValue == null) {
                    // Don't modify the secure, scheme and serverPort attributes
                    // of the request
                } else if (isForwardedProtoHeaderValueSecure(protocolHeaderValue)) {
                    xRequest.setSecure(true);
                    xRequest.setScheme("https");
                    setPorts(xRequest, httpsServerPort);
                } else {
                    xRequest.setSecure(false);
                    xRequest.setScheme("http");
                    setPorts(xRequest, httpServerPort);
                }
            }

            if (hostHeader != null) {
                String hostHeaderValue = request.getHeader(hostHeader);
                if (hostHeaderValue != null) {
                    try {
                        int portIndex = Host.parse(hostHeaderValue);
                        if (portIndex > -1) {
                            log.debug(sm.getString("remoteIpFilter.invalidHostWithPort", hostHeaderValue, hostHeader));
                            hostHeaderValue = hostHeaderValue.substring(0, portIndex);
                        }

                        xRequest.setServerName(hostHeaderValue);
                        if (isChangeLocalName()) {
                            xRequest.setLocalName(hostHeaderValue);
                        }

                    } catch (IllegalArgumentException iae) {
                        log.debug(sm.getString("remoteIpFilter.invalidHostHeader", hostHeaderValue, hostHeader));
                    }
                }
            }
            request.setAttribute(Globals.REQUEST_FORWARDED_ATTRIBUTE, Boolean.TRUE);

            if (log.isDebugEnabled()) {
                log.debug("Incoming request " + request.getRequestURI() + " with originalRemoteAddr [" + request.getRemoteAddr() +
                        "], originalRemoteHost=[" + request.getRemoteHost() + "], originalSecure=[" + request.isSecure() +
                        "], originalScheme=[" + request.getScheme() + "], originalServerName=[" + request.getServerName() +
                        "], originalServerPort=[" + request.getServerPort() +
                        "] will be seen as newRemoteAddr=[" + xRequest.getRemoteAddr() +
                        "], newRemoteHost=[" + xRequest.getRemoteHost() + "], newSecure=[" + xRequest.isSecure() +
                        "], newScheme=[" + xRequest.getScheme() + "], newServerName=[" + xRequest.getServerName() +
                        "], newServerPort=[" + xRequest.getServerPort() + "]");
            }
            if (requestAttributesEnabled) {
                request.setAttribute(AccessLog.REMOTE_ADDR_ATTRIBUTE,
                        xRequest.getRemoteAddr());
                request.setAttribute(Globals.REMOTE_ADDR_ATTRIBUTE,
                        xRequest.getRemoteAddr());
                request.setAttribute(AccessLog.REMOTE_HOST_ATTRIBUTE,
                        xRequest.getRemoteHost());
                request.setAttribute(AccessLog.PROTOCOL_ATTRIBUTE,
                        xRequest.getProtocol());
                request.setAttribute(AccessLog.SERVER_NAME_ATTRIBUTE,
                        xRequest.getServerName());
                request.setAttribute(AccessLog.SERVER_PORT_ATTRIBUTE,
                        Integer.valueOf(xRequest.getServerPort()));
            }
            chain.doFilter(xRequest, response);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Skip RemoteIpFilter for request " + request.getRequestURI() + " with originalRemoteAddr '"
                        + request.getRemoteAddr() + "'");
            }
            chain.doFilter(request, response);
        }

    }

    /*
     * Considers the value to be secure if it exclusively holds forwards for
     * {@link #protocolHeaderHttpsValue}.
     */
    private boolean isForwardedProtoHeaderValueSecure(String protocolHeaderValue) {
        if (!protocolHeaderValue.contains(",")) {
            return protocolHeaderHttpsValue.equalsIgnoreCase(protocolHeaderValue);
        }
        String[] forwardedProtocols = commaDelimitedListToStringArray(protocolHeaderValue);
        if (forwardedProtocols.length == 0) {
            return false;
        }
        for (String forwardedProtocol : forwardedProtocols) {
            if (!protocolHeaderHttpsValue.equalsIgnoreCase(forwardedProtocol)) {
                return false;
            }
        }
        return true;
    }

    private void setPorts(XForwardedRequest xrequest, int defaultPort) {
        int port = defaultPort;
        if (getPortHeader() != null) {
            String portHeaderValue = xrequest.getHeader(getPortHeader());
            if (portHeaderValue != null) {
                try {
                    port = Integer.parseInt(portHeaderValue);
                } catch (NumberFormatException nfe) {
                    log.debug("Invalid port value [" + portHeaderValue +
                            "] provided in header [" + getPortHeader() + "]");
                }
            }
        }
        xrequest.setServerPort(port);
        if (isChangeLocalPort()) {
            xrequest.setLocalPort(port);
        }
    }

    /**
     * Wrap the incoming <code>request</code> in a {@link XForwardedRequest} if the http header <code>x-forwarded-for</code> is not empty.
     * {@inheritDoc}
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            doFilter((HttpServletRequest)request, (HttpServletResponse)response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    public boolean isChangeLocalName() {
        return changeLocalName;
    }

    public boolean isChangeLocalPort() {
        return changeLocalPort;
    }

    public int getHttpsServerPort() {
        return httpsServerPort;
    }

    public Pattern getInternalProxies() {
        return internalProxies;
    }

    public String getProtocolHeader() {
        return protocolHeader;
    }

    public String getPortHeader() {
        return portHeader;
    }

    public String getProtocolHeaderHttpsValue() {
        return protocolHeaderHttpsValue;
    }

    public String getProxiesHeader() {
        return proxiesHeader;
    }

    public String getRemoteIpHeader() {
        return remoteIpHeader;
    }

    /**
     * @see #setRequestAttributesEnabled(boolean)
     * @return <code>true</code> if the attributes will be logged, otherwise
     *         <code>false</code>
     */
    public boolean getRequestAttributesEnabled() {
        return requestAttributesEnabled;
    }

    public Pattern getTrustedProxies() {
        return trustedProxies;
    }

    public boolean getEnableLookups() {
        return enableLookups;
    }

    @Override
    public void init() throws ServletException {
        if (getInitParameter(INTERNAL_PROXIES_PARAMETER) != null) {
            setInternalProxies(getInitParameter(INTERNAL_PROXIES_PARAMETER));
        }

        if (getInitParameter(PROTOCOL_HEADER_PARAMETER) != null) {
            setProtocolHeader(getInitParameter(PROTOCOL_HEADER_PARAMETER));
        }

        if (getInitParameter(PROTOCOL_HEADER_HTTPS_VALUE_PARAMETER) != null) {
            setProtocolHeaderHttpsValue(getInitParameter(PROTOCOL_HEADER_HTTPS_VALUE_PARAMETER));
        }

        if (getInitParameter(HOST_HEADER_PARAMETER) != null) {
            setHostHeader(getInitParameter(HOST_HEADER_PARAMETER));
        }

        if (getInitParameter(PORT_HEADER_PARAMETER) != null) {
            setPortHeader(getInitParameter(PORT_HEADER_PARAMETER));
        }

        if (getInitParameter(CHANGE_LOCAL_NAME_PARAMETER) != null) {
            setChangeLocalName(Boolean.parseBoolean(getInitParameter(CHANGE_LOCAL_NAME_PARAMETER)));
        }

        if (getInitParameter(CHANGE_LOCAL_PORT_PARAMETER) != null) {
            setChangeLocalPort(Boolean.parseBoolean(getInitParameter(CHANGE_LOCAL_PORT_PARAMETER)));
        }

        if (getInitParameter(PROXIES_HEADER_PARAMETER) != null) {
            setProxiesHeader(getInitParameter(PROXIES_HEADER_PARAMETER));
        }

        if (getInitParameter(REMOTE_IP_HEADER_PARAMETER) != null) {
            setRemoteIpHeader(getInitParameter(REMOTE_IP_HEADER_PARAMETER));
        }

        if (getInitParameter(TRUSTED_PROXIES_PARAMETER) != null) {
            setTrustedProxies(getInitParameter(TRUSTED_PROXIES_PARAMETER));
        }

        if (getInitParameter(HTTP_SERVER_PORT_PARAMETER) != null) {
            try {
                setHttpServerPort(Integer.parseInt(getInitParameter(HTTP_SERVER_PORT_PARAMETER)));
            } catch (NumberFormatException e) {
                throw new NumberFormatException(sm.getString("remoteIpFilter.invalidNumber", HTTP_SERVER_PORT_PARAMETER, e.getLocalizedMessage()));
            }
        }

        if (getInitParameter(HTTPS_SERVER_PORT_PARAMETER) != null) {
            try {
                setHttpsServerPort(Integer.parseInt(getInitParameter(HTTPS_SERVER_PORT_PARAMETER)));
            } catch (NumberFormatException e) {
                throw new NumberFormatException(sm.getString("remoteIpFilter.invalidNumber", HTTPS_SERVER_PORT_PARAMETER, e.getLocalizedMessage()));
            }
        }

        if (getInitParameter(ENABLE_LOOKUPS_PARAMETER) != null) {
            setEnableLookups(Boolean.parseBoolean(getInitParameter(ENABLE_LOOKUPS_PARAMETER)));
        }
    }

    /**
     * <p>
     * If <code>true</code>, the return values for both {@link
     * ServletRequest#getLocalName()} and {@link ServletRequest#getServerName()}
     * will be modified by this Filter rather than just
     * {@link ServletRequest#getServerName()}.
     * </p>
     * <p>
     * Default value : <code>false</code>
     * </p>
     * @param changeLocalName The new flag value
     */
    public void setChangeLocalName(boolean changeLocalName) {
        this.changeLocalName = changeLocalName;
    }

    /**
     * <p>
     * If <code>true</code>, the return values for both {@link
     * ServletRequest#getLocalPort()} and {@link ServletRequest#getServerPort()}
     * will be modified by this Filter rather than just
     * {@link ServletRequest#getServerPort()}.
     * </p>
     * <p>
     * Default value : <code>false</code>
     * </p>
     * @param changeLocalPort The new flag value
     */
    public void setChangeLocalPort(boolean changeLocalPort) {
        this.changeLocalPort = changeLocalPort;
    }

    /**
     * <p>
     * Server Port value if the {@link #protocolHeader} indicates HTTP (i.e. {@link #protocolHeader} is not null and
     * has a value different of {@link #protocolHeaderHttpsValue}).
     * </p>
     * <p>
     * Default value : 80
     * </p>
     * @param httpServerPort The server port to use
     */
    public void setHttpServerPort(int httpServerPort) {
        this.httpServerPort = httpServerPort;
    }

    /**
     * <p>
     * Server Port value if the {@link #protocolHeader} indicates HTTPS
     * </p>
     * <p>
     * Default value : 443
     * </p>
     * @param httpsServerPort The server port to use
     */
    public void setHttpsServerPort(int httpsServerPort) {
        this.httpsServerPort = httpsServerPort;
    }

    /**
     * <p>
     * Regular expression that defines the internal proxies.
     * </p>
     * <p>
     * Default value : 10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254.\d{1,3}.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|0:0:0:0:0:0:0:1
     * </p>
     * @param internalProxies The regexp
     */
    public void setInternalProxies(String internalProxies) {
        if (internalProxies == null || internalProxies.length() == 0) {
            this.internalProxies = null;
        } else {
            this.internalProxies = Pattern.compile(internalProxies);
        }
    }

    /**
     * <p>
     * Header that holds the incoming host, usually named
     * <code>X-Forwarded-HOst</code>.
     * </p>
     * <p>
     * Default value : <code>null</code>
     * </p>
     * @param hostHeader The header name
     */
    public void setHostHeader(String hostHeader) {
        this.hostHeader = hostHeader;
    }

    /**
     * <p>
     * Header that holds the incoming port, usually named
     * <code>X-Forwarded-Port</code>. If <code>null</code>,
     * {@link #httpServerPort} or {@link #httpsServerPort} will be used.
     * </p>
     * <p>
     * Default value : <code>null</code>
     * </p>
     * @param portHeader The header name
     */
    public void setPortHeader(String portHeader) {
        this.portHeader = portHeader;
    }

    /**
     * <p>
     * Header that holds the incoming protocol, usually named <code>X-Forwarded-Proto</code>. If <code>null</code>, request.scheme and
     * request.secure will not be modified.
     * </p>
     * <p>
     * Default value : <code>null</code>
     * </p>
     * @param protocolHeader The header name
     */
    public void setProtocolHeader(String protocolHeader) {
        this.protocolHeader = protocolHeader;
    }

    /**
     * <p>
     * Case insensitive value of the protocol header to indicate that the incoming http request uses HTTPS.
     * </p>
     * <p>
     * Default value : <code>https</code>
     * </p>
     * @param protocolHeaderHttpsValue The header value
     */
    public void setProtocolHeaderHttpsValue(String protocolHeaderHttpsValue) {
        this.protocolHeaderHttpsValue = protocolHeaderHttpsValue;
    }

    /**
     * <p>
     * The proxiesHeader directive specifies a header into which mod_remoteip will collect a list of all of the intermediate client IP
     * addresses trusted to resolve the actual remote IP. Note that intermediate RemoteIPTrustedProxy addresses are recorded in this header,
     * while any intermediate RemoteIPInternalProxy addresses are discarded.
     * </p>
     * <p>
     * Name of the http header that holds the list of trusted proxies that has been traversed by the http request.
     * </p>
     * <p>
     * The value of this header can be comma delimited.
     * </p>
     * <p>
     * Default value : <code>X-Forwarded-By</code>
     * </p>
     * @param proxiesHeader The header name
     */
    public void setProxiesHeader(String proxiesHeader) {
        this.proxiesHeader = proxiesHeader;
    }

    /**
     * <p>
     * Name of the http header from which the remote ip is extracted.
     * </p>
     * <p>
     * The value of this header can be comma delimited.
     * </p>
     * <p>
     * Default value : <code>X-Forwarded-For</code>
     * </p>
     * @param remoteIpHeader The header name
     */
    public void setRemoteIpHeader(String remoteIpHeader) {
        this.remoteIpHeader = remoteIpHeader;
    }

    /**
     * Should this filter set request attributes for IP address, Hostname,
     * protocol and port used for the request? This are typically used in
     * conjunction with an {@link AccessLog} which will otherwise log the
     * original values. Default is <code>true</code>.
     *
     * The attributes set are:
     * <ul>
     * <li>org.apache.catalina.AccessLog.RemoteAddr</li>
     * <li>org.apache.catalina.AccessLog.RemoteHost</li>
     * <li>org.apache.catalina.AccessLog.Protocol</li>
     * <li>org.apache.catalina.AccessLog.ServerPort</li>
     * <li>org.apache.tomcat.remoteAddr</li>
     * </ul>
     *
     * @param requestAttributesEnabled  <code>true</code> causes the attributes
     *                                  to be set, <code>false</code> disables
     *                                  the setting of the attributes.
     */
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
        this.requestAttributesEnabled = requestAttributesEnabled;
    }

    /**
     * <p>
     * Regular expression defining proxies that are trusted when they appear in
     * the {@link #remoteIpHeader} header.
     * </p>
     * <p>
     * Default value : empty list, no external proxy is trusted.
     * </p>
     * @param trustedProxies The trusted proxies regexp
     */
    public void setTrustedProxies(String trustedProxies) {
        if (trustedProxies == null || trustedProxies.length() == 0) {
            this.trustedProxies = null;
        } else {
            this.trustedProxies = Pattern.compile(trustedProxies);
        }
    }

    public void setEnableLookups(boolean enableLookups) {
        this.enableLookups = enableLookups;
    }

    /*
     * Log objects are not Serializable but this Filter is because it extends
     * GenericFilter. Tomcat won't serialize a Filter but in case something else
     * does...
     */
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        log = LogFactory.getLog(RemoteIpFilter.class);
    }
}

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
package org.apache.catalina.servlets;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.filters.ExpiresFilter;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.junit.Assert;

public class WebdavServletRfcSectionBase extends TomcatBaseTest {

    protected class WebdavClient extends SimpleHttpClient {
        final String DEFAULT_MESSAGE_OF_RESPONSE_PREDICATION = "Unexpected response";

        final Predicate<WebdavClient> DEFAULT_SC_PREDICATE = c -> c.getStatusCode() >= 200 && c.getStatusCode() < 300;

        public WebdavClient() {
            super();
            this.setPort(WebdavServletRfcSectionBase.this.getPort());
        }

        /**
         * Copy resource from srcUri to destUri
         * 
         * @param srcUri
         * @param destUri
         * @param overwrite
         * @param message   if unacceptable response received.
         * @param predicate to check acceptable response
         * 
         * @throws Exception
         */
        public void copyResource(String srcUri, String destUri, boolean overwrite, String lockToken, String message,
                Predicate<WebdavClient> predicate) throws Exception {
            StringBuffer buf = new StringBuffer();
            buf.append("COPY " + srcUri + " HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF + "Destination: " +
                    "http://localhost:" + getPort() + destUri + CRLF + "Overwrite: " + (overwrite ? "T" : "F") + CRLF);
            if (StringUtils.isNotEmpty(lockToken)) {
                buf.append("Lock-Token: <" + lockToken + ">").append(CRLF);
            }
            buf.append(CRLF);
            setRequest(new String[] { buf.toString() });
            connect();
            processRequest(true);
            if (Objects.isNull(message)) {
                message = DEFAULT_MESSAGE_OF_RESPONSE_PREDICATION;
            }
            if (predicate == null) {
                predicate = DEFAULT_SC_PREDICATE;
            }
            if (predicate.test(this)) {
                return;
            } else {
                throw new AssertionError(
                        message + ", Failed to copyResource, actual status code was " + getStatusCode());
            }
        }
        /**
         * Gets content of specific resource.
         * @param uri
         * @param message
         * @param predicate
         * @return
         * @throws Exception
         */
        protected String getResource(String uri, String message, Predicate<WebdavClient> predicate) throws Exception {
            StringBuffer buf = new StringBuffer();
            buf.append("GET " + uri + " HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF + "Connection: Close" +
                    CRLF + CRLF);
            setRequest(new String[] { buf.toString() });
            connect();
            processRequest(true);
            if (Objects.isNull(message)) {
                message = DEFAULT_MESSAGE_OF_RESPONSE_PREDICATION;
            }
            if (predicate == null) {
                predicate = DEFAULT_SC_PREDICATE;
            }
            if (predicate.test(this)) {
                return getResponseBody();
            } else {
                throw new AssertionError(message + ", actual status code was " + getStatusCode());
            }
        }

        @Override
        public boolean isResponseBodyOK() {
            return true;
        }

        final String DEPTH_INFINITY = "infinity";

        /**
         * Make a lock call and returns lock token.
         * 
         * @param uri       relative uri of target resource, start with splash '/'
         * @param lockOwner identifier
         * @param exclusive <code>true</code> - exclusive, <code>false</code> - shared.
         * @param depth     of lock, only 0, infinity, or empty is supported.
         * @param message   if predication failed
         * @param predicate test result. If <code>null</code>, then default status code predication used.
         * 
         * @return lock token of successful response. <code>null</code> or throws exception if failed.
         * 
         * @throws Exception
         */
        protected String lockResource(String uri, String lockOwner, boolean exclusive, String depth, String message,
                Predicate<WebdavClient> predicate) throws Exception {

            String lockBody = buildLockBody(exclusive, lockOwner);
            StringBuffer buf = new StringBuffer();
            buf.append("LOCK " + uri + " HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF);
            if ("0".equals(depth)) {
                buf.append("Depth: 0").append(CRLF);
            } else if ("infinity".equals(depth) || StringUtils.isEmpty(depth)) {
                buf.append("Depth: infinity").append(CRLF);
            } else {
                // Unsupported value
                buf.append("Depth: ").append(depth).append(CRLF);
                predicate = predicate.and(c -> c.getStatusCode() >= 400 && c.getStatusCode() < 500);
            }
            buf.append("Content-Length: " + lockBody.length() + CRLF + "Connection: Close" + CRLF + CRLF + lockBody);
            /* Lock on a mapped url */
            setRequest(new String[] { buf.toString() });

            connect(600000,600000);
            processRequest(true);
            if (Objects.isNull(message)) {
                message = DEFAULT_MESSAGE_OF_RESPONSE_PREDICATION;
            }
            if (predicate == null) {
                predicate = DEFAULT_SC_PREDICATE;
            }
            if (predicate.test(this)) {
                return parseLockToken();
            } else {
                throw new AssertionError(message + ", actual status code was " + getStatusCode());
            }
        }

        /**
         * Parse lock token from client headers.
         * 
         * @return valid lock token. returns <code>null</code> if not found.
         */
        protected String parseLockToken() {
            String lockToken = null;
            /* Lock-Token: <${lock_tocken}> */
            final String ltPrefix = "Lock-Token: <";
            final String ltSuffix = ">";
            for (String header : getResponseHeaders()) {
                if (header.startsWith(ltPrefix)) {
                    lockToken = header.substring(ltPrefix.length(), header.length() - ltSuffix.length());
                }
            }
            return lockToken;
        }

        /**
         * @param uri
         * @param contentType
         * @param content
         * @param ifHeaderValue
         * @param message
         * @param predicate
         * 
         * @throws Exception
         */
        protected void putResource(String uri, String contentType, String content, String ifHeaderValue, String message,
                Predicate<WebdavClient> predicate) throws Exception {
            Objects.requireNonNull(content);
            StringBuffer buf = new StringBuffer();
            buf.append("PUT " + uri + " HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF + "Content-Length: " +
                    content.length() + CRLF);
            if (StringUtils.isNotEmpty(contentType)) {
                buf.append("Content-Type: " + contentType).append(CRLF);
            }
            if (StringUtils.isNotEmpty(ifHeaderValue)) {
                buf.append("If: " + ifHeaderValue + CRLF);
            }
            buf.append("Connection: Close" + CRLF + CRLF + content);
            setRequest(new String[] { buf.toString() });
            connect();
            processRequest(true);
            if (Objects.isNull(message)) {
                message = DEFAULT_MESSAGE_OF_RESPONSE_PREDICATION;
            }
            if (predicate == null) {
                predicate = DEFAULT_SC_PREDICATE;
            }
            if (predicate.test(this)) {
                return;
            } else {
                throw new AssertionError(message + ", actual status code was " + getStatusCode());
            }
        }

        /**
         * Refresh a lock.
         * 
         * @param uri
         * @param lockOwner
         * @param exclusive
         * @param ifToken
         * @param message   if unacceptable response received.
         * @param predicate to check acceptable response
         * 
         * @throws Exception
         */
        protected void refreshResourceLock(String uri, String ifHeaderValue, String message,
                Predicate<WebdavClient> predicate) throws Exception {
            /* Lock on a mapped url */
            Objects.requireNonNull(ifHeaderValue);
            setRequest(new String[] { "LOCK " + uri + " HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF +
                    "Connection: Close" + CRLF + "If: " + ifHeaderValue + CRLF + CRLF });
            connect();
            processRequest(true);
            if (Objects.isNull(message)) {
                message = DEFAULT_MESSAGE_OF_RESPONSE_PREDICATION;
            }
            if (predicate == null) {
                predicate = DEFAULT_SC_PREDICATE;
            }
            if (predicate.test(this)) {
                return;
            } else {
                throw new AssertionError(message + ", actual status code was " + getStatusCode());
            }
        }

        /**
         * Make a unlock call
         * 
         * @param uri       relative uri of target resource, start with splash '/'
         * @param lockToken owner received via lock previous
         * @param client    of http impl.
         * @param message   if unacceptable response received.
         * @param predicate to check acceptable response
         * 
         * @throws Exception
         */
        protected void unlockResource(String uri, String lockToken, String message, Predicate<WebdavClient> predicate)
                throws Exception {
            if (StringUtils.isEmpty(lockToken)) {
                setRequest(new String[] { "UNLOCK " + uri + " HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF +
                        "Connection: Close" + CRLF + CRLF });
            } else {
                setRequest(new String[] { "UNLOCK " + uri + " HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF +
                        "Connection: Close" + CRLF + "Lock-Token: <" + lockToken + ">" + CRLF + CRLF });
            }
            connect();
            processRequest(true);
            if (Objects.isNull(message)) {
                message = DEFAULT_MESSAGE_OF_RESPONSE_PREDICATION;
            }
            if (predicate == null) {
                predicate = DEFAULT_SC_PREDICATE;
            }
            if (predicate.test(this)) {
                return;
            } else {
                throw new AssertionError(message + ", Failed to unlock(lockToken=" + lockToken +
                        "), actual status code was " + getStatusCode());
            }
        }

        /**
         * Make a unlock call
         * 
         * @param uri       relative uri of target resource, start with splash '/'
         * @param lockToken owner received via lock previous
         * @param client    of http impl.
         * @param message   if unacceptable response received.
         * @param predicate to check acceptable response
         * 
         * @throws Exception
         */
        protected void unlockResource(String uri, String lockToken, String ifHeaderValue, String message,
                Predicate<WebdavClient> predicate) throws Exception {

            setRequest(new String[] { "UNLOCK " + uri + " HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF +
                    "Connection: Close" + CRLF + "Lock-Token: <" + lockToken + ">" + CRLF + "If: " + ifHeaderValue +
                    "" + CRLF + CRLF });
            connect();
            processRequest(true);
            if (Objects.isNull(message)) {
                message = DEFAULT_MESSAGE_OF_RESPONSE_PREDICATION;
            }
            if (predicate == null) {
                predicate = DEFAULT_SC_PREDICATE;
            }
            if (predicate.test(this)) {
                return;
            } else {
                throw new AssertionError(message + ", Failed to unlock(lockToken=" + lockToken +
                        "), actual status code was " + getStatusCode());
            }
        }

        protected void mkcolResource(String destUri, String message, Predicate<WebdavClient> expect) throws Exception {
            setRequest(new String[] {
                    "MKCOL " + destUri + " HTTP/1.1" + CRLF + "Host: localhost:" + getPort() + CRLF + CRLF + CRLF });
            connect();
            processRequest(true);
            if (Objects.isNull(message)) {
                message = DEFAULT_MESSAGE_OF_RESPONSE_PREDICATION;
            }
            if (expect == null) {
                expect = DEFAULT_SC_PREDICATE;
            }
            if (expect.test(this)) {
                return;
            } else {
                throw new AssertionError(
                        message + ", Failed to mkcol on'" + destUri + "', actual status code was " + getStatusCode());
            }
        }

        /**
         * Checks whether response is ready for enable cache.
         * 
         * @return
         */
        protected boolean isResponseCacheable() {
            for (String header : getResponseHeaders()) {
                String lowerHeader = header.toLowerCase();
                if (lowerHeader.startsWith("cache-control")) {
                    if (!lowerHeader.contains("no-store")) {
                        // middle tier may enable cache.
                        return true;
                    }
                    int firstIndexOf = lowerHeader.indexOf("max-age=");
                    if (firstIndexOf >= 0 && !lowerHeader.endsWith("max-age=0") &&
                            !lowerHeader.contains("max-age=0,")) {
                        // defines max-age>0
                        return true;
                    }
                } else if (lowerHeader.startsWith("expires")) {
                    if (lowerHeader.length() > "expires: ".length()) {
                        String expireAt = header.substring("Expires: ".length());
                        long ts = FastHttpDateFormat.parseDate(expireAt);
                        if (ts > 1000L + System.currentTimeMillis()) {
                            return true;
                        }
                    }
                } else if (lowerHeader.startsWith("etag:")) {
                    return true;
                }
            }
            return false;
        }
    }

    private File tempWebapp = null;

    protected String buildLockBody(boolean isExclusive, String owner) {
        StringBuffer buf = new StringBuffer();
        buf.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
        buf.append("<D:lockinfo xmlns:D='DAV:'>");
        buf.append("<D:lockscope>");
        if (isExclusive) {
            buf.append("<D:exclusive/>");
        } else {
            buf.append("<D:shared/>");
        }
        buf.append("</D:lockscope>");
        buf.append("<D:locktype><D:write/></D:locktype>");
        buf.append("<D:owner>").append(owner).append("</D:owner>");
        buf.append("</D:lockinfo>");
        return buf.toString();
    }

    protected String getWebappAbsolutePath() {
        return tempWebapp.getAbsolutePath();
    }

    /**
     * Look up for specific header lines
     * 
     * @param headers a list of source header lines.
     * @param tester  a predicate
     * 
     * @return matched header lines
     */
    protected List<String> matchHeaders(List<String> headers, Predicate<String> tester) {
        Objects.requireNonNull(headers);
        Objects.requireNonNull(tester);
        List<String> matched = new ArrayList<String>();
        for (String headerLine : headers) {
            if (tester.test(headerLine)) {
                matched.add(headerLine);
            }
        }
        return matched;
    }

    protected Context prepareContext(String ctxDirName) {
        Tomcat tomcat = getTomcatInstance();

        // Create a temp webapp that can be safely written to
        tempWebapp = new File(getTemporaryDirectory(), ctxDirName);
        Assert.assertTrue("Failed to mkdirs on " + tempWebapp.getAbsolutePath() + ".", tempWebapp.mkdirs());
        Context ctxt = tomcat.addContext("", tempWebapp.getAbsolutePath());
        return ctxt;
    }

    protected void prepareExpirePolicy(Context ctx) {
        Objects.requireNonNull(ctx);
        FilterDef filterDef = new FilterDef();
        filterDef.addInitParameter("ExpiresDefault", "access plus 1 month");
        filterDef.addInitParameter("ExpiresByType text/html", "access plus 1 month 15 days 2 hours");
        filterDef.addInitParameter("ExpiresByType application/xml", "access plus 1 month 15 days 2 hours");
        filterDef.addInitParameter("ExpiresByType image/gif", "modification plus 5 hours 3 minutes");
        filterDef.addInitParameter("ExpiresByType image/jpg", "A10000");
        filterDef.addInitParameter("ExpiresByType video/mpeg", "M20000");
        filterDef.addInitParameter("ExpiresExcludedResponseStatusCodes", "304, 503");
        filterDef.setFilter(new ExpiresFilter());
        filterDef.setFilterClass(ExpiresFilter.class.getName());
        filterDef.setFilterName(ExpiresFilter.class.getName());

        ctx.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(ExpiresFilter.class.getName());
        filterMap.addURLPatternDecoded("*");

        ctx.addFilterMap(filterMap);
    }

    /**
     * Prepare a temp webapp for webdav
     * 
     * @param listings
     * @param readonly
     * 
     * @throws Exception
     */
    protected void prepareWebdav(Context ctx, boolean listings, boolean readonly) throws Exception {
        Objects.requireNonNull(ctx);
        Wrapper webdavServlet = Tomcat.addServlet(ctx, "webdav", new WebdavServlet());
        webdavServlet.addInitParameter("listings", listings ? "true" : "false");
        webdavServlet.addInitParameter("secret", "foo");
        webdavServlet.addInitParameter("readonly", readonly ? "true" : "false");
        ctx.addServletMappingDecoded("/*", "webdav");
    }
}
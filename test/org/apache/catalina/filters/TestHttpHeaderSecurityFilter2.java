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
package org.apache.catalina.filters;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.http.FastHttpDateFormat;

/*
 * Tests of the Content-Security-Policy support of HttpHeaderSecurityFilter that need a running container: the
 * interaction with RequestDispatcher.include() and with conditional requests.
 */
public class TestHttpHeaderSecurityFilter2 extends TomcatBaseTest {

    private static final String CSP = "Content-Security-Policy";
    private static final String FILTER_POLICY = "default-src 'none'";
    private static final String NONCE_POLICY = "script-src 'nonce-{nonce}'";
    private static final long LAST_MODIFIED = 1_700_000_000_000L;


    /*
     * The container ignores reset() and header changes made by an included resource, so the policies set by the
     * including servlet before and after the include must all remain.
     */
    @Test
    public void testIncludeResetKeepsPolicies() throws Exception {
        doTestIncludeReset(true, new ResetFragmentServlet(), List.of("script-src 'none'", "img-src 'self'"));
    }


    @Test
    public void testIncludeIgnoredChangesKeepPolicies() throws Exception {
        doTestIncludeReset(true, new IgnoredChangesFragmentServlet(), List.of("script-src 'none'", "img-src 'self'"));
    }


    @Test
    public void testIncludeResetKeepsPoliciesWithoutOverride() throws Exception {
        doTestIncludeReset(false, new ResetFragmentServlet(),
                List.of(FILTER_POLICY, "script-src 'none'", "img-src 'self'"));
    }


    private void doTestIncludeReset(boolean allowOverride, HttpServlet fragment, List<String> expected)
            throws Exception {
        Context ctx = getProgrammaticRootContext();
        addFilter(ctx, FILTER_POLICY, allowOverride);
        addServlet(ctx, "/page", new HttpServlet() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                resp.setHeader(CSP, "script-src 'none'");
                req.getRequestDispatcher("/fragment").include(req, resp);
                resp.addHeader(CSP, "img-src 'self'");
            }
        });
        addServlet(ctx, "/fragment", fragment);

        Map<String,List<String>> resHead = request("/page", null, HttpServletResponse.SC_OK);
        assertPolicies(expected, resHead);
    }


    /*
     * Header changes ignored by the container must not change what the including servlet sees. Only the filter's
     * policy is present after the include, so a component that only sets a policy if none is present sets its own.
     */
    @Test
    public void testIncludeDoesNotChangeVisibility() throws Exception {
        Context ctx = getProgrammaticRootContext();
        addFilter(ctx, FILTER_POLICY, true);
        addServlet(ctx, "/page", new HttpServlet() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                req.getRequestDispatcher("/fragment").include(req, resp);
                boolean present = resp.containsHeader(CSP);
                String value = resp.getHeader(CSP);
                if (!present) {
                    resp.setHeader(CSP, "script-src 'self'");
                }
                resp.getWriter().print(present + ":" + value);
            }
        });
        addServlet(ctx, "/fragment", new IgnoredChangesFragmentServlet());

        ByteChunk body = new ByteChunk();
        Map<String,List<String>> resHead = request("/page", body, HttpServletResponse.SC_OK);
        Assert.assertEquals("false:null", body.toString());
        assertPolicies(List.of("script-src 'self'"), resHead);
    }


    /*
     * Two filters with the same policy. The outer filter allows the application to override its policy, the inner
     * filter does not. The application cannot remove the inner filter's policy, although the outer filter hides a
     * policy with the same text.
     */
    @Test
    public void testNestedFiltersApplicationCannotRemovePolicy() throws Exception {
        doTestNestedFilters(null, List.of(FILTER_POLICY));
    }


    @Test
    public void testNestedFiltersApplicationPolicyAdded() throws Exception {
        doTestNestedFilters("img-src 'self'", List.of(FILTER_POLICY, "img-src 'self'"));
    }


    private void doTestNestedFilters(String applicationPolicy, List<String> expected) throws Exception {
        Context ctx = getProgrammaticRootContext();
        addFilter(ctx, "outer", FILTER_POLICY, true);
        addFilter(ctx, "inner", FILTER_POLICY, false);
        addServlet(ctx, "/page", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                resp.setHeader(CSP, applicationPolicy);
            }
        });

        Map<String,List<String>> resHead = request("/page", null, HttpServletResponse.SC_OK);
        assertPolicies(expected, resHead);
    }


    /*
     * A policy set by the application with the same text as the filter's policy belongs to the application: it is
     * visible and a policy added later does not replace it.
     */
    @Test
    public void testIdenticalApplicationPolicyIsKept() throws Exception {
        Context ctx = getProgrammaticRootContext();
        addFilter(ctx, FILTER_POLICY, true);
        addServlet(ctx, "/page", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setHeader(CSP, FILTER_POLICY);
                boolean visible = resp.containsHeader(CSP);
                resp.addHeader(CSP, "img-src 'self'");
                resp.getWriter().print(visible);
            }
        });

        ByteChunk body = new ByteChunk();
        Map<String,List<String>> resHead = request("/page", body, HttpServletResponse.SC_OK);
        Assert.assertEquals("true", body.toString());
        assertPolicies(List.of(FILTER_POLICY, "img-src 'self'"), resHead);
    }


    @Test
    public void testIdenticalApplicationPolicyIsVisibleWithoutOverride() throws Exception {
        Context ctx = getProgrammaticRootContext();
        addFilter(ctx, FILTER_POLICY, false);
        addServlet(ctx, "/page", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.addHeader(CSP, FILTER_POLICY);
                resp.getWriter().print(resp.getHeaders(CSP));
            }
        });

        ByteChunk body = new ByteChunk();
        Map<String,List<String>> resHead = request("/page", body, HttpServletResponse.SC_OK);
        Assert.assertEquals("[" + FILTER_POLICY + "]", body.toString());
        assertPolicies(List.of(FILTER_POLICY, FILTER_POLICY), resHead);
    }


    /*
     * A 304 response must not replace the policy a cache stored with a page by a policy with a new nonce.
     */
    @Test
    public void testNotModifiedOmitsNoncePolicy() throws Exception {
        Context ctx = getProgrammaticRootContext();
        addFilter(ctx, NONCE_POLICY, true);
        addServlet(ctx, "/page", new LastModifiedServlet());

        ByteChunk body = new ByteChunk();
        Map<String,List<String>> resHead = request("/page", body, HttpServletResponse.SC_OK);
        List<String> policies = getPolicies(resHead);
        Assert.assertEquals(1, policies.size());
        String nonce = policies.getFirst().substring("script-src 'nonce-".length(), policies.getFirst().length() - 1);
        Assert.assertTrue(body.toString(), body.toString().contains("nonce=\"" + nonce + "\""));

        resHead = request("/page", null, HttpServletResponse.SC_NOT_MODIFIED, "If-Modified-Since",
                FastHttpDateFormat.formatDate(LAST_MODIFIED));
        assertPolicies(List.of(), resHead);
    }


    @Test
    public void testNotModifiedStaticResourceOmitsNoncePolicy() throws Exception {
        doTestNotModifiedStaticResource(NONCE_POLICY, List.of());
    }


    /*
     * Without a nonce, the policy sent with a 304 response matches the stored page and is still sent.
     */
    @Test
    public void testNotModifiedStaticResourceKeepsPolicyWithoutNonce() throws Exception {
        doTestNotModifiedStaticResource(FILTER_POLICY, List.of(FILTER_POLICY));
    }


    private void doTestNotModifiedStaticResource(String policy, List<String> expected) throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File docBase = new File(getTemporaryDirectory(), "csp-not-modified");
        Assert.assertTrue(docBase.isDirectory() || docBase.mkdirs());
        addDeleteOnTearDown(docBase);
        Files.writeString(new File(docBase, "index.html").toPath(), "<!DOCTYPE html><title>static</title>",
                StandardCharsets.UTF_8);
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());
        addFilter(ctx, policy, true);
        addServlet(ctx, "/", new DefaultServlet());

        Map<String,List<String>> resHead = request("/index.html", null, HttpServletResponse.SC_OK);
        String eTag = getSingleHeader("ETag", resHead);
        Assert.assertNotNull(eTag);

        resHead = request("/index.html", null, HttpServletResponse.SC_NOT_MODIFIED, "If-None-Match", eTag);
        assertPolicies(expected, resHead);
    }


    /*
     * Only the filter's policy is removed from a 304 response, not the policies set by the application.
     */
    @Test
    public void testNotModifiedKeepsApplicationPolicy() throws Exception {
        Context ctx = getProgrammaticRootContext();
        addFilter(ctx, NONCE_POLICY, false);
        addServlet(ctx, "/page", new HttpServlet() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                resp.addHeader(CSP, "img-src 'self'");
                resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            }
        });

        Map<String,List<String>> resHead = request("/page", null, HttpServletResponse.SC_NOT_MODIFIED);
        assertPolicies(List.of("img-src 'self'"), resHead);
    }


    /*
     * The container ignores a status set by an included resource, so the response is not a 304 response and keeps
     * the filter's policy.
     */
    @Test
    public void testIncludeNotModifiedStatusKeepsNoncePolicy() throws Exception {
        Context ctx = getProgrammaticRootContext();
        addFilter(ctx, NONCE_POLICY, true);
        addServlet(ctx, "/page", new HttpServlet() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                req.getRequestDispatcher("/fragment").include(req, resp);
                resp.getWriter().print(req.getAttribute(Globals.CSP_NONCE_ATTR));
            }
        });
        addServlet(ctx, "/fragment", new IgnoredChangesFragmentServlet());

        ByteChunk body = new ByteChunk();
        Map<String,List<String>> resHead = request("/page", body, HttpServletResponse.SC_OK);
        assertPolicies(List.of("script-src 'nonce-" + body + "'"), resHead);
    }


    private static void addFilter(Context ctx, String policy, boolean allowOverride) {
        addFilter(ctx, "httpHeaderSecurity", policy, allowOverride);
    }


    private static void addFilter(Context ctx, String filterName, String policy, boolean allowOverride) {
        FilterDef filterDef = new FilterDef();
        filterDef.setFilterClass(HttpHeaderSecurityFilter.class.getName());
        filterDef.setFilterName(filterName);
        filterDef.addInitParameter("cspEnabled", "true");
        filterDef.addInitParameter("cspPolicy", policy);
        filterDef.addInitParameter("cspAllowOverride", Boolean.toString(allowOverride));
        ctx.addFilterDef(filterDef);
        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName(filterName);
        filterMap.addURLPattern("/*");
        ctx.addFilterMap(filterMap);
    }


    private static void addServlet(Context ctx, String pattern, HttpServlet servlet) {
        String name = "servlet" + pattern.replace('/', '_');
        Tomcat.addServlet(ctx, name, servlet);
        ctx.addServletMapping(pattern, name);
    }


    private Map<String,List<String>> request(String path, ByteChunk body, int expectedStatus, String... reqHeaders)
            throws Exception {
        Tomcat tomcat = getTomcatInstance();
        if (tomcat.getServer().getState() != LifecycleState.STARTED) {
            tomcat.start();
        }
        Map<String,List<String>> reqHead = new HashMap<>();
        for (int i = 0; i < reqHeaders.length; i += 2) {
            reqHead.put(reqHeaders[i], List.of(reqHeaders[i + 1]));
        }
        Map<String,List<String>> resHead = new HashMap<>();
        int rc = getUrl("http://localhost:" + getPort() + path, body == null ? new ByteChunk() : body, reqHead,
                resHead);
        Assert.assertEquals(expectedStatus, rc);
        return resHead;
    }


    private static List<String> getPolicies(Map<String,List<String>> resHead) {
        List<String> policies = new ArrayList<>();
        for (Map.Entry<String,List<String>> entry : resHead.entrySet()) {
            if (CSP.equalsIgnoreCase(entry.getKey())) {
                policies.addAll(entry.getValue());
            }
        }
        return policies;
    }


    /*
     * Compares the policies ignoring their order, as HttpURLConnection does not preserve the order of repeated header
     * fields.
     */
    private static void assertPolicies(List<String> expected, Map<String,List<String>> resHead) {
        List<String> sortedExpected = new ArrayList<>(expected);
        Collections.sort(sortedExpected);
        List<String> actual = getPolicies(resHead);
        Collections.sort(actual);
        Assert.assertEquals(sortedExpected, actual);
    }


    /*
     * Attempts to reset the response. As an included resource, this is ignored by the container.
     */
    private static final class ResetFragmentServlet extends HttpServlet {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
            resp.reset();
        }
    }


    /*
     * Attempts to reset the response, change the policy and the status. As an included resource, all of these are
     * ignored by the container.
     */
    private static final class IgnoredChangesFragmentServlet extends HttpServlet {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
            resp.reset();
            resp.setHeader(CSP, "default-src *");
            resp.addHeader(CSP, "default-src *");
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        }
    }


    private static final class LastModifiedServlet extends HttpServlet {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        protected long getLastModified(HttpServletRequest req) {
            return LAST_MODIFIED;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/html");
            resp.getWriter().print("<script nonce=\"" + req.getAttribute(Globals.CSP_NONCE_ATTR) + "\"></script>");
        }
    }
}

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
import java.util.Collections;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestFilterValve extends TomcatBaseTest {


    @Test
    public void testFilterPassthrough() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        FilterValve valve = new FilterValve();
        valve.setFilterClass(PassthroughFilter.class.getName());
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(HelloWorldServlet.RESPONSE_TEXT, res.toString());
    }


    @Test
    public void testFilterBlocks() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        FilterValve valve = new FilterValve();
        valve.setFilterClass(BlockingFilter.class.getName());
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        ByteChunk res = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort(), res, null);

        Assert.assertEquals(HttpServletResponse.SC_FORBIDDEN, rc);
    }

    @Test
    public void testFilterWrappingRequestThrows() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        Tomcat.addServlet(ctx, "hello", new HelloWorldServlet());
        ctx.addServletMappingDecoded("/", "hello");

        FilterValve valve = new FilterValve();
        valve.setFilterClass(WrappingFilter.class.getName());
        ctx.getPipeline().addValve(valve);

        tomcat.start();

        int rc = getUrl("http://localhost:" + getPort(), new ByteChunk(), null);

        Assert.assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, rc);
    }


    @Test(expected = LifecycleException.class)
    public void testNullFilterClassThrowsOnStart() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        FilterValve valve = new FilterValve();
        // Do NOT set filterClassName
        ctx.getPipeline().addValve(valve);

        tomcat.start();
    }


    @Test(expected = LifecycleException.class)
    public void testInvalidFilterClassThrowsOnStart() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context ctx = getProgrammaticRootContext();

        FilterValve valve = new FilterValve();
        valve.setFilterClass("com.nonexistent.FakeFilter");
        ctx.getPipeline().addValve(valve);

        tomcat.start();
    }


    @Test
    public void testGetFilterNameReturnsNull() {
        FilterValve valve = new FilterValve();
        Assert.assertNull(valve.getFilterName());
    }


    @Test
    public void testInitParams() {
        FilterValve valve = new FilterValve();

        valve.addInitParam("key1", "value1");
        valve.addInitParam("key2", "value2");

        Assert.assertEquals("value1", valve.getInitParameter("key1"));
        Assert.assertEquals("value2", valve.getInitParameter("key2"));
        Assert.assertNull(valve.getInitParameter("nonexistent"));

        List<String> names = Collections.list(valve.getInitParameterNames());
        Assert.assertEquals(2, names.size());
        Assert.assertTrue(names.contains("key1"));
        Assert.assertTrue(names.contains("key2"));
    }


    @Test
    public void testInitParamsEmpty() {
        FilterValve valve = new FilterValve();

        Assert.assertNull(valve.getInitParameter("anything"));
        Assert.assertFalse(valve.getInitParameterNames().hasMoreElements());
    }


    @Test
    public void testGetSetFilterClassName() {
        FilterValve valve = new FilterValve();

        Assert.assertNull(valve.getFilterClassName());

        valve.setFilterClassName("com.example.MyFilter");
        Assert.assertEquals("com.example.MyFilter", valve.getFilterClassName());

        valve.setFilterClass("com.example.OtherFilter");
        Assert.assertEquals("com.example.OtherFilter", valve.getFilterClassName());
    }

    @Test(expected = IllegalStateException.class)
    public void testGetServletContextThrowsBeforeStart() {
        FilterValve valve = new FilterValve();
        valve.getServletContext();
    }


    /**
     * A Filter that passes the request through to the next element in the chain.
     */
    public static final class PassthroughFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                             FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
        }
    }


    /**
     * A Filter that blocks the request by sending a 403 response without calling chain.doFilter().
     */
    public static final class BlockingFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                             FilterChain chain) throws IOException, ServletException {
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    /**
     * A Filter that wraps the request before calling chain.doFilter(), which FilterValve explicitly forbids.
     */
    public static final class WrappingFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                             FilterChain chain) throws IOException, ServletException {
            HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper((HttpServletRequest) request);
            chain.doFilter(wrapped, response);
        }
    }

}

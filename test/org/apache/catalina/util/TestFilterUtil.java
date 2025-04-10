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
package org.apache.catalina.util;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.GenericServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

public class TestFilterUtil extends TomcatBaseTest {

    @Test
    public void testContextRootMappedFilter() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        CountingFilter countingFilter = new CountingFilter();

        FilterDef fd = new FilterDef();
        fd.setFilter(countingFilter);
        fd.setFilterName("CountingFilter");

        FilterMap fm = new FilterMap();
        fm.setFilterName(fd.getFilterName());
        fm.addURLPattern("");

        ctx.addFilterDef(fd);
        ctx.addFilterMap(fm);

        Wrapper w = tomcat.addServlet("", "Default", new DefaultServlet());
        w.addMapping("/");

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc;

        Assert.assertEquals(0, countingFilter.getCount());

        rc = getUrl("http://localhost:" + getPort(), bc, false);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(1, countingFilter.getCount());

        rc = getUrl("http://localhost:" + getPort() + "/", bc, false);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(2, countingFilter.getCount());

        rc = getUrl("http://localhost:" + getPort() + "/not-a-context-root", bc, false);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertEquals(2, countingFilter.getCount());
    }


    public static class CountingFilter extends GenericFilter {

        private static final long serialVersionUID = 1L;

        private AtomicInteger count = new AtomicInteger(0);


        public int getCount() {
            return count.get();
        }


        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            count.incrementAndGet();
            chain.doFilter(request, response);
        }
    }


    public static class DefaultServlet extends GenericServlet {

        private static final long serialVersionUID = 1L;

        @Override
        public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
            res.setContentType("text/plain");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().print("OK");
        }
    }
}

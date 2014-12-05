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
package org.apache.catalina.core;

import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.catalina.valves.TesterAccessLogValve;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import org.junit.Assert;
import org.junit.Test;

public class TestApplicationFilterChain extends TomcatBaseTest {

    @Test
    public void testBug57284() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "HelloWorld", new HelloWorldServlet());
        ctx.addServletMapping("/", "HelloWorld");

        FilterDef filterDef = new FilterDef();
        filterDef.setFilterClass(AsyncFilter.class.getName());
        filterDef.setFilterName("async");
        ctx.addFilterDef(filterDef);

        FilterMap filterMap = new FilterMap();
        filterMap.setFilterName("async");
        filterMap.addServletName("HelloWorld");
        ctx.addFilterMap(filterMap);

        TesterAccessLogValve alv = new TesterAccessLogValve();
        ctx.getPipeline().addValve(alv);

        tomcat.start();

        // Call the servlet once
        ByteChunk bc = getUrl("http://localhost:" + getPort() + "/");
        String body = bc.toString();
        Assert.assertNotNull(body);
        Assert.assertTrue(body, body.contains("Hello World"));

        alv.validateAccessLog(1, 200, 0, 500);
    }


    static class AsyncFilter implements Filter {

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            // NO-OP
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                FilterChain chain) throws IOException, ServletException {

            AsyncContext ac = request.startAsync();
            ac.start(new Runnable() {

                @Override
                public void run() {
                    try {
                        chain.doFilter(request, response);
                        ac.complete();
                    } catch (IOException | ServletException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void destroy() {
            // NO-OP
        }
    }
}

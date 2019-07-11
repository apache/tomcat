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
package org.apache.catalina.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Written for the specific test case of async Servlet, dispatches to sync
 * Servlet that then tries to call startAsync() but covers all combinations
 * for completeness.
 */
@RunWith(Parameterized.class)
public class TestAsyncContextImplDispatch extends TomcatBaseTest {

    @Parameterized.Parameters(name = "{index}: tgt-sup [{0}], dis-sup [{1}], dis-st [{2}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        for (Boolean targetAsyncSupported : booleans) {
            for (Boolean dispatchAsyncSupported : booleans) {
                for (Boolean dispatchAsyncStart : booleans) {
                    Boolean allowed = Boolean.valueOf(!dispatchAsyncStart.booleanValue() ||
                            targetAsyncSupported.booleanValue() && dispatchAsyncSupported.booleanValue() &&
                                    dispatchAsyncStart.booleanValue());

                    parameterSets.add(new Object[] { targetAsyncSupported, dispatchAsyncSupported, dispatchAsyncStart, allowed} );
                }
            }
        }

        return parameterSets;
    }

    @Parameter(0)
    public boolean targetAsyncSupported;
    @Parameter(1)
    public boolean dispatchAsyncSupported;
    @Parameter(2)
    public boolean dispatchAsyncStart;
    @Parameter(3)
    public boolean allowed;


    @Test
    public void testSendError() throws Exception {
        // Setup Tomcat instance
        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Wrapper w1 = Tomcat.addServlet(ctx, "target", new TesterServlet());
        w1.setAsyncSupported(targetAsyncSupported);
        ctx.addServletMappingDecoded("/target", "target");

        Wrapper w2 = Tomcat.addServlet(ctx, "dispatch", new TesterDispatchServlet(dispatchAsyncStart));
        w2.setAsyncSupported(dispatchAsyncSupported);
        ctx.addServletMappingDecoded("/dispatch", "dispatch");

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc;

        rc = getUrl("http://localhost:" + getPort() + "/target", bc, null, null);

        String body = bc.toString();

        if (allowed) {
            Assert.assertEquals(200, rc);
            Assert.assertEquals("OK", body);
        } else {
            Assert.assertEquals(500, rc);
        }
    }


    public static class TesterServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            req.getRequestDispatcher("/dispatch").forward(req, resp);
        }
    }


    public static class TesterDispatchServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final  boolean start;

        public TesterDispatchServlet(boolean start) {
            this.start = start;
        }


        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            if (start) {
                AsyncContext ac = req.startAsync();
                ac.complete();
            }

            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("OK");
        }
    }
}

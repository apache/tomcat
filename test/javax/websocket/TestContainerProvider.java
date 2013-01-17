/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.websocket;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Host;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestContainerProvider extends TomcatBaseTest {

    /*
     * Obtain a reference to the client container from a web app.
     * Stop the web app.
     * Make sure that there is no memory leak.
     */
    @Test
    public void testGetClientContainer() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        // Must have a real docBase - just use temp
        StandardContext ctx = (StandardContext)
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));

        // Map the test Servlet
        GetClientContainerServlet ccServlet = new GetClientContainerServlet();
        Tomcat.addServlet(ctx, "ccServlet", ccServlet);
        ctx.addServletMapping("/", "ccServlet");

        tomcat.start();

        ByteChunk body = getUrl("http://localhost:" + getPort() + "/");

        Assert.assertEquals("PASS", body.toString());

        Host host = tomcat.getHost();
        host.removeChild(ctx);

        String[] leaks = ((StandardHost) host).findReloadedContextMemoryLeaks();

        Assert.assertEquals(0, leaks.length);
    }

    private static class GetClientContainerServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            WebSocketContainer wsc = ContainerProvider.getClientContainer();

            resp.setContentType("text/plain");

            if (wsc == null) {
                resp.getWriter().print("FAIL");
            } else {
                resp.getWriter().print("PASS");
            }
        }
    }
}

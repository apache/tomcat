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

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/*
 * The time is passed to the valve, so the thresholds are tested without having to wait.
 */
public class TestAccessLogValveMinRequestDuration extends TomcatBaseTest {

    private static final long MILLISECOND = 1_000_000L;

    // Real requests do not take this long
    private static final int MINUTE = 60_000;

    private static final String ATTRIBUTE = "slow";


    private static final class TextValve extends AbstractAccessLogValve {

        final AtomicInteger lines = new AtomicInteger();

        @Override
        public void log(CharArrayWriter message) {
            lines.incrementAndGet();
        }
    }


    private static final class JsonValve extends JsonAccessLogValve {

        final AtomicInteger lines = new AtomicInteger();

        @Override
        public void log(CharArrayWriter message) {
            lines.incrementAndGet();
        }
    }


    private static final class ExtendedValve extends ExtendedAccessLogValve {

        final AtomicInteger lines = new AtomicInteger();

        @Override
        public void log(CharArrayWriter message) {
            lines.incrementAndGet();
        }
    }


    private TextValve startTextValve(int minRequestDuration) throws Exception {
        TextValve valve = new TextValve();
        valve.setContainer(getTomcatInstance().getHost());
        valve.setPattern("%D");
        valve.setMinRequestDuration(minRequestDuration);
        valve.start();
        return valve;
    }


    @Test
    public void testDefaultLogsEveryRequest() throws Exception {
        TextValve valve = startTextValve(0);
        Assert.assertEquals(0, valve.getMinRequestDuration());
        valve.log(null, null, 0);
        valve.log(null, null, 1);
        Assert.assertEquals(2, valve.lines.get());
    }


    @Test
    public void testThresholdBoundary() throws Exception {
        TextValve valve = startTextValve(5);
        Assert.assertEquals(5, valve.getMinRequestDuration());
        valve.log(null, null, 0);
        valve.log(null, null, 5 * MILLISECOND - 1);
        Assert.assertEquals(0, valve.lines.get());
        // A request that takes exactly the minimum time is logged
        valve.log(null, null, 5 * MILLISECOND);
        valve.log(null, null, 5 * MILLISECOND + 1);
        Assert.assertEquals(2, valve.lines.get());
    }


    @Test
    public void testLargestThresholdDoesNotOverflow() throws Exception {
        TextValve valve = startTextValve(Integer.MAX_VALUE);
        Assert.assertEquals(Integer.MAX_VALUE, valve.getMinRequestDuration());
        valve.log(null, null, Integer.MAX_VALUE * MILLISECOND - 1);
        Assert.assertEquals(0, valve.lines.get());
        valve.log(null, null, Integer.MAX_VALUE * MILLISECOND);
        Assert.assertEquals(1, valve.lines.get());
    }


    @Test
    public void testNegativeThresholdIsRejected() {
        TextValve valve = new TextValve();
        valve.setMinRequestDuration(7);
        Assert.assertThrows(IllegalArgumentException.class, () -> valve.setMinRequestDuration(-1));
        // The previous value is kept
        Assert.assertEquals(7, valve.getMinRequestDuration());
    }


    @Test
    public void testJsonAndExtendedAccessLogValves() throws Exception {
        JsonValve json = new JsonValve();
        json.setPattern("%D");
        ExtendedValve extended = new ExtendedValve();
        extended.setPattern("time-taken");
        for (AccessLogValve valve : new AccessLogValve[] { json, extended }) {
            valve.setContainer(getTomcatInstance().getHost());
            valve.setDirectory(TEMP_DIR);
            valve.setMinRequestDuration(5);
            valve.start();
            valve.log(null, null, 5 * MILLISECOND - 1);
            valve.log(null, null, 5 * MILLISECOND);
            valve.stop();
        }
        Assert.assertEquals(1, json.lines.get());
        Assert.assertEquals(1, extended.lines.get());
    }


    /*
     * The threshold is applied in addition to the conditions, which are tested with real requests. The valves also log
     * the real requests, which never take as long as the threshold.
     */
    @Test
    public void testConditionsStillApply() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StandardContext ctx = (StandardContext) getProgrammaticRootContext();
        Tomcat.addServlet(ctx, "servlet", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                if (req.getParameter("mark") != null) {
                    req.setAttribute(ATTRIBUTE, Boolean.TRUE);
                }
            }
        });
        ctx.addServletMapping("/", "servlet");

        TextValve conditionIf = new TextValve();
        conditionIf.setConditionIf(ATTRIBUTE);
        TextValve conditionUnless = new TextValve();
        conditionUnless.setConditionUnless(ATTRIBUTE);
        Semaphore processed = new Semaphore(0);
        for (TextValve valve : new TextValve[] { conditionIf, conditionUnless }) {
            valve.setPattern("%D");
            valve.setMinRequestDuration(MINUTE);
            tomcat.getHost().getPipeline().addValve(valve);
        }
        // Logs the request with times either side of the threshold once the servlet has run
        tomcat.getHost().getPipeline().addValve(new ValveBase() {
            @Override
            public void invoke(Request request, Response response) throws IOException, ServletException {
                getNext().invoke(request, response);
                for (TextValve valve : new TextValve[] { conditionIf, conditionUnless }) {
                    valve.log(request, response, MINUTE * MILLISECOND - 1);
                    valve.log(request, response, MINUTE * MILLISECOND);
                }
                processed.release();
            }
        });
        tomcat.start();

        // Wait for the valve rather than the response, which may complete first
        Assert.assertEquals(HttpServletResponse.SC_OK,
                getUrl("http://localhost:" + getPort() + "/?mark=true", new ByteChunk(), null));
        Assert.assertTrue(processed.tryAcquire(10, TimeUnit.SECONDS));
        Assert.assertEquals(1, conditionIf.lines.get());
        Assert.assertEquals(0, conditionUnless.lines.get());

        Assert.assertEquals(HttpServletResponse.SC_OK,
                getUrl("http://localhost:" + getPort() + "/", new ByteChunk(), null));
        Assert.assertTrue(processed.tryAcquire(10, TimeUnit.SECONDS));
        Assert.assertEquals(1, conditionIf.lines.get());
        Assert.assertEquals(1, conditionUnless.lines.get());
    }
}

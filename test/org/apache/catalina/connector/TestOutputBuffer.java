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
package org.apache.catalina.connector;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestOutputBuffer extends TomcatBaseTest{

    @Test
    public void testWriteSpeed() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context root = tomcat.addContext("", TEMP_DIR);
        SingleCharWritingServlet servlet = new SingleCharWritingServlet();
        Wrapper w =
                Tomcat.addServlet(root, "singleCharWritingServlet", servlet);
        w.setAsyncSupported(true);
        root.addServletMapping("/", "singleCharWritingServlet");

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/", bc, null, null);

        assertEquals(200, rc);
        assertEquals(SingleCharWritingServlet.ITERATIONS, bc.getLength());

        long noBuffering = servlet.getLastRunNano();

        System.out.println(noBuffering);

        bc.recycle();

        rc = getUrl("http://localhost:" + getPort() + "/?useBuffering=y", bc,
                null, null);

        assertEquals(200, rc);
        assertEquals(SingleCharWritingServlet.ITERATIONS, bc.getLength());

        long buffering = servlet.getLastRunNano();

        System.out.println(buffering);
    }

    private static final class SingleCharWritingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        protected static final int ITERATIONS = 100000;

        // Not thread safe but will only be used with single calls.
        private volatile long lastRunNano = 0;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setContentType("text/plain");
            resp.setCharacterEncoding("ISO-8859-1");

            Writer w = resp.getWriter();

            // Wrap with a buffer if necessary
            String useBufferStr = req.getParameter("useBuffer");
            if (useBufferStr != null) {
                w = new BufferedWriter(w);
            }

            long start = System.nanoTime();

            for (int i = 0; i < ITERATIONS; i++) {
                w.write('x');
            }

            lastRunNano = System.nanoTime() - start;

            if (useBufferStr != null) {
                w.flush();
            }
        }

        public long getLastRunNano() {
            return lastRunNano;
        }
    }
}

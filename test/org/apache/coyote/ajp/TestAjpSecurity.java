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
package org.apache.coyote.ajp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.buf.HexUtils;

/*
 * Tests that can't easily be added to the {@code org.apache.tomcat.security} package because they need access to
 * package private classes.
 */
public class TestAjpSecurity extends AjpBaseTest {


    @Test
    public void testCVE_2026_103119() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        Wrapper w = Tomcat.addServlet(ctx, "largePayload", new LargePayloadServlet());
        w.setAsyncSupported(true);
        ctx.addServletMapping("/", "largePayload");

        Connector c = tomcat.getConnector();
        c.setProperty("socket.txBufSize", "4096");
        tomcat.start();

        SimpleAjpClient ajpClient = new SimpleAjpClient();
        ajpClient.setPort(getPort());
        ajpClient.connect();

        TestAbstractAjpProcessor.validateCpong(ajpClient.cping());

        ajpClient.setUri("/");
        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage();
        forwardMessage.end();

        Thread.sleep(1000);

        // Expected headers
        TesterAjpMessage responseHeaders = ajpClient.sendMessage(forwardMessage);
        TestAbstractAjpProcessor.validateResponseHeaders(responseHeaders, 200, "200");

        TesterAjpMessage msg = ajpClient.readMessage();
        byte type = msg.getByte();
        int responseByteCount = 0;
        while (type == 0x03) {
            responseByteCount += msg.getInt();
            msg = ajpClient.readMessage();
            type = msg.getByte();
        }

        Assert.assertTrue("Unexpected message type [" + HexUtils.toHexString(new byte[] { type }) + "]", type == 0x05);
        Assert.assertEquals("Incomplete response body", LargePayloadServlet.DEFAULT_SIZE, responseByteCount);
        ajpClient.disconnect();
    }


    static final class LargePayloadServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private static final int DEFAULT_SIZE = 1_382_300;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            int size = DEFAULT_SIZE;
            String requested = req.getParameter("size");
            if (requested != null) {
                size = Integer.parseInt(requested);
            }
            byte[] payload = buildPayload(size);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json");
            resp.setContentLengthLong(payload.length);

            AsyncContext async = req.startAsync();
            ServletOutputStream out = resp.getOutputStream();
            out.setWriteListener(new WriteListener() {
                private boolean written;

                @Override
                public void onWritePossible() throws IOException {
                    if (!written) {
                        written = true;
                        // One write of the whole array. Tomcat splits it into AJP packets and
                        // reaches the Content-Length on the final one, triggering the auto-close
                        // from inside this call.
                        out.write(payload);
                    }
                    if (out.isReady()) {
                        async.complete();
                    } else {
                        System.out.println("onWritePossible() NOT READY");
                    }
                }

                @Override
                public void onError(Throwable t) {
                    async.complete();
                }
            });
        }

        /** Deterministic filler with a recognisable JSON prefix. */
        private static byte[] buildPayload(int size) {
            byte[] out = new byte[size];
            byte[] prefix = "{\"payload\":\"".getBytes(StandardCharsets.UTF_8);
            System.arraycopy(prefix, 0, out, 0, Math.min(prefix.length, size));
            for (int i = prefix.length; i < size - 2; i++) {
                out[i] = (byte) ('a' + ((i - prefix.length) % 26));
            }
            if (size >= 2) {
                out[size - 2] = '"';
                out[size - 1] = '}';
            }
            return out;
        }
    }

}

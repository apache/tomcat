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
package org.apache.coyote;

import java.io.IOException;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Wrapper;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestIoTimeouts extends TomcatBaseTest {

    @Test
    public void testNonBlockingReadWithNoTimeout() {
        // Sends complete request in 3 packets
        ChunkedClient client = new ChunkedClient(true);
        client.doRequest();
        Assert.assertTrue(client.isResponse200());
        Assert.assertTrue(client.isResponseBodyOK());
        Assert.assertNull(EchoListener.t);
    }


    @Test
    public void testNonBlockingReadTimeout() {
        // Sends incomplete request (no end chunk) so read times out
        ChunkedClient client = new ChunkedClient(false);
        client.doRequest();
        Assert.assertFalse(client.isResponse200());
        Assert.assertFalse(client.isResponseBodyOK());
        // Socket will be closed before the error handler runs. Closing the
        // socket triggers the client code's return from the doRequest() method
        // above so we need to wait at this point for the error handler to be
        // triggered.
        int count = 0;
        // Shouldn't need to wait long but allow plenty of time as the CI
        // systems are sometimes slow.
        while (count < 100 && EchoListener.t == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }
            count++;
        }
        Assert.assertNotNull(EchoListener.t);
    }


    private class ChunkedClient extends SimpleHttpClient {

        private final boolean sendEndChunk;


        public ChunkedClient(boolean sendEndChunk) {
            this.sendEndChunk = sendEndChunk;
        }


        private Exception doRequest() {

            Tomcat tomcat = getTomcatInstance();

            Context root = tomcat.addContext("", TEMP_DIR);
            Wrapper w = Tomcat.addServlet(root, "Test", new NonBlockingEchoServlet());
            w.setAsyncSupported(true);
            root.addServletMappingDecoded("/test", "Test");

            try {
                tomcat.start();
                setPort(tomcat.getConnector().getLocalPort());

                // Open connection
                connect();

                int packetCount = 2;
                if (sendEndChunk) {
                    packetCount++;
                }

                String[] request = new String[packetCount];
                request[0] =
                    "POST /test HTTP/1.1" + CRLF +
                    "Host: localhost:8080" + CRLF +
                    "Transfer-Encoding: chunked" + CRLF +
                    "Connection: close" + CRLF +
                    CRLF;
                request[1] =
                        "b8" + CRLF +
                        "{" + CRLF +
                        "  \"tenantId\": \"dotCom\", "  + CRLF +
                        "  \"locale\": \"en-US\", "  + CRLF +
                        "  \"defaultZoneId\": \"25\", "  + CRLF +
                        "  \"itemIds\": [\"StaplesUSCAS/en-US/2/<EOF>/<EOF>\"] , "  + CRLF +
                        "  \"assetStoreId\": \"5051\", "  + CRLF +
                        "  \"zipCode\": \"98109\"" + CRLF +
                        "}" + CRLF;
                if (sendEndChunk) {
                    request[2] =
                            "0" + CRLF +
                            CRLF;
                }

                setRequest(request);
                processRequest(); // blocks until response has been read

                // Close the connection
                disconnect();
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        public boolean isResponseBodyOK() {
            if (getResponseBody() == null) {
                return false;
            }
            if (!getResponseBody().contains("98109")) {
                return false;
            }
            return true;
        }

    }


    private static class NonBlockingEchoServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            // Need to be in async mode to use non-blocking I/O
            AsyncContext ac = req.startAsync();
            ac.setTimeout(10000);

            ServletInputStream sis = null;
            ServletOutputStream sos = null;

            try {
                sis = req.getInputStream();
                sos = resp.getOutputStream();
            } catch (IOException ioe) {
                throw new ServletException(ioe);
            }

            EchoListener listener = new EchoListener(ac, sis, sos);
            sis.setReadListener(listener);
            sos.setWriteListener(listener);
        }
    }


    private static class EchoListener implements ReadListener, WriteListener {

        private static volatile Throwable t;

        private final AsyncContext ac;
        private final ServletInputStream sis;
        private final ServletOutputStream sos;
        private final byte[] buffer = new byte[8192];

        public EchoListener(AsyncContext ac, ServletInputStream sis, ServletOutputStream sos) {
            t = null;
            this.ac = ac;
            this.sis = sis;
            this.sos = sos;
        }

        @Override
        public void onWritePossible() throws IOException {
            if (sis.isFinished()) {
                sos.flush();
                ac.complete();
                return;
            }
            while (sis.isReady()) {
                int read = sis.read(buffer);
                if (read > 0) {
                    sos.write(buffer, 0, read);
                    if (!sos.isReady()) {
                        break;
                    }
                }
            }
        }

        @Override
        public void onDataAvailable() throws IOException {
            if (sos.isReady()) {
                onWritePossible();
            }
        }

        @Override
        public void onAllDataRead() throws IOException {
            if (sos.isReady()) {
                onWritePossible();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            t = throwable;
            ac.complete();
        }
    }
}

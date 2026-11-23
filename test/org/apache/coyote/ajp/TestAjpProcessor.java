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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

public class TestAjpProcessor extends AjpBaseTest {

    /*
     * When a request is rejected before the request body is read, the body packet that the proxy has already sent must
     * not be processed as the start of a new AJP request.
     */
    @Test
    public void testUnreadBodyPacketAfterRejectedRequest() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = getProgrammaticRootContext();

        RecordingServlet servlet = new RecordingServlet();
        Tomcat.addServlet(ctx, "recording", servlet);
        ctx.addServletMapping("/", "recording");

        tomcat.start();

        SimpleAjpClient ajpClient = new SimpleAjpClient();
        ajpClient.setPort(getPort());
        ajpClient.connect();

        /*
         * Build the body of the first body packet so that, if it is read as the next AJP message, it is a valid forward
         * request for /injected. The two byte chunk length (514 = 0x0202) is read as the message type (FORWARD_REQUEST)
         * and the method (GET) and the data that follows is read as the remainder of the forward request.
         */
        int bodyLength = 514;
        ajpClient.setMethod("GET");
        ajpClient.setUri("/injected");
        TesterAjpMessage injectedMessage = ajpClient.createForwardMessage();
        injectedMessage.end();
        byte[] injectedBuffer = injectedMessage.getBuffer();
        // Skip the 4 byte header, the message type and the method
        int injectedLength = injectedMessage.getLen() - 6;
        Assert.assertTrue(injectedLength <= bodyLength);
        byte[] body = new byte[bodyLength];
        System.arraycopy(injectedBuffer, 6, body, 0, injectedLength);

        // The request is rejected because of the invalid host name
        ajpClient.setMethod("POST");
        ajpClient.setUri("/rejected");
        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage();
        forwardMessage.addHeader("Host", "invalid_host");
        forwardMessage.addHeader("Content-Length", Integer.toString(bodyLength));
        forwardMessage.end();

        TesterAjpMessage responseHeaders = ajpClient.sendMessage(forwardMessage, ajpClient.createBodyMessage(body));
        TestAbstractAjpProcessor.validateResponseHeaders(responseHeaders, 400, "400");
        // Response body
        ajpClient.readMessage();
        // End of response. The connection is not to be re-used.
        TesterAjpMessage endMessage = ajpClient.readMessage();
        Assert.assertEquals(0x05, endMessage.getByte());
        Assert.assertEquals(0x00, endMessage.getByte());

        // The connection should now be closed without any further messages
        try {
            ajpClient.readMessage();
            Assert.fail("Unexpected message received. The connection should have been closed");
        } catch (IOException expected) {
            // Expected
        }

        Assert.assertFalse("The unread body packet was processed as a request: " + servlet.getUris(),
                servlet.getUris().contains("/injected"));

        ajpClient.disconnect();
    }


    static final class RecordingServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final List<String> uris = new CopyOnWriteArrayList<>();

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            uris.add(req.getRequestURI());
            resp.setContentType("text/plain");
            resp.getWriter().print("OK");
        }

        List<String> getUris() {
            return uris;
        }
    }

}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestAbstractAjpProcessor extends TomcatBaseTest {

    @Override
    protected String getProtocol() {
        /*
         * The tests are all setup for HTTP so need to convert the protocol
         * values to AJP.
         */
        // Has a protocol been specified
        String protocol = System.getProperty("tomcat.test.protocol");

        // Use BIO by default
        if (protocol == null) {
            protocol = "org.apache.coyote.ajp.AjpProtocol";
        } else if (protocol.contains("Nio")) {
            protocol = "org.apache.coyote.ajp.AjpNioProtocol";
        } else if (protocol.contains("Apr")) {
            protocol = "org.apache.coyote.ajp.AjpAprProtocol";
        } else {
            protocol = "org.apache.coyote.ajp.AjpProtocol";
        }

        return protocol;
    }

    @Test
    public void testKeepAlive() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setProperty("connectionTimeout", "-1");
        tomcat.start();

        // Must have a real docBase - just use temp
        org.apache.catalina.Context ctx =
            tomcat.addContext("", System.getProperty("java.io.tmpdir"));
        Tomcat.addServlet(ctx, "helloWorld", new HelloWorldServlet());
        ctx.addServletMapping("/", "helloWorld");

        SimpleAjpClient ajpClient = new SimpleAjpClient();

        ajpClient.setPort(getPort());

        ajpClient.connect();

        validateCpong(ajpClient.cping());

        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage("/");

        // Two requests
        for (int i = 0; i < 2; i++) {
            TesterAjpMessage responseHeaders = ajpClient.sendMessage(forwardMessage);
            // Expect 3 packets: headers, body, end
            validateResponseHeaders(responseHeaders, 200);
            TesterAjpMessage responseBody = ajpClient.readMessage();
            validateResponseBody(responseBody, HelloWorldServlet.RESPONSE_TEXT);
            validateResponseEnd(ajpClient.readMessage(), true);

            // Give connections plenty of time to time out
            Thread.sleep(2000);

            // Double check the connection is still open
            validateCpong(ajpClient.cping());
        }

        ajpClient.disconnect();
    }

    /**
     * Process response header packet and checks the status. Any other data is
     * ignored.
     */
    private void validateResponseHeaders(TesterAjpMessage message,
            int expectedStatus) throws Exception {
        // First two bytes should always be AB
        assertEquals((byte) 'A', message.buf[0]);
        assertEquals((byte) 'B', message.buf[1]);

        // Set the start position and read the length
        message.processHeader(false);

        // Check the length
        assertTrue(message.len > 0);

        // Should be a header message
        assertEquals(0x04, message.readByte());

        // Check status
        assertEquals(expectedStatus, message.readInt());

        // Read the status message
        message.readString();

        // Get the number of headers
        int headerCount = message.readInt();

        for (int i = 0; i < headerCount; i++) {
            // Read the header name
            message.readHeaderName();
            // Read the header value
            message.readString();
        }
    }

    /**
     * Validates that the response message is valid and contains the expected
     * content.
     */
    private void validateResponseBody(TesterAjpMessage message,
            String expectedBody) throws Exception {
        assertEquals((byte) 'A', message.buf[0]);
        assertEquals((byte) 'B', message.buf[1]);

        // Set the start position and read the length
        message.processHeader(false);

        // Should be a body chunk message
        assertEquals(0x03, message.readByte());

        int len = message.readInt();
        assertTrue(len > 0);
        String body = message.readString(len);

        assertEquals(expectedBody, body);
    }

    private void validateResponseEnd(TesterAjpMessage message,
            boolean expectedReuse) {
        assertEquals((byte) 'A', message.buf[0]);
        assertEquals((byte) 'B', message.buf[1]);

        message.processHeader(false);

        // Should be an end body message
        assertEquals(0x05, message.readByte());

        // Check the length
        assertEquals(2, message.getLen());

        boolean reuse = false;
        if (message.readByte() > 0) {
            reuse = true;
        }

        assertEquals(Boolean.valueOf(expectedReuse), Boolean.valueOf(reuse));
    }

    private void validateCpong(TesterAjpMessage message) throws Exception {
        // First two bytes should always be AB
        assertEquals((byte) 'A', message.buf[0]);
        assertEquals((byte) 'B', message.buf[1]);
        // CPONG should have a message length of 1
        // This effectively checks the next two bytes
        assertEquals(1, message.getLen());
        // Data should be the value 9
        assertEquals(9, message.buf[4]);
    }
}

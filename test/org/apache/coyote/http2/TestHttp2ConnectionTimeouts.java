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
package org.apache.coyote.http2;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

import org.junit.Assert;
import org.junit.Test;

public class TestHttp2ConnectionTimeouts extends Http2TestBase {

    @Test
    public void testConnectionTimeout() throws Exception {

        // Reduce default timeouts so test completes sooner
        enableHttp2(200, false, 5000, 5000, 10000, 5000, 5000);
        configureAndStartWebApplication();
        openClientConnection(false);
        doHttpUpgrade();
        sendClientPreface();
        validateHttp2InitialResponse();

        // Wait for timeout - should receive GoAway frame
        handleGoAwayResponse(1, Http2Error.NO_ERROR);
    }


    /*
     * The client has sent the HTTP upgrade request but then sends nothing. The timeouts must apply while the server is
     * waiting for the connection preface (regardless of whether the preface is read synchronously or asynchronously).
     */
    @Test
    public void testNoPrefaceTimeout() throws Exception {
        startAndUpgrade(2000, 25000);

        assertServerClosesConnection();
    }


    @Test
    public void testPartialPrefaceTimeout() throws Exception {
        startAndUpgrade(2000, 25000);

        // Start of the connection preface but not all of it
        os.write(Http2Parser.CLIENT_PREFACE_START, 0, 10);
        os.flush();

        assertServerClosesConnection();
    }


    /*
     * The client sends the connection preface string but not the SETTINGS frame that must follow it.
     */
    @Test
    public void testPrefaceWithoutSettingsTimeout() throws Exception {
        startAndUpgrade(2000, 25000);

        os.write(Http2Parser.CLIENT_PREFACE_START);
        os.flush();

        assertServerClosesConnection();
    }


    /*
     * A client that takes some time to send the preface, but sends it before the timeout, must not be affected.
     */
    @Test
    public void testSlowPrefaceWithinTimeout() throws Exception {
        startAndUpgrade(5000, 25000);

        Thread.sleep(1000);

        sendClientPreface();
        validateHttp2InitialResponse();
    }


    /*
     * The timeouts used while waiting for the connection preface must not remain in place once it has been received. An
     * idle connection must be left to the connection and stream timeouts (the keep-alive timeout in this case).
     */
    @Test
    public void testPrefaceTimeoutNotAppliedAfterPreface() throws Exception {
        startAndUpgrade(2000, 25000);

        sendClientPreface();
        validateHttp2InitialResponse();

        // Idle for longer than the read timeout
        Thread.sleep(4000);

        // The connection must still be open
        sendPing();
        parser.readFrame();
        Assert.assertEquals("0-Ping-Ack-[0,0,0,0,0,0,0,0]\n", output.getTrace());
    }


    /*
     * A direct HTTP/2 connection (no HTTP upgrade). The client sends the start of the connection preface (which is
     * required to identify the connection as HTTP/2) but not the SETTINGS frame that must follow it.
     */
    @Test
    public void testDirectConnectionNoSettingsTimeout() throws Exception {
        enableHttp2(200, false, 2000, 2000, 25000, 5000, 5000);
        configureAndStartWebApplication();
        openClientConnection(false);
        // Long enough that, if the server does not close the connection, the test fails rather than hangs
        s.setSoTimeout(20000);

        os.write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        os.flush();

        assertServerClosesConnection();
    }


    /*
     * A connection that used ALPN to negotiate HTTP/2. The client completes the TLS handshake and then sends nothing.
     */
    @Test
    public void testAlpnNoPrefaceTimeout() throws Exception {
        enableHttp2(200, true, 2000, 2000, 25000, 5000, 5000);
        configureAndStartWebApplication();
        openClientConnection(true);
        s.setSoTimeout(20000);

        SSLSocket sslSocket = (SSLSocket) s;
        SSLParameters parameters = sslSocket.getSSLParameters();
        parameters.setApplicationProtocols(new String[] { "h2" });
        sslSocket.setSSLParameters(parameters);
        sslSocket.startHandshake();
        Assert.assertEquals("h2", sslSocket.getApplicationProtocol());

        assertServerClosesConnection();
    }


    private void startAndUpgrade(long readTimeout, long keepAliveTimeout) throws Exception {
        enableHttp2(200, false, readTimeout, readTimeout, keepAliveTimeout, 5000, 5000);
        configureAndStartWebApplication();
        openClientConnection(false);
        // Long enough that, if the server does not close the connection, the test fails rather than hangs
        s.setSoTimeout(20000);
        doHttpUpgrade();
    }


    /*
     * The server has not been sent a valid connection preface so it is expected to close the connection once the read
     * timeout expires. The client is not sending anything so the only way the connection can end is for the server to
     * end it.
     */
    private void assertServerClosesConnection() throws IOException {
        InputStream is = s.getInputStream();
        byte[] buffer = new byte[1024];
        try {
            while (is.read(buffer) != -1) {
                // Discard whatever the server sends (the SETTINGS frame, possibly a GOAWAY frame)
            }
        } catch (SocketTimeoutException e) {
            Assert.fail("The server did not close the connection although the client did not send a valid preface");
        } catch (IOException e) {
            // Connection reset. That is also the server closing the connection.
        }
    }
}

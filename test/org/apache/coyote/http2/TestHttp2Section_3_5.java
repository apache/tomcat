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
import java.net.SocketTimeoutException;

import org.junit.Assert;
import org.junit.Test;

public class TestHttp2Section_3_5 extends Http2TestBase {

    @Test
    public void testNoConnectionPreface() throws Exception {
        enableHttp2();
        configureAndStartWebApplication();
        openClientConnection();
        doHttpUpgrade();

        // Server settings
        parser.readFrame();
        Assert.assertEquals("0-Settings-[3]-[200]\n" + "0-Settings-End\n", output.getTrace());
        output.clearTrace();

        // Should send client preface here. This will trigger an error.
        // Send two pings (2*(9+8)=34 bytes) as server looks for entire preface
        // of 24 bytes.
        sendPing();
        // Depending on timing, this ping may fail after the header has been
        // sent but before the ping body since:
        // 9 (ping 1 header) + 8 (ping 1 body) + 9 (ping 2 header) = 26 which
        // which is enough data for the server to determine that the preface is
        // invalid and close the connection. A subsequent attempt to send ping 2
        // body will fail.
        try {
            sendPing();
        } catch (IOException ignore) {
            // Ignore
        }

        // If the client preface had been valid, this would be an
        // acknowledgement. Of the settings. As the preface was invalid, it
        // should be a GOAWAY frame.
        try {
            parser.readFrame();
            Assert.assertTrue(output.getTrace(), output.getTrace().startsWith("0-Goaway-[1]-[1]-["));
        } catch (IOException ioe) {
            // Ignore
            // This is expected on some platforms and depends on timing. Once
            // the server closes the connection the client may see an exception
            // when trying to read the GOAWAY frame.
        }
    }

    @Test
    public void testFirstFrameAfterPrefaceNotSettings() throws Exception {
        // RFC 9113, section 3.4: the client connection preface must include a SETTINGS frame. An invalid connection
        // preface is a connection error of type PROTOCOL_ERROR.
        enableHttp2();
        configureAndStartWebApplication();
        openClientConnection();
        s.setSoTimeout(5000);

        // HTTP/2 with prior knowledge. The start of the preface is valid but the first frame is a PING.
        os.write(Http2Parser.CLIENT_PREFACE_START);
        os.flush();
        sendPing();

        boolean goaway = false;
        boolean closed = false;
        try {
            // Server settings, an optional ping and then the error. Read further than that so a connection that
            // is still open is detected.
            for (int i = 0; i < 4 && !goaway; i++) {
                parser.readFrame();
                goaway = output.getTrace().contains("-Goaway-");
            }
        } catch (SocketTimeoutException ste) {
            Assert.fail("The connection is still open. " + output.getTrace());
        } catch (Http2Exception e) {
            Assert.fail("The server sent an invalid frame. " + output.getTrace() + " " + e.getMessage());
        } catch (IOException ioe) {
            // The server may close the connection without sending a GOAWAY frame
            closed = true;
        }

        Assert.assertTrue(output.getTrace(), goaway || closed);
        Assert.assertFalse(output.getTrace(), output.getTrace().contains("-RST-"));
        if (goaway) {
            Assert.assertTrue(output.getTrace(), output.getTrace().contains("-Goaway-[0]-[1]-["));
        }
    }
}

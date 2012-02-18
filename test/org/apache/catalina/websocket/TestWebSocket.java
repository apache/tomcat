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
package org.apache.catalina.websocket;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.C2BConverter;
import org.apache.tomcat.util.buf.CharChunk;

public class TestWebSocket extends TomcatBaseTest {

    private static final String CRLF = "\r\n";

    private OutputStream os;
    private InputStream is;
    boolean isContinuation = false;

    @Test
    public void testSimple() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        File appDir = new File(getBuildDirectory(), "webapps/examples");
        tomcat.addWebapp(null, "/examples", appDir.getAbsolutePath());

        tomcat.start();

        // Open the socket
        final String encoding = "ISO-8859-1";
        SocketAddress addr = new InetSocketAddress("localhost", getPort());
        Socket socket = new Socket();
        socket.setSoTimeout(10000);
        socket.connect(addr, 10000);
        os = socket.getOutputStream();
        Writer writer = new OutputStreamWriter(os, encoding);
        is = socket.getInputStream();
        Reader r = new InputStreamReader(is, encoding);
        BufferedReader reader = new BufferedReader(r);

        // Send the WebSocket handshake
        writer.write("GET /examples/websocket/echoStream HTTP/1.1" + CRLF);
        writer.write("Host: foo" + CRLF);
        writer.write("Upgrade: websocket" + CRLF);
        writer.write("Connection: upgrade" + CRLF);
        writer.write("Sec-WebSocket-Version: 13" + CRLF);
        writer.write("Sec-WebSocket-Key: TODO" + CRLF);
        writer.write(CRLF);
        writer.flush();

        // Make sure we got an upgrade response
        String responseLine = reader.readLine();
        assertTrue(responseLine.startsWith("HTTP/1.1 101"));

        // Swallow the headers
        String responseHeaderLine = reader.readLine();
        while (!responseHeaderLine.equals("")) {
            responseHeaderLine = reader.readLine();
        }

        // Now we can do WebSocket
        sendMessage("foo", false);
        sendMessage("foo", true);

        assertEquals("foofoo",readMessage());

        // Finished with the socket
        socket.close();
    }

    private void sendMessage(String message, boolean finalFragment)
            throws IOException{
        ByteChunk bc = new ByteChunk(8192);
        C2BConverter c2b = new C2BConverter(bc, "UTF-8");
        c2b.convert(message);
        c2b.flushBuffer();

        int len = bc.getLength();
        assertTrue(len < 126);


        byte first;
        if (isContinuation) {
            first = Constants.OPCODE_CONTINUATION;
        } else {
            first = Constants.OPCODE_TEXT;
        }
        if (finalFragment) {
            first = (byte) (0x80 | first);
        }
        os.write(first);

        os.write(0x80 | len);

        // Zero mask
        os.write(0);
        os.write(0);
        os.write(0);
        os.write(0);

        // Payload
        os.write(bc.getBytes(), bc.getStart(), len);

        os.flush();

        // Will the next frame be a continuation frame
        isContinuation = !finalFragment;
    }

    private String readMessage() throws IOException {
        ByteChunk bc = new ByteChunk(125);
        CharChunk cc = new CharChunk(125);

        // Skip first byte
        is.read();

        // Get payload length
        int len = is.read() & 0x7F;
        assertTrue(len < 126);

        // Read payload
        int read = 0;
        while (read < len) {
            read = read + is.read(bc.getBytes(), read, len - read);
        }

        bc.setEnd(len);

        B2CConverter b2c = new B2CConverter("UTF-8");
        b2c.convert(bc, cc, len);

        return cc.toString();
    }
}

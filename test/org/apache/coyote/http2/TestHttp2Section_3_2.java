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
import java.nio.charset.StandardCharsets;

import org.junit.Test;

/**
 * Unit tests for Section 3.2 of
 * <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 * <br>
 * The order of tests in this class is aligned with the order of the
 * requirements in the RFC.
 */
public class TestHttp2Section_3_2 extends TestHttp2Base {

    // TODO: Test initial requests with bodies of various sizes

    // TODO: Test response with HTTP/2 is not enabled

    // TODO: Test HTTP upgrade with h2 rather tan h2c

    @Test(timeout=10000)
    public void testConnectionNoPreface() throws IOException {
        doHttpUpgrade();

        // If we don't send the preface the server should kill the connection.
        try {
            // Make the parser read something.
            parser.readFrame(true);
        } catch (IOException ioe) {
            // Expected because the server is going to drop the connection.
        }
    }


    @Test(timeout=10000)
    public void testConnectionIncompletePrefaceStart() throws IOException {
        doHttpUpgrade();

        // If we send an incomplete preface the server should kill the
        // connection.
        os.write("PRI * HTTP/2.0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        os.flush();
        try {
            // Make the parser read something.
            parser.readFrame(true);
        } catch (IOException ioe) {
            // Expected because the server is going to drop the connection.
        }
    }


    @Test(timeout=10000)
    public void testConnectionInvalidPrefaceStart() throws IOException {
        doHttpUpgrade();

        // If we send an incomplete preface the server should kill the
        // connection.
        os.write("xxxxxxxxx-xxxxxxxxx-xxxxxxxxx-xxxxxxxxxx".getBytes(
                StandardCharsets.ISO_8859_1));
        os.flush();
        try {
            // Make the parser read something.
            parser.readFrame(true);
        } catch (IOException ioe) {
            // Expected because the server is going to drop the connection.
        }
    }


    // TODO: Test if server sends settings frame

    // TODO: Test if client doesn't send SETTINGS as part of the preface

    // TODO: Test response is received on stream 1
}

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
package org.apache.tomcat.lite.http;

import java.io.IOException;

import junit.framework.TestCase;

import org.apache.tomcat.lite.TestMain;
import org.apache.tomcat.lite.io.BBuffer;

public class LiveHttp1Test extends TestCase {
    // Proxy tests extend this class, run same tests via proxy on 8903
    protected int clientPort = 8802;

    HttpRequest httpReq;

    BBuffer bodyRecvBuffer = BBuffer.allocate(1024);

    int to = 1000000;

    public void setUp() throws IOException {
        // DefaultHttpConnector.get().setDebug(true);
        // DefaultHttpConnector.get().setDebugHttp(true);
        TestMain.getTestServer();

        httpReq = HttpClient.newClient().request("localhost",
                clientPort);

        bodyRecvBuffer.recycle();
    }

    public void tearDown() throws Exception {
        if (httpReq != null) {
            httpReq.release(); // async
            httpReq = null;
        }
    }

    public void testSimpleRequest() throws Exception {
        httpReq.requestURI().set("/hello");

        httpReq.send();
        httpReq.readAll(bodyRecvBuffer, to);
        assertEquals("Hello world", bodyRecvBuffer.toString());
    }

    public void testSimpleRequestClose() throws Exception {
        httpReq.requestURI().set("/hello");
        httpReq.setHeader("Connection", "close");

        httpReq.send();
        httpReq.readAll(bodyRecvBuffer, to);
        assertEquals("Hello world", bodyRecvBuffer.toString());
    }

    public void testPoolGetRelease() throws Exception {
        HttpConnector con = HttpClient.newClient();
        con.setMaxHttpPoolSize(10);
        HttpChannel httpCh = con.get("localhost", clientPort);
        httpCh.release();

        httpCh = con.get("localhost", clientPort);
        httpCh.release();

        httpCh = con.get("localhost", clientPort);
        httpCh.release();

    }

    public void testSimpleChunkedRequest() throws Exception {
        httpReq.requestURI().set("/chunked/foo");
        httpReq.send();
        httpReq.readAll(bodyRecvBuffer, to);
        assertTrue(bodyRecvBuffer.toString(), bodyRecvBuffer.toString().indexOf("AAA") >= 0);
    }

    // Check waitResponseHead()
    public void testRequestHead() throws Exception {
        httpReq.requestURI().set("/echo/foo");

        // Send the request, wait response
        httpReq.send();

        httpReq.readAll(bodyRecvBuffer, to);
        assertTrue(bodyRecvBuffer.toString().indexOf("GET /echo/foo") > 0);
    }

    public void test10() throws Exception {
        for (int i = 0; i < 10; i++) {
            testSimpleRequest();
            tearDown();
            setUp();

            notFound();
            tearDown();
            setUp();

            testSimpleRequest();
            tearDown();
            setUp();
        }
    }

    public void notFound() throws Exception {
        httpReq.requestURI().set("/foo");
        httpReq.send();
        httpReq.readAll(bodyRecvBuffer, to);
    }

    // compression not implemented
    public void testGzipRequest() throws Exception {
        httpReq.requestURI().set("/hello");
        httpReq.setHeader("accept-encoding",
            "gzip");

        // Send the request, wait response
        httpReq.send();
        // cstate.waitResponseHead(10000); // headers are received
        // ByteChunk data = new ByteChunk(1024);
        // acstate.serializeResponse(acstate.res, data);

        // System.err.println(bodyRecvBuffer.toString());

        httpReq.readAll(bodyRecvBuffer, to);
        // Done
    }

    public void testWrongPort() throws Exception {
        httpReq = HttpClient.newClient().request("localhost", 18904);
        httpReq.requestURI().set("/hello");

        httpReq.send();

        httpReq.readAll(bodyRecvBuffer, to);
        assertEquals(0, bodyRecvBuffer.remaining());
    }
}

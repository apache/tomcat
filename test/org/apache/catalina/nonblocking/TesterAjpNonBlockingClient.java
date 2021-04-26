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
package org.apache.catalina.nonblocking;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.SocketFactory;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.nonblocking.TestNonBlockingAPI.DataWriter;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * This is not a standard set of unit tests. This is a set of test clients for
 * AJP support of Servlet 3.1 non-blocking IO. It assumes that there is an httpd
 * instance listening on localhost:80 that is redirecting all traffic to a
 * default Tomcat instance of version 8 or above that includes the examples
 * web application.
 */
public class TesterAjpNonBlockingClient extends TomcatBaseTest {

    @Test
    public void doTestAJPNonBlockingRead() throws Exception {

        Map<String, List<String>> resHeaders = new HashMap<>();
        ByteChunk out = new ByteChunk();
        int rc = postUrl(true, new DataWriter(2000, 5), "http://localhost" +
                "/examples/servlets/nonblocking/bytecounter",
                out, resHeaders, null);

        System.out.println(out.toString());

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }


    @Test
    public void testNonBlockingWrite() throws Exception {

        SocketFactory factory = SocketFactory.getDefault();
        Socket s = factory.createSocket("localhost", 80);

        ByteChunk result = new ByteChunk();
        OutputStream os = s.getOutputStream();
        os.write(("GET /examples/servlets/nonblocking/numberwriter HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        os.flush();

        InputStream is = s.getInputStream();
        byte[] buffer = new byte[8192];

        int read = 0;
        int readSinceLastPause = 0;
        while (read != -1) {
            read = is.read(buffer);
            if (read > 0) {
                result.append(buffer, 0, read);
            }
            readSinceLastPause += read;
            if (readSinceLastPause > 40000) {
                readSinceLastPause = 0;
                Thread.sleep(500);
            }
        }

        os.close();
        is.close();
        s.close();

        // Validate the result
        String resultString = result.toString();
        log.info("Client read " + resultString.length() + " bytes");

        System.out.println(resultString);

        Assert.assertTrue(resultString.contains("00000000000000010000"));
    }
}

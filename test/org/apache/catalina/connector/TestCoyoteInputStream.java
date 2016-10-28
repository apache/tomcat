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
package org.apache.catalina.connector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestCoyoteInputStream extends TomcatBaseTest {

    @Test
    public void testReadWithByteBuffer() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        Context root = tomcat.addContext("", TEMP_DIR);
        Tomcat.addServlet(root, "testServlet", new TestServlet());
        root.addServletMappingDecoded("/", "testServlet");

        tomcat.start();

        ByteChunk bc = new ByteChunk();
        String requestBody = "HelloWorld";
        int rc = postUrl(requestBody.getBytes(StandardCharsets.UTF_8),
                "http://localhost:" + getPort() + "/", bc, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
        Assert.assertTrue(requestBody.equals(bc.toString()));
    }

    private static final class TestServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            CoyoteInputStream is = (CoyoteInputStream) req.getInputStream();
            ByteBuffer buffer = ByteBuffer.allocate(256);
            is.read(buffer);
            CoyoteOutputStream os = (CoyoteOutputStream) resp.getOutputStream();
            os.write(buffer);
        }

    }

}

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.nonblocking.TestNonBlockingAPI.DataWriter;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TesterAjpNonBlockingClient extends TomcatBaseTest {

    /**
     * This is not a standard unit test. This is a test client for AJP
     * non-blocking reads. It assumes that there is an httpd instance listening
     * on localhost:80 that is redirecting all traffic to a default Tomcat 8
     * instance that includes the examples web application.
     */
    @Test
    public void doTestAJPNonBlockingRead() throws Exception {

        Map<String, List<String>> resHeaders = new HashMap<>();
        ByteChunk out = new ByteChunk();
        int rc = postUrl(true, new DataWriter(2000), "http://localhost" +
                "/examples/servlets/nonblocking/bytecounter",
                out, resHeaders, null);

        System.out.println(out.toString());

        Assert.assertEquals(HttpServletResponse.SC_OK, rc);
    }
}

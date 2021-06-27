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
package org.apache.jasper.compiler;

import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestJspUtil extends TomcatBaseTest {

    @Test
    public void testBoxedPrimitiveConstructors() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk bc = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/bug6nnnn/bug65377.jsp", bc, null);

        String body = bc.toString();
        String[] lines = body.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("<p>")) {
                line = line.substring(3, line.length() - 4);
                String[] parts = line.split(":");
                Assert.assertEquals(parts[0],  parts[1], parts[2]);
            }
        }
        Assert.assertEquals(bc.toString(), HttpServletResponse.SC_OK, rc);
    }
}

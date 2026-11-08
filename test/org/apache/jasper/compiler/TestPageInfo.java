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

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestPageInfo extends TomcatBaseTest {

    @Test
    public void testBufferNone() throws Exception {
        doTestBuffer("none", 200, null);
    }

    @Test
    public void testBufferValid() throws Exception {
        doTestBuffer("valid", 200, null);
    }

    @Test
    public void testBufferZero() throws Exception {
        // JSP.7.2.1 requires a positive integer
        doTestBuffer("zero", 500, "invalid value for buffer");
    }

    @Test
    public void testBufferNegative() throws Exception {
        // JSP.7.2.1 requires a positive integer
        doTestBuffer("negative", 500, "invalid value for buffer");
    }

    @Test
    public void testBufferOverflowMinimum() throws Exception {
        // The multiplication by 1024 overflows to Integer.MIN_VALUE
        doTestBuffer("min", 500, "invalid value for buffer");
    }

    @Test
    public void testBufferOverflowWrapAround() throws Exception {
        // The multiplication by 1024 overflows to zero
        doTestBuffer("overflow", 500, "invalid value for buffer");
    }

    private void doTestBuffer(String name, int expectedStatus, String expectedMessage) throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();
        int sc = getUrl("http://localhost:" + getPort() + "/test/jsp/pageinfo/buffer-" + name + ".jsp", res, null);

        Assert.assertEquals(expectedStatus, sc);

        String result = res.toString();
        if (expectedMessage == null) {
            Assert.assertTrue(result, result.contains("OK"));
        } else {
            // The page should fail translation with a clear message. Before
            // the range was validated, overflow and negative values either
            // passed translation silently or failed later at run time.
            Assert.assertTrue(result, result.contains(expectedMessage));
        }
    }
}

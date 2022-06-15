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

/**
 * Tests duplicate those in {@link TestParser} where the strict whitespace
 * parsing is enabled by default. Strict whitespace parsing is disabled for
 * these tests in web.xml.
 */
public class TestParserNoStrictWhitespace extends TomcatBaseTest {

    @Test
    public void testBug49297NoSpaceNotStrict() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = new ByteChunk();
        int sc = getUrl("http://localhost:" + getPort() + "/test/bug49nnn/bug49297NoSpace.jsp", res, null);

        Assert.assertEquals(200, sc);
        assertEcho(res.toString(), "Hello World");
    }


    /** Assertion for text printed by tags:echo */
    private static void assertEcho(String result, String expected) {
        Assert.assertTrue(result.indexOf("<p>" + expected + "</p>") > 0);
    }
}

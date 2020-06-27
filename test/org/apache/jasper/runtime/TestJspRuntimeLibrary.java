/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.jasper.runtime;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestJspRuntimeLibrary extends TomcatBaseTest {

    /*
     * Tests successful conversions
     */
    @Test
    public void testBug63359a() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk res = getUrl("http://localhost:" + getPort() + "/test/bug6nnnn/bug63359a.jsp");
        String result = res.toString();

        assertEcho(result, "01-false");
        assertEcho(result, "02-false");
        assertEcho(result, "03-true");
        assertEcho(result, "04-true");
        assertEcho(result, "05-false");

        assertEcho(result, "11-false");
        assertEcho(result, "12-false");
        assertEcho(result, "13-true");
        assertEcho(result, "14-true");
        assertEcho(result, "15-false");

        assertEcho(result, "21-0");
        assertEcho(result, "22-42");
        assertEcho(result, "23--42");
        assertEcho(result, "24-42");

        assertEcho(result, "31-0");
        assertEcho(result, "32-42");
        assertEcho(result, "33--42");
        assertEcho(result, "34-42");

        assertEcho(result, "41-\u0000");
        assertEcho(result, "42-f");
        assertEcho(result, "43-b");
        assertEcho(result, "44-" + System.lineSeparator().charAt(0));

        assertEcho(result, "51-\u0000");
        assertEcho(result, "52-f");
        assertEcho(result, "53-b");
        assertEcho(result, "54-" + System.lineSeparator().charAt(0));

        assertEcho(result, "61-0.0");
        assertEcho(result, "62-42.0");
        assertEcho(result, "63--42.0");
        assertEcho(result, "64-42.0");

        assertEcho(result, "71-0.0");
        assertEcho(result, "72-42.0");
        assertEcho(result, "73--42.0");
        assertEcho(result, "74-42.0");

        assertEcho(result, "81-0");
        assertEcho(result, "82-42");
        assertEcho(result, "83--42");
        assertEcho(result, "84-42");

        assertEcho(result, "91-0");
        assertEcho(result, "92-42");
        assertEcho(result, "93--42");
        assertEcho(result, "94-42");

        assertEcho(result, "101-0.0");
        assertEcho(result, "102-42.0");
        assertEcho(result, "103--42.0");
        assertEcho(result, "104-42.0");

        assertEcho(result, "111-0.0");
        assertEcho(result, "112-42.0");
        assertEcho(result, "113--42.0");
        assertEcho(result, "114-42.0");

        assertEcho(result, "121-0");
        assertEcho(result, "122-42");
        assertEcho(result, "123--42");
        assertEcho(result, "124-42");

        assertEcho(result, "131-0");
        assertEcho(result, "132-42");
        assertEcho(result, "133--42");
        assertEcho(result, "134-42");

        assertEcho(result, "141-0");
        assertEcho(result, "142-42");
        assertEcho(result, "143--42");
        assertEcho(result, "144-42");

        assertEcho(result, "151-0");
        assertEcho(result, "152-42");
        assertEcho(result, "153--42");
        assertEcho(result, "154-42");

        assertEcho(result, "161-");
        assertEcho(result, "162-42");
        assertEcho(result, "163--42");
        assertEcho(result, "164-+42");

        assertEcho(result, "171-");
        assertEcho(result, "172-42");
        assertEcho(result, "173--42");
        assertEcho(result, "174-+42");

        assertEcho(result, "181-");
        assertEcho(result, "182-42");
        assertEcho(result, "183--42");
        assertEcho(result, "184-42");

        // NB In EL null coerces to the empty String
        assertEcho(result, "191-");
    }


    // Assertion for text contained with <p></p>, e.g. printed by tags:echo
    private static void assertEcho(String result, String expected) {
        Assert.assertTrue(result, result.indexOf("<p>" + expected + "</p>") > 0);
    }
}

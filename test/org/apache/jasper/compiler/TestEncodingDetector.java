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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

@RunWith(Parameterized.class)
public class TestEncodingDetector extends TomcatBaseTest {

    @Parameters
    public static Collection<Object[]> inputs() {
        /// Note: These files are saved using the encoding indicated by the BOM
        List<Object[]> result = new ArrayList<>();
        result.add(new Object[] { "bom-none-prolog-none.jsp",        Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-none-prolog-none.jspx",       Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-none-prolog-utf16be.jspx",    Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-none-prolog-utf16le.jspx",    Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-none-prolog-utf8.jspx",       Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-utf8-prolog-none.jsp",        Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-utf8-prolog-none.jspx",       Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-utf8-prolog-utf16be.jspx",    Integer.valueOf(500), null });
        result.add(new Object[] { "bom-utf8-prolog-utf16le.jspx",    Integer.valueOf(500), null });
        result.add(new Object[] { "bom-utf8-prolog-utf8.jspx",       Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-utf16be-prolog-none.jsp",     Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-utf16be-prolog-none.jspx",    Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-utf16be-prolog-utf16be.jspx", Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-utf16be-prolog-utf16le.jspx", Integer.valueOf(500), null });
        result.add(new Object[] { "bom-utf16be-prolog-utf8.jspx",    Integer.valueOf(500), null });
        result.add(new Object[] { "bom-utf16le-prolog-none.jsp",     Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-utf16le-prolog-none.jspx",    Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-utf16le-prolog-utf16be.jspx", Integer.valueOf(500), null });
        result.add(new Object[] { "bom-utf16le-prolog-utf16le.jspx", Integer.valueOf(200), Boolean.TRUE });
        result.add(new Object[] { "bom-utf16le-prolog-utf8.jspx",    Integer.valueOf(500), null });
        result.add(new Object[] { "bug60769a.jspx",    Integer.valueOf(500), null });
        result.add(new Object[] { "bug60769b.jspx",    Integer.valueOf(200), Boolean.TRUE });
        return result;
    }


    @Parameter(0)
    public String jspName;

    @Parameter(1)
    public int expectedResponseCode;

    @Parameter(2)
    public Boolean responseBodyOK;

    @Test
    public void testEncodedJsp() throws Exception {
        getTomcatInstanceTestWebapp(false, true);

        ByteChunk responseBody = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() + "/test/jsp/encoding/" + jspName,
                responseBody, null);

        Assert.assertEquals(expectedResponseCode, rc);

        if (expectedResponseCode == 200) {
            // trim() to remove whitespace like new lines
            String bodyText = responseBody.toString().trim();
            if (responseBodyOK.booleanValue()) {
                Assert.assertEquals("OK", bodyText);
            } else {
                Assert.assertNotEquals("OK", bodyText);
            }
        }
    }
}

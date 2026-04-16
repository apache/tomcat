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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.tomcat.util.http.Method;
import org.apache.tomcat.util.http.parser.HttpParser;


/**
 * Unit tests for Section 8.2 of <a href="https://tools.ietf.org/html/rfc7540">RFC 7540</a>.
 */
@RunWith(Parameterized.class)
public class TestHttp2Section_8_2 extends Http2TestBase {

    @Parameterized.Parameters(name = "{index}: {0} {1} {2} {3} {4}")
    public static Collection<Object[]> parameters() {
        Collection<Object[]> baseData = data();

        List<Object[]> parameterSets = new ArrayList<>();
        for (Object[] base : baseData) {
            parameterSets.add(new Object[] { base[0], base[1], "x-test", "x-value", Boolean.TRUE });
            // Strings longer than 5 characters will be huffman encoded
            for (char c = 0; c < 256; c++) {
                // HTTP/2 field names must be tokens and must be lower case
                boolean valid = HttpParser.isToken(c) && !Character.isUpperCase(c);
                // Non-Huffman field names
                parameterSets.add(new Object[] { base[0], base[1], "x-" + c + "t", "x-value", Boolean.valueOf(valid) });
                parameterSets.add(new Object[] { base[0], base[1], c + "x-t", "x-value", Boolean.valueOf(valid) });
                parameterSets.add(new Object[] { base[0], base[1], "x-t" + c, "x-value", Boolean.valueOf(valid) });
                // Huffman field names
                parameterSets
                        .add(new Object[] { base[0], base[1], "x-te" + c + "st", "x-value", Boolean.valueOf(valid) });
                parameterSets.add(new Object[] { base[0], base[1], c + "x-test", "x-value", Boolean.valueOf(valid) });
                parameterSets.add(new Object[] { base[0], base[1], "x-test" + c, "x-value", Boolean.valueOf(valid) });

                // HTTP/2 field values have same criteria as HTTP/1.1
                // Non-Huffman field values
                parameterSets.add(new Object[] { base[0], base[1], "x-test", "x-" + c + "v",
                        Boolean.valueOf(HttpParser.isFieldContent(c)) });
                parameterSets.add(new Object[] { base[0], base[1], "x-test", c + "x-v",
                        Boolean.valueOf(HttpParser.isFieldVChar(c)) });
                parameterSets.add(new Object[] { base[0], base[1], "x-test", "x-v" + c,
                        Boolean.valueOf(HttpParser.isFieldVChar(c)) });
                parameterSets.add(new Object[] { base[0], base[1], "x-test", "" + c,
                        Boolean.valueOf(HttpParser.isFieldVChar(c)) });
                // Huffman field values
                parameterSets.add(new Object[] { base[0], base[1], "x-test", "x-va" + c + "lue",
                        Boolean.valueOf(HttpParser.isFieldContent(c)) });
                parameterSets.add(new Object[] { base[0], base[1], "x-test", c + "x-value",
                        Boolean.valueOf(HttpParser.isFieldVChar(c)) });
                parameterSets.add(new Object[] { base[0], base[1], "x-test", "x-value" + c,
                        Boolean.valueOf(HttpParser.isFieldVChar(c)) });
            }
        }
        return parameterSets;
    }


    @Parameter(2)
    public String fieldName;

    @Parameter(3)
    public String fieldValue;

    @Parameter(4)
    public boolean valid;


    @Test
    public void testFieldNameAndValue() throws Exception {
        http2Connect();

        List<Header> headers = new ArrayList<>(4);
        headers.add(new Header(":method", Method.GET));
        headers.add(new Header(":scheme", "http"));
        headers.add(new Header(":path", "/simple"));
        headers.add(new Header(":authority", "localhost:" + getPort()));
        headers.add(new Header(fieldName, fieldValue));

        byte[] headersFrameHeader = new byte[9];
        ByteBuffer headersPayload = ByteBuffer.allocate(128);

        buildGetRequest(headersFrameHeader, headersPayload, null, headers, 3);

        writeFrame(headersFrameHeader, headersPayload);

        parser.readFrame();

        String trace = output.getTrace();
        if (valid) {
            Assert.assertTrue(trace, trace.contains("3-Header-[:status]-[200]"));
        } else {
            Assert.assertTrue(trace, trace.contains("3-RST-[1]"));
        }
    }
}

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
package org.apache.tomcat.util.http.fileupload;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * RFC 2046, section 5.1.1: a boundary delimiter may be followed by transport padding (white space) which a receiver
 * must be able to handle.
 */
public class TestMultipartStreamTransportPadding {

    private static final String PART_HEADERS = "Content-Disposition: form-data; name=\"a\"\r\n\r\n";


    @Test
    public void testNoPadding() throws Exception {
        List<String> bodies = parse("--ab\r\n" + PART_HEADERS + "v1\r\n--ab\r\n" + PART_HEADERS + "v2\r\n--ab--\r\n");
        Assert.assertEquals(List.of("v1", "v2"), bodies);
    }


    @Test
    public void testPaddingAfterFirstDelimiter() throws Exception {
        List<String> bodies = parse("--ab \t\r\n" + PART_HEADERS + "v1\r\n--ab--\r\n");
        Assert.assertEquals(List.of("v1"), bodies);
    }


    @Test
    public void testPaddingAfterEncapsulationDelimiter() throws Exception {
        List<String> bodies = parse("--ab\r\n" + PART_HEADERS + "v1\r\n--ab  \r\n" + PART_HEADERS + "v2\r\n--ab--\r\n");
        Assert.assertEquals(List.of("v1", "v2"), bodies);
    }


    @Test
    public void testPaddingAfterCloseDelimiter() throws Exception {
        List<String> bodies = parse("--ab\r\n" + PART_HEADERS + "v1\r\n--ab--  \r\n");
        Assert.assertEquals(List.of("v1"), bodies);
    }


    @Test(expected = MultipartStream.MalformedStreamException.class)
    public void testPaddingBeforeCloseMarker() throws Exception {
        // Padding is only permitted after the complete delimiter
        parse("--ab\r\n" + PART_HEADERS + "v1\r\n--ab  --\r\n");
    }


    private static List<String> parse(String body) throws Exception {
        MultipartStream stream =
                new MultipartStream(new ByteArrayInputStream(body.getBytes(StandardCharsets.ISO_8859_1)),
                        "ab".getBytes(StandardCharsets.ISO_8859_1), null);
        List<String> bodies = new ArrayList<>();
        boolean next = stream.skipPreamble();
        while (next) {
            stream.readHeaders();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            stream.readBodyData(out);
            bodies.add(out.toString(StandardCharsets.ISO_8859_1));
            next = stream.readBoundary();
        }
        return bodies;
    }
}

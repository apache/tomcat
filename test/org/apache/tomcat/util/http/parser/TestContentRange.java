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
package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

public class TestContentRange {

    @Test
    public void testValid() throws Exception {
        ContentRange r = parse("bytes 0-9/10");
        Assert.assertEquals("bytes", r.getUnits());
        Assert.assertEquals(0, r.getStart());
        Assert.assertEquals(9, r.getEnd());
        Assert.assertEquals(10, r.getLength());
    }


    @Test
    public void testUnknownLength() throws Exception {
        // RFC 9110, section 14.4: '*' means the complete length is unknown
        ContentRange r = parse("bytes 0-9/*");
        Assert.assertEquals("bytes", r.getUnits());
        Assert.assertEquals(0, r.getStart());
        Assert.assertEquals(9, r.getEnd());
        Assert.assertEquals(-1, r.getLength());
    }


    @Test
    public void testInvalid() throws Exception {
        Assert.assertNull(parse(""));
        Assert.assertNull(parse("bytes 0-9/"));
        Assert.assertNull(parse("bytes 0-9/**"));
        Assert.assertNull(parse("bytes 0-9/*x"));
        Assert.assertNull(parse("bytes 9-0/*"));
        Assert.assertNull(parse("bytes 0-9/9"));
        Assert.assertNull(parse("bytes */*"));
    }


    private ContentRange parse(String s) throws IOException {
        return ContentRange.parse(new StringReader(s));
    }
}

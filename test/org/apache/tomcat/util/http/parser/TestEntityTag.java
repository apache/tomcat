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

import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

public class TestEntityTag {

    @Test
    public void testMatch() throws Exception {
        Assert.assertEquals(Boolean.TRUE, compare("\"a\", W/\"b\"", true, "\"b\""));
        Assert.assertEquals(Boolean.FALSE, compare("\"a\", W/\"b\"", false, "\"b\""));
        Assert.assertEquals(Boolean.FALSE, compare("\"a\", \"b\"", false, "\"c\""));
    }


    @Test
    public void testEmptyListElements() throws Exception {
        // RFC 9110, section 5.6.1.2: empty list elements must be ignored
        String[] headers = new String[] { "\"a\",, \"b\"", "\"a\", , \"b\"", ",\"a\", \"b\"", "\"a\", \"b\",",
                "\"a\", \"b\" ,, , " };
        for (String header : headers) {
            Assert.assertEquals(header, Boolean.TRUE, compare(header, false, "\"b\""));
            Assert.assertEquals(header, Boolean.FALSE, compare(header, false, "\"c\""));
        }
    }


    @Test
    public void testNoEntityTags() throws Exception {
        // RFC 9110, section 5.6.1.2: a list that contains no elements is valid and nothing in it can match
        String[] headers = new String[] { "", " ", ",", " , ,, " };
        for (String header : headers) {
            Assert.assertEquals("[" + header + "]", Boolean.FALSE, compare(header, false, "\"a\""));
            Assert.assertEquals("[" + header + "]", Boolean.FALSE, compare(header, true, "W/\"a\""));
        }
    }


    @Test
    public void testInvalid() throws Exception {
        Assert.assertNull(compare("a", false, "\"a\""));
        Assert.assertNull(compare("\"a\" \"b\"", false, "\"a\""));
        Assert.assertNull(compare("\"a\", b", false, "\"a\""));
        Assert.assertNull(compare("\"a\",, b", false, "\"a\""));
        Assert.assertNull(compare("W/", false, "\"a\""));
    }


    private Boolean compare(String header, boolean compareWeak, String resourceETag) throws Exception {
        return EntityTag.compareEntityTag(new StringReader(header), compareWeak, resourceETag);
    }
}

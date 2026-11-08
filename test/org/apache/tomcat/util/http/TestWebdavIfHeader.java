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
package org.apache.tomcat.util.http;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestWebdavIfHeader {

    private static final String TOKEN = "urn:uuid:0123456789abcdef";
    private static final List<String> TOKENS = Collections.singletonList(TOKEN);

    private static final String URI_PREFIX = "http://localhost/webdav/";

    /*
     * RFC 4918 section 10.4.2 requires an IfList to contain at least one condition:
     *
     * List = "(" 1*Condition ")"
     *
     * An empty list must make the header value invalid (hasValue() returns false) so that strict mode processing
     * rejects it rather than matching every token and etag vacuously.
     */
    @Test
    public void testEmptyIfListIsInvalid() throws Exception {
        assertInvalid("()");
        assertInvalid("( )");
        assertInvalid("(Not)");
        assertInvalid("(Not )");
        assertInvalid("(junk)");
        assertInvalid("(<urn");
        // A single empty list invalidates the entire header value
        assertInvalid("(<" + TOKEN + ">) ()");
        assertInvalid("() (<" + TOKEN + ">)");
        assertInvalid("</myfolder/> ()");
    }

    @Test
    public void testAbsentHeaderHasNoValue() throws Exception {
        assertInvalid(null);
        assertInvalid("");
    }

    @Test
    public void testUntaggedMatches() throws Exception {
        WebdavIfHeader header = new WebdavIfHeader(URI_PREFIX, "(<" + TOKEN + ">)");
        Assert.assertTrue(header.hasValue());
        Assert.assertTrue(header.matches("/myfolder/", TOKENS, "etag"));
        Assert.assertFalse(header.matches("/myfolder/", Collections.singletonList("other"), "etag"));
        Assert.assertTrue(header.matches("/other/", TOKENS, "etag"));
    }

    @Test
    public void testEtagMatches() throws Exception {
        WebdavIfHeader header = new WebdavIfHeader(URI_PREFIX, "([etag])");
        Assert.assertTrue(header.hasValue());
        Assert.assertTrue(header.matches("/myfolder/", TOKENS, "etag"));
        Assert.assertFalse(header.matches("/myfolder/", TOKENS, "other"));
    }

    @Test
    public void testMultipleLists() throws Exception {
        WebdavIfHeader header = new WebdavIfHeader(URI_PREFIX, "(<" + TOKEN + ">) ([etag])");
        Assert.assertTrue(header.hasValue());
        // Untagged lists are alternatives: the first list matches the token
        Assert.assertTrue(header.matches("/myfolder/", TOKENS, "other"));
        // The second list matches the etag
        Assert.assertTrue(header.matches("/myfolder/", Collections.singletonList("other"), "etag"));
    }

    @Test
    public void testTaggedMatches() throws Exception {
        WebdavIfHeader header = new WebdavIfHeader(URI_PREFIX, "</myfolder/> (<" + TOKEN + ">)");
        Assert.assertTrue(header.hasValue());
        Assert.assertTrue(header.matches("/myfolder/", TOKENS, "etag"));
        Assert.assertFalse(header.matches("/myfolder/", Collections.singletonList("other"), "etag"));
        // A resource not listed in a tagged header does not match
        Assert.assertFalse(header.matches("/other/", TOKENS, "etag"));
    }

    @Test
    public void testNotMatches() throws Exception {
        WebdavIfHeader header = new WebdavIfHeader(URI_PREFIX, "(Not <" + TOKEN + ">)");
        Assert.assertTrue(header.hasValue());
        Assert.assertFalse(header.matches("/myfolder/", TOKENS, "etag"));
        Assert.assertTrue(header.matches("/myfolder/", Collections.singletonList("other"), "etag"));
    }

    /*
     * The parser is deliberately lenient with malformed entries that do not prevent the rest of the header from
     * parsing (error recovery inherited from the Jackrabbit code). Only a list without any valid entry invalidates
     * the header value.
     */
    @Test
    public void testErrorRecoveryKeepsValidEntries() throws Exception {
        WebdavIfHeader header = new WebdavIfHeader(URI_PREFIX, "(<" + TOKEN + "> junk)");
        Assert.assertTrue(header.hasValue());
        Assert.assertTrue(header.matches("/myfolder/", TOKENS, "etag"));

        header = new WebdavIfHeader(URI_PREFIX, "(<" + TOKEN + ">) junk ([etag])");
        Assert.assertTrue(header.hasValue());
        Assert.assertTrue(header.matches("/myfolder/", TOKENS, "other"));
        Assert.assertTrue(header.matches("/myfolder/", Collections.singletonList("other"), "etag"));

        header = new WebdavIfHeader(URI_PREFIX, "(<" + TOKEN + "> <incomplete");
        Assert.assertTrue(header.hasValue());
        Assert.assertTrue(header.matches("/myfolder/", TOKENS, "etag"));

        header = new WebdavIfHeader(URI_PREFIX, "(<" + TOKEN + ">)]");
        Assert.assertTrue(header.hasValue());
        Assert.assertTrue(header.matches("/myfolder/", TOKENS, "etag"));
    }

    private void assertInvalid(String headerValue) throws Exception {
        WebdavIfHeader header = new WebdavIfHeader(URI_PREFIX, headerValue);
        Assert.assertFalse("Expected invalid header for value: " + headerValue, header.hasValue());
    }
}

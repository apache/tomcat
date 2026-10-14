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
package org.apache.tomcat.util.buf;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.buf.UEncoder.SafeCharsSet;

/**
 * Test cases for {@link UEncoder}.
 */
public class TestUEncoder {

    @Test
    public void testEncodeURLWithSlashInit() throws IOException {
        UEncoder urlEncoder = new UEncoder(SafeCharsSet.WITH_SLASH);

        String s = "a+b/c/d+e.class";
        Assert.assertTrue(urlEncoder.encodeURL(s, 0, s.length()).equals(
                "a%2bb/c/d%2be.class"));
        Assert.assertTrue(urlEncoder.encodeURL(s, 2, s.length() - 2).equals(
                "b/c/d%2be.cla"));

        s = new String(new char[] { 0xD801, 0xDC01 });
        Assert.assertTrue(urlEncoder.encodeURL(s, 0, s.length())
                .equals("%f0%90%90%81"));
    }


    @Test
    public void testEncodeURLUnpairedSurrogateDoesNotLeak() throws IOException {
        UEncoder urlEncoder = new UEncoder(SafeCharsSet.DEFAULT);

        // A string ending with an unpaired high surrogate. The surrogate
        // cannot be encoded. It must not be retained by the encoder and
        // combined with the start of a later, unrelated string.
        String loneHigh = "ab" + '\uD800';
        urlEncoder.encodeURL(loneHigh, 0, loneHigh.length());

        String low = '\uDC00' + "cd";
        String result = urlEncoder.encodeURL(low, 0, low.length()).toString();

        // The result must match that of a fresh encoder, i.e. the unpaired
        // low surrogate must be replaced, not paired with the leftover high
        // surrogate from the previous call
        UEncoder fresh = new UEncoder(SafeCharsSet.DEFAULT);
        Assert.assertEquals(fresh.encodeURL(low, 0, low.length()).toString(),
                result);
    }
}

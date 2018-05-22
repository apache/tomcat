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
import org.junit.Ignore;
import org.junit.Test;

import org.apache.tomcat.util.buf.CharChunk.CharOutputChannel;

/**
 * Test cases for {@link CharChunk}.
 */
public class TestCharChunk {

    @Test
    public void testEndsWith() {
        CharChunk cc = new CharChunk();
        Assert.assertFalse(cc.endsWith("test"));
        cc.setChars("xxtestxx".toCharArray(), 2, 4);
        Assert.assertTrue(cc.endsWith(""));
        Assert.assertTrue(cc.endsWith("t"));
        Assert.assertTrue(cc.endsWith("st"));
        Assert.assertTrue(cc.endsWith("test"));
        Assert.assertFalse(cc.endsWith("x"));
        Assert.assertFalse(cc.endsWith("xxtest"));
    }


    @Test
    public void testIndexOf_String() {
        char[] chars = "Hello\u00a0world".toCharArray();
        final int len = chars.length;

        CharChunk cc = new CharChunk();
        cc.setChars(chars, 0, len);

        Assert.assertEquals(0, cc.indexOf("Hello", 0, "Hello".length(), 0));
        Assert.assertEquals(2, cc.indexOf("ll", 0, 2, 0));
        Assert.assertEquals(2, cc.indexOf("Hello", 2, 2, 0));

        Assert.assertEquals(7, cc.indexOf("o", 0, 1, 5));

        // Does work outside of 0-127 (unlike ByteChunk)
        Assert.assertEquals(5, cc.indexOf("\u00a0", 0, 1, 0));

        cc.setChars(chars, 6, 5);
        Assert.assertEquals(1, cc.indexOf("o", 0, 1, 0));

        cc.setChars(chars, 6, 2);
        Assert.assertEquals(0, cc.indexOf("wo", 0, 1, 0));
        Assert.assertEquals(-1, cc.indexOf("d", 0, 1, 0));
    }


    @Ignore // Requires an 11GB heap (on markt's desktop - YMMV)
    @Test
    public void testAppend() throws Exception {
        CharChunk cc = new CharChunk();
        cc.setCharOutputChannel(new Sink());
        // Defaults to no limit

        char data[] = new char[32 * 1024 * 1024];

        for (int i = 0; i < 100; i++) {
            cc.append(data, 0, data.length);
        }

        Assert.assertEquals(AbstractChunk.ARRAY_MAX_SIZE, cc.getBuffer().length);
    }


    public static class Sink implements CharOutputChannel {

        @Override
        public void realWriteChars(char[] cbuf, int off, int len) throws IOException {
            // NO-OP
        }
    }


    @Test
    public void testToString() {
        CharChunk cc = new CharChunk();
        Assert.assertNull(cc.toString());
        char[] data = new char[8];
        cc.setChars(data, 0, data.length);
        Assert.assertNotNull(cc.toString());
        cc.recycle();
        // toString() should behave consistently for new ByteChunk and
        // immediately after a call to recycle().
        Assert.assertNull(cc.toString());
    }

}

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
package org.apache.tomcat.util.buf;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

public class TestUDecoder {

    @Test
    public void testURLDecodeStringInvalid() {
        // %n rather than %nn should throw an IAE according to the Javadoc
        Exception exception = null;
        try {
            UDecoder.URLDecode("%5xxxxx", StandardCharsets.UTF_8);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertTrue(exception instanceof IllegalArgumentException);

        // Edge case trying to trigger ArrayIndexOutOfBoundsException
        exception = null;
        try {
            UDecoder.URLDecode("%5", StandardCharsets.UTF_8);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertTrue(exception instanceof IllegalArgumentException);
    }


    @Test
    public void testURLDecodeStringValidIso88591Start() {
        String result = UDecoder.URLDecode("%41xxxx", StandardCharsets.ISO_8859_1);
        Assert.assertEquals("Axxxx", result);
    }


    @Test
    public void testURLDecodeStringValidIso88591Middle() {
        String result = UDecoder.URLDecode("xx%41xx", StandardCharsets.ISO_8859_1);
        Assert.assertEquals("xxAxx", result);
    }


    @Test
    public void testURLDecodeStringValidIso88591End() {
        String result = UDecoder.URLDecode("xxxx%41", StandardCharsets.ISO_8859_1);
        Assert.assertEquals("xxxxA", result);
    }


    @Test
    public void testURLDecodeStringValidUtf8Start() {
        String result = UDecoder.URLDecode("%c3%aaxxxx", StandardCharsets.UTF_8);
        Assert.assertEquals("\u00eaxxxx", result);
    }


    @Test
    public void testURLDecodeStringValidUtf8Middle() {
        String result = UDecoder.URLDecode("xx%c3%aaxx", StandardCharsets.UTF_8);
        Assert.assertEquals("xx\u00eaxx", result);
    }


    @Test
    public void testURLDecodeStringValidUtf8End() {
        String result = UDecoder.URLDecode("xxxx%c3%aa", StandardCharsets.UTF_8);
        Assert.assertEquals("xxxx\u00ea", result);
    }


    @Test
    public void testURLDecodeStringNonAsciiValidNone() {
        String result = UDecoder.URLDecode("\u00eaxxxx", StandardCharsets.UTF_8);
        Assert.assertEquals("\u00eaxxxx", result);
    }


    @Test
    public void testURLDecodeStringNonAsciiValidUtf8() {
        String result = UDecoder.URLDecode("\u00ea%c3%aa", StandardCharsets.UTF_8);
        Assert.assertEquals("\u00ea\u00ea", result);
    }
}

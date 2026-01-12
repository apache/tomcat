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
package org.apache.catalina.valves;

import java.io.CharArrayWriter;

import org.junit.Assert;
import org.junit.Test;

public class TestExtendedAccessLogValveWrap {

    @Test
    public void alpha() {
        CharArrayWriter buf = new CharArrayWriter();
        ExtendedAccessLogValve.wrap("foo", buf);
        Assert.assertEquals("\"foo\"", buf.toString());
    }

    @Test
    public void testNull() {
        CharArrayWriter buf = new CharArrayWriter();
        ExtendedAccessLogValve.wrap(null, buf);
        Assert.assertEquals("-", buf.toString());
    }

    @Test
    public void empty() {
        CharArrayWriter buf = new CharArrayWriter();
        ExtendedAccessLogValve.wrap("", buf);
        Assert.assertEquals("\"\"", buf.toString());
    }

    @Test
    public void singleQuoteMiddle() {
        CharArrayWriter buf = new CharArrayWriter();
        ExtendedAccessLogValve.wrap("foo'bar", buf);
        Assert.assertEquals("\"foo'bar\"", buf.toString());
    }

    @Test
    public void doubleQuoteMiddle() {
        CharArrayWriter buf = new CharArrayWriter();
        ExtendedAccessLogValve.wrap("foo\"bar", buf);
        Assert.assertEquals("\"foo\"\"bar\"", buf.toString());
    }

    @Test
    public void doubleQuoteStart() {
        CharArrayWriter buf = new CharArrayWriter();
        ExtendedAccessLogValve.wrap("\"foobar", buf);
        Assert.assertEquals("\"\"\"foobar\"", buf.toString());
    }

    @Test
    public void doubleQuoteEnd() {
        CharArrayWriter buf = new CharArrayWriter();
        ExtendedAccessLogValve.wrap("foobar\"", buf);
        Assert.assertEquals("\"foobar\"\"\"", buf.toString());
    }

    @Test
    public void doubleQuote() {
        CharArrayWriter buf = new CharArrayWriter();
        ExtendedAccessLogValve.wrap("\"", buf);
        Assert.assertEquals("\"\"\"\"", buf.toString());
    }
}

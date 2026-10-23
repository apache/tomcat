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

package org.apache.catalina.util;

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Test;

public class TestStrftime {

    private static String format(String pattern) {
        Strftime strftime = new Strftime(pattern, Locale.ENGLISH);
        strftime.setTimeZone(TimeZone.getTimeZone("UTC"));
        return strftime.format(new Date(0));
    }

    @Test
    public void testPlainPattern() {
        Assert.assertEquals("00:00", format("%H:%M"));
    }

    /*
     * A single quote in the strftime format is an ordinary literal character. It used to close the generated
     * SimpleDateFormat quoted literal without the conversion tracking that, leaving the literal text that follows
     * unquoted. Parsing the pattern then threw IllegalArgumentException (an "Illegal pattern character" or
     * "Unterminated quote"), or the following characters were silently interpreted as pattern letters.
     */
    @Test
    public void testQuoteInLiteral() {
        Assert.assertEquals("1970'it's", format("%Y'it's"));
        Assert.assertEquals("yyyy'ab'cd", format("yyyy'ab'cd"));
        // Trailing quote: in a pattern not starting with a command, this used to be dropped from the output
        Assert.assertEquals("a'", format("a'"));
        Assert.assertEquals("1970'", format("%Y'"));
    }

    @Test
    public void testLoneQuote() {
        Assert.assertEquals("'", format("'"));
    }

    @Test
    public void testQuoteBetweenWords() {
        Assert.assertEquals("a' b", format("a' b"));
    }

    @Test
    public void testQuoteAsUnknownCommand() {
        Assert.assertEquals("%'", format("%'"));
        Assert.assertEquals("a%'b", format("a%'b"));
        Assert.assertEquals("%'", format("%E'"));
    }

    /*
     * A pattern of the kind SSI config timefmt values commonly use, with the literal word 'GMT' quoted.
     */
    @Test
    public void testQuotedWordLiteral() {
        Assert.assertEquals("01 Jan 1970 00:00:00 UTC 'GMT'", format("%d %b %Y %H:%M:%S %Z 'GMT'"));
    }
}

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
package javax.servlet.http;

import java.util.BitSet;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Basic tests for Cookie in default configuration.
 */
public class TestCookie {
    public static final BitSet CHAR;  // <any US-ASCII character (octets 0 - 127)>
    public static final BitSet CTL;   // <any US-ASCII control character (octets 0 - 31) and DEL (127)>
    public static final BitSet SEPARATORS;
    public static final BitSet TOKEN; // 1*<any CHAR except CTLs or separators>

    public static final BitSet NETSCAPE_NAME; // "any character except comma, semicolon and whitespace"

    static {
        CHAR = new BitSet(256);
        CHAR.set(0, 128);

        CTL = new BitSet(256);
        CTL.set(0, 32);
        CTL.set(127);

        SEPARATORS = new BitSet(256);
        for (char ch : "()<>@,;:\\\"/[]?={} \t".toCharArray()) {
            SEPARATORS.set(ch);
        }

        TOKEN = new BitSet(256);
        TOKEN.or(CHAR); // any CHAR
        TOKEN.andNot(CTL); // except CTLs
        TOKEN.andNot(SEPARATORS); // or separators

        NETSCAPE_NAME = new BitSet(256);
        NETSCAPE_NAME.or(CHAR);
        NETSCAPE_NAME.andNot(CTL);
        NETSCAPE_NAME.clear(';');
        NETSCAPE_NAME.clear(',');
        NETSCAPE_NAME.clear(' ');
    }

    @Test
    public void testDefaults() {
        Cookie cookie = new Cookie("foo", null);
        Assert.assertEquals("foo", cookie.getName());
        Assert.assertNull(cookie.getValue());
        Assert.assertEquals(0, cookie.getVersion());
        Assert.assertEquals(-1, cookie.getMaxAge());
    }

    @Test
    public void testInitialValue() {
        Cookie cookie = new Cookie("foo", "bar");
        Assert.assertEquals("foo", cookie.getName());
        Assert.assertEquals("bar", cookie.getValue());
        Assert.assertEquals(0, cookie.getVersion());
    }

    @Test
    public void actualCharactersAllowedInName() {
        checkCharInName(NETSCAPE_NAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void leadingDollar() {
        new Cookie("$Version", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tokenVersion() {
        new Cookie("Version", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void attributeVersion() {
        new Cookie("Comment", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void attributeDiscard() {
        new Cookie("Discard", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void attributeExpires() {
        new Cookie("Expires", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void attributeMaxAge() {
        new Cookie("Max-Age", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void attributeDomain() {
        new Cookie("Domain", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void attributePath() {
        new Cookie("Path", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void attributeSecure() {
        new Cookie("Secure", null);
    }

    @Ignore("HttpOnly is not checked for")
    @Test(expected = IllegalArgumentException.class)
    public void attributeHttpOnly() {
        new Cookie("HttpOnly", null);
    }

    public static void checkCharInName(BitSet allowed) {
        for (char ch = 0; ch < allowed.size(); ch++) {
            boolean expected = allowed.get(ch);
            String name = "X" + ch + "X";
            boolean actual;
            try {
                new Cookie(name, null);
                actual = true;
            } catch (IllegalArgumentException e) {
                actual = false;
            }
            String msg = String.format("Check for char %d in name", (int) ch);
            Assert.assertEquals(msg, expected, actual);
        }
    }
}

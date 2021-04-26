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
package jakarta.servlet.http;

import java.util.BitSet;

import org.junit.Assert;
import org.junit.Test;

/**
 * Basic tests for Cookie in default configuration.
 */
public class TestCookie {
    public static final BitSet CHAR;  // <any US-ASCII character (octets 0 - 127)>
    public static final BitSet CTL;   // <any US-ASCII control character (octets 0 - 31) and DEL (127)>
    public static final BitSet SEPARATORS;
    public static final BitSet TOKEN; // 1*<any CHAR except CTLs or separators>

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
    public void defaultImpliesNetscape() {
        // $Foo is allowed by Netscape but not by RFC2109
        Cookie cookie = new Cookie("$Foo", null);
        Assert.assertEquals("$Foo", cookie.getName());
    }

    @Test
    public void tokenVersion() {
        Cookie cookie = new Cookie("Version", null);
        Assert.assertEquals("Version", cookie.getName());
    }

    @Test
    public void attributeVersion() {
        Cookie cookie = new Cookie("Comment", null);
        Assert.assertEquals("Comment", cookie.getName());
    }

    @Test
    public void attributeDiscard() {
        Cookie cookie = new Cookie("Discard", null);
        Assert.assertEquals("Discard", cookie.getName());
    }

    @Test
    public void attributeExpires() {
        Cookie cookie = new Cookie("Expires", null);
        Assert.assertEquals("Expires", cookie.getName());
    }

    @Test
    public void attributeMaxAge() {
        Cookie cookie = new Cookie("Max-Age", null);
        Assert.assertEquals("Max-Age", cookie.getName());
    }

    @Test
    public void attributeDomain() {
        Cookie cookie = new Cookie("Domain", null);
        Assert.assertEquals("Domain", cookie.getName());
    }

    @Test
    public void attributePath() {
        Cookie cookie = new Cookie("Path", null);
        Assert.assertEquals("Path", cookie.getName());
    }

    @Test
    public void attributeSecure() {
        Cookie cookie = new Cookie("Secure", null);
        Assert.assertEquals("Secure", cookie.getName());
    }

    @Test
    public void attributeHttpOnly() {
        Cookie cookie = new Cookie("HttpOnly", null);
        Assert.assertEquals("HttpOnly", cookie.getName());
    }

    @Test
    public void strictNamingImpliesRFC2109() {
        // Needs to be something RFC6265 allows, but strict naming does not.
        @SuppressWarnings("unused")
        Cookie cookie = new Cookie("$Foo", null);
    }

    public static void checkCharInName(CookieNameValidator validator, BitSet allowed) {
        for (char ch = 0; ch < allowed.size(); ch++) {
            boolean expected = allowed.get(ch);
            String name = "X" + ch + "X";
            try {
                validator.validate(name);
                if (!expected) {
                    Assert.fail(String.format("Char %d should not be allowed", Integer.valueOf(ch)));
                }
            } catch (IllegalArgumentException e) {
                if (expected) {
                    Assert.fail(String.format("Char %d should be allowed", Integer.valueOf(ch)));
                }
            }
        }
    }
}

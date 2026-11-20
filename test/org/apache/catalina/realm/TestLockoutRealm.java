/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.realm;

import java.security.Principal;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.TesterMapRealm;
import org.apache.tomcat.unittest.TesterContext;
import org.apache.tomcat.util.IntrospectionUtils;

public class TestLockoutRealm {

    private static final String USER_NAME = "user";
    private static final String PASSWORD = "password";

    private LockOutRealm realm;


    @Before
    public void init() throws Exception {
        Context context = new TesterContext();
        TesterMapRealm tmr = new TesterMapRealm();
        tmr.setContainer(context);
        MessageDigestCredentialHandler ch = new MessageDigestCredentialHandler();
        tmr.setCredentialHandler(ch);
        tmr.addUser(USER_NAME, PASSWORD);
        tmr.start();

        realm = new LockOutRealm();
        realm.setContainer(context);
        realm.addRealm(tmr);
        realm.setFailureCount(2);
        realm.start();
    }


    @Test
    public void testLockoutAfterFailure() {
        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNotNull(p);

        p = realm.authenticate(USER_NAME, "wrong");
        p = realm.authenticate(USER_NAME, "wrong");
        // Should be locked now
        p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNull(p);
    }


    @Test
    public void testLockoutAfterFailureCaseSensitiveDefault() {
        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNotNull(p);

        p = realm.authenticate(USER_NAME, "wrong");
        p = realm.authenticate(USER_NAME.toUpperCase(Locale.ENGLISH), "wrong");
        // Should be locked now
        p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNull(p);
    }


    @Test
    public void testLockoutAfterFailureCaseSensitiveFalse() {
        realm.setCaseSensitive(false);

        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNotNull(p);

        p = realm.authenticate(USER_NAME, "wrong");
        p = realm.authenticate(USER_NAME.toUpperCase(Locale.ENGLISH), "wrong");
        // Should be locked now
        p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNull(p);
    }


    @Test
    public void testLockoutAfterFailureCaseSensitiveTrue() {
        realm.setCaseSensitive(true);

        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNotNull(p);

        p = realm.authenticate(USER_NAME, "wrong");
        p = realm.authenticate(USER_NAME.toUpperCase(Locale.ENGLISH), "wrong");
        // Should not be locked yet
        p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNotNull(p);

        p = realm.authenticate(USER_NAME, "wrong");
        p = realm.authenticate(USER_NAME, "wrong");

        // Both should be locked now
        p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNull(p);
    }


    @Test
    public void testDefaults() {
        Assert.assertTrue(realm.getNormalizeUnicode());
        Assert.assertTrue(realm.getStripCombiningMarks());
        Assert.assertTrue(realm.getCollapseInternalSpace());
    }


    @Test
    public void testCharactersToIgnoreDefault() {
        // The value is returned in escaped form
        String defaults = realm.getCharactersToIgnore();

        // Characters that are invisible and ignored by servers
        Assert.assertTrue(defaults.contains("\\u00AD"));
        Assert.assertTrue(defaults.contains("\\u200B"));
        Assert.assertTrue(defaults.contains("\\uFEFF"));
        Assert.assertTrue(defaults.contains("\\uFE0F"));
        Assert.assertTrue(defaults.contains("\\u0001"));
        Assert.assertTrue(defaults.contains("\\u{E0001}"));
        // Characters that are significant or are space
        Assert.assertFalse(defaults.contains("\\u002D"));
        Assert.assertFalse(defaults.contains("\\u0020"));
        Assert.assertFalse(defaults.contains("\\u0009"));
        Assert.assertFalse(defaults.contains("\\u0085"));
        Assert.assertFalse(defaults.contains("\\u00A0"));
        Assert.assertEquals(-1, defaults.indexOf('-'));
        // Nothing is added by default
        Assert.assertEquals("", realm.getAdditionalCharactersToIgnore());

        // A zero width space does not make this a different user name by default
        realm.authenticate(USER_NAME, "wrong");
        realm.authenticate(USER_NAME + "\u200B", "wrong");
        // Should be locked now
        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNull(p);
    }


    @Test
    public void testCharactersToIgnoreDefaultRoundTrip() {
        String defaults = realm.getCharactersToIgnore();
        realm.setCharactersToIgnore(defaults);
        Assert.assertEquals(defaults, realm.getCharactersToIgnore());

        // Still ignored
        realm.authenticate(USER_NAME, "wrong");
        realm.authenticate(USER_NAME + "\u200B", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testCharactersToIgnoreEmpty() {
        realm.setCharactersToIgnore("");
        Assert.assertEquals("", realm.getCharactersToIgnore());

        // A zero width space makes this a different user name
        realm.authenticate(USER_NAME, "wrong");
        realm.authenticate(USER_NAME + "\u200B", "wrong");
        // Should not be locked yet
        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNotNull(p);
    }


    @Test
    public void testCharactersToIgnoreConfigured() {
        realm.setCharactersToIgnore("\u200B");
        Assert.assertEquals("\\u200B", realm.getCharactersToIgnore());

        realm.authenticate(USER_NAME, "wrong");
        realm.authenticate(USER_NAME + "\u200B", "wrong");
        // Should be locked now
        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNull(p);
    }


    @Test
    public void testCharactersToIgnoreReplacesDefault() {
        realm.setCharactersToIgnore("\u200B");

        // The soft hyphen is no longer ignored
        realm.authenticate(USER_NAME, "wrong");
        realm.authenticate(USER_NAME + "\u00AD", "wrong");
        // Should not be locked yet
        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNotNull(p);
    }


    @Test
    public void testCharactersToIgnoreAnywhereInUserName() {
        realm.setCharactersToIgnore("\u200B\u00AD");

        realm.authenticate("us\u00ADer", "wrong");
        realm.authenticate("\u200Buser\u200B", "wrong");
        // Should be locked now
        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNull(p);
    }


    @Test
    public void testCharactersToIgnoreSupplementaryCharacters() {
        // U+1F600 is stored as a surrogate pair. U+1F60E shares the first half of that pair.
        realm.setCharactersToIgnore("\uD83D\uDE00");

        realm.authenticate(USER_NAME, "wrong");
        realm.authenticate(USER_NAME + "\uD83D\uDE0E", "wrong");
        // The second user name is different so should not be locked yet
        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNotNull(p);

        realm.authenticate(USER_NAME, "wrong");
        realm.authenticate(USER_NAME + "\uD83D\uDE00", "wrong");
        // The second user name is the same so should be locked now
        p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNull(p);
    }


    @Test
    public void testCharactersToIgnoreNull() {
        realm.setCharactersToIgnore("\u200B");
        realm.setCharactersToIgnore(null);
        Assert.assertEquals("", realm.getCharactersToIgnore());

        realm.authenticate(USER_NAME, "wrong");
        realm.authenticate(USER_NAME + "\u200B", "wrong");
        // Nothing is ignored so should not be locked
        Principal p = realm.authenticate(USER_NAME, PASSWORD);
        Assert.assertNotNull(p);
    }


    @Test
    public void testNormalizeUnicodeEnabled() {
        // Full width forms of the user name
        String fullWidth = "\uFF55\uFF53\uFF45\uFF52";

        realm.authenticate(fullWidth, "wrong");
        realm.authenticate(USER_NAME, "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
        Assert.assertTrue(realm.isLocked(fullWidth));
    }


    @Test
    public void testNormalizeUnicodeDisabled() {
        realm.setNormalizeUnicode(false);
        String fullWidth = "\uFF55\uFF53\uFF45\uFF52";

        realm.authenticate(fullWidth, "wrong");
        realm.authenticate(USER_NAME, "wrong");
        Assert.assertFalse(realm.isLocked(USER_NAME));
        Assert.assertFalse(realm.isLocked(fullWidth));
    }


    @Test
    public void testNormalizeUnicodeBeforeCase() {
        // U+210C (black-letter capital H) has no lower case form but normalizes to 'H'
        realm.authenticate("\u210Cello", "wrong");
        realm.authenticate("hello", "wrong");
        Assert.assertTrue(realm.isLocked("hello"));
        Assert.assertTrue(realm.isLocked("HELLO"));
    }


    @Test
    public void testStripCombiningMarksEnabled() {
        // Precomposed and decomposed forms of u with an acute accent
        realm.authenticate("\u00FAser", "wrong");
        realm.authenticate("u\u0301ser", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testStripCombiningMarksDisabled() {
        realm.setStripCombiningMarks(false);

        realm.authenticate("\u00FAser", "wrong");
        realm.authenticate("u\u0301ser", "wrong");
        // Normalization still treats the two forms as the same but they are not the same as the plain user name
        Assert.assertTrue(realm.isLocked("\u00FAser"));
        Assert.assertFalse(realm.isLocked(USER_NAME));
    }


    @Test
    public void testCollapseInternalSpaceEnabled() {
        realm.authenticate("my  user", "wrong");
        realm.authenticate("my\u00A0\tuser", "wrong");
        Assert.assertTrue(realm.isLocked("my user"));
    }


    @Test
    public void testCollapseInternalSpaceDisabled() {
        realm.setCollapseInternalSpace(false);

        realm.authenticate("my  user", "wrong");
        realm.authenticate("my\u00A0\tuser", "wrong");
        Assert.assertFalse(realm.isLocked("my user"));
        Assert.assertFalse(realm.isLocked("my  user"));
    }


    @Test
    public void testCollapseInternalSpaceLeavesLeadingAndTrailingSpaceToStripOptions() {
        realm.setStripLeadingSpace(false);
        realm.setStripTrailingSpace(false);

        realm.authenticate("  my  user  ", "wrong");
        realm.authenticate("  my user  ", "wrong");
        // Internal space was collapsed but leading and trailing space was left alone
        Assert.assertTrue(realm.isLocked("  my user  "));
        Assert.assertFalse(realm.isLocked(" my user "));
        Assert.assertFalse(realm.isLocked("my user"));
    }


    @Test
    public void testIgnoredCharactersAreRemovedBeforeSpaceIsCollapsed() {
        realm.authenticate("my \u200B user", "wrong");
        realm.authenticate("my user", "wrong");
        Assert.assertTrue(realm.isLocked("my user"));
    }


    @Test
    public void testNextLineIsSpace() {
        realm.authenticate("my\u0085user", "wrong");
        realm.authenticate("my user", "wrong");
        Assert.assertTrue(realm.isLocked("my user"));
    }


    @Test
    public void testCharactersToIgnoreEscapeUnicode() {
        realm.setCharactersToIgnore("\\u200B\\u00ad");
        Assert.assertEquals("\\u200B\\u00AD", realm.getCharactersToIgnore());

        realm.authenticate("us\u00ADer", "wrong");
        realm.authenticate("user\u200B", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testCharactersToIgnoreEscapeBraces() {
        realm.setCharactersToIgnore("\\u{200B}\\u{1F600}");
        Assert.assertEquals("\\u200B\\u{1F600}", realm.getCharactersToIgnore());

        realm.authenticate("us\uD83D\uDE00er", "wrong");
        realm.authenticate("user\u200B", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testCharactersToIgnoreEscapeSurrogatePair() {
        realm.setCharactersToIgnore("\\uD83D\\uDE00");
        // The pair is a single character
        Assert.assertEquals("\\u{1F600}", realm.getCharactersToIgnore());

        realm.authenticate(USER_NAME, "wrong");
        realm.authenticate(USER_NAME + "\uD83D\uDE0E", "wrong");
        // Different user name
        Assert.assertFalse(realm.isLocked(USER_NAME));
        realm.authenticate(USER_NAME + "\uD83D\uDE00", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testCharactersToIgnoreEscapeBackslash() {
        realm.setCharactersToIgnore("\\\\");
        Assert.assertEquals("\\\\", realm.getCharactersToIgnore());

        // A backslash is ignored but nothing else is
        realm.authenticate("us\\er", "wrong");
        realm.authenticate("user\u200B", "wrong");
        Assert.assertFalse(realm.isLocked(USER_NAME));
        realm.authenticate("\\user", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testCharactersToIgnoreEscapeControlCharacter() {
        // Can't be written in an XML attribute value but can be escaped
        realm.setCharactersToIgnore("\\u0001");

        realm.authenticate("us\u0001er", "wrong");
        realm.authenticate("user\u0001", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testCharactersToIgnoreGetterEscapes() {
        // Printable ASCII is not escaped. Everything else is.
        realm.setCharactersToIgnore("a-~ \u00E9\u200B\\\\\uD83D\uDE00\u0001");
        Assert.assertEquals("a-~\\u0020\\u00E9\\u200B\\\\\\u{1F600}\\u0001", realm.getCharactersToIgnore());
    }


    @Test
    public void testCharactersToIgnoreRoundTrip() {
        String value = "a-~ \u00E9\u200B\\\\\uD83D\uDE00\u0001";
        realm.setCharactersToIgnore(value);
        String escaped = realm.getCharactersToIgnore();

        realm.setCharactersToIgnore("");
        realm.setCharactersToIgnore(escaped);
        Assert.assertEquals(escaped, realm.getCharactersToIgnore());

        // And the characters are the same
        realm.authenticate("us\u0001er", "wrong");
        realm.authenticate("user\u00E9", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testCharactersToIgnoreInvalidEscapes() {
        String[] invalid = { "\\", "a\\", "\\q", "\\u", "\\u20", "\\u20G0", "\\u{}", "\\u{200B", "\\u{110000}",
                "\\u{1234567}", "\\u{D83D}", "\\uD83D", "\\uDE00", "\\uD83Dx", "\\u{ 200B}", "\\u{+200B}",
                "\uD83D" };
        for (String value : invalid) {
            try {
                realm.setCharactersToIgnore(value);
                Assert.fail("Expected an IllegalArgumentException for [" + value + "]");
            } catch (IllegalArgumentException e) {
                // Expected
            }
            try {
                realm.setAdditionalCharactersToIgnore(value);
                Assert.fail("Expected an IllegalArgumentException for additional [" + value + "]");
            } catch (IllegalArgumentException e) {
                // Expected
            }
        }
        // The values were not changed by the failed calls
        Assert.assertTrue(realm.getCharactersToIgnore().contains("\\u200B"));
        Assert.assertEquals("", realm.getAdditionalCharactersToIgnore());
    }


    @Test
    public void testAdditionalCharactersToIgnore() {
        realm.setAdditionalCharactersToIgnore("-");
        Assert.assertEquals("-", realm.getAdditionalCharactersToIgnore());

        // The default characters are still ignored
        realm.authenticate("us-er", "wrong");
        realm.authenticate("user\u200B", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testAdditionalCharactersToIgnoreWithoutDefault() {
        realm.setCharactersToIgnore("");
        realm.setAdditionalCharactersToIgnore("\\u002D");
        // Printable ASCII is not escaped when returned
        Assert.assertEquals("-", realm.getAdditionalCharactersToIgnore());

        realm.authenticate("us-er", "wrong");
        realm.authenticate("user\u200B", "wrong");
        // Only the additional character is ignored
        Assert.assertFalse(realm.isLocked(USER_NAME));
        realm.authenticate("user-", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testAdditionalCharactersToIgnoreEscapes() {
        realm.setCharactersToIgnore("");
        realm.setAdditionalCharactersToIgnore("\\u{1F600}\\u0001");
        Assert.assertEquals("\\u{1F600}\\u0001", realm.getAdditionalCharactersToIgnore());

        realm.authenticate("us\uD83D\uDE00er", "wrong");
        realm.authenticate("user\u0001", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }


    @Test
    public void testAdditionalCharactersToIgnoreNull() {
        realm.setAdditionalCharactersToIgnore("-");
        realm.setAdditionalCharactersToIgnore(null);
        Assert.assertEquals("", realm.getAdditionalCharactersToIgnore());

        realm.authenticate("us-er", "wrong");
        realm.authenticate(USER_NAME, "wrong");
        Assert.assertFalse(realm.isLocked(USER_NAME));
    }


    @Test
    public void testCharactersToIgnoreSetViaIntrospection() {
        // This is how the values are set from server.xml. The escape sequences are in the attribute value as written
        // (an XML parser will have already replaced any character references).
        Assert.assertTrue(IntrospectionUtils.setProperty(realm, "charactersToIgnore", "\\u200B\u00AD"));
        Assert.assertTrue(IntrospectionUtils.setProperty(realm, "additionalCharactersToIgnore", "\\u0001-"));
        Assert.assertEquals("\\u200B\\u00AD", realm.getCharactersToIgnore());
        Assert.assertEquals("\\u0001-", realm.getAdditionalCharactersToIgnore());

        realm.authenticate("us\u0001-er", "wrong");
        realm.authenticate("\u200Buser\u00AD", "wrong");
        Assert.assertTrue(realm.isLocked(USER_NAME));
    }
}

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
package org.apache.tomcat.jdbc.pool.util;

import java.util.ListResourceBundle;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MessageFactory}. The factory is used inline to build
 * exceptions and log lines across the pool, so a message that MessageFormat
 * cannot parse or cannot apply - possible only through a malformed pattern,
 * typically a translation - must degrade to the raw pattern rather than throw.
 */
class TestMessageFactory {

    /**
     * A bundle standing for a translation: patterns that MessageFormat's
     * pattern parser rejects, plus one the formatter itself rejects at
     * application time, alongside a well-formed control case.
     */
    private static final class PatternBundle extends ListResourceBundle {

        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                // A brace opened before the argument index digit: applyPattern
                // fails to parse the index.
                { "brace", "Setting {\"a\":1} was rejected for {0}" },
                // An argument index beyond what the pattern parser accepts.
                { "hugeIndex", "Value {2147483648} is out of range" },
                // A pattern the parser accepts but the formatter cannot apply
                // to a String argument.
                { "numberFormat", "Count is {0,number}" },
                // The control case: a well-formed pattern.
                { "ok", "Count is {0} of {1}." },
            };
        }
    }

    private static MessageFactory factoryFor(ResourceBundle bundle) {
        return new MessageFactory(bundle);
    }

    @Test
    void unparseablePatternFallsBackToRawPattern() {
        MessageFactory factory = factoryFor(new PatternBundle());
        String pattern = "Setting {\"a\":1} was rejected for {0}";
        // Before the guard, this escaped as IllegalArgumentException from
        // new MessageFormat(pattern) into the caller's exception path.
        Assertions.assertEquals(pattern, factory.getInternal("brace", "value"));
    }

    @Test
    void argumentIndexOverflowFallsBackToRawPattern() {
        MessageFactory factory = factoryFor(new PatternBundle());
        String pattern = "Value {2147483648} is out of range";
        Assertions.assertEquals(pattern, factory.getInternal("hugeIndex", "x"));
    }

    @Test
    void unappliableFormatFallsBackToRawPattern() {
        MessageFactory factory = factoryFor(new PatternBundle());
        String pattern = "Count is {0,number}";
        // {0,number} applied to a String throws from format(), not from
        // pattern parsing.
        Assertions.assertEquals(pattern, factory.getInternal("numberFormat", "abc"));
        // The same pattern still formats a Number argument normally.
        Assertions.assertEquals("Count is 5", factory.getInternal("numberFormat", Integer.valueOf(5)));
    }

    @Test
    void wellFormedPatternStillFormats() {
        MessageFactory factory = factoryFor(new PatternBundle());
        Assertions.assertEquals("Count is 7 of 10.", factory.getInternal("ok", Integer.valueOf(7), Integer.valueOf(10)));
    }

    @Test
    void callerSuppliedArgumentArrayIsNotMutated() {
        MessageFactory factory = factoryFor(new PatternBundle());
        Integer seven = Integer.valueOf(7);
        Integer ten = Integer.valueOf(10);
        Object[] caller = { seven, ten };
        // The Number-to-String conversion must not be written back into the
        // array the caller passed in.
        Assertions.assertEquals("Count is 7 of 10.", factory.getInternal("ok", caller));
        Assertions.assertSame(seven, caller[0]);
        Assertions.assertSame(ten, caller[1]);
        // Reusing the same array keeps formatting identically.
        Assertions.assertEquals("Count is 7 of 10.", factory.getInternal("ok", caller));
    }

    @Test
    void missingKeyDegradesToTheKey() {
        MessageFactory factory = factoryFor(new PatternBundle());
        Assertions.assertEquals("no.such.key", factory.getInternal("no.such.key"));
        // The formatted path reuses the same fallback and cannot throw on it.
        Assertions.assertEquals("no.such.key", factory.getInternal("no.such.key", "x", 1));
    }

    @Test
    void realBundleResolvesEveryPoolKey() {
        // The shipped bundle must contain the keys the no-arg and formatted
        // paths promise to render, not fall back to the key text.
        String message = MessageFactory.get("pooledConnection.cannotReestablish");
        Assertions.assertNotEquals("pooledConnection.cannotReestablish", message);
        Assertions.assertThrows(MissingResourceException.class,
                () -> ResourceBundle.getBundle("org.apache.tomcat.jdbc.pool.util.LocalStrings")
                        .getString("this.key.does.not.exist"));
    }

    @Test
    void zeroArgMessageCarriesNoQuoteEscapes() {
        // A message fetched through the no-arg overload is handed out raw, so
        // MessageFormat quote escapes in it reach the application verbatim.
        // This key is only ever fetched without arguments (regression pin).
        Assertions.assertEquals("A connection once released, can't be reestablished.",
                MessageFactory.get("pooledConnection.cannotReestablish"));
    }
}

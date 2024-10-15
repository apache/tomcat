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
package org.apache.tomcat.util.res;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class TestStringManager {

    private static final String PACKAGE_NAME = "org.apache.tomcat.util";
    private static final StringManager sm = StringManager.getManager(PACKAGE_NAME);

    private static final Locale[] ALL_LOCALES = new Locale[] {
            Locale.ENGLISH, Locale.forLanguageTag("cs"), Locale.GERMAN, Locale.forLanguageTag("es"), Locale.FRENCH,
            Locale.JAPANESE, Locale.KOREAN, Locale.forLanguageTag("pt_BR"), Locale.forLanguageTag("ru"),
            Locale.SIMPLIFIED_CHINESE };
    private static final Set<Locale> CJK_LOCALES;

    static {
        CJK_LOCALES = new HashSet<>();
        CJK_LOCALES.add(Locale.SIMPLIFIED_CHINESE);
        CJK_LOCALES.add(Locale.JAPANESE);
        CJK_LOCALES.add(Locale.KOREAN);
    }

    @Test
    public void testNullKey() {
        boolean iaeThrown = false;

        try {
            sm.getString(null);
        } catch (IllegalArgumentException iae) {
            iaeThrown = true;
        }
        Assert.assertTrue("IAE not thrown on null key", iaeThrown);
    }

    @Test
    public void testBug46933() {
        // Check null args are OK
        sm.getString("namingContext.nameNotBound");
        sm.getString("namingContext.nameNotBound", (Object[]) null);
        sm.getString("namingContext.nameNotBound", new Object[1]);
    }

    @Test
    public void testFrench() {
        StringManager sm = StringManager.getManager(PACKAGE_NAME, Locale.FRENCH);
        Assert.assertEquals(Locale.FRENCH, sm.getLocale());
    }

    @Test
    public void testMissingWithTccl() {
        Thread.currentThread().setContextClassLoader(TestStringManager.class.getClassLoader());
        StringManager sm = StringManager.getManager("org.does.not.exist");
        Assert.assertNull(sm.getLocale());
    }


    @Test
    public void testMissingNullTccl() {
        Thread.currentThread().setContextClassLoader(null);
        StringManager sm = StringManager.getManager("org.does.not.exist");
        Assert.assertNull(sm.getLocale());
    }


    @Test
    public void testVersionLoggerListenerAlignment() throws Exception{
        // Get full list of properties from English
        InputStream is = TestStringManager.class.getClassLoader().getResourceAsStream("org/apache/catalina/startup/LocalStrings.properties");
        Properties props = new Properties();
        props.load(is);
        Set<String> versionLoggerListenerKeys = new HashSet<>();
        for (Object key : props.keySet()) {
            if (key instanceof String) {
                if (((String) key).startsWith("versionLoggerListener.")) {
                    versionLoggerListenerKeys.add((String) key);
                }
            }
        }

        for (Locale locale : ALL_LOCALES) {
            testVersionLoggerListenerAlignment(versionLoggerListenerKeys, locale);
        }
    }


    private void testVersionLoggerListenerAlignment(Set<String> keys, Locale locale) {
        System.out.println("\n" + locale.getDisplayName());
        StringManager sm = StringManager.getManager("org.apache.catalina.startup", locale);
        int standardLength = -1;
        for (String key : keys) {
            String fullLine = sm.getString(key, "XXX");
            // Provides a visual check but be aware CJK characters may be
            // displayed using full width (1 CJK character uses the space of two
            // ASCII characters) as assumed by this test or may use a narrower
            // representation.
            System.out.println(fullLine);
            int insertIndex = fullLine.indexOf("XXX");
            String preInsert = fullLine.substring(0, insertIndex);
            int length = getFixedWidth(preInsert, locale);
            if (standardLength == -1) {
                standardLength = length;
            } else {
                Assert.assertEquals(locale.getDisplayName() + " - " + key, standardLength, length);
            }
        }
    }


    private int getFixedWidth(String s, Locale l) {
        if (CJK_LOCALES.contains(l)) {
            // This isn't perfect but it is good enough for this test.
            // The test assumes CJK characters are all displayed double width
            // Ubuntu uses double width characters by default.
            // Eclipse uses 1.5 width characters by default.
            int len = 0;
            for (char c : s.toCharArray()) {
                if (c < 128) {
                    len ++;
                } else {
                    len += 2;
                }
            }
            return len;
        } else {
            return s.length();
        }
    }
}

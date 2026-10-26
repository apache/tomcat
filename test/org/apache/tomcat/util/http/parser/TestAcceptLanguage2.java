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
package org.apache.tomcat.util.http.parser;

import java.io.StringReader;
import java.util.List;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

/*
 * Tests for AcceptLanguage2, the Accept-Language parser that creates Locales
 * with the deprecated Locale constructor.
 * <p>
 * The Locales match those that AcceptLanguage (which uses
 * Locale.forLanguageTag()) produces for the language, region and variant
 * components of a tag. Script subtags are the documented exception: the
 * deprecated constructor cannot represent them, so they are dropped and the
 * result is the Locale for the tag with the script subtag removed.
 */
public class TestAcceptLanguage2 {

    private static final Locale L_EN = Locale.forLanguageTag("en");
    private static final Locale L_EN_GB = Locale.forLanguageTag("en-gb");
    private static final Locale L_FR = Locale.forLanguageTag("fr");
    private static final double Q1_000 = 1;
    private static final double Q0_500 = 0.5;

    private static List<AcceptLanguage> parse(String value) throws Exception {
        return AcceptLanguage2.parse(new StringReader(value), -1);
    }

    private static Locale locale(String value) throws Exception {
        List<AcceptLanguage> actual = parse(value);
        Assert.assertEquals(1, actual.size());
        return actual.get(0).getLocale();
    }

    /*
     * Removes the script subtag of a tag, which is what this parser is expected
     * to do with it.
     */
    private static String withoutScript(String tag) {
        int start = tag.indexOf('-');
        if (start < 0) {
            return tag;
        }
        int end = tag.indexOf('-', start + 1);
        int subEnd = end < 0 ? tag.length() : end;
        if (subEnd - start - 1 != 4) {
            return tag;
        }
        for (int i = start + 1; i < subEnd; i++) {
            char c = tag.charAt(i);
            if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
                return tag;
            }
        }
        return tag.substring(0, start) + (end < 0 ? "" : tag.substring(end));
    }

    /*
     * The reported case: the script subtag of zh-Hans-CN must no longer end up
     * in the Locale variant, which produced the language tag
     * zh-CN-x-lvariant-HANS rather than the tag the client sent.
     */
    @Test
    public void testScriptIsNotMappedToTheVariant() throws Exception {
        List<AcceptLanguage> actual = parse("zh-Hans-CN;q=1.0");

        Assert.assertEquals(1, actual.size());
        Locale locale = actual.get(0).getLocale();
        Assert.assertEquals("zh", locale.getLanguage());
        Assert.assertEquals("", locale.getScript());
        Assert.assertEquals("CN", locale.getCountry());
        Assert.assertEquals("", locale.getVariant());
        Assert.assertFalse(locale.toLanguageTag(), locale.toLanguageTag().contains("x-lvariant"));
        Assert.assertEquals("zh-CN", locale.toLanguageTag());
        Assert.assertEquals(Locale.forLanguageTag("zh-CN"), locale);
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    /*
     * Script subtags, in the various cases a header may use, are dropped. The
     * result is the Locale for the same tag without the script subtag, and the
     * region that follows the script is still used.
     */
    @Test
    public void testScriptIsDropped() throws Exception {
        String[] tags = { "zh-Hans-CN", "zh-Hant-TW", "zh-hans-cn", "ZH-HANS-CN", "zh-Hant-HK", "sr-Latn-RS",
                "sr-Cyrl-ME", "yue-Hant-HK", "az-Latn-AZ", "uz-Arab", "mn-Mong-CN", "hy-npoy", "pa-Pahk-PK",
                "bs-Cyrl-BA", "zh-Hani", "sr-latn", "zh-Latn-419" };

        for (String tag : tags) {
            Locale actual = locale(tag);
            Assert.assertEquals(tag, Locale.forLanguageTag(withoutScript(tag)), actual);
            Assert.assertEquals(tag, "", actual.getScript());
            Assert.assertEquals(tag, Locale.forLanguageTag(withoutScript(tag)).toLanguageTag(),
                    actual.toLanguageTag());
        }
    }

    /*
     * Script subtags that are not in the IANA registry, and script subtags in
     * any case, are dropped like the registered ones. Locale.forLanguageTag()
     * accepts them, so they must not make the tag invalid here.
     */
    @Test
    public void testUnregisteredScript() throws Exception {
        String[] tags = { "qaa-Qaak-CN", "zh-Qaaa-CN", "en-Zzzz-US", "abcd-Hans-US" };

        for (String tag : tags) {
            Assert.assertEquals(tag, Locale.forLanguageTag(withoutScript(tag)), locale(tag));
        }
    }

    /*
     * Dropping the script does not affect the variant, which is kept unchanged,
     * nor the region.
     */
    @Test
    public void testVariantWithScript() throws Exception {
        String[] tags = { "zh-Hans-CN-1996", "zh-Hant-1996", "de-Latn-DE-POSIX", "sr-Latn-RS-1996",
                "en-Latn-US-oxendict", "zh-Hans-US-VALAMY" };

        for (String tag : tags) {
            Locale expected = Locale.forLanguageTag(withoutScript(tag));
            Locale actual = locale(tag);
            Assert.assertEquals(tag, expected, actual);
            Assert.assertEquals(tag, expected.getVariant(), actual.getVariant());
            Assert.assertEquals(tag, expected.toLanguageTag(), actual.toLanguageTag());
        }
    }

    /*
     * Only the first four letter subtag before the region is a script. A second
     * one is not a valid variant subtag and drops the remainder of the tag,
     * while a script subtag after the region is ignored rather than replacing
     * the region.
     */
    @Test
    public void testRepeatedScript() throws Exception {
        Assert.assertEquals(Locale.forLanguageTag("zh"), locale("zh-Hans-Hans"));
        Assert.assertEquals(Locale.forLanguageTag("en-US"), locale("en-Latn-US-Latn"));
        Assert.assertEquals(Locale.forLanguageTag("zh-CN"), locale("zh-CN-Hans"));
    }

    /*
     * Same as the bug56848 test for AcceptLanguage. Note that the two entries
     * of that header produce two different Locales with AcceptLanguage, while
     * this parser drops the scripts and cannot tell zh-Hant-CN and zh-Hans-CN
     * apart. That is the accepted cost of using the deprecated constructor.
     */
    @Test
    public void bug56848() throws Exception {
        List<AcceptLanguage> actual = parse("zh-hant-CN;q=0.5,zh-hans-TW;q=0.05");

        Assert.assertEquals(2, actual.size());

        Assert.assertEquals(Locale.forLanguageTag("zh-CN"), actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
        Assert.assertEquals(Locale.forLanguageTag("zh-TW"), actual.get(1).getLocale());
        Assert.assertEquals(Q1_000 * 0.05, actual.get(1).getQuality(), 0.0001);
    }

    /*
     * Tags without a script subtag produce the same Locale as
     * Locale.forLanguageTag().
     */
    @Test
    public void testNoScript() throws Exception {
        String[] tags = { "en", "en-US", "en-GB", "fr-FR", "es-419", "zh-CN", "ja", "de-DE-1901", "de-DE-POSIX",
                "en-US-oxendict", "en-US-1996", "nl-NL", "pt-BR" };

        for (String tag : tags) {
            Assert.assertEquals(tag, Locale.forLanguageTag(tag), locale(tag));
        }
    }

    /*
     * Header parsing itself is shared with AcceptLanguage, so the basic cases
     * behave the same way.
     */
    @Test
    public void testMultiple() throws Exception {
        List<AcceptLanguage> actual = parse("en; q= 0.05,fr;q=0.5,zh-Hans-CN");

        Assert.assertEquals(3, actual.size());
        Assert.assertEquals(L_EN, actual.get(0).getLocale());
        Assert.assertEquals(0.05, actual.get(0).getQuality(), 0.0001);
        Assert.assertEquals(L_FR, actual.get(1).getLocale());
        Assert.assertEquals(Q0_500, actual.get(1).getQuality(), 0.0001);
        Assert.assertEquals(Locale.forLanguageTag("zh-CN"), actual.get(2).getLocale());
        Assert.assertEquals(Q1_000, actual.get(2).getQuality(), 0.0001);
    }


    @Test
    public void testSingle() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage2.parse(new StringReader("en-gb;q=0.500"), -1);

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    /*
     * Malformed tags are either reduced to what could be parsed or dropped, as
     * documented for this parser.
     */
    @Test
    public void testMalformed() throws Exception {
        // Invalid primary subtags give Locale.ROOT
        Assert.assertEquals(Locale.ROOT, locale("a"));
        Assert.assertEquals(Locale.ROOT, locale("abcdefghi-US"));

        // A tag that is invalid after the language subtag keeps what was parsed
        Locale actual = locale("zh-Hans-");
        Assert.assertEquals("zh", actual.getLanguage());
        Assert.assertEquals("", actual.getScript());
        Assert.assertEquals("", actual.getCountry());
        Assert.assertEquals("en", locale("en-US!").getLanguage());
    }

    /*
     * Accepted degradation: extensions are dropped, unlike
     * Locale.forLanguageTag() which records them on the Locale. The script is
     * dropped as well and the region is still parsed.
     */
    @Test
    public void testExtensionsAreDropped() throws Exception {
        Locale actual = locale("zh-Hans-CN-u-ca-buddhist");

        Assert.assertEquals("", actual.getScript());
        Assert.assertEquals("CN", actual.getCountry());
        Assert.assertNull(actual.getExtension('u'));
        Assert.assertEquals(Locale.forLanguageTag("zh-CN"), actual);

        actual = locale("en-Latn-GB-x-priv");

        Assert.assertEquals("", actual.getScript());
        Assert.assertEquals("GB", actual.getCountry());
        Assert.assertNull(actual.getExtension('x'));
    }

    /*
     * The limit on the number of entries is enforced while the header is
     * parsed.
     */
    @Test
    public void testLimited() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage2.parse(new StringReader("zh-Hans-CN,en,fr,de"), 2);

        Assert.assertEquals(2, actual.size());
        Assert.assertEquals(Locale.forLanguageTag("zh-CN"), actual.get(0).getLocale());
        Assert.assertEquals(L_EN, actual.get(1).getLocale());

        Assert.assertEquals(0, AcceptLanguage2.parse(new StringReader("zh-Hans-CN,en"), 0).size());
    }

}

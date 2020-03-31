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

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CharsetCache {

    /* Note: Package private to enable testing without reflection */
    static final String[] INITIAL_CHARSETS = new String[] { "iso-8859-1", "utf-8" };

    /*
     *  Note: Package private to enable testing without reflection
     */
    static final String[] LAZY_CHARSETS = new String[] {
            // Initial set from Oracle JDK 8 u192
            "037", "1006", "1025", "1026", "1046", "1047", "1089", "1097", "1098", "1112", "1122", "1123", "1124",
            "1140", "1141", "1142", "1143", "1144", "1145", "1146", "1147", "1148", "1149", "1166", "1364", "1381",
            "1383", "273", "277", "278", "280", "284", "285", "290", "297", "300", "33722", "420", "424", "437", "500",
            "5601", "646", "737", "775", "813", "834", "838", "850", "852", "855", "856", "857", "858", "860", "861",
            "862", "863", "864", "865", "866", "868", "869", "870", "871", "874", "875", "8859_13", "8859_15", "8859_2",
            "8859_3", "8859_4", "8859_5", "8859_6", "8859_7", "8859_8", "8859_9", "912", "913", "914", "915", "916",
            "918", "920", "921", "922", "923", "930", "933", "935", "937", "939", "942", "942c", "943", "943c", "948",
            "949", "949c", "950", "964", "970", "ansi-1251", "ansi_x3.4-1968", "ansi_x3.4-1986", "arabic", "ascii",
            "ascii7", "asmo-708", "big5", "big5-hkscs", "big5-hkscs", "big5-hkscs-2001", "big5-hkscs:unicode3.0",
            "big5_hkscs", "big5_hkscs_2001", "big5_solaris", "big5hk", "big5hk-2001", "big5hkscs", "big5hkscs-2001",
            "ccsid00858", "ccsid01140", "ccsid01141", "ccsid01142", "ccsid01143", "ccsid01144", "ccsid01145",
            "ccsid01146", "ccsid01147", "ccsid01148", "ccsid01149", "cesu-8", "cesu8", "cns11643", "compound_text",
            "cp-ar", "cp-gr", "cp-is", "cp00858", "cp01140", "cp01141", "cp01142", "cp01143", "cp01144", "cp01145",
            "cp01146", "cp01147", "cp01148", "cp01149", "cp037", "cp1006", "cp1025", "cp1026", "cp1046", "cp1047",
            "cp1089", "cp1097", "cp1098", "cp1112", "cp1122", "cp1123", "cp1124", "cp1140", "cp1141", "cp1142",
            "cp1143", "cp1144", "cp1145", "cp1146", "cp1147", "cp1148", "cp1149", "cp1166", "cp1250", "cp1251",
            "cp1252", "cp1253", "cp1254", "cp1255", "cp1256", "cp1257", "cp1258", "cp1364", "cp1381", "cp1383", "cp273",
            "cp277", "cp278", "cp280", "cp284", "cp285", "cp290", "cp297", "cp300", "cp33722", "cp367", "cp420",
            "cp424", "cp437", "cp500", "cp50220", "cp50221", "cp5346", "cp5347", "cp5348", "cp5349", "cp5350", "cp5353",
            "cp737", "cp775", "cp813", "cp833", "cp834", "cp838", "cp850", "cp852", "cp855", "cp856", "cp857", "cp858",
            "cp860", "cp861", "cp862", "cp863", "cp864", "cp865", "cp866", "cp868", "cp869", "cp870", "cp871", "cp874",
            "cp875", "cp912", "cp913", "cp914", "cp915", "cp916", "cp918", "cp920", "cp921", "cp922", "cp923", "cp930",
            "cp933", "cp935", "cp936", "cp937", "cp939", "cp942", "cp942c", "cp943", "cp943c", "cp948", "cp949",
            "cp949c", "cp950", "cp964", "cp970", "cpibm284", "cpibm285", "cpibm297", "cpibm37", "cs-ebcdic-cp-ca",
            "cs-ebcdic-cp-nl", "cs-ebcdic-cp-us", "cs-ebcdic-cp-wt", "csascii", "csbig5", "cscesu-8", "cseuckr",
            "cseucpkdfmtjapanese", "cshalfwidthkatakana", "csibm037", "csibm278", "csibm284", "csibm285", "csibm290",
            "csibm297", "csibm420", "csibm424", "csibm500", "csibm857", "csibm860", "csibm861", "csibm862", "csibm863",
            "csibm864", "csibm865", "csibm866", "csibm868", "csibm869", "csibm870", "csibm871", "csiso153gost1976874",
            "csiso159jisx02121990", "csiso2022cn", "csiso2022jp", "csiso2022jp2", "csiso2022kr", "csiso87jisx0208",
            "csisolatin0", "csisolatin2", "csisolatin3", "csisolatin4", "csisolatin5", "csisolatin9",
            "csisolatinarabic", "csisolatincyrillic", "csisolatingreek", "csisolatinhebrew", "csjisencoding", "cskoi8r",
            "cspc850multilingual", "cspc862latinhebrew", "cspc8codepage437", "cspcp852", "cspcp855", "csshiftjis",
            "cswindows31j", "cyrillic", "default", "ebcdic-cp-ar1", "ebcdic-cp-ar2", "ebcdic-cp-bh", "ebcdic-cp-ca",
            "ebcdic-cp-ch", "ebcdic-cp-fr", "ebcdic-cp-gb", "ebcdic-cp-he", "ebcdic-cp-is", "ebcdic-cp-nl",
            "ebcdic-cp-roece", "ebcdic-cp-se", "ebcdic-cp-us", "ebcdic-cp-wt", "ebcdic-cp-yu", "ebcdic-de-273+euro",
            "ebcdic-dk-277+euro", "ebcdic-es-284+euro", "ebcdic-fi-278+euro", "ebcdic-fr-277+euro", "ebcdic-gb",
            "ebcdic-gb-285+euro", "ebcdic-international-500+euro", "ebcdic-it-280+euro", "ebcdic-jp-kana",
            "ebcdic-no-277+euro", "ebcdic-s-871+euro", "ebcdic-se-278+euro", "ebcdic-sv", "ebcdic-us-037+euro",
            "ecma-114", "ecma-118", "elot_928", "euc-cn", "euc-jp", "euc-jp-linux", "euc-kr", "euc-tw", "euc_cn",
            "euc_jp", "euc_jp_linux", "euc_jp_solaris", "euc_kr", "euc_tw", "euccn", "eucjis", "eucjp", "eucjp-open",
            "euckr", "euctw", "extended_unix_code_packed_format_for_japanese", "gb18030", "gb18030-2000", "gb2312",
            "gb2312", "gb2312-1980", "gb2312-80", "gbk", "greek", "greek8", "hebrew", "ibm-037", "ibm-1006", "ibm-1025",
            "ibm-1026", "ibm-1046", "ibm-1047", "ibm-1089", "ibm-1097", "ibm-1098", "ibm-1112", "ibm-1122", "ibm-1123",
            "ibm-1124", "ibm-1166", "ibm-1364", "ibm-1381", "ibm-1383", "ibm-273", "ibm-277", "ibm-278", "ibm-280",
            "ibm-284", "ibm-285", "ibm-290", "ibm-297", "ibm-300", "ibm-33722", "ibm-33722_vascii_vpua", "ibm-37",
            "ibm-420", "ibm-424", "ibm-437", "ibm-500", "ibm-5050", "ibm-737", "ibm-775", "ibm-813", "ibm-833",
            "ibm-834", "ibm-838", "ibm-850", "ibm-852", "ibm-855", "ibm-856", "ibm-857", "ibm-860", "ibm-861",
            "ibm-862", "ibm-863", "ibm-864", "ibm-865", "ibm-866", "ibm-868", "ibm-869", "ibm-870", "ibm-871",
            "ibm-874", "ibm-875", "ibm-912", "ibm-913", "ibm-914", "ibm-915", "ibm-916", "ibm-918", "ibm-920",
            "ibm-921", "ibm-922", "ibm-923", "ibm-930", "ibm-933", "ibm-935", "ibm-937", "ibm-939", "ibm-942",
            "ibm-942c", "ibm-943", "ibm-943c", "ibm-948", "ibm-949", "ibm-949c", "ibm-950", "ibm-964", "ibm-970",
            "ibm-euckr", "ibm-thai", "ibm00858", "ibm01140", "ibm01141", "ibm01142", "ibm01143", "ibm01144", "ibm01145",
            "ibm01146", "ibm01147", "ibm01148", "ibm01149", "ibm037", "ibm037", "ibm1006", "ibm1025", "ibm1026",
            "ibm1026", "ibm1046", "ibm1047", "ibm1089", "ibm1097", "ibm1098", "ibm1112", "ibm1122", "ibm1123",
            "ibm1124", "ibm1166", "ibm1364", "ibm1381", "ibm1383", "ibm273", "ibm273", "ibm277", "ibm277", "ibm278",
            "ibm278", "ibm280", "ibm280", "ibm284", "ibm284", "ibm285", "ibm285", "ibm290", "ibm290", "ibm297",
            "ibm297", "ibm300", "ibm33722", "ibm367", "ibm420", "ibm420", "ibm424", "ibm424", "ibm437", "ibm437",
            "ibm500", "ibm500", "ibm737", "ibm775", "ibm775", "ibm813", "ibm833", "ibm834", "ibm838", "ibm850",
            "ibm850", "ibm852", "ibm852", "ibm855", "ibm855", "ibm856", "ibm857", "ibm857", "ibm860", "ibm860",
            "ibm861", "ibm861", "ibm862", "ibm862", "ibm863", "ibm863", "ibm864", "ibm864", "ibm865", "ibm865",
            "ibm866", "ibm866", "ibm868", "ibm868", "ibm869", "ibm869", "ibm870", "ibm870", "ibm871", "ibm871",
            "ibm874", "ibm875", "ibm912", "ibm913", "ibm914", "ibm915", "ibm916", "ibm918", "ibm920", "ibm921",
            "ibm922", "ibm923", "ibm930", "ibm933", "ibm935", "ibm937", "ibm939", "ibm942", "ibm942c", "ibm943",
            "ibm943c", "ibm948", "ibm949", "ibm949c", "ibm950", "ibm964", "ibm970", "iscii", "iscii91",
            "iso-10646-ucs-2", "iso-2022-cn", "iso-2022-cn-cns", "iso-2022-cn-gb", "iso-2022-jp", "iso-2022-jp-2",
            "iso-2022-kr", "iso-8859-11", "iso-8859-13", "iso-8859-15", "iso-8859-15", "iso-8859-2", "iso-8859-3",
            "iso-8859-4", "iso-8859-5", "iso-8859-6", "iso-8859-7", "iso-8859-8", "iso-8859-9", "iso-ir-101",
            "iso-ir-109", "iso-ir-110", "iso-ir-126", "iso-ir-127", "iso-ir-138", "iso-ir-144", "iso-ir-148",
            "iso-ir-153", "iso-ir-159", "iso-ir-6", "iso-ir-87", "iso2022cn", "iso2022cn_cns", "iso2022cn_gb",
            "iso2022jp", "iso2022jp2", "iso2022kr", "iso646-us", "iso8859-13", "iso8859-15", "iso8859-2", "iso8859-3",
            "iso8859-4", "iso8859-5", "iso8859-6", "iso8859-7", "iso8859-8", "iso8859-9", "iso8859_11", "iso8859_13",
            "iso8859_15", "iso8859_15_fdis", "iso8859_2", "iso8859_3", "iso8859_4", "iso8859_5", "iso8859_6",
            "iso8859_7", "iso8859_8", "iso8859_9", "iso_646.irv:1983", "iso_646.irv:1991", "iso_8859-13", "iso_8859-15",
            "iso_8859-2", "iso_8859-2:1987", "iso_8859-3", "iso_8859-3:1988", "iso_8859-4", "iso_8859-4:1988",
            "iso_8859-5", "iso_8859-5:1988", "iso_8859-6", "iso_8859-6:1987", "iso_8859-7", "iso_8859-7:1987",
            "iso_8859-8", "iso_8859-8:1988", "iso_8859-9", "iso_8859-9:1989", "jis", "jis0201", "jis0208", "jis0212",
            "jis_c6226-1983", "jis_encoding", "jis_x0201", "jis_x0201", "jis_x0208-1983", "jis_x0212-1990",
            "jis_x0212-1990", "jisautodetect", "johab", "koi8", "koi8-r", "koi8-u", "koi8_r", "koi8_u",
            "ks_c_5601-1987", "ksc5601", "ksc5601-1987", "ksc5601-1992", "ksc5601_1987", "ksc5601_1992", "ksc_5601",
            "l2", "l3", "l4", "l5", "l9", "latin0", "latin2", "latin3", "latin4", "latin5", "latin9", "macarabic",
            "maccentraleurope", "maccroatian", "maccyrillic", "macdingbat", "macgreek", "machebrew", "maciceland",
            "macroman", "macromania", "macsymbol", "macthai", "macturkish", "macukraine", "ms-874", "ms1361", "ms50220",
            "ms50221", "ms874", "ms932", "ms936", "ms949", "ms950", "ms950_hkscs", "ms950_hkscs_xp", "ms_936", "ms_949",
            "ms_kanji", "pc-multilingual-850+euro", "pck", "shift-jis", "shift_jis", "shift_jis", "sjis",
            "st_sev_358-88", "sun_eu_greek", "tis-620", "tis620", "tis620.2533", "unicode", "unicodebig",
            "unicodebigunmarked", "unicodelittle", "unicodelittleunmarked", "us", "us-ascii", "utf-16", "utf-16be",
            "utf-16le", "utf-32", "utf-32be", "utf-32be-bom", "utf-32le", "utf-32le-bom", "utf16", "utf32", "utf_16",
            "utf_16be", "utf_16le", "utf_32", "utf_32be", "utf_32be_bom", "utf_32le", "utf_32le_bom", "windows-1250",
            "windows-1251", "windows-1252", "windows-1253", "windows-1254", "windows-1255", "windows-1256",
            "windows-1257", "windows-1258", "windows-31j", "windows-437", "windows-874", "windows-932", "windows-936",
            "windows-949", "windows-950", "windows-iso2022jp", "windows949", "x-big5-hkscs-2001", "x-big5-solaris",
            "x-compound-text", "x-compound_text", "x-euc-cn", "x-euc-jp", "x-euc-jp-linux", "x-euc-tw", "x-eucjp",
            "x-eucjp-open", "x-ibm1006", "x-ibm1025", "x-ibm1046", "x-ibm1097", "x-ibm1098", "x-ibm1112", "x-ibm1122",
            "x-ibm1123", "x-ibm1124", "x-ibm1166", "x-ibm1364", "x-ibm1381", "x-ibm1383", "x-ibm300", "x-ibm33722",
            "x-ibm737", "x-ibm833", "x-ibm834", "x-ibm856", "x-ibm874", "x-ibm875", "x-ibm921", "x-ibm922", "x-ibm930",
            "x-ibm933", "x-ibm935", "x-ibm937", "x-ibm939", "x-ibm942", "x-ibm942c", "x-ibm943", "x-ibm943c",
            "x-ibm948", "x-ibm949", "x-ibm949c", "x-ibm950", "x-ibm964", "x-ibm970", "x-iscii91", "x-iso-2022-cn-cns",
            "x-iso-2022-cn-gb", "x-iso-8859-11", "x-jis0208", "x-jisautodetect", "x-johab", "x-macarabic",
            "x-maccentraleurope", "x-maccroatian", "x-maccyrillic", "x-macdingbat", "x-macgreek", "x-machebrew",
            "x-maciceland", "x-macroman", "x-macromania", "x-macsymbol", "x-macthai", "x-macturkish", "x-macukraine",
            "x-ms932_0213", "x-ms950-hkscs", "x-ms950-hkscs-xp", "x-mswin-936", "x-pck", "x-sjis", "x-sjis_0213",
            "x-utf-16be", "x-utf-16le", "x-utf-16le-bom", "x-utf-32be", "x-utf-32be-bom", "x-utf-32le",
            "x-utf-32le-bom", "x-windows-50220", "x-windows-50221", "x-windows-874", "x-windows-949", "x-windows-950",
            "x-windows-iso2022jp", "x0201", "x0208", "x0212", "x11-compound_text",
            // Added from Oracle JDK 10.0.2
            "csiso885915", "csiso885916", "iso-8859-16", "iso-ir-226", "iso_8859-16", "iso_8859-16:2001", "l10",
            "latin-9", "latin10", "ms932-0213", "ms932:2004", "ms932_0213", "shift_jis:2004", "shift_jis_0213:2004",
            "sjis-0213", "sjis:2004", "sjis_0213", "sjis_0213:2004", "windows-932-0213", "windows-932:2004",
            // Added from OpenJDK 11.0.1
            "932", "cp932", "cpeuccn", "ibm-1252", "ibm-932", "ibm-euccn", "ibm1252", "ibm932", "ibmeuccn", "x-ibm932",
            // Added from OpenJDK 12 ea28
            "1129", "cp1129", "ibm-1129", "ibm-euctw", "ibm1129", "x-ibm1129",
            // Added from OpenJDK 13 ea15
            "29626c", "833", "cp29626c", "ibm-1140", "ibm-1141", "ibm-1142", "ibm-1143", "ibm-1144", "ibm-1145",
            "ibm-1146", "ibm-1147", "ibm-1148", "ibm-1149", "ibm-29626c", "ibm-858", "ibm-eucjp", "ibm1140", "ibm1141",
            "ibm1142", "ibm1143", "ibm1144", "ibm1145", "ibm1146", "ibm1147", "ibm1148", "ibm1149", "ibm29626c",
            "ibm858", "x-ibm29626c",
            // Added from HPE JVM 1.8.0.17-hp-ux
            "cp1051", "cp1386", "cshproman8", "hp-roman8", "ibm-1051", "r8", "roman8", "roman9"
            // If you add and entry to this list, ensure you run
            // TestCharsetUtil#testIsAcsiiSupersetAll()
            };

    private static final Charset DUMMY_CHARSET = new DummyCharset("Dummy",  null);

    private ConcurrentMap<String,Charset> cache = new ConcurrentHashMap<String, Charset>();

    public CharsetCache() {
        // Pre-populate the cache
        for (String charsetName : INITIAL_CHARSETS) {
            Charset charset = Charset.forName(charsetName);
            addToCache(charsetName, charset);
        }

        for (String charsetName : LAZY_CHARSETS) {
            addToCache(charsetName, DUMMY_CHARSET);
        }
    }


    private void addToCache(String name, Charset charset) {
        cache.put(name, charset);
        for (String alias : charset.aliases()) {
            cache.put(alias.toLowerCase(Locale.ENGLISH), charset);
        }
    }


    public Charset getCharset(String charsetName) {
        String lcCharsetName = charsetName.toLowerCase(Locale.ENGLISH);

        Charset result = cache.get(lcCharsetName);

        if (result == DUMMY_CHARSET) {
            // Name is known but the Charset is not in the cache
            Charset charset = Charset.forName(lcCharsetName);
            if (charset == null) {
                // Charset not available in this JVM - remove cache entry
                cache.remove(lcCharsetName);
                result = null;
            } else {
                // Charset is available - populate cache entry
                addToCache(lcCharsetName, charset);
                result = charset;
            }
        }

        return result;
    }


    /*
     * Placeholder Charset implementation for entries that will be loaded lazily
     * into the cache.
     */
    private static class DummyCharset extends Charset {

        protected DummyCharset(String canonicalName, String[] aliases) {
            super(canonicalName, aliases);
        }

        @Override
        public boolean contains(Charset cs) {
            return false;
        }

        @Override
        public CharsetDecoder newDecoder() {
            return null;
        }

        @Override
        public CharsetEncoder newEncoder() {
            return null;
        }
    }
}

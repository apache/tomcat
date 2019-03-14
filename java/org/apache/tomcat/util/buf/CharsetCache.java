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

    private static final String[] INITIAL_CHARSETS = new String[] { "iso-8859-1", "utf-8" };

    /*
     * Tested with:
     *  - Oracle JDK 8 u192
     *  - OpenJDK 13 EA 4
     */
    private static final String[] LAZY_CHARSETS = new String[] {
            "big5", "big5-hkscs", "cesu-8", "euc-jp", "euc-kr", "gb18030", "gb2312", "gbk", "ibm-thai", "ibm00858",
            "ibm01140", "ibm01141", "ibm01142", "ibm01143", "ibm01144", "ibm01145", "ibm01146", "ibm01147", "ibm01148",
            "ibm01149", "ibm037", "ibm1026", "ibm1047", "ibm273", "ibm277", "ibm278", "ibm280", "ibm284", "ibm285",
            "ibm290", "ibm297", "ibm420", "ibm424", "ibm437", "ibm500", "ibm775", "ibm850", "ibm852", "ibm855",
            "ibm857", "ibm860", "ibm861", "ibm862", "ibm863", "ibm864", "ibm865", "ibm866", "ibm868", "ibm869",
            "ibm870", "ibm871", "ibm918", "iso-2022-cn", "iso-2022-jp", "iso-2022-jp-2", "iso-2022-kr", "iso-8859-13",
            "iso-8859-15", "iso-8859-2", "iso-8859-3", "iso-8859-4", "iso-8859-5", "iso-8859-6", "iso-8859-7",
            "iso-8859-8", "iso-8859-9", "iso-8859-16", "jis_x0201", "jis_x0212-1990", "koi8-r", "koi8-u", "shift_jis",
            "tis-620", "us-ascii", "utf-16", "utf-16be", "utf-16le", "utf-32", "utf-32be", "utf-32le", "x-utf-32be-bom",
            "x-utf-32le-bom", "windows-1250", "windows-1251", "windows-1252", "windows-1253", "windows-1254",
            "windows-1255", "windows-1256", "windows-1257", "windows-1258", "windows-31j", "x-big5-hkscs-2001",
            "x-big5-solaris", "x-compound_text", "x-euc-tw", "x-ibm1006", "x-ibm1025", "x-ibm1046", "x-ibm1097",
            "x-ibm1098", "x-ibm1112", "x-ibm1122", "x-ibm1123", "x-ibm1124", "x-ibm1129", "x-ibm1166", "x-ibm1364",
            "x-ibm1381", "x-ibm1383", "x-ibm300", "x-ibm33722", "x-ibm737", "x-ibm833", "x-ibm834", "x-ibm856",
            "x-ibm874", "x-ibm875", "x-ibm921", "x-ibm922", "x-ibm930", "x-ibm933", "x-ibm935", "x-ibm937", "x-ibm939",
            "x-ibm942", "x-ibm942c", "x-ibm943", "x-ibm943c", "x-ibm948", "x-ibm949", "x-ibm949c", "x-ibm950",
            "x-ibm964", "x-ibm970", "x-iscii91", "x-iso-2022-cn-cns", "x-iso-2022-cn-gb", "x-jis0208",
            "x-jisautodetect", "x-johab", "x-ms932_0213", "x-ms950-hkscs", "x-ms950-hkscs-xp", "x-macarabic",
            "x-maccentraleurope", "x-maccroatian", "x-maccyrillic", "x-macdingbat", "x-macgreek", "x-machebrew",
            "x-maciceland", "x-macroman", "x-macromania", "x-macsymbol", "x-macthai", "x-macturkish", "x-macukraine",
            "x-pck", "x-sjis_0213", "x-utf-16le-bom", "x-euc-jp-linux", "x-eucjp-open", "x-iso-8859-11", "x-mswin-936",
            "x-windows-50220", "x-windows-50221", "x-windows-874", "x-windows-949", "x-windows-950",
            "x-windows-iso2022jp"
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

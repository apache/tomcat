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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * MIME2Java is a convenience class which handles conversions between MIME charset names
 * and Java encoding names.
 * <p>The supported XML encodings are the intersection of XML-supported code sets and those
 * supported in JDK 1.1.
 * <p>MIME charset names are used on <var>xmlEncoding</var> parameters to methods such
 * as <code>TXDocument#setEncoding</code> and <code>DTD#setEncoding</code>.
 * <p>Java encoding names are used on <var>encoding</var> parameters to
 * methods such as <code>TXDocument#printWithFormat</code> and <code>DTD#printExternal</code>.
 * <TABLE BORDER="0" WIDTH="100%">
 *  <caption>MIME charset name to Java encoding name mapping</caption>
 *  <TR>
 *      <TD>
 *          <P><B>Common Name</B>
 *      </TD>
 *      <TD>
 *          <P><B>Use this name in XML files</B>
 *      </TD>
 *      <TD>
 *          <P><B>Name Type</B>
 *      </TD>
 *      <TD>
 *          <P><B>Xerces converts to this Java Encoder Name</B>
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>8 bit Unicode</TD>
 *      <TD>
 *          <P>UTF-8
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>UTF8
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>ISO Latin 1</TD>
 *      <TD>
 *          <P>ISO-8859-1
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>ISO-8859-1
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>ISO Latin 2</TD>
 *      <TD>
 *          <P>ISO-8859-2
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>ISO-8859-2
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>ISO Latin 3</TD>
 *      <TD>
 *          <P>ISO-8859-3
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>ISO-8859-3
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>ISO Latin 4</TD>
 *      <TD>
 *          <P>ISO-8859-4
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>ISO-8859-4
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>ISO Latin Cyrillic</TD>
 *      <TD>
 *          <P>ISO-8859-5
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>ISO-8859-5
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>ISO Latin Arabic</TD>
 *      <TD>
 *          <P>ISO-8859-6
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>ISO-8859-6
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>ISO Latin Greek</TD>
 *      <TD>
 *          <P>ISO-8859-7
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>ISO-8859-7
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>ISO Latin Hebrew</TD>
 *      <TD>
 *          <P>ISO-8859-8
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>ISO-8859-8
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>ISO Latin 5</TD>
 *      <TD>
 *          <P>ISO-8859-9
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>ISO-8859-9
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: US</TD>
 *      <TD>
 *          <P>ebcdic-cp-us
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp037
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Canada</TD>
 *      <TD>
 *          <P>ebcdic-cp-ca
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp037
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Netherlands</TD>
 *      <TD>
 *          <P>ebcdic-cp-nl
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp037
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Denmark</TD>
 *      <TD>
 *          <P>ebcdic-cp-dk
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp277
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Norway</TD>
 *      <TD>
 *          <P>ebcdic-cp-no
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp277
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Finland</TD>
 *      <TD>
 *          <P>ebcdic-cp-fi
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp278
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Sweden</TD>
 *      <TD>
 *          <P>ebcdic-cp-se
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp278
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Italy</TD>
 *      <TD>
 *          <P>ebcdic-cp-it
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp280
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Spain, Latin America</TD>
 *      <TD>
 *          <P>ebcdic-cp-es
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp284
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Great Britain</TD>
 *      <TD>
 *          <P>ebcdic-cp-gb
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp285
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: France</TD>
 *      <TD>
 *          <P>ebcdic-cp-fr
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp297
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Arabic</TD>
 *      <TD>
 *          <P>ebcdic-cp-ar1
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp420
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Hebrew</TD>
 *      <TD>
 *          <P>ebcdic-cp-he
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp424
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Switzerland</TD>
 *      <TD>
 *          <P>ebcdic-cp-ch
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp500
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Roece</TD>
 *      <TD>
 *          <P>ebcdic-cp-roece
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp870
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Yogoslavia</TD>
 *      <TD>
 *          <P>ebcdic-cp-yu
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp870
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Iceland</TD>
 *      <TD>
 *          <P>ebcdic-cp-is
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp871
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>EBCDIC: Urdu</TD>
 *      <TD>
 *          <P>ebcdic-cp-ar2
 *      </TD>
 *      <TD>
 *          <P>IANA
 *      </TD>
 *      <TD>
 *          <P>cp918
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>Chinese for PRC, mixed 1/2 byte</TD>
 *      <TD>
 *          <P>gb2312
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>GB2312
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>Extended Unix Code, packed for Japanese</TD>
 *      <TD>
 *          <P>euc-jp
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>eucjis
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>Japanese: iso-2022-jp</TD>
 *      <TD>
 *          <P>iso-2020-jp
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>JIS
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>Japanese: Shift JIS</TD>
 *      <TD>
 *          <P>Shift_JIS
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>SJIS
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>Chinese: Big5</TD>
 *      <TD>
 *          <P>Big5
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>Big5
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>Extended Unix Code, packed for Korean</TD>
 *      <TD>
 *          <P>euc-kr
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>iso2022kr
 *      </TD>
 *  </TR>
 *  <TR>
 *      <TD>Cyrillic</TD>
 *      <TD>
 *          <P>koi8-r
 *      </TD>
 *      <TD>
 *          <P>MIME
 *      </TD>
 *      <TD>
 *          <P>koi8-r
 *      </TD>
 *  </TR>
 * </TABLE>
 *
 * @author TAMURA Kent &lt;kent@trl.ibm.co.jp&gt;
 *
 * @deprecated Unused. Will be removed in Tomcat 9.
 */
@Deprecated
public class MIME2Java {

    private static final Map<String,String> s_enchash;
    private static final Map<String,String> s_revhash;

    static {
        s_enchash = new HashMap<>();
        //    <preferred MIME name>, <Java encoding name>
        s_enchash.put("UTF-8", "UTF8");
        s_enchash.put("US-ASCII",        "8859_1");    // ?
        s_enchash.put("ISO-8859-1",      "8859_1");
        s_enchash.put("ISO-8859-2",      "8859_2");
        s_enchash.put("ISO-8859-3",      "8859_3");
        s_enchash.put("ISO-8859-4",      "8859_4");
        s_enchash.put("ISO-8859-5",      "8859_5");
        s_enchash.put("ISO-8859-6",      "8859_6");
        s_enchash.put("ISO-8859-7",      "8859_7");
        s_enchash.put("ISO-8859-8",      "8859_8");
        s_enchash.put("ISO-8859-9",      "8859_9");
        s_enchash.put("ISO-2022-JP",     "JIS");
        s_enchash.put("SHIFT_JIS",       "SJIS");
        s_enchash.put("EUC-JP",          "EUCJIS");
        s_enchash.put("GB2312",          "GB2312");
        s_enchash.put("BIG5",            "Big5");
        s_enchash.put("EUC-KR",          "KSC5601");
        s_enchash.put("ISO-2022-KR",     "ISO2022KR");
        s_enchash.put("KOI8-R",          "KOI8_R");

        s_enchash.put("EBCDIC-CP-US",    "CP037");
        s_enchash.put("EBCDIC-CP-CA",    "CP037");
        s_enchash.put("EBCDIC-CP-NL",    "CP037");
        s_enchash.put("EBCDIC-CP-DK",    "CP277");
        s_enchash.put("EBCDIC-CP-NO",    "CP277");
        s_enchash.put("EBCDIC-CP-FI",    "CP278");
        s_enchash.put("EBCDIC-CP-SE",    "CP278");
        s_enchash.put("EBCDIC-CP-IT",    "CP280");
        s_enchash.put("EBCDIC-CP-ES",    "CP284");
        s_enchash.put("EBCDIC-CP-GB",    "CP285");
        s_enchash.put("EBCDIC-CP-FR",    "CP297");
        s_enchash.put("EBCDIC-CP-AR1",   "CP420");
        s_enchash.put("EBCDIC-CP-HE",    "CP424");
        s_enchash.put("EBCDIC-CP-CH",    "CP500");
        s_enchash.put("EBCDIC-CP-ROECE", "CP870");
        s_enchash.put("EBCDIC-CP-YU",    "CP870");
        s_enchash.put("EBCDIC-CP-IS",    "CP871");
        s_enchash.put("EBCDIC-CP-AR2",   "CP918");

        // j:CNS11643 -> EUC-TW?
        // ISO-2022-CN? ISO-2022-CN-EXT?

        s_revhash = new HashMap<>();
        //    <Java encoding name>, <preferred MIME name>
        s_revhash.put("UTF8", "UTF-8");
        //s_revhash.put("8859_1", "US-ASCII");    // ?
        s_revhash.put("8859_1", "ISO-8859-1");
        s_revhash.put("8859_2", "ISO-8859-2");
        s_revhash.put("8859_3", "ISO-8859-3");
        s_revhash.put("8859_4", "ISO-8859-4");
        s_revhash.put("8859_5", "ISO-8859-5");
        s_revhash.put("8859_6", "ISO-8859-6");
        s_revhash.put("8859_7", "ISO-8859-7");
        s_revhash.put("8859_8", "ISO-8859-8");
        s_revhash.put("8859_9", "ISO-8859-9");
        s_revhash.put("JIS", "ISO-2022-JP");
        s_revhash.put("SJIS", "Shift_JIS");
        s_revhash.put("EUCJIS", "EUC-JP");
        s_revhash.put("GB2312", "GB2312");
        s_revhash.put("BIG5", "Big5");
        s_revhash.put("KSC5601", "EUC-KR");
        s_revhash.put("ISO2022KR", "ISO-2022-KR");
        s_revhash.put("KOI8_R", "KOI8-R");

        s_revhash.put("CP037", "EBCDIC-CP-US");
        s_revhash.put("CP037", "EBCDIC-CP-CA");
        s_revhash.put("CP037", "EBCDIC-CP-NL");
        s_revhash.put("CP277", "EBCDIC-CP-DK");
        s_revhash.put("CP277", "EBCDIC-CP-NO");
        s_revhash.put("CP278", "EBCDIC-CP-FI");
        s_revhash.put("CP278", "EBCDIC-CP-SE");
        s_revhash.put("CP280", "EBCDIC-CP-IT");
        s_revhash.put("CP284", "EBCDIC-CP-ES");
        s_revhash.put("CP285", "EBCDIC-CP-GB");
        s_revhash.put("CP297", "EBCDIC-CP-FR");
        s_revhash.put("CP420", "EBCDIC-CP-AR1");
        s_revhash.put("CP424", "EBCDIC-CP-HE");
        s_revhash.put("CP500", "EBCDIC-CP-CH");
        s_revhash.put("CP870", "EBCDIC-CP-ROECE");
        s_revhash.put("CP870", "EBCDIC-CP-YU");
        s_revhash.put("CP871", "EBCDIC-CP-IS");
        s_revhash.put("CP918", "EBCDIC-CP-AR2");
    }

    private MIME2Java() {
    }

    /**
     * Convert a MIME charset name, also known as an XML encoding name, to a Java encoding name.
     * @param   mimeCharsetName Case insensitive MIME charset name: <code>UTF-8, US-ASCII, ISO-8859-1,
     *                          ISO-8859-2, ISO-8859-3, ISO-8859-4, ISO-8859-5, ISO-8859-6,
     *                          ISO-8859-7, ISO-8859-8, ISO-8859-9, ISO-2022-JP, Shift_JIS,
     *                          EUC-JP, GB2312, Big5, EUC-KR, ISO-2022-KR, KOI8-R,
     *                          EBCDIC-CP-US, EBCDIC-CP-CA, EBCDIC-CP-NL, EBCDIC-CP-DK,
     *                          EBCDIC-CP-NO, EBCDIC-CP-FI, EBCDIC-CP-SE, EBCDIC-CP-IT,
     *                          EBCDIC-CP-ES, EBCDIC-CP-GB, EBCDIC-CP-FR, EBCDIC-CP-AR1,
     *                          EBCDIC-CP-HE, EBCDIC-CP-CH, EBCDIC-CP-ROECE, EBCDIC-CP-YU,
     *                          EBCDIC-CP-IS and EBCDIC-CP-AR2</code>.
     * @return                  Java encoding name, or <var>null</var> if <var>mimeCharsetName</var>
     *                          is unknown.
     * @see #reverse
     */
    public static String convert(String mimeCharsetName) {
        return s_enchash.get(mimeCharsetName.toUpperCase(Locale.ENGLISH));
    }

    /**
     * Convert a Java encoding name to MIME charset name.
     * Available values of <i>encoding</i> are "UTF8", "8859_1", "8859_2", "8859_3", "8859_4",
     * "8859_5", "8859_6", "8859_7", "8859_8", "8859_9", "JIS", "SJIS", "EUCJIS",
     * "GB2312", "BIG5", "KSC5601", "ISO2022KR",  "KOI8_R", "CP037", "CP277", "CP278",
     * "CP280", "CP284", "CP285", "CP297", "CP420", "CP424", "CP500", "CP870", "CP871" and "CP918".
     * @param   encoding    Case insensitive Java encoding name: <code>UTF8, 8859_1, 8859_2, 8859_3,
     *                      8859_4, 8859_5, 8859_6, 8859_7, 8859_8, 8859_9, JIS, SJIS, EUCJIS,
     *                      GB2312, BIG5, KSC5601, ISO2022KR, KOI8_R, CP037, CP277, CP278,
     *                      CP280, CP284, CP285, CP297, CP420, CP424, CP500, CP870, CP871
     *                      and CP918</code>.
     * @return              MIME charset name, or <var>null</var> if <var>encoding</var> is unknown.
     * @see #convert
     */
    public static String reverse(String encoding) {
        return s_revhash.get(encoding.toUpperCase(Locale.ENGLISH));
    }
}

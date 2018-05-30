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

public class TestAcceptLanguage {

    private static final Locale L_EN = Locale.forLanguageTag("en");
    private static final Locale L_EN_GB = Locale.forLanguageTag("en-gb");
    private static final Locale L_FR = Locale.forLanguageTag("fr");
    private static final double Q1_000 = 1;
    private static final double Q0_500 = 0.5;
    private static final double Q0_050 = 0.05;

    @Test
    public void testSingle01() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle02() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle03() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle04() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb; "));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle05() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=1"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle06() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb; q=1"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle07() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb; q= 1"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle08() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb; q = 1"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle09() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb; q = 1 "));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle10() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb; q =\t1"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle11() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb; q =1\t"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle12() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb; q =\t1\t"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle13() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=0.5"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle14() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=0.50"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle15() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=0.500"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle16() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=0.5009"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testSingle17() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;,"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }



    @Test
    public void testMalformed01() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;x=1,en-gb;q=0.5"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed02() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=a,en-gb;q=0.5"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed03() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=0.5a,en-gb;q=0.5"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed04() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=0.05a,en-gb;q=0.5"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed05() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=0.005a,en-gb;q=0.5"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed06() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=0.00005a,en-gb;q=0.5"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed07() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en,,"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed08() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader(",en,"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed09() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader(",,en"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed10() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en;q"));

        Assert.assertEquals(0, actual.size());
    }

    @Test
    public void testMalformed11() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=1a0"));

        Assert.assertEquals(0, actual.size());
    }

    @Test
    public void testMalformed12() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=1.a0"));

        Assert.assertEquals(0, actual.size());
    }

    @Test
    public void testMalformed13() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=1.0a0"));

        Assert.assertEquals(0, actual.size());
    }

    @Test
    public void testMalformed14() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=1.1"));

        Assert.assertEquals(0, actual.size());
    }

    @Test
    public void testMalformed15() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en-gb;q=1a0,en-gb;q=0.5"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(L_EN_GB, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }


    @Test
    public void testMultiple01() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en,fr"));

        Assert.assertEquals(2, actual.size());
        Assert.assertEquals(L_EN, actual.get(0).getLocale());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
        Assert.assertEquals(L_FR, actual.get(1).getLocale());
        Assert.assertEquals(Q1_000, actual.get(1).getQuality(), 0.0001);
    }

    @Test
    public void testMultiple02() throws Exception {
        List<AcceptLanguage> actual = AcceptLanguage.parse(new StringReader("en; q= 0.05,fr;q=0.5"));

        Assert.assertEquals(2, actual.size());
        Assert.assertEquals(L_EN, actual.get(0).getLocale());
        Assert.assertEquals(Q0_050, actual.get(0).getQuality(), 0.0001);
        Assert.assertEquals(L_FR, actual.get(1).getLocale());
        Assert.assertEquals(Q0_500, actual.get(1).getQuality(), 0.0001);
    }


    @Test
    public void bug56848() throws Exception {
        List<AcceptLanguage> actual =
                AcceptLanguage.parse(new StringReader("zh-hant-CN;q=0.5,zh-hans-TW;q=0.05"));

        Assert.assertEquals(2, actual.size());

        Locale.Builder b = new Locale.Builder();
        b.setLanguage("zh").setRegion("CN").setScript("hant");
        Locale l1 = b.build();

        b.clear().setLanguage("zh").setRegion("TW").setScript("hans");
        Locale l2 = b.build();

        Assert.assertEquals(l1, actual.get(0).getLocale());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
        Assert.assertEquals(l2, actual.get(1).getLocale());
        Assert.assertEquals(Q0_050, actual.get(1).getQuality(), 0.0001);
    }
}

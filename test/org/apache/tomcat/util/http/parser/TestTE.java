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

import org.junit.Assert;
import org.junit.Test;

public class TestTE {

    private static final String GZIP = "gzip";
    private static final double Q1_000 = 1;
    private static final double Q0_500 = 0.5;
    private static final double Q0_050 = 0.05;

    @Test
    public void testSimpleTE() throws Exception {
        List<TE> actual = TE.parse(new StringReader("gzip"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals("gzip", actual.get(0).getEncoding());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testComplexTE1() throws Exception {
        List<TE> actual = TE.parse(new StringReader("gzip;q=0.5"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals("gzip", actual.get(0).getEncoding());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testComplexTE2() throws Exception {
        List<TE> actual = TE.parse(new StringReader("gzip;q=0.5, something;q=0.05"));

        Assert.assertEquals(2, actual.size());
        Assert.assertEquals("gzip", actual.get(0).getEncoding());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testComplexTE3() throws Exception {
        List<TE> actual = TE.parse(new StringReader("gzip; arg1= val1; arg2 = val2 ; q =0.05"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(GZIP, actual.get(0).getEncoding());
        Assert.assertEquals(Q0_050, actual.get(0).getQuality(), 0.0001);
        Assert.assertEquals("val1", actual.get(0).getParameters().get("arg1"));
        Assert.assertEquals("val2", actual.get(0).getParameters().get("arg2"));
    }

    @Test
    public void testMalformed01() throws Exception {
        List<TE> actual = TE.parse(new StringReader("gzip;q=a,gzip;q=0.5"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals(GZIP, actual.get(0).getEncoding());
        Assert.assertEquals(Q0_500, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed02() throws Exception {
        List<TE> actual = TE.parse(new StringReader("gzip,,"));

        Assert.assertEquals(1, actual.size());
        Assert.assertEquals("gzip", actual.get(0).getEncoding());
        Assert.assertEquals(Q1_000, actual.get(0).getQuality(), 0.0001);
    }

    @Test
    public void testMalformed03() throws Exception {
        List<TE> actual = TE.parse(new StringReader("gzip;q=1.0a0"));

        Assert.assertEquals(0, actual.size());
    }

}

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

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestAcceptEncoding {

    private static final double DELTA = 0.0001;


    @Test
    public void testSimple() throws Exception {
        List<AcceptEncoding> actual = parse("gzip");

        Assert.assertEquals(1, actual.size());
        assertEntry(actual.get(0), "gzip", 1);
    }


    @Test
    public void testEmpty() throws Exception {
        Assert.assertTrue(parse("").isEmpty());
    }


    @Test
    public void testMultiple() throws Exception {
        List<AcceptEncoding> actual = parse("gzip;q=0.5, br, *;q=0.1");

        Assert.assertEquals(3, actual.size());
        assertEntry(actual.get(0), "gzip", 0.5);
        assertEntry(actual.get(1), "br", 1);
        assertEntry(actual.get(2), "*", 0.1);
    }


    @Test
    public void testWeightWithThreeDecimalPlaces() throws Exception {
        List<AcceptEncoding> actual = parse("gzip;q=0.123");

        Assert.assertEquals(1, actual.size());
        assertEntry(actual.get(0), "gzip", 0.123);
    }


    @Test
    public void testWeightOfOne() throws Exception {
        List<AcceptEncoding> actual = parse("gzip;q=1, br;q=1.0, deflate;q=1.000");

        Assert.assertEquals(3, actual.size());
        assertEntry(actual.get(0), "gzip", 1);
        assertEntry(actual.get(1), "br", 1);
        assertEntry(actual.get(2), "deflate", 1);
    }


    /*
     * An explicit weight of zero means "not acceptable". The entry must be returned since it may exclude a coding that
     * would otherwise be matched by "*".
     */
    @Test
    public void testExplicitZeroWeightIsReturned() throws Exception {
        List<AcceptEncoding> actual = parse("gzip;q=0");

        Assert.assertEquals(1, actual.size());
        assertEntry(actual.get(0), "gzip", 0);
    }


    @Test
    public void testExplicitZeroWeightVariants() throws Exception {
        List<AcceptEncoding> actual = parse("gzip;q=0.0, br;q=0.00, deflate;q=0.000");

        Assert.assertEquals(3, actual.size());
        assertEntry(actual.get(0), "gzip", 0);
        assertEntry(actual.get(1), "br", 0);
        assertEntry(actual.get(2), "deflate", 0);
    }


    @Test
    public void testExplicitZeroWeightWithWildcard() throws Exception {
        List<AcceptEncoding> actual = parse("*;q=0.5, br;q=0");

        Assert.assertEquals(2, actual.size());
        assertEntry(actual.get(0), "*", 0.5);
        assertEntry(actual.get(1), "br", 0);
    }


    @Test
    public void testWildcardWithZeroWeight() throws Exception {
        List<AcceptEncoding> actual = parse("*;q=0, gzip");

        Assert.assertEquals(2, actual.size());
        assertEntry(actual.get(0), "*", 0);
        assertEntry(actual.get(1), "gzip", 1);
    }


    /*
     * Malformed weights are ignored. The entry is dropped rather than returned with a weight of zero so it can be
     * distinguished from an explicit exclusion.
     */
    @Test
    public void testMalformedWeightIsDropped() throws Exception {
        List<AcceptEncoding> actual = parse("gzip;q=abc");

        Assert.assertTrue(actual.isEmpty());
    }


    @Test
    public void testMalformedWeights() throws Exception {
        String[] malformed = new String[] { "gzip;q=abc", "gzip;q=", "gzip;q=-1", "gzip;q=2", "gzip;q=5",
                "gzip;q=1.5", "gzip;q=1.001", "gzip;q=Infinity", "gzip;q=NaN", "gzip;q=1e0", "gzip;q=0x1p0",
                "gzip;q=1d", "gzip;q=1f", "gzip;q=.5", "gzip;q=0.5x", "gzip;foo=bar", "gzip;q", "gzip;x=0.5" };

        for (String header : malformed) {
            Assert.assertTrue(header, parse(header).isEmpty());
        }
    }


    @Test
    public void testParsingResumesAfterMalformedWeight() throws Exception {
        List<AcceptEncoding> actual = parse("br;q=abc, gzip;q=0.5, deflate;q=7, identity");

        Assert.assertEquals(2, actual.size());
        assertEntry(actual.get(0), "gzip", 0.5);
        assertEntry(actual.get(1), "identity", 1);
    }


    @Test
    public void testMalformedWeightBetweenValidEntries() throws Exception {
        List<AcceptEncoding> actual = parse("gzip;q=0.9, br;q=Infinity, deflate;q=0");

        Assert.assertEquals(2, actual.size());
        assertEntry(actual.get(0), "gzip", 0.9);
        assertEntry(actual.get(1), "deflate", 0);
    }


    @Test
    public void testInvalidTokenIsSkipped() throws Exception {
        List<AcceptEncoding> actual = parse("<>, gzip");

        Assert.assertEquals(1, actual.size());
        assertEntry(actual.get(0), "gzip", 1);
    }


    @Test
    public void testWhitespaceAroundWeight() throws Exception {
        List<AcceptEncoding> actual = parse("gzip ; q=0.5 , br ;q=0");

        Assert.assertEquals(2, actual.size());
        assertEntry(actual.get(0), "gzip", 0.5);
        assertEntry(actual.get(1), "br", 0);
    }


    @Test
    public void testReadWeightNoWeight() throws Exception {
        Assert.assertEquals(1, HttpParser.readWeight(new StringReader(""), ','), DELTA);
        Assert.assertEquals(1, HttpParser.readWeight(new StringReader(","), ','), DELTA);
    }


    @Test
    public void testReadWeightValid() throws Exception {
        Assert.assertEquals(0, HttpParser.readWeight(new StringReader("q=0"), ','), DELTA);
        Assert.assertEquals(0.5, HttpParser.readWeight(new StringReader("q=0.5"), ','), DELTA);
        Assert.assertEquals(1, HttpParser.readWeight(new StringReader("q=1"), ','), DELTA);
    }


    /*
     * Malformed weights must be reported as -1 so they are distinguishable from an explicit weight of zero.
     */
    @Test
    public void testReadWeightMalformed() throws Exception {
        String[] malformed = new String[] { "x=0.5", "q", "q:0.5", "q=abc", "q=2", "q=1.5", "q=1.001", "q=0.5x" };

        for (String weight : malformed) {
            Assert.assertEquals(weight, -1, HttpParser.readWeight(new StringReader(weight), ','), DELTA);
        }
    }


    private static List<AcceptEncoding> parse(String header) throws IOException {
        return AcceptEncoding.parse(new StringReader(header));
    }


    private static void assertEntry(AcceptEncoding entry, String encoding, double quality) {
        Assert.assertEquals(encoding, entry.getEncoding());
        Assert.assertEquals(quality, entry.getQuality(), DELTA);
    }
}

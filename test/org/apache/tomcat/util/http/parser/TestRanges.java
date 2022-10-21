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
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.http.parser.Ranges.Entry;

public class TestRanges {

    @Test
    public void testCaseInsensitive() throws Exception {
        Ranges lower = parse("bytes=1-10");
        Ranges upper = parse("Bytes=1-10");

        compareRanges(lower, upper);
    }


    @Test
    public void testInvalid01() throws Exception {
        doTestInvalid("");
    }


    @Test
    public void testInvalid02() throws Exception {
        doTestInvalid("=1-10");
    }


    @Test
    public void testInvalid03() throws Exception {
        doTestInvalid("bytes");
    }


    @Test
    public void testInvalid04() throws Exception {
        doTestInvalid("bytes=1");
    }


    @Test
    public void testInvalid05() throws Exception {
        doTestInvalid("bytes=-");
    }


    @Test
    public void testInvalid06() throws Exception {
        doTestInvalid("bytes=1-10 a");
    }


    @Test
    public void testValid01() throws Exception {
        Ranges r = parse("bytes=1-10,21-30");
        Assert.assertEquals("bytes", r.getUnits());
        List<Entry> l = r.getEntries();
        Assert.assertEquals(2, l.size());
        Entry e1 = l.get(0);
        Assert.assertEquals(1, e1.getStart());
        Assert.assertEquals(10, e1.getEnd());
        Entry e2 = l.get(1);
        Assert.assertEquals(21, e2.getStart());
        Assert.assertEquals(30, e2.getEnd());
    }


    @Test
    public void testValid02() throws Exception {
        Ranges r = parse("bytes=-20");
        List<Entry> l = r.getEntries();
        Assert.assertEquals(1, l.size());
        Entry e1 = l.get(0);
        Assert.assertEquals(-1, e1.getStart());
        Assert.assertEquals(20, e1.getEnd());
    }


    @Test
    public void testNullUnits() throws Exception {
        Ranges r = new Ranges(null, new ArrayList<>());
        Assert.assertNotNull(r);
        Assert.assertNull(r.getUnits());
    }


    @Test
    public void testValid03() throws Exception {
        Ranges r = parse("bytes=21-");
        List<Entry> l = r.getEntries();
        Assert.assertEquals(1, l.size());
        Entry e1 = l.get(0);
        Assert.assertEquals(21, e1.getStart());
        Assert.assertEquals(-1, e1.getEnd());
    }


    private void doTestInvalid(String s) throws IOException {
        Ranges r = parse(s);
        Assert.assertNull(r);
    }


    private Ranges parse(String s) throws IOException {
        return Ranges.parse(new StringReader(s));
    }


    private void compareRanges(Ranges r1, Ranges r2) {
        Assert.assertEquals(r1.getUnits(), r2.getUnits());

        List<Entry> l1 = r1.getEntries();
        List<Entry> l2 = r2.getEntries();

        Assert.assertEquals(l1.size(), l2.size());
        for (int i = 0; i < l1.size(); i++) {
            Entry e1 = l1.get(i);
            Entry e2 = l2.get(i);

            Assert.assertEquals(e1.getStart(), e2.getStart());
            Assert.assertEquals(e1.getEnd(), e2.getEnd());
        }
    }
}

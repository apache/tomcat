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
package org.apache.tomcat.util.descriptor.web;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test case for {@link WebXml} fragment ordering.
 */
public class TestWebXmlOrdering {
    private WebXml app;
    private WebXml a;
    private WebXml b;
    private WebXml c;
    private WebXml d;
    private WebXml e;
    private WebXml f;
    private Map<String,WebXml> fragments;
    private int posA;
    private int posB;
    private int posC;
    private int posD;
    private int posE;
    private int posF;

    @Before
    public void setUp() {
        app = new WebXml();
        a = new WebXml();
        a.setName("a");
        b = new WebXml();
        b.setName("b");
        c = new WebXml();
        c.setName("c");
        d = new WebXml();
        d.setName("d");
        e = new WebXml();
        e.setName("e");
        f = new WebXml();
        f.setName("f");
        // Control the input order
        fragments = new LinkedHashMap<>();
        fragments.put("a",a);
        fragments.put("b",b);
        fragments.put("c",c);
        fragments.put("d",d);
        fragments.put("e",e);
        fragments.put("f",f);
    }

    @Test
    public void testOrderWebFragmentsAbsolute() {
        app.addAbsoluteOrdering("c");
        app.addAbsoluteOrdering("a");
        app.addAbsoluteOrdering("b");
        app.addAbsoluteOrdering("e");
        app.addAbsoluteOrdering("d");

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments, null);

        Iterator<WebXml> iter = ordered.iterator();
        Assert.assertEquals(c,iter.next());
        Assert.assertEquals(a,iter.next());
        Assert.assertEquals(b,iter.next());
        Assert.assertEquals(e,iter.next());
        Assert.assertEquals(d,iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void testOrderWebFragmentsAbsolutePartial() {
        app.addAbsoluteOrdering("c");
        app.addAbsoluteOrdering("a");

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments, null);

        Iterator<WebXml> iter = ordered.iterator();
        Assert.assertEquals(c,iter.next());
        Assert.assertEquals(a,iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void testOrderWebFragmentsAbsoluteOthersStart() {
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);
        app.addAbsoluteOrdering("b");
        app.addAbsoluteOrdering("d");

        Set<WebXml> others = new HashSet<>();
        others.add(a);
        others.add(c);
        others.add(e);
        others.add(f);

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments, null);

        Iterator<WebXml> iter = ordered.iterator();
        while (others.size() > 0) {
            WebXml o = iter.next();
            Assert.assertTrue(others.contains(o));
            others.remove(o);
        }
        Assert.assertEquals(b,iter.next());
        Assert.assertEquals(d,iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void testOrderWebFragmentsAbsoluteOthersMiddle() {
        app.addAbsoluteOrdering("b");
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);
        app.addAbsoluteOrdering("d");

        Set<WebXml> others = new HashSet<>();
        others.add(a);
        others.add(c);
        others.add(e);
        others.add(f);

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments, null);

        Iterator<WebXml> iter = ordered.iterator();
        Assert.assertEquals(b,iter.next());

        while (others.size() > 0) {
            WebXml o = iter.next();
            Assert.assertTrue(others.contains(o));
            others.remove(o);
        }
        Assert.assertEquals(d,iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    @Test
    public void testWebFragmentsAbsoluteWrongFragmentName() {
        app.addAbsoluteOrdering("a");
        app.addAbsoluteOrdering("z");
        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments, null);
        Assert.assertEquals(1,ordered.size());
        Assert.assertEquals(fragments.get("a"),ordered.toArray()[0]);
    }

    @Test
    public void testOrderWebFragmentsAbsoluteOthersEnd() {
        app.addAbsoluteOrdering("b");
        app.addAbsoluteOrdering("d");
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);

        Set<WebXml> others = new HashSet<>();
        others.add(a);
        others.add(c);
        others.add(e);
        others.add(f);

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments, null);

        Iterator<WebXml> iter = ordered.iterator();
        Assert.assertEquals(b,iter.next());
        Assert.assertEquals(d,iter.next());

        while (others.size() > 0) {
            WebXml o = iter.next();
            Assert.assertTrue(others.contains(o));
            others.remove(o);
        }
        Assert.assertFalse(iter.hasNext());
    }

    private void doRelativeOrderingTest(RelativeOrderingTestRunner runner) {
        // Confirm we have all 720 possible input orders
        // Set<String> orders = new HashSet<>();

        // Test all possible input orders since some bugs were discovered that
        // depended on input order
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 5; j++) {
                for (int k = 0; k < 4; k++) {
                    for (int l = 0; l < 3; l++) {
                        for (int m = 0; m < 2; m++) {
                            setUp();
                            runner.init();
                            ArrayList<WebXml> source = new ArrayList<>(fragments.values());
                            Map<String,WebXml> input =
                                    new LinkedHashMap<>();

                            WebXml one = source.remove(i);
                            input.put(one.getName(), one);

                            WebXml two = source.remove(j);
                            input.put(two.getName(), two);

                            WebXml three = source.remove(k);
                            input.put(three.getName(), three);

                            WebXml four = source.remove(l);
                            input.put(four.getName(), four);

                            WebXml five = source.remove(m);
                            input.put(five.getName(), five);

                            WebXml six = source.remove(0);
                            input.put(six.getName(), six);

                            /*
                            String order = one.getName() + two.getName() +
                                    three.getName() + four.getName() +
                                    five.getName() + six.getName();
                            orders.add(order);
                            */

                            Set<WebXml> ordered =
                                    WebXml.orderWebFragments(app, input, null);
                            populatePositions(ordered);

                            runner.validate(getOrder(ordered));
                        }
                    }
                }
            }
        }
        // System.out.println(orders.size());
    }

    private String getOrder(Set<WebXml> ordered) {
        StringBuilder sb = new StringBuilder(ordered.size());
        for (WebXml webXml : ordered) {
            sb.append(webXml.getName());
        }
        return sb.toString();
    }

    private void populatePositions(Set<WebXml> ordered) {
        List<WebXml> indexed = new ArrayList<>(ordered);

        posA = indexed.indexOf(a);
        posB = indexed.indexOf(b);
        posC = indexed.indexOf(c);
        posD = indexed.indexOf(d);
        posE = indexed.indexOf(e);
        posF = indexed.indexOf(f);
    }

    @Test
    public void testOrderWebFragmentsRelative1() {
        // First example from servlet spec
        doRelativeOrderingTest(new RelativeTestRunner1());
    }

    @Test
    public void testOrderWebFragmentsRelative2() {
        // Second example - use fragment a for no-id fragment
        doRelativeOrderingTest(new RelativeTestRunner2());
    }

    @Test
    public void testOrderWebFragmentsRelative3() {
        // Third example from spec with e & f added
        doRelativeOrderingTest(new RelativeTestRunner3());
    }

    @Test
    public void testOrderWebFragmentsRelative4Bug54068() {
        // Simple sequence that failed for some inputs
        doRelativeOrderingTest(new RelativeTestRunner4());
    }

    @Test
    public void testOrderWebFragmentsRelative5Bug54068() {
        // Simple sequence that failed for some inputs
        doRelativeOrderingTest(new RelativeTestRunner5());
    }

    @Test
    public void testOrderWebFragmentsRelative6Bug54068() {
        // Simple sequence that failed for some inputs
        doRelativeOrderingTest(new RelativeTestRunner6());
    }

    @Test
    public void testOrderWebFragmentsRelative7() {
        // Reference loop (but not circular dependencies)
        doRelativeOrderingTest(new RelativeTestRunner7());
    }

    @Test
    public void testOrderWebFragmentsRelative8() {
        // More complex, trying to break the algorithm
        doRelativeOrderingTest(new RelativeTestRunner8());
    }

    @Test
    public void testOrderWebFragmentsRelative9() {
        // Variation on bug 54068
        doRelativeOrderingTest(new RelativeTestRunner9());
    }

    @Test
    public void testOrderWebFragmentsRelative10() {
        // Variation on bug 54068
        doRelativeOrderingTest(new RelativeTestRunner10());
    }

    @Test
    public void testOrderWebFragmentsRelative11() {
        // Test references to non-existent fragments
        doRelativeOrderingTest(new RelativeTestRunner11());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testOrderWebFragmentsrelativeCircular1() {
        a.addBeforeOrdering("b");
        b.addBeforeOrdering("a");

        WebXml.orderWebFragments(app, fragments, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testOrderWebFragmentsrelativeCircular2() {
        a.addBeforeOrderingOthers();
        b.addAfterOrderingOthers();
        c.addBeforeOrdering("a");
        c.addAfterOrdering("b");

        WebXml.orderWebFragments(app, fragments, null);
    }

    private interface RelativeOrderingTestRunner {
        void init();
        void validate(String order);
    }

    private class RelativeTestRunner1 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            a.addAfterOrderingOthers();
            a.addAfterOrdering("c");
            b.addBeforeOrderingOthers();
            c.addAfterOrderingOthers();
            f.addBeforeOrderingOthers();
            f.addBeforeOrdering("b");
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            //a.addAfterOrderingOthers();
            Assert.assertTrue(order, posA > posB);
            Assert.assertTrue(order, posA > posC);
            Assert.assertTrue(order, posA > posD);
            Assert.assertTrue(order, posA > posE);
            Assert.assertTrue(order, posA > posF);

            // a.addAfterOrdering("c");
            Assert.assertTrue(order, posA > posC);

            // b.addBeforeOrderingOthers();
            Assert.assertTrue(order, posB < posC);

            // c.addAfterOrderingOthers();
            Assert.assertTrue(order, posC > posB);
            Assert.assertTrue(order, posC > posD);
            Assert.assertTrue(order, posC > posE);
            Assert.assertTrue(order, posC > posF);

            // f.addBeforeOrderingOthers();
            Assert.assertTrue(order, posF < posA);
            Assert.assertTrue(order, posF < posB);
            Assert.assertTrue(order, posF < posC);
            Assert.assertTrue(order, posF < posD);
            Assert.assertTrue(order, posF < posE);

            // f.addBeforeOrdering("b");
            Assert.assertTrue(order, posF < posB);
        }
    }

    private class RelativeTestRunner2 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            a.addAfterOrderingOthers();
            a.addBeforeOrdering("c");
            b.addBeforeOrderingOthers();
            d.addAfterOrderingOthers();
            e.addBeforeOrderingOthers();
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            // a.addAfterOrderingOthers();
            Assert.assertTrue(order, posA > posB);
            Assert.assertTrue(order, posA > posE);
            Assert.assertTrue(order, posA > posF);

            // a.addBeforeOrdering("c");
            Assert.assertTrue(order, posC > posA);
            Assert.assertTrue(order, posC > posB);
            Assert.assertTrue(order, posC > posE);
            Assert.assertTrue(order, posC > posF);

            // b.addBeforeOrderingOthers();
            Assert.assertTrue(order, posB < posA);
            Assert.assertTrue(order, posB < posC);
            Assert.assertTrue(order, posB < posD);
            Assert.assertTrue(order, posB < posF);

            // d.addAfterOrderingOthers();
            Assert.assertTrue(order, posD > posB);
            Assert.assertTrue(order, posD > posE);
            Assert.assertTrue(order, posD > posF);

            // e.addBeforeOrderingOthers();
            Assert.assertTrue(order, posE < posA);
            Assert.assertTrue(order, posE < posC);
            Assert.assertTrue(order, posE < posD);
            Assert.assertTrue(order, posE < posF);
        }
    }

    private class RelativeTestRunner3 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            a.addAfterOrdering("b");
            c.addBeforeOrderingOthers();
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            // a.addAfterOrdering("b");
            Assert.assertTrue(order, posA > posB);

            // c.addBeforeOrderingOthers();
            Assert.assertTrue(order, posC < posA);
            Assert.assertTrue(order, posC < posB);
            Assert.assertTrue(order, posC < posD);
            Assert.assertTrue(order, posC < posE);
            Assert.assertTrue(order, posC < posF);
        }
    }

    private class RelativeTestRunner4 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            b.addAfterOrdering("a");
            c.addAfterOrdering("b");
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            // b.addAfterOrdering("a");
            Assert.assertTrue(order, posB > posA);

            // c.addAfterOrdering("b");
            Assert.assertTrue(order, posC > posB);
        }
    }

    private class RelativeTestRunner5 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            b.addBeforeOrdering("a");
            c.addBeforeOrdering("b");
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            // b.addBeforeOrdering("a");
            Assert.assertTrue(order, posB < posA);

            // c.addBeforeOrdering("b");
            Assert.assertTrue(order, posC < posB);
        }
    }

    private class RelativeTestRunner6 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            b.addBeforeOrdering("a");
            b.addAfterOrdering("c");
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            // b.addBeforeOrdering("a");
            Assert.assertTrue(order, posB < posA);

            //b.addAfterOrdering("c");
            Assert.assertTrue(order, posB > posC);
        }
    }

    private class RelativeTestRunner7 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            b.addBeforeOrdering("a");
            c.addBeforeOrdering("b");
            a.addAfterOrdering("c");
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            // b.addBeforeOrdering("a");
            Assert.assertTrue(order, posB < posA);

            // c.addBeforeOrdering("b");
            Assert.assertTrue(order, posC < posB);

            // a.addAfterOrdering("c");
            Assert.assertTrue(order, posA > posC);
        }
    }

    private class RelativeTestRunner8 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            a.addBeforeOrderingOthers();
            a.addBeforeOrdering("b");
            b.addBeforeOrderingOthers();
            c.addAfterOrdering("b");
            d.addAfterOrdering("c");
            e.addAfterOrderingOthers();
            f.addAfterOrderingOthers();
            f.addAfterOrdering("e");
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            // a.addBeforeOrderingOthers();
            Assert.assertTrue(order, posA < posB);
            Assert.assertTrue(order, posA < posC);
            Assert.assertTrue(order, posA < posD);
            Assert.assertTrue(order, posA < posE);
            Assert.assertTrue(order, posA < posF);

            // a.addBeforeOrdering("b");
            Assert.assertTrue(order, posA < posB);

            // b.addBeforeOrderingOthers();
            Assert.assertTrue(order, posB < posC);
            Assert.assertTrue(order, posB < posD);
            Assert.assertTrue(order, posB < posE);
            Assert.assertTrue(order, posB < posF);

            // c.addAfterOrdering("b");
            Assert.assertTrue(order, posC > posB);

            // d.addAfterOrdering("c");
            Assert.assertTrue(order, posD > posC);

            // e.addAfterOrderingOthers();
            Assert.assertTrue(order, posE > posA);
            Assert.assertTrue(order, posE > posB);
            Assert.assertTrue(order, posE > posC);
            Assert.assertTrue(order, posE > posD);

            // f.addAfterOrderingOthers();
            Assert.assertTrue(order, posF > posA);
            Assert.assertTrue(order, posF > posB);
            Assert.assertTrue(order, posF > posC);
            Assert.assertTrue(order, posF > posD);
            Assert.assertTrue(order, posF > posE);

            // f.addAfterOrdering("e");
            Assert.assertTrue(order, posF > posE);
        }
    }

    private class RelativeTestRunner9 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            a.addBeforeOrderingOthers();
            b.addBeforeOrdering("a");
            c.addBeforeOrdering("b");
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            // a.addBeforeOrderingOthers();
            Assert.assertTrue(order, posA < posD);
            Assert.assertTrue(order, posA < posE);
            Assert.assertTrue(order, posA < posF);

            // b.addBeforeOrdering("a");
            Assert.assertTrue(order, posB < posA);

            // c.addBeforeOrdering("b");
            Assert.assertTrue(order, posC < posB);
        }
    }

    private class RelativeTestRunner10 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            a.addAfterOrderingOthers();
            b.addAfterOrdering("a");
            c.addAfterOrdering("b");
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            // a.addAfterOrderingOthers();
            Assert.assertTrue(order, posA > posD);
            Assert.assertTrue(order, posA > posE);
            Assert.assertTrue(order, posA > posF);

            // b.addAfterOrdering("a");
            Assert.assertTrue(order, posB > posA);

            // c.addAfterOrdering("b");
            Assert.assertTrue(order, posC > posB);
        }
    }

    private class RelativeTestRunner11 implements RelativeOrderingTestRunner {

        @Override
        public void init() {
            a.addAfterOrdering("b");
            b.addAfterOrdering("z");
            b.addBeforeOrdering("y");
        }

        @Override
        public void validate(String order) {
            // There is some duplication in the tests below - it is easier to
            // check the tests are complete this way.

            // a.addAfterOrdering("b");
            Assert.assertTrue(order, posA > posB);
        }
    }
}

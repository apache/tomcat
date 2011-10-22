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

package org.apache.catalina.deploy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Before
    public void setUp() throws Exception {
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
        fragments = new HashMap<String,WebXml>();
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

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments);

        Iterator<WebXml> iter = ordered.iterator();
        assertEquals(c,iter.next());
        assertEquals(a,iter.next());
        assertEquals(b,iter.next());
        assertEquals(e,iter.next());
        assertEquals(d,iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testOrderWebFragmentsAbsolutePartial() {
        app.addAbsoluteOrdering("c");
        app.addAbsoluteOrdering("a");

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments);

        Iterator<WebXml> iter = ordered.iterator();
        assertEquals(c,iter.next());
        assertEquals(a,iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testOrderWebFragmentsAbsoluteOthersStart() {
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);
        app.addAbsoluteOrdering("b");
        app.addAbsoluteOrdering("d");

        Set<WebXml> others = new HashSet<WebXml>();
        others.add(a);
        others.add(c);
        others.add(e);
        others.add(f);

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments);

        Iterator<WebXml> iter = ordered.iterator();
        while (others.size() > 0) {
            WebXml o = iter.next();
            assertTrue(others.contains(o));
            others.remove(o);
        }
        assertEquals(b,iter.next());
        assertEquals(d,iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testOrderWebFragmentsAbsoluteOthersMiddle() {
        app.addAbsoluteOrdering("b");
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);
        app.addAbsoluteOrdering("d");

        Set<WebXml> others = new HashSet<WebXml>();
        others.add(a);
        others.add(c);
        others.add(e);
        others.add(f);

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments);

        Iterator<WebXml> iter = ordered.iterator();
        assertEquals(b,iter.next());

        while (others.size() > 0) {
            WebXml o = iter.next();
            assertTrue(others.contains(o));
            others.remove(o);
        }
        assertEquals(d,iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testWebFragmentsAbsoluteWrongFragmentName() {
        app.addAbsoluteOrdering("a");
        app.addAbsoluteOrdering("z");
        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments);
        assertEquals(1,ordered.size());
        assertEquals(fragments.get("a"),ordered.toArray()[0]);
    }

    @Test
    public void testOrderWebFragmentsAbsoluteOthersEnd() {
        app.addAbsoluteOrdering("b");
        app.addAbsoluteOrdering("d");
        app.addAbsoluteOrdering(WebXml.ORDER_OTHERS);

        Set<WebXml> others = new HashSet<WebXml>();
        others.add(a);
        others.add(c);
        others.add(e);
        others.add(f);

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments);

        Iterator<WebXml> iter = ordered.iterator();
        assertEquals(b,iter.next());
        assertEquals(d,iter.next());

        while (others.size() > 0) {
            WebXml o = iter.next();
            assertTrue(others.contains(o));
            others.remove(o);
        }
        assertFalse(iter.hasNext());
    }

    @Test
    public void testOrderWebFragmentsRelative1() {
        // First example from servlet spec
        a.addAfterOrderingOthers();
        a.addAfterOrdering("c");
        b.addBeforeOrderingOthers();
        c.addAfterOrderingOthers();
        f.addBeforeOrderingOthers();
        f.addBeforeOrdering("b");

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments);

        Iterator<WebXml> iter = ordered.iterator();
        assertEquals(f,iter.next());
        assertEquals(b,iter.next());
        assertEquals(d,iter.next());
        assertEquals(e,iter.next());
        assertEquals(c,iter.next());
        assertEquals(a,iter.next());
    }

    @Test
    public void testOrderWebFragmentsRelative2() {
        // Second example - use fragment a for no-id fragment
        a.addAfterOrderingOthers();
        a.addBeforeOrdering("c");
        b.addBeforeOrderingOthers();
        d.addAfterOrderingOthers();
        e.addBeforeOrderingOthers();

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments);

        Iterator<WebXml> iter = ordered.iterator();
        // A number of orders are possible but the algorithm is deterministic
        // and this order is valid. If this fails after a change to the
        // algorithm, then check to see if the new order is also valid.
        assertEquals(b,iter.next());
        assertEquals(e,iter.next());
        assertEquals(f,iter.next());
        assertEquals(a,iter.next());
        assertEquals(c,iter.next());
        assertEquals(d,iter.next());
    }

    @Test
    public void testOrderWebFragmentsRelative3() {
        // Third example from spec
        a.addAfterOrdering("b");
        c.addBeforeOrderingOthers();
        fragments.remove("e");
        fragments.remove("f");

        Set<WebXml> ordered = WebXml.orderWebFragments(app, fragments);

        Iterator<WebXml> iter = ordered.iterator();
        // A number of orders are possible but the algorithm is deterministic
        // and this order is valid. If this fails after a change to the
        // algorithm, then check to see if the new order is also valid.
        assertEquals(c,iter.next());
        assertEquals(d,iter.next());
        assertEquals(b,iter.next());
        assertEquals(a,iter.next());
    }

    @Test
    public void testOrderWebFragmentsrelativeCircular() {
        a.addBeforeOrdering("b");
        b.addBeforeOrdering("a");

        Exception exception = null;

        try {
            WebXml.orderWebFragments(app, fragments);
        } catch (Exception e1) {
            exception = e1;
        }

        assertTrue(exception instanceof IllegalArgumentException);
    }
}

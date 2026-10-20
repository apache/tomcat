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
package org.apache.jasper.util;

import org.junit.Assert;
import org.junit.Test;


public class TestFastRemovalDequeue {

    @Test
    public void testSinglePushPop() throws Exception {
        FastRemovalDequeue<Object> q = new FastRemovalDequeue<>(2);

        Object o1 = new Object();

        q.push(o1);

        Object r = q.pop();

        Assert.assertEquals(o1, r);
        Assert.assertNull(q.first);
        Assert.assertNull(q.last);
    }


    @Test
    public void testDoublePushPop() throws Exception {
        FastRemovalDequeue<Object> q = new FastRemovalDequeue<>(2);

        Object o1 = new Object();
        Object o2 = new Object();

        q.push(o1);
        q.push(o2);

        Assert.assertEquals(o2, q.first.getContent());
        Assert.assertEquals(o1, q.last.getContent());

        Object r1 = q.pop();

        Assert.assertEquals(o1, r1);
        Assert.assertEquals(o2, q.first.getContent());
        Assert.assertEquals(o2, q.last.getContent());


        Object r2 = q.pop();
        Assert.assertEquals(o2, r2);
        Assert.assertNull(q.first);
        Assert.assertNull(q.last);
    }


    @Test
    public void testSingleUnpopPop() throws Exception {
        FastRemovalDequeue<Object> q = new FastRemovalDequeue<>(2);

        Object o1 = new Object();

        q.unpop(o1);

        Object r = q.pop();

        Assert.assertEquals(o1, r);
        Assert.assertNull(q.first);
        Assert.assertNull(q.last);
    }


    @Test
    public void testDoubleUnpopPop() throws Exception {
        FastRemovalDequeue<Object> q = new FastRemovalDequeue<>(2);

        Object o1 = new Object();
        Object o2 = new Object();

        q.unpop(o1);
        q.unpop(o2);

        Assert.assertEquals(o1, q.first.getContent());
        Assert.assertEquals(o2, q.last.getContent());

        Object r2 = q.pop();

        Assert.assertEquals(o2, r2);
        Assert.assertEquals(o1, q.first.getContent());
        Assert.assertEquals(o1, q.last.getContent());


        Object r1 = q.pop();
        Assert.assertEquals(o1, r1);
        Assert.assertNull(q.first);
        Assert.assertNull(q.last);
    }


    @Test
    public void testSinglePushUnpush() throws Exception {
        FastRemovalDequeue<Object> q = new FastRemovalDequeue<>(2);

        Object o1 = new Object();

        q.push(o1);

        Object r = q.unpush();

        Assert.assertEquals(o1, r);
        Assert.assertNull(q.first);
        Assert.assertNull(q.last);
    }


    @Test
    public void testDoublePushUnpush() throws Exception {
        FastRemovalDequeue<Object> q = new FastRemovalDequeue<>(2);

        Object o1 = new Object();
        Object o2 = new Object();

        q.push(o1);
        q.push(o2);

        Assert.assertEquals(o2, q.first.getContent());
        Assert.assertEquals(o1, q.last.getContent());

        Object r2 = q.unpush();

        Assert.assertEquals(o2, r2);
        Assert.assertEquals(o1, q.first.getContent());
        Assert.assertEquals(o1, q.last.getContent());


        Object r1 = q.unpush();
        Assert.assertEquals(o1, r1);
        Assert.assertNull(q.first);
        Assert.assertNull(q.last);
    }


    @Test
    public void testSinglePushRemove() throws Exception {
        FastRemovalDequeue<Object> q = new FastRemovalDequeue<>(2);

        Object o1 = new Object();

        FastRemovalDequeue<Object>.Entry e1 = q.push(o1);

        Assert.assertEquals(o1, e1.getContent());

        q.remove(e1);

        Assert.assertNull(q.first);
        Assert.assertNull(q.last);
    }


    @Test
    public void testDoublePushRemove() throws Exception {
        FastRemovalDequeue<Object> q = new FastRemovalDequeue<>(2);

        Object o1 = new Object();
        Object o2 = new Object();

        FastRemovalDequeue<Object>.Entry e1 = q.push(o1);
        FastRemovalDequeue<Object>.Entry e2 = q.push(o2);

        Assert.assertEquals(o1, e1.getContent());
        Assert.assertEquals(o2, e2.getContent());

        Assert.assertEquals(o2, q.first.getContent());
        Assert.assertEquals(o1, q.last.getContent());

        q.remove(e1);

        Assert.assertEquals(o2, q.first.getContent());
        Assert.assertEquals(o2, q.last.getContent());

        q.remove(e2);

        Assert.assertNull(q.first);
        Assert.assertNull(q.last);
    }


    /*
     * A maximum size of one must be honoured. The constructor used to silently raise it to two, so a configured
     * maxLoadedJsps of one kept two JSPs loaded and evicted nothing until a third one was requested.
     */
    @Test
    public void testSingleSizeIsHonoured() throws Exception {
        FastRemovalDequeue<Object> q = new FastRemovalDequeue<>(1);

        Object o1 = new Object();
        Object o2 = new Object();
        Object o3 = new Object();

        FastRemovalDequeue<Object>.Entry e1 = q.push(o1);
        Assert.assertNull(e1.getReplaced());
        Assert.assertEquals(1, q.getSize());

        FastRemovalDequeue<Object>.Entry e2 = q.push(o2);
        // Adding to a full queue of one displaces the single entry
        Assert.assertSame(o1, e2.getReplaced());
        Assert.assertEquals(1, q.getSize());
        Assert.assertSame(o2, q.first.getContent());
        Assert.assertSame(o2, q.last.getContent());

        // Removing the handle of the evicted object is a NOP
        q.remove(e1);
        Assert.assertEquals(1, q.getSize());
        Assert.assertSame(o2, q.first.getContent());
        Assert.assertSame(o2, q.last.getContent());

        FastRemovalDequeue<Object>.Entry e3 = q.unpop(o3);
        // The symmetric case at the other end of the queue
        Assert.assertSame(o2, e3.getReplaced());
        Assert.assertEquals(1, q.getSize());
        Assert.assertSame(o3, q.first.getContent());
        Assert.assertSame(o3, q.last.getContent());

        // Removing the single entry empties the queue
        q.remove(e3);
        Assert.assertEquals(0, q.getSize());
        Assert.assertNull(q.first);
        Assert.assertNull(q.last);
    }


    /*
     * The rendering of an entry must tolerate null content, as the JDK collections render null elements, rather than
     * fail with a null pointer exception.
     */
    @Test
    public void testNullContentToString() throws Exception {
        FastRemovalDequeue<Object> q = new FastRemovalDequeue<>(2);

        FastRemovalDequeue<Object>.Entry e = q.push(null);

        Assert.assertEquals("Entry-null", e.toString());
    }


    /*
     * A maximum size below one cannot produce a usable queue and is rejected, rather than silently replaced by a
     * different size.
     */
    @Test
    public void testInvalidMaxSize() throws Exception {
        try {
            @SuppressWarnings("unused")
            FastRemovalDequeue<Object> dqueue = new FastRemovalDequeue<>(0);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // The message is localized, pin the rendered value
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("[0]"));
        }
        try {
            @SuppressWarnings("unused")
            FastRemovalDequeue<Object> dqueue = new FastRemovalDequeue<>(-1);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("[-1]"));
        }
    }
}

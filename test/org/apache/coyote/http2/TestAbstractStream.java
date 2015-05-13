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
package org.apache.coyote.http2;

import org.junit.Assert;
import org.junit.Test;

/*
 * This tests use A=1, B=2, etc to map stream IDs to the names used in the
 * figures.
 */
public class TestAbstractStream {

    @Test
    public void testDependenciesFig3() {
        // Setup
        Http2UpgradeHandler handler = new Http2UpgradeHandler();
        Stream a = new Stream(Integer.valueOf(1), handler);
        Stream b = new Stream(Integer.valueOf(2), handler);
        Stream c = new Stream(Integer.valueOf(3), handler);
        Stream d = new Stream(Integer.valueOf(4), handler);
        b.rePrioritise(a, false, 16);
        c.rePrioritise(a, false, 16);

        // Action
        d.rePrioritise(a, false, 16);

        // Check parents
        Assert.assertEquals(handler, a.getParentStream());
        Assert.assertEquals(a, b.getParentStream());
        Assert.assertEquals(a, c.getParentStream());
        Assert.assertEquals(a, d.getParentStream());

        // Check children
        Assert.assertEquals(3,  a.getChildStreams().size());
        Assert.assertTrue(a.getChildStreams().contains(b));
        Assert.assertTrue(a.getChildStreams().contains(c));
        Assert.assertTrue(a.getChildStreams().contains(d));
        Assert.assertEquals(0,  b.getChildStreams().size());
        Assert.assertEquals(0,  c.getChildStreams().size());
        Assert.assertEquals(0,  d.getChildStreams().size());
    }


    @Test
    public void testDependenciesFig4() {
        // Setup
        Http2UpgradeHandler handler = new Http2UpgradeHandler();
        Stream a = new Stream(Integer.valueOf(1), handler);
        Stream b = new Stream(Integer.valueOf(2), handler);
        Stream c = new Stream(Integer.valueOf(3), handler);
        Stream d = new Stream(Integer.valueOf(4), handler);
        b.rePrioritise(a, false, 16);
        c.rePrioritise(a, false, 16);

        // Action
        d.rePrioritise(a, true, 16);

        // Check parents
        Assert.assertEquals(handler, a.getParentStream());
        Assert.assertEquals(d, b.getParentStream());
        Assert.assertEquals(d, c.getParentStream());
        Assert.assertEquals(a, d.getParentStream());

        // Check children
        Assert.assertEquals(1,  a.getChildStreams().size());
        Assert.assertTrue(a.getChildStreams().contains(d));
        Assert.assertEquals(2,  d.getChildStreams().size());
        Assert.assertTrue(d.getChildStreams().contains(b));
        Assert.assertTrue(d.getChildStreams().contains(c));
        Assert.assertEquals(0,  b.getChildStreams().size());
        Assert.assertEquals(0,  c.getChildStreams().size());
    }


    @Test
    public void testDependenciesFig5NonExclusive() {
        // Setup
        Http2UpgradeHandler handler = new Http2UpgradeHandler();
        Stream a = new Stream(Integer.valueOf(1), handler);
        Stream b = new Stream(Integer.valueOf(2), handler);
        Stream c = new Stream(Integer.valueOf(3), handler);
        Stream d = new Stream(Integer.valueOf(4), handler);
        Stream e = new Stream(Integer.valueOf(5), handler);
        Stream f = new Stream(Integer.valueOf(6), handler);
        b.rePrioritise(a, false, 16);
        c.rePrioritise(a, false, 16);
        d.rePrioritise(c, false, 16);
        e.rePrioritise(c, false, 16);
        f.rePrioritise(d, false, 16);

        // Action
        a.rePrioritise(d, false, 16);

        // Check parents
        Assert.assertEquals(handler, d.getParentStream());
        Assert.assertEquals(d, f.getParentStream());
        Assert.assertEquals(d, a.getParentStream());
        Assert.assertEquals(a, b.getParentStream());
        Assert.assertEquals(a, c.getParentStream());
        Assert.assertEquals(c, e.getParentStream());

        // Check children
        Assert.assertEquals(2,  d.getChildStreams().size());
        Assert.assertTrue(d.getChildStreams().contains(a));
        Assert.assertTrue(d.getChildStreams().contains(f));
        Assert.assertEquals(0,  f.getChildStreams().size());
        Assert.assertEquals(2,  a.getChildStreams().size());
        Assert.assertTrue(a.getChildStreams().contains(b));
        Assert.assertTrue(a.getChildStreams().contains(c));
        Assert.assertEquals(0,  b.getChildStreams().size());
        Assert.assertEquals(1,  c.getChildStreams().size());
        Assert.assertTrue(c.getChildStreams().contains(e));
        Assert.assertEquals(0,  e.getChildStreams().size());
    }


    @Test
    public void testDependenciesFig5Exclusive() {
        // Setup
        Http2UpgradeHandler handler = new Http2UpgradeHandler();
        Stream a = new Stream(Integer.valueOf(1), handler);
        Stream b = new Stream(Integer.valueOf(2), handler);
        Stream c = new Stream(Integer.valueOf(3), handler);
        Stream d = new Stream(Integer.valueOf(4), handler);
        Stream e = new Stream(Integer.valueOf(5), handler);
        Stream f = new Stream(Integer.valueOf(6), handler);
        b.rePrioritise(a, false, 16);
        c.rePrioritise(a, false, 16);
        d.rePrioritise(c, false, 16);
        e.rePrioritise(c, false, 16);
        f.rePrioritise(d, false, 16);

        // Action
        a.rePrioritise(d, true, 16);

        // Check parents
        Assert.assertEquals(handler, d.getParentStream());
        Assert.assertEquals(d, a.getParentStream());
        Assert.assertEquals(a, b.getParentStream());
        Assert.assertEquals(a, c.getParentStream());
        Assert.assertEquals(a, f.getParentStream());
        Assert.assertEquals(c, e.getParentStream());

        // Check children
        Assert.assertEquals(1,  d.getChildStreams().size());
        Assert.assertTrue(d.getChildStreams().contains(a));
        Assert.assertEquals(3,  a.getChildStreams().size());
        Assert.assertTrue(a.getChildStreams().contains(b));
        Assert.assertTrue(a.getChildStreams().contains(c));
        Assert.assertTrue(a.getChildStreams().contains(f));
        Assert.assertEquals(0,  b.getChildStreams().size());
        Assert.assertEquals(0,  f.getChildStreams().size());
        Assert.assertEquals(1,  c.getChildStreams().size());
        Assert.assertTrue(c.getChildStreams().contains(e));
        Assert.assertEquals(0,  e.getChildStreams().size());
    }
}

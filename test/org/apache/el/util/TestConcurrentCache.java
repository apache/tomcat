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
package org.apache.el.util;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for {@link ConcurrentCache}.
 */
public class TestConcurrentCache {

    private static Map<?,?> eden(ConcurrentCache<String,String> cache)
            throws Exception {
        Field edenField = ConcurrentCache.class.getDeclaredField("eden");
        edenField.setAccessible(true);
        return (Map<?,?>) edenField.get(cache);
    }


    private static int edenSize(ConcurrentCache<String,String> cache)
            throws Exception {
        return eden(cache).size();
    }


    @Test
    public void testGetPut() throws Exception {
        ConcurrentCache<String,String> cache = new ConcurrentCache<>(4);

        Assert.assertNull(cache.get("missing"));

        cache.put("key", "value");
        Assert.assertEquals("value", cache.get("key"));
    }


    @Test
    public void testPromotionToLongterm() {
        ConcurrentCache<String,String> cache = new ConcurrentCache<>(2);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        // Eden has reached the configured size. This put promotes the
        // previous eden contents to the long-term store.
        cache.put("k3", "v3");

        // k1 was only promoted (not inserted again), so it must still be
        // available via the long-term store
        Assert.assertEquals("v1", cache.get("k1"));
        Assert.assertEquals("v2", cache.get("k2"));
        Assert.assertEquals("v3", cache.get("k3"));
    }


    /*
     * A long-term hit must be promoted into eden. If eden is full, its content
     * is moved to the long-term store first, as for put(), so eden never
     * exceeds the configured size.
     */
    @Test
    public void testGetPromotesLongtermHit() throws Exception {
        ConcurrentCache<String,String> cache = new ConcurrentCache<>(2);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        // Eden has reached the configured size. This put promotes the
        // previous eden contents to the long-term store.
        cache.put("k3", "v3");

        // Only k3 is in eden now
        Assert.assertEquals(1, edenSize(cache));

        // k1 is served from the long-term store and promoted back into eden
        // since eden still has room
        Assert.assertEquals("v1", cache.get("k1"));
        Assert.assertEquals(2, edenSize(cache));

        // Eden is full. The content of eden is moved to the long-term store
        // and then k2 is promoted into the now empty eden.
        Assert.assertEquals("v2", cache.get("k2"));
        Assert.assertEquals(1, edenSize(cache));
        Assert.assertTrue(eden(cache).containsKey("k2"));

        // The entries that were moved out of eden are still available
        Assert.assertEquals("v1", cache.get("k1"));
        Assert.assertEquals("v3", cache.get("k3"));
        Assert.assertEquals("v2", cache.get("k2"));
    }


    /*
     * Without any calls to put(), the entries that are read must end up in eden
     * (where they can be read without taking a lock) rather than eden staying
     * as it was once it had filled.
     */
    @Test
    public void testGetWorkingSetMovesToEden() throws Exception {
        ConcurrentCache<String,String> cache = new ConcurrentCache<>(3);

        String[] keys = { "a", "b", "c", "d", "e" };
        for (String key : keys) {
            cache.put(key, "v" + key);
        }
        // a, b and c are in the long-term store. d and e are in eden.
        Assert.assertFalse(eden(cache).containsKey("a"));
        Assert.assertFalse(eden(cache).containsKey("b"));

        // Read the working set of a and b a number of times
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals("va", cache.get("a"));
            Assert.assertEquals("vb", cache.get("b"));
        }

        Assert.assertTrue(eden(cache).containsKey("a"));
        Assert.assertTrue(eden(cache).containsKey("b"));
        Assert.assertTrue(edenSize(cache) <= 3);

        // Everything is still available
        for (String key : keys) {
            Assert.assertEquals("v" + key, cache.get(key));
        }
    }


    /*
     * Regression test: promotion of long-term hits into eden by get() must
     * respect the configured eden size. Previously, get() promoted
     * unconditionally, so a hit dominated workload could grow eden far beyond
     * the configured size.
     */
    @Test
    public void testGetPromotionRespectsEdenSize() throws Exception {
        int size = 10;
        ConcurrentCache<String,String> cache = new ConcurrentCache<>(size);

        // Hold strong references to the keys for the duration of the test so
        // that the long-term store cannot silently lose entries via GC
        String[] keys = new String[100];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = "k" + i;
        }

        // Generate a number of promotions so that most keys end up only in
        // the long-term store
        for (int i = 0; i < keys.length; i++) {
            cache.put(keys[i], "v" + i);
        }

        // Read every key back. Each long-term hit is a promotion candidate.
        for (int i = 0; i < keys.length; i++) {
            Assert.assertEquals("v" + i, cache.get(keys[i]));
        }

        Assert.assertTrue("Eden size [" + edenSize(cache) +
                "] should not exceed the configured size [" + size + "]",
                edenSize(cache) <= size);
    }
}

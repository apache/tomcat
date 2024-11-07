/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.collections;

import org.junit.Assert;
import org.junit.Test;

public class TestConcurrentLruCache {

    @Test
    public void testLimit() {
        ConcurrentLruCache<String> cache = getStartingCache();

        cache.add("D");

        Assert.assertFalse(cache.contains("A"));
        Assert.assertTrue(cache.contains("B"));
        Assert.assertTrue(cache.contains("C"));
        Assert.assertTrue(cache.contains("D"));
    }


    @Test
    public void testLRU() {
        ConcurrentLruCache<String> cache = getStartingCache();

        cache.contains("A");
        cache.add("D");

        Assert.assertTrue(cache.contains("A"));
        Assert.assertFalse(cache.contains("B"));
        Assert.assertTrue(cache.contains("C"));
        Assert.assertTrue(cache.contains("D"));
    }


    @Test
    public void testIncreaseLimit() {
        ConcurrentLruCache<String> cache = getStartingCache();

        cache.setLimit(4);

        cache.add("D");

        Assert.assertTrue(cache.contains("A"));
        Assert.assertTrue(cache.contains("B"));
        Assert.assertTrue(cache.contains("C"));
        Assert.assertTrue(cache.contains("D"));

        cache.add("E");

        Assert.assertFalse(cache.contains("A"));
        Assert.assertTrue(cache.contains("B"));
        Assert.assertTrue(cache.contains("C"));
        Assert.assertTrue(cache.contains("D"));
        Assert.assertTrue(cache.contains("E"));
    }


    @Test
    public void testReduceLimit() {
        ConcurrentLruCache<String> cache = getStartingCache();

        cache.contains("A");
        cache.setLimit(2);

        Assert.assertTrue(cache.contains("A"));
        Assert.assertFalse(cache.contains("B"));
        Assert.assertTrue(cache.contains("C"));
    }


    private ConcurrentLruCache<String> getStartingCache() {
        ConcurrentLruCache<String> cache = new ConcurrentLruCache<>(3);

        cache.add("A");
        cache.add("B");
        cache.add("C");

        return cache;
    }
}

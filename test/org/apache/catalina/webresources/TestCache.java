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
package org.apache.catalina.webresources;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.WebResource;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestCache extends TomcatBaseTest {

    // Enough files to fill a 100 KB cache several times over
    private static final int FILE_COUNT = 1000;
    private static final long MAX_SIZE_KB = 100;

    private StandardRoot root;

    @Before
    public void createWebapp() throws Exception {
        File docBase = new File(getTemporaryDirectory(), "cache-eviction");
        Assert.assertTrue(docBase.mkdirs());
        addDeleteOnTearDown(docBase);
        for (int i = 0; i < FILE_COUNT; i++) {
            Files.writeString(new File(docBase, fileName(i)).toPath(), Integer.toString(i), StandardCharsets.UTF_8);
        }

        Tomcat tomcat = getTomcatInstance();
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());
        tomcat.start();

        root = (StandardRoot) ctx.getResources();
    }


    @Test
    public void testFullCacheOfUnexpiredEntries() throws Exception {
        Cache cache = createCache(60000);

        for (int i = 0; i < FILE_COUNT; i++) {
            assertContent(cache.getResource("/" + fileName(i), false), i);
            Assert.assertTrue(cache.getSize() <= MAX_SIZE_KB);
        }

        // None of the entries has expired so the last resource could not be cached
        long hits = cache.getHitCount();
        assertContent(cache.getResource("/" + fileName(FILE_COUNT - 1), false), FILE_COUNT - 1);
        Assert.assertEquals(hits, cache.getHitCount());

        // The first resource was cached before the cache was full
        assertContent(cache.getResource("/" + fileName(0), false), 0);
        Assert.assertEquals(hits + 1, cache.getHitCount());
    }


    @Test
    public void testEvictionResumesAfterEntriesExpire() throws Exception {
        Cache cache = createCache(1000);

        for (int i = 0; i < FILE_COUNT / 2; i++) {
            cache.getResource("/" + fileName(i), false);
        }

        // Wait for every cached entry to expire
        Thread.sleep(1500);

        String path = "/" + fileName(FILE_COUNT - 1);
        assertContent(cache.getResource(path, false), FILE_COUNT - 1);
        long hits = cache.getHitCount();
        assertContent(cache.getResource(path, false), FILE_COUNT - 1);
        Assert.assertEquals(hits + 1, cache.getHitCount());
    }


    @Test
    public void testEvictionResumesWhenOldestEntriesExpire() throws Exception {
        Cache cache = createCache(1500);

        for (int i = 0; i < 100; i++) {
            cache.getResource("/" + fileName(i), false);
        }
        Thread.sleep(1000);
        // Fill the cache with entries that expire later than the first ones
        for (int i = 100; i < 400; i++) {
            cache.getResource("/" + fileName(i), false);
        }
        Assert.assertFalse(isCached(cache, "/" + fileName(FILE_COUNT - 2)));

        // Only the first entries have expired
        Thread.sleep(700);
        Assert.assertTrue(isCached(cache, "/" + fileName(FILE_COUNT - 1)));
    }


    @Test
    public void testEvictionForDirectoryListing() throws Exception {
        Cache cache = createCache(1000);

        for (int i = 0; i < FILE_COUNT / 2; i++) {
            cache.getResource("/" + fileName(i), false);
        }

        long hits = cache.getHitCount();
        cache.getResources("/", false);
        cache.getResources("/", false);
        Assert.assertEquals(hits, cache.getHitCount());

        // Wait for every cached entry to expire
        Thread.sleep(1500);

        cache.getResources("/", false);
        hits = cache.getHitCount();
        cache.getResources("/", false);
        Assert.assertEquals(hits + 1, cache.getHitCount());
    }


    private static boolean isCached(Cache cache, String path) {
        cache.getResource(path, false);
        long hits = cache.getHitCount();
        cache.getResource(path, false);
        return cache.getHitCount() == hits + 1;
    }


    private Cache createCache(long ttl) {
        Cache cache = new Cache(root);
        cache.setMaxSize(MAX_SIZE_KB);
        cache.setObjectMaxSize((int) MAX_SIZE_KB / 20);
        cache.setTtl(ttl);
        return cache;
    }


    private static void assertContent(WebResource resource, int expected) {
        Assert.assertTrue(resource.exists());
        Assert.assertEquals(Integer.toString(expected), new String(resource.getContent(), StandardCharsets.UTF_8));
    }


    private static String fileName(int i) {
        return String.format("f%04d.txt", Integer.valueOf(i));
    }
}

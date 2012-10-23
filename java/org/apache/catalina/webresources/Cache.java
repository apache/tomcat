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

import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.catalina.WebResource;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class Cache {

    private static final Log log = LogFactory.getLog(Cache.class);
    protected static final StringManager sm =
            StringManager.getManager(Constants.Package);

    // Estimate (on high side to be safe) of average size excluding content
    // based on profiler data.
    private static final long CACHE_ENTRY_SIZE = 500;

    private static final long TARGET_FREE_PERCENT_GET = 5;
    private static final long TARGET_FREE_PERCENT_BACKGROUND = 10;

    private final StandardRoot root;
    private final AtomicLong size = new AtomicLong(0);

    private long ttl = 5000;
    private long maxSize = 10 * 1024 * 1024;
    private long maxObjectSize = maxSize / 20;

    private ConcurrentMap<String,CachedResource> resourceCache =
            new ConcurrentHashMap<>();

    public Cache(StandardRoot root) {
        this.root = root;
    }

    protected WebResource getResource(String path) {

        // TODO Should some resources be excluded from caching?

        CachedResource cacheEntry = resourceCache.get(path);

        if (cacheEntry != null && !cacheEntry.validate()) {
            removeCacheEntry(path, true);
            cacheEntry = null;
        }

        if (cacheEntry == null) {
            CachedResource newCacheEntry = new CachedResource(root, path, ttl);
            // Concurrent callers will end up with the same CachedResource
            // instance
            cacheEntry = resourceCache.putIfAbsent(path, newCacheEntry);

            if (cacheEntry == null) {
                // newCacheEntry was inserted into the cache - validate it
                cacheEntry = newCacheEntry;
                cacheEntry.validate();
                if (newCacheEntry.getContentLength() > getMaxObjectSize()) {
                    // Cache size has not been updated at this point
                    removeCacheEntry(path, false);
                    return newCacheEntry;
                }

                // Assume that the cache entry will include the content.
                // This isn't always the case but it makes tracking the
                // current cache size easier.
                long delta = CACHE_ENTRY_SIZE;
                delta += cacheEntry.getContentLength();
                size.addAndGet(delta);

                if (size.get() > maxSize) {
                    // Process resources unordered for speed. Trades cache
                    // efficiency (younger entries may be evicted before older
                    // ones) for speed since this is on the critical path for
                    // request processing
                    long targetSize =
                            maxSize * (100 - TARGET_FREE_PERCENT_GET) / 100;
                    long newSize = evict(
                            targetSize, resourceCache.values().iterator());
                    if (newSize > maxSize) {
                        // Unable to create sufficient space for this resource
                        // Remove it from the cache
                        removeCacheEntry(path, true);
                        log.warn(sm.getString("cache.addFail"));
                    }
                }
            } else {
                // Another thread added the entry to the cache
                // Make sure it is validated
                cacheEntry.validate();
            }
        }

        return cacheEntry;
    }

    protected void backgroundProcess() {
        // Create an ordered set of all cached resources with the least recently
        // used first. This is a background process so we can afford to take the
        // time to order the elements first
        TreeSet<CachedResource> orderedResources =
                new TreeSet<>(new EvictionOrder());
        orderedResources.addAll(resourceCache.values());

        Iterator<CachedResource> iter = orderedResources.iterator();

        long targetSize =
                maxSize * (100 - TARGET_FREE_PERCENT_BACKGROUND) / 100;
        long newSize = evict(targetSize, iter);

        if (newSize > targetSize) {
            log.info(sm.getString("cache.backgroundEvictFail",
                    Long.valueOf(TARGET_FREE_PERCENT_BACKGROUND),
                    root.getContext().getName(),
                    Long.valueOf(newSize / 1024)));
        }
    }

    private long evict(long targetSize, Iterator<CachedResource> iter) {

        long now = System.currentTimeMillis();

        long newSize = size.get();

        while (newSize > targetSize && iter.hasNext()) {
            CachedResource resource = iter.next();

            // Don't expire anything that has been checked within the TTL
            if (resource.getNextCheck() > now) {
                continue;
            }

            // Remove the entry from the cache
            removeCacheEntry(resource.getWebappPath(), true);

            newSize = size.get();
        }

        return newSize;
    }

    private void removeCacheEntry(String path, boolean updateSize) {
        // With concurrent calls for the same path, the entry is only removed
        // once and the cache size is only updated (if required) once.
        CachedResource cachedResource = resourceCache.remove(path);
        if (cachedResource != null && updateSize) {
            long delta =
                    0 - CACHE_ENTRY_SIZE - cachedResource.getContentLength();
            size.addAndGet(delta);
        }
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public long getMaxSize() {
        // Internally bytes, externally kilobytes
        return maxSize / 1024;
    }

    public void setMaxSize(long maxSize) {
        // Internally bytes, externally kilobytes
        this.maxSize = maxSize * 1024;
    }


    public void setMaxObjectSize(long maxObjectSize) {
        // Internally bytes, externally kilobytes
        this.maxObjectSize = maxObjectSize * 1024;
    }

    public long getMaxObjectSize() {
        // Internally bytes, externally kilobytes
        return maxObjectSize / 1024;
    }

    private static class EvictionOrder implements Comparator<CachedResource> {

        @Override
        public int compare(CachedResource cr1, CachedResource cr2) {
            long nc1 = cr1.getNextCheck();
            long nc2 = cr2.getNextCheck();

            // Oldest resource should be first (so iterator goes from oldest to
            // youngest.
            if (nc1 == nc2) {
                return 0;
            } else if (nc1 > nc2) {
                return -1;
            } else {
                return 1;
            }
        }
    }
}

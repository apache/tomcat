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

    private static final long TARGET_FREE_PERCENT_GET = 5;
    private static final long TARGET_FREE_PERCENT_BACKGROUND = 10;

    // objectMaxSize must be < maxSize/20
    private static final int OBJECT_MAX_SIZE_FACTOR = 20;

    private final StandardRoot root;
    private final AtomicLong size = new AtomicLong(0);

    private long ttl = 5000;
    private long maxSize = 10 * 1024 * 1024;
    private int objectMaxSize = (int) maxSize/OBJECT_MAX_SIZE_FACTOR;

    private AtomicLong lookupCount = new AtomicLong(0);
    private AtomicLong hitCount = new AtomicLong(0);

    private final ConcurrentMap<String,CachedResource> resourceCache =
            new ConcurrentHashMap<>();

    public Cache(StandardRoot root) {
        this.root = root;
    }

    protected WebResource getResource(String path, boolean useClassLoaderResources) {

        lookupCount.incrementAndGet();

        if (noCache(path)) {
            return root.getResourceInternal(path, useClassLoaderResources);
        }

        CachedResource cacheEntry = resourceCache.get(path);

        if (cacheEntry != null && !cacheEntry.validateResource(useClassLoaderResources)) {
            removeCacheEntry(path);
            cacheEntry = null;
        }

        if (cacheEntry == null) {
            // Local copy to ensure consistency
            int objectMaxSizeBytes = getObjectMaxSizeBytes();
            CachedResource newCacheEntry =
                    new CachedResource(this, root, path, getTtl(), objectMaxSizeBytes);

            // Concurrent callers will end up with the same CachedResource
            // instance
            cacheEntry = resourceCache.putIfAbsent(path, newCacheEntry);

            if (cacheEntry == null) {
                // newCacheEntry was inserted into the cache - validate it
                cacheEntry = newCacheEntry;
                cacheEntry.validateResource(useClassLoaderResources);

                // Even if the resource content larger than objectMaxSizeBytes
                // there is still benefit in caching the resource metadata

                long delta = cacheEntry.getSize();
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
                        removeCacheEntry(path);
                        log.warn(sm.getString("cache.addFail", path));
                    }
                }
            } else {
                // Another thread added the entry to the cache
                // Make sure it is validated
                cacheEntry.validateResource(useClassLoaderResources);
            }
        } else {
            hitCount.incrementAndGet();
        }

        return cacheEntry;
    }

    protected WebResource[] getResources(String path, boolean useClassLoaderResources) {
        lookupCount.incrementAndGet();

        // Don't call noCache(path) since the class loader only caches
        // individual resources. Therefore, always cache collections here

        CachedResource cacheEntry = resourceCache.get(path);

        if (cacheEntry != null && !cacheEntry.validateResources(useClassLoaderResources)) {
            removeCacheEntry(path);
            cacheEntry = null;
        }

        if (cacheEntry == null) {
            // Local copy to ensure consistency
            int objectMaxSizeBytes = getObjectMaxSizeBytes();
            CachedResource newCacheEntry =
                    new CachedResource(this, root, path, getTtl(), objectMaxSizeBytes);

            // Concurrent callers will end up with the same CachedResource
            // instance
            cacheEntry = resourceCache.putIfAbsent(path, newCacheEntry);

            if (cacheEntry == null) {
                // newCacheEntry was inserted into the cache - validate it
                cacheEntry = newCacheEntry;
                cacheEntry.validateResources(useClassLoaderResources);

                // Content will not be cached but we still need metadata size
                long delta = cacheEntry.getSize();
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
                        removeCacheEntry(path);
                        log.warn(sm.getString("cache.addFail", path));
                    }
                }
            } else {
                // Another thread added the entry to the cache
                // Make sure it is validated
                cacheEntry.validateResources(useClassLoaderResources);
            }
        } else {
            hitCount.incrementAndGet();
        }

        return cacheEntry.getWebResources();
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

    private boolean noCache(String path) {
        // Don't cache resources used by the class loader (it has its own cache)
        if (path.startsWith("/WEB-INF/classes") ||
                path.startsWith("/WEB-INF/lib")) {
            return true;
        }
        return false;
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
            removeCacheEntry(resource.getWebappPath());

            newSize = size.get();
        }

        return newSize;
    }

    void removeCacheEntry(String path) {
        // With concurrent calls for the same path, the entry is only removed
        // once and the cache size is only updated (if required) once.
        CachedResource cachedResource = resourceCache.remove(path);
        if (cachedResource != null) {
            long delta = cachedResource.getSize();
            size.addAndGet(-delta);
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

    public long getLookupCount() {
        return lookupCount.get();
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public void setObjectMaxSize(int objectMaxSize) {
        if (objectMaxSize * 1024L > Integer.MAX_VALUE) {
            log.warn(sm.getString("cache.objectMaxSizeTooBigBytes", Integer.valueOf(objectMaxSize)));
            this.objectMaxSize = Integer.MAX_VALUE;
        }
        // Internally bytes, externally kilobytes
        this.objectMaxSize = objectMaxSize * 1024;
    }

    public int getObjectMaxSize() {
        // Internally bytes, externally kilobytes
        return objectMaxSize / 1024;
    }

    public int getObjectMaxSizeBytes() {
        return objectMaxSize;
    }

    void enforceObjectMaxSizeLimit() {
        long limit = maxSize / OBJECT_MAX_SIZE_FACTOR;
        if (limit > Integer.MAX_VALUE) {
            return;
        }
        if (objectMaxSize > limit) {
            log.warn(sm.getString("cache.objectMaxSizeTooBig",
                    Integer.valueOf(objectMaxSize / 1024), Integer.valueOf((int)limit / 1024)));
            objectMaxSize = (int) limit;
        }
    }

    public void clear() {
        resourceCache.clear();
        size.set(0);
    }

    public long getSize() {
        return size.get() / 1024;
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

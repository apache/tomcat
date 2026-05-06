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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A thread-safe LRU (Least Recently Used) cache with a configurable size limit.
 * Entries beyond the limit are evicted in LRU order.
 *
 * @param <T> the type of entries stored in the cache
 */
public class ConcurrentLruCache<T> {

    private volatile LimitedLinkedHashMap<T,T> map;
    private final Object lock = new Object();

    /**
     * Creates a new LRU cache with the specified size limit.
     *
     * @param limit the maximum number of entries in the cache
     */
    public ConcurrentLruCache(int limit) {
        setLimit(limit);
    }


    /**
     * Adds an entry to the cache. If the cache is at its limit, the least
     * recently used entry will be evicted.
     *
     * @param entry the entry to add
     */
    public void add(T entry) {
        if (map == null) {
            return;
        }
        synchronized (lock) {
            if (map == null) {
                return;
            }
            map.put(entry, entry);
        }
    }


    /**
     * Checks whether the cache contains the given entry. Accessing an entry
     * through this method updates its recency.
     *
     * @param entry the entry to check
     * @return {@code true} if the entry is in the cache
     */
    public boolean contains(T entry) {
        if (map == null) {
            return false;
        }
        synchronized (lock) {
            if (map == null) {
                return false;
            }
            return map.get(entry) != null;
        }
    }


    /**
     * Removes all entries from the cache.
     */
    public void clear() {
        if (map == null) {
            return;
        }
        synchronized (lock) {
            if (map == null) {
                return;
            }
            map.clear();
        }
    }


    /**
     * Sets the maximum number of entries in the cache. If the new limit is
     * smaller than the current size, the least recently used entries will be evicted.
     * A limit of 0 or less disables the cache.
     *
     * @param limit the new maximum number of entries
     */
    public void setLimit(int limit) {
        synchronized (lock) {
            if (limit > 0) {
                Map<T,T> oldMap = map;
                map = new LimitedLinkedHashMap<>(limit);
                if (oldMap != null) {
                    map.putAll(oldMap);
                }
            } else {
                map = null;
            }
        }
    }


    /**
     * Returns the current maximum number of entries allowed in the cache.
     *
     * @return the cache limit, or -1 if the cache is disabled
     */
    public int getLimit() {
        synchronized (lock) {
            if (map == null) {
                return -1;
            } else {
                return map.getLimit();
            }
        }
    }


    private static class LimitedLinkedHashMap<K, V> extends LinkedHashMap<K,V> {
        private static final long serialVersionUID = 1L;

        private final int limit;

        LimitedLinkedHashMap(int limit) {
            super(16, 0.75F, true);
            this.limit = limit;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
            return size() > limit;
        }

        private int getLimit() {
            return limit;
        }
    }
}

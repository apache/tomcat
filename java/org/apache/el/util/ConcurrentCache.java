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
package org.apache.el.util;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe cache that uses an eden space backed by a {@link ConcurrentHashMap}
 * and a long-term space backed by a {@link WeakHashMap}. When the eden space exceeds
 * the configured size, its contents are promoted to the long-term space.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public final class ConcurrentCache<K, V> {

    private final int size;

    private final Map<K,V> eden;

    private final Map<K,V> longterm;

    /**
     * Creates a new cache with the specified eden space size.
     *
     * @param size the maximum size of the eden space before promotion to long-term storage
     */
    public ConcurrentCache(int size) {
        this.size = size;
        this.eden = new ConcurrentHashMap<>(size);
        this.longterm = new WeakHashMap<>(size);
    }

    /**
     * Retrieves a value from the cache. If found in the long-term store, it is
     * promoted to the eden space for faster subsequent access.
     *
     * @param k the key to look up
     * @return the cached value, or {@code null} if not found
     */
    public V get(K k) {
        V v = this.eden.get(k);
        if (v == null) {
            synchronized (longterm) {
                v = this.longterm.get(k);
            }
            if (v != null) {
                this.eden.put(k, v);
            }
        }
        return v;
    }

    /**
     * Stores a value in the cache. If the eden space is full, all eden entries
     * are promoted to the long-term store before adding the new entry.
     *
     * @param k the key
     * @param v the value to cache
     */
    public void put(K k, V v) {
        if (this.eden.size() >= size) {
            synchronized (longterm) {
                this.longterm.putAll(this.eden);
            }
            this.eden.clear();
        }
        this.eden.put(k, v);
    }
}

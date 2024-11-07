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

public class ConcurrentLruCache<T> {

    private volatile LimitedLinkedHashMap<T,T> map;
    private final Object lock = new Object();

    public ConcurrentLruCache(int limit) {
        setLimit(limit);
    }


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


    public int getLimit() {
        synchronized (lock) {
            if (map == null) {
                return -1;
            } else {
                return map.getLimit();
            }
        }
    }


    private static class LimitedLinkedHashMap<K,V> extends LinkedHashMap<K,V> {
        private static final long serialVersionUID = 1L;

        private volatile int limit;

        LimitedLinkedHashMap(int limit) {
            super(16, 0.75F, true);
            this.limit = limit;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
            if (size() > limit) {
                return true;
            }
            return false;
        }

        private int getLimit() {
            return limit;
        }
    }
}

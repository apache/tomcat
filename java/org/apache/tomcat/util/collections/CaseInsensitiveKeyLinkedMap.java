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
import java.util.Locale;

/**
 * A Map implementation that uses case-insensitive (using
 * {@link Locale#ENGLISH}) strings as keys. This class uses a
 * <code>LinkedHashMap</code> as backing store. So this Map's behavior, except
 * for case-insensitive key handling, is comparable with
 * <code>LinkedHashMap</code>. Most notably is its predictable iteration order.
 * <p>
 * Keys must be instances of {@link String}. Note that this means that
 * <code>null</code> keys are not permitted.
 * <p>
 * This implementation is not thread-safe.
 *
 * @param <V> Type of values placed in this Map.
 * @see LinkedHashMap
 */
public class CaseInsensitiveKeyLinkedMap<V> extends CaseInsensitiveKeyMap<V> {

    /**
     * Constructs an empty insertion-ordered
     * <code>CaseInsensitiveKeyLinkedMap</code> instance with the default initial
     * capacity (16) and load factor (0.75).
     */
    public CaseInsensitiveKeyLinkedMap() {
        this(false);
    }
    
    /**
     * Constructs an empty <code>CaseInsensitiveKeyLinkedMap</code> instance with
     * the specified ordering mode.
     *
     * @param accessOrder the ordering mode - <code>true</code> for access-order,
     *            <code>false</code> for insertion-order
     */
    public CaseInsensitiveKeyLinkedMap(boolean accessOrder) {
        super(new LinkedHashMap<>(16, 0.75f, accessOrder));
    }
}

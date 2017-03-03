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
package org.apache.catalina.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.apache.tomcat.util.res.StringManager;

/**
 * Extended implementation of <strong>HashMap</strong> that includes a
 * <code>locked</code> property.  This class can be used to safely expose
 * Catalina internal parameter map objects to user classes without having
 * to clone them in order to avoid modifications.  When first created, a
 * <code>ParmaeterMap</code> instance is not locked.
 *
 * @param <K> The type of Key
 * @param <V> The type of Value
 *
 * @author Craig R. McClanahan
 */
public final class ParameterMap<K,V> extends LinkedHashMap<K,V> {

    private static final long serialVersionUID = 1L;


    // ----------------------------------------------------------- Constructors
    /**
     * Construct a new, empty map with the default initial capacity and
     * load factor.
     */
    public ParameterMap() {

        super();

    }


    /**
     * Construct a new, empty map with the specified initial capacity and
     * default load factor.
     *
     * @param initialCapacity The initial capacity of this map
     */
    public ParameterMap(int initialCapacity) {

        super(initialCapacity);

    }


    /**
     * Construct a new, empty map with the specified initial capacity and
     * load factor.
     *
     * @param initialCapacity The initial capacity of this map
     * @param loadFactor The load factor of this map
     */
    public ParameterMap(int initialCapacity, float loadFactor) {

        super(initialCapacity, loadFactor);

    }


    /**
     * Construct a new map with the same mappings as the given map.
     *
     * @param map Map whose contents are duplicated in the new map
     */
    public ParameterMap(Map<K,V> map) {

        super(map);

    }


    // ------------------------------------------------------------- Properties


    /**
     * The current lock state of this parameter map.
     */
    private boolean locked = false;


    /**
     * @return the locked state of this parameter map.
     */
    public boolean isLocked() {

        return (this.locked);

    }


    /**
     * Set the locked state of this parameter map.
     *
     * @param locked The new locked state
     */
    public void setLocked(boolean locked) {

        this.locked = locked;

    }


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager("org.apache.catalina.util");


    // --------------------------------------------------------- Public Methods



    /**
     * Remove all mappings from this map.
     *
     * @exception IllegalStateException if this map is currently locked
     */
    @Override
    public void clear() {

        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        super.clear();

    }


    /**
     * Associate the specified value with the specified key in this map.  If
     * the map previously contained a mapping for this key, the old value is
     * replaced.
     *
     * @param key Key with which the specified value is to be associated
     * @param value Value to be associated with the specified key
     *
     * @return The previous value associated with the specified key, or
     *  <code>null</code> if there was no mapping for key
     *
     * @exception IllegalStateException if this map is currently locked
     */
    @Override
    public V put(K key, V value) {

        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        return (super.put(key, value));

    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this map is currently locked
     */
    @Override
    public V putIfAbsent(K key, V value) {

        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        return (super.putIfAbsent(key, value));

    }


    /**
     * Copy all of the mappings from the specified map to this one.  These
     * mappings replace any mappings that this map had for any of the keys
     * currently in the specified Map.
     *
     * @param map Mappings to be stored into this map
     *
     * @exception IllegalStateException if this map is currently locked
     */
    @Override
    public void putAll(Map<? extends K,? extends V> map) {

        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        super.putAll(map);

    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this map is currently locked
     */
    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {

        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        return super.merge(key, value, remappingFunction);

    }


    /**
     * Remove the mapping for this key from the map if present.
     *
     * @param key Key whose mapping is to be removed from the map
     *
     * @return The previous value associated with the specified key, or
     *  <code>null</code> if there was no mapping for that key
     *
     * @exception IllegalStateException if this map is currently locked
     */
    @Override
    public V remove(Object key) {

        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        return (super.remove(key));

    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this map is currently locked
     */
    @Override
    public boolean remove(Object key, Object value) {

        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        return (super.remove(key, value));

    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this map is currently locked
     */
    @Override
    public V replace(K key, V value) {

        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        return (super.replace(key, value));

    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this map is currently locked
     */
    @Override
    public boolean replace(K key, V oldValue, V newValue) {

        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        return (super.replace(key, oldValue, newValue));

    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this map is currently locked
     */
    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {

        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        super.replaceAll(function);

    }


    /**
     * {@inheritDoc}
     * <P>
     * <EM>NOTE:<EM> If this map is currently locked, it returns an <STRONG>unmodifiable</STRONG> {@link Set} view
     * of the keys instead.
     * </P>
     *
     * @return a set view of the keys contained in this map
     */
    @Override
    public Set<K> keySet() {

        if (locked)
            return Collections.unmodifiableSet(super.keySet());

        return super.keySet();

    }


    /**
     * {@inheritDoc}
     * <P>
     * <EM>NOTE:<EM> If this map is currently locked, it returns an <STRONG>unmodifiable</STRONG> {@link Collection}
     * view of the values instead.
     * </P>
     *
     * @return a collection view of the values contained in this map
     */
    @Override
    public Collection<V> values() {

        if (locked)
            return Collections.unmodifiableCollection(super.values());

        return super.values();

    }


    /**
     * {@inheritDoc}
     * <P>
     * <EM>NOTE:<EM> If this map is currently locked, it returns an <STRONG>unmodifiable</STRONG> {@link Set} view
     * of the mappings instead.
     * </P>
     */
    @Override
    public Set<Map.Entry<K, V>> entrySet() {

        if (locked)
            return Collections.unmodifiableSet(super.entrySet());

        return super.entrySet();

    }
}

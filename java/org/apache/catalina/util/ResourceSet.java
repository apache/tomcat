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


import java.io.Serial;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.tomcat.util.res.StringManager;


/**
 * Extended implementation of <strong>HashSet</strong> that includes a <code>locked</code> property. This class can be
 * used to safely expose resource path sets to user classes without having to clone them in order to avoid
 * modifications. When first created, a <code>ResourceSet</code> is not locked.
 *
 * @param <T> The type of elements in the Set
 */
public final class ResourceSet<T> extends HashSet<T> {

    @Serial
    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------------- Constructors
    /**
     * Construct a new, empty set with the default initial capacity and load factor.
     */
    public ResourceSet() {

        super();

    }


    /**
     * Construct a new, empty set with the specified initial capacity and default load factor.
     *
     * @param initialCapacity The initial capacity of this set
     */
    public ResourceSet(int initialCapacity) {

        super(initialCapacity);

    }


    /**
     * Construct a new, empty set with the specified initial capacity and load factor.
     *
     * @param initialCapacity The initial capacity of this set
     * @param loadFactor      The load factor of this set
     */
    public ResourceSet(int initialCapacity, float loadFactor) {

        super(initialCapacity, loadFactor);

    }


    /**
     * Construct a new set with the same contents as the existing collection.
     *
     * @param coll The collection whose contents we should copy
     */
    public ResourceSet(Collection<T> coll) {

        super(coll);

    }


    // ------------------------------------------------------------- Properties


    /**
     * The current lock state of this parameter map.
     */
    private boolean locked = false;


    /**
     * Return the locked state of this parameter map.
     *
     * @return the locked state of this parameter map
     */
    public boolean isLocked() {
        return this.locked;
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
    private static final StringManager sm = StringManager.getManager("org.apache.catalina.util");


    // --------------------------------------------------------- Public Methods


    /**
     * Check the lock state and throw an exception if locked.
     *
     * @exception IllegalStateException if this set is currently locked
     */
    private void checkLocked() {
        if (locked) {
            throw new IllegalStateException(sm.getString("resourceSet.locked"));
        }
    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this set is currently locked
     */
    @Override
    public boolean add(T o) {
        checkLocked();
        return super.add(o);
    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this set is currently locked
     */
    @Override
    public boolean addAll(Collection<? extends T> c) {
        checkLocked();
        return super.addAll(c);
    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this set is currently locked
     */
    @Override
    public void clear() {

        checkLocked();
        super.clear();

    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this set is currently locked
     */
    @Override
    public boolean remove(Object o) {
        checkLocked();
        return super.remove(o);
    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this set is currently locked
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        checkLocked();
        return super.removeAll(c);
    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this set is currently locked
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        checkLocked();
        return super.retainAll(c);
    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this set is currently locked
     */
    @Override
    public boolean removeIf(java.util.function.Predicate<? super T> filter) {
        checkLocked();
        return super.removeIf(filter);
    }


    /**
     * {@inheritDoc}
     *
     * @exception IllegalStateException if this set is currently locked
     */
    @Override
    public Iterator<T> iterator() {
        if (locked) {
            return new LockedIterator(super.iterator());
        }
        return super.iterator();
    }


    /**
     * An iterator that throws {@link IllegalStateException} on {@code remove()} when the set is locked.
     */
    private class LockedIterator implements Iterator<T> {

        private final Iterator<T> delegate;


        private LockedIterator(Iterator<T> delegate) {
            this.delegate = delegate;
        }


        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }


        @Override
        public T next() {
            return delegate.next();
        }


        @Override
        public void remove() {
            throw new IllegalStateException(sm.getString("resourceSet.locked"));
        }
    }


}

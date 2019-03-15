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

package org.apache.tomcat.dbcp.pool;

/**
 * A simple base implementation of <code>KeyedObjectPool</code>.
 * Optional operations are implemented to either do nothing, return a value
 * indicating it is unsupported or throw {@link UnsupportedOperationException}.
 *
 * @param <K> the type of keys in this pool
 * @param <V> the type of objects held in this pool
 *
 * @author Rodney Waldhoff
 * @author Sandy McArthur
 * @since Pool 1.0
 */
public abstract class BaseKeyedObjectPool<K, V> implements KeyedObjectPool<K, V> {

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract V borrowObject(K key) throws Exception;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract void returnObject(K key, V obj) throws Exception;

    /**
     * <p>Invalidates an object from the pool.</p>
     *
     * <p>By contract, <code>obj</code> <strong>must</strong> have been obtained
     * using {@link #borrowObject borrowObject} using a <code>key</code> that is
     * equivalent to the one used to borrow the <code>Object</code> in the first place.</p>
     *
     * <p>This method should be used when an object that has been borrowed
     * is determined (due to an exception or other problem) to be invalid.</p>
     *
     * @param key the key used to obtain the object
     * @param obj a {@link #borrowObject borrowed} instance to be returned.
     * @throws Exception
     */
    @Override
    public abstract void invalidateObject(K key, V obj) throws Exception;

    /**
     * Not supported in this base implementation.
     * Always throws an {@link UnsupportedOperationException},
     * subclasses should override this behavior.
     * @param key ignored
     * @throws UnsupportedOperationException
     */
    @Override
    public void addObject(K key) throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported in this base implementation.
     * @return a negative value.
     * @param key ignored
     */
    @Override
    public int getNumIdle(K key) throws UnsupportedOperationException {
        return -1;
    }

    /**
     * Not supported in this base implementation.
     * @return a negative value.
     * @param key ignored
     */
    @Override
    public int getNumActive(K key) throws UnsupportedOperationException {
        return -1;
    }

    /**
     * Not supported in this base implementation.
     * @return a negative value.
     */
    @Override
    public int getNumIdle() throws UnsupportedOperationException {
        return -1;
    }

    /**
     * Not supported in this base implementation.
     * @return a negative value.
     */
    @Override
    public int getNumActive() throws UnsupportedOperationException {
        return -1;
    }

    /**
     * Not supported in this base implementation.
     * @throws UnsupportedOperationException
     */
    @Override
    public void clear() throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported in this base implementation.
     * @param key ignored
     * @throws UnsupportedOperationException
     */
    @Override
    public void clear(K key) throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Close this pool.
     * This affects the behavior of <code>isClosed</code> and <code>assertOpen</code>.
     */
    @Override
    public void close() throws Exception {
        closed = true;
    }

    /**
     * Not supported in this base implementation.
     * Always throws an {@link UnsupportedOperationException},
     * subclasses should override this behavior.
     * @param factory the new KeyedPoolableObjectFactory
     * @deprecated to be removed in pool 2.0
     */
    @Override
    @Deprecated
    public void setFactory(KeyedPoolableObjectFactory<K, V> factory) throws IllegalStateException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Has this pool instance been closed.
     * @return <code>true</code> when this pool has been closed.
     * @since Pool 1.4
     */
    protected final boolean isClosed() {
        return closed;
    }

    /**
     * Throws an <code>IllegalStateException</code> when this pool has been closed.
     * @throws IllegalStateException when this pool has been closed.
     * @see #isClosed()
     * @since Pool 1.4
     */
    protected final void assertOpen() throws IllegalStateException {
        if(isClosed()) {
            throw new IllegalStateException("Pool not open");
        }
    }

    /** Whether or not the pool is close */
    private volatile boolean closed = false;
}

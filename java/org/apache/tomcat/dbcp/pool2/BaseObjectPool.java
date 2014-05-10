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
package org.apache.tomcat.dbcp.pool2;

/**
 * A simple base implementation of {@link ObjectPool}.
 * Optional operations are implemented to either do nothing, return a value
 * indicating it is unsupported or throw {@link UnsupportedOperationException}.
 * <p>
 * This class is intended to be thread-safe.
 *
 * @param <T> Type of element pooled in this pool.
 *
 * @since 2.0
 */
public abstract class BaseObjectPool<T> implements ObjectPool<T> {

    @Override
    public abstract T borrowObject() throws Exception;

    @Override
    public abstract void returnObject(T obj) throws Exception;

    @Override
    public abstract void invalidateObject(T obj) throws Exception;

    /**
     * Not supported in this base implementation.
     *
     * @return a negative value.
     */
    @Override
    public int getNumIdle() {
        return -1;
    }

    /**
     * Not supported in this base implementation.
     *
     * @return a negative value.
     */
    @Override
    public int getNumActive() {
        return -1;
    }

    /**
     * Not supported in this base implementation.
     *
     * @throws UnsupportedOperationException if the pool does not implement this
     *          method
     */
    @Override
    public void clear() throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported in this base implementation. Subclasses should override
     * this behavior.
     *
     * @throws UnsupportedOperationException if the pool does not implement this
     *          method
     */
    @Override
    public void addObject() throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This affects the behavior of <code>isClosed</code> and
     * <code>assertOpen</code>.
     */
    @Override
    public void close() {
        closed = true;
    }

    /**
     * Has this pool instance been closed.
     *
     * @return <code>true</code> when this pool has been closed.
     */
    public final boolean isClosed() {
        return closed;
    }

    /**
     * Throws an <code>IllegalStateException</code> when this pool has been
     * closed.
     *
     * @throws IllegalStateException when this pool has been closed.
     *
     * @see #isClosed()
     */
    protected final void assertOpen() throws IllegalStateException {
        if (isClosed()) {
            throw new IllegalStateException("Pool not open");
        }
    }

    private volatile boolean closed = false;
}

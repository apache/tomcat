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
 * A simple base implementation of {@link ObjectPool}.
 * Optional operations are implemented to either do nothing, return a value
 * indicating it is unsupported or throw {@link UnsupportedOperationException}.
 *
 * @author Rodney Waldhoff
 * @author Sandy McArthur
 * @version $Revision: 1084645 $ $Date: 2011-03-23 10:11:19 -0700 (Wed, 23 Mar 2011) $
 * @since Pool 1.0
 */
public abstract class BaseObjectPool implements ObjectPool {
    /**
     * Obtains an instance from the pool.
     * 
     * @return an instance from the pool
     * @throws Exception if an instance cannot be obtained from the pool
     */
    public abstract Object borrowObject() throws Exception;
    
    /**
     * Returns an instance to the pool.
     * 
     * @param obj instance to return to the pool
     */
    public abstract void returnObject(Object obj) throws Exception;
    
    /**
     * <p>Invalidates an object from the pool.</p>
     * 
     * <p>By contract, <code>obj</code> <strong>must</strong> have been obtained
     * using {@link #borrowObject borrowObject}.<p>
     * 
     * <p>This method should be used when an object that has been borrowed
     * is determined (due to an exception or other problem) to be invalid.</p>
     *
     * @param obj a {@link #borrowObject borrowed} instance to be disposed.
     * @throws Exception 
     */
    public abstract void invalidateObject(Object obj) throws Exception;

    /**
     * Not supported in this base implementation.
     * @return a negative value.
     * 
     * @throws UnsupportedOperationException
     */
    public int getNumIdle() throws UnsupportedOperationException {
        return -1;
    }

    /**
     * Not supported in this base implementation.
     * @return a negative value.
     * 
     * @throws UnsupportedOperationException
     */
    public int getNumActive() throws UnsupportedOperationException {
        return -1;
    }

    /**
     * Not supported in this base implementation.
     * 
     * @throws UnsupportedOperationException
     */
    public void clear() throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported in this base implementation.
     * Always throws an {@link UnsupportedOperationException},
     * subclasses should override this behavior.
     * 
     * @throws UnsupportedOperationException
     */
    public void addObject() throws Exception, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Close this pool.
     * This affects the behavior of <code>isClosed</code> and <code>assertOpen</code>.
     */
    public void close() throws Exception {
        closed = true;
    }

    /**
     * Not supported in this base implementation.
     * Always throws an {@link UnsupportedOperationException},
     * subclasses should override this behavior.
     * 
     * @param factory the PoolableObjectFactory
     * @throws UnsupportedOperationException
     * @throws IllegalStateException
     * @deprecated to be removed in pool 2.0
     */
    public void setFactory(PoolableObjectFactory factory) throws IllegalStateException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Has this pool instance been closed.
     * @return <code>true</code> when this pool has been closed.
     */
    public final boolean isClosed() {
        return closed;
    }

    /**
     * Throws an <code>IllegalStateException</code> when this pool has been closed.
     * @throws IllegalStateException when this pool has been closed.
     * @see #isClosed()
     */
    protected final void assertOpen() throws IllegalStateException {
        if (isClosed()) {
            throw new IllegalStateException("Pool not open");
        }
    }

    /** Whether or not the pool is closed */
    private volatile boolean closed = false;
}

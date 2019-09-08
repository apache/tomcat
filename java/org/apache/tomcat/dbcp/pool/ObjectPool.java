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

import java.util.NoSuchElementException;

/**
 * A pooling interface.
 * <p>
 * <code>ObjectPool</code> defines a trivially simple pooling interface. The only
 * required methods are {@link #borrowObject borrowObject}, {@link #returnObject returnObject}
 * and {@link #invalidateObject invalidateObject}.
 * </p>
 * <p>
 * Example of use:
 * <pre style="border:solid thin; padding: 1ex;"
 * > Object obj = <code style="color:#00C">null</code>;
 *
 * <code style="color:#00C">try</code> {
 *     obj = pool.borrowObject();
 *     <code style="color:#00C">try</code> {
 *         <code style="color:#0C0">//...use the object...</code>
 *     } <code style="color:#00C">catch</code>(Exception e) {
 *         <code style="color:#0C0">// invalidate the object</code>
 *         pool.invalidateObject(obj);
 *         <code style="color:#0C0">// do not return the object to the pool twice</code>
 *         obj = <code style="color:#00C">null</code>;
 *     } <code style="color:#00C">finally</code> {
 *         <code style="color:#0C0">// make sure the object is returned to the pool</code>
 *         <code style="color:#00C">if</code>(<code style="color:#00C">null</code> != obj) {
 *             pool.returnObject(obj);
 *        }
 *     }
 * } <code style="color:#00C">catch</code>(Exception e) {
 *       <code style="color:#0C0">// failed to borrow an object</code>
 * }</pre>
 *
 * <p>See {@link BaseObjectPool} for a simple base implementation.</p>
 *
 * @param <T> the type of objects held in this pool
 *
 * @author Rodney Waldhoff
 * @author Sandy McArthur
 * @see PoolableObjectFactory
 * @see ObjectPoolFactory
 * @see KeyedObjectPool
 * @see BaseObjectPool
 * @since Pool 1.0
 */
public interface ObjectPool<T> {
    /**
     * Obtains an instance from this pool.
     * <p>
     * Instances returned from this method will have been either newly created with
     * {@link PoolableObjectFactory#makeObject makeObject} or will be a previously idle object and
     * have been activated with {@link PoolableObjectFactory#activateObject activateObject} and
     * then validated with {@link PoolableObjectFactory#validateObject validateObject}.
     * </p>
     * <p>
     * By contract, clients <strong>must</strong> return the borrowed instance using
     * {@link #returnObject returnObject}, {@link #invalidateObject invalidateObject}, or a related method
     * as defined in an implementation or sub-interface.
     * </p>
     * <p>
     * The behaviour of this method when the pool has been exhausted
     * is not strictly specified (although it may be specified by implementations).
     * Older versions of this method would return <code>null</code> to indicate exhaustion,
     * newer versions are encouraged to throw a {@link NoSuchElementException}.
     * </p>
     *
     * @return an instance from this pool.
     * @throws IllegalStateException after {@link #close close} has been called on this pool.
     * @throws Exception when {@link PoolableObjectFactory#makeObject makeObject} throws an exception.
     * @throws NoSuchElementException when the pool is exhausted and cannot or will not return another instance.
     */
    T borrowObject() throws Exception, NoSuchElementException, IllegalStateException;

    /**
     * Return an instance to the pool.
     * By contract, <code>obj</code> <strong>must</strong> have been obtained
     * using {@link #borrowObject() borrowObject}
     * or a related method as defined in an implementation
     * or sub-interface.
     *
     * @param obj a {@link #borrowObject borrowed} instance to be returned.
     * @throws Exception
     */
    void returnObject(T obj) throws Exception;

    /**
     * <p>Invalidates an object from the pool.</p>
     *
     * <p>By contract, <code>obj</code> <strong>must</strong> have been obtained
     * using {@link #borrowObject borrowObject} or a related method as defined in
     * an implementation or sub-interface.</p>
     *
     * <p>This method should be used when an object that has been borrowed
     * is determined (due to an exception or other problem) to be invalid.</p>
     *
     * @param obj a {@link #borrowObject borrowed} instance to be disposed.
     * @throws Exception
     */
    void invalidateObject(T obj) throws Exception;

    /**
     * Create an object using the {@link PoolableObjectFactory factory} or other
     * implementation dependent mechanism, passivate it, and then place it in the idle object pool.
     * <code>addObject</code> is useful for "pre-loading" a pool with idle objects.
     * (Optional operation).
     *
     * @throws Exception when {@link PoolableObjectFactory#makeObject} fails.
     * @throws IllegalStateException after {@link #close} has been called on this pool.
     * @throws UnsupportedOperationException when this pool cannot add new idle objects.
     */
    void addObject() throws Exception, IllegalStateException, UnsupportedOperationException;

    /**
     * Return the number of instances
     * currently idle in this pool (optional operation).
     * This may be considered an approximation of the number
     * of objects that can be {@link #borrowObject borrowed}
     * without creating any new instances.
     * Returns a negative value if this information is not available.
     *
     * @return the number of instances currently idle in this pool or a negative value if unsupported
     * @throws UnsupportedOperationException <strong>deprecated</strong>: if this implementation does not support the operation
     */
    int getNumIdle() throws UnsupportedOperationException;

    /**
     * Return the number of instances
     * currently borrowed from this pool
     * (optional operation).
     * Returns a negative value if this information is not available.
     *
     * @return the number of instances currently borrowed from this pool or a negative value if unsupported
     * @throws UnsupportedOperationException <strong>deprecated</strong>: if this implementation does not support the operation
     */
    int getNumActive() throws UnsupportedOperationException;

    /**
     * Clears any objects sitting idle in the pool, releasing any
     * associated resources (optional operation).
     * Idle objects cleared must be {@link PoolableObjectFactory#destroyObject(Object) destroyed}.
     *
     * @throws UnsupportedOperationException if this implementation does not support the operation
     */
    void clear() throws Exception, UnsupportedOperationException;

    /**
     * Close this pool, and free any resources associated with it.
     * <p>
     * Calling {@link #addObject} or {@link #borrowObject} after invoking
     * this method on a pool will cause them to throw an
     * {@link IllegalStateException}.
     * </p>
     *
     * @throws Exception <strong>deprecated</strong>: implementations should silently fail if not all resources can be freed.
     */
    void close() throws Exception;

    /**
     * Sets the {@link PoolableObjectFactory factory} this pool uses
     * to create new instances (optional operation). Trying to change
     * the <code>factory</code> after a pool has been used will frequently
     * throw an {@link UnsupportedOperationException}. It is up to the pool
     * implementation to determine when it is acceptable to call this method.
     *
     * @param factory the {@link PoolableObjectFactory} used to create new instances.
     * @throws IllegalStateException when the factory cannot be set at this time
     * @throws UnsupportedOperationException if this implementation does not support the operation
     * @deprecated to be removed in pool 2.0
     */
    @Deprecated
    void setFactory(PoolableObjectFactory<T> factory) throws IllegalStateException, UnsupportedOperationException;
}

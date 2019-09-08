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
 * A "keyed" pooling interface.
 * <p>
 * A keyed pool pools instances of multiple types. Each
 * type may be accessed using an arbitrary key.
 * </p>
 * <p>
 * Example of use:
 * <pre style="border:solid thin; padding: 1ex;"
 * > Object obj = <code style="color:#00C">null</code>;
 * Object key = <code style="color:#C00">"Key"</code>;
 *
 * <code style="color:#00C">try</code> {
 *     obj = pool.borrowObject(key);
 *     <code style="color:#0C0">//...use the object...</code>
 * } <code style="color:#00C">catch</code>(Exception e) {
 *     <code style="color:#0C0">// invalidate the object</code>
 *     pool.invalidateObject(key, obj);
 *     <code style="color:#0C0">// do not return the object to the pool twice</code>
 *     obj = <code style="color:#00C">null</code>;
 * } <code style="color:#00C">finally</code> {
 *     <code style="color:#0C0">// make sure the object is returned to the pool</code>
 *     <code style="color:#00C">if</code>(<code style="color:#00C">null</code> != obj) {
 *         pool.returnObject(key, obj);
 *     }
 * }</pre>
 * <p>
 * {@link KeyedObjectPool} implementations <i>may</i> choose to store at most
 * one instance per key value, or may choose to maintain a pool of instances
 * for each key (essentially creating a {@link java.util.Map Map} of
 * {@link ObjectPool pools}).
 * </p>
 *
 * <p>See {@link BaseKeyedObjectPool} for a simple base implementation.</p>
 *
 * @param <K> the type of keys in this pool
 * @param <V> the type of objects held in this pool
 *
 * @author Rodney Waldhoff
 * @author Sandy McArthur
 * @see KeyedPoolableObjectFactory
 * @see KeyedObjectPoolFactory
 * @see ObjectPool
 * @see BaseKeyedObjectPool
 * @since Pool 1.0
 */
public interface KeyedObjectPool<K,V> {
    /**
     * Obtains an instance from this pool for the specified <code>key</code>.
     * <p>
     * Instances returned from this method will have been either newly created with
     * {@link KeyedPoolableObjectFactory#makeObject makeObject} or will be a previously idle object and
     * have been activated with {@link KeyedPoolableObjectFactory#activateObject activateObject} and
     * then validated with {@link KeyedPoolableObjectFactory#validateObject validateObject}.
     * <p>
     * By contract, clients <strong>must</strong> return the borrowed object using
     * {@link #returnObject returnObject}, {@link #invalidateObject invalidateObject}, or a related method
     * as defined in an implementation or sub-interface,
     * using a <code>key</code> that is {@link Object#equals equivalent} to the one used to
     * borrow the instance in the first place.
     * <p>
     * The behaviour of this method when the pool has been exhausted
     * is not strictly specified (although it may be specified by implementations).
     * Older versions of this method would return <code>null</code> to indicate exhaustion,
     * newer versions are encouraged to throw a {@link NoSuchElementException}.
     *
     * @param key the key used to obtain the object
     * @return an instance from this pool.
     * @throws IllegalStateException after {@link #close close} has been called on this pool
     * @throws Exception when {@link KeyedPoolableObjectFactory#makeObject makeObject} throws an exception
     * @throws NoSuchElementException when the pool is exhausted and cannot or will not return another instance
     */
    V borrowObject(K key) throws Exception, NoSuchElementException, IllegalStateException;

    /**
     * Return an instance to the pool.
     * By contract, <code>obj</code> <strong>must</strong> have been obtained
     * using {@link #borrowObject borrowObject}
     * or a related method as defined in an implementation
     * or sub-interface
     * using a <code>key</code> that is equivalent to the one used to
     * borrow the instance in the first place.
     *
     * @param key the key used to obtain the object
     * @param obj a {@link #borrowObject borrowed} instance to be returned.
     * @throws Exception
     */
    void returnObject(K key, V obj) throws Exception;

    /**
     * <p>Invalidates an object from the pool.</p>
     *
     * <p>By contract, <code>obj</code> <strong>must</strong> have been obtained
     * using {@link #borrowObject borrowObject} or a related method as defined
     * in an implementation or sub-interface using a <code>key</code> that is
     * equivalent to the one used to borrow the <code>Object</code> in the first place.</p>
     *
     * <p>This method should be used when an object that has been borrowed
     * is determined (due to an exception or other problem) to be invalid.</p>
     *
     * @param key the key used to obtain the object
     * @param obj a {@link #borrowObject borrowed} instance to be returned.
     * @throws Exception
     */
    void invalidateObject(K key, V obj) throws Exception;

    /**
     * Create an object using the {@link KeyedPoolableObjectFactory factory} or other
     * implementation dependent mechanism, passivate it, and then place it in the idle object pool.
     * <code>addObject</code> is useful for "pre-loading" a pool with idle objects
     * (Optional operation).
     *
     * @param key the key a new instance should be added to
     * @throws Exception when {@link KeyedPoolableObjectFactory#makeObject} fails.
     * @throws IllegalStateException after {@link #close} has been called on this pool.
     * @throws UnsupportedOperationException when this pool cannot add new idle objects.
     */
    void addObject(K key) throws Exception, IllegalStateException, UnsupportedOperationException;

    /**
     * Returns the number of instances
     * corresponding to the given <code>key</code>
     * currently idle in this pool (optional operation).
     * Returns a negative value if this information is not available.
     *
     * @param key the key to query
     * @return the number of instances corresponding to the given <code>key</code> currently idle in this pool or a negative value if unsupported
     * @throws UnsupportedOperationException <strong>deprecated</strong>: when this implementation doesn't support the operation
     */
    int getNumIdle(K key) throws UnsupportedOperationException;

    /**
     * Returns the number of instances
     * currently borrowed from but not yet returned
     * to the pool corresponding to the
     * given <code>key</code> (optional operation).
     * Returns a negative value if this information is not available.
     *
     * @param key the key to query
     * @return the number of instances corresponding to the given <code>key</code> currently borrowed in this pool or a negative value if unsupported
     * @throws UnsupportedOperationException <strong>deprecated</strong>: when this implementation doesn't support the operation
     */
    int getNumActive(K key) throws UnsupportedOperationException;

    /**
     * Returns the total number of instances
     * currently idle in this pool (optional operation).
     * Returns a negative value if this information is not available.
     *
     * @return the total number of instances currently idle in this pool or a negative value if unsupported
     * @throws UnsupportedOperationException <strong>deprecated</strong>: when this implementation doesn't support the operation
     */
    int getNumIdle() throws UnsupportedOperationException;

    /**
     * Returns the total number of instances
     * current borrowed from this pool but not
     * yet returned (optional operation).
     * Returns a negative value if this information is not available.
     *
     * @return the total number of instances currently borrowed from this pool or a negative value if unsupported
     * @throws UnsupportedOperationException <strong>deprecated</strong>: when this implementation doesn't support the operation
     */
    int getNumActive() throws UnsupportedOperationException;

    /**
     * Clears the pool, removing all pooled instances (optional operation).
     * Throws {@link UnsupportedOperationException} if the pool cannot be cleared.
     *
     * @throws UnsupportedOperationException when this implementation doesn't support the operation
     */
    void clear() throws Exception, UnsupportedOperationException;

    /**
     * Clears the specified pool, removing all
     * pooled instances corresponding to
     * the given <code>key</code> (optional operation).
     * Throws {@link UnsupportedOperationException} if the pool cannot be cleared.
     *
     * @param key the key to clear
     * @throws UnsupportedOperationException when this implementation doesn't support the operation
     */
    void clear(K key) throws Exception, UnsupportedOperationException;

    /**
     * Close this pool, and free any resources associated with it.
     * <p>
     * Calling {@link #addObject addObject} or {@link #borrowObject borrowObject} after invoking
     * this method on a pool will cause them to throw an {@link IllegalStateException}.
     * </p>
     *
     * @throws Exception
     */
    void close() throws Exception;

    /**
     * Sets the {@link KeyedPoolableObjectFactory factory} the pool uses
     * to create new instances (optional operation).
     * Trying to change the <code>factory</code> after a pool has been used will frequently
     * throw an {@link UnsupportedOperationException}. It is up to the pool
     * implementation to determine when it is acceptable to call this method.
     *
     * @param factory the {@link KeyedPoolableObjectFactory} used to create new instances.
     * @throws IllegalStateException when the factory cannot be set at this time
     * @throws UnsupportedOperationException when this implementation doesn't support the operation
     * @deprecated to be removed in pool 2.0
     */
    @Deprecated
    void setFactory(KeyedPoolableObjectFactory<K, V> factory) throws IllegalStateException, UnsupportedOperationException;
}

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

import java.io.Closeable;
import java.util.NoSuchElementException;

/**
 * A "keyed" pooling interface.
 * <p>
 * A keyed pool maintains a pool of instances for each key value.
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
 * <p>
 * See {@link org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool
 * GenericKeyedObjectPool} for an implementation.
 *
 * @param <K> The type of keys maintained by this pool.
 * @param <V> Type of element pooled in this pool.
 *
 * @see KeyedPooledObjectFactory
 * @see ObjectPool
 * @see org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool GenericKeyedObjectPool
 *
 * @since 2.0
 */
public interface KeyedObjectPool<K,V> extends Closeable {
    /**
     * Obtains an instance from this pool for the specified <code>key</code>.
     * <p>
     * Instances returned from this method will have been either newly created
     * with {@link KeyedPooledObjectFactory#makeObject makeObject} or will be
     * a previously idle object and have been activated with
     * {@link KeyedPooledObjectFactory#activateObject activateObject} and then
     * (optionally) validated with
     * {@link KeyedPooledObjectFactory#validateObject validateObject}.
     * <p>
     * By contract, clients <strong>must</strong> return the borrowed object
     * using {@link #returnObject returnObject},
     * {@link #invalidateObject invalidateObject}, or a related method as
     * defined in an implementation or sub-interface, using a <code>key</code>
     * that is {@link Object#equals equivalent} to the one used to borrow the
     * instance in the first place.
     * <p>
     * The behaviour of this method when the pool has been exhausted is not
     * strictly specified (although it may be specified by implementations).
     *
     * @param key the key used to obtain the object
     *
     * @return an instance from this pool.
     *
     * @throws IllegalStateException
     *              after {@link #close close} has been called on this pool
     * @throws Exception
     *              when {@link KeyedPooledObjectFactory#makeObject
     *              makeObject} throws an exception
     * @throws NoSuchElementException
     *              when the pool is exhausted and cannot or will not return
     *              another instance
     */
    V borrowObject(K key) throws Exception, NoSuchElementException, IllegalStateException;

    /**
     * Return an instance to the pool. By contract, <code>obj</code>
     * <strong>must</strong> have been obtained using
     * {@link #borrowObject borrowObject} or a related method as defined in an
     * implementation or sub-interface using a <code>key</code> that is
     * equivalent to the one used to borrow the instance in the first place.
     *
     * @param key the key used to obtain the object
     * @param obj a {@link #borrowObject borrowed} instance to be returned.
     *
     * @throws IllegalStateException
     *              if an attempt is made to return an object to the pool that
     *              is in any state other than allocated (i.e. borrowed).
     *              Attempting to return an object more than once or attempting
     *              to return an object that was never borrowed from the pool
     *              will trigger this exception.
     *
     * @throws Exception if an instance cannot be returned to the pool
     */
    void returnObject(K key, V obj) throws Exception;

    /**
     * Invalidates an object from the pool.
     * <p>
     * By contract, <code>obj</code> <strong>must</strong> have been obtained
     * using {@link #borrowObject borrowObject} or a related method as defined
     * in an implementation or sub-interface using a <code>key</code> that is
     * equivalent to the one used to borrow the <code>Object</code> in the first
     * place.
     * <p>
     * This method should be used when an object that has been borrowed is
     * determined (due to an exception or other problem) to be invalid.
     *
     * @param key the key used to obtain the object
     * @param obj a {@link #borrowObject borrowed} instance to be returned.
     *
     * @throws Exception if the instance cannot be invalidated
     */
    void invalidateObject(K key, V obj) throws Exception;

    /**
     * Create an object using the {@link KeyedPooledObjectFactory factory} or
     * other implementation dependent mechanism, passivate it, and then place it
     * in the idle object pool. <code>addObject</code> is useful for
     * "pre-loading" a pool with idle objects (Optional operation).
     *
     * @param key the key a new instance should be added to
     *
     * @throws Exception
     *              when {@link KeyedPooledObjectFactory#makeObject} fails.
     * @throws IllegalStateException
     *              after {@link #close} has been called on this pool.
     * @throws UnsupportedOperationException
     *              when this pool cannot add new idle objects.
     */
    void addObject(K key) throws Exception, IllegalStateException,
            UnsupportedOperationException;

    /**
     * Returns the number of instances corresponding to the given
     * <code>key</code> currently idle in this pool. Returns a negative value if
     * this information is not available.
     *
     * @param key the key to query
     * @return the number of instances corresponding to the given
     * <code>key</code> currently idle in this pool.
     */
    int getNumIdle(K key);

    /**
     * Returns the number of instances currently borrowed from but not yet
     * returned to the pool corresponding to the given <code>key</code>.
     * Returns a negative value if this information is not available.
     *
     * @param key the key to query
     * @return the number of instances currently borrowed from but not yet
     * returned to the pool corresponding to the given <code>key</code>.
     */
    int getNumActive(K key);

    /**
     * Returns the total number of instances currently idle in this pool.
     * Returns a negative value if this information is not available.
     * @return the total number of instances currently idle in this pool.
     */
    int getNumIdle();

    /**
     * Returns the total number of instances currently borrowed from this pool but
     * not yet returned. Returns a negative value if this information is not
     * available.
     * @return the total number of instances currently borrowed from this pool but
     * not yet returned.
     */
    int getNumActive();

    /**
     * Clears the pool, removing all pooled instances (optional operation).
     *
     * @throws UnsupportedOperationException when this implementation doesn't
     *                                       support the operation
     *
     * @throws Exception if the pool cannot be cleared
     */
    void clear() throws Exception, UnsupportedOperationException;

    /**
     * Clears the specified pool, removing all pooled instances corresponding to
     * the given <code>key</code> (optional operation).
     *
     * @param key the key to clear
     *
     * @throws UnsupportedOperationException when this implementation doesn't
     *                                       support the operation
     *
     * @throws Exception if the key cannot be cleared
     */
    void clear(K key) throws Exception, UnsupportedOperationException;

    /**
     * Close this pool, and free any resources associated with it.
     * <p>
     * Calling {@link #addObject addObject} or
     * {@link #borrowObject borrowObject} after invoking this method on a pool
     * will cause them to throw an {@link IllegalStateException}.
     * <p>
     * Implementations should silently fail if not all resources can be freed.
     */
    @Override
    void close();
}

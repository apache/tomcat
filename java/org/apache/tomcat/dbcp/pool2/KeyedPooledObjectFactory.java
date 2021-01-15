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
 * An interface defining life-cycle methods for
 * instances to be served by a {@link KeyedObjectPool}.
 * <p>
 * By contract, when an {@link KeyedObjectPool}
 * delegates to a {@link KeyedPooledObjectFactory},
 * </p>
 * <ol>
 *  <li>
 *   {@link #makeObject} is called whenever a new instance is needed.
 *  </li>
 *  <li>
 *   {@link #activateObject} is invoked on every instance that has been
 *   {@link #passivateObject passivated} before it is
 *   {@link KeyedObjectPool#borrowObject borrowed} from the pool.
 *  </li>
 *  <li>
 *   {@link #validateObject} may be invoked on {@link #activateObject activated}
 *   instances to make sure they can be
 *   {@link KeyedObjectPool#borrowObject borrowed} from the pool.
 *   {@code validateObject} may also be used to test an
 *   instance being {@link KeyedObjectPool#returnObject returned} to the pool
 *   before it is {@link #passivateObject passivated}. It will only be invoked
 *   on an activated instance.
 *  </li>
 *  <li>
 *   {@link #passivateObject passivateObject}
 *   is invoked on every instance when it is returned to the pool.
 *  </li>
 *  <li>
 *   {@link #destroyObject destroyObject}
 *   is invoked on every instance when it is being "dropped" from the
 *   pool (whether due to the response from {@code validateObject},
 *   or for reasons specific to the pool implementation.) There is no
 *   guarantee that the instance being destroyed will
 *   be considered active, passive or in a generally consistent state.
 *  </li>
 * </ol>
 * {@link KeyedPooledObjectFactory} must be thread-safe. The only promise
 * an {@link KeyedObjectPool} makes is that the same instance of an object will
 * not be passed to more than one method of a
 * {@code KeyedPoolableObjectFactory} at a time.
 * <p>
 * While clients of a {@link KeyedObjectPool} borrow and return instances of
 * the underlying value type V, the factory methods act on instances of
 * {@link PooledObject PooledObject&lt;V&gt;}.  These are the object wrappers that
 * pools use to track and maintain state informations about the objects that
 * they manage.
 * </p>
 *
 * @see KeyedObjectPool
 * @see BaseKeyedPooledObjectFactory
 *
 * @param <K> The type of keys managed by this factory.
 * @param <V> Type of element managed by this factory.
 *
 * @since 2.0
 */
public interface KeyedPooledObjectFactory<K, V> {

    /**
     * Create an instance that can be served by the pool and
     * wrap it in a {@link PooledObject} to be managed by the pool.
     *
     * @param key the key used when constructing the object
     *
     * @return a {@code PooledObject} wrapping an instance that can
     * be served by the pool.
     *
     * @throws Exception if there is a problem creating a new instance,
     *    this will be propagated to the code requesting an object.
     */
    PooledObject<V> makeObject(K key) throws Exception;

    /**
     * Destroy an instance no longer needed by the pool.
     * <p>
     * It is important for implementations of this method to be aware that there
     * is no guarantee about what state {@code obj} will be in and the
     * implementation should be prepared to handle unexpected errors.
     * </p>
     * <p>
     * Also, an implementation must take in to consideration that instances lost
     * to the garbage collector may never be destroyed.
     * </p>
     *
     * @param key the key used when selecting the instance
     * @param p a {@code PooledObject} wrapping the instance to be destroyed
     *
     * @throws Exception should be avoided as it may be swallowed by
     *    the pool implementation.
     *
     * @see #validateObject
     * @see KeyedObjectPool#invalidateObject
     */
    void destroyObject(K key, PooledObject<V> p) throws Exception;

    /**
     * Destroy an instance no longer needed by the pool, using the provided {@link DestroyMode}.
     *
     * @param key the key used when selecting the instance
     * @param p a {@code PooledObject} wrapping the instance to be destroyed
     * @param mode DestroyMode providing context to the factory
     *
     * @throws Exception should be avoided as it may be swallowed by
     *    the pool implementation.
     *
     * @see #validateObject
     * @see KeyedObjectPool#invalidateObject
     * @see #destroyObject(Object, PooledObject)
     * @see DestroyMode
     * @since 2.9.0
     */
    void destroyObject(final K key, final PooledObject<V> p, final DestroyMode mode) throws Exception;

    /**
     * Ensures that the instance is safe to be returned by the pool.
     *
     * @param key the key used when selecting the object
     * @param p a {@code PooledObject} wrapping the instance to be validated
     *
     * @return {@code false} if {@code obj} is not valid and should
     *         be dropped from the pool, {@code true} otherwise.
     */
    boolean validateObject(K key, PooledObject<V> p);

    /**
     * Reinitialize an instance to be returned by the pool.
     *
     * @param key the key used when selecting the object
     * @param p a {@code PooledObject} wrapping the instance to be activated
     *
     * @throws Exception if there is a problem activating {@code obj},
     *    this exception may be swallowed by the pool.
     *
     * @see #destroyObject
     */
    void activateObject(K key, PooledObject<V> p) throws Exception;

    /**
     * Uninitialize an instance to be returned to the idle object pool.
     *
     * @param key the key used when selecting the object
     * @param p a {@code PooledObject} wrapping the instance to be passivated
     *
     * @throws Exception if there is a problem passivating {@code obj},
     *    this exception may be swallowed by the pool.
     *
     * @see #destroyObject
     */
    void passivateObject(K key, PooledObject<V> p) throws Exception;
}


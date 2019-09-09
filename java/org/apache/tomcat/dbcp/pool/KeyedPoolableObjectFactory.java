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
 * An interface defining life-cycle methods for
 * instances to be served by a {@link KeyedObjectPool}.
 * <p>
 * By contract, when an {@link KeyedObjectPool}
 * delegates to a {@link KeyedPoolableObjectFactory},
 * <ol>
 *  <li>
 *   {@link #makeObject makeObject}
 *   is called whenever a new instance is needed.
 *  </li>
 *  <li>
 *   {@link #activateObject activateObject}
 *   is invoked on every instance that has been
 *   {@link #passivateObject passivated} before it is
 *   {@link KeyedObjectPool#borrowObject borrowed} from the pool.
 *  </li>
 *  <li>
 *   {@link #validateObject validateObject}
 *   is invoked on {@link #activateObject activated} instances to make sure
 *   they can be {@link KeyedObjectPool#borrowObject borrowed} from the pool.
 *   <code>validateObject</code> <strong>may</strong> also be used to test an
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
 *   pool (whether due to the response from <code>validateObject</code>,
 *   or for reasons specific to the pool implementation.) There is no
 *   guarantee that the instance being destroyed will
 *   be considered active, passive or in a generally consistent state.
 *  </li>
 * </ol>
 * <p>
 * {@link KeyedPoolableObjectFactory} must be thread-safe. The only promise
 * an {@link KeyedObjectPool} makes is that the same instance of an object will not
 * be passed to more than one method of a <code>KeyedPoolableObjectFactory</code>
 * at a time.
 * </p>
 *
 * @param <K> the type of keys in this pool
 * @param <V> the type of objects held in this pool
 *
 * @see KeyedObjectPool
 *
 * @author Rodney Waldhoff
 * @author Sandy McArthur
 * @since Pool 1.0
 */
public interface KeyedPoolableObjectFactory<K, V> {
    /**
     * Create an instance that can be served by the pool.
     *
     * @param key the key used when constructing the object
     * @return an instance that can be served by the pool.
     * @throws Exception if there is a problem creating a new instance,
     *    this will be propagated to the code requesting an object.
     */
    V makeObject(K key) throws Exception;

    /**
     * Destroy an instance no longer needed by the pool.
     * <p>
     * It is important for implementations of this method to be aware
     * that there is no guarantee about what state <code>obj</code>
     * will be in and the implementation should be prepared to handle
     * unexpected errors.
     * </p>
     * <p>
     * Also, an implementation must take in to consideration that
     * instances lost to the garbage collector may never be destroyed.
     * </p>
     *
     * @param key the key used when selecting the instance
     * @param obj the instance to be destroyed
     * @throws Exception should be avoided as it may be swallowed by
     *    the pool implementation.
     * @see #validateObject
     * @see KeyedObjectPool#invalidateObject
     */
    void destroyObject(K key, V obj) throws Exception;

    /**
     * Ensures that the instance is safe to be returned by the pool.
     * Returns <code>false</code> if <code>obj</code> should be destroyed.
     *
     * @param key the key used when selecting the object
     * @param obj the instance to be validated
     * @return <code>false</code> if <code>obj</code> is not valid and should
     *         be dropped from the pool, <code>true</code> otherwise.
     */
    boolean validateObject(K key, V obj);

    /**
     * Reinitialize an instance to be returned by the pool.
     *
     * @param key the key used when selecting the object
     * @param obj the instance to be activated
     * @throws Exception if there is a problem activating <code>obj</code>,
     *    this exception may be swallowed by the pool.
     * @see #destroyObject
     */
    void activateObject(K key, V obj) throws Exception;

    /**
     * Uninitialize an instance to be returned to the idle object pool.
     *
     * @param key the key used when selecting the object
     * @param obj the instance to be passivated
     * @throws Exception if there is a problem passivating <code>obj</code>,
     *    this exception may be swallowed by the pool.
     * @see #destroyObject
     */
    void passivateObject(K key, V obj) throws Exception;
}

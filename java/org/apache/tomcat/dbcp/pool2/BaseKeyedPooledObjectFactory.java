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
 * A base implementation of <code>KeyedPooledObjectFactory</code>.
 * <p>
 * All operations defined here are essentially no-op's.
 * </p>
 * This class is immutable, and therefore thread-safe.
 *
 * @see KeyedPooledObjectFactory
 *
 * @param <K> The type of keys managed by this factory.
 * @param <V> Type of element managed by this factory.
 *
 * @since 2.0
 */
public abstract class BaseKeyedPooledObjectFactory<K, V> extends BaseObject
        implements KeyedPooledObjectFactory<K, V> {

    /**
     * Create an instance that can be served by the pool.
     *
     * @param key the key used when constructing the object
     * @return an instance that can be served by the pool
     *
     * @throws Exception if there is a problem creating a new instance,
     *    this will be propagated to the code requesting an object.
     */
    public abstract V create(K key)
        throws Exception;

    /**
     * Wrap the provided instance with an implementation of
     * {@link PooledObject}.
     *
     * @param value the instance to wrap
     *
     * @return The provided instance, wrapped by a {@link PooledObject}
     */
    public abstract PooledObject<V> wrap(V value);

    @Override
    public PooledObject<V> makeObject(final K key) throws Exception {
        return wrap(create(key));
    }

    /**
     * Destroy an instance no longer needed by the pool.
     * <p>
     * The default implementation is a no-op.
     * </p>
     *
     * @param key the key used when selecting the instance
     * @param p a {@code PooledObject} wrapping the instance to be destroyed
     */
    @Override
    public void destroyObject(final K key, final PooledObject<V> p)
        throws Exception {
        // The default implementation is a no-op.
    }

    /**
     * Ensures that the instance is safe to be returned by the pool.
     * <p>
     * The default implementation always returns {@code true}.
     * </p>
     *
     * @param key the key used when selecting the object
     * @param p a {@code PooledObject} wrapping the instance to be validated
     * @return always <code>true</code> in the default implementation
     */
    @Override
    public boolean validateObject(final K key, final PooledObject<V> p) {
        return true;
    }

    /**
     * Reinitialize an instance to be returned by the pool.
     * <p>
     * The default implementation is a no-op.
     * </p>
     *
     * @param key the key used when selecting the object
     * @param p a {@code PooledObject} wrapping the instance to be activated
     */
    @Override
    public void activateObject(final K key, final PooledObject<V> p)
        throws Exception {
        // The default implementation is a no-op.
    }

    /**
     * Uninitialize an instance to be returned to the idle object pool.
     * <p>
     * The default implementation is a no-op.
     * </p>
     *
     * @param key the key used when selecting the object
     * @param p a {@code PooledObject} wrapping the instance to be passivated
     */
    @Override
    public void passivateObject(final K key, final PooledObject<V> p)
        throws Exception {
        // The default implementation is a no-op.
    }
}

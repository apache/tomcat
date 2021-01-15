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
 * An interface defining life-cycle methods for instances to be served by an
 * {@link ObjectPool}.
 * <p>
 * By contract, when an {@link ObjectPool} delegates to a
 * {@link PooledObjectFactory},
 * </p>
 * <ol>
 *  <li>
 *   {@link #makeObject} is called whenever a new instance is needed.
 *  </li>
 *  <li>
 *   {@link #activateObject} is invoked on every instance that has been
 *   {@link #passivateObject passivated} before it is
 *   {@link ObjectPool#borrowObject borrowed} from the pool.
 *  </li>
 *  <li>
 *   {@link #validateObject} may be invoked on {@link #activateObject activated}
 *   instances to make sure they can be {@link ObjectPool#borrowObject borrowed}
 *   from the pool. {@link #validateObject} may also be used to
 *   test an instance being {@link ObjectPool#returnObject returned} to the pool
 *   before it is {@link #passivateObject passivated}. It will only be invoked
 *   on an activated instance.
 *  </li>
 *  <li>
 *   {@link #passivateObject} is invoked on every instance when it is returned
 *   to the pool.
 *  </li>
 *  <li>
 *   {@link #destroyObject} is invoked on every instance when it is being
 *   "dropped" from the pool (whether due to the response from
 *   {@link #validateObject}, or for reasons specific to the pool
 *   implementation.) There is no guarantee that the instance being destroyed
 *   will be considered active, passive or in a generally consistent state.
 *  </li>
 * </ol>
 * {@link PooledObjectFactory} must be thread-safe. The only promise
 * an {@link ObjectPool} makes is that the same instance of an object will not
 * be passed to more than one method of a {@code PoolableObjectFactory}
 * at a time.
 * <p>
 * While clients of a {@link KeyedObjectPool} borrow and return instances of
 * the underlying value type {@code V}, the factory methods act on instances of
 * {@link PooledObject PooledObject&lt;V&gt;}.  These are the object wrappers that
 * pools use to track and maintain state information about the objects that
 * they manage.
 * </p>
 *
 * @param <T> Type of element managed in this factory.
 *
 * @see ObjectPool
 *
 * @since 2.0
 */
public interface PooledObjectFactory<T> {

  /**
   * Creates an instance that can be served by the pool and wrap it in a
   * {@link PooledObject} to be managed by the pool.
   *
   * @return a {@code PooledObject} wrapping an instance that can be served by the pool
   *
   * @throws Exception if there is a problem creating a new instance,
   *    this will be propagated to the code requesting an object.
   */
  PooledObject<T> makeObject() throws Exception;

  /**
   * Destroys an instance no longer needed by the pool, using the default (NORMAL)
   * DestroyMode.
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
   * @param p a {@code PooledObject} wrapping the instance to be destroyed
   *
   * @throws Exception should be avoided as it may be swallowed by
   *    the pool implementation.
   *
   * @see #validateObject
   * @see ObjectPool#invalidateObject
   */
  void destroyObject(PooledObject<T> p) throws Exception;

  /**
   * Destroys an instance no longer needed by the pool, using the provided
   * DestroyMode.
   *
   * @param p a {@code PooledObject} wrapping the instance to be destroyed
   * @param mode DestroyMode providing context to the factory
   *
   * @throws Exception should be avoided as it may be swallowed by
   *    the pool implementation.
   *
   * @see #validateObject
   * @see ObjectPool#invalidateObject
   * @see #destroyObject(PooledObject)
   * @see DestroyMode
   * @since 2.9.0
   */
  void destroyObject(final PooledObject<T> p, final DestroyMode mode) throws Exception;

  /**
   * Ensures that the instance is safe to be returned by the pool.
   *
   * @param p a {@code PooledObject} wrapping the instance to be validated
   *
   * @return {@code false} if {@code obj} is not valid and should
   *         be dropped from the pool, {@code true} otherwise.
   */
  boolean validateObject(PooledObject<T> p);

  /**
   * Reinitializes an instance to be returned by the pool.
   *
   * @param p a {@code PooledObject} wrapping the instance to be activated
   *
   * @throws Exception if there is a problem activating {@code obj},
   *    this exception may be swallowed by the pool.
   *
   * @see #destroyObject
   */
  void activateObject(PooledObject<T> p) throws Exception;

  /**
   * Uninitializes an instance to be returned to the idle object pool.
   *
   * @param p a {@code PooledObject} wrapping the instance to be passivated
   *
   * @throws Exception if there is a problem passivating {@code obj},
   *    this exception may be swallowed by the pool.
   *
   * @see #destroyObject
   */
  void passivateObject(PooledObject<T> p) throws Exception;
}

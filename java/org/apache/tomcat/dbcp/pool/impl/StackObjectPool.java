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

package org.apache.tomcat.dbcp.pool.impl;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Stack;

import org.apache.tomcat.dbcp.pool.BaseObjectPool;
import org.apache.tomcat.dbcp.pool.ObjectPool;
import org.apache.tomcat.dbcp.pool.PoolUtils;
import org.apache.tomcat.dbcp.pool.PoolableObjectFactory;

/**
 * A simple, {@link java.util.Stack Stack}-based {@link ObjectPool} implementation.
 * <p>
 * Given a {@link PoolableObjectFactory}, this class will maintain
 * a simple pool of instances.  A finite number of "sleeping"
 * or idle instances is enforced, but when the pool is
 * empty, new instances are created to support the new load.
 * Hence this class places no limit on the number of "active"
 * instances created by the pool, but is quite useful for
 * re-using <code>Object</code>s without introducing
 * artificial limits.
 *
 * @param <T> the type of objects held in this pool
 *
 * @author Rodney Waldhoff
 * @author Dirk Verbeeck
 * @author Sandy McArthur
 * @since Pool 1.0
 */
public class StackObjectPool<T> extends BaseObjectPool<T> {
    /**
     * Create a new pool using no factory. Clients must first
     * {@link #setFactory(PoolableObjectFactory) set the factory} or
     * else this pool will not behave correctly. Clients may first populate the pool
     * using {@link #returnObject(java.lang.Object)} before they can be {@link #borrowObject borrowed}
     * but this usage is <strong>discouraged</strong>.
     *
     * @see #StackObjectPool(PoolableObjectFactory)
     * @deprecated to be removed in pool 2.0 - use {@link #StackObjectPool(PoolableObjectFactory)}
     */
    @Deprecated
    public StackObjectPool() {
        this(null,DEFAULT_MAX_SLEEPING,DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new pool using no factory.
     * Clients must first {@link #setFactory(PoolableObjectFactory) set the factory} or
     * else this pool will not behave correctly. Clients may first populate the pool
     * using {@link #returnObject(java.lang.Object)} before they can be {@link #borrowObject borrowed}
     * but this usage is <strong>discouraged</strong>.
     *
     * @param maxIdle cap on the number of "sleeping" instances in the pool
     * @see #StackObjectPool(PoolableObjectFactory, int)
     * @deprecated to be removed in pool 2.0 - use {@link #StackObjectPool(PoolableObjectFactory, int)}
     */
    @Deprecated
    public StackObjectPool(int maxIdle) {
        this(null,maxIdle,DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new pool using no factory.
     * Clients must first {@link #setFactory(PoolableObjectFactory) set the factory} or
     * else this pool will not behave correctly. Clients may first populate the pool
     * using {@link #returnObject(java.lang.Object)} before they can be {@link #borrowObject borrowed}
     * but this usage is <strong>discouraged</strong>.
     *
     * @param maxIdle cap on the number of "sleeping" instances in the pool
     * @param initIdleCapacity initial size of the pool (this specifies the size of the container,
     *             it does not cause the pool to be pre-populated.)
     * @see #StackObjectPool(PoolableObjectFactory, int, int)
     * @deprecated to be removed in pool 2.0 - use {@link #StackObjectPool(PoolableObjectFactory, int, int)}
     */
    @Deprecated
    public StackObjectPool(int maxIdle, int initIdleCapacity) {
        this(null,maxIdle,initIdleCapacity);
    }

    /**
     * Create a new <code>StackObjectPool</code> using the specified <i>factory</i> to create new instances.
     *
     * @param factory the {@link PoolableObjectFactory} used to populate the pool
     */
    public StackObjectPool(PoolableObjectFactory<T> factory) {
        this(factory,DEFAULT_MAX_SLEEPING,DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new <code>SimpleObjectPool</code> using the specified <i>factory</i> to create new instances,
     * capping the number of "sleeping" instances to <i>maxIdle</i>.
     *
     * @param factory the {@link PoolableObjectFactory} used to populate the pool
     * @param maxIdle cap on the number of "sleeping" instances in the pool
     */
    public StackObjectPool(PoolableObjectFactory<T> factory, int maxIdle) {
        this(factory,maxIdle,DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * <p>Create a new <code>StackObjectPool</code> using the specified <code>factory</code> to create new instances,
     * capping the number of "sleeping" instances to <code>maxIdle</code>, and initially allocating a container
     * capable of containing at least <code>initIdleCapacity</code> instances.  The pool is not pre-populated.
     * The <code>initIdleCapacity</code> parameter just determines the initial size of the underlying
     * container, which can increase beyond this value if <code>maxIdle &gt; initIdleCapacity.</code></p>
     *
     * <p>Negative values of <code>maxIdle</code> are ignored (i.e., the pool is created using
     * {@link #DEFAULT_MAX_SLEEPING}) as are non-positive values for <code>initIdleCapacity.</code>
     *
     * @param factory the {@link PoolableObjectFactory} used to populate the pool
     * @param maxIdle cap on the number of "sleeping" instances in the pool
     * @param initIdleCapacity initial size of the pool (this specifies the size of the container,
     *             it does not cause the pool to be pre-populated.)
     */
    public StackObjectPool(PoolableObjectFactory<T> factory, int maxIdle, int initIdleCapacity) {
        _factory = factory;
        _maxSleeping = (maxIdle < 0 ? DEFAULT_MAX_SLEEPING : maxIdle);
        int initcapacity = (initIdleCapacity < 1 ? DEFAULT_INIT_SLEEPING_CAPACITY : initIdleCapacity);
        _pool = new Stack<T>();
        _pool.ensureCapacity( initcapacity > _maxSleeping ? _maxSleeping : initcapacity);
    }

    /**
     * <p>Borrows an object from the pool.  If there are idle instances available on the stack,
     * the top element of the stack is popped to activate, validate and return to the client.  If there
     * are no idle instances available, the {@link PoolableObjectFactory#makeObject() makeObject}
     * method of the pool's {@link PoolableObjectFactory} is invoked to create a new instance.</p>
     *
     * <p>All instances are {@link PoolableObjectFactory#activateObject(Object) activated} and
     * {@link PoolableObjectFactory#validateObject(Object) validated} before being returned to the
     * client.  If validation fails or an exception occurs activating or validating an instance
     * popped from the idle instance stack, the failing instance is
     * {@link PoolableObjectFactory#destroyObject(Object) destroyed} and the next instance on
     * the stack is popped, validated and activated.  This process continues until either the
     * stack is empty or an instance passes validation.  If the stack is empty on activation or
     * it does not contain any valid instances, the factory's <code>makeObject</code> method is used
     * to create a new instance.  If a null instance is returned by the factory or the created
     * instance either raises an exception on activation or fails validation, <code>NoSuchElementException</code>
     * is thrown. Exceptions thrown by <code>MakeObject</code> are propagated to the caller; but
     * other than <code>ThreadDeath</code> or <code>VirtualMachineError</code>, exceptions generated by
     * activation, validation or destroy methods are swallowed silently.</p>
     *
     * @return an instance from the pool
     */
    @Override
    public synchronized T borrowObject() throws Exception {
        assertOpen();
        T obj = null;
        boolean newlyCreated = false;
        while (null == obj) {
            if (!_pool.empty()) {
                obj = _pool.pop();
            } else {
                if(null == _factory) {
                    throw new NoSuchElementException();
                } else {
                    obj = _factory.makeObject();
                    newlyCreated = true;
                  if (obj == null) {
                    throw new NoSuchElementException("PoolableObjectFactory.makeObject() returned null.");
                  }
                }
            }
            if (null != _factory && null != obj) {
                try {
                    _factory.activateObject(obj);
                    if (!_factory.validateObject(obj)) {
                        throw new Exception("ValidateObject failed");
                    }
                } catch (Throwable t) {
                    PoolUtils.checkRethrow(t);
                    try {
                        _factory.destroyObject(obj);
                    } catch (Throwable t2) {
                        PoolUtils.checkRethrow(t2);
                        // swallowed
                    } finally {
                        obj = null;
                    }
                    if (newlyCreated) {
                        throw new NoSuchElementException(
                            "Could not create a validated object, cause: " +
                            t.getMessage());
                    }
                }
            }
        }
        _numActive++;
        return obj;
    }

    /**
     * <p>Returns an instance to the pool, pushing it on top of the idle instance stack after successful
     * validation and passivation. The returning instance is destroyed if any of the following are true:<ul>
     *   <li>the pool is closed</li>
     *   <li>{@link PoolableObjectFactory#validateObject(Object) validation} fails</li>
     *   <li>{@link PoolableObjectFactory#passivateObject(Object) passivation} throws an exception</li>
     * </ul>
     * If adding a validated, passivated returning instance to the stack would cause
     * {@link #getMaxSleeping() maxSleeping} to be exceeded, the oldest (bottom) instance on the stack
     * is destroyed to make room for the returning instance, which is pushed on top of the stack.
     *
     * <p>Exceptions passivating or destroying instances are silently swallowed.  Exceptions validating
     * instances are propagated to the client.</p>
     *
     * @param obj instance to return to the pool
     */
    @Override
    public synchronized void returnObject(T obj) throws Exception {
        boolean success = !isClosed();
        if(null != _factory) {
            if(!_factory.validateObject(obj)) {
                success = false;
            } else {
                try {
                    _factory.passivateObject(obj);
                } catch(Exception e) {
                    success = false;
                }
            }
        }

        boolean shouldDestroy = !success;

        _numActive--;
        if (success) {
            T toBeDestroyed = null;
            if(_pool.size() >= _maxSleeping) {
                shouldDestroy = true;
                toBeDestroyed = _pool.remove(0); // remove the stalest object
            }
            _pool.push(obj);
            obj = toBeDestroyed; // swap returned obj with the stalest one so it can be destroyed
        }
        notifyAll(); // _numActive has changed

        if(shouldDestroy) { // by constructor, shouldDestroy is false when _factory is null
            try {
                _factory.destroyObject(obj);
            } catch(Exception e) {
                // ignored
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void invalidateObject(T obj) throws Exception {
        _numActive--;
        if (null != _factory) {
            _factory.destroyObject(obj);
        }
        notifyAll(); // _numActive has changed
    }

    /**
     * Return the number of instances
     * currently idle in this pool.
     *
     * @return the number of instances currently idle in this pool
     */
    @Override
    public synchronized int getNumIdle() {
        return _pool.size();
    }

    /**
     * Return the number of instances currently borrowed from this pool.
     *
     * @return the number of instances currently borrowed from this pool
     */
    @Override
    public synchronized int getNumActive() {
        return _numActive;
    }

    /**
     * Clears any objects sitting idle in the pool. Silently swallows any
     * exceptions thrown by {@link PoolableObjectFactory#destroyObject(Object)}.
     */
    @Override
    public synchronized void clear() {
        if(null != _factory) {
            Iterator<T> it = _pool.iterator();
            while(it.hasNext()) {
                try {
                    _factory.destroyObject(it.next());
                } catch(Exception e) {
                    // ignore error, keep destroying the rest
                }
            }
        }
        _pool.clear();
    }

    /**
     * <p>Close this pool, and free any resources associated with it. Invokes
     * {@link #clear()} to destroy and remove instances in the pool.</p>
     *
     * <p>Calling {@link #addObject} or {@link #borrowObject} after invoking
     * this method on a pool will cause them to throw an
     * {@link IllegalStateException}.</p>
     *
     * @throws Exception never - exceptions clearing the pool are swallowed
     */
    @Override
    public void close() throws Exception {
        super.close();
        clear();
    }

    /**
     * <p>Create an object, and place it on top of the stack.
     * This method is useful for "pre-loading" a pool with idle objects.</p>
     *
     * <p>Before being added to the pool, the newly created instance is
     * {@link PoolableObjectFactory#validateObject(Object) validated} and
     * {@link PoolableObjectFactory#passivateObject(Object) passivated}.  If validation
     * fails, the new instance is {@link PoolableObjectFactory#destroyObject(Object) destroyed}.
     * Exceptions generated by the factory <code>makeObject</code> or <code>passivate</code> are
     * propagated to the caller. Exceptions destroying instances are silently swallowed.</p>
     *
     * <p>If a new instance is created and successfully validated and passivated and adding this
     * instance to the pool causes {@link #getMaxSleeping() maxSleeping} to be exceeded, the oldest
     * (bottom) instance in the pool is destroyed to make room for the newly created instance, which
     * is pushed on top of the stack.
     *
     * @throws Exception when the {@link #getFactory() factory} has a problem creating or passivating an object.
     */
    @Override
    public synchronized void addObject() throws Exception {
        assertOpen();
        if (_factory == null) {
            throw new IllegalStateException("Cannot add objects without a factory.");
        }
        T obj = _factory.makeObject();

        boolean success = true;
        if(!_factory.validateObject(obj)) {
            success = false;
        } else {
            _factory.passivateObject(obj);
        }

        boolean shouldDestroy = !success;

        if (success) {
            T toBeDestroyed = null;
            if(_pool.size() >= _maxSleeping) {
                shouldDestroy = true;
                toBeDestroyed = _pool.remove(0); // remove the stalest object
            }
            _pool.push(obj);
            obj = toBeDestroyed; // swap returned obj with the stalest one so it can be destroyed
        }
        notifyAll(); // _numIdle has changed

        if(shouldDestroy) { // by constructor, shouldDestroy is false when _factory is null
            try {
                _factory.destroyObject(obj);
            } catch(Exception e) {
                // ignored
            }
        }
    }

    /**
     * Sets the {@link PoolableObjectFactory factory} this pool uses
     * to create new instances. Trying to change
     * the <code>factory</code> while there are borrowed objects will
     * throw an {@link IllegalStateException}.
     *
     * @param factory the {@link PoolableObjectFactory} used to create new instances.
     * @throws IllegalStateException when the factory cannot be set at this time
     * @deprecated to be removed in pool 2.0
     */
    @Deprecated
    @Override
    public synchronized void setFactory(PoolableObjectFactory<T> factory) throws IllegalStateException {
        assertOpen();
        if(0 < getNumActive()) {
            throw new IllegalStateException("Objects are already active");
        } else {
            clear();
            _factory = factory;
        }
    }

    /**
     * The cap on the number of "sleeping" instances in the pool.
     */
    protected static final int DEFAULT_MAX_SLEEPING  = 8;

    /**
     * The default initial size of the pool
     * (this specifies the size of the container, it does not
     * cause the pool to be pre-populated.)
     */
    protected static final int DEFAULT_INIT_SLEEPING_CAPACITY = 4;

    /**
     * My pool.
     * @deprecated to be made private in pool 2.0
     */
    @Deprecated
    protected Stack<T> _pool = null;

    /**
     * My {@link PoolableObjectFactory}.
     * @deprecated to be made private in pool 2.0 - use {@link #getFactory()}
     */
    @Deprecated
    protected PoolableObjectFactory<T> _factory = null;

    /**
     * The cap on the number of "sleeping" instances in the pool.
     * @deprecated to be made private in pool 2.0 - use {@link #getMaxSleeping()}
     */
    @Deprecated
    protected int _maxSleeping = DEFAULT_MAX_SLEEPING;

    /**
     * Number of objects borrowed but not yet returned to the pool.
     * @deprecated to be made private in pool 2.0 - use {@link #getNumActive()}
     */
    @Deprecated
    protected int _numActive = 0;

    /**
     * Returns the {@link PoolableObjectFactory} used by this pool to create and manage object instances.
     *
     * @return the factory
     * @since 1.5.5
     */
    public synchronized PoolableObjectFactory<T> getFactory() {
        return _factory;
    }

    /**
     * Returns the maximum number of idle instances in the pool.
     *
     * @return maxSleeping
     * @since 1.5.5
     */
    public int getMaxSleeping() {
        return _maxSleeping;
    }


}

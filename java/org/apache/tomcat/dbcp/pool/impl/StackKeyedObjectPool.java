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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;

import org.apache.tomcat.dbcp.pool.BaseKeyedObjectPool;
import org.apache.tomcat.dbcp.pool.KeyedPoolableObjectFactory;
import org.apache.tomcat.dbcp.pool.PoolUtils;

/**
 * A simple, <code>Stack</code>-based <code>KeyedObjectPool</code> implementation.
 * <p>
 * Given a {@link KeyedPoolableObjectFactory}, this class will maintain
 * a simple pool of instances.  A finite number of "sleeping"
 * or inactive instances is enforced, but when the pool is
 * empty, new instances are created to support the new load.
 * Hence this class places no limit on the number of "active"
 * instances created by the pool, but is quite useful for
 * re-using <code>Object</code>s without introducing
 * artificial limits.
 * </p>
 *
 * @param <K> the type of keys in this pool
 * @param <V> the type of objects held in this pool
 *
 * @author Rodney Waldhoff
 * @author Sandy McArthur
 * @see Stack
 * @since Pool 1.0
 */
public class StackKeyedObjectPool<K, V> extends BaseKeyedObjectPool<K, V> {
    /**
     * Create a new pool using no factory.
     * Clients must first set the {@link #setFactory factory} or
     * may populate the pool using {@link #returnObject returnObject}
     * before they can be {@link #borrowObject borrowed}.
     *
     * @see #StackKeyedObjectPool(KeyedPoolableObjectFactory)
     * @see #setFactory(KeyedPoolableObjectFactory)
     */
    public StackKeyedObjectPool() {
        this(null,DEFAULT_MAX_SLEEPING,DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new pool using no factory.
     * Clients must first set the {@link #setFactory factory} or
     * may populate the pool using {@link #returnObject returnObject}
     * before they can be {@link #borrowObject borrowed}.
     *
     * @param max cap on the number of "sleeping" instances in the pool
     * @see #StackKeyedObjectPool(KeyedPoolableObjectFactory, int)
     * @see #setFactory(KeyedPoolableObjectFactory)
     */
    public StackKeyedObjectPool(int max) {
        this(null,max,DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new pool using no factory.
     * Clients must first set the {@link #setFactory factory} or
     * may populate the pool using {@link #returnObject returnObject}
     * before they can be {@link #borrowObject borrowed}.
     *
     * @param max cap on the number of "sleeping" instances in the pool
     * @param init initial size of the pool (this specifies the size of the container,
     *             it does not cause the pool to be pre-populated.)
     * @see #StackKeyedObjectPool(KeyedPoolableObjectFactory, int, int)
     * @see #setFactory(KeyedPoolableObjectFactory)
     */
    public StackKeyedObjectPool(int max, int init) {
        this(null,max,init);
    }

    /**
     * Create a new <code>SimpleKeyedObjectPool</code> using
     * the specified <code>factory</code> to create new instances.
     *
     * @param factory the {@link KeyedPoolableObjectFactory} used to populate the pool
     */
    public StackKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory) {
        this(factory,DEFAULT_MAX_SLEEPING);
    }

    /**
     * Create a new <code>SimpleKeyedObjectPool</code> using
     * the specified <code>factory</code> to create new instances.
     * capping the number of "sleeping" instances to <code>max</code>
     *
     * @param factory the {@link KeyedPoolableObjectFactory} used to populate the pool
     * @param max cap on the number of "sleeping" instances in the pool
     */
    public StackKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int max) {
        this(factory,max,DEFAULT_INIT_SLEEPING_CAPACITY);
    }

    /**
     * Create a new <code>SimpleKeyedObjectPool</code> using
     * the specified <code>factory</code> to create new instances.
     * capping the number of "sleeping" instances to <code>max</code>,
     * and initially allocating a container capable of containing
     * at least <code>init</code> instances.
     *
     * @param factory the {@link KeyedPoolableObjectFactory} used to populate the pool
     * @param max cap on the number of "sleeping" instances in the pool
     * @param init initial size of the pool (this specifies the size of the container,
     *             it does not cause the pool to be pre-populated.)
     */
    public StackKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int max, int init) {
        _factory = factory;
        _maxSleeping = (max < 0 ? DEFAULT_MAX_SLEEPING : max);
        _initSleepingCapacity = (init < 1 ? DEFAULT_INIT_SLEEPING_CAPACITY : init);
        _pools = new HashMap<K, Stack<V>>();
        _activeCount = new HashMap<K, Integer>();
    }

    /**
     * Borrows an object with the given key.  If there are no idle instances under the
     * given key, a new one is created.
     *
     * @param key the pool key
     * @return keyed poolable object instance
     */
    @Override
    public synchronized V borrowObject(K key) throws Exception {
        assertOpen();
        Stack<V> stack = (_pools.get(key));
        if(null == stack) {
            stack = new Stack<V>();
            stack.ensureCapacity( _initSleepingCapacity > _maxSleeping ? _maxSleeping : _initSleepingCapacity);
            _pools.put(key,stack);
        }
        V obj = null;
        do {
            boolean newlyMade = false;
            if (!stack.empty()) {
                obj = stack.pop();
                _totIdle--;
            } else {
                if(null == _factory) {
                    throw new NoSuchElementException("pools without a factory cannot create new objects as needed.");
                } else {
                    obj = _factory.makeObject(key);
                    newlyMade = true;
                }
            }
            if (null != _factory && null != obj) {
                try {
                    _factory.activateObject(key, obj);
                    if (!_factory.validateObject(key, obj)) {
                        throw new Exception("ValidateObject failed");
                    }
                } catch (Throwable t) {
                    PoolUtils.checkRethrow(t);
                    try {
                        _factory.destroyObject(key,obj);
                    } catch (Throwable t2) {
                        PoolUtils.checkRethrow(t2);
                        // swallowed
                    } finally {
                        obj = null;
                    }
                    if (newlyMade) {
                        throw new NoSuchElementException(
                            "Could not create a validated object, cause: " +
                            t.getMessage());
                    }
                }
            }
        } while (obj == null);
        incrementActiveCount(key);
        return obj;
    }

    /**
     * Returns <code>obj</code> to the pool under <code>key</code>.  If adding the
     * returning instance to the pool results in {@link #_maxSleeping maxSleeping}
     * exceeded for the given key, the oldest instance in the idle object pool
     * is destroyed to make room for the returning instance.
     *
     * @param key the pool key
     * @param obj returning instance
     */
    @Override
    public synchronized void returnObject(K key, V obj) throws Exception {
        decrementActiveCount(key);
        if (null != _factory) {
            if (_factory.validateObject(key, obj)) {
                try {
                    _factory.passivateObject(key, obj);
                } catch (Exception ex) {
                    _factory.destroyObject(key, obj);
                    return;
                }
            } else {
                return;
            }
        }

        if (isClosed()) {
            if (null != _factory) {
                try {
                    _factory.destroyObject(key, obj);
                } catch (Exception e) {
                    // swallowed
                }
            }
            return;
        }

        Stack<V> stack = _pools.get(key);
        if(null == stack) {
            stack = new Stack<V>();
            stack.ensureCapacity( _initSleepingCapacity > _maxSleeping ? _maxSleeping : _initSleepingCapacity);
            _pools.put(key,stack);
        }
        final int stackSize = stack.size();
        if (stackSize >= _maxSleeping) {
            final V staleObj;
            if (stackSize > 0) {
                staleObj = stack.remove(0);
                _totIdle--;
            } else {
                staleObj = obj;
            }
            if(null != _factory) {
                try {
                    _factory.destroyObject(key, staleObj);
                } catch (Exception e) {
                    // swallowed
                }
            }
        }
        stack.push(obj);
        _totIdle++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void invalidateObject(K key, V obj) throws Exception {
        decrementActiveCount(key);
        if(null != _factory) {
            _factory.destroyObject(key,obj);
        }
        notifyAll(); // _totalActive has changed
    }

    /**
     * Create an object using the {@link KeyedPoolableObjectFactory#makeObject factory},
     * passivate it, and then placed in the idle object pool.
     * <code>addObject</code> is useful for "pre-loading" a pool with idle objects.
     *
     * @param key the key a new instance should be added to
     * @throws Exception when {@link KeyedPoolableObjectFactory#makeObject} fails.
     * @throws IllegalStateException when no {@link #setFactory factory} has been set or after {@link #close} has been called on this pool.
     */
    @Override
    public synchronized void addObject(K key) throws Exception {
        assertOpen();
        if (_factory == null) {
            throw new IllegalStateException("Cannot add objects without a factory.");
        }
        V obj = _factory.makeObject(key);
        try {
            if (!_factory.validateObject(key, obj)) {
               return;
            }
        } catch (Exception e) {
            try {
                _factory.destroyObject(key, obj);
            } catch (Exception e2) {
                // swallowed
            }
            return;
        }
        _factory.passivateObject(key, obj);

        Stack<V> stack = _pools.get(key);
        if(null == stack) {
            stack = new Stack<V>();
            stack.ensureCapacity( _initSleepingCapacity > _maxSleeping ? _maxSleeping : _initSleepingCapacity);
            _pools.put(key,stack);
        }

        final int stackSize = stack.size();
        if (stackSize >= _maxSleeping) {
            final V staleObj;
            if (stackSize > 0) {
                staleObj = stack.remove(0);
                _totIdle--;
            } else {
                staleObj = obj;
            }
            try {
                _factory.destroyObject(key, staleObj);
            } catch (Exception e) {
                // Don't swallow destroying the newly created object.
                if (obj == staleObj) {
                    throw e;
                }
            }
        } else {
            stack.push(obj);
            _totIdle++;
        }
    }

    /**
     * Returns the total number of instances currently idle in this pool.
     *
     * @return the total number of instances currently idle in this pool
     */
    @Override
    public synchronized int getNumIdle() {
        return _totIdle;
    }

    /**
     * Returns the total number of instances current borrowed from this pool but not yet returned.
     *
     * @return the total number of instances currently borrowed from this pool
     */
    @Override
    public synchronized int getNumActive() {
        return _totActive;
    }

    /**
     * Returns the number of instances currently borrowed from but not yet returned
     * to the pool corresponding to the given <code>key</code>.
     *
     * @param key the key to query
     * @return the number of instances corresponding to the given <code>key</code> currently borrowed in this pool
     */
    @Override
    public synchronized int getNumActive(K key) {
        return getActiveCount(key);
    }

    /**
     * Returns the number of instances corresponding to the given <code>key</code> currently idle in this pool.
     *
     * @param key the key to query
     * @return the number of instances corresponding to the given <code>key</code> currently idle in this pool
     */
    @Override
    public synchronized int getNumIdle(K key) {
        try {
            return(_pools.get(key)).size();
        } catch(Exception e) {
            return 0;
        }
    }

    /**
     * Clears the pool, removing all pooled instances.
     */
    @Override
    public synchronized void clear() {
        Iterator<K> it = _pools.keySet().iterator();
        while(it.hasNext()) {
            K key = it.next();
            Stack<V> stack = _pools.get(key);
            destroyStack(key,stack);
        }
        _totIdle = 0;
        _pools.clear();
        _activeCount.clear();
    }

    /**
     * Clears the specified pool, removing all pooled instances corresponding to the given <code>key</code>.
     *
     * @param key the key to clear
     */
    @Override
    public synchronized void clear(K key) {
        Stack<V> stack = _pools.remove(key);
        destroyStack(key,stack);
    }

    /**
     * Destroys all instances in the stack and clears the stack.
     *
     * @param key key passed to factory when destroying instances
     * @param stack stack to destroy
     */
    private synchronized void destroyStack(K key, Stack<V> stack) {
        if(null == stack) {
            return;
        } else {
            if(null != _factory) {
                Iterator<V> it = stack.iterator();
                while(it.hasNext()) {
                    try {
                        _factory.destroyObject(key,it.next());
                    } catch(Exception e) {
                        // ignore error, keep destroying the rest
                    }
                }
            }
            _totIdle -= stack.size();
            _activeCount.remove(key);
            stack.clear();
        }
    }

    /**
     * Returns a string representation of this StackKeyedObjectPool, including
     * the number of pools, the keys and the size of each keyed pool.
     *
     * @return Keys and pool sizes
     */
    @Override
    public synchronized String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append(getClass().getName());
        buf.append(" contains ").append(_pools.size()).append(" distinct pools: ");
        Iterator<K> it = _pools.keySet().iterator();
        while(it.hasNext()) {
            K key = it.next();
            buf.append(" |").append(key).append("|=");
            Stack<V> s = _pools.get(key);
            buf.append(s.size());
        }
        return buf.toString();
    }

    /**
     * Close this pool, and free any resources associated with it.
     * <p>
     * Calling {@link #addObject addObject} or {@link #borrowObject borrowObject} after invoking
     * this method on a pool will cause them to throw an {@link IllegalStateException}.
     * </p>
     *
     * @throws Exception <strong>deprecated</strong>: implementations should silently fail if not all resources can be freed.
     */
    @Override
    public void close() throws Exception {
        super.close();
        clear();
    }

    /**
     * Sets the {@link KeyedPoolableObjectFactory factory} the pool uses
     * to create new instances.
     * Trying to change the <code>factory</code> after a pool has been used will frequently
     * throw an {@link UnsupportedOperationException}.
     *
     * @param factory the {@link KeyedPoolableObjectFactory} used to manage object instances
     * @throws IllegalStateException when the factory cannot be set at this time
     * @deprecated to be removed in pool 2.0
     */
    @Deprecated
    @Override
    public synchronized void setFactory(KeyedPoolableObjectFactory<K, V> factory) throws IllegalStateException {
        if(0 < getNumActive()) {
            throw new IllegalStateException("Objects are already active");
        } else {
            clear();
            _factory = factory;
        }
    }

    /**
     * @return the {@link KeyedPoolableObjectFactory} used by this pool to manage object instances.
     * @since 1.5.5
     */
    public synchronized KeyedPoolableObjectFactory<K, V> getFactory() {
        return _factory;
    }

    /**
     * Returns the active instance count for the given key.
     *
     * @param key pool key
     * @return active count
     */
    private int getActiveCount(K key) {
        try {
            return _activeCount.get(key).intValue();
        } catch(NoSuchElementException e) {
            return 0;
        } catch(NullPointerException e) {
            return 0;
        }
    }

    /**
     * This value is created to be used in {@link #incrementActiveCount(Object)}
     * when we increment the active count for a key for the first time
     * (and the count becomes 1).
     * Since <code>Integer</code> is immutable, we can share this instance
     * for all such cases, rather than creating a new object instance each time.
     */
    private static final Integer ONE = Integer.valueOf(1);

    /**
     * Increment the active count for the given key. Also
     * increments the total active count.
     *
     * @param key pool key
     */
    private void incrementActiveCount(K key) {
        _totActive++;
        Integer old = _activeCount.get(key);
        if(null == old) {
            _activeCount.put(key,ONE);
        } else {
            _activeCount.put(key,Integer.valueOf(old.intValue() + 1));
        }
    }

    /**
     * Decrements the active count for the given key.
     * Also decrements the total active count.
     *
     * @param key pool key
     */
    private void decrementActiveCount(K key) {
        _totActive--;
        Integer active = _activeCount.get(key);
        if(null == active) {
            // do nothing, either null or zero is OK
        } else if(active.intValue() <= 1) {
            _activeCount.remove(key);
        } else {
            _activeCount.put(key, Integer.valueOf(active.intValue() - 1));
        }
    }


    /**
     * @return map of keyed pools
     * @since 1.5.5
     */
    public Map<K, Stack<V>> getPools() {
        return _pools;
    }

    /**
     * @return the cap on the number of "sleeping" instances in <code>each</code> pool.
     * @since 1.5.5
     */
    public int getMaxSleeping() {
        return _maxSleeping;
    }

    /**
     * @return the initial capacity of each pool.
     * @since 1.5.5
     */
    public int getInitSleepingCapacity() {
        return _initSleepingCapacity;
    }

    /**
     * @return the _totActive
     */
    public int getTotActive() {
        return _totActive;
    }

    /**
     * @return the _totIdle
     */
    public int getTotIdle() {
        return _totIdle;
    }

    /**
     * @return the _activeCount
     * @since 1.5.5
     */
    public Map<K, Integer> getActiveCount() {
        return _activeCount;
    }


    /** The default cap on the number of "sleeping" instances in the pool. */
    protected static final int DEFAULT_MAX_SLEEPING  = 8;

    /**
     * The default initial size of the pool
     * (this specifies the size of the container, it does not
     * cause the pool to be pre-populated.)
     */
    protected static final int DEFAULT_INIT_SLEEPING_CAPACITY = 4;

    /**
     *  My named-set of pools.
     *  @deprecated to be removed in pool 2.0.  Use {@link #getPools()}
     */
    @Deprecated
    protected HashMap<K, Stack<V>> _pools = null;

    /**
     * My {@link KeyedPoolableObjectFactory}.
     * @deprecated to be removed in pool 2.0.  Use {@link #getFactory()}
     */
    @Deprecated
    protected KeyedPoolableObjectFactory<K, V> _factory = null;

    /**
     *  The cap on the number of "sleeping" instances in <code>each</code> pool.
     *  @deprecated to be removed in pool 2.0.  Use {@link #getMaxSleeping()}
     */
    @Deprecated
    protected int _maxSleeping = DEFAULT_MAX_SLEEPING;

    /**
     * The initial capacity of each pool.
     * @deprecated to be removed in pool 2.0.  Use {@link #getInitSleepingCapacity()}.
     */
    @Deprecated
    protected int _initSleepingCapacity = DEFAULT_INIT_SLEEPING_CAPACITY;

    /**
     * Total number of object borrowed and not yet returned for all pools.
     * @deprecated to be removed in pool 2.0.  Use {@link #getTotActive()}.
     */
    @Deprecated
    protected int _totActive = 0;

    /**
     * Total number of objects "sleeping" for all pools
     * @deprecated to be removed in pool 2.0.  Use {@link #getTotIdle()}.
     */
    @Deprecated
    protected int _totIdle = 0;

    /**
     * Number of active objects borrowed and not yet returned by pool
     * @deprecated to be removed in pool 2.0.  Use {@link #getActiveCount()}.
     */
    @Deprecated
    protected HashMap<K, Integer> _activeCount = null;

}

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
package org.apache.tomcat.dbcp.pool2.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.apache.tomcat.dbcp.pool2.DestroyMode;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.KeyedPooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PoolUtils;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectState;
import org.apache.tomcat.dbcp.pool2.SwallowedExceptionListener;
import org.apache.tomcat.dbcp.pool2.UsageTracking;

/**
 * A configurable {@code KeyedObjectPool} implementation.
 * <p>
 * When coupled with the appropriate {@link KeyedPooledObjectFactory},
 * {@code GenericKeyedObjectPool} provides robust pooling functionality for
 * keyed objects. A {@code GenericKeyedObjectPool} can be viewed as a map
 * of sub-pools, keyed on the (unique) key values provided to the
 * {@link #preparePool preparePool}, {@link #addObject addObject} or
 * {@link #borrowObject borrowObject} methods. Each time a new key value is
 * provided to one of these methods, a sub-new pool is created under the given
 * key to be managed by the containing {@code GenericKeyedObjectPool.}
 * </p>
 * <p>
 * Note that the current implementation uses a ConcurrentHashMap which uses
 * equals() to compare keys.
 * This means that distinct instance keys must be distinguishable using equals.
 * </p>
 * <p>
 * Optionally, one may configure the pool to examine and possibly evict objects
 * as they sit idle in the pool and to ensure that a minimum number of idle
 * objects is maintained for each key. This is performed by an "idle object
 * eviction" thread, which runs asynchronously. Caution should be used when
 * configuring this optional feature. Eviction runs contend with client threads
 * for access to objects in the pool, so if they run too frequently performance
 * issues may result.
 * </p>
 * <p>
 * Implementation note: To prevent possible deadlocks, care has been taken to
 * ensure that no call to a factory method will occur within a synchronization
 * block. See POOL-125 and DBCP-44 for more information.
 * </p>
 * <p>
 * This class is intended to be thread-safe.
 * </p>
 *
 * @see GenericObjectPool
 *
 * @param <K> The type of keys maintained by this pool.
 * @param <T> Type of element pooled in this pool.
 *
 * @since 2.0
 */
public class GenericKeyedObjectPool<K, T> extends BaseGenericObjectPool<T>
        implements KeyedObjectPool<K, T>, GenericKeyedObjectPoolMXBean<K>, UsageTracking<T> {

    /**
     * Maintains information on the per key queue for a given key.
     *
     * @param <S> type of objects in the pool
     */
    private static class ObjectDeque<S> {

        private final LinkedBlockingDeque<PooledObject<S>> idleObjects;

        /*
         * Number of instances created - number destroyed.
         * Invariant: createCount <= maxTotalPerKey
         */
        private final AtomicInteger createCount = new AtomicInteger(0);

        private long makeObjectCount;
        private final Object makeObjectCountLock = new Object();

        /*
         * The map is keyed on pooled instances, wrapped to ensure that
         * they work properly as keys.
         */
        private final Map<IdentityWrapper<S>, PooledObject<S>> allObjects =
                new ConcurrentHashMap<>();

        /*
         * Number of threads with registered interest in this key.
         * register(K) increments this counter and deRegister(K) decrements it.
         * Invariant: empty keyed pool will not be dropped unless numInterested
         *            is 0.
         */
        private final AtomicLong numInterested = new AtomicLong();

        /**
         * Constructs a new ObjectDeque with the given fairness policy.
         * @param fairness true means client threads waiting to borrow / return instances
         * will be served as if waiting in a FIFO queue.
         */
        ObjectDeque(final boolean fairness) {
            idleObjects = new LinkedBlockingDeque<>(fairness);
        }

        /**
         * Gets all the objects for the current key.
         *
         * @return All the objects
         */
        public Map<IdentityWrapper<S>, PooledObject<S>> getAllObjects() {
            return allObjects;
        }

        /**
         * Gets the number of instances created - number destroyed.
         * Should always be less than or equal to maxTotalPerKey.
         *
         * @return The net instance addition count for this deque
         */
        public AtomicInteger getCreateCount() {
            return createCount;
        }

        /**
         * Gets the idle objects for the current key.
         *
         * @return The idle objects
         */
        public LinkedBlockingDeque<PooledObject<S>> getIdleObjects() {
            return idleObjects;
        }

        /**
         * Gets the number of threads with an interest registered in this key.
         *
         * @return The number of threads with a registered interest in this key
         */
        public AtomicLong getNumInterested() {
            return numInterested;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("ObjectDeque [idleObjects=");
            builder.append(idleObjects);
            builder.append(", createCount=");
            builder.append(createCount);
            builder.append(", allObjects=");
            builder.append(allObjects);
            builder.append(", numInterested=");
            builder.append(numInterested);
            builder.append("]");
            return builder.toString();
        }

    }

    private static final Integer ZERO = Integer.valueOf(0);

    // JMX specific attributes
    private static final String ONAME_BASE =
            "org.apache.commons.pool2:type=GenericKeyedObjectPool,name=";

    private volatile int maxIdlePerKey =
            GenericKeyedObjectPoolConfig.DEFAULT_MAX_IDLE_PER_KEY;

    private volatile int minIdlePerKey =
            GenericKeyedObjectPoolConfig.DEFAULT_MIN_IDLE_PER_KEY;


    private volatile int maxTotalPerKey =
            GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL_PER_KEY;

    private final KeyedPooledObjectFactory<K, T> factory;

    private final boolean fairness;

    /*
     * My hash of sub-pools (ObjectQueue). The list of keys <b>must</b> be kept
     * in step with {@link #poolKeyList} using {@link #keyLock} to ensure any
     * changes to the list of current keys is made in a thread-safe manner.
     */
    private final Map<K, ObjectDeque<T>> poolMap =
            new ConcurrentHashMap<>(); // @GuardedBy("keyLock") for write access (and some read access)

    /*
     * List of pool keys - used to control eviction order. The list of keys
     * <b>must</b> be kept in step with {@link #poolMap} using {@link #keyLock}
     * to ensure any changes to the list of current keys is made in a
     * thread-safe manner.
     */
    private final ArrayList<K> poolKeyList = new ArrayList<>(); // @GuardedBy("keyLock")

    private final ReadWriteLock keyLock = new ReentrantReadWriteLock(true);

    /*
     * The combined count of the currently active objects for all keys and those
     * in the process of being created. Under load, it may exceed
     * {@link #maxTotal} but there will never be more than {@link #maxTotal}
     * created at any one time.
     */
    private final AtomicInteger numTotal = new AtomicInteger(0);

    private Iterator<K> evictionKeyIterator; // @GuardedBy("evictionLock")


    private K evictionKey; // @GuardedBy("evictionLock")

    /**
     * Constructs a new {@code GenericKeyedObjectPool} using defaults from
     * {@link GenericKeyedObjectPoolConfig}.
     * @param factory the factory to be used to create entries
     */
    public GenericKeyedObjectPool(final KeyedPooledObjectFactory<K, T> factory) {
        this(factory, new GenericKeyedObjectPoolConfig<>());
    }

    /**
     * Constructs a new {@code GenericKeyedObjectPool} using a specific
     * configuration.
     *
     * @param factory the factory to be used to create entries
     * @param config    The configuration to use for this pool instance. The
     *                  configuration is used by value. Subsequent changes to
     *                  the configuration object will not be reflected in the
     *                  pool.
     */
    public GenericKeyedObjectPool(final KeyedPooledObjectFactory<K, T> factory,
            final GenericKeyedObjectPoolConfig<T> config) {

        super(config, ONAME_BASE, config.getJmxNamePrefix());

        if (factory == null) {
            jmxUnregister(); // tidy up
            throw new IllegalArgumentException("Factory may not be null");
        }
        this.factory = factory;
        this.fairness = config.getFairness();

        setConfig(config);
    }

    /**
     * Creates a new {@code GenericKeyedObjectPool} that tracks and destroys
     * objects that are checked out, but never returned to the pool.
     *
     * @param factory   The object factory to be used to create object instances
     *                  used by this pool
     * @param config    The base pool configuration to use for this pool instance.
     *                  The configuration is used by value. Subsequent changes to
     *                  the configuration object will not be reflected in the
     *                  pool.
     * @param abandonedConfig  Configuration for abandoned object identification
     *                         and removal.  The configuration is used by value.
     * @since 2.10.0
     */
    public GenericKeyedObjectPool(final KeyedPooledObjectFactory<K, T> factory,
            final GenericKeyedObjectPoolConfig<T> config, final AbandonedConfig abandonedConfig) {
        this(factory, config);
        setAbandonedConfig(abandonedConfig);
    }

    /**
     * Add an object to the set of idle objects for a given key.
     * If the object is null this is a no-op.
     *
     * @param key The key to associate with the idle object
     * @param p The wrapped object to add.
     *
     * @throws Exception If the associated factory fails to passivate the object
     */
    private void addIdleObject(final K key, final PooledObject<T> p) throws Exception {
        if (!PooledObject.isNull(p)) {
            factory.passivateObject(key, p);
            final LinkedBlockingDeque<PooledObject<T>> idleObjects = poolMap.get(key).getIdleObjects();
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
    }

    /**
     * Create an object using the {@link KeyedPooledObjectFactory#makeObject
     * factory}, passivate it, and then place it in the idle object pool.
     * {@code addObject} is useful for "pre-loading" a pool with idle
     * objects.
     * <p>
     * If there is no capacity available to add to the pool under the given key,
     * this is a no-op (no exception, no impact to the pool).
     * </p>
     * <p>
     * If the factory returns null when creating an instance,
     * a {@code NullPointerException} is thrown.
     * </p>
     *
     * @param key the key a new instance should be added to
     *
     * @throws Exception when {@link KeyedPooledObjectFactory#makeObject}
     *                   fails.
     */
    @Override
    public void addObject(final K key) throws Exception {
        assertOpen();
        register(key);
        try {
            addIdleObject(key, create(key));
        } finally {
            deregister(key);
        }
    }

    /**
     * Equivalent to <code>{@link #borrowObject(Object, long) borrowObject}(key,
     * {@link #getMaxWaitDuration()})</code>.
     *
     * {@inheritDoc}
     */
    @Override
    public T borrowObject(final K key) throws Exception {
        return borrowObject(key, getMaxWaitDuration().toMillis());
    }

    /**
     * Borrows an object from the sub-pool associated with the given key using
     * the specified waiting time which only applies if
     * {@link #getBlockWhenExhausted()} is true.
     * <p>
     * If there is one or more idle instances available in the sub-pool
     * associated with the given key, then an idle instance will be selected
     * based on the value of {@link #getLifo()}, activated and returned.  If
     * activation fails, or {@link #getTestOnBorrow() testOnBorrow} is set to
     * {@code true} and validation fails, the instance is destroyed and the
     * next available instance is examined.  This continues until either a valid
     * instance is returned or there are no more idle instances available.
     * </p>
     * <p>
     * If there are no idle instances available in the sub-pool associated with
     * the given key, behavior depends on the {@link #getMaxTotalPerKey()
     * maxTotalPerKey}, {@link #getMaxTotal() maxTotal}, and (if applicable)
     * {@link #getBlockWhenExhausted()} and the value passed in to the
     * {@code borrowMaxWaitMillis} parameter. If the number of instances checked
     * out from the sub-pool under the given key is less than
     * {@code maxTotalPerKey} and the total number of instances in
     * circulation (under all keys) is less than {@code maxTotal}, a new
     * instance is created, activated and (if applicable) validated and returned
     * to the caller. If validation fails, a {@code NoSuchElementException}
     * will be thrown. If the factory returns null when creating an instance,
     * a {@code NullPointerException} is thrown.
     * </p>
     * <p>
     * If the associated sub-pool is exhausted (no available idle instances and
     * no capacity to create new ones), this method will either block
     * ({@link #getBlockWhenExhausted()} is true) or throw a
     * {@code NoSuchElementException}
     * ({@link #getBlockWhenExhausted()} is false).
     * The length of time that this method will block when
     * {@link #getBlockWhenExhausted()} is true is determined by the value
     * passed in to the {@code borrowMaxWait} parameter.
     * </p>
     * <p>
     * When {@code maxTotal} is set to a positive value and this method is
     * invoked when at the limit with no idle instances available under the requested
     * key, an attempt is made to create room by clearing the oldest 15% of the
     * elements from the keyed sub-pools.
     * </p>
     * <p>
     * When the pool is exhausted, multiple calling threads may be
     * simultaneously blocked waiting for instances to become available. A
     * "fairness" algorithm has been implemented to ensure that threads receive
     * available instances in request arrival order.
     * </p>
     *
     * @param key pool key
     * @param borrowMaxWaitMillis The time to wait in milliseconds for an object
     *                            to become available
     *
     * @return object instance from the keyed pool
     *
     * @throws NoSuchElementException if a keyed object instance cannot be
     *                                returned because the pool is exhausted.
     *
     * @throws Exception if a keyed object instance cannot be returned due to an
     *                   error
     */
    public T borrowObject(final K key, final long borrowMaxWaitMillis) throws Exception {
        assertOpen();

        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getRemoveAbandonedOnBorrow() && getNumIdle() < 2 &&
                getNumActive() > getMaxTotal() - 3) {
            removeAbandoned(ac);
        }

        PooledObject<T> p = null;

        // Get local copy of current config so it is consistent for entire
        // method execution
        final boolean blockWhenExhausted = getBlockWhenExhausted();

        boolean create;
        final Instant waitTime = Instant.now();
        final ObjectDeque<T> objectDeque = register(key);

        try {
            while (p == null) {
                create = false;
                p = objectDeque.getIdleObjects().pollFirst();
                if (p == null) {
                    p = create(key);
                    if (!PooledObject.isNull(p)) {
                        create = true;
                    }
                }
                if (blockWhenExhausted) {
                    if (PooledObject.isNull(p)) {
                        p = borrowMaxWaitMillis < 0 ? objectDeque.getIdleObjects().takeFirst():
                                objectDeque.getIdleObjects().pollFirst(borrowMaxWaitMillis, TimeUnit.MILLISECONDS);
                    }
                    if (PooledObject.isNull(p)) {
                        throw new NoSuchElementException(appendStats(
                                "Timeout waiting for idle object, borrowMaxWaitMillis=" + borrowMaxWaitMillis));
                    }
                } else if (PooledObject.isNull(p)) {
                    throw new NoSuchElementException(appendStats("Pool exhausted"));
                }
                if (!p.allocate()) {
                    p = null;
                }

                if (!PooledObject.isNull(p)) {
                    try {
                        factory.activateObject(key, p);
                    } catch (final Exception e) {
                        try {
                            destroy(key, p, true, DestroyMode.NORMAL);
                        } catch (final Exception ignored) {
                            // ignored - activation failure is more important
                        }
                        p = null;
                        if (create) {
                            final NoSuchElementException nsee = new NoSuchElementException(appendStats("Unable to activate object"));
                            nsee.initCause(e);
                            throw nsee;
                        }
                    }
                    if (!PooledObject.isNull(p) && getTestOnBorrow()) {
                        boolean validate = false;
                        Throwable validationThrowable = null;
                        try {
                            validate = factory.validateObject(key, p);
                        } catch (final Throwable t) {
                            PoolUtils.checkRethrow(t);
                            validationThrowable = t;
                        }
                        if (!validate) {
                            try {
                                destroy(key, p, true, DestroyMode.NORMAL);
                                destroyedByBorrowValidationCount.incrementAndGet();
                            } catch (final Exception ignored) {
                                // ignored - validation failure is more important
                            }
                            p = null;
                            if (create) {
                                final NoSuchElementException nsee = new NoSuchElementException(
                                        appendStats("Unable to validate object"));
                                nsee.initCause(validationThrowable);
                                throw nsee;
                            }
                        }
                    }
                }
            }
        } finally {
            deregister(key);
        }

        updateStatsBorrow(p, Duration.between(waitTime, Instant.now()));

        return p.getObject();
    }

    /**
     * Calculate the number of objects that need to be created to attempt to
     * maintain the minimum number of idle objects while not exceeded the limits
     * on the maximum number of objects either per key or totally.
     *
     * @param objectDeque   The set of objects to check
     *
     * @return The number of new objects to create
     */
    private int calculateDeficit(final ObjectDeque<T> objectDeque) {

        if (objectDeque == null) {
            return getMinIdlePerKey();
        }

        // Used more than once so keep a local copy so the value is consistent
        final int maxTotal = getMaxTotal();
        final int maxTotalPerKeySave = getMaxTotalPerKey();

        // Calculate no of objects needed to be created, in order to have
        // the number of pooled objects < maxTotalPerKey();
        int objectDefecit = getMinIdlePerKey() - objectDeque.getIdleObjects().size();
        if (maxTotalPerKeySave > 0) {
            final int growLimit = Math.max(0,
                    maxTotalPerKeySave - objectDeque.getIdleObjects().size());
            objectDefecit = Math.min(objectDefecit, growLimit);
        }

        // Take the maxTotal limit into account
        if (maxTotal > 0) {
            final int growLimit = Math.max(0, maxTotal - getNumActive() - getNumIdle());
            objectDefecit = Math.min(objectDefecit, growLimit);
        }

        return objectDefecit;
    }

    /**
     * Clears any objects sitting idle in the pool by removing them from the
     * idle instance sub-pools and then invoking the configured
     * PoolableObjectFactory's
     * {@link KeyedPooledObjectFactory#destroyObject(Object, PooledObject)}
     * method on each idle instance.
     * <p>
     * Implementation notes:
     * <ul>
     * <li>This method does not destroy or effect in any way instances that are
     * checked out when it is invoked.</li>
     * <li>Invoking this method does not prevent objects being returned to the
     * idle instance pool, even during its execution. Additional instances may
     * be returned while removed items are being destroyed.</li>
     * <li>Exceptions encountered destroying idle instances are swallowed
     * but notified via a {@link SwallowedExceptionListener}.</li>
     * </ul>
     */
    @Override
    public void clear() {
        poolMap.keySet().forEach(key -> clear(key, false));
    }

    /**
     * Clears the specified sub-pool, removing all pooled instances
     * corresponding to the given {@code key}. Exceptions encountered
     * destroying idle instances are swallowed but notified via a
     * {@link SwallowedExceptionListener}.
     * <p>
     * If there are clients waiting to borrow objects, this method will
     * attempt to reuse the capacity freed by this operation, adding
     * instances to the most loaded keyed pools.  To avoid triggering
     * possible object creation, use {@link #clear(Object, boolean)}.
     *
     * @param key the key to clear
     */
    @Override
    public void clear(final K key) {
        clear(key, true);
    }

    /**
     * Clears the specified sub-pool, removing all pooled instances
     * corresponding to the given {@code key}. Exceptions encountered
     * destroying idle instances are swallowed but notified via a
     * {@link SwallowedExceptionListener}.
     * <p>
     * If reuseCapacity is true and there are clients waiting to
     * borrow objects, this method will attempt to reuse the capacity freed
     * by this operation, adding instances to the most loaded keyed pools.
     * </p>
     *
     * @param key the key to clear
     * @param reuseCapacity whether or not to reuse freed capacity
     * @since 2.12.0
     */
    public void clear(final K key, final boolean reuseCapacity) {
        // Return immediately if there is no pool under this key.
        if (!poolMap.containsKey(key)) {
            return;
        }
        final ObjectDeque<T> objectDeque = register(key);
        int freedCapacity = 0;
        try {
            final LinkedBlockingDeque<PooledObject<T>> idleObjects = objectDeque.getIdleObjects();
            PooledObject<T> p = idleObjects.poll();
            while (p != null) {
                try {
                    if (destroy(key, p, true, DestroyMode.NORMAL)) {
                        freedCapacity++;
                    }
                } catch (final Exception e) {
                    swallowException(e);
                }
                p = idleObjects.poll();
            }
        } finally {
            deregister(key);
        }
        if (reuseCapacity) {
            reuseCapacity(freedCapacity);
        }
    }

    /**
     * Clears oldest 15% of objects in pool.  The method sorts the objects into
     * a TreeMap and then iterates the first 15% for removal.
     */
    public void clearOldest() {

        // build sorted map of idle objects
        final TreeMap<PooledObject<T>, K> map = new TreeMap<>();

        poolMap.forEach((key, value) -> {
            // Each item into the map using the PooledObject object as the
            // key. It then gets sorted based on the idle time
            value.getIdleObjects().forEach(p -> map.put(p, key));
        });

        // Now iterate created map and kill the first 15% plus one to account
        // for zero
        int itemsToRemove = (int) (map.size() * 0.15) + 1;
        final Iterator<Entry<PooledObject<T>, K>> iter = map.entrySet().iterator();

        while (iter.hasNext() && itemsToRemove > 0) {
            final Entry<PooledObject<T>, K> entry = iter.next();
            // kind of backwards on naming.  In the map, each key is the
            // PooledObject because it has the ordering with the timestamp
            // value.  Each value that the key references is the key of the
            // list it belongs to.
            final K key = entry.getValue();
            final PooledObject<T> p = entry.getKey();
            // Assume the destruction succeeds
            boolean destroyed = true;
            try {
                destroyed = destroy(key, p, false, DestroyMode.NORMAL);
            } catch (final Exception e) {
                swallowException(e);
            }
            if (destroyed) {
                itemsToRemove--;
            }
        }
    }


    /**
     * Closes the keyed object pool. Once the pool is closed,
     * {@link #borrowObject(Object)} will fail with IllegalStateException, but
     * {@link #returnObject(Object, Object)} and
     * {@link #invalidateObject(Object, Object)} will continue to work, with
     * returned objects destroyed on return.
     * <p>
     * Destroys idle instances in the pool by invoking {@link #clear()}.
     * </p>
     */
    @Override
    public void close() {
        if (isClosed()) {
            return;
        }

        synchronized (closeLock) {
            if (isClosed()) {
                return;
            }

            // Stop the evictor before the pool is closed since evict() calls
            // assertOpen()
            stopEvictor();

            closed = true;
            // This clear removes any idle objects
            clear();

            jmxUnregister();

            // Release any threads that were waiting for an object
            poolMap.values().forEach(e -> e.getIdleObjects().interuptTakeWaiters());
            // This clear cleans up the keys now any waiting threads have been
            // interrupted
            clear();
        }
    }


    /**
     * Creates a new pooled object or null.
     *
     * @param key Key associated with new pooled object.
     *
     * @return The new, wrapped pooled object. May return null.
     *
     * @throws Exception If the objection creation fails.
     */
    private PooledObject<T> create(final K key) throws Exception {
        int maxTotalPerKeySave = getMaxTotalPerKey(); // Per key
        if (maxTotalPerKeySave < 0) {
            maxTotalPerKeySave = Integer.MAX_VALUE;
        }
        final int maxTotal = getMaxTotal();   // All keys

        final ObjectDeque<T> objectDeque = poolMap.get(key);

        // Check against the overall limit
        boolean loop = true;

        while (loop) {
            final int newNumTotal = numTotal.incrementAndGet();
            if (maxTotal > -1 && newNumTotal > maxTotal) {
                numTotal.decrementAndGet();
                if (getNumIdle() == 0) {
                    return null;
                }
                clearOldest();
            } else {
                loop = false;
            }
        }

        // Flag that indicates if create should:
        // - TRUE:  call the factory to create an object
        // - FALSE: return null
        // - null:  loop and re-test the condition that determines whether to
        //          call the factory
        Boolean create = null;
        while (create == null) {
            synchronized (objectDeque.makeObjectCountLock) {
                final long newCreateCount = objectDeque.getCreateCount().incrementAndGet();
                // Check against the per key limit
                if (newCreateCount > maxTotalPerKeySave) {
                    // The key is currently at capacity or in the process of
                    // making enough new objects to take it to capacity.
                    objectDeque.getCreateCount().decrementAndGet();
                    if (objectDeque.makeObjectCount == 0) {
                        // There are no makeObject() calls in progress for this
                        // key so the key is at capacity. Do not attempt to
                        // create a new object. Return and wait for an object to
                        // be returned.
                        create = Boolean.FALSE;
                    } else {
                        // There are makeObject() calls in progress that might
                        // bring the pool to capacity. Those calls might also
                        // fail so wait until they complete and then re-test if
                        // the pool is at capacity or not.
                        objectDeque.makeObjectCountLock.wait();
                    }
                } else {
                    // The pool is not at capacity. Create a new object.
                    objectDeque.makeObjectCount++;
                    create = Boolean.TRUE;
                }
            }
        }

        if (!create.booleanValue()) {
            numTotal.decrementAndGet();
            return null;
        }

        PooledObject<T> p = null;
        try {
            p = factory.makeObject(key);
            if (PooledObject.isNull(p)) {
                numTotal.decrementAndGet();
                objectDeque.getCreateCount().decrementAndGet();
                throw new NullPointerException(String.format("%s.makeObject() = null", factory.getClass().getSimpleName()));
            }
            if (getTestOnCreate() && !factory.validateObject(key, p)) {
                numTotal.decrementAndGet();
                objectDeque.getCreateCount().decrementAndGet();
                return null;
            }
        } catch (final Exception e) {
            numTotal.decrementAndGet();
            objectDeque.getCreateCount().decrementAndGet();
            throw e;
        } finally {
            synchronized (objectDeque.makeObjectCountLock) {
                objectDeque.makeObjectCount--;
                objectDeque.makeObjectCountLock.notifyAll();
            }
        }

        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getLogAbandoned()) {
            p.setLogAbandoned(true);
            p.setRequireFullStackTrace(ac.getRequireFullStackTrace());
        }

        createdCount.incrementAndGet();
        objectDeque.getAllObjects().put(new IdentityWrapper<>(p.getObject()), p);
        return p;
    }

    /**
     * De-register the use of a key by an object.
     * <p>
     * {@link #register(Object)} and {@link #deregister(Object)} must always be used as a pair.
     * </p>
     *
     * @param k The key to de-register
     */
    private void deregister(final K k) {
        Lock lock = keyLock.readLock();
        try {
            lock.lock();
            ObjectDeque<T> objectDeque = poolMap.get(k);
            if (objectDeque == null) {
                throw new IllegalStateException("Attempt to de-register a key for a non-existent pool");
            }
            final long numInterested = objectDeque.getNumInterested().decrementAndGet();
            if (numInterested < 0) {
                throw new IllegalStateException("numInterested count for key " + k + " is less than zero");
            }
            if (numInterested == 0 && objectDeque.getCreateCount().get() == 0) {
                // Potential to remove key
                // Upgrade to write lock
                lock.unlock();
                lock = keyLock.writeLock();
                lock.lock();
                // Pool may have changed since we released the read lock
                // numInterested decrement could lead to removal while waiting for write lock
                objectDeque = poolMap.get(k);
                if (null != objectDeque && objectDeque.getNumInterested().get() == 0 && objectDeque.getCreateCount().get() == 0) {
                    // NOTE: Keys must always be removed from both poolMap and
                    // poolKeyList at the same time while protected by
                    // keyLock.writeLock()
                    poolMap.remove(k);
                    poolKeyList.remove(k);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Destroy the wrapped, pooled object.
     *
     * @param key The key associated with the object to destroy.
     * @param toDestroy The wrapped object to be destroyed
     * @param always Should the object be destroyed even if it is not currently
     *               in the set of idle objects for the given key
     * @param destroyMode DestroyMode context provided to the factory
     *
     * @return {@code true} if the object was destroyed, otherwise {@code false}
     * @throws Exception If the object destruction failed
     */
    private boolean destroy(final K key, final PooledObject<T> toDestroy, final boolean always, final DestroyMode destroyMode) throws Exception {

        final ObjectDeque<T> objectDeque = register(key);

        try {
            boolean isIdle;
            synchronized (toDestroy) {
                // Check idle state directly
                isIdle = toDestroy.getState().equals(PooledObjectState.IDLE);
                // If idle, not under eviction test, or always is true, remove instance,
                // updating isIdle if instance is found in idle objects
                if (isIdle || always) {
                    isIdle = objectDeque.getIdleObjects().remove(toDestroy);
                }
            }
            if (isIdle || always) {
                objectDeque.getAllObjects().remove(new IdentityWrapper<>(toDestroy.getObject()));
                toDestroy.invalidate();

                try {
                    factory.destroyObject(key, toDestroy, destroyMode);
                } finally {
                    objectDeque.getCreateCount().decrementAndGet();
                    destroyedCount.incrementAndGet();
                    numTotal.decrementAndGet();
                }
                return true;
            }
            return false;
        } finally {
            deregister(key);
        }
    }


    @Override
    void ensureMinIdle() throws Exception {
        final int minIdlePerKeySave = getMinIdlePerKey();
        if (minIdlePerKeySave < 1) {
            return;
        }

        for (final K k : poolMap.keySet()) {
            ensureMinIdle(k);
        }
    }

    /**
     * Try to ensure that the configured number of minimum idle objects is available in
     * the pool for the given key.
     * <p>
     * If there is no capacity available to add to the pool, this is a no-op
     * (no exception, no impact to the pool).
     * </p>
     * <p>
     * If the factory returns null when creating an object, a {@code NullPointerException}
     * is thrown.
     * </p>
     *
     * @param key The key to check for idle objects
     *
     * @throws Exception If a new object is required and cannot be created
     */
    private void ensureMinIdle(final K key) throws Exception {
        // Calculate current pool objects
        ObjectDeque<T> objectDeque = poolMap.get(key);

        // objectDeque == null is OK here. It is handled correctly by both
        // methods called below.

        // this method isn't synchronized so the
        // calculateDeficit is done at the beginning
        // as a loop limit and a second time inside the loop
        // to stop when another thread already returned the
        // needed objects
        final int deficit = calculateDeficit(objectDeque);

        for (int i = 0; i < deficit && calculateDeficit(objectDeque) > 0; i++) {
            addObject(key);
            // If objectDeque was null, it won't be any more. Obtain a reference
            // to it so the deficit can be correctly calculated. It needs to
            // take account of objects created in other threads.
            if (objectDeque == null) {
                objectDeque = poolMap.get(key);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Successive activations of this method examine objects in keyed sub-pools
     * in sequence, cycling through the keys and examining objects in
     * oldest-to-youngest order within the keyed sub-pools.
     * </p>
     */
    @Override
    public void evict() throws Exception {
        assertOpen();

        if (getNumIdle() > 0) {

            PooledObject<T> underTest = null;
            final EvictionPolicy<T> evictionPolicy = getEvictionPolicy();

            synchronized (evictionLock) {
                final EvictionConfig evictionConfig = new EvictionConfig(
                        getMinEvictableIdleDuration(),
                        getSoftMinEvictableIdleDuration(),
                        getMinIdlePerKey());

                final boolean testWhileIdle = getTestWhileIdle();

                for (int i = 0, m = getNumTests(); i < m; i++) {
                    if (evictionIterator == null || !evictionIterator.hasNext()) {
                        if (evictionKeyIterator == null ||
                                !evictionKeyIterator.hasNext()) {
                            final List<K> keyCopy = new ArrayList<>();
                            final Lock readLock = keyLock.readLock();
                            readLock.lock();
                            try {
                                keyCopy.addAll(poolKeyList);
                            } finally {
                                readLock.unlock();
                            }
                            evictionKeyIterator = keyCopy.iterator();
                        }
                        while (evictionKeyIterator.hasNext()) {
                            evictionKey = evictionKeyIterator.next();
                            final ObjectDeque<T> objectDeque = poolMap.get(evictionKey);
                            if (objectDeque == null) {
                                continue;
                            }

                            final Deque<PooledObject<T>> idleObjects = objectDeque.getIdleObjects();
                            evictionIterator = new EvictionIterator(idleObjects);
                            if (evictionIterator.hasNext()) {
                                break;
                            }
                            evictionIterator = null;
                        }
                    }
                    if (evictionIterator == null) {
                        // Pools exhausted
                        return;
                    }
                    final Deque<PooledObject<T>> idleObjects;
                    try {
                        underTest = evictionIterator.next();
                        idleObjects = evictionIterator.getIdleObjects();
                    } catch (final NoSuchElementException nsee) {
                        // Object was borrowed in another thread
                        // Don't count this as an eviction test so reduce i;
                        i--;
                        evictionIterator = null;
                        continue;
                    }

                    if (!underTest.startEvictionTest()) {
                        // Object was borrowed in another thread
                        // Don't count this as an eviction test so reduce i;
                        i--;
                        continue;
                    }

                    // User provided eviction policy could throw all sorts of
                    // crazy exceptions. Protect against such an exception
                    // killing the eviction thread.
                    boolean evict;
                    try {
                        evict = evictionPolicy.evict(evictionConfig, underTest,
                                poolMap.get(evictionKey).getIdleObjects().size());
                    } catch (final Throwable t) {
                        // Slightly convoluted as SwallowedExceptionListener
                        // uses Exception rather than Throwable
                        PoolUtils.checkRethrow(t);
                        swallowException(new Exception(t));
                        // Don't evict on error conditions
                        evict = false;
                    }

                    if (evict) {
                        destroy(evictionKey, underTest, true, DestroyMode.NORMAL);
                        destroyedByEvictorCount.incrementAndGet();
                    } else {
                        if (testWhileIdle) {
                            boolean active = false;
                            try {
                                factory.activateObject(evictionKey, underTest);
                                active = true;
                            } catch (final Exception e) {
                                destroy(evictionKey, underTest, true, DestroyMode.NORMAL);
                                destroyedByEvictorCount.incrementAndGet();
                            }
                            if (active) {
                                boolean validate = false;
                                Throwable validationThrowable = null;
                                try {
                                    validate = factory.validateObject(evictionKey, underTest);
                                } catch (final Throwable t) {
                                    PoolUtils.checkRethrow(t);
                                    validationThrowable = t;
                                }
                                if (!validate) {
                                    destroy(evictionKey, underTest, true, DestroyMode.NORMAL);
                                    destroyedByEvictorCount.incrementAndGet();
                                    if (validationThrowable != null) {
                                        if (validationThrowable instanceof RuntimeException) {
                                            throw (RuntimeException) validationThrowable;
                                        }
                                        throw (Error) validationThrowable;
                                    }
                                } else {
                                    try {
                                        factory.passivateObject(evictionKey, underTest);
                                    } catch (final Exception e) {
                                        destroy(evictionKey, underTest, true, DestroyMode.NORMAL);
                                        destroyedByEvictorCount.incrementAndGet();
                                    }
                                }
                            }
                        }
                        underTest.endEvictionTest(idleObjects);
                        // TODO - May need to add code here once additional
                        // states are used
                    }
                }
            }
        }
        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getRemoveAbandonedOnMaintenance()) {
            removeAbandoned(ac);
        }
    }


    /**
     * Gets a reference to the factory used to create, destroy and validate
     * the objects used by this pool.
     *
     * @return the factory
     */
    public KeyedPooledObjectFactory<K, T> getFactory() {
        return factory;
    }

    /**
     * Gets a copy of the pool key list.
     *
     * @return a copy of the pool key list.
     * @since 2.12.0
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<K> getKeys() {
        return (List<K>) poolKeyList.clone();
    }

    /**
     * Gets the cap on the number of "idle" instances per key in the pool.
     * If maxIdlePerKey is set too low on heavily loaded systems it is possible
     * you will see objects being destroyed and almost immediately new objects
     * being created. This is a result of the active threads momentarily
     * returning objects faster than they are requesting them, causing the
     * number of idle objects to rise above maxIdlePerKey. The best value for
     * maxIdlePerKey for heavily loaded system will vary but the default is a
     * good starting point.
     *
     * @return the maximum number of "idle" instances that can be held in a
     *         given keyed sub-pool or a negative value if there is no limit
     *
     * @see #setMaxIdlePerKey
     */
    @Override
    public int getMaxIdlePerKey() {
        return maxIdlePerKey;
    }

    /**
     * Gets the limit on the number of object instances allocated by the pool
     * (checked out or idle), per key. When the limit is reached, the sub-pool
     * is said to be exhausted. A negative value indicates no limit.
     *
     * @return the limit on the number of active instances per key
     *
     * @see #setMaxTotalPerKey
     */
    @Override
    public int getMaxTotalPerKey() {
        return maxTotalPerKey;
    }

    /**
     * Gets the target for the minimum number of idle objects to maintain in
     * each of the keyed sub-pools. This setting only has an effect if it is
     * positive and {@link #getDurationBetweenEvictionRuns()} is greater than
     * zero. If this is the case, an attempt is made to ensure that each
     * sub-pool has the required minimum number of instances during idle object
     * eviction runs.
     * <p>
     * If the configured value of minIdlePerKey is greater than the configured
     * value for maxIdlePerKey then the value of maxIdlePerKey will be used
     * instead.
     * </p>
     *
     * @return minimum size of the each keyed pool
     *
     * @see #setTimeBetweenEvictionRunsMillis
     */
    @Override
    public int getMinIdlePerKey() {
        final int maxIdlePerKeySave = getMaxIdlePerKey();
        return Math.min(this.minIdlePerKey, maxIdlePerKeySave);
    }

    @Override
    public int getNumActive() {
        return numTotal.get() - getNumIdle();
    }

    @Override
    public int getNumActive(final K key) {
        final ObjectDeque<T> objectDeque = poolMap.get(key);
        if (objectDeque != null) {
            return objectDeque.getAllObjects().size() -
                    objectDeque.getIdleObjects().size();
        }
        return 0;
    }

    @Override
    public Map<String, Integer> getNumActivePerKey() {
        return poolMap.entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey().toString(),
                e -> Integer.valueOf(e.getValue().getAllObjects().size() - e.getValue().getIdleObjects().size()),
                (t, u) -> u));
    }

    @Override
    public int getNumIdle() {
        return poolMap.values().stream().mapToInt(e -> e.getIdleObjects().size()).sum();
    }

    @Override
    public int getNumIdle(final K key) {
        final ObjectDeque<T> objectDeque = poolMap.get(key);
        return objectDeque != null ? objectDeque.getIdleObjects().size() : 0;
    }

    /**
     * Gets the number of objects to test in a run of the idle object
     * evictor.
     *
     * @return The number of objects to test for validity
     */
    private int getNumTests() {
        final int totalIdle = getNumIdle();
        final int numTests = getNumTestsPerEvictionRun();
        if (numTests >= 0) {
            return Math.min(numTests, totalIdle);
        }
        return (int) Math.ceil(totalIdle / Math.abs((double) numTests));
    }

    /**
     * Gets an estimate of the number of threads currently blocked waiting for
     * an object from the pool. This is intended for monitoring only, not for
     * synchronization control.
     *
     * @return The estimate of the number of threads currently blocked waiting
     *         for an object from the pool
     */
    @Override
    public int getNumWaiters() {
        if (getBlockWhenExhausted()) {
            // Assume no overflow
            return poolMap.values().stream().mapToInt(e -> e.getIdleObjects().getTakeQueueLength()).sum();
        }
        return 0;
    }

    /**
     * Gets an estimate of the number of threads currently blocked waiting for
     * an object from the pool for each key. This is intended for
     * monitoring only, not for synchronization control.
     *
     * @return The estimate of the number of threads currently blocked waiting
     *         for an object from the pool for each key
     */
    @Override
    public Map<String, Integer> getNumWaitersByKey() {
        final Map<String, Integer> result = new HashMap<>();
        poolMap.forEach((k, deque) -> result.put(k.toString(), getBlockWhenExhausted() ?
                Integer.valueOf(deque.getIdleObjects().getTakeQueueLength()) :
                ZERO));
        return result;
    }

    @SuppressWarnings("boxing") // Commons Pool uses auto-boxing
    @Override
    String getStatsString() {
        // Simply listed in AB order.
        return super.getStatsString() +
                String.format(", fairness=%s, maxIdlePerKey%,d, maxTotalPerKey=%,d, minIdlePerKey=%,d, numTotal=%,d",
                        fairness, maxIdlePerKey, maxTotalPerKey, minIdlePerKey, numTotal.get());
    }

    /**
     * Tests to see if there are any threads currently waiting to borrow
     * objects but are blocked waiting for more objects to become available.
     *
     * @return {@code true} if there is at least one thread waiting otherwise
     *         {@code false}
     */
    private boolean hasBorrowWaiters() {
        return getBlockWhenExhausted() && poolMap.values().stream().anyMatch(deque -> deque.getIdleObjects().hasTakeWaiters());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Activation of this method decrements the active count associated with
     * the given keyed pool and attempts to destroy {@code obj.}
     * </p>
     *
     * @param key pool key
     * @param obj instance to invalidate
     *
     * @throws Exception             if an exception occurs destroying the
     *                               object
     * @throws IllegalStateException if obj does not belong to the pool
     *                               under the given key
     */
    @Override
    public void invalidateObject(final K key, final T obj) throws Exception {
        invalidateObject(key, obj, DestroyMode.NORMAL);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Activation of this method decrements the active count associated with
     * the given keyed pool and attempts to destroy {@code obj.}
     * </p>
     *
     * @param key pool key
     * @param obj instance to invalidate
     * @param destroyMode DestroyMode context provided to factory
     *
     * @throws Exception             if an exception occurs destroying the
     *                               object
     * @throws IllegalStateException if obj does not belong to the pool
     *                               under the given key
     * @since 2.9.0
     */
    @Override
    public void invalidateObject(final K key, final T obj, final DestroyMode destroyMode) throws Exception {
        final ObjectDeque<T> objectDeque = poolMap.get(key);
        final PooledObject<T> p = objectDeque != null ? objectDeque.getAllObjects().get(new IdentityWrapper<>(obj)) : null;
        if (p == null) {
            throw new IllegalStateException(appendStats("Object not currently part of this pool"));
        }
        synchronized (p) {
            if (p.getState() != PooledObjectState.INVALID) {
                destroy(key, p, true, destroyMode);
                reuseCapacity();
            }
        }
    }

    /**
     * Provides information on all the objects in the pool, both idle (waiting
     * to be borrowed) and active (currently borrowed).
     * <p>
     * Note: This is named listAllObjects so it is presented as an operation via
     * JMX. That means it won't be invoked unless the explicitly requested
     * whereas all attributes will be automatically requested when viewing the
     * attributes for an object in a tool like JConsole.
     * </p>
     *
     * @return Information grouped by key on all the objects in the pool
     */
    @Override
    public Map<String, List<DefaultPooledObjectInfo>> listAllObjects() {
        return poolMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(),
                e -> e.getValue().getAllObjects().values().stream().map(DefaultPooledObjectInfo::new).collect(Collectors.toList())));
    }

    /**
     * Registers a key for pool control and ensures that
     * {@link #getMinIdlePerKey()} idle instances are created.
     *
     * @param key - The key to register for pool control.
     *
     * @throws Exception If the associated factory throws an exception
     */
    public void preparePool(final K key) throws Exception {
        final int minIdlePerKeySave = getMinIdlePerKey();
        if (minIdlePerKeySave < 1) {
            return;
        }
        ensureMinIdle(key);
    }

    /**
     * Register the use of a key by an object.
     * <p>
     * {@link #register(Object)} and {@link #deregister(Object)} must always be used as a pair.
     * </p>
     *
     * @param k The key to register
     *
     * @return The objects currently associated with the given key. If this
     *         method returns without throwing an exception then it will never
     *         return null.
     */
    private ObjectDeque<T> register(final K k) {
        Lock lock = keyLock.readLock();
        ObjectDeque<T> objectDeque = null;
        try {
            lock.lock();
            objectDeque = poolMap.get(k);
            if (objectDeque == null) {
                // Upgrade to write lock
                lock.unlock();
                lock = keyLock.writeLock();
                lock.lock();
                final AtomicBoolean allocated = new AtomicBoolean();
                objectDeque = poolMap.computeIfAbsent(k, key -> {
                    allocated.set(true);
                    final ObjectDeque<T> deque = new ObjectDeque<>(fairness);
                    deque.getNumInterested().incrementAndGet();
                    // NOTE: Keys must always be added to both poolMap and
                    //       poolKeyList at the same time while protected by
                    //       the write lock
                    poolKeyList.add(k);
                    return deque;
                });
                if (!allocated.get()) {
                    // Another thread could have beaten us to creating the deque, while we were
                    // waiting for the write lock, so re-get it from the map.
                    objectDeque = poolMap.get(k);
                    objectDeque.getNumInterested().incrementAndGet();
                }
            } else {
                objectDeque.getNumInterested().incrementAndGet();
            }
        } finally {
            lock.unlock();
        }
        return objectDeque;
    }

    /**
     * Recovers abandoned objects which have been checked out but
     * not used since longer than the removeAbandonedTimeout.
     *
     * @param abandonedConfig The configuration to use to identify abandoned objects
     */
    private void removeAbandoned(final AbandonedConfig abandonedConfig) {
        poolMap.forEach((key, value) -> {
            // Generate a list of abandoned objects to remove
            final ArrayList<PooledObject<T>> remove = createRemoveList(abandonedConfig, value.getAllObjects());
            // Now remove the abandoned objects
            remove.forEach(pooledObject -> {
                if (abandonedConfig.getLogAbandoned()) {
                    pooledObject.printStackTrace(abandonedConfig.getLogWriter());
                }
                try {
                    invalidateObject(key, pooledObject.getObject(), DestroyMode.ABANDONED);
                } catch (final Exception e) {
                    swallowException(e);
                }
            });
        });
    }

    /**
     * Returns an object to a keyed sub-pool.
     * <p>
     * If {@link #getMaxIdlePerKey() maxIdle} is set to a positive value and the
     * number of idle instances under the given key has reached this value, the
     * returning instance is destroyed.
     * </p>
     * <p>
     * If {@link #getTestOnReturn() testOnReturn} == true, the returning
     * instance is validated before being returned to the idle instance sub-pool
     * under the given key. In this case, if validation fails, the instance is
     * destroyed.
     * </p>
     * <p>
     * Exceptions encountered destroying objects for any reason are swallowed
     * but notified via a {@link SwallowedExceptionListener}.
     * </p>
     *
     * @param key pool key
     * @param obj instance to return to the keyed pool
     *
     * @throws IllegalStateException if an object is returned to the pool that
     *                               was not borrowed from it or if an object is
     *                               returned to the pool multiple times
     */
    @Override
    public void returnObject(final K key, final T obj) {

        final ObjectDeque<T> objectDeque = poolMap.get(key);

        if (objectDeque == null) {
            throw new IllegalStateException("No keyed pool found under the given key.");
        }

        final PooledObject<T> p = objectDeque.getAllObjects().get(new IdentityWrapper<>(obj));

        if (PooledObject.isNull(p)) {
            throw new IllegalStateException("Returned object not currently part of this pool");
        }

        markReturningState(p);

        final Duration activeTime = p.getActiveDuration();

        try {
            if (getTestOnReturn() && !factory.validateObject(key, p)) {
                try {
                    destroy(key, p, true, DestroyMode.NORMAL);
                } catch (final Exception e) {
                    swallowException(e);
                }
                whenWaitersAddObject(key, objectDeque.idleObjects);
                return;
            }

            try {
                factory.passivateObject(key, p);
            } catch (final Exception e1) {
                swallowException(e1);
                try {
                    destroy(key, p, true, DestroyMode.NORMAL);
                } catch (final Exception e) {
                    swallowException(e);
                }
                whenWaitersAddObject(key, objectDeque.idleObjects);
                return;
            }

            if (!p.deallocate()) {
                throw new IllegalStateException("Object has already been returned to this pool");
            }

            final int maxIdle = getMaxIdlePerKey();
            final LinkedBlockingDeque<PooledObject<T>> idleObjects = objectDeque.getIdleObjects();

            if (isClosed() || maxIdle > -1 && maxIdle <= idleObjects.size()) {
                try {
                    destroy(key, p, true, DestroyMode.NORMAL);
                } catch (final Exception e) {
                    swallowException(e);
                }
            } else {
                if (getLifo()) {
                    idleObjects.addFirst(p);
                } else {
                    idleObjects.addLast(p);
                }
                if (isClosed()) {
                    // Pool closed while object was being added to idle objects.
                    // Make sure the returned object is destroyed rather than left
                    // in the idle object pool (which would effectively be a leak)
                    clear(key);
                }
            }
        } finally {
            if (hasBorrowWaiters()) {
                reuseCapacity();
            }
            updateStatsReturn(activeTime);
        }
    }

    /**
     * Attempt to create one new instance to serve from the most heavily
     * loaded pool that can add a new instance.
     *
     * This method exists to ensure liveness in the pool when threads are
     * parked waiting and capacity to create instances under the requested keys
     * subsequently becomes available.
     *
     * This method is not guaranteed to create an instance and its selection
     * of the most loaded pool that can create an instance may not always be
     * correct, since it does not lock the pool and instances may be created,
     * borrowed, returned or destroyed by other threads while it is executing.
     */
    private void reuseCapacity() {
        final int maxTotalPerKeySave = getMaxTotalPerKey();
        int maxQueueLength = 0;
        LinkedBlockingDeque<PooledObject<T>> mostLoadedPool = null;
        K mostLoadedKey = null;

        // Find the most loaded pool that could take a new instance
        for (final Map.Entry<K, ObjectDeque<T>> entry : poolMap.entrySet()) {
            final K k = entry.getKey();
            final LinkedBlockingDeque<PooledObject<T>> pool = entry.getValue().getIdleObjects();
            final int queueLength = pool.getTakeQueueLength();
            if (getNumActive(k) < maxTotalPerKeySave && queueLength > maxQueueLength) {
                maxQueueLength = queueLength;
                mostLoadedPool = pool;
                mostLoadedKey = k;
            }
        }

        // Attempt to add an instance to the most loaded pool.
        if (mostLoadedPool != null) {
            register(mostLoadedKey);
            try {
                // If there is no capacity to add, create will return null
                // and addIdleObject will no-op.
                addIdleObject(mostLoadedKey, create(mostLoadedKey));
            } catch (final Exception e) {
                swallowException(e);
            } finally {
                deregister(mostLoadedKey);
            }
        }
    }

    /**
     * Call {@link #reuseCapacity()} repeatedly.
     * <p>
     * Always activates {@link #reuseCapacity()} at least once.
     *
     * @param newCapacity number of new instances to attempt to create.
     */
    private void reuseCapacity(final int newCapacity) {
        final int bound = newCapacity < 1 ? 1 : newCapacity;
        for (int i = 0; i < bound; i++) {
            reuseCapacity();
        }
    }

    /**
     * Sets the configuration.
     *
     * @param conf the new configuration to use. This is used by value.
     *
     * @see GenericKeyedObjectPoolConfig
     */
    public void setConfig(final GenericKeyedObjectPoolConfig<T> conf) {
        super.setConfig(conf);
        setMaxIdlePerKey(conf.getMaxIdlePerKey());
        setMaxTotalPerKey(conf.getMaxTotalPerKey());
        setMaxTotal(conf.getMaxTotal());
        setMinIdlePerKey(conf.getMinIdlePerKey());
    }

    /**
     * Sets the cap on the number of "idle" instances per key in the pool.
     * If maxIdlePerKey is set too low on heavily loaded systems it is possible
     * you will see objects being destroyed and almost immediately new objects
     * being created. This is a result of the active threads momentarily
     * returning objects faster than they are requesting them, causing the
     * number of idle objects to rise above maxIdlePerKey. The best value for
     * maxIdlePerKey for heavily loaded system will vary but the default is a
     * good starting point.
     *
     * @param maxIdlePerKey the maximum number of "idle" instances that can be
     *                      held in a given keyed sub-pool. Use a negative value
     *                      for no limit
     *
     * @see #getMaxIdlePerKey
     */
    public void setMaxIdlePerKey(final int maxIdlePerKey) {
        this.maxIdlePerKey = maxIdlePerKey;
    }

    /**
     * Sets the limit on the number of object instances allocated by the pool
     * (checked out or idle), per key. When the limit is reached, the sub-pool
     * is said to be exhausted. A negative value indicates no limit.
     *
     * @param maxTotalPerKey the limit on the number of active instances per key
     *
     * @see #getMaxTotalPerKey
     */
    public void setMaxTotalPerKey(final int maxTotalPerKey) {
        this.maxTotalPerKey = maxTotalPerKey;
    }

    /**
     * Sets the target for the minimum number of idle objects to maintain in
     * each of the keyed sub-pools. This setting only has an effect if it is
     * positive and {@link #getDurationBetweenEvictionRuns()} is greater than
     * zero. If this is the case, an attempt is made to ensure that each
     * sub-pool has the required minimum number of instances during idle object
     * eviction runs.
     * <p>
     * If the configured value of minIdlePerKey is greater than the configured
     * value for maxIdlePerKey then the value of maxIdlePerKey will be used
     * instead.
     * </p>
     *
     * @param minIdlePerKey The minimum size of the each keyed pool
     *
     * @see #getMinIdlePerKey()
     * @see #getMaxIdlePerKey()
     * @see #setDurationBetweenEvictionRuns(Duration)
     */
    public void setMinIdlePerKey(final int minIdlePerKey) {
        this.minIdlePerKey = minIdlePerKey;
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", maxIdlePerKey=");
        builder.append(maxIdlePerKey);
        builder.append(", minIdlePerKey=");
        builder.append(minIdlePerKey);
        builder.append(", maxTotalPerKey=");
        builder.append(maxTotalPerKey);
        builder.append(", factory=");
        builder.append(factory);
        builder.append(", fairness=");
        builder.append(fairness);
        builder.append(", poolMap=");
        builder.append(poolMap);
        builder.append(", poolKeyList=");
        builder.append(poolKeyList);
        builder.append(", keyLock=");
        builder.append(keyLock);
        builder.append(", numTotal=");
        builder.append(numTotal);
        builder.append(", evictionKeyIterator=");
        builder.append(evictionKeyIterator);
        builder.append(", evictionKey=");
        builder.append(evictionKey);
        builder.append(", abandonedConfig=");
        builder.append(abandonedConfig);
    }

    /**
     * @since 2.10.0
     */
    @Override
    public void use(final T pooledObject) {
        final AbandonedConfig abandonedCfg = this.abandonedConfig;
        if (abandonedCfg != null && abandonedCfg.getUseUsageTracking()) {
            poolMap.values().stream()
                .map(pool -> pool.getAllObjects().get(new IdentityWrapper<>(pooledObject)))
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(PooledObject::use);
        }
    }

    /**
     * When there is at least one thread waiting on the given deque, try to add an instance to pool under the given key.
     * NOTE: there is no check that the key corresponds to the deque (it is assumed that the key was used to get the deque
     * from the poolMap)
     *
     * @param key pool key.
     * @param idleObjects deque backing the pool under the given key
     */
    private void whenWaitersAddObject(final K key, final LinkedBlockingDeque<PooledObject<T>> idleObjects) {
        if (idleObjects.hasTakeWaiters()) {
            try {
                addObject(key);
            } catch (final Exception e) {
                swallowException(e);
            }
        }
    }

}

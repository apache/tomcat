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
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.tomcat.dbcp.pool2.DestroyMode;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.PoolUtils;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PooledObjectState;
import org.apache.tomcat.dbcp.pool2.SwallowedExceptionListener;
import org.apache.tomcat.dbcp.pool2.TrackedUse;
import org.apache.tomcat.dbcp.pool2.UsageTracking;

/**
 * A configurable {@link ObjectPool} implementation.
 * <p>
 * When coupled with the appropriate {@link PooledObjectFactory},
 * {@code GenericObjectPool} provides robust pooling functionality for
 * arbitrary objects.
 * </p>
 * <p>
 * Optionally, one may configure the pool to examine and possibly evict objects
 * as they sit idle in the pool and to ensure that a minimum number of idle
 * objects are available. This is performed by an "idle object eviction" thread,
 * which runs asynchronously. Caution should be used when configuring this
 * optional feature. Eviction runs contend with client threads for access to
 * objects in the pool, so if they run too frequently performance issues may
 * result.
 * </p>
 * <p>
 * The pool can also be configured to detect and remove "abandoned" objects,
 * i.e. objects that have been checked out of the pool but neither used nor
 * returned before the configured
 * {@link AbandonedConfig#getRemoveAbandonedTimeoutDuration() removeAbandonedTimeout}.
 * Abandoned object removal can be configured to happen when
 * {@code borrowObject} is invoked and the pool is close to starvation, or
 * it can be executed by the idle object evictor, or both. If pooled objects
 * implement the {@link TrackedUse} interface, their last use will be queried
 * using the {@code getLastUsed} method on that interface; otherwise
 * abandonment is determined by how long an object has been checked out from
 * the pool.
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
 * @see GenericKeyedObjectPool
 *
 * @param <T> Type of element pooled in this pool.
 *
 * @since 2.0
 */
public class GenericObjectPool<T> extends BaseGenericObjectPool<T>
        implements ObjectPool<T>, GenericObjectPoolMXBean, UsageTracking<T> {

    // JMX specific attributes
    private static final String ONAME_BASE =
        "org.apache.commons.pool2:type=GenericObjectPool,name=";

    private static void wait(final Object obj, final Duration duration) throws InterruptedException {
        obj.wait(duration.toMillis(), duration.getNano() % 1_000_000);
    }

    private volatile String factoryType;

    private volatile int maxIdle = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;

    private volatile int minIdle = GenericObjectPoolConfig.DEFAULT_MIN_IDLE;

    private final PooledObjectFactory<T> factory;

    /*
     * All of the objects currently associated with this pool in any state. It
     * excludes objects that have been destroyed. The size of
     * {@link #allObjects} will always be less than or equal to {@link
     * #_maxActive}. Map keys are pooled objects, values are the PooledObject
     * wrappers used internally by the pool.
     */
    private final ConcurrentHashMap<IdentityWrapper<T>, PooledObject<T>> allObjects = new ConcurrentHashMap<>();

    /*
     * The combined count of the currently created objects and those in the
     * process of being created. Under load, it may exceed {@link #_maxActive}
     * if multiple threads try and create a new object at the same time but
     * {@link #create()} will ensure that there are never more than
     * {@link #_maxActive} objects created at any one time.
     */
    private final AtomicLong createCount = new AtomicLong();

    private long makeObjectCount;

    private final Object makeObjectCountLock = new Object();

    private final LinkedBlockingDeque<PooledObject<T>> idleObjects;

    /**
     * Creates a new {@code GenericObjectPool} using defaults from
     * {@link GenericObjectPoolConfig}.
     *
     * @param factory The object factory to be used to create object instances
     *                used by this pool
     */
    public GenericObjectPool(final PooledObjectFactory<T> factory) {
        this(factory, new GenericObjectPoolConfig<>());
    }

    /**
     * Creates a new {@code GenericObjectPool} using a specific
     * configuration.
     *
     * @param factory   The object factory to be used to create object instances
     *                  used by this pool
     * @param config    The configuration to use for this pool instance. The
     *                  configuration is used by value. Subsequent changes to
     *                  the configuration object will not be reflected in the
     *                  pool.
     */
    public GenericObjectPool(final PooledObjectFactory<T> factory,
            final GenericObjectPoolConfig<T> config) {

        super(config, ONAME_BASE, config.getJmxNamePrefix());

        if (factory == null) {
            jmxUnregister(); // tidy up
            throw new IllegalArgumentException("Factory may not be null");
        }
        this.factory = factory;

        idleObjects = new LinkedBlockingDeque<>(config.getFairness());

        setConfig(config);
    }

    /**
     * Creates a new {@code GenericObjectPool} that tracks and destroys
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
     */
    public GenericObjectPool(final PooledObjectFactory<T> factory,
            final GenericObjectPoolConfig<T> config, final AbandonedConfig abandonedConfig) {
        this(factory, config);
        setAbandonedConfig(abandonedConfig);
    }

    /**
     * Adds the provided wrapped pooled object to the set of idle objects for
     * this pool. The object must already be part of the pool.  If {@code p}
     * is null, this is a no-op (no exception, but no impact on the pool).
     *
     * @param p The object to make idle
     *
     * @throws Exception If the factory fails to passivate the object
     */
    private void addIdleObject(final PooledObject<T> p) throws Exception {
        if (!PooledObject.isNull(p)) {
            factory.passivateObject(p);
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
    }

    /**
     * Creates an object, and place it into the pool. addObject() is useful for
     * "pre-loading" a pool with idle objects.
     * <p>
     * If there is no capacity available to add to the pool, this is a no-op
     * (no exception, no impact to the pool).
     * </p>
     * <p>
     * If the factory returns null when creating an object, a {@code NullPointerException}
     * is thrown. If there is no factory set (factory == null), an {@code IllegalStateException}
     * is thrown.
     * </p>
     *
     */
    @Override
    public void addObject() throws Exception {
        assertOpen();
        if (factory == null) {
            throw new IllegalStateException("Cannot add objects without a factory.");
        }
        addIdleObject(create());
    }

    /**
     * Equivalent to <code>{@link #borrowObject(long)
     * borrowObject}({@link #getMaxWaitDuration()})</code>.
     *
     * {@inheritDoc}
     */
    @Override
    public T borrowObject() throws Exception {
        return borrowObject(getMaxWaitDuration());
    }

    /**
     * Borrows an object from the pool using the specific waiting time which only
     * applies if {@link #getBlockWhenExhausted()} is true.
     * <p>
     * If there is one or more idle instance available in the pool, then an
     * idle instance will be selected based on the value of {@link #getLifo()},
     * activated and returned. If activation fails, or {@link #getTestOnBorrow()
     * testOnBorrow} is set to {@code true} and validation fails, the
     * instance is destroyed and the next available instance is examined. This
     * continues until either a valid instance is returned or there are no more
     * idle instances available.
     * </p>
     * <p>
     * If there are no idle instances available in the pool, behavior depends on
     * the {@link #getMaxTotal() maxTotal}, (if applicable)
     * {@link #getBlockWhenExhausted()} and the value passed in to the
     * {@code borrowMaxWaitMillis} parameter. If the number of instances
     * checked out from the pool is less than {@code maxTotal,} a new
     * instance is created, activated and (if applicable) validated and returned
     * to the caller. If validation fails, a {@code NoSuchElementException}
     * is thrown. If the factory returns null when creating an instance,
     * a {@code NullPointerException} is thrown.
     * </p>
     * <p>
     * If the pool is exhausted (no available idle instances and no capacity to
     * create new ones), this method will either block (if
     * {@link #getBlockWhenExhausted()} is true) or throw a
     * {@code NoSuchElementException} (if
     * {@link #getBlockWhenExhausted()} is false). The length of time that this
     * method will block when {@link #getBlockWhenExhausted()} is true is
     * determined by the value passed in to the {@code borrowMaxWaitMillis}
     * parameter.
     * </p>
     * <p>
     * When the pool is exhausted, multiple calling threads may be
     * simultaneously blocked waiting for instances to become available. A
     * "fairness" algorithm has been implemented to ensure that threads receive
     * available instances in request arrival order.
     * </p>
     *
     * @param borrowMaxWaitDuration The time to wait for an object
     *                            to become available
     *
     * @return object instance from the pool
     * @throws NoSuchElementException if an instance cannot be returned
     * @throws Exception if an object instance cannot be returned due to an error
     * @since 2.10.0
     */
    public T borrowObject(final Duration borrowMaxWaitDuration) throws Exception {
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

        while (p == null) {
            create = false;
            p = idleObjects.pollFirst();
            if (p == null) {
                p = create();
                if (!PooledObject.isNull(p)) {
                    create = true;
                }
            }
            if (blockWhenExhausted) {
                if (PooledObject.isNull(p)) {
                    p = borrowMaxWaitDuration.isNegative() ? idleObjects.takeFirst() : idleObjects.pollFirst(borrowMaxWaitDuration);
                }
                if (PooledObject.isNull(p)) {
                    throw new NoSuchElementException(appendStats(
                            "Timeout waiting for idle object, borrowMaxWaitDuration=" + borrowMaxWaitDuration));
                }
            } else if (PooledObject.isNull(p)) {
                throw new NoSuchElementException(appendStats("Pool exhausted"));
            }
            if (!p.allocate()) {
                p = null;
            }

            if (!PooledObject.isNull(p)) {
                try {
                    factory.activateObject(p);
                } catch (final Exception e) {
                    try {
                        destroy(p, DestroyMode.NORMAL);
                    } catch (final Exception ignored) {
                        // ignored - activation failure is more important
                    }
                    p = null;
                    if (create) {
                        final NoSuchElementException nsee = new NoSuchElementException(
                                appendStats("Unable to activate object"));
                        nsee.initCause(e);
                        throw nsee;
                    }
                }
                if (!PooledObject.isNull(p) && getTestOnBorrow()) {
                    boolean validate = false;
                    Throwable validationThrowable = null;
                    try {
                        validate = factory.validateObject(p);
                    } catch (final Throwable t) {
                        PoolUtils.checkRethrow(t);
                        validationThrowable = t;
                    }
                    if (!validate) {
                        try {
                            destroy(p, DestroyMode.NORMAL);
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

        updateStatsBorrow(p, Duration.between(waitTime, Instant.now()));

        return p.getObject();
    }

    /**
     * Borrows an object from the pool using the specific waiting time which only
     * applies if {@link #getBlockWhenExhausted()} is true.
     * <p>
     * If there is one or more idle instance available in the pool, then an
     * idle instance will be selected based on the value of {@link #getLifo()},
     * activated and returned. If activation fails, or {@link #getTestOnBorrow()
     * testOnBorrow} is set to {@code true} and validation fails, the
     * instance is destroyed and the next available instance is examined. This
     * continues until either a valid instance is returned or there are no more
     * idle instances available.
     * </p>
     * <p>
     * If there are no idle instances available in the pool, behavior depends on
     * the {@link #getMaxTotal() maxTotal}, (if applicable)
     * {@link #getBlockWhenExhausted()} and the value passed in to the
     * {@code borrowMaxWaitMillis} parameter. If the number of instances
     * checked out from the pool is less than {@code maxTotal,} a new
     * instance is created, activated and (if applicable) validated and returned
     * to the caller. If validation fails, a {@code NoSuchElementException}
     * is thrown. If the factory returns null when creating an instance,
     * a {@code NullPointerException} is thrown.
     * </p>
     * <p>
     * If the pool is exhausted (no available idle instances and no capacity to
     * create new ones), this method will either block (if
     * {@link #getBlockWhenExhausted()} is true) or throw a
     * {@code NoSuchElementException} (if
     * {@link #getBlockWhenExhausted()} is false). The length of time that this
     * method will block when {@link #getBlockWhenExhausted()} is true is
     * determined by the value passed in to the {@code borrowMaxWaitMillis}
     * parameter.
     * </p>
     * <p>
     * When the pool is exhausted, multiple calling threads may be
     * simultaneously blocked waiting for instances to become available. A
     * "fairness" algorithm has been implemented to ensure that threads receive
     * available instances in request arrival order.
     * </p>
     *
     * @param borrowMaxWaitMillis The time to wait in milliseconds for an object
     *                            to become available
     *
     * @return object instance from the pool
     *
     * @throws NoSuchElementException if an instance cannot be returned
     *
     * @throws Exception if an object instance cannot be returned due to an
     *                   error
     */
    public T borrowObject(final long borrowMaxWaitMillis) throws Exception {
        return borrowObject(Duration.ofMillis(borrowMaxWaitMillis));
    }

    /**
     * Clears any objects sitting idle in the pool by removing them from the
     * idle instance pool and then invoking the configured
     * {@link PooledObjectFactory#destroyObject(PooledObject)} method on each
     * idle instance.
     * <p>
     * Implementation notes:
     * </p>
     * <ul>
     * <li>This method does not destroy or effect in any way instances that are
     * checked out of the pool when it is invoked.</li>
     * <li>Invoking this method does not prevent objects being returned to the
     * idle instance pool, even during its execution. Additional instances may
     * be returned while removed items are being destroyed.</li>
     * <li>Exceptions encountered destroying idle instances are swallowed
     * but notified via a {@link SwallowedExceptionListener}.</li>
     * </ul>
     */
    @Override
    public void clear() {
        PooledObject<T> p = idleObjects.poll();

        while (p != null) {
            try {
                destroy(p, DestroyMode.NORMAL);
            } catch (final Exception e) {
                swallowException(e);
            }
            p = idleObjects.poll();
        }
    }

    /**
     * Closes the pool. Once the pool is closed, {@link #borrowObject()} will
     * fail with IllegalStateException, but {@link #returnObject(Object)} and
     * {@link #invalidateObject(Object)} will continue to work, with returned
     * objects destroyed on return.
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
            idleObjects.interuptTakeWaiters();
        }
    }

    /**
     * Attempts to create a new wrapped pooled object.
     * <p>
     * If there are {@link #getMaxTotal()} objects already in circulation or in process of being created, this method
     * returns null.
     * </p>
     * <p>
     * If the factory makeObject returns null, this method throws a NullPointerException.
     * </p>
     *
     * @return The new wrapped pooled object or null.
     * @throws Exception if the object factory's {@code makeObject} fails
     */
    private PooledObject<T> create() throws Exception {
        int localMaxTotal = getMaxTotal();
        // This simplifies the code later in this method
        if (localMaxTotal < 0) {
            localMaxTotal = Integer.MAX_VALUE;
        }

        final Instant localStartInstant = Instant.now();
        final Duration maxWaitDurationRaw = getMaxWaitDuration();
        final Duration localMaxWaitDuration = maxWaitDurationRaw.isNegative() ? Duration.ZERO : maxWaitDurationRaw;

        // Flag that indicates if create should:
        // - TRUE:  call the factory to create an object
        // - FALSE: return null
        // - null:  loop and re-test the condition that determines whether to
        //          call the factory
        Boolean create = null;
        while (create == null) {
            synchronized (makeObjectCountLock) {
                final long newCreateCount = createCount.incrementAndGet();
                if (newCreateCount > localMaxTotal) {
                    // The pool is currently at capacity or in the process of
                    // making enough new objects to take it to capacity.
                    createCount.decrementAndGet();
                    if (makeObjectCount == 0) {
                        // There are no makeObject() calls in progress so the
                        // pool is at capacity. Do not attempt to create a new
                        // object. Return and wait for an object to be returned
                        create = Boolean.FALSE;
                    } else {
                        // There are makeObject() calls in progress that might
                        // bring the pool to capacity. Those calls might also
                        // fail so wait until they complete and then re-test if
                        // the pool is at capacity or not.
                        wait(makeObjectCountLock, localMaxWaitDuration);
                    }
                } else {
                    // The pool is not at capacity. Create a new object.
                    makeObjectCount++;
                    create = Boolean.TRUE;
                }
            }

            // Do not block more if maxWaitTimeMillis is set.
            if (create == null && localMaxWaitDuration.compareTo(Duration.ZERO) > 0 &&
                    Duration.between(localStartInstant, Instant.now()).compareTo(localMaxWaitDuration) >= 0) {
                create = Boolean.FALSE;
            }
        }

        if (!create.booleanValue()) {
            return null;
        }

        final PooledObject<T> p;
        try {
            p = factory.makeObject();
            if (PooledObject.isNull(p)) {
                createCount.decrementAndGet();
                throw new NullPointerException(String.format("%s.makeObject() = null", factory.getClass().getSimpleName()));
            }
            if (getTestOnCreate() && !factory.validateObject(p)) {
                createCount.decrementAndGet();
                return null;
            }
        } catch (final Throwable e) {
            createCount.decrementAndGet();
            throw e;
        } finally {
            synchronized (makeObjectCountLock) {
                makeObjectCount--;
                makeObjectCountLock.notifyAll();
            }
        }

        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getLogAbandoned()) {
            p.setLogAbandoned(true);
            p.setRequireFullStackTrace(ac.getRequireFullStackTrace());
        }

        createdCount.incrementAndGet();
        allObjects.put(new IdentityWrapper<>(p.getObject()), p);
        return p;
    }

    /**
     * Destroys a wrapped pooled object.
     *
     * @param toDestroy The wrapped pooled object to destroy
     * @param destroyMode DestroyMode context provided to the factory
     *
     * @throws Exception If the factory fails to destroy the pooled object
     *                   cleanly
     */
    private void destroy(final PooledObject<T> toDestroy, final DestroyMode destroyMode) throws Exception {
        toDestroy.invalidate();
        idleObjects.remove(toDestroy);
        allObjects.remove(new IdentityWrapper<>(toDestroy.getObject()));
        try {
            factory.destroyObject(toDestroy, destroyMode);
        } finally {
            destroyedCount.incrementAndGet();
            createCount.decrementAndGet();
        }
    }

    /**
     * Tries to ensure that {@code idleCount} idle instances exist in the pool.
     * <p>
     * Creates and adds idle instances until either {@link #getNumIdle()} reaches {@code idleCount}
     * or the total number of objects (idle, checked out, or being created) reaches
     * {@link #getMaxTotal()}. If {@code always} is false, no instances are created unless
     * there are threads waiting to check out instances from the pool.
     * </p>
     * <p>
     * If the factory returns null when creating an instance, a {@code NullPointerException}
     * is thrown.
     * </p>
     *
     * @param idleCount the number of idle instances desired
     * @param always true means create instances even if the pool has no threads waiting
     * @throws Exception if the factory's makeObject throws
     */
    private void ensureIdle(final int idleCount, final boolean always) throws Exception {
        if (idleCount < 1 || isClosed() || !always && !idleObjects.hasTakeWaiters()) {
            return;
        }

        while (idleObjects.size() < idleCount) {
            final PooledObject<T> p = create();
            if (PooledObject.isNull(p)) {
                // Can't create objects, no reason to think another call to
                // create will work. Give up.
                break;
            }
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
        if (isClosed()) {
            // Pool closed while object was being added to idle objects.
            // Make sure the returned object is destroyed rather than left
            // in the idle object pool (which would effectively be a leak)
            clear();
        }
    }

    @Override
    void ensureMinIdle() throws Exception {
        ensureIdle(getMinIdle(), true);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Successive activations of this method examine objects in sequence,
     * cycling through objects in oldest-to-youngest order.
     * </p>
     */
    @Override
    public void evict() throws Exception {
        assertOpen();

        if (!idleObjects.isEmpty()) {

            PooledObject<T> underTest = null;
            final EvictionPolicy<T> evictionPolicy = getEvictionPolicy();

            synchronized (evictionLock) {
                final EvictionConfig evictionConfig = new EvictionConfig(
                        getMinEvictableIdleDuration(),
                        getSoftMinEvictableIdleDuration(),
                        getMinIdle());

                final boolean testWhileIdle = getTestWhileIdle();

                for (int i = 0, m = getNumTests(); i < m; i++) {
                    if (evictionIterator == null || !evictionIterator.hasNext()) {
                        evictionIterator = new EvictionIterator(idleObjects);
                    }
                    if (!evictionIterator.hasNext()) {
                        // Pool exhausted, nothing to do here
                        return;
                    }

                    try {
                        underTest = evictionIterator.next();
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
                                idleObjects.size());
                    } catch (final Throwable t) {
                        // Slightly convoluted as SwallowedExceptionListener
                        // uses Exception rather than Throwable
                        PoolUtils.checkRethrow(t);
                        swallowException(new Exception(t));
                        // Don't evict on error conditions
                        evict = false;
                    }

                    if (evict) {
                        destroy(underTest, DestroyMode.NORMAL);
                        destroyedByEvictorCount.incrementAndGet();
                    } else {
                        if (testWhileIdle) {
                            boolean active = false;
                            try {
                                factory.activateObject(underTest);
                                active = true;
                            } catch (final Exception e) {
                                destroy(underTest, DestroyMode.NORMAL);
                                destroyedByEvictorCount.incrementAndGet();
                            }
                            if (active) {
                                boolean validate = false;
                                Throwable validationThrowable = null;
                                try {
                                    validate = factory.validateObject(underTest);
                                } catch (final Throwable t) {
                                    PoolUtils.checkRethrow(t);
                                    validationThrowable = t;
                                }
                                if (!validate) {
                                    destroy(underTest, DestroyMode.NORMAL);
                                    destroyedByEvictorCount.incrementAndGet();
                                    if (validationThrowable != null) {
                                        if (validationThrowable instanceof RuntimeException) {
                                            throw (RuntimeException) validationThrowable;
                                        }
                                        throw (Error) validationThrowable;
                                    }
                                } else {
                                    try {
                                        factory.passivateObject(underTest);
                                    } catch (final Exception e) {
                                        destroy(underTest, DestroyMode.NORMAL);
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
    public PooledObjectFactory<T> getFactory() {
        return factory;
    }

    /**
     * Gets the type - including the specific type rather than the generic -
     * of the factory.
     *
     * @return A string representation of the factory type
     */
    @Override
    public String getFactoryType() {
        // Not thread safe. Accept that there may be multiple evaluations.
        if (factoryType == null) {
            final StringBuilder result = new StringBuilder();
            result.append(factory.getClass().getName());
            result.append('<');
            final Class<?> pooledObjectType =
                    PoolImplUtils.getFactoryType(factory.getClass());
            result.append(pooledObjectType.getName());
            result.append('>');
            factoryType = result.toString();
        }
        return factoryType;
    }

    /**
     * Gets the cap on the number of "idle" instances in the pool. If maxIdle
     * is set too low on heavily loaded systems it is possible you will see
     * objects being destroyed and almost immediately new objects being created.
     * This is a result of the active threads momentarily returning objects
     * faster than they are requesting them, causing the number of idle
     * objects to rise above maxIdle. The best value for maxIdle for heavily
     * loaded system will vary but the default is a good starting point.
     *
     * @return the maximum number of "idle" instances that can be held in the
     *         pool or a negative value if there is no limit
     *
     * @see #setMaxIdle
     */
    @Override
    public int getMaxIdle() {
        return maxIdle;
    }

    /**
     * Gets the target for the minimum number of idle objects to maintain in
     * the pool. This setting only has an effect if it is positive and
     * {@link #getDurationBetweenEvictionRuns()} is greater than zero. If this
     * is the case, an attempt is made to ensure that the pool has the required
     * minimum number of instances during idle object eviction runs.
     * <p>
     * If the configured value of minIdle is greater than the configured value
     * for maxIdle then the value of maxIdle will be used instead.
     * </p>
     *
     * @return The minimum number of objects.
     *
     * @see #setMinIdle(int)
     * @see #setMaxIdle(int)
     * @see #setDurationBetweenEvictionRuns(Duration)
     */
    @Override
    public int getMinIdle() {
        final int maxIdleSave = getMaxIdle();
        return Math.min(this.minIdle, maxIdleSave);
    }

    @Override
    public int getNumActive() {
        return allObjects.size() - idleObjects.size();
    }

    @Override
    public int getNumIdle() {
        return idleObjects.size();
    }

    /**
     * Calculates the number of objects to test in a run of the idle object
     * evictor.
     *
     * @return The number of objects to test for validity
     */
    private int getNumTests() {
        final int numTestsPerEvictionRun = getNumTestsPerEvictionRun();
        if (numTestsPerEvictionRun >= 0) {
            return Math.min(numTestsPerEvictionRun, idleObjects.size());
        }
        return (int) Math.ceil(idleObjects.size() /
                Math.abs((double) numTestsPerEvictionRun));
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
            return idleObjects.getTakeQueueLength();
        }
        return 0;
    }

    PooledObject<T> getPooledObject(final T obj) {
        return allObjects.get(new IdentityWrapper<>(obj));
    }

    @SuppressWarnings("boxing")  // Commons Pool uses auto-boxing
    @Override
    String getStatsString() {
        // Simply listed in AB order.
        return super.getStatsString() +
                String.format(", createdCount=%,d, makeObjectCount=%,d, maxIdle=%,d, minIdle=%,d",
                        createdCount.get(), makeObjectCount, maxIdle, minIdle);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Activation of this method decrements the active count and attempts to destroy the instance, using the default
     * (NORMAL) {@link DestroyMode}.
     * </p>
     *
     * @throws Exception if an exception occurs destroying the
     * @throws IllegalStateException if obj does not belong to this pool
     */
    @Override
    public void invalidateObject(final T obj) throws Exception {
        invalidateObject(obj, DestroyMode.NORMAL);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Activation of this method decrements the active count and attempts to destroy the instance, using the provided
     * {@link DestroyMode}.
     * </p>
     *
     * @throws Exception if an exception occurs destroying the object
     * @throws IllegalStateException if obj does not belong to this pool
     * @since 2.9.0
     */
    @Override
    public void invalidateObject(final T obj, final DestroyMode destroyMode) throws Exception {
        final PooledObject<T> p = getPooledObject(obj);
        if (p == null) {
            if (isAbandonedConfig()) {
                return;
            }
            throw new IllegalStateException("Invalidated object not currently part of this pool");
        }
        synchronized (p) {
            if (p.getState() != PooledObjectState.INVALID) {
                destroy(p, destroyMode);
            }
        }
        ensureIdle(1, false);
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
     * @return Information grouped on all the objects in the pool
     */
    @Override
    public Set<DefaultPooledObjectInfo> listAllObjects() {
        return allObjects.values().stream().map(DefaultPooledObjectInfo::new).collect(Collectors.toSet());
    }
    /**
     * Tries to ensure that {@link #getMinIdle()} idle instances are available
     * in the pool.
     *
     * @throws Exception If the associated factory throws an exception
     * @since 2.4
     */
    public void preparePool() throws Exception {
        if (getMinIdle() < 1) {
            return;
        }
        ensureMinIdle();
    }

    /**
     * Recovers abandoned objects which have been checked out but
     * not used since longer than the removeAbandonedTimeout.
     *
     * @param abandonedConfig The configuration to use to identify abandoned objects
     */
    private void removeAbandoned(final AbandonedConfig abandonedConfig) {
        // Generate a list of abandoned objects to remove
        final ArrayList<PooledObject<T>> remove = createRemoveList(abandonedConfig, allObjects);
        // Now remove the abandoned objects
        remove.forEach(pooledObject -> {
            if (abandonedConfig.getLogAbandoned()) {
                pooledObject.printStackTrace(abandonedConfig.getLogWriter());
            }
            try {
                invalidateObject(pooledObject.getObject(), DestroyMode.ABANDONED);
            } catch (final Exception e) {
                swallowException(e);
            }
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * If {@link #getMaxIdle() maxIdle} is set to a positive value and the
     * number of idle instances has reached this value, the returning instance
     * is destroyed.
     * </p>
     * <p>
     * If {@link #getTestOnReturn() testOnReturn} == true, the returning
     * instance is validated before being returned to the idle instance pool. In
     * this case, if validation fails, the instance is destroyed.
     * </p>
     * <p>
     * Exceptions encountered destroying objects for any reason are swallowed
     * but notified via a {@link SwallowedExceptionListener}.
     * </p>
     */
    @Override
    public void returnObject(final T obj) {
        final PooledObject<T> p = getPooledObject(obj);

        if (p == null) {
            if (!isAbandonedConfig()) {
                throw new IllegalStateException(
                        "Returned object not currently part of this pool");
            }
            return; // Object was abandoned and removed
        }

        markReturningState(p);

        final Duration activeTime = p.getActiveDuration();

        if (getTestOnReturn() && !factory.validateObject(p)) {
            try {
                destroy(p, DestroyMode.NORMAL);
            } catch (final Exception e) {
                swallowException(e);
            }
            try {
                ensureIdle(1, false);
            } catch (final Exception e) {
                swallowException(e);
            }
            updateStatsReturn(activeTime);
            return;
        }

        try {
            factory.passivateObject(p);
        } catch (final Exception e1) {
            swallowException(e1);
            try {
                destroy(p, DestroyMode.NORMAL);
            } catch (final Exception e) {
                swallowException(e);
            }
            try {
                ensureIdle(1, false);
            } catch (final Exception e) {
                swallowException(e);
            }
            updateStatsReturn(activeTime);
            return;
        }

        if (!p.deallocate()) {
            throw new IllegalStateException(
                    "Object has already been returned to this pool or is invalid");
        }

        final int maxIdleSave = getMaxIdle();
        if (isClosed() || maxIdleSave > -1 && maxIdleSave <= idleObjects.size()) {
            try {
                destroy(p, DestroyMode.NORMAL);
            } catch (final Exception e) {
                swallowException(e);
            }
            try {
                ensureIdle(1, false);
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
                clear();
            }
        }
        updateStatsReturn(activeTime);
    }

    /**
     * Sets the base pool configuration.
     *
     * @param conf the new configuration to use. This is used by value.
     *
     * @see GenericObjectPoolConfig
     */
    public void setConfig(final GenericObjectPoolConfig<T> conf) {
        super.setConfig(conf);
        setMaxIdle(conf.getMaxIdle());
        setMinIdle(conf.getMinIdle());
        setMaxTotal(conf.getMaxTotal());
    }

    /**
     * Sets the cap on the number of "idle" instances in the pool. If maxIdle
     * is set too low on heavily loaded systems it is possible you will see
     * objects being destroyed and almost immediately new objects being created.
     * This is a result of the active threads momentarily returning objects
     * faster than they are requesting them, causing the number of idle
     * objects to rise above maxIdle. The best value for maxIdle for heavily
     * loaded system will vary but the default is a good starting point.
     *
     * @param maxIdle
     *            The cap on the number of "idle" instances in the pool. Use a
     *            negative value to indicate an unlimited number of idle
     *            instances
     *
     * @see #getMaxIdle
     */
    public void setMaxIdle(final int maxIdle) {
        this.maxIdle = maxIdle;
    }

    /**
     * Sets the target for the minimum number of idle objects to maintain in
     * the pool. This setting only has an effect if it is positive and
     * {@link #getDurationBetweenEvictionRuns()} is greater than zero. If this
     * is the case, an attempt is made to ensure that the pool has the required
     * minimum number of instances during idle object eviction runs.
     * <p>
     * If the configured value of minIdle is greater than the configured value
     * for maxIdle then the value of maxIdle will be used instead.
     * </p>
     *
     * @param minIdle
     *            The minimum number of objects.
     *
     * @see #getMinIdle()
     * @see #getMaxIdle()
     * @see #getDurationBetweenEvictionRuns()
     */
    public void setMinIdle(final int minIdle) {
        this.minIdle = minIdle;
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", factoryType=");
        builder.append(factoryType);
        builder.append(", maxIdle=");
        builder.append(maxIdle);
        builder.append(", minIdle=");
        builder.append(minIdle);
        builder.append(", factory=");
        builder.append(factory);
        builder.append(", allObjects=");
        builder.append(allObjects);
        builder.append(", createCount=");
        builder.append(createCount);
        builder.append(", idleObjects=");
        builder.append(idleObjects);
        builder.append(", abandonedConfig=");
        builder.append(abandonedConfig);
    }

    @Override
    public void use(final T pooledObject) {
        final AbandonedConfig abandonedCfg = this.abandonedConfig;
        if (abandonedCfg != null && abandonedCfg.getUseUsageTracking()) {
            final PooledObject<T> po = getPooledObject(pooledObject);
            if (po != null) {
                po.use();
            }
        }
    }

}

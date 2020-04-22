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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.PoolUtils;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PooledObjectState;
import org.apache.tomcat.dbcp.pool2.UsageTracking;

/**
 * A configurable {@link ObjectPool} implementation.
 * <p>
 * When coupled with the appropriate {@link PooledObjectFactory},
 * <code>GenericObjectPool</code> provides robust pooling functionality for
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
 * {@link AbandonedConfig#getRemoveAbandonedTimeout() removeAbandonedTimeout}.
 * Abandoned object removal can be configured to happen when
 * <code>borrowObject</code> is invoked and the pool is close to starvation, or
 * it can be executed by the idle object evictor, or both. If pooled objects
 * implement the {@link org.apache.tomcat.dbcp.pool2.TrackedUse} interface,
 * their last use will be queried
 * using the <code>getLastUsed</code> method on that interface; otherwise
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

    /**
     * Creates a new <code>GenericObjectPool</code> using defaults from
     * {@link GenericObjectPoolConfig}.
     *
     * @param factory The object factory to be used to create object instances
     *                used by this pool
     */
    public GenericObjectPool(final PooledObjectFactory<T> factory) {
        this(factory, new GenericObjectPoolConfig<T>());
    }

    /**
     * Creates a new <code>GenericObjectPool</code> using a specific
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
            throw new IllegalArgumentException("factory may not be null");
        }
        this.factory = factory;

        idleObjects = new LinkedBlockingDeque<>(config.getFairness());

        setConfig(config);
    }

    /**
     * Creates a new <code>GenericObjectPool</code> that tracks and destroys
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
     * Returns the cap on the number of "idle" instances in the pool. If maxIdle
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
     * Returns the cap on the number of "idle" instances in the pool. If maxIdle
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
     * {@link #getTimeBetweenEvictionRunsMillis()} is greater than zero. If this
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
     * @see #getTimeBetweenEvictionRunsMillis()
     */
    public void setMinIdle(final int minIdle) {
        this.minIdle = minIdle;
    }

    /**
     * Returns the target for the minimum number of idle objects to maintain in
     * the pool. This setting only has an effect if it is positive and
     * {@link #getTimeBetweenEvictionRunsMillis()} is greater than zero. If this
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
     * @see #setTimeBetweenEvictionRunsMillis(long)
     */
    @Override
    public int getMinIdle() {
        final int maxIdleSave = getMaxIdle();
        if (this.minIdle > maxIdleSave) {
            return maxIdleSave;
        }
        return minIdle;
    }

    /**
     * Gets whether or not abandoned object removal is configured for this pool.
     *
     * @return true if this pool is configured to detect and remove
     * abandoned objects
     */
    @Override
    public boolean isAbandonedConfig() {
        return abandonedConfig != null;
    }

    /**
     * Gets whether this pool identifies and logs any abandoned objects.
     *
     * @return {@code true} if abandoned object removal is configured for this
     *         pool and removal events are to be logged otherwise {@code false}
     *
     * @see AbandonedConfig#getLogAbandoned()
     */
    @Override
    public boolean getLogAbandoned() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getLogAbandoned();
    }

    /**
     * Gets whether a check is made for abandoned objects when an object is borrowed
     * from this pool.
     *
     * @return {@code true} if abandoned object removal is configured to be
     *         activated by borrowObject otherwise {@code false}
     *
     * @see AbandonedConfig#getRemoveAbandonedOnBorrow()
     */
    @Override
    public boolean getRemoveAbandonedOnBorrow() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getRemoveAbandonedOnBorrow();
    }

    /**
     * Gets whether a check is made for abandoned objects when the evictor runs.
     *
     * @return {@code true} if abandoned object removal is configured to be
     *         activated when the evictor runs otherwise {@code false}
     *
     * @see AbandonedConfig#getRemoveAbandonedOnMaintenance()
     */
    @Override
    public boolean getRemoveAbandonedOnMaintenance() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null && ac.getRemoveAbandonedOnMaintenance();
    }

    /**
     * Obtains the timeout before which an object will be considered to be
     * abandoned by this pool.
     *
     * @return The abandoned object timeout in seconds if abandoned object
     *         removal is configured for this pool; Integer.MAX_VALUE otherwise.
     *
     * @see AbandonedConfig#getRemoveAbandonedTimeout()
     */
    @Override
    public int getRemoveAbandonedTimeout() {
        final AbandonedConfig ac = this.abandonedConfig;
        return ac != null ? ac.getRemoveAbandonedTimeout() : Integer.MAX_VALUE;
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
     * Sets the abandoned object removal configuration.
     *
     * @param abandonedConfig the new configuration to use. This is used by value.
     *
     * @see AbandonedConfig
     */
    public void setAbandonedConfig(final AbandonedConfig abandonedConfig) {
        if (abandonedConfig == null) {
            this.abandonedConfig = null;
        } else {
            this.abandonedConfig = new AbandonedConfig();
            this.abandonedConfig.setLogAbandoned(abandonedConfig.getLogAbandoned());
            this.abandonedConfig.setLogWriter(abandonedConfig.getLogWriter());
            this.abandonedConfig.setRemoveAbandonedOnBorrow(abandonedConfig.getRemoveAbandonedOnBorrow());
            this.abandonedConfig.setRemoveAbandonedOnMaintenance(abandonedConfig.getRemoveAbandonedOnMaintenance());
            this.abandonedConfig.setRemoveAbandonedTimeout(abandonedConfig.getRemoveAbandonedTimeout());
            this.abandonedConfig.setUseUsageTracking(abandonedConfig.getUseUsageTracking());
            this.abandonedConfig.setRequireFullStackTrace(abandonedConfig.getRequireFullStackTrace());
        }
    }

    /**
     * Obtains a reference to the factory used to create, destroy and validate
     * the objects used by this pool.
     *
     * @return the factory
     */
    public PooledObjectFactory<T> getFactory() {
        return factory;
    }

    /**
     * Equivalent to <code>{@link #borrowObject(long)
     * borrowObject}({@link #getMaxWaitMillis()})</code>.
     * <p>
     * {@inheritDoc}
     * </p>
     */
    @Override
    public T borrowObject() throws Exception {
        return borrowObject(getMaxWaitMillis());
    }

    /**
     * Borrows an object from the pool using the specific waiting time which only
     * applies if {@link #getBlockWhenExhausted()} is true.
     * <p>
     * If there is one or more idle instance available in the pool, then an
     * idle instance will be selected based on the value of {@link #getLifo()},
     * activated and returned. If activation fails, or {@link #getTestOnBorrow()
     * testOnBorrow} is set to <code>true</code> and validation fails, the
     * instance is destroyed and the next available instance is examined. This
     * continues until either a valid instance is returned or there are no more
     * idle instances available.
     * </p>
     * <p>
     * If there are no idle instances available in the pool, behavior depends on
     * the {@link #getMaxTotal() maxTotal}, (if applicable)
     * {@link #getBlockWhenExhausted()} and the value passed in to the
     * <code>borrowMaxWaitMillis</code> parameter. If the number of instances
     * checked out from the pool is less than <code>maxTotal,</code> a new
     * instance is created, activated and (if applicable) validated and returned
     * to the caller. If validation fails, a <code>NoSuchElementException</code>
     * is thrown.
     * </p>
     * <p>
     * If the pool is exhausted (no available idle instances and no capacity to
     * create new ones), this method will either block (if
     * {@link #getBlockWhenExhausted()} is true) or throw a
     * <code>NoSuchElementException</code> (if
     * {@link #getBlockWhenExhausted()} is false). The length of time that this
     * method will block when {@link #getBlockWhenExhausted()} is true is
     * determined by the value passed in to the <code>borrowMaxWaitMillis</code>
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
        assertOpen();

        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getRemoveAbandonedOnBorrow() &&
                (getNumIdle() < 2) &&
                (getNumActive() > getMaxTotal() - 3) ) {
            removeAbandoned(ac);
        }

        PooledObject<T> p = null;

        // Get local copy of current config so it is consistent for entire
        // method execution
        final boolean blockWhenExhausted = getBlockWhenExhausted();

        boolean create;
        final long waitTime = System.currentTimeMillis();

        while (p == null) {
            create = false;
            p = idleObjects.pollFirst();
            if (p == null) {
                p = create();
                if (p != null) {
                    create = true;
                }
            }
            if (blockWhenExhausted) {
                if (p == null) {
                    if (borrowMaxWaitMillis < 0) {
                        p = idleObjects.takeFirst();
                    } else {
                        p = idleObjects.pollFirst(borrowMaxWaitMillis,
                                TimeUnit.MILLISECONDS);
                    }
                }
                if (p == null) {
                    throw new NoSuchElementException(
                            "Timeout waiting for idle object");
                }
            } else {
                if (p == null) {
                    throw new NoSuchElementException("Pool exhausted");
                }
            }
            if (!p.allocate()) {
                p = null;
            }

            if (p != null) {
                try {
                    factory.activateObject(p);
                } catch (final Exception e) {
                    try {
                        destroy(p);
                    } catch (final Exception e1) {
                        // Ignore - activation failure is more important
                    }
                    p = null;
                    if (create) {
                        final NoSuchElementException nsee = new NoSuchElementException(
                                "Unable to activate object");
                        nsee.initCause(e);
                        throw nsee;
                    }
                }
                if (p != null && getTestOnBorrow()) {
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
                            destroy(p);
                            destroyedByBorrowValidationCount.incrementAndGet();
                        } catch (final Exception e) {
                            // Ignore - validation failure is more important
                        }
                        p = null;
                        if (create) {
                            final NoSuchElementException nsee = new NoSuchElementException(
                                    "Unable to validate object");
                            nsee.initCause(validationThrowable);
                            throw nsee;
                        }
                    }
                }
            }
        }

        updateStatsBorrow(p, System.currentTimeMillis() - waitTime);

        return p.getObject();
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
     * but notified via a
     * {@link org.apache.tomcat.dbcp.pool2.SwallowedExceptionListener}.
     * </p>
     */
    @Override
    public void returnObject(final T obj) {
        final PooledObject<T> p = allObjects.get(new IdentityWrapper<>(obj));

        if (p == null) {
            if (!isAbandonedConfig()) {
                throw new IllegalStateException(
                        "Returned object not currently part of this pool");
            }
            return; // Object was abandoned and removed
        }

        markReturningState(p);

        final long activeTime = p.getActiveTimeMillis();

        if (getTestOnReturn() && !factory.validateObject(p)) {
            try {
                destroy(p);
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
                destroy(p);
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
                destroy(p);
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
     * {@inheritDoc}
     * <p>
     * Activation of this method decrements the active count and attempts to
     * destroy the instance.
     * </p>
     *
     * @throws Exception             if an exception occurs destroying the
     *                               object
     * @throws IllegalStateException if obj does not belong to this pool
     */
    @Override
    public void invalidateObject(final T obj) throws Exception {
        final PooledObject<T> p = allObjects.get(new IdentityWrapper<>(obj));
        if (p == null) {
            if (isAbandonedConfig()) {
                return;
            }
            throw new IllegalStateException(
                    "Invalidated object not currently part of this pool");
        }
        synchronized (p) {
            if (p.getState() != PooledObjectState.INVALID) {
                destroy(p);
            }
        }
        ensureIdle(1, false);
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
     * but notified via a
     * {@link org.apache.tomcat.dbcp.pool2.SwallowedExceptionListener}.</li>
     * </ul>
     */
    @Override
    public void clear() {
        PooledObject<T> p = idleObjects.poll();

        while (p != null) {
            try {
                destroy(p);
            } catch (final Exception e) {
                swallowException(e);
            }
            p = idleObjects.poll();
        }
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
     * {@inheritDoc}
     * <p>
     * Successive activations of this method examine objects in sequence,
     * cycling through objects in oldest-to-youngest order.
     * </p>
     */
    @Override
    public void evict() throws Exception {
        assertOpen();

        if (idleObjects.size() > 0) {

            PooledObject<T> underTest = null;
            final EvictionPolicy<T> evictionPolicy = getEvictionPolicy();

            synchronized (evictionLock) {
                final EvictionConfig evictionConfig = new EvictionConfig(
                        getMinEvictableIdleTimeMillis(),
                        getSoftMinEvictableIdleTimeMillis(),
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
                        destroy(underTest);
                        destroyedByEvictorCount.incrementAndGet();
                    } else {
                        if (testWhileIdle) {
                            boolean active = false;
                            try {
                                factory.activateObject(underTest);
                                active = true;
                            } catch (final Exception e) {
                                destroy(underTest);
                                destroyedByEvictorCount.incrementAndGet();
                            }
                            if (active) {
                                if (!factory.validateObject(underTest)) {
                                    destroy(underTest);
                                    destroyedByEvictorCount.incrementAndGet();
                                } else {
                                    try {
                                        factory.passivateObject(underTest);
                                    } catch (final Exception e) {
                                        destroy(underTest);
                                        destroyedByEvictorCount.incrementAndGet();
                                    }
                                }
                            }
                        }
                        if (!underTest.endEvictionTest(idleObjects)) {
                            // TODO - May need to add code here once additional
                            // states are used
                        }
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
     * Attempts to create a new wrapped pooled object.
     * <p>
     * If there are {@link #getMaxTotal()} objects already in circulation
     * or in process of being created, this method returns null.
     * </p>
     *
     * @return The new wrapped pooled object
     *
     * @throws Exception if the object factory's {@code makeObject} fails
     */
    private PooledObject<T> create() throws Exception {
        int localMaxTotal = getMaxTotal();
        // This simplifies the code later in this method
        if (localMaxTotal < 0) {
            localMaxTotal = Integer.MAX_VALUE;
        }

        final long localStartTimeMillis = System.currentTimeMillis();
        final long localMaxWaitTimeMillis = Math.max(getMaxWaitMillis(), 0);

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
                        makeObjectCountLock.wait(localMaxWaitTimeMillis);
                    }
                } else {
                    // The pool is not at capacity. Create a new object.
                    makeObjectCount++;
                    create = Boolean.TRUE;
                }
            }

            // Do not block more if maxWaitTimeMillis is set.
            if (create == null &&
                (localMaxWaitTimeMillis > 0 &&
                 System.currentTimeMillis() - localStartTimeMillis >= localMaxWaitTimeMillis)) {
                create = Boolean.FALSE;
            }
        }

        if (!create.booleanValue()) {
            return null;
        }

        final PooledObject<T> p;
        try {
            p = factory.makeObject();
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
     *
     * @throws Exception If the factory fails to destroy the pooled object
     *                   cleanly
     */
    private void destroy(final PooledObject<T> toDestroy) throws Exception {
        toDestroy.invalidate();
        idleObjects.remove(toDestroy);
        allObjects.remove(new IdentityWrapper<>(toDestroy.getObject()));
        try {
            factory.destroyObject(toDestroy);
        } finally {
            destroyedCount.incrementAndGet();
            createCount.decrementAndGet();
        }
    }

    @Override
    void ensureMinIdle() throws Exception {
        ensureIdle(getMinIdle(), true);
    }

    /**
     * Tries to ensure that {@code idleCount} idle instances exist in the pool.
     * <p>
     * Creates and adds idle instances until either {@link #getNumIdle()} reaches {@code idleCount}
     * or the total number of objects (idle, checked out, or being created) reaches
     * {@link #getMaxTotal()}. If {@code always} is false, no instances are created unless
     * there are threads waiting to check out instances from the pool.
     * </p>
     *
     * @param idleCount the number of idle instances desired
     * @param always true means create instances even if the pool has no threads waiting
     * @throws Exception if the factory's makeObject throws
     */
    private void ensureIdle(final int idleCount, final boolean always) throws Exception {
        if (idleCount < 1 || isClosed() || (!always && !idleObjects.hasTakeWaiters())) {
            return;
        }

        while (idleObjects.size() < idleCount) {
            final PooledObject<T> p = create();
            if (p == null) {
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

    /**
     * Creates an object, and place it into the pool. addObject() is useful for
     * "pre-loading" a pool with idle objects.
     * <p>
     * If there is no capacity available to add to the pool, this is a no-op
     * (no exception, no impact to the pool). </p>
     */
    @Override
    public void addObject() throws Exception {
        assertOpen();
        if (factory == null) {
            throw new IllegalStateException(
                    "Cannot add objects without a factory.");
        }
        final PooledObject<T> p = create();
        addIdleObject(p);
    }

    /**
     * Calls {@link ObjectPool#addObject()} <code>count</code>
     * number of times.
     *
     * @param count
     *            the number of idle objects to add.
     * @throws Exception
     *             when {@link ObjectPool#addObject()} fails.
     * @since 2.8.0
     */
    @Override
    public void addObjects(final int count) throws Exception {
        for (int i = 0; i < count; i++) {
            addObject();
        }
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
        if (p != null) {
            factory.passivateObject(p);
            if (getLifo()) {
                idleObjects.addFirst(p);
            } else {
                idleObjects.addLast(p);
            }
        }
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
        return (int) (Math.ceil(idleObjects.size() /
                Math.abs((double) numTestsPerEvictionRun)));
    }

    /**
     * Recovers abandoned objects which have been checked out but
     * not used since longer than the removeAbandonedTimeout.
     *
     * @param ac The configuration to use to identify abandoned objects
     */
    private void removeAbandoned(final AbandonedConfig ac) {
        // Generate a list of abandoned objects to remove
        final long now = System.currentTimeMillis();
        final long timeout =
                now - (ac.getRemoveAbandonedTimeout() * 1000L);
        final ArrayList<PooledObject<T>> remove = new ArrayList<>();
        for (PooledObject<T> pooledObject : allObjects.values()) {
            synchronized (pooledObject) {
                if (pooledObject.getState() == PooledObjectState.ALLOCATED &&
                        pooledObject.getLastUsedTime() <= timeout) {
                    pooledObject.markAbandoned();
                    remove.add(pooledObject);
                }
            }
        }

        // Now remove the abandoned objects
        for (PooledObject<T> pooledObject : remove) {
            if (ac.getLogAbandoned()) {
                pooledObject.printStackTrace(ac.getLogWriter());
            }
            try {
                invalidateObject(pooledObject.getObject());
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }


    //--- Usage tracking support -----------------------------------------------

    @Override
    public void use(final T pooledObject) {
        final AbandonedConfig ac = this.abandonedConfig;
        if (ac != null && ac.getUseUsageTracking()) {
            final PooledObject<T> wrapper = allObjects.get(new IdentityWrapper<>(pooledObject));
            wrapper.use();
        }
    }


    //--- JMX support ----------------------------------------------------------

    private volatile String factoryType = null;

    /**
     * Returns an estimate of the number of threads currently blocked waiting for
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

    /**
     * Returns the type - including the specific type rather than the generic -
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
        final Set<DefaultPooledObjectInfo> result =
                new HashSet<>(allObjects.size());
        for (final PooledObject<T> p : allObjects.values()) {
            result.add(new DefaultPooledObjectInfo(p));
        }
        return result;
    }

    // --- configuration attributes --------------------------------------------

    private volatile int maxIdle = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;
    private volatile int minIdle = GenericObjectPoolConfig.DEFAULT_MIN_IDLE;
    private final PooledObjectFactory<T> factory;


    // --- internal attributes -------------------------------------------------

    /*
     * All of the objects currently associated with this pool in any state. It
     * excludes objects that have been destroyed. The size of
     * {@link #allObjects} will always be less than or equal to {@link
     * #_maxActive}. Map keys are pooled objects, values are the PooledObject
     * wrappers used internally by the pool.
     */
    private final Map<IdentityWrapper<T>, PooledObject<T>> allObjects =
        new ConcurrentHashMap<>();
    /*
     * The combined count of the currently created objects and those in the
     * process of being created. Under load, it may exceed {@link #_maxActive}
     * if multiple threads try and create a new object at the same time but
     * {@link #create()} will ensure that there are never more than
     * {@link #_maxActive} objects created at any one time.
     */
    private final AtomicLong createCount = new AtomicLong(0);
    private long makeObjectCount = 0;
    private final Object makeObjectCountLock = new Object();
    private final LinkedBlockingDeque<PooledObject<T>> idleObjects;

    // JMX specific attributes
    private static final String ONAME_BASE =
        "org.apache.tomcat.dbcp.pool2:type=GenericObjectPool,name=";

    // Additional configuration properties for abandoned object tracking
    private volatile AbandonedConfig abandonedConfig = null;

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

}

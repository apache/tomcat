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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TimerTask;

import org.apache.tomcat.dbcp.pool.BaseObjectPool;
import org.apache.tomcat.dbcp.pool.ObjectPool;
import org.apache.tomcat.dbcp.pool.PoolUtils;
import org.apache.tomcat.dbcp.pool.PoolableObjectFactory;
import org.apache.tomcat.dbcp.pool.impl.GenericKeyedObjectPool.ObjectTimestampPair;

/**
 * A configurable {@link ObjectPool} implementation.
 * <p>
 * When coupled with the appropriate {@link PoolableObjectFactory},
 * <code>GenericObjectPool</code> provides robust pooling functionality for
 * arbitrary objects.
 * <p>
 * A <code>GenericObjectPool</code> provides a number of configurable parameters:
 * <ul>
 *  <li>
 *    {@link #setMaxActive <i>maxActive</i>} controls the maximum number of
 *    objects that can be allocated by the pool (checked out to clients, or
 *    idle awaiting checkout) at a given time.  When non-positive, there is no
 *    limit to the number of objects that can be managed by the pool at one time.
 *    When {@link #setMaxActive <i>maxActive</i>} is reached, the pool is said
 *    to be exhausted. The default setting for this parameter is 8.
 *  </li>
 *  <li>
 *    {@link #setMaxIdle <i>maxIdle</i>} controls the maximum number of objects
 *    that can sit idle in the pool at any time.  When negative, there is no
 *    limit to the number of objects that may be idle at one time. The default
 *    setting for this parameter is 8.
 *  </li>
 *  <li>
 *    {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} specifies the
 *    behavior of the {@link #borrowObject} method when the pool is exhausted:
 *    <ul>
 *    <li>
 *      When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} is
 *      {@link #WHEN_EXHAUSTED_FAIL}, {@link #borrowObject} will throw
 *      a {@link NoSuchElementException}
 *    </li>
 *    <li>
 *      When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>} is
 *      {@link #WHEN_EXHAUSTED_GROW}, {@link #borrowObject} will create a new
 *      object and return it (essentially making {@link #setMaxActive <i>maxActive</i>}
 *      meaningless.)
 *    </li>
 *    <li>
 *      When {@link #setWhenExhaustedAction <i>whenExhaustedAction</i>}
 *      is {@link #WHEN_EXHAUSTED_BLOCK}, {@link #borrowObject} will block
 *      (invoke {@link Object#wait()}) until a new or idle object is available.
 *      If a positive {@link #setMaxWait <i>maxWait</i>}
 *      value is supplied, then {@link #borrowObject} will block for at
 *      most that many milliseconds, after which a {@link NoSuchElementException}
 *      will be thrown.  If {@link #setMaxWait <i>maxWait</i>} is non-positive,
 *      the {@link #borrowObject} method will block indefinitely.
 *    </li>
 *    </ul>
 *    The default <code>whenExhaustedAction</code> setting is
 *    {@link #WHEN_EXHAUSTED_BLOCK} and the default <code>maxWait</code>
 *    setting is -1. By default, therefore, <code>borrowObject</code> will
 *    block indefinitely until an idle instance becomes available.
 *  </li>
 *  <li>
 *    When {@link #setTestOnBorrow <i>testOnBorrow</i>} is set, the pool will
 *    attempt to validate each object before it is returned from the
 *    {@link #borrowObject} method. (Using the provided factory's
 *    {@link PoolableObjectFactory#validateObject} method.)  Objects that fail
 *    to validate will be dropped from the pool, and a different object will
 *    be borrowed. The default setting for this parameter is
 *    <code>false.</code>
 *  </li>
 *  <li>
 *    When {@link #setTestOnReturn <i>testOnReturn</i>} is set, the pool will
 *    attempt to validate each object before it is returned to the pool in the
 *    {@link #returnObject} method. (Using the provided factory's
 *    {@link PoolableObjectFactory#validateObject}
 *    method.)  Objects that fail to validate will be dropped from the pool.
 *    The default setting for this parameter is <code>false.</code>
 *  </li>
 * </ul>
 * <p>
 * Optionally, one may configure the pool to examine and possibly evict objects
 * as they sit idle in the pool and to ensure that a minimum number of idle
 * objects are available. This is performed by an "idle object eviction"
 * thread, which runs asynchronously. Caution should be used when configuring
 * this optional feature. Eviction runs contend with client threads for access
 * to objects in the pool, so if they run too frequently performance issues may
 * result. The idle object eviction thread may be configured using the following
 * attributes:
 * <ul>
 *  <li>
 *   {@link #setTimeBetweenEvictionRunsMillis <i>timeBetweenEvictionRunsMillis</i>}
 *   indicates how long the eviction thread should sleep before "runs" of examining
 *   idle objects.  When non-positive, no eviction thread will be launched. The
 *   default setting for this parameter is -1 (i.e., idle object eviction is
 *   disabled by default).
 *  </li>
 *  <li>
 *   {@link #setMinEvictableIdleTimeMillis <i>minEvictableIdleTimeMillis</i>}
 *   specifies the minimum amount of time that an object may sit idle in the pool
 *   before it is eligible for eviction due to idle time.  When non-positive, no object
 *   will be dropped from the pool due to idle time alone. This setting has no
 *   effect unless <code>timeBetweenEvictionRunsMillis &gt; 0.</code> The default
 *   setting for this parameter is 30 minutes.
 *  </li>
 *  <li>
 *   {@link #setTestWhileIdle <i>testWhileIdle</i>} indicates whether or not idle
 *   objects should be validated using the factory's
 *   {@link PoolableObjectFactory#validateObject} method. Objects that fail to
 *   validate will be dropped from the pool. This setting has no effect unless
 *   <code>timeBetweenEvictionRunsMillis &gt; 0.</code>  The default setting for
 *   this parameter is <code>false.</code>
 *  </li>
 *  <li>
 *   {@link #setSoftMinEvictableIdleTimeMillis <i>softMinEvictableIdleTimeMillis</i>}
 *   specifies the minimum amount of time an object may sit idle in the pool
 *   before it is eligible for eviction by the idle object evictor
 *   (if any), with the extra condition that at least "minIdle" object instances
 *   remain in the pool.  When non-positive, no objects will be evicted from the pool
 *   due to idle time alone. This setting has no effect unless
 *   <code>timeBetweenEvictionRunsMillis &gt; 0.</code> and it is superceded by
 *   {@link #setMinEvictableIdleTimeMillis <i>minEvictableIdleTimeMillis</i>}
 *   (that is, if <code>minEvictableIdleTimeMillis</code> is positive, then
 *   <code>softMinEvictableIdleTimeMillis</code> is ignored). The default setting for
 *   this parameter is -1 (disabled).
 *  </li>
 *  <li>
 *   {@link #setNumTestsPerEvictionRun <i>numTestsPerEvictionRun</i>}
 *   determines the number of objects examined in each run of the idle object
 *   evictor. This setting has no effect unless
 *   <code>timeBetweenEvictionRunsMillis &gt; 0.</code>  The default setting for
 *   this parameter is 3.
 *  </li>
 * </ul>
 * <p>
 * The pool can be configured to behave as a LIFO queue with respect to idle
 * objects - always returning the most recently used object from the pool,
 * or as a FIFO queue, where borrowObject always returns the oldest object
 * in the idle object pool.
 * <ul>
 *  <li>
 *   {@link #setLifo <i>lifo</i>}
 *   determines whether or not the pool returns idle objects in
 *   last-in-first-out order. The default setting for this parameter is
 *   <code>true.</code>
 *  </li>
 * </ul>
 * <p>
 * GenericObjectPool is not usable without a {@link PoolableObjectFactory}.  A
 * non-<code>null</code> factory must be provided either as a constructor argument
 * or via a call to {@link #setFactory} before the pool is used.
 * <p>
 * Implementation note: To prevent possible deadlocks, care has been taken to
 * ensure that no call to a factory method will occur within a synchronization
 * block. See POOL-125 and DBCP-44 for more information.
 *
 * @param <T> the type of objects held in this pool
 *
 * @see GenericKeyedObjectPool
 * @author Rodney Waldhoff
 * @author Dirk Verbeeck
 * @author Sandy McArthur
 * @since Pool 1.0
 */
public class GenericObjectPool<T> extends BaseObjectPool<T> {

    //--- public constants -------------------------------------------

    /**
     * A "when exhausted action" type indicating that when the pool is
     * exhausted (i.e., the maximum number of active objects has
     * been reached), the {@link #borrowObject}
     * method should fail, throwing a {@link NoSuchElementException}.
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setWhenExhaustedAction
     */
    public static final byte WHEN_EXHAUSTED_FAIL   = 0;

    /**
     * A "when exhausted action" type indicating that when the pool
     * is exhausted (i.e., the maximum number
     * of active objects has been reached), the {@link #borrowObject}
     * method should block until a new object is available, or the
     * {@link #getMaxWait maximum wait time} has been reached.
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setMaxWait
     * @see #getMaxWait
     * @see #setWhenExhaustedAction
     */
    public static final byte WHEN_EXHAUSTED_BLOCK  = 1;

    /**
     * A "when exhausted action" type indicating that when the pool is
     * exhausted (i.e., the maximum number
     * of active objects has been reached), the {@link #borrowObject}
     * method should simply create a new object anyway.
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setWhenExhaustedAction
     */
    public static final byte WHEN_EXHAUSTED_GROW   = 2;

    /**
     * The default cap on the number of "sleeping" instances in the pool.
     * @see #getMaxIdle
     * @see #setMaxIdle
     */
    public static final int DEFAULT_MAX_IDLE  = 8;

    /**
     * The default minimum number of "sleeping" instances in the pool
     * before before the evictor thread (if active) spawns new objects.
     * @see #getMinIdle
     * @see #setMinIdle
     */
    public static final int DEFAULT_MIN_IDLE = 0;

    /**
     * The default cap on the total number of active instances from the pool.
     * @see #getMaxActive
     */
    public static final int DEFAULT_MAX_ACTIVE  = 8;

    /**
     * The default "when exhausted action" for the pool.
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setWhenExhaustedAction
     */
    public static final byte DEFAULT_WHEN_EXHAUSTED_ACTION = WHEN_EXHAUSTED_BLOCK;

    /**
     * The default LIFO status. True means that borrowObject returns the
     * most recently used ("last in") idle object in the pool (if there are
     * idle instances available).  False means that the pool behaves as a FIFO
     * queue - objects are taken from the idle object pool in the order that
     * they are returned to the pool.
     * @see #setLifo
     * @since 1.4
     */
    public static final boolean DEFAULT_LIFO = true;

    /**
     * The default maximum amount of time (in milliseconds) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #getWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     * @see #getMaxWait
     * @see #setMaxWait
     */
    public static final long DEFAULT_MAX_WAIT = -1L;

    /**
     * The default "test on borrow" value.
     * @see #getTestOnBorrow
     * @see #setTestOnBorrow
     */
    public static final boolean DEFAULT_TEST_ON_BORROW = false;

    /**
     * The default "test on return" value.
     * @see #getTestOnReturn
     * @see #setTestOnReturn
     */
    public static final boolean DEFAULT_TEST_ON_RETURN = false;

    /**
     * The default "test while idle" value.
     * @see #getTestWhileIdle
     * @see #setTestWhileIdle
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public static final boolean DEFAULT_TEST_WHILE_IDLE = false;

    /**
     * The default "time between eviction runs" value.
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public static final long DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = -1L;

    /**
     * The default number of objects to examine per run in the
     * idle object evictor.
     * @see #getNumTestsPerEvictionRun
     * @see #setNumTestsPerEvictionRun
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public static final int DEFAULT_NUM_TESTS_PER_EVICTION_RUN = 3;

    /**
     * The default value for {@link #getMinEvictableIdleTimeMillis}.
     * @see #getMinEvictableIdleTimeMillis
     * @see #setMinEvictableIdleTimeMillis
     */
    public static final long DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS = 1000L * 60L * 30L;

    /**
     * The default value for {@link #getSoftMinEvictableIdleTimeMillis}.
     * @see #getSoftMinEvictableIdleTimeMillis
     * @see #setSoftMinEvictableIdleTimeMillis
     */
    public static final long DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS = -1;

    //--- constructors -----------------------------------------------

    /**
     * Create a new <code>GenericObjectPool</code> with default properties.
     */
    public GenericObjectPool() {
        this(null, DEFAULT_MAX_ACTIVE, DEFAULT_WHEN_EXHAUSTED_ACTION, DEFAULT_MAX_WAIT, DEFAULT_MAX_IDLE,
                DEFAULT_MIN_IDLE, DEFAULT_TEST_ON_BORROW, DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,
                DEFAULT_NUM_TESTS_PER_EVICTION_RUN, DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified factory.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory) {
        this(factory, DEFAULT_MAX_ACTIVE, DEFAULT_WHEN_EXHAUSTED_ACTION, DEFAULT_MAX_WAIT, DEFAULT_MAX_IDLE,
                DEFAULT_MIN_IDLE, DEFAULT_TEST_ON_BORROW, DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,
                DEFAULT_NUM_TESTS_PER_EVICTION_RUN, DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified values.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param config a non-<code>null</code> {@link GenericObjectPool.Config} describing my configuration
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, GenericObjectPool.Config config) {
        this(factory, config.maxActive, config.whenExhaustedAction, config.maxWait, config.maxIdle, config.minIdle,
                config.testOnBorrow, config.testOnReturn, config.timeBetweenEvictionRunsMillis,
                config.numTestsPerEvictionRun, config.minEvictableIdleTimeMillis, config.testWhileIdle,
                config.softMinEvictableIdleTimeMillis, config.lifo);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified values.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (see {@link #setMaxActive})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive) {
        this(factory, maxActive, DEFAULT_WHEN_EXHAUSTED_ACTION, DEFAULT_MAX_WAIT, DEFAULT_MAX_IDLE, DEFAULT_MIN_IDLE,
                DEFAULT_TEST_ON_BORROW, DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,
                DEFAULT_NUM_TESTS_PER_EVICTION_RUN, DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified values.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #getWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted an and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #getMaxWait})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait) {
        this(factory, maxActive, whenExhaustedAction, maxWait, DEFAULT_MAX_IDLE, DEFAULT_MIN_IDLE, DEFAULT_TEST_ON_BORROW,
                DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified values.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #getWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted an and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #getMaxWait})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject} method
     * (see {@link #getTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method
     * (see {@link #getTestOnReturn})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            boolean testOnBorrow, boolean testOnReturn) {
        this(factory, maxActive, whenExhaustedAction, maxWait, DEFAULT_MAX_IDLE, DEFAULT_MIN_IDLE, testOnBorrow,
                testOnReturn, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified values.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #getWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #getMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #getMaxIdle})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait, int maxIdle) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, DEFAULT_MIN_IDLE, DEFAULT_TEST_ON_BORROW,
                DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified values.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #getWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #getMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #getMaxIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject} method
     * (see {@link #getTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method
     * (see {@link #getTestOnReturn})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            int maxIdle, boolean testOnBorrow, boolean testOnReturn) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, DEFAULT_MIN_IDLE, testOnBorrow, testOnReturn,
                DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified values.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject}
     * method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method
     * (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle objects
     * for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction thread
     * (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before it
     * is eligible for eviction (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any
     * (see {@link #setTestWhileIdle})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            int maxIdle, boolean testOnBorrow, boolean testOnReturn, long timeBetweenEvictionRunsMillis,
            int numTestsPerEvictionRun, long minEvictableIdleTimeMillis, boolean testWhileIdle) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, DEFAULT_MIN_IDLE, testOnBorrow, testOnReturn,
                timeBetweenEvictionRunsMillis, numTestsPerEvictionRun, minEvictableIdleTimeMillis, testWhileIdle);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified values.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     *  <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param minIdle the minimum number of idle objects in my pool (see {@link #setMinIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject} method
     * (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject} method
     * (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle objects
     * for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction thread
     * (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before
     * it is eligible for eviction (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any
     *  (see {@link #setTestWhileIdle})
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            int maxIdle, int minIdle, boolean testOnBorrow, boolean testOnReturn, long timeBetweenEvictionRunsMillis,
            int numTestsPerEvictionRun, long minEvictableIdleTimeMillis, boolean testWhileIdle) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, minIdle, testOnBorrow, testOnReturn,
                timeBetweenEvictionRunsMillis, numTestsPerEvictionRun, minEvictableIdleTimeMillis, testWhileIdle,
                DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified values.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param minIdle the minimum number of idle objects in my pool (see {@link #setMinIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject}
     * method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject}
     * method (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle objects
     * for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction thread
     * (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before
     * it is eligible for eviction (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any
     * (see {@link #setTestWhileIdle})
     * @param softMinEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before it is
     * eligible for eviction with the extra condition that at least "minIdle" amount of object remain in the pool.
     * (see {@link #setSoftMinEvictableIdleTimeMillis})
     * @since Pool 1.3
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            int maxIdle, int minIdle, boolean testOnBorrow, boolean testOnReturn, long timeBetweenEvictionRunsMillis,
            int numTestsPerEvictionRun, long minEvictableIdleTimeMillis, boolean testWhileIdle,
            long softMinEvictableIdleTimeMillis) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, minIdle, testOnBorrow, testOnReturn,
                timeBetweenEvictionRunsMillis, numTestsPerEvictionRun, minEvictableIdleTimeMillis, testWhileIdle,
                softMinEvictableIdleTimeMillis, DEFAULT_LIFO);
    }

    /**
     * Create a new <code>GenericObjectPool</code> using the specified values.
     * @param factory the (possibly <code>null</code>)PoolableObjectFactory to use to create, validate and destroy objects
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <i>whenExhaustedAction</i> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param minIdle the minimum number of idle objects in my pool (see {@link #setMinIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject}
     * method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject}
     * method (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle
     * objects for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction
     * thread (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before
     * it is eligible for eviction (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any
     * (see {@link #setTestWhileIdle})
     * @param softMinEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the
     * pool before it is eligible for eviction with the extra condition that at least "minIdle" amount of object
     * remain in the pool. (see {@link #setSoftMinEvictableIdleTimeMillis})
     * @param lifo whether or not objects are returned in last-in-first-out order from the idle object pool
     * (see {@link #setLifo})
     * @since Pool 1.4
     */
    public GenericObjectPool(PoolableObjectFactory<T> factory, int maxActive, byte whenExhaustedAction, long maxWait,
            int maxIdle, int minIdle, boolean testOnBorrow, boolean testOnReturn, long timeBetweenEvictionRunsMillis,
            int numTestsPerEvictionRun, long minEvictableIdleTimeMillis, boolean testWhileIdle,
            long softMinEvictableIdleTimeMillis, boolean lifo) {
        _factory = factory;
        // save the current CCL to be used later by the evictor Thread
        _factoryClassLoader = Thread.currentThread().getContextClassLoader();
        _maxActive = maxActive;
        _lifo = lifo;
        switch(whenExhaustedAction) {
            case WHEN_EXHAUSTED_BLOCK:
            case WHEN_EXHAUSTED_FAIL:
            case WHEN_EXHAUSTED_GROW:
                _whenExhaustedAction = whenExhaustedAction;
                break;
            default:
                throw new IllegalArgumentException("whenExhaustedAction " + whenExhaustedAction + " not recognized.");
        }
        _maxWait = maxWait;
        _maxIdle = maxIdle;
        _minIdle = minIdle;
        _testOnBorrow = testOnBorrow;
        _testOnReturn = testOnReturn;
        _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        _softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
        _testWhileIdle = testWhileIdle;

        _pool = new CursorableLinkedList<ObjectTimestampPair<T>>();
        startEvictor(_timeBetweenEvictionRunsMillis);
    }

    //--- public methods ---------------------------------------------

    //--- configuration methods --------------------------------------

    /**
     * Returns the maximum number of objects that can be allocated by the pool
     * (checked out to clients, or idle awaiting checkout) at a given time.
     * When non-positive, there is no limit to the number of objects that can
     * be managed by the pool at one time.
     *
     * @return the cap on the total number of object instances managed by the pool.
     * @see #setMaxActive
     */
    public synchronized int getMaxActive() {
        return _maxActive;
    }

    /**
     * Sets the cap on the number of objects that can be allocated by the pool
     * (checked out to clients, or idle awaiting checkout) at a given time. Use
     * a negative value for no limit.
     *
     * @param maxActive The cap on the total number of object instances managed by the pool.
     * Negative values mean that there is no limit to the number of objects allocated
     * by the pool.
     * @see #getMaxActive
     */
    public void setMaxActive(int maxActive) {
        synchronized(this) {
            _maxActive = maxActive;
        }
        allocate();
    }

    /**
     * Returns the action to take when the {@link #borrowObject} method
     * is invoked when the pool is exhausted (the maximum number
     * of "active" objects has been reached).
     *
     * @return one of {@link #WHEN_EXHAUSTED_BLOCK}, {@link #WHEN_EXHAUSTED_FAIL} or {@link #WHEN_EXHAUSTED_GROW}
     * @see #setWhenExhaustedAction
     */
    public synchronized byte getWhenExhaustedAction() {
        return _whenExhaustedAction;
    }

    /**
     * Sets the action to take when the {@link #borrowObject} method
     * is invoked when the pool is exhausted (the maximum number
     * of "active" objects has been reached).
     *
     * @param whenExhaustedAction the action code, which must be one of
     *        {@link #WHEN_EXHAUSTED_BLOCK}, {@link #WHEN_EXHAUSTED_FAIL},
     *        or {@link #WHEN_EXHAUSTED_GROW}
     * @see #getWhenExhaustedAction
     */
    public void setWhenExhaustedAction(byte whenExhaustedAction) {
        synchronized(this) {
            switch(whenExhaustedAction) {
                case WHEN_EXHAUSTED_BLOCK:
                case WHEN_EXHAUSTED_FAIL:
                case WHEN_EXHAUSTED_GROW:
                    _whenExhaustedAction = whenExhaustedAction;
                    break;
                default:
                    throw new IllegalArgumentException("whenExhaustedAction " + whenExhaustedAction + " not recognized.");
            }
        }
        allocate();
    }


    /**
     * Returns the maximum amount of time (in milliseconds) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #setWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     *
     * When less than or equal to 0, the {@link #borrowObject} method
     * may block indefinitely.
     *
     * @return maximum number of milliseconds to block when borrowing an object.
     * @see #setMaxWait
     * @see #setWhenExhaustedAction
     * @see #WHEN_EXHAUSTED_BLOCK
     */
    public synchronized long getMaxWait() {
        return _maxWait;
    }

    /**
     * Sets the maximum amount of time (in milliseconds) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #setWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     *
     * When less than or equal to 0, the {@link #borrowObject} method
     * may block indefinitely.
     *
     * @param maxWait maximum number of milliseconds to block when borrowing an object.
     * @see #getMaxWait
     * @see #setWhenExhaustedAction
     * @see #WHEN_EXHAUSTED_BLOCK
     */
    public void setMaxWait(long maxWait) {
        synchronized(this) {
            _maxWait = maxWait;
        }
        allocate();
    }

    /**
     * Returns the cap on the number of "idle" instances in the pool.
     * @return the cap on the number of "idle" instances in the pool.
     * @see #setMaxIdle
     */
    public synchronized int getMaxIdle() {
        return _maxIdle;
    }

    /**
     * Sets the cap on the number of "idle" instances in the pool.
     * If maxIdle is set too low on heavily loaded systems it is possible you
     * will see objects being destroyed and almost immediately new objects
     * being created. This is a result of the active threads momentarily
     * returning objects faster than they are requesting them them, causing the
     * number of idle objects to rise above maxIdle. The best value for maxIdle
     * for heavily loaded system will vary but the default is a good starting
     * point.
     * @param maxIdle The cap on the number of "idle" instances in the pool.
     * Use a negative value to indicate an unlimited number of idle instances.
     * @see #getMaxIdle
     */
    public void setMaxIdle(int maxIdle) {
        synchronized(this) {
            _maxIdle = maxIdle;
        }
        allocate();
    }

    /**
     * Sets the minimum number of objects allowed in the pool
     * before the evictor thread (if active) spawns new objects.
     * Note that no objects are created when
     * <code>numActive + numIdle &gt;= maxActive.</code>
     * This setting has no effect if the idle object evictor is disabled
     * (i.e. if <code>timeBetweenEvictionRunsMillis &lt;= 0</code>).
     * <p>
     * If the configured value of minIdle is greater than the configured value
     * for maxIdle then the value of maxIdle will be used instead.
     *
     * @param minIdle The minimum number of objects.
     * @see #getMinIdle
     * @see #getTimeBetweenEvictionRunsMillis()
     */
    public void setMinIdle(int minIdle) {
        synchronized(this) {
            _minIdle = minIdle;
        }
        allocate();
    }

    /**
     * Returns the minimum number of objects allowed in the pool
     * before the evictor thread (if active) spawns new objects.
     * (Note no objects are created when: numActive + numIdle &gt;= maxActive)
     * <p>
     * If the configured value of minIdle is greater than the configured value
     * for maxIdle then the value of maxIdle will be used instead.
     *
     * @return The minimum number of objects.
     * @see #setMinIdle
     */
    public synchronized int getMinIdle() {
        if (_minIdle > _maxIdle) {
            return _maxIdle;
        } else {
            return _minIdle;
        }
    }

    /**
     * When <code>true</code>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned by the {@link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     *
     * @return <code>true</code> if objects are validated before being borrowed.
     * @see #setTestOnBorrow
     */
    public boolean getTestOnBorrow() {
        return _testOnBorrow;
    }

    /**
     * When <code>true</code>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned by the {@link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     *
     * @param testOnBorrow <code>true</code> if objects should be validated before being borrowed.
     * @see #getTestOnBorrow
     */
    public void setTestOnBorrow(boolean testOnBorrow) {
        _testOnBorrow = testOnBorrow;
    }

    /**
     * When <code>true</code>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @return <code>true</code> when objects will be validated after returned to {@link #returnObject}.
     * @see #setTestOnReturn
     */
    public boolean getTestOnReturn() {
        return _testOnReturn;
    }

    /**
     * When <code>true</code>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @param testOnReturn <code>true</code> so objects will be validated after returned to {@link #returnObject}.
     * @see #getTestOnReturn
     */
    public void setTestOnReturn(boolean testOnReturn) {
        _testOnReturn = testOnReturn;
    }

    /**
     * Returns the number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * @return number of milliseconds to sleep between evictor runs.
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized long getTimeBetweenEvictionRunsMillis() {
        return _timeBetweenEvictionRunsMillis;
    }

    /**
     * Sets the number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * @param timeBetweenEvictionRunsMillis number of milliseconds to sleep between evictor runs.
     * @see #getTimeBetweenEvictionRunsMillis
     */
    public synchronized void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        startEvictor(_timeBetweenEvictionRunsMillis);
    }

    /**
     * Returns the max number of objects to examine during each run of the
     * idle object evictor thread (if any).
     *
     * @return max number of objects to examine during each evictor run.
     * @see #setNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized int getNumTestsPerEvictionRun() {
        return _numTestsPerEvictionRun;
    }

    /**
     * Sets the max number of objects to examine during each run of the
     * idle object evictor thread (if any).
     * <p>
     * When a negative value is supplied, <code>ceil({@link #getNumIdle})/abs({@link #getNumTestsPerEvictionRun})</code>
     * tests will be run.  That is, when the value is <i>-n</i>, roughly one <i>n</i>th of the
     * idle objects will be tested per run. When the value is positive, the number of tests
     * actually performed in each run will be the minimum of this value and the number of instances
     * idle in the pool.
     *
     * @param numTestsPerEvictionRun max number of objects to examine during each evictor run.
     * @see #getNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Returns the minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any).
     *
     * @return minimum amount of time an object may sit idle in the pool before it is eligible for eviction.
     * @see #setMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized long getMinEvictableIdleTimeMillis() {
        return _minEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any).
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     * @param minEvictableIdleTimeMillis minimum amount of time an object may sit idle in the pool before
     * it is eligible for eviction.
     * @see #getMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * Returns the minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any), with the extra condition that at least
     * "minIdle" amount of object remain in the pool.
     *
     * @return minimum amount of time an object may sit idle in the pool before it is eligible for eviction.
     * @since Pool 1.3
     * @see #setSoftMinEvictableIdleTimeMillis
     */
    public synchronized long getSoftMinEvictableIdleTimeMillis() {
        return _softMinEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any), with the extra condition that at least
     * "minIdle" object instances remain in the pool.
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     *
     * @param softMinEvictableIdleTimeMillis minimum amount of time an object may sit idle in the pool before
     * it is eligible for eviction.
     * @since Pool 1.3
     * @see #getSoftMinEvictableIdleTimeMillis
     */
    public synchronized void setSoftMinEvictableIdleTimeMillis(long softMinEvictableIdleTimeMillis) {
        _softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
    }

    /**
     * When <code>true</code>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @return <code>true</code> when objects will be validated by the evictor.
     * @see #setTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized boolean getTestWhileIdle() {
        return _testWhileIdle;
    }

    /**
     * When <code>true</code>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @param testWhileIdle <code>true</code> so objects will be validated by the evictor.
     * @see #getTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized void setTestWhileIdle(boolean testWhileIdle) {
        _testWhileIdle = testWhileIdle;
    }

    /**
     * Whether or not the idle object pool acts as a LIFO queue. True means
     * that borrowObject returns the most recently used ("last in") idle object
     * in the pool (if there are idle instances available).  False means that
     * the pool behaves as a FIFO queue - objects are taken from the idle object
     * pool in the order that they are returned to the pool.
     *
     * @return <code>true</code> if the pool is configured to act as a LIFO queue
     * @since 1.4
     */
     public synchronized boolean getLifo() {
         return _lifo;
     }

     /**
      * Sets the LIFO property of the pool. True means that borrowObject returns
      * the most recently used ("last in") idle object in the pool (if there are
      * idle instances available).  False means that the pool behaves as a FIFO
      * queue - objects are taken from the idle object pool in the order that
      * they are returned to the pool.
      *
      * @param lifo the new value for the LIFO property
      * @since 1.4
      */
     public synchronized void setLifo(boolean lifo) {
         this._lifo = lifo;
     }

    /**
     * Sets my configuration.
     *
     * @param conf configuration to use.
     * @see GenericObjectPool.Config
     */
    public void setConfig(GenericObjectPool.Config conf) {
        synchronized (this) {
            setMaxIdle(conf.maxIdle);
            setMinIdle(conf.minIdle);
            setMaxActive(conf.maxActive);
            setMaxWait(conf.maxWait);
            setWhenExhaustedAction(conf.whenExhaustedAction);
            setTestOnBorrow(conf.testOnBorrow);
            setTestOnReturn(conf.testOnReturn);
            setTestWhileIdle(conf.testWhileIdle);
            setNumTestsPerEvictionRun(conf.numTestsPerEvictionRun);
            setMinEvictableIdleTimeMillis(conf.minEvictableIdleTimeMillis);
            setTimeBetweenEvictionRunsMillis(conf.timeBetweenEvictionRunsMillis);
            setSoftMinEvictableIdleTimeMillis(conf.softMinEvictableIdleTimeMillis);
            setLifo(conf.lifo);
        }
        allocate();
    }

    //-- ObjectPool methods ------------------------------------------

    /**
     * <p>Borrows an object from the pool.</p>
     *
     * <p>If there is an idle instance available in the pool, then either the most-recently returned
     * (if {@link #getLifo() lifo} == true) or "oldest" (lifo == false) instance sitting idle in the pool
     * will be activated and returned.  If activation fails, or {@link #getTestOnBorrow() testOnBorrow} is set
     * to true and validation fails, the instance is destroyed and the next available instance is examined.
     * This continues until either a valid instance is returned or there are no more idle instances available.</p>
     *
     * <p>If there are no idle instances available in the pool, behavior depends on the {@link #getMaxActive() maxActive}
     * and (if applicable) {@link #getWhenExhaustedAction() whenExhaustedAction} and {@link #getMaxWait() maxWait}
     * properties. If the number of instances checked out from the pool is less than <code>maxActive,</code> a new
     * instance is created, activated and (if applicable) validated and returned to the caller.</p>
     *
     * <p>If the pool is exhausted (no available idle instances and no capacity to create new ones),
     * this method will either block ({@link #WHEN_EXHAUSTED_BLOCK}), throw a <code>NoSuchElementException</code>
     * ({@link #WHEN_EXHAUSTED_FAIL}), or grow ({@link #WHEN_EXHAUSTED_GROW} - ignoring maxActive).
     * The length of time that this method will block when <code>whenExhaustedAction == WHEN_EXHAUSTED_BLOCK</code>
     * is determined by the {@link #getMaxWait() maxWait} property.</p>
     *
     * <p>When the pool is exhausted, multiple calling threads may be simultaneously blocked waiting for instances
     * to become available.  As of pool 1.5, a "fairness" algorithm has been implemented to ensure that threads receive
     * available instances in request arrival order.</p>
     *
     * @return object instance
     * @throws NoSuchElementException if an instance cannot be returned
     */
    @Override
    public T borrowObject() throws Exception {
        long starttime = System.currentTimeMillis();
        Latch<T> latch = new Latch<T>();
        byte whenExhaustedAction;
        long maxWait;
        synchronized (this) {
            // Get local copy of current config. Can't sync when used later as
            // it can result in a deadlock. Has the added advantage that config
            // is consistent for entire method execution
            whenExhaustedAction = _whenExhaustedAction;
            maxWait = _maxWait;

            // Add this request to the queue
            _allocationQueue.add(latch);
        }
        // Work the allocation queue, allocating idle instances and
        // instance creation permits in request arrival order
        allocate();

        for(;;) {
            synchronized (this) {
                assertOpen();
            }

            // If no object was allocated from the pool above
            if(latch.getPair() == null) {
                // check if we were allowed to create one
                if(latch.mayCreate()) {
                    // allow new object to be created
                } else {
                    // the pool is exhausted
                    switch(whenExhaustedAction) {
                        case WHEN_EXHAUSTED_GROW:
                            // allow new object to be created
                            synchronized (this) {
                                // Make sure another thread didn't allocate us an object
                                // or permit a new object to be created
                                if (latch.getPair() == null && !latch.mayCreate()) {
                                    _allocationQueue.remove(latch);
                                    _numInternalProcessing++;
                                }
                            }
                            break;
                        case WHEN_EXHAUSTED_FAIL:
                            synchronized (this) {
                                // Make sure allocate hasn't already assigned an object
                                // in a different thread or permitted a new object to be created
                                if (latch.getPair() != null || latch.mayCreate()) {
                                    break;
                                }
                                _allocationQueue.remove(latch);
                            }
                            throw new NoSuchElementException("Pool exhausted");
                        case WHEN_EXHAUSTED_BLOCK:
                            try {
                                synchronized (latch) {
                                    // Before we wait, make sure another thread didn't allocate us an object
                                    // or permit a new object to be created
                                    if (latch.getPair() == null && !latch.mayCreate()) {
                                        if(maxWait <= 0) {
                                            latch.wait();
                                        } else {
                                            // this code may be executed again after a notify then continue cycle
                                            // so, need to calculate the amount of time to wait
                                            final long elapsed = (System.currentTimeMillis() - starttime);
                                            final long waitTime = maxWait - elapsed;
                                            if (waitTime > 0)
                                            {
                                                latch.wait(waitTime);
                                            }
                                        }
                                    } else {
                                        break;
                                    }
                                }
                                // see if we were awakened by a closing pool
                                if(isClosed() == true) {
                                    throw new IllegalStateException("Pool closed");
                                }
                            } catch(InterruptedException e) {
                                boolean doAllocate = false;
                                synchronized(this) {
                                    // Need to handle the all three possibilities
                                    if (latch.getPair() == null && !latch.mayCreate()) {
                                        // Case 1: latch still in allocation queue
                                        // Remove latch from the allocation queue
                                        _allocationQueue.remove(latch);
                                    } else if (latch.getPair() == null && latch.mayCreate()) {
                                        // Case 2: latch has been given permission to create
                                        //         a new object
                                        _numInternalProcessing--;
                                        doAllocate = true;
                                    } else {
                                        // Case 3: An object has been allocated
                                        _numInternalProcessing--;
                                        _numActive++;
                                        returnObject(latch.getPair().getValue());
                                    }
                                }
                                if (doAllocate) {
                                    allocate();
                                }
                                Thread.currentThread().interrupt();
                                throw e;
                            }
                            if(maxWait > 0 && ((System.currentTimeMillis() - starttime) >= maxWait)) {
                                synchronized(this) {
                                    // Make sure allocate hasn't already assigned an object
                                    // in a different thread or permitted a new object to be created
                                    if (latch.getPair() == null && !latch.mayCreate()) {
                                        // Remove latch from the allocation queue
                                        _allocationQueue.remove(latch);
                                    } else {
                                        break;
                                    }
                                }
                                throw new NoSuchElementException("Timeout waiting for idle object");
                            } else {
                                continue; // keep looping
                            }
                        default:
                            throw new IllegalArgumentException("WhenExhaustedAction property " + whenExhaustedAction +
                                    " not recognized.");
                    }
                }
            }

            boolean newlyCreated = false;
            if(null == latch.getPair()) {
                try {
                    T obj = _factory.makeObject();
                    latch.setPair(new ObjectTimestampPair<T>(obj));
                    newlyCreated = true;
                } finally {
                    if (!newlyCreated) {
                        // object cannot be created
                        synchronized (this) {
                            _numInternalProcessing--;
                            // No need to reset latch - about to throw exception
                        }
                        allocate();
                    }
                }
            }
            // activate & validate the object
            try {
                _factory.activateObject(latch.getPair().getValue());
                if(_testOnBorrow &&
                        !_factory.validateObject(latch.getPair().getValue())) {
                    throw new Exception("ValidateObject failed");
                }
                synchronized(this) {
                    _numInternalProcessing--;
                    _numActive++;
                }
                return latch.getPair().getValue();
            }
            catch (Throwable e) {
                PoolUtils.checkRethrow(e);
                // object cannot be activated or is invalid
                try {
                    _factory.destroyObject(latch.getPair().getValue());
                } catch (Throwable e2) {
                    PoolUtils.checkRethrow(e2);
                    // cannot destroy broken object
                }
                synchronized (this) {
                    _numInternalProcessing--;
                    if (!newlyCreated) {
                        latch.reset();
                        _allocationQueue.add(0, latch);
                    }
                }
                allocate();
                if(newlyCreated) {
                    throw new NoSuchElementException("Could not create a validated object, cause: " + e.getMessage());
                }
                else {
                    continue; // keep looping
                }
            }
        }
    }

    /**
     * Allocate available instances to latches in the allocation queue.  Then
     * set _mayCreate to true for as many additional latches remaining in queue
     * as _maxActive allows. While it is safe for GOP, for consistency with GKOP
     * this method should not be called from inside a sync block.
     */
    private synchronized void allocate() {
        if (isClosed()) {
            return;
        }

        // First use any objects in the pool to clear the queue
        for (;;) {
            if (!_pool.isEmpty() && !_allocationQueue.isEmpty()) {
                Latch<T> latch = _allocationQueue.removeFirst();
                latch.setPair( _pool.removeFirst());
                _numInternalProcessing++;
                synchronized (latch) {
                    latch.notify();
                }
            } else {
                break;
            }
        }

        // Second utilise any spare capacity to create new objects
        for(;;) {
            if((!_allocationQueue.isEmpty()) && (_maxActive < 0 || (_numActive + _numInternalProcessing) < _maxActive)) {
                Latch<T> latch = _allocationQueue.removeFirst();
                latch.setMayCreate(true);
                _numInternalProcessing++;
                synchronized (latch) {
                    latch.notify();
                }
            } else {
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>Activation of this method decrements the active count and attempts to destroy the instance.</p>
     *
     * @throws Exception if the configured {@link PoolableObjectFactory} throws an exception destroying obj
     */
    @Override
    public void invalidateObject(T obj) throws Exception {
        try {
            if (_factory != null) {
                _factory.destroyObject(obj);
            }
        } finally {
            synchronized (this) {
                _numActive--;
            }
            allocate();
        }
    }

    /**
     * Clears any objects sitting idle in the pool by removing them from the
     * idle instance pool and then invoking the configured
     * {@link PoolableObjectFactory#destroyObject(Object)} method on each idle
     * instance.
     *
     * <p> Implementation notes:
     * <ul><li>This method does not destroy or effect in any way instances that are
     * checked out of the pool when it is invoked.</li>
     * <li>Invoking this method does not prevent objects being
     * returned to the idle instance pool, even during its execution. It locks
     * the pool only during instance removal. Additional instances may be returned
     * while removed items are being destroyed.</li>
     * <li>Exceptions encountered destroying idle instances are swallowed.</li></ul>
     */
    @Override
    public void clear() {
        List<ObjectTimestampPair<T>> toDestroy = new ArrayList<ObjectTimestampPair<T>>();

        synchronized(this) {
            toDestroy.addAll(_pool);
            _numInternalProcessing = _numInternalProcessing + _pool._size;
            _pool.clear();
        }
        destroy(toDestroy, _factory);
    }

    /**
     * Private method to destroy all the objects in a collection using the
     * supplied object factory.  Assumes that objects in the collection are
     * instances of ObjectTimestampPair and that the object instances that
     * they wrap were created by the factory.
     *
     * @param c Collection of objects to destroy
     * @param factory PoolableConnectionFactory used to destroy the objects
     */
    private void destroy(Collection<ObjectTimestampPair<T>> c, PoolableObjectFactory<T> factory) {
        for (Iterator<ObjectTimestampPair<T>> it = c.iterator(); it.hasNext();) {
            try {
                factory.destroyObject(it.next().getValue());
            } catch(Exception e) {
                // ignore error, keep destroying the rest
            } finally {
                synchronized(this) {
                    _numInternalProcessing--;
                }
                allocate();
            }
        }
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
     * Return the number of instances currently idle in this pool.
     *
     * @return the number of instances currently idle in this pool
     */
    @Override
    public synchronized int getNumIdle() {
        return _pool.size();
    }

    /**
     * <p>Returns an object instance to the pool.</p>
     *
     * <p>If {@link #getMaxIdle() maxIdle} is set to a positive value and the number of idle instances
     * has reached this value, the returning instance is destroyed.</p>
     *
     * <p>If {@link #getTestOnReturn() testOnReturn} == true, the returning instance is validated before being returned
     * to the idle instance pool.  In this case, if validation fails, the instance is destroyed.</p>
     *
     * <p><strong>Note: </strong> There is no guard to prevent an object
     * being returned to the pool multiple times. Clients are expected to
     * discard references to returned objects and ensure that an object is not
     * returned to the pool multiple times in sequence (i.e., without being
     * borrowed again between returns). Violating this contract will result in
     * the same object appearing multiple times in the pool and pool counters
     * (numActive, numIdle) returning incorrect values.</p>
     *
     * @param obj instance to return to the pool
     */
    @Override
    public void returnObject(T obj) throws Exception {
        try {
            addObjectToPool(obj, true);
        } catch (Exception e) {
            if (_factory != null) {
                try {
                    _factory.destroyObject(obj);
                } catch (Exception e2) {
                    // swallowed
                }
                // TODO: Correctness here depends on control in addObjectToPool.
                // These two methods should be refactored, removing the
                // "behavior flag", decrementNumActive, from addObjectToPool.
                synchronized(this) {
                    _numActive--;
                }
                allocate();
            }
        }
    }

    /**
     * <p>Adds an object to the pool.</p>
     *
     * <p>Validates the object if testOnReturn == true and passivates it before returning it to the pool.
     * if validation or passivation fails, or maxIdle is set and there is no room in the pool, the instance
     * is destroyed.</p>
     *
     * <p>Calls {@link #allocate()} on successful completion</p>
     *
     * @param obj instance to add to the pool
     * @param decrementNumActive whether or not to decrement the active count
     * @throws Exception
     */
    private void addObjectToPool(T obj, boolean decrementNumActive) throws Exception {
        boolean success = true;
        if(_testOnReturn && !(_factory.validateObject(obj))) {
            success = false;
        } else {
            _factory.passivateObject(obj);
        }

        boolean shouldDestroy = !success;

        // Add instance to pool if there is room and it has passed validation
        // (if testOnreturn is set)
        boolean doAllocate = false;
        synchronized (this) {
            if (isClosed()) {
                shouldDestroy = true;
            } else {
                if((_maxIdle >= 0) && (_pool.size() >= _maxIdle)) {
                    shouldDestroy = true;
                } else if(success) {
                    // borrowObject always takes the first element from the queue,
                    // so for LIFO, push on top, FIFO add to end
                    if (_lifo) {
                        _pool.addFirst(new ObjectTimestampPair<T>(obj));
                    } else {
                        _pool.addLast(new ObjectTimestampPair<T>(obj));
                    }
                    if (decrementNumActive) {
                        _numActive--;
                    }
                    doAllocate = true;
                }
            }
        }
        if (doAllocate) {
            allocate();
        }

        // Destroy the instance if necessary
        if(shouldDestroy) {
            try {
                _factory.destroyObject(obj);
            } catch(Exception e) {
                // ignored
            }
            // Decrement active count *after* destroy if applicable
            if (decrementNumActive) {
                synchronized(this) {
                    _numActive--;
                }
                allocate();
            }
        }

    }

    /**
     * <p>Closes the pool.  Once the pool is closed, {@link #borrowObject()}
     * will fail with IllegalStateException, but {@link #returnObject(Object)} and
     * {@link #invalidateObject(Object)} will continue to work, with returned objects
     * destroyed on return.</p>
     *
     * <p>Destroys idle instances in the pool by invoking {@link #clear()}.</p>
     *
     * @throws Exception
     */
    @Override
    public void close() throws Exception {
        super.close();
        synchronized (this) {
            clear();
            startEvictor(-1L);

            while(_allocationQueue.size() > 0) {
                Latch<T> l = _allocationQueue.removeFirst();

                synchronized (l) {
                    // notify the waiting thread
                    l.notify();
                }
            }
        }
    }

    /**
     * Sets the {@link PoolableObjectFactory factory} this pool uses
     * to create new instances. Trying to change
     * the <code>factory</code> while there are borrowed objects will
     * throw an {@link IllegalStateException}.  If there are instances idle
     * in the pool when this method is invoked, these will be destroyed
     * using the original factory.
     *
     * @param factory the {@link PoolableObjectFactory} used to create new instances.
     * @throws IllegalStateException when the factory cannot be set at this time
     * @deprecated to be removed in version 2.0
     */
    @Deprecated
    @Override
    public void setFactory(PoolableObjectFactory<T> factory) throws IllegalStateException {
        List<ObjectTimestampPair<T>> toDestroy = new ArrayList<ObjectTimestampPair<T>>();
        final PoolableObjectFactory<T> oldFactory = _factory;
        synchronized (this) {
            assertOpen();
            if(0 < getNumActive()) {
                throw new IllegalStateException("Objects are already active");
            } else {
                toDestroy.addAll(_pool);
                _numInternalProcessing = _numInternalProcessing + _pool._size;
                _pool.clear();
            }
            _factory = factory;
            // save the current CCL to be used later by the evictor Thread
            _factoryClassLoader = Thread.currentThread().getContextClassLoader();
        }
        destroy(toDestroy, oldFactory);
    }

    /**
     * <p>Perform <code>numTests</code> idle object eviction tests, evicting
     * examined objects that meet the criteria for eviction. If
     * <code>testWhileIdle</code> is true, examined objects are validated
     * when visited (and removed if invalid); otherwise only objects that
     * have been idle for more than <code>minEvicableIdletimeMillis</code>
     * are removed.</p>
     *
     * <p>Successive activations of this method examine objects in
     * in sequence, cycling through objects in oldest-to-youngest order.</p>
     *
     * @throws Exception if the pool is closed or eviction fails.
     */
    public void evict() throws Exception {
        assertOpen();
        synchronized (this) {
            if(_pool.isEmpty()) {
                return;
            }
            if (null == _evictionCursor) {
                _evictionCursor = _pool.cursor(_lifo ? _pool.size() : 0);
            }
        }

        for (int i=0,m=getNumTests();i<m;i++) {
            final ObjectTimestampPair<T> pair;
            synchronized (this) {
                if ((_lifo && !_evictionCursor.hasPrevious()) ||
                        !_lifo && !_evictionCursor.hasNext()) {
                    _evictionCursor.close();
                    _evictionCursor = _pool.cursor(_lifo ? _pool.size() : 0);
                }

                pair = _lifo ?
                         _evictionCursor.previous() :
                         _evictionCursor.next();

                _evictionCursor.remove();
                _numInternalProcessing++;
            }

            boolean removeObject = false;
            final long idleTimeMilis = System.currentTimeMillis() - pair.getTstamp();
            if ((getMinEvictableIdleTimeMillis() > 0) &&
                    (idleTimeMilis > getMinEvictableIdleTimeMillis())) {
                removeObject = true;
            } else if ((getSoftMinEvictableIdleTimeMillis() > 0) &&
                    (idleTimeMilis > getSoftMinEvictableIdleTimeMillis()) &&
                    ((getNumIdle() + 1)> getMinIdle())) { // +1 accounts for object we are processing
                removeObject = true;
            }
            if(getTestWhileIdle() && !removeObject) {
                boolean active = false;
                try {
                    _factory.activateObject(pair.getValue());
                    active = true;
                } catch(Exception e) {
                    removeObject=true;
                }
                if(active) {
                    if(!_factory.validateObject(pair.getValue())) {
                        removeObject=true;
                    } else {
                        try {
                            _factory.passivateObject(pair.getValue());
                        } catch(Exception e) {
                            removeObject=true;
                        }
                    }
                }
            }

            if (removeObject) {
                try {
                    _factory.destroyObject(pair.getValue());
                } catch(Exception e) {
                    // ignored
                }
            }
            synchronized (this) {
                if(!removeObject) {
                    _evictionCursor.add(pair);
                    if (_lifo) {
                        // Skip over the element we just added back
                        _evictionCursor.previous();
                    }
                }
                _numInternalProcessing--;
            }
        }
        allocate();
    }

    /**
     * Check to see if we are below our minimum number of objects
     * if so enough to bring us back to our minimum.
     *
     * @throws Exception when {@link #addObject()} fails.
     */
    private void ensureMinIdle() throws Exception {
        // this method isn't synchronized so the
        // calculateDeficit is done at the beginning
        // as a loop limit and a second time inside the loop
        // to stop when another thread already returned the
        // needed objects
        int objectDeficit = calculateDeficit(false);
        for ( int j = 0 ; j < objectDeficit && calculateDeficit(true) > 0 ; j++ ) {
            try {
                addObject();
            } finally {
                synchronized (this) {
                    _numInternalProcessing--;
                }
                allocate();
            }
        }
    }

    /**
     * This returns the number of objects to create during the pool
     * sustain cycle. This will ensure that the minimum number of idle
     * instances is maintained without going past the maxActive value.
     *
     * @param incrementInternal - Should the count of objects currently under
     *                            some form of internal processing be
     *                            incremented?
     * @return The number of objects to be created
     */
    private synchronized int calculateDeficit(boolean incrementInternal) {
        int objectDeficit = getMinIdle() - getNumIdle();
        if (_maxActive > 0) {
            int growLimit = Math.max(0,
                    getMaxActive() - getNumActive() - getNumIdle() - _numInternalProcessing);
            objectDeficit = Math.min(objectDeficit, growLimit);
        }
        if (incrementInternal && objectDeficit >0) {
            _numInternalProcessing++;
        }
        return objectDeficit;
    }

    /**
     * Create an object, and place it into the pool.
     * addObject() is useful for "pre-loading" a pool with idle objects.
     */
    @Override
    public void addObject() throws Exception {
        assertOpen();
        if (_factory == null) {
            throw new IllegalStateException("Cannot add objects without a factory.");
        }
        T obj = _factory.makeObject();
        try {
            assertOpen();
            addObjectToPool(obj, false);
        } catch (IllegalStateException ex) { // Pool closed
            try {
                _factory.destroyObject(obj);
            } catch (Exception ex2) {
                // swallow
            }
            throw ex;
        }
    }

    //--- non-public methods ----------------------------------------

    /**
     * Start the eviction thread or service, or when
     * <i>delay</i> is non-positive, stop it
     * if it is already running.
     *
     * @param delay milliseconds between evictor runs.
     */
    protected synchronized void startEvictor(long delay) {
        if(null != _evictor) {
            EvictionTimer.cancel(_evictor);
            _evictor = null;
        }
        if(delay > 0) {
            _evictor = new Evictor();
            EvictionTimer.schedule(_evictor, delay, delay);
        }
    }

    /**
     * Returns pool info including {@link #getNumActive()}, {@link #getNumIdle()}
     * and a list of objects idle in the pool with their idle times.
     *
     * @return string containing debug information
     */
    synchronized String debugInfo() {
        StringBuffer buf = new StringBuffer();
        buf.append("Active: ").append(getNumActive()).append("\n");
        buf.append("Idle: ").append(getNumIdle()).append("\n");
        buf.append("Idle Objects:\n");
        Iterator<ObjectTimestampPair<T>> it = _pool.iterator();
        long time = System.currentTimeMillis();
        while(it.hasNext()) {
            ObjectTimestampPair<T> pair = it.next();
            buf.append("\t").append(pair.getValue()).append("\t").append(time - pair.getTstamp()).append("\n");
        }
        return buf.toString();
    }

    /**
     * Returns the number of tests to be performed in an Evictor run,
     * based on the current value of <code>numTestsPerEvictionRun</code>
     * and the number of idle instances in the pool.
     *
     * @see #setNumTestsPerEvictionRun
     * @return the number of tests for the Evictor to run
     */
    private int getNumTests() {
        if(_numTestsPerEvictionRun >= 0) {
            return Math.min(_numTestsPerEvictionRun, _pool.size());
        } else {
            return(int)(Math.ceil(_pool.size()/Math.abs((double)_numTestsPerEvictionRun)));
        }
    }

    //--- inner classes ----------------------------------------------

    /**
     * The idle object evictor {@link TimerTask}.
     * @see GenericObjectPool#setTimeBetweenEvictionRunsMillis
     */
    private class Evictor extends TimerTask {
        /**
         * Run pool maintenance.  Evict objects qualifying for eviction and then
         * invoke {@link GenericObjectPool#ensureMinIdle()}.
         * Since the Timer that invokes Evictors is shared for all Pools, we try
         * to restore the ContextClassLoader that created the pool.
         */
        @Override
        public void run() {
            ClassLoader savedClassLoader =
                    Thread.currentThread().getContextClassLoader();
            try {
                //set the classloader for the factory
                Thread.currentThread().setContextClassLoader(
                        _factoryClassLoader);
                try {
                    evict();
                } catch(Exception e) {
                    // ignored
                } catch(OutOfMemoryError oome) {
                    // Log problem but give evictor thread a chance to continue in
                    // case error is recoverable
                    oome.printStackTrace(System.err);
                }
                try {
                    ensureMinIdle();
                } catch(Exception e) {
                    // ignored
                }
            } finally {
                //restore the previous CCL
                Thread.currentThread().setContextClassLoader(savedClassLoader);
            }
        }
    }

    /**
     * A simple "struct" encapsulating the
     * configuration information for a {@link GenericObjectPool}.
     * @see GenericObjectPool#GenericObjectPool(org.apache.tomcat.dbcp.pool.PoolableObjectFactory,
     * org.apache.tomcat.dbcp.pool.impl.GenericObjectPool.Config)
     * @see GenericObjectPool#setConfig
     */
    public static class Config {
        //CHECKSTYLE: stop VisibilityModifier
        /**
         * @see GenericObjectPool#setMaxIdle
         */
        public int maxIdle = GenericObjectPool.DEFAULT_MAX_IDLE;
        /**
         * @see GenericObjectPool#setMinIdle
         */
        public int minIdle = GenericObjectPool.DEFAULT_MIN_IDLE;
        /**
         * @see GenericObjectPool#setMaxActive
         */
        public int maxActive = GenericObjectPool.DEFAULT_MAX_ACTIVE;
        /**
         * @see GenericObjectPool#setMaxWait
         */
        public long maxWait = GenericObjectPool.DEFAULT_MAX_WAIT;
        /**
         * @see GenericObjectPool#setWhenExhaustedAction
         */
        public byte whenExhaustedAction = GenericObjectPool.DEFAULT_WHEN_EXHAUSTED_ACTION;
        /**
         * @see GenericObjectPool#setTestOnBorrow
         */
        public boolean testOnBorrow = GenericObjectPool.DEFAULT_TEST_ON_BORROW;
        /**
         * @see GenericObjectPool#setTestOnReturn
         */
        public boolean testOnReturn = GenericObjectPool.DEFAULT_TEST_ON_RETURN;
        /**
         * @see GenericObjectPool#setTestWhileIdle
         */
        public boolean testWhileIdle = GenericObjectPool.DEFAULT_TEST_WHILE_IDLE;
        /**
         * @see GenericObjectPool#setTimeBetweenEvictionRunsMillis
         */
        public long timeBetweenEvictionRunsMillis = GenericObjectPool.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
        /**
         * @see GenericObjectPool#setNumTestsPerEvictionRun
         */
        public int numTestsPerEvictionRun =  GenericObjectPool.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
        /**
         * @see GenericObjectPool#setMinEvictableIdleTimeMillis
         */
        public long minEvictableIdleTimeMillis = GenericObjectPool.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
        /**
         * @see GenericObjectPool#setSoftMinEvictableIdleTimeMillis
         */
        public long softMinEvictableIdleTimeMillis = GenericObjectPool.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
        /**
         * @see GenericObjectPool#setLifo
         */
        public boolean lifo = GenericObjectPool.DEFAULT_LIFO;
        //CHECKSTYLE: resume VisibilityModifier
    }

    /**
     * Latch used to control allocation order of objects to threads to ensure
     * fairness. That is, objects are allocated to threads in the order that
     * threads request objects.
     */
    private static final class Latch<T> {

        /** object timestamp pair allocated to this latch */
        private ObjectTimestampPair<T> _pair;

        /** Whether or not this latch may create an object instance */
        private boolean _mayCreate = false;

        /**
         * Returns ObjectTimestampPair allocated to this latch
         * @return ObjectTimestampPair allocated to this latch
         */
        private synchronized ObjectTimestampPair<T> getPair() {
            return _pair;
        }

        /**
         * Sets ObjectTimestampPair on this latch
         * @param pair ObjectTimestampPair allocated to this latch
         */
        private synchronized void setPair(ObjectTimestampPair<T> pair) {
            _pair = pair;
        }

        /**
         * Whether or not this latch may create an object instance
         * @return true if this latch has an instance creation permit
         */
        private synchronized boolean mayCreate() {
            return _mayCreate;
        }

        /**
         * Sets the mayCreate property
         * @param mayCreate new value for mayCreate
         */
        private synchronized void setMayCreate(boolean mayCreate) {
            _mayCreate = mayCreate;
        }

        /**
         * Reset the latch data. Used when an allocation fails and the latch
         * needs to be re-added to the queue.
         */
        private synchronized void reset() {
            _pair = null;
            _mayCreate = false;
        }
    }


    //--- private attributes ---------------------------------------

    /**
     * The cap on the number of idle instances in the pool.
     * @see #setMaxIdle
     * @see #getMaxIdle
     */
    private int _maxIdle = DEFAULT_MAX_IDLE;

    /**
    * The cap on the minimum number of idle instances in the pool.
    * @see #setMinIdle
    * @see #getMinIdle
    */
    private int _minIdle = DEFAULT_MIN_IDLE;

    /**
     * The cap on the total number of active instances from the pool.
     * @see #setMaxActive
     * @see #getMaxActive
     */
    private int _maxActive = DEFAULT_MAX_ACTIVE;

    /**
     * The maximum amount of time (in millis) the
     * {@link #borrowObject} method should block before throwing
     * an exception when the pool is exhausted and the
     * {@link #getWhenExhaustedAction "when exhausted" action} is
     * {@link #WHEN_EXHAUSTED_BLOCK}.
     *
     * When less than or equal to 0, the {@link #borrowObject} method
     * may block indefinitely.
     *
     * @see #setMaxWait
     * @see #getMaxWait
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #setWhenExhaustedAction
     * @see #getWhenExhaustedAction
     */
    private long _maxWait = DEFAULT_MAX_WAIT;

    /**
     * The action to take when the {@link #borrowObject} method
     * is invoked when the pool is exhausted (the maximum number
     * of "active" objects has been reached).
     *
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #DEFAULT_WHEN_EXHAUSTED_ACTION
     * @see #setWhenExhaustedAction
     * @see #getWhenExhaustedAction
     */
    private byte _whenExhaustedAction = DEFAULT_WHEN_EXHAUSTED_ACTION;

    /**
     * When <code>true</code>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned by the {@link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     *
     * @see #setTestOnBorrow
     * @see #getTestOnBorrow
     */
    private volatile boolean _testOnBorrow = DEFAULT_TEST_ON_BORROW;

    /**
     * When <code>true</code>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @see #getTestOnReturn
     * @see #setTestOnReturn
     */
    private volatile boolean _testOnReturn = DEFAULT_TEST_ON_RETURN;

    /**
     * When <code>true</code>, objects will be
     * {@link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @see #setTestWhileIdle
     * @see #getTestWhileIdle
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    private boolean _testWhileIdle = DEFAULT_TEST_WHILE_IDLE;

    /**
     * The number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * @see #setTimeBetweenEvictionRunsMillis
     * @see #getTimeBetweenEvictionRunsMillis
     */
    private long _timeBetweenEvictionRunsMillis = DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    /**
     * The max number of objects to examine during each run of the
     * idle object evictor thread (if any).
     * <p>
     * When a negative value is supplied, <code>ceil({@link #getNumIdle})/abs({@link #getNumTestsPerEvictionRun})</code>
     * tests will be run.  I.e., when the value is <i>-n</i>, roughly one <i>n</i>th of the
     * idle objects will be tested per run.
     *
     * @see #setNumTestsPerEvictionRun
     * @see #getNumTestsPerEvictionRun
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    private int _numTestsPerEvictionRun =  DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    /**
     * The minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any).
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     *
     * @see #setMinEvictableIdleTimeMillis
     * @see #getMinEvictableIdleTimeMillis
     * @see #getTimeBetweenEvictionRunsMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    private long _minEvictableIdleTimeMillis = DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * The minimum amount of time an object may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any), with the extra condition that at least
     * "minIdle" amount of object remain in the pool.
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     *
     * @see #setSoftMinEvictableIdleTimeMillis
     * @see #getSoftMinEvictableIdleTimeMillis
     */
    private long _softMinEvictableIdleTimeMillis = DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /** Whether or not the pool behaves as a LIFO queue (last in first out) */
    private boolean _lifo = DEFAULT_LIFO;

    /** My pool. */
    private CursorableLinkedList<ObjectTimestampPair<T>> _pool = null;

    /** Eviction cursor - keeps track of idle object evictor position */
    private CursorableLinkedList<ObjectTimestampPair<T>>.Cursor _evictionCursor = null;

    /** My {@link PoolableObjectFactory}. */
    private PoolableObjectFactory<T> _factory = null;

    /**
     * Class loader for evictor thread to use since in a J2EE or similar
     * environment the context class loader for the evictor thread may have
     * visibility of the correct factory. See POOL-161.
     */
    private ClassLoader _factoryClassLoader = null;

    /**
     * The number of objects {@link #borrowObject} borrowed
     * from the pool, but not yet returned.
     */
    private int _numActive = 0;

    /**
     * My idle object eviction {@link TimerTask}, if any.
     */
    private Evictor _evictor = null;

    /**
     * The number of objects subject to some form of internal processing
     * (usually creation or destruction) that should be included in the total
     * number of objects but are neither active nor idle.
     */
    private int _numInternalProcessing = 0;

    /**
     * Used to track the order in which threads call {@link #borrowObject()} so
     * that objects can be allocated in the order in which the threads requested
     * them.
     */
    private final LinkedList<Latch<T>> _allocationQueue = new LinkedList<Latch<T>>();

}

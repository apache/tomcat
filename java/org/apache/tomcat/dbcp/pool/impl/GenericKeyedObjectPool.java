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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TimerTask;
import java.util.TreeMap;

import org.apache.tomcat.dbcp.pool.BaseKeyedObjectPool;
import org.apache.tomcat.dbcp.pool.KeyedPoolableObjectFactory;
import org.apache.tomcat.dbcp.pool.PoolUtils;

/**
 * A configurable <code>KeyedObjectPool</code> implementation.
 * <p>
 * When coupled with the appropriate {@link KeyedPoolableObjectFactory},
 * <code>GenericKeyedObjectPool</code> provides robust pooling functionality for
 * keyed objects. A <code>GenericKeyedObjectPool</code> can be viewed as a map
 * of pools, keyed on the (unique) key values provided to the
 * {@link #preparePool preparePool}, {@link #addObject addObject} or
 * {@link #borrowObject borrowObject} methods. Each time a new key value is
 * provided to one of these methods, a new pool is created under the given key
 * to be managed by the containing <code>GenericKeyedObjectPool.</code>
 * </p>
 * <p>A <code>GenericKeyedObjectPool</code> provides a number of configurable
 * parameters:</p>
 * <ul>
 *  <li>
 *    {@link #setMaxActive maxActive} controls the maximum number of objects
 *    (per key) that can allocated by the pool (checked out to client threads,
 *    or idle in the pool) at one time.  When non-positive, there is no limit
 *    to the number of objects per key. When {@link #setMaxActive maxActive} is
 *    reached, the keyed pool is said to be exhausted.  The default setting for
 *    this parameter is 8.
 *  </li>
 *  <li>
 *    {@link #setMaxTotal maxTotal} sets a global limit on the number of objects
 *    that can be in circulation (active or idle) within the combined set of
 *    pools.  When non-positive, there is no limit to the total number of
 *    objects in circulation. When {@link #setMaxTotal maxTotal} is exceeded,
 *    all keyed pools are exhausted. When <code>maxTotal</code> is set to a
 *    positive value and {@link #borrowObject borrowObject} is invoked
 *    when at the limit with no idle instances available, an attempt is made to
 *    create room by clearing the oldest 15% of the elements from the keyed
 *    pools. The default setting for this parameter is -1 (no limit).
 *  </li>
 *  <li>
 *    {@link #setMaxIdle maxIdle} controls the maximum number of objects that can
 *    sit idle in the pool (per key) at any time.  When negative, there
 *    is no limit to the number of objects that may be idle per key. The
 *    default setting for this parameter is 8.
 *  </li>
 *  <li>
 *    {@link #setWhenExhaustedAction whenExhaustedAction} specifies the
 *    behavior of the {@link #borrowObject borrowObject} method when a keyed
 *    pool is exhausted:
 *    <ul>
 *    <li>
 *      When {@link #setWhenExhaustedAction whenExhaustedAction} is
 *      {@link #WHEN_EXHAUSTED_FAIL}, {@link #borrowObject borrowObject} will throw
 *      a {@link NoSuchElementException}
 *    </li>
 *    <li>
 *      When {@link #setWhenExhaustedAction whenExhaustedAction} is
 *      {@link #WHEN_EXHAUSTED_GROW}, {@link #borrowObject borrowObject} will create a new
 *      object and return it (essentially making {@link #setMaxActive maxActive}
 *      meaningless.)
 *    </li>
 *    <li>
 *      When {@link #setWhenExhaustedAction whenExhaustedAction}
 *      is {@link #WHEN_EXHAUSTED_BLOCK}, {@link #borrowObject borrowObject} will block
 *      (invoke {@link Object#wait() wait} until a new or idle object is available.
 *      If a positive {@link #setMaxWait maxWait}
 *      value is supplied, the {@link #borrowObject borrowObject} will block for at
 *      most that many milliseconds, after which a {@link NoSuchElementException}
 *      will be thrown.  If {@link #setMaxWait maxWait} is non-positive,
 *      the {@link #borrowObject borrowObject} method will block indefinitely.
 *    </li>
 *    </ul>
 *    The default <code>whenExhaustedAction</code> setting is
 *    {@link #WHEN_EXHAUSTED_BLOCK}.
 *  </li>
 *  <li>
 *    When {@link #setTestOnBorrow testOnBorrow} is set, the pool will
 *    attempt to validate each object before it is returned from the
 *    {@link #borrowObject borrowObject} method. (Using the provided factory's
 *    {@link KeyedPoolableObjectFactory#validateObject validateObject} method.)
 *    Objects that fail to validate will be dropped from the pool, and a
 *    different object will be borrowed. The default setting for this parameter
 *    is <code>false.</code>
 *  </li>
 *  <li>
 *    When {@link #setTestOnReturn testOnReturn} is set, the pool will
 *    attempt to validate each object before it is returned to the pool in the
 *    {@link #returnObject returnObject} method. (Using the provided factory's
 *    {@link KeyedPoolableObjectFactory#validateObject validateObject}
 *    method.)  Objects that fail to validate will be dropped from the pool.
 *    The default setting for this parameter is <code>false.</code>
 *  </li>
 * </ul>
 * <p>
 * Optionally, one may configure the pool to examine and possibly evict objects
 * as they sit idle in the pool and to ensure that a minimum number of idle
 * objects is maintained for each key. This is performed by an
 * "idle object eviction" thread, which runs asynchronously. Caution should be
 * used when configuring this optional feature. Eviction runs contend with client
 * threads for access to objects in the pool, so if they run too frequently
 * performance issues may result.  The idle object eviction thread may be
 * configured using the following attributes:
 * <ul>
 *  <li>
 *   {@link #setTimeBetweenEvictionRunsMillis timeBetweenEvictionRunsMillis}
 *   indicates how long the eviction thread should sleep before "runs" of examining
 *   idle objects.  When non-positive, no eviction thread will be launched. The
 *   default setting for this parameter is -1 (i.e., by default, idle object
 *   eviction is disabled).
 *  </li>
 *  <li>
 *   {@link #setMinEvictableIdleTimeMillis minEvictableIdleTimeMillis}
 *   specifies the minimum amount of time that an object may sit idle in the
 *   pool before it is eligible for eviction due to idle time.  When
 *   non-positive, no object will be dropped from the pool due to idle time
 *   alone.  This setting has no effect unless
 *   <code>timeBetweenEvictionRunsMillis &gt; 0.</code>  The default setting
 *   for this parameter is 30 minutes.
 *  </li>
 *  <li>
 *   {@link #setTestWhileIdle testWhileIdle} indicates whether or not idle
 *   objects should be validated using the factory's
 *   {@link KeyedPoolableObjectFactory#validateObject validateObject} method
 *   during idle object eviction runs.  Objects that fail to validate will be
 *   dropped from the pool. This setting has no effect unless
 *   <code>timeBetweenEvictionRunsMillis &gt; 0.</code>  The default setting
 *   for this parameter is <code>false.</code>
 *  </li>
 *  <li>
 *    {@link #setMinIdle minIdle} sets a target value for the minimum number of
 *    idle objects (per key) that should always be available. If this parameter
 *    is set to a positive number and
 *    <code>timeBetweenEvictionRunsMillis &gt; 0,</code> each time the idle object
 *    eviction thread runs, it will try to create enough idle instances so that
 *    there will be <code>minIdle</code> idle instances available under each
 *    key. This parameter is also used by {@link #preparePool preparePool}
 *    if <code>true</code> is provided as that method's
 *    <code>populateImmediately</code> parameter. The default setting for this
 *    parameter is 0.
 *  </li>
 * </ul>
 * <p>
 * The pools can be configured to behave as LIFO queues with respect to idle
 * objects - always returning the most recently used object from the pool,
 * or as FIFO queues, where borrowObject always returns the oldest object
 * in the idle object pool.
 * <ul>
 *  <li>
 *   {@link #setLifo <i>Lifo</i>}
 *   determines whether or not the pools return idle objects in
 *   last-in-first-out order. The default setting for this parameter is
 *   <code>true.</code>
 *  </li>
 * </ul>
 * <p>
 * GenericKeyedObjectPool is not usable without a {@link KeyedPoolableObjectFactory}.  A
 * non-<code>null</code> factory must be provided either as a constructor argument
 * or via a call to {@link #setFactory setFactory} before the pool is used.
 * </p>
 * <p>
 * Implementation note: To prevent possible deadlocks, care has been taken to
 * ensure that no call to a factory method will occur within a synchronization
 * block. See POOL-125 and DBCP-44 for more information.
 * </p>
 *
 * @param <K> the type of keys in this pool
 * @param <V> the type of objects held in this pool
 *
 * @see GenericObjectPool
 * @author Rodney Waldhoff
 * @author Dirk Verbeeck
 * @author Sandy McArthur
 * @since Pool 1.0
 */
public class GenericKeyedObjectPool<K, V> extends BaseKeyedObjectPool<K, V> {

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
     * The default cap on the number of idle instances (per key) in the pool.
     * @see #getMaxIdle
     * @see #setMaxIdle
     */
    public static final int DEFAULT_MAX_IDLE  = 8;

    /**
     * The default cap on the total number of active instances (per key)
     * from the pool.
     * @see #getMaxActive
     * @see #setMaxActive
     */
    public static final int DEFAULT_MAX_ACTIVE  = 8;

    /**
     * The default cap on the the overall maximum number of objects that can
     * exist at one time.
     * @see #getMaxTotal
     * @see #setMaxTotal
     */
    public static final int DEFAULT_MAX_TOTAL  = -1;

    /**
     * The default "when exhausted action" for the pool.
     * @see #WHEN_EXHAUSTED_BLOCK
     * @see #WHEN_EXHAUSTED_FAIL
     * @see #WHEN_EXHAUSTED_GROW
     * @see #setWhenExhaustedAction
     */
    public static final byte DEFAULT_WHEN_EXHAUSTED_ACTION = WHEN_EXHAUSTED_BLOCK;

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
     * The default minimum level of idle objects in the pool.
     * @since Pool 1.3
     * @see #setMinIdle
     * @see #getMinIdle
     */
    public static final int DEFAULT_MIN_IDLE = 0;

    /**
     * The default LIFO status. True means that borrowObject returns the
     * most recently used ("last in") idle object in a pool (if there are
     * idle instances available).  False means that pools behave as FIFO
     * queues - objects are taken from idle object pools in the order that
     * they are returned.
     * @see #setLifo
     */
    public static final boolean DEFAULT_LIFO = true;

    //--- constructors -----------------------------------------------

    /**
     * Create a new <code>GenericKeyedObjectPool</code> with no factory.
     *
     * @see #GenericKeyedObjectPool(KeyedPoolableObjectFactory)
     * @see #setFactory(KeyedPoolableObjectFactory)
     */
    public GenericKeyedObjectPool() {
        this(null, DEFAULT_MAX_ACTIVE, DEFAULT_WHEN_EXHAUSTED_ACTION, DEFAULT_MAX_WAIT, DEFAULT_MAX_IDLE,
                DEFAULT_TEST_ON_BORROW, DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,
                DEFAULT_NUM_TESTS_PER_EVICTION_RUN, DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy
     * objects if not <code>null</code>
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory) {
        this(factory, DEFAULT_MAX_ACTIVE, DEFAULT_WHEN_EXHAUSTED_ACTION, DEFAULT_MAX_WAIT, DEFAULT_MAX_IDLE,
                DEFAULT_TEST_ON_BORROW, DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,
                DEFAULT_NUM_TESTS_PER_EVICTION_RUN, DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy objects
     * if not <code>null</code>
     * @param config a non-<code>null</code> {@link GenericKeyedObjectPool.Config} describing the configuration
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, GenericKeyedObjectPool.Config config) {
        this(factory, config.maxActive, config.whenExhaustedAction, config.maxWait, config.maxIdle, config.maxTotal,
                config.minIdle, config.testOnBorrow, config.testOnReturn, config.timeBetweenEvictionRunsMillis,
                config.numTestsPerEvictionRun, config.minEvictableIdleTimeMillis, config.testWhileIdle, config.lifo);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy objects
     * if not <code>null</code>
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (see {@link #setMaxActive})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int maxActive) {
        this(factory,maxActive, DEFAULT_WHEN_EXHAUSTED_ACTION, DEFAULT_MAX_WAIT, DEFAULT_MAX_IDLE,
                DEFAULT_TEST_ON_BORROW, DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS,
                DEFAULT_NUM_TESTS_PER_EVICTION_RUN, DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy objects
     * if not <code>null</code>
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     *  <code>whenExhaustedAction</code> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int maxActive, byte whenExhaustedAction,
            long maxWait) {
        this(factory, maxActive, whenExhaustedAction, maxWait, DEFAULT_MAX_IDLE, DEFAULT_TEST_ON_BORROW,
                DEFAULT_TEST_ON_RETURN, DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy objects
     * if not <code>null</code>
     * @param maxActive the maximum number of objects that can be borrowed from me at one time (see {@link #setMaxActive})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <code>whenExhaustedAction</code> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject}
     * method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject}
     * method (see {@link #setTestOnReturn})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int maxActive, byte whenExhaustedAction,
            long maxWait, boolean testOnBorrow, boolean testOnReturn) {
        this(factory, maxActive, whenExhaustedAction, maxWait, DEFAULT_MAX_IDLE,testOnBorrow,testOnReturn,
                DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy objects
     * if not <code>null</code>
     * @param maxActive the maximum number of objects that can be borrowed from me at one time
     * (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <code>whenExhaustedAction</code> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int maxActive, byte whenExhaustedAction,
            long maxWait, int maxIdle) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, DEFAULT_TEST_ON_BORROW, DEFAULT_TEST_ON_RETURN,
                DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy objects
     * if not <code>null</code>
     * @param maxActive the maximum number of objects that can be borrowed from me at one time
     * (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <code>whenExhaustedAction</code> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #getMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject}
     * method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject}
     * method (see {@link #setTestOnReturn})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int maxActive, byte whenExhaustedAction,
            long maxWait, int maxIdle, boolean testOnBorrow, boolean testOnReturn) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, testOnBorrow, testOnReturn,
                DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS, DEFAULT_NUM_TESTS_PER_EVICTION_RUN,
                DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS, DEFAULT_TEST_WHILE_IDLE);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy objects
     * if not <code>null</code>
     * @param maxActive the maximum number of objects that can be borrowed from me at one time
     * (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted
     * (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <code>whenExhaustedAction</code> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
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
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int maxActive, byte whenExhaustedAction,
            long maxWait, int maxIdle, boolean testOnBorrow, boolean testOnReturn, long timeBetweenEvictionRunsMillis,
            int numTestsPerEvictionRun, long minEvictableIdleTimeMillis, boolean testWhileIdle) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, GenericKeyedObjectPool.DEFAULT_MAX_TOTAL,
                testOnBorrow, testOnReturn, timeBetweenEvictionRunsMillis, numTestsPerEvictionRun,
                minEvictableIdleTimeMillis, testWhileIdle);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy objects
     * if not <code>null</code>
     * @param maxActive the maximum number of objects that can be borrowed from me at one time
     * (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <code>whenExhaustedAction</code> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param maxTotal the maximum number of objects that can exists at one time (see {@link #setMaxTotal})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject}
     * method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject}
     * method (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle
     * objects for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction
     * thread (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool
     * before it is eligible for eviction (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any
     * (see {@link #setTestWhileIdle})
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int maxActive, byte whenExhaustedAction,
            long maxWait, int maxIdle, int maxTotal, boolean testOnBorrow, boolean testOnReturn,
            long timeBetweenEvictionRunsMillis, int numTestsPerEvictionRun, long minEvictableIdleTimeMillis,
            boolean testWhileIdle) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, maxTotal,
                GenericKeyedObjectPool.DEFAULT_MIN_IDLE, testOnBorrow, testOnReturn, timeBetweenEvictionRunsMillis,
                numTestsPerEvictionRun, minEvictableIdleTimeMillis, testWhileIdle);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy objects
     * if not <code>null</code>
     * @param maxActive the maximum number of objects that can be borrowed at one time (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <code>whenExhaustedAction</code> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param maxTotal the maximum number of objects that can exists at one time (see {@link #setMaxTotal})
     * @param minIdle the minimum number of idle objects to have in the pool at any one time (see {@link #setMinIdle})
     * @param testOnBorrow whether or not to validate objects before they are returned by the {@link #borrowObject}
     * method (see {@link #setTestOnBorrow})
     * @param testOnReturn whether or not to validate objects after they are returned to the {@link #returnObject}
     * method (see {@link #setTestOnReturn})
     * @param timeBetweenEvictionRunsMillis the amount of time (in milliseconds) to sleep between examining idle
     * objects
     * for eviction (see {@link #setTimeBetweenEvictionRunsMillis})
     * @param numTestsPerEvictionRun the number of idle objects to examine per run within the idle object eviction
     * thread (if any) (see {@link #setNumTestsPerEvictionRun})
     * @param minEvictableIdleTimeMillis the minimum number of milliseconds an object can sit idle in the pool before
     * it is eligible for eviction (see {@link #setMinEvictableIdleTimeMillis})
     * @param testWhileIdle whether or not to validate objects in the idle object eviction thread, if any
     * (see {@link #setTestWhileIdle})
     * @since Pool 1.3
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int maxActive, byte whenExhaustedAction,
            long maxWait, int maxIdle, int maxTotal, int minIdle, boolean testOnBorrow, boolean testOnReturn,
            long timeBetweenEvictionRunsMillis, int numTestsPerEvictionRun, long minEvictableIdleTimeMillis,
            boolean testWhileIdle) {
        this(factory, maxActive, whenExhaustedAction, maxWait, maxIdle, maxTotal, minIdle, testOnBorrow, testOnReturn,
                timeBetweenEvictionRunsMillis, numTestsPerEvictionRun, minEvictableIdleTimeMillis, testWhileIdle,
                DEFAULT_LIFO);
    }

    /**
     * Create a new <code>GenericKeyedObjectPool</code> using the specified values.
     * @param factory the <code>KeyedPoolableObjectFactory</code> to use to create, validate, and destroy objects
     * if not <code>null</code>
     * @param maxActive the maximum number of objects that can be borrowed at one time
     *  (see {@link #setMaxActive})
     * @param whenExhaustedAction the action to take when the pool is exhausted (see {@link #setWhenExhaustedAction})
     * @param maxWait the maximum amount of time to wait for an idle object when the pool is exhausted and
     * <code>whenExhaustedAction</code> is {@link #WHEN_EXHAUSTED_BLOCK} (otherwise ignored) (see {@link #setMaxWait})
     * @param maxIdle the maximum number of idle objects in my pool (see {@link #setMaxIdle})
     * @param maxTotal the maximum number of objects that can exists at one time (see {@link #setMaxTotal})
     * @param minIdle the minimum number of idle objects to have in the pool at any one time (see {@link #setMinIdle})
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
     * @param lifo whether or not the pools behave as LIFO (last in first out) queues (see {@link #setLifo})
     * @since Pool 1.4
     */
    public GenericKeyedObjectPool(KeyedPoolableObjectFactory<K, V> factory, int maxActive, byte whenExhaustedAction,
            long maxWait, int maxIdle, int maxTotal, int minIdle, boolean testOnBorrow, boolean testOnReturn,
            long timeBetweenEvictionRunsMillis, int numTestsPerEvictionRun, long minEvictableIdleTimeMillis,
            boolean testWhileIdle, boolean lifo) {
        _factory = factory;
        // save the current CCL to be used later by the evictor Thread
        _factoryClassLoader = Thread.currentThread().getContextClassLoader();
        _maxActive = maxActive;
        _lifo = lifo;
        switch (whenExhaustedAction) {
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
        _maxTotal = maxTotal;
        _minIdle = minIdle;
        _testOnBorrow = testOnBorrow;
        _testOnReturn = testOnReturn;
        _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        _testWhileIdle = testWhileIdle;

        _poolMap = new HashMap<K, ObjectQueue>();
        _poolList = new CursorableLinkedList<K>();

        startEvictor(_timeBetweenEvictionRunsMillis);
    }

    //--- public methods ---------------------------------------------

    //--- configuration methods --------------------------------------

    /**
     * Returns the cap on the number of object instances allocated by the pool
     * (checked out or idle),  per key.
     * A negative value indicates no limit.
     *
     * @return the cap on the number of active instances per key.
     * @see #setMaxActive
     */
    public synchronized int getMaxActive() {
        return _maxActive;
    }

    /**
     * Sets the cap on the number of object instances managed by the pool per key.
     * @param maxActive The cap on the number of object instances per key.
     * Use a negative value for no limit.
     *
     * @see #getMaxActive
     */
    public void setMaxActive(int maxActive) {
        synchronized(this) {
            _maxActive = maxActive;
        }
        allocate();
    }

    /**
     * Returns the overall maximum number of objects (across pools) that can
     * exist at one time. A negative value indicates no limit.
     * @return the maximum number of instances in circulation at one time.
     * @see #setMaxTotal
     */
    public synchronized int getMaxTotal() {
        return _maxTotal;
    }

    /**
     * Sets the cap on the total number of instances from all pools combined.
     * When <code>maxTotal</code> is set to a
     * positive value and {@link #borrowObject borrowObject} is invoked
     * when at the limit with no idle instances available, an attempt is made to
     * create room by clearing the oldest 15% of the elements from the keyed
     * pools.
     *
     * @param maxTotal The cap on the total number of instances across pools.
     * Use a negative value for no limit.
     * @see #getMaxTotal
     */
    public void setMaxTotal(int maxTotal) {
        synchronized(this) {
            _maxTotal = maxTotal;
        }
        allocate();
    }

    /**
     * Returns the action to take when the {@link #borrowObject} method
     * is invoked when the pool is exhausted (the maximum number
     * of "active" objects has been reached).
     *
     * @return one of {@link #WHEN_EXHAUSTED_BLOCK},
     * {@link #WHEN_EXHAUSTED_FAIL} or {@link #WHEN_EXHAUSTED_GROW}
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
     * @return the maximum number of milliseconds borrowObject will block.
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
     * @param maxWait the maximum number of milliseconds borrowObject will block or negative for indefinitely.
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
     * Returns the cap on the number of "idle" instances per key.
     * @return the maximum number of "idle" instances that can be held
     * in a given keyed pool.
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
     * @param maxIdle the maximum number of "idle" instances that can be held
     * in a given keyed pool. Use a negative value for no limit.
     * @see #getMaxIdle
     * @see #DEFAULT_MAX_IDLE
     */
    public void setMaxIdle(int maxIdle) {
        synchronized(this) {
            _maxIdle = maxIdle;
        }
        allocate();
    }

    /**
     * Sets the minimum number of idle objects to maintain in each of the keyed
     * pools. This setting has no effect unless
     * <code>timeBetweenEvictionRunsMillis &gt; 0</code> and attempts to ensure
     * that each pool has the required minimum number of instances are only
     * made during idle object eviction runs.
     * <p>
     * If the configured value of minIdle is greater than the configured value
     * for maxIdle then the value of maxIdle will be used instead.
     *
     * @param poolSize - The minimum size of the each keyed pool
     * @since Pool 1.3
     * @see #getMinIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public void setMinIdle(int poolSize) {
        _minIdle = poolSize;
    }

    /**
     * Returns the minimum number of idle objects to maintain in each of the keyed
     * pools. This setting has no effect unless
     * <code>timeBetweenEvictionRunsMillis &gt; 0</code> and attempts to ensure
     * that each pool has the required minimum number of instances are only
     * made during idle object eviction runs.
     * <p>
     * If the configured value of minIdle is greater than the configured value
     * for maxIdle then the value of maxIdle will be used instead.
     *
     * @return minimum size of the each keyed pool
     * @since Pool 1.3
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public int getMinIdle() {
        int maxIdle = getMaxIdle();
        if (_minIdle > maxIdle) {
            return maxIdle;
        } else {
            return _minIdle;
        }
    }

    /**
     * When <code>true</code>, objects will be
     * {@link org.apache.tomcat.dbcp.pool.PoolableObjectFactory#validateObject validated}
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
     * {@link org.apache.tomcat.dbcp.pool.PoolableObjectFactory#validateObject validated}
     * before being returned by the {@link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     *
     * @param testOnBorrow whether object should be validated before being returned by borrowObject.
     * @see #getTestOnBorrow
     */
    public void setTestOnBorrow(boolean testOnBorrow) {
        _testOnBorrow = testOnBorrow;
    }

    /**
     * When <code>true</code>, objects will be
     * {@link org.apache.tomcat.dbcp.pool.PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @return <code>true</code> when objects will be validated before being returned.
     * @see #setTestOnReturn
     */
    public boolean getTestOnReturn() {
        return _testOnReturn;
    }

    /**
     * When <code>true</code>, objects will be
     * {@link org.apache.tomcat.dbcp.pool.PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @param testOnReturn <code>true</code> so objects will be validated before being returned.
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
     * @return milliseconds to sleep between evictor runs.
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
     * @param timeBetweenEvictionRunsMillis milliseconds to sleep between evictor runs.
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
     * @return number of objects to examine each eviction run.
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
     * When a negative value is supplied,
     * <code>ceil({@link #getNumIdle()})/abs({@link #getNumTestsPerEvictionRun})</code>
     * tests will be run.  I.e., when the value is <code>-n</code>, roughly one <code>n</code>th of the
     * idle objects will be tested per run.  When the value is positive, the number of tests
     * actually performed in each run will be the minimum of this value and the number of instances
     * idle in the pools.
     *
     * @param numTestsPerEvictionRun number of objects to examine each eviction run.
     * @see #setNumTestsPerEvictionRun
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
     *
     * @param minEvictableIdleTimeMillis minimum amount of time an object may sit idle in the pool before
     * it is eligible for eviction.
     * @see #getMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * When <code>true</code>, objects will be
     * {@link org.apache.tomcat.dbcp.pool.PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @return <code>true</code> when objects are validated when borrowed.
     * @see #setTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized boolean getTestWhileIdle() {
        return _testWhileIdle;
    }

    /**
     * When <code>true</code>, objects will be
     * {@link org.apache.tomcat.dbcp.pool.PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @param testWhileIdle <code>true</code> so objects are validated when borrowed.
     * @see #getTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public synchronized void setTestWhileIdle(boolean testWhileIdle) {
        _testWhileIdle = testWhileIdle;
    }

    /**
     * Sets the configuration.
     * @param conf the new configuration to use.
     * @see GenericKeyedObjectPool.Config
     */
    public synchronized void setConfig(GenericKeyedObjectPool.Config conf) {
        setMaxIdle(conf.maxIdle);
        setMaxActive(conf.maxActive);
        setMaxTotal(conf.maxTotal);
        setMinIdle(conf.minIdle);
        setMaxWait(conf.maxWait);
        setWhenExhaustedAction(conf.whenExhaustedAction);
        setTestOnBorrow(conf.testOnBorrow);
        setTestOnReturn(conf.testOnReturn);
        setTestWhileIdle(conf.testWhileIdle);
        setNumTestsPerEvictionRun(conf.numTestsPerEvictionRun);
        setMinEvictableIdleTimeMillis(conf.minEvictableIdleTimeMillis);
        setTimeBetweenEvictionRunsMillis(conf.timeBetweenEvictionRunsMillis);
    }

    /**
     * Whether or not the idle object pools act as LIFO queues. True means
     * that borrowObject returns the most recently used ("last in") idle object
     * in a pool (if there are idle instances available).  False means that
     * the pools behave as FIFO queues - objects are taken from idle object
     * pools in the order that they are returned.
     *
     * @return <code>true</code> if the pools are configured to act as LIFO queues
     * @since 1.4
     */
     public synchronized boolean getLifo() {
         return _lifo;
     }

     /**
      * Sets the LIFO property of the pools. True means that borrowObject returns
      * the most recently used ("last in") idle object in a pool (if there are
      * idle instances available).  False means that the pools behave as FIFO
      * queues - objects are taken from idle object pools in the order that
      * they are returned.
      *
      * @param lifo the new value for the lifo property
      * @since 1.4
      */
     public synchronized void setLifo(boolean lifo) {
         this._lifo = lifo;
     }

    //-- ObjectPool methods ------------------------------------------

    /**
     * <p>Borrows an object from the keyed pool associated with the given key.</p>
     *
     * <p>If there is an idle instance available in the pool associated with the given key, then
     * either the most-recently returned (if {@link #getLifo() lifo} == true) or "oldest" (lifo == false)
     * instance sitting idle in the pool will be activated and returned.  If activation fails, or
     * {@link #getTestOnBorrow() testOnBorrow} is set to true and validation fails, the instance is destroyed and the
     * next available instance is examined.  This continues until either a valid instance is returned or there
     * are no more idle instances available.</p>
     *
     * <p>If there are no idle instances available in the pool associated with the given key, behavior
     * depends on the {@link #getMaxActive() maxActive}, {@link #getMaxTotal() maxTotal}, and (if applicable)
     * {@link #getWhenExhaustedAction() whenExhaustedAction} and {@link #getMaxWait() maxWait} properties. If the
     * number of instances checked out from the pool under the given key is less than <code>maxActive</code> and
     * the total number of instances in circulation (under all keys) is less than <code>maxTotal</code>, a new instance
     * is created, activated and (if applicable) validated and returned to the caller.</p>
     *
     * <p>If the associated keyed pool is exhausted (no available idle instances and no capacity to create new ones),
     * this method will either block ({@link #WHEN_EXHAUSTED_BLOCK}), throw a <code>NoSuchElementException</code>
     * ({@link #WHEN_EXHAUSTED_FAIL}), or grow ({@link #WHEN_EXHAUSTED_GROW} - ignoring maxActive, maxTotal properties).
     * The length of time that this method will block when <code>whenExhaustedAction == WHEN_EXHAUSTED_BLOCK</code>
     * is determined by the {@link #getMaxWait() maxWait} property.</p>
     *
     * <p>When the pool is exhausted, multiple calling threads may be simultaneously blocked waiting for instances
     * to become available.  As of pool 1.5, a "fairness" algorithm has been implemented to ensure that threads receive
     * available instances in request arrival order.</p>
     *
     * @param key pool key
     * @return object instance from the keyed pool
     * @throws NoSuchElementException if a keyed object instance cannot be returned.
     */
     @Override
    public V borrowObject(K key) throws Exception {
        long starttime = System.currentTimeMillis();
        Latch<K, V> latch = new Latch<K, V>(key);
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
            // If no object was allocated
            if (null == latch.getPair()) {
                // Check to see if we were allowed to create one
                if (latch.mayCreate()) {
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
                                    latch.getPool().incrementInternalProcessingCount();
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
                                        if (maxWait <= 0) {
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
                                synchronized (this) {
                                    // Need to handle the all three possibilities
                                    if (latch.getPair() == null && !latch.mayCreate()) {
                                        // Case 1: latch still in allocation queue
                                        // Remove latch from the allocation queue
                                        _allocationQueue.remove(latch);
                                    } else if (latch.getPair() == null && latch.mayCreate()) {
                                        // Case 2: latch has been given permission to create
                                        //         a new object
                                        latch.getPool().decrementInternalProcessingCount();
                                        doAllocate = true;
                                    } else {
                                        // Case 3: An object has been allocated
                                        latch.getPool().decrementInternalProcessingCount();
                                        latch.getPool().incrementActiveCount();
                                        returnObject(latch.getkey(), latch.getPair().getValue());
                                    }
                                }
                                if (doAllocate) {
                                    allocate();
                                }
                                Thread.currentThread().interrupt();
                                throw e;
                            }
                            if (maxWait > 0 && ((System.currentTimeMillis() - starttime) >= maxWait)) {
                                synchronized (this) {
                                    // Make sure allocate hasn't already assigned an object
                                    // in a different thread or permitted a new object to be created
                                    if (latch.getPair() == null && !latch.mayCreate()) {
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
                            throw new IllegalArgumentException("whenExhaustedAction " + whenExhaustedAction +
                                    " not recognized.");
                    }
                }
            }

            boolean newlyCreated = false;
            if (null == latch.getPair()) {
                try {
                    V obj = _factory.makeObject(key);
                    latch.setPair(new ObjectTimestampPair<V>(obj));
                    newlyCreated = true;
                } finally {
                    if (!newlyCreated) {
                        // object cannot be created
                        synchronized (this) {
                            latch.getPool().decrementInternalProcessingCount();
                            // No need to reset latch - about to throw exception
                        }
                        allocate();
                    }
                }
            }

            // activate & validate the object
            try {
                _factory.activateObject(key, latch.getPair().value);
                if (_testOnBorrow && !_factory.validateObject(key, latch.getPair().value)) {
                    throw new Exception("ValidateObject failed");
                }
                synchronized (this) {
                    latch.getPool().decrementInternalProcessingCount();
                    latch.getPool().incrementActiveCount();
                }
                return latch.getPair().value;
            } catch (Throwable e) {
                PoolUtils.checkRethrow(e);
                // object cannot be activated or is invalid
                try {
                    _factory.destroyObject(key, latch.getPair().value);
                } catch (Throwable e2) {
                    PoolUtils.checkRethrow(e2);
                    // cannot destroy broken object
                }
                synchronized (this) {
                    latch.getPool().decrementInternalProcessingCount();
                    if (!newlyCreated) {
                        latch.reset();
                        _allocationQueue.add(0, latch);
                    }
                }
                allocate();
                if (newlyCreated) {
                    throw new NoSuchElementException(
                       "Could not create a validated object, cause: " +
                            e.getMessage());
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
     * as _maxActive allows for each key. This method <b>MUST NOT</b> be called
     * from inside a sync block.
     */
    private void allocate() {
        boolean clearOldest = false;

        synchronized (this) {
            if (isClosed()) {
                return;
            }

            Iterator<Latch<K, V>> allocationQueueIter = _allocationQueue.iterator();

            while (allocationQueueIter.hasNext()) {
                // First use any objects in the pool to clear the queue
                Latch<K, V> latch = allocationQueueIter.next();
                ObjectQueue pool = (_poolMap.get(latch.getkey()));
                if (null == pool) {
                    pool = new ObjectQueue();
                    _poolMap.put(latch.getkey(), pool);
                    _poolList.add(latch.getkey());
                }
                latch.setPool(pool);
                if (!pool.queue.isEmpty()) {
                    allocationQueueIter.remove();
                    latch.setPair(
                            pool.queue.removeFirst());
                    pool.incrementInternalProcessingCount();
                    _totalIdle--;
                    synchronized (latch) {
                        latch.notify();
                    }
                    // Next item in queue
                    continue;
                }

                // If there is a totalMaxActive and we are at the limit then
                // we have to make room
                if ((_maxTotal > 0) &&
                        (_totalActive + _totalIdle + _totalInternalProcessing >= _maxTotal)) {
                    clearOldest = true;
                    break;
                }

                // Second utilise any spare capacity to create new objects
                if ((_maxActive < 0 || pool.activeCount + pool.internalProcessingCount < _maxActive) &&
                        (_maxTotal < 0 || _totalActive + _totalIdle + _totalInternalProcessing < _maxTotal)) {
                    // allow new object to be created
                    allocationQueueIter.remove();
                    latch.setMayCreate(true);
                    pool.incrementInternalProcessingCount();
                    synchronized (latch) {
                        latch.notify();
                    }
                    // Next item in queue
                    continue;
                }

                // If there is no per-key limit and we reach this point we
                // must have allocated all the objects we possibly can and there
                // is no point looking at the rest of the allocation queue
                if (_maxActive < 0) {
                    break;
                }
            }
        }

        if (clearOldest) {
            /* Clear oldest calls factory methods so it must be called from
             * outside the sync block.
             * It also needs to be outside the sync block as it calls
             * allocate(). If called inside the sync block, the call to
             * allocate() would be able to enter the sync block (since the
             * thread already has the lock) which may have unexpected,
             * unpleasant results.
             */
            clearOldest();
        }
    }

    /**
     * Clears any objects sitting idle in the pool by removing them from the
     * idle instance pool and then invoking the configured PoolableObjectFactory's
     * {@link KeyedPoolableObjectFactory#destroyObject(Object, Object)} method on
     * each idle instance.
     *
     * <p> Implementation notes:
     * <ul><li>This method does not destroy or effect in any way instances that are
     * checked out when it is invoked.</li>
     * <li>Invoking this method does not prevent objects being
     * returned to the idle instance pool, even during its execution. It locks
     * the pool only during instance removal. Additional instances may be returned
     * while removed items are being destroyed.</li>
     * <li>Exceptions encountered destroying idle instances are swallowed.</li></ul>
     */
    @Override
    public void clear() {
        Map<K,  List<ObjectTimestampPair<V>>> toDestroy = new HashMap<K,  List<ObjectTimestampPair<V>>>();
        synchronized (this) {
            for (Iterator<K> it = _poolMap.keySet().iterator(); it.hasNext();) {
                K key = it.next();
                ObjectQueue pool = _poolMap.get(key);
                // Copy objects to new list so pool.queue can be cleared inside
                // the sync
                List<ObjectTimestampPair<V>> objects = new ArrayList<ObjectTimestampPair<V>>();
                objects.addAll(pool.queue);
                toDestroy.put(key, objects);
                it.remove();
                _poolList.remove(key);
                _totalIdle = _totalIdle - pool.queue.size();
                _totalInternalProcessing =
                    _totalInternalProcessing + pool.queue.size();
                pool.queue.clear();
            }
        }
        destroy(toDestroy, _factory);
    }

    /**
     * Clears oldest 15% of objects in pool.  The method sorts the
     * objects into a TreeMap and then iterates the first 15% for removal.
     *
     * @since Pool 1.3
     */
    public void clearOldest() {
        // Map of objects to destroy my key
        final Map<K, List<ObjectTimestampPair<V>>> toDestroy = new HashMap<K, List<ObjectTimestampPair<V>>>();

        // build sorted map of idle objects
        final Map<ObjectTimestampPair<V>, K> map = new TreeMap<ObjectTimestampPair<V>, K>();
        synchronized (this) {
            for (Iterator<K> keyiter = _poolMap.keySet().iterator(); keyiter.hasNext();) {
                final K key = keyiter.next();
                final List<ObjectTimestampPair<V>> list = _poolMap.get(key).queue;
                for (Iterator<ObjectTimestampPair<V>> it = list.iterator(); it.hasNext();) {
                    // each item into the map uses the objectimestamppair object
                    // as the key.  It then gets sorted based on the timstamp field
                    // each value in the map is the parent list it belongs in.
                    map.put(it.next(), key);
                }
            }

            // Now iterate created map and kill the first 15% plus one to account for zero
            Set<Entry<ObjectTimestampPair<V>, K>> setPairKeys = map.entrySet();
            int itemsToRemove = ((int) (map.size() * 0.15)) + 1;

            Iterator<Entry<ObjectTimestampPair<V>, K>> iter = setPairKeys.iterator();
            while (iter.hasNext() && itemsToRemove > 0) {
                Entry<ObjectTimestampPair<V>, K> entry = iter.next();
                // kind of backwards on naming.  In the map, each key is the objecttimestamppair
                // because it has the ordering with the timestamp value.  Each value that the
                // key references is the key of the list it belongs to.
                K key = entry.getValue();
                ObjectTimestampPair<V> pairTimeStamp = entry.getKey();
                ObjectQueue objectQueue = _poolMap.get(key);
                final List<ObjectTimestampPair<V>> list = objectQueue.queue;
                list.remove(pairTimeStamp);

                if (toDestroy.containsKey(key)) {
                    toDestroy.get(key).add(pairTimeStamp);
                } else {
                    List<ObjectTimestampPair<V>> listForKey = new ArrayList<ObjectTimestampPair<V>>();
                    listForKey.add(pairTimeStamp);
                    toDestroy.put(key, listForKey);
                }
                objectQueue.incrementInternalProcessingCount();
                _totalIdle--;
                itemsToRemove--;
            }

        }
        destroy(toDestroy, _factory);
    }

    /**
     * Clears the specified pool, removing all pooled instances corresponding to the given <code>key</code>.
     *
     * @param key the key to clear
     */
    @Override
    public void clear(K key) {
        Map<K, List<ObjectTimestampPair<V>>> toDestroy = new HashMap<K , List<ObjectTimestampPair<V>>>();

        final ObjectQueue pool;
        synchronized (this) {
            pool = _poolMap.remove(key);
            if (pool == null) {
                return;
            } else {
                _poolList.remove(key);
            }
            // Copy objects to new list so pool.queue can be cleared inside
            // the sync
            List<ObjectTimestampPair<V>> objects = new ArrayList<ObjectTimestampPair<V>>();
            objects.addAll(pool.queue);
            toDestroy.put(key, objects);
            _totalIdle = _totalIdle - pool.queue.size();
            _totalInternalProcessing =
                _totalInternalProcessing + pool.queue.size();
            pool.queue.clear();
        }
        destroy(toDestroy, _factory);
    }

    /**
     * Assuming Map<Object,Collection<ObjectTimestampPair>>, destroy all
     * ObjectTimestampPair.value using the supplied factory.
     *
     * @param m Map containing keyed pools to clear
     * @param factory KeyedPoolableObjectFactory used to destroy the objects
     */
    private void destroy(Map<K,  List<ObjectTimestampPair<V>>> m, KeyedPoolableObjectFactory<K, V> factory) {
        for (Iterator<Entry<K,  List<ObjectTimestampPair<V>>>> entries = m.entrySet().iterator(); entries.hasNext();) {
            Entry<K,  List<ObjectTimestampPair<V>>> entry = entries.next();
            K key = entry.getKey();
            List<ObjectTimestampPair<V>> c = entry.getValue();
            for (Iterator<ObjectTimestampPair<V>> it = c.iterator(); it.hasNext();) {
                try {
                    factory.destroyObject(
                            key,it.next().value);
                } catch(Exception e) {
                    // ignore error, keep destroying the rest
                } finally {
                    synchronized(this) {
                        ObjectQueue objectQueue =
                                _poolMap.get(key);
                        if (objectQueue != null) {
                            objectQueue.decrementInternalProcessingCount();
                            if (objectQueue.internalProcessingCount == 0 &&
                                    objectQueue.activeCount == 0 &&
                                    objectQueue.queue.isEmpty()) {
                                _poolMap.remove(key);
                                _poolList.remove(key);
                            }
                        } else {
                            _totalInternalProcessing--;
                        }
                    }
                    allocate();
                }
            }

        }
    }

    /**
     * Returns the total number of instances current borrowed from this pool but not yet returned.
     *
     * @return the total number of instances currently borrowed from this pool
     */
    @Override
    public synchronized int getNumActive() {
        return _totalActive;
    }

    /**
     * Returns the total number of instances currently idle in this pool.
     *
     * @return the total number of instances currently idle in this pool
     */
    @Override
    public synchronized int getNumIdle() {
        return _totalIdle;
    }

    /**
     * Returns the number of instances currently borrowed from but not yet returned
     * to the pool corresponding to the given <code>key</code>.
     *
     * @param key the key to query
     * @return the number of instances corresponding to the given <code>key</code> currently borrowed in this pool
     */
    @Override
    public synchronized int getNumActive(Object key) {
        final ObjectQueue pool = (_poolMap.get(key));
        return pool != null ? pool.activeCount : 0;
    }

    /**
     * Returns the number of instances corresponding to the given <code>key</code> currently idle in this pool.
     *
     * @param key the key to query
     * @return the number of instances corresponding to the given <code>key</code> currently idle in this pool
     */
    @Override
    public synchronized int getNumIdle(Object key) {
        final ObjectQueue pool = (_poolMap.get(key));
        return pool != null ? pool.queue.size() : 0;
    }

    /**
     * <p>Returns an object to a keyed pool.</p>
     *
     * <p>For the pool to function correctly, the object instance <strong>must</strong> have been borrowed
     * from the pool (under the same key) and not yet returned. Repeated <code>returnObject</code> calls on
     * the same object/key pair (with no <code>borrowObject</code> calls in between) will result in multiple
     * references to the object in the idle instance pool.</p>
     *
     * <p>If {@link #getMaxIdle() maxIdle} is set to a positive value and the number of idle instances under the given
     * key has reached this value, the returning instance is destroyed.</p>
     *
     * <p>If {@link #getTestOnReturn() testOnReturn} == true, the returning instance is validated before being returned
     * to the idle instance pool under the given key.  In this case, if validation fails, the instance is destroyed.</p>
     *
     * @param key pool key
     * @param obj instance to return to the keyed pool
     * @throws Exception
     */
    @Override
    public void returnObject(K key, V obj) throws Exception {
        try {
            addObjectToPool(key, obj, true);
        } catch (Exception e) {
            if (_factory != null) {
                try {
                    _factory.destroyObject(key, obj);
                } catch (Exception e2) {
                    // swallowed
                }
                // TODO: Correctness here depends on control in addObjectToPool.
                // These two methods should be refactored, removing the
                // "behavior flag", decrementNumActive, from addObjectToPool.
                ObjectQueue pool = (_poolMap.get(key));
                if (pool != null) {
                    synchronized(this) {
                        pool.decrementActiveCount();
                        if (pool.queue.isEmpty() &&
                                pool.activeCount == 0 &&
                                pool.internalProcessingCount == 0) {
                            _poolMap.remove(key);
                            _poolList.remove(key);
                        }
                    }
                    allocate();
                }
            }
        }
    }

    /**
     * <p>Adds an object to the keyed pool.</p>
     *
     * <p>Validates the object if testOnReturn == true and passivates it before returning it to the pool.
     * if validation or passivation fails, or maxIdle is set and there is no room in the pool, the instance
     * is destroyed.</p>
     *
     * <p>Calls {@link #allocate()} on successful completion</p>
     *
     * @param key pool key
     * @param obj instance to add to the keyed pool
     * @param decrementNumActive whether or not to decrement the active count associated with the keyed pool
     * @throws Exception
     */
    private void addObjectToPool(K key, V obj,
            boolean decrementNumActive) throws Exception {

        // if we need to validate this object, do so
        boolean success = true; // whether or not this object passed validation
        if (_testOnReturn && !_factory.validateObject(key, obj)) {
            success = false;
        } else {
            _factory.passivateObject(key, obj);
        }

        boolean shouldDestroy = !success;
        ObjectQueue pool;

        // Add instance to pool if there is room and it has passed validation
        // (if testOnreturn is set)
        boolean doAllocate = false;
        synchronized (this) {
            // grab the pool (list) of objects associated with the given key
            pool = _poolMap.get(key);
            // if it doesn't exist, create it
            if (null == pool) {
                pool = new ObjectQueue();
                _poolMap.put(key, pool);
                _poolList.add(key);
            }
            if (isClosed()) {
                shouldDestroy = true;
            } else {
                // if there's no space in the pool, flag the object for destruction
                // else if we passivated successfully, return it to the pool
                if (_maxIdle >= 0 && (pool.queue.size() >= _maxIdle)) {
                    shouldDestroy = true;
                } else if (success) {
                    // borrowObject always takes the first element from the queue,
                    // so for LIFO, push on top, FIFO add to end
                    if (_lifo) {
                        pool.queue.addFirst(new ObjectTimestampPair<V>(obj));
                    } else {
                        pool.queue.addLast(new ObjectTimestampPair<V>(obj));
                    }
                    _totalIdle++;
                    if (decrementNumActive) {
                        pool.decrementActiveCount();
                    }
                    doAllocate = true;
                }
            }
        }
        if (doAllocate) {
            allocate();
        }

        // Destroy the instance if necessary
        if (shouldDestroy) {
            try {
                _factory.destroyObject(key, obj);
            } catch(Exception e) {
                // ignored?
            }
            // Decrement active count *after* destroy if applicable
            if (decrementNumActive) {
                synchronized(this) {
                    pool.decrementActiveCount();
                    if (pool.queue.isEmpty() &&
                            pool.activeCount == 0 &&
                            pool.internalProcessingCount == 0) {
                        _poolMap.remove(key);
                        _poolList.remove(key);
                    }
                }
                allocate();
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>Activation of this method decrements the active count associated with the given keyed pool
     * and attempts to destroy <code>obj.</code></p>
     *
     * @param key pool key
     * @param obj instance to invalidate
     * @throws Exception if an exception occurs destroying the object
     */
    @Override
    public void invalidateObject(K key, V obj) throws Exception {
        try {
            _factory.destroyObject(key, obj);
        } finally {
            synchronized (this) {
                ObjectQueue pool = (_poolMap.get(key));
                if (null == pool) {
                    pool = new ObjectQueue();
                    _poolMap.put(key, pool);
                    _poolList.add(key);
                }
                pool.decrementActiveCount();
            }
            allocate(); // _totalActive has changed
        }
    }

    /**
     * Create an object using the {@link KeyedPoolableObjectFactory#makeObject factory},
     * passivate it, and then place it in the idle object pool.
     * <code>addObject</code> is useful for "pre-loading" a pool with idle objects.
     *
     * @param key the key a new instance should be added to
     * @throws Exception when {@link KeyedPoolableObjectFactory#makeObject} fails.
     * @throws IllegalStateException when no {@link #setFactory factory} has been set or after {@link #close} has been
     * called on this pool.
     */
    @Override
    public void addObject(K key) throws Exception {
        assertOpen();
        if (_factory == null) {
            throw new IllegalStateException("Cannot add objects without a factory.");
        }
        V obj = _factory.makeObject(key);
        try {
            assertOpen();
            addObjectToPool(key, obj, false);
        } catch (IllegalStateException ex) { // Pool closed
            try {
                _factory.destroyObject(key, obj);
            } catch (Exception ex2) {
                // swallow
            }
            throw ex;
        }
    }

    /**
     * Registers a key for pool control.
     *
     * If <code>populateImmediately</code> is <code>true</code> and
     * <code>minIdle &gt; 0,</code> the pool under the given key will be
     * populated immediately with <code>minIdle</code> idle instances.
     *
     * @param key - The key to register for pool control.
     * @param populateImmediately - If this is <code>true</code>, the pool
     * will be populated immediately.
     * @since Pool 1.3
     */
    public synchronized void preparePool(K key, boolean populateImmediately) {
        ObjectQueue pool = (_poolMap.get(key));
        if (null == pool) {
            pool = new ObjectQueue();
            _poolMap.put(key,pool);
            _poolList.add(key);
        }

        if (populateImmediately) {
            try {
                // Create the pooled objects
                ensureMinIdle(key);
            }
            catch (Exception e) {
                //Do nothing
            }
        }
    }

    /**
     * <p>Closes the keyed object pool.  Once the pool is closed, {@link #borrowObject(Object)}
     * will fail with IllegalStateException, but {@link #returnObject(Object, Object)} and
     * {@link #invalidateObject(Object, Object)} will continue to work, with returned objects
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
            if (null != _evictionCursor) {
                _evictionCursor.close();
                _evictionCursor = null;
            }
            if (null != _evictionKeyCursor) {
                _evictionKeyCursor.close();
                _evictionKeyCursor = null;
            }
            startEvictor(-1L);

            while(_allocationQueue.size() > 0) {
                Latch<K, V> l = _allocationQueue.removeFirst();

                synchronized (l) {
                    // notify the waiting thread
                    l.notify();
                }
            }
        }
    }

    /**
     * <p>Sets the keyed poolable object factory associated with this pool.</p>
     *
     * <p>If this method is called when objects are checked out of any of the keyed pools,
     * an IllegalStateException is thrown.  Calling this method also has the side effect of
     * destroying any idle instances in existing keyed pools, using the original factory.</p>
     *
     * @param factory KeyedPoolableObjectFactory to use when creating keyed object pool instances
     * @throws IllegalStateException if there are active (checked out) instances associated with this keyed object pool
     * @deprecated to be removed in version 2.0
     */
    @Deprecated
    @Override
    public void setFactory(KeyedPoolableObjectFactory<K, V> factory) throws IllegalStateException {
        Map<K, List<ObjectTimestampPair<V>>> toDestroy = new HashMap<K, List<ObjectTimestampPair<V>>>();
        final KeyedPoolableObjectFactory<K, V> oldFactory = _factory;
        synchronized (this) {
            assertOpen();
            if (0 < getNumActive()) {
                throw new IllegalStateException("Objects are already active");
            } else {
                for (Iterator<K> it = _poolMap.keySet().iterator(); it.hasNext();) {
                    K key = it.next();
                    ObjectQueue pool = _poolMap.get(key);
                    if (pool != null) {
                        // Copy objects to new list so pool.queue can be cleared
                        // inside the sync
                        List<ObjectTimestampPair<V>> objects = new ArrayList<ObjectTimestampPair<V>>();
                        objects.addAll(pool.queue);
                        toDestroy.put(key, objects);
                        it.remove();
                        _poolList.remove(key);
                        _totalIdle = _totalIdle - pool.queue.size();
                        _totalInternalProcessing =
                            _totalInternalProcessing + pool.queue.size();
                        pool.queue.clear();
                    }
                }
                _factory = factory;
                // save the current CCL to be used later by the evictor Thread
                _factoryClassLoader = Thread.currentThread().getContextClassLoader();
            }
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
     * <p>Successive activations of this method examine objects in keyed pools
     * in sequence, cycling through the keys and examining objects in
     * oldest-to-youngest order within the keyed pools.</p>
     *
     * @throws Exception when there is a problem evicting idle objects.
     */
    public void evict() throws Exception {
        K key = null;
        boolean testWhileIdle;
        long minEvictableIdleTimeMillis;

        synchronized (this) {
            // Get local copy of current config. Can't sync when used later as
            // it can result in a deadlock. Has the added advantage that config
            // is consistent for entire method execution
            testWhileIdle = _testWhileIdle;
            minEvictableIdleTimeMillis = _minEvictableIdleTimeMillis;

            // Initialize key to last key value
            if (_evictionKeyCursor != null &&
                    _evictionKeyCursor._lastReturned != null) {
                key = _evictionKeyCursor._lastReturned.value();
            }
        }

        for (int i=0, m=getNumTests(); i<m; i++) {
            final ObjectTimestampPair<V> pair;
            synchronized (this) {
                // make sure pool map is not empty; otherwise do nothing
                if (_poolMap == null || _poolMap.size() == 0) {
                    continue;
                }

                // if we don't have a key cursor, then create one
                if (null == _evictionKeyCursor) {
                    resetEvictionKeyCursor();
                    key = null;
                }

                // if we don't have an object cursor, create one
                if (null == _evictionCursor) {
                    // if the _evictionKeyCursor has a next value, use this key
                    if (_evictionKeyCursor.hasNext()) {
                        key = _evictionKeyCursor.next();
                        resetEvictionObjectCursor(key);
                    } else {
                        // Reset the key cursor and try again
                        resetEvictionKeyCursor();
                        if (_evictionKeyCursor != null) {
                            if (_evictionKeyCursor.hasNext()) {
                                key = _evictionKeyCursor.next();
                                resetEvictionObjectCursor(key);
                            }
                        }
                    }
                }

                if (_evictionCursor == null) {
                    continue; // should never happen; do nothing
                }

                // If eviction cursor is exhausted, try to move
                // to the next key and reset
                if ((_lifo && !_evictionCursor.hasPrevious()) ||
                        (!_lifo && !_evictionCursor.hasNext())) {
                    if (_evictionKeyCursor != null) {
                        if (_evictionKeyCursor.hasNext()) {
                            key = _evictionKeyCursor.next();
                            resetEvictionObjectCursor(key);
                        } else { // Need to reset Key cursor
                            resetEvictionKeyCursor();
                            if (_evictionKeyCursor != null) {
                                if (_evictionKeyCursor.hasNext()) {
                                    key = _evictionKeyCursor.next();
                                    resetEvictionObjectCursor(key);
                                }
                            }
                        }
                    }
                }

                if ((_lifo && !_evictionCursor.hasPrevious()) ||
                        (!_lifo && !_evictionCursor.hasNext())) {
                    continue; // reset failed, do nothing
                }

                // if LIFO and the _evictionCursor has a previous object,
                // or FIFO and _evictionCursor has a next object, test it
                pair = _lifo ?
                        _evictionCursor.previous() :
                        _evictionCursor.next();
                _evictionCursor.remove();
                ObjectQueue objectQueue = _poolMap.get(key);
                objectQueue.incrementInternalProcessingCount();
                _totalIdle--;
            }

            boolean removeObject=false;
            if ((minEvictableIdleTimeMillis > 0) &&
               (System.currentTimeMillis() - pair.tstamp >
               minEvictableIdleTimeMillis)) {
                removeObject=true;
            }
            if (testWhileIdle && removeObject == false) {
                boolean active = false;
                try {
                    _factory.activateObject(key,pair.value);
                    active = true;
                } catch(Exception e) {
                    removeObject=true;
                }
                if (active) {
                    if (!_factory.validateObject(key,pair.value)) {
                        removeObject=true;
                    } else {
                        try {
                            _factory.passivateObject(key,pair.value);
                        } catch(Exception e) {
                            removeObject=true;
                        }
                    }
                }
            }

            if (removeObject) {
                try {
                    _factory.destroyObject(key, pair.value);
                } catch(Exception e) {
                    // ignored
                }
            }
            synchronized (this) {
                ObjectQueue objectQueue =
                    _poolMap.get(key);
                objectQueue.decrementInternalProcessingCount();
                if (removeObject) {
                    if (objectQueue.queue.isEmpty() &&
                            objectQueue.activeCount == 0 &&
                            objectQueue.internalProcessingCount == 0) {
                        _poolMap.remove(key);
                        _poolList.remove(key);
                    }
                } else {
                    _evictionCursor.add(pair);
                    _totalIdle++;
                    if (_lifo) {
                        // Skip over the element we just added back
                        _evictionCursor.previous();
                    }
                }
            }
        }
        allocate();
    }

    /**
     * Resets the eviction key cursor and closes any
     * associated eviction object cursor
     */
    private void resetEvictionKeyCursor() {
        if (_evictionKeyCursor != null) {
            _evictionKeyCursor.close();
        }
        _evictionKeyCursor = _poolList.cursor();
        if (null != _evictionCursor) {
            _evictionCursor.close();
            _evictionCursor = null;
        }
    }

    /**
     * Resets the eviction object cursor for the given key
     *
     * @param key eviction key
     */
    private void resetEvictionObjectCursor(Object key) {
        if (_evictionCursor != null) {
            _evictionCursor.close();
        }
        if (_poolMap == null) {
            return;
        }
        ObjectQueue pool = _poolMap.get(key);
        if (pool != null) {
            CursorableLinkedList<ObjectTimestampPair<V>> queue = pool.queue;
            _evictionCursor = queue.cursor(_lifo ? queue.size() : 0);
        }
    }

    /**
     * Iterates through all the known keys and creates any necessary objects to maintain
     * the minimum level of pooled objects.
     * @see #getMinIdle
     * @see #setMinIdle
     * @throws Exception If there was an error whilst creating the pooled objects.
     */
    @SuppressWarnings("unchecked")
    private void ensureMinIdle() throws Exception {
        //Check if should sustain the pool
        if (getMinIdle() > 0) {
            Object[] keysCopy;
            synchronized(this) {
                // Get the current set of keys
                keysCopy = _poolMap.keySet().toArray();
            }

            // Loop through all elements in _poolList
            // Find out the total number of max active and max idle for that class
            // If the number is less than the minIdle, do creation loop to boost numbers
            for (int i=0; i < keysCopy.length; i++) {
                //Get the next key to process
                ensureMinIdle((K)keysCopy[i]);
            }
        }
    }

    /**
     * Re-creates any needed objects to maintain the minimum levels of
     * pooled objects for the specified key.
     *
     * This method uses {@link #calculateDeficit} to calculate the number
     * of objects to be created. {@link #calculateDeficit} can be overridden to
     * provide a different method of calculating the number of objects to be
     * created.
     * @param key The key to process
     * @throws Exception If there was an error whilst creating the pooled objects
     */
    private void ensureMinIdle(K key) throws Exception {
        // Calculate current pool objects
        ObjectQueue pool;
        synchronized(this) {
            pool = (_poolMap.get(key));
        }
        if (pool == null) {
            return;
        }

        // this method isn't synchronized so the
        // calculateDeficit is done at the beginning
        // as a loop limit and a second time inside the loop
        // to stop when another thread already returned the
        // needed objects
        int objectDeficit = calculateDeficit(pool, false);

        for (int i = 0; i < objectDeficit && calculateDeficit(pool, true) > 0; i++) {
            try {
                addObject(key);
            } finally {
                synchronized (this) {
                    pool.decrementInternalProcessingCount();
                }
                allocate();
            }
        }
    }

    //--- non-public methods ----------------------------------------

    /**
     * Start the eviction thread or service, or when
     * <code>delay</code> is non-positive, stop it
     * if it is already running.
     *
     * @param delay milliseconds between evictor runs.
     */
    protected synchronized void startEvictor(long delay) {
        if (null != _evictor) {
            EvictionTimer.cancel(_evictor);
            _evictor = null;
        }
        if (delay > 0) {
            _evictor = new Evictor();
            EvictionTimer.schedule(_evictor, delay, delay);
        }
    }

    /**
     * Returns pool info including {@link #getNumActive()}, {@link #getNumIdle()}
     * and currently defined keys.
     *
     * @return string containing debug information
     */
    synchronized String debugInfo() {
        StringBuffer buf = new StringBuffer();
        buf.append("Active: ").append(getNumActive()).append("\n");
        buf.append("Idle: ").append(getNumIdle()).append("\n");
        Iterator<K> it = _poolMap.keySet().iterator();
        while (it.hasNext()) {
            K key = it.next();
            buf.append("\t").append(key).append(" ").append(_poolMap.get(key)).append("\n");
        }
        return buf.toString();
    }

    /**
     * Returns the number of tests to be performed in an Evictor run,
     * based on the current values of <code>_numTestsPerEvictionRun</code>
     * and <code>_totalIdle</code>.
     *
     * @see #setNumTestsPerEvictionRun
     * @return the number of tests for the Evictor to run
     */
    private synchronized int getNumTests() {
        if (_numTestsPerEvictionRun >= 0) {
            return Math.min(_numTestsPerEvictionRun, _totalIdle);
        } else {
            return(int)(Math.ceil(_totalIdle/Math.abs((double)_numTestsPerEvictionRun)));
        }
    }

    /**
     * This returns the number of objects to create during the pool
     * sustain cycle. This will ensure that the minimum number of idle
     * instances is maintained without going past the maxActive value.
     *
     * @param pool the ObjectPool to calculate the deficit for
     * @param incrementInternal - Should the count of objects currently under
     *                            some form of internal processing be
     *                            incremented?
     * @return The number of objects to be created
     */
    private synchronized int calculateDeficit(ObjectQueue pool,
            boolean incrementInternal) {
        int objectDefecit = 0;

        //Calculate no of objects needed to be created, in order to have
        //the number of pooled objects < maxActive();
        objectDefecit = getMinIdle() - pool.queue.size();
        if (getMaxActive() > 0) {
            int growLimit = Math.max(0, getMaxActive() - pool.activeCount - pool.queue.size() - pool.internalProcessingCount);
            objectDefecit = Math.min(objectDefecit, growLimit);
        }

        // Take the maxTotal limit into account
        if (getMaxTotal() > 0) {
            int growLimit = Math.max(0, getMaxTotal() - getNumActive() - getNumIdle() - _totalInternalProcessing);
            objectDefecit = Math.min(objectDefecit, growLimit);
        }

        if (incrementInternal && objectDefecit > 0) {
            pool.incrementInternalProcessingCount();
        }
        return objectDefecit;
    }

    //--- inner classes ----------------------------------------------

    /**
     * A "struct" that keeps additional information about the actual queue of pooled objects.
     */
    private class ObjectQueue {
        /** Number of instances checked out to clients from this queue */
        private int activeCount = 0;

        /** Idle instance queue */
        private final CursorableLinkedList<ObjectTimestampPair<V>> queue = new CursorableLinkedList<ObjectTimestampPair<V>>();

        /** Number of instances in process of being created */
        private int internalProcessingCount = 0;

        /** Increment the active count for this queue */
        void incrementActiveCount() {
            synchronized (GenericKeyedObjectPool.this) {
                _totalActive++;
            }
            activeCount++;
        }

        /** Decrement the active count for this queue */
        void decrementActiveCount() {
            synchronized (GenericKeyedObjectPool.this) {
                _totalActive--;
            }
            if (activeCount > 0) {
                activeCount--;
            }
        }

        /** Record the fact that one more instance is queued for creation */
        void incrementInternalProcessingCount() {
            synchronized (GenericKeyedObjectPool.this) {
                _totalInternalProcessing++;
            }
            internalProcessingCount++;
        }

        /** Decrement the number of instances in process of being created */
        void decrementInternalProcessingCount() {
            synchronized (GenericKeyedObjectPool.this) {
                _totalInternalProcessing--;
            }
            internalProcessingCount--;
        }
    }

    /**
     * A simple "struct" encapsulating an object instance and a timestamp.
     *
     * Implements Comparable, objects are sorted from old to new.
     *
     * This is also used by {@link GenericObjectPool}.
     */
    static class ObjectTimestampPair<T> implements Comparable<T> {
        /**
         * Object instance
         */
        private final T value;

        /**
         * timestamp
         */
        private final long tstamp;

        /**
         * Create a new ObjectTimestampPair using the given object and the current system time.
         * @param val object instance
         */
        ObjectTimestampPair(T val) {
            this(val, System.currentTimeMillis());
        }

        /**
         * Create a new ObjectTimeStampPair using the given object and timestamp value.
         * @param val object instance
         * @param time long representation of timestamp
         */
        ObjectTimestampPair(T val, long time) {
            value = val;
            tstamp = time;
        }

        /**
         * Returns a string representation.
         *
         * @return String representing this ObjectTimestampPair
         */
        @Override
        public String toString() {
            return value + ";" + tstamp;
        }

        /**
         * Compares this to another object by casting the argument to an
         * ObjectTimestampPair.
         *
         * @param obj object to cmpare
         * @return result of comparison
         */
        @Override
        @SuppressWarnings("unchecked")
        public int compareTo(Object obj) {
            return compareTo((ObjectTimestampPair<T>) obj);
        }

        /**
         * Compares this to another ObjectTimestampPair, using the timestamp as basis for comparison.
         * Implementation is consistent with equals.
         *
         * @param other object to compare
         * @return result of comparison
         */
        public int compareTo(ObjectTimestampPair<T> other) {
            final long tstampdiff = this.tstamp - other.tstamp;
            if (tstampdiff == 0) {
                // make sure the natural ordering is consistent with equals
                // see java.lang.Comparable Javadocs
                return System.identityHashCode(this) - System.identityHashCode(other);
            } else {
                // handle int overflow
                return (int)Math.min(Math.max(tstampdiff, Integer.MIN_VALUE), Integer.MAX_VALUE);
            }
        }

        /**
         * @return the value
         */
        public T getValue() {
            return value;
        }

        /**
         * @return the tstamp
         */
        public long getTstamp() {
            return tstamp;
        }
    }

    /**
     * The idle object evictor {@link TimerTask}.
     * @see GenericKeyedObjectPool#setTimeBetweenEvictionRunsMillis
     */
    private class Evictor extends TimerTask {
        /**
         * Run pool maintenance.  Evict objects qualifying for eviction and then
         * invoke {@link GenericKeyedObjectPool#ensureMinIdle()}.
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
                //Evict from the pool
                try {
                    evict();
                } catch(Exception e) {
                    // ignored
                } catch(OutOfMemoryError oome) {
                    // Log problem but give evictor thread a chance to continue in
                    // case error is recoverable
                    oome.printStackTrace(System.err);
                }
                //Re-create idle instances.
                try {
                    ensureMinIdle();
                } catch (Exception e) {
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
     * configuration information for a <code>GenericKeyedObjectPool</code>.
     * @see GenericKeyedObjectPool#GenericKeyedObjectPool(KeyedPoolableObjectFactory,GenericKeyedObjectPool.Config)
     * @see GenericKeyedObjectPool#setConfig
     */
    public static class Config {
        //CHECKSTYLE: stop VisibilityModifier
        /**
         * @see GenericKeyedObjectPool#setMaxIdle
         */
        public int maxIdle = GenericKeyedObjectPool.DEFAULT_MAX_IDLE;
        /**
         * @see GenericKeyedObjectPool#setMaxActive
         */
        public int maxActive = GenericKeyedObjectPool.DEFAULT_MAX_ACTIVE;
        /**
         * @see GenericKeyedObjectPool#setMaxTotal
         */
        public int maxTotal = GenericKeyedObjectPool.DEFAULT_MAX_TOTAL;
        /**
         * @see GenericKeyedObjectPool#setMinIdle
         */
        public int minIdle = GenericKeyedObjectPool.DEFAULT_MIN_IDLE;
        /**
         * @see GenericKeyedObjectPool#setMaxWait
         */
        public long maxWait = GenericKeyedObjectPool.DEFAULT_MAX_WAIT;
        /**
         * @see GenericKeyedObjectPool#setWhenExhaustedAction
         */
        public byte whenExhaustedAction = GenericKeyedObjectPool.DEFAULT_WHEN_EXHAUSTED_ACTION;
        /**
         * @see GenericKeyedObjectPool#setTestOnBorrow
         */
        public boolean testOnBorrow = GenericKeyedObjectPool.DEFAULT_TEST_ON_BORROW;
        /**
         * @see GenericKeyedObjectPool#setTestOnReturn
         */
        public boolean testOnReturn = GenericKeyedObjectPool.DEFAULT_TEST_ON_RETURN;
        /**
         * @see GenericKeyedObjectPool#setTestWhileIdle
         */
        public boolean testWhileIdle = GenericKeyedObjectPool.DEFAULT_TEST_WHILE_IDLE;
        /**
         * @see GenericKeyedObjectPool#setTimeBetweenEvictionRunsMillis
         */
        public long timeBetweenEvictionRunsMillis = GenericKeyedObjectPool.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
        /**
         * @see GenericKeyedObjectPool#setNumTestsPerEvictionRun
         */
        public int numTestsPerEvictionRun =  GenericKeyedObjectPool.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
        /**
         * @see GenericKeyedObjectPool#setMinEvictableIdleTimeMillis
         */
        public long minEvictableIdleTimeMillis = GenericKeyedObjectPool.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
        /**
         * @see GenericKeyedObjectPool#setLifo
         */
        public boolean lifo = GenericKeyedObjectPool.DEFAULT_LIFO;
        //CHECKSTYLE: resume VisibilityModifier
    }

    /**
     * Latch used to control allocation order of objects to threads to ensure
     * fairness. That is, for each key, objects are allocated to threads in the order
     * that threads request objects.
     *
     * @since 1.5
     */
    private final class Latch<LK, LV> {

        /** key of associated pool */
        private final LK _key;

        /** keyed pool associated with this latch */
        private ObjectQueue _pool;

        /** holds an ObjectTimestampPair when this latch has been allocated an instance */
        private ObjectTimestampPair<LV> _pair;

        /** indicates that this latch can create an instance */
        private boolean _mayCreate = false;

        /**
         * Create a latch with the given key
         * @param key key of the pool associated with this latch
         */
        private Latch(LK key) {
            _key = key;
        }

        /**
         * Retuns the key of the associated pool
         * @return associated pool key
         */
        private synchronized LK getkey() {
            return _key;
        }

        /**
         * Returns the pool associated with this latch
         * @return pool
         */
        private synchronized ObjectQueue getPool() {
            return _pool;
        }

        /**
         * Sets the pool associated with this latch
         * @param pool the pool
         */
        private synchronized void setPool(ObjectQueue pool) {
            _pool = pool;
        }

        /**
         * Gets the ObjectTimestampPair allocated to this latch.
         * Returns null if this latch does not have an instance allocated to it.
         * @return the associated ObjectTimestampPair
         */
        private synchronized ObjectTimestampPair<LV> getPair() {
            return _pair;
        }

        /**
         * Allocate an ObjectTimestampPair to this latch.
         * @param pair ObjectTimestampPair on this latch
         */
        private synchronized void setPair(ObjectTimestampPair<LV> pair) {
            _pair = pair;
        }

        /**
         * Whether or not this latch can create an instance
         * @return true if this latch has an instance creation permit
         */
        private synchronized boolean mayCreate() {
            return _mayCreate;
        }

        /**
         * Sets the mayCreate property
         *
         * @param mayCreate true means this latch can create an instance
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

    //--- protected attributes ---------------------------------------

    /**
     * The cap on the number of idle instances in the pool.
     * @see #setMaxIdle
     * @see #getMaxIdle
     */
    private int _maxIdle = DEFAULT_MAX_IDLE;

    /**
     * The minimum no of idle objects to keep in the pool.
     * @see #setMinIdle
     * @see #getMinIdle
     */
    private volatile int _minIdle = DEFAULT_MIN_IDLE;

    /**
     * The cap on the number of active instances from the pool.
     * @see #setMaxActive
     * @see #getMaxActive
     */
    private int _maxActive = DEFAULT_MAX_ACTIVE;

    /**
     * The cap on the total number of instances from the pool if non-positive.
     * @see #setMaxTotal
     * @see #getMaxTotal
     */
    private int _maxTotal = DEFAULT_MAX_TOTAL;

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
     * {@link org.apache.tomcat.dbcp.pool.PoolableObjectFactory#validateObject validated}
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
     * {@link org.apache.tomcat.dbcp.pool.PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {@link #returnObject}.
     *
     * @see #getTestOnReturn
     * @see #setTestOnReturn
     */
    private volatile boolean _testOnReturn = DEFAULT_TEST_ON_RETURN;

    /**
     * When <code>true</code>, objects will be
     * {@link org.apache.tomcat.dbcp.pool.PoolableObjectFactory#validateObject validated}
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
     * The number of objects to examine during each run of the
     * idle object evictor thread (if any).
     * <p>
     * When a negative value is supplied, <code>ceil({@link #getNumIdle})/abs({@link #getNumTestsPerEvictionRun})</code>
     * tests will be run.  I.e., when the value is <code>-n</code>, roughly one <code>n</code>th of the
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

    /** My hash of pools (ObjectQueue). */
    private Map<K, ObjectQueue> _poolMap = null;

    /** The total number of active instances. */
    private int _totalActive = 0;

    /** The total number of idle instances. */
    private int _totalIdle = 0;

    /**
     * The number of objects subject to some form of internal processing
     * (usually creation or destruction) that should be included in the total
     * number of objects but are neither active nor idle.
     */
    private int _totalInternalProcessing = 0;

    /** My {@link KeyedPoolableObjectFactory}. */
    private KeyedPoolableObjectFactory<K, V> _factory = null;

    /**
     * Class loader for evictor thread to use since in a J2EE or similar
     * environment the context class loader for the evictor thread may have
     * visibility of the correct factory. See POOL-161.
     */
    private ClassLoader _factoryClassLoader = null;


    /**
     * My idle object eviction {@link TimerTask}, if any.
     */
    private Evictor _evictor = null;

    /**
     * A cursorable list of my pools.
     * @see GenericKeyedObjectPool.Evictor#run
     */
    private CursorableLinkedList<K> _poolList = null;

    /** Eviction cursor (over instances within-key) */
    private CursorableLinkedList<ObjectTimestampPair<V>>.Cursor _evictionCursor = null;

    /** Eviction cursor (over keys) */
    private CursorableLinkedList<K>.Cursor _evictionKeyCursor = null;

    /** Whether or not the pools behave as LIFO queues (last in first out) */
    private boolean _lifo = DEFAULT_LIFO;

    /**
     * Used to track the order in which threads call {@link #borrowObject(Object)} so
     * that objects can be allocated in the order in which the threads requested
     * them.
     */
    private final LinkedList<Latch<K, V>> _allocationQueue = new LinkedList<Latch<K, V>>();

}

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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * This class consists exclusively of static methods that operate on or return
 * ObjectPool or KeyedObjectPool related interfaces.
 *
 * @since 2.0
 */
public final class PoolUtils {

    private static final String MSG_MIN_IDLE = "minIdle must be non-negative.";
    public static final String MSG_NULL_KEY = "key must not be null.";
    private static final String MSG_NULL_KEYED_POOL = "keyedPool must not be null.";
    public static final String MSG_NULL_KEYS = "keys must not be null.";
    private static final String MSG_NULL_POOL = "pool must not be null.";

    /**
     * Timer used to periodically check pools idle object count. Because a
     * {@link Timer} creates a {@link Thread}, an IODH is used.
     */
    static class TimerHolder {
        static final Timer MIN_IDLE_TIMER = new Timer(true);
    }

    /**
     * PoolUtils instances should NOT be constructed in standard programming.
     * Instead, the class should be used procedurally: PoolUtils.adapt(aPool);.
     * This constructor is public to permit tools that require a JavaBean
     * instance to operate.
     */
    public PoolUtils() {
    }

    /**
     * Should the supplied Throwable be re-thrown (eg if it is an instance of
     * one of the Throwables that should never be swallowed). Used by the pool
     * error handling for operations that throw exceptions that normally need to
     * be ignored.
     *
     * @param t
     *            The Throwable to check
     * @throws ThreadDeath
     *             if that is passed in
     * @throws VirtualMachineError
     *             if that is passed in
     */
    public static void checkRethrow(final Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }

    /**
     * Periodically check the idle object count for the pool. At most one idle
     * object will be added per period. If there is an exception when calling
     * {@link ObjectPool#addObject()} then no more checks will be performed.
     *
     * @param pool
     *            the pool to check periodically.
     * @param minIdle
     *            if the {@link ObjectPool#getNumIdle()} is less than this then
     *            add an idle object.
     * @param period
     *            the frequency to check the number of idle objects in a pool,
     *            see {@link Timer#schedule(TimerTask, long, long)}.
     * @param <T> the type of objects in the pool
     * @return the {@link TimerTask} that will periodically check the pools idle
     *         object count.
     * @throws IllegalArgumentException
     *             when {@code pool} is {@code null} or when {@code minIdle} is
     *             negative or when {@code period} isn't valid for
     *             {@link Timer#schedule(TimerTask, long, long)}
     */
    public static <T> TimerTask checkMinIdle(final ObjectPool<T> pool,
            final int minIdle, final long period)
            throws IllegalArgumentException {
        if (pool == null) {
            throw new IllegalArgumentException(MSG_NULL_KEYED_POOL);
        }
        if (minIdle < 0) {
            throw new IllegalArgumentException(MSG_MIN_IDLE);
        }
        final TimerTask task = new ObjectPoolMinIdleTimerTask<>(pool, minIdle);
        getMinIdleTimer().schedule(task, 0L, period);
        return task;
    }

    /**
     * Periodically check the idle object count for the key in the keyedPool. At
     * most one idle object will be added per period. If there is an exception
     * when calling {@link KeyedObjectPool#addObject(Object)} then no more
     * checks for that key will be performed.
     *
     * @param keyedPool
     *            the keyedPool to check periodically.
     * @param key
     *            the key to check the idle count of.
     * @param minIdle
     *            if the {@link KeyedObjectPool#getNumIdle(Object)} is less than
     *            this then add an idle object.
     * @param period
     *            the frequency to check the number of idle objects in a
     *            keyedPool, see {@link Timer#schedule(TimerTask, long, long)}.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     * @return the {@link TimerTask} that will periodically check the pools idle
     *         object count.
     * @throws IllegalArgumentException
     *             when {@code keyedPool}, {@code key} is {@code null} or
     *             when {@code minIdle} is negative or when {@code period} isn't
     *             valid for {@link Timer#schedule(TimerTask, long, long)}.
     */
    public static <K, V> TimerTask checkMinIdle(
            final KeyedObjectPool<K, V> keyedPool, final K key,
            final int minIdle, final long period)
            throws IllegalArgumentException {
        if (keyedPool == null) {
            throw new IllegalArgumentException(MSG_NULL_KEYED_POOL);
        }
        if (key == null) {
            throw new IllegalArgumentException(MSG_NULL_KEY);
        }
        if (minIdle < 0) {
            throw new IllegalArgumentException(MSG_MIN_IDLE);
        }
        final TimerTask task = new KeyedObjectPoolMinIdleTimerTask<>(
                keyedPool, key, minIdle);
        getMinIdleTimer().schedule(task, 0L, period);
        return task;
    }

    /**
     * Periodically check the idle object count for each key in the
     * {@code Collection keys} in the keyedPool. At most one idle object will be
     * added per period.
     *
     * @param keyedPool
     *            the keyedPool to check periodically.
     * @param keys
     *            a collection of keys to check the idle object count.
     * @param minIdle
     *            if the {@link KeyedObjectPool#getNumIdle(Object)} is less than
     *            this then add an idle object.
     * @param period
     *            the frequency to check the number of idle objects in a
     *            keyedPool, see {@link Timer#schedule(TimerTask, long, long)}.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     * @return a {@link Map} of key and {@link TimerTask} pairs that will
     *         periodically check the pools idle object count.
     * @throws IllegalArgumentException
     *             when {@code keyedPool}, {@code keys}, or any of the values in
     *             the collection is {@code null} or when {@code minIdle} is
     *             negative or when {@code period} isn't valid for
     *             {@link Timer#schedule(TimerTask, long, long)}.
     * @see #checkMinIdle(KeyedObjectPool, Object, int, long)
     */
    public static <K, V> Map<K, TimerTask> checkMinIdle(
            final KeyedObjectPool<K, V> keyedPool, final Collection<K> keys,
            final int minIdle, final long period)
            throws IllegalArgumentException {
        if (keys == null) {
            throw new IllegalArgumentException(MSG_NULL_KEYS);
        }
        final Map<K, TimerTask> tasks = new HashMap<>(keys.size());
        for (K key : keys) {
            final TimerTask task = checkMinIdle(keyedPool, key, minIdle, period);
            tasks.put(key, task);
        }
        return tasks;
    }

    /**
     * Calls {@link ObjectPool#addObject()} on {@code pool} {@code count} number
     * of times.
     *
     * @param pool
     *            the pool to prefill.
     * @param count
     *            the number of idle objects to add.
     * @param <T> the type of objects in the pool
     * @throws Exception
     *             when {@link ObjectPool#addObject()} fails.
     * @throws IllegalArgumentException
     *             when {@code pool} is {@code null}.
     * @deprecated Use {@link ObjectPool#addObjects(int)}.
     */
    @Deprecated
    public static <T> void prefill(final ObjectPool<T> pool, final int count)
            throws Exception, IllegalArgumentException {
        if (pool == null) {
            throw new IllegalArgumentException(MSG_NULL_POOL);
        }
        pool.addObjects(count);
    }

    /**
     * Calls {@link KeyedObjectPool#addObject(Object)} on {@code keyedPool} with
     * {@code key} {@code count} number of times.
     *
     * @param keyedPool
     *            the keyedPool to prefill.
     * @param key
     *            the key to add objects for.
     * @param count
     *            the number of idle objects to add for {@code key}.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     * @throws Exception
     *             when {@link KeyedObjectPool#addObject(Object)} fails.
     * @throws IllegalArgumentException
     *             when {@code keyedPool} or {@code key} is {@code null}.
     * @deprecated Use {@link KeyedObjectPool#addObjects(Object, int)}.
     */
    @Deprecated
    public static <K, V> void prefill(final KeyedObjectPool<K, V> keyedPool,
            final K key, final int count) throws Exception,
            IllegalArgumentException {
        if (keyedPool == null) {
            throw new IllegalArgumentException(MSG_NULL_KEYED_POOL);
        }
        keyedPool.addObjects(key, count);
    }

    /**
     * Calls {@link KeyedObjectPool#addObject(Object)} on {@code keyedPool} with
     * each key in {@code keys} for {@code count} number of times. This has
     * the same effect as calling {@link #prefill(KeyedObjectPool, Object, int)}
     * for each key in the {@code keys} collection.
     *
     * @param keyedPool
     *            the keyedPool to prefill.
     * @param keys
     *            {@link Collection} of keys to add objects for.
     * @param count
     *            the number of idle objects to add for each {@code key}.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     * @throws Exception
     *             when {@link KeyedObjectPool#addObject(Object)} fails.
     * @throws IllegalArgumentException
     *             when {@code keyedPool}, {@code keys}, or any value in
     *             {@code keys} is {@code null}.
     * @see #prefill(KeyedObjectPool, Object, int)
     * @deprecated Use {@link KeyedObjectPool#addObjects(Collection, int)}.
     */
    @Deprecated
    public static <K, V> void prefill(final KeyedObjectPool<K, V> keyedPool,
            final Collection<K> keys, final int count) throws Exception,
            IllegalArgumentException {
        if (keys == null) {
            throw new IllegalArgumentException(MSG_NULL_KEYS);
        }
        keyedPool.addObjects(keys, count);
    }

    /**
     * Returns a synchronized (thread-safe) PooledObjectFactory backed by the
     * specified PooledObjectFactory.
     *
     * @param factory
     *            the PooledObjectFactory to be "wrapped" in a synchronized
     *            PooledObjectFactory.
     * @param <T> the type of objects in the pool
     * @return a synchronized view of the specified PooledObjectFactory.
     */
    public static <T> PooledObjectFactory<T> synchronizedPooledFactory(
            final PooledObjectFactory<T> factory) {
        return new SynchronizedPooledObjectFactory<>(factory);
    }

    /**
     * Returns a synchronized (thread-safe) KeyedPooledObjectFactory backed by
     * the specified KeyedPoolableObjectFactory.
     *
     * @param keyedFactory
     *            the KeyedPooledObjectFactory to be "wrapped" in a
     *            synchronized KeyedPooledObjectFactory.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     * @return a synchronized view of the specified KeyedPooledObjectFactory.
     */
    public static <K, V> KeyedPooledObjectFactory<K, V> synchronizedKeyedPooledFactory(
            final KeyedPooledObjectFactory<K, V> keyedFactory) {
        return new SynchronizedKeyedPooledObjectFactory<>(keyedFactory);
    }

    /**
     * Gets the {@code Timer} for checking keyedPool's idle count.
     *
     * @return the {@link Timer} for checking keyedPool's idle count.
     */
    private static Timer getMinIdleTimer() {
        return TimerHolder.MIN_IDLE_TIMER;
    }

    /**
     * Timer task that adds objects to the pool until the number of idle
     * instances reaches the configured minIdle. Note that this is not the same
     * as the pool's minIdle setting.
     *
     * @param <T> type of objects in the pool
     */
    private static final class ObjectPoolMinIdleTimerTask<T> extends TimerTask {

        /** Minimum number of idle instances. Not the same as pool.getMinIdle(). */
        private final int minIdle;

        /** Object pool */
        private final ObjectPool<T> pool;

        /**
         * Create a new ObjectPoolMinIdleTimerTask for the given pool with the
         * given minIdle setting.
         *
         * @param pool
         *            object pool
         * @param minIdle
         *            number of idle instances to maintain
         * @throws IllegalArgumentException
         *             if the pool is null
         */
        ObjectPoolMinIdleTimerTask(final ObjectPool<T> pool, final int minIdle)
                throws IllegalArgumentException {
            if (pool == null) {
                throw new IllegalArgumentException(MSG_NULL_POOL);
            }
            this.pool = pool;
            this.minIdle = minIdle;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            boolean success = false;
            try {
                if (pool.getNumIdle() < minIdle) {
                    pool.addObject();
                }
                success = true;

            } catch (final Exception e) {
                cancel();
            } finally {
                // detect other types of Throwable and cancel this Timer
                if (!success) {
                    cancel();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("ObjectPoolMinIdleTimerTask");
            sb.append("{minIdle=").append(minIdle);
            sb.append(", pool=").append(pool);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * Timer task that adds objects to the pool until the number of idle
     * instances for the given key reaches the configured minIdle. Note that
     * this is not the same as the pool's minIdle setting.
     *
     * @param <K> object pool key type
     * @param <V> object pool value type
     */
    private static final class KeyedObjectPoolMinIdleTimerTask<K, V> extends
            TimerTask {

        /** Minimum number of idle instances. Not the same as pool.getMinIdle(). */
        private final int minIdle;

        /** Key to ensure minIdle for */
        private final K key;

        /** Keyed object pool */
        private final KeyedObjectPool<K, V> keyedPool;

        /**
         * Creates a new KeyedObjecPoolMinIdleTimerTask.
         *
         * @param keyedPool
         *            keyed object pool
         * @param key
         *            key to ensure minimum number of idle instances
         * @param minIdle
         *            minimum number of idle instances
         * @throws IllegalArgumentException
         *             if the key is null
         */
        KeyedObjectPoolMinIdleTimerTask(final KeyedObjectPool<K, V> keyedPool,
                final K key, final int minIdle) throws IllegalArgumentException {
            if (keyedPool == null) {
                throw new IllegalArgumentException(
                        MSG_NULL_KEYED_POOL);
            }
            this.keyedPool = keyedPool;
            this.key = key;
            this.minIdle = minIdle;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            boolean success = false;
            try {
                if (keyedPool.getNumIdle(key) < minIdle) {
                    keyedPool.addObject(key);
                }
                success = true;

            } catch (final Exception e) {
                cancel();

            } finally {
                // detect other types of Throwable and cancel this Timer
                if (!success) {
                    cancel();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("KeyedObjectPoolMinIdleTimerTask");
            sb.append("{minIdle=").append(minIdle);
            sb.append(", key=").append(key);
            sb.append(", keyedPool=").append(keyedPool);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * A fully synchronized PooledObjectFactory that wraps a
     * PooledObjectFactory and synchronizes access to the wrapped factory
     * methods.
     * <p>
     * <b>Note:</b> This should not be used on pool implementations that already
     * provide proper synchronization such as the pools provided in the Commons
     * Pool library.
     * </p>
     *
     * @param <T> pooled object factory type
     */
    private static final class SynchronizedPooledObjectFactory<T> implements
            PooledObjectFactory<T> {

        /** Synchronization lock */
        private final WriteLock writeLock = new ReentrantReadWriteLock().writeLock();

        /** Wrapped factory */
        private final PooledObjectFactory<T> factory;

        /**
         * Creates a SynchronizedPoolableObjectFactory wrapping the given
         * factory.
         *
         * @param factory
         *            underlying factory to wrap
         * @throws IllegalArgumentException
         *             if the factory is null
         */
        SynchronizedPooledObjectFactory(final PooledObjectFactory<T> factory)
                throws IllegalArgumentException {
            if (factory == null) {
                throw new IllegalArgumentException("factory must not be null.");
            }
            this.factory = factory;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PooledObject<T> makeObject() throws Exception {
            writeLock.lock();
            try {
                return factory.makeObject();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void destroyObject(final PooledObject<T> p) throws Exception {
            writeLock.lock();
            try {
                factory.destroyObject(p);
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public void destroyObject(PooledObject<T> p, DestroyMode mode) throws Exception {
            destroyObject(p);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean validateObject(final PooledObject<T> p) {
            writeLock.lock();
            try {
                return factory.validateObject(p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void activateObject(final PooledObject<T> p) throws Exception {
            writeLock.lock();
            try {
                factory.activateObject(p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void passivateObject(final PooledObject<T> p) throws Exception {
            writeLock.lock();
            try {
                factory.passivateObject(p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("SynchronizedPoolableObjectFactory");
            sb.append("{factory=").append(factory);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * A fully synchronized KeyedPooledObjectFactory that wraps a
     * KeyedPooledObjectFactory and synchronizes access to the wrapped factory
     * methods.
     * <p>
     * <b>Note:</b> This should not be used on pool implementations that already
     * provide proper synchronization such as the pools provided in the Commons
     * Pool library.
     * </p>
     *
     * @param <K> pooled object factory key type
     * @param <V> pooled object factory key value
     */
    private static final class SynchronizedKeyedPooledObjectFactory<K, V>
            implements KeyedPooledObjectFactory<K, V> {

        /** Synchronization lock */
        private final WriteLock writeLock = new ReentrantReadWriteLock().writeLock();

        /** Wrapped factory */
        private final KeyedPooledObjectFactory<K, V> keyedFactory;

        /**
         * Creates a SynchronizedKeyedPoolableObjectFactory wrapping the given
         * factory.
         *
         * @param keyedFactory
         *            underlying factory to wrap
         * @throws IllegalArgumentException
         *             if the factory is null
         */
        SynchronizedKeyedPooledObjectFactory(
                final KeyedPooledObjectFactory<K, V> keyedFactory)
                throws IllegalArgumentException {
            if (keyedFactory == null) {
                throw new IllegalArgumentException(
                        "keyedFactory must not be null.");
            }
            this.keyedFactory = keyedFactory;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public PooledObject<V> makeObject(final K key) throws Exception {
            writeLock.lock();
            try {
                return keyedFactory.makeObject(key);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void destroyObject(final K key, final PooledObject<V> p) throws Exception {
            writeLock.lock();
            try {
                keyedFactory.destroyObject(key, p);
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public void destroyObject(K key, PooledObject<V> p, DestroyMode mode) throws Exception {
            destroyObject(key, p);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean validateObject(final K key, final PooledObject<V> p) {
            writeLock.lock();
            try {
                return keyedFactory.validateObject(key, p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void activateObject(final K key, final PooledObject<V> p) throws Exception {
            writeLock.lock();
            try {
                keyedFactory.activateObject(key, p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void passivateObject(final K key, final PooledObject<V> p) throws Exception {
            writeLock.lock();
            try {
                keyedFactory.passivateObject(key, p);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("SynchronizedKeyedPoolableObjectFactory");
            sb.append("{keyedFactory=").append(keyedFactory);
            sb.append('}');
            return sb.toString();
        }
    }
}

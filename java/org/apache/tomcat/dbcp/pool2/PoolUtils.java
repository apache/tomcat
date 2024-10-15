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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * This class consists exclusively of static methods that operate on or return
 * ObjectPool or KeyedObjectPool related interfaces.
 *
 * @since 2.0
 */
public final class PoolUtils {

    /**
     * Encapsulate the logic for when the next poolable object should be
     * discarded. Each time update is called, the next time to shrink is
     * recomputed, based on the float factor, number of idle instances in the
     * pool and high water mark. Float factor is assumed to be between 0 and 1.
     * Values closer to 1 cause less frequent erosion events. Erosion event
     * timing also depends on numIdle. When this value is relatively high (close
     * to previously established high water mark), erosion occurs more
     * frequently.
     */
    private static final class ErodingFactor {
        /** Determines frequency of "erosion" events */
        private final float factor;

        /** Time of next shrink event */
        private transient volatile long nextShrinkMillis;

        /** High water mark - largest numIdle encountered */
        private transient volatile int idleHighWaterMark;

        /**
         * Creates a new ErodingFactor with the given erosion factor.
         *
         * @param factor
         *            erosion factor
         */
        ErodingFactor(final float factor) {
            this.factor = factor;
            nextShrinkMillis = System.currentTimeMillis() + (long) (900000 * factor); // now + 15 min * factor
            idleHighWaterMark = 1;
        }

        /**
         * Gets the time of the next erosion event.
         *
         * @return next shrink time
         */
        public long getNextShrink() {
            return nextShrinkMillis;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingFactor{" + "factor=" + factor +
                    ", idleHighWaterMark=" + idleHighWaterMark + '}';
        }

        /**
         * Updates internal state using the supplied time and numIdle.
         *
         * @param nowMillis
         *            current time
         * @param numIdle
         *            number of idle elements in the pool
         */
        public void update(final long nowMillis, final int numIdle) {
            final int idle = Math.max(0, numIdle);
            idleHighWaterMark = Math.max(idle, idleHighWaterMark);
            final float maxInterval = 15f;
            final float minutes = maxInterval +
                    (1f - maxInterval) / idleHighWaterMark * idle;
            nextShrinkMillis = nowMillis + (long) (minutes * 60000f * factor);
        }
    }
    /**
     * Decorates a keyed object pool, adding "eroding" behavior. Based on the
     * configured erosion factor, objects returning to the pool
     * may be invalidated instead of being added to idle capacity.
     *
     * @param <K> object pool key type
     * @param <V> object pool value type
     */
    private static class ErodingKeyedObjectPool<K, V> implements KeyedObjectPool<K, V> {

        /** Underlying pool */
        private final KeyedObjectPool<K, V> keyedPool;

        /** Erosion factor */
        private final ErodingFactor erodingFactor;

        /**
         * Creates an ErodingObjectPool wrapping the given pool using the
         * specified erosion factor.
         *
         * @param keyedPool
         *            underlying pool - must not be null
         * @param erodingFactor
         *            erosion factor - determines the frequency of erosion
         *            events
         * @see #erodingFactor
         */
        protected ErodingKeyedObjectPool(final KeyedObjectPool<K, V> keyedPool,
                final ErodingFactor erodingFactor) {
            if (keyedPool == null) {
                throw new IllegalArgumentException(
                        MSG_NULL_KEYED_POOL);
            }
            this.keyedPool = keyedPool;
            this.erodingFactor = erodingFactor;
        }

        /**
         * Creates an ErodingObjectPool wrapping the given pool using the
         * specified erosion factor.
         *
         * @param keyedPool
         *            underlying pool
         * @param factor
         *            erosion factor - determines the frequency of erosion
         *            events
         * @see #erodingFactor
         */
        ErodingKeyedObjectPool(final KeyedObjectPool<K, V> keyedPool,
                final float factor) {
            this(keyedPool, new ErodingFactor(factor));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addObject(final K key) throws Exception {
            keyedPool.addObject(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public V borrowObject(final K key) throws Exception {
            return keyedPool.borrowObject(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear() throws Exception {
            keyedPool.clear();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear(final K key) throws Exception {
            keyedPool.clear(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            try {
                keyedPool.close();
            } catch (final Exception ignored) {
                // ignored
            }
        }

        /**
         * Gets the eroding factor for the given key
         *
         * @param key
         *            key
         * @return eroding factor for the given keyed pool
         */
        protected ErodingFactor getErodingFactor(final K key) {
            return erodingFactor;
        }

        /**
         * Gets the underlying pool
         *
         * @return the keyed pool that this ErodingKeyedObjectPool wraps
         */
        protected KeyedObjectPool<K, V> getKeyedPool() {
            return keyedPool;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<K> getKeys() {
            return keyedPool.getKeys();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive() {
            return keyedPool.getNumActive();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive(final K key) {
            return keyedPool.getNumActive(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle() {
            return keyedPool.getNumIdle();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle(final K key) {
            return keyedPool.getNumIdle(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invalidateObject(final K key, final V obj) {
            try {
                keyedPool.invalidateObject(key, obj);
            } catch (final Exception ignored) {
                // ignored
            }
        }

        /**
         * Returns obj to the pool, unless erosion is triggered, in which case
         * obj is invalidated. Erosion is triggered when there are idle
         * instances in the pool associated with the given key and more than the
         * configured {@link #erodingFactor erosion factor} time has elapsed
         * since the last returnObject activation.
         *
         * @param obj
         *            object to return or invalidate
         * @param key
         *            key
         * @see #erodingFactor
         */
        @Override
        public void returnObject(final K key, final V obj) throws Exception {
            boolean discard = false;
            final long nowMillis = System.currentTimeMillis();
            final ErodingFactor factor = getErodingFactor(key);
            synchronized (keyedPool) {
                if (factor.getNextShrink() < nowMillis) {
                    final int numIdle = getNumIdle(key);
                    if (numIdle > 0) {
                        discard = true;
                    }

                    factor.update(nowMillis, numIdle);
                }
            }
            try {
                if (discard) {
                    keyedPool.invalidateObject(key, obj);
                } else {
                    keyedPool.returnObject(key, obj);
                }
            } catch (final Exception ignored) {
                // ignored
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingKeyedObjectPool{" + "factor=" +
                    erodingFactor + ", keyedPool=" + keyedPool + '}';
        }
    }

    /**
     * Decorates an object pool, adding "eroding" behavior. Based on the
     * configured {@link #factor erosion factor}, objects returning to the pool
     * may be invalidated instead of being added to idle capacity.
     *
     * @param <T> type of objects in the pool
     *
     */
    private static class ErodingObjectPool<T> implements ObjectPool<T> {

        /** Underlying object pool */
        private final ObjectPool<T> pool;

        /** Erosion factor */
        private final ErodingFactor factor;

        /**
         * Creates an ErodingObjectPool wrapping the given pool using the
         * specified erosion factor.
         *
         * @param pool
         *            underlying pool
         * @param factor
         *            erosion factor - determines the frequency of erosion
         *            events
         * @see #factor
         */
        ErodingObjectPool(final ObjectPool<T> pool, final float factor) {
            this.pool = pool;
            this.factor = new ErodingFactor(factor);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addObject() throws Exception{
            pool.addObject();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public T borrowObject() throws Exception {
            return pool.borrowObject();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear() throws Exception {
            pool.clear();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            try {
                pool.close();
            } catch (final Exception ignored) {
                // ignored
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive() {
            return pool.getNumActive();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle() {
            return pool.getNumIdle();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invalidateObject(final T obj) {
            try {
                pool.invalidateObject(obj);
            } catch (final Exception ignored) {
                // ignored
            }
        }

        /**
         * Returns * Gets obj to the pool, unless erosion is triggered, in which case
         * obj is invalidated. Erosion is triggered when there are idle
         * instances in the pool and more than the {@link #factor erosion
         * factor}-determined time has elapsed since the last returnObject
         * activation.
         *
         * @param obj
         *            object to return or invalidate
         * @see #factor
         */
        @Override
        public void returnObject(final T obj) {
            boolean discard = false;
            final long nowMillis = System.currentTimeMillis();
            synchronized (pool) {
                if (factor.getNextShrink() < nowMillis) { // XXX: Pool 3: move test
                                                    // out of sync block
                    final int numIdle = pool.getNumIdle();
                    if (numIdle > 0) {
                        discard = true;
                    }

                    factor.update(nowMillis, numIdle);
                }
            }
            try {
                if (discard) {
                    pool.invalidateObject(obj);
                } else {
                    pool.returnObject(obj);
                }
            } catch (final Exception ignored) {
                // ignored
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingObjectPool{" + "factor=" + factor + ", pool=" +
                    pool + '}';
        }
    }
    /**
     * Extends ErodingKeyedObjectPool to allow erosion to take place on a
     * per-key basis. Timing of erosion events is tracked separately for
     * separate keyed pools.
     *
     * @param <K> object pool key type
     * @param <V> object pool value type
     */
    private static final class ErodingPerKeyKeyedObjectPool<K, V> extends ErodingKeyedObjectPool<K, V> {

        /** Erosion factor - same for all pools */
        private final float factor;

        /** Map of ErodingFactor instances keyed on pool keys */
        private final Map<K, ErodingFactor> factors = Collections.synchronizedMap(new HashMap<>());

        /**
         * Creates a new ErordingPerKeyKeyedObjectPool decorating the given keyed
         * pool with the specified erosion factor.
         *
         * @param keyedPool
         *            underlying keyed pool
         * @param factor
         *            erosion factor
         */
        ErodingPerKeyKeyedObjectPool(final KeyedObjectPool<K, V> keyedPool, final float factor) {
            super(keyedPool, null);
            this.factor = factor;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected ErodingFactor getErodingFactor(final K key) {
            // This may result in two ErodingFactors being created for a key
            // since they are small and cheap this is okay.
            return factors.computeIfAbsent(key, k -> new ErodingFactor(this.factor));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "ErodingPerKeyKeyedObjectPool{" + "factor=" + factor +
                    ", keyedPool=" + getKeyedPool() + '}';
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
    private static final class KeyedObjectPoolMinIdleTimerTask<K, V> extends TimerTask {

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
     * Timer task that adds objects to the pool until the number of idle
     * instances reaches the configured minIdle. Note that this is not the same
     * as the pool's minIdle setting.
     *
     * @param <T> type of objects in the pool
     *
     */
    private static final class ObjectPoolMinIdleTimerTask<T> extends TimerTask {

        /** Minimum number of idle instances. Not the same as pool.getMinIdle(). */
        private final int minIdle;

        /** Object pool */
        private final ObjectPool<T> pool;

        /**
         * Constructs a new ObjectPoolMinIdleTimerTask for the given pool with the
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
     * A synchronized (thread-safe) KeyedObjectPool backed by the specified
     * KeyedObjectPool.
     * <p>
     * <b>Note:</b> This should not be used on pool implementations that already
     * provide proper synchronization such as the pools provided in the Commons
     * Pool library. Wrapping a pool that {@link #wait() waits} for poolable
     * objects to be returned before allowing another one to be borrowed with
     * another layer of synchronization will cause liveliness issues or a
     * deadlock.
     * </p>
     *
     * @param <K> object pool key type
     * @param <V> object pool value type
     */
    static final class SynchronizedKeyedObjectPool<K, V> implements KeyedObjectPool<K, V> {

        /**
         * Object whose monitor is used to synchronize methods on the wrapped
         * pool.
         */
        private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

        /** Underlying object pool */
        private final KeyedObjectPool<K, V> keyedPool;

        /**
         * Creates a new SynchronizedKeyedObjectPool wrapping the given pool
         *
         * @param keyedPool
         *            KeyedObjectPool to wrap
         * @throws IllegalArgumentException
         *             if keyedPool is null
         */
        SynchronizedKeyedObjectPool(final KeyedObjectPool<K, V> keyedPool)
                throws IllegalArgumentException {
            if (keyedPool == null) {
                throw new IllegalArgumentException(
                        MSG_NULL_KEYED_POOL);
            }
            this.keyedPool = keyedPool;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addObject(final K key) throws Exception {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.addObject(key);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public V borrowObject(final K key) throws Exception {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                return keyedPool.borrowObject(key);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear() throws Exception {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.clear();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear(final K key) throws Exception {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.clear(key);
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.close();
            } catch (final Exception ignored) {
                // ignored as of Pool 2
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<K> getKeys() {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return keyedPool.getKeys();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive() {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return keyedPool.getNumActive();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive(final K key) {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return keyedPool.getNumActive(key);
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle() {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return keyedPool.getNumIdle();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle(final K key) {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return keyedPool.getNumIdle(key);
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invalidateObject(final K key, final V obj) {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.invalidateObject(key, obj);
            } catch (final Exception ignored) {
                // ignored as of Pool 2
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void returnObject(final K key, final V obj) {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                keyedPool.returnObject(key, obj);
            } catch (final Exception ignored) {
                // ignored
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
            sb.append("SynchronizedKeyedObjectPool");
            sb.append("{keyedPool=").append(keyedPool);
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
     * @param <V> pooled object factory value type
     */
    private static final class SynchronizedKeyedPooledObjectFactory<K, V> implements KeyedPooledObjectFactory<K, V> {

        /** Synchronization lock */
        private final WriteLock writeLock = new ReentrantReadWriteLock().writeLock();

        /** Wrapped factory */
        private final KeyedPooledObjectFactory<K, V> keyedFactory;

        /**
         * Creates a SynchronizedKeyedPooledObjectFactory wrapping the given
         * factory.
         *
         * @param keyedFactory
         *            underlying factory to wrap
         * @throws IllegalArgumentException
         *             if the factory is null
         */
        SynchronizedKeyedPooledObjectFactory(final KeyedPooledObjectFactory<K, V> keyedFactory) throws IllegalArgumentException {
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
        public void destroyObject(final K key, final PooledObject<V> p) throws Exception {
            writeLock.lock();
            try {
                keyedFactory.destroyObject(key, p);
            } finally {
                writeLock.unlock();
            }
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
            sb.append("SynchronizedKeyedPooledObjectFactory");
            sb.append("{keyedFactory=").append(keyedFactory);
            sb.append('}');
            return sb.toString();
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
    }

    /**
     * A synchronized (thread-safe) ObjectPool backed by the specified
     * ObjectPool.
     * <p>
     * <b>Note:</b> This should not be used on pool implementations that already
     * provide proper synchronization such as the pools provided in the Commons
     * Pool library. Wrapping a pool that {@link #wait() waits} for poolable
     * objects to be returned before allowing another one to be borrowed with
     * another layer of synchronization will cause liveliness issues or a
     * deadlock.
     * </p>
     *
     * @param <T> type of objects in the pool
     *
     */
    private static final class SynchronizedObjectPool<T> implements ObjectPool<T> {

        /**
         * Object whose monitor is used to synchronize methods on the wrapped
         * pool.
         */
        private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

        /** the underlying object pool */
        private final ObjectPool<T> pool;

        /**
         * Creates a new SynchronizedObjectPool wrapping the given pool.
         *
         * @param pool
         *            the ObjectPool to be "wrapped" in a synchronized
         *            ObjectPool.
         * @throws IllegalArgumentException
         *             if the pool is null
         */
        SynchronizedObjectPool(final ObjectPool<T> pool)
                throws IllegalArgumentException {
            if (pool == null) {
                throw new IllegalArgumentException(MSG_NULL_POOL);
            }
            this.pool = pool;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addObject() throws Exception {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                pool.addObject();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public T borrowObject() throws Exception {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                return pool.borrowObject();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear() throws Exception {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                pool.clear();
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                pool.close();
            } catch (final Exception ignored) {
                // ignored as of Pool 2
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumActive() {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return pool.getNumActive();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getNumIdle() {
            final ReadLock readLock = readWriteLock.readLock();
            readLock.lock();
            try {
                return pool.getNumIdle();
            } finally {
                readLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void invalidateObject(final T obj) {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                pool.invalidateObject(obj);
            } catch (final Exception ignored) {
                // ignored as of Pool 2
            } finally {
                writeLock.unlock();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void returnObject(final T obj) {
            final WriteLock writeLock = readWriteLock.writeLock();
            writeLock.lock();
            try {
                pool.returnObject(obj);
            } catch (final Exception ignored) {
                // ignored as of Pool 2
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
            sb.append("SynchronizedObjectPool");
            sb.append("{pool=").append(pool);
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
        public void destroyObject(final PooledObject<T> p) throws Exception {
            writeLock.lock();
            try {
                factory.destroyObject(p);
            } finally {
                writeLock.unlock();
            }
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
    }

    /**
     * Timer used to periodically check pools idle object count. Because a
     * {@link Timer} creates a {@link Thread}, an IODH is used.
     */
    static class TimerHolder {
        static final Timer MIN_IDLE_TIMER = new Timer(true);
    }

    private static final String MSG_FACTOR_NEGATIVE = "factor must be positive.";

    private static final String MSG_MIN_IDLE = "minIdle must be non-negative.";

    static final String MSG_NULL_KEY = "key must not be null.";

    private static final String MSG_NULL_KEYED_POOL = "keyedPool must not be null.";

    static final String MSG_NULL_KEYS = "keys must not be null.";

    private static final String MSG_NULL_POOL = "pool must not be null.";

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
     * @param periodMillis
     *            the frequency in milliseconds to check the number of idle objects in a
     *            keyedPool, see {@link Timer#schedule(TimerTask, long, long)}.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     *
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
            final int minIdle, final long periodMillis)
            throws IllegalArgumentException {
        if (keys == null) {
            throw new IllegalArgumentException(MSG_NULL_KEYS);
        }
        final Map<K, TimerTask> tasks = new HashMap<>(keys.size());
        for (K key : keys) {
            final TimerTask task = checkMinIdle(keyedPool, key, minIdle, periodMillis);
            tasks.put(key, task);
        }
        return tasks;
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
     * @param periodMillis
     *            the frequency in milliseconds to check the number of idle objects in a
     *            keyedPool, see {@link Timer#schedule(TimerTask, long, long)}.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     *
     * @return the {@link TimerTask} that will periodically check the pools idle
     *         object count.
     * @throws IllegalArgumentException
     *             when {@code keyedPool}, {@code key} is {@code null} or
     *             when {@code minIdle} is negative or when {@code period} isn't
     *             valid for {@link Timer#schedule(TimerTask, long, long)}.
     */
    public static <K, V> TimerTask checkMinIdle(
            final KeyedObjectPool<K, V> keyedPool, final K key,
            final int minIdle, final long periodMillis)
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
        getMinIdleTimer().schedule(task, 0L, periodMillis);
        return task;
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
     * @param periodMillis
     *            the frequency in milliseconds to check the number of idle objects in a pool,
     *            see {@link Timer#schedule(TimerTask, long, long)}.
     * @param <T> the type of objects in the pool
     *
     * @return the {@link TimerTask} that will periodically check the pools idle
     *         object count.
     * @throws IllegalArgumentException
     *             when {@code pool} is {@code null} or when {@code minIdle} is
     *             negative or when {@code period} isn't valid for
     *             {@link Timer#schedule(TimerTask, long, long)}
     */
    public static <T> TimerTask checkMinIdle(final ObjectPool<T> pool,
            final int minIdle, final long periodMillis)
            throws IllegalArgumentException {
        if (pool == null) {
            throw new IllegalArgumentException(MSG_NULL_KEYED_POOL);
        }
        if (minIdle < 0) {
            throw new IllegalArgumentException(MSG_MIN_IDLE);
        }
        final TimerTask task = new ObjectPoolMinIdleTimerTask<>(pool, minIdle);
        getMinIdleTimer().schedule(task, 0L, periodMillis);
        return task;
    }

    /**
     * Should the supplied Throwable be re-thrown (eg if it is an instance of
     * one of the Throwables that should never be swallowed). Used by the pool
     * error handling for operations that throw exceptions that normally need to
     * be ignored.
     *
     * @param t
     *            The Throwable to check
     * @throws VirtualMachineError
     *             if that is passed in
     */
    public static void checkRethrow(final Throwable t) {
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }

    /**
     * Returns a pool that adaptively decreases its size when idle objects are
     * no longer needed. This is intended as an always thread-safe alternative
     * to using an idle object evictor provided by many pool implementations.
     * This is also an effective way to shrink FIFO ordered pools that
     * experience load spikes.
     *
     * @param keyedPool
     *            the KeyedObjectPool to be decorated so it shrinks its idle
     *            count when possible.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     *
     * @throws IllegalArgumentException
     *             when {@code keyedPool} is {@code null}.
     * @return a pool that adaptively decreases its size when idle objects are
     *         no longer needed.
     * @see #erodingPool(KeyedObjectPool, float)
     * @see #erodingPool(KeyedObjectPool, float, boolean)
     */
    public static <K, V> KeyedObjectPool<K, V> erodingPool(final KeyedObjectPool<K, V> keyedPool) {
        return erodingPool(keyedPool, 1f);
    }

    /**
     * Returns a pool that adaptively decreases its size when idle objects are
     * no longer needed. This is intended as an always thread-safe alternative
     * to using an idle object evictor provided by many pool implementations.
     * This is also an effective way to shrink FIFO ordered pools that
     * experience load spikes.
     * <p>
     * The factor parameter provides a mechanism to tweak the rate at which the
     * pool tries to shrink its size. Values between 0 and 1 cause the pool to
     * try to shrink its size more often. Values greater than 1 cause the pool
     * to less frequently try to shrink its size.
     * </p>
     *
     * @param keyedPool
     *            the KeyedObjectPool to be decorated so it shrinks its idle
     *            count when possible.
     * @param factor
     *            a positive value to scale the rate at which the pool tries to
     *            reduce its size. If 0 &lt; factor &lt; 1 then the pool
     *            shrinks more aggressively. If 1 &lt; factor then the pool
     *            shrinks less aggressively.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     *
     * @throws IllegalArgumentException
     *             when {@code keyedPool} is {@code null} or when {@code factor}
     *             is not positive.
     * @return a pool that adaptively decreases its size when idle objects are
     *         no longer needed.
     * @see #erodingPool(KeyedObjectPool, float, boolean)
     */
    public static <K, V> KeyedObjectPool<K, V> erodingPool(final KeyedObjectPool<K, V> keyedPool, final float factor) {
        return erodingPool(keyedPool, factor, false);
    }

    /**
     * Returns a pool that adaptively decreases its size when idle objects are
     * no longer needed. This is intended as an always thread-safe alternative
     * to using an idle object evictor provided by many pool implementations.
     * This is also an effective way to shrink FIFO ordered pools that
     * experience load spikes.
     * <p>
     * The factor parameter provides a mechanism to tweak the rate at which the
     * pool tries to shrink its size. Values between 0 and 1 cause the pool to
     * try to shrink its size more often. Values greater than 1 cause the pool
     * to less frequently try to shrink its size.
     * </p>
     * <p>
     * The perKey parameter determines if the pool shrinks on a whole pool basis
     * or a per key basis. When perKey is false, the keys do not have an effect
     * on the rate at which the pool tries to shrink its size. When perKey is
     * true, each key is shrunk independently.
     * </p>
     *
     * @param keyedPool
     *            the KeyedObjectPool to be decorated so it shrinks its idle
     *            count when possible.
     * @param factor
     *            a positive value to scale the rate at which the pool tries to
     *            reduce its size. If 0 &lt; factor &lt; 1 then the pool
     *            shrinks more aggressively. If 1 &lt; factor then the pool
     *            shrinks less aggressively.
     * @param perKey
     *            when true, each key is treated independently.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     *
     * @throws IllegalArgumentException
     *             when {@code keyedPool} is {@code null} or when {@code factor}
     *             is not positive.
     * @return a pool that adaptively decreases its size when idle objects are
     *         no longer needed.
     * @see #erodingPool(KeyedObjectPool)
     * @see #erodingPool(KeyedObjectPool, float)
     */
    public static <K, V> KeyedObjectPool<K, V> erodingPool(
            final KeyedObjectPool<K, V> keyedPool, final float factor,
            final boolean perKey) {
        if (keyedPool == null) {
            throw new IllegalArgumentException(MSG_NULL_KEYED_POOL);
        }
        if (factor <= 0f) {
            throw new IllegalArgumentException(MSG_FACTOR_NEGATIVE);
        }
        if (perKey) {
            return new ErodingPerKeyKeyedObjectPool<>(keyedPool, factor);
        }
        return new ErodingKeyedObjectPool<>(keyedPool, factor);
    }

    /**
     * Returns a pool that adaptively decreases its size when idle objects are
     * no longer needed. This is intended as an always thread-safe alternative
     * to using an idle object evictor provided by many pool implementations.
     * This is also an effective way to shrink FIFO ordered pools that
     * experience load spikes.
     *
     * @param pool
     *            the ObjectPool to be decorated so it shrinks its idle count
     *            when possible.
     * @param <T> the type of objects in the pool
     *
     * @throws IllegalArgumentException
     *             when {@code pool} is {@code null}.
     * @return a pool that adaptively decreases its size when idle objects are
     *         no longer needed.
     * @see #erodingPool(ObjectPool, float)
     */
    public static <T> ObjectPool<T> erodingPool(final ObjectPool<T> pool) {
        return erodingPool(pool, 1f);
    }

    /**
     * Returns a pool that adaptively decreases its size when idle objects are
     * no longer needed. This is intended as an always thread-safe alternative
     * to using an idle object evictor provided by many pool implementations.
     * This is also an effective way to shrink FIFO ordered pools that
     * experience load spikes.
     * <p>
     * The factor parameter provides a mechanism to tweak the rate at which the
     * pool tries to shrink its size. Values between 0 and 1 cause the pool to
     * try to shrink its size more often. Values greater than 1 cause the pool
     * to less frequently try to shrink its size.
     * </p>
     *
     * @param pool
     *            the ObjectPool to be decorated so it shrinks its idle count
     *            when possible.
     * @param factor
     *            a positive value to scale the rate at which the pool tries to
     *            reduce its size. If 0 &lt; factor &lt; 1 then the pool
     *            shrinks more aggressively. If 1 &lt; factor then the pool
     *            shrinks less aggressively.
     * @param <T> the type of objects in the pool
     *
     * @throws IllegalArgumentException
     *             when {@code pool} is {@code null} or when {@code factor} is
     *             not positive.
     * @return a pool that adaptively decreases its size when idle objects are
     *         no longer needed.
     * @see #erodingPool(ObjectPool)
     */
    public static <T> ObjectPool<T> erodingPool(final ObjectPool<T> pool, final float factor) {
        if (pool == null) {
            throw new IllegalArgumentException(MSG_NULL_POOL);
        }
        if (factor <= 0f) {
            throw new IllegalArgumentException(MSG_FACTOR_NEGATIVE);
        }
        return new ErodingObjectPool<>(pool, factor);
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
     *
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
     *
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
     * Calls {@link ObjectPool#addObject()} on {@code pool} {@code count} number
     * of times.
     *
     * @param pool
     *            the pool to prefill.
     * @param count
     *            the number of idle objects to add.
     * @param <T> the type of objects in the pool
     *
     * @throws Exception
     *             when {@link ObjectPool#addObject()} fails.
     * @throws IllegalArgumentException
     *             when {@code pool} is {@code null}.
     * @deprecated Use {@link ObjectPool#addObjects(int)}.
     */
    @Deprecated
    public static <T> void prefill(final ObjectPool<T> pool, final int count)
            throws Exception {
        if (pool == null) {
            throw new IllegalArgumentException(MSG_NULL_POOL);
        }
        pool.addObjects(count);
    }

    /**
     * Returns a synchronized (thread-safe) KeyedPooledObjectFactory backed by
     * the specified KeyedPooledObjectFactory.
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
     * Returns a synchronized (thread-safe) KeyedObjectPool backed by the
     * specified KeyedObjectPool.
     * <p>
     * <b>Note:</b> This should not be used on pool implementations that already
     * provide proper synchronization such as the pools provided in the Commons
     * Pool library. Wrapping a pool that {@link #wait() waits} for poolable
     * objects to be returned before allowing another one to be borrowed with
     * another layer of synchronization will cause liveliness issues or a
     * deadlock.
     * </p>
     *
     * @param keyedPool
     *            the KeyedObjectPool to be "wrapped" in a synchronized
     *            KeyedObjectPool.
     * @param <K> the type of the pool key
     * @param <V> the type of pool entries
     *
     * @return a synchronized view of the specified KeyedObjectPool.
     */
    public static <K, V> KeyedObjectPool<K, V> synchronizedPool(final KeyedObjectPool<K, V> keyedPool) {
        /*
         * assert !(keyedPool instanceof GenericKeyedObjectPool) :
         * "GenericKeyedObjectPool is already thread-safe"; assert !(keyedPool
         * instanceof StackKeyedObjectPool) :
         * "StackKeyedObjectPool is already thread-safe"; assert
         * !"org.apache.commons.pool.composite.CompositeKeyedObjectPool"
         * .equals(keyedPool.getClass().getName()) :
         * "CompositeKeyedObjectPools are already thread-safe";
         */
        return new SynchronizedKeyedObjectPool<>(keyedPool);
    }

    /**
     * Returns a synchronized (thread-safe) ObjectPool backed by the specified
     * ObjectPool.
     * <p>
     * <b>Note:</b> This should not be used on pool implementations that already
     * provide proper synchronization such as the pools provided in the Commons
     * Pool library. Wrapping a pool that {@link #wait() waits} for poolable
     * objects to be returned before allowing another one to be borrowed with
     * another layer of synchronization will cause liveliness issues or a
     * deadlock.
     * </p>
     *
     * @param <T> the type of objects in the pool
     * @param pool
     *            the ObjectPool to be "wrapped" in a synchronized ObjectPool.
     * @throws IllegalArgumentException
     *             when {@code pool} is {@code null}.
     * @return a synchronized view of the specified ObjectPool.
     */
    public static <T> ObjectPool<T> synchronizedPool(final ObjectPool<T> pool) {
        if (pool == null) {
            throw new IllegalArgumentException(MSG_NULL_POOL);
        }

        /*
         * assert !(pool instanceof GenericObjectPool) :
         * "GenericObjectPool is already thread-safe"; assert !(pool instanceof
         * SoftReferenceObjectPool) :
         * "SoftReferenceObjectPool is already thread-safe"; assert !(pool
         * instanceof StackObjectPool) :
         * "StackObjectPool is already thread-safe"; assert
         * !"org.apache.commons.pool.composite.CompositeObjectPool"
         * .equals(pool.getClass().getName()) :
         * "CompositeObjectPools are already thread-safe";
         */
        return new SynchronizedObjectPool<>(pool);
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
    public static <T> PooledObjectFactory<T> synchronizedPooledFactory(final PooledObjectFactory<T> factory) {
        return new SynchronizedPooledObjectFactory<>(factory);
    }

    /**
     * PoolUtils instances should NOT be constructed in standard programming.
     * Instead, the class should be used procedurally: PoolUtils.adapt(aPool);.
     * This constructor is public to permit tools that require a JavaBean
     * instance to operate.
     */
    public PoolUtils() {
    }
}

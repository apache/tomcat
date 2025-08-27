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
package org.apache.tomcat.util.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import org.apache.tomcat.util.res.StringManager;

/**
 * Provides a reentrant read/write lock for a given key. Any locks obtained from an instance of this class using the
 * same key will use the same underlying reentrant read/write lock as long as at least one lock for that key remains in
 * use. Once no locks are in use for the given key, the lock is eligible for GC and the next lock obtained using that key
 * will use a new underlying reentrant read/write lock.
 * <p>
 * The class is used when Tomcat needs to manage concurrent access to components identified by a key (e.g. sessions).
 * <p>
 * The map of keys to locks is maintained so that locks are created as required and removed when no longer used.
 * <p>
 * The locks provided by this class only implement {@code Lock#lock()} and {@code Lock#unlock()}. All other methods will
 * throw {@code UnsupportedOperationException}.
 */
public class KeyedReentrantReadWriteLock {

    private final Map<String,CountedLock> locksMap = new HashMap<>();


    /**
     * Obtain the reentrant read/write lock for the given key.
     *
     * @param key The key for which the lock should be obtained
     *
     * @return A reentrant read/write lock for the given key
     */
    public ReadWriteLock getLock(String key) {
        return new ReadWriteLockImpl(locksMap, key);
    }


    /*
     * Reentrant read/write lock implementation that is passed back to the caller. It provides the lock wrappers that
     * track usage.
     */
    private static class ReadWriteLockImpl implements ReadWriteLock {

        private final Map<String,CountedLock> locksMap;
        private final String key;
        private volatile Lock readLock;
        private volatile Lock writeLock;

        ReadWriteLockImpl(Map<String,CountedLock> locksMap, String key) {
            this.locksMap = locksMap;
            this.key = key;
        }

        @Override
        public Lock readLock() {
            if (readLock == null) {
                readLock = new LockImpl(locksMap, key, ReentrantReadWriteLock::readLock);
            }
            return readLock;
        }

        @Override
        public Lock writeLock() {
            if (writeLock == null) {
                writeLock = new LockImpl(locksMap, key, ReentrantReadWriteLock::writeLock);
            }
            return writeLock;
        }
    }


    /*
     * Lock wrapper implementation that provides both read locks and write locks from the underlying lock and tracks
     * their usage. Most of the methods throw UnsupportedOperationException as Tomcat does not (currently) require
     * implementations of those methods.
     */
    private static class LockImpl implements Lock {

        private static final StringManager sm = StringManager.getManager(LockImpl.class);

        private final Map<String,CountedLock> locksMap;
        private final String key;
        private final Function<ReentrantReadWriteLock,Lock> function;

        LockImpl(Map<String,CountedLock> locksMap, String key, Function<ReentrantReadWriteLock,Lock> function) {
            this.locksMap = locksMap;
            this.key = key;
            this.function = function;
        }

        @Override
        public void lock() {
            CountedLock countedLock = null;
            synchronized (locksMap) {
                // Lookup / create the counted lock for the given key
                countedLock = locksMap.compute(key, (k, v) -> v == null ? new CountedLock() : v);
                // Increment usage count inside the sync block to ensure other threads are aware key is in use.
                countedLock.count.incrementAndGet();
            }
            // Lock outside of the sync block in case the call to lock() blocks.
            function.apply(countedLock.reentrantLock).lock();
        }

        @Override
        public void unlock() {
            CountedLock countedLock = null;
            // Unlocking so a lock should exist in the map for the given key.
            synchronized (locksMap) {
                countedLock = locksMap.get(key);
            }
            if (countedLock == null) {
                throw new IllegalStateException(sm.getString("lockImpl.unlockWithoutLock"));
            }
            // No need to unlock inside sync block, so don't.
            function.apply(countedLock.reentrantLock).unlock();
            synchronized (locksMap) {
                /*
                 * Decrement usage count and check for zero inside the sync block to ensure usage tracking is consistent
                 * across multiple threads.
                 */
                if (countedLock.count.decrementAndGet() == 0) {
                    locksMap.remove(key);
                }
            }
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryLock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }


    /*
     * Holds the underlying reentrant read/write lock and the counter that tracks usage.
     */
    private static class CountedLock {
        AtomicInteger count = new AtomicInteger();
        ReentrantReadWriteLock reentrantLock = new ReentrantReadWriteLock();
    }
}

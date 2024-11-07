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
package org.apache.catalina;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Interface implemented by {@link WebResourceSet} implementations that wish to provide locking functionality.
 */
public interface WebResourceLockSet {

    /**
     * Lock the resource at the provided path for reading. The resource is not required to exist. Read locks are not
     * exclusive.
     *
     * @param path The path to the resource to be locked for reading
     *
     * @return The {@link ResourceLock} that must be passed to {@link #unlockForRead(ResourceLock)} to release the lock
     */
    ResourceLock lockForRead(String path);

    /**
     * Release a read lock from the resource associated with the given {@link ResourceLock}.
     *
     * @param resourceLock The {@link ResourceLock} associated with the resource for which a read lock should be
     *                         released
     */
    void unlockForRead(ResourceLock resourceLock);

    /**
     * Lock the resource at the provided path for writing. The resource is not required to exist. Write locks are
     * exclusive.
     *
     * @param path The path to the resource to be locked for writing
     *
     * @return The {@link ResourceLock} that must be passed to {@link #unlockForWrite(ResourceLock)} to release the lock
     */
    ResourceLock lockForWrite(String path);

    /**
     * Release the write lock from the resource associated with the given {@link ResourceLock}.
     *
     * @param resourceLock The {@link ResourceLock} associated with the resource for which the write lock should be
     *                         released
     */
    void unlockForWrite(ResourceLock resourceLock);


    class ResourceLock {
        public final AtomicInteger count = new AtomicInteger(0);
        public final ReentrantReadWriteLock reentrantLock = new ReentrantReadWriteLock();
        public final String key;

        public ResourceLock(String key) {
            this.key = key;
        }
    }
}

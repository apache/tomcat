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

import java.util.concurrent.locks.ReadWriteLock;

/**
 * Interface implemented by {@link WebResourceSet} implementations that wish to provide locking functionality.
 */
public interface WebResourceLockSet {

    /**
     * Obtain a reentrant read/write lock for the resource at the provided path. The resource is not required to exist.
     * Multiple calls to this method with the same path will return the same lock provided that at least one instance
     * of the lock remains in use between the calls.
     *
     * @param path The path for which the lock should be obtained
     *
     * @return A reentrant read/write lock for the given resource.
     */
    ReadWriteLock getLock(String path);
}

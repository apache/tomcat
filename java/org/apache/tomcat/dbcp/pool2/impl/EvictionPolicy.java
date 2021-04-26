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

import org.apache.tomcat.dbcp.pool2.PooledObject;

/**
 * To provide a custom eviction policy (i.e. something other than {@link
 * DefaultEvictionPolicy} for a pool, users must provide an implementation of
 * this interface that provides the required eviction policy.
 *
 * @param <T> the type of objects in the pool
 *
 * @since 2.0
 */
public interface EvictionPolicy<T> {

    /**
     * This method is called to test if an idle object in the pool should be
     * evicted or not.
     *
     * @param config    The pool configuration settings related to eviction
     * @param underTest The pooled object being tested for eviction
     * @param idleCount The current number of idle objects in the pool including
     *                      the object under test
     * @return {@code true} if the object should be evicted, otherwise
     *             {@code false}
     */
    boolean evict(EvictionConfig config, PooledObject<T> underTest, int idleCount);
}

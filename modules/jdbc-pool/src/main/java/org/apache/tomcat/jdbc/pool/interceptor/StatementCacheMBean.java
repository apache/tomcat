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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * MBean interface for monitoring the statement cache.
 */
public interface StatementCacheMBean {
    /**
     * Checks if prepared statements are being cached.
     * @return true if prepared statement caching is enabled
     */
    boolean isCachePrepared();
    /**
     * Checks if callable statements are being cached.
     * @return true if callable statement caching is enabled
     */
    boolean isCacheCallable();
    /**
     * Returns the maximum size of the statement cache.
     * @return maximum cache size
     */
    int getMaxCacheSize();
    /**
     * Returns the current global cache size across all connections.
     * @return current cache size counter
     */
    AtomicInteger getCacheSize();
    /**
     * Returns the cache size for the current connection.
     * @return number of cached statements for this connection
     */
    int getCacheSizePerConnection();
}

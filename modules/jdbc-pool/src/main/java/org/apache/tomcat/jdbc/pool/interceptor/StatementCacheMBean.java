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
     * Returns the number of statements currently held in the statement
     * cache, counted across all connections that belong to the same
     * connection pool - the value the {@code max} limit is compared
     * against. The number is a snapshot of the pool's accounting at the
     * moment of the read; it is not a handle that can be used to change
     * that accounting.
     * @return the number of cached statements, or {@code -1} if this
     *         interceptor instance is not bound to a pooled connection
     */
    int getCacheSize();
    /**
     * Returns the cache size for the current connection.
     * @return number of cached statements for this connection
     */
    int getCacheSizePerConnection();
}

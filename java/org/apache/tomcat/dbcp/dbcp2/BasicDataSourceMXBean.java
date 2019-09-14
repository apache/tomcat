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
package org.apache.tomcat.dbcp.dbcp2;

/**
 * Defines the methods that will be made available via JMX.
 *
 * @since 2.0
 */
public interface BasicDataSourceMXBean {

    /**
     * @return {@link BasicDataSource#getAbandonedUsageTracking()}
     */
    boolean getAbandonedUsageTracking();

    /**
     * @return {@link BasicDataSource#getDefaultAutoCommit()}
     */
    Boolean getDefaultAutoCommit();

    /**
     * @return {@link BasicDataSource#getDefaultReadOnly()}
     */
    Boolean getDefaultReadOnly();

    /**
     * @return {@link BasicDataSource#getDefaultTransactionIsolation()}
     */
    int getDefaultTransactionIsolation();

    /**
     * @return {@link BasicDataSource#getDefaultCatalog()}
     */
    String getDefaultCatalog();

    /**
     * @return {@link BasicDataSource#getDefaultSchema()}
     * @since 2.5.0
     */
    default String getDefaultSchema() {
        return null;
    }

    /**
     * @return {@link BasicDataSource#getCacheState()}
     */
    boolean getCacheState();

    /**
     * @return {@link BasicDataSource#getDriverClassName()}
     */
    String getDriverClassName();

    /**
     * @return {@link BasicDataSource#getLifo()}
     */
    boolean getLifo();

    /**
     * @return {@link BasicDataSource#getMaxTotal()}
     */
    int getMaxTotal();

    /**
     * @return {@link BasicDataSource#getMaxIdle()}
     */
    int getMaxIdle();

    /**
     * @return {@link BasicDataSource#getMinIdle()}
     */
    int getMinIdle();

    /**
     * @return {@link BasicDataSource#getInitialSize()}
     */
    int getInitialSize();

    /**
     * @return {@link BasicDataSource#getMaxWaitMillis()}
     */
    long getMaxWaitMillis();

    /**
     * @return {@link BasicDataSource#isPoolPreparedStatements()}
     */
    boolean isPoolPreparedStatements();

    /**
     * @return {@link BasicDataSource#getMaxOpenPreparedStatements()}
     */
    int getMaxOpenPreparedStatements();

    /**
     * @return {@link BasicDataSource#getTestOnCreate()}
     */
    boolean getTestOnCreate();

    /**
     * @return {@link BasicDataSource#getTestOnBorrow()}
     */
    boolean getTestOnBorrow();

    /**
     * @return {@link BasicDataSource#getTimeBetweenEvictionRunsMillis()}
     */
    long getTimeBetweenEvictionRunsMillis();

    /**
     * @return {@link BasicDataSource#getNumTestsPerEvictionRun()}
     */
    int getNumTestsPerEvictionRun();

    /**
     * @return {@link BasicDataSource#getMinEvictableIdleTimeMillis()}
     */
    long getMinEvictableIdleTimeMillis();

    /**
     * @return {@link BasicDataSource#getSoftMinEvictableIdleTimeMillis()}
     */
    long getSoftMinEvictableIdleTimeMillis();

    /**
     * @return {@link BasicDataSource#getTestWhileIdle()}
     */
    boolean getTestWhileIdle();

    /**
     * @return {@link BasicDataSource#getNumActive()}
     */
    int getNumActive();

    /**
     * @return {@link BasicDataSource#getNumIdle()}
     */
    int getNumIdle();

    /**
     * @return {@link BasicDataSource#getPassword()}
     */
    String getPassword();

    /**
     * @return {@link BasicDataSource#getUrl()}
     */
    String getUrl();

    /**
     * @return {@link BasicDataSource#getUsername()}
     */
    String getUsername();

    /**
     * @return {@link BasicDataSource#getValidationQuery()}
     */
    String getValidationQuery();

    /**
     * @return {@link BasicDataSource#getValidationQueryTimeout()}
     */
    int getValidationQueryTimeout();

    /**
     * @return {@link BasicDataSource#getConnectionInitSqlsAsArray()}
     */
    String[] getConnectionInitSqlsAsArray();

    /**
     * @return {@link BasicDataSource#isAccessToUnderlyingConnectionAllowed()}
     */
    boolean isAccessToUnderlyingConnectionAllowed();

    /**
     * @return {@link BasicDataSource#getMaxConnLifetimeMillis()}
     */
    long getMaxConnLifetimeMillis();

    /**
     * @return {@link BasicDataSource#getLogExpiredConnections()}
     * @since 2.1
     */
    boolean getLogExpiredConnections();

    /**
     * @return {@link BasicDataSource#getRemoveAbandonedOnBorrow()}
     */
    boolean getRemoveAbandonedOnBorrow();

    /**
     * @return {@link BasicDataSource#getRemoveAbandonedOnMaintenance()}
     */
    boolean getRemoveAbandonedOnMaintenance();

    /**
     * @return {@link BasicDataSource#getRemoveAbandonedTimeout()}
     */
    int getRemoveAbandonedTimeout();

    /**
     * @return {@link BasicDataSource#getLogAbandoned()}
     */
    boolean getLogAbandoned();

    /**
     * @return {@link BasicDataSource#isClosed()}
     */
    boolean isClosed();

    /**
     * @return {@link BasicDataSource#getFastFailValidation()}
     * @since 2.1
     */
    boolean getFastFailValidation();

    /**
     * @return {@link BasicDataSource#getDisconnectionSqlCodesAsArray()}
     * @since 2.1
     */
    String[] getDisconnectionSqlCodesAsArray();
}

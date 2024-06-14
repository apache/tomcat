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

import java.sql.SQLException;

/**
 * Defines the methods that will be made available via
 * <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/management/agent.html">JMX</a>.
 *
 * @since 2.9.0
 */
public interface DataSourceMXBean {

    /**
     * See {@link BasicDataSource#getAbandonedUsageTracking()}.
     *
     * @return {@link BasicDataSource#getAbandonedUsageTracking()}
     */
    boolean getAbandonedUsageTracking();

    /**
     * See {@link BasicDataSource#getCacheState()}.
     *
     * @return {@link BasicDataSource#getCacheState()}.
     */
    boolean getCacheState();

    /**
     * See {@link BasicDataSource#getConnectionInitSqlsAsArray()}.
     *
     * @return {@link BasicDataSource#getConnectionInitSqlsAsArray()}.
     */
    String[] getConnectionInitSqlsAsArray();

    /**
     * See {@link BasicDataSource#getDefaultAutoCommit()}.
     *
     * @return {@link BasicDataSource#getDefaultAutoCommit()}.
     */
    Boolean getDefaultAutoCommit();

    /**
     * See {@link BasicDataSource#getDefaultCatalog()}.
     *
     * @return {@link BasicDataSource#getDefaultCatalog()}.
     */
    String getDefaultCatalog();

    /**
     * See {@link BasicDataSource#getDefaultReadOnly()}.
     *
     * @return {@link BasicDataSource#getDefaultReadOnly()}.
     */
    Boolean getDefaultReadOnly();

    /**
     * See {@link BasicDataSource#getDefaultSchema()}.
     *
     * @return {@link BasicDataSource#getDefaultSchema()}.
     * @since 2.5.0
     */
    default String getDefaultSchema() {
        return null;
    }

    /**
     * See {@link BasicDataSource#getDefaultTransactionIsolation()}.
     *
     * @return {@link BasicDataSource#getDefaultTransactionIsolation()}.
     */
    int getDefaultTransactionIsolation();

    /**
     * See {@link BasicDataSource#getDisconnectionSqlCodesAsArray()}.
     *
     * @return {@link BasicDataSource#getDisconnectionSqlCodesAsArray()}.
     * @since 2.1
     */
    String[] getDisconnectionSqlCodesAsArray();

    /**
     * See {@link BasicDataSource#getDriverClassName()}.
     *
     * @return {@link BasicDataSource#getDriverClassName()}.
     */
    String getDriverClassName();

    /**
     * See {@link BasicDataSource#getFastFailValidation()}.
     *
     * @return {@link BasicDataSource#getFastFailValidation()}.
     * @since 2.1
     */
    boolean getFastFailValidation();

    /**
     * See {@link BasicDataSource#getInitialSize()}.
     *
     * @return {@link BasicDataSource#getInitialSize()}.
     */
    int getInitialSize();

    /**
     * See {@link BasicDataSource#getLifo()}.
     *
     * @return {@link BasicDataSource#getLifo()}.
     */
    boolean getLifo();

    /**
     * See {@link BasicDataSource#getLogAbandoned()}.
     *
     * @return {@link BasicDataSource#getLogAbandoned()}.
     */
    boolean getLogAbandoned();

    /**
     * See {@link BasicDataSource#getLogExpiredConnections()}.
     *
     * @return {@link BasicDataSource#getLogExpiredConnections()}.
     * @since 2.1
     */
    boolean getLogExpiredConnections();

    /**
     * See {@link BasicDataSource#getMaxConnLifetimeMillis()}.
     *
     * @return {@link BasicDataSource#getMaxConnLifetimeMillis()}.
     */
    @SuppressWarnings("javadoc")
    long getMaxConnLifetimeMillis();

    /**
     * See {@link BasicDataSource#getMaxIdle()}.
     *
     * @return {@link BasicDataSource#getMaxIdle()}.
     */
    int getMaxIdle();

    /**
     * See {@link BasicDataSource#getMaxOpenPreparedStatements()}.
     *
     * @return {@link BasicDataSource#getMaxOpenPreparedStatements()}.
     */
    int getMaxOpenPreparedStatements();

    /**
     * See {@link BasicDataSource#getMaxTotal()}.
     *
     * @return {@link BasicDataSource#getMaxTotal()}.
     */
    int getMaxTotal();

    /**
     * See {@link BasicDataSource#getMaxWaitMillis()}.
     *
     * @return {@link BasicDataSource#getMaxWaitMillis()}.
     */
    @SuppressWarnings("javadoc")
    long getMaxWaitMillis();

    /**
     * See {@link BasicDataSource#getMinEvictableIdleTimeMillis()}.
     *
     * @return {@link BasicDataSource#getMinEvictableIdleTimeMillis()}.
     */
    @SuppressWarnings("javadoc")
    long getMinEvictableIdleTimeMillis();

    /**
     * See {@link BasicDataSource#getMinIdle()}.
     *
     * @return {@link BasicDataSource#getMinIdle()}.
     */
    int getMinIdle();

    /**
     * See {@link BasicDataSource#getNumActive()}.
     *
     * @return {@link BasicDataSource#getNumActive()}.
     */
    int getNumActive();

    /**
     * See {@link BasicDataSource#getNumIdle()}.
     *
     * @return {@link BasicDataSource#getNumIdle()}.
     */
    int getNumIdle();

    /**
     * See {@link BasicDataSource#getNumTestsPerEvictionRun()}.
     *
     * @return {@link BasicDataSource#getNumTestsPerEvictionRun()}.
     */
    int getNumTestsPerEvictionRun();

    /**
     * See {@link BasicDataSource#getRemoveAbandonedOnBorrow()}.
     *
     * @return {@link BasicDataSource#getRemoveAbandonedOnBorrow()}.
     */
    boolean getRemoveAbandonedOnBorrow();

    /**
     * See {@link BasicDataSource#getRemoveAbandonedOnMaintenance()}.
     *
     * @return {@link BasicDataSource#getRemoveAbandonedOnMaintenance()}.
     */
    boolean getRemoveAbandonedOnMaintenance();

    /**
     * See {@link BasicDataSource#getRemoveAbandonedTimeout()}.
     *
     * @return {@link BasicDataSource#getRemoveAbandonedTimeout()}.
     */
    @SuppressWarnings("javadoc")
    int getRemoveAbandonedTimeout();

    /**
     * See {@link BasicDataSource#getSoftMinEvictableIdleTimeMillis()}.
     *
     * @return {@link BasicDataSource#getSoftMinEvictableIdleTimeMillis()}.
     */
    @SuppressWarnings("javadoc")
    long getSoftMinEvictableIdleTimeMillis();

    /**
     * See {@link BasicDataSource#getTestOnBorrow()}.
     *
     * @return {@link BasicDataSource#getTestOnBorrow()}.
     */
    boolean getTestOnBorrow();

    /**
     * See {@link BasicDataSource#getTestOnCreate()}.
     *
     * @return {@link BasicDataSource#getTestOnCreate()}.
     */
    boolean getTestOnCreate();

    /**
     * See {@link BasicDataSource#getTestWhileIdle()}.
     *
     * @return {@link BasicDataSource#getTestWhileIdle()}.
     */
    boolean getTestWhileIdle();

    /**
     * See {@link BasicDataSource#getTimeBetweenEvictionRunsMillis()}.
     *
     * @return {@link BasicDataSource#getTimeBetweenEvictionRunsMillis()}.
     */
    @SuppressWarnings("javadoc")
    long getTimeBetweenEvictionRunsMillis();

    /**
     * See {@link BasicDataSource#getUrl()}.
     *
     * @return {@link BasicDataSource#getUrl()}.
     */
    String getUrl();

    /**
     * See {@link BasicDataSource#getUsername()}.
     *
     * @return {@link BasicDataSource#getUsername()}.
     * @deprecated Use {@link #getUserName()}.
     */
    @Deprecated
    String getUsername();

    /**
     * See {@link BasicDataSource#getUsername()}.
     *
     * @return {@link BasicDataSource#getUsername()}.
     * @since 2.11.0
     */
    @SuppressWarnings("javadoc")
    default String getUserName() {
        return getUsername();
    }

    /**
     * See {@link BasicDataSource#getValidationQuery()}.
     *
     * @return {@link BasicDataSource#getValidationQuery()}.
     */
    String getValidationQuery();

    /**
     * See {@link BasicDataSource#getValidationQueryTimeout()}.
     *
     * @return {@link BasicDataSource#getValidationQueryTimeout()}.
     */
    @SuppressWarnings("javadoc")
    int getValidationQueryTimeout();

    /**
     * See {@link BasicDataSource#isAccessToUnderlyingConnectionAllowed()}.
     *
     * @return {@link BasicDataSource#isAccessToUnderlyingConnectionAllowed()}.
     */
    boolean isAccessToUnderlyingConnectionAllowed();

    /**
     * See {@link BasicDataSource#isClearStatementPoolOnReturn()}.
     *
     * @return {@link BasicDataSource#isClearStatementPoolOnReturn()}.
     * @since 2.8.0
     */
    default boolean isClearStatementPoolOnReturn() {
        return false;
    }

    /**
     * See {@link BasicDataSource#isClosed()}.
     *
     * @return {@link BasicDataSource#isClosed()}.
     */
    boolean isClosed();

    /**
     * See {@link BasicDataSource#isPoolPreparedStatements()}.
     *
     * @return {@link BasicDataSource#isPoolPreparedStatements()}.
     */
    boolean isPoolPreparedStatements();

    /**
     * See {@link BasicDataSource#restart()}
     *
     * @throws SQLException if an error occurs initializing the data source.
     *
     * @since 2.8.0
     */
    default void restart() throws SQLException {
        // do nothing by default?
    }

    /**
     * See {@link BasicDataSource#start()}
     *
     * @throws SQLException if an error occurs initializing the data source.
     *
     * @since 2.8.0
     */
    default void start() throws SQLException {
        // do nothing
    }
}

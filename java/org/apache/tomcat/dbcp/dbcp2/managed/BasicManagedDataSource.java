/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.dbcp.dbcp2.managed;

import java.sql.SQLException;

import javax.sql.DataSource;
import javax.sql.XADataSource;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.apache.tomcat.dbcp.dbcp2.ConnectionFactory;
import org.apache.tomcat.dbcp.dbcp2.PoolableConnection;
import org.apache.tomcat.dbcp.dbcp2.PoolableConnectionFactory;
import org.apache.tomcat.dbcp.dbcp2.PoolingDataSource;
import org.apache.tomcat.dbcp.dbcp2.Utils;

/**
 * <p>
 * BasicManagedDataSource is an extension of BasicDataSource which creates ManagedConnections. This data source can
 * create either full two-phase-commit XA connections or one-phase-commit local connections. Both types of connections
 * are committed or rolled back as part of the global transaction (a.k.a. XA transaction or JTA Transaction), but only
 * XA connections can be recovered in the case of a system crash.
 * </p>
 * <p>
 * BasicManagedDataSource adds the TransactionManager and XADataSource properties. The TransactionManager property is
 * required and is used to enlist connections in global transactions. The XADataSource is optional and if set is the
 * class name of the XADataSource class for a two-phase-commit JDBC driver. If the XADataSource property is set, the
 * driverClassName is ignored and a DataSourceXAConnectionFactory is created. Otherwise, a standard
 * DriverConnectionFactory is created and wrapped with a LocalXAConnectionFactory.
 * </p>
 *
 * @see BasicDataSource
 * @see ManagedConnection
 * @since 2.0
 */
public class BasicManagedDataSource extends BasicDataSource {

    /** Transaction Registry */
    private TransactionRegistry transactionRegistry;

    /** Transaction Manager */
    private transient TransactionManager transactionManager;

    /** XA data source class name */
    private String xaDataSource;

    /** XA data source instance */
    private XADataSource xaDataSourceInstance;

    /** Transaction Synchronization Registry */
    private transient TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    /**
     * Gets the XADataSource instance used by the XAConnectionFactory.
     *
     * @return the XADataSource
     */
    public synchronized XADataSource getXaDataSourceInstance() {
        return xaDataSourceInstance;
    }

    /**
     * <p>
     * Sets the XADataSource instance used by the XAConnectionFactory.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param xaDataSourceInstance
     *            XADataSource instance
     */
    public synchronized void setXaDataSourceInstance(final XADataSource xaDataSourceInstance) {
        this.xaDataSourceInstance = xaDataSourceInstance;
        xaDataSource = xaDataSourceInstance == null ? null : xaDataSourceInstance.getClass().getName();
    }

    /**
     * Gets the required transaction manager property.
     *
     * @return the transaction manager used to enlist connections
     */
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * Gets the optional TransactionSynchronizationRegistry.
     *
     * @return the TSR that can be used to register synchronizations.
     * @since 2.6.0
     */
    public TransactionSynchronizationRegistry getTransactionSynchronizationRegistry() {
        return transactionSynchronizationRegistry;
    }

    /**
     * Gets the transaction registry.
     *
     * @return the transaction registry associating XAResources with managed connections
     */
    protected synchronized TransactionRegistry getTransactionRegistry() {
        return transactionRegistry;
    }

    /**
     * Sets the required transaction manager property.
     *
     * @param transactionManager
     *            the transaction manager used to enlist connections
     */
    public void setTransactionManager(final TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Sets the optional TransactionSynchronizationRegistry property.
     *
     * @param transactionSynchronizationRegistry
     *            the TSR used to register synchronizations
     * @since 2.6.0
     */
    public void setTransactionSynchronizationRegistry(
            final TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
    }

    /**
     * Gets the optional XADataSource class name.
     *
     * @return the optional XADataSource class name
     */
    public synchronized String getXADataSource() {
        return xaDataSource;
    }

    /**
     * Sets the optional XADataSource class name.
     *
     * @param xaDataSource
     *            the optional XADataSource class name
     */
    public synchronized void setXADataSource(final String xaDataSource) {
        this.xaDataSource = xaDataSource;
    }

    @Override
    protected ConnectionFactory createConnectionFactory() throws SQLException {
        if (transactionManager == null) {
            throw new SQLException("Transaction manager must be set before a connection can be created");
        }

        // If xa data source is not specified a DriverConnectionFactory is created and wrapped with a
        // LocalXAConnectionFactory
        if (xaDataSource == null) {
            final ConnectionFactory connectionFactory = super.createConnectionFactory();
            final XAConnectionFactory xaConnectionFactory = new LocalXAConnectionFactory(getTransactionManager(),
                    getTransactionSynchronizationRegistry(), connectionFactory);
            transactionRegistry = xaConnectionFactory.getTransactionRegistry();
            return xaConnectionFactory;
        }

        // Create the XADataSource instance using the configured class name if it has not been set
        if (xaDataSourceInstance == null) {
            Class<?> xaDataSourceClass = null;
            try {
                xaDataSourceClass = Class.forName(xaDataSource);
            } catch (final Exception t) {
                final String message = "Cannot load XA data source class '" + xaDataSource + "'";
                throw new SQLException(message, t);
            }

            try {
                xaDataSourceInstance = (XADataSource) xaDataSourceClass.getConstructor().newInstance();
            } catch (final Exception t) {
                final String message = "Cannot create XA data source of class '" + xaDataSource + "'";
                throw new SQLException(message, t);
            }
        }

        // finally, create the XAConnectionFactory using the XA data source
        final XAConnectionFactory xaConnectionFactory = new DataSourceXAConnectionFactory(getTransactionManager(),
                xaDataSourceInstance, getUsername(), Utils.toCharArray(getPassword()), getTransactionSynchronizationRegistry());
        transactionRegistry = xaConnectionFactory.getTransactionRegistry();
        return xaConnectionFactory;
    }

    @Override
    protected DataSource createDataSourceInstance() throws SQLException {
        final PoolingDataSource<PoolableConnection> pds = new ManagedDataSource<>(getConnectionPool(),
                transactionRegistry);
        pds.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
        return pds;
    }

    /**
     * Creates the PoolableConnectionFactory and attaches it to the connection pool.
     *
     * @param driverConnectionFactory
     *            JDBC connection factory created by {@link #createConnectionFactory()}
     * @throws SQLException
     *             if an error occurs creating the PoolableConnectionFactory
     */
    @Override
    protected PoolableConnectionFactory createPoolableConnectionFactory(final ConnectionFactory driverConnectionFactory)
            throws SQLException {
        PoolableConnectionFactory connectionFactory = null;
        try {
            connectionFactory = new PoolableManagedConnectionFactory((XAConnectionFactory) driverConnectionFactory,
                    getRegisteredJmxName());
            connectionFactory.setValidationQuery(getValidationQuery());
            connectionFactory.setValidationQueryTimeout(getValidationQueryTimeout());
            connectionFactory.setConnectionInitSql(getConnectionInitSqls());
            connectionFactory.setDefaultReadOnly(getDefaultReadOnly());
            connectionFactory.setDefaultAutoCommit(getDefaultAutoCommit());
            connectionFactory.setDefaultTransactionIsolation(getDefaultTransactionIsolation());
            connectionFactory.setDefaultCatalog(getDefaultCatalog());
            connectionFactory.setDefaultSchema(getDefaultSchema());
            connectionFactory.setCacheState(getCacheState());
            connectionFactory.setPoolStatements(isPoolPreparedStatements());
            connectionFactory.setClearStatementPoolOnReturn(isClearStatementPoolOnReturn());
            connectionFactory.setMaxOpenPreparedStatements(getMaxOpenPreparedStatements());
            connectionFactory.setMaxConnLifetimeMillis(getMaxConnLifetimeMillis());
            connectionFactory.setRollbackOnReturn(getRollbackOnReturn());
            connectionFactory.setAutoCommitOnReturn(getAutoCommitOnReturn());
            connectionFactory.setDefaultQueryTimeout(getDefaultQueryTimeout());
            connectionFactory.setFastFailValidation(getFastFailValidation());
            connectionFactory.setDisconnectionSqlCodes(getDisconnectionSqlCodes());
            validateConnectionFactory(connectionFactory);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Cannot create PoolableConnectionFactory (" + e.getMessage() + ")", e);
        }
        return connectionFactory;
    }
}

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
package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.Properties;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Referenceable;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.dbcp2.Utils;
import org.apache.tomcat.dbcp.pool2.impl.BaseObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool;

/**
 * <p>
 * The base class for {@code SharedPoolDataSource} and {@code PerUserPoolDataSource}. Many of the
 * configuration properties are shared and defined here. This class is declared public in order to allow particular
 * usage with commons-beanutils; do not make direct use of it outside of <em>commons-dbcp2</em>.
 * </p>
 *
 * <p>
 * A J2EE container will normally provide some method of initializing the {@code DataSource} whose attributes are
 * presented as bean getters/setters and then deploying it via JNDI. It is then available to an application as a source
 * of pooled logical connections to the database. The pool needs a source of physical connections. This source is in the
 * form of a {@code ConnectionPoolDataSource} that can be specified via the {@link #setDataSourceName(String)} used
 * to lookup the source via JNDI.
 * </p>
 *
 * <p>
 * Although normally used within a JNDI environment, A DataSource can be instantiated and initialized as any bean. In
 * this case the {@code ConnectionPoolDataSource} will likely be instantiated in a similar manner. This class
 * allows the physical source of connections to be attached directly to this pool using the
 * {@link #setConnectionPoolDataSource(ConnectionPoolDataSource)} method.
 * </p>
 *
 * <p>
 * The dbcp package contains an adapter, {@link org.apache.tomcat.dbcp.dbcp2.cpdsadapter.DriverAdapterCPDS}, that can be
 * used to allow the use of {@code DataSource}'s based on this class with JDBC driver implementations that do not
 * supply a {@code ConnectionPoolDataSource}, but still provide a {@link java.sql.Driver} implementation.
 * </p>
 *
 * <p>
 * The <a href="package-summary.html">package documentation</a> contains an example using Apache Tomcat and JNDI and it
 * also contains a non-JNDI example.
 * </p>
 *
 * @since 2.0
 */
public abstract class InstanceKeyDataSource implements DataSource, Referenceable, Serializable, AutoCloseable {

    private static final long serialVersionUID = -6819270431752240878L;

    private static final String GET_CONNECTION_CALLED = "A Connection was already requested from this source, "
            + "further initialization is not allowed.";
    private static final String BAD_TRANSACTION_ISOLATION = "The requested TransactionIsolation level is invalid.";

    /**
     * Internal constant to indicate the level is not set.
     */
    protected static final int UNKNOWN_TRANSACTIONISOLATION = -1;

    /** Guards property setters - once true, setters throw IllegalStateException */
    private volatile boolean getConnectionCalled;

    /** Underlying source of PooledConnections */
    private ConnectionPoolDataSource dataSource;

    /** DataSource Name used to find the ConnectionPoolDataSource */
    private String dataSourceName;

    /** Description */
    private String description;

    /** Environment that may be used to set up a JNDI initial context. */
    private Properties jndiEnvironment;

    /** Login Timeout */
    private Duration loginTimeoutDuration = Duration.ZERO;

    /** Log stream */
    private PrintWriter logWriter;

    /** Instance key */
    private String instanceKey;

    // Pool properties
    private boolean defaultBlockWhenExhausted = BaseObjectPoolConfig.DEFAULT_BLOCK_WHEN_EXHAUSTED;
    private String defaultEvictionPolicyClassName = BaseObjectPoolConfig.DEFAULT_EVICTION_POLICY_CLASS_NAME;
    private boolean defaultLifo = BaseObjectPoolConfig.DEFAULT_LIFO;
    private int defaultMaxIdle = GenericKeyedObjectPoolConfig.DEFAULT_MAX_IDLE_PER_KEY;
    private int defaultMaxTotal = GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL;
    private Duration defaultMaxWaitDuration = BaseObjectPoolConfig.DEFAULT_MAX_WAIT;
    private Duration defaultMinEvictableIdleDuration = BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_DURATION;
    private int defaultMinIdle = GenericKeyedObjectPoolConfig.DEFAULT_MIN_IDLE_PER_KEY;
    private int defaultNumTestsPerEvictionRun = BaseObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
    private Duration defaultSoftMinEvictableIdleDuration = BaseObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_DURATION;
    private boolean defaultTestOnCreate = BaseObjectPoolConfig.DEFAULT_TEST_ON_CREATE;
    private boolean defaultTestOnBorrow = BaseObjectPoolConfig.DEFAULT_TEST_ON_BORROW;
    private boolean defaultTestOnReturn = BaseObjectPoolConfig.DEFAULT_TEST_ON_RETURN;
    private boolean defaultTestWhileIdle = BaseObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE;
    private Duration defaultDurationBetweenEvictionRuns = BaseObjectPoolConfig.DEFAULT_DURATION_BETWEEN_EVICTION_RUNS;

    // Connection factory properties
    private String validationQuery;
    private Duration validationQueryTimeoutDuration = Duration.ofSeconds(-1);
    private boolean rollbackAfterValidation;
    private Duration maxConnDuration = Duration.ofMillis(-1);

    // Connection properties
    private Boolean defaultAutoCommit;
    private int defaultTransactionIsolation = UNKNOWN_TRANSACTIONISOLATION;
    private Boolean defaultReadOnly;

    /**
     * Default no-arg constructor for Serialization.
     */
    public InstanceKeyDataSource() {
    }

    /**
     * Throws an IllegalStateException, if a PooledConnection has already been requested.
     *
     * @throws IllegalStateException Thrown if a PooledConnection has already been requested.
     */
    protected void assertInitializationAllowed() throws IllegalStateException {
        if (getConnectionCalled) {
            throw new IllegalStateException(GET_CONNECTION_CALLED);
        }
    }

    /**
     * Closes the connection pool being maintained by this datasource.
     */
    @Override
    public abstract void close() throws SQLException;

    private void closeDueToException(final PooledConnectionAndInfo info) {
        if (info != null) {
            try {
                info.getPooledConnection().getConnection().close();
            } catch (final Exception e) {
                // do not throw this exception because we are in the middle
                // of handling another exception. But record it because
                // it potentially leaks connections from the pool.
                getLogWriter().println("[ERROR] Could not return connection to pool during exception handling. " + e.getMessage());
            }
        }
    }

    /**
     * Attempts to establish a database connection.
     */
    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(null, null);
    }

    /**
     * Attempts to retrieve a database connection using {@link #getPooledConnectionAndInfo(String, String)} with the
     * provided user name and password. The password on the {@code PooledConnectionAndInfo} instance returned by
     * {@code getPooledConnectionAndInfo} is compared to the {@code password} parameter. If the comparison
     * fails, a database connection using the supplied user name and password is attempted. If the connection attempt
     * fails, an SQLException is thrown, indicating that the given password did not match the password used to create
     * the pooled connection. If the connection attempt succeeds, this means that the database password has been
     * changed. In this case, the {@code PooledConnectionAndInfo} instance retrieved with the old password is
     * destroyed and the {@code getPooledConnectionAndInfo} is repeatedly invoked until a
     * {@code PooledConnectionAndInfo} instance with the new password is returned.
     */
    @Override
    public Connection getConnection(final String userName, final String userPassword) throws SQLException {
        if (instanceKey == null) {
            throw new SQLException("Must set the ConnectionPoolDataSource "
                    + "through setDataSourceName or setConnectionPoolDataSource" + " before calling getConnection.");
        }
        getConnectionCalled = true;
        PooledConnectionAndInfo info = null;
        try {
            info = getPooledConnectionAndInfo(userName, userPassword);
        } catch (final RuntimeException | SQLException e) {
            closeDueToException(info);
            throw e;
        } catch (final Exception e) {
            closeDueToException(info);
            throw new SQLException("Cannot borrow connection from pool", e);
        }

        // Password on PooledConnectionAndInfo does not match
        if (!(null == userPassword ? null == info.getPassword() : userPassword.equals(info.getPassword()))) {
            try { // See if password has changed by attempting connection
                testCPDS(userName, userPassword);
            } catch (final SQLException ex) {
                // Password has not changed, so refuse client, but return connection to the pool
                closeDueToException(info);
                throw new SQLException(
                        "Given password did not match password used" + " to create the PooledConnection.", ex);
            } catch (final javax.naming.NamingException ne) {
                throw new SQLException("NamingException encountered connecting to database", ne);
            }
            /*
             * Password must have changed -> destroy connection and keep retrying until we get a new, good one,
             * destroying any idle connections with the old password as we pull them from the pool.
             */
            final UserPassKey upkey = info.getUserPassKey();
            final PooledConnectionManager manager = getConnectionManager(upkey);
            // Destroy and remove from pool
            manager.invalidate(info.getPooledConnection());
            // Reset the password on the factory if using CPDSConnectionFactory
            manager.setPassword(upkey.getPassword());
            info = null;
            for (int i = 0; i < 10; i++) { // Bound the number of retries - only needed if bad instances return
                try {
                    info = getPooledConnectionAndInfo(userName, userPassword);
                } catch (final RuntimeException | SQLException e) {
                    closeDueToException(info);
                    throw e;
                } catch (final Exception e) {
                    closeDueToException(info);
                    throw new SQLException("Cannot borrow connection from pool", e);
                }
                if (info != null && userPassword != null && userPassword.equals(info.getPassword())) {
                    break;
                }
                if (info != null) {
                    manager.invalidate(info.getPooledConnection());
                }
                info = null;
            }
            if (info == null) {
                throw new SQLException("Cannot borrow connection from pool - password change failure.");
            }
        }

        final Connection connection = info.getPooledConnection().getConnection();
        try {
            setupDefaults(connection, userName);
            connection.clearWarnings();
            return connection;
        } catch (final SQLException ex) {
            Utils.close(connection, e -> getLogWriter().println("ignoring exception during close: " + e));
            throw ex;
        }
    }

    protected abstract PooledConnectionManager getConnectionManager(UserPassKey upkey);

    /**
     * Gets the value of connectionPoolDataSource. This method will return null, if the backing data source is being
     * accessed via JNDI.
     *
     * @return value of connectionPoolDataSource.
     */
    public ConnectionPoolDataSource getConnectionPoolDataSource() {
        return dataSource;
    }

    /**
     * Gets the name of the ConnectionPoolDataSource which backs this pool. This name is used to look up the data source
     * from a JNDI service provider.
     *
     * @return value of dataSourceName.
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getBlockWhenExhausted()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getBlockWhenExhausted()} for each per user
     *         pool.
     */
    public boolean getDefaultBlockWhenExhausted() {
        return this.defaultBlockWhenExhausted;
    }

    /**
     * Gets the default value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for each per user pool.
     *
     * @return The default value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for each per user pool.
     * @since 2.10.0
     */
    public Duration getDefaultDurationBetweenEvictionRuns() {
        return this.defaultDurationBetweenEvictionRuns;
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getEvictionPolicyClassName()} for each per user
     * pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getEvictionPolicyClassName()} for each per user
     *         pool.
     */
    public String getDefaultEvictionPolicyClassName() {
        return this.defaultEvictionPolicyClassName;
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getLifo()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getLifo()} for each per user pool.
     */
    public boolean getDefaultLifo() {
        return this.defaultLifo;
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMaxIdlePerKey()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMaxIdlePerKey()} for each per user pool.
     */
    public int getDefaultMaxIdle() {
        return this.defaultMaxIdle;
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMaxTotalPerKey()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMaxTotalPerKey()} for each per user pool.
     */
    public int getDefaultMaxTotal() {
        return this.defaultMaxTotal;
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitDuration()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitDuration()} for each per user pool.
     * @since 2.9.0
     */
    public Duration getDefaultMaxWait() {
        return this.defaultMaxWaitDuration;
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitDuration()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitDuration()} for each per user pool.
     * @deprecated Use {@link #getDefaultMaxWait()}.
     */
    @Deprecated
    public long getDefaultMaxWaitMillis() {
        return getDefaultMaxWait().toMillis();
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleDuration()} for each per user
     * pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleDuration()} for each per
     *         user pool.
     * @since 2.10.0
     */
    public Duration getDefaultMinEvictableIdleDuration() {
        return this.defaultMinEvictableIdleDuration;
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleDuration()} for each per user
     * pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleDuration()} for each per
     *         user pool.
     * @deprecated Use {@link #getDefaultMinEvictableIdleDuration()}.
     */
    @Deprecated
    public long getDefaultMinEvictableIdleTimeMillis() {
        return this.defaultMinEvictableIdleDuration.toMillis();
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMinIdlePerKey()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMinIdlePerKey()} for each per user pool.
     */
    public int getDefaultMinIdle() {
        return this.defaultMinIdle;
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getNumTestsPerEvictionRun()} for each per user
     * pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getNumTestsPerEvictionRun()} for each per user
     *         pool.
     */
    public int getDefaultNumTestsPerEvictionRun() {
        return this.defaultNumTestsPerEvictionRun;
    }

    /**
     * Gets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     *
     * @return The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *         GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     * @since 2.10.0
     */
    public Duration getDefaultSoftMinEvictableIdleDuration() {
        return this.defaultSoftMinEvictableIdleDuration;
    }

    /**
     * Gets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     *
     * @return The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *         GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     * @deprecated Use {@link #getDefaultSoftMinEvictableIdleDuration()}.
     */
    @Deprecated
    public long getDefaultSoftMinEvictableIdleTimeMillis() {
        return this.defaultSoftMinEvictableIdleDuration.toMillis();
    }

    /**
     * Gets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getTestOnBorrow()} for each per user pool.
     *
     * @return The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *         GenericObjectPool#getTestOnBorrow()} for each per user pool.
     */
    public boolean getDefaultTestOnBorrow() {
        return this.defaultTestOnBorrow;
    }

    /**
     * Gets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getTestOnCreate()} for each per user pool.
     *
     * @return The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *         GenericObjectPool#getTestOnCreate()} for each per user pool.
     */
    public boolean getDefaultTestOnCreate() {
        return this.defaultTestOnCreate;
    }

    /**
     * Gets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getTestOnReturn()} for each per user pool.
     *
     * @return The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *         GenericObjectPool#getTestOnReturn()} for each per user pool.
     */
    public boolean getDefaultTestOnReturn() {
        return this.defaultTestOnReturn;
    }

    /**
     * Gets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getTestWhileIdle()} for each per user pool.
     *
     * @return The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *         GenericObjectPool#getTestWhileIdle()} for each per user pool.
     */
    public boolean getDefaultTestWhileIdle() {
        return this.defaultTestWhileIdle;
    }

    /**
     * Gets the default value for {@link GenericObjectPool#getDurationBetweenEvictionRuns ()} for each per user pool.
     *
     * @return The default value for {@link GenericObjectPool#getDurationBetweenEvictionRuns ()} for each per user pool.
     * @deprecated Use {@link #getDefaultDurationBetweenEvictionRuns()}.
     */
    @Deprecated
    public long getDefaultTimeBetweenEvictionRunsMillis() {
        return this.defaultDurationBetweenEvictionRuns.toMillis();
    }

    /**
     * Gets the value of defaultTransactionIsolation, which defines the state of connections handed out from this pool.
     * The value can be changed on the Connection using Connection.setTransactionIsolation(int). If this method returns
     * -1, the default is JDBC driver dependent.
     *
     * @return value of defaultTransactionIsolation.
     */
    public int getDefaultTransactionIsolation() {
        return defaultTransactionIsolation;
    }

    /**
     * Gets the description. This property is defined by JDBC as for use with GUI (or other) tools that might deploy the
     * datasource. It serves no internal purpose.
     *
     * @return value of description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the instance key.
     *
     * @return the instance key.
     */
    protected String getInstanceKey() {
        return instanceKey;
    }

    /**
     * Gets the value of jndiEnvironment which is used when instantiating a JNDI InitialContext. This InitialContext is
     * used to locate the back end ConnectionPoolDataSource.
     *
     * @param key
     *            JNDI environment key.
     * @return value of jndiEnvironment.
     */
    public String getJndiEnvironment(final String key) {
        String value = null;
        if (jndiEnvironment != null) {
            value = jndiEnvironment.getProperty(key);
        }
        return value;
    }

    /**
     * Gets the value of loginTimeout.
     *
     * @return value of loginTimeout.
     * @deprecated Use {@link #getLoginTimeoutDuration()}.
     */
    @Deprecated
    @Override
    public int getLoginTimeout() {
        return (int) loginTimeoutDuration.getSeconds();
    }

    /**
     * Gets the value of loginTimeout.
     *
     * @return value of loginTimeout.
     * @since 2.10.0
     */
    public Duration getLoginTimeoutDuration() {
        return loginTimeoutDuration;
    }

    /**
     * Gets the value of logWriter.
     *
     * @return value of logWriter.
     */
    @Override
    public PrintWriter getLogWriter() {
        if (logWriter == null) {
            logWriter = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        }
        return logWriter;
    }

    /**
     * Gets the maximum permitted lifetime of a connection. A value of zero or less indicates an
     * infinite lifetime.
     *
     * @return The maximum permitted lifetime of a connection. A value of zero or less indicates an
     *         infinite lifetime.
     * @since 2.10.0
     */
    public Duration getMaxConnDuration() {
        return maxConnDuration;
    }

    /**
     * Gets the maximum permitted lifetime of a connection. A value of zero or less indicates an
     * infinite lifetime.
     *
     * @return The maximum permitted lifetime of a connection. A value of zero or less indicates an
     *         infinite lifetime.
     * @deprecated Use {@link #getMaxConnDuration()}.
     */
    @Deprecated
    public Duration getMaxConnLifetime() {
        return maxConnDuration;
    }

    /**
     * Gets the maximum permitted lifetime of a connection in milliseconds. A value of zero or less indicates an
     * infinite lifetime.
     *
     * @return The maximum permitted lifetime of a connection in milliseconds. A value of zero or less indicates an
     *         infinite lifetime.
     * @deprecated Use {@link #getMaxConnLifetime()}.
     */
    @Deprecated
    public long getMaxConnLifetimeMillis() {
        return maxConnDuration.toMillis();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * This method is protected but can only be implemented in this package because PooledConnectionAndInfo is a package
     * private type.
     *
     * @param userName The user name.
     * @param userPassword The user password.
     * @return Matching PooledConnectionAndInfo.
     * @throws SQLException Connection or registration failure.
     */
    protected abstract PooledConnectionAndInfo getPooledConnectionAndInfo(String userName, String userPassword)
            throws SQLException;

    /**
     * Gets the SQL query that will be used to validate connections from this pool before returning them to the caller.
     * If specified, this query <strong>MUST</strong> be an SQL SELECT statement that returns at least one row. If not
     * specified, {@link Connection#isValid(int)} will be used to validate connections.
     *
     * @return The SQL query that will be used to validate connections from this pool before returning them to the
     *         caller.
     */
    public String getValidationQuery() {
        return this.validationQuery;
    }

    /**
     * Returns the timeout in seconds before the validation query fails.
     *
     * @return The timeout in seconds before the validation query fails.
     * @deprecated Use {@link #getValidationQueryTimeoutDuration()}.
     */
    @Deprecated
    public int getValidationQueryTimeout() {
        return (int) validationQueryTimeoutDuration.getSeconds();
    }

    /**
     * Returns the timeout Duration before the validation query fails.
     *
     * @return The timeout Duration before the validation query fails.
     */
    public Duration getValidationQueryTimeoutDuration() {
        return validationQueryTimeoutDuration;
    }

    /**
     * Gets the value of defaultAutoCommit, which defines the state of connections handed out from this pool. The value
     * can be changed on the Connection using Connection.setAutoCommit(boolean). The default is {@code null} which
     * will use the default value for the drive.
     *
     * @return value of defaultAutoCommit.
     */
    public Boolean isDefaultAutoCommit() {
        return defaultAutoCommit;
    }

    /**
     * Gets the value of defaultReadOnly, which defines the state of connections handed out from this pool. The value
     * can be changed on the Connection using Connection.setReadOnly(boolean). The default is {@code null} which
     * will use the default value for the drive.
     *
     * @return value of defaultReadOnly.
     */
    public Boolean isDefaultReadOnly() {
        return defaultReadOnly;
    }

    /**
     * Whether a rollback will be issued after executing the SQL query that will be used to validate connections from
     * this pool before returning them to the caller.
     *
     * @return true if a rollback will be issued after executing the validation query
     */
    public boolean isRollbackAfterValidation() {
        return this.rollbackAfterValidation;
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }

    /**
     * Sets the back end ConnectionPoolDataSource. This property should not be set if using JNDI to access the
     * data source.
     *
     * @param dataSource
     *            Value to assign to connectionPoolDataSource.
     */
    public void setConnectionPoolDataSource(final ConnectionPoolDataSource dataSource) {
        assertInitializationAllowed();
        if (dataSourceName != null) {
            throw new IllegalStateException("Cannot set the DataSource, if JNDI is used.");
        }
        if (this.dataSource != null) {
            throw new IllegalStateException("The CPDS has already been set. It cannot be altered.");
        }
        this.dataSource = dataSource;
        instanceKey = InstanceKeyDataSourceFactory.registerNewInstance(this);
    }

    /**
     * Sets the name of the ConnectionPoolDataSource which backs this pool. This name is used to look up the data source
     * from a JNDI service provider.
     *
     * @param dataSourceName
     *            Value to assign to dataSourceName.
     */
    public void setDataSourceName(final String dataSourceName) {
        assertInitializationAllowed();
        if (dataSource != null) {
            throw new IllegalStateException("Cannot set the JNDI name for the DataSource, if already "
                    + "set using setConnectionPoolDataSource.");
        }
        if (this.dataSourceName != null) {
            throw new IllegalStateException("The DataSourceName has already been set. " + "It cannot be altered.");
        }
        this.dataSourceName = dataSourceName;
        instanceKey = InstanceKeyDataSourceFactory.registerNewInstance(this);
    }

    /**
     * Sets the value of defaultAutoCommit, which defines the state of connections handed out from this pool. The value
     * can be changed on the Connection using Connection.setAutoCommit(boolean). The default is {@code null} which
     * will use the default value for the drive.
     *
     * @param defaultAutoCommit
     *            Value to assign to defaultAutoCommit.
     */
    public void setDefaultAutoCommit(final Boolean defaultAutoCommit) {
        assertInitializationAllowed();
        this.defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getBlockWhenExhausted()} for each per user pool.
     *
     * @param blockWhenExhausted
     *            The default value for {@link GenericKeyedObjectPoolConfig#getBlockWhenExhausted()} for each per user
     *            pool.
     */
    public void setDefaultBlockWhenExhausted(final boolean blockWhenExhausted) {
        assertInitializationAllowed();
        this.defaultBlockWhenExhausted = blockWhenExhausted;
    }

    /**
     * Sets the default value for {@link GenericObjectPool#getDurationBetweenEvictionRuns ()} for each per user pool.
     *
     * @param defaultDurationBetweenEvictionRuns The default value for
     *        {@link GenericObjectPool#getDurationBetweenEvictionRuns ()} for each per user pool.
     * @since 2.10.0
     */
    public void setDefaultDurationBetweenEvictionRuns(final Duration defaultDurationBetweenEvictionRuns) {
        assertInitializationAllowed();
        this.defaultDurationBetweenEvictionRuns = defaultDurationBetweenEvictionRuns;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getEvictionPolicyClassName()} for each per user
     * pool.
     *
     * @param evictionPolicyClassName
     *            The default value for {@link GenericKeyedObjectPoolConfig#getEvictionPolicyClassName()} for each per
     *            user pool.
     */
    public void setDefaultEvictionPolicyClassName(final String evictionPolicyClassName) {
        assertInitializationAllowed();
        this.defaultEvictionPolicyClassName = evictionPolicyClassName;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getLifo()} for each per user pool.
     *
     * @param lifo
     *            The default value for {@link GenericKeyedObjectPoolConfig#getLifo()} for each per user pool.
     */
    public void setDefaultLifo(final boolean lifo) {
        assertInitializationAllowed();
        this.defaultLifo = lifo;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getMaxIdlePerKey()} for each per user pool.
     *
     * @param maxIdle
     *            The default value for {@link GenericKeyedObjectPoolConfig#getMaxIdlePerKey()} for each per user pool.
     */
    public void setDefaultMaxIdle(final int maxIdle) {
        assertInitializationAllowed();
        this.defaultMaxIdle = maxIdle;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getMaxTotalPerKey()} for each per user pool.
     *
     * @param maxTotal
     *            The default value for {@link GenericKeyedObjectPoolConfig#getMaxTotalPerKey()} for each per user pool.
     */
    public void setDefaultMaxTotal(final int maxTotal) {
        assertInitializationAllowed();
        this.defaultMaxTotal = maxTotal;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitDuration()} for each per user pool.
     *
     * @param maxWaitMillis
     *            The default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitDuration()} for each per user pool.
     * @since 2.9.0
     */
    public void setDefaultMaxWait(final Duration maxWaitMillis) {
        assertInitializationAllowed();
        this.defaultMaxWaitDuration = maxWaitMillis;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitMillis()} for each per user pool.
     *
     * @param maxWaitMillis
     *            The default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitMillis()} for each per user pool.
     * @deprecated Use {@link #setDefaultMaxWait(Duration)}.
     */
    @Deprecated
    public void setDefaultMaxWaitMillis(final long maxWaitMillis) {
        setDefaultMaxWait(Duration.ofMillis(maxWaitMillis));
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleDuration()} for each per user
     * pool.
     *
     * @param defaultMinEvictableIdleDuration
     *            The default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleDuration()} for each
     *            per user pool.
     * @since 2.10.0
     */
    public void setDefaultMinEvictableIdle(final Duration defaultMinEvictableIdleDuration) {
        assertInitializationAllowed();
        this.defaultMinEvictableIdleDuration = defaultMinEvictableIdleDuration;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleDuration()} for each per user
     * pool.
     *
     * @param minEvictableIdleTimeMillis
     *            The default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleDuration()} for each
     *            per user pool.
     * @deprecated Use {@link #setDefaultMinEvictableIdle(Duration)}.
     */
    @Deprecated
    public void setDefaultMinEvictableIdleTimeMillis(final long minEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        this.defaultMinEvictableIdleDuration = Duration.ofMillis(minEvictableIdleTimeMillis);
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getMinIdlePerKey()} for each per user pool.
     *
     * @param minIdle
     *            The default value for {@link GenericKeyedObjectPoolConfig#getMinIdlePerKey()} for each per user pool.
     */
    public void setDefaultMinIdle(final int minIdle) {
        assertInitializationAllowed();
        this.defaultMinIdle = minIdle;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getNumTestsPerEvictionRun()} for each per user
     * pool.
     *
     * @param numTestsPerEvictionRun
     *            The default value for {@link GenericKeyedObjectPoolConfig#getNumTestsPerEvictionRun()} for each per
     *            user pool.
     */
    public void setDefaultNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        assertInitializationAllowed();
        this.defaultNumTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Sets the value of defaultReadOnly, which defines the state of connections handed out from this pool. The value
     * can be changed on the Connection using Connection.setReadOnly(boolean). The default is {@code null} which
     * will use the default value for the drive.
     *
     * @param defaultReadOnly
     *            Value to assign to defaultReadOnly.
     */
    public void setDefaultReadOnly(final Boolean defaultReadOnly) {
        assertInitializationAllowed();
        this.defaultReadOnly = defaultReadOnly;
    }

    /**
     * Sets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     *
     * @param defaultSoftMinEvictableIdleDuration
     *            The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *            GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     * @since 2.10.0
     */
    public void setDefaultSoftMinEvictableIdle(final Duration defaultSoftMinEvictableIdleDuration) {
        assertInitializationAllowed();
        this.defaultSoftMinEvictableIdleDuration = defaultSoftMinEvictableIdleDuration;
    }

    /**
     * Sets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     *
     * @param softMinEvictableIdleTimeMillis
     *            The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *            GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     * @deprecated Use {@link #setDefaultSoftMinEvictableIdle(Duration)}.
     */
    @Deprecated
    public void setDefaultSoftMinEvictableIdleTimeMillis(final long softMinEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        this.defaultSoftMinEvictableIdleDuration = Duration.ofMillis(softMinEvictableIdleTimeMillis);
    }

    /**
     * Sets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getTestOnBorrow()} for each per user pool.
     *
     * @param testOnBorrow
     *            The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *            GenericObjectPool#getTestOnBorrow()} for each per user pool.
     */
    public void setDefaultTestOnBorrow(final boolean testOnBorrow) {
        assertInitializationAllowed();
        this.defaultTestOnBorrow = testOnBorrow;
    }

    /**
     * Sets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getTestOnCreate()} for each per user pool.
     *
     * @param testOnCreate
     *            The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *            GenericObjectPool#getTestOnCreate()} for each per user pool.
     */
    public void setDefaultTestOnCreate(final boolean testOnCreate) {
        assertInitializationAllowed();
        this.defaultTestOnCreate = testOnCreate;
    }

    /**
     * Sets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getTestOnReturn()} for each per user pool.
     *
     * @param testOnReturn
     *            The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *            GenericObjectPool#getTestOnReturn()} for each per user pool.
     */
    public void setDefaultTestOnReturn(final boolean testOnReturn) {
        assertInitializationAllowed();
        this.defaultTestOnReturn = testOnReturn;
    }

    /**
     * Sets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getTestWhileIdle()} for each per user pool.
     *
     * @param testWhileIdle
     *            The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *            GenericObjectPool#getTestWhileIdle()} for each per user pool.
     */
    public void setDefaultTestWhileIdle(final boolean testWhileIdle) {
        assertInitializationAllowed();
        this.defaultTestWhileIdle = testWhileIdle;
    }

    /**
     * Sets the default value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for each per user pool.
     *
     * @param timeBetweenEvictionRunsMillis The default value for
     *        {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for each per user pool.
     * @deprecated Use {@link #setDefaultDurationBetweenEvictionRuns(Duration)}.
     */
    @Deprecated
    public void setDefaultTimeBetweenEvictionRunsMillis(final long timeBetweenEvictionRunsMillis) {
        assertInitializationAllowed();
        this.defaultDurationBetweenEvictionRuns = Duration.ofMillis(timeBetweenEvictionRunsMillis);
    }

    /**
     * Sets the value of defaultTransactionIsolation, which defines the state of connections handed out from this pool.
     * The value can be changed on the Connection using Connection.setTransactionIsolation(int). The default is JDBC
     * driver dependent.
     *
     * @param defaultTransactionIsolation
     *            Value to assign to defaultTransactionIsolation
     */
    public void setDefaultTransactionIsolation(final int defaultTransactionIsolation) {
        assertInitializationAllowed();
        switch (defaultTransactionIsolation) {
        case Connection.TRANSACTION_NONE:
        case Connection.TRANSACTION_READ_COMMITTED:
        case Connection.TRANSACTION_READ_UNCOMMITTED:
        case Connection.TRANSACTION_REPEATABLE_READ:
        case Connection.TRANSACTION_SERIALIZABLE:
            break;
        default:
            throw new IllegalArgumentException(BAD_TRANSACTION_ISOLATION);
        }
        this.defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * Sets the description. This property is defined by JDBC as for use with GUI (or other) tools that might deploy the
     * datasource. It serves no internal purpose.
     *
     * @param description
     *            Value to assign to description.
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Sets the JNDI environment to be used when instantiating a JNDI InitialContext. This InitialContext is used to
     * locate the back end ConnectionPoolDataSource.
     *
     * @param properties
     *            the JNDI environment property to set which will overwrite any current settings
     */
    void setJndiEnvironment(final Properties properties) {
        if (jndiEnvironment == null) {
            jndiEnvironment = new Properties();
        } else {
            jndiEnvironment.clear();
        }
        jndiEnvironment.putAll(properties);
    }

    /**
     * Sets the value of the given JNDI environment property to be used when instantiating a JNDI InitialContext. This
     * InitialContext is used to locate the back end ConnectionPoolDataSource.
     *
     * @param key
     *            the JNDI environment property to set.
     * @param value
     *            the value assigned to specified JNDI environment property.
     */
    public void setJndiEnvironment(final String key, final String value) {
        if (jndiEnvironment == null) {
            jndiEnvironment = new Properties();
        }
        jndiEnvironment.setProperty(key, value);
    }

    /**
     * Sets the value of loginTimeout.
     *
     * @param loginTimeout
     *            Value to assign to loginTimeout.
     * @since 2.10.0
     */
    public void setLoginTimeout(final Duration loginTimeout) {
        this.loginTimeoutDuration = loginTimeout;
    }

    /**
     * Sets the value of loginTimeout.
     *
     * @param loginTimeout
     *            Value to assign to loginTimeout.
     * @deprecated Use {@link #setLoginTimeout(Duration)}.
     */
    @Deprecated
    @Override
    public void setLoginTimeout(final int loginTimeout) {
        this.loginTimeoutDuration = Duration.ofSeconds(loginTimeout);
    }

    /**
     * Sets the value of logWriter.
     *
     * @param logWriter
     *            Value to assign to logWriter.
     */
    @Override
    public void setLogWriter(final PrintWriter logWriter) {
        this.logWriter = logWriter;
    }

    /**
     * <p>
     * Sets the maximum permitted lifetime of a connection. A value of zero or less indicates an infinite lifetime.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first time one of the following methods is
     * invoked: {@link #getConnection()}, {@link #setLogWriter(PrintWriter)}, {@link #setLoginTimeout(Duration)}, {@link #getLoginTimeoutDuration()},
     * {@link #getLogWriter()}.
     * </p>
     *
     * @param maxConnLifetimeMillis The maximum permitted lifetime of a connection. A value of zero or less indicates an infinite lifetime.
     * @since 2.9.0
     */
    public void setMaxConnLifetime(final Duration maxConnLifetimeMillis) {
        this.maxConnDuration = maxConnLifetimeMillis;
    }

    /**
     * <p>
     * Sets the maximum permitted lifetime of a connection in milliseconds. A value of zero or less indicates an infinite lifetime.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first time one of the following methods is
     * invoked: {@link #getConnection()}, {@link #setLogWriter(PrintWriter)}, {@link #setLoginTimeout(Duration)}, {@link #getLoginTimeoutDuration()},
     * {@link #getLogWriter()}.
     * </p>
     *
     * @param maxConnLifetimeMillis The maximum permitted lifetime of a connection in milliseconds. A value of zero or less indicates an infinite lifetime.
     * @deprecated Use {@link #setMaxConnLifetime(Duration)}.
     */
    @Deprecated
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        setMaxConnLifetime(Duration.ofMillis(maxConnLifetimeMillis));
    }

    /**
     * Whether a rollback will be issued after executing the SQL query that will be used to validate connections from
     * this pool before returning them to the caller. Default behavior is NOT to issue a rollback. The setting will only
     * have an effect if a validation query is set
     *
     * @param rollbackAfterValidation
     *            new property value
     */
    public void setRollbackAfterValidation(final boolean rollbackAfterValidation) {
        assertInitializationAllowed();
        this.rollbackAfterValidation = rollbackAfterValidation;
    }

    protected abstract void setupDefaults(Connection connection, String userName) throws SQLException;

    /**
     * Sets the SQL query that will be used to validate connections from this pool before returning them to the caller.
     * If specified, this query <strong>MUST</strong> be an SQL SELECT statement that returns at least one row. If not
     * specified, connections will be validated using {@link Connection#isValid(int)}.
     *
     * @param validationQuery
     *            The SQL query that will be used to validate connections from this pool before returning them to the
     *            caller.
     */
    public void setValidationQuery(final String validationQuery) {
        assertInitializationAllowed();
        this.validationQuery = validationQuery;
    }

    /**
     * Sets the timeout duration before the validation query fails.
     *
     * @param validationQueryTimeoutDuration
     *            The new timeout duration.
     */
    public void setValidationQueryTimeout(final Duration validationQueryTimeoutDuration) {
        this.validationQueryTimeoutDuration = validationQueryTimeoutDuration;
    }

    /**
     * Sets the timeout in seconds before the validation query fails.
     *
     * @param validationQueryTimeoutSeconds
     *            The new timeout in seconds
     * @deprecated Use {@link #setValidationQueryTimeout(Duration)}.
     */
    @Deprecated
    public void setValidationQueryTimeout(final int validationQueryTimeoutSeconds) {
        this.validationQueryTimeoutDuration = Duration.ofSeconds(validationQueryTimeoutSeconds);
    }

    protected ConnectionPoolDataSource testCPDS(final String userName, final String userPassword)
            throws javax.naming.NamingException, SQLException {
        // The source of physical db connections
        ConnectionPoolDataSource cpds = this.dataSource;
        if (cpds == null) {
            Context ctx = null;
            if (jndiEnvironment == null) {
                ctx = new InitialContext();
            } else {
                ctx = new InitialContext(jndiEnvironment);
            }
            final Object ds = ctx.lookup(dataSourceName);
            if (!(ds instanceof ConnectionPoolDataSource)) {
                throw new SQLException("Illegal configuration: " + "DataSource " + dataSourceName + " ("
                        + ds.getClass().getName() + ")" + " doesn't implement javax.sql.ConnectionPoolDataSource");
            }
            cpds = (ConnectionPoolDataSource) ds;
        }

        // try to get a connection with the supplied userName/password
        PooledConnection conn = null;
        try {
            if (userName != null) {
                conn = cpds.getPooledConnection(userName, userPassword);
            } else {
                conn = cpds.getPooledConnection();
            }
            if (conn == null) {
                throw new SQLException("Cannot connect using the supplied userName/password");
            }
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (final SQLException ignored) {
                    // at least we could connect
                }
            }
        }
        return cpds;
    }

    /**
     * @since 2.6.0
     */
    @Override
    public synchronized String toString() {
        final StringBuilder builder = new StringBuilder(super.toString());
        builder.append('[');
        toStringFields(builder);
        builder.append(']');
        return builder.toString();
    }

    protected void toStringFields(final StringBuilder builder) {
        builder.append("getConnectionCalled=");
        builder.append(getConnectionCalled);
        builder.append(", dataSource=");
        builder.append(dataSource);
        builder.append(", dataSourceName=");
        builder.append(dataSourceName);
        builder.append(", description=");
        builder.append(description);
        builder.append(", jndiEnvironment=");
        builder.append(jndiEnvironment);
        builder.append(", loginTimeoutDuration=");
        builder.append(loginTimeoutDuration);
        builder.append(", logWriter=");
        builder.append(logWriter);
        builder.append(", instanceKey=");
        builder.append(instanceKey);
        builder.append(", defaultBlockWhenExhausted=");
        builder.append(defaultBlockWhenExhausted);
        builder.append(", defaultEvictionPolicyClassName=");
        builder.append(defaultEvictionPolicyClassName);
        builder.append(", defaultLifo=");
        builder.append(defaultLifo);
        builder.append(", defaultMaxIdle=");
        builder.append(defaultMaxIdle);
        builder.append(", defaultMaxTotal=");
        builder.append(defaultMaxTotal);
        builder.append(", defaultMaxWaitDuration=");
        builder.append(defaultMaxWaitDuration);
        builder.append(", defaultMinEvictableIdleDuration=");
        builder.append(defaultMinEvictableIdleDuration);
        builder.append(", defaultMinIdle=");
        builder.append(defaultMinIdle);
        builder.append(", defaultNumTestsPerEvictionRun=");
        builder.append(defaultNumTestsPerEvictionRun);
        builder.append(", defaultSoftMinEvictableIdleDuration=");
        builder.append(defaultSoftMinEvictableIdleDuration);
        builder.append(", defaultTestOnCreate=");
        builder.append(defaultTestOnCreate);
        builder.append(", defaultTestOnBorrow=");
        builder.append(defaultTestOnBorrow);
        builder.append(", defaultTestOnReturn=");
        builder.append(defaultTestOnReturn);
        builder.append(", defaultTestWhileIdle=");
        builder.append(defaultTestWhileIdle);
        builder.append(", defaultDurationBetweenEvictionRuns=");
        builder.append(defaultDurationBetweenEvictionRuns);
        builder.append(", validationQuery=");
        builder.append(validationQuery);
        builder.append(", validationQueryTimeoutDuration=");
        builder.append(validationQueryTimeoutDuration);
        builder.append(", rollbackAfterValidation=");
        builder.append(rollbackAfterValidation);
        builder.append(", maxConnDuration=");
        builder.append(maxConnDuration);
        builder.append(", defaultAutoCommit=");
        builder.append(defaultAutoCommit);
        builder.append(", defaultTransactionIsolation=");
        builder.append(defaultTransactionIsolation);
        builder.append(", defaultReadOnly=");
        builder.append(defaultReadOnly);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (isWrapperFor(iface)) {
            return (T) this;
        }
        throw new SQLException(this + " is not a wrapper for " + iface);
    }
}

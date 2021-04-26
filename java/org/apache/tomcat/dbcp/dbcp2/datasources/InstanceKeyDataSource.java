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
import java.util.Properties;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Referenceable;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.pool2.impl.BaseObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 * <p>
 * The base class for <code>SharedPoolDataSource</code> and <code>PerUserPoolDataSource</code>. Many of the
 * configuration properties are shared and defined here. This class is declared public in order to allow particular
 * usage with commons-beanutils; do not make direct use of it outside of <em>commons-dbcp2</em>.
 * </p>
 *
 * <p>
 * A J2EE container will normally provide some method of initializing the <code>DataSource</code> whose attributes are
 * presented as bean getters/setters and then deploying it via JNDI. It is then available to an application as a source
 * of pooled logical connections to the database. The pool needs a source of physical connections. This source is in the
 * form of a <code>ConnectionPoolDataSource</code> that can be specified via the {@link #setDataSourceName(String)} used
 * to lookup the source via JNDI.
 * </p>
 *
 * <p>
 * Although normally used within a JNDI environment, A DataSource can be instantiated and initialized as any bean. In
 * this case the <code>ConnectionPoolDataSource</code> will likely be instantiated in a similar manner. This class
 * allows the physical source of connections to be attached directly to this pool using the
 * {@link #setConnectionPoolDataSource(ConnectionPoolDataSource)} method.
 * </p>
 *
 * <p>
 * The dbcp package contains an adapter, {@link org.apache.tomcat.dbcp.dbcp2.cpdsadapter.DriverAdapterCPDS}, that can be
 * used to allow the use of <code>DataSource</code>'s based on this class with JDBC driver implementations that do not
 * supply a <code>ConnectionPoolDataSource</code>, but still provide a {@link java.sql.Driver} implementation.
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

    /** Login TimeOut in seconds */
    private int loginTimeout;

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
    private long defaultMaxWaitMillis = BaseObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS;
    private long defaultMinEvictableIdleTimeMillis = BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    private int defaultMinIdle = GenericKeyedObjectPoolConfig.DEFAULT_MIN_IDLE_PER_KEY;
    private int defaultNumTestsPerEvictionRun = BaseObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
    private long defaultSoftMinEvictableIdleTimeMillis = BaseObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    private boolean defaultTestOnCreate = BaseObjectPoolConfig.DEFAULT_TEST_ON_CREATE;
    private boolean defaultTestOnBorrow = BaseObjectPoolConfig.DEFAULT_TEST_ON_BORROW;
    private boolean defaultTestOnReturn = BaseObjectPoolConfig.DEFAULT_TEST_ON_RETURN;
    private boolean defaultTestWhileIdle = BaseObjectPoolConfig.DEFAULT_TEST_WHILE_IDLE;
    private long defaultTimeBetweenEvictionRunsMillis = BaseObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    // Connection factory properties
    private String validationQuery;
    private int validationQueryTimeoutSeconds = -1;
    private boolean rollbackAfterValidation;
    private long maxConnLifetimeMillis = -1;

    // Connection properties
    private Boolean defaultAutoCommit;
    private int defaultTransactionIsolation = UNKNOWN_TRANSACTIONISOLATION;
    private Boolean defaultReadOnly;

    /**
     * Default no-arg constructor for Serialization
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
    public abstract void close() throws Exception;

    protected abstract PooledConnectionManager getConnectionManager(UserPassKey upkey);

    /* JDBC_4_ANT_KEY_BEGIN */
    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        throw new SQLException("InstanceKeyDataSource is not a wrapper.");
    }
    /* JDBC_4_ANT_KEY_END */

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    // -------------------------------------------------------------------
    // Properties

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
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getLifo()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getLifo()} for each per user pool.
     */
    public boolean getDefaultLifo() {
        return this.defaultLifo;
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
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMaxIdlePerKey()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMaxIdlePerKey()} for each per user pool.
     */
    public int getDefaultMaxIdle() {
        return this.defaultMaxIdle;
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
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMaxTotalPerKey()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMaxTotalPerKey()} for each per user pool.
     */
    public int getDefaultMaxTotal() {
        return this.defaultMaxTotal;
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
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitMillis()} for each per user pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitMillis()} for each per user pool.
     */
    public long getDefaultMaxWaitMillis() {
        return this.defaultMaxWaitMillis;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitMillis()} for each per user pool.
     *
     * @param maxWaitMillis
     *            The default value for {@link GenericKeyedObjectPoolConfig#getMaxWaitMillis()} for each per user pool.
     */
    public void setDefaultMaxWaitMillis(final long maxWaitMillis) {
        assertInitializationAllowed();
        this.defaultMaxWaitMillis = maxWaitMillis;
    }

    /**
     * Gets the default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleTimeMillis()} for each per user
     * pool.
     *
     * @return The default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleTimeMillis()} for each per
     *         user pool.
     */
    public long getDefaultMinEvictableIdleTimeMillis() {
        return this.defaultMinEvictableIdleTimeMillis;
    }

    /**
     * Sets the default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleTimeMillis()} for each per user
     * pool.
     *
     * @param minEvictableIdleTimeMillis
     *            The default value for {@link GenericKeyedObjectPoolConfig#getMinEvictableIdleTimeMillis()} for each
     *            per user pool.
     */
    public void setDefaultMinEvictableIdleTimeMillis(final long minEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        this.defaultMinEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
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
     * Gets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     *
     * @return The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *         GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     */
    public long getDefaultSoftMinEvictableIdleTimeMillis() {
        return this.defaultSoftMinEvictableIdleTimeMillis;
    }

    /**
     * Sets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     *
     * @param softMinEvictableIdleTimeMillis
     *            The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *            GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for each per user pool.
     */
    public void setDefaultSoftMinEvictableIdleTimeMillis(final long softMinEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        this.defaultSoftMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
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
     * Gets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getTimeBetweenEvictionRunsMillis ()} for each per user pool.
     *
     * @return The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *         GenericObjectPool#getTimeBetweenEvictionRunsMillis ()} for each per user pool.
     */
    public long getDefaultTimeBetweenEvictionRunsMillis() {
        return this.defaultTimeBetweenEvictionRunsMillis;
    }

    /**
     * Sets the default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     * GenericObjectPool#getTimeBetweenEvictionRunsMillis ()} for each per user pool.
     *
     * @param timeBetweenEvictionRunsMillis
     *            The default value for {@link org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool
     *            GenericObjectPool#getTimeBetweenEvictionRunsMillis ()} for each per user pool.
     */
    public void setDefaultTimeBetweenEvictionRunsMillis(final long timeBetweenEvictionRunsMillis) {
        assertInitializationAllowed();
        this.defaultTimeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

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
     * Sets the back end ConnectionPoolDataSource. This property should not be set if using JNDI to access the
     * data source.
     *
     * @param v
     *            Value to assign to connectionPoolDataSource.
     */
    public void setConnectionPoolDataSource(final ConnectionPoolDataSource v) {
        assertInitializationAllowed();
        if (dataSourceName != null) {
            throw new IllegalStateException("Cannot set the DataSource, if JNDI is used.");
        }
        if (dataSource != null) {
            throw new IllegalStateException("The CPDS has already been set. It cannot be altered.");
        }
        dataSource = v;
        instanceKey = InstanceKeyDataSourceFactory.registerNewInstance(this);
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
     * Sets the name of the ConnectionPoolDataSource which backs this pool. This name is used to look up the data source
     * from a JNDI service provider.
     *
     * @param v
     *            Value to assign to dataSourceName.
     */
    public void setDataSourceName(final String v) {
        assertInitializationAllowed();
        if (dataSource != null) {
            throw new IllegalStateException("Cannot set the JNDI name for the DataSource, if already "
                    + "set using setConnectionPoolDataSource.");
        }
        if (dataSourceName != null) {
            throw new IllegalStateException("The DataSourceName has already been set. " + "It cannot be altered.");
        }
        this.dataSourceName = v;
        instanceKey = InstanceKeyDataSourceFactory.registerNewInstance(this);
    }

    /**
     * Gets the value of defaultAutoCommit, which defines the state of connections handed out from this pool. The value
     * can be changed on the Connection using Connection.setAutoCommit(boolean). The default is <code>null</code> which
     * will use the default value for the drive.
     *
     * @return value of defaultAutoCommit.
     */
    public Boolean isDefaultAutoCommit() {
        return defaultAutoCommit;
    }

    /**
     * Sets the value of defaultAutoCommit, which defines the state of connections handed out from this pool. The value
     * can be changed on the Connection using Connection.setAutoCommit(boolean). The default is <code>null</code> which
     * will use the default value for the drive.
     *
     * @param v
     *            Value to assign to defaultAutoCommit.
     */
    public void setDefaultAutoCommit(final Boolean v) {
        assertInitializationAllowed();
        this.defaultAutoCommit = v;
    }

    /**
     * Gets the value of defaultReadOnly, which defines the state of connections handed out from this pool. The value
     * can be changed on the Connection using Connection.setReadOnly(boolean). The default is <code>null</code> which
     * will use the default value for the drive.
     *
     * @return value of defaultReadOnly.
     */
    public Boolean isDefaultReadOnly() {
        return defaultReadOnly;
    }

    /**
     * Sets the value of defaultReadOnly, which defines the state of connections handed out from this pool. The value
     * can be changed on the Connection using Connection.setReadOnly(boolean). The default is <code>null</code> which
     * will use the default value for the drive.
     *
     * @param v
     *            Value to assign to defaultReadOnly.
     */
    public void setDefaultReadOnly(final Boolean v) {
        assertInitializationAllowed();
        this.defaultReadOnly = v;
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
     * Sets the value of defaultTransactionIsolation, which defines the state of connections handed out from this pool.
     * The value can be changed on the Connection using Connection.setTransactionIsolation(int). The default is JDBC
     * driver dependent.
     *
     * @param v
     *            Value to assign to defaultTransactionIsolation
     */
    public void setDefaultTransactionIsolation(final int v) {
        assertInitializationAllowed();
        switch (v) {
        case Connection.TRANSACTION_NONE:
        case Connection.TRANSACTION_READ_COMMITTED:
        case Connection.TRANSACTION_READ_UNCOMMITTED:
        case Connection.TRANSACTION_REPEATABLE_READ:
        case Connection.TRANSACTION_SERIALIZABLE:
            break;
        default:
            throw new IllegalArgumentException(BAD_TRANSACTION_ISOLATION);
        }
        this.defaultTransactionIsolation = v;
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
     * Sets the description. This property is defined by JDBC as for use with GUI (or other) tools that might deploy the
     * datasource. It serves no internal purpose.
     *
     * @param v
     *            Value to assign to description.
     */
    public void setDescription(final String v) {
        this.description = v;
    }

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
     * Gets the value of loginTimeout.
     *
     * @return value of loginTimeout.
     */
    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    /**
     * Sets the value of loginTimeout.
     *
     * @param v
     *            Value to assign to loginTimeout.
     */
    @Override
    public void setLoginTimeout(final int v) {
        this.loginTimeout = v;
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
     * Sets the value of logWriter.
     *
     * @param v
     *            Value to assign to logWriter.
     */
    @Override
    public void setLogWriter(final PrintWriter v) {
        this.logWriter = v;
    }

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
     * Returns the timeout in seconds before the validation query fails.
     *
     * @return The timeout in seconds before the validation query fails.
     */
    public int getValidationQueryTimeout() {
        return validationQueryTimeoutSeconds;
    }

    /**
     * Sets the timeout in seconds before the validation query fails.
     *
     * @param validationQueryTimeoutSeconds
     *            The new timeout in seconds
     */
    public void setValidationQueryTimeout(final int validationQueryTimeoutSeconds) {
        this.validationQueryTimeoutSeconds = validationQueryTimeoutSeconds;
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

    /**
     * Returns the maximum permitted lifetime of a connection in milliseconds. A value of zero or less indicates an
     * infinite lifetime.
     *
     * @return The maximum permitted lifetime of a connection in milliseconds. A value of zero or less indicates an
     *         infinite lifetime.
     */
    public long getMaxConnLifetimeMillis() {
        return maxConnLifetimeMillis;
    }

    /**
     * <p>
     * Sets the maximum permitted lifetime of a connection in milliseconds. A value of zero or less indicates an
     * infinite lifetime.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param maxConnLifetimeMillis
     *            The maximum permitted lifetime of a connection in milliseconds. A value of zero or less indicates an
     *            infinite lifetime.
     */
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        this.maxConnLifetimeMillis = maxConnLifetimeMillis;
    }

    // ----------------------------------------------------------------------
    // Instrumentation Methods

    // ----------------------------------------------------------------------
    // DataSource implementation

    /**
     * Attempts to establish a database connection.
     */
    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(null, null);
    }

    /**
     * Attempts to retrieve a database connection using {@link #getPooledConnectionAndInfo(String, String)} with the
     * provided user name and password. The password on the {@link PooledConnectionAndInfo} instance returned by
     * <code>getPooledConnectionAndInfo</code> is compared to the <code>password</code> parameter. If the comparison
     * fails, a database connection using the supplied user name and password is attempted. If the connection attempt
     * fails, an SQLException is thrown, indicating that the given password did not match the password used to create
     * the pooled connection. If the connection attempt succeeds, this means that the database password has been
     * changed. In this case, the <code>PooledConnectionAndInfo</code> instance retrieved with the old password is
     * destroyed and the <code>getPooledConnectionAndInfo</code> is repeatedly invoked until a
     * <code>PooledConnectionAndInfo</code> instance with the new password is returned.
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

        final Connection con = info.getPooledConnection().getConnection();
        try {
            setupDefaults(con, userName);
            con.clearWarnings();
            return con;
        } catch (final SQLException ex) {
            try {
                con.close();
            } catch (final Exception exc) {
                getLogWriter().println("ignoring exception during close: " + exc);
            }
            throw ex;
        }
    }

    protected abstract PooledConnectionAndInfo getPooledConnectionAndInfo(String userName, String userPassword)
            throws SQLException;

    protected abstract void setupDefaults(Connection connection, String userName) throws SQLException;

    private void closeDueToException(final PooledConnectionAndInfo info) {
        if (info != null) {
            try {
                info.getPooledConnection().getConnection().close();
            } catch (final Exception e) {
                // do not throw this exception because we are in the middle
                // of handling another exception. But record it because
                // it potentially leaks connections from the pool.
                getLogWriter().println("[ERROR] Could not return connection to " + "pool during exception handling. "
                        + e.getMessage());
            }
        }
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
            if (ds instanceof ConnectionPoolDataSource) {
                cpds = (ConnectionPoolDataSource) ds;
            } else {
                throw new SQLException("Illegal configuration: " + "DataSource " + dataSourceName + " ("
                        + ds.getClass().getName() + ")" + " doesn't implement javax.sql.ConnectionPoolDataSource");
            }
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
                } catch (final SQLException e) {
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
        builder.append(", loginTimeout=");
        builder.append(loginTimeout);
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
        builder.append(", defaultMaxWaitMillis=");
        builder.append(defaultMaxWaitMillis);
        builder.append(", defaultMinEvictableIdleTimeMillis=");
        builder.append(defaultMinEvictableIdleTimeMillis);
        builder.append(", defaultMinIdle=");
        builder.append(defaultMinIdle);
        builder.append(", defaultNumTestsPerEvictionRun=");
        builder.append(defaultNumTestsPerEvictionRun);
        builder.append(", defaultSoftMinEvictableIdleTimeMillis=");
        builder.append(defaultSoftMinEvictableIdleTimeMillis);
        builder.append(", defaultTestOnCreate=");
        builder.append(defaultTestOnCreate);
        builder.append(", defaultTestOnBorrow=");
        builder.append(defaultTestOnBorrow);
        builder.append(", defaultTestOnReturn=");
        builder.append(defaultTestOnReturn);
        builder.append(", defaultTestWhileIdle=");
        builder.append(defaultTestWhileIdle);
        builder.append(", defaultTimeBetweenEvictionRunsMillis=");
        builder.append(defaultTimeBetweenEvictionRunsMillis);
        builder.append(", validationQuery=");
        builder.append(validationQuery);
        builder.append(", validationQueryTimeoutSeconds=");
        builder.append(validationQueryTimeoutSeconds);
        builder.append(", rollbackAfterValidation=");
        builder.append(rollbackAfterValidation);
        builder.append(", maxConnLifetimeMillis=");
        builder.append(maxConnLifetimeMillis);
        builder.append(", defaultAutoCommit=");
        builder.append(defaultAutoCommit);
        builder.append(", defaultTransactionIsolation=");
        builder.append(defaultTransactionIsolation);
        builder.append(", defaultReadOnly=");
        builder.append(defaultReadOnly);
    }
}

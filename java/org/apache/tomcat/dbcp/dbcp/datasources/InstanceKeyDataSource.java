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

package org.apache.tomcat.dbcp.dbcp.datasources;

import java.io.Serializable;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.Referenceable;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.pool.impl.GenericObjectPool;

/**
 * <p>The base class for <code>SharedPoolDataSource</code> and
 * <code>PerUserPoolDataSource</code>.  Many of the configuration properties
 * are shared and defined here.  This class is declared public in order
 * to allow particular usage with commons-beanutils; do not make direct
 * use of it outside of commons-dbcp.
 * </p>
 *
 * <p>
 * A J2EE container will normally provide some method of initializing the
 * <code>DataSource</code> whose attributes are presented
 * as bean getters/setters and then deploying it via JNDI.  It is then
 * available to an application as a source of pooled logical connections to
 * the database.  The pool needs a source of physical connections.  This
 * source is in the form of a <code>ConnectionPoolDataSource</code> that
 * can be specified via the {@link #setDataSourceName(String)} used to
 * lookup the source via JNDI.
 * </p>
 *
 * <p>
 * Although normally used within a JNDI environment, A DataSource
 * can be instantiated and initialized as any bean.  In this case the
 * <code>ConnectionPoolDataSource</code> will likely be instantiated in
 * a similar manner.  This class allows the physical source of connections
 * to be attached directly to this pool using the
 * {@link #setConnectionPoolDataSource(ConnectionPoolDataSource)} method.
 * </p>
 *
 * <p>
 * The dbcp package contains an adapter,
 * {@link org.apache.tomcat.dbcp.dbcp.cpdsadapter.DriverAdapterCPDS},
 * that can be used to allow the use of <code>DataSource</code>'s based on this
 * class with JDBC driver implementations that do not supply a
 * <code>ConnectionPoolDataSource</code>, but still
 * provide a {@link java.sql.Driver} implementation.
 * </p>
 *
 * <p>
 * The <a href="package-summary.html">package documentation</a> contains an
 * example using Apache Tomcat and JNDI and it also contains a non-JNDI example.
 * </p>
 *
 * @author John D. McNally
 */
public abstract class InstanceKeyDataSource
        implements DataSource, Referenceable, Serializable {
    private static final long serialVersionUID = -4243533936955098795L;
    private static final String GET_CONNECTION_CALLED
            = "A Connection was already requested from this source, "
            + "further initialization is not allowed.";
    private static final String BAD_TRANSACTION_ISOLATION
        = "The requested TransactionIsolation level is invalid.";
    /**
    * Internal constant to indicate the level is not set.
    */
    protected static final int UNKNOWN_TRANSACTIONISOLATION = -1;

    /** Guards property setters - once true, setters throw IllegalStateException */
    private volatile boolean getConnectionCalled = false;

    /** Underlying source of PooledConnections */
    private ConnectionPoolDataSource dataSource = null;

    /** DataSource Name used to find the ConnectionPoolDataSource */
    private String dataSourceName = null;

    // Default connection properties
    private boolean defaultAutoCommit = false;
    private int defaultTransactionIsolation = UNKNOWN_TRANSACTIONISOLATION;
    private boolean defaultReadOnly = false;

    /** Description */
    private String description = null;

    /** Environment that may be used to set up a jndi initial context. */
    Properties jndiEnvironment = null;

    /** Login TimeOut in seconds */
    private int loginTimeout = 0;

    /** Log stream */
    private PrintWriter logWriter = null;

    // Pool properties
    private boolean _testOnBorrow = GenericObjectPool.DEFAULT_TEST_ON_BORROW;
    private boolean _testOnReturn = GenericObjectPool.DEFAULT_TEST_ON_RETURN;
    private int _timeBetweenEvictionRunsMillis = (int)
        Math.min(Integer.MAX_VALUE,
                 GenericObjectPool.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS);
    private int _numTestsPerEvictionRun =
        GenericObjectPool.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;
    private int _minEvictableIdleTimeMillis = (int)
    Math.min(Integer.MAX_VALUE,
             GenericObjectPool.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS);
    private boolean _testWhileIdle = GenericObjectPool.DEFAULT_TEST_WHILE_IDLE;
    private String validationQuery = null;
    private boolean rollbackAfterValidation = false;

    /** true iff one of the setters for testOnBorrow, testOnReturn, testWhileIdle has been called. */
    private boolean testPositionSet = false;

    /** Instance key */
    protected String instanceKey = null;

    /**
     * Default no-arg constructor for Serialization
     */
    public InstanceKeyDataSource() {
        defaultAutoCommit = true;
    }

    /**
     * Throws an IllegalStateException, if a PooledConnection has already
     * been requested.
     */
    protected void assertInitializationAllowed()
        throws IllegalStateException {
        if (getConnectionCalled) {
            throw new IllegalStateException(GET_CONNECTION_CALLED);
        }
    }

    /**
     * Close the connection pool being maintained by this datasource.
     */
    public abstract void close() throws Exception;

    protected abstract PooledConnectionManager getConnectionManager(UserPassKey upkey);

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("InstanceKeyDataSource is not a wrapper.");
    }

    /* JDBC_4_1_ANT_KEY_BEGIN */
    // No @Override else it won't compile with Java 6
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
    /* JDBC_4_1_ANT_KEY_END */

    // -------------------------------------------------------------------
    // Properties

    /**
     * Get the value of connectionPoolDataSource.  This method will return
     * null, if the backing datasource is being accessed via jndi.
     *
     * @return value of connectionPoolDataSource.
     */
    public ConnectionPoolDataSource getConnectionPoolDataSource() {
        return dataSource;
    }

    /**
     * Set the backend ConnectionPoolDataSource.  This property should not be
     * set if using jndi to access the datasource.
     *
     * @param v  Value to assign to connectionPoolDataSource.
     */
    public void setConnectionPoolDataSource(ConnectionPoolDataSource v) {
        assertInitializationAllowed();
        if (dataSourceName != null) {
            throw new IllegalStateException(
                "Cannot set the DataSource, if JNDI is used.");
        }
        if (dataSource != null)
        {
            throw new IllegalStateException(
                "The CPDS has already been set. It cannot be altered.");
        }
        dataSource = v;
        instanceKey = InstanceKeyObjectFactory.registerNewInstance(this);
    }

    /**
     * Get the name of the ConnectionPoolDataSource which backs this pool.
     * This name is used to look up the datasource from a jndi service
     * provider.
     *
     * @return value of dataSourceName.
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * Set the name of the ConnectionPoolDataSource which backs this pool.
     * This name is used to look up the datasource from a jndi service
     * provider.
     *
     * @param v  Value to assign to dataSourceName.
     */
    public void setDataSourceName(String v) {
        assertInitializationAllowed();
        if (dataSource != null) {
            throw new IllegalStateException(
                "Cannot set the JNDI name for the DataSource, if already " +
                "set using setConnectionPoolDataSource.");
        }
        if (dataSourceName != null)
        {
            throw new IllegalStateException(
                "The DataSourceName has already been set. " +
                "It cannot be altered.");
        }
        this.dataSourceName = v;
        instanceKey = InstanceKeyObjectFactory.registerNewInstance(this);
    }

    /**
     * Get the value of defaultAutoCommit, which defines the state of
     * connections handed out from this pool.  The value can be changed
     * on the Connection using Connection.setAutoCommit(boolean).
     * The default is true.
     *
     * @return value of defaultAutoCommit.
     */
    public boolean isDefaultAutoCommit() {
        return defaultAutoCommit;
    }

    /**
     * Set the value of defaultAutoCommit, which defines the state of
     * connections handed out from this pool.  The value can be changed
     * on the Connection using Connection.setAutoCommit(boolean).
     * The default is true.
     *
     * @param v  Value to assign to defaultAutoCommit.
     */
    public void setDefaultAutoCommit(boolean v) {
        assertInitializationAllowed();
        this.defaultAutoCommit = v;
    }

    /**
     * Get the value of defaultReadOnly, which defines the state of
     * connections handed out from this pool.  The value can be changed
     * on the Connection using Connection.setReadOnly(boolean).
     * The default is false.
     *
     * @return value of defaultReadOnly.
     */
    public boolean isDefaultReadOnly() {
        return defaultReadOnly;
    }

    /**
     * Set the value of defaultReadOnly, which defines the state of
     * connections handed out from this pool.  The value can be changed
     * on the Connection using Connection.setReadOnly(boolean).
     * The default is false.
     *
     * @param v  Value to assign to defaultReadOnly.
     */
    public void setDefaultReadOnly(boolean v) {
        assertInitializationAllowed();
        this.defaultReadOnly = v;
    }

    /**
     * Get the value of defaultTransactionIsolation, which defines the state of
     * connections handed out from this pool.  The value can be changed
     * on the Connection using Connection.setTransactionIsolation(int).
     * If this method returns -1, the default is JDBC driver dependent.
     *
     * @return value of defaultTransactionIsolation.
     */
    public int getDefaultTransactionIsolation() {
            return defaultTransactionIsolation;
    }

    /**
     * Set the value of defaultTransactionIsolation, which defines the state of
     * connections handed out from this pool.  The value can be changed
     * on the Connection using Connection.setTransactionIsolation(int).
     * The default is JDBC driver dependent.
     *
     * @param v  Value to assign to defaultTransactionIsolation
     */
    public void setDefaultTransactionIsolation(int v) {
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
     * Get the description.  This property is defined by JDBC as for use with
     * GUI (or other) tools that might deploy the datasource.  It serves no
     * internal purpose.
     *
     * @return value of description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the description.  This property is defined by JDBC as for use with
     * GUI (or other) tools that might deploy the datasource.  It serves no
     * internal purpose.
     *
     * @param v  Value to assign to description.
     */
    public void setDescription(String v) {
        this.description = v;
    }

    /**
     * Get the value of jndiEnvironment which is used when instantiating
     * a jndi InitialContext.  This InitialContext is used to locate the
     * backend ConnectionPoolDataSource.
     *
     * @return value of jndiEnvironment.
     */
    public String getJndiEnvironment(String key) {
        String value = null;
        if (jndiEnvironment != null) {
            value = jndiEnvironment.getProperty(key);
        }
        return value;
    }

    /**
     * Sets the value of the given JNDI environment property to be used when
     * instantiating a JNDI InitialContext. This InitialContext is used to
     * locate the backend ConnectionPoolDataSource.
     *
     * @param key the JNDI environment property to set.
     * @param value the value assigned to specified JNDI environment property.
     */
    public void setJndiEnvironment(String key, String value) {
        if (jndiEnvironment == null) {
            jndiEnvironment = new Properties();
        }
        jndiEnvironment.setProperty(key, value);
    }

    /**
     * Get the value of loginTimeout.
     * @return value of loginTimeout.
     */
    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    /**
     * Set the value of loginTimeout.
     * @param v  Value to assign to loginTimeout.
     */
    @Override
    public void setLoginTimeout(int v) {
        this.loginTimeout = v;
    }

    /**
     * Get the value of logWriter.
     * @return value of logWriter.
     */
    @Override
    public PrintWriter getLogWriter() {
        if (logWriter == null) {
            logWriter = new PrintWriter(System.out);
        }
        return logWriter;
    }

    /**
     * Set the value of logWriter.
     * @param v  Value to assign to logWriter.
     */
    @Override
    public void setLogWriter(PrintWriter v) {
        this.logWriter = v;
    }

    /**
     * @see #getTestOnBorrow
     */
    public final boolean isTestOnBorrow() {
        return getTestOnBorrow();
    }

    /**
     * When <code>true</code>, objects will be
     * {*link PoolableObjectFactory#validateObject validated}
     * before being returned by the {*link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another.
     *
     * @see #setTestOnBorrow
     */
    public boolean getTestOnBorrow() {
        return _testOnBorrow;
    }

    /**
     * When <code>true</code>, objects will be
     * {*link PoolableObjectFactory#validateObject validated}
     * before being returned by the {*link #borrowObject}
     * method.  If the object fails to validate,
     * it will be dropped from the pool, and we will attempt
     * to borrow another. For a <code>true</code> value to have any effect,
     * the <code>validationQuery</code> property must be set to a non-null
     * string.
     *
     * @see #getTestOnBorrow
     */
    public void setTestOnBorrow(boolean testOnBorrow) {
        assertInitializationAllowed();
        _testOnBorrow = testOnBorrow;
        testPositionSet = true;
    }

    /**
     * @see #getTestOnReturn
     */
    public final boolean isTestOnReturn() {
        return getTestOnReturn();
    }

    /**
     * When <code>true</code>, objects will be
     * {*link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {*link #returnObject}.
     *
     * @see #setTestOnReturn
     */
    public boolean getTestOnReturn() {
        return _testOnReturn;
    }

    /**
     * When <code>true</code>, objects will be
     * {*link PoolableObjectFactory#validateObject validated}
     * before being returned to the pool within the
     * {*link #returnObject}. For a <code>true</code> value to have any effect,
     * the <code>validationQuery</code> property must be set to a non-null
     * string.
     *
     * @see #getTestOnReturn
     */
    public void setTestOnReturn(boolean testOnReturn) {
        assertInitializationAllowed();
        _testOnReturn = testOnReturn;
        testPositionSet = true;
    }

    /**
     * Returns the number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public int getTimeBetweenEvictionRunsMillis() {
        return _timeBetweenEvictionRunsMillis;
    }

    /**
     * Sets the number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     *
     * @see #getTimeBetweenEvictionRunsMillis
     */
    public void
        setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        assertInitializationAllowed();
            _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

    /**
     * Returns the number of objects to examine during each run of the
     * idle object evictor thread (if any).
     *
     * @see #setNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public int getNumTestsPerEvictionRun() {
        return _numTestsPerEvictionRun;
    }

    /**
     * Sets the number of objects to examine during each run of the
     * idle object evictor thread (if any).
     * <p>
     * When a negative value is supplied, <code>ceil({*link #numIdle})/abs({*link #getNumTestsPerEvictionRun})</code>
     * tests will be run.  I.e., when the value is <i>-n</i>, roughly one <i>n</i>th of the
     * idle objects will be tested per run.
     *
     * @see #getNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        assertInitializationAllowed();
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Returns the minimum amount of time an object may sit idle in the pool
     * before it is eligable for eviction by the idle object evictor
     * (if any).
     *
     * @see #setMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public int getMinEvictableIdleTimeMillis() {
        return _minEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum amount of time an object may sit idle in the pool
     * before it is eligable for eviction by the idle object evictor
     * (if any).
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     *
     * @see #getMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * @see #getTestWhileIdle
     */
    public final boolean isTestWhileIdle() {
        return getTestWhileIdle();
    }

    /**
     * When <code>true</code>, objects will be
     * {*link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool.
     *
     * @see #setTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public boolean getTestWhileIdle() {
        return _testWhileIdle;
    }

    /**
     * When <code>true</code>, objects will be
     * {*link PoolableObjectFactory#validateObject validated}
     * by the idle object evictor (if any).  If an object
     * fails to validate, it will be dropped from the pool. For a
     * <code>true</code> value to have any effect,
     * the <code>validationQuery</code> property must be set to a non-null
     * string.
     *
     * @see #getTestWhileIdle
     * @see #setTimeBetweenEvictionRunsMillis
     */
    public void setTestWhileIdle(boolean testWhileIdle) {
        assertInitializationAllowed();
        _testWhileIdle = testWhileIdle;
        testPositionSet = true;
    }

    /**
     * The SQL query that will be used to validate connections from this pool
     * before returning them to the caller.  If specified, this query
     * <strong>MUST</strong> be an SQL SELECT statement that returns at least
     * one row.
     */
    public String getValidationQuery() {
        return (this.validationQuery);
    }

    /**
     * The SQL query that will be used to validate connections from this pool
     * before returning them to the caller.  If specified, this query
     * <strong>MUST</strong> be an SQL SELECT statement that returns at least
     * one row. If none of the properties, testOnBorow, testOnReturn, testWhileIdle
     * have been previously set, calling this method sets testOnBorrow to true.
     */
    public void setValidationQuery(String validationQuery) {
        assertInitializationAllowed();
        this.validationQuery = validationQuery;
        if (!testPositionSet) {
            setTestOnBorrow(true);
        }
    }

    /**
     * Whether a rollback will be issued after executing the SQL query
     * that will be used to validate connections from this pool
     * before returning them to the caller.
     *
     * @return true if a rollback will be issued after executing the
     * validation query
     * @since 1.2.2
     */
    public boolean isRollbackAfterValidation() {
        return (this.rollbackAfterValidation);
    }

    /**
     * Whether a rollback will be issued after executing the SQL query
     * that will be used to validate connections from this pool
     * before returning them to the caller. Default behavior is NOT
     * to issue a rollback. The setting will only have an effect
     * if a validation query is set
     *
     * @param rollbackAfterValidation new property value
     * @since 1.2.2
     */
    public void setRollbackAfterValidation(boolean rollbackAfterValidation) {
        assertInitializationAllowed();
        this.rollbackAfterValidation = rollbackAfterValidation;
    }

    // ----------------------------------------------------------------------
    // Instrumentation Methods

    // ----------------------------------------------------------------------
    // DataSource implementation

    /**
     * Attempt to establish a database connection.
     */
    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(null, null);
    }

    /**
     * Attempt to retrieve a database connection using {@link #getPooledConnectionAndInfo(String, String)}
     * with the provided username and password.  The password on the {@link PooledConnectionAndInfo}
     * instance returned by <code>getPooledConnectionAndInfo</code> is compared to the <code>password</code>
     * parameter.  If the comparison fails, a database connection using the supplied username and password
     * is attempted.  If the connection attempt fails, an SQLException is thrown, indicating that the given password
     * did not match the password used to create the pooled connection.  If the connection attempt succeeds, this
     * means that the database password has been changed.  In this case, the <code>PooledConnectionAndInfo</code>
     * instance retrieved with the old password is destroyed and the <code>getPooledConnectionAndInfo</code> is
     * repeatedly invoked until a <code>PooledConnectionAndInfo</code> instance with the new password is returned.
     *
     */
    @Override
    public Connection getConnection(String username, String password)
            throws SQLException {
        if (instanceKey == null) {
            throw new SQLException("Must set the ConnectionPoolDataSource "
                    + "through setDataSourceName or setConnectionPoolDataSource"
                    + " before calling getConnection.");
        }
        getConnectionCalled = true;
        PooledConnectionAndInfo info = null;
        try {
            info = getPooledConnectionAndInfo(username, password);
        } catch (NoSuchElementException e) {
            closeDueToException(info);
            throw new SQLException("Cannot borrow connection from pool", e);
        } catch (RuntimeException e) {
            closeDueToException(info);
            throw e;
        } catch (SQLException e) {
            closeDueToException(info);
            throw e;
        } catch (Exception e) {
            closeDueToException(info);
            throw new SQLException("Cannot borrow connection from pool", e);
        }

        // Password on PooledConnectionAndInfo does not match
        if (!(null == password ? null == info.getPassword()
                : password.equals(info.getPassword()))) {
            try { // See if password has changed by attempting connection
                testCPDS(username, password);
            } catch (SQLException ex) {
                // Password has not changed, so refuse client, but return connection to the pool
                closeDueToException(info);
                throw new SQLException("Given password did not match password used"
                                       + " to create the PooledConnection.");
            } catch (javax.naming.NamingException ne) {
                throw new SQLException("NamingException encountered connecting to database", ne);
            }
            /*
             * Password must have changed -> destroy connection and keep retrying until we get a new, good one,
             * destroying any idle connections with the old passowrd as we pull them from the pool.
             */
            final UserPassKey upkey = info.getUserPassKey();
            final PooledConnectionManager manager = getConnectionManager(upkey);
            manager.invalidate(info.getPooledConnection()); // Destroy and remove from pool
            manager.setPassword(upkey.getPassword()); // Reset the password on the factory if using CPDSConnectionFactory
            info = null;
            for (int i = 0; i < 10; i++) { // Bound the number of retries - only needed if bad instances return
                try {
                    info = getPooledConnectionAndInfo(username, password);
                } catch (NoSuchElementException e) {
                    closeDueToException(info);
                    throw new SQLException("Cannot borrow connection from pool", e);
                } catch (RuntimeException e) {
                    closeDueToException(info);
                    throw e;
                } catch (SQLException e) {
                    closeDueToException(info);
                    throw e;
                } catch (Exception e) {
                    closeDueToException(info);
                    throw new SQLException("Cannot borrow connection from pool", e);
                }
                if (info != null && password != null && password.equals(info.getPassword())) {
                    break;
                } else {
                    if (info != null) {
                        manager.invalidate(info.getPooledConnection());
                    }
                    info = null;
                }
            }
            if (info == null) {
                throw new SQLException("Cannot borrow connection from pool - password change failure.");
            }
        }

        Connection con = info.getPooledConnection().getConnection();
        try {
            setupDefaults(con, username);
            con.clearWarnings();
            return con;
        } catch (SQLException ex) {
            try {
                con.close();
            } catch (Exception exc) {
                getLogWriter().println(
                     "ignoring exception during close: " + exc);
            }
            throw ex;
        }
    }

    protected abstract PooledConnectionAndInfo
        getPooledConnectionAndInfo(String username, String password)
        throws SQLException;

    protected abstract void setupDefaults(Connection con, String username)
        throws SQLException;


    private void closeDueToException(PooledConnectionAndInfo info) {
        if (info != null) {
            try {
                info.getPooledConnection().getConnection().close();
            } catch (Exception e) {
                // do not throw this exception because we are in the middle
                // of handling another exception.  But record it because
                // it potentially leaks connections from the pool.
                getLogWriter().println("[ERROR] Could not return connection to "
                    + "pool during exception handling. " + e.getMessage());
            }
        }
    }

    protected ConnectionPoolDataSource
        testCPDS(String username, String password)
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
            Object ds = ctx.lookup(dataSourceName);
            if (ds instanceof ConnectionPoolDataSource) {
                cpds = (ConnectionPoolDataSource) ds;
            } else {
                throw new SQLException("Illegal configuration: "
                    + "DataSource " + dataSourceName
                    + " (" + ds.getClass().getName() + ")"
                    + " doesn't implement javax.sql.ConnectionPoolDataSource");
            }
        }

        // try to get a connection with the supplied username/password
        PooledConnection conn = null;
        try {
            if (username != null) {
                conn = cpds.getPooledConnection(username, password);
            }
            else {
                conn = cpds.getPooledConnection();
            }
            if (conn == null) {
                throw new SQLException(
                    "Cannot connect using the supplied username/password");
            }
        }
        finally {
            if (conn != null) {
                try {
                    conn.close();
                }
                catch (SQLException e) {
                    // at least we could connect
                }
            }
        }
        return cpds;
    }

    protected byte whenExhaustedAction(int maxActive, int maxWait) {
        byte whenExhausted = GenericObjectPool.WHEN_EXHAUSTED_BLOCK;
        if (maxActive <= 0) {
            whenExhausted = GenericObjectPool.WHEN_EXHAUSTED_GROW;
        } else if (maxWait == 0) {
            whenExhausted = GenericObjectPool.WHEN_EXHAUSTED_FAIL;
        }
        return whenExhausted;
    }

    // ----------------------------------------------------------------------
    // Referenceable implementation

    /**
     * Retrieves the Reference of this object.
     * <strong>Note:</strong> <code>InstanceKeyDataSource</code> subclasses
     * should override this method. The implementaion included below
     * is not robust and will be removed at the next major version DBCP
     * release.
     *
     * @return The non-null Reference of this object.
     * @exception NamingException If a naming exception was encountered
     *      while retrieving the reference.
     */
    // TODO: Remove the implementation of this method at next major
    // version release.

    @Override
    public Reference getReference() throws NamingException {
        final String className = getClass().getName();
        final String factoryName = className + "Factory"; // XXX: not robust
        Reference ref = new Reference(className, factoryName, null);
        ref.add(new StringRefAddr("instanceKey", instanceKey));
        return ref;
    }
}

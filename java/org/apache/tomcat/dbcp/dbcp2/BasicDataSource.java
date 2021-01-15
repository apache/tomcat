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

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.sql.DataSource;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.impl.AbandonedConfig;
import org.apache.tomcat.dbcp.pool2.impl.BaseObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPoolConfig;

/**
 * <p>
 * Basic implementation of <code>javax.sql.DataSource</code> that is configured via JavaBeans properties. This is not
 * the only way to combine the <em>commons-dbcp2</em> and <em>commons-pool2</em> packages, but provides a "one stop
 * shopping" solution for basic requirements.
 * </p>
 *
 * @since 2.0
 */
public class BasicDataSource implements DataSource, BasicDataSourceMXBean, MBeanRegistration, AutoCloseable {

    private static final Log log = LogFactory.getLog(BasicDataSource.class);

    static {
        // Attempt to prevent deadlocks - see DBCP - 272
        DriverManager.getDrivers();
        try {
            // Load classes now to prevent AccessControlExceptions later
            // A number of classes are loaded when getConnection() is called
            // but the following classes are not loaded and therefore require
            // explicit loading.
            if (Utils.IS_SECURITY_ENABLED) {
                final ClassLoader loader = BasicDataSource.class.getClassLoader();
                final String dbcpPackageName = BasicDataSource.class.getPackage().getName();
                loader.loadClass(dbcpPackageName + ".DelegatingCallableStatement");
                loader.loadClass(dbcpPackageName + ".DelegatingDatabaseMetaData");
                loader.loadClass(dbcpPackageName + ".DelegatingPreparedStatement");
                loader.loadClass(dbcpPackageName + ".DelegatingResultSet");
                loader.loadClass(dbcpPackageName + ".PoolableCallableStatement");
                loader.loadClass(dbcpPackageName + ".PoolablePreparedStatement");
                loader.loadClass(dbcpPackageName + ".PoolingConnection$StatementType");
                loader.loadClass(dbcpPackageName + ".PStmtKey");

                final String poolPackageName = PooledObject.class.getPackage().getName();
                loader.loadClass(poolPackageName + ".impl.LinkedBlockingDeque$Node");
                loader.loadClass(poolPackageName + ".impl.GenericKeyedObjectPool$ObjectDeque");
            }
        } catch (final ClassNotFoundException cnfe) {
            throw new IllegalStateException("Unable to pre-load classes", cnfe);
        }
    }

    protected static void validateConnectionFactory(final PoolableConnectionFactory connectionFactory)
            throws Exception {
        PoolableConnection conn = null;
        PooledObject<PoolableConnection> p = null;
        try {
            p = connectionFactory.makeObject();
            conn = p.getObject();
            connectionFactory.activateObject(p);
            connectionFactory.validateConnection(conn);
            connectionFactory.passivateObject(p);
        } finally {
            if (p != null) {
                connectionFactory.destroyObject(p);
            }
        }
    }

    /**
     * The default auto-commit state of connections created by this pool.
     */
    private volatile Boolean defaultAutoCommit;

    /**
     * The default read-only state of connections created by this pool.
     */
    private transient Boolean defaultReadOnly;

    /**
     * The default TransactionIsolation state of connections created by this pool.
     */
    private volatile int defaultTransactionIsolation = PoolableConnectionFactory.UNKNOWN_TRANSACTION_ISOLATION;

    private Integer defaultQueryTimeoutSeconds;

    /**
     * The default "catalog" of connections created by this pool.
     */
    private volatile String defaultCatalog;

    /**
     * The default "schema" of connections created by this pool.
     */
    private volatile String defaultSchema;

    /**
     * The property that controls if the pooled connections cache some state rather than query the database for current
     * state to improve performance.
     */
    private boolean cacheState = true;

    /**
     * The instance of the JDBC Driver to use.
     */
    private Driver driver;

    /**
     * The fully qualified Java class name of the JDBC driver to be used.
     */
    private String driverClassName;

    /**
     * The class loader instance to use to load the JDBC driver. If not specified, {@link Class#forName(String)} is used
     * to load the JDBC driver. If specified, {@link Class#forName(String, boolean, ClassLoader)} is used.
     */
    private ClassLoader driverClassLoader;

    /**
     * True means that borrowObject returns the most recently used ("last in") connection in the pool (if there are idle
     * connections available). False means that the pool behaves as a FIFO queue - connections are taken from the idle
     * instance pool in the order that they are returned to the pool.
     */
    private boolean lifo = BaseObjectPoolConfig.DEFAULT_LIFO;

    /**
     * The maximum number of active connections that can be allocated from this pool at the same time, or negative for
     * no limit.
     */
    private int maxTotal = GenericObjectPoolConfig.DEFAULT_MAX_TOTAL;

    /**
     * The maximum number of connections that can remain idle in the pool, without extra ones being destroyed, or
     * negative for no limit. If maxIdle is set too low on heavily loaded systems it is possible you will see
     * connections being closed and almost immediately new connections being opened. This is a result of the active
     * threads momentarily closing connections faster than they are opening them, causing the number of idle connections
     * to rise above maxIdle. The best value for maxIdle for heavily loaded system will vary but the default is a good
     * starting point.
     */
    private int maxIdle = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;

    /**
     * The minimum number of active connections that can remain idle in the pool, without extra ones being created when
     * the evictor runs, or 0 to create none. The pool attempts to ensure that minIdle connections are available when
     * the idle object evictor runs. The value of this property has no effect unless
     * {@link #timeBetweenEvictionRunsMillis} has a positive value.
     */
    private int minIdle = GenericObjectPoolConfig.DEFAULT_MIN_IDLE;

    /**
     * The initial number of connections that are created when the pool is started.
     */
    private int initialSize = 0;

    /**
     * The maximum number of milliseconds that the pool will wait (when there are no available connections) for a
     * connection to be returned before throwing an exception, or <= 0 to wait indefinitely.
     */
    private long maxWaitMillis = BaseObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS;

    /**
     * Prepared statement pooling for this pool. When this property is set to <code>true</code> both PreparedStatements
     * and CallableStatements are pooled.
     */
    private boolean poolPreparedStatements = false;

    private boolean clearStatementPoolOnReturn = false;

    /**
     * <p>
     * The maximum number of open statements that can be allocated from the statement pool at the same time, or negative
     * for no limit. Since a connection usually only uses one or two statements at a time, this is mostly used to help
     * detect resource leaks.
     * </p>
     * <p>
     * Note: As of version 1.3, CallableStatements (those produced by {@link Connection#prepareCall}) are pooled along
     * with PreparedStatements (produced by {@link Connection#prepareStatement}) and
     * <code>maxOpenPreparedStatements</code> limits the total number of prepared or callable statements that may be in
     * use at a given time.
     * </p>
     */
    private int maxOpenPreparedStatements = GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL;

    /**
     * The indication of whether objects will be validated as soon as they have been created by the pool. If the object
     * fails to validate, the borrow operation that triggered the creation will fail.
     */
    private boolean testOnCreate = false;

    /**
     * The indication of whether objects will be validated before being borrowed from the pool. If the object fails to
     * validate, it will be dropped from the pool, and we will attempt to borrow another.
     */
    private boolean testOnBorrow = true;

    /**
     * The indication of whether objects will be validated before being returned to the pool.
     */
    private boolean testOnReturn = false;

    /**
     * The number of milliseconds to sleep between runs of the idle object evictor thread. When non-positive, no idle
     * object evictor thread will be run.
     */
    private long timeBetweenEvictionRunsMillis = BaseObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    /**
     * The number of objects to examine during each run of the idle object evictor thread (if any).
     */
    private int numTestsPerEvictionRun = BaseObjectPoolConfig.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    /**
     * The minimum amount of time an object may sit idle in the pool before it is eligible for eviction by the idle
     * object evictor (if any).
     */
    private long minEvictableIdleTimeMillis = BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * The minimum amount of time a connection may sit idle in the pool before it is eligible for eviction by the idle
     * object evictor, with the extra condition that at least "minIdle" connections remain in the pool. Note that
     * {@code minEvictableIdleTimeMillis} takes precedence over this parameter. See
     * {@link #getSoftMinEvictableIdleTimeMillis()}.
     */
    private long softMinEvictableIdleTimeMillis = BaseObjectPoolConfig.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    private String evictionPolicyClassName = BaseObjectPoolConfig.DEFAULT_EVICTION_POLICY_CLASS_NAME;

    /**
     * The indication of whether objects will be validated by the idle object evictor (if any). If an object fails to
     * validate, it will be dropped from the pool.
     */
    private boolean testWhileIdle = false;

    /**
     * The connection password to be passed to our JDBC driver to establish a connection.
     */
    private volatile String password;

    /**
     * The connection URL to be passed to our JDBC driver to establish a connection.
     */
    private String url;

    /**
     * The connection user name to be passed to our JDBC driver to establish a connection.
     */
    private String userName;

    /**
     * The SQL query that will be used to validate connections from this pool before returning them to the caller. If
     * specified, this query <strong>MUST</strong> be an SQL SELECT statement that returns at least one row. If not
     * specified, {@link Connection#isValid(int)} will be used to validate connections.
     */
    private volatile String validationQuery;

    /**
     * Timeout in seconds before connection validation queries fail.
     */
    private volatile int validationQueryTimeoutSeconds = -1;

    /**
     * The fully qualified Java class name of a {@link ConnectionFactory} implementation.
     */
    private String connectionFactoryClassName;

    /**
     * These SQL statements run once after a Connection is created.
     * <p>
     * This property can be used for example to run ALTER SESSION SET NLS_SORT=XCYECH in an Oracle Database only once
     * after connection creation.
     * </p>
     */
    private volatile List<String> connectionInitSqls;

    /**
     * Controls access to the underlying connection.
     */
    private boolean accessToUnderlyingConnectionAllowed = false;

    private long maxConnLifetimeMillis = -1;

    private boolean logExpiredConnections = true;

    private String jmxName;

    private boolean autoCommitOnReturn = true;

    private boolean rollbackOnReturn = true;

    private volatile Set<String> disconnectionSqlCodes;

    private boolean fastFailValidation;

    /**
     * The object pool that internally manages our connections.
     */
    private volatile GenericObjectPool<PoolableConnection> connectionPool;

    /**
     * The connection properties that will be sent to our JDBC driver when establishing new connections.
     * <strong>NOTE</strong> - The "user" and "password" properties will be passed explicitly, so they do not need to be
     * included here.
     */
    private Properties connectionProperties = new Properties();

    /**
     * The data source we will use to manage connections. This object should be acquired <strong>ONLY</strong> by calls
     * to the <code>createDataSource()</code> method.
     */
    private volatile DataSource dataSource;

    /**
     * The PrintWriter to which log messages should be directed.
     */
    private volatile PrintWriter logWriter = new PrintWriter(
            new OutputStreamWriter(System.out, StandardCharsets.UTF_8));

    private AbandonedConfig abandonedConfig;

    private boolean closed;

    /**
     * Actual name under which this component has been registered.
     */
    private ObjectNameWrapper registeredJmxObjectName;

    /**
     * Adds a custom connection property to the set that will be passed to our JDBC driver. This <strong>MUST</strong>
     * be called before the first connection is retrieved (along with all the other configuration property setters).
     * Calls to this method after the connection pool has been initialized have no effect.
     *
     * @param name  Name of the custom connection property
     * @param value Value of the custom connection property
     */
    public void addConnectionProperty(final String name, final String value) {
        connectionProperties.put(name, value);
    }

    /**
     * <p>
     * Closes and releases all idle connections that are currently stored in the connection pool associated with this
     * data source.
     * </p>
     * <p>
     * Connections that are checked out to clients when this method is invoked are not affected. When client
     * applications subsequently invoke {@link Connection#close()} to return these connections to the pool, the
     * underlying JDBC connections are closed.
     * </p>
     * <p>
     * Attempts to acquire connections using {@link #getConnection()} after this method has been invoked result in
     * SQLExceptions.  To reopen a datasource that has been closed using this method, use {@link #start()}.
     * </p>
     * <p>
     * This method is idempotent - i.e., closing an already closed BasicDataSource has no effect and does not generate
     * exceptions.
     * </p>
     *
     * @throws SQLException if an error occurs closing idle connections
     */
    @Override
    public synchronized void close() throws SQLException {
        if (registeredJmxObjectName != null) {
            registeredJmxObjectName.unregisterMBean();
            registeredJmxObjectName = null;
        }
        closed = true;
        final GenericObjectPool<?> oldPool = connectionPool;
        connectionPool = null;
        dataSource = null;
        try {
            if (oldPool != null) {
                oldPool.close();
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException(Utils.getMessage("pool.close.fail"), e);
        }
    }

    /**
     * Closes the connection pool, silently swallowing any exception that occurs.
     */
    private void closeConnectionPool() {
        final GenericObjectPool<?> oldPool = connectionPool;
        connectionPool = null;
        try {
            if (oldPool != null) {
                oldPool.close();
            }
        } catch (final Exception e) {
            /* Ignore */
        }
    }

    /**
     * Creates a JDBC connection factory for this data source. The JDBC driver is loaded using the following algorithm:
     * <ol>
     * <li>If a Driver instance has been specified via {@link #setDriver(Driver)} use it</li>
     * <li>If no Driver instance was specified and {@link #driverClassName} is specified that class is loaded using the
     * {@link ClassLoader} of this class or, if {@link #driverClassLoader} is set, {@link #driverClassName} is loaded
     * with the specified {@link ClassLoader}.</li>
     * <li>If {@link #driverClassName} is specified and the previous attempt fails, the class is loaded using the
     * context class loader of the current thread.</li>
     * <li>If a driver still isn't loaded one is loaded via the {@link DriverManager} using the specified {@link #url}.
     * </ol>
     * <p>
     * This method exists so subclasses can replace the implementation class.
     * </p>
     *
     * @return A new connection factory.
     *
     * @throws SQLException If the connection factory cannot be created
     */
    protected ConnectionFactory createConnectionFactory() throws SQLException {
        // Load the JDBC driver class
        return ConnectionFactoryFactory.createConnectionFactory(this, DriverFactory.createDriver(this));
    }


    /**
     * Creates a connection pool for this datasource. This method only exists so subclasses can replace the
     * implementation class.
     * <p>
     * This implementation configures all pool properties other than timeBetweenEvictionRunsMillis. Setting that
     * property is deferred to {@link #startPoolMaintenance()}, since setting timeBetweenEvictionRunsMillis to a
     * positive value causes {@link GenericObjectPool}'s eviction timer to be started.
     * </p>
     *
     * @param factory The factory to use to create new connections for this pool.
     */
    protected void createConnectionPool(final PoolableConnectionFactory factory) {
        // Create an object pool to contain our active connections
        final GenericObjectPoolConfig<PoolableConnection> config = new GenericObjectPoolConfig<>();
        updateJmxName(config);
        // Disable JMX on the underlying pool if the DS is not registered:
        config.setJmxEnabled(registeredJmxObjectName != null);
        final GenericObjectPool<PoolableConnection> gop = createObjectPool(factory, config, abandonedConfig);
        gop.setMaxTotal(maxTotal);
        gop.setMaxIdle(maxIdle);
        gop.setMinIdle(minIdle);
        gop.setMaxWaitMillis(maxWaitMillis);
        gop.setTestOnCreate(testOnCreate);
        gop.setTestOnBorrow(testOnBorrow);
        gop.setTestOnReturn(testOnReturn);
        gop.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        gop.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        gop.setSoftMinEvictableIdleTimeMillis(softMinEvictableIdleTimeMillis);
        gop.setTestWhileIdle(testWhileIdle);
        gop.setLifo(lifo);
        gop.setSwallowedExceptionListener(new SwallowedExceptionLogger(log, logExpiredConnections));
        gop.setEvictionPolicyClassName(evictionPolicyClassName);
        factory.setPool(gop);
        connectionPool = gop;
    }

    /**
     * <p>
     * Creates (if necessary) and return the internal data source we are using to manage our connections.
     * </p>
     *
     * @return The current internal DataSource or a newly created instance if it has not yet been created.
     * @throws SQLException if the object pool cannot be created.
     */
    protected DataSource createDataSource() throws SQLException {
        if (closed) {
            throw new SQLException("Data source is closed");
        }

        // Return the pool if we have already created it
        // This is double-checked locking. This is safe since dataSource is
        // volatile and the code is targeted at Java 5 onwards.
        if (dataSource != null) {
            return dataSource;
        }
        synchronized (this) {
            if (dataSource != null) {
                return dataSource;
            }
            jmxRegister();

            // create factory which returns raw physical connections
            final ConnectionFactory driverConnectionFactory = createConnectionFactory();

            // Set up the poolable connection factory
            boolean success = false;
            final PoolableConnectionFactory poolableConnectionFactory;
            try {
                poolableConnectionFactory = createPoolableConnectionFactory(driverConnectionFactory);
                poolableConnectionFactory.setPoolStatements(poolPreparedStatements);
                poolableConnectionFactory.setMaxOpenPreparedStatements(maxOpenPreparedStatements);
                success = true;
            } catch (final SQLException | RuntimeException se) {
                throw se;
            } catch (final Exception ex) {
                throw new SQLException("Error creating connection factory", ex);
            }

            if (success) {
                // create a pool for our connections
                createConnectionPool(poolableConnectionFactory);
            }

            // Create the pooling data source to manage connections
            DataSource newDataSource;
            success = false;
            try {
                newDataSource = createDataSourceInstance();
                newDataSource.setLogWriter(logWriter);
                success = true;
            } catch (final SQLException | RuntimeException se) {
                throw se;
            } catch (final Exception ex) {
                throw new SQLException("Error creating datasource", ex);
            } finally {
                if (!success) {
                    closeConnectionPool();
                }
            }

            // If initialSize > 0, preload the pool
            try {
                for (int i = 0; i < initialSize; i++) {
                    connectionPool.addObject();
                }
            } catch (final Exception e) {
                closeConnectionPool();
                throw new SQLException("Error preloading the connection pool", e);
            }

            // If timeBetweenEvictionRunsMillis > 0, start the pool's evictor
            // task
            startPoolMaintenance();

            dataSource = newDataSource;
            return dataSource;
        }
    }

    /**
     * Creates the actual data source instance. This method only exists so that subclasses can replace the
     * implementation class.
     *
     * @throws SQLException if unable to create a datasource instance
     *
     * @return A new DataSource instance
     */
    protected DataSource createDataSourceInstance() throws SQLException {
        final PoolingDataSource<PoolableConnection> pds = new PoolingDataSource<>(connectionPool);
        pds.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
        return pds;
    }

    /**
     * Creates an object pool used to provide pooling support for {@link Connection JDBC connections}.
     *
     * @param factory         the object factory
     * @param poolConfig      the object pool configuration
     * @param abandonedConfig the abandoned objects configuration
     * @return a non-null instance
     */
    protected GenericObjectPool<PoolableConnection> createObjectPool(final PoolableConnectionFactory factory,
            final GenericObjectPoolConfig<PoolableConnection> poolConfig, final AbandonedConfig abandonedConfig) {
        final GenericObjectPool<PoolableConnection> gop;
        if (abandonedConfig != null && (abandonedConfig.getRemoveAbandonedOnBorrow()
                || abandonedConfig.getRemoveAbandonedOnMaintenance())) {
            gop = new GenericObjectPool<>(factory, poolConfig, abandonedConfig);
        } else {
            gop = new GenericObjectPool<>(factory, poolConfig);
        }
        return gop;
    }

    /**
     * Creates the PoolableConnectionFactory and attaches it to the connection pool. This method only exists so
     * subclasses can replace the default implementation.
     *
     * @param driverConnectionFactory JDBC connection factory
     * @throws SQLException if an error occurs creating the PoolableConnectionFactory
     *
     * @return A new PoolableConnectionFactory configured with the current configuration of this BasicDataSource
     */
    protected PoolableConnectionFactory createPoolableConnectionFactory(final ConnectionFactory driverConnectionFactory)
            throws SQLException {
        PoolableConnectionFactory connectionFactory = null;
        try {
            connectionFactory = new PoolableConnectionFactory(driverConnectionFactory,
                    ObjectNameWrapper.unwrap(registeredJmxObjectName));
            connectionFactory.setValidationQuery(validationQuery);
            connectionFactory.setValidationQueryTimeout(validationQueryTimeoutSeconds);
            connectionFactory.setConnectionInitSql(connectionInitSqls);
            connectionFactory.setDefaultReadOnly(defaultReadOnly);
            connectionFactory.setDefaultAutoCommit(defaultAutoCommit);
            connectionFactory.setDefaultTransactionIsolation(defaultTransactionIsolation);
            connectionFactory.setDefaultCatalog(defaultCatalog);
            connectionFactory.setDefaultSchema(defaultSchema);
            connectionFactory.setCacheState(cacheState);
            connectionFactory.setPoolStatements(poolPreparedStatements);
            connectionFactory.setClearStatementPoolOnReturn(clearStatementPoolOnReturn);
            connectionFactory.setMaxOpenPreparedStatements(maxOpenPreparedStatements);
            connectionFactory.setMaxConnLifetimeMillis(maxConnLifetimeMillis);
            connectionFactory.setRollbackOnReturn(getRollbackOnReturn());
            connectionFactory.setAutoCommitOnReturn(getAutoCommitOnReturn());
            connectionFactory.setDefaultQueryTimeout(getDefaultQueryTimeout());
            connectionFactory.setFastFailValidation(fastFailValidation);
            connectionFactory.setDisconnectionSqlCodes(disconnectionSqlCodes);
            validateConnectionFactory(connectionFactory);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Cannot create PoolableConnectionFactory (" + e.getMessage() + ")", e);
        }
        return connectionFactory;
    }

    /**
     * Manually evicts idle connections
     *
     * @throws Exception when there is a problem evicting idle objects.
     */
    public void evict() throws Exception {
        if (connectionPool != null) {
            connectionPool.evict();
        }
    }

    /**
     * Gets the print writer used by this configuration to log information on abandoned objects.
     *
     * @return The print writer used by this configuration to log information on abandoned objects.
     */
    public PrintWriter getAbandonedLogWriter() {
        return abandonedConfig == null ? null : abandonedConfig.getLogWriter();
    }

    /**
     * If the connection pool implements {@link org.apache.tomcat.dbcp.pool2.UsageTracking UsageTracking}, should the
     * connection pool record a stack trace every time a method is called on a pooled connection and retain the most
     * recent stack trace to aid debugging of abandoned connections?
     *
     * @return <code>true</code> if usage tracking is enabled
     */
    @Override
    public boolean getAbandonedUsageTracking() {
        return abandonedConfig != null && abandonedConfig.getUseUsageTracking();
    }

    /**
     * Returns the value of the flag that controls whether or not connections being returned to the pool will be checked
     * and configured with {@link Connection#setAutoCommit(boolean) Connection.setAutoCommit(true)} if the auto commit
     * setting is {@code false} when the connection is returned. It is <code>true</code> by default.
     *
     * @return Whether or not connections being returned to the pool will be checked and configured with auto-commit.
     */
    public boolean getAutoCommitOnReturn() {
        return autoCommitOnReturn;
    }

    /**
     * Returns the state caching flag.
     *
     * @return the state caching flag
     */
    @Override
    public boolean getCacheState() {
        return cacheState;
    }

    /**
     * Creates (if necessary) and return a connection to the database.
     *
     * @throws SQLException if a database access error occurs
     * @return a database connection
     */
    @Override
    public Connection getConnection() throws SQLException {
        if (Utils.IS_SECURITY_ENABLED) {
            final PrivilegedExceptionAction<Connection> action = () -> createDataSource().getConnection();
            try {
                return AccessController.doPrivileged(action);
            } catch (final PrivilegedActionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof SQLException) {
                    throw (SQLException) cause;
                }
                throw new SQLException(e);
            }
        }
        return createDataSource().getConnection();
    }

    /**
     * <strong>BasicDataSource does NOT support this method.</strong>
     *
     * @param user Database user on whose behalf the Connection is being made
     * @param pass The database user's password
     *
     * @throws UnsupportedOperationException always thrown.
     * @throws SQLException                  if a database access error occurs
     * @return nothing - always throws UnsupportedOperationException
     */
    @Override
    public Connection getConnection(final String user, final String pass) throws SQLException {
        // This method isn't supported by the PoolingDataSource returned by the
        // createDataSource
        throw new UnsupportedOperationException("Not supported by BasicDataSource");
    }

    /**
     * Returns the ConnectionFactoryClassName that has been configured for use by this pool.
     * <p>
     * Note: This getter only returns the last value set by a call to {@link #setConnectionFactoryClassName(String)}.
     * </p>
     *
     * @return the ConnectionFactoryClassName that has been configured for use by this pool.
     * @since 2.7.0
     */
    public String getConnectionFactoryClassName() {
        return this.connectionFactoryClassName;
    }

    /**
     * Returns the list of SQL statements executed when a physical connection is first created. Returns an empty list if
     * there are no initialization statements configured.
     *
     * @return initialization SQL statements
     */
    public List<String> getConnectionInitSqls() {
        final List<String> result = connectionInitSqls;
        return result == null ? Collections.emptyList() : result;
    }

    /**
     * Provides the same data as {@link #getConnectionInitSqls()} but in an array so it is accessible via JMX.
     */
    @Override
    public String[] getConnectionInitSqlsAsArray() {
        return getConnectionInitSqls().toArray(Utils.EMPTY_STRING_ARRAY);
    }

    protected GenericObjectPool<PoolableConnection> getConnectionPool() {
        return connectionPool;
    }

    Properties getConnectionProperties() {
        return connectionProperties;
    }

    /**
     * Returns the default auto-commit property.
     *
     * @return true if default auto-commit is enabled
     */
    @Override
    public Boolean getDefaultAutoCommit() {
        return defaultAutoCommit;
    }

    /**
     * Returns the default catalog.
     *
     * @return the default catalog
     */
    @Override
    public String getDefaultCatalog() {
        return this.defaultCatalog;
    }

    /**
     * Gets the default query timeout that will be used for {@link java.sql.Statement Statement}s created from this
     * connection. <code>null</code> means that the driver default will be used.
     *
     * @return The default query timeout in seconds.
     */
    public Integer getDefaultQueryTimeout() {
        return defaultQueryTimeoutSeconds;
    }

    /**
     * Returns the default readOnly property.
     *
     * @return true if connections are readOnly by default
     */
    @Override
    public Boolean getDefaultReadOnly() {
        return defaultReadOnly;
    }

    /**
     * Returns the default schema.
     *
     * @return the default schema.
     * @since 2.5.0
     */
    @Override
    public String getDefaultSchema() {
        return this.defaultSchema;
    }

    /**
     * Returns the default transaction isolation state of returned connections.
     *
     * @return the default value for transaction isolation state
     * @see Connection#getTransactionIsolation
     */
    @Override
    public int getDefaultTransactionIsolation() {
        return this.defaultTransactionIsolation;
    }

    /**
     * Returns the set of SQL_STATE codes considered to signal fatal conditions.
     *
     * @return fatal disconnection state codes
     * @see #setDisconnectionSqlCodes(Collection)
     * @since 2.1
     */
    public Set<String> getDisconnectionSqlCodes() {
        final Set<String> result = disconnectionSqlCodes;
        return result == null ? Collections.emptySet() : result;
    }

    /**
     * Provides the same data as {@link #getDisconnectionSqlCodes} but in an array so it is accessible via JMX.
     *
     * @since 2.1
     */
    @Override
    public String[] getDisconnectionSqlCodesAsArray() {
        return getDisconnectionSqlCodes().toArray(Utils.EMPTY_STRING_ARRAY);
    }

    /**
     * Returns the JDBC Driver that has been configured for use by this pool.
     * <p>
     * Note: This getter only returns the last value set by a call to {@link #setDriver(Driver)}. It does not return any
     * driver instance that may have been created from the value set via {@link #setDriverClassName(String)}.
     * </p>
     *
     * @return the JDBC Driver that has been configured for use by this pool
     */
    public synchronized Driver getDriver() {
        return driver;
    }

    /**
     * Returns the class loader specified for loading the JDBC driver. Returns <code>null</code> if no class loader has
     * been explicitly specified.
     * <p>
     * Note: This getter only returns the last value set by a call to {@link #setDriverClassLoader(ClassLoader)}. It
     * does not return the class loader of any driver that may have been set via {@link #setDriver(Driver)}.
     * </p>
     *
     * @return The class loader specified for loading the JDBC driver.
     */
    public synchronized ClassLoader getDriverClassLoader() {
        return this.driverClassLoader;
    }

    /**
     * Returns the JDBC driver class name.
     * <p>
     * Note: This getter only returns the last value set by a call to {@link #setDriverClassName(String)}. It does not
     * return the class name of any driver that may have been set via {@link #setDriver(Driver)}.
     * </p>
     *
     * @return the JDBC driver class name
     */
    @Override
    public synchronized String getDriverClassName() {
        return this.driverClassName;
    }

    /**
     * Returns the value of the flag that controls whether or not connections being returned to the pool will be checked
     * and configured with {@link Connection#setAutoCommit(boolean) Connection.setAutoCommit(true)} if the auto commit
     * setting is {@code false} when the connection is returned. It is <code>true</code> by default.
     *
     * @return Whether or not connections being returned to the pool will be checked and configured with auto-commit.
     * @deprecated Use {@link #getAutoCommitOnReturn()}.
     */
    @Deprecated
    public boolean getEnableAutoCommitOnReturn() {
        return autoCommitOnReturn;
    }

    /**
     * Gets the EvictionPolicy implementation in use with this connection pool.
     *
     * @return The EvictionPolicy implementation in use with this connection pool.
     */
    public synchronized String getEvictionPolicyClassName() {
        return evictionPolicyClassName;
    }

    /**
     * True means that validation will fail immediately for connections that have previously thrown SQLExceptions with
     * SQL_STATE indicating fatal disconnection errors.
     *
     * @return true if connections created by this datasource will fast fail validation.
     * @see #setDisconnectionSqlCodes(Collection)
     * @since 2.1
     */
    @Override
    public boolean getFastFailValidation() {
        return fastFailValidation;
    }

    /**
     * Returns the initial size of the connection pool.
     *
     * @return the number of connections created when the pool is initialized
     */
    @Override
    public synchronized int getInitialSize() {
        return this.initialSize;
    }

    /**
     * Returns the JMX name that has been requested for this DataSource. If the requested name is not valid, an
     * alternative may be chosen.
     *
     * @return The JMX name that has been requested for this DataSource.
     */
    public String getJmxName() {
        return jmxName;
    }

    /**
     * Returns the LIFO property.
     *
     * @return true if connection pool behaves as a LIFO queue.
     */
    @Override
    public synchronized boolean getLifo() {
        return this.lifo;
    }

    /**
     * <p>
     * Flag to log stack traces for application code which abandoned a Statement or Connection.
     * </p>
     * <p>
     * Defaults to false.
     * </p>
     * <p>
     * Logging of abandoned Statements and Connections adds overhead for every Connection open or new Statement because
     * a stack trace has to be generated.
     * </p>
     */
    @Override
    public boolean getLogAbandoned() {
        return abandonedConfig != null && abandonedConfig.getLogAbandoned();
    }

    /**
     * When {@link #getMaxConnLifetimeMillis()} is set to limit connection lifetime, this property determines whether or
     * not log messages are generated when the pool closes connections due to maximum lifetime exceeded.
     *
     * @since 2.1
     */
    @Override
    public boolean getLogExpiredConnections() {
        return logExpiredConnections;
    }

    /**
     * <strong>BasicDataSource does NOT support this method.</strong>
     *
     * <p>
     * Returns the login timeout (in seconds) for connecting to the database.
     * </p>
     * <p>
     * Calls {@link #createDataSource()}, so has the side effect of initializing the connection pool.
     * </p>
     *
     * @throws SQLException                  if a database access error occurs
     * @throws UnsupportedOperationException If the DataSource implementation does not support the login timeout
     *                                       feature.
     * @return login timeout in seconds
     */
    @Override
    public int getLoginTimeout() throws SQLException {
        // This method isn't supported by the PoolingDataSource returned by the createDataSource
        throw new UnsupportedOperationException("Not supported by BasicDataSource");
    }

    /**
     * <p>
     * Returns the log writer being used by this data source.
     * </p>
     * <p>
     * Calls {@link #createDataSource()}, so has the side effect of initializing the connection pool.
     * </p>
     *
     * @throws SQLException if a database access error occurs
     * @return log writer in use
     */
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return createDataSource().getLogWriter();
    }

    /**
     * Returns the maximum permitted lifetime of a connection in milliseconds. A value of zero or less indicates an
     * infinite lifetime.
     */
    @Override
    public long getMaxConnLifetimeMillis() {
        return maxConnLifetimeMillis;
    }

    /**
     * <p>
     * Returns the maximum number of connections that can remain idle in the pool. Excess idle connections are destroyed
     * on return to the pool.
     * </p>
     * <p>
     * A negative value indicates that there is no limit
     * </p>
     *
     * @return the maximum number of idle connections
     */
    @Override
    public synchronized int getMaxIdle() {
        return this.maxIdle;
    }

    /**
     * Gets the value of the <code>maxOpenPreparedStatements</code> property.
     *
     * @return the maximum number of open statements
     */
    @Override
    public synchronized int getMaxOpenPreparedStatements() {
        return this.maxOpenPreparedStatements;
    }

    /**
     * <p>
     * Returns the maximum number of active connections that can be allocated at the same time.
     * </p>
     * <p>
     * A negative number means that there is no limit.
     * </p>
     *
     * @return the maximum number of active connections
     */
    @Override
    public synchronized int getMaxTotal() {
        return this.maxTotal;
    }

    /**
     * Returns the maximum number of milliseconds that the pool will wait for a connection to be returned before
     * throwing an exception. A value less than or equal to zero means the pool is set to wait indefinitely.
     *
     * @return the maxWaitMillis property value
     */
    @Override
    public synchronized long getMaxWaitMillis() {
        return this.maxWaitMillis;
    }

    /**
     * Returns the {@link #minEvictableIdleTimeMillis} property.
     *
     * @return the value of the {@link #minEvictableIdleTimeMillis} property
     * @see #minEvictableIdleTimeMillis
     */
    @Override
    public synchronized long getMinEvictableIdleTimeMillis() {
        return this.minEvictableIdleTimeMillis;
    }

    /**
     * Returns the minimum number of idle connections in the pool. The pool attempts to ensure that minIdle connections
     * are available when the idle object evictor runs. The value of this property has no effect unless
     * {@link #timeBetweenEvictionRunsMillis} has a positive value.
     *
     * @return the minimum number of idle connections
     * @see GenericObjectPool#getMinIdle()
     */
    @Override
    public synchronized int getMinIdle() {
        return this.minIdle;
    }

    /**
     * [Read Only] The current number of active connections that have been allocated from this data source.
     *
     * @return the current number of active connections
     */
    @Override
    public int getNumActive() {
        // Copy reference to avoid NPE if close happens after null check
        final GenericObjectPool<PoolableConnection> pool = connectionPool;
        return pool == null ? 0 : pool.getNumActive();
    }

    /**
     * [Read Only] The current number of idle connections that are waiting to be allocated from this data source.
     *
     * @return the current number of idle connections
     */
    @Override
    public int getNumIdle() {
        // Copy reference to avoid NPE if close happens after null check
        final GenericObjectPool<PoolableConnection> pool = connectionPool;
        return pool == null ? 0 : pool.getNumIdle();
    }

    /**
     * Returns the value of the {@link #numTestsPerEvictionRun} property.
     *
     * @return the number of objects to examine during idle object evictor runs
     * @see #numTestsPerEvictionRun
     */
    @Override
    public synchronized int getNumTestsPerEvictionRun() {
        return this.numTestsPerEvictionRun;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * Returns the password passed to the JDBC driver to establish connections.
     *
     * @return the connection password
     */
    @Override
    public String getPassword() {
        return this.password;
    }

    protected ObjectName getRegisteredJmxName() {
        return ObjectNameWrapper.unwrap(registeredJmxObjectName);
    }

    /**
     * <p>
     * Flag to remove abandoned connections if they exceed the removeAbandonedTimeout when borrowObject is invoked.
     * </p>
     * <p>
     * The default value is false.
     * </p>
     * <p>
     * If set to true a connection is considered abandoned and eligible for removal if it has not been used for more
     * than {@link #getRemoveAbandonedTimeout() removeAbandonedTimeout} seconds.
     * </p>
     * <p>
     * Abandoned connections are identified and removed when {@link #getConnection()} is invoked and all of the
     * following conditions hold:
     * </p>
     * <ul>
     * <li>{@link #getRemoveAbandonedOnBorrow()}</li>
     * <li>{@link #getNumActive()} &gt; {@link #getMaxTotal()} - 3</li>
     * <li>{@link #getNumIdle()} &lt; 2</li>
     * </ul>
     *
     * @see #getRemoveAbandonedTimeout()
     */
    @Override
    public boolean getRemoveAbandonedOnBorrow() {
        return abandonedConfig != null && abandonedConfig.getRemoveAbandonedOnBorrow();
    }

    /**
     * <p>
     * Flag to remove abandoned connections if they exceed the removeAbandonedTimeout during pool maintenance.
     * </p>
     *
     * <p>
     * The default value is false.
     * </p>
     *
     * <p>
     * If set to true a connection is considered abandoned and eligible for removal if it has not been used for more
     * than {@link #getRemoveAbandonedTimeout() removeAbandonedTimeout} seconds.
     * </p>
     *
     * @see #getRemoveAbandonedTimeout()
     */
    @Override
    public boolean getRemoveAbandonedOnMaintenance() {
        return abandonedConfig != null && abandonedConfig.getRemoveAbandonedOnMaintenance();
    }

    /**
     * <p>
     * Timeout in seconds before an abandoned connection can be removed.
     * </p>
     * <p>
     * Creating a Statement, PreparedStatement or CallableStatement or using one of these to execute a query (using one
     * of the execute methods) resets the lastUsed property of the parent connection.
     * </p>
     * <p>
     * Abandoned connection cleanup happens when:
     * </p>
     * <ul>
     * <li>{@link #getRemoveAbandonedOnBorrow()} or {@link #getRemoveAbandonedOnMaintenance()} = true</li>
     * <li>{@link #getNumIdle() numIdle} &lt; 2</li>
     * <li>{@link #getNumActive() numActive} &gt; {@link #getMaxTotal() maxTotal} - 3</li>
     * </ul>
     * <p>
     * The default value is 300 seconds.
     * </p>
     */
    @Override
    public int getRemoveAbandonedTimeout() {
        return abandonedConfig == null ? 300 : abandonedConfig.getRemoveAbandonedTimeout();
    }

    /**
     * Gets the current value of the flag that controls whether a connection will be rolled back when it is returned to
     * the pool if auto commit is not enabled and the connection is not read only.
     *
     * @return whether a connection will be rolled back when it is returned to the pool.
     */
    public boolean getRollbackOnReturn() {
        return rollbackOnReturn;
    }

    /**
     * <p>
     * Returns the minimum amount of time a connection may sit idle in the pool before it is eligible for eviction by
     * the idle object evictor, with the extra condition that at least "minIdle" connections remain in the pool.
     * </p>
     *
     * <p>
     * When {@link #getMinEvictableIdleTimeMillis() minEvictableIdleTimeMillis} is set to a positive value,
     * minEvictableIdleTimeMillis is examined first by the idle connection evictor - i.e. when idle connections are
     * visited by the evictor, idle time is first compared against {@code minEvictableIdleTimeMillis} (without
     * considering the number of idle connections in the pool) and then against {@code softMinEvictableIdleTimeMillis},
     * including the {@code minIdle}, constraint.
     * </p>
     *
     * @return minimum amount of time a connection may sit idle in the pool before it is eligible for eviction, assuming
     *         there are minIdle idle connections in the pool
     */
    @Override
    public synchronized long getSoftMinEvictableIdleTimeMillis() {
        return softMinEvictableIdleTimeMillis;
    }

    /**
     * Returns the {@link #testOnBorrow} property.
     *
     * @return true if objects are validated before being borrowed from the pool
     *
     * @see #testOnBorrow
     */
    @Override
    public synchronized boolean getTestOnBorrow() {
        return this.testOnBorrow;
    }

    /**
     * Returns the {@link #testOnCreate} property.
     *
     * @return true if objects are validated immediately after they are created by the pool
     * @see #testOnCreate
     */
    @Override
    public synchronized boolean getTestOnCreate() {
        return this.testOnCreate;
    }

    /**
     * Returns the value of the {@link #testOnReturn} property.
     *
     * @return true if objects are validated before being returned to the pool
     * @see #testOnReturn
     */
    public synchronized boolean getTestOnReturn() {
        return this.testOnReturn;
    }

    /**
     * Returns the value of the {@link #testWhileIdle} property.
     *
     * @return true if objects examined by the idle object evictor are validated
     * @see #testWhileIdle
     */
    @Override
    public synchronized boolean getTestWhileIdle() {
        return this.testWhileIdle;
    }

    /**
     * Returns the value of the {@link #timeBetweenEvictionRunsMillis} property.
     *
     * @return the time (in milliseconds) between evictor runs
     * @see #timeBetweenEvictionRunsMillis
     */
    @Override
    public synchronized long getTimeBetweenEvictionRunsMillis() {
        return this.timeBetweenEvictionRunsMillis;
    }

    /**
     * Returns the JDBC connection {@link #url} property.
     *
     * @return the {@link #url} passed to the JDBC driver to establish connections
     */
    @Override
    public synchronized String getUrl() {
        return this.url;
    }

    /**
     * Returns the JDBC connection {@link #userName} property.
     *
     * @return the {@link #userName} passed to the JDBC driver to establish connections
     */
    @Override
    public String getUsername() {
        return this.userName;
    }

    /**
     * Returns the validation query used to validate connections before returning them.
     *
     * @return the SQL validation query
     * @see #validationQuery
     */
    @Override
    public String getValidationQuery() {
        return this.validationQuery;
    }

    /**
     * Returns the validation query timeout.
     *
     * @return the timeout in seconds before connection validation queries fail.
     */
    @Override
    public int getValidationQueryTimeout() {
        return validationQueryTimeoutSeconds;
    }

    /**
     * Manually invalidates a connection, effectively requesting the pool to try to close it, remove it from the pool
     * and reclaim pool capacity.
     *
     * @param connection The Connection to invalidate.
     *
     * @throws IllegalStateException if invalidating the connection failed.
     * @since 2.1
     */
    public void invalidateConnection(final Connection connection) throws IllegalStateException {
        if (connection == null) {
            return;
        }
        if (connectionPool == null) {
            throw new IllegalStateException("Cannot invalidate connection: ConnectionPool is null.");
        }

        final PoolableConnection poolableConnection;
        try {
            poolableConnection = connection.unwrap(PoolableConnection.class);
            if (poolableConnection == null) {
                throw new IllegalStateException(
                        "Cannot invalidate connection: Connection is not a poolable connection.");
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("Cannot invalidate connection: Unwrapping poolable connection failed.", e);
        }

        try {
            connectionPool.invalidateObject(poolableConnection);
        } catch (final Exception e) {
            throw new IllegalStateException("Invalidating connection threw unexpected exception", e);
        }
    }

    /**
     * Returns the value of the accessToUnderlyingConnectionAllowed property.
     *
     * @return true if access to the underlying connection is allowed, false otherwise.
     */
    @Override
    public synchronized boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    /**
     * Returns true if the statement pool is cleared when the connection is returned to its pool.
     *
     * @return true if the statement pool is cleared at connection return
     * @since 2.8.0
     */
    @Override
    public boolean isClearStatementPoolOnReturn() {
        return clearStatementPoolOnReturn;
    }

    /**
     * If true, this data source is closed and no more connections can be retrieved from this data source.
     *
     * @return true, if the data source is closed; false otherwise
     */
    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Delegates in a null-safe manner to {@link String#isEmpty()}.
     *
     * @param value the string to test, may be null.
     * @return boolean false if value is null, otherwise {@link String#isEmpty()}.
     */
    private boolean isEmpty(final String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Returns true if we are pooling statements.
     *
     * @return true if prepared and callable statements are pooled
     */
    @Override
    public synchronized boolean isPoolPreparedStatements() {
        return this.poolPreparedStatements;
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return false;
    }

    private void jmxRegister() {
        // Return immediately if this DataSource has already been registered
        if (registeredJmxObjectName != null) {
            return;
        }
        // Return immediately if no JMX name has been specified
        final String requestedName = getJmxName();
        if (requestedName == null) {
            return;
        }
        try {
            ObjectNameWrapper.wrap(requestedName).registerMBean(this);
        } catch (final MalformedObjectNameException e) {
            log.warn("The requested JMX name [" + requestedName + "] was not valid and will be ignored.");
        }
    }

    protected void log(final String message) {
        if (logWriter != null) {
            logWriter.println(message);
        }
    }

    /**
     * Logs the given throwable.
     * @param message TODO
     * @param throwable the throwable.
     *
     * @since 2.7.0
     */
    protected void log(final String message, final Throwable throwable) {
        if (logWriter != null) {
            logWriter.println(message);
            throwable.printStackTrace(logWriter);
        }
    }

    @Override
    public void postDeregister() {
        // NO-OP
    }

    @Override
    public void postRegister(final Boolean registrationDone) {
        // NO-OP
    }

    @Override
    public void preDeregister() throws Exception {
        // NO-OP
    }

    @Override
    public ObjectName preRegister(final MBeanServer server, final ObjectName objectName) {
        final String requestedName = getJmxName();
        if (requestedName != null) {
            try {
                registeredJmxObjectName = ObjectNameWrapper.wrap(requestedName);
            } catch (final MalformedObjectNameException e) {
                log.warn("The requested JMX name [" + requestedName + "] was not valid and will be ignored.");
            }
        }
        if (registeredJmxObjectName == null) {
            registeredJmxObjectName = ObjectNameWrapper.wrap(objectName);
        }
        return ObjectNameWrapper.unwrap(registeredJmxObjectName);
    }

    /**
     * Removes a custom connection property.
     *
     * @param name Name of the custom connection property to remove
     * @see #addConnectionProperty(String, String)
     */
    public void removeConnectionProperty(final String name) {
        connectionProperties.remove(name);
    }

    /**
     * Restarts the datasource.
     * <p>
     * This method calls {@link #close()} and {@link #start()} in sequence within synchronized scope so any
     * connection requests that come in while the datasource is shutting down will be served by the new pool.
     * <p>
     * Idle connections that are stored in the connection pool when this method is invoked are closed, but
     * connections that are checked out to clients when this method is invoked are not affected. When client
     * applications subsequently invoke {@link Connection#close()} to return these connections to the pool, the
     * underlying JDBC connections are closed. These connections do not count in {@link #getMaxTotal()} or
     * {@link #getNumActive()} after invoking this method. For example, if there are 3 connections checked out by
     * clients when {@link #restart()} is invoked, after this method is called, {@link #getNumActive()} will
     * return 0 and up to {@link #getMaxTotal()} + 3 connections may be open until the connections sourced from
     * the original pool are returned.
     * <p>
     * The new connection pool created by this method is initialized with currently set configuration properties.
     *
     * @throws SQLException if an error occurs initializing the datasource
     */
    @Override
    public synchronized void restart() throws SQLException {
        close();
        start();
    }

    /**
     * Sets the print writer to be used by this configuration to log information on abandoned objects.
     *
     * @param logWriter The new log writer
     */
    public void setAbandonedLogWriter(final PrintWriter logWriter) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setLogWriter(logWriter);
        final GenericObjectPool<?> gop = this.connectionPool;
        if (gop != null) {
            gop.setAbandonedConfig(abandonedConfig);
        }
    }

    /**
     * If the connection pool implements {@link org.apache.tomcat.dbcp.pool2.UsageTracking UsageTracking}, configure whether
     * the connection pool should record a stack trace every time a method is called on a pooled connection and retain
     * the most recent stack trace to aid debugging of abandoned connections.
     *
     * @param usageTracking A value of <code>true</code> will enable the recording of a stack trace on every use of a
     *                      pooled connection
     */
    public void setAbandonedUsageTracking(final boolean usageTracking) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setUseUsageTracking(usageTracking);
        final GenericObjectPool<?> gop = this.connectionPool;
        if (gop != null) {
            gop.setAbandonedConfig(abandonedConfig);
        }
    }

    /**
     * <p>
     * Sets the value of the accessToUnderlyingConnectionAllowed property. It controls if the PoolGuard allows access to
     * the underlying connection. (Default: false)
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param allow Access to the underlying connection is granted when true.
     */
    public synchronized void setAccessToUnderlyingConnectionAllowed(final boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }

    /**
     * Sets the value of the flag that controls whether or not connections being returned to the pool will be checked
     * and configured with {@link Connection#setAutoCommit(boolean) Connection.setAutoCommit(true)} if the auto commit
     * setting is {@code false} when the connection is returned. It is <code>true</code> by default.
     *
     * @param autoCommitOnReturn Whether or not connections being returned to the pool will be checked and configured
     *                           with auto-commit.
     * @since 2.6.0
     */
    public void setAutoCommitOnReturn(final boolean autoCommitOnReturn) {
        this.autoCommitOnReturn = autoCommitOnReturn;
    }

    /**
     * Sets the state caching flag.
     *
     * @param cacheState The new value for the state caching flag
     */
    public void setCacheState(final boolean cacheState) {
        this.cacheState = cacheState;
    }

    /**
     * Sets whether the pool of statements (which was enabled with {@link #setPoolPreparedStatements(boolean)}) should
     * be cleared when the connection is returned to its pool. Default is false.
     *
     * @param clearStatementPoolOnReturn clear or not
     * @since 2.8.0
     */
    public void setClearStatementPoolOnReturn(final boolean clearStatementPoolOnReturn) {
        this.clearStatementPoolOnReturn = clearStatementPoolOnReturn;
    }

    /**
     * Sets the ConnectionFactory class name.
     *
     * @param connectionFactoryClassName A class name.
     * @since 2.7.0
     */
    public void setConnectionFactoryClassName(final String connectionFactoryClassName) {
        this.connectionFactoryClassName = isEmpty(connectionFactoryClassName) ? null : connectionFactoryClassName;
    }

    /**
     * Sets the list of SQL statements to be executed when a physical connection is first created.
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param connectionInitSqls Collection of SQL statements to execute on connection creation
     */
    public void setConnectionInitSqls(final Collection<String> connectionInitSqls) {
        if (connectionInitSqls != null && connectionInitSqls.size() > 0) {
            ArrayList<String> newVal = null;
            for (final String s : connectionInitSqls) {
                if (!isEmpty(s)) {
                    if (newVal == null) {
                        newVal = new ArrayList<>();
                    }
                    newVal.add(s);
                }
            }
            this.connectionInitSqls = newVal;
        } else {
            this.connectionInitSqls = null;
        }
    }

    /**
     * Sets the connection properties passed to driver.connect(...).
     * <p>
     * Format of the string must be [propertyName=property;]*
     * </p>
     * <p>
     * NOTE - The "user" and "password" properties will be added explicitly, so they do not need to be included here.
     * </p>
     *
     * @param connectionProperties the connection properties used to create new connections
     */
    public void setConnectionProperties(final String connectionProperties) {
        Objects.requireNonNull(connectionProperties, "connectionProperties is null");
        final String[] entries = connectionProperties.split(";");
        final Properties properties = new Properties();
        for (final String entry : entries) {
            if (entry.length() > 0) {
                final int index = entry.indexOf('=');
                if (index > 0) {
                    final String name = entry.substring(0, index);
                    final String value = entry.substring(index + 1);
                    properties.setProperty(name, value);
                } else {
                    // no value is empty string which is how
                    // java.util.Properties works
                    properties.setProperty(entry, "");
                }
            }
        }
        this.connectionProperties = properties;
    }

    /**
     * <p>
     * Sets default auto-commit state of connections returned by this datasource.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param defaultAutoCommit default auto-commit value
     */
    public void setDefaultAutoCommit(final Boolean defaultAutoCommit) {
        this.defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * <p>
     * Sets the default catalog.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param defaultCatalog the default catalog
     */
    public void setDefaultCatalog(final String defaultCatalog) {
        this.defaultCatalog = isEmpty(defaultCatalog) ? null : defaultCatalog;
    }

    /**
     * Sets the default query timeout that will be used for {@link java.sql.Statement Statement}s created from this
     * connection. <code>null</code> means that the driver default will be used.
     *
     * @param defaultQueryTimeoutSeconds The default query timeout in seconds.
     */
    public void setDefaultQueryTimeout(final Integer defaultQueryTimeoutSeconds) {
        this.defaultQueryTimeoutSeconds = defaultQueryTimeoutSeconds;
    }

    /**
     * <p>
     * Sets defaultReadonly property.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param defaultReadOnly default read-only value
     */
    public void setDefaultReadOnly(final Boolean defaultReadOnly) {
        this.defaultReadOnly = defaultReadOnly;
    }

    /**
     * <p>
     * Sets the default schema.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param defaultSchema the default catalog
     * @since 2.5.0
     */
    public void setDefaultSchema(final String defaultSchema) {
        this.defaultSchema = isEmpty(defaultSchema) ? null : defaultSchema;
    }

    /**
     * <p>
     * Sets the default transaction isolation state for returned connections.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param defaultTransactionIsolation the default transaction isolation state
     * @see Connection#getTransactionIsolation
     */
    public void setDefaultTransactionIsolation(final int defaultTransactionIsolation) {
        this.defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * Sets the SQL_STATE codes considered to signal fatal conditions.
     * <p>
     * Overrides the defaults in {@link Utils#DISCONNECTION_SQL_CODES} (plus anything starting with
     * {@link Utils#DISCONNECTION_SQL_CODE_PREFIX}). If this property is non-null and {@link #getFastFailValidation()}
     * is {@code true}, whenever connections created by this datasource generate exceptions with SQL_STATE codes in this
     * list, they will be marked as "fatally disconnected" and subsequent validations will fail fast (no attempt at
     * isValid or validation query).
     * </p>
     * <p>
     * If {@link #getFastFailValidation()} is {@code false} setting this property has no effect.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: {@code getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter}.
     * </p>
     *
     * @param disconnectionSqlCodes SQL_STATE codes considered to signal fatal conditions
     * @since 2.1
     */
    public void setDisconnectionSqlCodes(final Collection<String> disconnectionSqlCodes) {
        if (disconnectionSqlCodes != null && disconnectionSqlCodes.size() > 0) {
            HashSet<String> newVal = null;
            for (final String s : disconnectionSqlCodes) {
                if (!isEmpty(s)) {
                    if (newVal == null) {
                        newVal = new HashSet<>();
                    }
                    newVal.add(s);
                }
            }
            this.disconnectionSqlCodes = newVal;
        } else {
            this.disconnectionSqlCodes = null;
        }
    }

    /**
     * Sets the JDBC Driver instance to use for this pool.
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param driver The JDBC Driver instance to use for this pool.
     */
    public synchronized void setDriver(final Driver driver) {
        this.driver = driver;
    }

    /**
     * <p>
     * Sets the class loader to be used to load the JDBC driver.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param driverClassLoader the class loader with which to load the JDBC driver
     */
    public synchronized void setDriverClassLoader(final ClassLoader driverClassLoader) {
        this.driverClassLoader = driverClassLoader;
    }

    /**
     * <p>
     * Sets the JDBC driver class name.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param driverClassName the class name of the JDBC driver
     */
    public synchronized void setDriverClassName(final String driverClassName) {
        this.driverClassName = isEmpty(driverClassName) ? null : driverClassName;
    }

    /**
     * Sets the value of the flag that controls whether or not connections being returned to the pool will be checked
     * and configured with {@link Connection#setAutoCommit(boolean) Connection.setAutoCommit(true)} if the auto commit
     * setting is {@code false} when the connection is returned. It is <code>true</code> by default.
     *
     * @param autoCommitOnReturn Whether or not connections being returned to the pool will be checked and configured
     *                           with auto-commit.
     * @deprecated Use {@link #setAutoCommitOnReturn(boolean)}.
     */
    @Deprecated
    public void setEnableAutoCommitOnReturn(final boolean autoCommitOnReturn) {
        this.autoCommitOnReturn = autoCommitOnReturn;
    }

    /**
     * Sets the EvictionPolicy implementation to use with this connection pool.
     *
     * @param evictionPolicyClassName The fully qualified class name of the EvictionPolicy implementation
     */
    public synchronized void setEvictionPolicyClassName(final String evictionPolicyClassName) {
        if (connectionPool != null) {
            connectionPool.setEvictionPolicyClassName(evictionPolicyClassName);
        }
        this.evictionPolicyClassName = evictionPolicyClassName;
    }

    /**
     * @see #getFastFailValidation()
     * @param fastFailValidation true means connections created by this factory will fast fail validation
     * @since 2.1
     */
    public void setFastFailValidation(final boolean fastFailValidation) {
        this.fastFailValidation = fastFailValidation;
    }

    /**
     * <p>
     * Sets the initial size of the connection pool.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param initialSize the number of connections created when the pool is initialized
     */
    public synchronized void setInitialSize(final int initialSize) {
        this.initialSize = initialSize;
    }

    /**
     * Sets the JMX name that has been requested for this DataSource. If the requested name is not valid, an alternative
     * may be chosen. This DataSource will attempt to register itself using this name. If another component registers
     * this DataSource with JMX and this name is valid this name will be used in preference to any specified by the
     * other component.
     *
     * @param jmxName The JMX name that has been requested for this DataSource
     */
    public void setJmxName(final String jmxName) {
        this.jmxName = jmxName;
    }

    /**
     * Sets the LIFO property. True means the pool behaves as a LIFO queue; false means FIFO.
     *
     * @param lifo the new value for the LIFO property
     */
    public synchronized void setLifo(final boolean lifo) {
        this.lifo = lifo;
        if (connectionPool != null) {
            connectionPool.setLifo(lifo);
        }
    }

    /**
     * @param logAbandoned new logAbandoned property value
     */
    public void setLogAbandoned(final boolean logAbandoned) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setLogAbandoned(logAbandoned);
        final GenericObjectPool<?> gop = this.connectionPool;
        if (gop != null) {
            gop.setAbandonedConfig(abandonedConfig);
        }
    }

    /**
     * When {@link #getMaxConnLifetimeMillis()} is set to limit connection lifetime, this property determines whether or
     * not log messages are generated when the pool closes connections due to maximum lifetime exceeded. Set this
     * property to false to suppress log messages when connections expire.
     *
     * @param logExpiredConnections Whether or not log messages are generated when the pool closes connections due to
     *                              maximum lifetime exceeded.
     */
    public void setLogExpiredConnections(final boolean logExpiredConnections) {
        this.logExpiredConnections = logExpiredConnections;
    }

    /**
     * <strong>BasicDataSource does NOT support this method. </strong>
     *
     * <p>
     * Set the login timeout (in seconds) for connecting to the database.
     * </p>
     * <p>
     * Calls {@link #createDataSource()}, so has the side effect of initializing the connection pool.
     * </p>
     *
     * @param loginTimeout The new login timeout, or zero for no timeout
     * @throws UnsupportedOperationException If the DataSource implementation does not support the login timeout
     *                                       feature.
     * @throws SQLException                  if a database access error occurs
     */
    @Override
    public void setLoginTimeout(final int loginTimeout) throws SQLException {
        // This method isn't supported by the PoolingDataSource returned by the
        // createDataSource
        throw new UnsupportedOperationException("Not supported by BasicDataSource");
    }

    /**
     * <p>
     * Sets the log writer being used by this data source.
     * </p>
     * <p>
     * Calls {@link #createDataSource()}, so has the side effect of initializing the connection pool.
     * </p>
     *
     * @param logWriter The new log writer
     * @throws SQLException if a database access error occurs
     */
    @Override
    public void setLogWriter(final PrintWriter logWriter) throws SQLException {
        createDataSource().setLogWriter(logWriter);
        this.logWriter = logWriter;
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
     * @param maxConnLifetimeMillis The maximum permitted lifetime of a connection in milliseconds.
     */
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        this.maxConnLifetimeMillis = maxConnLifetimeMillis;
    }

    /**
     * Sets the maximum number of connections that can remain idle in the pool. Excess idle connections are destroyed on
     * return to the pool.
     *
     * @see #getMaxIdle()
     * @param maxIdle the new value for maxIdle
     */
    public synchronized void setMaxIdle(final int maxIdle) {
        this.maxIdle = maxIdle;
        if (connectionPool != null) {
            connectionPool.setMaxIdle(maxIdle);
        }
    }

    /**
     * <p>
     * Sets the value of the <code>maxOpenPreparedStatements</code> property.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param maxOpenStatements the new maximum number of prepared statements
     */
    public synchronized void setMaxOpenPreparedStatements(final int maxOpenStatements) {
        this.maxOpenPreparedStatements = maxOpenStatements;
    }

    /**
     * Sets the maximum total number of idle and borrows connections that can be active at the same time. Use a negative
     * value for no limit.
     *
     * @param maxTotal the new value for maxTotal
     * @see #getMaxTotal()
     */
    public synchronized void setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
        if (connectionPool != null) {
            connectionPool.setMaxTotal(maxTotal);
        }
    }

    /**
     * Sets the MaxWaitMillis property. Use -1 to make the pool wait indefinitely.
     *
     * @param maxWaitMillis the new value for MaxWaitMillis
     * @see #getMaxWaitMillis()
     */
    public synchronized void setMaxWaitMillis(final long maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
        if (connectionPool != null) {
            connectionPool.setMaxWaitMillis(maxWaitMillis);
        }
    }

    /**
     * Sets the {@link #minEvictableIdleTimeMillis} property.
     *
     * @param minEvictableIdleTimeMillis the minimum amount of time an object may sit idle in the pool
     * @see #minEvictableIdleTimeMillis
     */
    public synchronized void setMinEvictableIdleTimeMillis(final long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        if (connectionPool != null) {
            connectionPool.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        }
    }

    // ------------------------------------------------------ Protected Methods

    /**
     * Sets the minimum number of idle connections in the pool. The pool attempts to ensure that minIdle connections are
     * available when the idle object evictor runs. The value of this property has no effect unless
     * {@link #timeBetweenEvictionRunsMillis} has a positive value.
     *
     * @param minIdle the new value for minIdle
     * @see GenericObjectPool#setMinIdle(int)
     */
    public synchronized void setMinIdle(final int minIdle) {
        this.minIdle = minIdle;
        if (connectionPool != null) {
            connectionPool.setMinIdle(minIdle);
        }
    }

    /**
     * Sets the value of the {@link #numTestsPerEvictionRun} property.
     *
     * @param numTestsPerEvictionRun the new {@link #numTestsPerEvictionRun} value
     * @see #numTestsPerEvictionRun
     */
    public synchronized void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
        if (connectionPool != null) {
            connectionPool.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        }
    }

    /**
     * <p>
     * Sets the {@link #password}.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param password new value for the password
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    /**
     * <p>
     * Sets whether to pool statements or not.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param poolingStatements pooling on or off
     */
    public synchronized void setPoolPreparedStatements(final boolean poolingStatements) {
        this.poolPreparedStatements = poolingStatements;
    }

    /**
     * @param removeAbandonedOnBorrow true means abandoned connections may be removed when connections are borrowed from
     *                                the pool.
     * @see #getRemoveAbandonedOnBorrow()
     */
    public void setRemoveAbandonedOnBorrow(final boolean removeAbandonedOnBorrow) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setRemoveAbandonedOnBorrow(removeAbandonedOnBorrow);
        final GenericObjectPool<?> gop = this.connectionPool;
        if (gop != null) {
            gop.setAbandonedConfig(abandonedConfig);
        }
    }

    /**
     * @param removeAbandonedOnMaintenance true means abandoned connections may be removed on pool maintenance.
     * @see #getRemoveAbandonedOnMaintenance()
     */
    public void setRemoveAbandonedOnMaintenance(final boolean removeAbandonedOnMaintenance) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setRemoveAbandonedOnMaintenance(removeAbandonedOnMaintenance);
        final GenericObjectPool<?> gop = this.connectionPool;
        if (gop != null) {
            gop.setAbandonedConfig(abandonedConfig);
        }
    }

    /**
     * <p>
     * Sets the timeout in seconds before an abandoned connection can be removed.
     * </p>
     *
     * <p>
     * Setting this property has no effect if {@link #getRemoveAbandonedOnBorrow()} and
     * {@link #getRemoveAbandonedOnMaintenance()} are false.
     * </p>
     *
     * @param removeAbandonedTimeout new abandoned timeout in seconds
     * @see #getRemoveAbandonedTimeout()
     * @see #getRemoveAbandonedOnBorrow()
     * @see #getRemoveAbandonedOnMaintenance()
     */
    public void setRemoveAbandonedTimeout(final int removeAbandonedTimeout) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setRemoveAbandonedTimeout(removeAbandonedTimeout);
        final GenericObjectPool<?> gop = this.connectionPool;
        if (gop != null) {
            gop.setAbandonedConfig(abandonedConfig);
        }
    }

    /**
     * Sets the flag that controls if a connection will be rolled back when it is returned to the pool if auto commit is
     * not enabled and the connection is not read only.
     *
     * @param rollbackOnReturn whether a connection will be rolled back when it is returned to the pool.
     */
    public void setRollbackOnReturn(final boolean rollbackOnReturn) {
        this.rollbackOnReturn = rollbackOnReturn;
    }

    /**
     * Sets the minimum amount of time a connection may sit idle in the pool before it is eligible for eviction by the
     * idle object evictor, with the extra condition that at least "minIdle" connections remain in the pool.
     *
     * @param softMinEvictableIdleTimeMillis minimum amount of time a connection may sit idle in the pool before it is
     *                                       eligible for eviction, assuming there are minIdle idle connections in the
     *                                       pool.
     * @see #getSoftMinEvictableIdleTimeMillis
     */
    public synchronized void setSoftMinEvictableIdleTimeMillis(final long softMinEvictableIdleTimeMillis) {
        this.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
        if (connectionPool != null) {
            connectionPool.setSoftMinEvictableIdleTimeMillis(softMinEvictableIdleTimeMillis);
        }
    }

    /**
     * Sets the {@link #testOnBorrow} property. This property determines whether or not the pool will validate objects
     * before they are borrowed from the pool.
     *
     * @param testOnBorrow new value for testOnBorrow property
     */
    public synchronized void setTestOnBorrow(final boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
        if (connectionPool != null) {
            connectionPool.setTestOnBorrow(testOnBorrow);
        }
    }

    /**
     * Sets the {@link #testOnCreate} property. This property determines whether or not the pool will validate objects
     * immediately after they are created by the pool
     *
     * @param testOnCreate new value for testOnCreate property
     */
    public synchronized void setTestOnCreate(final boolean testOnCreate) {
        this.testOnCreate = testOnCreate;
        if (connectionPool != null) {
            connectionPool.setTestOnCreate(testOnCreate);
        }
    }

    /**
     * Sets the <code>testOnReturn</code> property. This property determines whether or not the pool will validate
     * objects before they are returned to the pool.
     *
     * @param testOnReturn new value for testOnReturn property
     */
    public synchronized void setTestOnReturn(final boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
        if (connectionPool != null) {
            connectionPool.setTestOnReturn(testOnReturn);
        }
    }

    /**
     * Sets the <code>testWhileIdle</code> property. This property determines whether or not the idle object evictor
     * will validate connections.
     *
     * @param testWhileIdle new value for testWhileIdle property
     */
    public synchronized void setTestWhileIdle(final boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
        if (connectionPool != null) {
            connectionPool.setTestWhileIdle(testWhileIdle);
        }
    }

    /**
     * Sets the {@link #timeBetweenEvictionRunsMillis} property.
     *
     * @param timeBetweenEvictionRunsMillis the new time between evictor runs
     * @see #timeBetweenEvictionRunsMillis
     */
    public synchronized void setTimeBetweenEvictionRunsMillis(final long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        if (connectionPool != null) {
            connectionPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        }
    }

    /**
     * <p>
     * Sets the {@link #url}.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param url the new value for the JDBC connection url
     */
    public synchronized void setUrl(final String url) {
        this.url = url;
    }

    /**
     * <p>
     * Sets the {@link #userName}.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param userName the new value for the JDBC connection user name
     */
    public void setUsername(final String userName) {
        this.userName = userName;
    }

    /**
     * <p>
     * Sets the {@link #validationQuery}.
     * </p>
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param validationQuery the new value for the validation query
     */
    public void setValidationQuery(final String validationQuery) {
        this.validationQuery = isEmpty(validationQuery) ? null : validationQuery;
    }

    /**
     * Sets the validation query timeout, the amount of time, in seconds, that connection validation will wait for a
     * response from the database when executing a validation query. Use a value less than or equal to 0 for no timeout.
     * <p>
     * Note: this method currently has no effect once the pool has been initialized. The pool is initialized the first
     * time one of the following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code>
     * </p>
     *
     * @param validationQueryTimeoutSeconds new validation query timeout value in seconds
     */
    public void setValidationQueryTimeout(final int validationQueryTimeoutSeconds) {
        this.validationQueryTimeoutSeconds = validationQueryTimeoutSeconds;
    }

    /**
     * Starts the datasource.
     * <p>
     * It is not necessary to call this method before using a newly created BasicDataSource instance, but
     * calling it in that context causes the datasource to be immediately initialized (instead of waiting for
     * the first {@link #getConnection()} request). Its primary use is to restart and reinitialize a
     * datasource that has been closed.
     * <p>
     * When this method is called after {@link #close()}, connections checked out by clients
     * before the datasource was stopped do not count in {@link #getMaxTotal()} or {@link #getNumActive()}.
     * For example, if there are 3 connections checked out by clients when {@link #close()} is invoked and they are
     * not returned before {@link #start()} is invoked, after this method is called, {@link #getNumActive()} will
     * return 0.  These connections will be physically closed when they are returned, but they will not count against
     * the maximum allowed in the newly started datasource.
     *
     * @throws SQLException if an error occurs initializing the datasource
     */
    @Override
    public synchronized void start() throws SQLException {
        closed = false;
        createDataSource();
    }

    /**
     * Starts the connection pool maintenance task, if configured.
     */
    protected void startPoolMaintenance() {
        if (connectionPool != null && timeBetweenEvictionRunsMillis > 0) {
            connectionPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        }
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        throw new SQLException("BasicDataSource is not a wrapper.");
    }

    private void updateJmxName(final GenericObjectPoolConfig<?> config) {
        if (registeredJmxObjectName == null) {
            return;
        }
        final StringBuilder base = new StringBuilder(registeredJmxObjectName.toString());
        base.append(Constants.JMX_CONNECTION_POOL_BASE_EXT);
        config.setJmxNameBase(base.toString());
        config.setJmxNamePrefix(Constants.JMX_CONNECTION_POOL_PREFIX);
    }

}

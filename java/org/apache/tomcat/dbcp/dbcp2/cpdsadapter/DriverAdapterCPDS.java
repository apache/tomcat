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
package org.apache.tomcat.dbcp.dbcp2.cpdsadapter;

import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Logger;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

import org.apache.tomcat.dbcp.dbcp2.Constants;
import org.apache.tomcat.dbcp.dbcp2.DelegatingPreparedStatement;
import org.apache.tomcat.dbcp.dbcp2.PStmtKey;
import org.apache.tomcat.dbcp.dbcp2.Utils;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.BaseObjectPoolConfig;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 * <p>
 * An adapter for JDBC drivers that do not include an implementation of {@link javax.sql.ConnectionPoolDataSource}, but
 * still include a {@link java.sql.DriverManager} implementation. {@code ConnectionPoolDataSource}s are not used
 * within general applications. They are used by {@code DataSource} implementations that pool
 * {@code Connection}s, such as {@link org.apache.tomcat.dbcp.dbcp2.datasources.SharedPoolDataSource}. A J2EE container
 * will normally provide some method of initializing the {@code ConnectionPoolDataSource} whose attributes are
 * presented as bean getters/setters and then deploying it via JNDI. It is then available as a source of physical
 * connections to the database, when the pooling {@code DataSource} needs to create a new physical connection.
 * </p>
 * <p>
 * Although normally used within a JNDI environment, the DriverAdapterCPDS can be instantiated and initialized as any
 * bean and then attached directly to a pooling {@code DataSource}. {@code Jdbc2PoolDataSource} can use the
 * {@code ConnectionPoolDataSource} with or without the use of JNDI.
 * </p>
 * <p>
 * The DriverAdapterCPDS also provides {@code PreparedStatement} pooling which is not generally available in jdbc2
 * {@code ConnectionPoolDataSource} implementation, but is addressed within the JDBC 3 specification. The
 * {@code PreparedStatement} pool in DriverAdapterCPDS has been in the DBCP package for some time, but it has not
 * undergone extensive testing in the configuration used here. It should be considered experimental and can be toggled
 * with the poolPreparedStatements attribute.
 * </p>
 * <p>
 * The <a href="package-summary.html">package documentation</a> contains an example using Apache Catalina and JNDI. The
 * <a href="../datasources/package-summary.html">datasources package documentation</a> shows how to use
 * {@code DriverAdapterCPDS} as a source for {@code Jdbc2PoolDataSource} without the use of JNDI.
 * </p>
 *
 * @since 2.0
 */
public class DriverAdapterCPDS implements ConnectionPoolDataSource, Referenceable, Serializable, ObjectFactory {

    private static final String KEY_MIN_EVICTABLE_IDLE_DURATION = "minEvictableIdleDuration";

    private static final String KEY_DURATION_BETWEEN_EVICTION_RUNS = "durationBetweenEvictionRuns";

    private static final String KEY_LOGIN_TIMEOUT = "loginTimeout";

    private static final String KEY_URL = "url";

    private static final String KEY_DRIVER = "driver";

    private static final String KEY_DESCRIPTION = "description";

    private static final String KEY_ACCESS_TO_UNDERLYING_CONNECTION_ALLOWED = "accessToUnderlyingConnectionAllowed";

    private static final String KEY_MAX_PREPARED_STATEMENTS = "maxPreparedStatements";

    private static final String KEY_MIN_EVICTABLE_IDLE_TIME_MILLIS = "minEvictableIdleTimeMillis";

    private static final String KEY_NUM_TESTS_PER_EVICTION_RUN = "numTestsPerEvictionRun";

    private static final String KEY_TIME_BETWEEN_EVICTION_RUNS_MILLIS = "timeBetweenEvictionRunsMillis";

    private static final String KEY_MAX_IDLE = "maxIdle";

    private static final String KEY_POOL_PREPARED_STATEMENTS = "poolPreparedStatements";

    private static final long serialVersionUID = -4820523787212147844L;

    private static final String GET_CONNECTION_CALLED = "A PooledConnection was already requested from this source, further initialization is not allowed.";

    static {
        // Attempt to prevent deadlocks - see DBCP-272
        DriverManager.getDrivers();
    }

    /** Description */
    private String description;

    /** Connection string */
    private String connectionString;

    /** User name */
    private String userName;

    /** User password */
    private char[] userPassword;

    /** Driver class name */
    private String driver;

    /** Login TimeOut in seconds */
    private int loginTimeout;

    /** Log stream. NOT USED */
    private transient PrintWriter logWriter;

    // PreparedStatement pool properties
    private boolean poolPreparedStatements;
    private int maxIdle = 10;
    private Duration durationBetweenEvictionRuns = BaseObjectPoolConfig.DEFAULT_DURATION_BETWEEN_EVICTION_RUNS;
    private int numTestsPerEvictionRun = -1;
    private Duration minEvictableIdleDuration = BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_DURATION;

    private int maxPreparedStatements = -1;

    /** Whether or not getConnection has been called */
    private volatile boolean getConnectionCalled;

    /** Connection properties passed to JDBC Driver */
    private Properties connectionProperties;

    /**
     * Controls access to the underlying connection
     */
    private boolean accessToUnderlyingConnectionAllowed;

    /**
     * Default no-argument constructor for Serialization
     */
    public DriverAdapterCPDS() {
    }

    /**
     * Throws an IllegalStateException, if a PooledConnection has already been requested.
     */
    private void assertInitializationAllowed() throws IllegalStateException {
        if (getConnectionCalled) {
            throw new IllegalStateException(GET_CONNECTION_CALLED);
        }
    }

    private boolean getBooleanContentString(final RefAddr ra) {
        return Boolean.parseBoolean(getStringContent(ra));
    }

    /**
     * Gets the connection properties passed to the JDBC driver.
     *
     * @return the JDBC connection properties used when creating connections.
     */
    public Properties getConnectionProperties() {
        return connectionProperties;
    }

    /**
     * Gets the value of description. This property is here for use by the code which will deploy this data source. It
     * is not used internally.
     *
     * @return value of description, may be null.
     * @see #setDescription(String)
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the driver class name.
     *
     * @return value of driver.
     */
    public String getDriver() {
        return driver;
    }

    /**
     * Gets the duration to sleep between runs of the idle object evictor thread. When non-positive, no
     * idle object evictor thread will be run.
     *
     * @return the value of the evictor thread timer
     * @see #setDurationBetweenEvictionRuns(Duration)
     * @since 2.9.0
     */
    public Duration getDurationBetweenEvictionRuns() {
        return durationBetweenEvictionRuns;
    }

    private int getIntegerStringContent(final RefAddr ra) {
        return Integer.parseInt(getStringContent(ra));
    }

    /**
     * Gets the maximum time in seconds that this data source can wait while attempting to connect to a database. NOT
     * USED.
     */
    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    /**
     * Gets the log writer for this data source. NOT USED.
     */
    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    /**
     * Gets the maximum number of statements that can remain idle in the pool, without extra ones being released, or
     * negative for no limit.
     *
     * @return the value of maxIdle
     */
    public int getMaxIdle() {
        return maxIdle;
    }

    /**
     * Gets the maximum number of prepared statements.
     *
     * @return maxPrepartedStatements value
     */
    public int getMaxPreparedStatements() {
        return maxPreparedStatements;
    }

    /**
     * Gets the minimum amount of time a statement may sit idle in the pool before it is eligible for eviction by the
     * idle object evictor (if any).
     *
     * @see #setMinEvictableIdleDuration
     * @see #setDurationBetweenEvictionRuns
     * @return the minimum amount of time a statement may sit idle in the pool.
     * @since 2.9.0
     */
    public Duration getMinEvictableIdleDuration() {
        return minEvictableIdleDuration;
    }

    /**
     * Gets the minimum amount of time a statement may sit idle in the pool before it is eligible for eviction by the
     * idle object evictor (if any).
     *
     * @see #setMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     * @return the minimum amount of time a statement may sit idle in the pool.
     * @deprecated USe {@link #getMinEvictableIdleDuration()}.
     */
    @Deprecated
    public int getMinEvictableIdleTimeMillis() {
        return (int) minEvictableIdleDuration.toMillis();
    }

    /**
     * Gets the number of statements to examine during each run of the idle object evictor thread (if any.)
     *
     * @see #setNumTestsPerEvictionRun
     * @see #setTimeBetweenEvictionRunsMillis
     * @return the number of statements to examine during each run of the idle object evictor thread (if any.)
     */
    public int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    /**
     * Implements {@link ObjectFactory} to create an instance of this class
     */
    @Override
    public Object getObjectInstance(final Object refObj, final Name name, final Context context, final Hashtable<?, ?> env) throws ClassNotFoundException {
        // The spec says to return null if we can't create an instance
        // of the reference
        DriverAdapterCPDS cpds = null;
        if (refObj instanceof Reference) {
            final Reference ref = (Reference) refObj;
            if (ref.getClassName().equals(getClass().getName())) {
                RefAddr ra = ref.get(KEY_DESCRIPTION);
                if (isNotEmpty(ra)) {
                    setDescription(getStringContent(ra));
                }

                ra = ref.get(KEY_DRIVER);
                if (isNotEmpty(ra)) {
                    setDriver(getStringContent(ra));
                }
                ra = ref.get(KEY_URL);
                if (isNotEmpty(ra)) {
                    setUrl(getStringContent(ra));
                }
                ra = ref.get(Constants.KEY_USER);
                if (isNotEmpty(ra)) {
                    setUser(getStringContent(ra));
                }
                ra = ref.get(Constants.KEY_PASSWORD);
                if (isNotEmpty(ra)) {
                    setPassword(getStringContent(ra));
                }

                ra = ref.get(KEY_POOL_PREPARED_STATEMENTS);
                if (isNotEmpty(ra)) {
                    setPoolPreparedStatements(getBooleanContentString(ra));
                }
                ra = ref.get(KEY_MAX_IDLE);
                if (isNotEmpty(ra)) {
                    setMaxIdle(getIntegerStringContent(ra));
                }

                ra = ref.get(KEY_TIME_BETWEEN_EVICTION_RUNS_MILLIS);
                if (isNotEmpty(ra)) {
                    setTimeBetweenEvictionRunsMillis(getIntegerStringContent(ra));
                }

                ra = ref.get(KEY_NUM_TESTS_PER_EVICTION_RUN);
                if (isNotEmpty(ra)) {
                    setNumTestsPerEvictionRun(getIntegerStringContent(ra));
                }

                ra = ref.get(KEY_MIN_EVICTABLE_IDLE_TIME_MILLIS);
                if (isNotEmpty(ra)) {
                    setMinEvictableIdleTimeMillis(getIntegerStringContent(ra));
                }

                ra = ref.get(KEY_MAX_PREPARED_STATEMENTS);
                if (isNotEmpty(ra)) {
                    setMaxPreparedStatements(getIntegerStringContent(ra));
                }

                ra = ref.get(KEY_ACCESS_TO_UNDERLYING_CONNECTION_ALLOWED);
                if (isNotEmpty(ra)) {
                    setAccessToUnderlyingConnectionAllowed(getBooleanContentString(ra));
                }

                cpds = this;
            }
        }
        return cpds;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * Gets the value of password for the default user.
     *
     * @return value of password.
     */
    public String getPassword() {
        return Utils.toString(userPassword);
    }

    /**
     * Gets the value of password for the default user.
     *
     * @return value of password.
     * @since 2.4.0
     */
    public char[] getPasswordCharArray() {
        return Utils.clone(userPassword);
    }

    /**
     * Attempts to establish a database connection using the default user and password.
     */
    @Override
    public PooledConnection getPooledConnection() throws SQLException {
        return getPooledConnection(getUser(), getPassword());
    }

    /**
     * Attempts to establish a database connection.
     *
     * @param pooledUserName name to be used for the connection
     * @param pooledUserPassword password to be used fur the connection
     */
    @Override
    public PooledConnection getPooledConnection(final String pooledUserName, final String pooledUserPassword) throws SQLException {
        getConnectionCalled = true;
        if (connectionProperties != null) {
            update(connectionProperties, Constants.KEY_USER, pooledUserName);
            update(connectionProperties, Constants.KEY_PASSWORD, pooledUserPassword);
        }
        // Workaround for buggy WebLogic 5.1 class loader - ignore ClassCircularityError upon first invocation.
        PooledConnectionImpl pooledConnection = null;
        try {
            pooledConnection = getPooledConnectionImpl(pooledUserName, pooledUserPassword);
        } catch (final ClassCircularityError e) {
            pooledConnection = getPooledConnectionImpl(pooledUserName, pooledUserPassword);
        }
        if (isPoolPreparedStatements()) {
            final GenericKeyedObjectPoolConfig<DelegatingPreparedStatement> config = new GenericKeyedObjectPoolConfig<>();
            config.setMaxTotalPerKey(Integer.MAX_VALUE);
            config.setBlockWhenExhausted(false);
            config.setMaxWait(Duration.ZERO);
            config.setMaxIdlePerKey(getMaxIdle());
            if (getMaxPreparedStatements() <= 0) {
                // Since there is no limit, create a prepared statement pool with an eviction thread;
                // evictor settings are the same as the connection pool settings.
                config.setTimeBetweenEvictionRuns(getDurationBetweenEvictionRuns());
                config.setNumTestsPerEvictionRun(getNumTestsPerEvictionRun());
                config.setMinEvictableIdleDuration(getMinEvictableIdleDuration());
            } else {
                // Since there is a limit, create a prepared statement pool without an eviction thread;
                // pool has LRU functionality so when the limit is reached, 15% of the pool is cleared.
                // see org.apache.commons.pool2.impl.GenericKeyedObjectPool.clearOldest method
                config.setMaxTotal(getMaxPreparedStatements());
                config.setTimeBetweenEvictionRuns(Duration.ofMillis(-1));
                config.setNumTestsPerEvictionRun(0);
                config.setMinEvictableIdleDuration(Duration.ZERO);
            }
            final KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> stmtPool = new GenericKeyedObjectPool<>(pooledConnection, config);
            pooledConnection.setStatementPool(stmtPool);
        }
        return pooledConnection;
    }

    private PooledConnectionImpl getPooledConnectionImpl(final String pooledUserName, final String pooledUserPassword) throws SQLException {
        PooledConnectionImpl pooledConnection;
        if (connectionProperties != null) {
            pooledConnection = new PooledConnectionImpl(DriverManager.getConnection(getUrl(), connectionProperties));
        } else {
            pooledConnection = new PooledConnectionImpl(DriverManager.getConnection(getUrl(), pooledUserName, pooledUserPassword));
        }
        pooledConnection.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
        return pooledConnection;
    }

    /**
     * Implements {@link Referenceable}.
     */
    @Override
    public Reference getReference() throws NamingException {
        // this class implements its own factory
        final String factory = getClass().getName();

        final Reference ref = new Reference(getClass().getName(), factory, null);

        ref.add(new StringRefAddr(KEY_DESCRIPTION, getDescription()));
        ref.add(new StringRefAddr(KEY_DRIVER, getDriver()));
        ref.add(new StringRefAddr(KEY_LOGIN_TIMEOUT, String.valueOf(getLoginTimeout())));
        ref.add(new StringRefAddr(Constants.KEY_PASSWORD, getPassword()));
        ref.add(new StringRefAddr(Constants.KEY_USER, getUser()));
        ref.add(new StringRefAddr(KEY_URL, getUrl()));

        ref.add(new StringRefAddr(KEY_POOL_PREPARED_STATEMENTS, String.valueOf(isPoolPreparedStatements())));
        ref.add(new StringRefAddr(KEY_MAX_IDLE, String.valueOf(getMaxIdle())));
        ref.add(new StringRefAddr(KEY_NUM_TESTS_PER_EVICTION_RUN, String.valueOf(getNumTestsPerEvictionRun())));
        ref.add(new StringRefAddr(KEY_MAX_PREPARED_STATEMENTS, String.valueOf(getMaxPreparedStatements())));
        //
        // Pair of current and deprecated.
        ref.add(new StringRefAddr(KEY_DURATION_BETWEEN_EVICTION_RUNS, String.valueOf(getDurationBetweenEvictionRuns())));
        ref.add(new StringRefAddr(KEY_TIME_BETWEEN_EVICTION_RUNS_MILLIS, String.valueOf(getTimeBetweenEvictionRunsMillis())));
        //
        // Pair of current and deprecated.
        ref.add(new StringRefAddr(KEY_MIN_EVICTABLE_IDLE_DURATION, String.valueOf(getMinEvictableIdleDuration())));
        ref.add(new StringRefAddr(KEY_MIN_EVICTABLE_IDLE_TIME_MILLIS, String.valueOf(getMinEvictableIdleTimeMillis())));

        return ref;
    }

    private String getStringContent(final RefAddr ra) {
        return ra.getContent().toString();
    }

    /**
     * Gets the number of milliseconds to sleep between runs of the idle object evictor thread. When non-positive, no
     * idle object evictor thread will be run.
     *
     * @return the value of the evictor thread timer
     * @see #setDurationBetweenEvictionRuns(Duration)
     * @deprecated Use {@link #getDurationBetweenEvictionRuns()}.
     */
    @Deprecated
    public long getTimeBetweenEvictionRunsMillis() {
        return durationBetweenEvictionRuns.toMillis();
    }

    /**
     * Gets the value of connection string used to locate the database for this data source.
     *
     * @return value of connection string.
     */
    public String getUrl() {
        return connectionString;
    }

    /**
     * Gets the value of default user (login or user name).
     *
     * @return value of user.
     */
    public String getUser() {
        return userName;
    }

    /**
     * Returns the value of the accessToUnderlyingConnectionAllowed property.
     *
     * @return true if access to the underlying is allowed, false otherwise.
     */
    public synchronized boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    private boolean isNotEmpty(final RefAddr ra) {
        return ra != null && ra.getContent() != null;
    }

    /**
     * Whether to toggle the pooling of {@code PreparedStatement}s
     *
     * @return value of poolPreparedStatements.
     */
    public boolean isPoolPreparedStatements() {
        return poolPreparedStatements;
    }

    /**
     * Sets the value of the accessToUnderlyingConnectionAllowed property. It controls if the PoolGuard allows access to
     * the underlying connection. (Default: false)
     *
     * @param allow Access to the underlying connection is granted when true.
     */
    public synchronized void setAccessToUnderlyingConnectionAllowed(final boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }

    /**
     * Sets the connection properties passed to the JDBC driver.
     * <p>
     * If {@code props} contains "user" and/or "password" properties, the corresponding instance properties are
     * set. If these properties are not present, they are filled in using {@link #getUser()}, {@link #getPassword()}
     * when {@link #getPooledConnection()} is called, or using the actual parameters to the method call when
     * {@link #getPooledConnection(String, String)} is called. Calls to {@link #setUser(String)} or
     * {@link #setPassword(String)} overwrite the values of these properties if {@code connectionProperties} is not
     * null.
     * </p>
     *
     * @param props Connection properties to use when creating new connections.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setConnectionProperties(final Properties props) {
        assertInitializationAllowed();
        connectionProperties = props;
        if (connectionProperties != null) {
            final String user = connectionProperties.getProperty(Constants.KEY_USER);
            if (user != null) {
                setUser(user);
            }
            final String password = connectionProperties.getProperty(Constants.KEY_PASSWORD);
            if (password != null) {
                setPassword(password);
            }
        }
    }

    /**
     * Sets the value of description. This property is here for use by the code which will deploy this datasource. It is
     * not used internally.
     *
     * @param description Value to assign to description.
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Sets the driver class name. Setting the driver class name cause the driver to be registered with the
     * DriverManager.
     *
     * @param driver Value to assign to driver.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     * @throws ClassNotFoundException if the class cannot be located
     */
    public void setDriver(final String driver) throws ClassNotFoundException {
        assertInitializationAllowed();
        this.driver = driver;
        // make sure driver is registered
        Class.forName(driver);
    }

    /**
     * Sets the duration to sleep between runs of the idle object evictor thread. When non-positive, no
     * idle object evictor thread will be run.
     *
     * @param durationBetweenEvictionRuns The duration to sleep between runs of the idle object evictor
     *        thread. When non-positive, no idle object evictor thread will be run.
     * @see #getDurationBetweenEvictionRuns()
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     * @since 2.9.0
     */
    public void setDurationBetweenEvictionRuns(final Duration durationBetweenEvictionRuns) {
        assertInitializationAllowed();
        this.durationBetweenEvictionRuns = durationBetweenEvictionRuns;
    }

    /**
     * Sets the maximum time in seconds that this data source will wait while attempting to connect to a database. NOT
     * USED.
     */
    @Override
    public void setLoginTimeout(final int seconds) {
        this.loginTimeout = seconds;
    }

    /**
     * Sets the log writer for this data source. NOT USED.
     */
    @Override
    public void setLogWriter(final PrintWriter logWriter) {
        this.logWriter = logWriter;
    }

    /**
     * Gets the maximum number of statements that can remain idle in the pool, without extra ones being released, or
     * negative for no limit.
     *
     * @param maxIdle The maximum number of statements that can remain idle
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setMaxIdle(final int maxIdle) {
        assertInitializationAllowed();
        this.maxIdle = maxIdle;
    }

    /**
     * Sets the maximum number of prepared statements.
     *
     * @param maxPreparedStatements the new maximum number of prepared statements
     */
    public void setMaxPreparedStatements(final int maxPreparedStatements) {
        this.maxPreparedStatements = maxPreparedStatements;
    }

    /**
     * Sets the minimum amount of time a statement may sit idle in the pool before it is eligible for eviction by the
     * idle object evictor (if any). When non-positive, no objects will be evicted from the pool due to idle time alone.
     *
     * @param minEvictableIdleDuration minimum time to set in milliseconds.
     * @see #getMinEvictableIdleDuration()
     * @see #setDurationBetweenEvictionRuns(Duration)
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called.
     * @since 2.9.0
     */
    public void setMinEvictableIdleDuration(final Duration minEvictableIdleDuration) {
        assertInitializationAllowed();
        this.minEvictableIdleDuration = minEvictableIdleDuration;
    }

    /**
     * Sets the minimum amount of time a statement may sit idle in the pool before it is eligible for eviction by the
     * idle object evictor (if any). When non-positive, no objects will be evicted from the pool due to idle time alone.
     *
     * @param minEvictableIdleTimeMillis minimum time to set in milliseconds.
     * @see #getMinEvictableIdleDuration()
     * @see #setDurationBetweenEvictionRuns(Duration)
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     * @deprecated Use {@link #setMinEvictableIdleDuration(Duration)}.
     */
    @Deprecated
    public void setMinEvictableIdleTimeMillis(final int minEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        this.minEvictableIdleDuration = Duration.ofMillis(minEvictableIdleTimeMillis);
    }

    /**
     * Sets the number of statements to examine during each run of the idle object evictor thread (if any).
     * <p>
     * When a negative value is supplied,
     * {@code ceil({@link BasicDataSource#getNumIdle})/abs({@link #getNumTestsPerEvictionRun})} tests will be run.
     * I.e., when the value is <em>-n</em>, roughly one <em>n</em>th of the idle objects will be tested per run.
     * </p>
     *
     * @param numTestsPerEvictionRun number of statements to examine per run
     * @see #getNumTestsPerEvictionRun()
     * @see #setDurationBetweenEvictionRuns(Duration)
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        assertInitializationAllowed();
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Sets the value of password for the default user.
     *
     * @param userPassword Value to assign to password.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setPassword(final char[] userPassword) {
        assertInitializationAllowed();
        this.userPassword = Utils.clone(userPassword);
        update(connectionProperties, Constants.KEY_PASSWORD, Utils.toString(this.userPassword));
    }

    /**
     * Sets the value of password for the default user.
     *
     * @param userPassword Value to assign to password.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setPassword(final String userPassword) {
        assertInitializationAllowed();
        this.userPassword = Utils.toCharArray(userPassword);
        update(connectionProperties, Constants.KEY_PASSWORD, userPassword);
    }

    /**
     * Whether to toggle the pooling of {@code PreparedStatement}s
     *
     * @param poolPreparedStatements true to pool statements.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setPoolPreparedStatements(final boolean poolPreparedStatements) {
        assertInitializationAllowed();
        this.poolPreparedStatements = poolPreparedStatements;
    }

    /**
     * Sets the number of milliseconds to sleep between runs of the idle object evictor thread. When non-positive, no
     * idle object evictor thread will be run.
     *
     * @param timeBetweenEvictionRunsMillis The number of milliseconds to sleep between runs of the idle object evictor
     *        thread. When non-positive, no idle object evictor thread will be run.
     * @see #getDurationBetweenEvictionRuns()
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     * @deprecated Use {@link #setDurationBetweenEvictionRuns(Duration)}.
     */
    @Deprecated
    public void setTimeBetweenEvictionRunsMillis(final long timeBetweenEvictionRunsMillis) {
        assertInitializationAllowed();
        this.durationBetweenEvictionRuns = Duration.ofMillis(timeBetweenEvictionRunsMillis);
    }

    /**
     * Sets the value of URL string used to locate the database for this data source.
     *
     * @param connectionString Value to assign to connection string.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setUrl(final String connectionString) {
        assertInitializationAllowed();
        this.connectionString = connectionString;
    }

    /**
     * Sets the value of default user (login or user name).
     *
     * @param userName Value to assign to user.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setUser(final String userName) {
        assertInitializationAllowed();
        this.userName = userName;
        update(connectionProperties, Constants.KEY_USER, userName);
    }

    /**
     * Does not print the userName and userPassword field nor the 'user' or 'password' in the connectionProperties.
     *
     * @since 2.6.0
     */
    @Override
    public synchronized String toString() {
        final StringBuilder builder = new StringBuilder(super.toString());
        builder.append("[description=");
        builder.append(description);
        builder.append(", connectionString=");
        // TODO What if the connection string contains a 'user' or 'password' query parameter but that connection string
        // is not in a legal URL format?
        builder.append(connectionString);
        builder.append(", driver=");
        builder.append(driver);
        builder.append(", loginTimeout=");
        builder.append(loginTimeout);
        builder.append(", poolPreparedStatements=");
        builder.append(poolPreparedStatements);
        builder.append(", maxIdle=");
        builder.append(maxIdle);
        builder.append(", timeBetweenEvictionRunsMillis=");
        builder.append(durationBetweenEvictionRuns);
        builder.append(", numTestsPerEvictionRun=");
        builder.append(numTestsPerEvictionRun);
        builder.append(", minEvictableIdleTimeMillis=");
        builder.append(minEvictableIdleDuration);
        builder.append(", maxPreparedStatements=");
        builder.append(maxPreparedStatements);
        builder.append(", getConnectionCalled=");
        builder.append(getConnectionCalled);
        builder.append(", connectionProperties=");
        builder.append(Utils.cloneWithoutCredentials(connectionProperties));
        builder.append(", accessToUnderlyingConnectionAllowed=");
        builder.append(accessToUnderlyingConnectionAllowed);
        builder.append("]");
        return builder.toString();
    }

    private void update(final Properties properties, final String key, final String value) {
        if (properties != null && key != null) {
            if (value == null) {
                properties.remove(key);
            } else {
                properties.setProperty(key, value);
            }
        }
    }
}

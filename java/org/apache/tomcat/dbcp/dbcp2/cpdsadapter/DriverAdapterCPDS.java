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
 * still include a {@link java.sql.DriverManager} implementation. <code>ConnectionPoolDataSource</code>s are not used
 * within general applications. They are used by <code>DataSource</code> implementations that pool
 * <code>Connection</code>s, such as {@link org.apache.tomcat.dbcp.dbcp2.datasources.SharedPoolDataSource}. A J2EE container
 * will normally provide some method of initializing the <code>ConnectionPoolDataSource</code> whose attributes are
 * presented as bean getters/setters and then deploying it via JNDI. It is then available as a source of physical
 * connections to the database, when the pooling <code>DataSource</code> needs to create a new physical connection.
 * </p>
 * <p>
 * Although normally used within a JNDI environment, the DriverAdapterCPDS can be instantiated and initialized as any
 * bean and then attached directly to a pooling <code>DataSource</code>. <code>Jdbc2PoolDataSource</code> can use the
 * <code>ConnectionPoolDataSource</code> with or without the use of JNDI.
 * </p>
 * <p>
 * The DriverAdapterCPDS also provides <code>PreparedStatement</code> pooling which is not generally available in jdbc2
 * <code>ConnectionPoolDataSource</code> implementation, but is addressed within the jdbc3 specification. The
 * <code>PreparedStatement</code> pool in DriverAdapterCPDS has been in the dbcp package for some time, but it has not
 * undergone extensive testing in the configuration used here. It should be considered experimental and can be toggled
 * with the poolPreparedStatements attribute.
 * </p>
 * <p>
 * The <a href="package-summary.html">package documentation</a> contains an example using catalina and JNDI. The
 * <a href="../datasources/package-summary.html">datasources package documentation</a> shows how to use
 * <code>DriverAdapterCPDS</code> as a source for <code>Jdbc2PoolDataSource</code> without the use of JNDI.
 * </p>
 *
 * @since 2.0
 */
public class DriverAdapterCPDS implements ConnectionPoolDataSource, Referenceable, Serializable, ObjectFactory {

    private static final String KEY_USER = "user";

    private static final String KEY_PASSWORD = "password";

    private static final long serialVersionUID = -4820523787212147844L;

    private static final String GET_CONNECTION_CALLED = "A PooledConnection was already requested from this source, "
            + "further initialization is not allowed.";

    /** Description */
    private String description;

    /** Url name */
    private String url;

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
    private long timeBetweenEvictionRunsMillis = BaseObjectPoolConfig.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
    private int numTestsPerEvictionRun = -1;
    private int minEvictableIdleTimeMillis = -1;
    private int maxPreparedStatements = -1;

    /** Whether or not getConnection has been called */
    private volatile boolean getConnectionCalled;

    /** Connection properties passed to JDBC Driver */
    private Properties connectionProperties;

    static {
        // Attempt to prevent deadlocks - see DBCP - 272
        DriverManager.getDrivers();
    }

    /**
     * Controls access to the underlying connection
     */
    private boolean accessToUnderlyingConnectionAllowed;

    /**
     * Default no-arg constructor for Serialization
     */
    public DriverAdapterCPDS() {
    }

    /**
     * Attempts to establish a database connection using the default user and password.
     */
    @Override
    public PooledConnection getPooledConnection() throws SQLException {
        return getPooledConnection(getUser(), getPassword());
    }

    /**
     * Attempt to establish a database connection.
     *
     * @param pooledUserName
     *            name to be used for the connection
     * @param pooledUserPassword
     *            password to be used fur the connection
     */
    @Override
    public PooledConnection getPooledConnection(final String pooledUserName, final String pooledUserPassword)
            throws SQLException {
        getConnectionCalled = true;
        PooledConnectionImpl pooledConnection = null;
        // Workaround for buggy WebLogic 5.1 classloader - ignore the exception upon first invocation.
        try {
            if (connectionProperties != null) {
                update(connectionProperties, KEY_USER, pooledUserName);
                update(connectionProperties, KEY_PASSWORD, pooledUserPassword);
                pooledConnection = new PooledConnectionImpl(
                        DriverManager.getConnection(getUrl(), connectionProperties));
            } else {
                pooledConnection = new PooledConnectionImpl(
                        DriverManager.getConnection(getUrl(), pooledUserName, pooledUserPassword));
            }
            pooledConnection.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
        } catch (final ClassCircularityError e) {
            if (connectionProperties != null) {
                pooledConnection = new PooledConnectionImpl(
                        DriverManager.getConnection(getUrl(), connectionProperties));
            } else {
                pooledConnection = new PooledConnectionImpl(
                        DriverManager.getConnection(getUrl(), pooledUserName, pooledUserPassword));
            }
            pooledConnection.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
        }
        KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> stmtPool = null;
        if (isPoolPreparedStatements()) {
            final GenericKeyedObjectPoolConfig<DelegatingPreparedStatement> config = new GenericKeyedObjectPoolConfig<>();
            config.setMaxTotalPerKey(Integer.MAX_VALUE);
            config.setBlockWhenExhausted(false);
            config.setMaxWaitMillis(0);
            config.setMaxIdlePerKey(getMaxIdle());
            if (getMaxPreparedStatements() <= 0) {
                // since there is no limit, create a prepared statement pool with an eviction thread;
                // evictor settings are the same as the connection pool settings.
                config.setTimeBetweenEvictionRunsMillis(getTimeBetweenEvictionRunsMillis());
                config.setNumTestsPerEvictionRun(getNumTestsPerEvictionRun());
                config.setMinEvictableIdleTimeMillis(getMinEvictableIdleTimeMillis());
            } else {
                // since there is a limit, create a prepared statement pool without an eviction thread;
                // pool has LRU functionality so when the limit is reached, 15% of the pool is cleared.
                // see org.apache.commons.pool2.impl.GenericKeyedObjectPool.clearOldest method
                config.setMaxTotal(getMaxPreparedStatements());
                config.setTimeBetweenEvictionRunsMillis(-1);
                config.setNumTestsPerEvictionRun(0);
                config.setMinEvictableIdleTimeMillis(0);
            }
            stmtPool = new GenericKeyedObjectPool<>(pooledConnection, config);
            pooledConnection.setStatementPool(stmtPool);
        }
        return pooledConnection;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    // ----------------------------------------------------------------------
    // Referenceable implementation

    /**
     * <CODE>Referenceable</CODE> implementation.
     */
    @Override
    public Reference getReference() throws NamingException {
        // this class implements its own factory
        final String factory = getClass().getName();

        final Reference ref = new Reference(getClass().getName(), factory, null);

        ref.add(new StringRefAddr("description", getDescription()));
        ref.add(new StringRefAddr("driver", getDriver()));
        ref.add(new StringRefAddr("loginTimeout", String.valueOf(getLoginTimeout())));
        ref.add(new StringRefAddr(KEY_PASSWORD, getPassword()));
        ref.add(new StringRefAddr(KEY_USER, getUser()));
        ref.add(new StringRefAddr("url", getUrl()));

        ref.add(new StringRefAddr("poolPreparedStatements", String.valueOf(isPoolPreparedStatements())));
        ref.add(new StringRefAddr("maxIdle", String.valueOf(getMaxIdle())));
        ref.add(new StringRefAddr("timeBetweenEvictionRunsMillis", String.valueOf(getTimeBetweenEvictionRunsMillis())));
        ref.add(new StringRefAddr("numTestsPerEvictionRun", String.valueOf(getNumTestsPerEvictionRun())));
        ref.add(new StringRefAddr("minEvictableIdleTimeMillis", String.valueOf(getMinEvictableIdleTimeMillis())));
        ref.add(new StringRefAddr("maxPreparedStatements", String.valueOf(getMaxPreparedStatements())));

        return ref;
    }

    // ----------------------------------------------------------------------
    // ObjectFactory implementation

    /**
     * implements ObjectFactory to create an instance of this class
     */
    @Override
    public Object getObjectInstance(final Object refObj, final Name name, final Context context,
            final Hashtable<?, ?> env) throws Exception {
        // The spec says to return null if we can't create an instance
        // of the reference
        DriverAdapterCPDS cpds = null;
        if (refObj instanceof Reference) {
            final Reference ref = (Reference) refObj;
            if (ref.getClassName().equals(getClass().getName())) {
                RefAddr ra = ref.get("description");
                if (ra != null && ra.getContent() != null) {
                    setDescription(ra.getContent().toString());
                }

                ra = ref.get("driver");
                if (ra != null && ra.getContent() != null) {
                    setDriver(ra.getContent().toString());
                }
                ra = ref.get("url");
                if (ra != null && ra.getContent() != null) {
                    setUrl(ra.getContent().toString());
                }
                ra = ref.get(KEY_USER);
                if (ra != null && ra.getContent() != null) {
                    setUser(ra.getContent().toString());
                }
                ra = ref.get(KEY_PASSWORD);
                if (ra != null && ra.getContent() != null) {
                    setPassword(ra.getContent().toString());
                }

                ra = ref.get("poolPreparedStatements");
                if (ra != null && ra.getContent() != null) {
                    setPoolPreparedStatements(Boolean.valueOf(ra.getContent().toString()).booleanValue());
                }
                ra = ref.get("maxIdle");
                if (ra != null && ra.getContent() != null) {
                    setMaxIdle(Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("timeBetweenEvictionRunsMillis");
                if (ra != null && ra.getContent() != null) {
                    setTimeBetweenEvictionRunsMillis(Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("numTestsPerEvictionRun");
                if (ra != null && ra.getContent() != null) {
                    setNumTestsPerEvictionRun(Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("minEvictableIdleTimeMillis");
                if (ra != null && ra.getContent() != null) {
                    setMinEvictableIdleTimeMillis(Integer.parseInt(ra.getContent().toString()));
                }
                ra = ref.get("maxPreparedStatements");
                if (ra != null && ra.getContent() != null) {
                    setMaxPreparedStatements(Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("accessToUnderlyingConnectionAllowed");
                if (ra != null && ra.getContent() != null) {
                    setAccessToUnderlyingConnectionAllowed(Boolean.valueOf(ra.getContent().toString()).booleanValue());
                }

                cpds = this;
            }
        }
        return cpds;
    }

    /**
     * Throws an IllegalStateException, if a PooledConnection has already been requested.
     */
    private void assertInitializationAllowed() throws IllegalStateException {
        if (getConnectionCalled) {
            throw new IllegalStateException(GET_CONNECTION_CALLED);
        }
    }

    // ----------------------------------------------------------------------
    // Properties

    /**
     * Gets the connection properties passed to the JDBC driver.
     *
     * @return the JDBC connection properties used when creating connections.
     */
    public Properties getConnectionProperties() {
        return connectionProperties;
    }

    /**
     * <p>
     * Sets the connection properties passed to the JDBC driver.
     * </p>
     *
     * <p>
     * If <code>props</code> contains "user" and/or "password" properties, the corresponding instance properties are
     * set. If these properties are not present, they are filled in using {@link #getUser()}, {@link #getPassword()}
     * when {@link #getPooledConnection()} is called, or using the actual parameters to the method call when
     * {@link #getPooledConnection(String, String)} is called. Calls to {@link #setUser(String)} or
     * {@link #setPassword(String)} overwrite the values of these properties if <code>connectionProperties</code> is not
     * null.
     * </p>
     *
     * @param props
     *            Connection properties to use when creating new connections.
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     */
    public void setConnectionProperties(final Properties props) {
        assertInitializationAllowed();
        connectionProperties = props;
        if (connectionProperties != null) {
            if (connectionProperties.containsKey(KEY_USER)) {
                setUser(connectionProperties.getProperty(KEY_USER));
            }
            if (connectionProperties.containsKey(KEY_PASSWORD)) {
                setPassword(connectionProperties.getProperty(KEY_PASSWORD));
            }
        }
    }

    /**
     * Gets the value of description. This property is here for use by the code which will deploy this datasource. It is
     * not used internally.
     *
     * @return value of description, may be null.
     * @see #setDescription(String)
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the value of description. This property is here for use by the code which will deploy this datasource. It is
     * not used internally.
     *
     * @param v
     *            Value to assign to description.
     */
    public void setDescription(final String v) {
        this.description = v;
    }

    /**
     * Gets the value of password for the default user.
     *
     * @return value of password.
     * @since 2.4.0
     */
    public char[] getPasswordCharArray() {
        return userPassword;
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
     * Sets the value of password for the default user.
     *
     * @param userPassword
     *            Value to assign to password.
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     */
    public void setPassword(final char[] userPassword) {
        assertInitializationAllowed();
        this.userPassword = userPassword;
        update(connectionProperties, KEY_PASSWORD, Utils.toString(userPassword));
    }

    /**
     * Sets the value of password for the default user.
     *
     * @param userPassword
     *            Value to assign to password.
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     */
    public void setPassword(final String userPassword) {
        assertInitializationAllowed();
        this.userPassword = Utils.toCharArray(userPassword);
        update(connectionProperties, KEY_PASSWORD, userPassword);
    }

    /**
     * Gets the value of url used to locate the database for this datasource.
     *
     * @return value of url.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the value of URL string used to locate the database for this datasource.
     *
     * @param v
     *            Value to assign to url.
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     */
    public void setUrl(final String v) {
        assertInitializationAllowed();
        this.url = v;
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
     * Sets the value of default user (login or user name).
     *
     * @param v
     *            Value to assign to user.
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     */
    public void setUser(final String v) {
        assertInitializationAllowed();
        this.userName = v;
        update(connectionProperties, KEY_USER, v);
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
     * Sets the driver class name. Setting the driver class name cause the driver to be registered with the
     * DriverManager.
     *
     * @param v
     *            Value to assign to driver.
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     * @throws ClassNotFoundException
     *             if the class cannot be located
     */
    public void setDriver(final String v) throws ClassNotFoundException {
        assertInitializationAllowed();
        this.driver = v;
        // make sure driver is registered
        Class.forName(v);
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
     * Sets the maximum time in seconds that this data source will wait while attempting to connect to a database. NOT
     * USED.
     */
    @Override
    public void setLoginTimeout(final int seconds) {
        loginTimeout = seconds;
    }

    /**
     * Sets the log writer for this data source. NOT USED.
     */
    @Override
    public void setLogWriter(final PrintWriter out) {
        logWriter = out;
    }

    // ------------------------------------------------------------------
    // PreparedStatement pool properties

    /**
     * Flag to toggle the pooling of <code>PreparedStatement</code>s
     *
     * @return value of poolPreparedStatements.
     */
    public boolean isPoolPreparedStatements() {
        return poolPreparedStatements;
    }

    /**
     * Flag to toggle the pooling of <code>PreparedStatement</code>s
     *
     * @param poolPreparedStatements
     *            true to pool statements.
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     */
    public void setPoolPreparedStatements(final boolean poolPreparedStatements) {
        assertInitializationAllowed();
        this.poolPreparedStatements = poolPreparedStatements;
    }

    /**
     * Gets the maximum number of statements that can remain idle in the pool, without extra ones being released, or
     * negative for no limit.
     *
     * @return the value of maxIdle
     */
    public int getMaxIdle() {
        return this.maxIdle;
    }

    /**
     * Gets the maximum number of statements that can remain idle in the pool, without extra ones being released, or
     * negative for no limit.
     *
     * @param maxIdle
     *            The maximum number of statements that can remain idle
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     */
    public void setMaxIdle(final int maxIdle) {
        assertInitializationAllowed();
        this.maxIdle = maxIdle;
    }

    /**
     * Gets the number of milliseconds to sleep between runs of the idle object evictor thread. When non-positive, no
     * idle object evictor thread will be run.
     *
     * @return the value of the evictor thread timer
     * @see #setTimeBetweenEvictionRunsMillis(long)
     */
    public long getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    /**
     * Sets the number of milliseconds to sleep between runs of the idle object evictor thread. When non-positive, no
     * idle object evictor thread will be run.
     *
     * @param timeBetweenEvictionRunsMillis
     *            The number of milliseconds to sleep between runs of the idle object evictor thread. When non-positive,
     *            no idle object evictor thread will be run.
     * @see #getTimeBetweenEvictionRunsMillis()
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     */
    public void setTimeBetweenEvictionRunsMillis(final long timeBetweenEvictionRunsMillis) {
        assertInitializationAllowed();
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
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
     * Sets the number of statements to examine during each run of the idle object evictor thread (if any).
     * <p>
     * When a negative value is supplied, <tt>ceil({*link #numIdle})/abs({*link #getNumTestsPerEvictionRun})</tt> tests
     * will be run. I.e., when the value is <i>-n</i>, roughly one <i>n</i>th of the idle objects will be tested per
     * run.
     * </p>
     *
     * @param numTestsPerEvictionRun
     *            number of statements to examine per run
     * @see #getNumTestsPerEvictionRun()
     * @see #setTimeBetweenEvictionRunsMillis(long)
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     */
    public void setNumTestsPerEvictionRun(final int numTestsPerEvictionRun) {
        assertInitializationAllowed();
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Gets the minimum amount of time a statement may sit idle in the pool before it is eligible for eviction by the
     * idle object evictor (if any).
     *
     * @see #setMinEvictableIdleTimeMillis
     * @see #setTimeBetweenEvictionRunsMillis
     * @return the minimum amount of time a statement may sit idle in the pool.
     */
    public int getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum amount of time a statement may sit idle in the pool before it is eligible for eviction by the
     * idle object evictor (if any). When non-positive, no objects will be evicted from the pool due to idle time alone.
     *
     * @param minEvictableIdleTimeMillis
     *            minimum time to set (in ms)
     * @see #getMinEvictableIdleTimeMillis()
     * @see #setTimeBetweenEvictionRunsMillis(long)
     * @throws IllegalStateException
     *             if {@link #getPooledConnection()} has been called
     */
    public void setMinEvictableIdleTimeMillis(final int minEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    /**
     * Returns the value of the accessToUnderlyingConnectionAllowed property.
     *
     * @return true if access to the underlying is allowed, false otherwise.
     */
    public synchronized boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    /**
     * Sets the value of the accessToUnderlyingConnectionAllowed property. It controls if the PoolGuard allows access to
     * the underlying connection. (Default: false)
     *
     * @param allow
     *            Access to the underlying connection is granted when true.
     */
    public synchronized void setAccessToUnderlyingConnectionAllowed(final boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
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
     * Sets the maximum number of prepared statements.
     *
     * @param maxPreparedStatements
     *            the new maximum number of prepared statements
     */
    public void setMaxPreparedStatements(final int maxPreparedStatements) {
        this.maxPreparedStatements = maxPreparedStatements;
    }

    private void update(final Properties properties, final String key, final String value) {
        if (properties != null) {
            if (value == null) {
                properties.remove(key);
            } else {
                properties.setProperty(key, value);
            }
        }
    }
}

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

package org.apache.tomcat.dbcp.dbcp.cpdsadapter;

import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Logger;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import javax.sql.PooledConnection;
import javax.sql.ConnectionPoolDataSource;
import javax.naming.Name;
import javax.naming.Context;
import javax.naming.Referenceable;
import javax.naming.spi.ObjectFactory;
import javax.naming.Reference;
import javax.naming.RefAddr;
import javax.naming.StringRefAddr;
import javax.naming.NamingException;

import org.apache.tomcat.dbcp.dbcp.DelegatingPreparedStatement;
import org.apache.tomcat.dbcp.dbcp.PStmtKey;
import org.apache.tomcat.dbcp.pool.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool.impl.GenericKeyedObjectPool;

/**
 * <p>
 * An adapter for JDBC drivers that do not include an implementation
 * of {@link javax.sql.ConnectionPoolDataSource}, but still include a
 * {@link java.sql.DriverManager} implementation.
 * <code>ConnectionPoolDataSource</code>s are not used within general
 * applications.  They are used by <code>DataSource</code> implementations
 * that pool <code>Connection</code>s, such as
 * {@link org.apache.tomcat.dbcp.dbcp.datasources.SharedPoolDataSource}.  A J2EE
 * container will normally provide some method of initializing the
 * <code>ConnectionPoolDataSource</code> whose attributes are presented
 * as bean getters/setters and then deploying it via JNDI.  It is then
 * available as a source of physical connections to the database, when
 * the pooling <code>DataSource</code> needs to create a new
 * physical connection.
 * </p>
 *
 * <p>
 * Although normally used within a JNDI environment, the DriverAdapterCPDS
 * can be instantiated and initialized as any bean and then attached
 * directly to a pooling <code>DataSource</code>.
 * <code>Jdbc2PoolDataSource</code> can use the
 * <code>ConnectionPoolDataSource</code> with or without the use of JNDI.
 * </p>
 *
 * <p>
 * The DriverAdapterCPDS also provides <code>PreparedStatement</code> pooling
 * which is not generally available in jbdc2
 * <code>ConnectionPoolDataSource</code> implementation, but is
 * addressed within the JDBC3 specification.  The <code>PreparedStatement</code>
 * pool in DriverAdapterCPDS has been in the dbcp package for some time, but
 * it has not undergone extensive testing in the configuration used here.
 * It should be considered experimental and can be toggled with the
 * poolPreparedStatements attribute.
 * </p>
 *
 * <p>
 * The <a href="package-summary.html">package documentation</a> contains an
 * example using catalina and JNDI.  The <a
 * href="../datasources/package-summary.html">datasources package documentation</a>
 * shows how to use <code>DriverAdapterCPDS</code> as a source for
 * <code>Jdbc2PoolDataSource</code> without the use of JNDI.
 * </p>
 *
 * @author John D. McNally
 */
public class DriverAdapterCPDS
    implements ConnectionPoolDataSource, Referenceable, Serializable,
               ObjectFactory {

    private static final long serialVersionUID = -4820523787212147844L;


    private static final String GET_CONNECTION_CALLED
            = "A PooledConnection was already requested from this source, "
            + "further initialization is not allowed.";

    /** Description */
    private String description;
    /** Password */
    private String password;
    /** Url name */
    private String url;
    /** User name */
    private String user;
    /** Driver class name */
    private String driver;

    /** Login TimeOut in seconds */
    private int loginTimeout;
    /** Log stream. NOT USED */
    private transient PrintWriter logWriter = null;

    // PreparedStatement pool properties
    private boolean poolPreparedStatements;
    private int maxActive = 10;
    private int maxIdle = 10;
    private int _timeBetweenEvictionRunsMillis = -1;
    private int _numTestsPerEvictionRun = -1;
    private int _minEvictableIdleTimeMillis = -1;
    private int _maxPreparedStatements = -1;

    /** Whether or not getConnection has been called */
    private volatile boolean getConnectionCalled = false;

    /** Connection properties passed to JDBC Driver */
    private Properties connectionProperties = null;

    static {
        // Attempt to prevent deadlocks - see DBCP - 272
        DriverManager.getDrivers();
    }

    /**
     * Controls access to the underlying connection
     */
    private boolean accessToUnderlyingConnectionAllowed = false;

    /**
     * Default no-arg constructor for Serialization
     */
    public DriverAdapterCPDS() {
    }

    /**
     * Attempt to establish a database connection using the default
     * user and password.
     */
    @Override
    public PooledConnection getPooledConnection() throws SQLException {
        return getPooledConnection(getUser(), getPassword());
    }

    /**
     * Attempt to establish a database connection.
     * @param username name to be used for the connection
     * @param pass password to be used fur the connection
     */
    @Override
    public PooledConnection getPooledConnection(String username,
                                                String pass)
            throws SQLException {
        getConnectionCalled = true;
        /*
        public GenericKeyedObjectPool(KeyedPoolableObjectFactory factory,
        int maxActive, byte whenExhaustedAction, long maxWait,
        int maxIdle, boolean testOnBorrow, boolean testOnReturn,
        long timeBetweenEvictionRunsMillis,
        int numTestsPerEvictionRun, long minEvictableIdleTimeMillis,
        boolean testWhileIdle) {
        */
        KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> stmtPool = null;
        if (isPoolPreparedStatements()) {
            if (getMaxPreparedStatements() <= 0)
            {
                // since there is no limit, create a prepared statement pool with an eviction thread
                //  evictor settings are the same as the connection pool settings.
                stmtPool = new GenericKeyedObjectPool<PStmtKey, DelegatingPreparedStatement>(null,
                    getMaxActive(), GenericKeyedObjectPool.WHEN_EXHAUSTED_GROW, 0,
                    getMaxIdle(), false, false,
                    getTimeBetweenEvictionRunsMillis(),getNumTestsPerEvictionRun(),getMinEvictableIdleTimeMillis(),
                    false);
            }
            else
            {
                // since there is limit, create a prepared statement pool without an eviction thread
                //  pool has LRU functionality so when the limit is reached, 15% of the pool is cleared.
                // see org.apache.tomcat.dbcp.pool.impl.GenericKeyedObjectPool.clearOldest method
                stmtPool = new GenericKeyedObjectPool<PStmtKey, DelegatingPreparedStatement>(null,
                    getMaxActive(), GenericKeyedObjectPool.WHEN_EXHAUSTED_GROW, 0,
                    getMaxIdle(), getMaxPreparedStatements(), false, false,
                    -1,0,0, // -1 tells the pool that there should be no eviction thread.
                    false);
            }
        }
        // Workaround for buggy WebLogic 5.1 classloader - ignore the
        // exception upon first invocation.
        try {
            PooledConnectionImpl pci = null;
            if (connectionProperties != null) {
                connectionProperties.put("user", username);
                connectionProperties.put("password", pass);
                pci = new PooledConnectionImpl(
                        DriverManager.getConnection(getUrl(), connectionProperties),
                        stmtPool);
            } else {
                pci = new PooledConnectionImpl(
                        DriverManager.getConnection(getUrl(), username, pass),
                        stmtPool);
            }
            pci.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
            return pci;
        }
        catch (ClassCircularityError e)
        {
            PooledConnectionImpl pci = null;
            if (connectionProperties != null) {
                pci = new PooledConnectionImpl(
                        DriverManager.getConnection(getUrl(), connectionProperties),
                        stmtPool);
            } else {
                pci = new PooledConnectionImpl(
                        DriverManager.getConnection(getUrl(), username, pass),
                        stmtPool);
            }
            pci.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
            return pci;
        }
    }

    // ----------------------------------------------------------------------
    // Referenceable implementation

    /**
     * <CODE>Referenceable</CODE> implementation.
     */
    @Override
    public Reference getReference() throws NamingException {
        // this class implements its own factory
        String factory = getClass().getName();

        Reference ref = new Reference(getClass().getName(), factory, null);

        ref.add(new StringRefAddr("description", getDescription()));
        ref.add(new StringRefAddr("driver", getDriver()));
        ref.add(new StringRefAddr("loginTimeout",
                                  String.valueOf(getLoginTimeout())));
        ref.add(new StringRefAddr("password", getPassword()));
        ref.add(new StringRefAddr("user", getUser()));
        ref.add(new StringRefAddr("url", getUrl()));

        ref.add(new StringRefAddr("poolPreparedStatements",
                                  String.valueOf(isPoolPreparedStatements())));
        ref.add(new StringRefAddr("maxActive",
                                  String.valueOf(getMaxActive())));
        ref.add(new StringRefAddr("maxIdle",
                                  String.valueOf(getMaxIdle())));
        ref.add(new StringRefAddr("timeBetweenEvictionRunsMillis",
            String.valueOf(getTimeBetweenEvictionRunsMillis())));
        ref.add(new StringRefAddr("numTestsPerEvictionRun",
            String.valueOf(getNumTestsPerEvictionRun())));
        ref.add(new StringRefAddr("minEvictableIdleTimeMillis",
            String.valueOf(getMinEvictableIdleTimeMillis())));
        ref.add(new StringRefAddr("maxPreparedStatements",
            String.valueOf(getMaxPreparedStatements())));

        return ref;
    }


    // ----------------------------------------------------------------------
    // ObjectFactory implementation

    /**
     * implements ObjectFactory to create an instance of this class
     */
    @Override
    public Object getObjectInstance(Object refObj, Name name,
                                    Context context, Hashtable<?, ?> env)
            throws Exception {
        // The spec says to return null if we can't create an instance
        // of the reference
        DriverAdapterCPDS cpds = null;
        if (refObj instanceof Reference) {
            Reference ref = (Reference)refObj;
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
                ra = ref.get("user");
                if (ra != null && ra.getContent() != null) {
                    setUser(ra.getContent().toString());
                }
                ra = ref.get("password");
                if (ra != null && ra.getContent() != null) {
                    setPassword(ra.getContent().toString());
                }

                ra = ref.get("poolPreparedStatements");
                if (ra != null && ra.getContent() != null) {
                    setPoolPreparedStatements(Boolean.valueOf(
                        ra.getContent().toString()).booleanValue());
                }
                ra = ref.get("maxActive");
                if (ra != null && ra.getContent() != null) {
                    setMaxActive(Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("maxIdle");
                if (ra != null && ra.getContent() != null) {
                    setMaxIdle(Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("timeBetweenEvictionRunsMillis");
                if (ra != null && ra.getContent() != null) {
                    setTimeBetweenEvictionRunsMillis(
                        Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("numTestsPerEvictionRun");
                if (ra != null && ra.getContent() != null) {
                    setNumTestsPerEvictionRun(
                        Integer.parseInt(ra.getContent().toString()));
                }

                ra = ref.get("minEvictableIdleTimeMillis");
                if (ra != null && ra.getContent() != null) {
                    setMinEvictableIdleTimeMillis(
                        Integer.parseInt(ra.getContent().toString()));
                }
                ra = ref.get("maxPreparedStatements");
                if (ra != null && ra.getContent() != null) {
                    setMaxPreparedStatements(
                        Integer.parseInt(ra.getContent().toString()));
                }

                cpds = this;
            }
        }
        return cpds;
    }

    /**
     * Throws an IllegalStateException, if a PooledConnection has already
     * been requested.
     */
    private void assertInitializationAllowed() throws IllegalStateException {
        if (getConnectionCalled) {
            throw new IllegalStateException(GET_CONNECTION_CALLED);
        }
    }

    // ----------------------------------------------------------------------
    // Properties

    /**
     * Get the connection properties passed to the JDBC driver.
     *
     * @return the JDBC connection properties used when creating connections.
     * @since 1.3
     */
    public Properties getConnectionProperties() {
        return connectionProperties;
    }

    /**
     * <p>Set the connection properties passed to the JDBC driver.</p>
     *
     * <p>If <code>props</code> contains "user" and/or "password"
     * properties, the corresponding instance properties are set. If these
     * properties are not present, they are filled in using
     * {@link #getUser()}, {@link #getPassword()} when {@link #getPooledConnection()}
     * is called, or using the actual parameters to the method call when
     * {@link #getPooledConnection(String, String)} is called. Calls to
     * {@link #setUser(String)} or {@link #setPassword(String)} overwrite the values
     * of these properties if <code>connectionProperties</code> is not null.</p>
     *
     * @param props Connection properties to use when creating new connections.
     * @since 1.3
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setConnectionProperties(Properties props) {
        assertInitializationAllowed();
        connectionProperties = props;
        if (connectionProperties.containsKey("user")) {
            setUser(connectionProperties.getProperty("user"));
        }
        if (connectionProperties.containsKey("password")) {
            setPassword(connectionProperties.getProperty("password"));
        }
    }

    /**
     * Get the value of description.  This property is here for use by
     * the code which will deploy this datasource.  It is not used
     * internally.
     *
     * @return value of description, may be null.
     * @see #setDescription(String)
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the value of description.  This property is here for use by
     * the code which will deploy this datasource.  It is not used
     * internally.
     *
     * @param v  Value to assign to description.
     */
    public void setDescription(String  v) {
        this.description = v;
    }

    /**
     * Get the value of password for the default user.
     * @return value of password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Set the value of password for the default user.
     * @param v  Value to assign to password.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setPassword(String v) {
        assertInitializationAllowed();
        this.password = v;
        if (connectionProperties != null) {
            connectionProperties.setProperty("password", v);
        }
    }

    /**
     * Get the value of url used to locate the database for this datasource.
     * @return value of url.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Set the value of url used to locate the database for this datasource.
     * @param v  Value to assign to url.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
    */
    public void setUrl(String v) {
        assertInitializationAllowed();
        this.url = v;
    }

    /**
     * Get the value of default user (login or username).
     * @return value of user.
     */
    public String getUser() {
        return user;
    }

    /**
     * Set the value of default user (login or username).
     * @param v  Value to assign to user.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setUser(String v) {
        assertInitializationAllowed();
        this.user = v;
        if (connectionProperties != null) {
            connectionProperties.setProperty("user", v);
        }
    }

    /**
     * Get the driver classname.
     * @return value of driver.
     */
    public String getDriver() {
        return driver;
    }

    /**
     * Set the driver classname.  Setting the driver classname cause the
     * driver to be registered with the DriverManager.
     * @param v  Value to assign to driver.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setDriver(String v) throws ClassNotFoundException {
        assertInitializationAllowed();
        this.driver = v;
        // make sure driver is registered
        Class.forName(v);
    }

    /**
     * Gets the maximum time in seconds that this data source can wait
     * while attempting to connect to a database. NOT USED.
     */
    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    /**
     * Get the log writer for this data source. NOT USED.
     */
    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    /**
     * Sets the maximum time in seconds that this data source will wait
     * while attempting to connect to a database. NOT USED.
     */
    @Override
    public void setLoginTimeout(int seconds) {
        loginTimeout = seconds;
    }

    /**
     * Set the log writer for this data source. NOT USED.
     */
    @Override
    public void setLogWriter(java.io.PrintWriter out) {
        logWriter = out;
    }


    // ------------------------------------------------------------------
    // PreparedStatement pool properties


    /**
     * Flag to toggle the pooling of <code>PreparedStatement</code>s
     * @return value of poolPreparedStatements.
     */
    public boolean isPoolPreparedStatements() {
        return poolPreparedStatements;
    }

    /**
     * Flag to toggle the pooling of <code>PreparedStatement</code>s
     * @param v  true to pool statements.
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setPoolPreparedStatements(boolean v) {
        assertInitializationAllowed();
        this.poolPreparedStatements = v;
    }

    /**
     * The maximum number of active statements that can be allocated from
     * this pool at the same time, or non-positive for no limit.
     */
    public int getMaxActive() {
        return (this.maxActive);
    }

    /**
     * The maximum number of active statements that can be allocated from
     * this pool at the same time, or non-positive for no limit.
     * @param maxActive the maximum number of concurrent active statements allowed
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setMaxActive(int maxActive) {
        assertInitializationAllowed();
        this.maxActive = maxActive;
    }

    /**
     * The maximum number of statements that can remain idle in the
     * pool, without extra ones being released, or negative for no limit.
     * @return the value of maxIdle
     */
    public int getMaxIdle() {
        return (this.maxIdle);
    }

    /**
     * The maximum number of statements that can remain idle in the
     * pool, without extra ones being released, or negative for no limit.
     *
     * @param maxIdle The maximum number of statements that can remain idle
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setMaxIdle(int maxIdle) {
        assertInitializationAllowed();
        this.maxIdle = maxIdle;
    }

    /**
     * Returns the number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     * @return the value of the evictor thread timer
     * @see #setTimeBetweenEvictionRunsMillis(int)
     */
    public int getTimeBetweenEvictionRunsMillis() {
        return _timeBetweenEvictionRunsMillis;
    }

    /**
     * Sets the number of milliseconds to sleep between runs of the
     * idle object evictor thread.
     * When non-positive, no idle object evictor thread will be
     * run.
     * @param timeBetweenEvictionRunsMillis
     * @see #getTimeBetweenEvictionRunsMillis()
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setTimeBetweenEvictionRunsMillis(
            int timeBetweenEvictionRunsMillis) {
        assertInitializationAllowed();
        _timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

    /**
     * Returns the number of statements to examine during each run of the
     * idle object evictor thread (if any).
     *
     * *see #setNumTestsPerEvictionRun
     * *see #setTimeBetweenEvictionRunsMillis
     */
    public int getNumTestsPerEvictionRun() {
        return _numTestsPerEvictionRun;
    }

    /**
     * Sets the number of statements to examine during each run of the
     * idle object evictor thread (if any).
     * <p>
     * When a negative value is supplied, <code>ceil({*link #numIdle})/abs({*link #getNumTestsPerEvictionRun})</code>
     * tests will be run.  I.e., when the value is <i>-n</i>, roughly one <i>n</i>th of the
     * idle objects will be tested per run.
     *
     * @param numTestsPerEvictionRun number of statements to examine per run
     * @see #getNumTestsPerEvictionRun()
     * @see #setTimeBetweenEvictionRunsMillis(int)
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        assertInitializationAllowed();
        _numTestsPerEvictionRun = numTestsPerEvictionRun;
    }

    /**
     * Returns the minimum amount of time a statement may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor
     * (if any).
     *
     * *see #setMinEvictableIdleTimeMillis
     * *see #setTimeBetweenEvictionRunsMillis
     */
    public int getMinEvictableIdleTimeMillis() {
        return _minEvictableIdleTimeMillis;
    }

    /**
     * Sets the minimum amount of time a statement may sit idle in the pool
     * before it is eligable for eviction by the idle object evictor
     * (if any).
     * When non-positive, no objects will be evicted from the pool
     * due to idle time alone.
     * @param minEvictableIdleTimeMillis minimum time to set (in ms)
     * @see #getMinEvictableIdleTimeMillis()
     * @see #setTimeBetweenEvictionRunsMillis(int)
     * @throws IllegalStateException if {@link #getPooledConnection()} has been called
     */
    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        _minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
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
     * Sets the value of the accessToUnderlyingConnectionAllowed property.
     * It controls if the PoolGuard allows access to the underlying connection.
     * (Default: false)
     *
     * @param allow Access to the underlying connection is granted when true.
     */
    public synchronized void setAccessToUnderlyingConnectionAllowed(boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }

    /**
     * Returns the maximun number of prepared statements.
     *
     * @return maxPrepartedStatements value
     * @since 1.2.2
     */
    public int getMaxPreparedStatements()
    {
        return _maxPreparedStatements;
    }

    /**
     * Sets the maximum number of prepared statements.
     * @param maxPreparedStatements the new maximum number of prepared
     * statements
     *
     * @since 1.2.2
     */
    public void setMaxPreparedStatements(int maxPreparedStatements)
    {
        _maxPreparedStatements = maxPreparedStatements;
    }

    /* JDBC_4_1_ANT_KEY_BEGIN */
    // No @Override else it won't compile with Java 6
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
    /* JDBC_4_1_ANT_KEY_END */
}

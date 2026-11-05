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
package org.apache.tomcat.jdbc.pool;


import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Represents a pooled connection and holds a reference to the {@link java.sql.Connection} object
 */
public class PooledConnection implements PooledConnectionMBean {
    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(PooledConnection.class);

    /**
     * Attribute key for the username.
     */
    public static final String PROP_USER = PoolUtilities.PROP_USER;

    /**
     * Attribute key for the password.
     */
    public static final String PROP_PASSWORD = PoolUtilities.PROP_PASSWORD;

    /**
     * Validate when connection is borrowed flag
     */
    public static final int VALIDATE_BORROW = 1;
    /**
     * Validate when connection is returned flag
     */
    public static final int VALIDATE_RETURN = 2;
    /**
     * Validate when connection is idle flag
     */
    public static final int VALIDATE_IDLE = 3;
    /**
     * Validate when connection is initialized flag
     */
    public static final int VALIDATE_INIT = 4;
    /**
     * The properties for the connection pool
     */
    protected PoolConfiguration poolProperties;
    /**
     * The underlying database connection
     */
    private volatile java.sql.Connection connection;

    /**
     * If using a XAConnection underneath. On an XA session this is the pool's
     * {@link XABranchTracker} wrapper around the handle the data source returned.
     */
    protected volatile javax.sql.XAConnection xaConnection;
    /**
     * If using a ConnectionPoolDataSource underneath, this is the javax.sql.PooledConnection handle obtained from it.
     * Per the JDBC specification, closing the logical connection obtained from that handle does not close the physical
     * connection, so the handle itself must be kept in order to close the physical connection.
     */
    private volatile javax.sql.PooledConnection cpdsConnection;
    /**
     * When we track abandon traces, the borrow site of this connection, in one
     * of three shapes: a {@link StackTraceElement} array as captured at the
     * borrow and not yet formatted, the formatted trace (a String) that such an
     * array was turned into, or {@code null} for no site recorded. The formatting
     * is deferred to {@link #getStackTrace()} because a pool with logAbandoned
     * borrows constantly and reports rarely, so the cost is only paid when the
     * trace is actually read - and it is one slot rather than two fields, with
     * the formatting published by compare-and-set on the very array it read,
     * because the read-modify-write has no lock to sit in: the abandoned check
     * that reads it can be JMX-invoked, and con.lock() is a no-op unless the
     * sweeper or useLock is on. A formatting of an older borrow's frames then
     * loses the slot to any re-borrow that swapped its own site in meanwhile,
     * so a live session always carries its own borrow site rather than a
     * previous borrower's.
     */
    private final AtomicReference<Object> abandonTrace = new AtomicReference<>();
    /**
     * Timestamp the connection was last 'touched' by the pool. Seeded at construction - itself a pool action - so a
     * connection that registers its MBean inside a successful connect() never publishes the epoch-0 initial value
     * before the pool's first setTimestamp() lands.
     */
    private volatile long timestamp = System.currentTimeMillis();
    /**
     * Lock for this connection only
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);
    /**
     * Set to true if this connection has been discarded by the pool
     */
    private volatile boolean discarded = false;
    /**
     * The Timestamp when the last time the connect() method was called successfully
     */
    private volatile long lastConnected = -1;
    /**
     * Timestamp of the last completed successful validation, used to keep track of validation intervals. Seeded with
     * the time this pooled connection was created, and reset to 0 when a new physical session is established: a
     * validation that completed on the session just replaced says nothing about the new one.
     */
    private volatile long lastValidated = System.currentTimeMillis();
    /**
     * The parent
     */
    protected ConnectionPool parent;

    private HashMap<Object, Object> attributes = new HashMap<>();

    private volatile long connectionVersion = 0;

    private static final AtomicLong connectionIndex = new AtomicLong(0);

    /**
     * The JMX name this connection is registered under, or null while it is not registered. Written and cleared under
     * this connection's monitor (see {@link #createMBean()} and {@link #release()}), and read unlocked by
     * {@link #getObjectName()}, so the field is volatile.
     */
    private volatile ObjectName oname = null;

    /**
     * The first link of the interceptor chain built for this connection, cached so that the chain is not rebuilt on
     * every borrow. The cache holds the chain by ordinary strong references - a pooled connection keeps the interceptors
     * it was given alive with itself. The pool writes the chain at each borrow (see
     * {@link #setHandler(JdbcInterceptor)}) and clears it when the connection goes out of service.
     */
    private volatile JdbcInterceptor handler = null;

    private AtomicBoolean released = new AtomicBoolean(false);

    private volatile boolean suspect = false;

    private java.sql.Driver driver = null;

    /**
     * Constructor
     *
     * @param prop   - pool properties
     * @param parent - the parent connection pool
     */
    public PooledConnection(PoolConfiguration prop, ConnectionPool parent) {
        poolProperties = prop;
        this.parent = parent;
        connectionVersion = parent.getPoolVersion();
    }

    @Override
    public long getConnectionVersion() {
        return connectionVersion;
    }

    /**
     * @deprecated use {@link #shouldForceReconnect(String, String)} method kept since it was public, to avoid changing
     *                 interface.
     *
     * @param username The user name
     * @param password The password
     *
     * @return <code>true</code> if the pool does not need to reconnect
     */
    @Deprecated
    public boolean checkUser(String username, String password) {
        return !shouldForceReconnect(username, password);
    }

    /**
     * Returns true if we must force reconnect based on credentials passed in. Returns false if the
     * username/password has not changed since this connection was connected
     *
     * <p>This is a comparison only. It records nothing: the identity a session is established with is written by the
     * reconnect that follows this decision (see {@link #reconnectWith(String, String)}), so that a reconnect the
     * database refused cannot leave this connection carrying credentials that were never accepted.</p>
     *
     * <p>Where {@link PoolConfiguration#isAlternateUsernameAllowed()} is false the passed credentials carry no
     * weight - the pool ignores them at the borrow, as it does at every other surface - but the comparison is not
     * skipped: with alternate identities disallowed the identity every session must serve under is the pool's own
     * configured one, and a session carrying any other identity, however it came to carry it - the flag was turned
     * at runtime over a session a borrower had re-established under its own credentials - is re-established rather
     * than served.</p>
     *
     * @param username the username you wish to connect with, pass in null to accept the default username from
     *                     {@link PoolConfiguration#getUsername()}
     * @param password the password you wish to connect with, pass in null to accept the default username from
     *                     {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#getPassword()}
     *
     * @return true if the pool must reconnect
     */
    public boolean shouldForceReconnect(String username, String password) {

        if (!getPoolProperties().isAlternateUsernameAllowed()) {
            // Not the pool's answer to skip the comparison here: this is the
            // gate that keeps a session's authenticated identity matched to
            // the identity it is served under, and with alternates disallowed
            // that identity is always the pool's configured one. Comparing
            // against the configured credentials rather than against what the
            // borrower asked for is what keeps a session that carries another
            // identity from being served to a borrower that never claimed it.
            username = poolProperties.getUsername();
            password = poolProperties.getPassword();
        }

        if (username == null) {
            username = poolProperties.getUsername();
        }
        if (password == null) {
            password = poolProperties.getPassword();
        }

        String storedUsr = (String) getAttributes().get(PROP_USER);
        String storedPwd = (String) getAttributes().get(PROP_PASSWORD);

        boolean noChangeInCredentials = (username == null && storedUsr == null);
        noChangeInCredentials = (noChangeInCredentials || (username != null && username.equals(storedUsr)));

        noChangeInCredentials = noChangeInCredentials &&
                ((password == null && storedPwd == null) || (password != null && password.equals(storedPwd)));

        return !noChangeInCredentials;
    }

    /**
     * Connects the underlying connection to the database.
     *
     * @throws SQLException if the method {@link #release()} has been called.
     * @throws SQLException if driver instantiation fails
     * @throws SQLException if a call to {@link java.sql.Driver#connect(String, java.util.Properties)} fails.
     * @throws SQLException if default properties are configured and a call to
     *                          {@link java.sql.Connection#setAutoCommit(boolean)},
     *                          {@link java.sql.Connection#setCatalog(String)},
     *                          {@link java.sql.Connection#setTransactionIsolation(int)} or
     *                          {@link java.sql.Connection#setReadOnly(boolean)} fails.
     */
    public void connect() throws SQLException {
        if (released.get()) {
            throw new SQLException(MessageFactory.get("pooledConnection.cannotReestablish"));
        }
        if (connection != null) {
            try {
                this.disconnect(false);
            } catch (Throwable x) {
                // A non-fatal Error from the previous session's teardown is
                // a failed disconnect, not a reason to abort the new
                // connection attempt.
                ExceptionUtils.handleThrowable(x);
                if (log.isDebugEnabled()) {
                    log.debug("Unable to disconnect previous connection.", x);
                }
            }
        }
        if (poolProperties.getDataSource() != null) {
            connectUsingDataSource();
        } else {
            connectUsingDriver();
        }

        // Set up the default state, unless we expect the interceptor to do it
        if (!hasConnectionStateInterceptor()) {
            if (poolProperties.getDefaultTransactionIsolation() != DataSourceFactory.UNKNOWN_TRANSACTIONISOLATION) {
                connection.setTransactionIsolation(poolProperties.getDefaultTransactionIsolation());
            }
            if (poolProperties.getDefaultReadOnly() != null) {
                connection.setReadOnly(poolProperties.getDefaultReadOnly().booleanValue());
            }
            if (poolProperties.getDefaultAutoCommit() != null) {
                connection.setAutoCommit(poolProperties.getDefaultAutoCommit().booleanValue());
            }
            if (poolProperties.getDefaultCatalog() != null) {
                connection.setCatalog(poolProperties.getDefaultCatalog());
            }
        }
        this.discarded = false;
        this.lastConnected = System.currentTimeMillis();
        // The validation timestamp belongs to the session that was just given
        // up: a new physical session must be due for its own validation, or a
        // recently validated predecessor lets the configured testOnBorrow /
        // testOnReturn policy skip the check on this one.
        this.lastValidated = 0;

        // The physical session is up: this is the point at which the
        // connection can publish itself below the pool's MBean. Tying that to
        // the first borrow instead left a connection the pool pre-created for
        // its initial size, or one that is simply never borrowed, counted in
        // the pool's size yet invisible to JMX for its whole life. This also
        // covers every reconnect; a connection that is already registered
        // keeps its registration across a reconnect, and createMBean() leaves
        // such a call alone by its own guards. With JMX disabled the pool has
        // no JMX object to register under, and the borrow path still calls the
        // registration unconditionally, so it is skipped here rather than
        // called only to do nothing.
        if (parent.getJmxPool() != null) {
            createMBean();
        }
    }

    /**
     * Returns <code>true</code> if the configured interceptor chain contains the {@link ConnectionState} interceptor
     * or a subclass of it, which applies the configured default connection state itself every time the connection is
     * borrowed.
     *
     * <p>The interceptor is recognized by its resolved class rather than by its name in the configuration string:
     * the documented short name resolves to the same class as the fully qualified name, a subclass inherits the job
     * whatever it is named, and an interceptor merely named after the state interceptor does not do its job.</p>
     *
     * @return <code>true</code> if a {@link ConnectionState} interceptor is configured
     */
    private boolean hasConnectionStateInterceptor() {
        for (PoolProperties.InterceptorDefinition definition : poolProperties.getJdbcInterceptorsAsArray()) {
            try {
                if (ConnectionState.class.isAssignableFrom(definition.getInterceptorClass())) {
                    return true;
                }
            } catch (ClassNotFoundException e) {
                // An interceptor whose class cannot be loaded can never join the chain, so this definition cannot be
                // the state interceptor; fall through to the next one.
            }
        }
        return false;
    }

    /**
     * Connects using the configured DataSource.
     *
     * @throws SQLException if unable to obtain a connection from the DataSource
     */
    protected void connectUsingDataSource() throws SQLException {
        String usr = null;
        String pwd = null;
        if (getAttributes().containsKey(PROP_USER)) {
            usr = (String) getAttributes().get(PROP_USER);
        } else {
            usr = poolProperties.getUsername();
            getAttributes().put(PROP_USER, usr);
        }
        if (getAttributes().containsKey(PROP_PASSWORD)) {
            pwd = (String) getAttributes().get(PROP_PASSWORD);
        } else {
            pwd = poolProperties.getPassword();
            getAttributes().put(PROP_PASSWORD, pwd);
        }
        if (poolProperties.getDataSource() instanceof javax.sql.XADataSource) {
            javax.sql.XADataSource xds = (javax.sql.XADataSource) poolProperties.getDataSource();
            javax.sql.XAConnection pooled = (usr != null && pwd != null) ? xds.getXAConnection(usr, pwd)
                    : xds.getXAConnection();
            // Keep the handle before obtaining the logical connection: the
            // physical connection is only closed when the handle is closed,
            // so losing the handle here would leak the physical connection.
            xaConnection = pooled;
            Throwable failure = null;
            try {
                connection = pooled.getConnection();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                failure = t;
            }
            if (connection == null && failure == null) {
                // Contract-violating provider: a handle without a logical
                // connection is of no use either.
                failure = new SQLException(
                        MessageFactory.get("pooledConnection.xaDataSourceNullConnection", xds));
            }
            if (failure != null) {
                // Do not strand the handle if the logical connection
                // could not be obtained, whatever the provider failed with.
                try {
                    pooled.close();
                } catch (Throwable closeX) {
                    ExceptionUtils.handleThrowable(closeX);
                    if (closeX != failure) {
                        failure.addSuppressed(closeX);
                    }
                }
                xaConnection = null;
                connection = null;
                throw wrapConnectFailure(failure);
            }
            // Once the session is fully established, hand out the pool's
            // wrapper rather than the driver's handle: it tracks the
            // transaction branches started through this session, so a return
            // can tell whether its borrower is leaving a branch behind (see
            // hasUnresolvedXaBranches()). The connect above holds the driver's
            // handle directly, and the wrapper below only delegates - a close
            // through the field still physically closes the session.
            xaConnection = new XABranchTracker(pooled);
        } else if (poolProperties.getDataSource() instanceof javax.sql.DataSource) {
            javax.sql.DataSource ds = (javax.sql.DataSource) poolProperties.getDataSource();
            if (usr != null && pwd != null) {
                connection = ds.getConnection(usr, pwd);
            } else {
                connection = ds.getConnection();
            }
            if (connection == null) {
                // The DataSource contract allows getConnection to return null
                // when no connection can be provided; keeping that null would
                // surface later as a misleading validation failure, or as a
                // null pointer when the default connection state is applied.
                throw new SQLException(MessageFactory.get("pooledConnection.dataSourceNullConnection", ds));
            }
        } else if (poolProperties.getDataSource() instanceof javax.sql.ConnectionPoolDataSource) {
            javax.sql.ConnectionPoolDataSource ds = (javax.sql.ConnectionPoolDataSource) poolProperties.getDataSource();
            javax.sql.PooledConnection pooled = (usr != null && pwd != null) ? ds.getPooledConnection(usr, pwd)
                    : ds.getPooledConnection();
            // Keep the handle before obtaining the logical connection: the
            // physical connection is only closed when the handle is closed,
            // so losing the handle here would leak the physical connection.
            cpdsConnection = pooled;
            Throwable failure = null;
            try {
                connection = pooled.getConnection();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                failure = t;
            }
            if (connection == null && failure == null) {
                // Contract-violating provider: a handle without a logical
                // connection is of no use either.
                failure = new SQLException(
                        MessageFactory.get("pooledConnection.connectionPoolDataSourceNullConnection", ds));
            }
            if (failure != null) {
                // Do not strand the handle if the logical connection
                // could not be obtained, whatever the provider failed with.
                try {
                    pooled.close();
                } catch (Throwable closeX) {
                    ExceptionUtils.handleThrowable(closeX);
                    if (closeX != failure) {
                        failure.addSuppressed(closeX);
                    }
                }
                cpdsConnection = null;
                connection = null;
                throw wrapConnectFailure(failure);
            }
        } else {
            throw new SQLException(MessageFactory.get("pooledConnection.unknownDataSource",
                    (poolProperties.getDataSource() != null ? poolProperties.getDataSource().getClass() : "null")));
        }
    }

    /**
     * Connects using the configured JDBC Driver class.
     *
     * @throws SQLException if unable to instantiate the driver or obtain a connection
     */
    protected void connectUsingDriver() throws SQLException {

        try {
            if (driver == null) {
                if (poolProperties.getDriverClassName() == null) {
                    // Rely on DriverManager
                    if (log.isDebugEnabled()) {
                        log.debug("No driverClassName is configured so the driver will be located by DriverManager.");
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        // The dump of the configuration masks this URL (see
                        // PoolProperties#maskCredentials), and a DEBUG level
                        // is a standard support step, so a URL that carries
                        // its credential inline must reach this line masked
                        // as well - the driver is handed the real URL below.
                        log.debug("Instantiating driver using class: " + poolProperties.getDriverClassName() +
                                " [url=" + PoolProperties.maskCredentials(poolProperties.getUrl()) + "]");
                    }
                    driver = (java.sql.Driver) ClassLoaderUtil.loadClass(poolProperties.getDriverClassName(),
                            PooledConnection.class.getClassLoader(), Thread.currentThread().getContextClassLoader())
                            .getConstructor().newInstance();
                }
            }
        } catch (Exception cn) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to instantiate JDBC driver.", cn);
            }
            SQLException ex = new SQLException(cn.getMessage());
            ex.initCause(cn);
            throw ex;
        }
        String driverURL = poolProperties.getUrl();
        String usr = null;
        String pwd = null;
        if (getAttributes().containsKey(PROP_USER)) {
            usr = (String) getAttributes().get(PROP_USER);
        } else {
            usr = poolProperties.getUsername();
            getAttributes().put(PROP_USER, usr);
        }
        if (getAttributes().containsKey(PROP_PASSWORD)) {
            pwd = (String) getAttributes().get(PROP_PASSWORD);
        } else {
            pwd = poolProperties.getPassword();
            getAttributes().put(PROP_PASSWORD, pwd);
        }
        // A configuration whose database properties are null - a state
        // PoolConfiguration#setDbProperties accepts, and that the credential
        // setters and setConnectionProperties already tolerate - carries no
        // driver properties. That is not a configuration that cannot connect:
        // the URL and the credential below are enough for the driver, so the
        // absent collection becomes an empty one rather than a failed connect.
        // The copy is taken under the configuration's monitor, for the same
        // torn-snapshot reason PoolProperties#clone() synchronizes for:
        // setConnectionProperties and the credential setters mutate the live
        // collection in place under that monitor, while its reads of the
        // collection are not atomic against such mutation. A copy taken
        // outside the monitor can therefore mix two published generations:
        // the driver then sees a key a concurrent reconfigure has just
        // removed next to the keys that replaced it, or - when the credential
        // travels in the connection-properties string rather than in the
        // credential fields that are overlaid below - a user/password pair no
        // generation ever published together. The configuration monitor is a
        // leaf, so taking it here orders against no other lock.
        Properties properties;
        synchronized (poolProperties) {
            Properties dbProperties = poolProperties.getDbProperties();
            properties =
                    dbProperties == null ? new Properties() : PoolUtilities.clone(dbProperties);
        }
        if (usr != null) {
            properties.setProperty(PROP_USER, usr);
        }
        if (pwd != null) {
            properties.setProperty(PROP_PASSWORD, pwd);
        }

        try {
            if (driver == null) {
                connection = DriverManager.getConnection(driverURL, properties);
            } else {
                connection = driver.connect(driverURL, properties);
            }
        } catch (Exception x) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to connect to database.", x);
            }
            // Read the pool's JMX object only once: close() gives it up
            // concurrently, and a second read can observe the null the first
            // one did not
            org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = parent.getJmxPool();
            if (jmx != null) {
                jmx.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.NOTIFY_CONNECT,
                        ConnectionPool.getStackTrace(x));
            }
            if (x instanceof SQLException) {
                throw (SQLException) x;
            } else {
                SQLException ex = new SQLException(x.getMessage());
                ex.initCause(x);
                throw ex;
            }
        }
        if (connection == null) {
            // A driver declining the URL is the persistent-misconfiguration
            // case: this message leaves connect() as the borrow's own
            // SQLException and is logged as the pool's failure on every
            // attempt, at the default log level. It carries the pool's own
            // words, so it carries the URL masked - the way the dump and the
            // debug line above do; the driver's identity and the rest of the
            // URL stay readable.
            throw new SQLException(MessageFactory.get("pooledConnection.driverReturnedNull",
                    driver, PoolProperties.maskCredentials(driverURL)));
        }
    }

    /**
     * @return true if connect() was called successfully and disconnect has not yet been called
     */
    @Override
    public boolean isInitialized() {
        return connection != null;
    }

    /**
     * Returns true if the connection has been connected more than {@link PoolConfiguration#getMaxAge()} milliseconds.
     * false otherwise. When a max age is configured, a connection that currently has no physical session - see
     * {@link #getLastConnected()} - also returns true, as a session that is not connected is never young enough to
     * keep; without a configured max age this method always returns false.
     *
     * @return Returns true if the connection has been connected more than {@link PoolConfiguration#getMaxAge()}
     *             milliseconds. false otherwise.
     */
    @Override
    public boolean isMaxAgeExpired() {
        if (getPoolProperties().getMaxAge() > 0) {
            return (System.currentTimeMillis() - getLastConnected()) > getPoolProperties().getMaxAge();
        } else {
            return false;
        }
    }

    /**
     * Issues a call to {@link #disconnect(boolean)} with the argument false followed by a call to {@link #connect()}
     *
     * @throws SQLException if the call to {@link #connect()} fails.
     */
    public void reconnect() throws SQLException {
        this.disconnect(false);
        this.connect();
    }

    /**
     * Re-establishes this connection under the credentials handed in, giving up the session it currently holds only
     * once a session established under those credentials has been opened.
     *
     * <p>
     * {@link #reconnect()} closes the session in hand before it asks for another one, which is the right order for a
     * session the pool is retiring on its own account - because it reached {@link PoolConfiguration#getMaxAge() maxAge}
     * or failed a validation. It is the wrong order for a session that is being handed to a borrower under different
     * credentials: the database decides whether those credentials are acceptable, and a database that refuses them
     * would, with that order, have taken a healthy session out of the pool - a session every other borrower of the
     * pool can still use, and one the refused borrower never had a claim on. Here the order is reversed: the
     * credentials are put to the database while the session in hand stays in place, and only a session the database
     * has accepted replaces it.</p>
     *
     * <p>
     * The credentials themselves are written into {@link #getAttributes()} for the attempt, because that is where the
     * connect reads them from, and are put back as they were if the attempt fails. So neither a refusal nor a
     * connection that could not be established leaves this connection carrying credentials the database never
     * accepted - which matters because those attributes decide the identity of every later reconnect.</p>
     *
     * @param username the username to re-establish the connection with, pass in null to accept the default username
     *                     from {@link PoolConfiguration#getUsername()}
     * @param password the password to re-establish the connection with, pass in null to accept the default password
     *                     from {@link PoolConfiguration#getPassword()}
     *
     * @throws SQLException if the database refused the credentials or a session under them could not be established.
     *                         This connection then still holds the session, and carries the credentials, it held
     *                         before the call.
     */
    public void reconnectWith(String username, String password) throws SQLException {
        if (username == null) {
            username = poolProperties.getUsername();
        }
        if (password == null) {
            password = poolProperties.getPassword();
        }

        // The state a refused attempt has to hand back: the session this
        // connection is serving and the identity that session was opened
        // with.
        java.sql.Connection served = connection;
        javax.sql.XAConnection servedXa = xaConnection;
        javax.sql.PooledConnection servedCpds = cpdsConnection;
        long servedLastConnected = lastConnected;
        long servedLastValidated = lastValidated;
        HashMap<Object, Object> sessionAttributes = getAttributes();
        boolean hadUser = sessionAttributes.containsKey(PROP_USER);
        Object servedUser = sessionAttributes.get(PROP_USER);
        boolean hadPassword = sessionAttributes.containsKey(PROP_PASSWORD);
        Object servedPassword = sessionAttributes.get(PROP_PASSWORD);

        // Stage the credentials the attempt is made under. A null stands for
        // the pool's default, which the connect substitutes the same way it
        // does for a plain connect.
        if (username == null) {
            sessionAttributes.remove(PROP_USER);
        } else {
            sessionAttributes.put(PROP_USER, username);
        }
        if (password == null) {
            sessionAttributes.remove(PROP_PASSWORD);
        } else {
            sessionAttributes.put(PROP_PASSWORD, password);
        }

        // Set the session being replaced out of reach of the connect below,
        // which closes any session it finds before it opens the new one. This
        // one has to stay open until the attempt that replaces it is known to
        // have succeeded. The connected timestamp goes with it: while the
        // fields read null, the published LastConnected must read -1 like in
        // every other no-session state, as the per-connection MBean stays
        // registered across this window.
        connection = null;
        xaConnection = null;
        cpdsConnection = null;
        lastConnected = -1;
        try {
            connect();
        } catch (Throwable x) {
            // A connect can fail after it established the session - the
            // default state it applies to a new session can be refused, or a
            // driver wrapper can fail with a non-fatal LinkageError - so
            // whatever is in the fields is closed here rather than dropped.
            // The interceptor chain is told nothing: this session never
            // served anyone, and the state the chain holds belongs to the
            // session that is about to be back in service.
            ExceptionUtils.handleThrowable(x);
            try {
                closeSession(connection, xaConnection, cpdsConnection);
            } catch (Throwable closeFailure) {
                ExceptionUtils.handleThrowable(closeFailure);
                x.addSuppressed(closeFailure);
            } finally {
                // Restore even if the partial session could not be closed:
                // the served session is what the pool still owns, and a null
                // field would leave it open but unreachable.
                connection = served;
                xaConnection = servedXa;
                cpdsConnection = servedCpds;
                lastConnected = servedLastConnected;
                lastValidated = servedLastValidated;
                if (hadUser) {
                    sessionAttributes.put(PROP_USER, servedUser);
                } else {
                    sessionAttributes.remove(PROP_USER);
                }
                if (hadPassword) {
                    sessionAttributes.put(PROP_PASSWORD, servedPassword);
                } else {
                    sessionAttributes.remove(PROP_PASSWORD);
                }
            }
            if (x instanceof SQLException) {
                throw (SQLException) x;
            } else if (x instanceof RuntimeException) {
                throw (RuntimeException) x;
            } else if (x instanceof Error) {
                throw (Error) x;
            }
            SQLException ex = new SQLException(x.getMessage());
            ex.initCause(x);
            throw ex;
        }

        if (served == null) {
            // There was no session to replace: this is a plain connect of a
            // connection that had none, and the new session is already in
            // place.
            return;
        }

        // The database accepted the credentials, so the session they replace
        // can be given up now. The fields are turned back to it while it is
        // retired, so that the chain is told - as it is for every other
        // retirement - about the session it has been serving rather than
        // about the one that takes its place.
        java.sql.Connection accepted = connection;
        javax.sql.XAConnection acceptedXa = xaConnection;
        javax.sql.PooledConnection acceptedCpds = cpdsConnection;
        connection = served;
        xaConnection = servedXa;
        cpdsConnection = servedCpds;
        notifyDisconnected(false);
        closeSession(served, servedXa, servedCpds);
        connection = accepted;
        xaConnection = acceptedXa;
        cpdsConnection = acceptedCpds;
    }

    /**
     * Disconnects the connection. All exceptions are logged using debug level.
     *
     * @param finalize if set to true, a call to {@link ConnectionPool#finalize(PooledConnection)} is called.
     */
    private void disconnect(boolean finalize) {
        if (isDiscarded() && connection == null) {
            return;
        }
        setDiscarded(true);
        if (connection != null || xaConnection != null || cpdsConnection != null) {
            if (connection != null) {
                notifyDisconnected(finalize);
            }
            // Close any handle too: an XA/CPDS handle owns the physical
            // session even when obtaining its logical connection failed.
            closeSession(connection, xaConnection, cpdsConnection);
        }
        connection = null;
        xaConnection = null;
        cpdsConnection = null;
        lastConnected = -1;
        // The chain's teardown reset is delivered once, by the release that
        // clears the handler after it has freed the pool slot - see
        // ConnectionPool.release(). The disconnect event above is a separate
        // notification and does not replace it.
    }

    /**
     * Tells the interceptor chain that the connection is going out of service, so that each link can give up what it
     * holds for the session being retired.
     *
     * @param finalizing whether the retirement is the one that ends the pooled connection
     */
    private void notifyDisconnected(boolean finalizing) {
        try {
            parent.disconnectEvent(this, finalizing);
        } catch (Throwable x) {
            // The event itself isolates each link of the chain, so this
            // catch only guards against a failure of the walk itself;
            // an interceptor must not be able to keep the physical
            // connection from being closed
            log.warn(MessageFactory.get("pooledConnection.interceptorDisconnectedFailed"), x);
        }
    }

    /**
     * Turns a failure while acquiring a logical connection into the exception a connect reports, preserving
     * SQLException, RuntimeException and Error, and wrapping any other Throwable.
     *
     * @param failure the failure to rethrow
     *
     * @return a wrapper for a checked failure that is not an SQLException
     *
     * @throws SQLException the original SQLException
     * @throws RuntimeException the original RuntimeException
     * @throws Error the original Error
     */
    private static SQLException wrapConnectFailure(Throwable failure) throws SQLException {
        if (failure instanceof SQLException) {
            throw (SQLException) failure;
        } else if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        } else if (failure instanceof Error) {
            throw (Error) failure;
        }
        SQLException ex = new SQLException(failure.getMessage());
        ex.initCause(failure);
        return ex;
    }

    /**
     * Closes a physical session and the handle it came in on, if it came in on one. The session is passed in rather
     * than read from the fields, because a caller that has already taken on the session replacing this one - see
     * {@link #reconnectWith(String, String)} - still has to close the session it gave up.
     *
     * @param session    the session to close, ignored when null
     * @param xaHandle   the XA handle the session was obtained through, or null
     * @param cpdsHandle the {@link javax.sql.PooledConnection} handle the session was obtained through, or null
     */
    private void closeSession(java.sql.Connection session, javax.sql.XAConnection xaHandle,
            javax.sql.PooledConnection cpdsHandle) {
        if (cpdsHandle == null && xaHandle == null && session == null) {
            return;
        }
        if (xaHandle instanceof XABranchTracker) {
            // Mark the handle retired before the physical close: branch
            // work on a resource captured from this handle then meets the
            // pool's clean XA refusal instead of the driver's half-closed
            // state.
            ((XABranchTracker) xaHandle).markClosed();
        }
        try {
            if (cpdsHandle != null) {
                // Closing the handle closes the physical connection
                // and invalidates the logical connection; closing the
                // logical connection alone would not.
                cpdsHandle.close();
            } else if (xaHandle != null) {
                xaHandle.close();
            } else {
                session.close();
            }
        } catch (Throwable ignore) {
            // A non-fatal Error from the driver's close is a failed close,
            // not a reason to let the pool lose the slot or leave the chain
            // installed: the caller has already settled the release.
            ExceptionUtils.handleThrowable(ignore);
            if (log.isDebugEnabled()) {
                log.debug("Unable to close underlying SQL connection", ignore);
            }
        }
    }


    /**
     * Returns abandon timeout in milliseconds
     *
     * @return abandon timeout in milliseconds
     */
    public long getAbandonTimeout() {
        if (poolProperties.getRemoveAbandonedTimeout() <= 0) {
            return Long.MAX_VALUE;
        } else {
            return poolProperties.getRemoveAbandonedTimeout() * 1000L;
        }
    }

    /**
     * Returns <code>true</code> if the connection pool is configured to do validation for a certain action.
     *
     * @param action The validation action
     */
    private boolean doValidate(int action) {
        if (action == VALIDATE_BORROW && poolProperties.isTestOnBorrow()) {
            return true;
        } else if (action == VALIDATE_RETURN && poolProperties.isTestOnReturn()) {
            return true;
        } else if (action == VALIDATE_IDLE && poolProperties.isTestWhileIdle()) {
            return true;
        } else if (action == VALIDATE_INIT && poolProperties.isTestOnConnect()) {
            return true;
        } else if (action == VALIDATE_INIT &&
                PoolProperties.configuredStatement(poolProperties.getInitSQL()) != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns <code>true</code> if the object is still valid. A false answer is the caller's cue to stop trusting this
     * session: a newly established connection that fails its check is released rather than pooled, a borrow whose check
     * fails is re-established before it is served, and an idle sweep that reports failure takes the connection out of
     * service.
     *
     * @param validateAction The value
     *
     * @return <code>true</code> if the connection is valid
     */
    public boolean validate(int validateAction) {
        return validate(validateAction, null);
    }

    /**
     * Validates a connection.
     *
     * @param validateAction the action used. One of {@link #VALIDATE_BORROW}, {@link #VALIDATE_IDLE},
     *                           {@link #VALIDATE_INIT} or {@link #VALIDATE_RETURN}
     * @param sql            the SQL to be used during validation. If the {@link PoolConfiguration#setInitSQL(String)}
     *                           has been called with a statement and the action is {@link #VALIDATE_INIT} the init SQL
     *                           will be used for validation, and a blank argument, like a blank configured statement,
     *                           reads as no statement. Whether anything runs at all is the action's own configuration
     *                           that decides: for an action the pool has not enabled, this method returns true without
     *                           executing the statement or touching the connection.
     *
     * @return true if the connection was validated successfully. It returns true even if validation was not performed,
     *             such as when {@link PoolConfiguration#setValidationInterval(long)} has been called with a positive
     *             value.
     *             <p>
     *             false if the validation failed. The caller should close the connection if false is returned since a
     *             session could have been left in an unknown state during initialization. A connection that was never
     *             connected, or has already been disconnected, is always reported as invalid.
     *             <p>
     *             A validation that runs its built-in check against the database also ends any transaction that is open
     *             on the session when the check completes - a pool configured with a custom {@link Validator} is not,
     *             as the validator replaces the check and owns the session state it observes. The transaction ended is
     *             the one the check itself may have started, or one a previous borrower left open. A read-only check is
     *             ended with a rollback, never with a commit: a pool health check must not commit application work the
     *             application itself never committed. The one exception is the {@link #VALIDATE_INIT} execution of the
     *             configured {@link PoolConfiguration#getInitSQL() init SQL}: that query is not a check but the pool's
     *             own initialization of the session it has just established, so its transaction is ended with a commit
     *             and the initialization persists for the connection's lifetime. When the transaction cannot be ended -
     *             by the commit or by the rollback - the session state is unknown, so the validation is reported as
     *             failed and the pool takes the connection out of service.
     */
    public boolean validate(int validateAction, String sql) {
        if (connection == null) {
            // Never connected - a wake-up placeholder that no borrower took
            // - or already disconnected; there is nothing to validate and it
            // cannot be handed out
            return false;
        }
        if (this.isDiscarded()) {
            return false;
        }

        if (!doValidate(validateAction)) {
            // No validation required, no init sql and props not set
            return true;
        }

        // Don't bother validating if already have recently enough
        long now = System.currentTimeMillis();
        if (validateAction != VALIDATE_INIT && poolProperties.getValidationInterval() > 0 &&
                (now - this.lastValidated) < poolProperties.getValidationInterval()) {
            return true;
        }

        // One read of a runtime-reconfigurable field. The validator is
        // volatile and its setter is a supported live surface (JMX, the
        // DataSource), so a second read could name a different validator
        // than the one being called: the call would NPE on a null
        // replacement and the guard below would read it as this session
        // failing its own check, retiring a healthy connection that no
        // validator ever refused; a swap to a different validator would
        // run that one mid-borrow and name it in a log line about work
        // the first one did. The snapshot decides, is called, and signs
        // the log lines alike.
        final Validator validator = poolProperties.getValidator();
        if (validator != null) {
            boolean valid;
            try {
                valid = validator.validate(connection, validateAction);
            } catch (Throwable x) {
                ExceptionUtils.handleThrowable(x);
                // The SPI declares no checked exception, so throwing is the
                // only way an implementation can report its own failure; the
                // pool must treat that as a failed validation, because a
                // throw escaping from here unwinds the borrow or return path
                // between the idle and busy queues and strands the pooled
                // connection - permanently shrinking the pool. Errors belong
                // in this guard too: an implementation loaded through a web
                // application class loader fails with LinkageError variants -
                // a NoClassDefFoundError once the context has stopped is the
                // common one - and that is a check that cannot vouch for the
                // session, nothing else. Only the fatal conditions that
                // handleThrowable re-throws keep propagating.
                if (getPoolProperties().getLogValidationErrors()) {
                    log.error(MessageFactory.get("pooledConnection.customValidationThrew",
                            validator), x);
                } else if (log.isDebugEnabled()) {
                    log.debug("Custom validation threw an exception.", x);
                }
                return false;
            }
            if (valid) {
                this.lastValidated = System.currentTimeMillis();
                return true;
            } else {
                if (getPoolProperties().getLogValidationErrors()) {
                    log.error(MessageFactory.get("pooledConnection.customValidationFailed",
                            validator));
                }
                return false;
            }
        }

        String query = PoolProperties.configuredStatement(sql);

        // The init SQL is not a health check: it is the pool deliberately
        // initializing the physical session it has just established, and its
        // documented purpose is to leave effects that persist for the
        // connection's lifetime. The pool only ever runs the init action on a
        // session that connect() established moments ago, so the transaction
        // this execution opens is the pool's own initialization work, and
        // nothing a borrower could have left behind on it.
        String initSql = PoolProperties.configuredStatement(poolProperties.getInitSQL());
        boolean executingInitSql = validateAction == VALIDATE_INIT && initSql != null;

        if (executingInitSql) {
            query = initSql;
        }

        if (query == null) {
            query = PoolProperties.configuredStatement(poolProperties.getValidationQuery());
        }

        if (query == null) {
            boolean transactionTerminated = false;
            int validationQueryTimeout = poolProperties.getValidationQueryTimeout();
            if (validationQueryTimeout < 0) {
                validationQueryTimeout = 0;
            }
            try {
                if (connection.isValid(validationQueryTimeout)) {
                    this.lastValidated = System.currentTimeMillis();
                    // Terminate - by rollback, never by commit - any
                    // transaction the isValid() call opened, or that a
                    // previous borrower left open: a read-only health check
                    // must not commit application work.
                    transactionTerminated = silentlyRollbackTransactionIfNeeded();
                    return transactionTerminated;
                } else {
                    if (getPoolProperties().getLogValidationErrors()) {
                        log.error(MessageFactory.get("pooledConnection.isValidReturnedFalse"));
                    }
                    return false;
                }
            } catch (SQLException | RuntimeException e) {
                // A driver or connection wrapper can also fail isValid()
                // with an unchecked exception, which likewise means the
                // session cannot be vouched for; like the checked failure
                // it is reported as a failed validation, because a throw
                // escaping from here unwinds the borrow path between the
                // idle and busy queues and strands the pooled connection -
                // permanently shrinking the pool
                if (getPoolProperties().getLogValidationErrors()) {
                    log.error(MessageFactory.get("pooledConnection.isValidFailed"), e);
                } else if (e instanceof RuntimeException) {
                    // Unlike the checked failure this anomaly would once
                    // have escaped as the driver's own throw; the caller
                    // is handed only a generic validation SQLException that
                    // says nothing about it, so it is logged at warn rather
                    // than debug - otherwise at the default log level a
                    // broken driver would fail every validation with no
                    // trace anywhere
                    log.warn(MessageFactory.get("pooledConnection.isValidFailed"), e);
                } else if (log.isDebugEnabled()) {
                    log.debug("isValid() failed.", e);
                }
                return false;
            } catch (Throwable x) {
                // Same reasoning one step further: a driver or wrapper that
                // fails the check with an Error - a LinkageError surfacing
                // through a wrapper whose class loader has lost a class is
                // the realistic arrival - has answered the question the
                // check asks, "can this session be vouched for", with
                // nothing better than an exception would; handleThrowable
                // keeps the fatal conditions it keeps everywhere else.
                ExceptionUtils.handleThrowable(x);
                if (getPoolProperties().getLogValidationErrors()) {
                    log.error(MessageFactory.get("pooledConnection.isValidFailed"), x);
                } else {
                    // Logged like the unchecked case above: this is the
                    // same anomaly one type further out
                    log.warn(MessageFactory.get("pooledConnection.isValidFailed"), x);
                }
                return false;
            } finally {
                if (!transactionTerminated) {
                    silentlyRollbackTransactionIfNeeded();
                }
            }
        }

        boolean transactionTerminated = false;
        Statement stmt = null;
        try {
            stmt = connection.createStatement();

            int validationQueryTimeout = poolProperties.getValidationQueryTimeout();
            if (validationQueryTimeout > 0) {
                stmt.setQueryTimeout(validationQueryTimeout);
            }

            stmt.execute(query);
            stmt.close();
            this.lastValidated = System.currentTimeMillis();
            if (executingInitSql) {
                // Terminate the transaction the init SQL executed into by
                // committing it: this is the pool's own initialization of a
                // freshly established session, and its effects are meant to
                // persist (Bugzilla 56586). A commit the driver refuses
                // leaves the session in an unknown state, so - like a failed
                // rollback - it fails the validation and the pool takes the
                // connection out of service, with the finally below rolling
                // the session back to a clean state before it goes.
                transactionTerminated = silentlyCommitTransactionIfNeeded();
            } else {
                // Terminate - by rollback, never by commit - any transaction
                // the validation query opened, or that a previous borrower
                // left open: a read-only health check must not commit
                // application work. A session whose transaction cannot be
                // terminated is in an unknown state, so it fails the
                // validation and the pool takes the connection out of
                // service.
                transactionTerminated = silentlyRollbackTransactionIfNeeded();
            }
            return transactionTerminated;
        } catch (Throwable ex) {
            // The checked and unchecked failures of the driver calls above
            // are failed validations, and so is an Error short of the fatal
            // conditions handleThrowable keeps: an escaping throw unwinds
            // the borrow path between the idle and busy queues and strands
            // the pooled connection, whatever the throw's type.
            ExceptionUtils.handleThrowable(ex);
            if (getPoolProperties().getLogValidationErrors()) {
                log.error(MessageFactory.get("pooledConnection.sqlValidationError"), ex);
            } else if (log.isDebugEnabled()) {
                log.debug("Unable to validate object:", ex);
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (Throwable ignore2) {
                    ExceptionUtils.handleThrowable(ignore2);
                }
            }

        } finally {
            if (!transactionTerminated) {
                silentlyRollbackTransactionIfNeeded();
            }
        }
        return false;
    }


    /**
     * Terminates a transaction open on the session by committing it, for the
     * one case where the pending transaction is the pool's own deliberate
     * initialization work: the init SQL the init-time validation has just
     * executed on a freshly established session. A failure to commit -
     * checked or unchecked - means the session cannot be vouched for, so it
     * is reported as false and the caller treats the validation as failed;
     * an unchecked failure must not escape through the caller's finally
     * block, outside the catch that converts it, and strand the pooled
     * connection between the idle and busy queues.
     */
    private boolean silentlyCommitTransactionIfNeeded() {
        try {
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
            return true;
        } catch (SQLException e) {
            log.debug("Failed to commit transaction", e);
        } catch (Throwable e) {
            // A non-fatal Error is the same failed commit an unchecked
            // exception is: it must not escape through the caller's finally
            // block and strand the connection.
            ExceptionUtils.handleThrowable(e);
            if (getPoolProperties().getLogValidationErrors()) {
                log.error(MessageFactory.get("pooledConnection.commitFailed"), e);
            } else {
                // The unchecked variant is logged at warn rather than debug,
                // like the rollback helper's: the caller surfaces it only as
                // the generic validation failure, so at the default log level
                // a driver that fails to commit would otherwise destroy
                // connection after connection with no trace anywhere
                log.warn(MessageFactory.get("pooledConnection.commitFailed"), e);
            }
        }
        return false;
    }


    private boolean silentlyRollbackTransactionIfNeeded() {
        try {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            return true;
        } catch (SQLException e) {
            log.debug("Failed to rollback transaction", e);
        } catch (Throwable e) {
            // A non-fatal Error is handled exactly like the unchecked
            // failure below.
            ExceptionUtils.handleThrowable(e);
            // A failure to end the transaction is a failure whether the
            // driver reports it checked or unchecked: the caller reads the
            // false as a failed validation and takes the session out of
            // service, while a throw escaping from here would escape even
            // through a caller's finally block, outside the catch that
            // converts it, and strand the pooled connection between the
            // idle and busy queues
            if (getPoolProperties().getLogValidationErrors()) {
                log.error(MessageFactory.get("pooledConnection.rollbackFailed"), e);
            } else {
                // The unchecked variant is an anomaly of the same kind as
                // a throwing custom Validator, and unlike the checked
                // rollback failure the pool surfaces it only as the same
                // generic validation failure as any other, so it is logged
                // at warn rather than debug: otherwise at the default log
                // level a driver that fails to end a transaction unchecked
                // would destroy connection after connection with no trace
                // anywhere
                log.warn(MessageFactory.get("pooledConnection.rollbackFailed"), e);
            }
        }
        return false;
    }


    /**
     * The time limit for how long the object can remain unused before it is released
     *
     * @return {@link PoolConfiguration#getMinEvictableIdleTimeMillis()}
     */
    public long getReleaseTime() {
        return this.poolProperties.getMinEvictableIdleTimeMillis();
    }

    /**
     * This method is called if (Now - timeCheckedIn &gt; getReleaseTime()). It disconnects the connection - a failure of
     * the disconnect itself is logged at debug level - and then sets the {@link #released} flag to true, so any attempt
     * to connect this cached object again fails per {@link #connect()}. Only the call that settles the flag drives the
     * disconnection; later calls return without touching the session. The connection pool uses the atomic return value
     * to decrement the pool size counter.
     *
     * @return true if this is the first time this method has been called. false if this method has been called before.
     */
    public boolean release() {
        // The claim precedes the acting. release() reaches a connection from
        // several hands at once - the abandon check and a losing return both
        // call it on the same connection - and the terminal sequence it
        // drives (the chain's disconnected(finalizing=true), the physical
        // close, the chain teardown at parent.finalize) is not a sequence a
        // session can be taken through twice: an interceptor would meet its
        // own disposal, then the same close again. So the winner is settled
        // first, under this connection's monitor, and only that winner acts.
        //
        // Under this connection's monitor - the one lock every registration
        // holds from its released-check through its name assignment (see
        // createMBean()), and the only lock that every configuration shares,
        // the per-connection lock being a deliberate no-op unless the sweeper
        // or useLock is on. Without this pairing a close could read the name
        // while a registration still runs: it would find null, unregister
        // nothing, and the registration would then publish an MBean of this
        // now-dead connection that nothing is left ever to take back. The
        // flag that tells a registration this connection is gone is settled
        // inside the same step: set only after leaving the monitor, it would
        // leave a registration that wins the monitor in between seeing
        // neither the name nor the flag, and publishing the orphan anyway.
        // With both settled here, either the registration completes first and
        // this unregisters it, or this passes first and the registration
        // sees the released flag and publishes nothing. Settling the flag
        // before the disconnect rather than after it is what stops a racing
        // connect() from passing that check on a session already being torn
        // down, and the MBean going before the session is what stops a JMX
        // reader from watching the teardown as a live connection.
        boolean first;
        synchronized (this) {
            if (oname != null) {
                JmxUtil.unregisterJmx(oname);
                oname = null;
            }
            first = released.compareAndSet(false, true);
        }
        if (first) {
            try {
                disconnect(true);
            } catch (Throwable x) {
                // A non-fatal Error from the driver's close is a failed
                // disconnect, not a reason to skip the first-CAS result the
                // pool uses to retire the slot and count the release.
                ExceptionUtils.handleThrowable(x);
                if (log.isDebugEnabled()) {
                    log.debug("Unable to close SQL connection", x);
                }
            }
        }
        return first;
    }

    /**
     * Replaces the abandoned-connection trace. The pool itself only ever clears it - the return path hands the session
     * back with a null trace so no report can read the finished borrow's site - and a non-null value is the externally
     * supplied form of the trace, kept public for that use. Either way the value lands in the same slot the borrow-site
     * frames live in (see {@link #abandonTrace}), so a hand-written trace is replaced by the next borrow's site just
     * like a recorded one is.
     *
     * @param trace the stack trace for this connection, or null to clear it
     */
    public void setStackTrace(String trace) {
        abandonTrace.set(trace);
    }

    /**
     * Records the borrow-site frames for this connection. They are formatted into a trace only if an
     * abandoned-connection report reads them back (see {@link #getStackTrace()}), and each borrow simply swaps its own
     * frames into the slot, replacing whatever an earlier borrow's site had become.
     *
     * @param trace the borrow-site frames for this connection
     */
    void setStackTraceElements(StackTraceElement[] trace) {
        abandonTrace.set(trace);
    }

    /**
     * Returns the stack trace from when this connection was borrowed. Can return null if no stack trace was set.
     *
     * @return the stack trace or null of no trace was set
     */
    public String getStackTrace() {
        Object site = abandonTrace.get();
        if (site instanceof String) {
            // Already formatted and cached by this or an earlier read.
            return (String) site;
        }
        StackTraceElement[] elements = (StackTraceElement[]) site;
        if (elements == null) {
            return null;
        }
        Exception x = new Exception();
        x.setStackTrace(elements);
        String trace = ConnectionPool.getStackTrace(x);
        // Publish through compare-and-set on the very frames that were formatted:
        // a re-borrow that swapped its own site in during the formatting wins the
        // slot, and this reader's text - which it still returns - was for the
        // borrow that has since been replaced. (The abandonTrace field explains
        // why the swap has to be atomic; caching the text is what makes the
        // abandon path's two readings pay for a single formatting.)
        abandonTrace.compareAndSet(elements, trace);
        return trace;
    }

    /**
     * Sets a timestamp on this connection. A timestamp usually means that some operation performed successfully.
     *
     * @param timestamp the timestamp as defined by {@link System#currentTimeMillis()}
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        setSuspect(false);
    }

    @Override
    public boolean isSuspect() {
        return suspect;
    }

    /**
     * Sets the suspect flag for this connection.
     *
     * @param suspect true if the connection is suspected to be invalid
     */
    public void setSuspect(boolean suspect) {
        this.suspect = suspect;
    }

    /**
     * An interceptor can call this method with the value true, and the connection will be closed when it is returned to
     * the pool. An {@link java.sql.Connection#abort(java.util.concurrent.Executor) abort()} on a handle to this
     * connection completes the discard immediately rather than waiting for the return.
     *
     * @param discarded - only valid value is true
     *
     * @throws IllegalStateException if this method is called with the value false and the value true has already been
     *                                   set.
     */
    public void setDiscarded(boolean discarded) {
        if (this.discarded && !discarded) {
            throw new IllegalStateException(MessageFactory.get("pooledConnection.discardedStateChange"));
        }
        this.discarded = discarded;
    }

    /**
     * Set the timestamp the connection was last validated. This flag is used to keep track when we are using a
     * {@link PoolConfiguration#setValidationInterval(long) validation-interval}.
     *
     * <p>The pool stamps this itself as each of its own validations completes; writing it by hand suppresses the checks
     * the interval would otherwise run for up to that interval, which makes the setter a surface for tests and for an
     * embedder driving that bookkeeping directly rather than a way to operate a running pool.</p>
     *
     * @param lastValidated a timestamp as defined by {@link System#currentTimeMillis()}
     */
    public void setLastValidated(long lastValidated) {
        this.lastValidated = lastValidated;
    }

    /**
     * Sets the pool configuration for this connection and connection pool. Object is shared with the
     * {@link ConnectionPool}
     *
     * <p>Every connection of a pool reads the same configuration object, and the pool builds each connection against
     * the one it was created with; replacing the reference here therefore changes what this single connection sees
     * while the rest of the pool keeps reading the old configuration. That makes the setter a surface for tests and
     * for an embedder that owns the wiring, not a way to reconfigure a running pool - which is what
     * {@link DataSourceProxy#setPoolProperties(PoolConfiguration)} is, since it rebuilds the pool on the
     * configuration it is given.</p>
     *
     * @param poolProperties The pool properties
     */
    public void setPoolProperties(PoolConfiguration poolProperties) {
        this.poolProperties = poolProperties;
    }

    /**
     * Return the timestamps of last pool action. Timestamps are typically set when connections are borrowed from the
     * pool. It is used to keep track of {@link PoolConfiguration#setRemoveAbandonedTimeout(int) abandon-timeouts}. This
     * timestamp can also be reset by the
     * {@link org.apache.tomcat.jdbc.pool.interceptor.ResetAbandonedTimer#invoke(Object, java.lang.reflect.Method, Object[])}
     *
     * @return the timestamp of the last pool action as defined by {@link System#currentTimeMillis()}
     */
    @Override
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the discarded flag.
     *
     * @return the discarded flag. If the value is true, either {@link #disconnect(boolean)} has been called or it will
     *             be called when the connection is returned to the pool.
     */
    @Override
    public boolean isDiscarded() {
        return discarded;
    }

    /**
     * Returns the timestamp at which the last successful validation completed on the current physical session. While
     * the session has not been validated it reports 0, so a validation inherited from the session a reconnect replaced
     * cannot be mistaken for one of this session. Before any session has been established, it reports the time this
     * pooled connection was created.
     *
     * @return the timestamp at which the last successful validation completed as defined by
     *             {@link System#currentTimeMillis()}, or 0 while the current session has not been validated
     */
    @Override
    public long getLastValidated() {
        return lastValidated;
    }

    /**
     * Returns the configuration for this connection and pool
     *
     * @return the configuration for this connection and pool
     */
    public PoolConfiguration getPoolProperties() {
        return poolProperties;
    }

    /**
     * Locks the connection only if either {@link PoolConfiguration#isPoolSweeperEnabled()} or
     * {@link PoolConfiguration#getUseLock()} return true. The per connection lock ensures thread safety if multiple
     * threads are performing operations on the connection. Otherwise this is a noop for performance
     */
    public void lock() {
        if (poolProperties.getUseLock() || this.poolProperties.isPoolSweeperEnabled()) {
            // Optimized, only use a lock when there is concurrency
            lock.writeLock().lock();
        }
    }

    /**
     * Unlocks the connection if, and only if, this thread currently holds the lock. The release decision is based on
     * the lock state rather than on the configuration that {@link #lock()} consulted, because that configuration is
     * mutable (it can be changed at runtime through JMX) and may differ between the two calls: deciding by
     * configuration would either leave the lock write-locked forever (enabled at lock() time, disabled at unlock()
     * time) or throw IllegalMonitorStateException (disabled at lock() time, enabled at unlock() time).
     */
    public void unlock() {
        if (lock.isWriteLockedByCurrentThread()) {
            // Release only what this thread actually acquired
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the underlying connection: the session the pool established for this entry, not a logical handle created
     * on demand. On a CPDS-configured pool the {@link javax.sql.PooledConnection} handle that creates such logical
     * handles is the separate <code>cpdsConnection</code> this class keeps, just as the XA handle is
     * {@link #getXAConnection()}.
     *
     * @return the underlying JDBC connection as it was returned from the JDBC driver
     */
    public java.sql.Connection getConnection() {
        return this.connection;
    }

    /**
     * Returns the underlying XA connection
     *
     * @return the XA connection of this session on an XA pool - the pool's
     *             {@link XABranchTracker} wrapper around the handle that the data
     *             source returned, which delegates every call to it - or null when
     *             the pool is not backed by an XA data source or this session has
     *             been disconnected
     */
    public javax.sql.XAConnection getXAConnection() {
        return this.xaConnection;
    }

    /**
     * Reports whether this session carries an XA transaction branch that was
     * started through its XA resource and never resolved. Such a branch is
     * attached to the physical connection until the database drops it - the
     * pool's return-time transaction termination unwinds only local work - so
     * the session must not go back to the idle queue with one on it.
     *
     * @return true if at least one branch started on this session was never
     *             committed, rolled back or forgotten
     */
    boolean hasUnresolvedXaBranches() {
        javax.sql.XAConnection xa = xaConnection;
        if (xa instanceof XABranchTracker) {
            return ((XABranchTracker) xa).hasUnresolvedBranches();
        }
        return false;
    }


    /**
     * Returns the timestamp of when the connection was last connected to the database, ie, a successful call to
     * {@link java.sql.Driver#connect(String, java.util.Properties)}.
     *
     * @return the timestamp of the last successful connection as defined by {@link System#currentTimeMillis()}, or -1
     *             when this pooled connection currently has no physical session - never connected, disconnected, or
     *             mid-reconnect
     */
    @Override
    public long getLastConnected() {
        return lastConnected;
    }

    /**
     * Returns the first handler in the interceptor chain
     *
     * @return the first interceptor for this connection
     */
    public JdbcInterceptor getHandler() {
        return handler;
    }

    /**
     * Drops the cached interceptor chain after the release has delivered its teardown reset, without walking it a
     * second time.
     */
    void clearHandler() {
        this.handler = null;
    }

    /**
     * Sets the interceptor chain handler for this connection.
     *
     * @param handler the first interceptor in the chain
     */
    public void setHandler(JdbcInterceptor handler) {
        try {
            if (this.handler != null && this.handler != handler) {
                JdbcInterceptor interceptor = this.handler;
                while (interceptor != null) {
                    // Read the next link before the callback, as the callback
                    // is allowed to modify it
                    JdbcInterceptor next = interceptor.getNext();
                    try {
                        interceptor.reset(null, null);
                    } catch (Throwable t) {
                        // One broken interceptor must not stop the rest of the
                        // chain from being reset
                        log.warn(MessageFactory.get("pooledConnection.interceptorResetFailed",
                                interceptor.getClass().getName()), t);
                    }
                    interceptor = next;
                }
            }
        } finally {
            // A released connection must never keep a stale chain,
            // even if resetting the old one failed
            this.handler = handler;
        }
    }

    /**
     * Returns a diagnostic rendering of this pooled connection that is safe to interpolate into a pool log message:
     * the connection's identity and whether it currently holds a physical session, never the driver's own
     * {@link java.sql.Connection}'s own {@code toString()}. That text is unmasked and may carry the URL, the user or other session details,
     * and it may contain newlines, so passing it into a pool warning could expose a credential or forge log lines. A
     * caller that wants the driver's text for an explicit dump can read the physical connection itself.
     *
     * @return the credential-safe diagnostic rendering
     */
    @Override
    public String toString() {
        return "PooledConnection@" + Integer.toHexString(System.identityHashCode(this))
                + (connection != null ? "[connected]" : "[null]");
    }

    /**
     * Returns true if this connection has been released and wont be reused.
     *
     * @return true if the method {@link #release()} has been called
     */
    @Override
    public boolean isReleased() {
        return released.get();
    }

    /**
     * Returns the attributes map for this connection.
     *
     * @return the attributes map
     */
    public HashMap<Object, Object> getAttributes() {
        return attributes;
    }

    /**
     * Creates and registers an MBean for this pooled connection with JMX. The pool's own MBean is the registration
     * base; once close() has given it up - which a borrow can race - there is no base to register under, and the
     * registration is skipped. It is skipped as well for a connection that is already registered - the pool reaches a
     * live connection from the connect path and from the sweep it runs when its own MBean is published - and for a
     * released one, which would leave a registration of a dead connection behind with nothing left to unregister it.
     * The check and the publish are one synchronized step because those paths can meet on the same connection and must
     * never publish it twice: only one of the registrations would ever be unregistered. The
     * release of a connection settles its unregistration and its released flag under this same monitor (see
     * {@link #release()}), so whichever of the two passes first settles the outcome: a registration after a release
     * sees the released flag and publishes nothing, and a release after a registration finds the name and takes it
     * back.
     */
    public synchronized void createMBean() {
        if (oname != null || released.get()) {
            return;
        }
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = parent.getJmxPool();
        if (jmx == null) {
            return;
        }
        String keyprop = ",connections=PooledConnection[" + connectionIndex.getAndIncrement() + "]";
        oname = JmxUtil.registerJmx(jmx.getObjectName(), keyprop, this);
    }

    /**
     * Releases the MBeans published for this connection and for the interceptors of its chain, without releasing the
     * physical session. The pool uses this when its own MBean is released while the pool stays live: the connection
     * and its chain remain usable, and a later borrow or reconnect can publish them again.
     */
    public void unregisterMBean() {
        synchronized (this) {
            if (oname != null) {
                JmxUtil.unregisterJmx(oname);
                oname = null;
            }
        }
        for (JdbcInterceptor interceptor = getHandler(); interceptor != null; interceptor = interceptor.getNext()) {
            try {
                interceptor.unregisterMBean();
            } catch (Throwable x) {
                // JMX is best effort: one interceptor that fails to release
                // its MBean must not keep the others registered
                ExceptionUtils.handleThrowable(x);
                if (log.isDebugEnabled()) {
                    log.debug("Unable to unregister interceptor MBean", x);
                }
            }
        }
    }

    /**
     * Returns the JMX ObjectName for this pooled connection.
     *
     * @return the JMX ObjectName or null if not registered
     */
    public ObjectName getObjectName() {
        return oname;
    }

    /**
     * Returns the physical connection behind this pooled connection. The connection field is transiently null while a
     * reconnect is in flight (and after a failed one), during which the per-connection MBean stays registered, so the
     * JMX-exposed delegation methods must report that state rather than fail with a {@link NullPointerException}.
     *
     * @return the live physical connection
     *
     * @throws SQLException if this pooled connection currently has no physical connection
     */
    private java.sql.Connection requireConnection() throws SQLException {
        java.sql.Connection c = connection;
        if (c == null) {
            throw new SQLException(MessageFactory.get("pooledConnection.disconnected"));
        }
        return c;
    }

    /**
     * Clears the warnings on the physical connection. A no-op while this pooled connection has no physical
     * connection, as there is nothing to clear warnings on. Like the delegating getters below, this operation is
     * best effort against the live session: it does not take the per-connection lock, and the session it reaches
     * may be in use by a borrower or by the pool's own work.
     *
     * @throws SQLException if the driver refuses to clear the warnings
     */
    @Override
    public void clearWarnings() throws SQLException {
        java.sql.Connection c = connection;
        if (c == null) {
            return;
        }
        c.clearWarnings();
    }

    /**
     * Returns true if this pooled connection is closed: either it currently has no physical connection or that
     * connection is closed.
     *
     * @return true if disconnected or the physical connection is closed
     *
     * @throws SQLException if the physical connection reports a failure
     */
    @Override
    public boolean isClosed() throws SQLException {
        java.sql.Connection c = connection;
        return c == null || c.isClosed();
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return requireConnection().getAutoCommit();
    }

    @Override
    public String getCatalog() throws SQLException {
        return requireConnection().getCatalog();
    }

    @Override
    public int getHoldability() throws SQLException {
        return requireConnection().getHoldability();
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return requireConnection().isReadOnly();
    }

    @Override
    public String getSchema() throws SQLException {
        return requireConnection().getSchema();
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return requireConnection().getTransactionIsolation();
    }
}

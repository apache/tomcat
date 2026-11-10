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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.sql.XAConnection;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition;
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * The data source base of the pool: it delegates every configuration property to the
 * {@link PoolConfiguration} it holds and implements the {@link javax.sql.DataSource} accessors on top of a lazily
 * created {@link ConnectionPool}. The class doubles as the data source's own JMX/bean surface, which is why the
 * public getters and setters here are the attribute surface a container publishes.
 */

public class DataSourceProxy implements PoolConfiguration {
    private static final Log log = LogFactory.getLog(DataSourceProxy.class);

    private final AtomicBoolean connectionWithoutCredentialsWarningIssued = new AtomicBoolean(false);

    /**
     * The underlying connection pool.
     */
    protected volatile ConnectionPool pool = null;

    /**
     * The pool configuration properties.
     */
    protected volatile PoolConfiguration poolProperties = null;

    /**
     * Terminal lifecycle flag, set by {@link #close(boolean)} under this proxy's monitor. Once set, no pool is
     * published any more and creation fails with an {@link SQLException}.
     */
    private volatile boolean closed = false;

    /**
     * Serializes pool construction away from this proxy's monitor. The constructor pre-connects the initial
     * connections and can block in the driver, so it must not run under the monitor {@link #close(boolean)} takes.
     * This lock is taken before the proxy monitor and never while holding it.
     */
    private final Object poolCreationLock = new Object();

    /**
     * Construct a DataSourceProxy with default pool properties.
     */
    public DataSourceProxy() {
        this(new PoolProperties());
    }

    /**
     * Construct a DataSourceProxy with the given pool configuration.
     *
     * @param poolProperties The pool configuration properties
     */
    public DataSourceProxy(PoolConfiguration poolProperties) {
        if (poolProperties == null) {
            throw new NullPointerException(MessageFactory.get("dataSourceProxy.poolConfigurationNull"));
        }
        this.poolProperties = poolProperties;
    }


    /**
     * Check if this proxy implements, or wraps an instance of, the given interface. The configured
     * {@link PoolConfiguration#getDataSource() DataSource}, if any, is checked as the wrapped object.
     * <p>
     * Has to match signature in DataSource.
     *
     * @param iface The interface to check
     *
     * @return {@code true} if this proxy implements the interface or wraps an object that does
     *
     * @throws SQLException if the wrapped data source throws one
     */
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return true;
        }
        Object dataSource = poolProperties.getDataSource();
        if (iface.isInstance(dataSource)) {
            return true;
        }
        if (dataSource instanceof java.sql.Wrapper) {
            // Like the pooled connections, the configured data source may
            // itself be a wrapper - of the driver's data source, of a
            // pooling data source the configuration replaced - and it is
            // what answers for its own layers
            return ((java.sql.Wrapper) dataSource).isWrapperFor(iface);
        }
        return false;
    }


    /**
     * Unwrap the proxy to the given interface. The configured {@link PoolConfiguration#getDataSource() DataSource}, if
     * any, is checked as the wrapped object.
     * <p>
     * Has to match signature in DataSource
     *
     * @param <T>   The interface type
     * @param iface The interface to unwrap to
     *
     * @return an object that implements the interface
     *
     * @throws SQLException if no object implementing the interface is found
     */
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        Object dataSource = poolProperties.getDataSource();
        if (iface.isInstance(dataSource)) {
            return iface.cast(dataSource);
        }
        if (dataSource instanceof java.sql.Wrapper && ((java.sql.Wrapper) dataSource).isWrapperFor(iface)) {
            return ((java.sql.Wrapper) dataSource).unwrap(iface);
        }
        // The Wrapper contract asks for a failure, not for a silent null
        // that turns into an unrelated NullPointerException at the caller's
        // first use of the "unwrapped" value.
        throw new SQLException(MessageFactory.get("dataSourceProxy.notAWrapper", iface.getName()));
    }

    /**
     * Get a database connection. {@link javax.sql.DataSource#getConnection()}
     *
     * @param username The user name
     * @param password The password
     *
     * @return the connection
     *
     * @throws SQLException Connection error, or this data source has been closed
     */
    public Connection getConnection(String username, String password) throws SQLException {
        if (this.getPoolProperties().isAlternateUsernameAllowed()) {
            // Reach the pool through createPool(), as every borrow path does.
            return createPool().getConnection(username, password);
        } else {
            if (connectionWithoutCredentialsWarningIssued.compareAndSet(false, true)) {
                log.warn(MessageFactory.get("dataSourceProxy.alternateUsernameIgnored", username));
            }
            return getConnection();
        }
    }

    /**
     * Return the pool configuration properties.
     *
     * @return the pool configuration
     */
    public PoolConfiguration getPoolProperties() {
        return poolProperties;
    }

    /**
     * Sets up the connection pool, by creating a pooling driver. The reference is taken from the volatile field with a
     * single read, so that the borrow paths which use this method as their single point of access cannot observe a
     * {@code null} that a concurrent {@link #close(boolean)} published after they saw the field set. The result is
     * never {@code null}: it is either the pool that is in use or this method fails with an {@link SQLException}.
     *
     * @return the connection pool
     *
     * @throws SQLException Error creating pool, or if this data source has been closed
     */
    public ConnectionPool createPool() throws SQLException {
        // Read the volatile field once: guarding on it and then returning it
        // would read it twice, and a close() landing between the reads makes
        // the second one null.
        final ConnectionPool p = pool;
        if (p != null) {
            return p;
        } else {
            return pCreatePool();
        }
    }

    /**
     * Sets up the connection pool, by creating a pooling driver, unless this data source has been closed - which
     * then rejects the caller, save for a pool that a racing creation still publishes in time for the closure to
     * take it down again.
     */
    private ConnectionPool pCreatePool() throws SQLException {
        // Runs under the construction gate, not under the monitor that
        // close(boolean) takes; why the two are separate is recorded at
        // poolCreationLock. Returning null is not possible here: either the
        // closed flag rejects the caller or a pool is published and returned.
        if (closed) {
            throw new SQLException(MessageFactory.get("dataSourceProxy.dataSourceClosed"));
        }
        synchronized (poolCreationLock) {
            // Re-check under the gate: a close completed while this thread
            // queued must not be resurrected, and a creator that won the
            // gate has already published its pool. The gate also keeps this
            // the only construction the shared poolProperties ever faces.
            if (closed) {
                throw new SQLException(MessageFactory.get("dataSourceProxy.dataSourceClosed"));
            }
            final ConnectionPool existing = pool;
            if (existing != null) {
                return existing;
            }
            // The constructor - and with it the driver connects of the
            // initial pre-load - runs with the close monitor free. A failure
            // here propagates directly: an init that fails after starting
            // anything tears its pool down itself before throwing.
            final ConnectionPool fresh = new ConnectionPool(poolProperties);
            final boolean rejected;
            try {
                synchronized (this) {
                    // Check-and-publish against the closure, which sets the flag
                    // and takes the field in one step under this same monitor:
                    // whichever of the two runs first wins, so the close either
                    // finds this pool published and closes it, or it found the
                    // field empty and this thread takes the pool down below. It
                    // is never neither - that was the leak - and never both.
                    rejected = closed;
                    if (!rejected) {
                        // Finish the configuration before the pool can be read
                        // from. A write of one of the attributes the check
                        // corrects lands its value in the shared configuration
                        // but skips that correction, because the setters run it
                        // only once a pool is reachable. The check has to come
                        // before the cleaner is looked over: it decides the
                        // interval the cleaner should be running on.
                        fresh.checkPoolConfiguration(poolProperties);
                        fresh.reconcilePoolCleaner(poolProperties);
                        pool = fresh;
                    }
                }
            } catch (Throwable x) {
                // What handleThrowable lets through is not recoverable, so
                // check that first. Otherwise the constructor has already
                // pre-loaded sessions and may have armed the cleaner, so close
                // the fresh pool before reporting the publication failure.
                ExceptionUtils.handleThrowable(x);
                try {
                    fresh.close(true);
                } catch (Exception y) {
                    log.warn(MessageFactory.get("dataSourceProxy.errorClosingPool"), y);
                }
                if (x instanceof SQLException) {
                    throw (SQLException) x;
                }
                SQLException sx = new SQLException(x.getMessage());
                sx.initCause(x);
                throw sx;
            }
            if (rejected) {
                // close(boolean) completed while this pool was being built
                // and had nothing published to close, so the sessions the
                // pre-load established and the cleaner the constructor
                // started are this thread's to release.
                try {
                    fresh.close(true);
                } catch (Exception x) {
                    log.warn(MessageFactory.get("dataSourceProxy.errorClosingPool"), x);
                }
                throw new SQLException(MessageFactory.get("dataSourceProxy.dataSourceClosed"));
            }
            return fresh;
        }
    }

    /**
     * Get a database connection. {@link javax.sql.DataSource#getConnection()}
     *
     * @return the connection
     *
     * @throws SQLException Connection error, or this data source has been closed
     */
    public Connection getConnection() throws SQLException {
        // One read of the volatile pool field, through createPool() - see
        // that method for why the borrow paths reach the pool only this way.
        return createPool().getConnection();
    }

    /**
     * Retrieves a connection future: the pool's synchronous borrow is invoked first with no wait, and when a
     * connection cannot be handed out immediately the returned future completes once one becomes available.
     *
     * @return a Future containing a reference to the connection when it becomes available
     *
     * @throws SQLException Connection error, or this data source has been closed
     */
    public Future<Connection> getConnectionAsync() throws SQLException {
        // One read of the volatile pool field, through createPool() - see
        // that method for why the borrow paths reach the pool only this way.
        return createPool().getConnectionAsync();
    }

    /**
     * Get a database connection. {@link javax.sql.XADataSource#getXAConnection()}
     *
     * @return the connection
     *
     * @throws SQLException Connection error
     */
    public XAConnection getXAConnection() throws SQLException {
        Connection con = getConnection();
        if (con instanceof XAConnection) {
            return (XAConnection) con;
        } else {
            try {
                con.close();
            } catch (Exception ignore) {
                // Ignore
            }
            throw new SQLException(MessageFactory.get("dataSourceProxy.connectionNotXA"));
        }
    }

    /**
     * Get a database connection. {@link javax.sql.XADataSource#getXAConnection(String, String)}
     *
     * @param username The user name
     * @param password The password
     *
     * @return the connection
     *
     * @throws SQLException Connection error
     */
    public XAConnection getXAConnection(String username, String password) throws SQLException {
        Connection con = getConnection(username, password);
        if (con instanceof XAConnection) {
            return (XAConnection) con;
        } else {
            try {
                con.close();
            } catch (Exception ignore) {
                // Ignore
            }
            throw new SQLException(MessageFactory.get("dataSourceProxy.connectionNotXA"));
        }
    }


    /**
     * Get a database connection. {@link javax.sql.DataSource#getConnection()}
     *
     * @return the connection
     *
     * @throws SQLException Connection error
     */
    public javax.sql.PooledConnection getPooledConnection() throws SQLException {
        return (javax.sql.PooledConnection) getConnection();
    }

    /**
     * Get a database connection. {@link javax.sql.DataSource#getConnection(String,String)}
     *
     * @param username The user name
     * @param password The password
     *
     * @return the connection
     *
     * @throws SQLException Connection error
     */
    public javax.sql.PooledConnection getPooledConnection(String username, String password) throws SQLException {
        return (javax.sql.PooledConnection) getConnection(username, password);
    }

    /**
     * Return the underlying connection pool, creating it if necessary.
     *
     * @return the connection pool, or null if creation failed or this data source has been closed
     */
    public ConnectionPool getPool() {
        try {
            return createPool();
        } catch (SQLException | RuntimeException x) {
            if (closed) {
                // A closed data source legitimately has no pool; this is
                // a normal lifecycle state, not a creation failure.
                return null;
            }
            // A configuration error before the pool is built - a malformed
            // interceptor value, for example - is a creation failure like any
            // other to this contract: report it and answer no pool rather than
            // let it escape from a getter documented to return null.
            log.error(MessageFactory.get("dataSourceProxy.errorCreatingPool"), x);
            return null;
        }
    }


    /**
     * Close the connection pool, returning connections to the database. The closure is terminal: later borrow or pool
     * creation attempts on this data source fail with an <code>SQLException</code> instead of recreating the pool.
     */
    public void close() {
        close(false);
    }

    /**
     * Close the connection pool. If a pool creation is in flight, this method does not wait for it - the driver
     * connects of the creation's initial pre-load can block for an unbounded, uninterruptible time against a hung
     * database - it marks this data source closed and returns: the creation observes the closure at its publication
     * step and closes the pool it built instead of publishing it. The same reason keeps the teardown outside the
     * monitor: the physical disconnect of every pooled session is as unbounded against a hung database as the
     * connect is, and it must not park the paths that need the monitor meanwhile - a later borrow's publication step,
     * or the container's JMX lifecycle callbacks.
     *
     * @param all if true close all connections, otherwise only idle ones
     */
    public void close(boolean all) {
        closePool(takePoolForClose(), all);
    }

    /**
     * Sets the terminal closed flag and takes the published pool, as one step under this data source's monitor.
     * The check-and-publish race this closes with is told at
     * {@code pCreatePool()}'s publication step, which takes the same monitor: whichever of the
     * two runs first wins. The closed flag makes the closure terminal for the pool creation paths as well: a closed
     * data source no longer resurrects its pool on the next borrow or statistics access.
     *
     * @return the pool that must now be torn down, or {@code null} when nothing was published (yet)
     */
    protected synchronized ConnectionPool takePoolForClose() {
        closed = true;
        final ConnectionPool p = pool;
        pool = null;
        return p;
    }

    /**
     * Tears down the pool that {@link #takePoolForClose()} handed over, outside this data source's monitor: a
     * physical disconnect against a hung database is unbounded and uninterruptible, and a parked teardown must not
     * park the later borrows or lifecycle callbacks that need that monitor. A failure of the teardown is logged,
     * not propagated: the closure already happened when the flag was set, and one failing session must not stop
     * the rest from being disconnected or hide the closure from the caller.
     *
     * @param p   the pool to close, or {@code null} to close nothing
     * @param all if true close all connections, otherwise only idle ones
     */
    protected void closePool(ConnectionPool p, boolean all) {
        if (p == null) {
            return;
        }
        try {
            p.close(all);
        } catch (Exception x) {
            log.warn(MessageFactory.get("dataSourceProxy.errorClosingPool"), x);
        }
    }

    /**
     * Return the current number of connections in the pool. The pool is not created by this call: before the pool
     * has been created, and after it has been closed, this returns <code>0</code> even though the configuration
     * asks for more connections. See {@link #getSize()} for the reading that creates the pool when necessary.
     *
     * @return the pool size, or <code>0</code> while no pool instance exists
     */
    public int getPoolSize() {
        final ConnectionPool p = pool;
        if (p == null) {
            return 0;
        } else {
            return p.getSize();
        }
    }


    @Override
    public String toString() {
        return super.toString() + "{" + getPoolProperties() + "}";
    }


    @Override
    public String getPoolName() {
        return getPoolProperties().getPoolName();
    }


    /**
     * Replaces the configuration this data source hands out. A pool that already exists keeps the configuration it was
     * built from; only a pool created afterwards uses the replacement. The one thing the replacement reaches on a
     * running pool is its background maintenance, which is judged from this class's current configuration.
     *
     * @param poolProperties The new pool configuration
     */
    public void setPoolProperties(PoolConfiguration poolProperties) {
        if (poolProperties == null) {
            throw new NullPointerException(MessageFactory.get("dataSourceProxy.poolConfigurationNull"));
        }
        this.poolProperties = poolProperties;
    }


    @Override
    public void setDriverClassName(String driverClassName) {
        this.poolProperties.setDriverClassName(driverClassName);
    }


    /**
     * Sets the number of connections pre-loaded when the pool starts. The value is used when the pool is created; a
     * later write would only change the configuration without pre-loading any connection.
     *
     * @param initialSize the number of connections to pre-load
     *
     * @throws UnsupportedOperationException if the pool has already been created or this data source has been closed
     */
    @Override
    public synchronized void setInitialSize(int initialSize) {
        checkPoolNotStarted("initialSize");
        this.poolProperties.setInitialSize(initialSize);
    }


    @Override
    public void setInitSQL(String initSQL) {
        this.poolProperties.setInitSQL(initSQL);
    }


    @Override
    public void setLogAbandoned(boolean logAbandoned) {
        this.poolProperties.setLogAbandoned(logAbandoned);
    }


    @Override
    public synchronized void setMaxActive(int maxActive) {
        this.poolProperties.setMaxActive(maxActive);
        poolConfigurationUpdated();
    }


    @Override
    public synchronized void setMaxIdle(int maxIdle) {
        this.poolProperties.setMaxIdle(maxIdle);
        poolConfigurationUpdated();
    }


    @Override
    public void setMaxWait(int maxWait) {
        this.poolProperties.setMaxWait(maxWait);
    }


    @Override
    public synchronized void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        this.poolProperties.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        poolCleanerAttributeUpdated();
    }


    @Override
    public synchronized void setMinIdle(int minIdle) {
        this.poolProperties.setMinIdle(minIdle);
        poolConfigurationUpdated();
    }


    @Override
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.poolProperties.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    }


    /**
     * Sets the password used to establish connections with. The pool configuration keeps the matching database
     * property in step, so a {@code null} password also removes it. Connections that are already established keep the
     * credential they connected with until they leave the pool.
     *
     * @param password the password, or {@code null} to take the credential away
     */
    @Override
    public void setPassword(String password) {
        this.poolProperties.setPassword(password);
    }


    @Override
    public synchronized void setRemoveAbandoned(boolean removeAbandoned) {
        this.poolProperties.setRemoveAbandoned(removeAbandoned);
        poolCleanerAttributeUpdated();
    }


    @Override
    public synchronized void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        this.poolProperties.setRemoveAbandonedTimeout(removeAbandonedTimeout);
        poolCleanerAttributeUpdated();
    }


    /**
     * Validates the configuration a setter just wrote and reconciles the pool's cleaner with it, as one step under the
     * pool's monitor. A pool that has not been created yet has nothing to report to.
     */
    private void poolConfigurationUpdated() {
        final ConnectionPool p = pool;
        if (p == null) {
            return;
        }
        p.applyConfigurationChange(poolProperties);
    }


    /**
     * Starts or stops a created pool's background maintenance after a change to one of the properties
     * {@link PoolConfiguration#isPoolSweeperEnabled()} computes from, mirroring the pool MBean's handling of the same
     * attributes. The change is handed to {@link ConnectionPool#reconcilePoolCleaner(PoolConfiguration)} rather than
     * decided here, since the MBean writes the same configuration without a monitor.
     */
    private void poolCleanerAttributeUpdated() {
        final ConnectionPool p = pool;
        if (p == null) {
            return;
        }
        p.reconcilePoolCleaner(poolProperties);
    }


    @Override
    public void setTestOnBorrow(boolean testOnBorrow) {
        this.poolProperties.setTestOnBorrow(testOnBorrow);
    }


    @Override
    public void setTestOnConnect(boolean testOnConnect) {
        this.poolProperties.setTestOnConnect(testOnConnect);
    }


    @Override
    public void setTestOnReturn(boolean testOnReturn) {
        this.poolProperties.setTestOnReturn(testOnReturn);
    }


    @Override
    public synchronized void setTestWhileIdle(boolean testWhileIdle) {
        this.poolProperties.setTestWhileIdle(testWhileIdle);
        poolCleanerAttributeUpdated();
    }


    @Override
    public synchronized void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        this.poolProperties.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        poolConfigurationUpdated();
    }


    @Override
    public void setUrl(String url) {
        this.poolProperties.setUrl(url);
    }


    /**
     * Sets the user name used to establish connections with. The value goes to the pool configuration, which - as
     * {@link PoolConfiguration#setUsername(String)} promises - keeps the {@code user} entry of the database properties
     * the driver is given in step with it, and takes that entry away as well for a {@code null} user name.
     * <p>
     * Only connections established from now on, as {@link #setPassword(String)} explains: a pooled connection
     * reconnects with the credential it first connected with.
     *
     * @param username the user name, or {@code null} to take the credential away
     */
    @Override
    public void setUsername(String username) {
        this.poolProperties.setUsername(username);
    }


    @Override
    public void setValidationInterval(long validationInterval) {
        this.poolProperties.setValidationInterval(validationInterval);
    }


    @Override
    public void setValidationQuery(String validationQuery) {
        this.poolProperties.setValidationQuery(validationQuery);
    }


    @Override
    public void setValidatorClassName(String className) {
        this.poolProperties.setValidatorClassName(className);
    }


    @Override
    public void setValidationQueryTimeout(int validationQueryTimeout) {
        this.poolProperties.setValidationQueryTimeout(validationQueryTimeout);
    }


    @Override
    public void setJdbcInterceptors(String interceptors) {
        this.getPoolProperties().setJdbcInterceptors(interceptors);
    }


    /**
     * Rejects a write to a property that is read only while the pool is being created.
     *
     * @param property the property name
     *
     * @throws UnsupportedOperationException if the pool exists or this data source is closed
     */
    private void checkPoolNotStarted(String property) {
        if (closed) {
            throw new UnsupportedOperationException(
                    MessageFactory.get("dataSourceProxy.startTimePropertyAfterClose", property));
        }
        if (pool != null) {
            throw new UnsupportedOperationException(
                    MessageFactory.get("dataSourceProxy.startTimePropertyAfterStart", property));
        }
    }


    /**
     * Sets whether JMX is enabled, before the pool is created. Whether the pool registers an MBean is decided when
     * the pool is created, so a write after that point is rejected rather than stored without effect, matching the
     * pool MBean's own setter. A closed data source is rejected as well: it can never create a pool again, so a write
     * there would be stored without any possible effect.
     *
     * @param enabled whether JMX is enabled
     *
     * @throws UnsupportedOperationException if the pool has already been created or this data source has been closed
     */
    @Override
    public synchronized void setJmxEnabled(boolean enabled) {
        if (closed) {
            throw new UnsupportedOperationException(MessageFactory.get("dataSourceProxy.jmxEnabledAfterClose"));
        }
        if (pool != null) {
            throw new UnsupportedOperationException(MessageFactory.get("dataSourceProxy.jmxEnabledAfterStart"));
        }
        this.getPoolProperties().setJmxEnabled(enabled);
    }


    /**
     * Sets whether the pool's idle queue is fair. The queue type is chosen when the pool is created; a later write would
     * only change the configuration without changing the live queue.
     *
     * @param fairQueue {@code true} to use a fair idle queue
     *
     * @throws UnsupportedOperationException if the pool has already been created or this data source has been closed
     */
    @Override
    public synchronized void setFairQueue(boolean fairQueue) {
        checkPoolNotStarted("fairQueue");
        this.getPoolProperties().setFairQueue(fairQueue);
    }


    @Override
    public void setUseLock(boolean useLock) {
        this.getPoolProperties().setUseLock(useLock);
    }


    @Override
    public void setDefaultCatalog(String catalog) {
        this.getPoolProperties().setDefaultCatalog(catalog);
    }


    @Override
    public void setDefaultAutoCommit(Boolean autocommit) {
        this.getPoolProperties().setDefaultAutoCommit(autocommit);
    }


    @Override
    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        this.getPoolProperties().setDefaultTransactionIsolation(defaultTransactionIsolation);
    }


    @Override
    public void setConnectionProperties(String properties) {
        try {
            // Delegate so the raw string is stored as well as parsed: a
            // hand-rolled merge into the database properties left
            // getConnectionProperties() reporting null for a value that
            // was just set, unlike every other implementation of the
            // interface (PoolProperties itself and the pool's JMX wrapper).
            getPoolProperties().setConnectionProperties(properties);
        } catch (RuntimeException x) {
            log.error(MessageFactory.get("dataSourceProxy.unableToParseConnectionProperties"), x);
            throw x;
        }
    }


    @Override
    public void setUseEquals(boolean useEquals) {
        this.getPoolProperties().setUseEquals(useEquals);
    }

    /**
     * no-op {@link javax.sql.DataSource#getParentLogger}
     *
     * @return no return value
     *
     * @throws SQLFeatureNotSupportedException Unsupported
     */
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * no-op {@link javax.sql.DataSource#getLogWriter}
     *
     * @return null
     *
     * @throws SQLException No exception
     */
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }


    /**
     * no-op {@link javax.sql.DataSource#setLogWriter(PrintWriter)}
     *
     * @param out Ignored
     *
     * @throws SQLException No exception
     */
    public void setLogWriter(PrintWriter out) throws SQLException {
        // NOOP
    }

    /**
     * The pool's borrow wait timeout, {@link PoolConfiguration#getMaxWait()}, expressed in whole seconds:
     * {@link javax.sql.DataSource#getLoginTimeout()} is mapped onto that property rather than ignored. A
     * sub-second <code>maxWait</code> therefore reads back as zero, and so does a negative one, which the pool reads
     * as "wait indefinitely"; a negative login timeout is not a legal JDBC value.
     *
     * @return the timeout in whole seconds, never negative
     */
    public int getLoginTimeout() {
        return Math.max(0, poolProperties.getMaxWait() / 1000);
    }

    /**
     * Reconfigures the pool's borrow wait timeout: {@link javax.sql.DataSource#setLoginTimeout(int)} is mapped onto
     * {@link PoolConfiguration#setMaxWait(int)} as {@code 1000 * i} milliseconds, with the seconds-to-milliseconds
     * conversion of an {@code int} seconds value. A seconds value too large to be represented in milliseconds is
     * clamped to the largest representable timeout rather than wrapped. A negative value is rejected, since the pool
     * reads a non-positive wait as "wait indefinitely".
     *
     * @param i The timeout in seconds
     *
     * @throws IllegalArgumentException if the timeout is negative
     */
    public void setLoginTimeout(int i) {
        if (i < 0) {
            throw new IllegalArgumentException(MessageFactory.get("dataSourceProxy.negativeLoginTimeout",
                    Integer.valueOf(i)));
        }
        // The multiplication happens in long and the result is clamped: in
        // int arithmetic the product of a seconds value above a little under
        // 25 days wraps into the negatives, and the borrow path reads a
        // maxWait of zero or less as the pool's "no timeout" setting - the
        // wrapped value would invert the meaning of the timeout just set.
        poolProperties.setMaxWait((int) Math.min(Integer.MAX_VALUE, 1000L * i));
    }


    @Override
    public int getSuspectTimeout() {
        return getPoolProperties().getSuspectTimeout();
    }


    @Override
    public synchronized void setSuspectTimeout(int seconds) {
        getPoolProperties().setSuspectTimeout(seconds);
        poolCleanerAttributeUpdated();
    }

    // These members expose JMX attributes through Tomcat's dynamic
    // reflection.
    /**
     * If the pool has not been created, it will be created during this call.
     *
     * @return the number of established but idle connections
     */
    public int getIdle() {
        try {
            return createPool().getIdle();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * {@link #getIdle()}
     *
     * @return the number of established but idle connections
     */
    public int getNumIdle() {
        return getIdle();
    }

    /**
     * Forces an abandon check on the connection pool. If connections that have been abandoned exists, they will be
     * closed during this run
     */
    public void checkAbandoned() {
        try {
            createPool().checkAbandoned();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Forces a check for resizing of the idle connections
     */
    public void checkIdle() {
        try {
            createPool().checkIdle();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Returns the number of connections currently in use by the application.
     *
     * @return number of connections in use by the application
     */
    public int getActive() {
        try {
            return createPool().getActive();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Returns the number of connections currently in use by the application.
     *
     * @return number of active connections
     */
    public int getNumActive() {
        return getActive();
    }

    /**
     * Returns the number of threads currently waiting for a connection from the pool.
     *
     * @return number of waiting threads
     */
    public int getWaitCount() {
        try {
            return createPool().getWaitCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Returns the current total number of connections in the pool. If the pool has not been created, it will be
     * created during this call, and a data source that has been closed throws {@link RuntimeException}. See
     * {@link #getPoolSize()} for the reading that never creates the pool and answers <code>0</code> while none
     * exists.
     *
     * @return current pool size
     */
    public int getSize() {
        try {
            return createPool().getSize();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Performs a validation on idle connections.
     * <p>
     * Note: this operation is safe with concurrent traffic and with a closing
     * pool even when the per-connection lock is disabled (sweeper switched
     * off and {@link PoolProperties#getUseLock()} returning {@code false}),
     * as described on {@link ConnectionPool#testAllIdle()}.
     */
    public void testIdle() {
        try {
            createPool().testAllIdle();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections borrowed from this pool.
     *
     * @return the borrowed connection count
     */
    public long getBorrowedCount() {
        try {
            return createPool().getBorrowedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections returned to this pool.
     *
     * @return the returned connection count
     */
    public long getReturnedCount() {
        try {
            return createPool().getReturnedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections created by this pool.
     *
     * @return the created connection count
     */
    public long getCreatedCount() {
        try {
            return createPool().getCreatedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections released from this pool.
     *
     * @return the released connection count
     */
    public long getReleasedCount() {
        try {
            return createPool().getReleasedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections reconnected by this pool.
     *
     * @return the reconnected connection count
     */
    public long getReconnectedCount() {
        try {
            return createPool().getReconnectedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections released by remove abandoned.
     *
     * @return the PoolCleaner removed abandoned connection count
     */
    public long getRemoveAbandonedCount() {
        try {
            return createPool().getRemoveAbandonedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections released by eviction.
     *
     * @return the PoolCleaner evicted idle connection count
     */
    public long getReleasedIdleCount() {
        try {
            return createPool().getReleasedIdleCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * reset the statistics of this pool.
     */
    public void resetStats() {
        try {
            createPool().resetStats();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }


    /**
     * Returns the connection properties with any credential they carry masked out, so the data source's own bean/JMX
     * surface does not expose a password in cleartext. The write is not masked: {@link #setConnectionProperties(String)}
     * takes the string as it is meant to reach the driver. Use {@link #getPoolProperties()} for the raw value.
     * {@inheritDoc}
     */
    @Override
    public String getConnectionProperties() {
        return PoolProperties.maskCredentials(getPoolProperties().getConnectionProperties());
    }


    /**
     * Returns a copy of the database properties with the credential entries removed. A credential entry is one whose
     * key is {@code password}, {@code pass} or {@code pwd}, ignoring case and surrounding whitespace. This object
     * doubles as its own JMX/bean surface and {@link #setPassword(String)} puts the password into the database
     * properties, so returning them as they are would expose the plaintext password through a getter while
     * {@link #getPassword()} is explicitly masked -
     * the same protection the pool's own MBean
     * ({@link org.apache.tomcat.jdbc.pool.jmx.ConnectionPool#getDbProperties()}) already applies. A configuration whose
     * database properties are {@code null} - a state {@link #setPassword(String)} and {@link #setUsername(String)}
     * document as supported - has nothing to mask, so this returns {@code null}. {@inheritDoc}
     */
    @Override
    public Properties getDbProperties() {
        Properties live = getPoolProperties().getDbProperties();
        if (live == null) {
            return null;
        }
        return PoolUtilities.cloneWithoutPassword(live);
    }


    @Override
    public String getDefaultCatalog() {
        return getPoolProperties().getDefaultCatalog();
    }


    @Override
    public int getDefaultTransactionIsolation() {
        return getPoolProperties().getDefaultTransactionIsolation();
    }


    @Override
    public String getDriverClassName() {
        return getPoolProperties().getDriverClassName();
    }


    @Override
    public int getInitialSize() {
        return getPoolProperties().getInitialSize();
    }


    @Override
    public String getInitSQL() {
        return getPoolProperties().getInitSQL();
    }


    /**
     * Returns the interceptor configuration with any credential its definitions carry masked out, so the data source's
     * own bean/JMX surface does not expose a password in cleartext. Use {@link #getPoolProperties()} for the raw value.
     * {@inheritDoc}
     */
    @Override
    public String getJdbcInterceptors() {
        return PoolProperties.maskInterceptorCredentials(getPoolProperties().getJdbcInterceptors());
    }


    @Override
    public int getMaxActive() {
        return getPoolProperties().getMaxActive();
    }


    @Override
    public int getMaxIdle() {
        return getPoolProperties().getMaxIdle();
    }


    @Override
    public int getMaxWait() {
        return getPoolProperties().getMaxWait();
    }


    @Override
    public int getMinEvictableIdleTimeMillis() {
        return getPoolProperties().getMinEvictableIdleTimeMillis();
    }


    @Override
    public int getMinIdle() {
        return getPoolProperties().getMinIdle();
    }


    @Override
    public long getMaxAge() {
        return getPoolProperties().getMaxAge();
    }


    @Override
    public String getName() {
        return getPoolProperties().getName();
    }


    @Override
    public int getNumTestsPerEvictionRun() {
        return getPoolProperties().getNumTestsPerEvictionRun();
    }

    /**
     * @return DOES NOT RETURN THE PASSWORD, IT WOULD SHOW UP IN JMX
     */
    @Override
    public String getPassword() {
        return "Password not available as DataSource/JMX operation.";
    }


    @Override
    public int getRemoveAbandonedTimeout() {
        return getPoolProperties().getRemoveAbandonedTimeout();
    }


    @Override
    public int getTimeBetweenEvictionRunsMillis() {
        return getPoolProperties().getTimeBetweenEvictionRunsMillis();
    }


    /**
     * Returns the JDBC URL with any credential it carries masked out, so the data source's own bean/JMX surface does
     * not expose a password in cleartext. The write is not masked: {@link #setUrl(String)} takes the URL as it is meant
     * to reach the driver. Use {@link #getPoolProperties()} for the raw value. {@inheritDoc}
     */
    @Override
    public String getUrl() {
        return PoolProperties.maskCredentials(getPoolProperties().getUrl());
    }


    @Override
    public String getUsername() {
        return getPoolProperties().getUsername();
    }


    @Override
    public long getValidationInterval() {
        return getPoolProperties().getValidationInterval();
    }


    @Override
    public String getValidationQuery() {
        return getPoolProperties().getValidationQuery();
    }


    @Override
    public int getValidationQueryTimeout() {
        return getPoolProperties().getValidationQueryTimeout();
    }


    @Override
    public String getValidatorClassName() {
        return getPoolProperties().getValidatorClassName();
    }


    @Override
    public Validator getValidator() {
        return getPoolProperties().getValidator();
    }

    @Override
    public void setValidator(Validator validator) {
        getPoolProperties().setValidator(validator);
    }


    @Override
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return getPoolProperties().isAccessToUnderlyingConnectionAllowed();
    }


    @Override
    public Boolean isDefaultAutoCommit() {
        return getPoolProperties().isDefaultAutoCommit();
    }


    @Override
    public Boolean isDefaultReadOnly() {
        return getPoolProperties().isDefaultReadOnly();
    }


    @Override
    public boolean isLogAbandoned() {
        return getPoolProperties().isLogAbandoned();
    }


    @Override
    public boolean isPoolSweeperEnabled() {
        return getPoolProperties().isPoolSweeperEnabled();
    }


    @Override
    public boolean isRemoveAbandoned() {
        return getPoolProperties().isRemoveAbandoned();
    }


    @Override
    public int getAbandonWhenPercentageFull() {
        return getPoolProperties().getAbandonWhenPercentageFull();
    }


    @Override
    public boolean isTestOnBorrow() {
        return getPoolProperties().isTestOnBorrow();
    }


    @Override
    public boolean isTestOnConnect() {
        return getPoolProperties().isTestOnConnect();
    }


    @Override
    public boolean isTestOnReturn() {
        return getPoolProperties().isTestOnReturn();
    }


    @Override
    public boolean isTestWhileIdle() {
        return getPoolProperties().isTestWhileIdle();
    }


    @Override
    public Boolean getDefaultAutoCommit() {
        return getPoolProperties().getDefaultAutoCommit();
    }


    @Override
    public Boolean getDefaultReadOnly() {
        return getPoolProperties().getDefaultReadOnly();
    }


    /**
     * Returns the parsed interceptor definitions with their credential values masked, the display counterpart of
     * {@link #getJdbcInterceptors()}. This object doubles as its own JMX/bean surface, so the raw definitions - which
     * carry the pairs the pool builds its interceptor chains from - stay reachable only through
     * {@link #getPoolProperties()}. The copy is also defensive: it shares no definition, property or array slot with the
     * live configuration, so a caller cannot mutate the chain a later connection will build. {@inheritDoc}
     */
    @Override
    public InterceptorDefinition[] getJdbcInterceptorsAsArray() {
        return PoolProperties.maskInterceptorCredentials(getPoolProperties().getJdbcInterceptorsAsArray());
    }


    @Override
    public boolean getUseLock() {
        return getPoolProperties().getUseLock();
    }


    @Override
    public boolean isFairQueue() {
        return getPoolProperties().isFairQueue();
    }


    @Override
    public boolean isJmxEnabled() {
        return getPoolProperties().isJmxEnabled();
    }


    @Override
    public boolean isUseEquals() {
        return getPoolProperties().isUseEquals();
    }


    @Override
    public void setAbandonWhenPercentageFull(int percentage) {
        getPoolProperties().setAbandonWhenPercentageFull(percentage);
    }


    @Override
    public void setAccessToUnderlyingConnectionAllowed(boolean accessToUnderlyingConnectionAllowed) {
        getPoolProperties().setAccessToUnderlyingConnectionAllowed(accessToUnderlyingConnectionAllowed);
    }


    /**
     * Replaces the live database properties with the given set. The write is not masked, and it replaces the whole
     * collection: a read-modify-write of the masked {@link #getDbProperties()} view hands back a set with the password
     * removed and takes that credential away from the live configuration. Use {@link #setPassword(String)} and
     * {@link #setUsername(String)} to change the credentials, as {@link #getDbProperties()} describes.
     *
     * @param dbProperties the replacement database properties
     */
    @Override
    public void setDbProperties(Properties dbProperties) {
        getPoolProperties().setDbProperties(dbProperties);
    }


    @Override
    public void setDefaultReadOnly(Boolean defaultReadOnly) {
        getPoolProperties().setDefaultReadOnly(defaultReadOnly);
    }


    @Override
    public synchronized void setMaxAge(long maxAge) {
        getPoolProperties().setMaxAge(maxAge);
        poolConfigurationUpdated();
    }


    @Override
    public void setName(String name) {
        getPoolProperties().setName(name);
    }

    @Override
    public void setDataSource(Object ds) {
        getPoolProperties().setDataSource(ds);
    }

    @Override
    public Object getDataSource() {
        return getPoolProperties().getDataSource();
    }


    @Override
    public void setDataSourceJNDI(String jndiDS) {
        getPoolProperties().setDataSourceJNDI(jndiDS);
    }

    @Override
    public String getDataSourceJNDI() {
        return getPoolProperties().getDataSourceJNDI();
    }

    @Override
    public boolean isAlternateUsernameAllowed() {
        return getPoolProperties().isAlternateUsernameAllowed();
    }

    @Override
    public void setAlternateUsernameAllowed(boolean alternateUsernameAllowed) {
        getPoolProperties().setAlternateUsernameAllowed(alternateUsernameAllowed);
    }

    @Override
    public void setCommitOnReturn(boolean commitOnReturn) {
        getPoolProperties().setCommitOnReturn(commitOnReturn);
    }

    @Override
    public boolean getCommitOnReturn() {
        return getPoolProperties().getCommitOnReturn();
    }

    @Override
    public void setRollbackOnReturn(boolean rollbackOnReturn) {
        getPoolProperties().setRollbackOnReturn(rollbackOnReturn);
    }

    @Override
    public boolean getRollbackOnReturn() {
        return getPoolProperties().getRollbackOnReturn();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The disposable connection facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
    public void setUseDisposableConnectionFacade(boolean useDisposableConnectionFacade) {
        getPoolProperties().setUseDisposableConnectionFacade(useDisposableConnectionFacade);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The disposable connection facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
    public boolean getUseDisposableConnectionFacade() {
        return getPoolProperties().getUseDisposableConnectionFacade();
    }

    @Override
    public void setLogValidationErrors(boolean logValidationErrors) {
        getPoolProperties().setLogValidationErrors(logValidationErrors);
    }

    @Override
    public boolean getLogValidationErrors() {
        return getPoolProperties().getLogValidationErrors();
    }

    @Override
    public boolean getPropagateInterruptState() {
        return getPoolProperties().getPropagateInterruptState();
    }

    @Override
    public void setPropagateInterruptState(boolean propagateInterruptState) {
        getPoolProperties().setPropagateInterruptState(propagateInterruptState);
    }

    @Override
    public boolean isIgnoreExceptionOnPreLoad() {
        return getPoolProperties().isIgnoreExceptionOnPreLoad();
    }

    /**
     * Sets whether failures during the pool's initial pre-load may be ignored. The value is used only while the pool is
     * being created; a later write would only change the configuration.
     *
     * @param ignoreExceptionOnPreLoad {@code true} to ignore pre-load failures
     *
     * @throws UnsupportedOperationException if the pool has already been created or this data source has been closed
     */
    @Override
    public synchronized void setIgnoreExceptionOnPreLoad(boolean ignoreExceptionOnPreLoad) {
        checkPoolNotStarted("ignoreExceptionOnPreLoad");
        getPoolProperties().setIgnoreExceptionOnPreLoad(ignoreExceptionOnPreLoad);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The statement facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
    public boolean getUseStatementFacade() {
        return getPoolProperties().getUseStatementFacade();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The statement facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
    public void setUseStatementFacade(boolean useStatementFacade) {
        getPoolProperties().setUseStatementFacade(useStatementFacade);
    }

    /**
     * Purge all connections from the pool.
     */
    public void purge() {
        try {
            createPool().purge();
        } catch (SQLException x) {
            log.error(MessageFactory.get("dataSourceProxy.unableToPurgePool"), x);
        }
    }

    /**
     * Enable purging of connections on return to the pool.
     */
    public void purgeOnReturn() {
        try {
            createPool().purgeOnReturn();
        } catch (SQLException x) {
            log.error(MessageFactory.get("dataSourceProxy.unableToPurgePool"), x);
        }
    }
}

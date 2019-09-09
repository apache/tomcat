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

package org.apache.tomcat.dbcp.dbcp;

import java.io.PrintWriter;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import javax.sql.DataSource;

import org.apache.tomcat.dbcp.pool.KeyedObjectPoolFactory;
import org.apache.tomcat.dbcp.pool.impl.GenericKeyedObjectPool;
import org.apache.tomcat.dbcp.pool.impl.GenericKeyedObjectPoolFactory;
import org.apache.tomcat.dbcp.pool.impl.GenericObjectPool;


/**
 * <p>Basic implementation of <code>javax.sql.DataSource</code> that is
 * configured via JavaBeans properties.  This is not the only way to
 * combine the <em>commons-dbcp</em> and <em>commons-pool</em> packages,
 * but provides a "one stop shopping" solution for basic requirements.</p>
 *
 * <p>Users extending this class should take care to use appropriate accessors
 * rather than accessing protected fields directly to ensure thread-safety.</p>
 *
 * @author Glenn L. Nielsen
 * @author Craig R. McClanahan
 * @author Dirk Verbeeck
 */
public class BasicDataSource implements DataSource {

    static {
        // Attempt to prevent deadlocks - see DBCP - 272
        DriverManager.getDrivers();
    }

    // ------------------------------------------------------------- Properties

    /**
     * The default auto-commit state of connections created by this pool.
     */
    protected volatile boolean defaultAutoCommit = true;

    /**
     * Returns the default auto-commit property.
     *
     * @return true if default auto-commit is enabled
     */
    public boolean getDefaultAutoCommit() {
        return this.defaultAutoCommit;
    }

    /**
     * <p>Sets default auto-commit state of connections returned by this
     * datasource.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param defaultAutoCommit default auto-commit value
     */
    public void setDefaultAutoCommit(boolean defaultAutoCommit) {
        this.defaultAutoCommit = defaultAutoCommit;
    }


    /**
     * The default read-only state of connections created by this pool.
     */
    protected transient Boolean defaultReadOnly = null;

    /**
     * Returns the default readOnly property.
     *
     * @return true if connections are readOnly by default
     */
    public boolean getDefaultReadOnly() {
        Boolean val = defaultReadOnly;
        if (val != null) {
            return val.booleanValue();
        }
        return false;
    }

    /**
     * <p>Sets defaultReadonly property.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param defaultReadOnly default read-only value
     */
    public void setDefaultReadOnly(boolean defaultReadOnly) {
        this.defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
    }

    /**
     * The default TransactionIsolation state of connections created by this pool.
     */
    protected volatile int defaultTransactionIsolation =
        PoolableConnectionFactory.UNKNOWN_TRANSACTIONISOLATION;

    /**
     * Returns the default transaction isolation state of returned connections.
     *
     * @return the default value for transaction isolation state
     * @see Connection#getTransactionIsolation
     */
    public int getDefaultTransactionIsolation() {
        return this.defaultTransactionIsolation;
    }

    /**
     * <p>Sets the default transaction isolation state for returned
     * connections.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param defaultTransactionIsolation the default transaction isolation
     * state
     * @see Connection#getTransactionIsolation
     */
    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        this.defaultTransactionIsolation = defaultTransactionIsolation;
    }


    /**
     * The default "catalog" of connections created by this pool.
     */
    protected volatile String defaultCatalog = null;

    /**
     * Returns the default catalog.
     *
     * @return the default catalog
     */
    public String getDefaultCatalog() {
        return this.defaultCatalog;
    }

    /**
     * <p>Sets the default catalog.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param defaultCatalog the default catalog
     */
    public void setDefaultCatalog(String defaultCatalog) {
        if ((defaultCatalog != null) && (defaultCatalog.trim().length() > 0)) {
            this.defaultCatalog = defaultCatalog;
        }
        else {
            this.defaultCatalog = null;
        }
    }


    /**
     * The fully qualified Java class name of the JDBC driver to be used.
     */
    protected String driverClassName = null;

    /**
     * Returns the JDBC driver class name.
     *
     * @return the JDBC driver class name
     */
    public synchronized String getDriverClassName() {
        return this.driverClassName;
    }

    /**
     * <p>Sets the JDBC driver class name.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param driverClassName the class name of the JDBC driver
     */
    public synchronized void setDriverClassName(String driverClassName) {
        if ((driverClassName != null) && (driverClassName.trim().length() > 0)) {
            this.driverClassName = driverClassName;
        }
        else {
            this.driverClassName = null;
        }
    }

    /**
     * The class loader instance to use to load the JDBC driver. If not
     * specified, {@link Class#forName(String)} is used to load the JDBC driver.
     * If specified, {@link Class#forName(String, boolean, ClassLoader)} is
     * used.
     */
    protected ClassLoader driverClassLoader = null;

    /**
     * Returns the class loader specified for loading the JDBC driver. Returns
     * <code>null</code> if no class loader has been explicitly specified.
     */
    public synchronized ClassLoader getDriverClassLoader() {
        return this.driverClassLoader;
    }

    /**
     * <p>Sets the class loader to be used to load the JDBC driver.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param driverClassLoader the class loader with which to load the JDBC
     *                          driver
     */
    public synchronized void setDriverClassLoader(
            ClassLoader driverClassLoader) {
        this.driverClassLoader = driverClassLoader;
    }

    /**
     * True means that borrowObject returns the most recently used ("last in")
     * connection in the pool (if there are idle connections available).  False
     * means that the pool behaves as a FIFO queue - connections are taken from
     * the idle instance pool in the order that they are returned to the pool.
     */
    private boolean lifo = GenericObjectPool.DEFAULT_LIFO;

    /**
     * Returns the LIFO property.
     *
     * @return true if connection pool behaves as a LIFO queue.
     *
     * @see #lifo
     */
    public synchronized boolean getLifo() {
        return this.lifo;
    }

    /**
     * Sets the LIFO property. True means the pool behaves as a LIFO queue;
     * false means FIFO.
     *
     * @param lifo the new value for the LIFO property
     *
     */
    public synchronized void setLifo(boolean lifo) {
        this.lifo = lifo;
        if (connectionPool != null) {
            connectionPool.setLifo(lifo);
        }
    }

    /**
     * The maximum number of active connections that can be allocated from
     * this pool at the same time, or negative for no limit.
     */
    protected int maxActive = GenericObjectPool.DEFAULT_MAX_ACTIVE;

    /**
     * <p>Returns the maximum number of active connections that can be
     * allocated at the same time.
     * </p>
     * <p>A negative number means that there is no limit.</p>
     *
     * @return the maximum number of active connections
     */
    public synchronized int getMaxActive() {
        return this.maxActive;
    }

    /**
     * Sets the maximum number of active connections that can be
     * allocated at the same time. Use a negative value for no limit.
     *
     * @param maxActive the new value for maxActive
     * @see #getMaxActive()
     */
    public synchronized void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
        if (connectionPool != null) {
            connectionPool.setMaxActive(maxActive);
        }
    }

    /**
     * The maximum number of connections that can remain idle in the
     * pool, without extra ones being destroyed, or negative for no limit.
     * If maxIdle is set too low on heavily loaded systems it is possible you
     * will see connections being closed and almost immediately new connections
     * being opened. This is a result of the active threads momentarily closing
     * connections faster than they are opening them, causing the number of idle
     * connections to rise above maxIdle. The best value for maxIdle for heavily
     * loaded system will vary but the default is a good starting point.
     */
    protected int maxIdle = GenericObjectPool.DEFAULT_MAX_IDLE;

    /**
     * <p>Returns the maximum number of connections that can remain idle in the
     * pool. Excess idle connections are destroyed on return to the pool.
     * </p>
     * <p>A negative value indicates that there is no limit</p>
     *
     * @return the maximum number of idle connections
     */
    public synchronized int getMaxIdle() {
        return this.maxIdle;
    }

    /**
     * Sets the maximum number of connections that can remain idle in the
     * pool. Excess idle connections are destroyed on return to the pool.
     *
     * @see #getMaxIdle()
     * @param maxIdle the new value for maxIdle
     */
    public synchronized void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
        if (connectionPool != null) {
            connectionPool.setMaxIdle(maxIdle);
        }
    }

    /**
     * The minimum number of active connections that can remain idle in the
     * pool, without extra ones being created when the evictor runs, or 0 to create none.
     * The pool attempts to ensure that minIdle connections are available when the idle object evictor
     * runs. The value of this property has no effect unless {@link #timeBetweenEvictionRunsMillis}
     * has a positive value.
     */
    protected int minIdle = GenericObjectPool.DEFAULT_MIN_IDLE;

    /**
     * Returns the minimum number of idle connections in the pool. The pool attempts
     * to ensure that minIdle connections are available when the idle object evictor
     * runs. The value of this property has no effect unless {@link #timeBetweenEvictionRunsMillis}
     * has a positive value.
     *
     * @return the minimum number of idle connections
     * @see GenericObjectPool#getMinIdle()
     */
    public synchronized int getMinIdle() {
        return this.minIdle;
    }

    /**
     * Sets the minimum number of idle connections in the pool. The pool attempts
     * to ensure that minIdle connections are available when the idle object evictor
     * runs. The value of this property has no effect unless {@link #timeBetweenEvictionRunsMillis}
     * has a positive value.
     *
     * @param minIdle the new value for minIdle
     * @see GenericObjectPool#setMinIdle(int)
     */
    public synchronized void setMinIdle(int minIdle) {
       this.minIdle = minIdle;
       if (connectionPool != null) {
           connectionPool.setMinIdle(minIdle);
       }
    }

    /**
     * The initial number of connections that are created when the pool
     * is started.
     *
     * @since 1.2
     */
    protected int initialSize = 0;

    /**
     * Returns the initial size of the connection pool.
     *
     * @return the number of connections created when the pool is initialized
     */
    public synchronized int getInitialSize() {
        return this.initialSize;
    }

    /**
     * <p>Sets the initial size of the connection pool.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param initialSize the number of connections created when the pool
     * is initialized
     */
    public synchronized void setInitialSize(int initialSize) {
        this.initialSize = initialSize;
    }

    /**
     * The maximum number of milliseconds that the pool will wait (when there
     * are no available connections) for a connection to be returned before
     * throwing an exception, or &lt;= 0 to wait indefinitely.
     */
    protected long maxWait = GenericObjectPool.DEFAULT_MAX_WAIT;

    /**
     * <p>Returns the maximum number of milliseconds that the pool will wait
     * for a connection to be returned before throwing an exception.
     * </p>
     * <p>A value less than or equal to zero means the pool is set to wait
     * indefinitely.</p>
     *
     * @return the maxWait property value
     */
    public synchronized long getMaxWait() {
        return this.maxWait;
    }

    /**
     * <p>Sets the maxWait property.
     * </p>
     * <p>Use -1 to make the pool wait indefinitely.
     * </p>
     *
     * @param maxWait the new value for maxWait
     * @see #getMaxWait()
     */
    public synchronized void setMaxWait(long maxWait) {
        this.maxWait = maxWait;
        if (connectionPool != null) {
            connectionPool.setMaxWait(maxWait);
        }
    }

    /**
     * Prepared statement pooling for this pool. When this property is set to <code>true</code>
     * both PreparedStatements and CallableStatements are pooled.
     */
    protected boolean poolPreparedStatements = false;

    /**
     * Returns true if we are pooling statements.
     *
     * @return true if prepared and callable statements are pooled
     */
    public synchronized boolean isPoolPreparedStatements() {
        return this.poolPreparedStatements;
    }

    /**
     * <p>Sets whether to pool statements or not.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param poolingStatements pooling on or off
     */
    public synchronized void setPoolPreparedStatements(boolean poolingStatements) {
        this.poolPreparedStatements = poolingStatements;
    }

    /**
     * <p>The maximum number of open statements that can be allocated from
     * the statement pool at the same time, or non-positive for no limit.  Since
     * a connection usually only uses one or two statements at a time, this is
     * mostly used to help detect resource leaks.</p>
     *
     * <p>Note: As of version 1.3, CallableStatements (those produced by {@link Connection#prepareCall})
     * are pooled along with PreparedStatements (produced by {@link Connection#prepareStatement})
     * and <code>maxOpenPreparedStatements</code> limits the total number of prepared or callable statements
     * that may be in use at a given time.</p>
     */
    protected int maxOpenPreparedStatements = GenericKeyedObjectPool.DEFAULT_MAX_TOTAL;

    /**
     * Gets the value of the {@link #maxOpenPreparedStatements} property.
     *
     * @return the maximum number of open statements
     * @see #maxOpenPreparedStatements
     */
    public synchronized int getMaxOpenPreparedStatements() {
        return this.maxOpenPreparedStatements;
    }

    /**
     * <p>Sets the value of the {@link #maxOpenPreparedStatements}
     * property.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param maxOpenStatements the new maximum number of prepared statements
     * @see #maxOpenPreparedStatements
     */
    public synchronized void setMaxOpenPreparedStatements(int maxOpenStatements) {
        this.maxOpenPreparedStatements = maxOpenStatements;
    }

    /**
     * The indication of whether objects will be validated before being
     * borrowed from the pool.  If the object fails to validate, it will be
     * dropped from the pool, and we will attempt to borrow another.
     */
    protected boolean testOnBorrow = true;

    /**
     * Returns the {@link #testOnBorrow} property.
     *
     * @return true if objects are validated before being borrowed from the
     * pool
     *
     * @see #testOnBorrow
     */
    public synchronized boolean getTestOnBorrow() {
        return this.testOnBorrow;
    }

    /**
     * Sets the {@link #testOnBorrow} property. This property determines
     * whether or not the pool will validate objects before they are borrowed
     * from the pool. For a <code>true</code> value to have any effect, the
     * <code>validationQuery</code> property must be set to a non-null string.
     *
     * @param testOnBorrow new value for testOnBorrow property
     */
    public synchronized void setTestOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
        if (connectionPool != null) {
            connectionPool.setTestOnBorrow(testOnBorrow);
        }
    }

    /**
     * The indication of whether objects will be validated before being
     * returned to the pool.
     */
    protected boolean testOnReturn = false;

    /**
     * Returns the value of the {@link #testOnReturn} property.
     *
     * @return true if objects are validated before being returned to the
     * pool
     * @see #testOnReturn
     */
    public synchronized boolean getTestOnReturn() {
        return this.testOnReturn;
    }

    /**
     * Sets the <code>testOnReturn</code> property. This property determines
     * whether or not the pool will validate objects before they are returned
     * to the pool. For a <code>true</code> value to have any effect, the
     * <code>validationQuery</code> property must be set to a non-null string.
     *
     * @param testOnReturn new value for testOnReturn property
     */
    public synchronized void setTestOnReturn(boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
        if (connectionPool != null) {
            connectionPool.setTestOnReturn(testOnReturn);
        }
    }

    /**
     * The number of milliseconds to sleep between runs of the idle object
     * evictor thread.  When non-positive, no idle object evictor thread will
     * be run.
     */
    protected long timeBetweenEvictionRunsMillis =
        GenericObjectPool.DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    /**
     * Returns the value of the {@link #timeBetweenEvictionRunsMillis}
     * property.
     *
     * @return the time (in milliseconds) between evictor runs
     * @see #timeBetweenEvictionRunsMillis
     */
    public synchronized long getTimeBetweenEvictionRunsMillis() {
        return this.timeBetweenEvictionRunsMillis;
    }

    /**
     * Sets the {@link #timeBetweenEvictionRunsMillis} property.
     *
     * @param timeBetweenEvictionRunsMillis the new time between evictor runs
     * @see #timeBetweenEvictionRunsMillis
     */
    public synchronized void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        if (connectionPool != null) {
            connectionPool.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        }
    }

    /**
     * The number of objects to examine during each run of the idle object
     * evictor thread (if any).
     */
    protected int numTestsPerEvictionRun =
        GenericObjectPool.DEFAULT_NUM_TESTS_PER_EVICTION_RUN;

    /**
     * Returns the value of the {@link #numTestsPerEvictionRun} property.
     *
     * @return the number of objects to examine during idle object evictor
     * runs
     * @see #numTestsPerEvictionRun
     */
    public synchronized int getNumTestsPerEvictionRun() {
        return this.numTestsPerEvictionRun;
    }

    /**
     * Sets the value of the {@link #numTestsPerEvictionRun} property.
     *
     * @param numTestsPerEvictionRun the new {@link #numTestsPerEvictionRun}
     * value
     * @see #numTestsPerEvictionRun
     */
    public synchronized void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.numTestsPerEvictionRun = numTestsPerEvictionRun;
        if (connectionPool != null) {
            connectionPool.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        }
    }

    /**
     * The minimum amount of time an object may sit idle in the pool before it
     * is eligible for eviction by the idle object evictor (if any).
     */
    protected long minEvictableIdleTimeMillis =
        GenericObjectPool.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * Returns the {@link #minEvictableIdleTimeMillis} property.
     *
     * @return the value of the {@link #minEvictableIdleTimeMillis} property
     * @see #minEvictableIdleTimeMillis
     */
    public synchronized long getMinEvictableIdleTimeMillis() {
        return this.minEvictableIdleTimeMillis;
    }

    /**
     * Sets the {@link #minEvictableIdleTimeMillis} property.
     *
     * @param minEvictableIdleTimeMillis the minimum amount of time an object
     * may sit idle in the pool
     * @see #minEvictableIdleTimeMillis
     */
    public synchronized void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        if (connectionPool != null) {
            connectionPool.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        }
    }

    /**
     * The minimum amount of time a connection may sit idle in the pool before
     * it is eligible for eviction by the idle object evictor, with the extra
     * condition that at least "minIdle" connections remain in the pool.
     * Note that {@code minEvictableIdleTimeMillis} takes precedence over this
     * parameter.  See {@link #getSoftMinEvictableIdleTimeMillis()}.
     */
    private long softMinEvictableIdleTimeMillis = GenericObjectPool.DEFAULT_SOFT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * Sets the minimum amount of time a connection may sit idle in the pool
     * before it is eligible for eviction by the idle object evictor, with the
     * extra condition that at least "minIdle" connections remain in the pool.
     *
     * @param softMinEvictableIdleTimeMillis minimum amount of time a
     * connection may sit idle in the pool before it is eligible for eviction,
     * assuming there are minIdle idle connections in the pool.
     * @since 1.4.1
     * @see #getSoftMinEvictableIdleTimeMillis
     */
    public synchronized void setSoftMinEvictableIdleTimeMillis(long softMinEvictableIdleTimeMillis) {
        this.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
        if (connectionPool != null) {
            connectionPool.setSoftMinEvictableIdleTimeMillis(softMinEvictableIdleTimeMillis);
        }
    }

    /**
     * <p>Returns the minimum amount of time a connection may sit idle in the
     * pool before it is eligible for eviction by the idle object evictor, with
     * the extra condition that at least "minIdle" connections remain in the
     * pool.</p>
     *
     * <p>When {@link #getMinEvictableIdleTimeMillis() miniEvictableIdleTimeMillis}
     * is set to a positive value, miniEvictableIdleTimeMillis is examined
     * first by the idle connection evictor - i.e. when idle connections are
     * visited by the evictor, idle time is first compared against
     * {@code minEvictableIdleTimeMillis} (without considering the number of idle
     * connections in the pool) and then against
     * {@code softMinEvictableIdleTimeMillis}, including the {@code minIdle},
     * constraint.</p>
     *
     * @return minimum amount of time a connection may sit idle in the pool before
     * it is eligible for eviction, assuming there are minIdle idle connections
     * in the pool
     * @since 1.4.1
     */
    public synchronized long getSoftMinEvictableIdleTimeMillis() {
        return softMinEvictableIdleTimeMillis;
    }

    /**
     * The indication of whether objects will be validated by the idle object
     * evictor (if any).  If an object fails to validate, it will be dropped
     * from the pool.
     */
    protected boolean testWhileIdle = false;

    /**
     * Returns the value of the {@link #testWhileIdle} property.
     *
     * @return true if objects examined by the idle object evictor are
     * validated
     * @see #testWhileIdle
     */
    public synchronized boolean getTestWhileIdle() {
        return this.testWhileIdle;
    }

    /**
     * Sets the <code>testWhileIdle</code> property. This property determines
     * whether or not the idle object evictor will validate connections.  For a
     * <code>true</code> value to have any effect, the
     * <code>validationQuery</code> property must be set to a non-null string.
     *
     * @param testWhileIdle new value for testWhileIdle property
     */
    public synchronized void setTestWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
        if (connectionPool != null) {
            connectionPool.setTestWhileIdle(testWhileIdle);
        }
    }

    /**
     * [Read Only] The current number of active connections that have been
     * allocated from this data source.
     *
     * @return the current number of active connections
     */
    public synchronized int getNumActive() {
        if (connectionPool != null) {
            return connectionPool.getNumActive();
        } else {
            return 0;
        }
    }


    /**
     * [Read Only] The current number of idle connections that are waiting
     * to be allocated from this data source.
     *
     * @return the current number of idle connections
     */
    public synchronized int getNumIdle() {
        if (connectionPool != null) {
            return connectionPool.getNumIdle();
        } else {
            return 0;
        }
    }

    /**
     * The connection password to be passed to our JDBC driver to establish
     * a connection.
     */
    protected volatile String password = null;

    /**
     * Returns the password passed to the JDBC driver to establish connections.
     *
     * @return the connection password
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * <p>Sets the {@link #password}.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param password new value for the password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * The connection URL to be passed to our JDBC driver to establish
     * a connection.
     */
    protected String url = null;

    /**
     * Returns the JDBC connection {@link #url} property.
     *
     * @return the {@link #url} passed to the JDBC driver to establish
     * connections
     */
    public synchronized String getUrl() {
        return this.url;
    }

    /**
     * <p>Sets the {@link #url}.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param url the new value for the JDBC connection url
     */
    public synchronized void setUrl(String url) {
        this.url = url;
    }

    /**
     * The connection username to be passed to our JDBC driver to
     * establish a connection.
     */
    protected String username = null;

    /**
     * Returns the JDBC connection {@link #username} property.
     *
     * @return the {@link #username} passed to the JDBC driver to establish
     * connections
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * <p>Sets the {@link #username}.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param username the new value for the JDBC connection username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * The SQL query that will be used to validate connections from this pool
     * before returning them to the caller.  If specified, this query
     * <strong>MUST</strong> be an SQL SELECT statement that returns at least
     * one row.
     */
    protected volatile String validationQuery = null;

    /**
     * Returns the validation query used to validate connections before
     * returning them.
     *
     * @return the SQL validation query
     * @see #validationQuery
     */
    public String getValidationQuery() {
        return this.validationQuery;
    }

    /**
     * <p>Sets the {@link #validationQuery}.</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param validationQuery the new value for the validation query
     */
    public void setValidationQuery(String validationQuery) {
        if ((validationQuery != null) && (validationQuery.trim().length() > 0)) {
            this.validationQuery = validationQuery;
        } else {
            this.validationQuery = null;
        }
    }

    /**
     * Timeout in seconds before connection validation queries fail.
     *
     * @since 1.3
     */
    protected volatile int validationQueryTimeout = -1;

    /**
     * Returns the validation query timeout.
     *
     * @return the timeout in seconds before connection validation queries fail.
     * @since 1.3
     */
    public int getValidationQueryTimeout() {
        return validationQueryTimeout;
    }

    /**
     * Sets the validation query timeout, the amount of time, in seconds, that
     * connection validation will wait for a response from the database when
     * executing a validation query.  Use a value less than or equal to 0 for
     * no timeout.
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param timeout new validation query timeout value in seconds
     * @since 1.3
     */
    public void setValidationQueryTimeout(int timeout) {
        this.validationQueryTimeout = timeout;
    }

    /**
     * These SQL statements run once after a Connection is created.
     * <p>
     * This property can be used for example to run ALTER SESSION SET
     * NLS_SORT=XCYECH in an Oracle Database only once after connection
     * creation.
     * </p>
     *
     * @since 1.3
     */
    protected volatile List<String> connectionInitSqls;

    /**
     * Returns the list of SQL statements executed when a physical connection
     * is first created. Returns an empty list if there are no initialization
     * statements configured.
     *
     * @return initialization SQL statements
     * @since 1.3
     */
    public Collection<String> getConnectionInitSqls() {
        Collection<String> result = connectionInitSqls;
        if (result == null) {
            return Collections.emptyList();
        }
        return result;
    }

    /**
     * Sets the list of SQL statements to be executed when a physical
     * connection is first created.
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param connectionInitSqls Collection of SQL statements to execute
     * on connection creation
     */
    public void setConnectionInitSqls(Collection<String> connectionInitSqls) {
        if ((connectionInitSqls != null) && (connectionInitSqls.size() > 0)) {
            ArrayList<String> newVal = null;
            for (Iterator<String> iterator = connectionInitSqls.iterator();
            iterator.hasNext();) {
                String s = iterator.next();
                if (s != null) {
                    if (s.trim().length() > 0) {
                        if (newVal == null) {
                            newVal = new ArrayList<String>();
                        }
                        newVal.add(s);
                    }
                }
            }
            this.connectionInitSqls = newVal;
        } else {
            this.connectionInitSqls = null;
        }
    }


    /**
     * Controls access to the underlying connection.
     */
    private boolean accessToUnderlyingConnectionAllowed = false;

    /**
     * Returns the value of the accessToUnderlyingConnectionAllowed property.
     *
     * @return true if access to the underlying connection is allowed, false
     * otherwise.
     */
    public synchronized boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    /**
     * <p>Sets the value of the accessToUnderlyingConnectionAllowed property.
     * It controls if the PoolGuard allows access to the underlying connection.
     * (Default: false)</p>
     * <p>
     * Note: this method currently has no effect once the pool has been
     * initialized.  The pool is initialized the first time one of the
     * following methods is invoked: <code>getConnection, setLogwriter,
     * setLoginTimeout, getLoginTimeout, getLogWriter.</code></p>
     *
     * @param allow Access to the underlying connection is granted when true.
     */
    public synchronized void setAccessToUnderlyingConnectionAllowed(boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * The object pool that internally manages our connections.
     */
    protected volatile GenericObjectPool<PoolableConnection> connectionPool = null;

    /**
     * The connection properties that will be sent to our JDBC driver when
     * establishing new connections.  <strong>NOTE</strong> - The "user" and
     * "password" properties will be passed explicitly, so they do not need
     * to be included here.
     */
    protected Properties connectionProperties = new Properties();

    /**
     * The data source we will use to manage connections.  This object should
     * be acquired <strong>ONLY</strong> by calls to the
     * <code>createDataSource()</code> method.
     */
    protected volatile DataSource dataSource = null;

    /**
     * The PrintWriter to which log messages should be directed.
     */
    protected PrintWriter logWriter = new PrintWriter(System.out);


    // ----------------------------------------------------- DataSource Methods


    /**
     * Create (if necessary) and return a connection to the database.
     *
     * @throws SQLException if a database access error occurs
     * @return a database connection
     */
    @Override
    public Connection getConnection() throws SQLException {
        return createDataSource().getConnection();
    }


    /**
     * <strong>BasicDataSource does NOT support this method. </strong>
     *
     * @param user Database user on whose behalf the Connection
     *   is being made
     * @param pass The database user's password
     *
     * @throws UnsupportedOperationException
     * @throws SQLException if a database access error occurs
     * @return nothing - always throws UnsupportedOperationException
     */
    @Override
    public Connection getConnection(String user, String pass) throws SQLException {
        // This method isn't supported by the PoolingDataSource returned by
        // the createDataSource
        throw new UnsupportedOperationException("Not supported by BasicDataSource");
        // return createDataSource().getConnection(username, password);
    }


    /**
     * <strong>BasicDataSource does NOT support this method. </strong>
     *
     * <p>Returns the login timeout (in seconds) for connecting to the database.
     * </p>
     * <p>Calls {@link #createDataSource()}, so has the side effect
     * of initializing the connection pool.</p>
     *
     * @throws SQLException if a database access error occurs
     * @throws UnsupportedOperationException If the DataSource implementation
     *   does not support the login timeout feature.
     * @return login timeout in seconds
     */
    @Override
    public int getLoginTimeout() throws SQLException {
        // This method isn't supported by the PoolingDataSource returned by
        // the createDataSource
        throw new UnsupportedOperationException("Not supported by BasicDataSource");
        //return createDataSource().getLoginTimeout();
    }


    /**
     * <p>Returns the log writer being used by this data source.</p>
     * <p>
     * Calls {@link #createDataSource()}, so has the side effect
     * of initializing the connection pool.</p>
     *
     * @throws SQLException if a database access error occurs
     * @return log writer in use
     */
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return createDataSource().getLogWriter();
    }


    /**
     * <strong>BasicDataSource does NOT support this method. </strong>
     *
     * <p>Set the login timeout (in seconds) for connecting to the
     * database.</p>
     * <p>
     * Calls {@link #createDataSource()}, so has the side effect
     * of initializing the connection pool.</p>
     *
     * @param loginTimeout The new login timeout, or zero for no timeout
     * @throws UnsupportedOperationException If the DataSource implementation
     *   does not support the login timeout feature.
     * @throws SQLException if a database access error occurs
     */
    @Override
    public void setLoginTimeout(int loginTimeout) throws SQLException {
        // This method isn't supported by the PoolingDataSource returned by
        // the createDataSource
        throw new UnsupportedOperationException("Not supported by BasicDataSource");
        //createDataSource().setLoginTimeout(loginTimeout);
    }


    /**
     * <p>Sets the log writer being used by this data source.</p>
     * <p>
     * Calls {@link #createDataSource()}, so has the side effect
     * of initializing the connection pool.</p>
     *
     * @param logWriter The new log writer
     * @throws SQLException if a database access error occurs
     */
    @Override
    public void setLogWriter(PrintWriter logWriter) throws SQLException {
        createDataSource().setLogWriter(logWriter);
        this.logWriter = logWriter;
    }

    private AbandonedConfig abandonedConfig;

    /**
     * <p>Flag to remove abandoned connections if they exceed the
     * removeAbandonedTimout.</p>
     *
     * <p>The default value is false.<p>
     *
     * <p>If set to true a connection is considered abandoned and eligible
     * for removal if it has not been used for more than
     * {@link #getRemoveAbandonedTimeout() removeAbandonedTimeout} seconds.</p>
     *
     * <p>Abandoned connections are identified and removed when
     * {@link #getConnection()} is invoked and the following conditions hold
     * <ul><li>{@link #getRemoveAbandoned()} = true </li>
     *     <li>{@link #getNumActive()} &gt; {@link #getMaxActive()} - 3 </li>
     *     <li>{@link #getNumIdle()} &lt; 2 </li></ul>
     *
     * @see #getRemoveAbandonedTimeout()
     */
    public boolean getRemoveAbandoned() {
        if (abandonedConfig != null) {
            return abandonedConfig.getRemoveAbandoned();
        }
        return false;
    }

    /**
     * <p>Flag to remove abandoned connections if they exceed the
     * removeAbandonedTimeout.</p>
     *
     * <p>If set to true a connection is considered abandoned and eligible
     * for removal if it has been idle longer than the
     * {@link #getRemoveAbandoned() removeAbandonedTimeout}.</p>
     *
     * <p>Setting this to true can recover db connections from poorly written
     * applications which fail to close a connection.</p>
     *
     * @param removeAbandoned true means abandoned connections will be
     *   removed
     * @see #getRemoveAbandoned()
     */
    public void setRemoveAbandoned(boolean removeAbandoned) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setRemoveAbandoned(removeAbandoned);
    }

    /**
     * <p>Timeout in seconds before an abandoned connection can be removed.</p>
     *
     * <p>Creating a Statement, PreparedStatement or CallableStatement or using
     * one of these to execute a query (using one of the execute methods)
     * resets the lastUsed property of the parent connection.</p>
     *
     * <p>Abandoned connection cleanup happens when
     * <ul>
     * <li>{@link #getRemoveAbandoned() removeAbandoned} == true</li>
     * <li>{@link #getNumIdle() numIdle} &lt; 2</li>
     * <li>{@link #getNumActive() numActive} &gt; {@link #getMaxActive() maxActive} - 3</li>
     * </ul>
     *
     * <p>The default value is 300 seconds.</p>
     */
    public int getRemoveAbandonedTimeout() {
        if (abandonedConfig != null) {
            return abandonedConfig.getRemoveAbandonedTimeout();
        }
        return 300;
    }

    /**
     * <p>Sets the timeout in seconds before an abandoned connection can be
     * removed.</p>
     *
     * <p>Setting this property has no effect if
     * {@link #getRemoveAbandoned() removeAbandoned} is false.</p>
     *
     * @param removeAbandonedTimeout new abandoned timeout in seconds
     * @see #getRemoveAbandonedTimeout()
     * @see #getRemoveAbandoned()
     */
    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setRemoveAbandonedTimeout(removeAbandonedTimeout);
    }

    /**
     * <p>Flag to log stack traces for application code which abandoned
     * a Statement or Connection.
     * </p>
     * <p>Defaults to false.
     * </p>
     * <p>Logging of abandoned Statements and Connections adds overhead
     * for every Connection open or new Statement because a stack
     * trace has to be generated. </p>
     */
    public boolean getLogAbandoned() {
        if (abandonedConfig != null) {
            return abandonedConfig.getLogAbandoned();
        }
        return false;
    }

    /**
     * @param logAbandoned new logAbandoned property value
     */
    public void setLogAbandoned(boolean logAbandoned) {
        if (abandonedConfig == null) {
            abandonedConfig = new AbandonedConfig();
        }
        abandonedConfig.setLogAbandoned(logAbandoned);
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Add a custom connection property to the set that will be passed to our
     * JDBC driver. This <strong>MUST</strong> be called before the first
     * connection is retrieved (along with all the other configuration
     * property setters). Calls to this method after the connection pool
     * has been initialized have no effect.
     *
     * @param name Name of the custom connection property
     * @param value Value of the custom connection property
     */
    public void addConnectionProperty(String name, String value) {
        connectionProperties.put(name, value);
    }

    /**
     * Remove a custom connection property.
     *
     * @param name Name of the custom connection property to remove
     * @see #addConnectionProperty(String, String)
     */
    public void removeConnectionProperty(String name) {
        connectionProperties.remove(name);
    }

    /**
     * Sets the connection properties passed to driver.connect(...).
     *
     * Format of the string must be [propertyName=property;]*
     *
     * NOTE - The "user" and "password" properties will be added
     * explicitly, so they do not need to be included here.
     *
     * @param connectionProperties the connection properties used to
     * create new connections
     */
    public void setConnectionProperties(String connectionProperties) {
        if (connectionProperties == null) throw new NullPointerException("connectionProperties is null");

        String[] entries = connectionProperties.split(";");
        Properties properties = new Properties();
        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i];
            if (entry.length() > 0) {
                int index = entry.indexOf('=');
                if (index > 0) {
                    String name = entry.substring(0, index);
                    String value = entry.substring(index + 1);
                    properties.setProperty(name, value);
                } else {
                    // no value is empty string which is how java.util.Properties works
                    properties.setProperty(entry, "");
                }
            }
        }
        this.connectionProperties = properties;
    }

    protected boolean closed;

    /**
     * <p>Closes and releases all idle connections that are currently stored in the connection pool
     * associated with this data source.</p>
     *
     * <p>Connections that are checked out to clients when this method is invoked are not affected.
     * When client applications subsequently invoke {@link Connection#close()} to return
     * these connections to the pool, the underlying JDBC connections are closed.</p>
     *
     * <p>Attempts to acquire connections using {@link #getConnection()} after this method has been
     * invoked result in SQLExceptions.<p>
     *
     * <p>This method is idempotent - i.e., closing an already closed BasicDataSource has no effect
     * and does not generate exceptions.</p>
     *
     * @throws SQLException if an error occurs closing idle connections
     */
    public synchronized void close() throws SQLException {
        closed = true;
        GenericObjectPool<PoolableConnection> oldpool = connectionPool;
        connectionPool = null;
        dataSource = null;
        try {
            if (oldpool != null) {
                oldpool.close();
            }
        } catch(SQLException e) {
            throw e;
        } catch(RuntimeException e) {
            throw e;
        } catch(Exception e) {
            throw new SQLException("Cannot close connection pool", e);
        }
    }

    /**
     * If true, this data source is closed and no more connections can be retrieved from this datasource.
     * @return true, if the data source is closed; false otherwise
     */
    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("BasicDataSource is not a wrapper.");
    }


    /* JDBC_4_1_ANT_KEY_BEGIN */
    // No @Override else it won't compile with Java 6
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
    /* JDBC_4_1_ANT_KEY_END */

    // ------------------------------------------------------ Protected Methods


    /**
     * <p>Create (if necessary) and return the internal data source we are
     * using to manage our connections.</p>
     *
     * <p><strong>IMPLEMENTATION NOTE</strong> - It is tempting to use the
     * "double checked locking" idiom in an attempt to avoid synchronizing
     * on every single call to this method.  However, this idiom fails to
     * work correctly in the face of some optimizations that are legal for
     * a JVM to perform.</p>
     *
     * @throws SQLException if the object pool cannot be created.
     */
    protected synchronized DataSource createDataSource()
        throws SQLException {
        if (closed) {
            throw new SQLException("Data source is closed");
        }

        // Return the pool if we have already created it
        if (dataSource != null) {
            return (dataSource);
        }

        // create factory which returns raw physical connections
        ConnectionFactory driverConnectionFactory = createConnectionFactory();

        // create a pool for our connections
        createConnectionPool();

        // Set up statement pool, if desired
        GenericKeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> statementPoolFactory = null;
        if (isPoolPreparedStatements()) {
            statementPoolFactory = new GenericKeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement>(null,
                        -1, // unlimited maxActive (per key)
                        GenericKeyedObjectPool.WHEN_EXHAUSTED_FAIL,
                        0, // maxWait
                        1, // maxIdle (per key)
                        maxOpenPreparedStatements);
        }

        // Set up the poolable connection factory
        boolean success = false;
        try {
            createPoolableConnectionFactory(driverConnectionFactory,
                    statementPoolFactory, abandonedConfig);
            success = true;
        } catch (SQLException se) {
            throw se;
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new SQLException("Error creating connection factory", ex);
        } finally {
            if (!success) {
                closeConnectionPool();
            }
        }

        // Create the pooling data source to manage connections
        success = false;
        try {
            createDataSourceInstance();
            success = true;
        } catch (SQLException se) {
            throw se;
        } catch (RuntimeException rte) {
            throw rte;
        } catch (Exception ex) {
            throw new SQLException("Error creating datasource", ex);
        } finally {
            if (!success) {
                closeConnectionPool();
            }
        }

        // If initialSize > 0, preload the pool
        try {
            for (int i = 0 ; i < initialSize ; i++) {
                connectionPool.addObject();
            }
        } catch (Exception e) {
            closeConnectionPool();
            throw new SQLException("Error preloading the connection pool", e);
        }

        // If timeBetweenEvictionRunsMillis > 0, start the pool's evictor task
        startPoolMaintenance();

        return dataSource;
    }

    /**
     * Creates a JDBC connection factory for this datasource.  The JDBC driver
     * is loaded using the following algorithm:
     * <ol>
     * <li>If {@link #driverClassName} is specified that class is loaded using
     * the {@link ClassLoader} of this class or, if {@link #driverClassLoader}
     * is set, {@link #driverClassName} is loaded with the specified
     * {@link ClassLoader}.</li>
     * <li>If {@link #driverClassName} is specified and the previous attempt
     * fails, the class is loaded using the context class loader of the current
     * thread.</li>
     * <li>If a driver still isn't loaded one is loaded via the
     * {@link DriverManager} using the specified {@link #url}.
     * </ol>
     * This method exists so subclasses can replace the implementation class.
     */
    protected ConnectionFactory createConnectionFactory() throws SQLException {
        // Load the JDBC driver class
        Class<?> driverFromCCL = null;
        if (driverClassName != null) {
            try {
                try {
                    if (driverClassLoader == null) {
                        driverFromCCL = Class.forName(driverClassName);
                    } else {
                        driverFromCCL = Class.forName(
                                driverClassName, true, driverClassLoader);
                    }
                } catch (ClassNotFoundException cnfe) {
                    driverFromCCL = Thread.currentThread(
                            ).getContextClassLoader().loadClass(
                                    driverClassName);
                }
            } catch (Throwable t) {
                String message = "Cannot load JDBC driver class '" +
                    driverClassName + "'";
                logWriter.println(message);
                t.printStackTrace(logWriter);
                throw new SQLException(message, t);
            }
        }

        // Create a JDBC driver instance
        Driver driver = null;
        try {
            if (driverFromCCL == null) {
                driver = DriverManager.getDriver(url);
            } else {
                // Usage of DriverManager is not possible, as it does not
                // respect the ContextClassLoader
                driver = (Driver) driverFromCCL.newInstance();
                if (!driver.acceptsURL(url)) {
                    throw new SQLException("No suitable driver", "08001");
                }
            }
        } catch (Throwable t) {
            String message = "Cannot create JDBC driver of class '" +
                (driverClassName != null ? driverClassName : "") +
                "' for connect URL '" + url + "'";
            logWriter.println(message);
            t.printStackTrace(logWriter);
            throw new SQLException(message, t);
        }

        // Can't test without a validationQuery
        if (validationQuery == null) {
            setTestOnBorrow(false);
            setTestOnReturn(false);
            setTestWhileIdle(false);
        }

        // Set up the driver connection factory we will use
        String user = username;
        if (user != null) {
            connectionProperties.put("user", user);
        } else {
            log("DBCP DataSource configured without a 'username'");
        }

        String pwd = password;
        if (pwd != null) {
            connectionProperties.put("password", pwd);
        } else {
            log("DBCP DataSource configured without a 'password'");
        }

        ConnectionFactory driverConnectionFactory = new DriverConnectionFactory(driver, url, connectionProperties);
        return driverConnectionFactory;
    }

    /**
     * Creates a connection pool for this datasource.  This method only exists
     * so subclasses can replace the implementation class.
     *
     * This implementation configures all pool properties other than
     * timeBetweenEvictionRunsMillis.  Setting that property is deferred to
     * {@link #startPoolMaintenance()}, since setting timeBetweenEvictionRunsMillis
     * to a positive value causes {@link GenericObjectPool}'s eviction timer
     * to be started.
     */
    protected void createConnectionPool() {
        // Create an object pool to contain our active connections
        GenericObjectPool<PoolableConnection> gop;
        if ((abandonedConfig != null) && (abandonedConfig.getRemoveAbandoned())) {
            gop = new AbandonedObjectPool<PoolableConnection>(null,abandonedConfig);
        }
        else {
            gop = new GenericObjectPool<PoolableConnection>();
        }
        gop.setMaxActive(maxActive);
        gop.setMaxIdle(maxIdle);
        gop.setMinIdle(minIdle);
        gop.setMaxWait(maxWait);
        gop.setTestOnBorrow(testOnBorrow);
        gop.setTestOnReturn(testOnReturn);
        gop.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        gop.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        gop.setTestWhileIdle(testWhileIdle);
        gop.setLifo(lifo);
        connectionPool = gop;
    }

    /**
     * Closes the connection pool, silently swallowing any exception that occurs.
     */
    private void closeConnectionPool() {
        GenericObjectPool<PoolableConnection> oldpool = connectionPool;
        connectionPool = null;
        try {
            if (oldpool != null) {
                oldpool.close();
            }
        } catch(Exception e) {
            // Do not propagate
        }
    }

    /**
     * Starts the connection pool maintenance task, if configured.
     */
    protected void startPoolMaintenance() {
        if (connectionPool != null && timeBetweenEvictionRunsMillis > 0) {
            connectionPool.setTimeBetweenEvictionRunsMillis(
                    timeBetweenEvictionRunsMillis);
        }
    }

    /**
     * Creates the actual data source instance.  This method only exists so
     * that subclasses can replace the implementation class.
     *
     * @throws SQLException if unable to create a datasource instance
     */
    protected void createDataSourceInstance() throws SQLException {
        PoolingDataSource<PoolableConnection> pds =
                new PoolingDataSource<PoolableConnection>(connectionPool);
        pds.setAccessToUnderlyingConnectionAllowed(isAccessToUnderlyingConnectionAllowed());
        pds.setLogWriter(logWriter);
        dataSource = pds;
    }

    /**
     * Creates the PoolableConnectionFactory and attaches it to the connection pool.  This method only exists
     * so subclasses can replace the default implementation.
     *
     * @param driverConnectionFactory JDBC connection factory
     * @param statementPoolFactory statement pool factory (null if statement pooling is turned off)
     * @param configuration abandoned connection tracking configuration (null if no tracking)
     * @throws SQLException if an error occurs creating the PoolableConnectionFactory
     */
    protected void createPoolableConnectionFactory(ConnectionFactory driverConnectionFactory,
            KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> statementPoolFactory,
            AbandonedConfig configuration) throws SQLException {
        PoolableConnectionFactory connectionFactory = null;
        try {
            connectionFactory =
                new PoolableConnectionFactory(driverConnectionFactory,
                                              connectionPool,
                                              statementPoolFactory,
                                              validationQuery,
                                              validationQueryTimeout,
                                              connectionInitSqls,
                                              defaultReadOnly,
                                              defaultAutoCommit,
                                              defaultTransactionIsolation,
                                              defaultCatalog,
                                              configuration);
            validateConnectionFactory(connectionFactory);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Cannot create PoolableConnectionFactory (" + e.getMessage() + ")", e);
        }
    }

    protected static void validateConnectionFactory(PoolableConnectionFactory connectionFactory) throws Exception {
        PoolableConnection conn = null;
        try {
            conn = connectionFactory.makeObject();
            connectionFactory.activateObject(conn);
            connectionFactory.validateConnection(conn);
            connectionFactory.passivateObject(conn);
        }
        finally {
            connectionFactory.destroyObject(conn);
        }
    }

    protected void log(String message) {
        if (logWriter != null) {
            logWriter.println(message);
        }
    }
}

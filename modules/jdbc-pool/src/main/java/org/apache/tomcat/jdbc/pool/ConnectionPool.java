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

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.XAConnection;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Implementation of simple connection pool. The ConnectionPool uses a {@link PoolProperties} object for storing all the
 * meta information about the connection pool. As the underlying implementation, the connection pool uses
 * {@link java.util.concurrent.BlockingQueue} to store active and idle connections. A custom implementation of a fair
 * {@link FairBlockingQueue} blocking queue is provided with the connection pool itself.
 */
public class ConnectionPool {

    /**
     * Default domain for objects registering with an mbean server
     */
    public static final String POOL_JMX_DOMAIN = "tomcat.jdbc";
    /**
     * Prefix type for JMX registration
     */
    public static final String POOL_JMX_TYPE_PREFIX = POOL_JMX_DOMAIN + ":type=";

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(ConnectionPool.class);

    /**
     * The JDBC 4.3 <code>beginRequest()</code> hint, looked up by reflection because this module compiles against Java
     * 8, whose {@link java.sql.Connection} does not declare it; on a runtime that predates JDBC 4.3 the lookup yields
     * null and the hint is not sent.
     */
    private static final Method BEGIN_REQUEST = getRequestHintMethod("beginRequest");

    /**
     * The counterpart of {@link #BEGIN_REQUEST}, sent when the application returns the connection to the pool.
     */
    private static final Method END_REQUEST = getRequestHintMethod("endRequest");

    /**
     * Carries the size of the pool, instead of relying on a queue implementation that usually iterates over to get an
     * exact count
     */
    private AtomicInteger size = new AtomicInteger(0);

    /**
     * All the information about the connection pool These are the properties the pool got instantiated with
     */
    private PoolConfiguration poolProperties;

    /**
     * Contains all the connections that are in use TODO - this shouldn't be a blocking queue, simply a list to hold our
     * objects
     */
    private BlockingQueue<PooledConnection> busy;

    /**
     * Contains all the idle connections
     */
    private BlockingQueue<PooledConnection> idle;

    /**
     * The thread that is responsible for checking abandoned and idle threads
     */
    private PoolCleaner poolCleaner;

    /**
     * Pool closed flag
     */
    private volatile boolean closed = false;

    /**
     * Since newProxyInstance performs the same operation, over and over again, it is much more optimized if we simply
     * store the constructor ourselves. One constructor is cached per proxy kind (with and without the XAConnection
     * interface), so that a pool whose backing data source changes kind at runtime keeps proxying each connection
     * correctly.
     */
    private volatile Constructor<?> proxyClassConstructor;

    /**
     * Cached constructor for proxies that also implement XAConnection.
     */
    private volatile Constructor<?> xaProxyClassConstructor;

    /**
     * Executor service used to cancel Futures
     */
    private ThreadPoolExecutor cancellator = new ThreadPoolExecutor(0, 1, 1000, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());

    /**
     * reference to the JMX mbean. close() gives it up without holding any pool lock while other threads still notify
     * through it, so the field is volatile and every user reads it at most once, into a local.
     */
    protected volatile org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmxPool = null;

    /**
     * counter to track how many threads are waiting for a connection
     */
    private AtomicInteger waitcount = new AtomicInteger(0);

    private AtomicLong poolVersion = new AtomicLong(Long.MIN_VALUE);

    /**
     * The counters for statistics of the pool.
     */
    private final AtomicLong borrowedCount = new AtomicLong(0);
    private final AtomicLong returnedCount = new AtomicLong(0);
    private final AtomicLong createdCount = new AtomicLong(0);
    private final AtomicLong releasedCount = new AtomicLong(0);
    private final AtomicLong reconnectedCount = new AtomicLong(0);
    private final AtomicLong removeAbandonedCount = new AtomicLong(0);
    private final AtomicLong releasedIdleCount = new AtomicLong(0);

    /**
     * Instantiate a connection pool. This will create connections if initialSize is larger than 0. The
     * {@link PoolProperties} should not be reused for another connection pool.
     *
     * @param prop PoolProperties - all the properties for this connection pool
     *
     * @throws SQLException Pool initialization error
     */
    public ConnectionPool(PoolConfiguration prop) throws SQLException {
        // Setup quick access variables and pools
        init(prop);
    }


    /**
     * Retrieves a Connection future. If a connection is not available, one can block using future.get() until a
     * connection has become available. If a connection is not retrieved, the Future must be cancelled in order for the
     * connection to be returned to the pool. Closing the pool cancels a future that has not received a connection yet,
     * so a thread blocked in {@link Future#get()} observes a {@link java.util.concurrent.CancellationException} instead
     * of waiting for a connection that can no longer be handed out.
     *
     * @return a Future containing a reference to the connection or the future connection
     *
     * @throws SQLException Cannot use asynchronous connect
     */
    public Future<Connection> getConnectionAsync() throws SQLException {
        try {
            PooledConnection pc = borrowConnection(0, null, null);
            if (pc != null) {
                return new ConnectionFuture(pc);
            }
        } catch (SQLException x) {
            // JDBC exceptions may carry no message at all; a missing
            // "NoWait" marker must be assumed for those, so that the
            // original failure reaches the caller instead of a
            // NullPointerException from dereferencing the message
            String msg = x.getMessage();
            if (msg == null || msg.indexOf("NoWait") < 0) {
                throw x;
            }
        }
        // We can only retrieve a future if the underlying queue supports it.
        if (idle instanceof FairBlockingQueue<?>) {
            Future<PooledConnection> pcf = ((FairBlockingQueue<PooledConnection>) idle).pollAsync();
            if (isClosed() && pcf.isCancelled()) {
                // The pool was closed between the borrow attempt above and the
                // registration of the waiter: close() fails every waiter the
                // queue holds and a later poll cannot register one, so this
                // future will never hand out a connection. Reject the borrow the
                // same way a synchronous borrow on a closed pool does, rather than
                // handing out a future that only ever fails with a cancellation.
                throw new SQLException("Connection pool closed.");
            }
            return new ConnectionFuture(pcf);
        } else {
            throw new SQLException(
                    "Connection pool is misconfigured, doesn't support async retrieval. Set the 'fair' property to 'true'");
        }
    }

    /**
     * Borrows a connection from the pool. If a connection is available (in the idle queue) or the pool has not reached
     * {@link PoolProperties#maxActive maxActive} connections a connection is returned immediately. If no connection is
     * available, the pool will attempt to fetch a connection for {@link PoolProperties#maxWait maxWait} milliseconds.
     *
     * @return Connection - a java.sql.Connection/javax.sql.PooledConnection reflection proxy, wrapping the underlying
     *             object.
     *
     * @throws SQLException - if the wait times out or a failure occurs creating a connection
     */
    public Connection getConnection() throws SQLException {
        // Check out a connection
        PooledConnection con = borrowConnection(-1, null, null);
        return setupConnection(con);
    }


    /**
     * Borrows a connection from the pool. If a connection is available (in the idle queue) or the pool has not reached
     * {@link PoolProperties#maxActive maxActive} connections a connection is returned immediately. If no connection is
     * available, the pool will attempt to fetch a connection for {@link PoolProperties#maxWait maxWait} milliseconds.
     *
     * @param username The user name to use for the connection
     * @param password The password for the connection
     *
     * @return Connection - a java.sql.Connection/javax.sql.PooledConnection reflection proxy, wrapping the underlying
     *             object.
     *
     * @throws SQLException - if the wait times out or a failure occurs creating a connection
     */
    public Connection getConnection(String username, String password) throws SQLException {
        // Check out a connection
        PooledConnection con = borrowConnection(-1, username, password);
        return setupConnection(con);
    }

    /**
     * Returns the name of this pool
     *
     * @return String - the name of the pool
     */
    public String getName() {
        return getPoolProperties().getPoolName();
    }

    /**
     * Return the number of threads waiting for a connection
     *
     * @return number of threads waiting for a connection
     */
    public int getWaitCount() {
        return waitcount.get();
    }

    /**
     * Returns the pool properties associated with this connection pool
     *
     * @return PoolProperties
     */
    public PoolConfiguration getPoolProperties() {
        return this.poolProperties;
    }

    /**
     * Returns the total size of this pool, this includes both busy and idle connections
     *
     * @return int - number of established connections to the database
     */
    public int getSize() {
        return size.get();
    }

    /**
     * Returns the number of connections that are in use
     *
     * @return int - number of established connections that are being used by the application
     */
    public int getActive() {
        return busy.size();
    }

    /**
     * Returns the number of idle connections
     *
     * @return int - number of established connections not being used
     */
    public int getIdle() {
        return idle.size();
    }

    /**
     * Returns true if {@link #close close} has been called, and the connection pool is unusable
     *
     * @return boolean
     */
    public boolean isClosed() {
        return this.closed;
    }


    /**
     * configures a pooled connection as a proxy. This Proxy implements {@link java.sql.Connection} and
     * {@link javax.sql.PooledConnection} interfaces. All calls on {@link java.sql.Connection} methods will be
     * propagated down to the actual JDBC connection except for the {@link java.sql.Connection#close()} method.
     *
     * @param con a {@link PooledConnection} to wrap in a Proxy
     *
     * @return a {@link java.sql.Connection} object wrapping a pooled connection.
     *
     * @throws SQLException if an interceptor can't be configured, if the proxy can't be instantiated
     */
    protected Connection setupConnection(PooledConnection con) throws SQLException {
        // The connection is being handed to a borrower: a borrow is counted
        // here rather than where it was claimed, as that is the one point all
        // hand-offs pass - the synchronous borrows, the async borrow that
        // takes an idle connection, the configuration of a connection a queue
        // handed to an async poll, and the cleanup that retrieves a cancelled
        // poll's connection to return it - each exactly once, while a claim
        // that ends in a throw never reaches a borrower at all. A setup that
        // fails gives the connection back through abandonSetup(), which counts
        // the return, so the borrow is counted on the way in to match. The
        // pool's own initialization is the only path that takes connections
        // out of the creation route without a borrower: it returns its
        // preloaded connections directly and never sets one up here, so it
        // stays out of the count.
        borrowedCount.incrementAndGet();
        // Fetch previously cached interceptor proxy - one per connection
        JdbcInterceptor handler = con.getHandler();
        if (handler == null) {
            // The registration reads the pool's own JMX object itself and does
            // nothing once close() has given it up; it is not gated on jmxPool
            // here because checking the field and then calling into the
            // connection would let close() take it away between the two, and
            // the resulting failure must not strand the connection - it is
            // already in busy, so a throw that skips abandonSetup() costs a
            // physical connection and a maxActive slot until the pool restarts.
            try {
                con.createMBean();
            } catch (Exception x) {
                SQLException sx = new SQLException("Unable to register the pooled connection MBean.");
                sx.initCause(x);
                // Nothing has been done to the connection, it can go back to the pool
                abandonSetup(con, sx, false);
                throw sx;
            }
            // Build the proxy handler
            handler = new ProxyConnection(this, con, getPoolProperties().isUseEquals());
            // Set up the interceptor chain
            PoolProperties.InterceptorDefinition[] proxies = getPoolProperties().getJdbcInterceptorsAsArray();
            for (int i = proxies.length - 1; i >= 0; i--) {
                try {
                    // Create a new instance
                    JdbcInterceptor interceptor = proxies[i].getInterceptorClass().getConstructor().newInstance();
                    // Configure properties
                    interceptor.setProperties(proxies[i].getProperties());
                    // Setup the chain
                    interceptor.setNext(handler);
                    // Configure the last one to be held by the connection
                    handler = interceptor;
                } catch (Exception x) {
                    SQLException sx = new SQLException("Unable to instantiate interceptor chain.");
                    sx.initCause(x);
                    // Nothing has been done to the connection, it can go back to the pool
                    abandonSetup(con, sx, false);
                    throw sx;
                }
            }
            // Cache handler for the next iteration
            con.setHandler(handler);
        }
        // Reset the chain in the order the interceptors are declared, for new and cached handlers alike
        JdbcInterceptor next = handler;
        try {
            while (next != null) {
                next.reset(this, con);
                next = next.getNext();
            }
        } catch (Exception x) {
            SQLException sx = new SQLException(
                    "Unable to reset interceptor [" + (next != null ? next.getClass().getName() : "unknown") + "].");
            sx.initCause(x);
            // The state of the connection is unknown, discard it so its partially reset chain is dropped
            abandonSetup(con, sx, true);
            throw sx;
        }
        // Setup statement proxy
        // The statement facade is always used; statement and result set
        // handles a borrower keeps after returning the connection must stay
        // inert, as the interceptor chain below is cached on the pooled
        // connection and re-armed for the next borrower. (The
        // useStatementFacade property that used to switch this off is
        // deprecated and no longer has any effect.)
        handler = new StatementFacade(handler);
        try {
            Constructor<?> proxyConstructor = getProxyConstructor(con.getXAConnection() != null);
            // Create the proxy
            // TODO possible optimization, keep track if this connection was returned properly, and don't generate a new
            // handle and facade
            // The disposable connection facade is always used; a handle that
            // its borrower has closed must stay inert, as the interceptor
            // chain below is cached on the pooled connection and re-armed
            // for the next borrower. (The useDisposableConnectionFacade
            // property that used to switch this off is deprecated and no
            // longer has any effect.)
            Connection connection = (Connection) proxyConstructor
                    .newInstance(new Object[] { new DisposableConnectionFacade(handler) });
            // The handle exists and the borrow is about to start: send the
            // JDBC 4.3 hint that opens the request, so a driver that
            // optimises per-request session handling knows the unit of work
            // begins here. The matching endRequest() is sent at the return,
            // in returnConnection().
            invokeRequestHint(BEGIN_REQUEST, con.getConnection());
            // Return the connection
            return connection;
        } catch (Exception x) {
            SQLException s = new SQLException();
            s.initCause(x);
            abandonSetup(con, s, false);
            throw s;
        }

    }

    /**
     * Gives up a connection that was borrowed but could not be set up, so that it does not stay borrowed forever. Any
     * failure to do so is added to the exception that is about to be thrown rather than hiding it.
     *
     * @param con     the borrowed connection
     * @param cause   the exception that is about to be thrown to the caller
     * @param discard {@code true} if the connection must be closed, {@code false} if it can go back to the pool
     */
    private void abandonSetup(PooledConnection con, SQLException cause, boolean discard) {
        try {
            if (discard) {
                con.setDiscarded(true);
            }
            returnConnection(con);
        } catch (Exception y) {
            cause.addSuppressed(y);
        }
    }

    /**
     * Looks up one of the JDBC 4.3 request boundary hint methods on {@link java.sql.Connection} by name, as they are
     * absent from the Java 8 API this module compiles against.
     *
     * @param methodName the name of the hint method
     *
     * @return the method, or <code>null</code> on a runtime that predates JDBC 4.3
     */
    private static Method getRequestHintMethod(String methodName) {
        try {
            return Connection.class.getMethod(methodName);
        } catch (NoSuchMethodException x) {
            // Java 8 and earlier: there are no request boundary hints to send
            return null;
        }
    }

    /**
     * Sends one of the JDBC 4.3 request boundary hints to a physical connection. The hint is advisory - the
     * specification lets the driver treat it as a no-op - so a driver that refuses one does not fail the borrow or the
     * return around it; the refusal is logged with the driver's own cause.
     *
     * @param hint       one of the hint methods, or <code>null</code> when running where they do not exist
     * @param connection the physical connection to hint
     */
    private static void invokeRequestHint(Method hint, Connection connection) {
        if (hint == null) {
            return;
        }
        try {
            hint.invoke(connection);
        } catch (InvocationTargetException x) {
            log.warn("The request boundary hint [" + hint.getName() + "] was refused by the driver",
                    x.getTargetException());
        } catch (ReflectiveOperationException | IllegalArgumentException x) {
            log.warn("Unable to send the request boundary hint [" + hint.getName() + "]", x);
        }
    }

    /**
     * Creates and caches a {@link java.lang.reflect.Constructor} used to instantiate the proxy object of the requested
     * kind (with or without the XAConnection interface). We cache these, since the creation of a constructor is fairly
     * slow.
     *
     * @param xa Use a XA connection
     *
     * @return constructor used to instantiate the wrapper object
     *
     * @throws NoSuchMethodException Failed to get a constructor
     */
    /*
     * Neither the class nor the constructor are exposed outside of jdbc-pool. Given the comments in the jdbc-pool code
     * regarding caching for performance, continue to use Proxy.getProxyClass(). This will need to be revisited if that
     * method is marked for removal.
     */
    @SuppressWarnings("deprecation")
    public Constructor<?> getProxyConstructor(boolean xa) throws NoSuchMethodException {
        // Cache the constructor per proxy kind
        Constructor<?> constructor = xa ? xaProxyClassConstructor : proxyClassConstructor;
        if (constructor == null) {
            Class<?> proxyClass = xa
                    ? Proxy.getProxyClass(ConnectionPool.class.getClassLoader(),
                            new Class[] { Connection.class, javax.sql.PooledConnection.class, XAConnection.class })
                    : Proxy.getProxyClass(ConnectionPool.class.getClassLoader(),
                            new Class[] { Connection.class, javax.sql.PooledConnection.class });
            constructor = proxyClass.getConstructor(new Class[] { InvocationHandler.class });
            if (xa) {
                xaProxyClassConstructor = constructor;
            } else {
                proxyClassConstructor = constructor;
            }
        }
        return constructor;
    }

    /**
     * Closes the pool and all disconnects all idle connections Active connections will be closed upon the
     * {@link java.sql.Connection#close close} method is called on the underlying connection instead of being returned
     * to the pool
     *
     * @param force - true to even close the active connections
     */
    protected void close(boolean force) {
        // Are we already closed
        if (this.closed) {
            return;
        }
        // Prevent other threads from entering
        this.closed = true;
        // Stop background thread
        terminatePoolCleaner();

        // An asynchronous borrow waits on the idle queue, where a thread cannot
        // observe the closed flag: this pool never offers a connection to that
        // queue once it is closed, so a caller parked in the Future.get() of
        // getConnectionAsync() would stay parked forever. Fail the waiters that
        // are registered on the queue, so that the futures handed out terminate.
        if (idle instanceof FairBlockingQueue<?>) {
            ((FairBlockingQueue<PooledConnection>) idle).cancelWaiters();
        }

        // Release all idle connections
        BlockingQueue<PooledConnection> pool = (!idle.isEmpty()) ? idle : (force ? busy : idle);
        boolean interrupted = false;
        while (!pool.isEmpty()) {
            try {
                // Retrieve the next connection
                PooledConnection con = pool.poll(1000, TimeUnit.MILLISECONDS);
                // Close it and retrieve the next one, if one is available
                while (con != null) {
                    // Close the connection. A forced shutdown tears a busy
                    // connection away from a borrower that is legitimately
                    // using it, so the drain releases it the way it releases
                    // an idle one: disconnecting the session and running the
                    // interceptors' disconnect events. It does not abandon it.
                    // The abandonment counter, the WARN log and the JMX
                    // abandon notification record an application that leaked
                    // a connection - checkAbandoned()'s bookkeeping - and a
                    // shutdown the operator ordered is no such leak, so
                    // recording it there inflates the monitoring counter and
                    // fires one bogus leak notification per connection in
                    // use. The borrower's handle is left equally unusable
                    // either way: an abandon disconnected it through this
                    // same release, and a later close on the handle finds the
                    // pool closed and the connection already released.
                    try {
                        release(con);
                    } catch (Throwable x) {
                        // One failing connection must not leave the
                        // remaining idle and busy connections undisconnected
                        log.warn("Unable to close pooled connection.", x);
                    }
                    if (!pool.isEmpty()) {
                        con = pool.poll(1000, TimeUnit.MILLISECONDS);
                    } else {
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                // Do not restore the interrupt status here. A poll on a queue
                // that checks the status before inspecting its elements would
                // then throw again immediately, removing no connection, and
                // the loop would spin without progress. Clear the status so
                // the polls keep working and remember that we were interrupted
                // so the status can be propagated once the drain completes.
                Thread.interrupted();
                interrupted = true;
            }
            if (pool.isEmpty() && force && pool != busy) {
                pool = busy;
            }
        }
        if (interrupted && getPoolProperties().getPropagateInterruptState()) {
            Thread.currentThread().interrupt();
        }
        if (this.getPoolProperties().isJmxEnabled()) {
            this.jmxPool = null;
        }
        PoolProperties.InterceptorDefinition[] proxies = getPoolProperties().getJdbcInterceptorsAsArray();
        for (int i = 0; i < proxies.length; i++) {
            try {
                JdbcInterceptor interceptor = proxies[i].getInterceptorClass().getConstructor().newInstance();
                interceptor.setProperties(proxies[i].getProperties());
                interceptor.poolClosed(this);
            } catch (Exception x) {
                if (log.isDebugEnabled()) {
                    log.debug("Unable to inform interceptor of pool closure.", x);
                }
            }
        }
    }


    /**
     * Initialize the connection pool - called from the constructor
     *
     * @param properties PoolProperties - properties used to initialize the pool with
     *
     * @throws SQLException if initialization fails
     */
    protected void init(PoolConfiguration properties) throws SQLException {
        poolProperties = properties;

        // Make sure the pool is properly configured
        checkPoolConfiguration(properties);

        // Make space for 10 extra in case we flow over a bit
        busy = new LinkedBlockingQueue<>();
        // busy = new FairBlockingQueue<PooledConnection>();
        // Make space for 10 extra in case we flow over a bit
        if (properties.isFairQueue()) {
            idle = new FairBlockingQueue<>();
            // idle = new LinkedTransferQueue<PooledConnection>();
            // idle = new ArrayBlockingQueue<PooledConnection>(properties.getMaxActive(),false);
        } else {
            idle = new LinkedBlockingQueue<>();
        }

        initializePoolCleaner(properties);

        // Create JMX MBean
        if (this.getPoolProperties().isJmxEnabled()) {
            createMBean();
        }

        // Parse and create an initial set of interceptors. Letting them know the pool has started.
        // These interceptors will not get any connection.
        PoolProperties.InterceptorDefinition[] proxies = getPoolProperties().getJdbcInterceptorsAsArray();
        for (int i = 0; i < proxies.length; i++) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Creating interceptor instance of class:" + proxies[i].getInterceptorClass());
                }
                JdbcInterceptor interceptor = proxies[i].getInterceptorClass().getConstructor().newInstance();
                interceptor.setProperties(proxies[i].getProperties());
                interceptor.poolStarted(this);
            } catch (Exception x) {
                log.error("Unable to inform interceptor of pool start.", x);
                org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = jmxPool;
                if (jmx != null) {
                    jmx.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.NOTIFY_INIT, getStackTrace(x));
                }
                close(true);
                SQLException ex = new SQLException();
                ex.initCause(x);
                throw ex;
            }
        }

        // Initialize the pool with its initial set of members
        PooledConnection[] initialPool = new PooledConnection[poolProperties.getInitialSize()];
        try {
            for (int i = 0; i < initialPool.length; i++) {
                initialPool[i] = this.borrowConnection(0, null, null); // Don't wait, should be no contention
            }

        } catch (SQLException x) {
            log.error("Unable to create initial connections of pool.", x);
            if (!poolProperties.isIgnoreExceptionOnPreLoad()) {
                org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = jmxPool;
                if (jmx != null) {
                    jmx.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.NOTIFY_INIT, getStackTrace(x));
                }
                close(true);
                throw x;
            }
        } finally {
            // Return the members as idle to the pool
            for (int i = 0; i < initialPool.length; i++) {
                if (initialPool[i] != null) {
                    try {
                        this.returnConnection(initialPool[i]);
                    } catch (Exception x) {
                        /* NOOP */}
                }
            }
        }

        closed = false;
    }

    /**
     * Validates and corrects pool configuration values. Ensures that maxActive, initialSize, minIdle, maxIdle, and
     * related values are consistent.
     *
     * @param properties the pool configuration to validate and correct
     */
    public void checkPoolConfiguration(PoolConfiguration properties) {
        // Make sure the pool is properly configured
        if (properties.getMaxActive() < 1) {
            log.warn("maxActive is smaller than 1, setting maxActive to: " + PoolProperties.DEFAULT_MAX_ACTIVE);
            properties.setMaxActive(PoolProperties.DEFAULT_MAX_ACTIVE);
        }
        if (properties.getMaxActive() < properties.getInitialSize()) {
            log.warn("initialSize is larger than maxActive, setting initialSize to: " + properties.getMaxActive());
            properties.setInitialSize(properties.getMaxActive());
        }
        if (properties.getMinIdle() > properties.getMaxActive()) {
            log.warn("minIdle is larger than maxActive, setting minIdle to: " + properties.getMaxActive());
            properties.setMinIdle(properties.getMaxActive());
        }
        if (properties.getMaxIdle() > properties.getMaxActive()) {
            log.warn("maxIdle is larger than maxActive, setting maxIdle to: " + properties.getMaxActive());
            properties.setMaxIdle(properties.getMaxActive());
        }
        if (properties.getMaxIdle() < properties.getMinIdle()) {
            log.warn("maxIdle is smaller than minIdle, setting maxIdle to: " + properties.getMinIdle());
            properties.setMaxIdle(properties.getMinIdle());
        }
        if (properties.getMaxAge() > 0 && properties.isPoolSweeperEnabled() &&
                properties.getTimeBetweenEvictionRunsMillis() > properties.getMaxAge()) {
            log.warn("timeBetweenEvictionRunsMillis is larger than maxAge, setting timeBetweenEvictionRunsMillis to: " +
                    properties.getMaxAge());
            properties.setTimeBetweenEvictionRunsMillis((int) properties.getMaxAge());
        }
    }

    /**
     * Starts the pool cleaner (evicter) thread if pool sweeper is enabled. A cleaner that is still running from a
     * previous start is terminated first, so this call never leaves a replaced task registered on the shared timer
     * without a reference to stop it.
     *
     * @param properties the pool configuration containing sweeper settings
     */
    public synchronized void initializePoolCleaner(PoolConfiguration properties) {
        // If the evictor thread is supposed to run, start it now
        if (properties.isPoolSweeperEnabled()) {
            // Never overwrite a running cleaner: the replaced task would
            // stay registered on the shared timer with no reference left to
            // stop it, duplicating maintenance work for the pool's lifetime
            terminatePoolCleaner();
            poolCleaner = new PoolCleaner(this, properties.getTimeBetweenEvictionRunsMillis());
            poolCleaner.start();
        }
    }

    /**
     * Stops the pool cleaner (evicter) thread if it is currently running and forgets its reference.
     */
    public synchronized void terminatePoolCleaner() {
        if (poolCleaner != null) {
            poolCleaner.stopRunning();
            poolCleaner = null;
        }
    }


    /**
     * thread safe way to abandon a connection signals a connection to be abandoned. this will disconnect the
     * connection, and log the stack trace if logAbandoned=true
     *
     * @param con PooledConnection
     */
    protected void abandon(PooledConnection con) {
        if (con == null) {
            return;
        }
        try {
            con.lock();
            String trace = con.getStackTrace();
            if (getPoolProperties().isLogAbandoned()) {
                log.warn("Connection has been abandoned " + con + ":" + trace);
            }
            org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = jmxPool;
            if (jmx != null) {
                jmx.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.NOTIFY_ABANDON, trace);
            }
            // Release the connection
            removeAbandonedCount.incrementAndGet();
            release(con);
        } finally {
            con.unlock();
        }
    }

    /**
     * Thread safe way to suspect a connection. Similar to {@link #abandon(PooledConnection)}, but instead of actually
     * abandoning the connection, this will log a warning and set the suspect flag on the {@link PooledConnection} if
     * logAbandoned=true
     *
     * @param con PooledConnection
     */
    protected void suspect(PooledConnection con) {
        if (con == null) {
            return;
        }
        if (con.isSuspect()) {
            return;
        }
        try {
            con.lock();
            String trace = con.getStackTrace();
            if (getPoolProperties().isLogAbandoned()) {
                log.warn("Connection has been marked suspect, possibly abandoned " + con + "[" +
                        (System.currentTimeMillis() - con.getTimestamp()) + " ms.]:" + trace);
            }
            org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = jmxPool;
            if (jmx != null) {
                jmx.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.SUSPECT_ABANDONED_NOTIFICATION, trace);
            }
            con.setSuspect(true);
        } finally {
            con.unlock();
        }
    }

    /**
     * thread safe way to release a connection
     *
     * @param con PooledConnection
     */
    protected void release(PooledConnection con) {
        if (con == null) {
            return;
        }
        boolean destroyed;
        try {
            con.lock();
            destroyed = con.release();
            if (destroyed) {
                // Counter only decremented once
                size.addAndGet(-1);
                con.setHandler(null);
            }
            releasedCount.incrementAndGet();
        } finally {
            con.unlock();
        }
        // We've asynchronously reduced the number of connections
        // we could have threads stuck in idle.poll(timeout) that will never be
        // notified. Only a call that actually destroyed a connection opened a
        // slot; offering a placeholder for a connection that was already
        // released would run the pool over maxActive.
        // A counted thread is not necessarily able to consume the placeholder:
        // after an interrupted or timed out wait its queue latch is already
        // deregistered while the wait count is still held, and a thread that
        // creates its connection does not wait on the queue at all. With no
        // counted thread able to consume it, a placeholder can only be taken
        // by the shutdown drain, which drains this very queue: it would poll
        // the placeholder, destroy it, and the release of that destruction
        // would queue the next one, spinning for as long as any thread holds
        // a wait count. A closed pool serves no more borrowers, so only
        // queue the placeholder while it is open.
        if (destroyed && !isClosed() && waitcount.get() > 0) {
            if (!idle.offer(create(true))) {
                log.warn("Failed to add a new connection to the pool after releasing a connection " +
                        "when at least one thread was waiting for a connection.");
            }
        }
    }

    /**
     * Thread safe way to retrieve a connection from the pool
     *
     * @param wait     - time to wait, overrides the maxWait from the properties, set to -1 if you wish to use maxWait,
     *                     0 if you wish no wait time.
     * @param username The user name to use for the connection
     * @param password The password for the connection
     *
     * @return a connection
     *
     * @throws SQLException Failed to get a connection
     */
    private PooledConnection borrowConnection(int wait, String username, String password) throws SQLException {

        if (isClosed()) {
            throw new SQLException("Connection pool closed.");
        }

        // Get the current time stamp
        long now = System.currentTimeMillis();
        // See if there is one available immediately
        PooledConnection con = idle.poll();

        while (true) {
            if (con != null) {
                // Configure the connection and return it
                PooledConnection result = borrowConnection(now, con, username, password);
                if (result != null) {
                    return result;
                }
                // The connection was released while it sat idle, so this
                // poll got nothing to hand out; keep polling. Nothing is
                // counted here: a borrow is counted on the hand-off that
                // succeeds, in setupConnection().
            }

            // If we get here, see if we need to create one
            // this is not 100% accurate since it doesn't use a shared
            // atomic variable - a connection can become idle while we are creating
            // a new connection
            if (size.get() < getPoolProperties().getMaxActive()) {
                // Atomic duplicate check
                if (size.addAndGet(1) > getPoolProperties().getMaxActive()) {
                    // If we got here, two threads passed through the first if
                    size.decrementAndGet();
                } else {
                    // Create a connection, we're below the limit
                    return createConnection(now, con, username, password);
                }
            }

            // Calculate wait time for this iteration
            long maxWait = wait;
            // If the passed in wait time is -1, means we should use the pool property value
            if (wait == -1) {
                maxWait = (getPoolProperties().getMaxWait() <= 0) ? Long.MAX_VALUE : getPoolProperties().getMaxWait();
            }

            long timetowait = Math.max(0, maxWait - (System.currentTimeMillis() - now));
            waitcount.incrementAndGet();
            try {
                // A connection may have been destroyed between the size check
                // above and the registration of this waiter; such a releasing
                // thread saw no waiter and will not notify this poll, so
                // re-check the limit now rather than wait for a notification
                // that may never arrive. Skip the case where a connection (or
                // a wake-up placeholder) is already queued to be taken.
                if (idle.isEmpty() && size.get() < getPoolProperties().getMaxActive()) {
                    // Atomic duplicate check
                    if (size.addAndGet(1) > getPoolProperties().getMaxActive()) {
                        // If we got here, two threads passed through the first if
                        size.decrementAndGet();
                    } else {
                        // Create a connection, we're below the limit
                        return createConnection(now, con, username, password);
                    }
                }
                // Retrieve an existing connection
                con = idle.poll(timetowait, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                if (getPoolProperties().getPropagateInterruptState()) {
                    Thread.currentThread().interrupt();
                }
                SQLException sx = new SQLException("Pool wait interrupted.");
                sx.initCause(ex);
                throw sx;
            } finally {
                waitcount.decrementAndGet();
            }
            if (maxWait == 0 && con == null) { // No wait, return one if we have one
                org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = jmxPool;
                if (jmx != null) {
                    jmx.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.POOL_EMPTY, "Pool empty - no wait.");
                }
                throw new PoolExhaustedException("[" + Thread.currentThread().getName() + "] " +
                        "NoWait: Pool empty. Unable to fetch a connection, none available[" + busy.size() +
                        " in use].");
            }
            // We didn't get a connection, lets see if we timed out
            if (con == null) {
                if ((System.currentTimeMillis() - now) >= maxWait) {
                    org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = jmxPool;
                    if (jmx != null) {
                        jmx.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.POOL_EMPTY,
                                "Pool empty - timeout.");
                    }
                    throw new PoolExhaustedException("[" + Thread.currentThread().getName() + "] " +
                            "Timeout: Pool empty. Unable to fetch a connection in " + (maxWait / 1000) +
                            " seconds, none available[size:" + size.get() + "; busy:" + busy.size() + "; idle:" +
                            idle.size() + "; lastwait:" + timetowait + "].");
                } else {
                    // No timeout, lets try again
                    continue;
                }
            }
        }
    }

    /**
     * Creates a JDBC connection and tries to connect to the database.
     *
     * @param now      timestamp of when this was called
     * @param notUsed  Argument not used
     * @param username The user name to use for the connection
     * @param password The password for the connection
     *
     * @return a PooledConnection that has been connected
     *
     * @throws SQLException Failed to get a connection
     */
    protected PooledConnection createConnection(long now, PooledConnection notUsed, String username, String password)
            throws SQLException {
        // No connections where available we'll create one
        PooledConnection con = create(false);
        if (username != null) {
            con.getAttributes().put(PooledConnection.PROP_USER, username);
        }
        if (password != null) {
            con.getAttributes().put(PooledConnection.PROP_PASSWORD, password);
        }
        boolean error = false;
        try {
            // Connect and validate the connection
            con.lock();
            con.connect();
            if (isClosed()) {
                // The pool was closed while this physical connection was
                // being created; the shutdown drain has already passed over
                // the queues, so neither hand the connection out nor register
                // it in busy - the error path below disconnects it
                throw new SQLException("Connection pool closed.");
            }
            if (con.validate(PooledConnection.VALIDATE_INIT)) {
                // No need to lock a new one, its not contented
                con.setTimestamp(now);
                if (getPoolProperties().isLogAbandoned()) {
                    con.setStackTraceElements(getThreadDumpElements());
                }
                if (!busy.offer(con)) {
                    log.debug("Connection doesn't fit into busy array, connection will not be traceable.");
                }
                createdCount.incrementAndGet();
                return con;
            } else {
                // Validation failed, make sure we disconnect
                // and clean up
                throw new SQLException("Validation Query Failed, enable logValidationErrors for more details.");
            }
        } catch (Exception e) {
            error = true;
            if (log.isDebugEnabled()) {
                log.debug("Unable to create a new JDBC connection.", e);
            }
            if (e instanceof SQLException) {
                throw (SQLException) e;
            } else {
                SQLException ex = new SQLException(e.getMessage());
                ex.initCause(e);
                throw ex;
            }
        } finally {
            // con can never be null here
            if (error) {
                release(con);
            }
            con.unlock();
        }
    }

    /**
     * Validates and configures a previously idle connection
     *
     * @param now      - timestamp
     * @param con      - the connection to validate and configure
     * @param username The user name to use for the connection
     * @param password The password for the connection
     *
     * @return a connection
     *
     * @throws SQLException if a validation error happens
     */
    protected PooledConnection borrowConnection(long now, PooledConnection con, String username, String password)
            throws SQLException {
        // We have a connection, lets set it up

        // Flag to see if we need to nullify
        boolean setToNull = false;
        try {
            con.lock();
            if (con.isReleased()) {
                return null;
            }

            // Evaluate username/password change as well as max age functionality
            boolean forceReconnect = con.shouldForceReconnect(username, password) || con.isMaxAgeExpired();

            if (!con.isDiscarded() && !con.isInitialized()) {
                // Here it states that the connection not discarded, but the connection is null
                // don't attempt a connect here. It will be done during the reconnect.
                forceReconnect = true;
            }

            if (!forceReconnect) {
                if ((!con.isDiscarded()) && con.validate(PooledConnection.VALIDATE_BORROW)) {
                    // Set the timestamp
                    con.setTimestamp(now);
                    if (getPoolProperties().isLogAbandoned()) {
                        // Set the stack trace for this pool
                        con.setStackTraceElements(getThreadDumpElements());
                    }
                    if (!busy.offer(con)) {
                        log.debug("Connection doesn't fit into busy array, connection will not be traceable.");
                    }
                    return con;
                }
            }
            // If we reached here, that means the connection
            // is either has another principal, is discarded or validation failed.
            // We will make one more attempt
            // in order to guarantee that the thread that just acquired
            // the connection shouldn't have to poll again.
            try {
                con.reconnect();
                reconnectedCount.incrementAndGet();
                int validationMode = isInitNewConnections() ? PooledConnection.VALIDATE_INIT
                        : PooledConnection.VALIDATE_BORROW;
                if (con.validate(validationMode)) {
                    // Set the timestamp
                    con.setTimestamp(now);
                    if (getPoolProperties().isLogAbandoned()) {
                        // Set the stack trace for this pool
                        con.setStackTraceElements(getThreadDumpElements());
                    }
                    if (!busy.offer(con)) {
                        log.debug("Connection doesn't fit into busy array, connection will not be traceable.");
                    }
                    return con;
                } else {
                    // Validation failed.
                    throw new SQLException("Failed to validate a newly established connection.");
                }
            } catch (Exception x) {
                release(con);
                setToNull = true;
                if (x instanceof SQLException) {
                    throw (SQLException) x;
                } else {
                    SQLException ex = new SQLException(x.getMessage());
                    ex.initCause(x);
                    throw ex;
                }
            }
        } finally {
            con.unlock();
            if (setToNull) {
                con = null;
            }
        }
    }

    /**
     * Returns whether new connections should be initialized by invoking {@link PooledConnection#validate(int)} with
     * {@link PooledConnection#VALIDATE_INIT}.
     *
     * @return true if pool is either configured to test connections on connect or a non-NULL init SQL has been
     *             configured
     */
    private boolean isInitNewConnections() {
        return getPoolProperties().isTestOnConnect() || getPoolProperties().getInitSQL() != null;
    }

    /**
     * Terminate the current transaction for the given connection.
     *
     * @param con The connection
     *
     * @return <code>true</code> if the connection TX termination succeeded otherwise <code>false</code>
     */
    protected boolean terminateTransaction(PooledConnection con) {
        try {
            // Terminate the transaction of any connection that is returned
            // while still in one; the pool-level defaultAutoCommit property
            // must not gate this, the connection's own state is what matters
            boolean doRollback = getPoolProperties().getRollbackOnReturn();
            boolean doCommit = !doRollback && getPoolProperties().getCommitOnReturn();
            if (doRollback || doCommit) {
                Connection raw = con.getConnection();
                if (raw == null) {
                    // Defensive: the pool's call path reaches here only on
                    // a session that is not closed; with nothing to
                    // terminate there is nothing to fail, and the caller's
                    // own closed checks take the connection out of service
                    return true;
                }
                if (!raw.getAutoCommit()) {
                    if (doRollback) {
                        raw.rollback();
                    } else {
                        raw.commit();
                    }
                }
            }
            return true;
        } catch (SQLException x) {
            log.warn("Unable to terminate transaction, connection will be closed.", x);
            return false;
        }

    }

    /**
     * Determines if a connection should be closed upon return to the pool.
     *
     * @param con    - the connection
     * @param action - the validation action that should be performed
     *
     * @return <code>true</code> if the connection should be closed
     */
    protected boolean shouldClose(PooledConnection con, int action) {
        if (con.getConnectionVersion() < getPoolVersion()) {
            return true;
        }
        if (con.isDiscarded()) {
            return true;
        }
        try {
            if (con.isClosed()) {
                return true;
            }
        } catch (SQLException e) {
            log.warn("Unable to check if connection is closed", e);
            return true;
        }
        if (isClosed()) {
            return true;
        }
        // Terminate the transaction before validating. When the pool is
        // configured to commit or roll back on return, that is the explicit
        // choice for the pending work, and a validation that runs first
        // would end the transaction with its own rollback and silently
        // override a configured commitOnReturn. With the termination first,
        // a validation below only ever sees a transaction the return path
        // deliberately left open.
        if (!terminateTransaction(con)) {
            return true;
        }
        if (!con.validate(action)) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether this connection has {@link PooledConnection#isMaxAgeExpired() expired} and tries to reconnect if
     * it has.
     *
     * @param con PooledConnection
     *
     * @return true if the connection was either not expired or expired but reconnecting succeeded, false if
     *             reconnecting failed (either because a new connection could not be established or validating the newly
     *             created connection failed)
     *
     * @see PooledConnection#isMaxAgeExpired()
     */
    protected boolean reconnectIfExpired(PooledConnection con) {
        if (con.isMaxAgeExpired()) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Connection [" + this + "] expired because of maxAge, trying to reconnect");
                }
                con.reconnect();
                reconnectedCount.incrementAndGet();
                if (isInitNewConnections() && !con.validate(PooledConnection.VALIDATE_INIT)) {
                    return false;
                }
            } catch (Exception e) {
                log.error("Failed to re-connect connection [" + this + "] that expired because of maxAge", e);
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a connection to the pool If the pool is closed, the connection will be released If the connection is not
     * part of the busy queue, it will be released. If {@link PoolProperties#testOnReturn} is set to true it will be
     * validated
     *
     * @param con PooledConnection to be returned to the pool
     */
    protected void returnConnection(PooledConnection con) {
        if (isClosed()) {
            // If the connection pool is closed
            // close the connection instead of returning it
            release(con);
            return;
        }

        if (con != null) {
            try {
                returnedCount.incrementAndGet();
                con.lock();
                if (con.isSuspect()) {
                    if (poolProperties.isLogAbandoned() && log.isInfoEnabled()) {
                        log.info("Connection(" + con + ") that has been marked suspect was returned." +
                                " The processing time is " + (System.currentTimeMillis() - con.getTimestamp()) +
                                " ms.");
                    }
                    org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = jmxPool;
                    if (jmx != null) {
                        jmx.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.SUSPECT_RETURNED_NOTIFICATION,
                                "Connection(" + con + ") that has been marked suspect was returned.");
                    }
                }
                if (busy.remove(con)) {
                    try {
                        if (!shouldClose(con, PooledConnection.VALIDATE_RETURN) && reconnectIfExpired(con)) {
                            con.clearWarnings();
                            con.setStackTrace(null);
                            con.setTimestamp(System.currentTimeMillis());
                            // The application is done with the connection:
                            // close the JDBC 4.3 request that beginRequest()
                            // opened in setupConnection(). A connection
                            // discarded instead of going back to the pool is
                            // not hinted; it is about to be closed, which
                            // subsumes the hint.
                            invokeRequestHint(END_REQUEST, con.getConnection());
                            if (((idle.size() >= poolProperties.getMaxIdle()) && !poolProperties.isPoolSweeperEnabled()) ||
                                    (!idle.offer(con))) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Connection [" + con +
                                            "] will be closed and not returned to the pool, idle[" + idle.size() +
                                            "]>=maxIdle[" + poolProperties.getMaxIdle() + "] idle.offer failed.");
                                }
                                release(con);
                            }
                        } else {
                            if (log.isDebugEnabled()) {
                                log.debug("Connection [" + con + "] will be closed and not returned to the pool.");
                            }
                            release(con);
                        }
                    } catch (RuntimeException x) {
                        // Everything above runs after the connection was
                        // claimed from the busy queue and calls into the
                        // driver - a closed check, a transaction termination,
                        // a validation, a warnings clear - before the
                        // connection is handed to the idle queue or released.
                        // A driver that fails one of those calls with an
                        // unchecked exception must not strand the connection
                        // between the two queues: it would be invisible to
                        // checkAbandoned(), to checkIdle() and to the close()
                        // drain alike, never disconnected, and the pool would
                        // permanently lose one maxActive slot per occurrence.
                        // Take it out of service instead, as a failed
                        // validation already does: the application sees the
                        // return complete quietly, just as it does when the
                        // driver fails a check it does handle. Release
                        // before logging: nothing but the release may keep a
                        // claimed connection from being closed, and after it
                        // the connection renders itself without consulting
                        // the driver that failed.
                        con.setDiscarded(true);
                        release(con);
                        log.warn("Unable to return connection [" + con + "] to the pool, closing it.", x);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Connection [" + con +
                                "] will be closed and not returned to the pool, busy.remove failed.");
                    }
                    release(con);
                }
            } finally {
                con.unlock();
            }
        }
    }

    /**
     * Determines if a connection should be abandoned based on {@link PoolProperties#abandonWhenPercentageFull} setting.
     *
     * @return <code>true</code> if the connection should be abandoned
     */
    protected boolean shouldAbandon() {
        if (!poolProperties.isRemoveAbandoned()) {
            return false;
        }
        if (poolProperties.getAbandonWhenPercentageFull() == 0) {
            return true;
        }
        float used = busy.size();
        float max = poolProperties.getMaxActive();
        float perc = poolProperties.getAbandonWhenPercentageFull();
        return (used / max * 100f) >= perc;
    }

    /**
     * Iterates through all the busy connections and checks for connections that have timed out
     */
    public void checkAbandoned() {
        try {
            if (busy.isEmpty()) {
                return;
            }
            Iterator<PooledConnection> locked = busy.iterator();
            int sto = getPoolProperties().getSuspectTimeout();
            while (locked.hasNext()) {
                PooledConnection con = locked.next();
                boolean setToNull = false;
                try {
                    con.lock();
                    // The con has been returned to the pool or released
                    // ignore it
                    if (idle.contains(con) || con.isReleased()) {
                        continue;
                    }
                    long time = con.getTimestamp();
                    long now = System.currentTimeMillis();
                    if (shouldAbandon() && (now - time) > con.getAbandonTimeout()) {
                        // Claim the connection before acting on it; without
                        // a real lock the checks above may already be stale,
                        // and a connection the application returned to the
                        // pool in the meantime must not be abandoned
                        if (!busy.remove(con)) {
                            continue;
                        }
                        abandon(con);
                        setToNull = true;
                    } else if (sto > 0 && (now - time) > (sto * 1000L)) {
                        suspect(con);
                    } else {
                        // Do nothing
                    }
                } finally {
                    con.unlock();
                    if (setToNull) {
                        con = null;
                    }
                }
            }
        } catch (ConcurrentModificationException e) {
            if (log.isDebugEnabled()) {
                log.debug("checkAbandoned failed.", e);
            }
        } catch (Exception e) {
            log.warn("checkAbandoned failed, it will be retried.", e);
        }
    }

    /**
     * Iterates through the idle connections and resizes the idle pool based on parameters
     * {@link PoolProperties#maxIdle}, {@link PoolProperties#minIdle}, {@link PoolProperties#minEvictableIdleTimeMillis}
     */
    public void checkIdle() {
        checkIdle(false);
    }

    /**
     * Iterates through the idle connections and resizes the idle pool based on pool configuration parameters. If
     * ignoreMinSize is true, idle connections may be removed even if the pool is below minIdle.
     *
     * @param ignoreMinSize if true, ignore the minIdle setting when evicting
     */
    public void checkIdle(boolean ignoreMinSize) {

        try {
            if (idle.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            Iterator<PooledConnection> unlocked = idle.iterator();
            while ((ignoreMinSize || (idle.size() >= getPoolProperties().getMinIdle())) && unlocked.hasNext()) {
                PooledConnection con = unlocked.next();
                boolean setToNull = false;
                try {
                    con.lock();
                    // The con been taken out, we can't clean it up
                    if (busy.contains(con)) {
                        continue;
                    }
                    long time = con.getTimestamp();
                    if (shouldReleaseIdle(now, con, time)) {
                        // Claim the connection before acting on it; without
                        // a real lock the checks above may already be stale,
                        // and a connection a borrower took out of the idle
                        // queue in the meantime must not be released here
                        if (!idle.remove(con)) {
                            continue;
                        }
                        releasedIdleCount.incrementAndGet();
                        release(con);
                        setToNull = true;
                    } else {
                        // Do nothing
                    }
                } finally {
                    con.unlock();
                    if (setToNull) {
                        con = null;
                    }
                }
            }
        } catch (ConcurrentModificationException e) {
            if (log.isDebugEnabled()) {
                log.debug("checkIdle failed.", e);
            }
        } catch (Exception e) {
            log.warn("checkIdle failed, it will be retried.", e);
        }

    }


    /**
     * Determines whether an idle connection should be released from the pool.
     *
     * @param now  the current time in milliseconds
     * @param con  the pooled connection to evaluate
     * @param time the timestamp when the connection last became idle
     *
     * @return {@code true} if the connection should be released
     */
    protected boolean shouldReleaseIdle(long now, PooledConnection con, long time) {
        if (con.getConnectionVersion() < getPoolVersion()) {
            return true;
        } else {
            return (con.getReleaseTime() > 0) && ((now - time) > con.getReleaseTime()) &&
                    (getSize() > getPoolProperties().getMinIdle());
        }
    }

    /**
     * Forces a validation of all idle connections if {@link PoolProperties#testWhileIdle} is set.
     * <p>
     * Note: when this method is invoked externally while the per-connection lock is disabled (sweeper switched off and
     * {@link PoolProperties#getUseLock()} returning {@code false}), validating and reconnecting a connection can still
     * overlap with a borrower that just took it out of the idle queue. That overlap is transient and surfaces to one of
     * the two threads as a connection error; it is sharpest when {@link PoolProperties#getMaxAge()} is set so that an
     * expired connection may be reconnected under a borrower mid-use.
     */
    public void testAllIdle() {
        testAllIdle(false);
    }

    /**
     * Forces a validation of all idle connections if {@link PoolProperties#testWhileIdle} is set.
     *
     * @param checkMaxAgeOnly whether to only check {@link PooledConnection#isMaxAgeExpired()} but not invoke
     *                            {@link PooledConnection#validate(int)}
     */
    public void testAllIdle(boolean checkMaxAgeOnly) {
        try {
            if (idle.isEmpty()) {
                return;
            }
            Iterator<PooledConnection> unlocked = idle.iterator();
            while (unlocked.hasNext()) {
                PooledConnection con = unlocked.next();
                try {
                    con.lock();
                    // The con been taken out, we can't clean it up
                    if (busy.contains(con)) {
                        continue;
                    }

                    boolean release;
                    if (checkMaxAgeOnly) {
                        release = !reconnectIfExpired(con);
                    } else {
                        release = !reconnectIfExpired(con) || !con.validate(PooledConnection.VALIDATE_IDLE);
                    }
                    if (release) {
                        // Claim the connection before acting on it; without
                        // a real lock the check above may already be stale,
                        // and a connection a borrower took out of the idle
                        // queue in the meantime must not be released here
                        if (!idle.remove(con)) {
                            continue;
                        }
                        releasedIdleCount.incrementAndGet();
                        release(con);
                    }
                } finally {
                    con.unlock();
                }
            }
        } catch (ConcurrentModificationException e) {
            if (log.isDebugEnabled()) {
                log.debug("testAllIdle failed.", e);
            }
        } catch (Exception e) {
            log.warn("testAllIdle failed, it will be retried.", e);
        }

    }

    /**
     * Creates a stack trace representing the existing thread's current state.
     *
     * @return a string object representing the current state.
     */
    protected static String getThreadDump() {
        Exception x = new Exception();
        x.fillInStackTrace();
        return getStackTrace(x);
    }

    /**
     * Captures the existing thread's current state without formatting it. An abandoned connection reports the borrow
     * site only rarely, so the formatting is left to {@link PooledConnection#getStackTrace()} and a borrow that is
     * never reported never pays for a thread dump.
     *
     * @return the stack trace elements representing the current state
     */
    protected static StackTraceElement[] getThreadDumpElements() {
        return new Exception().getStackTrace();
    }

    /**
     * Convert an exception into a String
     *
     * @param x - the throwable
     *
     * @return a string representing the stack trace
     */
    public static String getStackTrace(Throwable x) {
        if (x == null) {
            return null;
        } else {
            java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
            java.io.PrintStream writer = new java.io.PrintStream(bout);
            x.printStackTrace(writer);
            String result = bout.toString();
            return (x.getMessage() != null && x.getMessage().length() > 0) ? x.getMessage() + ";" + result : result;
        }
    }


    /**
     * Create a new pooled connection object. Not connected nor validated.
     *
     * @param incrementCounter <code>true</code> to increment the connection count
     *
     * @return a pooled connection object
     */
    protected PooledConnection create(boolean incrementCounter) {
        if (incrementCounter) {
            size.incrementAndGet();
        }
        PooledConnection con = new PooledConnection(getPoolProperties(), this);
        return con;
    }

    /**
     * Purges all connections in the pool. For connections currently in use, these connections will be purged when
     * returned on the pool. This call also purges connections that are idle and in the pool To only purge used/active
     * connections see {@link #purgeOnReturn()}
     */
    public void purge() {
        purgeOnReturn();
        checkIdle(true);
    }

    /**
     * Purges connections when they are returned from the pool. This call does not purge idle connections until they are
     * used. To purge idle connections see {@link #purge()}
     */
    public void purgeOnReturn() {
        poolVersion.incrementAndGet();
    }

    /**
     * Hook to perform final actions on a pooled connection object once it has been disconnected and will be discarded
     *
     * @param con The connection
     */
    protected void finalize(PooledConnection con) {
        JdbcInterceptor handler = con.getHandler();
        while (handler != null) {
            // Read the next link before the callback, as the callback
            // is allowed to modify it
            JdbcInterceptor next = handler.getNext();
            try {
                handler.reset(null, null);
            } catch (Throwable t) {
                // One broken interceptor must not stop the rest of the
                // chain from being reset
                log.warn("Interceptor [" + handler.getClass().getName() + "] threw an exception from reset(); " +
                        "continuing with the rest of the chain.", t);
            }
            handler = next;
        }
    }

    /**
     * Hook to perform final actions on a pooled connection object once it has been disconnected and will be discarded
     *
     * @param con        The connection
     * @param finalizing <code>true</code> if finalizing the connection
     */
    protected void disconnectEvent(PooledConnection con, boolean finalizing) {
        JdbcInterceptor handler = con.getHandler();
        while (handler != null) {
            // Read the next link before the callback, as the callback
            // is allowed to modify it
            JdbcInterceptor next = handler.getNext();
            try {
                handler.disconnected(this, con, finalizing);
            } catch (Throwable t) {
                // One broken interceptor must not stop the rest of the
                // chain from being notified
                log.warn("Interceptor [" + handler.getClass().getName() + "] threw an exception from disconnected(); " +
                        "continuing with the rest of the chain.", t);
            }
            handler = next;
        }
    }

    /**
     * Return the object that is potentially registered in JMX for notifications
     *
     * @return the object implementing the {@link org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean} interface
     */
    public org.apache.tomcat.jdbc.pool.jmx.ConnectionPool getJmxPool() {
        return jmxPool;
    }

    /**
     * Create MBean object that can be registered.
     */
    protected void createMBean() {
        try {
            jmxPool = new org.apache.tomcat.jdbc.pool.jmx.ConnectionPool(this);
        } catch (Exception x) {
            log.warn("Unable to start JMX integration for connection pool. Instance[" + getName() +
                    "] can't be monitored.", x);
        }
    }

    /**
     * The total number of connections borrowed from this pool.
     *
     * @return the borrowed connection count
     */
    public long getBorrowedCount() {
        return borrowedCount.get();
    }

    /**
     * The total number of connections returned to this pool.
     *
     * @return the returned connection count
     */
    public long getReturnedCount() {
        return returnedCount.get();
    }

    /**
     * The total number of connections created by this pool.
     *
     * @return the created connection count
     */
    public long getCreatedCount() {
        return createdCount.get();
    }

    /**
     * The total number of connections released from this pool.
     *
     * @return the released connection count
     */
    public long getReleasedCount() {
        return releasedCount.get();
    }

    /**
     * The total number of connections reconnected by this pool.
     *
     * @return the reconnected connection count
     */
    public long getReconnectedCount() {
        return reconnectedCount.get();
    }

    /**
     * The total number of connections released by remove abandoned.
     *
     * @return the PoolCleaner removed abandoned connection count
     */
    public long getRemoveAbandonedCount() {
        return removeAbandonedCount.get();
    }

    /**
     * The total number of connections released by eviction.
     *
     * @return the PoolCleaner evicted idle connection count
     */
    public long getReleasedIdleCount() {
        return releasedIdleCount.get();
    }

    /**
     * reset the statistics of this pool.
     */
    public void resetStats() {
        borrowedCount.set(0);
        returnedCount.set(0);
        createdCount.set(0);
        releasedCount.set(0);
        reconnectedCount.set(0);
        removeAbandonedCount.set(0);
        releasedIdleCount.set(0);
    }

    /**
     * Thread safe wrapper around a future for the regular queue This one retrieves the pooled connection object and
     * performs the initialization according to interceptors and validation rules. This class is thread safe and is
     * cancellable
     */
    protected class ConnectionFuture implements Future<Connection>, Runnable {
        Future<PooledConnection> pcFuture = null;
        /**
         * The claim on the pooled connection this future produces. The queue hands its connection to every caller of
         * the queue future, so this single compare-and-set is what decides who may act on it: a
         * {@link #get(long, TimeUnit)} caller, who then owns the connection that comes out of it, or
         * {@link #cancel(boolean)}, whose cleanup task returns that connection to the pool. Whichever path loses the
         * claim must neither use the connection nor report a cancellation of it, so one physical connection never ends
         * up with two owners.
         */
        AtomicBoolean configured = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        volatile Connection result = null;
        SQLException cause = null;
        AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile PooledConnection pc = null;

        /**
         * Creates a ConnectionFuture backed by an async pooled connection future.
         *
         * @param pcf the future for the pooled connection
         */
        public ConnectionFuture(Future<PooledConnection> pcf) {
            this.pcFuture = pcf;
        }

        /**
         * Creates a ConnectionFuture backed by an already available pooled connection.
         *
         * @param pc the pooled connection
         *
         * @throws SQLException if connection setup fails
         */
        public ConnectionFuture(PooledConnection pc) throws SQLException {
            this.pc = pc;
            result = ConnectionPool.this.setupConnection(pc);
            configured.set(true);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // On the async path the pc field is never assigned; once a
            // connection has been produced it is held in the result field,
            // so a non-null result means the future is complete and it is
            // too late to cancel. A queue future the pool closure already
            // cancelled is complete in the same way and leaves nothing to
            // deregister and no connection to clean up, so report nothing
            // cancelled without arming the deferred cleanup task, which a
            // java.util.concurrent.Future must not run for a completed task.
            if (pc != null || result != null || cancelled.get() || pcFuture.isCancelled()) {
                return false;
            }
            // The queue may have produced its connection already, and a get()
            // caller that received it is using it, so cancel only when this
            // thread wins the claim on that connection. Losing the claim means
            // the connection belongs to that caller, which is the completed
            // case a java.util.concurrent.Future reports as not cancelled.
            if (!configured.compareAndSet(false, true)) {
                return false;
            }
            cancelled.set(true);
            if (pcFuture.cancel(mayInterruptIfRunning)) {
                // The waiter was still queued, so it is deregistered and no
                // connection will be produced for this future. Release the
                // callers that are waiting on the claim above, as nobody is
                // left to publish a result for them.
                latch.countDown();
            } else {
                // The pooled connection was already handed over to the queue
                // future (or is being handed over right now), so cancel by
                // retrieving the connection and returning it to the pool
                ConnectionPool.this.cancellator.execute(this);
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Connection get() throws InterruptedException, ExecutionException {
            try {
                return get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (TimeoutException x) {
                throw new ExecutionException(x);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Connection get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            PooledConnection pc = this.pc != null ? this.pc : pcFuture.get(timeout, unit);
            if (pc != null) {
                if (result != null) {
                    return result;
                }
                if (configured.compareAndSet(false, true)) {
                    configure(pc);
                } else {
                    // If we reach here, another thread claimed the connection:
                    // a get() caller that is setting it up, or the cancellation
                    // cleanup that returns it to the pool
                    if (!latch.await(timeout, unit)) { // This shouldn't block for long
                        if (cancelled.get()) {
                            // A cancellation won the claim, so its cleanup is
                            // what this wait was for: as for a cancelled
                            // java.util.concurrent.Future, its callers are told
                            // about the cancellation rather than about their
                            // own deadline expiring. A timeout observed before
                            // the cancellation wins the claim stays a timeout;
                            // that window is inherent and bounded, the next
                            // get() here observes the cancellation
                            throw new CancellationException();
                        }
                        throw new TimeoutException();
                    }
                }
                if (result == null) {
                    if (cancelled.get()) {
                        // The cancellation cleanup won the claim on this
                        // connection and returned it to the pool, so it is not
                        // ours to hand out; the callers of a cancelled future
                        // are told about the cancellation instead of being
                        // handed a connection that is already back in the pool
                        throw new CancellationException();
                    }
                    throw new ExecutionException(cause);
                }
                return result;
            } else {
                return null;
            }
        }

        /**
         * Sets up the pooled connection that the claim on {@link #configured} gave to this caller and publishes what
         * came out of it for the callers waiting on {@link #latch}. Only the winner of that claim may call this, which
         * is what keeps the connection from being handed to one caller while the cancellation cleanup returns it to the
         * pool.
         *
         * @param pooled the pooled connection the queue handed out
         */
        private void configure(PooledConnection pooled) {
            try {
                PooledConnection pc = borrowConnection(System.currentTimeMillis(), pooled, null, null);
                if (pc != null) {
                    result = ConnectionPool.this.setupConnection(pc);
                } else {
                    // The connection was released while it sat idle, so this
                    // hand-off got nothing to set up. The synchronous borrow
                    // treats that as a poll to retry; an async poll is served
                    // by exactly the one connection its wait produced, so it
                    // fails the caller instead. Every caller released on the
                    // latch below reports what this publishes, and an
                    // ExecutionException with no cause tells them nothing.
                    cause = new SQLException("The pooled connection served to" +
                            " this asynchronous borrow was released before it" +
                            " could be set up; no connection was borrowed," +
                            " borrow the pool again.");
                }
            } catch (SQLException x) {
                cause = x;
            } finally {
                latch.countDown();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isCancelled() {
            return pc == null && (pcFuture.isCancelled() || cancelled.get());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isDone() {
            return pc != null || pcFuture.isDone();
        }

        /**
         * run method to be executed when cancelled by an executor
         */
        @Override
        public void run() {
            // cancel() armed this task because it won the claim on the
            // connection this future produces, so this is the path that hands
            // that connection back to the pool when no get() caller claimed it.
            // It sets the connection up the way a borrow does, so that the
            // return runs through the interceptors, but it never publishes a
            // result: a get() caller waiting on the claim has to observe the
            // cancellation rather than a connection that is already closed.
            try {
                PooledConnection pooled = null;
                try {
                    pooled = pcFuture.get(); // The connection this future produced
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException x) {
                    // The queue future produced no connection to return
                } catch (CancellationException x) {
                    // The pool closure cancelled the queued wait between the
                    // registration of this cleanup task and its execution; this
                    // future never produced a connection, so there is nothing
                    // to close
                }
                if (pooled != null) {
                    try {
                        PooledConnection pc = borrowConnection(System.currentTimeMillis(), pooled, null, null);
                        if (pc != null) {
                            Connection con = ConnectionPool.this.setupConnection(pc);
                            if (con != null) {
                                con.close(); // Return to the pool
                            }
                        }
                    } catch (SQLException x) {
                        // The connection was released or could not be set up,
                        // so there is nothing left to return to the pool
                        cause = x;
                    }
                }
            } catch (Exception x) {
                log.error("Unable to cancel ConnectionFuture.", x);
            } finally {
                // Whatever came out of it, the callers waiting on the claim are
                // done waiting
                latch.countDown();
            }
        }

    }


    private static volatile Timer poolCleanTimer = null;
    private static Set<PoolCleaner> cleaners = new HashSet<>();

    private static synchronized void registerCleaner(PoolCleaner cleaner) {
        unregisterCleaner(cleaner);
        cleaners.add(cleaner);
        if (poolCleanTimer == null) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(ConnectionPool.class.getClassLoader());
                // Create the timer thread in a PrivilegedAction so that a
                // reference to the web application class loader is not created
                // via Thread.inheritedAccessControlContext
                poolCleanTimer = new Timer(
                        "Tomcat JDBC Pool Cleaner[" + System.identityHashCode(ConnectionPool.class.getClassLoader()) +
                                ":" + System.currentTimeMillis() + "]",
                        true);
            } finally {
                Thread.currentThread().setContextClassLoader(loader);
            }
        }
        poolCleanTimer.schedule(cleaner, cleaner.sleepTime, cleaner.sleepTime);
    }

    private static synchronized void unregisterCleaner(PoolCleaner cleaner) {
        boolean removed = cleaners.remove(cleaner);
        if (removed) {
            cleaner.cancel();
            if (poolCleanTimer != null) {
                poolCleanTimer.purge();
                if (cleaners.isEmpty()) {
                    poolCleanTimer.cancel();
                    poolCleanTimer = null;
                }
            }
        }
    }

    // Testing use only
    /**
     * Returns an unmodifiable set of all registered pool cleaner tasks.
     *
     * @return the set of pool cleaner timer tasks
     */
    public static synchronized Set<TimerTask> getPoolCleaners() {
        return Collections.<TimerTask>unmodifiableSet(cleaners);
    }

    /**
     * Returns the current pool version counter. This counter is incremented when connections are purged, allowing
     * pooled connections to detect that they should be discarded.
     *
     * @return the current pool version
     */
    public long getPoolVersion() {
        return poolVersion.get();
    }

    // Testing use only
    /**
     * Returns the shared timer used for scheduling pool cleaner tasks.
     *
     * @return the pool cleaner timer, or {@code null} if no cleaners are registered
     */
    public static synchronized Timer getPoolTimer() {
        return poolCleanTimer;
    }

    /**
     * A scheduled task that periodically checks for abandoned connections, validates idle connections, and resizes the
     * idle pool according to the pool's configuration.
     */
    protected static class PoolCleaner extends TimerTask {
        /**
         * Weak reference to the connection pool being monitored.
         */
        protected WeakReference<ConnectionPool> pool;

        /**
         * Sleep interval in milliseconds between cleaner runs.
         */
        protected long sleepTime;

        /**
         * Creates a new pool cleaner task.
         *
         * @param pool      the connection pool to clean
         * @param sleepTime the interval in milliseconds between cleanups
         */
        PoolCleaner(ConnectionPool pool, long sleepTime) {
            this.pool = new WeakReference<>(pool);
            this.sleepTime = sleepTime;
            if (sleepTime <= 0) {
                log.warn("Database connection pool evicter thread interval is set to 0, defaulting to 30 seconds");
                this.sleepTime = 1000 * 30;
            } else if (sleepTime < 1000) {
                log.warn("Database connection pool evicter thread interval is set to lower than 1 second.");
            }
        }

        @Override
        public void run() {
            ConnectionPool pool = this.pool.get();
            if (pool == null) {
                stopRunning();
            } else if (!pool.isClosed()) {
                try {
                    if (pool.getPoolProperties().isRemoveAbandoned() ||
                            pool.getPoolProperties().getSuspectTimeout() > 0) {
                        pool.checkAbandoned();
                    }
                    // The eviction predicate in checkIdle() compares
                    // against the total pool size, not the idle count: a
                    // busy pool can be over the min-idle floor while idle
                    // stays at or below it
                    if (pool.getPoolProperties().getMinIdle() < pool.getSize()) {
                        pool.checkIdle();
                    }
                    if (pool.getPoolProperties().isTestWhileIdle()) {
                        pool.testAllIdle(false);
                    } else if (pool.getPoolProperties().getMaxAge() > 0) {
                        pool.testAllIdle(true);
                    }
                } catch (Exception x) {
                    log.error(x.toString(), x);
                }
            }
        }

        /**
         * Starts the pool cleaner by registering it with the shared timer.
         */
        public void start() {
            registerCleaner(this);
        }

        /**
         * Stops the pool cleaner by unregistering it from the shared timer.
         */
        public void stopRunning() {
            unregisterCleaner(this);
        }
    }
}

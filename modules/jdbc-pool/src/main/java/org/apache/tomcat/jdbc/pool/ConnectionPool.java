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
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
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
import org.apache.tomcat.jdbc.pool.interceptor.QueryTimeoutInterceptor;
import org.apache.tomcat.jdbc.pool.interceptor.StatementCache;
import org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer;
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

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
     * The longest single park of a long wait on a queue that cannot be told to fail its waiters, so that pool
     * closure is noticed within a slice instead of only when the wait expires.
     */
    private static final long WAIT_SLICE_MILLIS = 500;

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
     * Contains all the connections that are in use. Unbounded: membership is bounded by the maxActive/size
     * bookkeeping, not by the queue capacity.
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
     * The pool name recorded when this pool started, written by {@link #init(PoolConfiguration)} before anything is
     * registered for the pool and never rewritten. The {@code name} property is mutable at runtime - the pool's own
     * JMX exposes a {@code setName} that rewrites it on a running pool - while the registrations made for a pool
     * (the query statistics and, for the reporting interceptors, the MBean and the broadcaster published under the
     * name) stay keyed by the name the pool ran under when they were made. Resolving one of those registrations
     * through the live property would move it away from a renamed pool: the renamed pool would book nothing and
     * report a false "closed or abandoned" warning on every execution while the entry published at start would
     * outlive every close, pinned by the registration that could no longer be found to release it. Interceptors
     * that hold per-name state therefore resolve it through this record rather than through {@link #getName()}.
     */
    private volatile String registrationName;

    /**
     * The interceptor definitions that {@link #init(PoolConfiguration)} told about the pool starting, recorded
     * before the first of them is instantiated so a start that fails partway through still knows what it told.
     * The close delivers {@link JdbcInterceptor#poolClosed(ConnectionPool)} to these definitions as configured
     * then, not to whatever the {@code jdbcInterceptors} property happens to list by the time the pool comes down
     * - that property is mutable at runtime through {@link PoolProperties#setJdbcInterceptors}. Telling the
     * start-time definitions is what pairs the two lifecycle notifications: a definition removed from the property
     * while the pool ran released nothing under the close-the-current-list reading, leaving whatever its start
     * registered - the per-name query statistics and, for the reporting interceptors, the published MBean - behind
     * for the life of the JVM. Definitions the property gained after the start are told about the close as well:
     * a borrow served through such a definition may have registered state under the pool's name that only a close
     * releases, even though the definition never saw the start.
     */
    private volatile PoolProperties.InterceptorDefinition[] startedInterceptors;

    /**
     * The interceptor definitions a connection chain of this pool was actually built from. The close pairs with
     * {@link #startedInterceptors} and with the definitions the {@code jdbcInterceptors} property lists at close
     * time, but neither of those covers a definition the property gained after the start and lost again before the
     * close: it never saw the start to be recorded there, and by the close it is gone from the list the close reads
     * - yet a chain was built from it, and what a borrow through that chain registered under the pool's name (the
     * statistics and, for the reporting interceptors, the published MBean) is only ever taken down by a close.
     * Recording the definitions at chain construction is what lets the close find that third set. Entries are the
     * small configuration objects the property parses, one per definition per reconfiguration of a live pool; a
     * restarted pool is a new instance pairing over its own generation.
     */
    private final Set<PoolProperties.InterceptorDefinition> chainDefinitions =
            ConcurrentHashMap.newKeySet();

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

    // Guards the one-per-pool warning that a borrow carried credentials the
    // configuration will not honour, mirroring how DataSourceProxy issues the
    // same warning at the application surface.
    private final AtomicBoolean ignoredBorrowCredentialsWarned = new AtomicBoolean(false);

    /**
     * Instantiate a connection pool. This will create connections if initialSize is larger than 0. The
     * {@link PoolProperties} should not be reused for another connection pool.
     *
     * @param prop PoolProperties - all the properties for this connection pool
     *
     * @throws SQLException Pool initialization error
     */
    public ConnectionPool(PoolConfiguration prop) throws SQLException {
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
        // Hold a wait count across the whole attempt, including the queue
        // registration below: the release of a destroyed connection decides
        // whether to wake anybody from the wait counter and the waiters
        // registered on the queue, and between a failed no-wait attempt here
        // and that registration both of them read empty. A release landing
        // in that gap would find no waiter to wake and park this future
        // forever on a slot its own failed attempt could not take. While the
        // count is held, such a release instead queues the slot, which the
        // registration below - polling the queue first - then takes.
        waitcount.incrementAndGet();
        try {
            try {
                PooledConnection pc = borrowConnection(0, null, null);
                if (pc != null) {
                    return new ConnectionFuture(pc);
                }
            } catch (PoolExhaustedException x) {
                // A wait of zero is only refused with the no-wait variant of
                // this exception, so the type itself is the "pool is empty
                // right now" signal that this path converts into an async
                // wait. Discriminating on the type rather than on a marker
                // inside the localized message keeps the conversion alive
                // when the message is reworded or translated. Every other
                // SQLException this attempt can throw - a closed pool, an
                // interrupted wait, a validation or connect failure - is a
                // failure of the borrow itself and propagates from the
                // method to the caller untouched.
            }
            // We can only retrieve a future if the underlying queue supports it.
            if (idle instanceof FairBlockingQueue<?>) {
                Future<PooledConnection> pcf = ((FairBlockingQueue<PooledConnection>) idle).pollAsync();
                if (isClosed() && pcf.isCancelled()) {
                    // The pool was closed between the borrow attempt above and
                    // the registration of the waiter: close() fails every
                    // waiter the queue holds and a later poll cannot register
                    // one, so this future will never hand out a connection.
                    // Reject the borrow the same way a synchronous borrow on a
                    // closed pool does, rather than handing out a future that
                    // only ever fails with a cancellation.
                    throw new SQLException(MessageFactory.get("connectionPool.poolClosed"));
                }
                return new ConnectionFuture(pcf);
            } else {
                // The pattern is written with MessageFormat escapes like every
                // other key of the bundle, so it is fetched through the
                // formatting overload: the no-arg lookup would hand the raw
                // pattern to the application, doubled quotes and all
                throw new SQLException(MessageFactory.get("connectionPool.poolMisconfigured",
                        new Object[0]));
            }
        } finally {
            waitcount.decrementAndGet();
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
     * Returns the name this pool was registered under when it started. The registrations made for a pool - its
     * query statistics and the monitoring MBean the reporting interceptors publish under the name - are keyed by
     * this name, not by the live {@code name} property, which {@link #getName()} returns and which management can
     * rewrite on a running pool: a rename then changes what the pool calls itself and moves nothing that was
     * registered under the old name.
     *
     * @return the name recorded at pool start, or {@code null} while the pool has not gotten far enough into its
     *             start to record one, or if it carried no name when it started
     */
    public String getRegistrationName() {
        return registrationName;
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
            // A connection registers its MBean when its physical session is
            // established, and the pool sweeps the connections it already
            // holds when its own MBean is published. JMX is best effort, so a
            // registration that neither path made is not retried on the borrow:
            // a borrow has no business failing, or doing extra work, because a
            // management channel is unavailable.
            //
            // Build the proxy handler
            handler = new ProxyConnection(this, con, getPoolProperties().isUseEquals());
            // Set up the interceptor chain. The property is mutable on a live
            // pool, so a malformed value is a failure of this borrow that must
            // still give the connection back.
            PoolProperties.InterceptorDefinition[] proxies;
            try {
                proxies = getPoolProperties().getJdbcInterceptorsAsArray();
            } catch (Throwable x) {
                ExceptionUtils.handleThrowable(x);
                SQLException sx = new SQLException(MessageFactory.get("connectionPool.unableToParseInterceptorChain"));
                sx.initCause(x);
                abandonSetup(con, sx, false);
                throw sx;
            }
            for (int i = proxies.length - 1; i >= 0; i--) {
                // Record what this chain was built from: the definitions the
                // close has to pair with are the ones a chain ran, not only
                // the ones the property lists when the pool comes down
                chainDefinitions.add(proxies[i]);
                try {
                    JdbcInterceptor interceptor = proxies[i].getInterceptorClass().getConstructor().newInstance();
                    interceptor.setProperties(proxies[i].getProperties());
                    interceptor.setNext(handler);
                    handler = interceptor;
                } catch (Throwable x) {
                    ExceptionUtils.handleThrowable(x);
                    SQLException sx = new SQLException(MessageFactory.get("connectionPool.unableToInstantiateInterceptorChain"));
                    sx.initCause(x);
                    // Nothing has been done to the connection, it can go back to the pool
                    abandonSetup(con, sx, false);
                    throw sx;
                }
            }
            con.setHandler(handler);
        }
        // Reset the chain in the order the interceptors are declared, for new and cached handlers alike,
        // picking up the chain's tail on the way: the ProxyConnection at the bottom has to be told which
        // handle this borrow is served under, and this walk is already visiting every link of the chain
        // with the borrow not yet started - the one point where the tail can be armed before any call of
        // this borrow can run.
        JdbcInterceptor next = handler;
        ProxyConnection tail = null;
        try {
            while (next != null) {
                if (next instanceof ProxyConnection) {
                    tail = (ProxyConnection) next;
                }
                next.reset(this, con);
                next = next.getNext();
            }
        } catch (Throwable x) {
            ExceptionUtils.handleThrowable(x);
            SQLException sx = new SQLException(MessageFactory.get("connectionPool.unableToResetInterceptor",
                    (next != null ? next.getClass().getName() : "unknown")));
            sx.initCause(x);
            // The state of the connection is unknown, discard it so its partially reset chain is dropped
            abandonSetup(con, sx, true);
            throw sx;
        }
        if (tail == null) {
            // Unreachable by construction: the chain is built with a ProxyConnection at its bottom and
            // no path removes it. It is checked because a borrow without the tail could not be armed at
            // all - nothing in its chain would know the handle it is served under, and every call that
            // later reached the bottom of this chain would be refused as a foreign borrow - so the
            // setup fails as the failed setup it is, and the half-armed chain goes out of service with
            // the connection rather than handing out a handle that can never dispatch.
            SQLException sx = new SQLException(
                    MessageFactory.get("connectionPool.interceptorChainHasNoProxyConnection"));
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
            // Tell the bottom of the chain which handle this borrow is served under. The chain is shared
            // between the borrows of this pooled connection and only the handle is per-borrow: a call left
            // parked in an interceptor above the tail descends carrying the handle of its own borrow, and
            // the tail can tell the current borrow from a borrow that has ended - even after a clean
            // recycle re-armed the session field - only by that identity. Recorded before the handle
            // exists for anyone else: no call of this borrow can precede this point, and until the next
            // borrow's reset clears it again, the tail refuses every other handle as an ended borrow.
            tail.setBorrowHandle(connection);
            // The handle exists and the borrow is about to start: send the
            // JDBC 4.3 hint that opens the request, so a driver that
            // optimises per-request session handling knows the unit of work
            // begins here. The matching endRequest() is sent at the return,
            // in returnConnection().
            invokeRequestHint(BEGIN_REQUEST, con.getConnection());
            return connection;
        } catch (Throwable x) {
            ExceptionUtils.handleThrowable(x);
            SQLException s = new SQLException();
            s.initCause(x);
            // The reset walk above has already armed every interceptor of the chain for this borrow, and from here
            // no close() will ever travel that chain - the handle was never handed out - while the return path does
            // not walk the chain either. The connection itself is healthy; it is the borrow state that is unusable,
            // and there is no chain-wide unwind that is safe for arbitrary interceptors, as each keys its disposal
            // on the reset(null, null) call the release's teardown walk makes (StatementCache.reset is one).
            // So the half-armed chain goes out of service with the connection, the posture the two catches above
            // take, rather than being parked idle armed for a borrow that never started.
            abandonSetup(con, s, true);
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
            log.warn(MessageFactory.get("connectionPool.requestHintRefused", hint.getName()),
                    x.getTargetException());
        } catch (ReflectiveOperationException | IllegalArgumentException x) {
            log.warn(MessageFactory.get("connectionPool.unableToSendRequestHint", hint.getName()), x);
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
        // Publish the closure: operations that observe it fail fast - this
        // write itself blocks nothing, the detailed comment below covers
        // what each kind of concurrent operation does once it sees it
        this.closed = true;
        // Stop background thread
        terminatePoolCleaner();

        // Fail the waiters registered on a fair idle queue. A plain queue has
        // no such lever; its long waits are taken in slices instead, so the
        // borrow loop observes the closure on its own.
        if (idle instanceof FairBlockingQueue<?>) {
            ((FairBlockingQueue<PooledConnection>) idle).cancelWaiters();
        }

        // Release all idle connections
        BlockingQueue<PooledConnection> pool = (!idle.isEmpty()) ? idle : (force ? busy : idle);
        boolean interrupted = false;
        while (!pool.isEmpty()) {
            try {
                PooledConnection con = pool.poll(1000, TimeUnit.MILLISECONDS);
                while (con != null) {
                    // A forced shutdown releases busy connections the same way
                    // as idle ones; it does not record an abandonment.
                    try {
                        release(con);
                    } catch (Throwable x) {
                        // One failing connection must not leave the
                        // remaining idle and busy connections undisconnected
                        log.warn(MessageFactory.get("connectionPool.unableToClosePooledConnection"), x);
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
        // Tell the interceptors about the close, once per definition. The
        // definitions from the start come first, then the current list, then
        // the definitions the connection chains were actually built from, so a
        // definition added or removed after the start is still covered.
        PoolProperties.InterceptorDefinition[] started = startedInterceptors;
        if (started != null) {
            for (int i = 0; i < started.length; i++) {
                informInterceptorOfPoolClosure(started[i]);
            }
        }
        PoolProperties.InterceptorDefinition[] proxies;
        try {
            proxies = getPoolProperties().getJdbcInterceptorsAsArray();
        } catch (Throwable x) {
            // A malformed list must not abort the close or skip the
            // notifications already recorded.
            ExceptionUtils.handleThrowable(x);
            log.warn(MessageFactory.get("connectionPool.unableToParseInterceptorChain"), x);
            proxies = new PoolProperties.InterceptorDefinition[0];
        }
        for (int i = 0; i < proxies.length; i++) {
            if (!isToldAlready(proxies[i], started)) {
                informInterceptorOfPoolClosure(proxies[i]);
            }
        }
        for (PoolProperties.InterceptorDefinition chainDefinition : chainDefinitions) {
            if (!isToldAlready(chainDefinition, started) && !isToldAlready(chainDefinition, proxies)) {
                informInterceptorOfPoolClosure(chainDefinition);
            }
        }
    }


    /**
     * Delivers {@link JdbcInterceptor#poolClosed(ConnectionPool)} to a throwaway instance of the given
     * definition: the instance shares no state with the chains the connections run or with the instance the
     * start told, so what the callback releases has to be reachable from the definition's configuration and the
     * closing pool. A definition whose class cannot be instantiated, and an interceptor that fails the callback,
     * is reported at debug level and the close moves on.
     *
     * @param definition the interceptor definition to tell about the close
     */
    private void informInterceptorOfPoolClosure(PoolProperties.InterceptorDefinition definition) {
        try {
            JdbcInterceptor interceptor = definition.getInterceptorClass().getConstructor().newInstance();
            interceptor.setProperties(definition.getProperties());
            interceptor.poolClosed(this);
        } catch (Throwable x) {
            ExceptionUtils.handleThrowable(x);
            if (log.isDebugEnabled()) {
                log.debug("Unable to inform interceptor of pool closure.", x);
            }
        }
    }


    /**
     * Whether the close of the given definition is already covered by a set of definitions the close has told (or
     * is telling): the very definition object in that set, or one configured the same way. The parsed list is cached
     * and only re-parsed when the property is rewritten, so an unchanged list answers by object identity alone.
     *
     * @param definition the definition to check
     * @param told       the definitions already covered by one set of the close's notifications, or {@code null}
     *            when that set does not exist - a pool whose start told nothing
     *
     * @return {@code true} if the close of this definition is already covered by the given set
     */
    private static boolean isToldAlready(PoolProperties.InterceptorDefinition definition,
            PoolProperties.InterceptorDefinition[] told) {
        if (told == null) {
            return false;
        }
        for (int i = 0; i < told.length; i++) {
            if (told[i] == definition || isConfiguredTheSame(told[i], definition)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Whether two definitions name one interceptor class and give it one set of properties: the same property
     * names carrying the same values. Interceptor properties carry no equality of their own, so the values are
     * compared directly.
     *
     * @param a the first definition
     * @param b the second definition
     *
     * @return {@code true} if the two definitions configure one interceptor the same way
     */
    private static boolean isConfiguredTheSame(PoolProperties.InterceptorDefinition a,
            PoolProperties.InterceptorDefinition b) {
        if (!a.getClassName().equals(b.getClassName())) {
            return false;
        }
        Map<String, PoolProperties.InterceptorProperty> pa = a.getProperties();
        Map<String, PoolProperties.InterceptorProperty> pb = b.getProperties();
        if (pa.size() != pb.size()) {
            return false;
        }
        for (Map.Entry<String, PoolProperties.InterceptorProperty> entry : pa.entrySet()) {
            PoolProperties.InterceptorProperty other = pb.get(entry.getKey());
            if (other == null || !Objects.equals(entry.getValue().getValue(), other.getValue())) {
                return false;
            }
        }
        return true;
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

        // Record the name this pool starts under before its MBean or the
        // interceptors are told about the pool, so that everything the start
        // registers under the name and everything a later lookup of it
        // resolves through one record (see the field).
        registrationName = properties.getPoolName();

        // Both queues are unbounded by design: membership is bounded by the
        // maxActive/size bookkeeping, not by queue capacity.
        busy = new LinkedBlockingQueue<>();
        if (properties.isFairQueue()) {
            idle = new FairBlockingQueue<>();
        } else {
            idle = new LinkedBlockingQueue<>();
        }

        if (this.getPoolProperties().isJmxEnabled()) {
            createMBean();
        }

        // Parse the interceptor definitions and tell a throwaway instance of
        // each that the pool has started. These instances get no connection.
        PoolProperties.InterceptorDefinition[] proxies = getPoolProperties().getJdbcInterceptorsAsArray();
        startedInterceptors = proxies;
        for (int i = 0; i < proxies.length; i++) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Creating interceptor instance of class:" + proxies[i].getInterceptorClass());
                }
                JdbcInterceptor interceptor = proxies[i].getInterceptorClass().getConstructor().newInstance();
                interceptor.setProperties(proxies[i].getProperties());
                interceptor.poolStarted(this);
            } catch (Throwable x) {
                ExceptionUtils.handleThrowable(x);
                log.error(MessageFactory.get("connectionPool.unableToInformInterceptorOfPoolStart"), x);
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
        // Every interceptor class has now been loaded and instantiated above,
        // so the parsed definitions can be compared against each other. The
        // relative order of two interceptors in the chain is not something a
        // single interceptor can see; only the pool holds the ordered list.
        checkInterceptorOrder(proxies);

        // Initialize the pool with its initial set of members
        // The count is bounded at the reading that consumes it, not only in
        // the check: a writer of this attribute holds no monitor the
        // construction takes, so a store can land between the check above
        // (which repairs and reads) and this allocation (which builds).
        PooledConnection[] initialPool =
                new PooledConnection[Math.max(0, poolProperties.getInitialSize())];
        try {
            for (int i = 0; i < initialPool.length; i++) {
                initialPool[i] = this.borrowConnection(0, null, null); // Don't wait, should be no contention
            }

        } catch (Throwable x) {
            ExceptionUtils.handleThrowable(x);
            log.error(MessageFactory.get("connectionPool.unableToCreateInitialConnections"), x);
            if (!poolProperties.isIgnoreExceptionOnPreLoad()) {
                org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = jmxPool;
                if (jmx != null) {
                    jmx.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.NOTIFY_INIT, getStackTrace(x));
                }
                close(true);
                if (x instanceof SQLException) {
                    throw (SQLException) x;
                }
                SQLException ex = new SQLException(x.getMessage());
                ex.initCause(x);
                throw ex;
            }
        } finally {
            // Return the members as idle to the pool. These hand-backs are
            // not counted as returns: no borrower ever received them, and
            // counting them would inflate the returned statistic for the
            // pool's whole life
            for (int i = 0; i < initialPool.length; i++) {
                if (initialPool[i] != null) {
                    try {
                        this.returnConnection(initialPool[i], false);
                    } catch (Exception x) {
                        /* NOOP */}
                }
            }
        }

        // Arm the sweeper last: an interceptor's poolStarted() callback and the
        // initial pre-load can still change its configuration while the pool is
        // being built and is not yet published.
        try {
            initializePoolCleaner(properties);
        } catch (Throwable x) {
            // The constructor has already acquired resources that no caller can
            // reach once it fails, so close the partially built pool first.
            ExceptionUtils.handleThrowable(x);
            log.error(MessageFactory.get("connectionPool.unableToInitializePoolCleaner"), x);
            close(true);
            SQLException ex = new SQLException(x.getMessage());
            ex.initCause(x);
            throw ex;
        }

        closed = false;
    }

    /**
     * Warns once at pool start when the configured interceptor order defeats one of the pool's own interceptors. A
     * {@link org.apache.tomcat.jdbc.pool.interceptor.StatementCache} hit is served without invoking the rest of the
     * chain, and a statement recycled into the cache carries query timeout zero, so a
     * {@link org.apache.tomcat.jdbc.pool.interceptor.QueryTimeoutInterceptor} listed after the cache never sees the
     * reuse and every cached statement executes with no timeout. And because a parked statement is deliberately left
     * open, a {@link StatementFinalizer} listed after the cache still tracks that statement - it is reached only by the
     * cache's own handle, never by the finalizer's close-aware proxy, so the close that parked it is invisible to the
     * tracker - and the tracker's close on connection return then closes the parked statement out from under a live
     * cache entry, leaving the next borrower with a closed statement. The chain is built back-to-front in
     * {@link #setupConnection(PooledConnection)}, making the first-listed interceptor the outermost one, so both the
     * timeout interceptor and the finalizer must be listed before the cache. This detection only reports the
     * misconfiguration; the cache's own defence against the tracker beneath it is separate and does not depend on
     * this check. A subclass of one of these interceptors inherits its job whatever it is named, so each role is
     * recognised with {@link Class#isAssignableFrom} rather than by exact class - the same convention the pool's
     * own {@code hasConnectionStateInterceptor} check follows.
     *
     * @param proxies the parsed interceptor definitions, in configuration order
     */
    private void checkInterceptorOrder(PoolProperties.InterceptorDefinition[] proxies) {
        int firstCache = -1;
        int firstTimeout = -1;
        int lastFinalizer = -1;
        for (int i = 0; i < proxies.length; i++) {
            try {
                Class<? extends JdbcInterceptor> interceptorClass = proxies[i].getInterceptorClass();
                // The roles are not exclusive: one class may inherit the job
                // of more than one of them, and then it counts in all of the
                // roles it fills, exactly as a chain listing them separately
                // would.
                if (StatementCache.class.isAssignableFrom(interceptorClass)) {
                    if (firstCache < 0) {
                        firstCache = i;
                    }
                }
                if (QueryTimeoutInterceptor.class.isAssignableFrom(interceptorClass)) {
                    if (firstTimeout < 0) {
                        firstTimeout = i;
                    }
                }
                if (StatementFinalizer.class.isAssignableFrom(interceptorClass)) {
                    lastFinalizer = i;
                }
            } catch (ReflectiveOperationException | LinkageError x) {
                // The pool-start loop above has already loaded and instantiated every one of these classes, so
                // resolution cannot fail here. This check is diagnostic only; if it ever does fail, say nothing
                // rather than disturbing a pool that started successfully.
                return;
            }
        }
        // The two checks read the same positions differently, because the two
        // interceptors are defeated differently. A timeout interceptor listed
        // before the first cache protects every cache hit: it sees the
        // statement object the innermost cache hands back - hits included -
        // and re-applies the timeout there, so one further down the chain is
        // redundant but harmless and no warning belongs to that chain. A
        // chain with no timeout interceptor at all is nothing to warn about
        // either: firstTimeout stays -1. The finalizer, by contrast, is
        // defeated by any finalizer beneath the cache no matter what is
        // listed above it, so its check looks at the last one.
        if (firstCache >= 0 && firstTimeout > firstCache) {
            log.warn(MessageFactory.get("connectionPool.statementCacheBeforeQueryTimeout"));
        }
        // A finalizer beneath some cache is exactly lastFinalizer > firstCache, so this catches the case where
        // the finalizer is listed on both sides of the cache (firstFinalizer would sit before it and stay silent).
        if (firstCache >= 0 && lastFinalizer > firstCache) {
            log.warn(MessageFactory.get("connectionPool.statementCacheBeforeStatementFinalizer"));
        }
    }

    /**
     * Validates and corrects pool configuration values. Ensures that maxActive, initialSize, minIdle, maxIdle, and
     * related values are consistent.
     * <p>
     * A running pool's writers must not call this alone: the correction can change the eviction interval the cleaner
     * runs on, and a change made outside this object's monitor can interleave with another surface's write between
     * the correction and the cleaner reconciliation. See {@link #applyConfigurationChange(PoolConfiguration)}.
     *
     * @param properties the pool configuration to validate and correct
     */
    public void checkPoolConfiguration(PoolConfiguration properties) {
        if (properties.getMaxActive() < 1) {
            log.warn(MessageFactory.get("connectionPool.maxActiveTooSmall",
                    Integer.valueOf(PoolProperties.DEFAULT_MAX_ACTIVE)));
            properties.setMaxActive(PoolProperties.DEFAULT_MAX_ACTIVE);
        }
        if (properties.getInitialSize() < 0) {
            // The count of connections to pre-load is used to size an array while
            // the pool is being built, so a negative one is not a number the pool
            // can work with at all. Corrected before the rule below reads it.
            log.warn(MessageFactory.get("connectionPool.initialSizeTooSmall"));
            properties.setInitialSize(0);
        }
        if (properties.getMinIdle() < 0) {
            log.warn(MessageFactory.get("connectionPool.minIdleNegative"));
            properties.setMinIdle(0);
        }
        if (properties.getMaxIdle() < 0) {
            log.warn(MessageFactory.get("connectionPool.maxIdleNegative"));
            properties.setMaxIdle(0);
        }
        if (properties.getMaxActive() < properties.getInitialSize()) {
            log.warn(MessageFactory.get("connectionPool.initialSizeTooLarge",
                    Integer.valueOf(properties.getMaxActive())));
            properties.setInitialSize(properties.getMaxActive());
        }
        if (properties.getMinIdle() > properties.getMaxActive()) {
            log.warn(MessageFactory.get("connectionPool.minIdleTooLarge", Integer.valueOf(properties.getMaxActive())));
            properties.setMinIdle(properties.getMaxActive());
        }
        if (properties.getMaxIdle() > properties.getMaxActive()) {
            log.warn(MessageFactory.get("connectionPool.maxIdleTooLarge", Integer.valueOf(properties.getMaxActive())));
            properties.setMaxIdle(properties.getMaxActive());
        }
        if (properties.getMaxIdle() < properties.getMinIdle()) {
            log.warn(MessageFactory.get("connectionPool.maxIdleTooSmall", Integer.valueOf(properties.getMinIdle())));
            properties.setMaxIdle(properties.getMinIdle());
        }
        if (properties.getMaxAge() > 0 && properties.isPoolSweeperEnabled() &&
                properties.getTimeBetweenEvictionRunsMillis() > properties.getMaxAge()) {
            log.warn(MessageFactory.get("connectionPool.timeBetweenEvictionRunsTooLarge",
                    Long.valueOf(properties.getMaxAge())));
            properties.setTimeBetweenEvictionRunsMillis((int) properties.getMaxAge());
        }
    }

    /**
     * Validates a running pool's configuration and brings the pool cleaner in line with the result as one step under
     * this pool's monitor.
     * <p>
     * The two halves belong together: the validation corrects values the caller did not write - the eviction interval
     * among them - and the cleaner's period is fixed when its task is built, so validating without then looking the
     * cleaner over leaves a task on the interval the correction replaced. Running them under one monitor is what makes
     * the pair atomic against the other management surface: the data source holds its own monitor while it writes the
     * shared configuration, and the pool's MBean holds none, so this object's monitor is the only thing both writers
     * are certain to serialise on.
     *
     * @param properties the pool configuration to validate and reconcile the cleaner with
     */
    public synchronized void applyConfigurationChange(PoolConfiguration properties) {
        checkPoolConfiguration(properties);
        reconcilePoolCleaner(properties);
    }

    /**
     * Starts the pool cleaner (evicter) thread if pool sweeper is enabled. A cleaner that is still running from a
     * previous start is terminated first, so this call never leaves a replaced task registered on the shared timer
     * without a reference to stop it. A pool that has been closed never gets a cleaner at all.
     * <p>
     * {@link #close(boolean)} sets the closed flag before it terminates the cleaner, and that termination and the
     * registration below both run under this object's monitor, so whichever of the two calls happens last wins: a
     * close that arrives first is seen here and no task is created, a close that arrives afterwards terminates the
     * task that was created. Without the check, a thread writing a sweep attribute concurrently with the close -
     * either of the configuration setters that re-arm the sweeper, or the JMX resource attributes - could register
     * a task for a pool that had already released its connections. Nothing else ever unregisters such a task: it
     * keeps the shared cleaner timer, and the thread it owns, alive for the lifetime of the class loader that
     * loaded the pool. The task itself does no work, as its run method checks the closed flag, but it is never
     * removed from the timer.
     *
     * @param properties the pool configuration containing sweeper settings
     */
    public synchronized void initializePoolCleaner(PoolConfiguration properties) {
        // If the evictor thread is supposed to run, start it now
        if (properties.isPoolSweeperEnabled() && !closed) {
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
     * Arms a replacement for this pool's maintenance task when the task the pool still holds is the one that lost the
     * shared timer, and does nothing when it is not. A pool whose cleaner has meanwhile been armed by a configuration
     * write is served by that task, and one that has been stopped - by a close, or by a configuration that no longer
     * asks for a sweeper - is served by none on purpose; reviving the spent task would either duplicate the
     * maintenance work for the pool's lifetime or arm a pool that has given its connections back.
     *
     * @param stranded the task that was scheduled on the shared timer that died
     */
    synchronized void rearmPoolCleaner(PoolCleaner stranded) {
        if (poolCleaner != stranded) {
            return;
        }
        // The spent task goes first whatever the configuration now asks for: its
        // registry entry would otherwise stand for a task that can never run
        // again, and no close would hand it back. What is armed after it is what
        // the configuration asks for now, so a pool that has closed in the
        // meantime, or has had its sweeper switched off, is left as it is.
        terminatePoolCleaner();
        initializePoolCleaner(getPoolProperties());
    }

    /**
     * Brings the pool cleaner in line with a configuration: what is running now is
     * compared against what the configuration asks for, including the interval the
     * running task was built with, and only a disagreement is acted on. Nothing the
     * caller noticed before it wrote is taken into account, because a change to a
     * pool is something an operator can make from either of two surfaces at the
     * same moment, and what an operator saw on the way in is not what the other
     * operator left behind.
     * <p>
     * Both writers of the sweeper's attributes hand their change here rather than
     * deciding it themselves - the data source and this pool's own MBean - because
     * this object's monitor is the only thing the two are certain to serialise on:
     * the data source holds its own monitor, the MBean holds nothing, and they write
     * the same configuration through whichever of them is reached for. Whether the
     * sweeper is wanted, and what interval it wants, are therefore both read here,
     * under that monitor, together with the task that is registered; whoever decides
     * last sees every write that any setter has made, and no arm can slip between one
     * writer's reading and another's stopping of the task that arm registered. A
     * writer of an attribute the configuration check covers ends here as well, even
     * when that attribute is not one the sweeper computes from: the check can move
     * the interval the sweeper runs on, and a task's period is fixed when it is
     * built, so checking without then looking the cleaner over is what leaves a task
     * running on an interval the configuration has already replaced.
     * <p>
     * The pool's creation uses this too, after it has validated the configuration
     * and published the pool. Its own arming happened while the pool was still
     * under construction, so a change that landed between that arming and the
     * publication is something the arming never saw and that no setter could
     * report to the pool; see {@link DataSourceProxy} for that window. A change
     * that moved the interval is expected here rather than surprising: the
     * validation can shorten it to the max age, which leaves the task the
     * constructor armed running on the longer one.
     *
     * @param properties the pool configuration containing sweeper settings
     */
    public synchronized void reconcilePoolCleaner(PoolConfiguration properties) {
        if (!properties.isPoolSweeperEnabled()) {
            terminatePoolCleaner();
        } else if (poolCleaner == null ||
                poolCleaner.sleepTime != properties.getTimeBetweenEvictionRunsMillis()) {
            // Arm it, or replace the running task: its period is fixed at
            // construction, so it re-reads it only from a new one
            initializePoolCleaner(properties);
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
                log.warn(MessageFactory.get("connectionPool.connectionAbandoned", con, trace));
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
     * abandoning the connection, this marks the suspect flag on the {@link PooledConnection} and reports it through the
     * JMX suspect notification - in every configuration, whatever later reporting keys off that flag must see it. A
     * warning log on top of that is only produced when logAbandoned is set.
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
            // The guard above runs unlocked, and checkAbandoned() has two
            // drivers - the cleaner timer and a JMX-invoked one - so both
            // can pass it for the same connection before either marks it.
            // Re-check under the lock that the marking happens on, or the
            // second driver logs and notifies a connection the first has
            // already reported.
            if (con.isSuspect()) {
                return;
            }
            String trace = con.getStackTrace();
            if (getPoolProperties().isLogAbandoned()) {
                log.warn(MessageFactory.get("connectionPool.connectionSuspect",
                        con, Long.valueOf(System.currentTimeMillis() - con.getTimestamp()), trace));
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
                // The one teardown reset, delivered after the slot has been
                // freed and before the wake-up placeholder is offered; the
                // handler is then dropped without resetting it again
                finalize(con);
                con.clearHandler();
                // Counted with the destruction, not with the call: a
                // connection the abandon path already released can reach
                // this method once more - a return whose busy.remove lost
                // that race ends here as well - and only the call that
                // actually destroyed the connection closes the bookkeeping
                // one the creation opened.
                releasedCount.incrementAndGet();
            }
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
        // The wait counter also sees only synchronous borrowers: a caller
        // registered on a fair queue by a timed or asynchronous poll waits
        // where the counter cannot reach it, and is a prospective consumer
        // the queue itself can report.
        // The destroy above and the slot a placeholder stands for are also
        // separate steps: a borrower racing in between can claim the freed
        // slot for itself, and claiming a slot that is no longer free would
        // stand the pool up over maxActive. When the claim fails there is no
        // free slot left to wake anyone for; the waiter is served by the
        // borrower that won the slot once it returns its connection, or by
        // that borrower's own release should its creation fail.
        if (destroyed && !isClosed() && (waitcount.get() > 0 || queueWaiterPresent()) && claimSlot()) {
            PooledConnection placeholder = create(false);
            // A placeholder that has never been borrowed carries no borrow or
            // return timestamp, and the idle sweep measures age from that
            // timestamp. Left at its epoch default the age rule finds it older
            // than minEvictableIdleTimeMillis the instant it is offered, so a
            // sweep racing its still-coming waiter would release it as an
            // expired idle connection - a phantom release of a slot that
            // stands for a live one. Stamp the moment it entered the queue so
            // it ages like the connection it stands in for.
            placeholder.setTimestamp(System.currentTimeMillis());
            if (!idle.offer(placeholder)) {
                size.decrementAndGet(); // hand the claimed slot back
                log.warn(MessageFactory.get("connectionPool.failedToAddNewConnection"));
            } else if (isClosed() && idle.remove(placeholder)) {
                // The closed check that gated this placeholder and the offer
                // below are not one atomic step: a close arriving in between
                // has drained the queue once already, and this placeholder
                // would park in it holding its claimed slot, invisible to the
                // drain that has passed and to every later sweep, which a
                // closed pool no longer runs. Take the placeholder back out of
                // a queue that has been drained past and hand its slot back.
                // If it is no longer there, a registered waiter took it in a
                // hand-off, and that taker's own setup re-checks the closure.
                size.decrementAndGet(); // hand the claimed slot back
            } else {
                // The placeholder has entered circulation, so count it as a
                // creation. Every way it leaves circulation - a waiter that
                // re-establishes it and later returns it, an idle sweep that
                // finds it unusable, a close drain - releases it through
                // release(), and that released count needs this matching
                // created count to stay reconcilable with it: a placeholder
                // that is only ever released would run the released count
                // permanently ahead of the created one, by one per
                // placeholder, leaving monitoring unable to read the pair.
                createdCount.incrementAndGet();
            }
        }
    }

    /**
     * Claims one of the pool's {@link PoolProperties#getMaxActive()} slots for
     * a connection that is about to be put in a queue, and that without the
     * ability to run the pool over the limit: a plain increment would race a
     * borrower claiming the same slot. Unlike the borrow loop's reservation -
     * which reserves for the calling borrower itself - this claim backs a
     * wake-up placeholder whose consumer is somebody else, so it must stand
     * down when no slot is free.
     *
     * @return <code>true</code> if a slot was claimed
     */
    private boolean claimSlot() {
        int maxActive = getPoolProperties().getMaxActive();
        int current = size.get();
        while (current < maxActive) {
            if (size.compareAndSet(current, current + 1)) {
                return true;
            }
            current = size.get();
        }
        return false;
    }

    /**
     * Whether a caller is registered on the idle queue waiting for an item.
     * An asynchronous borrow registers its latch on the queue itself and
     * never passes through the pool's wait counter, so a release consulting
     * only that counter would never wake such a waiter. Only the fair queue
     * supports these registrations; a plain queue holds none.
     *
     * @return <code>true</code> if the idle queue reports a registered waiter
     */
    private boolean queueWaiterPresent() {
        return idle instanceof FairBlockingQueue<?>
                && ((FairBlockingQueue<PooledConnection>) idle).hasRegisteredWaiter();
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
            throw new SQLException(MessageFactory.get("connectionPool.poolClosed"));
        }

        if (username != null || password != null) {
            if (!poolProperties.isAlternateUsernameAllowed()) {
                // The credentials are not this pool's to honour. DataSourceProxy
                // already ignores them at the application surface; this is the
                // same contract at the pool's own, which is also reachable as
                // getConnection(user,password). Honouring them here would
                // authenticate a session as the borrower's identity and - since
                // with alternates disallowed every later borrow is served under
                // the pool's identity - hand that session to later borrowers as
                // the identity they never claimed. Dropping them before the
                // borrow decision below is what makes the decision's premise,
                // that no session carries another identity, one the pool keeps
                // rather than one it hopes for.
                if (ignoredBorrowCredentialsWarned.compareAndSet(false, true)) {
                    log.warn(MessageFactory.get("connectionPool.alternateUsernameIgnored", username));
                }
                username = null;
                password = null;
            }
        }

        // Get the current time stamp
        long now = System.currentTimeMillis();
        // See if there is one available immediately
        PooledConnection con = idle.poll();

        while (true) {
            if (con != null) {
                PooledConnection result = borrowConnection(con, username, password);
                if (result != null) {
                    return result;
                }
                // The connection was released while it sat idle, so this
                // poll got nothing to hand out; keep polling. Nothing is
                // counted here: a borrow is counted on the hand-off that
                // succeeds, in setupConnection(). The consumed connection is
                // dropped here so that "keep polling" really polls again: a
                // wait that ends only on an item would otherwise treat the
                // dead connection it already took out of the queue as still
                // pending, and spin on a full core through any later check -
                // including the closed re-check that a pool closure needs this
                // borrower to reach.
                con = null;
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
                    return createConnection(con, username, password);
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
                        size.decrementAndGet();
                    } else {
                        return createConnection(con, username, password);
                    }
                }
                // Retrieve an existing connection
                if (!(idle instanceof FairBlockingQueue) && timetowait > WAIT_SLICE_MILLIS) {
                    // A plain queue knows nothing about failing a wait that is
                    // parked in it - only the fair queue is told to terminate
                    // when the pool closes. A long wait parked here would
                    // therefore sleep through the close for its whole
                    // duration - and a wait whose timeout never runs out, or
                    // whose int-millisecond maxWait reaches into days, sleeps
                    // through it forever, parking its borrower thread and
                    // classloader past the pool's own death. Park in bounded
                    // slices instead, so the loop's own closed and timeout
                    // checks run between slices; the timeout is still measured
                    // from now, so the slicing never moves when a wait
                    // expires, it only lets a closure be noticed early.
                    while (con == null && !isClosed()) {
                        long remaining = maxWait - (System.currentTimeMillis() - now);
                        if (remaining <= WAIT_SLICE_MILLIS) {
                            if (remaining > 0) {
                                con = idle.poll(remaining, TimeUnit.MILLISECONDS);
                            }
                            break;
                        }
                        con = idle.poll(WAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS);
                    }
                } else {
                    con = idle.poll(timetowait, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException ex) {
                if (getPoolProperties().getPropagateInterruptState()) {
                    Thread.currentThread().interrupt();
                }
                SQLException sx = new SQLException(MessageFactory.get("connectionPool.poolWaitInterrupted"));
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
                throw new PoolExhaustedException(MessageFactory.get("connectionPool.poolEmptyNoWait",
                        Thread.currentThread().getName(), Integer.valueOf(busy.size())));
            }
            // We didn't get a connection, lets see if we timed out
            if (con == null) {
                if (isClosed()) {
                    // The pool closed while we waited: close() fails every
                    // waiter registered on the idle queue, and a closed pool
                    // never offers a connection to it again, so no further
                    // wait can end any differently than this one did. Fail
                    // the borrow the way a borrow that caught the closure on
                    // entry does, rather than register a wait nothing can
                    // ever serve - which for an unbounded maxWait would park
                    // this borrower for the life of the pool's classloader.
                    throw new SQLException(MessageFactory.get("connectionPool.poolClosed"));
                }
                if ((System.currentTimeMillis() - now) >= maxWait) {
                    org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = jmxPool;
                    if (jmx != null) {
                        jmx.notify(org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.POOL_EMPTY,
                                "Pool empty - timeout.");
                    }
                    throw new PoolExhaustedException(MessageFactory.get("connectionPool.poolEmptyTimeout",
                            Thread.currentThread().getName(), Long.valueOf(maxWait / 1000), Integer.valueOf(size.get()),
                            Integer.valueOf(busy.size()), Integer.valueOf(idle.size()), Long.valueOf(timetowait)));
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
     * @param notUsed  Not used; retained for API compatibility
     * @param username The user name to use for the connection
     * @param password The password for the connection
     *
     * @return a PooledConnection that has been connected
     *
     * @throws SQLException Failed to get a connection
     */
    protected PooledConnection createConnection(PooledConnection notUsed, String username, String password)
            throws SQLException {
        // No connections where available we'll create one
        PooledConnection con = create(false);
        // Counted where the pool takes the object into service, not where
        // the attempt succeeds: the error path below retires a failed
        // creation through release(), which counts it as released, and a
        // creation counted only on success would leave that release without
        // its counterpart - one per failed attempt, permanently running the
        // released count ahead of the created one.
        createdCount.incrementAndGet();
        if (username != null) {
            con.getAttributes().put(PooledConnection.PROP_USER, username);
        }
        if (password != null) {
            con.getAttributes().put(PooledConnection.PROP_PASSWORD, password);
        }
        boolean error = false;
        try {
            con.lock();
            con.connect();
            if (isClosed()) {
                // The pool was closed while this physical connection was
                // being created; the shutdown drain has already passed over
                // the queues, so neither hand the connection out nor register
                // it in busy - the error path below disconnects it
                throw new SQLException(MessageFactory.get("connectionPool.poolClosed"));
            }
            if (con.validate(PooledConnection.VALIDATE_INIT)) {
                // Stamped with the moment of the hand-off, not with the
                // borrow loop's entry: checkAbandoned() measures the checkout
                // age from this timestamp, and a connection created at the
                // end of a long wait must not be born already past the
                // abandon timeout.
                con.setTimestamp(System.currentTimeMillis());
                if (getPoolProperties().isLogAbandoned()) {
                    con.setStackTraceElements(getThreadDumpElements());
                }
                if (!busy.offer(con)) {
                    log.debug("Connection doesn't fit into busy array, connection will not be traceable.");
                } else if (isClosed()) {
                    // The closed check above and this publish are not one
                    // atomic step: a forced close may already have drained
                    // busy, and this entry would sit there unseen while the
                    // borrower is handed a connection from a dead pool.
                    // Retract the publish and fail the borrow the way the
                    // check does; the error path below disconnects the
                    // session, or finds the drain already disconnected it.
                    busy.remove(con);
                    throw new SQLException(MessageFactory.get("connectionPool.poolClosed"));
                }
                return con;
            } else {
                // Validation failed, make sure we disconnect
                // and clean up
                throw new SQLException(MessageFactory.get("connectionPool.validationQueryFailed"));
            }
        } catch (Throwable e) {
            // A non-fatal Error from a driver or wrapper (typically a
            // LinkageError from a stopped web application) is an ordinary
            // failed creation here. Guarding only Exception would skip the
            // error flag and the finally block's release, stranding the
            // connection and the maxActive slot it already owns.
            ExceptionUtils.handleThrowable(e);
            error = true;
            if (log.isDebugEnabled()) {
                // The driver's message can carry the URL and its credential,
                // so log it masked and without the raw exception.
                log.debug("Unable to create a new JDBC connection. " + e.getClass().getName() + ": " +
                        PoolProperties.maskCredentials(e.getMessage()));
            }
            if (e instanceof SQLException) {
                throw (SQLException) e;
            } else {
                SQLException ex = new SQLException(e.getMessage());
                ex.initCause(e);
                throw ex;
            }
        } finally {
            if (error) {
                release(con);
            }
            con.unlock();
        }
    }

    /**
     * Validates and configures a previously idle connection
     *
     * @param con      - the connection to validate and configure
     * @param username The user name to use for the connection
     * @param password The password for the connection
     *
     * @return a connection
     *
     * @throws SQLException if a validation error happens
     */
    protected PooledConnection borrowConnection(PooledConnection con, String username, String password)
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
            boolean credentialsDiffer = con.shouldForceReconnect(username, password);
            boolean forceReconnect = credentialsDiffer || con.isMaxAgeExpired();

            if (!con.isDiscarded() && !con.isInitialized()) {
                // Here it states that the connection not discarded, but the connection is null
                // don't attempt a connect here. It will be done during the reconnect.
                forceReconnect = true;
            }

            if (!forceReconnect) {
                if ((!con.isDiscarded()) && con.validate(PooledConnection.VALIDATE_BORROW)) {
                    // Stamp the checkout with this hand-off moment, not the
                    // borrow loop's entry: the abandoned check measures the
                    // time since here, and a borrower that queued longer
                    // than the abandon timeout must not be born abandoned.
                    con.setTimestamp(System.currentTimeMillis());
                    if (getPoolProperties().isLogAbandoned()) {
                        con.setStackTraceElements(getThreadDumpElements());
                    }
                    if (!busy.offer(con)) {
                        log.debug("Connection doesn't fit into busy array, connection will not be traceable.");
                    } else if (isClosed()) {
                        // The close this path races had already drained busy
                        // once when this offer landed, and - unlike the
                        // returning thread, which reclaims what it offered
                        // too late - this publish would hand the session to
                        // the borrower over a pool that has shut down: the
                        // return that handed this connection over lets it go
                        // precisely because it trusts the taker to see to the
                        // connection's closure. Retract the publish and
                        // retire the connection; the borrow loop's own closed
                        // re-check turns this into the failure a closed pool
                        // owes the borrower.
                        busy.remove(con);
                        release(con);
                        setToNull = true;
                        return null;
                    }
                    return con;
                }
            }
            // If we reached here, that means the connection
            // is either has another principal, is discarded or validation failed.
            // We will make one more attempt
            // in order to guarantee that the thread that just acquired
            // the connection shouldn't have to poll again.
            if (isClosed()) {
                // The pool closed between taking this connection out of the
                // idle queue and setting it up here - a close lands after
                // this path's own entry check whenever the item was already
                // in hand, queued or handed straight over a waiter's latch,
                // none of which observes the flag again. The shutdown drain
                // has passed the queues, so the reconnect below would open a
                // fresh session on a closed pool and hand it to this borrower
                // out of every shutdown path. Retire the connection instead
                // and report nothing handed out: the synchronous loop
                // re-checks the closure on its next pass and fails the
                // borrow, and an asynchronous poll publishes the failure.
                release(con);
                setToNull = true;
                return null;
            }
            // Whether the reconnect below took on a new session: only a
            // reconnect that never got that far can have left a session worth
            // keeping behind it.
            boolean reconnected = false;
            try {
                if (credentialsDiffer) {
                    // A session the database has not refused is not this
                    // borrower's to spend, so the new identity is put to the
                    // database before the session it replaces is given up.
                    con.reconnectWith(username, password);
                } else {
                    con.reconnect();
                }
                reconnected = true;
                reconnectedCount.incrementAndGet();
                int validationMode = isInitNewConnections() ? PooledConnection.VALIDATE_INIT
                        : PooledConnection.VALIDATE_BORROW;
                if (con.validate(validationMode)) {
                    // Same hand-off stamping as the validate-pass branch
                    // above: the abandoned check measures the time since this
                    // moment, not since the borrow loop's entry.
                    con.setTimestamp(System.currentTimeMillis());
                    if (getPoolProperties().isLogAbandoned()) {
                        con.setStackTraceElements(getThreadDumpElements());
                    }
                    if (!busy.offer(con)) {
                        log.debug("Connection doesn't fit into busy array, connection will not be traceable.");
                    } else if (isClosed()) {
                        // Same closed-versus-publish race as in the
                        // validate-pass branch above: retract the offer and
                        // retire the connection, and let the borrow loop's
                        // own closed re-check turn this into the failure a
                        // closed pool owes the borrower.
                        busy.remove(con);
                        release(con);
                        setToNull = true;
                        return null;
                    }
                    return con;
                } else {
                    // Validation failed.
                    throw new SQLException(MessageFactory.get("connectionPool.failedToValidateNewConnection"));
                }
            } catch (Throwable x) {
                // The module's fatal policy runs first, as everywhere else.
                // What is left is the refused borrow this guard already
                // reports: the reconnect and the validation above reach the
                // driver over a connection this borrow has claimed out of the
                // idle queue, and a throwable that unwound them now would leave
                // that connection in neither queue - invisible to the abandoned
                // check, to the sweep and to the close drain alike, and gone
                // from the budget for the life of the pool. A LinkageError is
                // an ordinary arrival behind a driver loaded or wrapped through
                // a web application class loader, and a borrow the driver could
                // not carry out is what the borrower is owed, as the checked
                // exception a borrow answers with.
                ExceptionUtils.handleThrowable(x);
                if (credentialsDiffer && !reconnected && con.isInitialized() && !con.isDiscarded()) {
                    // The attempt was refused before it gave up the session
                    // this connection was holding: that session is open, it
                    // carries its own still-valid credentials, and it belongs
                    // to the pool and to every borrower the database does not
                    // turn away. It goes back to the queue it came from
                    // instead of paying for a borrow that never used it.
                    returnToIdle(con);
                } else {
                    release(con);
                }
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
     * Hands a connection back to the idle queue after a borrow attempt that failed without using the session the
     * connection holds. The attempt neither handed the session out nor closed it, so the connection is exactly as
     * usable as it was before the attempt and the pool keeps it: a borrower the database refused is not a reason to
     * destroy a session, and the slot it stands for.
     *
     * @param con the connection to hand back to the idle queue
     */
    private void returnToIdle(PooledConnection con) {
        if (isClosed()) {
            // A close has drained the queues once already, so a session
            // handed to the idle queue now would sit there out of reach of
            // every shutdown path. Retire it instead.
            release(con);
        } else if (!idle.offer(con)) {
            release(con);
        } else if (isClosed() && idle.remove(con)) {
            // The closed check above and this offer are not one atomic step:
            // a close arriving in between has drained the queue this offer
            // then fills for the last time. Take the connection back out of a
            // queue that has already been drained past; if it is no longer
            // there, a taker has taken ownership of it and sees to its
            // closure.
            release(con);
        }
    }

    /**
     * Returns whether new connections should be initialized by invoking {@link PooledConnection#validate(int)} with
     * {@link PooledConnection#VALIDATE_INIT}.
     *
     * @return true if pool is either configured to test connections on connect or an init SQL that carries a statement
     *             has been configured
     */
    private boolean isInitNewConnections() {
        // The same reading the validation path applies to this setting: a
        // blank init SQL is the unset value, so it gives a new session
        // nothing to run. Counting the blank as a configured statement here
        // would send a freshly re-established session down the init-check
        // path - which an unset statement makes a check that runs nothing -
        // instead of the borrow check the configuration did ask for.
        return getPoolProperties().isTestOnConnect() ||
                PoolProperties.configuredStatement(getPoolProperties().getInitSQL()) != null;
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
            // Terminate the pending transaction of a returned connection
            // when the pool is configured to commit or roll back on return -
            // without that configuration the pending work is deliberately
            // left to the interceptors that own it, the ConnectionState
            // interceptor chief among them. The pool-level defaultAutoCommit
            // property must not gate this; the connection's own state is what
            // matters.
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
            log.warn(MessageFactory.get("connectionPool.unableToTerminateTransaction"), x);
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
            log.warn(MessageFactory.get("connectionPool.unableToCheckClosed"), e);
            return true;
        }
        if (isClosed()) {
            return true;
        }
        if (con.hasUnresolvedXaBranches()) {
            // A branch that was started through this session's XA resource
            // and never committed, rolled back or forgotten stays attached to
            // the physical connection, and neither the transaction
            // termination below nor a plain rollback can resolve it: pooling
            // the session would hand the branch and every database lock held
            // for it to the next borrower, while the transaction manager's
            // late commit would land on a session that does not carry it.
            // Close the session instead - the resource manager drops the
            // branch with it, and the manager's late call then fails visibly
            // against a closed session.
            log.warn(MessageFactory.get("connectionPool.unresolvedXaBranchReturned", con));
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
                    log.debug("Connection [" + con + "] expired because of maxAge, trying to reconnect");
                }
                con.reconnect();
                reconnectedCount.incrementAndGet();
                if (isInitNewConnections() && !con.validate(PooledConnection.VALIDATE_INIT)) {
                    return false;
                }
            } catch (Exception e) {
                // The pattern names a connection, so the argument is the
                // connection that expired and failed - the pool identity
                // would send operators chasing one session to the wrong
                // subject entirely
                log.error(MessageFactory.get("connectionPool.failedToReconnectExpired", con), e);
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
        returnConnection(con, true);
    }

    /**
     * Returns a connection to the pool, optionally without the returned-connection statistic.
     *
     * @param con         PooledConnection to be returned to the pool
     * @param countReturn whether this hand-back is one borrower's return to count: the pool's own preload hands back
     *                        connections no borrower ever received, and counting them would run the returned count
     *                        permanently ahead of the borrowed one
     */
    private void returnConnection(PooledConnection con, boolean countReturn) {
        if (isClosed()) {
            // If the connection pool is closed
            // close the connection instead of returning it
            release(con);
            return;
        }

        if (con != null) {
            try {
                if (countReturn) {
                    // A borrower's hand-back is the counterpart of its borrow;
                    // the pool's preload returns connections it never handed
                    // out, and counting those would run the returned count
                    // permanently ahead of the borrowed one
                    returnedCount.incrementAndGet();
                }
                con.lock();
                if (con.isSuspect()) {
                    if (poolProperties.isLogAbandoned() && log.isInfoEnabled()) {
                        log.info(MessageFactory.get("connectionPool.suspectConnectionReturned",
                                con, Long.valueOf(System.currentTimeMillis() - con.getTimestamp())));
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
                            try {
                                con.clearWarnings();
                            } catch (SQLException x) {
                                // The application is done with the session and
                                // the return must not fail over a warning chain
                                // the driver refuses to drop; this is the one
                                // caller for which a refused clear carries no
                                // outcome anyone can act on, unlike the JMX
                                // operation, which reports it to its client
                                log.warn(MessageFactory.get("pooledConnection.unableToClearWarnings"), x);
                            }
                            con.setStackTrace(null);
                            con.setTimestamp(System.currentTimeMillis());
                            // The application is done with the connection:
                            // close the JDBC 4.3 request that beginRequest()
                            // opened in setupConnection(). A connection
                            // discarded instead of going back to the pool is
                            // not hinted; it is about to be closed, which
                            // subsumes the hint.
                            invokeRequestHint(END_REQUEST, con.getConnection());
                            if (isClosed()) {
                                // The pool closed while this return was in
                                // flight: the checks above each read the flag
                                // before the driver work they gate ran, and
                                // close() drains the queues only once - an
                                // offer now would park a live session in a
                                // queue nothing will ever drain again, out of
                                // reach of every shutdown path. Retire the
                                // connection instead, the way testAllIdle()
                                // re-reads the flag before its own offer-back.
                                if (log.isDebugEnabled()) {
                                    log.debug("Connection [" + con +
                                            "] will be closed and not returned to the pool, pool closed.");
                                }
                                release(con);
                            } else if (((idle.size() >= poolProperties.getMaxIdle()) && !poolProperties.isPoolSweeperEnabled()) ||
                                    (!idle.offer(con))) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Connection [" + con +
                                            "] will be closed and not returned to the pool, idle[" + idle.size() +
                                            "]>=maxIdle[" + poolProperties.getMaxIdle() + "] idle.offer failed.");
                                }
                                release(con);
                            } else if (isClosed() && idle.remove(con)) {
                                // The closed check above and this offer are
                                // not one atomic step: a close arriving in
                                // between drains the queue this offer then
                                // fills for the last time, stranding the
                                // session out of every shutdown path. Take
                                // the connection back out of a queue that
                                // has already been drained past. If it is
                                // no longer there, the drain - or a waiting
                                // borrower - took ownership of it, and the
                                // holder sees to its closure.
                                if (log.isDebugEnabled()) {
                                    log.debug("Connection [" + con +
                                            "] was offered after the close drained the queue and is closed now.");
                                }
                                release(con);
                            }
                        } else {
                            if (log.isDebugEnabled()) {
                                log.debug("Connection [" + con + "] will be closed and not returned to the pool.");
                            }
                            release(con);
                        }
                    } catch (Throwable x) {
                        // The module's fatal policy runs first: what a driver
                        // fails with an Error is the same failed check this
                        // guard already retires on an unchecked exception, and
                        // the Error a driver wrapper loaded through a web
                        // application class loader throws once that context has
                        // stopped is that loader's ordinary failure, not a sign
                        // the JVM cannot be carried on.
                        ExceptionUtils.handleThrowable(x);
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
                        log.warn(MessageFactory.get("connectionPool.unableToReturnConnection", con), x);
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
            log.warn(MessageFactory.get("connectionPool.checkAbandonedFailed"), e);
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
            log.warn(MessageFactory.get("connectionPool.checkIdleFailed"), e);
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
     * {@link PoolProperties#getUseLock()} returning {@code false}), it still serialises with borrowers through the
     * queues: every connection is claimed out of the idle queue before it is validated or reconnected, and is put back
     * or released afterwards, so a borrower is never handed a connection this method is working on. That claim is what
     * makes an invocation concurrent with traffic safe, as {@link PooledConnection#lock()} alone protects nothing in
     * this configuration. A pool closed while a connection is claimed does not get it offered back into its idle
     * queue - the shutdown drain has passed it by, so the released connection is disconnected directly; the small
     * window that accepts between that check and the queue hand-over is the same one the return path accepts.
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
                    // Claim the connection before acting on it; without a
                    // real lock the check above may already be stale, and a
                    // connection a borrower took out of the idle queue in
                    // the meantime must not be validated or reconnected
                    // here. Reconnecting one under a borrower is the worst
                    // of it: both threads run disconnect-then-connect on the
                    // same PooledConnection, the loser's physical handle is
                    // overwritten by the winner's field write, and a session
                    // nothing references any more can never be closed.
                    if (!idle.remove(con)) {
                        continue;
                    }
                    boolean keep;
                    try {
                        if (checkMaxAgeOnly) {
                            keep = reconnectIfExpired(con);
                        } else {
                            keep = reconnectIfExpired(con) && con.validate(PooledConnection.VALIDATE_IDLE);
                        }
                    } catch (Throwable x) {
                        // The module's fatal policy runs first, as everywhere
                        // else: a non-fatal Error from behind one of the calls
                        // below is a check that cannot vouch for the session,
                        // which is what this guard already retires a driver's
                        // unchecked exception as.
                        ExceptionUtils.handleThrowable(x);
                        // The checks above swallow every checked driver
                        // failure, but an unchecked throw from behind one of
                        // them - a validation ending a transaction is one
                        // such call - must not escape now that the
                        // connection is claimed: unwinding here would leave
                        // it in neither queue, invisible to the next sweep
                        // and to the close() drain alike. Take it out of
                        // service instead, as a failed check already does.
                        con.setDiscarded(true);
                        release(con);
                        log.warn(MessageFactory.get("connectionPool.unableToTestIdleConnection", con), x);
                        continue;
                    }
                    if (keep) {
                        if (isClosed()) {
                            // The pool was closed while this connection was
                            // claimed: the shutdown drain has already passed
                            // over the queues and a closing pool offers no
                            // further item to the idle queue, so return it
                            // through the drain's own exit, as the return
                            // path does - an offer would land it in a dead
                            // queue that nothing drains any more, stranding
                            // the session out of every counter
                            release(con);
                        } else if (((idle.size() >= poolProperties.getMaxIdle()) &&
                                !poolProperties.isPoolSweeperEnabled()) || (!idle.offer(con))) {
                            if (log.isDebugEnabled()) {
                                log.debug("Connection [" + con + "] passed the idle test but will be closed and " +
                                        "not returned to the pool, idle[" + idle.size() + "]>=maxIdle[" +
                                        poolProperties.getMaxIdle() + "] idle.offer failed.");
                            }
                            release(con);
                        } else if (isClosed() && idle.remove(con)) {
                            // The closed check above and this offer are not
                            // one atomic step: a close arriving in between
                            // drains the queue this offer then fills for the
                            // last time. Take the connection back out of a
                            // queue that has already been drained past; if
                            // it is gone, whoever polled it owns its closure.
                            if (log.isDebugEnabled()) {
                                log.debug("Connection [" + con +
                                        "] was offered back after the close drained the queue and is closed now.");
                            }
                            release(con);
                        }
                    } else {
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
            log.warn(MessageFactory.get("connectionPool.testAllIdleFailed"), e);
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
                log.warn(MessageFactory.get("connectionPool.interceptorResetFailed",
                        handler.getClass().getName()), t);
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
                log.warn(MessageFactory.get("connectionPool.interceptorDisconnectedFailed",
                        handler.getClass().getName()), t);
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
            log.warn(MessageFactory.get("connectionPool.unableToStartJmx", getName()), x);
        }
    }

    /**
     * Publishes the MBeans of all connections this pool currently holds, below the pool's own MBean. A connection
     * registers itself when its physical connection is established, but that is only possible once the pool's own
     * MBean has been published - and a container publishes it only after the pool, already connected to its initial
     * size, has been created. Without this sweep the connections made before that publication, and in particular the
     * ones the pool pre-created for its initial size, would be counted in the pool's size while staying invisible to
     * JMX for as long as they are never borrowed.
     */
    public void registerConnectionMBeans() {
        // The connections publish themselves below the pool's own name, so
        // until that name is registered there is no base to publish them under.
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = jmxPool;
        if (jmx == null || jmx.getObjectName() == null) {
            return;
        }
        registerConnectionMBeans(idle);
        registerConnectionMBeans(busy);
    }

    /**
     * Releases the MBeans published for the connections this pool currently holds and for the interceptors of their
     * chains, when the pool's own MBean is released while the pool stays live. The connections and their sessions are
     * untouched; a later borrow or reconnect can publish them again.
     */
    public void unregisterConnectionMBeans() {
        unregisterConnectionMBeans(idle);
        unregisterConnectionMBeans(busy);
    }

    private void unregisterConnectionMBeans(BlockingQueue<PooledConnection> pool) {
        for (PooledConnection con : pool) {
            try {
                con.unregisterMBean();
            } catch (Throwable x) {
                // JMX is best effort: one connection that fails to release
                // its MBeans must not keep the others registered
                ExceptionUtils.handleThrowable(x);
                if (log.isDebugEnabled()) {
                    log.debug("Unable to unregister connection MBean", x);
                }
            }
        }
    }

    private void registerConnectionMBeans(BlockingQueue<PooledConnection> pool) {
        // The fair queue iterates a snapshot taken under its lock, the default
        // queue a view that tolerates concurrent modification: either way, a
        // connection that is concurrently taken out of or added to the queue
        // may or may not be seen. JMX is best effort, so a connection this pass
        // misses stays unregistered until it reconnects; it does not fail or
        // delay a borrow.
        for (PooledConnection con : pool) {
            try {
                // Under the connection's own lock, as every operation acting
                // on a pooled connection is, and - inside createMBean() -
                // under the connection's monitor, which that release of the
                // same connection shares as its unregistering lock in every
                // configuration: the registration must not slip in beside
                // the release, whose unregistration would then find no name
                // to take back and this one would leave a MBean of a dead
                // connection behind forever.
                con.lock();
                // A connection that is still without a physical connection is a
                // placeholder for a slot, not a session to monitor, and it
                // registers through the connect path once it gets its
                // connection - publishing one now would name a session that
                // does not exist.
                if (con.isInitialized()) {
                    con.createMBean();
                }
            } finally {
                con.unlock();
            }
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
                PooledConnection pc = borrowConnection(pooled, null, null);
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
                    cause = new SQLException(MessageFactory.get("connectionPool.asyncBorrowConnectionReleased"));
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
                        PooledConnection pc = borrowConnection(pooled, null, null);
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
                log.error(MessageFactory.get("connectionPool.unableToCancelConnectionFuture"), x);
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
            poolCleanTimer = newPoolTimer();
        }
        try {
            poolCleanTimer.schedule(cleaner, cleaner.sleepTime, cleaner.sleepTime);
            return;
        } catch (IllegalStateException x) {
            // Two different failures answer with this exception and they call
            // for opposite handling. A timer cancels itself for good the
            // moment a task lets a throwable escape it, and every pool this
            // class loader holds shares the one timer, so a timer that died
            // refuses every pool that needs a cleaner afterwards for the rest
            // of the JVM's life - unless it is replaced. The other failure is
            // the task itself, which has been scheduled once already and no
            // timer will ever accept again. The replacement is attempted on a
            // timer built for the attempt, which is a question the second
            // cause cannot fake: a task the new timer takes was refused by
            // the old timer's death. Attempted once, because keyed on the
            // refusal alone the second cause is incurable and the loop spins,
            // handing out a fresh timer per turn and scheduling nothing on
            // any of them.
            Timer replacement = newPoolTimer();
            try {
                replacement.schedule(cleaner, cleaner.sleepTime, cleaner.sleepTime);
            } catch (IllegalStateException y) {
                // The task is the spent one, so nothing is rebuilt. The timer
                // this call found may well be alive and serving the tasks
                // registered on it; the one just discarded was never asked to
                // hold anything, and the registry goes back as this call found
                // it. The refusal itself goes back to the caller, which is
                // what starting a spent task has always answered with.
                cleaners.remove(cleaner);
                replacement.cancel();
                log.error(MessageFactory.get("connectionPool.cleanerSchedulingFailed"), y);
                throw y;
            }
            // The diagnosis is the acceptance: every task the registry holds
            // besides this one was scheduled on the timer that died. A spent
            // task can never be scheduled again, so replacing the timer
            // revives nothing by itself - each pool has to build a new task,
            // and only that pool's monitor can arm it. Pool threads reach
            // this method's monitor from behind their own, so arming them
            // from here would cross the two orders; the new timer's thread
            // holds no pool monitor and takes them one at a time.
            List<PoolCleaner> stranded = new ArrayList<>(cleaners);
            stranded.remove(cleaner);
            poolCleanTimer = replacement;
            if (!stranded.isEmpty()) {
                log.error(MessageFactory.get("connectionPool.cleanerTimerReplaced",
                        Integer.valueOf(stranded.size())), x);
                try {
                    replacement.schedule(new CleanerReviver(stranded), 0);
                } catch (RuntimeException z) {
                    // The recovery's own step is the one failing here. The pool
                    // that arrived has its task on the new timer and owes its
                    // configuration write no failure it did not cause, so this
                    // is reported rather than thrown back at it; the pools left
                    // stranded are what the message counts.
                    log.error(MessageFactory.get("connectionPool.cleanerRearmFailed"), z);
                }
            }
        }
    }

    /**
     * Builds the timer that carries the maintenance of every pool this class
     * loader holds. Its thread is created with the pool's own class loader as
     * the context one, so that a reference to a web application class loader is
     * not created via Thread.inheritedAccessControlContext.
     *
     * @return a timer with no tasks scheduled on it
     */
    private static Timer newPoolTimer() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ConnectionPool.class.getClassLoader());
            return new Timer(
                    "Tomcat JDBC Pool Cleaner[" + System.identityHashCode(ConnectionPool.class.getClassLoader()) +
                            ":" + System.currentTimeMillis() + "]",
                    true);
        } finally {
            Thread.currentThread().setContextClassLoader(loader);
        }
    }

    /**
     * Arms a fresh maintenance task for each pool whose task was scheduled on
     * the shared timer that died. The revival cannot reuse those tasks - one
     * that has been scheduled once is spent - so it asks the pools themselves
     * through {@link ConnectionPool#rearmPoolCleaner(PoolCleaner)}, which takes
     * effect only for a pool still holding the spent task.
     */
    private static final class CleanerReviver extends TimerTask {
        private final List<PoolCleaner> stranded;

        CleanerReviver(List<PoolCleaner> stranded) {
            this.stranded = stranded;
        }

        @Override
        public void run() {
            for (PoolCleaner cleaner : stranded) {
                try {
                    ConnectionPool pool = cleaner.pool.get();
                    if (pool == null) {
                        // Nothing holds the pool any more. Its task will never
                        // run again, so it is the one entry in the registry that
                        // no cleaner will ever stop and no close will ever hand
                        // back - drop it here, as the task's own run method does
                        // when it outlives the pool.
                        unregisterCleaner(cleaner);
                        continue;
                    }
                    pool.rearmPoolCleaner(cleaner);
                } catch (Throwable x) {
                    // The module's fatal policy runs first, as everywhere else.
                    // This is a timer task: a throwable that escaped it would
                    // take the replacement timer down the way the last one went,
                    // and the pools further down this list with it.
                    ExceptionUtils.handleThrowable(x);
                    log.error(MessageFactory.get("connectionPool.cleanerRearmFailed"), x);
                }
            }
        }
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
                log.warn(MessageFactory.get("connectionPool.evicterIntervalZero"));
                this.sleepTime = 1000 * 30;
            } else if (sleepTime < 1000) {
                log.warn(MessageFactory.get("connectionPool.evicterIntervalTooLow"));
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
                } catch (Throwable x) {
                    // The guards on the maintenance paths convert exceptions
                    // and - since handleThrowable joined them - non-fatal
                    // Errors, but a throwable can still reach here from a
                    // path without a guard of its own. The module's fatal
                    // policy runs first, as everywhere else; losing this
                    // task's next runs to a timer that dies on a fatal Error
                    // is borne by the replacement in registerCleaner.
                    ExceptionUtils.handleThrowable(x);
                    log.error(MessageFactory.get("connectionPool.poolCleanerFailed"), x);
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

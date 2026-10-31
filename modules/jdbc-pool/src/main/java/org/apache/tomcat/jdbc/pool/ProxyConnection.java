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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.XAConnection;

import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * A ProxyConnection object is the bottom most interceptor that wraps an object of type {@link PooledConnection}. The
 * ProxyConnection intercepts, among the calls it forwards, the following methods:
 * <ul>
 * <li>{@link java.sql.Connection#close()} - returns the connection to the pool. May be called multiple times.</li>
 * <li>{@link java.sql.Connection#abort(java.util.concurrent.Executor)} - delegates the abort to the physical
 * connection and then ends the borrow: the session is taken out of service and the slot is freed. On a released
 * handle it is the specification's no-op; on a handle whose session the pool discarded while borrowed it skips the
 * driver and ends the borrow quietly.</li>
 * <li>{@link java.lang.Object#toString()} - returns a custom string for this object</li>
 * <li>{@link javax.sql.PooledConnection#getConnection()} - returns the underlying connection</li>
 * <li>{@link java.lang.Object#equals(Object)} and {@link java.lang.Object#hashCode()} - answered with the identity of
 * the connection handle, so that the {@code Object} contract holds even when no facade sits in front of this
 * interceptor</li>
 * </ul>
 * By default method comparisons is done on a String reference level, unless the
 * {@link PoolConfiguration#setUseEquals(boolean)} has been called with a <code>true</code> argument.
 * <p>
 * The connection handle the pool hands out implements {@link javax.sql.PooledConnection} (and
 * {@link javax.sql.XAConnection} when the pool is backed by an {@link javax.sql.XADataSource}). The pool keeps no
 * connection or statement event listeners of its own, so the four listener methods of
 * {@code javax.sql.PooledConnection} - {@code addConnectionEventListener}, {@code removeConnectionEventListener},
 * {@code addStatementEventListener} and {@code removeStatementEventListener} - are answered by the XA-family
 * dispatch in {@link #invoke(Object, Method, Object[])}: where the handle is itself an {@code XAConnection} they
 * reach the XA connection the driver created, whose implementation decides what registering them means; where it
 * is not, the call has no dispatch target and each of the four fails with an {@link SQLException} naming the
 * method. The refusal also stands on a pool backed by a {@link javax.sql.ConnectionPoolDataSource}: the pool does
 * hold the driver's own pooled connection there, but it holds it for its own session management and does not
 * route these calls to it. A handle whose session the pool discarded while it was still borrowed, or which its
 * borrower has already closed, answers instead with the closed-connection failure the handle gives in those
 * states. Either failure reaches a caller of the interface - the interface declares no checked exception - as an
 * {@link java.lang.reflect.UndeclaredThrowableException} carrying it. That refusal is deliberate rather than
 * unfinished work. The specification has the connection pool manager register through these methods, to be told
 * to deactivate the logical handle and reuse or retire the physical session - decisions this pool makes and
 * carries out itself: {@code close()} on the handle recycles a session that stays open and owned by the pool, and
 * the next borrower is handed a new handle, so this handle is not the long-lived producer object whose listener
 * list the specification describes; and where the pool does not observe statements - as it does not by default -
 * a {@code statementClosed} event has no point from which it could be delivered. Accepting a registration it
 * could not notify faithfully would only hide from the caller that no notification will ever arrive.
 */
public class ProxyConnection extends JdbcInterceptor {

    /** The underlying pooled connection. */
    protected PooledConnection connection = null;

    /** The parent connection pool. */
    protected ConnectionPool pool = null;

    /**
     * Returns the underlying pooled connection.
     *
     * @return the pooled connection
     */
    public PooledConnection getConnection() {
        return connection;
    }

    /**
     * Sets the underlying pooled connection.
     *
     * @param connection the pooled connection
     */
    public void setConnection(PooledConnection connection) {
        this.connection = connection;
    }

    /**
     * Returns the parent connection pool.
     *
     * @return the connection pool
     */
    public ConnectionPool getPool() {
        return pool;
    }

    /**
     * Sets the parent connection pool.
     *
     * @param pool the connection pool
     */
    public void setPool(ConnectionPool pool) {
        this.pool = pool;
    }

    /**
     * Creates a new ProxyConnection wrapping the given pooled connection.
     *
     * @param parent    the parent connection pool
     * @param con       the pooled connection
     * @param useEquals whether to use equals for method comparison
     */
    protected ProxyConnection(ConnectionPool parent, PooledConnection con, boolean useEquals) {
        pool = parent;
        connection = con;
        setUseEquals(useEquals);
    }

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        this.pool = parent;
        this.connection = con;
    }

    /**
     * Checks whether this interceptor wraps the given interface. This is the view from the interceptor itself; the
     * contract of the connection handle the pool hands out - which also implements {@link java.sql.Connection} and
     * {@link javax.sql.PooledConnection} (and {@link javax.sql.XAConnection} on XA pools) - is answered by the dispatch
     * in {@link #invoke(Object, Method, Object[])}.
     *
     * @param iface the interface to check
     *
     * @return true if this interceptor wraps the given interface
     */
    public boolean isWrapperFor(Class<?> iface) {
        if (iface == PooledConnection.class) {
            // The Wrapper answer must agree with what unwrap() hands out:
            // for this class unwrap() returns the pooled connection this
            // interceptor sits on, so the honest answer is whether one is
            // still held. That object is pool-internal - see unwrap().
            return connection != null;
        }
        if (iface == XAConnection.class && connection.getXAConnection() != null) {
            return true;
        }
        Connection physical = connection.getConnection();
        if (physical == null) {
            // Released handle: there is nothing left below to be a wrapper
            return false;
        }
        if (iface.isInstance(physical)) {
            return true;
        }
        if (iface == XAConnection.class) {
            // javax.sql.XAConnection is only ever handed out from the pooled
            // connection's own XA handle - unwrap() says so - so the driver
            // below is not asked about it
            return false;
        }
        // The driver's own connection may itself wrap a further connection -
        // a driver built around another driver's, like the AWS wrapper over
        // the PostgreSQL one - and it, not the class of the object it
        // presents, is what knows which interfaces it can unwrap to.
        try {
            return physical.isWrapperFor(iface);
        } catch (SQLException x) {
            // A driver that cannot answer the question is not a wrapper
            // for the interface
            return false;
        }
    }


    /**
     * Unwraps the connection to the given interface.
     *
     * @param iface the interface to unwrap to
     *
     * @return the unwrapped connection. For {@link PooledConnection} this is
     *         the pool's own pooled connection - a pool-internal object meant
     *         for trusted callers such as interceptors, not for application
     *         code, as it can close and discard the connection behind the
     *         pool's own accounting.
     *
     * @throws SQLException if the connection does not wrap the given interface
     */
    public Object unwrap(Class<?> iface) throws SQLException {
        if (iface == PooledConnection.class) {
            if (connection == null) {
                // The same not-a-wrapper failure the XA branch below gives
                // for a missing XA connection: with none held there is
                // nothing to hand out, and the silent null would contradict
                // what isWrapperFor() says and turn into an unrelated
                // NullPointerException at the caller's first use of the
                // "unwrapped" value.
                throw new SQLException(MessageFactory.get("proxyConnection.notAWrapper", iface.getName()));
            }
            return connection;
        } else if (iface == XAConnection.class) {
            javax.sql.XAConnection xa = connection.getXAConnection();
            if (xa == null) {
                // The Wrapper contract asks for a failure, not for a silent
                // null that turns into an unrelated NullPointerException at
                // the caller's first use of the "unwrapped" value.
                throw new SQLException(MessageFactory.get("proxyConnection.notAWrapper", iface.getName()));
            }
            return xa;
        } else if (iface.isInstance(connection.getConnection())) {
            return connection.getConnection();
        } else if (isWrapperFor(iface)) {
            // The driver's connection is a wrapper further down, and
            // unwrapping that far is its answer to give, not ours
            return connection.getConnection().unwrap(iface);
        } else {
            throw new SQLException(MessageFactory.get("proxyConnection.notAWrapper", iface.getName()));
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (compare(ISCLOSED_VAL, method)) {
            return Boolean.valueOf(isClosed());
        }
        if (compare(ISVALID_VAL, method) && isClosed()) {
            // isValid() is the probe for liveness, and the JDBC contract
            // answers a closed connection with false rather than with an
            // exception. A session the pool discarded while its borrower
            // still held the handle - an abandon, a forced pool close, an
            // interceptor marking it discarded - does not seal the
            // disposable facade, so without this branch the probe would
            // reach the closed guard below and throw. The guard answers
            // false, as the sealed facade does; a live handle falls through.
            return Boolean.FALSE;
        }
        if (compare(EQUALS_VAL, method)) {
            // With the DisposableConnectionFacade switched off, Object methods
            // reach this interceptor, and the XA branch below would dispatch
            // them at a null XA connection (or at the XA connection of another
            // borrower) instead of answering them. Object.equals must answer
            // false rather than throw for a null or foreign argument, and it
            // must stay answerable on a released handle, so the identity of
            // the handle itself is what compares - mirroring the semantics of
            // the facade when it is enabled.
            Object other = (args == null || args.length == 0) ? null : args[0];
            return Boolean.valueOf(proxy == other);
        }
        if (compare(HASHCODE_VAL, method)) {
            return Integer.valueOf(System.identityHashCode(proxy));
        }
        if (compare(CLOSE_VAL, method)) {
            if (connection == null) {
                return null; // No-op for already closed.
            }
            PooledConnection poolc = this.connection;
            this.connection = null;
            pool.returnConnection(poolc);
            return null;
        } else if (compare(ABORT_VAL, method)) {
            // java.sql.Connection.abort(Executor). The specification has the
            // call mark the connection closed, close the physical session and
            // release its resources, and it makes the call on a closed
            // connection a no-op. A handle its borrower has already released
            // is answered by the disposable facade, which disposes of abort
            // as it does of close and then answers it quietly; the guard here
            // covers the same state reached without a facade. The pool and
            // session fields are read once, into locals, for the whole branch:
            // a concurrent disposal of the session - which a destroy or a
            // factory close can trigger at any moment, from the finalizer
            // thread - nulls them, and reading them again after the guard
            // would either fail the branch with a null pointer or silently
            // skip the return that frees the slot the abort is there to free.
            ConnectionPool poolRef = this.pool;
            PooledConnection poolc = this.connection;
            if (poolc == null || poolRef == null) {
                return null;
            }
            // The borrow ends here, ahead of the driver call, which the
            // specification allows to run long: from now on every use of this
            // handle fails the way it fails on a closed connection, and a
            // concurrent close of the same handle finds it already ended.
            this.connection = null;
            try {
                if (!poolc.isDiscarded()) {
                    // A session the pool marked to be taken out of service on
                    // its return while this handle was borrowed makes the
                    // handle report itself closed, and the no-op the
                    // specification prescribes for a closed connection asks
                    // nothing of the driver.
                    Connection physical = poolc.getConnection();
                    if (physical != null) {
                        // The driver's own answer - including the failure the
                        // specification prescribes for a null executor, and a
                        // refusal - reaches the caller.
                        method.invoke(physical, args);
                    }
                }
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException) {
                    throw t.getCause() != null ? t.getCause() : t;
                } else {
                    throw t;
                }
            } finally {
                // Whatever the driver made of the call, the session cannot go
                // back to the pool: the application holding this handle now
                // believes the specification, which says the connection is
                // closed after abort, and will not return it - leaving it in
                // busy would strand one maxActive slot per abort (bug 65929) -
                // and where the driver ran the closing work asynchronously the
                // session may die at any moment, so no other borrower may
                // receive it either. Marking it discarded first, the return
                // takes it out of service whatever the driver's isClosed()
                // says, the same terminal step the pool takes on a connection
                // whose state it cannot trust; it frees the slot and wakes the
                // threads waiting for one. For a session already marked
                // discarded it is that step run early: this handle is now
                // disposed, so the close that was to complete the discard
                // will not come.
                poolc.setDiscarded(true);
                poolRef.returnConnection(poolc);
            }
            return null;
        } else if (compare(TOSTRING_VAL, method)) {
            return this.toString();
        } else if (compare(GETCONNECTION_VAL, method) && connection != null) {
            return connection.getConnection();
        } else if (method.getDeclaringClass() != Object.class &&
                method.getDeclaringClass().isAssignableFrom(XAConnection.class) && connection != null) {
            // The Object.class exclusion is defensive: the only Object
            // methods java.lang.reflect.Proxy routes to an invocation
            // handler are equals, hashCode and toString, and all three are
            // answered above. Any other method the branch condition matches
            // can only be answered by the XA connection wrapper - and that
            // wrapper exists only when the pool is backed by an XA data
            // source (and only while the physical connection is still
            // connected). The condition also matches methods declared by
            // the interfaces XAConnection extends, javax.sql.PooledConnection
            // among them, so on a plain pool calls such as
            // addConnectionEventListener reach this branch without a target;
            // dispatching them at null would surface a raw
            // NullPointerException to the application.
            javax.sql.XAConnection xaConnection = connection.getXAConnection();
            if (xaConnection == null) {
                if (isClosed()) {
                    // The handle was disconnected while borrowed; report it
                    // the way every other method on this class does, rather
                    // than blaming the XA configuration.
                    throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
                }
                throw new SQLException(MessageFactory.get("proxyConnection.notXAConnection", method.getName()));
            }
            try {
                return method.invoke(xaConnection, args);
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException) {
                    throw t.getCause() != null ? t.getCause() : t;
                } else {
                    throw t;
                }
            }
        }
        if (isClosed()) {
            throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
        }
        if (compare(UNWRAP_VAL, method)) {
            Class<?> iface = (Class<?>) args[0];
            if (iface != XAConnection.class && iface.isInstance(proxy) &&
                    !iface.isInstance(connection.getConnection())) {
                // The Wrapper contract: the handle is a wrapper of every
                // interface it implements itself. Interfaces the physical
                // connection behind it also provides - java.sql.Connection
                // first of all - keep resolving to the physical connection,
                // as they always have; javax.sql.XAConnection is excluded
                // because unwrapping to it has always yielded the XA
                // connection object where one exists, and the unwrap above
                // reports the not-a-wrapper failure where none does.
                return proxy;
            }
            return unwrap(iface);
        } else if (compare(ISWRAPPERFOR_VAL, method)) {
            Class<?> iface = (Class<?>) args[0];
            // An object is always a wrapper of the interfaces it implements.
            return Boolean.valueOf(iface.isInstance(proxy) || this.isWrapperFor(iface));
        }
        try {
            PooledConnection poolc = connection;
            if (poolc != null) {
                return method.invoke(poolc.getConnection(), args);
            } else {
                throw new SQLException(MessageFactory.get("proxyConnection.connectionClosed"));
            }
        } catch (Throwable t) {
            if (t instanceof InvocationTargetException) {
                throw t.getCause() != null ? t.getCause() : t;
            } else {
                throw t;
            }
        }
    }

    /**
     * Returns true if the underlying connection has been closed or discarded.
     *
     * @return true if closed or discarded
     */
    public boolean isClosed() {
        return connection == null || connection.isDiscarded();
    }

    /**
     * Returns the delegate pooled connection.
     *
     * @return the delegate pooled connection
     */
    public PooledConnection getDelegateConnection() {
        return connection;
    }

    /**
     * Returns the parent connection pool.
     *
     * @return the parent connection pool
     */
    public ConnectionPool getParentPool() {
        return pool;
    }

    @Override
    public String toString() {
        return "ProxyConnection[" + (connection != null ? connection.toString() : "null") + "]";
    }

}

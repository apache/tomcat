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
import java.sql.SQLException;

import javax.sql.XAConnection;
/**
 * A ProxyConnection object is the bottom most interceptor that wraps an object of type
 * {@link PooledConnection}. The ProxyConnection intercepts, among the calls it forwards,
 * the following methods:
 * <ul>
 *   <li>{@link java.sql.Connection#close()} - returns the connection to the pool. May be called multiple times.</li>
 *   <li>{@link java.lang.Object#toString()} - returns a custom string for this object</li>
 *   <li>{@link javax.sql.PooledConnection#getConnection()} - returns the underlying connection</li>
 *   <li>{@link java.lang.Object#equals(Object)} and {@link java.lang.Object#hashCode()} -
 *       answered with the identity of the connection handle, so that the {@code Object}
 *       contract holds even when no facade sits in front of this interceptor</li>
 * </ul>
 * By default method comparisons is done on a String reference level, unless the {@link PoolConfiguration#setUseEquals(boolean)} has been called
 * with a <code>true</code> argument.
 */
public class ProxyConnection extends JdbcInterceptor {

    /** The underlying pooled connection. */
    protected PooledConnection connection = null;

    /** The parent connection pool. */
    protected ConnectionPool pool = null;

    /**
     * Returns the underlying pooled connection.
     * @return the pooled connection
     */
    public PooledConnection getConnection() {
        return connection;
    }

    /**
     * Sets the underlying pooled connection.
     * @param connection the pooled connection
     */
    public void setConnection(PooledConnection connection) {
        this.connection = connection;
    }

    /**
     * Returns the parent connection pool.
     * @return the connection pool
     */
    public ConnectionPool getPool() {
        return pool;
    }

    /**
     * Sets the parent connection pool.
     * @param pool the connection pool
     */
    public void setPool(ConnectionPool pool) {
        this.pool = pool;
    }

    /**
     * Creates a new ProxyConnection wrapping the given pooled connection.
     * @param parent the parent connection pool
     * @param con the pooled connection
     * @param useEquals whether to use equals for method comparison
     */
    protected ProxyConnection(ConnectionPool parent, PooledConnection con,
            boolean useEquals) {
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
     * Checks whether this interceptor wraps the given interface. This is the
     * view from the interceptor itself; the contract of the connection handle
     * the pool hands out - which also implements {@link java.sql.Connection}
     * and {@link javax.sql.PooledConnection} (and {@link javax.sql.XAConnection}
     * on XA pools) - is answered by the dispatch in
     * {@link #invoke(Object, Method, Object[])}.
     * @param iface the interface to check
     * @return true if this interceptor wraps the given interface
     */
    public boolean isWrapperFor(Class<?> iface) {
        if (iface == XAConnection.class && connection.getXAConnection()!=null) {
            return true;
        } else {
            return iface.isInstance(connection.getConnection());
        }
    }


    /**
     * Unwraps the connection to the given interface.
     * @param iface the interface to unwrap to
     * @return the unwrapped connection
     * @throws SQLException if the connection does not wrap the given interface
     */
    public Object unwrap(Class<?> iface) throws SQLException {
        if (iface == PooledConnection.class) {
            return connection;
        }else if (iface == XAConnection.class) {
            javax.sql.XAConnection xa = connection.getXAConnection();
            if (xa == null) {
                // The Wrapper contract asks for a failure, not for a silent
                // null that turns into an unrelated NullPointerException at
                // the caller's first use of the "unwrapped" value.
                throw new SQLException("Not a wrapper of " + iface.getName());
            }
            return xa;
        } else if (isWrapperFor(iface)) {
            return connection.getConnection();
        } else {
            throw new SQLException("Not a wrapper of "+iface.getName());
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (compare(ISCLOSED_VAL,method)) {
            return Boolean.valueOf(isClosed());
        }
        if (compare(EQUALS_VAL,method)) {
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
        if (compare(HASHCODE_VAL,method)) {
            return Integer.valueOf(System.identityHashCode(proxy));
        }
        if (compare(CLOSE_VAL,method)) {
            if (connection==null)
            {
                return null; //noop for already closed.
            }
            PooledConnection poolc = this.connection;
            this.connection = null;
            pool.returnConnection(poolc);
            return null;
        } else if (compare(TOSTRING_VAL,method)) {
            return this.toString();
        } else if (compare(GETCONNECTION_VAL,method) && connection!=null) {
            return connection.getConnection();
        } else if (method.getDeclaringClass() != Object.class
                && method.getDeclaringClass().isAssignableFrom(XAConnection.class)
                && connection != null) {
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
                    throw new SQLException("Connection has already been closed.");
                }
                throw new SQLException("The pooled connection is not an XA connection; "
                        + "method [" + method.getName() + "] cannot be dispatched.");
            }
            try {
                return method.invoke(xaConnection, args);
            }catch (Throwable t) {
                if (t instanceof InvocationTargetException) {
                    throw t.getCause() != null ? t.getCause() : t;
                } else {
                    throw t;
                }
            }
        }
        if (isClosed()) {
            throw new SQLException("Connection has already been closed.");
        }
        if (compare(UNWRAP_VAL,method)) {
            Class<?> iface = (Class<?>)args[0];
            if (iface != XAConnection.class && iface.isInstance(proxy)
                    && !iface.isInstance(connection.getConnection())) {
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
        } else if (compare(ISWRAPPERFOR_VAL,method)) {
            Class<?> iface = (Class<?>)args[0];
            // An object is always a wrapper of the interfaces it implements.
            return Boolean.valueOf(iface.isInstance(proxy)
                    || this.isWrapperFor(iface));
        }
        try {
            PooledConnection poolc = connection;
            if (poolc!=null) {
                return method.invoke(poolc.getConnection(),args);
            } else {
                throw new SQLException("Connection has already been closed.");
            }
        }catch (Throwable t) {
            if (t instanceof InvocationTargetException) {
                throw t.getCause() != null ? t.getCause() : t;
            } else {
                throw t;
            }
        }
    }

    /**
     * Returns true if the underlying connection has been closed or discarded.
     * @return true if closed or discarded
     */
    public boolean isClosed() {
        return connection==null || connection.isDiscarded();
    }

    /**
     * Returns the delegate pooled connection.
     * @return the delegate pooled connection
     */
    public PooledConnection getDelegateConnection() {
        return connection;
    }

    /**
     * Returns the parent connection pool.
     * @return the parent connection pool
     */
    public ConnectionPool getParentPool() {
        return pool;
    }

    @Override
    public String toString() {
        return "ProxyConnection["+(connection!=null?connection.toString():"null")+"]";
    }

}

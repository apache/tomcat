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
 * {@link PooledConnection}. The ProxyConnection intercepts three methods:
 * <ul>
 *   <li>{@link java.sql.Connection#close()} - returns the connection to the pool. May be called multiple times.</li>
 *   <li>{@link java.lang.Object#toString()} - returns a custom string for this object</li>
 *   <li>{@link javax.sql.PooledConnection#getConnection()} - returns the underlying connection</li>
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
     * Checks whether this proxy wraps the given interface.
     * @param iface the interface to check
     * @return true if this proxy wraps the given interface
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
            return connection.getXAConnection();
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
        } else if (method.getDeclaringClass().isAssignableFrom(XAConnection.class) && connection != null) {
            try {
                return method.invoke(connection.getXAConnection(),args);
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
            return unwrap((Class<?>)args[0]);
        } else if (compare(ISWRAPPERFOR_VAL,method)) {
            return Boolean.valueOf(this.isWrapperFor((Class<?>)args[0]));
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

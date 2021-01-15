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
package org.apache.tomcat.dbcp.dbcp2;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.tomcat.dbcp.pool2.ObjectPool;

/**
 * A {@link Driver} implementation that obtains {@link Connection}s from a registered {@link ObjectPool}.
 *
 * @since 2.0
 */
public class PoolingDriver implements Driver {

    private static final DriverPropertyInfo[] EMPTY_DRIVER_PROPERTY_INFO_ARRAY = new DriverPropertyInfo[0];

    /* Register myself with the {@link DriverManager}. */
    static {
        try {
            DriverManager.registerDriver(new PoolingDriver());
        } catch (final Exception e) {
            // ignore
        }
    }

    /** The map of registered pools. */
    protected static final HashMap<String, ObjectPool<? extends Connection>> pools = new HashMap<>();

    /** Controls access to the underlying connection */
    private final boolean accessToUnderlyingConnectionAllowed;

    /**
     * Constructs a new driver with <code>accessToUnderlyingConnectionAllowed</code> enabled.
     */
    public PoolingDriver() {
        this(true);
    }

    /**
     * For unit testing purposes.
     *
     * @param accessToUnderlyingConnectionAllowed
     *            Do {@link DelegatingConnection}s created by this driver permit access to the delegate?
     */
    protected PoolingDriver(final boolean accessToUnderlyingConnectionAllowed) {
        this.accessToUnderlyingConnectionAllowed = accessToUnderlyingConnectionAllowed;
    }

    /**
     * Returns the value of the accessToUnderlyingConnectionAllowed property.
     *
     * @return true if access to the underlying is allowed, false otherwise.
     */
    protected boolean isAccessToUnderlyingConnectionAllowed() {
        return accessToUnderlyingConnectionAllowed;
    }

    /**
     * Gets the connection pool for the given name.
     *
     * @param name
     *            The pool name
     * @return The pool
     * @throws SQLException
     *             Thrown when the named pool is not registered.
     */
    public synchronized ObjectPool<? extends Connection> getConnectionPool(final String name) throws SQLException {
        final ObjectPool<? extends Connection> pool = pools.get(name);
        if (null == pool) {
            throw new SQLException("Pool not registered: " + name);
        }
        return pool;
    }

    /**
     * Registers a named pool.
     *
     * @param name
     *            The pool name.
     * @param pool
     *            The pool.
     */
    public synchronized void registerPool(final String name, final ObjectPool<? extends Connection> pool) {
        pools.put(name, pool);
    }

    /**
     * Closes a named pool.
     *
     * @param name
     *            The pool name.
     * @throws SQLException
     *             Thrown when a problem is caught closing the pool.
     */
    public synchronized void closePool(final String name) throws SQLException {
        final ObjectPool<? extends Connection> pool = pools.get(name);
        if (pool != null) {
            pools.remove(name);
            try {
                pool.close();
            } catch (final Exception e) {
                throw new SQLException("Error closing pool " + name, e);
            }
        }
    }

    /**
     * Gets the pool names.
     *
     * @return the pool names.
     */
    public synchronized String[] getPoolNames() {
        return pools.keySet().toArray(Utils.EMPTY_STRING_ARRAY);
    }

    @Override
    public boolean acceptsURL(final String url) throws SQLException {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public Connection connect(final String url, final Properties info) throws SQLException {
        if (acceptsURL(url)) {
            final ObjectPool<? extends Connection> pool = getConnectionPool(url.substring(URL_PREFIX_LEN));

            try {
                final Connection conn = pool.borrowObject();
                if (conn == null) {
                    return null;
                }
                return new PoolGuardConnectionWrapper(pool, conn);
            } catch (final NoSuchElementException e) {
                throw new SQLException("Cannot get a connection, pool error: " + e.getMessage(), e);
            } catch (final SQLException | RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new SQLException("Cannot get a connection, general error: " + e.getMessage(), e);
            }
        }
        return null;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * Invalidates the given connection.
     *
     * @param conn
     *            connection to invalidate
     * @throws SQLException
     *             if the connection is not a <code>PoolGuardConnectionWrapper</code> or an error occurs invalidating
     *             the connection
     */
    public void invalidateConnection(final Connection conn) throws SQLException {
        if (conn instanceof PoolGuardConnectionWrapper) { // normal case
            final PoolGuardConnectionWrapper pgconn = (PoolGuardConnectionWrapper) conn;
            @SuppressWarnings("unchecked")
            final ObjectPool<Connection> pool = (ObjectPool<Connection>) pgconn.pool;
            try {
                pool.invalidateObject(pgconn.getDelegateInternal());
            } catch (final Exception e) {
                // Ignore.
            }
        } else {
            throw new SQLException("Invalid connection class");
        }
    }

    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    @Override
    public boolean jdbcCompliant() {
        return true;
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) {
        return EMPTY_DRIVER_PROPERTY_INFO_ARRAY;
    }

    /** My URL prefix */
    public static final String URL_PREFIX = "jdbc:apache:commons:dbcp:";
    protected static final int URL_PREFIX_LEN = URL_PREFIX.length();

    // version numbers
    protected static final int MAJOR_VERSION = 1;
    protected static final int MINOR_VERSION = 0;

    /**
     * PoolGuardConnectionWrapper is a Connection wrapper that makes sure a closed connection cannot be used anymore.
     *
     * @since 2.0
     */
    private class PoolGuardConnectionWrapper extends DelegatingConnection<Connection> {

        private final ObjectPool<? extends Connection> pool;

        PoolGuardConnectionWrapper(final ObjectPool<? extends Connection> pool, final Connection delegate) {
            super(delegate);
            this.pool = pool;
        }

        /**
         * @see org.apache.tomcat.dbcp.dbcp2.DelegatingConnection#getDelegate()
         */
        @Override
        public Connection getDelegate() {
            if (isAccessToUnderlyingConnectionAllowed()) {
                return super.getDelegate();
            }
            return null;
        }

        /**
         * @see org.apache.tomcat.dbcp.dbcp2.DelegatingConnection#getInnermostDelegate()
         */
        @Override
        public Connection getInnermostDelegate() {
            if (isAccessToUnderlyingConnectionAllowed()) {
                return super.getInnermostDelegate();
            }
            return null;
        }
    }
}

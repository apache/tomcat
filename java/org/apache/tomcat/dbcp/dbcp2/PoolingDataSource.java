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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool;

/**
 * A simple {@link DataSource} implementation that obtains {@link Connection}s from the specified {@link ObjectPool}.
 *
 * @param <C>
 *            The connection type
 *
 * @since 2.0
 */
public class PoolingDataSource<C extends Connection> implements DataSource, AutoCloseable {

    /**
     * PoolGuardConnectionWrapper is a Connection wrapper that makes sure a closed connection cannot be used anymore.
     *
     * @since 2.0
     */
    private final class PoolGuardConnectionWrapper<D extends Connection> extends DelegatingConnection<D> {

        PoolGuardConnectionWrapper(final D delegate) {
            super(delegate);
        }

        @Override
        public void close() throws SQLException {
            if (getDelegateInternal() != null) {
                super.close();
                super.setDelegate(null);
            }
        }

        /**
         * @see org.apache.tomcat.dbcp.dbcp2.DelegatingConnection#getDelegate()
         */
        @Override
        public D getDelegate() {
            return isAccessToUnderlyingConnectionAllowed() ? super.getDelegate() : null;
        }

        /**
         * @see org.apache.tomcat.dbcp.dbcp2.DelegatingConnection#getInnermostDelegate()
         */
        @Override
        public Connection getInnermostDelegate() {
            return isAccessToUnderlyingConnectionAllowed() ? super.getInnermostDelegate() : null;
        }

        @Override
        public boolean isClosed() throws SQLException {
            return getDelegateInternal() == null || super.isClosed();
        }
    }

    private static final Log log = LogFactory.getLog(PoolingDataSource.class);

    /** Controls access to the underlying connection */
    private boolean accessToUnderlyingConnectionAllowed;

    /** My log writer. */
    private PrintWriter logWriter;

    private final ObjectPool<C> pool;

    /**
     * Constructs a new instance backed by the given connection pool.
     *
     * @param pool
     *            the given connection pool.
     */
    public PoolingDataSource(final ObjectPool<C> pool) {
        Objects.requireNonNull(pool, "pool");
        this.pool = pool;
        // Verify that pool's factory refers back to it. If not, log a warning and try to fix.
        if (this.pool instanceof GenericObjectPool<?>) {
            final PoolableConnectionFactory pcf = (PoolableConnectionFactory) ((GenericObjectPool<?>) this.pool).getFactory();
            Objects.requireNonNull(pcf, "this.pool.getFactory()");
            if (pcf.getPool() != this.pool) {
                log.warn(Utils.getMessage("poolingDataSource.factoryConfig"));
                @SuppressWarnings("unchecked") // PCF must have a pool of PCs
                final ObjectPool<PoolableConnection> p = (ObjectPool<PoolableConnection>) this.pool;
                pcf.setPool(p);
            }
        }
    }

    /**
     * Closes and free all {@link Connection}s from the pool.
     *
     * @since 2.1
     */
    @Override
    public void close() throws SQLException {
        try {
            pool.close();
        } catch (final Exception e) {
            throw new SQLException(Utils.getMessage("pool.close.fail"), e);
        }
    }

    /**
     * Returns a {@link java.sql.Connection} from my pool, according to the contract specified by
     * {@link ObjectPool#borrowObject}.
     */
    @Override
    public Connection getConnection() throws SQLException {
        try {
            final C conn = pool.borrowObject();
            if (conn == null) {
                return null;
            }
            return new PoolGuardConnectionWrapper<>(conn);
        } catch (final NoSuchElementException e) {
            throw new SQLException("Cannot get a connection, pool error " + e.getMessage(), e);
        } catch (final SQLException | RuntimeException e) {
            throw e;
        } catch (final InterruptedException e) {
            // Reset the interrupt status so it is visible to callers
            Thread.currentThread().interrupt();
            throw new SQLException("Cannot get a connection, general error", e);
        } catch (final Exception e) {
            throw new SQLException("Cannot get a connection, general error", e);
        }
    }

    /**
     * Throws {@link UnsupportedOperationException}
     *
     * @throws UnsupportedOperationException
     *             always thrown
     */
    @Override
    public Connection getConnection(final String userName, final String password) throws SQLException {
        throw new UnsupportedOperationException();
    }


    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException
     *             As this implementation does not support this feature.
     */
    @Override
    public int getLoginTimeout() {
        throw new UnsupportedOperationException("Login timeout is not supported.");
    }

    /**
     * Returns my log writer.
     *
     * @return my log writer
     * @see DataSource#getLogWriter
     */
    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * Gets the backing object pool.
     *
     * @return the backing object pool.
     */
    protected ObjectPool<C> getPool() {
        return pool;
    }

    /**
     * Returns the value of the accessToUnderlyingConnectionAllowed property.
     *
     * @return true if access to the underlying {@link Connection} is allowed, false otherwise.
     */
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface != null && iface.isInstance(this);
    }

    /**
     * Sets the value of the accessToUnderlyingConnectionAllowed property. It controls if the PoolGuard allows access to
     * the underlying connection. (Default: false)
     *
     * @param allow
     *            Access to the underlying connection is granted when true.
     */
    public void setAccessToUnderlyingConnectionAllowed(final boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }

    /**
     * Throws {@link UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException
     *             As this implementation does not support this feature.
     */
    @Override
    public void setLoginTimeout(final int seconds) {
        throw new UnsupportedOperationException("Login timeout is not supported.");
    }

    /**
     * Sets my log writer.
     *
     * @see DataSource#setLogWriter
     */
    @Override
    public void setLogWriter(final PrintWriter out) {
        logWriter = out;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (isWrapperFor(iface)) {
            return iface.cast(this);
        }
        throw new SQLException(this + " is not a wrapper for " + iface);
    }
}

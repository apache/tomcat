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
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool;

/**
 * A simple {@link DataSource} implementation that obtains
 * {@link Connection}s from the specified {@link ObjectPool}.
 *
 * @param <C> The connection type
 *
 * @author Rodney Waldhoff
 * @author Glenn L. Nielsen
 * @author James House
 * @author Dirk Verbeeck
 * @since 2.0
 */
public class PoolingDataSource<C extends Connection> implements DataSource {

    private static final Log log = LogFactory.getLog(PoolingDataSource.class);

    /** Controls access to the underlying connection */
    private boolean accessToUnderlyingConnectionAllowed = false;

    public PoolingDataSource(ObjectPool<C> pool) {
        if (null == pool) {
            throw new NullPointerException("Pool must not be null.");
        }
        _pool = pool;
        // Verify that _pool's factory refers back to it.  If not, log a warning and try to fix.
        if (_pool instanceof GenericObjectPool<?>) {
            PoolableConnectionFactory pcf = (PoolableConnectionFactory) ((GenericObjectPool<?>) _pool).getFactory();
            if (pcf == null) {
                throw new NullPointerException("PoolableConnectionFactory must not be null.");
            }
            if (pcf.getPool() != _pool) {
                log.warn(Utils.getMessage("poolingDataSource.factoryConfig"));
                @SuppressWarnings("unchecked") // PCF must have a pool of PCs
                ObjectPool<PoolableConnection> p = (ObjectPool<PoolableConnection>) _pool;
                pcf.setPool(p);
            }
        }
    }

    /**
     * Returns the value of the accessToUnderlyingConnectionAllowed property.
     *
     * @return true if access to the underlying is allowed, false otherwise.
     */
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    /**
     * Sets the value of the accessToUnderlyingConnectionAllowed property.
     * It controls if the PoolGuard allows access to the underlying connection.
     * (Default: false)
     *
     * @param allow Access to the underlying connection is granted when true.
     */
    public void setAccessToUnderlyingConnectionAllowed(boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }

    /* JDBC_4_ANT_KEY_BEGIN */
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("PoolingDataSource is not a wrapper.");
    }
    /* JDBC_4_ANT_KEY_END */

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    //--- DataSource methods -----------------------------------------

    /**
     * Return a {@link java.sql.Connection} from my pool,
     * according to the contract specified by {@link ObjectPool#borrowObject}.
     */
    @Override
    public Connection getConnection() throws SQLException {
        try {
            C conn = _pool.borrowObject();
            if (conn == null) {
                return null;
            }
            return new PoolGuardConnectionWrapper<>(conn);
        } catch(SQLException e) {
            throw e;
        } catch(NoSuchElementException e) {
            throw new SQLException("Cannot get a connection, pool error " + e.getMessage(), e);
        } catch(RuntimeException e) {
            throw e;
        } catch(Exception e) {
            throw new SQLException("Cannot get a connection, general error", e);
        }
    }

    /**
     * Throws {@link UnsupportedOperationException}
     * @throws UnsupportedOperationException
     */
    @Override
    public Connection getConnection(String uname, String passwd) throws SQLException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns my log writer.
     * @return my log writer
     * @see DataSource#getLogWriter
     */
    @Override
    public PrintWriter getLogWriter() {
        return _logWriter;
    }

    /**
     * Throws {@link UnsupportedOperationException}.
     * @throws UnsupportedOperationException As this
     *   implementation does not support this feature.
     */
    @Override
    public int getLoginTimeout() {
        throw new UnsupportedOperationException("Login timeout is not supported.");
    }

    /**
     * Throws {@link UnsupportedOperationException}.
     * @throws UnsupportedOperationException As this
     *   implementation does not support this feature.
     */
    @Override
    public void setLoginTimeout(int seconds) {
        throw new UnsupportedOperationException("Login timeout is not supported.");
    }

    /**
     * Sets my log writer.
     * @see DataSource#setLogWriter
     */
    @Override
    public void setLogWriter(PrintWriter out) {
        _logWriter = out;
    }

    /** My log writer. */
    private PrintWriter _logWriter = null;

    private final ObjectPool<C> _pool;

    protected ObjectPool<C> getPool() {
        return _pool;
    }

    /**
     * PoolGuardConnectionWrapper is a Connection wrapper that makes sure a
     * closed connection cannot be used anymore.
     * @since 2.0
     */
    private class PoolGuardConnectionWrapper<D extends Connection>
            extends DelegatingConnection<D> {

        PoolGuardConnectionWrapper(D delegate) {
            super(delegate);
        }

        /**
         * @see org.apache.tomcat.dbcp.dbcp2.DelegatingConnection#getDelegate()
         */
        @Override
        public D getDelegate() {
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

        @Override
        public void close() throws SQLException {
            if (getDelegateInternal() != null) {
                super.close();
                super.setDelegate(null);
            }
        }

        @Override
        public boolean isClosed() throws SQLException {
            if (getDelegateInternal() == null) {
                return true;
            }
            return super.isClosed();
        }
    }
}

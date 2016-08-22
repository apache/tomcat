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

package org.apache.tomcat.dbcp.dbcp2.cpdsadapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.tomcat.dbcp.dbcp2.DelegatingConnection;
import org.apache.tomcat.dbcp.dbcp2.DelegatingPreparedStatement;

/**
 * This class is the <code>Connection</code> that will be returned
 * from <code>PooledConnectionImpl.getConnection()</code>.
 * Most methods are wrappers around the JDBC 1.x <code>Connection</code>.
 * A few exceptions include preparedStatement and close.
 * In accordance with the JDBC specification this Connection cannot
 * be used after closed() is called.  Any further usage will result in an
 * SQLException.
 *
 * ConnectionImpl extends DelegatingConnection to enable access to the
 * underlying connection.
 *
 * @author John D. McNally
 * @since 2.0
 */
class ConnectionImpl extends DelegatingConnection<Connection> {

    private final boolean accessToUnderlyingConnectionAllowed;

    /** The object that instantiated this object */
     private final PooledConnectionImpl pooledConnection;

    /**
     * Creates a <code>ConnectionImpl</code>.
     *
     * @param pooledConnection The PooledConnection that is calling the ctor.
     * @param connection The JDBC 1.x Connection to wrap.
     * @param accessToUnderlyingConnectionAllowed if true, then access is allowed to the underlying connection
     */
    ConnectionImpl(final PooledConnectionImpl pooledConnection,
            final Connection connection,
            final boolean accessToUnderlyingConnectionAllowed) {
        super(connection);
        this.pooledConnection = pooledConnection;
        this.accessToUnderlyingConnectionAllowed =
            accessToUnderlyingConnectionAllowed;
    }

    /**
     * Marks the Connection as closed, and notifies the pool that the
     * pooled connection is available.
     * In accordance with the JDBC specification this Connection cannot
     * be used after closed() is called.  Any further usage will result in an
     * SQLException.
     *
     * @throws SQLException The database connection couldn't be closed.
     */
    @Override
    public void close() throws SQLException {
        if (!isClosedInternal()) {
            try {
                passivate();
            } finally {
                setClosedInternal(true);
                pooledConnection.notifyListeners();
            }
        }
    }

    /**
     * If pooling of <code>PreparedStatement</code>s is turned on in the
     * {@link DriverAdapterCPDS}, a pooled object may be returned, otherwise
     * delegate to the wrapped JDBC 1.x {@link java.sql.Connection}.
     *
     * @param sql SQL statement to be prepared
     * @return the prepared statement
     * @throws SQLException if this connection is closed or an error occurs
     * in the wrapped connection.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement
                (this, pooledConnection.prepareStatement(sql));
        }
        catch (final SQLException e) {
            handleException(e); // Does not return
            return null;
        }
    }

    /**
     * If pooling of <code>PreparedStatement</code>s is turned on in the
     * {@link DriverAdapterCPDS}, a pooled object may be returned, otherwise
     * delegate to the wrapped JDBC 1.x {@link java.sql.Connection}.
     *
     * @throws SQLException if this connection is closed or an error occurs
     * in the wrapped connection.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                                              final int resultSetConcurrency)
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement
                (this, pooledConnection.prepareStatement
                    (sql,resultSetType,resultSetConcurrency));
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType,
                                              final int resultSetConcurrency,
                                              final int resultSetHoldability)
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this,
                    pooledConnection.prepareStatement(sql, resultSetType,
                            resultSetConcurrency, resultSetHoldability));
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys)
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this,
                    pooledConnection.prepareStatement(sql, autoGeneratedKeys));
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int columnIndexes[])
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this,
                    pooledConnection.prepareStatement(sql, columnIndexes));
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String columnNames[])
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this,
                    pooledConnection.prepareStatement(sql, columnNames));
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    //
    // Methods for accessing the delegate connection
    //

    /**
     * If false, getDelegate() and getInnermostDelegate() will return null.
     * @return true if access is allowed to the underlying connection
     * @see ConnectionImpl
     */
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return accessToUnderlyingConnectionAllowed;
    }

    /**
     * Get the delegated connection, if allowed.
     * @return the internal connection, or null if access is not allowed.
     * @see #isAccessToUnderlyingConnectionAllowed()
     */
    @Override
    public Connection getDelegate() {
        if (isAccessToUnderlyingConnectionAllowed()) {
            return getDelegateInternal();
        }
        return null;
    }

    /**
     * Get the innermost connection, if allowed.
     * @return the innermost internal connection, or null if access is not allowed.
     * @see #isAccessToUnderlyingConnectionAllowed()
     */
    @Override
    public Connection getInnermostDelegate() {
        if (isAccessToUnderlyingConnectionAllowed()) {
            return super.getInnermostDelegateInternal();
        }
        return null;
    }

}

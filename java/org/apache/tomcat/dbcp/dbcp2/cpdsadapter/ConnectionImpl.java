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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.tomcat.dbcp.dbcp2.DelegatingCallableStatement;
import org.apache.tomcat.dbcp.dbcp2.DelegatingConnection;
import org.apache.tomcat.dbcp.dbcp2.DelegatingPreparedStatement;

/**
 * This class is the <code>Connection</code> that will be returned from
 * <code>PooledConnectionImpl.getConnection()</code>. Most methods are wrappers around the JDBC 1.x
 * <code>Connection</code>. A few exceptions include preparedStatement and close. In accordance with the JDBC
 * specification this Connection cannot be used after closed() is called. Any further usage will result in an
 * SQLException.
 * <p>
 * ConnectionImpl extends DelegatingConnection to enable access to the underlying connection.
 * </p>
 *
 * @since 2.0
 */
class ConnectionImpl extends DelegatingConnection<Connection> {

    private final boolean accessToUnderlyingConnectionAllowed;

    /** The object that instantiated this object */
    private final PooledConnectionImpl pooledConnection;

    /**
     * Creates a <code>ConnectionImpl</code>.
     *
     * @param pooledConnection
     *            The PooledConnection that is calling the ctor.
     * @param connection
     *            The JDBC 1.x Connection to wrap.
     * @param accessToUnderlyingConnectionAllowed
     *            if true, then access is allowed to the underlying connection
     */
    ConnectionImpl(final PooledConnectionImpl pooledConnection, final Connection connection,
            final boolean accessToUnderlyingConnectionAllowed) {
        super(connection);
        this.pooledConnection = pooledConnection;
        this.accessToUnderlyingConnectionAllowed = accessToUnderlyingConnectionAllowed;
    }

    /**
     * Marks the Connection as closed, and notifies the pool that the pooled connection is available.
     * <p>
     * In accordance with the JDBC specification this Connection cannot be used after closed() is called. Any further
     * usage will result in an SQLException.
     * </p>
     *
     * @throws SQLException
     *             The database connection couldn't be closed.
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
     * If pooling of <code>CallableStatement</code>s is turned on in the {@link DriverAdapterCPDS}, a pooled object may
     * be returned, otherwise delegate to the wrapped JDBC 1.x {@link java.sql.Connection}.
     *
     * @param sql
     *            an SQL statement that may contain one or more '?' parameter placeholders. Typically this statement is
     *            specified using JDBC call escape syntax.
     * @return a default <code>CallableStatement</code> object containing the pre-compiled SQL statement.
     * @exception SQLException
     *                Thrown if a database access error occurs or this method is called on a closed connection.
     * @since 2.4.0
     */
    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
        checkOpen();
        try {
            return new DelegatingCallableStatement(this, pooledConnection.prepareCall(sql));
        } catch (final SQLException e) {
            handleException(e); // Does not return
            return null;
        }
    }

    /**
     * If pooling of <code>CallableStatement</code>s is turned on in the {@link DriverAdapterCPDS}, a pooled object may
     * be returned, otherwise delegate to the wrapped JDBC 1.x {@link java.sql.Connection}.
     *
     * @param sql
     *            a <code>String</code> object that is the SQL statement to be sent to the database; may contain on or
     *            more '?' parameters.
     * @param resultSetType
     *            a result set type; one of <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *            <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>.
     * @param resultSetConcurrency
     *            a concurrency type; one of <code>ResultSet.CONCUR_READ_ONLY</code> or
     *            <code>ResultSet.CONCUR_UPDATABLE</code>.
     * @return a <code>CallableStatement</code> object containing the pre-compiled SQL statement that will produce
     *         <code>ResultSet</code> objects with the given type and concurrency.
     * @throws SQLException
     *             Thrown if a database access error occurs, this method is called on a closed connection or the given
     *             parameters are not <code>ResultSet</code> constants indicating type and concurrency.
     * @since 2.4.0
     */
    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency)
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingCallableStatement(this,
                    pooledConnection.prepareCall(sql, resultSetType, resultSetConcurrency));
        } catch (final SQLException e) {
            handleException(e); // Does not return
            return null;
        }
    }

    /**
     * If pooling of <code>CallableStatement</code>s is turned on in the {@link DriverAdapterCPDS}, a pooled object may
     * be returned, otherwise delegate to the wrapped JDBC 1.x {@link java.sql.Connection}.
     *
     * @param sql
     *            a <code>String</code> object that is the SQL statement to be sent to the database; may contain on or
     *            more '?' parameters.
     * @param resultSetType
     *            one of the following <code>ResultSet</code> constants: <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *            <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>.
     * @param resultSetConcurrency
     *            one of the following <code>ResultSet</code> constants: <code>ResultSet.CONCUR_READ_ONLY</code> or
     *            <code>ResultSet.CONCUR_UPDATABLE</code>.
     * @param resultSetHoldability
     *            one of the following <code>ResultSet</code> constants: <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code>
     *            or <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>.
     * @return a new <code>CallableStatement</code> object, containing the pre-compiled SQL statement, that will
     *         generate <code>ResultSet</code> objects with the given type, concurrency, and holdability.
     * @throws SQLException
     *             Thrown if a database access error occurs, this method is called on a closed connection or the given
     *             parameters are not <code>ResultSet</code> constants indicating type, concurrency, and holdability.
     * @since 2.4.0
     */
    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) throws SQLException {
        checkOpen();
        try {
            return new DelegatingCallableStatement(this,
                    pooledConnection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        } catch (final SQLException e) {
            handleException(e); // Does not return
            return null;
        }
    }

    /**
     * If pooling of <code>PreparedStatement</code>s is turned on in the {@link DriverAdapterCPDS}, a pooled object may
     * be returned, otherwise delegate to the wrapped JDBC 1.x {@link java.sql.Connection}.
     *
     * @param sql
     *            SQL statement to be prepared
     * @return the prepared statement
     * @throws SQLException
     *             if this connection is closed or an error occurs in the wrapped connection.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this, pooledConnection.prepareStatement(sql));
        } catch (final SQLException e) {
            handleException(e); // Does not return
            return null;
        }
    }

    /**
     * If pooling of <code>PreparedStatement</code>s is turned on in the {@link DriverAdapterCPDS}, a pooled object may
     * be returned, otherwise delegate to the wrapped JDBC 1.x {@link java.sql.Connection}.
     *
     * @throws SQLException
     *             if this connection is closed or an error occurs in the wrapped connection.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency)
            throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this,
                    pooledConnection.prepareStatement(sql, resultSetType, resultSetConcurrency));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this,
                    pooledConnection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this, pooledConnection.prepareStatement(sql, autoGeneratedKeys));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this, pooledConnection.prepareStatement(sql, columnIndexes));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        checkOpen();
        try {
            return new DelegatingPreparedStatement(this, pooledConnection.prepareStatement(sql, columnNames));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    //
    // Methods for accessing the delegate connection
    //

    /**
     * If false, getDelegate() and getInnermostDelegate() will return null.
     *
     * @return true if access is allowed to the underlying connection
     * @see ConnectionImpl
     */
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return accessToUnderlyingConnectionAllowed;
    }

    /**
     * Get the delegated connection, if allowed.
     *
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
     *
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

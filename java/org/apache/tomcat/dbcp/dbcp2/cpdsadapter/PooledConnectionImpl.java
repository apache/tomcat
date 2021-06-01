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
import java.util.Vector;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;

import org.apache.tomcat.dbcp.dbcp2.DelegatingConnection;
import org.apache.tomcat.dbcp.dbcp2.DelegatingPreparedStatement;
import org.apache.tomcat.dbcp.dbcp2.Jdbc41Bridge;
import org.apache.tomcat.dbcp.dbcp2.PStmtKey;
import org.apache.tomcat.dbcp.dbcp2.PoolableCallableStatement;
import org.apache.tomcat.dbcp.dbcp2.PoolablePreparedStatement;
import org.apache.tomcat.dbcp.dbcp2.PoolingConnection.StatementType;
import org.apache.tomcat.dbcp.pool2.DestroyMode;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.KeyedPooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;

/**
 * Implementation of PooledConnection that is returned by PooledConnectionDataSource.
 *
 * @since 2.0
 */
class PooledConnectionImpl
        implements PooledConnection, KeyedPooledObjectFactory<PStmtKey, DelegatingPreparedStatement> {

    private static final String CLOSED = "Attempted to use PooledConnection after closed() was called.";

    /**
     * The JDBC database connection that represents the physical db connection.
     */
    private Connection connection;

    /**
     * A DelegatingConnection used to create a PoolablePreparedStatementStub.
     */
    private final DelegatingConnection<?> delegatingConnection;

    /**
     * The JDBC database logical connection.
     */
    private Connection logicalConnection;

    /**
     * ConnectionEventListeners.
     */
    private final Vector<ConnectionEventListener> eventListeners;

    /**
     * StatementEventListeners.
     */
    private final Vector<StatementEventListener> statementEventListeners = new Vector<>();

    /**
     * Flag set to true, once {@link #close()} is called.
     */
    private boolean closed;

    /** My pool of {@link PreparedStatement}s. */
    private KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> pStmtPool;

    /**
     * Controls access to the underlying connection.
     */
    private boolean accessToUnderlyingConnectionAllowed;

    /**
     * Wraps the real connection.
     *
     * @param connection
     *            the connection to be wrapped.
     */
    PooledConnectionImpl(final Connection connection) {
        this.connection = connection;
        if (connection instanceof DelegatingConnection) {
            this.delegatingConnection = (DelegatingConnection<?>) connection;
        } else {
            this.delegatingConnection = new DelegatingConnection<>(connection);
        }
        eventListeners = new Vector<>();
        closed = false;
    }

    /**
     * My {@link KeyedPooledObjectFactory} method for activating {@link PreparedStatement}s.
     *
     * @param pooledObject Activates the underlying object.
     */
    @Override
    public void activateObject(final PStmtKey key, final PooledObject<DelegatingPreparedStatement> pooledObject)
            throws Exception {
        pooledObject.getObject().activate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addConnectionEventListener(final ConnectionEventListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    /* JDBC_4_ANT_KEY_BEGIN */
    @Override
    public void addStatementEventListener(final StatementEventListener listener) {
        if (!statementEventListeners.contains(listener)) {
            statementEventListeners.add(listener);
        }
    }
    /* JDBC_4_ANT_KEY_END */

    /**
     * Throws an SQLException, if isClosed is true
     */
    private void assertOpen() throws SQLException {
        if (closed) {
            throw new SQLException(CLOSED);
        }
    }

    /**
     * Closes the physical connection and marks this <code>PooledConnection</code> so that it may not be used to
     * generate any more logical <code>Connection</code>s.
     *
     * @throws SQLException
     *             Thrown when an error occurs or the connection is already closed.
     */
    @Override
    public void close() throws SQLException {
        assertOpen();
        closed = true;
        try {
            if (pStmtPool != null) {
                try {
                    pStmtPool.close();
                } finally {
                    pStmtPool = null;
                }
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Cannot close connection (return to pool failed)", e);
        } finally {
            try {
                connection.close();
            } finally {
                connection = null;
            }
        }
    }

    /**
     * Creates a {@link PStmtKey} for the given arguments.
     *
     * @param sql
     *            The SQL statement.
     * @return a {@link PStmtKey} for the given arguments.
     */
    protected PStmtKey createKey(final String sql) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull());
    }

    /**
     * Creates a {@link PStmtKey} for the given arguments.
     *
     * @param sql
     *            The SQL statement.
     * @param autoGeneratedKeys
     *            A flag indicating whether auto-generated keys should be returned; one of
     *            <code>Statement.RETURN_GENERATED_KEYS</code> or <code>Statement.NO_GENERATED_KEYS</code>.
     * @return a key to uniquely identify a prepared statement.
     */
    protected PStmtKey createKey(final String sql, final int autoGeneratedKeys) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), autoGeneratedKeys);
    }

    /**
     * Creates a {@link PStmtKey} for the given arguments.
     *
     * @param sql
     *            The SQL statement.
     * @param columnIndexes
     *            An array of column indexes indicating the columns that should be returned from the inserted row or
     *            rows.
     * @return a key to uniquely identify a prepared statement.
     */
    protected PStmtKey createKey(final String sql, final int[] columnIndexes) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), columnIndexes);
    }

    /**
     * Creates a {@link PStmtKey} for the given arguments.
     *
     * @param sql
     *            The SQL statement.
     * @param resultSetType
     *            A result set type; one of <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *            <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>.
     * @param resultSetConcurrency
     *            A concurrency type; one of <code>ResultSet.CONCUR_READ_ONLY</code> or
     *            <code>ResultSet.CONCUR_UPDATABLE</code>.
     * @return a key to uniquely identify a prepared statement.
     */
    protected PStmtKey createKey(final String sql, final int resultSetType, final int resultSetConcurrency) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), resultSetType,
                resultSetConcurrency);
    }

    /**
     * Creates a {@link PStmtKey} for the given arguments.
     *
     * @param sql
     *            The SQL statement.
     * @param resultSetType
     *            a result set type; one of <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *            <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>.
     * @param resultSetConcurrency
     *            A concurrency type; one of <code>ResultSet.CONCUR_READ_ONLY</code> or
     *            <code>ResultSet.CONCUR_UPDATABLE</code>
     * @param resultSetHoldability
     *            One of the following <code>ResultSet</code> constants: <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code>
     *            or <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>.
     * @return a key to uniquely identify a prepared statement.
     */
    protected PStmtKey createKey(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), resultSetType,
                resultSetConcurrency, resultSetHoldability);
    }

    /**
     * Creates a {@link PStmtKey} for the given arguments.
     *
     * @param sql
     *            The SQL statement.
     * @param resultSetType
     *            a result set type; one of <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *            <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @param resultSetConcurrency
     *            A concurrency type; one of <code>ResultSet.CONCUR_READ_ONLY</code> or
     *            <code>ResultSet.CONCUR_UPDATABLE</code>.
     * @param resultSetHoldability
     *            One of the following <code>ResultSet</code> constants: <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code>
     *            or <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>.
     * @param statementType
     *            The SQL statement type, prepared or callable.
     * @return a key to uniquely identify a prepared statement.
     * @since 2.4.0
     */
    protected PStmtKey createKey(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability, final StatementType statementType) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), resultSetType,
                resultSetConcurrency, resultSetHoldability, statementType);
    }

    /**
     * Creates a {@link PStmtKey} for the given arguments.
     *
     * @param sql
     *            The SQL statement.
     * @param resultSetType
     *            A result set type; one of <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *            <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>.
     * @param resultSetConcurrency
     *            A concurrency type; one of <code>ResultSet.CONCUR_READ_ONLY</code> or
     *            <code>ResultSet.CONCUR_UPDATABLE</code>.
     * @param statementType
     *            The SQL statement type, prepared or callable.
     * @return a key to uniquely identify a prepared statement.
     * @since 2.4.0
     */
    protected PStmtKey createKey(final String sql, final int resultSetType, final int resultSetConcurrency,
            final StatementType statementType) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), resultSetType,
                resultSetConcurrency, statementType);
    }

    /**
     * Creates a {@link PStmtKey} for the given arguments.
     *
     * @param sql
     *            The SQL statement.
     * @param statementType
     *            The SQL statement type, prepared or callable.
     * @return a key to uniquely identify a prepared statement.
     */
    protected PStmtKey createKey(final String sql, final StatementType statementType) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), statementType);
    }

    /**
     * Creates a {@link PStmtKey} for the given arguments.
     *
     * @param sql
     *            The SQL statement.
     * @param columnNames
     *            An array of column names indicating the columns that should be returned from the inserted row or rows.
     * @return a key to uniquely identify a prepared statement.
     */
    protected PStmtKey createKey(final String sql, final String[] columnNames) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), columnNames);
    }

    /**
     * My {@link KeyedPooledObjectFactory} method for destroying {@link PreparedStatement}s.
     *
     * @param key
     *            ignored
     * @param pooledObject
     *            the wrapped {@link PreparedStatement} to be destroyed.
     */
    @Override
    public void destroyObject(final PStmtKey key, final PooledObject<DelegatingPreparedStatement> pooledObject)
            throws Exception {
        pooledObject.getObject().getInnermostDelegate().close();
    }

    @Override
    public void destroyObject(PStmtKey key, PooledObject<DelegatingPreparedStatement> p,
            DestroyMode mode) throws Exception {
        destroyObject(key, p);
    }

    /**
     * Closes the physical connection and checks that the logical connection was closed as well.
     */
    @Override
    protected void finalize() throws Throwable {
        // Closing the Connection ensures that if anyone tries to use it,
        // an error will occur.
        try {
            connection.close();
        } catch (final Exception ignored) {
            // ignore
        }

        // make sure the last connection is marked as closed
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            throw new SQLException("PooledConnection was gc'ed, without its last Connection being closed.");
        }
    }

    private String getCatalogOrNull() {
        try {
            return connection == null ? null : connection.getCatalog();
        } catch (final SQLException e) {
            return null;
        }
    }

    private String getSchemaOrNull() {
        try {
            return connection == null ? null : Jdbc41Bridge.getSchema(connection);
        } catch (final SQLException e) {
            return null;
        }
    }

    /**
     * Returns a JDBC connection.
     *
     * @return The database connection.
     * @throws SQLException
     *             if the connection is not open or the previous logical connection is still open
     */
    @Override
    public Connection getConnection() throws SQLException {
        assertOpen();
        // make sure the last connection is marked as closed
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            // should notify pool of error so the pooled connection can
            // be removed !FIXME!
            throw new SQLException("PooledConnection was reused, without its previous Connection being closed.");
        }

        // the spec requires that this return a new Connection instance.
        logicalConnection = new ConnectionImpl(this, connection, isAccessToUnderlyingConnectionAllowed());
        return logicalConnection;
    }

    /**
     * Returns the value of the accessToUnderlyingConnectionAllowed property.
     *
     * @return true if access to the underlying is allowed, false otherwise.
     */
    public synchronized boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    /**
     * My {@link KeyedPooledObjectFactory} method for creating {@link PreparedStatement}s.
     *
     * @param key
     *            The key for the {@link PreparedStatement} to be created.
     */
    @Override
    public PooledObject<DelegatingPreparedStatement> makeObject(final PStmtKey key) throws Exception {
        if (null == key) {
            throw new IllegalArgumentException("Prepared statement key is null or invalid.");
        }
        if (key.getStmtType() == StatementType.PREPARED_STATEMENT) {
            final PreparedStatement statement = (PreparedStatement) key.createStatement(connection);
            @SuppressWarnings({"rawtypes", "unchecked" }) // Unable to find way to avoid this
            final PoolablePreparedStatement pps = new PoolablePreparedStatement(statement, key, pStmtPool,
                    delegatingConnection);
            return new DefaultPooledObject<DelegatingPreparedStatement>(pps);
        }
        final CallableStatement statement = (CallableStatement) key.createStatement(connection);
        @SuppressWarnings("unchecked")
        final PoolableCallableStatement pcs = new PoolableCallableStatement(statement, key, pStmtPool,
                (DelegatingConnection<Connection>) delegatingConnection);
        return new DefaultPooledObject<DelegatingPreparedStatement>(pcs);
    }

    /**
     * Normalizes the given SQL statement, producing a canonical form that is semantically equivalent to the original.
     * @param sql
     *            The SQL statement.
     * @return the normalized SQL statement.
     */
    protected String normalizeSQL(final String sql) {
        return sql.trim();
    }

    /**
     * Sends a connectionClosed event.
     */
    void notifyListeners() {
        final ConnectionEvent event = new ConnectionEvent(this);
        final Object[] listeners = eventListeners.toArray();
        for (final Object listener : listeners) {
            ((ConnectionEventListener) listener).connectionClosed(event);
        }
    }

    /**
     * My {@link KeyedPooledObjectFactory} method for passivating {@link PreparedStatement}s. Currently invokes
     * {@link PreparedStatement#clearParameters}.
     *
     * @param key
     *            ignored
     * @param pooledObject
     *            a wrapped {@link PreparedStatement}
     */
    @Override
    public void passivateObject(final PStmtKey key, final PooledObject<DelegatingPreparedStatement> pooledObject)
            throws Exception {
        final DelegatingPreparedStatement dps = pooledObject.getObject();
        dps.clearParameters();
        dps.passivate();
    }

    /**
     * Creates or obtains a {@link CallableStatement} from my pool.
     *
     * @param sql
     *            an SQL statement that may contain one or more '?' parameter placeholders. Typically this statement is
     *            specified using JDBC call escape syntax.
     * @return a default <code>CallableStatement</code> object containing the pre-compiled SQL statement.
     * @exception SQLException
     *                Thrown if a database access error occurs or this method is called on a closed connection.
     * @since 2.4.0
     */
    CallableStatement prepareCall(final String sql) throws SQLException {
        if (pStmtPool == null) {
            return connection.prepareCall(sql);
        }
        try {
            return (CallableStatement) pStmtPool.borrowObject(createKey(sql, StatementType.CALLABLE_STATEMENT));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareCall from pool failed", e);
        }
    }

    /**
     * Creates or obtains a {@link CallableStatement} from my pool.
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
    CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency)
            throws SQLException {
        if (pStmtPool == null) {
            return connection.prepareCall(sql, resultSetType, resultSetConcurrency);
        }
        try {
            return (CallableStatement) pStmtPool.borrowObject(
                    createKey(sql, resultSetType, resultSetConcurrency, StatementType.CALLABLE_STATEMENT));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareCall from pool failed", e);
        }
    }

    /**
     * Creates or obtains a {@link CallableStatement} from my pool.
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
    CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) throws SQLException {
        if (pStmtPool == null) {
            return connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        try {
            return (CallableStatement) pStmtPool.borrowObject(createKey(sql, resultSetType, resultSetConcurrency,
                    resultSetHoldability, StatementType.CALLABLE_STATEMENT));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareCall from pool failed", e);
        }
    }

    /**
     * Creates or obtains a {@link PreparedStatement} from my pool.
     *
     * @param sql the SQL statement.
     * @return a {@link PoolablePreparedStatement}
     * @throws SQLException Thrown if a database access error occurs, this method is called on a closed connection, or
     *         the borrow failed.
     */
    PreparedStatement prepareStatement(final String sql) throws SQLException {
        if (pStmtPool == null) {
            return connection.prepareStatement(sql);
        }
        try {
            return pStmtPool.borrowObject(createKey(sql));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    /**
     * Creates or obtains a {@link PreparedStatement} from my pool.
     *
     * @param sql
     *            an SQL statement that may contain one or more '?' IN parameter placeholders.
     * @param autoGeneratedKeys
     *            a flag indicating whether auto-generated keys should be returned; one of
     *            <code>Statement.RETURN_GENERATED_KEYS</code> or <code>Statement.NO_GENERATED_KEYS</code>.
     * @return a {@link PoolablePreparedStatement}
     * @throws SQLException Thrown if a database access error occurs, this method is called on a closed connection, or
     *         the borrow failed.
     * @see Connection#prepareStatement(String, int)
     */
    PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        if (pStmtPool == null) {
            return connection.prepareStatement(sql, autoGeneratedKeys);
        }
        try {
            return pStmtPool.borrowObject(createKey(sql, autoGeneratedKeys));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        if (pStmtPool == null) {
            return connection.prepareStatement(sql, columnIndexes);
        }
        try {
            return pStmtPool.borrowObject(createKey(sql, columnIndexes));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    /**
     * Creates or obtains a {@link PreparedStatement} from my pool.
     *
     * @param sql
     *            a <code>String</code> object that is the SQL statement to be sent to the database; may contain one or
     *            more '?' IN parameters.
     * @param resultSetType
     *            a result set type; one of <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *            <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>.
     * @param resultSetConcurrency
     *            a concurrency type; one of <code>ResultSet.CONCUR_READ_ONLY</code> or
     *            <code>ResultSet.CONCUR_UPDATABLE</code>.
     *
     * @return a {@link PoolablePreparedStatement}.
     * @throws SQLException Thrown if a database access error occurs, this method is called on a closed connection, or
     *         the borrow failed.
     * @see Connection#prepareStatement(String, int, int)
     */
    PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency)
            throws SQLException {
        if (pStmtPool == null) {
            return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }
        try {
            return pStmtPool.borrowObject(createKey(sql, resultSetType, resultSetConcurrency));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) throws SQLException {
        if (pStmtPool == null) {
            return connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }
        try {
            return pStmtPool.borrowObject(createKey(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        if (pStmtPool == null) {
            return connection.prepareStatement(sql, columnNames);
        }
        try {
            return pStmtPool.borrowObject(createKey(sql, columnNames));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeConnectionEventListener(final ConnectionEventListener listener) {
        eventListeners.remove(listener);
    }

    /* JDBC_4_ANT_KEY_BEGIN */
    @Override
    public void removeStatementEventListener(final StatementEventListener listener) {
        statementEventListeners.remove(listener);
    }
    /* JDBC_4_ANT_KEY_END */

    /**
     * Sets the value of the accessToUnderlyingConnectionAllowed property. It controls if the PoolGuard allows access to
     * the underlying connection. (Default: false.)
     *
     * @param allow
     *            Access to the underlying connection is granted when true.
     */
    public synchronized void setAccessToUnderlyingConnectionAllowed(final boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }

    public void setStatementPool(final KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> statementPool) {
        pStmtPool = statementPool;
    }

    /**
     * My {@link KeyedPooledObjectFactory} method for validating {@link PreparedStatement}s.
     *
     * @param key
     *            Ignored.
     * @param pooledObject
     *            Ignored.
     * @return {@code true}
     */
    @Override
    public boolean validateObject(final PStmtKey key, final PooledObject<DelegatingPreparedStatement> pooledObject) {
        return true;
    }

    /**
     * @since 2.6.0
     */
    @Override
    public synchronized String toString() {
        final StringBuilder builder = new StringBuilder(super.toString());
        builder.append("[connection=");
        builder.append(connection);
        builder.append(", delegatingConnection=");
        builder.append(delegatingConnection);
        builder.append(", logicalConnection=");
        builder.append(logicalConnection);
        builder.append(", eventListeners=");
        builder.append(eventListeners);
        builder.append(", statementEventListeners=");
        builder.append(statementEventListeners);
        builder.append(", closed=");
        builder.append(closed);
        builder.append(", pStmtPool=");
        builder.append(pStmtPool);
        builder.append(", accessToUnderlyingConnectionAllowed=");
        builder.append(accessToUnderlyingConnectionAllowed);
        builder.append("]");
        return builder.toString();
    }
}

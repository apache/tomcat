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

package org.apache.tomcat.dbcp.dbcp.cpdsadapter;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Vector;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;

import org.apache.tomcat.dbcp.dbcp.DelegatingConnection;
import org.apache.tomcat.dbcp.dbcp.DelegatingPreparedStatement;
import org.apache.tomcat.dbcp.dbcp.PStmtKey;
import org.apache.tomcat.dbcp.dbcp.PoolableCallableStatement;
import org.apache.tomcat.dbcp.dbcp.PoolablePreparedStatement;
import org.apache.tomcat.dbcp.dbcp.PoolingConnection.StatementType;
import org.apache.tomcat.dbcp.pool.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool.KeyedPoolableObjectFactory;
import org.apache.tomcat.util.compat.JreCompat;

/**
 * Implementation of PooledConnection that is returned by
 * PooledConnectionDataSource.
 *
 * @author John D. McNally
 */
class PooledConnectionImpl
        implements PooledConnection, KeyedPoolableObjectFactory<PStmtKey, DelegatingPreparedStatement> {

    private static final String CLOSED
            = "Attempted to use PooledConnection after closed() was called.";

    /**
     * The JDBC database connection that represents the physical db connection.
     */
    private Connection connection = null;

    /**
     * A DelegatingConnection used to create a PoolablePreparedStatementStub
     */
    private final DelegatingConnection delegatingConnection;

    /**
     * The JDBC database logical connection.
     */
    private Connection logicalConnection = null;

    /**
     * ConnectionEventListeners
     */
    private final Vector<ConnectionEventListener> eventListeners;

    /**
     * StatementEventListeners
     */
    private final Vector<StatementEventListener> statementEventListeners = new Vector<StatementEventListener>();

    /**
     * flag set to true, once close() is called.
     */
    boolean isClosed; // TODO - make private?

    /** My pool of {*link PreparedStatement}s. */
    // TODO - make final?
    protected KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> pstmtPool = null;

    /**
     * Controls access to the underlying connection
     */
    private boolean accessToUnderlyingConnectionAllowed = false;

    /**
     * Wrap the real connection.
     * @param connection the connection to be wrapped
     * @param pool the pool to use
     */
    PooledConnectionImpl(Connection connection, KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> pool) {
        this.connection = connection;
        if (connection instanceof DelegatingConnection) {
            this.delegatingConnection = (DelegatingConnection) connection;
        } else {
            this.delegatingConnection = new DelegatingConnection(connection);
        }
        eventListeners = new Vector<ConnectionEventListener>();
        isClosed = false;
        if (pool != null) {
            pstmtPool = pool;
            pstmtPool.setFactory(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    @Override
    public void addStatementEventListener(StatementEventListener listener) {
        if (!statementEventListeners.contains(listener)) {
            statementEventListeners.add(listener);
        }
    }

    /**
     * Closes the physical connection and marks this
     * <code>PooledConnection</code> so that it may not be used
     * to generate any more logical <code>Connection</code>s.
     *
     * @exception SQLException if an error occurs or the connection is already closed
     */
    @Override
    public void close() throws SQLException {
        assertOpen();
        isClosed = true;
        try {
            if (pstmtPool != null) {
                try {
                    pstmtPool.close();
                } finally {
                    pstmtPool = null;
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
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
     * Throws an SQLException, if isClosed is true
     */
    private void assertOpen() throws SQLException {
        if (isClosed) {
            throw new SQLException(CLOSED);
        }
    }

    /**
     * Returns a JDBC connection.
     *
     * @return The database connection.
     * @throws SQLException if the connection is not open or the previous logical connection is still open
     */
    @Override
    public Connection getConnection() throws SQLException {
        assertOpen();
        // make sure the last connection is marked as closed
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            // should notify pool of error so the pooled connection can
            // be removed !FIXME!
            throw new SQLException("PooledConnection was reused, without"
                    + "its previous Connection being closed.");
        }

        // the spec requires that this return a new Connection instance.
        logicalConnection = new ConnectionImpl(
                this, connection, isAccessToUnderlyingConnectionAllowed());
        return logicalConnection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeConnectionEventListener(
            ConnectionEventListener listener) {
        eventListeners.remove(listener);
    }

    @Override
    public void removeStatementEventListener(StatementEventListener listener) {
        statementEventListeners.remove(listener);
    }

    /**
     * Closes the physical connection and checks that the logical connection
     * was closed as well.
     */
    @Override
    protected void finalize() throws Throwable {
        // Closing the Connection ensures that if anyone tries to use it,
        // an error will occur.
        try {
            connection.close();
        } catch (Exception ignored) {
        }

        // make sure the last connection is marked as closed
        if (logicalConnection != null && !logicalConnection.isClosed()) {
            throw new SQLException("PooledConnection was gc'ed, without"
                    + "its last Connection being closed.");
        }
    }

    /**
     * sends a connectionClosed event.
     */
    void notifyListeners() {
        ConnectionEvent event = new ConnectionEvent(this);
        Object[] listeners = eventListeners.toArray();
        for (int i = 0; i < listeners.length; i++) {
            ((ConnectionEventListener) listeners[i]).connectionClosed(event);
        }
    }

    // -------------------------------------------------------------------
    // The following code implements a PreparedStatement pool

    /**
     * Create or obtain a {@link PreparedStatement} from my pool.
     * @param sql the SQL statement
     * @return a {@link org.apache.tomcat.dbcp.dbcp.PoolablePreparedStatement}
     */
    PreparedStatement prepareStatement(String sql) throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql);
        } else {
            try {
                return pstmtPool.borrowObject(createKey(sql));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLException("Borrow prepareStatement from pool failed", e);
            }
        }
    }

    /**
     * Create or obtain a {@link PreparedStatement} from my pool.
     * @param sql a <code>String</code> object that is the SQL statement to
     *            be sent to the database; may contain one or more '?' IN
     *            parameters
     * @param resultSetType a result set type; one of
     *         <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     *         <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
     *         <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @param resultSetConcurrency a concurrency type; one of
     *         <code>ResultSet.CONCUR_READ_ONLY</code> or
     *         <code>ResultSet.CONCUR_UPDATABLE</code>
     *
     * @return a {@link org.apache.tomcat.dbcp.dbcp.PoolablePreparedStatement}
     * @see Connection#prepareStatement(String, int, int)
     */
    PreparedStatement prepareStatement(String sql, int resultSetType,
                                       int resultSetConcurrency)
            throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        } else {
            try {
                return pstmtPool.borrowObject(createKey(sql,resultSetType,resultSetConcurrency));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLException("Borrow prepareStatement from pool failed", e);
            }
        }
    }

    /**
     * Create or obtain a {@link PreparedStatement} from my pool.
     * @param sql an SQL statement that may contain one or more '?' IN
     *        parameter placeholders
     * @param autoGeneratedKeys a flag indicating whether auto-generated keys
     *        should be returned; one of
     *        <code>Statement.RETURN_GENERATED_KEYS</code> or
     *        <code>Statement.NO_GENERATED_KEYS</code>
     * @return a {@link org.apache.tomcat.dbcp.dbcp.PoolablePreparedStatement}
     * @see Connection#prepareStatement(String, int)
     */
    PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
            throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, autoGeneratedKeys);
        } else {
            try {
                return pstmtPool.borrowObject(createKey(sql,autoGeneratedKeys));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLException("Borrow prepareStatement from pool failed", e);
            }
        }
    }

    PreparedStatement prepareStatement(String sql, int resultSetType,
            int resultSetConcurrency, int resultSetHoldability)
    throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, resultSetType,
                    resultSetConcurrency, resultSetHoldability);
        } else {
            try {
                return pstmtPool.borrowObject(createKey(sql, resultSetType, resultSetConcurrency,
                            resultSetHoldability));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLException("Borrow prepareStatement from pool failed", e);
            }
        }
    }

    PreparedStatement prepareStatement(String sql, int columnIndexes[])
    throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, columnIndexes);
        } else {
            try {
                return pstmtPool.borrowObject(createKey(sql, columnIndexes));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLException("Borrow prepareStatement from pool failed", e);
            }
        }
    }

    PreparedStatement prepareStatement(String sql, String columnNames[])
    throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, columnNames);
        } else {
            try {
                return pstmtPool.borrowObject(createKey(sql, columnNames));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLException("Borrow prepareStatement from pool failed", e);
            }
        }
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected PStmtKey createKey(String sql, int autoGeneratedKeys) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), autoGeneratedKeys);
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected PStmtKey createKey(String sql, int resultSetType,
            int resultSetConcurrency, int resultSetHoldability) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), resultSetType,
                resultSetConcurrency, resultSetHoldability);
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected PStmtKey createKey(String sql, int columnIndexes[]) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), columnIndexes);
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected PStmtKey createKey(String sql, String columnNames[]) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), columnNames);
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected PStmtKey createKey(String sql, int resultSetType,
                               int resultSetConcurrency) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), resultSetType,
                            resultSetConcurrency);
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected PStmtKey createKey(String sql) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull());
    }

    /**
     * Normalize the given SQL statement, producing a
     * cannonical form that is semantically equivalent to the original.
     */
    protected String normalizeSQL(String sql) {
        return sql.trim();
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
            return connection == null ? null : JreCompat.getInstance().getSchema(connection);
        } catch (final SQLException e) {
            return null;
        }
    }

    /**
     * My {*link KeyedPoolableObjectFactory} method for creating
     * {*link PreparedStatement}s.
     * @param key the key for the {*link PreparedStatement} to be created
     */
    @Override
    public DelegatingPreparedStatement makeObject(PStmtKey key) throws Exception {
        if (null == key) {
            throw new IllegalArgumentException();
        }

        if (key.getStmtType() == StatementType.PREPARED_STATEMENT) {
            final PreparedStatement statement = (PreparedStatement) key.createStatement(connection);
            @SuppressWarnings({"rawtypes", "unchecked" }) // Unable to find way to avoid this
            final PoolablePreparedStatement pps = new PoolablePreparedStatement(statement, key, pstmtPool,
                    delegatingConnection);
            return pps;
        }
        final CallableStatement statement = (CallableStatement) key.createStatement(connection);
        return new PoolableCallableStatement(statement, key, pstmtPool, delegatingConnection);
    }

    /**
     * My {*link KeyedPoolableObjectFactory} method for destroying
     * {*link PreparedStatement}s.
     * @param key ignored
     * @param obj the {*link PreparedStatement} to be destroyed.
     */
    @Override
    public void destroyObject(PStmtKey key, DelegatingPreparedStatement obj) throws Exception {
        obj.getInnermostDelegate().close();
    }

    /**
     * My {*link KeyedPoolableObjectFactory} method for validating
     * {*link PreparedStatement}s.
     * @param key ignored
     * @param obj ignored
     * @return <code>true</code>
     */
    @Override
    public boolean validateObject(PStmtKey key, DelegatingPreparedStatement obj) {
        return true;
    }

    /**
     * My {*link KeyedPoolableObjectFactory} method for activating
     * {*link PreparedStatement}s.
     * @param key ignored
     * @param obj ignored
     */
    @Override
    public void activateObject(PStmtKey key, DelegatingPreparedStatement obj) throws Exception {
        obj.activate();
    }

    /**
     * My {*link KeyedPoolableObjectFactory} method for passivating
     * {*link PreparedStatement}s.  Currently invokes {*link PreparedStatement#clearParameters}.
     * @param key ignored
     * @param obj a {*link PreparedStatement}
     */
    @Override
    public void passivateObject(PStmtKey key, DelegatingPreparedStatement obj) throws Exception {
        ((PreparedStatement) obj).clearParameters();
        obj.passivate();
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
     * Sets the value of the accessToUnderlyingConnectionAllowed property.
     * It controls if the PoolGuard allows access to the underlying connection.
     * (Default: false)
     *
     * @param allow Access to the underlying connection is granted when true.
     */
    public synchronized void setAccessToUnderlyingConnectionAllowed(boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }
}

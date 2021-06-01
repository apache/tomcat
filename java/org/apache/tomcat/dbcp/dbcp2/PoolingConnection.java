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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.NoSuchElementException;

import org.apache.tomcat.dbcp.pool2.DestroyMode;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.KeyedPooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;

/**
 * A {@link DelegatingConnection} that pools {@link PreparedStatement}s.
 * <p>
 * The {@link #prepareStatement} and {@link #prepareCall} methods, rather than creating a new PreparedStatement each
 * time, may actually pull the statement from a pool of unused statements. The {@link PreparedStatement#close} method of
 * the returned statement doesn't actually close the statement, but rather returns it to the pool. (See
 * {@link PoolablePreparedStatement}, {@link PoolableCallableStatement}.)
 * </p>
 *
 * @see PoolablePreparedStatement
 * @since 2.0
 */
public class PoolingConnection extends DelegatingConnection<Connection>
        implements KeyedPooledObjectFactory<PStmtKey, DelegatingPreparedStatement> {

    /**
     * Statement types.
     *
     * @since 2.0 protected enum.
     * @since 2.4.0 public enum.
     */
    public enum StatementType {

        /**
         * Callable statement.
         */
        CALLABLE_STATEMENT,

        /**
         * Prepared statement.
         */
        PREPARED_STATEMENT
    }

    /** Pool of {@link PreparedStatement}s. and {@link CallableStatement}s */
    private KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> pstmtPool;

    private boolean clearStatementPoolOnReturn = false;

    /**
     * Constructor.
     *
     * @param connection
     *            the underlying {@link Connection}.
     */
    public PoolingConnection(final Connection connection) {
        super(connection);
    }

    /**
     * {@link KeyedPooledObjectFactory} method for activating pooled statements.
     *
     * @param key
     *            ignored
     * @param pooledObject
     *            wrapped pooled statement to be activated
     */
    @Override
    public void activateObject(final PStmtKey key, final PooledObject<DelegatingPreparedStatement> pooledObject)
            throws Exception {
        pooledObject.getObject().activate();
    }

    /**
     * Closes and frees all {@link PreparedStatement}s or {@link CallableStatement}s from the pool, and close the
     * underlying connection.
     */
    @Override
    public synchronized void close() throws SQLException {
        try {
            if (null != pstmtPool) {
                final KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> oldpool = pstmtPool;
                pstmtPool = null;
                try {
                    oldpool.close();
                } catch (final RuntimeException e) {
                    throw e;
                } catch (final Exception e) {
                    throw new SQLException("Cannot close connection", e);
                }
            }
        } finally {
            try {
                getDelegateInternal().close();
            } finally {
                setClosedInternal(true);
            }
        }
    }

    /**
     * Notification from {@link PoolableConnection} that we returned to the pool.
     *
     * @throws SQLException when <code>clearStatementPoolOnReturn</code> is true and the statement pool could not be
     *                      cleared
     * @since 2.8.0
     */
    public void connectionReturnedToPool() throws SQLException {
        if (pstmtPool != null && clearStatementPoolOnReturn) {
            try {
                pstmtPool.clear();
            } catch (final Exception e) {
                throw new SQLException("Error clearing statement pool", e);
            }
        }
    }

    /**
     * Creates a PStmtKey for the given arguments.
     *
     * @param sql
     *            the SQL string used to define the statement
     *
     * @return the PStmtKey created for the given arguments.
     */
    protected PStmtKey createKey(final String sql) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull());
    }

    /**
     * Creates a PStmtKey for the given arguments.
     *
     * @param sql
     *            the SQL string used to define the statement
     * @param columnIndexes
     *            An array of column indexes indicating the columns that should be returned from the inserted row or
     *            rows.
     *
     * @return the PStmtKey created for the given arguments.
     */
    protected PStmtKey createKey(final String sql, final int[] columnIndexes) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), columnIndexes);
    }

    /**
     * Creates a PStmtKey for the given arguments.
     *
     * @param sql
     *            the SQL string used to define the statement
     * @param autoGeneratedKeys
     *            A flag indicating whether auto-generated keys should be returned; one of
     *            <code>Statement.RETURN_GENERATED_KEYS</code> or <code>Statement.NO_GENERATED_KEYS</code>.
     *
     * @return the PStmtKey created for the given arguments.
     */
    protected PStmtKey createKey(final String sql, final int autoGeneratedKeys) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), autoGeneratedKeys);
    }

    /**
     * Creates a PStmtKey for the given arguments.
     *
     * @param sql
     *            the SQL string used to define the statement
     * @param resultSetType
     *            result set type
     * @param resultSetConcurrency
     *            result set concurrency
     *
     * @return the PStmtKey created for the given arguments.
     */
    protected PStmtKey createKey(final String sql, final int resultSetType, final int resultSetConcurrency) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), resultSetType, resultSetConcurrency);
    }

    /**
     * Creates a PStmtKey for the given arguments.
     *
     * @param sql
     *            the SQL string used to define the statement
     * @param resultSetType
     *            result set type
     * @param resultSetConcurrency
     *            result set concurrency
     * @param resultSetHoldability
     *            result set holdability
     *
     * @return the PStmtKey created for the given arguments.
     */
    protected PStmtKey createKey(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), resultSetType, resultSetConcurrency,
                resultSetHoldability);
    }

    /**
     * Creates a PStmtKey for the given arguments.
     *
     * @param sql
     *            the SQL string used to define the statement
     * @param resultSetType
     *            result set type
     * @param resultSetConcurrency
     *            result set concurrency
     * @param resultSetHoldability
     *            result set holdability
     * @param statementType
     *            statement type
     *
     * @return the PStmtKey created for the given arguments.
     */
    protected PStmtKey createKey(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability, final StatementType statementType) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), resultSetType, resultSetConcurrency,
                resultSetHoldability, statementType);
    }

    /**
     * Creates a PStmtKey for the given arguments.
     *
     * @param sql
     *            the SQL string used to define the statement
     * @param resultSetType
     *            result set type
     * @param resultSetConcurrency
     *            result set concurrency
     * @param statementType
     *            statement type
     *
     * @return the PStmtKey created for the given arguments.
     */
    protected PStmtKey createKey(final String sql, final int resultSetType, final int resultSetConcurrency,
            final StatementType statementType) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), resultSetType, resultSetConcurrency, statementType);
    }

    /**
     * Creates a PStmtKey for the given arguments.
     *
     * @param sql
     *            the SQL string used to define the statement
     * @param statementType
     *            statement type
     *
     * @return the PStmtKey created for the given arguments.
     */
    protected PStmtKey createKey(final String sql, final StatementType statementType) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), statementType, null);
    }

    /**
     * Creates a PStmtKey for the given arguments.
     *
     * @param sql
     *            the SQL string used to define the statement
     * @param columnNames
     *            column names
     *
     * @return the PStmtKey created for the given arguments.
     */
    protected PStmtKey createKey(final String sql, final String[] columnNames) {
        return new PStmtKey(normalizeSQL(sql), getCatalogOrNull(), getSchemaOrNull(), columnNames);
    }

    /**
     * {@link KeyedPooledObjectFactory} method for destroying PoolablePreparedStatements and PoolableCallableStatements.
     * Closes the underlying statement.
     *
     * @param key
     *            ignored
     * @param pooledObject
     *            the wrapped pooled statement to be destroyed.
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

    private String getCatalogOrNull() {
        String catalog = null;
        try {
            catalog = getCatalog();
        } catch (final SQLException e) {
            // Ignored
        }
        return catalog;
    }

    private String getSchemaOrNull() {
        String schema = null;
        try {
            schema = getSchema();
        } catch (final SQLException e) {
            // Ignored
        }
        return schema;
    }

    /**
     * Returns the prepared statement pool we're using.
     *
     * @return statement pool
     * @since 2.8.0
     */
    public KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> getStatementPool() {
        return pstmtPool;
    }

    /**
     * {@link KeyedPooledObjectFactory} method for creating {@link PoolablePreparedStatement}s or
     * {@link PoolableCallableStatement}s. The <code>stmtType</code> field in the key determines whether a
     * PoolablePreparedStatement or PoolableCallableStatement is created.
     *
     * @param key
     *            the key for the {@link PreparedStatement} to be created
     * @see #createKey(String, int, int, StatementType)
     */
    @Override
    public PooledObject<DelegatingPreparedStatement> makeObject(final PStmtKey key) throws Exception {
        if (null == key) {
            throw new IllegalArgumentException("Prepared statement key is null or invalid.");
        }
        if (key.getStmtType() == StatementType.PREPARED_STATEMENT) {
            final PreparedStatement statement = (PreparedStatement) key.createStatement(getDelegate());
            @SuppressWarnings({"rawtypes", "unchecked" }) // Unable to find way to avoid this
            final PoolablePreparedStatement pps = new PoolablePreparedStatement(statement, key, pstmtPool, this);
            return new DefaultPooledObject<DelegatingPreparedStatement>(pps);
        }
        final CallableStatement statement = (CallableStatement) key.createStatement(getDelegate());
        final PoolableCallableStatement pcs = new PoolableCallableStatement(statement, key, pstmtPool, this);
        return new DefaultPooledObject<DelegatingPreparedStatement>(pcs);
    }

    /**
     * Normalizes the given SQL statement, producing a canonical form that is semantically equivalent to the original.
     *
     * @param sql The statement to be normalized.
     *
     * @return The canonical form of the supplied SQL statement.
     */
    protected String normalizeSQL(final String sql) {
        return sql.trim();
    }

    /**
     * {@link KeyedPooledObjectFactory} method for passivating {@link PreparedStatement}s or {@link CallableStatement}s.
     * Invokes {@link PreparedStatement#clearParameters}.
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
     * Creates or obtains a {@link CallableStatement} from the pool.
     *
     * @param key
     *            a {@link PStmtKey} for the given arguments
     * @return a {@link PoolableCallableStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     */
    private CallableStatement prepareCall(final PStmtKey key) throws SQLException {
        return (CallableStatement) prepareStatement(key);
    }

    /**
     * Creates or obtains a {@link CallableStatement} from the pool.
     *
     * @param sql
     *            the SQL string used to define the CallableStatement
     * @return a {@link PoolableCallableStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     */
    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
        return prepareCall(createKey(sql, StatementType.CALLABLE_STATEMENT));
    }

    /**
     * Creates or obtains a {@link CallableStatement} from the pool.
     *
     * @param sql
     *            the SQL string used to define the CallableStatement
     * @param resultSetType
     *            result set type
     * @param resultSetConcurrency
     *            result set concurrency
     * @return a {@link PoolableCallableStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     */
    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency)
            throws SQLException {
        return prepareCall(createKey(sql, resultSetType, resultSetConcurrency, StatementType.CALLABLE_STATEMENT));
    }

    /**
     * Creates or obtains a {@link CallableStatement} from the pool.
     *
     * @param sql
     *            the SQL string used to define the CallableStatement
     * @param resultSetType
     *            result set type
     * @param resultSetConcurrency
     *            result set concurrency
     * @param resultSetHoldability
     *            result set holdability
     * @return a {@link PoolableCallableStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     */
    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) throws SQLException {
        return prepareCall(createKey(sql, resultSetType, resultSetConcurrency,
                resultSetHoldability, StatementType.CALLABLE_STATEMENT));
    }

    /**
     * Creates or obtains a {@link PreparedStatement} from the pool.
     *
     * @param key
     *            a {@link PStmtKey} for the given arguments
     * @return a {@link PoolablePreparedStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     */
    private PreparedStatement prepareStatement(final PStmtKey key) throws SQLException {
        if (null == pstmtPool) {
            throw new SQLException("Statement pool is null - closed or invalid PoolingConnection.");
        }
        try {
            return pstmtPool.borrowObject(key);
        } catch (final NoSuchElementException e) {
            throw new SQLException("MaxOpenPreparedStatements limit reached", e);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new SQLException("Borrow prepareStatement from pool failed", e);
        }
    }

    /**
     * Creates or obtains a {@link PreparedStatement} from the pool.
     *
     * @param sql
     *            the SQL string used to define the PreparedStatement
     * @return a {@link PoolablePreparedStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        return prepareStatement(createKey(sql));
    }

    /*
     * Creates or obtains a {@link PreparedStatement} from the pool.
     *
     * @param sql
     *            the SQL string used to define the PreparedStatement
     * @param autoGeneratedKeys
     *            A flag indicating whether auto-generated keys should be returned; one of
     *            <code>Statement.RETURN_GENERATED_KEYS</code> or <code>Statement.NO_GENERATED_KEYS</code>.
     * @return a {@link PoolablePreparedStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        return prepareStatement(createKey(sql, autoGeneratedKeys));
    }

    /**
     * Creates or obtains a {@link PreparedStatement} from the pool.
     *
     * @param sql
     *            the SQL string used to define the PreparedStatement
     * @param columnIndexes
     *            An array of column indexes indicating the columns that should be returned from the inserted row or
     *            rows.
     * @return a {@link PoolablePreparedStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     *
     */
    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        return prepareStatement(createKey(sql, columnIndexes));
    }

    /**
     * Creates or obtains a {@link PreparedStatement} from the pool.
     *
     * @param sql
     *            the SQL string used to define the PreparedStatement
     * @param resultSetType
     *            result set type
     * @param resultSetConcurrency
     *            result set concurrency
     * @return a {@link PoolablePreparedStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency)
            throws SQLException {
        return prepareStatement(createKey(sql, resultSetType, resultSetConcurrency));
    }

    /**
     * Creates or obtains a {@link PreparedStatement} from the pool.
     *
     * @param sql
     *            the SQL string used to define the PreparedStatement
     * @param resultSetType
     *            result set type
     * @param resultSetConcurrency
     *            result set concurrency
     * @param resultSetHoldability
     *            result set holdability
     * @return a {@link PoolablePreparedStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency,
            final int resultSetHoldability) throws SQLException {
        return prepareStatement(createKey(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
    }

    /**
     * Creates or obtains a {@link PreparedStatement} from the pool.
     *
     * @param sql
     *            the SQL string used to define the PreparedStatement
     * @param columnNames
     *            column names
     * @return a {@link PoolablePreparedStatement}
     * @throws SQLException
     *             Wraps an underlying exception.
     */
    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        return prepareStatement(createKey(sql, columnNames));
    }

    /**
     * Sets whether the pool of statements should be cleared when the connection is returned to its pool.
     * Default is false.
     *
     * @param clearStatementPoolOnReturn clear or not
     * @since 2.8.0
     */
    public void setClearStatementPoolOnReturn(final boolean clearStatementPoolOnReturn) {
        this.clearStatementPoolOnReturn = clearStatementPoolOnReturn;
    }

    /**
     * Sets the prepared statement pool.
     *
     * @param pool
     *            the prepared statement pool.
     */
    public void setStatementPool(final KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> pool) {
        pstmtPool = pool;
    }

    @Override
    public synchronized String toString() {
        if (pstmtPool != null) {
            return "PoolingConnection: " + pstmtPool.toString();
        }
        return "PoolingConnection: null";
    }

    /**
     * {@link KeyedPooledObjectFactory} method for validating pooled statements. Currently always returns true.
     *
     * @param key
     *            ignored
     * @param pooledObject
     *            ignored
     * @return {@code true}
     */
    @Override
    public boolean validateObject(final PStmtKey key, final PooledObject<DelegatingPreparedStatement> pooledObject) {
        return true;
    }
}

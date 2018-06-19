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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.List;

/**
 * A base delegating implementation of {@link Statement}.
 * <p>
 * All of the methods from the {@link Statement} interface simply check to see that the {@link Statement} is active, and
 * call the corresponding method on the "delegate" provided in my constructor.
 * <p>
 * Extends AbandonedTrace to implement Statement tracking and logging of code which created the Statement. Tracking the
 * Statement ensures that the Connection which created it can close any open Statement's on Connection close.
 *
 * @since 2.0
 */
public class DelegatingStatement extends AbandonedTrace implements Statement {

    /** My delegate. */
    private Statement statement;

    /** The connection that created me. **/
    private DelegatingConnection<?> connection;

    /**
     * Create a wrapper for the Statement which traces this Statement to the Connection which created it and the code
     * which created it.
     *
     * @param statement
     *            the {@link Statement} to delegate all calls to.
     * @param connection
     *            the {@link DelegatingConnection} that created this statement.
     */
    public DelegatingStatement(final DelegatingConnection<?> connection, final Statement statement) {
        super(connection);
        this.statement = statement;
        this.connection = connection;
    }

    /**
     * Returns my underlying {@link Statement}.
     *
     * @return my underlying {@link Statement}.
     * @see #getInnermostDelegate
     */
    public Statement getDelegate() {
        return statement;
    }

    /**
     * If my underlying {@link Statement} is not a {@code DelegatingStatement}, returns it, otherwise recursively
     * invokes this method on my delegate.
     * <p>
     * Hence this method will return the first delegate that is not a {@code DelegatingStatement} or {@code null} when
     * no non-{@code DelegatingStatement} delegate can be found by traversing this chain.
     * </p>
     * <p>
     * This method is useful when you may have nested {@code DelegatingStatement}s, and you want to make sure to obtain
     * a "genuine" {@link Statement}.
     * </p>
     *
     * @return The innermost delegate.
     *
     * @see #getDelegate
     */
    public Statement getInnermostDelegate() {
        Statement s = statement;
        while (s != null && s instanceof DelegatingStatement) {
            s = ((DelegatingStatement) s).getDelegate();
            if (this == s) {
                return null;
            }
        }
        return s;
    }

    /**
     * Sets my delegate.
     *
     * @param statement
     *            my delegate.
     */
    public void setDelegate(final Statement statement) {
        this.statement = statement;
    }

    private boolean closed = false;

    protected boolean isClosedInternal() {
        return closed;
    }

    protected void setClosedInternal(final boolean closed) {
        this.closed = closed;
    }

    protected void checkOpen() throws SQLException {
        if (isClosed()) {
            throw new SQLException(this.getClass().getName() + " with address: \"" + this.toString() + "\" is closed.");
        }
    }

    /**
     * Close this DelegatingStatement, and close any ResultSets that were not explicitly closed.
     */
    @Override
    public void close() throws SQLException {
        if (isClosed()) {
            return;
        }
        try {
            try {
                if (connection != null) {
                    connection.removeTrace(this);
                    connection = null;
                }

                // The JDBC spec requires that a statement close any open
                // ResultSet's when it is closed.
                // FIXME The PreparedStatement we're wrapping should handle this for us.
                // See bug 17301 for what could happen when ResultSets are closed twice.
                final List<AbandonedTrace> resultSets = getTrace();
                if (resultSets != null) {
                    final ResultSet[] set = resultSets.toArray(new ResultSet[resultSets.size()]);
                    for (final ResultSet element : set) {
                        element.close();
                    }
                    clearTrace();
                }

                if (statement != null) {
                    statement.close();
                }
            } catch (final SQLException e) {
                handleException(e);
            }
        } finally {
            closed = true;
            statement = null;
        }
    }

    protected void handleException(final SQLException e) throws SQLException {
        if (connection != null) {
            connection.handleException(e);
        } else {
            throw e;
        }
    }

    /**
     *
     * @throws SQLException
     *             thrown by the delegating statement.
     * @since 2.4.0 made public, was protected in 2.3.0.
     */
    public void activate() throws SQLException {
        if (statement instanceof DelegatingStatement) {
            ((DelegatingStatement) statement).activate();
        }
    }

    /**
     *
     * @throws SQLException
     *             thrown by the delegating statement.
     * @since 2.4.0 made public, was protected in 2.3.0.
     */
    public void passivate() throws SQLException {
        if (statement instanceof DelegatingStatement) {
            ((DelegatingStatement) statement).passivate();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        checkOpen();
        return getConnectionInternal(); // return the delegating connection that created this
    }

    protected DelegatingConnection<?> getConnectionInternal() {
        return connection;
    }

    @Override
    public ResultSet executeQuery(final String sql) throws SQLException {
        checkOpen();
        if (connection != null) {
            connection.setLastUsed();
        }
        try {
            return DelegatingResultSet.wrapResultSet(this, statement.executeQuery(sql));
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(this, statement.getResultSet());
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int executeUpdate(final String sql) throws SQLException {
        checkOpen();
        if (connection != null) {
            connection.setLastUsed();
        }
        try {
            return statement.executeUpdate(sql);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        checkOpen();
        try {
            return statement.getMaxFieldSize();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public void setMaxFieldSize(final int max) throws SQLException {
        checkOpen();
        try {
            statement.setMaxFieldSize(max);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public int getMaxRows() throws SQLException {
        checkOpen();
        try {
            return statement.getMaxRows();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public void setMaxRows(final int max) throws SQLException {
        checkOpen();
        try {
            statement.setMaxRows(max);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setEscapeProcessing(final boolean enable) throws SQLException {
        checkOpen();
        try {
            statement.setEscapeProcessing(enable);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        checkOpen();
        try {
            return statement.getQueryTimeout();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public void setQueryTimeout(final int seconds) throws SQLException {
        checkOpen();
        try {
            statement.setQueryTimeout(seconds);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void cancel() throws SQLException {
        checkOpen();
        try {
            statement.cancel();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkOpen();
        try {
            return statement.getWarnings();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkOpen();
        try {
            statement.clearWarnings();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCursorName(final String name) throws SQLException {
        checkOpen();
        try {
            statement.setCursorName(name);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean execute(final String sql) throws SQLException {
        checkOpen();
        if (connection != null) {
            connection.setLastUsed();
        }
        try {
            return statement.execute(sql);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public int getUpdateCount() throws SQLException {
        checkOpen();
        try {
            return statement.getUpdateCount();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        checkOpen();
        try {
            return statement.getMoreResults();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void setFetchDirection(final int direction) throws SQLException {
        checkOpen();
        try {
            statement.setFetchDirection(direction);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public int getFetchDirection() throws SQLException {
        checkOpen();
        try {
            return statement.getFetchDirection();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public void setFetchSize(final int rows) throws SQLException {
        checkOpen();
        try {
            statement.setFetchSize(rows);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public int getFetchSize() throws SQLException {
        checkOpen();
        try {
            return statement.getFetchSize();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        checkOpen();
        try {
            return statement.getResultSetConcurrency();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getResultSetType() throws SQLException {
        checkOpen();
        try {
            return statement.getResultSetType();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public void addBatch(final String sql) throws SQLException {
        checkOpen();
        try {
            statement.addBatch(sql);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void clearBatch() throws SQLException {
        checkOpen();
        try {
            statement.clearBatch();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public int[] executeBatch() throws SQLException {
        checkOpen();
        if (connection != null) {
            connection.setLastUsed();
        }
        try {
            return statement.executeBatch();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    /**
     * Returns a String representation of this object.
     *
     * @return String
     */
    @Override
    public String toString() {
        return statement == null ? "NULL" : statement.toString();
    }

    @Override
    public boolean getMoreResults(final int current) throws SQLException {
        checkOpen();
        try {
            return statement.getMoreResults(current);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(this, statement.getGeneratedKeys());
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkOpen();
        if (connection != null) {
            connection.setLastUsed();
        }
        try {
            return statement.executeUpdate(sql, autoGeneratedKeys);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int executeUpdate(final String sql, final int columnIndexes[]) throws SQLException {
        checkOpen();
        if (connection != null) {
            connection.setLastUsed();
        }
        try {
            return statement.executeUpdate(sql, columnIndexes);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int executeUpdate(final String sql, final String columnNames[]) throws SQLException {
        checkOpen();
        if (connection != null) {
            connection.setLastUsed();
        }
        try {
            return statement.executeUpdate(sql, columnNames);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkOpen();
        if (connection != null) {
            connection.setLastUsed();
        }
        try {
            return statement.execute(sql, autoGeneratedKeys);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean execute(final String sql, final int columnIndexes[]) throws SQLException {
        checkOpen();
        if (connection != null) {
            connection.setLastUsed();
        }
        try {
            return statement.execute(sql, columnIndexes);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean execute(final String sql, final String columnNames[]) throws SQLException {
        checkOpen();
        if (connection != null) {
            connection.setLastUsed();
        }
        try {
            return statement.execute(sql, columnNames);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        checkOpen();
        try {
            return statement.getResultSetHoldability();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    /*
     * Note was protected prior to JDBC 4
     */
    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        } else if (iface.isAssignableFrom(statement.getClass())) {
            return true;
        } else {
            return statement.isWrapperFor(iface);
        }
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        } else if (iface.isAssignableFrom(statement.getClass())) {
            return iface.cast(statement);
        } else {
            return statement.unwrap(iface);
        }
    }

    @Override
    public void setPoolable(final boolean poolable) throws SQLException {
        checkOpen();
        try {
            statement.setPoolable(poolable);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean isPoolable() throws SQLException {
        checkOpen();
        try {
            return statement.isPoolable();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        checkOpen();
        try {
            statement.closeOnCompletion();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        checkOpen();
        try {
            return statement.isCloseOnCompletion();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        // This is required because of statement pooling. The poolable
        // statements will always be strongly held by the statement pool. If the
        // delegating statements that wrap the poolable statement are not
        // strongly held they will be garbage collected but at that point the
        // poolable statements need to be returned to the pool else there will
        // be a leak of statements from the pool. Closing this statement will
        // close all the wrapped statements and return any poolable statements
        // to the pool.
        close();
        super.finalize();
    }
}

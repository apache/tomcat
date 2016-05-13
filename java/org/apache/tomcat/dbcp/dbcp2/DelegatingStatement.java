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
 * All of the methods from the {@link Statement} interface
 * simply check to see that the {@link Statement} is active,
 * and call the corresponding method on the "delegate"
 * provided in my constructor.
 * <p>
 * Extends AbandonedTrace to implement Statement tracking and
 * logging of code which created the Statement. Tracking the
 * Statement ensures that the Connection which created it can
 * close any open Statement's on Connection close.
 *
 * @author Rodney Waldhoff
 * @author Glenn L. Nielsen
 * @author James House
 * @author Dirk Verbeeck
 * @since 2.0
 */
public class DelegatingStatement extends AbandonedTrace implements Statement {
    /** My delegate. */
    private Statement _stmt = null;
    /** The connection that created me. **/
    private DelegatingConnection<?> _conn = null;

    /**
     * Create a wrapper for the Statement which traces this
     * Statement to the Connection which created it and the
     * code which created it.
     *
     * @param s the {@link Statement} to delegate all calls to.
     * @param c the {@link DelegatingConnection} that created this statement.
     */
    public DelegatingStatement(final DelegatingConnection<?> c, final Statement s) {
        super(c);
        _stmt = s;
        _conn = c;
    }

    /**
     * Returns my underlying {@link Statement}.
     * @return my underlying {@link Statement}.
     * @see #getInnermostDelegate
     */
    public Statement getDelegate() {
        return _stmt;
    }


    /**
     * If my underlying {@link Statement} is not a
     * {@code DelegatingStatement}, returns it,
     * otherwise recursively invokes this method on
     * my delegate.
     * <p>
     * Hence this method will return the first
     * delegate that is not a {@code DelegatingStatement}
     * or {@code null} when no non-{@code DelegatingStatement}
     * delegate can be found by transversing this chain.
     * <p>
     * This method is useful when you may have nested
     * {@code DelegatingStatement}s, and you want to make
     * sure to obtain a "genuine" {@link Statement}.
     * @see #getDelegate
     * @return the statement
     */
    public Statement getInnermostDelegate() {
        Statement s = _stmt;
        while(s != null && s instanceof DelegatingStatement) {
            s = ((DelegatingStatement)s).getDelegate();
            if(this == s) {
                return null;
            }
        }
        return s;
    }

    /**
     * Sets my delegate.
     * @param s The statement
     */
    public void setDelegate(final Statement s) {
        _stmt = s;
    }

    private boolean _closed = false;

    protected boolean isClosedInternal() {
        return _closed;
    }

    protected void setClosedInternal(final boolean closed) {
        this._closed = closed;
    }

    protected void checkOpen() throws SQLException {
        if(isClosed()) {
            throw new SQLException
                (this.getClass().getName() + " with address: \"" +
                this.toString() + "\" is closed.");
        }
    }

    /**
     * Close this DelegatingStatement, and close
     * any ResultSets that were not explicitly closed.
     */
    @Override
    public void close() throws SQLException {
        if (isClosed()) {
            return;
        }
        try {
            try {
                if (_conn != null) {
                    _conn.removeTrace(this);
                    _conn = null;
                }

                // The JDBC spec requires that a statement close any open
                // ResultSet's when it is closed.
                // FIXME The PreparedStatement we're wrapping should handle this for us.
                // See bug 17301 for what could happen when ResultSets are closed twice.
                final List<AbandonedTrace> resultSets = getTrace();
                if( resultSets != null) {
                    final ResultSet[] set = resultSets.toArray(new ResultSet[resultSets.size()]);
                    for (final ResultSet element : set) {
                        element.close();
                    }
                    clearTrace();
                }

                if (_stmt != null) {
                    _stmt.close();
                }
            }
            catch (final SQLException e) {
                handleException(e);
            }
        }
        finally {
            _closed = true;
            _stmt = null;
        }
    }

    protected void handleException(final SQLException e) throws SQLException {
        if (_conn != null) {
            _conn.handleException(e);
        }
        else {
            throw e;
        }
    }

    protected void activate() throws SQLException {
        if(_stmt instanceof DelegatingStatement) {
            ((DelegatingStatement)_stmt).activate();
        }
    }

    protected void passivate() throws SQLException {
        if(_stmt instanceof DelegatingStatement) {
            ((DelegatingStatement)_stmt).passivate();
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        checkOpen();
        return getConnectionInternal(); // return the delegating connection that created this
    }

    protected DelegatingConnection<?> getConnectionInternal() {
        return _conn;
    }

    @Override
    public ResultSet executeQuery(final String sql) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return DelegatingResultSet.wrapResultSet(this,_stmt.executeQuery(sql));
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(this,_stmt.getResultSet());
        }
        catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int executeUpdate(final String sql) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.executeUpdate(sql);
        } catch (final SQLException e) {
            handleException(e); return 0;
        }
    }

    @Override
    public int getMaxFieldSize() throws SQLException
    { checkOpen(); try { return _stmt.getMaxFieldSize(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void setMaxFieldSize(final int max) throws SQLException
    { checkOpen(); try { _stmt.setMaxFieldSize(max); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int getMaxRows() throws SQLException
    { checkOpen(); try { return _stmt.getMaxRows(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void setMaxRows(final int max) throws SQLException
    { checkOpen(); try { _stmt.setMaxRows(max); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setEscapeProcessing(final boolean enable) throws SQLException
    { checkOpen(); try { _stmt.setEscapeProcessing(enable); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int getQueryTimeout() throws SQLException
    { checkOpen(); try { return _stmt.getQueryTimeout(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void setQueryTimeout(final int seconds) throws SQLException
    { checkOpen(); try { _stmt.setQueryTimeout(seconds); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void cancel() throws SQLException
    { checkOpen(); try { _stmt.cancel(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public SQLWarning getWarnings() throws SQLException
    { checkOpen(); try { return _stmt.getWarnings(); } catch (final SQLException e) { handleException(e); throw new AssertionError(); } }

    @Override
    public void clearWarnings() throws SQLException
    { checkOpen(); try { _stmt.clearWarnings(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setCursorName(final String name) throws SQLException
    { checkOpen(); try { _stmt.setCursorName(name); } catch (final SQLException e) { handleException(e); } }

    @Override
    public boolean execute(final String sql) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.execute(sql);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public int getUpdateCount() throws SQLException
    { checkOpen(); try { return _stmt.getUpdateCount(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public boolean getMoreResults() throws SQLException
    { checkOpen(); try { return _stmt.getMoreResults(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public void setFetchDirection(final int direction) throws SQLException
    { checkOpen(); try { _stmt.setFetchDirection(direction); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int getFetchDirection() throws SQLException
    { checkOpen(); try { return _stmt.getFetchDirection(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void setFetchSize(final int rows) throws SQLException
    { checkOpen(); try { _stmt.setFetchSize(rows); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int getFetchSize() throws SQLException
    { checkOpen(); try { return _stmt.getFetchSize(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public int getResultSetConcurrency() throws SQLException
    { checkOpen(); try { return _stmt.getResultSetConcurrency(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public int getResultSetType() throws SQLException
    { checkOpen(); try { return _stmt.getResultSetType(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void addBatch(final String sql) throws SQLException
    { checkOpen(); try { _stmt.addBatch(sql); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void clearBatch() throws SQLException
    { checkOpen(); try { _stmt.clearBatch(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int[] executeBatch() throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.executeBatch();
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
    return _stmt == null ? "NULL" : _stmt.toString();
    }

    @Override
    public boolean getMoreResults(final int current) throws SQLException
    { checkOpen(); try { return _stmt.getMoreResults(current); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        checkOpen();
        try {
            return DelegatingResultSet.wrapResultSet(this, _stmt.getGeneratedKeys());
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.executeUpdate(sql, autoGeneratedKeys);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int executeUpdate(final String sql, final int columnIndexes[]) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.executeUpdate(sql, columnIndexes);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int executeUpdate(final String sql, final String columnNames[]) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.executeUpdate(sql, columnNames);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.execute(sql, autoGeneratedKeys);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean execute(final String sql, final int columnIndexes[]) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.execute(sql, columnIndexes);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean execute(final String sql, final String columnNames[]) throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return _stmt.execute(sql, columnNames);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public int getResultSetHoldability() throws SQLException
    { checkOpen(); try { return _stmt.getResultSetHoldability(); } catch (final SQLException e) { handleException(e); return 0; } }

    /*
     * Note was protected prior to JDBC 4
     */
    @Override
    public boolean isClosed() throws SQLException {
        return _closed;
    }


    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        } else if (iface.isAssignableFrom(_stmt.getClass())) {
            return true;
        } else {
            return _stmt.isWrapperFor(iface);
        }
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        } else if (iface.isAssignableFrom(_stmt.getClass())) {
            return iface.cast(_stmt);
        } else {
            return _stmt.unwrap(iface);
        }
    }

    @Override
    public void setPoolable(final boolean poolable) throws SQLException {
        checkOpen();
        try {
            _stmt.setPoolable(poolable);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean isPoolable() throws SQLException {
        checkOpen();
        try {
            return _stmt.isPoolable();
        }
        catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        checkOpen();
        try {
            _stmt.closeOnCompletion();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        checkOpen();
        try {
            return _stmt.isCloseOnCompletion();
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

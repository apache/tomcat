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

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.ClientInfoStatus;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * A base delegating implementation of {@link Connection}.
 * <p>
 * All of the methods from the {@link Connection} interface
 * simply check to see that the {@link Connection} is active,
 * and call the corresponding method on the "delegate"
 * provided in my constructor.
 * <p>
 * Extends AbandonedTrace to implement Connection tracking and
 * logging of code which created the Connection. Tracking the
 * Connection ensures that the AbandonedObjectPool can close
 * this connection and recycle it if its pool of connections
 * is nearing exhaustion and this connection's last usage is
 * older than the removeAbandonedTimeout.
 *
 * @param <C> the Connection type
 *
 * @author Rodney Waldhoff
 * @author Glenn L. Nielsen
 * @author James House
 * @author Dirk Verbeeck
 * @since 2.0
 */
public class DelegatingConnection<C extends Connection> extends AbandonedTrace
        implements Connection {

    private static final Map<String, ClientInfoStatus> EMPTY_FAILED_PROPERTIES =
        Collections.<String, ClientInfoStatus>emptyMap();

    /** My delegate {@link Connection}. */
    private volatile C _conn = null;

    private volatile boolean _closed = false;

    private boolean _cacheState = true;
    private Boolean _autoCommitCached = null;
    private Boolean _readOnlyCached = null;
    private Integer defaultQueryTimeout = null;

    /**
     * Create a wrapper for the Connection which traces this
     * Connection in the AbandonedObjectPool.
     *
     * @param c the {@link Connection} to delegate all calls to.
     */
    public DelegatingConnection(C c) {
        super();
        _conn = c;
    }


    /**
     * Returns a string representation of the metadata associated with
     * the innnermost delegate connection.
     */
    @Override
    public String toString() {
        String s = null;

        Connection c = this.getInnermostDelegateInternal();
        if (c != null) {
            try {
                if (c.isClosed()) {
                    s = "connection is closed";
                }
                else {
                    StringBuffer sb = new StringBuffer();
                    sb.append(hashCode());
                    DatabaseMetaData meta = c.getMetaData();
                    if (meta != null) {
                        sb.append(", URL=");
                        sb.append(meta.getURL());
                        sb.append(", UserName=");
                        sb.append(meta.getUserName());
                        sb.append(", ");
                        sb.append(meta.getDriverName());
                        s = sb.toString();
                    }
                }
            }
            catch (SQLException ex) {
                // Ignore
            }
        }

        if (s == null) {
            s = super.toString();
        }

        return s;
    }

    /**
     * Returns my underlying {@link Connection}.
     * @return my underlying {@link Connection}.
     */
    public C getDelegate() {
        return getDelegateInternal();
    }

    protected final C getDelegateInternal() {
        return _conn;
    }

    /**
     * Compares innermost delegate to the given connection.
     *
     * @param c connection to compare innermost delegate with
     * @return true if innermost delegate equals <code>c</code>
     */
    public boolean innermostDelegateEquals(Connection c) {
        Connection innerCon = getInnermostDelegateInternal();
        if (innerCon == null) {
            return c == null;
        }
        return innerCon.equals(c);
    }


    /**
     * If my underlying {@link Connection} is not a
     * <tt>DelegatingConnection</tt>, returns it,
     * otherwise recursively invokes this method on
     * my delegate.
     * <p>
     * Hence this method will return the first
     * delegate that is not a <tt>DelegatingConnection</tt>,
     * or <tt>null</tt> when no non-<tt>DelegatingConnection</tt>
     * delegate can be found by traversing this chain.
     * <p>
     * This method is useful when you may have nested
     * <tt>DelegatingConnection</tt>s, and you want to make
     * sure to obtain a "genuine" {@link Connection}.
     */
    public Connection getInnermostDelegate() {
        return getInnermostDelegateInternal();
    }


    /**
     * Although this method is public, it is part of the internal API and should
     * not be used by clients. The signature of this method may change at any
     * time including in ways that break backwards compatibility.
     */
    public final Connection getInnermostDelegateInternal() {
        Connection c = _conn;
        while(c != null && c instanceof DelegatingConnection) {
            c = ((DelegatingConnection<?>)c).getDelegateInternal();
            if(this == c) {
                return null;
            }
        }
        return c;
    }

    /** Sets my delegate. */
    public void setDelegate(C c) {
        _conn = c;
    }

    /**
     * Closes the underlying connection, and close any Statements that were not
     * explicitly closed. Sub-classes that override this method must:
     * <ol>
     * <li>Call passivate()</li>
     * <li>Call close (or the equivalent appropriate action) on the wrapped
     *     connection</li>
     * <li>Set _closed to <code>false</code></li>
     * </ol>
     */
    @Override
    public void close() throws SQLException {
        if (!_closed) {
            closeInternal();
        }
    }

    protected boolean isClosedInternal() {
        return _closed;
    }

    protected void setClosedInternal(boolean closed) {
        this._closed = closed;
    }

    protected final void closeInternal() throws SQLException {
        try {
            passivate();
        } finally {
            try {
                _conn.close();
            } finally {
                _closed = true;
            }
        }
    }

    protected void handleException(SQLException e) throws SQLException {
        throw e;
    }

    private void initializeStatement(DelegatingStatement ds) throws SQLException {
        if (defaultQueryTimeout != null &&
                defaultQueryTimeout.intValue() != ds.getQueryTimeout()) {
            ds.setQueryTimeout(defaultQueryTimeout.intValue());
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkOpen();
        try {
            DelegatingStatement ds =
                    new DelegatingStatement(this, _conn.createStatement());
            initializeStatement(ds);
            return ds;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Statement createStatement(int resultSetType,
                                     int resultSetConcurrency) throws SQLException {
        checkOpen();
        try {
            DelegatingStatement ds = new DelegatingStatement(
                    this, _conn.createStatement(resultSetType,resultSetConcurrency));
            initializeStatement(ds);
            return ds;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkOpen();
        try {
            DelegatingPreparedStatement dps = new DelegatingPreparedStatement(
                    this, _conn.prepareStatement(sql));
            initializeStatement(dps);
            return dps;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(String sql,
                                              int resultSetType,
                                              int resultSetConcurrency) throws SQLException {
        checkOpen();
        try {
            DelegatingPreparedStatement dps = new DelegatingPreparedStatement(
                    this, _conn.prepareStatement(sql,resultSetType,resultSetConcurrency));
            initializeStatement(dps);
            return dps;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        checkOpen();
        try {
            DelegatingCallableStatement dcs =
                    new DelegatingCallableStatement(this, _conn.prepareCall(sql));
            initializeStatement(dcs);
            return dcs;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public CallableStatement prepareCall(String sql,
                                         int resultSetType,
                                         int resultSetConcurrency) throws SQLException {
        checkOpen();
        try {
            DelegatingCallableStatement dcs = new DelegatingCallableStatement(
                    this, _conn.prepareCall(sql, resultSetType,resultSetConcurrency));
            initializeStatement(dcs);
            return dcs;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


    @Override
    public void clearWarnings() throws SQLException {
        checkOpen();
        try {
            _conn.clearWarnings();
        } catch (SQLException e) {
            handleException(e);
        }
    }


    @Override
    public void commit() throws SQLException {
        checkOpen();
        try {
            _conn.commit();
        } catch (SQLException e) {
            handleException(e);
        }
    }


    /**
     * Returns the state caching flag.
     *
     * @return  the state caching flag
     */
    public boolean getCacheState() {
        return _cacheState;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkOpen();
        if (_cacheState && _autoCommitCached != null) {
            return _autoCommitCached.booleanValue();
        }
        try {
            _autoCommitCached = Boolean.valueOf(_conn.getAutoCommit());
            return _autoCommitCached.booleanValue();
        } catch (SQLException e) {
            handleException(e);
            return false;
        }
    }


    @Override
    public String getCatalog() throws SQLException {
        checkOpen();
        try {
            return _conn.getCatalog();
        } catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkOpen();
        try {
            return new DelegatingDatabaseMetaData(this, _conn.getMetaData());
        } catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


    @Override
    public int getTransactionIsolation() throws SQLException {
        checkOpen();
        try {
            return _conn.getTransactionIsolation();
        } catch (SQLException e) {
            handleException(e);
            return -1;
        }
    }


    @Override
    public Map<String,Class<?>> getTypeMap() throws SQLException {
        checkOpen();
        try {
            return _conn.getTypeMap();
        } catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkOpen();
        try {
            return _conn.getWarnings();
        } catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


    @Override
    public boolean isReadOnly() throws SQLException {
        checkOpen();
        if (_cacheState && _readOnlyCached != null) {
            return _readOnlyCached.booleanValue();
        }
        try {
            _readOnlyCached = Boolean.valueOf(_conn.isReadOnly());
            return _readOnlyCached.booleanValue();
        } catch (SQLException e) {
            handleException(e);
            return false;
        }
    }


    @Override
    public String nativeSQL(String sql) throws SQLException {
        checkOpen();
        try {
            return _conn.nativeSQL(sql);
        } catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


    @Override
    public void rollback() throws SQLException {
        checkOpen();
        try {
            _conn.rollback();
        } catch (SQLException e) {
            handleException(e);
        }
    }


    /**
     * Obtain the default query timeout that will be used for {@link Statement}s
     * created from this connection. <code>null</code> means that the driver
     * default will be used.
     */
    public Integer getDefaultQueryTimeout() {
        return defaultQueryTimeout;
    }


    /**
     * Set the default query timeout that will be used for {@link Statement}s
     * created from this connection. <code>null</code> means that the driver
     * default will be used.
     */
    public void setDefaultQueryTimeout(Integer defaultQueryTimeout) {
        this.defaultQueryTimeout = defaultQueryTimeout;
    }


    /**
     * Sets the state caching flag.
     *
     * @param cacheState    The new value for the state caching flag
     */
    public void setCacheState(boolean cacheState) {
        this._cacheState = cacheState;
    }

    /**
     * Can be used to clear cached state when it is known that the underlying
     * connection may have been accessed directly.
     */
    public void clearCachedState() {
        _autoCommitCached = null;
        _readOnlyCached = null;
        if (_conn instanceof DelegatingConnection) {
            ((DelegatingConnection<?>)_conn).clearCachedState();
        }
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkOpen();
        try {
            _conn.setAutoCommit(autoCommit);
            if (_cacheState) {
                _autoCommitCached = Boolean.valueOf(autoCommit);
            }
        } catch (SQLException e) {
            _autoCommitCached = null;
            handleException(e);
        }
    }

    @Override
    public void setCatalog(String catalog) throws SQLException
    { checkOpen(); try { _conn.setCatalog(catalog); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        checkOpen();
        try {
            _conn.setReadOnly(readOnly);
            if (_cacheState) {
                _readOnlyCached = Boolean.valueOf(readOnly);
            }
        } catch (SQLException e) {
            _readOnlyCached = null;
            handleException(e);
        }
    }


    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkOpen();
        try {
            _conn.setTransactionIsolation(level);
        } catch (SQLException e) {
            handleException(e);
        }
    }


    @Override
    public void setTypeMap(Map<String,Class<?>> map) throws SQLException {
        checkOpen();
        try {
            _conn.setTypeMap(map);
        } catch (SQLException e) {
            handleException(e);
        }
    }


    @Override
    public boolean isClosed() throws SQLException {
        return _closed || _conn.isClosed();
    }

    protected void checkOpen() throws SQLException {
        if(_closed) {
            if (null != _conn) {
                String label = "";
                try {
                    label = _conn.toString();
                } catch (Exception ex) {
                    // ignore, leave label empty
                }
                throw new SQLException
                    ("Connection " + label + " is closed.");
            }
            throw new SQLException
                ("Connection is null.");
        }
    }

    protected void activate() {
        _closed = false;
        setLastUsed();
        if(_conn instanceof DelegatingConnection) {
            ((DelegatingConnection<?>)_conn).activate();
        }
    }

    protected void passivate() throws SQLException {
        // The JDBC spec requires that a Connection close any open
        // Statement's when it is closed.
        // DBCP-288. Not all the traced objects will be statements
        List<AbandonedTrace> traces = getTrace();
        if(traces != null && traces.size() > 0) {
            Iterator<AbandonedTrace> traceIter = traces.iterator();
            while (traceIter.hasNext()) {
                Object trace = traceIter.next();
                if (trace instanceof Statement) {
                    ((Statement) trace).close();
                } else if (trace instanceof ResultSet) {
                    // DBCP-265: Need to close the result sets that are
                    // generated via DatabaseMetaData
                    ((ResultSet) trace).close();
                }
            }
            clearTrace();
        }
        setLastUsed(0);
    }


    @Override
    public int getHoldability() throws SQLException {
        checkOpen();
        try {
            return _conn.getHoldability();
        } catch (SQLException e) {
            handleException(e);
            return 0;
        }
    }


    @Override
    public void setHoldability(int holdability) throws SQLException {
        checkOpen();
        try {
            _conn.setHoldability(holdability);
        } catch (SQLException e) {
            handleException(e);
        }
    }


    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkOpen();
        try {
            return _conn.setSavepoint();
        } catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        checkOpen();
        try {
            return _conn.setSavepoint(name);
        } catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        checkOpen();
        try {
            _conn.rollback(savepoint);
        } catch (SQLException e) {
            handleException(e);
        }
    }


    @Override
    public void releaseSavepoint(Savepoint savepoint)
            throws SQLException {
        checkOpen();
        try {
            _conn.releaseSavepoint(savepoint);
        } catch (SQLException e) {
            handleException(e);
        }
    }


    @Override
    public Statement createStatement(int resultSetType,
                                     int resultSetConcurrency,
                                     int resultSetHoldability) throws SQLException {
        checkOpen();
        try {
            DelegatingStatement ds = new DelegatingStatement(this,
                    _conn.createStatement(resultSetType, resultSetConcurrency,
                            resultSetHoldability));
            initializeStatement(ds);
            return ds;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType,
                                              int resultSetConcurrency,
                                              int resultSetHoldability) throws SQLException {
        checkOpen();
        try {
            DelegatingPreparedStatement dps = new DelegatingPreparedStatement(
                    this, _conn.prepareStatement(sql, resultSetType,
                            resultSetConcurrency, resultSetHoldability));
            initializeStatement(dps);
            return dps;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType,
                                         int resultSetConcurrency,
                                         int resultSetHoldability) throws SQLException {
        checkOpen();
        try {
            DelegatingCallableStatement dcs = new DelegatingCallableStatement(
                    this, _conn.prepareCall(sql, resultSetType,
                            resultSetConcurrency, resultSetHoldability));
            initializeStatement(dcs);
            return dcs;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        checkOpen();
        try {
            DelegatingPreparedStatement dps = new DelegatingPreparedStatement(
                    this, _conn.prepareStatement(sql, autoGeneratedKeys));
            initializeStatement(dps);
            return dps;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int columnIndexes[]) throws SQLException {
        checkOpen();
        try {
            DelegatingPreparedStatement dps = new DelegatingPreparedStatement(
                    this, _conn.prepareStatement(sql, columnIndexes));
            initializeStatement(dps);
            return dps;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String columnNames[]) throws SQLException {
        checkOpen();
        try {
            DelegatingPreparedStatement dps =  new DelegatingPreparedStatement(
                    this, _conn.prepareStatement(sql, columnNames));
            initializeStatement(dps);
            return dps;
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        } else if (iface.isAssignableFrom(_conn.getClass())) {
            return true;
        } else {
            return _conn.isWrapperFor(iface);
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        } else if (iface.isAssignableFrom(_conn.getClass())) {
            return iface.cast(_conn);
        } else {
            return _conn.unwrap(iface);
        }
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkOpen();
        try {
            return _conn.createArrayOf(typeName, elements);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Blob createBlob() throws SQLException {
        checkOpen();
        try {
            return _conn.createBlob();
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Clob createClob() throws SQLException {
        checkOpen();
        try {
            return _conn.createClob();
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public NClob createNClob() throws SQLException {
        checkOpen();
        try {
            return _conn.createNClob();
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkOpen();
        try {
            return _conn.createSQLXML();
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        checkOpen();
        try {
            return _conn.createStruct(typeName, attributes);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        if (isClosed()) {
            return false;
        }
        try {
            return _conn.isValid(timeout);
        }
        catch (SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        try {
            checkOpen();
            _conn.setClientInfo(name, value);
        }
        catch (SQLClientInfoException e) {
            throw e;
        }
        catch (SQLException e) {
            throw new SQLClientInfoException("Connection is closed.", EMPTY_FAILED_PROPERTIES, e);
        }
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        try {
            checkOpen();
            _conn.setClientInfo(properties);
        }
        catch (SQLClientInfoException e) {
            throw e;
        }
        catch (SQLException e) {
            throw new SQLClientInfoException("Connection is closed.", EMPTY_FAILED_PROPERTIES, e);
        }
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        checkOpen();
        try {
            return _conn.getClientInfo();
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        checkOpen();
        try {
            return _conn.getClientInfo(name);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        checkOpen();
        try {
            _conn.setSchema(schema);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public String getSchema() throws SQLException {
        checkOpen();
        try {
            return _conn.getSchema();
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        checkOpen();
        try {
            _conn.abort(executor);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds)
            throws SQLException {
        checkOpen();
        try {
            _conn.setNetworkTimeout(executor, milliseconds);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        checkOpen();
        try {
            return _conn.getNetworkTimeout();
        }
        catch (SQLException e) {
            handleException(e);
            return 0;
        }
    }
}

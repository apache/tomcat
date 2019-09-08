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

package org.apache.tomcat.dbcp.dbcp;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.sql.ResultSet;
import java.sql.Array;
import java.sql.Blob;
import java.sql.ClientInfoStatus;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLClientInfoException;
import java.sql.SQLXML;
import java.sql.Struct;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.apache.tomcat.util.compat.JreCompat;

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
 * @author Rodney Waldhoff
 * @author Glenn L. Nielsen
 * @author James House
 * @author Dirk Verbeeck
 */
public class DelegatingConnection extends AbandonedTrace
        implements Connection {

    private static final Map<String, ClientInfoStatus> EMPTY_FAILED_PROPERTIES =
        Collections.<String, ClientInfoStatus>emptyMap();

    /** My delegate {@link Connection}. */
    protected Connection _conn = null;

    protected boolean _closed = false;

    /**
     * Create a wrapper for the Connection which traces this
     * Connection in the AbandonedObjectPool.
     *
     * @param c the {@link Connection} to delegate all calls to.
     */
    public DelegatingConnection(Connection c) {
        super();
        _conn = c;
    }

    /**
     * Create a wrapper for the Connection which traces
     * the Statements created so that any unclosed Statements
     * can be closed when this Connection is closed.
     *
     * @param c the {@link Connection} to delegate all calls to.
     * @param config the configuration for tracing abandoned objects
     */
    public DelegatingConnection(Connection c, AbandonedConfig config) {
        super(config);
        _conn = c;
    }

    /**
     * Returns a string representation of the metadata associated with
     * the innnermost delegate connection.
     *
     * @since 1.2.2
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
                    DatabaseMetaData meta = c.getMetaData();
                    if (meta != null) {
                        StringBuffer sb = new StringBuffer();
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
    public Connection getDelegate() {
        return getDelegateInternal();
    }

    /**
     * Should be final but can't be for compatibility with previous releases.
     */
    protected Connection getDelegateInternal() {
        return _conn;
    }

    /**
     * Compares innermost delegate to the given connection.
     *
     * @param c connection to compare innermost delegate with
     * @return true if innermost delegate equals <code>c</code>
     * @since 1.2.2
     */
    public boolean innermostDelegateEquals(Connection c) {
        Connection innerCon = getInnermostDelegateInternal();
        if (innerCon == null) {
            return c == null;
        } else {
            return innerCon.equals(c);
        }
    }

    /**
     * This method considers two objects to be equal
     * if the underlying JDBC objects are equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        Connection delegate = getInnermostDelegateInternal();
        if (delegate == null) {
            return false;
        }
        if (obj instanceof DelegatingConnection) {
            DelegatingConnection c = (DelegatingConnection) obj;
            return c.innermostDelegateEquals(delegate);
        }
        else {
            return delegate.equals(obj);
        }
    }

    @Override
    public int hashCode() {
        Object obj = getInnermostDelegateInternal();
        if (obj == null) {
            return 0;
        }
        return obj.hashCode();
    }


    /**
     * If my underlying {@link Connection} is not a
     * <code>DelegatingConnection</code>, returns it,
     * otherwise recursively invokes this method on
     * my delegate.
     * <p>
     * Hence this method will return the first
     * delegate that is not a <code>DelegatingConnection</code>,
     * or <code>null</code> when no non-<code>DelegatingConnection</code>
     * delegate can be found by traversing this chain.
     * <p>
     * This method is useful when you may have nested
     * <code>DelegatingConnection</code>s, and you want to make
     * sure to obtain a "genuine" {@link Connection}.
     *
     * @return The first delegate that is not a {@link DelegatingConnection}, or {@code null}.
     */
    public Connection getInnermostDelegate() {
        return getInnermostDelegateInternal();
    }

    protected final Connection getInnermostDelegateInternal() {
        Connection c = _conn;
        while(c != null && c instanceof DelegatingConnection) {
            c = ((DelegatingConnection)c).getDelegateInternal();
            if(this == c) {
                return null;
            }
        }
        return c;
    }

    /**
     * Sets my delegate.
     *
     * @param c the delegate Connection
     */
    public void setDelegate(Connection c) {
        _conn = c;
    }

    /**
     * Closes the underlying connection, and close
     * any Statements that were not explicitly closed.
     */
    @Override
    public void close() throws SQLException {
        passivate();
        _conn.close();
    }

    protected void handleException(SQLException e) throws SQLException {
        throw e;
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkOpen();
        try {
            return new DelegatingStatement(this, _conn.createStatement());
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
            return new DelegatingStatement
                (this, _conn.createStatement(resultSetType,resultSetConcurrency));
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
            return new DelegatingPreparedStatement
                (this, _conn.prepareStatement(sql));
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
            return new DelegatingPreparedStatement
                (this, _conn.prepareStatement
                    (sql,resultSetType,resultSetConcurrency));
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
            return new DelegatingCallableStatement(this, _conn.prepareCall(sql));
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
            return new DelegatingCallableStatement
                (this, _conn.prepareCall(sql, resultSetType,resultSetConcurrency));
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void clearWarnings() throws SQLException
    { checkOpen(); try { _conn.clearWarnings(); } catch (SQLException e) { handleException(e); } }

    @Override
    public void commit() throws SQLException
    { checkOpen(); try { _conn.commit(); } catch (SQLException e) { handleException(e); } }

    @Override
    public boolean getAutoCommit() throws SQLException
    { checkOpen(); try { return _conn.getAutoCommit(); } catch (SQLException e) { handleException(e); return false; }
    }
    @Override
    public String getCatalog() throws SQLException
    { checkOpen(); try { return _conn.getCatalog(); } catch (SQLException e) { handleException(e); return null; } }

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
    public int getTransactionIsolation() throws SQLException
    { checkOpen(); try { return _conn.getTransactionIsolation(); } catch (SQLException e) { handleException(e); return -1; } }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException
    { checkOpen(); try { return _conn.getTypeMap(); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public SQLWarning getWarnings() throws SQLException
    { checkOpen(); try { return _conn.getWarnings(); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public boolean isReadOnly() throws SQLException
    { checkOpen(); try { return _conn.isReadOnly(); } catch (SQLException e) { handleException(e); return false; } }

    @Override
    public String nativeSQL(String sql) throws SQLException
    { checkOpen(); try { return _conn.nativeSQL(sql); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public void rollback() throws SQLException
    { checkOpen(); try {  _conn.rollback(); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException
    { checkOpen(); try { _conn.setAutoCommit(autoCommit); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setCatalog(String catalog) throws SQLException
    { checkOpen(); try { _conn.setCatalog(catalog); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException
    { checkOpen(); try { _conn.setReadOnly(readOnly); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setTransactionIsolation(int level) throws SQLException
    { checkOpen(); try { _conn.setTransactionIsolation(level); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException
    { checkOpen(); try { _conn.setTypeMap(map); } catch (SQLException e) { handleException(e); } }

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
            } else {
                throw new SQLException
                    ("Connection is null.");
            }
        }
    }

    protected void activate() {
        _closed = false;
        setLastUsed();
        if(_conn instanceof DelegatingConnection) {
            ((DelegatingConnection)_conn).activate();
        }
    }

    protected void passivate() throws SQLException {
        try {
            // The JDBC spec requires that a Connection close any open
            // Statement's when it is closed.
            // DBCP-288. Not all the traced objects will be statements
            List<AbandonedTrace> traces = getTrace();
            if(traces != null) {
                Iterator<AbandonedTrace> traceIter = traces.iterator();
                while (traceIter.hasNext()) {
                    AbandonedTrace trace = traceIter.next();
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
            if(_conn instanceof DelegatingConnection) {
                ((DelegatingConnection)_conn).passivate();
            }
        }
        finally {
            _closed = true;
        }
    }

    @Override
    public int getHoldability() throws SQLException
    { checkOpen(); try { return _conn.getHoldability(); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public void setHoldability(int holdability) throws SQLException
    { checkOpen(); try { _conn.setHoldability(holdability); } catch (SQLException e) { handleException(e); } }

    @Override
    public java.sql.Savepoint setSavepoint() throws SQLException
    { checkOpen(); try { return _conn.setSavepoint(); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public java.sql.Savepoint setSavepoint(String name) throws SQLException
    { checkOpen(); try { return _conn.setSavepoint(name); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public void rollback(java.sql.Savepoint savepoint) throws SQLException
    { checkOpen(); try { _conn.rollback(savepoint); } catch (SQLException e) { handleException(e); } }

    @Override
    public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException
    { checkOpen(); try { _conn.releaseSavepoint(savepoint); } catch (SQLException e) { handleException(e); } }

    @Override
    public Statement createStatement(int resultSetType,
                                     int resultSetConcurrency,
                                     int resultSetHoldability) throws SQLException {
        checkOpen();
        try {
            return new DelegatingStatement(this, _conn.createStatement(
                resultSetType, resultSetConcurrency, resultSetHoldability));
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
            return new DelegatingPreparedStatement(this, _conn.prepareStatement(
                sql, resultSetType, resultSetConcurrency, resultSetHoldability));
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
            return new DelegatingCallableStatement(this, _conn.prepareCall(
                sql, resultSetType, resultSetConcurrency, resultSetHoldability));
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
            return new DelegatingPreparedStatement(this, _conn.prepareStatement(
                sql, autoGeneratedKeys));
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
            return new DelegatingPreparedStatement(this, _conn.prepareStatement(
                sql, columnIndexes));
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
            return new DelegatingPreparedStatement(this, _conn.prepareStatement(
                sql, columnNames));
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass()) || _conn.isWrapperFor(iface);
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
        checkOpen();
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

    /* JDBC_4_1_ANT_KEY_BEGIN */
    // No @Override else it won't compile with Java 6
    public void setSchema(String schema) throws SQLException {
        checkOpen();
        try {
            JreCompat.getInstance().setSchema(_conn, schema);
        } catch (SQLException e) {
            handleException(e);
        }
    }

    // No @Override else it won't compile with Java 6
    public String getSchema() throws SQLException {
        checkOpen();
        try {
            return JreCompat.getInstance().getSchema(_conn);
        } catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    // No @Override else it won't compile with Java 6
    public void abort(Executor executor) throws SQLException {
        checkOpen();
        try {
            JreCompat.getInstance().abort(_conn, executor);
        } catch (SQLException e) {
            handleException(e);
        }
    }

    // No @Override else it won't compile with Java 6
    public void setNetworkTimeout(Executor executor, int milliseconds)
            throws SQLException {
        try {
            JreCompat.getInstance().setNetworkTimeout(_conn, executor, milliseconds);
        } catch (SQLException e) {
            handleException(e);
        }
    }

    // No @Override else it won't compile with Java 6
    public int getNetworkTimeout() throws SQLException {
        try {
            return JreCompat.getInstance().getNetworkTimeout(_conn);
        } catch (SQLException e) {
            handleException(e);
            return 0;
        }
    }
    /* JDBC_4_1_ANT_KEY_END */
}

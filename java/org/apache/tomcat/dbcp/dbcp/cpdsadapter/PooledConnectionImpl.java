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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Vector;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
/* JDBC_4_ANT_KEY_BEGIN */
import javax.sql.StatementEventListener;
/* JDBC_4_ANT_KEY_END */

import org.apache.tomcat.dbcp.dbcp.DelegatingConnection;
import org.apache.tomcat.dbcp.dbcp.DelegatingPreparedStatement;
import org.apache.tomcat.dbcp.dbcp.SQLNestedException;
import org.apache.tomcat.dbcp.pool.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool.KeyedPoolableObjectFactory;

/**
 * Implementation of PooledConnection that is returned by
 * PooledConnectionDataSource.
 *
 * @author John D. McNally
 * @version $Revision: 899987 $ $Date: 2010-01-16 11:51:16 -0500 (Sat, 16 Jan 2010) $
 */
class PooledConnectionImpl 
        implements PooledConnection, KeyedPoolableObjectFactory {
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
    private final Vector eventListeners;

    /**
     * StatementEventListeners
     */
    private final Vector statementEventListeners = new Vector();

    /**
     * flag set to true, once close() is called.
     */
    boolean isClosed; // TODO - make private?

    /** My pool of {*link PreparedStatement}s. */
    // TODO - make final?
    protected KeyedObjectPool pstmtPool = null;

    /** 
     * Controls access to the underlying connection 
     */
    private boolean accessToUnderlyingConnectionAllowed = false; 

    /**
     * Wrap the real connection.
     * @param connection the connection to be wrapped
     * @param pool the pool to use
     */
    PooledConnectionImpl(Connection connection, KeyedObjectPool pool) {
        this.connection = connection;
        if (connection instanceof DelegatingConnection) {
            this.delegatingConnection = (DelegatingConnection) connection;
        } else {
            this.delegatingConnection = new DelegatingConnection(connection);
        }
        eventListeners = new Vector();
        isClosed = false;
        if (pool != null) {
            pstmtPool = pool;
            pstmtPool.setFactory(this);            
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addConnectionEventListener(ConnectionEventListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    /* JDBC_4_ANT_KEY_BEGIN */
    public void addStatementEventListener(StatementEventListener listener) {
        if (!statementEventListeners.contains(listener)) {
            statementEventListeners.add(listener);
        }
    }
    /* JDBC_4_ANT_KEY_END */

    /**
     * Closes the physical connection and marks this 
     * <code>PooledConnection</code> so that it may not be used 
     * to generate any more logical <code>Connection</code>s.
     *
     * @exception SQLException if an error occurs or the connection is already closed
     */
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
            throw new SQLNestedException("Cannot close connection (return to pool failed)", e);
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
    public void removeConnectionEventListener(
            ConnectionEventListener listener) {
        eventListeners.remove(listener);
    }

    /* JDBC_4_ANT_KEY_BEGIN */
    public void removeStatementEventListener(StatementEventListener listener) {
        statementEventListeners.remove(listener);
    }
    /* JDBC_4_ANT_KEY_END */

    /**
     * Closes the physical connection and checks that the logical connection
     * was closed as well.
     */
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
     * @return a {@link PoolablePreparedStatement}
     */
    PreparedStatement prepareStatement(String sql) throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql);
        } else {
            try {
                return (PreparedStatement) 
                        pstmtPool.borrowObject(createKey(sql));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLNestedException("Borrow prepareStatement from pool failed", e);
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
     * @return a {@link PoolablePreparedStatement}
     * @see Connection#prepareStatement(String, int, int)
     */
    PreparedStatement prepareStatement(String sql, int resultSetType, 
                                       int resultSetConcurrency) 
            throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
        } else {
            try {
                return (PreparedStatement) pstmtPool.borrowObject(
                    createKey(sql,resultSetType,resultSetConcurrency));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLNestedException("Borrow prepareStatement from pool failed", e);
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
     * @return a {@link PoolablePreparedStatement}
     * @see Connection#prepareStatement(String, int)
     */
    PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) 
            throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, autoGeneratedKeys);
        } else {
            try {
                return (PreparedStatement) pstmtPool.borrowObject(
                    createKey(sql,autoGeneratedKeys));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLNestedException("Borrow prepareStatement from pool failed", e);
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
                return (PreparedStatement) pstmtPool.borrowObject(
                    createKey(sql, resultSetType, resultSetConcurrency,
                            resultSetHoldability));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLNestedException("Borrow prepareStatement from pool failed", e);
            }
        }
    }

    PreparedStatement prepareStatement(String sql, int columnIndexes[])
    throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, columnIndexes);
        } else {
            try {
                return (PreparedStatement) pstmtPool.borrowObject(
                    createKey(sql, columnIndexes));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLNestedException("Borrow prepareStatement from pool failed", e);
            }
        }
    }

    PreparedStatement prepareStatement(String sql, String columnNames[])
    throws SQLException {
        if (pstmtPool == null) {
            return connection.prepareStatement(sql, columnNames);
        } else {
            try {
                return (PreparedStatement) pstmtPool.borrowObject(
                    createKey(sql, columnNames));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLNestedException("Borrow prepareStatement from pool failed", e);
            }
        }
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected Object createKey(String sql, int autoGeneratedKeys) {
        return new PStmtKey(normalizeSQL(sql), autoGeneratedKeys);
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected Object createKey(String sql, int resultSetType,
            int resultSetConcurrency, int resultSetHoldability) {
        return new PStmtKey(normalizeSQL(sql), resultSetType,
                resultSetConcurrency, resultSetHoldability);
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected Object createKey(String sql, int columnIndexes[]) {
        return new PStmtKey(normalizeSQL(sql), columnIndexes);
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected Object createKey(String sql, String columnNames[]) {
        return new PStmtKey(normalizeSQL(sql), columnNames);
    }

    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected Object createKey(String sql, int resultSetType, 
                               int resultSetConcurrency) {
        return new PStmtKey(normalizeSQL(sql), resultSetType,
                            resultSetConcurrency);
    }
    
    /**
     * Create a {*link PooledConnectionImpl.PStmtKey} for the given arguments.
     */
    protected Object createKey(String sql) {
        return new PStmtKey(normalizeSQL(sql));
    }

    /**
     * Normalize the given SQL statement, producing a
     * cannonical form that is semantically equivalent to the original.
     */
    protected String normalizeSQL(String sql) {
        return sql.trim();
    }

    /**
     * My {*link KeyedPoolableObjectFactory} method for creating
     * {*link PreparedStatement}s.
     * @param obj the key for the {*link PreparedStatement} to be created
     */
    public Object makeObject(Object obj) throws Exception {
        if (null == obj || !(obj instanceof PStmtKey)) {
            throw new IllegalArgumentException();
        } else {
            // _openPstmts++;
            PStmtKey key = (PStmtKey)obj;
            if (null == key._resultSetType 
                    && null == key._resultSetConcurrency) {
                if (null == key._autoGeneratedKeys) {
                    return new PoolablePreparedStatementStub(
                            connection.prepareStatement(key._sql),
                            key, pstmtPool, delegatingConnection);
                } else {
                    return new PoolablePreparedStatementStub(
                            connection.prepareStatement(key._sql,
                                    key._autoGeneratedKeys.intValue()),
                            key, pstmtPool, delegatingConnection);
                }
            } else {
                return new PoolablePreparedStatementStub(
                        connection.prepareStatement(key._sql,
                        key._resultSetType.intValue(),
                        key._resultSetConcurrency.intValue()),
                        key, pstmtPool, delegatingConnection);
            }
        }
    }

    /**
     * My {*link KeyedPoolableObjectFactory} method for destroying
     * {*link PreparedStatement}s.
     * @param key ignored
     * @param obj the {*link PreparedStatement} to be destroyed.
     */
    public void destroyObject(Object key, Object obj) throws Exception {
        //_openPstmts--;
        if (obj instanceof DelegatingPreparedStatement) {
            ((DelegatingPreparedStatement) obj).getInnermostDelegate().close();
        } else {
            ((PreparedStatement) obj).close();
        }
    }

    /**
     * My {*link KeyedPoolableObjectFactory} method for validating
     * {*link PreparedStatement}s.
     * @param key ignored
     * @param obj ignored
     * @return <tt>true</tt>
     */
    public boolean validateObject(Object key, Object obj) {
        return true;
    }

    /**
     * My {*link KeyedPoolableObjectFactory} method for activating
     * {*link PreparedStatement}s.
     * @param key ignored
     * @param obj ignored
     */
    public void activateObject(Object key, Object obj) throws Exception {
        ((PoolablePreparedStatementStub) obj).activate();
    }

    /**
     * My {*link KeyedPoolableObjectFactory} method for passivating
     * {*link PreparedStatement}s.  Currently invokes {*link PreparedStatement#clearParameters}.
     * @param key ignored
     * @param obj a {*link PreparedStatement}
     */
    public void passivateObject(Object key, Object obj) throws Exception {
        ((PreparedStatement) obj).clearParameters();
        ((PoolablePreparedStatementStub) obj).passivate();
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
    
    /**
     * A key uniquely identifying {*link PreparedStatement}s.
     */
    static class PStmtKey {
        protected String _sql = null;
        protected Integer _resultSetType = null;
        protected Integer _resultSetConcurrency = null;
        protected Integer _autoGeneratedKeys = null;
        protected Integer _resultSetHoldability = null;
        protected int _columnIndexes[] = null;
        protected String _columnNames[] = null;
        
        PStmtKey(String sql) {
            _sql = sql;
        }

        PStmtKey(String sql, int resultSetType, int resultSetConcurrency) {
            _sql = sql;
            _resultSetType = new Integer(resultSetType);
            _resultSetConcurrency = new Integer(resultSetConcurrency);
        }

        PStmtKey(String sql, int autoGeneratedKeys) {
            _sql = sql;
            _autoGeneratedKeys = new Integer(autoGeneratedKeys);
        }

        PStmtKey(String sql, int resultSetType, int resultSetConcurrency,
                int resultSetHoldability) {
            _sql = sql;
            _resultSetType = new Integer(resultSetType);
            _resultSetConcurrency = new Integer(resultSetConcurrency);
            _resultSetHoldability = new Integer(resultSetHoldability);
        }

        PStmtKey(String sql, int columnIndexes[]) {
            _sql = sql;
            _columnIndexes = columnIndexes;
        }

        PStmtKey(String sql, String columnNames[]) {
            _sql = sql;
            _columnNames = columnNames;
        }

        
        public boolean equals(Object that) {
            try {
                PStmtKey key = (PStmtKey) that;
                return(((null == _sql && null == key._sql) || _sql.equals(key._sql)) &&
                       ((null == _resultSetType && null == key._resultSetType) || _resultSetType.equals(key._resultSetType)) &&
                       ((null == _resultSetConcurrency && null == key._resultSetConcurrency) || _resultSetConcurrency.equals(key._resultSetConcurrency)) &&
                       ((null == _autoGeneratedKeys && null == key._autoGeneratedKeys) || _autoGeneratedKeys.equals(key._autoGeneratedKeys)) &&
                       ((null == _resultSetHoldability && null == key._resultSetHoldability) || _resultSetHoldability.equals(key._resultSetHoldability)) &&
                       ((null == _columnIndexes && null == key._columnIndexes) || Arrays.equals(_columnIndexes, key._columnIndexes)) &&
                       ((null == _columnNames && null == key._columnNames) || Arrays.equals(_columnNames, key._columnNames))
                      );
            } catch (ClassCastException e) {
                return false;
            } catch (NullPointerException e) {
                return false;
            }
        }

        public int hashCode() {
            return(null == _sql ? 0 : _sql.hashCode());
        }

        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append("PStmtKey: sql=");
            buf.append(_sql);
            buf.append(", resultSetType=");
            buf.append(_resultSetType);
            buf.append(", resultSetConcurrency=");
            buf.append(_resultSetConcurrency);
            buf.append(", autoGeneratedKeys=");
            buf.append(_autoGeneratedKeys);
            buf.append(", resultSetHoldability=");
            buf.append(_resultSetHoldability);
            buf.append(", columnIndexes=");
// JDK1.5   buf.append(Arrays.toString(_columnIndexes));
            arrayToString(buf,_columnIndexes);
            buf.append(", columnNames=");
// JDK1.5   buf.append(Arrays.toString(_columnNames));
            arrayToString(buf,_columnNames);
            return buf.toString();
        }
        private void arrayToString(StringBuffer sb, int[] array){
            if (array == null) {
                sb.append("null");
                return;
            }
            sb.append('[');
            for(int i=0; i<array.length; i++){
                if (i>0){
                    sb.append(',');
                }
                sb.append(array[i]);
            }
            sb.append(']');
        }
        private void arrayToString(StringBuffer sb, String[] array){
            if (array == null) {
                sb.append("null");
                return;
            }
            sb.append('[');
            for(int i=0; i<array.length; i++){
                if (i>0){
                    sb.append(',');
                }
                sb.append(array[i]);
            }
            sb.append(']');
        }
    }
}

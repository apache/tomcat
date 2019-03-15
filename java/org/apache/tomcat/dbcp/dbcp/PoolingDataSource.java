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

import java.io.PrintWriter;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.sql.DataSource;

import org.apache.tomcat.dbcp.pool.ObjectPool;

/**
 * A simple {@link DataSource} implementation that obtains
 * {@link Connection}s from the specified {@link ObjectPool}.
 *
 * @author Rodney Waldhoff
 * @author Glenn L. Nielsen
 * @author James House
 * @author Dirk Verbeeck
 * @version $Revision: 895844 $ $Date: 2010-01-04 20:50:04 -0500 (Mon, 04 Jan 2010) $
 */
public class PoolingDataSource implements DataSource {

    /** Controls access to the underlying connection */
    private boolean accessToUnderlyingConnectionAllowed = false; 

    public PoolingDataSource() {
        this(null);
    }

    public PoolingDataSource(ObjectPool pool) {
        _pool = pool;
    }

    public void setPool(ObjectPool pool) throws IllegalStateException, NullPointerException {
        if(null != _pool) {
            throw new IllegalStateException("Pool already set");
        } else if(null == pool) {
            throw new NullPointerException("Pool must not be null.");
        } else {
            _pool = pool;
        }
    }

    /**
     * Returns the value of the accessToUnderlyingConnectionAllowed property.
     * 
     * @return true if access to the underlying is allowed, false otherwise.
     */
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return this.accessToUnderlyingConnectionAllowed;
    }

    /**
     * Sets the value of the accessToUnderlyingConnectionAllowed property.
     * It controls if the PoolGuard allows access to the underlying connection.
     * (Default: false)
     * 
     * @param allow Access to the underlying connection is granted when true.
     */
    public void setAccessToUnderlyingConnectionAllowed(boolean allow) {
        this.accessToUnderlyingConnectionAllowed = allow;
    }

    /* JDBC_4_ANT_KEY_BEGIN */
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("PoolingDataSource is not a wrapper.");
    }
    /* JDBC_4_ANT_KEY_END */
    
    //--- DataSource methods -----------------------------------------

    /**
     * Return a {@link java.sql.Connection} from my pool,
     * according to the contract specified by {@link ObjectPool#borrowObject}.
     */
    public Connection getConnection() throws SQLException {
        try {
            Connection conn = (Connection)(_pool.borrowObject());
            if (conn != null) {
                conn = new PoolGuardConnectionWrapper(conn);
            } 
            return conn;
        } catch(SQLException e) {
            throw e;
        } catch(NoSuchElementException e) {
            throw new SQLNestedException("Cannot get a connection, pool error " + e.getMessage(), e);
        } catch(RuntimeException e) {
            throw e;
        } catch(Exception e) {
            throw new SQLNestedException("Cannot get a connection, general error", e);
        }
    }

    /**
     * Throws {@link UnsupportedOperationException}
     * @throws UnsupportedOperationException
     */
    public Connection getConnection(String uname, String passwd) throws SQLException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns my log writer.
     * @return my log writer
     * @see DataSource#getLogWriter
     */
    public PrintWriter getLogWriter() {
        return _logWriter;
    }

    /**
     * Throws {@link UnsupportedOperationException}.
     * @throws UnsupportedOperationException As this
     *   implementation does not support this feature.
     */
    public int getLoginTimeout() {
        throw new UnsupportedOperationException("Login timeout is not supported.");
    }

    /**
     * Throws {@link UnsupportedOperationException}.
     * @throws UnsupportedOperationException As this
     *   implementation does not support this feature.
     */
    public void setLoginTimeout(int seconds) {
        throw new UnsupportedOperationException("Login timeout is not supported.");
    }

    /**
     * Sets my log writer.
     * @see DataSource#setLogWriter
     */
    public void setLogWriter(PrintWriter out) {
        _logWriter = out;
    }

    /** My log writer. */
    protected PrintWriter _logWriter = null;

    protected ObjectPool _pool = null;

    /**
     * PoolGuardConnectionWrapper is a Connection wrapper that makes sure a 
     * closed connection cannot be used anymore.
     */
    private class PoolGuardConnectionWrapper extends DelegatingConnection {

        private Connection delegate;
    
        PoolGuardConnectionWrapper(Connection delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        protected void checkOpen() throws SQLException {
            if(delegate == null) {
                throw new SQLException("Connection is closed.");
            }
        }
    
        public void close() throws SQLException {
            if (delegate != null) {
                this.delegate.close();
                this.delegate = null;
                super.setDelegate(null);
            }
        }

        public boolean isClosed() throws SQLException {
            if (delegate == null) {
                return true;
            }
            return delegate.isClosed();
        }

        public void clearWarnings() throws SQLException {
            checkOpen();
            delegate.clearWarnings();
        }

        public void commit() throws SQLException {
            checkOpen();
            delegate.commit();
        }

        public Statement createStatement() throws SQLException {
            checkOpen();
            return new DelegatingStatement(this, delegate.createStatement());
        }

        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            checkOpen();
            return new DelegatingStatement(this, delegate.createStatement(resultSetType, resultSetConcurrency));
        }

        public boolean innermostDelegateEquals(Connection c) {
            Connection innerCon = super.getInnermostDelegate();
            if (innerCon == null) {
                return c == null;
            } else {
                return innerCon.equals(c);
            }
        }
        
        public boolean getAutoCommit() throws SQLException {
            checkOpen();
            return delegate.getAutoCommit();
        }

        public String getCatalog() throws SQLException {
            checkOpen();
            return delegate.getCatalog();
        }

        public DatabaseMetaData getMetaData() throws SQLException {
            checkOpen();
            return delegate.getMetaData();
        }

        public int getTransactionIsolation() throws SQLException {
            checkOpen();
            return delegate.getTransactionIsolation();
        }

        public Map getTypeMap() throws SQLException {
            checkOpen();
            return delegate.getTypeMap();
        }

        public SQLWarning getWarnings() throws SQLException {
            checkOpen();
            return delegate.getWarnings();
        }

        public int hashCode() {
            if (delegate == null){
                return 0;
            }
            return delegate.hashCode();
        }
        
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            // Use superclass accessor to skip access test
            Connection conn = super.getInnermostDelegate();
            if (conn == null) {
                return false;
            }
            if (obj instanceof DelegatingConnection) {    
                DelegatingConnection c = (DelegatingConnection) obj;
                return c.innermostDelegateEquals(conn);
            }
            else {
                return conn.equals(obj);
            }
        }

        public boolean isReadOnly() throws SQLException {
            checkOpen();
            return delegate.isReadOnly();
        }

        public String nativeSQL(String sql) throws SQLException {
            checkOpen();
            return delegate.nativeSQL(sql);
        }

        public CallableStatement prepareCall(String sql) throws SQLException {
            checkOpen();
            return new DelegatingCallableStatement(this, delegate.prepareCall(sql));
        }

        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            checkOpen();
            return new DelegatingCallableStatement(this, delegate.prepareCall(sql, resultSetType, resultSetConcurrency));
        }

        public PreparedStatement prepareStatement(String sql) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql));
        }

        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql, resultSetType, resultSetConcurrency));
        }

        public void rollback() throws SQLException {
            checkOpen();
            delegate.rollback();
        }

        public void setAutoCommit(boolean autoCommit) throws SQLException {
            checkOpen();
            delegate.setAutoCommit(autoCommit);
        }

        public void setCatalog(String catalog) throws SQLException {
            checkOpen();
            delegate.setCatalog(catalog);
        }

        public void setReadOnly(boolean readOnly) throws SQLException {
            checkOpen();
            delegate.setReadOnly(readOnly);
        }

        public void setTransactionIsolation(int level) throws SQLException {
            checkOpen();
            delegate.setTransactionIsolation(level);
        }

        public void setTypeMap(Map map) throws SQLException {
            checkOpen();
            delegate.setTypeMap(map);
        }

        public String toString() {
            if (delegate == null){
                return "NULL";
            }
            return delegate.toString();
        }

        public int getHoldability() throws SQLException {
            checkOpen();
            return delegate.getHoldability();
        }
    
        public void setHoldability(int holdability) throws SQLException {
            checkOpen();
            delegate.setHoldability(holdability);
        }

        public java.sql.Savepoint setSavepoint() throws SQLException {
            checkOpen();
            return delegate.setSavepoint();
        }

        public java.sql.Savepoint setSavepoint(String name) throws SQLException {
            checkOpen();
            return delegate.setSavepoint(name);
        }

        public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException {
            checkOpen();
            delegate.releaseSavepoint(savepoint);
        }

        public void rollback(java.sql.Savepoint savepoint) throws SQLException {
            checkOpen();
            delegate.rollback(savepoint);
        }

        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkOpen();
            return new DelegatingStatement(this, delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
        }

        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkOpen();
            return new DelegatingCallableStatement(this, delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        }

        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql, autoGeneratedKeys));
        }

        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this,delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
        }

        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql, columnIndexes));
        }

        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            checkOpen();
            return new DelegatingPreparedStatement(this, delegate.prepareStatement(sql, columnNames));
        }

        /**
         * @see org.apache.tomcat.dbcp.dbcp.DelegatingConnection#getDelegate()
         */
        public Connection getDelegate() {
            if (isAccessToUnderlyingConnectionAllowed()) {
                return super.getDelegate();
            } else {
                return null;
            }
        }

        /**
         * @see org.apache.tomcat.dbcp.dbcp.DelegatingConnection#getInnermostDelegate()
         */
        public Connection getInnermostDelegate() {
            if (isAccessToUnderlyingConnectionAllowed()) {
                return super.getInnermostDelegate();
            } else {
                return null;
            }
        }
    }
}

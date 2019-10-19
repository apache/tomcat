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

package org.apache.commons.dbcp;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Map;
/* JDBC_4_ANT_KEY_BEGIN */
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLClientInfoException;
import java.sql.SQLXML;
import java.sql.Struct;
import java.util.Properties;
/* JDBC_4_ANT_KEY_END */
import java.util.concurrent.Executor;

/**
 * A dummy {@link Connection}, for testing purposes.
 *
 * @author Rodney Waldhoff
 * @author Dirk Verbeeck
 * @version $Revision$ $Date$
 */
public class TesterConnection implements Connection {
    protected boolean _open = true;
    protected boolean _autoCommit = true;
    protected int _transactionIsolation = 1;
    protected DatabaseMetaData _metaData = new TesterDatabaseMetaData();
    protected String _catalog = null;
    protected Map _typeMap = null;
    protected boolean _readOnly = false;
    protected SQLWarning warnings = null;
    protected String username = null;
    protected String password = null;
    protected Exception failure;

    public TesterConnection(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return this.username;
    }

    public void setWarnings(SQLWarning warning) {
        this.warnings = warning;
    }

    public void clearWarnings() throws SQLException {
        checkOpen();
        warnings = null;
    }

    public void close() throws SQLException {
        checkFailure();
        _open = false;
    }

    public void commit() throws SQLException {
        checkOpen();
        if (isReadOnly()) {
            throw new SQLException("Cannot commit a readonly connection");
        }
    }

    public Statement createStatement() throws SQLException {
        checkOpen();
        return new TesterStatement(this);
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        checkOpen();
        return new TesterStatement(this);
    }

    public boolean getAutoCommit() throws SQLException {
        checkOpen();
        return _autoCommit;
    }

    public String getCatalog() throws SQLException {
        checkOpen();
        return _catalog;
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        checkOpen();
        return _metaData;
    }

    public int getTransactionIsolation() throws SQLException {
        checkOpen();
        return _transactionIsolation;
    }

    public Map getTypeMap() throws SQLException {
        checkOpen();
        return _typeMap;
    }

    public SQLWarning getWarnings() throws SQLException {
        checkOpen();
        return warnings;
    }

    public boolean isClosed() throws SQLException {
        checkFailure();
        return !_open;
    }

    public boolean isReadOnly() throws SQLException {
        checkOpen();
        return _readOnly;
    }

    public String nativeSQL(String sql) throws SQLException {
        checkOpen();
        return sql;
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
        checkOpen();
        if ("warning".equals(sql)) {
            setWarnings(new SQLWarning("warning in prepareCall"));
        }
        return new TesterCallableStatement(this);
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkOpen();
        return new TesterCallableStatement(this);
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkOpen();
        if("null".equals(sql)) {
            return null;
        } if("invalid".equals(sql)) {
            throw new SQLException("invalid query");
        } if ("broken".equals(sql)) {
            throw new SQLException("broken connection");
        }
        return new TesterPreparedStatement(this, sql);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkOpen();
        return new TesterPreparedStatement(this, sql, resultSetType, resultSetConcurrency);
    }

    public void rollback() throws SQLException {
        checkOpen();
        if (isReadOnly()) {
            throw new SQLException("Cannot rollback a readonly connection");
        }
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkOpen();
        _autoCommit = autoCommit;
    }

    public void setCatalog(String catalog) throws SQLException {
        checkOpen();
        _catalog = catalog;
    }

    public void setReadOnly(boolean readOnly) throws SQLException {
        checkOpen();
        _readOnly = readOnly;
    }

    public void setTransactionIsolation(int level) throws SQLException {
        checkOpen();
        _transactionIsolation = level;
    }

    public void setTypeMap(Map map) throws SQLException {
        checkOpen();
        _typeMap = map;
    }

    protected void checkOpen() throws SQLException {
        if(!_open) {
            throw new SQLException("Connection is closed.");
        }
        checkFailure();
    }

    protected void checkFailure() throws SQLException {
        if (failure != null) {
            throw (SQLException) new SQLException("TesterConnection failure").initCause(failure);
        }
    }

    public void setFailure(Exception failure) {
        this.failure = failure;
    }

    public int getHoldability() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public void setHoldability(int holdability) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public java.sql.Savepoint setSavepoint() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public java.sql.Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public void rollback(java.sql.Savepoint savepoint) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public Statement createStatement(int resultSetType,
                                     int resultSetConcurrency,
                                     int resultSetHoldability)
        throws SQLException {
        return createStatement();
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType,
                                              int resultSetConcurrency,
                                              int resultSetHoldability)
        throws SQLException {
        return prepareStatement(sql);
    }

    public CallableStatement prepareCall(String sql, int resultSetType,
                                         int resultSetConcurrency,
                                         int resultSetHoldability)
        throws SQLException {
        return prepareCall(sql);
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
        throws SQLException {
        return prepareStatement(sql);
    }

    public PreparedStatement prepareStatement(String sql, int columnIndexes[])
        throws SQLException {
        return prepareStatement(sql);
    }

    public PreparedStatement prepareStatement(String sql, String columnNames[])
        throws SQLException {
        return prepareStatement(sql);
    }

/* JDBC_4_ANT_KEY_BEGIN */

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public Blob createBlob() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public Clob createClob() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public NClob createNClob() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public SQLXML createSQLXML() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public boolean isValid(int timeout) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        throw new SQLClientInfoException();
    }

    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        throw new SQLClientInfoException();
    }

    public Properties getClientInfo() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    public String getClientInfo(String name) throws SQLException {
        throw new SQLException("Not implemented.");
    }
/* JDBC_4_ANT_KEY_END */

    /* JDBC_4_1_ANT_KEY_BEGIN */
    @Override
    public void setSchema(String schema) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public String getSchema() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds)
            throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        throw new SQLException("Not implemented.");
    }
    /* JDBC_4_1_ANT_KEY_END */
}

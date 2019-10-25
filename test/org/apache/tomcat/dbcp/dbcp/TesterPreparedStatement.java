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

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Calendar;
/* JDBC_4_ANT_KEY_BEGIN */
import java.io.InputStream;
import java.io.Reader;
import java.sql.NClob;
import java.sql.RowId;
import java.sql.SQLXML;
/* JDBC_4_ANT_KEY_END */

/**
 * A dummy {@link PreparedStatement}, for testing purposes.
 *
 * @author Rodney Waldhoff
 * @author Dirk Verbeeck
 * @version $Revision$ $Date$
 */
public class TesterPreparedStatement extends TesterStatement implements PreparedStatement {
    private ResultSetMetaData _resultSetMetaData = null;
    private String _sql = null;
    private String _catalog = null;

    public TesterPreparedStatement(Connection conn) {
        super(conn);
        try {
            _catalog = conn.getCatalog();
        } catch (SQLException e) { }
    }

    public TesterPreparedStatement(Connection conn, String sql) {
        super(conn);
        _sql = sql;
        try {
            _catalog = conn.getCatalog();
        } catch (SQLException e) { }
    }

    public TesterPreparedStatement(Connection conn, String sql, int resultSetType, int resultSetConcurrency) {
        super(conn, resultSetType, resultSetConcurrency);
        _sql = sql;
        try {
            _catalog = conn.getCatalog();
        } catch (SQLException e) { }
    }

    /** for junit test only */
    public String getCatalog() {
        return _catalog;
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        checkOpen();
        if("null".equals(sql)) {
            return null;
        } else {
            return new TesterResultSet(this, null, _resultSetType, _resultSetConcurrency);
        }
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        checkOpen();
        return _rowsUpdated;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        checkOpen();
        if("null".equals(_sql)) {
            return null;
        } else {
            return new TesterResultSet(this, null, _resultSetType, _resultSetConcurrency);
        }
    }

    @Override
    public int executeUpdate() throws SQLException {
        checkOpen();
        return _rowsUpdated;
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        checkOpen();
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setBytes(int parameterIndex, byte x[]) throws SQLException {
        checkOpen();
    }

    @Override
    public void setDate(int parameterIndex, java.sql.Date x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setTime(int parameterIndex, java.sql.Time x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setTimestamp(int parameterIndex, java.sql.Timestamp x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
        checkOpen();
    }

    /** @deprecated */
    @Deprecated
    @Override
    public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
        checkOpen();
    }

    @Override
    public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
        checkOpen();
    }

    @Override
    public void clearParameters() throws SQLException {
        checkOpen();
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scale) throws SQLException {
        checkOpen();
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        checkOpen();
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        checkOpen();
    }


    @Override
    public boolean execute() throws SQLException {
        checkOpen(); return true;
    }

    @Override
    public void addBatch() throws SQLException {
        checkOpen();
    }

    @Override
    public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws SQLException {
        checkOpen();
    }

    @Override
    public void setRef (int i, Ref x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setBlob (int i, Blob x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setClob (int i, Clob x) throws SQLException {
        checkOpen();
    }

    @Override
    public void setArray (int i, Array x) throws SQLException {
        checkOpen();
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkOpen();
        return _resultSetMetaData;
    }

    @Override
    public void setDate(int parameterIndex, java.sql.Date x, Calendar cal) throws SQLException {
        checkOpen();
    }

    @Override
    public void setTime(int parameterIndex, java.sql.Time x, Calendar cal) throws SQLException {
        checkOpen();
    }

    @Override
    public void setTimestamp(int parameterIndex, java.sql.Timestamp x, Calendar cal) throws SQLException {
        checkOpen();
    }

    @Override
    public void setNull (int paramIndex, int sqlType, String typeName) throws SQLException {
        checkOpen();
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return new TesterResultSet(this, null, _resultSetType, _resultSetConcurrency);
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys)
        throws SQLException {
        checkOpen(); return 0;
    }

    @Override
    public int executeUpdate(String sql, int columnIndexes[])
        throws SQLException {
        checkOpen(); return 0;
    }

    @Override
    public int executeUpdate(String sql, String columnNames[])
        throws SQLException {
        checkOpen(); return 0;
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys)
        throws SQLException {
        checkOpen(); return true;
    }

    @Override
    public boolean execute(String sl, int columnIndexes[])
        throws SQLException {
        checkOpen(); return true;
    }

    @Override
    public boolean execute(String sql, String columnNames[])
        throws SQLException {
        checkOpen(); return true;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setURL(int parameterIndex, java.net.URL x)
        throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public java.sql.ParameterMetaData getParameterMetaData() throws SQLException {
        throw new SQLException("Not implemented.");
    }

/* JDBC_4_ANT_KEY_BEGIN */

    @Override
    public void setRowId(int parameterIndex, RowId value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML value) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream inputStream) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream inputStream) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        throw new SQLException("Not implemented.");
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLException("Not implemented.");
    }
/* JDBC_4_ANT_KEY_END */
}

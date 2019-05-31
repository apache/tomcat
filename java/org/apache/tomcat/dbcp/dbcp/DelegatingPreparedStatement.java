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
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Calendar;
import java.io.InputStream;
import java.io.Reader;
import java.sql.NClob;
import java.sql.RowId;
import java.sql.SQLXML;

/**
 * A base delegating implementation of {@link PreparedStatement}.
 * <p>
 * All of the methods from the {@link PreparedStatement} interface
 * simply check to see that the {@link PreparedStatement} is active,
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
 */
public class DelegatingPreparedStatement extends DelegatingStatement
        implements PreparedStatement {

    /**
     * Create a wrapper for the Statement which traces this
     * Statement to the Connection which created it and the
     * code which created it.
     *
     * @param s the {@link PreparedStatement} to delegate all calls to.
     * @param c the {@link DelegatingConnection} that created this statement.
     */
    public DelegatingPreparedStatement(DelegatingConnection c,
                                       PreparedStatement s) {
        super(c, s);
    }

    @Override
    public boolean equals(Object obj) {
    	if (this == obj) return true;
        PreparedStatement delegate = (PreparedStatement) getInnermostDelegate();
        if (delegate == null) {
            return false;
        }
        if (obj instanceof DelegatingPreparedStatement) {
            DelegatingPreparedStatement s = (DelegatingPreparedStatement) obj;
            return delegate.equals(s.getInnermostDelegate());
        }
        else {
            return delegate.equals(obj);
        }
    }

    /** Sets my delegate. */
    public void setDelegate(PreparedStatement s) {
        super.setDelegate(s);
        _stmt = s;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return DelegatingResultSet.wrapResultSet(this,((PreparedStatement)_stmt).executeQuery());
        }
        catch (SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int executeUpdate() throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return ((PreparedStatement) _stmt).executeUpdate();
        } catch (SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setNull(parameterIndex,sqlType); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setBoolean(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setByte(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setShort(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setInt(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setLong(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setFloat(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setDouble(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setBigDecimal(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setString(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setBytes(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setDate(int parameterIndex, java.sql.Date x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setDate(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setTime(int parameterIndex, java.sql.Time x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setTime(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setTimestamp(int parameterIndex, java.sql.Timestamp x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setTimestamp(parameterIndex,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setAsciiStream(parameterIndex,x,length); } catch (SQLException e) { handleException(e); } }

    /** @deprecated */
    @Deprecated
    @Override
    public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setUnicodeStream(parameterIndex,x,length); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setBinaryStream(parameterIndex,x,length); } catch (SQLException e) { handleException(e); } }

    @Override
    public void clearParameters() throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).clearParameters(); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scale) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setObject(parameterIndex, x, targetSqlType, scale); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setObject(parameterIndex, x, targetSqlType); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setObject(parameterIndex, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public boolean execute() throws SQLException {
        checkOpen();
        if (_conn != null) {
            _conn.setLastUsed();
        }
        try {
            return ((PreparedStatement) _stmt).execute();
        } catch (SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void addBatch() throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).addBatch(); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setCharacterStream(parameterIndex,reader,length); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setRef(int i, Ref x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setRef(i,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setBlob(int i, Blob x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setBlob(i,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setClob(int i, Clob x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setClob(i,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setArray(int i, Array x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setArray(i,x); } catch (SQLException e) { handleException(e); } }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException
    { checkOpen(); try { return ((PreparedStatement)_stmt).getMetaData(); } catch (SQLException e) { handleException(e); throw new AssertionError(); } }

    @Override
    public void setDate(int parameterIndex, java.sql.Date x, Calendar cal) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setDate(parameterIndex,x,cal); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setTime(int parameterIndex, java.sql.Time x, Calendar cal) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setTime(parameterIndex,x,cal); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setTimestamp(int parameterIndex, java.sql.Timestamp x, Calendar cal) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setTimestamp(parameterIndex,x,cal); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setNull(int paramIndex, int sqlType, String typeName) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setNull(paramIndex,sqlType,typeName); } catch (SQLException e) { handleException(e); } }

    /**
     * Returns a String representation of this object.
     *
     * @return String
     * @since 1.2.2
     */
    @Override
    public String toString() {
    return _stmt.toString();
    }

    @Override
    public void setURL(int parameterIndex, java.net.URL x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)_stmt).setURL(parameterIndex, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public java.sql.ParameterMetaData getParameterMetaData() throws SQLException
    { checkOpen(); try { return ((PreparedStatement)_stmt).getParameterMetaData(); } catch (SQLException e) { handleException(e); throw new AssertionError(); } }


    @Override
    public void setRowId(int parameterIndex, RowId value) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setRowId(parameterIndex, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setNString(parameterIndex, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setNCharacterStream(parameterIndex, value, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setNClob(parameterIndex, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setClob(parameterIndex, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setBlob(parameterIndex, inputStream, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setNClob(parameterIndex, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML value) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setSQLXML(parameterIndex, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setAsciiStream(parameterIndex, inputStream, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setBinaryStream(parameterIndex, inputStream, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setCharacterStream(parameterIndex, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setAsciiStream(parameterIndex, inputStream);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setBinaryStream(parameterIndex, inputStream);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setCharacterStream(parameterIndex, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setNCharacterStream(parameterIndex, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setClob(parameterIndex, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setBlob(parameterIndex, inputStream);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)_stmt).setNClob(parameterIndex, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }
}

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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * A base delegating implementation of {@link CallableStatement}.
 * <p>
 * All of the methods from the {@link CallableStatement} interface
 * simply call the corresponding method on the "delegate"
 * provided in my constructor.
 * <p>
 * Extends AbandonedTrace to implement Statement tracking and
 * logging of code which created the Statement. Tracking the
 * Statement ensures that the Connection which created it can
 * close any open Statement's on Connection close.
 *
 * @author Glenn L. Nielsen
 * @author James House
 * @author Dirk Verbeeck
 * @since 2.0
 */
public class DelegatingCallableStatement extends DelegatingPreparedStatement
        implements CallableStatement {

    /**
     * Create a wrapper for the Statement which traces this
     * Statement to the Connection which created it and the
     * code which created it.
     *
     * @param c the {@link DelegatingConnection} that created this statement
     * @param s the {@link CallableStatement} to delegate all calls to
     */
    public DelegatingCallableStatement(DelegatingConnection<?> c,
                                       CallableStatement s) {
        super(c, s);
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).registerOutParameter( parameterIndex,  sqlType); } catch (SQLException e) { handleException(e); } }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).registerOutParameter( parameterIndex,  sqlType,  scale); } catch (SQLException e) { handleException(e); } }

    @Override
    public boolean wasNull() throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).wasNull(); } catch (SQLException e) { handleException(e); return false; } }

    @Override
    public String getString(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getString( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public boolean getBoolean(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getBoolean( parameterIndex); } catch (SQLException e) { handleException(e); return false; } }

    @Override
    public byte getByte(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getByte( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public short getShort(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getShort( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public int getInt(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getInt( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public long getLong(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getLong( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public float getFloat(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getFloat( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public double getDouble(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getDouble( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    /** @deprecated Use {@link #getBigDecimal(int)} or {@link #getBigDecimal(String)} */
    @Override
    @Deprecated
    public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getBigDecimal( parameterIndex,  scale); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public byte[] getBytes(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getBytes( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Date getDate(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getDate( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Time getTime(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getTime( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Timestamp getTimestamp(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getTimestamp( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Object getObject(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getObject( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public BigDecimal getBigDecimal(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getBigDecimal( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Object getObject(int i, Map<String,Class<?>> map) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getObject( i, map); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Ref getRef(int i) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getRef( i); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Blob getBlob(int i) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getBlob( i); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Clob getClob(int i) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getClob( i); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Array getArray(int i) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getArray( i); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Date getDate(int parameterIndex, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getDate( parameterIndex,  cal); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Time getTime(int parameterIndex, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getTime( parameterIndex,  cal); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getTimestamp( parameterIndex,  cal); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public void registerOutParameter(int paramIndex, int sqlType, String typeName) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).registerOutParameter( paramIndex,  sqlType,  typeName); } catch (SQLException e) { handleException(e); } }

    @Override
    public void registerOutParameter(String parameterName, int sqlType) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).registerOutParameter(parameterName, sqlType); } catch (SQLException e) { handleException(e); } }

    @Override
    public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).registerOutParameter(parameterName, sqlType, scale); } catch (SQLException e) { handleException(e); } }

    @Override
    public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).registerOutParameter(parameterName, sqlType, typeName); } catch (SQLException e) { handleException(e); } }

    @Override
    public URL getURL(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getURL(parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public void setURL(String parameterName, URL val) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setURL(parameterName, val); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setNull(String parameterName, int sqlType) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setNull(parameterName, sqlType); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setBoolean(String parameterName, boolean x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setBoolean(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setByte(String parameterName, byte x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setByte(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setShort(String parameterName, short x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setShort(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setInt(String parameterName, int x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setInt(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setLong(String parameterName, long x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setLong(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setFloat(String parameterName, float x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setFloat(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setDouble(String parameterName, double x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setDouble(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setBigDecimal(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setString(String parameterName, String x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setString(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setBytes(String parameterName, byte [] x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setBytes(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setDate(String parameterName, Date x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setDate(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setTime(String parameterName, Time x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setTime(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setTimestamp(String parameterName, Timestamp x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setTimestamp(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setAsciiStream(parameterName, x, length); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setBinaryStream(parameterName, x, length); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setObject(parameterName, x, targetSqlType, scale); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setObject(parameterName, x, targetSqlType); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setObject(String parameterName, Object x) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setObject(parameterName, x); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException
    { checkOpen(); ((CallableStatement)getDelegate()).setCharacterStream(parameterName, reader, length); }

    @Override
    public void setDate(String parameterName, Date x, Calendar cal) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setDate(parameterName, x, cal); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setTime(String parameterName, Time x, Calendar cal) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setTime(parameterName, x, cal); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setTimestamp(parameterName, x, cal); } catch (SQLException e) { handleException(e); } }

    @Override
    public void setNull(String parameterName, int sqlType, String typeName) throws SQLException
    { checkOpen(); try { ((CallableStatement)getDelegate()).setNull(parameterName, sqlType, typeName); } catch (SQLException e) { handleException(e); } }

    @Override
    public String getString(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getString(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public boolean getBoolean(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getBoolean(parameterName); } catch (SQLException e) { handleException(e); return false; } }

    @Override
    public byte getByte(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getByte(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public short getShort(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getShort(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public int getInt(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getInt(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public long getLong(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getLong(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public float getFloat(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getFloat(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public double getDouble(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getDouble(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    @Override
    public byte[] getBytes(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getBytes(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Date getDate(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getDate(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Time getTime(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getTime(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Timestamp getTimestamp(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getTimestamp(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Object getObject(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getObject(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public BigDecimal getBigDecimal(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getBigDecimal(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Object getObject(String parameterName, Map<String,Class<?>> map) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getObject(parameterName, map); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Ref getRef(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getRef(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Blob getBlob(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getBlob(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Clob getClob(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getClob(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Array getArray(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getArray(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Date getDate(String parameterName, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getDate(parameterName, cal); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Time getTime(String parameterName, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getTime(parameterName, cal); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getTimestamp(parameterName, cal); } catch (SQLException e) { handleException(e); return null; } }

    @Override
    public URL getURL(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)getDelegate()).getURL(parameterName); } catch (SQLException e) { handleException(e); return null; } }


    @Override
    public RowId getRowId(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getRowId(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public RowId getRowId(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getRowId(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void setRowId(String parameterName, RowId value) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setRowId(parameterName, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNString(String parameterName, String value) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setNString(parameterName, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNCharacterStream(String parameterName, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setNCharacterStream(parameterName, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(String parameterName, NClob value) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setNClob(parameterName, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(String parameterName, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setClob(parameterName, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setBlob(parameterName, inputStream, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(String parameterName, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setNClob(parameterName, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public NClob getNClob(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getNClob(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public NClob getNClob(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getNClob(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void setSQLXML(String parameterName, SQLXML value) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setSQLXML(parameterName, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public SQLXML getSQLXML(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getSQLXML(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public SQLXML getSQLXML(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getSQLXML(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getNString(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getNString(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getNString(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getNString(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getNCharacterStream(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getNCharacterStream(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getNCharacterStream(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getNCharacterStream(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getCharacterStream(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getCharacterStream(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getCharacterStream(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getCharacterStream(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void setBlob(String parameterName, Blob blob) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setBlob(parameterName, blob);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(String parameterName, Clob clob) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setClob(parameterName, clob);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(String parameterName, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setAsciiStream(parameterName, inputStream, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setBinaryStream(parameterName, inputStream, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setCharacterStream(parameterName, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(String parameterName, InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setAsciiStream(parameterName, inputStream);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setBinaryStream(parameterName, inputStream);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setCharacterStream(parameterName, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNCharacterStream(String parameterName, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setNCharacterStream(parameterName, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(String parameterName, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setClob(parameterName, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(String parameterName, InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setBlob(parameterName, inputStream);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(String parameterName, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)getDelegate()).setNClob(parameterName, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    @Override
    public <T> T getObject(int parameterIndex, Class<T> type)
            throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getObject(parameterIndex, type);
}
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public <T> T getObject(String parameterName, Class<T> type)
            throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)getDelegate()).getObject(parameterName, type);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }


}

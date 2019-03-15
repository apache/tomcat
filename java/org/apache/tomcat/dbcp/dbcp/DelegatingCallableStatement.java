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

import java.net.URL;
import java.sql.CallableStatement;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Map;
import java.sql.Ref;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Array;
import java.util.Calendar;
import java.io.InputStream;
import java.io.Reader;
import java.sql.SQLException;
/* JDBC_4_ANT_KEY_BEGIN */
import java.sql.NClob;
import java.sql.RowId;
import java.sql.SQLXML;
/* JDBC_4_ANT_KEY_END */

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
 * @version $Revision: 883941 $ $Date: 2009-11-24 19:58:50 -0500 (Tue, 24 Nov 2009) $
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
    public DelegatingCallableStatement(DelegatingConnection c,
                                       CallableStatement s) {
        super(c, s);
    }

    public boolean equals(Object obj) {
        CallableStatement delegate = (CallableStatement) getInnermostDelegate();
        if (delegate == null) {
            return false;
        }
        if (obj instanceof DelegatingCallableStatement) {
            DelegatingCallableStatement s = (DelegatingCallableStatement) obj;
            return delegate.equals(s.getInnermostDelegate());
        }
        else {
            return delegate.equals(obj);
        }
    }

    /** Sets my delegate. */
    public void setDelegate(CallableStatement s) {
        super.setDelegate(s);
        _stmt = s;
    }

    public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).registerOutParameter( parameterIndex,  sqlType); } catch (SQLException e) { handleException(e); } }

    public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).registerOutParameter( parameterIndex,  sqlType,  scale); } catch (SQLException e) { handleException(e); } }

    public boolean wasNull() throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).wasNull(); } catch (SQLException e) { handleException(e); return false; } }

    public String getString(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getString( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    public boolean getBoolean(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getBoolean( parameterIndex); } catch (SQLException e) { handleException(e); return false; } }

    public byte getByte(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getByte( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    public short getShort(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getShort( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    public int getInt(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getInt( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    public long getLong(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getLong( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    public float getFloat(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getFloat( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    public double getDouble(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getDouble( parameterIndex); } catch (SQLException e) { handleException(e); return 0; } }

    /** @deprecated */
    public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getBigDecimal( parameterIndex,  scale); } catch (SQLException e) { handleException(e); return null; } }

    public byte[] getBytes(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getBytes( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    public Date getDate(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getDate( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    public Time getTime(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getTime( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    public Timestamp getTimestamp(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getTimestamp( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    public Object getObject(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getObject( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    public BigDecimal getBigDecimal(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getBigDecimal( parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    public Object getObject(int i, Map map) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getObject( i, map); } catch (SQLException e) { handleException(e); return null; } }

    public Ref getRef(int i) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getRef( i); } catch (SQLException e) { handleException(e); return null; } }

    public Blob getBlob(int i) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getBlob( i); } catch (SQLException e) { handleException(e); return null; } }

    public Clob getClob(int i) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getClob( i); } catch (SQLException e) { handleException(e); return null; } }

    public Array getArray(int i) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getArray( i); } catch (SQLException e) { handleException(e); return null; } }

    public Date getDate(int parameterIndex, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getDate( parameterIndex,  cal); } catch (SQLException e) { handleException(e); return null; } }

    public Time getTime(int parameterIndex, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getTime( parameterIndex,  cal); } catch (SQLException e) { handleException(e); return null; } }

    public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getTimestamp( parameterIndex,  cal); } catch (SQLException e) { handleException(e); return null; } }

    public void registerOutParameter(int paramIndex, int sqlType, String typeName) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).registerOutParameter( paramIndex,  sqlType,  typeName); } catch (SQLException e) { handleException(e); } }

    public void registerOutParameter(String parameterName, int sqlType) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).registerOutParameter(parameterName, sqlType); } catch (SQLException e) { handleException(e); } }

    public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).registerOutParameter(parameterName, sqlType, scale); } catch (SQLException e) { handleException(e); } }

    public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).registerOutParameter(parameterName, sqlType, typeName); } catch (SQLException e) { handleException(e); } }

    public URL getURL(int parameterIndex) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getURL(parameterIndex); } catch (SQLException e) { handleException(e); return null; } }

    public void setURL(String parameterName, URL val) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setURL(parameterName, val); } catch (SQLException e) { handleException(e); } }

    public void setNull(String parameterName, int sqlType) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setNull(parameterName, sqlType); } catch (SQLException e) { handleException(e); } }

    public void setBoolean(String parameterName, boolean x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setBoolean(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setByte(String parameterName, byte x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setByte(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setShort(String parameterName, short x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setShort(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setInt(String parameterName, int x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setInt(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setLong(String parameterName, long x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setLong(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setFloat(String parameterName, float x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setFloat(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setDouble(String parameterName, double x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setDouble(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setBigDecimal(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setString(String parameterName, String x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setString(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setBytes(String parameterName, byte [] x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setBytes(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setDate(String parameterName, Date x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setDate(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setTime(String parameterName, Time x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setTime(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setTimestamp(String parameterName, Timestamp x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setTimestamp(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setAsciiStream(parameterName, x, length); } catch (SQLException e) { handleException(e); } }

    public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setBinaryStream(parameterName, x, length); } catch (SQLException e) { handleException(e); } }

    public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setObject(parameterName, x, targetSqlType, scale); } catch (SQLException e) { handleException(e); } }

    public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setObject(parameterName, x, targetSqlType); } catch (SQLException e) { handleException(e); } }

    public void setObject(String parameterName, Object x) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setObject(parameterName, x); } catch (SQLException e) { handleException(e); } }

    public void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException
    { checkOpen(); ((CallableStatement)_stmt).setCharacterStream(parameterName, reader, length); }

    public void setDate(String parameterName, Date x, Calendar cal) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setDate(parameterName, x, cal); } catch (SQLException e) { handleException(e); } }

    public void setTime(String parameterName, Time x, Calendar cal) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setTime(parameterName, x, cal); } catch (SQLException e) { handleException(e); } }

    public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setTimestamp(parameterName, x, cal); } catch (SQLException e) { handleException(e); } }

    public void setNull(String parameterName, int sqlType, String typeName) throws SQLException
    { checkOpen(); try { ((CallableStatement)_stmt).setNull(parameterName, sqlType, typeName); } catch (SQLException e) { handleException(e); } }

    public String getString(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getString(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public boolean getBoolean(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getBoolean(parameterName); } catch (SQLException e) { handleException(e); return false; } }

    public byte getByte(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getByte(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    public short getShort(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getShort(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    public int getInt(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getInt(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    public long getLong(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getLong(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    public float getFloat(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getFloat(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    public double getDouble(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getDouble(parameterName); } catch (SQLException e) { handleException(e); return 0; } }

    public byte[] getBytes(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getBytes(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public Date getDate(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getDate(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public Time getTime(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getTime(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public Timestamp getTimestamp(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getTimestamp(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public Object getObject(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getObject(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public BigDecimal getBigDecimal(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getBigDecimal(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public Object getObject(String parameterName, Map map) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getObject(parameterName, map); } catch (SQLException e) { handleException(e); return null; } }

    public Ref getRef(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getRef(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public Blob getBlob(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getBlob(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public Clob getClob(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getClob(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public Array getArray(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getArray(parameterName); } catch (SQLException e) { handleException(e); return null; } }

    public Date getDate(String parameterName, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getDate(parameterName, cal); } catch (SQLException e) { handleException(e); return null; } }

    public Time getTime(String parameterName, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getTime(parameterName, cal); } catch (SQLException e) { handleException(e); return null; } }

    public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getTimestamp(parameterName, cal); } catch (SQLException e) { handleException(e); return null; } }

    public URL getURL(String parameterName) throws SQLException
    { checkOpen(); try { return ((CallableStatement)_stmt).getURL(parameterName); } catch (SQLException e) { handleException(e); return null; } }

/* JDBC_4_ANT_KEY_BEGIN */

    public RowId getRowId(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getRowId(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public RowId getRowId(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getRowId(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public void setRowId(String parameterName, RowId value) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setRowId(parameterName, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setNString(String parameterName, String value) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setNString(parameterName, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setNCharacterStream(String parameterName, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setNCharacterStream(parameterName, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setNClob(String parameterName, NClob value) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setNClob(parameterName, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setClob(String parameterName, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setClob(parameterName, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setBlob(parameterName, inputStream, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setNClob(String parameterName, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setNClob(parameterName, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public NClob getNClob(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getNClob(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public NClob getNClob(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getNClob(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public void setSQLXML(String parameterName, SQLXML value) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setSQLXML(parameterName, value);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public SQLXML getSQLXML(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getSQLXML(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public SQLXML getSQLXML(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getSQLXML(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public String getNString(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getNString(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public String getNString(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getNString(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public Reader getNCharacterStream(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getNCharacterStream(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public Reader getNCharacterStream(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getNCharacterStream(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public Reader getCharacterStream(int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getCharacterStream(parameterIndex);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public Reader getCharacterStream(String parameterName) throws SQLException {
        checkOpen();
        try {
            return ((CallableStatement)_stmt).getCharacterStream(parameterName);
        }
        catch (SQLException e) {
            handleException(e);
            return null;
        }
    }

    public void setBlob(String parameterName, Blob blob) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setBlob(parameterName, blob);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setClob(String parameterName, Clob clob) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setClob(parameterName, clob);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setAsciiStream(String parameterName, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setAsciiStream(parameterName, inputStream, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setBinaryStream(String parameterName, InputStream inputStream, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setBinaryStream(parameterName, inputStream, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setCharacterStream(parameterName, reader, length);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setAsciiStream(String parameterName, InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setAsciiStream(parameterName, inputStream);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setBinaryStream(String parameterName, InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setBinaryStream(parameterName, inputStream);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setCharacterStream(String parameterName, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setCharacterStream(parameterName, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setNCharacterStream(String parameterName, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setNCharacterStream(parameterName, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }

    public void setClob(String parameterName, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setClob(parameterName, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }    }

    public void setBlob(String parameterName, InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setBlob(parameterName, inputStream);
        }
        catch (SQLException e) {
            handleException(e);
        }    }

    public void setNClob(String parameterName, Reader reader) throws SQLException {
        checkOpen();
        try {
            ((CallableStatement)_stmt).setNClob(parameterName, reader);
        }
        catch (SQLException e) {
            handleException(e);
        }
    }
/* JDBC_4_ANT_KEY_END */
}

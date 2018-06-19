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
 * All of the methods from the {@link CallableStatement} interface simply call the corresponding method on the
 * "delegate" provided in my constructor.
 * </p>
 * <p>
 * Extends AbandonedTrace to implement Statement tracking and logging of code which created the Statement. Tracking the
 * Statement ensures that the Connection which created it can close any open Statement's on Connection close.
 * </p>
 *
 * @since 2.0
 */
public class DelegatingCallableStatement extends DelegatingPreparedStatement implements CallableStatement {

    /**
     * Creates a wrapper for the Statement which traces this Statement to the Connection which created it and the code
     * which created it.
     *
     * @param c
     *            the {@link DelegatingConnection} that created this statement
     * @param s
     *            the {@link CallableStatement} to delegate all calls to
     */
    public DelegatingCallableStatement(final DelegatingConnection<?> c, final CallableStatement s) {
        super(c, s);
    }

    @Override
    public void registerOutParameter(final int parameterIndex, final int sqlType) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().registerOutParameter(parameterIndex, sqlType);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void registerOutParameter(final int parameterIndex, final int sqlType, final int scale) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().registerOutParameter(parameterIndex, sqlType, scale);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean wasNull() throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().wasNull();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public String getString(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getString(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public boolean getBoolean(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getBoolean(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public byte getByte(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getByte(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public short getShort(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getShort(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getInt(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getInt(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public long getLong(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getLong(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public float getFloat(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getFloat(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public double getDouble(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getDouble(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    /** @deprecated Use {@link #getBigDecimal(int)} or {@link #getBigDecimal(String)} */
    @Override
    @Deprecated
    public BigDecimal getBigDecimal(final int parameterIndex, final int scale) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getBigDecimal(parameterIndex, scale);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public byte[] getBytes(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getBytes(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Date getDate(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getDate(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Time getTime(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getTime(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Timestamp getTimestamp(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getTimestamp(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Object getObject(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getObject(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public BigDecimal getBigDecimal(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getBigDecimal(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Object getObject(final int i, final Map<String, Class<?>> map) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getObject(i, map);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Ref getRef(final int i) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getRef(i);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Blob getBlob(final int i) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getBlob(i);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Clob getClob(final int i) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getClob(i);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Array getArray(final int i) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getArray(i);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Date getDate(final int parameterIndex, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getDate(parameterIndex, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Time getTime(final int parameterIndex, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getTime(parameterIndex, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Timestamp getTimestamp(final int parameterIndex, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getTimestamp(parameterIndex, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void registerOutParameter(final int paramIndex, final int sqlType, final String typeName)
            throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().registerOutParameter(paramIndex, sqlType, typeName);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void registerOutParameter(final String parameterName, final int sqlType) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().registerOutParameter(parameterName, sqlType);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void registerOutParameter(final String parameterName, final int sqlType, final int scale)
            throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().registerOutParameter(parameterName, sqlType, scale);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void registerOutParameter(final String parameterName, final int sqlType, final String typeName)
            throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().registerOutParameter(parameterName, sqlType, typeName);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public URL getURL(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getURL(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void setURL(final String parameterName, final URL val) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setURL(parameterName, val);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNull(final String parameterName, final int sqlType) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setNull(parameterName, sqlType);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBoolean(final String parameterName, final boolean x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setBoolean(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setByte(final String parameterName, final byte x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setByte(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setShort(final String parameterName, final short x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setShort(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setInt(final String parameterName, final int x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setInt(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setLong(final String parameterName, final long x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setLong(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setFloat(final String parameterName, final float x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setFloat(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setDouble(final String parameterName, final double x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setDouble(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBigDecimal(final String parameterName, final BigDecimal x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setBigDecimal(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setString(final String parameterName, final String x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setString(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBytes(final String parameterName, final byte[] x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setBytes(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setDate(final String parameterName, final Date x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setDate(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setTime(final String parameterName, final Time x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setTime(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setTimestamp(final String parameterName, final Timestamp x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setTimestamp(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(final String parameterName, final InputStream x, final int length) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setAsciiStream(parameterName, x, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(final String parameterName, final InputStream x, final int length) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setBinaryStream(parameterName, x, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setObject(final String parameterName, final Object x, final int targetSqlType, final int scale)
            throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setObject(parameterName, x, targetSqlType, scale);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setObject(final String parameterName, final Object x, final int targetSqlType) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setObject(parameterName, x, targetSqlType);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setObject(final String parameterName, final Object x) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setObject(parameterName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(final String parameterName, final Reader reader, final int length)
            throws SQLException {
        checkOpen();
        getDelegateCallableStatement().setCharacterStream(parameterName, reader, length);
    }

    @Override
    public void setDate(final String parameterName, final Date x, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setDate(parameterName, x, cal);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setTime(final String parameterName, final Time x, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setTime(parameterName, x, cal);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setTimestamp(final String parameterName, final Timestamp x, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setTimestamp(parameterName, x, cal);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNull(final String parameterName, final int sqlType, final String typeName) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setNull(parameterName, sqlType, typeName);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public String getString(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getString(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public boolean getBoolean(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getBoolean(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public byte getByte(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getByte(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public short getShort(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getShort(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getInt(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getInt(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public long getLong(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getLong(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public float getFloat(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getFloat(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public double getDouble(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getDouble(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public byte[] getBytes(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getBytes(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Date getDate(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getDate(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Time getTime(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getTime(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Timestamp getTimestamp(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getTimestamp(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Object getObject(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getObject(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public BigDecimal getBigDecimal(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getBigDecimal(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Object getObject(final String parameterName, final Map<String, Class<?>> map) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getObject(parameterName, map);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Ref getRef(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getRef(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Blob getBlob(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getBlob(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Clob getClob(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getClob(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Array getArray(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getArray(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Date getDate(final String parameterName, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getDate(parameterName, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    private CallableStatement getDelegateCallableStatement() {
        return (CallableStatement) getDelegate();
    }

    @Override
    public Time getTime(final String parameterName, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getTime(parameterName, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Timestamp getTimestamp(final String parameterName, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getTimestamp(parameterName, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public URL getURL(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getURL(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public RowId getRowId(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getRowId(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public RowId getRowId(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getRowId(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void setRowId(final String parameterName, final RowId value) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setRowId(parameterName, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNString(final String parameterName, final String value) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setNString(parameterName, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNCharacterStream(final String parameterName, final Reader reader, final long length)
            throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setNCharacterStream(parameterName, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(final String parameterName, final NClob value) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setNClob(parameterName, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(final String parameterName, final Reader reader, final long length) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setClob(parameterName, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(final String parameterName, final InputStream inputStream, final long length)
            throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setBlob(parameterName, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(final String parameterName, final Reader reader, final long length) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setNClob(parameterName, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public NClob getNClob(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getNClob(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public NClob getNClob(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getNClob(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void setSQLXML(final String parameterName, final SQLXML value) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setSQLXML(parameterName, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public SQLXML getSQLXML(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getSQLXML(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public SQLXML getSQLXML(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getSQLXML(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getNString(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getNString(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getNString(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getNString(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getNCharacterStream(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getNCharacterStream(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getNCharacterStream(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getNCharacterStream(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getCharacterStream(final int parameterIndex) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getCharacterStream(parameterIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getCharacterStream(final String parameterName) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getCharacterStream(parameterName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void setBlob(final String parameterName, final Blob blob) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setBlob(parameterName, blob);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(final String parameterName, final Clob clob) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setClob(parameterName, clob);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(final String parameterName, final InputStream inputStream, final long length)
            throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setAsciiStream(parameterName, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(final String parameterName, final InputStream inputStream, final long length)
            throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setBinaryStream(parameterName, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(final String parameterName, final Reader reader, final long length)
            throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setCharacterStream(parameterName, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(final String parameterName, final InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setAsciiStream(parameterName, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(final String parameterName, final InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setBinaryStream(parameterName, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(final String parameterName, final Reader reader) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setCharacterStream(parameterName, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNCharacterStream(final String parameterName, final Reader reader) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setNCharacterStream(parameterName, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(final String parameterName, final Reader reader) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setClob(parameterName, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(final String parameterName, final InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setBlob(parameterName, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(final String parameterName, final Reader reader) throws SQLException {
        checkOpen();
        try {
            getDelegateCallableStatement().setNClob(parameterName, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public <T> T getObject(final int parameterIndex, final Class<T> type) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getObject(parameterIndex, type);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public <T> T getObject(final String parameterName, final Class<T> type) throws SQLException {
        checkOpen();
        try {
            return getDelegateCallableStatement().getObject(parameterName, type);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

}

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
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * A base delegating implementation of {@link PreparedStatement}.
 * <p>
 * All of the methods from the {@link PreparedStatement} interface simply check to see that the
 * {@link PreparedStatement} is active, and call the corresponding method on the "delegate" provided in my constructor.
 * <p>
 * Extends AbandonedTrace to implement Statement tracking and logging of code which created the Statement. Tracking the
 * Statement ensures that the Connection which created it can close any open Statement's on Connection close.
 *
 * @since 2.0
 */
public class DelegatingPreparedStatement extends DelegatingStatement implements PreparedStatement {

    /**
     * Create a wrapper for the Statement which traces this Statement to the Connection which created it and the code
     * which created it.
     *
     * @param statement
     *            the {@link PreparedStatement} to delegate all calls to.
     * @param connection
     *            the {@link DelegatingConnection} that created this statement.
     */
    public DelegatingPreparedStatement(final DelegatingConnection<?> connection, final PreparedStatement statement) {
        super(connection, statement);
    }

    @Override
    public void addBatch() throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().addBatch();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void clearParameters() throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().clearParameters();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean execute() throws SQLException {
        checkOpen();
        if (getConnectionInternal() != null) {
            getConnectionInternal().setLastUsed();
        }
        try {
            return getDelegatePreparedStatement().execute();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    /**
     * @since 2.5.0
     */
    @Override
    public long executeLargeUpdate() throws SQLException {
        checkOpen();
        try {
            return getDelegatePreparedStatement().executeLargeUpdate();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        checkOpen();
        if (getConnectionInternal() != null) {
            getConnectionInternal().setLastUsed();
        }
        try {
            return DelegatingResultSet.wrapResultSet(this, getDelegatePreparedStatement().executeQuery());
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public int executeUpdate() throws SQLException {
        checkOpen();
        if (getConnectionInternal() != null) {
            getConnectionInternal().setLastUsed();
        }
        try {
            return getDelegatePreparedStatement().executeUpdate();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    private PreparedStatement getDelegatePreparedStatement() {
        return (PreparedStatement) getDelegate();
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkOpen();
        try {
            return getDelegatePreparedStatement().getMetaData();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public java.sql.ParameterMetaData getParameterMetaData() throws SQLException {
        checkOpen();
        try {
            return getDelegatePreparedStatement().getParameterMetaData();
        } catch (final SQLException e) {
            handleException(e);
            throw new AssertionError();
        }
    }

    @Override
    public void setArray(final int i, final Array x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setArray(i, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(final int parameterIndex, final InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setAsciiStream(parameterIndex, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setAsciiStream(parameterIndex, x, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(final int parameterIndex, final InputStream inputStream, final long length)
            throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setAsciiStream(parameterIndex, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBigDecimal(final int parameterIndex, final BigDecimal x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setBigDecimal(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(final int parameterIndex, final InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setBinaryStream(parameterIndex, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setBinaryStream(parameterIndex, x, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(final int parameterIndex, final InputStream inputStream, final long length)
            throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setBinaryStream(parameterIndex, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(final int i, final Blob x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setBlob(i, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(final int parameterIndex, final InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setBlob(parameterIndex, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(final int parameterIndex, final InputStream inputStream, final long length)
            throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setBlob(parameterIndex, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBoolean(final int parameterIndex, final boolean x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setBoolean(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setByte(final int parameterIndex, final byte x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setByte(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBytes(final int parameterIndex, final byte[] x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setBytes(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(final int parameterIndex, final Reader reader) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setCharacterStream(parameterIndex, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(final int parameterIndex, final Reader reader, final int length)
            throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setCharacterStream(parameterIndex, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(final int parameterIndex, final Reader reader, final long length)
            throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setCharacterStream(parameterIndex, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(final int i, final Clob x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setClob(i, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(final int parameterIndex, final Reader reader) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setClob(parameterIndex, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setClob(parameterIndex, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setDate(final int parameterIndex, final Date x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setDate(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setDate(final int parameterIndex, final Date x, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setDate(parameterIndex, x, cal);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setDouble(final int parameterIndex, final double x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setDouble(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setFloat(final int parameterIndex, final float x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setFloat(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setInt(final int parameterIndex, final int x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setInt(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setLong(final int parameterIndex, final long x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setLong(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNCharacterStream(final int parameterIndex, final Reader reader) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setNCharacterStream(parameterIndex, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNCharacterStream(final int parameterIndex, final Reader value, final long length)
            throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setNCharacterStream(parameterIndex, value, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(final int parameterIndex, final NClob value) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setNClob(parameterIndex, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(final int parameterIndex, final Reader reader) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setNClob(parameterIndex, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setNClob(parameterIndex, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNString(final int parameterIndex, final String value) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setNString(parameterIndex, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNull(final int parameterIndex, final int sqlType) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setNull(parameterIndex, sqlType);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNull(final int paramIndex, final int sqlType, final String typeName) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setNull(paramIndex, sqlType, typeName);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setObject(final int parameterIndex, final Object x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setObject(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setObject(final int parameterIndex, final Object x, final int targetSqlType) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setObject(parameterIndex, x, targetSqlType);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setObject(final int parameterIndex, final Object x, final int targetSqlType, final int scale)
            throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setObject(parameterIndex, x, targetSqlType, scale);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    /**
     * @since 2.5.0
     */
    @Override
    public void setObject(final int parameterIndex, final Object x, final SQLType targetSqlType) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setObject(parameterIndex, x, targetSqlType);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    /**
     * @since 2.5.0
     */
    @Override
    public void setObject(final int parameterIndex, final Object x, final SQLType targetSqlType, final int scaleOrLength) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setObject(parameterIndex, x, targetSqlType, scaleOrLength);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setRef(final int i, final Ref x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setRef(i, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setRowId(final int parameterIndex, final RowId value) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setRowId(parameterIndex, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setShort(final int parameterIndex, final short x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setShort(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setSQLXML(final int parameterIndex, final SQLXML value) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setSQLXML(parameterIndex, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setString(final int parameterIndex, final String x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setString(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setTime(final int parameterIndex, final Time x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setTime(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setTime(final int parameterIndex, final Time x, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setTime(parameterIndex, x, cal);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setTimestamp(final int parameterIndex, final Timestamp x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setTimestamp(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setTimestamp(final int parameterIndex, final Timestamp x, final Calendar cal) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setTimestamp(parameterIndex, x, cal);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    /** @deprecated Use setAsciiStream(), setCharacterStream() or setNCharacterStream() */
    @Deprecated
    @Override
    public void setUnicodeStream(final int parameterIndex, final InputStream x, final int length) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setUnicodeStream(parameterIndex, x, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setURL(final int parameterIndex, final java.net.URL x) throws SQLException {
        checkOpen();
        try {
            getDelegatePreparedStatement().setURL(parameterIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    /**
     * Returns a String representation of this object.
     *
     * @return String
     */
    @Override
    public synchronized String toString() {
        final Statement statement = getDelegate();
        return statement == null ? "NULL" : statement.toString();
    }
}

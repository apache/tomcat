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
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

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
 * @since 2.0
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
    public DelegatingPreparedStatement(final DelegatingConnection<?> c,
                                       final PreparedStatement s) {
        super(c, s);
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        checkOpen();
        if (getConnectionInternal() != null) {
            getConnectionInternal().setLastUsed();
        }
        try {
            return DelegatingResultSet.wrapResultSet(this,((PreparedStatement)getDelegate()).executeQuery());
        }
        catch (final SQLException e) {
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
            return ((PreparedStatement) getDelegate()).executeUpdate();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public void setNull(final int parameterIndex, final int sqlType) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setNull(parameterIndex,sqlType); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setBoolean(final int parameterIndex, final boolean x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setBoolean(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setByte(final int parameterIndex, final byte x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setByte(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setShort(final int parameterIndex, final short x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setShort(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setInt(final int parameterIndex, final int x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setInt(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setLong(final int parameterIndex, final long x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setLong(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setFloat(final int parameterIndex, final float x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setFloat(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setDouble(final int parameterIndex, final double x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setDouble(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setBigDecimal(final int parameterIndex, final BigDecimal x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setBigDecimal(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setString(final int parameterIndex, final String x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setString(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setBytes(final int parameterIndex, final byte[] x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setBytes(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setDate(final int parameterIndex, final Date x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setDate(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setTime(final int parameterIndex, final Time x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setTime(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setTimestamp(final int parameterIndex, final Timestamp x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setTimestamp(parameterIndex,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setAsciiStream(final int parameterIndex, final InputStream x, final int length) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setAsciiStream(parameterIndex,x,length); } catch (final SQLException e) { handleException(e); } }

    /** @deprecated Use setAsciiStream(), setCharacterStream() or setNCharacterStream() */
    @Deprecated
    @Override
    public void setUnicodeStream(final int parameterIndex, final InputStream x, final int length) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setUnicodeStream(parameterIndex,x,length); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setBinaryStream(final int parameterIndex, final InputStream x, final int length) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setBinaryStream(parameterIndex,x,length); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void clearParameters() throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).clearParameters(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setObject(final int parameterIndex, final Object x, final int targetSqlType, final int scale) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setObject(parameterIndex, x, targetSqlType, scale); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setObject(final int parameterIndex, final Object x, final int targetSqlType) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setObject(parameterIndex, x, targetSqlType); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setObject(final int parameterIndex, final Object x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setObject(parameterIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public boolean execute() throws SQLException {
        checkOpen();
        if (getConnectionInternal() != null) {
            getConnectionInternal().setLastUsed();
        }
        try {
            return ((PreparedStatement) getDelegate()).execute();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void addBatch() throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).addBatch(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setCharacterStream(final int parameterIndex, final Reader reader, final int length) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setCharacterStream(parameterIndex,reader,length); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setRef(final int i, final Ref x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setRef(i,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setBlob(final int i, final Blob x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setBlob(i,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setClob(final int i, final Clob x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setClob(i,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setArray(final int i, final Array x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setArray(i,x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException
    { checkOpen(); try { return ((PreparedStatement)getDelegate()).getMetaData(); } catch (final SQLException e) { handleException(e); throw new AssertionError(); } }

    @Override
    public void setDate(final int parameterIndex, final Date x, final Calendar cal) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setDate(parameterIndex,x,cal); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setTime(final int parameterIndex, final Time x, final Calendar cal) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setTime(parameterIndex,x,cal); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setTimestamp(final int parameterIndex, final Timestamp x, final Calendar cal) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setTimestamp(parameterIndex,x,cal); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void setNull(final int paramIndex, final int sqlType, final String typeName) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setNull(paramIndex,sqlType,typeName); } catch (final SQLException e) { handleException(e); } }

    /**
     * Returns a String representation of this object.
     *
     * @return String
     */
    @Override
    public String toString() {
        final Statement statement = getDelegate();
        return statement == null ? "NULL" : statement.toString();
    }

    @Override
    public void setURL(final int parameterIndex, final java.net.URL x) throws SQLException
    { checkOpen(); try { ((PreparedStatement)getDelegate()).setURL(parameterIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public java.sql.ParameterMetaData getParameterMetaData() throws SQLException
    { checkOpen(); try { return ((PreparedStatement)getDelegate()).getParameterMetaData(); } catch (final SQLException e) { handleException(e); throw new AssertionError(); } }


    @Override
    public void setRowId(final int parameterIndex, final RowId value) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setRowId(parameterIndex, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNString(final int parameterIndex, final String value) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setNString(parameterIndex, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNCharacterStream(final int parameterIndex, final Reader value, final long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setNCharacterStream(parameterIndex, value, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(final int parameterIndex, final NClob value) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setNClob(parameterIndex, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setClob(parameterIndex, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(final int parameterIndex, final InputStream inputStream, final long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setBlob(parameterIndex, inputStream, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(final int parameterIndex, final Reader reader, final long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setNClob(parameterIndex, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setSQLXML(final int parameterIndex, final SQLXML value) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setSQLXML(parameterIndex, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(final int parameterIndex, final InputStream inputStream, final long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setAsciiStream(parameterIndex, inputStream, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(final int parameterIndex, final InputStream inputStream, final long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setBinaryStream(parameterIndex, inputStream, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(final int parameterIndex, final Reader reader, final long length) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setCharacterStream(parameterIndex, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAsciiStream(final int parameterIndex, final InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setAsciiStream(parameterIndex, inputStream);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBinaryStream(final int parameterIndex, final InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setBinaryStream(parameterIndex, inputStream);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setCharacterStream(final int parameterIndex, final Reader reader) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setCharacterStream(parameterIndex, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNCharacterStream(final int parameterIndex, final Reader reader) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setNCharacterStream(parameterIndex, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setClob(final int parameterIndex, final Reader reader) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setClob(parameterIndex, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setBlob(final int parameterIndex, final InputStream inputStream) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setBlob(parameterIndex, inputStream);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNClob(final int parameterIndex, final Reader reader) throws SQLException {
        checkOpen();
        try {
            ((PreparedStatement)getDelegate()).setNClob(parameterIndex, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }
}

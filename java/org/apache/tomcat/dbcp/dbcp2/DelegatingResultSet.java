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
import java.sql.Connection;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * A base delegating implementation of {@link ResultSet}.
 * <p>
 * All of the methods from the {@link ResultSet} interface
 * simply call the corresponding method on the "delegate"
 * provided in my constructor.
 * <p>
 * Extends AbandonedTrace to implement result set tracking and
 * logging of code which created the ResultSet. Tracking the
 * ResultSet ensures that the Statement which created it can
 * close any open ResultSet's on Statement close.
 *
 * @author Glenn L. Nielsen
 * @author James House
 * @author Dirk Verbeeck
 * @since 2.0
 */
public final class DelegatingResultSet extends AbandonedTrace implements ResultSet {

    /** My delegate. **/
    private final ResultSet _res;

    /** The Statement that created me, if any. **/
    private Statement _stmt;

    /** The Connection that created me, if any. **/
    private Connection _conn;

    /**
     * Create a wrapper for the ResultSet which traces this
     * ResultSet to the Statement which created it and the
     * code which created it.
     * <p>
     * Private to ensure all construction is
     * {@link #wrapResultSet(Statement, ResultSet)}
     *
     * @param stmt Statement which created this ResultSet
     * @param res ResultSet to wrap
     */
    private DelegatingResultSet(final Statement stmt, final ResultSet res) {
        super((AbandonedTrace)stmt);
        this._stmt = stmt;
        this._res = res;
    }

    /**
     * Create a wrapper for the ResultSet which traces this
     * ResultSet to the Connection which created it (via, for
     * example DatabaseMetadata, and the code which created it.
     * <p>
     * Private to ensure all construction is
     * {@link #wrapResultSet(Connection, ResultSet)}
     *
     * @param conn Connection which created this ResultSet
     * @param res ResultSet to wrap
     */
    private DelegatingResultSet(final Connection conn, final ResultSet res) {
        super((AbandonedTrace)conn);
        this._conn = conn;
        this._res = res;
    }

    public static ResultSet wrapResultSet(final Statement stmt, final ResultSet rset) {
        if(null == rset) {
            return null;
        }
        return new DelegatingResultSet(stmt,rset);
    }

    public static ResultSet wrapResultSet(final Connection conn, final ResultSet rset) {
        if(null == rset) {
            return null;
        }
        return new DelegatingResultSet(conn,rset);
    }

    public ResultSet getDelegate() {
        return _res;
    }

    /**
     * If my underlying {@link ResultSet} is not a
     * {@code DelegatingResultSet}, returns it,
     * otherwise recursively invokes this method on
     * my delegate.
     * <p>
     * Hence this method will return the first
     * delegate that is not a {@code DelegatingResultSet},
     * or {@code null} when no non-{@code DelegatingResultSet}
     * delegate can be found by traversing this chain.
     * <p>
     * This method is useful when you may have nested
     * {@code DelegatingResultSet}s, and you want to make
     * sure to obtain a "genuine" {@link ResultSet}.
     * @return the result set
     */
    public ResultSet getInnermostDelegate() {
        ResultSet r = _res;
        while(r != null && r instanceof DelegatingResultSet) {
            r = ((DelegatingResultSet)r).getDelegate();
            if(this == r) {
                return null;
            }
        }
        return r;
    }

    @Override
    public Statement getStatement() throws SQLException {
        return _stmt;
    }

    /**
     * Wrapper for close of ResultSet which removes this
     * result set from being traced then calls close on
     * the original ResultSet.
     */
    @Override
    public void close() throws SQLException {
        try {
            if(_stmt != null) {
                ((AbandonedTrace)_stmt).removeTrace(this);
                _stmt = null;
            }
            if(_conn != null) {
                ((AbandonedTrace)_conn).removeTrace(this);
                _conn = null;
            }
            _res.close();
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    protected void handleException(final SQLException e) throws SQLException {
        if (_stmt != null && _stmt instanceof DelegatingStatement) {
            ((DelegatingStatement)_stmt).handleException(e);
        }
        else if (_conn != null && _conn instanceof DelegatingConnection) {
            ((DelegatingConnection<?>)_conn).handleException(e);
        }
        else {
            throw e;
        }
    }

    @Override
    public boolean next() throws SQLException
    { try { return _res.next(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public boolean wasNull() throws SQLException
    { try { return _res.wasNull(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public String getString(final int columnIndex) throws SQLException
    { try { return _res.getString(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public boolean getBoolean(final int columnIndex) throws SQLException
    { try { return _res.getBoolean(columnIndex); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public byte getByte(final int columnIndex) throws SQLException
    { try { return _res.getByte(columnIndex); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public short getShort(final int columnIndex) throws SQLException
    { try { return _res.getShort(columnIndex); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public int getInt(final int columnIndex) throws SQLException
    { try { return _res.getInt(columnIndex); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public long getLong(final int columnIndex) throws SQLException
    { try { return _res.getLong(columnIndex); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public float getFloat(final int columnIndex) throws SQLException
    { try { return _res.getFloat(columnIndex); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public double getDouble(final int columnIndex) throws SQLException
    { try { return _res.getDouble(columnIndex); } catch (final SQLException e) { handleException(e); return 0; } }

    /** @deprecated Use {@link #getBigDecimal(int)} */
    @Deprecated
    @Override
    public BigDecimal getBigDecimal(final int columnIndex, final int scale) throws SQLException
    { try { return _res.getBigDecimal(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public byte[] getBytes(final int columnIndex) throws SQLException
    { try { return _res.getBytes(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Date getDate(final int columnIndex) throws SQLException
    { try { return _res.getDate(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Time getTime(final int columnIndex) throws SQLException
    { try { return _res.getTime(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Timestamp getTimestamp(final int columnIndex) throws SQLException
    { try { return _res.getTimestamp(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public InputStream getAsciiStream(final int columnIndex) throws SQLException
    { try { return _res.getAsciiStream(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    /** @deprecated Use {@link #getCharacterStream(int)} */
    @Deprecated
    @Override
    public InputStream getUnicodeStream(final int columnIndex) throws SQLException
    { try { return _res.getUnicodeStream(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public InputStream getBinaryStream(final int columnIndex) throws SQLException
    { try { return _res.getBinaryStream(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public String getString(final String columnName) throws SQLException
    { try { return _res.getString(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public boolean getBoolean(final String columnName) throws SQLException
    { try { return _res.getBoolean(columnName); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public byte getByte(final String columnName) throws SQLException
    { try { return _res.getByte(columnName); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public short getShort(final String columnName) throws SQLException
    { try { return _res.getShort(columnName); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public int getInt(final String columnName) throws SQLException
    { try { return _res.getInt(columnName); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public long getLong(final String columnName) throws SQLException
    { try { return _res.getLong(columnName); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public float getFloat(final String columnName) throws SQLException
    { try { return _res.getFloat(columnName); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public double getDouble(final String columnName) throws SQLException
    { try { return _res.getDouble(columnName); } catch (final SQLException e) { handleException(e); return 0; } }

    /** @deprecated Use {@link #getBigDecimal(String)} */
    @Deprecated
    @Override
    public BigDecimal getBigDecimal(final String columnName, final int scale) throws SQLException
    { try { return _res.getBigDecimal(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public byte[] getBytes(final String columnName) throws SQLException
    { try { return _res.getBytes(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Date getDate(final String columnName) throws SQLException
    { try { return _res.getDate(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Time getTime(final String columnName) throws SQLException
    { try { return _res.getTime(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Timestamp getTimestamp(final String columnName) throws SQLException
    { try { return _res.getTimestamp(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public InputStream getAsciiStream(final String columnName) throws SQLException
    { try { return _res.getAsciiStream(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    /** @deprecated Use {@link #getCharacterStream(String)} */
    @Deprecated
    @Override
    public InputStream getUnicodeStream(final String columnName) throws SQLException
    { try { return _res.getUnicodeStream(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public InputStream getBinaryStream(final String columnName) throws SQLException
    { try { return _res.getBinaryStream(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public SQLWarning getWarnings() throws SQLException
    { try { return _res.getWarnings(); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public void clearWarnings() throws SQLException
    { try { _res.clearWarnings(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public String getCursorName() throws SQLException
    { try { return _res.getCursorName(); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException
    { try { return _res.getMetaData(); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Object getObject(final int columnIndex) throws SQLException
    { try { return _res.getObject(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Object getObject(final String columnName) throws SQLException
    { try { return _res.getObject(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public int findColumn(final String columnName) throws SQLException
    { try { return _res.findColumn(columnName); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public Reader getCharacterStream(final int columnIndex) throws SQLException
    { try { return _res.getCharacterStream(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Reader getCharacterStream(final String columnName) throws SQLException
    { try { return _res.getCharacterStream(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public BigDecimal getBigDecimal(final int columnIndex) throws SQLException
    { try { return _res.getBigDecimal(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public BigDecimal getBigDecimal(final String columnName) throws SQLException
    { try { return _res.getBigDecimal(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public boolean isBeforeFirst() throws SQLException
    { try { return _res.isBeforeFirst(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public boolean isAfterLast() throws SQLException
    { try { return _res.isAfterLast(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public boolean isFirst() throws SQLException
    { try { return _res.isFirst(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public boolean isLast() throws SQLException
    { try { return _res.isLast(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public void beforeFirst() throws SQLException
    { try { _res.beforeFirst(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void afterLast() throws SQLException
    { try { _res.afterLast(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public boolean first() throws SQLException
    { try { return _res.first(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public boolean last() throws SQLException
    { try { return _res.last(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public int getRow() throws SQLException
    { try { return _res.getRow(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public boolean absolute(final int row) throws SQLException
    { try { return _res.absolute(row); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public boolean relative(final int rows) throws SQLException
    { try { return _res.relative(rows); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public boolean previous() throws SQLException
    { try { return _res.previous(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public void setFetchDirection(final int direction) throws SQLException
    { try { _res.setFetchDirection(direction); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int getFetchDirection() throws SQLException
    { try { return _res.getFetchDirection(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public void setFetchSize(final int rows) throws SQLException
    { try { _res.setFetchSize(rows); } catch (final SQLException e) { handleException(e); } }

    @Override
    public int getFetchSize() throws SQLException
    { try { return _res.getFetchSize(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public int getType() throws SQLException
    { try { return _res.getType(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public int getConcurrency() throws SQLException
    { try { return _res.getConcurrency(); } catch (final SQLException e) { handleException(e); return 0; } }

    @Override
    public boolean rowUpdated() throws SQLException
    { try { return _res.rowUpdated(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public boolean rowInserted() throws SQLException
    { try { return _res.rowInserted(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public boolean rowDeleted() throws SQLException
    { try { return _res.rowDeleted(); } catch (final SQLException e) { handleException(e); return false; } }

    @Override
    public void updateNull(final int columnIndex) throws SQLException
    { try { _res.updateNull(columnIndex); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateBoolean(final int columnIndex, final boolean x) throws SQLException
    { try { _res.updateBoolean(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateByte(final int columnIndex, final byte x) throws SQLException
    { try { _res.updateByte(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateShort(final int columnIndex, final short x) throws SQLException
    { try { _res.updateShort(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateInt(final int columnIndex, final int x) throws SQLException
    { try { _res.updateInt(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateLong(final int columnIndex, final long x) throws SQLException
    { try { _res.updateLong(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateFloat(final int columnIndex, final float x) throws SQLException
    { try { _res.updateFloat(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateDouble(final int columnIndex, final double x) throws SQLException
    { try { _res.updateDouble(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateBigDecimal(final int columnIndex, final BigDecimal x) throws SQLException
    { try { _res.updateBigDecimal(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateString(final int columnIndex, final String x) throws SQLException
    { try { _res.updateString(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateBytes(final int columnIndex, final byte[] x) throws SQLException
    { try { _res.updateBytes(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateDate(final int columnIndex, final Date x) throws SQLException
    { try { _res.updateDate(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateTime(final int columnIndex, final Time x) throws SQLException
    { try { _res.updateTime(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateTimestamp(final int columnIndex, final Timestamp x) throws SQLException
    { try { _res.updateTimestamp(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream x, final int length) throws SQLException
    { try { _res.updateAsciiStream(columnIndex, x, length); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream x, final int length) throws SQLException
    { try { _res.updateBinaryStream(columnIndex, x, length); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader x, final int length) throws SQLException
    { try { _res.updateCharacterStream(columnIndex, x, length); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateObject(final int columnIndex, final Object x, final int scale) throws SQLException
    { try { _res.updateObject(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateObject(final int columnIndex, final Object x) throws SQLException
    { try { _res.updateObject(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateNull(final String columnName) throws SQLException
    { try { _res.updateNull(columnName); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateBoolean(final String columnName, final boolean x) throws SQLException
    { try { _res.updateBoolean(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateByte(final String columnName, final byte x) throws SQLException
    { try { _res.updateByte(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateShort(final String columnName, final short x) throws SQLException
    { try { _res.updateShort(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateInt(final String columnName, final int x) throws SQLException
    { try { _res.updateInt(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateLong(final String columnName, final long x) throws SQLException
    { try { _res.updateLong(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateFloat(final String columnName, final float x) throws SQLException
    { try { _res.updateFloat(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateDouble(final String columnName, final double x) throws SQLException
    { try { _res.updateDouble(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateBigDecimal(final String columnName, final BigDecimal x) throws SQLException
    { try { _res.updateBigDecimal(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateString(final String columnName, final String x) throws SQLException
    { try { _res.updateString(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateBytes(final String columnName, final byte[] x) throws SQLException
    { try { _res.updateBytes(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateDate(final String columnName, final Date x) throws SQLException
    { try { _res.updateDate(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateTime(final String columnName, final Time x) throws SQLException
    { try { _res.updateTime(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateTimestamp(final String columnName, final Timestamp x) throws SQLException
    { try { _res.updateTimestamp(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateAsciiStream(final String columnName, final InputStream x, final int length) throws SQLException
    { try { _res.updateAsciiStream(columnName, x, length); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateBinaryStream(final String columnName, final InputStream x, final int length) throws SQLException
    { try { _res.updateBinaryStream(columnName, x, length); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateCharacterStream(final String columnName, final Reader reader, final int length) throws SQLException
    { try { _res.updateCharacterStream(columnName, reader, length); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateObject(final String columnName, final Object x, final int scale) throws SQLException
    { try { _res.updateObject(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateObject(final String columnName, final Object x) throws SQLException
    { try { _res.updateObject(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void insertRow() throws SQLException
    { try { _res.insertRow(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateRow() throws SQLException
    { try { _res.updateRow(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void deleteRow() throws SQLException
    { try { _res.deleteRow(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void refreshRow() throws SQLException
    { try { _res.refreshRow(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void cancelRowUpdates() throws SQLException
    { try { _res.cancelRowUpdates(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void moveToInsertRow() throws SQLException
    { try { _res.moveToInsertRow(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void moveToCurrentRow() throws SQLException
    { try { _res.moveToCurrentRow(); } catch (final SQLException e) { handleException(e); } }

    @Override
    public Object getObject(final int i, final Map<String,Class<?>> map) throws SQLException
    { try { return _res.getObject(i, map); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Ref getRef(final int i) throws SQLException
    { try { return _res.getRef(i); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Blob getBlob(final int i) throws SQLException
    { try { return _res.getBlob(i); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Clob getClob(final int i) throws SQLException
    { try { return _res.getClob(i); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Array getArray(final int i) throws SQLException
    { try { return _res.getArray(i); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Object getObject(final String colName, final Map<String,Class<?>> map) throws SQLException
    { try { return _res.getObject(colName, map); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Ref getRef(final String colName) throws SQLException
    { try { return _res.getRef(colName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Blob getBlob(final String colName) throws SQLException
    { try { return _res.getBlob(colName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Clob getClob(final String colName) throws SQLException
    { try { return _res.getClob(colName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Array getArray(final String colName) throws SQLException
    { try { return _res.getArray(colName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Date getDate(final int columnIndex, final Calendar cal) throws SQLException
    { try { return _res.getDate(columnIndex, cal); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Date getDate(final String columnName, final Calendar cal) throws SQLException
    { try { return _res.getDate(columnName, cal); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Time getTime(final int columnIndex, final Calendar cal) throws SQLException
    { try { return _res.getTime(columnIndex, cal); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Time getTime(final String columnName, final Calendar cal) throws SQLException
    { try { return _res.getTime(columnName, cal); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Timestamp getTimestamp(final int columnIndex, final Calendar cal) throws SQLException
    { try { return _res.getTimestamp(columnIndex, cal); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public Timestamp getTimestamp(final String columnName, final Calendar cal) throws SQLException
    { try { return _res.getTimestamp(columnName, cal); } catch (final SQLException e) { handleException(e); return null; } }


    @Override
    public java.net.URL getURL(final int columnIndex) throws SQLException
    { try { return _res.getURL(columnIndex); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public java.net.URL getURL(final String columnName) throws SQLException
    { try { return _res.getURL(columnName); } catch (final SQLException e) { handleException(e); return null; } }

    @Override
    public void updateRef(final int columnIndex, final Ref x) throws SQLException
    { try { _res.updateRef(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateRef(final String columnName, final Ref x) throws SQLException
    { try { _res.updateRef(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateBlob(final int columnIndex, final Blob x) throws SQLException
    { try { _res.updateBlob(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateBlob(final String columnName, final Blob x) throws SQLException
    { try { _res.updateBlob(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateClob(final int columnIndex, final Clob x) throws SQLException
    { try { _res.updateClob(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateClob(final String columnName, final Clob x) throws SQLException
    { try { _res.updateClob(columnName, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateArray(final int columnIndex, final Array x) throws SQLException
    { try { _res.updateArray(columnIndex, x); } catch (final SQLException e) { handleException(e); } }

    @Override
    public void updateArray(final String columnName, final Array x) throws SQLException
    { try { _res.updateArray(columnName, x); } catch (final SQLException e) { handleException(e); } }


    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        } else if (iface.isAssignableFrom(_res.getClass())) {
            return true;
        } else {
            return _res.isWrapperFor(iface);
        }
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        } else if (iface.isAssignableFrom(_res.getClass())) {
            return iface.cast(_res);
        } else {
            return _res.unwrap(iface);
        }
    }

    @Override
    public RowId getRowId(final int columnIndex) throws SQLException {
        try {
            return _res.getRowId(columnIndex);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public RowId getRowId(final String columnLabel) throws SQLException {
        try {
            return _res.getRowId(columnLabel);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void updateRowId(final int columnIndex, final RowId value) throws SQLException {
        try {
            _res.updateRowId(columnIndex, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateRowId(final String columnLabel, final RowId value) throws SQLException {
        try {
            _res.updateRowId(columnLabel, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public int getHoldability() throws SQLException {
        try {
            return _res.getHoldability();
        }
        catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        try {
            return _res.isClosed();
        }
        catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void updateNString(final int columnIndex, final String value) throws SQLException {
        try {
            _res.updateNString(columnIndex, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNString(final String columnLabel, final String value) throws SQLException {
        try {
            _res.updateNString(columnLabel, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final int columnIndex, final NClob value) throws SQLException {
        try {
            _res.updateNClob(columnIndex, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final String columnLabel, final NClob value) throws SQLException {
        try {
            _res.updateNClob(columnLabel, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public NClob getNClob(final int columnIndex) throws SQLException {
        try {
            return _res.getNClob(columnIndex);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public NClob getNClob(final String columnLabel) throws SQLException {
        try {
            return _res.getNClob(columnLabel);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public SQLXML getSQLXML(final int columnIndex) throws SQLException {
        try {
            return _res.getSQLXML(columnIndex);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public SQLXML getSQLXML(final String columnLabel) throws SQLException {
        try {
            return _res.getSQLXML(columnLabel);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void updateSQLXML(final int columnIndex, final SQLXML value) throws SQLException {
        try {
            _res.updateSQLXML(columnIndex, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateSQLXML(final String columnLabel, final SQLXML value) throws SQLException {
        try {
            _res.updateSQLXML(columnLabel, value);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public String getNString(final int columnIndex) throws SQLException {
        try {
            return _res.getNString(columnIndex);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getNString(final String columnLabel) throws SQLException {
        try {
            return _res.getNString(columnLabel);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getNCharacterStream(final int columnIndex) throws SQLException {
        try {
            return _res.getNCharacterStream(columnIndex);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getNCharacterStream(final String columnLabel) throws SQLException {
        try {
            return _res.getNCharacterStream(columnLabel);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void updateNCharacterStream(final int columnIndex, final Reader reader, final long length) throws SQLException {
        try {
            _res.updateNCharacterStream(columnIndex, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNCharacterStream(final String columnLabel, final Reader reader, final long length) throws SQLException {
        try {
            _res.updateNCharacterStream(columnLabel, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream inputStream, final long length) throws SQLException {
        try {
            _res.updateAsciiStream(columnIndex, inputStream, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream inputStream, final long length) throws SQLException {
        try {
            _res.updateBinaryStream(columnIndex, inputStream, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader reader, final long length) throws SQLException {
        try {
            _res.updateCharacterStream(columnIndex, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream inputStream, final long length) throws SQLException {
        try {
            _res.updateAsciiStream(columnLabel, inputStream, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream inputStream, final long length) throws SQLException {
        try {
            _res.updateBinaryStream(columnLabel, inputStream, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader, final long length) throws SQLException {
        try {
            _res.updateCharacterStream(columnLabel, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBlob(final int columnIndex, final InputStream inputStream, final long length) throws SQLException {
        try {
            _res.updateBlob(columnIndex, inputStream, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBlob(final String columnLabel, final InputStream inputStream, final long length) throws SQLException {
        try {
            _res.updateBlob(columnLabel, inputStream, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
        try {
            _res.updateClob(columnIndex, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
        try {
            _res.updateClob(columnLabel, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
        try {
            _res.updateNClob(columnIndex, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
        try {
            _res.updateNClob(columnLabel, reader, length);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNCharacterStream(final int columnIndex, final Reader reader) throws SQLException {
        try {
            _res.updateNCharacterStream(columnIndex, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
        try {
            _res.updateNCharacterStream(columnLabel, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream inputStream) throws SQLException {
        try {
            _res.updateAsciiStream(columnIndex, inputStream);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream inputStream) throws SQLException {
        try {
            _res.updateBinaryStream(columnIndex, inputStream);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader reader) throws SQLException {
        try {
            _res.updateCharacterStream(columnIndex, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream inputStream) throws SQLException {
        try {
            _res.updateAsciiStream(columnLabel, inputStream);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream inputStream) throws SQLException {
        try {
            _res.updateBinaryStream(columnLabel, inputStream);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
        try {
            _res.updateCharacterStream(columnLabel, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBlob(final int columnIndex, final InputStream inputStream) throws SQLException {
        try {
            _res.updateBlob(columnIndex, inputStream);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBlob(final String columnLabel, final InputStream inputStream) throws SQLException {
        try {
            _res.updateBlob(columnLabel, inputStream);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateClob(final int columnIndex, final Reader reader) throws SQLException {
        try {
            _res.updateClob(columnIndex, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateClob(final String columnLabel, final Reader reader) throws SQLException {
        try {
            _res.updateClob(columnLabel, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final int columnIndex, final Reader reader) throws SQLException {
        try {
            _res.updateNClob(columnIndex, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final String columnLabel, final Reader reader) throws SQLException {
        try {
            _res.updateNClob(columnLabel, reader);
        }
        catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public <T> T getObject(final int columnIndex, final Class<T> type) throws SQLException {
        try {
            return _res.getObject(columnIndex, type);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public <T> T getObject(final String columnLabel, final Class<T> type)
            throws SQLException {
        try {
            return _res.getObject(columnLabel, type);
        }
        catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }
}

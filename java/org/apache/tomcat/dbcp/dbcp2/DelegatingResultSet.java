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
 * All of the methods from the {@link ResultSet} interface simply call the corresponding method on the "delegate"
 * provided in my constructor.
 * </p>
 * <p>
 * Extends AbandonedTrace to implement result set tracking and logging of code which created the ResultSet. Tracking the
 * ResultSet ensures that the Statement which created it can close any open ResultSet's on Statement close.
 * </p>
 *
 * @since 2.0
 */
public final class DelegatingResultSet extends AbandonedTrace implements ResultSet {

    /**
     * Wraps the given result set in a delegate.
     *
     * @param connection
     *            The Connection which created the ResultSet.
     * @param resultSet
     *            The ResultSet to wrap.
     * @return a new delegate.
     */
    public static ResultSet wrapResultSet(final Connection connection, final ResultSet resultSet) {
        if (null == resultSet) {
            return null;
        }
        return new DelegatingResultSet(connection, resultSet);
    }

    /**
     * Wraps the given result set in a delegate.
     *
     * @param statement
     *            The Statement which created the ResultSet.
     * @param resultSet
     *            The ResultSet to wrap.
     * @return a new delegate.
     */
    public static ResultSet wrapResultSet(final Statement statement, final ResultSet resultSet) {
        if (null == resultSet) {
            return null;
        }
        return new DelegatingResultSet(statement, resultSet);
    }

    /** My delegate. **/
    private final ResultSet resultSet;

    /** The Statement that created me, if any. **/
    private Statement statement;

    /** The Connection that created me, if any. **/
    private Connection connection;

    /**
     * Creates a wrapper for the ResultSet which traces this ResultSet to the Connection which created it (via, for
     * example DatabaseMetadata, and the code which created it.
     * <p>
     * Private to ensure all construction is {@link #wrapResultSet(Connection, ResultSet)}
     * </p>
     *
     * @param conn
     *            Connection which created this ResultSet
     * @param res
     *            ResultSet to wrap
     */
    private DelegatingResultSet(final Connection conn, final ResultSet res) {
        super((AbandonedTrace) conn);
        this.connection = conn;
        this.resultSet = res;
    }

    /**
     * Creates a wrapper for the ResultSet which traces this ResultSet to the Statement which created it and the code
     * which created it.
     * <p>
     * Private to ensure all construction is {@link #wrapResultSet(Statement, ResultSet)}
     * </p>
     *
     * @param statement
     *            The Statement which created the ResultSet.
     * @param resultSet
     *            The ResultSet to wrap.
     */
    private DelegatingResultSet(final Statement statement, final ResultSet resultSet) {
        super((AbandonedTrace) statement);
        this.statement = statement;
        this.resultSet = resultSet;
    }

    @Override
    public boolean absolute(final int row) throws SQLException {
        try {
            return resultSet.absolute(row);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void afterLast() throws SQLException {
        try {
            resultSet.afterLast();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void beforeFirst() throws SQLException {
        try {
            resultSet.beforeFirst();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        try {
            resultSet.cancelRowUpdates();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void clearWarnings() throws SQLException {
        try {
            resultSet.clearWarnings();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    /**
     * Wrapper for close of ResultSet which removes this result set from being traced then calls close on the original
     * ResultSet.
     */
    @Override
    public void close() throws SQLException {
        try {
            if (statement != null) {
                removeThisTrace(statement);
                statement = null;
            }
            if (connection != null) {
                removeThisTrace(connection);
                connection = null;
            }
            resultSet.close();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void deleteRow() throws SQLException {
        try {
            resultSet.deleteRow();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public int findColumn(final String columnName) throws SQLException {
        try {
            return resultSet.findColumn(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public boolean first() throws SQLException {
        try {
            return resultSet.first();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public Array getArray(final int i) throws SQLException {
        try {
            return resultSet.getArray(i);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Array getArray(final String colName) throws SQLException {
        try {
            return resultSet.getArray(colName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public InputStream getAsciiStream(final int columnIndex) throws SQLException {
        try {
            return resultSet.getAsciiStream(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public InputStream getAsciiStream(final String columnName) throws SQLException {
        try {
            return resultSet.getAsciiStream(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public BigDecimal getBigDecimal(final int columnIndex) throws SQLException {
        try {
            return resultSet.getBigDecimal(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    /** @deprecated Use {@link #getBigDecimal(int)} */
    @Deprecated
    @Override
    public BigDecimal getBigDecimal(final int columnIndex, final int scale) throws SQLException {
        try {
            return resultSet.getBigDecimal(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public BigDecimal getBigDecimal(final String columnName) throws SQLException {
        try {
            return resultSet.getBigDecimal(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    /** @deprecated Use {@link #getBigDecimal(String)} */
    @Deprecated
    @Override
    public BigDecimal getBigDecimal(final String columnName, final int scale) throws SQLException {
        try {
            return resultSet.getBigDecimal(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public InputStream getBinaryStream(final int columnIndex) throws SQLException {
        try {
            return resultSet.getBinaryStream(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public InputStream getBinaryStream(final String columnName) throws SQLException {
        try {
            return resultSet.getBinaryStream(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Blob getBlob(final int i) throws SQLException {
        try {
            return resultSet.getBlob(i);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Blob getBlob(final String colName) throws SQLException {
        try {
            return resultSet.getBlob(colName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public boolean getBoolean(final int columnIndex) throws SQLException {
        try {
            return resultSet.getBoolean(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean getBoolean(final String columnName) throws SQLException {
        try {
            return resultSet.getBoolean(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public byte getByte(final int columnIndex) throws SQLException {
        try {
            return resultSet.getByte(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public byte getByte(final String columnName) throws SQLException {
        try {
            return resultSet.getByte(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public byte[] getBytes(final int columnIndex) throws SQLException {
        try {
            return resultSet.getBytes(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public byte[] getBytes(final String columnName) throws SQLException {
        try {
            return resultSet.getBytes(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getCharacterStream(final int columnIndex) throws SQLException {
        try {
            return resultSet.getCharacterStream(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getCharacterStream(final String columnName) throws SQLException {
        try {
            return resultSet.getCharacterStream(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Clob getClob(final int i) throws SQLException {
        try {
            return resultSet.getClob(i);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Clob getClob(final String colName) throws SQLException {
        try {
            return resultSet.getClob(colName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public int getConcurrency() throws SQLException {
        try {
            return resultSet.getConcurrency();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public String getCursorName() throws SQLException {
        try {
            return resultSet.getCursorName();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Date getDate(final int columnIndex) throws SQLException {
        try {
            return resultSet.getDate(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Date getDate(final int columnIndex, final Calendar cal) throws SQLException {
        try {
            return resultSet.getDate(columnIndex, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Date getDate(final String columnName) throws SQLException {
        try {
            return resultSet.getDate(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Date getDate(final String columnName, final Calendar cal) throws SQLException {
        try {
            return resultSet.getDate(columnName, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Gets my delegate.
     *
     * @return my delegate.
     */
    public ResultSet getDelegate() {
        return resultSet;
    }

    @Override
    public double getDouble(final int columnIndex) throws SQLException {
        try {
            return resultSet.getDouble(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public double getDouble(final String columnName) throws SQLException {
        try {
            return resultSet.getDouble(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getFetchDirection() throws SQLException {
        try {
            return resultSet.getFetchDirection();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getFetchSize() throws SQLException {
        try {
            return resultSet.getFetchSize();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public float getFloat(final int columnIndex) throws SQLException {
        try {
            return resultSet.getFloat(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public float getFloat(final String columnName) throws SQLException {
        try {
            return resultSet.getFloat(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getHoldability() throws SQLException {
        try {
            return resultSet.getHoldability();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    /**
     * If my underlying {@link ResultSet} is not a {@code DelegatingResultSet}, returns it, otherwise recursively
     * invokes this method on my delegate.
     * <p>
     * Hence this method will return the first delegate that is not a {@code DelegatingResultSet}, or {@code null} when
     * no non-{@code DelegatingResultSet} delegate can be found by traversing this chain.
     * </p>
     * <p>
     * This method is useful when you may have nested {@code DelegatingResultSet}s, and you want to make sure to obtain
     * a "genuine" {@link ResultSet}.
     * </p>
     *
     * @return the innermost delegate.
     */
    public ResultSet getInnermostDelegate() {
        ResultSet r = resultSet;
        while (r != null && r instanceof DelegatingResultSet) {
            r = ((DelegatingResultSet) r).getDelegate();
            if (this == r) {
                return null;
            }
        }
        return r;
    }

    @Override
    public int getInt(final int columnIndex) throws SQLException {
        try {
            return resultSet.getInt(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public int getInt(final String columnName) throws SQLException {
        try {
            return resultSet.getInt(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public long getLong(final int columnIndex) throws SQLException {
        try {
            return resultSet.getLong(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public long getLong(final String columnName) throws SQLException {
        try {
            return resultSet.getLong(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        try {
            return resultSet.getMetaData();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getNCharacterStream(final int columnIndex) throws SQLException {
        try {
            return resultSet.getNCharacterStream(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Reader getNCharacterStream(final String columnLabel) throws SQLException {
        try {
            return resultSet.getNCharacterStream(columnLabel);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public NClob getNClob(final int columnIndex) throws SQLException {
        try {
            return resultSet.getNClob(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public NClob getNClob(final String columnLabel) throws SQLException {
        try {
            return resultSet.getNClob(columnLabel);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getNString(final int columnIndex) throws SQLException {
        try {
            return resultSet.getNString(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getNString(final String columnLabel) throws SQLException {
        try {
            return resultSet.getNString(columnLabel);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Object getObject(final int columnIndex) throws SQLException {
        try {
            return resultSet.getObject(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public <T> T getObject(final int columnIndex, final Class<T> type) throws SQLException {
        try {
            return Jdbc41Bridge.getObject(resultSet, columnIndex, type);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Object getObject(final int i, final Map<String, Class<?>> map) throws SQLException {
        try {
            return resultSet.getObject(i, map);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Object getObject(final String columnName) throws SQLException {
        try {
            return resultSet.getObject(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public <T> T getObject(final String columnLabel, final Class<T> type) throws SQLException {
        try {
            return Jdbc41Bridge.getObject(resultSet, columnLabel, type);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Object getObject(final String colName, final Map<String, Class<?>> map) throws SQLException {
        try {
            return resultSet.getObject(colName, map);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Ref getRef(final int i) throws SQLException {
        try {
            return resultSet.getRef(i);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Ref getRef(final String colName) throws SQLException {
        try {
            return resultSet.getRef(colName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public int getRow() throws SQLException {
        try {
            return resultSet.getRow();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public RowId getRowId(final int columnIndex) throws SQLException {
        try {
            return resultSet.getRowId(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public RowId getRowId(final String columnLabel) throws SQLException {
        try {
            return resultSet.getRowId(columnLabel);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public short getShort(final int columnIndex) throws SQLException {
        try {
            return resultSet.getShort(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public short getShort(final String columnName) throws SQLException {
        try {
            return resultSet.getShort(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public SQLXML getSQLXML(final int columnIndex) throws SQLException {
        try {
            return resultSet.getSQLXML(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public SQLXML getSQLXML(final String columnLabel) throws SQLException {
        try {
            return resultSet.getSQLXML(columnLabel);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Statement getStatement() throws SQLException {
        return statement;
    }

    @Override
    public String getString(final int columnIndex) throws SQLException {
        try {
            return resultSet.getString(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getString(final String columnName) throws SQLException {
        try {
            return resultSet.getString(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Time getTime(final int columnIndex) throws SQLException {
        try {
            return resultSet.getTime(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Time getTime(final int columnIndex, final Calendar cal) throws SQLException {
        try {
            return resultSet.getTime(columnIndex, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Time getTime(final String columnName) throws SQLException {
        try {
            return resultSet.getTime(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Time getTime(final String columnName, final Calendar cal) throws SQLException {
        try {
            return resultSet.getTime(columnName, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Timestamp getTimestamp(final int columnIndex) throws SQLException {
        try {
            return resultSet.getTimestamp(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Timestamp getTimestamp(final int columnIndex, final Calendar cal) throws SQLException {
        try {
            return resultSet.getTimestamp(columnIndex, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Timestamp getTimestamp(final String columnName) throws SQLException {
        try {
            return resultSet.getTimestamp(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Timestamp getTimestamp(final String columnName, final Calendar cal) throws SQLException {
        try {
            return resultSet.getTimestamp(columnName, cal);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public int getType() throws SQLException {
        try {
            return resultSet.getType();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    /** @deprecated Use {@link #getCharacterStream(int)} */
    @Deprecated
    @Override
    public InputStream getUnicodeStream(final int columnIndex) throws SQLException {
        try {
            return resultSet.getUnicodeStream(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    /** @deprecated Use {@link #getCharacterStream(String)} */
    @Deprecated
    @Override
    public InputStream getUnicodeStream(final String columnName) throws SQLException {
        try {
            return resultSet.getUnicodeStream(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public java.net.URL getURL(final int columnIndex) throws SQLException {
        try {
            return resultSet.getURL(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public java.net.URL getURL(final String columnName) throws SQLException {
        try {
            return resultSet.getURL(columnName);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        try {
            return resultSet.getWarnings();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    protected void handleException(final SQLException e) throws SQLException {
        if (statement != null && statement instanceof DelegatingStatement) {
            ((DelegatingStatement) statement).handleException(e);
        } else if (connection != null && connection instanceof DelegatingConnection) {
            ((DelegatingConnection<?>) connection).handleException(e);
        } else {
            throw e;
        }
    }

    @Override
    public void insertRow() throws SQLException {
        try {
            resultSet.insertRow();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        try {
            return resultSet.isAfterLast();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        try {
            return resultSet.isBeforeFirst();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        try {
            return resultSet.isClosed();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean isFirst() throws SQLException {
        try {
            return resultSet.isFirst();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean isLast() throws SQLException {
        try {
            return resultSet.isLast();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        } else if (iface.isAssignableFrom(resultSet.getClass())) {
            return true;
        } else {
            return resultSet.isWrapperFor(iface);
        }
    }

    @Override
    public boolean last() throws SQLException {
        try {
            return resultSet.last();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        try {
            resultSet.moveToCurrentRow();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        try {
            resultSet.moveToInsertRow();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean next() throws SQLException {
        try {
            return resultSet.next();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean previous() throws SQLException {
        try {
            return resultSet.previous();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void refreshRow() throws SQLException {
        try {
            resultSet.refreshRow();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean relative(final int rows) throws SQLException {
        try {
            return resultSet.relative(rows);
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean rowDeleted() throws SQLException {
        try {
            return resultSet.rowDeleted();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean rowInserted() throws SQLException {
        try {
            return resultSet.rowInserted();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public boolean rowUpdated() throws SQLException {
        try {
            return resultSet.rowUpdated();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    @Override
    public void setFetchDirection(final int direction) throws SQLException {
        try {
            resultSet.setFetchDirection(direction);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setFetchSize(final int rows) throws SQLException {
        try {
            resultSet.setFetchSize(rows);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public synchronized String toString() {
        return super.toString() + "[resultSet=" + resultSet + ", statement=" + statement + ", connection=" + connection + "]";
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        } else if (iface.isAssignableFrom(resultSet.getClass())) {
            return iface.cast(resultSet);
        } else {
            return resultSet.unwrap(iface);
        }
    }

    @Override
    public void updateArray(final int columnIndex, final Array x) throws SQLException {
        try {
            resultSet.updateArray(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateArray(final String columnName, final Array x) throws SQLException {
        try {
            resultSet.updateArray(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream inputStream) throws SQLException {
        try {
            resultSet.updateAsciiStream(columnIndex, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream x, final int length) throws SQLException {
        try {
            resultSet.updateAsciiStream(columnIndex, x, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateAsciiStream(final int columnIndex, final InputStream inputStream, final long length)
            throws SQLException {
        try {
            resultSet.updateAsciiStream(columnIndex, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream inputStream) throws SQLException {
        try {
            resultSet.updateAsciiStream(columnLabel, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateAsciiStream(final String columnName, final InputStream x, final int length) throws SQLException {
        try {
            resultSet.updateAsciiStream(columnName, x, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateAsciiStream(final String columnLabel, final InputStream inputStream, final long length)
            throws SQLException {
        try {
            resultSet.updateAsciiStream(columnLabel, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBigDecimal(final int columnIndex, final BigDecimal x) throws SQLException {
        try {
            resultSet.updateBigDecimal(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBigDecimal(final String columnName, final BigDecimal x) throws SQLException {
        try {
            resultSet.updateBigDecimal(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream inputStream) throws SQLException {
        try {
            resultSet.updateBinaryStream(columnIndex, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream x, final int length) throws SQLException {
        try {
            resultSet.updateBinaryStream(columnIndex, x, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBinaryStream(final int columnIndex, final InputStream inputStream, final long length)
            throws SQLException {
        try {
            resultSet.updateBinaryStream(columnIndex, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream inputStream) throws SQLException {
        try {
            resultSet.updateBinaryStream(columnLabel, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBinaryStream(final String columnName, final InputStream x, final int length) throws SQLException {
        try {
            resultSet.updateBinaryStream(columnName, x, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBinaryStream(final String columnLabel, final InputStream inputStream, final long length)
            throws SQLException {
        try {
            resultSet.updateBinaryStream(columnLabel, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBlob(final int columnIndex, final Blob x) throws SQLException {
        try {
            resultSet.updateBlob(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBlob(final int columnIndex, final InputStream inputStream) throws SQLException {
        try {
            resultSet.updateBlob(columnIndex, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBlob(final int columnIndex, final InputStream inputStream, final long length)
            throws SQLException {
        try {
            resultSet.updateBlob(columnIndex, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBlob(final String columnName, final Blob x) throws SQLException {
        try {
            resultSet.updateBlob(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBlob(final String columnLabel, final InputStream inputStream) throws SQLException {
        try {
            resultSet.updateBlob(columnLabel, inputStream);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBlob(final String columnLabel, final InputStream inputStream, final long length)
            throws SQLException {
        try {
            resultSet.updateBlob(columnLabel, inputStream, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBoolean(final int columnIndex, final boolean x) throws SQLException {
        try {
            resultSet.updateBoolean(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBoolean(final String columnName, final boolean x) throws SQLException {
        try {
            resultSet.updateBoolean(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateByte(final int columnIndex, final byte x) throws SQLException {
        try {
            resultSet.updateByte(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateByte(final String columnName, final byte x) throws SQLException {
        try {
            resultSet.updateByte(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBytes(final int columnIndex, final byte[] x) throws SQLException {
        try {
            resultSet.updateBytes(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateBytes(final String columnName, final byte[] x) throws SQLException {
        try {
            resultSet.updateBytes(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader reader) throws SQLException {
        try {
            resultSet.updateCharacterStream(columnIndex, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader x, final int length) throws SQLException {
        try {
            resultSet.updateCharacterStream(columnIndex, x, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateCharacterStream(final int columnIndex, final Reader reader, final long length)
            throws SQLException {
        try {
            resultSet.updateCharacterStream(columnIndex, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
        try {
            resultSet.updateCharacterStream(columnLabel, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateCharacterStream(final String columnName, final Reader reader, final int length)
            throws SQLException {
        try {
            resultSet.updateCharacterStream(columnName, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateCharacterStream(final String columnLabel, final Reader reader, final long length)
            throws SQLException {
        try {
            resultSet.updateCharacterStream(columnLabel, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateClob(final int columnIndex, final Clob x) throws SQLException {
        try {
            resultSet.updateClob(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateClob(final int columnIndex, final Reader reader) throws SQLException {
        try {
            resultSet.updateClob(columnIndex, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
        try {
            resultSet.updateClob(columnIndex, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateClob(final String columnName, final Clob x) throws SQLException {
        try {
            resultSet.updateClob(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateClob(final String columnLabel, final Reader reader) throws SQLException {
        try {
            resultSet.updateClob(columnLabel, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
        try {
            resultSet.updateClob(columnLabel, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateDate(final int columnIndex, final Date x) throws SQLException {
        try {
            resultSet.updateDate(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateDate(final String columnName, final Date x) throws SQLException {
        try {
            resultSet.updateDate(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateDouble(final int columnIndex, final double x) throws SQLException {
        try {
            resultSet.updateDouble(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateDouble(final String columnName, final double x) throws SQLException {
        try {
            resultSet.updateDouble(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateFloat(final int columnIndex, final float x) throws SQLException {
        try {
            resultSet.updateFloat(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateFloat(final String columnName, final float x) throws SQLException {
        try {
            resultSet.updateFloat(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateInt(final int columnIndex, final int x) throws SQLException {
        try {
            resultSet.updateInt(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateInt(final String columnName, final int x) throws SQLException {
        try {
            resultSet.updateInt(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateLong(final int columnIndex, final long x) throws SQLException {
        try {
            resultSet.updateLong(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateLong(final String columnName, final long x) throws SQLException {
        try {
            resultSet.updateLong(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNCharacterStream(final int columnIndex, final Reader reader) throws SQLException {
        try {
            resultSet.updateNCharacterStream(columnIndex, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNCharacterStream(final int columnIndex, final Reader reader, final long length)
            throws SQLException {
        try {
            resultSet.updateNCharacterStream(columnIndex, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNCharacterStream(final String columnLabel, final Reader reader) throws SQLException {
        try {
            resultSet.updateNCharacterStream(columnLabel, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNCharacterStream(final String columnLabel, final Reader reader, final long length)
            throws SQLException {
        try {
            resultSet.updateNCharacterStream(columnLabel, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final int columnIndex, final NClob value) throws SQLException {
        try {
            resultSet.updateNClob(columnIndex, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final int columnIndex, final Reader reader) throws SQLException {
        try {
            resultSet.updateNClob(columnIndex, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final int columnIndex, final Reader reader, final long length) throws SQLException {
        try {
            resultSet.updateNClob(columnIndex, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final String columnLabel, final NClob value) throws SQLException {
        try {
            resultSet.updateNClob(columnLabel, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final String columnLabel, final Reader reader) throws SQLException {
        try {
            resultSet.updateNClob(columnLabel, reader);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNClob(final String columnLabel, final Reader reader, final long length) throws SQLException {
        try {
            resultSet.updateNClob(columnLabel, reader, length);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNString(final int columnIndex, final String value) throws SQLException {
        try {
            resultSet.updateNString(columnIndex, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNString(final String columnLabel, final String value) throws SQLException {
        try {
            resultSet.updateNString(columnLabel, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNull(final int columnIndex) throws SQLException {
        try {
            resultSet.updateNull(columnIndex);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateNull(final String columnName) throws SQLException {
        try {
            resultSet.updateNull(columnName);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateObject(final int columnIndex, final Object x) throws SQLException {
        try {
            resultSet.updateObject(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateObject(final int columnIndex, final Object x, final int scale) throws SQLException {
        try {
            resultSet.updateObject(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateObject(final String columnName, final Object x) throws SQLException {
        try {
            resultSet.updateObject(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateObject(final String columnName, final Object x, final int scale) throws SQLException {
        try {
            resultSet.updateObject(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateRef(final int columnIndex, final Ref x) throws SQLException {
        try {
            resultSet.updateRef(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateRef(final String columnName, final Ref x) throws SQLException {
        try {
            resultSet.updateRef(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateRow() throws SQLException {
        try {
            resultSet.updateRow();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateRowId(final int columnIndex, final RowId value) throws SQLException {
        try {
            resultSet.updateRowId(columnIndex, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateRowId(final String columnLabel, final RowId value) throws SQLException {
        try {
            resultSet.updateRowId(columnLabel, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateShort(final int columnIndex, final short x) throws SQLException {
        try {
            resultSet.updateShort(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateShort(final String columnName, final short x) throws SQLException {
        try {
            resultSet.updateShort(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateSQLXML(final int columnIndex, final SQLXML value) throws SQLException {
        try {
            resultSet.updateSQLXML(columnIndex, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateSQLXML(final String columnLabel, final SQLXML value) throws SQLException {
        try {
            resultSet.updateSQLXML(columnLabel, value);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateString(final int columnIndex, final String x) throws SQLException {
        try {
            resultSet.updateString(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateString(final String columnName, final String x) throws SQLException {
        try {
            resultSet.updateString(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateTime(final int columnIndex, final Time x) throws SQLException {
        try {
            resultSet.updateTime(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateTime(final String columnName, final Time x) throws SQLException {
        try {
            resultSet.updateTime(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateTimestamp(final int columnIndex, final Timestamp x) throws SQLException {
        try {
            resultSet.updateTimestamp(columnIndex, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void updateTimestamp(final String columnName, final Timestamp x) throws SQLException {
        try {
            resultSet.updateTimestamp(columnName, x);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public boolean wasNull() throws SQLException {
        try {
            return resultSet.wasNull();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }
}

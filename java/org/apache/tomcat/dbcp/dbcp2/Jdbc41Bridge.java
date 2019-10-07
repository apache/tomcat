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
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.sql.CommonDataSource;

/**
 * Defines bridge methods to JDBC 4.1 (Java 7) methods to allow call sites to operate safely (without
 * {@link AbstractMethodError}) when using a JDBC driver written for JDBC 4.0 (Java 6).
 *
 * @since 2.6.0
 */
public class Jdbc41Bridge {

    /**
     * Delegates to {@link Connection#abort(Executor)} without throwing an {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link Connection#abort(Executor)}, then call {@link Connection#close()}.
     * </p>
     *
     * @param connection
     *            the receiver
     * @param executor
     *            See {@link Connection#abort(Executor)}.
     * @throws SQLException
     *             See {@link Connection#abort(Executor)}.
     * @see Connection#abort(Executor)
     */
    public static void abort(final Connection connection, final Executor executor) throws SQLException {
        try {
            connection.abort(executor);
        } catch (final AbstractMethodError e) {
            connection.close();
        }
    }

    /**
     * Delegates to {@link DatabaseMetaData#generatedKeyAlwaysReturned()} without throwing a
     * {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link DatabaseMetaData#generatedKeyAlwaysReturned()}, then return false.
     * </p>
     *
     * @param databaseMetaData
     *            See {@link DatabaseMetaData#generatedKeyAlwaysReturned()}
     * @return See {@link DatabaseMetaData#generatedKeyAlwaysReturned()}
     * @throws SQLException
     *             See {@link DatabaseMetaData#generatedKeyAlwaysReturned()}
     * @see DatabaseMetaData#generatedKeyAlwaysReturned()
     */
    public static boolean generatedKeyAlwaysReturned(final DatabaseMetaData databaseMetaData) throws SQLException {
        try {
            return databaseMetaData.generatedKeyAlwaysReturned();
        } catch (final AbstractMethodError e) {
            // do nothing
            return false;
        }
    }

    /**
     * Delegates to {@link Connection#getNetworkTimeout()} without throwing an {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link Connection#getNetworkTimeout()}, then return 0.
     * </p>
     *
     * @param connection
     *            the receiver
     * @return See {@link Connection#getNetworkTimeout()}
     * @throws SQLException
     *             See {@link Connection#getNetworkTimeout()}
     * @see Connection#getNetworkTimeout()
     */
    public static int getNetworkTimeout(final Connection connection) throws SQLException {
        try {
            return connection.getNetworkTimeout();
        } catch (final AbstractMethodError e) {
            return 0;
        }
    }

    /**
     * Delegates to {@link ResultSet#getObject(int, Class)} without throwing an {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link ResultSet#getObject(int, Class)}, then return 0.
     * </p>
     *
     * @param <T>
     *            See {@link ResultSet#getObject(int, Class)}
     * @param resultSet
     *            See {@link ResultSet#getObject(int, Class)}
     * @param columnIndex
     *            See {@link ResultSet#getObject(int, Class)}
     * @param type
     *            See {@link ResultSet#getObject(int, Class)}
     * @return See {@link ResultSet#getObject(int, Class)}
     * @throws SQLException
     *             See {@link ResultSet#getObject(int, Class)}
     * @see ResultSet#getObject(int, Class)
     */
    @SuppressWarnings("unchecked")
    public static <T> T getObject(final ResultSet resultSet, final int columnIndex, final Class<T> type)
            throws SQLException {
        try {
            return resultSet.getObject(columnIndex, type);
        } catch (final AbstractMethodError e) {
            if (type == String.class) {
                return (T) resultSet.getString(columnIndex);
            }
            // Numbers
            if (type == Integer.class) {
                return (T) Integer.valueOf(resultSet.getInt(columnIndex));
            }
            if (type == Long.class) {
                return (T) Long.valueOf(resultSet.getLong(columnIndex));
            }
            if (type == Double.class) {
                return (T) Double.valueOf(resultSet.getDouble(columnIndex));
            }
            if (type == Float.class) {
                return (T) Float.valueOf(resultSet.getFloat(columnIndex));
            }
            if (type == Short.class) {
                return (T) Short.valueOf(resultSet.getShort(columnIndex));
            }
            if (type == BigDecimal.class) {
                return (T) resultSet.getBigDecimal(columnIndex);
            }
            if (type == Byte.class) {
                return (T) Byte.valueOf(resultSet.getByte(columnIndex));
            }
            // Dates
            if (type == Date.class) {
                return (T) resultSet.getDate(columnIndex);
            }
            if (type == Time.class) {
                return (T) resultSet.getTime(columnIndex);
            }
            if (type == Timestamp.class) {
                return (T) resultSet.getTimestamp(columnIndex);
            }
            // Streams
            if (type == InputStream.class) {
                return (T) resultSet.getBinaryStream(columnIndex);
            }
            if (type == Reader.class) {
                return (T) resultSet.getCharacterStream(columnIndex);
            }
            // Other
            if (type == Object.class) {
                return (T) resultSet.getObject(columnIndex);
            }
            if (type == Boolean.class) {
                return (T) Boolean.valueOf(resultSet.getBoolean(columnIndex));
            }
            if (type == Array.class) {
                return (T) resultSet.getArray(columnIndex);
            }
            if (type == Blob.class) {
                return (T) resultSet.getBlob(columnIndex);
            }
            if (type == Clob.class) {
                return (T) resultSet.getClob(columnIndex);
            }
            if (type == Ref.class) {
                return (T) resultSet.getRef(columnIndex);
            }
            if (type == RowId.class) {
                return (T) resultSet.getRowId(columnIndex);
            }
            if (type == SQLXML.class) {
                return (T) resultSet.getSQLXML(columnIndex);
            }
            if (type == URL.class) {
                return (T) resultSet.getURL(columnIndex);
            }
            throw new SQLFeatureNotSupportedException(
                    String.format("resultSet=%s, columnIndex=%,d, type=%s", resultSet, Integer.valueOf(columnIndex), type));
        }
    }

    /**
     * Delegates to {@link ResultSet#getObject(String, Class)} without throwing an {@link AbstractMethodError}.
     *
     * @param <T>
     *            See {@link ResultSet#getObject(String, Class)}
     * @param resultSet
     *            See {@link ResultSet#getObject(String, Class)}
     * @param columnLabel
     *            See {@link ResultSet#getObject(String, Class)}
     * @param type
     *            See {@link ResultSet#getObject(String, Class)}
     * @return See {@link ResultSet#getObject(String, Class)}
     * @throws SQLException
     *             See {@link ResultSet#getObject(String, Class)}
     * @see ResultSet#getObject(int, Class)
     */
    @SuppressWarnings("unchecked")
    public static <T> T getObject(final ResultSet resultSet, final String columnLabel, final Class<T> type)
            throws SQLException {
        try {
            return resultSet.getObject(columnLabel, type);
        } catch (final AbstractMethodError e) {
            // Numbers
            if (type == Integer.class) {
                return (T) Integer.valueOf(resultSet.getInt(columnLabel));
            }
            if (type == Long.class) {
                return (T) Long.valueOf(resultSet.getLong(columnLabel));
            }
            if (type == Double.class) {
                return (T) Double.valueOf(resultSet.getDouble(columnLabel));
            }
            if (type == Float.class) {
                return (T) Float.valueOf(resultSet.getFloat(columnLabel));
            }
            if (type == Short.class) {
                return (T) Short.valueOf(resultSet.getShort(columnLabel));
            }
            if (type == BigDecimal.class) {
                return (T) resultSet.getBigDecimal(columnLabel);
            }
            if (type == Byte.class) {
                return (T) Byte.valueOf(resultSet.getByte(columnLabel));
            }
            // Dates
            if (type == Date.class) {
                return (T) resultSet.getDate(columnLabel);
            }
            if (type == Time.class) {
                return (T) resultSet.getTime(columnLabel);
            }
            if (type == Timestamp.class) {
                return (T) resultSet.getTimestamp(columnLabel);
            }
            // Streams
            if (type == InputStream.class) {
                return (T) resultSet.getBinaryStream(columnLabel);
            }
            if (type == Reader.class) {
                return (T) resultSet.getCharacterStream(columnLabel);
            }
            // Other
            if (type == Object.class) {
                return (T) resultSet.getObject(columnLabel);
            }
            if (type == Boolean.class) {
                return (T) Boolean.valueOf(resultSet.getBoolean(columnLabel));
            }
            if (type == Array.class) {
                return (T) resultSet.getArray(columnLabel);
            }
            if (type == Blob.class) {
                return (T) resultSet.getBlob(columnLabel);
            }
            if (type == Clob.class) {
                return (T) resultSet.getClob(columnLabel);
            }
            if (type == Ref.class) {
                return (T) resultSet.getRef(columnLabel);
            }
            if (type == RowId.class) {
                return (T) resultSet.getRowId(columnLabel);
            }
            if (type == SQLXML.class) {
                return (T) resultSet.getSQLXML(columnLabel);
            }
            if (type == URL.class) {
                return (T) resultSet.getURL(columnLabel);
            }
            throw new SQLFeatureNotSupportedException(
                    String.format("resultSet=%s, columnLabel=%s, type=%s", resultSet, columnLabel, type));
        }
    }

    /**
     * Delegates to {@link DatabaseMetaData#getPseudoColumns(String, String, String, String)} without throwing a
     * {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link DatabaseMetaData#getPseudoColumns(String, String, String, String)},
     * then return null.
     * </p>
     *
     * @param databaseMetaData
     *            the receiver
     * @param catalog
     *            See {@link DatabaseMetaData#getPseudoColumns(String, String, String, String)}
     * @param schemaPattern
     *            See {@link DatabaseMetaData#getPseudoColumns(String, String, String, String)}
     * @param tableNamePattern
     *            See {@link DatabaseMetaData#getPseudoColumns(String, String, String, String)}
     * @param columnNamePattern
     *            See {@link DatabaseMetaData#getPseudoColumns(String, String, String, String)}
     * @return See {@link DatabaseMetaData#getPseudoColumns(String, String, String, String)}
     * @throws SQLException
     *             See {@link DatabaseMetaData#getPseudoColumns(String, String, String, String)}
     * @see DatabaseMetaData#getPseudoColumns(String, String, String, String)
     */
    public static ResultSet getPseudoColumns(final DatabaseMetaData databaseMetaData, final String catalog,
            final String schemaPattern, final String tableNamePattern, final String columnNamePattern)
            throws SQLException {
        try {
            return databaseMetaData.getPseudoColumns(catalog, schemaPattern, tableNamePattern, columnNamePattern);
        } catch (final AbstractMethodError e) {
            // do nothing
            return null;
        }
    }

    /**
     * Delegates to {@link Connection#getSchema()} without throwing an {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link Connection#getSchema()}, then return null.
     * </p>
     *
     * @param connection
     *            the receiver
     * @return null for a JDBC 4 driver or a value per {@link Connection#getSchema()}.
     * @throws SQLException
     *             See {@link Connection#getSchema()}.
     * @see Connection#getSchema()
     */
    public static String getSchema(final Connection connection) throws SQLException {
        try {
            return connection.getSchema();
        } catch (final AbstractMethodError e) {
            // do nothing
            return null;
        }
    }

    /**
     * Delegates to {@link Connection#setNetworkTimeout(Executor, int)} without throwing an {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link Connection#setNetworkTimeout(Executor, int)}, then do nothing.
     * </p>
     *
     * @param connection
     *            the receiver
     * @param executor
     *            See {@link Connection#setNetworkTimeout(Executor, int)}
     * @param milliseconds
     *            {@link Connection#setNetworkTimeout(Executor, int)}
     * @throws SQLException
     *             {@link Connection#setNetworkTimeout(Executor, int)}
     * @see Connection#setNetworkTimeout(Executor, int)
     */
    public static void setNetworkTimeout(final Connection connection, final Executor executor, final int milliseconds)
            throws SQLException {
        try {
            connection.setNetworkTimeout(executor, milliseconds);
        } catch (final AbstractMethodError e) {
            // do nothing
        }
    }

    /**
     * Delegates to {@link Connection#setSchema(String)} without throwing an {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link Connection#setSchema(String)}, then do nothing.
     * </p>
     *
     * @param connection
     *            the receiver
     * @param schema
     *            See {@link Connection#setSchema(String)}.
     * @throws SQLException
     *             See {@link Connection#setSchema(String)}.
     * @see Connection#setSchema(String)
     */
    public static void setSchema(final Connection connection, final String schema) throws SQLException {
        try {
            connection.setSchema(schema);
        } catch (final AbstractMethodError e) {
            // do nothing
        }
    }

    /**
     * Delegates to {@link Statement#closeOnCompletion()} without throwing an {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link Statement#closeOnCompletion()}, then just check that the connection
     * is closed to then throw an SQLException.
     * </p>
     *
     * @param statement
     *            See {@link Statement#closeOnCompletion()}
     * @throws SQLException
     *             See {@link Statement#closeOnCompletion()}
     * @see Statement#closeOnCompletion()
     */
    public static void closeOnCompletion(final Statement statement) throws SQLException {
        try {
            statement.closeOnCompletion();
        } catch (final AbstractMethodError e) {
            if (statement.isClosed()) {
                throw new SQLException("Statement closed");
            }
        }
    }

    /**
     * Delegates to {@link Statement#isCloseOnCompletion()} without throwing an {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link Statement#isCloseOnCompletion()}, then just check that the
     * connection is closed to then throw an SQLException.
     * </p>
     *
     * @param statement
     *            See {@link Statement#isCloseOnCompletion()}
     * @return See {@link Statement#isCloseOnCompletion()}
     * @throws SQLException
     *             See {@link Statement#isCloseOnCompletion()}
     * @see Statement#closeOnCompletion()
     */
    public static boolean isCloseOnCompletion(final Statement statement) throws SQLException {
        try {
            return statement.isCloseOnCompletion();
        } catch (final AbstractMethodError e) {
            if (statement.isClosed()) {
                throw new SQLException("Statement closed");
            }
            return false;
        }
    }

    /**
     * Delegates to {@link CommonDataSource#getParentLogger()} without throwing an {@link AbstractMethodError}.
     * <p>
     * If the JDBC driver does not implement {@link CommonDataSource#getParentLogger()}, then return null.
     * </p>
     *
     * @param commonDataSource
     *            See {@link CommonDataSource#getParentLogger()}
     * @return See {@link CommonDataSource#getParentLogger()}
     * @throws SQLFeatureNotSupportedException
     *             See {@link CommonDataSource#getParentLogger()}
     */
    public static Logger getParentLogger(final CommonDataSource commonDataSource) throws SQLFeatureNotSupportedException {
        try {
            return commonDataSource.getParentLogger();
        } catch (final AbstractMethodError e) {
            throw new SQLFeatureNotSupportedException("javax.sql.CommonDataSource#getParentLogger()");
        }
    }

}

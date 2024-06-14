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

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.ClientInfoStatus;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.apache.tomcat.dbcp.dbcp2.managed.ManagedConnection;

/**
 * A base delegating implementation of {@link Connection}.
 * <p>
 * All of the methods from the {@link Connection} interface simply check to see that the {@link Connection} is active,
 * and call the corresponding method on the "delegate" provided in my constructor.
 * </p>
 * <p>
 * Extends AbandonedTrace to implement Connection tracking and logging of code which created the Connection. Tracking
 * the Connection ensures that the AbandonedObjectPool can close this connection and recycle it if its pool of
 * connections is nearing exhaustion and this connection's last usage is older than the removeAbandonedTimeout.
 * </p>
 *
 * @param <C>
 *            the Connection type
 *
 * @since 2.0
 */
public class DelegatingConnection<C extends Connection> extends AbandonedTrace implements Connection {

    private static final Map<String, ClientInfoStatus> EMPTY_FAILED_PROPERTIES = Collections
            .<String, ClientInfoStatus>emptyMap();

    /** My delegate {@link Connection}. */
    private volatile C connection;

    private volatile boolean closed;

    private boolean cacheState = true;
    private Boolean cachedAutoCommit;
    private Boolean cachedReadOnly;
    private String cachedCatalog;
    private String cachedSchema;
    private Duration defaultQueryTimeoutDuration;

    /**
     * Creates a wrapper for the Connection which traces this Connection in the AbandonedObjectPool.
     *
     * @param connection the {@link Connection} to delegate all calls to, may be null (see {@link ManagedConnection}).
     */
    public DelegatingConnection(final C connection) {
        this.connection = connection;
    }

    @Override
    public void abort(final Executor executor) throws SQLException {
        try {
            Jdbc41Bridge.abort(connection, executor);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    protected void activate() {
        closed = false;
        setLastUsed();
        if (connection instanceof DelegatingConnection) {
            ((DelegatingConnection<?>) connection).activate();
        }
    }

    protected void checkOpen() throws SQLException {
        if (closed) {
            if (null != connection) {
                String label;
                try {
                    label = connection.toString();
                } catch (final Exception e) {
                    // leave label empty
                    label = "";
                }
                throw new SQLException("Connection " + label + " is closed.");
            }
            throw new SQLException("Connection is null.");
        }
    }

    /**
     * Clears the cached state. Call when you know that the underlying connection may have been accessed
     * directly.
     */
    public void clearCachedState() {
        cachedAutoCommit = null;
        cachedReadOnly = null;
        cachedSchema = null;
        cachedCatalog = null;
        if (connection instanceof DelegatingConnection) {
            ((DelegatingConnection<?>) connection).clearCachedState();
        }
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkOpen();
        try {
            connection.clearWarnings();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    /**
     * Closes the underlying connection, and close any Statements that were not explicitly closed. Sub-classes that
     * override this method must:
     * <ol>
     * <li>Call {@link #passivate()}</li>
     * <li>Call close (or the equivalent appropriate action) on the wrapped connection</li>
     * <li>Set {@code closed} to {@code false}</li>
     * </ol>
     */
    @Override
    public void close() throws SQLException {
        if (!closed) {
            closeInternal();
        }
    }

    protected final void closeInternal() throws SQLException {
        try {
            passivate();
        } finally {
            if (connection != null) {
                boolean connectionIsClosed;
                try {
                    connectionIsClosed = connection.isClosed();
                } catch (final SQLException e) {
                    // not sure what the state is, so assume the connection is open.
                    connectionIsClosed = false;
                }
                try {
                    // DBCP-512: Avoid exceptions when closing a connection in multi-threaded use case.
                    // Avoid closing again, which should be a no-op, but some drivers like H2 throw an exception when
                    // closing from multiple threads.
                    if (!connectionIsClosed) {
                        connection.close();
                    }
                } finally {
                    closed = true;
                }
            } else {
                closed = true;
            }
        }
    }

    @Override
    public void commit() throws SQLException {
        checkOpen();
        try {
            connection.commit();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
        checkOpen();
        try {
            return connection.createArrayOf(typeName, elements);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Blob createBlob() throws SQLException {
        checkOpen();
        try {
            return connection.createBlob();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Clob createClob() throws SQLException {
        checkOpen();
        try {
            return connection.createClob();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public NClob createNClob() throws SQLException {
        checkOpen();
        try {
            return connection.createNClob();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkOpen();
        try {
            return connection.createSQLXML();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingStatement(this, connection.createStatement()));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency) throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingStatement(this, connection.createStatement(resultSetType, resultSetConcurrency)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency,
        final int resultSetHoldability) throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingStatement(this,
                connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Struct createStruct(final String typeName, final Object[] attributes) throws SQLException {
        checkOpen();
        try {
            return connection.createStruct(typeName, attributes);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkOpen();
        if (cacheState && cachedAutoCommit != null) {
            return cachedAutoCommit.booleanValue();
        }
        try {
            cachedAutoCommit = Boolean.valueOf(connection.getAutoCommit());
            return cachedAutoCommit.booleanValue();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    /**
     * Returns the state caching flag.
     *
     * @return the state caching flag
     */
    public boolean getCacheState() {
        return cacheState;
    }

    @Override
    public String getCatalog() throws SQLException {
        checkOpen();
        if (cacheState && cachedCatalog != null) {
            return cachedCatalog;
        }
        try {
            cachedCatalog = connection.getCatalog();
            return cachedCatalog;
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        checkOpen();
        try {
            return connection.getClientInfo();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public String getClientInfo(final String name) throws SQLException {
        checkOpen();
        try {
            return connection.getClientInfo(name);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Gets the default query timeout that will be used for {@link Statement}s created from this connection.
     * {@code null} means that the driver default will be used.
     *
     * @return query timeout limit in seconds; zero means there is no limit.
     * @deprecated Use {@link #getDefaultQueryTimeoutDuration()}.
     */
    @Deprecated
    public Integer getDefaultQueryTimeout() {
        return defaultQueryTimeoutDuration == null ? null : Integer.valueOf((int) defaultQueryTimeoutDuration.getSeconds());
    }

    /**
     * Gets the default query timeout that will be used for {@link Statement}s created from this connection.
     * {@code null} means that the driver default will be used.
     *
     * @return query timeout limit; zero means there is no limit.
     * @since 2.10.0
     */
    public Duration getDefaultQueryTimeoutDuration() {
        return defaultQueryTimeoutDuration;
    }

    /**
     * Returns my underlying {@link Connection}.
     *
     * @return my underlying {@link Connection}.
     */
    public C getDelegate() {
        return getDelegateInternal();
    }

    /**
     * Gets the delegate connection.
     *
     * @return the delegate connection.
     */
    protected final C getDelegateInternal() {
        return connection;
    }

    @Override
    public int getHoldability() throws SQLException {
        checkOpen();
        try {
            return connection.getHoldability();
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    /**
     * If my underlying {@link Connection} is not a {@code DelegatingConnection}, returns it, otherwise recursively
     * invokes this method on my delegate.
     * <p>
     * Hence this method will return the first delegate that is not a {@code DelegatingConnection}, or {@code null} when
     * no non-{@code DelegatingConnection} delegate can be found by traversing this chain.
     * </p>
     * <p>
     * This method is useful when you may have nested {@code DelegatingConnection}s, and you want to make sure to obtain
     * a "genuine" {@link Connection}.
     * </p>
     *
     * @return innermost delegate.
     */
    public Connection getInnermostDelegate() {
        return getInnermostDelegateInternal();
    }

    /**
     * Although this method is public, it is part of the internal API and should not be used by clients. The signature
     * of this method may change at any time including in ways that break backwards compatibility.
     *
     * @return innermost delegate.
     */
    public final Connection getInnermostDelegateInternal() {
        Connection conn = connection;
        while (conn instanceof DelegatingConnection) {
            conn = ((DelegatingConnection<?>) conn).getDelegateInternal();
            if (this == conn) {
                return null;
            }
        }
        return conn;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkOpen();
        try {
            return new DelegatingDatabaseMetaData(this, connection.getMetaData());
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        checkOpen();
        try {
            return Jdbc41Bridge.getNetworkTimeout(connection);
        } catch (final SQLException e) {
            handleException(e);
            return 0;
        }
    }

    @Override
    public String getSchema() throws SQLException {
        checkOpen();
        if (cacheState && cachedSchema != null) {
            return cachedSchema;
        }
        try {
            cachedSchema = Jdbc41Bridge.getSchema(connection);
            return cachedSchema;
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkOpen();
        try {
            return connection.getTransactionIsolation();
        } catch (final SQLException e) {
            handleException(e);
            return -1;
        }
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkOpen();
        try {
            return connection.getTypeMap();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkOpen();
        try {
            return connection.getWarnings();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Handles the given exception by throwing it.
     *
     * @param e the exception to throw.
     * @throws SQLException the exception to throw.
     */
    protected void handleException(final SQLException e) throws SQLException {
        throw e;
    }

    /**
     * Handles the given {@code SQLException}.
     *
     * @param <T> The throwable type.
     * @param e   The SQLException
     * @return the given {@code SQLException}
     * @since 2.7.0
     */
    protected <T extends Throwable> T handleExceptionNoThrow(final T e) {
        return e;
    }

    /**
     * Initializes the given statement with this connection's settings.
     *
     * @param <T> The DelegatingStatement type.
     * @param delegatingStatement The DelegatingStatement to initialize.
     * @return The given DelegatingStatement.
     * @throws SQLException if a database access error occurs, this method is called on a closed Statement.
     */
    private <T extends DelegatingStatement> T init(final T delegatingStatement) throws SQLException {
        if (defaultQueryTimeoutDuration != null && defaultQueryTimeoutDuration.getSeconds() != delegatingStatement.getQueryTimeout()) {
            delegatingStatement.setQueryTimeout((int) defaultQueryTimeoutDuration.getSeconds());
        }
        return delegatingStatement;
    }

    /**
     * Compares innermost delegate to the given connection.
     *
     * @param c
     *            connection to compare innermost delegate with
     * @return true if innermost delegate equals {@code c}
     */
    public boolean innermostDelegateEquals(final Connection c) {
        final Connection innerCon = getInnermostDelegateInternal();
        if (innerCon == null) {
            return c == null;
        }
        return innerCon.equals(c);
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed || connection == null || connection.isClosed();
    }

    protected boolean isClosedInternal() {
        return closed;
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        checkOpen();
        if (cacheState && cachedReadOnly != null) {
            return cachedReadOnly.booleanValue();
        }
        try {
            cachedReadOnly = Boolean.valueOf(connection.isReadOnly());
            return cachedReadOnly.booleanValue();
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    /**
     * Tests if the connection has not been closed and is still valid.
     *
     * @param timeout The duration to wait for the database operation used to validate the connection to complete.
     * @return See {@link Connection#isValid(int)}.
     * @throws SQLException See {@link Connection#isValid(int)}.
     * @see Connection#isValid(int)
     * @since 2.10.0
     */
    public boolean isValid(final Duration timeout) throws SQLException {
        if (isClosed()) {
            return false;
        }
        try {
            return connection.isValid((int) timeout.getSeconds());
        } catch (final SQLException e) {
            handleException(e);
            return false;
        }
    }

    /**
     * @deprecated Use {@link #isValid(Duration)}.
     */
    @Override
    @Deprecated
    public boolean isValid(final int timeoutSeconds) throws SQLException {
        return isValid(Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return true;
        }
        if (iface.isAssignableFrom(connection.getClass())) {
            return true;
        }
        return connection.isWrapperFor(iface);
    }

    @Override
    public String nativeSQL(final String sql) throws SQLException {
        checkOpen();
        try {
            return connection.nativeSQL(sql);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    protected void passivate() throws SQLException {
        // The JDBC specification requires that a Connection close any open
        // Statements when it is closed.
        // DBCP-288. Not all the traced objects will be statements
        final List<AbandonedTrace> traceList = getTrace();
        if (!Utils.isEmpty(traceList)) {
            final List<Exception> thrownList = new ArrayList<>();
            traceList.forEach(trace -> trace.close(thrownList::add));
            clearTrace();
            if (!thrownList.isEmpty()) {
                throw new SQLExceptionList(thrownList);
            }
        }
        setLastUsed(Instant.EPOCH);
    }

    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingCallableStatement(this, connection.prepareCall(sql)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency)
        throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingCallableStatement(this,
                connection.prepareCall(sql, resultSetType, resultSetConcurrency)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency,
        final int resultSetHoldability) throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingCallableStatement(this,
                connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingPreparedStatement(this, connection.prepareStatement(sql)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingPreparedStatement(this, connection.prepareStatement(sql, autoGeneratedKeys)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency)
        throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingPreparedStatement(this,
                connection.prepareStatement(sql, resultSetType, resultSetConcurrency)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency,
        final int resultSetHoldability) throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingPreparedStatement(this,
                connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingPreparedStatement(this, connection.prepareStatement(sql, columnIndexes)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        checkOpen();
        try {
            return init(new DelegatingPreparedStatement(this, connection.prepareStatement(sql, columnNames)));
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
        checkOpen();
        try {
            connection.releaseSavepoint(savepoint);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void rollback() throws SQLException {
        checkOpen();
        try {
            connection.rollback();
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void rollback(final Savepoint savepoint) throws SQLException {
        checkOpen();
        try {
            connection.rollback(savepoint);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        checkOpen();
        try {
            connection.setAutoCommit(autoCommit);
            if (cacheState) {
                cachedAutoCommit = Boolean.valueOf(connection.getAutoCommit());
            }
        } catch (final SQLException e) {
            cachedAutoCommit = null;
            handleException(e);
        }
    }

    /**
     * Sets the state caching flag.
     *
     * @param cacheState
     *            The new value for the state caching flag
     */
    public void setCacheState(final boolean cacheState) {
        this.cacheState = cacheState;
    }

    @Override
    public void setCatalog(final String catalog) throws SQLException {
        checkOpen();
        try {
            connection.setCatalog(catalog);
            if (cacheState) {
                cachedCatalog = connection.getCatalog();
            }
        } catch (final SQLException e) {
            cachedCatalog = null;
            handleException(e);
        }
    }

    @Override
    public void setClientInfo(final Properties properties) throws SQLClientInfoException {
        try {
            checkOpen();
            connection.setClientInfo(properties);
        } catch (final SQLClientInfoException e) {
            throw e;
        } catch (final SQLException e) {
            throw new SQLClientInfoException("Connection is closed.", EMPTY_FAILED_PROPERTIES, e);
        }
    }

    @Override
    public void setClientInfo(final String name, final String value) throws SQLClientInfoException {
        try {
            checkOpen();
            connection.setClientInfo(name, value);
        } catch (final SQLClientInfoException e) {
            throw e;
        } catch (final SQLException e) {
            throw new SQLClientInfoException("Connection is closed.", EMPTY_FAILED_PROPERTIES, e);
        }
    }

    protected void setClosedInternal(final boolean closed) {
        this.closed = closed;
    }

    /**
     * Sets the default query timeout that will be used for {@link Statement}s created from this connection.
     * {@code null} means that the driver default will be used.
     *
     * @param defaultQueryTimeoutDuration
     *            the new query timeout limit Duration; zero means there is no limit.
     * @since 2.10.0
     */
    public void setDefaultQueryTimeout(final Duration defaultQueryTimeoutDuration) {
        this.defaultQueryTimeoutDuration = defaultQueryTimeoutDuration;
    }

    /**
     * Sets the default query timeout that will be used for {@link Statement}s created from this connection.
     * {@code null} means that the driver default will be used.
     *
     * @param defaultQueryTimeoutSeconds
     *            the new query timeout limit in seconds; zero means there is no limit.
     * @deprecated Use {@link #setDefaultQueryTimeout(Duration)}.
     */
    @Deprecated
    public void setDefaultQueryTimeout(final Integer defaultQueryTimeoutSeconds) {
        this.defaultQueryTimeoutDuration = defaultQueryTimeoutSeconds == null ? null : Duration.ofSeconds(defaultQueryTimeoutSeconds.intValue());
    }

    /**
     * Sets my delegate.
     *
     * @param connection
     *            my delegate, may be null.
     */
    public void setDelegate(final C connection) {
        this.connection = connection;
    }

    @Override
    public void setHoldability(final int holdability) throws SQLException {
        checkOpen();
        try {
            connection.setHoldability(holdability);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setNetworkTimeout(final Executor executor, final int milliseconds) throws SQLException {
        checkOpen();
        try {
            Jdbc41Bridge.setNetworkTimeout(connection, executor, milliseconds);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
        checkOpen();
        try {
            connection.setReadOnly(readOnly);
            if (cacheState) {
                cachedReadOnly = Boolean.valueOf(connection.isReadOnly());
            }
        } catch (final SQLException e) {
            cachedReadOnly = null;
            handleException(e);
        }
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkOpen();
        try {
            return connection.setSavepoint();
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public Savepoint setSavepoint(final String name) throws SQLException {
        checkOpen();
        try {
            return connection.setSavepoint(name);
        } catch (final SQLException e) {
            handleException(e);
            return null;
        }
    }

    @Override
    public void setSchema(final String schema) throws SQLException {
        checkOpen();
        try {
            Jdbc41Bridge.setSchema(connection, schema);
            if (cacheState) {
                cachedSchema = connection.getSchema();
            }
        } catch (final SQLException e) {
            cachedSchema = null;
            handleException(e);
        }
    }

    @Override
    public void setTransactionIsolation(final int level) throws SQLException {
        checkOpen();
        try {
            connection.setTransactionIsolation(level);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    @Override
    public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
        checkOpen();
        try {
            connection.setTypeMap(map);
        } catch (final SQLException e) {
            handleException(e);
        }
    }

    /**
     * Returns a string representation of the metadata associated with the innermost delegate connection.
     */
    @Override
    public synchronized String toString() {
        String str = null;

        final Connection conn = this.getInnermostDelegateInternal();
        if (conn != null) {
            try {
                if (conn.isClosed()) {
                    str = "connection is closed";
                } else {
                    final StringBuilder sb = new StringBuilder();
                    sb.append(hashCode());
                    final DatabaseMetaData meta = conn.getMetaData();
                    if (meta != null) {
                        sb.append(", URL=");
                        sb.append(meta.getURL());
                        sb.append(", ");
                        sb.append(meta.getDriverName());
                        str = sb.toString();
                    }
                }
            } catch (final SQLException ignored) {
                // Ignore
            }
        }
        return str != null ? str : super.toString();
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        if (iface.isAssignableFrom(connection.getClass())) {
            return iface.cast(connection);
        }
        return connection.unwrap(iface);
    }
}

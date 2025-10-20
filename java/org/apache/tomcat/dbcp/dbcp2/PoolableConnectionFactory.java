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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.pool2.DestroyMode;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 * A {@link PooledObjectFactory} that creates {@link PoolableConnection}s.
 *
 * @since 2.0
 */
public class PoolableConnectionFactory implements PooledObjectFactory<PoolableConnection> {

    private static final Log log = LogFactory.getLog(PoolableConnectionFactory.class);

    /**
     * Internal constant to indicate the level is not set.
     */
    static final int UNKNOWN_TRANSACTION_ISOLATION = -1;

    private final ConnectionFactory connectionFactory;

    private final ObjectName dataSourceJmxObjectName;

    private volatile String validationQuery;

    private volatile Duration validationQueryTimeoutDuration = Duration.ofSeconds(-1);

    private Collection<String> connectionInitSqls;

    private Collection<String> disconnectionSqlCodes;

    private Collection<String> disconnectionIgnoreSqlCodes;

    private boolean fastFailValidation = true;

    private volatile ObjectPool<PoolableConnection> pool;

    private Boolean defaultReadOnly;

    private Boolean defaultAutoCommit;

    private boolean autoCommitOnReturn = true;

    private boolean rollbackOnReturn = true;

    private int defaultTransactionIsolation = UNKNOWN_TRANSACTION_ISOLATION;

    private String defaultCatalog;

    private String defaultSchema;

    private boolean cacheState;

    private boolean poolStatements;

    private boolean clearStatementPoolOnReturn;

    private int maxOpenPreparedStatements = GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL_PER_KEY;

    private Duration maxConnDuration = Duration.ofMillis(-1);

    private final AtomicLong connectionIndex = new AtomicLong();

    private Duration defaultQueryTimeoutDuration;

    /**
     * Creates a new {@code PoolableConnectionFactory}.
     *
     * @param connFactory
     *            the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param dataSourceJmxObjectName
     *            The JMX object name, may be null.
     */
    public PoolableConnectionFactory(final ConnectionFactory connFactory, final ObjectName dataSourceJmxObjectName) {
        this.connectionFactory = connFactory;
        this.dataSourceJmxObjectName = dataSourceJmxObjectName;
    }

    @Override
    public void activateObject(final PooledObject<PoolableConnection> p) throws SQLException {

        validateLifetime(p);

        final PoolableConnection pConnection = p.getObject();
        pConnection.activate();

        if (defaultAutoCommit != null && pConnection.getAutoCommit() != defaultAutoCommit.booleanValue()) {
            pConnection.setAutoCommit(defaultAutoCommit.booleanValue());
        }
        if (defaultTransactionIsolation != UNKNOWN_TRANSACTION_ISOLATION
                && pConnection.getTransactionIsolation() != defaultTransactionIsolation) {
            pConnection.setTransactionIsolation(defaultTransactionIsolation);
        }
        if (defaultReadOnly != null && pConnection.isReadOnly() != defaultReadOnly.booleanValue()) {
            pConnection.setReadOnly(defaultReadOnly.booleanValue());
        }
        if (defaultCatalog != null && !defaultCatalog.equals(pConnection.getCatalog())) {
            pConnection.setCatalog(defaultCatalog);
        }
        if (defaultSchema != null && !defaultSchema.equals(Jdbc41Bridge.getSchema(pConnection))) {
            Jdbc41Bridge.setSchema(pConnection, defaultSchema);
        }
        pConnection.setDefaultQueryTimeout(defaultQueryTimeoutDuration);
    }

    @Override
    public void destroyObject(final PooledObject<PoolableConnection> p) throws SQLException {
        p.getObject().reallyClose();
    }

    /**
     * @since 2.9.0
     */
    @Override
    public void destroyObject(final PooledObject<PoolableConnection> p, final DestroyMode mode) throws SQLException {
        if (mode == DestroyMode.ABANDONED) {
            Jdbc41Bridge.abort(p.getObject().getInnermostDelegate(), Runnable::run);
        } else {
            p.getObject().reallyClose();
        }
    }

    /**
     * Gets the cache state to propagate in {@link #makeObject()}.
     *
     * @return The cache state.
     * @since 2.6.0.
     */
    public boolean getCacheState() {
        return cacheState;
    }

    /**
     * Gets the connection factory.
     *
     * @return The connection factory.
     * @since 2.6.0.
     */
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * Gets how many connections were created in {@link #makeObject()}.
     *
     * @return the connection count.
     */
    protected AtomicLong getConnectionIndex() {
        return connectionIndex;
    }

    /**
     * Gets the collection of initialization SQL statements.
     *
     * @return The collection of initialization SQL statements.
     * @since 2.6.0
     */
    public Collection<String> getConnectionInitSqls() {
        return connectionInitSqls;
    }

    /**
     * Gets data source JMX ObjectName.
     *
     * @return The data source JMX ObjectName.
     * @since 2.6.0.
     */
    public ObjectName getDataSourceJmxName() {
        return dataSourceJmxObjectName;
    }

    /**
     * Gets the data source JMX ObjectName.
     *
     * @return The data source JMX ObjectName.
     * @since 2.6.0
     */
    public ObjectName getDataSourceJmxObjectName() {
        return dataSourceJmxObjectName;
    }

    /**
     * Gets the Default auto-commit value.
     *
     * @return The default auto-commit value.
     * @since 2.6.0
     */
    public Boolean getDefaultAutoCommit() {
        return defaultAutoCommit;
    }

    /**
     * Gets the default catalog.
     *
     * @return The default catalog.
     * @since 2.6.0
     */
    public String getDefaultCatalog() {
        return defaultCatalog;
    }

    /**
     * Gets the default query timeout in seconds.
     *
     * @return The default query timeout in seconds.
     * @deprecated Use {@link #getDefaultQueryTimeoutDuration()}.
     */
    @Deprecated
    public Integer getDefaultQueryTimeout() {
        return getDefaultQueryTimeoutSeconds();
    }

    /**
     * Gets the default query timeout Duration.
     *
     * @return The default query timeout Duration.
     * @since 2.10.0
     */
    public Duration getDefaultQueryTimeoutDuration() {
        return defaultQueryTimeoutDuration;
    }

    /**
     * Gets the default query timeout in seconds.
     *
     * @return The default query timeout in seconds.
     * @since 2.6.0
     * @deprecated Use {@link #getDefaultQueryTimeoutDuration()}.
     */
    @Deprecated
    public Integer getDefaultQueryTimeoutSeconds() {
        return defaultQueryTimeoutDuration == null ? null : Integer.valueOf((int) defaultQueryTimeoutDuration.getSeconds());
    }

    /**
     * Gets the default read-only-value.
     *
     * @return The default read-only-value.
     * @since 2.6.0
     */
    public Boolean getDefaultReadOnly() {
        return defaultReadOnly;
    }

    /**
     * Gets the default schema.
     *
     * @return The default schema.
     * @since 2.6.0
     */
    public String getDefaultSchema() {
        return defaultSchema;
    }

    /**
     * Gets the default transaction isolation.
     *
     * @return The default transaction isolation.
     * @since 2.6.0
     */
    public int getDefaultTransactionIsolation() {
        return defaultTransactionIsolation;
    }

    /**
     * Gets the collection of SQL State codes that are not considered fatal disconnection codes.
     * <p>
     * This method returns the collection of SQL State codes that have been set to be ignored when
     * determining if a {@link SQLException} signals a disconnection. These codes are excluded from
     * being treated as fatal even if they match the typical disconnection criteria.
     * </p>
     *
     * @return a {@link Collection} of SQL State codes that should be ignored for disconnection checks.
     * @since 2.13.0
     */
    public Collection<String> getDisconnectionIgnoreSqlCodes() {
        return disconnectionIgnoreSqlCodes;
    }

    /**
     * Gets SQL State codes considered to signal fatal conditions.
     * <p>
     * Overrides the defaults in {@link Utils#getDisconnectionSqlCodes()} (plus anything starting with
     * {@link Utils#DISCONNECTION_SQL_CODE_PREFIX}). If this property is non-null and {@link #isFastFailValidation()} is
     * {@code true}, whenever connections created by this factory generate exceptions with SQL State codes in this list,
     * they will be marked as "fatally disconnected" and subsequent validations will fail fast (no attempt at isValid or
     * validation query).
     * </p>
     * <p>
     * If {@link #isFastFailValidation()} is {@code false} setting this property has no effect.
     * </p>
     *
     * @return SQL State codes overriding defaults
     * @since 2.1
     */
    public Collection<String> getDisconnectionSqlCodes() {
        return disconnectionSqlCodes;
    }

    /**
     * Gets the Maximum connection duration.
     *
     * @return Maximum connection duration.
     * @since 2.10.0
     */
    public Duration getMaxConnDuration() {
        return maxConnDuration;
    }

    /**
     * Gets the Maximum connection lifetime in milliseconds.
     *
     * @return Maximum connection lifetime in milliseconds.
     * @since 2.6.0
     */
    public long getMaxConnLifetimeMillis() {
        return maxConnDuration.toMillis();
    }

    /**
     * Gets the maximum number of open prepared statements.
     *
     * @return The maximum number of open prepared statements.
     */
    protected int getMaxOpenPreparedStatements() {
        return maxOpenPreparedStatements;
    }

    /**
     * Returns the {@link ObjectPool} in which {@link Connection}s are pooled.
     *
     * @return the connection pool
     */
    public synchronized ObjectPool<PoolableConnection> getPool() {
        return pool;
    }

    /**
     * Tests whether to pool statements.
     *
     * @return Whether to pool statements.
     * @since 2.6.0.
     */
    public boolean getPoolStatements() {
        return poolStatements;
    }

    /**
     * Gets the validation query.
     *
     * @return Validation query.
     * @since 2.6.0
     */
    public String getValidationQuery() {
        return validationQuery;
    }

    /**
     * Gets the query timeout in seconds.
     *
     * @return Validation query timeout in seconds.
     * @since 2.10.0
     */
    public Duration getValidationQueryTimeoutDuration() {
        return validationQueryTimeoutDuration;
    }

    /**
     * Gets the query timeout in seconds.
     *
     * @return Validation query timeout in seconds.
     * @since 2.6.0
     * @deprecated Use {@link #getValidationQueryTimeoutDuration()}.
     */
    @Deprecated
    public int getValidationQueryTimeoutSeconds() {
        return (int) validationQueryTimeoutDuration.getSeconds();
    }

    /**
     * Initializes the given connection with the collection of SQL statements set in {@link #setConnectionInitSql(Collection)}.
     *
     * @param conn the connection to initialize.
     * @throws SQLException if a database access error occurs or this method is called on a closed connection.
     * @see #setConnectionInitSql(Collection)
     */
    protected void initializeConnection(final Connection conn) throws SQLException {
        final Collection<String> sqls = connectionInitSqls;
        if (conn.isClosed()) {
            throw new SQLException("initializeConnection: connection closed");
        }
        if (!Utils.isEmpty(sqls)) {
            try (Statement statement = conn.createStatement()) {
                for (final String sql : sqls) {
                    statement.execute(Objects.requireNonNull(sql, "null connectionInitSqls element"));
                }
            }
        }
    }

    /**
     * Tests whether to set auto-commit on {@link #passivateObject(PooledObject)}.
     *
     * @return Whether to set auto-commit on {@link #passivateObject(PooledObject)}.
     * @since 2.6.0
     */
    public boolean isAutoCommitOnReturn() {
        return autoCommitOnReturn;
    }

    /**
     * Tests whether to set auto-commit on {@link #passivateObject(PooledObject)}.
     *
     * @return Whether to set auto-commit on {@link #passivateObject(PooledObject)}.
     * @deprecated Use {@link #isAutoCommitOnReturn()}.
     */
    @Deprecated
    public boolean isEnableAutoCommitOnReturn() {
        return autoCommitOnReturn;
    }

    /**
     * True means that validation will fail immediately for connections that have previously thrown SQLExceptions with
     * SQL State indicating fatal disconnection errors.
     *
     * @return true if connections created by this factory will fast fail validation.
     * @see #setDisconnectionSqlCodes(Collection)
     * @since 2.1
     * @since 2.5.0 Defaults to true, previous versions defaulted to false.
     */
    public boolean isFastFailValidation() {
        return fastFailValidation;
    }

    /**
     * Tests whether to rollback on return.
     *
     * @return Whether to rollback on return.
     */
    public boolean isRollbackOnReturn() {
        return rollbackOnReturn;
    }

    @Override
    public PooledObject<PoolableConnection> makeObject() throws SQLException {
        Connection conn = connectionFactory.createConnection();
        if (conn == null) {
            throw new IllegalStateException("Connection factory returned null from createConnection");
        }
        try {
            initializeConnection(conn);
        } catch (final SQLException e) {
            // Make sure the connection is closed
            Utils.closeQuietly((AutoCloseable) conn);
            // Rethrow original exception so it is visible to caller
            throw e;
        }

        final long connIndex = connectionIndex.getAndIncrement();

        if (poolStatements) {
            conn = new PoolingConnection(conn);
            final GenericKeyedObjectPoolConfig<DelegatingPreparedStatement> config = new GenericKeyedObjectPoolConfig<>();
            config.setMaxTotalPerKey(-1);
            config.setBlockWhenExhausted(false);
            config.setMaxWait(Duration.ZERO);
            config.setMaxIdlePerKey(1);
            config.setMaxTotal(maxOpenPreparedStatements);
            if (dataSourceJmxObjectName != null) {
                final StringBuilder base = new StringBuilder(dataSourceJmxObjectName.toString());
                base.append(Constants.JMX_CONNECTION_BASE_EXT);
                base.append(connIndex);
                config.setJmxNameBase(base.toString());
                config.setJmxNamePrefix(Constants.JMX_STATEMENT_POOL_PREFIX);
            } else {
                config.setJmxEnabled(false);
            }
            final PoolingConnection poolingConn = (PoolingConnection) conn;
            final KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> stmtPool = new GenericKeyedObjectPool<>(poolingConn, config);
            poolingConn.setStatementPool(stmtPool);
            poolingConn.setClearStatementPoolOnReturn(clearStatementPoolOnReturn);
            poolingConn.setCacheState(cacheState);
        }

        // Register this connection with JMX
        final ObjectName connJmxName;
        if (dataSourceJmxObjectName == null) {
            connJmxName = null;
        } else {
            final String name = dataSourceJmxObjectName.toString() + Constants.JMX_CONNECTION_BASE_EXT + connIndex;
            try {
                connJmxName = new ObjectName(name);
            } catch (final MalformedObjectNameException e) {
                Utils.closeQuietly((AutoCloseable) conn);
                throw new SQLException(name, e);
            }
        }

        final PoolableConnection pc = new PoolableConnection(conn, pool, connJmxName,
                disconnectionSqlCodes, disconnectionIgnoreSqlCodes, fastFailValidation);
        pc.setCacheState(cacheState);

        return new DefaultPooledObject<>(pc);
    }

    @Override
    public void passivateObject(final PooledObject<PoolableConnection> p) throws SQLException {

        validateLifetime(p);

        final PoolableConnection conn = p.getObject();
        Boolean connAutoCommit = null;
        if (rollbackOnReturn) {
            connAutoCommit = Boolean.valueOf(conn.getAutoCommit());
            if (!connAutoCommit.booleanValue() && !conn.isReadOnly()) {
                conn.rollback();
            }
        }

        conn.clearWarnings();

        // DBCP-97 / DBCP-399 / DBCP-351 Idle connections in the pool should
        // have autoCommit enabled
        if (autoCommitOnReturn) {
            if (connAutoCommit == null) {
                connAutoCommit = Boolean.valueOf(conn.getAutoCommit());
            }
            if (!connAutoCommit.booleanValue()) {
                conn.setAutoCommit(true);
            }
        }

        conn.passivate();
    }

    /**
     * Sets whether to set auto-commit on {@link #passivateObject(PooledObject)}.
     *
     * @param autoCommitOnReturn whether to set auto-commit.
     */
    public void setAutoCommitOnReturn(final boolean autoCommitOnReturn) {
        this.autoCommitOnReturn = autoCommitOnReturn;
    }

    /**
     * Sets the cache state to propagate in {@link #makeObject()}.
     *
     * @param cacheState the cache state to propagate.
     */
    public void setCacheState(final boolean cacheState) {
        this.cacheState = cacheState;
    }

    /**
     * Sets whether the pool of statements (which was enabled with {@link #setPoolStatements(boolean)}) should
     * be cleared when the connection is returned to its pool. Default is false.
     *
     * @param clearStatementPoolOnReturn clear or not
     * @since 2.8.0
     */
    public void setClearStatementPoolOnReturn(final boolean clearStatementPoolOnReturn) {
        this.clearStatementPoolOnReturn = clearStatementPoolOnReturn;
    }

    /**
     * Sets the SQL statements I use to initialize newly created {@link Connection}s. Using {@code null} turns off
     * connection initialization.
     *
     * @param connectionInitSqls
     *            SQL statement to initialize {@link Connection}s.
     */
    public void setConnectionInitSql(final Collection<String> connectionInitSqls) {
        this.connectionInitSqls = connectionInitSqls;
    }
    /**
     * Sets the default "auto commit" setting for borrowed {@link Connection}s
     *
     * @param defaultAutoCommit
     *            the default "auto commit" setting for borrowed {@link Connection}s
     */
    public void setDefaultAutoCommit(final Boolean defaultAutoCommit) {
        this.defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * Sets the default "catalog" setting for borrowed {@link Connection}s
     *
     * @param defaultCatalog
     *            the default "catalog" setting for borrowed {@link Connection}s
     */
    public void setDefaultCatalog(final String defaultCatalog) {
        this.defaultCatalog = defaultCatalog;
    }

    /**
     * Sets the query timeout Duration.
     *
     * @param defaultQueryTimeoutDuration the query timeout Duration.
     * @since 2.10.0
     */
    public void setDefaultQueryTimeout(final Duration defaultQueryTimeoutDuration) {
        this.defaultQueryTimeoutDuration = defaultQueryTimeoutDuration;
    }

    /**
     * Sets the query timeout in seconds.
     *
     * @param defaultQueryTimeoutSeconds the query timeout in seconds.
     * @deprecated Use {@link #setDefaultQueryTimeout(Duration)}.
     */
    @Deprecated
    public void setDefaultQueryTimeout(final Integer defaultQueryTimeoutSeconds) {
        this.defaultQueryTimeoutDuration = defaultQueryTimeoutSeconds == null ? null : Duration.ofSeconds(defaultQueryTimeoutSeconds.longValue());
    }

    /**
     * Sets the default "read only" setting for borrowed {@link Connection}s
     *
     * @param defaultReadOnly
     *            the default "read only" setting for borrowed {@link Connection}s
     */
    public void setDefaultReadOnly(final Boolean defaultReadOnly) {
        this.defaultReadOnly = defaultReadOnly;
    }

    /**
     * Sets the default "schema" setting for borrowed {@link Connection}s
     *
     * @param defaultSchema
     *            the default "schema" setting for borrowed {@link Connection}s
     * @since 2.5.0
     */
    public void setDefaultSchema(final String defaultSchema) {
        this.defaultSchema = defaultSchema;
    }

    /**
     * Sets the default "Transaction Isolation" setting for borrowed {@link Connection}s
     *
     * @param defaultTransactionIsolation
     *            the default "Transaction Isolation" setting for returned {@link Connection}s
     */
    public void setDefaultTransactionIsolation(final int defaultTransactionIsolation) {
        this.defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * Sets the disconnection SQL codes to ignore.
     *
     * @param disconnectionIgnoreSqlCodes
     *            The collection of SQL State codes to be ignored.
     * @see #getDisconnectionIgnoreSqlCodes()
     * @throws IllegalArgumentException if any SQL state codes overlap with those in {@link #disconnectionSqlCodes}.
     * @since 2.13.0
     */
    public void setDisconnectionIgnoreSqlCodes(final Collection<String> disconnectionIgnoreSqlCodes) {
        Utils.checkSqlCodes(disconnectionIgnoreSqlCodes, this.disconnectionSqlCodes);
        this.disconnectionIgnoreSqlCodes = disconnectionIgnoreSqlCodes;
    }

    /**
     * Sets the disconnection SQL codes.
     *
     * @param disconnectionSqlCodes
     *            The disconnection SQL codes.
     * @see #getDisconnectionSqlCodes()
     * @since 2.1
     * @throws IllegalArgumentException if any SQL state codes overlap with those in {@link #disconnectionIgnoreSqlCodes}.
     */
    public void setDisconnectionSqlCodes(final Collection<String> disconnectionSqlCodes) {
        Utils.checkSqlCodes(disconnectionSqlCodes, this.disconnectionIgnoreSqlCodes);
        this.disconnectionSqlCodes = disconnectionSqlCodes;
    }

    /**
     * Sets whether to set auto-commit on {@link #passivateObject(PooledObject)}.
     *
     * @param autoCommitOnReturn whether to set auto-commit.
     */
    @Deprecated
    public void setEnableAutoCommitOnReturn(final boolean autoCommitOnReturn) {
        this.autoCommitOnReturn = autoCommitOnReturn;
    }

    /**
     * @see #isFastFailValidation()
     * @param fastFailValidation
     *            true means connections created by this factory will fast fail validation
     * @since 2.1
     */
    public void setFastFailValidation(final boolean fastFailValidation) {
        this.fastFailValidation = fastFailValidation;
    }

    /**
     * Sets the maximum lifetime in milliseconds of a connection after which the connection will always fail activation,
     * passivation and validation. A value of zero or less indicates an infinite lifetime. The default value is -1.
     *
     * @param maxConnDuration
     *            The maximum lifetime in milliseconds.
     * @since 2.10.0
     */
    public void setMaxConn(final Duration maxConnDuration) {
        this.maxConnDuration = maxConnDuration;
    }

    /**
     * Sets the maximum lifetime in milliseconds of a connection after which the connection will always fail activation,
     * passivation and validation. A value of zero or less indicates an infinite lifetime. The default value is -1.
     *
     * @param maxConnLifetimeMillis
     *            The maximum lifetime in milliseconds.
     * @deprecated Use {@link #setMaxConn(Duration)}.
     */
    @Deprecated
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        this.maxConnDuration = Duration.ofMillis(maxConnLifetimeMillis);
    }

    /**
     * Sets the maximum number of open prepared statements.
     *
     * @param maxOpenPreparedStatements
     *            The maximum number of open prepared statements.
     */
    public void setMaxOpenPreparedStatements(final int maxOpenPreparedStatements) {
        this.maxOpenPreparedStatements = maxOpenPreparedStatements;
    }

    /**
     * Deprecated due to typo in method name.
     *
     * @param maxOpenPreparedStatements
     *            The maximum number of open prepared statements.
     * @deprecated Use {@link #setMaxOpenPreparedStatements(int)}.
     */
    @Deprecated // Due to typo in method name.
    public void setMaxOpenPrepatedStatements(final int maxOpenPreparedStatements) {
        setMaxOpenPreparedStatements(maxOpenPreparedStatements);
    }

    /**
     * Sets the {@link ObjectPool} in which to pool {@link Connection}s.
     *
     * @param pool
     *            the {@link ObjectPool} in which to pool those {@link Connection}s
     */
    public synchronized void setPool(final ObjectPool<PoolableConnection> pool) {
        if (null != this.pool && pool != this.pool) {
            Utils.closeQuietly(this.pool);
        }
        this.pool = pool;
    }

    /**
     * Sets whether to pool statements.
     *
     * @param poolStatements whether to pool statements.
     */
    public void setPoolStatements(final boolean poolStatements) {
        this.poolStatements = poolStatements;
    }

    /**
     * Sets whether to rollback on return.
     *
     * @param rollbackOnReturn whether to rollback on return.
     */
    public void setRollbackOnReturn(final boolean rollbackOnReturn) {
        this.rollbackOnReturn = rollbackOnReturn;
    }

    /**
     * Sets the query I use to {@link #validateObject validate} {@link Connection}s. Should return at least one row. If
     * not specified, {@link Connection#isValid(int)} will be used to validate connections.
     *
     * @param validationQuery
     *            a query to use to {@link #validateObject validate} {@link Connection}s.
     */
    public void setValidationQuery(final String validationQuery) {
        this.validationQuery = validationQuery;
    }

    /**
     * Sets the validation query timeout, the amount of time, that connection validation will wait for a response from the
     * database when executing a validation query. Use a value less than or equal to 0 for no timeout.
     *
     * @param validationQueryTimeoutDuration new validation query timeout duration.
     * @since 2.10.0
     */
    public void setValidationQueryTimeout(final Duration validationQueryTimeoutDuration) {
        this.validationQueryTimeoutDuration = validationQueryTimeoutDuration;
    }

    /**
     * Sets the validation query timeout, the amount of time, in seconds, that connection validation will wait for a
     * response from the database when executing a validation query. Use a value less than or equal to 0 for no timeout.
     *
     * @param validationQueryTimeoutSeconds
     *            new validation query timeout value in seconds
     * @deprecated {@link #setValidationQueryTimeout(Duration)}.
     */
    @Deprecated
    public void setValidationQueryTimeout(final int validationQueryTimeoutSeconds) {
        this.validationQueryTimeoutDuration = Duration.ofSeconds(validationQueryTimeoutSeconds);
    }

    /**
     * Validates the given connection if it is open.
     *
     * @param conn the connection to validate.
     * @throws SQLException if the connection is closed or validate fails.
     */
    public void validateConnection(final PoolableConnection conn) throws SQLException {
        if (conn.isClosed()) {
            throw new SQLException("validateConnection: connection closed");
        }
        conn.validate(validationQuery, validationQueryTimeoutDuration);
    }

    private void validateLifetime(final PooledObject<PoolableConnection> p) throws LifetimeExceededException {
        Utils.validateLifetime(p, maxConnDuration);
    }

    @Override
    public boolean validateObject(final PooledObject<PoolableConnection> p) {
        try {
            validateLifetime(p);
            validateConnection(p.getObject());
            return true;
        } catch (final Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(Utils.getMessage("poolableConnectionFactory.validateObject.fail"), e);
            }
            return false;
        }
    }
}

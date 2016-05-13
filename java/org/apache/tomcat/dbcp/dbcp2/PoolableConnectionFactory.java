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
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.PooledObject;
import org.apache.tomcat.dbcp.pool2.PooledObjectFactory;
import org.apache.tomcat.dbcp.pool2.impl.DefaultPooledObject;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 * A {@link PooledObjectFactory} that creates
 * {@link PoolableConnection}s.
 *
 * @author Rodney Waldhoff
 * @author Glenn L. Nielsen
 * @author James House
 * @author Dirk Verbeeck
 * @since 2.0
 */
public class PoolableConnectionFactory
        implements PooledObjectFactory<PoolableConnection> {

    private static final Log log =
            LogFactory.getLog(PoolableConnectionFactory.class);

    /**
     * Create a new {@code PoolableConnectionFactory}.
     * @param connFactory the {@link ConnectionFactory} from which to obtain
     * @param dataSourceJmxName The JMX name
     * base {@link Connection}s
     */
    public PoolableConnectionFactory(final ConnectionFactory connFactory,
            final ObjectName dataSourceJmxName) {
        _connFactory = connFactory;
        this.dataSourceJmxName = dataSourceJmxName;
    }

    /**
     * Sets the query I use to {@link #validateObject validate} {@link Connection}s.
     * Should return at least one row. If not specified,
     * {@link Connection#isValid(int)} will be used to validate connections.
     *
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.
     */
    public void setValidationQuery(final String validationQuery) {
        _validationQuery = validationQuery;
    }

    /**
     * Sets the validation query timeout, the amount of time, in seconds, that
     * connection validation will wait for a response from the database when
     * executing a validation query.  Use a value less than or equal to 0 for
     * no timeout.
     *
     * @param timeout new validation query timeout value in seconds
     */
    public void setValidationQueryTimeout(final int timeout) {
        _validationQueryTimeout = timeout;
    }

    /**
     * Sets the SQL statements I use to initialize newly created {@link Connection}s.
     * Using {@code null} turns off connection initialization.
     * @param connectionInitSqls SQL statement to initialize {@link Connection}s.
     */
    public void setConnectionInitSql(final Collection<String> connectionInitSqls) {
        _connectionInitSqls = connectionInitSqls;
    }

    /**
     * Sets the {@link ObjectPool} in which to pool {@link Connection}s.
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     */
    public synchronized void setPool(final ObjectPool<PoolableConnection> pool) {
        if(null != _pool && pool != _pool) {
            try {
                _pool.close();
            } catch(final Exception e) {
                // ignored !?!
            }
        }
        _pool = pool;
    }

    /**
     * Returns the {@link ObjectPool} in which {@link Connection}s are pooled.
     * @return the connection pool
     */
    public synchronized ObjectPool<PoolableConnection> getPool() {
        return _pool;
    }

    /**
     * Sets the default "read only" setting for borrowed {@link Connection}s
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     */
    public void setDefaultReadOnly(final Boolean defaultReadOnly) {
        _defaultReadOnly = defaultReadOnly;
    }

    /**
     * Sets the default "auto commit" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for borrowed {@link Connection}s
     */
    public void setDefaultAutoCommit(final Boolean defaultAutoCommit) {
        _defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * Sets the default "Transaction Isolation" setting for borrowed {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     */
    public void setDefaultTransactionIsolation(final int defaultTransactionIsolation) {
        _defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * Sets the default "catalog" setting for borrowed {@link Connection}s
     * @param defaultCatalog the default "catalog" setting for borrowed {@link Connection}s
     */
    public void setDefaultCatalog(final String defaultCatalog) {
        _defaultCatalog = defaultCatalog;
    }

    public void setCacheState(final boolean cacheState) {
        this._cacheState = cacheState;
    }

    public void setPoolStatements(final boolean poolStatements) {
        this.poolStatements = poolStatements;
    }

    public void setMaxOpenPrepatedStatements(final int maxOpenPreparedStatements) {
        this.maxOpenPreparedStatements = maxOpenPreparedStatements;
    }

    /**
     * Sets the maximum lifetime in milliseconds of a connection after which the
     * connection will always fail activation, passivation and validation. A
     * value of zero or less indicates an infinite lifetime. The default value
     * is -1.
     * @param maxConnLifetimeMillis The maximum connection lifetime
     */
    public void setMaxConnLifetimeMillis(final long maxConnLifetimeMillis) {
        this.maxConnLifetimeMillis = maxConnLifetimeMillis;
    }


    public boolean isEnableAutoCommitOnReturn() {
        return enableAutoCommitOnReturn;
    }

    public void setEnableAutoCommitOnReturn(final boolean enableAutoCommitOnReturn) {
        this.enableAutoCommitOnReturn = enableAutoCommitOnReturn;
    }


    public boolean isRollbackOnReturn() {
        return rollbackOnReturn;
    }

    public void setRollbackOnReturn(final boolean rollbackOnReturn) {
        this.rollbackOnReturn = rollbackOnReturn;
    }

    public Integer getDefaultQueryTimeout() {
        return defaultQueryTimeout;
    }

    public void setDefaultQueryTimeout(final Integer defaultQueryTimeout) {
        this.defaultQueryTimeout = defaultQueryTimeout;
    }

    /**
     * SQL_STATE codes considered to signal fatal conditions.
     * <p>
     * Overrides the defaults in {@link Utils#DISCONNECTION_SQL_CODES}
     * (plus anything starting with {@link Utils#DISCONNECTION_SQL_CODE_PREFIX}).
     * If this property is non-null and {@link #isFastFailValidation()} is
     * {@code true}, whenever connections created by this factory generate exceptions
     * with SQL_STATE codes in this list, they will be marked as "fatally disconnected"
     * and subsequent validations will fail fast (no attempt at isValid or validation
     * query).</p>
     * <p>
     * If {@link #isFastFailValidation()} is {@code false} setting this property has no
     * effect.</p>
     *
     * @return SQL_STATE codes overriding defaults
     * @since 2.1
     */
    public Collection<String> getDisconnectionSqlCodes() {
        return _disconnectionSqlCodes;
    }

    /**
     * @see #getDisconnectionSqlCodes()
     * @param disconnectionSqlCodes The disconnection codes
     * @since 2.1
     */
    public void setDisconnectionSqlCodes(final Collection<String> disconnectionSqlCodes) {
        _disconnectionSqlCodes = disconnectionSqlCodes;
    }

    /**
     * True means that validation will fail immediately for connections that
     * have previously thrown SQLExceptions with SQL_STATE indicating fatal
     * disconnection errors.
     *
     * @return true if connections created by this factory will fast fail validation.
     * @see #setDisconnectionSqlCodes(Collection)
     * @since 2.1
     */
    public boolean isFastFailValidation() {
        return _fastFailValidation;
    }

    /**
     * @see #isFastFailValidation()
     * @param fastFailValidation true means connections created by this factory will
     * fast fail validation
     * @since 2.1
     */
    public void setFastFailValidation(final boolean fastFailValidation) {
        _fastFailValidation = fastFailValidation;
    }

    @Override
    public PooledObject<PoolableConnection> makeObject() throws Exception {
        Connection conn = _connFactory.createConnection();
        if (conn == null) {
            throw new IllegalStateException("Connection factory returned null from createConnection");
        }
        try {
            initializeConnection(conn);
        } catch (final SQLException sqle) {
            // Make sure the connection is closed
            try {
                conn.close();
            } catch (final SQLException ignore) {
                // ignore
            }
            // Rethrow original exception so it is visible to caller
            throw sqle;
        }

        final long connIndex = connectionIndex.getAndIncrement();

        if(poolStatements) {
            conn = new PoolingConnection(conn);
            final GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();
            config.setMaxTotalPerKey(-1);
            config.setBlockWhenExhausted(false);
            config.setMaxWaitMillis(0);
            config.setMaxIdlePerKey(1);
            config.setMaxTotal(maxOpenPreparedStatements);
            if (dataSourceJmxName != null) {
                final StringBuilder base = new StringBuilder(dataSourceJmxName.toString());
                base.append(Constants.JMX_CONNECTION_BASE_EXT);
                base.append(Long.toString(connIndex));
                config.setJmxNameBase(base.toString());
                config.setJmxNamePrefix(Constants.JMX_STATEMENT_POOL_PREFIX);
            } else {
                config.setJmxEnabled(false);
            }
            final KeyedObjectPool<PStmtKey,DelegatingPreparedStatement> stmtPool =
                    new GenericKeyedObjectPool<>((PoolingConnection)conn, config);
            ((PoolingConnection)conn).setStatementPool(stmtPool);
            ((PoolingConnection) conn).setCacheState(_cacheState);
        }

        // Register this connection with JMX
        ObjectName connJmxName;
        if (dataSourceJmxName == null) {
            connJmxName = null;
        } else {
            connJmxName = new ObjectName(dataSourceJmxName.toString() +
                    Constants.JMX_CONNECTION_BASE_EXT + connIndex);
        }

        final PoolableConnection pc = new PoolableConnection(conn, _pool, connJmxName,
                                      _disconnectionSqlCodes, _fastFailValidation);
        pc.setCacheState(_cacheState);

        return new DefaultPooledObject<>(pc);
    }

    protected void initializeConnection(final Connection conn) throws SQLException {
        final Collection<String> sqls = _connectionInitSqls;
        if(conn.isClosed()) {
            throw new SQLException("initializeConnection: connection closed");
        }
        if(null != sqls) {
            try (Statement stmt = conn.createStatement();) {
                for (final String sql : sqls) {
                    if (sql == null) {
                        throw new NullPointerException(
                                "null connectionInitSqls element");
                    }
                    stmt.execute(sql);
                }
            }
        }
    }

    @Override
    public void destroyObject(final PooledObject<PoolableConnection> p)
            throws Exception {
        p.getObject().reallyClose();
    }

    @Override
    public boolean validateObject(final PooledObject<PoolableConnection> p) {
        try {
            validateLifetime(p);

            validateConnection(p.getObject());
            return true;
        } catch (final Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(Utils.getMessage(
                        "poolableConnectionFactory.validateObject.fail"), e);
            }
            return false;
        }
    }

    public void validateConnection(final PoolableConnection conn) throws SQLException {
        if(conn.isClosed()) {
            throw new SQLException("validateConnection: connection closed");
        }
        conn.validate(_validationQuery, _validationQueryTimeout);
    }

    @Override
    public void passivateObject(final PooledObject<PoolableConnection> p)
            throws Exception {

        validateLifetime(p);

        final PoolableConnection conn = p.getObject();
        Boolean connAutoCommit = null;
        if (rollbackOnReturn) {
            connAutoCommit = Boolean.valueOf(conn.getAutoCommit());
            if(!connAutoCommit.booleanValue() && !conn.isReadOnly()) {
                conn.rollback();
            }
        }

        conn.clearWarnings();

        // DBCP-97 / DBCP-399 / DBCP-351 Idle connections in the pool should
        // have autoCommit enabled
        if (enableAutoCommitOnReturn) {
            if (connAutoCommit == null) {
                connAutoCommit = Boolean.valueOf(conn.getAutoCommit());
            }
            if(!connAutoCommit.booleanValue()) {
                conn.setAutoCommit(true);
            }
        }

        conn.passivate();
    }

    @Override
    public void activateObject(final PooledObject<PoolableConnection> p)
            throws Exception {

        validateLifetime(p);

        final PoolableConnection conn = p.getObject();
        conn.activate();

        if (_defaultAutoCommit != null &&
                conn.getAutoCommit() != _defaultAutoCommit.booleanValue()) {
            conn.setAutoCommit(_defaultAutoCommit.booleanValue());
        }
        if (_defaultTransactionIsolation != UNKNOWN_TRANSACTIONISOLATION &&
                conn.getTransactionIsolation() != _defaultTransactionIsolation) {
            conn.setTransactionIsolation(_defaultTransactionIsolation);
        }
        if (_defaultReadOnly != null &&
                conn.isReadOnly() != _defaultReadOnly.booleanValue()) {
            conn.setReadOnly(_defaultReadOnly.booleanValue());
        }
        if (_defaultCatalog != null &&
                !_defaultCatalog.equals(conn.getCatalog())) {
            conn.setCatalog(_defaultCatalog);
        }
        conn.setDefaultQueryTimeout(defaultQueryTimeout);
    }

    private void validateLifetime(final PooledObject<PoolableConnection> p)
            throws Exception {
        if (maxConnLifetimeMillis > 0) {
            final long lifetime = System.currentTimeMillis() - p.getCreateTime();
            if (lifetime > maxConnLifetimeMillis) {
                throw new LifetimeExceededException(Utils.getMessage(
                        "connectionFactory.lifetimeExceeded",
                        Long.valueOf(lifetime),
                        Long.valueOf(maxConnLifetimeMillis)));
            }
        }
    }

    protected ConnectionFactory getConnectionFactory() {
        return _connFactory;
    }

    protected boolean getPoolStatements() {
        return poolStatements;
    }

    protected int getMaxOpenPreparedStatements() {
        return maxOpenPreparedStatements;
    }

    protected boolean getCacheState() {
        return _cacheState;
    }

    protected ObjectName getDataSourceJmxName() {
        return dataSourceJmxName;
    }

    protected AtomicLong getConnectionIndex() {
        return connectionIndex;
    }

    private final ConnectionFactory _connFactory;
    private final ObjectName dataSourceJmxName;
    private volatile String _validationQuery = null;
    private volatile int _validationQueryTimeout = -1;
    private Collection<String> _connectionInitSqls = null;
    private Collection<String> _disconnectionSqlCodes = null;
    private boolean _fastFailValidation = false;
    private volatile ObjectPool<PoolableConnection> _pool = null;
    private Boolean _defaultReadOnly = null;
    private Boolean _defaultAutoCommit = null;
    private boolean enableAutoCommitOnReturn = true;
    private boolean rollbackOnReturn = true;
    private int _defaultTransactionIsolation = UNKNOWN_TRANSACTIONISOLATION;
    private String _defaultCatalog;
    private boolean _cacheState;
    private boolean poolStatements = false;
    private int maxOpenPreparedStatements =
        GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL_PER_KEY;
    private long maxConnLifetimeMillis = -1;
    private final AtomicLong connectionIndex = new AtomicLong(0);
    private Integer defaultQueryTimeout = null;

    /**
     * Internal constant to indicate the level is not set.
     */
    static final int UNKNOWN_TRANSACTIONISOLATION = -1;
}

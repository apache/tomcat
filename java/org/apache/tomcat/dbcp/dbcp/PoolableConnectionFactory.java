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

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;

import org.apache.tomcat.dbcp.pool.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool.KeyedObjectPoolFactory;
import org.apache.tomcat.dbcp.pool.PoolableObjectFactory;
import org.apache.tomcat.dbcp.pool.ObjectPool;

/**
 * A {@link PoolableObjectFactory} that creates
 * {@link PoolableConnection}s.
 *
 * @author Rodney Waldhoff
 * @author Glenn L. Nielsen
 * @author James House
 * @author Dirk Verbeeck
 */
public class PoolableConnectionFactory implements PoolableObjectFactory<PoolableConnection> {
    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     */
    public PoolableConnectionFactory(ConnectionFactory connFactory, ObjectPool<PoolableConnection> pool,
            KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory, String validationQuery,
            boolean defaultReadOnly, boolean defaultAutoCommit) {
        _connFactory = connFactory;
        _pool = pool;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param connectionInitSqls a Collection of SQL statements to initialize {@link Connection}s. Using <code>null</code> turns off initialization.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @since 1.3
     */
    public PoolableConnectionFactory(ConnectionFactory connFactory, ObjectPool<PoolableConnection> pool,
            KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory, String validationQuery,
            Collection<String> connectionInitSqls, boolean defaultReadOnly, boolean defaultAutoCommit) {
        _connFactory = connFactory;
        _pool = pool;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _connectionInitSqls = connectionInitSqls;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param validationQueryTimeout the number of seconds that validation queries will wait for database response before failing.  Use a value less than or equal to 0 for no timeout.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @since 1.3
     */
    public PoolableConnectionFactory(ConnectionFactory connFactory, ObjectPool<PoolableConnection> pool,
            KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory, String validationQuery,
            int validationQueryTimeout, boolean defaultReadOnly, boolean defaultAutoCommit) {
        _connFactory = connFactory;
        _pool = pool;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _validationQueryTimeout = validationQueryTimeout;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param validationQueryTimeout the number of seconds that validation queries will wait for database response before failing.  Use a value less than or equal to 0 for no timeout.
     * @param connectionInitSqls a Collection of SQL statements to initialize {@link Connection}s. Using <code>null</code> turns off initialization.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @since 1.3
     */
    public PoolableConnectionFactory(ConnectionFactory connFactory, ObjectPool<PoolableConnection> pool,
            KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory, String validationQuery,
            int validationQueryTimeout, Collection<String> connectionInitSqls, boolean defaultReadOnly, boolean defaultAutoCommit) {
        _connFactory = connFactory;
        _pool = pool;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _validationQueryTimeout = validationQueryTimeout;
        _connectionInitSqls = connectionInitSqls;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     */
    public PoolableConnectionFactory(ConnectionFactory connFactory, ObjectPool<PoolableConnection> pool,
            KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory, String validationQuery,
            boolean defaultReadOnly, boolean defaultAutoCommit, int defaultTransactionIsolation) {
        _connFactory = connFactory;
        _pool = pool;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
        _defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param connectionInitSqls a Collection of SQL statement to initialize {@link Connection}s. Using <code>null</code> turns off initialization.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     * @since 1.3
     */
    public PoolableConnectionFactory(ConnectionFactory connFactory, ObjectPool<PoolableConnection> pool,
            KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory, String validationQuery,
            Collection<String> connectionInitSqls, boolean defaultReadOnly, boolean defaultAutoCommit, int defaultTransactionIsolation) {
        _connFactory = connFactory;
        _pool = pool;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _connectionInitSqls = connectionInitSqls;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
        _defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param validationQueryTimeout the number of seconds that validation queries will wait for database response before failing.  Use a value less than or equal to 0 for no timeout.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     * @since 1.3
     */
    public PoolableConnectionFactory(ConnectionFactory connFactory, ObjectPool<PoolableConnection> pool,
            KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory, String validationQuery,
            int validationQueryTimeout, boolean defaultReadOnly, boolean defaultAutoCommit, int defaultTransactionIsolation) {
        _connFactory = connFactory;
        _pool = pool;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _validationQueryTimeout = validationQueryTimeout;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
        _defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param validationQueryTimeout the number of seconds that validation queries will wait for database response before failing.  Use a value less than or equal to 0 for no timeout.
     * @param connectionInitSqls a Collection of SQL statement to initialize {@link Connection}s. Using <code>null</code> turns off initialization.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     * @since 1.3
     */
    public PoolableConnectionFactory(ConnectionFactory connFactory, ObjectPool<PoolableConnection> pool,
            KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory, String validationQuery,
            int validationQueryTimeout, Collection<String> connectionInitSqls, boolean defaultReadOnly, boolean defaultAutoCommit, int defaultTransactionIsolation) {
        _connFactory = connFactory;
        _pool = pool;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _validationQueryTimeout = validationQueryTimeout;
        _connectionInitSqls = connectionInitSqls;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
        _defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param config the AbandonedConfig if tracing SQL objects
     */
    public PoolableConnectionFactory(
        ConnectionFactory connFactory,
        ObjectPool<PoolableConnection> pool,
        KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory,
        String validationQuery,
        boolean defaultReadOnly,
        boolean defaultAutoCommit,
        AbandonedConfig config) {

        _connFactory = connFactory;
        _pool = pool;
        _config = config;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     * @param config the AbandonedConfig if tracing SQL objects
     */
    public PoolableConnectionFactory(
        ConnectionFactory connFactory,
        ObjectPool<PoolableConnection> pool,
        KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory,
        String validationQuery,
        boolean defaultReadOnly,
        boolean defaultAutoCommit,
        int defaultTransactionIsolation,
        AbandonedConfig config) {

        _connFactory = connFactory;
        _pool = pool;
        _config = config;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
        _defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     * @param defaultCatalog the default "catalog" setting for returned {@link Connection}s
     * @param config the AbandonedConfig if tracing SQL objects
     */
    public PoolableConnectionFactory(
        ConnectionFactory connFactory,
        ObjectPool<PoolableConnection> pool,
        KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory,
        String validationQuery,
        boolean defaultReadOnly,
        boolean defaultAutoCommit,
        int defaultTransactionIsolation,
        String defaultCatalog,
        AbandonedConfig config) {

        _connFactory = connFactory;
        _pool = pool;
        _config = config;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
        _defaultAutoCommit = defaultAutoCommit;
        _defaultTransactionIsolation = defaultTransactionIsolation;
        _defaultCatalog = defaultCatalog;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     * @param defaultCatalog the default "catalog" setting for returned {@link Connection}s
     * @param config the AbandonedConfig if tracing SQL objects
     */
    public PoolableConnectionFactory(
        ConnectionFactory connFactory,
        ObjectPool<PoolableConnection> pool,
        KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory,
        String validationQuery,
        Boolean defaultReadOnly,
        boolean defaultAutoCommit,
        int defaultTransactionIsolation,
        String defaultCatalog,
        AbandonedConfig config) {

        _connFactory = connFactory;
        _pool = pool;
        _config = config;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _defaultReadOnly = defaultReadOnly;
        _defaultAutoCommit = defaultAutoCommit;
        _defaultTransactionIsolation = defaultTransactionIsolation;
        _defaultCatalog = defaultCatalog;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param connectionInitSqls a Collection of SQL statements to initialize {@link Connection}s. Using <code>null</code> turns off initialization.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     * @param defaultCatalog the default "catalog" setting for returned {@link Connection}s
     * @param config the AbandonedConfig if tracing SQL objects
     * @since 1.3
     */
    public PoolableConnectionFactory(
        ConnectionFactory connFactory,
        ObjectPool<PoolableConnection> pool,
        KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory,
        String validationQuery,
        Collection<String> connectionInitSqls,
        Boolean defaultReadOnly,
        boolean defaultAutoCommit,
        int defaultTransactionIsolation,
        String defaultCatalog,
        AbandonedConfig config) {

        _connFactory = connFactory;
        _pool = pool;
        _config = config;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _connectionInitSqls = connectionInitSqls;
        _defaultReadOnly = defaultReadOnly;
        _defaultAutoCommit = defaultAutoCommit;
        _defaultTransactionIsolation = defaultTransactionIsolation;
        _defaultCatalog = defaultCatalog;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param validationQueryTimeout the number of seconds that validation queries will wait for database response before failing.  Use a value less than or equal to 0 for no timeout.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     * @param defaultCatalog the default "catalog" setting for returned {@link Connection}s
     * @param config the AbandonedConfig if tracing SQL objects
     * @since 1.3
     */
    public PoolableConnectionFactory(
        ConnectionFactory connFactory,
        ObjectPool<PoolableConnection> pool,
        KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory,
        String validationQuery,
        int validationQueryTimeout,
        Boolean defaultReadOnly,
        boolean defaultAutoCommit,
        int defaultTransactionIsolation,
        String defaultCatalog,
        AbandonedConfig config) {

        _connFactory = connFactory;
        _pool = pool;
        _config = config;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _validationQueryTimeout = validationQueryTimeout;
        _defaultReadOnly = defaultReadOnly;
        _defaultAutoCommit = defaultAutoCommit;
        _defaultTransactionIsolation = defaultTransactionIsolation;
        _defaultCatalog = defaultCatalog;
    }

    /**
     * Create a new <code>PoolableConnectionFactory</code>.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s, or <code>null</code> to disable {@link java.sql.PreparedStatement} pooling
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.  Should return at least one row. Using <code>null</code> turns off validation.
     * @param validationQueryTimeout the number of seconds that validation queries will wait for database response before failing.  Use a value less than or equal to 0 for no timeout.
     * @param connectionInitSqls a Collection of SQL statements to initialize {@link Connection}s. Using <code>null</code> turns off initialization.
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for returned {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     * @param defaultCatalog the default "catalog" setting for returned {@link Connection}s
     * @param config the AbandonedConfig if tracing SQL objects
     * @since 1.3
     */
    public PoolableConnectionFactory(
        ConnectionFactory connFactory,
        ObjectPool<PoolableConnection> pool,
        KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory,
        String validationQuery,
        int validationQueryTimeout,
        Collection<String> connectionInitSqls,
        Boolean defaultReadOnly,
        boolean defaultAutoCommit,
        int defaultTransactionIsolation,
        String defaultCatalog,
        AbandonedConfig config) {

        _connFactory = connFactory;
        _pool = pool;
        _config = config;
        _pool.setFactory(this);
        _stmtPoolFactory = stmtPoolFactory;
        _validationQuery = validationQuery;
        _validationQueryTimeout = validationQueryTimeout;
        _connectionInitSqls = connectionInitSqls;
        _defaultReadOnly = defaultReadOnly;
        _defaultAutoCommit = defaultAutoCommit;
        _defaultTransactionIsolation = defaultTransactionIsolation;
        _defaultCatalog = defaultCatalog;
    }

    /**
     * Sets the {@link ConnectionFactory} from which to obtain base {@link Connection}s.
     * @param connFactory the {@link ConnectionFactory} from which to obtain base {@link Connection}s
     */
    public void setConnectionFactory(ConnectionFactory connFactory) {
        _connFactory = connFactory;
    }

    /**
     * Sets the query I use to {@link #validateObject validate} {@link Connection}s.
     * Should return at least one row.
     * Using <code>null</code> turns off validation.
     * @param validationQuery a query to use to {@link #validateObject validate} {@link Connection}s.
     */
    public void setValidationQuery(String validationQuery) {
        _validationQuery = validationQuery;
    }

    /**
     * Sets the validation query timeout, the amount of time, in seconds, that
     * connection validation will wait for a response from the database when
     * executing a validation query.  Use a value less than or equal to 0 for
     * no timeout.
     *
     * @param timeout new validation query timeout value in seconds
     * @since 1.3
     */
    public void setValidationQueryTimeout(int timeout) {
        _validationQueryTimeout = timeout;
    }

    /**
     * Sets the SQL statements I use to initialize newly created {@link Connection}s.
     * Using <code>null</code> turns off connection initialization.
     * @param connectionInitSqls SQL statement to initialize {@link Connection}s.
     * @since 1.3
     */
    synchronized public void setConnectionInitSql(Collection<String> connectionInitSqls) {
        _connectionInitSqls = connectionInitSqls;
    }

    /**
     * Sets the {@link ObjectPool} in which to pool {@link Connection}s.
     * @param pool the {@link ObjectPool} in which to pool those {@link Connection}s
     */
    synchronized public void setPool(ObjectPool<PoolableConnection> pool) {
        if(null != _pool && pool != _pool) {
            try {
                _pool.close();
            } catch(Exception e) {
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
     * Sets the {@link KeyedObjectPoolFactory} I use to create {@link KeyedObjectPool}s
     * for pooling {@link java.sql.PreparedStatement}s.
     * Set to <code>null</code> to disable {@link java.sql.PreparedStatement} pooling.
     * @param stmtPoolFactory the {@link KeyedObjectPoolFactory} to use to create {@link KeyedObjectPool}s for pooling {@link java.sql.PreparedStatement}s
     */
    public void setStatementPoolFactory(KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> stmtPoolFactory) {
        _stmtPoolFactory = stmtPoolFactory;
    }

    /**
     * Sets the default "read only" setting for borrowed {@link Connection}s
     * @param defaultReadOnly the default "read only" setting for borrowed {@link Connection}s
     */
    public void setDefaultReadOnly(boolean defaultReadOnly) {
        _defaultReadOnly = defaultReadOnly ? Boolean.TRUE : Boolean.FALSE;
    }

    /**
     * Sets the default "auto commit" setting for borrowed {@link Connection}s
     * @param defaultAutoCommit the default "auto commit" setting for borrowed {@link Connection}s
     */
    public void setDefaultAutoCommit(boolean defaultAutoCommit) {
        _defaultAutoCommit = defaultAutoCommit;
    }

    /**
     * Sets the default "Transaction Isolation" setting for borrowed {@link Connection}s
     * @param defaultTransactionIsolation the default "Transaction Isolation" setting for returned {@link Connection}s
     */
    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        _defaultTransactionIsolation = defaultTransactionIsolation;
    }

    /**
     * Sets the default "catalog" setting for borrowed {@link Connection}s
     * @param defaultCatalog the default "catalog" setting for borrowed {@link Connection}s
     */
    public void setDefaultCatalog(String defaultCatalog) {
        _defaultCatalog = defaultCatalog;
    }

    @Override
    public PoolableConnection makeObject() throws Exception {
        Connection conn = _connFactory.createConnection();
        if (conn == null) {
            throw new IllegalStateException("Connection factory returned null from createConnection");
        }
        initializeConnection(conn);
        if(null != _stmtPoolFactory) {
            KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> stmtpool = _stmtPoolFactory.createPool();
            conn = new PoolingConnection(conn,stmtpool);
            stmtpool.setFactory((PoolingConnection)conn);
        }
        return new PoolableConnection(conn,_pool,_config);
    }

    protected void initializeConnection(Connection conn) throws SQLException {
        Collection<String> sqls = _connectionInitSqls;
        if(conn.isClosed()) {
            throw new SQLException("initializeConnection: connection closed");
        }
        if(null != sqls) {
            Statement stmt = null;
            try {
                stmt = conn.createStatement();
                for (Iterator<String> iterator = sqls.iterator(); iterator.hasNext();)
                {
                    String sql = iterator.next();
                    if (sql == null) {
                        throw new NullPointerException("null connectionInitSqls element");
                    }
                    stmt.execute(sql);
                }
            } finally {
                if (stmt != null) {
                    try {
                        stmt.close();
                    } catch(Exception t) {
                        // ignored
                    }
                }
            }
        }
    }

    @Override
    public void destroyObject(PoolableConnection obj) throws Exception {
        if (obj != null) {
            obj.reallyClose();
        }
    }

    @Override
    public boolean validateObject(PoolableConnection obj) {
        try {
            validateConnection(obj);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public void validateConnection(Connection conn) throws SQLException {
        String query = _validationQuery;
        if(conn.isClosed()) {
            throw new SQLException("validateConnection: connection closed");
        }
        if(null != query) {
            Statement stmt = null;
            ResultSet rset = null;
            try {
                stmt = conn.createStatement();
                if (_validationQueryTimeout > 0) {
                    stmt.setQueryTimeout(_validationQueryTimeout);
                }
                rset = stmt.executeQuery(query);
                if(!rset.next()) {
                    throw new SQLException("validationQuery didn't return a row");
                }
            } finally {
                if (rset != null) {
                    try {
                        rset.close();
                    } catch(Exception t) {
                        // ignored
                    }
                }
                if (stmt != null) {
                    try {
                        stmt.close();
                    } catch(Exception t) {
                        // ignored
                    }
                }
            }
        }
    }

    @Override
    public void passivateObject(PoolableConnection conn) throws Exception {
        if(!conn.getAutoCommit() && !conn.isReadOnly()) {
            conn.rollback();
        }
        conn.clearWarnings();
        if(!conn.getAutoCommit()) {
            conn.setAutoCommit(true);
        }
        conn.passivate();
    }

    @Override
    public void activateObject(PoolableConnection conn) throws Exception {
        conn.activate();
        if (conn.getAutoCommit() != _defaultAutoCommit) {
            conn.setAutoCommit(_defaultAutoCommit);
        }
        if ((_defaultTransactionIsolation != UNKNOWN_TRANSACTIONISOLATION)
                && (conn.getTransactionIsolation() !=
                _defaultTransactionIsolation)) {
            conn.setTransactionIsolation(_defaultTransactionIsolation);
        }
        if ((_defaultReadOnly != null) &&
                (conn.isReadOnly() != _defaultReadOnly.booleanValue())) {
            conn.setReadOnly(_defaultReadOnly.booleanValue());
        }
        if ((_defaultCatalog != null) &&
                (!_defaultCatalog.equals(conn.getCatalog()))) {
            conn.setCatalog(_defaultCatalog);
        }
    }

    protected volatile ConnectionFactory _connFactory = null;
    protected volatile String _validationQuery = null;
    protected volatile int _validationQueryTimeout = -1;
    protected Collection<String> _connectionInitSqls = null;
    protected volatile ObjectPool<PoolableConnection> _pool = null;
    protected volatile KeyedObjectPoolFactory<PStmtKey, DelegatingPreparedStatement> _stmtPoolFactory = null;
    protected Boolean _defaultReadOnly = null;
    protected boolean _defaultAutoCommit = true;
    protected int _defaultTransactionIsolation = UNKNOWN_TRANSACTIONISOLATION;
    protected String _defaultCatalog;

    /**
     * Configuration for removing abandoned connections.
     */
    protected AbandonedConfig _config = null;

    /**
     * Internal constant to indicate the level is not set.
     */
    static final int UNKNOWN_TRANSACTIONISOLATION = -1;
}

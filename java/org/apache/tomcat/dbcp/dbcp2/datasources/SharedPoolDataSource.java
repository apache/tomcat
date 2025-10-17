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
package org.apache.tomcat.dbcp.dbcp2.datasources;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.sql.ConnectionPoolDataSource;

import org.apache.tomcat.dbcp.pool2.KeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 * <p>
 * A pooling {@code DataSource} appropriate for deployment within J2EE environment. There are many configuration
 * options, most of which are defined in the parent class. All users (based on user name) share a single maximum number
 * of Connections in this data source.
 * </p>
 *
 * <p>
 * User passwords can be changed without re-initializing the data source. When a
 * {@code getConnection(user name, password)} request is processed with a password that is different from those
 * used to create connections in the pool associated with {@code user name}, an attempt is made to create a new
 * connection using the supplied password and if this succeeds, idle connections created using the old password are
 * destroyed and new connections are created using the new password.
 * </p>
 *
 * @since 2.0
 */
public class SharedPoolDataSource extends InstanceKeyDataSource {

    private static final long serialVersionUID = -1458539734480586454L;

    /**
     * Max total defaults to {@link GenericKeyedObjectPoolConfig#DEFAULT_MAX_TOTAL}.
     */
    private int maxTotal = GenericKeyedObjectPoolConfig.DEFAULT_MAX_TOTAL;

    /**
     * Maps user credentials to pooled connection with credentials.
     */
    private transient KeyedObjectPool<UserPassKey, PooledConnectionAndInfo> pool;

    /**
     * A {@link KeyedPooledObjectFactory} that creates {@link PoolableConnection}s.
     */
    private transient KeyedCPDSConnectionFactory factory;

    /**
     * Default no-argument constructor for Serialization
     */
    public SharedPoolDataSource() {
        // empty.
    }

    /**
     * Closes pool being maintained by this data source.
     */
    @Override
    public void close() throws SQLException {
        if (pool != null) {
            pool.close();
        }
        InstanceKeyDataSourceFactory.removeInstance(getInstanceKey());
    }

    @Override
    protected PooledConnectionManager getConnectionManager(final UserPassKey userPassKey) {
        return factory;
    }

    /**
     * Gets {@link GenericKeyedObjectPool#getMaxTotal()} for this pool.
     *
     * @return {@link GenericKeyedObjectPool#getMaxTotal()} for this pool.
     */
    public int getMaxTotal() {
        return this.maxTotal;
    }

    /**
     * Gets the number of active connections in the pool.
     *
     * @return The number of active connections in the pool.
     */
    public int getNumActive() {
        return pool == null ? 0 : pool.getNumActive();
    }

    /**
     * Gets the number of idle connections in the pool.
     *
     * @return The number of idle connections in the pool.
     */
    public int getNumIdle() {
        return pool == null ? 0 : pool.getNumIdle();
    }

    @Override
    protected PooledConnectionAndInfo getPooledConnectionAndInfo(final String userName, final String userPassword)
            throws SQLException {

        synchronized (this) {
            if (pool == null) {
                try {
                    registerPool(userName, userPassword);
                } catch (final NamingException e) {
                    throw new SQLException("registerPool failed", e);
                }
            }
        }

        try {
            return pool.borrowObject(new UserPassKey(userName, userPassword));
        } catch (final Exception e) {
            throw new SQLException("Could not retrieve connection info from pool", e);
        }
    }

    /**
     * Creates a new {@link Reference} to a {@link SharedPoolDataSource}.
     */
    @Override
    public Reference getReference() throws NamingException {
        final Reference ref = new Reference(getClass().getName(), SharedPoolDataSourceFactory.class.getName(), null);
        ref.add(new StringRefAddr("instanceKey", getInstanceKey()));
        return ref;
    }

    /**
     * Deserializes an instance from an ObjectInputStream.
     *
     * @param in The source ObjectInputStream.
     * @throws IOException            Any of the usual Input/Output related exceptions.
     * @throws ClassNotFoundException A class of a serialized object cannot be found.
     */
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.pool = readObjectImpl();
    }

    private KeyedObjectPool<UserPassKey, PooledConnectionAndInfo> readObjectImpl() throws IOException, ClassNotFoundException {
        try {
            return ((SharedPoolDataSource) new SharedPoolDataSourceFactory().getObjectInstance(getReference(), null, null, null)).pool;
        } catch (final NamingException e) {
            throw new IOException("NamingException: " + e);
        }
    }

    private void registerPool(final String userName, final String password) throws NamingException, SQLException {

        final ConnectionPoolDataSource cpds = testCPDS(userName, password);

        // Create an object pool to contain our PooledConnections
        factory = new KeyedCPDSConnectionFactory(cpds, getValidationQuery(), getValidationQueryTimeoutDuration(), isRollbackAfterValidation());
        factory.setMaxConn(getMaxConnDuration());

        final GenericKeyedObjectPoolConfig<PooledConnectionAndInfo> config = new GenericKeyedObjectPoolConfig<>();
        config.setBlockWhenExhausted(getDefaultBlockWhenExhausted());
        config.setEvictionPolicyClassName(getDefaultEvictionPolicyClassName());
        config.setLifo(getDefaultLifo());
        config.setMaxIdlePerKey(getDefaultMaxIdle());
        config.setMaxTotal(getMaxTotal());
        config.setMaxTotalPerKey(getDefaultMaxTotal());
        config.setMaxWait(getDefaultMaxWait());
        config.setMinEvictableIdleDuration(getDefaultMinEvictableIdleDuration());
        config.setMinIdlePerKey(getDefaultMinIdle());
        config.setNumTestsPerEvictionRun(getDefaultNumTestsPerEvictionRun());
        config.setSoftMinEvictableIdleDuration(getDefaultSoftMinEvictableIdleDuration());
        config.setTestOnCreate(getDefaultTestOnCreate());
        config.setTestOnBorrow(getDefaultTestOnBorrow());
        config.setTestOnReturn(getDefaultTestOnReturn());
        config.setTestWhileIdle(getDefaultTestWhileIdle());
        config.setTimeBetweenEvictionRuns(getDefaultDurationBetweenEvictionRuns());

        final KeyedObjectPool<UserPassKey, PooledConnectionAndInfo> tmpPool = new GenericKeyedObjectPool<>(factory, config);
        factory.setPool(tmpPool);
        pool = tmpPool;
    }

    /**
     * Sets {@link GenericKeyedObjectPool#getMaxTotal()} for this pool.
     *
     * @param maxTotal
     *            {@link GenericKeyedObjectPool#getMaxTotal()} for this pool.
     */
    public void setMaxTotal(final int maxTotal) {
        assertInitializationAllowed();
        this.maxTotal = maxTotal;
    }

    @Override
    protected void setupDefaults(final Connection connection, final String userName) throws SQLException {
        final Boolean defaultAutoCommit = isDefaultAutoCommit();
        if (defaultAutoCommit != null && connection.getAutoCommit() != defaultAutoCommit.booleanValue()) {
            connection.setAutoCommit(defaultAutoCommit.booleanValue());
        }

        final int defaultTransactionIsolation = getDefaultTransactionIsolation();
        if (defaultTransactionIsolation != UNKNOWN_TRANSACTIONISOLATION) {
            connection.setTransactionIsolation(defaultTransactionIsolation);
        }

        final Boolean defaultReadOnly = isDefaultReadOnly();
        if (defaultReadOnly != null && connection.isReadOnly() != defaultReadOnly.booleanValue()) {
            connection.setReadOnly(defaultReadOnly.booleanValue());
        }
    }

    @Override
    protected void toStringFields(final StringBuilder builder) {
        super.toStringFields(builder);
        builder.append(", maxTotal=");
        builder.append(maxTotal);
    }
}

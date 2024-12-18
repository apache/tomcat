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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.sql.ConnectionPoolDataSource;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.dbcp2.SwallowedExceptionLogger;
import org.apache.tomcat.dbcp.dbcp2.Utils;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.EvictionPolicy;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool;

/**
 * <p>
 * A pooling {@code DataSource} appropriate for deployment within J2EE environment. There are many configuration
 * options, most of which are defined in the parent class. This datasource uses individual pools per user, and some
 * properties can be set specifically for a given user, if the deployment environment can support initialization of
 * mapped properties. So for example, a pool of admin or write-access Connections can be guaranteed a certain number of
 * connections, separate from a maximum set for users with read-only connections.
 * </p>
 *
 * <p>
 * User passwords can be changed without re-initializing the datasource. When a
 * {@code getConnection(userName, password)} request is processed with a password that is different from those used
 * to create connections in the pool associated with {@code userName}, an attempt is made to create a new
 * connection using the supplied password and if this succeeds, the existing pool is cleared and a new pool is created
 * for connections using the new password.
 * </p>
 *
 * @since 2.0
 */
public class PerUserPoolDataSource extends InstanceKeyDataSource {

    private static final long serialVersionUID = 7872747993848065028L;

    private static final Log log = LogFactory.getLog(PerUserPoolDataSource.class);

    private static <K, V> HashMap<K, V> createMap() {
        // Should there be a default size different from what this ctor provides?
        return new HashMap<>();
    }

    /**
     * Maps user names to a data source property: BlockWhenExhausted.
     */
    private Map<String, Boolean> perUserBlockWhenExhausted;

    /**
     * Maps user names to a data source property: EvictionPolicyClassName.
     */
    private Map<String, String> perUserEvictionPolicyClassName;

    /**
     * Maps user names to a data source property: Lifo.
     */
    private Map<String, Boolean> perUserLifo;

    /**
     * Maps user names to a data source property: MaxIdle.
     */
    private Map<String, Integer> perUserMaxIdle;

    /**
     * Maps user names to a data source property: MaxTotal.
     */
    private Map<String, Integer> perUserMaxTotal;

    /**
     * Maps user names to a data source property: MaxWaitDuration.
     */
    private Map<String, Duration> perUserMaxWaitDuration;

    /**
     * Maps user names to a data source property: MinEvictableIdleDuration.
     */
    private Map<String, Duration> perUserMinEvictableIdleDuration;

    /**
     * Maps user names to a data source property: MinIdle.
     */
    private Map<String, Integer> perUserMinIdle;

    /**
     * Maps user names to a data source property: NumTestsPerEvictionRun.
     */
    private Map<String, Integer> perUserNumTestsPerEvictionRun;

    /**
     * Maps user names to a data source property: SoftMinEvictableIdleDuration.
     */
    private Map<String, Duration> perUserSoftMinEvictableIdleDuration;

    /**
     * Maps user names to a data source property: TestOnCreate.
     */
    private Map<String, Boolean> perUserTestOnCreate;

    /**
     * Maps user names to a data source property: TestOnBorrow.
     */
    private Map<String, Boolean> perUserTestOnBorrow;

    /**
     * Maps user names to a data source property: TestOnReturn.
     */
    private Map<String, Boolean> perUserTestOnReturn;

    /**
     * Maps user names to a data source property: TestWhileIdle.
     */
    private Map<String, Boolean> perUserTestWhileIdle;

    /**
     * Maps user names to a data source property: DurationBetweenEvictionRuns.
     */
    private Map<String, Duration> perUserDurationBetweenEvictionRuns;

    /**
     * Maps user names to a data source property: DefaultAutoCommit.
     */
    private Map<String, Boolean> perUserDefaultAutoCommit;

    /**
     * Maps user names to a data source property: DefaultTransactionIsolation.
     */
    private Map<String, Integer> perUserDefaultTransactionIsolation;

    /**
     * Maps user names to a data source property: DefaultReadOnly.
     */
    private Map<String, Boolean> perUserDefaultReadOnly;

    /**
     * Map to keep track of Pools for a given user.
     */
    private transient Map<PoolKey, PooledConnectionManager> managers = createMap();

    /**
     * Constructs a new instance.
     */
    public PerUserPoolDataSource() {
    }

    /**
     * Clears pool(s) maintained by this data source.
     *
     * @see org.apache.tomcat.dbcp.pool2.ObjectPool#clear()
     * @since 2.3.0
     */
    public void clear() {
        managers.values().forEach(manager -> {
            try {
                getCPDSConnectionFactoryPool(manager).clear();
            } catch (final Exception ignored) {
                // ignore and try to close others.
            }
        });
        InstanceKeyDataSourceFactory.removeInstance(getInstanceKey());
    }

    /**
     * Closes pool(s) maintained by this data source.
     *
     * @see org.apache.tomcat.dbcp.pool2.ObjectPool#close()
     */
    @Override
    public void close() {
        managers.values().forEach(manager -> Utils.closeQuietly(getCPDSConnectionFactoryPool(manager)));
        InstanceKeyDataSourceFactory.removeInstance(getInstanceKey());
    }

    /**
     * Converts a map with Long milliseconds values to another map with Duration values.
     */
    private Map<String, Duration> convertMap(final Map<String, Duration> currentMap, final Map<String, Long> longMap) {
        final Map<String, Duration> durationMap = createMap();
        longMap.forEach((k, v) -> durationMap.put(k, toDurationOrNull(v)));
        if (currentMap == null) {
            return durationMap;
        }
        currentMap.clear();
        currentMap.putAll(durationMap);
        return currentMap;

    }

    /**
     * Gets the user specific default value in a map for the specified user's pool.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    private <V> V get(final Map<String, V> map, final String userName) {
        return map != null ? map.get(userName) : null;
    }

    /**
     * Gets the user specific default value in a map for the specified user's pool.
     *
     * @param userName The user name key.
     * @return The user specific value.
     */
    private <V> V get(final Map<String, V> map, final String userName, final Supplier<V> defaultSupplier) {
        final V v = get(map, userName);
        return v != null ? v : defaultSupplier.get();
    }

    @Override
    protected PooledConnectionManager getConnectionManager(final UserPassKey upKey) {
        return managers.get(getPoolKey(upKey.getUserName()));
    }

    /**
     * Gets the underlying pre-allocated pool (does NOT allocate).
     *
     * @param manager A CPDSConnectionFactory.
     * @return the underlying pool.
     */
    private ObjectPool<PooledConnectionAndInfo> getCPDSConnectionFactoryPool(final PooledConnectionManager manager) {
        return ((CPDSConnectionFactory) manager).getPool();
    }

    /**
     * Gets the number of active connections in the default pool.
     *
     * @return The number of active connections in the default pool.
     */
    public int getNumActive() {
        return getNumActive(null);
    }

    /**
     * Gets the number of active connections in the pool for a given user.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public int getNumActive(final String userName) {
        final ObjectPool<PooledConnectionAndInfo> pool = getPool(getPoolKey(userName));
        return pool == null ? 0 : pool.getNumActive();
    }

    /**
     * Gets the number of idle connections in the default pool.
     *
     * @return The number of idle connections in the default pool.
     */
    public int getNumIdle() {
        return getNumIdle(null);
    }

    /**
     * Gets the number of idle connections in the pool for a given user.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public int getNumIdle(final String userName) {
        final ObjectPool<PooledConnectionAndInfo> pool = getPool(getPoolKey(userName));
        return pool == null ? 0 : pool.getNumIdle();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getBlockWhenExhausted()} for the specified user's pool
     * or the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserBlockWhenExhausted(final String userName) {
        return get(perUserBlockWhenExhausted, userName, this::getDefaultBlockWhenExhausted).booleanValue();
    }

    /**
     * Gets the user specific default value for {@link Connection#setAutoCommit(boolean)} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public Boolean getPerUserDefaultAutoCommit(final String userName) {
        return get(perUserDefaultAutoCommit, userName);
    }

    /**
     * Gets the user specific default value for {@link Connection#setReadOnly(boolean)} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public Boolean getPerUserDefaultReadOnly(final String userName) {
        return get(perUserDefaultReadOnly, userName);
    }

    /**
     * Gets the user specific default value for {@link Connection#setTransactionIsolation(int)} for the specified user's
     * pool.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public Integer getPerUserDefaultTransactionIsolation(final String userName) {
        return get(perUserDefaultTransactionIsolation, userName);
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     * @since 2.10.0
     */
    public Duration getPerUserDurationBetweenEvictionRuns(final String userName) {
        return get(perUserDurationBetweenEvictionRuns, userName, this::getDefaultDurationBetweenEvictionRuns);
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getEvictionPolicyClassName()} for the specified user's
     * pool or the default if no user specific value is defined.
     * <p>
     * The class must implement {@link EvictionPolicy}.
     * </p>
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public String getPerUserEvictionPolicyClassName(final String userName) {
        return get(perUserEvictionPolicyClassName, userName, this::getDefaultEvictionPolicyClassName);
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getLifo()} for the specified user's pool or the default
     * if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserLifo(final String userName) {
        return get(perUserLifo, userName, this::getDefaultLifo).booleanValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMaxIdle()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public int getPerUserMaxIdle(final String userName) {
        return get(perUserMaxIdle, userName, this::getDefaultMaxIdle).intValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMaxTotal()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public int getPerUserMaxTotal(final String userName) {
        return get(perUserMaxTotal, userName, this::getDefaultMaxTotal).intValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMaxWaitDuration()} for the specified user's pool or
     * the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     * @since 2.10.0
     */
    public Duration getPerUserMaxWaitDuration(final String userName) {
        return get(perUserMaxWaitDuration, userName, this::getDefaultMaxWait);
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMaxWaitDuration()} for the specified user's pool or
     * the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     * @deprecated Use {@link #getPerUserMaxWaitDuration}.
     */
    @Deprecated
    public long getPerUserMaxWaitMillis(final String userName) {
        return getPerUserMaxWaitDuration(userName).toMillis();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMinEvictableIdleDuration()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value, never null.
     * @since 2.10.0
     */
    public Duration getPerUserMinEvictableIdleDuration(final String userName) {
        return get(perUserMinEvictableIdleDuration, userName, this::getDefaultMinEvictableIdleDuration);
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMinEvictableIdleDuration()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     * @deprecated Use {@link #getPerUserMinEvictableIdleDuration(String)}.
     */
    @Deprecated
    public long getPerUserMinEvictableIdleTimeMillis(final String userName) {
        return getPerUserMinEvictableIdleDuration(userName).toMillis();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getMinIdle()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public int getPerUserMinIdle(final String userName) {
        return get(perUserMinIdle, userName, this::getDefaultMinIdle).intValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getNumTestsPerEvictionRun()} for the specified user's
     * pool or the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public int getPerUserNumTestsPerEvictionRun(final String userName) {
        return get(perUserNumTestsPerEvictionRun, userName, this::getDefaultNumTestsPerEvictionRun).intValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getSoftMinEvictableIdleDuration()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     * @since 2.10.0
     */
    public Duration getPerUserSoftMinEvictableIdleDuration(final String userName) {
        return get(perUserSoftMinEvictableIdleDuration, userName, this::getDefaultSoftMinEvictableIdleDuration);
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getSoftMinEvictableIdleDuration()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     * @deprecated Use {@link #getPerUserSoftMinEvictableIdleDuration(String)}.
     */
    @Deprecated
    public long getPerUserSoftMinEvictableIdleTimeMillis(final String userName) {
        return getPerUserSoftMinEvictableIdleDuration(userName).toMillis();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getTestOnBorrow()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserTestOnBorrow(final String userName) {
        return get(perUserTestOnBorrow, userName, this::getDefaultTestOnBorrow).booleanValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getTestOnCreate()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserTestOnCreate(final String userName) {
        return get(perUserTestOnCreate, userName, this::getDefaultTestOnCreate).booleanValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getTestOnReturn()} for the specified user's pool or the
     * default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserTestOnReturn(final String userName) {
        return get(perUserTestOnReturn, userName, this::getDefaultTestOnReturn).booleanValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getTestWhileIdle()} for the specified user's pool or
     * the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     */
    public boolean getPerUserTestWhileIdle(final String userName) {
        return get(perUserTestWhileIdle, userName, this::getDefaultTestWhileIdle).booleanValue();
    }

    /**
     * Gets the user specific value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for the specified
     * user's pool or the default if no user specific value is defined.
     *
     * @param userName
     *            The user name key.
     * @return The user specific value.
     * @deprecated Use {@link #getPerUserDurationBetweenEvictionRuns(String)}.
     */
    @Deprecated
    public long getPerUserTimeBetweenEvictionRunsMillis(final String userName) {
        return getPerUserDurationBetweenEvictionRuns(userName).toMillis();
    }

    /**
     * Returns the object pool associated with the given PoolKey.
     *
     * @param poolKey
     *            PoolKey identifying the pool
     * @return the GenericObjectPool pooling connections for the userName and datasource specified by the PoolKey
     */
    private ObjectPool<PooledConnectionAndInfo> getPool(final PoolKey poolKey) {
        final CPDSConnectionFactory mgr = (CPDSConnectionFactory) managers.get(poolKey);
        return mgr == null ? null : mgr.getPool();
    }

    @Override
    protected PooledConnectionAndInfo getPooledConnectionAndInfo(final String userName, final String password) throws SQLException {
        final PoolKey key = getPoolKey(userName);
        ObjectPool<PooledConnectionAndInfo> pool;
        PooledConnectionManager manager;
        synchronized (this) {
            manager = managers.get(key);
            if (manager == null) {
                try {
                    registerPool(userName, password);
                    manager = managers.get(key);
                } catch (final NamingException e) {
                    throw new SQLException("RegisterPool failed", e);
                }
            }
            pool = getCPDSConnectionFactoryPool(manager);
        }
        PooledConnectionAndInfo info = null;
        try {
            info = pool.borrowObject();
        } catch (final NoSuchElementException ex) {
            throw new SQLException("Could not retrieve connection info from pool", ex);
        } catch (final Exception e) {
            // See if failure is due to CPDSConnectionFactory authentication failure
            try {
                testCPDS(userName, password);
            } catch (final Exception ex) {
                throw new SQLException("Could not retrieve connection info from pool", ex);
            }
            // New password works, so kill the old pool, create a new one, and borrow
            manager.closePool(userName);
            synchronized (this) {
                managers.remove(key);
            }
            try {
                registerPool(userName, password);
                pool = getPool(key);
            } catch (final NamingException ne) {
                throw new SQLException("RegisterPool failed", ne);
            }
            try {
                info = pool.borrowObject();
            } catch (final Exception ex) {
                throw new SQLException("Could not retrieve connection info from pool", ex);
            }
        }
        return info;
    }

    /**
     * Creates a pool key from the provided parameters.
     *
     * @param userName
     *            User name
     * @return The pool key
     */
    private PoolKey getPoolKey(final String userName) {
        return new PoolKey(getDataSourceName(), userName);
    }

    /**
     * Returns a {@code PerUserPoolDataSource} {@link Reference}.
     */
    @Override
    public Reference getReference() throws NamingException {
        final Reference ref = new Reference(getClass().getName(), PerUserPoolDataSourceFactory.class.getName(), null);
        ref.add(new StringRefAddr("instanceKey", getInstanceKey()));
        return ref;
    }

    <K, V> Map<K, V> put(Map<K, V> map, final K key, final V value) {
        if (map == null) {
            map = createMap();
        }
        map.put(key, value);
        return map;
    }

    /**
     * Deserializes an instance from an ObjectInputStream.
     *
     * @param in The source ObjectInputStream.
     * @throws IOException            Any of the usual Input/Output related exceptions.
     * @throws ClassNotFoundException A class of a serialized object cannot be found.
     */
    @SuppressWarnings("resource")
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.managers = readObjectImpl().managers;
    }

    private PerUserPoolDataSource readObjectImpl() throws IOException, ClassNotFoundException {
        try {
            return (PerUserPoolDataSource) new PerUserPoolDataSourceFactory().getObjectInstance(getReference(), null, null, null);
        } catch (final NamingException e) {
            throw new IOException("NamingException: " + e);
        }
    }

    private synchronized void registerPool(final String userName, final String password) throws NamingException, SQLException {
        final ConnectionPoolDataSource cpds = testCPDS(userName, password);
        // Set up the factory we will use (passing the pool associates
        // the factory with the pool, so we do not have to do so
        // explicitly)
        final CPDSConnectionFactory factory = new CPDSConnectionFactory(cpds, getValidationQuery(), getValidationQueryTimeoutDuration(),
                isRollbackAfterValidation(), userName, password);
        factory.setMaxConn(getMaxConnDuration());
        // Create an object pool to contain our PooledConnections
        final GenericObjectPool<PooledConnectionAndInfo> pool = new GenericObjectPool<>(factory);
        factory.setPool(pool);
        pool.setBlockWhenExhausted(getPerUserBlockWhenExhausted(userName));
        pool.setEvictionPolicyClassName(getPerUserEvictionPolicyClassName(userName));
        pool.setLifo(getPerUserLifo(userName));
        pool.setMaxIdle(getPerUserMaxIdle(userName));
        pool.setMaxTotal(getPerUserMaxTotal(userName));
        pool.setMaxWait(getPerUserMaxWaitDuration(userName));
        pool.setMinEvictableIdleDuration(getPerUserMinEvictableIdleDuration(userName));
        pool.setMinIdle(getPerUserMinIdle(userName));
        pool.setNumTestsPerEvictionRun(getPerUserNumTestsPerEvictionRun(userName));
        pool.setSoftMinEvictableIdleDuration(getPerUserSoftMinEvictableIdleDuration(userName));
        pool.setTestOnCreate(getPerUserTestOnCreate(userName));
        pool.setTestOnBorrow(getPerUserTestOnBorrow(userName));
        pool.setTestOnReturn(getPerUserTestOnReturn(userName));
        pool.setTestWhileIdle(getPerUserTestWhileIdle(userName));
        pool.setDurationBetweenEvictionRuns(getPerUserDurationBetweenEvictionRuns(userName));
        pool.setSwallowedExceptionListener(new SwallowedExceptionLogger(log));
        final PoolKey poolKey = getPoolKey(userName);
        if (managers.containsKey(poolKey)) {
            pool.close();
            throw new IllegalStateException("Pool already contains an entry for this user/password: " + userName);
        }
        managers.put(poolKey, factory);
    }

    private <K, V> Map<K, V> replaceAll(final Map<K, V> currentMap, final Map<K, V> newMap) {
        if (currentMap == null) {
            return new HashMap<>(newMap);
        }
        currentMap.clear();
        currentMap.putAll(newMap);
        return currentMap;
    }

    void setPerUserBlockWhenExhausted(final Map<String, Boolean> newMap) {
        assertInitializationAllowed();
        perUserBlockWhenExhausted = replaceAll(perUserBlockWhenExhausted, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getBlockWhenExhausted()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserBlockWhenExhausted(final String userName, final Boolean value) {
        assertInitializationAllowed();
        perUserBlockWhenExhausted = put(perUserBlockWhenExhausted, userName, value);
    }

    void setPerUserDefaultAutoCommit(final Map<String, Boolean> newMap) {
        assertInitializationAllowed();
        perUserDefaultAutoCommit = replaceAll(perUserDefaultAutoCommit, newMap);
    }

    /**
     * Sets a user specific default value for {@link Connection#setAutoCommit(boolean)} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserDefaultAutoCommit(final String userName, final Boolean value) {
        assertInitializationAllowed();
        perUserDefaultAutoCommit = put(perUserDefaultAutoCommit, userName, value);

    }

    void setPerUserDefaultReadOnly(final Map<String, Boolean> newMap) {
        assertInitializationAllowed();
        perUserDefaultReadOnly = replaceAll(perUserDefaultReadOnly, newMap);
    }

    /**
     * Sets a user specific default value for {@link Connection#setReadOnly(boolean)} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserDefaultReadOnly(final String userName, final Boolean value) {
        assertInitializationAllowed();
        perUserDefaultReadOnly = put(perUserDefaultReadOnly, userName, value);

    }

    void setPerUserDefaultTransactionIsolation(final Map<String, Integer> newMap) {
        assertInitializationAllowed();
        perUserDefaultTransactionIsolation = replaceAll(perUserDefaultTransactionIsolation, newMap);
    }

    /**
     * Sets a user specific default value for {@link Connection#setTransactionIsolation(int)} for the specified user's
     * pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserDefaultTransactionIsolation(final String userName, final Integer value) {
        assertInitializationAllowed();
        perUserDefaultTransactionIsolation = put(perUserDefaultTransactionIsolation, userName, value);

    }

    void setPerUserDurationBetweenEvictionRuns(final Map<String, Duration> newMap) {
        assertInitializationAllowed();
        perUserDurationBetweenEvictionRuns = replaceAll(perUserDurationBetweenEvictionRuns, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for the specified
     * user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     * @since 2.10.0
     */
    public void setPerUserDurationBetweenEvictionRuns(final String userName, final Duration value) {
        assertInitializationAllowed();
        perUserDurationBetweenEvictionRuns = put(perUserDurationBetweenEvictionRuns, userName, value);

    }

    void setPerUserEvictionPolicyClassName(final Map<String, String> newMap) {
        assertInitializationAllowed();
        perUserEvictionPolicyClassName = replaceAll(perUserEvictionPolicyClassName, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getEvictionPolicyClassName()} for the specified user's
     * pool.
     * <p>
     * The class must implement {@link EvictionPolicy}.
     * </p>
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserEvictionPolicyClassName(final String userName, final String value) {
        assertInitializationAllowed();
        perUserEvictionPolicyClassName = put(perUserEvictionPolicyClassName, userName, value);
    }

    void setPerUserLifo(final Map<String, Boolean> newMap) {
        assertInitializationAllowed();
        perUserLifo = replaceAll(perUserLifo, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getLifo()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserLifo(final String userName, final Boolean value) {
        assertInitializationAllowed();
        perUserLifo = put(perUserLifo, userName, value);
    }

    void setPerUserMaxIdle(final Map<String, Integer> newMap) {
        assertInitializationAllowed();
        perUserMaxIdle = replaceAll(perUserMaxIdle, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMaxIdle()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserMaxIdle(final String userName, final Integer value) {
        assertInitializationAllowed();
        perUserMaxIdle = put(perUserMaxIdle, userName, value);
    }

    void setPerUserMaxTotal(final Map<String, Integer> newMap) {
        assertInitializationAllowed();
        perUserMaxTotal = replaceAll(perUserMaxTotal, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMaxTotal()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserMaxTotal(final String userName, final Integer value) {
        assertInitializationAllowed();
        perUserMaxTotal = put(perUserMaxTotal, userName, value);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMaxWaitDuration()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     * @since 2.10.0
     */
    public void setPerUserMaxWait(final String userName, final Duration value) {
        assertInitializationAllowed();
        perUserMaxWaitDuration = put(perUserMaxWaitDuration, userName, value);
    }

    void setPerUserMaxWaitDuration(final Map<String, Duration> newMap) {
        assertInitializationAllowed();
        perUserMaxWaitDuration = replaceAll(perUserMaxWaitDuration, newMap);
    }

    void setPerUserMaxWaitMillis(final Map<String, Long> newMap) {
        assertInitializationAllowed();
        perUserMaxWaitDuration = convertMap(perUserMaxWaitDuration, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMaxWaitDuration()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     * @deprecated Use {@link #setPerUserMaxWait(String, Duration)}.
     */
    @Deprecated
    public void setPerUserMaxWaitMillis(final String userName, final Long value) {
        setPerUserMaxWait(userName, toDurationOrNull(value));
    }

    void setPerUserMinEvictableIdle(final Map<String, Duration> newMap) {
        assertInitializationAllowed();
        perUserMinEvictableIdleDuration = replaceAll(perUserMinEvictableIdleDuration, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMinEvictableIdleDuration()} for the specified user's
     * pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     * @since 2.10.0
     */
    public void setPerUserMinEvictableIdle(final String userName, final Duration value) {
        assertInitializationAllowed();
        perUserMinEvictableIdleDuration = put(perUserMinEvictableIdleDuration, userName, value);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMinEvictableIdleDuration()} for the specified user's
     * pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     * @deprecated Use {@link #setPerUserMinEvictableIdle(String, Duration)}.
     */
    @Deprecated
    public void setPerUserMinEvictableIdleTimeMillis(final String userName, final Long value) {
        setPerUserMinEvictableIdle(userName, toDurationOrNull(value));
    }

    void setPerUserMinIdle(final Map<String, Integer> newMap) {
        assertInitializationAllowed();
        perUserMinIdle = replaceAll(perUserMinIdle, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getMinIdle()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserMinIdle(final String userName, final Integer value) {
        assertInitializationAllowed();
        perUserMinIdle = put(perUserMinIdle, userName, value);
    }

    void setPerUserNumTestsPerEvictionRun(final Map<String, Integer> newMap) {
        assertInitializationAllowed();
        perUserNumTestsPerEvictionRun = replaceAll(perUserNumTestsPerEvictionRun, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getNumTestsPerEvictionRun()} for the specified user's
     * pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserNumTestsPerEvictionRun(final String userName, final Integer value) {
        assertInitializationAllowed();
        perUserNumTestsPerEvictionRun = put(perUserNumTestsPerEvictionRun, userName, value);
    }

    void setPerUserSoftMinEvictableIdle(final Map<String, Duration> newMap) {
        assertInitializationAllowed();
        perUserSoftMinEvictableIdleDuration = replaceAll(perUserSoftMinEvictableIdleDuration, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getSoftMinEvictableIdleDuration()} for the specified
     * user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     * @since 2.10.0
     */
    public void setPerUserSoftMinEvictableIdle(final String userName, final Duration value) {
        assertInitializationAllowed();
        perUserSoftMinEvictableIdleDuration = put(perUserSoftMinEvictableIdleDuration, userName, value);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getSoftMinEvictableIdleDuration()} for the specified
     * user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     * @deprecated Use {@link #setPerUserSoftMinEvictableIdle(String, Duration)}.
     */
    @Deprecated
    public void setPerUserSoftMinEvictableIdleTimeMillis(final String userName, final Long value) {
        setPerUserSoftMinEvictableIdle(userName, toDurationOrNull(value));
    }

    void setPerUserTestOnBorrow(final Map<String, Boolean> newMap) {
        assertInitializationAllowed();
        perUserTestOnBorrow = replaceAll(perUserTestOnBorrow, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getTestOnBorrow()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserTestOnBorrow(final String userName, final Boolean value) {
        assertInitializationAllowed();
        perUserTestOnBorrow = put(perUserTestOnBorrow, userName, value);
    }

    void setPerUserTestOnCreate(final Map<String, Boolean> newMap) {
        assertInitializationAllowed();
        perUserTestOnCreate = replaceAll(perUserTestOnCreate, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getTestOnCreate()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserTestOnCreate(final String userName, final Boolean value) {
        assertInitializationAllowed();
        perUserTestOnCreate = put(perUserTestOnCreate, userName, value);
    }

    void setPerUserTestOnReturn(final Map<String, Boolean> newMap) {
        assertInitializationAllowed();
        perUserTestOnReturn = replaceAll(perUserTestOnReturn, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getTestOnReturn()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserTestOnReturn(final String userName, final Boolean value) {
        assertInitializationAllowed();
        perUserTestOnReturn = put(perUserTestOnReturn, userName, value);
    }

    void setPerUserTestWhileIdle(final Map<String, Boolean> newMap) {
        assertInitializationAllowed();
        perUserTestWhileIdle = replaceAll(perUserTestWhileIdle, newMap);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getTestWhileIdle()} for the specified user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     */
    public void setPerUserTestWhileIdle(final String userName, final Boolean value) {
        assertInitializationAllowed();
        perUserTestWhileIdle = put(perUserTestWhileIdle, userName, value);
    }

    /**
     * Sets a user specific value for {@link GenericObjectPool#getDurationBetweenEvictionRuns()} for the specified
     * user's pool.
     *
     * @param userName
     *            The user name key.
     * @param value
     *            The user specific value.
     * @deprecated Use {@link #setPerUserDurationBetweenEvictionRuns(String, Duration)}.
     */
    @Deprecated
    public void setPerUserTimeBetweenEvictionRunsMillis(final String userName, final Long value) {
        setPerUserDurationBetweenEvictionRuns(userName, toDurationOrNull(value));
    }

    @Override
    protected void setupDefaults(final Connection con, final String userName) throws SQLException {
        Boolean defaultAutoCommit = isDefaultAutoCommit();
        if (userName != null) {
            final Boolean userMax = getPerUserDefaultAutoCommit(userName);
            if (userMax != null) {
                defaultAutoCommit = userMax;
            }
        }

        Boolean defaultReadOnly = isDefaultReadOnly();
        if (userName != null) {
            final Boolean userMax = getPerUserDefaultReadOnly(userName);
            if (userMax != null) {
                defaultReadOnly = userMax;
            }
        }

        int defaultTransactionIsolation = getDefaultTransactionIsolation();
        if (userName != null) {
            final Integer userMax = getPerUserDefaultTransactionIsolation(userName);
            if (userMax != null) {
                defaultTransactionIsolation = userMax.intValue();
            }
        }

        if (defaultAutoCommit != null && con.getAutoCommit() != defaultAutoCommit.booleanValue()) {
            con.setAutoCommit(defaultAutoCommit.booleanValue());
        }

        if (defaultTransactionIsolation != UNKNOWN_TRANSACTIONISOLATION) {
            con.setTransactionIsolation(defaultTransactionIsolation);
        }

        if (defaultReadOnly != null && con.isReadOnly() != defaultReadOnly.booleanValue()) {
            con.setReadOnly(defaultReadOnly.booleanValue());
        }
    }

    private Duration toDurationOrNull(final Long millis) {
        return millis == null ? null : Duration.ofMillis(millis.longValue());
    }
}

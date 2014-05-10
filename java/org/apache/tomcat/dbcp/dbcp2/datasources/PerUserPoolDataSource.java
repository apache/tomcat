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
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.sql.ConnectionPoolDataSource;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.dbcp.dbcp2.SwallowedExceptionLogger;
import org.apache.tomcat.dbcp.pool2.ObjectPool;
import org.apache.tomcat.dbcp.pool2.impl.GenericObjectPool;

/**
 * <p>A pooling <code>DataSource</code> appropriate for deployment within
 * J2EE environment.  There are many configuration options, most of which are
 * defined in the parent class.  This datasource uses individual pools per
 * user, and some properties can be set specifically for a given user, if the
 * deployment environment can support initialization of mapped properties.
 * So for example, a pool of admin or write-access Connections can be
 * guaranteed a certain number of connections, separate from a maximum
 * set for users with read-only connections.</p>
 *
 * <p>User passwords can be changed without re-initializing the datasource.
 * When a <code>getConnection(username, password)</code> request is processed
 * with a password that is different from those used to create connections in
 * the pool associated with <code>username</code>, an attempt is made to create
 * a new connection using the supplied password and if this succeeds, the
 * existing pool is cleared and a new pool is created for connections using the
 * new password.</p>
 *
 * @author John D. McNally
 * @since 2.0
 */
public class PerUserPoolDataSource extends InstanceKeyDataSource {

    private static final long serialVersionUID = 7872747993848065028L;

    private static final Log log =
            LogFactory.getLog(PerUserPoolDataSource.class);

    // Per user pool properties
    private Map<String,Boolean> perUserBlockWhenExhausted = null;
    private Map<String,String> perUserEvictionPolicyClassName = null;
    private Map<String,Boolean> perUserLifo = null;
    private Map<String,Integer> perUserMaxIdle = null;
    private Map<String,Integer> perUserMaxTotal = null;
    private Map<String,Long> perUserMaxWaitMillis = null;
    private Map<String,Long> perUserMinEvictableIdleTimeMillis = null;
    private Map<String,Integer> perUserMinIdle = null;
    private Map<String,Integer> perUserNumTestsPerEvictionRun = null;
    private Map<String,Long> perUserSoftMinEvictableIdleTimeMillis = null;
    private Map<String,Boolean> perUserTestOnCreate = null;
    private Map<String,Boolean> perUserTestOnBorrow = null;
    private Map<String,Boolean> perUserTestOnReturn = null;
    private Map<String,Boolean> perUserTestWhileIdle = null;
    private Map<String,Long> perUserTimeBetweenEvictionRunsMillis = null;

    // Per user connection properties
    private Map<String,Boolean> perUserDefaultAutoCommit = null;
    private Map<String,Integer> perUserDefaultTransactionIsolation = null;
    private Map<String,Boolean> perUserDefaultReadOnly = null;

    /**
     * Map to keep track of Pools for a given user
     */
    private transient Map<PoolKey, PooledConnectionManager> managers =
            new HashMap<>();

    /**
     * Default no-arg constructor for Serialization
     */
    public PerUserPoolDataSource() {
    }

    /**
     * Close pool(s) being maintained by this datasource.
     */
    @Override
    public void close() {
        for (PooledConnectionManager manager : managers.values()) {
            try {
              ((CPDSConnectionFactory) manager).getPool().close();
            } catch (Exception closePoolException) {
                    //ignore and try to close others.
            }
        }
        InstanceKeyDataSourceFactory.removeInstance(getInstanceKey());
    }

    // -------------------------------------------------------------------
    // Properties

    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getBlockWhenExhausted()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public boolean getPerUserBlockWhenExhausted(String key) {
        Boolean value = null;
        if (perUserBlockWhenExhausted != null) {
            value = perUserBlockWhenExhausted.get(key);
        }
        if (value == null) {
            return getDefaultBlockWhenExhausted();
        }
        return value.booleanValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getBlockWhenExhausted()} for the specified
     * user's pool.
     */
    public void setPerUserBlockWhenExhausted(String username,
            Boolean value) {
        assertInitializationAllowed();
        if (perUserBlockWhenExhausted == null) {
            perUserBlockWhenExhausted = new HashMap<>();
        }
        perUserBlockWhenExhausted.put(username, value);
    }

    void setPerUserBlockWhenExhausted(
            Map<String,Boolean> userDefaultBlockWhenExhausted) {
        assertInitializationAllowed();
        if (perUserBlockWhenExhausted == null) {
            perUserBlockWhenExhausted = new HashMap<>();
        } else {
            perUserBlockWhenExhausted.clear();
        }
        perUserBlockWhenExhausted.putAll(userDefaultBlockWhenExhausted);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getEvictionPolicyClassName()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public String getPerUserEvictionPolicyClassName(String key) {
        String value = null;
        if (perUserEvictionPolicyClassName != null) {
            value = perUserEvictionPolicyClassName.get(key);
        }
        if (value == null) {
            return getDefaultEvictionPolicyClassName();
        }
        return value;
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getEvictionPolicyClassName()} for the specified
     * user's pool.
     */
    public void setPerUserEvictionPolicyClassName(String username,
            String value) {
        assertInitializationAllowed();
        if (perUserEvictionPolicyClassName == null) {
            perUserEvictionPolicyClassName = new HashMap<>();
        }
        perUserEvictionPolicyClassName.put(username, value);
    }

    void setPerUserEvictionPolicyClassName(
            Map<String,String> userDefaultEvictionPolicyClassName) {
        assertInitializationAllowed();
        if (perUserEvictionPolicyClassName == null) {
            perUserEvictionPolicyClassName = new HashMap<>();
        } else {
            perUserEvictionPolicyClassName.clear();
        }
        perUserEvictionPolicyClassName.putAll(userDefaultEvictionPolicyClassName);
    }


    /**
     * Gets the user specific value for {@link GenericObjectPool#getLifo()} for
     * the specified user's pool or the default if no user specific value is
     * defined.
     */
    public boolean getPerUserLifo(String key) {
        Boolean value = null;
        if (perUserLifo != null) {
            value = perUserLifo.get(key);
        }
        if (value == null) {
            return getDefaultLifo();
        }
        return value.booleanValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getLifo()} for the specified
     * user's pool.
     */
    public void setPerUserLifo(String username, Boolean value) {
        assertInitializationAllowed();
        if (perUserLifo == null) {
            perUserLifo = new HashMap<>();
        }
        perUserLifo.put(username, value);
    }

    void setPerUserLifo(Map<String,Boolean> userDefaultLifo) {
        assertInitializationAllowed();
        if (perUserLifo == null) {
            perUserLifo = new HashMap<>();
        } else {
            perUserLifo.clear();
        }
        perUserLifo.putAll(userDefaultLifo);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getMaxIdle()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public int getPerUserMaxIdle(String key) {
        Integer value = null;
        if (perUserMaxIdle != null) {
            value = perUserMaxIdle.get(key);
        }
        if (value == null) {
            return getDefaultMaxIdle();
        }
        return value.intValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getMaxIdle()} for the specified
     * user's pool.
     */
    public void setPerUserMaxIdle(String username, Integer value) {
        assertInitializationAllowed();
        if (perUserMaxIdle == null) {
            perUserMaxIdle = new HashMap<>();
        }
        perUserMaxIdle.put(username, value);
    }

    void setPerUserMaxIdle(Map<String,Integer> userDefaultMaxIdle) {
        assertInitializationAllowed();
        if (perUserMaxIdle == null) {
            perUserMaxIdle = new HashMap<>();
        } else {
            perUserMaxIdle.clear();
        }
        perUserMaxIdle.putAll(userDefaultMaxIdle);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getMaxTotal()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public int getPerUserMaxTotal(String key) {
        Integer value = null;
        if (perUserMaxTotal != null) {
            value = perUserMaxTotal.get(key);
        }
        if (value == null) {
            return getDefaultMaxTotal();
        }
        return value.intValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getMaxTotal()} for the specified
     * user's pool.
     */
    public void setPerUserMaxTotal(String username, Integer value) {
        assertInitializationAllowed();
        if (perUserMaxTotal == null) {
            perUserMaxTotal = new HashMap<>();
        }
        perUserMaxTotal.put(username, value);
    }

    void setPerUserMaxTotal(Map<String,Integer> userDefaultMaxTotal) {
        assertInitializationAllowed();
        if (perUserMaxTotal == null) {
            perUserMaxTotal = new HashMap<>();
        } else {
            perUserMaxTotal.clear();
        }
        perUserMaxTotal.putAll(userDefaultMaxTotal);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getMaxWaitMillis()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public long getPerUserMaxWaitMillis(String key) {
        Long value = null;
        if (perUserMaxWaitMillis != null) {
            value = perUserMaxWaitMillis.get(key);
        }
        if (value == null) {
            return getDefaultMaxWaitMillis();
        }
        return value.longValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getMaxWaitMillis()} for the specified
     * user's pool.
     */
    public void setPerUserMaxWaitMillis(String username, Long value) {
        assertInitializationAllowed();
        if (perUserMaxWaitMillis == null) {
            perUserMaxWaitMillis = new HashMap<>();
        }
        perUserMaxWaitMillis.put(username, value);
    }

    void setPerUserMaxWaitMillis(
            Map<String,Long> userDefaultMaxWaitMillis) {
        assertInitializationAllowed();
        if (perUserMaxWaitMillis == null) {
            perUserMaxWaitMillis = new HashMap<>();
        } else {
            perUserMaxWaitMillis.clear();
        }
        perUserMaxWaitMillis.putAll(userDefaultMaxWaitMillis);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getMinEvictableIdleTimeMillis()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public long getPerUserMinEvictableIdleTimeMillis(String key) {
        Long value = null;
        if (perUserMinEvictableIdleTimeMillis != null) {
            value = perUserMinEvictableIdleTimeMillis.get(key);
        }
        if (value == null) {
            return getDefaultMinEvictableIdleTimeMillis();
        }
        return value.longValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getMinEvictableIdleTimeMillis()} for the
     * specified user's pool.
     */
    public void setPerUserMinEvictableIdleTimeMillis(String username,
            Long value) {
        assertInitializationAllowed();
        if (perUserMinEvictableIdleTimeMillis == null) {
            perUserMinEvictableIdleTimeMillis = new HashMap<>();
        }
        perUserMinEvictableIdleTimeMillis.put(username, value);
    }

    void setPerUserMinEvictableIdleTimeMillis(
            Map<String,Long> userDefaultMinEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        if (perUserMinEvictableIdleTimeMillis == null) {
            perUserMinEvictableIdleTimeMillis = new HashMap<>();
        } else {
            perUserMinEvictableIdleTimeMillis.clear();
        }
        perUserMinEvictableIdleTimeMillis.putAll(
                userDefaultMinEvictableIdleTimeMillis);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getMinIdle()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public int getPerUserMinIdle(String key) {
        Integer value = null;
        if (perUserMinIdle != null) {
            value = perUserMinIdle.get(key);
        }
        if (value == null) {
            return getDefaultMinIdle();
        }
        return value.intValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getMinIdle()} for the specified
     * user's pool.
     */
    public void setPerUserMinIdle(String username, Integer value) {
        assertInitializationAllowed();
        if (perUserMinIdle == null) {
            perUserMinIdle = new HashMap<>();
        }
        perUserMinIdle.put(username, value);
    }

    void setPerUserMinIdle(Map<String,Integer> userDefaultMinIdle) {
        assertInitializationAllowed();
        if (perUserMinIdle == null) {
            perUserMinIdle = new HashMap<>();
        } else {
            perUserMinIdle.clear();
        }
        perUserMinIdle.putAll(userDefaultMinIdle);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getNumTestsPerEvictionRun()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public int getPerUserNumTestsPerEvictionRun(String key) {
        Integer value = null;
        if (perUserNumTestsPerEvictionRun != null) {
            value = perUserNumTestsPerEvictionRun.get(key);
        }
        if (value == null) {
            return getDefaultNumTestsPerEvictionRun();
        }
        return value.intValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getNumTestsPerEvictionRun()} for the specified
     * user's pool.
     */
    public void setPerUserNumTestsPerEvictionRun(String username,
            Integer value) {
        assertInitializationAllowed();
        if (perUserNumTestsPerEvictionRun == null) {
            perUserNumTestsPerEvictionRun = new HashMap<>();
        }
        perUserNumTestsPerEvictionRun.put(username, value);
    }

    void setPerUserNumTestsPerEvictionRun(
            Map<String,Integer> userDefaultNumTestsPerEvictionRun) {
        assertInitializationAllowed();
        if (perUserNumTestsPerEvictionRun == null) {
            perUserNumTestsPerEvictionRun = new HashMap<>();
        } else {
            perUserNumTestsPerEvictionRun.clear();
        }
        perUserNumTestsPerEvictionRun.putAll(userDefaultNumTestsPerEvictionRun);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public long getPerUserSoftMinEvictableIdleTimeMillis(String key) {
        Long value = null;
        if (perUserSoftMinEvictableIdleTimeMillis != null) {
            value = perUserSoftMinEvictableIdleTimeMillis.get(key);
        }
        if (value == null) {
            return getDefaultSoftMinEvictableIdleTimeMillis();
        }
        return value.longValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getSoftMinEvictableIdleTimeMillis()} for the
     * specified user's pool.
     */
    public void setPerUserSoftMinEvictableIdleTimeMillis(String username,
            Long value) {
        assertInitializationAllowed();
        if (perUserSoftMinEvictableIdleTimeMillis == null) {
            perUserSoftMinEvictableIdleTimeMillis = new HashMap<>();
        }
        perUserSoftMinEvictableIdleTimeMillis.put(username, value);
    }

    void setPerUserSoftMinEvictableIdleTimeMillis(
            Map<String,Long> userDefaultSoftMinEvictableIdleTimeMillis) {
        assertInitializationAllowed();
        if (perUserSoftMinEvictableIdleTimeMillis == null) {
            perUserSoftMinEvictableIdleTimeMillis = new HashMap<>();
        } else {
            perUserSoftMinEvictableIdleTimeMillis.clear();
        }
        perUserSoftMinEvictableIdleTimeMillis.putAll(userDefaultSoftMinEvictableIdleTimeMillis);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getTestOnCreate()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public boolean getPerUserTestOnCreate(String key) {
        Boolean value = null;
        if (perUserTestOnCreate != null) {
            value = perUserTestOnCreate.get(key);
        }
        if (value == null) {
            return getDefaultTestOnCreate();
        }
        return value.booleanValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getTestOnCreate()} for the specified
     * user's pool.
     */
    public void setPerUserTestOnCreate(String username, Boolean value) {
        assertInitializationAllowed();
        if (perUserTestOnCreate == null) {
            perUserTestOnCreate = new HashMap<>();
        }
        perUserTestOnCreate.put(username, value);
    }

    void setPerUserTestOnCreate(Map<String,Boolean> userDefaultTestOnCreate) {
        assertInitializationAllowed();
        if (perUserTestOnCreate == null) {
            perUserTestOnCreate = new HashMap<>();
        } else {
            perUserTestOnCreate.clear();
        }
        perUserTestOnCreate.putAll(userDefaultTestOnCreate);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getTestOnBorrow()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public boolean getPerUserTestOnBorrow(String key) {
        Boolean value = null;
        if (perUserTestOnBorrow != null) {
            value = perUserTestOnBorrow.get(key);
        }
        if (value == null) {
            return getDefaultTestOnBorrow();
        }
        return value.booleanValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getTestOnBorrow()} for the specified
     * user's pool.
     */
    public void setPerUserTestOnBorrow(String username, Boolean value) {
        assertInitializationAllowed();
        if (perUserTestOnBorrow == null) {
            perUserTestOnBorrow = new HashMap<>();
        }
        perUserTestOnBorrow.put(username, value);
    }

    void setPerUserTestOnBorrow(Map<String,Boolean> userDefaultTestOnBorrow) {
        assertInitializationAllowed();
        if (perUserTestOnBorrow == null) {
            perUserTestOnBorrow = new HashMap<>();
        } else {
            perUserTestOnBorrow.clear();
        }
        perUserTestOnBorrow.putAll(userDefaultTestOnBorrow);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getTestOnReturn()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public boolean getPerUserTestOnReturn(String key) {
        Boolean value = null;
        if (perUserTestOnReturn != null) {
            value = perUserTestOnReturn.get(key);
        }
        if (value == null) {
            return getDefaultTestOnReturn();
        }
        return value.booleanValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getTestOnReturn()} for the specified
     * user's pool.
     */
    public void setPerUserTestOnReturn(String username, Boolean value) {
        assertInitializationAllowed();
        if (perUserTestOnReturn == null) {
            perUserTestOnReturn = new HashMap<>();
        }
        perUserTestOnReturn.put(username, value);
    }

    void setPerUserTestOnReturn(
            Map<String,Boolean> userDefaultTestOnReturn) {
        assertInitializationAllowed();
        if (perUserTestOnReturn == null) {
            perUserTestOnReturn = new HashMap<>();
        } else {
            perUserTestOnReturn.clear();
        }
        perUserTestOnReturn.putAll(userDefaultTestOnReturn);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getTestWhileIdle()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public boolean getPerUserTestWhileIdle(String key) {
        Boolean value = null;
        if (perUserTestWhileIdle != null) {
            value = perUserTestWhileIdle.get(key);
        }
        if (value == null) {
            return getDefaultTestWhileIdle();
        }
        return value.booleanValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getTestWhileIdle()} for the specified
     * user's pool.
     */
    public void setPerUserTestWhileIdle(String username, Boolean value) {
        assertInitializationAllowed();
        if (perUserTestWhileIdle == null) {
            perUserTestWhileIdle = new HashMap<>();
        }
        perUserTestWhileIdle.put(username, value);
    }

    void setPerUserTestWhileIdle(
            Map<String,Boolean> userDefaultTestWhileIdle) {
        assertInitializationAllowed();
        if (perUserTestWhileIdle == null) {
            perUserTestWhileIdle = new HashMap<>();
        } else {
            perUserTestWhileIdle.clear();
        }
        perUserTestWhileIdle.putAll(userDefaultTestWhileIdle);
    }


    /**
     * Gets the user specific value for
     * {@link GenericObjectPool#getTimeBetweenEvictionRunsMillis()} for the
     * specified user's pool or the default if no user specific value is defined.
     */
    public long getPerUserTimeBetweenEvictionRunsMillis(String key) {
        Long value = null;
        if (perUserTimeBetweenEvictionRunsMillis != null) {
            value = perUserTimeBetweenEvictionRunsMillis.get(key);
        }
        if (value == null) {
            return getDefaultTimeBetweenEvictionRunsMillis();
        }
        return value.longValue();
    }

    /**
     * Sets a user specific value for
     * {@link GenericObjectPool#getTimeBetweenEvictionRunsMillis ()} for the specified
     * user's pool.
     */
    public void setPerUserTimeBetweenEvictionRunsMillis(String username,
            Long value) {
        assertInitializationAllowed();
        if (perUserTimeBetweenEvictionRunsMillis == null) {
            perUserTimeBetweenEvictionRunsMillis = new HashMap<>();
        }
        perUserTimeBetweenEvictionRunsMillis.put(username, value);
    }

    void setPerUserTimeBetweenEvictionRunsMillis(
            Map<String,Long> userDefaultTimeBetweenEvictionRunsMillis ) {
        assertInitializationAllowed();
        if (perUserTimeBetweenEvictionRunsMillis == null) {
            perUserTimeBetweenEvictionRunsMillis = new HashMap<>();
        } else {
            perUserTimeBetweenEvictionRunsMillis.clear();
        }
        perUserTimeBetweenEvictionRunsMillis.putAll(
                userDefaultTimeBetweenEvictionRunsMillis );
    }


    /**
     * Gets the user specific default value for
     * {@link Connection#setAutoCommit(boolean)} for the specified user's pool.
     */
    public Boolean getPerUserDefaultAutoCommit(String key) {
        Boolean value = null;
        if (perUserDefaultAutoCommit != null) {
            value = perUserDefaultAutoCommit.get(key);
        }
        return value;
    }

    /**
     * Sets a user specific default value for
     * {@link Connection#setAutoCommit(boolean)} for the specified user's pool.
     */
    public void setPerUserDefaultAutoCommit(String username, Boolean value) {
        assertInitializationAllowed();
        if (perUserDefaultAutoCommit == null) {
            perUserDefaultAutoCommit = new HashMap<>();
        }
        perUserDefaultAutoCommit.put(username, value);
    }

    void setPerUserDefaultAutoCommit(Map<String,Boolean> userDefaultAutoCommit) {
        assertInitializationAllowed();
        if (perUserDefaultAutoCommit == null) {
            perUserDefaultAutoCommit = new HashMap<>();
        } else {
            perUserDefaultAutoCommit.clear();
        }
        perUserDefaultAutoCommit.putAll(userDefaultAutoCommit);
    }


    /**
     * Gets the user specific default value for
     * {@link Connection#setReadOnly(boolean)} for the specified user's pool.
     */
    public Boolean getPerUserDefaultReadOnly(String key) {
        Boolean value = null;
        if (perUserDefaultReadOnly != null) {
            value = perUserDefaultReadOnly.get(key);
        }
        return value;
    }

    /**
     * Sets a user specific default value for
     * {@link Connection#setReadOnly(boolean)} for the specified user's pool.
     */
    public void setPerUserDefaultReadOnly(String username, Boolean value) {
        assertInitializationAllowed();
        if (perUserDefaultReadOnly == null) {
            perUserDefaultReadOnly = new HashMap<>();
        }
        perUserDefaultReadOnly.put(username, value);
    }

    void setPerUserDefaultReadOnly(Map<String,Boolean> userDefaultReadOnly) {
        assertInitializationAllowed();
        if (perUserDefaultReadOnly == null) {
            perUserDefaultReadOnly = new HashMap<>();
        } else {
            perUserDefaultReadOnly.clear();
        }
        perUserDefaultReadOnly.putAll(userDefaultReadOnly);
    }


    /**
     * Gets the user specific default value for
     * {@link Connection#setTransactionIsolation(int)} for the specified user's pool.
     */
    public Integer getPerUserDefaultTransactionIsolation(String key) {
        Integer value = null;
        if (perUserDefaultTransactionIsolation != null) {
            value = perUserDefaultTransactionIsolation.get(key);
        }
        return value;
    }

    /**
     * Sets a user specific default value for
     * {@link Connection#setTransactionIsolation(int)} for the specified user's pool.
     */
    public void setPerUserDefaultTransactionIsolation(String username,
            Integer value) {
        assertInitializationAllowed();
        if (perUserDefaultTransactionIsolation == null) {
            perUserDefaultTransactionIsolation = new HashMap<>();
        }
        perUserDefaultTransactionIsolation.put(username, value);
    }

    void setPerUserDefaultTransactionIsolation(
            Map<String,Integer> userDefaultTransactionIsolation) {
        assertInitializationAllowed();
        if (perUserDefaultTransactionIsolation == null) {
            perUserDefaultTransactionIsolation = new HashMap<>();
        } else {
            perUserDefaultTransactionIsolation.clear();
        }
        perUserDefaultTransactionIsolation.putAll(userDefaultTransactionIsolation);
    }


    // ----------------------------------------------------------------------
    // Instrumentation Methods

    /**
     * Get the number of active connections in the default pool.
     */
    public int getNumActive() {
        return getNumActive(null);
    }

    /**
     * Get the number of active connections in the pool for a given user.
     */
    public int getNumActive(String username) {
        ObjectPool<PooledConnectionAndInfo> pool =
            getPool(getPoolKey(username));
        return pool == null ? 0 : pool.getNumActive();
    }

    /**
     * Get the number of idle connections in the default pool.
     */
    public int getNumIdle() {
        return getNumIdle(null);
    }

    /**
     * Get the number of idle connections in the pool for a given user.
     */
    public int getNumIdle(String username) {
        ObjectPool<PooledConnectionAndInfo> pool =
            getPool(getPoolKey(username));
        return pool == null ? 0 : pool.getNumIdle();
    }


    // ----------------------------------------------------------------------
    // Inherited abstract methods

    @Override
    protected PooledConnectionAndInfo
        getPooledConnectionAndInfo(String username, String password)
        throws SQLException {

        final PoolKey key = getPoolKey(username);
        ObjectPool<PooledConnectionAndInfo> pool;
        PooledConnectionManager manager;
        synchronized(this) {
            manager = managers.get(key);
            if (manager == null) {
                try {
                    registerPool(username, password);
                    manager = managers.get(key);
                } catch (NamingException e) {
                    throw new SQLException("RegisterPool failed", e);
                }
            }
            pool = ((CPDSConnectionFactory) manager).getPool();
        }

        PooledConnectionAndInfo info = null;
        try {
            info = pool.borrowObject();
        }
        catch (NoSuchElementException ex) {
            throw new SQLException(
                    "Could not retrieve connection info from pool", ex);
        }
        catch (Exception e) {
            // See if failure is due to CPDSConnectionFactory authentication failure
            try {
                testCPDS(username, password);
            } catch (Exception ex) {
                throw new SQLException(
                        "Could not retrieve connection info from pool", ex);
            }
            // New password works, so kill the old pool, create a new one, and borrow
            manager.closePool(username);
            synchronized (this) {
                managers.remove(key);
            }
            try {
                registerPool(username, password);
                pool = getPool(key);
            } catch (NamingException ne) {
                throw new SQLException("RegisterPool failed", ne);
            }
            try {
                info = pool.borrowObject();
            } catch (Exception ex) {
                throw new SQLException(
                        "Could not retrieve connection info from pool", ex);
            }
        }
        return info;
    }

    @Override
    protected void setupDefaults(Connection con, String username)
        throws SQLException {
        Boolean defaultAutoCommit = isDefaultAutoCommit();
        if (username != null) {
            Boolean userMax = getPerUserDefaultAutoCommit(username);
            if (userMax != null) {
                defaultAutoCommit = userMax;
            }
        }

        Boolean defaultReadOnly = isDefaultReadOnly();
        if (username != null) {
            Boolean userMax = getPerUserDefaultReadOnly(username);
            if (userMax != null) {
                defaultReadOnly = userMax;
            }
        }

        int defaultTransactionIsolation = getDefaultTransactionIsolation();
        if (username != null) {
            Integer userMax = getPerUserDefaultTransactionIsolation(username);
            if (userMax != null) {
                defaultTransactionIsolation = userMax.intValue();
            }
        }

        if (defaultAutoCommit != null &&
                con.getAutoCommit() != defaultAutoCommit.booleanValue()) {
            con.setAutoCommit(defaultAutoCommit.booleanValue());
        }

        if (defaultTransactionIsolation != UNKNOWN_TRANSACTIONISOLATION) {
            con.setTransactionIsolation(defaultTransactionIsolation);
        }

        if (defaultReadOnly != null &&
                con.isReadOnly() != defaultReadOnly.booleanValue()) {
            con.setReadOnly(defaultReadOnly.booleanValue());
        }
    }

    @Override
    protected PooledConnectionManager getConnectionManager(UserPassKey upkey) {
        return managers.get(getPoolKey(upkey.getUsername()));
    }

    /**
     * Returns a <code>PerUserPoolDataSource</code> {@link Reference}.
     */
    @Override
    public Reference getReference() throws NamingException {
        Reference ref = new Reference(getClass().getName(),
                PerUserPoolDataSourceFactory.class.getName(), null);
        ref.add(new StringRefAddr("instanceKey", getInstanceKey()));
        return ref;
    }

    /**
     * Create a pool key from the provided parameters.
     *
     * @param username  User name
     * @return  The pool key
     */
    private PoolKey getPoolKey(String username) {
        return new PoolKey(getDataSourceName(), username);
    }

    private synchronized void registerPool(String username, String password)
            throws NamingException, SQLException {

        ConnectionPoolDataSource cpds = testCPDS(username, password);

        // Set up the factory we will use (passing the pool associates
        // the factory with the pool, so we do not have to do so
        // explicitly)
        CPDSConnectionFactory factory = new CPDSConnectionFactory(cpds,
                getValidationQuery(), getValidationQueryTimeout(),
                isRollbackAfterValidation(), username, password);
        factory.setMaxConnLifetimeMillis(getMaxConnLifetimeMillis());

        // Create an object pool to contain our PooledConnections
        GenericObjectPool<PooledConnectionAndInfo> pool =
                new GenericObjectPool<>(factory);
        factory.setPool(pool);
        pool.setBlockWhenExhausted(getPerUserBlockWhenExhausted(username));
        pool.setEvictionPolicyClassName(
                getPerUserEvictionPolicyClassName(username));
        pool.setLifo(getPerUserLifo(username));
        pool.setMaxIdle(getPerUserMaxIdle(username));
        pool.setMaxTotal(getPerUserMaxTotal(username));
        pool.setMaxWaitMillis(getPerUserMaxWaitMillis(username));
        pool.setMinEvictableIdleTimeMillis(
                getPerUserMinEvictableIdleTimeMillis(username));
        pool.setMinIdle(getPerUserMinIdle(username));
        pool.setNumTestsPerEvictionRun(
                getPerUserNumTestsPerEvictionRun(username));
        pool.setSoftMinEvictableIdleTimeMillis(
                getPerUserSoftMinEvictableIdleTimeMillis(username));
        pool.setTestOnCreate(getPerUserTestOnCreate(username));
        pool.setTestOnBorrow(getPerUserTestOnBorrow(username));
        pool.setTestOnReturn(getPerUserTestOnReturn(username));
        pool.setTestWhileIdle(getPerUserTestWhileIdle(username));
        pool.setTimeBetweenEvictionRunsMillis(
                getPerUserTimeBetweenEvictionRunsMillis(username));

        pool.setSwallowedExceptionListener(new SwallowedExceptionLogger(log));

        Object old = managers.put(getPoolKey(username), factory);
        if (old != null) {
            throw new IllegalStateException("Pool already contains an entry for this user/password: " + username);
        }
    }

    /**
     * Supports Serialization interface.
     *
     * @param in a <code>java.io.ObjectInputStream</code> value
     * @exception IOException if an error occurs
     * @exception ClassNotFoundException if an error occurs
     */
    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        try
        {
            in.defaultReadObject();
            PerUserPoolDataSource oldDS = (PerUserPoolDataSource)
                new PerUserPoolDataSourceFactory()
                    .getObjectInstance(getReference(), null, null, null);
            this.managers = oldDS.managers;
        }
        catch (NamingException e)
        {
            throw new IOException("NamingException: " + e);
        }
    }

    /**
     * Returns the object pool associated with the given PoolKey.
     *
     * @param key PoolKey identifying the pool
     * @return the GenericObjectPool pooling connections for the username and datasource
     * specified by the PoolKey
     */
    private ObjectPool<PooledConnectionAndInfo> getPool(PoolKey key) {
        CPDSConnectionFactory mgr = (CPDSConnectionFactory) managers.get(key);
        return mgr == null ? null : mgr.getPool();
    }
}

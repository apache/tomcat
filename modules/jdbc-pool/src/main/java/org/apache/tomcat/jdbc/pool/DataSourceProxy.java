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
package org.apache.tomcat.jdbc.pool;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.Future;

import javax.sql.XAConnection;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition;

/**
 *
 * The DataSource proxy lets us implements methods that don't exist in the current 
 * compiler JDK but might be methods that are part of a future JDK DataSource interface.
 * <br/>
 * It's a trick to work around compiler issues when implementing interfaces. For example, 
 * I could put in Java 6 methods of javax.sql.DataSource here, and compile it with JDK 1.5
 * and still be able to run under Java 6 without getting NoSuchMethodException.
 *
 * @author Filip Hanik
 * @version 1.0
 */

public class DataSourceProxy implements PoolConfiguration {
    private static final Log log = LogFactory.getLog(DataSourceProxy.class);
    
    protected volatile ConnectionPool pool = null;
    
    protected PoolConfiguration poolProperties = null;

    public DataSourceProxy() {
        this(new PoolProperties());
    }
    
    public DataSourceProxy(PoolConfiguration poolProperties) {
        if (poolProperties == null) throw new NullPointerException("PoolConfiguration can not be null.");
        this.poolProperties = poolProperties;
    }


    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // we are not a wrapper of anything
        return false;
    }


    public <T> T unwrap(Class<T> iface) throws SQLException {
        //we can't unwrap anything
        return null;
    }

    /**
     * {@link javax.sql.DataSource#getConnection()}
     */
    public Connection getConnection(String username, String password) throws SQLException {
        if (this.getPoolProperties().isAlternateUsernameAllowed()) {
            if (pool == null)
                return createPool().getConnection(username,password);
            return pool.getConnection(username,password);
        } else {
            return getConnection();
        }
    }

    public PoolConfiguration getPoolProperties() {
        return poolProperties;
    }

    /**
     * Sets up the connection pool, by creating a pooling driver.
     * @return Driver
     * @throws SQLException
     */
    public ConnectionPool createPool() throws SQLException {
        if (pool != null) {
            return pool;
        } else {
            return pCreatePool();
        }
    }
    
    /**
     * Sets up the connection pool, by creating a pooling driver.
     * @return Driver
     * @throws SQLException
     */
    private synchronized ConnectionPool pCreatePool() throws SQLException {
        if (pool != null) {
            return pool;
        } else {
            pool = new ConnectionPool(poolProperties);
            return pool;
        }
    }

    /**
     * {@link javax.sql.DataSource#getConnection()}
     */

    public Connection getConnection() throws SQLException {
        if (pool == null)
            return createPool().getConnection();
        return pool.getConnection();
    }
    
    /**
     * Invokes an sync operation to retrieve the connection.
     * @return a Future containing a reference to the connection when it becomes available 
     * @throws SQLException
     */
    public Future<Connection> getConnectionAsync() throws SQLException {
        if (pool == null)
            return createPool().getConnectionAsync();
        return pool.getConnectionAsync();
    }
    
    /**
     * {@link javax.sql.XADataSource#getXAConnection()} 
     */
    public XAConnection getXAConnection() throws SQLException {
        Connection con = getConnection();
        if (con instanceof XAConnection) {
            return (XAConnection)con;
        } else {
            try {con.close();} catch (Exception ignore){}
            throw new SQLException("Connection from pool does not implement javax.sql.XAConnection");
        }
    }
    
    /**
     * {@link javax.sql.XADataSource#getXAConnection(String, String)} 
     */
    public XAConnection getXAConnection(String username, String password) throws SQLException {
        Connection con = getConnection(username, password);
        if (con instanceof XAConnection) {
            return (XAConnection)con;
        } else {
            try {con.close();} catch (Exception ignore){}
            throw new SQLException("Connection from pool does not implement javax.sql.XAConnection");
        }
    }


    /**
     * {@link javax.sql.DataSource#getConnection()}
     */
    public javax.sql.PooledConnection getPooledConnection() throws SQLException {
        return (javax.sql.PooledConnection) getConnection();
    }

    /**
     * {@link javax.sql.DataSource#getConnection()}
     */
    public javax.sql.PooledConnection getPooledConnection(String username,
                                                String password) throws SQLException {
        return (javax.sql.PooledConnection) getConnection();
    }
    
    public ConnectionPool getPool() {
        return pool;
    }

    
    public void close() {
        close(false);
    }
    public void close(boolean all) {
        try {
            if (pool != null) {
                final ConnectionPool p = pool;
                pool = null;
                if (p!=null) {
                    p.close(all);
                }
            }
        }catch (Exception x) {
            log.warn("Error duing connection pool closure.", x);
        }
    }

    public int getPoolSize() throws SQLException{
        final ConnectionPool p = pool;
        if (p == null) return 0;
        else return p.getSize();
    }

    
    public String toString() {
        return super.toString()+"{"+getPoolProperties()+"}";
    }


/*-----------------------------------------------------------------------*/
//      PROPERTIES WHEN NOT USED WITH FACTORY
/*------------------------------------------------------------------------*/
   
    /** 
     * {@inheritDoc}
     */
    
    public String getPoolName() {
        return pool.getName();
    }
   
   
    public void setPoolProperties(PoolConfiguration poolProperties) {
        this.poolProperties = poolProperties;
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setDriverClassName(String driverClassName) {
        this.poolProperties.setDriverClassName(driverClassName);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setInitialSize(int initialSize) {
        this.poolProperties.setInitialSize(initialSize);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setInitSQL(String initSQL) {
        this.poolProperties.setInitSQL(initSQL);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setLogAbandoned(boolean logAbandoned) {
        this.poolProperties.setLogAbandoned(logAbandoned);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setMaxActive(int maxActive) {
        this.poolProperties.setMaxActive(maxActive);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setMaxIdle(int maxIdle) {
        this.poolProperties.setMaxIdle(maxIdle);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setMaxWait(int maxWait) {
        this.poolProperties.setMaxWait(maxWait);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        this.poolProperties.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setMinIdle(int minIdle) {
        this.poolProperties.setMinIdle(minIdle);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.poolProperties.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setPassword(String password) {
        this.poolProperties.setPassword(password);
        this.poolProperties.getDbProperties().setProperty("password",this.poolProperties.getPassword());
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setRemoveAbandoned(boolean removeAbandoned) {
        this.poolProperties.setRemoveAbandoned(removeAbandoned);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        this.poolProperties.setRemoveAbandonedTimeout(removeAbandonedTimeout);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setTestOnBorrow(boolean testOnBorrow) {
        this.poolProperties.setTestOnBorrow(testOnBorrow);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setTestOnConnect(boolean testOnConnect) {
        this.poolProperties.setTestOnConnect(testOnConnect);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setTestOnReturn(boolean testOnReturn) {
        this.poolProperties.setTestOnReturn(testOnReturn);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setTestWhileIdle(boolean testWhileIdle) {
        this.poolProperties.setTestWhileIdle(testWhileIdle);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        this.poolProperties.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setUrl(String url) {
        this.poolProperties.setUrl(url);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setUsername(String username) {
        this.poolProperties.setUsername(username);
        this.poolProperties.getDbProperties().setProperty("user",getPoolProperties().getUsername());
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setValidationInterval(long validationInterval) {
        this.poolProperties.setValidationInterval(validationInterval);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setValidationQuery(String validationQuery) {
        this.poolProperties.setValidationQuery(validationQuery);
    }

    /**
     * {@inheritDoc}
     */
    
    public void setValidatorClassName(String className) {
        this.poolProperties.setValidatorClassName(className);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setJdbcInterceptors(String interceptors) {
        this.getPoolProperties().setJdbcInterceptors(interceptors);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setJmxEnabled(boolean enabled) {
        this.getPoolProperties().setJmxEnabled(enabled);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setFairQueue(boolean fairQueue) {
        this.getPoolProperties().setFairQueue(fairQueue);
    }
    
    /** 
     * {@inheritDoc}
     */
    
    public void setUseLock(boolean useLock) {
        this.getPoolProperties().setUseLock(useLock);
    }
    
    /** 
     * {@inheritDoc}
     */
    
    public void setDefaultCatalog(String catalog) {
        this.getPoolProperties().setDefaultCatalog(catalog);
    }
    
    /** 
     * {@inheritDoc}
     */
    
    public void setDefaultAutoCommit(Boolean autocommit) {
        this.getPoolProperties().setDefaultAutoCommit(autocommit);
    }
    
    /** 
     * {@inheritDoc}
     */
    
    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        this.getPoolProperties().setDefaultTransactionIsolation(defaultTransactionIsolation);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setConnectionProperties(String properties) {
        try {
            java.util.Properties prop = DataSourceFactory
                    .getProperties(properties);
            Iterator<?> i = prop.keySet().iterator();
            while (i.hasNext()) {
                String key = (String) i.next();
                String value = prop.getProperty(key);
                getPoolProperties().getDbProperties().setProperty(key, value);
            }

        } catch (Exception x) {
            log.error("Unable to parse connection properties.", x);
            throw new RuntimeException(x);
        }
    }
    
    /** 
     * {@inheritDoc}
     */
    
    public void setUseEquals(boolean useEquals) {
        this.getPoolProperties().setUseEquals(useEquals);
    }

    /**
     * no-op
     * {@link javax.sql.DataSource#getLogWriter}
     */
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }


    /**
     * no-op
     * {@link javax.sql.DataSource#setLogWriter(PrintWriter)}
     */
    public void setLogWriter(PrintWriter out) throws SQLException {
        // NOOP
    }

    /**
     * no-op
     * {@link javax.sql.DataSource#getLoginTimeout}
     */
    public int getLoginTimeout() {
        if (poolProperties == null) {
            return 0;
        } else {
            return poolProperties.getMaxWait() / 1000;
        }
    }

    /**
     * {@link javax.sql.DataSource#setLoginTimeout(int)}
     */
    public void setLoginTimeout(int i) {
        if (poolProperties == null) {
            return;
        } else {
            poolProperties.setMaxWait(1000 * i);
        }

    }    
    
    
    /**
     * {@inheritDoc}
     */
    
    public int getSuspectTimeout() {
        return getPoolProperties().getSuspectTimeout(); 
    }

    /**
     * {@inheritDoc}
     */
    
    public void setSuspectTimeout(int seconds) {
        getPoolProperties().setSuspectTimeout(seconds);
    }
    
  //===============================================================================
//  Expose JMX attributes through Tomcat's dynamic reflection
//===============================================================================
    /**
     * If the pool has not been created, it will be created during this call.
     * @return the number of established but idle connections
     */
    public int getIdle() {
        try {
            return createPool().getIdle();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * {@link #getIdle()}
     */
    public int getNumIdle() {
        return getIdle();
    }

    /**
     * Forces an abandon check on the connection pool.
     * If connections that have been abandoned exists, they will be closed during this run
     */
    public void checkAbandoned() {
        try {
            createPool().checkAbandoned();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Forces a check for resizing of the idle connections
     */
    public void checkIdle() {
        try {
            createPool().checkIdle();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * @return number of connections in use by the application
     */
    public int getActive() {
        try {
            return createPool().getActive();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }
    
    /**
     * @return number of connections in use by the application
     * {@link DataSource#getActive()}
     */
    public int getNumActive() {
        return getActive();
    }

    /**
     * @return number of threads waiting for a connection
     */
    public int getWaitCount() {
        try {
            return createPool().getWaitCount();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * @return the current size of the pool
     */
    public int getSize() {
        try {
            return createPool().getSize();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Performs a validation on idle connections
     */
    public void testIdle() {
        try {
            createPool().testAllIdle();
        }catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }    
    //=========================================================
    //  PROPERTIES / CONFIGURATION
    //=========================================================    
    
    /** 
     * {@inheritDoc}
     */
    
    public String getConnectionProperties() {
        return getPoolProperties().getConnectionProperties();
    }

    /** 
     * {@inheritDoc}
     */
    
    public Properties getDbProperties() {
        return getPoolProperties().getDbProperties();
    }

    /** 
     * {@inheritDoc}
     */
    
    public String getDefaultCatalog() {
        return getPoolProperties().getDefaultCatalog();
    }

    /** 
     * {@inheritDoc}
     */
    
    public int getDefaultTransactionIsolation() {
        return getPoolProperties().getDefaultTransactionIsolation();
    }

    /** 
     * {@inheritDoc}
     */
    
    public String getDriverClassName() {
        return getPoolProperties().getDriverClassName();
    }


    /** 
     * {@inheritDoc}
     */
    
    public int getInitialSize() {
        return getPoolProperties().getInitialSize();
    }

    /** 
     * {@inheritDoc}
     */
    
    public String getInitSQL() {
        return getPoolProperties().getInitSQL();
    }

    /** 
     * {@inheritDoc}
     */
    
    public String getJdbcInterceptors() {
        return getPoolProperties().getJdbcInterceptors();
    }

    /** 
     * {@inheritDoc}
     */
    
    public int getMaxActive() {
        return getPoolProperties().getMaxActive();
    }

    /** 
     * {@inheritDoc}
     */
    
    public int getMaxIdle() {
        return getPoolProperties().getMaxIdle();
    }

    /** 
     * {@inheritDoc}
     */
    
    public int getMaxWait() {
        return getPoolProperties().getMaxWait();
    }

    /** 
     * {@inheritDoc}
     */
    
    public int getMinEvictableIdleTimeMillis() {
        return getPoolProperties().getMinEvictableIdleTimeMillis();
    }

    /** 
     * {@inheritDoc}
     */
    
    public int getMinIdle() {
        return getPoolProperties().getMinIdle();
    }
    
    /** 
     * {@inheritDoc}
     */
    
    public long getMaxAge() {
        return getPoolProperties().getMaxAge();
    }    

    /** 
     * {@inheritDoc}
     */
    
    public String getName() {
        return getPoolProperties().getName();
    }

    /** 
     * {@inheritDoc}
     */
    
    public int getNumTestsPerEvictionRun() {
        return getPoolProperties().getNumTestsPerEvictionRun();
    }

    /**
     * @return DOES NOT RETURN THE PASSWORD, IT WOULD SHOW UP IN JMX
     */
    public String getPassword() {
        return "Password not available as DataSource/JMX operation.";
    }

    /** 
     * {@inheritDoc}
     */
    
    public int getRemoveAbandonedTimeout() {
        return getPoolProperties().getRemoveAbandonedTimeout();
    }


    /** 
     * {@inheritDoc}
     */
    
    public int getTimeBetweenEvictionRunsMillis() {
        return getPoolProperties().getTimeBetweenEvictionRunsMillis();
    }

    /** 
     * {@inheritDoc}
     */
    
    public String getUrl() {
        return getPoolProperties().getUrl();
    }

    /** 
     * {@inheritDoc}
     */
    
    public String getUsername() {
        return getPoolProperties().getUsername();
    }

    /** 
     * {@inheritDoc}
     */
    
    public long getValidationInterval() {
        return getPoolProperties().getValidationInterval();
    }

    /** 
     * {@inheritDoc}
     */
    
    public String getValidationQuery() {
        return getPoolProperties().getValidationQuery();
    }

    /**
     * {@inheritDoc}
     */
    
    public String getValidatorClassName() {
        return getPoolProperties().getValidatorClassName();
    }

    /**
     * {@inheritDoc}
     */
    
    public Validator getValidator() {
        return getPoolProperties().getValidator();
    }
    
    /** 
     * {@inheritDoc}
     */
    public void setValidator(Validator validator) {
        getPoolProperties().setValidator(validator);
    }


    /** 
     * {@inheritDoc}
     */
    
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return getPoolProperties().isAccessToUnderlyingConnectionAllowed();
    }

    /** 
     * {@inheritDoc}
     */
    
    public Boolean isDefaultAutoCommit() {
        return getPoolProperties().isDefaultAutoCommit();
    }

    /** 
     * {@inheritDoc}
     */
    
    public Boolean isDefaultReadOnly() {
        return getPoolProperties().isDefaultReadOnly();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean isLogAbandoned() {
        return getPoolProperties().isLogAbandoned();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean isPoolSweeperEnabled() {
        return getPoolProperties().isPoolSweeperEnabled();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean isRemoveAbandoned() {
        return getPoolProperties().isRemoveAbandoned();
    }

    /** 
     * {@inheritDoc}
     */
    
    public int getAbandonWhenPercentageFull() {
        return getPoolProperties().getAbandonWhenPercentageFull();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean isTestOnBorrow() {
        return getPoolProperties().isTestOnBorrow();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean isTestOnConnect() {
        return getPoolProperties().isTestOnConnect();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean isTestOnReturn() {
        return getPoolProperties().isTestOnReturn();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean isTestWhileIdle() {
        return getPoolProperties().isTestWhileIdle();
    }


    /** 
     * {@inheritDoc}
     */
    
    public Boolean getDefaultAutoCommit() {
        return getPoolProperties().getDefaultAutoCommit();
    }

    /** 
     * {@inheritDoc}
     */
    
    public Boolean getDefaultReadOnly() {
        return getPoolProperties().getDefaultReadOnly();
    }

    /** 
     * {@inheritDoc}
     */
    
    public InterceptorDefinition[] getJdbcInterceptorsAsArray() {
        return getPoolProperties().getJdbcInterceptorsAsArray();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean getUseLock() {
        return getPoolProperties().getUseLock();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean isFairQueue() {
        return getPoolProperties().isFairQueue();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean isJmxEnabled() {
        return getPoolProperties().isJmxEnabled();
    }

    /** 
     * {@inheritDoc}
     */
    
    public boolean isUseEquals() {
        return getPoolProperties().isUseEquals();
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setAbandonWhenPercentageFull(int percentage) {
        getPoolProperties().setAbandonWhenPercentageFull(percentage);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setAccessToUnderlyingConnectionAllowed(boolean accessToUnderlyingConnectionAllowed) {
        getPoolProperties().setAccessToUnderlyingConnectionAllowed(accessToUnderlyingConnectionAllowed);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setDbProperties(Properties dbProperties) {
        getPoolProperties().setDbProperties(dbProperties);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setDefaultReadOnly(Boolean defaultReadOnly) {
        getPoolProperties().setDefaultReadOnly(defaultReadOnly);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setMaxAge(long maxAge) {
        getPoolProperties().setMaxAge(maxAge);
    }

    /** 
     * {@inheritDoc}
     */
    
    public void setName(String name) {
        getPoolProperties().setName(name);
    }
    
    /** 
     * {@inheritDoc}
     */
    public void setDataSource(Object ds) {
        getPoolProperties().setDataSource(ds);
    }
    
    /** 
     * {@inheritDoc}
     */
    public Object getDataSource() {
        return getPoolProperties().getDataSource();
    }
    
    
    /** 
     * {@inheritDoc}
     */
    public void setDataSourceJNDI(String jndiDS) {
        getPoolProperties().setDataSourceJNDI(jndiDS);
    }
    
    /** 
     * {@inheritDoc}
     */
    public String getDataSourceJNDI() {
        return getPoolProperties().getDataSourceJNDI();
    }

    /** 
     * {@inheritDoc}
     */
    public boolean isAlternateUsernameAllowed() {
        return getPoolProperties().isAlternateUsernameAllowed();
    }

    /** 
     * {@inheritDoc}
     */
    public void setAlternateUsernameAllowed(boolean alternateUsernameAllowed) {
        getPoolProperties().setAlternateUsernameAllowed(alternateUsernameAllowed);
    }
    
}

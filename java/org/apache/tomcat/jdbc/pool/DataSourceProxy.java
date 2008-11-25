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

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 *
 * <p>Title: Uber Pool</p>
 *
 * <p>Description: A simple, yet efficient and powerful connection pool</p>
 *
 * <p>Copyright: Copyright (c) 2008 Filip Hanik</p>
 *
 * <p> </p>
 *
 * @author Filip Hanik
 * @version 1.0
 */

public class DataSourceProxy  {
    protected static Log log = LogFactory.getLog(DataSourceProxy.class);
    
    protected volatile ConnectionPool pool = null;
    protected PoolProperties poolProperties = new PoolProperties();

    public DataSourceProxy() {
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
     * {@inheritDoc}
     */
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    public PoolProperties getPoolProperties() {
        return poolProperties;
    }

    /**
     * Sets up the connection pool, by creating a pooling driver.
     * @return Driver
     * @throws SQLException
     */
    public synchronized ConnectionPool createPool() throws SQLException {
        if (pool != null) {
            return pool;
        } else {
            pool = new ConnectionPool(poolProperties);
            return pool;
        }
    }

    /**
     * {@inheritDoc}
     */

    public Connection getConnection() throws SQLException {
        if (pool == null)
            return createPool().getConnection();
        return pool.getConnection();
    }

    /**
     * {@inheritDoc}
     */
    public PooledConnection getPooledConnection() throws SQLException {
        return (PooledConnection) getConnection();
    }

    /**
     * {@inheritDoc}
     */
    public PooledConnection getPooledConnection(String username,
                                                String password) throws SQLException {
        return (PooledConnection) getConnection();
    }

    /**
     * {@inheritDoc}
     */
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void setLogWriter(PrintWriter out) throws SQLException {
    }

    /**
     * {@inheritDoc}
     */
    public int getLoginTimeout() {
        if (poolProperties == null) {
            return 0;
        } else {
            return poolProperties.getMaxWait() / 1000;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setLoginTimeout(int i) {
        if (poolProperties == null) {
            return;
        } else {
            poolProperties.setMaxWait(1000 * i);
        }

    }


    public void close() {
        close(false);
    }
    public void close(boolean all) {
        try {
            if (pool != null) {
                final ConnectionPool p = pool;
                pool = null;
                if (p!=null) p.close(all);
            }
        }catch (Exception x) {
            x.printStackTrace();
        }
    }

    protected void finalize() throws Throwable {
        //terminate the pool?
        close(true);
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
   
   
   
    public void setPoolProperties(PoolProperties poolProperties) {
        this.poolProperties = poolProperties;
    }

    public void setDriverClassName(String driverClassName) {
        this.poolProperties.setDriverClassName(driverClassName);
    }

    public void setInitialSize(int initialSize) {
        this.poolProperties.setInitialSize(initialSize);
    }

    public void setInitSQL(String initSQL) {
        this.poolProperties.setInitSQL(initSQL);
    }

    public void setLogAbandoned(boolean logAbandoned) {
        this.poolProperties.setLogAbandoned(logAbandoned);
    }

    public void setMaxActive(int maxActive) {
        this.poolProperties.setMaxActive(maxActive);
    }

    public void setMaxIdle(int maxIdle) {
        this.poolProperties.setMaxIdle(maxIdle);
    }

    public void setMaxWait(int maxWait) {
        this.poolProperties.setMaxWait(maxWait);
    }

    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        this.poolProperties.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
    }

    public void setMinIdle(int minIdle) {
        this.poolProperties.setMinIdle(minIdle);
    }

    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.poolProperties.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    }

    public void setPassword(String password) {
        this.poolProperties.setPassword(password);
        this.poolProperties.getDbProperties().setProperty("password",this.poolProperties.getPassword());
    }

    public void setRemoveAbandoned(boolean removeAbandoned) {
        this.poolProperties.setRemoveAbandoned(removeAbandoned);
    }

    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        this.poolProperties.setRemoveAbandonedTimeout(removeAbandonedTimeout);
    }

    public void setTestOnBorrow(boolean testOnBorrow) {
        this.poolProperties.setTestOnBorrow(testOnBorrow);
    }

    public void setTestOnConnect(boolean testOnConnect) {
        this.poolProperties.setTestOnConnect(testOnConnect);
    }

    public void setTestOnReturn(boolean testOnReturn) {
        this.poolProperties.setTestOnReturn(testOnReturn);
    }

    public void setTestWhileIdle(boolean testWhileIdle) {
        this.poolProperties.setTestWhileIdle(testWhileIdle);
    }

    public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        this.poolProperties.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
    }

    public void setUrl(String url) {
        this.poolProperties.setUrl(url);
    }

    public void setUsername(String username) {
        this.poolProperties.setUsername(username);
        this.poolProperties.getDbProperties().setProperty("user",getPoolProperties().getUsername());
    }

    public void setValidationInterval(long validationInterval) {
        this.poolProperties.setValidationInterval(validationInterval);
    }

    public void setValidationQuery(String validationQuery) {
        this.poolProperties.setValidationQuery(validationQuery);
    }

    public void setJdbcInterceptors(String interceptors) {
        this.getPoolProperties().setJdbcInterceptors(interceptors);
    }

    public void setJmxEnabled(boolean enabled) {
        this.getPoolProperties().setJmxEnabled(enabled);
    }

    public void setFairQueue(boolean fairQueue) {
        this.getPoolProperties().setFairQueue(fairQueue);
    }
    
    public void setDefaultCatalog(String catalog) {
        this.getPoolProperties().setDefaultCatalog(catalog);
    }
    
    public void setDefaultAutoCommit(Boolean autocommit) {
        this.getPoolProperties().setDefaultAutoCommit(autocommit);
    }
    
    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        this.getPoolProperties().setDefaultTransactionIsolation(defaultTransactionIsolation);
    }

    public void setConnectionProperties(String properties) {
        try {
            java.util.Properties prop = DataSourceFactory
                    .getProperties(properties);
            Iterator i = prop.keySet().iterator();
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
    
    public void setUseEquals(boolean useEquals) {
        this.getPoolProperties().setUseEquals(useEquals);
    }

}

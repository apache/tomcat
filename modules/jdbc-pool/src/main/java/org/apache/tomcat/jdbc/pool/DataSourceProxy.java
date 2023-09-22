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
import java.sql.SQLFeatureNotSupportedException;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.sql.XAConnection;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition;

/**
 * The DataSource proxy lets us implements methods that don't exist in the current
 * compiler JDK but might be methods that are part of a future JDK DataSource interface.
 * <br>
 * It's a trick to work around compiler issues when implementing interfaces. For example,
 * I could put in Java 6 methods of javax.sql.DataSource here, and compile it with JDK 1.5
 * and still be able to run under Java 6 without getting NoSuchMethodException.
 */

public class DataSourceProxy implements PoolConfiguration {
    private static final Log log = LogFactory.getLog(DataSourceProxy.class);

    protected volatile ConnectionPool pool = null;

    protected volatile PoolConfiguration poolProperties = null;

    public DataSourceProxy() {
        this(new PoolProperties());
    }

    public DataSourceProxy(PoolConfiguration poolProperties) {
        if (poolProperties == null) {
          throw new NullPointerException("PoolConfiguration cannot be null.");
        }
        this.poolProperties = poolProperties;
    }


    @SuppressWarnings("unused") // Has to match signature in DataSource
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // we are not a wrapper of anything
        return false;
    }


    @SuppressWarnings("unused") // Has to match signature in DataSource
    public <T> T unwrap(Class<T> iface) throws SQLException {
        //we can't unwrap anything
        return null;
    }

    /**
     * Get a database connection.
     * {@link javax.sql.DataSource#getConnection()}
     * @param username The user name
     * @param password The password
     * @return the connection
     * @throws SQLException Connection error
     */
    public Connection getConnection(String username, String password) throws SQLException {
        if (this.getPoolProperties().isAlternateUsernameAllowed()) {
            if (pool == null) {
              return createPool().getConnection(username,password);
            }
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
     * @return the connection pool
     * @throws SQLException Error creating pool
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
     * Get a database connection.
     * {@link javax.sql.DataSource#getConnection()}
     * @return the connection
     * @throws SQLException Connection error
     */
    public Connection getConnection() throws SQLException {
        if (pool == null) {
          return createPool().getConnection();
        }
        return pool.getConnection();
    }

    /**
     * Invokes an sync operation to retrieve the connection.
     * @return a Future containing a reference to the connection when it becomes available
     * @throws SQLException Connection error
     */
    public Future<Connection> getConnectionAsync() throws SQLException {
        if (pool == null) {
          return createPool().getConnectionAsync();
        }
        return pool.getConnectionAsync();
    }

    /**
     * Get a database connection.
     * {@link javax.sql.XADataSource#getXAConnection()}
     * @return the connection
     * @throws SQLException Connection error
     */
    public XAConnection getXAConnection() throws SQLException {
        Connection con = getConnection();
        if (con instanceof XAConnection) {
            return (XAConnection)con;
        } else {
            try {
                con.close();
            } catch (Exception ignore) {
                // Ignore
            }
            throw new SQLException("Connection from pool does not implement javax.sql.XAConnection");
        }
    }

    /**
     * Get a database connection.
     * {@link javax.sql.XADataSource#getXAConnection(String, String)}
     * @param username The user name
     * @param password The password
     * @return the connection
     * @throws SQLException Connection error
     */
    public XAConnection getXAConnection(String username, String password) throws SQLException {
        Connection con = getConnection(username, password);
        if (con instanceof XAConnection) {
            return (XAConnection)con;
        } else {
            try {
                con.close();
            } catch (Exception ignore) {
                // Ignore
            }
            throw new SQLException("Connection from pool does not implement javax.sql.XAConnection");
        }
    }


    /**
     * Get a database connection.
     * {@link javax.sql.DataSource#getConnection()}
     * @return the connection
     * @throws SQLException Connection error
     */
    public javax.sql.PooledConnection getPooledConnection() throws SQLException {
        return (javax.sql.PooledConnection) getConnection();
    }

    /**
     * Get a database connection.
     * {@link javax.sql.DataSource#getConnection()}
     * @param username unused
     * @param password unused
     * @return the connection
     * @throws SQLException Connection error
     */
    public javax.sql.PooledConnection getPooledConnection(String username,
            String password) throws SQLException {
        return (javax.sql.PooledConnection) getConnection();
    }

    public ConnectionPool getPool() {
        try {
            return createPool();
        }catch (SQLException x) {
            log.error("Error during connection pool creation.", x);
            return null;
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
                if (p!=null) {
                    p.close(all);
                }
            }
        }catch (Exception x) {
            log.warn("Error during connection pool closure.", x);
        }
    }

    public int getPoolSize() {
        final ConnectionPool p = pool;
        if (p == null) {
          return 0;
        } else {
          return p.getSize();
        }
    }


    @Override
    public String toString() {
        return super.toString()+"{"+getPoolProperties()+"}";
    }


/*-----------------------------------------------------------------------*/
//      PROPERTIES WHEN NOT USED WITH FACTORY
/*------------------------------------------------------------------------*/


    @Override
    public String getPoolName() {
        return pool.getName();
    }


    public void setPoolProperties(PoolConfiguration poolProperties) {
        this.poolProperties = poolProperties;
    }


    @Override
    public void setDriverClassName(String driverClassName) {
        this.poolProperties.setDriverClassName(driverClassName);
    }


    @Override
    public void setInitialSize(int initialSize) {
        this.poolProperties.setInitialSize(initialSize);
    }


    @Override
    public void setInitSQL(String initSQL) {
        this.poolProperties.setInitSQL(initSQL);
    }


    @Override
    public void setLogAbandoned(boolean logAbandoned) {
        this.poolProperties.setLogAbandoned(logAbandoned);
    }


    @Override
    public void setMaxActive(int maxActive) {
        this.poolProperties.setMaxActive(maxActive);
    }


    @Override
    public void setMaxIdle(int maxIdle) {
        this.poolProperties.setMaxIdle(maxIdle);
    }


    @Override
    public void setMaxWait(int maxWait) {
        this.poolProperties.setMaxWait(maxWait);
    }


    @Override
    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        this.poolProperties.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
    }


    @Override
    public void setMinIdle(int minIdle) {
        this.poolProperties.setMinIdle(minIdle);
    }


    @Override
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        this.poolProperties.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    }


    @Override
    public void setPassword(String password) {
        this.poolProperties.setPassword(password);
        this.poolProperties.getDbProperties().setProperty("password",this.poolProperties.getPassword());
    }


    @Override
    public void setRemoveAbandoned(boolean removeAbandoned) {
        this.poolProperties.setRemoveAbandoned(removeAbandoned);
    }


    @Override
    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        this.poolProperties.setRemoveAbandonedTimeout(removeAbandonedTimeout);
    }


    @Override
    public void setTestOnBorrow(boolean testOnBorrow) {
        this.poolProperties.setTestOnBorrow(testOnBorrow);
    }


    @Override
    public void setTestOnConnect(boolean testOnConnect) {
        this.poolProperties.setTestOnConnect(testOnConnect);
    }


    @Override
    public void setTestOnReturn(boolean testOnReturn) {
        this.poolProperties.setTestOnReturn(testOnReturn);
    }


    @Override
    public void setTestWhileIdle(boolean testWhileIdle) {
        this.poolProperties.setTestWhileIdle(testWhileIdle);
    }


    @Override
    public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        this.poolProperties.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
    }


    @Override
    public void setUrl(String url) {
        this.poolProperties.setUrl(url);
    }


    @Override
    public void setUsername(String username) {
        this.poolProperties.setUsername(username);
        this.poolProperties.getDbProperties().setProperty("user",getPoolProperties().getUsername());
    }


    @Override
    public void setValidationInterval(long validationInterval) {
        this.poolProperties.setValidationInterval(validationInterval);
    }


    @Override
    public void setValidationQuery(String validationQuery) {
        this.poolProperties.setValidationQuery(validationQuery);
    }


    @Override
    public void setValidatorClassName(String className) {
        this.poolProperties.setValidatorClassName(className);
    }


    @Override
    public void setValidationQueryTimeout(int validationQueryTimeout) {
        this.poolProperties.setValidationQueryTimeout(validationQueryTimeout);
    }


    @Override
    public void setJdbcInterceptors(String interceptors) {
        this.getPoolProperties().setJdbcInterceptors(interceptors);
    }


    @Override
    public void setJmxEnabled(boolean enabled) {
        this.getPoolProperties().setJmxEnabled(enabled);
    }


    @Override
    public void setFairQueue(boolean fairQueue) {
        this.getPoolProperties().setFairQueue(fairQueue);
    }


    @Override
    public void setUseLock(boolean useLock) {
        this.getPoolProperties().setUseLock(useLock);
    }


    @Override
    public void setDefaultCatalog(String catalog) {
        this.getPoolProperties().setDefaultCatalog(catalog);
    }


    @Override
    public void setDefaultAutoCommit(Boolean autocommit) {
        this.getPoolProperties().setDefaultAutoCommit(autocommit);
    }


    @Override
    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        this.getPoolProperties().setDefaultTransactionIsolation(defaultTransactionIsolation);
    }


    @Override
    public void setConnectionProperties(String properties) {
        try {
            Properties prop = DataSourceFactory.getProperties(properties);
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


    @Override
    public void setUseEquals(boolean useEquals) {
        this.getPoolProperties().setUseEquals(useEquals);
    }

    /**
     * no-op
     * {@link javax.sql.DataSource#getParentLogger}
     * @return no return value
     * @throws SQLFeatureNotSupportedException Unsupported
     */
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * no-op
     * {@link javax.sql.DataSource#getLogWriter}
     * @return null
     * @throws SQLException No exception
     */
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }


    /**
     * no-op
     * {@link javax.sql.DataSource#setLogWriter(PrintWriter)}
     * @param out Ignored
     * @throws SQLException No exception
     */
    public void setLogWriter(PrintWriter out) throws SQLException {
        // NOOP
    }

    /**
     * no-op
     * {@link javax.sql.DataSource#getLoginTimeout}
     * @return the timeout
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
     * @param i The timeout value
     */
    public void setLoginTimeout(int i) {
        if (poolProperties == null) {
            return;
        } else {
            poolProperties.setMaxWait(1000 * i);
        }

    }



    @Override
    public int getSuspectTimeout() {
        return getPoolProperties().getSuspectTimeout();
    }


    @Override
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
     * @return the number of established but idle connections
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

    /**
     * The total number of connections borrowed from this pool.
     * @return the borrowed connection count
     */
    public long getBorrowedCount() {
        try {
            return createPool().getBorrowedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections returned to this pool.
     * @return the returned connection count
     */
    public long getReturnedCount() {
        try {
            return createPool().getReturnedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections created by this pool.
     * @return the created connection count
     */
    public long getCreatedCount() {
        try {
            return createPool().getCreatedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections released from this pool.
     * @return the released connection count
     */
    public long getReleasedCount() {
        try {
            return createPool().getReleasedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections reconnected by this pool.
     * @return the reconnected connection count
     */
    public long getReconnectedCount() {
        try {
            return createPool().getReconnectedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections released by remove abandoned.
     * @return the PoolCleaner removed abandoned connection count
     */
    public long getRemoveAbandonedCount() {
        try {
            return createPool().getRemoveAbandonedCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * The total number of connections released by eviction.
     * @return the PoolCleaner evicted idle connection count
     */
    public long getReleasedIdleCount() {
        try {
            return createPool().getReleasedIdleCount();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * reset the statistics of this pool.
     */
    public void resetStats() {
        try {
            createPool().resetStats();
        } catch (SQLException x) {
            throw new RuntimeException(x);
        }
    }

    //=========================================================
    //  PROPERTIES / CONFIGURATION
    //=========================================================


    @Override
    public String getConnectionProperties() {
        return getPoolProperties().getConnectionProperties();
    }


    @Override
    public Properties getDbProperties() {
        return getPoolProperties().getDbProperties();
    }


    @Override
    public String getDefaultCatalog() {
        return getPoolProperties().getDefaultCatalog();
    }


    @Override
    public int getDefaultTransactionIsolation() {
        return getPoolProperties().getDefaultTransactionIsolation();
    }


    @Override
    public String getDriverClassName() {
        return getPoolProperties().getDriverClassName();
    }



    @Override
    public int getInitialSize() {
        return getPoolProperties().getInitialSize();
    }


    @Override
    public String getInitSQL() {
        return getPoolProperties().getInitSQL();
    }


    @Override
    public String getJdbcInterceptors() {
        return getPoolProperties().getJdbcInterceptors();
    }


    @Override
    public int getMaxActive() {
        return getPoolProperties().getMaxActive();
    }


    @Override
    public int getMaxIdle() {
        return getPoolProperties().getMaxIdle();
    }


    @Override
    public int getMaxWait() {
        return getPoolProperties().getMaxWait();
    }


    @Override
    public int getMinEvictableIdleTimeMillis() {
        return getPoolProperties().getMinEvictableIdleTimeMillis();
    }


    @Override
    public int getMinIdle() {
        return getPoolProperties().getMinIdle();
    }


    @Override
    public long getMaxAge() {
        return getPoolProperties().getMaxAge();
    }


    @Override
    public String getName() {
        return getPoolProperties().getName();
    }


    @Override
    public int getNumTestsPerEvictionRun() {
        return getPoolProperties().getNumTestsPerEvictionRun();
    }

    /**
     * @return DOES NOT RETURN THE PASSWORD, IT WOULD SHOW UP IN JMX
     */
    @Override
    public String getPassword() {
        return "Password not available as DataSource/JMX operation.";
    }


    @Override
    public int getRemoveAbandonedTimeout() {
        return getPoolProperties().getRemoveAbandonedTimeout();
    }



    @Override
    public int getTimeBetweenEvictionRunsMillis() {
        return getPoolProperties().getTimeBetweenEvictionRunsMillis();
    }


    @Override
    public String getUrl() {
        return getPoolProperties().getUrl();
    }


    @Override
    public String getUsername() {
        return getPoolProperties().getUsername();
    }


    @Override
    public long getValidationInterval() {
        return getPoolProperties().getValidationInterval();
    }


    @Override
    public String getValidationQuery() {
        return getPoolProperties().getValidationQuery();
    }


    @Override
    public int getValidationQueryTimeout() {
        return getPoolProperties().getValidationQueryTimeout();
    }


    @Override
    public String getValidatorClassName() {
        return getPoolProperties().getValidatorClassName();
    }


    @Override
    public Validator getValidator() {
        return getPoolProperties().getValidator();
    }

    @Override
    public void setValidator(Validator validator) {
        getPoolProperties().setValidator(validator);
    }



    @Override
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return getPoolProperties().isAccessToUnderlyingConnectionAllowed();
    }


    @Override
    public Boolean isDefaultAutoCommit() {
        return getPoolProperties().isDefaultAutoCommit();
    }


    @Override
    public Boolean isDefaultReadOnly() {
        return getPoolProperties().isDefaultReadOnly();
    }


    @Override
    public boolean isLogAbandoned() {
        return getPoolProperties().isLogAbandoned();
    }


    @Override
    public boolean isPoolSweeperEnabled() {
        return getPoolProperties().isPoolSweeperEnabled();
    }


    @Override
    public boolean isRemoveAbandoned() {
        return getPoolProperties().isRemoveAbandoned();
    }


    @Override
    public int getAbandonWhenPercentageFull() {
        return getPoolProperties().getAbandonWhenPercentageFull();
    }


    @Override
    public boolean isTestOnBorrow() {
        return getPoolProperties().isTestOnBorrow();
    }


    @Override
    public boolean isTestOnConnect() {
        return getPoolProperties().isTestOnConnect();
    }


    @Override
    public boolean isTestOnReturn() {
        return getPoolProperties().isTestOnReturn();
    }


    @Override
    public boolean isTestWhileIdle() {
        return getPoolProperties().isTestWhileIdle();
    }



    @Override
    public Boolean getDefaultAutoCommit() {
        return getPoolProperties().getDefaultAutoCommit();
    }


    @Override
    public Boolean getDefaultReadOnly() {
        return getPoolProperties().getDefaultReadOnly();
    }


    @Override
    public InterceptorDefinition[] getJdbcInterceptorsAsArray() {
        return getPoolProperties().getJdbcInterceptorsAsArray();
    }


    @Override
    public boolean getUseLock() {
        return getPoolProperties().getUseLock();
    }


    @Override
    public boolean isFairQueue() {
        return getPoolProperties().isFairQueue();
    }


    @Override
    public boolean isJmxEnabled() {
        return getPoolProperties().isJmxEnabled();
    }


    @Override
    public boolean isUseEquals() {
        return getPoolProperties().isUseEquals();
    }


    @Override
    public void setAbandonWhenPercentageFull(int percentage) {
        getPoolProperties().setAbandonWhenPercentageFull(percentage);
    }


    @Override
    public void setAccessToUnderlyingConnectionAllowed(boolean accessToUnderlyingConnectionAllowed) {
        getPoolProperties().setAccessToUnderlyingConnectionAllowed(accessToUnderlyingConnectionAllowed);
    }


    @Override
    public void setDbProperties(Properties dbProperties) {
        getPoolProperties().setDbProperties(dbProperties);
    }


    @Override
    public void setDefaultReadOnly(Boolean defaultReadOnly) {
        getPoolProperties().setDefaultReadOnly(defaultReadOnly);
    }


    @Override
    public void setMaxAge(long maxAge) {
        getPoolProperties().setMaxAge(maxAge);
    }


    @Override
    public void setName(String name) {
        getPoolProperties().setName(name);
    }

    @Override
    public void setDataSource(Object ds) {
        getPoolProperties().setDataSource(ds);
    }

    @Override
    public Object getDataSource() {
        return getPoolProperties().getDataSource();
    }


    @Override
    public void setDataSourceJNDI(String jndiDS) {
        getPoolProperties().setDataSourceJNDI(jndiDS);
    }

    @Override
    public String getDataSourceJNDI() {
        return getPoolProperties().getDataSourceJNDI();
    }

    @Override
    public boolean isAlternateUsernameAllowed() {
        return getPoolProperties().isAlternateUsernameAllowed();
    }

    @Override
    public void setAlternateUsernameAllowed(boolean alternateUsernameAllowed) {
        getPoolProperties().setAlternateUsernameAllowed(alternateUsernameAllowed);
    }

    @Override
    public void setCommitOnReturn(boolean commitOnReturn) {
        getPoolProperties().setCommitOnReturn(commitOnReturn);
    }

    @Override
    public boolean getCommitOnReturn() {
        return getPoolProperties().getCommitOnReturn();
    }

    @Override
    public void setRollbackOnReturn(boolean rollbackOnReturn) {
        getPoolProperties().setRollbackOnReturn(rollbackOnReturn);
    }

    @Override
    public boolean getRollbackOnReturn() {
        return getPoolProperties().getRollbackOnReturn();
    }

    @Override
    public void setUseDisposableConnectionFacade(boolean useDisposableConnectionFacade) {
        getPoolProperties().setUseDisposableConnectionFacade(useDisposableConnectionFacade);
    }

    @Override
    public boolean getUseDisposableConnectionFacade() {
        return getPoolProperties().getUseDisposableConnectionFacade();
    }

    @Override
    public void setLogValidationErrors(boolean logValidationErrors) {
        getPoolProperties().setLogValidationErrors(logValidationErrors);
    }

    @Override
    public boolean getLogValidationErrors() {
        return getPoolProperties().getLogValidationErrors();
    }

    @Override
    public boolean getPropagateInterruptState() {
        return getPoolProperties().getPropagateInterruptState();
    }

    @Override
    public void setPropagateInterruptState(boolean propagateInterruptState) {
        getPoolProperties().setPropagateInterruptState(propagateInterruptState);
    }

    @Override
    public boolean isIgnoreExceptionOnPreLoad() {
        return getPoolProperties().isIgnoreExceptionOnPreLoad();
    }

    @Override
    public void setIgnoreExceptionOnPreLoad(boolean ignoreExceptionOnPreLoad) {
        getPoolProperties().setIgnoreExceptionOnPreLoad(ignoreExceptionOnPreLoad);
    }

    @Override
    public boolean getUseStatementFacade() {
        return getPoolProperties().getUseStatementFacade();
    }

    @Override
    public void setUseStatementFacade(boolean useStatementFacade) {
        getPoolProperties().setUseStatementFacade(useStatementFacade);
    }

    public void purge()  {
        try {
            createPool().purge();
        }catch (SQLException x) {
            log.error("Unable to purge pool.",x);
        }
    }

    public void purgeOnReturn() {
        try {
            createPool().purgeOnReturn();
        }catch (SQLException x) {
            log.error("Unable to purge pool.",x);
        }
    }
}

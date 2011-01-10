/* Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.tomcat.jdbc.pool.jmx;
/**
 * @author Filip Hanik
 */
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.Validator;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition;

public class ConnectionPool extends NotificationBroadcasterSupport implements ConnectionPoolMBean  {
    /**
     * logger
     */
    private static final Log log = LogFactory.getLog(ConnectionPool.class);

    /**
     * the connection pool
     */
    protected org.apache.tomcat.jdbc.pool.ConnectionPool pool = null;
    /**
     * sequence for JMX notifications
     */
    protected AtomicInteger sequence = new AtomicInteger(0);
    
    /**
     * Listeners that are local and interested in our notifications, no need for JMX
     */
    protected ConcurrentLinkedQueue<NotificationListener> listeners = new ConcurrentLinkedQueue<NotificationListener>(); 

    public ConnectionPool(org.apache.tomcat.jdbc.pool.ConnectionPool pool) {
        super();
        this.pool = pool;
    }

    public org.apache.tomcat.jdbc.pool.ConnectionPool getPool() {
        return pool;
    }
    
    public PoolConfiguration getPoolProperties() {
        return pool.getPoolProperties();
    }
    
    //=================================================================
    //       NOTIFICATION INFO
    //=================================================================
    public static final String NOTIFY_INIT = "INIT FAILED";
    public static final String NOTIFY_CONNECT = "CONNECTION FAILED";
    public static final String NOTIFY_ABANDON = "CONNECTION ABANDONED";
    public static final String SLOW_QUERY_NOTIFICATION = "SLOW QUERY";
    public static final String FAILED_QUERY_NOTIFICATION = "FAILED QUERY";
    public static final String SUSPECT_ABANDONED_NOTIFICATION = "SUSPECT CONNETION ABANDONED";


    public MBeanNotificationInfo[] getNotificationInfo() { 
        MBeanNotificationInfo[] pres = super.getNotificationInfo();
        MBeanNotificationInfo[] loc = getDefaultNotificationInfo();
        MBeanNotificationInfo[] aug = new MBeanNotificationInfo[pres.length + loc.length];
        if (pres.length>0) System.arraycopy(pres, 0, aug, 0, pres.length);
        if (loc.length >0) System.arraycopy(loc, 0, aug, pres.length, loc.length);    
        return aug; 
    } 
    
    public static MBeanNotificationInfo[] getDefaultNotificationInfo() {
        String[] types = new String[] {NOTIFY_INIT, NOTIFY_CONNECT, NOTIFY_ABANDON, SLOW_QUERY_NOTIFICATION, FAILED_QUERY_NOTIFICATION, SUSPECT_ABANDONED_NOTIFICATION}; 
        String name = Notification.class.getName(); 
        String description = "A connection pool error condition was met."; 
        MBeanNotificationInfo info = new MBeanNotificationInfo(types, name, description); 
        return new MBeanNotificationInfo[] {info};
    }
    
    /**
     * Return true if the notification was sent successfully, false otherwise.
     * @param type
     * @param message
     * @return true if the notification succeeded
     */
    public boolean notify(final String type, String message) {
        try {
            Notification n = new Notification(
                    type,
                    this,
                    sequence.incrementAndGet(),
                    System.currentTimeMillis(),
                    "["+type+"] "+message);
            sendNotification(n);
            for (NotificationListener listener : listeners) {
                listener.handleNotification(n,this);
            }
            return true;
        }catch (Exception x) {
            if (log.isDebugEnabled()) {
                log.debug("Notify failed. Type="+type+"; Message="+message,x);
            }
            return false;
        }
        
    }
    
    public void addListener(NotificationListener list) {
        listeners.add(list);
    }
    
    public boolean removeListener(NotificationListener list) {
        return listeners.remove(list);
    }
    
    //=================================================================
    //       POOL STATS
    //=================================================================

    public int getSize() {
        return pool.getSize();
    }

    public int getIdle() {
        return pool.getIdle();
    }

    public int getActive() {
        return pool.getActive();
    }
    
    public int getNumIdle() {
        return getIdle();
    }
    
    public int getNumActive() {
        return getActive();
    }
    
    public int getWaitCount() {
        return pool.getWaitCount();
    }

    //=================================================================
    //       POOL OPERATIONS
    //=================================================================
    public void checkIdle() {
        pool.checkIdle();
    }

    public void checkAbandoned() {
        pool.checkAbandoned();
    }

    public void testIdle() {
        pool.testAllIdle();
    }
    //=================================================================
    //       POOL PROPERTIES
    //=================================================================
    //=========================================================
    //  PROPERTIES / CONFIGURATION
    //=========================================================    


    public String getConnectionProperties() {
        return getPoolProperties().getConnectionProperties();
    }

    public Properties getDbProperties() {
        return getPoolProperties().getDbProperties();
    }

    public String getDefaultCatalog() {
        return getPoolProperties().getDefaultCatalog();
    }

    public int getDefaultTransactionIsolation() {
        return getPoolProperties().getDefaultTransactionIsolation();
    }

    public String getDriverClassName() {
        return getPoolProperties().getDriverClassName();
    }


    public int getInitialSize() {
        return getPoolProperties().getInitialSize();
    }

    public String getInitSQL() {
        return getPoolProperties().getInitSQL();
    }

    public String getJdbcInterceptors() {
        return getPoolProperties().getJdbcInterceptors();
    }

    public int getMaxActive() {
        return getPoolProperties().getMaxActive();
    }

    public int getMaxIdle() {
        return getPoolProperties().getMaxIdle();
    }

    public int getMaxWait() {
        return getPoolProperties().getMaxWait();
    }

    public int getMinEvictableIdleTimeMillis() {
        return getPoolProperties().getMinEvictableIdleTimeMillis();
    }

    public int getMinIdle() {
        return getPoolProperties().getMinIdle();
    }
    
    public long getMaxAge() {
        return getPoolProperties().getMaxAge();
    }    

    public String getName() {
        return this.getPoolName();
    }

    public int getNumTestsPerEvictionRun() {
        return getPoolProperties().getNumTestsPerEvictionRun();
    }

    /**
     * @return DOES NOT RETURN THE PASSWORD, IT WOULD SHOW UP IN JMX
     */
    public String getPassword() {
        return "Password not available as DataSource/JMX operation.";
    }

    public int getRemoveAbandonedTimeout() {
        return getPoolProperties().getRemoveAbandonedTimeout();
    }


    public int getTimeBetweenEvictionRunsMillis() {
        return getPoolProperties().getTimeBetweenEvictionRunsMillis();
    }

    public String getUrl() {
        return getPoolProperties().getUrl();
    }

    public String getUsername() {
        return getPoolProperties().getUsername();
    }

    public long getValidationInterval() {
        return getPoolProperties().getValidationInterval();
    }

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

    public boolean isAccessToUnderlyingConnectionAllowed() {
        return getPoolProperties().isAccessToUnderlyingConnectionAllowed();
    }

    public Boolean isDefaultAutoCommit() {
        return getPoolProperties().isDefaultAutoCommit();
    }

    public Boolean isDefaultReadOnly() {
        return getPoolProperties().isDefaultReadOnly();
    }

    public boolean isLogAbandoned() {
        return getPoolProperties().isLogAbandoned();
    }

    public boolean isPoolSweeperEnabled() {
        return getPoolProperties().isPoolSweeperEnabled();
    }

    public boolean isRemoveAbandoned() {
        return getPoolProperties().isRemoveAbandoned();
    }

    public int getAbandonWhenPercentageFull() {
        return getPoolProperties().getAbandonWhenPercentageFull();
    }

    public boolean isTestOnBorrow() {
        return getPoolProperties().isTestOnBorrow();
    }

    public boolean isTestOnConnect() {
        return getPoolProperties().isTestOnConnect();
    }

    public boolean isTestOnReturn() {
        return getPoolProperties().isTestOnReturn();
    }

    public boolean isTestWhileIdle() {
        return getPoolProperties().isTestWhileIdle();
    }


    public Boolean getDefaultAutoCommit() {
        return getPoolProperties().getDefaultAutoCommit();
    }

    public Boolean getDefaultReadOnly() {
        return getPoolProperties().getDefaultReadOnly();
    }

    public InterceptorDefinition[] getJdbcInterceptorsAsArray() {
        return getPoolProperties().getJdbcInterceptorsAsArray();
    }

    public boolean getUseLock() {
        return getPoolProperties().getUseLock();
    }

    public boolean isFairQueue() {
        return getPoolProperties().isFairQueue();
    }

    public boolean isJmxEnabled() {
        return getPoolProperties().isJmxEnabled();
    }

    public boolean isUseEquals() {
        return getPoolProperties().isUseEquals();
    }

    public void setAbandonWhenPercentageFull(int percentage) {
        getPoolProperties().setAbandonWhenPercentageFull(percentage);
    }

    public void setAccessToUnderlyingConnectionAllowed(boolean accessToUnderlyingConnectionAllowed) {
        getPoolProperties().setAccessToUnderlyingConnectionAllowed(accessToUnderlyingConnectionAllowed);
    }

    public void setDbProperties(Properties dbProperties) {
        getPoolProperties().setDbProperties(dbProperties);
    }

    public void setDefaultReadOnly(Boolean defaultReadOnly) {
        getPoolProperties().setDefaultReadOnly(defaultReadOnly);
    }

    public void setMaxAge(long maxAge) {
        getPoolProperties().setMaxAge(maxAge);
    }

    public void setName(String name) {
        getPoolProperties().setName(name);
    }

    public String getPoolName() {
        return getPoolProperties().getName();
    }
    

    public void setConnectionProperties(String connectionProperties) {
        getPoolProperties().setConnectionProperties(connectionProperties);
        
    }

    public void setDefaultAutoCommit(Boolean defaultAutoCommit) {
        getPoolProperties().setDefaultAutoCommit(defaultAutoCommit);
    }

    public void setDefaultCatalog(String defaultCatalog) {
        getPoolProperties().setDefaultCatalog(defaultCatalog);
    }

    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        getPoolProperties().setDefaultTransactionIsolation(defaultTransactionIsolation);
    }

    public void setDriverClassName(String driverClassName) {
        getPoolProperties().setDriverClassName(driverClassName);
    }
    
    
    public void setFairQueue(boolean fairQueue) {
        getPoolProperties().setFairQueue(fairQueue);
    }

    
    public void setInitialSize(int initialSize) {
        // TODO Auto-generated method stub
        
    }

    
    public void setInitSQL(String initSQL) {
        // TODO Auto-generated method stub
        
    }

    
    public void setJdbcInterceptors(String jdbcInterceptors) {
        // TODO Auto-generated method stub
        
    }

    
    public void setJmxEnabled(boolean jmxEnabled) {
        // TODO Auto-generated method stub
        
    }

    
    public void setLogAbandoned(boolean logAbandoned) {
        // TODO Auto-generated method stub
        
    }

    
    public void setMaxActive(int maxActive) {
        // TODO Auto-generated method stub
        
    }

     
    public void setMaxIdle(int maxIdle) {
        // TODO Auto-generated method stub
        
    }

    
    public void setMaxWait(int maxWait) {
        // TODO Auto-generated method stub
        
    }

    
    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        // TODO Auto-generated method stub
        
    }

    
    public void setMinIdle(int minIdle) {
        // TODO Auto-generated method stub
        
    }

    
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        // TODO Auto-generated method stub
        
    }

    
    public void setPassword(String password) {
        // TODO Auto-generated method stub
        
    }

    
    public void setRemoveAbandoned(boolean removeAbandoned) {
        // TODO Auto-generated method stub
        
    }

    
    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        // TODO Auto-generated method stub
        
    }

    
    public void setTestOnBorrow(boolean testOnBorrow) {
        // TODO Auto-generated method stub
        
    }

    
    public void setTestOnConnect(boolean testOnConnect) {
        // TODO Auto-generated method stub
        
    }

    
    public void setTestOnReturn(boolean testOnReturn) {
        // TODO Auto-generated method stub
        
    }

    
    public void setTestWhileIdle(boolean testWhileIdle) {
        // TODO Auto-generated method stub
        
    }

    
    public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        // TODO Auto-generated method stub
        
    }

    
    public void setUrl(String url) {
        // TODO Auto-generated method stub
        
    }

    
    public void setUseEquals(boolean useEquals) {
        // TODO Auto-generated method stub
        
    }

    
    public void setUseLock(boolean useLock) {
        // TODO Auto-generated method stub
        
    }

    
    public void setUsername(String username) {
        // TODO Auto-generated method stub
        
    }

    
    public void setValidationInterval(long validationInterval) {
        // TODO Auto-generated method stub
        
    }

    
    public void setValidationQuery(String validationQuery) {
        // TODO Auto-generated method stub
        
    }
    
    /**
     * {@inheritDoc}
     */
    
    public void setValidatorClassName(String className) {
        getPoolProperties().setValidatorClassName(className);
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
        //no op
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
        //noop
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
        //noop
    }

}

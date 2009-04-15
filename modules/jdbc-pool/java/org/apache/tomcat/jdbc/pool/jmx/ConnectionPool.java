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
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class ConnectionPool extends NotificationBroadcasterSupport implements ConnectionPoolMBean  {
    /**
     * logger
     */
    protected static Log log = LogFactory.getLog(ConnectionPool.class);

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

    public ConnectionPool(org.apache.tomcat.jdbc.pool.ConnectionPool pool, boolean for16) {
        super();
        this.pool = pool;
    }

    public org.apache.tomcat.jdbc.pool.ConnectionPool getPool() {
        return pool;
    }
    
    //=================================================================
    //       NOTIFICATION INFO
    //=================================================================
    public static final String NOTIFY_INIT = "INIT FAILED";
    public static final String NOTIFY_CONNECT = "CONNECTION FAILED";
    public static final String NOTIFY_ABANDON = "CONNECTION ABANDONED";
    public static final String SLOW_QUERY_NOTIFICATION = "SLOW QUERY";
    public static final String FAILED_QUERY_NOTIFICATION = "FAILED QUERY";
    
    
    
    @Override 
    public MBeanNotificationInfo[] getNotificationInfo() { 
        MBeanNotificationInfo[] pres = super.getNotificationInfo();
        MBeanNotificationInfo[] loc = getDefaultNotificationInfo();
        MBeanNotificationInfo[] aug = new MBeanNotificationInfo[pres.length + loc.length];
        if (pres.length>0) System.arraycopy(pres, 0, aug, 0, pres.length);
        if (loc.length >0) System.arraycopy(loc, 0, aug, pres.length, loc.length);    
        return aug; 
    } 
    
    public static MBeanNotificationInfo[] getDefaultNotificationInfo() {
        String[] types = new String[] {NOTIFY_INIT, NOTIFY_CONNECT, NOTIFY_ABANDON, SLOW_QUERY_NOTIFICATION, FAILED_QUERY_NOTIFICATION}; 
        String name = Notification.class.getName(); 
        String description = "A connection pool error condition was met."; 
        MBeanNotificationInfo info = new MBeanNotificationInfo(types, name, description); 
        return new MBeanNotificationInfo[] {info};
    }
    
    /**
     * Return true if the notification was sent successfully, false otherwise.
     * @param type
     * @param message
     * @return
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
    
    public boolean isPoolSweeperEnabled() {
        return pool.getPoolProperties().isPoolSweeperEnabled();
    }
    
    public int getNumIdle() {
        return getIdle();
    }
    
    public int getNumActive() {
        return getActive();
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
    public Properties getDbProperties() {
        return null; //pool.getPoolProperties().getDbProperties();
    }
    public String getUrl() {
        return pool.getPoolProperties().getUrl();
    }
    public String getDriverClassName() {
        return pool.getPoolProperties().getDriverClassName();
    }
    public boolean isDefaultAutoCommit() {
        return pool.getPoolProperties().isDefaultAutoCommit();
    }
    public boolean isDefaultReadOnly() {
        return pool.getPoolProperties().isDefaultReadOnly();
    }
    public int getDefaultTransactionIsolation() {
        return pool.getPoolProperties().getDefaultTransactionIsolation();
    }
    public String getConnectionProperties() {
        return pool.getPoolProperties().getConnectionProperties();
    }
    public String getDefaultCatalog() {
        return pool.getPoolProperties().getDefaultCatalog();
    }
    public int getInitialSize() {
        return pool.getPoolProperties().getInitialSize();
    }
    public int getMaxActive() {
        return pool.getPoolProperties().getMaxActive();
    }
    public int getMaxIdle() {
        return pool.getPoolProperties().getMaxIdle();
    }
    public int getMinIdle() {
        return pool.getPoolProperties().getMinIdle();
    }
    public int getMaxWait() {
        return pool.getPoolProperties().getMaxWait();
    }
    public String getValidationQuery() {
        return pool.getPoolProperties().getValidationQuery();
    }
    public boolean isTestOnBorrow() {
        return pool.getPoolProperties().isTestOnBorrow();
    }
    public boolean isTestOnReturn() {
        return pool.getPoolProperties().isTestOnReturn();
    }
    public boolean isTestWhileIdle() {
        return pool.getPoolProperties().isTestWhileIdle();
    }
    public int getTimeBetweenEvictionRunsMillis() {
        return pool.getPoolProperties().getTimeBetweenEvictionRunsMillis();
    }
    public int getNumTestsPerEvictionRun() {
        return pool.getPoolProperties().getNumTestsPerEvictionRun();
    }
    public int getMinEvictableIdleTimeMillis() {
        return pool.getPoolProperties().getMinEvictableIdleTimeMillis();
    }
    public boolean isAccessToUnderlyingConnectionAllowed() {
        return pool.getPoolProperties().isAccessToUnderlyingConnectionAllowed();
    }
    public boolean isRemoveAbandoned() {
        return pool.getPoolProperties().isRemoveAbandoned();
    }
    public int getRemoveAbandonedTimeout() {
        return pool.getPoolProperties().getRemoveAbandonedTimeout();
    }
    public boolean isLogAbandoned() {
        return pool.getPoolProperties().isLogAbandoned();
    }
    public int getLoginTimeout() {
        return pool.getPoolProperties().getLoginTimeout();
    }
    public String getName() {
        return pool.getPoolProperties().getName();
    }
    public String getPassword() {
        return "";
    }
    public String getUsername() {
        return pool.getPoolProperties().getUsername();
    }
    public long getValidationInterval() {
        return pool.getPoolProperties().getValidationInterval();
    }
    public String getInitSQL() {
        return pool.getPoolProperties().getInitSQL();
    }
    public boolean isTestOnConnect() {
        return pool.getPoolProperties().isTestOnConnect();
    }
    public String getJdbcInterceptors() {
        return pool.getPoolProperties().getJdbcInterceptors();
    }
    public int getWaitCount() {
        return pool.getWaitCount();
    }

}

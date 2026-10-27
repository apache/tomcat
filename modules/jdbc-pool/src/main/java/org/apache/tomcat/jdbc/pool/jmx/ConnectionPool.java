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

import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition;
import org.apache.tomcat.jdbc.pool.PoolUtilities;
import org.apache.tomcat.jdbc.pool.Validator;

/**
 * JMX MBean wrapper for a {@link org.apache.tomcat.jdbc.pool.ConnectionPool}.
 * Provides management and monitoring capabilities for connection pool statistics,
 * operations, and configuration.
 */
public class ConnectionPool extends NotificationBroadcasterSupport
        implements ConnectionPoolMBean, MBeanRegistration {

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
    protected ConcurrentLinkedQueue<NotificationListener> listeners =
            new ConcurrentLinkedQueue<>();

    /**
     * How many delivery tasks the notifier thread accepts into its queue
     * before further notifications are dropped and logged rather than
     * queued. The queue bounds the memory a flooded event source - the pool
     * notifying once per failed borrow while it is exhausted - can make the
     * pool allocate; it is not a promise that every notification is
     * delivered.
     */
    private static final int NOTIFY_QUEUE_CAPACITY = 2000;

    /**
     * The single thread that pool notifications are delivered on. Delivery
     * goes through here rather than running on the thread that reports the
     * condition, because several of the pool's notifications are raised from
     * the connection lifecycle - some of them while holding the lock of the
     * connection concerned - and a monitoring listener that is slow or
     * blocked must not stall those paths. One worker keeps the notifications
     * in the order the pool raised them; the bounded queue and the drop on
     * overflow keep a listener that stops draining from growing the queue
     * without limit. The thread is a daemon, is started on the first
     * notification and dies when it has been idle, so a pool that never
     * notifies costs nothing, and it never holds up the JVM on shutdown.
     * Shared by every pool MBean in the JVM: notifications of one pool can
     * queue behind those of another, but a blocked listener then only
     * starves delivery - the drop above is what a pool's operations feel,
     * not the block.
     */
    private static final ThreadPoolExecutor NOTIFIER = new ThreadPoolExecutor(
            0, 1, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(NOTIFY_QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "JDBC-Pool-JMX-Notifier");
                t.setDaemon(true);
                return t;
            });

    /**
     * the ObjectName of this pool.
     */
    private ObjectName oname = null;

    /**
     * Creates a new JMX wrapper for the given connection pool.
     *
     * @param pool the connection pool to wrap
     */
    public ConnectionPool(org.apache.tomcat.jdbc.pool.ConnectionPool pool) {
        // hand the JMX listener delivery to the notifier thread as well;
        // see notify(String,String)
        super(NOTIFIER);
        this.pool = pool;
    }

    /**
     * Returns the underlying connection pool instance.
     *
     * @return the wrapped connection pool
     */
    public org.apache.tomcat.jdbc.pool.ConnectionPool getPool() {
        return pool;
    }

    /**
     * Returns the pool configuration for the wrapped connection pool.
     *
     * @return the pool configuration properties
     */
    public PoolConfiguration getPoolProperties() {
        return pool.getPoolProperties();
    }

    /**
     * Returns the JMX ObjectName under which this MBean is registered.
     *
     * @return the JMX ObjectName, or {@code null} if not yet registered
     */
    public ObjectName getObjectName() {
        return oname;
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name)
            throws Exception {
        this.oname = name;
        return name;
    }

    @Override
    public void postRegister(Boolean registrationDone) {
    }

    @Override
    public void preDeregister() throws Exception {
    }

    @Override
    public void postDeregister() {
    }

    //=================================================================
    //       NOTIFICATION INFO
    //=================================================================
    // The notification types this MBean sends are declared as constants on
    // the ConnectionPoolMBean interface, under POOL NOTIFICATIONS.

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        MBeanNotificationInfo[] pres = super.getNotificationInfo();
        MBeanNotificationInfo[] loc = getDefaultNotificationInfo();
        MBeanNotificationInfo[] aug = new MBeanNotificationInfo[pres.length + loc.length];
        if (pres.length>0) {
            System.arraycopy(pres, 0, aug, 0, pres.length);
        }
        if (loc.length >0) {
            System.arraycopy(loc, 0, aug, pres.length, loc.length);
        }
        return aug;
    }

    /**
     * Returns the default notification info describing all notification types
     * that this MBean can send.
     *
     * @return array of MBeanNotificationInfo describing supported notifications
     */
    public static MBeanNotificationInfo[] getDefaultNotificationInfo() {
        String[] types = new String[] {NOTIFY_INIT, NOTIFY_CONNECT, NOTIFY_ABANDON, SLOW_QUERY_NOTIFICATION,
                FAILED_QUERY_NOTIFICATION, SUSPECT_ABANDONED_NOTIFICATION, POOL_EMPTY, SUSPECT_RETURNED_NOTIFICATION};
        String name = Notification.class.getName();
        String description = "A connection pool error condition was met.";
        MBeanNotificationInfo info = new MBeanNotificationInfo(types, name, description);
        return new MBeanNotificationInfo[] {info};
    }

    /**
     * Reports a condition to the pool's notification listeners.
     * <p>
     * The delivery is asynchronous: both the listeners reached through JMX -
     * delivered by {@link #sendNotification(Notification)} - and the ones
     * registered locally with {@link #addListener(NotificationListener)}
     * receive the notification on the pool's notification thread, not on the
     * thread that calls this method. The pool raises several of its
     * notifications from the connection lifecycle, some of them while holding
     * the lock of the connection concerned, and a monitoring listener that is
     * slow or blocked must not reach those paths. The notification itself is
     * still built on the calling thread, so it carries the time, sequence
     * number and serializable source of the condition it reports - see
     * {@link #getNotificationSource()} - and, since the notifier is a single
     * thread, listeners observe notifications in the order the pool reported
     * them.
     *
     * @param type The notification type
     * @param message The message
     * @return true if the notification was accepted for delivery, false if it
     *         was dropped - which happens when the delivery queue is full or
     *         the notification could not be built. The result reports the
     *         handoff only: it says nothing about whether any listener was
     *         reached or what a listener did with the notification.
     */
    public boolean notify(final String type, String message) {
        try {
            Notification n = new Notification(
                    type,
                    getNotificationSource(),
                    sequence.incrementAndGet(),
                    System.currentTimeMillis(),
                    "["+type+"] "+message);
            // queues the notification for the JMX listeners on the notifier
            sendNotification(n);
            // and the same for the local listeners, behind the JMX handoff so
            // that per notification the listener order stays as it was when
            // delivery was synchronous
            NOTIFIER.execute(() -> deliverLocally(n, type));
            return true;
        }catch (RejectedExecutionException x) {
            // the delivery queue is full; dropping the notification is the
            // point of the bound, and must not fail the pool operation that
            // reported the condition
            log.warn("Dropping notification of type ["+type+"] for pool ["
                    +getNotificationSource()+"]: the notification delivery queue ("
                    +NOTIFY_QUEUE_CAPACITY+" deliveries) is full");
            return false;
        }catch (Exception x) {
            if (log.isDebugEnabled()) {
                log.debug("Notify failed. Type="+type+"; Message="+message,x);
            }
            return false;
        }

    }

    /**
     * Hands a notification to the listeners registered with this MBean
     * outside JMX. Runs on the notifier thread. A listener that throws is
     * contained here: its exception may not kill the notifier thread that
     * every pool in the JVM delivers its notifications on.
     *
     * @param n         the notification to deliver
     * @param type      the notification type, for the failure log
     */
    private void deliverLocally(Notification n, String type) {
        try {
            for (NotificationListener listener : listeners) {
                listener.handleNotification(n, this);
            }
        } catch (Exception x) {
            if (log.isDebugEnabled()) {
                log.debug("Local notification listener failed. Type="+type,x);
            }
        }
    }

    /**
     * Returns the source the pool's notifications are sent with.
     * <p>
     * {@link Notification} marshals its source, so a listener outside this
     * JVM - one reached through a JMX connector, or one that hands the
     * notification on to another process - only receives the notification at
     * all if the source is serializable. This MBean is not, and neither is the
     * pool it wraps, so the source is the {@link ObjectName} the MBean is
     * registered under, which names the pool and is serializable. A pool MBean
     * that has never been registered has no such name: the source is then a
     * string of the shape the name would have had.
     *
     * @return the registered ObjectName, or a string naming the pool if the
     *         MBean is not registered
     */
    protected Object getNotificationSource() {
        // the registration handle is written by whichever thread registered the
        // MBean; read it once so a single notification is sent with one source
        // whatever happens to the field while the notification is on its way
        ObjectName name = oname;
        if (name != null) {
            return name;
        }
        return org.apache.tomcat.jdbc.pool.ConnectionPool.POOL_JMX_TYPE_PREFIX
                + "ConnectionPool,name=" + getPoolName();
    }

    /**
     * Adds a local notification listener that receives pool notifications
     * without requiring JMX registration.
     *
     * @param list the notification listener to add
     */
    public void addListener(NotificationListener list) {
        listeners.add(list);
    }

    /**
     * Removes a local notification listener from the pool.
     * @param list the notification listener to remove
     * @return {@code true} if the listener was removed
     */
    public boolean removeListener(NotificationListener list) {
        return listeners.remove(list);
    }

    //=================================================================
    //       POOL STATS
    //=================================================================

    @Override
    public int getSize() {
        return pool.getSize();
    }

    @Override
    public int getIdle() {
        return pool.getIdle();
    }

    @Override
    public int getActive() {
        return pool.getActive();
    }

    @Override
    public int getNumIdle() {
        return getIdle();
    }

    @Override
    public int getNumActive() {
        return getActive();
    }

    @Override
    public int getWaitCount() {
        return pool.getWaitCount();
    }

    @Override
    public long getBorrowedCount() {
        return pool.getBorrowedCount();
    }

    @Override
    public long getReturnedCount() {
        return pool.getReturnedCount();
    }

    @Override
    public long getCreatedCount() {
        return pool.getCreatedCount();
    }

    @Override
    public long getReleasedCount() {
        return pool.getReleasedCount();
    }

    @Override
    public long getReconnectedCount() {
        return pool.getReconnectedCount();
    }

    @Override
    public long getRemoveAbandonedCount() {
        return pool.getRemoveAbandonedCount();
    }

    @Override
    public long getReleasedIdleCount() {
        return pool.getReleasedIdleCount();
    }

    //=================================================================
    //       POOL OPERATIONS
    //=================================================================
    @Override
    public void checkIdle() {
        pool.checkIdle();
    }

    @Override
    public void checkAbandoned() {
        pool.checkAbandoned();
    }

    @Override
    public void testIdle() {
        pool.testAllIdle();
    }

    @Override
    public void resetStats() {
        pool.resetStats();
    }

    //=================================================================
    //       POOL PROPERTIES
    //=================================================================
    //=========================================================
    //  PROPERTIES / CONFIGURATION
    //=========================================================


    @Override
    public String getConnectionProperties() {
        return getPoolProperties().getConnectionProperties();
    }

    @Override
    public Properties getDbProperties() {
        return PoolUtilities.cloneWithoutPassword(getPoolProperties().getDbProperties());
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
        return this.getPoolName();
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
        boolean wasEnabled = getPoolProperties().isPoolSweeperEnabled();
        getPoolProperties().setMaxAge(maxAge);
        //make sure the pool is properly configured
        pool.checkPoolConfiguration(getPoolProperties());
        poolCleanerAttributeUpdated(wasEnabled);
    }

    @Override
    public void setName(String name) {
        getPoolProperties().setName(name);
    }

    @Override
    public String getPoolName() {
        return getPoolProperties().getName();
    }


    @Override
    public void setConnectionProperties(String connectionProperties) {
        getPoolProperties().setConnectionProperties(connectionProperties);

    }

    @Override
    public void setDefaultAutoCommit(Boolean defaultAutoCommit) {
        getPoolProperties().setDefaultAutoCommit(defaultAutoCommit);
    }

    @Override
    public void setDefaultCatalog(String defaultCatalog) {
        getPoolProperties().setDefaultCatalog(defaultCatalog);
    }

    @Override
    public void setDefaultTransactionIsolation(int defaultTransactionIsolation) {
        getPoolProperties().setDefaultTransactionIsolation(defaultTransactionIsolation);
    }

    @Override
    public void setDriverClassName(String driverClassName) {
        getPoolProperties().setDriverClassName(driverClassName);
    }


    @Override
    public void setFairQueue(boolean fairQueue) {
        // noop - this pool is already running
        throw new UnsupportedOperationException();
    }


    @Override
    public void setInitialSize(int initialSize) {
        // noop - this pool is already running
        throw new UnsupportedOperationException();

    }


    @Override
    public void setInitSQL(String initSQL) {
        getPoolProperties().setInitSQL(initSQL);

    }


    @Override
    public void setJdbcInterceptors(String jdbcInterceptors) {
        // noop - this pool is already running
        throw new UnsupportedOperationException();
    }


    @Override
    public void setJmxEnabled(boolean jmxEnabled) {
        // noop - this pool is already running and obviously jmx enabled
        throw new UnsupportedOperationException();
    }


    @Override
    public void setLogAbandoned(boolean logAbandoned) {
        getPoolProperties().setLogAbandoned(logAbandoned);
    }


    @Override
    public void setMaxActive(int maxActive) {
        getPoolProperties().setMaxActive(maxActive);
        //make sure the pool is properly configured
        pool.checkPoolConfiguration(getPoolProperties());
    }


    @Override
    public void setMaxIdle(int maxIdle) {
        getPoolProperties().setMaxIdle(maxIdle);
        //make sure the pool is properly configured
        pool.checkPoolConfiguration(getPoolProperties());

    }


    @Override
    public void setMaxWait(int maxWait) {
        getPoolProperties().setMaxWait(maxWait);
    }


    @Override
    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        boolean wasEnabled = getPoolProperties().isPoolSweeperEnabled();
        getPoolProperties().setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        poolCleanerAttributeUpdated(wasEnabled);
    }


    @Override
    public void setMinIdle(int minIdle) {
        getPoolProperties().setMinIdle(minIdle);
        //make sure the pool is properly configured
        pool.checkPoolConfiguration(getPoolProperties());
    }


    @Override
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        getPoolProperties().setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    }


    @Override
    public void setPassword(String password) {
        getPoolProperties().setPassword(password);
    }


    @Override
    public void setRemoveAbandoned(boolean removeAbandoned) {
        boolean wasEnabled = getPoolProperties().isPoolSweeperEnabled();
        getPoolProperties().setRemoveAbandoned(removeAbandoned);
        poolCleanerAttributeUpdated(wasEnabled);
    }


    @Override
    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        boolean wasEnabled = getPoolProperties().isPoolSweeperEnabled();
        getPoolProperties().setRemoveAbandonedTimeout(removeAbandonedTimeout);
        poolCleanerAttributeUpdated(wasEnabled);
    }


    @Override
    public void setTestOnBorrow(boolean testOnBorrow) {
        getPoolProperties().setTestOnBorrow(testOnBorrow);
    }


    @Override
    public void setTestOnConnect(boolean testOnConnect) {
        getPoolProperties().setTestOnConnect(testOnConnect);
    }


    @Override
    public void setTestOnReturn(boolean testOnReturn) {
        getPoolProperties().setTestOnReturn(testOnReturn);
    }


    @Override
    public void setTestWhileIdle(boolean testWhileIdle) {
        boolean wasEnabled = getPoolProperties().isPoolSweeperEnabled();
        getPoolProperties().setTestWhileIdle(testWhileIdle);
        poolCleanerAttributeUpdated(wasEnabled);
    }


    @Override
    public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        boolean wasEnabled = getPoolProperties().isPoolSweeperEnabled();
        getPoolProperties().setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        //make sure the pool is properly configured
        pool.checkPoolConfiguration(getPoolProperties());
        poolCleanerAttributeUpdated(wasEnabled);
    }

    /**
     * Starts/stops pool cleaner thread as necessary after its configuration properties
     * were updated.
     *
     * This method must be called <b>after</b> configuration properties affecting the pool cleaner
     * have been updated.
     *
     * @param wasEnabled whether the pool cleaner was enabled <b>before</b> the configuration change occurred.
     */
    private void poolCleanerAttributeUpdated(boolean wasEnabled) {
        boolean shouldBeEnabled = getPoolProperties().isPoolSweeperEnabled();
        //make sure pool cleaner starts/stops when it should
        if (!wasEnabled && shouldBeEnabled) {
            pool.initializePoolCleaner(getPoolProperties());
        } else if (wasEnabled) {
            pool.terminatePoolCleaner();
            if (shouldBeEnabled) {
                pool.initializePoolCleaner(getPoolProperties());
            }
        }
    }

    @Override
    public void setUrl(String url) {
        getPoolProperties().setUrl(url);
    }


    @Override
    public void setUseEquals(boolean useEquals) {
        getPoolProperties().setUseEquals(useEquals);
    }


    @Override
    public void setUseLock(boolean useLock) {
        getPoolProperties().setUseLock(useLock);
    }


    @Override
    public void setUsername(String username) {
        getPoolProperties().setUsername(username);
    }


    @Override
    public void setValidationInterval(long validationInterval) {
        getPoolProperties().setValidationInterval(validationInterval);
    }


    @Override
    public void setValidationQuery(String validationQuery) {
        getPoolProperties().setValidationQuery(validationQuery);
    }

    @Override
    public void setValidationQueryTimeout(int validationQueryTimeout) {
        getPoolProperties().setValidationQueryTimeout(validationQueryTimeout);
    }


    @Override
    public void setValidatorClassName(String className) {
        getPoolProperties().setValidatorClassName(className);
    }


    @Override
    public int getSuspectTimeout() {
        return getPoolProperties().getSuspectTimeout();
    }


    @Override
    public void setSuspectTimeout(int seconds) {
        boolean wasEnabled = getPoolProperties().isPoolSweeperEnabled();
        getPoolProperties().setSuspectTimeout(seconds);
        poolCleanerAttributeUpdated(wasEnabled);
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
    public void setValidator(Validator validator) {
        getPoolProperties().setValidator(validator);
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

    /**
     * {@inheritDoc}
     * @deprecated The disposable connection facade is always used, so this
     *             property no longer has any effect.
     */
    @Override
    @Deprecated
    public void setUseDisposableConnectionFacade(boolean useDisposableConnectionFacade) {
        getPoolProperties().setUseDisposableConnectionFacade(useDisposableConnectionFacade);
    }

    /**
     * {@inheritDoc}
     * @deprecated The disposable connection facade is always used, so this
     *             property no longer has any effect.
     */
    @Override
    @Deprecated
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
        // noop - this pool is already running
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getUseStatementFacade() {
        return getPoolProperties().getUseStatementFacade();
    }

    @Override
    public void setUseStatementFacade(boolean useStatementFacade) {
        getPoolProperties().setUseStatementFacade(useStatementFacade);
    }

    @Override
    public void purge() {
        pool.purge();

    }

    @Override
    public void purgeOnReturn() {
        pool.purgeOnReturn();

    }
}

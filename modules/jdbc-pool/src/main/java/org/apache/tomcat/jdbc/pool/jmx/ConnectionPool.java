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
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition;
import org.apache.tomcat.jdbc.pool.PoolUtilities;
import org.apache.tomcat.jdbc.pool.Validator;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * JMX MBean wrapper for a {@link org.apache.tomcat.jdbc.pool.ConnectionPool}. Provides management and monitoring
 * capabilities for connection pool statistics, operations, and configuration.
 * <p>
 * The ObjectName this MBean is registered under is fixed when the registration succeeds. A refused registration leaves
 * {@link #getObjectName()} null until a later registration succeeds; {@link #getName()} and {@link #getPoolName()}
 * report the pool's live name and may differ from that registration name after a rename.
 */
public class ConnectionPool extends NotificationBroadcasterSupport implements ConnectionPoolMBean, MBeanRegistration {

    private static final Log log = LogFactory.getLog(ConnectionPool.class);

    /**
     * The pool this MBean wraps, assigned once by the constructor and never swapped: attribute reads consult the
     * live configuration the pool shares with its DataSource, the operations run against this pool, and it is the
     * pool the reported conditions describe. {@link #getPool()} hands out the same reference in-process.
     */
    protected org.apache.tomcat.jdbc.pool.ConnectionPool pool = null;
    /**
     * sequence for JMX notifications
     */
    protected AtomicInteger sequence = new AtomicInteger(0);

    /**
     * Listeners that are local and interested in our notifications, no need for JMX
     */
    protected ConcurrentLinkedQueue<NotificationListener> listeners = new ConcurrentLinkedQueue<>();

    /**
     * How many delivery tasks the notifier thread accepts into its queue before further notifications are dropped and
     * logged rather than queued. The queue bounds the memory a flooded event source - the pool notifying once per
     * failed borrow while it is exhausted - can make the pool allocate; it is not a promise that every notification is
     * delivered.
     */
    private static final int NOTIFY_QUEUE_CAPACITY = 2000;

    /**
     * The single thread that pool notifications are delivered on. Delivery goes through here rather than running on the
     * thread that reports the condition, because several of the pool's notifications are raised from the connection
     * lifecycle - some of them while holding the lock of the connection concerned - and a monitoring listener that is
     * slow or blocked must not stall those paths. One worker keeps the notifications one reporting thread raised in
     * the order that thread raised them: the sequence number is stamped on the reporting thread before the hand-off,
     * so it records the order the notifications were built in, and two threads reporting at the same time can hand off
     * - and so be delivered - in either order. The bounded queue and the drop on overflow keep a listener that stops
     * draining from growing the queue without limit. The thread is a daemon, is started on the first notification and
     * dies when it has been idle, so a pool that never notifies costs nothing, and it never holds up the JVM on
     * shutdown. Shared by every pool MBean in the JVM: notifications of one pool can queue behind those of another,
     * but a blocked listener then only starves delivery - the drop above is what a pool's operations feel, not the
     * block.
     */
    private static final ThreadPoolExecutor NOTIFIER = new ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(NOTIFY_QUEUE_CAPACITY), r -> {
                Thread t = new Thread(r, "JDBC-Pool-JMX-Notifier");
                t.setDaemon(true);
                return t;
            });

    /**
     * the ObjectName of this pool, or {@code null} while this MBean is not registered - before its registration, after
     * one the server has refused, and after its deregistration. Written by the threads that register and deregister the
     * MBean and read by the pool's own threads, so it is volatile - as it already is on the
     * {@link org.apache.tomcat.jdbc.pool.DataSource} that publishes this MBean - to make those writes visible to the
     * threads that raise notifications.
     */
    private volatile ObjectName oname = null;

    /**
     * Creates a new JMX wrapper for the given connection pool.
     *
     * @param pool the connection pool to wrap
     */
    public ConnectionPool(org.apache.tomcat.jdbc.pool.ConnectionPool pool) {
        // Run the per-listener hand-offs that sendNotification builds on the
        // thread that calls sendNotification instead of queueing one task per
        // listener: notify() hands a notification to the notifier as a single
        // task and calls sendNotification on that thread, so a queued
        // hand-off would in the end only ever run on the very thread calling
        // it - and a delivery queue that stayed full could refuse some of
        // those re-queued tasks after the queue had accepted the notification
        // as a whole, splitting one notification across its own listeners.
        // Run inline, the bounded queue sees one task per notification and
        // accepts or refuses the notification as a whole - see
        // NOTIFY_QUEUE_CAPACITY and notify(String,String)
        super(command -> command.run());
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
     * @return the JMX ObjectName, or {@code null} while this MBean is not registered - before its registration, after
     *             one the server has refused, and after its deregistration
     */
    public ObjectName getObjectName() {
        return oname;
    }

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        this.oname = name;
        return name;
    }

    /**
     * Drops the registration name when the MBean server did not complete this MBean's registration.
     * <p>
     * The server calls {@link #preRegister(MBeanServer, ObjectName)} before it has looked the requested name up in its
     * repository, so the name stored there can still be refused - the usual reason being another MBean that already
     * holds it, most often the leftover of a pool that died without deregistering. After refusing a registration the
     * server runs no {@link #postDeregister()}: per the {@link MBeanRegistration} contract a {@code false}
     * {@code registrationDone} reports that the registration phase failed, which leaves this callback as the only
     * chance to undo what {@code preRegister} wrote.
     * <p>
     * A name kept through a refusal is a name this MBean never acquired, and both ways it is used then act on a
     * publication that never happened. The pool's notifications - the pool keeps running - attribute themselves to the
     * MBean that does answer for that name, while {@link #getNotificationSource()} names the pool as a string
     * precisely so that an MBean nobody published points at nobody else's name. And the connections, which publish
     * themselves below {@link #getObjectName()} as soon as their physical session is up, are offered to the server
     * below a stranger's name: the publish either collides with the children of the MBean that owns that name - a
     * stale pool MBean holds the very same child names - or, the child name being free, publishes a connection of this
     * pool into a subtree this pool has no claim to and no release for, at one error log per connection. The name is
     * also what {@link org.apache.tomcat.jdbc.pool.ConnectionPool#registerConnectionMBeans()} waits for, so the gate
     * that keeps the connections of an unpublished pool unpublished opens for a name this MBean was refused. The
     * {@link org.apache.tomcat.jdbc.pool.DataSource} that publishes this MBean gives up its own side of a refused
     * registration the same way.
     */
    @Override
    public void postRegister(Boolean registrationDone) {
        if (!Boolean.TRUE.equals(registrationDone)) {
            this.oname = null;
        }
    }

    @Override
    public void preDeregister() throws Exception {
    }

    /**
     * Drops the registration name.
     * <p>
     * The MBean server invokes this when the MBean is deregistered, and the pool it wraps may well keep running - a
     * container that takes its DataSource back releases the pool MBean without closing the pool. From then on the name
     * resolves to no MBean: notifications the pool still raises must not attribute themselves to it (see
     * {@link #getNotificationSource()}, which falls back to naming the pool as a string), and no further MBean may be
     * published below it. {@link #preRegister(MBeanServer, ObjectName)} writes the name again if this MBean is ever
     * registered anew.
     */
    @Override
    public void postDeregister() {
        this.oname = null;
    }

    // The notification types this MBean sends are declared as constants on
    // the ConnectionPoolMBean interface.

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        MBeanNotificationInfo[] pres = super.getNotificationInfo();
        MBeanNotificationInfo[] loc = getDefaultNotificationInfo();
        MBeanNotificationInfo[] aug = new MBeanNotificationInfo[pres.length + loc.length];
        if (pres.length > 0) {
            System.arraycopy(pres, 0, aug, 0, pres.length);
        }
        if (loc.length > 0) {
            System.arraycopy(loc, 0, aug, pres.length, loc.length);
        }
        return aug;
    }

    /**
     * Returns the default notification info describing all notification types that this MBean can send.
     *
     * @return array of MBeanNotificationInfo describing supported notifications
     */
    public static MBeanNotificationInfo[] getDefaultNotificationInfo() {
        String[] types = new String[] { NOTIFY_INIT, NOTIFY_CONNECT, NOTIFY_ABANDON, SLOW_QUERY_NOTIFICATION,
                FAILED_QUERY_NOTIFICATION, SUSPECT_ABANDONED_NOTIFICATION, POOL_EMPTY, SUSPECT_RETURNED_NOTIFICATION };
        String name = Notification.class.getName();
        String description = "A connection pool error condition was met.";
        MBeanNotificationInfo info = new MBeanNotificationInfo(types, name, description);
        return new MBeanNotificationInfo[] { info };
    }

    /**
     * Reports a condition to the pool's notification listeners.
     * <p>
     * The delivery is asynchronous: the notification is handed to the pool's notification thread as one task, and that
     * task delivers it - first through {@link #sendNotification(Notification)} to the listeners reached through JMX,
     * then to the ones registered locally with {@link #addListener(NotificationListener)}. Nothing of the delivery runs
     * on the thread that calls this method, not even the JMX listeners' filters: the pool raises several of its
     * notifications from the connection lifecycle, some of them while holding the lock of the connection concerned,
     * and a monitoring listener - or a filter - that is slow or blocked must not reach those paths. The notification
     * itself is still built on the calling thread, so it carries the time, sequence number and serializable source of
     * the condition it reports - see {@link #getNotificationSource()} - and, since the notifier is a single thread and
     * delivers a notification to all its listeners in one go, every listener observes the notifications one reporting
     * thread raised in the order that thread raised them: the sequence number stamped on the calling thread records
     * that construction order, and notifications two threads raise at the same time may be delivered in either order.
     * A listener registered both through JMX and locally is reached through JMX before it is reached locally, as it
     * was when delivery was synchronous.
     *
     * @param type    The notification type
     * @param message The message
     *
     * @return true if the notification was accepted for delivery as a whole, false if it was dropped as a whole -
     *             which happens when the delivery queue is full or the notification could not be built. A false means
     *             nothing was scheduled: no listener will receive this notification. The result otherwise reports the
     *             handoff only: it says nothing about whether any listener was reached or what a listener did with
     *             the notification.
     */
    public boolean notify(final String type, String message) {
        try {
            Notification n = new Notification(type, getNotificationSource(), sequence.incrementAndGet(),
                    System.currentTimeMillis(), "[" + type + "] " + message);
            // Hand the notification over as one task: the delivery queue is
            // then the single gate that accepts or refuses it, so the result
            // and the drop log below stay true to what was scheduled - a
            // refusal never means the notification partly reached its
            // listeners, it means nothing was queued at all. The order the
            // two halves of the delivery run in is the one the javadoc above
            // promises.
            NOTIFIER.execute(() -> {
                try {
                    sendNotification(n);
                } catch (Exception x) {
                    // sendNotification contains what a listener or a filter
                    // throws, job per job; this catch is a defence against a
                    // failure escaping it, which must not skip the local
                    // listeners of this notification
                    if (log.isDebugEnabled()) {
                        log.debug("JMX notification hand-off failed. Type=" + type, x);
                    }
                }
                deliverLocally(n, type);
            });
            return true;
        } catch (RejectedExecutionException x) {
            // The delivery queue is full; dropping the notification is the
            // point of the bound, and must not fail the pool operation that
            // reported the condition
            log.warn(MessageFactory.get("jmxConnectionPool.droppingNotification",
                    type, getNotificationSource(), NOTIFY_QUEUE_CAPACITY));
            return false;
        } catch (Exception x) {
            if (log.isDebugEnabled()) {
                log.debug("Notify failed. Type=" + type + "; Message=" + message, x);
            }
            return false;
        }

    }

    /**
     * Hands a notification to the listeners registered with this MBean outside JMX. Runs on the notifier thread. Each
     * listener is handed the notification on its own and what one of them throws is contained at that one listener: it
     * may not kill the notifier thread that every pool in the JVM delivers its notifications on, and it may not
     * suppress the delivery of that notification to the listeners registered behind the thrower either. An
     * {@link Error} is not contained - as it is not on the JMX half of the delivery, where the JDK broadcaster catches
     * only what a listener throws - and takes the notifier thread down; the pool replaces the worker as soon as the
     * executor notices it is gone, so the next notification is delivered by a live worker.
     *
     * @param n    the notification to deliver
     * @param type the notification type, for the failure log
     */
    private void deliverLocally(Notification n, String type) {
        for (NotificationListener listener : listeners) {
            try {
                listener.handleNotification(n, this);
            } catch (Exception x) {
                if (log.isDebugEnabled()) {
                    log.debug("Local notification listener failed. Type=" + type, x);
                }
            }
        }
    }

    /**
     * Returns the source the pool's notifications are sent with.
     * <p>
     * {@link Notification} marshals its source, so a listener outside this JVM - one reached through a JMX connector,
     * or one that hands the notification on to another process - only receives the notification at all if the source is
     * serializable. This MBean is not, and neither is the pool it wraps, so the source is the {@link ObjectName} the
     * MBean is registered under, which names the pool and is serializable. A pool MBean that is not registered - one
     * that has never been registered, or one the server has taken back while its pool keeps running - has no such
     * name: the source is then a string that names the pool.
     *
     * @return the registered ObjectName, or a string naming the pool if the MBean is not registered. That fallback is
     *             a diagnostic label, not necessarily a valid ObjectName
     */
    protected Object getNotificationSource() {
        // The registration handle is written by whichever thread registers or
        // deregisters the MBean; the field is volatile so those writes become
        // visible to the pool threads that read it here, and it is read once so
        // that a single notification is sent with one source whatever happens
        // to the field while the notification is on its way
        ObjectName name = oname;
        if (name != null) {
            return name;
        }
        return org.apache.tomcat.jdbc.pool.ConnectionPool.POOL_JMX_TYPE_PREFIX + "ConnectionPool,name=" + getPoolName();
    }

    /**
     * Adds a local notification listener that receives pool notifications without requiring JMX registration.
     *
     * @param list the notification listener to add
     */
    public void addListener(NotificationListener list) {
        listeners.add(list);
    }

    /**
     * Removes a local notification listener from the pool.
     *
     * @param list the notification listener to remove
     *
     * @return {@code true} if the listener was removed
     */
    public boolean removeListener(NotificationListener list) {
        return listeners.remove(list);
    }

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


    /**
     * Returns the connection properties with any credential they carry masked out, so that a read of this attribute
     * does not hand back in cleartext the password {@link #getPassword()} exists to withhold. The write is not masked:
     * {@link #setConnectionProperties(String)} takes the string as it is meant to reach the driver, which is the same
     * read/write asymmetry the password attribute has.
     * <p>
     * The masked value is a display value, not a safe value to write back: the ordinary read-modify-write replaces the
     * credential with the mask and leaves the pool unable to authenticate. Change the credential through
     * {@link #setPassword(String)} and {@link #setUsername(String)}, or read the raw value from {@link #getPool()}.
     * {@inheritDoc}
     */
    @Override
    public String getConnectionProperties() {
        return PoolProperties.maskCredentials(getPoolProperties().getConnectionProperties());
    }

    /**
     * Returns a copy of the database properties with the password removed, so the JMX surface does not expose the
     * plaintext password while {@link #getPassword()} is explicitly masked. A configuration whose database properties
     * are {@code null} - a state {@link PoolConfiguration#setDbProperties(Properties)} accepts - has nothing to mask,
     * so this returns {@code null}. {@inheritDoc}
     * <p>
     * The copy is a display value, and it looks complete: nothing in it says that the pool's own set carries a pair
     * this one withholds. That matters because this attribute is also writable and its write is the whole set -
     * {@link #setDbProperties(Properties)} replaces the live collection - so the ordinary read-modify-write of the
     * attribute hands the pool back a set with the credential taken out and takes that credential away from the live
     * configuration along with it. A configuration whose password travels only among the database properties - the
     * shape the credential has when a {@link PoolConfiguration#getConnectionProperties() connectionProperties} line
     * carries it - then establishes its next connections with nothing to authenticate them, and nothing on the read,
     * the write or in between warns. The credential's owning controls are the {@link #setPassword(String) Password}
     * and {@link #setUsername(String) Username} attributes: each mirrors its value into the database properties and
     * takes the entry away when cleared, and while one of them holds the password it, not the entry, is what new
     * connections are established with however this attribute is written.
     */
    @Override
    public Properties getDbProperties() {
        Properties live = getPoolProperties().getDbProperties();
        if (live == null) {
            return null;
        }
        return PoolUtilities.cloneWithoutPassword(live);
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

    /**
     * Returns the interceptor configuration with any credential its definitions carry masked out, so that a read of
     * this attribute does not hand back in cleartext the password {@link #getPassword()} exists to withhold - the
     * parameter list of a definition reaches a third-party interceptor as configured, so it is one of the raw strings
     * an operator may type a credential into. There is no write to mask: {@link #setJdbcInterceptors(String)} refuses
     * the change on a running pool at all. {@inheritDoc}
     */
    @Override
    public String getJdbcInterceptors() {
        return PoolProperties.maskInterceptorCredentials(getPoolProperties().getJdbcInterceptors());
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

    /**
     * Returns the pool's current name, as the {@code Name} attribute. This is the live configuration value and may
     * differ from the ObjectName this MBean was registered under after a rename; see {@link #setName(String)}.
     *
     * @return the pool's current name
     */
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

    /**
     * Returns the JDBC URL with any credential it carries masked out, so that a read of this attribute does not hand
     * back in cleartext the password {@link #getPassword()} exists to withhold. The write is not masked:
     * {@link #setUrl(String)} takes the URL as it is meant to reach the driver, which is the same read/write asymmetry
     * the password attribute has.
     * <p>
     * The masked value is a display value, not a safe value to write back: the ordinary read-modify-write replaces the
     * credential with the mask and leaves the pool unable to authenticate. Change the credential through
     * {@link #setPassword(String)} and {@link #setUsername(String)}, or read the raw value from {@link #getPool()}.
     * {@inheritDoc}
     */
    @Override
    public String getUrl() {
        return PoolProperties.maskCredentials(getPoolProperties().getUrl());
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

    /**
     * Returns the parsed interceptor configuration with any credential its
     * definitions carry masked out, so that a read of this attribute does not
     * hand back in cleartext the password {@link #getPassword()} exists to
     * withhold - the pairs of a definition reach a third-party interceptor as
     * configured, which is why {@link #getJdbcInterceptors()} masks them in the
     * string this attribute is the parsed form of. There is no write to mask:
     * {@link #setJdbcInterceptors(String)} refuses the change on a running pool
     * at all. The array is a copy made for this read, so the configuration the
     * interceptor chains are built from keeps the raw pairs and this attribute is
     * no way to reach those definitions either. {@inheritDoc}
     */
    @Override
    public InterceptorDefinition[] getJdbcInterceptorsAsArray() {
        return PoolProperties.maskInterceptorCredentials(getPoolProperties().getJdbcInterceptorsAsArray());
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

    /**
     * Replaces the whole set of database properties the pool connects with: the write is a new set rather than a
     * change applied over the set the pool holds, and the caller owns whatever the written collection holds, as
     * {@link PoolProperties#setDbProperties(Properties)} records. It re-mirrors nothing - the {@code user} and
     * {@code password} entries are whatever the set written here carries. The read of this same attribute,
     * {@link #getDbProperties()}, withholds the password, so writing back what that read returned - the ordinary way
     * of adding or removing one entry - drops the credential from the live configuration: a pool whose password
     * travels only among the database properties establishes its next connections with nothing to authenticate them,
     * while a pool whose credential the {@link #setPassword(String) Password} attribute holds keeps authenticating
     * with the attribute, the superseded entry going with the replaced set. The Password attribute and its
     * {@link #setUsername(String) Username} pair are therefore the credential's owning controls: they mirror their
     * value into the database properties and take the entry away when cleared, whereas this attribute can only be
     * written with a set the writer itself already holds the credential for - what the read hands back is never that
     * set.
     */
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
        poolConfigurationUpdated();
    }

    /**
     * Renames the running pool. The write is allowed at any time and changes only what the pool calls itself: the
     * {@code Name} and {@code PoolName} attributes of this MBean, the name
     * {@link org.apache.tomcat.jdbc.pool.ConnectionPool#getName()} reports for the pool, and - while this MBean is not
     * registered, so it has no {@link ObjectName} to name itself by - the string its notifications carry as their
     * source (see {@link #getNotificationSource()}).
     * <p>
     * Nothing that was registered under a name moves with the rename. Every registration made for a pool is keyed by
     * the name the pool started with, which {@link org.apache.tomcat.jdbc.pool.ConnectionPool#getRegistrationName()}
     * returns, and not by this property: the statistics the {@code SlowQueryReport} interceptor keeps for this pool
     * stay reachable under the old name and are released under it when the pool closes, as does the MBean its
     * {@code SlowQueryReportJmx} subclass publishes for the pool. The ObjectName this MBean is registered under is, in
     * the same way, the one its registration took, and the per-connection MBeans stay published below that name.
     *
     * @param name the name the pool is to call itself by from now on
     */
    @Override
    public void setName(String name) {
        getPoolProperties().setName(name);
    }

    /**
     * Returns the pool's current name, as the {@code PoolName} attribute. The MBean's registration name is fixed when
     * it is registered; a rename changes this value without moving the registration.
     *
     * @return the pool's current name
     */
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


    /**
     * @throws UnsupportedOperationException always - whether borrow waits are queued fairly is decided when the pool
     *             builds its wait queue; set the property before starting the pool
     */
    @Override
    public void setFairQueue(boolean fairQueue) {
        throw new UnsupportedOperationException("fairQueue must be set before the pool is started");
    }


    /**
     * @throws UnsupportedOperationException always - the initial size is what the pool pre-loads with when it is
     *             created; set the property before starting the pool
     */
    @Override
    public void setInitialSize(int initialSize) {
        throw new UnsupportedOperationException("initialSize must be set before the pool is started");
    }


    @Override
    public void setInitSQL(String initSQL) {
        getPoolProperties().setInitSQL(initSQL);

    }


    /**
     * @throws UnsupportedOperationException always - the interceptor chain is built when the pool starts and for
     *             every connection it establishes; set the property before starting the pool
     */
    @Override
    public void setJdbcInterceptors(String jdbcInterceptors) {
        throw new UnsupportedOperationException("jdbcInterceptors must be set before the pool is started");
    }


    /**
     * @throws UnsupportedOperationException always - whether the pool registers this MBean at all is decided when the
     *             pool is created, and a client reaching this setter has reached an MBean whose registration is that
     *             decision; set the property before starting the pool
     */
    @Override
    public void setJmxEnabled(boolean jmxEnabled) {
        throw new UnsupportedOperationException("jmxEnabled must be set before the pool is started");
    }


    @Override
    public void setLogAbandoned(boolean logAbandoned) {
        getPoolProperties().setLogAbandoned(logAbandoned);
    }


    @Override
    public void setMaxActive(int maxActive) {
        getPoolProperties().setMaxActive(maxActive);
        poolConfigurationUpdated();
    }


    @Override
    public void setMaxIdle(int maxIdle) {
        getPoolProperties().setMaxIdle(maxIdle);
        poolConfigurationUpdated();
    }


    @Override
    public void setMaxWait(int maxWait) {
        getPoolProperties().setMaxWait(maxWait);
    }


    @Override
    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        getPoolProperties().setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        poolCleanerAttributeUpdated();
    }


    @Override
    public void setMinIdle(int minIdle) {
        getPoolProperties().setMinIdle(minIdle);
        poolConfigurationUpdated();
    }


    @Override
    public void setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        getPoolProperties().setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    }


    /**
     * Sets the password on the running pool's configuration. The write carries the same meaning as
     * {@link org.apache.tomcat.jdbc.pool.DataSourceProxy#setPassword(String)}: the credential also lives among the
     * database properties the driver is given, and a {@code null} password takes it away from there as well, so that
     * clearing it here really does stop new connections authenticating with it.
     * <p>
     * No session is signed on again by the write itself, so what a rotation does to the connections the pool had
     * already established depends on who takes such a connection up again. A borrowed one takes the new password up:
     * the borrow compares the identity the session carries with the identity configured now and, finding them
     * different, re-establishes that same connection under the password written here, which is the identity it keeps
     * from then on. A connection the pool takes up on its own account - an idle one whose
     * {@link PoolConfiguration#getMaxAge() maxAge} has expired - signs on again with the credential it cached for
     * itself at its first sign-on rather than with this one, so wherever the two differ the database may refuse that
     * reconnect; the pool retires such a connection and the next borrow is served by one established under the
     * password set here. A rotation therefore converges without the operator doing anything, at the price of a
     * refused sign-on or two on the renewals the pool runs by itself, and it does not leave sessions in service under
     * a superseded credential.
     * <p>
     * To stop presenting the superseded credential now rather than connection by connection, expire the sessions that
     * still carry it: {@link #purge()} closes the idle connections at once and the busy ones as they come back, while
     * {@link #purgeOnReturn()} waits for each connection to come back, an idle one no sooner than it has been borrowed
     * and returned. Whatever the pool establishes after either authenticates with the password set here.
     *
     * @param password the password, or {@code null} to take the credential away
     */
    @Override
    public void setPassword(String password) {
        getPoolProperties().setPassword(password);
    }


    @Override
    public void setRemoveAbandoned(boolean removeAbandoned) {
        getPoolProperties().setRemoveAbandoned(removeAbandoned);
        poolCleanerAttributeUpdated();
    }


    @Override
    public void setRemoveAbandonedTimeout(int removeAbandonedTimeout) {
        getPoolProperties().setRemoveAbandonedTimeout(removeAbandonedTimeout);
        poolCleanerAttributeUpdated();
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
        getPoolProperties().setTestWhileIdle(testWhileIdle);
        poolCleanerAttributeUpdated();
    }


    @Override
    public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        getPoolProperties().setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        poolConfigurationUpdated();
    }

    /**
     * Starts/stops pool cleaner thread as necessary after its configuration properties were updated. This method must
     * be called <b>after</b> configuration properties affecting the pool cleaner have been updated.
     * <p>
     * What is wanted and what is registered are both read, and the starting or
     * stopping is done, under the pool's monitor, in
     * {@link org.apache.tomcat.jdbc.pool.ConnectionPool#reconcilePoolCleaner(PoolConfiguration)}.
     * Neither half happens here: this MBean holds no monitor of its own, and an
     * operator writing through it is writing the same configuration an operator
     * writing through the data source writes, so a decision taken here from what
     * this writer saw can land after the other surface's write and either stop the
     * task that write armed or leave running a task that write made unnecessary.
     */
    private void poolCleanerAttributeUpdated() {
        pool.reconcilePoolCleaner(getPoolProperties());
    }

    /**
     * Validates the configuration a setter of this MBean just wrote and looks the
     * pool's cleaner over as a result of it, in that order.
     * <p>
     * The two halves belong together because the validation is not only about the
     * attribute the caller wrote: among the corrections it makes is bringing the
     * eviction interval down to the max age whenever the interval is the longer of
     * the two, and a pool cleaner's period is fixed when the task is built. A setter
     * that validated the configuration without then looking the cleaner over could
     * therefore move the interval the configuration asks for and leave the running
     * task on the interval it just replaced, a disagreement no getter shows until
     * some sweeper attribute is written again. That is why the pairing is one step
     * rather than something each setter remembers: it is what the data source does
     * for the same attributes, and what the pool's creation does when it publishes a
     * pool.
     * <p>
     * Which half is taken under the pool's monitor, and why neither half is decided
     * from a reading this writer took, is recorded at poolCleanerAttributeUpdated.
     */
    private void poolConfigurationUpdated() {
        pool.checkPoolConfiguration(getPoolProperties());
        poolCleanerAttributeUpdated();
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


    /**
     * Sets the user name on the running pool's configuration, with the same meaning as
     * {@link org.apache.tomcat.jdbc.pool.DataSourceProxy#setUsername(String)}: a {@code null} user name takes the
     * credential away from the database properties as well as from the configuration.
     * <p>
     * What {@link #setPassword(String)} records about connections the pool had already established holds here in
     * full, because the identity a session serves under is this user name together with that password: the borrow
     * compares the pair the session carries with the pair configured now and re-establishes the connection under the
     * new one, while a connection the pool renews on its own account signs on again with the pair it cached at its
     * first sign-on and is retired when the database refuses it. {@link #purge()} and {@link #purgeOnReturn()} expire
     * the sessions that still carry the superseded user name, on demand.
     *
     * @param username the user name, or {@code null} to take the credential away
     */
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
        getPoolProperties().setSuspectTimeout(seconds);
        poolCleanerAttributeUpdated();
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
     *
     * @deprecated The disposable connection facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
    public void setUseDisposableConnectionFacade(boolean useDisposableConnectionFacade) {
        getPoolProperties().setUseDisposableConnectionFacade(useDisposableConnectionFacade);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The disposable connection facade is always used, so this property no longer has any effect.
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

    /**
     * @throws UnsupportedOperationException always - the flag governs the preload of initial connections the pool
     *             runs when it is created; set the property before starting the pool
     */
    @Override
    public void setIgnoreExceptionOnPreLoad(boolean ignoreExceptionOnPreLoad) {
        throw new UnsupportedOperationException("ignoreExceptionOnPreLoad must be set before the pool is started");
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The statement facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
    public boolean getUseStatementFacade() {
        return getPoolProperties().getUseStatementFacade();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated The statement facade is always used, so this property no longer has any effect.
     */
    @Override
    @Deprecated
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

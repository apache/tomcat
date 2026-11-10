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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceAlreadyExistsException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;
import org.apache.tomcat.jdbc.pool.PoolUtilities;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * JMX-enabled slow query report interceptor. Publishes slow and failed query statistics to JMX and provides
 * notifications when slow or failed queries are detected.
 * <p>
 * With {@code notifyPool=false} this interceptor registers its own MBean and broadcaster. In the default
 * {@code notifyPool=true} mode it registers nothing and raises notifications on the pool's own MBean instead. The MBean
 * belongs to a pool and is published by the first borrow under the name the pool started with; the publication is
 * attempted once and given up by the pool's close. Names must be unique among live pools.
 * <p>
 * The published resource is a {@link Registration} holding the pool name, the statistics store and the pool's JMX
 * state, but not the pool itself, so an abandoned pool is not pinned by the MBean server. Listener subscriptions
 * belong to the registration they were added through and must be re-attached after a pool closes.
 */
public class SlowQueryReportJmx extends SlowQueryReport implements NotificationEmitter, SlowQueryReportJmxMBean {
    /**
     * Notification type for slow query events. The pool's own MBean reports the same event under the same type when
     * this interceptor is configured with {@code notifyPool=true}, so this is that very type and not a copy of its
     * text: a client filters notifications on the type, and the two channels have to answer to one string.
     */
    public static final String SLOW_QUERY_NOTIFICATION = ConnectionPoolMBean.SLOW_QUERY_NOTIFICATION;
    /**
     * Notification type for failed query events, shared with the pool's own MBean for the same reason as
     * {@link #SLOW_QUERY_NOTIFICATION}.
     */
    public static final String FAILED_QUERY_NOTIFICATION = ConnectionPoolMBean.FAILED_QUERY_NOTIFICATION;

    /**
     * The notification information every broadcaster of this class is built with, so that both the published MBean and
     * the interceptor's own emitter surface advertise the two types above. JMX clients discover what an MBean can send
     * through {@code MBeanInfo.getNotifications()} - consoles, monitoring and bridge agents, tooling that builds a
     * notification filter from the advertised types - and a broadcaster constructed without this information leaves the
     * MBean advertising none of the types it actually delivers.
     */
    private static final MBeanNotificationInfo[] NOTIFICATION_INFO = { new MBeanNotificationInfo(
            new String[] { SLOW_QUERY_NOTIFICATION, FAILED_QUERY_NOTIFICATION },
            Notification.class.getName(),
            "A slow or failed query was detected by the slow query report.") };

    /**
     * Property key for overriding the JMX object name.
     */
    public static final String objectNameAttribute = "objectName";

    /**
     * The name of the property that configures {@link #setNotifyPool(boolean)}. Spelled once, here, because
     * {@link #setProperties(java.util.Map)} applies it and {@link #isKnownProperty(String)} reports the names it does
     * not read.
     */
    protected static final String NOTIFY_POOL = "notifyPool";

    /**
     * Composite type used for JMX query statistics data.
     */
    protected static volatile CompositeType SLOW_QUERY_TYPE;

    /**
     * Whether the one attempt to build {@link #SLOW_QUERY_TYPE} failed. The type is static, so a failure is permanent for
     * the class loader; recording it keeps every later borrow from retrying and logging the same warning.
     */
    private static volatile boolean compositeTypeInitializationFailed;

    private static final Log log = LogFactory.getLog(SlowQueryReportJmx.class);

    /**
     * Creates a new SlowQueryReportJmx instance.
     */
    public SlowQueryReportJmx() {
    }

    /**
     * Everything this class publishes for one pool: the notification broadcaster its clients attach their listeners to
     * and, in the {@code notifyPool=false} mode, the ObjectName the pool's MBean occupies.
     * <p>
     * The state hangs from the pool's statistics store (see {@link PoolStatistics#getJmxState()}), which is 1:1 with
     * the pool, and nothing a pool published can be given up by another pool's close. It is built by the first
     * borrower that needs it and given up by the release of the store it hangs from, which is what the close of the
     * pool is.
     * </p>
     * <p>
     * The publication is attempted once per pool: JMX is a best-effort management channel, so a refusal is logged and
     * not retried by later borrows. Publication and release are serialised against each other by this object's monitor,
     * and the MBeanServer is called outside it: a borrow either publishes, finds that the attempt has already been
     * made, or finds the state already superseded by the pool's close - in which case the borrow gives back the
     * publication it just made. A published MBean can therefore never outlive the state that published it, and the
     * ObjectName a release gives up is the one this state recorded when it published, whatever class or
     * {@code objectName} property the interceptor instance running the close happens to carry.
     * </p>
     */
    protected static class PoolJmx {
        /**
         * the name of the pool this state belongs to - the name recorded at its start, which the ObjectName of a
         * publication is formed from
         */
        private final String poolName;
        /**
         * the broadcaster every interceptor instance of this pool sends its notifications through; built on the first
         * need, because having someone to listen is not a reason to publish anything
         */
        private volatile NotificationBroadcasterSupport broadcaster;
        /**
         * the ObjectName this pool's MBean occupies, or {@code null} while nothing is published for the pool; guarded
         * by this
         */
        private ObjectName publishedUnder;
        /**
         * whether a publication has been attempted for this pool, so that a refusal is logged once and later borrows
         * leave the management server alone; guarded by this
         */
        private boolean attempted;
        /**
         * whether the close of the pool has given this state up; guarded by this
         */
        private boolean released;

        /**
         * Creates the JMX state of one pool.
         *
         * @param poolName the name the pool's registrations are keyed under
         */
        protected PoolJmx(String poolName) {
            this.poolName = poolName;
        }

        /**
         * Returns the name of the pool this state belongs to.
         *
         * @return the name recorded at the pool's start
         */
        protected String getPoolName() {
            return poolName;
        }

        /**
         * Returns the ObjectName this pool's MBean is published under.
         *
         * @return the published ObjectName, or {@code null} while nothing is published for this pool
         */
        protected synchronized ObjectName publishedUnder() {
            return publishedUnder;
        }

        /**
         * Returns whether the close of this pool has given this state up.
         *
         * @return {@code true} if the pool has closed
         */
        protected synchronized boolean isReleased() {
            return released;
        }

        /**
         * Returns the broadcaster all interceptor instances of this pool send their notifications through, building it
         * on the first need.
         *
         * @return this pool's broadcaster; never {@code null}
         */
        protected NotificationBroadcasterSupport broadcaster() {
            NotificationBroadcasterSupport shared = broadcaster;
            if (shared == null) {
                synchronized (this) {
                    shared = broadcaster;
                    if (shared == null) {
                        shared = new NotificationBroadcasterSupport(NOTIFICATION_INFO);
                        broadcaster = shared;
                    }
                }
            }
            return shared;
        }

        /**
         * Decides whether the calling borrow is to make this pool's one publication attempt.
         *
         * @return {@code true} if the caller is to attempt the publication - none has been made or attempted yet, and
         *             the pool has not closed - and {@code false} when there is nothing left to do
         */
        protected synchronized boolean beginPublication() {
            if (released || attempted) {
                return false;
            }
            attempted = true;
            return true;
        }

        /**
         * Records a publication the MBeanServer accepted.
         *
         * @param oname the ObjectName the MBean was published under
         *
         * @return {@code true} if the publication stands and is now this pool's MBean, and {@code false} when the pool's
         *             close landed while the MBeanServer was taking it in - nothing else in this JVM will ever learn of
         *             that publication, so the borrow that made it has to give it back
         */
        protected synchronized boolean published(ObjectName oname) {
            if (released) {
                return false;
            }
            publishedUnder = oname;
            return true;
        }

        /**
         * Gives up everything this state holds: the ObjectName the pool's MBean occupied, which the MBeanServer is
         * asked to release, and with it the broadcaster the pool's clients' listeners are attached to. The close of the
         * pool's statistics store calls this, and it is idempotent: a later call, from any thread, does nothing.
         * <p>
         * The broadcaster itself is left in place rather than dropped: whoever still holds this pool's registration can
         * subscribe to it, and a subscription made after the pool came down is served by a broadcaster nothing will
         * ever send through, which is what a released registration has to answer a stale handle with. What must not
         * survive the release is the published MBean, and it does not: the ObjectName is given up here, against the
         * name this state recorded when it published.
         * </p>
         */
        protected void released() {
            ObjectName oname;
            synchronized (this) {
                if (released) {
                    return;
                }
                released = true;
                oname = publishedUnder;
                publishedUnder = null;
            }
            if (oname != null) {
                JmxUtil.unregisterJmx(oname);
            }
        }
    }

    /**
     * Notification broadcaster used while this instance serves no pool, and the one a notification of a pool that has
     * already come down is sent through; while it serves a live pool, {@link #getNotifier()} returns the broadcaster
     * shared by all interceptor instances of that pool. Like the shared broadcasters, it is built with
     * {@link #NOTIFICATION_INFO}, so every handle's emitter surface advertises the types it sends.
     */
    protected volatile NotificationBroadcasterSupport notifier = new NotificationBroadcasterSupport(NOTIFICATION_INFO);

    /**
     * Adds a notification listener.
     *
     * @param listener the listener to add
     * @param filter   the notification filter
     * @param handback the handback object
     *
     * @throws IllegalArgumentException if the listener is null
     */
    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback)
            throws IllegalArgumentException {
        getNotifier().addNotificationListener(listener, filter, handback);
    }


    /**
     * Returns information about the notifications this MBean can send.
     *
     * @return an array of MBeanNotificationInfo objects
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return getNotifier().getNotificationInfo();
    }

    /**
     * Removes a notification listener.
     *
     * @param listener the listener to remove
     *
     * @throws ListenerNotFoundException if the listener was not previously added
     */
    @Override
    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        getNotifier().removeNotificationListener(listener);

    }

    /**
     * Removes a notification listener with the given filter and handback.
     *
     * @param listener the listener to remove
     * @param filter   the notification filter
     * @param handback the handback object
     *
     * @throws ListenerNotFoundException if the listener was not previously added
     */
    @Override
    public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback)
            throws ListenerNotFoundException {
        getNotifier().removeNotificationListener(listener, filter, handback);

    }


    /**
     * The name of the connection pool.
     */
    protected String poolName = null;

    /**
     * Atomic sequence counter for notifications.
     */
    protected static final AtomicLong notifySequence = new AtomicLong(0);

    /**
     * Whether to send notifications to the pool.
     */
    protected boolean notifyPool = true;

    /**
     * The connection pool reference. During a borrow this is a strong reference used to route the instance's
     * notifications and JMX reads; it is cleared when the chain is released.
     */
    protected ConnectionPool pool = null;

    /**
     * A weak reference to the pool, kept across the release of the chain so an instance whose session outlives the
     * connection can still resolve the pool's JMX state while the pool is alive. It must not keep a closed or abandoned
     * pool, its sessions and its driver class loader reachable from a retained statement handle.
     */
    private volatile WeakReference<ConnectionPool> poolRef = new WeakReference<>(null);

    /**
     * Returns the composite type used for JMX query statistics.
     *
     * @return the composite type, or {@code null} if initialization failed
     */
    protected static CompositeType getCompositeType() {
        if (SLOW_QUERY_TYPE == null && !compositeTypeInitializationFailed) {
            synchronized (SlowQueryReportJmx.class) {
                if (SLOW_QUERY_TYPE == null && !compositeTypeInitializationFailed) {
                    try {
                        SLOW_QUERY_TYPE = new CompositeType(SlowQueryReportJmx.class.getName(),
                                "Composite data type for query statistics", QueryStats.getFieldNames(),
                                QueryStats.getFieldDescriptions(), QueryStats.getFieldTypes());
                    } catch (OpenDataException x) {
                        compositeTypeInitializationFailed = true;
                        log.warn(MessageFactory.get("slowQueryReportJmx.unableToInitCompositeType"), PoolUtilities.maskCredentials(x));
                    }
                }
            }
        }
        return SLOW_QUERY_TYPE;
    }

    /**
     * Returns the JMX state published for the given pool.
     *
     * @param pool the pool whose published state is wanted
     *
     * @return what has been published for that pool, or {@code null} while nothing has been - because the pool has no
     *             statistics store, because no borrow of it has needed the state yet, or because its close has given
     *             the state up along with the store
     */
    protected static PoolJmx jmxStateFor(ConnectionPool pool) {
        PoolStatistics own = statisticsFor(pool);
        return own == null ? null : own.getJmxState();
    }

    /**
     * Returns the JMX state of the given statistics store, publishing one for the store if it has none yet.
     *
     * @param statistics the store whose pool's state is wanted
     *
     * @return the state, or {@code null} when the store has already been released with its pool's close - nothing is
     *             published for a pool that has come down, and a state built behind the release would be given up by
     *             nothing
     */
    private static PoolJmx stateFor(PoolStatistics statistics) {
        PoolJmx state = statistics.getJmxState();
        if (state == null) {
            synchronized (statistics) {
                if (!statistics.isReleased() && statistics.getJmxState() == null) {
                    statistics.setJmxState(new PoolJmx(statistics.getName()));
                }
                state = statistics.getJmxState();
            }
        }
        return state;
    }

    /**
     * Resets this interceptor for a new connection, registering JMX if applicable.
     *
     * @param parent the connection pool
     * @param con    the pooled connection
     */
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        super.reset(parent, con);
        if (parent != null) {
            // The name the pool's registrations are keyed under, recorded at
            // its start - renaming the pool does not move them, and does not
            // make a borrow register a second MBean under the new name
            poolName = parent.getRegistrationName();
            pool = parent;
            poolRef = new WeakReference<>(parent);
            registerJmx();
        } else {
            // The chain is going out of service. Keep only the weak reference:
            // a statement or result-set handle retained by the application
            // keeps this instance reachable, so a strong pool reference here
            // would keep a closed pool, its sessions and its driver class
            // loader alive with it. A session that outlives its pool can still
            // resolve the pool through the weak reference while the pool is
            // alive.
            pool = null;
        }
    }

    /**
     * Called when the connection pool is started.
     *
     * @param pool the connection pool being started
     */
    @Override
    public void poolStarted(ConnectionPool pool) {
        this.pool = pool;
        this.poolRef = new WeakReference<>(pool);
        super.poolStarted(pool);
        this.poolName = pool.getRegistrationName();
    }

    /**
     * Returns the pool this instance serves. While a connection is borrowed the strong field answers; after the chain
     * is released the weak reference still resolves the pool while it is alive, so a session that outlives its
     * connection can report on it, but a closed or abandoned pool is not kept alive by a retained statement handle.
     *
     * @return the pool, or {@code null} when there is none or it has been collected
     */
    private ConnectionPool currentPool() {
        ConnectionPool strong = this.pool;
        return strong != null ? strong : poolRef.get();
    }

    /**
     * Reports a failed query and sends a JMX notification if logging is enabled.
     *
     * @param query the SQL query
     * @param args  the query arguments
     * @param name  the name of the method used to execute
     * @param start the wall clock time the query execution started
     * @param delta the time the execution took before it failed, measured with the monotonic clock
     * @param t     the throwable that caused the failure
     *
     * @return the formatted query string
     */
    @Override
    protected String reportFailedQuery(String query, Object[] args, String name, long start, long delta,
            Throwable t) {
        query = super.reportFailedQuery(query, args, name, start, delta, t);
        if (isLogFailed()) {
            notifyJmx(query, FAILED_QUERY_NOTIFICATION);
        }
        return query;
    }

    /**
     * Sends a JMX notification for the given query and notification type.
     *
     * @param query the SQL query string
     * @param type  the notification type (slow or failed query)
     */
    protected void notifyJmx(String query, String type) {
        try {
            long sequence = notifySequence.incrementAndGet();

            if (isNotifyPool()) {
                // Read the pool and its JMX object each only once: close()
                // gives the JMX object up concurrently, and a second read can
                // observe the null the first one did not
                ConnectionPool p = currentPool();
                org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = p == null ? null : p.getJmxPool();
                if (jmx != null) {
                    jmx.notify(type, sqlForLog(query));
                }
            } else {
                NotificationBroadcasterSupport target = broadcasterToSendTo();
                if (target != null) {
                    Notification notification = new Notification(type, getNotificationSource(), sequence,
                            System.currentTimeMillis(), sqlForLog(query));

                    target.sendNotification(notification);
                }
            }
        } catch (RuntimeOperationsException e) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to send failed query notification.", PoolUtilities.maskCredentials(e));
            }
        }
    }

    /**
     * Returns the broadcaster that the notifications of this interceptor's pool are sent through. Unlike
     * {@link #getNotifier()} this one reads rather than provides: a send is not a reason for a pool's JMX state to
     * exist. A live pool's notifications go to the broadcaster every instance of it shares; a pool that has come down
     * has nothing left to receive a notification - what such a send can still serve is whoever reached this instance,
     * so it falls back to the instance's own broadcaster.
     *
     * @return the broadcaster to send a notification of this interceptor's pool through
     */
    private NotificationBroadcasterSupport broadcasterToSendTo() {
        PoolJmx state = publishedStateOfThisPool();
        if (state != null && !state.isReleased()) {
            return state.broadcaster();
        }
        return notifier;
    }

    /**
     * Returns the JMX state published for the pool this instance serves, or {@code null} while it has none.
     * <p>
     * The store the borrow handed this instance is the first place to look, and it is all a healthy instance needs.
     * The pool is the second: a chain whose release took the store away while its pool keeps running - the session that
     * outlived the connection that served it - still reports on that pool, and its notifications belong with the
     * publication every other instance of the pool sends to. Asking the pool rather than the name is what keeps this
     * honest: the answer is this instance's own pool's state, never a state that merely happens to be published under
     * the name this pool was configured with.
     * </p>
     *
     * @return the state published for this instance's pool, or {@code null}
     */
    private PoolJmx publishedStateOfThisPool() {
        PoolStatistics statistics = this.poolStatistics;
        PoolJmx state = statistics == null ? null : statistics.getJmxState();
        if (state != null) {
            return state;
        }
        PoolStatistics own = statisticsFor(currentPool());
        return own == null ? null : own.getJmxState();
    }

    /**
     * Returns the notification broadcaster shared by all interceptor instances of the current pool, so that
     * notifications raised on any connection reach the listeners registered with the pool's MBean.
     *
     * @return the shared broadcaster for this interceptor's pool
     */
    protected NotificationBroadcasterSupport getNotifier() {
        PoolJmx state = publishedStateOfThisPool();
        if (state == null) {
            PoolStatistics statistics = this.poolStatistics;
            state = statistics == null ? null : stateFor(statistics);
        }
        return state == null ? notifier : state.broadcaster();
    }

    /**
     * Returns the source this MBean's notifications are sent with.
     * <p>
     * {@link Notification} marshals its source, so a listener outside this JVM - one reached through a JMX connector,
     * or one that hands the notification on to another process - only receives the notification at all if the source is
     * serializable. This MBean is not, and neither is the pool it reports on, so the source is the {@link ObjectName}
     * the MBean is registered under, which names the pool and is serializable. Interceptor instances are created per
     * pooled connection and none of them is the published resource - that is the pool's {@link Registration} - so an
     * instance cannot name itself: the source is the ObjectName the pool's JMX state records as published, which every
     * instance of the pool sends alike. That includes the instances of a second definition on one pool: the
     * two-thresholds configuration publishes one MBean, and an instance that published nothing must not stamp its
     * notifications with the name its own properties would compute - that name is registered under nothing, and a
     * client filtering or grouping by source reads the published one. An instance whose name cannot be formed - a
     * malformed {@code objectName} property or a pool name that cannot be quoted into an ObjectName - was never
     * registered at all, so no listener reached through a connector can exist for it; the source is then a string of
     * the default name's shape, which still names the pool for the local listeners the broadcaster serves. A pool that
     * carries no name is the same case taken to its end: it holds no registration to name itself by, and the shape its
     * source takes is the default one with no name in it - whatever survives delivery, it cannot name a pool that never
     * had a name to register.
     *
     * @return the ObjectName this interceptor's pool is published under, or, when nothing is published for it or the
     *             published name cannot be told, a name formed from this instance - as an ObjectName where that
     *             succeeds, and as a string of the default name's shape where it does not
     */
    protected Object getNotificationSource() {
        PoolJmx state = publishedStateOfThisPool();
        if (state != null) {
            ObjectName published = state.publishedUnder();
            if (published != null) {
                // The name as the publication itself recorded it: exactly what
                // the MBeanServer occupies, and what the release gives up
                return published;
            }
        }
        String name = poolName;
        try {
            return getObjectName(getClass(), name);
        } catch (MalformedObjectNameException | RuntimeOperationsException x) {
            // The name construction getObjectName uses when no override is
            // configured - with a malformed name this MBean was never
            // registered, so this fallback is only read by local listeners,
            // for which it must still name the pool
            return ConnectionPool.POOL_JMX_TYPE_PREFIX + getClass().getName() + ",name=" + name;
        }
    }

    /**
     * Reports a slow query and sends a JMX notification if logging is enabled.
     *
     * @param query the SQL query
     * @param args  the query arguments
     * @param name  the connection pool name
     * @param start the start time in milliseconds
     * @param delta the elapsed time in milliseconds
     *
     * @return the formatted query string
     */
    @Override
    protected String reportSlowQuery(String query, Object[] args, String name, long start, long delta) {
        query = super.reportSlowQuery(query, args, name, start, delta);
        if (isLogSlow()) {
            notifyJmx(query, SLOW_QUERY_NOTIFICATION);
        }
        return query;
    }

    /**
     * JMX operation - return the names of all the pools
     *
     * @return - all the names of pools that we have stored data for, which is a view of the JVM-wide statistics
     *             registry rather than of this pool alone
     */
    @Override
    public String[] getPoolNames() {
        return allPoolNames();
    }

    /**
     * The names currently holding recorded statistics - the read behind {@link #getPoolNames()} and the registration's
     * own {@code PoolNames} attribute. These are the names of the pools that publish a statistics store, one store per
     * pool.
     *
     * @return the names of the pools that currently hold a statistics map
     */
    private static String[] allPoolNames() {
        return perPoolStats.keySet().toArray(new String[0]);
    }

    /**
     * JMX operation - return the name of the pool.
     *
     * @return the registration name recorded when this instance was bound to its pool, or {@code null} before binding.
     *             The value is retained when the chain is released with {@code reset(null, null)}, so it can remain
     *             non-null after that release
     */
    @Override
    public String getPoolName() {
        return poolName;
    }


    /**
     * Returns whether notifications are sent to the pool.
     *
     * @return true if notifications are sent to the pool
     */
    public boolean isNotifyPool() {
        return notifyPool;
    }

    /**
     * Sets whether notifications are sent to the pool. Configuration entry point only, applied to every interceptor
     * instance of a pool by {@code setProperties} while its connection chain is built: it is not part of the JMX
     * management interface, because writing it through the registered MBean would reach only the one instance that owns
     * the registration and leave the pool's other connections routing as before. See {@link SlowQueryReportJmxMBean}.
     *
     * @param notifyPool true if notifications should be sent to the pool
     */
    public void setNotifyPool(boolean notifyPool) {
        this.notifyPool = notifyPool;
    }

    /**
     * JMX operation - remove all stats for this connection pool. The statistics belong to this interceptor's pool, so
     * what this clears is what that pool has recorded - and nothing another pool collected, however it was named. Only
     * the recorded content goes: the statistics store stays registered with the pool, which keeps accumulating into it.
     */
    @Override
    public void resetStats() {
        clearStats(statisticsToRead());
    }

    /**
     * Clears the statistics of one pool - the operation behind {@link #resetStats()} and the registration's own
     * {@code resetStats()}.
     *
     * @param statistics the store whose statistics are to be cleared, possibly {@code null}
     */
    private static void clearStats(PoolStatistics statistics) {
        ConcurrentHashMap<String, QueryStats> queries = statistics == null ? null : statistics.getQueryStats();
        if (queries != null) {
            // Clear the map itself rather than emptying it through an
            // iterator: the map is shared with every interceptor instance of
            // the pool, which all hold this same instance
            queries.clear();
        }
    }

    /**
     * JMX operation - returns all the queries we have collected.
     *
     * @return - the slow query report as composite data; an empty array when this interceptor's pool publishes no
     *             statistics - because it never registered a store, or because its close has released it
     */
    @Override
    public CompositeData[] getSlowQueriesCD() throws OpenDataException {
        return slowQueriesCDFor(statisticsToRead());
    }

    /**
     * The store a read of this instance's pool answers from: the store of the pool it serves, or - for an instance
     * that never served a borrow, which is what an interceptor handed to a management client before the pool has been
     * used is - the store the pool's name currently publishes.
     *
     * @return the statistics store to read, or {@code null} when there is none
     */
    private PoolStatistics statisticsToRead() {
        ConnectionPool current = currentPool();
        PoolStatistics own = this.poolStatistics;
        if (own != null && own.getPool() == current) {
            return own;
        }
        String name = poolName;
        if (name == null) {
            return null;
        }
        PoolStatistics stats = perPoolStats.get(name);
        // A name can be reused by another pool after this one is abandoned;
        // a read of this instance's pool must not answer from the successor.
        return stats != null && stats.getPool() == current ? stats : null;
    }

    /**
     * The statistics of one pool as JMX composite data - the read behind {@link #getSlowQueriesCD()} and the
     * registration's own {@code SlowQueriesCD} attribute.
     *
     * @param statistics the store whose statistics are to be published, possibly {@code null}
     *
     * @return the recorded statistics, or an empty array when there are none to publish
     *
     * @throws OpenDataException if the composite data cannot be created
     */
    private static CompositeData[] slowQueriesCDFor(PoolStatistics statistics) throws OpenDataException {
        // Default to the empty array, never null: the attribute is advertised
        // as a CompositeData array and the store can be released while a
        // client still reaches this registration.
        CompositeDataSupport[] result = new CompositeDataSupport[0];
        ConcurrentHashMap<String, QueryStats> queries = statistics == null ? null : statistics.getQueryStats();
        if (queries != null) {
            // Materialise a point-in-time snapshot before sizing the array: the
            // map is shared and can change while the array is filled.
            Map<String, QueryStats> snapshot = new HashMap<>(queries);
            CompositeType type = getCompositeType();
            if (type == null) {
                // The type could not be built for this class loader; the
                // documented degradation is an empty array, not a null type
                // passed to CompositeDataSupport.
                return new CompositeDataSupport[0];
            }
            result = new CompositeDataSupport[snapshot.size()];
            int pos = 0;
            for (QueryStats qs : snapshot.values()) {
                result[pos++] = qs.getCompositeData(type);
            }
        }
        return result;
    }

    /**
     * Registers this pool's MBean with JMX. The publication is attempted once per pool by the first borrow that needs
     * it; a refusal is logged and not retried. A pool with no statistics store, or whose store has been released, has
     * nothing to publish.
     */
    protected void registerJmx() {
        // With the pool notified, the notifications go to the pool's own
        // MBean and there is nothing for this interceptor to register
        if (isNotifyPool()) {
            return;
        }
        if (getCompositeType() == null) {
            // The one-time failure was reported by getCompositeType(); the
            // type is static, so retrying or logging it again on every borrow
            // would only repeat the same permanent diagnostic.
            return;
        }
        PoolStatistics statistics = this.poolStatistics;
        if (statistics == null || statistics.isReleased()) {
            return;
        }
        PoolJmx state = stateFor(statistics);
        if (state == null || !state.beginPublication()) {
            return;
        }
        ObjectName oname;
        try {
            oname = getObjectName(getClass(), state.getPoolName());
        } catch (MalformedObjectNameException | RuntimeOperationsException x) {
            // A name that cannot be formed - a malformed objectName property, or
            // a pool name carrying characters the ObjectName grammar reserves.
            // There is no publication to make, and JMX is best effort, so this
            // is reported once and not attempted again.
            log.error(MessageFactory.get("slowQueryReportJmx.jmxRegistrationFailedNoData"), PoolUtilities.maskCredentials(x));
            return;
        }
        // The MBeanServer keeps the registration resource, never this
        // instance: an instance reaches the pool (its own and the inherited
        // pool fields), so publishing one made the JVM-lifetime registration
        // pin a pool that was abandoned without closing - the only path that
        // ever takes a registration down again is a close, which abandonment
        // never runs
        Registration registration = new Registration(state.getPoolName(), statistics, state);
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(registration, oname);
        } catch (InstanceAlreadyExistsException x) {
            // The name is held by a foreign or stale MBean. JMX is best effort
            // and names are unique under Catalina, so this is reported rather
            // than retried: by the time a retry ran, the name could describe a
            // different component.
            log.warn(MessageFactory.get("slowQueryReportJmx.jmxRegistrationInUse", oname), PoolUtilities.maskCredentials(x));
            return;
        } catch (Exception x) {
            // A non-compliant resource, a security restriction, or another
            // server-side refusal. Reported once, not retried.
            log.error(MessageFactory.get("slowQueryReportJmx.jmxRegistrationFailed", oname), PoolUtilities.maskCredentials(x));
            return;
        }
        if (!state.published(oname)) {
            // The pool's close landed while the server was taking this in,
            // and has already given up everything the state held; this
            // publication belongs to nobody now but the thread that made it
            JmxUtil.unregisterJmx(oname);
        }
    }

    /**
     * The management interface of a published {@link Registration} resource. The naming convention pairs a class
     * {@code X} with its interface {@code XMBean}; the resource therefore needs its own interface carrying
     * {@link SlowQueryReportJmxMBean}. Renaming this interface or {@link Registration} breaks that convention.
     */
    public interface RegistrationMBean extends SlowQueryReportJmxMBean {
    }

    /**
     * The resource published with the MBeanServer for one pool.
     * <p>
     * It holds the name, the statistics store of the pool it reports for, and that pool's JMX state, and nothing else:
     * every attribute and operation it serves reads the store and the pool's broadcaster exactly as an interceptor
     * instance of the pool would. Keeping the pool out of the resource is the point: the MBeanServer outlives a pool
     * that was abandoned without closing, and a resource that reached the pool would pin it, its sessions and its
     * classloader for the life of the JVM, since a registration is only ever taken down by a close the abandoned pool
     * never runs. The published surface is unchanged by this indirection: {@link SlowQueryReportJmxMBean} plus the
     * notification methods of {@link NotificationEmitter}, with listeners landing on the pool's shared broadcaster.
     * </p>
     * <p>
     * The ObjectName is deliberately not among what it holds: the name a publication occupies is recorded by the pool's
     * {@link PoolJmx}, which is also what gives it up, so the resource cannot be the one to give up a name other than
     * the one it was published under - and does not need to know any.
     * </p>
     * <p>
     * The class is public because the Standard-MBean introspection publishes the resource through its interfaces;
     * instances are created only by {@link #registerJmx()}.
     * </p>
     */
    public static class Registration implements NotificationEmitter, RegistrationMBean {
        /**
         * The pool name this registration reports for.
         */
        private final String registrationPoolName;
        /**
         * the statistics store of the pool this registration reports for; it publishes nothing once the pool has
         * closed, which is what the attributes answer with from then on
         */
        private final PoolStatistics statistics;
        /**
         * the JMX state of the pool this registration reports for, whose broadcaster its clients' listeners attach to
         */
        private final PoolJmx state;

        /**
         * Creates the registration of one pool.
         *
         * @param poolName   the pool name to report for
         * @param statistics the statistics store of the pool the registration reports for
         * @param state      the JMX state of that pool
         */
        public Registration(String poolName, PoolStatistics statistics, PoolJmx state) {
            this.registrationPoolName = poolName;
            this.statistics = statistics;
            this.state = state;
        }

        @Override
        public String getPoolName() {
            return registrationPoolName;
        }

        @Override
        public String[] getPoolNames() {
            return allPoolNames();
        }

        @Override
        public void resetStats() {
            clearStats(statistics);
        }

        @Override
        public CompositeData[] getSlowQueriesCD() throws OpenDataException {
            return slowQueriesCDFor(statistics);
        }

        @Override
        public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback)
                throws IllegalArgumentException {
            state.broadcaster().addNotificationListener(listener, filter, handback);
        }

        @Override
        public MBeanNotificationInfo[] getNotificationInfo() {
            return state.broadcaster().getNotificationInfo();
        }

        @Override
        public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
            state.broadcaster().removeNotificationListener(listener);
        }

        @Override
        public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback)
                throws ListenerNotFoundException {
            state.broadcaster().removeNotificationListener(listener, filter, handback);
        }
    }

    /**
     * Sets the interceptor properties, including the notifyPool flag.
     * <p>
     * The flag is read the way the inherited properties are: a value that states neither true nor false is reported and
     * leaves the flag as it stands, rather than being read as the {@code false} the raw parser answered with - which
     * would have moved the pool's notifications off the route its default selects without saying so.
     * </p>
     *
     * @param properties the interceptor properties
     */
    @Override
    public void setProperties(Map<String, InterceptorProperty> properties) {
        super.setProperties(properties);
        InterceptorProperty notifyPoolProperty = properties.get(NOTIFY_POOL);
        if (notifyPoolProperty != null && notifyPoolProperty.getValue() != null) {
            Boolean notifyPoolFlag = flagValue(notifyPoolProperty.getValue());
            if (notifyPoolFlag == null) {
                log.warn(MessageFactory.get("slowQueryReportJmx.invalidNotifyPool", notifyPoolProperty.getValue(),
                        Boolean.toString(isNotifyPool())));
            } else {
                setNotifyPool(notifyPoolFlag.booleanValue());
            }
        }
    }

    /**
     * Returns the JMX ObjectName for the given class and pool name.
     * <p>
     * A property carrying no value - which the interceptor-string grammar cannot produce, but a programmatically built
     * {@link org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition} can hand through - names nothing to
     * override, and reads as if the property were absent, the way every sibling property of this family reads its null
     * value.
     * </p>
     *
     * @param clazz    the interceptor class
     * @param poolName the name of the connection pool
     *
     * @return the JMX ObjectName
     *
     * @throws MalformedObjectNameException if the object name is malformed
     */
    public ObjectName getObjectName(Class<?> clazz, String poolName) throws MalformedObjectNameException {
        ObjectName oname;
        Map<String, InterceptorProperty> properties = getProperties();
        InterceptorProperty override = properties == null ? null : properties.get(objectNameAttribute);
        if (override != null && override.getValue() != null) {
            oname = new ObjectName(override.getValue());
        } else {
            oname = new ObjectName(ConnectionPool.POOL_JMX_TYPE_PREFIX + clazz.getName() + ",name=" +
                    objectNameValue(poolName));
        }
        return oname;
    }

    /**
     * Renders a pool name as an ObjectName property value, quoting it only when the unquoted grammar cannot carry it.
     * A name containing a comma, equals sign, colon, quote or another character the grammar reserves would otherwise
     * alter the ObjectName instead of naming the pool, or fail registration.
     *
     * @param poolName the pool name
     *
     * @return the property value, quoted when needed
     */
    private static String objectNameValue(String poolName) {
        if (poolName == null) {
            return "null";
        }
        try {
            ObjectName probe = new ObjectName(ConnectionPool.POOL_JMX_TYPE_PREFIX +
                    SlowQueryReportJmx.class.getName() + ",name=" + poolName);
            if (poolName.equals(probe.getKeyProperty("name")) && probe.getKeyPropertyList().size() == 2) {
                return poolName;
            }
        } catch (MalformedObjectNameException x) {
            // The grammar cannot carry the name unquoted; quote it below.
        }
        return ObjectName.quote(poolName);
    }

    /**
     * Recognises the two names this class reads on top of the ones the base reads: {@code notifyPool}, which decides
     * which notification channel reports a query, and {@code objectName}, which names the MBean (see
     * {@link #getObjectName(Class, String)}). Without them here, every correct configuration of this interceptor would
     * be reported as a typo by the property check it inherits.
     *
     * @param name the property name to test
     *
     * @return {@code true} if this configuration applies that name
     */
    @Override
    protected boolean isKnownProperty(String name) {
        return super.isKnownProperty(name) || NOTIFY_POOL.equals(name)
                || objectNameAttribute.equals(name);
    }
}

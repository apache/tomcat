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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceAlreadyExistsException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
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
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * JMX-enabled slow query report interceptor. Publishes slow and failed query statistics to JMX and provides
 * notifications when slow or failed queries are detected.
 * <p>
 * All of the publication described below belongs to the interceptor's own MBean, which exists only when the
 * interceptor is configured with {@code notifyPool=false}. In the default {@code notifyPool=true} mode this class
 * registers nothing of its own: the notifications are raised on the pool's own MBean instead, through
 * {@link org.apache.tomcat.jdbc.pool.jmx.ConnectionPool#notify(String, String)}, and that MBean is registered by the
 * pool itself when its {@code jmxEnabled} property is on - so a pool that is neither JMX-enabled nor configured with
 * {@code notifyPool=false} reports its slow and failed queries to its log alone.
 * </p>
 * <p>
 * The MBean and the notification broadcaster are published for the pool <em>name</em>: the first interceptor instance
 * to borrow a connection registers them, and every other instance sharing the name - the other connections of that
 * pool, the second interceptor of the recommended two-thresholds configuration, or a second pool configured with the
 * same name - reports through them. The MBean's resource is a name-bound {@link Registration}, not an interceptor
 * instance: it holds the name and the ObjectName it was published under and reaches neither the pool nor its sessions,
 * so the registration is a harmless, releasable entry when the pool that published it was abandoned without closing,
 * instead of a JVM-lifetime root pinning the abandoned pool through the MBeanServer. They stay published until the
 * last live pool registered under the name closes; closing one of several same-named pools leaves the registration
 * with the survivors. A pool that starts under the name after that last close has decided its release - racing it -
 * adopts the registration still published under the name, and the pending release notices the takeover and leaves the
 * registration with the new pool. The name is the one the pool started with - see
 * {@link ConnectionPool#getRegistrationName()} - so renaming a running pool, which the pool's own JMX allows, moves
 * nothing: the MBean stays published under the name it was registered under, and the pool keeps reporting through it
 * exactly as before the rename.
 * </p>
 * <p>
 * A JMX client's listener subscription belongs to the registration it was added through. JMX cannot carry a
 * subscription across an unregistration, so after the last pool of a name has closed, a client must attach its
 * listeners again to receive notifications from a pool started under that name anew.
 * </p>
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

    private static final Log log = LogFactory.getLog(SlowQueryReportJmx.class);

    /**
     * Creates a new SlowQueryReportJmx instance.
     */
    public SlowQueryReportJmx() {
    }

    /**
     * Registry of the registration resource published for each connection pool name. The registry value is the MBean
     * the server holds - never an interceptor instance: a value that reached the pool would let the registry and the
     * MBeanServer, both of which outlive an abandoned pool, keep the pool and everything it reaches alive for the life
     * of the JVM, because only a close ever removes a registration.
     */
    protected static final ConcurrentHashMap<String, SlowQueryReportJmxMBean> mbeans = new ConcurrentHashMap<>();

    /**
     * Notification broadcasters for each connection pool. Interceptor instances are created per pooled connection, but
     * JMX clients attach their listeners to the single MBean registered per pool, so notifications raised by any
     * instance of a pool must reach the broadcaster of the pool rather than a broadcaster private to the instance.
     */
    protected static final ConcurrentHashMap<String, NotificationBroadcasterSupport> notifiers = new ConcurrentHashMap<>();

    /**
     * Registry of the first registration failure seen for each pool name, holding the ObjectName that failed.
     * Registration is retried by every borrow of such a pool, but only the first failure of a name is logged with its
     * exception, so a persistent refusal - a foreign MBean holding the name, a security restriction - is not reported
     * with a stack trace once per borrow. The marker is dropped when a registration under the name succeeds and when
     * the name's release runs through {@link #deregisterJmx()}, so a later failure cycle is reported loudly once again.
     */
    protected static final ConcurrentHashMap<String, ObjectName> registrationFailuresLogged = new ConcurrentHashMap<>();

    /**
     * Notification broadcaster used before the pool name is known, and the one a notification of a name that has been
     * released is sent through; once the pool name is known and live, {@link #getNotifier()} returns the broadcaster
     * shared by all interceptor instances of that pool. Like the shared broadcasters, it is built with
     * {@link #NOTIFICATION_INFO}, so every handle's emitter surface advertises the types it sends.
     */
    protected volatile NotificationBroadcasterSupport notifier = new NotificationBroadcasterSupport(NOTIFICATION_INFO);

    /**
     * Returns the notification broadcaster shared by all registrants of the given pool name.
     *
     * @param name the pool name whose broadcaster is wanted
     *
     * @return the shared broadcaster registered for that name
     */
    private static NotificationBroadcasterSupport broadcasterFor(String name) {
        return notifiers.computeIfAbsent(name, key -> new NotificationBroadcasterSupport(NOTIFICATION_INFO));
    }

    /**
     * Returns the notification broadcaster that all interceptor instances of the current pool share, so that
     * notifications raised on any connection reach the listeners registered with the pool's MBean.
     *
     * @return the shared broadcaster for this interceptor's pool
     */
    protected NotificationBroadcasterSupport getNotifier() {
        String name = poolName;
        if (name == null) {
            return notifier;
        }
        return broadcasterFor(name);
    }

    /**
     * Returns the broadcaster that the notifications of this interceptor's pool are sent through. Unlike
     * {@link #getNotifier()} this one reads rather than provides: a send is not a reason for a name to exist. The
     * name's broadcaster is made for a live pool - including one whose registration was refused, which reports
     * through it all the same - but a name that no live pool holds any more has been released, and an entry put back
     * under it by a notification that was simply still in flight when the last pool came down is taken down by
     * nothing ever again: not the close that already ran, and not a later one, because no later pool of that name
     * exists. What such a send can still serve is whoever reached this instance, so it falls back to the
     * instance's own broadcaster.
     *
     * @return the broadcaster to send a notification of this interceptor's pool through
     */
    private NotificationBroadcasterSupport broadcasterToSendTo() {
        String name = poolName;
        if (name == null) {
            return notifier;
        }
        NotificationBroadcasterSupport shared = notifiers.get(name);
        if (shared != null) {
            return shared;
        }
        if (mbeans.containsKey(name) || getPoolStats(name) != null) {
            // A pool of the name is live: its broadcaster is built by the first
            // thing that needs it, and a send of a live pool is one
            return broadcasterFor(name);
        }
        return notifier;
    }

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
     * The connection pool reference.
     */
    protected ConnectionPool pool = null;

    /**
     * Returns the composite type used for JMX query statistics.
     *
     * @return the composite type, or {@code null} if initialization failed
     */
    protected static CompositeType getCompositeType() {
        if (SLOW_QUERY_TYPE == null) {
            try {
                SLOW_QUERY_TYPE = new CompositeType(SlowQueryReportJmx.class.getName(),
                        "Composite data type for query statistics", QueryStats.getFieldNames(),
                        QueryStats.getFieldDescriptions(), QueryStats.getFieldTypes());
            } catch (OpenDataException x) {
                log.warn(MessageFactory.get("slowQueryReportJmx.unableToInitCompositeType"), x);
            }
        }
        return SLOW_QUERY_TYPE;
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
            registerJmx();
        }
    }


    /**
     * Called when the connection pool is closed; releases the JMX registration of the pool's name - the MBean and the
     * broadcaster its clients' listeners are attached to - once no live pool is registered under the name any more.
     * Both belong to the name and are shared by every live pool of that name, so taking them down while a
     * survivor is still running would orphan the survivor's monitoring; releasing them while a survivor still runs
     * would unregister a shared registration that other closing or live pools still rely on. Whether this
     * close is the last one is decided atomically by {@link SlowQueryReport#releaseStatistics(ConnectionPool)} on the
     * way through {@code super.poolClosed}, so concurrent closes of same-named pools cannot all see a survivor and
     * leave the name registered for ever.
     * <p>
     * {@code ConnectionPool.close()} delivers this callback to a freshly instantiated interceptor per definition
     * as configured when the pool started - and per definition added since - in declaration order, and
     * {@code releaseStatistics} hands the {@code true} signal to whichever
     * definition happens to run first - which need not be this one. A chain of a base {@link SlowQueryReport}
     * definition above this one therefore has the base consume the signal and this definition see {@code false}
     * although the statistics, and with them the last live pool of the name, are already gone. The statistics being
     * gone is the same signal seen from the other side, so it releases as well; what remains safe is that the
     * release only ever takes down a registration no live pool of the name holds, because
     * {@link #releaseJmxRegistration(ConnectionPool)} re-verifies the name before anything is taken down - a pool
     * that started under the name between the statistics removal and this check has taken the registration back and
     * owns it from then on.
     * </p>
     *
     * @param pool the connection pool being closed
     */
    @Override
    public void poolClosed(ConnectionPool pool) {
        this.poolName = pool.getRegistrationName();
        super.poolClosed(pool);
        // Either this instance's releaseStatistics call deregistered the
        // closing pool as the last live pool of the name, or an earlier
        // definition of this very close consumed that signal and the name's
        // statistics are already gone - which means no live pool is
        // registered under it either. In both cases the registered MBean,
        // whose resource is the name's own registration and reaches no pool,
        // goes with them, unless a pool has taken the name back in the
        // meantime - which releaseJmxRegistration re-verifies. A pool that
        // never got far enough into its start to record a name never had
        // anything registered, and needs no release.
        if (lastPoolOfNameClosed || (poolName != null && getPoolStats(poolName) == null)) {
            releaseJmxRegistration(pool);
        }
    }

    /**
     * Completes the release of the current pool name whose statistics the given pool has just freed: drops the MBean
     * registration with the name's broadcaster and registration-failure marker through {@link #deregisterJmx()} -
     * unless a live pool is registered under the name again that does not include the releasing one. A pool that
     * started under the name after the release was decided has taken the name over, adopting the registration still
     * published under it, and it - not this close - owns the name's JMX state from then on; the last live pool of the
     * name is the one to release it.
     * <p>
     * The statistics are read the plain way here, without a lock, deliberately: a check computed inside a
     * {@code ConcurrentHashMap.compute} on the name would be atomic against a start but would also make that start
     * wait behind the release, and a release must never block a pool that is coming up. Without the lock the check
     * cannot see a start that begins after it has read - the {@link #deregisterJmx()} that follows then still takes
     * down only the registration the registry held at its own read, never a fresher one, and only the name's
     * broadcaster and failure marker can be hit early by a start landing inside that window; see
     * {@link #deregisterJmx()}. The one case the value guard does not spare is a start whose fresh registration
     * lands in the slot between the read here and the read in {@link #deregisterJmx()}: that release takes down
     * the value it read, leaving the newcomer to republish on its next borrow - a window narrower than the one
     * that existed before this guard, measured in the gap between two map reads. The guard likewise proceeds
     * when the statistics still hold the very pool that is closing, which only happens when the same pool
     * instance is restarted while this close is still in flight; the restarted generation then republishes its
     * registration on its first borrow, like any pool whose registration was released.
     * </p>
     *
     * @param releasedPool the pool whose statistics release decided this release of the name
     */
    protected void releaseJmxRegistration(ConnectionPool releasedPool) {
        PoolStatistics stats = perPoolStats.get(poolName);
        if (stats != null && !stats.hasLivePool(releasedPool)) {
            // A pool has started under the name since this close released the
            // statistics: the name is live again and the new pool owns
            // everything registered under it now - the MBean it adopted, the
            // broadcaster its clients' listeners are attached to, and the
            // bookkeeping. Leave them for the last live pool of the name.
            return;
        }
        deregisterJmx();
    }

    /**
     * Called when the connection pool is started.
     *
     * @param pool the connection pool being started
     */
    @Override
    public void poolStarted(ConnectionPool pool) {
        this.pool = pool;
        super.poolStarted(pool);
        this.poolName = pool.getRegistrationName();
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
                ConnectionPool p = this.pool;
                org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = p == null ? null : p.getJmxPool();
                if (jmx != null) {
                    jmx.notify(type, query);
                }
            } else {
                NotificationBroadcasterSupport target = broadcasterToSendTo();
                if (target != null) {
                    Notification notification = new Notification(type, getNotificationSource(), sequence,
                            System.currentTimeMillis(), query);

                    target.sendNotification(notification);
                }
            }
        } catch (RuntimeOperationsException e) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to send failed query notification.", e);
            }
        }
    }

    /**
     * Returns the source this MBean's notifications are sent with.
     * <p>
     * {@link Notification} marshals its source, so a listener outside this JVM - one reached through a JMX connector,
     * or one that hands the notification on to another process - only receives the notification at all if the source is
     * serializable. This MBean is not, and neither is the pool it reports on, so the source is the {@link ObjectName}
     * the MBean is registered under, which names the pool and is serializable. Interceptor instances are created per
     * pooled connection and none of them is the published resource - that is the pool name's {@link Registration} - so
     * an instance cannot name itself: the source is the name the registry's registration records as published, which
     * every instance of the name sends alike. Instances of a second definition under the same name - a two-thresholds
     * chain, or a pool that adopted a name another definition published - never published anything themselves and must
     * not stamp the notifications with the name their own properties would compute: that name is registered under
     * nothing, and a client filtering or grouping by source reads the published one. An instance whose name cannot be
     * formed - a malformed {@code objectName} property or a pool name that cannot be quoted into an ObjectName - was
     * never registered at all, so no listener reached through a connector can exist for it; the source is then a string
     * of the default name's shape, which still names the pool for the local listeners the broadcaster serves. A pool
     * that carries no name at all is the same case taken to its end: it holds no registration to name itself by, and
     * the shape its source takes is the default one with no name in it - whatever survives delivery, it cannot name a
     * pool that never had a name to register.
     *
     * @return the ObjectName the registration of this interceptor's pool name is published under, or, when nothing is
     *             registered under the name or the published name cannot be told, a name formed from this instance -
     *             as an ObjectName where that succeeds, and as a string of the default name's shape where it does not
     */
    protected Object getNotificationSource() {
        String name = poolName;
        try {
            // A pool that carries no name holds no registration to name itself
            // by: nothing can be keyed under no name (see registerJmx), and the
            // registry would refuse the lookup before answering it
            SlowQueryReportJmxMBean registered = name == null ? null : mbeans.get(name);
            if (registered != null) {
                // The name as that registration itself recorded it: the same
                // derivation the release path uses to unregister exactly the
                // occupied name, for the same reason - a name recomputed from
                // the properties of the instance that happens to be sending
                // can differ from the one the MBeanServer actually occupies
                return registrationObjectName(registered);
            }
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
     * The names of all pool names currently holding recorded statistics - the read behind
     * {@link #getPoolNames()} and the registration's own {@code PoolNames} attribute.
     *
     * @return the names of the pools that currently hold a statistics map
     */
    private static String[] allPoolNames() {
        Set<String> keys = perPoolStats.keySet();
        return keys.toArray(new String[0]);
    }

    /**
     * JMX operation - return the name of the pool
     *
     * @return the name of the pool, unique within the JVM, or {@code null} before this instance is bound to a pool
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
     * JMX operation - remove all stats for this connection pool. The statistics are keyed by pool name and shared by
     * every live pool configured with that name, so clearing them through one pool's MBean clears them for all of those
     * pools. Only the recorded content goes: the statistics map stays registered with the name - every live instance
     * holds that very map and keeps accumulating into it.
     */
    @Override
    public void resetStats() {
        clearStats(poolName);
    }

    /**
     * Clears the statistics map registered under the given pool name - the operation behind {@link #resetStats()} and
     * the registration's own {@code resetStats()}.
     *
     * @param poolName the pool name whose statistics are to be cleared
     */
    private static void clearStats(String poolName) {
        ConcurrentHashMap<String, QueryStats> queries = getPoolStats(poolName);
        if (queries != null) {
            // Clear the map itself rather than emptying it through an
            // iterator: the map is shared with every interceptor instance
            // of the name, which all hold this same instance
            queries.clear();
        }
    }

    /**
     * JMX operation - returns all the queries we have collected.
     *
     * @return - the slow query report as composite data; an empty array when the pool name holds no statistics map -
     *             none was ever registered under it, or the last live pool of the name has closed and freed it
     */
    @Override
    public CompositeData[] getSlowQueriesCD() throws OpenDataException {
        return slowQueriesCDFor(poolName);
    }

    /**
     * The statistics published under the given pool name as JMX composite data - the read behind
     * {@link #getSlowQueriesCD()} and the registration's own {@code SlowQueriesCD} attribute.
     *
     * @param poolName the pool name whose statistics are to be published
     *
     * @return the recorded statistics, or an empty array when the name holds no statistics map
     *
     * @throws OpenDataException if the composite data cannot be created
     */
    private static CompositeData[] slowQueriesCDFor(String poolName) throws OpenDataException {
        // Default to the empty array, never null: the descriptor advertises
        // the attribute as a CompositeData array, getPoolNames() beside it
        // already degrades to an empty String array, and the name's statistics
        // map can be gone while a client still reaches this registration -
        // reading between the release of the statistics and the unregistration
        // in poolClosed, or permanently when the unregistration of the closed
        // pool's MBean was refused and JmxUtil swallowed the failure. A client
        // that trusts the advertised type must not find null in its hands.
        CompositeDataSupport[] result = new CompositeDataSupport[0];
        ConcurrentHashMap<String, QueryStats> queries = getPoolStats(poolName);
        if (queries != null) {
            // Materialise a point-in-time snapshot before sizing the array:
            // the map is shared with every interceptor instance of the pool
            // name, which keep adding entries on every query and removing
            // them on capacity eviction, so sizing from the live map and
            // then filling from its weakly consistent entry set could run
            // past the end of the array or leave nulls in it
            Map<String, QueryStats> snapshot = new HashMap<>(queries);
            result = new CompositeDataSupport[snapshot.size()];
            int pos = 0;
            for (QueryStats qs : snapshot.values()) {
                result[pos++] = qs.getCompositeData(getCompositeType());
            }
        }
        return result;
    }

    /**
     * Releases the MBean registration and the shared broadcaster held for the current pool name - even when the pool
     * name is still owned by live pools. Normal pool shutdown releases through {@code super.poolClosed} plus
     * {@link #lastPoolOfNameClosed} in {@link #poolClosed(ConnectionPool)}, which keeps the registration for any
     * surviving same-named pool and skips the release when a new pool has taken the name back
     * ({@link #releaseJmxRegistration(ConnectionPool)}).
     * <p>
     * The registry slot is dropped only while it still holds the registration the registry was read at: a registration
     * a pool installing itself behind this release puts into the slot belongs to that pool and keeps its MBean
     * published. The unregistration uses the ObjectName that registration recorded when it was published, so it never
     * depends on the class or the {@code objectName} property of the instance running this release - which a closing
     * pool delivers freshly, and which need not share either with the registration it releases.
     * </p>
     * <p>
     * The broadcaster and the registration-failure marker belong to the name as much as the MBean does, so the release
     * drops them even when this pool never held the registry slot: a pool whose every registration attempt was refused
     * still reported through the broadcaster, and its throttle marker must not outlive the name and hush the first
     * failure of a later failure cycle under the name. Both entries are created lazily - by a report of a name that
     * is still live and by listeners a client adds through interceptor handles it still holds - so a client
     * subscribing to the released name concurrently with this release can have its late subscription re-insert an
     * entry nothing registered under the name uses any more; a fresh registration under the name re-uses or replaces
     * it. A notification that is merely still in flight while this release runs is not given that power, as
     * broadcasterToSendTo() reads the name's broadcaster instead of providing one: an intent to listen is a reason to
     * give the name a broadcaster again, a send that lost the race to the close is not.
     * </p>
     * <p>
     * The ObjectName to unregister is derived while the registry still holds the slot, before the slot is dropped: the
     * derivation can fail for a registration this class did not publish itself (see {@link
     * #registrationObjectName(SlowQueryReportJmxMBean)}), and a release that cannot tell the MBeanServer which name to
     * take down must leave the slot held so that a later release still can. Dropping the slot on the way to a failed
     * derivation would strand the published MBean behind a registry that no longer records it - the one half-released
     * state no later release of the name can repair.
     * </p>
     */
    protected void deregisterJmx() {
        SlowQueryReportJmxMBean registered = mbeans.get(poolName);
        try {
            if (registered != null) {
                // Derivation first, while the slot is still held: if it
                // throws, the registry still records the published MBean and
                // a later release of the name can still take it down
                ObjectName name = registrationObjectName(registered);
                if (mbeans.remove(poolName, registered)) {
                    JmxUtil.unregisterJmx(name);
                }
            }
        } catch (MalformedObjectNameException | RuntimeOperationsException e) {
            log.warn(MessageFactory.get("slowQueryReportJmx.jmxDeregistrationFailed"), e);
        } finally {
            notifiers.remove(poolName);
            registrationFailuresLogged.remove(poolName);
        }
    }

    /**
     * Returns the ObjectName the given registry registration occupies, as that registration itself recorded when it
     * was constructed. Unregistering this - rather than a name recomputed from the class and properties of the
     * instance running the release - is what keeps the unregistration on the name the MBean actually occupies: two
     * pools configured with one name may carry interceptor definitions that compute different ObjectNames, and the
     * release run by one of them must not give up on the name the other one's registration was published under and
     * leave it registered for ever.
     *
     * @param registered the registry value this release took out of the registry
     *
     * @return the ObjectName to unregister
     *
     * @throws MalformedObjectNameException if a fallback ObjectName cannot be formed
     */
    protected ObjectName registrationObjectName(SlowQueryReportJmxMBean registered)
            throws MalformedObjectNameException {
        if (registered instanceof Registration) {
            // The registration has carried its published name since its
            // construction: exactly the name the MBean occupies
            return ((Registration) registered).publishedUnder;
        }
        if (registered instanceof SlowQueryReportJmx) {
            // A value a subclass put into the registry itself: the name that
            // registration would be attempted under - the owner's own class
            // and properties, not this releasing instance's - is all there is
            // to go by
            SlowQueryReportJmx owner = (SlowQueryReportJmx) registered;
            return owner.getObjectName(owner.getClass(), poolName);
        }
        // A value this class never registered - the protected registry is open
        // to subclasses; the caller's own name shape is all there is to go by
        return getObjectName(getClass(), poolName);
    }


    /**
     * Returns the JMX ObjectName for the given class and pool name.
     * <p>
     * A property carrying no value - which the interceptor-string grammar
     * cannot produce, but a programmatically built {@link
     * org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition} can
     * hand through - names nothing to override, and reads as if the property
     * were absent, the way every sibling property of this family reads its
     * null value.
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
            oname = new ObjectName(ConnectionPool.POOL_JMX_TYPE_PREFIX + clazz.getName() + ",name=" + poolName);
        }
        return oname;
    }

    /**
     * Registers the pool name's MBean with JMX.
     * <p>
     * The registration is attempted under the protection of the per-name registry slot: the slot is taken with
     * {@code putIfAbsent}, holding the freshly constructed {@link Registration} the winner then publishes, so only one
     * MBean per pool name is ever published and it is released again when the MBeanServer refuses it. A failed publish
     * must not keep the slot, because the slot is the only registration gate: every later borrow would find it occupied
     * and skip the registration, so the pool would claim an MBean for its whole life that was never published - and
     * would not re-register even after whatever made the MBeanServer refuse it is gone. Releasing the slot lets a later
     * borrow retry; {@code JmxUtil.registerJmx} cannot be used for the attempt because it swallows every exception and
     * returns a name whatever happened, so the failure of its own call is unobservable to the caller. The retry is not
     * made cheaper than the attempt it repeats, and that is deliberate: the first borrow that finds the name free
     * publishes it, so a clash with a stale or foreign MBean costs monitoring only for as long as that MBean is there,
     * while a backoff would make every pool that ever clashed wait out a delay before its monitoring came back. What a
     * persistent refusal therefore costs is one publish attempt per borrow - the name formed, a {@link Registration}
     * built, and the MBeanServer's refusal - on a pool whose monitoring is misconfigured, which is the cost paid for
     * that recovery. Only the first
     * refusal of a name is logged with its exception, so a persistent refusal is not reported once per borrow. A borrow
     * that finds the slot held is skipped before the ObjectName is formed: forming it and building a {@link
     * Registration} for that race can only ever lose the race, and the healthy path pays that price once per pool
     * name rather than once per borrow. A pool that carries no name is skipped before any of it: no name is not a
     * key the registry takes, and the start-time refusal of such a pool (see {@link SlowQueryReport#poolStarted})
     * does not reach a definition the mutable interceptor property hands over later, so this is where that pairing
     * is met - and it costs the monitoring, never the borrow.
     * The one outcome this method cannot observe - a publication that lands after a release already let go of the slot
     * it took - is undone by the {@link Registration} itself, which checks its own publication as the server commits
     * it (see {@link Registration#postRegister(Boolean)}).
     */
    protected void registerJmx() {
        try {
            // With the pool notified, the notifications go to the pool's own
            // MBean and there is nothing for this interceptor to register
            if (isNotifyPool()) {
                return;
            }
            if (getCompositeType() != null) {
                String name = poolName;
                if (name == null) {
                    // The pool carries no name, and no name is not a name: the
                    // registry, the failure throttle and the ObjectName are all
                    // keyed by it, and the map takes no null key. A definition
                    // the pool was started with refuses this pool at its start
                    // with a message naming the missing setting (see
                    // SlowQueryReport#poolStarted); a definition the mutable
                    // interceptor property hands over later never runs that
                    // start callback, so the borrow path is where the pairing
                    // shows up - and a borrow is not the place to decide it.
                    // Declining quietly costs the monitoring, which is all a
                    // name that cannot be had can ever cost.
                    return;
                }
                if (mbeans.containsKey(name)) {
                    // The name holds a registration - published, or being
                    // published by a concurrent borrow: this borrow cannot
                    // take the slot either way, so it forms no ObjectName and
                    // builds no Registration just to lose the race to its own
                    // name's registration. A refused publish frees the slot
                    // again, so a retry needs no fresh look past this read.
                    return;
                }
                ObjectName oname = getObjectName(getClass(), name);
                // The registry and the MBeanServer keep the registration
                // resource, never this instance: an instance reaches the pool
                // (its own and the inherited pool fields), so publishing one
                // made the JVM-lifetime registration pin a pool that was
                // abandoned without closing - the only path that ever takes a
                // registration down again is a close, which abandonment
                // never runs
                Registration registration = new Registration(name, oname);
                if (mbeans.putIfAbsent(name, registration) == null) {
                    try {
                        // The registration carries the ObjectName it is to be
                        // published under from its construction: a release
                        // racing this registration unregisters the very name
                        // being published, not one of its own choosing
                        ManagementFactory.getPlatformMBeanServer()
                                .registerMBean(registration, oname);
                        // A registration under the name works again - drop
                        // the marker so a later failure cycle reports loudly
                        registrationFailuresLogged.remove(name);
                    } catch (Exception x) {
                        mbeans.remove(name, registration);
                        if (oname.equals(registrationFailuresLogged.putIfAbsent(name, oname))) {
                            // The loud report went out with the first
                            // refusal of this name; repeats ride along at
                            // debug, without a stack trace, so a persistent
                            // refusal stays quiet under borrow load
                            if (log.isDebugEnabled()) {
                                log.debug("Jmx registration of [" + oname + "] failed again: " + x);
                            }
                        } else {
                            // First refusal for the name, or the name now
                            // fails under a different ObjectName: report it
                            // loudly and remember to throttle the repeats
                            registrationFailuresLogged.put(name, oname);
                            if (x instanceof InstanceAlreadyExistsException) {
                                // Retryable: the name may be held by a stale
                                // or foreign MBean that a later borrow finds
                                // gone
                                log.warn(MessageFactory.get("slowQueryReportJmx.jmxRegistrationInUse", oname), x);
                            } else {
                                log.error(MessageFactory.get("slowQueryReportJmx.jmxRegistrationFailed", oname), x);
                            }
                        }
                    }
                }
            } else {
                log.warn(MessageFactory.get("slowQueryReportJmx.noJmxSupport",
                        SlowQueryReport.class.getName()));
            }
        } catch (MalformedObjectNameException | RuntimeOperationsException e) {
            // A name that cannot be formed - a malformed objectName property,
            // or a pool name carrying characters the ObjectName grammar
            // reserves - fails here, before the registry slot and the
            // registrationFailuresLogged throttle are reachable: there is no
            // ObjectName to record against the name, so the per-name throttle
            // cannot key this failure. The error is therefore reported on
            // every borrow of such a pool. That is acceptable: the condition
            // is a permanent misconfiguration, the stack trace is the signal
            // that makes the operator fix it, and a busy pool with a
            // misnamed monitoring MBean is rarer - and less costly - than a
            // suppressed error nobody ever acts on.
            log.error(MessageFactory.get("slowQueryReportJmx.jmxRegistrationFailedNoData"), e);
        }
    }

    /**
     * The management interface of a published {@link Registration} resource.
     * <p>
     * The MBeanServer's implicit Standard-MBean detection identifies a resource's management interface by the naming
     * convention: the interface of a class {@code X} is the one named {@code X}{@code MBean}. That held for the
     * interceptor instance itself only because its class and interface names happened to pair up
     * ({@code SlowQueryReportJmx}/{@link SlowQueryReportJmxMBean}); a separate resource class therefore needs its own
     * interface under its own name. The surface is unchanged - this interface declares nothing beyond
     * {@link SlowQueryReportJmxMBean}, it just carries it under the name the resource is found by.
     * </p>
     * <p>
     * Renaming this interface or {@link Registration} breaks that convention and with it the publication of every
     * pool name's MBean, so the two names are part of the registration mechanism, not just its vocabulary.
     * </p>
     */
    public interface RegistrationMBean extends SlowQueryReportJmxMBean {
    }

    /**
     * The resource published with the MBeanServer for one pool name.
     * <p>
     * It holds the name and the ObjectName it was published under, and nothing else: every attribute and operation
     * it serves reads the static per-name state - the statistics map, the name's broadcaster - exactly as an
     * interceptor instance of the name would. Keeping the pool out of the resource is the point: the MBeanServer and
     * the {@link #mbeans} registry outlive a pool that was abandoned without closing, and a resource that reached the
     * pool would pin it, its sessions and its classloader for the life of the JVM, since a registration is only ever
     * taken down by a close the abandoned pool never runs. The published surface is unchanged by this indirection:
     * {@link SlowQueryReportJmxMBean} plus the notification methods of {@link NotificationEmitter}, with listeners
     * landing on the name's shared broadcaster.
     * </p>
     * <p>
     * The class is public because the Standard-MBean introspection publishes the resource through its interfaces;
     * instances are created only by {@link #registerJmx()}.
     * </p>
     * <p>
     * A registration is valid only while the {@link #mbeans} slot of its name holds it. The publication and the
     * release of a name are separate, unguarded steps, so a publication can land behind a release of its own slot:
     * the release takes the slot out and unregisters a name nothing was published under yet, and the publication then
     * completes with nothing left in the JVM that still knows of it. The registration is the one object that can tell
     * its own publication apart from a name already let go of - {@link #postRegister(Boolean)} is the server
     * announcing that its repository has taken the registration, and a registration that finds its slot gone by then
     * takes that late publication back down itself, leaving the name free for the next pool to register it cleanly.
     * A slot by then held by another registration is not gone, but it only settles the question for the name that
     * registration occupies: a takeover published under this very name can only have got there after the release took
     * this publication down, so there is nothing of this one left to undo and the takeover is left alone, while a
     * takeover published under another name leaves this publication standing under a name the registry no longer
     * records - which nothing but the registration itself can still give up.
     * </p>
     */
    public static class Registration implements NotificationEmitter, RegistrationMBean, MBeanRegistration {
        /**
         * The pool name this registration reports for.
         */
        private final String registrationPoolName;
        /**
         * The ObjectName this registration is published under, carried from its construction: the release unregisters
         * exactly this name, whatever class and {@code objectName} property the interceptor instance running the
         * release carries - a closing pool delivers a freshly instantiated interceptor whose definitions need not match
         * those of the pool that registered.
         */
        protected final ObjectName publishedUnder;

        /**
         * Creates the registration for one pool name, bound to the ObjectName it is to be published under.
         *
         * @param poolName       the pool name to report for
         * @param publishedUnder the ObjectName the registration is published under
         */
        public Registration(String poolName, ObjectName publishedUnder) {
            this.registrationPoolName = poolName;
            this.publishedUnder = publishedUnder;
        }

        /*
         * The registration is published under exactly the ObjectName it was
         * constructed with; that name is also what a release unregisters, so
         * the server is answered with the name it asked about, unchanged.
         */
        @Override
        public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
            return name;
        }

        /*
         * A registration is published under its ObjectName and held by the
         * per-name slot, and those are two different questions: two
         * definitions of one pool name may compute different ObjectNames (see
         * registrationObjectName), so what the slot answers is not what decides
         * whether unregistering this name destroys somebody else's live MBean.
         * postRegister is the MBeanServer announcing that its repository has
         * taken this registration, the earliest point at which the outcome of
         * the publication is known here. A publication whose slot is gone was
         * decided away by a release that ran while it was in flight - the
         * release took the slot out and unregistered a name nothing had been
         * published under yet, so nothing else in the JVM knows to take this
         * now-late publication down. A publication whose slot is held by
         * another registration is only accounted for when that registration
         * occupies this very name: it can have committed there only after this
         * publication was already given up, which is why unregistering now
         * would take down the live MBean of the pool that took the name over,
         * with the registry correctly claiming that one is published. Under a
         * different name the successor's commit never needed this publication
         * to be given up first, the registry records only the successor's name,
         * and no release of the pool can ever reach this one - so the
         * registration is the only object that can still undo it. A
         * registration refused before its commit was never published and keeps
         * its name - if any - with whoever else holds it, which is why only a
         * completed registration here undoes anything.
         */
        @Override
        public void postRegister(Boolean registrationDone) {
            if (Boolean.TRUE.equals(registrationDone) && publicationIsLeftWithoutOwner()) {
                JmxUtil.unregisterJmx(publishedUnder);
            }
        }

        /*
         * Whether this publication, at the moment its commit is announced,
         * stands under a name nothing in this JVM can give up any more.
         */
        private boolean publicationIsLeftWithoutOwner() {
            SlowQueryReportJmxMBean held = mbeans.get(registrationPoolName);
            if (held == null) {
                // The release ran while this publication was in flight: it is
                // an orphan, and the name it carries is free for the next pool
                return true;
            }
            if (held == this) {
                // The registry holds this publication: it is the name's own
                return false;
            }
            if (held instanceof Registration) {
                // A takeover - and the only question that matters is whether it
                // occupies this name or came to the slot by another one
                return !((Registration) held).publishedUnder.equals(publishedUnder);
            }
            // A value a subclass put into the registry itself: the name it
            // occupies cannot be told from here, so it is given the field and
            // this publication leaves the name alone
            return false;
        }

        @Override
        public void preDeregister() throws Exception {
            // nothing to guard: the registry bookkeeping runs around the
            // unregistration in deregisterJmx(), not in the resource
        }

        @Override
        public void postDeregister() {
            // nothing to release: the resource holds no state beyond the two
            // fields a construction gave it
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
            clearStats(registrationPoolName);
        }

        @Override
        public CompositeData[] getSlowQueriesCD() throws OpenDataException {
            return slowQueriesCDFor(registrationPoolName);
        }

        @Override
        public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback)
                throws IllegalArgumentException {
            broadcasterFor(registrationPoolName).addNotificationListener(listener, filter, handback);
        }

        @Override
        public MBeanNotificationInfo[] getNotificationInfo() {
            return broadcasterFor(registrationPoolName).getNotificationInfo();
        }

        @Override
        public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
            broadcasterFor(registrationPoolName).removeNotificationListener(listener);
        }

        @Override
        public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback)
                throws ListenerNotFoundException {
            broadcasterFor(registrationPoolName).removeNotificationListener(listener, filter, handback);
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

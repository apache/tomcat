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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
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
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;
/**
 * JMX-enabled slow query report interceptor.
 * Publishes slow and failed query statistics to JMX and provides
 * notifications when slow or failed queries are detected.
 * <p>
 * The MBean and the notification broadcaster are published for the pool
 * <em>name</em>: the first interceptor instance to borrow a connection
 * registers them, and every other instance sharing the name - the other
 * connections of that pool, the second interceptor of the recommended
 * two-thresholds configuration, or a second pool configured with the
 * same name - reports through them. They stay published until the last
 * live pool registered under the name closes; closing one of several
 * same-named pools leaves the registration with the survivors.</p>
 * <p>
 * A JMX client's listener subscription belongs to the registration it
 * was added through. JMX cannot carry a subscription across an
 * unregistration, so after the last pool of a name has closed, a client
 * must attach its listeners again to receive notifications from a pool
 * started under that name anew.</p>
 */
public class SlowQueryReportJmx extends SlowQueryReport implements NotificationEmitter, SlowQueryReportJmxMBean{
    /**
     * Notification type for slow query events.
     */
    public static final String SLOW_QUERY_NOTIFICATION = "SLOW QUERY";
    /**
     * Notification type for failed query events.
     */
    public static final String FAILED_QUERY_NOTIFICATION = "FAILED QUERY";

    /**
     * Property key for overriding the JMX object name.
     */
    public static final String objectNameAttribute = "objectName";

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
     * Registry of JMX MBeans for each connection pool.
     */
    protected static final ConcurrentHashMap<String,SlowQueryReportJmxMBean> mbeans =
            new ConcurrentHashMap<>();

    /**
     * Notification broadcasters for each connection pool. Interceptor
     * instances are created per pooled connection, but JMX clients
     * attach their listeners to the single MBean registered per pool, so
     * notifications raised by any instance of a pool must reach the
     * broadcaster of the pool rather than a broadcaster private to the
     * instance.
     */
    protected static final ConcurrentHashMap<String,NotificationBroadcasterSupport> notifiers =
            new ConcurrentHashMap<>();

    /**
     * Registry of the first registration failure seen for each pool name,
     * holding the ObjectName that failed. Registration is retried by every
     * borrow of such a pool, but only the first failure of a name is
     * logged with its exception, so a persistent refusal - a foreign MBean
     * holding the name, a security restriction - is not reported with a
     * stack trace once per borrow. The marker is dropped when a
     * registration under the name succeeds and when the name's release
     * runs through {@link #deregisterJmx()}, so a later failure cycle is
     * reported loudly once again.
     */
    protected static final ConcurrentHashMap<String,ObjectName> registrationFailuresLogged =
            new ConcurrentHashMap<>();


    //==============================JMX STUFF========================
    /**
     * Notification broadcaster used before the pool name is known;
     * once the pool name is known, {@link #getNotifier()} returns the
     * broadcaster shared by all interceptor instances of that pool.
     */
    protected volatile NotificationBroadcasterSupport notifier = new NotificationBroadcasterSupport();

    /**
     * Returns the notification broadcaster that all interceptor
     * instances of the current pool share, so that notifications raised
     * on any connection reach the listeners registered with the pool's
     * MBean.
     * @return the shared broadcaster for this interceptor's pool
     */
    protected NotificationBroadcasterSupport getNotifier() {
        String name = poolName;
        if (name == null) {
            return notifier;
        }
        return notifiers.computeIfAbsent(name, key -> new NotificationBroadcasterSupport());
    }

    /**
     * Adds a notification listener.
     * @param listener the listener to add
     * @param filter the notification filter
     * @param handback the handback object
     * @throws IllegalArgumentException if the listener is null
     */
    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws IllegalArgumentException {
        getNotifier().addNotificationListener(listener, filter, handback);
    }


    /**
     * Returns information about the notifications this MBean can send.
     * @return an array of MBeanNotificationInfo objects
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return getNotifier().getNotificationInfo();
    }

    /**
     * Removes a notification listener.
     * @param listener the listener to remove
     * @throws ListenerNotFoundException if the listener was not previously added
     */
    @Override
    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        getNotifier().removeNotificationListener(listener);

    }

    /**
     * Removes a notification listener with the given filter and handback.
     * @param listener the listener to remove
     * @param filter the notification filter
     * @param handback the handback object
     * @throws ListenerNotFoundException if the listener was not previously added
     */
    @Override
    public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws ListenerNotFoundException {
        getNotifier().removeNotificationListener(listener, filter, handback);

    }


    //==============================JMX STUFF========================

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
     * @return the composite type, or {@code null} if initialization failed
     */
    protected static CompositeType getCompositeType() {
        if (SLOW_QUERY_TYPE==null) {
            try {
                SLOW_QUERY_TYPE = new CompositeType(
                        SlowQueryReportJmx.class.getName(),
                        "Composite data type for query statistics",
                        QueryStats.getFieldNames(),
                        QueryStats.getFieldDescriptions(),
                        QueryStats.getFieldTypes());
            }catch (OpenDataException x) {
                log.warn("Unable to initialize composite data type for JMX stats and notifications.",x);
            }
        }
        return SLOW_QUERY_TYPE;
    }

    /**
     * Resets this interceptor for a new connection, registering JMX if applicable.
     * @param parent the connection pool
     * @param con the pooled connection
     */
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        super.reset(parent, con);
        if (parent!=null) {
            poolName = parent.getName();
            pool = parent;
            registerJmx();
        }
    }


    /**
     * Called when the connection pool is closed; releases the JMX
     * registration of the pool's name - the MBean and the broadcaster its
     * clients' listeners are attached to - only once that pool was the
     * last live pool registered under the name. Both belong to the name
     * and are shared by every live pool of that name, so taking them down
     * while a survivor is still running would orphan the survivor's
     * monitoring; releasing them on every close but a decided last one
     * would unregister a shared registration that other closing or live
     * pools still rely on. Whether this close is the last one is decided
     * atomically by {@link SlowQueryReport#releaseStatistics(ConnectionPool)}
     * on the way through {@code super.poolClosed}, so concurrent closes
     * of same-named pools cannot all see a survivor and leave the name
     * registered for ever.
     * @param pool the connection pool being closed
     */
    @Override
    public void poolClosed(ConnectionPool pool) {
        this.poolName = pool.getName();
        super.poolClosed(pool);
        if (lastPoolOfNameClosed) {
            // the statistics of the name were freed by this very close,
            // so no live pool is registered under it any more - and the
            // registered MBean, whose delegate may be an interceptor
            // instance of an already closed pool, goes with them
            deregisterJmx();
        }
    }

    /**
     * Called when the connection pool is started.
     * @param pool the connection pool being started
     */
    @Override
    public void poolStarted(ConnectionPool pool) {
        this.pool = pool;
        super.poolStarted(pool);
        this.poolName = pool.getName();
    }

    /**
     * Reports a failed query and sends a JMX notification if logging is enabled.
     * @param query the SQL query
     * @param args the query arguments
     * @param name the connection pool name
     * @param start the start time in milliseconds
     * @param t the throwable that caused the failure
     * @return the formatted query string
     */
    @Override
    protected String reportFailedQuery(String query, Object[] args, String name, long start, Throwable t) {
        query = super.reportFailedQuery(query, args, name, start, t);
        if (isLogFailed()) {
            notifyJmx(query,FAILED_QUERY_NOTIFICATION);
        }
        return query;
    }

    /**
     * Sends a JMX notification for the given query and notification type.
     * @param query the SQL query string
     * @param type the notification type (slow or failed query)
     */
    protected void notifyJmx(String query, String type) {
        try {
            long sequence = notifySequence.incrementAndGet();

            if (isNotifyPool()) {
                if (this.pool!=null && this.pool.getJmxPool()!=null) {
                    this.pool.getJmxPool().notify(type, query);
                }
            } else {
                NotificationBroadcasterSupport target = getNotifier();
                if (target!=null) {
                    Notification notification =
                            new Notification(type,
                                         this,
                                         sequence,
                                         System.currentTimeMillis(),
                                         query);

                    target.sendNotification(notification);
                }
            }
        } catch (RuntimeOperationsException e) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to send failed query notification.",e);
            }
        }
    }

    /**
     * Reports a slow query and sends a JMX notification if logging is enabled.
     * @param query the SQL query
     * @param args the query arguments
     * @param name the connection pool name
     * @param start the start time in milliseconds
     * @param delta the elapsed time in milliseconds
     * @return the formatted query string
     */
    @Override
    protected String reportSlowQuery(String query, Object[] args, String name, long start, long delta) {
        query = super.reportSlowQuery(query, args, name, start, delta);
        if (isLogSlow()) {
            notifyJmx(query,SLOW_QUERY_NOTIFICATION);
        }
        return query;
    }

    /**
     * JMX operation - return the names of all the pools
     * @return - all the names of pools that we have stored data for,
     *         which is a view of the JVM-wide statistics registry rather
     *         than of this pool alone
     */
    @Override
    public String[] getPoolNames() {
        Set<String> keys = perPoolStats.keySet();
        return keys.toArray(new String[0]);
    }

    /**
     * JMX operation - return the name of the pool
     * @return the name of the pool, unique within the JVM, or
     *         {@code null} before this instance is bound to a pool
     */
    @Override
    public String getPoolName() {
        return poolName;
    }


    /**
     * Returns whether notifications are sent to the pool.
     * @return true if notifications are sent to the pool
     */
    public boolean isNotifyPool() {
        return notifyPool;
    }

    /**
     * Sets whether notifications are sent to the pool. Configuration
     * entry point only, applied to every interceptor instance of a pool by
     * {@code setProperties} while its connection chain is built: it is not
     * part of the JMX management interface, because writing it through the
     * registered MBean would reach only the one instance that owns the
     * registration and leave the pool's other connections routing as
     * before. See {@link SlowQueryReportJmxMBean}.
     * @param notifyPool true if notifications should be sent to the pool
     */
    public void setNotifyPool(boolean notifyPool) {
        this.notifyPool = notifyPool;
    }

    /**
     * JMX operation - remove all stats for this connection pool. The
     * statistics are keyed by pool name and shared by every live pool
     * configured with that name, so clearing them through one pool's MBean
     * clears them for all of those pools. Only the recorded content goes:
     * the statistics map stays registered with the name - every live
     * instance holds that very map and keeps accumulating into it.
     */
    @Override
    public void resetStats() {
        ConcurrentHashMap<String,QueryStats> queries = getPoolStats(poolName);
        if (queries!=null) {
            // clear the map itself rather than emptying it through an
            // iterator: the map is shared with every interceptor instance
            // of the name, which all hold this same instance
            queries.clear();
        }
    }

    /**
     * JMX operation - returns all the queries we have collected.
     * @return - the slow query report as composite data.
     */
    @Override
    public CompositeData[] getSlowQueriesCD() throws OpenDataException {
        CompositeDataSupport[] result = null;
        ConcurrentHashMap<String,QueryStats> queries = getPoolStats(poolName);
        if (queries!=null) {
            Set<Map.Entry<String,QueryStats>> stats = queries.entrySet();
            if (stats!=null) {
                result = new CompositeDataSupport[stats.size()];
                Iterator<Map.Entry<String,QueryStats>> it = stats.iterator();
                int pos = 0;
                while (it.hasNext()) {
                    Map.Entry<String,QueryStats> entry = it.next();
                    QueryStats qs = entry.getValue();
                    result[pos++] = qs.getCompositeData(getCompositeType());
                }
            }
        }
        return result;
    }

    /**
     * Releases the MBean registration and the shared broadcaster held
     * for the current pool name unconditionally - even when the pool
     * name is still owned by live pools. Normal pool shutdown releases
     * through {@code super.poolClosed} plus {@link #lastPoolOfNameClosed}
     * in {@link #poolClosed(ConnectionPool)}, which keeps the
     * registration for any surviving same-named pool.
     * <p>
     * The broadcaster and the registration-failure marker belong to the
     * name as much as the MBean does, so the release drops them even
     * when this pool never held the registry slot: a pool whose every
     * registration attempt was refused still reported through the
     * broadcaster, and its throttle marker must not outlive the name and
     * hush the first failure of a later failure cycle under the name.</p>
     */
    protected void deregisterJmx() {
        try {
            if (mbeans.remove(poolName)!=null) {
                ObjectName oname = getObjectName(getClass(),poolName);
                JmxUtil.unregisterJmx(oname);
            }
            notifiers.remove(poolName);
            registrationFailuresLogged.remove(poolName);
        } catch (MalformedObjectNameException | RuntimeOperationsException e) {
            log.warn("Jmx deregistration failed.",e);
        }

    }


    /**
     * Returns the JMX ObjectName for the given class and pool name.
     * @param clazz the interceptor class
     * @param poolName the name of the connection pool
     * @return the JMX ObjectName
     * @throws MalformedObjectNameException if the object name is malformed
     */
    public ObjectName getObjectName(Class<?> clazz, String poolName) throws MalformedObjectNameException {
        ObjectName oname;
        Map<String,InterceptorProperty> properties = getProperties();
        if (properties != null && properties.containsKey(objectNameAttribute)) {
            oname = new ObjectName(properties.get(objectNameAttribute).getValue());
        } else {
            oname = new ObjectName(ConnectionPool.POOL_JMX_TYPE_PREFIX+clazz.getName()+",name=" + poolName);
        }
        return oname;
    }

    /**
     * Registers this interceptor with JMX.
     * <p>
     * The registration is attempted under the protection of the per-name
     * registry slot: the slot is taken with {@code putIfAbsent} so only
     * one instance per pool name registers, and it is released again when
     * the MBeanServer refuses the MBean. A failed publish must not keep
     * the slot, because the slot is the only registration gate: every
     * later borrow would find it occupied and skip the registration, so
     * the pool would claim an MBean for its whole life that was never
     * published - and would not re-register even after whatever made the
     * MBeanServer refuse it is gone. Releasing the slot lets a later
     * borrow retry; {@code JmxUtil.registerJmx} cannot be used for the
     * attempt because it swallows every exception and returns a name
     * whatever happened, so the failure of its own call is unobservable
     * to the caller. Only the first refusal of a name is logged with its
     * exception, so a persistent refusal is not reported once per borrow.
     */
    protected void registerJmx() {
        try {
            //only if we notify the pool itself
            if (isNotifyPool()) {

            } else if (getCompositeType()!=null) {
                ObjectName oname = getObjectName(getClass(),poolName);
                if (mbeans.putIfAbsent(poolName, this)==null) {
                    try {
                        ManagementFactory.getPlatformMBeanServer().registerMBean(this, oname);
                        // a registration under the name works again - drop
                        // the marker so a later failure cycle reports loudly
                        registrationFailuresLogged.remove(poolName);
                    } catch (Exception x) {
                        mbeans.remove(poolName, this);
                        if (oname.equals(registrationFailuresLogged.putIfAbsent(poolName, oname))) {
                            // the loud report went out with the first
                            // refusal of this name; repeats ride along at
                            // debug, without a stack trace, so a persistent
                            // refusal stays quiet under borrow load
                            if (log.isDebugEnabled()) {
                                log.debug("Jmx registration of [" + oname
                                        + "] failed again: " + x);
                            }
                        } else {
                            // first refusal for the name, or the name now
                            // fails under a different ObjectName: report it
                            // loudly and remember to throttle the repeats
                            registrationFailuresLogged.put(poolName, oname);
                            if (x instanceof InstanceAlreadyExistsException) {
                                // retryable: the name may be held by a stale
                                // or foreign MBean that a later borrow finds
                                // gone
                                log.warn("Jmx registration of [" + oname
                                        + "] failed: the name is already in use by another MBean."
                                        + " The registration is retried by later borrows;"
                                        + " repeated failures are logged at debug level.", x);
                            } else {
                                log.error("Jmx registration of [" + oname
                                        + "] failed, no JMX data will be exposed for the query stats"
                                        + " until a registration succeeds. The registration is"
                                        + " retried by later borrows; repeated failures are"
                                        + " logged at debug level.", x);
                            }
                        }
                    }
                }
            } else {
                log.warn(SlowQueryReport.class.getName()+ "- No JMX support, composite type was not found.");
            }
        } catch (MalformedObjectNameException | RuntimeOperationsException e) {
            log.error("Jmx registration failed, no JMX data will be exposed for the query stats.",e);
        }
    }

    /**
     * Sets the interceptor properties, including the notifyPool flag.
     * @param properties the interceptor properties
     */
    @Override
    public void setProperties(Map<String, InterceptorProperty> properties) {
        super.setProperties(properties);
        final String threshold = "notifyPool";
        InterceptorProperty p1 = properties.get(threshold);
        if (p1!=null) {
            this.setNotifyPool(Boolean.parseBoolean(p1.getValue()));
        }
    }
}

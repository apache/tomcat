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

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Slow query report interceptor. Tracks timing of query executions.
 * <p>
 * Statistics belong to the pool the interceptor serves: the pool registers a store when it starts, every connection
 * books into it, and the close releases it. The store is read by the name the pool started with. Names must be unique
 * among live pools; a pool starting under a live pool's name is a configuration error and displaces the other pool's
 * store. Registrations hold the pool only weakly, so an abandoned pool is not pinned by its statistics.
 * <p>
 * A reporting definition added after the pool started registers its store on first borrow. Several reporting
 * definitions may be configured on one pool; the deepest definition that stores statistics books each execution once,
 * while each definition's own slow and failed query logging still fires.
 */
public class SlowQueryReport extends AbstractQueryReport {

    private static final Log log = LogFactory.getLog(SlowQueryReport.class);

    /**
     * The name of the property that configures {@link #setThreshold(long)}. The property names are spelled once each,
     * here: {@link #setProperties(java.util.Map)} applies them and {@link #isKnownProperty(String)} reports the names
     * it does not read, and the two must not disagree about the spelling.
     */
    protected static final String THRESHOLD = "threshold";
    /**
     * The name of the property that configures {@link #setMaxQueries(int)}.
     */
    protected static final String MAX_QUERIES = "maxQueries";
    /**
     * The name of the property that configures {@link #setLogSlow(boolean)}.
     */
    protected static final String LOG_SLOW = "logSlow";
    /**
     * The name of the property that configures {@link #setLogFailed(boolean)}.
     */
    protected static final String LOG_FAILED = "logFailed";

    /**
     * The statistics store published under each pool name. The name identifies the pool - a container names the pools
     * it manages uniquely, and the default name of a programmatically built pool is unique as well - so this one
     * registry resolves both the store a pool's connections book into and the store {@link #getPoolStats(String)} and
     * {@link SlowQueryReportJmxMBean#getPoolNames()} read.
     */
    protected static final ConcurrentHashMap<String, PoolStatistics> perPoolStats = new ConcurrentHashMap<>();
    /**
     * The statistics store this instance books to, resolved from the pool it serves; {@code null} when that pool has
     * no store. See {@link #reset(ConnectionPool, PooledConnection)}.
     */
    protected volatile PoolStatistics poolStatistics = null;
    /**
     * The statistics map this instance books to: the store of the pool it serves, handed to the chain by
     * {@link #reset(ConnectionPool, PooledConnection)} at a borrow and taken away when that chain is released.
     */
    protected volatile ConcurrentHashMap<String, QueryStats> queries = null;
    /**
     * Whether this instance has already reported that it cannot resolve a statistics store. One loss of the store is
     * one fact about the session this instance serves, so it is reported once; see
     * {@link #getQueryStats(String)} for why the report cannot simply be made on every execution. The atomic compare
     * and set makes the check and the marking one step, so concurrent executions cannot both report the same loss.
     */
    private final AtomicBoolean closedOrAbandonedReported = new AtomicBoolean();
    /**
     * Maximum number of queries we will be storing statistics for. Eviction keeps the number of stored queries near
     * this amount; while queries are being executed concurrently it can briefly be exceeded. This sizes the statistics
     * store only: the slow and failed query logging is configured by {@link #setLogSlow(boolean)} and
     * {@link #setLogFailed(boolean)} independently of this value.
     */
    protected int maxQueries = 1000;

    /**
     * Flag to enable or disable slow query logging
     */
    protected boolean logSlow = true;

    /**
     * Flag to enable or disable failed query logging
     */
    protected boolean logFailed = false;

    /**
     * Sort QueryStats by last invocation time
     */
    protected final Comparator<QueryStats> queryStatsComparator = new QueryStatsComparator();

    /**
     * Returns the query statistics published under the given pool name.
     * <p>
     * The store is published under the name the pool started with and is given up with that pool's close. The one
     * store that outlives the rule is a pool that was abandoned without closing: its bounded statistics stay
     * published under its name until a pool starting under the name replaces it, because the close that would give
     * them up never ran.
     * </p>
     *
     * @param poolname - the name of the pool we want to retrieve stats for
     *
     * @return a hash map containing the statistics of the monitored queries, kept near {@code maxQueries} entries by
     *             eviction, or {@code null} when no store is published under that name. A {@code null} name is answered
     *             the same way as a name that holds nothing, since nothing can be registered under it. The map is the
     *             live store, not a copy: callers must not modify it
     */
    public static ConcurrentHashMap<String, QueryStats> getPoolStats(String poolname) {
        if (poolname == null) {
            // Nothing is, or can be, registered under no name: the registry
            // takes no null key and poolStarted refuses a pool that carries
            // none. Callers read null as "this pool reports nothing here".
            return null;
        }
        PoolStatistics stats = perPoolStats.get(poolname);
        return stats == null ? null : stats.getQueryStats();
    }

    /**
     * Returns the statistics store registered for the given pool.
     *
     * @param pool the pool whose store is wanted, possibly {@code null}
     *
     * @return the store registered for that pool, or {@code null} when the pool has none - because it never registered
     *             one, because it has closed, or because it carries no name
     */
    protected static PoolStatistics statisticsFor(ConnectionPool pool) {
        if (pool == null) {
            return null;
        }
        String name = pool.getRegistrationName();
        if (name == null) {
            return null;
        }
        PoolStatistics stats = perPoolStats.get(name);
        // The registry is keyed by the pool name, but a name can be reused by
        // another pool after the first one was abandoned. The ownership check
        // keeps a lookup made for one pool from resolving another pool's store.
        return stats != null && stats.getPool() == pool && !stats.isReleased() ? stats : null;
    }

    /**
     * Creates a slow query report interceptor
     */
    public SlowQueryReport() {
        super();
    }

    /**
     * Sets the maximum number of queries to track statistics for.
     *
     * @param maxQueries the maximum number of queries
     */
    public void setMaxQueries(int maxQueries) {
        this.maxQueries = maxQueries;
    }


    @Override
    protected String reportFailedQuery(String query, Object[] args, String name, long start, long delta,
            Throwable t) {
        String sql = super.reportFailedQuery(query, args, name, start, delta, t);
        // The wall clock is read for the timestamp the failure is recorded
        // against, a point in time; the duration is the monotonic elapsed time
        // the statement proxy measured, so a step of the wall clock during the
        // execution cannot make the booked duration negative, as it could while
        // this hook recomputed it from start.
        long now = System.currentTimeMillis();
        if (booksSharedStats()) {
            QueryStats qs = this.getQueryStats(sql);
            if (qs != null) {
                qs.failure(delta, now);
            }
        }
        // The WARN reports the event and logFailed alone configures it;
        // maxQueries only sizes the statistics store. Keeping the log inside
        // that guard made maxQueries=0 - "store no per-query statistics" -
        // silence this channel while SlowQueryReportJmx kept sending the
        // FAILED QUERY notification behind isLogFailed() alone, the two
        // channels disagreeing on the same event.
        if (isLogFailed() && log.isWarnEnabled()) {
            log.warn(MessageFactory.get("slowQueryReport.failedQueryReport", sqlForLog(sql), Long.valueOf(delta)));
        }
        return sql;
    }

    @Override
    protected String reportQuery(String query, Object[] args, final String name, long start, long delta) {
        String sql = super.reportQuery(query, args, name, start, delta);
        if (booksSharedStats()) {
            QueryStats qs = this.getQueryStats(sql);
            if (qs != null) {
                // Booked against the time of recording, as the failure path
                // books its event: the execution start would age a long query
                // by its own duration against the entries that age by when
                // they finished.
                qs.add(delta, System.currentTimeMillis());
            }
        }
        return sql;
    }

    @Override
    protected String reportSlowQuery(String query, Object[] args, String name, long start, long delta) {
        String sql = super.reportSlowQuery(query, args, name, start, delta);
        if (booksSharedStats()) {
            QueryStats qs = this.getQueryStats(sql);
            if (qs != null) {
                // Booked against the time of recording, not the execution
                // start, for the same reason as in reportQuery above
                qs.add(delta, System.currentTimeMillis());
            }
        }
        // The WARN reports the event and logSlow alone configures it, exactly
        // as reportFailedQuery gates its own on logFailed: it sits outside the
        // capacity guard, and outside the statistics lookup, which legitimately
        // hands back no entry - when the store is disabled, when a chain release
        // took this instance's reference away, or when the entry was lost to the
        // post-putIfAbsent get race against eviction. Gating the WARN on that
        // lookup would let this channel fall silent while SlowQueryReportJmx
        // keeps sending the SLOW QUERY notification behind isLogSlow() alone,
        // the two channels disagreeing on the same event.
        if (isLogSlow() && log.isWarnEnabled()) {
            log.warn(MessageFactory.get("slowQueryReport.slowQueryReport", sqlForLog(sql), Long.valueOf(delta)));
        }
        return sql;
    }

    /**
     * Renders the SQL text for interpolation into a log message.
     * <p>
     * The statement text is application- and, wherever the application concatenates, user-shaped data, and JULI
     * writes message text out without newline escaping: carriage returns and line feeds embedded in the SQL would
     * otherwise split the report into additional lines the pool's logger identity carries - forged log entries
     * shaped by the SQL text itself. They are replaced by spaces, which keeps the statement readable in the one
     * line the report owns. The length is not bounded here: surfacing the slow statement in full is what this
     * interceptor is configured for, and the JMX representation of the same statistics - an opt-in channel the
     * operator reads, not a sequential log to forge - keeps the text exactly as executed.
     * </p>
     *
     * @param sql the SQL text to report, possibly {@code null}
     *
     * @return the text as safe to interpolate into a log message
     */
    private static String sqlForLog(String sql) {
        if (sql == null) {
            return "null";
        }
        if (sql.indexOf('\r') < 0 && sql.indexOf('\n') < 0) {
            return sql;
        }
        return sql.replace('\r', ' ').replace('\n', ' ');
    }

    /**
     * Invoked when the connection receives the close request. Not used for now.
     */
    @Override
    public void closeInvoked() {
        // NOOP
    }

    /**
     * Tracks the preparation time for a prepared statement.
     *
     * @param sql  the SQL statement
     * @param time the time taken to prepare the statement
     */
    @Override
    public void prepareStatement(String sql, long time) {
        if (booksSharedStats()) {
            QueryStats qs = getQueryStats(sql);
            if (qs != null) {
                qs.prepare(time);
            }
        }
    }

    /**
     * Tracks the preparation time for a callable statement.
     *
     * @param sql  the SQL call
     * @param time the time taken to prepare the call
     */
    @Override
    public void prepareCall(String sql, long time) {
        if (booksSharedStats()) {
            QueryStats qs = getQueryStats(sql);
            if (qs != null) {
                qs.prepare(time);
            }
        }
    }

    /**
     * Registers the statistics store of a pool that has started, under the name the pool started with. A store left
     * under that name by an abandoned pool is not inherited: it is replaced and released. Several reporting definitions
     * configured on one pool share the one store registered here.
     *
     * @param pool the connection pool that has started
     *
     * @throws IllegalStateException if the pool carries no name to publish under
     */
    @Override
    public void poolStarted(ConnectionPool pool) {
        super.poolStarted(pool);
        // The name is the one the pool started under, not the live property:
        // everything this registration publishes is keyed by it for the whole
        // life of the pool, and every later lookup of it comes back through
        // the same record (see ConnectionPool#getRegistrationName()).
        String name = pool.getRegistrationName();
        if (name == null) {
            // The mirror of releaseStatistics, which treats a nameless pool as
            // one that never registered anything: the pair has to agree, and a
            // ConcurrentHashMap would refuse the key before anything else could
            throw new IllegalStateException(MessageFactory.get("slowQueryReport.poolNameRequired"));
        }
        PoolStatistics own;
        PoolStatistics previous = null;
        synchronized (perPoolStats) {
            own = perPoolStats.get(name);
            if (own == null || own.getPool() != pool || own.isReleased()) {
                // A fresh store for this pool. The previous store under the
                // name may be the leftover of an abandoned pool or a duplicate
                // live pool's store; it is released below.
                own = new PoolStatistics(pool, name);
                previous = perPoolStats.put(name, own);
            }
        }
        reportDuplicateName(previous, pool, name);
        if (previous != null && previous.getPool() != pool) {
            // Defensive only: pool names must be unique, and a live store is
            // released by its own close, so a live duplicate should never
            // happen. This also covers the leftover of a pool abandoned without
            // closing, keeping it from blocking the new publication under the
            // name.
            previous.release();
        }
        this.poolStatistics = own;
        this.queries = own.getQueryStats();
    }

    /**
     * Reports a pool that takes a name a live pool already holds. The name is the key both the statistics and the JMX
     * registration are published under, so two live pools sharing it cannot be told apart: the registry, like the
     * MBeanServer, keeps one of them. That is a configuration error rather than a supported way to share reporting -
     * Tomcat gives every pool it manages a name of its own - so it is reported at error level. A store whose pool has
     * been collected is not a live duplicate: a pool that starts under the name an abandoned pool left behind takes
     * the name over without a report.
     *
     * @param previous the store the name held before, or {@code null} when it held none
     * @param pool     the pool that registered the store now
     * @param name     the name both pools carry
     */
    private static void reportDuplicateName(PoolStatistics previous, ConnectionPool pool, String name) {
        if (previous == null) {
            return;
        }
        ConnectionPool owner = previous.getPool();
        if (owner != null && owner != pool) {
            log.error(MessageFactory.get("slowQueryReport.duplicatePoolName", name));
        }
    }

    /**
     * Resolves the store the borrow this instance serves is to book into, registering the pool's store when this
     * instance is the first component of that pool to need one. The registration is checked against the pool's closed
     * flag under the registry's monitor, so a close and a registration cannot leave a store nothing will release.
     *
     * @param parent the pool the borrow belongs to, possibly {@code null} for the release of a chain
     *
     * @return the store to book into, or {@code null} when this pool publishes none and may not register one
     */
    private static PoolStatistics statisticsForBorrow(ConnectionPool parent) {
        PoolStatistics own = statisticsFor(parent);
        if (own != null || parent == null) {
            return own;
        }
        String name = parent.getRegistrationName();
        if (name == null) {
            // Nothing can be published under no name, at a start or anywhere
            // else; poolStarted says so when the pool has a start to say it at
            return null;
        }
        PoolStatistics previous = null;
        synchronized (perPoolStats) {
            if (parent.isClosed()) {
                return null;
            }
            own = perPoolStats.get(name);
            if (own == null || own.getPool() != parent || own.isReleased()) {
                // A fresh store for this pool. The store under the name may be
                // a leftover from an abandoned pool or a duplicate live pool's
                // store; it is released below so the name now belongs to this
                // pool.
                own = new PoolStatistics(parent, name);
                previous = perPoolStats.put(name, own);
            }
        }
        if (previous != null) {
            reportDuplicateName(previous, parent, name);
            if (previous.getPool() != parent) {
                // Defensive only: pool names must be unique, and a live store is
                // released by its own close, so a live duplicate should never
                // happen. This also covers the leftover of a pool abandoned
                // without closing, keeping it from blocking the new publication
                // under the name.
                previous.release();
            }
        }
        return own;
    }

    /**
     * Releases the statistics store of the given connection pool.
     * <p>
     * The callback is delivered by {@link ConnectionPool} to a freshly instantiated interceptor (see
     * {@code ConnectionPool#close(boolean)}), which has never held a reference to the statistics, so the store to
     * release can only be identified through the closing pool passed here, not through this instance's own state. The
     * release is idempotent, and every definition of the closing pool's chain can run it.
     * </p>
     *
     * @param pool the connection pool that has been closed
     */
    @Override
    public void poolClosed(ConnectionPool pool) {
        releaseStatistics(pool);
        super.poolClosed(pool);
    }

    /**
     * Gives up the statistics store registered for the given pool: the store stops publishing its statistics and is
     * taken out of the registry.
     *
     * @param pool the connection pool that has been closed
     *
     * @return the store that was registered for the pool, or {@code null} if it had registered none
     */
    protected static PoolStatistics releaseStatistics(ConnectionPool pool) {
        // The name the pool registered under, recorded at its start: a pool
        // renamed while running - the name property is mutable at runtime -
        // still releases what its start published, and not whatever it
        // happens to be called by the time it closes
        String name = pool.getRegistrationName();
        if (name == null) {
            // The pool never got far enough into its start to record a name, or
            // its close has already been delivered, so nothing is left for it to
            // release
            return null;
        }
        PoolStatistics own = null;
        synchronized (perPoolStats) {
            PoolStatistics current = perPoolStats.get(name);
            // Defensive: with unique pool names the store under this name is
            // this pool's own. The ownership check keeps a store another live
            // pool has taken over from being removed by the displaced pool.
            if (current != null && current.getPool() == pool) {
                own = perPoolStats.remove(name);
            }
        }
        if (own != null) {
            own.release();
        }
        return own;
    }

    /**
     * Whether this instance books the shared statistics for its pool. Every reporting definition sees every execution,
     * so only the deepest definition that stores statistics books; a definition with no capacity never claims the
     * booking. The decision reads the fixed chain below this instance and nothing is remembered across executions.
     *
     * @return {@code true} if this instance must book the statistics of its pool for the executions it reports
     */
    protected boolean booksSharedStats() {
        if (maxQueries <= 0) {
            return false;
        }
        for (JdbcInterceptor i = getNext(); i != null; i = i.getNext()) {
            if (i instanceof SlowQueryReport && ((SlowQueryReport) i).maxQueries > 0) {
                // A definition that stores statistics lies beneath this one
                // in the chain: it sees every execution this instance sees,
                // and it is the one to book them
                return false;
            }
        }
        return true;
    }

    /**
     * Reports that this instance lost the statistics store it booked executions on, once for as long as that store is
     * gone. The flag is cleared by the first booking made after a store is resolved again, so a later loss is a new
     * event.
     *
     * @return {@code true} when this call is the one that reports the loss, {@code false} when it was already reported
     */
    private boolean reportClosedOrAbandoned() {
        return closedOrAbandonedReported.compareAndSet(false, true);
    }

    /**
     * Retrieves or creates the statistics entry for the given SQL string in the store this instance books to.
     *
     * @param sql the SQL query string
     *
     * @return the query statistics, or {@code null} when this instance has no store to book to - the session it serves
     *             outlived the pool it was borrowed from, the chain it belongs to has been released, or its pool carries
     *             no name to publish statistics under
     */
    protected QueryStats getQueryStats(String sql) {
        if (sql == null) {
            sql = "";
        }
        ConcurrentHashMap<String, QueryStats> queries = SlowQueryReport.this.queries;
        PoolStatistics own = SlowQueryReport.this.poolStatistics;
        if (own != null && own.isReleased()) {
            // The pool closed while this session was still borrowed, so the
            // store is no longer observable: do not book into it.
            queries = null;
        }
        if (queries == null) {
            if (reportClosedOrAbandoned() && log.isWarnEnabled()) {
                log.warn(MessageFactory.get("slowQueryReport.connectionClosedOrAbandoned"));
            }
            return null;
        }
        // This instance books on a store again, so a later loss is a fresh
        // event - see reportClosedOrAbandoned(). Guarded so that the common,
        // healthy path stays a plain read of the flag.
        closedOrAbandonedReported.set(false);
        QueryStats qs = queries.get(sql);
        if (qs == null) {
            qs = new QueryStats(sql);
            if (queries.putIfAbsent(sql, qs) != null) {
                qs = queries.get(sql);
            } else {
                // We added a new element, see if we need to remove the oldest
                if (queries.size() > maxQueries) {
                    removeOldest(queries);
                }
            }
        }
        return qs;
    }

    /**
     * Evicts the least recently invoked query statistics until the map is back within {@code maxQueries} entries. The
     * map is shared by all the connections of the pool, so the eviction runs concurrently with the inserts and removals
     * of the other borrower threads and can only do as much work as its own snapshot allows; it then leaves the map
     * above capacity for the next new query to evict further.
     *
     * @param queries The queries map
     */
    protected void removeOldest(ConcurrentHashMap<String, QueryStats> queries) {
        // Sort a snapshot of the statistics rather than the live entries:
        // the invocation times are updated concurrently, and a comparator
        // that reads mutable state may violate its own contract while the
        // sort is in progress.
        ArrayList<QueryStats> snapshot = new ArrayList<>(queries.size());
        for (QueryStats qs : queries.values()) {
            snapshot.add(qs.snapshotForEviction());
        }
        Collections.sort(snapshot, queryStatsComparator);
        // The map is shared by all connections of the pool, so it may have
        // grown or shrunk since the snapshot was taken. Entries that another
        // thread removed already make our removal a no-op, so the snapshot
        // can run out while the map is still over capacity. Eviction then
        // gives up rather than running past the end of the list, leaving the
        // map over capacity by about as many queries as were being inserted
        // concurrently; the next new query evicts further.
        int removeIndex = 0;
        while (queries.size() > maxQueries && removeIndex < snapshot.size()) {
            String sql = snapshot.get(removeIndex).getQuery();
            removeIndex++;
            queries.remove(sql);
            if (log.isDebugEnabled()) {
                log.debug("Removing slow query, capacity reached:" + sqlForLog(sql));
            }
        }
    }


    /**
     * Resolves the statistics store this instance serves, registering its pool's store when this borrow is the first
     * component of that pool to need one.
     *
     * @param parent the connection pool
     * @param con    the pooled connection being reset
     */
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        super.reset(parent, con);
        // The name the pool's statistics were published under, recorded at its
        // start - renaming the pool does not move them
        PoolStatistics own = statisticsForBorrow(parent);
        poolStatistics = own;
        queries = own == null ? null : own.getQueryStats();
    }


    /**
     * Returns whether slow query logging is enabled.
     *
     * @return {@code true} if slow query logging is enabled
     */
    public boolean isLogSlow() {
        return logSlow;
    }

    /**
     * Enables or disables slow query logging.
     *
     * @param logSlow {@code true} to enable slow query logging
     */
    public void setLogSlow(boolean logSlow) {
        this.logSlow = logSlow;
    }

    /**
     * Returns whether failed query logging is enabled.
     *
     * @return {@code true} if failed query logging is enabled
     */
    public boolean isLogFailed() {
        return logFailed;
    }

    /**
     * Enables or disables failed query logging.
     *
     * @param logFailed {@code true} to enable failed query logging
     */
    public void setLogFailed(boolean logFailed) {
        this.logFailed = logFailed;
    }

    /**
     * Configures this interceptor from the properties of its definition: the
     * threshold, the capacity of the statistics store and the two log flags.
     * <p>
     * The values arrive as text, and text is where typos live, so a value this
     * interceptor cannot read is reported and left out rather than read as
     * something else: the setting keeps the value it already carries, which for
     * the log flags means the channel the operator meant to configure is not
     * switched off by the typo, and for the numbers means the default threshold
     * or capacity stays in force. A name this configuration does not read is
     * reported the same way. The pool configures an interceptor chain per
     * physical connection, so one misconfiguration is reported once per
     * configuration of the bad definition rather than once per JVM.
     * </p>
     *
     * @param properties the interceptor properties
     */
    @Override
    public void setProperties(Map<String, InterceptorProperty> properties) {
        super.setProperties(properties);
        for (String name : properties.keySet()) {
            if (!isKnownProperty(name)) {
                log.warn(MessageFactory.get("slowQueryReport.unknownProperty", name));
            }
        }
        InterceptorProperty thresholdProperty = properties.get(THRESHOLD);
        if (thresholdProperty != null && thresholdProperty.getValue() != null) {
            try {
                setThreshold(Long.parseLong(thresholdProperty.getValue()));
            } catch (NumberFormatException x) {
                log.warn(MessageFactory.get("slowQueryReport.invalidThreshold", thresholdProperty.getValue(),
                        Long.toString(getThreshold())));
            }
        }
        InterceptorProperty maxQueriesProperty = properties.get(MAX_QUERIES);
        if (maxQueriesProperty != null && maxQueriesProperty.getValue() != null) {
            try {
                setMaxQueries(Integer.parseInt(maxQueriesProperty.getValue()));
            } catch (NumberFormatException x) {
                log.warn(MessageFactory.get("slowQueryReport.invalidMaxQueries", maxQueriesProperty.getValue(),
                        Integer.toString(maxQueries)));
            }
        }
        InterceptorProperty logSlowProperty = properties.get(LOG_SLOW);
        if (logSlowProperty != null && logSlowProperty.getValue() != null) {
            Boolean logSlowFlag = flagValue(logSlowProperty.getValue());
            if (logSlowFlag == null) {
                log.warn(MessageFactory.get("slowQueryReport.invalidLogSlow", logSlowProperty.getValue(),
                        Boolean.toString(isLogSlow())));
            } else {
                setLogSlow(logSlowFlag.booleanValue());
            }
        }
        InterceptorProperty logFailedProperty = properties.get(LOG_FAILED);
        if (logFailedProperty != null && logFailedProperty.getValue() != null) {
            Boolean logFailedFlag = flagValue(logFailedProperty.getValue());
            if (logFailedFlag == null) {
                log.warn(MessageFactory.get("slowQueryReport.invalidLogFailed", logFailedProperty.getValue(),
                        Boolean.toString(isLogFailed())));
            } else {
                setLogFailed(logFailedFlag.booleanValue());
            }
        }
    }

    /**
     * Whether this interceptor configuration reads the named property.
     * {@code useEquals} belongs to the {@link org.apache.tomcat.jdbc.pool.JdbcInterceptor}
     * base and is reported as known beside this class's own names, since the
     * point of the report is a mistyped name rather than a configuration that
     * sets more than one thing. A subclass that reads properties of its own -
     * as {@link SlowQueryReportJmx} does - recognises them in its own override,
     * so a correct configuration of the subclass is never reported as a typo.
     *
     * @param name the property name to test
     *
     * @return {@code true} if this configuration applies that name
     */
    protected boolean isKnownProperty(String name) {
        return THRESHOLD.equals(name) || MAX_QUERIES.equals(name) || LOG_SLOW.equals(name)
                || LOG_FAILED.equals(name) || "useEquals".equals(name);
    }

    /**
     * The flag a configured text states, accepting the word in any case as the
     * parser this reading replaces did.
     *
     * @param value the configured text
     *
     * @return the stated flag, or {@code null} when the text states neither true
     *             nor false and is therefore a value to report rather than to
     *             read as false
     */
    protected static Boolean flagValue(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }


    /**
     * The query statistics of one pool: the map its connections book into and the name the map is published under.
     * <p>
     * The store refers to the pool only weakly. The name identifies the pool and must be unique among live pools -
     * Tomcat names every pool it manages, and the default name of a programmatic pool is unique as well - so the store
     * can outlive a pool abandoned without closing without pinning that pool, together with everything it reaches: its
     * queues, its pooled connections and their open physical sessions. The weak reference is what tells a live holder
     * of the name from an abandoned pool's leftover store.
     * </p>
     */
    protected static class PoolStatistics {
        /**
         * the pool this store belongs to, held weakly so that the store never pins a pool abandoned without closing;
         * used to tell a live pool holding the name from one whose store is all that is left of it
         */
        private final WeakReference<ConnectionPool> pool;
        /**
         * the name this store is published under - the registration name of the pool at its start
         */
        private final String name;
        /**
         * The statistics map of this one pool, kept near {@code maxQueries} entries by eviction
         */
        private final ConcurrentHashMap<String, QueryStats> stats = new ConcurrentHashMap<>();
        /**
         * What the reporting subclass has published for this pool with JMX - its broadcaster and, in the
         * {@code notifyPool=false} mode, its MBean. The state is 1:1 with this store.
         */
        private volatile SlowQueryReportJmx.PoolJmx jmxState;
        /**
         * Whether the store has been released with the close of its pool. A released store publishes nothing - its
         * statistics read as absent - so a chain or a JMX client that reaches it after the close sees the same
         * nothing that a reader of a name nobody holds sees, instead of statistics of a pool that no longer exists.
         */
        private volatile boolean released;

        /**
         * Creates the statistics store of one pool.
         *
         * @param pool the pool the store belongs to
         * @param name the name the store is published under
         */
        protected PoolStatistics(ConnectionPool pool, String name) {
            this.pool = new WeakReference<>(pool);
            this.name = name;
        }

        /**
         * Returns the pool this store belongs to while it is still reachable.
         *
         * @return the owning pool, or {@code null} once it has been collected
         */
        protected ConnectionPool getPool() {
            return pool.get();
        }

        /**
         * Returns the name this store is published under.
         *
         * @return the pool name recorded when the store was created
         */
        protected String getName() {
            return name;
        }

        /**
         * Returns whether this store has been released with the close of its pool.
         *
         * @return {@code true} if the pool's close has given this store up
         */
        protected boolean isReleased() {
            return released;
        }

        /**
         * Returns what the reporting subclass has published for this pool with JMX.
         *
         * @return the pool's JMX state, or {@code null} while no borrow of the pool has needed one or after the store
         *             has been released with its pool's close
         */
        protected SlowQueryReportJmx.PoolJmx getJmxState() {
            return released ? null : jmxState;
        }

        /**
         * Records what the reporting subclass has published for this pool, so that the release of this store gives it
         * up. Callers publish one state per store under this store's monitor (see
         * {@link SlowQueryReportJmx}).
         *
         * @param jmxState the state to hang from this store
         */
        protected void setJmxState(SlowQueryReportJmx.PoolJmx jmxState) {
            this.jmxState = jmxState;
        }

        /**
         * Releases the store with the close of its pool: later reads of its statistics answer as if nothing were
         * published, and the JMX state published for this pool is given up with it.
         * <p>
         * Whether a state exists is decided under this store's monitor, which is the one a borrow publishes a new state
         * under, so no state can be created behind the release and left for nothing to give up. The state's own release
         * then runs outside the monitor: it gives the MBean back to the MBeanServer, and a close has no business
         * holding the store while it does.
         * </p>
         */
        protected void release() {
            SlowQueryReportJmx.PoolJmx published;
            synchronized (this) {
                released = true;
                published = jmxState;
            }
            if (published != null) {
                published.released();
            }
        }

        /**
         * Returns the statistics map, or {@code null} once the store has been released with the close of its pool.
         *
         * @return this pool's query statistics
         */
        protected ConcurrentHashMap<String, QueryStats> getQueryStats() {
            return released ? null : stats;
        }
    }


    /**
     * Holds timing and invocation statistics for a single SQL query.
     */
    public static class QueryStats {
        static final String[] FIELD_NAMES = new String[] { "query", "nrOfInvocations", "maxInvocationTime",
                "maxInvocationDate", "minInvocationTime", "minInvocationDate", "totalInvocationTime", "failures",
                "prepareCount", "prepareTime", "lastInvocation" };

        static final String[] FIELD_DESCRIPTIONS = new String[] { "The SQL query",
                "The number of query invocations, a call to executeXXX",
                "The longest time for this query in milliseconds, 0 while no execution has been recorded yet",
                "The time and date for when the longest query took place",
                "The shortest time for this query in milliseconds, 0 while no execution has been recorded yet",
                "The time and date for when the shortest query took place",
                "The total amount of milliseconds spent executing this query", "The number of failures for this query",
                "The number of times this query was prepared (prepareStatement/prepareCall)",
                "The total number of milliseconds spent preparing this query",
                "The date and time of the last invocation or preparation" };

        static final OpenType<?>[] FIELD_TYPES = new OpenType[] { SimpleType.STRING, SimpleType.INTEGER,
                SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG, SimpleType.LONG,
                SimpleType.INTEGER, SimpleType.LONG, SimpleType.LONG };

        /**
         * The order the interceptor evicts by, which {@link #isOlderThan(QueryStats)} answers through so that the two
         * orderings of this class cannot state opposite things about one pair of entries.
         */
        private static final QueryStatsComparator evictionOrder = new QueryStatsComparator();

        private final String query;
        private volatile int nrOfInvocations;
        private volatile long maxInvocationTime = Long.MIN_VALUE;
        private volatile long maxInvocationDate;
        private volatile long minInvocationTime = Long.MAX_VALUE;
        private volatile long minInvocationDate;
        private volatile long totalInvocationTime;
        private volatile long failures;
        private volatile int prepareCount;
        private volatile long prepareTime;
        private volatile long lastInvocation = 0;

        /**
         * Returns the names of the fields in the composite data representation.
         *
         * @return the field names
         */
        public static String[] getFieldNames() {
            return FIELD_NAMES.clone();
        }

        /**
         * Returns the descriptions of the fields in the composite data representation.
         *
         * @return the field descriptions
         */
        public static String[] getFieldDescriptions() {
            return FIELD_DESCRIPTIONS.clone();
        }

        /**
         * Returns the JMX OpenType of each field in the composite data representation.
         *
         * @return the field types
         */
        public static OpenType<?>[] getFieldTypes() {
            return FIELD_TYPES.clone();
        }

        @Override
        public String toString() {
            SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy HH:mm:ss z", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            StringBuilder buf = new StringBuilder("QueryStats[query:");
            buf.append(query);
            buf.append(", nrOfInvocations:");
            buf.append(nrOfInvocations);
            buf.append(", maxInvocationTime:");
            buf.append(publishedMaxInvocationTime());
            buf.append(", maxInvocationDate:");
            buf.append(sdf.format(new java.util.Date(maxInvocationDate)));
            buf.append(", minInvocationTime:");
            buf.append(publishedMinInvocationTime());
            buf.append(", minInvocationDate:");
            buf.append(sdf.format(new java.util.Date(minInvocationDate)));
            buf.append(", totalInvocationTime:");
            buf.append(totalInvocationTime);
            buf.append(", averageInvocationTime:");
            buf.append(publishedAverageInvocationTime());
            buf.append(", failures:");
            buf.append(failures);
            buf.append(", prepareCount:");
            buf.append(prepareCount);
            buf.append(", prepareTime:");
            buf.append(prepareTime);
            buf.append(", lastInvocation:");
            buf.append(sdf.format(new java.util.Date(lastInvocation)));
            buf.append(']');
            return buf.toString();
        }

        /**
         * Returns the query statistics as JMX CompositeData.
         *
         * @param type the composite type definition
         *
         * @return the composite data representation of this query's statistics
         *
         * @throws OpenDataException if the data cannot be converted
         */
        public CompositeDataSupport getCompositeData(final CompositeType type) throws OpenDataException {
            Object[] values = new Object[] { query, Integer.valueOf(nrOfInvocations),
                    Long.valueOf(publishedMaxInvocationTime()), Long.valueOf(maxInvocationDate),
                    Long.valueOf(publishedMinInvocationTime()), Long.valueOf(minInvocationDate),
                    Long.valueOf(totalInvocationTime), Long.valueOf(failures), Integer.valueOf(prepareCount),
                    Long.valueOf(prepareTime), Long.valueOf(lastInvocation) };
            return new CompositeDataSupport(type, FIELD_NAMES, values);
        }

        /**
         * The maximum invocation time as the published views report it. A statement that is prepared but has never
         * executed holds a live entry whose running maximum is still its initial sentinel; publishing that as a
         * duration would hand consumers {@code Long.MIN_VALUE} milliseconds as if it were a measurement, so until
         * the first execution is booked the published duration is {@code 0}. The
         * {@link #getMaxInvocationTime()} getter keeps the sentinel, which is what distinguishes an entry that has
         * never executed from one whose executions all took no measurable time.
         *
         * @return the recorded maximum, or {@code 0} while no execution has been booked
         */
        private long publishedMaxInvocationTime() {
            return nrOfInvocations == 0 ? 0 : maxInvocationTime;
        }

        /**
         * The minimum invocation time as the published views report it, guarded against the initial sentinel of a
         * prepared-but-never-executed entry the way {@link #publishedMaxInvocationTime()} is.
         *
         * @return the recorded minimum, or {@code 0} while no execution has been booked
         */
        private long publishedMinInvocationTime() {
            return nrOfInvocations == 0 ? 0 : minInvocationTime;
        }

        /**
         * The average invocation time as the published views report it. An entry without executions has no average
         * to report; the {@code 0}/{@code 0} float division would render it as {@code NaN}, so the published
         * average is {@code 0} until the first execution is booked.
         *
         * @return the average of the booked executions, or {@code 0} while none has been booked
         */
        private float publishedAverageInvocationTime() {
            return nrOfInvocations == 0 ? 0f : (float) totalInvocationTime / (float) nrOfInvocations;
        }

        /**
         * Creates a new QueryStats instance for the given SQL query.
         *
         * @param query the SQL query string
         */
        public QueryStats(String query) {
            this.query = query;
        }

        /**
         * Returns a snapshot of the data that orders this query for eviction. Only the query and the time of its last
         * invocation are carried over: the snapshot exists to be sorted, and unlike the live statistics its ordering
         * does not change while a sort is running.
         *
         * @return a snapshot of this query and its last invocation time
         */
        private QueryStats snapshotForEviction() {
            QueryStats result = new QueryStats(query);
            result.lastInvocation = lastInvocation;
            return result;
        }

        /**
         * Records the time taken to prepare this query. Preparation counts as activity on the entry, so the last
         * invocation time that orders the statistics for eviction is updated as well: a statement that is prepared but
         * never executed must age like any other query instead of occupying a statistics slot for ever.
         *
         * @param invocationTime the time in milliseconds to prepare the query
         */
        public synchronized void prepare(long invocationTime) {
            prepareCount++;
            prepareTime += invocationTime;
            lastInvocation = System.currentTimeMillis();
        }

        /**
         * Records a successful query invocation, updating min/max/total timing statistics.
         *
         * @param invocationTime the time in milliseconds the query took
         * @param now            the current timestamp in milliseconds
         */
        public synchronized void add(long invocationTime, long now) {
            maxInvocationTime = Math.max(invocationTime, maxInvocationTime);
            if (maxInvocationTime == invocationTime) {
                maxInvocationDate = now;
            }
            minInvocationTime = Math.min(invocationTime, minInvocationTime);
            if (minInvocationTime == invocationTime) {
                minInvocationDate = now;
            }
            nrOfInvocations++;
            totalInvocationTime += invocationTime;
            lastInvocation = now;
        }

        /**
         * Records a failed query invocation, updating timing statistics and failure count.
         *
         * @param invocationTime the time in milliseconds the query took before failing
         * @param now            the current timestamp in milliseconds
         */
        public synchronized void failure(long invocationTime, long now) {
            add(invocationTime, now);
            failures++;

        }

        /**
         * Returns the SQL query string for these statistics.
         *
         * @return the SQL query string
         */
        public String getQuery() {
            return query;
        }

        /**
         * Returns the total number of times this query has been invoked.
         *
         * @return the number of invocations
         */
        public int getNrOfInvocations() {
            return nrOfInvocations;
        }

        /**
         * Returns the longest time this query took to execute. Until an execution has been booked the value is
         * {@link Long#MIN_VALUE}; the published views report {@code 0} in that state.
         *
         * @return the maximum invocation time in milliseconds, or {@link Long#MIN_VALUE} while no execution has been
         *             booked
         */
        public long getMaxInvocationTime() {
            return maxInvocationTime;
        }

        /**
         * Returns the timestamp when the longest query execution occurred. Until an execution has been booked the value
         * is {@code 0}, because no date has been recorded.
         *
         * @return the timestamp in milliseconds of the maximum invocation, or {@code 0} while no execution has been
         *             booked
         */
        public long getMaxInvocationDate() {
            return maxInvocationDate;
        }

        /**
         * Returns the shortest time this query took to execute. Until an execution has been booked the value is
         * {@link Long#MAX_VALUE}; the published views report {@code 0} in that state.
         *
         * @return the minimum invocation time in milliseconds, or {@link Long#MAX_VALUE} while no execution has been
         *             booked
         */
        public long getMinInvocationTime() {
            return minInvocationTime;
        }

        /**
         * Returns the timestamp when the shortest query execution occurred. Until an execution has been booked the value
         * is {@code 0}, because no date has been recorded.
         *
         * @return the timestamp in milliseconds of the minimum invocation, or {@code 0} while no execution has been
         *             booked
         */
        public long getMinInvocationDate() {
            return minInvocationDate;
        }

        /**
         * Returns the total time spent executing this query across all invocations.
         *
         * @return the total invocation time in milliseconds
         */
        public long getTotalInvocationTime() {
            return totalInvocationTime;
        }

        @Override
        public int hashCode() {
            return query.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof QueryStats) {
                QueryStats qs = (QueryStats) other;
                return qs.query.equals(this.query);
            }
            return false;
        }

        /**
         * Returns whether this query is the one eviction would take before the given one, that is, whether it was last
         * invoked or prepared earlier. An entry that has never been invoked or prepared is the newest of all by that
         * order rather than the oldest, and answers {@code false} here, because the comparison is the very one the
         * eviction of the statistics map makes.
         *
         * @param other the other query statistics to compare against
         *
         * @return {@code true} if this query's last invocation is older
         */
        public boolean isOlderThan(QueryStats other) {
            return evictionOrder.compare(this, other) < 0;
        }
    }

    /**
     * Compares QueryStats by their lastInvocation value. QueryStats that have never been updated have a lastInvocation
     * value of {@code 0} which is treated as the newest possible invocation.
     */
    // Public for unit tests
    public static class QueryStatsComparator implements Comparator<QueryStats> {

        /**
         * Creates a new QueryStatsComparator.
         */
        public QueryStatsComparator() {
        }

        @Override
        public int compare(QueryStats stats1, QueryStats stats2) {
            return Long.compare(handleZero(stats1.lastInvocation), handleZero(stats2.lastInvocation));
        }

        private static long handleZero(long value) {
            return value == 0 ? Long.MAX_VALUE : value;
        }

    }

}

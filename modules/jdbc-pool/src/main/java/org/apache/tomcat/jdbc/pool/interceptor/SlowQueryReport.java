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
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

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
 * The statistics belong to a pool: a pool registers its own store when it starts and releases it when it closes, and
 * every connection of that pool books its executions into that one store. The store is published under the name the
 * pool started with - see {@link ConnectionPool#getRegistrationName()} - which is how it is read by name
 * (see {@link #getPoolStats(String)}), so renaming a running pool, which the pool's own JMX allows, neither detaches
 * the pool from its statistics nor strands what its start published. A reporting definition the interceptor list gains
 * after the start is not stranded either: the list is mutable at runtime through the pool's own MBean, so its first
 * borrow registers the store its start would have registered, and the close that releases the store is told about that
 * definition as well.
 * </p>
 * <p>
 * The name is a view over pool-keyed registrations, not the registration itself. Two live pools configured with the
 * same name therefore each keep their own statistics instead of reporting into one another's numbers: the view answers
 * with the store of the pool that started under the name last, and the pool that started there first keeps collecting
 * into its own store, reachable by its own connections and by no name. Sharing a name between live pools is not
 * supported - a container guarantees a container-managed pool a name of its own, so what is left is programmatic
 * configuration - and a pool that starts under a name another live pool holds says so in the log.
 * </p>
 * <p>
 * The registrations hold the pool weakly: a pool abandoned without closing is never pinned by them and can be
 * collected with its open sessions, leaving only the bounded statistics store published under its name until a pool
 * starting under that name takes the name over.
 * </p>
 * <p>
 * More than one reporting definition can be configured on one pool - the two-thresholds configuration of
 * {@link SlowQueryReportJmx} is documented as a supported use - and every definition sees every execution, so the
 * shared statistics are booked by one definition only: the deepest one in the interceptor chain that stores statistics
 * (see {@link #booksSharedStats()}). A configuration of several definitions books each execution once, like a single
 * definition does; the per-definition channels - this definition's slow and failed query logging - keep firing for
 * every definition that configures them.
 * </p>
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
     * The view of the statistics stores by the name the owning pool started with. The registration itself is keyed by
     * pool instance (see {@link #perPoolStatistics}); this map exists for {@link #getPoolStats(String)} and
     * {@link SlowQueryReportJmxMBean#getPoolNames()}, which address statistics by name. It holds one store per name:
     * the store of the pool that started under that name last.
     */
    protected static final ConcurrentHashMap<String, PoolStatistics> perPoolStats = new ConcurrentHashMap<>();
    /**
     * The statistics store of every live pool, keyed by pool instance: pools are what the statistics belong to, so
     * two pools configured with the same name keep two stores and never book into each other's numbers. The map is
     * keyed weakly - a pool the application abandoned without closing must drain out of this JVM-lifetime registry
     * together with everything it reaches, exactly as it did before the registry keyed anything by pool.
     */
    private static final Map<ConnectionPool, PoolStatistics> perPoolStatistics = Collections
            .synchronizedMap(new WeakHashMap<ConnectionPool, PoolStatistics>());
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
     * {@link #getQueryStats(String)} for why the report cannot simply be made on every execution.
     */
    private volatile boolean closedOrAbandonedReported;
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
     * The name is a view over the pool-keyed registrations (see {@link #perPoolStats}): it publishes the store of the
     * pool that started under that name last, and is given up with that pool's close. The one store that outlives the
     * rule is a pool that was abandoned without closing: its bounded statistics stay published under its name until a
     * pool starting under the name replaces it, because the close that would give them up never ran.
     * </p>
     *
     * @param poolname - the name of the pool we want to retrieve stats for
     *
     * @return a hash map containing the statistics of the monitored queries, kept near {@code maxQueries} entries by
     *             eviction, or {@code null} when no store is published under that name. A {@code null} name is answered
     *             the same way as a name that holds nothing, since nothing can be registered under it
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
     * <p>
     * The name view answers whenever it publishes this pool's own store, which is the case for every pool the
     * container names and for every programmatic pool whose name no other live pool shares; for those the read is the
     * one concurrent lookup it has always been. Only a pool whose name a second live pool has taken over - the
     * configuration {@link #poolStarted(ConnectionPool)} warns about - is reached through the pool-keyed registry,
     * whose lookup is synchronised. An unsupported configuration pays for itself; the healthy borrow path does not.
     * </p>
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
        if (name != null) {
            PoolStatistics published = perPoolStats.get(name);
            if (published != null && published.isFor(pool)) {
                return published;
            }
        }
        return perPoolStatistics.get(pool);
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
            log.warn(MessageFactory.get("slowQueryReport.failedQueryReport", sqlForLog(sql), delta));
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
            log.warn(MessageFactory.get("slowQueryReport.slowQueryReport", sqlForLog(sql), delta));
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
     * Registers the statistics store of a pool that has started.
     * <p>
     * The store belongs to this pool alone and is published twice: in the pool-keyed registration, which is what the
     * pool's own connections book through, and in the view by the name the pool started with, which is what
     * {@link #getPoolStats(String)} and the JMX read surface answer from. A name is published by one store at a time,
     * so a pool that starts under a name another live pool holds takes over that view - it does not merge with the
     * other pool's statistics, and the warning its start writes says which of the two pools now answers for the name.
     * </p>
     * <p>
     * A pool that carries no name is refused. The name is not one setting among others here: it is the key of the view
     * the store is published in and of the ObjectName the JMX subclass publishes its MBean under, and neither can be
     * keyed under no name. Failing the start with a message that names the setting is therefore the honest outcome,
     * and it is what the pool reports as the reason its start failed - where the missing key gave a start failure that
     * said nothing. A container names every pool it starts, so reaching this is a programmatic pool configured without
     * a name; the check stays as the library's own guarantee that what it registers always has a key.
     * </p>
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
        synchronized (perPoolStatistics) {
            own = registerStatistics(pool, name);
        }
        this.poolStatistics = own;
        this.queries = own.getQueryStats();
    }

    /**
     * Registers a statistics store for the given pool, unless it holds one already.
     * <p>
     * The pool's monitor is the caller's to hold: {@link #poolStarted(ConnectionPool)} takes it for the start, and the
     * first borrow of a definition that joined the chain after the start takes it for the registration its start never
     * came. Registering rather than only reading is what keeps that second case alive - the interceptor property is
     * mutable at runtime, through the pool's own MBean as much as in code, and a reporting definition added to a
     * running pool has no start left to register with but every right to monitor the pool it was added to.
     * </p>
     * <p>
     * The registration is idempotent per pool: a store that is still publishing is kept, so the two definitions of the
     * recommended two-thresholds configuration, and every borrow of a late definition, share the one store the first of
     * them registered instead of each starting a statistics map of its own.
     * </p>
     *
     * @param pool the pool to register a store for
     * @param name the name the pool started with, which the store is published under in the view
     *
     * @return the store of that pool - the one it held, or the one this call registered
     */
    private static PoolStatistics registerStatistics(ConnectionPool pool, String name) {
        PoolStatistics existing = perPoolStatistics.get(pool);
        if (existing != null && !existing.isReleased()) {
            return existing;
        }
        PoolStatistics own = new PoolStatistics(pool, name);
        perPoolStatistics.put(pool, own);
        PoolStatistics displaced = perPoolStats.put(name, own);
        if (displaced != null && displaced != own && displaced.isLive() && log.isWarnEnabled()) {
            log.warn(MessageFactory.get("slowQueryReport.poolNameInUse", name));
        }
        return own;
    }

    /**
     * Resolves the store the borrow this instance serves is to book into, registering the pool's store when this
     * instance is the first component of that pool to need one.
     * <p>
     * The cheap read comes first, and it is the whole cost for a healthy pool: a pool named by its container answers
     * from the lock-free view. Only a borrow that finds nothing looks further, and only for a pool that is still live
     * and carries a name to publish under.
     * </p>
     * <p>
     * The liveness check is read under the registry's monitor, which is the monitor the close of the store's release
     * runs under, and that ordering is the point: {@link ConnectionPool} sets its closed flag before it tells the
     * interceptors about the close, so a registration that gets the monitor first is met by the close's removal, while
     * a close that got there first is not followed by a store nothing will ever give up. A session that outlived its
     * pool therefore still resolves nothing, which is the state the borrow reports once
     * (see {@link #getQueryStats(String)}).
     * </p>
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
        synchronized (perPoolStatistics) {
            if (parent.isClosed()) {
                return null;
            }
            return registerStatistics(parent, name);
        }
    }

    /**
     * Releases the statistics store of the given connection pool.
     * <p>
     * The callback is delivered by {@link ConnectionPool} to a freshly instantiated interceptor (see
     * {@code ConnectionPool#close(boolean)}), which has never held a reference to the statistics, so the store to
     * release can only be identified through the closing pool passed here, not through this instance's own state. The
     * release is idempotent and needs no decision about anyone else: the store being given up is the closing pool's
     * own, so a second pool that was configured with the same name never loses anything through it, and every
     * definition of the closing pool's chain can run the release without consuming a signal a later definition needed.
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
     * Gives up the statistics store registered for the given pool: the store stops publishing its statistics, the pool
     * is taken out of the pool-keyed registration, and the name view is freed unless a later pool has taken the name
     * over in the meantime.
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
        PoolStatistics own;
        synchronized (perPoolStatistics) {
            own = perPoolStatistics.remove(pool);
        }
        if (own == null) {
            // The pool never got far enough into its start to record a name, or
            // its close has already been delivered, so nothing is left for it to
            // release
            return null;
        }
        own.release();
        // The name view is freed only while it still publishes this pool's own
        // store: a pool that started under the name later owns the view now, and
        // this close has no claim on it.
        perPoolStats.remove(own.getName(), own);
        return own;
    }

    /**
     * Whether this instance is the one to book the statistics of its pool for the executions its chain reports.
     * <p>
     * Every configured reporting definition wraps the statement again, so one logical execution passes through every
     * definition's hooks; booking the pool's map from each of them would multiply its invocation counts, totals and
     * prepare counters by the number of definitions. The booking therefore belongs to the deepest definition in the
     * chain that stores statistics: it sees every execution the layers above it see, so no booking is lost, and its
     * measurement is the one closest to the driver. The layers above it report only their own channels - logging and,
     * for {@link SlowQueryReportJmx}, notifications. A definition that stores no statistics (a {@code maxQueries} of
     * zero) never claims the booking, so it cannot hold the statistics silent for the definitions above it.
     * </p>
     * <p>
     * The decision reads only the chain below this instance and the configured capacity of each layer found there.
     * Both are fixed when the chain is built (properties are set before the link, and the chain is published through
     * the connection's volatile handler reference), and nothing is remembered across executions, so a re-entered
     * execution - a report hook that itself runs a query on this pool - books on its own chain's merits, whatever the
     * enclosing execution's layers have already booked or passed on.
     * </p>
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
     * gone.
     * <p>
     * The condition is a fact about the session this instance serves - it outlived the pool whose statistics it booked
     * on, or the chain it belongs to has been released - and it stays exactly as true for every execution that follows,
     * while an application still holding a statement opened on the session can keep executing on it indefinitely.
     * Writing the line for each of those executions is a WARN per query, forever, and nothing configures it: this is not
     * one of the query reports the {@code logSlow} and {@code logFailed} flags gate. The flag is cleared by the first
     * booking this instance makes once it holds a store again - a borrow of the session it belongs to is what hands the
     * store back - so a loss that follows such a booking is a new event and earns its own report. A store lost again
     * before anything was booked on the recovered one states the same fact as the loss this line covered, and is not
     * reported a second time.
     * </p>
     *
     * @return {@code true} when this call is the one that reports the loss, {@code false} when the loss has already been
     *             reported
     */
    private boolean reportClosedOrAbandoned() {
        if (closedOrAbandonedReported) {
            return false;
        }
        closedOrAbandonedReported = true;
        return true;
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
        if (queries == null) {
            if (reportClosedOrAbandoned() && log.isWarnEnabled()) {
                log.warn(MessageFactory.get("slowQueryReport.connectionClosedOrAbandoned"));
            }
            return null;
        }
        // This instance books on a store again, so a later loss is a fresh
        // event - see reportClosedOrAbandoned(). Guarded so that the common,
        // healthy path stays a plain read of the flag.
        if (closedOrAbandonedReported) {
            closedOrAbandonedReported = false;
        }
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
     * The query statistics of one pool: the map its connections book into, the name the map is published under in the
     * view, and the pool the statistics belong to.
     * <p>
     * The pool is held with a weak reference: the store outlives the pool only in the sense that the bounded map under
     * its name stays published, and a store that held its pool strongly would pin an abandoned pool together with
     * everything it reaches - its queues, its pooled connections and their open physical sessions - which is what the
     * registrations did before the pool was held weakly. A store's own identity is what the name view is keyed by
     * (see {@link #perPoolStats}) and what a {@link SlowQueryReportJmx} interceptor's per-pool JMX state hangs from,
     * so neither has to know the pool to know which pool it is serving.
     * </p>
     */
    protected static class PoolStatistics {
        /**
         * the pool these statistics belong to, held weakly so that a pool abandoned without closing drains out of the
         * JVM-lifetime registries that reach this store
         */
        private final WeakReference<ConnectionPool> owner;
        /**
         * the name this store is published under in the view - the registration name of the pool at its start
         */
        private final String name;
        /**
         * The statistics map of this one pool, kept near {@code maxQueries} entries by eviction
         */
        private final ConcurrentHashMap<String, QueryStats> stats = new ConcurrentHashMap<>();
        /**
         * What the reporting subclass has published for this pool with JMX - its broadcaster and, in the
         * {@code notifyPool=false} mode, its MBean. The state is 1:1 with this store, which is 1:1 with the pool, which
         * is why it hangs from here instead of from a registry that would have to tell two pools of one name apart.
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
         * @param pool the pool the statistics belong to
         * @param name the name the store is published under in the view
         */
        protected PoolStatistics(ConnectionPool pool, String name) {
            owner = new WeakReference<>(pool);
            this.name = name;
        }

        /**
         * Returns whether this is the store of the given pool.
         *
         * @param pool the pool to look for
         *
         * @return {@code true} if this store belongs to the pool
         */
        protected boolean isFor(ConnectionPool pool) {
            return owner.get() == pool;
        }

        /**
         * Returns whether the pool this store belongs to is still live - which it is not once the store has been
         * released with its close, and not once a pool that was abandoned without closing has been collected. A store
         * whose pool a caller still holds always reads as live: a weak reference to a reachable pool is not cleared.
         *
         * @return {@code true} if a live pool still owns this store
         */
        protected boolean isLive() {
            return !released && owner.get() != null;
        }

        /**
         * Returns the name this store is published under in the view.
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
         * @return the pool's JMX state, or {@code null} while no borrow of the pool has needed one
         */
        protected SlowQueryReportJmx.PoolJmx getJmxState() {
            return jmxState;
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
            return FIELD_NAMES;
        }

        /**
         * Returns the descriptions of the fields in the composite data representation.
         *
         * @return the field descriptions
         */
        public static String[] getFieldDescriptions() {
            return FIELD_DESCRIPTIONS;
        }

        /**
         * Returns the JMX OpenType of each field in the composite data representation.
         *
         * @return the field types
         */
        public static OpenType<?>[] getFieldTypes() {
            return FIELD_TYPES;
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
         * Returns the longest time this query took to execute.
         *
         * @return the maximum invocation time in milliseconds
         */
        public long getMaxInvocationTime() {
            return maxInvocationTime;
        }

        /**
         * Returns the timestamp when the longest query execution occurred.
         *
         * @return the timestamp in milliseconds of the maximum invocation
         */
        public long getMaxInvocationDate() {
            return maxInvocationDate;
        }

        /**
         * Returns the shortest time this query took to execute.
         *
         * @return the minimum invocation time in milliseconds
         */
        public long getMinInvocationTime() {
            return minInvocationTime;
        }

        /**
         * Returns the timestamp when the shortest query execution occurred.
         *
         * @return the timestamp in milliseconds of the minimum invocation
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

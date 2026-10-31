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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Interceptor that caches {@code PreparedStatement} and/or {@code CallableStatement} instances on a connection.
 * <p>
 * A cache hit is answered by this interceptor alone: the rest of the interceptor chain is not invoked for it, and a
 * statement recycled into the cache is reset to driver defaults (its query timeout goes back to zero). Interceptors
 * listed <em>after</em> this one in {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setJdbcInterceptors(String)
 * jdbcInterceptors} therefore do not see reused statements - relevant to any interceptor whose {@code createStatement}
 * hook performs an action rather than only wrapping the statement: {@link QueryTimeoutInterceptor} must be listed
 * before this one to keep its timeout on cached statements, while wrapping-only interceptors are unaffected: their
 * wrapper is carried inside the cached proxy, and the cache binds a cached statement to the physical session the
 * prepare ran on rather than to whatever connection a wrapper beneath it reports for the statement.
 */
public class StatementCache extends StatementDecoratorInterceptor implements StatementCacheMBean {
    private static final Log log = LogFactory.getLog(StatementCache.class);
    /**
     * All statement types (prepared and callable).
     */
    protected static final String[] ALL_TYPES = new String[] { PREPARE_STATEMENT, PREPARE_CALL };
    /**
     * Callable statement type only.
     */
    protected static final String[] CALLABLE_TYPE = new String[] { PREPARE_CALL };
    /**
     * Prepared statement type only.
     */
    protected static final String[] PREPARED_TYPE = new String[] { PREPARE_STATEMENT };
    /**
     * No statement types.
     */
    protected static final String[] NO_TYPE = new String[] {};

    /**
     * Attribute key for storing the statement cache in connection attributes.
     */
    protected static final String STATEMENT_CACHE_ATTR = StatementCache.class.getName() + ".cache";

    private static final String SET_POOLABLE = "setPoolable";

    /**
     * Default constructor.
     */
    public StatementCache() {
    }

    private boolean cachePrepared = true;
    private boolean cacheCallable = false;
    private int maxCacheSize = 50;
    private PooledConnection pcon;
    private String[] types;

    private ObjectName oname = null;

    @Override
    public boolean isCachePrepared() {
        return cachePrepared;
    }

    @Override
    public boolean isCacheCallable() {
        return cacheCallable;
    }

    @Override
    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * Returns the statement types being cached.
     *
     * @return array of statement type strings
     */
    public String[] getTypes() {
        return types;
    }

    @Override
    public int getCacheSize() {
        AtomicInteger size = this.cacheSize;
        return size == null ? -1 : size.get();
    }

    @Override
    public void setProperties(Map<String, InterceptorProperty> properties) {
        super.setProperties(properties);
        InterceptorProperty p = properties.get("prepared");
        if (p != null) {
            cachePrepared = p.getValueAsBoolean(cachePrepared);
        }
        p = properties.get("callable");
        if (p != null) {
            cacheCallable = p.getValueAsBoolean(cacheCallable);
        }
        p = properties.get("max");
        if (p != null) {
            maxCacheSize = p.getValueAsInt(maxCacheSize);
        }
        if (cachePrepared && cacheCallable) {
            this.types = ALL_TYPES;
        } else if (cachePrepared) {
            this.types = PREPARED_TYPE;
        } else if (cacheCallable) {
            this.types = CALLABLE_TYPE;
        } else {
            this.types = NO_TYPE;
        }

    }

    private static ConcurrentHashMap<ConnectionPool, AtomicInteger> cacheSizeMap = new ConcurrentHashMap<>();

    private AtomicInteger cacheSize;

    @Override
    public void poolStarted(ConnectionPool pool) {
        cacheSizeMap.putIfAbsent(pool, new AtomicInteger(0));
        super.poolStarted(pool);
    }

    @Override
    public void poolClosed(ConnectionPool pool) {
        cacheSizeMap.remove(pool);
        super.poolClosed(pool);
    }

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        super.reset(parent, con);
        if (parent == null) {
            cacheSize = null;
            this.pcon = null;
            if (oname != null) {
                JmxUtil.unregisterJmx(oname);
                oname = null;
            }
        } else {
            cacheSize = cacheSizeMap.get(parent);
            this.pcon = con;
            if (!pcon.getAttributes().containsKey(STATEMENT_CACHE_ATTR)) {
                ConcurrentHashMap<CacheKey, CachedStatement> cache = new ConcurrentHashMap<>();
                pcon.getAttributes().put(STATEMENT_CACHE_ATTR, cache);
            }
            if (oname == null) {
                String keyprop = ",JdbcInterceptor=" + getClass().getSimpleName();
                oname = JmxUtil.registerJmx(pcon.getObjectName(), keyprop, this);
            }
        }
    }

    @Override
    public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<CacheKey, CachedStatement> statements = (ConcurrentHashMap<CacheKey, CachedStatement>) con
                .getAttributes().get(STATEMENT_CACHE_ATTR);

        if (statements != null) {
            for (Map.Entry<CacheKey, CachedStatement> p : statements.entrySet()) {
                closeStatement(p.getValue());
            }
            statements.clear();
        }

        super.disconnected(parent, con, finalizing);
    }

    /**
     * Closes the given cached statement by forcing it to close.
     *
     * @param st the cached statement to close
     */
    public void closeStatement(CachedStatement st) {
        if (st == null) {
            return;
        }
        st.forceClose();
    }

    @Override
    protected Object createDecorator(Object proxy, Method method, Object[] args, Object statement,
            Constructor<?> constructor, String sql)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        boolean process = process(this.types, method, false);
        if (process) {
            Object result = null;
            CachedStatement statementProxy = new CachedStatement((PreparedStatement) statement, sql);
            result = constructor.newInstance(new Object[] { statementProxy });
            statementProxy.setActualProxy(result);
            statementProxy.setConnection(proxy);
            statementProxy.setConstructor(constructor);
            statementProxy.setCacheKey(createCacheKey(method, args));
            return result;
        } else {
            return super.createDecorator(proxy, method, args, statement, constructor, sql);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        boolean process = process(this.types, method, false);
        if (process && args.length > 0 && args[0] instanceof String) {
            CachedStatement statement = isCached(method, args);
            if (statement != null) {
                // Remove it from the cache since it is used
                removeStatement(statement);
                if (statement.getAssociatedConnection() != currentPhysicalConnection()) {
                    // The entry belongs to a connection that has been
                    // replaced (a statement close that raced a reconnect
                    // parked it after the disconnect purge ran). It is a
                    // statement of a dead connection: close it and prepare
                    // again rather than hand it to this borrower.
                    closeStatement(statement);
                    return super.invoke(proxy, method, args);
                }
                // The cached proxy carries the connection handle of the
                // borrower that closed the statement, which by now is a
                // disposed facade. Re-bind it to the borrower the proxy is
                // handed to, so that getConnection() on the reused statement
                // reports a live handle instead of the previous borrower's.
                statement.setConnection(proxy);
                return statement.getActualProxy();
            } else {
                return super.invoke(proxy, method, args);
            }
        } else {
            return super.invoke(proxy, method, args);
        }
    }

    /**
     * Checks if a statement for the given method and arguments is currently cached.
     *
     * @param method the SQL method (prepareStatement or prepareCall)
     * @param args   the method arguments
     *
     * @return the cached statement, or null if not cached
     */
    public CachedStatement isCached(Method method, Object[] args) {
        ConcurrentHashMap<CacheKey, CachedStatement> cache = getCache();
        if (cache == null) {
            return null;
        }
        CacheKey key = createCacheKey(method, args);
        if (key == null) {
            throw new IllegalArgumentException(MessageFactory.get("statementCache.nullKey"));
        }
        return cache.get(key);
    }

    /**
     * Caches the given statement proxy if there is room in the cache.
     * <p>
     * The insertion is atomic: of two statements closed concurrently under one key exactly one is parked, and the other
     * is refused as a duplicate - a park can never overwrite, and so can never orphan, a statement another close has
     * already parked. A proxy that is refused was never in the map, and the caller that receives {@code false} must
     * close the statement it offered.
     *
     * @param proxy the statement proxy to cache
     *
     * @return true if the statement was cached, false otherwise
     */
    public boolean cacheStatement(CachedStatement proxy) {
        ConcurrentHashMap<CacheKey, CachedStatement> cache = getCache();
        if (cache == null) {
            return false;
        }
        if (proxy.getCacheKey() == null) {
            return false;
        } else if (proxy.getAssociatedConnection() != currentPhysicalConnection()) {
            // The physical connection the statement was prepared on has
            // been replaced (typically a close that raced a
            // disconnect/reconnect, landing after the disconnected() purge
            // had already run). The statement belongs to a dead connection
            // and must not be parked in the cache of the new one.
            logLostAssociationOnce(proxy);
            return false;
        } else if (cacheSize.get() >= maxCacheSize) {
            return false;
        } else if (cacheSize.incrementAndGet() > maxCacheSize) {
            cacheSize.decrementAndGet();
            return false;
        }
        // Reserve the slot first and let the map itself arbitrate the
        // duplicate check: putIfAbsent makes the statement visible
        // atomically, so a second close racing in under the same key
        // either sees the parked statement and is refused, or parks its
        // own - it can no longer overwrite it. (The separate
        // containsKey/put pair this replaces let both racers pass the
        // check, counted both, and let the second put orphan the
        // statement the first one had parked.) Reserving before the
        // insert keeps the pool-wide count ahead of the map's visibility,
        // as increment-then-put already did, so retracting a reservation
        // never has to withdraw an entry that another thread can already
        // see, hand out, or close underneath.
        boolean parked = false;
        try {
            parked = cache.putIfAbsent(proxy.getCacheKey(), proxy) == null;
        } finally {
            if (!parked) {
                // The key was already taken, or the insert threw: this
                // statement is not in the map and never was. Undo exactly
                // the one increment this call made and let the caller
                // close it - nothing here can leak a slot or a statement.
                cacheSize.decrementAndGet();
            }
        }
        return parked;
    }

    /**
     * Removes the given statement from the cache. The removal is identity-aware: it only takes effect while the entry
     * under the statement's key still holds this very proxy, so a proxy that was never parked - or is no longer the
     * cached statement - cannot evict a different statement parked under an equal key.
     *
     * @param proxy the statement proxy to remove
     *
     * @return true if the statement was removed, false otherwise
     */
    public boolean removeStatement(CachedStatement proxy) {
        ConcurrentHashMap<CacheKey, CachedStatement> cache = getCache();
        if (cache == null) {
            return false;
        }
        if (cache.remove(proxy.getCacheKey(), proxy)) {
            cacheSize.decrementAndGet();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the physical connection behind the pooled connection this interceptor is bound to, or {@code null} if
     * there is none (yet).
     *
     * @return the current physical connection, or null
     */
    private Connection currentPhysicalConnection() {
        PooledConnection pCon = this.pcon;
        return pCon == null ? null : pCon.getConnection();
    }

    /**
     * Whether a reported connection is one of the handles the pool itself hands out - a proxy whose invocation
     * handler is a pool interceptor - rather than a physical connection. Only the pool's own handles are read this
     * way; an answer from anything else stays the statement's reported binding, so a driver that honestly names a
     * replaced (dead) session keeps refusing the park.
     *
     * @param reported the value reported as a statement's connection
     *
     * @return true if the value is one of the pool's own connection handles
     */
    private static boolean isPoolHandle(Connection reported) {
        return reported != null && Proxy.isProxyClass(reported.getClass())
                && Proxy.getInvocationHandler(reported) instanceof JdbcInterceptor;
    }

    /**
     * Set once the refusal of a park for a lost association has been logged for the pooled connection this
     * interceptor instance is bound to (the pool builds one chain, and so one interceptor instance, per pooled
     * connection), so the note appears once per connection and not once per refused statement. The claim is a
     * compare-and-set run under the debug guard, so of two refusals on concurrent closes exactly one logs, and
     * a refusal met while debug is off leaves the claim unspent for a later refusal to make after debug is
     * enabled.
     */
    private final AtomicBoolean associationLossLogged = new AtomicBoolean();

    /**
     * Logs, at most once per connection, that a park was refused because the connection recorded for the statement
     * is not the current physical one. The usual cause is benign - a close racing a session replacement - but a
     * wrapper beneath this cache that answers the statement's {@code getConnection()} with a value this cache
     * cannot resolve to a physical session reaches this refusal for every statement it closes, and a cache that
     * never caches is worth one line of log.
     *
     * @param proxy the statement whose park was refused
     */
    private void logLostAssociationOnce(CachedStatement proxy) {
        // The debug guard runs first, so a refusal met while debug is off does
        // not spend the claim, and the compare-and-set runs second, so of the
        // refusals met while debug is on exactly one logs.
        if (log.isDebugEnabled() && associationLossLogged.compareAndSet(false, true)) {
            log.debug("Not caching statement [" + proxy.getSql() + "]: the connection recorded for it is "
                    + "not the current physical connection. Either the session was replaced while the "
                    + "statement was being closed, or an interceptor listed below "
                    + getClass().getSimpleName() + " answers the statement's getConnection() with a value "
                    + "that cannot be resolved to the physical session, which keeps every statement out "
                    + "of the cache.");
        }
    }

    /**
     * Returns the statement cache for the current pooled connection.
     *
     * @return the cache map, or null if the connection has been closed
     */
    protected ConcurrentHashMap<CacheKey, CachedStatement> getCache() {
        PooledConnection pCon = this.pcon;
        if (pCon == null) {
            if (log.isWarnEnabled()) {
                log.warn(MessageFactory.get("statementCache.connectionClosedOrAbandoned"));
            }
            return null;
        }
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<CacheKey, CachedStatement> cache = (ConcurrentHashMap<CacheKey, CachedStatement>) pCon
                .getAttributes().get(STATEMENT_CACHE_ATTR);
        return cache;
    }

    @Override
    public int getCacheSizePerConnection() {
        ConcurrentHashMap<CacheKey, CachedStatement> cache = getCache();
        if (cache == null) {
            return 0;
        }
        return cache.size();
    }

    /**
     * Proxy for a cached prepared statement.
     */
    protected class CachedStatement extends StatementDecoratorInterceptor.StatementProxy<PreparedStatement> {
        /**
         * Cache key for this statement.
         */
        CacheKey key;
        /**
         * The physical connection this statement was prepared on. Cached statements are bound to the connection they
         * were prepared on; if the pooled connection is reconnected, the entries become stale and must neither be
         * cached nor handed out.
         */
        private final Connection associatedConnection;
        /**
         * Poolable hint set by the application. Tracked here as some drivers (e.g. H2) always return false from
         * {@code isPoolable()}.
         */
        private boolean poolable = true;

        /**
         * Constructs a CachedStatement, recording the physical connection the statement was prepared on as the
         * answer of the statement itself (or, when that answer is not a physical connection the cache can read, of
         * the pool's own record of the session the prepare ran on).
         *
         * @param parent the parent prepared statement
         * @param sql    the SQL text
         */
        public CachedStatement(PreparedStatement parent, String sql) {
            super(parent, sql);
            associatedConnection = associatedConnectionOf(parent);
        }

        /**
         * Constructs a CachedStatement with an already determined binding to the physical connection the statement
         * was prepared on. {@link #closeInvoked()} parks through this constructor, carrying the binding that was
         * made when the statement was prepared down to the proxy that enters the cache: asking the delegate again
         * at park time would hand a wrapper beneath this cache another chance to answer with something that is not
         * the physical session, while the binding made at prepare time names the session the statement was really
         * prepared on and still lets a session replacement between the prepare and the park refuse it.
         *
         * @param parent               the parent prepared statement
         * @param sql                  the SQL text
         * @param associatedConnection the physical connection the statement was prepared on
         */
        CachedStatement(PreparedStatement parent, String sql, Connection associatedConnection) {
            super(parent, sql);
            this.associatedConnection = associatedConnection;
        }

        /**
         * Resolves the physical connection a statement belongs to. The connection the driver reports for the
         * statement itself is authoritative; a wrapper listed beneath this cache may however intercept the call and
         * answer it with a pool handle rather than forward it to the driver, and a handle names no session of its
         * own - the physical session that sits behind the handle now, and behind the prepare of this very
         * statement, serves as the binding for that answer. Only when the driver will not answer does the pool's
         * own record of the current connection serve as the binding.
         *
         * @param statement the statement whose associated connection to determine
         *
         * @return the physical connection of the statement
         */
        private Connection associatedConnectionOf(PreparedStatement statement) {
            try {
                Connection reported = statement.getConnection();
                if (reported == currentPhysicalConnection()) {
                    return reported;
                }
                if (isPoolHandle(reported)) {
                    // A wrapper beneath the cache intercepted the call (the
                    // StatementDecoratorInterceptor answers it with the
                    // handle of the borrow, among others). The handle is
                    // this pooled connection's own, and the current borrow
                    // - on whose session this prepare ran - sits on its
                    // current physical connection: that session is the
                    // statement's binding.
                    return currentPhysicalConnection();
                }
                return reported;
            } catch (SQLException x) {
                return currentPhysicalConnection();
            }
        }

        /**
         * Returns the physical connection this statement was prepared on.
         *
         * @return the connection associated with the statement
         */
        public Connection getAssociatedConnection() {
            return associatedConnection;
        }

        @Override
        public void closeInvoked() throws SQLException {
            // Should we cache it. Read the counter once, as getCacheSize()
            // does: a disposal (reset(null, null) from a finalize or from an
            // abandoned or closed session) nulls the field, and a handle the
            // application kept through that window must still close through
            // the normal path - the sibling entry points answer a disposed
            // cache quietly; this first read has to do the same rather than
            // dereference the field.
            AtomicInteger size = cacheSize;
            boolean shouldClose = true;
            if (size != null && size.get() < maxCacheSize) {
                // Cache a proxy so that we don't reuse the facade. The
                // binding to the physical session made at the prepare is
                // carried over to the parked proxy rather than the delegate
                // asked again: a wrapper beneath this cache would intercept
                // that second question too, while the prepare-time binding
                // still names the session the statement was prepared on for
                // the session check in cacheStatement.
                CachedStatement proxy = new CachedStatement(getDelegate(), getSql(), associatedConnection);
                proxy.setCacheKey(getCacheKey());
                try {
                    // Only cache if statement has been marked as poolable and not close on completion
                    if (poolable && !getDelegate().isCloseOnCompletion()) {
                        // Clear Resultset
                        ResultSet result = getDelegate().getResultSet();
                        if (result != null && !result.isClosed()) {
                            result.close();
                        }
                        // Clear parameter
                        getDelegate().clearParameters();
                        // Clear batch
                        getDelegate().clearBatch();
                        // Clear warnings
                        getDelegate().clearWarnings();
                        // Reset the timeout
                        getDelegate().setQueryTimeout(0);
                        // Reset fetch direction
                        getDelegate().setFetchDirection(ResultSet.FETCH_FORWARD);
                        // Reset max field size
                        getDelegate().setMaxFieldSize(0);
                        // Reset max rows
                        getDelegate().setMaxRows(0);
                        // Reset fetchsize
                        getDelegate().setFetchSize(0);
                        // Reset the escape processing
                        getDelegate().setEscapeProcessing(true);
                        // Create a new facade
                        Object actualProxy = getConstructor().newInstance(new Object[] { proxy });
                        proxy.setActualProxy(actualProxy);
                        proxy.setConnection(getConnection());
                        proxy.setConstructor(getConstructor());
                        if (cacheStatement(proxy)) {
                            shouldClose = false;
                        }
                    }
                } catch (RuntimeException | ReflectiveOperationException | SQLException x) {
                    // The throw always precedes the park attempt in the try
                    // above, so this proxy is not in the cache; the
                    // identity-aware removal is a no-op for it and leaves any
                    // other statement parked under the same key alone.
                    removeStatement(proxy);
                    if (log.isDebugEnabled()) {
                        log.debug("Unable to recycle statement: " + getSql(), x);
                    }
                }
            }
            if (shouldClose) {
                // The physical close belongs to the superclass, and so does the
                // retirement of this handle: a driver that refuses the close
                // throws from there and the application hears about it. The
                // statement never reached the cache in this branch, so nothing
                // here has to be unwound for it.
                super.closeInvoked();
            }
            closed = true;
            delegate = null;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = super.invoke(proxy, method, args);
            if (compare(SET_POOLABLE, method)) {
                poolable = ((Boolean) args[0]).booleanValue();
            }
            return result;
        }

        /**
         * Forces immediate closure of this cached statement. No application waits on this close - the
         * pool is dropping the statement, from the purge that runs when a session is disconnected or
         * from a cache hit on an entry of a replaced session - so a driver that refuses it is logged
         * rather than thrown: the caller walks the cache, and one statement that will not close must
         * not keep the entries after it from being dropped as well.
         */
        public void forceClose() {
            removeStatement(this);
            try {
                super.closeInvoked();
            } catch (SQLException x) {
                log.warn(MessageFactory.get("statementCache.unableToCloseDiscardedStatement", getSql()), x);
            }
        }

        /**
         * Returns the cache key for this statement.
         *
         * @return the cache key
         */
        public CacheKey getCacheKey() {
            return key;
        }

        /**
         * Sets the cache key for this statement.
         *
         * @param cacheKey the cache key
         */
        public void setCacheKey(CacheKey cacheKey) {
            key = cacheKey;
        }

    }

    /**
     * Creates a cache key from the given method and arguments.
     *
     * @param method the SQL method
     * @param args   the method arguments
     *
     * @return the cache key
     */
    protected CacheKey createCacheKey(Method method, Object[] args) {
        return createCacheKey(method.getName(), args);
    }

    /**
     * Creates a cache key from the given method name and arguments.
     *
     * @param methodName the SQL method name
     * @param args       the method arguments
     *
     * @return the cache key, or null if the method name doesn't match a cached type
     */
    protected CacheKey createCacheKey(String methodName, Object[] args) {
        CacheKey key = null;
        if (compare(PREPARE_STATEMENT, methodName)) {
            key = new CacheKey(PREPARE_STATEMENT, args);
        } else if (compare(PREPARE_CALL, methodName)) {
            key = new CacheKey(PREPARE_CALL, args);
        }
        return key;
    }


    private static final class CacheKey {
        private final String stmtType;
        private final Object[] args;

        /*
         * The key must be an immutable snapshot of the call it was made for.
         * It is built from the live argument array of the prepare call - the
         * outer Object[] allocated by the connection proxy for that call,
         * whose int[]/String[] elements (prepareStatement(String, int[]) and
         * prepareStatement(String, String[])) are the application's own
         * arrays - and the key built at creation time is the one that parks
         * the statement, living as long as the physical session. Storing
         * those arrays by reference would let a caller that reuses and
         * mutates one of them after the prepare move the parked entry's hash
         * bucket under the map, since hashCode() below re-computes
         * Arrays.deepHashCode over the live data: the entry becomes
         * unreachable to lookups while its statement stays open on the
         * session and its slot stays counted, each further drifted key
         * parking another such entry until the cache is full. So snapshot
         * everything the hash and the comparison descend into: the outer
         * array - per-call on the proxy path, but caller-owned through the
         * createCacheKey entry point - and every array element, one level
         * deep, which is as far as deepHashCode/deepEquals reach for the
         * arrays the prepare methods can carry, whose own elements are
         * immutable Strings or primitives. A null element is no array,
         * instanceof answers false for it, and it is stored and hashed as
         * it stands.
         */
        private CacheKey(String type, Object[] methodArgs) {
            stmtType = type;
            args = new Object[methodArgs.length];
            for (int i = 0; i < methodArgs.length; i++) {
                Object arg = methodArgs[i];
                if (arg instanceof int[]) {
                    arg = ((int[]) arg).clone();
                } else if (arg instanceof Object[]) {
                    arg = ((Object[]) arg).clone();
                }
                args[i] = arg;
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.deepHashCode(args);
            result = prime * result + ((stmtType == null) ? 0 : stmtType.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            CacheKey other = (CacheKey) obj;
            if (!Arrays.deepEquals(args, other.args)) {
                return false;
            }
            if (stmtType == null) {
                if (other.stmtType != null) {
                    return false;
                }
            } else if (!stmtType.equals(other.stmtType)) {
                return false;
            }
            return true;
        }
    }
}

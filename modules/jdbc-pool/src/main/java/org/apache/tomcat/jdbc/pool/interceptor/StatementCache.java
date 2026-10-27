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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;

/**
 * Interceptor that caches {@code PreparedStatement} and/or
 * {@code CallableStatement} instances on a connection.
 * <p>
 * A cache hit is answered by this interceptor alone: the rest of the
 * interceptor chain is not invoked for it, and a statement recycled into
 * the cache is reset to driver defaults (its query timeout goes back to
 * zero). Interceptors listed <em>after</em> this one in
 * {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setJdbcInterceptors(String)
 * jdbcInterceptors} therefore do not see reused statements - relevant to
 * any interceptor whose {@code createStatement} hook performs an action
 * rather than only wrapping the statement: {@link QueryTimeoutInterceptor}
 * must be listed before this one to keep its timeout on cached statements,
 * while wrapping-only interceptors are unaffected because their wrapper is
 * carried inside the cached proxy.
 */
public class StatementCache extends StatementDecoratorInterceptor implements StatementCacheMBean {
    private static final Log log = LogFactory.getLog(StatementCache.class);
    /**
     * All statement types (prepared and callable).
     */
    protected static final String[] ALL_TYPES = new String[] {PREPARE_STATEMENT,PREPARE_CALL};
    /**
     * Callable statement type only.
     */
    protected static final String[] CALLABLE_TYPE = new String[] {PREPARE_CALL};
    /**
     * Prepared statement type only.
     */
    protected static final String[] PREPARED_TYPE = new String[] {PREPARE_STATEMENT};
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

    /*begin properties for the statement cache*/
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
        if (p!=null) {
            cachePrepared = p.getValueAsBoolean(cachePrepared);
        }
        p = properties.get("callable");
        if (p!=null) {
            cacheCallable = p.getValueAsBoolean(cacheCallable);
        }
        p = properties.get("max");
        if (p!=null) {
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
    /*end properties for the statement cache*/

    /*begin the cache size*/
    private static ConcurrentHashMap<ConnectionPool,AtomicInteger> cacheSizeMap =
            new ConcurrentHashMap<>();

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
    /*end the cache size*/

    /*begin the actual statement cache*/
    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        super.reset(parent, con);
        if (parent==null) {
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
                ConcurrentHashMap<CacheKey,CachedStatement> cache =
                        new ConcurrentHashMap<>();
                pcon.getAttributes().put(STATEMENT_CACHE_ATTR,cache);
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
        ConcurrentHashMap<CacheKey,CachedStatement> statements =
                (ConcurrentHashMap<CacheKey,CachedStatement>)con.getAttributes().get(STATEMENT_CACHE_ATTR);

        if (statements!=null) {
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
        if (st==null) {
            return;
        }
        st.forceClose();
    }

    @Override
    protected Object createDecorator(Object proxy, Method method, Object[] args,
                                     Object statement, Constructor<?> constructor, String sql)
        throws InstantiationException, IllegalAccessException, InvocationTargetException {
        boolean process = process(this.types, method, false);
        if (process) {
            Object result = null;
            CachedStatement statementProxy = new CachedStatement((PreparedStatement)statement,sql);
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
        if (process && args.length>0 && args[0] instanceof String) {
            CachedStatement statement = isCached(method, args);
            if (statement!=null) {
                //remove it from the cache since it is used
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
            return super.invoke(proxy,method,args);
        }
    }

    /**
     * Checks if a statement for the given method and arguments is currently cached.
     *
     * @param method the SQL method (prepareStatement or prepareCall)
     * @param args the method arguments
     * @return the cached statement, or null if not cached
     */
    public CachedStatement isCached(Method method, Object[] args) {
        ConcurrentHashMap<CacheKey,CachedStatement> cache = getCache();
        if (cache == null) {
            return null;
        }
        CacheKey key = createCacheKey(method, args);
        if (key == null) {
            throw new IllegalArgumentException("Null key");
        }
        return cache.get(key);
    }

    /**
     * Caches the given statement proxy if there is room in the cache.
     *
     * @param proxy the statement proxy to cache
     * @return true if the statement was cached, false otherwise
     */
    public boolean cacheStatement(CachedStatement proxy) {
        ConcurrentHashMap<CacheKey,CachedStatement> cache = getCache();
        if (cache == null) {
            return false;
        }
        if (proxy.getCacheKey()==null) {
            return false;
        } else if (proxy.getAssociatedConnection() != currentPhysicalConnection()) {
            // The physical connection the statement was prepared on has
            // been replaced (typically a close that raced a
            // disconnect/reconnect, landing after the disconnected() purge
            // had already run). The statement belongs to a dead connection
            // and must not be parked in the cache of the new one.
            return false;
        } else if (cache.containsKey(proxy.getCacheKey())) {
            return false;
        } else if (cacheSize.get()>=maxCacheSize) {
            return false;
        } else if (cacheSize.incrementAndGet()>maxCacheSize) {
            cacheSize.decrementAndGet();
            return false;
        } else {
            //cache the statement
            cache.put(proxy.getCacheKey(), proxy);
            return true;
        }
    }

    /**
     * Removes the given statement from the cache.
     *
     * @param proxy the statement proxy to remove
     * @return true if the statement was removed, false otherwise
     */
    public boolean removeStatement(CachedStatement proxy) {
        ConcurrentHashMap<CacheKey,CachedStatement> cache = getCache();
        if (cache == null) {
            return false;
        }
        if (cache.remove(proxy.getCacheKey()) != null) {
            cacheSize.decrementAndGet();
            return true;
        } else {
            return false;
        }
    }
    /*end the actual statement cache*/

    /**
     * Returns the physical connection behind the pooled connection this
     * interceptor is bound to, or {@code null} if there is none (yet).
     *
     * @return the current physical connection, or null
     */
    private Connection currentPhysicalConnection() {
        PooledConnection pCon = this.pcon;
        return pCon == null ? null : pCon.getConnection();
    }

    /**
     * Returns the statement cache for the current pooled connection.
     *
     * @return the cache map, or null if the connection has been closed
     */
    protected ConcurrentHashMap<CacheKey,CachedStatement> getCache() {
        PooledConnection pCon = this.pcon;
        if (pCon == null) {
            if (log.isWarnEnabled()) {
                log.warn("Connection has already been closed or abandoned");
            }
            return null;
        }
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<CacheKey,CachedStatement> cache =
                (ConcurrentHashMap<CacheKey,CachedStatement>)pCon.getAttributes().get(STATEMENT_CACHE_ATTR);
        return cache;
    }

    @Override
    public int getCacheSizePerConnection() {
        ConcurrentHashMap<CacheKey,CachedStatement> cache = getCache();
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
         * The physical connection this statement was prepared on. Cached
         * statements are bound to the connection they were prepared on; if
         * the pooled connection is reconnected, the entries become stale
         * and must neither be cached nor handed out.
         */
        private final Connection associatedConnection;
        /**
         * Poolable hint set by the application. Tracked here as some drivers
         * (e.g. H2) always return false from {@code isPoolable()}.
         */
        private boolean poolable = true;
        /**
         * Constructs a CachedStatement.
         * @param parent the parent prepared statement
         * @param sql the SQL text
         */
        public CachedStatement(PreparedStatement parent, String sql) {
            super(parent, sql);
            associatedConnection = associatedConnectionOf(parent);
        }

        /**
         * Resolves the physical connection a statement belongs to. The
         * connection the driver reports for the statement itself is
         * authoritative; only when the driver will not answer does the
         * pool's own record of the current connection serve as the binding.
         *
         * @param statement the statement whose associated connection to
         *                  determine
         * @return the physical connection of the statement
         */
        private Connection associatedConnectionOf(PreparedStatement statement) {
            try {
                return statement.getConnection();
            } catch (SQLException x) {
                return currentPhysicalConnection();
            }
        }

        /**
         * Returns the physical connection this statement was prepared on.
         * @return the connection associated with the statement
         */
        public Connection getAssociatedConnection() {
            return associatedConnection;
        }

        @Override
        public void closeInvoked() {
            //should we cache it
            boolean shouldClose = true;
            if (cacheSize.get() < maxCacheSize) {
                //cache a proxy so that we don't reuse the facade
                CachedStatement proxy = new CachedStatement(getDelegate(),getSql());
                proxy.setCacheKey(getCacheKey());
                try {
                    // Only cache if statement has been marked as poolable and not close on completion
                    if (poolable && !getDelegate().isCloseOnCompletion()) {
                        // clear Resultset
                        ResultSet result = getDelegate().getResultSet();
                        if (result != null && !result.isClosed()) {
                            result.close();
                        }
                        // clear parameter
                        getDelegate().clearParameters();
                        // clear batch
                        getDelegate().clearBatch();
                        // clear warnings
                        getDelegate().clearWarnings();
                        // reset the timeout
                        getDelegate().setQueryTimeout(0);
                        // reset fetch direction
                        getDelegate().setFetchDirection(ResultSet.FETCH_FORWARD);
                        // reset max field size
                        getDelegate().setMaxFieldSize(0);
                        // reset max rows
                        getDelegate().setMaxRows(0);
                        // reset fetchsize
                        getDelegate().setFetchSize(0);
                        // reset the escape processing
                        getDelegate().setEscapeProcessing(true);
                        //create a new facade
                        Object actualProxy = getConstructor().newInstance(new Object[] { proxy });
                        proxy.setActualProxy(actualProxy);
                        proxy.setConnection(getConnection());
                        proxy.setConstructor(getConstructor());
                        if (cacheStatement(proxy)) {
                            shouldClose = false;
                        }
                    }
                } catch (RuntimeException | ReflectiveOperationException | SQLException x) {
                    removeStatement(proxy);
                }
            }
            if (shouldClose) {
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
         * Forces immediate closure of this cached statement.
         */
        public void forceClose() {
            removeStatement(this);
            super.closeInvoked();
        }

        /**
         * Returns the cache key for this statement.
         * @return the cache key
         */
        public CacheKey getCacheKey() {
            return key;
        }

        /**
         * Sets the cache key for this statement.
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
     * @param args the method arguments
     * @return the cache key
     */
    protected CacheKey createCacheKey(Method method, Object[] args) {
        return createCacheKey(method.getName(), args);
    }

    /**
     * Creates a cache key from the given method name and arguments.
     *
     * @param methodName the SQL method name
     * @param args the method arguments
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
        private CacheKey(String type, Object[] methodArgs) {
            stmtType = type;
            args = methodArgs;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.deepHashCode(args);
            result = prime * result
                    + ((stmtType == null) ? 0 : stmtType.hashCode());
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

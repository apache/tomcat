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
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;

public class StatementCache extends StatementDecoratorInterceptor {
    protected static final String[] ALL_TYPES = new String[] {PREPARE_STATEMENT,PREPARE_CALL}; 
    protected static final String[] CALLABLE_TYPE = new String[] {PREPARE_CALL}; 
    protected static final String[] PREPARED_TYPE = new String[] {PREPARE_STATEMENT}; 
    protected static final String[] NO_TYPE = new String[] {};

    /*begin properties for the statement cache*/
    private boolean cachePrepared = true;
    private boolean cacheCallable = false;
    private int maxCacheSize = 50;
    private ConnectionPool parent;
    private PooledConnection pcon;
    private String[] types;
    
    
    public void setProperties(Map<String, InterceptorProperty> properties) {
        super.setProperties(properties);
        InterceptorProperty p = properties.get("prepared");
        if (p!=null) cachePrepared = p.getValueAsBoolean(cachePrepared);
        p = properties.get("callable");
        if (p!=null) cacheCallable = p.getValueAsBoolean(cacheCallable);
        p = properties.get("max");
        if (p!=null) maxCacheSize = p.getValueAsInt(maxCacheSize);
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
        new ConcurrentHashMap<ConnectionPool,AtomicInteger>();
    
    private AtomicInteger cacheSize;

    public void poolStarted(ConnectionPool pool) {
        cacheSizeMap.putIfAbsent(pool, new AtomicInteger(0));
        super.poolStarted(pool);
    }
    
    public void poolClosed(ConnectionPool pool) {
        cacheSizeMap.remove(pool);
        super.poolClosed(pool);
    }
    /*end the cache size*/
    
    /*begin the actual statement cache*/
    
    private static ConcurrentHashMap<PooledConnection, ConcurrentHashMap<String,StatementProxy>> statementCache =
        new ConcurrentHashMap<PooledConnection, ConcurrentHashMap<String,StatementProxy>>();
    
    
    public void reset(ConnectionPool parent, PooledConnection con) {
        super.reset(parent, con);
        if (parent==null) {
            cacheSize = null;
            this.parent = null;
            this.pcon = null;
        } else {
            cacheSize = cacheSizeMap.get(parent);
            this.parent = parent;
            this.pcon = con;
        }
    }

    public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
        ConcurrentHashMap<String,StatementProxy> statements = statementCache.get(con);
        if (statements!=null) {
            for (Map.Entry<String, StatementProxy> p : statements.entrySet()) {
                closeStatement(p.getValue());
            }
            statements.clear();
        }
        super.disconnected(parent, con, finalizing);
    }
    
    public void closeStatement(StatementProxy st) {
        try {
            if (st==null) return;
            if (((PreparedStatement)st).isClosed()) return;
            cacheSize.decrementAndGet();
            st.forceClose();
        }catch (SQLException sqe) {
            //log debug message
        }
    }
    
    
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (compare(CLOSE_VAL,method)) {
            return super.invoke(proxy, method, args);
        } else {
            boolean process = false;
            process = process(this.types, method, process);
            
            if (process) {
                //check the cache
                //if (isCached) {
                    
                //} else {
                    return super.invoke(proxy, method, args);
                //}
            } else {
                return super.invoke(proxy,method,args);
            }
        }
    }
    
    public boolean isCached(String sql) {
        ConcurrentHashMap<String,StatementProxy> cache = statementCache.get(pcon);
        return cache.containsKey(sql);
    }
    
    public boolean cacheStatement(StatementProxy proxy) {
        ConcurrentHashMap<String,StatementProxy> cache = statementCache.get(pcon); 
        if (proxy.getSql()==null) {
            return false;
        } else if (cache.containsKey(proxy.getSql())) {
            cache.put(proxy.getSql(), proxy);
            return true;
        } else if (cacheSize.get()>=maxCacheSize) {
            return false;
        } else if (cacheSize.incrementAndGet()>maxCacheSize) {
            cacheSize.decrementAndGet();
            return false;
        } else {
            //cache the statement
            return true;
        }
    }
    /*end the actual statement cache*/

    
    protected class StatementProxy extends StatementDecoratorInterceptor.StatementProxy {
        boolean cached = false;
        public StatementProxy(Object parent, String sql) {
            super(parent, sql);
            cached = cacheStatement(this);
        }

        public void closeInvoked() {
            super.closedInvoked();
            if (cached) {
                //cache a proxy so that we don't reuse the facade
                StatementProxy proxy = new StatementProxy(getDelegate(),getSql());
                proxy.setActualProxy(getActualProxy());
                proxy.setConnection(getConnection());
            }
           
        }
        
        public void forceClose() throws SQLException {
            super.closedInvoked();
            ((Statement)getDelegate()).close();
        }
        
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // get the name of the method for comparison
            final String name = method.getName();
            // was close invoked?
            boolean close = compare(JdbcInterceptor.CLOSE_VAL, name);
            // allow close to be called multiple times
            if (close && closed)
                return null;
            // are we calling isClosed?
            if (compare(JdbcInterceptor.ISCLOSED_VAL, name))
                return Boolean.valueOf(closed);
            // if we are calling anything else, bail out
            if (closed)
                throw new SQLException("Statement closed.");
            if (name.equals("getConnection")){
                return getConnection();
            }
            boolean process = isExecuteQuery(method);
            // check to see if we are about to execute a query
            // if we are executing, get the current time
            Object result = null;
            try {
                if (cached && close) {
                    //dont invoke actual close
                } else {
                    // execute the query
                    result = method.invoke(delegate, args);
                }
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException) {
                    InvocationTargetException it = (InvocationTargetException) t;
                    throw it.getCause() != null ? it.getCause() : it;
                } else {
                    throw t;
                }
            }
            // perform close cleanup
            if (close) {
                closeInvoked();
            }
            if (process){
                Constructor<?> cons = getResultSetConstructor();
                result = cons.newInstance(new Object[]{new ResultSetProxy(getActualProxy(), result)});
            }
            return result;
        }
        
    }
    
}



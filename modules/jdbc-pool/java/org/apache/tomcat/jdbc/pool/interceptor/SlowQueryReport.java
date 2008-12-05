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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * @author Filip Hanik
 * @version 1.0
 */
public class SlowQueryReport extends AbstractCreateStatementInterceptor {
    protected final String[] statements = {"createStatement","prepareStatement","prepareCall"};
    protected final String[] executes = {"execute","executeQuery","executeUpdate","executeBatch"};

    protected static IdentityHashMap<ConnectionPool,HashMap<String,QueryStats>> perPoolStats = 
        new IdentityHashMap<ConnectionPool,HashMap<String,QueryStats>>();
    
    protected HashMap<String,QueryStats> queries = null;
    
    protected long threshold = 100; //don't report queries less than this
    protected int  maxQueries= 1000; //don't store more than this amount of queries

    
    
    public SlowQueryReport() {
        super();
    }

    public long getThreshold() {
        return threshold;
    }

    public void setThreshold(long threshold) {
        this.threshold = threshold;
    }

    @Override
    public void closeInvoked() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement) {
        // TODO Auto-generated method stub
        String sql = null;
        if (method.getName().startsWith("prepare")) {
            sql = (args.length>0 && (args[0] instanceof String))?(String)args[0]:null;
        }
        return new StatementProxy(statement,sql);
    }

    protected boolean process(final String[] names, Method method, boolean process) {
        for (int i=0; (!process) && i<names.length; i++) {
            process = compare(method.getName(),names[i]);
        }
        return process;
    }

    protected class QueryStats {
        private final String query;
        private int nrOfInvocations;
        private long maxInvocationTime;
        private long maxInvocationDate;
        private long minInvocationTime;
        private long minInvocationDate;
        private long totalInvocationTime;
        
        public QueryStats(String query) {
            this.query = query;
        }
        
        public void add(long invocationTime) {
            long now = -1;
            //not thread safe, but don't sacrifice performance for this kind of stuff
            maxInvocationTime = Math.max(invocationTime, maxInvocationTime);
            if (maxInvocationTime == invocationTime) {
                now = System.currentTimeMillis();
                maxInvocationDate = now;
            }
            minInvocationTime = Math.min(invocationTime, minInvocationTime);
            if (minInvocationTime==invocationTime) {
                now = (now==-1)?System.currentTimeMillis():now;
                minInvocationDate = now;
            }
            nrOfInvocations++;
            totalInvocationTime+=invocationTime;
        }
        
        public String getQuery() {
            return query;
        }

        public int getNrOfInvocations() {
            return nrOfInvocations;
        }

        public long getMaxInvocationTime() {
            return maxInvocationTime;
        }

        public long getMaxInvocationDate() {
            return maxInvocationDate;
        }

        public long getMinInvocationTime() {
            return minInvocationTime;
        }

        public long getMinInvocationDate() {
            return minInvocationDate;
        }

        public long getTotalInvocationTime() {
            return totalInvocationTime;
        }

        public int hashCode() {
            return query.hashCode();
        }
        
        public boolean equals(Object other) {
            if (other instanceof QueryStats) {
                QueryStats qs = (QueryStats)other;
                return SlowQueryReport.this.compare(qs.query,this.query);
            } 
            return false;
        }
    }
    
    protected class StatementProxy implements InvocationHandler {
        protected boolean closed = false;
        protected Object delegate;
        protected final String query;
        public StatementProxy(Object parent, String query) {
            this.delegate = parent;
            this.query = query;
        }
        
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            final String name = method.getName();
            boolean close = compare(JdbcInterceptor.CLOSE_VAL,name);
            if (close && closed) return null; //allow close to be called multiple times
            if (closed) throw new SQLException("Statement closed.");
            boolean process = false;
            process = process(executes, method, process);
            long start = (process)?System.currentTimeMillis():0;
            //execute the query
            Object result =  method.invoke(delegate,args);
            long delta = (process)?(System.currentTimeMillis()-start):0;
            if (delta>threshold) {
                String sql = null;//TODO
                QueryStats qs = SlowQueryReport.this.queries.get(sql);
                if (qs == null) {
                    qs = new QueryStats(sql);
                    SlowQueryReport.this.queries.put((String)sql,qs);
                }
                qs.add(delta);
                return qs;
            }
            if (close) {
                closed=true;
                delegate = null;
            }
            return result;
        }
    }

    public void reset(ConnectionPool parent, PooledConnection con) {
        if (queries==null && SlowQueryReport.perPoolStats.get(parent)==null) {
            queries = new LinkedHashMap<String,QueryStats>() {
                @Override
                protected boolean removeEldestEntry(Entry<String, QueryStats> eldest) {
                    return size()>maxQueries;
                }

            };
        }
    }
}

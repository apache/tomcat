/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.jdbc.pool.interceptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.management.ObjectName;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.jmx.JmxUtil;

/**
 * Class that resets the abandoned timer on any activity on the
 * Connection, on any query execution and on any read of a
 * {@link java.sql.ResultSet} returned by a statement obtained from the
 * pooled connection, whether the operation completed successfully or
 * failed. Iterating a large result set therefore keeps the connection
 * alive through the fetch phase, not only until the query returns.
 * Each reset happens when an individual call completes, so a single call
 * that itself blocks on the database for longer than
 * {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setRemoveAbandonedTimeout(int)}
 * still gets the connection abandoned. When the
 * {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setUseStatementFacade(boolean)
 * statement facade} is switched off, a result set handle that outlives
 * its borrow keeps refreshing the abandoned timer of whoever holds that
 * physical connection next; with the facade enabled, as by default, a
 * returned borrow is sealed off before that can happen.
 * This interceptor is useful for when you have a {@link org.apache.tomcat.jdbc.pool.PoolConfiguration#setRemoveAbandonedTimeout(int)}
 * that is fairly low, and you want to reset the abandoned time each time any operation on the connection is performed
 * This is useful for batch processing programs that use connections for extensive amount of times.
 *
 */
public class ResetAbandonedTimer extends AbstractQueryReport implements ResetAbandonedTimerMBean {

    private static final Log log = LogFactory.getLog(ResetAbandonedTimer.class);

    private PooledConnection pcon;

    private ObjectName oname = null;

    /**
     * Default constructor.
     */
    public ResetAbandonedTimer() {
    }

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        super.reset(parent, con);
        if (con == null) {
            this.pcon = null;
            if (oname != null) {
                JmxUtil.unregisterJmx(oname);
                oname = null;
            }
        } else {
            this.pcon = con;
            if (oname == null) {
                String keyprop = ",JdbcInterceptor=" + getClass().getSimpleName();
                oname = JmxUtil.registerJmx(pcon.getObjectName(), keyprop, this);
            }
        }
    }

    @Override
    public boolean resetTimer() {
        boolean result = false;
        if (pcon != null) {
            pcon.setTimestamp(System.currentTimeMillis());
            result = true;
        }
        return result;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            return super.invoke(proxy, method, args);
        } finally {
            //the operation was attempted on the connection, which counts
            //as activity even when it failed
            resetTimer();
        }
    }

    /**
     * Wraps the query reporting statement proxy so that the result sets
     * handed to the application reset the abandoned timer while they are
     * read. Statements that this interceptor does not proxy are passed
     * through unchanged.
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        Object result = super.createStatement(proxy, method, args, statement, time);
        if (result == statement) {
            //not one of the statement types we proxy, or the proxy
            //creation above failed - nothing to wrap
            return result;
        }
        try {
            String name = method.getName();
            Class<?> type = Statement.class;
            int index = CREATE_STATEMENT_IDX;
            if (compare(PREPARE_STATEMENT, name)) {
                type = PreparedStatement.class;
                index = PREPARE_STATEMENT_IDX;
            } else if (compare(PREPARE_CALL, name)) {
                type = CallableStatement.class;
                index = PREPARE_CALL_IDX;
            }
            return getConstructor(index, type).newInstance(
                    new Object[] { new TimerStatementProxy(result) });
        } catch (Exception x) {
            log.warn("Unable to create timer statement proxy for "
                    + getClass().getName() + ".", x);
        }
        return result;
    }

    @Override
    protected void prepareCall(String query, long time) {
        resetTimer();
    }

    @Override
    protected void prepareStatement(String sql, long time) {
        resetTimer();

    }

    @Override
    public void closeInvoked() {
        resetTimer();
    }

    @Override
    protected String reportFailedQuery(String query, Object[] args, String name,long start, Throwable t) {
        resetTimer();
        return super.reportFailedQuery(query, args, name, start, t);
    }

    @Override
    protected String reportQuery(String query, Object[] args, String name,long start, long delta) {
        resetTimer();
        return super.reportQuery(query, args, name, start, delta);
    }

    @Override
    protected String reportSlowQuery(String query, Object[] args, String name,long start, long delta) {
        resetTimer();
        return super.reportSlowQuery(query, args, name, start, delta);
    }

    /**
     * Proxy for a statement that turns the result sets the statement hands
     * out into {@link TimerResultSetProxy} instances. Every other call is
     * passed straight through to the wrapped statement.
     */
    private final class TimerStatementProxy implements InvocationHandler {
        /**
         * The statement proxy created by {@code AbstractQueryReport}.
         */
        private final Object delegate;

        TimerStatementProxy(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            //Handle identity on this proxy, like the statement facade
            //does, rather than on the object below it: the application
            //must be able to rely on its own handles
            if (compare(TOSTRING_VAL, method)) {
                return toString();
            }
            if (compare(EQUALS_VAL, method)) {
                if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
                    return Boolean.FALSE;
                }
                return Boolean.valueOf(this.equals(Proxy.getInvocationHandler(args[0])));
            }
            if (compare(HASHCODE_VAL, method)) {
                return Integer.valueOf(this.hashCode());
            }
            Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (InvocationTargetException x) {
                Throwable cause = x.getCause();
                if (cause == null) {
                    throw x;
                }
                throw cause;
            }
            //Wrap the same three result set producing calls that the
            //statement facade wraps: executeQuery, getResultSet and
            //getGeneratedKeys
            if (result instanceof ResultSet) {
                String name = method.getName();
                if (compare(EXECUTE_QUERY, name) || compare(GET_RESULTSET, name)
                        || compare(GET_GENERATED_KEYS, name)) {
                    try {
                        return getConstructor(RESULTSET_IDX, ResultSet.class).newInstance(
                                new Object[] { new TimerResultSetProxy(result, proxy) });
                    } catch (Exception x) {
                        //the application just does not get the timer reset
                        //while reading this result set
                        log.warn("Unable to create timer result set proxy for "
                                + ResetAbandonedTimer.this.getClass().getName() + ".", x);
                    }
                }
            }
            return result;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return ResetAbandonedTimer.this.getClass().getName()
                    + "[Proxy=" + hashCode() + "; Delegate=" + delegate + ']';
        }
    }

    /**
     * Proxy for a result set that resets the abandoned timer on every read,
     * so that an application iterating a result set - or doing its per-row
     * work between the reads - counts as active and is not abandoned
     * mid-iteration.
     */
    private final class TimerResultSetProxy implements InvocationHandler {
        /**
         * The result set being read.
         */
        private final Object delegate;
        /**
         * The statement handle in use by this interceptor, returned by
         * {@link java.sql.ResultSet#getStatement()}. It keeps the timer
         * reset like the handle the application holds, but it is not
         * necessarily that same outermost handle: the statement facade
         * substitutes its own handle from outside this interceptor, and
         * any interceptor configured around this one may too.
         */
        private final Object statement;

        TimerResultSetProxy(Object delegate, Object statement) {
            this.delegate = delegate;
            this.statement = statement;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            //Handle identity on this proxy, like the statement facade
            //does, rather than on the object below it: the application
            //must be able to rely on its own handles
            if (compare(TOSTRING_VAL, method)) {
                return toString();
            }
            if (compare(EQUALS_VAL, method)) {
                if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
                    return Boolean.FALSE;
                }
                return Boolean.valueOf(this.equals(Proxy.getInvocationHandler(args[0])));
            }
            if (compare(HASHCODE_VAL, method)) {
                return Integer.valueOf(this.hashCode());
            }
            if (compare(GET_STATEMENT, method)) {
                return statement;
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException x) {
                Throwable cause = x.getCause();
                if (cause == null) {
                    throw x;
                }
                throw cause;
            } finally {
                //reading the result set is activity on the connection,
                //even when the read failed
                resetTimer();
            }
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public String toString() {
            return ResetAbandonedTimer.this.getClass().getName()
                    + "[Proxy=" + hashCode() + "; Delegate=" + delegate + ']';
        }
    }
}

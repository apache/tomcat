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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
/**
 * Abstract class that wraps statements and intercepts query executions.
 *
 */
public abstract class AbstractQueryReport extends AbstractCreateStatementInterceptor {
    //logger
    private static final Log log = LogFactory.getLog(AbstractQueryReport.class);

    /**
     * The threshold in milliseconds. If the query is faster than this, we don't measure it
     */
    protected long threshold = 1000; //don't report queries less than this

    /**
     * Constructs a new {@code AbstractQueryReport} with the default threshold.
     */
    public AbstractQueryReport() {
        super();
    }

    /**
     * Invoked when prepareStatement has been called and completed.
     * @param sql - the string used to prepare the statement with
     * @param time - the time it took to invoke prepare
     */
    protected abstract void prepareStatement(String sql, long time);

    /**
     * Invoked when prepareCall has been called and completed.
     * @param query - the string used to prepare the statement with
     * @param time - the time it took to invoke prepare
     */
    protected abstract void prepareCall(String query, long time);

    /**
     * Extracts the SQL that a statement execution is reported under: the SQL
     * the statement was prepared with, else a {@code String} first argument,
     * else {@code "batch"} for batch executions, which carry no SQL of their
     * own, else {@code null}.
     *
     * @param query the SQL the statement was prepared with, or {@code null}
     * @param args  the arguments of the invoked method
     * @param name  the name of the invoked method
     *
     * @return the SQL, {@code "batch"} or {@code null}
     */
    private String extractQuery(String query, Object[] args, final String name) {
        //extract the query string
        String sql = query;
        if (sql == null && args != null && args.length > 0 && args[0] instanceof String) {
            sql = (String) args[0];
        }
        //if we do batch execution, then we name the query 'batch'
        if (sql == null && (compare(EXECUTE_BATCH, name) || compare(EXECUTE_LARGE_BATCH, name))) {
            sql = "batch";
        }
        return sql;
    }

    /**
     * Invoked when a query execution, a call to an execute method as
     * recognised by {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}, failed.
     * @param query the query that was executed and failed
     * @param args the arguments to the execution
     * @param name the name of the method used to execute {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}
     * @param start the time the query execution started
     * @param t the exception that happened
     * @return - the SQL that was executed or the string &quot;batch&quot; if it was a batch execution,
     *         or {@code null} if the invocation carries no SQL string
     */
    protected String reportFailedQuery(String query, Object[] args, final String name, long start, Throwable t) {
        return extractQuery(query, args, name);
    }

    /**
     * Invoked when a query execution, a call to an execute method as
     * recognised by {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}, succeeded and was within the timing threshold
     * @param query the query that was executed and failed
     * @param args the arguments to the execution
     * @param name the name of the method used to execute {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}
     * @param start the time the query execution started
     * @param delta the time the execution took
     * @return - the SQL that was executed or the string &quot;batch&quot; if it was a batch execution,
     *         or {@code null} if the invocation carries no SQL string
     */
    protected String reportQuery(String query, Object[] args, final String name, long start, long delta) {
        return extractQuery(query, args, name);
    }

    /**
     * Invoked when a query execution, a call to an execute method as
     * recognised by {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}, succeeded and was exceeded the timing threshold
     * @param query the query that was executed and failed
     * @param args the arguments to the execution
     * @param name the name of the method used to execute {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}
     * @param start the time the query execution started
     * @param delta the time the execution took
     * @return - the SQL that was executed or the string &quot;batch&quot; if it was a batch execution,
     *         or {@code null} if the invocation carries no SQL string
     */
    protected String reportSlowQuery(String query, Object[] args, final String name, long start, long delta) {
        return extractQuery(query, args, name);
    }

    /**
     * returns the query measure threshold.
     * This value is in milliseconds. If the query is faster than this threshold than it won't be accounted for
     * @return the threshold in milliseconds
     */
    public long getThreshold() {
        return threshold;
    }

    /**
     * Sets the query measurement threshold. The value is in milliseconds.
     * If the query goes faster than this threshold it will not be recorded.
     * @param threshold set to -1 to record every query. Value is in milliseconds.
     */
    public void setThreshold(long threshold) {
        this.threshold = threshold;
    }

    /**
     * Creates a statement interceptor to monitor query response times
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        try {
            Object result = null;
            String name = method.getName();
            String sql = null;
            Constructor<?> constructor = null;
            if (compare(CREATE_STATEMENT,name)) {
                //createStatement
                constructor = getConstructor(CREATE_STATEMENT_IDX,Statement.class);
            }else if (compare(PREPARE_STATEMENT,name)) {
                //prepareStatement
                sql = (String)args[0];
                constructor = getConstructor(PREPARE_STATEMENT_IDX,PreparedStatement.class);
                if (sql!=null) {
                    prepareStatement(sql, time);
                }
            }else if (compare(PREPARE_CALL,name)) {
                //prepareCall
                sql = (String)args[0];
                constructor = getConstructor(PREPARE_CALL_IDX,CallableStatement.class);
                prepareCall(sql,time);
            }else {
                //do nothing, might be a future unsupported method
                //so we better bail out and let the system continue
                return statement;
            }
            result = constructor.newInstance(new Object[] { new StatementProxy(statement,sql) });
            return result;
        }catch (Exception x) {
            log.warn("Unable to create statement proxy for slow query report.",x);
        }
        return statement;
    }


    /**
     * Class to measure query execute time
     *
     */
    protected class StatementProxy implements InvocationHandler {
        /**
         * Indicates whether the proxy has been closed.
         */
        protected boolean closed = false;
        /**
         * The underlying statement or connection being proxied.
         */
        protected Object delegate;
        /**
         * The SQL query being executed.
         */
        protected final String query;
        /**
         * Constructs a new {@code StatementProxy}.
         *
         * @param parent The underlying statement or connection
         * @param query The SQL query being executed
         */
        public StatementProxy(Object parent, String query) {
            this.delegate = parent;
            this.query = query;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            //get the name of the method for comparison
            final String name = method.getName();
            //was close invoked?
            boolean close = compare(CLOSE_VAL, name);
            //allow close to be called multiple times
            if (close && closed) {
                return null;
            }
            //are we calling isClosed?
            if (compare(ISCLOSED_VAL, name)) {
                return Boolean.valueOf(closed);
            }
            //if we are calling anything else, bail out
            if (closed) {
                throw new SQLException("Statement closed.");
            }
            boolean process = false;
            //check to see if we are about to execute a query
            process = isExecute( method, process);
            //if we are executing, get the current time
            long start = (process)?System.currentTimeMillis():0;
            Object result =  null;
            try {
                //execute the query
                result =  method.invoke(delegate,args);
            }catch (Throwable t) {
                //only report the failure if we were executing a query.
                //For any other method there is no query to report, and
                //reporting it would replace the exception that the
                //application is expecting with a reporting failure
                if (process) {
                    try {
                        reportFailedQuery(query,args,name,start,t);
                    }catch (Throwable reporting) {
                        //reporting must never disturb the application -
                        //the driver's exception is the one being thrown
                        if (log.isWarnEnabled()) {
                            log.warn("Unable to process failed query for "
                                    + AbstractQueryReport.this.getClass().getName(),reporting);
                        }
                    }
                }
                if (t instanceof InvocationTargetException
                        && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            }
            //measure the time
            long delta = (process)?(System.currentTimeMillis()-start):Long.MIN_VALUE;
            //see if we meet the requirements to measure
            if (delta>threshold) {
                try {
                    //report the slow query
                    reportSlowQuery(query, args, name, start, delta);
                }catch (Throwable t) {
                    if (log.isWarnEnabled()) {
                        log.warn("Unable to process slow query for "
                                + AbstractQueryReport.this.getClass().getName(),t);
                    }
                }
            } else if (process) {
                try {
                    reportQuery(query, args, name, start, delta);
                }catch (Throwable t) {
                    //reporting must never disturb the application
                    if (log.isWarnEnabled()) {
                        log.warn("Unable to process query report for "
                                + AbstractQueryReport.this.getClass().getName(),t);
                    }
                }
            }
            //perform close cleanup
            if (close) {
                closed=true;
                delegate = null;
            }
            return result;
        }
    }

}
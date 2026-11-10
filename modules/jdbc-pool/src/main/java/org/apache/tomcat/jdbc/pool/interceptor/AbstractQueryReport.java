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
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Abstract class that wraps statements and intercepts query executions.
 */
public abstract class AbstractQueryReport extends AbstractCreateStatementInterceptor {
    // Logger
    private static final Log log = LogFactory.getLog(AbstractQueryReport.class);

    /**
     * The threshold in milliseconds. A successful execution that takes
     * longer than this is reported as a slow query; one that takes no
     * longer is reported as a measured query. A negative threshold keeps
     * every execution out of the slow path, so every successful execution
     * is reported as measured. Failed executions are reported through
     * {@link #reportFailedQuery} regardless of this threshold.
     */
    protected long threshold = 1000; // Executions slower than this are slow

    /**
     * Constructs a new {@code AbstractQueryReport} with the default threshold.
     */
    public AbstractQueryReport() {
        super();
    }

    /**
     * Invoked when prepareStatement has been called and completed.
     *
     * @param sql  - the string used to prepare the statement with
     * @param time - the time it took to invoke prepare
     */
    protected abstract void prepareStatement(String sql, long time);

    /**
     * Invoked when prepareCall has been called and completed.
     *
     * @param query - the string used to prepare the statement with
     * @param time  - the time it took to invoke prepare
     */
    protected abstract void prepareCall(String query, long time);

    /**
     * Extracts the SQL that a statement execution is reported under: the SQL the statement was prepared with, else a
     * {@code String} first argument, else {@code "batch"} for batch executions, which carry no SQL of their own, else
     * {@code null}.
     *
     * @param query the SQL the statement was prepared with, or {@code null}
     * @param args  the arguments of the invoked method
     * @param name  the name of the invoked method
     *
     * @return the SQL, {@code "batch"} or {@code null}
     */
    private String extractQuery(String query, Object[] args, final String name) {
        String sql = query;
        if (sql == null && args != null && args.length > 0 && args[0] instanceof String) {
            sql = (String) args[0];
        }
        if (sql == null && (compare(EXECUTE_BATCH, name) || compare(EXECUTE_LARGE_BATCH, name))) {
            sql = "batch";
        }
        return sql;
    }

    /**
     * Invoked when a query execution, a call to an execute method as recognised by
     * {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}, failed.
     *
     * @param query the query that was executed and failed
     * @param args  the arguments to the execution
     * @param name  the name of the method used to execute, as recognised by
     *                  {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}
     * @param start the wall clock time the query execution started
     * @param delta the time the execution took before it failed, measured with the monotonic clock
     * @param t     the exception that happened
     *
     * @return - the SQL that was executed or the string &quot;batch&quot; if it was a batch execution, or {@code null}
     *             if the invocation carries no SQL string
     */
    protected String reportFailedQuery(String query, Object[] args, final String name, long start, long delta,
            Throwable t) {
        return extractQuery(query, args, name);
    }

    /**
     * Invoked when a query execution, a call to an execute method as recognised by
     * {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}, succeeded and was within the timing
     * threshold
     *
     * @param query the query that was executed
     * @param args  the arguments to the execution
     * @param name  the name of the method used to execute, as recognised by
     *                  {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}
     * @param start the wall clock time the query execution started
     * @param delta the time the execution took, measured with the monotonic clock
     *
     * @return - the SQL that was executed or the string &quot;batch&quot; if it was a batch execution, or {@code null}
     *             if the invocation carries no SQL string
     */
    protected String reportQuery(String query, Object[] args, final String name, long start, long delta) {
        return extractQuery(query, args, name);
    }

    /**
     * Invoked when a query execution, a call to an execute method as recognised by
     * {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}, succeeded and exceeded the timing
     * threshold
     *
     * @param query the query that was executed
     * @param args  the arguments to the execution
     * @param name  the name of the method used to execute, as recognised by
     *                  {@link AbstractCreateStatementInterceptor#isExecute(Method, boolean)}
     * @param start the wall clock time the query execution started
     * @param delta the time the execution took, measured with the monotonic clock
     *
     * @return - the SQL that was executed or the string &quot;batch&quot; if it was a batch execution, or {@code null}
     *             if the invocation carries no SQL string
     */
    protected String reportSlowQuery(String query, Object[] args, final String name, long start, long delta) {
        return extractQuery(query, args, name);
    }

    /**
     * returns the query measure threshold. This value is in milliseconds.
     * A successful execution that takes longer than this threshold is
     * reported as a slow query; see {@link #setThreshold(long)} for the
     * meaning of a negative value and for the handling of failed
     * executions.
     *
     * @return the threshold in milliseconds
     */
    public long getThreshold() {
        return threshold;
    }

    /**
     * Sets the query measurement threshold. The value is in milliseconds.
     * A successful execution that takes longer than this threshold is
     * reported through {@link #reportSlowQuery}; one that takes no longer
     * is reported through {@link #reportQuery}. A negative threshold
     * records every query: every execution is measured and reported as
     * measured, and no execution is ever reported as slow.
     *
     * @param threshold the threshold in milliseconds; set to a negative
     *             value (conventionally -1) to record every query as
     *             measured without reporting any of them as slow
     */
    public void setThreshold(long threshold) {
        this.threshold = threshold;
    }

    /**
     * Wraps a newly created statement in a proxy that measures its executions, so they can be reported as measured,
     * slow or failed queries.
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        try {
            Object result = null;
            String name = method.getName();
            String sql = null;
            Constructor<?> constructor = null;
            if (compare(CREATE_STATEMENT, name)) {
                constructor = getConstructor(CREATE_STATEMENT_IDX, Statement.class);
            } else if (compare(PREPARE_STATEMENT, name)) {
                sql = (String) args[0];
                constructor = getConstructor(PREPARE_STATEMENT_IDX, PreparedStatement.class);
                if (sql != null) {
                    prepareStatement(sql, time);
                }
            } else if (compare(PREPARE_CALL, name)) {
                sql = (String) args[0];
                constructor = getConstructor(PREPARE_CALL_IDX, CallableStatement.class);
                if (sql != null) {
                    prepareCall(sql, time);
                }
            } else {
                // Do nothing, might be a future unsupported method
                // so we better bail out and let the system continue
                return statement;
            }
            result = constructor.newInstance(new Object[] { new StatementProxy(statement, sql) });
            return result;
        } catch (Exception x) {
            log.warn(MessageFactory.get("abstractQueryReport.unableToCreateStatementProxy"), x);
        }
        return statement;
    }

    /**
     * Class to measure query execute time
     */
    protected class StatementProxy implements InvocationHandler {
        /**
         * Indicates whether the proxy has been closed. A statement can be closed from a thread other than the one
         * executing on it - the statement finalizer closes tracked statements from whichever thread closes the
         * connection - so the flag is written by one thread and read by others, and must be volatile to reach them at
         * all.
         */
        protected volatile boolean closed = false;
        /**
         * The statement being proxied, or a further interceptor's proxy of it, nulled once a close has been forwarded.
         * It is read by threads other than the one that nulls it, so, like {@link #closed}, volatile.
         */
        protected volatile Object delegate;
        /**
         * The SQL the statement was prepared with, or {@code null} when it was created without one.
         */
        protected final String query;

        /**
         * Constructs a new {@code StatementProxy}.
         *
         * @param parent The statement being proxied, or a further interceptor's proxy of it
         * @param query  The SQL the statement was prepared with, or {@code null} when it was created without one
         */
        public StatementProxy(Object parent, String query) {
            this.delegate = parent;
            this.query = query;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            final String name = method.getName();
            // Handle identity on this proxy, like the statement facade
            // does, rather than forwarding it to the delegate: the delegate
            // does not recognise the handle the application holds, and after
            // close there is no delegate left to answer at all.
            if (compare(TOSTRING_VAL, name)) {
                return toString();
            }
            if (compare(EQUALS_VAL, name)) {
                if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
                    return Boolean.FALSE;
                }
                return Boolean.valueOf(this.equals(Proxy.getInvocationHandler(args[0])));
            }
            if (compare(HASHCODE_VAL, name)) {
                return Integer.valueOf(this.hashCode());
            }
            boolean close = compare(CLOSE_VAL, name);
            // Read the state once: another thread's close() - the pool's
            // statement finalizer closes tracked statements from whichever
            // thread closes the connection - may complete while this call
            // is being answered, and flag and delegate are two separate
            // fields, so a call that read the flag as open can find the
            // delegate already nulled. A close that finished in between is
            // then answered as closed, on the missing delegate as much as
            // on the flag, rather than by invoking on a null object: that
            // is a NullPointerException, and the application is owed the
            // SQLException the closed contract promises. The window this
            // cannot close is the race itself: a call that reads the
            // delegate while it is still live goes to it, an outcome
            // indistinguishable from having simply outrun the close.
            final boolean wasClosed = closed;
            final Object target = delegate;
            final boolean gone = wasClosed || target == null;
            // Allow close to be called multiple times, from either thread
            if (close && gone) {
                return null;
            }
            if (compare(ISCLOSED_VAL, name)) {
                return Boolean.valueOf(gone);
            }
            // Anything other than close() or isClosed() on a closed handle is
            // refused, the way the closed contract promises
            if (gone) {
                throw new SQLException(MessageFactory.get("abstractQueryReport.statementClosed"));
            }
            boolean process = isExecute(method, false);
            // If we are executing, get the current time. The wall clock
            // only gives the timestamp the report hooks carry; the elapsed
            // time is measured with the monotonic clock on both the success
            // and the failure path below, since a clock step between two
            // wall clock reads could make a negative duration, which loses
            // the slow query report and corrupts the statistics booked from
            // it either way
            long start = (process) ? System.currentTimeMillis() : 0;
            long startNanos = (process) ? System.nanoTime() : 0;
            Object result = null;
            try {
                // Execute the query, on the delegate this call read as
                // live: the snapshot keeps a concurrent close from
                // replacing the receiver with null between the guard and
                // the call
                result = method.invoke(target, args);
            } catch (Throwable t) {
                // Only report the failure if we were executing a query.
                // For any other method there is no query to report, and
                // reporting it would replace the exception that the
                // application is expecting with a reporting failure
                if (process) {
                    // The elapsed time of a failed execution is measured
                    // here, on the same monotonic clock the success path
                    // uses below, and handed to the hook along with the
                    // wall clock start. A hook that recomputed it from that
                    // start would read a step of the wall clock during the
                    // execution as a negative duration, and the statistics
                    // the failure is booked into are the ones the success
                    // path keeps
                    long failedDelta = elapsedMillis(startNanos);
                    try {
                        reportFailedQuery(query, args, name, start, failedDelta, t);
                    } catch (Throwable reporting) {
                        ExceptionUtils.handleThrowable(reporting);
                        // Reporting must never disturb the application -
                        // the driver's exception is the one being thrown
                        if (log.isWarnEnabled()) {
                            log.warn(MessageFactory.get("abstractQueryReport.unableToProcessFailedQuery",
                                    AbstractQueryReport.this.getClass().getName()), reporting);
                        }
                    }
                }
                if (t instanceof InvocationTargetException && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            }
            long delta = (process) ? elapsedMillis(startNanos) : Long.MIN_VALUE;
            // See if we meet the requirements to measure. A negative
            // threshold keeps every execution out of the slow path: the
            // duration of an execution is never negative, so a bare
            // comparison against a negative threshold would pass for
            // every execution and leave reportQuery unreachable - the
            // documented "record every query" setting must record the
            // queries as measured, not report them all as slow. A
            // non-execution never reaches either hook: it is not
            // processed, and its sentinel duration would stay under every
            // threshold anyway.
            if (process && threshold >= 0 && delta > threshold) {
                try {
                    reportSlowQuery(query, args, name, start, delta);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    if (log.isWarnEnabled()) {
                        log.warn(MessageFactory.get("abstractQueryReport.unableToProcessSlowQuery",
                                AbstractQueryReport.this.getClass().getName()), t);
                    }
                }
            } else if (process) {
                try {
                    reportQuery(query, args, name, start, delta);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    // Reporting must never disturb the application
                    if (log.isWarnEnabled()) {
                        log.warn(MessageFactory.get("abstractQueryReport.unableToProcessQueryReport",
                                AbstractQueryReport.this.getClass().getName()), t);
                    }
                }
            }
            // Perform close cleanup
            if (close) {
                closed = true;
                delegate = null;
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
            // Reads the live delegate rather than a snapshot: a description
            // says what the proxy holds as it is asked.
            return AbstractQueryReport.this.getClass().getName() + "[Proxy=" + hashCode() + "; Query=" + query +
                    "; Delegate=" + delegate + ']';
        }
    }

}
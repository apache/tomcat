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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Keeps track of statements associated with a connection and invokes close upon {@link java.sql.Connection#close()}
 * Useful for applications that don't close the associated statements after being done with a connection.
 * <p>
 * A statement is tracked with a strong reference until it is closed: an application that forgot about a statement is
 * exactly the case the interceptor exists for, so finalisation must not depend on that statement still being reachable
 * when the connection is returned. A statement closed through the handle this interceptor returns is forgotten at that
 * moment, which keeps the tracked list bounded by the statements that are genuinely still open.
 * <p>
 * A close that reaches the statement by another route cannot be observed by that removal, so its entry is reclaimed at
 * the connection return: there the renewed close is a no-op on a statement the application had already closed, and the
 * close it still owed on one it had not. That assumes the swallowed close did not itself recycle the statement into a
 * cache: a recycling interceptor below this one would have parked it open, and the return-time close would then close
 * a statement a live cache entry still holds. {@link StatementCache} is the module's recycling interceptor and refuses
 * to park while it sits below this finalizer, so the ordering is safe for it; a third-party recycling interceptor is
 * not covered by that guarantee. A creation still in flight while the connection is returned is the one case that
 * return cannot reach - it registers after the return drained the list - and is closed where it arrives instead of
 * being tracked.
 * <p>
 * Tracked statements are handed to the application as proxies implementing the standard JDBC statement interfaces, as
 * is the statement facade the pool always installs in front of this interceptor and as are the proxies of the other
 * statement-wrapping interceptors; the driver's own statement class is therefore not reachable by casting. The
 * <code>Wrapper</code> methods are delegated to the driver, and <code>unwrap</code> is the supported way to reach
 * driver-specific statement methods. A statement the application reaches that way is still tracked here and is closed,
 * like any other tracked statement, when the connection is returned - unless an interceptor listed after this one
 * recycles the statement instead of closing it, as a statement cache beneath this interceptor does.
 * <p>
 * Tracking covers the statements created through the pooled connection handle. A statement created on the driver's
 * own connection - the object <code>Connection.unwrap(Connection.class)</code> hands back - never reaches this
 * interceptor and is neither tracked nor closed when the connection is returned.
 */
public class StatementFinalizer extends AbstractCreateStatementInterceptor {
    private static final Log log = LogFactory.getLog(StatementFinalizer.class);

    /**
     * Statements created on the associated connection that have not been closed yet. Every access must be guarded by
     * {@link #statementsLock}.
     */
    protected final List<StatementEntry> statements = new LinkedList<>();

    /**
     * Monitor guarding {@link #statements} and {@link #returnDrained}. The pool's cleaner thread may clear the list
     * (through {@link #reset}) while a borrower is still creating or closing statements on an abandoned connection, so
     * subclasses that access either must hold this lock as well.
     */
    protected final Object statementsLock = new Object();

    /**
     * Whether the connection return has already drained {@link #statements}: set by {@link #closeInvoked()}, cleared by
     * {@link #reset} at the start of the next borrow. Every access is guarded by {@link #statementsLock}.
     * <p>
     * The driver's create has finished before this interceptor can register the statement, so a creation is not
     * serialized against the connection return. One that lands after the return drained the list cannot be reclaimed by
     * that return, and carrying it into the next borrow's books would only close it out from under the application
     * still holding it; {@link #createStatement} closes it where it arrives instead.
     */
    protected boolean returnDrained = false;

    /**
     * Default constructor.
     */
    public StatementFinalizer() {
    }

    private volatile boolean logCreationStack = false;

    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        if (!(statement instanceof Statement)) {
            return statement;
        }
        Statement tracked = (Statement) statement;
        String name = method.getName();
        int constructorIndex;
        Class<?> statementInterface;
        if (compare(CREATE_STATEMENT, name)) {
            constructorIndex = CREATE_STATEMENT_IDX;
            statementInterface = Statement.class;
        } else if (compare(PREPARE_STATEMENT, name)) {
            constructorIndex = PREPARE_STATEMENT_IDX;
            statementInterface = PreparedStatement.class;
        } else if (compare(PREPARE_CALL, name)) {
            constructorIndex = PREPARE_CALL_IDX;
            statementInterface = CallableStatement.class;
        } else {
            // Not one of the statement creation methods; nothing to track
            return tracked;
        }
        StatementEntry entry = new StatementEntry(tracked);
        boolean drained;
        synchronized (statementsLock) {
            drained = returnDrained;
            if (!drained) {
                statements.add(entry);
            }
        }
        if (drained) {
            // The return's drain has passed, so that return cannot reclaim
            // this statement; close it here rather than leave it open on a
            // session about to serve the next borrower. There is no later
            // attempt to make, so a failure is what the operator hears.
            if (!entry.close()) {
                log.warn(MessageFactory.get("statementFinalizer.unableToCloseStatement"),
                        entry.getAllocationStack());
            }
            return tracked;
        }
        try {
            return getConstructor(constructorIndex, statementInterface)
                    .newInstance(new Object[] { new CloseAwareProxy(entry) });
        } catch (Exception x) {
            // The statement is still tracked and will be closed when the
            // connection is returned, but closing it early will no longer
            // forget it before then.
            log.warn(MessageFactory.get("statementFinalizer.unableToCreateTrackingProxy"), x);
            return tracked;
        }
    }

    @Override
    public void closeInvoked() {
        List<StatementEntry> unclosed;
        synchronized (statementsLock) {
            // From this point a creation still in flight can no longer be
            // reclaimed by this drain; it is told to close itself instead
            // (see returnDrained).
            returnDrained = true;
            if (statements.isEmpty()) {
                return;
            }
            unclosed = new ArrayList<>(statements);
            statements.clear();
        }
        // Closing a statement can block on the database, so it happens
        // outside the lock that guards the list.
        List<StatementEntry> failed = null;
        for (StatementEntry entry : unclosed) {
            if (!entry.close()) {
                if (failed == null) {
                    failed = new ArrayList<>();
                }
                failed.add(entry);
            }
        }
        if (failed != null) {
            // Back on the list, like an entry whose close failed through the
            // application's handle: the reset at the next borrow makes the
            // next attempt.
            synchronized (statementsLock) {
                statements.addAll(failed);
            }
        }
    }

    @Override
    public void setProperties(Map<String, PoolProperties.InterceptorProperty> properties) {
        super.setProperties(properties);

        PoolProperties.InterceptorProperty logProperty = properties.get("trace");
        if (null != logProperty) {
            logCreationStack = logProperty.getValueAsBoolean(logCreationStack);
        }
    }

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        List<StatementEntry> left = null;
        synchronized (statementsLock) {
            // A new borrow starts here (or the instance goes out of
            // service): statements created from now on are accepted into
            // the list again.
            returnDrained = false;
            if (!statements.isEmpty()) {
                left = new ArrayList<>(statements);
                statements.clear();
            }
        }
        // On a teardown the physical close that precedes this reset has
        // already released the statements. On a borrow the session serves
        // again, so what a failed return-time close left behind gets its
        // last attempt here, before the new borrower runs.
        if (left != null && con != null) {
            List<StatementEntry> failed = null;
            for (StatementEntry entry : left) {
                if (!entry.close()) {
                    if (failed == null) {
                        failed = new ArrayList<>();
                    }
                    failed.add(entry);
                    log.warn(MessageFactory.get("statementFinalizer.unableToCloseStatement"),
                            entry.getAllocationStack());
                }
            }
            if (failed != null) {
                // The statement may still be open on the session now being
                // served to the new borrower: keep it for the next attempt,
                // as the return-time drain does.
                synchronized (statementsLock) {
                    statements.addAll(failed);
                }
            }
        }
        super.reset(parent, con);
    }

    /**
     * Entry that tracks a statement and its allocation stack. The statement is held strongly: it must stay closeable
     * until the application or the connection return disposes of it.
     */
    protected class StatementEntry {
        private final Statement statement;
        private final Throwable allocationStack;

        /**
         * Creates a new StatementEntry.
         *
         * @param statement the statement to track
         */
        public StatementEntry(Statement statement) {
            this.statement = statement;
            if (logCreationStack) {
                this.allocationStack = new Throwable();
            } else {
                this.allocationStack = null;
            }
        }

        /**
         * Returns the tracked statement.
         *
         * @return the statement
         */
        public Statement getStatement() {
            return statement;
        }

        /**
         * Returns the stack trace from when this statement was created.
         *
         * @return the allocation stack trace, or null if tracing is disabled
         */
        public Throwable getAllocationStack() {
            return allocationStack;
        }

        /**
         * Closes the tracked statement unless the driver reports it closed already and, with tracing on, reports a
         * statement the application left open.
         *
         * @return <code>true</code> if the statement is closed - because it was found closed or because the close
         *         succeeded; <code>false</code> if an attempted close failed and the statement may still be open
         */
        public boolean close() {
            boolean shallClose;
            // Whether the driver answered at all. The forced close below is no evidence about the application - an
            // already closed statement takes a close as the no-op JDBC demands of it - so the trace warning, which
            // names the application, waits for an answer.
            boolean answered;
            try {
                shallClose = !statement.isClosed();
                answered = true;
            } catch (Throwable ignore) {
                // A driver that cannot say whether the statement is closed
                // must not get to skip the close attempt - the statement
                // may still hold server-side state. A non-fatal Error is the
                // same refused answer as an exception.
                ExceptionUtils.handleThrowable(ignore);
                shallClose = true;
                answered = false;
            }
            boolean closed = !shallClose;
            if (shallClose) {
                try {
                    statement.close();
                    closed = true;
                } catch (Throwable ignore) {
                    // The failed close is reported as false so closeInvoked
                    // keeps the entry for the next attempt; an Error must not
                    // abort the rest of the drain.
                    ExceptionUtils.handleThrowable(ignore);
                    if (log.isDebugEnabled()) {
                        log.debug("Unable to close statement upon connection close.", ignore);
                    }
                }
            }
            if (logCreationStack && shallClose && answered) {
                log.warn(MessageFactory.get("statementFinalizer.statementNotClosed"), allocationStack);
            }
            return closed;
        }
    }

    /**
     * Thin proxy around a tracked statement. Every call is delegated unchanged; only <code>close()</code> is reacted
     * to, by forgetting the tracked entry so that a statement the application closed properly neither lingers in the
     * list nor is touched again when the connection is returned.
     * <p>
     * <code>Object.equals(Object)</code> and <code>hashCode()</code> are answered on the handle itself, as
     * {@link org.apache.tomcat.jdbc.pool.ProxyConnection} and the pool's other statement proxies answer them: the
     * handle equals only itself and hashes at its own identity, so a container holding it next to an unwrapped handle
     * for the same statement holds two elements whichever of the two arrived first. Reaching the statement underneath
     * is <code>unwrap</code>'s purpose, and there the driver's own equality applies.
     */
    protected class CloseAwareProxy implements InvocationHandler {
        private final StatementEntry entry;

        /**
         * Creates a handler for the given tracked statement.
         *
         * @param entry the list entry tracking the statement
         */
        public CloseAwareProxy(StatementEntry entry) {
            this.entry = entry;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (compare(EQUALS_VAL, method)) {
                // The identity of the handle the application holds, and
                // nothing else, as ProxyConnection answers it too: equality
                // delegated to the statement underneath made the handle equal
                // to a statement that did not answer equal back, and a set
                // holding both objects resolved them by insertion order.
                Object other = (args == null || args.length == 0) ? null : args[0];
                return Boolean.valueOf(proxy == other);
            }
            if (compare(HASHCODE_VAL, method)) {
                // The hash that goes with the identity above: the handle's
                // own, so that it keeps the same place in a hash container
                // whatever it is compared against.
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            Object result;
            try {
                result = method.invoke(entry.getStatement(), args);
            } catch (InvocationTargetException ite) {
                // The close failed in the statement - keep the entry so the
                // connection return tries again.
                Throwable cause = ite.getTargetException();
                if (cause != null) {
                    throw cause;
                }
                throw ite;
            }
            if (compare(CLOSE_VAL, method)) {
                synchronized (statementsLock) {
                    statements.remove(entry);
                }
            }
            return result;
        }
    }
}

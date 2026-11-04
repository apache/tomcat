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
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Keeps track of statements associated with a connection and invokes close upon {@link java.sql.Connection#close()}
 * Useful for applications that don't close the associated statements after being done with a connection.
 * <p>
 * A statement is tracked with a strong reference until it is closed: an application that forgot about a statement is
 * exactly the case the interceptor exists for, so finalisation must not depend on that statement still being reachable
 * when the connection is returned. Statements that the application does close are forgotten by the returned proxy at
 * the moment <code>close()</code> completes, keeping the tracked list bounded by the statements that are genuinely
 * still open and closed through that handle. (The removal cannot observe a close that reaches the statement by
 * another route: a statement the application closes through the driver handle that <code>unwrap</code> hands back
 * never passes through this proxy; such entries are simply reclaimed at the connection return, where the renewed
 * close meets a statement already closed as a harmless no-op, as they were before. An interceptor placed outside
 * this one could swallow a close in the same way, but the pool's own {@link StatementCache} no longer does: listed
 * above this interceptor it refuses to recycle, because a recycled statement is left open rather than closed and
 * would be reclaimed here alive, out from under the cache.) A statement whose driver create was still in flight
 * while the connection was returned cannot be reclaimed at the return either - it registers after that return has
 * drained the list - so such a late registration is closed where it arrives instead of being tracked.
 * <p>
 * Tracked statements are handed to the application as proxies implementing the standard JDBC statement interfaces, as
 * is the statement facade the pool always installs in front of this interceptor and as are the proxies of the other
 * statement-wrapping interceptors; the driver's own statement class is therefore not reachable by casting. The
 * <code>Wrapper</code> methods are delegated to the driver, and <code>unwrap</code> is the supported way to reach
 * driver-specific statement methods. A statement the application reaches that way is still tracked here and is closed,
 * like any other tracked statement, when the connection is returned - unless an interceptor listed after this one
 * recycles the statement instead of closing it, as a statement cache beneath this interceptor does.
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
     * A statement creation is not serialized against the connection return: the driver's create has completed and the
     * statement is open before this interceptor can register it. A creation whose connection is returned inside that
     * window would register after the drain - not closed by that return, dropped by the next borrow's {@link #reset},
     * and left open on a session that is about to serve another borrower. A registration that arrives drained
     * therefore does not enter the list: {@link #createStatement} closes the statement itself.
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
            // The connection return drained the list before this statement
            // could be registered, so the return's renewed close cannot find
            // it; close it here rather than leave it open on a session that
            // is about to serve the next borrower. There is no later
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
            // A close the driver refused is retried, the same way a close
            // that fails through the application's handle is kept for this
            // return to retry: back on the list, the entries wait for the
            // next attempt, which the reset at the next borrow makes.
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
        // On a teardown (no connection) the physical close that precedes
        // the reset has already released the statements. On a borrow the
        // session is about to serve again, so an entry a failed return-time
        // close left behind gets its last attempt here, before the new
        // borrower is handed the session.
        if (left != null && con != null) {
            for (StatementEntry entry : left) {
                if (!entry.close()) {
                    log.warn(MessageFactory.get("statementFinalizer.unableToCloseStatement"),
                            entry.getAllocationStack());
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
         * Closes the tracked statement, reporting failure the same way the connection return does.
         *
         * @return <code>true</code> if the statement is closed - because it was found closed or because the close
         *         succeeded; <code>false</code> if an attempted close failed and the statement may still be open
         */
        public boolean close() {
            boolean shallClose = false;
            try {
                shallClose = !statement.isClosed();
            } catch (Exception ignore) {
                // A driver that cannot say whether the statement is closed
                // must not get to skip the close attempt - the statement
                // may still hold server-side state.
                shallClose = true;
            }
            boolean closed = !shallClose;
            if (shallClose) {
                try {
                    statement.close();
                    closed = true;
                } catch (Exception ignore) {
                    if (log.isDebugEnabled()) {
                        log.debug("Unable to close statement upon connection close.", ignore);
                    }
                }
            }
            if (logCreationStack && shallClose) {
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
     * handle equals only itself and hashes at its own identity. A container that holds this handle next to an
     * unwrapped handle for the same statement therefore holds two elements, whichever of the two was added first,
     * instead of resolving the pair to one element or the other by insertion order. The statement underneath stays a
     * distinct object that the handle compares equal to in neither direction; reaching it is <code>unwrap</code>'s
     * purpose, and there the driver's own equality applies, as it does for the statement facade's handle above.
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
                // nothing else, as ProxyConnection answers it too. Equality
                // was delegated to the statement underneath, which made the
                // handle equal to a statement that - answering by its own
                // equality, typically identity - did not answer equal to the
                // handle back. That one-sided relation is what
                // Object.equals(Object) rules out, and it decided membership
                // of a container holding both objects by insertion order:
                // the same pair hashed alike and came out as one element or
                // two depending on which was added first.
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

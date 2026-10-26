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

/**
 * Keeps track of statements associated with a connection and invokes close upon {@link java.sql.Connection#close()}
 * Useful for applications that don't close the associated statements after being done with a connection.
 * <p>
 * A statement is tracked with a strong reference until it is closed: an
 * application that forgot about a statement is exactly the case the
 * interceptor exists for, so finalisation must not depend on that statement
 * still being reachable when the connection is returned. Statements that the
 * application does close are forgotten by the returned proxy at the moment
 * <code>close()</code> completes, keeping the tracked list bounded by the
 * statements that are genuinely still open. (An interceptor placed outside
 * this one may swallow the statement's <code>close()</code>, e.g. a statement
 * cache recycling instead of closing; such entries are simply reclaimed at
 * the connection return, as they were before.)
 *
 */
public class StatementFinalizer extends AbstractCreateStatementInterceptor {
    private static final Log log = LogFactory.getLog(StatementFinalizer.class);

    /**
     * Statements created on the associated connection that have not been
     * closed yet. Every access must be guarded by {@link #statementsLock}.
     */
    protected final List<StatementEntry> statements = new LinkedList<>();

    /**
     * Monitor guarding {@link #statements}. The pool's cleaner thread may
     * clear the list (through {@link #reset}) while a borrower is still
     * creating or closing statements on an abandoned connection, so
     * subclasses that access the list must hold this lock as well.
     */
    protected final Object statementsLock = new Object();

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
            // not one of the statement creation methods; nothing to track
            return tracked;
        }
        StatementEntry entry = new StatementEntry(tracked);
        synchronized (statementsLock) {
            statements.add(entry);
        }
        try {
            return getConstructor(constructorIndex, statementInterface)
                    .newInstance(new Object[] { new CloseAwareProxy(entry) });
        } catch (Exception x) {
            // The statement is still tracked and will be closed when the
            // connection is returned, but closing it early will no longer
            // forget it before then.
            log.warn("Unable to create statement tracking proxy.", x);
            return tracked;
        }
    }

    @Override
    public void closeInvoked() {
        List<StatementEntry> unclosed;
        synchronized (statementsLock) {
            if (statements.isEmpty()) {
                return;
            }
            unclosed = new ArrayList<>(statements);
            statements.clear();
        }
        // Closing a statement can block on the database, so it happens
        // outside the lock that guards the list.
        for (StatementEntry entry : unclosed) {
            entry.close();
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
        synchronized (statementsLock) {
            statements.clear();
        }
        super.reset(parent, con);
    }

    /**
     * Entry that tracks a statement and its allocation stack. The statement
     * is held strongly: it must stay closeable until the application or the
     * connection return disposes of it.
     */
    protected class StatementEntry {
        private final Statement statement;
        private final Throwable allocationStack;

        /**
         * Creates a new StatementEntry.
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
         * @return the statement
         */
        public Statement getStatement() {
            return statement;
        }

        /**
         * Returns the stack trace from when this statement was created.
         * @return the allocation stack trace, or null if tracing is disabled
         */
        public Throwable getAllocationStack() {
            return allocationStack;
        }

        /**
         * Closes the tracked statement, reporting failure the same way the
         * connection return does.
         */
        public void close() {
            boolean shallClose = false;
            try {
                shallClose = !statement.isClosed();
            } catch (Exception ignore) {
                // A driver that cannot say whether the statement is closed
                // must not get to skip the close attempt - the statement
                // may still hold server-side state.
                shallClose = true;
            }
            if (shallClose) {
                try {
                    statement.close();
                } catch (Exception ignore) {
                    if (log.isDebugEnabled()) {
                        log.debug("Unable to close statement upon connection close.", ignore);
                    }
                }
            }
            if (logCreationStack && shallClose) {
                log.warn("Statement created, but was not closed at:", allocationStack);
            }
        }
    }

    /**
     * Thin proxy around a tracked statement. Every call is delegated
     * unchanged; only <code>close()</code> is reacted to, by forgetting the
     * tracked entry so that a statement the application closed properly
     * neither lingers in the list nor is touched again when the connection
     * is returned. Identity follows the statement underneath: the proxy is
     * equal to itself and to its delegate, and hashes like its delegate,
     * though the delegate's own equality - typically identity - will not
     * recognise the proxy in the other direction.
     */
    protected class CloseAwareProxy implements InvocationHandler {
        private final StatementEntry entry;

        /**
         * Creates a handler for the given tracked statement.
         * @param entry the list entry tracking the statement
         */
        public CloseAwareProxy(StatementEntry entry) {
            this.entry = entry;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (compare(EQUALS_VAL, method)) {
                // Identity of the handle the application holds, else the
                // statement underneath - a proxy that answered equals by
                // delegating would not be equal to itself.
                Object other = args[0];
                return Boolean.valueOf(other == proxy || entry.getStatement().equals(other));
            }
            if (compare(HASHCODE_VAL, method)) {
                return Integer.valueOf(entry.getStatement().hashCode());
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

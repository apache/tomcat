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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Implementation of <b>JdbcInterceptor</b> that proxies statements and result sets so that neither the physical
 * session nor the driver's own handles reach the application through them: {@link Statement#getConnection()} is
 * answered with the pool's connection handle rather than the driver's connection, and {@link ResultSet#getStatement()}
 * with the statement handle rather than the driver's statement - the hiding of the physical connection this
 * interceptor exists for (Bug 48392, described in this interceptor's section of the <code>jdbc-pool</code>
 * documentation page).
 * <p>
 * Beyond the hiding, the statement proxy keeps its own handle contract: calls reach the driver statement only
 * while the proxy is open, a repeat close answers as a no-op, every other call on a retired handle is refused with
 * an {@link SQLException}, and the driver's answer to a close that failed is passed to the caller - the handle is
 * retired whichever way the driver answered. The connection the proxy reports is a snapshot of the borrow that
 * created it, so a component that reuses a statement across borrows - {@link StatementCache} - must re-bind the
 * handle first, as {@link StatementProxy#setConnection(Object)} documents. The result set proxy answers
 * {@link ResultSet#getStatement()} and the {@link Object} identity methods ({@code toString}, {@code equals},
 * {@code hashCode}) itself; every other call, the {@link java.sql.Wrapper} methods included, reaches the driver object
 * unchanged.
 */
public class StatementDecoratorInterceptor extends AbstractCreateStatementInterceptor {

    private static final Log logger = LogFactory.getLog(StatementDecoratorInterceptor.class);

    /**
     * Method names that return a ResultSet, spelled with the base class's constants.
     */
    protected static final String[] RESULTSET_TYPES = { EXECUTE_QUERY, GET_GENERATED_KEYS, GET_RESULTSET };

    /**
     * Default constructor.
     */
    public StatementDecoratorInterceptor() {
    }

    /**
     * the constructor to create the resultSet proxies
     */
    protected static volatile Constructor<?> resultSetConstructor = null;

    @Override
    public void closeInvoked() {
        // Nothing to do
    }

    /*
     * The accessor and the cached constructor are protected, so a subclass - in-tree or third party, since the pool
     * instantiates interceptors reflectively from the configured class names - can reach and replace both. Like the
     * base class's constructor cache, every use must create a proxy implementing the ResultSet interface; overriding
     * the accessor is the supported way for a subclass to supply its own. The cache duplicates the constructor the
     * base class caches at its RESULTSET_IDX slot, but is computed lazily rather than in a class initializer. Given
     * the comments in the jdbc-pool code regarding caching for performance, continue to use Proxy.getProxyClass().
     * This will need to be revisited if that method is marked for removal.
     */
    /**
     * Gets the constructor for creating ResultSet proxies.
     *
     * @return the ResultSet proxy constructor
     *
     * @throws NoSuchMethodException if the constructor is not found
     */
    @SuppressWarnings("deprecation")
    protected Constructor<?> getResultSetConstructor() throws NoSuchMethodException {
        if (resultSetConstructor == null) {
            Class<?> proxyClass = Proxy.getProxyClass(StatementDecoratorInterceptor.class.getClassLoader(),
                    new Class[] { ResultSet.class });
            resultSetConstructor = proxyClass.getConstructor(new Class[] { InvocationHandler.class });
        }
        return resultSetConstructor;
    }

    /**
     * Wraps the statement the connection call has just created in this interceptor's proxy.
     * {@inheritDoc}
     * <p>
     * The {@code time} argument is unused - this interceptor does no timing. If building the proxy fails, the
     * failure is logged and the unproxied driver statement is returned, so a failure degrades the connection
     * hiding rather than failing the application's call.
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        try {
            String name = method.getName();
            Constructor<?> constructor = null;
            String sql = null;
            if (compare(CREATE_STATEMENT, name)) {
                constructor = getConstructor(CREATE_STATEMENT_IDX, Statement.class);
            } else if (compare(PREPARE_STATEMENT, name)) {
                constructor = getConstructor(PREPARE_STATEMENT_IDX, PreparedStatement.class);
                sql = (String) args[0];
            } else if (compare(PREPARE_CALL, name)) {
                constructor = getConstructor(PREPARE_CALL_IDX, CallableStatement.class);
                sql = (String) args[0];
            } else {
                // Do nothing, might be a future unsupported method
                // so we better bail out and let the system continue
                return statement;
            }
            return createDecorator(proxy, method, args, statement, constructor, sql);
        } catch (Exception x) {
            if (x instanceof InvocationTargetException) {
                Throwable cause = x.getCause();
                if (cause instanceof ThreadDeath) {
                    throw (ThreadDeath) cause;
                }
                if (cause instanceof VirtualMachineError) {
                    throw (VirtualMachineError) cause;
                }
            }
            logger.warn(MessageFactory.get("statementDecoratorInterceptor.unableToCreateStatementProxy"), x);
        }
        return statement;
    }

    /**
     * Creates a proxy for a Statement.
     *
     * @param proxy       The proxy object on which the method that triggered the creation of the statement was called.
     * @param method      The method that was called on the proxy
     * @param args        The arguments passed as part of the method call to the proxy
     * @param statement   The statement object that is to be proxied
     * @param constructor The constructor for the desired proxy
     * @param sql         The sql of the statement
     *
     * @return A new proxy for the Statement
     *
     * @throws InstantiationException    Couldn't instantiate object
     * @throws IllegalAccessException    Inaccessible constructor
     * @throws InvocationTargetException Exception thrown from constructor
     */
    protected Object createDecorator(Object proxy, Method method, Object[] args, Object statement,
            Constructor<?> constructor, String sql)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Object result = null;
        StatementProxy<Statement> statementProxy = new StatementProxy<>((Statement) statement, sql);
        result = constructor.newInstance(new Object[] { statementProxy });
        statementProxy.setActualProxy(result);
        statementProxy.setConnection(proxy);
        statementProxy.setConstructor(constructor);
        return result;
    }

    /**
     * Checks if the given method returns a ResultSet. This, not any executeQuery-specific test, is the dispatch
     * predicate the proxies consult.
     *
     * @param method  the method to check
     * @param process whether processing is already enabled
     *
     * @return true if the method returns a ResultSet
     */
    protected boolean isResultSet(Method method, boolean process) {
        return process(RESULTSET_TYPES, method, process);
    }

    /**
     * Invocation handler behind a statement proxy: it keeps the handle contract the class javadoc describes -
     * lifecycle answered from its own state, close failures passed on, the borrow's connection handle reported
     * instead of the driver's - and wraps the result sets of the calls named in {@link #RESULTSET_TYPES}.
     *
     * @param <T> The type of Statement being proxied.
     */
    protected class StatementProxy<T extends Statement> implements InvocationHandler {

        /**
         * Indicates whether this statement has been closed. A statement can be closed from a thread other than the one
         * executing on it - the statement finalizer closes tracked statements from whichever thread closes the
         * connection, and the statement cache forces a close from its own purge - so the flag is written by one thread
         * and read by others, and must be volatile to reach them at all.
         */
        protected volatile boolean closed = false;
        /**
         * The delegated statement, nulled once a close has been forwarded. It is read by threads other than the one
         * that nulls it, so, like {@link #closed}, volatile.
         */
        protected volatile T delegate;
        private Object actualProxy;
        /**
         * The connection handle of the borrow during which this proxy was created, bound through
         * {@link #setConnection(Object)}. It is a creation-time snapshot, not a live view: once that borrow is
         * returned the snapshot is the disposed facade, which answers close, abort, isClosed, isValid, toString,
         * equals and hashCode itself and refuses everything else. A component that lets a statement proxy
         * outlive its borrow -
         * {@link StatementCache} recycling a parked statement, for instance - must therefore re-bind the handle to the
         * new borrower before handing the proxy out; see {@link StatementCache#invoke(Object, Method, Object[])}.
         */
        private Object connection;
        private String sql;
        private Constructor<?> constructor;

        /**
         * Constructs a StatementProxy.
         *
         * @param delegate the statement to delegate to
         * @param sql      the SQL text
         */
        public StatementProxy(T delegate, String sql) {
            this.delegate = delegate;
            this.sql = sql;
        }

        /**
         * Returns the delegated statement.
         *
         * @return the delegated statement
         */
        public T getDelegate() {
            return this.delegate;
        }

        /**
         * Returns the SQL text.
         *
         * @return the SQL text
         */
        public String getSql() {
            return sql;
        }

        /**
         * Binds the connection handle that {@link #getConnection()} will report for this statement.
         * {@code createDecorator} binds the handle of the borrow that creates the statement, and {@link StatementCache}
         * seeds the closing borrow's handle when it parks a statement; the binding is kept as a snapshot and is never
         * refreshed on its own. A handle kept past its borrow is inert by design, so anything reusing a statement proxy
         * across borrows must call this method with the new borrower's handle before the proxy is handed out, as
         * {@link StatementCache} does on a cache hit.
         *
         * @param proxy the connection proxy
         */
        public void setConnection(Object proxy) {
            this.connection = proxy;
        }

        /**
         * Returns the connection proxy most recently bound through {@link #setConnection(Object)}. While that binding's
         * borrow is current it is a live handle; a statement kept past its borrow answers with the disposed facade it
         * came from. Once the statement is closed the handler refuses the call before it reaches the snapshot, so a
         * stale binding is only visible through diagnostics such as {@link #toString()}.
         *
         * @return the connection proxy
         */
        public Object getConnection() {
            return this.connection;
        }

        /**
         * Sets the actual proxy object.
         *
         * @param proxy the actual proxy
         */
        public void setActualProxy(Object proxy) {
            this.actualProxy = proxy;
        }

        /**
         * Returns the actual proxy object.
         *
         * @return the actual proxy
         */
        public Object getActualProxy() {
            return this.actualProxy;
        }

        /**
         * Returns the constructor used to create this proxy.
         *
         * @return the constructor
         */
        public Constructor<?> getConstructor() {
            return constructor;
        }

        /**
         * Sets the constructor for creating proxies.
         *
         * @param constructor the constructor
         */
        public void setConstructor(Constructor<?> constructor) {
            this.constructor = constructor;
        }

        /**
         * Called when the close method is invoked on the statement.
         * <p>
         * The driver's answer is passed on to the caller: {@code Statement.close()} declares
         * {@link SQLException} for a database error while closing, and this proxy reports every other
         * call as the delegate answered it, so a failed close must not come back as a success. The
         * swallowed failure this replaces told the application that the statement was closed while
         * the driver had left its resources on the session the pooled connection was about to hand to
         * the next borrower, and nothing along the path recorded or retried it.
         * <p>
         * The handle is retired whichever way the driver answered: the application has been told, and
         * a handle whose close failed must not go on answering calls on a statement the driver may
         * have half released. Where no application waits on the close - the cache dropping a
         * statement, see {@link StatementCache#closeStatement} - the failure is answered with a log
         * line instead, so that one statement that will not close cannot stop the rest of that purge.
         *
         * @throws SQLException if the underlying statement could not be closed
         */
        public void closeInvoked() throws SQLException {
            // Read the delegate once: a second close running concurrently -
            // the cache purge, or the application and the finalizer closing
            // the same handle - can null the field between two reads, and a
            // second read that finds it gone would invoke close() on a null
            // receiver. A close that already completed is the one this
            // snapshot answers for: the delegate is gone, nothing is
            // re-closed, and the handle is retired quietly all the same.
            final T target = delegate;
            try {
                if (target != null) {
                    target.close();
                }
            } finally {
                closed = true;
                delegate = null;
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (compare(TOSTRING_VAL, method)) {
                return toString();
            }
            // Handle identity on this proxy, like the statement facade
            // does, rather than forwarding it to the delegate: the delegate
            // does not recognise the handle the application holds, and after
            // close there is no delegate left to answer at all.
            if (compare(EQUALS_VAL, method)) {
                if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
                    return Boolean.FALSE;
                }
                return Boolean.valueOf(this.equals(Proxy.getInvocationHandler(args[0])));
            }
            if (compare(HASHCODE_VAL, method)) {
                return Integer.valueOf(this.hashCode());
            }
            boolean close = compare(CLOSE_VAL, method);
            // Read the state once: another thread's close() - the pool's
            // statement finalizer closes tracked statements from whichever
            // thread closes the connection, and the statement cache forces a
            // close from its purge - may complete while this call is being
            // answered, and flag and delegate are two separate fields, so a
            // call that read the flag as open can find the delegate already
            // nulled. A close that finished in between is then answered as
            // closed, on the missing delegate as much as on the flag, rather
            // than by invoking on a null object: that is a
            // NullPointerException, and the application is owed the
            // SQLException the closed contract promises. The window this
            // cannot close is the race itself: a call that reads the
            // delegate while it is still live goes to it, an outcome
            // indistinguishable from having simply outrun the close.
            final boolean wasClosed = closed;
            final T target = delegate;
            final boolean gone = wasClosed || target == null;
            // Allow close to be called multiple times, from either thread
            if (close && gone) {
                return null;
            }
            // Answered from this handle's own state, not from the driver:
            // while the handle is open, a delegate closed behind the proxy's
            // back - the closeOnCompletion() auto-close, or an out-of-band
            // close of the object reached through unwrap() - is reported
            // open by design, and the next forwarded call surfaces the
            // driver's own failure. This is the answer an application sees
            // through a live facade handle, because StatementFacade forwards
            // isClosed while that layer is itself live. Answering from the
            // flag once retired is deliberate and pinned by
            // TestStatementDecoratorCloseFailure: a handle whose close the
            // driver refused must still report itself closed, so this arm
            // must not be "simplified" into a pure forward.
            if (compare(ISCLOSED_VAL, method)) {
                return Boolean.valueOf(gone);
            }
            if (gone) {
                throw new SQLException(MessageFactory.get("statementDecoratorInterceptor.statementClosed"));
            }
            if (compare(GETCONNECTION_VAL, method)) {
                // The handle of the current borrow, as bound by
                // setConnection; a proxy kept past its borrow gets the
                // inert handle it came from, and a closed one has already
                // thrown above
                return connection;
            }
            // Whether this call hands out a result set that must be wrapped
            boolean process = isResultSet(method, false);
            Object result = null;
            try {
                // The driver's answer to a close reaches the caller:
                // closeInvoked propagates it, the handle being retired
                // whichever way the driver answered
                if (close) {
                    closeInvoked();
                } else {
                    // Execute the query, on the delegate this call read as
                    // live: the snapshot keeps a concurrent close from
                    // replacing the receiver with null between the guard and
                    // the call
                    result = method.invoke(target, args);
                }
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            }
            if (process && result != null) {
                try {
                    Constructor<?> cons = getResultSetConstructor();
                    result = cons.newInstance(new Object[] { new ResultSetProxy(actualProxy, result) });
                } catch (ReflectiveOperationException x) {
                    // The proxied methods that produce a result set -
                    // executeQuery, getResultSet, getGeneratedKeys - all
                    // declare SQLException and nothing else. A checked
                    // reflective failure escaping here would be wrapped by
                    // the proxy machinery into an UndeclaredThrowableException,
                    // breaking the declared contract of the call; report it as
                    // the exception the method does declare. The driver result
                    // set the call already opened belongs to the physical
                    // session this pooled connection will hand to the next
                    // borrower, so a failed wrap closes it rather than
                    // stranding the cursor there.
                    SQLException sx = new SQLException(
                            MessageFactory.get("statementDecoratorInterceptor.unableToWrapResultSet"), x);
                    try {
                        ((ResultSet) result).close();
                    } catch (SQLException | RuntimeException ignore) {
                        // The failure reported above is the one the
                        // application is owed; a cursor that will not close
                        // on top of it adds nothing to tell.
                    }
                    throw sx;
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
            StringBuffer buf = new StringBuffer(StatementProxy.class.getName());
            buf.append("[Proxy=");
            buf.append(System.identityHashCode(this));
            buf.append("; Sql=");
            buf.append(getSql());
            buf.append("; Delegate=");
            // Reads the live delegate rather than a snapshot: a description
            // says what the proxy holds as it is asked.
            buf.append(getDelegate());
            buf.append("; Connection=");
            buf.append(getConnection());
            buf.append(']');
            return buf.toString();
        }
    }

    /**
     * Invocation handler that proxies ResultSet instances.
     */
    protected static class ResultSetProxy implements InvocationHandler {

        private Object st;
        private Object delegate;

        /**
         * Constructs a ResultSetProxy.
         *
         * @param st       the statement proxy
         * @param delegate the ResultSet to delegate to
         */
        public ResultSetProxy(Object st, Object delegate) {
            this.st = st;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            // Handle the Object methods on this proxy, like the statement
            // facade does, rather than forwarding them to the delegate: the
            // delegate does not recognise the handle the application holds,
            // and answers that leak driver details through a proxy.
            if (TOSTRING_VAL.equals(name)) {
                return toString();
            }
            if (EQUALS_VAL.equals(name)) {
                if (args[0] == null || !Proxy.isProxyClass(args[0].getClass())) {
                    return Boolean.FALSE;
                }
                return Boolean.valueOf(this.equals(Proxy.getInvocationHandler(args[0])));
            }
            if (HASHCODE_VAL.equals(name)) {
                return Integer.valueOf(this.hashCode());
            }
            if (GET_STATEMENT.equals(name)) {
                return this.st;
            } else {
                try {
                    return method.invoke(this.delegate, args);
                } catch (Throwable t) {
                    if (t instanceof InvocationTargetException && t.getCause() != null) {
                        throw t.getCause();
                    } else {
                        throw t;
                    }
                }
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
            return ResultSetProxy.class.getName() + "[Proxy=" + hashCode() + "; Delegate=" + delegate + ']';
        }
    }
}

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

/**
 * Implementation of <b>JdbcInterceptor</b> that proxies resultSets and statements.
 */
public class StatementDecoratorInterceptor extends AbstractCreateStatementInterceptor {

    private static final Log logger = LogFactory.getLog(StatementDecoratorInterceptor.class);

    /**
     * Method name for executeQuery.
     */
    protected static final String EXECUTE_QUERY  = "executeQuery";
    /**
     * Method name for getGeneratedKeys.
     */
    protected static final String GET_GENERATED_KEYS = "getGeneratedKeys";
    /**
     * Method name for getResultSet.
     */
    protected static final String GET_RESULTSET  = "getResultSet";

    /**
     * Method names that return a ResultSet.
     */
    protected static final String[] RESULTSET_TYPES = {EXECUTE_QUERY, GET_GENERATED_KEYS, GET_RESULTSET};

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
        // nothing to do
    }

    /*
     * Neither the class nor the constructor are exposed outside of jdbc-pool.
     * Given the comments in the jdbc-pool code regarding caching for
     * performance, continue to use Proxy.getProxyClass(). This will need to be
     * revisited if that method is marked for removal.
     */
    /**
     * Gets the constructor for creating ResultSet proxies.
     * @return the ResultSet proxy constructor
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
     * Creates a statement interceptor to monitor query response times
     */
    @Override
    public Object createStatement(Object proxy, Method method, Object[] args, Object statement, long time) {
        try {
            String name = method.getName();
            Constructor<?> constructor = null;
            String sql = null;
            if (compare(CREATE_STATEMENT, name)) {
                // createStatement
                constructor = getConstructor(CREATE_STATEMENT_IDX, Statement.class);
            } else if (compare(PREPARE_STATEMENT, name)) {
                // prepareStatement
                constructor = getConstructor(PREPARE_STATEMENT_IDX, PreparedStatement.class);
                sql = (String)args[0];
            } else if (compare(PREPARE_CALL, name)) {
                // prepareCall
                constructor = getConstructor(PREPARE_CALL_IDX, CallableStatement.class);
                sql = (String)args[0];
            } else {
                // do nothing, might be a future unsupported method
                // so we better bail out and let the system continue
                return statement;
            }
            return createDecorator(proxy, method, args, statement, constructor, sql);
        } catch (Exception x) {
            if (x instanceof InvocationTargetException) {
                Throwable cause = x.getCause();
                if (cause instanceof VirtualMachineError) {
                    throw (VirtualMachineError) cause;
                }
            }
            logger.warn("Unable to create statement proxy for slow query report.", x);
        }
        return statement;
    }

    /**
     * Creates a proxy for a Statement.
     *
     * @param proxy         The proxy object on which the method that triggered
     *                          the creation of the statement was called.
     * @param method        The method that was called on the proxy
     * @param args          The arguments passed as part of the method call to
     *                          the proxy
     * @param statement     The statement object that is to be proxied
     * @param constructor   The constructor for the desired proxy
     * @param sql           The sql of of the statement
     *
     * @return  A new proxy for the Statement
     * @throws InstantiationException Couldn't instantiate object
     * @throws IllegalAccessException Inaccessible constructor
     * @throws InvocationTargetException Exception thrown from constructor
     */
    protected Object createDecorator(Object proxy, Method method, Object[] args,
                                     Object statement, Constructor<?> constructor, String sql)
        throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Object result = null;
        StatementProxy<Statement> statementProxy =
                new StatementProxy<>((Statement)statement,sql);
        result = constructor.newInstance(new Object[] { statementProxy });
        statementProxy.setActualProxy(result);
        statementProxy.setConnection(proxy);
        statementProxy.setConstructor(constructor);
        return result;
    }

    /**
     * Checks if the given method name is executeQuery.
     * @param methodName the method name to check
     * @return true if the method is executeQuery
     */
    protected boolean isExecuteQuery(String methodName) {
        return EXECUTE_QUERY.equals(methodName);
    }

    /**
     * Checks if the given method is executeQuery.
     * @param method the method to check
     * @return true if the method is executeQuery
     */
    protected boolean isExecuteQuery(Method method) {
        return isExecuteQuery(method.getName());
    }

    /**
     * Checks if the given method returns a ResultSet.
     * @param method the method to check
     * @param process whether processing is already enabled
     * @return true if the method returns a ResultSet
     */
    protected boolean isResultSet(Method method, boolean process) {
        return process(RESULTSET_TYPES, method, process);
    }

    /**
     * Class to measure query execute time.
     *
     * @param <T> The type of Statement being proxied.
     */
    protected class StatementProxy<T extends Statement> implements InvocationHandler {

        /**
         * Indicates whether this statement has been closed.
         */
        protected boolean closed = false;
        /**
         * The delegated statement.
         */
        protected T delegate;
        private Object actualProxy;
        /**
         * The connection handle of the borrow during which this proxy was
         * created, bound through {@link #setConnection(Object)}. It is a
         * creation-time snapshot, not a live view: once that borrow is
         * returned the handle answers nothing. A component that lets a
         * statement proxy outlive its borrow - {@link StatementCache}
         * recycling a parked statement, for instance - must therefore
         * re-bind the handle to the new borrower before handing the proxy
         * out; see {@link StatementCache#invoke(Object, Method, Object[])}.
         */
        private Object connection;
        private String sql;
        private Constructor<?> constructor;

        /**
         * Constructs a StatementProxy.
         * @param delegate the statement to delegate to
         * @param sql the SQL text
         */
        public StatementProxy(T delegate, String sql) {
            this.delegate = delegate;
            this.sql = sql;
        }
        /**
         * Returns the delegated statement.
         * @return the delegated statement
         */
        public T getDelegate() {
            return this.delegate;
        }

        /**
         * Returns the SQL text.
         * @return the SQL text
         */
        public String getSql() {
            return sql;
        }

        /**
         * Binds the connection handle that {@link #getConnection()} will
         * report for this statement. {@code createDecorator} binds the
         * handle of the borrow that creates the statement, and
         * {@link StatementCache} seeds the closing borrow's handle when it
         * parks a statement; the binding is kept as a snapshot and is
         * never refreshed on its own. A handle kept past its borrow is
         * inert by design, so anything reusing a statement proxy across
         * borrows must call this method with the new borrower's handle
         * before the proxy is handed out, as {@link StatementCache} does
         * on a cache hit.
         *
         * @param proxy the connection proxy
         */
        public void setConnection(Object proxy) {
            this.connection = proxy;
        }
        /**
         * Returns the connection proxy most recently bound through
         * {@link #setConnection(Object)}. While that binding's borrow is
         * current it is a live handle; a statement kept past its borrow
         * answers with the disposed facade it came from. Once the
         * statement is closed the handler refuses the call before it
         * reaches the snapshot, so a stale binding is only visible
         * through diagnostics such as {@link #toString()}.
         *
         * @return the connection proxy
         */
        public Object getConnection() {
            return this.connection;
        }

        /**
         * Sets the actual proxy object.
         * @param proxy the actual proxy
         */
        public void setActualProxy(Object proxy){
            this.actualProxy = proxy;
        }
        /**
         * Returns the actual proxy object.
         * @return the actual proxy
         */
        public Object getActualProxy() {
            return this.actualProxy;
        }


        /**
         * Returns the constructor used to create this proxy.
         * @return the constructor
         */
        public Constructor<?> getConstructor() {
            return constructor;
        }
        /**
         * Sets the constructor for creating proxies.
         * @param constructor the constructor
         */
        public void setConstructor(Constructor<?> constructor) {
            this.constructor = constructor;
        }
        /**
         * Called when the close method is invoked on the statement.
         */
        public void closeInvoked() {
            if (getDelegate()!=null) {
                try {
                    getDelegate().close();
                }catch (SQLException ignore) {
                }
            }
            closed = true;
            delegate = null;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (compare(TOSTRING_VAL,method)) {
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
            // was close invoked?
            boolean close = compare(CLOSE_VAL, method);
            // allow close to be called multiple times
            if (close && closed) {
                return null;
            }
            // are we calling isClosed?
            if (compare(ISCLOSED_VAL, method)) {
                return Boolean.valueOf(closed);
            }
            // if we are calling anything else, bail out
            if (closed) {
                throw new SQLException("Statement closed.");
            }
            if (compare(GETCONNECTION_VAL,method)){
                // the handle of the current borrow, as bound by
                // setConnection; a proxy kept past its borrow gets the
                // inert handle it came from, and a closed one has already
                // thrown above
                return connection;
            }
            boolean process = false;
            process = isResultSet(method, process);
            // check to see if we are about to execute a query
            // if we are executing, get the current time
            Object result = null;
            try {
                // perform close cleanup
                if (close) {
                    closeInvoked();
                } else {
                    // execute the query
                    result = method.invoke(delegate, args);
                }
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException
                        && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            }
            if (process && result != null) {
                Constructor<?> cons = getResultSetConstructor();
                result = cons.newInstance(new Object[]{new ResultSetProxy(actualProxy, result)});
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
         * @param st the statement proxy
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
                    if (t instanceof InvocationTargetException
                            && t.getCause() != null) {
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

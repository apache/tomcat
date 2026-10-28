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
package org.apache.tomcat.jdbc.pool;

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
import java.sql.Wrapper;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jdbc.pool.interceptor.AbstractCreateStatementInterceptor;

/**
 * Facade that creates statement and result set proxies.
 * <p>
 * The handles this facade hands out implement only the standard JDBC
 * interfaces, as do the proxies of the interceptors that wrap statements
 * beneath it, so the driver's own statement and result set classes are never
 * visible to the application through a cast. Every result set a pooled
 * statement call hands back reaches the application as one of these
 * handles, whatever the method that returned it was called: a cursor that
 * arrives as a plain object - a REF cursor OUT-parameter read through
 * {@link java.sql.CallableStatement#getObject(int)}, for instance - is
 * sealed to the borrow like the ones the standard result set methods
 * return. The <code>Wrapper</code> methods are delegated to the driver, and
 * <code>unwrap</code> is the supported way to reach driver-specific methods
 * through every layer. An unwrapped driver object, however, is a driver
 * object rather than a pooled handle: retiring
 * these handles at the end of the borrow does not retire what was already
 * unwrapped, and only tracking the statements with
 * {@link org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer} (or the
 * application closing them) keeps the statements underneath from outliving
 * the borrow - unless an interceptor recycles them for the next borrow, as a
 * statement cache does.
 */
public class StatementFacade extends AbstractCreateStatementInterceptor {

    private static final Log logger = LogFactory.getLog(StatementFacade.class);

    /**
     * Whether the borrow this facade was created for has ended. The pool
     * creates one facade per borrow, so this says when every handle this
     * instance handed out stopped belonging to a connection the application
     * still holds.
     */
    private volatile boolean borrowEnded = false;

    /**
     * Constructs a StatementFacade.
     * @param interceptor the next interceptor in the chain
     */
    protected StatementFacade(JdbcInterceptor interceptor) {
        setUseEquals(interceptor.isUseEquals());
        setNext(interceptor);
    }

    /**
     * Marks the borrow as ended, which closes every statement and result set
     * handle this facade handed out. Called before the connection is handed
     * back, so a handle cannot outlive the borrow and end up driving the
     * session of the next borrower. A borrow that is abandoned instead never
     * runs this method, but the pool destroys an abandoned session rather
     * than lending it again, so those handles only fail at the driver.
     */
    @Override
    public void closeInvoked() {
        borrowEnded = true;
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
                sql = (String) args[0];
            } else if (compare(PREPARE_CALL, name)) {
                // prepareCall
                constructor = getConstructor(PREPARE_CALL_IDX, CallableStatement.class);
                sql = (String) args[0];
            } else {
                // do nothing
                return statement;
            }
            return constructor.newInstance(new Object[] { new StatementProxy(statement, sql, proxy) });
        } catch (Exception x) {
            logger.warn("Unable to create statement proxy.", x);
        }
        return statement;
    }

    /**
     * Proxy for a SQL statement used to measure query execute time.
     */
    protected class StatementProxy implements InvocationHandler {
        /**
         * Indicates whether this statement has been closed.
         */
        protected boolean closed = false;
        /**
         * The delegated statement.
         */
        protected Object delegate;
        /**
         * The SQL query text.
         */
        protected final String query;
        /**
         * The pooled connection the statement was created from, as reported
         * by {@link java.sql.Statement#getConnection()}. It is the handle the
         * application borrowed, so it stops working once that borrow has been
         * returned, while the driver's own connection would have gone on
         * serving whoever borrowed the connection next.
         */
        protected final Object connection;

        /**
         * Constructs a StatementProxy.
         * @param parent the parent statement
         * @param query the SQL query text
         * @param connection the pooled connection the statement was created
         *                   from
         */
        public StatementProxy(Object parent, String query, Object connection) {
            this.delegate = parent;
            this.query = query;
            this.connection = connection;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
            // Read the state once: whether the borrow has ended must not
            // change while the call is being answered.
            boolean ended = borrowEnded;
            if (compare(CLOSE_VAL, method)) {
                if (delegate == null || ended) {
                    return null;
                }
            }
            if (compare(ISCLOSED_VAL, method)) {
                if (delegate == null || ended) {
                    return Boolean.TRUE;
                }
            }
            if (delegate == null) {
                throw new SQLException("Statement closed.");
            }
            if (ended) {
                // The delegate is still open, but it is open on a session
                // that belongs to the next borrower now.
                throw new SQLException("Statement closed. The pooled connection this "
                        + "statement was created from has been returned to the pool.");
            }
            // The connection a statement reports is the pooled handle the
            // application created it from, not the driver's own connection.
            // Forwarding the call instead hands the application a handle on
            // the physical session: it survives the borrow it came from and
            // bypasses every interceptor, so statements made through it
            // escape the close-on-return tracking and any statement cache.
            if (compare(GETCONNECTION_VAL, method)) {
                return connection;
            }

            Object result;
            try {
                // invoke next
                result = method.invoke(delegate, args);

                // Seal every result set the call hands back, whatever the
                // method that returned it was called. Matching the three
                // names the standard API gives them (executeQuery,
                // getResultSet, getGeneratedKeys) misses the result sets
                // declared to come back as an Object: a cursor OUT-parameter
                // reaches the application through
                // CallableStatement.getObject(...). The return type sees them
                // all, and a null result simply is no result set. The Wrapper
                // methods are kept out of this: unwrap() is the documented
                // way to reach the driver's own object, so what it hands back
                // stays the driver's object even when that object implements
                // ResultSet.
                if (result instanceof ResultSet
                        && method.getDeclaringClass() != Wrapper.class) {
                    return getConstructor(RESULTSET_IDX, ResultSet.class)
                            .newInstance(new ResultSetProxy(result, proxy));
                }
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            }
            // perform close cleanup
            if (compare(CLOSE_VAL, method)) {
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
            StringBuffer buf = new StringBuffer(StatementProxy.class.getName());
            buf.append("[Proxy=");
            buf.append(hashCode());
            buf.append("; Query=");
            buf.append(query);
            buf.append("; Delegate=");
            buf.append(delegate);
            buf.append(']');
            return buf.toString();
        }
    }

    /**
     * Proxy for a ResultSet.
     */
    protected class ResultSetProxy implements InvocationHandler {

        private final Object parent;
        private Object delegate;

        /**
         * Constructs a ResultSetProxy.
         * @param delegate the ResultSet to delegate to
         * @param parent the parent statement proxy
         */
        public ResultSetProxy(Object delegate, Object parent) {
            this.delegate = delegate;
            this.parent = parent;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
            // Read the state once: whether the borrow has ended must not
            // change while the call is being answered.
            boolean ended = borrowEnded;
            if (compare(CLOSE_VAL, method)) {
                if (delegate == null || ended) {
                    return null;
                }
            }
            if (compare(ISCLOSED_VAL, method)) {
                if (delegate == null || ended) {
                    return Boolean.TRUE;
                }
            }
            if (delegate == null) {
                throw new SQLException("ResultSet closed.");
            }
            if (ended) {
                // The cursor is still open, but it is open on a session that
                // belongs to the next borrower now.
                throw new SQLException("ResultSet closed. The pooled connection this "
                        + "result set was read from has been returned to the pool.");
            }

            if (compare(GET_STATEMENT, method)) {
                return parent;
            }

            Object result;
            try {
                // invoke next
                result = method.invoke(delegate, args);
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            }
            // perform close cleanup
            if (compare(CLOSE_VAL, method)) {
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
            return ResultSetProxy.class.getName() + "[Proxy=" + hashCode() + "; Delegate=" + delegate + ']';
        }
    }

}

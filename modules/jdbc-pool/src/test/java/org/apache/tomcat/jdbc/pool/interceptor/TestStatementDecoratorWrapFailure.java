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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * What the application is told when {@link StatementDecoratorInterceptor}
 * cannot wrap the result set a call just produced. The wrapping of the
 * result set of {@code executeQuery}, {@code getResultSet} or
 * {@code getGeneratedKeys} used to sit outside the dispatch loop's
 * exception translation, so a reflective failure there -
 * {@code NoSuchMethodException} from the proxy constructor lookup, an
 * {@code InstantiationException} or an {@code InvocationTargetException}
 * from the proxy instantiation - escaped the handler as a checked
 * exception the proxied methods never declare. The proxy machinery
 * wrapped it into an {@code UndeclaredThrowableException}, breaking the
 * declared {@code SQLException} contract the same dispatch loop is
 * careful to preserve for every other call. Worse, the driver cursor the
 * call had already opened was neither handed over nor closed: it was
 * stranded on the physical session, which the pool then handed to the
 * next borrower.
 *
 * <p>The pool now reports a failed wrap as the exception the proxied
 * method does declare, with the reflective failure as its cause, and
 * closes the cursor the call opened so a failed wrap cannot strand it.
 * The failure is about that one result set: the statement and the borrow
 * keep serving afterwards.</p>
 *
 * <p>The failure is injected through the class's own protected hook,
 * {@code getResultSetConstructor()}, by an interceptor subclass armed to
 * refuse the next wrap once; the driver cursor the query really opened is
 * captured by a recording wrapper around the driver statement, so the
 * assertions about the stranded cursor are about the driver's own object.</p>
 */
class TestStatementDecoratorWrapFailure extends JdbcPoolBaseTest {

    /*
     * A driver statement that records the result set each query returns,
     * so a test can ask the driver's own cursor whether the failed wrap
     * closed it. Every other call is forwarded unchanged.
     */
    private static final class RecordingCursor implements InvocationHandler {
        private final Statement delegate;
        private ResultSet lastReturned;

        private RecordingCursor(Statement delegate) {
            this.delegate = delegate;
        }

        /* The wrapper, on the statement interface the pool works with. */
        private Statement proxy() {
            Class<?> iface = CallableStatement.class.isAssignableFrom(delegate.getClass())
                    ? CallableStatement.class
                    : PreparedStatement.class.isAssignableFrom(delegate.getClass())
                            ? PreparedStatement.class
                            : Statement.class;
            return (Statement) Proxy.newProxyInstance(RecordingCursor.class.getClassLoader(),
                    new Class<?>[] { iface }, this);
        }

        private ResultSet lastReturned() {
            return lastReturned;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result;
            try {
                result = args == null ? method.invoke(delegate) : method.invoke(delegate, args);
            } catch (InvocationTargetException x) {
                throw x.getCause();
            }
            if ("executeQuery".equals(method.getName()) && result instanceof ResultSet) {
                lastReturned = (ResultSet) result;
            }
            return result;
        }
    }

    /*
     * The decorator with two hooks: while armed, the next statement it
     * decorates runs over a recording wrapper, and the next result set
     * wrap fails once with an injected NoSuchMethodException at the
     * class's own constructor hook. The pool builds interceptors
     * reflectively from the configured class name, so this must stay a
     * public static class with a no-arg constructor.
     */
    public static class WrapFailureStatementDecorator extends StatementDecoratorInterceptor {
        private boolean failNextWrap;
        private NoSuchMethodException injectedFailure;
        private RecordingCursor lastWrapped;

        /** Arms the hook: the next result set wrap fails, over a recorded cursor. */
        void failNextWrap() {
            failNextWrap = true;
        }

        /** The reflective failure the armed wrap reported, once it has fired. */
        NoSuchMethodException injectedFailure() {
            return injectedFailure;
        }

        /** The driver cursor the last query on a decorated statement returned. */
        ResultSet lastDriverCursor() {
            return lastWrapped == null ? null : lastWrapped.lastReturned();
        }

        @Override
        protected Object createDecorator(Object proxy, Method method, Object[] args, Object statement,
                Constructor<?> constructor, String sql)
                throws InstantiationException, IllegalAccessException, InvocationTargetException {
            lastWrapped = new RecordingCursor((Statement) statement);
            return super.createDecorator(proxy, method, args, lastWrapped.proxy(), constructor, sql);
        }

        @Override
        protected Constructor<?> getResultSetConstructor() throws NoSuchMethodException {
            if (failNextWrap) {
                failNextWrap = false;
                injectedFailure = new NoSuchMethodException("injected: the result set proxy constructor is gone");
                throw injectedFailure;
            }
            return super.getResultSetConstructor();
        }
    }

    @Test
    void aFailedResultSetWrapComesBackAsSQLExceptionAndClosesTheCursorItStranded() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(WrapFailureStatementDecorator.class.getName());
        ds.createPool();
        Connection pooled = ds.getConnection();
        try {
            WrapFailureStatementDecorator decorator =
                    interceptorOf(pooled, WrapFailureStatementDecorator.class);
            Statement st = pooled.createStatement();

            decorator.failNextWrap();
            SQLException thrown = Assertions.assertThrows(SQLException.class, () -> st.executeQuery("SELECT 1"),
                    "a result set the decorator cannot wrap must come back as the declared "
                    + "SQLException, not as an UndeclaredThrowableException from the proxy machinery");
            Assertions.assertSame(decorator.injectedFailure(), thrown.getCause(),
                    "the reflective failure must be reported as the cause of the SQLException");
            Assertions.assertEquals("Unable to create result set proxy.", thrown.getMessage());

            ResultSet cursor = decorator.lastDriverCursor();
            Assertions.assertNotNull(cursor, "the query must really have run at the driver");
            Assertions.assertTrue(cursor.isClosed(),
                    "the cursor the failed wrap left behind must be closed, not stranded on the session "
                    + "the next borrower will get");

            // The failure is about the one result set it was told about:
            // the statement and the borrow keep serving, and the next wrap
            // succeeds normally.
            ResultSet ok = st.executeQuery("SELECT 1");
            Assertions.assertTrue(ok.next());
            Assertions.assertEquals(1, ok.getInt(1));
            ok.close();
            st.close();
        } finally {
            pooled.close();
        }
    }
}

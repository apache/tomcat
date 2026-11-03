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
package org.apache.tomcat.jdbc.pool;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A blank validation query is the unset value, not a statement. Configuration
 * surfaces that cannot express {@code null} - a JNDI attribute, a
 * connection-properties line - are routinely written with an empty
 * {@code validationQuery} to mean "no query, run {@link Connection#isValid(int)}
 * instead", and an emptiness executed as SQL is at best a wasted round trip
 * and, on a driver that refuses empty statements, a validation that fails
 * every connection the pool touches. The setter collapses the blank the same
 * way {@link PoolProperties#setInitSQL(String)} does, and the validation path
 * reads a blank as unset even when it reaches the configuration around that
 * setter - through a {@link PoolConfiguration} implementation of the pool's
 * own, or as the explicit statement argument of
 * {@link PooledConnection#validate(int, String)}. The init SQL the pool runs
 * on a session it has just established is a statement of the same path and
 * reads the same way: a blank one means there is nothing to initialize the
 * session with, not an emptiness to execute.
 *
 * <p>The path witness is a recording {@link Connection}: the built-in check
 * goes through {@code isValid()} and the statement path goes through
 * {@code createStatement()}, so which half of the contract a case took is
 * counted, not guessed at through a driver's tolerance of the emptiness.</p>
 */
class TestBlankValidationQuery {

    /** Counts which half of the validation contract a connect's validation ran through. */
    private static final class Recording {
        int isValidCalls;
        int createStatementCalls;
        int executeCalls;
        String lastExecuted;
    }

    private static Connection recordingConnection(Recording rec) {
        return (Connection) Proxy.newProxyInstance(TestBlankValidationQuery.class.getClassLoader(),
                new Class<?>[] { Connection.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isValid" :
                            rec.isValidCalls++;
                            return Boolean.TRUE;
                        case "createStatement" :
                            rec.createStatementCalls++;
                            return recordingStatement(rec);
                        case "getAutoCommit" :
                            return Boolean.TRUE;
                        case "isClosed" :
                            return Boolean.FALSE;
                        case "equals" :
                            return proxy == args[0];
                        case "hashCode" :
                            return Integer.valueOf(System.identityHashCode(proxy));
                        case "toString" :
                            return "recording-connection";
                        default :
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Statement recordingStatement(Recording rec) {
        return (Statement) Proxy.newProxyInstance(TestBlankValidationQuery.class.getClassLoader(),
                new Class<?>[] { Statement.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "execute" :
                            rec.executeCalls++;
                            rec.lastExecuted = args == null || args.length == 0 ? null : (String) args[0];
                            return Boolean.TRUE;
                        case "equals" :
                            return proxy == args[0];
                        case "hashCode" :
                            return Integer.valueOf(System.identityHashCode(proxy));
                        case "toString" :
                            return "recording-statement";
                        default :
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class || type == Integer.class) {
            return Integer.valueOf(0);
        }
        if (type == long.class || type == Long.class) {
            return Long.valueOf(0);
        }
        if (type == short.class || type == Short.class) {
            return Short.valueOf((short) 0);
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.valueOf((byte) 0);
        }
        if (type == char.class || type == Character.class) {
            return Character.valueOf((char) 0);
        }
        if (type == double.class || type == Double.class) {
            return Double.valueOf(0);
        }
        if (type == float.class || type == Float.class) {
            return Float.valueOf(0);
        }
        return null;
    }

    /** A configuration handing out statements that never passed through the collapsing setters. */
    private static final class BypassedConfiguration extends PoolProperties {
        private final String rawQuery;
        private final String rawInitSql;

        BypassedConfiguration(String rawQuery, String rawInitSql) {
            this.rawQuery = rawQuery;
            this.rawInitSql = rawInitSql;
        }

        @Override
        public String getValidationQuery() {
            return rawQuery;
        }

        @Override
        public String getInitSQL() {
            return rawInitSql;
        }
    }

    /**
     * Applies the stand-in plumbing around a recording session and brings the
     * pool up on it. Whatever the pool reads while it starts - the initial
     * size, JMX - must already be set on the configuration when this runs.
     */
    private static ConnectionPool standInPool(PoolProperties props, Recording rec) throws Exception {
        props.setDataSource(Proxy.newProxyInstance(TestBlankValidationQuery.class.getClassLoader(),
                new Class<?>[] { javax.sql.DataSource.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getConnection" :
                            return recordingConnection(rec);
                        case "equals" :
                            return proxy == args[0];
                        case "hashCode" :
                            return Integer.valueOf(System.identityHashCode(proxy));
                        case "toString" :
                            return "recording-datasource";
                        default :
                            return defaultValue(method.getReturnType());
                    }
                }));
        props.setJmxEnabled(false);
        props.setTimeBetweenEvictionRunsMillis(-1);
        // Without this the interval suppression - by design - answers "true"
        // for the first seconds after construction and the validation never
        // reaches the calls this recorder watches.
        props.setValidationInterval(0);
        return new ConnectionPool(props);
    }

    private static PooledConnection connectedTo(PoolProperties props, Recording rec) throws Exception {
        props.setInitialSize(0);
        props.setTestOnBorrow(true);
        PooledConnection con = new PooledConnection(props, standInPool(props, rec));
        con.connect();
        return con;
    }

    @ParameterizedTest(name = "configured query [{0}] validates through isValid()")
    @ValueSource(strings = { "", " ", "\t" })
    void aBlankQueryReachingTheValidationAroundTheSetterIsNotExecuted(String blank) throws Exception {
        Recording rec = new Recording();
        // A PoolConfiguration implementation of the caller's own can hand out a
        // blank the pool's setter would never have stored.
        PooledConnection con = connectedTo(new BypassedConfiguration(blank, null), rec);

        Assertions.assertTrue(con.validate(PooledConnection.VALIDATE_BORROW),
                "a blank query means the built-in check, which the session passes");
        Assertions.assertEquals(1, rec.isValidCalls, "the built-in check is what must have run");
        Assertions.assertEquals(0, rec.createStatementCalls,
                "a blank must never be prepared and executed as a statement");
    }

    @Test
    void anExplicitBlankArgumentMeansNoArgument() throws Exception {
        Recording rec = new Recording();
        PoolProperties props = new PoolProperties();
        props.setValidationQuery("SELECT 1");
        PooledConnection con = connectedTo(props, rec);

        Assertions.assertTrue(con.validate(PooledConnection.VALIDATE_BORROW, ""));
        Assertions.assertEquals(1, rec.executeCalls, "the configured query still validates");
        Assertions.assertEquals("SELECT 1", rec.lastExecuted,
                "an empty argument reads as no argument, so it is the configured query that runs");
        Assertions.assertEquals(0, rec.isValidCalls);
    }

    @Test
    void anExplicitQueryArgumentIsStillExecuted() throws Exception {
        Recording rec = new Recording();
        PooledConnection con = connectedTo(new PoolProperties(), rec);

        Assertions.assertTrue(con.validate(PooledConnection.VALIDATE_BORROW, "SELECT 42"));
        Assertions.assertEquals(1, rec.executeCalls);
        Assertions.assertEquals("SELECT 42", rec.lastExecuted);
        Assertions.assertEquals(0, rec.isValidCalls,
                "a caller that names a statement gets that statement, not the built-in check");
    }

    @ParameterizedTest(name = "init SQL [{0}] is not executed as a statement")
    @ValueSource(strings = { "", " ", "\t" })
    void aBlankInitSqlReachingTheValidationAroundTheSetterIsNotExecuted(String blank) throws Exception {
        Recording rec = new Recording();
        PooledConnection con = connectedTo(new BypassedConfiguration(null, blank), rec);

        Assertions.assertTrue(con.validate(PooledConnection.VALIDATE_INIT),
                "a blank init SQL means the unset value, and a session with nothing to run needs no check");
        Assertions.assertEquals(0, rec.executeCalls,
                "an emptiness must not be executed as the session's initialization");
        Assertions.assertEquals(0, rec.isValidCalls,
                "with the init statement unset and no test on connect configured, there is nothing to run");
    }

    @Test
    void aBlankInitSqlLeavesTheBuiltInCheckToRunWhenConnectTestsTheSession() throws Exception {
        Recording rec = new Recording();
        BypassedConfiguration props = new BypassedConfiguration(null, "   ");
        props.setTestOnConnect(true);
        PooledConnection con = connectedTo(props, rec);

        Assertions.assertTrue(con.validate(PooledConnection.VALIDATE_INIT),
                "the built-in check the unset statement means, against a session that is up");
        Assertions.assertEquals(1, rec.isValidCalls, "the built-in check is what the init action ran");
        Assertions.assertEquals(0, rec.executeCalls, "and no emptiness alongside it");
    }

    @Test
    void aConfiguredInitSqlIsStillExecutedOnConnect() throws Exception {
        Recording rec = new Recording();
        PoolProperties props = new PoolProperties();
        props.setInitSQL("SET SESSION MARK 7");
        PooledConnection con = connectedTo(props, rec);

        Assertions.assertTrue(con.validate(PooledConnection.VALIDATE_INIT));
        Assertions.assertEquals(1, rec.executeCalls);
        Assertions.assertEquals("SET SESSION MARK 7", rec.lastExecuted,
                "an init SQL that carries something is still the session's initialization");
        Assertions.assertEquals(0, rec.isValidCalls, "a named init statement is what runs, not the built-in check");
    }

    @ParameterizedTest(name = "setter collapses [{0}] to unset")
    @ValueSource(strings = { "", " ", "\t" })
    void theSetterStoresABlankQueryAsTheUnsetValue(String blank) {
        PoolProperties props = new PoolProperties();
        props.setValidationQuery("SELECT 1");
        props.setValidationQuery(blank);
        Assertions.assertNull(props.getValidationQuery(),
                "the blank must read back as the unset value, the way setInitSQL collapses it");
    }

    @Test
    void theSetterKeepsAConfiguredQueryVerbatim() {
        PoolProperties props = new PoolProperties();
        props.setValidationQuery(" SELECT 1 FROM DUAL ");
        Assertions.assertEquals(" SELECT 1 FROM DUAL ", props.getValidationQuery(),
                "a query that carries anything is stored exactly as written");
    }

    @Test
    void aBorrowThatReconnectsStillRunsTheConfiguredBorrowCheck() throws Exception {
        Recording rec = new Recording();
        BypassedConfiguration props = new BypassedConfiguration("SELECT 1", "   ");
        props.setTestOnBorrow(true);
        props.setInitialSize(1);
        // The session goes stale the moment the pool makes it, so the borrow
        // below takes the reconnect path - the path on which the pool decides
        // between the init check and the borrow check by whether an init
        // statement is configured. A blank one must not read as configured.
        props.setMaxAge(1);
        ConnectionPool pool = standInPool(props, rec);
        Thread.sleep(5);
        int executedBefore = rec.executeCalls;

        // The witness is read before the connection goes back: at a maxAge of
        // a millisecond the return path reconnects the session it is handed,
        // and that hand-off is no more this test's subject than the borrow's.
        Connection c = pool.getConnection();
        int executedByBorrow = rec.executeCalls;
        c.close();

        Assertions.assertNotNull(c, "the borrow must succeed");
        Assertions.assertEquals(executedBefore + 1, executedByBorrow,
                "a blank init SQL leaves the configured borrow check standing, reconnect path and all");
        Assertions.assertEquals("SELECT 1", rec.lastExecuted);
    }

    @Test
    void aReconnectOnBorrowStillRunsTheConnectCheckWhenThatIsWhatIsConfigured() throws Exception {
        Recording rec = new Recording();
        // Nothing but the blank init statement and a test on connect: the
        // re-established session is still due its check, the built-in one.
        BypassedConfiguration props = new BypassedConfiguration(null, "   ");
        props.setTestOnConnect(true);
        props.setInitialSize(1);
        props.setMaxAge(1);
        ConnectionPool pool = standInPool(props, rec);
        Thread.sleep(5);
        int checkedBefore = rec.isValidCalls;

        // Read before the return, for the same reason as the test above.
        Connection c = pool.getConnection();
        int checkedByBorrow = rec.isValidCalls;
        c.close();

        Assertions.assertNotNull(c, "the borrow must succeed");
        Assertions.assertEquals(checkedBefore + 1, checkedByBorrow,
                "a configured test on connect keeps checking the re-established session");
        Assertions.assertEquals(0, rec.executeCalls, "and no emptiness runs alongside it");
    }
}

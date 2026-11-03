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
 * {@link PooledConnection#validate(int, String)}.
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

    /** A configuration handing out a query that never passed through the collapsing setter. */
    private static final class BypassedConfiguration extends PoolProperties {
        private final String rawQuery;

        BypassedConfiguration(String rawQuery) {
            this.rawQuery = rawQuery;
        }

        @Override
        public String getValidationQuery() {
            return rawQuery;
        }
    }

    /**
     * A connection that is up against a stand-in {@link javax.sql.DataSource}
     * handing out the recording connection - the connect genuinely runs, but
     * every call the validation makes lands in the recorder.
     */
    private static PooledConnection connectedTo(PoolProperties props, Recording rec) throws Exception {
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
        props.setInitialSize(0);
        props.setTimeBetweenEvictionRunsMillis(-1);
        // Without this the interval suppression - by design - answers "true"
        // for the first seconds after construction and the validation never
        // reaches the calls this recorder watches.
        props.setValidationInterval(0);
        props.setTestOnBorrow(true);
        PooledConnection con = new PooledConnection(props, new ConnectionPool(props));
        con.connect();
        return con;
    }

    @ParameterizedTest(name = "configured query [{0}] validates through isValid()")
    @ValueSource(strings = { "", " ", "\t" })
    void aBlankQueryReachingTheValidationAroundTheSetterIsNotExecuted(String blank) throws Exception {
        Recording rec = new Recording();
        // A PoolConfiguration implementation of the caller's own can hand out a
        // blank the pool's setter would never have stored.
        PooledConnection con = connectedTo(new BypassedConfiguration(blank), rec);

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
}

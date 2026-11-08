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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * A throwing {@code closeInvoked()} hook must not strand the borrow: the
 * close dispatch runs the hook before handing the call down the chain, so an
 * unguarded throw used to short-circuit the chain terminal, the close threw
 * at the application over an already-disposed handle and the session stayed
 * busy - permanently so without the abandoned-connection reaper, which is not
 * on by default. The base class now isolates the hook the way it isolates the
 * reset and disconnected fan-outs, and the close travels on.
 */
class TestCloseInvokedFailureIsolated extends JdbcPoolBaseTest {

    /**
     * Interceptor that always throws from its borrow-end hook, simulating a
     * third-party subclass that fails unexpectedly. Configured through the
     * jdbcInterceptors class-name list, so it must stay a public static class
     * with a no-arg constructor.
     */
    public static class ThrowingCloseHook extends SlowQueryReport {
        @Override
        public void closeInvoked() {
            throw new IllegalStateException("close hook intentionally fails");
        }
    }

    /* maxWait stays short so a stranded borrow fails the test in seconds. */
    private DataSource throwingHookPool(String interceptors) throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxWait(2000);
        ds.getPoolProperties().setJdbcInterceptors(interceptors);
        ds.createPool();
        return ds;
    }

    @Test
    void throwingCloseHookStillReturnsTheSessionToThePool() throws Exception {
        DataSource ds = throwingHookPool(ThrowingCloseHook.class.getName());
        ConnectionPool pool = ds.getPool();

        Connection c = ds.getConnection();
        try (Statement st = c.createStatement()) {
            st.execute("SELECT 1");
        }
        Assertions.assertDoesNotThrow(c::close,
                "a failing close hook must not fail the application's close()");

        Assertions.assertEquals(0, pool.getActive(),
                "the borrow must end even though the hook threw");
        Assertions.assertEquals(1, pool.getIdle(),
                "the session must be back in the idle queue");

        // And the slot is genuinely free: the next borrow is served.
        try (Connection next = ds.getConnection(); Statement st = next.createStatement()) {
            st.execute("SELECT 1");
        }
    }

    /*
     * The isolation must not reorder the fan-out or spare the pool: a hook
     * failing below a StatementFinalizer still leaves the borrow ended, the
     * same property the always-configured StatementFacade and the other
     * in-tree hooks rely on.
     */
    @Test
    void closeHookFailingBeneathTheStatementFinalizerStillEndsTheBorrow() throws Exception {
        DataSource ds = throwingHookPool("org.apache.tomcat.jdbc.pool.interceptor.StatementFinalizer;"
                + ThrowingCloseHook.class.getName());
        ConnectionPool pool = ds.getPool();

        Connection c = ds.getConnection();
        try (Statement st = c.createStatement()) {
            st.execute("SELECT 1");
        }
        Assertions.assertDoesNotThrow(c::close,
                "a failing hook below the finalizer must not fail the close either");

        Assertions.assertEquals(0, pool.getActive(),
                "the borrow must end whatever the lower hook made of it");
        Assertions.assertEquals(1, pool.getIdle());
    }
}

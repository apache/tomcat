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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * An aborted connection is closed, so it must give its slot back to the pool and must not be handed out again.
 */
class TestConnectionAbort extends JdbcPoolBaseTest {

    private DataSource pool() {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        return ds;
    }

    @Test
    void abortReleasesTheConnection() throws Exception {
        DataSource ds = pool();
        Connection con = ds.getConnection();

        con.abort(Runnable::run);

        assertTrue(con.isClosed());
        assertEquals(0, ds.getActive());
        awaitSessions(0);
    }

    @Test
    void abortedConnectionIsNotReused() throws Exception {
        DataSource ds = pool();
        Connection first = ds.getConnection();
        setSessionMarker(first, 7);
        first.abort(Runnable::run);

        try (Connection second = ds.getConnection()) {
            assertNotEquals(Integer.valueOf(7), sessionMarker(second),
                    "an aborted session must not be handed out again");
        }
    }

    @Test
    void abortWithoutDisposableFacade() throws Exception {
        DataSource ds = pool();
        ds.getPoolProperties().setUseDisposableConnectionFacade(false);
        Connection con = ds.getConnection();

        con.abort(Runnable::run);
        assertTrue(con.isClosed());
        assertEquals(0, ds.getActive());

        con.abort(Runnable::run);
        assertEquals(0, ds.getActive());
    }

    @Test
    void abortOnClosedConnectionDoesNothing() throws Exception {
        DataSource ds = pool();
        Connection con = ds.getConnection();
        con.close();

        con.abort(Runnable::run);

        assertTrue(con.isClosed());
        assertEquals(0, ds.getActive());
        assertEquals(1, ds.getIdle());
    }

    /*
     * The pool can disconnect a pooled connection while a call is in progress, which leaves the pooled connection
     * without a physical connection.
     */
    @Test
    void abortWithoutPhysicalConnection() throws Throwable {
        PoolProperties properties = new PoolProperties();
        properties.setInitialSize(0);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        ConnectionPool pool = new ConnectionPool(properties);
        PooledConnection pooled = new PooledConnection(properties, pool);
        assertNull(pooled.getConnection());
        ProxyConnection proxy = new ProxyConnection(pool, pooled, false);
        Method abort = Connection.class.getMethod("abort", Executor.class);

        Object result = proxy.invoke(null, abort, new Object[] { (Executor) Runnable::run });

        assertNull(result);
        assertTrue(proxy.isClosed());
    }
}

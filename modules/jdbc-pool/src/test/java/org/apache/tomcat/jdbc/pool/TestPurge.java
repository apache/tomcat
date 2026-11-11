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

import java.sql.Connection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected functionality of {@code purge()} and {@code purgeOnReturn()}:
 * a pool can be told that the sessions it currently holds are stale, and
 * from that moment on they are physically closed instead of handed to the
 * next borrower - while the borrowers that already hold one are not
 * disturbed. Each pooled connection remembers the pool generation it was
 * created in; both operations start a new generation, and a connection
 * from an older one is closed when it comes back. {@code purge()} also
 * does it to the connections that are idle right now,
 * {@code purgeOnReturn()} waits for them to be returned. Scenario
 * harvested from the retired legacy {@code PoolPurgeTest}, asserted here
 * with the database's session count.
 */
class TestPurge extends JdbcPoolBaseTest {

    @Test
    void purgeClosesIdleSessionsAndDiscardsTheNextReturn() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(3);
        ds.getPoolProperties().setMaxActive(3);
        ds.createPool();
        awaitSessions(3);

        Connection inUse = ds.getConnection();
        for (int i = 0; i < 2; i++) {
            ds.getConnection().close();
        }
        Assertions.assertEquals(3, ds.getSize());
        Assertions.assertEquals(2, ds.getIdle());

        ds.purge();

        Assertions.assertEquals(0, ds.getIdle(), "purge must drop every idle connection");
        Assertions.assertEquals(1, ds.getSize(),
                "only the borrowed connection may survive a purge");
        awaitSessions(1);

        // the borrower is untouched by the purge...
        inUse.createStatement().execute("SELECT 1");
        // ...but its session is stale, so returning it closes it
        inUse.close();
        Assertions.assertEquals(0, ds.getIdle(),
                "a session older than the purge must not be repooled");
        Assertions.assertEquals(0, ds.getSize());
        awaitSessions(0);
    }

    @Test
    void purgeOnReturnDiscardsTheSessionsEstablishedBeforeIt() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(2);
        ds.getPoolProperties().setMaxActive(2);
        ds.createPool();
        awaitSessions(2);

        Connection held = ds.getConnection();
        Assertions.assertEquals(1, ds.getIdle());

        ds.purgeOnReturn();

        Assertions.assertEquals(1, ds.getIdle(),
                "purgeOnReturn must not touch idle connections, unlike purge()");
        Assertions.assertEquals(2, ds.getSize());
        Assertions.assertEquals(2, poolSessions());

        // both pooled sessions predate the purge, so the return of the one
        // in use closes it instead of repooling it
        held.close();
        Assertions.assertEquals(1, ds.getIdle(), "the untouched idle session stays available");
        Assertions.assertEquals(1, ds.getSize());
        awaitSessions(1);

        // the other pre-purge session is discarded on its own return as well
        Connection stale = ds.getConnection();
        stale.close();
        Assertions.assertEquals(0, ds.getSize(),
                "every session established before the purge must end up closed");
        awaitSessions(0);

        // a session established after the purge belongs to the new
        // generation and is pooled as usual
        try (Connection fresh = ds.getConnection()) {
            Assertions.assertTrue(fresh.createStatement().execute("SELECT 1"));
        }
        Assertions.assertEquals(1, ds.getSize(),
                "a session established after the purge must be pooled");
        Assertions.assertEquals(1, ds.getIdle());
        awaitSessions(1);
    }
}

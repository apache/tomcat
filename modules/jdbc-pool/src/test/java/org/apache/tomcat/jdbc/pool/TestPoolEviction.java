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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected pool sizing behaviour: idle capping on return, the min-idle
 * floor under aggressive eviction, max-age rotation, and idle
 * validation keeping connections alive. Observed exclusively through
 * physical sessions on the database.
 */
class TestPoolEviction extends JdbcPoolBaseTest {

    @Test
    void excessIdleConnectionsAreClosedOnReturnDownToMaxIdle() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(5);
        ds.getPoolProperties().setMaxIdle(2);
        // sweeper disabled: the maxIdle cap must be enforced by the
        // return path itself, not by a background thread
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);

        // open 5 concurrent physical connections, then return them all
        List<Connection> opened = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            opened.add(ds.getConnection());
        }
        Assertions.assertEquals(5, poolSessions());
        for (Connection c : opened) {
            c.close();
        }
        awaitSessions(2);
        // pool still functional after the shrink
        try (Connection c = ds.getConnection()) {
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next());
            }
        }
    }

    @Test
    void minIdleFloorSurvivesAggressiveEviction() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(3);
        ds.getPoolProperties().setMinIdle(3);
        ds.getPoolProperties().setMaxIdle(5);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(200);
        ds.getPoolProperties().setMinEvictableIdleTimeMillis(100);
        ds.createPool();
        Assertions.assertEquals(3, poolSessions());

        // several eviction rounds over well past the idle threshold
        sleep(1500);
        Assertions.assertEquals(3, poolSessions(),
                "connections at the min-idle floor must not be evicted");

        try (Connection c = ds.getConnection()) {
            Assertions.assertTrue(c.isValid(2));
        }
    }

    @Test
    void maxAgeRetiresAnOldConnectionBetweenBorrowers() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxAge(1500);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(200);

        Connection first = ds.getConnection();
        setSessionMarker(first, 99);
        first.close();
        // within maxAge the very same physical session is reused
        try (Connection same = ds.getConnection()) {
            Integer marker = sessionMarker(same);
            Assertions.assertNotNull(marker, "young connection must be reused");
            Assertions.assertEquals(99, marker.intValue());
            same.close();
        }

        // past maxAge the connection is retired and a fresh one takes its
        // place (sweeper and/or borrow-time check), keeping the pool size
        sleep(3500); // ~2x maxAge plus sweeper interval
        awaitSessions(1);
        try (Connection replacement = ds.getConnection()) {
            Assertions.assertNull(sessionMarker(replacement),
                    "after maxAge the pool must not hand out the old session");
            try (Statement st = replacement.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next());
            }
        }
        Assertions.assertEquals(1, poolSessions());
    }

    @Test
    void testWhileIdleKeepsConnectionsAliveAndUsable() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(2);
        ds.getPoolProperties().setMinIdle(2);
        ds.getPoolProperties().setTestWhileIdle(true);
        ds.getPoolProperties().setValidationQuery("SELECT 1");
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(200);
        ds.getPoolProperties().setMinEvictableIdleTimeMillis(100);
        ds.createPool();

        sleep(1500); // many validation rounds
        Assertions.assertEquals(2, poolSessions(),
                "validated idle connections must be kept, not replaced");

        try (Connection a = ds.getConnection(); Connection b = ds.getConnection()) {
            try (Statement st = a.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next());
            }
            try (Statement st = b.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next());
            }
        }
    }

    @Test
    void agedIdleConnectionIsEvictedWhileBusyKeepsSizeAboveMinIdle()
            throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMinIdle(1);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(200);
        ds.getPoolProperties().setMinEvictableIdleTimeMillis(100);
        ds.createPool();

        // one busy connection above the min-idle floor: the pool is over the
        // floor by total size, yet idle never exceeds the floor, so the
        // cleaner's guard must not skip the eviction sweep
        Connection hold = ds.getConnection();
        try {
            Connection extra = ds.getConnection();
            Assertions.assertEquals(2, poolSessions());
            extra.close(); // aged after 100 ms; evictable while size(2) > minIdle(1)

            awaitSessions(1); // only the busy connection's session may survive
        } finally {
            // release the busy slot even if the eviction assertion above
            // failed, so a red run cannot pollute the shared server; the
            // returned connection then sits at the min-idle floor, where it
            // must stay and where the teardown closes it
            hold.close();
        }
    }

    @Test
    void borrowAfterEvictionCycleYieldsFreshConnection() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(200);
        ds.getPoolProperties().setMinEvictableIdleTimeMillis(300);

        Connection c1 = ds.getConnection();
        setSessionMarker(c1, 7);
        c1.close();
        Integer markerBeforeEviction = null;
        try (Connection probe = ds.getConnection()) {
            markerBeforeEviction = sessionMarker(probe);
        }
        Assertions.assertNotNull(markerBeforeEviction,
                "quick re-borrow must reuse the idle connection");

        awaitSessions(0); // idle eviction retires it (age > 300ms, minIdle 0)

        try (Connection c2 = ds.getConnection()) {
            Assertions.assertNull(sessionMarker(c2),
                    "after eviction the pool must hand out a fresh connection");
            Integer marker = sessionMarker(c2);
            Assertions.assertNull(marker);
            setSessionMarker(c2, 8);
            Assertions.assertEquals(8, sessionMarker(c2).intValue());
        }
    }
}

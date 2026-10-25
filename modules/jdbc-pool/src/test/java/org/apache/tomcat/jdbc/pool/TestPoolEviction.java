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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        assertEquals(5, poolSessions());
        for (Connection c : opened) {
            c.close();
        }
        awaitSessions(2);
        // pool still functional after the shrink
        try (Connection c = ds.getConnection()) {
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
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
        assertEquals(3, poolSessions());

        // several eviction rounds over well past the idle threshold
        sleep(1500);
        assertEquals(3, poolSessions(),
                "connections at the min-idle floor must not be evicted");

        try (Connection c = ds.getConnection()) {
            assertTrue(c.isValid(2));
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
            assertNotNull(marker, "young connection must be reused");
            assertEquals(99, marker.intValue());
            same.close();
        }

        // past maxAge the connection is retired and a fresh one takes its
        // place (sweeper and/or borrow-time check), keeping the pool size
        sleep(3500); // ~2x maxAge plus sweeper interval
        awaitSessions(1);
        try (Connection replacement = ds.getConnection()) {
            assertNull(sessionMarker(replacement),
                    "after maxAge the pool must not hand out the old session");
            try (Statement st = replacement.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
            }
        }
        assertEquals(1, poolSessions());
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
        assertEquals(2, poolSessions(),
                "validated idle connections must be kept, not replaced");

        try (Connection a = ds.getConnection(); Connection b = ds.getConnection()) {
            try (Statement st = a.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
            }
            try (Statement st = b.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
            }
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
        assertNotNull(markerBeforeEviction,
                "quick re-borrow must reuse the idle connection");

        awaitSessions(0); // idle eviction retires it (age > 300ms, minIdle 0)

        try (Connection c2 = ds.getConnection()) {
            assertNull(sessionMarker(c2),
                    "after eviction the pool must hand out a fresh connection");
            Integer marker = sessionMarker(c2);
            assertNull(marker);
            setSessionMarker(c2, 8);
            assertEquals(8, sessionMarker(c2).intValue());
        }
    }
}

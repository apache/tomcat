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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Expected functionality of executing statements (DDL, DML, queries,
 * batches, calls) through pooled connections against the real database.
 */
class TestStatementExecution extends JdbcPoolBaseTest {

    @Test
    void ddlDmlSelectRoundTrip() throws Exception {
        DataSource ds = newDataSource();
        String t = uniqueTable();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + t + " (id INT PRIMARY KEY, name VARCHAR(32))");
            assertEquals(1, st.executeUpdate(
                    "INSERT INTO " + t + " VALUES (1, 'hello')"));
            try (ResultSet rs = st.executeQuery(
                    "SELECT id, name FROM " + t + " WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("id"));
                assertEquals("hello", rs.getString("name"));
                assertFalse(rs.next(), "exactly one row");
            }
        }
        assertEquals(1, adminRowCount(t), "auto-commit DML must be committed");
    }

    @Test
    void preparedStatementWithParametersAndGeneratedKeys() throws Exception {
        DataSource ds = newDataSource();
        String t = uniqueTable();
        try (Connection c = ds.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE " + t
                        + " (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(32))");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t + " (name) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "first");
                assertEquals(1, ps.executeUpdate());
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    assertEquals(1, keys.getInt(1));
                }
                ps.setString(1, "second");
                assertEquals(1, ps.executeUpdate());
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    assertEquals(2, keys.getInt(1));
                }
            }
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery(
                            "SELECT name FROM " + t + " WHERE id = 2")) {
                assertTrue(rs.next());
                assertEquals("second", rs.getString(1));
            }
        }
        assertEquals(2, adminRowCount(t));
    }

    @Test
    void batchExecution() throws Exception {
        DataSource ds = newDataSource();
        String t = uniqueTable();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
            st.addBatch("INSERT INTO " + t + " VALUES (1)");
            st.addBatch("INSERT INTO " + t + " VALUES (2)");
            st.addBatch("INSERT INTO " + t + " VALUES (3)");
            assertArrayEquals(new int[] { 1, 1, 1 }, st.executeBatch());

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t + " VALUES (?)")) {
                for (int i = 4; i <= 6; i++) {
                    ps.setInt(1, i);
                    ps.addBatch();
                }
                int[] counts = ps.executeBatch();
                assertEquals(3, counts.length);
                for (int count : counts) {
                    assertEquals(1, count);
                }
            }
        }
        assertEquals(6, adminRowCount(t));
    }

    @Test
    void updateCountsReflectAffectedRows() throws Exception {
        DataSource ds = newDataSource();
        String t = uniqueTable();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
            st.execute("INSERT INTO " + t + " VALUES (1),(2),(3)");
            assertEquals(3, st.executeUpdate("UPDATE " + t + " SET v = v + 10"));
            try (ResultSet rs = st.executeQuery(
                    "SELECT MIN(v), MAX(v) FROM " + t)) {
                assertTrue(rs.next());
                assertEquals(11, rs.getInt(1));
                assertEquals(13, rs.getInt(2));
            }
            assertEquals(2, st.executeUpdate("DELETE FROM " + t + " WHERE v < 13"));
            assertEquals(1, adminRowCount(t));
        }
    }

    @Test
    void resultSetMetadataAndNavigation() throws Exception {
        DataSource ds = newDataSource();
        String t = uniqueTable();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + t + " (id INT, label VARCHAR(10))");
            st.execute("INSERT INTO " + t +
                    " SELECT X, 'row' || X FROM SYSTEM_RANGE(1, 10)");
            try (ResultSet rs = st.executeQuery(
                    "SELECT id, label FROM " + t + " ORDER BY id")) {
                ResultSetMetaData md = rs.getMetaData();
                assertEquals(2, md.getColumnCount());
                int idColumn = rs.findColumn("LABEL");
                assertTrue(idColumn >= 1);
                int sum = 0;
                int rows = 0;
                while (rs.next()) {
                    sum += rs.getInt("id");
                    assertEquals("row" + rs.getInt("id"), rs.getString("label"));
                    rows++;
                }
                assertEquals(10, rows);
                assertEquals(55, sum);
            }
        }
    }

    @Test
    void setMaxRowsLimitsResultSets() throws Exception {
        DataSource ds = newDataSource();
        String t = uniqueTable();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE " + t + " (v INT)");
            st.execute("INSERT INTO " + t +
                    " SELECT X FROM SYSTEM_RANGE(1, 10)");
            st.setMaxRows(4);
            try (ResultSet rs = st.executeQuery("SELECT v FROM " + t)) {
                int rows = 0;
                while (rs.next()) {
                    rows++;
                }
                assertEquals(4, rows);
            }
        }
    }

    @Test
    void failedStatementLeavesConnectionUsable() throws Exception {
        DataSource ds = newDataSource();
        String t = uniqueTable();
        try (Connection c = ds.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE " + t + " (v INT)");
            }
            try (Statement bad = c.createStatement()) {
                assertThrows(SQLException.class,
                        () -> bad.executeQuery("SELECT no_such_column FROM " + t));
            }
            // the connection must survive a failed statement
            assertTrue(c.isValid(2));
            try (Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
            }
            try (Statement st = c.createStatement()) {
                assertEquals(1, st.executeUpdate(
                        "INSERT INTO " + t + " VALUES (1)"));
            }
        }
        assertEquals(1, adminRowCount(t));
    }

    @Test
    void callableStatementInvocation() throws Exception {
        DataSource ds = newDataSource();
        try (Connection c = ds.getConnection();
                CallableStatement cs = c.prepareCall("{call ROUND(2.5, 0)}")) {
            try (ResultSet rs = cs.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(3.0, rs.getDouble(1), 0.0001);
            }
        }
    }

    @Test
    void multipleStatementsPerBorrowAreIndependent() throws Exception {
        DataSource ds = newDataSource();
        String t = uniqueTable();
        try (Connection c = ds.getConnection()) {
            Statement one = c.createStatement();
            Statement two = c.createStatement();
            try {
                one.execute("CREATE TABLE " + t + " (v INT)");
                two.execute("INSERT INTO " + t + " VALUES (1)");
                one.close();
                // closing one statement must not affect the other
                try (ResultSet rs = two.executeQuery("SELECT COUNT(*) FROM " + t)) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }
            } finally {
                one.close();
                two.close();
            }
        }
        assertEquals(1, adminRowCount(t));
    }

}

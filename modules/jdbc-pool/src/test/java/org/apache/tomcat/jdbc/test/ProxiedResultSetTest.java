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
package org.apache.tomcat.jdbc.test;

import org.apache.tomcat.jdbc.test.driver.Driver;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ProxiedResultSetTest extends DefaultTestCase {

    @Test
    public void shouldReturnWrappedOwningPreparedStatementFromExecuteQueryResultSet() throws Exception {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             PreparedStatement statement = con.prepareStatement("");
             ResultSet resultSet = statement.executeQuery()) {
            assertEquals(statement, resultSet.getStatement());
        }
    }

    @Test
    public void shouldReturnWrappedOwningStatementFromGetGeneratedKeyResultSet() throws Exception {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             Statement statement = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             ResultSet resultSet = statement.getGeneratedKeys()) {
            assertEquals(statement, resultSet.getStatement());
        }
    }

    @Test
    public void shouldReturnWrappedOwningStatementFromExecuteQuery() throws Exception {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             Statement statement = con.createStatement();
             ResultSet resultSet = statement.executeQuery("")) {
            assertEquals(statement, resultSet.getStatement());
        }
    }

    @Test
    public void shouldReturnWrappedOwningStatementForGetResultSet() throws Exception {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             Statement statement = con.createStatement();
             ResultSet resultSet = statement.getResultSet()) {
            assertEquals(statement, resultSet.getStatement());
        }
    }

    @Test
    public void shouldReturnCorrectClosedStatus() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = datasource.getConnection();
             Statement statement = con.createStatement();
             ResultSet resultSet = statement.getResultSet()) {
            assertFalse(resultSet.isClosed());
            resultSet.close();
            assertTrue(resultSet.isClosed());
        }
    }

    @Test
    public void shouldReturnHashcodeRegardlessOfClosedStatus() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = datasource.getConnection();
             Statement statement = con.createStatement();
             ResultSet resultSet = statement.getResultSet()) {
            int hashcode = resultSet.hashCode();
            resultSet.close();
            assertEquals(hashcode, resultSet.hashCode());
        }
    }

    @Test
    public void shouldReturnEqualsRegardlessOfClosedStatus() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = datasource.getConnection();
             Statement statement = con.createStatement();
             ResultSet resultSet = statement.getResultSet()) {
            assertNotEquals(resultSet, "");
            assertEquals(resultSet, resultSet);
            resultSet.close();
            assertNotEquals(resultSet, "");
            assertEquals(resultSet, resultSet);
        }
    }


    @Test
    public void shouldReturnToStringRegardlessOfClosedStatus() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = datasource.getConnection();
             Statement statement = con.createStatement();
             ResultSet resultSet = statement.getResultSet()) {
            String toStringResult = resultSet.toString();
            resultSet.close();
            // the delegate will change, so we can't compare the whole string
            assertEquals(toStringResult.substring(0, 50), resultSet.toString().substring(0, 50));
        }
    }


    @Test
    public void shouldReturnFalseForNullEqualsComparison() throws Exception {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             PreparedStatement statement = con.prepareStatement("sql");
             ResultSet resultSet = statement.executeQuery()) {
            assertNotEquals(resultSet, null);
        }
    }

    @Test
    public void shouldReturnFalseForDifferentObjectTypeEqualsComparison() throws Exception {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             PreparedStatement statement = con.prepareStatement("sql");
             ResultSet resultSet = statement.executeQuery()) {
            assertNotEquals(resultSet, "");
        }
    }

    @Test
    public void shouldReturnFalseForNonProxiedResultSetEqualsComparison() throws Exception {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             PreparedStatement statement = con.prepareStatement("sql");
             ResultSet resultSet = statement.executeQuery()) {
            assertNotEquals(resultSet, new org.apache.tomcat.jdbc.test.driver.ResultSet(statement));
        }
    }

    @Test
    public void shouldReturnTrueForSameProxiedResultSetEqualsComparison() throws Exception {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             PreparedStatement statement = con.prepareStatement("sql");
             ResultSet resultSet = statement.executeQuery()) {
            assertEquals(resultSet, resultSet);
        }
    }

    @Test
    public void shouldReturnFalseForNonEqualProxiedResultSetEqualsComparison() throws Exception {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             PreparedStatement statement = con.prepareStatement("sql");
             ResultSet resultSet = statement.executeQuery();
             Connection con2 = this.datasource.getConnection();
             PreparedStatement statement2 = con2.prepareStatement("sql");
             ResultSet resultSet2 = statement2.executeQuery()) {
            assertNotEquals(resultSet, resultSet2);
        }
    }
}

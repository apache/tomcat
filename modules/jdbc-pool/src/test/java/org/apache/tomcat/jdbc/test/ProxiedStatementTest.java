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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.jdbc.test.driver.Driver;

public class ProxiedStatementTest extends DefaultTestCase {

    @Test
    public void shouldReturnFalseForNullEqualsComparison() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
                PreparedStatement statement = con.prepareStatement("sql")) {
            Assert.assertNotEquals(statement, null);
        }
    }

    @Test
    public void shouldReturnFalseForDifferentObjectTypeEqualsComparison() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
                Statement statement = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            Assert.assertNotEquals(statement, "");
        }
    }

    @Test
    public void shouldReturnFalseForNonProxiedResultSetEqualsComparison() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
                Statement statement = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            Assert.assertNotEquals(statement, new org.apache.tomcat.jdbc.test.driver.Statement());
        }
    }

    @Test
    public void shouldUnwrapInvocationTargetExceptionFromGetResultSet() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             Statement statement = con.prepareStatement("sql", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT)) {
            Assert.assertThrows("Throwing exception on execute", SQLException.class, statement::getResultSet);
        }
    }

    @Test
    public void shouldUnwrapInvocationTargetExceptionFromExecute() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             Statement statement = con.prepareStatement("sql", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT)) {
            Assert.assertThrows("Throwing exception on execute", SQLException.class, () -> statement.executeQuery(""));
        }
    }

    @Test
    public void shouldUnwrapInvocationTargetExceptionFromGetGeneratedKeys() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             Statement statement = con.prepareStatement("sql", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT)) {
            Assert.assertThrows("Throwing exception on execute", SQLException.class, statement::getGeneratedKeys);
        }
    }
}

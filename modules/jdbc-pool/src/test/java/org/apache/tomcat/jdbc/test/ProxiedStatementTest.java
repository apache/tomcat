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

import static org.junit.Assert.assertNotEquals;

public class ProxiedStatementTest extends DefaultTestCase {

    @Test
    public void shouldReturnFalseForNullEqualsComparison() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             PreparedStatement statement = con.prepareStatement("sql")) {
            assertNotEquals(statement, null);
        }
    }

    @Test
    public void shouldReturnFalseForDifferentObjectTypeEqualsComparison() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             Statement statement = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            assertNotEquals(statement, "");
        }
    }

    @Test
    public void shouldReturnFalseForNonProxiedResultSetEqualsComparison() throws SQLException {
        this.datasource.setDriverClassName(Driver.class.getName());
        this.datasource.setUrl("jdbc:tomcat:test");
        try (Connection con = this.datasource.getConnection();
             Statement statement = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            assertNotEquals(statement, new org.apache.tomcat.jdbc.test.driver.Statement());
        }
    }
}

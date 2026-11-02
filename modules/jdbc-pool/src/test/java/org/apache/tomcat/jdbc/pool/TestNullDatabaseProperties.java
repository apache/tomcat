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
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * {@link PoolConfiguration#setDbProperties(java.util.Properties)} accepts a {@code null} collection, and the pool
 * treats that as one more ordinary configuration state: the credential setters store the credential without a mirror,
 * {@link PoolConfiguration#setConnectionProperties(String)} stores the string without parsing it into anything, and
 * the bean and MBean views hand back {@code null} instead of a masked copy. The one place that had not been told was
 * the connect itself, which builds the properties for the driver by cloning the collection - and a clone of nothing
 * threw, turning a configuration that has no driver properties into a pool that hands out no connections at all.
 */
class TestNullDatabaseProperties extends JdbcPoolBaseTest {

    @Test
    void aConfigurationWithoutDatabasePropertiesStillConnectsThroughTheDriver() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDbProperties(null);

        // The URL and the credential are all a driver route needs; the absent
        // collection only means there are no extra driver properties to send.
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT 1")) {
                Assertions.assertTrue(rs.next(), "the query should return a row");
                Assertions.assertEquals(1, rs.getInt(1));
            }
        } catch (SQLException x) {
            throw new AssertionError("a configuration with no database properties must still be able to "
                    + "establish a connection - the collection only carries extra driver properties", x);
        }
    }

    @Test
    void aConfigurationWithoutDatabasePropertiesStillReportsNoDatabaseProperties() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDbProperties(null);

        try (Connection con = ds.getConnection()) {
            Assertions.assertNotNull(con);
        } catch (SQLException x) {
            throw new AssertionError(x);
        }

        Assertions.assertNull(ds.getPoolProperties().getDbProperties(),
                "connecting must not quietly invent a collection the operator took away");
        Assertions.assertNull(ds.getDbProperties(),
                "the masked view has nothing to mask, so it stays null as the contract says");
    }
}

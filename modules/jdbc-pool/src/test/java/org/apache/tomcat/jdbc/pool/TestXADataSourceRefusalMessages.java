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

import java.sql.SQLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The refusal an {@link XADataSource} gives when it has no XA-capable
 * backing must describe what the pool is actually configured with, not
 * assert a configuration that may not exist. Two configurations reach the
 * no-backing refusal: a programmatically set dataSourceJNDI name - the
 * pool never resolves such a name itself, the lookup only happens in the
 * DataSourceFactory, which fails the pool creation outright when the name
 * cannot be resolved - and a pool with neither dataSource attribute set.
 * The refusal needs no database: the guard fires before the pool is
 * touched.
 */
class TestXADataSourceRefusalMessages {

    @Test
    void unresolvedDataSourceJndiRefusalNamesTheConfiguredJndiName() {
        // Programmatic configuration: only a 'dataSourceJNDI' name is set.
        // Nothing outside the DataSourceFactory resolves the name, so the
        // pool has no backing data source and the refusal must name the
        // actually-configured JNDI name instead of pointing at a
        // driver/URL configuration this pool does not have.
        XADataSource ds = new XADataSource();
        ds.getPoolProperties().setDataSourceJNDI("jdbc/XaTestDataSource");

        SQLException e = Assertions.assertThrows(SQLException.class, ds::getXAConnection,
                "an XA pool whose JNDI name was never resolved must refuse XA connections");
        String message = e.getMessage();
        Assertions.assertTrue(message.contains("jdbc/XaTestDataSource"),
                "failure must name the configured 'dataSourceJNDI' name, was: " + message);
        Assertions.assertFalse(message.contains("URL"),
                "failure must not assert a driver/URL configuration, was: " + message);
        Assertions.assertFalse(message.contains("configured with"),
                "failure must not assert a configuration the pool does not have, was: " + message);

        // The credential variant must refuse with the same message.
        SQLException e2 = Assertions.assertThrows(SQLException.class, () -> ds.getXAConnection("sa", ""),
                "the credential variant must refuse the same way");
        Assertions.assertEquals(message, e2.getMessage());
    }

    @Test
    void poolWithoutDataSourceOrJndiRefusalStatesMissingDataSourceConfiguration() {
        // Neither 'dataSource' nor 'dataSourceJNDI' is set: the refusal must
        // say so rather than claim the pool is configured with a JDBC driver
        // and URL, which this pool does not have either.
        XADataSource ds = new XADataSource();

        SQLException e = Assertions.assertThrows(SQLException.class, ds::getXAConnection,
                "an XA pool without any backing data source configuration must refuse XA connections");
        String message = e.getMessage();
        Assertions.assertTrue(message.contains("'dataSource'"),
                "failure must name the missing 'dataSource' attribute, was: " + message);
        Assertions.assertTrue(message.contains("'dataSourceJNDI'"),
                "failure must name the missing 'dataSourceJNDI' attribute, was: " + message);
        Assertions.assertFalse(message.contains("configured with"),
                "failure must not assert a configuration the pool does not have, was: " + message);
    }
}

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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;

import org.junit.Test;

/**
 * Test related to pooled connection.
 */
public class PooledConnectionTest {
    @Test
    public void avoidNPEWhenTcclIsNull() throws SQLException, IOException {
        final PoolProperties poolProperties = new PoolProperties();
        poolProperties.setDriverClassName("org.hsqldb.jdbcDriver"); // not in test loader otherwise test is broken
        poolProperties.setUsername("sa");
        poolProperties.setPassword("");
        poolProperties.setUrl("jdbc:hsqldb:mem:PooledConnectionTest_avoidNPEWhenTcclIsNull");
        poolProperties.setMaxIdle(1);
        poolProperties.setMinIdle(1);
        poolProperties.setInitialSize(1);
        poolProperties.setMaxActive(1);

        final Thread thread = Thread.currentThread();
        final ClassLoader testLoader = thread.getContextClassLoader();
        final DataSource dataSource;
        try (final URLClassLoader loader = new URLClassLoader(new URL[] {
                Paths.get("target/test-libs/hsqldb.jar").toUri().toURL()
        }, testLoader)) {
            thread.setContextClassLoader(loader);
            dataSource = new DataSource(poolProperties);
            checkConnection(dataSource);
        } finally {
            thread.setContextClassLoader(testLoader);
        }

        thread.setContextClassLoader(null);
        try {
            checkConnection(dataSource);
        } finally {
            thread.setContextClassLoader(testLoader);
        }

        dataSource.close();
    }

    private void checkConnection(DataSource dataSource) throws SQLException {
        try (final Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(5));
        }
    }
}

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
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The properties a connect hands the driver come from the pool's immutable snapshot. Reconfiguring the mutable builder
 * after the pool has been built must not change what a connection the pool creates is built from: the driver must
 * receive one published generation whole, never a merge of the builder's current state and an earlier one.
 */
abstract class ReconfigureVsConnectPropertiesBase {

    private static final String GENERATION_1 = "user=bob;password=P1;sslmode=require";
    private static final String GENERATION_2 = "user=alice;password=P2;newsetting=1";

    /**
     * Records the properties each connect was offered and then declines to
     * open a session: the test is about what the copy contained, not about
     * any real database voting on it.
     */
    public static class RecordingNullDriver implements Driver {

        static final AtomicReference<Properties> lastConnectInfo = new AtomicReference<>();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Properties handed = new Properties();
            handed.putAll(info);
            lastConnectInfo.set(handed);
            return null;
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return true;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    @Test
    void aReconfigureAfterThePoolIsBuiltDoesNotChangeTheSnapshotAConnectUses() throws Exception {
        PoolProperties props = new PoolProperties();
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setUrl("jdbc:tomcat-jdbc-test:reconfiguration");
        props.setDriverClassName(RecordingNullDriver.class.getName());
        Properties generationOne = new Properties();
        generationOne.setProperty("user", "bob");
        generationOne.setProperty("password", "P1");
        generationOne.setProperty("sslmode", "require");
        props.setDbProperties(generationOne);
        props.setConnectionProperties(GENERATION_1);
        RecordingNullDriver.lastConnectInfo.set(null);

        ConnectionPool pool = new ConnectionPool(props);
        try {
            // The pool's configuration is the immutable snapshot it took at
            // construction. Reconfiguring the mutable builder afterwards must
            // not change what the connection the pool creates is built from.
            props.setConnectionProperties(GENERATION_2);

            PooledConnection con = new PooledConnection(pool.getPoolProperties(), pool);
            Assertions.assertThrows(SQLException.class, con::connectUsingDriver,
                    "the driver declines every connect, after recording the copy");

            Properties handed = RecordingNullDriver.lastConnectInfo.get();
            Assertions.assertNotNull(handed, "the driver must have been offered the snapshot properties");
            Assertions.assertEquals(new HashSet<>(Arrays.asList("user", "password", "sslmode")),
                    handed.stringPropertyNames(),
                    "the driver must be handed one published generation, not a merge of two");
            Assertions.assertEquals("bob", handed.getProperty("user"));
            Assertions.assertEquals("P1", handed.getProperty("password"));
            Assertions.assertEquals("require", handed.getProperty("sslmode"));

            // The mutable builder really did move on: the pool's snapshot is
            // what kept the connect on one generation.
            Assertions.assertEquals("alice", props.getDbProperties().getProperty("user"));
            Assertions.assertEquals("P2", props.getDbProperties().getProperty("password"));
            Assertions.assertEquals("1", props.getDbProperties().getProperty("newsetting"));
            Assertions.assertNull(props.getDbProperties().getProperty("sslmode"),
                    "the second generation dropped the key the first one carried");
        } finally {
            pool.close(true);
        }
    }
}

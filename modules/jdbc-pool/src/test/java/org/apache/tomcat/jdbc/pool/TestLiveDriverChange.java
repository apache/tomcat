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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A {@link PooledConnection} caches the instantiated {@link java.sql.Driver}. The cache was never invalidated when the
 * configured {@code driverClassName} changed, so a reconnect of an existing connection kept using the driver the
 * connection was first built with even though the configuration named another one.
 */
class TestLiveDriverChange extends JdbcPoolBaseTest {

    /**
     * An H2 driver that records how many physical connections it opens. Configured through {@code driverClassName}, so
     * it must stay a public static class with a no-arg constructor.
     */
    public static class CountingDriver extends org.h2.Driver {
        static final AtomicInteger CONNECT_CALLS = new AtomicInteger();

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            CONNECT_CALLS.incrementAndGet();
            return super.connect(url, info);
        }
    }

    @Test
    void aReconnectUsesTheDriverNameConfiguredAtReconnectTime() throws Exception {
        CountingDriver.CONNECT_CALLS.set(0);
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxIdle(1);
        ds.getPoolProperties().setMaxAge(1);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();

        Connection first = ds.getConnection();
        // The return below reconnects the expired connection. The configured
        // name now changes, so the reconnect must build a driver for it
        // instead of reusing the cached H2 driver.
        Thread.sleep(20);
        ds.getPoolProperties().setDriverClassName(CountingDriver.class.getName());
        first.close();
        Assertions.assertEquals(1, CountingDriver.CONNECT_CALLS.get(),
                "the reconnect must use the driver named by the live configuration");

        // Borrow the reconnected connection without letting its expiry trigger
        // another reconnect through the counting driver; the next reconnect is
        // the one that switches back below.
        ds.getPoolProperties().setMaxAge(100000);
        Connection second = ds.getConnection();
        // Switch the configuration back to the original driver. The
        // reconnected connection has the counting driver cached; the next
        // reconnect must not keep using it.
        ds.getPoolProperties().setMaxAge(1);
        Thread.sleep(20);
        ds.getPoolProperties().setDriverClassName("org.h2.Driver");
        second.close();
        Assertions.assertEquals(1, CountingDriver.CONNECT_CALLS.get(),
                "switching back must discard the counting driver rather than reuse it");

        try (Connection third = ds.getConnection();
                Statement st = third.createStatement()) {
            st.execute("SELECT 1");
        }
        ds.close();
    }
}

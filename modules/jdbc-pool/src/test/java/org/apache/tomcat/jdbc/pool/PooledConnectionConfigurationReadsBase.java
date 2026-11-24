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

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A configured value that decides a timeout or expiry is read once for the
 * decision and the calculation. Reading it twice lets a concurrent live change
 * land between the two, making the result describe a configuration that never
 * existed: a timeout guard that passed on a positive value could produce a
 * zero or negative timeout, and a max-age guard could expire a session the
 * second read no longer asks to expire.
 */
abstract class PooledConnectionConfigurationReadsBase extends JdbcPoolBaseTest {

    /**
     * Configuration whose first read of each mutable value differs from every
     * later one, so a method that reads twice sees two different generations.
     */
    private static final class ChangingProperties extends PoolProperties {

        private static final long serialVersionUID = 1L;

        final AtomicInteger abandonTimeoutReads = new AtomicInteger();
        final AtomicInteger maxAgeReads = new AtomicInteger();

        @Override
        public int getRemoveAbandonedTimeout() {
            return abandonTimeoutReads.incrementAndGet() == 1 ? 60 : 0;
        }

        @Override
        public long getMaxAge() {
            return maxAgeReads.incrementAndGet() == 1 ? 1000 : 0;
        }
    }

    private static void setLastConnected(PooledConnection connection, long value) throws Exception {
        Field field = PooledConnection.class.getDeclaredField("lastConnected");
        field.setAccessible(true);
        field.setLong(connection, value);
    }

    @Test
    void aTerminalReleaseRemovesTheCachedCredentials() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();
        Connection held = ds.getConnection();
        PooledConnection pooled = internalOf(held);
        Assertions.assertEquals("sa", pooled.getAttributes().get(PooledConnection.PROP_USER),
                "precondition: the connected session caches its user name for renewal");
        Assertions.assertEquals("", pooled.getAttributes().get(PooledConnection.PROP_PASSWORD),
                "precondition: the connected session caches its password for renewal");

        ds.close(true);

        Assertions.assertFalse(pooled.getAttributes().containsKey(PooledConnection.PROP_USER),
                "a terminal release must not leave the cached user name behind");
        Assertions.assertFalse(pooled.getAttributes().containsKey(PooledConnection.PROP_PASSWORD),
                "a terminal release must not leave the cached password behind");
    }

    @Test
    void theAbandonTimeoutUsesOneConfigurationRead() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();
        PooledConnection connection = new PooledConnection(new ChangingProperties(), ds.getPool());

        Assertions.assertEquals(60000L, connection.getAbandonTimeout(),
                "the guard and the calculation must use the same configured timeout, "
                        + "not combine a positive guard with a later non-positive value");
    }

    @Test
    void theMaxAgeExpiryUsesOneConfigurationRead() throws Exception {
        DataSource ds = newDataSource();
        ds.createPool();
        PooledConnection connection = new PooledConnection(new ChangingProperties(), ds.getPool());
        setLastConnected(connection, System.currentTimeMillis() - 500);

        Assertions.assertFalse(connection.isMaxAgeExpired(),
                "a session younger than the configured max age must not expire because "
                        + "a second read of the same setting saw a different value");
    }
}

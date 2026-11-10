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

import java.lang.management.ManagementFactory;
import java.sql.Connection;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * {@link PooledConnection#getLastValidated()} documents 0 for a session that
 * has not been validated. The timestamp belongs to one physical session, so a
 * disconnect must clear it: while a reconnect is in flight the per-connection
 * MBean stays registered, and the old session's timestamp must not be read as
 * if it vouched for the new session.
 */
class TestLastValidatedLifecycle extends JdbcPoolBaseTest {

    @Test
    void aDestroyedSessionClearsLastValidated() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();

        Connection held = ds.getConnection();
        PooledConnection pooled = interceptorOf(held, ProxyConnection.class).getConnection();
        pooled.setLastValidated(12345L);

        ds.close(true);

        Assertions.assertEquals(0L, pooled.getLastValidated(),
                "a disconnected session must report 0 rather than the validation of the session it replaced");
    }

    @Test
    void thePerConnectionMBeanReportsZeroAcrossAReconnect() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJmxEnabled(true);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ds.preRegister(server,
                new ObjectName("tomcat.jdbc:type=LastValidatedTest,uid=" + System.nanoTime()));

        Connection held = ds.getConnection();
        try {
            PooledConnection pooled = interceptorOf(held, ProxyConnection.class).getConnection();
            ObjectName name = pooled.getObjectName();
            Assertions.assertNotNull(name,
                    "precondition: the borrowed connection has published its MBean");

            pooled.setLastValidated(12345L);
            Assertions.assertEquals(12345L, attribute(server, name, "LastValidated"),
                    "precondition: the MBean reads the validation timestamp of the current session");

            pooled.reconnect();

            Assertions.assertEquals(0L, attribute(server, name, "LastValidated"),
                    "the replaced session's validation must not be reported for the new session");
            Assertions.assertEquals(0L, pooled.getLastValidated());
        } finally {
            held.close();
            ds.close(true);
        }
    }

    private static long attribute(MBeanServer server, ObjectName name, String attribute) throws Exception {
        return ((Number) server.getAttribute(name, attribute)).longValue();
    }
}

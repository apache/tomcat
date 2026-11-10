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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * {@link org.apache.tomcat.jdbc.pool.jmx.ConnectionPoolMBean#getReleasedIdleCount()} documents that every pass which
 * takes an idle connection out of the pool counts it. {@code testAllIdle()} counted only the failed-validation branch:
 * a connection that passed its check but was released because the pool had closed, the idle limit was reached, or the
 * offer failed was taken out of service without a count.
 */
class TestReleasedIdleCount extends JdbcPoolBaseTest {

    /**
     * Parks the idle validation. Configured through {@code validator}, so it must stay a public static class with a
     * no-arg constructor.
     */
    public static class ParkingValidator implements Validator {
        static final CountDownLatch INSIDE = new CountDownLatch(1);
        static final CountDownLatch RESUME = new CountDownLatch(1);

        @Override
        public boolean validate(Connection connection, int validateAction) {
            if (validateAction == PooledConnection.VALIDATE_IDLE) {
                INSIDE.countDown();
                try {
                    RESUME.await();
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
            }
            return true;
        }
    }

    public static class ExposedPool extends ConnectionPool {
        ExposedPool(PoolProperties properties) throws SQLException {
            super(properties);
        }

        void closeNow() {
            close(true);
        }
    }

    @Test
    void anIdleConnectionReleasedAfterThePoolClosedIsCounted() throws Exception {
        PoolProperties properties = new PoolProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setJmxEnabled(false);
        properties.setInitialSize(0);
        properties.setMinIdle(0);
        properties.setMaxActive(1);
        properties.setMaxIdle(1);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        properties.setTestWhileIdle(true);
        properties.setValidationInterval(0);
        properties.setValidator(new ParkingValidator());
        ExposedPool pool = new ExposedPool(properties);

        try (Connection con = pool.getConnection()) {
            con.createStatement().close();
        }

        Thread tester = new Thread(pool::testAllIdle, "idle-tester");
        tester.start();
        Assertions.assertTrue(ParkingValidator.INSIDE.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "precondition: the idle pass must have claimed the connection and entered validation");
        pool.closeNow();
        ParkingValidator.RESUME.countDown();
        tester.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(tester.isAlive(), "the idle pass must finish");

        Assertions.assertEquals(1, pool.getReleasedIdleCount(),
                "the idle pass released the claimed connection after the close and must count it");
    }
}

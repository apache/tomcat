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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A close can set the pooled connection's released flag and begin the physical close while a borrow is still in
 * {@link ConnectionPool#setupConnection(PooledConnection)}. The setup used to finish building a handle from that
 * released connection, and the only thing that failed such a borrow on a current JDK was the JDBC 4.3 beginRequest hint
 * receiving the nulled physical connection by accident; where that hint is absent or the physical close has not nulled
 * the field yet, the caller received an inert handle instead of an {@link SQLException}.
 */
class TestSetupVsCloseRace extends JdbcPoolBaseTest {

    /**
     * Parks the setup in the interceptor reset walk, before the handle is built. Configured through
     * {@code jdbcInterceptors}, so it must stay a public static class with a no-arg constructor.
     */
    public static class ParkingResetInterceptor extends JdbcInterceptor {
        static final CountDownLatch PARKED = new CountDownLatch(1);
        static final CountDownLatch RESUME = new CountDownLatch(1);

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            if (parent != null) {
                PARKED.countDown();
                try {
                    RESUME.await();
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return super.invoke(proxy, method, args);
        }
    }

    /**
     * A driver whose physical connection parks in {@code close()}. The pool's release marks the pooled connection
     * released before it calls the physical close, so while this close is parked the setup sees a released connection
     * whose physical handle is still in place. Configured through {@code driverClassName}, so it must stay a public
     * static class with a no-arg constructor.
     */
    public static class ParkingCloseDriver extends org.h2.Driver {
        static final CountDownLatch CLOSE_ENTERED = new CountDownLatch(1);
        static final CountDownLatch RESUME_CLOSE = new CountDownLatch(1);

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection real = super.connect(url, info);
            if (real == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[] { Connection.class }, new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("close".equals(method.getName())) {
                                CLOSE_ENTERED.countDown();
                                try {
                                    RESUME_CLOSE.await();
                                } catch (InterruptedException x) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            try {
                                return method.invoke(real, args);
                            } catch (InvocationTargetException x) {
                                throw x.getTargetException();
                            }
                        }
                    });
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
    void aCloseThatReleasesTheConnectionDuringSetupFailsTheBorrow() throws Exception {
        PoolProperties properties = new PoolProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName(ParkingCloseDriver.class.getName());
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setJmxEnabled(false);
        properties.setInitialSize(0);
        properties.setMinIdle(0);
        properties.setMaxActive(1);
        properties.setMaxIdle(1);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        properties.setJdbcInterceptors(ParkingResetInterceptor.class.getName());
        ExposedPool pool = new ExposedPool(properties);

        AtomicReference<Object> outcome = new AtomicReference<>();
        Thread closer = null;
        try {
            Thread borrower = new Thread(() -> {
                try {
                    outcome.set(pool.getConnection());
                } catch (Throwable x) {
                    outcome.set(x);
                }
            }, "borrower-in-setup");
            borrower.start();
            Assertions.assertTrue(ParkingResetInterceptor.PARKED.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "precondition: the borrow must be parked in the setup");

            closer = new Thread(pool::closeNow, "closer-during-setup");
            closer.start();
            Assertions.assertTrue(ParkingCloseDriver.CLOSE_ENTERED.await(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                    "precondition: the close must have released the pooled connection and be parked in the"
                            + " physical close");

            // Let the setup finish while the released connection's physical
            // handle is still in place.
            ParkingResetInterceptor.RESUME.countDown();
            borrower.join(AWAIT_TIMEOUT_MS);
            Assertions.assertFalse(borrower.isAlive(), "the borrow must finish");
            Assertions.assertInstanceOf(SQLException.class, outcome.get(),
                    "a setup whose pooled connection has been released must fail the borrow instead of"
                            + " handing out a handle backed by the released connection, got: " + outcome.get());
        } finally {
            ParkingResetInterceptor.RESUME.countDown();
            ParkingCloseDriver.RESUME_CLOSE.countDown();
            if (closer != null) {
                closer.join(AWAIT_TIMEOUT_MS);
            }
            pool.closeNow();
        }
        Assertions.assertEquals(0, pool.getActive(), "the closed pool must hold no active connection");
        Assertions.assertEquals(0, pool.getIdle(), "the closed pool must hold no idle connection");
    }
}

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The start-time properties ({@code poolProperties}, {@code fairQueue},
 * {@code jmxEnabled} and {@code ignoreExceptionOnPreLoad}) decide what the pool
 * constructor builds, from which configuration it builds it, and how the
 * published pool is validated. A write that lands while the constructor is
 * already running can no longer change any of those decisions, so it must be
 * rejected. Accepting it leaves the data source reporting a configuration that
 * the pool was not built from - in particular a replacement configuration whose
 * credential writes never reach the connections the pool creates.
 * <p>
 * {@code initialSize} is deliberately not in that group: the pre-load count is
 * bounded where the array that consumes it is allocated, so a write racing the
 * construction changes only the reported configuration and cannot make the
 * creation die on the value. The existing lifecycle test covers that tolerance.
 */
abstract class DataSourceProxyConstructionSettersBase extends JdbcPoolBaseTest {

    /**
     * Parks the first {@code getMaxActive()} call the pool constructor makes,
     * so the construction can be held open while the setters are attempted.
     */
    private static final class ParkingProperties extends PoolProperties {

        private static final long serialVersionUID = 1L;

        final CountDownLatch maxActiveRead = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        private final AtomicBoolean parked = new AtomicBoolean(true);

        @Override
        public int getMaxActive() {
            if (parked.compareAndSet(true, false)) {
                maxActiveRead.countDown();
                try {
                    if (!resume.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to resume the pool constructor");
                    }
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(x);
                }
            }
            return super.getMaxActive();
        }
    }

    private static ParkingProperties properties() {
        ParkingProperties props = new ParkingProperties();
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setTimeBetweenEvictionRunsMillis(-1);
        props.setJmxEnabled(false);
        props.setFairQueue(false);
        props.setIgnoreExceptionOnPreLoad(false);
        props.setUrl("jdbc:tomcat-jdbc-test:construction-setters");
        props.setDriverClassName("org.h2.Driver");
        return props;
    }

    @Test
    void startTimeSettersAreRejectedWhileThePoolIsBeingConstructed() throws Exception {
        ParkingProperties props = properties();
        DataSourceProxy ds = new DataSourceProxy(props);
        try {
            AtomicReference<ConnectionPool> created = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread creator = new Thread(() -> {
                try {
                    created.set(ds.createPool());
                } catch (Throwable x) {
                    failure.set(x);
                }
            }, "pool-creator");
            creator.start();
            Assertions.assertTrue(props.maxActiveRead.await(10, TimeUnit.SECONDS),
                    () -> "the pool constructor must be running (creator=" + creator.getState()
                            + ", failure=" + failure.get() + ")");

            PoolProperties replacement = new PoolProperties();
            replacement.setInitialSize(0);
            replacement.setTimeBetweenEvictionRunsMillis(-1);
            replacement.setJmxEnabled(false);
            Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> ds.setPoolProperties(replacement),
                    "the configuration object must not be replaced while the pool is being built");
            Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> ds.setFairQueue(true),
                    "the queue type has already been chosen, so fairQueue must not be changed during construction");
            Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> ds.setJmxEnabled(true),
                    "the JMX decision is already being applied, so jmxEnabled must not be changed during construction");
            Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> ds.setIgnoreExceptionOnPreLoad(true),
                    "the pre-load policy is already running, so ignoreExceptionOnPreLoad must not be changed during construction");

            Assertions.assertSame(props, ds.getPoolProperties(),
                    "the data source must keep the configuration the pool is being built from");
            Assertions.assertEquals(0, props.getInitialSize());
            Assertions.assertFalse(props.isFairQueue());
            Assertions.assertFalse(props.isJmxEnabled());
            Assertions.assertFalse(props.isIgnoreExceptionOnPreLoad());

            props.resume.countDown();
            creator.join(TimeUnit.SECONDS.toMillis(10));
            Assertions.assertFalse(creator.isAlive(), "the pool creation must have ended");
            Assertions.assertNull(failure.get(), "the pool creation must succeed");
            Assertions.assertNotNull(created.get(), "the pool creation must publish a pool");
            Assertions.assertSame(created.get().getPoolProperties(), ds.getPoolProperties(),
                    "the data source and the pool must share the live configuration view");
            Assertions.assertEquals(0, ds.getPoolProperties().getInitialSize());
            Assertions.assertFalse(ds.getPoolProperties().isFairQueue());
            Assertions.assertFalse(ds.getPoolProperties().isJmxEnabled());
            Assertions.assertFalse(ds.getPoolProperties().isIgnoreExceptionOnPreLoad());
        } finally {
            props.resume.countDown();
            ds.close(true);
        }
    }
}

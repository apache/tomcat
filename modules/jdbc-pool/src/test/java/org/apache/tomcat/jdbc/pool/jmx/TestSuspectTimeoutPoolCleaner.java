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
package org.apache.tomcat.jdbc.pool.jmx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;

/**
 * Setting the suspect timeout on a running pool through its JMX MBean must
 * start or stop the pool cleaner, like every other attribute that takes part
 * in {@link org.apache.tomcat.jdbc.pool.PoolProperties#isPoolSweeperEnabled()}
 * does.
 * <p>
 * The suspect check only ever runs from the pool cleaner task, and the cleaner
 * is scheduled at pool initialization or when one of the JMX setters rechecks
 * the sweeper state after an update. A setter that updates the property
 * without that recheck therefore changes what the pool would do but not
 * whether anything is running to do it: enabling suspect detection over JMX
 * silently never takes effect until a restart, and disabling it leaves a
 * cleaner running that no longer has work to do for this trigger.
 * <p>
 * The cleaner is observed through the static registry of scheduled tasks,
 * which moves synchronously with every start and stop of the pool's own
 * cleaner. Tests run sequentially and the base fixture closes each pool, so
 * the registry only moves while the running test changes it.
 */
class TestSuspectTimeoutPoolCleaner extends JdbcPoolBaseTest {

    /* How many pool cleaners are currently scheduled on the shared timer. */
    private static int scheduledCleaners() {
        return org.apache.tomcat.jdbc.pool.ConnectionPool.getPoolCleaners().size();
    }

    /*
     * A running pool with the eviction timer on and every other sweeper
     * trigger off - the configuration an operator is in when they decide to
     * turn on suspect detection from a JMX console.
     */
    private DataSource poolWithSweeperOff() throws Exception {
        DataSource ds = newDataSource();
        PoolConfiguration properties = ds.getPoolProperties();
        properties.setJmxEnabled(true);
        properties.setTimeBetweenEvictionRunsMillis(1000);
        properties.setRemoveAbandoned(false);
        properties.setSuspectTimeout(0);
        properties.setTestWhileIdle(false);
        properties.setMinEvictableIdleTimeMillis(0);
        properties.setMaxAge(0);
        ds.createPool();
        return ds;
    }

    /* The JMX bean of a pool created with JMX enabled. */
    private static ConnectionPool jmxBeanOf(DataSource ds) {
        ConnectionPool jmxPool = ds.getPool().getJmxPool();
        Assertions.assertNotNull(jmxPool,
                "a pool created with JMX enabled must expose its MBean object");
        return jmxPool;
    }

    @Test
    void enablingSuspectTimeoutOverJmxStartsThePoolCleaner() throws Exception {
        int baseline = scheduledCleaners();
        DataSource ds = poolWithSweeperOff();
        org.apache.tomcat.jdbc.pool.ConnectionPool pool = ds.getPool();

        Assertions.assertFalse(pool.getPoolProperties().isPoolSweeperEnabled(),
                "the fixture must start with the sweeper disabled");
        Assertions.assertEquals(baseline, scheduledCleaners(),
                "a pool with the sweeper disabled must not have scheduled a cleaner");

        jmxBeanOf(ds).setSuspectTimeout(60);

        Assertions.assertEquals(60, pool.getPoolProperties().getSuspectTimeout(),
                "the attribute must be set on the running pool");
        Assertions.assertTrue(pool.getPoolProperties().isPoolSweeperEnabled(),
                "a suspect timeout over the eviction timer enables the sweeper");
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "setting the suspect timeout on a running pool over JMX must schedule the "
                        + "pool cleaner that runs the suspect check, since that check only "
                        + "ever runs from the cleaner task");
    }

    @Test
    void droppingSuspectTimeoutOverJmxStopsThePoolCleanerItStarted() throws Exception {
        int baseline = scheduledCleaners();
        DataSource ds = poolWithSweeperOff();
        org.apache.tomcat.jdbc.pool.ConnectionPool pool = ds.getPool();

        jmxBeanOf(ds).setSuspectTimeout(120);
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "precondition: enabling suspect detection must have scheduled the cleaner");

        jmxBeanOf(ds).setSuspectTimeout(0);

        Assertions.assertFalse(pool.getPoolProperties().isPoolSweeperEnabled(),
                "with every other trigger off, dropping the timeout must disable the sweeper");
        Assertions.assertEquals(baseline, scheduledCleaners(),
                "disabling the sweeper's only trigger over JMX must terminate the cleaner "
                        + "it started for it, instead of leaving it running without work");
    }

    /*
     * The reverse direction of the same recheck: while another attribute keeps
     * the sweeper enabled, touching the suspect timeout must leave the running
     * cleaner in place rather than stop it.
     */
    @Test
    void togglingSuspectTimeoutOverJmxKeepsACleanerStartedForAnotherTrigger() throws Exception {
        int baseline = scheduledCleaners();
        DataSource ds = newDataSource();
        PoolConfiguration properties = ds.getPoolProperties();
        properties.setJmxEnabled(true);
        properties.setTimeBetweenEvictionRunsMillis(1000);
        properties.setRemoveAbandoned(true);
        ds.createPool();
        org.apache.tomcat.jdbc.pool.ConnectionPool pool = ds.getPool();

        Assertions.assertTrue(pool.getPoolProperties().isPoolSweeperEnabled(),
                "removing abandoned connections over the eviction timer enables the sweeper");
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "a pool with the sweeper enabled must have scheduled its cleaner at init");

        ConnectionPool jmxPool = jmxBeanOf(ds);
        jmxPool.setSuspectTimeout(45);
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "enabling suspect detection while abandoned removal already enables the "
                        + "sweeper must leave a cleaner running, not stop the existing one");
        jmxPool.setSuspectTimeout(0);
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "dropping the timeout again must likewise leave the cleaner that the "
                        + "remaining trigger needs in place");
    }
}

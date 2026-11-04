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

import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Turning a pool sweeper trigger on or off through the plain {@link DataSource}
 * accessors - the bean/programmatic surface, not the pool's JMX MBean, which
 * already re-arms - must start or stop the pool cleaner on a running pool, so
 * that {@link PoolConfiguration#isPoolSweeperEnabled()} and the maintenance it
 * promises agree. These accessors used to be pure delegations: a sweeper
 * switched on through them after the pool had started, with the eviction timer
 * off at creation, raised the getter to true without ever arming the thread
 * that performs the maintenance.
 * <p>
 * The cleaner is observed through the static registry of scheduled tasks, which
 * moves synchronously with every start and stop of the pool's own cleaner. Tests
 * run sequentially and the base fixture closes each pool, so the registry only
 * moves while the running test changes it.
 */
class TestPoolSweeperSetters extends JdbcPoolBaseTest {

    /* How many pool cleaners are currently scheduled on the shared timer. */
    private static int scheduledCleaners() {
        return org.apache.tomcat.jdbc.pool.ConnectionPool.getPoolCleaners().size();
    }

    /*
     * The interval the one scheduled cleaner was built with. Its period is fixed
     * at construction, so it is a fact a test has to read back rather than infer:
     * a configuration the pool believes in and a task running on another interval
     * look the same from the getters.
     */
    private static long registeredCleanerPeriod() {
        long period = -1;
        for (TimerTask task : org.apache.tomcat.jdbc.pool.ConnectionPool.getPoolCleaners()) {
            period = ((org.apache.tomcat.jdbc.pool.ConnectionPool.PoolCleaner) task).sleepTime;
        }
        return period;
    }

    /*
     * A running pool with the eviction timer on and every other sweeper trigger
     * off - the configuration an operator is in when they decide to turn on
     * abandoned-connection removal from the DataSource bean, JMX disabled so the
     * test exercises the plain accessor rather than the MBean.
     */
    private static PoolConfiguration sweeperOff(PoolConfiguration properties) {
        properties.setJmxEnabled(false);
        properties.setTimeBetweenEvictionRunsMillis(1000);
        properties.setRemoveAbandoned(false);
        properties.setSuspectTimeout(0);
        properties.setTestWhileIdle(false);
        properties.setMinEvictableIdleTimeMillis(0);
        properties.setMaxAge(0);
        return properties;
    }

    private DataSource poolWithSweeperOff() throws Exception {
        DataSource ds = newDataSource();
        sweeperOff(ds.getPoolProperties());
        ds.createPool();
        return ds;
    }

    /*
     * An interceptor that turns a sweeper trigger on through the data source
     * from inside the pool's own start-up, standing in for the management write
     * that lands while the pool is still being built: at that moment the pool
     * exists and the data source's field is still null, so the write cannot be
     * re-armed by the setters and has to be picked up by the creation itself.
     * Configured through the jdbcInterceptors class-name list, so it must stay a
     * public static class with a no-arg constructor.
     */
    public static class SweeperTriggerAtPoolStartInterceptor extends JdbcInterceptor {

        static volatile DataSource target;

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // Nothing to track: this interceptor only listens for the start.
        }

        @Override
        public void poolStarted(ConnectionPool pool) {
            DataSource ds = target;
            target = null;
            if (ds != null) {
                ds.setTestWhileIdle(true);
            }
            super.poolStarted(pool);
        }
    }

    @Test
    void aSweeperTriggerTurnedOnWhileThePoolIsBeingBuiltStillGetsItsCleaner() throws Exception {
        int baseline = scheduledCleaners();
        DataSource ds = newDataSource();
        sweeperOff(ds.getPoolProperties());
        SweeperTriggerAtPoolStartInterceptor.target = ds;
        ds.getPoolProperties()
                .setJdbcInterceptors(SweeperTriggerAtPoolStartInterceptor.class.getName());
        try {
            ds.createPool();
        } finally {
            SweeperTriggerAtPoolStartInterceptor.target = null;
        }

        Assertions.assertTrue(ds.getPoolProperties().isPoolSweeperEnabled(),
                "precondition: the write made during creation raised the sweeper");
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "a trigger that lands while the pool is being built must get its cleaner too: the "
                        + "creation is what has to arm it, and it must do so after everything that "
                        + "can still change the configuration during startup");
    }

    @Test
    void enablingRemoveAbandonedOverTheDataSourceStartsThePoolCleaner() throws Exception {
        int baseline = scheduledCleaners();
        DataSource ds = poolWithSweeperOff();

        Assertions.assertFalse(ds.getPoolProperties().isPoolSweeperEnabled(),
                "the fixture must start with the sweeper disabled");
        Assertions.assertEquals(baseline, scheduledCleaners(),
                "a pool with the sweeper disabled must not have scheduled a cleaner");

        ds.setRemoveAbandoned(true);

        Assertions.assertTrue(ds.getPoolProperties().isPoolSweeperEnabled(),
                "enabling abandoned-connection removal over the eviction timer enables the sweeper");
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "switching the sweeper on through the DataSource must schedule the pool "
                        + "cleaner that performs the maintenance, not only update the property");
    }

    @Test
    void droppingRemoveAbandonedOverTheDataSourceStopsThePoolCleanerItStarted() throws Exception {
        int baseline = scheduledCleaners();
        DataSource ds = poolWithSweeperOff();

        ds.setRemoveAbandoned(true);
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "precondition: enabling the trigger must have scheduled the cleaner");

        ds.setRemoveAbandoned(false);

        Assertions.assertFalse(ds.getPoolProperties().isPoolSweeperEnabled(),
                "with every other trigger off, dropping the only one must disable the sweeper");
        Assertions.assertEquals(baseline, scheduledCleaners(),
                "dropping the sweeper's only trigger must terminate the cleaner it started "
                        + "for it, instead of leaving it running without work");
    }

    /*
     * Reconfiguring the sweeper of a pool that is already closed stands in for
     * the setter racing the close: the close marks the pool closed before it
     * terminates the cleaner, so a setter that reaches the pool after that point
     * must find the door shut. Were it open, the task it scheduled would be the
     * one thing the close could no longer unregister - the close had already
     * looked for a cleaner - and the shared timer and its thread would stay
     * alive for the lifetime of the class loader that loaded the pool.
     */
    @Test
    void enablingATriggerOnAClosedPoolDoesNotScheduleACleaner() throws Exception {
        int baseline = scheduledCleaners();
        DataSource ds = poolWithSweeperOff();

        ds.getPool().close(false);
        Assertions.assertEquals(baseline, scheduledCleaners(), "precondition: closing must leave no cleaner");

        ds.setTestWhileIdle(true);

        Assertions.assertTrue(ds.getPoolProperties().isPoolSweeperEnabled(),
                "precondition: the write itself still takes effect");
        Assertions.assertEquals(baseline, scheduledCleaners(),
                "a closed pool has no connections to maintain, so re-arming its sweeper must not "
                        + "schedule a task that nothing will ever unregister again");
    }

    @Test
    void thePoolRefusesToStartACleanerOnceItIsClosed() throws Exception {
        int baseline = scheduledCleaners();
        DataSource ds = poolWithSweeperOff();

        ds.setTestWhileIdle(true);
        ConnectionPool pool = ds.getPool();
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "precondition: turning a trigger on over the DataSource armed the cleaner");

        pool.close(false);
        Assertions.assertEquals(baseline, scheduledCleaners(),
                "precondition: closing the pool terminated its cleaner");

        pool.initializePoolCleaner(pool.getPoolProperties());

        Assertions.assertEquals(baseline, scheduledCleaners(),
                "the pool must not start a cleaner for itself once it is closed, whoever asks");
    }

    /**
     * Configuration that lets a test place a second management write inside a
     * window of a first one, and hands the first one the answer it read before
     * that write - the view a writer has when it is preempted between deciding
     * and acting. The window is picked by which read it fires on: the pool's own
     * arming, the read the cleaner's outcome is decided from, or the Nth read made
     * after the test armed the hook, which is how a writer's own first look at the
     * sweeper state is reached whatever else the code around it reads.
     */
    private static final class HookedSweeperProperties extends PoolProperties {

        private static final long serialVersionUID = 1L;

        final AtomicInteger checks = new AtomicInteger();
        volatile int fireOnCheck;
        volatile Runnable onSweeperCheck;
        volatile Runnable onDecisionCheck;
        volatile Runnable onArmingCheck;

        @Override
        public boolean isPoolSweeperEnabled() {
            boolean read = super.isPoolSweeperEnabled();
            Runnable arming = onArmingCheck;
            if (arming != null && fromFrame("initializePoolCleaner")) {
                // One shot: the write this test runs reads the sweeper state
                // itself, nested inside the read being hooked.
                onArmingCheck = null;
                arming.run();
            }
            Runnable decision = onDecisionCheck;
            if (decision != null && (fromFrame("reconcilePoolCleaner") ||
                    fromFrame("poolCleanerAttributeUpdated"))) {
                onDecisionCheck = null;
                decision.run();
            }
            if (fireOnCheck != 0 && checks.incrementAndGet() == fireOnCheck) {
                fireOnCheck = 0;
                onSweeperCheck.run();
            }
            return read;
        }

        /*
         * Whether the read came from one of the named methods. Which read a test
         * wants has to be found by its call site: the state is read a dozen times
         * while a pool starts - once per initial connection and again on every
         * return - and a setter reads it however many times its own body happens
         * to, which is not a fact a test should have to know.
         */
        private static boolean fromFrame(String method) {
            for (StackTraceElement frame : new Throwable().getStackTrace()) {
                if (method.equals(frame.getMethodName())) {
                    return true;
                }
            }
            return false;
        }
    }

    @Test
    void twoConcurrentSweeperWritesCannotLeaveTheSweeperWithoutACleaner() throws Exception {
        int baseline = scheduledCleaners();
        HookedSweeperProperties properties = new HookedSweeperProperties();
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(properties);
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        sweeperOff(properties);
        ds.createPool();

        ds.setTestWhileIdle(true);
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "precondition: the trigger that enables the sweeper got its cleaner");

        CountDownLatch secondWriteDone = new CountDownLatch(1);
        Thread second = new Thread(() -> {
            try {
                ds.setSuspectTimeout(45);
            } finally {
                secondWriteDone.countDown();
            }
        }, "concurrent-sweeper-write");

        properties.onDecisionCheck = () -> {
            // The writer turning its trigger off is preempted right here, between
            // the reading its outcome is decided from and the stop that follows it.
            // The second write is started inside that window; whether it gets as
            // far as its own decision before this writer acts is what the fix
            // decides, and the end state is what the two agree on either way.
            second.start();
            try {
                secondWriteDone.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        };

        ds.setTestWhileIdle(false);
        second.join(5000);
        Assertions.assertFalse(second.isAlive(), "the second write must eventually run to completion");

        Assertions.assertTrue(ds.getPoolProperties().isPoolSweeperEnabled(),
                "precondition: the second writer's trigger leaves the sweeper enabled");
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "a write that decides the sweeper is no longer wanted must not stop a cleaner that "
                        + "a later write armed: the last word on the sweeper has to be the last write's");
    }

    /*
     * The same pair of writes through the pool's own MBean. Its accessors write
     * the very configuration the data source's setters write, but they hold no
     * monitor of their own, so the data source's monitor protects a data source
     * writer only from another data source writer.
     */
    @Test
    void twoConcurrentSweeperWritesThroughTheMBeanCannotLeaveTheSweeperWithoutACleaner() throws Exception {
        int baseline = scheduledCleaners();
        HookedSweeperProperties properties = new HookedSweeperProperties();
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(properties);
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        sweeperOff(properties);
        properties.setJmxEnabled(true);
        ds.createPool();
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = ds.getPool().getJmxPool();
        Assertions.assertNotNull(jmx, "precondition: a pool created with JMX enabled exposes its MBean");

        jmx.setTestWhileIdle(true);
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "precondition: the trigger that enables the sweeper got its cleaner");

        CountDownLatch secondWriteDone = new CountDownLatch(1);
        Thread second = new Thread(() -> {
            try {
                jmx.setMinEvictableIdleTimeMillis(60000);
            } finally {
                secondWriteDone.countDown();
            }
        }, "concurrent-mbean-sweeper-write");

        properties.onDecisionCheck = () -> {
            second.start();
            try {
                secondWriteDone.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        };

        jmx.setTestWhileIdle(false);
        second.join(5000);
        Assertions.assertFalse(second.isAlive(), "the second write must eventually run to completion");

        Assertions.assertTrue(ds.getPoolProperties().isPoolSweeperEnabled(),
                "precondition: the second writer's trigger leaves the sweeper enabled");
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "a stale stop decided by one MBean writer must not unregister the cleaner a later "
                        + "MBean write armed: the two surfaces share the pool cleaner");
    }

    /*
     * The write that lands in the last sliver of creation: after the arming has
     * read what the configuration asks for, but before the pool is published in
     * the data source's field, so the setter finds no pool to re-arm against and
     * the arming has already answered.
     */
    @Test
    void aSweeperTriggerTurnedOnAfterTheCreationDecidedStillGetsItsCleaner() throws Exception {
        int baseline = scheduledCleaners();
        HookedSweeperProperties properties = new HookedSweeperProperties();
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(properties);
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        sweeperOff(properties);

        // The write lands inside the arming's own read of the sweeper state:
        // the arming receives the value from before the write, and the setter
        // finds no published pool to re-arm against.
        properties.onArmingCheck = () -> ds.setTestWhileIdle(true);
        ds.createPool();

        Assertions.assertTrue(ds.getPoolProperties().isPoolSweeperEnabled(),
                "precondition: the write that landed during creation raised the sweeper");
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "a trigger that lands after the creation has decided still has to get its cleaner: "
                        + "the publication is the last point at which the creation can notice it");
    }

    /*
     * The same window from the other side: the write turns a trigger off after
     * the arming has read that it is on, so the creation arms a cleaner for a
     * configuration that has just been switched off.
     */
    @Test
    void aSweeperTriggerTurnedOffAfterTheCreationDecidedLeavesNoCleaner() throws Exception {
        int baseline = scheduledCleaners();
        HookedSweeperProperties properties = new HookedSweeperProperties();
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(properties);
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        sweeperOff(properties);
        properties.setTestWhileIdle(true);

        properties.onArmingCheck = () -> ds.setTestWhileIdle(false);
        ds.createPool();

        Assertions.assertFalse(ds.getPoolProperties().isPoolSweeperEnabled(),
                "precondition: the write that landed during creation disabled the sweeper");
        Assertions.assertEquals(baseline, scheduledCleaners(),
                "a cleaner armed from a reading the last write had already overtaken has to be "
                        + "taken down at the publication, not left running for a switched-off sweeper");
    }

    /*
     * Two of the sweeper's attributes are not stored as written: the eviction
     * interval is brought down to the max age whenever the max age is shorter,
     * because the sweeper is what retires idle sessions at their max age and an
     * interval longer than that lets them sit past it. That correction belongs to
     * the write, so a write that lands while the pool is still being built - after
     * the constructor's own check and before the pool can be reached, which is
     * where the setters' check is gated - has to be corrected at the publication,
     * or the pool ends up running an interval every other write path promises to
     * have shortened, with none of the warning an operator would have seen.
     */
    @Test
    void aMaxAgeWriteThatLandsWhileThePoolIsBeingBuiltIsStillCorrected() throws Exception {
        HookedSweeperProperties properties = new HookedSweeperProperties();
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(properties);
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        sweeperOff(properties);
        properties.setTimeBetweenEvictionRunsMillis(5000);
        properties.setTestWhileIdle(true);

        // The write lands inside the arming's own read: past the constructor's
        // check, before the pool is reachable.
        properties.onArmingCheck = () -> ds.setMaxAge(3000);
        ds.createPool();

        Assertions.assertTrue(ds.getPoolProperties().getTimeBetweenEvictionRunsMillis()
                        <= ds.getPoolProperties().getMaxAge(),
                "the eviction interval must not outlive the max age it exists to enforce, whichever "
                        + "side of the publication its write landed on");
        Assertions.assertEquals(ds.getPoolProperties().getTimeBetweenEvictionRunsMillis(),
                registeredCleanerPeriod(),
                "the registered task has to run on the interval the configuration ends up with");
    }

    @Test
    void anEvictionIntervalWriteThatLandsWhileThePoolIsBeingBuiltIsStillCorrected() throws Exception {
        HookedSweeperProperties properties = new HookedSweeperProperties();
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(properties);
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        sweeperOff(properties);
        properties.setMaxAge(3000);
        properties.setTimeBetweenEvictionRunsMillis(3000);

        properties.onArmingCheck = () -> ds.setTimeBetweenEvictionRunsMillis(9000);
        ds.createPool();

        Assertions.assertEquals(3000, ds.getPoolProperties().getTimeBetweenEvictionRunsMillis(),
                "an interval written during creation is still an interval that outlives the max age");
        Assertions.assertEquals(3000, registeredCleanerPeriod(),
                "a task armed before the correction has to be replaced by one on the corrected interval");
    }

    /*
     * The interval can also move as a side effect of a write to something else: the
     * configuration check brings it down to the max age, and a task's period is
     * fixed when it is built. No write through the max age or the interval setters
     * can leave the configuration in need of that correction, since both validate as
     * they write and the creation validates what is left over; the state therefore
     * has to be reached by writing the configuration object itself, which is the
     * surface every caller has through the getter and which runs no validation. What
     * the count setters are tested for is the next step: they are the write that
     * triggers the correction without being about the sweeper at all, and the cleaner
     * has to end up on the interval the configuration just became, because nothing
     * else is obliged to come and move it.
     */
    @Test
    void aCountWriteThatMovesTheEvictionIntervalMovesTheCleanerWithIt() throws Exception {
        PoolConfiguration properties = sweeperOff(new PoolProperties());
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(properties);
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        properties.setTimeBetweenEvictionRunsMillis(9000);
        properties.setTestWhileIdle(true);
        ds.createPool();

        Assertions.assertEquals(9000, registeredCleanerPeriod(),
                "precondition: the cleaner is armed on the interval the pool was created with");

        // Written past the setter, so nothing validates it: this is the pair out of
        // agreement that the check exists to put right.
        properties.setMaxAge(3000);
        Assertions.assertEquals(9000, properties.getTimeBetweenEvictionRunsMillis(),
                "precondition: the interval has not been corrected by the raw write");
        Assertions.assertEquals(9000, registeredCleanerPeriod(),
                "precondition: and the task still runs on the interval it was built with");

        ds.setMaxActive(4);

        Assertions.assertEquals(3000, properties.getTimeBetweenEvictionRunsMillis(),
                "precondition: the count write triggered the correction");
        Assertions.assertEquals(3000, registeredCleanerPeriod(),
                "a count write is not the moment to leave the cleaner running on an "
                        + "interval the configuration no longer asks for");
    }

    @Test
    void aCountWriteThroughTheMBeanThatMovesTheEvictionIntervalMovesTheCleanerWithIt() throws Exception {
        PoolConfiguration properties = sweeperOff(new PoolProperties());
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(properties);
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        properties.setJmxEnabled(true);
        properties.setTimeBetweenEvictionRunsMillis(9000);
        properties.setTestWhileIdle(true);
        ds.createPool();
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = ds.getPool().getJmxPool();
        Assertions.assertNotNull(jmx, "precondition: a pool created with JMX enabled exposes its MBean");

        Assertions.assertEquals(9000, registeredCleanerPeriod(),
                "precondition: the cleaner is armed on the interval the pool was created with");

        properties.setMaxAge(3000);
        Assertions.assertEquals(9000, properties.getTimeBetweenEvictionRunsMillis(),
                "precondition: the interval has not been corrected by the raw write");

        jmx.setMaxActive(4);

        Assertions.assertEquals(3000, properties.getTimeBetweenEvictionRunsMillis(),
                "precondition: the count write triggered the correction");
        Assertions.assertEquals(3000, registeredCleanerPeriod(),
                "the MBean and the data source run the same check, so neither may leave the "
                        + "task on the interval it replaced");
    }

    /*
     * A write that turns the sweeper off has to leave no cleaner behind, whatever
     * it happened to read on its way there. Before the decision was taken under the
     * pool's monitor, a disabling write preempted at its own first reading of the
     * sweeper state carried a stale "was not enabled" flag into the pool, neither
     * branch of the decision applied, and the cleaner a concurrent enable had just
     * armed stayed registered on the shared timer for a pool whose configuration
     * reported no sweeper at all.
     */
    @Test
    void aDisableWriteWithAStaleReadingDoesNotLeaveACleanerOnADisabledSweeper() throws Exception {
        int baseline = scheduledCleaners();
        HookedSweeperProperties properties = new HookedSweeperProperties();
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(properties);
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        sweeperOff(properties);
        properties.setJmxEnabled(true);
        ds.createPool();
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = ds.getPool().getJmxPool();

        CountDownLatch secondWriteDone = new CountDownLatch(1);
        Thread second = new Thread(() -> {
            try {
                jmx.setTestWhileIdle(true);
            } finally {
                secondWriteDone.countDown();
            }
        }, "concurrent-sweeper-enable");

        properties.onSweeperCheck = () -> {
            // The disabling write is preempted at its first look at the sweeper
            // state. The enable lands inside that window, so whatever this writer
            // learns on its way is already overtaken by the time it acts.
            second.start();
            try {
                secondWriteDone.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        };
        properties.checks.set(0);
        properties.fireOnCheck = 1;

        ds.setTimeBetweenEvictionRunsMillis(0);
        second.join(5000);
        Assertions.assertFalse(second.isAlive(), "the second write must eventually run to completion");

        Assertions.assertFalse(ds.getPoolProperties().isPoolSweeperEnabled(),
                "precondition: the last write switched the sweeper off, the interval gating every trigger");
        Assertions.assertEquals(baseline, scheduledCleaners(),
                "a write that turns the sweeper off has to take the cleaner down even when another "
                        + "write armed one while it was on its way");
    }

    /*
     * The same write through the pool's MBean, which writes the same configuration
     * and reaches the same decision by the other route.
     */
    @Test
    void aDisableWriteThroughTheMBeanWithAStaleReadingDoesNotLeaveACleanerOnADisabledSweeper() throws Exception {
        int baseline = scheduledCleaners();
        HookedSweeperProperties properties = new HookedSweeperProperties();
        DataSource ds = manage(new DataSource());
        ds.setPoolProperties(properties);
        ds.setUrl(getUrl());
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        sweeperOff(properties);
        properties.setJmxEnabled(true);
        ds.createPool();
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = ds.getPool().getJmxPool();

        CountDownLatch secondWriteDone = new CountDownLatch(1);
        Thread second = new Thread(() -> {
            try {
                jmx.setTestWhileIdle(true);
            } finally {
                secondWriteDone.countDown();
            }
        }, "concurrent-sweeper-enable-mbean");

        properties.onSweeperCheck = () -> {
            // As above: the preemption is the disabling write's own first reading
            // of the sweeper state, and the enable lands inside it.
            second.start();
            try {
                secondWriteDone.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        };
        properties.checks.set(0);
        properties.fireOnCheck = 1;

        jmx.setTimeBetweenEvictionRunsMillis(0);
        second.join(5000);
        Assertions.assertFalse(second.isAlive(), "the second write must eventually run to completion");

        Assertions.assertFalse(ds.getPoolProperties().isPoolSweeperEnabled(),
                "precondition: the last write switched the sweeper off");
        Assertions.assertEquals(baseline, scheduledCleaners(),
                "the MBean and the data source write the same configuration, so a disable through "
                        + "either of them has to leave no cleaner behind");
    }

    @Test
    void togglingASecondTriggerKeepsACleanerStartedForAnother() throws Exception {
        int baseline = scheduledCleaners();
        DataSource ds = poolWithSweeperOff();

        ds.setRemoveAbandoned(true);
        Assertions.assertEquals(baseline + 1, scheduledCleaners(), "precondition");

        // Suspect detection on while abandoned removal already enables the
        // sweeper: the existing cleaner stays, it is not stopped and doubled.
        ds.setSuspectTimeout(45);
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "enabling a second trigger while one already enables the sweeper must leave "
                        + "the running cleaner in place, not stop or duplicate it");

        ds.setSuspectTimeout(0);
        Assertions.assertEquals(baseline + 1, scheduledCleaners(),
                "dropping the second trigger must leave the cleaner the remaining trigger "
                        + "still needs running");
    }
}

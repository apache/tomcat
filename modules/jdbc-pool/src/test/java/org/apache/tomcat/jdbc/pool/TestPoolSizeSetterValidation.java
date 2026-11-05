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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The pool bounds its active and idle counts by relations, not by the stored
 * numbers: a limit below one, or an idle count above the active limit, is
 * corrected by {@link ConnectionPool#checkPoolConfiguration(PoolConfiguration)}
 * with a warning, because the stores themselves are plain assignments and that
 * method is the only place those relations exist. Every writer of these
 * attributes therefore runs the check as part of writing them - the pool's own
 * MBean always, the data source's max-age and eviction-interval setters always
 * - and three of the data source's count setters did not: a write through them
 * stored a value the project's own validator calls invalid, logged nothing, and
 * left it in force for the life of the data source.
 * <p>
 * The creation window matters as much as the live pool: the setters' repair
 * reaches only a published pool, so what completes the contract for a store
 * that lands while a pool is being built is that the writers take the same
 * monitor the publication takes, and every such write is then either fully
 * before the publication's own check or fully after it, where the repair finds
 * the pool.
 */
class TestPoolSizeSetterValidation extends JdbcPoolBaseTest {

    /*
     * Hooks the single read the publication check makes of the active limit
     * before its lower-bound rule decides - the stack is checkPoolConfiguration
     * called directly from pCreatePool, while the construction check runs under
     * ConnectionPool.init and borrow-path reads sit elsewhere. The hook releases
     * a racer parked before its setter call and waits briefly for the write to
     * land: against the guarded writers the racer queues for the monitor the
     * publication holds, the wait expires, and the store serialises after the
     * publication - where the setter's own repair finds the pool.
     */
    private static final class HookedMaxActiveProperties extends PoolProperties {

        private static final long serialVersionUID = 1L;

        volatile boolean armed;
        volatile boolean fired;
        volatile CountDownLatch release;
        volatile CountDownLatch finished;

        @Override
        public int getMaxActive() {
            if (armed && !fired && fromPublicationCheck()) {
                fired = true;
                int before = super.getMaxActive();
                release.countDown();
                try {
                    finished.await(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // The read being hooked has already happened: answer it with
                // what its reader saw, whatever the racer has stored since.
                return before;
            }
            return super.getMaxActive();
        }

        private boolean fromPublicationCheck() {
            StackTraceElement[] frames = new Throwable().getStackTrace();
            for (int i = 1; i < frames.length - 1; i++) {
                if ("checkPoolConfiguration".equals(frames[i].getMethodName())
                        && "pCreatePool".equals(frames[i + 1].getMethodName())) {
                    return true;
                }
            }
            return false;
        }
    }

    /*
     * The limit that has teeth: sessions are only ever created while the pool is
     * below maxActive, so a stored zero takes a live pool out of service - the
     * sessions already checked out keep working, no new one is ever created.
     */
    @Test
    void aMaxActiveWriteThroughTheDataDoesNotTakeALivePoolOutOfService() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(1);
        // Short enough that a pool that cannot create a session fails the
        // borrow instead of parking the caller for the default half minute.
        ds.getPoolProperties().setMaxWait(2000);
        Connection held = ds.getConnection();

        ds.setMaxActive(0);

        Assertions.assertTrue(ds.getPoolProperties().getMaxActive() >= 1,
                "a max active below one is a misconfiguration every other writer of it corrects, "
                        + "so it must not be the value a running pool is left with");
        try (Connection second = ds.getConnection()) {
            Assertions.assertTrue(second.isValid(1),
                    "the pool must still be able to create a session after its limit is written "
                            + "through the data source");
        } finally {
            held.close();
        }
    }

    /*
     * The two idle counts are bounded by the active limit, and the bound is what
     * makes them mean anything: written past it on a running pool they used to
     * stay there, disagreeing with the same numbers written at creation.
     */
    @Test
    void anIdleCountWrittenThroughTheDataIsBoundedByTheActiveLimit() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(4);
        ds.createPool();

        ds.setMaxIdle(100);
        Assertions.assertEquals(4, ds.getPoolProperties().getMaxIdle(),
                "idle connections cannot outnumber the active limit, and a number that pretends "
                        + "otherwise has to be corrected rather than stored");

        ds.setMinIdle(100);
        Assertions.assertEquals(4, ds.getPoolProperties().getMinIdle(),
                "the floor has to stay at or below the ceiling it is bounded by");
    }

    @Test
    void negativeIdleCountsAreCorrectedAtPoolCreation() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(4);
        ds.getPoolProperties().setMinIdle(-3);
        ds.getPoolProperties().setMaxIdle(-1);

        ds.createPool();

        Assertions.assertEquals(0, ds.getPoolProperties().getMinIdle(),
                "a negative idle floor has to be corrected like a negative initial size");
        Assertions.assertEquals(0, ds.getPoolProperties().getMaxIdle(),
                "a negative idle ceiling has to be corrected like a negative initial size");
    }

    /*
     * The control, and the reason this is treated as a gap rather than a choice:
     * the pool's own MBean takes the same three writes and corrects them, so the
     * same operator action leaves the two surfaces disagreeing with each other.
     */
    @Test
    void theSameWritesThroughThePoolsMBeanAreAlreadyCorrected() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setMaxActive(4);
        ds.getPoolProperties().setJmxEnabled(true);
        ds.createPool();
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmx = ds.getPool().getJmxPool();
        Assertions.assertNotNull(jmx, "precondition: a pool created with JMX enabled exposes its MBean");

        jmx.setMaxActive(0);
        Assertions.assertEquals(PoolProperties.DEFAULT_MAX_ACTIVE, ds.getPoolProperties().getMaxActive(),
                "precondition: the MBean surface already refuses a limit that admits no session");

        // Back to a bounded limit, since the repair above replaced it: the idle
        // bound is only interesting against a limit the idle count can exceed.
        jmx.setMaxActive(4);
        jmx.setMaxIdle(100);
        Assertions.assertEquals(4, ds.getPoolProperties().getMaxIdle(),
                "precondition: and already bounds the idle count on the active limit");
    }

    /*
     * The creation-window instance of the same contract. A count writer that
     * holds nothing can land its store inside the publication check - after
     * the lower-bound rule has read the old limit, while the setter's own
     * repair still finds no pool to report to - and the degenerate limit then
     * reaches callers through the published pool unrepaired, because nothing
     * after the check looks at counts again. The writers take the data
     * source's monitor to prevent exactly this, so the store is forced to
     * land right in the middle of the check here, and it must still end
     * repaired.
     */
    @Test
    void aMaxActiveStoreLandingDuringPoolPublicationIsNotPublishedUnrepaired() throws Exception {
        DataSource ds = new DataSource();
        HookedMaxActiveProperties properties = new HookedMaxActiveProperties();
        properties.setUrl(getUrl());
        properties.setDriverClassName("org.h2.Driver");
        properties.setUsername("sa");
        properties.setPassword("");
        properties.setJmxEnabled(false);
        properties.setInitialSize(1);
        properties.setMaxActive(4);
        properties.setMinIdle(0);
        properties.setMaxIdle(4);
        properties.setMaxWait(2000);
        ds.setPoolProperties(properties);
        manage(ds);

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        properties.release = release;
        properties.finished = finished;
        Thread racer = new Thread(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            ds.setMaxActive(0);
            finished.countDown();
        }, "store-during-publication");
        racer.setDaemon(true);
        racer.start();

        properties.armed = true;
        ds.createPool();
        racer.join(TimeUnit.SECONDS.toMillis(10));

        int published = ds.getPoolProperties().getMaxActive();
        Assertions.assertTrue(published >= 1,
                "a max active below one was published: the racing write landed while the "
                        + "publication check was reading the counts, its setter found the pool "
                        + "field null and reconciled nothing, and nothing repaired it later");
    }
}

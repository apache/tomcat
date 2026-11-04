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
package org.apache.tomcat.jdbc.pool.interceptor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.QueryStats;

/**
 * A read of the {@code SlowQueriesCD} attribute concurrent with query
 * recording. The statistics map is the live {@code ConcurrentHashMap} that
 * every borrower thread of the pool inserts into on every query and that
 * capacity eviction removes from, so monitoring while the pool is under
 * load is the normal operating pattern. The attribute value used to be
 * sized from the live map and then filled by iterating that map's weakly
 * consistent entry set: an entry added meanwhile pushed the fill loop past
 * the end of the array - an {@code ArrayIndexOutOfBoundsException} reaching
 * the JMX client as an exception on a plain attribute read - while an entry
 * removed meanwhile left trailing {@code null} elements that NPE any client
 * iterating the advertised array (the module's own tests do exactly that).
 * <p>
 * The race is provoked deterministically rather than by threading: the
 * statistics are seeded with a {@link QueryStats} whose JMX conversion
 * performs the concurrent map mutation, so the mutation lands exactly
 * between the size snapshot and the end of the fill loop, inside the very
 * read under test.
 */
class TestSlowQueryJmxSlowQueriesCDRace {

    /*
     * The fill loop walks the statistics map in CHM bin order: with the
     * map's default initial table of 16 bins that is ascending
     * (spread(key.hashCode()) & 15). The SQL below was chosen so that the
     * converting entry is visited first, and both the entry the conversion
     * adds and the entry it evicts sit in bins above MIDDLE's. When the
     * conversion runs, the iterator's cursor has reached at most the next
     * populated bin (MIDDLE's); the higher bins are still unscanned, so
     * the weakly consistent iterator must report an entry inserted there
     * by then (array overrun) and must skip one removed from there (null
     * hole) - as verified against the JDK's traverser.
     */
    private static final String FIRST = "SELECT BUCKET_17";  // bin 0
    private static final String MIDDLE = "SELECT BUCKET_13"; // bin 4
    private static final String EXTRA = "SELECT BUCKET_12";  // bin 5
    private static final String LAST = "SELECT BUCKET_2";    // bin 8

    /**
     * Stands in for the other borrower threads: the conversion JMX asks
     * for when publishing this entry performs the concurrent map mutation
     * exactly once, at the moment the attribute read is mid-way through
     * filling its result.
     */
    private static final class MutatingStats extends QueryStats {
        private final ConcurrentHashMap<String, QueryStats> live;
        private final String added;
        private final String evicted;
        private boolean done;

        MutatingStats(String query, ConcurrentHashMap<String, QueryStats> live,
                String added, String evicted) {
            super(query);
            this.live = live;
            this.added = added;
            this.evicted = evicted;
        }

        @Override
        public CompositeDataSupport getCompositeData(CompositeType type)
                throws OpenDataException {
            if (!done) {
                done = true;
                if (added != null) {
                    live.put(added, new QueryStats(added));
                }
                if (evicted != null) {
                    live.remove(evicted);
                }
            }
            return super.getCompositeData(type);
        }
    }

    private static int bin(String sql) {
        int hash = sql.hashCode();
        hash ^= (hash >>> 16);
        return hash & 15;
    }

    /*
     * The statistics of a pool name, registered directly rather than
     * through a live pool: the map is plain shared state keyed by name, and
     * the read under test only ever consults it. The unique name keeps the
     * static registry clean across tests.
     */
    private static ConcurrentHashMap<String, QueryStats> seedStats(String poolName) {
        assertBinsInvisitOrder();
        SlowQueryReport.PoolStatistics statistics = new SlowQueryReport.PoolStatistics();
        SlowQueryReport.perPoolStats.put(poolName, statistics);
        ConcurrentHashMap<String, QueryStats> live = statistics.getQueryStats();
        live.put(MIDDLE, new QueryStats(MIDDLE));
        live.put(LAST, new QueryStats(LAST));
        return live;
    }

    /* Guards the assumption the scenario is built on, documented above. */
    private static void assertBinsInvisitOrder() {
        Assertions.assertTrue(bin(FIRST) < bin(MIDDLE)
                        && bin(MIDDLE) < bin(EXTRA) && bin(MIDDLE) < bin(LAST),
                "the scenario needs FIRST visited first and LAST/EXTRA in bins "
                        + "above MIDDLE's, found bins " + bin(FIRST) + '/'
                        + bin(MIDDLE) + '/' + bin(EXTRA) + '/' + bin(LAST));
    }

    /* An interceptor bound to the name, as reset()/poolStarted() do. */
    private static SlowQueryReportJmx interceptorFor(String poolName) {
        SlowQueryReportJmx interceptor = new SlowQueryReportJmx();
        interceptor.poolName = poolName;
        return interceptor;
    }

    private static Set<String> queriesOf(CompositeData[] data) {
        Set<String> queries = new HashSet<>();
        for (CompositeData element : data) {
            Assertions.assertNotNull(element,
                    "the published array must not carry null elements for a "
                            + "JMX client to trip over");
            queries.add((String) element.get("query"));
        }
        return queries;
    }

    @Test
    void newQueryRecordedWhileTheAttributeIsFilledIsNotAnOverrun() {
        String poolName = "JmxRaceAdd-" + System.nanoTime();
        try {
            ConcurrentHashMap<String, QueryStats> live = seedStats(poolName);
            live.put(FIRST, new MutatingStats(FIRST, live, EXTRA, null));

            CompositeData[] result = Assertions.assertDoesNotThrow(
                    interceptorFor(poolName)::getSlowQueriesCD,
                    "recording a new query while the attribute read converts "
                            + "the statistics must not fail the JMX client");

            Assertions.assertEquals(3, result.length,
                    "the result covers the statistics as of the read, not the "
                            + "entry recorded after the read began");
            Assertions.assertEquals(new HashSet<>(Arrays.asList(
                            FIRST, MIDDLE, LAST)), queriesOf(result),
                    "the result must cover exactly the queries recorded before "
                            + "the read began, without null padding");
            Assertions.assertTrue(live.containsKey(EXTRA),
                    "the scenario's mutation must actually have run");
        } finally {
            SlowQueryReport.perPoolStats.remove(poolName);
        }
    }

    @Test
    void queryEvictedWhileTheAttributeIsFilledIsNotANullHole() {
        String poolName = "JmxRaceRemove-" + System.nanoTime();
        try {
            ConcurrentHashMap<String, QueryStats> live = seedStats(poolName);
            live.put(FIRST, new MutatingStats(FIRST, live, null, LAST));

            CompositeData[] result = Assertions.assertDoesNotThrow(
                    interceptorFor(poolName)::getSlowQueriesCD,
                    "the read itself may not fail");

            Assertions.assertEquals(3, result.length,
                    "the result covers the statistics as of the read, including "
                            + "the entry the eviction removed meanwhile");
            Assertions.assertEquals(new HashSet<>(Arrays.asList(
                            FIRST, MIDDLE, LAST)), queriesOf(result));
            Assertions.assertFalse(live.containsKey(LAST),
                    "the scenario's eviction must actually have run");
        } finally {
            SlowQueryReport.perPoolStats.remove(poolName);
        }
    }
}

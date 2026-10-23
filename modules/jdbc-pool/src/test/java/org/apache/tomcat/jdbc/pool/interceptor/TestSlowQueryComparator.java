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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.QueryStats;
import org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport.QueryStatsComparator;

/**
 * The statistics comparator the slow query report sorts its results with,
 * checked in isolation. Queries that were never invoked must sort last,
 * and the ordering must be a consistent total order - an inconsistent
 * comparator makes {@code Collections.sort} throw, which used to take the
 * reporting MBean down with it (bug 58489).
 *
 * <p>Ported from the retired legacy {@code TestSlowQueryComparator}.</p>
 */
class TestSlowQueryComparator {

    private final Comparator<QueryStats> comparator = new QueryStatsComparator();

    @Test
    void sortingARealisticMixOfInvokedAndNeverInvokedQueriesSucceeds() {
        long[] testData = { 0, 0, 0, 1444225382010L, 0, 1444225382011L, 0,
                1444225382012L, 0, 1444225382056L, 0, 1444225382014L, 0,
                1444225382015L, 0, 1444225382016L, 0, 0, 1444225382017L, 0,
                1444225678350L, 0, 1444225680397L, 0, 1444225382018L,
                1444225382019L, 1444225382020L, 0, 1444225382021L, 0,
                1444225382022L, 1444225382023L
        };

        List<QueryStats> stats = new ArrayList<>();
        for (int i = 0; i < testData.length; i++) {
            QueryStats qs = new QueryStats(String.valueOf(i));
            qs.add(0, testData[i]);
            stats.add(qs);
        }

        assertDoesNotThrow(() -> Collections.sort(stats, comparator),
                "sorting the collected statistics must not fail");
    }

    @Test
    void aQueryComparedWithItselfIsEqual() {
        QueryStats neverInvoked = new QueryStats("abc");
        assertEquals(0, comparator.compare(neverInvoked, neverInvoked));

        QueryStats invoked = new QueryStats("abc");
        invoked.add(0, 100);
        assertEquals(0, comparator.compare(invoked, invoked));
    }

    @Test
    void aQueryThatWasNeverInvokedSortsLast() {
        QueryStats neverInvoked = new QueryStats("abc");
        QueryStats invoked = new QueryStats("def");
        invoked.add(0, 100);

        assertEquals(1, comparator.compare(neverInvoked, invoked),
                "a query without invocations must sort after an invoked one");
        assertEquals(-1, comparator.compare(invoked, neverInvoked));
    }

    @Test
    void queriesInvokedAtTheSameTimeAreEqual() {
        QueryStats first = new QueryStats("abc");
        QueryStats second = new QueryStats("def");
        first.add(0, 100);
        second.add(0, 100);

        assertEquals(0, comparator.compare(first, second));
        assertEquals(0, comparator.compare(second, first));
    }

    @Test
    void theMoreRecentlyInvokedQuerySortsLast() {
        QueryStats older = new QueryStats("abc");
        QueryStats newer = new QueryStats("abc");
        older.add(0, 100);
        newer.add(0, 150);

        assertEquals(-1, comparator.compare(older, newer));
        assertEquals(1, comparator.compare(newer, older));
    }
}

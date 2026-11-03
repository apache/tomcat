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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The borrow site of a pooled connection is kept as stack frames and only
 * formatted into a trace when an abandoned- or suspect-connection report asks
 * for one, and the formatted trace is then cached. The fields behind that
 * deferred formatting move together on every borrow, and they move without a
 * lock wherever {@code con.lock()} is the no-op it is by default - which is
 * also the configuration in which the abandoned check runs only when JMX
 * invokes it, so the reader formats outside any lock too. Formatting the older
 * site and then caching it unconditionally would cache it over a newer borrow
 * that completed meanwhile, erase that newer site, and have the next report
 * show the previous borrower's call as the current one's. The cache is for
 * the site still current and for nobody else.
 */
class TestBorrowSiteTrace {

    private static StackTraceElement[] site(String marker) {
        // A deep site keeps the formatting in flight long enough that the
        // race below is reached rather than merely possible.
        StackTraceElement[] frames = new StackTraceElement[60000];
        for (int i = 0; i < frames.length - 1; i++) {
            frames[i] = new StackTraceElement("FillerClass", "fillerMethod" + i, "FillerClass.java", i);
        }
        frames[frames.length - 1] = new StackTraceElement(marker, "entryMethod", marker + ".java", 1);
        return frames;
    }

    private static PooledConnection pooledConnection() throws Exception {
        PoolProperties properties = new PoolProperties();
        properties.setInitialSize(0);
        properties.setTimeBetweenEvictionRunsMillis(-1);
        return new PooledConnection(properties, new ConnectionPool(properties));
    }

    @Test
    void aDeferredBorrowSiteFormatsIntoTheReport() throws Exception {
        PooledConnection con = pooledConnection();
        con.setStackTraceElements(site("FirstBorrowSite"));

        String trace = con.getStackTrace();
        Assertions.assertNotNull(trace);
        Assertions.assertTrue(trace.contains("FirstBorrowSite"),
                "the report must show the borrow site it was handed");
        Assertions.assertEquals(trace, con.getStackTrace(),
                "the formatted trace is cached, so a second reader pays no reformatting");
    }

    @Test
    void anOlderSiteBeingFormattedDoesNotEraseANewerOne() throws Exception {
        PooledConnection con = pooledConnection();
        StackTraceElement[] stale = site("StaleBorrowSite");
        StackTraceElement[] fresh = site("FreshBorrowSite");

        // Each round leaves a deep site pending, starts readers formatting it,
        // and re-borrows the connection while that formatting runs. However
        // the steps interleave, the site of the latest borrow must survive:
        // either a formatting finished before the re-borrow (and the re-borrow
        // overwrote the cache), or it finished after (and it may not publish
        // over the newer site). The latter is the whole point: a reader that
        // checks the site is still current and then writes is still open to a
        // re-borrow slipping between the two, which is why the publication is
        // atomic against the borrow - so here it must hold with two readers
        // in flight and no delay to line the steps up.
        for (int round = 0; round < 12; round++) {
            con.setStackTraceElements(stale);
            Thread first = new Thread(con::getStackTrace, "trace-formatter-1");
            Thread second = new Thread(con::getStackTrace, "trace-formatter-2");
            first.start();
            second.start();
            con.setStackTraceElements(fresh);
            first.join(10000);
            second.join(10000);

            String trace = con.getStackTrace();
            Assertions.assertNotNull(trace, "the fresh borrow site must still be there to report");
            Assertions.assertTrue(trace.contains("FreshBorrowSite"),
                    "the newest borrow site must never be lost to a formatting of the older one");
            Assertions.assertFalse(trace.contains("StaleBorrowSite"),
                    "a report of the current borrow must not read as the previous borrower's call");
        }
    }
}

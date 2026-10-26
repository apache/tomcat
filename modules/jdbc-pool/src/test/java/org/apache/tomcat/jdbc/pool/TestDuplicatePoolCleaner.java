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

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A pool must never have more than one pool cleaner scheduled. Two
 * overlapping sweeper-enable updates (the JMX attribute path runs
 * check-then-act without any lock) otherwise overwrite the pool's cleaner
 * reference while the replaced task stays registered on the shared timer,
 * duplicating the maintenance work for as long as the pool is reachable,
 * with no reference left to stop it.
 */
class TestDuplicatePoolCleaner extends JdbcPoolBaseTest {

    @Test
    void closingThePoolUnregistersEveryCleanerItStarted() throws Exception {
        int baseline = ConnectionPool.getPoolCleaners().size();
        DataSource ds = newDataSource();
        ds.createPool();
        ConnectionPool pool = ds.getPool();

        // The pool's own initialization already started one cleaner; a
        // second initializer run is what an overlapping sweeper-enable
        // update performs (the sequential shape reproduces the outcome of
        // the race deterministically).
        pool.initializePoolCleaner(ds.getPoolProperties());

        ds.close();

        Assertions.assertEquals(baseline, ConnectionPool.getPoolCleaners().size(),
                "closing the pool must unregister every cleaner it started; "
                        + "a replaced cleaner must not stay scheduled");
    }
}

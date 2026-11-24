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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A pool keeps a private copy of the configuration it was built from. The
 * caller's object stops being a configuration surface at construction: a
 * direct write to it must not reach the pool's operational readers, which
 * would otherwise bypass the validation, snapshot publication and cleaner
 * reconciliation of the single apply path.
 */
abstract class RetainedBuilderConfigurationBase extends JdbcPoolBaseTest {

    @Test
    void aRetainedBuilderWriteCannotAbandonAnInUseConnection() throws Exception {
        DataSource ds = newDataSource();
        PoolProperties retained = (PoolProperties) ds.getPoolProperties();
        retained.setMaxActive(10);
        retained.setMaxIdle(10);
        retained.setRemoveAbandoned(true);
        retained.setRemoveAbandonedTimeout(1);
        retained.setAbandonWhenPercentageFull(20);
        // A very long cleaner interval keeps this test deterministic: the
        // abandonment check below is invoked directly.
        retained.setTimeBetweenEvictionRunsMillis(-1);
        ds.createPool();

        Connection held = ds.getConnection();
        Assertions.assertEquals(1, ds.getPool().getActive(),
                "precondition: one connection is in use");
        sleep(1500);

        // One of ten in use is below the 20% threshold, so the connection is
        // not abandoned. The retained builder now says maxActive=0, which
        // would make shouldAbandon() see an infinite percentage if the pool
        // still read that object.
        retained.setMaxActive(0);
        ds.getPool().checkAbandoned();

        Assertions.assertEquals(1, ds.getPool().getActive(),
                "a direct write to the caller's configuration must not abandon "
                        + "a connection the pool's applied configuration protects");
        Assertions.assertFalse(held.isClosed(),
                "the borrower's handle must stay usable");
        Assertions.assertEquals(10, ds.getPool().getPoolProperties().getMaxActive(),
                "the live view must keep the applied configuration");
        Assertions.assertEquals(10, ds.getPool().getCurrentConfiguration().getMaxActive(),
                "the connect snapshot must keep the applied configuration");

        held.close();
    }

    @Test
    void theCallersConfigurationIsNoLongerMutatedByLiveWrites() throws Exception {
        DataSource ds = newDataSource();
        PoolProperties retained = (PoolProperties) ds.getPoolProperties();
        int original = retained.getMaxActive();
        ds.createPool();

        ds.getPoolProperties().setMaxActive(7);
        Assertions.assertEquals(original, retained.getMaxActive(),
                "a live write must not mutate the caller's configuration object");
    }
}

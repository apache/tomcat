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
 * What a failed borrow says about itself to a caller that classifies
 * {@link java.sql.SQLException}s by state.
 *
 * <p>The pool-exhaustion exits throw {@link PoolExhaustedException},
 * and it used to leave {@code getSQLState()} null: an application
 * multiplexing many {@code SQLException} sources could only recognize
 * pool exhaustion by referencing the pool's class or by matching
 * message text. The exhaustion exception now carries its documented
 * state, stamped by the constructor the pool throws through, so a
 * state-based classifier sees the condition.</p>
 */
class TestPoolExhaustionSqlState extends JdbcPoolBaseTest {

    /**
     * The class-level contract: the message-only constructor - the one
     * both the no-wait refusal and the wait timeout throw through -
     * stamps the documented state, while a constructor given an
     * explicit state keeps that state.
     */
    @Test
    void messageConstructorStampsTheStateAndAnExplicitStateWins() {
        Assertions.assertEquals(PoolExhaustedException.SQL_STATE,
                new PoolExhaustedException("pool empty").getSQLState(),
                "the message-only exhaustion must carry the documented "
                        + "state, not a null one");
        Assertions.assertEquals("08001",
                new PoolExhaustedException("pool empty", "08001").getSQLState(),
                "an explicitly given state must be kept as given");
    }

    /**
     * The application-visible end of the contract: the exhaustion a
     * timing-out borrow fails with reaches the caller carrying the
     * state, not null.
     */
    @Test
    void waitTimeoutReachesTheCallerWithTheExhaustionState() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setMaxWait(250);
        ds.createPool();
        try (Connection held = ds.getConnection()) {
            PoolExhaustedException exhausted = Assertions.assertThrows(
                    PoolExhaustedException.class, ds::getConnection,
                    "a timing-out borrow on an exhausted pool must fail "
                            + "with the exhaustion type");
            Assertions.assertEquals(PoolExhaustedException.SQL_STATE,
                    exhausted.getSQLState(),
                    "the exhaustion that reaches the caller must carry the "
                            + "documented state, not a null one");
        }
    }
}

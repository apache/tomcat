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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * Pool shutdown routinely runs on container-stop threads that already
 * carry an interrupt status. The drain in {@code close()} must then still
 * close every physical connection rather than spin on polls that throw
 * immediately because the interrupt status is pending.
 */
class TestInterruptedPoolClose extends JdbcPoolBaseTest {

    private static final int CONNECTIONS = 3;

    /* Captures the interrupt status observed by the thread that closed. */
    private static final class CloseOutcome {
        private volatile boolean interruptedAfterClose;
    }

    private CloseOutcome closeFromInterruptedThread(DataSource ds) throws InterruptedException {
        CloseOutcome outcome = new CloseOutcome();
        Thread closer = new Thread(() -> {
            Thread.currentThread().interrupt(); // as a stop thread may carry
            ds.close(true);
            outcome.interruptedAfterClose = Thread.currentThread().isInterrupted();
        }, "interrupted-closer");
        closer.setDaemon(true);
        closer.start();
        closer.join(AWAIT_TIMEOUT_MS);
        Assertions.assertFalse(closer.isAlive(), "close() must complete on an interrupted thread");
        return outcome;
    }

    @Test
    void interruptedForceCloseClosesBorrowedConnections() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setPropagateInterruptState(true);
        ds.createPool();
        for (int i = 0; i < CONNECTIONS; i++) {
            ds.getConnection(); // stay borrowed - these sit in the busy queue
        }
        awaitSessions(CONNECTIONS);

        CloseOutcome outcome = closeFromInterruptedThread(ds);

        Assertions.assertTrue(outcome.interruptedAfterClose,
                "propagateInterruptState=true must keep the interrupt status across close()");
        awaitSessions(0);
    }

    @Test
    void interruptedForceCloseClosesIdleAndBorrowedConnections() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setPropagateInterruptState(true);
        ds.createPool();
        List<Connection> idleOnes = new ArrayList<>();
        for (int i = 0; i < CONNECTIONS * 2; i++) {
            idleOnes.add(ds.getConnection());
        }
        for (int i = 0; i < CONNECTIONS; i++) {
            idleOnes.get(i).close(); // returned - these sit in the idle queue
        }
        awaitSessions(CONNECTIONS * 2);

        // the drain starts on the idle queue and must then switch to busy
        CloseOutcome outcome = closeFromInterruptedThread(ds);

        Assertions.assertTrue(outcome.interruptedAfterClose,
                "propagateInterruptState=true must keep the interrupt status across close()");
        awaitSessions(0);
    }

    @Test
    void interruptedCloseWithoutPropagationClearsInterruptStatus() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.createPool(); // propagateInterruptState is false by default
        for (int i = 0; i < CONNECTIONS; i++) {
            ds.getConnection();
        }
        awaitSessions(CONNECTIONS);

        CloseOutcome outcome = closeFromInterruptedThread(ds);

        Assertions.assertFalse(outcome.interruptedAfterClose,
                "propagateInterruptState=false must not leave the interrupt status set");
        awaitSessions(0);
    }
}

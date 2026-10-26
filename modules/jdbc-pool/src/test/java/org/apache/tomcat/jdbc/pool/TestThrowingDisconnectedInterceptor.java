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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A configured interceptor whose {@code disconnected()} hook throws must
 * not be able to strand physical database connections or to keep later
 * interceptors in the chain from being notified: the disconnect path
 * isolates the hook from the physical close and every link of the chain
 * from every other.
 */
class TestThrowingDisconnectedInterceptor extends JdbcPoolBaseTest {

    public static class BoomInterceptor extends JdbcInterceptor {
        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // empty
        }

        @Override
        public void disconnected(ConnectionPool parent, PooledConnection con,
                boolean finalizing) {
            throw new IllegalStateException("boom from disconnected()");
        }
    }

    public static class RecordingInterceptor extends JdbcInterceptor {
        static final AtomicInteger DISCONNECTED_CALLS = new AtomicInteger();

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // empty
        }

        @Override
        public void disconnected(ConnectionPool parent, PooledConnection con,
                boolean finalizing) {
            DISCONNECTED_CALLS.incrementAndGet();
        }
    }

    @Test
    void throwingDisconnectedInterceptorDoesNotLeakPhysicalConnections() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setJdbcInterceptors(BoomInterceptor.class.getName());
        Connection first = ds.getConnection();
        Connection second = ds.getConnection();
        // Hold strong references to the physical connections for the whole
        // test: an abandoned, unclosed physical connection is otherwise
        // reclaimed by the JDK's socket cleaner and the leaked database
        // sessions would disappear with GC timing instead of staying open
        java.sql.Connection physicalFirst =
                ((PooledConnection) first.unwrap(PooledConnection.class)).getConnection();
        java.sql.Connection physicalSecond =
                ((PooledConnection) second.unwrap(PooledConnection.class)).getConnection();
        awaitSessions(2);
        first.close();
        second.close();
        // closing the pool disconnects both connections; the hook throws
        // for both, the physical connections must still be closed
        ds.close();
        awaitSessions(0);
        Assertions.assertTrue(physicalFirst.isClosed(),
                "the physical connection must be closed despite the throwing hook");
        Assertions.assertTrue(physicalSecond.isClosed(),
                "the physical connection must be closed despite the throwing hook");
    }

    @Test
    void throwingLinkDoesNotAbortTheDisconnectedChain() throws Exception {
        RecordingInterceptor.DISCONNECTED_CALLS.set(0);
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setJdbcInterceptors(
                BoomInterceptor.class.getName() + ";" + RecordingInterceptor.class.getName());
        Connection con = ds.getConnection();
        // keep the physical connection strongly reachable (see above)
        java.sql.Connection physical =
                ((PooledConnection) con.unwrap(PooledConnection.class)).getConnection();
        con.close();
        ds.close();
        Assertions.assertEquals(1, RecordingInterceptor.DISCONNECTED_CALLS.get(),
                "interceptors after a throwing link must still receive disconnected()");
        awaitSessions(0);
        Assertions.assertTrue(physical.isClosed(),
                "the physical connection must be closed despite the throwing link");
    }
}

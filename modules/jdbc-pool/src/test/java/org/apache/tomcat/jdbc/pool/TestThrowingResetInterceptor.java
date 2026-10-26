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
 * An interceptor whose shutdown-time {@code reset(null, null)} throws must
 * not abort the drain loop of {@code close()}: every other pooled
 * connection must still be disconnected, and the remaining interceptors
 * in the chain must still receive their reset callback.
 */
class TestThrowingResetInterceptor extends JdbcPoolBaseTest {

    public static class ThrowResetInterceptor extends JdbcInterceptor {
        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            if (con == null) {
                throw new IllegalStateException("boom from reset(null,null)");
            }
        }
    }

    public static class RecordingInterceptor extends JdbcInterceptor {
        static final AtomicInteger SHUTDOWN_RESETS = new AtomicInteger();

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            if (con == null) {
                SHUTDOWN_RESETS.incrementAndGet();
            }
        }
    }

    @Test
    void throwingShutdownResetDoesNotAbortThePoolClose() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setJdbcInterceptors(ThrowResetInterceptor.class.getName());
        Connection first = ds.getConnection();
        Connection second = ds.getConnection();
        // Hold strong references to the physical connections for the whole
        // test: an abandoned, unclosed physical connection is otherwise
        // reclaimed by the JDK's socket cleaner and the leaked database
        // sessions would disappear with GC timing instead of staying open
        PooledConnection pooledFirst =
                (PooledConnection) first.unwrap(PooledConnection.class);
        PooledConnection pooledSecond =
                (PooledConnection) second.unwrap(PooledConnection.class);
        java.sql.Connection physicalFirst = pooledFirst.getConnection();
        java.sql.Connection physicalSecond = pooledSecond.getConnection();
        awaitSessions(2);
        first.close();
        second.close();
        // closing the pool resets both connections; the shutdown hook
        // throws for both, every connection must still be disconnected
        ds.close();
        awaitSessions(0);
        Assertions.assertTrue(physicalFirst.isClosed(),
                "the physical connection must be closed despite the throwing reset");
        Assertions.assertTrue(physicalSecond.isClosed(),
                "the physical connection must be closed despite the throwing reset");
        Assertions.assertNull(pooledFirst.getHandler(),
                "a destroyed connection must not keep a stale interceptor chain");
        Assertions.assertNull(pooledSecond.getHandler(),
                "a destroyed connection must not keep a stale interceptor chain");
    }

    @Test
    void throwingLinkDoesNotAbortTheShutdownResetChain() throws Exception {
        RecordingInterceptor.SHUTDOWN_RESETS.set(0);
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setJdbcInterceptors(
                ThrowResetInterceptor.class.getName() + ";" + RecordingInterceptor.class.getName());
        Connection con = ds.getConnection();
        // keep the physical connection strongly reachable (see above)
        java.sql.Connection physical =
                ((PooledConnection) con.unwrap(PooledConnection.class)).getConnection();
        con.close();
        ds.close();
        // a shutdown reset walks the chain twice by existing design
        // (once from finalize(), once from release()'s setHandler(null)),
        // so only the presence of a callback past the throwing link is pinned
        Assertions.assertTrue(RecordingInterceptor.SHUTDOWN_RESETS.get() >= 1,
                "interceptors after a throwing link must still be reset for shutdown");
        awaitSessions(0);
        Assertions.assertTrue(physical.isClosed(),
                "the physical connection must be closed despite the throwing reset");
    }
}

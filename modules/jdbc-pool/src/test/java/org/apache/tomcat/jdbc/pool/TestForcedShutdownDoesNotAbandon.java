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
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.Notification;
import javax.management.NotificationListener;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The drain that {@code close(true)} runs over the busy queue tears down
 * connections that are legitimately in use, on the operator's order. That
 * is not an application leaking a connection, which is the one thing the
 * pool's abandonment bookkeeping - the {@code removeAbandonedCount}
 * counter, the {@code logAbandoned} WARN and the {@code NOTIFY_ABANDON}
 * JMX notification - reports, and it belongs to
 * {@link ConnectionPool#checkAbandoned()} alone. The forced shutdown must
 * therefore drop every one of the leak accounting, the warning and the
 * notification, while still fully disconnecting and draining the pool:
 * the borrowed handles end up unusable, no physical session survives the
 * close, and the borrowers' late returns stay quiet no-ops.
 */
class TestForcedShutdownDoesNotAbandon extends JdbcPoolBaseTest {

    @Test
    void forcedShutdownReleasesBusyConnectionsWithoutRecordingThemAsAbandoned()
            throws Exception {
        DataSource ds = newDataSource();
        PoolConfiguration properties = ds.getPoolProperties();
        properties.setJmxEnabled(true);
        properties.setName("ForcedShutdown-" + System.nanoTime());
        // the WARN the abandon path logs is gated on this; the shutdown
        // drain must stay silent under it as well as without it
        properties.setLogAbandoned(true);
        properties.setInitialSize(0);
        ds.createPool();
        ConnectionPool pool = ds.getPool();
        org.apache.tomcat.jdbc.pool.jmx.ConnectionPool jmxPool = pool.getJmxPool();
        Assertions.assertNotNull(jmxPool, "the fixture must have created the pool MBean");

        List<Notification> abandoned = new CopyOnWriteArrayList<>();
        NotificationListener abandonedListener = (notification, handback) -> {
            if (org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.NOTIFY_ABANDON
                    .equals(notification.getType())) {
                abandoned.add(notification);
            }
        };
        jmxPool.addListener(abandonedListener);

        // two connections legitimately in use when the shutdown is ordered
        Connection first = ds.getConnection();
        Connection second = ds.getConnection();
        Assertions.assertEquals(2, poolSessions(),
                "the fixture must own both physical sessions before the close");

        ds.close(true);

        Assertions.assertEquals(0, pool.getRemoveAbandonedCount(),
                "a connection that was in use during a forced shutdown was not"
                        + " abandoned by the application and must not inflate the"
                        + " removed-abandoned counter");

        // Delivery runs on the pool's single notifier thread in the order the
        // notifications were raised, so a sentinel queued behind the close is
        // only reached once everything the shutdown itself notified of (had it
        // notified at all) has been delivered.
        List<Notification> flushed = new CopyOnWriteArrayList<>();
        NotificationListener sentinelListener = (notification, handback) -> {
            if (org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.POOL_EMPTY
                    .equals(notification.getType())) {
                flushed.add(notification);
            }
        };
        jmxPool.addListener(sentinelListener);
        Assertions.assertTrue(jmxPool.notify(
                org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.POOL_EMPTY, "flush sentinel"),
                "the fixture must be able to queue its sentinel notification");
        awaitUntil(() -> !flushed.isEmpty());
        Assertions.assertTrue(abandoned.isEmpty(),
                "a forced shutdown must not report a leak to JMX, got "
                        + abandoned.size() + " abandon notifications");

        // What the shutdown must still do, unchanged: the handle it took away
        // is disconnected and refuses use
        Assertions.assertTrue(first.isClosed(),
                "the forced shutdown must disconnect the connection it takes away");
        Assertions.assertThrows(SQLException.class, first::createStatement,
                "a handle whose session the shutdown tore down must refuse use");

        // and the pool drained completely: the in-use sessions left with it
        awaitSessions(0);
        Assertions.assertEquals(0, pool.getSize(),
                "the forced shutdown must release every connection it drained");

        // the late return of a dead handle is a quiet no-op that neither
        // fails nor counts anything
        first.close();
        second.close();
        Assertions.assertEquals(0, pool.getSize(),
                "returning an already released handle must not move the pool size");
        Assertions.assertEquals(0, pool.getRemoveAbandonedCount(),
                "a late return to a closed pool must not be counted as abandoned");
    }
}

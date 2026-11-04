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

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.Notification;
import javax.management.NotificationListener;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The abandoned-connection trace is recorded as the borrow-site stack
 * trace elements and is formatted into the reported thread-dump string
 * only when a report reads it. A pool with {@code logAbandoned} borrows
 * constantly and reports rarely, so a borrow that is never reported must
 * not have paid for - or produced - a formatted dump, while the text an
 * abandoned connection does report must be exactly what the eager
 * formatting always produced: a {@code java.lang.Exception} header and
 * the frames of the borrowing thread, delivered to the log and to the
 * pool's JMX listeners.
 *
 * <p>Scenario harvested from pull request 1048, whose test drove the
 * pool's internals with hand-built subclasses; here the deferral is read
 * off a real borrow and the reported text is captured from the JMX
 * notification an actual abandonment sends.</p>
 */
class TestAbandonedTrace extends JdbcPoolBaseTest {

    /*
     * The trace fields are private and the point of the change is what
     * they hold between the borrow and the first read, so they are read
     * directly - the way the suite's other fixture reaches behind the
     * pool's own accessors.
     */
    private static Object traceField(PooledConnection pooled, String name)
            throws ReflectiveOperationException {
        Field field = PooledConnection.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(pooled);
    }

    /* The pooled connection behind a borrowed handle. */
    private static PooledConnection pooledOf(Connection borrowed) {
        return interceptorOf(borrowed, ProxyConnection.class).getConnection();
    }

    @Test
    void borrowStoresTheBorrowSiteAndFormatsItOnlyOnFirstRead() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setLogAbandoned(true);

        Connection borrowed = ds.getConnection();
        try {
            PooledConnection pooled = pooledOf(borrowed);
            Assertions.assertNull(traceField(pooled, "abandonTrace"),
                    "a borrow with logAbandoned must not format a thread dump up front");
            StackTraceElement[] stored =
                    (StackTraceElement[]) traceField(pooled, "abandonTraceElements");
            Assertions.assertNotNull(stored,
                    "the borrow must record the borrow site, unformatted");

            String trace = pooled.getStackTrace();
            Assertions.assertTrue(trace.startsWith("java.lang.Exception"),
                    "the reported text must keep the header the eager formatting produced: "
                            + trace.substring(0, Math.min(trace.length(), 40)));
            Assertions.assertTrue(trace.contains("TestAbandonedTrace"),
                    "the trace must name the borrowing class");
            Assertions.assertTrue(trace.contains("borrowStoresTheBorrowSiteAndFormatsItOnlyOnFirstRead"),
                    "the trace must name the borrowing method");

            // a report reads the trace twice - it logs it and notifies of
            // it - and must see one and the same text both times
            Assertions.assertEquals(trace, pooled.getStackTrace());
            Assertions.assertSame(trace, pooled.getStackTrace(),
                    "the formatted trace must be cached, not reformatted per reading");
            Assertions.assertNull(traceField(pooled, "abandonTraceElements"),
                    "the elements must not be kept once they have been formatted");
        } finally {
            borrowed.close();
        }
    }

    @Test
    void anAbandonedBorrowReportsTheBorrowSiteTrace() throws Exception {
        DataSource ds = newDataSource();
        PoolConfiguration properties = ds.getPoolProperties();
        properties.setName("AbandonedTrace-" + System.nanoTime());
        properties.setJmxEnabled(true);
        properties.setLogAbandoned(true);
        properties.setRemoveAbandoned(true);
        // abandon a connection held for one second, checked four times a second
        properties.setRemoveAbandonedTimeout(1);
        properties.setTimeBetweenEvictionRunsMillis(250);
        properties.setInitialSize(0);
        ds.createPool();

        List<Notification> abandoned = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> {
            if (org.apache.tomcat.jdbc.pool.jmx.ConnectionPool.NOTIFY_ABANDON
                    .equals(notification.getType())) {
                abandoned.add(notification);
            }
        };
        ds.getPool().getJmxPool().addListener(listener);

        // deliberately never returned: the pool is to abandon this borrow,
        // whose trace was recorded unformatted at the borrow above
        ds.getConnection();

        awaitUntil(() -> !abandoned.isEmpty());
        String trace = String.valueOf(abandoned.get(0).getMessage());
        Assertions.assertTrue(trace.contains("TestAbandonedTrace"),
                "the abandoned notification must carry the borrow-site trace: " + trace);
        Assertions.assertTrue(trace.contains("anAbandonedBorrowReportsTheBorrowSiteTrace"),
                "the abandoned notification must carry the borrowing method's frame: " + trace);

        // the abandoned connection went out of service with its session
        awaitSessions(0);
    }
}

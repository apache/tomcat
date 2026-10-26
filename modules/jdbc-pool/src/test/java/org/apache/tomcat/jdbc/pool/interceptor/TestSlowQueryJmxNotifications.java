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

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * JMX notifications of the {@code SlowQueryReportJmx} interceptor in
 * the documented {@code notifyPool=false} mode. Interceptor chains are
 * created per pooled connection, while JMX clients attach their
 * listeners to the single MBean registered per pool: a slow query
 * occurring on any connection must reach those listeners, not only the
 * ones occurring on the connection whose interceptor instance happened
 * to win the MBean registration.
 */
class TestSlowQueryJmxNotifications extends JdbcPoolBaseTest {

    @Test
    void notifyPoolFalseDeliversNotificationsOfEveryConnection() throws Exception {
        String poolName = "JmxNotify-" + System.nanoTime();
        DataSource ds = newDataSource();
        ds.setName(poolName);
        ds.setJdbcInterceptors("org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx"
                + "(notifyPool=false,threshold=-1)");
        ds.getPoolProperties().setMaxActive(2);
        ds.getPoolProperties().setInitialSize(0);

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName oname = new ObjectName("tomcat.jdbc:type="
                + SlowQueryReportJmx.class.getName() + ",name=" + poolName);
        List<Notification> received = new CopyOnWriteArrayList<>();
        NotificationListener listener = (notification, handback) -> received.add(notification);

        try (Connection first = ds.getConnection()) {
            Assertions.assertTrue(server.isRegistered(oname),
                    "the interceptor MBean must be registered once the pool is in use");
            server.addNotificationListener(oname, listener, null, null);
            try (Connection second = ds.getConnection()) {
                // two concurrent borrows with no pre-created connections
                // means two physical connections, hence two interceptor
                // instances - only one of which can own the MBean
                Assertions.assertEquals(2, ds.getNumActive());
                try (Statement st1 = first.createStatement();
                        Statement st2 = second.createStatement()) {
                    st1.execute("SELECT 1");
                    st2.execute("SELECT 2");
                }
            }
            server.removeNotificationListener(oname, listener);
        }

        List<String> messages = received.stream().map(Notification::getMessage)
                .collect(Collectors.toList());
        Assertions.assertEquals(2, messages.size(),
                "every slow query of every connection must notify, received: " + messages);
        Assertions.assertTrue(messages.stream().anyMatch(m -> m.contains("SELECT 1")),
                "the query run on the first connection must be reported, received: " + messages);
        Assertions.assertTrue(messages.stream().anyMatch(m -> m.contains("SELECT 2")),
                "the query run on the second physical connection must also reach the listener, received: "
                        + messages);
    }
}

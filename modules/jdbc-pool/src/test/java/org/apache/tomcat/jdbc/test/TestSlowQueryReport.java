/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.jdbc.test;

import java.lang.management.ManagementFactory;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.management.AttributeChangeNotification;
import javax.management.Notification;
import javax.management.NotificationListener;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReport;
import org.apache.tomcat.jdbc.pool.interceptor.SlowQueryReportJmx;

public class TestSlowQueryReport extends DefaultTestCase {
    public static final String superSlowSql = "select count(1) from test where val1 like 'ewq%eq' and val2 = 'ew%rre' and val3 = 'sda%da' and val4 = 'dad%ada'";
    public static final String failedSql = "select 1 from non_existent";
    @Before
    public void setUp() throws SQLException {
        DriverManager.registerDriver(new MockDriver());

        // use our mock driver
        this.datasource.setDriverClassName(MockDriver.class.getName());
        this.datasource.setUrl(MockDriver.url);

        // Required to trigger validation query's execution
        this.datasource.setInitialSize(1);
        this.datasource.setTestOnBorrow(true);
        this.datasource.setValidationInterval(-1);
        this.datasource.setValidationQuery("SELECT 1");
        this.datasource.setMaxActive(1);
        this.datasource.setJdbcInterceptors(SlowQueryReportJmx.class.getName()+"(threshold=50,notifyPool=false)");
    }

    @Test
    public void testSlowSql() throws Exception {
        int count = 3;
        this.datasource.setMaxActive(1);
        this.datasource.setJdbcInterceptors(SlowQueryReport.class.getName()+"(threshold=50)");
        Connection con = this.datasource.getConnection();
        for (int i=0; i<count; i++) {
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(superSlowSql);
            rs.close();
            st.close();
        }
        Map<String,SlowQueryReport.QueryStats> map = SlowQueryReport.getPoolStats(datasource.getPool().getName());
        Assert.assertNotNull(map);
        Assert.assertEquals(1,map.size());
        String key = map.keySet().iterator().next();
        SlowQueryReport.QueryStats stats = map.get(key);
        System.out.println("Stats:"+stats);

        for (int i=0; i<count; i++) {
            PreparedStatement st = con.prepareStatement(superSlowSql);
            ResultSet rs = st.executeQuery();
            rs.close();
            st.close();
        }
        System.out.println("Stats:"+stats);

        for (int i=0; i<count; i++) {
            CallableStatement st = con.prepareCall(superSlowSql);
            ResultSet rs = st.executeQuery();
            rs.close();
            st.close();
        }
        System.out.println("Stats:"+stats);
        ConnectionPool pool = datasource.getPool();
        con.close();
        tearDown();
        //make sure we actually did clean up when the pool closed
        Assert.assertNull(SlowQueryReport.getPoolStats(pool.getName()));
    }

    @Test
    public void testSlowSqlJmx() throws Exception {
        int count = 1;
        Connection con = this.datasource.getConnection();
        for (int i=0; i<count; i++) {
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(superSlowSql);
            rs.close();
            st.close();
        }
        Map<String,SlowQueryReport.QueryStats> map = SlowQueryReport.getPoolStats(datasource.getPool().getName());
        Assert.assertNotNull(map);
        Assert.assertEquals(1,map.size());
        String key = map.keySet().iterator().next();
        SlowQueryReport.QueryStats stats = map.get(key);
        System.out.println("Stats:"+stats);
        ClientListener listener = new ClientListener();
        ConnectionPool pool = datasource.getPool();
        ManagementFactory.getPlatformMBeanServer().addNotificationListener(
                new SlowQueryReportJmx().getObjectName(SlowQueryReportJmx.class, pool.getName()),
                listener,
                null,
                null);

        for (int i=0; i<count; i++) {
            PreparedStatement st = con.prepareStatement(superSlowSql);
            ResultSet rs = st.executeQuery();
            rs.close();
            st.close();
        }
        System.out.println("Stats:"+stats);

        for (int i=0; i<count; i++) {
            CallableStatement st = con.prepareCall(superSlowSql);
            ResultSet rs = st.executeQuery();
            rs.close();
            st.close();
        }
        System.out.println("Stats:"+stats);
        Assert.assertEquals("Expecting to have received "+(2*count)+" notifications.",2*count, listener.notificationCount.get());
        con.close();
        tearDown();
        //make sure we actually did clean up when the pool closed
        Assert.assertNull(SlowQueryReport.getPoolStats(pool.getName()));
    }

    @Test
    public void testFastSql() throws Exception {
        int count = 3;
        Connection con = this.datasource.getConnection();
        String fastSql = this.datasource.getValidationQuery();
        for (int i=0; i<count; i++) {
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(fastSql);
            rs.close();
            st.close();
        }
        Map<String,SlowQueryReport.QueryStats> map = SlowQueryReport.getPoolStats(datasource.getPool().getName());
        Assert.assertNotNull(map);
        Assert.assertEquals(1,map.size());
        ConnectionPool pool = datasource.getPool();
        con.close();
        tearDown();
        Assert.assertNull(SlowQueryReport.getPoolStats(pool.getName()));
    }

    @Test
    public void testFailedSql() throws Exception {
        int count = 3;
        Connection con = this.datasource.getConnection();
        for (int i=0; i<count; i++) {
            Statement st = con.createStatement();
            try {
                ResultSet rs = st.executeQuery(failedSql);
                rs.close();
            }catch (Exception x) {
                // NO-OP
            }
            st.close();
        }
        Map<String,SlowQueryReport.QueryStats> map = SlowQueryReport.getPoolStats(datasource.getPool().getName());
        Assert.assertNotNull(map);
        Assert.assertEquals(1,map.size());
        ConnectionPool pool = datasource.getPool();
        String key = map.keySet().iterator().next();
        SlowQueryReport.QueryStats stats = map.get(key);
        System.out.println("Stats:"+stats);
        con.close();
        tearDown();
        Assert.assertNull(SlowQueryReport.getPoolStats(pool.getName()));
    }


    public class ClientListener implements NotificationListener {
        AtomicInteger notificationCount = new AtomicInteger(0);
        @Override
        public void handleNotification(Notification notification,
                                       Object handback) {
            notificationCount.incrementAndGet();
            System.out.println("\nReceived notification:");
            System.out.println("\tClassName: " + notification.getClass().getName());
            System.out.println("\tSource: " + notification.getSource());
            System.out.println("\tType: " + notification.getType());
            System.out.println("\tMessage: " + notification.getMessage());
            if (notification instanceof AttributeChangeNotification) {
                AttributeChangeNotification acn =
                    (AttributeChangeNotification) notification;
                System.out.println("\tAttributeName: " + acn.getAttributeName());
                System.out.println("\tAttributeType: " + acn.getAttributeType());
                System.out.println("\tNewValue: " + acn.getNewValue());
                System.out.println("\tOldValue: " + acn.getOldValue());
            }
        }
    }

    /**
     * Mock Driver, Connection and Statement implementations use to verify setQueryTimeout was called.
     */
    public static class MockDriver implements java.sql.Driver {
        public static final String url = "jdbc:tomcat:mock";

        public MockDriver() {
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return url!=null && url.equals(MockDriver.url);
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return new MockConnection(info);
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return null;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }
    }

    public static class MockConnection extends org.apache.tomcat.jdbc.test.driver.Connection {
        public MockConnection(Properties info) {
            super(info);
        }

        @Override
        public Statement createStatement() throws SQLException {
            return new MockStatement(false);
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return new MockStatement(sql.equals(superSlowSql));
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            return new MockStatement(sql.equals(superSlowSql));
        }
    }

    public static class MockStatement extends org.apache.tomcat.jdbc.test.driver.Statement {
        boolean slow = false;

        public MockStatement(boolean slow) {
            this.slow = slow;
        }

        @Override
        public boolean execute(String sql) throws SQLException {
            if (failedSql.equals(sql)) {
                throw new SQLException("Invalid SQL:"+sql);
            }
            if (slow || superSlowSql.equals(sql)) {
                try {
                    Thread.sleep(200);
                }catch (Exception x) {
                }
            }
            return super.execute(sql);
        }

        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            if (failedSql.equals(sql)) {
                throw new SQLException("Invalid SQL:"+sql);
            }
            if (slow || superSlowSql.equals(sql)) {
                try {
                    Thread.sleep(200);
                }catch (Exception x) {
                }
            }
            return super.executeQuery(sql);
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            if (slow) {
                try {
                    Thread.sleep(200);
                }catch (Exception x) {
                }
            }
            return super.executeQuery();
        }
    }


}

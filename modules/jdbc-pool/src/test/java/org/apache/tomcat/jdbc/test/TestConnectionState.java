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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.PooledConnection;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.jdbc.pool.DataSourceProxy;
import org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;
import org.apache.tomcat.jdbc.test.driver.Driver;

public class TestConnectionState extends DefaultTestCase {

    @Test
    public void testAutoCommitFalse() throws Exception {
        DataSourceProxy d1 = this.createDefaultDataSource();
        d1.setMaxActive(1);
        d1.setMinIdle(1);
        d1.setMaxIdle(1);
        d1.setJdbcInterceptors(ConnectionState.class.getName());
        d1.setDefaultAutoCommit(Boolean.FALSE);
        Connection c1 = d1.getConnection();
        Assert.assertFalse("Auto commit should be false",c1.getAutoCommit());
        c1.setAutoCommit(true);
        Assert.assertTrue("Auto commit should be true",c1.getAutoCommit());
        c1.close();
        c1 = d1.getConnection();
        Assert.assertFalse("Auto commit should be false for a reused connection",c1.getAutoCommit());
        d1.close(true);
        Assert.assertTrue("Connection should be closed",c1.isClosed());
    }

    @Test
    public void testAutoCommitTrue() throws Exception {
        DataSourceProxy d1 = this.createDefaultDataSource();
        d1.setMaxActive(1);
        d1.setJdbcInterceptors(ConnectionState.class.getName());
        d1.setDefaultAutoCommit(Boolean.TRUE);
        d1.setMinIdle(1);
        Connection c1 = d1.getConnection();
        Assert.assertTrue("Auto commit should be true",c1.getAutoCommit());
        c1.setAutoCommit(false);
        Assert.assertFalse("Auto commit should be false",c1.getAutoCommit());
        c1.close();
        c1 = d1.getConnection();
        Assert.assertTrue("Auto commit should be true for a reused connection",c1.getAutoCommit());
    }

    @Test
    public void testDefaultCatalog() throws Exception {
        DataSourceProxy d1 = this.createDefaultDataSource();
        d1.setMaxActive(1);
        d1.setJdbcInterceptors(ConnectionState.class.getName());
        d1.setDefaultCatalog("information_schema");
        d1.setMinIdle(1);
        Connection c1 = d1.getConnection();
        Assert.assertEquals("Catalog should be information_schema",c1.getCatalog(),"information_schema");
        c1.close();
        c1 = d1.getConnection();
        Assert.assertEquals("Catalog should be information_schema",c1.getCatalog(),"information_schema");
        c1.setCatalog("mysql");
        Assert.assertEquals("Catalog should be information_schema",c1.getCatalog(),"mysql");
        c1.close();
        c1 = d1.getConnection();
        Assert.assertEquals("Catalog should be information_schema",c1.getCatalog(),"information_schema");
    }

    @Test
    public void testWithException() throws SQLException {
        DriverManager.registerDriver(new MockErrorDriver());
        // use our mock driver
        datasource.setDriverClassName(MockErrorDriver.class.getName());
        datasource.setUrl(MockErrorDriver.url);
        datasource.setDefaultAutoCommit(Boolean.TRUE);
        datasource.setMinIdle(1);
        datasource.setMaxIdle(1);
        datasource.setMaxActive(1);
        datasource.setJdbcInterceptors(ConnectionState.class.getName());
        Connection connection = datasource.getConnection();
        PooledConnection pc = (PooledConnection) connection;
        MockErrorConnection c1 = (MockErrorConnection) pc.getConnection();
        Assert.assertTrue("Auto commit should be true", c1.getAutoCommit());
        connection.close();
        c1.setThrowError(true);
        Connection c2 = datasource.getConnection();
        try {
            c2.setAutoCommit(false);
        } catch (SQLException e) {
            // ignore
        }
        Assert.assertFalse("Auto commit should be false", c2.getAutoCommit());
        c2.close();
        DriverManager.deregisterDriver(new MockErrorDriver());
    }
    public static class MockErrorDriver extends Driver {
        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return new MockErrorConnection(info);
        }
    }

    public static class MockErrorConnection extends org.apache.tomcat.jdbc.test.driver.Connection {
        private boolean throwError = false;
        private boolean autoCommit = false;
        public void setThrowError(boolean throwError) {
            this.throwError = throwError;
        }

        public MockErrorConnection(Properties info) {
            super(info);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            return autoCommit;
        }

        @Override
        public void close() throws SQLException {
            super.close();
            throwError = true;
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            this.autoCommit = autoCommit;
            if (throwError) {
                throw new SQLException("Mock error");
            }
        }

    }
}

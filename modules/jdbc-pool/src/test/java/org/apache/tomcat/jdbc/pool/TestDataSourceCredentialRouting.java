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
package org.apache.tomcat.jdbc.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A data-source-backed pool must route a configured username or password to
 * the matching {@code getConnection(String, String)} overload even when the
 * other credential is absent, just as the driver-backed route passes the
 * credentials it has independently.
 */
class TestDataSourceCredentialRouting {

    private static final class RecordingDataSource {
        final AtomicBoolean noArg = new AtomicBoolean();
        final AtomicBoolean withArgs = new AtomicBoolean();
        final javax.sql.DataSource proxy;

        RecordingDataSource() {
            proxy = (javax.sql.DataSource) Proxy.newProxyInstance(
                    TestDataSourceCredentialRouting.class.getClassLoader(),
                    new Class[] { javax.sql.DataSource.class }, new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("getConnection".equals(method.getName())) {
                                if (args == null || args.length == 0) {
                                    noArg.set(true);
                                } else {
                                    withArgs.set(true);
                                }
                                // A recording failure is enough: the test only
                                // needs to observe which overload the pool chose.
                                throw new SQLException("recorded getConnection call");
                            }
                            if ("getLogWriter".equals(method.getName())) {
                                return null;
                            }
                            if ("getLoginTimeout".equals(method.getName())) {
                                return Integer.valueOf(0);
                            }
                            if ("getParentLogger".equals(method.getName())) {
                                throw new java.sql.SQLFeatureNotSupportedException();
                            }
                            return null;
                        }
                    });
        }
    }

    @Test
    void aUsernameWithoutPasswordUsesTheCredentialOverload() throws Exception {
        RecordingDataSource recording = new RecordingDataSource();
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.getPoolProperties().setDataSource(recording.proxy);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setUsername("alice");
        ds.getPoolProperties().setPassword(null);
        ds.createPool();
        try {
            Assertions.assertThrows(SQLException.class, ds::getConnection);
            Assertions.assertTrue(recording.withArgs.get(),
                    "a username without a password must reach getConnection(String,String)");
            Assertions.assertFalse(recording.noArg.get(),
                    "the no-argument overload must not be used when a username is configured");
        } finally {
            ds.close(true);
        }
    }

    @Test
    void aUsernameWithoutPasswordUsesTheXaCredentialOverload() throws Exception {
        AtomicBoolean noArg = new AtomicBoolean();
        AtomicBoolean withArgs = new AtomicBoolean();
        javax.sql.XADataSource dataSource = (javax.sql.XADataSource) Proxy.newProxyInstance(
                TestDataSourceCredentialRouting.class.getClassLoader(), new Class[] { javax.sql.XADataSource.class },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("getXAConnection".equals(method.getName())) {
                            if (args == null || args.length == 0) {
                                noArg.set(true);
                            } else {
                                withArgs.set(true);
                            }
                            throw new SQLException("recorded getXAConnection call");
                        }
                        if ("getLoginTimeout".equals(method.getName())) {
                            return Integer.valueOf(0);
                        }
                        if ("getParentLogger".equals(method.getName())) {
                            throw new java.sql.SQLFeatureNotSupportedException();
                        }
                        return null;
                    }
                });
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.getPoolProperties().setDataSource(dataSource);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setUsername("alice");
        ds.getPoolProperties().setPassword(null);
        ds.createPool();
        try {
            Assertions.assertThrows(SQLException.class, ds::getConnection);
            Assertions.assertTrue(withArgs.get(),
                    "a username without a password must reach getXAConnection(String,String)");
            Assertions.assertFalse(noArg.get(),
                    "the no-argument XA overload must not be used when a username is configured");
        } finally {
            ds.close(true);
        }
    }

    @Test
    void aUsernameWithoutPasswordUsesTheCpdsCredentialOverload() throws Exception {
        AtomicBoolean noArg = new AtomicBoolean();
        AtomicBoolean withArgs = new AtomicBoolean();
        javax.sql.ConnectionPoolDataSource dataSource = (javax.sql.ConnectionPoolDataSource) Proxy.newProxyInstance(
                TestDataSourceCredentialRouting.class.getClassLoader(),
                new Class[] { javax.sql.ConnectionPoolDataSource.class }, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("getPooledConnection".equals(method.getName())) {
                            if (args == null || args.length == 0) {
                                noArg.set(true);
                            } else {
                                withArgs.set(true);
                            }
                            throw new SQLException("recorded getPooledConnection call");
                        }
                        if ("getLoginTimeout".equals(method.getName())) {
                            return Integer.valueOf(0);
                        }
                        if ("getParentLogger".equals(method.getName())) {
                            throw new java.sql.SQLFeatureNotSupportedException();
                        }
                        return null;
                    }
                });
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.getPoolProperties().setDataSource(dataSource);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setUsername("alice");
        ds.getPoolProperties().setPassword(null);
        ds.createPool();
        try {
            Assertions.assertThrows(SQLException.class, ds::getConnection);
            Assertions.assertTrue(withArgs.get(),
                    "a username without a password must reach getPooledConnection(String,String)");
            Assertions.assertFalse(noArg.get(),
                    "the no-argument CPDS overload must not be used when a username is configured");
        } finally {
            ds.close(true);
        }
    }

    @Test
    void aPasswordWithoutUsernameUsesTheCredentialOverload() throws Exception {
        RecordingDataSource recording = new RecordingDataSource();
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.getPoolProperties().setDataSource(recording.proxy);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setUsername(null);
        ds.getPoolProperties().setPassword("secret");
        ds.createPool();
        try {
            Assertions.assertThrows(SQLException.class, ds::getConnection);
            Assertions.assertTrue(recording.withArgs.get(),
                    "a password without a username must reach getConnection(String,String)");
            Assertions.assertFalse(recording.noArg.get(),
                    "the no-argument overload must not be used when a password is configured");
        } finally {
            ds.close(true);
        }
    }

    @Test
    void neitherCredentialUsesTheNoArgumentOverload() throws Exception {
        RecordingDataSource recording = new RecordingDataSource();
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.getPoolProperties().setDataSource(recording.proxy);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.getPoolProperties().setJmxEnabled(false);
        ds.getPoolProperties().setUsername(null);
        ds.getPoolProperties().setPassword(null);
        ds.createPool();
        try {
            Assertions.assertThrows(SQLException.class, ds::getConnection);
            Assertions.assertTrue(recording.noArg.get(),
                    "with no credentials the no-argument overload must be used");
            Assertions.assertFalse(recording.withArgs.get(),
                    "the credential overload must not be used with no credentials");
        } finally {
            ds.close(true);
        }
    }
}

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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A provider that breaks the contract by returning no XA/CPDS handle must get
 * the pool's explicit contract-violation failure instead of a NullPointerException
 * whose message depends on the JVM's helpful-NullPointerException setting.
 */
class TestDataSourceNullHandle {

    private static InvocationHandler nullHandleHandler(String handleMethod) {
        return new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (handleMethod.equals(method.getName())) {
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
        };
    }

    @Test
    void aNullXaHandleIsReportedExplicitly() throws Exception {
        javax.sql.XADataSource fake = (javax.sql.XADataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[] { javax.sql.XADataSource.class }, nullHandleHandler("getXAConnection"));
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.getPoolProperties().setDataSource(fake);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.getPoolProperties().setJmxEnabled(false);
        ds.createPool();
        try {
            SQLException failure = Assertions.assertThrows(SQLException.class, ds::getConnection);
            Assertions.assertTrue(failure.getMessage().contains("returned no XA connection handle"),
                    "the failure must name the contract violation, was: " + failure.getMessage());
        } finally {
            ds.close(true);
        }
    }

    @Test
    void aNullCpdsHandleIsReportedExplicitly() throws Exception {
        javax.sql.ConnectionPoolDataSource fake = (javax.sql.ConnectionPoolDataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[] { javax.sql.ConnectionPoolDataSource.class },
                nullHandleHandler("getPooledConnection"));
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.getPoolProperties().setDataSource(fake);
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(-1);
        ds.getPoolProperties().setJmxEnabled(false);
        ds.createPool();
        try {
            SQLException failure = Assertions.assertThrows(SQLException.class, ds::getConnection);
            Assertions.assertTrue(failure.getMessage().contains("returned no pooled connection handle"),
                    "the failure must name the contract violation, was: " + failure.getMessage());
        } finally {
            ds.close(true);
        }
    }
}

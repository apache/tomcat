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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Setting the configured query timeout is an optional protection: a driver that refuses it still hands the statement
 * out. The module's fatal-error policy applies to it too, so a non-fatal {@link LinkageError} from the call degrades to
 * an un-timed but usable statement, while a {@link java.lang.VirtualMachineError} propagates.
 */
class TestQueryTimeoutErrorPolicy extends JdbcPoolBaseTest {

    /**
     * An H2 driver whose statements throw the error the current test selected from {@code setQueryTimeout()}.
     * Configured through {@code driverClassName}, so it must stay a public static class with a no-arg constructor.
     */
    public static class ErroringTimeoutDriver extends org.h2.Driver {
        static volatile Error failure;

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection real = super.connect(url, info);
            if (real == null) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[] { Connection.class }, new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            Object result;
                            try {
                                result = method.invoke(real, args);
                            } catch (InvocationTargetException x) {
                                throw x.getTargetException();
                            }
                            if ("createStatement".equals(method.getName()) && result instanceof Statement) {
                                Statement statement = (Statement) result;
                                return Proxy.newProxyInstance(getClass().getClassLoader(),
                                        new Class[] { Statement.class }, new InvocationHandler() {
                                            @Override
                                            public Object invoke(Object p, Method m, Object[] a) throws Throwable {
                                                if ("setQueryTimeout".equals(m.getName()) && failure != null) {
                                                    throw failure;
                                                }
                                                try {
                                                    return m.invoke(statement, a);
                                                } catch (InvocationTargetException x) {
                                                    throw x.getTargetException();
                                                }
                                            }
                                        });
                            }
                            return result;
                        }
                    });
        }
    }

    @Test
    void aNonFatalErrorFromSettingTheTimeoutIsIsolated() throws Exception {
        ErroringTimeoutDriver.failure = new LinkageError("wrapper class loader failed");
        DataSource ds = timeoutDataSource();
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            st.execute("SELECT 1");
        } finally {
            ErroringTimeoutDriver.failure = null;
            ds.close();
        }
    }

    @Test
    void aVirtualMachineErrorFromSettingTheTimeoutPropagates() throws Exception {
        ErroringTimeoutDriver.failure = new InternalError("fatal timeout setup");
        DataSource ds = timeoutDataSource();
        try (Connection con = ds.getConnection()) {
            Assertions.assertThrows(InternalError.class, con::createStatement,
                    "a virtual-machine error from the optional timeout call must propagate");
        } finally {
            ErroringTimeoutDriver.failure = null;
            ds.close();
        }
    }

    private DataSource timeoutDataSource() throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setDriverClassName(ErroringTimeoutDriver.class.getName());
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setJdbcInterceptors(
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
        ds.createPool();
        return ds;
    }
}

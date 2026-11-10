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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * The module's fatal-error policy, stated by {@link org.apache.tomcat.jdbc.pool.util.ExceptionUtils}, re-throws a
 * {@link VirtualMachineError} and swallows the other throwables. The teardown walks used to catch {@code Throwable}
 * without applying that policy, so an {@link InternalError} from an interceptor reset or disconnect hook was logged and
 * the teardown continued in a JVM the error says is unstable.
 */
class TestFatalTeardownError extends JdbcPoolBaseTest {

    /**
     * Throws a fatal error from the chain-release reset. Configured through {@code jdbcInterceptors}, so it must stay a
     * public static class with a no-arg constructor.
     */
    public static class FatalResetInterceptor extends JdbcInterceptor {
        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            if (parent == null) {
                throw new InternalError("fatal reset");
            }
        }
    }

    /**
     * Throws a fatal error from the disconnect event. Configured through {@code jdbcInterceptors}, so it must stay a
     * public static class with a no-arg constructor.
     */
    public static class FatalDisconnectedInterceptor extends JdbcInterceptor {
        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // NO-OP
        }

        @Override
        public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
            throw new InternalError("fatal disconnect");
        }
    }

    @Test
    void aFatalErrorFromAnInterceptorResetPropagatesFromTheClose() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setJdbcInterceptors(FatalResetInterceptor.class.getName());
        ds.createPool();
        useOneConnection(ds);

        Assertions.assertThrows(InternalError.class, ds::close,
                "a virtual-machine error from the chain teardown must not be logged and swallowed");
    }

    @Test
    void aFatalErrorFromADisconnectHookPropagatesFromTheClose() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        ds.getPoolProperties().setMinIdle(0);
        ds.getPoolProperties().setJdbcInterceptors(FatalDisconnectedInterceptor.class.getName());
        ds.createPool();
        useOneConnection(ds);

        Assertions.assertThrows(InternalError.class, ds::close,
                "a virtual-machine error from the disconnect event must not be logged and swallowed");
    }

    private static void useOneConnection(DataSource ds) throws Exception {
        try (Connection con = ds.getConnection()) {
            con.createStatement().close();
        }
    }
}

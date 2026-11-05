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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * A driver statement close that throws an unchecked failure, or a non-fatal
 * Error, while the disconnect purge discards cached statements must not abort
 * the rest of the purge. Every entry the purge owns is removed and counted
 * before the close runs, but the statements still waiting behind the failing
 * one must also be closed.
 */
class TestStatementCachePurgeCloseFailure extends JdbcPoolBaseTest {

    /** Arms the next prepared statement to fail its close with an Error. */
    private static final AtomicBoolean FAIL_NEXT_CLOSE = new AtomicBoolean();

    /** A cache that can arm one prepared statement to fail its close. */
    public static class ErrorCloseStatementCache extends StatementCache {

        @Override
        protected Object createDecorator(Object proxy, Method method, Object[] args,
                Object statement, Constructor<?> constructor, String sql)
                throws InstantiationException, IllegalAccessException, InvocationTargetException {
            if (compare(PREPARE_STATEMENT, method.getName())
                    && FAIL_NEXT_CLOSE.compareAndSet(true, false)) {
                statement = errorClose((PreparedStatement) statement);
            }
            return super.createDecorator(proxy, method, args, statement, constructor, sql);
        }
    }

    private static PreparedStatement errorClose(PreparedStatement delegate) {
        return (PreparedStatement) Proxy.newProxyInstance(
                TestStatementCachePurgeCloseFailure.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        throw new NoClassDefFoundError("simulated stopped context in close()");
                    }
                    try {
                        return args == null ? method.invoke(delegate) : method.invoke(delegate, args);
                    } catch (InvocationTargetException x) {
                        throw x.getCause();
                    }
                });
    }

    @Test
    void anErrorFromOneCloseDoesNotAbortThePurge() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                ErrorCloseStatementCache.class.getName() + "(prepared=true,max=5)");
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(60000);
        ds.createPool();

        Connection shared = ds.getConnection();
        try {
            ErrorCloseStatementCache cache =
                    interceptorOf(shared, ErrorCloseStatementCache.class);
            PooledConnection pcon = (PooledConnection) shared.unwrap(PooledConnection.class);

            FAIL_NEXT_CLOSE.set(true);
            PreparedStatement first = shared.prepareStatement("SELECT 1");
            Statement firstDriver = driverStatement(first);
            PreparedStatement second = shared.prepareStatement("SELECT 2");
            Statement secondDriver = driverStatement(second);
            first.close();
            second.close();
            Assertions.assertEquals(2, cache.getCacheSize(),
                    "precondition: both statements must be parked");

            Assertions.assertDoesNotThrow(() -> cache.disconnected(ds.getPool(), pcon, false),
                    "the purge must report the failing close, not abort the borrow or close path");
            Assertions.assertEquals(0, cache.getCacheSize(),
                    "every purged entry must give its counter slot back");
            Assertions.assertEquals(0, cache.getCacheSizePerConnection(),
                    "the cache of the connection must be empty");
            Assertions.assertTrue(secondDriver.isClosed(),
                    "a statement after the failing one must still be closed");
            Assertions.assertFalse(firstDriver.isClosed(),
                    "the statement whose close failed with an Error could not be closed");
        } finally {
            shared.close();
        }
    }

    @Test
    void theDiscardedStatementLogSanitizesNewlines() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                ErrorCloseStatementCache.class.getName() + "(prepared=true,max=5)");
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(60000);
        ds.createPool();

        Connection shared = ds.getConnection();
        try {
            ErrorCloseStatementCache cache =
                    interceptorOf(shared, ErrorCloseStatementCache.class);
            PooledConnection pcon = (PooledConnection) shared.unwrap(PooledConnection.class);
            FAIL_NEXT_CLOSE.set(true);
            PreparedStatement statement = shared.prepareStatement(
                    "SELECT 1\nFROM SYSTEM_RANGE(1,1)\r");
            statement.close();

            Logger logger = Logger.getLogger(StatementCache.class.getName());
            Level originalLevel = logger.getLevel();
            List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
            Handler handler = new Handler() {
                @Override
                public void publish(LogRecord record) {
                    records.add(record);
                }

                @Override
                public void flush() {
                    // no buffering
                }

                @Override
                public void close() {
                    // nothing to release
                }
            };
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            try {
                cache.disconnected(ds.getPool(), pcon, false);
            } finally {
                logger.removeHandler(handler);
                logger.setLevel(originalLevel);
            }

            LogRecord reported = null;
            for (LogRecord record : records) {
                if (record.getMessage() != null
                        && record.getMessage().contains("that the statement cache is discarding")) {
                    reported = record;
                }
            }
            Assertions.assertNotNull(reported, "the failing close must be reported");
            Assertions.assertFalse(reported.getMessage().contains("\n")
                            || reported.getMessage().contains("\r"),
                    "the SQL in the log must not carry newlines: " + reported.getMessage());
            Assertions.assertTrue(reported.getMessage().contains("SELECT 1 FROM SYSTEM_RANGE(1,1)"),
                    "the SQL must stay readable in one line: " + reported.getMessage());
        } finally {
            shared.close();
        }
    }
}

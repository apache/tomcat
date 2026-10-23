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
package org.apache.tomcat.jdbc.test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.test.driver.Driver;

/**
 * The reset order of the interceptors must follow the order in which they are declared, whether the connection is new
 * or is taken from the pool.
 */
public class InterceptorResetOrderTest extends DefaultTestCase {

    private static final List<String> RESETS = Collections.synchronizedList(new ArrayList<>());

    @Before
    public void setUp() {
        RESETS.clear();
        datasource.setDriverClassName(Driver.class.getName());
        datasource.setUrl("jdbc:tomcat:test");
        datasource.setMaxActive(1);
        datasource.setInitialSize(0);
        datasource.setMinIdle(0);
        datasource.setJdbcInterceptors(First.class.getName() + ";" + Second.class.getName());
    }

    @Test
    public void testResetOrderOnNewConnection() throws SQLException {
        datasource.getConnection().close();
        Assert.assertEquals(Arrays.asList("first", "second"), RESETS);
    }

    @Test
    public void testResetOrderOnReusedConnection() throws SQLException {
        datasource.getConnection().close();
        RESETS.clear();
        datasource.getConnection().close();
        Assert.assertEquals(Arrays.asList("first", "second"), RESETS);
    }

    @Test
    public void testResetFailureIsReportedAsSQLException() {
        datasource.setJdbcInterceptors(Failing.class.getName());

        SQLException e = Assert.assertThrows(SQLException.class, () -> datasource.getConnection());

        Assert.assertTrue(e.getCause() instanceof IllegalStateException);
    }

    public static class Failing extends JdbcInterceptor {

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            if (parent != null) {
                throw new IllegalStateException();
            }
        }
    }

    public static class First extends JdbcInterceptor {

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // the pool also resets interceptors without a parent when a connection is discarded
            if (parent != null) {
                RESETS.add("first");
            }
        }
    }

    public static class Second extends JdbcInterceptor {

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            if (parent != null) {
                RESETS.add("second");
            }
        }
    }
}

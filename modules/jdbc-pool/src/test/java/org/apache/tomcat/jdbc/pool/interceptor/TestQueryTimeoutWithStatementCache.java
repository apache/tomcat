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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * The query timeout is applied by the {@code createStatement} hook of
 * {@link QueryTimeoutInterceptor}, which only runs for creation calls that
 * pass through that interceptor. A {@link StatementCache} hit is served
 * without invoking the rest of the chain, and a statement recycled into
 * the cache carries query timeout 0. The ordering in
 * {@code jdbcInterceptors} therefore decides whether reused statements
 * keep the configured timeout - a requirement now documented on the
 * interceptor and in the jdbc-pool docs, and pinned here in both orders.
 */
class TestQueryTimeoutWithStatementCache extends JdbcPoolBaseTest {

    private static final String SQL = "SELECT 42";

    private DataSource poolWith(String... interceptors) throws SQLException {
        StringBuilder chain = new StringBuilder();
        for (String interceptor : interceptors) {
            if (chain.length() > 0) {
                chain.append(';');
            }
            chain.append(interceptor);
        }
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(chain.toString());
        ds.createPool();
        return ds;
    }

    @Test
    void reusedStatementKeepsTheTimeoutWhenTheInterceptorIsListedFirst()
            throws Exception {
        DataSource ds = poolWith(
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)",
                StatementCache.class.getName() + "(prepared=true,max=5)");
        try (Connection pooled = ds.getConnection()) {
            PreparedStatement first = pooled.prepareStatement(SQL);
            Assertions.assertEquals(5, first.getQueryTimeout());
            Statement physical = driverStatement(first);
            first.close();
            Assertions.assertEquals(0, physical.getQueryTimeout(),
                    "the cache must have recycled the statement with the timeout reset");

            // the cache serves the second creation, and because this
            // interceptor sits outside the cache its hook still runs - the
            // timeout is re-applied to the recycled statement
            PreparedStatement reused = pooled.prepareStatement(SQL);
            Assertions.assertSame(physical, driverStatement(reused),
                    "the same driver statement must come back from the cache");
            Assertions.assertEquals(5, reused.getQueryTimeout(),
                    "a cache hit served through an outer QueryTimeoutInterceptor "
                            + "must carry the configured timeout");
            reused.close();
        }
    }

    @Test
    void reusedStatementLosesTheTimeoutWhenTheCacheIsListedFirst()
            throws Exception {
        DataSource ds = poolWith(
                StatementCache.class.getName() + "(prepared=true,max=5)",
                QueryTimeoutInterceptor.class.getName() + "(queryTimeout=5)");
        try (Connection pooled = ds.getConnection()) {
            PreparedStatement first = pooled.prepareStatement(SQL);
            Assertions.assertEquals(5, first.getQueryTimeout(),
                    "the creating call reaches the interceptor even in this order");
            Statement physical = driverStatement(first);
            first.close();
            Assertions.assertEquals(0, physical.getQueryTimeout(),
                    "the cache must have recycled the statement with the timeout reset");

            // documented limitation: the cache answers the second creation
            // itself, the deeper interceptor never runs, and the recycled
            // statement carries the 0 the cache reset it to - pinning this
            // so a future change to either interceptor is noticed here
            PreparedStatement reused = pooled.prepareStatement(SQL);
            Assertions.assertSame(physical, driverStatement(reused),
                    "the same driver statement must come back from the cache");
            Assertions.assertEquals(0, reused.getQueryTimeout(),
                    "a cache hit served around an inner QueryTimeoutInterceptor "
                            + "carries no timeout - which is why the interceptor "
                            + "must be listed before the cache; if this ever reads "
                            + "the configured value instead, the order dependency "
                            + "has been removed and the notes on "
                            + "QueryTimeoutInterceptor and StatementCache must be "
                            + "updated along with this test");
            reused.close();
        }
    }
}

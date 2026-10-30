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
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.ProxyConnection;

/**
 * The statement facade seals its handles when a borrow is returned, but a
 * borrow that is abandoned instead never seals them - the pool destroys an
 * abandoned session rather than lending it again, so the javadoc of
 * {@link org.apache.tomcat.jdbc.pool.StatementFacade#closeInvoked()}
 * documents that those handles "only fail at the driver". The application
 * handle reaching that driver is the contract this class pins: when the
 * disposal resets the interceptor chain with {@code reset(null, null)}, the
 * statement cache drops its binding to the pool-wide entry counter, and a
 * still-open statement closed from that window must skip the recycling
 * attempt and close through the normal path, delivering the driver's
 * outcome - with H2 that outcome is a quiet success, because the driver
 * closes an open statement of a closed connection without complaint, and an
 * even later close is the contract's repeated no-op - rather than the
 * pool's own {@code NullPointerException} from the unguarded counter read
 * that pre-fix {@code CachedStatement.closeInvoked()} performed.
 */
class TestStatementCacheCloseAfterDisposal extends JdbcPoolBaseTest {

    @Test
    void aStatementClosedAfterItsSessionWasAbandonedClosesThroughTheDriver() throws Exception {
        DataSource ds = newDataSource();
        PoolConfiguration properties = ds.getPoolProperties();
        properties.setJdbcInterceptors(
                StatementCache.class.getName() + "(prepared=true,max=5)");
        properties.setRemoveAbandoned(true);
        properties.setRemoveAbandonedTimeout(1); // seconds
        properties.setInitialSize(0);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }

        Connection abandoned = ds.getConnection();
        PreparedStatement statement = abandoned.prepareStatement(
                "INSERT INTO " + table + " VALUES (?)");
        Statement physical = driverStatement(statement);
        statement.setInt(1, 1);
        Assertions.assertEquals(1, statement.executeUpdate());

        PooledConnection pooled =
                interceptorOf(abandoned, ProxyConnection.class).getDelegateConnection();
        StatementCache cache = interceptorOf(abandoned, StatementCache.class);

        // Backdate the borrow past the abandon timeout and run the check the
        // pool's cleaner thread runs: the abandon, the disconnect purge (which
        // still sees the counter bound) and the final reset of the chain all
        // complete synchronously on this thread.
        pooled.setTimestamp(0L);
        ds.getPool().checkAbandoned();

        Assertions.assertTrue(pooled.isReleased(),
                "precondition: the pool must have released the abandoned session");
        awaitSessions(0);
        Assertions.assertEquals(-1, cache.getCacheSize(),
                "precondition: the disposal reset must have unbound the interceptor"
                        + " from the pool-wide counter");

        // The documented window: an application handle that outlives the
        // abandoned session. Pre-fix the close dies on the nulled counter
        // field with a NullPointerException before ever reaching the driver.
        statement.close();

        Assertions.assertTrue(physical.isClosed(),
                "the close must have reached the driver and closed its statement");
        Assertions.assertTrue(statement.isClosed(),
                "the pooled handle must report itself closed afterwards");

        // A repeated close stays the quiet no-op the JDBC contract requires.
        statement.close();
    }
}

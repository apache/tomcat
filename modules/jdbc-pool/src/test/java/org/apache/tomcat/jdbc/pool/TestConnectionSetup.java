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
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.interceptor.ConnectionState;

/**
 * Expected functionality of the per-connection setup the pool performs
 * while a physical session is being established: the configured init SQL
 * is applied to the session, unusable configuration is normalised or
 * tolerated instead of wedging the pool, and the session the application
 * receives is in a defined state.
 *
 * <p>Scenarios harvested from the retired legacy classes
 * {@code Bug54225} (blank init SQL), {@code Bug54978} (init SQL used for
 * connect validation) and {@code Bug50571} (unusable configured
 * transaction isolation with the {@code ConnectionState} interceptor) -
 * none of which ran in the old build.</p>
 */
class TestConnectionSetup extends JdbcPoolBaseTest {

    private static final String INIT_MARKER = "SET @POOL_INIT_STATE = 'applied'";

    private static String initMarker(Connection c) throws Exception {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT @POOL_INIT_STATE")) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void initSqlIsAppliedToEveryPhysicalConnection() throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitSQL(INIT_MARKER);
        ds.getPoolProperties().setMaxActive(2);
        ds.createPool();

        try (Connection initial = ds.getConnection()) {
            assertEquals("applied", initMarker(initial),
                    "the init SQL must have run on the initial connection");
        }
        awaitSessions(1);

        // a second physical connection gets the init SQL as well
        Connection secondPhysical;
        try (@SuppressWarnings("unused") Connection first = ds.getConnection()) {
            secondPhysical = ds.getConnection();
            assertEquals(2, poolSessions(), "two concurrent borrowers need two sessions");
        }
        assertEquals("applied", initMarker(secondPhysical),
                "the init SQL must run on every session the pool establishes");
        secondPhysical.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void aBlankInitSqlIsNotConfiguredAtAll(String initSql) throws Exception {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitSQL(initSql);
        assertNull(ds.getPoolProperties().getInitSQL(),
                "a blank init SQL must be normalised to 'not configured'");
        ds.createPool();

        try (Connection c = ds.getConnection()) {
            assertNull(initMarker(c), "nothing may have been executed on the session");
            c.createStatement().execute("SELECT 1");
        }
    }

    @Test
    void theInitSqlValidatesANewlyEstablishedConnection() throws Exception {
        // Documented in PoolConfiguration#setValidationQuery: "Setting an
        // initSQL will override this setting, as the init SQL will be used
        // instead of the validation query" - so for a freshly established
        // session the init SQL is the validation, and the (broken)
        // validationQuery below is never executed.
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitSQL(INIT_MARKER);
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidationQuery("SELECT * FROM NO_SUCH_VALIDATION_TABLE");
        ds.getPoolProperties().setValidationInterval(0);
        ds.getPoolProperties().setMaxActive(1);
        ds.createPool();

        try (Connection c = ds.getConnection()) {
            assertEquals("applied", initMarker(c),
                    "the configured init SQL must have validated the session");
        }
        assertEquals(1, ds.getIdle());
    }

    @Test
    void anUnusableTransactionIsolationSettingDoesNotBlockThePool() throws Exception {
        DataSource ds = newDataSource();
        // not a JDBC isolation level at all; the pool must not strand borrowers over it
        ds.getPoolProperties().setDefaultTransactionIsolation(-55);
        ds.getPoolProperties().setJdbcInterceptors(ConnectionState.class.getName());
        ds.createPool();

        for (int i = 0; i < 2; i++) {
            try (Connection c = ds.getConnection()) {
                assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                        c.getTransactionIsolation(),
                        "the session keeps the driver's isolation level");
                assertTrue(c.createStatement().execute("SELECT 1"),
                        "the connection must be usable despite the unusable setting");
            }
        }
        assertEquals(1, ds.getIdle(), "the pool must still pool the connection");
    }
}

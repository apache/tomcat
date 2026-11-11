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

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PooledConnection;

/**
 * How long the statement and result set handles the
 * {@code ResetAbandonedTimer} interceptor hands out beneath the facade
 * keep the objects they wrap.
 *
 * <p>Once a close has been forwarded, the interceptor's own handle must let
 * go of the layer beneath it - the query-report proxy over the statement, and
 * the driver's cursor and statement layer for a result set - so that a handle
 * an application keeps beyond its close stops pinning the physical objects,
 * the same rule the statement facade's own handles obey (see
 * {@code TestStatementHandleLifetime} for the facade's lifetime, and
 * {@code TestStatementFacadeCrossThreadClose} for the close race the volatile
 * snapshot here answers). What a released handle still owes the caller is the
 * closed contract - an honest {@code isClosed()}, an idempotent
 * {@code close()} and a refusal on everything else - and a refused read still
 * counts as activity on the connection, the contract the failing-operation
 * keep-alive established.</p>
 */
class TestResetAbandonedTimerHandleRetention extends JdbcPoolBaseTest {

    private DataSource timerPool() throws SQLException {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setJdbcInterceptors(
                "org.apache.tomcat.jdbc.pool.interceptor.ResetAbandonedTimer");
        ds.createPool();
        return ds;
    }

    /*
     * A named field of any pool object the tests need to see into - the
     * interceptor's own fields (its pooled connection) and the fields of an
     * invocation handler, which the caller passes through
     * {@link Proxy#getInvocationHandler}. The base class' proxiedObject only
     * reads the delegate field of a handler; this reaches the rest.
     */
    private static Object fieldOf(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object handlerField(Object handle, String name)
            throws ReflectiveOperationException {
        return fieldOf(Proxy.getInvocationHandler(handle), name);
    }

    @Test
    void aClosedStatementHandleReleasesTheLayerBeneathIt() throws Exception {
        DataSource ds = timerPool();
        try (Connection con = ds.getConnection()) {
            Statement st = con.createStatement();
            Object timerHandle = proxiedObject(st);
            Assertions.assertTrue(Proxy.getInvocationHandler(timerHandle).getClass().getName()
                            .contains("TimerStatementProxy"),
                    "precondition: beneath the facade sits this interceptor's handle, found "
                            + Proxy.getInvocationHandler(timerHandle).getClass().getName());
            Assertions.assertNotNull(proxiedObject(timerHandle),
                    "the open handle must hold the query-report layer beneath it");

            st.close();

            Assertions.assertNull(proxiedObject(timerHandle),
                    "a closed handle must release the layer beneath it");

            // The closed contract on this interceptor's own handle: honest
            // isClosed, idempotent close, and a refusal that says so - the
            // same answers the handle gave once its own delegate beneath had
            // closed, short-circuited here now that this delegate is gone.
            Statement timerStatement = (Statement) timerHandle;
            Assertions.assertTrue(timerStatement.isClosed(),
                    "the released handle must report itself closed");
            timerStatement.close();
            timerStatement.close();
            SQLException thrown = Assertions.assertThrows(SQLException.class,
                    () -> timerStatement.executeQuery("SELECT 1"),
                    "a released handle must refuse to execute");
            Assertions.assertEquals("Statement closed.", thrown.getMessage(),
                    "the refusal must say what happened");

            // The application's own handle behaves as before, unchanged by
            // what the layer beneath it now does with its reference.
            Assertions.assertTrue(st.isClosed(),
                    "the application's handle reports itself closed");
            st.close();
            Assertions.assertThrows(SQLException.class, () -> st.executeQuery("SELECT 1"),
                    "the application's handle still refuses after close");
        }
    }

    @Test
    void aClosedResultSetHandleReleasesTheCursorAndTheStatement() throws Exception {
        DataSource ds = timerPool();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT X FROM SYSTEM_RANGE(1,3)");
            Object timerHandle = proxiedObject(rs);
            Assertions.assertTrue(Proxy.getInvocationHandler(timerHandle).getClass().getName()
                            .contains("TimerResultSetProxy"),
                    "precondition: beneath the facade sits this interceptor's handle, found "
                            + Proxy.getInvocationHandler(timerHandle).getClass().getName());
            Assertions.assertNotNull(proxiedObject(timerHandle),
                    "the open handle must hold the driver cursor");
            Assertions.assertNotNull(handlerField(timerHandle, "statement"),
                    "the open handle must hold the statement layer");

            rs.close();

            Assertions.assertNull(proxiedObject(timerHandle),
                    "a closed result set handle must release the driver cursor");
            Assertions.assertNull(handlerField(timerHandle, "statement"),
                    "a closed result set handle must release the statement layer");

            // The closed contract on the application's own handle, unchanged.
            Assertions.assertTrue(rs.isClosed(),
                    "the application's result set handle reports itself closed");
            rs.close();
            Assertions.assertThrows(SQLException.class, rs::next,
                    "the application's result set handle still refuses reads after close");
        }
    }

    /*
     * The attempted-operation-is-activity contract, on the one call a
     * released handle now refuses itself: a read that gets refused for
     * having no delegate left is still an operation attempted on the
     * connection, and must reset the abandoned timer just like every read
     * that reached the driver - failing included (see
     * TestResetAbandonedTimerStreaming.failedResultSetReadsKeepConnectionAlive).
     * The refusal is invoked on the interceptor's own handle, captured
     * before the close: through the facade the application's handle is
     * refused one layer up, which is its own sealed-contract behavior.
     */
    @Test
    void aReadRefusedByAReleasedHandleIsStillActivity() throws Exception {
        DataSource ds = timerPool();
        try (Connection con = ds.getConnection()) {
            ResetAbandonedTimer timer = interceptorOf(con, ResetAbandonedTimer.class);
            Assertions.assertNotNull(timer, "precondition: the interceptor is in the chain");
            try (Statement st = con.createStatement()) {
                ResultSet rs = st.executeQuery("SELECT X FROM SYSTEM_RANGE(1,3)");
                ResultSet timerHandle = (ResultSet) proxiedObject(rs);
                rs.close(); // through the facade, releasing this delegate too

                PooledConnection pcon = (PooledConnection) fieldOf(timer, "pcon");
                pcon.setTimestamp(1L); // as if this borrow had all but expired

                SQLException thrown = Assertions.assertThrows(SQLException.class,
                        timerHandle::next,
                        "the released handle must refuse the read itself");
                Assertions.assertEquals("ResultSet closed.", thrown.getMessage(),
                        "the refusal must be the closed contract, not a driver error");
                Assertions.assertTrue(pcon.getTimestamp() > 1L,
                        "the refused read must still have reset the abandoned timer");
            }
        }
    }
}

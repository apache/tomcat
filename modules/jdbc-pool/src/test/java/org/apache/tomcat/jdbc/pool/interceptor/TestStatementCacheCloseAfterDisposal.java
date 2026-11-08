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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *
 * <p>The second test pins the narrower window inside a park that is still
 * running: the disposal lands after the close has reserved its slot on the
 * pool-wide entry counter but before the withdrawal of a refused insert. The
 * counter object outlives the field's nulling (the pool owns it), so the
 * withdrawal must run through the counter read at the start of the call -
 * pre-fix it re-read the nulled field, threw, and left the reserved slot
 * counted forever, shrinking the {@code max} budget of every connection in
 * the pool by one per such race.</p>
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

    /**
     * The disposal meeting a park mid-flight: the hook takes the key so the
     * insert is refused, and disposes the interceptor while the park still
     * holds its reservation. The close must finish as a plain physical close,
     * and the refused statement's reservation must come back off the pool-wide
     * counter - so a later, live session still finds the full {@code max=2}
     * budget and parks both of its statements. Pre-fix the withdrawal hit the
     * nulled field, the reservation leaked into the counter, and the second
     * statement of the later session was refused with a full-looking cache.
     */
    @Test
    void aDisposalLandingInsideAnUnsuccessfulParkDoesNotEatAPoolWideSlot() throws Exception {
        DataSource ds = newDataSource();
        PoolConfiguration properties = ds.getPoolProperties();
        properties.setJdbcInterceptors(
                StatementCache.class.getName() + "(prepared=true,max=2)");
        properties.setMaxActive(2);
        properties.setInitialSize(0);
        // keep the background sweeper out of the way during the race
        properties.setTimeBetweenEvictionRunsMillis(60000);
        String table = uniqueTable();
        ds.createPool();
        try (Connection admin = adminConnection(); Statement st = admin.createStatement()) {
            st.execute("CREATE TABLE " + table + " (v INT)");
        }

        Connection first = ds.getConnection();
        StatementCache cache = interceptorOf(first, StatementCache.class);
        PooledConnection pooled = internalOf(first);
        DisposalAtParkMap parkPoint = new DisposalAtParkMap();
        parkPoint.disposal = () -> cache.reset(null, null);
        pooled.getAttributes().put(StatementCache.STATEMENT_CACHE_ATTR, parkPoint);

        setSessionMarker(first, 61);
        String sqlOne = "INSERT INTO " + table + " VALUES (?)";
        PreparedStatement racing = first.prepareStatement(sqlOne);
        Statement racingPhysical = driverStatement(racing);
        racing.setInt(1, 1);
        Assertions.assertEquals(1, racing.executeUpdate());
        racing.close(); // the hook fires inside this close's park
        Assertions.assertTrue(parkPoint.raced,
                "precondition: the disposal must have landed inside the park");
        Assertions.assertTrue(racingPhysical.isClosed(),
                "the refused park must have closed the statement through the normal path");
        // The raced session's chain is disposed; take it out of service so the
        // next borrow must come from a fresh chain (on the shared counter).
        pooled.setDiscarded(true);
        first.close();
        awaitSessions(0);

        Connection second = ds.getConnection();
        try {
            Assertions.assertNull(sessionMarker(second),
                    "the next borrower must sit on a fresh session and chain");
            StatementCache fresh = interceptorOf(second, StatementCache.class);
            String sqlTwo = "INSERT INTO " + table + " VALUES (? + 1)";
            PreparedStatement a = second.prepareStatement(sqlOne);
            Statement aPhysical = driverStatement(a);
            a.close();
            PreparedStatement b = second.prepareStatement(sqlTwo);
            Statement bPhysical = driverStatement(b);
            b.close();
            Assertions.assertFalse(aPhysical.isClosed(),
                    "the first statement of the fresh session must park");
            Assertions.assertFalse(bPhysical.isClosed(),
                    "the second must park too - the raced reservation must have "
                            + "come back off the pool-wide counter");
            Assertions.assertEquals(2, fresh.getCacheSizePerConnection());
            Assertions.assertEquals(2, fresh.getCacheSize(),
                    "the pool-wide counter must equal the parked count");
            Assertions.assertSame(aPhysical, driverStatement(second.prepareStatement(sqlOne)),
                    "both cached statements must serve their next prepare");
        } finally {
            second.close();
        }
    }

    /**
     * The cache map, swapped in like in {@link TestStatementCacheRacingPark},
     * on whose park point the disposal runs: the first {@code putIfAbsent}
     * takes the key with a sentinel, disposes the interceptor, and then lets
     * the real insert see the taken key, so the park is refused while its
     * reservation is still in flight. The sentinel is withdrawn again - the
     * entry the disconnect purge later walks is never a foreign object.
     */
    private static final class DisposalAtParkMap extends ConcurrentHashMap<Object, Object> {

        private static final long serialVersionUID = 1L;

        private final AtomicBoolean racedOnce = new AtomicBoolean();

        /** Run by the first intercepted insert, inside the park window. */
        volatile Runnable disposal = () -> {
        };

        /** Whether the disposal really ran inside a park. */
        volatile boolean raced;

        @Override
        public Object putIfAbsent(Object key, Object value) {
            if (racedOnce.compareAndSet(false, true)) {
                super.putIfAbsent(key, this);
                disposal.run();
                Object refused = super.putIfAbsent(key, value);
                if (refused == this) {
                    super.remove(key, this);
                }
                raced = true;
                return refused;
            }
            return super.putIfAbsent(key, value);
        }
    }
}

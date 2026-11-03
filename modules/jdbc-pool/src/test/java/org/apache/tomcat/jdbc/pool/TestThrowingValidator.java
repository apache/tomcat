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
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A custom {@link Validator} cannot declare a checked exception, so
 * throwing is the only way it can report its own failure. The pool must
 * treat such a runtime exception as a failed validation: it must not
 * escape {@code getConnection()}/{@code close()}, must not strand the
 * pooled connection between the idle and busy queues (a permanently lost
 * slot), and must not silently disable idle eviction.
 *
 * <p>An {@code Error} thrown from behind an implementation counts the
 * same way: an implementation loaded through a web-application class
 * loader fails with {@code LinkageError} variants long after any
 * deliberate throw, and the guard must be as wide as the stranding it
 * prevents - fatal JVM errors excepted.</p>
 */
class TestThrowingValidator extends JdbcPoolBaseTest {

    /** The natural broken implementation: reports its failure by throwing. */
    public static class ThrowingValidator implements Validator {
        @Override
        public boolean validate(Connection connection, int validateAction) {
            throw new IllegalStateException("validator boom");
        }
    }

    /**
     * The broken implementation of the stopped-web-application case: a
     * class the implementation can no longer reach fails every call with
     * a LinkageError, which no implementation author throws on purpose.
     */
    public static class ErrorThrowingValidator implements Validator {
        @Override
        public boolean validate(Connection connection, int validateAction) {
            throw new NoClassDefFoundError("the context this validator was loaded from is stopped");
        }
    }

    /** A validator that only fails, the way the throwing one now fails. */
    public static class RejectingValidator implements Validator {
        @Override
        public boolean validate(Connection connection, int validateAction) {
            return false;
        }
    }

    /* One-slot pool with a throwing validator, gated on by the caller's test flag. */
    private DataSource throwingPool() {
        return throwingPool(new ThrowingValidator());
    }

    /* As above, with the implementation of the caller's choosing. */
    private DataSource throwingPool(Validator validator) {
        DataSource ds = newDataSource();
        ds.getPoolProperties().setInitialSize(0);
        // exactly one slot: any leaked slot exhausts the pool immediately
        ds.getPoolProperties().setMaxActive(1);
        ds.getPoolProperties().setValidationInterval(0);
        // the tests drive validation themselves; with the guard in place a
        // background sweep would now be a working competitor for the idle
        // assertions below
        ds.getPoolProperties().setTimeBetweenEvictionRunsMillis(0);
        ds.getPoolProperties().setValidator(validator);
        return ds;
    }

    @Test
    void throwingValidatorOnBorrowDoesNotLeakTheSlot() throws Exception {
        DataSource ds = throwingPool();
        ds.getPoolProperties().setTestOnBorrow(true);
        // the creating borrow is not validated (testOnConnect is off), the
        // validator first runs when the returned connection is re-borrowed
        Connection first = ds.getConnection();
        awaitSessions(1);
        first.close();
        Assertions.assertEquals(1, ds.getPool().getIdle());

        // the borrow must fail, but as the checked SQLException the
        // DataSource contract allows - not as the validator's throw
        SQLException sqlX = Assertions.assertThrows(SQLException.class,
                ds::getConnection);
        Assertions.assertTrue(sqlX.getMessage().contains("Failed to validate"),
                "expected a validation failure, got: " + sqlX.getMessage());
        // and the attempt must not strand the connection: it was polled out
        // of idle, so a throw escaping the validation would leave it in
        // neither idle nor busy while still counted in size
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getIdle());
        Assertions.assertEquals(0, ds.getPool().getActive());
        Assertions.assertEquals(0, ds.getPool().getSize());
    }

    @Test
    void throwingValidatorOnReturnDoesNotLeakTheSlot() throws Exception {
        DataSource ds = throwingPool();
        ds.getPoolProperties().setTestOnReturn(true);
        Connection first = ds.getConnection();
        awaitSessions(1);

        // close() completes normally: the throwing validation counts as a
        // failure, so the connection is discarded rather than returned
        Assertions.assertDoesNotThrow(first::close);
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getIdle());
        Assertions.assertEquals(0, ds.getPool().getActive());
        Assertions.assertEquals(0, ds.getPool().getSize());

        // the pool has not permanently lost capacity: the discarded slot
        // was returned to the size budget, and the next borrow succeeds
        ds.getPoolProperties().setValidator(null);
        Assertions.assertEquals(0, ds.getPool().getSize());
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        Assertions.assertEquals(0, ds.getPool().getActive());
        // the discarded connection's physical session is gone, and the
        // rebuilt one is released when the pool closes
        ds.close();
        awaitSessions(0);
    }

    @Test
    void throwingValidatorOnIdleValidationDoesNotBlockEviction() throws Exception {
        DataSource ds = throwingPool();
        ds.getPoolProperties().setTestWhileIdle(true);
        Connection first = ds.getConnection();
        first.close();
        awaitSessions(1);
        Assertions.assertEquals(1, ds.getPool().getIdle());

        // the throw must not abort the sweep with the broken connection
        // still parked in idle forever
        Assertions.assertDoesNotThrow(ds::testIdle);
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getIdle());
    }

    @Test
    void throwingValidatorOnConnectFailsAsCheckedException() throws Exception {
        DataSource ds = throwingPool();
        ds.getPoolProperties().setTestOnConnect(true);
        // the creation path was already guarded by a catch(Exception) that
        // releases the half-built connection; the behavior must not regress
        Assertions.assertThrows(SQLException.class, ds::getConnection);
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getSize());
    }

    @Test
    void errorThrowingValidatorOnBorrowFailsAsCheckedException() throws Exception {
        DataSource ds = throwingPool(new ErrorThrowingValidator());
        ds.getPoolProperties().setTestOnBorrow(true);
        // The stopped-class-loader failure mode: the LinkageError must be
        // read as a check that cannot vouch for the session, not as a
        // condition beyond the pool's handling - a throw escaping the
        // borrow would strand the session outside both queues, where no
        // drain, sweep or abandoned check can ever reach it.
        Connection first = ds.getConnection();
        awaitSessions(1);
        first.close();
        Assertions.assertEquals(1, ds.getPool().getIdle());

        SQLException sqlX = Assertions.assertThrows(SQLException.class, ds::getConnection);
        Assertions.assertTrue(sqlX.getMessage().contains("Failed to validate"),
                "expected a validation failure, got: " + sqlX.getMessage());
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getSize());

        // and the slot survived its own validator: the pool is whole
        ds.getPoolProperties().setValidator(null);
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        // the returned session parks in idle, where it is whole and
        // reusable - the failed borrow cost the pool nothing
        Assertions.assertEquals(1, ds.getPool().getIdle());
        Assertions.assertEquals(1, ds.getPool().getSize());
        ds.close();
        awaitSessions(0);
    }

    @Test
    void errorThrowingValidatorOnReturnDoesNotEscapeOrStrand() throws Exception {
        DataSource ds = throwingPool(new ErrorThrowingValidator());
        ds.getPoolProperties().setTestOnReturn(true);
        Connection first = ds.getConnection();
        awaitSessions(1);

        Assertions.assertDoesNotThrow(first::close);
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getIdle());
        Assertions.assertEquals(0, ds.getPool().getActive());
        Assertions.assertEquals(0, ds.getPool().getSize());

        ds.getPoolProperties().setValidator(null);
        try (Connection again = ds.getConnection()) {
            Assertions.assertNotNull(again);
        }
        ds.close();
        awaitSessions(0);
    }

    @Test
    void errorThrowingValidatorOnIdleValidationDoesNotAbortTheSweep() throws Exception {
        DataSource ds = throwingPool(new ErrorThrowingValidator());
        ds.getPoolProperties().setTestWhileIdle(true);
        Connection first = ds.getConnection();
        first.close();
        awaitSessions(1);
        Assertions.assertEquals(1, ds.getPool().getIdle());

        // the Error must not escape the sweep with the tested connection
        // claimed out of idle and stranded, nor leave it parked untested
        Assertions.assertDoesNotThrow(ds::testIdle);
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getIdle());
    }

    /**
     * The shape a runtime swap of the validator takes when it races the
     * call from inside: the null check is answered the validator, the
     * next read is answered the replacement - here, none.
     */
    private static final class SwapOnSecondRead extends PoolProperties {
        private Validator real;
        private int reads;

        void install(Validator validator) {
            real = validator;
        }

        @Override
        public Validator getValidator() {
            return (++reads) == 1 ? real : null;
        }
    }

    /** An implementation that vouches for every session it is asked about. */
    public static class CountingValidator implements Validator {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean validate(Connection connection, int validateAction) {
            calls.incrementAndGet();
            return true;
        }
    }

    @Test
    void aswapOutOfTheValidatorMidCallDoesNotSpoilTheCheck() throws Exception {
        CountingValidator validator = new CountingValidator();
        SwapOnSecondRead props = new SwapOnSecondRead();
        props.install(validator);
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        props.setTestOnBorrow(true);
        props.setValidationInterval(0);
        props.setTimeBetweenEvictionRunsMillis(-1);

        ConnectionPool pool = new ConnectionPool(props);
        PooledConnection con = new PooledConnection(props, pool);
        con.connect();
        try {
            // The swap races behind a validation the null check already
            // admitted. A re-read null must not reach the call: validate
            // would read the null dereference as its own throw, a failed
            // check that retires a healthy session no validator ever
            // refused - and a re-read of a replacement validator would
            // answer the check while the log line blamed the first.
            Assertions.assertTrue(con.validate(PooledConnection.VALIDATE_BORROW),
                    "the replacement read after the null check must not spoil the check");
            Assertions.assertEquals(1, validator.calls.get(),
                    "the validator the null check saw is the one that must have run");
        } finally {
            con.release();
        }
    }

    @Test
    void rejectingValidatorOnBorrowFailsAndReleasesTheSlot() throws Exception {
        DataSource ds = throwingPool();
        ds.getPoolProperties().setTestOnBorrow(true);
        ds.getPoolProperties().setValidator(new RejectingValidator());
        // control for the new guard shape: a plain false from the validator
        // keeps its pre-existing meaning all the way through
        Connection first = ds.getConnection();
        awaitSessions(1);
        first.close();
        Assertions.assertThrows(SQLException.class, ds::getConnection);
        awaitSessions(0);
        Assertions.assertEquals(0, ds.getPool().getSize());
    }
}

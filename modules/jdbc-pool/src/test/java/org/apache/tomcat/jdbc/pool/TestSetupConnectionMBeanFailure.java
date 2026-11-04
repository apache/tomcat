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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A borrow reads the pool's JMX object when it registers the per-connection
 * MBean, but {@code close()} gives that object up without holding any pool
 * lock, so a borrow racing the close can find it gone between a null check
 * and its use. Read unsafely, that produced a NullPointerException from
 * {@code PooledConnection.createMBean()} inside
 * {@code ConnectionPool.setupConnection()}. The interesting part is where
 * that lands: the connection is already in the busy queue when setup runs,
 * and a failure that skips {@code abandonSetup()} strands it there - the
 * physical session and a maxActive slot are lost until the pool restarts.
 * <p>
 * The window between check and use is a few instructions wide and cannot be
 * reproduced deterministically. What these tests pin down instead is the
 * contract the fix makes total: the registration is skipped when the pool has
 * no JMX object to register under - rather than dereferencing it blindly -
 * and it runs unconditionally from the setup path, so that whatever it fails
 * with, the borrowed connection goes back to the pool. That the remaining
 * JMX notifications read the volatile field into a local before using it is
 * carried by review, not by a test.
 */
class TestSetupConnectionMBeanFailure extends JdbcPoolBaseTest {

    /*
     * A connection whose JMX registration fails while the flag is set, and
     * performs the real registration once the test lets it through.
     */
    static class FailingRegistrationConnection extends PooledConnection {

        private final AtomicBoolean failRegistration;

        FailingRegistrationConnection(PoolConfiguration prop, ConnectionPool parent,
                AtomicBoolean failRegistration) {
            super(prop, parent);
            this.failRegistration = failRegistration;
        }

        @Override
        public void createMBean() {
            if (failRegistration.get()) {
                throw new IllegalStateException("boom from createMBean()");
            }
            super.createMBean();
        }
    }

    /*
     * A pool handing out the connections above. The base create() is still
     * invoked for its pool-size bookkeeping; the pooled connection object it
     * builds is never connected and simply discarded.
     */
    static class FailingRegistrationPool extends ConnectionPool {

        final AtomicBoolean failRegistration = new AtomicBoolean(true);

        FailingRegistrationPool(PoolConfiguration prop) throws SQLException {
            super(prop);
        }

        @Override
        protected PooledConnection create(boolean incrementCounter) {
            super.create(incrementCounter);
            return new FailingRegistrationConnection(getPoolProperties(), this, failRegistration);
        }
    }

    /*
     * The pool is built directly rather than through a DataSource: the setup
     * path and the failure seam are package-protected, and JMX stays disabled
     * so nothing reaches the platform MBean server.
     */
    private static PoolConfiguration newPoolProperties() {
        PoolProperties props = new PoolProperties();
        props.setUrl(getUrl());
        props.setDriverClassName("org.h2.Driver");
        props.setUsername("sa");
        props.setPassword("");
        props.setJmxEnabled(false);
        props.setInitialSize(0);
        props.setMinIdle(0);
        // one slot only: a stranded connection would show up as an
        // exhausted pool on the very next borrow
        props.setMaxActive(1);
        return props;
    }

    @Test
    void createMBeanSkipsRegistrationWhenPoolHasNoJmxObject() throws Exception {
        ConnectionPool pool = new ConnectionPool(newPoolProperties());
        try {
            Assertions.assertNull(pool.getJmxPool(),
                    "a pool with JMX disabled has no MBean to register under");
            PooledConnection con = pool.create(false);
            // Before the fix this dereferenced the pool's absent JMX object
            // and threw a NullPointerException; the registration must instead
            // be skipped, leaving the connection unregistered.
            Assertions.assertDoesNotThrow(con::createMBean,
                    "a missing pool MBean must not fail the connection's registration");
            Assertions.assertNull(con.getObjectName(),
                    "nothing may be registered while the pool has no JMX object");
        } finally {
            pool.close(true);
        }
        Assertions.assertEquals(0, poolSessions(),
                "an unconnected pooled connection may not own a session");
    }

    @Test
    void failingMBeanRegistrationReturnsTheConnectionInsteadOfStrandingIt() throws Exception {
        FailingRegistrationPool pool = new FailingRegistrationPool(newPoolProperties());
        try {
            // The setup path must reach the registration even with JMX
            // disabled, so this is the failure the borrow has to survive; a
            // gate on the pool's JMX field in front of the call would let the
            // check-and-use window back in.
            SQLException x = Assertions.assertThrows(SQLException.class, pool::getConnection,
                    "a failing MBean registration must fail the borrow through the pool's own exception");
            Assertions.assertTrue(x.getCause() instanceof IllegalStateException,
                    "the borrow must report the registration failure, was: " + x.getCause());

            // The stranding defect: the connection was already in busy when
            // setup failed, so only the abandonSetup() path can hand it back.
            Assertions.assertEquals(0, pool.getActive(),
                    "a setup that must not strand the connection may not leave it in busy");
            Assertions.assertEquals(1, pool.getIdle(),
                    "the connection must go back to the idle queue, not be discarded");
            Assertions.assertEquals(1, pool.getBorrowedCount(), "the failed borrow is counted");
            Assertions.assertEquals(1, pool.getReturnedCount(),
                    "the hand-back after the failed setup is counted as a return");
            Assertions.assertEquals(1, poolSessions(),
                    "the physical connection must still be owned by the pool");

            // The slot the borrow needed is intact: with maxActive=1, a
            // stranded connection would exhaust the pool right here.
            pool.failRegistration.set(false);
            Connection borrowed = pool.getConnection();
            Assertions.assertNotNull(borrowed, "the pool must still be able to hand out its connection");
            borrowed.close();
            Assertions.assertEquals(0, pool.getActive());
            Assertions.assertEquals(1, pool.getIdle());
        } finally {
            pool.close(true);
        }
        awaitSessions(0);
    }
}

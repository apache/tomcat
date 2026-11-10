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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.JdbcPoolBaseTest;

/**
 * A connection registers its MBean when its physical session is established,
 * not when it is borrowed: JMX is a best-effort management channel, so the
 * borrow path neither retries a registration nor fails because one is
 * unavailable. The registration is skipped when the pool has no JMX object to
 * register under, rather than dereferencing it blindly.
 */
class TestSetupConnectionMBeanFailure extends JdbcPoolBaseTest {

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

}

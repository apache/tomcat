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

/**
 * The data source implements {@link javax.sql.DataSource#setLoginTimeout(int)} by mapping it onto the pool's borrow
 * wait, {@link PoolConfiguration#getMaxWait()} - the standard accessor is not ignored. The mapping converts seconds to
 * milliseconds, and the pool treats a wait of zero or less as its "no timeout" setting, so a conversion that changes
 * sign turns a configured timeout into the one value that never gives up waiting for a connection.
 */
class TestLoginTimeoutMapping {

    /* The branch that {@link ConnectionPool} takes when it computes a borrow deadline. */
    private static boolean waitsForever(PoolConfiguration properties) {
        return properties.getMaxWait() <= 0;
    }

    @Test
    void secondsAreMappedOntoTheBorrowWaitInMilliseconds() {
        DataSource ds = new DataSource();

        ds.setLoginTimeout(30);

        Assertions.assertEquals(30000, ds.getPoolProperties().getMaxWait(),
                "the login timeout is the pool's borrow wait, so it must be the same duration");
        Assertions.assertEquals(30, ds.getLoginTimeout(), "a sane value must read back unchanged");
        Assertions.assertFalse(waitsForever(ds.getPoolProperties()),
                "precondition: a configured wait is a bounded one");
    }

    @Test
    void theLargestSecondsValueThatFitsInMillisecondsStillMeansAWait() {
        DataSource ds = new DataSource();

        // 2147483 seconds is a shade under 25 days: the last value whose
        // milliseconds still fit the int the wait is stored in.
        ds.setLoginTimeout(2147483);

        Assertions.assertEquals(2147483000, ds.getPoolProperties().getMaxWait(),
                "a value that is representable must be mapped exactly, not clamped");
        Assertions.assertFalse(waitsForever(ds.getPoolProperties()), "and it must stay a bounded wait");
    }

    @Test
    void aLoginTimeoutTooLargeForMillisecondsStaysABoundedWait() {
        DataSource ds = new DataSource();

        // One second further and the product no longer fits: multiplied in
        // int arithmetic it wraps negative, and the pool reads a negative wait
        // as "no timeout", which is the opposite of what was configured.
        ds.setLoginTimeout(2147484);

        Assertions.assertEquals(Integer.MAX_VALUE, ds.getPoolProperties().getMaxWait(),
                "a seconds value that cannot be represented in milliseconds must clamp to the longest "
                        + "timeout there is, rather than wrap into a number that means something else");
        Assertions.assertFalse(waitsForever(ds.getPoolProperties()),
                "the longest timeout on offer is still a timeout: setting one must never land on the "
                        + "value the pool reads as an unbounded wait");
    }

    @Test
    void theLargestPossibleSecondsValueStaysABoundedWait() {
        DataSource ds = new DataSource();

        ds.setLoginTimeout(Integer.MAX_VALUE);

        Assertions.assertFalse(waitsForever(ds.getPoolProperties()),
                "Integer.MAX_VALUE seconds multiplied by 1000 wraps to minus one second in int "
                        + "arithmetic, which the pool reads as no timeout at all");
    }
}

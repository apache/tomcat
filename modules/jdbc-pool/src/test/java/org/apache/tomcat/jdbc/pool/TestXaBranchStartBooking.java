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

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.XAConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A branch must be visible to the pool's return check as soon as the driver can
 * have accepted it, not only after {@link XAResource#start(Xid, int)} returns.
 * The driver call may be concurrent with a return of the same pooled handle.
 */
class TestXaBranchStartBooking {

    private static final Xid XID = new Xid() {
        @Override
        public int getFormatId() {
            return 1;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return new byte[] { 1 };
        }

        @Override
        public byte[] getBranchQualifier() {
            return new byte[] { 2 };
        }
    };

    private static XAConnection xaConnection(XAResource resource) {
        return (XAConnection) Proxy.newProxyInstance(TestXaBranchStartBooking.class.getClassLoader(),
                new Class[] { XAConnection.class }, (proxy, method, args) -> {
                    if ("getXAResource".equals(method.getName())) {
                        return resource;
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) {
                        return Boolean.FALSE;
                    }
                    if (type == int.class) {
                        return Integer.valueOf(0);
                    }
                    return null;
                });
    }

    private static XAResource blockingResource(CountDownLatch accepted, CountDownLatch release) {
        return (XAResource) Proxy.newProxyInstance(TestXaBranchStartBooking.class.getClassLoader(),
                new Class[] { XAResource.class }, (proxy, method, args) -> {
                    if ("start".equals(method.getName())) {
                        accepted.countDown();
                        try {
                            release.await(15000, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException x) {
                            Thread.currentThread().interrupt();
                            throw new XAException(XAException.XAER_RMERR);
                        }
                        return null;
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) {
                        return Boolean.FALSE;
                    }
                    if (type == int.class) {
                        return Integer.valueOf(0);
                    }
                    return null;
                });
    }

    @Test
    void aBranchIsBookedWhileTheDriverStartIsStillRunning() throws Exception {
        CountDownLatch accepted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        XABranchTracker tracker = new XABranchTracker(xaConnection(blockingResource(accepted, release)));

        Thread starter = new Thread(() -> {
            try {
                tracker.getXAResource().start(XID, XAResource.TMNOFLAGS);
            } catch (XAException | java.sql.SQLException x) {
                throw new IllegalStateException(x);
            }
        }, "xa-branch-starter");
        starter.setDaemon(true);
        starter.start();

        Assertions.assertTrue(accepted.await(15000, TimeUnit.MILLISECONDS),
                "the driver must have accepted the start call");
        Assertions.assertTrue(tracker.hasUnresolvedBranches(),
                "the branch must be visible to a concurrent return before start() returns");

        release.countDown();
        starter.join(15000);
        Assertions.assertFalse(starter.isAlive(), "the start call must complete");
        Assertions.assertTrue(tracker.hasUnresolvedBranches(), "the accepted branch must stay booked");
    }

    @Test
    void aRetiredSessionRefusesRecoverAndTransactionTimeoutCalls() throws Exception {
        AtomicInteger delegateCalls = new AtomicInteger();
        XAResource resource = (XAResource) Proxy.newProxyInstance(
                TestXaBranchStartBooking.class.getClassLoader(), new Class[] { XAResource.class },
                (proxy, method, args) -> {
                    delegateCalls.incrementAndGet();
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) {
                        return Boolean.FALSE;
                    }
                    if (type == int.class) {
                        return Integer.valueOf(0);
                    }
                    return null;
                });
        XABranchTracker tracker = new XABranchTracker(xaConnection(resource));
        tracker.markClosed();

        assertRetired(Assertions.assertThrows(XAException.class,
                () -> tracker.getXAResource().recover(XAResource.TMSTARTRSCAN)));
        assertRetired(Assertions.assertThrows(XAException.class,
                tracker.getXAResource()::getTransactionTimeout));
        assertRetired(Assertions.assertThrows(XAException.class,
                () -> tracker.getXAResource().setTransactionTimeout(10)));
        Assertions.assertEquals(0, delegateCalls.get(),
                "none of the retired-session calls may reach the closed driver resource");
    }

    private static void assertRetired(XAException failure) {
        Assertions.assertEquals(XAException.XAER_RMFAIL, failure.errorCode,
                "a retired session must refuse with XAER_RMFAIL");
    }

    @Test
    void aRefusedStartRemovesOnlyAKeyItNewlyBooked() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        XAResource resource = (XAResource) Proxy.newProxyInstance(TestXaBranchStartBooking.class.getClassLoader(),
                new Class[] { XAResource.class }, (proxy, method, args) -> {
                    if ("start".equals(method.getName()) && starts.incrementAndGet() > 1) {
                        throw new XAException(XAException.XAER_NOTA);
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) {
                        return Boolean.FALSE;
                    }
                    if (type == int.class) {
                        return Integer.valueOf(0);
                    }
                    return null;
                });
        XABranchTracker tracker = new XABranchTracker(xaConnection(resource));

        tracker.getXAResource().start(XID, XAResource.TMNOFLAGS);
        Assertions.assertTrue(tracker.hasUnresolvedBranches(), "the accepted branch must be booked");

        // A join/resume of the already booked branch is refused. The refusal must not remove the live branch.
        Assertions.assertThrows(XAException.class,
                () -> tracker.getXAResource().start(XID, XAResource.TMJOIN));
        Assertions.assertTrue(tracker.hasUnresolvedBranches(),
                "a refused join must not remove the branch it did not create");

        // A refused fresh branch leaves no bookkeeping.
        XAResource refusing = (XAResource) Proxy.newProxyInstance(
                TestXaBranchStartBooking.class.getClassLoader(), new Class[] { XAResource.class },
                (proxy, method, args) -> {
                    if ("start".equals(method.getName())) {
                        throw new XAException(XAException.XAER_NOTA);
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) {
                        return Boolean.FALSE;
                    }
                    if (type == int.class) {
                        return Integer.valueOf(0);
                    }
                    return null;
                });
        XABranchTracker freshTracker = new XABranchTracker(xaConnection(refusing));
        Assertions.assertThrows(XAException.class,
                () -> freshTracker.getXAResource().start(XID, XAResource.TMNOFLAGS));
        Assertions.assertFalse(freshTracker.hasUnresolvedBranches(),
                "a branch whose start the driver refused leaves no bookkeeping");
    }
}

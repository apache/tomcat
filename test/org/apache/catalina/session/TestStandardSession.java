/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Manager;
import org.apache.catalina.core.StandardContext;

public class TestStandardSession {

    private static final Manager TEST_MANAGER;

    static {
        TEST_MANAGER = new StandardManager();
        TEST_MANAGER.setContext(new StandardContext());
    }


    @Test
    public void testSerializationEmpty() throws Exception {

        StandardSession s1 = new StandardSession(TEST_MANAGER);
        s1.setValid(true);
        StandardSession s2 = serializeThenDeserialize(s1);

        validateSame(s1, s2, 0);
    }


    @Test
    public void testSerializationSimple01() throws Exception {

        StandardSession s1 = new StandardSession(TEST_MANAGER);
        s1.setValid(true);
        s1.setAttribute("attr01", "value01");

        StandardSession s2 = serializeThenDeserialize(s1);

        validateSame(s1, s2, 1);
    }


    @Test
    public void testSerializationSimple02() throws Exception {

        StandardSession s1 = new StandardSession(TEST_MANAGER);
        s1.setValid(true);
        s1.setAttribute("attr01", new NonSerializable());

        StandardSession s2 = serializeThenDeserialize(s1);

        validateSame(s1, s2, 0);
    }


    @Test
    public void testSerializationSimple03() throws Exception {

        StandardSession s1 = new StandardSession(TEST_MANAGER);
        s1.setValid(true);
        s1.setAttribute("attr01", "value01");
        s1.setAttribute("attr02", new NonSerializable());

        StandardSession s2 = serializeThenDeserialize(s1);

        validateSame(s1, s2, 1);
    }


    /*
     * See Bug 58284
     */
    @Test
    public void serializeSkipsNonSerializableAttributes() throws Exception {
        final String nonSerializableKey = "nonSerializable";
        final String nestedNonSerializableKey = "nestedNonSerializable";
        final String serializableKey = "serializable";
        final Object serializableValue = "foo";

        StandardSession s1 = new StandardSession(TEST_MANAGER);
        s1.setValid(true);
        Map<String,NonSerializable> value = new HashMap<>();
        value.put("key", new NonSerializable());
        s1.setAttribute(nestedNonSerializableKey, value);
        s1.setAttribute(serializableKey, serializableValue);
        s1.setAttribute(nonSerializableKey, new NonSerializable());

        StandardSession s2 = serializeThenDeserialize(s1);

        Assert.assertNull(s2.getAttribute(nestedNonSerializableKey));
        Assert.assertNull(s2.getAttribute(nonSerializableKey));
        Assert.assertEquals(serializableValue, s2.getAttribute(serializableKey));
    }


    @Test
    public void testBindingListenerAddedDuringExpiration() throws Exception {
        StandardSession session = new StandardSession(TEST_MANAGER);
        session.setValid(true);

        CountDownLatch valueBoundEntered = new CountDownLatch(1);
        CountDownLatch continueValueBound = new CountDownLatch(1);
        CountDownLatch valueUnboundCalled = new CountDownLatch(1);
        AtomicReference<Throwable> setAttributeException = new AtomicReference<>();
        HttpSessionBindingListener listener = new HttpSessionBindingListener() {

            @Override
            public void valueBound(HttpSessionBindingEvent event) {
                valueBoundEntered.countDown();
                try {
                    continueValueBound.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void valueUnbound(HttpSessionBindingEvent event) {
                valueUnboundCalled.countDown();
            }
        };

        Thread setAttributeThread = new Thread(() -> {
            try {
                session.setAttribute("listener", listener);
            } catch (Throwable t) {
                setAttributeException.set(t);
            }
        });
        setAttributeThread.start();

        try {
            Assert.assertTrue(valueBoundEntered.await(10, TimeUnit.SECONDS));
            session.expire();
        } finally {
            continueValueBound.countDown();
        }
        setAttributeThread.join(10000);

        // The attribute lost the race with expiration: setAttribute() must not throw and must not leave the
        // listener bound without a matching valueUnbound() call.
        Assert.assertFalse(setAttributeThread.isAlive());
        Assert.assertNull(setAttributeException.get());
        Assert.assertTrue(valueUnboundCalled.await(10, TimeUnit.SECONDS));
    }


    @Test
    public void testBindingListenerUnchangedValueUnboundOnceDuringExpiration() throws Exception {
        NotifyStallingManager manager = new NotifyStallingManager();
        manager.setContext(new StandardContext());

        StandardSession session = new StandardSession(manager);
        session.setValid(true);

        AtomicInteger boundCount = new AtomicInteger();
        AtomicInteger unboundCount = new AtomicInteger();
        HttpSessionBindingListener listener = new HttpSessionBindingListener() {

            @Override
            public void valueBound(HttpSessionBindingEvent event) {
                boundCount.incrementAndGet();
            }

            @Override
            public void valueUnbound(HttpSessionBindingEvent event) {
                unboundCount.incrementAndGet();
            }
        };

        // Bind normally first.
        session.setAttribute("listener", listener);
        Assert.assertEquals(1, boundCount.get());

        // Re-setting the same reference under the same name skips valueBound() (the default
        // getNotifyBindingListenerOnUnchangedValue() is false), but that decision reads
        // Manager.getNotifyBindingListenerOnUnchangedValue(). Stall there so the session can
        // expire - and legitimately unbind the listener - while this call is still in flight.
        manager.stall = true;
        Thread setAttributeThread = new Thread(() -> session.setAttribute("listener", listener));
        setAttributeThread.start();

        try {
            Assert.assertTrue(manager.atStallPoint.await(10, TimeUnit.SECONDS));
            session.expire();
        } finally {
            manager.releaseStall.countDown();
        }
        setAttributeThread.join(10000);

        Assert.assertFalse(setAttributeThread.isAlive());
        Assert.assertEquals(1, boundCount.get());
        // valueUnbound() must fire exactly once: from expire()'s sweep. The racing setAttribute()
        // call must not add a second, unmatched valueUnbound() since valueBound() was never called
        // for it.
        Assert.assertEquals(1, unboundCount.get());
    }


    @Test
    public void testNonListenerReplacementRejectedAfterConcurrentExpiration() throws Exception {
        DistributableStallingContext context = new DistributableStallingContext();
        Manager manager = new StandardManager();
        manager.setContext(context);

        StandardSession session = new StandardSession(manager);
        session.setValid(true);

        AtomicInteger unboundCount = new AtomicInteger();
        HttpSessionBindingListener oldListener = new HttpSessionBindingListener() {

            @Override
            public void valueUnbound(HttpSessionBindingEvent event) {
                unboundCount.incrementAndGet();
            }
        };
        session.setAttribute("x", oldListener);

        // The replacement value ("plainValue") is not itself a listener, so this call takes the
        // unguarded put() path. Stall inside the distributable check - evaluated for every
        // setAttribute() call, before that path is chosen - so the session can expire while this
        // call is still in flight.
        context.stall = true;
        AtomicReference<Throwable> setAttributeException = new AtomicReference<>();
        Thread setAttributeThread = new Thread(() -> {
            try {
                session.setAttribute("x", "plainValue");
            } catch (Throwable t) {
                setAttributeException.set(t);
            }
        });
        setAttributeThread.start();

        try {
            Assert.assertTrue(context.atStallPoint.await(10, TimeUnit.SECONDS));
            session.expire();
        } finally {
            context.releaseStall.countDown();
        }
        setAttributeThread.join(10000);

        Assert.assertFalse(setAttributeThread.isAlive());
        Assert.assertEquals(1, unboundCount.get());
        // setAttribute() started while the session was valid but the session expired before the
        // replacement was applied. It must not silently succeed against an invalidated session.
        Assert.assertTrue(setAttributeException.get() instanceof IllegalStateException);
    }


    private StandardSession serializeThenDeserialize(StandardSession source)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        source.writeObjectData(oos);

        StandardSession dest = new StandardSession(TEST_MANAGER);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        dest.readObjectData(ois);

        return dest;
    }


    private void validateSame(StandardSession s1, StandardSession s2, int expectedCount) {
        int count = 0;
        Enumeration<String> names = s1.getAttributeNames();
        while (names.hasMoreElements()) {
            count++;
            String name = names.nextElement();
            Object v1 = s1.getAttribute(name);
            Object v2 = s2.getAttribute(name);

            Assert.assertEquals(v1, v2);
        }

        Assert.assertEquals(expectedCount, count);
    }


    private static class NonSerializable {
    }


    /*
     * A Manager whose getNotifyBindingListenerOnUnchangedValue() blocks the calling thread on demand, to open a
     * controlled race window at that exact point in StandardSession.setAttribute().
     */
    private static class NotifyStallingManager extends StandardManager {
        private volatile boolean stall;
        private final CountDownLatch atStallPoint = new CountDownLatch(1);
        private final CountDownLatch releaseStall = new CountDownLatch(1);

        @Override
        public boolean getNotifyBindingListenerOnUnchangedValue() {
            if (stall) {
                atStallPoint.countDown();
                try {
                    releaseStall.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.getNotifyBindingListenerOnUnchangedValue();
        }
    }


    /*
     * A Context whose getDistributable() blocks the calling thread on demand, to open a controlled race window at that
     * exact point in StandardSession.setAttribute().
     */
    private static class DistributableStallingContext extends StandardContext {
        private volatile boolean stall;
        private final CountDownLatch atStallPoint = new CountDownLatch(1);
        private final CountDownLatch releaseStall = new CountDownLatch(1);

        @Override
        public boolean getDistributable() {
            if (stall) {
                atStallPoint.countDown();
                try {
                    releaseStall.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.getDistributable();
        }
    }
}

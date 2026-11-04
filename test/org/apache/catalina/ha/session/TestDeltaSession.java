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
package org.apache.catalina.ha.session;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Manager;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;

public class TestDeltaSession {

    @Test
    public void testDeltaSessionBindingListenerAddedDuringExpiration() throws Exception {
        StandardContext context = new StandardContext();
        Manager manager = new StandardManager();
        manager.setContext(context);

        DeltaSession session = new DeltaSession(manager);
        session.setValid(true);

        CountDownLatch valueBoundEntered = new CountDownLatch(1);
        CountDownLatch continueValueBound = new CountDownLatch(1);
        CountDownLatch sessionDestroyedEntered = new CountDownLatch(1);
        AtomicReference<Throwable> setAttributeException = new AtomicReference<>();
        AtomicReference<Throwable> expireException = new AtomicReference<>();

        context.setApplicationLifecycleListeners(new Object[] { new HttpSessionListener() {

            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                sessionDestroyedEntered.countDown();
                session.setAttribute("fromSessionDestroyed", "value");
            }
        } });

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
        };

        Thread setAttributeThread = new Thread(() -> {
            try {
                session.setAttribute("listener", listener);
            } catch (Throwable t) {
                setAttributeException.set(t);
            }
        });
        setAttributeThread.setDaemon(true);
        setAttributeThread.start();

        Thread expireThread = new Thread(() -> {
            try {
                session.expire();
            } catch (Throwable t) {
                expireException.set(t);
            }
        });
        expireThread.setDaemon(true);

        try {
            Assert.assertTrue(valueBoundEntered.await(10, TimeUnit.SECONDS));
            expireThread.start();
            Assert.assertTrue(sessionDestroyedEntered.await(10, TimeUnit.SECONDS));
        } finally {
            continueValueBound.countDown();
        }
        setAttributeThread.join(10000);
        expireThread.join(10000);

        Assert.assertFalse(setAttributeThread.isAlive());
        Assert.assertFalse(expireThread.isAlive());
        Assert.assertNull(setAttributeException.get());
        Assert.assertNull(expireException.get());
    }


    @Test
    public void testIsAccessReplicateNonPositiveInterval() {
        DeltaSession session = new DeltaSession(new StandardManager());
        // Last replicated long enough ago to exceed any reasonable interval
        session.setLastTimeReplicated(System.currentTimeMillis() - 3600 * 1000L);

        // Zero or negative means the session never times out so there is no
        // need to replicate access
        session.setMaxInactiveInterval(0);
        Assert.assertFalse(session.isAccessReplicate());
        session.setMaxInactiveInterval(-1);
        Assert.assertFalse(session.isAccessReplicate());

        // Positive interval that has elapsed since last replication
        session.setMaxInactiveInterval(60);
        Assert.assertTrue(session.isAccessReplicate());

        // Positive interval that has not yet elapsed
        session.setLastTimeReplicated(System.currentTimeMillis());
        Assert.assertFalse(session.isAccessReplicate());
    }
}

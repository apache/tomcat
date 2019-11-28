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

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.Store;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.RequestFacade;
import org.apache.tomcat.unittest.TesterContext;
import org.apache.tomcat.unittest.TesterHost;
import org.easymock.EasyMock;
import org.easymock.IAnswer;

public class TestPersistentManager {

    @Test
    public void testMinIdleSwap() throws Exception {
        PersistentManager manager = new PersistentManager();
        manager.setStore(new TesterStore());

        Host host = new TesterHost();
        Context context = new TesterContext();
        context.setParent(host);

        manager.setContext(context);

        manager.setMaxActiveSessions(2);
        manager.setMinIdleSwap(0);

        manager.start();

        // Create the maximum number of sessions
        manager.createSession(null);
        manager.createSession(null);

        // Given the minIdleSwap settings, this should swap one out to get below
        // the limit
        manager.processPersistenceChecks();
        Assert.assertEquals(1, manager.getActiveSessions());
        Assert.assertEquals(2, manager.getActiveSessionsFull());

        manager.createSession(null);
        Assert.assertEquals(2, manager.getActiveSessions());
        Assert.assertEquals(3, manager.getActiveSessionsFull());
    }

    @Test
    public void testBug62175() throws Exception {
        PersistentManager manager = new PersistentManager();
        AtomicInteger sessionExpireCounter = new AtomicInteger();

        Store mockStore = EasyMock.createNiceMock(Store.class);
        EasyMock.expect(mockStore.load(EasyMock.anyString())).andAnswer(new IAnswer<Session>() {

            @Override
            public Session answer() throws Throwable {
                return timedOutSession(manager, sessionExpireCounter);
            }
        }).anyTimes();

        EasyMock.replay(mockStore);

        manager.setStore(mockStore);

        Host host = new TesterHost();

        RequestCachingSessionListener requestCachingSessionListener = new RequestCachingSessionListener();

        Context context = new TesterContext() {

            @Override
            public Object[] getApplicationLifecycleListeners() {
                return new Object[] { requestCachingSessionListener };
            }

            @Override
            public Manager getManager() {
                return manager;
            }
        };
        context.setParent(host);

        Connector connector = EasyMock.createNiceMock(Connector.class);
        Request req = new Request(connector) {
            @Override
            public Context getContext() {
                return context;
            }
        };
        req.setRequestedSessionId("invalidSession");
        HttpServletRequest request = new RequestFacade(req);
        EasyMock.replay(connector);
        requestCachingSessionListener.request = request;

        manager.setContext(context);

        manager.start();

        Assert.assertNull(request.getSession(false));
        Assert.assertEquals(1, sessionExpireCounter.get());

    }

    private static class RequestCachingSessionListener implements HttpSessionListener {

        private HttpServletRequest request;

        @Override
        public void sessionDestroyed(HttpSessionEvent se) {
            request.getSession(false);
        }
    }

    private StandardSession timedOutSession(PersistentManager manager, AtomicInteger counter) {
        StandardSession timedOutSession = new StandardSession(manager) {
            private static final long serialVersionUID = -5910605558747844210L;

            @Override
            public void expire() {
                counter.incrementAndGet();
                super.expire();
            }
        };
        timedOutSession.isValid = true;
        timedOutSession.expiring = false;
        timedOutSession.maxInactiveInterval = 1;
        timedOutSession.lastAccessedTime = 0;
        timedOutSession.id = "invalidSession";
        return timedOutSession;
    }
}


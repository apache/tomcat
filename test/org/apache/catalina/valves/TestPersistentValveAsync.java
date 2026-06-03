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
package org.apache.catalina.valves;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.Store;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.PersistentManager;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;

public class TestPersistentValveAsync extends TomcatBaseTest {

    @Test
    public void testAsyncRequestStoresSessionOnComplete() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StandardContext context = (StandardContext) getProgrammaticRootContext();
        context.setDistributable(true);

        Wrapper wrapper = Tomcat.addServlet(context, "async-complete", new AsyncCompleteServlet());
        wrapper.setAsyncSupported(true);
        context.addServletMappingDecoded("/async-complete", "async-complete");

        TesterStore store = new TesterStore();
        PersistentManager manager = configurePersistentManager(context, store, new PersistentValve());

        tomcat.start();

        ByteChunk responseBody = getUrl("http://localhost:" + getPort() + "/async-complete");
        String sessionId = responseBody.toString();

        Assert.assertEquals(List.of(sessionId), store.getSavedIds());
        Assert.assertNotNull(store.load(sessionId));
        Assert.assertEquals(0, manager.getActiveSessions());
    }


    @Test
    public void testSecondAsyncCycleStoresSessionOnFinalComplete() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StandardContext context = (StandardContext) getProgrammaticRootContext();
        context.setDistributable(true);

        Wrapper wrapper = Tomcat.addServlet(context, "async-dispatch", new AsyncDispatchServlet());
        wrapper.setAsyncSupported(true);
        context.addServletMappingDecoded("/async-dispatch", "async-dispatch");

        TesterStore store = new TesterStore();
        PersistentManager manager = configurePersistentManager(context, store, new PersistentValve());

        tomcat.start();

        ByteChunk responseBody = getUrl("http://localhost:" + getPort() + "/async-dispatch");
        String sessionId = responseBody.toString();

        Assert.assertEquals(List.of(sessionId), store.getSavedIds());
        Assert.assertNotNull(store.load(sessionId));
        Assert.assertEquals(0, manager.getActiveSessions());
    }


    @Test
    public void testSemaphoreHeldWhileAsyncRequestInProgress() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        StandardContext context = (StandardContext) getProgrammaticRootContext();
        context.setDistributable(true);

        Tomcat.addServlet(context, "session", new SessionServlet());
        context.addServletMappingDecoded("/session", "session");

        CountDownLatch asyncStarted = new CountDownLatch(1);
        CountDownLatch allowAsyncComplete = new CountDownLatch(1);
        Wrapper asyncWrapper =
                Tomcat.addServlet(context, "async-block", new BlockingAsyncServlet(asyncStarted, allowAsyncComplete));
        asyncWrapper.setAsyncSupported(true);
        context.addServletMappingDecoded("/async-block", "async-block");

        TesterStore store = new TesterStore();
        PersistentValve persistentValve = new PersistentValve();
        persistentValve.setSemaphoreBlockOnAcquire(false);
        configurePersistentManager(context, store, persistentValve);

        tomcat.start();

        String sessionId = "TEST-SESSION-ID";

        Map<String,List<String>> requestHeaders = cookieHeaders(sessionId);

        ByteChunk asyncResponseBody = new ByteChunk();
        AtomicInteger asyncResponseCode = new AtomicInteger(-1);
        AtomicReference<Throwable> asyncFailure = new AtomicReference<>();
        Thread asyncClientThread = new Thread(() -> {
            try {
                asyncResponseCode.set(getUrl("http://localhost:" + getPort() + "/async-block", asyncResponseBody,
                        requestHeaders, null));
            } catch (Throwable t) {
                asyncFailure.set(t);
            }
        });

        asyncClientThread.start();

        Assert.assertTrue(asyncStarted.await(10, TimeUnit.SECONDS));

        ByteChunk rejectedOne = new ByteChunk();
        int rejectedOneStatus = getUrl("http://localhost:" + getPort() + "/session", rejectedOne, requestHeaders, null);
        Assert.assertEquals(HttpServletResponse.SC_TOO_MANY_REQUESTS, rejectedOneStatus);

        ByteChunk rejectedTwo = new ByteChunk();
        int rejectedTwoStatus = getUrl("http://localhost:" + getPort() + "/session", rejectedTwo, requestHeaders, null);
        Assert.assertEquals(HttpServletResponse.SC_TOO_MANY_REQUESTS, rejectedTwoStatus);

        allowAsyncComplete.countDown();
        asyncClientThread.join(10000);

        Assert.assertNull(asyncFailure.get());
        Assert.assertEquals(HttpServletResponse.SC_OK, asyncResponseCode.get());
        Assert.assertEquals(sessionId, asyncResponseBody.toString());

        ByteChunk success = new ByteChunk();
        int successStatus = getUrl("http://localhost:" + getPort() + "/session", success, requestHeaders, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, successStatus);
        Assert.assertFalse(success.isNull());
    }


    private PersistentManager configurePersistentManager(StandardContext context, TesterStore store,
            PersistentValve valve) {
        PersistentManager manager = new PersistentManager();
        manager.setStore(store);
        manager.setMaxIdleBackup(0);
        manager.setSessionActivityCheck(true);
        context.setManager(manager);
        context.addValve(valve);
        return manager;
    }


    private Map<String,List<String>> cookieHeaders(String sessionId) {
        Map<String,List<String>> result = new HashMap<>();
        result.put("Cookie", List.of("JSESSIONID=" + sessionId));
        return result;
    }


    private static class SessionServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            HttpSession session = request.getSession();
            response.getWriter().print(session.getId());
        }
    }


    private static class AsyncCompleteServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            HttpSession session = request.getSession();
            AsyncContext asyncContext = request.startAsync();
            asyncContext.start(() -> {
                try {
                    response.getWriter().print(session.getId());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                } finally {
                    asyncContext.complete();
                }
            });
        }
    }


    private static class AsyncDispatchServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            HttpSession session = request.getSession();

            if (request.getAttribute("asyncCycleStarted") == null) {
                request.setAttribute("asyncCycleStarted", Boolean.TRUE);
                AsyncContext asyncContext = request.startAsync();
                asyncContext.start(asyncContext::dispatch);
            } else {
                AsyncContext asyncContext = request.startAsync();
                asyncContext.start(() -> {
                    try {
                        response.getWriter().print(session.getId());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        asyncContext.complete();
                    }
                });
            }
        }
    }


    private static class BlockingAsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final CountDownLatch asyncStarted;
        private final CountDownLatch allowAsyncComplete;

        private BlockingAsyncServlet(CountDownLatch asyncStarted, CountDownLatch allowAsyncComplete) {
            this.asyncStarted = asyncStarted;
            this.allowAsyncComplete = allowAsyncComplete;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            String sessionId = request.getRequestedSessionId();
            AsyncContext asyncContext = request.startAsync();
            asyncContext.start(() -> {
                asyncStarted.countDown();
                try {
                    Assert.assertTrue(allowAsyncComplete.await(10, TimeUnit.SECONDS));
                    response.getWriter().print(sessionId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                } finally {
                    asyncContext.complete();
                }
            });
        }
    }


    private static class TesterStore implements Store {

        private Manager manager;
        private final Map<String,Session> sessions = new HashMap<>();
        private final List<String> savedIds = new ArrayList<>();

        private List<String> getSavedIds() {
            return savedIds;
        }

        @Override
        public Manager getManager() {
            return manager;
        }

        @Override
        public void setManager(Manager manager) {
            this.manager = manager;
        }

        @Override
        public int getSize() {
            return sessions.size();
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
            // NO-OP
        }

        @Override
        public String[] keys() {
            return sessions.keySet().toArray(new String[0]);
        }

        @Override
        public Session load(String id) {
            return sessions.get(id);
        }

        @Override
        public void remove(String id) {
            sessions.remove(id);
        }

        @Override
        public void clear() {
            sessions.clear();
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            // NO-OP
        }

        @Override
        public void save(Session session) {
            sessions.put(session.getId(), session);
            savedIds.add(session.getId());
        }
    }
}

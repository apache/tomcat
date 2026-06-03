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
import jakarta.servlet.ServletException;
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

    private static final String TEST_SESSION_ID = "TEST-SESSION-ID";
    private static final long TEST_TIMEOUT_MS = 1000;

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
        CountDownLatch asyncStarted = new CountDownLatch(1);
        CountDownLatch allowAsyncComplete = new CountDownLatch(1);

        Tomcat tomcat = getTomcatInstance();
        addSemaphoreTestServlets("async-block",
                new BlockingAsyncServlet(asyncStarted, allowAsyncComplete), "/async-block");
        tomcat.start();

        assertSemaphoreHeldUntilAsyncRequestCompletes("/async-block", asyncStarted, allowAsyncComplete,
                HttpServletResponse.SC_OK, TEST_SESSION_ID);
    }


    @Test
    public void testSemaphoreHeldAcrossAsyncDispatchDispatchComplete() throws Exception {
        CountDownLatch asyncStarted = new CountDownLatch(1);
        CountDownLatch allowAsyncComplete = new CountDownLatch(1);

        Tomcat tomcat = getTomcatInstance();
        addSemaphoreTestServlets("async-dispatch-dispatch-complete",
                new DispatchingAsyncServlet(2, asyncStarted, allowAsyncComplete, TerminalAction.COMPLETE), "/async-ddc");
        tomcat.start();

        assertSemaphoreHeldUntilAsyncRequestCompletes("/async-ddc", asyncStarted, allowAsyncComplete,
                HttpServletResponse.SC_OK, TEST_SESSION_ID);
    }


    @Test
    public void testSemaphoreHeldUntilAsyncError() throws Exception {
        CountDownLatch asyncStarted = new CountDownLatch(1);
        CountDownLatch allowAsyncError = new CountDownLatch(1);

        Tomcat tomcat = getTomcatInstance();
        StandardContext context = addSemaphoreTestServlets("async-error",
                new ErrorDispatchingAsyncServlet(asyncStarted, allowAsyncError), "/async-error");
        Tomcat.addServlet(context, "async-error-target", new ErrorServlet());
        context.addServletMappingDecoded("/async-error-target", "async-error-target");
        tomcat.start();

        assertSemaphoreHeldUntilAsyncRequestCompletes("/async-error", asyncStarted, allowAsyncError,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null);
    }


    @Test
    public void testSemaphoreHeldUntilAsyncTimeout() throws Exception {
        CountDownLatch asyncStarted = new CountDownLatch(1);

        Tomcat tomcat = getTomcatInstance();
        addSemaphoreTestServlets("async-timeout", new TimeoutAsyncServlet(asyncStarted), "/async-timeout");
        tomcat.start();

        assertSemaphoreHeldUntilAsyncRequestCompletes("/async-timeout", asyncStarted, null,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null);
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


    private StandardContext addSemaphoreTestServlets(String asyncServletName, HttpServlet asyncServlet,
            String asyncPath) {
        StandardContext context = (StandardContext) getProgrammaticRootContext();
        context.setDistributable(true);

        Tomcat.addServlet(context, "session", new SessionServlet());
        context.addServletMappingDecoded("/session", "session");

        Wrapper asyncWrapper = Tomcat.addServlet(context, asyncServletName, asyncServlet);
        asyncWrapper.setAsyncSupported(true);
        context.addServletMappingDecoded(asyncPath, asyncServletName);

        TesterStore store = new TesterStore();
        PersistentValve persistentValve = new PersistentValve();
        persistentValve.setSemaphoreBlockOnAcquire(false);
        configurePersistentManager(context, store, persistentValve);

        return context;
    }


    private Map<String,List<String>> cookieHeaders(String sessionId) {
        Map<String,List<String>> result = new HashMap<>();
        result.put("Cookie", List.of("JSESSIONID=" + sessionId));
        return result;
    }


    private void assertSemaphoreHeldUntilAsyncRequestCompletes(String path, CountDownLatch asyncStarted,
            CountDownLatch allowTerminalAction, int expectedStatus, String expectedResponseBody) throws Exception {
        Map<String,List<String>> requestHeaders = cookieHeaders(TEST_SESSION_ID);

        AsyncRequest asyncRequest = new AsyncRequest(path, requestHeaders);
        asyncRequest.start();

        Assert.assertTrue(asyncStarted.await(10, TimeUnit.SECONDS));

        assertSessionRequestRejected(requestHeaders);
        assertSessionRequestRejected(requestHeaders);

        if (allowTerminalAction != null) {
            allowTerminalAction.countDown();
        }

        asyncRequest.await();
        asyncRequest.assertResponse(expectedStatus, expectedResponseBody);
        assertSessionRequestSucceeds(requestHeaders);
    }


    private void assertSessionRequestRejected(Map<String,List<String>> requestHeaders) throws Exception {
        ByteChunk rejected = new ByteChunk();
        int status = getUrl("http://localhost:" + getPort() + "/session", rejected, requestHeaders, null);
        Assert.assertEquals(HttpServletResponse.SC_TOO_MANY_REQUESTS, status);
    }


    private void assertSessionRequestSucceeds(Map<String,List<String>> requestHeaders) throws Exception {
        ByteChunk success = new ByteChunk();
        int status = getUrl("http://localhost:" + getPort() + "/session", success, requestHeaders, null);
        Assert.assertEquals(HttpServletResponse.SC_OK, status);
        Assert.assertFalse(success.isNull());
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


    private static class DispatchingAsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final int dispatchCount;
        private final CountDownLatch asyncStarted;
        private final CountDownLatch allowTerminalAction;
        private final TerminalAction terminalAction;

        private DispatchingAsyncServlet(int dispatchCount, CountDownLatch asyncStarted, CountDownLatch allowTerminalAction,
                TerminalAction terminalAction) {
            this.dispatchCount = dispatchCount;
            this.asyncStarted = asyncStarted;
            this.allowTerminalAction = allowTerminalAction;
            this.terminalAction = terminalAction;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            Integer dispatches = (Integer) request.getAttribute("dispatches");
            int dispatchesSoFar = dispatches == null ? 0 : dispatches.intValue();

            if (dispatchesSoFar < dispatchCount) {
                request.setAttribute("dispatches", Integer.valueOf(dispatchesSoFar + 1));
                AsyncContext asyncContext = request.startAsync();
                asyncContext.start(asyncContext::dispatch);
                return;
            }

            if (terminalAction == TerminalAction.COMPLETE) {
                String sessionId = request.getRequestedSessionId();
                AsyncContext asyncContext = request.startAsync();
                asyncContext.start(() -> {
                    asyncStarted.countDown();
                    try {
                        Assert.assertTrue(allowTerminalAction.await(10, TimeUnit.SECONDS));
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
    }


    private static class ErrorDispatchingAsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final CountDownLatch asyncStarted;
        private final CountDownLatch allowAsyncError;

        private ErrorDispatchingAsyncServlet(CountDownLatch asyncStarted, CountDownLatch allowAsyncError) {
            this.asyncStarted = asyncStarted;
            this.allowAsyncError = allowAsyncError;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            AsyncContext asyncContext = request.startAsync();
            asyncContext.start(() -> {
                asyncStarted.countDown();
                try {
                    Assert.assertTrue(allowAsyncError.await(10, TimeUnit.SECONDS));
                    asyncContext.dispatch("/async-error-target");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            });
        }
    }


    private static class TimeoutAsyncServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final CountDownLatch asyncStarted;

        private TimeoutAsyncServlet(CountDownLatch asyncStarted) {
            this.asyncStarted = asyncStarted;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            AsyncContext asyncContext = request.startAsync();
            asyncContext.setTimeout(TEST_TIMEOUT_MS);
            asyncStarted.countDown();
        }
    }


    private static class ErrorServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
            throw new ServletException("Async test error");
        }
    }


    private class AsyncRequest {

        private final String path;
        private final Map<String,List<String>> requestHeaders;
        private final ByteChunk responseBody = new ByteChunk();
        private final AtomicInteger responseCode = new AtomicInteger(-1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final Thread thread;

        private AsyncRequest(String path, Map<String,List<String>> requestHeaders) {
            this.path = path;
            this.requestHeaders = requestHeaders;
            this.thread = new Thread(this::doRequest);
        }

        private void start() {
            thread.start();
        }

        private void await() throws InterruptedException {
            thread.join(10000);
        }

        private void assertResponse(int expectedStatus, String expectedResponseBody) {
            Assert.assertNull(failure.get());
            Assert.assertEquals(expectedStatus, responseCode.get());
            if (expectedResponseBody != null) {
                Assert.assertEquals(expectedResponseBody, responseBody.toString());
            }
        }

        private void doRequest() {
            try {
                responseCode.set(getUrl("http://localhost:" + getPort() + path, responseBody, requestHeaders, null));
            } catch (Throwable t) {
                failure.set(t);
            }
        }
    }


    private enum TerminalAction {
        COMPLETE
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

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
package org.apache.coyote;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.coyote.http11.Http11Processor;
import org.apache.tomcat.util.net.SocketEvent;

/**
 * Tests the dispatching of the asynchronous timeout by the timeout task of the protocol, which runs
 * {@link AbstractProcessor#timeoutAsync(long)} once per second for every processor that is waiting.
 */
public class TestAbstractProcessorAsyncTimeout {

    private TestProcessor processor;
    private TestAsyncContextCallback asyncCtxt;

    @Before
    public void setupTest() {
        processor = new TestProcessor();
        asyncCtxt = new TestAsyncContextCallback();

        processor.action(ActionCode.ASYNC_START, asyncCtxt);
        processor.action(ActionCode.ASYNC_POST_PROCESS, null);
        processor.setAsyncTimeout(1000);
    }

    @Test
    public void testTimeoutIsDispatchedUntilItIsProcessed() {
        // A dispatched event is not necessarily processed. It may be discarded, for example if another thread is
        // processing the socket when the event is dispatched, in which case the timeout remains in place so that the
        // next run of the timeout task dispatches it again.
        long asyncStart = processor.asyncStateMachine.getLastAsyncStart();

        processor.timeoutAsync(asyncStart + 2000);

        Assert.assertEquals("The timeout should have been dispatched", List.of(SocketEvent.TIMEOUT),
                processor.getDispatchedEvents());
        Assert.assertTrue("The timeout must remain in place until it has been processed",
                processor.getAsyncTimeout() > 0);

        // The event was not processed, so this run of the timeout task has to dispatch it again.
        processor.timeoutAsync(asyncStart + 4000);

        Assert.assertEquals("The timeout should have been dispatched again",
                List.of(SocketEvent.TIMEOUT, SocketEvent.TIMEOUT), processor.getDispatchedEvents());

        // Processing the event removes the timeout and marks the generation as handled, so any event that is already
        // queued for it is ignored.
        AtomicBoolean result = new AtomicBoolean();
        processor.action(ActionCode.ASYNC_TIMEOUT, result);
        Assert.assertTrue("The timeout should have been processed", result.get());
        Assert.assertEquals(-1, processor.getAsyncTimeout());
        Assert.assertFalse("A second event for the same timeout should be stale",
                processor.checkAsyncTimeoutGeneration());

        // Once processed, the timeout task must not dispatch it again.
        processor.timeoutAsync(asyncStart + 6000);

        Assert.assertEquals("The timeout must not be dispatched once it has been processed",
                List.of(SocketEvent.TIMEOUT, SocketEvent.TIMEOUT), processor.getDispatchedEvents());
    }

    private static class TestProcessor extends Http11Processor {

        private final List<SocketEvent> dispatchedEvents = new ArrayList<>();

        TestProcessor() {
            super(new Http11NioProtocol(), null);
        }

        @Override
        protected void processSocketEvent(SocketEvent event, boolean dispatch) {
            // Log the event rather than passing it on to the socket. Tests can then determine which events would have
            // been dispatched without requiring a socket, an endpoint or an executor.
            dispatchedEvents.add(event);
        }

        List<SocketEvent> getDispatchedEvents() {
            return dispatchedEvents;
        }
    }

    private static class TestAsyncContextCallback implements AsyncContextCallback {

        @Override
        public void fireOnComplete() {
            // NO-OP
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void incrementInProgressAsyncCount() {
            // NO-OP
        }

        @Override
        public void decrementInProgressAsyncCount() {
            // NO-OP
        }
    }
}

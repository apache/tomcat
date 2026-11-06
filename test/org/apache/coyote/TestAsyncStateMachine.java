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
package org.apache.coyote;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.coyote.http11.Http11Processor;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketEvent;

/**
 * Tests the handling of the async state machine when the connection is in an error state at the point the container
 * thread that made the initial service() call exits. The tests drive the state machine directly through a processor so
 * the interleavings are deterministic and no sockets, threads or timing are required.
 */
public class TestAsyncStateMachine {

    private TestProcessor processor;
    private TestAsyncContextCallback asyncCtxt;

    @Before
    public void setupTest() {
        processor = new TestProcessor();
        asyncCtxt = new TestAsyncContextCallback();

        // The initial service() call runs on the container (request) thread
        processor.getRequest().setRequestThread();

        // ServletRequest.startAsync(). The caller increments the in-progress count when starting async.
        asyncCtxt.incrementInProgressAsyncCount();
        processor.action(ActionCode.ASYNC_START, asyncCtxt);
    }


    /*
     * BZ 70290: The client drops the connection during the initial service() call (STARTING -> MUST_ERROR) and
     * AsyncListener.onError() then calls AsyncContext.dispatch() (MUST_ERROR -> MUST_DISPATCH). The dispatch can no
     * longer be performed so onComplete() must be called and the in-progress count must be decremented. Previously the
     * request was left in MUST_DISPATCH, onComplete() was never called and the count leaked.
     */
    @Test
    public void testBz70290DispatchFromOnErrorAfterConnectionClosed() throws IOException {
        processor.setErrorState(ErrorState.CLOSE_CONNECTION_NOW, new IOException());
        Assert.assertTrue(isAsyncError());

        processor.action(ActionCode.ASYNC_DISPATCH, null);
        Assert.assertTrue(isAsyncDispatching());

        SocketState state = processor.asyncPostProcess();

        Assert.assertEquals(SocketState.ASYNC_END, state);
        assertCompleted();
    }


    /*
     * BZ 70290: As above but the error state is CLOSE_NOW rather than CLOSE_CONNECTION_NOW. Both prevent further IO so
     * both must balance the in-progress count.
     */
    @Test
    public void testBz70290DispatchFromOnErrorAfterStreamReset() throws IOException {
        processor.setErrorState(ErrorState.CLOSE_NOW, new IOException());

        processor.action(ActionCode.ASYNC_DISPATCH, null);

        SocketState state = processor.asyncPostProcess();

        Assert.assertEquals(SocketState.ASYNC_END, state);
        assertCompleted();
    }


    /*
     * BZ 70290: onComplete() must only be called once and the in-progress count must only be decremented once, even if
     * the post processing is triggered more than once (CoyoteAdapter triggers it early when the connection is in an
     * error state and the processor triggers it again on the normal path).
     */
    @Test
    public void testBz70290PostProcessTwiceDoesNotDoubleComplete() throws IOException {
        processor.setErrorState(ErrorState.CLOSE_CONNECTION_NOW, new IOException());
        processor.action(ActionCode.ASYNC_DISPATCH, null);

        processor.asyncPostProcess();
        assertCompleted();

        // State is now DISPATCHED. A second call is not valid and must not trigger a second completion.
        try {
            processor.asyncPostProcess();
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected
        }
        assertCompleted();
    }


    /*
     * BZ 70290: Control. With no error the dispatch must still be performed: the state machine moves to DISPATCHING,
     * onComplete() is not called yet and the in-progress count is only decremented once the dispatch has completed.
     */
    @Test
    public void testBz70290DispatchWithoutErrorIsStillPerformed() throws IOException {
        processor.action(ActionCode.ASYNC_DISPATCH, null);

        SocketState state = processor.asyncPostProcess();

        Assert.assertEquals(SocketState.ASYNC_END, state);
        Assert.assertTrue("Dispatch must still be pending", isAsyncDispatching());
        Assert.assertEquals(0, asyncCtxt.getCompleteCount());
        Assert.assertEquals(0, asyncCtxt.getDecrementCount());

        // The dispatch is processed
        processor.action(ActionCode.ASYNC_DISPATCHED, null);

        Assert.assertFalse(isAsync());
        Assert.assertEquals(1, asyncCtxt.getDecrementCount());
    }


    /*
     * BZ 70290: An error state that still permits IO (CLOSE_CLEAN) still results in the connection being closed, so the
     * pending dispatch can not be performed. onComplete() must be called and the in-progress count must be balanced.
     */
    @Test
    public void testBz70290DispatchWithIoAllowedErrorStateIsCompleted() throws IOException {
        processor.setErrorState(ErrorState.CLOSE_CLEAN, null);

        processor.action(ActionCode.ASYNC_DISPATCH, null);

        SocketState state = processor.asyncPostProcess();

        Assert.assertEquals(SocketState.ASYNC_END, state);
        assertCompleted();
    }


    /*
     * BZ 70290: Control. The equivalent complete() handling (MUST_COMPLETE) in an error state already balanced the
     * count. Confirm the dispatch and complete cases behave consistently.
     */
    @Test
    public void testBz70290CompleteAfterConnectionClosedBalancesCount() throws IOException {
        processor.setErrorState(ErrorState.CLOSE_CONNECTION_NOW, new IOException());

        processor.action(ActionCode.ASYNC_COMPLETE, null);

        SocketState state = processor.asyncPostProcess();

        Assert.assertEquals(SocketState.ASYNC_END, state);
        assertCompleted();
    }


    /*
     * BZ 70290: Parity with the dispatch case. complete() after CLOSE_NOW (MUST_ERROR -> MUST_COMPLETE or
     * STARTING -> MUST_COMPLETE) must call onComplete() once and balance the in-progress count.
     */
    @Test
    public void testBz70290CompleteAfterCloseNowBalancesCount() throws IOException {
        processor.setErrorState(ErrorState.CLOSE_NOW, new IOException());

        processor.action(ActionCode.ASYNC_COMPLETE, null);

        SocketState state = processor.asyncPostProcess();

        Assert.assertEquals(SocketState.ASYNC_END, state);
        assertCompleted();
    }


    /*
     * BZ 70290: Parity with the dispatch case. complete() after CLOSE_CLEAN (an error state that still permits IO) must
     * call onComplete() once and balance the in-progress count.
     */
    @Test
    public void testBz70290CompleteAfterCloseCleanBalancesCount() throws IOException {
        processor.setErrorState(ErrorState.CLOSE_CLEAN, null);

        processor.action(ActionCode.ASYNC_COMPLETE, null);

        SocketState state = processor.asyncPostProcess();

        Assert.assertEquals(SocketState.ASYNC_END, state);
        assertCompleted();
    }


    private void assertCompleted() {
        Assert.assertFalse("Async processing must have ended", isAsync());
        Assert.assertEquals("onComplete() must be called exactly once", 1, asyncCtxt.getCompleteCount());
        Assert.assertEquals("In-progress async count must be decremented exactly once", 1,
                asyncCtxt.getDecrementCount());
        Assert.assertEquals("In-progress async count must be balanced", 0, asyncCtxt.getInProgress());
    }


    private boolean isAsync() {
        AtomicBoolean result = new AtomicBoolean();
        processor.action(ActionCode.ASYNC_IS_ASYNC, result);
        return result.get();
    }


    private boolean isAsyncDispatching() {
        AtomicBoolean result = new AtomicBoolean();
        processor.action(ActionCode.ASYNC_IS_DISPATCHING, result);
        return result.get();
    }


    private boolean isAsyncError() {
        AtomicBoolean result = new AtomicBoolean();
        processor.action(ActionCode.ASYNC_IS_ERROR, result);
        return result.get();
    }


    private static class TestProcessor extends Http11Processor {

        TestProcessor() {
            super(new Http11NioProtocol(), null);
        }

        @Override
        protected void processSocketEvent(SocketEvent event, boolean dispatch) {
            // NO-OP. Tests drive the state machine directly rather than via a socket.
        }

        @Override
        protected boolean flushBufferedWrite() throws IOException {
            // There is no socket so there is never any buffered data to flush
            return false;
        }

        @Override
        protected void setErrorState(ErrorState errorState, Throwable t) {
            super.setErrorState(errorState, t);
        }
    }


    private static class TestAsyncContextCallback implements AsyncContextCallback {

        private int inProgress;
        private int decrementCount;
        private int completeCount;

        @Override
        public void fireOnComplete() {
            completeCount++;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void incrementInProgressAsyncCount() {
            inProgress++;
        }

        @Override
        public void decrementInProgressAsyncCount() {
            inProgress--;
            decrementCount++;
        }

        int getInProgress() {
            return inProgress;
        }

        int getDecrementCount() {
            return decrementCount;
        }

        int getCompleteCount() {
            return completeCount;
        }
    }
}

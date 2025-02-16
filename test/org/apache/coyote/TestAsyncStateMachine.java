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

import jakarta.servlet.ServletConnection;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestAsyncStateMachine {

    @Test
    public void testAsyncPostProcessWithAsyncStarting() throws IOException {
        // given
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(new FakeAbstractProcessor());
        asyncStateMachine.asyncStart(null); // STARTING

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.LONG, actual);
        Assert.assertTrue(asyncStateMachine.isAsyncStarted());
    }

    @Test
    public void testAsyncPostProcessWithAsyncStarted() throws IOException {
        // given
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(new FakeAbstractProcessor());
        asyncStateMachine.asyncStart(null); // STARTING
        asyncStateMachine.asyncPostProcess(); // STARTED

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.LONG, actual);
    }

    @Test
    public void testAsyncPostProcessWithAsyncReadWriteOp() throws IOException {
        // given
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(new FakeAbstractProcessor());
        asyncStateMachine.asyncStart(null); // STARTING
        asyncStateMachine.asyncPostProcess(); // STARTED
        asyncStateMachine.asyncOperation(); // READ_WRITE_OP

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.LONG, actual);
        Assert.assertTrue(asyncStateMachine.isAsyncStarted());
    }

    @Test
    public void testAsyncPostProcessWithAsyncCompletePending() throws IOException {
        // given
        FakeAbstractProcessor processor = new FakeAbstractProcessor();
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(processor);
        asyncStateMachine.asyncStart(null); // STARTING
        asyncStateMachine.asyncPostProcess(); // STARTED
        asyncStateMachine.asyncOperation(); // READ_WRITE_OP
        asyncStateMachine.asyncComplete(); // COMPLETE_PENDING

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.ASYNC_END, actual);
        Assert.assertTrue(asyncStateMachine.isCompleting());
    }

    @Test
    public void testAsyncPostProcessWithAsyncMustCompleteAndIoAllowed() throws IOException {
        // given
        FakeAbstractProcessor processor = new FakeAbstractProcessor(true, true);
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(processor);
        asyncStateMachine.asyncStart(null); // STARTING
        asyncStateMachine.asyncError(); // MUST_ERROR
        asyncStateMachine.asyncComplete(); // MUST_COMPLETE

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.LONG, actual);
    }

    @Test
    public void testAsyncPostProcessWithAsyncMustCompleteAndIoNotAllowed() throws IOException {
        // given
        FakeAbstractProcessor processor = new FakeAbstractProcessor(false, false);
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(processor);
        asyncStateMachine.asyncStart(new FakeAsyncContextCallback()); // STARTING
        asyncStateMachine.asyncError(); // MUST_ERROR
        asyncStateMachine.asyncComplete(); // MUST_COMPLETE

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.ASYNC_END, actual);
        Assert.assertTrue(asyncStateMachine.isAsyncDispatched());
    }

    @Test
    public void testAsyncPostProcessWithAsyncDispatchPending() throws IOException {
        FakeAbstractProcessor processor = new FakeAbstractProcessor(false, false);
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(processor);
        asyncStateMachine.asyncStart(new FakeAsyncContextCallback()); //STARTING
        processor.setNullRequest(true);
        asyncStateMachine.asyncDispatch(); //DISPATCHING_PENDING
        processor.setNullRequest(false);

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.ASYNC_END, actual);
        Assert.assertTrue(asyncStateMachine.isAsyncDispatching());
    }

    @Test
    public void testAsyncPostProcessWithAsyncCompletingAndIOAllowed() throws IOException {
        FakeAbstractProcessor processor = new FakeAbstractProcessor(true, true);
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(processor);
        asyncStateMachine.asyncStart(new FakeAsyncContextCallback()); // STARTING
        asyncStateMachine.asyncPostProcess(); // STARTED
        asyncStateMachine.asyncOperation(); // READ_WRITE_OP
        asyncStateMachine.asyncComplete(); // COMPLETE_PENDING
        asyncStateMachine.asyncPostProcess(); // COMPLETING

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.LONG, actual);
    }

    @Test
    public void testAsyncPostProcessWithAsyncCompletingAndIONotAllowed() throws IOException {
        FakeAbstractProcessor processor = new FakeAbstractProcessor(false, false);
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(processor);
        asyncStateMachine.asyncStart(new FakeAsyncContextCallback()); // STARTING
        asyncStateMachine.asyncPostProcess(); // STARTED
        asyncStateMachine.asyncOperation(); // READ_WRITE_OP
        asyncStateMachine.asyncComplete(); // COMPLETE_PENDING
        asyncStateMachine.asyncPostProcess(); // COMPLETING

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.ASYNC_END, actual);
        Assert.assertTrue(asyncStateMachine.isAsyncDispatched());
    }

    @Test
    public void testAsyncPostProcessWithAsyncDispatchingAndIOAllowed() throws IOException {
        FakeAbstractProcessor processor = new FakeAbstractProcessor(true, true);
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(processor);
        asyncStateMachine.asyncStart(new FakeAsyncContextCallback()); // STARTING
        processor.setNullRequest(true);
        asyncStateMachine.asyncDispatch(); // DISPATCHING_PENDING
        processor.setNullRequest(false);
        asyncStateMachine.asyncPostProcess(); // DISPATCHING

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.LONG, actual);
    }

    @Test
    public void testAsyncPostProcessWithAsyncDispatchingAndIONotAllowed() throws IOException {
        FakeAbstractProcessor processor = new FakeAbstractProcessor(false, false);
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(processor);
        asyncStateMachine.asyncStart(new FakeAsyncContextCallback()); // STARTING
        processor.setNullRequest(true);
        asyncStateMachine.asyncDispatch(); // DISPATCH_PENDING
        processor.setNullRequest(false);
        asyncStateMachine.asyncPostProcess(); // DISPATCHING

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.ASYNC_END, actual);
        Assert.assertTrue(asyncStateMachine.isAsyncDispatched());
    }

    @Test
    public void testAsyncPostProcessWithAsyncMustDispatch() throws IOException {
        FakeAbstractProcessor processor = new FakeAbstractProcessor(false, false);
        AsyncStateMachine asyncStateMachine = new AsyncStateMachine(processor);
        asyncStateMachine.asyncStart(new FakeAsyncContextCallback()); // STARTING
        asyncStateMachine.asyncError(); // MUST_ERROR
        asyncStateMachine.asyncDispatch(); //MUST_DISPATCH

        // when
        AbstractEndpoint.Handler.SocketState actual = asyncStateMachine.asyncPostProcess();

        // then
        Assert.assertEquals(AbstractEndpoint.Handler.SocketState.ASYNC_END, actual);
        Assert.assertTrue(asyncStateMachine.isAsyncDispatching());
    }

    public static class FakeAsyncContextCallback implements AsyncContextCallback {

        @Override
        public void fireOnComplete() {
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void incrementInProgressAsyncCount() {

        }

        @Override
        public void decrementInProgressAsyncCount() {

        }
    }

    public static class FakeAbstractProcessor extends AbstractProcessor {

        boolean isFlushBufferedWrite;
        boolean isIoAllowedErrorState;
        boolean isNullRequest;

        public FakeAbstractProcessor(boolean isFlushBufferedWrite, boolean isIoAllowedErrorState) {
            super(null);
            this.isFlushBufferedWrite = isFlushBufferedWrite;
            this.isIoAllowedErrorState = isIoAllowedErrorState;
        }

        public FakeAbstractProcessor() {
            super(null);
        }

        @Override
        public Request getRequest() {
            if (isNullRequest) {
                return null;
            }
            return super.getRequest();
        }

        public void setNullRequest(boolean nullRequest) {
            isNullRequest = nullRequest;
        }

        @Override
        protected ErrorState getErrorState() {
            if (isIoAllowedErrorState) {
                return ErrorState.NONE;
            }
            return ErrorState.CLOSE_NOW;
        }

        protected void setIoAllowedErrorState(boolean value) {
            this.isIoAllowedErrorState = value;
        }

        @Override
        protected void prepareResponse() throws IOException {

        }

        @Override
        protected void finishResponse() throws IOException {

        }

        @Override
        protected void ack(ContinueResponseTiming continueResponseTiming) {

        }

        @Override
        protected void earlyHints() throws IOException {

        }

        @Override
        protected void flush() throws IOException {

        }

        @Override
        protected int available(boolean doRead) {
            return 0;
        }

        @Override
        protected void setRequestBody(ByteChunk body) {

        }

        @Override
        protected void setSwallowResponse() {

        }

        @Override
        protected void disableSwallowRequest() {

        }

        @Override
        protected boolean isRequestBodyFullyRead() {
            return false;
        }

        @Override
        protected void registerReadInterest() {

        }

        @Override
        protected boolean isReadyForWrite() {
            return false;
        }

        @Override
        protected boolean isTrailerFieldsReady() {
            return false;
        }

        @Override
        protected ServletConnection getServletConnection() {
            return null;
        }

        @Override
        protected boolean flushBufferedWrite() throws IOException {
            return isFlushBufferedWrite;
        }

        protected void setFlushBufferedWrite(boolean value) {
            this.isFlushBufferedWrite = value;
        }

        @Override
        protected AbstractEndpoint.Handler.SocketState dispatchEndRequest() throws IOException {
            return null;
        }

        @Override
        protected AbstractEndpoint.Handler.SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException {
            return null;
        }

        @Override
        protected Log getLog() {
            return null;
        }

        @Override
        public void pause() {

        }
    }
}


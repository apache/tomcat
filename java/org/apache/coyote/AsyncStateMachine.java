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

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.PrivilegedGetTccl;
import org.apache.tomcat.util.security.PrivilegedSetTccl;

/**
 * Manages the state transitions for async requests.
 *
 * <pre>
 * The internal states that are used are:
 * DISPATCHED    - Standard request. Not in Async mode.
 * STARTING      - ServletRequest.startAsync() has been called but the
 *                 request in which that call was made has not finished
 *                 processing.
 * STARTED       - ServletRequest.startAsync() has been called and the
 *                 request in which that call was made has finished
 *                 processing.
 * READ_WRITE_OP - Performing an asynchronous read or write.
 * MUST_COMPLETE - complete() has been called before the request in which
 *                 ServletRequest.startAsync() has finished. As soon as that
 *                 request finishes, the complete() will be processed.
 * COMPLETING    - The call to complete() was made once the request was in
 *                 the STARTED state. May or may not be triggered by a
 *                 container thread - depends if start(Runnable) was used
 * TIMING_OUT    - The async request has timed out and is waiting for a call
 *                 to complete(). If that isn't made, the error state will
 *                 entered.
 * MUST_DISPATCH - dispatch() has been called before the request in which
 *                 ServletRequest.startAsync() has finished. As soon as that
 *                 request finishes, the dispatch() will be processed.
 * DISPATCHING   - The dispatch is being processed.
 * ERROR         - Something went wrong.
 *
 * |-----------------»--------------|
 * |                               \|/
 * |   |----------«---------------ERROR---------------------------«-------------------------------|
 * |   |      complete()         /|\ | \                                                          |
 * |   |                          |  |  \---------------|                                         |
 * |   |                          |  |                  |dispatch()                               |
 * |   |                          |  |postProcess()    \|/                                        |
 * |   |                   error()|  |                  |                                         |
 * |   |                          |  |  |--|timeout()   |                                         |
 * |   |           postProcess()  | \|/ | \|/           |         auto                            |
 * |   |         |---------------»DISPATCHED«---------- | --------------COMPLETING«-----|         |
 * |   |         |               /|\  |                 |                 | /|\         |         |
 * |   |         |    |---»-------|   |                 |                 |--|          |         |
 * |   |         ^    |               |startAsync()     |               timeout()       |         |
 * |   |         |    |               |                 |                               |         |
 * |  \|/        |    |  complete()  \|/  postProcess() |                               |         |
 * | MUST_COMPLETE-«- | ----«------STARTING--»--------- | ------------|                 ^         |
 * |  /|\    /|\      |               |                 |             |      complete() |         |
 * |   |      |       |               |                 |             |     /-----------|         |
 * |   |      |       ^               |dispatch()       |             |    /                      |
 * |   |      |       |               |                 |             |   /                       |
 * |   |      |       |              \|/                |            \|/ /    postProcess()       |
 * |   |      |       |----«----MUST_DISPATCH-----«-----|          STARTED«---------«---------|   |
 * |   |      |       |  auto        /|\                          / |   |                     |   |
 * |   |      |       |               |                          /  |   |                     ^   |
 * ^   |      ^       |               |                         /   |   |asyncOperation()     |   |
 * |   |      |       ^               |                        /    |   |                     |   |
 * |   |      |       |               |         |-------------/     |   |--READ_WRITE_OP--»---|   |
 * |   |      |       |               |         |    dispatch()     |            |  |  |          |
 * |   |      |       |               |         |               auto|            |  |  |   error()|
 * |   |      |       | auto          |        \|/                  |  dispatch()|  |  |-»--------|
 * |   |      |       |---«---------- | ---DISPATCHING«-----«------ | ------«----|  |
 * |   |      |                       |                             |               |
 * |   |      |                       |       dispatch()           \|/              |
 * |   |      |                       |-----------«-----------TIMING_OUT            |
 * |   |      |                                                 |   |               |
 * |   |      |-------«----------------------------------«------|   |               |
 * |   |                          complete()                        |               |
 * |   |                                                            |               |
 * |«- | ----«-------------------«-------------------------------«--|               |
 *     |                           error()                                          |
 *     |                                                  complete()                |
 *     |----------------------------------------------------------------------------|
 * </pre>
 */
public class AsyncStateMachine {

    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    private static enum AsyncState {
        DISPATCHED   (false, false, false, false, false),
        STARTING     (true,  true,  false, false, true),
        STARTED      (true,  true,  false, false, false),
        MUST_COMPLETE(true,  true,  true,  false, false),
        COMPLETING   (true,  false, true,  false, false),
        TIMING_OUT   (true,  false, false, false, false),
        MUST_DISPATCH(true,  true,  false, true,  true),
        DISPATCHING  (true,  false, false, true,  false),
        READ_WRITE_OP(true,  true,  false, false, true),
        ERROR        (true,  false, false, false, false);

        private final boolean isAsync;
        private final boolean isStarted;
        private final boolean isCompleting;
        private final boolean isDispatching;
        private final boolean pauseNonContainerThread;

        private AsyncState(boolean isAsync, boolean isStarted, boolean isCompleting,
                boolean isDispatching, boolean pauseNonContainerThread) {
            this.isAsync = isAsync;
            this.isStarted = isStarted;
            this.isCompleting = isCompleting;
            this.isDispatching = isDispatching;
            this.pauseNonContainerThread = pauseNonContainerThread;
        }

        public boolean isAsync() {
            return isAsync;
        }

        public boolean isStarted() {
            return isStarted;
        }

        public boolean isDispatching() {
            return isDispatching;
        }

        public boolean isCompleting() {
            return isCompleting;
        }

        public boolean getPauseNonContainerThread() {
            return pauseNonContainerThread;
        }
    }


    private volatile AsyncState state = AsyncState.DISPATCHED;
    // Need this to fire listener on complete
    private AsyncContextCallback asyncCtxt = null;
    private final AbstractProcessor processor;


    public AsyncStateMachine(AbstractProcessor processor) {
        this.processor = processor;
    }


    public boolean isAsync() {
        return state.isAsync();
    }

    public boolean isAsyncDispatching() {
        return state.isDispatching();
    }

    public boolean isAsyncStarted() {
        return state.isStarted();
    }

    public boolean isAsyncTimingOut() {
        return state == AsyncState.TIMING_OUT;
    }

    public boolean isAsyncError() {
        return state == AsyncState.ERROR;
    }

    public boolean isCompleting() {
        return state.isCompleting();
    }

    public synchronized void asyncStart(AsyncContextCallback asyncCtxt) {
        if (state == AsyncState.DISPATCHED) {
            state = AsyncState.STARTING;
            this.asyncCtxt = asyncCtxt;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncStart()", state));
        }
    }

    public synchronized void asyncOperation() {
        if (state==AsyncState.STARTED) {
            state = AsyncState.READ_WRITE_OP;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncOperation()", state));
        }
    }

    /*
     * Async has been processed. Whether or not to enter a long poll depends on
     * current state. For example, as per SRV.2.3.3.3 can now process calls to
     * complete() or dispatch().
     */
    public synchronized SocketState asyncPostProcess() {

        // Unpause any non-container threads that may be waiting for this
        // container thread to complete this method. Note because of the syncs
        // those non-container threads won't start back up until until this
        // method exits.
        notifyAll();

        if (state == AsyncState.STARTING || state == AsyncState.READ_WRITE_OP) {
            state = AsyncState.STARTED;
            return SocketState.LONG;
        } else if (state == AsyncState.MUST_COMPLETE) {
            asyncCtxt.fireOnComplete();
            state = AsyncState.DISPATCHED;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.COMPLETING) {
            asyncCtxt.fireOnComplete();
            state = AsyncState.DISPATCHED;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.MUST_DISPATCH) {
            state = AsyncState.DISPATCHING;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.DISPATCHING) {
            state = AsyncState.DISPATCHED;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.STARTED) {
            // This can occur if an async listener does a dispatch to an async
            // servlet during onTimeout
            return SocketState.LONG;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncPostProcess()", state));
        }
    }


    public synchronized boolean asyncComplete() {
        pauseNonContainerThread();
        boolean doComplete = false;
        if (state == AsyncState.STARTING) {
            state = AsyncState.MUST_COMPLETE;
        } else if (state == AsyncState.STARTED) {
            state = AsyncState.COMPLETING;
            doComplete = true;
        } else if (state == AsyncState.TIMING_OUT ||
                state == AsyncState.ERROR) {
            state = AsyncState.MUST_COMPLETE;
        } else if (state == AsyncState.READ_WRITE_OP) {
            clearNonBlockingListeners();
            state = AsyncState.MUST_COMPLETE;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncComplete()", state));
        }
        return doComplete;
    }


    public synchronized boolean asyncTimeout() {
        if (state == AsyncState.STARTED) {
            state = AsyncState.TIMING_OUT;
            return true;
        } else if (state == AsyncState.COMPLETING ||
                state == AsyncState.DISPATCHING ||
                state == AsyncState.DISPATCHED) {
            // NOOP - App called complete() or dispatch() between the the
            // timeout firing and execution reaching this point
            return false;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncTimeout()", state));
        }
    }


    public synchronized boolean asyncDispatch() {
        pauseNonContainerThread();
        boolean doDispatch = false;
        if (state == AsyncState.STARTING ||
                state == AsyncState.TIMING_OUT ||
                state == AsyncState.ERROR) {
            // In these three cases processing is on a container thread so no
            // need to transfer processing to a new container thread
            state = AsyncState.MUST_DISPATCH;
        } else if (state == AsyncState.STARTED) {
            state = AsyncState.DISPATCHING;
            // A dispatch is always required.
            // If on a non-container thread, need to get back onto a container
            // thread to complete the processing.
            // If on a container thread the current request/response are not the
            // request/response associated with the AsyncContext so need a new
            // container thread to process the different request/response.
            doDispatch = true;
        } else if (state == AsyncState.READ_WRITE_OP) {
            state = AsyncState.DISPATCHING;
            // If on a container thread then the socket will be added to the
            // poller poller when the thread exits the
            // AbstractConnectionHandler.process() method so don't do a dispatch
            // here which would add it to the poller a second time.
            if (!ContainerThreadMarker.isContainerThread()) {
                doDispatch = true;
            }
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncDispatch()", state));
        }
        return doDispatch;
    }


    public synchronized void asyncDispatched() {
        if (state == AsyncState.DISPATCHING ||
                state == AsyncState.MUST_DISPATCH) {
            state = AsyncState.DISPATCHED;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncDispatched()", state));
        }
    }


    public synchronized void asyncError() {
        if (state == AsyncState.DISPATCHED ||
                state == AsyncState.TIMING_OUT ||
                state == AsyncState.READ_WRITE_OP) {
            clearNonBlockingListeners();
            state = AsyncState.ERROR;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncError()", state));
        }
    }

    public synchronized void asyncRun(Runnable runnable) {
        if (state == AsyncState.STARTING || state ==  AsyncState.STARTED ||
                state == AsyncState.READ_WRITE_OP) {
            // Execute the runnable using a container thread from the
            // Connector's thread pool. Use a wrapper to prevent a memory leak
            ClassLoader oldCL;
            if (Constants.IS_SECURITY_ENABLED) {
                PrivilegedAction<ClassLoader> pa = new PrivilegedGetTccl();
                oldCL = AccessController.doPrivileged(pa);
            } else {
                oldCL = Thread.currentThread().getContextClassLoader();
            }
            try {
                if (Constants.IS_SECURITY_ENABLED) {
                    PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                            this.getClass().getClassLoader());
                    AccessController.doPrivileged(pa);
                } else {
                    Thread.currentThread().setContextClassLoader(
                            this.getClass().getClassLoader());
                }

                processor.getExecutor().execute(runnable);
            } finally {
                if (Constants.IS_SECURITY_ENABLED) {
                    PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                            oldCL);
                    AccessController.doPrivileged(pa);
                } else {
                    Thread.currentThread().setContextClassLoader(oldCL);
                }
            }
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState",
                            "asyncRun()", state));
        }

    }


    public synchronized void recycle() {
        // Ensure in case of error that any non-container threads that have been
        // paused are unpaused.
        notifyAll();
        asyncCtxt = null;
        state = AsyncState.DISPATCHED;
    }


    private void clearNonBlockingListeners() {
        processor.getRequest().listener = null;
        processor.getRequest().getResponse().listener = null;
    }


    /*
     * startAsync() has been called but the container thread where this was
     * called has not completed processing. To avoid various race conditions -
     * including several related to error page handling - pause this
     * non-container thread until the container thread has finished processing.
     * The non-container thread will be paused until the container thread
     * completes asyncPostProcess().
     */
    private synchronized void pauseNonContainerThread() {
        while (!ContainerThreadMarker.isContainerThread() &&
                state.getPauseNonContainerThread()) {
            try {
                wait();
            } catch (InterruptedException e) {
                // TODO Log this?
            }
        }
    }
}

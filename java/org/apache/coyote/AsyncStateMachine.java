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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.res.StringManager;

/**
 * Manages the state transitions for async requests.
 *
 * <pre>
 * The internal states that are used are:
 * DISPATCHED       - Standard request. Not in Async mode.
 * STARTING         - ServletRequest.startAsync() has been called from
 *                    Servlet.service() but service() has not exited.
 * STARTED          - ServletRequest.startAsync() has been called from
 *                    Servlet.service() and service() has exited.
 * READ_WRITE_OP    - Performing an asynchronous read or write.
 * MUST_COMPLETE    - ServletRequest.startAsync() followed by complete() have
 *                    been called during a single Servlet.service() method. The
 *                    complete() will be processed as soon as Servlet.service()
 *                    exits.
 * COMPLETE_PENDING - ServletRequest.startAsync() has been called from
 *                    Servlet.service() but, before service() exited, complete()
 *                    was called from another thread. The complete() will
 *                    be processed as soon as Servlet.service() exits.
 * COMPLETING       - The call to complete() was made once the request was in
 *                    the STARTED state.
 * TIMING_OUT       - The async request has timed out and is waiting for a call
 *                    to complete() or dispatch(). If that isn't made, the error
 *                    state will be entered.
 * MUST_DISPATCH    - ServletRequest.startAsync() followed by dispatch() have
 *                    been called during a single Servlet.service() method. The
 *                    dispatch() will be processed as soon as Servlet.service()
 *                    exits.
 * DISPATCH_PENDING - ServletRequest.startAsync() has been called from
 *                    Servlet.service() but, before service() exited, dispatch()
 *                    was called from another thread. The dispatch() will
 *                    be processed as soon as Servlet.service() exits.
 * DISPATCHING      - The dispatch is being processed.
 * MUST_ERROR       - ServletRequest.startAsync() has been called from
 *                    Servlet.service() but, before service() exited, an I/O
 *                    error occurred on another thread. The container will
 *                    perform the necessary error handling when
 *                    Servlet.service() exits.
 * ERROR            - Something went wrong.
 *
 *
 * The valid state transitions are:
 *
 *                  post()                                        dispatched()
 *    |-------»------------------»---------|    |-------«-----------------------«-----|
 *    |                                    |    |                                     |
 *    |                                    |    |        post()                       |
 *    |               post()              \|/  \|/       dispatched()                 |
 *    |           |-----»----------------»DISPATCHED«-------------«-------------|     |
 *    |           |                          | /|\ |                            |     |
 *    |           |              startAsync()|  |--|timeout()                   |     |
 *    ^           |                          |                                  |     |
 *    |           |        complete()        |                  dispatch()      ^     |
 *    |           |   |--«---------------«-- | ---«--MUST_ERROR--»-----|        |     |
 *    |           |   |                      |         /|\             |        |     |
 *    |           ^   |                      |          |              |        |     |
 *    |           |   |                      |    /-----|error()       |        |     |
 *    |           |   |                      |   /                     |        ^     |
 *    |           |  \|/  ST-complete()     \|/ /   ST-dispatch()     \|/       |     |
 *    |    MUST_COMPLETE«--------«--------STARTING--------»---------»MUST_DISPATCH    |
 *    |                                    / | \                                      |
 *    |                                   /  |  \                                     |
 *    |                    OT-complete() /   |   \    OT-dispatch()                   |
 *    |   COMPLETE_PENDING«------«------/    |    \-------»---------»DISPATCH_PENDING |
 *    |        |      /|\                    |                       /|\ |            |
 *    |        |       |                     |                        |  |post()      |
 *    |        |       |OT-complete()        |           OT-dispatch()|  |            |
 *    |        |       |---------«-------«---|---«--\                 |  |            |
 *    |        |                             |       \                |  |            |
 *    |        |         /-------«-------«-- | --«---READ_WRITE--»----|  |            |
 *    |        |        / ST-complete()      |        /  /|\  \          |            |
 *    |        |       /                     | post()/   /     \         |            |
 *    |        |      /                      |      /   /       \        |            |
 *    |        |     /                       |     /   /         \       |            |
 *    |        |    /                        |    /   /           \      |            |
 *    |        |   /                         |   |   /             \     |            |
 *    |        |  /                          |   |  /  ST-dispatch()\    |            |
 *    |        |  |                          |   | |                 \   |            |
 *    |  post()|  |  timeout()         post()|   | |asyncOperation()  \  |  timeout() |
 *    |        |  |  |--|                    |   | |                  |  |    |--|    |
 *    |       \|/\|/\|/ |     complete()    \|/ \|/|   dispatch()    \|/\|/  \|/ |    |
 *    |--«-----COMPLETING«--------«----------STARTED--------»---------»DISPATCHING----|
 *            /|\  /|\                       | /|\ |                       /|\ /|\
 *             |    |                        |  |--|                        |   |
 *             |    |               timeout()|  post()                      |   |
 *             |    |                        |                              |   |
 *             |    |       complete()      \|/         dispatch()          |   |
 *             |    |------------«-------TIMING_OUT--------»----------------|   |
 *             |                                                                |
 *             |            complete()                     dispatch()           |
 *             |---------------«-----------ERROR--------------»-----------------|
 *
 *
 * Notes: * For clarity, the transitions to ERROR which are valid from every state apart from
 *          STARTING are not shown.
 *        * All transitions may happen on either the Servlet.service() thread (ST) or on any
 *          other thread (OT) unless explicitly marked.
 * </pre>
 */
class AsyncStateMachine {

    private static final Log log = LogFactory.getLog(AsyncStateMachine.class);
    private static final StringManager sm = StringManager.getManager(AsyncStateMachine.class);

    private enum AsyncState {
        DISPATCHED(false, false, false, false),
        STARTING(true, true, false, false),
        STARTED(true, true, false, false),
        MUST_COMPLETE(true, true, true, false),
        COMPLETE_PENDING(true, true, false, false),
        COMPLETING(true, false, true, false),
        TIMING_OUT(true, true, false, false),
        MUST_DISPATCH(true, true, false, true),
        DISPATCH_PENDING(true, true, false, false),
        DISPATCHING(true, false, false, true),
        READ_WRITE_OP(true, true, false, false),
        MUST_ERROR(true, true, false, false),
        ERROR(true, true, false, false);

        private final boolean isAsync;
        private final boolean isStarted;
        private final boolean isCompleting;
        private final boolean isDispatching;

        AsyncState(boolean isAsync, boolean isStarted, boolean isCompleting, boolean isDispatching) {
            this.isAsync = isAsync;
            this.isStarted = isStarted;
            this.isCompleting = isCompleting;
            this.isDispatching = isDispatching;
        }

        boolean isAsync() {
            return isAsync;
        }

        boolean isStarted() {
            return isStarted;
        }

        boolean isDispatching() {
            return isDispatching;
        }

        boolean isCompleting() {
            return isCompleting;
        }
    }


    private volatile AsyncState state = AsyncState.DISPATCHED;
    private volatile long lastAsyncStart = 0;
    /*
     * Tracks the current generation of async processing for this state machine. The generation is incremented every
     * time async processing is started. The primary purpose of this is to enable Tomcat to detect and prevent attempts
     * to process an event for a previous generation with the current generation as processing such an event usually
     * ends badly: e.g. CVE-2018-8037.
     */
    private final AtomicLong generation = new AtomicLong(0);
    /*
     * Error processing should only be triggered once per async generation. This field tracks whether the async
     * processing has entered the error state during this async cycle.
     *
     * Guarded by this
     */
    private boolean hasProcessedError = false;

    // Need this to fire listener on complete
    private AsyncContextCallback asyncCtxt = null;
    private final AbstractProcessor processor;


    AsyncStateMachine(AbstractProcessor processor) {
        this.processor = processor;
    }


    boolean isAsync() {
        return state.isAsync();
    }

    boolean isAsyncDispatching() {
        return state.isDispatching();
    }

    boolean isAsyncStarted() {
        return state.isStarted();
    }

    boolean isAsyncTimingOut() {
        return state == AsyncState.TIMING_OUT;
    }

    boolean isAsyncError() {
        return state == AsyncState.ERROR;
    }

    boolean isCompleting() {
        return state.isCompleting();
    }

    /**
     * Obtain the time that this connection last transitioned to async processing.
     *
     * @return The time (as returned by {@link System#currentTimeMillis()}) that this connection last transitioned to
     *             async
     */
    long getLastAsyncStart() {
        return lastAsyncStart;
    }

    long getCurrentGeneration() {
        return generation.get();
    }

    synchronized void asyncStart(AsyncContextCallback asyncCtxt) {
        if (state == AsyncState.DISPATCHED) {
            generation.incrementAndGet();
            updateState(AsyncState.STARTING);
            // Note: In this instance, caller is responsible for calling
            // asyncCtxt.incrementInProgressAsyncCount() as that allows simpler
            // error handling.
            this.asyncCtxt = asyncCtxt;
            lastAsyncStart = System.currentTimeMillis();
        } else {
            throw new IllegalStateException(sm.getString("asyncStateMachine.invalidAsyncState", "asyncStart()", state));
        }
    }

    synchronized void asyncOperation() {
        if (state == AsyncState.STARTED) {
            updateState(AsyncState.READ_WRITE_OP);
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState", "asyncOperation()", state));
        }
    }

    /*
     * Async has been processed. Whether or not to enter a long poll depends on current state. For example, as per
     * SRV.2.3.3.3 can now process calls to complete() or dispatch().
     */
    synchronized SocketState asyncPostProcess() throws IOException {
        if (state == AsyncState.COMPLETE_PENDING) {
            clearNonBlockingListeners();
            updateState(AsyncState.COMPLETING);
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.DISPATCH_PENDING) {
            clearNonBlockingListeners();
            updateState(AsyncState.DISPATCHING);
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.STARTING || state == AsyncState.READ_WRITE_OP) {
            updateState(AsyncState.STARTED);
            return SocketState.LONG;
        } else if (state == AsyncState.MUST_COMPLETE || state == AsyncState.COMPLETING) {
            if (processor.getErrorState().isIoAllowed() && processor.flushBufferedWrite()) {
                return SocketState.LONG;
            }
            asyncCtxt.fireOnComplete();
            updateState(AsyncState.DISPATCHED);
            asyncCtxt.decrementInProgressAsyncCount();
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.MUST_DISPATCH) {
            updateState(AsyncState.DISPATCHING);
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.DISPATCHING) {
            if (processor.getErrorState().isIoAllowed() && processor.flushBufferedWrite()) {
                return SocketState.LONG;
            }
            asyncCtxt.fireOnComplete();
            updateState(AsyncState.DISPATCHED);
            asyncCtxt.decrementInProgressAsyncCount();
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.STARTED) {
            // This can occur if an async listener does a dispatch to an async
            // servlet during onTimeout
            return SocketState.LONG;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState", "asyncPostProcess()", state));
        }
    }


    synchronized boolean asyncComplete() {
        Request request = processor.getRequest();
        if ((request == null || !request.isRequestThread()) &&
                (state == AsyncState.STARTING || state == AsyncState.READ_WRITE_OP)) {
            updateState(AsyncState.COMPLETE_PENDING);
            return false;
        }

        clearNonBlockingListeners();
        boolean triggerDispatch = false;
        if (state == AsyncState.STARTING || state == AsyncState.MUST_ERROR) {
            // Processing is on a container thread so no need to transfer
            // processing to a new container thread
            updateState(AsyncState.MUST_COMPLETE);
        } else if (state == AsyncState.STARTED) {
            updateState(AsyncState.COMPLETING);
            // A dispatch to a container thread is always required.
            // If on a non-container thread, need to get back onto a container
            // thread to complete the processing.
            // If on a container thread the current request/response are not the
            // request/response associated with the AsyncContext so need a new
            // container thread to process the different request/response.
            triggerDispatch = true;
        } else if (state == AsyncState.READ_WRITE_OP || state == AsyncState.TIMING_OUT || state == AsyncState.ERROR) {
            // Read/write operations can happen on or off a container thread but
            // while in this state the call to listener that triggers the
            // read/write will be in progress on a container thread.
            // Processing of timeouts and errors can happen on or off a
            // container thread (on is much more likely) but while in this state
            // the call that triggers the timeout will be in progress on a
            // container thread.
            // The socket will be added to the poller when the container thread
            // exits the AbstractConnectionHandler.process() method so don't do
            // a dispatch here which would add it to the poller a second time.
            updateState(AsyncState.COMPLETING);
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState", "asyncComplete()", state));
        }
        return triggerDispatch;
    }


    synchronized boolean asyncTimeout() {
        if (state == AsyncState.STARTED) {
            updateState(AsyncState.TIMING_OUT);
            return true;
        } else if (state == AsyncState.COMPLETING || state == AsyncState.DISPATCHING ||
                state == AsyncState.DISPATCHED) {
            // NOOP - App called complete() or dispatch() between the the
            // timeout firing and execution reaching this point
            return false;
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState", "asyncTimeout()", state));
        }
    }


    synchronized boolean asyncDispatch() {
        Request request = processor.getRequest();
        if ((request == null || !request.isRequestThread()) &&
                (state == AsyncState.STARTING || state == AsyncState.READ_WRITE_OP)) {
            updateState(AsyncState.DISPATCH_PENDING);
            return false;
        }

        clearNonBlockingListeners();
        boolean triggerDispatch = false;
        if (state == AsyncState.STARTING || state == AsyncState.MUST_ERROR) {
            // Processing is on a container thread so no need to transfer
            // processing to a new container thread
            updateState(AsyncState.MUST_DISPATCH);
        } else if (state == AsyncState.STARTED) {
            updateState(AsyncState.DISPATCHING);
            // A dispatch to a container thread is always required.
            // If on a non-container thread, need to get back onto a container
            // thread to complete the processing.
            // If on a container thread the current request/response are not the
            // request/response associated with the AsyncContext so need a new
            // container thread to process the different request/response.
            triggerDispatch = true;
        } else if (state == AsyncState.READ_WRITE_OP || state == AsyncState.TIMING_OUT || state == AsyncState.ERROR) {
            // Read/write operations can happen on or off a container thread but
            // while in this state the call to listener that triggers the
            // read/write will be in progress on a container thread.
            // Processing of timeouts and errors can happen on or off a
            // container thread (on is much more likely) but while in this state
            // the call that triggers the timeout will be in progress on a
            // container thread.
            // The socket will be added to the poller when the container thread
            // exits the AbstractConnectionHandler.process() method so don't do
            // a dispatch here which would add it to the poller a second time.
            updateState(AsyncState.DISPATCHING);
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState", "asyncDispatch()", state));
        }
        return triggerDispatch;
    }


    synchronized void asyncDispatched() {
        if (state == AsyncState.DISPATCHING || state == AsyncState.MUST_DISPATCH) {
            updateState(AsyncState.DISPATCHED);
            asyncCtxt.decrementInProgressAsyncCount();
        } else {
            throw new IllegalStateException(
                    sm.getString("asyncStateMachine.invalidAsyncState", "asyncDispatched()", state));
        }
    }


    synchronized boolean asyncError() {
        Request request = processor.getRequest();
        boolean containerThread = (request != null && request.isRequestThread());

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("asyncStateMachine.asyncError.start"));
        }

        clearNonBlockingListeners();
        if (state == AsyncState.STARTING) {
            updateState(AsyncState.MUST_ERROR);
        } else {
            if (hasProcessedError) {
                if (log.isTraceEnabled()) {
                    log.trace(sm.getString("asyncStateMachine.asyncError.skip"));
                }
                return false;
            }
            hasProcessedError = true;
            if (state == AsyncState.DISPATCHED) {
                // Async error handling has moved processing back into an async
                // state. Need to increment in progress count as it will decrement
                // when the async state is exited again.
                asyncCtxt.incrementInProgressAsyncCount();
                updateState(AsyncState.ERROR);
            } else {
                updateState(AsyncState.ERROR);
            }
        }

        // Return true for non-container threads to trigger a dispatch
        return !containerThread;
    }


    synchronized void asyncRun(Runnable runnable) {
        if (state == AsyncState.STARTING || state == AsyncState.STARTED || state == AsyncState.READ_WRITE_OP) {
            // Execute the runnable using a container thread from the
            // Connector's thread pool. Use a wrapper to prevent a memory leak
            Thread currentThread = Thread.currentThread();
            ClassLoader oldCL = currentThread.getContextClassLoader();
            try {
                currentThread.setContextClassLoader(this.getClass().getClassLoader());
                processor.execute(runnable);
            } finally {
                currentThread.setContextClassLoader(oldCL);
            }
        } else {
            throw new IllegalStateException(sm.getString("asyncStateMachine.invalidAsyncState", "asyncRun()", state));
        }
    }


    synchronized boolean isAvailable() {
        if (asyncCtxt == null) {
            // Async processing has probably been completed in another thread.
            // Trigger a timeout to make sure the Processor is cleaned up.
            return false;
        }
        return asyncCtxt.isAvailable();
    }


    synchronized void recycle() {
        // Use lastAsyncStart to determine if this instance has been used since
        // it was last recycled. If it hasn't there is no need to recycle again
        // which saves the relatively expensive call to notifyAll()
        if (lastAsyncStart == 0) {
            return;
        }
        // Ensure in case of error that any non-container threads that have been
        // paused are unpaused.
        notifyAll();
        asyncCtxt = null;
        state = AsyncState.DISPATCHED;
        lastAsyncStart = 0;
        hasProcessedError = false;
    }


    private void clearNonBlockingListeners() {
        processor.getRequest().listener = null;
        processor.getRequest().getResponse().listener = null;
    }


    private synchronized void updateState(AsyncState newState) {
        if (log.isTraceEnabled()) {
            log.trace(sm.getString("asyncStateMachine.stateChange", state, newState));
        }
        state = newState;
    }
}

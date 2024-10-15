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
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;

/**
 * This is a light-weight abstract processor implementation that is intended as a basis for all Processor
 * implementations from the light-weight upgrade processors to the HTTP/AJP processors.
 */
public abstract class AbstractProcessorLight implements Processor {

    private Set<DispatchType> dispatches = new CopyOnWriteArraySet<>();


    @Override
    public SocketState process(SocketWrapperBase<?> socketWrapper, SocketEvent status) throws IOException {

        SocketState state = SocketState.CLOSED;
        Iterator<DispatchType> dispatches = null;
        do {
            if (dispatches != null) {
                DispatchType nextDispatch = dispatches.next();
                if (getLog().isTraceEnabled()) {
                    getLog().trace("Processing dispatch type: [" + nextDispatch + "]");
                }
                state = dispatch(nextDispatch.getSocketStatus());
                if (!dispatches.hasNext()) {
                    state = checkForPipelinedData(state, socketWrapper);
                }
            } else if (status == SocketEvent.DISCONNECT) {
                // Do nothing here, just wait for it to get recycled
            } else if (isAsync() || isUpgrade() || state == SocketState.ASYNC_END) {
                state = dispatch(status);
                state = checkForPipelinedData(state, socketWrapper);
            } else if (status == SocketEvent.OPEN_WRITE) {
                // Extra write event likely after async, ignore
                state = SocketState.LONG;
            } else if (status == SocketEvent.OPEN_READ) {
                state = service(socketWrapper);
            } else if (status == SocketEvent.CONNECT_FAIL) {
                logAccess(socketWrapper);
            } else {
                // Default to closing the socket if the SocketEvent passed in
                // is not consistent with the current state of the Processor
                state = SocketState.CLOSED;
            }

            if (getLog().isTraceEnabled()) {
                getLog().trace(
                        "Socket: [" + socketWrapper + "], Status in: [" + status + "], State out: [" + state + "]");
            }

            /*
             * If state is already CLOSED don't call asyncPostProcess() as that will likely change the the state to some
             * other value causing processing to continue when it should cease. The AsyncStateMachine will be recycled
             * as part of the Processor clean-up on CLOSED so it doesn't matter what state it is left in at this point.
             */
            if (isAsync() && state != SocketState.CLOSED) {
                state = asyncPostProcess();
                if (getLog().isTraceEnabled()) {
                    getLog().trace(
                            "Socket: [" + socketWrapper + "], State after async post processing: [" + state + "]");
                }
            }

            if (dispatches == null || !dispatches.hasNext()) {
                // Only returns non-null iterator if there are
                // dispatches to process.
                dispatches = getIteratorAndClearDispatches();
            }
        } while (state == SocketState.ASYNC_END || dispatches != null && state != SocketState.CLOSED);

        return state;
    }


    private SocketState checkForPipelinedData(SocketState inState, SocketWrapperBase<?> socketWrapper)
            throws IOException {
        if (inState == SocketState.OPEN) {
            // There may be pipe-lined data to read. If the data isn't
            // processed now, execution will exit this loop and call
            // release() which will recycle the processor (and input
            // buffer) deleting any pipe-lined data. To avoid this,
            // process it now.
            return service(socketWrapper);
        } else {
            return inState;
        }
    }


    public void addDispatch(DispatchType dispatchType) {
        synchronized (dispatches) {
            dispatches.add(dispatchType);
        }
    }


    public Iterator<DispatchType> getIteratorAndClearDispatches() {
        // Note: Logic in AbstractProtocol depends on this method only returning
        // a non-null value if the iterator is non-empty. i.e. it should never
        // return an empty iterator.
        Iterator<DispatchType> result;
        synchronized (dispatches) {
            // Synchronized as the generation of the iterator and the clearing
            // of dispatches needs to be an atomic operation.
            result = dispatches.iterator();
            if (result.hasNext()) {
                dispatches.clear();
            } else {
                result = null;
            }
        }
        return result;
    }


    protected void clearDispatches() {
        synchronized (dispatches) {
            dispatches.clear();
        }
    }


    /**
     * Add an entry to the access log for a failed connection attempt.
     *
     * @param socketWrapper The connection to process
     *
     * @throws IOException If an I/O error occurs during the processing of the request
     */
    protected void logAccess(SocketWrapperBase<?> socketWrapper) throws IOException {
        // NO-OP by default
    }


    /**
     * Service a 'standard' HTTP request. This method is called for both new requests and for requests that have
     * partially read the HTTP request line or HTTP headers. Once the headers have been fully read this method is not
     * called again until there is a new HTTP request to process. Note that the request type may change during
     * processing which may result in one or more calls to {@link #dispatch(SocketEvent)}. Requests may be pipe-lined.
     *
     * @param socketWrapper The connection to process
     *
     * @return The state the caller should put the socket in when this method returns
     *
     * @throws IOException If an I/O error occurs during the processing of the request
     */
    protected abstract SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException;

    /**
     * Process an in-progress request that is not longer in standard HTTP mode. Uses currently include Servlet 3.0 Async
     * and HTTP upgrade connections. Further uses may be added in the future. These will typically start as HTTP
     * requests.
     *
     * @param status The event to process
     *
     * @return The state the caller should put the socket in when this method returns
     *
     * @throws IOException If an I/O error occurs during the processing of the request
     */
    protected abstract SocketState dispatch(SocketEvent status) throws IOException;

    /**
     * Calls the post process of the async state machine.
     *
     * @return The state the caller should put the socket in when this method returns
     *
     * @throws IOException If an I/O error occurs during the processing of the request
     */
    protected abstract SocketState asyncPostProcess() throws IOException;

    /**
     * @return the logger associated with this processor type
     */
    protected abstract Log getLog();
}

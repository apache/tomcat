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
import java.io.InterruptedIOException;
import java.util.concurrent.Executor;

import javax.servlet.RequestDispatcher;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provides functionality and attributes common to all supported protocols
 * (currently HTTP and AJP).
 */
public abstract class AbstractProcessor extends AbstractProcessorLight implements ActionHook {

    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    protected Adapter adapter;
    protected final AsyncStateMachine asyncStateMachine;
    protected final AbstractEndpoint<?> endpoint;
    protected final Request request;
    protected final Response response;
    protected volatile SocketWrapperBase<?> socketWrapper = null;
    protected volatile SSLSupport sslSupport;
    private String clientCertProvider = null;

    /**
     * Error state for the request/response currently being processed.
     */
    private ErrorState errorState = ErrorState.NONE;


    /**
     * Used by HTTP/2.
     *
     * @param coyoteRequest
     * @param coyoteResponse
     */
    protected AbstractProcessor(Request coyoteRequest, Response coyoteResponse) {
        this(null, coyoteRequest, coyoteResponse);
    }


    public AbstractProcessor(AbstractEndpoint<?> endpoint) {
        this(endpoint, new Request(), new Response());
    }


    private AbstractProcessor(AbstractEndpoint<?> endpoint, Request coyoteRequest,
            Response coyoteResponse) {
        this.endpoint = endpoint;
        asyncStateMachine = new AsyncStateMachine(this);
        request = coyoteRequest;
        response = coyoteResponse;
        response.setHook(this);
        request.setResponse(response);
        request.setHook(this);
    }

    /**
     * Update the current error state to the new error state if the new error
     * state is more severe than the current error state.
     */
    protected void setErrorState(ErrorState errorState, Throwable t) {
        boolean blockIo = this.errorState.isIoAllowed() && !errorState.isIoAllowed();
        this.errorState = this.errorState.getMostSevere(errorState);
        if (blockIo && !ContainerThreadMarker.isContainerThread() && isAsync()) {
            // The error occurred on a non-container thread during async
            // processing which means not all of the necessary clean-up will
            // have been completed. Dispatch to a container thread to do the
            // clean-up. Need to do it this way to ensure that all the necessary
            // clean-up is performed.
            if (response.getStatus() < 400) {
                response.setStatus(500);
            }
            getLog().info(sm.getString("abstractProcessor.nonContainerThreadError"), t);
            socketWrapper.processSocket(SocketStatus.CLOSE_NOW, true);
        }
    }


    protected void resetErrorState() {
        errorState = ErrorState.NONE;
    }


    protected ErrorState getErrorState() {
        return errorState;
    }


    /**
     * The request associated with this processor.
     */
    @Override
    public Request getRequest() {
        return request;
    }


    /**
     * Set the associated adapter.
     *
     * @param adapter the new adapter
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }


    /**
     * Get the associated adapter.
     *
     * @return the associated adapter
     */
    public Adapter getAdapter() {
        return adapter;
    }


    @Override
    public String getClientCertProvider() {
        return clientCertProvider;
    }


    public void setClientCertProvider(String s) {
        this.clientCertProvider = s;
    }


    /**
     * Set the socket wrapper being used.
     */
    protected final void setSocketWrapper(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    /**
     * Get the socket wrapper being used.
     */
    protected final SocketWrapperBase<?> getSocketWrapper() {
        return socketWrapper;
    }


    /**
     * Set the SSL information for this HTTP connection.
     */
    @Override
    public final void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }


    /**
     * Obtain the Executor used by the underlying endpoint.
     */
    @Override
    public Executor getExecutor() {
        return endpoint.getExecutor();
    }


    @Override
    public boolean isAsync() {
        return asyncStateMachine.isAsync();
    }


    @Override
    public SocketState asyncPostProcess() {
        return asyncStateMachine.asyncPostProcess();
    }

    @Override
    public void errorDispatch() {
        getAdapter().errorDispatch(request, response);
    }


    /**
     * Process an in-progress request that is not longer in standard HTTP mode.
     * Uses currently include Servlet 3.0 Async and HTTP upgrade connections.
     * Further uses may be added in the future. These will typically start as
     * HTTP requests.
     */
    @Override
    public final SocketState dispatch(SocketStatus status) {

        if (status == SocketStatus.OPEN_WRITE && response.getWriteListener() != null) {
            asyncStateMachine.asyncOperation();
            try {
                if (flushBufferedWrite()) {
                    return SocketState.LONG;
                }
            } catch (IOException ioe) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Unable to write async data.", ioe);
                }
                status = SocketStatus.ASYNC_WRITE_ERROR;
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ioe);
            }
        } else if (status == SocketStatus.OPEN_READ && request.getReadListener() != null) {
            dispatchNonBlockingRead();
        }

        RequestInfo rp = request.getRequestProcessor();
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            if (!getAdapter().asyncDispatch(request, response, status)) {
                setErrorState(ErrorState.CLOSE_NOW, null);
            }
        } catch (InterruptedIOException e) {
            setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            setErrorState(ErrorState.CLOSE_NOW, t);
            getLog().error(sm.getString("http11processor.request.process"), t);
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (getErrorState().isError()) {
            request.updateCounters();
            return SocketState.CLOSED;
        } else if (isAsync()) {
            return SocketState.LONG;
        } else {
            request.updateCounters();
            return dispatchEndRequest();
        }
    }


    /**
     * Perform any necessary processing for a non-blocking read before
     * dispatching to the adapter.
     */
    protected void dispatchNonBlockingRead() {
        asyncStateMachine.asyncOperation();
    }

    /**
     * Flush any pending writes. Used during non-blocking writes to flush any
     * remaining data from a previous incomplete write.
     *
     * @return <code>true</code> if data remains to be flushed at the end of
     *         method
     *
     * @throws IOException If an I/O error occurs while attempting to flush the
     *         data
     */
    protected abstract boolean flushBufferedWrite() throws IOException ;

    /**
     * Perform any necessary clean-up processing if the dispatch resulted in the
     * completion of processing for the current request.
     *
     * @return The state to return for the socket once the clean-up for the
     *         current request has completed
     */
    protected abstract SocketState dispatchEndRequest();

    protected abstract Log getLog();
}

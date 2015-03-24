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
import java.util.concurrent.Executor;

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provides functionality and attributes common to all supported protocols
 * (currently HTTP and AJP).
 */
public abstract class AbstractProcessor implements ActionHook, Processor {

    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    protected Adapter adapter;
    protected final AsyncStateMachine asyncStateMachine;
    protected final AbstractEndpoint<?> endpoint;
    protected final Request request;
    protected final Response response;
    protected SocketWrapperBase<?> socketWrapper = null;
    private String clientCertProvider = null;

    /**
     * Error state for the request/response currently being processed.
     */
    private ErrorState errorState = ErrorState.NONE;


    /**
     * Intended for use by the Upgrade sub-classes that have no need to
     * initialise the request, response, etc.
     */
    protected AbstractProcessor() {
        asyncStateMachine = null;
        endpoint = null;
        request = null;
        response = null;
    }

    public AbstractProcessor(AbstractEndpoint<?> endpoint) {
        this.endpoint = endpoint;
        asyncStateMachine = new AsyncStateMachine(this);
        request = new Request();
        response = new Response();
        response.setHook(this);
        request.setResponse(response);
    }


    /**
     * Update the current error state to the new error state if the new error
     * state is more severe than the current error state.
     */
    protected void setErrorState(ErrorState errorState, Throwable t) {
        boolean blockIo = this.errorState.isIoAllowed() && !errorState.isIoAllowed();
        this.errorState = this.errorState.getMostSevere(errorState);
        if (blockIo && !ContainerThreadMarker.isContainerThread()) {
            // The error occurred on a non-container thread which means not all
            // of the necessary clean-up will have been completed. Dispatch to
            // a container thread to do the clean-up. Need to do it this way to
            // ensure that all the necessary clean-up is performed.
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
     * The endpoint receiving connections that are handled by this processor.
     */
    protected AbstractEndpoint<?> getEndpoint() {
        return endpoint;
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
     * Obtain the Executor used by the underlying endpoint.
     */
    @Override
    public Executor getExecutor() {
        return endpoint.getExecutor();
    }


    @Override
    public boolean isAsync() {
        return (asyncStateMachine != null && asyncStateMachine.isAsync());
    }


    @Override
    public SocketState asyncPostProcess() {
        return asyncStateMachine.asyncPostProcess();
    }

    @Override
    public void errorDispatch() {
        getAdapter().errorDispatch(request, response);
    }

    @Override
    public abstract boolean isUpgrade();

    /**
     * Process HTTP requests. All requests are treated as HTTP requests to start
     * with although they may change type during processing.
     */
    @Override
    public abstract SocketState process(SocketWrapperBase<?> socket) throws IOException;

    /**
     * Process an in-progress request that is not longer in standard HTTP mode.
     * Uses currently include Servlet 3.0 Async and HTTP upgrade connections.
     * Further uses may be added in the future. These will typically start as
     * HTTP requests.
     */
    @Override
    public abstract SocketState dispatch(SocketStatus status);


    @Override
    public abstract HttpUpgradeHandler getHttpUpgradeHandler();

    protected abstract Log getLog();
}

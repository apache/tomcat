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
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.RequestDispatcher;

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provides functionality and attributes common to all supported protocols
 * (currently HTTP and AJP).
 */
public abstract class AbstractProcessor extends AbstractProcessorLight implements ActionHook {

    private static final StringManager sm = StringManager.getManager(AbstractProcessor.class);

    protected Adapter adapter;
    protected final AsyncStateMachine asyncStateMachine;
    private volatile long asyncTimeout = -1;
    protected final AbstractEndpoint<?> endpoint;
    protected final Request request;
    protected final Response response;
    protected volatile SocketWrapperBase<?> socketWrapper = null;
    protected volatile SSLSupport sslSupport;
    private int maxCookieCount = 200;


    /**
     * Error state for the request/response currently being processed.
     */
    private ErrorState errorState = ErrorState.NONE;


    /**
     * Used by HTTP/2.
     * @param coyoteRequest The request
     * @param coyoteResponse The response
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
     * @param errorState The error status details
     * @param t The error which occurred
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
            socketWrapper.processSocket(SocketEvent.ERROR, true);
        }
    }


    protected ErrorState getErrorState() {
        return errorState;
    }


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


    /**
     * Set the socket wrapper being used.
     * @param socketWrapper The socket wrapper
     */
    protected final void setSocketWrapper(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }


    /**
     * @return the socket wrapper being used.
     */
    protected final SocketWrapperBase<?> getSocketWrapper() {
        return socketWrapper;
    }


    @Override
    public final void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }


    /**
     * @return the Executor used by the underlying endpoint.
     */
    protected Executor getExecutor() {
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
    public final SocketState dispatch(SocketEvent status) {

        if (status == SocketEvent.OPEN_WRITE && response.getWriteListener() != null) {
            asyncStateMachine.asyncOperation();
            try {
                if (flushBufferedWrite()) {
                    return SocketState.LONG;
                }
            } catch (IOException ioe) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Unable to write async data.", ioe);
                }
                status = SocketEvent.ERROR;
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ioe);
            }
        } else if (status == SocketEvent.OPEN_READ && request.getReadListener() != null) {
            dispatchNonBlockingRead();
        } else if (status == SocketEvent.ERROR) {
            // An I/O error occurred on a non-container thread. This includes:
            // - read/write timeouts fired by the Poller (NIO & APR)
            // - completion handler failures in NIO2

            if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) == null) {
                // Because the error did not occur on a container thread the
                // request's error attribute has not been set. If an exception
                // is available from the socketWrapper, use it to set the
                // request's error attribute here so it is visible to the error
                // handling.
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, socketWrapper.getError());
            }

            if (request.getReadListener() != null || response.getWriteListener() != null) {
                // The error occurred during non-blocking I/O. Set the correct
                // state else the error handling will trigger an ISE.
                asyncStateMachine.asyncOperation();
            }
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


    @Override
    public final void action(ActionCode actionCode, Object param) {
        switch (actionCode) {
        // 'Normal' servlet support
        case COMMIT: {
            if (!response.isCommitted()) {
                try {
                    // Validate and write response headers
                    prepareResponse();
                } catch (IOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                }
            }
            break;
        }
        case CLOSE: {
            action(ActionCode.COMMIT, null);
            try {
                finishResponse();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
            }
            break;
        }
        case ACK: {
            ack();
            break;
        }
        case CLIENT_FLUSH: {
            action(ActionCode.COMMIT, null);
            try {
                flush();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                response.setErrorException(e);
            }
            break;
        }
        case AVAILABLE: {
            request.setAvailable(available(Boolean.TRUE.equals(param)));
            break;
        }
        case REQ_SET_BODY_REPLAY: {
            ByteChunk body = (ByteChunk) param;
            setRequestBody(body);
            break;
        }

        // Error handling
        case IS_ERROR: {
            ((AtomicBoolean) param).set(getErrorState().isError());
            break;
        }
        case CLOSE_NOW: {
            // Prevent further writes to the response
            setSwallowResponse();
            setErrorState(ErrorState.CLOSE_NOW, null);
            break;
        }
        case DISABLE_SWALLOW_INPUT: {
            // Aborted upload or similar.
            // No point reading the remainder of the request.
            disableSwallowRequest();
            // This is an error state. Make sure it is marked as such.
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            break;
        }

        // Request attribute support
        case REQ_HOST_ADDR_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.remoteAddr().setString(socketWrapper.getRemoteAddr());
            }
            break;
        }
        case REQ_HOST_ATTRIBUTE: {
            populateRequestAttributeRemoteHost();
            break;
        }
        case REQ_LOCALPORT_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.setLocalPort(socketWrapper.getLocalPort());
            }
            break;
        }
        case REQ_LOCAL_ADDR_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.localAddr().setString(socketWrapper.getLocalAddr());
            }
            break;
        }
        case REQ_LOCAL_NAME_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.localName().setString(socketWrapper.getLocalName());
            }
            break;
        }
        case REQ_REMOTEPORT_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.setRemotePort(socketWrapper.getRemotePort());
            }
            break;
        }

        // SSL request attribute support
        case REQ_SSL_ATTRIBUTE: {
            populateSslRequestAttributes();
            break;
        }
        case REQ_SSL_CERTIFICATE: {
            sslReHandShake();
            break;
        }

        // Servlet 3.0 asynchronous support
        case ASYNC_START: {
            asyncStateMachine.asyncStart((AsyncContextCallback) param);
            break;
        }
        case ASYNC_COMPLETE: {
            clearDispatches();
            if (asyncStateMachine.asyncComplete()) {
                socketWrapper.processSocket(SocketEvent.OPEN_READ, true);
            }
            break;
        }
        case ASYNC_DISPATCH: {
            if (asyncStateMachine.asyncDispatch()) {
                socketWrapper.processSocket(SocketEvent.OPEN_READ, true);
            }
            break;
        }
        case ASYNC_DISPATCHED: {
            asyncStateMachine.asyncDispatched();
            break;
        }
        case ASYNC_ERROR: {
            asyncStateMachine.asyncError();
            break;
        }
        case ASYNC_IS_ASYNC: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsync());
            break;
        }
        case ASYNC_IS_COMPLETING: {
            ((AtomicBoolean) param).set(asyncStateMachine.isCompleting());
            break;
        }
        case ASYNC_IS_DISPATCHING: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncDispatching());
            break;
        }
        case ASYNC_IS_ERROR: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncError());
            break;
        }
        case ASYNC_IS_STARTED: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncStarted());
            break;
        }
        case ASYNC_IS_TIMINGOUT: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncTimingOut());
            break;
        }
        case ASYNC_RUN: {
            asyncStateMachine.asyncRun((Runnable) param);
            break;
        }
        case ASYNC_SETTIMEOUT: {
            if (param == null) {
                return;
            }
            long timeout = ((Long) param).longValue();
            setAsyncTimeout(timeout);
            break;
        }
        case ASYNC_TIMEOUT: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(asyncStateMachine.asyncTimeout());
            break;
        }
        case ASYNC_POST_PROCESS: {
            asyncStateMachine.asyncPostProcess();
            break;
        }

        // Servlet 3.1 non-blocking I/O
        case REQUEST_BODY_FULLY_READ: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(isRequestBodyFullyRead());
            break;
        }
        case NB_READ_INTEREST: {
            if (!isRequestBodyFullyRead()) {
                registerReadInterest();
            }
            break;
        }
        case NB_WRITE_INTEREST: {
            AtomicBoolean isReady = (AtomicBoolean)param;
            isReady.set(isReady());
            break;
        }
        case DISPATCH_READ: {
            addDispatch(DispatchType.NON_BLOCKING_READ);
            break;
        }
        case DISPATCH_WRITE: {
            addDispatch(DispatchType.NON_BLOCKING_WRITE);
            break;
        }
        case DISPATCH_EXECUTE: {
            SocketWrapperBase<?> wrapper = socketWrapper;
            if (wrapper != null) {
                executeDispatches(wrapper);
            }
            break;
        }

        // Servlet 3.1 HTTP Upgrade
        case UPGRADE: {
            doHttpUpgrade((UpgradeToken) param);
            break;
        }

        // Servlet 4.0 Push requests
        case IS_PUSH_SUPPORTED: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(isPushSupported());
            break;
        }
        case PUSH_REQUEST: {
            doPush((PushToken) param);
            break;
        }
        }
    }


    /**
     * Perform any necessary processing for a non-blocking read before
     * dispatching to the adapter.
     */
    protected void dispatchNonBlockingRead() {
        asyncStateMachine.asyncOperation();
    }


    @Override
    public void timeoutAsync(long now) {
        if (now < 0) {
            doTimeoutAsync();
        } else {
            long asyncTimeout = getAsyncTimeout();
            if (asyncTimeout > 0) {
                long asyncStart = asyncStateMachine.getLastAsyncStart();
                if ((now - asyncStart) > asyncTimeout) {
                    doTimeoutAsync();
                }
            }
        }
    }


    private void doTimeoutAsync() {
        // Avoid multiple timeouts
        setAsyncTimeout(-1);
        socketWrapper.processSocket(SocketEvent.TIMEOUT, true);
    }


    public void setAsyncTimeout(long timeout) {
        asyncTimeout = timeout;
    }


    public long getAsyncTimeout() {
        return asyncTimeout;
    }


    public int getMaxCookieCount() {
        return maxCookieCount;
    }


    public void setMaxCookieCount(int maxCookieCount) {
        this.maxCookieCount = maxCookieCount;
    }


    @Override
    public void recycle() {
        errorState = ErrorState.NONE;
        asyncStateMachine.recycle();
    }


    protected abstract void prepareResponse() throws IOException;


    protected abstract void finishResponse() throws IOException;


    protected abstract void ack();


    protected abstract void flush() throws IOException;


    protected abstract int available(boolean doRead);


    protected abstract void setRequestBody(ByteChunk body);


    protected abstract void setSwallowResponse();


    protected abstract void disableSwallowRequest();


    /**
     * Processors that populate request attributes directly (e.g. AJP) should
     * over-ride this method and return {@code false}.
     *
     * @return {@code true} if the SocketWrapper should be used to populate the
     *         request attributes, otherwise {@code false}.
     */
    protected boolean getPopulateRequestAttributesFromSocket() {
        return true;
    }


    /**
     * Populate the remote host request attribute. Processors (e.g. AJP) that
     * populate this from an alternative source should override this method.
     */
    protected void populateRequestAttributeRemoteHost() {
        if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
            request.remoteHost().setString(socketWrapper.getRemoteHost());
        }
    }


    /**
     * Populate the TLS related request attributes from the {@link SSLSupport}
     * instance associated with this processor. Protocols that populate TLS
     * attributes from a different source (e.g. AJP) should override this
     * method.
     */
    protected void populateSslRequestAttributes() {
        try {
            if (sslSupport != null) {
                Object sslO = sslSupport.getCipherSuite();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.CIPHER_SUITE_KEY, sslO);
                }
                sslO = sslSupport.getPeerCertificateChain();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.CERTIFICATE_KEY, sslO);
                }
                sslO = sslSupport.getKeySize();
                if (sslO != null) {
                    request.setAttribute (SSLSupport.KEY_SIZE_KEY, sslO);
                }
                sslO = sslSupport.getSessionId();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.SESSION_ID_KEY, sslO);
                }
                sslO = sslSupport.getProtocol();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.PROTOCOL_VERSION_KEY, sslO);
                }
                request.setAttribute(SSLSupport.SESSION_MGR, sslSupport);
            }
        } catch (Exception e) {
            getLog().warn(sm.getString("abstractProcessor.socket.ssl"), e);
        }
    }


    /**
     * Processors that can perform a TLS re-handshake (e.g. HTTP/1.1) should
     * override this method and implement the re-handshake.
     */
    protected void sslReHandShake() {
        // NO-OP
    }


    protected abstract boolean isRequestBodyFullyRead();


    protected abstract void registerReadInterest();


    protected abstract boolean isReady();


    protected abstract void executeDispatches(SocketWrapperBase<?> wrapper);


    /**
     * {@inheritDoc}
     * Processors that implement HTTP upgrade must override this method and
     * provide the necessary token.
     */
    @Override
    public UpgradeToken getUpgradeToken() {
        // Should never reach this code but in case we do...
        throw new IllegalStateException(
                sm.getString("abstractProcessor.httpupgrade.notsupported"));
    }


    /**
     * Process an HTTP upgrade. Processors that support HTTP upgrade should
     * override this method and process the provided token.
     *
     * @param upgradeToken Contains all the information necessary for the
     *                     Processor to process the upgrade
     *
     * @throws UnsupportedOperationException if the protocol does not support
     *         HTTP upgrade
     */
    protected void doHttpUpgrade(UpgradeToken upgradeToken) {
        // Should never happen
        throw new UnsupportedOperationException(
                sm.getString("abstractProcessor.httpupgrade.notsupported"));
    }


    /**
     * {@inheritDoc}
     * Processors that implement HTTP upgrade must override this method.
     */
    @Override
    public ByteBuffer getLeftoverInput() {
        // Should never reach this code but in case we do...
        throw new IllegalStateException(sm.getString("abstractProcessor.httpupgrade.notsupported"));
    }


    /**
     * {@inheritDoc}
     * Processors that implement HTTP upgrade must override this method.
     */
    @Override
    public boolean isUpgrade() {
        return false;
    }


    /**
     * Protocols that support push should override this method and return {@code
     * true}.
     *
     * @return {@code true} if push is supported by this processor, otherwise
     *         {@code false}.
     */
    protected boolean isPushSupported() {
        return false;
    }


    /**
     * Process a push. Processors that support push should override this method
     * and process the provided token.
     *
     * @param pushToken Contains all the information necessary for the Processor
     *                  to process the push request
     *
     * @throws UnsupportedOperationException if the protocol does not support
     *         push
     */
    protected void doPush(PushToken pushToken) {
        throw new UnsupportedOperationException(
                sm.getString("abstractProcessor.pushrequest.notsupported"));
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
}

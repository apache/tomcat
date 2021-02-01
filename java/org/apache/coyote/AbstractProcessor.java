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
import java.util.Iterator;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.RequestDispatcher;

import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.parser.Host;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provides functionality and attributes common to all supported protocols
 * (currently HTTP and AJP) for processing a single request/response.
 */
public abstract class AbstractProcessor extends AbstractProcessorLight implements ActionHook {

    private static final StringManager sm = StringManager.getManager(AbstractProcessor.class);

    // Used to avoid useless B2C conversion on the host name.
    private char[] hostNameC = new char[0];

    protected final Adapter adapter;
    protected final AsyncStateMachine asyncStateMachine;
    private volatile long asyncTimeout = -1;
    /*
     * Tracks the current async generation when a timeout is dispatched. In the
     * time it takes for a container thread to be allocated and the timeout
     * processing to start, it is possible that the application completes this
     * generation of async processing and starts a new one. If the timeout is
     * then processed against the new generation, response mix-up can occur.
     * This field is used to ensure that any timeout event processed is for the
     * current async generation. This prevents the response mix-up.
     */
    private volatile long asyncTimeoutGeneration = 0;
    protected final Request request;
    protected final Response response;
    protected volatile SocketWrapperBase<?> socketWrapper = null;
    protected volatile SSLSupport sslSupport;


    /**
     * Error state for the request/response currently being processed.
     */
    private ErrorState errorState = ErrorState.NONE;

    protected final UserDataHelper userDataHelper;

    public AbstractProcessor(Adapter adapter) {
        this(adapter, new Request(), new Response());
    }


    protected AbstractProcessor(Adapter adapter, Request coyoteRequest, Response coyoteResponse) {
        this.adapter = adapter;
        asyncStateMachine = new AsyncStateMachine(this);
        request = coyoteRequest;
        response = coyoteResponse;
        response.setHook(this);
        request.setResponse(response);
        request.setHook(this);
        userDataHelper = new UserDataHelper(getLog());
    }


    /**
     * Update the current error state to the new error state if the new error
     * state is more severe than the current error state.
     * @param errorState The error status details
     * @param t The error which occurred
     */
    protected void setErrorState(ErrorState errorState, Throwable t) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString("abstractProcessor.setErrorState", errorState), t);
        }
        // Use the return value to avoid processing more than one async error
        // in a single async cycle.
        boolean setError = response.setError();
        boolean blockIo = this.errorState.isIoAllowed() && !errorState.isIoAllowed();
        this.errorState = this.errorState.getMostSevere(errorState);
        // Don't change the status code for IOException since that is almost
        // certainly a client disconnect in which case it is preferable to keep
        // the original status code http://markmail.org/message/4cxpwmxhtgnrwh7n
        if (response.getStatus() < 400 && !(t instanceof IOException)) {
            response.setStatus(500);
        }
        if (t != null) {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
        }
        if (blockIo && isAsync() && setError) {
            if (asyncStateMachine.asyncError()) {
                processSocketEvent(SocketEvent.ERROR, true);
            }
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
    protected void setSocketWrapper(SocketWrapperBase<?> socketWrapper) {
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
     * Provides a mechanism to trigger processing on a container thread.
     *
     * @param runnable  The task representing the processing that needs to take
     *                  place on a container thread
     */
    protected void execute(Runnable runnable) {
        SocketWrapperBase<?> socketWrapper = this.socketWrapper;
        if (socketWrapper == null) {
            throw new RejectedExecutionException(sm.getString("abstractProcessor.noExecute"));
        } else {
            socketWrapper.execute(runnable);
        }
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
    public final SocketState dispatch(SocketEvent status) throws IOException {

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

        SocketState state;

        if (getErrorState().isError()) {
            request.updateCounters();
            state = SocketState.CLOSED;
        } else if (isAsync()) {
            state = SocketState.LONG;
        } else {
            request.updateCounters();
            state = dispatchEndRequest();
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Socket: [" + socketWrapper +
                    "], Status in: [" + status +
                    "], State out: [" + state + "]");
        }

        return state;
    }


    protected void parseHost(MessageBytes valueMB) {
        if (valueMB == null || valueMB.isNull()) {
            populateHost();
            populatePort();
            return;
        } else if (valueMB.getLength() == 0) {
            // Empty Host header so set sever name to empty string
            request.serverName().setString("");
            populatePort();
            return;
        }

        ByteChunk valueBC = valueMB.getByteChunk();
        byte[] valueB = valueBC.getBytes();
        int valueL = valueBC.getLength();
        int valueS = valueBC.getStart();
        if (hostNameC.length < valueL) {
            hostNameC = new char[valueL];
        }

        try {
            // Validates the host name
            int colonPos = Host.parse(valueMB);

            // Extract the port information first, if any
            if (colonPos != -1) {
                int port = 0;
                for (int i = colonPos + 1; i < valueL; i++) {
                    char c = (char) valueB[i + valueS];
                    if (c < '0' || c > '9') {
                        response.setStatus(400);
                        setErrorState(ErrorState.CLOSE_CLEAN, null);
                        return;
                    }
                    port = port * 10 + c - '0';
                }
                request.setServerPort(port);

                // Only need to copy the host name up to the :
                valueL = colonPos;
            }

            // Extract the host name
            for (int i = 0; i < valueL; i++) {
                hostNameC[i] = (char) valueB[i + valueS];
            }
            request.serverName().setChars(hostNameC, 0, valueL);

        } catch (IllegalArgumentException e) {
            // IllegalArgumentException indicates that the host name is invalid
            UserDataHelper.Mode logMode = userDataHelper.getNextMode();
            if (logMode != null) {
                String message = sm.getString("abstractProcessor.hostInvalid", valueMB.toString());
                switch (logMode) {
                    case INFO_THEN_DEBUG:
                        message += sm.getString("abstractProcessor.fallToDebug");
                        //$FALL-THROUGH$
                    case INFO:
                        getLog().info(message, e);
                        break;
                    case DEBUG:
                        getLog().debug(message, e);
                }
            }

            response.setStatus(400);
            setErrorState(ErrorState.CLOSE_CLEAN, e);
        }
    }


    /**
     * Called when a host header is not present in the request (e.g. HTTP/1.0).
     * It populates the server name with appropriate information. The source is
     * expected to vary by protocol.
     * <p>
     * The default implementation is a NO-OP.
     */
    protected void populateHost() {
        // NO-OP
    }


    /**
     * Called when a host header is not present or is empty in the request (e.g.
     * HTTP/1.0). It populates the server port with appropriate information. The
     * source is expected to vary by protocol.
     * <p>
     * The default implementation is a NO-OP.
     */
    protected void populatePort() {
        // NO-OP
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
                    handleIOException(e);
                }
            }
            break;
        }
        case CLOSE: {
            action(ActionCode.COMMIT, null);
            try {
                finishResponse();
            } catch (IOException e) {
                handleIOException(e);
            }
            break;
        }
        case ACK: {
            ack((ContinueResponseTiming) param);
            break;
        }
        case CLIENT_FLUSH: {
            action(ActionCode.COMMIT, null);
            try {
                flush();
            } catch (IOException e) {
                handleIOException(e);
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
        case IS_IO_ALLOWED: {
            ((AtomicBoolean) param).set(getErrorState().isIoAllowed());
            break;
        }
        case CLOSE_NOW: {
            // Prevent further writes to the response
            setSwallowResponse();
            if (param instanceof Throwable) {
                setErrorState(ErrorState.CLOSE_NOW, (Throwable) param);
            } else {
                setErrorState(ErrorState.CLOSE_NOW, null);
            }
            break;
        }
        case DISABLE_SWALLOW_INPUT: {
            // Cancelled upload or similar.
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
        case REQ_PEER_ADDR_ATTRIBUTE: {
            if (getPopulateRequestAttributesFromSocket() && socketWrapper != null) {
                request.peerAddr().setString(socketWrapper.getRemoteAddr());
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
            try {
                sslReHandShake();
            } catch (IOException ioe) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
            }
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
                processSocketEvent(SocketEvent.OPEN_READ, true);
            }
            break;
        }
        case ASYNC_DISPATCH: {
            if (asyncStateMachine.asyncDispatch()) {
                processSocketEvent(SocketEvent.OPEN_READ, true);
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
            AtomicBoolean isReady = (AtomicBoolean)param;
            isReady.set(isReadyForRead());
            break;
        }
        case NB_WRITE_INTEREST: {
            AtomicBoolean isReady = (AtomicBoolean)param;
            isReady.set(isReadyForWrite());
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
            executeDispatches();
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
            doPush((Request) param);
            break;
        }

        // Servlet 4.0 Trailers
        case IS_TRAILER_FIELDS_READY: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(isTrailerFieldsReady());
            break;
        }
        case IS_TRAILER_FIELDS_SUPPORTED: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(isTrailerFieldsSupported());
            break;
        }

        // Identifiers associated with multiplexing protocols like HTTP/2
        case CONNECTION_ID: {
            @SuppressWarnings("unchecked")
            AtomicReference<Object> result = (AtomicReference<Object>) param;
            result.set(getConnectionID());
            break;
        }
        case STREAM_ID: {
            @SuppressWarnings("unchecked")
            AtomicReference<Object> result = (AtomicReference<Object>) param;
            result.set(getStreamID());
            break;
        }
        }
    }


    private void handleIOException (IOException ioe) {
        if (ioe instanceof CloseNowException) {
            // Close the channel but keep the connection open
            setErrorState(ErrorState.CLOSE_NOW, ioe);
        } else {
            // Close the connection and all channels within that connection
            setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
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
     * {@inheritDoc}
     * <p>
     * Sub-classes of this base class represent a single request/response pair.
     * The timeout to be processed is, therefore, the Servlet asynchronous
     * processing timeout.
     */
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
            } else if (!asyncStateMachine.isAvailable()) {
                // Timeout the async process if the associated web application
                // is no longer running.
                doTimeoutAsync();
            }
        }
    }


    private void doTimeoutAsync() {
        // Avoid multiple timeouts
        setAsyncTimeout(-1);
        asyncTimeoutGeneration = asyncStateMachine.getCurrentGeneration();
        processSocketEvent(SocketEvent.TIMEOUT, true);
    }


    @Override
    public boolean checkAsyncTimeoutGeneration() {
        return asyncTimeoutGeneration == asyncStateMachine.getCurrentGeneration();
    }


    public void setAsyncTimeout(long timeout) {
        asyncTimeout = timeout;
    }


    public long getAsyncTimeout() {
        return asyncTimeout;
    }


    @Override
    public void recycle() {
        errorState = ErrorState.NONE;
        asyncStateMachine.recycle();
    }


    protected abstract void prepareResponse() throws IOException;


    protected abstract void finishResponse() throws IOException;


    /**
     * @deprecated Unused. This will be removed in Tomcat 10 onwards. Use
     *             {@link #ack(ContinueResponseTiming)}.
     */
    @Deprecated
    protected void ack() {
        ack(ContinueResponseTiming.ALWAYS);
    }


    protected abstract void ack(ContinueResponseTiming continueResponseTiming);


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
                sslO = sslSupport.getRequestedProtocols();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.REQUESTED_PROTOCOL_VERSIONS_KEY, sslO);
                }
                sslO = sslSupport.getRequestedCiphers();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.REQUESTED_CIPHERS_KEY, sslO);
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
     *
     * @throws IOException If authentication is required then there will be I/O
     *                     with the client and this exception will be thrown if
     *                     that goes wrong
     */
    protected void sslReHandShake() throws IOException {
        // NO-OP
    }


    protected void processSocketEvent(SocketEvent event, boolean dispatch) {
        SocketWrapperBase<?> socketWrapper = getSocketWrapper();
        if (socketWrapper != null) {
            socketWrapper.processSocket(event, dispatch);
        }
    }


    protected boolean isReadyForRead() {
        if (available(true) > 0) {
            return true;
        }

        if (!isRequestBodyFullyRead()) {
            registerReadInterest();
        }

        return false;
    }


    protected abstract boolean isRequestBodyFullyRead();


    protected abstract void registerReadInterest();


    protected abstract boolean isReadyForWrite();


    protected void executeDispatches() {
        SocketWrapperBase<?> socketWrapper = getSocketWrapper();
        Iterator<DispatchType> dispatches = getIteratorAndClearDispatches();
        if (socketWrapper != null) {
            synchronized (socketWrapper) {
                /*
                 * This method is called when non-blocking IO is initiated by defining
                 * a read and/or write listener in a non-container thread. It is called
                 * once the non-container thread completes so that the first calls to
                 * onWritePossible() and/or onDataAvailable() as appropriate are made by
                 * the container.
                 *
                 * Processing the dispatches requires (for APR/native at least)
                 * that the socket has been added to the waitingRequests queue. This may
                 * not have occurred by the time that the non-container thread completes
                 * triggering the call to this method. Therefore, the coded syncs on the
                 * SocketWrapper as the container thread that initiated this
                 * non-container thread holds a lock on the SocketWrapper. The container
                 * thread will add the socket to the waitingRequests queue before
                 * releasing the lock on the socketWrapper. Therefore, by obtaining the
                 * lock on socketWrapper before processing the dispatches, we can be
                 * sure that the socket has been added to the waitingRequests queue.
                 */
                while (dispatches != null && dispatches.hasNext()) {
                    DispatchType dispatchType = dispatches.next();
                    socketWrapper.processSocket(dispatchType.getSocketStatus(), false);
                }
            }
        }
    }


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
     * @param pushTarget Contains all the information necessary for the Processor
     *                   to process the push request
     *
     * @throws UnsupportedOperationException if the protocol does not support
     *         push
     */
    protected void doPush(Request pushTarget) {
        throw new UnsupportedOperationException(
                sm.getString("abstractProcessor.pushrequest.notsupported"));
    }


    protected abstract boolean isTrailerFieldsReady();


    /**
     * Protocols that support trailer fields should override this method and
     * return {@code true}.
     *
     * @return {@code true} if trailer fields are supported by this processor,
     *         otherwise {@code false}.
     */
    protected boolean isTrailerFieldsSupported() {
        return false;
    }


    /**
     * Protocols that support multiplexing (e.g. HTTP/2) should override this
     * method and return the appropriate ID.
     *
     * @return The stream ID associated with this request or {@code null} if a
     *         multiplexing protocol is not being used
      */
    protected Object getConnectionID() {
        return null;
    }


    /**
     * Protocols that support multiplexing (e.g. HTTP/2) should override this
     * method and return the appropriate ID.
     *
     * @return The stream ID associated with this request or {@code null} if a
     *         multiplexing protocol is not being used
     */
    protected Object getStreamID() {
        return null;
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
     *
     * @throws IOException If an I/O error occurs while attempting to end the
     *         request
     */
    protected abstract SocketState dispatchEndRequest() throws IOException;


    @Override
    protected final void logAccess(SocketWrapperBase<?> socketWrapper) throws IOException {
        // Set the socket wrapper so the access log can read the socket related
        // information (e.g. client IP)
        setSocketWrapper(socketWrapper);
        // Setup the minimal request information
        request.setStartTime(System.currentTimeMillis());
        // Setup the minimal response information
        response.setStatus(400);
        response.setError();
        getAdapter().log(request, response, 0);
    }
}

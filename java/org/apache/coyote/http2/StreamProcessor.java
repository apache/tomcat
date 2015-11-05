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
package org.apache.coyote.http2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.AsyncContextCallback;
import org.apache.coyote.ContainerThreadMarker;
import org.apache.coyote.ErrorState;
import org.apache.coyote.Request;
import org.apache.coyote.UpgradeToken;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.DispatchType;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class StreamProcessor extends AbstractProcessor implements Runnable {

    private static final Log log = LogFactory.getLog(StreamProcessor.class);
    private static final StringManager sm = StringManager.getManager(StreamProcessor.class);

    private final Stream stream;

    private volatile SSLSupport sslSupport;


    public StreamProcessor(Stream stream, Adapter adapter, SocketWrapperBase<?> socketWrapper) {
        super(stream.getCoyoteRequest(), stream.getCoyoteResponse());
        this.stream = stream;
        setAdapter(adapter);
        setSocketWrapper(socketWrapper);
    }


    @Override
    public synchronized void run() {
        // HTTP/2 equivalent of AbstractConnectionHandler#process() without the
        // socket <-> processor mapping
        ContainerThreadMarker.set();
        SocketState state = SocketState.CLOSED;
        try {
            state = process(socketWrapper, SocketStatus.OPEN_READ);

            if (state == SocketState.CLOSED) {
                if (!getErrorState().isConnectionIoAllowed()) {
                    ConnectionException ce = new ConnectionException(sm.getString(
                            "streamProcessor.error.connection", stream.getConnectionId(),
                            stream.getIdentifier()), Http2Error.INTERNAL_ERROR);
                    stream.close(ce);
                } else if (!getErrorState().isIoAllowed()) {
                    StreamException se = new StreamException(sm.getString(
                            "streamProcessor.error.stream", stream.getConnectionId(),
                            stream.getIdentifier()), Http2Error.INTERNAL_ERROR,
                            stream.getIdentifier().intValue());
                    stream.close(se);
                }
            }
        } catch (Exception e) {
            ConnectionException ce = new ConnectionException(sm.getString(
                    "streamProcessor.error.connection", stream.getConnectionId(),
                    stream.getIdentifier()), Http2Error.INTERNAL_ERROR);
            ce.initCause(e);
            stream.close(ce);
        } finally {
            ContainerThreadMarker.clear();
        }
    }


    @Override
    public void action(ActionCode actionCode, Object param) {
        switch (actionCode) {
        // 'Normal' servlet support
        case COMMIT: {
            if (!response.isCommitted()) {
                try {
                    response.setCommitted(true);
                    stream.writeHeaders();
                } catch (IOException ioe) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
                }
            }
            break;
        }
        case CLOSE: {
            action(ActionCode.COMMIT, null);
            try {
                stream.getOutputBuffer().close();
            } catch (IOException ioe) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
            }
            break;
        }
        case ACK: {
            if (!response.isCommitted() && request.hasExpectation()) {
                try {
                    stream.writeAck();
                } catch (IOException ioe) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
                }
            }
            break;
        }
        case CLIENT_FLUSH: {
            action(ActionCode.COMMIT, null);
            try {
                stream.flushData();
            } catch (IOException ioe) {
                response.setErrorException(ioe);
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
            }
            break;
        }
        case AVAILABLE: {
            request.setAvailable(stream.getInputBuffer().available());
            break;
        }
        case REQ_SET_BODY_REPLAY: {
            ByteChunk body = (ByteChunk) param;
            stream.getInputBuffer().insertReplayedBody(body);
            stream.receivedEndOfStream();
            break;
        }
        case RESET: {
            stream.getOutputBuffer().reset();
            break;
        }

        // Error handling
        case IS_ERROR: {
            ((AtomicBoolean) param).set(getErrorState().isError());
            break;
        }
        case CLOSE_NOW: {
            // No need to block further output. This is called by the error
            // reporting valve if the response is already committed. It will
            // flush any remaining response data before this call.
            // Setting the error state will then cause this stream to be reset.
            setErrorState(ErrorState.CLOSE_NOW,  null);
            break;
        }
        case DISABLE_SWALLOW_INPUT: {
            // NO-OP
            // HTTP/2 has to swallow any input received to ensure that the flow
            // control windows are correctly tracked.
            break;
        }
        case END_REQUEST: {
            // NO-OP
            // This action is geared towards handling HTTP/1.1 expectations and
            // keep-alive. Does not apply to HTTP/2 streams.
            break;
        }

        // Request attribute support
        case REQ_HOST_ADDR_ATTRIBUTE: {
            request.remoteAddr().setString(socketWrapper.getRemoteAddr());
            break;
        }
        case REQ_HOST_ATTRIBUTE: {
            request.remoteHost().setString(socketWrapper.getRemoteHost());
            break;
        }
        case REQ_LOCALPORT_ATTRIBUTE: {
            request.setLocalPort(socketWrapper.getLocalPort());
            break;
        }
        case REQ_LOCAL_ADDR_ATTRIBUTE: {
            request.localAddr().setString(socketWrapper.getLocalAddr());
            break;
        }
        case REQ_LOCAL_NAME_ATTRIBUTE: {
            request.localName().setString(socketWrapper.getLocalName());
            break;
        }
        case REQ_REMOTEPORT_ATTRIBUTE: {
            request.setRemotePort(socketWrapper.getRemotePort());
            break;
        }

        // SSL request attribute support
        case REQ_SSL_ATTRIBUTE: {
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
                        request.setAttribute(SSLSupport.KEY_SIZE_KEY, sslO);
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
                log.warn(sm.getString("streamProcessor.ssl.error"), e);
            }
            break;
        }
        case REQ_SSL_CERTIFICATE: {
            // No re-negotiation support in HTTP/2. Either the certificate is
            // available or it isn't.
            try {
                if (sslSupport != null) {
                    Object sslO = sslSupport.getCipherSuite();
                    sslO = sslSupport.getPeerCertificateChain();
                    if (sslO != null) {
                        request.setAttribute(SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                }
            } catch (Exception e) {
                log.warn(sm.getString("streamProcessor.ssl.error"), e);
            }
            break;
        }

        // Servlet 3.0 asynchronous support
        case ASYNC_START: {
            asyncStateMachine.asyncStart((AsyncContextCallback) param);
            break;
        }
        case ASYNC_COMPLETE: {
            if (asyncStateMachine.asyncComplete()) {
                socketWrapper.getEndpoint().getExecutor().execute(this);
            }
            break;
        }
        case ASYNC_DISPATCH: {
            if (asyncStateMachine.asyncDispatch()) {
                socketWrapper.getEndpoint().getExecutor().execute(this);
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

        // Servlet 3.1 non-blocking I/O
        case REQUEST_BODY_FULLY_READ: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(stream.getInputBuffer().isRequestBodyFullyRead());
            break;
        }
        case NB_READ_INTEREST: {
            stream.getInputBuffer().registerReadInterest();
            break;
        }
        case NB_WRITE_INTEREST: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(stream.getOutputBuffer().isReady());
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
            socketWrapper.getEndpoint().getExecutor().execute(this);
            break;
        }

        // Servlet 4.0 Push requests
        case PUSH_REQUEST: {
            try {
                stream.push((Request) param);
            } catch (IOException ioe) {
                response.setErrorException(ioe);
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
            }
            break;
        }

        // Unsupported / illegal under HTTP/2
        case UPGRADE:
            throw new UnsupportedOperationException(
                    sm.getString("streamProcessor.httpupgrade.notsupported"));
        }
    }


    @Override
    public void recycle() {
        // StreamProcessor instances are not re-used.
        // Clear fields that can be cleared to aid GC and trigger NPEs if this
        // is reused
        setSocketWrapper(null);
        setAdapter(null);
    }


    @Override
    public boolean isUpgrade() {
        return false;
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    public void pause() {
        // NO-OP. Handled by the Http2UpgradeHandler
    }


    @Override
    public SocketState service(SocketWrapperBase<?> socket) throws IOException {
        try {
            adapter.service(request, response);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("streamProcessor.service.error"), e);
            }
            setErrorState(ErrorState.CLOSE_NOW, e);
        }

        if (getErrorState().isError()) {
            action(ActionCode.CLOSE, null);
            request.updateCounters();
            return SocketState.CLOSED;
        } else if (isAsync()) {
            return SocketState.LONG;
        } else {
            action(ActionCode.CLOSE, null);
            request.updateCounters();
            return SocketState.CLOSED;
        }
    }


    @Override
    protected boolean flushBufferedWrite() throws IOException {
        if (stream.getOutputBuffer().flush(false)) {
            // The buffer wasn't fully flushed so re-register the
            // stream for write. Note this does not go via the
            // Response since the write registration state at
            // that level should remain unchanged. Once the buffer
            // has been emptied then the code below will call
            // dispatch() which will enable the
            // Response to respond to this event.
            if (stream.getOutputBuffer().isReady()) {
                // Unexpected
                throw new IllegalStateException();
            }
            return true;
        }
        return false;
    }


    @Override
    protected SocketState dispatchEndRequest() {
        return SocketState.CLOSED;
    }


    @Override
    public UpgradeToken getUpgradeToken() {
        // Should never happen
        throw new IllegalStateException(sm.getString("streamProcessor.httpupgrade.notsupported"));
    }


    @Override
    public ByteBuffer getLeftoverInput() {
        // Should never happen
        throw new IllegalStateException(sm.getString("streamProcessor.httpupgrade.notsupported"));
    }
}

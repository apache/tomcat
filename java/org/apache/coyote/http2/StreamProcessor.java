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
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpUpgradeHandler;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.AsyncContextCallback;
import org.apache.coyote.ContainerThreadMarker;
import org.apache.coyote.ErrorState;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
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
    private Set<DispatchType> dispatches = new CopyOnWriteArraySet<>();

    private volatile SSLSupport sslSupport;


    public StreamProcessor(Stream stream, Adapter adapter, SocketWrapperBase<?> socketWrapper) {
        super(stream.getCoyoteRequest(), stream.getCoyoteResponse());
        this.stream = stream;
        setAdapter(adapter);
        setSocketWrapper(socketWrapper);
    }


    @Override
    public synchronized void run() {
        process(SocketStatus.OPEN_READ);
    }


    private synchronized void process(SocketStatus status) {
        // HTTP/2 equivalent of AbstractConnectionHandler#process() without the
        // socket <-> processor mapping
        ContainerThreadMarker.set();
        SocketState state = SocketState.CLOSED;
        try {
            Iterator<DispatchType> dispatches = getIteratorAndClearDispatches();
            do {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("streamProcessor.process.loopstart",
                            stream.getConnectionId(), stream.getIdentifier(), status, dispatches));
                }
                // TODO CLOSE_NOW ?
                if (dispatches != null) {
                    DispatchType nextDispatch = dispatches.next();
                    state = dispatch(nextDispatch.getSocketStatus());
                // TODO DISCONNECT ?
                } else if (isAsync()) {
                    state = dispatch(status);
                } else if (state == SocketState.ASYNC_END) {
                    state = dispatch(status);
                } else {
                    state = process((SocketWrapperBase<?>) null);
                }

                if (state != SocketState.CLOSED && isAsync()) {
                    state = asyncStateMachine.asyncPostProcess();
                }

                if (dispatches == null || !dispatches.hasNext()) {
                    // Only returns non-null iterator if there are
                    // dispatches to process.
                    dispatches = getIteratorAndClearDispatches();
                }
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("streamProcessor.process.loopend",
                            stream.getConnectionId(), stream.getIdentifier(), state, dispatches));
                }
            } while (state == SocketState.ASYNC_END ||
                    dispatches != null && state != SocketState.CLOSED);

            if (state == SocketState.CLOSED) {
                // TODO
            }
        } catch (Exception e) {
            // TODO
            e.printStackTrace();
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
                response.setCommitted(true);
                stream.writeHeaders();
            }
            break;
        }
        case CLOSE: {
            // Tell the output buffer there will be no more data
            stream.getOutputBuffer().close();
            // Then flush it
            action(ActionCode.CLIENT_FLUSH, null);
            break;
        }
        case CLIENT_FLUSH: {
            action(ActionCode.COMMIT, null);
            try {
                stream.flushData();
            } catch (IOException ioe) {
                response.setErrorException(ioe);
                // TODO: Shut stream down?
            }
            break;
        }
        case IS_ERROR: {
            ((AtomicBoolean) param).set(getErrorState().isError());
            break;
        }
        case AVAILABLE: {
            request.setAvailable(stream.getInputBuffer().available());
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
            // TODO
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
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(stream.getInputBuffer().isReady());
            break;
        }
        case NB_WRITE_INTEREST: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(stream.getOutputBuffer().isReady());
            break;
        }
        case DISPATCH_READ: {
            dispatches.add(DispatchType.NON_BLOCKING_READ);
            break;
        }
        case DISPATCH_WRITE: {
            dispatches.add(DispatchType.NON_BLOCKING_WRITE);
            break;
        }
        case DISPATCH_EXECUTE: {
            socketWrapper.getEndpoint().getExecutor().execute(this);
            break;
        }

        // Unsupported / illegal under HTTP/2
        case UPGRADE:
            throw new UnsupportedOperationException(
                    sm.getString("streamProcessor.httpupgrade.notsupported"));

        // Unimplemented / to review
        case ACK:
        case CLOSE_NOW:
        case DISABLE_SWALLOW_INPUT:
        case END_REQUEST:
        case REQ_SET_BODY_REPLAY:
        case RESET:
            log.info("TODO: Implement [" + actionCode + "] for HTTP/2");
            break;
        }
    }


    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }


    @Override
    public void recycle() {
        // StreamProcessor instances are not re-used.
        // Clear fields that can be cleared to aid GC and trigger NPEs if this
        // is reused
        setSocketWrapper(null);
        setAdapter(null);
        setClientCertProvider(null);
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
    public SocketState process(SocketWrapperBase<?> socket) throws IOException {
        try {
            adapter.service(request, response);
        } catch (Exception e) {
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
    public SocketState dispatch(SocketStatus status) {

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
            setErrorState(ErrorState.CLOSE_NOW, e);
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
    public void clearDispatches() {
        synchronized (dispatches) {
            dispatches.clear();
        }
    }


    @Override
    public HttpUpgradeHandler getHttpUpgradeHandler() {
        // Should never happen
        throw new IllegalStateException(sm.getString("streamProcessor.httpupgrade.notsupported"));
    }


    @Override
    public ByteBuffer getLeftoverInput() {
        // Should never happen
        throw new IllegalStateException(sm.getString("streamProcessor.httpupgrade.notsupported"));
    }
}

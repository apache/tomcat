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

import javax.servlet.http.HttpUpgradeHandler;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.AsyncContextCallback;
import org.apache.coyote.AsyncStateMachine;
import org.apache.coyote.ContainerThreadMarker;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class StreamProcessor extends AbstractProcessor implements Runnable {

    private static final Log log = LogFactory.getLog(StreamProcessor.class);
    private static final StringManager sm = StringManager.getManager(StreamProcessor.class);

    private final Stream stream;
    private final AsyncStateMachine asyncStateMachine;

    private volatile SSLSupport sslSupport;


    public StreamProcessor(Stream stream, Adapter adapter, SocketWrapperBase<?> socketWrapper) {
        super(stream.getCoyoteRequest(), stream.getCoyoteResponse());
        this.stream = stream;
        asyncStateMachine = new AsyncStateMachine(this);
        setAdapter(adapter);
        setSocketWrapper(socketWrapper);
    }


    @Override
    public void run() {
        // HTTP/2 equivalent of AbstractConnectionHandler#process()
        ContainerThreadMarker.set();
        SocketState state = SocketState.CLOSED;
        try {
            do {
                if (asyncStateMachine.isAsync()) {
                    adapter.asyncDispatch(request, response, SocketStatus.OPEN_READ);
                } else {
                    adapter.service(request, response);
                }

                if (asyncStateMachine.isAsync()) {
                    state = asyncStateMachine.asyncPostProcess();
                } else {
                    response.action(ActionCode.CLOSE, null);
                }
            } while (state == SocketState.ASYNC_END);
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
            stream.flushData();
            break;
        }
        case IS_ERROR: {
            ((AtomicBoolean) param).set(getErrorState().isError());
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

        // Unsupported / illegal under HTTP/2
        case UPGRADE:
            throw new UnsupportedOperationException(
                    sm.getString("streamProcessor.httpupgrade.notsupported"));

        // Unimplemented / to review
        case ACK:
        case AVAILABLE:
        case CLOSE_NOW:
        case DISABLE_SWALLOW_INPUT:
        case DISPATCH_EXECUTE:
        case DISPATCH_READ:
        case DISPATCH_WRITE:
        case END_REQUEST:
        case NB_READ_INTEREST:
        case NB_WRITE_INTEREST:
        case REQUEST_BODY_FULLY_READ:
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
        // Should never happen
        throw new IllegalStateException(sm.getString("streamProcessor.httpupgrade.notsupported"));
    }


    @Override
    public SocketState dispatch(SocketStatus status) {
        // Should never happen
        throw new IllegalStateException(sm.getString("streamProcessor.httpupgrade.notsupported"));
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

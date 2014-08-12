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
package org.apache.coyote.http11;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLEngine;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ErrorState;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.Nio2Channel;
import org.apache.tomcat.util.net.Nio2Endpoint;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SecureNio2Channel;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Processes HTTP requests.
 */
public class Http11Nio2Processor extends AbstractHttp11Processor<Nio2Channel> {

    private static final Log log = LogFactory.getLog(Http11Nio2Processor.class);
    @Override
    protected Log getLog() {
        return log;
    }


    /**
     * SSL information.
     */
    protected SSLSupport sslSupport;

    // ----------------------------------------------------------- Constructors


    public Http11Nio2Processor(int maxHttpHeaderSize, Nio2Endpoint endpoint,
            int maxTrailerSize, int maxExtensionSize, int maxSwallowSize) {

        super(endpoint);

        inputBuffer = new InternalNio2InputBuffer(request, maxHttpHeaderSize);
        request.setInputBuffer(inputBuffer);

        outputBuffer = new InternalNio2OutputBuffer(response, maxHttpHeaderSize);
        response.setOutputBuffer(outputBuffer);

        initializeFilters(maxTrailerSize, maxExtensionSize, maxSwallowSize);
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * Sendfile data.
     */
    protected Nio2Endpoint.SendfileData sendfileData = null;


    // --------------------------------------------------------- Public Methods

    @Override
    public SocketState event(SocketStatus status)
        throws IOException {

        long soTimeout = endpoint.getSoTimeout();

        RequestInfo rp = request.getRequestProcessor();
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            if (!getAdapter().event(request, response, status)) {
                setErrorState(ErrorState.CLOSE_NOW, null);
            }
            if (!getErrorState().isError()) {
                if (socketWrapper != null) {
                    socketWrapper.setComet(comet);
                    if (comet) {
                        Integer comettimeout = (Integer) request.getAttribute(
                                org.apache.coyote.Constants.COMET_TIMEOUT_ATTR);
                        if (comettimeout != null) {
                            socketWrapper.setTimeout(comettimeout.longValue());
                        }
                    } else {
                        //reset the timeout
                        if (keepAlive) {
                            socketWrapper.setTimeout(keepAliveTimeout);
                        } else {
                            socketWrapper.setTimeout(soTimeout);
                        }
                    }

                }
            }
        } catch (InterruptedIOException e) {
            setErrorState(ErrorState.CLOSE_NOW, e);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // 500 - Internal Server Error
            response.setStatus(500);
            setErrorState(ErrorState.CLOSE_NOW, t);
            getAdapter().log(request, response, 0);
            log.error(sm.getString("http11processor.request.process"), t);
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (getErrorState().isError() || status==SocketStatus.STOP) {
            return SocketState.CLOSED;
        } else if (!comet) {
            if (keepAlive) {
                inputBuffer.nextRequest();
                outputBuffer.nextRequest();
                if (((InternalNio2InputBuffer) inputBuffer).isPending()) {
                    // Following comet processing, a read is still pending, so
                    // keep the processor associated
                    return SocketState.LONG;
                } else {
                    return SocketState.OPEN;
                }
            } else {
                return SocketState.CLOSED;
            }
        } else {
            return SocketState.LONG;
        }
    }

    @Override
    public SocketState asyncDispatch(SocketStatus status) {
        SocketState state = super.asyncDispatch(status);
        if (state == SocketState.OPEN && ((InternalNio2InputBuffer) inputBuffer).isPending()) {
            // Following async processing, a read is still pending, so
            // keep the processor associated
            return SocketState.LONG;
        } else {
            return state;
        }
    }

    @Override
    protected void registerForEvent(boolean read, boolean write) {
        if (read) {
            ((InternalNio2InputBuffer) inputBuffer).registerReadInterest();
        }
        if (write) {
            ((InternalNio2OutputBuffer) outputBuffer).registerWriteInterest();
        }
    }


    @Override
    protected void resetTimeouts() {
        if (!getErrorState().isError() && socketWrapper != null &&
                asyncStateMachine.isAsyncDispatching()) {
            long soTimeout = endpoint.getSoTimeout();

            //reset the timeout
            if (keepAlive) {
                socketWrapper.setTimeout(keepAliveTimeout);
            } else {
                socketWrapper.setTimeout(soTimeout);
            }
        }
    }


    @Override
    protected boolean disableKeepAlive() {
        return false;
    }


    @Override
    protected void setRequestLineReadTimeout() throws IOException {
        // socket.setTimeout()
        //     - timeout used by poller
        // socket.getSocket().getIOChannel().socket().setSoTimeout()
        //     - timeout used for blocking reads

        // When entering the processing loop there will always be data to read
        // so no point changing timeouts at this point

        // For the second and subsequent executions of the processing loop, a
        // non-blocking read is used so again no need to set the timeouts

        // Because NIO supports non-blocking reading of the request line and
        // headers the timeouts need to be set when returning the socket to
        // the poller rather than here.

        // NO-OP
    }


    @Override
    protected boolean handleIncompleteRequestLineRead() {
        // Haven't finished reading the request so keep the socket
        // open
        openSocket = true;
        // Check to see if we have read any of the request line yet
        if (((InternalNio2InputBuffer)
                inputBuffer).getParsingRequestLinePhase() < 1) {
            if (socketWrapper.getLastAccess() > -1 || keptAlive) {
                // Haven't read the request line and have previously processed a
                // request. Must be keep-alive. Make sure poller uses keepAlive.
                socketWrapper.setTimeout(endpoint.getKeepAliveTimeout());
            }
        } else {
            // Started to read request line.
            if (request.getStartTime() < 0) {
                request.setStartTime(System.currentTimeMillis());
            }
            if (endpoint.isPaused()) {
                // Partially processed the request so need to respond
                response.setStatus(503);
                setErrorState(ErrorState.CLOSE_CLEAN, null);
                getAdapter().log(request, response, 0);
                return false;
            } else {
                // Need to keep processor associated with socket
                readComplete = false;
                // Make sure poller uses soTimeout from here onwards
                socketWrapper.setTimeout(endpoint.getSoTimeout());
            }
        }
        return true;
    }


    @Override
    protected void setSocketTimeout(int timeout) throws IOException {
        socketWrapper.setTimeout(timeout);
    }


    @Override
    protected void setCometTimeouts(SocketWrapper<Nio2Channel> socketWrapper) {
        if (socketWrapper != null)  {
            socketWrapper.setComet(comet);
            if (comet) {
                Integer comettimeout = (Integer) request.getAttribute(
                        org.apache.coyote.Constants.COMET_TIMEOUT_ATTR);
                if (comettimeout != null) {
                    socketWrapper.setTimeout(comettimeout.longValue());
                }
            }
        }
    }


    @Override
    protected boolean breakKeepAliveLoop(
            SocketWrapper<Nio2Channel> socketWrapper) {
        openSocket = keepAlive;
        // Do sendfile as needed: add socket to sendfile and end
        if (sendfileData != null && !getErrorState().isError()) {
            ((Nio2Endpoint.Nio2SocketWrapper) socketWrapper).setSendfileData(sendfileData);
            sendfileData.keepAlive = keepAlive;
            switch (((Nio2Endpoint) endpoint)
                    .processSendfile((Nio2Endpoint.Nio2SocketWrapper) socketWrapper)) {
            case DONE:
                return false;
            case ERROR:
                // Write failed
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("http11processor.sendfile.error"));
                }
                setErrorState(ErrorState.CLOSE_NOW, null);
                return true;
            case PENDING:
                sendfileInProgress = true;
                return true;
            }
        }
        return false;
    }


    @Override
    public void recycleInternal() {
        socketWrapper = null;
        sendfileData = null;
    }


    // ----------------------------------------------------- ActionHook Methods


    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @Override
    @SuppressWarnings("incomplete-switch") // Other cases are handled by action()
    public void actionInternal(ActionCode actionCode, Object param) {

        switch (actionCode) {
        case REQ_HOST_ADDR_ATTRIBUTE: {
            if (socketWrapper == null || socketWrapper.getSocket() == null) {
                request.remoteAddr().recycle();
            } else {
                if (socketWrapper.getRemoteAddr() == null) {
                    InetAddress inetAddr = null;
                    try {
                        inetAddr = ((InetSocketAddress) socketWrapper.getSocket().getIOChannel().getRemoteAddress()).getAddress();
                    } catch (IOException e) {
                        // Ignore
                    }
                    if (inetAddr != null) {
                        socketWrapper.setRemoteAddr(inetAddr.getHostAddress());
                    }
                }
                request.remoteAddr().setString(socketWrapper.getRemoteAddr());
            }
            break;
        }
        case REQ_LOCAL_NAME_ATTRIBUTE: {
            if (socketWrapper == null || socketWrapper.getSocket() == null) {
                request.localName().recycle();
            } else {
                if (socketWrapper.getLocalName() == null) {
                    InetAddress inetAddr = null;
                    try {
                        inetAddr = ((InetSocketAddress) socketWrapper.getSocket().getIOChannel().getLocalAddress()).getAddress();
                    } catch (IOException e) {
                        // Ignore
                    }
                    if (inetAddr != null) {
                        socketWrapper.setLocalName(inetAddr.getHostName());
                    }
                }
                request.localName().setString(socketWrapper.getLocalName());
            }
            break;
        }
        case REQ_HOST_ATTRIBUTE: {
            if (socketWrapper == null || socketWrapper.getSocket() == null) {
                request.remoteHost().recycle();
            } else {
                if (socketWrapper.getRemoteHost() == null) {
                    InetAddress inetAddr = null;
                    try {
                        inetAddr = ((InetSocketAddress) socketWrapper.getSocket().getIOChannel().getRemoteAddress()).getAddress();
                    } catch (IOException e) {
                        // Ignore
                    }
                    if (inetAddr != null) {
                        socketWrapper.setRemoteHost(inetAddr.getHostName());
                    }
                    if (socketWrapper.getRemoteHost() == null) {
                        if (socketWrapper.getRemoteAddr() == null &&
                                inetAddr != null) {
                            socketWrapper.setRemoteAddr(inetAddr.getHostAddress());
                        }
                        if (socketWrapper.getRemoteAddr() != null) {
                            socketWrapper.setRemoteHost(socketWrapper.getRemoteAddr());
                        }
                    }
                }
                request.remoteHost().setString(socketWrapper.getRemoteHost());
            }
            break;
        }
        case REQ_LOCAL_ADDR_ATTRIBUTE: {
            if (socketWrapper == null || socketWrapper.getSocket() == null) {
                request.localAddr().recycle();
            } else {
                if (socketWrapper.getLocalAddr() == null) {
                    try {
                        socketWrapper.setLocalAddr(
                                ((InetSocketAddress) socketWrapper.getSocket().getIOChannel().getLocalAddress()).getAddress().getHostAddress());
                    } catch (IOException e) {
                        // Ignore
                    }
                }
                request.localAddr().setString(socketWrapper.getLocalAddr());
            }
            break;
        }
        case REQ_REMOTEPORT_ATTRIBUTE: {
            if (socketWrapper == null || socketWrapper.getSocket() == null) {
                request.setRemotePort(0);
            } else {
                if (socketWrapper.getRemotePort() == -1) {
                    try {
                        socketWrapper.setRemotePort(((InetSocketAddress) socketWrapper.getSocket().getIOChannel().getRemoteAddress()).getPort());
                    } catch (IOException e) {
                        // Ignore
                    }
                }
                request.setRemotePort(socketWrapper.getRemotePort());
            }
            break;
        }
        case REQ_LOCALPORT_ATTRIBUTE: {
            if (socketWrapper == null || socketWrapper.getSocket() == null) {
                request.setLocalPort(0);
            } else {
                if (socketWrapper.getLocalPort() == -1) {
                    try {
                        socketWrapper.setLocalPort(((InetSocketAddress) socketWrapper.getSocket().getIOChannel().getLocalAddress()).getPort());
                    } catch (IOException e) {
                        // Ignore
                    }
                }
                request.setLocalPort(socketWrapper.getLocalPort());
            }
            break;
        }
        case REQ_SSL_ATTRIBUTE: {
            try {
                if (sslSupport != null) {
                    Object sslO = sslSupport.getCipherSuite();
                    if (sslO != null) {
                        request.setAttribute
                            (SSLSupport.CIPHER_SUITE_KEY, sslO);
                    }
                    sslO = sslSupport.getPeerCertificateChain(false);
                    if (sslO != null) {
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                    sslO = sslSupport.getKeySize();
                    if (sslO != null) {
                        request.setAttribute
                            (SSLSupport.KEY_SIZE_KEY, sslO);
                    }
                    sslO = sslSupport.getSessionId();
                    if (sslO != null) {
                        request.setAttribute
                            (SSLSupport.SESSION_ID_KEY, sslO);
                    }
                    request.setAttribute(SSLSupport.SESSION_MGR, sslSupport);
                }
            } catch (Exception e) {
                log.warn(sm.getString("http11processor.socket.ssl"), e);
            }
            break;
        }
        case REQ_SSL_CERTIFICATE: {
            if (sslSupport != null && socketWrapper.getSocket() != null) {
                boolean force = ((Boolean) param).booleanValue();
                if (force) {
                    /* Forced triggers a handshake so consume and buffer the
                     * request body, so that it does not interfere with the
                     * client's handshake messages
                     */
                    InputFilter[] inputFilters = inputBuffer.getFilters();
                    ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER])
                            .setLimit(maxSavePostSize);
                    inputBuffer.addActiveFilter(inputFilters[Constants.BUFFERED_FILTER]);
                }
                SecureNio2Channel sslChannel = (SecureNio2Channel) socketWrapper.getSocket();
                SSLEngine engine = sslChannel.getSslEngine();
                if (!engine.getNeedClientAuth() && force) {
                    // Need to re-negotiate SSL connection
                    engine.setNeedClientAuth(true);
                    try {
                        sslChannel.rehandshake();
                        sslSupport = ((Nio2Endpoint)endpoint).getHandler()
                                .getSslImplementation().getSSLSupport(
                                        engine.getSession());
                    } catch (IOException ioe) {
                        log.warn(sm.getString("http11processor.socket.sslreneg"), ioe);
                    }
                }

                try {
                    // use force=false since re-negotiation is handled above
                    // (and it is a NO-OP for NIO anyway)
                    Object sslO = sslSupport.getPeerCertificateChain(false);
                    if (sslO != null) {
                        request.setAttribute(SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.ssl"), e);
                }
            }
            break;
        }
        case COMET_BEGIN: {
            comet = true;
            break;
        }
        case COMET_END: {
            comet = false;
            break;
        }
        case COMET_CLOSE: {
            if (socketWrapper == null || socketWrapper.getSocket() == null) {
                return;
            }
            RequestInfo rp = request.getRequestProcessor();
            if (rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE) {
                // Close event for this processor triggered by request
                // processing in another processor, a non-Tomcat thread (i.e.
                // an application controlled thread) or similar.
                endpoint.processSocket(this.socketWrapper, SocketStatus.OPEN_READ, true);
            }
            break;
        }
        case COMET_SETTIMEOUT: {
            if (param == null) {
                return;
            }
            if (socketWrapper == null) {
                return;
            }
            long timeout = ((Long)param).longValue();
            //if we are not piggy backing on a worker thread, set the timeout
            RequestInfo rp = request.getRequestProcessor();
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) {
                socketWrapper.setTimeout(timeout);
            }
            break;
        }
        }
    }


    // ------------------------------------------------------ Protected Methods


    @Override
    protected void prepareRequestInternal() {
        sendfileData = null;
    }

    @Override
    protected boolean prepareSendfile(OutputFilter[] outputFilters) {
        String fileName = (String) request.getAttribute(
                org.apache.coyote.Constants.SENDFILE_FILENAME_ATTR);
        if (fileName != null) {
            // No entity body sent here
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
            sendfileData = new Nio2Endpoint.SendfileData();
            sendfileData.fileName = fileName;
            sendfileData.pos = ((Long) request.getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_START_ATTR)).longValue();
            sendfileData.length = ((Long) request.getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_END_ATTR)).longValue() - sendfileData.pos;
            return true;
        }
        return false;
    }

    @Override
    protected AbstractInputBuffer<Nio2Channel> getInputBuffer() {
        return inputBuffer;
    }

    @Override
    protected AbstractOutputBuffer<Nio2Channel> getOutputBuffer() {
        return outputBuffer;
    }

    /**
     * Set the SSL information for this HTTP connection.
     */
    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }
}

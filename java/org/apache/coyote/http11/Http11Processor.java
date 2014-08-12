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

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.coyote.ActionCode;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.JIoEndpoint;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Processes HTTP requests.
 *
 * @author Remy Maucherat
 */
public class Http11Processor extends AbstractHttp11Processor<Socket> {

    private static final Log log = LogFactory.getLog(Http11Processor.class);
    @Override
    protected Log getLog() {
        return log;
    }

   // ------------------------------------------------------------ Constructor


    public Http11Processor(int headerBufferSize, JIoEndpoint endpoint,
            int maxTrailerSize, int maxExtensionSize, int maxSwallowSize) {

        super(endpoint);

        inputBuffer = new InternalInputBuffer(request, headerBufferSize);
        request.setInputBuffer(inputBuffer);

        outputBuffer = new InternalOutputBuffer(response, headerBufferSize);
        response.setOutputBuffer(outputBuffer);

        initializeFilters(maxTrailerSize, maxExtensionSize, maxSwallowSize);
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * SSL information.
     */
    protected SSLSupport sslSupport;


    /**
     * The percentage of threads that have to be in use before keep-alive is
     * disabled to aid scalability.
     */
    private int disableKeepAlivePercentage = 75;

    // --------------------------------------------------------- Public Methods


    /**
     * Set the SSL information for this HTTP connection.
     */
    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }


    public int getDisableKeepAlivePercentage() {
        return disableKeepAlivePercentage;
    }


    public void setDisableKeepAlivePercentage(int disableKeepAlivePercentage) {
        this.disableKeepAlivePercentage = disableKeepAlivePercentage;
    }


    @Override
    protected boolean disableKeepAlive() {
        int threadRatio = -1;
        // These may return zero or negative values
        // Only calculate a thread ratio when both are >0 to ensure we get a
        // sensible result
        int maxThreads, threadsBusy;
        if ((maxThreads = endpoint.getMaxThreads()) > 0
                && (threadsBusy = endpoint.getCurrentThreadsBusy()) > 0) {
            threadRatio = (threadsBusy * 100) / maxThreads;
        }
        // Disable keep-alive if we are running low on threads
        if (threadRatio > getDisableKeepAlivePercentage()) {
            return true;
        }

        return false;
    }


    @Override
    protected void setRequestLineReadTimeout() throws IOException {

        /*
         * When there is no data in the buffer and this is not the first
         * request on this connection and timeouts are being used the
         * first read for this request may need a different timeout to
         * take account of time spent waiting for a processing thread.
         *
         * This is a little hacky but better than exposing the socket
         * and the timeout info to the InputBuffer
         */
        if (inputBuffer.lastValid == 0 && socketWrapper.getLastAccess() > -1) {
            int firstReadTimeout;
            if (keepAliveTimeout == -1) {
                firstReadTimeout = 0;
            } else {
                long queueTime =
                    System.currentTimeMillis() - socketWrapper.getLastAccess();

                if (queueTime >= keepAliveTimeout) {
                    // Queued for longer than timeout but there might be
                    // data so use shortest possible timeout
                    firstReadTimeout = 1;
                } else {
                    // Cast is safe since queueTime must be less than
                    // keepAliveTimeout which is an int
                    firstReadTimeout = keepAliveTimeout - (int) queueTime;
                }
            }
            socketWrapper.getSocket().setSoTimeout(firstReadTimeout);
            // Blocking IO so fill() always blocks
            if (!inputBuffer.fill(true)) {
                throw new EOFException(sm.getString("iib.eof.error"));
            }
            // Once the first byte has been read, the standard timeout should be
            // used so restore it here.
            if (endpoint.getSoTimeout()> 0) {
                setSocketTimeout(endpoint.getSoTimeout());
            } else {
                setSocketTimeout(0);
            }
        }
    }


    @Override
    protected boolean handleIncompleteRequestLineRead() {
        // Not used with BIO since it uses blocking reads
        return false;
    }


    @Override
    protected void setSocketTimeout(int timeout) throws IOException {
        socketWrapper.getSocket().setSoTimeout(timeout);
    }


    @Override
    protected void setCometTimeouts(SocketWrapper<Socket> socketWrapper) {
        // NO-OP for BIO
    }


    @Override
    protected boolean breakKeepAliveLoop(SocketWrapper<Socket> socketWrapper) {
        openSocket = keepAlive;
        // If we don't have a pipe-lined request allow this thread to be
        // used by another connection
        if (inputBuffer.lastValid == 0) {
            return true;
        }
        return false;
    }


    @Override
    protected void registerForEvent(boolean read, boolean write) {
        // NO-OP for BIO
    }

    @Override
    protected void resetTimeouts() {
        // NO-OP for BIO
    }


    @Override
    protected void recycleInternal() {
        // Recycle
        this.socketWrapper = null;
        // Recycle ssl info
        sslSupport = null;
    }


    @Override
    public SocketState event(SocketStatus status) throws IOException {
        // Should never reach this code but in case we do...
        throw new IOException(
                sm.getString("http11processor.comet.notsupported"));
    }

    // ----------------------------------------------------- ActionHook Methods


    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @SuppressWarnings("incomplete-switch") // Other cases are handled by action()
    @Override
    public void actionInternal(ActionCode actionCode, Object param) {

        switch (actionCode) {
        case REQ_SSL_ATTRIBUTE: {
            try {
                if (sslSupport != null) {
                    Object sslO = sslSupport.getCipherSuite();
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.CIPHER_SUITE_KEY, sslO);
                    sslO = sslSupport.getPeerCertificateChain(false);
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    sslO = sslSupport.getKeySize();
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.KEY_SIZE_KEY, sslO);
                    sslO = sslSupport.getSessionId();
                    if (sslO != null)
                        request.setAttribute
                            (SSLSupport.SESSION_ID_KEY, sslO);
                    request.setAttribute(SSLSupport.SESSION_MGR, sslSupport);
                }
            } catch (Exception e) {
                log.warn(sm.getString("http11processor.socket.ssl"), e);
            }
            break;
        }
        case REQ_HOST_ADDR_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.remoteAddr().recycle();
            } else {
                if (socketWrapper.getRemoteAddr() == null) {
                    InetAddress inetAddr = socketWrapper.getSocket().getInetAddress();
                    if (inetAddr != null) {
                        socketWrapper.setRemoteAddr(inetAddr.getHostAddress());
                    }
                }
                request.remoteAddr().setString(socketWrapper.getRemoteAddr());
            }
            break;
        }
        case REQ_LOCAL_NAME_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.localName().recycle();
            } else {
                if (socketWrapper.getLocalName() == null) {
                    InetAddress inetAddr = socketWrapper.getSocket().getLocalAddress();
                    if (inetAddr != null) {
                        socketWrapper.setLocalName(inetAddr.getHostName());
                    }
                }
                request.localName().setString(socketWrapper.getLocalName());
            }
            break;
        }
        case REQ_HOST_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.remoteHost().recycle();
            } else {
                if (socketWrapper.getRemoteHost() == null) {
                    InetAddress inetAddr = socketWrapper.getSocket().getInetAddress();
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
            if (socketWrapper == null) {
                request.localAddr().recycle();
            } else {
                if (socketWrapper.getLocalAddr() == null) {
                    socketWrapper.setLocalAddr(
                            socketWrapper.getSocket().getLocalAddress().getHostAddress());
                }
                request.localAddr().setString(socketWrapper.getLocalAddr());
            }
            break;
        }
        case REQ_REMOTEPORT_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.setRemotePort(0);
            } else {
                if (socketWrapper.getRemotePort() == -1) {
                    socketWrapper.setRemotePort(socketWrapper.getSocket().getPort());
                }
                request.setRemotePort(socketWrapper.getRemotePort());
            }
            break;
        }
        case REQ_LOCALPORT_ATTRIBUTE: {
            if (socketWrapper == null) {
                request.setLocalPort(0);
            } else {
                if (socketWrapper.getLocalPort() == -1) {
                    socketWrapper.setLocalPort(socketWrapper.getSocket().getLocalPort());
                }
                request.setLocalPort(socketWrapper.getLocalPort());
            }
            break;
        }
        case REQ_SSL_CERTIFICATE: {
            if (sslSupport != null) {
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
                try {
                    Object sslO = sslSupport.getPeerCertificateChain(force);
                    if( sslO != null) {
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.ssl"), e);
                }
            }
            break;
        }
        }
    }


    // ------------------------------------------------------ Protected Methods


    @Override
    protected void prepareRequestInternal() {
        // NOOP for BIO
    }

    @Override
    protected boolean prepareSendfile(OutputFilter[] outputFilters) {
        // Should never, ever call this code
        Exception e = new Exception();
        log.error(sm.getString("http11processor.neverused"), e);
        return false;
    }

    @Override
    protected AbstractInputBuffer<Socket> getInputBuffer() {
        return inputBuffer;
    }

    @Override
    protected AbstractOutputBuffer<Socket> getOutputBuffer() {
        return outputBuffer;
    }

    /**
     * Set the socket buffer flag.
     */
    @Override
    public void setSocketBuffer(int socketBuffer) {
        super.setSocketBuffer(socketBuffer);
        outputBuffer.setSocketBuffer(socketBuffer);
    }
}

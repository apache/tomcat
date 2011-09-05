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
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.coyote.ActionCode;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.JIoEndpoint;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Processes HTTP requests.
 *
 * @author Remy Maucherat
 * @author fhanik
 */
public class Http11Processor extends AbstractHttp11Processor<Socket> {

    private static final Log log = LogFactory.getLog(Http11Processor.class);
    @Override
    protected Log getLog() {
        return log;
    }

   // ------------------------------------------------------------ Constructor


    public Http11Processor(int headerBufferSize, JIoEndpoint endpoint,
            int maxTrailerSize) {

        super(endpoint);
        
        inputBuffer = new InternalInputBuffer(request, headerBufferSize);
        request.setInputBuffer(inputBuffer);

        outputBuffer = new InternalOutputBuffer(response, headerBufferSize);
        response.setOutputBuffer(outputBuffer);

        initializeFilters(maxTrailerSize);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Input.
     */
    protected InternalInputBuffer inputBuffer = null;


    /**
     * Output.
     */
    protected InternalOutputBuffer outputBuffer = null;


    /**
     * SSL information.
     */
    protected SSLSupport sslSupport;

    
    /**
     * Socket associated with the current connection.
     */
    protected SocketWrapper<Socket> socket;


    /**
     * The percentage of threads that have to be in use before keep-alive is
     * disabled to aid scalability.
     */
    private int disableKeepAlivePercentage = 75;

    // --------------------------------------------------------- Public Methods


    /**
     * Set the SSL information for this HTTP connection.
     */
    public void setSSLSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }


    public int getDisableKeepAlivePercentage() {
        return disableKeepAlivePercentage;
    }


    public void setDisableKeepAlivePercentage(int disableKeepAlivePercentage) {
        this.disableKeepAlivePercentage = disableKeepAlivePercentage;
    }


    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @param socketWrapper Socket from which the HTTP requests will be read
     *               and the HTTP responses will be written.
     *  
     * @throws IOException error during an I/O operation
     */
    @Override
    public SocketState process(SocketWrapper<Socket> socketWrapper)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the I/O
        this.socket = socketWrapper;
        inputBuffer.init(socketWrapper, endpoint);
        outputBuffer.init(socketWrapper, endpoint);

        // Flags
        error = false;
        keepAlive = true;
        comet = false;
        openSocket = false;
        sendfileInProgress = false;
        readComplete = true;

        int soTimeout = endpoint.getSoTimeout();

        if (disableKeepAlive()) {
            socketWrapper.setKeepAliveLeft(0);
        }

        boolean keptAlive = socketWrapper.isKeptAlive();

        while (!error && keepAlive && !comet && !isAsync() &&
                !endpoint.isPaused()) {

            // Parsing the request header
            try {
                int standardTimeout = 0;
                if (keptAlive) {
                    if (keepAliveTimeout > 0) {
                        standardTimeout = keepAliveTimeout;
                    } else if (soTimeout > 0) {
                        standardTimeout = soTimeout;
                    }
                }
                /*
                 * When there is no data in the buffer and this is not the first
                 * request on this connection and timeouts are being used the
                 * first read for this request may need a different timeout to
                 * take account of time spent waiting for a processing thread.
                 * 
                 * This is a little hacky but better than exposing the socket
                 * and the timeout info to the InputBuffer
                 */
                if (inputBuffer.lastValid == 0 &&
                        socketWrapper.getLastAccess() > -1 &&
                        standardTimeout > 0) {

                    long queueTime = System.currentTimeMillis() -
                            socketWrapper.getLastAccess();
                    int firstReadTimeout;
                    if (queueTime >= standardTimeout) {
                        // Queued for longer than timeout but there might be
                        // data so use shortest possible timeout
                        firstReadTimeout = 1;
                    } else {
                        // Cast is safe since queueTime must be less than
                        // standardTimeout which is an int
                        firstReadTimeout = standardTimeout - (int) queueTime;
                    }
                    socket.getSocket().setSoTimeout(firstReadTimeout);
                    if (!inputBuffer.fill()) {
                        throw new EOFException(sm.getString("iib.eof.error"));
                    }
                }
                if (standardTimeout > 0) {
                    socket.getSocket().setSoTimeout(standardTimeout);
                }

                inputBuffer.parseRequestLine(false);
                if (endpoint.isPaused()) {
                    // 503 - Service unavailable
                    response.setStatus(503);
                    adapter.log(request, response, 0);
                    error = true;
                } else {
                    request.setStartTime(System.currentTimeMillis());
                    keptAlive = true;
                    // Reset timeout for reading headers
                    socket.getSocket().setSoTimeout(soTimeout);
                    inputBuffer.parseHeaders();
                    if (!disableUploadTimeout) {
                        socket.getSocket().setSoTimeout(connectionUploadTimeout);
                    }
                }
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("http11processor.header.parse"), e);
                }
                error = true;
                break;
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("http11processor.header.parse"), t);
                }
                // 400 - Bad Request
                response.setStatus(400);
                adapter.log(request, response, 0);
                error = true;
            }

            if (!error) {
                // Setting up filters, and parse some request headers
                rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
                try {
                    prepareRequest();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("http11processor.request.prepare"), t);
                    }
                    // 400 - Internal Server Error
                    response.setStatus(400);
                    adapter.log(request, response, 0);
                    error = true;
                }
            }

            if (maxKeepAliveRequests == 1) {
                keepAlive = false;
            } else if (maxKeepAliveRequests > 0 &&
                    socketWrapper.decrementKeepAlive() <= 0) {
                keepAlive = false;
            }

            // Process the request in the adapter
            if (!error) {
                try {
                    rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                    adapter.service(request, response);
                    // Handle when the response was committed before a serious
                    // error occurred.  Throwing a ServletException should both
                    // set the status to 500 and set the errorException.
                    // If we fail here, then the response is likely already
                    // committed, so we can't try and set headers.
                    if(keepAlive && !error) { // Avoid checking twice.
                        error = response.getErrorException() != null ||
                                (!isAsync() &&
                                statusDropsConnection(response.getStatus()));
                    }
                    setCometTimeouts(socketWrapper);
                } catch (InterruptedIOException e) {
                    error = true;
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("http11processor.request.process"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    adapter.log(request, response, 0);
                    error = true;
                }
            }

            // Finish the handling of the request
            rp.setStage(org.apache.coyote.Constants.STAGE_ENDINPUT);

            if(!isAsync() && !comet) {
                if (error) {
                    // If we know we are closing the connection, don't drain
                    // input. This way uploading a 100GB file doesn't tie up the
                    // thread if the servlet has rejected it.
                    inputBuffer.setSwallowInput(false);
                }
                endRequest();
            }

            rp.setStage(org.apache.coyote.Constants.STAGE_ENDOUTPUT);

            // If there was an error, make sure the request is counted as
            // and error, and update the statistics counter
            if (error) {
                response.setStatus(500);
            }
            request.updateCounters();

            // Next request
            if (!isAsync() || error) {
                inputBuffer.nextRequest();
                outputBuffer.nextRequest();
            }

            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);

            if (breakKeepAliveLoop(socketWrapper)) {
                break;
            }
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (error || endpoint.isPaused()) {
            return SocketState.CLOSED;
        } else if (comet || isAsync()) {
            return SocketState.LONG;
        } else {
            if (sendfileInProgress) {
                return SocketState.SENDFILE;
            } else {
                if (openSocket) {
                    if (readComplete) {
                        return SocketState.OPEN;
                    } else {
                        return SocketState.LONG;
                    }
                } else {
                    return SocketState.CLOSED;
                }
            }
        }
    }


    @Override
    protected void setCometTimeouts(SocketWrapper<Socket> socketWrapper) {
        // NO-OP for BIO
        return;
    }


    @Override
    protected boolean breakKeepAliveLoop(SocketWrapper<Socket> socketWrapper) {
        // If we don't have a pipe-lined request allow this thread to be
        // used by another connection
        if (inputBuffer.lastValid == 0) {
            return true;
        }
        return false;
    }

    
    @Override
    protected boolean disableKeepAlive() {
        int threadRatio = -1;   
        // These may return zero or negative values     
        // Only calculate a thread ratio when both are >0 to ensure we get a    
        // sensible result      
        if (endpoint.getCurrentThreadsBusy() >0 &&      
                endpoint.getMaxThreads() >0) {      
            threadRatio = (endpoint.getCurrentThreadsBusy() * 100)      
                    / endpoint.getMaxThreads();     
        }   
        // Disable keep-alive if we are running low on threads      
        if (threadRatio > getDisableKeepAlivePercentage()) {     
            return true;
        }
        
        return false;
    }


    @Override
    protected void resetTimeouts() {
        // NOOP for BIO
    }


    @Override
    protected void recycleInternal() {
        // Recycle
        this.socket = null;
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
    @Override
    public void actionInternal(ActionCode actionCode, Object param) {

        if (actionCode == ActionCode.CLOSE) {
            // Close
            // End the processing of the current request, and stop any further
            // transactions with the client

            try {
                outputBuffer.endRequest();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }

        } else if (actionCode == ActionCode.REQ_SSL_ATTRIBUTE ) {

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

        } else if (actionCode == ActionCode.REQ_HOST_ADDR_ATTRIBUTE) {

            if ((remoteAddr == null) && (socket != null)) {
                InetAddress inetAddr = socket.getSocket().getInetAddress();
                if (inetAddr != null) {
                    remoteAddr = inetAddr.getHostAddress();
                }
            }
            request.remoteAddr().setString(remoteAddr);

        } else if (actionCode == ActionCode.REQ_LOCAL_NAME_ATTRIBUTE) {

            if ((localName == null) && (socket != null)) {
                InetAddress inetAddr = socket.getSocket().getLocalAddress();
                if (inetAddr != null) {
                    localName = inetAddr.getHostName();
                }
            }
            request.localName().setString(localName);

        } else if (actionCode == ActionCode.REQ_HOST_ATTRIBUTE) {

            if ((remoteHost == null) && (socket != null)) {
                InetAddress inetAddr = socket.getSocket().getInetAddress();
                if (inetAddr != null) {
                    remoteHost = inetAddr.getHostName();
                }
                if(remoteHost == null) {
                    if(remoteAddr != null) {
                        remoteHost = remoteAddr;
                    } else { // all we can do is punt
                        request.remoteHost().recycle();
                    }
                }
            }
            request.remoteHost().setString(remoteHost);

        } else if (actionCode == ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE) {

            if (localAddr == null)
               localAddr = socket.getSocket().getLocalAddress().getHostAddress();

            request.localAddr().setString(localAddr);

        } else if (actionCode == ActionCode.REQ_REMOTEPORT_ATTRIBUTE) {

            if ((remotePort == -1 ) && (socket !=null)) {
                remotePort = socket.getSocket().getPort();
            }
            request.setRemotePort(remotePort);

        } else if (actionCode == ActionCode.REQ_LOCALPORT_ATTRIBUTE) {

            if ((localPort == -1 ) && (socket !=null)) {
                localPort = socket.getSocket().getLocalPort();
            }
            request.setLocalPort(localPort);

        } else if (actionCode == ActionCode.REQ_SSL_CERTIFICATE) {
            if( sslSupport != null) {
                /*
                 * Consume and buffer the request body, so that it does not
                 * interfere with the client's handshake messages
                 */
                InputFilter[] inputFilters = inputBuffer.getFilters();
                ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER])
                    .setLimit(maxSavePostSize);
                inputBuffer.addActiveFilter
                    (inputFilters[Constants.BUFFERED_FILTER]);
                try {
                    Object sslO = sslSupport.getPeerCertificateChain(true);
                    if( sslO != null) {
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.ssl"), e);
                }
            }
        } else if (actionCode == ActionCode.ASYNC_COMPLETE) {
            if (asyncStateMachine.asyncComplete()) {
                ((JIoEndpoint) endpoint).processSocketAsync(this.socket,
                        SocketStatus.OPEN);
            }
        } else if (actionCode == ActionCode.ASYNC_SETTIMEOUT) {
            if (param == null) return;
            long timeout = ((Long)param).longValue();
            // if we are not piggy backing on a worker thread, set the timeout
            socket.setTimeout(timeout);
        } else if (actionCode == ActionCode.ASYNC_DISPATCH) {
            if (asyncStateMachine.asyncDispatch()) {
                ((JIoEndpoint) endpoint).processSocketAsync(this.socket,
                        SocketStatus.OPEN);
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

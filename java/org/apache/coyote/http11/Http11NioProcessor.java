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
import java.nio.channels.SelectionKey;

import javax.net.ssl.SSLEngine;

import org.apache.coyote.ActionCode;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.KeyAttachment;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SecureNioChannel;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Processes HTTP requests.
 *
 * @author Remy Maucherat
 * @author Filip Hanik
 */
public class Http11NioProcessor extends AbstractHttp11Processor<NioChannel> {

    private static final Log log = LogFactory.getLog(Http11NioProcessor.class);
    @Override
    protected Log getLog() {
        return log;
    }


    /**
     * SSL information.
     */
    protected SSLSupport sslSupport;

    // ----------------------------------------------------------- Constructors


    public Http11NioProcessor(int maxHttpHeaderSize, NioEndpoint endpoint,
            int maxTrailerSize) {

        super(endpoint);

        inputBuffer = new InternalNioInputBuffer(request, maxHttpHeaderSize);
        request.setInputBuffer(inputBuffer);

        outputBuffer = new InternalNioOutputBuffer(response, maxHttpHeaderSize);
        response.setOutputBuffer(outputBuffer);

        initializeFilters(maxTrailerSize);
    }


    // ----------------------------------------------------- Instance Variables
    /**
     * Input.
     */
    protected InternalNioInputBuffer inputBuffer = null;


    /**
     * Output.
     */
    protected InternalNioOutputBuffer outputBuffer = null;


    /**
     * Sendfile data.
     */
    protected NioEndpoint.SendfileData sendfileData = null;

    /**
     * Closed flag, a Comet async thread can 
     * signal for this Nio processor to be closed and recycled instead
     * of waiting for a timeout.
     * Closed by HttpServletResponse.getWriter().close()
     */
    protected boolean cometClose = false;
    
    /**
     * Socket associated with the current connection.
     */
    protected SocketWrapper<NioChannel> socket = null;


    // --------------------------------------------------------- Public Methods


    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @throws IOException error during an I/O operation
     */
    @Override
    public SocketState event(SocketStatus status)
        throws IOException {

        long soTimeout = endpoint.getSoTimeout();

        RequestInfo rp = request.getRequestProcessor();
        final NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            error = !adapter.event(request, response, status);
            if ( !error ) {
                if (attach != null) {
                    attach.setComet(comet);
                    if (comet) {
                        Integer comettimeout = (Integer) request.getAttribute("org.apache.tomcat.comet.timeout");
                        if (comettimeout != null) attach.setTimeout(comettimeout.longValue());
                    } else {
                        //reset the timeout
                        if (keepAlive && keepAliveTimeout>0) {
                            attach.setTimeout(keepAliveTimeout);
                        } else {
                            attach.setTimeout(soTimeout);
                        }
                    }

                }
            }
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

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (error) {
            return SocketState.CLOSED;
        } else if (!comet) {
            return (keepAlive)?SocketState.OPEN:SocketState.CLOSED;
        } else {
            return SocketState.LONG;
        }
    }
    

    @Override
    protected void resetTimeouts() {
        final NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
        if (!error && attach != null &&
                asyncStateMachine.isAsyncDispatching()) {
            long soTimeout = endpoint.getSoTimeout();

            //reset the timeout
            if (keepAlive && keepAliveTimeout>0) {
                attach.setTimeout(keepAliveTimeout);
            } else {
                attach.setTimeout(soTimeout);
            }
        }
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
    public SocketState process(SocketWrapper<NioChannel> socketWrapper)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the socket
        this.socket = socketWrapper;
        inputBuffer.init(socketWrapper, endpoint);
        outputBuffer.init(socketWrapper, endpoint);

        // Error flag
        error = false;
        keepAlive = true;
        comet = false;
        openSocket = false;
        sendfileInProgress = false;
        
        int soTimeout = endpoint.getSoTimeout();

        if (disableKeepAlive()) {
            socketWrapper.setKeepAliveLeft(0);
        }

        boolean keptAlive = false;
        boolean readComplete = true;
        
        while (!error && keepAlive && !comet && !isAsync() && !endpoint.isPaused()) {
            //always default to our soTimeout
            socketWrapper.setTimeout(soTimeout);
            // Parsing the request header
            try {
                if( !disableUploadTimeout && keptAlive && soTimeout > 0 ) {
                    socketWrapper.getSocket().getIOChannel().socket().setSoTimeout(soTimeout);
                }
                if (!inputBuffer.parseRequestLine(keptAlive)) {
                    // Haven't finished reading the request so keep the socket
                    // open
                    openSocket = true;
                    // Check to see if we have read any of the request line yet
                    if (inputBuffer.getParsingRequestLinePhase()<2) {
                        // No data read, OK to recycle the processor
                        // Continue to use keep alive timeout
                        if (keepAliveTimeout>0) {
                            socketWrapper.setTimeout(keepAliveTimeout);
                        }
                    } else {
                        // Started to read request line. Need to keep processor
                        // associated with socket
                        readComplete = false;
                    }
                    if (endpoint.isPaused()) {
                        // 503 - Service unavailable
                        response.setStatus(503);
                        adapter.log(request, response, 0);
                        error = true;
                    } else {
                        break;
                    }
                }
                if (!endpoint.isPaused()) {
                    keptAlive = true;
                    if ( !inputBuffer.parseHeaders() ) {
                        //we've read part of the request, don't recycle it
                        //instead associate it with the socket
                        openSocket = true;
                        readComplete = false;
                        break;
                    }
                    request.setStartTime(System.currentTimeMillis());
                    if (!disableUploadTimeout) { //only for body, not for request headers
                        socketWrapper.getSocket().getIOChannel().socket().setSoTimeout(
                                connectionUploadTimeout);
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
                    // Comet support
                    SelectionKey key = socketWrapper.getSocket().getIOChannel().keyFor(
                            socketWrapper.getSocket().getPoller().getSelector());
                    if (key != null) {
                        NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment) key.attachment();
                        if (attach != null)  {
                            attach.setComet(comet);
                            if (comet) {
                                Integer comettimeout = (Integer) request.getAttribute("org.apache.tomcat.comet.timeout");
                                if (comettimeout != null) attach.setTimeout(comettimeout.longValue());
                            }
                        }
                    }
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

            if (!isAsync() && !comet) {
                if(error) {
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

            if (!comet && !isAsync()) {
                // Next request
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
            return (openSocket) ? (readComplete?SocketState.OPEN:SocketState.LONG) : SocketState.CLOSED;
        }
    }


    @Override
    protected boolean breakKeepAliveLoop(
    		SocketWrapper<NioChannel> socketWrapper) {
        // Do sendfile as needed: add socket to sendfile and end
        if (sendfileData != null && !error) {
            ((KeyAttachment) socketWrapper).setSendfileData(sendfileData);
            sendfileData.keepAlive = keepAlive;
            SelectionKey key = socketWrapper.getSocket().getIOChannel().keyFor(
                    socketWrapper.getSocket().getPoller().getSelector());
            //do the first write on this thread, might as well
            openSocket = socketWrapper.getSocket().getPoller().processSendfile(key,
                    (KeyAttachment) socketWrapper, true, true);
            return true;
        }
    	return false;
    }


    @Override
    protected boolean disableKeepAlive() {
        return false;
    }


    @Override
    public void recycleInternal() {
        socket = null;
        cometClose = false;
        comet = false;
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
    public void actionInternal(ActionCode actionCode, Object param) {

        if (actionCode == ActionCode.CLOSE) {
            // Close
            // End the processing of the current request, and stop any further
            // transactions with the client

            comet = false;
            cometClose = true;
            SelectionKey key = socket.getSocket().getIOChannel().keyFor(socket.getSocket().getPoller().getSelector());
            if ( key != null ) {
                NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment) key.attachment();
                if ( attach!=null && attach.getComet()) {
                    //if this is a comet connection
                    //then execute the connection closure at the next selector loop
                    //request.getAttributes().remove("org.apache.tomcat.comet.timeout");
                    //attach.setTimeout(5000); //force a cleanup in 5 seconds
                    //attach.setError(true); //this has caused concurrency errors
                }
            }

            try {
                outputBuffer.endRequest();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }

        } else if (actionCode == ActionCode.REQ_HOST_ADDR_ATTRIBUTE) {

            // Get remote host address
            if ((remoteAddr == null) && (socket != null)) {
                InetAddress inetAddr = socket.getSocket().getIOChannel().socket().getInetAddress();
                if (inetAddr != null) {
                    remoteAddr = inetAddr.getHostAddress();
                }
            }
            request.remoteAddr().setString(remoteAddr);

        } else if (actionCode == ActionCode.REQ_LOCAL_NAME_ATTRIBUTE) {

            // Get local host name
            if ((localName == null) && (socket != null)) {
                InetAddress inetAddr = socket.getSocket().getIOChannel().socket().getLocalAddress();
                if (inetAddr != null) {
                    localName = inetAddr.getHostName();
                }
            }
            request.localName().setString(localName);

        } else if (actionCode == ActionCode.REQ_HOST_ATTRIBUTE) {

            // Get remote host name
            if ((remoteHost == null) && (socket != null)) {
                InetAddress inetAddr = socket.getSocket().getIOChannel().socket().getInetAddress();
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
               localAddr = socket.getSocket().getIOChannel().socket().getLocalAddress().getHostAddress();

            request.localAddr().setString(localAddr);

        } else if (actionCode == ActionCode.REQ_REMOTEPORT_ATTRIBUTE) {

            if ((remotePort == -1 ) && (socket !=null)) {
                remotePort = socket.getSocket().getIOChannel().socket().getPort();
            }
            request.setRemotePort(remotePort);

        } else if (actionCode == ActionCode.REQ_LOCALPORT_ATTRIBUTE) {

            if ((localPort == -1 ) && (socket !=null)) {
                localPort = socket.getSocket().getIOChannel().socket().getLocalPort();
            }
            request.setLocalPort(localPort);

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
                SecureNioChannel sslChannel = (SecureNioChannel) socket.getSocket();
                SSLEngine engine = sslChannel.getSslEngine();
                if (!engine.getNeedClientAuth()) {
                    // Need to re-negotiate SSL connection
                    engine.setNeedClientAuth(true);
                    try {
                        sslChannel.rehandshake(endpoint.getSoTimeout());
                        sslSupport = ((NioEndpoint)endpoint).getHandler()
                                .getSslImplementation().getSSLSupport(
                                        engine.getSession());
                    } catch (IOException ioe) {
                        log.warn(sm.getString("http11processor.socket.sslreneg",ioe));
                    }
                }

                try {
                    // use force=false since re-negotiation is handled above
                    // (and it is a NO-OP for NIO anyway)
                    Object sslO = sslSupport.getPeerCertificateChain(false);
                    if( sslO != null) {
                        request.setAttribute
                            (SSLSupport.CERTIFICATE_KEY, sslO);
                    }
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.ssl"), e);
                }
            }

        } else if (actionCode == ActionCode.AVAILABLE) {
            request.setAvailable(inputBuffer.available());
        } else if (actionCode == ActionCode.COMET_BEGIN) {
            comet = true;
        } else if (actionCode == ActionCode.COMET_END) {
            comet = false;
        }  else if (actionCode == ActionCode.COMET_CLOSE) {
            if (socket==null || socket.getSocket().getAttachment(false)==null) return;
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
            attach.setCometOps(NioEndpoint.OP_CALLBACK);
            //notify poller if not on a tomcat thread
            RequestInfo rp = request.getRequestProcessor();
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) //async handling
                socket.getSocket().getPoller().add(socket.getSocket());
        } else if (actionCode == ActionCode.COMET_SETTIMEOUT) {
            if (param==null) return;
            if (socket==null || socket.getSocket().getAttachment(false)==null) return;
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
            long timeout = ((Long)param).longValue();
            //if we are not piggy backing on a worker thread, set the timeout
            RequestInfo rp = request.getRequestProcessor();
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) //async handling
                attach.setTimeout(timeout);
        } else if (actionCode == ActionCode.ASYNC_COMPLETE) {
            if (asyncStateMachine.asyncComplete()) {
                ((NioEndpoint)endpoint).processSocket(this.socket.getSocket(),
                        SocketStatus.OPEN, true);
            }
        } else if (actionCode == ActionCode.ASYNC_SETTIMEOUT) {
            if (param==null) return;
            if (socket==null || socket.getSocket().getAttachment(false)==null) return;
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getSocket().getAttachment(false);
            long timeout = ((Long)param).longValue();
            //if we are not piggy backing on a worker thread, set the timeout
            attach.setTimeout(timeout);
        } else if (actionCode == ActionCode.ASYNC_DISPATCH) {
            if (asyncStateMachine.asyncDispatch()) {
                ((NioEndpoint)endpoint).processSocket(this.socket.getSocket(),
                        SocketStatus.OPEN, true);
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
                "org.apache.tomcat.sendfile.filename");
        if (fileName != null) {
            // No entity body sent here
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
            sendfileData = new NioEndpoint.SendfileData();
            sendfileData.fileName = fileName;
            sendfileData.pos = ((Long) request.getAttribute(
                    "org.apache.tomcat.sendfile.start")).longValue();
            sendfileData.length = ((Long) request.getAttribute(
                    "org.apache.tomcat.sendfile.end")).longValue() - sendfileData.pos;
            return true;
        }
        return false;
    }

    @Override
    protected AbstractInputBuffer<NioChannel> getInputBuffer() {
        return inputBuffer;
    }

    @Override
    protected AbstractOutputBuffer<NioChannel> getOutputBuffer() {
        return outputBuffer;
    }

    /**
     * Set the SSL information for this HTTP connection.
     */
    public void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }
}

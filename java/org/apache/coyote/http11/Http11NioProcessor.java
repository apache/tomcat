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
import java.util.Locale;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLEngine;

import org.apache.coyote.ActionCode;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.NioChannel;
import org.apache.tomcat.util.net.NioEndpoint;
import org.apache.tomcat.util.net.NioEndpoint.KeyAttachment;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SecureNioChannel;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.jsse.JSSEFactory;


/**
 * Processes HTTP requests.
 *
 * @author Remy Maucherat
 * @author Filip Hanik
 */
public class Http11NioProcessor extends AbstractHttp11Processor {

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

        this.endpoint = endpoint;

        request = new Request();
        inputBuffer = new InternalNioInputBuffer(request, maxHttpHeaderSize);
        request.setInputBuffer(inputBuffer);

        response = new Response();
        response.setHook(this);
        outputBuffer = new InternalNioOutputBuffer(response, maxHttpHeaderSize);
        response.setOutputBuffer(outputBuffer);
        request.setResponse(response);

        ssl = endpoint.isSSLEnabled();

        initializeFilters(maxTrailerSize);

        // Cause loading of HexUtils
        HexUtils.load();
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
     * Comet used.
     */
    protected boolean comet = false;
    
    /**
     * Closed flag, a Comet async thread can 
     * signal for this Nio processor to be closed and recycled instead
     * of waiting for a timeout.
     * Closed by HttpServletResponse.getWriter().close()
     */
    protected boolean cometClose = false;
    
    /**
     * SSL enabled ?
     */
    protected boolean ssl = false;


    /**
     * Socket associated with the current connection.
     */
    protected NioChannel socket = null;


    /**
     * Associated endpoint.
     */
    protected NioEndpoint endpoint;

    
    // ------------------------------------------------------------- Properties

    /**
     * Expose the endpoint.
     */
    @Override
    protected AbstractEndpoint getEndpoint() {
        return endpoint;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @throws IOException error during an I/O operation
     */
    public SocketState event(SocketStatus status)
        throws IOException {

        long soTimeout = endpoint.getSoTimeout();
        int keepAliveTimeout = endpoint.getKeepAliveTimeout();

        RequestInfo rp = request.getRequestProcessor();
        final NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
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
            recycle();
            return SocketState.CLOSED;
        } else if (!comet) {
            recycle();
            return (keepAlive)?SocketState.OPEN:SocketState.CLOSED;
        } else {
            return SocketState.LONG;
        }
    }
    
    
    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @throws IOException error during an I/O operation
     */
    public SocketState asyncDispatch(SocketStatus status)
        throws IOException {

        long soTimeout = endpoint.getSoTimeout();
        int keepAliveTimeout = endpoint.getKeepAliveTimeout();

        RequestInfo rp = request.getRequestProcessor();
        final NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            error = !adapter.asyncDispatch(request, response, status);
            if ( !error ) {
                if (attach != null) {
                    attach.setComet(comet);
                    if (comet) {
                        Integer comettimeout = (Integer) request.getAttribute("org.apache.tomcat.comet.timeout");
                        if (comettimeout != null) attach.setTimeout(comettimeout.longValue());
                    } else {
                        if (asyncStateMachine.isAsyncDispatching()) {
                            //reset the timeout
                            if (keepAlive && keepAliveTimeout>0) {
                                attach.setTimeout(keepAliveTimeout);
                            } else {
                                attach.setTimeout(soTimeout);
                            }
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
            recycle();
            return SocketState.CLOSED;
        } else if (!comet && !isAsync()) {
            recycle();
            return (keepAlive)?SocketState.OPEN:SocketState.CLOSED;
        } else {
            return SocketState.LONG;
        }
    }

    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @throws IOException error during an I/O operation
     */
    public SocketState process(NioChannel socket)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the socket
        this.socket = socket;
        inputBuffer.setSocket(socket);
        outputBuffer.setSocket(socket);
        inputBuffer.setSelectorPool(endpoint.getSelectorPool());
        outputBuffer.setSelectorPool(endpoint.getSelectorPool());

        // Error flag
        error = false;
        keepAlive = true;
        comet = false;
        
        long soTimeout = endpoint.getSoTimeout();
        int keepAliveTimeout = endpoint.getKeepAliveTimeout();

        boolean keptAlive = false;
        boolean openSocket = false;
        boolean recycle = true;
        final KeyAttachment ka = (KeyAttachment)socket.getAttachment(false);
        
        while (!error && keepAlive && !comet && !isAsync() && !endpoint.isPaused()) {
            //always default to our soTimeout
            ka.setTimeout(soTimeout);
            // Parsing the request header
            try {
                if( !disableUploadTimeout && keptAlive && soTimeout > 0 ) {
                    socket.getIOChannel().socket().setSoTimeout((int)soTimeout);
                }
                if (!inputBuffer.parseRequestLine(keptAlive)) {
                    // Haven't finished reading the request so keep the socket
                    // open
                    openSocket = true;
                    // Check to see if we have read any of the request line yet
                    if (inputBuffer.getParsingRequestLinePhase()<2) {
                        // No data read, OK to recycle the processor
                        // Continue to use keep alive timeout
                        if (keepAliveTimeout>0) ka.setTimeout(keepAliveTimeout);
                    } else {
                        // Started to read request line. Need to keep processor
                        // associated with socket
                        recycle = false;
                    }
                    break;
                }
                keptAlive = true;
                if ( !inputBuffer.parseHeaders() ) {
                    //we've read part of the request, don't recycle it
                    //instead associate it with the socket
                    openSocket = true;
                    recycle = false;
                    break;
                }
                request.setStartTime(System.currentTimeMillis());
                if (!disableUploadTimeout) { //only for body, not for request headers
                    socket.getIOChannel().socket().setSoTimeout(
                            connectionUploadTimeout);
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
            
            if (maxKeepAliveRequests == 1 )
                keepAlive = false;
            if (maxKeepAliveRequests > 0 && ka.decrementKeepAlive() <= 0)
                keepAlive = false;

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
                                statusDropsConnection(response.getStatus());
                    }
                    // Comet support
                    SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
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
            if (!comet && !isAsync()) {
                // If we know we are closing the connection, don't drain input.
                // This way uploading a 100GB file doesn't tie up the thread 
                // if the servlet has rejected it.
                if(error)
                    inputBuffer.setSwallowInput(false);
                endRequest();
            }

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
            
            // Do sendfile as needed: add socket to sendfile and end
            if (sendfileData != null && !error) {
                ka.setSendfileData(sendfileData);
                sendfileData.keepAlive = keepAlive;
                SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
                //do the first write on this thread, might as well
                openSocket = socket.getPoller().processSendfile(key,ka,true,true);
                break;
            }


            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);

        }//while

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
        if (error || endpoint.isPaused()) {
            recycle();
            return SocketState.CLOSED;
        } else if (comet || isAsync()) {
            return SocketState.LONG;
        } else {
            if (recycle) {
                recycle();
            }
            //return (openSocket) ? (SocketState.OPEN) : SocketState.CLOSED;
            return (openSocket) ? (recycle?SocketState.OPEN:SocketState.LONG) : SocketState.CLOSED;
        }

    }


    @Override
    public void recycleInternal() {
        this.socket = null;
        this.cometClose = false;
        this.comet = false;
        remoteAddr = null;
        remoteHost = null;
        localAddr = null;
        localName = null;
        remotePort = -1;
        localPort = -1;
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
            SelectionKey key = socket.getIOChannel().keyFor(socket.getPoller().getSelector());
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
                InetAddress inetAddr = socket.getIOChannel().socket().getInetAddress();
                if (inetAddr != null) {
                    remoteAddr = inetAddr.getHostAddress();
                }
            }
            request.remoteAddr().setString(remoteAddr);

        } else if (actionCode == ActionCode.REQ_LOCAL_NAME_ATTRIBUTE) {

            // Get local host name
            if ((localName == null) && (socket != null)) {
                InetAddress inetAddr = socket.getIOChannel().socket().getLocalAddress();
                if (inetAddr != null) {
                    localName = inetAddr.getHostName();
                }
            }
            request.localName().setString(localName);

        } else if (actionCode == ActionCode.REQ_HOST_ATTRIBUTE) {

            // Get remote host name
            if ((remoteHost == null) && (socket != null)) {
                InetAddress inetAddr = socket.getIOChannel().socket().getInetAddress();
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
               localAddr = socket.getIOChannel().socket().getLocalAddress().getHostAddress();

            request.localAddr().setString(localAddr);

        } else if (actionCode == ActionCode.REQ_REMOTEPORT_ATTRIBUTE) {

            if ((remotePort == -1 ) && (socket !=null)) {
                remotePort = socket.getIOChannel().socket().getPort();
            }
            request.setRemotePort(remotePort);

        } else if (actionCode == ActionCode.REQ_LOCALPORT_ATTRIBUTE) {

            if ((localPort == -1 ) && (socket !=null)) {
                localPort = socket.getIOChannel().socket().getLocalPort();
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
                SecureNioChannel sslChannel = (SecureNioChannel) socket;
                SSLEngine engine = sslChannel.getSslEngine();
                if (!engine.getNeedClientAuth()) {
                    // Need to re-negotiate SSL connection
                    engine.setNeedClientAuth(true);
                    try {
                        sslChannel.rehandshake(endpoint.getSoTimeout());
                        sslSupport = (new JSSEFactory()).getSSLSupport(engine.getSession());
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
            if (socket==null || socket.getAttachment(false)==null) return;
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
            attach.setCometOps(NioEndpoint.OP_CALLBACK);
            //notify poller if not on a tomcat thread
            RequestInfo rp = request.getRequestProcessor();
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) //async handling
                socket.getPoller().add(socket);
        } else if (actionCode == ActionCode.COMET_SETTIMEOUT) {
            if (param==null) return;
            if (socket==null || socket.getAttachment(false)==null) return;
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
            long timeout = ((Long)param).longValue();
            //if we are not piggy backing on a worker thread, set the timeout
            RequestInfo rp = request.getRequestProcessor();
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) //async handling
                attach.setTimeout(timeout);
        } else if (actionCode == ActionCode.ASYNC_COMPLETE) {
            if (asyncStateMachine.asyncComplete()) {
                endpoint.processSocket(this.socket, SocketStatus.OPEN, true);
            }
        } else if (actionCode == ActionCode.ASYNC_SETTIMEOUT) {
            if (param==null) return;
            if (socket==null || socket.getAttachment(false)==null) return;
            NioEndpoint.KeyAttachment attach = (NioEndpoint.KeyAttachment)socket.getAttachment(false);
            long timeout = ((Long)param).longValue();
            //if we are not piggy backing on a worker thread, set the timeout
            attach.setTimeout(timeout);
        } else if (actionCode == ActionCode.ASYNC_DISPATCH) {
            if (asyncStateMachine.asyncDispatch()) {
                endpoint.processSocket(this.socket, SocketStatus.OPEN, true);
            }
        }
    }


    // ------------------------------------------------------ Connector Methods

    public SSLSupport getSslSupport() {
        return sslSupport;
    }

    // ------------------------------------------------------ Protected Methods


    /**
     * After reading the request headers, we have to setup the request filters.
     */
    protected void prepareRequest() {

        http11 = true;
        http09 = false;
        contentDelimitation = false;
        expectation = false;
        sendfileData = null;
        if (ssl) {
            request.scheme().setString("https");
        }
        MessageBytes protocolMB = request.protocol();
        if (protocolMB.equals(Constants.HTTP_11)) {
            http11 = true;
            protocolMB.setString(Constants.HTTP_11);
        } else if (protocolMB.equals(Constants.HTTP_10)) {
            http11 = false;
            keepAlive = false;
            protocolMB.setString(Constants.HTTP_10);
        } else if (protocolMB.equals("")) {
            // HTTP/0.9
            http09 = true;
            http11 = false;
            keepAlive = false;
        } else {
            // Unsupported protocol
            http11 = false;
            error = true;
            // Send 505; Unsupported HTTP version
            response.setStatus(505);
            adapter.log(request, response, 0);
        }

        MessageBytes methodMB = request.method();
        if (methodMB.equals(Constants.GET)) {
            methodMB.setString(Constants.GET);
        } else if (methodMB.equals(Constants.POST)) {
            methodMB.setString(Constants.POST);
        }

        MimeHeaders headers = request.getMimeHeaders();

        // Check connection header
        MessageBytes connectionValueMB = headers.getValue("connection");
        if (connectionValueMB != null) {
            ByteChunk connectionValueBC = connectionValueMB.getByteChunk();
            if (findBytes(connectionValueBC, Constants.CLOSE_BYTES) != -1) {
                keepAlive = false;
            } else if (findBytes(connectionValueBC,
                                 Constants.KEEPALIVE_BYTES) != -1) {
                keepAlive = true;
            }
        }

        MessageBytes expectMB = null;
        if (http11)
            expectMB = headers.getValue("expect");
        if ((expectMB != null)
            && (expectMB.indexOfIgnoreCase("100-continue", 0) != -1)) {
            inputBuffer.setSwallowInput(false);
            expectation = true;
        }

        // Check user-agent header
        if ((restrictedUserAgents != null) && ((http11) || (keepAlive))) {
            MessageBytes userAgentValueMB = headers.getValue("user-agent");
            // Check in the restricted list, and adjust the http11
            // and keepAlive flags accordingly
            if(userAgentValueMB != null) {
                String userAgentValue = userAgentValueMB.toString();
                if (restrictedUserAgents != null &&
                        restrictedUserAgents.matcher(userAgentValue).matches()) {
                    http11 = false;
                    keepAlive = false;
                }
            }
        }

        // Check for a full URI (including protocol://host:port/)
        ByteChunk uriBC = request.requestURI().getByteChunk();
        if (uriBC.startsWithIgnoreCase("http", 0)) {

            int pos = uriBC.indexOf("://", 0, 3, 4);
            int uriBCStart = uriBC.getStart();
            int slashPos = -1;
            if (pos != -1) {
                byte[] uriB = uriBC.getBytes();
                slashPos = uriBC.indexOf('/', pos + 3);
                if (slashPos == -1) {
                    slashPos = uriBC.getLength();
                    // Set URI as "/"
                    request.requestURI().setBytes
                        (uriB, uriBCStart + pos + 1, 1);
                } else {
                    request.requestURI().setBytes
                        (uriB, uriBCStart + slashPos,
                         uriBC.getLength() - slashPos);
                }
                MessageBytes hostMB = headers.setValue("host");
                hostMB.setBytes(uriB, uriBCStart + pos + 3,
                                slashPos - pos - 3);
            }

        }

        // Input filter setup
        InputFilter[] inputFilters = inputBuffer.getFilters();

        // Parse transfer-encoding header
        MessageBytes transferEncodingValueMB = null;
        if (http11)
            transferEncodingValueMB = headers.getValue("transfer-encoding");
        if (transferEncodingValueMB != null) {
            String transferEncodingValue = transferEncodingValueMB.toString();
            // Parse the comma separated list. "identity" codings are ignored
            int startPos = 0;
            int commaPos = transferEncodingValue.indexOf(',');
            String encodingName = null;
            while (commaPos != -1) {
                encodingName = transferEncodingValue.substring
                    (startPos, commaPos).toLowerCase(Locale.ENGLISH).trim();
                if (!addInputFilter(inputFilters, encodingName)) {
                    // Unsupported transfer encoding
                    error = true;
                    // 501 - Unimplemented
                    response.setStatus(501);
                    adapter.log(request, response, 0);
                }
                startPos = commaPos + 1;
                commaPos = transferEncodingValue.indexOf(',', startPos);
            }
            encodingName = transferEncodingValue.substring(startPos)
                .toLowerCase(Locale.ENGLISH).trim();
            if (!addInputFilter(inputFilters, encodingName)) {
                // Unsupported transfer encoding
                error = true;
                // 501 - Unimplemented
                response.setStatus(501);
                adapter.log(request, response, 0);
            }
        }

        // Parse content-length header
        long contentLength = request.getContentLengthLong();
        if (contentLength >= 0 && !contentDelimitation) {
            inputBuffer.addActiveFilter
                (inputFilters[Constants.IDENTITY_FILTER]);
            contentDelimitation = true;
        }

        MessageBytes valueMB = headers.getValue("host");

        // Check host header
        if (http11 && (valueMB == null)) {
            error = true;
            // 400 - Bad request
            response.setStatus(400);
            adapter.log(request, response, 0);
        }

        parseHost(valueMB);

        if (!contentDelimitation) {
            // If there's no content length 
            // (broken HTTP/1.0 or HTTP/1.1), assume
            // the client is not broken and didn't send a body
            inputBuffer.addActiveFilter
                    (inputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }

        // Advertise sendfile support through a request attribute
        if (endpoint.getUseSendfile()) 
            request.setAttribute("org.apache.tomcat.sendfile.support", Boolean.TRUE);
        // Advertise comet support through a request attribute
        request.setAttribute("org.apache.tomcat.comet.support", Boolean.TRUE);
        // Advertise comet timeout support
        request.setAttribute("org.apache.tomcat.comet.timeout.support", Boolean.TRUE);

    }


    /**
     * Parse host.
     */
    public void parseHost(MessageBytes valueMB) {

        if (valueMB == null || valueMB.isNull()) {
            // HTTP/1.0
            // Default is what the socket tells us. Overridden if a host is
            // found/parsed
            request.setServerPort(endpoint.getPort());
            return;
        }

        ByteChunk valueBC = valueMB.getByteChunk();
        byte[] valueB = valueBC.getBytes();
        int valueL = valueBC.getLength();
        int valueS = valueBC.getStart();
        int colonPos = -1;
        if (hostNameC.length < valueL) {
            hostNameC = new char[valueL];
        }

        boolean ipv6 = (valueB[valueS] == '[');
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            char b = (char) valueB[i + valueS];
            hostNameC[i] = b;
            if (b == ']') {
                bracketClosed = true;
            } else if (b == ':') {
                if (!ipv6 || bracketClosed) {
                    colonPos = i;
                    break;
                }
            }
        }

        if (colonPos < 0) {
            if (!ssl) {
                // 80 - Default HTTP port
                request.setServerPort(80);
            } else {
                // 443 - Default HTTPS port
                request.setServerPort(443);
            }
            request.serverName().setChars(hostNameC, 0, valueL);
        } else {

            request.serverName().setChars(hostNameC, 0, colonPos);

            int port = 0;
            int mult = 1;
            for (int i = valueL - 1; i > colonPos; i--) {
                int charValue = HexUtils.getDec(valueB[i + valueS]);
                if (charValue == -1) {
                    // Invalid character
                    error = true;
                    // 400 - Bad request
                    response.setStatus(400);
                    adapter.log(request, response, 0);
                    break;
                }
                port = port + (charValue * mult);
                mult = 10 * mult;
            }
            request.setServerPort(port);

        }

    }

    @Override
    protected boolean prepareSendfile(OutputFilter[] outputFilters) {
        String fileName = (String) request.getAttribute("org.apache.tomcat.sendfile.filename");
        if (fileName != null) {
            // No entity body sent here
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
            sendfileData = new NioEndpoint.SendfileData();
            sendfileData.fileName = fileName;
            sendfileData.pos = ((Long) request.getAttribute("org.apache.tomcat.sendfile.start")).longValue();
            sendfileData.length = ((Long) request.getAttribute("org.apache.tomcat.sendfile.end")).longValue() - sendfileData.pos;
            return true;
        }
        return false;
    }

    /**
     * Specialized utility method: find a sequence of lower case bytes inside
     * a ByteChunk.
     */
    @Override
    protected int findBytes(ByteChunk bc, byte[] b) {

        byte first = b[0];
        byte[] buff = bc.getBuffer();
        int start = bc.getStart();
        int end = bc.getEnd();

        // Look for first char
        int srcEnd = b.length;
    
        for (int i = start; i <= (end - srcEnd); i++) {
            if (Ascii.toLower(buff[i]) != first) continue;
            // found first char, now look for a match
                int myPos = i+1;
            for (int srcPos = 1; srcPos < srcEnd;) {
                    if (Ascii.toLower(buff[myPos++]) != b[srcPos++])
                break;
                    if (srcPos == srcEnd) return i - start; // found it
            }
        }
        return -1;

    }

    @Override
    protected AbstractInputBuffer getInputBuffer() {
        return inputBuffer;
    }

    @Override
    protected AbstractOutputBuffer getOutputBuffer() {
        return outputBuffer;
    }

    /**
     * Set the SSL information for this HTTP connection.
     */
    public void setSslSupport(SSLSupport sslSupport) {
        this.sslSupport = sslSupport;
    }

    @Override
    public Executor getExecutor() {
        return endpoint.getExecutor();
    }
}

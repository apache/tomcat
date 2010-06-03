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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.coyote.http11.filters.ChunkedInputFilter;
import org.apache.coyote.http11.filters.ChunkedOutputFilter;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.coyote.http11.filters.IdentityInputFilter;
import org.apache.coyote.http11.filters.IdentityOutputFilter;
import org.apache.coyote.http11.filters.SavedRequestInputFilter;
import org.apache.coyote.http11.filters.VoidInputFilter;
import org.apache.coyote.http11.filters.VoidOutputFilter;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.Address;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLSocket;
import org.apache.tomcat.jni.Sockaddr;
import org.apache.tomcat.jni.Socket;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;


/**
 * Processes HTTP requests.
 *
 * @author Remy Maucherat
 */
public class Http11AprProcessor extends AbstractHttp11Processor implements ActionHook {


    /**
     * Logger.
     */
    private static final Log log = LogFactory.getLog(Http11AprProcessor.class);

    // ----------------------------------------------------------- Constructors


    public Http11AprProcessor(int headerBufferSize, AprEndpoint endpoint) {

        this.endpoint = endpoint;
        
        request = new Request();
        inputBuffer = new InternalAprInputBuffer(request, headerBufferSize);
        request.setInputBuffer(inputBuffer);

        response = new Response();
        response.setHook(this);
        outputBuffer = new InternalAprOutputBuffer(response, headerBufferSize);
        response.setOutputBuffer(outputBuffer);
        request.setResponse(response);
        
        ssl = endpoint.isSSLEnabled();

        initializeFilters();

        // Cause loading of HexUtils
        HexUtils.load();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Input.
     */
    protected InternalAprInputBuffer inputBuffer = null;


    /**
     * Output.
     */
    protected InternalAprOutputBuffer outputBuffer = null;


    /**
     * Sendfile data.
     */
    protected AprEndpoint.SendfileData sendfileData = null;


    /**
     * Comet used.
     */
    protected boolean comet = false;


    /**
     * Async used
     */
    protected boolean async = false;


    /**
     * SSL enabled ?
     */
    protected boolean ssl = false;
    

    /**
     * Socket associated with the current connection.
     */
    protected long socket = 0;


    /**
     * Associated endpoint.
     */
    protected AprEndpoint endpoint;


    // --------------------------------------------------------- Public Methods


    /**
     * Add input or output filter.
     *
     * @param className class name of the filter
     */
    protected void addFilter(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Object obj = clazz.newInstance();
            if (obj instanceof InputFilter) {
                inputBuffer.addFilter((InputFilter) obj);
            } else if (obj instanceof OutputFilter) {
                outputBuffer.addFilter((OutputFilter) obj);
            } else {
                log.warn(sm.getString("http11processor.filter.unknown", className));
            }
        } catch (Exception e) {
            log.error(sm.getString("http11processor.filter.error", className), e);
        }
    }


    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @throws IOException error during an I/O operation
     */
    public SocketState event(SocketStatus status)
        throws IOException {
        
        RequestInfo rp = request.getRequestProcessor();
        
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            error = !adapter.event(request, response, status);
        } catch (InterruptedIOException e) {
            error = true;
        } catch (Throwable t) {
            log.error(sm.getString("http11processor.request.process"), t);
            // 500 - Internal Server Error
            response.setStatus(500);
            adapter.log(request, response, 0);
            error = true;
        }
        
        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (error) {
            inputBuffer.nextRequest();
            outputBuffer.nextRequest();
            recycle();
            return SocketState.CLOSED;
        } else if (!comet) {
            inputBuffer.nextRequest();
            outputBuffer.nextRequest();
            recycle();
            return SocketState.OPEN;
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
    public SocketState process(long socket)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Set the remote address
        remoteAddr = null;
        remoteHost = null;
        localAddr = null;
        localName = null;
        remotePort = -1;
        localPort = -1;

        // Setting up the socket
        this.socket = socket;
        inputBuffer.setSocket(socket);
        outputBuffer.setSocket(socket);

        // Error flag
        error = false;
        comet = false;
        async = false;
        keepAlive = true;

        int keepAliveLeft = maxKeepAliveRequests;
        long soTimeout = endpoint.getSoTimeout();
        
        boolean keptAlive = false;
        boolean openSocket = false;

        while (!error && keepAlive && !comet && !async) {

            // Parsing the request header
            try {
                if( !disableUploadTimeout && keptAlive && soTimeout > 0 ) {
                    Socket.timeoutSet(socket, soTimeout * 1000);
                }
                if (!inputBuffer.parseRequestLine(keptAlive)) {
                    // This means that no data is available right now
                    // (long keepalive), so that the processor should be recycled
                    // and the method should return true
                    openSocket = true;
                    // Add the socket to the poller
                    endpoint.getPoller().add(socket);
                    break;
                }
                request.setStartTime(System.currentTimeMillis());
                keptAlive = true;
                if (!disableUploadTimeout) {
                    Socket.timeoutSet(socket, timeout * 1000);
                }
                inputBuffer.parseHeaders();
            } catch (IOException e) {
                error = true;
                break;
            } catch (Throwable t) {
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
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("http11processor.request.prepare"), t);
                    }
                    // 400 - Internal Server Error
                    response.setStatus(400);
                    adapter.log(request, response, 0);
                    error = true;
                }
            }

            if (maxKeepAliveRequests > 0 && --keepAliveLeft == 0)
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
                } catch (InterruptedIOException e) {
                    error = true;
                } catch (Throwable t) {
                    log.error(sm.getString("http11processor.request.process"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    adapter.log(request, response, 0);
                    error = true;
                }
            }

            // Finish the handling of the request
            if (!comet && !async) {
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

            if (!comet && !async) {
                // Next request
                inputBuffer.nextRequest();
                outputBuffer.nextRequest();
            }
            
            // Do sendfile as needed: add socket to sendfile and end
            if (sendfileData != null && !error) {
                sendfileData.socket = socket;
                sendfileData.keepAlive = keepAlive;
                if (!endpoint.getSendfile().add(sendfileData)) {
                    openSocket = true;
                    break;
                }
            }
            
            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);

        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (comet  || async) {
            if (error) {
                inputBuffer.nextRequest();
                outputBuffer.nextRequest();
                recycle();
                return SocketState.CLOSED;
            } else {
                return SocketState.LONG;
            }
        } else {
            recycle();
            return (openSocket) ? SocketState.OPEN : SocketState.CLOSED;
        }
        
    }

    /* Copied from the AjpProcessor.java */
    public SocketState asyncDispatch(long socket, SocketStatus status) throws IOException {

        // Setting up the socket
        this.socket = socket;
        inputBuffer.setSocket(socket);
        outputBuffer.setSocket(socket);

        RequestInfo rp = request.getRequestProcessor();
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            error = !adapter.asyncDispatch(request, response, status);
        } catch (InterruptedIOException e) {
            error = true;
        } catch (Throwable t) {
            log.error(sm.getString("http11processor.request.process"), t);
            // 500 - Internal Server Error
            response.setStatus(500);
            adapter.log(request, response, 0);
            error = true;
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (async) {
            if (error) {
                response.setStatus(500);
                request.updateCounters();
                recycle();
                return SocketState.CLOSED;
            } else {
                return SocketState.LONG;
            }
        } else {
            if (error) {
                response.setStatus(500);
            }
            request.updateCounters();
            recycle();
            return SocketState.CLOSED;
        }
        
    }

    
    public void endRequest() {
        
        // Finish the handling of the request
        try {
            inputBuffer.endRequest();
        } catch (IOException e) {
            error = true;
        } catch (Throwable t) {
            log.error(sm.getString("http11processor.request.finish"), t);
            // 500 - Internal Server Error
            response.setStatus(500);
            adapter.log(request, response, 0);
            error = true;
        }
        try {
            outputBuffer.endRequest();
        } catch (IOException e) {
            error = true;
        } catch (Throwable t) {
            log.error(sm.getString("http11processor.response.finish"), t);
            error = true;
        }

    }
    
    
    public void recycle() {
        inputBuffer.recycle();
        outputBuffer.recycle();
        this.socket = 0;
    }
    

    // ----------------------------------------------------- ActionHook Methods


    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    public void action(ActionCode actionCode, Object param) {

        if (actionCode == ActionCode.ACTION_COMMIT) {
            // Commit current response

            if (response.isCommitted())
                return;

            // Validate and write response headers
            prepareResponse();
            try {
                outputBuffer.commit();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }

        } else if (actionCode == ActionCode.ACTION_ACK) {

            // Acknowledge request

            // Send a 100 status back if it makes sense (response not committed
            // yet, and client specified an expectation for 100-continue)

            if ((response.isCommitted()) || !expectation)
                return;

            inputBuffer.setSwallowInput(true);
            try {
                outputBuffer.sendAck();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }

        } else if (actionCode == ActionCode.ACTION_CLIENT_FLUSH) {

            try {
                outputBuffer.flush();
            } catch (IOException e) {
                // Set error flag
                error = true;
                response.setErrorException(e);
            }

        } else if (actionCode == ActionCode.ACTION_CLOSE) {
            // Close

            // End the processing of the current request, and stop any further
            // transactions with the client

            comet = false;
            async = false;
            try {
                outputBuffer.endRequest();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }

        } else if (actionCode == ActionCode.ACTION_RESET) {

            // Reset response

            // Note: This must be called before the response is committed

            outputBuffer.reset();

        } else if (actionCode == ActionCode.ACTION_CUSTOM) {

            // Do nothing

        } else if (actionCode == ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE) {

            // Get remote host address
            if (remoteAddr == null && (socket != 0)) {
                try {
                    long sa = Address.get(Socket.APR_REMOTE, socket);
                    remoteAddr = Address.getip(sa);
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.info"), e);
                }
            }
            request.remoteAddr().setString(remoteAddr);

        } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE) {

            // Get local host name
            if (localName == null && (socket != 0)) {
                try {
                    long sa = Address.get(Socket.APR_LOCAL, socket);
                    localName = Address.getnameinfo(sa, 0);
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.info"), e);
                }
            }
            request.localName().setString(localName);

        } else if (actionCode == ActionCode.ACTION_REQ_HOST_ATTRIBUTE) {

            // Get remote host name
            if (remoteHost == null && (socket != 0)) {
                try {
                    long sa = Address.get(Socket.APR_REMOTE, socket);
                    remoteHost = Address.getnameinfo(sa, 0);
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.info"), e);
                }
            }
            request.remoteHost().setString(remoteHost);

        } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE) {

            // Get local host address
            if (localAddr == null && (socket != 0)) {
                try {
                    long sa = Address.get(Socket.APR_LOCAL, socket);
                    localAddr = Address.getip(sa);
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.info"), e);
                }
            }

            request.localAddr().setString(localAddr);

        } else if (actionCode == ActionCode.ACTION_REQ_REMOTEPORT_ATTRIBUTE) {

            // Get remote port
            if (remotePort == -1 && (socket != 0)) {
                try {
                    long sa = Address.get(Socket.APR_REMOTE, socket);
                    Sockaddr addr = Address.getInfo(sa);
                    remotePort = addr.port;
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.info"), e);
                }
            }
            request.setRemotePort(remotePort);

        } else if (actionCode == ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE) {

            // Get local port
            if (localPort == -1 && (socket != 0)) {
                try {
                    long sa = Address.get(Socket.APR_LOCAL, socket);
                    Sockaddr addr = Address.getInfo(sa);
                    localPort = addr.port;
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.info"), e);
                }
            }
            request.setLocalPort(localPort);

        } else if (actionCode == ActionCode.ACTION_REQ_SSL_ATTRIBUTE ) {

            if (ssl && (socket != 0)) {
                try {
                    // Cipher suite
                    Object sslO = SSLSocket.getInfoS(socket, SSL.SSL_INFO_CIPHER);
                    if (sslO != null) {
                        request.setAttribute(AbstractEndpoint.CIPHER_SUITE_KEY, sslO);
                    }
                    // Get client certificate and the certificate chain if present
                    // certLength == -1 indicates an error
                    int certLength = SSLSocket.getInfoI(socket, SSL.SSL_INFO_CLIENT_CERT_CHAIN);
                    byte[] clientCert = SSLSocket.getInfoB(socket, SSL.SSL_INFO_CLIENT_CERT);
                    X509Certificate[] certs = null;
                    if (clientCert != null  && certLength > -1) {
                        certs = new X509Certificate[certLength + 1];
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        certs[0] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(clientCert));
                        for (int i = 0; i < certLength; i++) {
                            byte[] data = SSLSocket.getInfoB(socket, SSL.SSL_INFO_CLIENT_CERT_CHAIN + i);
                            certs[i+1] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data));
                        }
                    }
                    if (certs != null) {
                        request.setAttribute(AbstractEndpoint.CERTIFICATE_KEY, certs);
                    }
                    // User key size
                    sslO = new Integer(SSLSocket.getInfoI(socket, SSL.SSL_INFO_CIPHER_USEKEYSIZE));
                    request.setAttribute(AbstractEndpoint.KEY_SIZE_KEY, sslO);

                    // SSL session ID
                    sslO = SSLSocket.getInfoS(socket, SSL.SSL_INFO_SESSION_ID);
                    if (sslO != null) {
                        request.setAttribute(AbstractEndpoint.SESSION_ID_KEY, sslO);
                    }
                    //TODO provide a hook to enable the SSL session to be
                    // invalidated. Set AprEndpoint.SESSION_MGR req attr
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.ssl"), e);
                }
            }

        } else if (actionCode == ActionCode.ACTION_REQ_SSL_CERTIFICATE) {

            if (ssl && (socket != 0)) {
                // Consume and buffer the request body, so that it does not
                // interfere with the client's handshake messages
                InputFilter[] inputFilters = inputBuffer.getFilters();
                ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER]).setLimit(maxSavePostSize);
                inputBuffer.addActiveFilter(inputFilters[Constants.BUFFERED_FILTER]);
                try {
                    // Configure connection to require a certificate
                    SSLSocket.setVerify(socket, SSL.SSL_CVERIFY_REQUIRE,
                            endpoint.getSSLVerifyDepth());
                    // Renegotiate certificates
                    if (SSLSocket.renegotiate(socket) == 0) {
                        // Don't look for certs unless we know renegotiation worked.
                        // Get client certificate and the certificate chain if present
                        // certLength == -1 indicates an error 
                        int certLength = SSLSocket.getInfoI(socket,SSL.SSL_INFO_CLIENT_CERT_CHAIN);
                        byte[] clientCert = SSLSocket.getInfoB(socket, SSL.SSL_INFO_CLIENT_CERT);
                        X509Certificate[] certs = null;
                        if (clientCert != null && certLength > -1) {
                            certs = new X509Certificate[certLength + 1];
                            CertificateFactory cf = CertificateFactory.getInstance("X.509");
                            certs[0] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(clientCert));
                            for (int i = 0; i < certLength; i++) {
                                byte[] data = SSLSocket.getInfoB(socket, SSL.SSL_INFO_CLIENT_CERT_CHAIN + i);
                                certs[i+1] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data));
                            }
                        }
                        if (certs != null) {
                            request.setAttribute(AbstractEndpoint.CERTIFICATE_KEY, certs);
                        }
                    }
                } catch (Exception e) {
                    log.warn(sm.getString("http11processor.socket.ssl"), e);
                }
            }

        } else if (actionCode == ActionCode.ACTION_REQ_SET_BODY_REPLAY) {
            ByteChunk body = (ByteChunk) param;
            
            InputFilter savedBody = new SavedRequestInputFilter(body);
            savedBody.setRequest(request);
            
            InternalAprInputBuffer internalBuffer = (InternalAprInputBuffer)
                request.getInputBuffer();
            internalBuffer.addActiveFilter(savedBody);
            
        } else if (actionCode == ActionCode.ACTION_AVAILABLE) {
            request.setAvailable(inputBuffer.available());
        } else if (actionCode == ActionCode.ACTION_COMET_BEGIN) {
            comet = true;
        } else if (actionCode == ActionCode.ACTION_COMET_END) {
            comet = false;
        } else if (actionCode == ActionCode.ACTION_COMET_CLOSE) {
            //no op
        } else if (actionCode == ActionCode.ACTION_COMET_SETTIMEOUT) {
            //no op
        } else if (actionCode == ActionCode.ACTION_ASYNC_START) {
            //TODO SERVLET3 - async
            async = true;
        } else if (actionCode == ActionCode.ACTION_ASYNC_COMPLETE) {
          //TODO SERVLET3 - async - that is bit hacky -
            AtomicBoolean dispatch = (AtomicBoolean)param;
            RequestInfo rp = request.getRequestProcessor();
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) { //async handling
                dispatch.set(true);
                endpoint.getHandler().asyncDispatch(this.socket, SocketStatus.STOP);
            } else {
                dispatch.set(false);
            }
        } else if (actionCode == ActionCode.ACTION_ASYNC_SETTIMEOUT) {
          //TODO SERVLET3 - async
            if (param==null) return;
            if (socket==0) return;
            long timeout = ((Long)param).longValue();
            Socket.timeoutSet(socket, timeout * 1000);
        } else if (actionCode == ActionCode.ACTION_ASYNC_DISPATCH) {
            RequestInfo rp = request.getRequestProcessor();
            AtomicBoolean dispatch = (AtomicBoolean)param;
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) {//async handling
                endpoint.getPoller().add(this.socket);
                dispatch.set(true);
            } else {
                dispatch.set(true);
            }
        }
        

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
                for (int i = 0; i < restrictedUserAgents.length; i++) {
                    if (restrictedUserAgents[i].matcher(userAgentValue).matches()) {
                        http11 = false;
                        keepAlive = false;
                        break;
                    }
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
        if (endpoint.getUseSendfile()) {
            request.setAttribute("org.apache.tomcat.sendfile.support", Boolean.TRUE);
        }
        // Advertise comet support through a request attribute
        request.setAttribute("org.apache.tomcat.comet.support", Boolean.TRUE);
        
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


    /**
     * When committing the response, we have to validate the set of headers, as
     * well as setup the response filters.
     */
    protected void prepareResponse() {

        boolean entityBody = true;
        contentDelimitation = false;

        OutputFilter[] outputFilters = outputBuffer.getFilters();

        if (http09 == true) {
            // HTTP/0.9
            outputBuffer.addActiveFilter
                (outputFilters[Constants.IDENTITY_FILTER]);
            return;
        }

        int statusCode = response.getStatus();
        if ((statusCode == 204) || (statusCode == 205)
            || (statusCode == 304)) {
            // No entity body
            outputBuffer.addActiveFilter
                (outputFilters[Constants.VOID_FILTER]);
            entityBody = false;
            contentDelimitation = true;
        }

        MessageBytes methodMB = request.method();
        if (methodMB.equals("HEAD")) {
            // No entity body
            outputBuffer.addActiveFilter
                (outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }

        // Sendfile support
        if (endpoint.getUseSendfile()) {
            String fileName = (String) request.getAttribute("org.apache.tomcat.sendfile.filename");
            if (fileName != null) {
                // No entity body sent here
                outputBuffer.addActiveFilter
                    (outputFilters[Constants.VOID_FILTER]);
                contentDelimitation = true;
                sendfileData = new AprEndpoint.SendfileData();
                sendfileData.fileName = fileName;
                sendfileData.start = 
                    ((Long) request.getAttribute("org.apache.tomcat.sendfile.start")).longValue();
                sendfileData.end = 
                    ((Long) request.getAttribute("org.apache.tomcat.sendfile.end")).longValue();
            }
        }
        
        // Check for compression
        boolean useCompression = false;
        if (entityBody && (compressionLevel > 0) && (sendfileData == null)) {
            useCompression = isCompressable();
            // Change content-length to -1 to force chunking
            if (useCompression) {
                response.setContentLength(-1);
            }
        }

        MimeHeaders headers = response.getMimeHeaders();
        if (!entityBody) {
            response.setContentLength(-1);
        } else {
            String contentType = response.getContentType();
            if (contentType != null) {
                headers.setValue("Content-Type").setString(contentType);
            }
            String contentLanguage = response.getContentLanguage();
            if (contentLanguage != null) {
                headers.setValue("Content-Language")
                    .setString(contentLanguage);
            }
        }

        long contentLength = response.getContentLengthLong();
        if (contentLength != -1) {
            headers.setValue("Content-Length").setLong(contentLength);
            outputBuffer.addActiveFilter
                (outputFilters[Constants.IDENTITY_FILTER]);
            contentDelimitation = true;
        } else {
            if (entityBody && http11) {
                outputBuffer.addActiveFilter
                    (outputFilters[Constants.CHUNKED_FILTER]);
                contentDelimitation = true;
                headers.addValue(Constants.TRANSFERENCODING).setString(Constants.CHUNKED);
            } else {
                outputBuffer.addActiveFilter
                    (outputFilters[Constants.IDENTITY_FILTER]);
            }
        }

        if (useCompression) {
            outputBuffer.addActiveFilter(outputFilters[Constants.GZIP_FILTER]);
            headers.setValue("Content-Encoding").setString("gzip");
            // Make Proxies happy via Vary (from mod_deflate)
            MessageBytes vary = headers.getValue("Vary");
            if (vary == null) {
                // Add a new Vary header
                headers.setValue("Vary").setString("Accept-Encoding");
            } else if (vary.equals("*")) {
                // No action required
            } else {
                // Merge into current header
                headers.setValue("Vary").setString(
                        vary.getString() + ",Accept-Encoding");
            }
        }

        // Add date header
        headers.setValue("Date").setString(FastHttpDateFormat.getCurrentDate());

        // FIXME: Add transfer encoding header

        if ((entityBody) && (!contentDelimitation)) {
            // Mark as close the connection after the request, and add the
            // connection: close header
            keepAlive = false;
        }

        // If we know that the request is bad this early, add the
        // Connection: close header.
        keepAlive = keepAlive && !statusDropsConnection(statusCode);
        if (!keepAlive) {
            headers.addValue(Constants.CONNECTION).setString(Constants.CLOSE);
        } else if (!http11 && !error) {
            headers.addValue(Constants.CONNECTION).setString(Constants.KEEPALIVE);
        }

        // Build the response header
        outputBuffer.sendStatus();

        // Add server header
        if (server != null) {
            // Always overrides anything the app might set
            headers.setValue("Server").setString(server);
        } else if (headers.getValue("Server") == null) {
            // If app didn't set the header, use the default
            outputBuffer.write(Constants.SERVER_BYTES);
        }

        int size = headers.size();
        for (int i = 0; i < size; i++) {
            outputBuffer.sendHeader(headers.getName(i), headers.getValue(i));
        }
        outputBuffer.endHeaders();

    }


    /**
     * Initialize standard input and output filters.
     */
    protected void initializeFilters() {

        // Create and add the identity filters.
        inputBuffer.addFilter(new IdentityInputFilter());
        outputBuffer.addFilter(new IdentityOutputFilter());

        // Create and add the chunked filters.
        inputBuffer.addFilter(new ChunkedInputFilter());
        outputBuffer.addFilter(new ChunkedOutputFilter());

        // Create and add the void filters.
        inputBuffer.addFilter(new VoidInputFilter());
        outputBuffer.addFilter(new VoidOutputFilter());

        // Create and add buffered input filter
        inputBuffer.addFilter(new BufferedInputFilter());

        // Create and add the chunked filters.
        //inputBuffer.addFilter(new GzipInputFilter());
        outputBuffer.addFilter(new GzipOutputFilter());

    }


    /**
     * Add an input filter to the current request.
     *
     * @return false if the encoding was not found (which would mean it is
     * unsupported)
     */
    protected boolean addInputFilter(InputFilter[] inputFilters,
                                     String encodingName) {
        if (encodingName.equals("identity")) {
            // Skip
        } else if (encodingName.equals("chunked")) {
            inputBuffer.addActiveFilter
                (inputFilters[Constants.CHUNKED_FILTER]);
            contentDelimitation = true;
        } else {
            for (int i = 2; i < inputFilters.length; i++) {
                if (inputFilters[i].getEncodingName()
                    .toString().equals(encodingName)) {
                    inputBuffer.addActiveFilter(inputFilters[i]);
                    return true;
                }
            }
            return false;
        }
        return true;
    }

}

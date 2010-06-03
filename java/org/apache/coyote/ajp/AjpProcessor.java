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

package org.apache.coyote.ajp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.HexUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.JIoEndpoint;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.res.StringManager;


/**
 * Processes HTTP requests.
 *
 * @author Remy Maucherat
 * @author Henri Gomez
 * @author Dan Milstein
 * @author Keith Wannamaker
 * @author Kevin Seguin
 * @author Costin Manolache
 * @author Bill Barker
 */
public class AjpProcessor implements ActionHook {


    /**
     * Logger.
     */
    private static final Log log = LogFactory.getLog(AjpProcessor.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // ----------------------------------------------------------- Constructors


    public AjpProcessor(int packetSize, JIoEndpoint endpoint) {

        this.endpoint = endpoint;

        request = new Request();
        request.setInputBuffer(new SocketInputBuffer());

        response = new Response();
        response.setHook(this);
        response.setOutputBuffer(new SocketOutputBuffer());
        request.setResponse(response);

        this.packetSize = packetSize;
        requestHeaderMessage = new AjpMessage(packetSize);
        responseHeaderMessage = new AjpMessage(packetSize);
        bodyMessage = new AjpMessage(packetSize);

        // Set the get body message buffer
        AjpMessage getBodyMessage = new AjpMessage(16);
        getBodyMessage.reset();
        getBodyMessage.appendByte(Constants.JK_AJP13_GET_BODY_CHUNK);
        // Adjust allowed size if packetSize != default (Constants.MAX_PACKET_SIZE)
        getBodyMessage.appendInt(Constants.MAX_READ_SIZE + packetSize - Constants.MAX_PACKET_SIZE);
        getBodyMessage.end();
        getBodyMessageArray = new byte[getBodyMessage.getLen()];
        System.arraycopy(getBodyMessage.getBuffer(), 0, getBodyMessageArray, 
                         0, getBodyMessage.getLen());

        // Cause loading of HexUtils
        HexUtils.load();

        // Cause loading of HttpMessages
        HttpMessages.getMessage(200);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Associated adapter.
     */
    protected Adapter adapter = null;


    /**
     * Request object.
     */
    protected Request request = null;


    /**
     * Response object.
     */
    protected Response response = null;


    /**
     * The socket timeout used when reading the first block of the request
     * header.
     */
    protected int packetSize;

    /**
     * Header message. Note that this header is merely the one used during the
     * processing of the first message of a "request", so it might not be a request
     * header. It will stay unchanged during the processing of the whole request.
     */
    protected AjpMessage requestHeaderMessage = null;


    /**
     * Message used for response header composition.
     */
    protected AjpMessage responseHeaderMessage = null;


    /**
     * Body message.
     */
    protected AjpMessage bodyMessage = null;


    /**
     * Body message.
     */
    protected MessageBytes bodyBytes = MessageBytes.newInstance();


    /**
     * State flag.
     */
    protected boolean started = false;


    /**
     * Error flag.
     */
    protected boolean error = false;


    /**
     * Socket associated with the current connection.
     */
    protected SocketWrapper<Socket> socket;

    
    /**
     * Input stream.
     */
    protected InputStream input;
    
    
    /**
     * Output stream.
     */
    protected OutputStream output;
    

    /**
     * Host name (used to avoid useless B2C conversion on the host name).
     */
    protected char[] hostNameC = new char[0];


    /**
     * Associated endpoint.
     */
    protected JIoEndpoint endpoint;


    /**
     * Temp message bytes used for processing.
     */
    protected MessageBytes tmpMB = MessageBytes.newInstance();


    /**
     * Byte chunk for certs.
     */
    protected MessageBytes certificates = MessageBytes.newInstance();


    /**
     * End of stream flag.
     */
    protected boolean endOfStream = false;


    /**
     * Body empty flag.
     */
    protected boolean empty = true;


    /**
     * First read.
     */
    protected boolean first = true;


    /**
     * Replay read.
     */
    protected boolean replay = false;


    /**
     * Finished response.
     */
    protected boolean finished = false;


    /**
     * Direct buffer used for sending right away a get body message.
     */
    protected final byte[] getBodyMessageArray;


    /**
     * Direct buffer used for sending right away a pong message.
     */
    protected static final byte[] pongMessageArray;


    /**
     * End message array.
     */
    protected static final byte[] endMessageArray;

    /**
     * Flush message array.
     */
    protected static final byte[] flushMessageArray;
    
    /**
     * Async used
     */
    protected boolean async = false;


    // ----------------------------------------------------- Static Initializer


    static {

        // Set the read body message buffer
        AjpMessage pongMessage = new AjpMessage(16);
        pongMessage.reset();
        pongMessage.appendByte(Constants.JK_AJP13_CPONG_REPLY);
        pongMessage.end();
        pongMessageArray = new byte[pongMessage.getLen()];
        System.arraycopy(pongMessage.getBuffer(), 0, pongMessageArray, 
                0, pongMessage.getLen());

        // Allocate the end message array
        AjpMessage endMessage = new AjpMessage(16);
        endMessage.reset();
        endMessage.appendByte(Constants.JK_AJP13_END_RESPONSE);
        endMessage.appendByte(1);
        endMessage.end();
        endMessageArray = new byte[endMessage.getLen()];
        System.arraycopy(endMessage.getBuffer(), 0, endMessageArray, 0,
                endMessage.getLen());

        // Allocate the flush message array
        AjpMessage flushMessage = new AjpMessage(16);
        flushMessage.reset();
        flushMessage.appendByte(Constants.JK_AJP13_SEND_BODY_CHUNK);
        flushMessage.appendInt(0);
        flushMessage.appendByte(0);
        flushMessage.end();
        flushMessageArray = new byte[flushMessage.getLen()];
        System.arraycopy(flushMessage.getBuffer(), 0, flushMessageArray, 0,
                flushMessage.getLen());

    }


    // ------------------------------------------------------------- Properties


    /**
     * Use Tomcat authentication ?
     */
    protected boolean tomcatAuthentication = true;
    public boolean getTomcatAuthentication() { return tomcatAuthentication; }
    public void setTomcatAuthentication(boolean tomcatAuthentication) { this.tomcatAuthentication = tomcatAuthentication; }


    /**
     * Required secret.
     */
    protected String requiredSecret = null;
    public void setRequiredSecret(String requiredSecret) { this.requiredSecret = requiredSecret; }


    /**
     * The number of milliseconds Tomcat will wait for a subsequent request
     * before closing the connection. The default is the same as for
     * Apache HTTP Server (15 000 milliseconds).
     */
    protected int keepAliveTimeout = -1;
    public int getKeepAliveTimeout() { return keepAliveTimeout; }
    public void setKeepAliveTimeout(int timeout) { keepAliveTimeout = timeout; }


    // --------------------------------------------------------- Public Methods


    /** Get the request associated with this processor.
     *
     * @return The request
     */
    public Request getRequest() {
        return request;
    }


    /**
     * Process pipelined HTTP requests using the specified input and output
     * streams.
     *
     * @throws IOException error during an I/O operation
     */
    public SocketState process(SocketWrapper<Socket> socket)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the socket
        this.socket = socket;
        input = socket.getSocket().getInputStream();
        output = socket.getSocket().getOutputStream();
        int soTimeout = -1;
        if (keepAliveTimeout > 0) {
            soTimeout = socket.getSocket().getSoTimeout();
        }

        // Error flag
        error = false;

        while (started && !error) {

            // Parsing the request header
            try {
                // Set keep alive timeout if enabled
                if (keepAliveTimeout > 0) {
                    socket.getSocket().setSoTimeout(keepAliveTimeout);
                }
                // Get first message of the request
                if (!readMessage(requestHeaderMessage)) {
                    // This means a connection timeout
                    rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
                    break;
                }
                // Set back timeout if keep alive timeout is enabled
                if (keepAliveTimeout > 0) {
                    socket.getSocket().setSoTimeout(soTimeout);
                }
                // Check message type, process right away and break if
                // not regular request processing
                int type = requestHeaderMessage.getByte();
                if (type == Constants.JK_AJP13_CPING_REQUEST) {
                    try {
                        output.write(pongMessageArray);
                    } catch (IOException e) {
                        error = true;
                    }
                    continue;
                } else if(type != Constants.JK_AJP13_FORWARD_REQUEST) {
                    // Usually the servlet didn't read the previous request body
                    if(log.isDebugEnabled()) {
                        log.debug("Unexpected message: "+type);
                    }
                    continue;
                }

                request.setStartTime(System.currentTimeMillis());
            } catch (IOException e) {
                error = true;
                break;
            } catch (Throwable t) {
                log.debug(sm.getString("ajpprocessor.header.error"), t);
                // 400 - Bad Request
                response.setStatus(400);
                adapter.log(request, response, 0);
                error = true;
            }

            // Setting up filters, and parse some request headers
            rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
            try {
                prepareRequest();
            } catch (Throwable t) {
                log.debug(sm.getString("ajpprocessor.request.prepare"), t);
                // 400 - Internal Server Error
                response.setStatus(400);
                adapter.log(request, response, 0);
                error = true;
            }

            // Process the request in the adapter
            if (!error) {
                try {
                    rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                    adapter.service(request, response);
                } catch (InterruptedIOException e) {
                    error = true;
                } catch (Throwable t) {
                    log.error(sm.getString("ajpprocessor.request.process"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    adapter.log(request, response, 0);
                    error = true;
                }
            }
            
            if (async && !error) {
                break;
            }

            // Finish the response if not done yet
            if (!finished) {
                try {
                    finish();
                } catch (Throwable t) {
                    error = true;
                }
            }

            // If there was an error, make sure the request is counted as
            // and error, and update the statistics counter
            if (error) {
                response.setStatus(500);
            }
            request.updateCounters();

            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);
            recycle();

        }
        if (async && !error) {
            rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
            return SocketState.LONG;
        } else {
            rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
            recycle();
            input = null;
            output = null;
            return SocketState.CLOSED;
        }
        
    }

    public SocketState asyncDispatch(SocketStatus status) throws IOException {

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
                input = null;
                output = null;
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
            input = null;
            output = null;
            return SocketState.CLOSED;
        }
        
        
        
        
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

            if (response.isCommitted())
                return;

            // Validate and write response headers
            try {
                prepareResponse();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }

        } else if (actionCode == ActionCode.ACTION_CLIENT_FLUSH) {

            if (!response.isCommitted()) {
                // Validate and write response headers
                try {
                    prepareResponse();
                } catch (IOException e) {
                    // Set error flag
                    error = true;
                    return;
                }
            }

            try {
                flush();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }

        } else if (actionCode == ActionCode.ACTION_CLOSE) {
            // Close
            async = false;
            // End the processing of the current request, and stop any further
            // transactions with the client

            try {
                finish();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }

        } else if (actionCode == ActionCode.ACTION_START) {

            started = true;

        } else if (actionCode == ActionCode.ACTION_STOP) {

            started = false;

        } else if (actionCode == ActionCode.ACTION_REQ_SSL_ATTRIBUTE ) {

            if (!certificates.isNull()) {
                ByteChunk certData = certificates.getByteChunk();
                X509Certificate jsseCerts[] = null;
                ByteArrayInputStream bais =
                    new ByteArrayInputStream(certData.getBytes(),
                            certData.getStart(),
                            certData.getLength());
                // Fill the  elements.
                try {
                    CertificateFactory cf =
                        CertificateFactory.getInstance("X.509");
                    while(bais.available() > 0) {
                        X509Certificate cert = (X509Certificate)
                            cf.generateCertificate(bais);
                        if(jsseCerts == null) {
                            jsseCerts = new X509Certificate[1];
                            jsseCerts[0] = cert;
                        } else {
                            X509Certificate [] temp = new X509Certificate[jsseCerts.length+1];
                            System.arraycopy(jsseCerts,0,temp,0,jsseCerts.length);
                            temp[jsseCerts.length] = cert;
                            jsseCerts = temp;
                        }
                    }
                } catch (java.security.cert.CertificateException e) {
                    log.error(sm.getString("ajpprocessor.certs.fail"), e);
                    return;
                }
                request.setAttribute(AbstractEndpoint.CERTIFICATE_KEY, jsseCerts);
            }

        } else if (actionCode == ActionCode.ACTION_REQ_HOST_ATTRIBUTE) {

            // Get remote host name using a DNS resolution
            if (request.remoteHost().isNull()) {
                try {
                    request.remoteHost().setString(InetAddress.getByName
                            (request.remoteAddr().toString()).getHostName());
                } catch (IOException iex) {
                    // Ignore
                }
            }

        } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE) {

            // Copy from local name for now, which should simply be an address
            request.localAddr().setString(request.localName().toString());

        } else if (actionCode == ActionCode.ACTION_REQ_SET_BODY_REPLAY) {

            // Set the given bytes as the content
            ByteChunk bc = (ByteChunk) param;
            int length = bc.getLength();
            bodyBytes.setBytes(bc.getBytes(), bc.getStart(), length);
            request.setContentLength(length);
            first = false;
            empty = false;
            replay = true;

        }  else if (actionCode == ActionCode.ACTION_ASYNC_START) {
            //TODO SERVLET3 - async
            async = true;
        } else if (actionCode == ActionCode.ACTION_ASYNC_COMPLETE) {
          //TODO SERVLET3 - async
            AtomicBoolean dispatch = (AtomicBoolean)param;
            RequestInfo rp = request.getRequestProcessor();
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) { //async handling
                dispatch.set(true);
                endpoint.processSocket(this.socket, SocketStatus.STOP);
            } else {
                dispatch.set(false);
            }
        } else if (actionCode == ActionCode.ACTION_ASYNC_SETTIMEOUT) {
          //TODO SERVLET3 - async
            if (param==null) return;
            long timeout = ((Long)param).longValue();
            //if we are not piggy backing on a worker thread, set the timeout
            socket.setTimeout(timeout);
        } else if (actionCode == ActionCode.ACTION_ASYNC_DISPATCH) {
            RequestInfo rp = request.getRequestProcessor();
            AtomicBoolean dispatch = (AtomicBoolean)param;
            if ( rp.getStage() != org.apache.coyote.Constants.STAGE_SERVICE ) {//async handling
                endpoint.processSocket(this.socket, SocketStatus.OPEN);
                dispatch.set(true);
            } else { 
                dispatch.set(true);
            }
        }


    }


    // ------------------------------------------------------ Connector Methods


    /**
     * Set the associated adapter.
     *
     * @param adapter the new adapter
     */
    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
    }


    /**
     * Get the associated adapter.
     *
     * @return the associated adapter
     */
    public Adapter getAdapter() {
        return adapter;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * After reading the request headers, we have to setup the request filters.
     */
    protected void prepareRequest() {

        // Translate the HTTP method code to a String.
        byte methodCode = requestHeaderMessage.getByte();
        if (methodCode != Constants.SC_M_JK_STORED) {
            String methodName = Constants.methodTransArray[methodCode - 1];
            request.method().setString(methodName);
        }

        requestHeaderMessage.getBytes(request.protocol());
        requestHeaderMessage.getBytes(request.requestURI());

        requestHeaderMessage.getBytes(request.remoteAddr());
        requestHeaderMessage.getBytes(request.remoteHost());
        requestHeaderMessage.getBytes(request.localName());
        request.setLocalPort(requestHeaderMessage.getInt());

        boolean isSSL = requestHeaderMessage.getByte() != 0;
        if (isSSL) {
            request.scheme().setString("https");
        }

        // Decode headers
        MimeHeaders headers = request.getMimeHeaders();

        int hCount = requestHeaderMessage.getInt();
        for(int i = 0 ; i < hCount ; i++) {
            String hName = null;

            // Header names are encoded as either an integer code starting
            // with 0xA0, or as a normal string (in which case the first
            // two bytes are the length).
            int isc = requestHeaderMessage.peekInt();
            int hId = isc & 0xFF;

            MessageBytes vMB = null;
            isc &= 0xFF00;
            if(0xA000 == isc) {
                requestHeaderMessage.getInt(); // To advance the read position
                hName = Constants.headerTransArray[hId - 1];
                vMB = headers.addValue(hName);
            } else {
                // reset hId -- if the header currently being read
                // happens to be 7 or 8 bytes long, the code below
                // will think it's the content-type header or the
                // content-length header - SC_REQ_CONTENT_TYPE=7,
                // SC_REQ_CONTENT_LENGTH=8 - leading to unexpected
                // behaviour.  see bug 5861 for more information.
                hId = -1;
                requestHeaderMessage.getBytes(tmpMB);
                ByteChunk bc = tmpMB.getByteChunk();
                vMB = headers.addValue(bc.getBuffer(),
                        bc.getStart(), bc.getLength());
            }

            requestHeaderMessage.getBytes(vMB);

            if (hId == Constants.SC_REQ_CONTENT_LENGTH ||
                    (hId == -1 && tmpMB.equalsIgnoreCase("Content-Length"))) {
                // just read the content-length header, so set it
                long cl = vMB.getLong();
                if(cl < Integer.MAX_VALUE)
                    request.setContentLength( (int)cl );
            } else if (hId == Constants.SC_REQ_CONTENT_TYPE ||
                    (hId == -1 && tmpMB.equalsIgnoreCase("Content-Type"))) {
                // just read the content-type header, so set it
                ByteChunk bchunk = vMB.getByteChunk();
                request.contentType().setBytes(bchunk.getBytes(),
                        bchunk.getOffset(),
                        bchunk.getLength());
            }
        }

        // Decode extra attributes
        boolean secret = false;
        byte attributeCode;
        while ((attributeCode = requestHeaderMessage.getByte())
                != Constants.SC_A_ARE_DONE) {

            switch (attributeCode) {

            case Constants.SC_A_REQ_ATTRIBUTE :
                requestHeaderMessage.getBytes(tmpMB);
                String n = tmpMB.toString();
                requestHeaderMessage.getBytes(tmpMB);
                String v = tmpMB.toString();
                /*
                 * AJP13 misses to forward the remotePort.
                 * Allow the AJP connector to add this info via
                 * a private request attribute.
                 * We will accept the forwarded data as the remote port,
                 * and remove it from the public list of request attributes.
                 */
                if(n.equals(Constants.SC_A_REQ_REMOTE_PORT)) {
                    try {
                        request.setRemotePort(Integer.parseInt(v));
                    } catch (NumberFormatException nfe) {
                    }
                } else {
                    request.setAttribute(n, v );
                }
                break;

            case Constants.SC_A_CONTEXT :
                requestHeaderMessage.getBytes(tmpMB);
                // nothing
                break;

            case Constants.SC_A_SERVLET_PATH :
                requestHeaderMessage.getBytes(tmpMB);
                // nothing
                break;

            case Constants.SC_A_REMOTE_USER :
                if (tomcatAuthentication) {
                    // ignore server
                    requestHeaderMessage.getBytes(tmpMB);
                } else {
                    requestHeaderMessage.getBytes(request.getRemoteUser());
                }
                break;

            case Constants.SC_A_AUTH_TYPE :
                if (tomcatAuthentication) {
                    // ignore server
                    requestHeaderMessage.getBytes(tmpMB);
                } else {
                    requestHeaderMessage.getBytes(request.getAuthType());
                }
                break;

            case Constants.SC_A_QUERY_STRING :
                requestHeaderMessage.getBytes(request.queryString());
                break;

            case Constants.SC_A_JVM_ROUTE :
                requestHeaderMessage.getBytes(request.instanceId());
                break;

            case Constants.SC_A_SSL_CERT :
                request.scheme().setString("https");
                // SSL certificate extraction is lazy, moved to JkCoyoteHandler
                requestHeaderMessage.getBytes(certificates);
                break;

            case Constants.SC_A_SSL_CIPHER :
                request.scheme().setString("https");
                requestHeaderMessage.getBytes(tmpMB);
                request.setAttribute(AbstractEndpoint.CIPHER_SUITE_KEY,
                                     tmpMB.toString());
                break;

            case Constants.SC_A_SSL_SESSION :
                request.scheme().setString("https");
                requestHeaderMessage.getBytes(tmpMB);
                request.setAttribute(AbstractEndpoint.SESSION_ID_KEY,
                                     tmpMB.toString());
                break;

            case Constants.SC_A_SSL_KEY_SIZE :
                request.setAttribute(AbstractEndpoint.KEY_SIZE_KEY,
                                     new Integer(requestHeaderMessage.getInt()));
                break;

            case Constants.SC_A_STORED_METHOD:
                requestHeaderMessage.getBytes(request.method());
                break;

            case Constants.SC_A_SECRET:
                requestHeaderMessage.getBytes(tmpMB);
                if (requiredSecret != null) {
                    secret = true;
                    if (!tmpMB.equals(requiredSecret)) {
                        response.setStatus(403);
                        adapter.log(request, response, 0);
                        error = true;
                    }
                }
                break;

            default:
                // Ignore unknown attribute for backward compatibility
                break;

            }

        }

        // Check if secret was submitted if required
        if ((requiredSecret != null) && !secret) {
            response.setStatus(403);
            adapter.log(request, response, 0);
            error = true;
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

        MessageBytes valueMB = request.getMimeHeaders().getValue("host");
        parseHost(valueMB);

    }


    /**
     * Parse host.
     */
    public void parseHost(MessageBytes valueMB) {

        if (valueMB == null || (valueMB != null && valueMB.isNull()) ) {
            // HTTP/1.0
            request.setServerPort(request.getLocalPort());
            try {
                request.serverName().duplicate(request.localName());
            } catch (IOException e) {
                response.setStatus(400);
                adapter.log(request, response, 0);
                error = true;
            }
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
            if (request.scheme().equalsIgnoreCase("https")) {
                // 443 - Default HTTPS port
                request.setServerPort(443);
            } else {
                // 80 - Default HTTTP port
                request.setServerPort(80);
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
    protected void prepareResponse()
        throws IOException {

        response.setCommitted(true);

        responseHeaderMessage.reset();
        responseHeaderMessage.appendByte(Constants.JK_AJP13_SEND_HEADERS);

        // HTTP header contents
        responseHeaderMessage.appendInt(response.getStatus());
        String message = null;
        if (org.apache.coyote.Constants.USE_CUSTOM_STATUS_MSG_IN_HEADER &&
                HttpMessages.isSafeInHttpHeader(response.getMessage())) {
            message = response.getMessage();
        }
        if (message == null){
            message = HttpMessages.getMessage(response.getStatus());
        }
        if (message == null) {
            // mod_jk + httpd 2.x fails with a null status message - bug 45026
            message = Integer.toString(response.getStatus());
        }
        tmpMB.setString(message);
        responseHeaderMessage.appendBytes(tmpMB);

        // Special headers
        MimeHeaders headers = response.getMimeHeaders();
        String contentType = response.getContentType();
        if (contentType != null) {
            headers.setValue("Content-Type").setString(contentType);
        }
        String contentLanguage = response.getContentLanguage();
        if (contentLanguage != null) {
            headers.setValue("Content-Language").setString(contentLanguage);
        }
        long contentLength = response.getContentLengthLong();
        if (contentLength >= 0) {
            headers.setValue("Content-Length").setLong(contentLength);
        }

        // Other headers
        int numHeaders = headers.size();
        responseHeaderMessage.appendInt(numHeaders);
        for (int i = 0; i < numHeaders; i++) {
            MessageBytes hN = headers.getName(i);
            int hC = Constants.getResponseAjpIndex(hN.toString());
            if (hC > 0) {
                responseHeaderMessage.appendInt(hC);
            }
            else {
                responseHeaderMessage.appendBytes(hN);
            }
            MessageBytes hV=headers.getValue(i);
            responseHeaderMessage.appendBytes(hV);
        }

        // Write to buffer
        responseHeaderMessage.end();
        output.write(responseHeaderMessage.getBuffer(), 0, responseHeaderMessage.getLen());

    }


    /**
     * Finish AJP response.
     */
    protected void finish()
        throws IOException {

        if (!response.isCommitted()) {
            // Validate and write response headers
            try {
                prepareResponse();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }
        }

        if (finished)
            return;

        finished = true;

        // Add the end message
        output.write(endMessageArray);

    }


    /**
     * Read at least the specified amount of bytes, and place them
     * in the input buffer.
     */
    protected boolean read(byte[] buf, int pos, int n)
        throws IOException {

        int read = 0;
        int res = 0;
        while (read < n) {
            res = input.read(buf, read + pos, n - read);
            if (res > 0) {
                read += res;
            } else {
                throw new IOException(sm.getString("ajpprotocol.failedread"));
            }
        }
        
        return true;

    }


    /** Receive a chunk of data. Called to implement the
     *  'special' packet in ajp13 and to receive the data
     *  after we send a GET_BODY packet
     */
    public boolean receive() throws IOException {

        first = false;
        bodyMessage.reset();
        if (!readMessage(bodyMessage)) {
            // Invalid message
            return false;
        }
        // No data received.
        if (bodyMessage.getLen() == 0) {
            // just the header
            // Don't mark 'end of stream' for the first chunk.
            return false;
        }
        int blen = bodyMessage.peekInt();
        if (blen == 0) {
            return false;
        }

        bodyMessage.getBytes(bodyBytes);
        empty = false;
        return true;
    }

    /**
     * Get more request body data from the web server and store it in the
     * internal buffer.
     *
     * @return true if there is more data, false if not.
     */
    private boolean refillReadBuffer() throws IOException {
        // If the server returns an empty packet, assume that that end of
        // the stream has been reached (yuck -- fix protocol??).
        // FORM support
        if (replay) {
            endOfStream = true; // we've read everything there is
        }
        if (endOfStream) {
            return false;
        }

        // Request more data immediately
        output.write(getBodyMessageArray);

        boolean moreData = receive();
        if( !moreData ) {
            endOfStream = true;
        }
        return moreData;
    }


    /**
     * Read an AJP message.
     *
     * @return true if the message has been read, false if the short read
     *         didn't return anything
     * @throws IOException any other failure, including incomplete reads
     */
    protected boolean readMessage(AjpMessage message)
        throws IOException {

        byte[] buf = message.getBuffer();
        int headerLength = message.getHeaderLength();

        read(buf, 0, headerLength);

        int messageLength = message.processHeader();
        if (messageLength < 0) {
            // Invalid AJP header signature
            // TODO: Throw some exception and close the connection to frontend.
            return false;
        }
        else if (messageLength == 0) {
            // Zero length message.
            return true;
        }
        else {
            read(buf, headerLength, messageLength);
            return true;
        }
    }


    /**
     * Recycle the processor.
     */
    public void recycle() {

        // Recycle Request object
        first = true;
        endOfStream = false;
        empty = true;
        replay = false;
        finished = false;
        request.recycle();
        response.recycle();
        certificates.recycle();
        async = false;

    }


    /**
     * Callback to write data from the buffer.
     */
    protected void flush()
        throws IOException {
        // Send the flush message
        output.write(flushMessageArray);
    }


    // ------------------------------------- InputStreamInputBuffer Inner Class


    /**
     * This class is an input buffer which will read its data from an input
     * stream.
     */
    protected class SocketInputBuffer
        implements InputBuffer {


        /**
         * Read bytes into the specified chunk.
         */
        public int doRead(ByteChunk chunk, Request req )
            throws IOException {

            if (endOfStream) {
                return -1;
            }
            if (first && req.getContentLengthLong() > 0) {
                // Handle special first-body-chunk
                if (!receive()) {
                    return 0;
                }
            } else if (empty) {
                if (!refillReadBuffer()) {
                    return -1;
                }
            }
            ByteChunk bc = bodyBytes.getByteChunk();
            chunk.setBytes(bc.getBuffer(), bc.getStart(), bc.getLength());
            empty = true;
            return chunk.getLength();

        }

    }


    // ----------------------------------- OutputStreamOutputBuffer Inner Class


    /**
     * This class is an output buffer which will write data to an output
     * stream.
     */
    protected class SocketOutputBuffer
        implements OutputBuffer {


        /**
         * Write chunk.
         */
        public int doWrite(ByteChunk chunk, Response res)
            throws IOException {

            if (!response.isCommitted()) {
                // Validate and write response headers
                try {
                    prepareResponse();
                } catch (IOException e) {
                    // Set error flag
                    error = true;
                }
            }

            int len = chunk.getLength();
            // 4 - hardcoded, byte[] marshaling overhead
            // Adjust allowed size if packetSize != default (Constants.MAX_PACKET_SIZE)
            int chunkSize = Constants.MAX_SEND_SIZE + packetSize - Constants.MAX_PACKET_SIZE;
            int off = 0;
            while (len > 0) {
                int thisTime = len;
                if (thisTime > chunkSize) {
                    thisTime = chunkSize;
                }
                len -= thisTime;
                responseHeaderMessage.reset();
                responseHeaderMessage.appendByte(Constants.JK_AJP13_SEND_BODY_CHUNK);
                responseHeaderMessage.appendBytes(chunk.getBytes(), chunk.getOffset() + off, thisTime);
                responseHeaderMessage.end();
                output.write(responseHeaderMessage.getBuffer(), 0, responseHeaderMessage.getLen());

                off += thisTime;
            }

            return chunk.getLength();

        }


    }


}

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
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.ContinueResponseTiming;
import org.apache.coyote.ErrorState;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.RequestInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.HttpMessages;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

/**
 * AJP Processor implementation.
 */
public class AjpProcessor extends AbstractProcessor {

    private static final Log log = LogFactory.getLog(AjpProcessor.class);
    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(AjpProcessor.class);


    /**
     * End message array.
     */
    private static final byte[] endMessageArray;
    private static final byte[] endAndCloseMessageArray;


    /**
     * Flush message array.
     */
    private static final byte[] flushMessageArray;


    /**
     * Pong message array.
     */
    private static final byte[] pongMessageArray;


    private static final Set<String> javaxAttributes;
    private static final Set<String> iisTlsAttributes;


    static {
        // Allocate the end message array
        AjpMessage endMessage = new AjpMessage(16);
        endMessage.reset();
        endMessage.appendByte(Constants.JK_AJP13_END_RESPONSE);
        endMessage.appendByte(1);
        endMessage.end();
        endMessageArray = new byte[endMessage.getLen()];
        System.arraycopy(endMessage.getBuffer(), 0, endMessageArray, 0,
                endMessage.getLen());

        // Allocate the end and close message array
        AjpMessage endAndCloseMessage = new AjpMessage(16);
        endAndCloseMessage.reset();
        endAndCloseMessage.appendByte(Constants.JK_AJP13_END_RESPONSE);
        endAndCloseMessage.appendByte(0);
        endAndCloseMessage.end();
        endAndCloseMessageArray = new byte[endAndCloseMessage.getLen()];
        System.arraycopy(endAndCloseMessage.getBuffer(), 0, endAndCloseMessageArray, 0,
                endAndCloseMessage.getLen());

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

        // Allocate the pong message array
        AjpMessage pongMessage = new AjpMessage(16);
        pongMessage.reset();
        pongMessage.appendByte(Constants.JK_AJP13_CPONG_REPLY);
        pongMessage.end();
        pongMessageArray = new byte[pongMessage.getLen()];
        System.arraycopy(pongMessage.getBuffer(), 0, pongMessageArray,
                0, pongMessage.getLen());

        // Build the Set of javax attributes
        Set<String> s = new HashSet<>();
        s.add("javax.servlet.request.cipher_suite");
        s.add("javax.servlet.request.key_size");
        s.add("javax.servlet.request.ssl_session");
        s.add("javax.servlet.request.X509Certificate");
        javaxAttributes= Collections.unmodifiableSet(s);

        Set<String> iis = new HashSet<>();
        iis.add("CERT_ISSUER");
        iis.add("CERT_SUBJECT");
        iis.add("CERT_COOKIE");
        iis.add("HTTPS_SERVER_SUBJECT");
        iis.add("CERT_FLAGS");
        iis.add("HTTPS_SECRETKEYSIZE");
        iis.add("CERT_SERIALNUMBER");
        iis.add("HTTPS_SERVER_ISSUER");
        iis.add("HTTPS_KEYSIZE");
        iisTlsAttributes = Collections.unmodifiableSet(iis);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * GetBody message array. Not static like the other message arrays since the
     * message varies with packetSize and that can vary per connector.
     */
    private final byte[] getBodyMessageArray;


    /**
     * AJP packet size.
     */
    private final int outputMaxChunkSize;

    /**
     * Header message. Note that this header is merely the one used during the
     * processing of the first message of a "request", so it might not be a
     * request header. It will stay unchanged during the processing of the whole
     * request.
     */
    private final AjpMessage requestHeaderMessage;


    /**
     * Message used for response composition.
     */
    private final AjpMessage responseMessage;


    /**
     * Location of next write of the response message (used with non-blocking
     * writes when the message may not be written in a single write). A value of
     * -1 indicates that no message has been written to the buffer.
     */
    private int responseMsgPos = -1;


    /**
     * Body message.
     */
    private final AjpMessage bodyMessage;


    /**
     * Body message.
     */
    private final MessageBytes bodyBytes = MessageBytes.newInstance();


    /**
     * Temp message bytes used for processing.
     */
    private final MessageBytes tmpMB = MessageBytes.newInstance();


    /**
     * Byte chunk for certs.
     */
    private final MessageBytes certificates = MessageBytes.newInstance();


    /**
     * End of stream flag.
     */
    private boolean endOfStream = false;


    /**
     * Request body empty flag.
     */
    private boolean empty = true;


    /**
     * First read.
     */
    private boolean first = true;


    /**
     * Indicates that a 'get body chunk' message has been sent but the body
     * chunk has not yet been received.
     */
    private boolean waitingForBodyMessage = false;


    /**
     * Replay read.
     */
    private boolean replay = false;


    /**
     * Should any response body be swallowed and not sent to the client.
     */
    private boolean swallowResponse = false;


    /**
     * Finished response.
     */
    private boolean responseFinished = false;


    /**
     * Bytes written to client for the current request.
     */
    private long bytesWritten = 0;




    // ------------------------------------------------------------ Constructor

    public AjpProcessor(int packetSize, AbstractEndpoint<?> endpoint) {

        super(endpoint);

        // Calculate maximum chunk size as packetSize may have been changed from
        // the default (Constants.MAX_PACKET_SIZE)
        this.outputMaxChunkSize =
                Constants.MAX_SEND_SIZE + packetSize - Constants.MAX_PACKET_SIZE;

        request.setInputBuffer(new SocketInputBuffer());

        requestHeaderMessage = new AjpMessage(packetSize);
        responseMessage = new AjpMessage(packetSize);
        bodyMessage = new AjpMessage(packetSize);

        // Set the getBody message buffer
        AjpMessage getBodyMessage = new AjpMessage(16);
        getBodyMessage.reset();
        getBodyMessage.appendByte(Constants.JK_AJP13_GET_BODY_CHUNK);
        // Adjust read size if packetSize != default (Constants.MAX_PACKET_SIZE)
        getBodyMessage.appendInt(Constants.MAX_READ_SIZE + packetSize -
                Constants.MAX_PACKET_SIZE);
        getBodyMessage.end();
        getBodyMessageArray = new byte[getBodyMessage.getLen()];
        System.arraycopy(getBodyMessage.getBuffer(), 0, getBodyMessageArray,
                0, getBodyMessage.getLen());

        response.setOutputBuffer(new SocketOutputBuffer());
    }


    // ------------------------------------------------------------- Properties


    /**
     * Send AJP flush packet when flushing.
     * An flush packet is a zero byte AJP13 SEND_BODY_CHUNK
     * packet. mod_jk and mod_proxy_ajp interprete this as
     * a request to flush data to the client.
     * AJP always does flush at the and of the response, so if
     * it is not important, that the packets get streamed up to
     * the client, do not use extra flush packets.
     * For compatibility and to stay on the safe side, flush
     * packets are enabled by default.
     */
    protected boolean ajpFlush = true;
    public boolean getAjpFlush() { return ajpFlush; }
    public void setAjpFlush(boolean ajpFlush) {
        this.ajpFlush = ajpFlush;
    }


    /**
     * The number of milliseconds Tomcat will wait for a subsequent request
     * before closing the connection. The default is -1 which is an infinite
     * timeout.
     */
    private int keepAliveTimeout = -1;
    public int getKeepAliveTimeout() { return keepAliveTimeout; }
    public void setKeepAliveTimeout(int timeout) { keepAliveTimeout = timeout; }


    /**
     * Use Tomcat authentication ?
     */
    private boolean tomcatAuthentication = true;
    public boolean getTomcatAuthentication() { return tomcatAuthentication; }
    public void setTomcatAuthentication(boolean tomcatAuthentication) {
        this.tomcatAuthentication = tomcatAuthentication;
    }


    /**
     * Use Tomcat authorization ?
     */
    private boolean tomcatAuthorization = false;
    public boolean getTomcatAuthorization() { return tomcatAuthorization; }
    public void setTomcatAuthorization(boolean tomcatAuthorization) {
        this.tomcatAuthorization = tomcatAuthorization;
    }


    /**
     * Required secret.
     */
    private String secret = null;
    @Deprecated
    public void setRequiredSecret(String requiredSecret) {
        setSecret(requiredSecret);
    }
    public void setSecret(String secret) {
        this.secret = secret;
    }


    /**
     * When client certificate information is presented in a form other than
     * instances of {@link java.security.cert.X509Certificate} it needs to be
     * converted before it can be used and this property controls which JSSE
     * provider is used to perform the conversion.
     */
    private String clientCertProvider = null;
    public String getClientCertProvider() { return clientCertProvider; }
    public void setClientCertProvider(String clientCertProvider) {
        this.clientCertProvider = clientCertProvider;
    }


    @Deprecated
    private boolean sendReasonPhrase = false;
    @Deprecated
    void setSendReasonPhrase(boolean sendReasonPhrase) {
        this.sendReasonPhrase = sendReasonPhrase;
    }


    private Pattern allowedRequestAttributesPattern;
    public void setAllowedRequestAttributesPattern(Pattern allowedRequestAttributesPattern) {
        this.allowedRequestAttributesPattern = allowedRequestAttributesPattern;
    }

    // --------------------------------------------------------- Public Methods

    @Override
    protected boolean flushBufferedWrite() throws IOException {
        if (hasDataToWrite()) {
            socketWrapper.flush(false);
            if (hasDataToWrite()) {
                // There is data to write but go via Response to
                // maintain a consistent view of non-blocking state
                response.checkRegisterForWrite();
                return true;
            }
        }
        return false;
    }


    @Override
    protected void dispatchNonBlockingRead() {
        if (available(true) > 0) {
            super.dispatchNonBlockingRead();
        }
    }


    @Override
    protected SocketState dispatchEndRequest() {
        // Set keep alive timeout for next request if enabled
        if (keepAliveTimeout > 0) {
            socketWrapper.setReadTimeout(keepAliveTimeout);
        }
        recycle();
        if (endpoint.isPaused()) {
            return SocketState.CLOSED;
        } else {
            return SocketState.OPEN;
        }
    }


    @Override
    public SocketState service(SocketWrapperBase<?> socket) throws IOException {

        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the socket
        this.socketWrapper = socket;

        int soTimeout = endpoint.getConnectionTimeout();
        boolean cping = false;
        // Expected to block on the first read as there should be at least one
        // AJP message to read.
        boolean firstRead = true;

        while (!getErrorState().isError() && !endpoint.isPaused()) {
            // Parsing the request header
            try {
                // Get first message of the request
                if (!readMessage(requestHeaderMessage, firstRead)) {
                    break;
                }
                firstRead = false;

                // Set back timeout if keep alive timeout is enabled
                if (keepAliveTimeout > 0) {
                    socketWrapper.setReadTimeout(soTimeout);
                }

                // Check message type, process right away and break if
                // not regular request processing
                int type = requestHeaderMessage.getByte();
                if (type == Constants.JK_AJP13_CPING_REQUEST) {
                    if (endpoint.isPaused()) {
                        recycle();
                        break;
                    }
                    cping = true;
                    try {
                        socketWrapper.write(true, pongMessageArray, 0, pongMessageArray.length);
                        socketWrapper.flush(true);
                    } catch (IOException e) {
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("Pong message failed", e);
                        }
                        setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                    }
                    recycle();
                    continue;
                } else if(type != Constants.JK_AJP13_FORWARD_REQUEST) {
                    // Unexpected packet type. Unread body packets should have
                    // been swallowed in finish().
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("Unexpected message: " + type);
                    }
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, null);
                    break;
                }
                request.setStartTime(System.currentTimeMillis());
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                break;
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                getLog().debug(sm.getString("ajpprocessor.header.error"), t);
                // 400 - Bad Request
                response.setStatus(400);
                setErrorState(ErrorState.CLOSE_CLEAN, t);
            }

            if (getErrorState().isIoAllowed()) {
                // Setting up filters, and parse some request headers
                rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
                try {
                    prepareRequest();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().debug(sm.getString("ajpprocessor.request.prepare"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, t);
                }
            }

            if (getErrorState().isIoAllowed() && !cping && endpoint.isPaused()) {
                // 503 - Service unavailable
                response.setStatus(503);
                setErrorState(ErrorState.CLOSE_CLEAN, null);
            }
            cping = false;

            // Process the request in the adapter
            if (getErrorState().isIoAllowed()) {
                try {
                    rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                    getAdapter().service(request, response);
                } catch (InterruptedIOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    getLog().error(sm.getString("ajpprocessor.request.process"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, t);
                    getAdapter().log(request, response, 0);
                }
            }

            if (isAsync() && !getErrorState().isError()) {
                break;
            }

            // Finish the response if not done yet
            if (!responseFinished && getErrorState().isIoAllowed()) {
                try {
                    action(ActionCode.COMMIT, null);
                    finishResponse();
                } catch (IOException ioe){
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    setErrorState(ErrorState.CLOSE_NOW, t);
                }
            }

            // If there was an error, make sure the request is counted as
            // and error, and update the statistics counter
            if (getErrorState().isError()) {
                response.setStatus(500);
            }
            request.updateCounters();

            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);
            // Set keep alive timeout for next request if enabled
            if (keepAliveTimeout > 0) {
                socketWrapper.setReadTimeout(keepAliveTimeout);
            }

            recycle();
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (getErrorState().isError() || endpoint.isPaused()) {
            return SocketState.CLOSED;
        } else {
            if (isAsync()) {
                return SocketState.LONG;
            } else {
                return SocketState.OPEN;
            }
        }
    }


    @Override
    public void recycle() {
        getAdapter().checkRecycled(request, response);
        super.recycle();
        request.recycle();
        response.recycle();
        first = true;
        endOfStream = false;
        waitingForBodyMessage = false;
        empty = true;
        replay = false;
        responseFinished = false;
        certificates.recycle();
        swallowResponse = false;
        bytesWritten = 0;
    }


    @Override
    public void pause() {
        // NOOP for AJP
    }


    // ------------------------------------------------------ Protected Methods

    // Methods used by SocketInputBuffer
    /**
     * Read an AJP body message. Used to read both the 'special' packet in ajp13
     * and to receive the data after we send a GET_BODY packet.
     *
     * @param block If there is no data available to read when this method is
     *              called, should this call block until data becomes available?
     *
     * @return <code>true</code> if at least one body byte was read, otherwise
     *         <code>false</code>
     */
    private boolean receive(boolean block) throws IOException {

        bodyMessage.reset();

        if (!readMessage(bodyMessage, block)) {
            return false;
        }

        waitingForBodyMessage = false;

        // No data received.
        if (bodyMessage.getLen() == 0) {
            // just the header
            return false;
        }
        int blen = bodyMessage.peekInt();
        if (blen == 0) {
            return false;
        }

        bodyMessage.getBodyBytes(bodyBytes);
        empty = false;
        return true;
    }


    /**
     * Read an AJP message.
     *
     * @param message   The message to populate
     * @param block If there is no data available to read when this method is
     *              called, should this call block until data becomes available?

     * @return true if the message has been read, false if no data was read
     *
     * @throws IOException any other failure, including incomplete reads
     */
    private boolean readMessage(AjpMessage message, boolean block)
        throws IOException {

        byte[] buf = message.getBuffer();

        if (!read(buf, 0, Constants.H_SIZE, block)) {
            return false;
        }

        int messageLength = message.processHeader(true);
        if (messageLength < 0) {
            // Invalid AJP header signature
            throw new IOException(sm.getString("ajpmessage.invalidLength",
                    Integer.valueOf(messageLength)));
        }
        else if (messageLength == 0) {
            // Zero length message.
            return true;
        } else {
            if (messageLength > message.getBuffer().length) {
                // Message too long for the buffer
                // Need to trigger a 400 response
                String msg = sm.getString("ajpprocessor.header.tooLong",
                        Integer.valueOf(messageLength), Integer.valueOf(buf.length));
                log.error(msg);
                throw new IllegalArgumentException(msg);
            }
            read(buf, Constants.H_SIZE, messageLength, true);
            return true;
        }
    }


    /**
     * Get more request body data from the web server and store it in the
     * internal buffer.
     * @param block <code>true</code> if this is blocking IO
     * @return <code>true</code> if there is more data,
     *  <code>false</code> if not.
     * @throws IOException An IO error occurred
     */
    protected boolean refillReadBuffer(boolean block) throws IOException {
        // When using replay (e.g. after FORM auth) all the data to read has
        // been buffered so there is no opportunity to refill the buffer.
        if (replay) {
            endOfStream = true; // we've read everything there is
        }
        if (endOfStream) {
            return false;
        }

        if (first) {
            first = false;
            long contentLength = request.getContentLengthLong();
            // - When content length > 0, AJP sends the first body message
            //   automatically.
            // - When content length == 0, AJP does not send a body message.
            // - When content length is unknown, AJP does not send the first
            //   body message automatically.
            if (contentLength > 0) {
                waitingForBodyMessage = true;
            } else if (contentLength == 0) {
                endOfStream = true;
                return false;
            }
        }

        // Request more data immediately
        if (!waitingForBodyMessage) {
            socketWrapper.write(true, getBodyMessageArray, 0, getBodyMessageArray.length);
            socketWrapper.flush(true);
            waitingForBodyMessage = true;
        }

        boolean moreData = receive(block);
        if (!moreData && !waitingForBodyMessage) {
            endOfStream = true;
        }
        return moreData;
    }


    /**
     * After reading the request headers, we have to setup the request filters.
     */
    private void prepareRequest() {

        // Translate the HTTP method code to a String.
        byte methodCode = requestHeaderMessage.getByte();
        if (methodCode != Constants.SC_M_JK_STORED) {
            String methodName = Constants.getMethodForCode(methodCode - 1);
            request.method().setString(methodName);
        }

        requestHeaderMessage.getBytes(request.protocol());
        requestHeaderMessage.getBytes(request.requestURI());

        requestHeaderMessage.getBytes(request.remoteAddr());
        requestHeaderMessage.getBytes(request.remoteHost());
        requestHeaderMessage.getBytes(request.localName());
        request.setLocalPort(requestHeaderMessage.getInt());

        if (socketWrapper != null) {
            request.peerAddr().setString(socketWrapper.getRemoteAddr());
        }

        boolean isSSL = requestHeaderMessage.getByte() != 0;
        if (isSSL) {
            request.scheme().setString("https");
        }

        // Decode headers
        MimeHeaders headers = request.getMimeHeaders();

        // Set this every time in case limit has been changed via JMX
        headers.setLimit(endpoint.getMaxHeaderCount());

        boolean contentLengthSet = false;
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
                hName = Constants.getHeaderForCode(hId - 1);
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
                long cl = vMB.getLong();
                if (contentLengthSet) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                } else {
                    contentLengthSet = true;
                    // Set the content-length header for the request
                    request.setContentLength(cl);
                }
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
        boolean secretPresentInRequest = false;
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
                 * AJP13 misses to forward the local IP address and the
                 * remote port. Allow the AJP connector to add this info via
                 * private request attributes.
                 * We will accept the forwarded data and remove it from the
                 * public list of request attributes.
                 */
                if(n.equals(Constants.SC_A_REQ_LOCAL_ADDR)) {
                    request.localAddr().setString(v);
                } else if(n.equals(Constants.SC_A_REQ_REMOTE_PORT)) {
                    try {
                        request.setRemotePort(Integer.parseInt(v));
                    } catch (NumberFormatException nfe) {
                        // Ignore invalid value
                    }
                } else if(n.equals(Constants.SC_A_SSL_PROTOCOL)) {
                    request.setAttribute(SSLSupport.PROTOCOL_VERSION_KEY, v);
                } else if (n.equals("JK_LB_ACTIVATION")) {
                    request.setAttribute(n, v);
                } else if (javaxAttributes.contains(n)) {
                    request.setAttribute(n, v);
                } else if (iisTlsAttributes.contains(n)) {
                    // Allow IIS TLS attributes
                    request.setAttribute(n, v);
                } else {
                    // All 'known' attributes will be processed by the previous
                    // blocks. Any remaining attribute is an 'arbitrary' one.
                    if (allowedRequestAttributesPattern != null &&
                            allowedRequestAttributesPattern.matcher(n).matches()) {
                        request.setAttribute(n, v);
                    } else {
                        log.warn(sm.getString("ajpprocessor.unknownAttribute", n));
                        response.setStatus(403);
                        setErrorState(ErrorState.CLOSE_CLEAN, null);
                    }
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
                if (tomcatAuthorization || !tomcatAuthentication) {
                    // Implies tomcatAuthentication == false
                    requestHeaderMessage.getBytes(request.getRemoteUser());
                    request.setRemoteUserNeedsAuthorization(tomcatAuthorization);
                } else {
                    // Ignore user information from reverse proxy
                    requestHeaderMessage.getBytes(tmpMB);
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
                requestHeaderMessage.getBytes(tmpMB);
                // nothing
                break;

            case Constants.SC_A_SSL_CERT :
                // SSL certificate extraction is lazy, moved to JkCoyoteHandler
                requestHeaderMessage.getBytes(certificates);
                break;

            case Constants.SC_A_SSL_CIPHER :
                requestHeaderMessage.getBytes(tmpMB);
                request.setAttribute(SSLSupport.CIPHER_SUITE_KEY,
                        tmpMB.toString());
                break;

            case Constants.SC_A_SSL_SESSION :
                requestHeaderMessage.getBytes(tmpMB);
                request.setAttribute(SSLSupport.SESSION_ID_KEY,
                        tmpMB.toString());
                break;

            case Constants.SC_A_SSL_KEY_SIZE :
                request.setAttribute(SSLSupport.KEY_SIZE_KEY,
                        Integer.valueOf(requestHeaderMessage.getInt()));
                break;

            case Constants.SC_A_STORED_METHOD:
                requestHeaderMessage.getBytes(request.method());
                break;

            case Constants.SC_A_SECRET:
                requestHeaderMessage.getBytes(tmpMB);
                if (secret != null && secret.length() > 0) {
                    secretPresentInRequest = true;
                    if (!tmpMB.equals(secret)) {
                        response.setStatus(403);
                        setErrorState(ErrorState.CLOSE_CLEAN, null);
                    }
                }
                break;

            default:
                // Ignore unknown attribute for backward compatibility
                break;

            }

        }

        // Check if secret was submitted if required
        if (secret != null && secret.length() > 0 && !secretPresentInRequest) {
            response.setStatus(403);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
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

        if (!getErrorState().isIoAllowed()) {
            getAdapter().log(request, response, 0);
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation populates the server name from the local name
     * provided by the AJP message.
     */
    @Override
    protected void populateHost() {
        try {
            request.serverName().duplicate(request.localName());
        } catch (IOException e) {
            response.setStatus(400);
            setErrorState(ErrorState.CLOSE_CLEAN, e);
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation populates the server port from the local port
     * provided by the AJP message.
     */
    @Override
    protected void populatePort() {
        // No host information (HTTP/1.0)
        request.setServerPort(request.getLocalPort());
    }


    /**
     * When committing the response, we have to validate the set of headers, as
     * well as setup the response filters.
     */
    @SuppressWarnings("deprecation")
    @Override
    protected final void prepareResponse() throws IOException {

        response.setCommitted(true);

        tmpMB.recycle();
        responseMsgPos = -1;
        responseMessage.reset();
        responseMessage.appendByte(Constants.JK_AJP13_SEND_HEADERS);

        // Responses with certain status codes are not permitted to include a
        // response body.
        int statusCode = response.getStatus();
        if (statusCode < 200 || statusCode == 204 || statusCode == 205 ||
                statusCode == 304) {
            // No entity body
            swallowResponse = true;
        }

        // Responses to HEAD requests are not permitted to include a response
        // body.
        MessageBytes methodMB = request.method();
        if (methodMB.equals("HEAD")) {
            // No entity body
            swallowResponse = true;
        }

        // HTTP header contents
        responseMessage.appendInt(statusCode);
        if (sendReasonPhrase) {
            String message = null;
            if (org.apache.coyote.Constants.USE_CUSTOM_STATUS_MSG_IN_HEADER &&
                    HttpMessages.isSafeInHttpHeader(response.getMessage())) {
                message = response.getMessage();
            }
            if (message == null) {
                message = HttpMessages.getInstance(
                        response.getLocale()).getMessage(response.getStatus());
            }
            if (message == null) {
                // mod_jk + httpd 2.x fails with a null status message - bug 45026
                message = Integer.toString(response.getStatus());
            }
            tmpMB.setString(message);
        } else {
            // Reason phrase is optional but mod_jk + httpd 2.x fails with a null
            // reason phrase - bug 45026
            tmpMB.setString(Integer.toString(response.getStatus()));
        }
        responseMessage.appendBytes(tmpMB);

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
        responseMessage.appendInt(numHeaders);
        for (int i = 0; i < numHeaders; i++) {
            MessageBytes hN = headers.getName(i);
            int hC = Constants.getResponseAjpIndex(hN.toString());
            if (hC > 0) {
                responseMessage.appendInt(hC);
            } else {
                responseMessage.appendBytes(hN);
            }
            MessageBytes hV=headers.getValue(i);
            responseMessage.appendBytes(hV);
        }

        // Write to buffer
        responseMessage.end();
        socketWrapper.write(true, responseMessage.getBuffer(), 0, responseMessage.getLen());
        socketWrapper.flush(true);
    }


    /**
     * Callback to write data from the buffer.
     */
    @Override
    protected final void flush() throws IOException {
        // Calling code should ensure that there is no data in the buffers for
        // non-blocking writes.
        // TODO Validate the assertion above
        if (!responseFinished) {
            if (ajpFlush) {
                // Send the flush message
                socketWrapper.write(true, flushMessageArray, 0, flushMessageArray.length);
            }
            socketWrapper.flush(true);
        }
    }


    /**
     * Finish AJP response.
     */
    @Override
    protected final void finishResponse() throws IOException {
        if (responseFinished)
            return;

        responseFinished = true;

        // Swallow the unread body packet if present
        if (waitingForBodyMessage || first && request.getContentLengthLong() > 0) {
            refillReadBuffer(true);
        }

        // Add the end message
        if (getErrorState().isError()) {
            socketWrapper.write(true, endAndCloseMessageArray, 0, endAndCloseMessageArray.length);
        } else {
            socketWrapper.write(true, endMessageArray, 0, endMessageArray.length);
        }
        socketWrapper.flush(true);
    }


    @Override
    protected final void ack(ContinueResponseTiming continueResponseTiming) {
        // NO-OP for AJP
    }


    @Override
    protected final int available(boolean doRead) {
        if (endOfStream) {
            return 0;
        }
        if (empty && doRead) {
            try {
                refillReadBuffer(false);
            } catch (IOException timeout) {
                // Not ideal. This will indicate that data is available
                // which should trigger a read which in turn will trigger
                // another IOException and that one can be thrown.
                return 1;
            }
        }
        if (empty) {
            return 0;
        } else {
            return request.getInputBuffer().available();
        }
    }


    @Override
    protected final void setRequestBody(ByteChunk body) {
        int length = body.getLength();
        bodyBytes.setBytes(body.getBytes(), body.getStart(), length);
        request.setContentLength(length);
        first = false;
        empty = false;
        replay = true;
        endOfStream = false;
    }


    @Override
    protected final void setSwallowResponse() {
        swallowResponse = true;
    }


    @Override
    protected final void disableSwallowRequest() {
        /* NO-OP
         * With AJP, Tomcat controls when the client sends request body data. At
         * most there will be a single packet to read and that will be handled
         * in finishResponse().
         */
    }


    @Override
    protected final boolean getPopulateRequestAttributesFromSocket() {
        // NO-OPs the attribute requests since they are pre-populated when
        // parsing the first AJP message.
        return false;
    }


    @Override
    protected final void populateRequestAttributeRemoteHost() {
        // Get remote host name using a DNS resolution
        if (request.remoteHost().isNull()) {
            try {
                request.remoteHost().setString(InetAddress.getByName
                        (request.remoteAddr().toString()).getHostName());
            } catch (IOException iex) {
                // Ignore
            }
        }
    }


    @Override
    protected final void populateSslRequestAttributes() {
        if (!certificates.isNull()) {
            ByteChunk certData = certificates.getByteChunk();
            X509Certificate jsseCerts[] = null;
            ByteArrayInputStream bais =
                new ByteArrayInputStream(certData.getBytes(),
                        certData.getStart(),
                        certData.getLength());
            // Fill the  elements.
            try {
                CertificateFactory cf;
                String clientCertProvider = getClientCertProvider();
                if (clientCertProvider == null) {
                    cf = CertificateFactory.getInstance("X.509");
                } else {
                    cf = CertificateFactory.getInstance("X.509",
                            clientCertProvider);
                }
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
            } catch (CertificateException | NoSuchProviderException e) {
                getLog().error(sm.getString("ajpprocessor.certs.fail"), e);
                return;
            }
            request.setAttribute(SSLSupport.CERTIFICATE_KEY, jsseCerts);
        }
    }


    @Override
    protected final boolean isRequestBodyFullyRead() {
        return endOfStream;
    }


    @Override
    protected final void registerReadInterest() {
        socketWrapper.registerReadInterest();
    }


    @Override
    protected final boolean isReadyForWrite() {
        return responseMsgPos == -1 && socketWrapper.isReadyForWrite();
    }


    /**
     * Read at least the specified amount of bytes, and place them
     * in the input buffer. Note that if any data is available to read then this
     * method will always block until at least the specified number of bytes
     * have been read.
     *
     * @param buf   Buffer to read data into
     * @param pos   Start position
     * @param n     The minimum number of bytes to read
     * @param block If there is no data available to read when this method is
     *              called, should this call block until data becomes available?
     * @return  <code>true</code> if the requested number of bytes were read
     *          else <code>false</code>
     * @throws IOException
     */
    private boolean read(byte[] buf, int pos, int n, boolean block) throws IOException {
        int read = socketWrapper.read(block, buf, pos, n);
        if (read > 0 && read < n) {
            int left = n - read;
            int start = pos + read;
            while (left > 0) {
                read = socketWrapper.read(true, buf, start, left);
                if (read == -1) {
                    throw new EOFException();
                }
                left = left - read;
                start = start + read;
            }
        } else if (read == -1) {
            throw new EOFException();
        }

        return read > 0;
    }


    /**
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #writeData(ByteBuffer)}
     */
    @Deprecated
    private void writeData(ByteChunk chunk) throws IOException {
        boolean blocking = (response.getWriteListener() == null);

        int len = chunk.getLength();
        int off = 0;

        // Write this chunk
        while (len > 0) {
            int thisTime = Math.min(len, outputMaxChunkSize);

            responseMessage.reset();
            responseMessage.appendByte(Constants.JK_AJP13_SEND_BODY_CHUNK);
            responseMessage.appendBytes(chunk.getBytes(), chunk.getOffset() + off, thisTime);
            responseMessage.end();
            socketWrapper.write(blocking, responseMessage.getBuffer(), 0, responseMessage.getLen());
            socketWrapper.flush(blocking);

            len -= thisTime;
            off += thisTime;
        }

        bytesWritten += off;
    }


    private void writeData(ByteBuffer chunk) throws IOException {
        boolean blocking = (response.getWriteListener() == null);

        int len = chunk.remaining();
        int off = 0;

        // Write this chunk
        while (len > 0) {
            int thisTime = Math.min(len, outputMaxChunkSize);

            responseMessage.reset();
            responseMessage.appendByte(Constants.JK_AJP13_SEND_BODY_CHUNK);
            chunk.limit(chunk.position() + thisTime);
            responseMessage.appendBytes(chunk);
            responseMessage.end();
            socketWrapper.write(blocking, responseMessage.getBuffer(), 0, responseMessage.getLen());
            socketWrapper.flush(blocking);

            len -= thisTime;
            off += thisTime;
        }

        bytesWritten += off;
    }


    private boolean hasDataToWrite() {
        return responseMsgPos != -1 || socketWrapper.hasDataToWrite();
    }


    @Override
    protected Log getLog() {
        return log;
    }


    // ------------------------------------- InputStreamInputBuffer Inner Class

    /**
     * This class is an input buffer which will read its data from an input
     * stream.
     */
    protected class SocketInputBuffer implements InputBuffer {

        /**
         * @deprecated Unused. Will be removed in Tomcat 9. Use
         *             {@link #doRead(ApplicationBufferHandler)}
         */
        @Deprecated
        @Override
        public int doRead(ByteChunk chunk) throws IOException {

            if (endOfStream) {
                return -1;
            }
            if (empty) {
                if (!refillReadBuffer(true)) {
                    return -1;
                }
            }
            ByteChunk bc = bodyBytes.getByteChunk();
            chunk.setBytes(bc.getBuffer(), bc.getStart(), bc.getLength());
            empty = true;
            return chunk.getLength();
        }

        @Override
        public int doRead(ApplicationBufferHandler handler) throws IOException {

            if (endOfStream) {
                return -1;
            }
            if (empty) {
                if (!refillReadBuffer(true)) {
                    return -1;
                }
            }
            ByteChunk bc = bodyBytes.getByteChunk();
            handler.setByteBuffer(ByteBuffer.wrap(bc.getBuffer(), bc.getStart(), bc.getLength()));
            empty = true;
            return handler.getByteBuffer().remaining();
        }

        @Override
        public int available() {
            if (empty) {
                return 0;
            } else {
                return bodyBytes.getByteChunk().getLength();
            }
        }
    }


    // ----------------------------------- OutputStreamOutputBuffer Inner Class

    /**
     * This class is an output buffer which will write data to an output
     * stream.
     */
    protected class SocketOutputBuffer implements OutputBuffer {

        /**
         * @deprecated Unused. Will be removed in Tomcat 9. Use
         *             {@link #doWrite(ByteBuffer)}
         */
        @Deprecated
        @Override
        public int doWrite(ByteChunk chunk) throws IOException {

            if (!response.isCommitted()) {
                // Validate and write response headers
                try {
                    prepareResponse();
                } catch (IOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                }
            }

            if (!swallowResponse) {
                writeData(chunk);
            }
            return chunk.getLength();
        }

        @Override
        public int doWrite(ByteBuffer chunk) throws IOException {

            if (!response.isCommitted()) {
                // Validate and write response headers
                try {
                    prepareResponse();
                } catch (IOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                }
            }

            int len = 0;
            if (!swallowResponse) {
                try {
                    len = chunk.remaining();
                    writeData(chunk);
                    len -= chunk.remaining();
                } catch (IOException ioe) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, ioe);
                    // Re-throw
                    throw ioe;
                }
            }
            return len;
        }

        @Override
        public long getBytesWritten() {
            return bytesWritten;
        }
    }
}

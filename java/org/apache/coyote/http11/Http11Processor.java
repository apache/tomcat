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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.ServletConnection;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.ContinueResponseTiming;
import org.apache.coyote.ErrorState;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.UpgradeToken;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.coyote.http11.filters.ChunkedInputFilter;
import org.apache.coyote.http11.filters.ChunkedOutputFilter;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.coyote.http11.filters.IdentityInputFilter;
import org.apache.coyote.http11.filters.IdentityOutputFilter;
import org.apache.coyote.http11.filters.SavedRequestInputFilter;
import org.apache.coyote.http11.filters.VoidInputFilter;
import org.apache.coyote.http11.filters.VoidOutputFilter;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.coyote.http11.upgrade.UpgradeApplicationBufferHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.HttpParser;
import org.apache.tomcat.util.http.parser.TokenList;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SendfileDataBase;
import org.apache.tomcat.util.net.SendfileKeepAliveState;
import org.apache.tomcat.util.net.SendfileState;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;

public class Http11Processor extends AbstractProcessor {

    private static final Log log = LogFactory.getLog(Http11Processor.class);

    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Http11Processor.class);


    private final AbstractHttp11Protocol<?> protocol;


    /**
     * Input.
     */
    private final Http11InputBuffer inputBuffer;


    /**
     * Output.
     */
    private final Http11OutputBuffer outputBuffer;


    /**
     * Tracks how many internal filters are in the filter library so they are skipped when looking for pluggable
     * filters.
     */
    private int pluggableFilterIndex = Integer.MAX_VALUE;


    /**
     * Keep-alive.
     */
    private volatile boolean keepAlive = true;


    /**
     * Flag used to indicate that the socket should be kept open (e.g. for keep alive or send file).
     */
    private volatile boolean openSocket = false;


    /**
     * Flag that indicates if the request headers have been completely read.
     */
    private volatile boolean readComplete = true;

    /**
     * HTTP/1.1 flag.
     */
    private boolean http11 = true;


    /**
     * HTTP/0.9 flag.
     */
    private boolean http09 = false;


    /**
     * Content delimiter for the request (if false, the connection will be closed at the end of the request).
     */
    private boolean contentDelimitation = true;


    /**
     * Instance of the new protocol to use after the HTTP connection has been upgraded.
     */
    private UpgradeToken upgradeToken = null;


    /**
     * Sendfile data.
     */
    private SendfileDataBase sendfileData = null;


    /**
     * Http parser.
     */
    private final HttpParser httpParser;


    public Http11Processor(AbstractHttp11Protocol<?> protocol, Adapter adapter) {
        super(adapter);
        this.protocol = protocol;

        HttpParser httpParser = protocol.getHttpParser();
        if (httpParser == null) {
            log.info(sm.getString("http11processor.noParser"));
            httpParser = new HttpParser(protocol.getRelaxedPathChars(), protocol.getRelaxedQueryChars());
        }
        this.httpParser = httpParser;

        inputBuffer = new Http11InputBuffer(request, protocol.getMaxHttpRequestHeaderSize(), httpParser);
        request.setInputBuffer(inputBuffer);

        outputBuffer = new Http11OutputBuffer(response, protocol.getMaxHttpResponseHeaderSize());
        response.setOutputBuffer(outputBuffer);

        // Create and add the identity filters.
        inputBuffer.addFilter(new IdentityInputFilter(protocol.getMaxSwallowSize()));
        outputBuffer.addFilter(new IdentityOutputFilter());

        // Create and add the chunked filters.
        inputBuffer.addFilter(new ChunkedInputFilter(request, protocol.getMaxTrailerSize(),
                protocol.getAllowedTrailerHeadersInternal(), protocol.getMaxExtensionSize(),
                protocol.getMaxSwallowSize()));
        outputBuffer.addFilter(new ChunkedOutputFilter());

        // Create and add the void filters.
        inputBuffer.addFilter(new VoidInputFilter());
        outputBuffer.addFilter(new VoidOutputFilter());

        // Create and add buffered input filter
        inputBuffer.addFilter(new BufferedInputFilter(protocol.getMaxSwallowSize()));

        // Create and add the gzip filters.
        // inputBuffer.addFilter(new GzipInputFilter());
        outputBuffer.addFilter(new GzipOutputFilter());

        pluggableFilterIndex = inputBuffer.getFilters().length;
    }


    /**
     * Determine if we must drop the connection because of the HTTP status code. Use the same list of codes as
     * Apache/httpd.
     */
    private static boolean statusDropsConnection(int status) {
        return status == 400 /* SC_BAD_REQUEST */ || status == 408 /* SC_REQUEST_TIMEOUT */ ||
                status == 411 /* SC_LENGTH_REQUIRED */ || status == 413 /* SC_REQUEST_ENTITY_TOO_LARGE */ ||
                status == 414 /* SC_REQUEST_URI_TOO_LONG */ || status == 500 /* SC_INTERNAL_SERVER_ERROR */ ||
                status == 503 /* SC_SERVICE_UNAVAILABLE */ || status == 501 /* SC_NOT_IMPLEMENTED */;
    }


    /**
     * Add an input filter to the current request. If the encoding is not supported, a 501 response will be returned to
     * the client.
     */
    private void addInputFilter(InputFilter[] inputFilters, String encodingName) {
        if (contentDelimitation) {
            // Chunked has already been specified and it must be the final
            // encoding.
            // 400 - Bad request
            response.setStatus(400);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http11processor.request.alreadyChunked", encodingName));
            }
            return;
        }

        // Parsing trims and converts to lower case.
        if (encodingName.equals("chunked")) {
            inputBuffer.addActiveFilter(inputFilters[Constants.CHUNKED_FILTER]);
            contentDelimitation = true;
        } else {
            for (int i = pluggableFilterIndex; i < inputFilters.length; i++) {
                if (inputFilters[i].getEncodingName().toString().equals(encodingName)) {
                    inputBuffer.addActiveFilter(inputFilters[i]);
                    return;
                }
            }
            // Unsupported transfer encoding
            // 501 - Unimplemented
            response.setStatus(501);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http11processor.request.unsupportedEncoding", encodingName));
            }
        }
    }


    @Override
    public SocketState service(SocketWrapperBase<?> socketWrapper) throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the I/O
        setSocketWrapper(socketWrapper);

        // Flags
        keepAlive = true;
        openSocket = false;
        readComplete = true;
        boolean keptAlive = false;
        SendfileState sendfileState = SendfileState.DONE;

        while (!getErrorState().isError() && keepAlive && !isAsync() && upgradeToken == null &&
                sendfileState == SendfileState.DONE && !protocol.isPaused()) {

            // Parsing the request header
            try {
                if (!inputBuffer.parseRequestLine(keptAlive, protocol.getConnectionTimeout(),
                        protocol.getKeepAliveTimeout())) {
                    if (inputBuffer.getParsingRequestLinePhase() == -1) {
                        return SocketState.UPGRADING;
                    } else if (handleIncompleteRequestLineRead()) {
                        break;
                    }
                }

                // Process the Protocol component of the request line
                // Need to know if this is an HTTP 0.9 request before trying to
                // parse headers.
                prepareRequestProtocol();

                if (protocol.isPaused()) {
                    // 503 - Service unavailable
                    response.setStatus(503);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                } else {
                    keptAlive = true;
                    // Set this every time in case limit has been changed via JMX
                    request.getMimeHeaders().setLimit(protocol.getMaxHeaderCount());
                    // Don't parse headers for HTTP/0.9
                    if (!http09 && !inputBuffer.parseHeaders()) {
                        // We've read part of the request, don't recycle it
                        // instead associate it with the socket
                        openSocket = true;
                        readComplete = false;
                        break;
                    }
                    if (!protocol.getDisableUploadTimeout()) {
                        socketWrapper.setReadTimeout(protocol.getConnectionUploadTimeout());
                    }
                }
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("http11processor.header.parse"), e);
                }
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                break;
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                UserDataHelper.Mode logMode = userDataHelper.getNextMode();
                if (logMode != null) {
                    String message = sm.getString("http11processor.header.parse");
                    switch (logMode) {
                        case INFO_THEN_DEBUG:
                            message += sm.getString("http11processor.fallToDebug");
                            //$FALL-THROUGH$
                        case INFO:
                            log.info(message, t);
                            break;
                        case DEBUG:
                            log.debug(message, t);
                    }
                }
                // 400 - Bad Request
                response.setStatus(400);
                setErrorState(ErrorState.CLOSE_CLEAN, t);
            }

            // Has an upgrade been requested?
            if (isConnectionToken(request.getMimeHeaders(), "upgrade")) {
                // Check the protocol
                String requestedProtocol = request.getHeader("Upgrade");

                UpgradeProtocol upgradeProtocol = protocol.getUpgradeProtocol(requestedProtocol);
                if (upgradeProtocol != null) {
                    if (upgradeProtocol.accept(request)) {
                        // Create clone of request for upgraded protocol
                        Request upgradeRequest = null;
                        try {
                            upgradeRequest = cloneRequest(request);
                        } catch (ByteChunk.BufferOverflowException ioe) {
                            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                            setErrorState(ErrorState.CLOSE_CLEAN, null);
                        } catch (IOException ioe) {
                            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            setErrorState(ErrorState.CLOSE_CLEAN, ioe);
                        }

                        if (upgradeRequest != null) {
                            // Complete the HTTP/1.1 upgrade process
                            response.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
                            response.setHeader("Connection", "Upgrade");
                            response.setHeader("Upgrade", requestedProtocol);
                            action(ActionCode.CLOSE, null);
                            getAdapter().log(request, response, 0);

                            // Continue processing using new protocol
                            InternalHttpUpgradeHandler upgradeHandler = upgradeProtocol
                                    .getInternalUpgradeHandler(socketWrapper, getAdapter(), upgradeRequest);
                            UpgradeToken upgradeToken = new UpgradeToken(upgradeHandler, null, null, requestedProtocol);
                            action(ActionCode.UPGRADE, upgradeToken);
                            return SocketState.UPGRADING;
                        }
                    }
                }
            }

            if (getErrorState().isIoAllowed()) {
                // Setting up filters, and parse some request headers
                rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
                try {
                    prepareRequest();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("http11processor.request.prepare"), t);
                    }
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, t);
                }
            }

            int maxKeepAliveRequests = protocol.getMaxKeepAliveRequests();
            if (maxKeepAliveRequests == 1) {
                keepAlive = false;
            } else if (maxKeepAliveRequests > 0 && socketWrapper.decrementKeepAlive() <= 0) {
                keepAlive = false;
            }

            // Process the request in the adapter
            if (getErrorState().isIoAllowed()) {
                try {
                    rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                    getAdapter().service(request, response);
                    // Handle when the response was committed before a serious
                    // error occurred. Throwing a ServletException should both
                    // set the status to 500 and set the errorException.
                    // If we fail here, then the response is likely already
                    // committed, so we can't try and set headers.
                    if (keepAlive && !getErrorState().isError() && !isAsync() &&
                            statusDropsConnection(response.getStatus())) {
                        setErrorState(ErrorState.CLOSE_CLEAN, null);
                    }
                } catch (InterruptedIOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                } catch (HeadersTooLargeException e) {
                    log.error(sm.getString("http11processor.request.process"), e);
                    // The response should not have been committed but check it
                    // anyway to be safe
                    if (response.isCommitted()) {
                        setErrorState(ErrorState.CLOSE_NOW, e);
                    } else {
                        response.reset();
                        response.setStatus(500);
                        setErrorState(ErrorState.CLOSE_CLEAN, e);
                        response.setHeader("Connection", "close"); // TODO: Remove
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("http11processor.request.process"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, t);
                    getAdapter().log(request, response, 0);
                }
            }

            // Finish the handling of the request
            rp.setStage(org.apache.coyote.Constants.STAGE_ENDINPUT);
            if (!isAsync()) {
                // If this is an async request then the request ends when it has
                // been completed. The AsyncContext is responsible for calling
                // endRequest() in that case.
                endRequest();
            }
            rp.setStage(org.apache.coyote.Constants.STAGE_ENDOUTPUT);

            // If there was an error, make sure the request is counted as
            // and error, and update the statistics counter
            if (getErrorState().isError()) {
                response.setStatus(500);
            }

            if (!isAsync() || getErrorState().isError()) {
                request.updateCounters();
                if (getErrorState().isIoAllowed()) {
                    inputBuffer.nextRequest();
                    outputBuffer.nextRequest();
                }
            }

            if (!protocol.getDisableUploadTimeout()) {
                int connectionTimeout = protocol.getConnectionTimeout();
                if (connectionTimeout > 0) {
                    socketWrapper.setReadTimeout(connectionTimeout);
                } else {
                    socketWrapper.setReadTimeout(0);
                }
            }

            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);

            sendfileState = processSendfile(socketWrapper);
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (getErrorState().isError() || (protocol.isPaused() && !isAsync())) {
            return SocketState.CLOSED;
        } else if (isAsync()) {
            return SocketState.LONG;
        } else if (isUpgrade()) {
            return SocketState.UPGRADING;
        } else {
            if (sendfileState == SendfileState.PENDING) {
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
    protected final void setSocketWrapper(SocketWrapperBase<?> socketWrapper) {
        super.setSocketWrapper(socketWrapper);
        inputBuffer.init(socketWrapper);
        outputBuffer.init(socketWrapper);
    }


    private Request cloneRequest(Request source) throws IOException {
        Request dest = new Request();

        // Transfer the minimal information required for the copy of the Request
        // that is passed to the HTTP upgrade process
        dest.decodedURI().duplicate(source.decodedURI());
        dest.method().duplicate(source.method());
        dest.getMimeHeaders().duplicate(source.getMimeHeaders());
        dest.requestURI().duplicate(source.requestURI());
        dest.queryString().duplicate(source.queryString());

        // Preparation for reading the request body
        MimeHeaders headers = source.getMimeHeaders();
        prepareExpectation(headers);
        prepareInputFilters(headers);
        ack(ContinueResponseTiming.ALWAYS);

        // Need to read and buffer the request body, if any. RFC 7230 requires
        // that the request is fully read before the upgrade takes place.
        ByteChunk body = new ByteChunk();
        int maxSavePostSize = protocol.getMaxSavePostSize();
        if (maxSavePostSize != 0) {
            body.setLimit(maxSavePostSize);
            ApplicationBufferHandler buffer = new UpgradeApplicationBufferHandler();

            while (source.getInputBuffer().doRead(buffer) >= 0) {
                body.append(buffer.getByteBuffer());
            }
        }

        // Make the buffered request body available to the upgraded protocol.
        SavedRequestInputFilter srif = new SavedRequestInputFilter(body);
        dest.setInputBuffer(srif);

        return dest;
    }


    private boolean handleIncompleteRequestLineRead() {
        // Haven't finished reading the request so keep the socket
        // open
        openSocket = true;
        // Check to see if we have read any of the request line yet
        if (inputBuffer.getParsingRequestLinePhase() > 1) {
            // Started to read request line.
            if (protocol.isPaused()) {
                // Partially processed the request so need to respond
                response.setStatus(503);
                setErrorState(ErrorState.CLOSE_CLEAN, null);
                return false;
            } else {
                // Need to keep processor associated with socket
                readComplete = false;
            }
        }
        return true;
    }


    private void checkExpectationAndResponseStatus() {
        if (request.hasExpectation() && !isRequestBodyFullyRead() &&
                (response.getStatus() < 200 || response.getStatus() > 299)) {
            // Client sent Expect: 100-continue but received a
            // non-2xx final response. Disable keep-alive (if enabled)
            // to ensure that the connection is closed. Some clients may
            // still send the body, some may send the next request.
            // No way to differentiate, so close the connection to
            // force the client to send the next request.
            inputBuffer.setSwallowInput(false);
            keepAlive = false;
        }
    }


    private void checkMaxSwallowSize() {
        // Parse content-length header
        long contentLength = -1;
        try {
            contentLength = request.getContentLengthLong();
        } catch (Exception e) {
            // Ignore, an error here is already processed in prepareRequest
            // but is done again since the content length is still -1
        }
        if (contentLength > 0 && protocol.getMaxSwallowSize() > -1 &&
                (contentLength - request.getBytesRead() > protocol.getMaxSwallowSize())) {
            // There is more data to swallow than Tomcat will accept so the
            // connection is going to be closed. Disable keep-alive which will
            // trigger adding the "Connection: close" header if not already
            // present.
            keepAlive = false;
        }
    }


    private void prepareRequestProtocol() {

        MessageBytes protocolMB = request.protocol();
        if (protocolMB.equals(Constants.HTTP_11)) {
            http09 = false;
            http11 = true;
            protocolMB.setString(Constants.HTTP_11);
        } else if (protocolMB.equals(Constants.HTTP_10)) {
            http09 = false;
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
            http09 = false;
            http11 = false;
            // Send 505; Unsupported HTTP version
            response.setStatus(505);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http11processor.request.unsupportedVersion", protocolMB));
            }
        }
    }


    /**
     * After reading the request headers, we have to setup the request filters.
     */
    private void prepareRequest() throws IOException {

        if (protocol.isSSLEnabled()) {
            request.scheme().setString("https");
        }

        MimeHeaders headers = request.getMimeHeaders();

        // Check connection header
        MessageBytes connectionValueMB = headers.getValue(Constants.CONNECTION);
        if (connectionValueMB != null && !connectionValueMB.isNull()) {
            Set<String> tokens = new HashSet<>();
            TokenList.parseTokenList(headers.values(Constants.CONNECTION), tokens);
            if (tokens.contains(Constants.CLOSE)) {
                keepAlive = false;
            } else if (tokens.contains(Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN)) {
                keepAlive = true;
            }
        }

        if (http11) {
            prepareExpectation(headers);
        }

        // Check user-agent header
        Pattern restrictedUserAgents = protocol.getRestrictedUserAgentsPattern();
        if (restrictedUserAgents != null && (http11 || keepAlive)) {
            MessageBytes userAgentValueMB = headers.getValue("user-agent");
            // Check in the restricted list, and adjust the http11
            // and keepAlive flags accordingly
            if (userAgentValueMB != null && !userAgentValueMB.isNull()) {
                String userAgentValue = userAgentValueMB.toString();
                if (restrictedUserAgents.matcher(userAgentValue).matches()) {
                    http11 = false;
                    keepAlive = false;
                }
            }
        }


        // Check host header
        MessageBytes hostValueMB = null;
        try {
            hostValueMB = headers.getUniqueValue("host");
        } catch (IllegalArgumentException iae) {
            // Multiple Host headers are not permitted
            badRequest("http11processor.request.multipleHosts");
        }
        if (http11 && hostValueMB == null) {
            badRequest("http11processor.request.noHostHeader");
        }

        // Check for an absolute-URI less the query string which has already
        // been removed during the parsing of the request line
        ByteChunk uriBC = request.requestURI().getByteChunk();
        byte[] uriB = uriBC.getBytes();
        if (uriBC.startsWithIgnoreCase("http", 0)) {
            int pos = 4;
            // Check for https
            if (uriBC.startsWithIgnoreCase("s", pos)) {
                pos++;
            }
            // Next 3 characters must be "://"
            if (uriBC.startsWith("://", pos)) {
                pos += 3;
                int uriBCStart = uriBC.getStart();

                // '/' does not appear in the authority so use the first
                // instance to split the authority and the path segments
                int slashPos = uriBC.indexOf('/', pos);
                // '@' in the authority delimits the userinfo
                int atPos = uriBC.indexOf('@', pos);
                if (slashPos > -1 && atPos > slashPos) {
                    // First '@' is in the path segments so no userinfo
                    atPos = -1;
                }

                if (slashPos == -1) {
                    slashPos = uriBC.getLength();
                    // Set URI as "/". Use 6 as it will always be a '/'.
                    // 01234567
                    // http://
                    // https://
                    request.requestURI().setBytes(uriB, uriBCStart + 6, 1);
                } else {
                    request.requestURI().setBytes(uriB, uriBCStart + slashPos, uriBC.getLength() - slashPos);
                }

                // Skip any user info
                if (atPos != -1) {
                    // Validate the userinfo
                    for (; pos < atPos; pos++) {
                        byte c = uriB[uriBCStart + pos];
                        if (!HttpParser.isUserInfo(c)) {
                            // Strictly there needs to be a check for valid %nn
                            // encoding here but skip it since it will never be
                            // decoded because the userinfo is ignored
                            badRequest("http11processor.request.invalidUserInfo");
                            break;
                        }
                    }
                    // Skip the '@'
                    pos = atPos + 1;
                }

                if (http11) {
                    // Missing host header is illegal but handled above
                    if (hostValueMB != null) {
                        // Any host in the request line must be consistent with
                        // the Host header
                        if (!hostValueMB.getByteChunk().equalsIgnoreCase(uriB, uriBCStart + pos, slashPos - pos)) {
                            // The requirements of RFC 7230 are being
                            // applied. If the host header and the request
                            // line do not agree, trigger a 400 response.
                            badRequest("http11processor.request.inconsistentHosts");
                        }
                    }
                } else {
                    // Not HTTP/1.1 - no Host header so generate one since
                    // Tomcat internals assume it is set
                    try {
                        hostValueMB = headers.setValue("host");
                        hostValueMB.setBytes(uriB, uriBCStart + pos, slashPos - pos);
                    } catch (IllegalStateException e) {
                        // Edge case
                        // If the request has too many headers it won't be
                        // possible to create the host header. Ignore this as
                        // processing won't reach the point where the Tomcat
                        // internals expect there to be a host header.
                    }
                }
            } else {
                badRequest("http11processor.request.invalidScheme");
            }
        }

        // Validate the characters in the URI. %nn decoding will be checked at
        // the point of decoding.
        for (int i = uriBC.getStart(); i < uriBC.getEnd(); i++) {
            if (!httpParser.isAbsolutePathRelaxed(uriB[i])) {
                badRequest("http11processor.request.invalidUri");
                break;
            }
        }

        // Input filter setup
        prepareInputFilters(headers);

        // Validate host name and extract port if present
        parseHost(hostValueMB);

        if (!getErrorState().isIoAllowed()) {
            getAdapter().log(request, response, 0);
        }
    }


    private void prepareExpectation(MimeHeaders headers) {
        MessageBytes expectMB = headers.getValue("expect");
        if (expectMB != null && !expectMB.isNull()) {
            if (expectMB.toString().trim().equalsIgnoreCase("100-continue")) {
                request.setExpectation(true);
            } else {
                response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                setErrorState(ErrorState.CLOSE_CLEAN, null);
            }
        }
    }

    private void prepareInputFilters(MimeHeaders headers) throws IOException {

        contentDelimitation = false;

        InputFilter[] inputFilters = inputBuffer.getFilters();

        // Parse transfer-encoding header
        // HTTP specs say an HTTP 1.1 server should accept any recognised
        // HTTP 1.x header from a 1.x client unless the specs says otherwise.
        if (!http09) {
            MessageBytes transferEncodingValueMB = headers.getValue("transfer-encoding");
            if (transferEncodingValueMB != null) {
                List<String> encodingNames = new ArrayList<>();
                if (TokenList.parseTokenList(headers.values("transfer-encoding"), encodingNames)) {
                    for (String encodingName : encodingNames) {
                        addInputFilter(inputFilters, encodingName);
                    }
                } else {
                    // Invalid transfer encoding
                    badRequest("http11processor.request.invalidTransferEncoding");
                }
            }
        }

        // Parse content-length header
        long contentLength = -1;
        try {
            contentLength = request.getContentLengthLong();
        } catch (NumberFormatException e) {
            badRequest("http11processor.request.nonNumericContentLength");
        } catch (IllegalArgumentException e) {
            badRequest("http11processor.request.multipleContentLength");
        }
        if (contentLength >= 0) {
            if (contentDelimitation) {
                // contentDelimitation being true at this point indicates that
                // chunked encoding is being used but chunked encoding should
                // not be used with a content length. RFC 2616, section 4.4,
                // bullet 3 states Content-Length must be ignored in this case -
                // so remove it.
                headers.removeHeader("content-length");
                request.setContentLength(-1);
                keepAlive = false;
            } else {
                inputBuffer.addActiveFilter(inputFilters[Constants.IDENTITY_FILTER]);
                contentDelimitation = true;
            }
        }

        if (!contentDelimitation) {
            // If there's no content length
            // (broken HTTP/1.0 or HTTP/1.1), assume
            // the client is not broken and didn't send a body
            inputBuffer.addActiveFilter(inputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }
    }


    private void badRequest(String errorKey) {
        response.setStatus(400);
        setErrorState(ErrorState.CLOSE_CLEAN, null);
        if (log.isDebugEnabled()) {
            log.debug(sm.getString(errorKey));
        }
    }


    @Override
    protected final void prepareResponse() throws IOException {

        boolean entityBody = true;
        contentDelimitation = false;

        OutputFilter[] outputFilters = outputBuffer.getFilters();

        if (http09 == true) {
            // HTTP/0.9
            outputBuffer.addActiveFilter(outputFilters[Constants.IDENTITY_FILTER]);
            outputBuffer.commit();
            return;
        }

        int statusCode = response.getStatus();
        if (statusCode < 200 || statusCode == 204 || statusCode == 205 || statusCode == 304) {
            // No entity body
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            entityBody = false;
            contentDelimitation = true;
            if (statusCode == 205) {
                // RFC 7231 requires the server to explicitly signal an empty
                // response in this case
                response.setContentLength(0);
            } else {
                response.setContentLength(-1);
            }
        }

        boolean head = request.method().equals("HEAD");
        if (head) {
            // Any entity body, if present, should not be sent
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }

        // Sendfile support
        if (protocol.getUseSendfile()) {
            prepareSendfile(outputFilters);
        }

        // Check for compression
        boolean useCompression = false;
        if (entityBody && sendfileData == null) {
            useCompression = protocol.useCompression(request, response);
        }

        MimeHeaders headers = response.getMimeHeaders();
        // A SC_NO_CONTENT response may include entity headers
        if (entityBody || statusCode == HttpServletResponse.SC_NO_CONTENT) {
            String contentType = response.getContentType();
            if (contentType != null) {
                headers.setValue("Content-Type").setString(contentType);
            }
            String contentLanguage = response.getContentLanguage();
            if (contentLanguage != null) {
                headers.setValue("Content-Language").setString(contentLanguage);
            }
        }

        long contentLength = response.getContentLengthLong();
        boolean connectionClosePresent = isConnectionToken(headers, Constants.CLOSE);
        if (http11 && response.getTrailerFields() != null) {
            // If trailer fields are set, always use chunking
            outputBuffer.addActiveFilter(outputFilters[Constants.CHUNKED_FILTER]);
            contentDelimitation = true;
            headers.addValue(Constants.TRANSFERENCODING).setString(Constants.CHUNKED);
        } else if (contentLength != -1) {
            headers.setValue("Content-Length").setLong(contentLength);
            outputBuffer.addActiveFilter(outputFilters[Constants.IDENTITY_FILTER]);
            contentDelimitation = true;
        } else if (head) {
            /*
             * The OutputBuffer can't differentiate between a zero length response because GET would have produced a
             * zero length body and a zero length response because HEAD didn't write any content. Therefore skip setting
             * the "transfer-encoding: chunked" header as that may not be consistent with what would be seen with the
             * equivalent GET.
             */
        } else {
            // If the response code supports an entity body and we're on
            // HTTP 1.1 then we chunk unless we have a Connection: close header
            if (http11 && entityBody && !connectionClosePresent) {
                outputBuffer.addActiveFilter(outputFilters[Constants.CHUNKED_FILTER]);
                contentDelimitation = true;
                headers.addValue(Constants.TRANSFERENCODING).setString(Constants.CHUNKED);
            } else {
                outputBuffer.addActiveFilter(outputFilters[Constants.IDENTITY_FILTER]);
            }
        }

        if (useCompression) {
            outputBuffer.addActiveFilter(outputFilters[Constants.GZIP_FILTER]);
        }

        // Add date header unless application has already set one (e.g. in a
        // Caching Filter)
        if (headers.getValue("Date") == null) {
            headers.addValue("Date").setString(FastHttpDateFormat.getCurrentDate());
        }

        // Although using transfer-encoding for gzip would be doable and was
        // the original intent (which means the compression would be from an
        // endpoint to the next, so only for the current transmission), it
        // has been found that using content-encoding (which is end to end
        // compression) is more efficient and more reliable.

        if ((entityBody) && (!contentDelimitation) || connectionClosePresent) {
            // Disable keep-alive if:
            // - there is a response body but way for the client to determine
            // the content length information; or
            // - there is a "connection: close" header present
            // This will cause the "connection: close" header to be added if it
            // is not already present.
            keepAlive = false;
        }

        // This may disable keep-alive so check before working out the Connection header
        checkExpectationAndResponseStatus();

        // This may disable keep-alive if there is more body to swallow
        // than the configuration allows
        checkMaxSwallowSize();

        // If we know that the request is bad this early, add the
        // Connection: close header.
        if (keepAlive && statusDropsConnection(statusCode)) {
            keepAlive = false;
        }
        if (!keepAlive) {
            // Avoid adding the close header twice
            if (!connectionClosePresent) {
                headers.addValue(Constants.CONNECTION).setString(Constants.CLOSE);
            }
        } else if (!getErrorState().isError()) {
            if (!http11) {
                headers.addValue(Constants.CONNECTION).setString(Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN);
            }

            if (protocol.getUseKeepAliveResponseHeader()) {
                boolean connectionKeepAlivePresent =
                        isConnectionToken(request.getMimeHeaders(), Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN);

                if (connectionKeepAlivePresent) {
                    int keepAliveTimeout = protocol.getKeepAliveTimeout();

                    if (keepAliveTimeout > 0) {
                        String value = "timeout=" + keepAliveTimeout / 1000L;
                        headers.setValue(Constants.KEEP_ALIVE_HEADER_NAME).setString(value);

                        if (http11) {
                            // Append if there is already a Connection header,
                            // else create the header
                            MessageBytes connectionHeaderValue = headers.getValue(Constants.CONNECTION);
                            if (connectionHeaderValue == null) {
                                headers.addValue(Constants.CONNECTION)
                                        .setString(Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN);
                            } else {
                                connectionHeaderValue.setString(connectionHeaderValue.getString() + ", " +
                                        Constants.KEEP_ALIVE_HEADER_VALUE_TOKEN);
                            }
                        }
                    }
                }
            }
        }

        // Add server header
        String server = protocol.getServer();
        if (server == null) {
            if (protocol.getServerRemoveAppProvidedValues()) {
                headers.removeHeader("server");
            }
        } else {
            // server always overrides anything the app might set
            headers.setValue("Server").setString(server);
        }

        writeHeaders(response.getStatus(), headers);

        outputBuffer.commit();
    }


    private void writeHeaders(int status, MimeHeaders headers) {
        try {
            outputBuffer.sendStatus(status);

            int size = headers.size();
            for (int i = 0; i < size; i++) {
                try {
                    outputBuffer.sendHeader(headers.getName(i), headers.getValue(i));
                } catch (IllegalArgumentException iae) {
                    // Log the problematic header
                    log.warn(sm.getString("http11processor.response.invalidHeader", headers.getName(i),
                            headers.getValue(i)), iae);
                    // Remove the problematic header
                    headers.removeHeader(i);
                    size--;
                    // Header buffer is corrupted. Reset it and start again.
                    outputBuffer.resetHeaderBuffer();
                    // -1 as it will be incremented at the start of the loop and header indexes start at 0.
                    i = -1;
                    outputBuffer.sendStatus(status);
                }
            }
            outputBuffer.endHeaders();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // If something goes wrong, reset the header buffer so the error
            // response can be written instead.
            outputBuffer.resetHeaderBuffer();
            throw t;
        }
    }


    private static boolean isConnectionToken(MimeHeaders headers, String token) throws IOException {
        MessageBytes connection = headers.getValue(Constants.CONNECTION);
        if (connection == null) {
            return false;
        }

        Set<String> tokens = new HashSet<>();
        TokenList.parseTokenList(headers.values(Constants.CONNECTION), tokens);
        return tokens.contains(token);
    }


    private void prepareSendfile(OutputFilter[] outputFilters) {
        String fileName = (String) request.getAttribute(org.apache.coyote.Constants.SENDFILE_FILENAME_ATTR);
        if (fileName == null) {
            sendfileData = null;
        } else {
            // No entity body sent here
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
            long pos = ((Long) request.getAttribute(org.apache.coyote.Constants.SENDFILE_FILE_START_ATTR)).longValue();
            long end = ((Long) request.getAttribute(org.apache.coyote.Constants.SENDFILE_FILE_END_ATTR)).longValue();
            sendfileData = socketWrapper.createSendfileData(fileName, pos, end - pos);
        }
    }


    /*
     * Note: populateHost() is not over-ridden. request.serverName() will be set to return the default host name by the
     * Mapper.
     */


    /**
     * {@inheritDoc}
     * <p>
     * This implementation provides the server port from the local port.
     */
    @Override
    protected void populatePort() {
        // Ensure the local port field is populated before using it.
        request.action(ActionCode.REQ_LOCALPORT_ATTRIBUTE, request);
        request.setServerPort(request.getLocalPort());
    }


    @Override
    protected boolean flushBufferedWrite() throws IOException {
        if (outputBuffer.hasDataToWrite()) {
            if (outputBuffer.flushBuffer(false)) {
                // The buffer wasn't fully flushed so re-register the
                // socket for write. Note this does not go via the
                // Response since the write registration state at
                // that level should remain unchanged. Once the buffer
                // has been emptied then the code below will call
                // Adaptor.asyncDispatch() which will enable the
                // Response to respond to this event.
                outputBuffer.registerWriteInterest();
                return true;
            }
        }
        return false;
    }


    @Override
    protected SocketState dispatchEndRequest() {
        if (!keepAlive || protocol.isPaused()) {
            return SocketState.CLOSED;
        } else {
            endRequest();
            inputBuffer.nextRequest();
            outputBuffer.nextRequest();
            if (socketWrapper.isReadPending()) {
                return SocketState.LONG;
            } else {
                return SocketState.OPEN;
            }
        }
    }


    @Override
    protected Log getLog() {
        return log;
    }


    @Override
    protected ServletConnection getServletConnection() {
        return socketWrapper.getServletConnection("http/1.1", "");
    }


    /*
     * No more input will be passed to the application. Remaining input will be swallowed or the connection dropped
     * depending on the error and expectation status.
     */
    private void endRequest() {
        if (getErrorState().isError()) {
            // If we know we are closing the connection, don't drain
            // input. This way uploading a 100GB file doesn't tie up the
            // thread if the servlet has rejected it.
            inputBuffer.setSwallowInput(false);
        } else {
            // Need to check this again here in case the response was
            // committed before the error that requires the connection
            // to be closed occurred.
            checkExpectationAndResponseStatus();
        }

        // Finish the handling of the request
        if (getErrorState().isIoAllowed()) {
            try {
                inputBuffer.endRequest();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // 500 - Internal Server Error
                // Can't add a 500 to the access log since that has already been
                // written in the Adapter.service method.
                response.setStatus(500);
                setErrorState(ErrorState.CLOSE_NOW, t);
                log.error(sm.getString("http11processor.request.finish"), t);
            }
        }
        if (getErrorState().isIoAllowed()) {
            try {
                action(ActionCode.COMMIT, null);
                outputBuffer.end();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                setErrorState(ErrorState.CLOSE_NOW, t);
                log.error(sm.getString("http11processor.response.finish"), t);
            }
        }
    }


    @Override
    protected final void finishResponse() throws IOException {
        outputBuffer.end();
    }


    @Override
    protected final void ack(ContinueResponseTiming continueResponseTiming) {
        // Only try and send the ACK for ALWAYS or if the timing of the request
        // to send the ACK matches the current configuration.
        if (continueResponseTiming == ContinueResponseTiming.ALWAYS ||
                continueResponseTiming == protocol.getContinueResponseTimingInternal()) {
            // Acknowledge request
            // Send a 100 status back if it makes sense (response not committed
            // yet, and client specified an expectation for 100-continue)
            if (!response.isCommitted() && request.hasExpectation()) {
                try {
                    outputBuffer.sendAck();
                } catch (IOException e) {
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
                }
            }
        }
    }


    @Override
    protected void earlyHints() throws IOException {
        writeHeaders(HttpServletResponse.SC_EARLY_HINTS, response.getMimeHeaders());
        outputBuffer.writeHeaders();
        outputBuffer.resetHeaderBuffer();
    }


    @Override
    protected final void flush() throws IOException {
        outputBuffer.flush();
    }


    @Override
    protected final int available(boolean doRead) {
        return inputBuffer.available(doRead);
    }


    @Override
    protected final void setRequestBody(ByteChunk body) {
        InputFilter savedBody = new SavedRequestInputFilter(body);
        Http11InputBuffer internalBuffer = (Http11InputBuffer) request.getInputBuffer();
        internalBuffer.addActiveFilter(savedBody);
    }


    @Override
    protected final void setSwallowResponse() {
        outputBuffer.responseFinished = true;
    }


    @Override
    protected final void disableSwallowRequest() {
        inputBuffer.setSwallowInput(false);
    }


    @Override
    protected final void sslReHandShake() throws IOException {
        if (sslSupport != null) {
            // Consume and buffer the request body, so that it does not
            // interfere with the client's handshake messages
            InputFilter[] inputFilters = inputBuffer.getFilters();
            ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER]).setLimit(protocol.getMaxSavePostSize());
            inputBuffer.addActiveFilter(inputFilters[Constants.BUFFERED_FILTER]);

            /*
             * Outside the try/catch because we want I/O errors during renegotiation to be thrown for the caller to
             * handle since they will be fatal to the connection.
             */
            socketWrapper.doClientAuth(sslSupport);
            try {
                /*
                 * Errors processing the cert chain do not affect the client connection so they can be logged and
                 * swallowed here.
                 */
                Object sslO = sslSupport.getPeerCertificateChain();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.CERTIFICATE_KEY, sslO);
                }
            } catch (IOException ioe) {
                log.warn(sm.getString("http11processor.socket.ssl"), ioe);
            }
        }
    }


    @Override
    protected final boolean isRequestBodyFullyRead() {
        return inputBuffer.isFinished();
    }


    @Override
    protected final void registerReadInterest() {
        socketWrapper.registerReadInterest();
    }


    @Override
    protected final boolean isReadyForWrite() {
        return outputBuffer.isReady();
    }


    @Override
    public UpgradeToken getUpgradeToken() {
        return upgradeToken;
    }


    @Override
    protected final void doHttpUpgrade(UpgradeToken upgradeToken) {
        this.upgradeToken = upgradeToken;
        // Stop further HTTP output
        outputBuffer.responseFinished = true;
    }


    @Override
    public ByteBuffer getLeftoverInput() {
        return inputBuffer.getLeftover();
    }


    @Override
    public boolean isUpgrade() {
        return upgradeToken != null;
    }


    @Override
    protected boolean isTrailerFieldsReady() {
        if (inputBuffer.isChunking()) {
            return inputBuffer.isFinished();
        } else {
            return true;
        }
    }


    @Override
    protected boolean isTrailerFieldsSupported() {
        // Request must be HTTP/1.1 to support trailer fields
        if (!http11) {
            return false;
        }

        // If the response is not yet committed, chunked encoding can be used
        // and the trailer fields sent
        if (!response.isCommitted()) {
            return true;
        }

        // Response has been committed - need to see if chunked is being used
        return outputBuffer.isChunking();
    }


    /**
     * Trigger sendfile processing if required.
     *
     * @return The state of send file processing
     */
    private SendfileState processSendfile(SocketWrapperBase<?> socketWrapper) {
        openSocket = keepAlive;
        // Done is equivalent to sendfile not being used
        SendfileState result = SendfileState.DONE;
        // Do sendfile as needed: add socket to sendfile and end
        if (sendfileData != null && !getErrorState().isError()) {
            if (keepAlive) {
                if (available(false) == 0) {
                    sendfileData.keepAliveState = SendfileKeepAliveState.OPEN;
                } else {
                    sendfileData.keepAliveState = SendfileKeepAliveState.PIPELINED;
                }
            } else {
                sendfileData.keepAliveState = SendfileKeepAliveState.NONE;
            }
            result = socketWrapper.processSendfile(sendfileData);
            switch (result) {
                case ERROR:
                    // Write failed
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("http11processor.sendfile.error"));
                    }
                    setErrorState(ErrorState.CLOSE_CONNECTION_NOW, null);
                    //$FALL-THROUGH$
                default:
                    sendfileData = null;
            }
        }
        return result;
    }


    @Override
    public final void recycle() {
        getAdapter().checkRecycled(request, response);
        super.recycle();
        inputBuffer.recycle();
        outputBuffer.recycle();
        upgradeToken = null;
        socketWrapper = null;
        sendfileData = null;
        sslSupport = null;
    }


    @Override
    public void pause() {
        // NOOP for HTTP
    }
}

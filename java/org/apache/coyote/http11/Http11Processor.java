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
import java.util.Enumeration;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
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
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.HttpParser;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
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


    private final HttpParser httpParser;


    /**
     * Tracks how many internal filters are in the filter library so they
     * are skipped when looking for pluggable filters.
     */
    private int pluggableFilterIndex = Integer.MAX_VALUE;


    /**
     * Keep-alive.
     */
    private volatile boolean keepAlive = true;


    /**
     * Flag used to indicate that the socket should be kept open (e.g. for keep
     * alive or send file.
     */
    private boolean openSocket = false;


    /**
     * Flag that indicates if the request headers have been completely read.
     */
    private boolean readComplete = true;

    /**
     * HTTP/1.1 flag.
     */
    private boolean http11 = true;


    /**
     * HTTP/0.9 flag.
     */
    private boolean http09 = false;


    /**
     * Content delimiter for the request (if false, the connection will
     * be closed at the end of the request).
     */
    private boolean contentDelimitation = true;


    /**
     * Instance of the new protocol to use after the HTTP connection has been
     * upgraded.
     */
    private UpgradeToken upgradeToken = null;


    /**
     * Sendfile data.
     */
    private SendfileDataBase sendfileData = null;


    public Http11Processor(AbstractHttp11Protocol<?> protocol, Adapter adapter) {
        super(adapter);
        this.protocol = protocol;

        httpParser = new HttpParser(protocol.getRelaxedPathChars(),
                protocol.getRelaxedQueryChars());

        inputBuffer = new Http11InputBuffer(request, protocol.getMaxHttpHeaderSize(),
                protocol.getRejectIllegalHeaderName(), httpParser);
        request.setInputBuffer(inputBuffer);

        outputBuffer = new Http11OutputBuffer(response, protocol.getMaxHttpHeaderSize());
        response.setOutputBuffer(outputBuffer);

        // Create and add the identity filters.
        inputBuffer.addFilter(new IdentityInputFilter(protocol.getMaxSwallowSize()));
        outputBuffer.addFilter(new IdentityOutputFilter());

        // Create and add the chunked filters.
        inputBuffer.addFilter(new ChunkedInputFilter(protocol.getMaxTrailerSize(),
                protocol.getAllowedTrailerHeadersInternal(), protocol.getMaxExtensionSize(),
                protocol.getMaxSwallowSize()));
        outputBuffer.addFilter(new ChunkedOutputFilter());

        // Create and add the void filters.
        inputBuffer.addFilter(new VoidInputFilter());
        outputBuffer.addFilter(new VoidOutputFilter());

        // Create and add buffered input filter
        inputBuffer.addFilter(new BufferedInputFilter());

        // Create and add the chunked filters.
        //inputBuffer.addFilter(new GzipInputFilter());
        outputBuffer.addFilter(new GzipOutputFilter());

        pluggableFilterIndex = inputBuffer.getFilters().length;
    }


    /**
     * Specialized utility method: find a sequence of lower case bytes inside
     * a ByteChunk.
     */
    private static int findBytes(ByteChunk bc, byte[] b) {

        byte first = b[0];
        byte[] buff = bc.getBuffer();
        int start = bc.getStart();
        int end = bc.getEnd();

        // Look for first char
        int srcEnd = b.length;

        for (int i = start; i <= (end - srcEnd); i++) {
            if (Ascii.toLower(buff[i]) != first) {
                continue;
            }
            // found first char, now look for a match
            int myPos = i+1;
            for (int srcPos = 1; srcPos < srcEnd;) {
                if (Ascii.toLower(buff[myPos++]) != b[srcPos++]) {
                    break;
                }
                if (srcPos == srcEnd) {
                    return i - start; // found it
                }
            }
        }
        return -1;
    }


    /**
     * Determine if we must drop the connection because of the HTTP status
     * code.  Use the same list of codes as Apache/httpd.
     */
    private static boolean statusDropsConnection(int status) {
        return status == 400 /* SC_BAD_REQUEST */ ||
               status == 408 /* SC_REQUEST_TIMEOUT */ ||
               status == 411 /* SC_LENGTH_REQUIRED */ ||
               status == 413 /* SC_REQUEST_ENTITY_TOO_LARGE */ ||
               status == 414 /* SC_REQUEST_URI_TOO_LONG */ ||
               status == 500 /* SC_INTERNAL_SERVER_ERROR */ ||
               status == 503 /* SC_SERVICE_UNAVAILABLE */ ||
               status == 501 /* SC_NOT_IMPLEMENTED */;
    }


    /**
     * Add an input filter to the current request. If the encoding is not
     * supported, a 501 response will be returned to the client.
     */
    private void addInputFilter(InputFilter[] inputFilters, String encodingName) {

        // Trim provided encoding name and convert to lower case since transfer
        // encoding names are case insensitive. (RFC2616, section 3.6)
        encodingName = encodingName.trim().toLowerCase(Locale.ENGLISH);

        if (encodingName.equals("identity")) {
            // Skip
        } else if (encodingName.equals("chunked")) {
            inputBuffer.addActiveFilter
                (inputFilters[Constants.CHUNKED_FILTER]);
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
                log.debug(sm.getString("http11processor.request.prepare") +
                          " Unsupported transfer encoding [" + encodingName + "]");
            }
        }
    }


    @Override
    public SocketState service(SocketWrapperBase<?> socketWrapper)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the I/O
        setSocketWrapper(socketWrapper);
        inputBuffer.init(socketWrapper);
        outputBuffer.init(socketWrapper);

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

                if (protocol.isPaused()) {
                    // 503 - Service unavailable
                    response.setStatus(503);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                } else {
                    keptAlive = true;
                    // Set this every time in case limit has been changed via JMX
                    request.getMimeHeaders().setLimit(protocol.getMaxHeaderCount());
                    if (!inputBuffer.parseHeaders()) {
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
            Enumeration<String> connectionValues = request.getMimeHeaders().values("Connection");
            boolean foundUpgrade = false;
            while (connectionValues.hasMoreElements() && !foundUpgrade) {
                foundUpgrade = connectionValues.nextElement().toLowerCase(
                        Locale.ENGLISH).contains("upgrade");
            }

            if (foundUpgrade) {
                // Check the protocol
                String requestedProtocol = request.getHeader("Upgrade");

                UpgradeProtocol upgradeProtocol = protocol.getUpgradeProtocol(requestedProtocol);
                if (upgradeProtocol != null) {
                    if (upgradeProtocol.accept(request)) {
                        response.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
                        response.setHeader("Connection", "Upgrade");
                        response.setHeader("Upgrade", requestedProtocol);
                        action(ActionCode.CLOSE,  null);
                        getAdapter().log(request, response, 0);

                        InternalHttpUpgradeHandler upgradeHandler =
                                upgradeProtocol.getInternalUpgradeHandler(
                                        socketWrapper, getAdapter(), cloneRequest(request));
                        UpgradeToken upgradeToken = new UpgradeToken(upgradeHandler, null, null);
                        action(ActionCode.UPGRADE, upgradeToken);
                        return SocketState.UPGRADING;
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
            } else if (maxKeepAliveRequests > 0 &&
                    socketWrapper.decrementKeepAlive() <= 0) {
                keepAlive = false;
            }

            // Process the request in the adapter
            if (getErrorState().isIoAllowed()) {
                try {
                    rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                    getAdapter().service(request, response);
                    // Handle when the response was committed before a serious
                    // error occurred.  Throwing a ServletException should both
                    // set the status to 500 and set the errorException.
                    // If we fail here, then the response is likely already
                    // committed, so we can't try and set headers.
                    if(keepAlive && !getErrorState().isError() && !isAsync() &&
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
                if(connectionTimeout > 0) {
                    socketWrapper.setReadTimeout(connectionTimeout);
                } else {
                    socketWrapper.setReadTimeout(0);
                }
            }

            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);

            sendfileState = processSendfile(socketWrapper);
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (getErrorState().isError() || protocol.isPaused()) {
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


    private Request cloneRequest(Request source) throws IOException {
        Request dest = new Request();

        // Transfer the minimal information required for the copy of the Request
        // that is passed to the HTTP upgrade process

        dest.decodedURI().duplicate(source.decodedURI());
        dest.method().duplicate(source.method());
        dest.getMimeHeaders().duplicate(source.getMimeHeaders());
        dest.requestURI().duplicate(source.requestURI());

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
        if (request.hasExpectation() &&
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


    /**
     * After reading the request headers, we have to setup the request filters.
     */
    private void prepareRequest() {

        http11 = true;
        http09 = false;
        contentDelimitation = false;

        if (protocol.isSSLEnabled()) {
            request.scheme().setString("https");
        }
        MessageBytes protocolMB = request.protocol();
        if (protocolMB.equals(Constants.HTTP_11)) {
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
            // Send 505; Unsupported HTTP version
            response.setStatus(505);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http11processor.request.prepare")+
                          " Unsupported HTTP version \""+protocolMB+"\"");
            }
        }

        MimeHeaders headers = request.getMimeHeaders();

        // Check connection header
        MessageBytes connectionValueMB = headers.getValue(Constants.CONNECTION);
        if (connectionValueMB != null) {
            ByteChunk connectionValueBC = connectionValueMB.getByteChunk();
            if (findBytes(connectionValueBC, Constants.CLOSE_BYTES) != -1) {
                keepAlive = false;
            } else if (findBytes(connectionValueBC,
                                 Constants.KEEPALIVE_BYTES) != -1) {
                keepAlive = true;
            }
        }

        if (http11) {
            MessageBytes expectMB = headers.getValue("expect");
            if (expectMB != null) {
                if (expectMB.indexOfIgnoreCase("100-continue", 0) != -1) {
                    inputBuffer.setSwallowInput(false);
                    request.setExpectation(true);
                } else {
                    response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                }
            }
        }

        // Check user-agent header
        Pattern restrictedUserAgents = protocol.getRestrictedUserAgentsPattern();
        if (restrictedUserAgents != null && (http11 || keepAlive)) {
            MessageBytes userAgentValueMB = headers.getValue("user-agent");
            // Check in the restricted list, and adjust the http11
            // and keepAlive flags accordingly
            if(userAgentValueMB != null) {
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
            // 400 - Bad request
            response.setStatus(400);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http11processor.request.multipleHosts"));
            }
        }
        if (http11 && hostValueMB == null) {
            // 400 - Bad request
            response.setStatus(400);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("http11processor.request.noHostHeader"));
            }
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
                            response.setStatus(400);
                            setErrorState(ErrorState.CLOSE_CLEAN, null);
                            if (log.isDebugEnabled()) {
                                log.debug(sm.getString("http11processor.request.invalidUserInfo"));
                            }
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
                        if (!hostValueMB.getByteChunk().equals(
                                uriB, uriBCStart + pos, slashPos - pos)) {
                            if (protocol.getAllowHostHeaderMismatch()) {
                                // The requirements of RFC 2616 are being
                                // applied. If the host header and the request
                                // line do not agree, the request line takes
                                // precedence
                                hostValueMB = headers.setValue("host");
                                hostValueMB.setBytes(uriB, uriBCStart + pos, slashPos - pos);
                            } else {
                                // The requirements of RFC 7230 are being
                                // applied. If the host header and the request
                                // line do not agree, trigger a 400 response.
                                response.setStatus(400);
                                setErrorState(ErrorState.CLOSE_CLEAN, null);
                                if (log.isDebugEnabled()) {
                                    log.debug(sm.getString("http11processor.request.inconsistentHosts"));
                                }
                            }
                        }
                    }
                } else {
                    // Not HTTP/1.1 - no Host header so generate one since
                    // Tomcat internals assume it is set
                    hostValueMB = headers.setValue("host");
                    hostValueMB.setBytes(uriB, uriBCStart + pos, slashPos - pos);
                }
            } else {
                response.setStatus(400);
                setErrorState(ErrorState.CLOSE_CLEAN, null);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("http11processor.request.invalidScheme"));
                }
            }
        }

        // Validate the characters in the URI. %nn decoding will be checked at
        // the point of decoding.
        for (int i = uriBC.getStart(); i < uriBC.getEnd(); i++) {
            if (!httpParser.isAbsolutePathRelaxed(uriB[i])) {
                response.setStatus(400);
                setErrorState(ErrorState.CLOSE_CLEAN, null);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("http11processor.request.invalidUri"));
                }
                break;
            }
        }

        // Input filter setup
        InputFilter[] inputFilters = inputBuffer.getFilters();

        // Parse transfer-encoding header
        if (http11) {
            MessageBytes transferEncodingValueMB = headers.getValue("transfer-encoding");
            if (transferEncodingValueMB != null) {
                String transferEncodingValue = transferEncodingValueMB.toString();
                // Parse the comma separated list. "identity" codings are ignored
                int startPos = 0;
                int commaPos = transferEncodingValue.indexOf(',');
                String encodingName = null;
                while (commaPos != -1) {
                    encodingName = transferEncodingValue.substring(startPos, commaPos);
                    addInputFilter(inputFilters, encodingName);
                    startPos = commaPos + 1;
                    commaPos = transferEncodingValue.indexOf(',', startPos);
                }
                encodingName = transferEncodingValue.substring(startPos);
                addInputFilter(inputFilters, encodingName);
            }
        }

        // Parse content-length header
        long contentLength = request.getContentLengthLong();
        if (contentLength >= 0) {
            if (contentDelimitation) {
                // contentDelimitation being true at this point indicates that
                // chunked encoding is being used but chunked encoding should
                // not be used with a content length. RFC 2616, section 4.4,
                // bullet 3 states Content-Length must be ignored in this case -
                // so remove it.
                headers.removeHeader("content-length");
                request.setContentLength(-1);
            } else {
                inputBuffer.addActiveFilter(inputFilters[Constants.IDENTITY_FILTER]);
                contentDelimitation = true;
            }
        }

        // Validate host name and extract port if present
        parseHost(hostValueMB);

        if (!contentDelimitation) {
            // If there's no content length
            // (broken HTTP/1.0 or HTTP/1.1), assume
            // the client is not broken and didn't send a body
            inputBuffer.addActiveFilter(inputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }

        if (!getErrorState().isIoAllowed()) {
            getAdapter().log(request, response, 0);
        }
    }


    /**
     * When committing the response, we have to validate the set of headers, as
     * well as setup the response filters.
     */
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
        if (statusCode < 200 || statusCode == 204 || statusCode == 205 ||
                statusCode == 304) {
            // No entity body
            outputBuffer.addActiveFilter
                (outputFilters[Constants.VOID_FILTER]);
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

        MessageBytes methodMB = request.method();
        if (methodMB.equals("HEAD")) {
            // No entity body
            outputBuffer.addActiveFilter
                (outputFilters[Constants.VOID_FILTER]);
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
                headers.setValue("Content-Language")
                    .setString(contentLanguage);
            }
        }

        long contentLength = response.getContentLengthLong();
        boolean connectionClosePresent = false;
        if (http11 && response.getTrailerFields() != null) {
            // If trailer fields are set, always use chunking
            outputBuffer.addActiveFilter(outputFilters[Constants.CHUNKED_FILTER]);
            contentDelimitation = true;
            headers.addValue(Constants.TRANSFERENCODING).setString(Constants.CHUNKED);
        } else if (contentLength != -1) {
            headers.setValue("Content-Length").setLong(contentLength);
            outputBuffer.addActiveFilter(outputFilters[Constants.IDENTITY_FILTER]);
            contentDelimitation = true;
        } else {
            // If the response code supports an entity body and we're on
            // HTTP 1.1 then we chunk unless we have a Connection: close header
            connectionClosePresent = isConnectionClose(headers);
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
            headers.addValue("Date").setString(
                    FastHttpDateFormat.getCurrentDate());
        }

        // FIXME: Add transfer encoding header

        if ((entityBody) && (!contentDelimitation)) {
            // Mark as close the connection after the request, and add the
            // connection: close header
            keepAlive = false;
        }

        // This may disabled keep-alive to check before working out the
        // Connection header.
        checkExpectationAndResponseStatus();

        // If we know that the request is bad this early, add the
        // Connection: close header.
        if (keepAlive && statusDropsConnection(statusCode)) {
            keepAlive = false;
        }
        if (!keepAlive) {
            // Avoid adding the close header twice
            if (!connectionClosePresent) {
                headers.addValue(Constants.CONNECTION).setString(
                        Constants.CLOSE);
            }
        } else if (!http11 && !getErrorState().isError()) {
            headers.addValue(Constants.CONNECTION).setString(Constants.KEEPALIVE);
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

        // Build the response header
        try {
            outputBuffer.sendStatus();

            int size = headers.size();
            for (int i = 0; i < size; i++) {
                outputBuffer.sendHeader(headers.getName(i), headers.getValue(i));
            }
            outputBuffer.endHeaders();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // If something goes wrong, reset the header buffer so the error
            // response can be written instead.
            outputBuffer.resetHeaderBuffer();
            throw t;
        }

        outputBuffer.commit();
    }

    private static boolean isConnectionClose(MimeHeaders headers) {
        MessageBytes connection = headers.getValue(Constants.CONNECTION);
        if (connection == null) {
            return false;
        }
        return connection.equals(Constants.CLOSE);
    }

    private void prepareSendfile(OutputFilter[] outputFilters) {
        String fileName = (String) request.getAttribute(
                org.apache.coyote.Constants.SENDFILE_FILENAME_ATTR);
        if (fileName == null) {
            sendfileData = null;
        } else {
            // No entity body sent here
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
            long pos = ((Long) request.getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_START_ATTR)).longValue();
            long end = ((Long) request.getAttribute(
                    org.apache.coyote.Constants.SENDFILE_FILE_END_ATTR)).longValue();
            sendfileData = socketWrapper.createSendfileData(fileName, pos, end - pos);
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation provides the server name from the default host and
     * the server port from the local port.
     */
    @Override
    protected void populateHost() {
        // No host information (HTTP/1.0)
        // Ensure the local port field is populated before using it.
        request.action(ActionCode.REQ_LOCALPORT_ATTRIBUTE, request);
        request.setServerPort(request.getLocalPort());

        // request.serverName() will be set to the default host name by the
        // mapper
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
        if (!keepAlive) {
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


    /*
     * No more input will be passed to the application. Remaining input will be
     * swallowed or the connection dropped depending on the error and
     * expectation status.
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
    protected final void ack() {
        // Acknowledge request
        // Send a 100 status back if it makes sense (response not committed
        // yet, and client specified an expectation for 100-continue)
        if (!response.isCommitted() && request.hasExpectation()) {
            inputBuffer.setSwallowInput(true);
            try {
                outputBuffer.sendAck();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_CONNECTION_NOW, e);
            }
        }
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
            ((BufferedInputFilter) inputFilters[Constants.BUFFERED_FILTER]).setLimit(
                    protocol.getMaxSavePostSize());
            inputBuffer.addActiveFilter(inputFilters[Constants.BUFFERED_FILTER]);

            /*
             * Outside the try/catch because we want I/O errors during
             * renegotiation to be thrown for the caller to handle since they
             * will be fatal to the connection.
             */
            socketWrapper.doClientAuth(sslSupport);
            try {
                /*
                 * Errors processing the cert chain do not affect the client
                 * connection so they can be logged and swallowed here.
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
    protected final boolean isReady() {
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

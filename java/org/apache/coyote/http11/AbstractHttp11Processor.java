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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.apache.coyote.AbstractProcessor;
import org.apache.coyote.ActionCode;
import org.apache.coyote.AsyncContextCallback;
import org.apache.coyote.ErrorState;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.coyote.http11.filters.ChunkedInputFilter;
import org.apache.coyote.http11.filters.ChunkedOutputFilter;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.coyote.http11.filters.IdentityInputFilter;
import org.apache.coyote.http11.filters.IdentityOutputFilter;
import org.apache.coyote.http11.filters.SavedRequestInputFilter;
import org.apache.coyote.http11.filters.VoidInputFilter;
import org.apache.coyote.http11.filters.VoidOutputFilter;
import org.apache.coyote.http11.upgrade.servlet31.HttpUpgradeHandler;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.ResponseUtil;
import org.apache.tomcat.util.http.parser.AcceptEncoding;
import org.apache.tomcat.util.http.parser.HttpParser;
import org.apache.tomcat.util.http.parser.TokenList;
import org.apache.tomcat.util.log.UserDataHelper;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractHttp11Processor<S> extends AbstractProcessor<S> {

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /*
     * Tracks how many internal filters are in the filter library so they
     * are skipped when looking for pluggable filters.
     */
    private int pluggableFilterIndex = Integer.MAX_VALUE;


    /**
     * Keep-alive.
     */
    protected boolean keepAlive = true;


    /**
     * Flag used to indicate that the socket should be kept open (e.g. for keep
     * alive or send file.
     */
    protected boolean openSocket = false;


    /**
     * Flag used to indicate that the socket should treat the next request
     * processed like a keep-alive connection - i.e. one where there may not be
     * any data to process. The initial value of this flag on entering the
     * process method is different for connectors that use polling (NIO / APR -
     * data is always expected) compared to those that use blocking (BIO - data
     * is only expected if the connection isn't in the keep-alive state).
     */
    protected boolean keptAlive;


    /**
     * Flag that indicates that send file processing is in progress and that the
     * socket should not be returned to the poller (where a poller is used).
     */
    protected boolean sendfileInProgress = false;


    /**
     * Flag that indicates if the request headers have been completely read.
     */
    protected boolean readComplete = true;

    /**
     * HTTP/1.1 flag.
     */
    protected boolean http11 = true;


    /**
     * HTTP/0.9 flag.
     */
    protected boolean http09 = false;


    /**
     * Content delimiter for the request (if false, the connection will
     * be closed at the end of the request).
     */
    protected boolean contentDelimitation = true;


    /**
     * Is there an expectation ?
     */
    protected boolean expectation = false;


    /**
     * Comet used.
     */
    protected boolean comet = false;


    /**
     * Regular expression that defines the restricted user agents.
     */
    protected Pattern restrictedUserAgents = null;


    /**
     * Maximum number of Keep-Alive requests to honor.
     */
    protected int maxKeepAliveRequests = -1;

    /**
     * The number of seconds Tomcat will wait for a subsequent request
     * before closing the connection.
     */
    protected int keepAliveTimeout = -1;

    /**
     * Remote Address associated with the current connection.
     */
    protected String remoteAddr = null;


    /**
     * Remote Host associated with the current connection.
     */
    protected String remoteHost = null;


    /**
     * Local Host associated with the current connection.
     */
    protected String localName = null;


    /**
     * Local port to which the socket is connected
     */
    protected int localPort = -1;


    /**
     * Remote port to which the socket is connected
     */
    protected int remotePort = -1;


    /**
     * The local Host address.
     */
    protected String localAddr = null;


    /**
     * Maximum timeout on uploads. 5 minutes as in Apache HTTPD server.
     */
    protected int connectionUploadTimeout = 300000;


    /**
     * Flag to disable setting a different time-out on uploads.
     */
    protected boolean disableUploadTimeout = false;


    /**
     * Allowed compression level.
     */
    protected int compressionLevel = 0;


    /**
     * Flag to disable compression when the resource has a strong ETag.
     */
    protected boolean noCompressionStrongETag = true;


    /**
     * Minimum content size to make compression.
     */
    protected int compressionMinSize = 2048;


    /**
     * Socket buffering.
     */
    protected int socketBuffer = -1;


    /**
     * Max saved post size.
     */
    protected int maxSavePostSize = 4 * 1024;


    /**
     * Regular expression that defines the user agents to not use gzip with
     */
    protected Pattern noCompressionUserAgents = null;

    /**
     * List of MIMES which could be gzipped
     * Note: This is not spelled correctly but can't be changed without breaking
     *       compatibility
     */
    protected String[] compressableMimeTypes = { "text/html", "text/xml", "text/plain" };


    /**
     * Allow a customized the server header for the tin-foil hat folks.
     */
    protected String server = null;


    /**
     * Listener to which data available events are passed once the associated
     * connection has completed the proprietary Tomcat HTTP upgrade process.
     *
     * @deprecated  Will be removed in Tomcat 8.0.x.
     */
    @Deprecated
    protected org.apache.coyote.http11.upgrade.UpgradeInbound upgradeInbound = null;


    /**
     * Instance of the new protocol to use after the HTTP connection has been
     * upgraded using the Servlet 3.1 based upgrade process.
     */
    protected HttpUpgradeHandler httpUpgradeHandler = null;


    private boolean allowHostHeaderMismatch = true;


    protected HttpParser httpParser;


    /**
     * Will Tomcat accept an HTTP 1.1 request where the host header does not
     * agree with the host specified (if any) in the request line?
     *
     * @return {@code true} if Tomcat will allow such requests, otherwise
     *         {@code false}
     */
    public boolean getAllowHostHeaderMismatch() {
        return allowHostHeaderMismatch;
    }


    /**
     * Will Tomcat accept an HTTP 1.1 request where the host header does not
     * agree with the host specified (if any) in the request line?
     *
     * @param allowHostHeaderMismatch {@code true} to allow such requests,
     *                                {@code false} to reject them with a 400
     */
    public void setAllowHostHeaderMismatch(boolean allowHostHeaderMismatch) {
        this.allowHostHeaderMismatch = allowHostHeaderMismatch;
    }


    public AbstractHttp11Processor(AbstractEndpoint<S> endpoint) {
        super(endpoint);
    }


    /**
     * Set compression level.
     */
    public void setCompression(String compression) {
        if (compression.equals("on")) {
            this.compressionLevel = 1;
        } else if (compression.equals("force")) {
            this.compressionLevel = 2;
        } else if (compression.equals("off")) {
            this.compressionLevel = 0;
        } else {
            try {
                // Try to parse compression as an int, which would give the
                // minimum compression size
                compressionMinSize = Integer.parseInt(compression);
                this.compressionLevel = 1;
            } catch (Exception e) {
                this.compressionLevel = 0;
            }
        }
    }

    /**
     * Set Minimum size to trigger compression.
     */
    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }


    public void setNoCompressionStrongETag(boolean noCompressionStrongETag) {
        this.noCompressionStrongETag = noCompressionStrongETag;
    }


    /**
     * Set no compression user agent pattern. Regular expression as supported
     * by {@link Pattern}.
     *
     * ie: "gorilla|desesplorer|tigrus"
     */
    public void setNoCompressionUserAgents(String noCompressionUserAgents) {
        if (noCompressionUserAgents == null || noCompressionUserAgents.length() == 0) {
            this.noCompressionUserAgents = null;
        } else {
            this.noCompressionUserAgents =
                Pattern.compile(noCompressionUserAgents);
        }
    }


    /**
     * @deprecated Use {@link #addCompressibleMimeType(String)}
     */
    @Deprecated
    public void addCompressableMimeType(String mimeType) {
        addCompressibleMimeType(mimeType);
    }


    /**
     * @deprecated Use {@link #setCompressibleMimeTypes(String[])}
     */
    @Deprecated
    public void setCompressableMimeTypes(String[] compressibleMimeTypes) {
        setCompressibleMimeTypes(compressibleMimeTypes);
    }


    /**
     * @deprecated Use {@link #setCompressibleMimeTypes(String)}
     */
    @Deprecated
    public void setCompressableMimeTypes(String compressibleMimeTypes) {
        setCompressibleMimeTypes(compressibleMimeTypes);
    }


    /**
     * Add a mime-type which will be compressible
     * The mime-type String will be exactly matched
     * in the response mime-type header .
     *
     * @param mimeType mime-type string
     */
    public void addCompressibleMimeType(String mimeType) {
        compressableMimeTypes = addStringArray(compressableMimeTypes, mimeType);
    }


    /**
     * Set compressible mime-type list (this method is best when used with
     * a large number of connectors, where it would be better to have all of
     * them referenced a single array).
     */
    public void setCompressibleMimeTypes(String[] compressibleMimeTypes) {
        this.compressableMimeTypes = compressibleMimeTypes;
    }


    /**
     * Set compressible mime-type list
     * List contains users agents separated by ',' :
     *
     * ie: "text/html,text/xml,text/plain"
     */
    public void setCompressibleMimeTypes(String compressibleMimeTypes) {
        if (compressibleMimeTypes != null) {
            this.compressableMimeTypes = null;
            StringTokenizer st = new StringTokenizer(compressibleMimeTypes, ",");

            while (st.hasMoreTokens()) {
                addCompressibleMimeType(st.nextToken().trim());
            }
        }
    }


    /**
     * Return compression level.
     */
    public String getCompression() {
        switch (compressionLevel) {
        case 0:
            return "off";
        case 1:
            return "on";
        case 2:
            return "force";
        }
        return "off";
    }


    /**
     * General use method
     *
     * @param sArray the StringArray
     * @param value string
     */
    private String[] addStringArray(String sArray[], String value) {
        String[] result = null;
        if (sArray == null) {
            result = new String[1];
            result[0] = value;
        }
        else {
            result = new String[sArray.length + 1];
            for (int i = 0; i < sArray.length; i++) {
                result[i] = sArray[i];
            }
            result[sArray.length] = value;
        }
        return result;
    }


    /**
     * Checks if any entry in the string array starts with the specified value
     *
     * @param sArray the StringArray
     * @param value string
     */
    private boolean startsWithStringArray(String sArray[], String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < sArray.length; i++) {
            if (value.startsWith(sArray[i])) {
                return true;
            }
        }
        return false;
    }


    /**
     * Set restricted user agent list (which will downgrade the connector
     * to HTTP/1.0 mode). Regular expression as supported by {@link Pattern}.
     *
     * ie: "gorilla|desesplorer|tigrus"
     */
    public void setRestrictedUserAgents(String restrictedUserAgents) {
        if (restrictedUserAgents == null ||
                restrictedUserAgents.length() == 0) {
            this.restrictedUserAgents = null;
        } else {
            this.restrictedUserAgents = Pattern.compile(restrictedUserAgents);
        }
    }


    /**
     * Set the maximum number of Keep-Alive requests to honor.
     * This is to safeguard from DoS attacks.  Setting to a negative
     * value disables the check.
     */
    public void setMaxKeepAliveRequests(int mkar) {
        maxKeepAliveRequests = mkar;
    }


    /**
     * Return the number of Keep-Alive requests that we will honor.
     */
    public int getMaxKeepAliveRequests() {
        return maxKeepAliveRequests;
    }

    /**
     * Set the Keep-Alive timeout.
     */
    public void setKeepAliveTimeout(int timeout) {
        keepAliveTimeout = timeout;
    }


    /**
     * Return the number Keep-Alive timeout.
     */
    public int getKeepAliveTimeout() {
        return keepAliveTimeout;
    }


    /**
     * Set the maximum size of a POST which will be buffered in SSL mode.
     */
    public void setMaxSavePostSize(int msps) {
        maxSavePostSize = msps;
    }


    /**
     * Return the maximum size of a POST which will be buffered in SSL mode.
     */
    public int getMaxSavePostSize() {
        return maxSavePostSize;
    }


    /**
     * Set the flag to control upload time-outs.
     */
    public void setDisableUploadTimeout(boolean isDisabled) {
        disableUploadTimeout = isDisabled;
    }

    /**
     * Get the flag that controls upload time-outs.
     */
    public boolean getDisableUploadTimeout() {
        return disableUploadTimeout;
    }

    /**
     * Set the socket buffer flag.
     */
    public void setSocketBuffer(int socketBuffer) {
        this.socketBuffer = socketBuffer;
    }

    /**
     * Get the socket buffer flag.
     */
    public int getSocketBuffer() {
        return socketBuffer;
    }

    /**
     * Set the upload timeout.
     */
    public void setConnectionUploadTimeout(int timeout) {
        connectionUploadTimeout = timeout ;
    }

    /**
     * Get the upload timeout.
     */
    public int getConnectionUploadTimeout() {
        return connectionUploadTimeout;
    }


    /**
     * Set the server header name.
     */
    public void setServer( String server ) {
        if (server==null || server.equals("")) {
            this.server = null;
        } else {
            this.server = server;
        }
    }

    /**
     * Get the server header name.
     */
    public String getServer() {
        return server;
    }


    /**
     * Check if the resource could be compressed, if the client supports it.
     */
    private boolean isCompressible() {

        MimeHeaders responseHeaders = response.getMimeHeaders();

        // Check if content is not already compressed
        MessageBytes contentEncodingMB = responseHeaders.getValue("Content-Encoding");

        if (contentEncodingMB != null) {
            // Content-Encoding values are ordered but order is not important
            // for this check so use a Set rather than a List
            Set<String> tokens = new HashSet<String>();
            try {
                TokenList.parseTokenList(responseHeaders.values("Content-Encoding"), tokens);
            } catch (IOException e) {
                // Because we are using StringReader, any exception here is a
                // Tomcat bug.
                getLog().warn(sm.getString("http11Processor.contentEncodingParseFail"), e);
                return false;
            }
            if (tokens.contains("gzip") || tokens.contains("br")) {
                return false;
            }
        }

        // Check if the resource has a strong ETag
        if (noCompressionStrongETag) {
            String eTag = responseHeaders.getHeader("ETag");
            if (eTag != null && !eTag.trim().startsWith("W/")) {
                // Has an ETag that doesn't start with "W/..." so it must be a
                // strong ETag
                return false;
            }
        }

        // If force mode, always compress (test purposes only)
        if (compressionLevel == 2) {
            return true;
        }

        // Check if sufficient length to trigger the compression
        long contentLength = response.getContentLengthLong();
        if ((contentLength == -1)
            || (contentLength > compressionMinSize)) {
            // Check for compatible MIME-TYPE
            if (compressableMimeTypes != null) {
                return (startsWithStringArray(compressableMimeTypes,
                                              response.getContentType()));
            }
        }

        return false;
    }


    /**
     * Check if compression should be used for this resource. Already checked
     * that the resource could be compressed if the client supports it.
     */
    private boolean useCompression() {

        // Check if user-agent supports gzip encoding
        // Only interested in whether gzip encoding is supported. Other
        // encodings and weights can be ignored.
        Enumeration<String> headerValues = request.getMimeHeaders().values("accept-encoding");
        boolean foundGzip = false;
        while (!foundGzip && headerValues.hasMoreElements()) {
            List<AcceptEncoding> acceptEncodings = null;
            try {
                acceptEncodings = AcceptEncoding.parse(new StringReader(headerValues.nextElement()));
            } catch (IOException ioe) {
                // If there is a problem reading the header, disable compression
                return false;
            }

            for (AcceptEncoding acceptEncoding : acceptEncodings) {
                if ("gzip".equalsIgnoreCase(acceptEncoding.getEncoding())) {
                    foundGzip = true;
                    break;
                }
            }
        }

        if (!foundGzip) {
            return false;
        }

        // If force mode, always compress (test purposes only)
        if (compressionLevel == 2) {
            return true;
        }

        // Check for incompatible Browser
        if (noCompressionUserAgents != null) {
            MessageBytes userAgentValueMB =
                request.getMimeHeaders().getValue("user-agent");
            if(userAgentValueMB != null) {
                String userAgentValue = userAgentValueMB.toString();

                if (noCompressionUserAgents.matcher(userAgentValue).matches()) {
                    return false;
                }
            }
        }

        return true;
    }


    /**
     * Specialized utility method: find a sequence of lower case bytes inside
     * a ByteChunk.
     *
     * @deprecated Unused. Will be removed in Tomcat 8.5.x.
     */
    @Deprecated
    protected int findBytes(ByteChunk bc, byte[] b) {

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
    protected boolean statusDropsConnection(int status) {
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
     * Exposes input buffer to super class to allow better code re-use.
     * @return  The input buffer used by the processor.
     */
    protected abstract AbstractInputBuffer<S> getInputBuffer();


    /**
     * Exposes output buffer to super class to allow better code re-use.
     * @return  The output buffer used by the processor.
     */
    protected abstract AbstractOutputBuffer<S> getOutputBuffer();


    /**
     * Initialize standard input and output filters.
     */
    protected void initializeFilters(int maxTrailerSize, Set<String> allowedTrailerHeaders,
            int maxExtensionSize, int maxSwallowSize) {
        // Create and add the identity filters.
        getInputBuffer().addFilter(new IdentityInputFilter(maxSwallowSize));
        getOutputBuffer().addFilter(new IdentityOutputFilter());

        // Create and add the chunked filters.
        getInputBuffer().addFilter( new ChunkedInputFilter(maxTrailerSize,allowedTrailerHeaders,
                maxExtensionSize, maxSwallowSize));
        getOutputBuffer().addFilter(new ChunkedOutputFilter());

        // Create and add the void filters
        getInputBuffer().addFilter(new VoidInputFilter());
        getOutputBuffer().addFilter(new VoidOutputFilter());

        // Create and add buffered input filter
        getInputBuffer().addFilter(new BufferedInputFilter());

        // Create and add the gzip filters.
        //getInputBuffer().addFilter(new GzipInputFilter());
        getOutputBuffer().addFilter(new GzipOutputFilter());

        pluggableFilterIndex = getInputBuffer().getFilters().length;
    }


    /**
     * Add an input filter to the current request. If the encoding is not
     * supported, a 501 response will be returned to the client.
     */
    private void addInputFilter(InputFilter[] inputFilters, String encodingName) {

        // Parsing trims and converts to lower case.

        if (encodingName.equals("identity")) {
            // Skip
        } else if (encodingName.equals("chunked")) {
            getInputBuffer().addActiveFilter
                (inputFilters[Constants.CHUNKED_FILTER]);
            contentDelimitation = true;
        } else {
            for (int i = pluggableFilterIndex; i < inputFilters.length; i++) {
                if (inputFilters[i].getEncodingName().toString().equals(encodingName)) {
                    getInputBuffer().addActiveFilter(inputFilters[i]);
                    return;
                }
            }
            // Unsupported transfer encoding
            // 501 - Unimplemented
            response.setStatus(501);
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("http11processor.request.prepare") +
                          " Unsupported transfer encoding [" + encodingName + "]");
            }
        }
    }


    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    @Override
    @SuppressWarnings("deprecation") // Inbound/Outbound based upgrade mechanism
    public final void action(ActionCode actionCode, Object param) {

        switch (actionCode) {
        case CLOSE: {
            // End the processing of the current request
            try {
                getOutputBuffer().endRequest();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_NOW, e);
            }
            break;
        }
        case COMMIT: {
            // Commit current response
            if (response.isCommitted()) {
                return;
            }

            // Validate and write response headers
            try {
                prepareResponse();
                getOutputBuffer().commit();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_NOW, e);
            }
            break;
        }
        case ACK: {
            // Acknowledge request
            // Send a 100 status back if it makes sense (response not committed
            // yet, and client specified an expectation for 100-continue)
            if ((response.isCommitted()) || !expectation) {
                return;
            }

            getInputBuffer().setSwallowInput(true);
            try {
                getOutputBuffer().sendAck();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_NOW, e);
            }
            break;
        }
        case CLIENT_FLUSH: {
            try {
                getOutputBuffer().flush();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_NOW, e);
                response.setErrorException(e);
            }
            break;
        }
        case IS_ERROR: {
            ((AtomicBoolean) param).set(getErrorState().isError());
            break;
        }
        case DISABLE_SWALLOW_INPUT: {
            // Do not swallow request input and make sure we are closing the
            // connection
            setErrorState(ErrorState.CLOSE_CLEAN, null);
            getInputBuffer().setSwallowInput(false);
            break;
        }
        case RESET: {
            // Note: This must be called before the response is committed
            getOutputBuffer().reset();
            break;
        }
        case CUSTOM: {
            // Do nothing
            // TODO Remove this action
            break;
        }
        case REQ_SET_BODY_REPLAY: {
            ByteChunk body = (ByteChunk) param;

            InputFilter savedBody = new SavedRequestInputFilter(body);
            savedBody.setRequest(request);

            @SuppressWarnings("unchecked")
            AbstractInputBuffer<S> internalBuffer = (AbstractInputBuffer<S>)
                request.getInputBuffer();
            internalBuffer.addActiveFilter(savedBody);
            break;
        }
        case ASYNC_START: {
            asyncStateMachine.asyncStart((AsyncContextCallback) param);
            // Async time out is based on SocketWrapper access time
            getSocketWrapper().access();
            break;
        }
        case ASYNC_DISPATCHED: {
            asyncStateMachine.asyncDispatched();
            break;
        }
        case ASYNC_TIMEOUT: {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(asyncStateMachine.asyncTimeout());
            break;
        }
        case ASYNC_RUN: {
            asyncStateMachine.asyncRun((Runnable) param);
            break;
        }
        case ASYNC_ERROR: {
            asyncStateMachine.asyncError();
            break;
        }
        case ASYNC_IS_STARTED: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncStarted());
            break;
        }
        case ASYNC_IS_COMPLETING: {
            ((AtomicBoolean) param).set(asyncStateMachine.isCompleting());
            break;
        }
        case ASYNC_IS_DISPATCHING: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncDispatching());
            break;
        }
        case ASYNC_IS_ASYNC: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsync());
            break;
        }
        case ASYNC_IS_TIMINGOUT: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncTimingOut());
            break;
        }
        case ASYNC_IS_ERROR: {
            ((AtomicBoolean) param).set(asyncStateMachine.isAsyncError());
            break;
        }
        case UPGRADE_TOMCAT: {
            upgradeInbound = (org.apache.coyote.http11.upgrade.UpgradeInbound) param;
            // Stop further HTTP output
            getOutputBuffer().finished = true;
            break;
        }
        case ASYNC_POST_PROCESS: {
            asyncStateMachine.asyncPostProcess();
            break;
        }
        case UPGRADE: {
            httpUpgradeHandler = (HttpUpgradeHandler) param;
            // Stop further HTTP output
            getOutputBuffer().finished = true;
            break;
        }
        case CLOSE_NOW: {
            // Block further output
            getOutputBuffer().finished = true;
            if (param instanceof Throwable) {
                setErrorState(ErrorState.CLOSE_NOW, (Throwable) param);
            } else {
                setErrorState(ErrorState.CLOSE_NOW, null);
            }
            break;
        }
        case END_REQUEST: {
            endRequest();
            break;
        }
        default: {
            actionInternal(actionCode, param);
            break;
        }
        }
    }

    protected abstract void actionInternal(ActionCode actionCode, Object param);


    /**
     * Processors (currently only HTTP BIO) may elect to disable HTTP keep-alive
     * in some circumstances. This method allows the processor implementation to
     * determine if keep-alive should be disabled or not.
     */
    protected abstract boolean disableKeepAlive();


    /**
     * Configures the timeout to be used for reading the request line.
     */
    protected abstract void setRequestLineReadTimeout() throws IOException;


    /**
     * Defines how a connector handles an incomplete request line read.
     *
     * @return <code>true</code> if the processor should break out of the
     *         processing loop, otherwise <code>false</code>.
     */
    protected abstract boolean handleIncompleteRequestLineRead();


    /**
     * Set the socket timeout.
     */
    protected abstract void setSocketTimeout(int timeout) throws IOException;


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
    public SocketState process(SocketWrapper<S> socketWrapper)
        throws IOException {
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PARSE);

        // Setting up the I/O
        setSocketWrapper(socketWrapper);
        getInputBuffer().init(socketWrapper, endpoint);
        getOutputBuffer().init(socketWrapper, endpoint);

        // Flags
        keepAlive = true;
        comet = false;
        openSocket = false;
        sendfileInProgress = false;
        readComplete = true;
        if (endpoint.getUsePolling()) {
            keptAlive = false;
        } else {
            keptAlive = socketWrapper.isKeptAlive();
        }

        if (disableKeepAlive()) {
            socketWrapper.setKeepAliveLeft(0);
        }

        while (!getErrorState().isError() && keepAlive && !comet && !isAsync() &&
                upgradeInbound == null &&
                httpUpgradeHandler == null && !endpoint.isPaused()) {

            // Parsing the request header
            try {
                setRequestLineReadTimeout();

                if (!getInputBuffer().parseRequestLine(keptAlive)) {
                    if (handleIncompleteRequestLineRead()) {
                        break;
                    }
                }

                // Process the Protocol component of the request line
                // Need to know if this is an HTTP 0.9 request before trying to
                // parse headers.
                prepareRequestProtocol();

                if (endpoint.isPaused()) {
                    // 503 - Service unavailable
                    response.setStatus(503);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                } else {
                    keptAlive = true;
                    // Set this every time in case limit has been changed via JMX
                    request.getMimeHeaders().setLimit(endpoint.getMaxHeaderCount());
                    request.getCookies().setLimit(getMaxCookieCount());
                    // Currently only NIO will ever return false here
                    // Don't parse headers for HTTP/0.9
                    if (!http09 && !getInputBuffer().parseHeaders()) {
                        // We've read part of the request, don't recycle it
                        // instead associate it with the socket
                        openSocket = true;
                        readComplete = false;
                        break;
                    }
                    if (!disableUploadTimeout) {
                        setSocketTimeout(connectionUploadTimeout);
                    }
                }
            } catch (IOException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(
                            sm.getString("http11processor.header.parse"), e);
                }
                setErrorState(ErrorState.CLOSE_NOW, e);
                break;
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                UserDataHelper.Mode logMode = userDataHelper.getNextMode();
                if (logMode != null) {
                    String message = sm.getString(
                            "http11processor.header.parse");
                    switch (logMode) {
                        case INFO_THEN_DEBUG:
                            message += sm.getString(
                                    "http11processor.fallToDebug");
                            //$FALL-THROUGH$
                        case INFO:
                            getLog().info(message, t);
                            break;
                        case DEBUG:
                            getLog().debug(message, t);
                    }
                }
                // 400 - Bad Request
                response.setStatus(400);
                setErrorState(ErrorState.CLOSE_CLEAN, t);
                getAdapter().log(request, response, 0);
            }

            if (!getErrorState().isError()) {
                // Setting up filters, and parse some request headers
                rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
                try {
                    prepareRequest();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(sm.getString(
                                "http11processor.request.prepare"), t);
                    }
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, t);
                    getAdapter().log(request, response, 0);
                }
            }

            if (maxKeepAliveRequests == 1) {
                keepAlive = false;
            } else if (maxKeepAliveRequests > 0 &&
                    socketWrapper.decrementKeepAlive() <= 0) {
                keepAlive = false;
            }

            // Process the request in the adapter
            if (!getErrorState().isError()) {
                try {
                    rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                    adapter.service(request, response);
                    // Handle when the response was committed before a serious
                    // error occurred.  Throwing a ServletException should both
                    // set the status to 500 and set the errorException.
                    // If we fail here, then the response is likely already
                    // committed, so we can't try and set headers.
                    if(keepAlive && !getErrorState().isError() && (
                            response.getErrorException() != null ||
                                    (!isAsync() &&
                                    statusDropsConnection(response.getStatus())))) {
                        setErrorState(ErrorState.CLOSE_CLEAN, null);
                    }
                    setCometTimeouts(socketWrapper);
                } catch (InterruptedIOException e) {
                    setErrorState(ErrorState.CLOSE_NOW, e);
                } catch (HeadersTooLargeException e) {
                    getLog().error(sm.getString("http11processor.request.process"), e);
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
                    getLog().error(sm.getString("http11processor.request.process"), t);
                    // 500 - Internal Server Error
                    response.setStatus(500);
                    setErrorState(ErrorState.CLOSE_CLEAN, t);
                    getAdapter().log(request, response, 0);
                }
            }

            // Finish the handling of the request
            rp.setStage(org.apache.coyote.Constants.STAGE_ENDINPUT);

            if (!isAsync() && !comet) {
                if (getErrorState().isError()) {
                    // If we know we are closing the connection, don't drain
                    // input. This way uploading a 100GB file doesn't tie up the
                    // thread if the servlet has rejected it.
                    getInputBuffer().setSwallowInput(false);
                } else {
                    // Need to check this again here in case the response was
                    // committed before the error that requires the connection
                    // to be closed occurred.
                    checkExpectationAndResponseStatus();
                }
                endRequest();
            }

            rp.setStage(org.apache.coyote.Constants.STAGE_ENDOUTPUT);

            // If there was an error, make sure the request is counted as
            // and error, and update the statistics counter
            if (getErrorState().isError()) {
                response.setStatus(500);
            }
            request.updateCounters();

            if (!isAsync() && !comet || getErrorState().isError()) {
                if (getErrorState().isIoAllowed()) {
                    getInputBuffer().nextRequest();
                    getOutputBuffer().nextRequest();
                }
            }

            if (!disableUploadTimeout) {
                if(endpoint.getSoTimeout() > 0) {
                    setSocketTimeout(endpoint.getSoTimeout());
                } else {
                    setSocketTimeout(0);
                }
            }

            rp.setStage(org.apache.coyote.Constants.STAGE_KEEPALIVE);

            if (breakKeepAliveLoop(socketWrapper)) {
                break;
            }
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        if (getErrorState().isError() || endpoint.isPaused()) {
            return SocketState.CLOSED;
        } else if (isAsync() || comet) {
            return SocketState.LONG;
        } else if (isUpgrade()) {
            return SocketState.UPGRADING;
        } else if (getUpgradeInbound() != null) {
            return SocketState.UPGRADING_TOMCAT;
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


    private void checkExpectationAndResponseStatus() {
        if (expectation && (response.getStatus() < 200 || response.getStatus() > 299)) {
            // Client sent Expect: 100-continue but received a
            // non-2xx final response. Disable keep-alive (if enabled)
            // to ensure that the connection is closed. Some clients may
            // still send the body, some may send the next request.
            // No way to differentiate, so close the connection to
            // force the client to send the next request.
            getInputBuffer().setSwallowInput(false);
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
            if (getLog().isDebugEnabled()) {
                getLog().debug(sm.getString("http11processor.request.prepare")+
                          " Unsupported HTTP version \""+protocolMB+"\"");
            }
        }
    }


    /**
     * After reading the request headers, we have to setup the request filters.
     */
    protected void prepareRequest() throws IOException {

        contentDelimitation = false;
        expectation = false;

        prepareRequestInternal();

        if (endpoint.isSSLEnabled()) {
            request.scheme().setString("https");
        }

        MessageBytes methodMB = request.method();
        if (methodMB.equals(Constants.GET)) {
            methodMB.setString(Constants.GET);
        } else if (methodMB.equals(Constants.POST)) {
            methodMB.setString(Constants.POST);
        }

        MimeHeaders headers = request.getMimeHeaders();

        // Check connection header
        MessageBytes connectionValueMB = headers.getValue(Constants.CONNECTION);
        if (connectionValueMB != null && !connectionValueMB.isNull()) {
            Set<String> tokens = new HashSet<String>();
            TokenList.parseTokenList(headers.values(Constants.CONNECTION), tokens);
            if (tokens.contains(Constants.CLOSE)) {
                keepAlive = false;
            } else if (tokens.contains(Constants.KEEPALIVE)) {
                keepAlive = true;
            }
        }

        if (http11) {
            MessageBytes expectMB = headers.getValue("expect");
            if (expectMB != null && !expectMB.isNull()) {
                if (expectMB.toString().trim().equalsIgnoreCase("100-continue")) {
                    getInputBuffer().setSwallowInput(false);
                    expectation = true;
                } else {
                    response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                    setErrorState(ErrorState.CLOSE_CLEAN, null);
                }
            }
        }

        // Check user-agent header
        if ((restrictedUserAgents != null) && ((http11) || (keepAlive))) {
            MessageBytes userAgentValueMB = headers.getValue("user-agent");
            // Check in the restricted list, and adjust the http11
            // and keepAlive flags accordingly
            if(userAgentValueMB != null && !userAgentValueMB.isNull()) {
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
                        if (!hostValueMB.getByteChunk().equals(
                                uriB, uriBCStart + pos, slashPos - pos)) {
                            if (allowHostHeaderMismatch) {
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
                                badRequest("http11processor.request.inconsistentHosts");
                            }
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
        InputFilter[] inputFilters = getInputBuffer().getFilters();

        // Parse transfer-encoding header
        MessageBytes transferEncodingValueMB = null;
        if (http11) {
            transferEncodingValueMB = headers.getValue("transfer-encoding");
        }
        if (transferEncodingValueMB != null) {
            List<String> encodingNames = new ArrayList<String>();
            if (TokenList.parseTokenList(headers.values("transfer-encoding"), encodingNames)) {
                for (String encodingName : encodingNames) {
                    // "identity" codings are ignored
                    addInputFilter(inputFilters, encodingName);
                }
            } else {
                // Invalid transfer encoding
                badRequest("http11processor.request.invalidTransferEncoding");
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
            } else {
                getInputBuffer().addActiveFilter(inputFilters[Constants.IDENTITY_FILTER]);
                contentDelimitation = true;
            }
        }

        // Validate host name and extract port if present
        parseHost(hostValueMB);

        if (!contentDelimitation) {
            // If there's no content length
            // (broken HTTP/1.0 or HTTP/1.1), assume
            // the client is not broken and didn't send a body
            getInputBuffer().addActiveFilter(inputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }

        // Advertise sendfile support through a request attribute
        if (endpoint.getUseSendfile()) {
            request.setAttribute(
                    org.apache.coyote.Constants.SENDFILE_SUPPORTED_ATTR,
                    Boolean.TRUE);
        }

        // Advertise comet support through a request attribute
        if (endpoint.getUseComet()) {
            request.setAttribute(
                    org.apache.coyote.Constants.COMET_SUPPORTED_ATTR,
                    Boolean.TRUE);
        }
        // Advertise comet timeout support
        if (endpoint.getUseCometTimeout()) {
            request.setAttribute(
                    org.apache.coyote.Constants.COMET_TIMEOUT_SUPPORTED_ATTR,
                    Boolean.TRUE);
        }
        if (getErrorState().isError()) {
            adapter.log(request, response, 0);
        }
    }


    private void badRequest(String errorKey) {
        response.setStatus(400);
        setErrorState(ErrorState.CLOSE_CLEAN, null);
        if (getLog().isDebugEnabled()) {
            getLog().debug(sm.getString(errorKey));
        }
    }


    /**
     * Connector implementation specific request preparation. Ideally, this will
     * go away in the future.
     */
    protected abstract void prepareRequestInternal();

    /**
     * When committing the response, we have to validate the set of headers, as
     * well as setup the response filters.
     */
    private void prepareResponse() throws IOException {

        boolean entityBody = true;
        contentDelimitation = false;

        OutputFilter[] outputFilters = getOutputBuffer().getFilters();

        if (http09 == true) {
            // HTTP/0.9
            getOutputBuffer().addActiveFilter
                (outputFilters[Constants.IDENTITY_FILTER]);
            return;
        }

        int statusCode = response.getStatus();
        if (statusCode < 200 || statusCode == 204 || statusCode == 205 ||
                statusCode == 304) {
            // No entity body
            getOutputBuffer().addActiveFilter
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
            getOutputBuffer().addActiveFilter
                (outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }

        // Sendfile support
        boolean sendingWithSendfile = false;
        if (getEndpoint().getUseSendfile()) {
            sendingWithSendfile = prepareSendfile(outputFilters);
        }

        // Check for compression
        boolean isCompressible = false;
        boolean useCompression = false;
        if (entityBody && (compressionLevel > 0) && !sendingWithSendfile) {
            isCompressible = isCompressible();
            if (isCompressible) {
                useCompression = useCompression();
            }
            // Change content-length to -1 to force chunking
            if (useCompression) {
                response.setContentLength(-1);
            }
        }

        MimeHeaders headers = response.getMimeHeaders();
        // A SC_NO_CONTENT response may include entity headers
        if (entityBody || statusCode == 204) {
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
        boolean connectionClosePresent = isConnectionToken(headers, Constants.CLOSE);
        if (contentLength != -1) {
            headers.setValue("Content-Length").setLong(contentLength);
            getOutputBuffer().addActiveFilter
                (outputFilters[Constants.IDENTITY_FILTER]);
            contentDelimitation = true;
        } else {
            // If the response code supports an entity body and we're on
            // HTTP 1.1 then we chunk unless we have a Connection: close header
            if (http11 && entityBody && !connectionClosePresent) {
                getOutputBuffer().addActiveFilter(outputFilters[Constants.CHUNKED_FILTER]);
                contentDelimitation = true;
                headers.addValue(Constants.TRANSFERENCODING).setString(Constants.CHUNKED);
            } else {
                getOutputBuffer().addActiveFilter
                    (outputFilters[Constants.IDENTITY_FILTER]);
            }
        }

        if (useCompression) {
            getOutputBuffer().addActiveFilter(outputFilters[Constants.GZIP_FILTER]);
            headers.setValue("Content-Encoding").setString("gzip");
        }
        // If it might be compressed, set the Vary header
        if (isCompressible) {
            ResponseUtil.addVaryFieldName(headers, "accept-encoding");
        }

        // Add date header unless application has already set one (e.g. in a
        // Caching Filter)
        if (headers.getValue("Date") == null) {
            headers.setValue("Date").setString(
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
        keepAlive = keepAlive && !statusDropsConnection(statusCode);
        if (!keepAlive) {
            // Avoid adding the close header twice
            if (!connectionClosePresent) {
                headers.addValue(Constants.CONNECTION).setString(
                        Constants.CLOSE);
            }
        } else if (!http11 && !getErrorState().isError()) {
            headers.addValue(Constants.CONNECTION).setString(Constants.KEEPALIVE);
        }

        // Build the response header
        getOutputBuffer().sendStatus();

        // Add server header
        if (server != null) {
            // Always overrides anything the app might set
            headers.setValue("Server").setString(server);
        } else if (headers.getValue("Server") == null) {
            // If app didn't set the header, use the default
            getOutputBuffer().write(Constants.SERVER_BYTES);
        }

        int size = headers.size();
        for (int i = 0; i < size; i++) {
            getOutputBuffer().sendHeader(headers.getName(i), headers.getValue(i));
        }
        getOutputBuffer().endHeaders();

    }


    private static boolean isConnectionToken(MimeHeaders headers, String token) throws IOException {
        MessageBytes connection = headers.getValue(Constants.CONNECTION);
        if (connection == null) {
            return false;
        }

        Set<String> tokens = new HashSet<String>();
        TokenList.parseTokenList(headers.values(Constants.CONNECTION), tokens);
        return tokens.contains(token);
    }


    protected abstract boolean prepareSendfile(OutputFilter[] outputFilters);


    /*
     * Note: populateHost() is not over-ridden.
     *       request.serverName() will be set to return the default host name by
     *       the Mapper.
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
    public SocketState asyncDispatch(SocketStatus status) {

        RequestInfo rp = request.getRequestProcessor();
        try {
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            if (!getAdapter().asyncDispatch(request, response, status)) {
                setErrorState(ErrorState.CLOSE_NOW, null);
            }
            resetTimeouts();
        } catch (InterruptedIOException e) {
            setErrorState(ErrorState.CLOSE_NOW, e);
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            setErrorState(ErrorState.CLOSE_NOW, t);
            getLog().error(sm.getString("http11processor.request.process"), t);
        } finally {
            if (getErrorState().isError()) {
                // 500 - Internal Server Error
                response.setStatus(500);
                adapter.log(request, response, 0);
            }
        }

        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);

        SocketState state;

        if (getErrorState().isError()) {
            state = SocketState.CLOSED;
        } else if (isAsync()) {
            state = SocketState.LONG;
        } else {
            if (!keepAlive || endpoint.isPaused()) {
                state = SocketState.CLOSED;
            } else {
                endRequest();
                getInputBuffer().nextRequest();
                getOutputBuffer().nextRequest();
                state = SocketState.OPEN;
            }
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Socket: [" + socketWrapper +
                    "], Status in: [" + status +
                    "], State out: [" + state + "]");
        }

        return state;
    }


    @Override
    public boolean isComet() {
        return comet;
    }


    @Override
    public SocketState upgradeDispatch() throws IOException {
        // Should never reach this code but in case we do...
        throw new IOException(
                sm.getString("http11Processor.upgrade"));
    }


    /**
     * @deprecated  Will be removed in Tomcat 8.0.x.
     */
    @Deprecated
    @Override
    public org.apache.coyote.http11.upgrade.UpgradeInbound getUpgradeInbound() {
        return upgradeInbound;
    }


    @Override
    public boolean isUpgrade() {
        return httpUpgradeHandler != null;
    }


    @Override
    public SocketState upgradeDispatch(SocketStatus status) throws IOException {
        // Should never reach this code but in case we do...
        throw new IOException(
                sm.getString("ajpprocessor.httpupgrade.notsupported"));
    }


    @Override
    public HttpUpgradeHandler getHttpUpgradeHandler() {
        return httpUpgradeHandler;
    }


    /**
     * Provides a mechanism for those connector implementations (currently only
     * NIO) that need to reset timeouts from Async timeouts to standard HTTP
     * timeouts once async processing completes.
     */
    protected abstract void resetTimeouts();


    /**
     * Provides a mechanism for those connectors (currently only NIO) that need
     * that need to set comet timeouts.
     */
    protected abstract void setCometTimeouts(SocketWrapper<S> socketWrapper);

    public void endRequest() {

        // Finish the handling of the request
        if (getErrorState().isIoAllowed()) {
            try {
                getInputBuffer().endRequest();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_NOW, e);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // 500 - Internal Server Error
                // Can't add a 500 to the access log since that has already been
                // written in the Adapter.service method.
                response.setStatus(500);
                setErrorState(ErrorState.CLOSE_NOW, t);
                getLog().error(sm.getString("http11processor.request.finish"), t);
            }
        }
        if (getErrorState().isIoAllowed()) {
            try {
                getOutputBuffer().endRequest();
            } catch (IOException e) {
                setErrorState(ErrorState.CLOSE_NOW, e);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                setErrorState(ErrorState.CLOSE_NOW, t);
                getLog().error(sm.getString("http11processor.response.finish"), t);
            }
        }
    }


    /**
     * Checks to see if the keep-alive loop should be broken, performing any
     * processing (e.g. sendfile handling) that may have an impact on whether
     * or not the keep-alive loop should be broken.
     * @return true if the keep-alive loop should be broken
     */
    protected abstract boolean breakKeepAliveLoop(
            SocketWrapper<S> socketWrapper);



    @Override
    public final void recycle(boolean isSocketClosing) {
        getAdapter().checkRecycled(request, response);

        if (getInputBuffer() != null) {
            getInputBuffer().recycle();
        }
        if (getOutputBuffer() != null) {
            getOutputBuffer().recycle();
        }
        if (asyncStateMachine != null) {
            asyncStateMachine.recycle();
        }
        upgradeInbound = null;
        httpUpgradeHandler = null;
        remoteAddr = null;
        remoteHost = null;
        localAddr = null;
        localName = null;
        remotePort = -1;
        localPort = -1;
        comet = false;
        resetErrorState();
        recycleInternal();
    }

    protected abstract void recycleInternal();
}

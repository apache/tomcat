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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.StringTokenizer;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.catalina.core.AsyncContextImpl;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.filters.BufferedInputFilter;
import org.apache.coyote.http11.filters.ChunkedInputFilter;
import org.apache.coyote.http11.filters.ChunkedOutputFilter;
import org.apache.coyote.http11.filters.GzipOutputFilter;
import org.apache.coyote.http11.filters.IdentityInputFilter;
import org.apache.coyote.http11.filters.IdentityOutputFilter;
import org.apache.coyote.http11.filters.SavedRequestInputFilter;
import org.apache.coyote.http11.filters.VoidInputFilter;
import org.apache.coyote.http11.filters.VoidOutputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.res.StringManager;

public abstract class AbstractHttp11Processor {

    /**
     * Logger.
     */
    private static final Log log = LogFactory.getLog(AbstractHttp11Processor.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    protected static boolean isSecurityEnabled = 
        org.apache.coyote.Constants.IS_SECURITY_ENABLED;

    /*
     * Tracks how many internal filters are in the filter library so they
     * are skipped when looking for pluggable filters. 
     */
    private int pluggableFilterIndex = Integer.MAX_VALUE;
    
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
     * Error flag.
     */
    protected boolean error = false;


    /**
     * Keep-alive.
     */
    protected boolean keepAlive = true;


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
     * List of restricted user agents.
     */
    protected Pattern[] restrictedUserAgents = null;


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
    protected int timeout = 300000;


    /**
     * Flag to disable setting a different time-out on uploads.
     */
    protected boolean disableUploadTimeout = false;


    /**
     * Allowed compression level.
     */
    protected int compressionLevel = 0;


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
     * List of user agents to not use gzip with
     */
    protected Pattern noCompressionUserAgents[] = null;

    /**
     * List of MIMES which could be gzipped
     */
    protected String[] compressableMimeTypes =
    { "text/html", "text/xml", "text/plain" };


    /**
     * Host name (used to avoid useless B2C conversion on the host name).
     */
    protected char[] hostNameC = new char[0];


    /**
     * Allow a customized the server header for the tin-foil hat folks.
     */
    protected String server = null;

    
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


    /**
     * Add user-agent for which gzip compression didn't works
     * The user agent String given will be exactly matched
     * to the user-agent header submitted by the client.
     *
     * @param userAgent user-agent string
     */
    public void addNoCompressionUserAgent(String userAgent) {
        try {
            Pattern nRule = Pattern.compile(userAgent);
            noCompressionUserAgents =
                addREArray(noCompressionUserAgents, nRule);
        } catch (PatternSyntaxException pse) {
            log.error(sm.getString("http11processor.regexp.error", userAgent), pse);
        }
    }


    /**
     * Set no compression user agent list (this method is best when used with
     * a large number of connectors, where it would be better to have all of
     * them referenced a single array).
     */
    public void setNoCompressionUserAgents(Pattern[] noCompressionUserAgents) {
        this.noCompressionUserAgents = noCompressionUserAgents;
    }


    /**
     * Set no compression user agent list.
     * List contains users agents separated by ',' :
     *
     * ie: "gorilla,desesplorer,tigrus"
     */
    public void setNoCompressionUserAgents(String noCompressionUserAgents) {
        if (noCompressionUserAgents != null) {
            StringTokenizer st = new StringTokenizer(noCompressionUserAgents, ",");

            while (st.hasMoreTokens()) {
                addNoCompressionUserAgent(st.nextToken().trim());
            }
        }
    }

    /**
     * Add a mime-type which will be compressible
     * The mime-type String will be exactly matched
     * in the response mime-type header .
     *
     * @param mimeType mime-type string
     */
    public void addCompressableMimeType(String mimeType) {
        compressableMimeTypes =
            addStringArray(compressableMimeTypes, mimeType);
    }


    /**
     * Set compressible mime-type list (this method is best when used with
     * a large number of connectors, where it would be better to have all of
     * them referenced a single array).
     */
    public void setCompressableMimeTypes(String[] compressableMimeTypes) {
        this.compressableMimeTypes = compressableMimeTypes;
    }


    /**
     * Set compressable mime-type list
     * List contains users agents separated by ',' :
     *
     * ie: "text/html,text/xml,text/plain"
     */
    public void setCompressableMimeTypes(String compressableMimeTypes) {
        if (compressableMimeTypes != null) {
            this.compressableMimeTypes = null;
            StringTokenizer st = new StringTokenizer(compressableMimeTypes, ",");

            while (st.hasMoreTokens()) {
                addCompressableMimeType(st.nextToken().trim());
            }
        }
    }


    /**
     * Return the list of compressable mime-types.
     */
    public String[] findCompressableMimeTypes() {
        return (compressableMimeTypes);
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
            for (int i = 0; i < sArray.length; i++)
                result[i] = sArray[i];
            result[sArray.length] = value;
        }
        return result;
    }


    /**
     * General use method
     *
     * @param rArray the REArray
     * @param value Obj
     */
    private Pattern[] addREArray(Pattern rArray[], Pattern value) {
        Pattern[] result = null;
        if (rArray == null) {
            result = new Pattern[1];
            result[0] = value;
        }
        else {
            result = new Pattern[rArray.length + 1];
            for (int i = 0; i < rArray.length; i++)
                result[i] = rArray[i];
            result[rArray.length] = value;
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
        if (value == null)
           return false;
        for (int i = 0; i < sArray.length; i++) {
            if (value.startsWith(sArray[i])) {
                return true;
            }
        }
        return false;
    }


    /**
     * Add restricted user-agent (which will downgrade the connector
     * to HTTP/1.0 mode). The user agent String given will be matched
     * via regexp to the user-agent header submitted by the client.
     *
     * @param userAgent user-agent string
     */
    public void addRestrictedUserAgent(String userAgent) {
        try {
            Pattern nRule = Pattern.compile(userAgent);
            restrictedUserAgents = addREArray(restrictedUserAgents, nRule);
        } catch (PatternSyntaxException pse) {
            log.error(sm.getString("http11processor.regexp.error", userAgent), pse);
        }
    }


    /**
     * Set restricted user agent list (this method is best when used with
     * a large number of connectors, where it would be better to have all of
     * them referenced a single array).
     */
    public void setRestrictedUserAgents(Pattern[] restrictedUserAgents) {
        this.restrictedUserAgents = restrictedUserAgents;
    }


    /**
     * Set restricted user agent list (which will downgrade the connector
     * to HTTP/1.0 mode). List contains users agents separated by ',' :
     *
     * ie: "gorilla,desesplorer,tigrus"
     */
    public void setRestrictedUserAgents(String restrictedUserAgents) {
        if (restrictedUserAgents != null) {
            StringTokenizer st =
                new StringTokenizer(restrictedUserAgents, ",");
            while (st.hasMoreTokens()) {
                addRestrictedUserAgent(st.nextToken().trim());
            }
        }
    }


    /**
     * Return the list of restricted user agents.
     */
    public String[] findRestrictedUserAgents() {
        String[] sarr = new String [restrictedUserAgents.length];

        for (int i = 0; i < restrictedUserAgents.length; i++)
            sarr[i] = restrictedUserAgents[i].toString();

        return (sarr);
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
    public void setTimeout( int timeouts ) {
        timeout = timeouts ;
    }

    /**
     * Get the upload timeout.
     */
    public int getTimeout() {
        return timeout;
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


    /** Get the request associated with this processor.
     *
     * @return The request
     */
    public Request getRequest() {
        return request;
    }


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


    /**
     * Check for compression
     */
    protected boolean isCompressable() {

        // Nope Compression could works in HTTP 1.0 also
        // cf: mod_deflate

        // Compression only since HTTP 1.1
        // if (! http11)
        //    return false;

        // Check if browser support gzip encoding
        MessageBytes acceptEncodingMB =
            request.getMimeHeaders().getValue("accept-encoding");

        if ((acceptEncodingMB == null)
            || (acceptEncodingMB.indexOf("gzip") == -1))
            return false;

        // Check if content is not already gzipped
        MessageBytes contentEncodingMB =
            response.getMimeHeaders().getValue("Content-Encoding");

        if ((contentEncodingMB != null)
            && (contentEncodingMB.indexOf("gzip") != -1))
            return false;

        // If force mode, always compress (test purposes only)
        if (compressionLevel == 2)
           return true;

        // Check for incompatible Browser
        if (noCompressionUserAgents != null) {
            MessageBytes userAgentValueMB =
                request.getMimeHeaders().getValue("user-agent");
            if(userAgentValueMB != null) {
                String userAgentValue = userAgentValueMB.toString();

                // If one Regexp rule match, disable compression
                for (int i = 0; i < noCompressionUserAgents.length; i++)
                    if (noCompressionUserAgents[i].matcher(userAgentValue).matches())
                        return false;
            }
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
     * Specialized utility method: find a sequence of lower case bytes inside
     * a ByteChunk.
     */
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
    protected abstract AbstractInputBuffer getInputBuffer();

    
    /**
     * Exposes output buffer to super class to allow better code re-use.
     * @return  The output buffer used by the processor. 
     */
    protected abstract AbstractOutputBuffer getOutputBuffer();

    
    /**
     * Initialize standard input and output filters.
     */
    protected void initializeFilters() {
        // Create and add the identity filters.
        getInputBuffer().addFilter(new IdentityInputFilter());
        getOutputBuffer().addFilter(new IdentityOutputFilter());

        // Create and add the chunked filters.
        getInputBuffer().addFilter(new ChunkedInputFilter());
        getOutputBuffer().addFilter(new ChunkedOutputFilter());

        // Create and add the void filters.
        getInputBuffer().addFilter(new VoidInputFilter());
        getOutputBuffer().addFilter(new VoidOutputFilter());

        // Create and add buffered input filter
        getInputBuffer().addFilter(new BufferedInputFilter());

        // Create and add the chunked filters.
        //getInputBuffer().addFilter(new GzipInputFilter());
        getOutputBuffer().addFilter(new GzipOutputFilter());
        
        pluggableFilterIndex = getInputBuffer().getFilters().length;
    }

    
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
                getInputBuffer().addFilter((InputFilter) obj);
            } else if (obj instanceof OutputFilter) {
                getOutputBuffer().addFilter((OutputFilter) obj);
            } else {
                log.warn(sm.getString("http11processor.filter.unknown",
                        className));
            }
        } catch (Exception e) {
            log.error(sm.getString(
                    "http11processor.filter.error", className), e);
        }
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
            getInputBuffer().addActiveFilter
                (inputFilters[Constants.CHUNKED_FILTER]);
            contentDelimitation = true;
        } else {
            for (int i = pluggableFilterIndex; i < inputFilters.length; i++) {
                if (inputFilters[i].getEncodingName()
                    .toString().equals(encodingName)) {
                    getInputBuffer().addActiveFilter(inputFilters[i]);
                    return true;
                }
            }
            return false;
        }
        return true;
    }


    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    public final void action(ActionCode actionCode, Object param) {
        
        if (actionCode == ActionCode.COMMIT) {
            // Commit current response

            if (response.isCommitted())
                return;

            // Validate and write response headers
            try {
                prepareResponse();
                getOutputBuffer().commit();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }
        } else if (actionCode == ActionCode.ACK) {
            // Acknowledge request
            // Send a 100 status back if it makes sense (response not committed
            // yet, and client specified an expectation for 100-continue)

            if ((response.isCommitted()) || !expectation)
                return;

            getInputBuffer().setSwallowInput(true);
            try {
                getOutputBuffer().sendAck();
            } catch (IOException e) {
                // Set error flag
                error = true;
            }
        } else if (actionCode == ActionCode.CLIENT_FLUSH) {

            try {
                getOutputBuffer().flush();
            } catch (IOException e) {
                // Set error flag
                error = true;
                response.setErrorException(e);
            }

        } else if (actionCode == ActionCode.RESET) {
            // Reset response
            // Note: This must be called before the response is committed

            getOutputBuffer().reset();

        } else if (actionCode == ActionCode.CUSTOM) {
            // Do nothing
            // TODO Remove this action

        } else if (actionCode == ActionCode.REQ_SET_BODY_REPLAY) {
            ByteChunk body = (ByteChunk) param;
            
            InputFilter savedBody = new SavedRequestInputFilter(body);
            savedBody.setRequest(request);

            AbstractInputBuffer internalBuffer = (AbstractInputBuffer)
                request.getInputBuffer();
            internalBuffer.addActiveFilter(savedBody);
        } else if (actionCode == ActionCode.ASYNC_START) {
            asyncStart((AsyncContextImpl) param);
        } else if (actionCode == ActionCode.ASYNC_DISPATCHED) {
            asyncDispatched();
        } else if (actionCode == ActionCode.ASYNC_TIMEOUT) {
            AtomicBoolean result = (AtomicBoolean) param;
            result.set(asyncTimeout());
        } else if (actionCode == ActionCode.ASYNC_RUN) {
            asyncRun((Runnable) param);
        } else if (actionCode == ActionCode.ASYNC_ERROR) {
            asyncError();
        } else if (actionCode == ActionCode.ASYNC_IS_STARTED) {
            ((AtomicBoolean) param).set(isAsyncStarted());
        } else if (actionCode == ActionCode.ASYNC_IS_DISPATCHING) {
            ((AtomicBoolean) param).set(isAsyncDispatching());
        } else if (actionCode == ActionCode.ASYNC_IS_ASYNC) {
            ((AtomicBoolean) param).set(isAsync());
        } else if (actionCode == ActionCode.ASYNC_IS_TIMINGOUT) {
            ((AtomicBoolean) param).set(isAsyncTimingOut());
        } else {
            actionInternal(actionCode, param);
        }
    }
    
    abstract void actionInternal(ActionCode actionCode, Object param);
    

    /**
     * When committing the response, we have to validate the set of headers, as
     * well as setup the response filters.
     */
    private void prepareResponse() {

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
        if ((statusCode == 204) || (statusCode == 205)
            || (statusCode == 304)) {
            // No entity body
            getOutputBuffer().addActiveFilter
                (outputFilters[Constants.VOID_FILTER]);
            entityBody = false;
            contentDelimitation = true;
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
        boolean useCompression = false;
        if (entityBody && (compressionLevel > 0) && !sendingWithSendfile) {
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
            getOutputBuffer().addActiveFilter
                (outputFilters[Constants.IDENTITY_FILTER]);
            contentDelimitation = true;
        } else {
            if (entityBody && http11) {
                getOutputBuffer().addActiveFilter
                    (outputFilters[Constants.CHUNKED_FILTER]);
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

    abstract AbstractEndpoint getEndpoint();
    abstract boolean prepareSendfile(OutputFilter[] outputFilters);
    
    public void endRequest() {

        // Finish the handling of the request
        try {
            getInputBuffer().endRequest();
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
            getOutputBuffer().endRequest();
        } catch (IOException e) {
            error = true;
        } catch (Throwable t) {
            log.error(sm.getString("http11processor.response.finish"), t);
            error = true;
        }

    }
    
    public final void recycle() {
        getInputBuffer().recycle();
        getOutputBuffer().recycle();
        asyncCtxt = null;
        recycleInternal();
    }
    
    protected abstract void recycleInternal();
    
    protected abstract Executor getExecutor();
    
    // -------------------------------------------------- Async state management
    
    /*
     * DISPATCHED    - Standard request. Not in Async mode.
     * STARTING      - ServletRequest.startAsync() has been called but the
     *                 request in which that call was made has not finished
     *                 processing.
     * STARTED       - ServletRequest.startAsync() has been called and the
     *                 request in which that call was made has finished
     *                 processing.
     * MUST_COMPLETE - complete() has been called before the request in which
     *                 ServletRequest.startAsync() has finished. As soon as that
     *                 request finishes, the complete() will be processed.
     * COMPLETING    - The call to complete() was made once the request was in
     *                 the STARTED state. May or may not be triggered by a
     *                 container thread - depends if start(Runnable) was used
     * 
     * TODO - markt - Move this to a separate class
     */
    private static enum AsyncState {
        DISPATCHED(false, false, false),
        STARTING(true, true, false),
        STARTED(true, true, false),
        MUST_COMPLETE(true, false, false),
        COMPLETING(true, false, false),
        TIMING_OUT(true, false, false),
        MUST_DISPATCH(true, false, true),
        DISPATCHING(true, false, true),
        ERROR(true,false,false);
    
        private boolean isAsync;
        private boolean isStarted;
        private boolean isDispatching;
        
        private AsyncState(boolean isAsync, boolean isStarted,
                boolean isDispatching) {
            this.isAsync = isAsync;
            this.isStarted = isStarted;
            this.isDispatching = isDispatching;
        }
        
        public boolean isAsync() {
            return this.isAsync;
        }
        
        public boolean isStarted() {
            return this.isStarted;
        }
        
        public boolean isDispatching() {
            return this.isDispatching;
        }
    }
    
    private volatile AsyncState state = AsyncState.DISPATCHED;
    // Need this to fire listener on complete
    private AsyncContextImpl asyncCtxt = null;
    
    protected boolean isAsync() {
        return state.isAsync();
    }

    protected boolean isAsyncDispatching() {
        return state.isDispatching();
    }

    protected boolean isAsyncStarted() {
        return state.isStarted();
    }

    protected boolean isAsyncTimingOut() {
        return state == AsyncState.TIMING_OUT;
    }


    private synchronized void asyncStart(AsyncContextImpl asyncCtxt) {
        if (state == AsyncState.DISPATCHED) {
            state = AsyncState.STARTING;
            this.asyncCtxt = asyncCtxt;
        } else {
            throw new IllegalStateException(
                    sm.getString("abstractHttp11Protocol.invalidAsyncState",
                            "startAsync()", state));
        }
    }
    
    /*
     * Async has been processed. Whether or not to enter a long poll depends on
     * current state. For example, as per SRV.2.3.3.3 can now process calls to
     * complete() or dispatch().
     */
    protected synchronized SocketState asyncPostProcess() {
        
        if (state == AsyncState.STARTING) {
            state = AsyncState.STARTED;
            return SocketState.LONG;
        } else if (state == AsyncState.MUST_COMPLETE) {
            asyncCtxt.fireOnComplete();
            state = AsyncState.DISPATCHED;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.COMPLETING) {
            state = AsyncState.DISPATCHED;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.MUST_DISPATCH) {
            state = AsyncState.DISPATCHING;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.DISPATCHING) {
            state = AsyncState.DISPATCHED;
            return SocketState.ASYNC_END;
        } else if (state == AsyncState.ERROR) {
            asyncCtxt.fireOnComplete();
            state = AsyncState.DISPATCHED;
            return SocketState.ASYNC_END;
        //} else if (state == AsyncState.DISPATCHED) {
        //    // No state change
        //    return SocketState.OPEN;
        } else {
            throw new IllegalStateException(
                    sm.getString("abstractHttp11Protocol.invalidAsyncState",
                            "asyncLongPoll()", state));
        }
    }
    

    protected synchronized boolean asyncComplete() {
        boolean doComplete = false;
        
        if (state == AsyncState.STARTING) {
            state = AsyncState.MUST_COMPLETE;
        } else if (state == AsyncState.STARTED) {
            state = AsyncState.COMPLETING;
            doComplete = true;
        } else if (state == AsyncState.TIMING_OUT ||
                state == AsyncState.ERROR) {
            state = AsyncState.MUST_COMPLETE;
        } else {
            throw new IllegalStateException(
                    sm.getString("abstractHttp11Protocol.invalidAsyncState",
                            "asyncComplete()", state));
            
        }
        return doComplete;
    }
    
    
    private synchronized boolean asyncTimeout() {
        if (state == AsyncState.STARTED) {
            state = AsyncState.TIMING_OUT;
            return true;
        } else if (state == AsyncState.COMPLETING ||
                state == AsyncState.DISPATCHED) {
            // NOOP - App called complete between the the timeout firing and
            // execution reaching this point
            return false;
        } else {
            throw new IllegalStateException(
                    sm.getString("abstractHttp11Protocol.invalidAsyncState",
                            "timeoutAsync()", state));
        }
    }
    
    
    protected synchronized boolean asyncDispatch() {
        boolean doDispatch = false;
        if (state == AsyncState.STARTING) {
            state = AsyncState.MUST_DISPATCH;
        } else if (state == AsyncState.STARTED) {
            state = AsyncState.DISPATCHING;
            doDispatch = true;
        } else {
            throw new IllegalStateException(
                    sm.getString("abstractHttp11Protocol.invalidAsyncState",
                            "dispatchAsync()", state));
        }
        return doDispatch;
    }
    
    
    private synchronized void asyncDispatched() {
        if (state == AsyncState.DISPATCHING) {
            state = AsyncState.DISPATCHED;
        } else {
            throw new IllegalStateException(
                    sm.getString("abstractHttp11Protocol.invalidAsyncState",
                            "dispatchAsync()", state));
        }
    }
    
    
    private synchronized boolean asyncError() {
        boolean doDispatch = false;
        if (state == AsyncState.DISPATCHED ||
                state == AsyncState.TIMING_OUT) {
            state = AsyncState.ERROR;
        } else {
            throw new IllegalStateException(
                    sm.getString("abstractHttp11Protocol.invalidAsyncState",
                            "dispatchAsync()", state));
        }
        return doDispatch;
    }
    
    private synchronized void asyncRun(Runnable runnable) {
        if (state == AsyncState.STARTING || state ==  AsyncState.STARTED) {
            // Execute the runnable using a container thread from the
            // Connector's thread pool. Use a wrapper to prevent a memory leak
            ClassLoader oldCL;
            if (Constants.IS_SECURITY_ENABLED) {
                PrivilegedAction<ClassLoader> pa = new PrivilegedGetTccl();
                oldCL = AccessController.doPrivileged(pa);
            } else {
                oldCL = Thread.currentThread().getContextClassLoader();
            }
            try {
                if (Constants.IS_SECURITY_ENABLED) {
                    PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                            this.getClass().getClassLoader());
                    AccessController.doPrivileged(pa);
                } else {
                    Thread.currentThread().setContextClassLoader(
                            this.getClass().getClassLoader());
                }
                
                getExecutor().execute(runnable);
            } finally {
                if (Constants.IS_SECURITY_ENABLED) {
                    PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                            oldCL);
                    AccessController.doPrivileged(pa);
                } else {
                    Thread.currentThread().setContextClassLoader(oldCL);
                }
            }
        } else {
            throw new IllegalStateException(
                    sm.getString("abstractHttp11Protocol.invalidAsyncState",
                            "runAsync()", state));
        }

    }
    
    private static class PrivilegedSetTccl implements PrivilegedAction<Void> {

        private ClassLoader cl;

        PrivilegedSetTccl(ClassLoader cl) {
            this.cl = cl;
        }

        @Override
        public Void run() {
            Thread.currentThread().setContextClassLoader(cl);
            return null;
        }
    }

    private static class PrivilegedGetTccl
            implements PrivilegedAction<ClassLoader> {

        @Override
        public ClassLoader run() {
            return Thread.currentThread().getContextClassLoader();
        }
    }
}

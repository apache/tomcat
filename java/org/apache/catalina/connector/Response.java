/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.connector;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.apache.catalina.util.SessionConfig;
import org.apache.coyote.ActionCode;
import org.apache.coyote.ContinueResponseTiming;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.CharsetHolder;
import org.apache.tomcat.util.buf.UEncoder;
import org.apache.tomcat.util.buf.UEncoder.SafeCharsSet;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.MediaTypeCache;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;

/**
 * Wrapper object for the Coyote response.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 */
public class Response implements HttpServletResponse {

    private static final Log log = LogFactory.getLog(Response.class);
    protected static final StringManager sm = StringManager.getManager(Response.class);

    private static final MediaTypeCache MEDIA_TYPE_CACHE = new MediaTypeCache(100);

    /**
     * Coyote response.
     */
    protected final org.apache.coyote.Response coyoteResponse;


    /**
     * The associated output buffer.
     */
    protected final OutputBuffer outputBuffer;


    /**
     * The associated output stream.
     */
    protected CoyoteOutputStream outputStream;


    /**
     * The associated writer.
     */
    protected CoyoteWriter writer;


    /**
     * The application commit flag.
     */
    protected boolean appCommitted = false;


    /**
     * The included flag.
     */
    protected boolean included = false;


    /**
     * The characterEncoding flag
     */
    private boolean isCharacterEncodingSet = false;


    /**
     * Using output stream flag.
     */
    protected boolean usingOutputStream = false;


    /**
     * Using writer flag.
     */
    protected boolean usingWriter = false;


    /**
     * URL encoder.
     */
    protected final UEncoder urlEncoder = new UEncoder(SafeCharsSet.WITH_SLASH);


    /**
     * Recyclable buffer to hold the redirect URL.
     */
    protected final CharChunk redirectURLCC = new CharChunk();


    private HttpServletResponse applicationResponse = null;


    public Response(org.apache.coyote.Response coyoteResponse) {
        this(coyoteResponse, OutputBuffer.DEFAULT_BUFFER_SIZE);
    }


    public Response(org.apache.coyote.Response coyoteResponse, int outputBufferSize) {
        this.coyoteResponse = coyoteResponse;
        outputBuffer = new OutputBuffer(outputBufferSize, coyoteResponse);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * @return the Coyote response.
     */
    public org.apache.coyote.Response getCoyoteResponse() {
        return this.coyoteResponse;
    }


    /**
     * @return the Context within which this Request is being processed.
     */
    public Context getContext() {
        return request.getContext();
    }


    /**
     * Release all object references, and initialize instance variables, in preparation for reuse of this object.
     */
    public void recycle() {

        outputBuffer.recycle();
        usingOutputStream = false;
        usingWriter = false;
        appCommitted = false;
        included = false;
        isCharacterEncodingSet = false;

        applicationResponse = null;
        if (getRequest().getDiscardFacades()) {
            if (facade != null) {
                facade.clear();
                facade = null;
            }
            if (outputStream != null) {
                outputStream.clear();
                outputStream = null;
            }
            if (writer != null) {
                writer.clear();
                writer = null;
            }
        } else if (writer != null) {
            writer.recycle();
        }

    }


    // ------------------------------------------------------- Response Methods

    /**
     * @return the number of bytes the application has actually written to the output stream. This excludes chunking,
     *             compression, etc. as well as headers.
     */
    public long getContentWritten() {
        return outputBuffer.getContentWritten();
    }


    /**
     * @return the number of bytes the actually written to the socket. This includes chunking, compression, etc. but
     *             excludes headers.
     *
     * @param flush if <code>true</code> will perform a buffer flush first
     */
    public long getBytesWritten(boolean flush) {
        if (flush) {
            try {
                outputBuffer.flush();
            } catch (IOException ioe) {
                // Ignore - the client has probably closed the connection
            }
        }
        return getCoyoteResponse().getBytesWritten(flush);
    }

    /**
     * Set the application commit flag.
     *
     * @param appCommitted The new application committed flag value
     */
    public void setAppCommitted(boolean appCommitted) {
        this.appCommitted = appCommitted;
    }


    /**
     * Application commit flag accessor.
     *
     * @return <code>true</code> if the application has committed the response
     */
    public boolean isAppCommitted() {
        return this.appCommitted || isCommitted() || isSuspended() ||
                ((getContentLength() > 0) && (getContentWritten() >= getContentLength()));
    }


    /**
     * The request with which this response is associated.
     */
    protected Request request = null;

    /**
     * @return the Request with which this Response is associated.
     */
    public Request getRequest() {
        return this.request;
    }

    /**
     * Set the Request with which this Response is associated.
     *
     * @param request The new associated request
     */
    public void setRequest(Request request) {
        this.request = request;
    }


    /**
     * The facade associated with this response.
     */
    protected ResponseFacade facade = null;


    /**
     * @return the <code>ServletResponse</code> for which this object is the facade.
     */
    public HttpServletResponse getResponse() {
        if (facade == null) {
            facade = new ResponseFacade(this);
        }
        if (applicationResponse == null) {
            applicationResponse = facade;
        }
        return applicationResponse;
    }


    /**
     * Set a wrapped HttpServletResponse to pass to the application. Components wishing to wrap the response should
     * obtain the response via {@link #getResponse()}, wrap it and then call this method with the wrapped response.
     *
     * @param applicationResponse The wrapped response to pass to the application
     */
    public void setResponse(HttpServletResponse applicationResponse) {
        // Check the wrapper wraps this request
        ServletResponse r = applicationResponse;
        while (r instanceof HttpServletResponseWrapper) {
            r = ((HttpServletResponseWrapper) r).getResponse();
        }
        if (r != facade) {
            throw new IllegalArgumentException(sm.getString("response.illegalWrap"));
        }
        this.applicationResponse = applicationResponse;
    }


    /**
     * Set the suspended flag.
     *
     * @param suspended The new suspended flag value
     */
    public void setSuspended(boolean suspended) {
        outputBuffer.setSuspended(suspended);
    }


    /**
     * Suspended flag accessor.
     *
     * @return <code>true</code> if the response is suspended
     */
    public boolean isSuspended() {
        return outputBuffer.isSuspended();
    }


    /**
     * Closed flag accessor.
     *
     * @return <code>true</code> if the response has been closed
     */
    public boolean isClosed() {
        return outputBuffer.isClosed();
    }


    /**
     * Set the error flag if not already set.
     */
    public void setError() {
        getCoyoteResponse().setError();
    }


    /**
     * Error flag accessor.
     *
     * @return <code>true</code> if the response has encountered an error
     */
    public boolean isError() {
        return getCoyoteResponse().isError();
    }


    public boolean isErrorReportRequired() {
        return getCoyoteResponse().isErrorReportRequired();
    }


    public boolean setErrorReported() {
        return getCoyoteResponse().setErrorReported();
    }


    public void resetError() {
        getCoyoteResponse().resetError();
    }


    /**
     * Perform whatever actions are required to flush and close the output stream or writer, in a single operation.
     *
     * @exception IOException if an input/output error occurs
     */
    public void finishResponse() throws IOException {
        // Writing leftover bytes
        outputBuffer.close();
    }


    /**
     * @return the content length that was set or calculated for this Response.
     */
    public int getContentLength() {
        return getCoyoteResponse().getContentLength();
    }


    @Override
    public String getContentType() {
        return getCoyoteResponse().getContentType();
    }


    /**
     * Return a PrintWriter that can be used to render error messages, regardless of whether a stream or writer has
     * already been acquired.
     *
     * @return Writer which can be used for error reports. If the response is not an error report returned using
     *             sendError or triggered by an unexpected exception thrown during the servlet processing (and only in
     *             that case), null will be returned if the response stream has already been used.
     *
     * @exception IOException if an input/output error occurs
     */
    public PrintWriter getReporter() throws IOException {
        if (outputBuffer.isNew()) {
            outputBuffer.checkConverter();
            if (writer == null) {
                writer = new CoyoteWriter(outputBuffer);
            }
            return writer;
        } else {
            return null;
        }
    }


    // ------------------------------------------------ ServletResponse Methods


    @Override
    public void flushBuffer() throws IOException {
        outputBuffer.flush();
    }


    @Override
    public int getBufferSize() {
        return outputBuffer.getBufferSize();
    }


    @Override
    public String getCharacterEncoding() {
        String charset = getCoyoteResponse().getCharsetHolder().getName();
        if (charset == null) {
            Context context = getContext();
            if (context != null) {
                charset = context.getResponseCharacterEncoding();
            }
        }

        if (charset == null) {
            charset = org.apache.coyote.Constants.DEFAULT_BODY_CHARSET.name();
        }

        return charset;
    }


    @Override
    public ServletOutputStream getOutputStream() throws IOException {

        if (usingWriter) {
            throw new IllegalStateException(sm.getString("coyoteResponse.getOutputStream.ise"));
        }

        usingOutputStream = true;
        if (outputStream == null) {
            outputStream = new CoyoteOutputStream(outputBuffer);
        }
        return outputStream;

    }


    @Override
    public Locale getLocale() {
        return getCoyoteResponse().getLocale();
    }


    @Override
    public PrintWriter getWriter() throws IOException {

        if (usingOutputStream) {
            throw new IllegalStateException(sm.getString("coyoteResponse.getWriter.ise"));
        }

        if (request.getConnector().getEnforceEncodingInGetWriter()) {
            /*
             * If the response's character encoding has not been specified as described in
             * <code>getCharacterEncoding</code> (i.e., the method just returns the default value
             * <code>ISO-8859-1</code>), <code>getWriter</code> updates it to <code>ISO-8859-1</code> (with the effect
             * that a subsequent call to getContentType() will include a charset=ISO-8859-1 component which will also be
             * reflected in the Content-Type response header, thereby satisfying the Servlet spec requirement that
             * containers must communicate the character encoding used for the servlet response's writer to the client).
             */
            setCharacterEncoding(getCharacterEncoding());
        }

        usingWriter = true;
        outputBuffer.checkConverter();
        if (writer == null) {
            writer = new CoyoteWriter(outputBuffer);
        }
        return writer;
    }


    @Override
    public boolean isCommitted() {
        return getCoyoteResponse().isCommitted();
    }


    @Override
    public void reset() {
        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        getCoyoteResponse().reset();
        outputBuffer.reset();
        usingOutputStream = false;
        usingWriter = false;
        isCharacterEncodingSet = false;
    }


    @Override
    public void resetBuffer() {
        resetBuffer(false);
    }


    /**
     * Reset the data buffer and the using Writer/Stream flags but not any status or header information.
     *
     * @param resetWriterStreamFlags <code>true</code> if the internal <code>usingWriter</code>,
     *                                   <code>usingOutputStream</code>, <code>isCharacterEncodingSet</code> flags
     *                                   should also be reset
     *
     * @exception IllegalStateException if the response has already been committed
     */
    public void resetBuffer(boolean resetWriterStreamFlags) {

        if (isCommitted()) {
            throw new IllegalStateException(sm.getString("coyoteResponse.resetBuffer.ise"));
        }

        outputBuffer.reset(resetWriterStreamFlags);

        if (resetWriterStreamFlags) {
            usingOutputStream = false;
            usingWriter = false;
            isCharacterEncodingSet = false;
        }

    }


    @Override
    public void setBufferSize(int size) {

        if (isCommitted() || !outputBuffer.isNew()) {
            throw new IllegalStateException(sm.getString("coyoteResponse.setBufferSize.ise"));
        }

        outputBuffer.setBufferSize(size);

    }


    @Override
    public void setContentLength(int length) {

        setContentLengthLong(length);
    }


    @Override
    public void setContentLengthLong(long length) {
        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        getCoyoteResponse().setContentLength(length);
    }


    @Override
    public void setContentType(String type) {

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        if (type == null) {
            getCoyoteResponse().setContentType(null);
            getCoyoteResponse().setCharsetHolder(CharsetHolder.EMPTY);
            isCharacterEncodingSet = false;
            return;
        }

        String[] m = MEDIA_TYPE_CACHE.parse(type);
        if (m == null) {
            // Invalid - Assume no charset and just pass through whatever
            // the user provided.
            getCoyoteResponse().setContentTypeNoCharset(type);
            return;
        }


        if (m[1] == null) {
            // No charset and we know value is valid as cache lookup was
            // successful
            // Pass-through user provided value in case user-agent is buggy and
            // requires specific format
            getCoyoteResponse().setContentTypeNoCharset(type);
        } else {
            // There is a charset so have to rebuild content-type without it
            getCoyoteResponse().setContentTypeNoCharset(m[0]);

            // Ignore charset if getWriter() has already been called
            if (!usingWriter) {
                getCoyoteResponse().setCharsetHolder(CharsetHolder.getInstance(m[1]));
                try {
                    getCoyoteResponse().getCharsetHolder().validate();
                } catch (UnsupportedEncodingException e) {
                    log.warn(sm.getString("coyoteResponse.encoding.invalid", m[1]), e);
                }

                isCharacterEncodingSet = true;
            }
        }
    }


    @Override
    public void setCharacterEncoding(String encoding) {

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        // Ignore any call made after the getWriter has been invoked
        // The default should be used
        if (usingWriter) {
            return;
        }

        getCoyoteResponse().setCharsetHolder(CharsetHolder.getInstance(encoding));
        try {
            getCoyoteResponse().getCharsetHolder().validate();
        } catch (UnsupportedEncodingException e) {
            log.warn(sm.getString("coyoteResponse.encoding.invalid", encoding), e);
            return;
        }
        if (encoding == null) {
            isCharacterEncodingSet = false;
        } else {
            isCharacterEncodingSet = true;
        }
    }


    @Override
    public void setCharacterEncoding(Charset charset) {

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        // Ignore any call made after the getWriter has been invoked
        // The default should be used
        if (usingWriter) {
            return;
        }

        getCoyoteResponse().setCharsetHolder(CharsetHolder.getInstance(charset));
        if (charset == null) {
            isCharacterEncodingSet = false;
        } else {
            isCharacterEncodingSet = true;
        }
    }


    @Override
    public void setLocale(Locale locale) {

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        getCoyoteResponse().setLocale(locale);

        // Ignore any call made after the getWriter has been invoked.
        // The default should be used
        if (usingWriter) {
            return;
        }

        if (isCharacterEncodingSet) {
            return;
        }

        if (locale == null) {
            getCoyoteResponse().setCharsetHolder(CharsetHolder.EMPTY);
        } else {
            // In some error handling scenarios, the context is unknown
            // (e.g. a 404 when a ROOT context is not present)
            Context context = getContext();
            if (context != null) {
                String charset = context.getCharset(locale);
                if (charset != null) {
                    getCoyoteResponse().setCharsetHolder(CharsetHolder.getInstance(charset));
                    try {
                        getCoyoteResponse().getCharsetHolder().validate();
                    } catch (UnsupportedEncodingException e) {
                        log.warn(sm.getString("coyoteResponse.encoding.invalid", charset), e);
                    }
                }
            }
        }
    }


    // --------------------------------------------------- HttpResponse Methods


    @Override
    public String getHeader(String name) {
        return getCoyoteResponse().getMimeHeaders().getHeader(name);
    }


    @Override
    public Collection<String> getHeaderNames() {
        MimeHeaders headers = getCoyoteResponse().getMimeHeaders();
        int n = headers.size();
        List<String> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(headers.getName(i).toString());
        }
        return result;

    }


    @Override
    public Collection<String> getHeaders(String name) {
        Enumeration<String> enumeration = getCoyoteResponse().getMimeHeaders().values(name);
        Set<String> result = new LinkedHashSet<>();
        while (enumeration.hasMoreElements()) {
            result.add(enumeration.nextElement());
        }
        return result;
    }


    /**
     * @return the error message that was set with <code>sendError()</code> for this Response.
     */
    public String getMessage() {
        return getCoyoteResponse().getMessage();
    }


    @Override
    public int getStatus() {
        return getCoyoteResponse().getStatus();
    }


    // -------------------------------------------- HttpServletResponse Methods

    /**
     * Add the specified Cookie to those that will be included with this Response.
     *
     * @param cookie Cookie to be added
     */
    @Override
    public void addCookie(final Cookie cookie) {

        // Ignore any call from an included servlet
        if (included || isCommitted()) {
            return;
        }

        String header = generateCookieString(cookie);
        // if we reached here, no exception, cookie is valid
        addHeader("Set-Cookie", header, getContext().getCookieProcessor().getCharset());
    }

    /**
     * Special method for adding a session cookie as we should be overriding any previous.
     *
     * @param cookie The new session cookie to add the response
     */
    public void addSessionCookieInternal(final Cookie cookie) {
        if (isCommitted()) {
            return;
        }

        String name = cookie.getName();
        final String headername = "Set-Cookie";
        final String startsWith = name + "=";
        String header = generateCookieString(cookie);
        boolean set = false;
        MimeHeaders headers = getCoyoteResponse().getMimeHeaders();
        int n = headers.size();
        for (int i = 0; i < n; i++) {
            if (headers.getName(i).toString().equals(headername)) {
                if (headers.getValue(i).toString().startsWith(startsWith)) {
                    headers.getValue(i).setString(header);
                    set = true;
                }
            }
        }
        if (!set) {
            addHeader(headername, header);
        }


    }

    public String generateCookieString(final Cookie cookie) {
        // Web application code can receive a IllegalArgumentException
        // from the generateHeader() invocation
        return getContext().getCookieProcessor().generateHeader(cookie, request.getRequest());
    }


    @Override
    public void addDateHeader(String name, long value) {

        if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        addHeader(name, FastHttpDateFormat.formatDate(value));
    }


    @Override
    public void addHeader(String name, String value) {
        addHeader(name, value, null);
    }


    private void addHeader(String name, String value, Charset charset) {

        if (name == null || name.length() == 0 || value == null) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        char cc = name.charAt(0);
        if (cc == 'C' || cc == 'c') {
            if (checkSpecialHeader(name, value)) {
                return;
            }
        }

        getCoyoteResponse().addHeader(name, value, charset);
    }


    /**
     * An extended version of this exists in {@link org.apache.coyote.Response}. This check is required here to ensure
     * that the usingWriter check in {@link #setContentType(String)} is applied since usingWriter is not visible to
     * {@link org.apache.coyote.Response} Called from set/addHeader.
     *
     * @return <code>true</code> if the header is special, no need to set the header.
     */
    private boolean checkSpecialHeader(String name, String value) {
        if (name.equalsIgnoreCase("Content-Type")) {
            setContentType(value);
            return true;
        }
        return false;
    }


    @Override
    public void addIntHeader(String name, int value) {

        if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        addHeader(name, "" + value);

    }


    @Override
    public boolean containsHeader(String name) {
        // Need special handling for Content-Type and Content-Length due to
        // special handling of these in coyoteResponse
        char cc = name.charAt(0);
        if (cc == 'C' || cc == 'c') {
            if (name.equalsIgnoreCase("Content-Type")) {
                // Will return null if this has not been set
                return (getCoyoteResponse().getContentType() != null);
            }
            if (name.equalsIgnoreCase("Content-Length")) {
                // -1 means not known and is not sent to client
                return (getCoyoteResponse().getContentLengthLong() != -1);
            }
        }

        return getCoyoteResponse().containsHeader(name);
    }


    @Override
    public void setTrailerFields(Supplier<Map<String,String>> supplier) {
        getCoyoteResponse().setTrailerFields(supplier);
    }


    @Override
    public Supplier<Map<String,String>> getTrailerFields() {
        return getCoyoteResponse().getTrailerFields();
    }


    @Override
    public String encodeRedirectURL(String url) {
        if (isEncodeable(toAbsolute(url))) {
            return toEncoded(url, request.getSessionInternal().getIdInternal());
        } else {
            return url;
        }
    }


    @Override
    public String encodeURL(String url) {

        String absolute;
        try {
            absolute = toAbsolute(url);
        } catch (IllegalArgumentException iae) {
            // Relative URL
            return url;
        }

        if (isEncodeable(absolute)) {
            // W3c spec clearly said
            if (url.equalsIgnoreCase("")) {
                url = absolute;
            } else if (url.equals(absolute) && !hasPath(url)) {
                url += '/';
            }
            return toEncoded(url, request.getSessionInternal().getIdInternal());
        } else {
            return url;
        }

    }


    /**
     * Send an acknowledgement of a request.
     *
     * @param continueResponseTiming Indicates when the request for the ACK originated so it can be compared with the
     *                                   configured timing for ACK responses.
     *
     * @exception IOException if an input/output error occurs
     */
    public void sendAcknowledgement(ContinueResponseTiming continueResponseTiming) throws IOException {

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        getCoyoteResponse().action(ActionCode.ACK, continueResponseTiming);
    }


    @Override
    public void sendError(int status) throws IOException {
        sendError(status, null);
    }


    @Override
    public void sendError(int status, String message) throws IOException {

        if (isCommitted()) {
            throw new IllegalStateException(sm.getString("coyoteResponse.sendError.ise"));
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        setError();

        getCoyoteResponse().setStatus(status);
        getCoyoteResponse().setMessage(message);

        // Clear any data content that has been buffered
        resetBuffer();

        // Cause the response to be finished (from the application perspective)
        setSuspended(true);
    }


    @Override
    public void sendRedirect(String location, int status, boolean clearBuffer) throws IOException {
        if (isCommitted()) {
            throw new IllegalStateException(sm.getString("coyoteResponse.sendRedirect.ise"));
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        // Clear any data content that has been buffered
        if (clearBuffer) {
            resetBuffer(true);
        }

        // Generate a temporary redirect to the specified location
        try {
            Context context = getContext();
            // If no ROOT context is defined, the context can be null.
            // In this case, the default Tomcat values are assumed, but without
            // reference to org.apache.catalina.STRICT_SERVLET_COMPLIANCE.
            String locationUri;
            // Relative redirects require HTTP/1.1 or later
            if (getRequest().getCoyoteRequest().getSupportsRelativeRedirects() &&
                    (context == null || context.getUseRelativeRedirects())) {
                locationUri = location;
            } else {
                locationUri = toAbsolute(location);
            }
            setStatus(status);
            setHeader("Location", locationUri);
            if (clearBuffer && context != null && context.getSendRedirectBody()) {
                PrintWriter writer = getWriter();
                writer.print(sm.getString("coyoteResponse.sendRedirect.note", Escape.htmlElementContent(locationUri)));
                flushBuffer();
            }
        } catch (IllegalArgumentException e) {
            log.warn(sm.getString("response.sendRedirectFail", location), e);
            setStatus(SC_NOT_FOUND);
        }

        // Cause the response to be finished (from the application perspective)
        setSuspended(true);
    }


    @Override
    public void setDateHeader(String name, long value) {

        if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        setHeader(name, FastHttpDateFormat.formatDate(value));
    }


    @Override
    public void setHeader(String name, String value) {

        if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        char cc = name.charAt(0);
        if (cc == 'C' || cc == 'c') {
            if (checkSpecialHeader(name, value)) {
                return;
            }
        }

        getCoyoteResponse().setHeader(name, value);
    }


    @Override
    public void setIntHeader(String name, int value) {

        if (name == null || name.length() == 0) {
            return;
        }

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        setHeader(name, "" + value);

    }


    @Override
    public void setStatus(int status) {

        if (isCommitted()) {
            return;
        }

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        getCoyoteResponse().setStatus(status);
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Return <code>true</code> if the specified URL should be encoded with a session identifier. This will be true if
     * all of the following conditions are met:
     * <ul>
     * <li>The request we are responding to asked for a valid session
     * <li>The requested session ID was not received via a cookie
     * <li>The specified URL points back to somewhere within the web application that is responding to this request
     * </ul>
     *
     * @param location Absolute URL to be validated
     *
     * @return <code>true</code> if the URL should be encoded
     */
    protected boolean isEncodeable(final String location) {

        if (location == null) {
            return false;
        }

        // Is this an intra-document reference?
        if (location.startsWith("#")) {
            return false;
        }

        // Are we in a valid session that is not using cookies?
        final Request hreq = request;
        final Session session = hreq.getSessionInternal(false);
        if (session == null) {
            return false;
        }
        if (hreq.isRequestedSessionIdFromCookie()) {
            return false;
        }

        // Is URL encoding permitted
        if (!hreq.getServletContext().getEffectiveSessionTrackingModes().contains(SessionTrackingMode.URL)) {
            return false;
        }

        return doIsEncodeable(getContext(), hreq, session, location);
    }


    private static boolean doIsEncodeable(Context context, Request hreq, Session session, String location) {
        // Is this a valid absolute URL?
        URL url = null;
        try {
            URI uri = new URI(location);
            url = uri.toURL();
        } catch (MalformedURLException | URISyntaxException | IllegalArgumentException e) {
            return false;
        }

        // Does this URL match down to (and including) the context path?
        if (!hreq.getScheme().equalsIgnoreCase(url.getProtocol())) {
            return false;
        }
        if (!hreq.getServerName().equalsIgnoreCase(url.getHost())) {
            return false;
        }
        int serverPort = hreq.getServerPort();
        if (serverPort == -1) {
            if ("https".equals(hreq.getScheme())) {
                serverPort = 443;
            } else {
                serverPort = 80;
            }
        }
        int urlPort = url.getPort();
        if (urlPort == -1) {
            if ("https".equals(url.getProtocol())) {
                urlPort = 443;
            } else {
                urlPort = 80;
            }
        }
        if (serverPort != urlPort) {
            return false;
        }

        String contextPath = context.getPath();
        if (contextPath != null) {
            String file = url.getFile();
            if (!file.startsWith(contextPath)) {
                return false;
            }
            String tok = ";" + SessionConfig.getSessionUriParamName(context) + "=" + session.getIdInternal();
            if (file.indexOf(tok, contextPath.length()) >= 0) {
                return false;
            }
        }

        // This URL belongs to our web application, so it is encodeable
        return true;

    }


    /**
     * Convert (if necessary) and return the absolute URL that represents the resource referenced by this possibly
     * relative URL. If this URL is already absolute, return it unchanged.
     *
     * @param location URL to be (possibly) converted and then returned
     *
     * @return the encoded URL
     *
     * @exception IllegalArgumentException if a MalformedURLException is thrown when converting the relative URL to an
     *                                         absolute one
     */
    protected String toAbsolute(String location) {

        if (location == null) {
            return location;
        }

        boolean leadingSlash = location.startsWith("/");

        if (location.startsWith("//")) {
            // Scheme relative
            redirectURLCC.recycle();
            // Add the scheme
            String scheme = request.getScheme();
            try {
                redirectURLCC.append(scheme, 0, scheme.length());
                redirectURLCC.append(':');
                redirectURLCC.append(location, 0, location.length());
                return redirectURLCC.toString();
            } catch (IOException e) {
                throw new IllegalArgumentException(location, e);
            }

        } else if (leadingSlash || !UriUtil.hasScheme(location)) {

            redirectURLCC.recycle();

            String scheme = request.getScheme();
            String name = request.getServerName();
            int port = request.getServerPort();

            try {
                redirectURLCC.append(scheme, 0, scheme.length());
                redirectURLCC.append("://", 0, 3);
                redirectURLCC.append(name, 0, name.length());
                if ((scheme.equals("http") && port != 80) || (scheme.equals("https") && port != 443)) {
                    redirectURLCC.append(':');
                    String portS = port + "";
                    redirectURLCC.append(portS, 0, portS.length());
                }
                if (!leadingSlash) {
                    String relativePath = request.getDecodedRequestURI();
                    int pos = relativePath.lastIndexOf('/');
                    CharChunk encodedURI = urlEncoder.encodeURL(relativePath, 0, pos);
                    redirectURLCC.append(encodedURI);
                    encodedURI.recycle();
                    redirectURLCC.append('/');
                }
                redirectURLCC.append(location, 0, location.length());

                normalize(redirectURLCC);
            } catch (IOException e) {
                throw new IllegalArgumentException(location, e);
            }

            return redirectURLCC.toString();

        } else {

            return location;

        }

    }

    /**
     * Removes /./ and /../ sequences from absolute URLs. Code borrowed heavily from CoyoteAdapter.normalize()
     *
     * @param cc the char chunk containing the chars to normalize
     */
    private void normalize(CharChunk cc) {
        // Strip query string and/or fragment first as doing it this way makes
        // the normalization logic a lot simpler
        int truncate = cc.indexOf('?');
        if (truncate == -1) {
            truncate = cc.indexOf('#');
        }
        char[] truncateCC = null;
        if (truncate > -1) {
            truncateCC = Arrays.copyOfRange(cc.getBuffer(), cc.getStart() + truncate, cc.getEnd());
            cc.setEnd(cc.getStart() + truncate);
        }

        if (cc.endsWith("/.") || cc.endsWith("/..")) {
            try {
                cc.append('/');
            } catch (IOException e) {
                throw new IllegalArgumentException(cc.toString(), e);
            }
        }

        char[] c = cc.getChars();
        int start = cc.getStart();
        int end = cc.getEnd();
        int index = 0;
        int startIndex = 0;

        // Advance past the first three / characters (should place index just
        // scheme://host[:port]

        for (int i = 0; i < 3; i++) {
            startIndex = cc.indexOf('/', startIndex + 1);
        }

        // Remove /./
        index = startIndex;
        while (true) {
            index = cc.indexOf("/./", 0, 3, index);
            if (index < 0) {
                break;
            }
            copyChars(c, start + index, start + index + 2, end - start - index - 2);
            end = end - 2;
            cc.setEnd(end);
        }

        // Remove /../
        index = startIndex;
        int pos;
        while (true) {
            index = cc.indexOf("/../", 0, 4, index);
            if (index < 0) {
                break;
            }
            // Can't go above the server root
            if (index == startIndex) {
                throw new IllegalArgumentException();
            }
            int index2 = -1;
            for (pos = start + index - 1; (pos >= 0) && (index2 < 0); pos--) {
                if (c[pos] == (byte) '/') {
                    index2 = pos;
                }
            }
            copyChars(c, start + index2, start + index + 3, end - start - index - 3);
            end = end + index2 - index - 3;
            cc.setEnd(end);
            index = index2;
        }

        // Add the query string and/or fragment (if present) back in
        if (truncateCC != null) {
            try {
                cc.append(truncateCC, 0, truncateCC.length);
            } catch (IOException ioe) {
                throw new IllegalArgumentException(ioe);
            }
        }
    }

    private void copyChars(char[] c, int dest, int src, int len) {
        System.arraycopy(c, src, c, dest, len);
    }


    /**
     * Determine if an absolute URL has a path component.
     *
     * @param uri the URL that will be checked
     *
     * @return <code>true</code> if the URL has a path
     */
    private boolean hasPath(String uri) {
        int pos = uri.indexOf("://");
        if (pos < 0) {
            return false;
        }
        pos = uri.indexOf('/', pos + 3);
        if (pos < 0) {
            return false;
        }
        return true;
    }

    /**
     * Return the specified URL with the specified session identifier suitably encoded.
     *
     * @param url       URL to be encoded with the session id
     * @param sessionId Session id to be included in the encoded URL
     *
     * @return the encoded URL
     */
    protected String toEncoded(String url, String sessionId) {
        if (url == null || sessionId == null) {
            return url;
        }

        String path = url;
        String query = "";
        String anchor = "";
        int question = url.indexOf('?');
        if (question >= 0) {
            path = url.substring(0, question);
            query = url.substring(question);
        }
        int pound = path.indexOf('#');
        if (pound >= 0) {
            anchor = path.substring(pound);
            path = path.substring(0, pound);
        }
        StringBuilder sb = new StringBuilder(path);
        if (sb.length() > 0) { // jsessionid can't be first.
            sb.append(';');
            sb.append(SessionConfig.getSessionUriParamName(request.getContext()));
            sb.append('=');
            sb.append(sessionId);
        }
        sb.append(anchor);
        sb.append(query);
        return sb.toString();
    }
}

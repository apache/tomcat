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
package org.apache.coyote;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletConnection;

import org.apache.tomcat.util.buf.CharsetHolder;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.Parameters;
import org.apache.tomcat.util.http.ServerCookies;
import org.apache.tomcat.util.http.parser.MediaType;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * This is a low-level, efficient representation of a server request. Most fields are GC-free, expensive operations are
 * delayed until the user code needs the information. Processing is delegated to modules, using a hook mechanism. This
 * class is not intended for user code - it is used internally by tomcat for processing the request in the most
 * efficient way. Users ( servlets ) can access the information using a facade, which provides the high-level view of
 * the request. Tomcat defines a number of attributes:
 * <ul>
 * <li>"org.apache.tomcat.request" - allows access to the low-level request object in trusted applications
 * </ul>
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author Alex Cruikshank [alex@epitonic.com]
 * @author Hans Bergsten [hans@gefionsoftware.com]
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public final class Request {

    private static final StringManager sm = StringManager.getManager(Request.class);

    // Expected maximum typical number of cookies per request.
    private static final int INITIAL_COOKIE_SIZE = 4;

    /*
     * At 100,000 requests a second there are enough IDs here for ~3,000,000 years before it overflows (and then we have
     * another 3,000,000 years before it gets back to zero).
     *
     * Local testing shows that 5, 10, 50, 500 or 1000 threads can obtain 60,000,000+ IDs a second from a single
     * AtomicLong. That is about about 17ns per request. It does not appear that the introduction of this counter will
     * cause a bottleneck for request processing.
     */
    private static final AtomicLong requestIdGenerator = new AtomicLong(0);

    // ----------------------------------------------------------- Constructors

    public Request() {
        parameters.setQuery(queryMB);
        parameters.setURLDecoder(urlDecoder);
    }


    // ----------------------------------------------------- Instance Variables

    private int serverPort = -1;
    private final MessageBytes serverNameMB = MessageBytes.newInstance();

    private int remotePort;
    private int localPort;

    private final MessageBytes schemeMB = MessageBytes.newInstance();

    private final MessageBytes methodMB = MessageBytes.newInstance();
    private final MessageBytes uriMB = MessageBytes.newInstance();
    private final MessageBytes decodedUriMB = MessageBytes.newInstance();
    private final MessageBytes queryMB = MessageBytes.newInstance();
    private final MessageBytes protoMB = MessageBytes.newInstance();

    private volatile String requestId = Long.toString(requestIdGenerator.getAndIncrement());

    // remote address/host
    private final MessageBytes remoteAddrMB = MessageBytes.newInstance();
    private final MessageBytes peerAddrMB = MessageBytes.newInstance();
    private final MessageBytes localNameMB = MessageBytes.newInstance();
    private final MessageBytes remoteHostMB = MessageBytes.newInstance();
    private final MessageBytes localAddrMB = MessageBytes.newInstance();

    private final MimeHeaders headers = new MimeHeaders();
    private final MimeHeaders trailerFields = new MimeHeaders();

    /**
     * Path parameters
     */
    private final Map<String,String> pathParameters = new HashMap<>();

    /**
     * Notes.
     */
    private final Object notes[] = new Object[Constants.MAX_NOTES];


    /**
     * Associated input buffer.
     */
    private InputBuffer inputBuffer = null;


    /**
     * URL decoder.
     */
    private final UDecoder urlDecoder = new UDecoder();


    /**
     * HTTP specific fields. (remove them ?)
     */
    private long contentLength = -1;
    private MessageBytes contentTypeMB = null;
    private CharsetHolder charsetHolder = CharsetHolder.EMPTY;

    /**
     * Is there an expectation ?
     */
    private boolean expectation = false;

    private final ServerCookies serverCookies = new ServerCookies(INITIAL_COOKIE_SIZE);
    private final Parameters parameters = new Parameters();

    private final MessageBytes remoteUser = MessageBytes.newInstance();
    private boolean remoteUserNeedsAuthorization = false;
    private final MessageBytes authType = MessageBytes.newInstance();
    private final HashMap<String,Object> attributes = new HashMap<>();

    private Response response;
    private volatile ActionHook hook;

    private long bytesRead = 0;
    // Time of the request - useful to avoid repeated calls to System.currentTime
    private long startTimeNanos = -1;
    private long threadId = 0;
    private int available = 0;

    private final RequestInfo reqProcessorMX = new RequestInfo(this);

    private boolean sendfile = true;

    /**
     * Holds request body reading error exception.
     */
    private Exception errorException = null;

    /*
     * State for non-blocking output is maintained here as it is the one point easily reachable from the
     * CoyoteInputStream and the CoyoteAdapter which both need access to state.
     */
    volatile ReadListener listener;
    // Ensures listener is only fired after a call is isReady()
    private boolean fireListener = false;
    // Tracks read registration to prevent duplicate registrations
    private boolean registeredForRead = false;
    // Lock used to manage concurrent access to above flags
    private final Object nonBlockingStateLock = new Object();

    public ReadListener getReadListener() {
        return listener;
    }

    public void setReadListener(ReadListener listener) {
        if (listener == null) {
            throw new NullPointerException(sm.getString("request.nullReadListener"));
        }
        if (getReadListener() != null) {
            throw new IllegalStateException(sm.getString("request.readListenerSet"));
        }
        // Note: This class is not used for HTTP upgrade so only need to test
        // for async
        AtomicBoolean result = new AtomicBoolean(false);
        action(ActionCode.ASYNC_IS_ASYNC, result);
        if (!result.get()) {
            throw new IllegalStateException(sm.getString("request.notAsync"));
        }

        this.listener = listener;

        // The container is responsible for the first call to
        // listener.onDataAvailable(). If isReady() returns true, the container
        // needs to call listener.onDataAvailable() from a new thread. If
        // isReady() returns false, the socket will be registered for read and
        // the container will call listener.onDataAvailable() once data arrives.
        // Must call isFinished() first as a call to isReady() if the request
        // has been finished will register the socket for read interest and that
        // is not required.
        if (!isFinished() && isReady()) {
            synchronized (nonBlockingStateLock) {
                // Ensure we don't get multiple read registrations
                registeredForRead = true;
                // Need to set the fireListener flag otherwise when the
                // container tries to trigger onDataAvailable, nothing will
                // happen
                fireListener = true;
            }
            action(ActionCode.DISPATCH_READ, null);
            if (!isRequestThread()) {
                // Not on a container thread so need to execute the dispatch
                action(ActionCode.DISPATCH_EXECUTE, null);
            }
        }
    }

    public boolean isReady() {
        // Assume read is not possible
        boolean ready = false;
        synchronized (nonBlockingStateLock) {
            if (registeredForRead) {
                fireListener = true;
                return false;
            }
            ready = checkRegisterForRead();
            fireListener = !ready;
        }
        return ready;
    }

    private boolean checkRegisterForRead() {
        AtomicBoolean ready = new AtomicBoolean(false);
        synchronized (nonBlockingStateLock) {
            if (!registeredForRead) {
                action(ActionCode.NB_READ_INTEREST, ready);
                registeredForRead = !ready.get();
            }
        }
        return ready.get();
    }

    public void onDataAvailable() throws IOException {
        boolean fire = false;
        synchronized (nonBlockingStateLock) {
            registeredForRead = false;
            if (fireListener) {
                fireListener = false;
                fire = true;
            }
        }
        if (fire) {
            listener.onDataAvailable();
        }
    }


    private final AtomicBoolean allDataReadEventSent = new AtomicBoolean(false);

    public boolean sendAllDataReadEvent() {
        return allDataReadEventSent.compareAndSet(false, true);
    }


    // ------------------------------------------------------------- Properties

    public MimeHeaders getMimeHeaders() {
        return headers;
    }


    public boolean isTrailerFieldsReady() {
        AtomicBoolean result = new AtomicBoolean(false);
        action(ActionCode.IS_TRAILER_FIELDS_READY, result);
        return result.get();
    }


    public Map<String,String> getTrailerFields() {
        return trailerFields.toMap();
    }


    public MimeHeaders getMimeTrailerFields() {
        return trailerFields;
    }


    public UDecoder getURLDecoder() {
        return urlDecoder;
    }


    // -------------------- Request data --------------------

    public MessageBytes scheme() {
        return schemeMB;
    }

    public MessageBytes method() {
        return methodMB;
    }

    public MessageBytes requestURI() {
        return uriMB;
    }

    public MessageBytes decodedURI() {
        return decodedUriMB;
    }

    public MessageBytes queryString() {
        return queryMB;
    }

    public MessageBytes protocol() {
        return protoMB;
    }

    /**
     * Get the "virtual host", derived from the Host: header associated with this request.
     *
     * @return The buffer holding the server name, if any. Use isNull() to check if there is no value set.
     */
    public MessageBytes serverName() {
        return serverNameMB;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public MessageBytes remoteAddr() {
        return remoteAddrMB;
    }

    public MessageBytes peerAddr() {
        return peerAddrMB;
    }

    public MessageBytes remoteHost() {
        return remoteHostMB;
    }

    public MessageBytes localName() {
        return localNameMB;
    }

    public MessageBytes localAddr() {
        return localAddrMB;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int port) {
        this.remotePort = port;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int port) {
        this.localPort = port;
    }


    // -------------------- encoding/type --------------------

    /**
     * Get the character encoding used for this request.
     *
     * @return The value set via {@link #setCharset(Charset)} or if no call has been made to that method try to obtain
     *             if from the content type.
     *
     * @deprecated Unused. This method will be removed in Tomcat 12.
     */
    @Deprecated
    public String getCharacterEncoding() {
        if (charsetHolder.getName() == null) {
            charsetHolder = CharsetHolder.getInstance(getCharsetFromContentType(getContentType()));
        }

        return charsetHolder.getName();
    }


    /**
     * Get the character encoding used for this request.
     *
     * @return The value set via {@link #setCharset(Charset)} or if no call has been made to that method try to obtain
     *             if from the content type.
     *
     * @throws UnsupportedEncodingException If the user agent has specified an invalid character encoding
     *
     * @deprecated Unused. This method will be removed in Tomcat 12.
     */
    @Deprecated
    public Charset getCharset() throws UnsupportedEncodingException {
        if (charsetHolder.getName() == null) {
            // Populates charsetHolder
            getCharacterEncoding();
        }

        return charsetHolder.getValidatedCharset();
    }


    /**
     * Unused.
     *
     * @param charset The Charset to use for the request
     *
     * @deprecated Unused. This method will be removed in Tomcat 12.
     */
    @Deprecated
    public void setCharset(Charset charset) {
        charsetHolder = CharsetHolder.getInstance(charset);
    }


    public CharsetHolder getCharsetHolder() {
        if (charsetHolder.getName() == null) {
            charsetHolder = CharsetHolder.getInstance(getCharsetFromContentType(getContentType()));
        }
        return charsetHolder;
    }


    public void setCharsetHolder(CharsetHolder charsetHolder) {
        this.charsetHolder = charsetHolder;
    }


    public void setContentLength(long len) {
        this.contentLength = len;
    }


    public int getContentLength() {
        long length = getContentLengthLong();

        if (length < Integer.MAX_VALUE) {
            return (int) length;
        }
        return -1;
    }

    public long getContentLengthLong() {
        if (contentLength > -1) {
            return contentLength;
        }

        MessageBytes clB = headers.getUniqueValue("content-length");
        contentLength = (clB == null || clB.isNull()) ? -1 : clB.getLong();

        return contentLength;
    }

    public String getContentType() {
        contentType();
        if (contentTypeMB == null || contentTypeMB.isNull()) {
            return null;
        }
        return contentTypeMB.toStringType();
    }


    public void setContentType(String type) {
        contentTypeMB.setString(type);
    }


    public MessageBytes contentType() {
        if (contentTypeMB == null) {
            contentTypeMB = headers.getValue("content-type");
        }
        return contentTypeMB;
    }


    public void setContentType(MessageBytes mb) {
        contentTypeMB = mb;
    }


    public String getHeader(String name) {
        return headers.getHeader(name);
    }


    public void setExpectation(boolean expectation) {
        this.expectation = expectation;
    }


    public boolean hasExpectation() {
        return expectation;
    }


    // -------------------- Associated response --------------------

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
        response.setRequest(this);
    }

    protected void setHook(ActionHook hook) {
        this.hook = hook;
    }

    public void action(ActionCode actionCode, Object param) {
        if (hook != null) {
            if (param == null) {
                hook.action(actionCode, this);
            } else {
                hook.action(actionCode, param);
            }
        }
    }


    // -------------------- Cookies --------------------

    public ServerCookies getCookies() {
        return serverCookies;
    }


    // -------------------- Parameters --------------------

    public Parameters getParameters() {
        return parameters;
    }


    public void addPathParameter(String name, String value) {
        pathParameters.put(name, value);
    }

    public String getPathParameter(String name) {
        return pathParameters.get(name);
    }


    // -------------------- Other attributes --------------------
    // We can use notes for most - need to discuss what is of general interest

    public void setAttribute(String name, Object o) {
        attributes.put(name, o);
    }

    public HashMap<String,Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public MessageBytes getRemoteUser() {
        return remoteUser;
    }

    public boolean getRemoteUserNeedsAuthorization() {
        return remoteUserNeedsAuthorization;
    }

    public void setRemoteUserNeedsAuthorization(boolean remoteUserNeedsAuthorization) {
        this.remoteUserNeedsAuthorization = remoteUserNeedsAuthorization;
    }

    public MessageBytes getAuthType() {
        return authType;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public boolean getSendfile() {
        return sendfile;
    }

    public void setSendfile(boolean sendfile) {
        this.sendfile = sendfile;
    }

    public boolean isFinished() {
        AtomicBoolean result = new AtomicBoolean(false);
        action(ActionCode.REQUEST_BODY_FULLY_READ, result);
        return result.get();
    }

    public boolean getSupportsRelativeRedirects() {
        if (protocol().equals("") || protocol().equals("HTTP/1.0")) {
            return false;
        }
        return true;
    }


    // -------------------- Input Buffer --------------------

    public InputBuffer getInputBuffer() {
        return inputBuffer;
    }


    public void setInputBuffer(InputBuffer inputBuffer) {
        this.inputBuffer = inputBuffer;
    }


    /**
     * Read data from the input buffer and put it into ApplicationBufferHandler. The buffer is owned by the protocol
     * implementation - it will be reused on the next read. The Adapter must either process the data in place or copy it
     * to a separate buffer if it needs to hold it. In most cases this is done during byte-&gt;char conversions or via
     * InputStream. Unlike InputStream, this interface allows the app to process data in place, without copy.
     *
     * @param handler The destination to which to copy the data
     *
     * @return The number of bytes copied
     *
     * @throws IOException If an I/O error occurs during the copy
     */
    public int doRead(ApplicationBufferHandler handler) throws IOException {
        if (getBytesRead() == 0 && !response.isCommitted()) {
            action(ActionCode.ACK, ContinueResponseTiming.ON_REQUEST_BODY_READ);
        }

        int n = inputBuffer.doRead(handler);
        if (n > 0) {
            bytesRead += n;
        }
        return n;
    }


    // -------------------- Error tracking --------------------

    /**
     * Set the error Exception that occurred during the writing of the response processing.
     *
     * @param ex The exception that occurred
     */
    public void setErrorException(Exception ex) {
        errorException = ex;
    }


    /**
     * Get the Exception that occurred during the writing of the response.
     *
     * @return The exception that occurred
     */
    public Exception getErrorException() {
        return errorException;
    }


    public boolean isExceptionPresent() {
        return errorException != null;
    }


    // -------------------- debug --------------------

    public String getRequestId() {
        return requestId;
    }


    public String getProtocolRequestId() {
        AtomicReference<String> ref = new AtomicReference<>();
        hook.action(ActionCode.PROTOCOL_REQUEST_ID, ref);
        return ref.get();
    }


    public ServletConnection getServletConnection() {
        AtomicReference<ServletConnection> ref = new AtomicReference<>();
        hook.action(ActionCode.SERVLET_CONNECTION, ref);
        return ref.get();
    }


    @Override
    public String toString() {
        return "R( " + requestURI().toString() + ")";
    }

    public long getStartTime() {
        return System.currentTimeMillis() - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public void setStartTimeNanos(long startTimeNanos) {
        this.startTimeNanos = startTimeNanos;
    }

    public long getThreadId() {
        return threadId;
    }

    public void clearRequestThread() {
        threadId = 0;
    }

    @SuppressWarnings("deprecation")
    public void setRequestThread() {
        Thread t = Thread.currentThread();
        threadId = t.getId();
        getRequestProcessor().setWorkerThreadName(t.getName());
    }

    @SuppressWarnings("deprecation")
    public boolean isRequestThread() {
        return Thread.currentThread().getId() == threadId;
    }

    // -------------------- Per-Request "notes" --------------------


    /**
     * Used to store private data. Thread data could be used instead - but if you have the req, getting/setting a note
     * is just an array access, may be faster than ThreadLocal for very frequent operations. Example use: Catalina
     * CoyoteAdapter: ADAPTER_NOTES = 1 - stores the HttpServletRequest object ( req/res) To avoid conflicts, note in
     * the range 0 - 8 are reserved for the servlet container ( catalina connector, etc ), and values in 9 - 16 for
     * connector use. 17-31 range is not allocated or used.
     *
     * @param pos   Index to use to store the note
     * @param value The value to store at that index
     */
    public void setNote(int pos, Object value) {
        notes[pos] = value;
    }


    public Object getNote(int pos) {
        return notes[pos];
    }


    // -------------------- Recycling --------------------


    public void recycle() {
        bytesRead = 0;

        contentLength = -1;
        contentTypeMB = null;
        charsetHolder = CharsetHolder.EMPTY;
        expectation = false;
        headers.recycle();
        trailerFields.recycle();
        /*
         * Trailer fields are limited in size by bytes. The following call ensures that any request with a large number
         * of small trailer fields doesn't result in a long lasting, large array of headers inside the MimeHeader
         * instance.
         */
        trailerFields.setLimit(MimeHeaders.DEFAULT_HEADER_SIZE);
        serverNameMB.recycle();
        serverPort = -1;
        localAddrMB.recycle();
        localNameMB.recycle();
        localPort = -1;
        peerAddrMB.recycle();
        remoteAddrMB.recycle();
        remoteHostMB.recycle();
        remotePort = -1;
        available = 0;
        sendfile = true;

        // There may be multiple calls to recycle but only the first should
        // trigger a change in the request ID until a new request has been
        // started. Use startTimeNanos to detect when a request has started so a
        // subsequent call to recycle() will trigger a change in the request ID.
        if (startTimeNanos != -1) {
            requestId = Long.toHexString(requestIdGenerator.getAndIncrement());
        }

        serverCookies.recycle();
        parameters.recycle();
        pathParameters.clear();

        uriMB.recycle();
        decodedUriMB.recycle();
        queryMB.recycle();
        methodMB.recycle();
        protoMB.recycle();

        schemeMB.recycle();

        remoteUser.recycle();
        remoteUserNeedsAuthorization = false;
        authType.recycle();
        attributes.clear();

        errorException = null;

        listener = null;
        synchronized (nonBlockingStateLock) {
            fireListener = false;
            registeredForRead = false;
        }
        allDataReadEventSent.set(false);

        startTimeNanos = -1;
        threadId = 0;
    }

    // -------------------- Info --------------------
    public void updateCounters() {
        reqProcessorMX.updateCounters();
    }

    public RequestInfo getRequestProcessor() {
        return reqProcessorMX;
    }

    public long getBytesRead() {
        return bytesRead;
    }

    public boolean isProcessing() {
        return reqProcessorMX.getStage() == Constants.STAGE_SERVICE;
    }

    /**
     * Parse the character encoding from the specified content type header. If the content type is null, or there is no
     * explicit character encoding, <code>null</code> is returned.
     *
     * @param contentType a content type header
     */
    private static String getCharsetFromContentType(String contentType) {

        if (contentType == null) {
            return null;
        }

        MediaType mediaType = null;
        try {
            mediaType = MediaType.parseMediaType(new StringReader(contentType));
        } catch (IOException e) {
            // Ignore - null test below handles this
        }
        if (mediaType != null) {
            return mediaType.getCharset();
        }

        return null;
    }
}

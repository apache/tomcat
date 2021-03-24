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
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ReadListener;

import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.Parameters;
import org.apache.tomcat.util.http.ServerCookies;
import org.apache.tomcat.util.net.ApplicationBufferHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * This is a low-level, efficient representation of a server request. Most
 * fields are GC-free, expensive operations are delayed until the  user code
 * needs the information.
 *
 * Processing is delegated to modules, using a hook mechanism.
 *
 * This class is not intended for user code - it is used internally by tomcat
 * for processing the request in the most efficient way. Users ( servlets ) can
 * access the information using a facade, which provides the high-level view
 * of the request.
 *
 * Tomcat defines a number of attributes:
 * <ul>
 *   <li>"org.apache.tomcat.request" - allows access to the low-level
 *       request object in trusted applications
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

    // remote address/host
    private final MessageBytes remoteAddrMB = MessageBytes.newInstance();
    private final MessageBytes peerAddrMB = MessageBytes.newInstance();
    private final MessageBytes localNameMB = MessageBytes.newInstance();
    private final MessageBytes remoteHostMB = MessageBytes.newInstance();
    private final MessageBytes localAddrMB = MessageBytes.newInstance();

    private final MimeHeaders headers = new MimeHeaders();


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
    private Charset charset = null;
    // Retain the original, user specified character encoding so it can be
    // returned even if it is invalid
    private String characterEncoding = null;

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

    private long bytesRead=0;
    // Time of the request - useful to avoid repeated calls to System.currentTime
    private long startTime = -1;
    private int available = 0;

    private final RequestInfo reqProcessorMX=new RequestInfo(this);

    private boolean sendfile = true;

    /**
     * Holds request body reading error exception.
     */
    private Exception errorException = null;

    /*
     * State for non-blocking output is maintained here as it is the one point
     * easily reachable from the CoyoteInputStream and the CoyoteAdapter which
     * both need access to state.
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
            throw new NullPointerException(
                    sm.getString("request.nullReadListener"));
        }
        if (getReadListener() != null) {
            throw new IllegalStateException(
                    sm.getString("request.readListenerSet"));
        }
        // Note: This class is not used for HTTP upgrade so only need to test
        //       for async
        AtomicBoolean result = new AtomicBoolean(false);
        action(ActionCode.ASYNC_IS_ASYNC, result);
        if (!result.get()) {
            throw new IllegalStateException(
                    sm.getString("request.notAsync"));
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
            if (!ContainerThreadMarker.isContainerThread()) {
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
     * Get the "virtual host", derived from the Host: header associated with
     * this request.
     *
     * @return The buffer holding the server name, if any. Use isNull() to check
     *         if there is no value set.
     */
    public MessageBytes serverName() {
        return serverNameMB;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort ) {
        this.serverPort=serverPort;
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

    public int getRemotePort(){
        return remotePort;
    }

    public void setRemotePort(int port){
        this.remotePort = port;
    }

    public int getLocalPort(){
        return localPort;
    }

    public void setLocalPort(int port){
        this.localPort = port;
    }


    // -------------------- encoding/type --------------------

    /**
     * Get the character encoding used for this request.
     *
     * @return The value set via {@link #setCharacterEncoding(String)} or if no
     *         call has been made to that method try to obtain if from the
     *         content type.
     */
    public String getCharacterEncoding() {
        if (characterEncoding == null) {
            characterEncoding = getCharsetFromContentType(getContentType());
        }

        return characterEncoding;
    }


    /**
     * Get the character encoding used for this request.
     *
     * @return The value set via {@link #setCharacterEncoding(String)} or if no
     *         call has been made to that method try to obtain if from the
     *         content type.
     *
     * @throws UnsupportedEncodingException If the user agent has specified an
     *         invalid character encoding
     */
    public Charset getCharset() throws UnsupportedEncodingException {
        if (charset == null) {
            getCharacterEncoding();
            if (characterEncoding != null) {
                charset = B2CConverter.getCharset(characterEncoding);
            }
        }

        return charset;
    }


    /**
     * @param enc The new encoding
     *
     * @throws UnsupportedEncodingException If the encoding is invalid
     *
     * @deprecated This method will be removed in Tomcat 9.0.x
     */
    @Deprecated
    public void setCharacterEncoding(String enc) throws UnsupportedEncodingException {
        setCharset(B2CConverter.getCharset(enc));
    }


    public void setCharset(Charset charset) {
        this.charset = charset;
        this.characterEncoding = charset.name();
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
        if( contentLength > -1 ) {
            return contentLength;
        }

        MessageBytes clB = headers.getUniqueValue("content-length");
        contentLength = (clB == null || clB.isNull()) ? -1 : clB.getLong();

        return contentLength;
    }

    public String getContentType() {
        contentType();
        if ((contentTypeMB == null) || contentTypeMB.isNull()) {
            return null;
        }
        return contentTypeMB.toString();
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
        contentTypeMB=mb;
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

    public void setAttribute( String name, Object o ) {
        attributes.put( name, o );
    }

    public HashMap<String,Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String name ) {
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
     * Read data from the input buffer and put it into a byte chunk.
     *
     * The buffer is owned by the protocol implementation - it will be reused on
     * the next read. The Adapter must either process the data in place or copy
     * it to a separate buffer if it needs to hold it. In most cases this is
     * done during byte-&gt;char conversions or via InputStream. Unlike
     * InputStream, this interface allows the app to process data in place,
     * without copy.
     *
     * @param chunk The destination to which to copy the data
     *
     * @return The number of bytes copied
     *
     * @throws IOException If an I/O error occurs during the copy
     *
     * @deprecated Unused. Will be removed in Tomcat 9. Use
     *             {@link #doRead(ApplicationBufferHandler)}
     */
    @Deprecated
    public int doRead(ByteChunk chunk) throws IOException {
        int n = inputBuffer.doRead(chunk);
        if (n > 0) {
            bytesRead+=n;
        }
        return n;
    }


    /**
     * Read data from the input buffer and put it into ApplicationBufferHandler.
     *
     * The buffer is owned by the protocol implementation - it will be reused on
     * the next read. The Adapter must either process the data in place or copy
     * it to a separate buffer if it needs to hold it. In most cases this is
     * done during byte-&gt;char conversions or via InputStream. Unlike
     * InputStream, this interface allows the app to process data in place,
     * without copy.
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
            bytesRead+=n;
        }
        return n;
    }


    // -------------------- Error tracking --------------------

    /**
     * Set the error Exception that occurred during the writing of the response
     * processing.
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

    @Override
    public String toString() {
        return "R( " + requestURI().toString() + ")";
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    // -------------------- Per-Request "notes" --------------------


    /**
     * Used to store private data. Thread data could be used instead - but
     * if you have the req, getting/setting a note is just a array access, may
     * be faster than ThreadLocal for very frequent operations.
     *
     *  Example use:
     *   Catalina CoyoteAdapter:
     *      ADAPTER_NOTES = 1 - stores the HttpServletRequest object ( req/res)
     *
     *   To avoid conflicts, note in the range 0 - 8 are reserved for the
     *   servlet container ( catalina connector, etc ), and values in 9 - 16
     *   for connector use.
     *
     *   17-31 range is not allocated or used.
     *
     * @param pos Index to use to store the note
     * @param value The value to store at that index
     */
    public final void setNote(int pos, Object value) {
        notes[pos] = value;
    }


    public final Object getNote(int pos) {
        return notes[pos];
    }


    // -------------------- Recycling --------------------


    public void recycle() {
        bytesRead=0;

        contentLength = -1;
        contentTypeMB = null;
        charset = null;
        characterEncoding = null;
        expectation = false;
        headers.recycle();
        serverNameMB.recycle();
        serverPort=-1;
        localAddrMB.recycle();
        localNameMB.recycle();
        localPort = -1;
        peerAddrMB.recycle();
        remoteAddrMB.recycle();
        remoteHostMB.recycle();
        remotePort = -1;
        available = 0;
        sendfile = true;

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

        startTime = -1;
    }

    // -------------------- Info  --------------------
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
        return reqProcessorMX.getStage()==org.apache.coyote.Constants.STAGE_SERVICE;
    }

    /**
     * Parse the character encoding from the specified content type header.
     * If the content type is null, or there is no explicit character encoding,
     * <code>null</code> is returned.
     *
     * @param contentType a content type header
     */
    private static String getCharsetFromContentType(String contentType) {

        if (contentType == null) {
            return null;
        }
        int start = contentType.indexOf("charset=");
        if (start < 0) {
            return null;
        }
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0) {
            encoding = encoding.substring(0, end);
        }
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
            && (encoding.endsWith("\""))) {
            encoding = encoding.substring(1, encoding.length() - 1);
        }

        return encoding.trim();
    }
}

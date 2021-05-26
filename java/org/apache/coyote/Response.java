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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.servlet.WriteListener;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.MediaType;
import org.apache.tomcat.util.res.StringManager;

/**
 * Response object.
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Harish Prabandham
 * @author Hans Bergsten [hans@gefionsoftware.com]
 * @author Remy Maucherat
 */
public final class Response {

    private static final StringManager sm = StringManager.getManager(Response.class);
    private static final Log log = LogFactory.getLog(Response.class);


    // ----------------------------------------------------- Class Variables

    /**
     * Default locale as mandated by the spec.
     */
    private static final Locale DEFAULT_LOCALE = Locale.getDefault();


    // ----------------------------------------------------- Instance Variables

    /**
     * Status code.
     */
    int status = 200;


    /**
     * Status message.
     */
    String message = null;


    /**
     * Response headers.
     */
    final MimeHeaders headers = new MimeHeaders();


    private Supplier<Map<String,String>> trailerFieldsSupplier = null;

    /**
     * Associated output buffer.
     */
    OutputBuffer outputBuffer;


    /**
     * Notes.
     */
    final Object notes[] = new Object[Constants.MAX_NOTES];


    /**
     * Committed flag.
     */
    volatile boolean committed = false;


    /**
     * Action hook.
     */
    volatile ActionHook hook;


    /**
     * HTTP specific fields.
     */
    String contentType = null;
    String contentLanguage = null;
    Charset charset = null;
    // Retain the original name used to set the charset so exactly that name is
    // used in the ContentType header. Some (arguably non-specification
    // compliant) user agents are very particular
    String characterEncoding = null;
    long contentLength = -1;
    private Locale locale = DEFAULT_LOCALE;

    // General informations
    private long contentWritten = 0;
    private long commitTime = -1;

    /**
     * Holds response writing error exception.
     */
    private Exception errorException = null;

    /**
     * With the introduction of async processing and the possibility of
     * non-container threads calling sendError() tracking the current error
     * state and ensuring that the correct error page is called becomes more
     * complicated. This state attribute helps by tracking the current error
     * state and informing callers that attempt to change state if the change
     * was successful or if another thread got there first.
     *
     * <pre>
     * The state machine is very simple:
     *
     * 0 - NONE
     * 1 - NOT_REPORTED
     * 2 - REPORTED
     *
     *
     *   -->---->-- >NONE
     *   |   |        |
     *   |   |        | setError()
     *   ^   ^        |
     *   |   |       \|/
     *   |   |-<-NOT_REPORTED
     *   |            |
     *   ^            | report()
     *   |            |
     *   |           \|/
     *   |----<----REPORTED
     * </pre>
     */
    private final AtomicInteger errorState = new AtomicInteger(0);

    Request req;


    // ------------------------------------------------------------- Properties

    public Request getRequest() {
        return req;
    }

    public void setRequest( Request req ) {
        this.req=req;
    }


    public void setOutputBuffer(OutputBuffer outputBuffer) {
        this.outputBuffer = outputBuffer;
    }


    public MimeHeaders getMimeHeaders() {
        return headers;
    }


    protected void setHook(ActionHook hook) {
        this.hook = hook;
    }


    // -------------------- Per-Response "notes" --------------------

    public final void setNote(int pos, Object value) {
        notes[pos] = value;
    }


    public final Object getNote(int pos) {
        return notes[pos];
    }


    // -------------------- Actions --------------------

    public void action(ActionCode actionCode, Object param) {
        if (hook != null) {
            if (param == null) {
                hook.action(actionCode, this);
            } else {
                hook.action(actionCode, param);
            }
        }
    }


    // -------------------- State --------------------

    public int getStatus() {
        return status;
    }


    /**
     * Set the response status.
     *
     * @param status The status value to set
     */
    public void setStatus(int status) {
        this.status = status;
    }


    /**
     * Get the status message.
     *
     * @return The message associated with the current status
     */
    public String getMessage() {
        return message;
    }


    /**
     * Set the status message.
     *
     * @param message The status message to set
     */
    public void setMessage(String message) {
        this.message = message;
    }


    public boolean isCommitted() {
        return committed;
    }


    public void setCommitted(boolean v) {
        if (v && !this.committed) {
            this.commitTime = System.currentTimeMillis();
        }
        this.committed = v;
    }

    /**
     * Return the time the response was committed (based on System.currentTimeMillis).
     *
     * @return the time the response was committed
     */
    public long getCommitTime() {
        return commitTime;
    }

    // -----------------Error State --------------------

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


    /**
     * Set the error flag.
     *
     * @return <code>false</code> if the error flag was already set
     */
    public boolean setError() {
        return errorState.compareAndSet(0, 1);
    }


    /**
     * Error flag accessor.
     *
     * @return <code>true</code> if the response has encountered an error
     */
    public boolean isError() {
        return errorState.get() > 0;
    }


    public boolean isErrorReportRequired() {
        return errorState.get() == 1;
    }


    public boolean setErrorReported() {
        return errorState.compareAndSet(1, 2);
    }


    // -------------------- Methods --------------------

    public void reset() throws IllegalStateException {

        if (committed) {
            throw new IllegalStateException();
        }

        recycle();
    }


    // -------------------- Headers --------------------
    /**
     * Does the response contain the given header.
     * <br>
     * Warning: This method always returns <code>false</code> for Content-Type
     * and Content-Length.
     *
     * @param name The name of the header of interest
     *
     * @return {@code true} if the response contains the header.
     */
    public boolean containsHeader(String name) {
        return headers.getHeader(name) != null;
    }


    public void setHeader(String name, String value) {
        char cc=name.charAt(0);
        if( cc=='C' || cc=='c' ) {
            if( checkSpecialHeader(name, value) ) {
                return;
            }
        }
        headers.setValue(name).setString( value);
    }


    public void addHeader(String name, String value) {
        addHeader(name, value, null);
    }


    public void addHeader(String name, String value, Charset charset) {
        char cc=name.charAt(0);
        if( cc=='C' || cc=='c' ) {
            if( checkSpecialHeader(name, value) ) {
                return;
            }
        }
        MessageBytes mb = headers.addValue(name);
        if (charset != null) {
            mb.setCharset(charset);
        }
        mb.setString(value);
    }


    public void setTrailerFields(Supplier<Map<String, String>> supplier) {
        AtomicBoolean trailerFieldsSupported = new AtomicBoolean(false);
        action(ActionCode.IS_TRAILER_FIELDS_SUPPORTED, trailerFieldsSupported);
        if (!trailerFieldsSupported.get()) {
            throw new IllegalStateException(sm.getString("response.noTrailers.notSupported"));
        }

        this.trailerFieldsSupplier = supplier;
    }


    public Supplier<Map<String, String>> getTrailerFields() {
        return trailerFieldsSupplier;
    }


    /**
     * Set internal fields for special header names.
     * Called from set/addHeader.
     * Return true if the header is special, no need to set the header.
     */
    private boolean checkSpecialHeader( String name, String value) {
        // XXX Eliminate redundant fields !!!
        // ( both header and in special fields )
        if( name.equalsIgnoreCase( "Content-Type" ) ) {
            setContentType( value );
            return true;
        }
        if( name.equalsIgnoreCase( "Content-Length" ) ) {
            try {
                long cL=Long.parseLong( value );
                setContentLength( cL );
                return true;
            } catch( NumberFormatException ex ) {
                // Do nothing - the spec doesn't have any "throws"
                // and the user might know what they're doing
                return false;
            }
        }
        return false;
    }


    /** Signal that we're done with the headers, and body will follow.
     *  Any implementation needs to notify ContextManager, to allow
     *  interceptors to fix headers.
     */
    public void sendHeaders() {
        action(ActionCode.COMMIT, this);
        setCommitted(true);
    }


    // -------------------- I18N --------------------


    public Locale getLocale() {
        return locale;
    }

    /**
     * Called explicitly by user to set the Content-Language and the default
     * encoding.
     *
     * @param locale The locale to use for this response
     */
    public void setLocale(Locale locale) {

        if (locale == null) {
            this.locale = null;
            this.contentLanguage = null;
            return;
        }

        // Save the locale for use by getLocale()
        this.locale = locale;

        // Set the contentLanguage for header output
        contentLanguage = locale.toLanguageTag();
    }

    /**
     * Return the content language.
     *
     * @return The language code for the language currently associated with this
     *         response
     */
    public String getContentLanguage() {
        return contentLanguage;
    }


    /**
     * Overrides the character encoding used in the body of the response. This
     * method must be called prior to writing output using getWriter().
     *
     * @param characterEncoding The name of character encoding.
     *
     * @throws UnsupportedEncodingException If the specified name is not
     *         recognised
     */
    public void setCharacterEncoding(String characterEncoding) throws UnsupportedEncodingException {
        if (isCommitted()) {
            return;
        }
        if (characterEncoding == null) {
            this.charset = null;
            this.characterEncoding = null;
            return;
        }

        this.characterEncoding = characterEncoding;
        this.charset = B2CConverter.getCharset(characterEncoding);
    }


    public Charset getCharset() {
        return charset;
    }


    /**
     * @return The name of the current encoding
     */
    public String getCharacterEncoding() {
        return characterEncoding;
    }


    /**
     * Sets the content type.
     *
     * This method must preserve any response charset that may already have
     * been set via a call to response.setContentType(), response.setLocale(),
     * or response.setCharacterEncoding().
     *
     * @param type the content type
     */
    public void setContentType(String type) {

        if (type == null) {
            this.contentType = null;
            return;
        }

        MediaType m = null;
        try {
             m = MediaType.parseMediaType(new StringReader(type));
        } catch (IOException e) {
            // Ignore - null test below handles this
        }
        if (m == null) {
            // Invalid - Assume no charset and just pass through whatever
            // the user provided.
            this.contentType = type;
            return;
        }

        this.contentType = m.toStringNoCharset();

        String charsetValue = m.getCharset();

        if (charsetValue == null) {
            // No charset and we know value is valid as parser was successful
            // Pass-through user provided value in case user-agent is buggy and
            // requires specific format
            this.contentType = type;
        } else {
            // There is a charset so have to rebuild content-type without it
            this.contentType = m.toStringNoCharset();
            charsetValue = charsetValue.trim();
            if (charsetValue.length() > 0) {
                try {
                    charset = B2CConverter.getCharset(charsetValue);
                } catch (UnsupportedEncodingException e) {
                    log.warn(sm.getString("response.encoding.invalid", charsetValue), e);
                }
            }
        }
    }

    public void setContentTypeNoCharset(String type) {
        this.contentType = type;
    }

    public String getContentType() {

        String ret = contentType;

        if (ret != null && charset != null) {
            ret = ret + ";charset=" + characterEncoding;
        }

        return ret;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public int getContentLength() {
        long length = getContentLengthLong();

        if (length < Integer.MAX_VALUE) {
            return (int) length;
        }
        return -1;
    }

    public long getContentLengthLong() {
        return contentLength;
    }


    /**
     * Write a chunk of bytes.
     *
     * @param chunk The ByteBuffer to write
     *
     * @throws IOException If an I/O error occurs during the write
     */
    public void doWrite(ByteBuffer chunk) throws IOException {
        int len = chunk.remaining();
        outputBuffer.doWrite(chunk);
        contentWritten += len - chunk.remaining();
    }

    // --------------------

    public void recycle() {

        contentType = null;
        contentLanguage = null;
        locale = DEFAULT_LOCALE;
        charset = null;
        characterEncoding = null;
        contentLength = -1;
        status = 200;
        message = null;
        committed = false;
        commitTime = -1;
        errorException = null;
        errorState.set(0);
        headers.clear();
        trailerFieldsSupplier = null;
        // Servlet 3.1 non-blocking write listener
        listener = null;
        synchronized (nonBlockingStateLock) {
            fireListener = false;
            registeredForWrite = false;
        }

        // update counters
        contentWritten=0;
    }

    /**
     * Bytes written by application - i.e. before compression, chunking, etc.
     *
     * @return The total number of bytes written to the response by the
     *         application. This will not be the number of bytes written to the
     *         network which may be more or less than this value.
     */
    public long getContentWritten() {
        return contentWritten;
    }

    /**
     * Bytes written to socket - i.e. after compression, chunking, etc.
     *
     * @param flush Should any remaining bytes be flushed before returning the
     *              total? If {@code false} bytes remaining in the buffer will
     *              not be included in the returned value
     *
     * @return The total number of bytes written to the socket for this response
     */
    public long getBytesWritten(boolean flush) {
        if (flush) {
            action(ActionCode.CLIENT_FLUSH, this);
        }
        return outputBuffer.getBytesWritten();
    }

    /*
     * State for non-blocking output is maintained here as it is the one point
     * easily reachable from the CoyoteOutputStream and the Processor which both
     * need access to state.
     */
    volatile WriteListener listener;
    // Ensures listener is only fired after a call is isReady()
    private boolean fireListener = false;
    // Tracks write registration to prevent duplicate registrations
    private boolean registeredForWrite = false;
    // Lock used to manage concurrent access to above flags
    private final Object nonBlockingStateLock = new Object();

    public WriteListener getWriteListener() {
        return listener;
    }

    public void setWriteListener(WriteListener listener) {
        if (listener == null) {
            throw new NullPointerException(
                    sm.getString("response.nullWriteListener"));
        }
        if (getWriteListener() != null) {
            throw new IllegalStateException(
                    sm.getString("response.writeListenerSet"));
        }
        // Note: This class is not used for HTTP upgrade so only need to test
        //       for async
        AtomicBoolean result = new AtomicBoolean(false);
        action(ActionCode.ASYNC_IS_ASYNC, result);
        if (!result.get()) {
            throw new IllegalStateException(
                    sm.getString("response.notAsync"));
        }

        this.listener = listener;

        // The container is responsible for the first call to
        // listener.onWritePossible(). If isReady() returns true, the container
        // needs to call listener.onWritePossible() from a new thread. If
        // isReady() returns false, the socket will be registered for write and
        // the container will call listener.onWritePossible() once data can be
        // written.
        if (isReady()) {
            synchronized (nonBlockingStateLock) {
                // Ensure we don't get multiple write registrations if
                // ServletOutputStream.isReady() returns false during a call to
                // onDataAvailable()
                registeredForWrite = true;
                // Need to set the fireListener flag otherwise when the
                // container tries to trigger onWritePossible, nothing will
                // happen
                fireListener = true;
            }
            action(ActionCode.DISPATCH_WRITE, null);
            if (!ContainerThreadMarker.isContainerThread()) {
                // Not on a container thread so need to execute the dispatch
                action(ActionCode.DISPATCH_EXECUTE, null);
            }
        }
    }

    public boolean isReady() {
        if (listener == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("response.notNonBlocking"));
            }
            return false;
        }
        // Assume write is not possible
        boolean ready = false;
        synchronized (nonBlockingStateLock) {
            if (registeredForWrite) {
                fireListener = true;
                return false;
            }
            ready = checkRegisterForWrite();
            fireListener = !ready;
        }
        return ready;
    }

    public boolean checkRegisterForWrite() {
        AtomicBoolean ready = new AtomicBoolean(false);
        synchronized (nonBlockingStateLock) {
            if (!registeredForWrite) {
                action(ActionCode.NB_WRITE_INTEREST, ready);
                registeredForWrite = !ready.get();
            }
        }
        return ready.get();
    }

    public void onWritePossible() throws IOException {
        // Any buffered data left over from a previous non-blocking write is
        // written in the Processor so if this point is reached the app is able
        // to write data.
        boolean fire = false;
        synchronized (nonBlockingStateLock) {
            registeredForWrite = false;
            if (fireListener) {
                fireListener = false;
                fire = true;
            }
        }
        if (fire) {
            listener.onWritePossible();
        }
    }
}

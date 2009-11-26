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
package org.apache.tomcat.lite.servlet;


import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.tomcat.lite.http.HttpResponse;
import org.apache.tomcat.lite.http.HttpWriter;
import org.apache.tomcat.lite.http.MultiMap;
import org.apache.tomcat.lite.http.ServerCookie;
import org.apache.tomcat.lite.http.MultiMap.Entry;
import org.apache.tomcat.lite.io.CBuffer;
import org.apache.tomcat.lite.io.FastHttpDateFormat;
import org.apache.tomcat.lite.io.IOWriter;

/**
 * Wrapper object for the Coyote response.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 * @version $Revision: 793797 $ $Date: 2009-07-13 22:38:02 -0700 (Mon, 13 Jul 2009) $
 */

public class ServletResponseImpl
    implements HttpServletResponse {

    /** 
     * Format for http response header date field
     * From DateTool
     */
    public static final String HTTP_RESPONSE_DATE_HEADER =
        "EEE, dd MMM yyyy HH:mm:ss zzz";

    // ----------------------------------------------------------- Constructors
    
    
    // Package - only ServletRequestImpl should call it.
    ServletResponseImpl() {
    }


    /**
     * The date format we will use for creating date headers.
     */
    protected SimpleDateFormat format = null;


    /**
     * The associated output buffer.
     */
    protected HttpWriter outputBuffer;


    /**
     * The associated output stream.
     */
    protected ServletOutputStreamImpl outputStream;


    /**
     * The associated writer.
     */
    protected PrintWriter writer;


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
     * The error flag.
     */
    protected boolean error = false;


    /**
     * The set of Cookies associated with this Response.
     * Only used in facade - base object uses ServerCookie
     */
    protected ArrayList<Cookie> cookies = new ArrayList<Cookie>();


    /**
     * Using output stream flag.
     */
    protected boolean usingOutputStream = false;


    /**
     * Using writer flag.
     */
    protected boolean usingWriter = false;


    protected ServletRequestImpl req = null;


    protected CBuffer tmpUrlBuffer = CBuffer.newInstance();
    
    private HttpResponse resB;
    
    
    // Cached/derived information - reflected in headers
    protected static Locale DEFAULT_LOCALE = Locale.getDefault();
    
    public static final String DEFAULT_CHARACTER_ENCODING="ISO-8859-1";

    protected Locale locale = DEFAULT_LOCALE;

    // XXX 
    protected boolean commited = false;
    protected String contentType = null;
    
    /**
     * Has the charset been explicitly set.
     */
    protected boolean charsetSet = false;
    protected String characterEncoding = DEFAULT_CHARACTER_ENCODING;


    // --------------------------------------------------------- Public Methods

    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    void recycle() {

        usingOutputStream = false;
        usingWriter = false;
        appCommitted = false;
        commited = false;
        included = false;
        error = false;
        isCharacterEncodingSet = false;
        
        cookies.clear();

        outputBuffer.recycle();
        
        //resB.recycle();
    }


    // ------------------------------------------------------- Response Methods


    /**
     * Return the number of bytes actually written to the output stream.
     */
//    public int getContentCount() {
//        return outputBuffer.getBytesWritten() + outputBuffer.getCharsWritten();
//    }


    /**
     * Set the application commit flag.
     * 
     * @param appCommitted The new application committed flag value
     */
    public void setAppCommitted(boolean appCommitted) {
        this.appCommitted = appCommitted;
    }


//    /**
//     * Application commit flag accessor.
//     */
//    public boolean isAppCommitted() {
//        return (this.appCommitted || isCommitted() || isSuspended()
//                || ((getHttpResponse().getContentLength() > 0) 
//                    && (getContentCount() >= getHttpResponse().getContentLength())));
//    }


    /**
     * Return the "processing inside an include" flag.
     */
    public boolean getIncluded() {
        return included;
    }


    /**
     * Set the "processing inside an include" flag.
     *
     * @param included <code>true</code> if we are currently inside a
     *  RequestDispatcher.include(), else <code>false</code>
     */
    public void setIncluded(boolean included) {
        this.included = included;
    }


    /**
     * Return the Request with which this Response is associated.
     */
    public ServletRequestImpl getRequest() {
        return (this.req);
    }

    /**
     * Set the Request with which this Response is associated.
     *
     * @param request The new associated request
     */
    public void setRequest(ServletRequestImpl request) {
        this.req = (ServletRequestImpl) request;
    }


    /**
     * Return the output stream associated with this Response.
     */
    public OutputStream getStream() {
        return outputStream;
    }


    /**
     * Set the output stream associated with this Response.
     *
     * @param stream The new output stream
     */
    public void setStream(OutputStream stream) {
        // This method is evil
    }


    /**
     * Set the suspended flag.
     * 
     * @param suspended The new suspended flag value
     */
    public void setSuspended(boolean suspended) throws IOException {
        //coyoteResponse.setCommitted(true);
        flushBuffer();
        outputBuffer.setSuspended(suspended);
    }


    /**
     * Suspended flag accessor.
     */
    public boolean isSuspended() {
        return outputBuffer.isSuspended();
    }


    /**
     * Set the error flag.
     */
    public void setError() {
        error = true;
    }


    /**
     * Error flag accessor.
     */
    public boolean isError() {
        return error;
    }


    /**
     * Create and return a ServletOutputStream to write the content
     * associated with this Response.
     *
     * @exception IOException if an input/output error occurs
     */
    public ServletOutputStream createOutputStream() 
        throws IOException {
        // Probably useless
        return outputStream;
    }

    /**
     * Return the content type that was set or calculated for this response,
     * or <code>null</code> if no content type was set.
     */
    public String getContentType() {
        String ret = contentType;

        if (ret != null 
            && characterEncoding != null
            && charsetSet) {
            ret = ret + ";charset=" + characterEncoding;
        }

        return ret;
    }


    // ------------------------------------------------ ServletResponse Methods


    /**
     * Flush the buffer and commit this response.
     *
     * @exception IOException if an input/output error occurs
     */
    public void flushBuffer() 
        throws IOException {
        outputBuffer.flush();
    }


    /**
     * Return the actual buffer size used for this Response.
     */
    public int getBufferSize() {
        return outputBuffer.getBufferSize();
    }


    /**
     * Return the character encoding used for this Response.
     */
    public String getCharacterEncoding() {
        return characterEncoding;
    }


    /**
     * Return the servlet output stream associated with this Response.
     *
     * @exception IllegalStateException if <code>getWriter</code> has
     *  already been called for this response
     * @exception IOException if an input/output error occurs
     */
    public ServletOutputStream getOutputStream() 
        throws IOException {

        if (usingWriter)
            throw new IllegalStateException
                ("usingWriter");

        usingOutputStream = true;
        return outputStream;

    }

    public HttpWriter getOutputBuffer() {
        return outputBuffer;
      }
      

    /**
     * Return the Locale assigned to this response.
     */
    public Locale getLocale() {
        return locale;
    }


    /**
     * Return the writer associated with this Response.
     *
     * @exception IllegalStateException if <code>getOutputStream</code> has
     *  already been called for this response
     * @exception IOException if an input/output error occurs
     */
    public PrintWriter getWriter() 
        throws IOException {

        if (usingOutputStream)
            throw new IllegalStateException
                ("usingOutputStream");

        /*
         * If the response's character encoding has not been specified as
         * described in <code>getCharacterEncoding</code> (i.e., the method
         * just returns the default value <code>ISO-8859-1</code>),
         * <code>getWriter</code> updates it to <code>ISO-8859-1</code>
         * (with the effect that a subsequent call to getContentType() will
         * include a charset=ISO-8859-1 component which will also be
         * reflected in the Content-Type response header, thereby satisfying
         * the Servlet spec requirement that containers must communicate the
         * character encoding used for the servlet response's writer to the
         * client).
         */
        setCharacterEncoding(getCharacterEncoding());

        usingWriter = true;
        // Otherwise it'll be set on first write - encoding 
        // should be fixed.
        outputBuffer.checkConverter();
        
        if (writer == null) {
            writer = new ServletWriterImpl(outputBuffer);
        }
        return writer;

    }


    /**
     * Has the output of this response already been committed?
     */
    public boolean isCommitted() {
        return getHttpResponse().isCommitted();
    }

    /**
     * Clear any content written to the buffer.
     *
     * @exception IllegalStateException if this response has already
     *  been committed
     */
    public void reset() {

        if (included)
            return;     // Ignore any call from an included servlet

        if (isCommitted())
            throw new IllegalStateException("isCommitted");
        
        resB.recycle(); // reset headers, status code, message
        //req.getConnector().reset(this);
        contentType = null;
        locale = DEFAULT_LOCALE;
        characterEncoding = DEFAULT_CHARACTER_ENCODING;
        charsetSet = false;
        
        outputBuffer.reset();
    }


    /**
     * Reset the data buffer but not any status or header information.
     *
     * @exception IllegalStateException if the response has already
     *  been committed
     */
    public void resetBuffer() {

        if (isCommitted())
            throw new IllegalStateException("isCommitted");

        outputBuffer.reset();

    }


    /**
     * Set the buffer size to be used for this Response.
     *
     * @param size The new buffer size
     *
     * @exception IllegalStateException if this method is called after
     *  output has been committed for this response
     */
    public void setBufferSize(int size) {

        if (isCommitted() || outputBuffer.getWrittenSinceFlush() > 0 ||
                getHttpResponse().getBodyOutputStream().getWrittenSinceFlush() > 0)
            throw new IllegalStateException
                ("isCommitted || !isNew");

        outputBuffer.setBufferSize(size);

    }


    /**
     * Set the content length (in bytes) for this Response.
     * Ignored for writers if non-ISO-8859-1 encoding ( we could add more 
     * encodings that are constant.
     */
    public void setContentLength(int length) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;
        
        // writers can use variable-length encoding. 
        if (usingWriter && !"ISO-8859-1".equals(getCharacterEncoding())) {
            return;
        }
        getHttpResponse().setContentLength(length);

    }


    /**
     * Set the content type for this Response.
     *
     * @param type The new content type
     */
    public void setContentType(String type) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        // Ignore charset if getWriter() has already been called
        if (usingWriter) {
            if (type != null) {
                int index = type.indexOf(";");
                if (index != -1) {
                    type = type.substring(0, index);
                }
            }
        }

        getHttpResponse().setContentType(type);

        // Check to see if content type contains charset
        if (type != null) {
            int index = type.indexOf(";");
            if (index != -1) {
                int len = type.length();
                index++;
                while (index < len && Character.isSpace(type.charAt(index))) {
                    index++;
                }
                if (index+7 < len
                        && type.charAt(index) == 'c'
                        && type.charAt(index+1) == 'h'
                        && type.charAt(index+2) == 'a'
                        && type.charAt(index+3) == 'r'
                        && type.charAt(index+4) == 's'
                        && type.charAt(index+5) == 'e'
                        && type.charAt(index+6) == 't'
                        && type.charAt(index+7) == '=') {
                    isCharacterEncodingSet = true;
                }
            }
        }
    }


    /*
     * Overrides the name of the character encoding used in the body
     * of the request. This method must be called prior to reading
     * request parameters or reading input using getReader().
     *
     * @param charset String containing the name of the chararacter encoding.
     */
    public void setCharacterEncoding(String charset) {

        if (isCommitted())
            return;
        
        // Ignore any call from an included servlet
        if (included)
            return;     
        
        // Ignore any call made after the getWriter has been invoked
        // The default should be used
        if (usingWriter)
            return;

        if (isCommitted())
            return;
        if (charset == null)
            return;

        characterEncoding = charset;
        charsetSet=true;
        isCharacterEncodingSet = true;
    }

    
    
    /**
     * Set the Locale that is appropriate for this response, including
     * setting the appropriate character encoding.
     *
     * @param locale The new locale
     */
    public void setLocale(Locale locale) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        if (locale == null) {
            return;  // throw an exception?
        }

        // Save the locale for use by getLocale()
        this.locale = locale;

        // Set the contentLanguage for header output
        String contentLanguage = locale.getLanguage();
        if ((contentLanguage != null) && (contentLanguage.length() > 0)) {
            String country = locale.getCountry();
            StringBuffer value = new StringBuffer(contentLanguage);
            if ((country != null) && (country.length() > 0)) {
                value.append('-');
                value.append(country);
            }
            contentLanguage = value.toString();
        }
        resB.setHeader("Content-Language", contentLanguage);

        // Ignore any call made after the getWriter has been invoked.
        // The default should be used
        if (usingWriter)
            return;

        if (isCharacterEncodingSet) {
            return;
        }

        Locale2Charset cm = req.getContext().getCharsetMapper();
        String charset = cm.getCharset( locale );
        if ( charset != null ){
            setCharacterEncoding(charset);
        }

    }


    // --------------------------------------------------- HttpResponse Methods


    /**
     * Return an array of all cookies set for this response, or
     * a zero-length array if no cookies have been set.
     */
    public Cookie[] getCookies() {
        return ((Cookie[]) cookies.toArray(new Cookie[cookies.size()]));
    }


    /**
     * Return the value for the specified header, or <code>null</code> if this
     * header has not been set.  If more than one value was added for this
     * name, only the first is returned; use getHeaderValues() to retrieve all
     * of them.
     *
     * @param name Header name to look up
     */
    public String getHeader(String name) {
        return getHttpResponse().getHeader(name);
    }


    /**
     * Return an array of all the header names set for this response, or
     * a zero-length array if no headers have been set.
     */
    public Collection<String> getHeaderNames() {
        return getHttpResponse().getHeaderNames();
    }

    public Collection<String> getHeaders(String name) {
        return null;
    }

    /**
     * Return an array of all the header values associated with the
     * specified header name, or an zero-length array if there are no such
     * header values.
     *
     * @param name Header name to look up
     */
    public String[] getHeaderValues(String name) {
        Entry entry = getHttpResponse().getMimeHeaders().getEntry(name);
        if (entry == null) {
            return new String[] {};
        }
        int size = entry.values.size();
        String[] resultArray = new String[size];
        for (int i = 0; i < size; i++) {
            resultArray[i] = entry.values.get(i).getValue().toString();
        }
        return resultArray;

    }


    /**
     * Return the error message that was set with <code>sendError()</code>
     * for this Response.
     */
    public String getMessage() {
        return getHttpResponse().getMessage();
    }


    /**
     * Return the HTTP status code associated with this Response.
     */
    public int getStatus() {
        return getHttpResponse().getStatus();
    }


    /**
     * Reset this response, and specify the values for the HTTP status code
     * and corresponding message.
     *
     * @exception IllegalStateException if this response has already been
     *  committed
     */
    public void reset(int status, String message) {
        reset();
        setStatus(status, message);
    }


    // -------------------------------------------- HttpServletResponse Methods


    /**
     * Add the specified Cookie to those that will be included with
     * this Response.
     *
     * @param cookie Cookie to be added
     */
    public void addCookie(final Cookie cookie) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        cookies.add(cookie);

        final StringBuffer sb = new StringBuffer();
        ServerCookie.appendCookieValue
            (sb, cookie.getVersion(), cookie.getName(), cookie.getValue(),
                cookie.getPath(), cookie.getDomain(), cookie.getComment(), 
                cookie.getMaxAge(), cookie.getSecure(), false);

        // the header name is Set-Cookie for both "old" and v.1 ( RFC2109 )
        // RFC2965 is not supported by browsers and the Servlet spec
        // asks for 2109.
        addHeader("Set-Cookie", sb.toString());

    }


    /**
     * Add the specified date header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Date value to be set
     */
    public void addDateHeader(String name, long value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        if (format == null) {
            format = new SimpleDateFormat(HTTP_RESPONSE_DATE_HEADER,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        addHeader(name, FastHttpDateFormat.formatDate(value, format));

    }


    /**
     * Add the specified header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Value to be set
     */
    public void addHeader(String name, String value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        getHttpResponse().addHeader(name, value);

    }


    /**
     * Add the specified integer header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Integer value to be set
     */
    public void addIntHeader(String name, int value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        addHeader(name, "" + value);

    }


    /**
     * Has the specified header been set already in this response?
     *
     * @param name Name of the header to check
     */
    public boolean containsHeader(String name) {
        // Need special handling for Content-Type and Content-Length due to
        // special handling of these in coyoteResponse
        char cc=name.charAt(0);
        if(cc=='C' || cc=='c') {
            if(name.equalsIgnoreCase("Content-Type")) {
                // Will return null if this has not been set
                return getContentType() != null;
            }
            if(name.equalsIgnoreCase("Content-Length")) {
                // -1 means not known and is not sent to client
                return (getHttpResponse().getContentLength() != -1);
            }
        }

        return getHttpResponse().containsHeader(name);
    }


    /**
     * Encode the session identifier associated with this response
     * into the specified redirect URL, if necessary.
     *
     * @param url URL to be encoded
     */
    public String encodeRedirectURL(String url) {
        req.getHttpRequest().toAbsolute(url, tmpUrlBuffer);
        if (isEncodeable(tmpUrlBuffer.toString())) {
            return (appendSessionId(url, req.getSession().getId()));
        } else {
            return (url);
        }

    }


    /**
     * Encode the session identifier associated with this response
     * into the specified redirect URL, if necessary.
     *
     * @param url URL to be encoded
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *  <code>encodeRedirectURL()</code> instead.
     */
    public String encodeRedirectUrl(String url) {
        return (encodeRedirectURL(url));
    }


    /**
     * Encode the session identifier associated with this response
     * into the specified URL, if necessary.
     *
     * @param url URL to be encoded
     */
    public String encodeURL(String url) {
        req.getHttpRequest().toAbsolute(url, tmpUrlBuffer);        
        String absolute = tmpUrlBuffer.toString();
        if (isEncodeable(absolute)) {
            // W3c spec clearly said 
            if (url.equalsIgnoreCase("")){
                url = absolute;
            }
            return (appendSessionId(url, req.getSession().getId()));
        } else {
            return (url);
        }

    }


    /**
     * Encode the session identifier associated with this response
     * into the specified URL, if necessary.
     *
     * @param url URL to be encoded
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *  <code>encodeURL()</code> instead.
     */
    public String encodeUrl(String url) {
        return (encodeURL(url));
    }


    /**
     * Send an error response with the specified status and a
     * default message.
     *
     * @param status HTTP status code to send
     *
     * @exception IllegalStateException if this response has
     *  already been committed
     * @exception IOException if an input/output error occurs
     */
    public void sendError(int status) 
        throws IOException {
        sendError(status, null);
    }


    /**
     * Send an error response with the specified status and message.
     *
     * @param status HTTP status code to send
     * @param message Corresponding message to send
     *
     * @exception IllegalStateException if this response has
     *  already been committed
     * @exception IOException if an input/output error occurs
     */
    public void sendError(int status, String message) 
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                ("isCommitted");

        // Ignore any call from an included servlet
        if (included)
            return; 

        setError();

        getHttpResponse().setStatus(status);
        getHttpResponse().setMessage(message);

        // Clear any data content that has been buffered
        resetBuffer();

        // Cause the response to be finished (from the application perspective)
        String statusPage = req.getContext().findStatusPage(status);

        if (statusPage != null) {
            req.getContext().handleStatusPage(req, this, status, statusPage);
        } else {
            // Send a default message body.
            // TODO: maybe other mechanism to customize default.
            defaultStatusPage(status, message);
        }
        setSuspended(true);        
    }

    /** 
     * Default handler for status code != 200
     */
    void defaultStatusPage(int status, String message)
            throws IOException {
        setContentType("text/html");
        if (status > 400 && status < 600) {
            if (!getHttpResponse().isCommitted()) {
                resetBuffer();
                getOutputBuffer().write("<html><body><h1>Status: " + 
                        status + "</h1><h1>Message: " + message + 
                        "</h1></body></html>");
                getOutputBuffer().flush();
            }
        }
    }

    

    /**
     * Send a temporary redirect to the specified redirect location URL.
     *
     * @param location Location URL to redirect to
     *
     * @exception IllegalStateException if this response has
     *  already been committed
     * @exception IOException if an input/output error occurs
     */
    public void sendRedirect(String location) 
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                ("isCommitted");

        // Ignore any call from an included servlet
        if (included)
            return; 

        // Clear any data content that has been buffered
        resetBuffer();

        // Generate a temporary redirect to the specified location
        try {
            req.getHttpRequest().toAbsolute(location, tmpUrlBuffer);
            setStatus(SC_FOUND);
            resB.getMimeHeaders().setValue("Location").set(tmpUrlBuffer);
        } catch (IllegalArgumentException e) {
            setStatus(SC_NOT_FOUND);
        }

        // Cause the response to be finished (from the application perspective)
        setSuspended(true);

    }


    /**
     * Set the specified date header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Date value to be set
     */
    public void setDateHeader(String name, long value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        if (format == null) {
            format = new SimpleDateFormat(HTTP_RESPONSE_DATE_HEADER,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        setHeader(name, FastHttpDateFormat.formatDate(value, format));

    }


    /**
     * Set the specified header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Value to be set
     */
    public void setHeader(String name, String value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        getHttpResponse().setHeader(name, value);

    }


    /**
     * Set the specified integer header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Integer value to be set
     */
    public void setIntHeader(String name, int value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        setHeader(name, "" + value);

    }


    /**
     * Set the HTTP status to be returned with this response.
     *
     * @param status The new HTTP status
     */
    public void setStatus(int status) {
        setStatus(status, null);
    }


    /**
     * Set the HTTP status and message to be returned with this response.
     *
     * @param status The new HTTP status
     * @param message The associated text message
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, this method
     *  has been deprecated due to the ambiguous meaning of the message
     *  parameter.
     */
    public void setStatus(int status, String message) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        getHttpResponse().setStatus(status);
        getHttpResponse().setMessage(message);

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Return <code>true</code> if the specified URL should be encoded with
     * a session identifier.  This will be true if all of the following
     * conditions are met:
     * <ul>
     * <li>The request we are responding to asked for a valid session
     * <li>The requested session ID was not received via a cookie
     * <li>The specified URL points back to somewhere within the web
     *     application that is responding to this request
     * </ul>
     *
     * @param location Absolute URL to be validated
     */
    protected boolean isEncodeable(final String location) {

        if (location == null)
            return (false);

        // Is this an intra-document reference?
        if (location.startsWith("#"))
            return (false);

        // Are we in a valid session that is not using cookies?
        final ServletRequestImpl hreq = req;
        final HttpSession session = hreq.getSession(false);
        if (session == null)
            return (false);
        if (hreq.isRequestedSessionIdFromCookie())
            return (false);
        
        // Is this a valid absolute URL?
        URL url = null;
        try {
            url = new URL(location);
        } catch (MalformedURLException e) {
            return (false);
        }

        // Does this URL match down to (and including) the context path?
        if (!hreq.getScheme().equalsIgnoreCase(url.getProtocol()))
            return (false);
        if (!hreq.getServerName().equalsIgnoreCase(url.getHost()))
            return (false);
        int serverPort = hreq.getServerPort();
        if (serverPort == -1) {
            if ("https".equals(hreq.getScheme()))
                serverPort = 443;
            else
                serverPort = 80;
        }
        int urlPort = url.getPort();
        if (urlPort == -1) {
            if ("https".equals(url.getProtocol()))
                urlPort = 443;
            else
                urlPort = 80;
        }
        if (serverPort != urlPort)
            return (false);

        String contextPath = req.getContext().getContextPath();
        if (contextPath != null) {
            String file = url.getFile();
            if ((file == null) || !file.startsWith(contextPath))
                return (false);
            if( file.indexOf(";jsessionid=" + session.getId()) >= 0 )
                return (false);
        }

        // This URL belongs to our web application, so it is encodeable
        return (true);

    }


    
    /**
     * Return the specified URL with the specified session identifier
     * suitably encoded.
     *
     * @param url URL to be encoded with the session id
     * @param sessionId Session id to be included in the encoded URL
     */
    protected String appendSessionId(String url, String sessionId) {

        if ((url == null) || (sessionId == null))
            return (url);

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
        StringBuffer sb = new StringBuffer(path);
        if( sb.length() > 0 ) { // jsessionid can't be first.
            sb.append(";jsessionid=");
            sb.append(sessionId);
        }
        sb.append(anchor);
        sb.append(query);
        return (sb.toString());

    }

    public HttpResponse getHttpResponse() {
      return resB;
    }


    void setHttpResponse(HttpResponse resB) {
        this.resB = resB;
        outputBuffer = resB.getBodyWriter();
        outputStream = new ServletOutputStreamImpl(resB.getBodyOutputStream());
    }



}


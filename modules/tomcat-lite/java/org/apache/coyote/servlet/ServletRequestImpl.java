/*
 * Copyright 1999,2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.coyote.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;

import javax.security.auth.Subject;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import org.apache.tomcat.servlets.session.UserSessionManager;
import org.apache.tomcat.servlets.util.Enumerator;
import org.apache.tomcat.servlets.util.LocaleParser;
import org.apache.tomcat.servlets.util.RequestUtil;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.UriNormalizer;
import org.apache.tomcat.util.http.Cookies;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.HttpRequest;
import org.apache.tomcat.util.http.Parameters;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.mapper.MappingData;


/**
 *
 * Wrapper object for the Coyote request.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 */
public class ServletRequestImpl implements HttpServletRequest {

    /**
     * The request attribute under which we store the array of X509Certificate
     * objects representing the certificate chain presented by our client,
     * if any.
     */
    public static final String CERTIFICATES_ATTR =
        "javax.servlet.request.X509Certificate";

    /**
     * The request attribute under which we store the name of the cipher suite
     * being used on an SSL connection (as an object of type
     * java.lang.String).
     */
    public static final String CIPHER_SUITE_ATTR =
        "javax.servlet.request.cipher_suite";

    /**
     * Request dispatcher state.
     */
    public static final String DISPATCHER_TYPE_ATTR =
        "org.apache.catalina.core.DISPATCHER_TYPE";

    /**
     * Request dispatcher path.
     */
    public static final String DISPATCHER_REQUEST_PATH_ATTR =
        "org.apache.catalina.core.DISPATCHER_REQUEST_PATH";

    /**
     * The servlet context attribute under which we store the class path
     * for our application class loader (as an object of type String),
     * delimited with the appropriate path delimiter for this platform.
     */
    public static final String CLASS_PATH_ATTR =
        "org.apache.catalina.jsp_classpath";


    /**
     * The request attribute under which we forward a Java exception
     * (as an object of type Throwable) to an error page.
     */
    public static final String EXCEPTION_ATTR =
        "javax.servlet.error.exception";


    /**
     * The request attribute under which we forward the request URI
     * (as an object of type String) of the page on which an error occurred.
     */
    public static final String EXCEPTION_PAGE_ATTR =
        "javax.servlet.error.request_uri";


    /**
     * The request attribute under which we forward a Java exception type
     * (as an object of type Class) to an error page.
     */
    public static final String EXCEPTION_TYPE_ATTR =
        "javax.servlet.error.exception_type";


    /**
     * The request attribute under which we forward an HTTP status message
     * (as an object of type STring) to an error page.
     */
    public static final String ERROR_MESSAGE_ATTR =
        "javax.servlet.error.message";


    /**
     * The request attribute under which we expose the value of the
     * <code>&lt;jsp-file&gt;</code> value associated with this servlet,
     * if any.
     */
    public static final String JSP_FILE_ATTR =
        "org.apache.catalina.jsp_file";


    /**
     * The request attribute under which we store the key size being used for
     * this SSL connection (as an object of type java.lang.Integer).
     */
    public static final String KEY_SIZE_ATTR =
        "javax.servlet.request.key_size";

    /**
     * The request attribute under which we store the session id being used
     * for this SSL connection (as an object of type java.lang.String).
     */
    public static final String SSL_SESSION_ID_ATTR =
        "javax.servlet.request.ssl_session";

    /**
     * The request attribute under which we forward a servlet name to
     * an error page.
     */
    public static final String SERVLET_NAME_ATTR =
        "javax.servlet.error.servlet_name";


    /**
     * The name of the cookie used to pass the session identifier back
     * and forth with the client.
     */
    public static final String SESSION_COOKIE_NAME = "JSESSIONID";


    /**
     * The name of the path parameter used to pass the session identifier
     * back and forth with the client.
     */
    public static final String SESSION_PARAMETER_NAME = "jsessionid";


    /**
     * The request attribute under which we forward an HTTP status code
     * (as an object of type Integer) to an error page.
     */
    public static final String STATUS_CODE_ATTR =
        "javax.servlet.error.status_code";


    /**
     * The subject under which the AccessControlContext is running.
     */
    public static final String SUBJECT_ATTR =
        "javax.security.auth.subject";


    /**
     * The servlet context attribute under which we store a temporary
     * working directory (as an object of type File) for use by servlets
     * within this web application.
     */
    public static final String WORK_DIR_ATTR =
        "javax.servlet.context.tempdir";

    protected static final TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");


    /**
     * The default Locale if none are specified.
     */
    protected static Locale defaultLocale = Locale.getDefault();

    // ApplicationFilterFactory. What's the use ???
    private static Integer REQUEST_INTEGER = new Integer(8);

    /**
     * The match string for identifying a session ID parameter.
     */
    private static final String match = ";" + SESSION_PARAMETER_NAME + "=";

    /**
     * The set of cookies associated with this Request.
     */
    protected Cookie[] cookies = null;


    /**
     * The set of SimpleDateFormat formats to use in getDateHeader().
     *
     * Notice that because SimpleDateFormat is not thread-safe, we can't
     * declare formats[] as a static variable.
     */
    protected SimpleDateFormat formats[] = null;


    /**
     * The attributes associated with this Request, keyed by attribute name.
     */
    protected HashMap attributes = new HashMap();


    /**
     * List of read only attributes for this Request.
     */
    //private HashMap readOnlyAttributes = new HashMap();


    /**
     * The preferred Locales assocaited with this Request.
     */
    protected ArrayList locales = new ArrayList();


    /**
     * Authentication type.
     */
    protected String authType = null;

    /**
     * User principal.
     */
    protected Principal userPrincipal = null;


    /**
     * The Subject associated with the current AccessControllerContext
     */
    protected transient Subject subject = null;


    /**
     * The current dispatcher type.
     */
    protected Object dispatcherType = null;


    /**
     * The associated input buffer.
     */
    protected BodyReader inputBuffer;

    Connector connector;

    /**
     * ServletInputStream.
     */
    protected ServletInputStreamImpl inputStream;


    /**
     * Reader.
     */
    protected BufferedReader reader;


    /**
     * Using stream flag.
     */
    protected boolean usingInputStream = false;


    /**
     * Using writer flag.
     */
    protected boolean usingReader = false;


    /**
     * Session parsed flag.
     */
    protected boolean sessionParsed = false;


    /**
     * Request parameters parsed flag.
     */
    protected boolean parametersParsed = false;


    /**
     * Cookies parsed flag.
     */
    protected boolean cookiesParsed = false;


    /**
     * Secure flag.
     */
    protected boolean secure = false;


    /**
     * Hash map used in the getParametersMap method.
     */
    protected ParameterMap parameterMap = new ParameterMap();


    /**
     * The currently active session for this request.
     */
    protected HttpSession session = null;


    /**
     * The current request dispatcher path.
     */
    protected Object requestDispatcherPath = null;


    /**
     * Was the requested session ID received in a cookie?
     */
    protected boolean requestedSessionCookie = false;


    /**
     * The requested session ID (if any) for this request.
     */
    protected String requestedSessionId = null;


    /**
     * Was the requested session ID received in a URL?
     */
    protected boolean requestedSessionURL = false;


    /**
     * Parse locales.
     */
    protected boolean localesParsed = false;


    /**
     * Associated context.
     */
    protected ServletContextImpl context = null;



    // --------------------------------------------------------- Public Methods

    /**
     * Filter chain associated with the request.
     */
    protected FilterChainImpl filterChain = new FilterChainImpl();

    /**
     * Mapping data.
     */
    protected MappingData mappingData = new MappingData();


    // -------------------------------------------------------- Request Methods

    /**
     * The response with which this request is associated.
     */
    protected ServletResponseImpl response = new ServletResponseImpl();

    /**
     * URI byte to char converter (not recycled).
     */
   // protected B2CConverter URIConverter = null;

    /**
     * Associated wrapper.
     */
    protected ServletConfigImpl wrapper = null;

    /**
     * Post data buffer.
     */
    public final static int CACHED_POST_LEN = 8192;

    public  byte[] postData = null;


    private HttpRequest httpRequest;

    /** New IO/buffer model
     */
    //protected Http11Connection con;


    public ServletRequestImpl() {
        response.setRequest(this);
    }


//    /**
//     * Return the Host within which this Request is being processed.
//     */
//    public Host getHost() {
//        if (getContext() == null)
//            return null;
//        return (Host) getContext().getParent();
//        //return ((Host) mappingData.host);
//    }
//
//
//    /**
//     * Set the Host within which this Request is being processed.  This
//     * must be called as soon as the appropriate Host is identified, and
//     * before the Request is passed to a context.
//     *
//     * @param host The newly associated Host
//     */
//    public void setHost(Host host) {
//        mappingData.host = host;
//    }

    /**
     * Add a Cookie to the set of Cookies associated with this Request.
     *
     * @param cookie The new cookie
     */
    public void addCookie(Cookie cookie) {

        if (!cookiesParsed)
            parseCookies();

        int size = 0;
        if (cookies != null) {
            size = cookies.length;
        }

        Cookie[] newCookies = new Cookie[size + 1];
        for (int i = 0; i < size; i++) {
            newCookies[i] = cookies[i];
        }
        newCookies[size] = cookie;

        cookies = newCookies;

    }

    /**
     * Add a Header to the set of Headers associated with this Request.
     *
     * @param name The new header name
     * @param value The new header value
     */
    public void addHeader(String name, String value) {
        // Not used
    }

    public void setConnector(Connector c) {
        connector = c;
    }

    public Connector getConnector() {
        return connector;
    }

    /**
     * Add a Locale to the set of preferred Locales for this Request.  The
     * first added Locale will be the first one returned by getLocales().
     *
     * @param locale The new preferred Locale
     */
    public void addLocale(Locale locale) {
        locales.add(locale);
    }


    /**
     * Add a parameter name and corresponding set of values to this Request.
     * (This is used when restoring the original request on a form based
     * login).
     *
     * @param name Name of this request parameter
     * @param values Corresponding values for this request parameter
     */
    public void addParameter(String name, String values[]) {
        httpRequest.getParameters().addParameterValues(name, values);
    }

    /**
     * Clear the collection of Cookies associated with this Request.
     */
    public void clearCookies() {
        cookiesParsed = true;
        cookies = null;
    }

    /**
     * Clear the collection of Headers associated with this Request.
     */
    public void clearHeaders() {
        // Not used
    }

    /**
     * Clear the collection of Locales associated with this Request.
     */
    public void clearLocales() {
        locales.clear();
    }

    /**
     * Clear the collection of parameters associated with this Request.
     */
    public void clearParameters() {
        // Not used
    }


    /**
     * Create and return a ServletInputStream to read the content
     * associated with this Request.
     *
     * @exception IOException if an input/output error occurs
     */
    public ServletInputStream createInputStream()
        throws IOException {
        return inputStream;
    }

    /**
     * Perform whatever actions are required to flush and close the input
     * stream or reader, in a single operation.
     *
     * @exception IOException if an input/output error occurs
     */
    public void finishRequest() throws IOException {
        // The reader and input stream don't need to be closed
    }


    /**
     * Return the specified request attribute if it exists; otherwise, return
     * <code>null</code>.
     *
     * @param name Name of the request attribute to return
     */
    public Object getAttribute(String name) {

        if (name.equals(ServletRequestImpl.DISPATCHER_TYPE_ATTR)) {
            return (dispatcherType == null)
                ? REQUEST_INTEGER
                : dispatcherType;
        } else if (name.equals(ServletRequestImpl.DISPATCHER_REQUEST_PATH_ATTR)) {
            return (requestDispatcherPath == null)
                ? getRequestPathMB().toString()
                : requestDispatcherPath.toString();
        }

        Object attr=attributes.get(name);

        if(attr!=null)
            return(attr);

//        attr =  reqB.getAttribute(name);
//        if(attr != null)
//            return attr;
//        if( isSSLAttribute(name) ) {
//            reqB.action(ActionCode.ACTION_REQ_SSL_ATTRIBUTE,
//                                 reqB);
//            attr = reqB.getAttribute(ServletRequestImpl.CERTIFICATES_ATTR);
//            if( attr != null) {
//                attributes.put(ServletRequestImpl.CERTIFICATES_ATTR, attr);
//            }
//            attr = reqB.getAttribute(ServletRequestImpl.CIPHER_SUITE_ATTR);
//            if(attr != null) {
//                attributes.put(ServletRequestImpl.CIPHER_SUITE_ATTR, attr);
//            }
//            attr = reqB.getAttribute(ServletRequestImpl.KEY_SIZE_ATTR);
//            if(attr != null) {
//                attributes.put(ServletRequestImpl.KEY_SIZE_ATTR, attr);
//            }
//            attr = reqB.getAttribute(ServletRequestImpl.SSL_SESSION_ID_ATTR);
//            if(attr != null) {
//                attributes.put(ServletRequestImpl.SSL_SESSION_ID_ATTR, attr);
//            }
//            attr = attributes.get(name);
//        }
        return attr;
    }

    /**
     * Return the names of all request attributes for this Request, or an
     * empty <code>Enumeration</code> if there are none.
     */
    public Enumeration getAttributeNames() {
        if (isSecure()) {
            getAttribute(ServletRequestImpl.CERTIFICATES_ATTR);
        }
        return new Enumerator(attributes.keySet(), true);
    }


    /**
     * Return the authentication type used for this Request.
     */
    public String getAuthType() {
        return (authType);
    }


    // ------------------------------------------------- Request Public Methods


    /**
     * Return the character encoding for this Request.
     */
    public String getCharacterEncoding() {
      return (httpRequest.getCharacterEncoding());
    }


    /**
     * Return the content length for this Request.
     */
    public int getContentLength() {
        return (httpRequest.getContentLength());
    }


//    /**
//     * Return the object bound with the specified name to the internal notes
//     * for this request, or <code>null</code> if no such binding exists.
//     *
//     * @param name Name of the note to be returned
//     */
//    public Object getNote(String name) {
//        return (notes.get(name));
//    }
//
//
//    /**
//     * Return an Iterator containing the String names of all notes bindings
//     * that exist for this request.
//     */
//    public Iterator getNoteNames() {
//        return (notes.keySet().iterator());
//    }
//
//
//    /**
//     * Remove any object bound to the specified name in the internal notes
//     * for this request.
//     *
//     * @param name Name of the note to be removed
//     */
//    public void removeNote(String name) {
//        notes.remove(name);
//    }
//
//
//    /**
//     * Bind an object to a specified name in the internal notes associated
//     * with this request, replacing any existing binding for this name.
//     *
//     * @param name Name to which the object should be bound
//     * @param value Object to be bound to the specified name
//     */
//    public void setNote(String name, Object value) {
//        notes.put(name, value);
//    }
//

    /**
     * Return the content type for this Request.
     */
    public String getContentType() {
        return (httpRequest.getContentType());
    }


    /**
     * Return the Context within which this Request is being processed.
     */
    public ServletContextImpl getContext() {
        return (this.context);
    }


    /**
     * Return the portion of the request URI used to select the Context
     * of the Request.
     */
    public String getContextPath() {
        return (mappingData.contextPath.toString());
    }


    /**
     * Get the context path.
     *
     * @return the context path
     */
    public MessageBytes getContextPathMB() {
        return (mappingData.contextPath);
    }


    /**
     * Return the set of Cookies received with this Request.
     */
    public Cookie[] getCookies() {

        if (!cookiesParsed)
            parseCookies();

        return cookies;

    }


    /**
     * Return the value of the specified date header, if any; otherwise
     * return -1.
     *
     * @param name Name of the requested date header
     *
     * @exception IllegalArgumentException if the specified header value
     *  cannot be converted to a date
     */
    public long getDateHeader(String name) {

        String value = getHeader(name);
        if (value == null)
            return (-1L);
        if (formats == null) {
            formats = new SimpleDateFormat[] {
                new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
                new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
                new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
            };
            formats[0].setTimeZone(GMT_ZONE);
            formats[1].setTimeZone(GMT_ZONE);
            formats[2].setTimeZone(GMT_ZONE);
        }

        // Attempt to convert the date header in a variety of formats
        long result = FastHttpDateFormat.parseDate(value, formats);
        if (result != (-1L)) {
            return result;
        }
        throw new IllegalArgumentException(value);

    }


    /**
     * Get the decoded request URI.
     *
     * @return the URL decoded request URI
     */
    public String getDecodedRequestURI() {
        return (httpRequest.decodedURI().toString());
    }


    /**
     * Get the decoded request URI.
     *
     * @return the URL decoded request URI
     */
    public MessageBytes getDecodedRequestURIMB() {
        return (httpRequest.decodedURI());
    }


    /**
     * Get filter chain associated with the request.
     */
    public FilterChainImpl getFilterChain() {
        return (this.filterChain);
    }


    // ------------------------------------------------- ServletRequest Methods

    /**
     * Return the first value of the specified header, if any; otherwise,
     * return <code>null</code>
     *
     * @param name Name of the requested header
     */
    public String getHeader(String name) {
        return httpRequest.getHeader(name);
    }

    /**
     * Return the names of all headers received with this request.
     */
    public Enumeration getHeaderNames() {
        return httpRequest.getMimeHeaders().names();
    }


    /**
     * Return all of the values of the specified header, if any; otherwise,
     * return an empty enumeration.
     *
     * @param name Name of the requested header
     */
    public Enumeration getHeaders(String name) {
        return httpRequest.getMimeHeaders().values(name);
    }

    /**
     * Return the servlet input stream for this Request.  The default
     * implementation returns a servlet input stream created by
     * <code>createInputStream()</code>.
     *
     * @exception IllegalStateException if <code>getReader()</code> has
     *  already been called for this request
     * @exception IOException if an input/output error occurs
     */
    public ServletInputStream getInputStream() throws IOException {

        if (usingReader)
            throw new IllegalStateException
                ("usingReader");

        usingInputStream = true;
        return inputStream;

    }


    /**
     * Return the value of the specified header as an integer, or -1 if there
     * is no such header for this request.
     *
     * @param name Name of the requested header
     *
     * @exception IllegalArgumentException if the specified header value
     *  cannot be converted to an integer
     */
    public int getIntHeader(String name) {

        String value = getHeader(name);
        if (value == null) {
            return (-1);
        } else {
            return (Integer.parseInt(value));
        }

    }


    /**
     * Returns the Internet Protocol (IP) address of the interface on
     * which the request  was received.
     */
    public String getLocalAddr(){
        return httpRequest.localAddr().toString();
    }


    /**
     * Return the preferred Locale that the client will accept content in,
     * based on the value for the first <code>Accept-Language</code> header
     * that was encountered.  If the request did not specify a preferred
     * language, the server's default Locale is returned.
     */
    public Locale getLocale() {

        if (!localesParsed)
            parseLocales();

        if (locales.size() > 0) {
            return ((Locale) locales.get(0));
        } else {
            return (defaultLocale);
        }

    }


    /**
     * Return the set of preferred Locales that the client will accept
     * content in, based on the values for any <code>Accept-Language</code>
     * headers that were encountered.  If the request did not specify a
     * preferred language, the server's default Locale is returned.
     */
    public Enumeration getLocales() {

        if (!localesParsed)
            parseLocales();

        if (locales.size() > 0)
            return (new Enumerator(locales));
        ArrayList results = new ArrayList();
        results.add(defaultLocale);
        return (new Enumerator(results));

    }


    /**
     * Returns the host name of the Internet Protocol (IP) interface on
     * which the request was received.
     */
    public String getLocalName(){
        return httpRequest.localName().toString();
    }


    /**
     * Returns the Internet Protocol (IP) port number of the interface
     * on which the request was received.
     */
    public int getLocalPort(){
        return httpRequest.getLocalPort();
    }


    /**
     * Return mapping data.
     */
    public MappingData getMappingData() {
        return (mappingData);
    }



    /**
     * Return the HTTP request method used in this Request.
     */
    public String getMethod() {
        return httpRequest.method().toString();
    }


    /**
     * Return the value of the specified request parameter, if any; otherwise,
     * return <code>null</code>.  If there is more than one value defined,
     * return only the first one.
     *
     * @param name Name of the desired request parameter
     */
    public String getParameter(String name) {

        if (!parametersParsed)
            parseParameters();

        return httpRequest.getParameters().getParameter(name);

    }


    /**
     * Returns a <code>Map</code> of the parameters of this request.
     * Request parameters are extra information sent with the request.
     * For HTTP servlets, parameters are contained in the query string
     * or posted form data.
     *
     * @return A <code>Map</code> containing parameter names as keys
     *  and parameter values as map values.
     */
    public Map getParameterMap() {

        if (parameterMap.isLocked())
            return parameterMap;

        Enumeration enumeration = getParameterNames();
        while (enumeration.hasMoreElements()) {
            String name = enumeration.nextElement().toString();
            String[] values = getParameterValues(name);
            parameterMap.put(name, values);
        }

        parameterMap.setLocked(true);

        return parameterMap;

    }


    /**
     * Return the names of all defined request parameters for this request.
     */
    public Enumeration getParameterNames() {

        if (!parametersParsed)
            parseParameters();

        return httpRequest.getParameters().getParameterNames();

    }


    /**
     * Return the defined values for the specified request parameter, if any;
     * otherwise, return <code>null</code>.
     *
     * @param name Name of the desired request parameter
     */
    public String[] getParameterValues(String name) {

        if (!parametersParsed)
            parseParameters();

        return httpRequest.getParameters().getParameterValues(name);

    }


    /**
     * Return the path information associated with this Request.
     */
    public String getPathInfo() {
        return (mappingData.pathInfo.toString());
    }


    /**
     * Get the path info.
     *
     * @return the path info
     */
    public MessageBytes getPathInfoMB() {
        return (mappingData.pathInfo);
    }


    /**
     * Return the extra path information for this request, translated
     * to a real path.
     */
    public String getPathTranslated() {

        if (context == null)
            return (null);

        if (getPathInfo() == null) {
            return (null);
        } else {
            return (context.getServletContext().getRealPath(getPathInfo()));
        }

    }

    /**
     * Return the principal that has been authenticated for this Request.
     */
    public Principal getPrincipal() {
        return (userPrincipal);
    }

    /**
     * Return the protocol and version used to make this Request.
     */
    public String getProtocol() {
        return httpRequest.protocol().toString();
    }

    /**
     * Return the query string associated with this request.
     */
    public String getQueryString() {
        String queryString = httpRequest.queryString().toString();
        if (queryString == null || queryString.equals("")) {
            return (null);
        } else {
            return queryString;
        }
    }


    /**
     * Read the Reader wrapping the input stream for this Request.  The
     * default implementation wraps a <code>BufferedReader</code> around the
     * servlet input stream returned by <code>createInputStream()</code>.
     *
     * @exception IllegalStateException if <code>getInputStream()</code>
     *  has already been called for this request
     * @exception IOException if an input/output error occurs
     */
    public BufferedReader getReader() throws IOException {

        if (usingInputStream)
            throw new IllegalStateException
                ("usingInputStream");

        usingReader = true;
        //inputBuffer.setConverter();// getB2C());
        return reader;

    }

    /**
     * Cached list of encoders.
     */
    protected HashMap encoders = new HashMap();

    public static final String DEFAULT_CHARACTER_ENCODING="ISO-8859-1";

    /**
     *  Converter for the encoding associated with the request.
     *  If encoding is changed - a different encoder will be returned.
     *
     *  Encoders are cached ( per request ) - at least 8K per charset
     */
    public B2CConverter getB2C() throws IOException {
      String enc = getCharacterEncoding();
      if (enc == null) {
        enc = DEFAULT_CHARACTER_ENCODING;
      }
      B2CConverter conv =
        (B2CConverter) encoders.get(enc);
      if (conv == null) {
        conv = new B2CConverter(enc);
        encoders.put(enc, conv);
      }
      return conv;
    }

    /**
     * Return the real path of the specified virtual path.
     *
     * @param path Path to be translated
     *
     * @deprecated As of version 2.1 of the Java Servlet API, use
     *  <code>ServletContext.getRealPath()</code>.
     */
    public String getRealPath(String path) {

        if (context == null)
            return (null);
        ServletContext servletContext = context; // .getServletContext();
        if (servletContext == null)
            return (null);
        else {
            try {
                return (servletContext.getRealPath(path));
            } catch (IllegalArgumentException e) {
                return (null);
            }
        }

    }


    /**
     * Return the remote IP address making this Request.
     */
    public String getRemoteAddr() {
      if (httpRequest.remoteAddr().isNull()) {
        httpRequest.remoteAddr().setString(connector.getRemoteAddr(this));
      }
      return httpRequest.remoteAddr().toString();
    }


    /**
     * Return the remote host name making this Request.
     */
    public String getRemoteHost() {
      if (httpRequest.remoteHost().isNull()) {
        httpRequest.remoteHost().setString(connector.getRemoteHost(this));
      }
      return httpRequest.remoteHost().toString();
    }


    /**
     * Returns the Internet Protocol (IP) source port of the client
     * or last proxy that sent the request.
     */
    public int getRemotePort(){
        return httpRequest.getRemotePort();
    }


    /**
     * Return the name of the remote user that has been authenticated
     * for this Request.
     */
    public String getRemoteUser() {

        if (userPrincipal != null) {
            return (userPrincipal.getName());
        } else {
            return (null);
        }

    }


    /**
     * Return the <code>ServletRequest</code> for which this object
     * is the facade.  This method must be implemented by a subclass.
     */
    public HttpServletRequest getRequest() {
        return this;
    }

    public HttpRequest getHttpRequest() {
      return httpRequest;
    }

    public void setHttpRequest(HttpRequest req, BodyReader in) {
      this.httpRequest = req;
      inputBuffer = in;
      inputStream = new ServletInputStreamImpl(inputBuffer.asInputStream());
      reader = new ServletReaderImpl(inputBuffer);
    }

    public BodyReader getBodyReader() {
        return inputBuffer;
    }

    /**
     * Return a RequestDispatcher that wraps the resource at the specified
     * path, which may be interpreted as relative to the current request path.
     *
     * @param path Path of the resource to be wrapped
     */
    public RequestDispatcher getRequestDispatcher(String path) {

        if (context == null)
            return (null);

        // If the path is already context-relative, just pass it through
        if (path == null)
            return (null);
        else if (path.startsWith("/"))
            return (context.getRequestDispatcher(path));

        // Convert a request-relative path to a context-relative one
        String servletPath = (String) getAttribute(RequestDispatcherImpl.INCLUDE_SERVLET_PATH_ATTR);
        if (servletPath == null)
            servletPath = getServletPath();

        // Add the path info, if there is any
        String pathInfo = getPathInfo();
        String requestPath = null;

        if (pathInfo == null) {
            requestPath = servletPath;
        } else {
            requestPath = servletPath + pathInfo;
        }

        int pos = requestPath.lastIndexOf('/');
        String relative = null;
        if (pos >= 0) {
            relative = RequestUtil.normalize
                (requestPath.substring(0, pos + 1) + path);
        } else {
            relative = RequestUtil.normalize(requestPath + path);
        }

        return (context.getRequestDispatcher(relative));

    }


    /**
     * Return the session identifier included in this request, if any.
     */
    public String getRequestedSessionId() {
        return (requestedSessionId);
    }


    // ---------------------------------------------------- HttpRequest Methods


    /**
     * Get the request path.
     *
     * @return the request path
     */
    public MessageBytes getRequestPathMB() {
        return (mappingData.requestPath);
    }


    /**
     * Return the request URI for this request.
     */
    public String getRequestURI() {
        return httpRequest.requestURI().toString();
    }

    /**
     */
    public void setRequestURI(String uri) {
      httpRequest.decodedURI().setString(uri);
      try {
        UriNormalizer.decodeRequest(httpRequest.decodedURI(),
                httpRequest.requestURI(), httpRequest.getURLDecoder());
      } catch(IOException ioe) {
        ioe.printStackTrace();
        return;
      }
    }



    /**
     * Reconstructs the URL the client used to make the request.
     * The returned URL contains a protocol, server name, port
     * number, and server path, but it does not include query
     * string parameters.
     * <p>
     * Because this method returns a <code>StringBuffer</code>,
     * not a <code>String</code>, you can modify the URL easily,
     * for example, to append query parameters.
     * <p>
     * This method is useful for creating redirect messages and
     * for reporting errors.
     *
     * @return A <code>StringBuffer</code> object containing the
     *  reconstructed URL
     */
    public StringBuffer getRequestURL() {

        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0)
            port = 80; // Work around java.net.URL bug

        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());

        return (url);

    }


    /**
     * Return the Response with which this Request is associated.
     */
    public ServletResponseImpl getResponse() {
        return (this.response);
    }


    /**
     * Return the scheme used to make this Request.
     */
    public String getScheme() {
        String scheme = httpRequest.scheme().toString();
        if (scheme == null) {
            scheme = (isSecure() ? "https" : "http");
        }
        return scheme;
    }


    /**
     * Return the server name responding to this Request.
     */
    public String getServerName() {
        return (httpRequest.serverName().toString());
    }


    /**
     * Return the server port responding to this Request.
     */
    public int getServerPort() {
        return (httpRequest.getServerPort());
    }


    /**
     * Return the portion of the request URI used to select the servlet
     * that will process this request.
     */
    public String getServletPath() {
        return (mappingData.wrapperPath.toString());
    }


    /**
     * Get the servlet path.
     *
     * @return the servlet path
     */
    public MessageBytes getServletPathMB() {
        return (mappingData.wrapperPath);
    }



    /**
     * Return the input stream associated with this Request.
     */
    public InputStream getStream() {
        return inputStream;
    }


    /**
     * Return the principal that has been authenticated for this Request.
     */
    public Principal getUserPrincipal() {
        return userPrincipal;
    }


    /**
     * Return the Wrapper within which this Request is being processed.
     */
    public ServletConfigImpl getWrapper() {
        return (this.wrapper);
    }


    /**
     * Return <code>true</code> if the session identifier included in this
     * request came from a cookie.
     */
    public boolean isRequestedSessionIdFromCookie() {

        if (requestedSessionId != null)
            return (requestedSessionCookie);
        else
            return (false);

    }


    /**
     * Return <code>true</code> if the session identifier included in this
     * request came from the request URI.
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *  <code>isRequestedSessionIdFromURL()</code> instead.
     */
    public boolean isRequestedSessionIdFromUrl() {
        return (isRequestedSessionIdFromURL());
    }


    /**
     * Return <code>true</code> if the session identifier included in this
     * request came from the request URI.
     */
    public boolean isRequestedSessionIdFromURL() {

        if (requestedSessionId != null)
            return (requestedSessionURL);
        else
            return (false);

    }


    /**
     * Return <code>true</code> if the session identifier included in this
     * request identifies a valid session.
     */
    public boolean isRequestedSessionIdValid() {

        if (requestedSessionId == null)
            return (false);
        if (context == null)
            return (false);
        UserSessionManager manager = context.getManager();
        if (manager == null)
            return (false);
        HttpSession session = null;
        try {
            session = manager.findSession(requestedSessionId);
        } catch (IOException e) {
            session = null;
        }
        if ((session != null) && manager.isValid(session))
            return (true);
        else
            return (false);

    }

    /**
     * Was this request received on a secure connection?
     */
    public boolean isSecure() {
        return (secure);
    }


    /**
     * Return <code>true</code> if the authenticated user principal
     * possesses the specified role name.
     *
     * @param role Role name to be validated
     */
    public boolean isUserInRole(String role) {
        // Have we got an authenticated principal at all?
        Principal userPrincipal = getPrincipal();
        if (userPrincipal == null)
            return (false);

        // Identify the Realm we will use for checking role assignmenets
        if (context == null)
            return (false);

        // Check for a role alias defined in a <security-role-ref> element
        if (wrapper != null) {
            String realRole = wrapper.getSecurityRoleRef(role);
            if (realRole != null) {
                role = realRole;
            }
        }

        if (role.equals(userPrincipal.getName())) {
            return true;
        }

        // TODO: check !!!!
        // Check for a role defined directly as a <security-role>
        return false;
    }

    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    public void recycle() {

        wrapper = null;

        dispatcherType = null;
        requestDispatcherPath = null;

        authType = null;
        inputBuffer.recycle();
        usingInputStream = false;
        usingReader = false;
        userPrincipal = null;
        subject = null;
        sessionParsed = false;
        parametersParsed = false;
        cookiesParsed = false;
        locales.clear();
        localesParsed = false;
        secure = false;

        attributes.clear();
        //notes.clear();
        cookies = null;

        if (session != null) {
            context.getManager().endAccess(session);
        }
        context = null;
        session = null;
        requestedSessionCookie = false;
        requestedSessionId = null;
        requestedSessionURL = false;

        parameterMap.setLocked(false);
        parameterMap.clear();

        mappingData.recycle();
        httpRequest.recycle();
    }


    /**
     * Remove the specified request attribute if it exists.
     *
     * @param name Name of the request attribute to remove
     */
    public void removeAttribute(String name) {
        Object value = null;
        boolean found = false;

        // Remove the specified attribute
        // Check for read only attribute
        // requests are per thread so synchronization unnecessary
//        if (readOnlyAttributes.containsKey(name)) {
//            return;
//        }
        found = attributes.containsKey(name);
        if (found) {
            value = attributes.get(name);
            attributes.remove(name);
        } else {
            return;
        }

        // Notify interested application event listeners
        List listeners = context.getListeners();
        if (listeners.size() == 0)
            return;
        ServletRequestAttributeEvent event = null;
        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof ServletRequestAttributeListener))
                continue;
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners.get(i);
            try {
                if (event == null) {
                    event =
                        new ServletRequestAttributeEvent(context.getServletContext(),
                            getRequest(), name, value);
                }
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                context.getLogger().log(Level.WARNING, "ServletRequestAttributeListner.attributeRemoved()", t);
                // Error valve will pick this execption up and display it to user
                attributes.put( ServletRequestImpl.EXCEPTION_ATTR, t );
            }
        }
    }


    /**
     * Set the specified request attribute to the specified value.
     *
     * @param name Name of the request attribute to set
     * @param value The associated value
     */
    public void setAttribute(String name, Object value) {

        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                ("setAttribute() name == null");

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        if (name.equals(ServletRequestImpl.DISPATCHER_TYPE_ATTR)) {
            dispatcherType = value;
            return;
        } else if (name.equals(ServletRequestImpl.DISPATCHER_REQUEST_PATH_ATTR)) {
            requestDispatcherPath = value;
            return;
        }

        Object oldValue = null;
        boolean replaced = false;

        // Add or replace the specified attribute
        // Check for read only attribute
        // requests are per thread so synchronization unnecessary
//        if (readOnlyAttributes.containsKey(name)) {
//            return;
//        }

        oldValue = attributes.put(name, value);
        if (oldValue != null) {
            replaced = true;
        }

        // Pass special attributes to the native layer
//        if (name.startsWith("org.apache.tomcat.")) {
//            reqB.setAttribute(name, value);
//        }
//
        // Notify interested application event listeners
        List listeners = context.getListeners();
        if (listeners.size() == 0)
            return;
        ServletRequestAttributeEvent event = null;

        for (int i = 0; i < listeners.size(); i++) {
            if (!(listeners.get(i) instanceof ServletRequestAttributeListener))
                continue;
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners.get(i);
            try {
                if (event == null) {
                    if (replaced)
                        event =
                            new ServletRequestAttributeEvent(context.getServletContext(),
                                                             getRequest(), name, oldValue);
                    else
                        event =
                            new ServletRequestAttributeEvent(context.getServletContext(),
                                                             getRequest(), name, value);
                }
                if (replaced) {
                    listener.attributeReplaced(event);
                } else {
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                context.getLogger().log(Level.WARNING, "ServletRequestAttributeListener error", t);
                // Error valve will pick this execption up and display it to user
                attributes.put( ServletRequestImpl.EXCEPTION_ATTR, t );
            }
        }
    }


    // --------------------------------------------- HttpServletRequest Methods


    /**
     * Set the authentication type used for this request, if any; otherwise
     * set the type to <code>null</code>.  Typical values are "BASIC",
     * "DIGEST", or "SSL".
     *
     * @param type The authentication type used
     */
    public void setAuthType(String type) {
        this.authType = type;
    }


    /**
     * Overrides the name of the character encoding used in the body of
     * this request.  This method must be called prior to reading request
     * parameters or reading input using <code>getReader()</code>.
     *
     * @param enc The character encoding to be used
     *
     * @exception UnsupportedEncodingException if the specified encoding
     *  is not supported
     *
     * @since Servlet 2.3
     */
    public void setCharacterEncoding(String enc)
        throws UnsupportedEncodingException {

        // Ensure that the specified encoding is valid
        byte buffer[] = new byte[1];
        buffer[0] = (byte) 'a';
        String dummy = new String(buffer, enc);

        // Save the validated encoding
        httpRequest.setCharacterEncoding(enc);

    }


//    public void setConnection(Http11Connection con) {
//        this.con = con;
//        //reqB.messageWriter.setConnection(con);
//        //inputBuffer.setRequest(req);
//    }


    /**
     * Set the content length associated with this Request.
     *
     * @param length The new content length
     */
    public void setContentLength(int length) {
        // Not used
    }


    /**
     * Set the content type (and optionally the character encoding)
     * associated with this Request.  For example,
     * <code>text/html; charset=ISO-8859-4</code>.
     *
     * @param type The new content type
     */
    public void setContentType(String type) {
        // Not used
    }


    /**
     * Set the Context within which this Request is being processed.  This
     * must be called as soon as the appropriate Context is identified, because
     * it identifies the value to be returned by <code>getContextPath()</code>,
     * and thus enables parsing of the request URI.
     *
     * @param context The newly associated Context
     */
    public void setContext(ServletContextImpl context) {
        this.context = context;
    }


    /**
     * Set the context path for this Request.  This will normally be called
     * when the associated Context is mapping the Request to a particular
     * Wrapper.
     *
     * @param path The context path
     */
    public void setContextPath(String path) {

        if (path == null) {
            mappingData.contextPath.setString("");
        } else {
            mappingData.contextPath.setString(path);
        }

    }


    /**
     * Set the set of cookies recieved with this Request.
     */
    public void setCookies(Cookie[] cookies) {

        this.cookies = cookies;

    }


    /**
     * Set the decoded request URI.
     *
     * @param uri The decoded request URI
     */
    public void setDecodedRequestURI(String uri) {
        // Not used
    }


    /**
     * Set the HTTP request method used for this Request.
     *
     * @param method The request method
     */
    public void setMethod(String method) {
      httpRequest.method().setString(method);
    }


    /**
     * Set the path information for this Request.  This will normally be called
     * when the associated Context is mapping the Request to a particular
     * Wrapper.
     *
     * @param path The path information
     */
    public void setPathInfo(String path) {
        mappingData.pathInfo.setString(path);
    }


    /**
     * Set the protocol name and version associated with this Request.
     *
     * @param protocol Protocol name and version
     */
    public void setProtocol(String protocol) {
        // Not used
    }


    /**
     * Set the query string for this Request.  This will normally be called
     * by the HTTP Connector, when it parses the request headers.
     *
     * @param query The query string
     */
    public void setQueryString(String query) {
        // Not used
    }


    /**
     * Set the IP address of the remote client associated with this Request.
     *
     * @param remoteAddr The remote IP address
     */
    public void setRemoteAddr(String remoteAddr) {
        // Not used
    }


    /**
     * Set the fully qualified name of the remote client associated with this
     * Request.
     *
     * @param remoteHost The remote host name
     */
    public void setRemoteHost(String remoteHost) {
        // Not used
    }


    /**
     * Set a flag indicating whether or not the requested session ID for this
     * request came in through a cookie.  This is normally called by the
     * HTTP Connector, when it parses the request headers.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionCookie(boolean flag) {

        this.requestedSessionCookie = flag;

    }


    /**
     * Set the requested session ID for this request.  This is normally called
     * by the HTTP Connector, when it parses the request headers.
     *
     * @param id The new session id
     */
    public void setRequestedSessionId(String id) {

        this.requestedSessionId = id;

    }


    /**
     * Set a flag indicating whether or not the requested session ID for this
     * request came in through a URL.  This is normally called by the
     * HTTP Connector, when it parses the request headers.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionURL(boolean flag) {

        this.requestedSessionURL = flag;

    }


    /**
     * Set the name of the scheme associated with this request.  Typical values
     * are <code>http</code>, <code>https</code>, and <code>ftp</code>.
     *
     * @param scheme The scheme
     */
    public void setScheme(String scheme) {
        // Not used
    }


    /**
     * Set the value to be returned by <code>isSecure()</code>
     * for this Request.
     *
     * @param secure The new isSecure value
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
    }


    /**
     * Set the name of the server (virtual host) to process this request.
     *
     * @param name The server name
     */
    public void setServerName(String name) {
        httpRequest.serverName().setString(name);
    }


    /**
     * Set the port number of the server to process this request.
     *
     * @param port The server port
     */
    public void setServerPort(int port) {
        httpRequest.setServerPort(port);
    }


    /**
     * Set the servlet path for this Request.  This will normally be called
     * when the associated Context is mapping the Request to a particular
     * Wrapper.
     *
     * @param path The servlet path
     */
    public void setServletPath(String path) {
        if (path != null)
            mappingData.wrapperPath.setString(path);
    }


    /**
     * Set the input stream associated with this Request.
     *
     * @param stream The new input stream
     */
    public void setStream(InputStream stream) {
        // Ignore
    }


    /**
     * Set the Principal who has been authenticated for this Request.  This
     * value is also used to calculate the value to be returned by the
     * <code>getRemoteUser()</code> method.
     *
     * @param principal The user Principal
     */
    public void setUserPrincipal(Principal principal) {

        if (System.getSecurityManager() != null){
            HttpSession session = getSession(false);
            if ( (subject != null) &&
                 (!subject.getPrincipals().contains(principal)) ){
                subject.getPrincipals().add(principal);
            } else if (session != null &&
                        session.getAttribute(ServletRequestImpl.SUBJECT_ATTR) == null) {
                subject = new Subject();
                subject.getPrincipals().add(principal);
            }
            if (session != null){
                session.setAttribute(ServletRequestImpl.SUBJECT_ATTR, subject);
            }
        }

        this.userPrincipal = principal;
    }


    /**
     * Set the Wrapper within which this Request is being processed.  This
     * must be called as soon as the appropriate Wrapper is identified, and
     * before the Request is ultimately passed to an application servlet.
     * @param wrapper The newly associated Wrapper
     */
    public void setWrapper(ServletConfigImpl wrapper) {
        this.wrapper = wrapper;
    }


    public String toString() {
        return httpRequest.requestURI().toString();
    }


    /**
     * Configures the given JSESSIONID cookie.
     *
     * @param cookie The JSESSIONID cookie to be configured
     */
    protected void configureSessionCookie(Cookie cookie) {
        cookie.setMaxAge(-1);
        String contextPath = null;
        if (//!connector.getEmptySessionPath() &&
                (getContext() != null)) {
            contextPath = getContext().getEncodedPath();
        }
        if ((contextPath != null) && (contextPath.length() > 0)) {
            cookie.setPath(contextPath);
        } else {
            cookie.setPath("/");
        }
        if (isSecure()) {
            cookie.setSecure(true);
        }
    }


    /**
     * Return the session associated with this Request, creating one
     * if necessary.
     */
    public HttpSession getSession() {
        return getSession(true);
    }


    public HttpSession getSession(boolean create) {

        // There cannot be a session if no context has been assigned yet
        if (context == null)
            return (null);


        // Return the requested session if it exists and is valid
        UserSessionManager manager = null;
        if (context != null)
            manager = context.getManager();
        if (manager == null)
            return (null);      // Sessions are not supported

        // Return the current session if it exists and is valid
        if ((session != null) && !manager.isValid(session))
            session = null;
        if (session != null)
            return (session);


        if (requestedSessionId != null) {
            try {
                session = manager.findSession(requestedSessionId);
            } catch (IOException e) {
                session = null;
            }
            if ((session != null) && !manager.isValid(session))
                session = null;
            if (session != null) {
                manager.access(session);
                return (session);
            }
        }

        // Create a new session if requested and the response is not committed
        if (!create)
            return (null);
        if ((context != null) && (response != null) &&
            context.getCookies() &&
            getResponse().isCommitted()) {
            throw new IllegalStateException
              ("isCommited()");
        }

        // Attempt to reuse session id if one was submitted in a cookie
        // Do not reuse the session id if it is from a URL, to prevent possible
        // phishing attacks
        if (// connector.getEmptySessionPath() &&
                isRequestedSessionIdFromCookie()) {
            session = manager.createSession(getRequestedSessionId());
        } else {
            session = manager.createSession(null);
        }

        // Creating a new session cookie based on that session
        if ((session != null) && (getContext() != null)
               && getContext().getCookies()) {
            Cookie cookie = new Cookie(ServletRequestImpl.SESSION_COOKIE_NAME,
                                       session.getId());
            configureSessionCookie(cookie);
            response.addCookie(cookie);
        }

        if (session != null) {
            manager.access(session);
            return (session);
        } else {
            return (null);
        }

    }


    /**
     * Return the URI converter.
     */
//    protected B2CConverter getURIConverter() {
//        return URIConverter;
//    }
//

    // ------------------------------------------------------ Protected Methods


    /**
     * Parse cookies.
     */
    protected void parseCookies() {

        cookiesParsed = true;

        Cookies serverCookies = httpRequest.getCookies();
        int count = serverCookies.getCookieCount();
        if (count <= 0)
            return;

        cookies = new Cookie[count];

        int idx=0;
        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            try {
                Cookie cookie = new Cookie(scookie.getName().toString(),
                                           scookie.getValue().toString());
                cookie.setPath(scookie.getPath().toString());
                cookie.setVersion(scookie.getVersion());
                String domain = scookie.getDomain().toString();
                if (domain != null) {
                    cookie.setDomain(scookie.getDomain().toString());
                }
                cookies[idx++] = cookie;
            } catch(IllegalArgumentException e) {
                // Ignore bad cookie
            }
        }
        if( idx < count ) {
            Cookie [] ncookies = new Cookie[idx];
            System.arraycopy(cookies, 0, ncookies, 0, idx);
            cookies = ncookies;
        }

    }

    /**
     * Parse request locales.
     */
    protected void parseLocales() {

        localesParsed = true;

        Enumeration values = getHeaders("accept-language");

        while (values.hasMoreElements()) {
            String value = values.nextElement().toString();
            parseLocalesHeader(value);
        }

    }

    /**
     * Parse accept-language header value.
     */
    protected void parseLocalesHeader(String value) {

      TreeMap locales = new LocaleParser().parseLocale(value);
      // Process the quality values in highest->lowest order (due to
      // negating the Double value when creating the key)
      Iterator keys = locales.keySet().iterator();
      while (keys.hasNext()) {
        Double key = (Double) keys.next();
        ArrayList list = (ArrayList) locales.get(key);
        Iterator values = list.iterator();
        while (values.hasNext()) {
          Locale locale = (Locale) values.next();
          addLocale(locale);
        }
      }

    }

    /**
     * Parse request parameters.
     */
    protected void parseParameters() {

        parametersParsed = true;

        Parameters parameters = httpRequest.getParameters();

        // getCharacterEncoding() may have been overridden to search for
        // hidden form field containing request encoding
        String enc = getCharacterEncoding();

//        boolean useBodyEncodingForURI = connector.getUseBodyEncodingForURI();
        if (enc != null) {
            parameters.setEncoding(enc);
//            if (useBodyEncodingForURI) {
//                parameters.setQueryStringEncoding(enc);
//            }
        } else {
            parameters.setEncoding(DEFAULT_CHARACTER_ENCODING);
//            if (useBodyEncodingForURI) {
//                parameters.setQueryStringEncoding
//                    (DEFAULT_CHARACTER_ENCODING);
//            }
        }

        parameters.handleQueryParameters();

        if (usingInputStream || usingReader)
            return;

        if (!getMethod().equalsIgnoreCase("POST"))
            return;

        String contentType = getContentType();
        if (contentType == null)
            contentType = "";
        int semicolon = contentType.indexOf(';');
        if (semicolon >= 0) {
            contentType = contentType.substring(0, semicolon).trim();
        } else {
            contentType = contentType.trim();
        }
        if (!("application/x-www-form-urlencoded".equals(contentType)))
            return;

        int len = getContentLength();

        if (len > 0) {
//            int maxPostSize = connector.getMaxPostSize();
//            if ((maxPostSize > 0) && (len > maxPostSize)) {
//                context.getLogger().info
//                    (sm.getString("reqB.postTooLarge"));
//                throw new IllegalStateException("Post too large");
//            }
            try {
                byte[] formData = null;
                if (len < CACHED_POST_LEN) {
                    if (postData == null)
                        postData = new byte[CACHED_POST_LEN];
                    formData = postData;
                } else {
                    formData = new byte[len];
                }
                int actualLen = readPostBody(formData, len);
                if (actualLen == len) {
                    parameters.processParameters(formData, 0, len);
                }
            } catch (Throwable t) {
                ; // Ignore
            }
        }

    }


    /**
     * Parse session id in URL. Done in request for performance.
     * TODO: should be done in manager
     */
    protected void parseSessionCookiesId() {
        String sessionCookieName = context.getSessionCookieName();

        // Parse session id from cookies
        Cookies serverCookies = httpRequest.getCookies();
        int count = serverCookies.getCookieCount();
        if (count <= 0)
            return;

        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            if (scookie.getName().equals(sessionCookieName)) {
                // Override anything requested in the URL
                if (!isRequestedSessionIdFromCookie()) {
                    // Accept only the first session id cookie
                    //scookie.getValue().convertToAscii();

                    setRequestedSessionId
                        (scookie.getValue().toString());
                    setRequestedSessionCookie(true);
                    setRequestedSessionURL(false);
                } else {
                    if (!isRequestedSessionIdValid()) {
                        // Replace the session id until one is valid
                        //scookie.getValue().convertToAscii();
                        setRequestedSessionId
                            (scookie.getValue().toString());
                    }
                }
            }
        }
    }

    /**
     * Parse session id in URL.
     */
    protected void parseSessionId() {
        ServletRequestImpl request = this;
        ByteChunk uriBC = httpRequest.requestURI().getByteChunk();
        int semicolon = uriBC.indexOf(match, 0, match.length(), 0);

        if (semicolon > 0) {

            // Parse session ID, and extract it from the decoded request URI
            int start = uriBC.getStart();
            int end = uriBC.getEnd();

            int sessionIdStart = semicolon + match.length();
            int semicolon2 = uriBC.indexOf(';', sessionIdStart);
            if (semicolon2 >= 0) {
                request.setRequestedSessionId
                    (new String(uriBC.getBuffer(), start + sessionIdStart,
                            semicolon2 - sessionIdStart));
                // Extract session ID from request URI
                byte[] buf = uriBC.getBuffer();
                for (int i = 0; i < end - start - semicolon2; i++) {
                    buf[start + semicolon + i]
                        = buf[start + i + semicolon2];
                }
                uriBC.setBytes(buf, start, end - start - semicolon2 + semicolon);
            } else {
                request.setRequestedSessionId
                    (new String(uriBC.getBuffer(), start + sessionIdStart,
                            (end - start) - sessionIdStart));
                uriBC.setEnd(start + semicolon);
            }
            request.setRequestedSessionURL(true);

        } else {
            request.setRequestedSessionId(null);
            request.setRequestedSessionURL(false);
        }

    }


    /**
     * Read post body in an array.
     */
    protected int readPostBody(byte body[], int len)
        throws IOException {

        int offset = 0;
        do {
            int inputLen = getStream().read(body, offset, len - offset);
            if (inputLen <= 0) {
                return offset;
            }
            offset += inputLen;
        } while ((len - offset) > 0);
        return len;

    }


    /**
     * Test if a given name is one of the special Servlet-spec SSL attributes.
     */
    static boolean isSSLAttribute(String name) {
        return ServletRequestImpl.CERTIFICATES_ATTR.equals(name) ||
            ServletRequestImpl.CIPHER_SUITE_ATTR.equals(name) ||
            ServletRequestImpl.KEY_SIZE_ATTR.equals(name)  ||
            ServletRequestImpl.SSL_SESSION_ID_ATTR.equals(name);
    }


    public void addAsyncListener(AsyncListener listener) {
    }



    public void addAsyncListener(AsyncListener listener,
                                 ServletRequest servletRequest,
                                 ServletResponse servletResponse) {
    }



    @Override
    public AsyncContext getAsyncContext() {
        return null;
    }



    @Override
    public ServletContext getServletContext() {
        return null;
    }



    @Override
    public boolean isAsyncStarted() {
        return false;
    }



    @Override
    public boolean isAsyncSupported() {
        return false;
    }


    public void setAsyncTimeout(long timeout) {
    }



   @Override
    public AsyncContext startAsync() throws IllegalStateException {
        return null;
    }


   @Override
    public AsyncContext startAsync(ServletRequest servletRequest,
                                   ServletResponse servletResponse)
            throws IllegalStateException {
        return null;
    }



   @Override
    public boolean authenticate(HttpServletResponse response)
            throws IOException, ServletException {
        return false;
    }



   @Override
    public Part getPart(String name) {
        return null;
    }



   @Override
    public void login(String username, String password) throws ServletException {
    }



   @Override
    public void logout() throws ServletException {
    }



    public long getAsyncTimeout() {
        return 0;
    }



   @Override
    public DispatcherType getDispatcherType() {
        return null;
    }


   @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return null;
    }


}

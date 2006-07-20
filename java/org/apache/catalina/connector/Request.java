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


package org.apache.catalina.connector;


import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.StringCache;
import org.apache.tomcat.util.http.Cookies;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.Parameters;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.mapper.MappingData;

import org.apache.coyote.ActionCode;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.ApplicationFilterFactory;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.ParameterMap;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.util.StringParser;


/**
 * Wrapper object for the Coyote request.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 * @version $Revision: 382659 $ $Date: 2006-03-03 06:01:16 +0100 (ven., 03 mars 2006) $
 */

public class Request
    implements HttpServletRequest {


    // ----------------------------------------------------------- Constructors


    static {
        // Ensure that classes are loaded for SM
        new StringCache.ByteEntry();
        new StringCache.CharEntry();
    }

    public Request() {

        formats[0].setTimeZone(GMT_ZONE);
        formats[1].setTimeZone(GMT_ZONE);
        formats[2].setTimeZone(GMT_ZONE);

    }


    // ------------------------------------------------------------- Properties


    /**
     * Coyote request.
     */
    protected org.apache.coyote.Request coyoteRequest;

    /**
     * Set the Coyote request.
     * 
     * @param coyoteRequest The Coyote request
     */
    public void setCoyoteRequest(org.apache.coyote.Request coyoteRequest) {
        this.coyoteRequest = coyoteRequest;
        inputBuffer.setRequest(coyoteRequest);
    }

    /**
     * Get the Coyote request.
     */
    public org.apache.coyote.Request getCoyoteRequest() {
        return (this.coyoteRequest);
    }


    // ----------------------------------------------------- Variables


    protected static final TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");


    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);


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
    protected SimpleDateFormat formats[] = {
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    };


    /**
     * The default Locale if none are specified.
     */
    protected static Locale defaultLocale = Locale.getDefault();


    /**
     * The attributes associated with this Request, keyed by attribute name.
     */
    protected HashMap attributes = new HashMap();


    /**
     * List of read only attributes for this Request.
     */
    private HashMap readOnlyAttributes = new HashMap();


    /**
     * The preferred Locales assocaited with this Request.
     */
    protected ArrayList locales = new ArrayList();


    /**
     * Internal notes associated with this request by Catalina components
     * and event listeners.
     */
    private transient HashMap notes = new HashMap();


    /**
     * Authentication type.
     */
    protected String authType = null;


    /**
     * The current dispatcher type.
     */
    protected Object dispatcherType = null;


    /**
     * The associated input buffer.
     */
    protected InputBuffer inputBuffer = new InputBuffer();


    /**
     * ServletInputStream.
     */
    protected CoyoteInputStream inputStream = 
        new CoyoteInputStream(inputBuffer);


    /**
     * Reader.
     */
    protected CoyoteReader reader = new CoyoteReader(inputBuffer);


    /**
     * Using stream flag.
     */
    protected boolean usingInputStream = false;


    /**
     * Using writer flag.
     */
    protected boolean usingReader = false;


    /**
     * User principal.
     */
    protected Principal userPrincipal = null;


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
     * The Subject associated with the current AccessControllerContext
     */
    protected transient Subject subject = null;


    /**
     * Post data buffer.
     */
    protected static int CACHED_POST_LEN = 8192;
    protected byte[] postData = null;


    /**
     * Hash map used in the getParametersMap method.
     */
    protected ParameterMap parameterMap = new ParameterMap();


    /**
     * The currently active session for this request.
     */
    protected Session session = null;


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
     * The string parser we will use for parsing request lines.
     */
    private StringParser parser = new StringParser();


    /**
     * Local port
     */
    protected int localPort = -1;

    /**
     * Remote address.
     */
    protected String remoteAddr = null;


    /**
     * Remote host.
     */
    protected String remoteHost = null;

    
    /**
     * Remote port
     */
    protected int remotePort = -1;
    
    /**
     * Local address
     */
    protected String localAddr = null;

    
    /**
     * Local address
     */
    protected String localName = null;


    // --------------------------------------------------------- Public Methods


    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    public void recycle() {

        context = null;
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
        remoteAddr = null;
        remoteHost = null;
        remotePort = -1;
        localPort = -1;
        localAddr = null;
        localName = null;

        attributes.clear();
        notes.clear();
        cookies = null;

        if (session != null) {
            session.endAccess();
        }
        session = null;
        requestedSessionCookie = false;
        requestedSessionId = null;
        requestedSessionURL = false;

        if (Constants.SECURITY) {
            parameterMap = new ParameterMap();
        } else {
            parameterMap.setLocked(false);
            parameterMap.clear();
        }

        mappingData.recycle();

        if (Constants.SECURITY) {
            if (facade != null) {
                facade.clear();
                facade = null;
            }
            if (inputStream != null) {
                inputStream.clear();
                inputStream = null;
            }
            if (reader != null) {
                reader.clear();
                reader = null;
            }
        }

    }


    /**
     * Clear cached encoders (to save memory for Comet requests).
     */
    public void clearEncoders() {
        inputBuffer.clearEncoders();
    }
    

    // -------------------------------------------------------- Request Methods


    /**
     * Associated Catalina connector.
     */
    protected Connector connector;

    /**
     * Return the Connector through which this Request was received.
     */
    public Connector getConnector() {
        return (this.connector);
    }

    /**
     * Set the Connector through which this Request was received.
     *
     * @param connector The new connector
     */
    public void setConnector(Connector connector) {
        this.connector = connector;
    }


    /**
     * Associated context.
     */
    protected Context context = null;

    /**
     * Return the Context within which this Request is being processed.
     */
    public Context getContext() {
        return (this.context);
    }


    /**
     * Set the Context within which this Request is being processed.  This
     * must be called as soon as the appropriate Context is identified, because
     * it identifies the value to be returned by <code>getContextPath()</code>,
     * and thus enables parsing of the request URI.
     *
     * @param context The newly associated Context
     */
    public void setContext(Context context) {
        this.context = context;
    }


    /**
     * Filter chain associated with the request.
     */
    protected FilterChain filterChain = null;

    /**
     * Get filter chain associated with the request.
     */
    public FilterChain getFilterChain() {
        return (this.filterChain);
    }

    /**
     * Set filter chain associated with the request.
     * 
     * @param filterChain new filter chain
     */
    public void setFilterChain(FilterChain filterChain) {
        this.filterChain = filterChain;
    }


    /**
     * Return the Host within which this Request is being processed.
     */
    public Host getHost() {
        if (getContext() == null)
            return null;
        return (Host) getContext().getParent();
        //return ((Host) mappingData.host);
    }


    /**
     * Set the Host within which this Request is being processed.  This
     * must be called as soon as the appropriate Host is identified, and
     * before the Request is passed to a context.
     *
     * @param host The newly associated Host
     */
    public void setHost(Host host) {
        mappingData.host = host;
    }


    /**
     * Descriptive information about this Request implementation.
     */
    protected static final String info =
        "org.apache.coyote.catalina.CoyoteRequest/1.0";

    /**
     * Return descriptive information about this Request implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * Mapping data.
     */
    protected MappingData mappingData = new MappingData();

    /**
     * Return mapping data.
     */
    public MappingData getMappingData() {
        return (mappingData);
    }


    /**
     * The facade associated with this request.
     */
    protected RequestFacade facade = null;

    /**
     * Return the <code>ServletRequest</code> for which this object
     * is the facade.  This method must be implemented by a subclass.
     */
    public HttpServletRequest getRequest() {
        if (facade == null) {
            facade = new RequestFacade(this);
        } 
        return (facade);
    }


    /**
     * The response with which this request is associated.
     */
    protected org.apache.catalina.connector.Response response = null;

    /**
     * Return the Response with which this Request is associated.
     */
    public org.apache.catalina.connector.Response getResponse() {
        return (this.response);
    }

    /**
     * Set the Response with which this Request is associated.
     *
     * @param response The new associated response
     */
    public void setResponse(org.apache.catalina.connector.Response response) {
        this.response = response;
    }

    /**
     * Return the input stream associated with this Request.
     */
    public InputStream getStream() {
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;
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
     * URI byte to char converter (not recycled).
     */
    protected B2CConverter URIConverter = null;

    /**
     * Return the URI converter.
     */
    protected B2CConverter getURIConverter() {
        return URIConverter;
    }

    /**
     * Set the URI converter.
     * 
     * @param URIConverter the new URI connverter
     */
    protected void setURIConverter(B2CConverter URIConverter) {
        this.URIConverter = URIConverter;
    }


    /**
     * Associated wrapper.
     */
    protected Wrapper wrapper = null;

    /**
     * Return the Wrapper within which this Request is being processed.
     */
    public Wrapper getWrapper() {
        return (this.wrapper);
    }


    /**
     * Set the Wrapper within which this Request is being processed.  This
     * must be called as soon as the appropriate Wrapper is identified, and
     * before the Request is ultimately passed to an application servlet.
     * @param wrapper The newly associated Wrapper
     */
    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
    }


    // ------------------------------------------------- Request Public Methods


    /**
     * Create and return a ServletInputStream to read the content
     * associated with this Request.
     *
     * @exception IOException if an input/output error occurs
     */
    public ServletInputStream createInputStream() 
        throws IOException {
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
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
     * Return the object bound with the specified name to the internal notes
     * for this request, or <code>null</code> if no such binding exists.
     *
     * @param name Name of the note to be returned
     */
    public Object getNote(String name) {
        return (notes.get(name));
    }


    /**
     * Return an Iterator containing the String names of all notes bindings
     * that exist for this request.
     */
    public Iterator getNoteNames() {
        return (notes.keySet().iterator());
    }


    /**
     * Remove any object bound to the specified name in the internal notes
     * for this request.
     *
     * @param name Name of the note to be removed
     */
    public void removeNote(String name) {
        notes.remove(name);
    }


    /**
     * Bind an object to a specified name in the internal notes associated
     * with this request, replacing any existing binding for this name.
     *
     * @param name Name to which the object should be bound
     * @param value Object to be bound to the specified name
     */
    public void setNote(String name, Object value) {
        notes.put(name, value);
    }


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
     * Set the protocol name and version associated with this Request.
     *
     * @param protocol Protocol name and version
     */
    public void setProtocol(String protocol) {
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
        coyoteRequest.serverName().setString(name);
    }


    /**
     * Set the port number of the server to process this request.
     *
     * @param port The server port
     */
    public void setServerPort(int port) {
        coyoteRequest.setServerPort(port);
    }


    // ------------------------------------------------- ServletRequest Methods


    /**
     * Return the specified request attribute if it exists; otherwise, return
     * <code>null</code>.
     *
     * @param name Name of the request attribute to return
     */
    public Object getAttribute(String name) {

        if (name.equals(Globals.DISPATCHER_TYPE_ATTR)) {
            return (dispatcherType == null) 
                ? ApplicationFilterFactory.REQUEST_INTEGER
                : dispatcherType;
        } else if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            return (requestDispatcherPath == null) 
                ? getRequestPathMB().toString()
                : requestDispatcherPath.toString();
        }

        Object attr=attributes.get(name);

        if(attr!=null)
            return(attr);

        attr =  coyoteRequest.getAttribute(name);
        if(attr != null)
            return attr;
        if( isSSLAttribute(name) ) {
            coyoteRequest.action(ActionCode.ACTION_REQ_SSL_ATTRIBUTE, 
                                 coyoteRequest);
            attr = coyoteRequest.getAttribute(Globals.CERTIFICATES_ATTR);
            if( attr != null) {
                attributes.put(Globals.CERTIFICATES_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.CIPHER_SUITE_ATTR);
            if(attr != null) {
                attributes.put(Globals.CIPHER_SUITE_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.KEY_SIZE_ATTR);
            if(attr != null) {
                attributes.put(Globals.KEY_SIZE_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.SSL_SESSION_ID_ATTR);
            if(attr != null) {
                attributes.put(Globals.SSL_SESSION_ID_ATTR, attr);
            }
            attr = attributes.get(name);
        }
        return attr;
    }


    /**
     * Test if a given name is one of the special Servlet-spec SSL attributes.
     */
    static boolean isSSLAttribute(String name) {
        return Globals.CERTIFICATES_ATTR.equals(name) ||
            Globals.CIPHER_SUITE_ATTR.equals(name) ||
            Globals.KEY_SIZE_ATTR.equals(name)  ||
            Globals.SSL_SESSION_ID_ATTR.equals(name);
    }

    /**
     * Return the names of all request attributes for this Request, or an
     * empty <code>Enumeration</code> if there are none.
     */
    public Enumeration getAttributeNames() {
        if (isSecure()) {
            getAttribute(Globals.CERTIFICATES_ATTR);
        }
        return new Enumerator(attributes.keySet(), true);
    }


    /**
     * Return the character encoding for this Request.
     */
    public String getCharacterEncoding() {
      return (coyoteRequest.getCharacterEncoding());
    }


    /**
     * Return the content length for this Request.
     */
    public int getContentLength() {
        return (coyoteRequest.getContentLength());
    }


    /**
     * Return the content type for this Request.
     */
    public String getContentType() {
        return (coyoteRequest.getContentType());
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
                (sm.getString("coyoteRequest.getInputStream.ise"));

        usingInputStream = true;
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;

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
     * Return the value of the specified request parameter, if any; otherwise,
     * return <code>null</code>.  If there is more than one value defined,
     * return only the first one.
     *
     * @param name Name of the desired request parameter
     */
    public String getParameter(String name) {

        if (!parametersParsed)
            parseParameters();

        return coyoteRequest.getParameters().getParameter(name);

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

        return coyoteRequest.getParameters().getParameterNames();

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

        return coyoteRequest.getParameters().getParameterValues(name);

    }


    /**
     * Return the protocol and version used to make this Request.
     */
    public String getProtocol() {
        return coyoteRequest.protocol().toString();
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
                (sm.getString("coyoteRequest.getReader.ise"));

        usingReader = true;
        inputBuffer.checkConverter();
        if (reader == null) {
            reader = new CoyoteReader(inputBuffer);
        }
        return reader;

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
        ServletContext servletContext = context.getServletContext();
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
        if (remoteAddr == null) {
            coyoteRequest.action
                (ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE, coyoteRequest);
            remoteAddr = coyoteRequest.remoteAddr().toString();
        }
        return remoteAddr;
    }


    /**
     * Return the remote host name making this Request.
     */
    public String getRemoteHost() {
        if (remoteHost == null) {
            if (!connector.getEnableLookups()) {
                remoteHost = getRemoteAddr();
            } else {
                coyoteRequest.action
                    (ActionCode.ACTION_REQ_HOST_ATTRIBUTE, coyoteRequest);
                remoteHost = coyoteRequest.remoteHost().toString();
            }
        }
        return remoteHost;
    }

    /**
     * Returns the Internet Protocol (IP) source port of the client
     * or last proxy that sent the request.
     */    
    public int getRemotePort(){
        if (remotePort == -1) {
            coyoteRequest.action
                (ActionCode.ACTION_REQ_REMOTEPORT_ATTRIBUTE, coyoteRequest);
            remotePort = coyoteRequest.getRemotePort();
        }
        return remotePort;    
    }

    /**
     * Returns the host name of the Internet Protocol (IP) interface on
     * which the request was received.
     */
    public String getLocalName(){
        if (localName == null) {
            coyoteRequest.action
                (ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE, coyoteRequest);
            localName = coyoteRequest.localName().toString();
        }
        return localName;
    }

    /**
     * Returns the Internet Protocol (IP) address of the interface on
     * which the request  was received.
     */       
    public String getLocalAddr(){
        if (localAddr == null) {
            coyoteRequest.action
                (ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE, coyoteRequest);
            localAddr = coyoteRequest.localAddr().toString();
        }
        return localAddr;    
    }


    /**
     * Returns the Internet Protocol (IP) port number of the interface
     * on which the request was received.
     */
    public int getLocalPort(){
        if (localPort == -1){
            coyoteRequest.action
                (ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE, coyoteRequest);
            localPort = coyoteRequest.getLocalPort();
        }
        return localPort;
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
            return (context.getServletContext().getRequestDispatcher(path));

        // Convert a request-relative path to a context-relative one
        String servletPath = (String) getAttribute(Globals.INCLUDE_SERVLET_PATH_ATTR);
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

        return (context.getServletContext().getRequestDispatcher(relative));

    }


    /**
     * Return the scheme used to make this Request.
     */
    public String getScheme() {
        return (coyoteRequest.scheme().toString());
    }


    /**
     * Return the server name responding to this Request.
     */
    public String getServerName() {
        return (coyoteRequest.serverName().toString());
    }


    /**
     * Return the server port responding to this Request.
     */
    public int getServerPort() {
        return (coyoteRequest.getServerPort());
    }


    /**
     * Was this request received on a secure connection?
     */
    public boolean isSecure() {
        return (secure);
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
        if (readOnlyAttributes.containsKey(name)) {
            return;
        }

        // Pass special attributes to the native layer
        if (name.startsWith("org.apache.tomcat.")) {
            coyoteRequest.getAttributes().remove(name);
        }

        found = attributes.containsKey(name);
        if (found) {
            value = attributes.get(name);
            attributes.remove(name);
        } else {
            return;
        }
        
        // Notify interested application event listeners
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletRequestAttributeEvent event =
          new ServletRequestAttributeEvent(context.getServletContext(),
                                           getRequest(), name, value);
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletRequestAttributeListener))
                continue;
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners[i];
            try {
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                context.getLogger().error(sm.getString("coyoteRequest.attributeEvent"), t);
                // Error valve will pick this execption up and display it to user
                attributes.put( Globals.EXCEPTION_ATTR, t );
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
                (sm.getString("coyoteRequest.setAttribute.namenull"));

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        if (name.equals(Globals.DISPATCHER_TYPE_ATTR)) {
            dispatcherType = value;
            return;
        } else if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            requestDispatcherPath = value;
            return;
        }

        Object oldValue = null;
        boolean replaced = false;

        // Add or replace the specified attribute
        // Check for read only attribute
        // requests are per thread so synchronization unnecessary
        if (readOnlyAttributes.containsKey(name)) {
            return;
        }

        oldValue = attributes.put(name, value);
        if (oldValue != null) {
            replaced = true;
        }

        // Pass special attributes to the native layer
        if (name.startsWith("org.apache.tomcat.")) {
            coyoteRequest.setAttribute(name, value);
        }
        
        // Notify interested application event listeners
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletRequestAttributeEvent event = null;
        if (replaced)
            event =
                new ServletRequestAttributeEvent(context.getServletContext(),
                                                 getRequest(), name, oldValue);
        else
            event =
                new ServletRequestAttributeEvent(context.getServletContext(),
                                                 getRequest(), name, value);

        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletRequestAttributeListener))
                continue;
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners[i];
            try {
                if (replaced) {
                    listener.attributeReplaced(event);
                } else {
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                context.getLogger().error(sm.getString("coyoteRequest.attributeEvent"), t);
                // Error valve will pick this execption up and display it to user
                attributes.put( Globals.EXCEPTION_ATTR, t );
            }
        }
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

        if (usingReader)
            return;
        
        // Ensure that the specified encoding is valid
        byte buffer[] = new byte[1];
        buffer[0] = (byte) 'a';
        String dummy = new String(buffer, enc);

        // Save the validated encoding
        coyoteRequest.setCharacterEncoding(enc);

    }


    // ---------------------------------------------------- HttpRequest Methods


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
        coyoteRequest.getParameters().addParameterValues(name, values);
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
     * Set the HTTP request method used for this Request.
     *
     * @param method The request method
     */
    public void setMethod(String method) {
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
     * Set the unparsed request URI for this Request.  This will normally be
     * called by the HTTP Connector, when it parses the request headers.
     *
     * @param uri The request URI
     */
    public void setRequestURI(String uri) {
        // Not used
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
     * Get the decoded request URI.
     * 
     * @return the URL decoded request URI
     */
    public String getDecodedRequestURI() {
        return (coyoteRequest.decodedURI().toString());
    }


    /**
     * Get the decoded request URI.
     * 
     * @return the URL decoded request URI
     */
    public MessageBytes getDecodedRequestURIMB() {
        return (coyoteRequest.decodedURI());
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
                        session.getAttribute(Globals.SUBJECT_ATTR) == null) {
                subject = new Subject();
                subject.getPrincipals().add(principal);         
            }
            if (session != null){
                session.setAttribute(Globals.SUBJECT_ATTR, subject);
            }
        } 

        this.userPrincipal = principal;
    }


    // --------------------------------------------- HttpServletRequest Methods


    /**
     * Return the authentication type used for this Request.
     */
    public String getAuthType() {
        return (authType);
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
     * Set the set of cookies recieved with this Request.
     */
    public void setCookies(Cookie[] cookies) {

        this.cookies = cookies;

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

        // Attempt to convert the date header in a variety of formats
        long result = FastHttpDateFormat.parseDate(value, formats);
        if (result != (-1L)) {
            return result;
        }
        throw new IllegalArgumentException(value);

    }


    /**
     * Return the first value of the specified header, if any; otherwise,
     * return <code>null</code>
     *
     * @param name Name of the requested header
     */
    public String getHeader(String name) {
        return coyoteRequest.getHeader(name);
    }


    /**
     * Return all of the values of the specified header, if any; otherwise,
     * return an empty enumeration.
     *
     * @param name Name of the requested header
     */
    public Enumeration getHeaders(String name) {
        return coyoteRequest.getMimeHeaders().values(name);
    }


    /**
     * Return the names of all headers received with this request.
     */
    public Enumeration getHeaderNames() {
        return coyoteRequest.getMimeHeaders().names();
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
     * Return the HTTP request method used in this Request.
     */
    public String getMethod() {
        return coyoteRequest.method().toString();
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
     * Return the query string associated with this request.
     */
    public String getQueryString() {
        String queryString = coyoteRequest.queryString().toString();
        if (queryString == null || queryString.equals("")) {
            return (null);
        } else {
            return queryString;
        }
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
     * Get the request path.
     * 
     * @return the request path
     */
    public MessageBytes getRequestPathMB() {
        return (mappingData.requestPath);
    }


    /**
     * Return the session identifier included in this request, if any.
     */
    public String getRequestedSessionId() {
        return (requestedSessionId);
    }


    /**
     * Return the request URI for this request.
     */
    public String getRequestURI() {
        return coyoteRequest.requestURI().toString();
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
     * Return the session associated with this Request, creating one
     * if necessary.
     */
    public HttpSession getSession() {
        Session session = doGetSession(true);
        if (session != null) {
            return session.getSession();
        } else {
            return null;
        }
    }


    /**
     * Return the session associated with this Request, creating one
     * if necessary and requested.
     *
     * @param create Create a new session if one does not exist
     */
    public HttpSession getSession(boolean create) {
        Session session = doGetSession(create);
        if (session != null) {
            return session.getSession();
        } else {
            return null;
        }
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
     */
    public boolean isRequestedSessionIdFromURL() {

        if (requestedSessionId != null)
            return (requestedSessionURL);
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
     * request identifies a valid session.
     */
    public boolean isRequestedSessionIdValid() {

        if (requestedSessionId == null)
            return (false);
        if (context == null)
            return (false);
        Manager manager = context.getManager();
        if (manager == null)
            return (false);
        Session session = null;
        try {
            session = manager.findSession(requestedSessionId);
        } catch (IOException e) {
            session = null;
        }
        if ((session != null) && session.isValid())
            return (true);
        else
            return (false);

    }


    /**
     * Return <code>true</code> if the authenticated user principal
     * possesses the specified role name.
     *
     * @param role Role name to be validated
     */
    public boolean isUserInRole(String role) {

        // Have we got an authenticated principal at all?
        if (userPrincipal == null)
            return (false);

        // Identify the Realm we will use for checking role assignmenets
        if (context == null)
            return (false);
        Realm realm = context.getRealm();
        if (realm == null)
            return (false);

        // Check for a role alias defined in a <security-role-ref> element
        if (wrapper != null) {
            String realRole = wrapper.findSecurityReference(role);
            if ((realRole != null) &&
                realm.hasRole(userPrincipal, realRole))
                return (true);
        }

        // Check for a role defined directly as a <security-role>
        return (realm.hasRole(userPrincipal, role));

    }


    /**
     * Return the principal that has been authenticated for this Request.
     */
    public Principal getPrincipal() {
        return (userPrincipal);
    }


    /**
     * Return the principal that has been authenticated for this Request.
     */
    public Principal getUserPrincipal() {
        if (userPrincipal instanceof GenericPrincipal) {
            return ((GenericPrincipal) userPrincipal).getUserPrincipal();
        } else {
            return (userPrincipal);
        }
    }


    /**
     * Return the session associated with this Request, creating one
     * if necessary.
     */
    public Session getSessionInternal() {
        return doGetSession(true);
    }


    /**
     * Return the session associated with this Request, creating one
     * if necessary and requested.
     *
     * @param create Create a new session if one does not exist
     */
    public Session getSessionInternal(boolean create) {
        return doGetSession(create);
    }


    // ------------------------------------------------------ Protected Methods


    protected Session doGetSession(boolean create) {

        // There cannot be a session if no context has been assigned yet
        if (context == null)
            return (null);

        // Return the current session if it exists and is valid
        if ((session != null) && !session.isValid())
            session = null;
        if (session != null)
            return (session);

        // Return the requested session if it exists and is valid
        Manager manager = null;
        if (context != null)
            manager = context.getManager();
        if (manager == null)
            return (null);      // Sessions are not supported
        if (requestedSessionId != null) {
            try {
                session = manager.findSession(requestedSessionId);
            } catch (IOException e) {
                session = null;
            }
            if ((session != null) && !session.isValid())
                session = null;
            if (session != null) {
                session.access();
                return (session);
            }
        }

        // Create a new session if requested and the response is not committed
        if (!create)
            return (null);
        if ((context != null) && (response != null) &&
            context.getCookies() &&
            response.getResponse().isCommitted()) {
            throw new IllegalStateException
              (sm.getString("coyoteRequest.sessionCreateCommitted"));
        }

        // Attempt to reuse session id if one was submitted in a cookie
        // Do not reuse the session id if it is from a URL, to prevent possible
        // phishing attacks
        if (connector.getEmptySessionPath() 
                && isRequestedSessionIdFromCookie()) {
            session = manager.createSession(getRequestedSessionId());
        } else {
            session = manager.createSession(null);
        }

        // Creating a new session cookie based on that session
        if ((session != null) && (getContext() != null)
               && getContext().getCookies()) {
            Cookie cookie = new Cookie(Globals.SESSION_COOKIE_NAME,
                                       session.getIdInternal());
            configureSessionCookie(cookie);
            response.addCookieInternal(cookie);
        }

        if (session != null) {
            session.access();
            return (session);
        } else {
            return (null);
        }

    }

    /**
     * Configures the given JSESSIONID cookie.
     *
     * @param cookie The JSESSIONID cookie to be configured
     */
    protected void configureSessionCookie(Cookie cookie) {
        cookie.setMaxAge(-1);
        String contextPath = null;
        if (!connector.getEmptySessionPath() && (getContext() != null)) {
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
     * Parse cookies.
     */
    protected void parseCookies() {

        cookiesParsed = true;

        Cookies serverCookies = coyoteRequest.getCookies();
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
     * Parse request parameters.
     */
    protected void parseParameters() {

        parametersParsed = true;

        Parameters parameters = coyoteRequest.getParameters();

        // getCharacterEncoding() may have been overridden to search for
        // hidden form field containing request encoding
        String enc = getCharacterEncoding();

        boolean useBodyEncodingForURI = connector.getUseBodyEncodingForURI();
        if (enc != null) {
            parameters.setEncoding(enc);
            if (useBodyEncodingForURI) {
                parameters.setQueryStringEncoding(enc);
            }
        } else {
            parameters.setEncoding
                (org.apache.coyote.Constants.DEFAULT_CHARACTER_ENCODING);
            if (useBodyEncodingForURI) {
                parameters.setQueryStringEncoding
                    (org.apache.coyote.Constants.DEFAULT_CHARACTER_ENCODING);
            }
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
            int maxPostSize = connector.getMaxPostSize();
            if ((maxPostSize > 0) && (len > maxPostSize)) {
                context.getLogger().info
                    (sm.getString("coyoteRequest.postTooLarge"));
                throw new IllegalStateException("Post too large");
            }
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

        // Store the accumulated languages that have been requested in
        // a local collection, sorted by the quality value (so we can
        // add Locales in descending order).  The values will be ArrayLists
        // containing the corresponding Locales to be added
        TreeMap locales = new TreeMap();

        // Preprocess the value to remove all whitespace
        int white = value.indexOf(' ');
        if (white < 0)
            white = value.indexOf('\t');
        if (white >= 0) {
            StringBuffer sb = new StringBuffer();
            int len = value.length();
            for (int i = 0; i < len; i++) {
                char ch = value.charAt(i);
                if ((ch != ' ') && (ch != '\t'))
                    sb.append(ch);
            }
            value = sb.toString();
        }

        // Process each comma-delimited language specification
        parser.setString(value);        // ASSERT: parser is available to us
        int length = parser.getLength();
        while (true) {

            // Extract the next comma-delimited entry
            int start = parser.getIndex();
            if (start >= length)
                break;
            int end = parser.findChar(',');
            String entry = parser.extract(start, end).trim();
            parser.advance();   // For the following entry

            // Extract the quality factor for this entry
            double quality = 1.0;
            int semi = entry.indexOf(";q=");
            if (semi >= 0) {
                try {
                    quality = Double.parseDouble(entry.substring(semi + 3));
                } catch (NumberFormatException e) {
                    quality = 0.0;
                }
                entry = entry.substring(0, semi);
            }

            // Skip entries we are not going to keep track of
            if (quality < 0.00005)
                continue;       // Zero (or effectively zero) quality factors
            if ("*".equals(entry))
                continue;       // FIXME - "*" entries are not handled

            // Extract the language and country for this entry
            String language = null;
            String country = null;
            String variant = null;
            int dash = entry.indexOf('-');
            if (dash < 0) {
                language = entry;
                country = "";
                variant = "";
            } else {
                language = entry.substring(0, dash);
                country = entry.substring(dash + 1);
                int vDash = country.indexOf('-');
                if (vDash > 0) {
                    String cTemp = country.substring(0, vDash);
                    variant = country.substring(vDash + 1);
                    country = cTemp;
                } else {
                    variant = "";
                }
            }

            // Add a new Locale to the list of Locales for this quality level
            Locale locale = new Locale(language, country, variant);
            Double key = new Double(-quality);  // Reverse the order
            ArrayList values = (ArrayList) locales.get(key);
            if (values == null) {
                values = new ArrayList();
                locales.put(key, values);
            }
            values.add(locale);

        }

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


}

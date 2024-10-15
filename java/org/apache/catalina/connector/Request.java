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

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.naming.NamingException;
import javax.security.auth.Subject;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestAttributeEvent;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.TomcatPrincipal;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.ApplicationFilterChain;
import org.apache.catalina.core.ApplicationMapping;
import org.apache.catalina.core.ApplicationPart;
import org.apache.catalina.core.ApplicationSessionCookieConfig;
import org.apache.catalina.core.AsyncContextImpl;
import org.apache.catalina.mapper.MappingData;
import org.apache.catalina.util.ParameterMap;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.TLSUtil;
import org.apache.catalina.util.URLEncoder;
import org.apache.coyote.ActionCode;
import org.apache.coyote.BadRequestException;
import org.apache.coyote.UpgradeToken;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharsetHolder;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.http.CookieProcessor;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.apache.tomcat.util.http.Parameters;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.ServerCookies;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileUpload;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.impl.InvalidContentTypeException;
import org.apache.tomcat.util.http.fileupload.impl.SizeException;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;
import org.apache.tomcat.util.http.parser.AcceptLanguage;
import org.apache.tomcat.util.http.parser.Upgrade;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.res.StringManager;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;

/**
 * Wrapper object for the Coyote request.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 */
public class Request implements HttpServletRequest {

    private static final String HTTP_UPGRADE_HEADER_NAME = "upgrade";

    private static final Log log = LogFactory.getLog(Request.class);

    /**
     * Create a new Request object associated with the given Connector.
     *
     * @param connector     The Connector with which this Request object will always be associated. In normal usage this
     *                          must be non-null. In some test scenarios, it may be possible to use a null Connector
     *                          without triggering an NPE.
     * @param coyoteRequest The Coyote request with which this Request object will always be associated. In normal usage
     *                          this must be non-null. In some test scenarios, it may be possible to use a null request
     *                          without triggering an NPE.
     */
    public Request(Connector connector, org.apache.coyote.Request coyoteRequest) {
        this.connector = connector;
        this.coyoteRequest = coyoteRequest;
        inputBuffer = new InputBuffer(coyoteRequest);
    }


    // ------------------------------------------------------------- Properties

    /**
     * Coyote request.
     */
    protected final org.apache.coyote.Request coyoteRequest;

    /**
     * Get the Coyote request.
     *
     * @return the Coyote request object
     */
    public org.apache.coyote.Request getCoyoteRequest() {
        return this.coyoteRequest;
    }


    // ----------------------------------------------------- Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Request.class);


    /**
     * The set of cookies associated with this Request.
     */
    protected Cookie[] cookies = null;


    /**
     * The default Locale if none are specified.
     */
    protected static final Locale defaultLocale = Locale.getDefault();


    /**
     * The attributes associated with this Request, keyed by attribute name.
     */
    private final Map<String,Object> attributes = new ConcurrentHashMap<>();


    /**
     * Flag that indicates if SSL attributes have been parsed to improve performance for applications (usually
     * frameworks) that make multiple calls to {@link Request#getAttributeNames()}.
     */
    protected boolean sslAttributesParsed = false;


    /**
     * The preferred Locales associated with this Request.
     */
    protected final ArrayList<Locale> locales = new ArrayList<>();


    /**
     * Internal notes associated with this request by Catalina components and event listeners.
     */
    private final transient HashMap<String,Object> notes = new HashMap<>();


    /**
     * Authentication type.
     */
    protected String authType = null;


    /**
     * The current dispatcher type.
     */
    protected DispatcherType internalDispatcherType = null;


    /**
     * The associated input buffer.
     */
    protected final InputBuffer inputBuffer;


    /**
     * ServletInputStream.
     */
    protected CoyoteInputStream inputStream;


    /**
     * Reader.
     */
    protected CoyoteReader reader;


    /**
     * Using stream flag.
     */
    protected boolean usingInputStream = false;


    /**
     * Using reader flag.
     */
    protected boolean usingReader = false;


    /**
     * User principal.
     */
    protected Principal userPrincipal = null;


    /**
     * Request parameters parsed flag.
     */
    protected boolean parametersParsed = false;


    /**
     * Cookie headers parsed flag. Indicates that the cookie headers have been parsed into ServerCookies.
     */
    protected boolean cookiesParsed = false;


    /**
     * Cookie parsed flag. Indicates that the ServerCookies have been converted into user facing Cookie objects.
     */
    protected boolean cookiesConverted = false;


    /**
     * Secure flag.
     */
    protected boolean secure = false;


    /**
     * The Subject associated with the current AccessControlContext
     */
    protected transient Subject subject = null;


    /**
     * Post data buffer.
     */
    protected static final int CACHED_POST_LEN = 8192;
    protected byte[] postData = null;


    /**
     * Hash map used in the getParametersMap method.
     */
    protected ParameterMap<String,String[]> parameterMap = new ParameterMap<>();


    /**
     * The exception thrown, if any when parsing the parameters including parts.
     */
    protected IllegalStateException parametersParseException = null;


    /**
     * The parts, if any, uploaded with this request.
     */
    protected Collection<Part> parts = null;


    /**
     * The exception thrown, if any when parsing the parts.
     */
    protected Exception partsParseException = null;


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
     * Was the requested session ID obtained from the SSL session?
     */
    protected boolean requestedSessionSSL = false;


    /**
     * Parse locales.
     */
    protected boolean localesParsed = false;


    /**
     * Local port
     */
    protected int localPort = -1;

    /**
     * Remote address.
     */
    protected String remoteAddr = null;


    /**
     * Connection peer address.
     */
    protected String peerAddr = null;


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

    /**
     * AsyncContext
     */
    private volatile AsyncContextImpl asyncContext = null;

    protected Boolean asyncSupported = null;

    private HttpServletRequest applicationRequest = null;


    // --------------------------------------------------------- Public Methods

    protected void addPathParameter(String name, String value) {
        coyoteRequest.addPathParameter(name, value);
    }

    protected String getPathParameter(String name) {
        return coyoteRequest.getPathParameter(name);
    }

    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = Boolean.valueOf(asyncSupported);
    }

    /**
     * Release all object references, and initialize instance variables, in preparation for reuse of this object.
     */
    public void recycle() {

        internalDispatcherType = null;
        requestDispatcherPath = null;

        authType = null;
        inputBuffer.recycle();
        usingInputStream = false;
        usingReader = false;
        userPrincipal = null;
        subject = null;
        parametersParsed = false;
        if (parts != null) {
            for (Part part : parts) {
                try {
                    part.delete();
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.warn(sm.getString("coyoteRequest.deletePartFailed", part.getName()), t);
                }
            }
            parts = null;
        }
        parametersParseException = null;
        partsParseException = null;
        locales.clear();
        localesParsed = false;
        secure = false;
        remoteAddr = null;
        peerAddr = null;
        remoteHost = null;
        remotePort = -1;
        localPort = -1;
        localAddr = null;
        localName = null;

        attributes.clear();
        sslAttributesParsed = false;
        notes.clear();

        recycleSessionInfo();
        recycleCookieInfo(false);

        if (getDiscardFacades()) {
            parameterMap = new ParameterMap<>();
        } else {
            parameterMap.setLocked(false);
            parameterMap.clear();
        }

        mappingData.recycle();
        applicationMapping.recycle();

        applicationRequest = null;
        if (getDiscardFacades()) {
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

        asyncSupported = null;
        if (asyncContext != null) {
            asyncContext.recycle();
            asyncContext = null;
        }
    }


    protected void recycleSessionInfo() {
        if (session != null) {
            try {
                session.endAccess();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.warn(sm.getString("coyoteRequest.sessionEndAccessFail"), t);
            }
        }
        session = null;
        requestedSessionCookie = false;
        requestedSessionId = null;
        requestedSessionURL = false;
        requestedSessionSSL = false;
    }


    protected void recycleCookieInfo(boolean recycleCoyote) {
        cookiesParsed = false;
        cookiesConverted = false;
        cookies = null;
        if (recycleCoyote) {
            getCoyoteRequest().getCookies().recycle();
        }
    }


    // -------------------------------------------------------- Request Methods

    /**
     * Associated Catalina connector.
     */
    protected final Connector connector;

    /**
     * @return the Connector through which this Request was received.
     */
    public Connector getConnector() {
        return this.connector;
    }


    /**
     * Return the Context within which this Request is being processed.
     * <p>
     * This is available as soon as the appropriate Context is identified. Note that availability of a Context allows
     * <code>getContextPath()</code> to return a value, and thus enables parsing of the request URI.
     *
     * @return the Context mapped with the request
     */
    public Context getContext() {
        return mappingData.context;
    }


    /**
     * Get the recycling strategy of the facade objects.
     *
     * @return the value of the flag as set on the connector, or <code>true</code> if no connector is associated with
     *             this request
     */
    public boolean getDiscardFacades() {
        return (connector == null) ? true : connector.getDiscardFacades();
    }


    /**
     * Filter chain associated with the request.
     */
    protected FilterChain filterChain = null;

    /**
     * Get filter chain associated with the request.
     *
     * @return the associated filter chain
     */
    public FilterChain getFilterChain() {
        return this.filterChain;
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
     * @return the Host within which this Request is being processed.
     */
    public Host getHost() {
        return mappingData.host;
    }


    /**
     * Mapping data.
     */
    protected final MappingData mappingData = new MappingData();
    private final ApplicationMapping applicationMapping = new ApplicationMapping(mappingData);

    /**
     * @return mapping data.
     */
    public MappingData getMappingData() {
        return mappingData;
    }


    /**
     * The facade associated with this request.
     */
    protected RequestFacade facade = null;


    /**
     * @return the <code>ServletRequest</code> for which this object is the facade. This method must be implemented by a
     *             subclass.
     */
    public HttpServletRequest getRequest() {
        if (facade == null) {
            facade = new RequestFacade(this);
        }
        if (applicationRequest == null) {
            applicationRequest = facade;
        }
        return applicationRequest;
    }


    /**
     * Set a wrapped HttpServletRequest to pass to the application. Components wishing to wrap the request should obtain
     * the request via {@link #getRequest()}, wrap it and then call this method with the wrapped request.
     *
     * @param applicationRequest The wrapped request to pass to the application
     */
    public void setRequest(HttpServletRequest applicationRequest) {
        // Check the wrapper wraps this request
        ServletRequest r = applicationRequest;
        while (r instanceof HttpServletRequestWrapper) {
            r = ((HttpServletRequestWrapper) r).getRequest();
        }
        if (r != facade) {
            throw new IllegalArgumentException(sm.getString("request.illegalWrap"));
        }
        this.applicationRequest = applicationRequest;
    }


    /**
     * The response with which this request is associated.
     */
    protected Response response = null;

    /**
     * @return the Response with which this Request is associated.
     */
    public Response getResponse() {
        return this.response;
    }

    /**
     * Set the Response with which this Request is associated.
     *
     * @param response The new associated response
     */
    public void setResponse(Response response) {
        this.response = response;
    }

    /**
     * @return the input stream associated with this Request.
     */
    public InputStream getStream() {
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;
    }

    /**
     * URI byte to char converter.
     */
    protected B2CConverter URIConverter = null;

    /**
     * @return the URI converter.
     */
    protected B2CConverter getURIConverter() {
        return URIConverter;
    }

    /**
     * Set the URI converter.
     *
     * @param URIConverter the new URI converter
     */
    protected void setURIConverter(B2CConverter URIConverter) {
        this.URIConverter = URIConverter;
    }


    /**
     * @return the Wrapper within which this Request is being processed.
     */
    public Wrapper getWrapper() {
        return mappingData.wrapper;
    }


    // ------------------------------------------------- Request Public Methods

    /**
     * Create and return a ServletInputStream to read the content associated with this Request.
     *
     * @return the created input stream
     *
     * @exception IOException if an input/output error occurs
     */
    public ServletInputStream createInputStream() throws IOException {
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;
    }


    /**
     * Perform whatever actions are required to flush and close the input stream or reader, in a single operation.
     *
     * @exception IOException if an input/output error occurs
     */
    public void finishRequest() throws IOException {
        if (response.getStatus() == HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE) {
            checkSwallowInput();
        }
    }


    /**
     * @return the object bound with the specified name to the internal notes for this request, or <code>null</code> if
     *             no such binding exists.
     *
     * @param name Name of the note to be returned
     */
    public Object getNote(String name) {
        return notes.get(name);
    }


    /**
     * Remove any object bound to the specified name in the internal notes for this request.
     *
     * @param name Name of the note to be removed
     */
    public void removeNote(String name) {
        notes.remove(name);
    }


    /**
     * Set the port number of the server to process this request.
     *
     * @param port The server port
     */
    public void setLocalPort(int port) {
        localPort = port;
    }

    /**
     * Bind an object to a specified name in the internal notes associated with this request, replacing any existing
     * binding for this name.
     *
     * @param name  Name to which the object should be bound
     * @param value Object to be bound to the specified name
     */
    public void setNote(String name, Object value) {
        notes.put(name, value);
    }


    /**
     * Set the IP address of the remote client associated with this Request.
     *
     * @param remoteAddr The remote IP address
     */
    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }


    /**
     * Set the fully qualified name of the remote client associated with this Request.
     *
     * @param remoteHost The remote host name
     */
    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }


    /**
     * Set the value to be returned by <code>isSecure()</code> for this Request.
     *
     * @param secure The new isSecure value
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
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

    @SuppressWarnings("deprecation")
    @Override
    public Object getAttribute(String name) {
        // Special attributes
        SpecialAttributeAdapter adapter = specialAttributes.get(name);
        if (adapter != null) {
            return adapter.get(this, name);
        }

        Object attr = attributes.get(name);

        if (attr != null) {
            return attr;
        }

        attr = coyoteRequest.getAttribute(name);
        if (attr != null) {
            return attr;
        }
        if (!sslAttributesParsed && TLSUtil.isTLSRequestAttribute(name)) {
            coyoteRequest.action(ActionCode.REQ_SSL_ATTRIBUTE, coyoteRequest);
            attr = coyoteRequest.getAttribute(Globals.CERTIFICATES_ATTR);
            if (attr != null) {
                attributes.put(Globals.CERTIFICATES_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.SECURE_PROTOCOL_ATTR);
            if (attr != null) {
                attributes.put(Globals.SECURE_PROTOCOL_ATTR, attr);
                attributes.put(SSLSupport.PROTOCOL_VERSION_KEY, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.CIPHER_SUITE_ATTR);
            if (attr != null) {
                attributes.put(Globals.CIPHER_SUITE_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.KEY_SIZE_ATTR);
            if (attr != null) {
                attributes.put(Globals.KEY_SIZE_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.SSL_SESSION_ID_ATTR);
            if (attr != null) {
                attributes.put(Globals.SSL_SESSION_ID_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.SSL_SESSION_MGR_ATTR);
            if (attr != null) {
                attributes.put(Globals.SSL_SESSION_MGR_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(SSLSupport.REQUESTED_PROTOCOL_VERSIONS_KEY);
            if (attr != null) {
                attributes.put(SSLSupport.REQUESTED_PROTOCOL_VERSIONS_KEY, attr);
            }
            attr = coyoteRequest.getAttribute(SSLSupport.REQUESTED_CIPHERS_KEY);
            if (attr != null) {
                attributes.put(SSLSupport.REQUESTED_CIPHERS_KEY, attr);
            }
            attr = attributes.get(name);
            sslAttributesParsed = true;
        }
        return attr;
    }


    @Override
    public long getContentLengthLong() {
        return coyoteRequest.getContentLengthLong();
    }


    /**
     * {@inheritDoc}
     * <p>
     * The attribute names returned will only be those for the attributes set via {@link #setAttribute(String, Object)}.
     * Tomcat internal attributes will not be included although they are accessible via {@link #getAttribute(String)}.
     * The Tomcat internal attributes include:
     * <ul>
     * <li>{@link Globals#DISPATCHER_TYPE_ATTR}</li>
     * <li>{@link Globals#DISPATCHER_REQUEST_PATH_ATTR}</li>
     * <li>{@link Globals#ASYNC_SUPPORTED_ATTR}</li>
     * <li>{@link Globals#CERTIFICATES_ATTR} (SSL connections only)</li>
     * <li>{@link Globals#SECURE_PROTOCOL_ATTR} (SSL connections only)</li>
     * <li>{@link Globals#CIPHER_SUITE_ATTR} (SSL connections only)</li>
     * <li>{@link Globals#KEY_SIZE_ATTR} (SSL connections only)</li>
     * <li>{@link Globals#SSL_SESSION_ID_ATTR} (SSL connections only)</li>
     * <li>{@link Globals#SSL_SESSION_MGR_ATTR} (SSL connections only)</li>
     * </ul>
     * The underlying connector may also expose request attributes. These all have names starting with
     * "org.apache.tomcat" and include:
     * <ul>
     * <li>{@link Globals#SENDFILE_SUPPORTED_ATTR}</li>
     * </ul>
     * Connector implementations may return some, all or none of these attributes and may also support additional
     * attributes.
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        if (isSecure() && !sslAttributesParsed) {
            getAttribute(Globals.CERTIFICATES_ATTR);
        }
        // Take a copy to prevent ConcurrentModificationExceptions if used to
        // remove attributes
        Set<String> names = new HashSet<>(attributes.keySet());
        return Collections.enumeration(names);
    }


    @Override
    public String getCharacterEncoding() {
        String characterEncoding = coyoteRequest.getCharsetHolder().getName();

        if (characterEncoding == null) {
            Context context = getContext();
            if (context != null) {
                characterEncoding = context.getRequestCharacterEncoding();
            }
        }

        return characterEncoding;
    }


    private Charset getCharset() {
        Charset charset = coyoteRequest.getCharsetHolder().getCharset();

        if (charset == null) {
            Context context = getContext();
            if (context != null) {
                charset = CharsetHolder.getInstance(context.getRequestCharacterEncoding()).getCharset();
            }
        }

        if (charset == null) {
            charset = org.apache.coyote.Constants.DEFAULT_BODY_CHARSET;
        }

        return charset;
    }


    @Override
    public int getContentLength() {
        return coyoteRequest.getContentLength();
    }


    @Override
    public String getContentType() {
        return coyoteRequest.getContentType();
    }


    /**
     * Set the content type for this Request.
     *
     * @param contentType The content type
     */
    public void setContentType(String contentType) {
        coyoteRequest.setContentType(contentType);
    }


    @Override
    public ServletInputStream getInputStream() throws IOException {

        if (usingReader) {
            throw new IllegalStateException(sm.getString("coyoteRequest.getInputStream.ise"));
        }

        usingInputStream = true;
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;

    }


    @Override
    public Locale getLocale() {

        if (!localesParsed) {
            parseLocales();
        }

        if (locales.size() > 0) {
            return locales.get(0);
        }

        return defaultLocale;
    }


    @Override
    public Enumeration<Locale> getLocales() {

        if (!localesParsed) {
            parseLocales();
        }

        if (locales.size() > 0) {
            return Collections.enumeration(locales);
        }
        ArrayList<Locale> results = new ArrayList<>();
        results.add(defaultLocale);
        return Collections.enumeration(results);

    }


    @Override
    public String getParameter(String name) {
        parseParameters();
        return coyoteRequest.getParameters().getParameter(name);
    }


    @Override
    public Map<String,String[]> getParameterMap() {

        if (parameterMap.isLocked()) {
            return parameterMap;
        }

        Enumeration<String> enumeration = getParameterNames();
        while (enumeration.hasMoreElements()) {
            String name = enumeration.nextElement();
            String[] values = getParameterValues(name);
            parameterMap.put(name, values);
        }

        parameterMap.setLocked(true);

        return parameterMap;
    }


    @Override
    public Enumeration<String> getParameterNames() {
        parseParameters();
        return coyoteRequest.getParameters().getParameterNames();
    }


    @Override
    public String[] getParameterValues(String name) {
        parseParameters();
        return coyoteRequest.getParameters().getParameterValues(name);
    }


    @Override
    public String getProtocol() {
        return coyoteRequest.protocol().toStringType();
    }


    @Override
    public BufferedReader getReader() throws IOException {

        if (usingInputStream) {
            throw new IllegalStateException(sm.getString("coyoteRequest.getReader.ise"));
        }

        // InputBuffer has no easily accessible reference chain to the Context
        // to check for a default request character encoding at the Context.
        // Therefore, if a Context default should be used, it is set explicitly
        // here. Need to do this before setting usingReader.
        if (coyoteRequest.getCharsetHolder().getName() == null) {
            // Nothing currently set explicitly.
            // Check the context
            Context context = getContext();
            if (context != null) {
                String enc = context.getRequestCharacterEncoding();
                if (enc != null) {
                    // Explicitly set the context default so it is visible to
                    // InputBuffer when creating the Reader.
                    setCharacterEncoding(enc);
                }
            }
        }

        usingReader = true;

        inputBuffer.checkConverter();
        if (reader == null) {
            reader = new CoyoteReader(inputBuffer);
        }
        return reader;
    }


    @Override
    public String getRemoteAddr() {
        if (remoteAddr == null) {
            coyoteRequest.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, coyoteRequest);
            remoteAddr = coyoteRequest.remoteAddr().toString();
        }
        return remoteAddr;
    }


    /**
     * @return the connection peer IP address making this Request.
     */
    public String getPeerAddr() {
        if (peerAddr == null) {
            coyoteRequest.action(ActionCode.REQ_PEER_ADDR_ATTRIBUTE, coyoteRequest);
            peerAddr = coyoteRequest.peerAddr().toString();
        }
        return peerAddr;
    }


    @Override
    public String getRemoteHost() {
        if (remoteHost == null) {
            if (!connector.getEnableLookups()) {
                remoteHost = getRemoteAddr();
            } else {
                coyoteRequest.action(ActionCode.REQ_HOST_ATTRIBUTE, coyoteRequest);
                remoteHost = coyoteRequest.remoteHost().toString();
            }
        }
        return remoteHost;
    }

    @Override
    public int getRemotePort() {
        if (remotePort == -1) {
            coyoteRequest.action(ActionCode.REQ_REMOTEPORT_ATTRIBUTE, coyoteRequest);
            remotePort = coyoteRequest.getRemotePort();
        }
        return remotePort;
    }

    @Override
    public String getLocalName() {
        if (localName == null) {
            coyoteRequest.action(ActionCode.REQ_LOCAL_NAME_ATTRIBUTE, coyoteRequest);
            localName = coyoteRequest.localName().toString();
        }
        return localName;
    }

    @Override
    public String getLocalAddr() {
        if (localAddr == null) {
            coyoteRequest.action(ActionCode.REQ_LOCAL_ADDR_ATTRIBUTE, coyoteRequest);
            localAddr = coyoteRequest.localAddr().toString();
        }
        return localAddr;
    }


    @Override
    public int getLocalPort() {
        if (localPort == -1) {
            coyoteRequest.action(ActionCode.REQ_LOCALPORT_ATTRIBUTE, coyoteRequest);
            localPort = coyoteRequest.getLocalPort();
        }
        return localPort;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {

        Context context = getContext();
        if (context == null) {
            return null;
        }

        if (path == null) {
            return null;
        }

        int fragmentPos = path.indexOf('#');
        if (fragmentPos > -1) {
            log.warn(sm.getString("request.fragmentInDispatchPath", path));
            path = path.substring(0, fragmentPos);
        }

        // If the path is already context-relative, just pass it through
        if (path.startsWith("/")) {
            return context.getServletContext().getRequestDispatcher(path);
        }

        /*
         * Relative to what, exactly?
         *
         * From the Servlet 4.0 Javadoc: - The pathname specified may be relative, although it cannot extend outside the
         * current servlet context. - If it is relative, it must be relative against the current servlet
         *
         * From Section 9.1 of the spec: - The servlet container uses information in the request object to transform the
         * given relative path against the current servlet to a complete path.
         *
         * It is undefined whether the requestURI is used or whether servletPath and pathInfo are used. Given that the
         * RequestURI includes the contextPath (and extracting that is messy) , using the servletPath and pathInfo looks
         * to be the more reasonable choice.
         */

        // Convert a request-relative path to a context-relative one
        String servletPath = (String) getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        if (servletPath == null) {
            servletPath = getServletPath();
        }

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
        if (context.getDispatchersUseEncodedPaths()) {
            if (pos >= 0) {
                relative = URLEncoder.DEFAULT.encode(requestPath.substring(0, pos + 1), StandardCharsets.UTF_8) + path;
            } else {
                relative = URLEncoder.DEFAULT.encode(requestPath, StandardCharsets.UTF_8) + path;
            }
        } else {
            if (pos >= 0) {
                relative = requestPath.substring(0, pos + 1) + path;
            } else {
                relative = requestPath + path;
            }
        }

        return context.getServletContext().getRequestDispatcher(relative);
    }


    @Override
    public String getScheme() {
        return coyoteRequest.scheme().toStringType();
    }


    @Override
    public String getServerName() {
        return coyoteRequest.serverName().toString();
    }


    @Override
    public int getServerPort() {
        return coyoteRequest.getServerPort();
    }


    @Override
    public boolean isSecure() {
        return secure;
    }


    @Override
    public void removeAttribute(String name) {
        // Remove the specified attribute
        // Pass special attributes to the native layer
        if (name.startsWith("org.apache.tomcat.")) {
            coyoteRequest.getAttributes().remove(name);
        }

        boolean found = attributes.containsKey(name);
        if (found) {
            Object value = attributes.get(name);
            attributes.remove(name);

            // Notify interested application event listeners
            notifyAttributeRemoved(name, value);
        }
    }


    @Override
    public void setAttribute(String name, Object value) {

        // Name cannot be null
        if (name == null) {
            throw new IllegalArgumentException(sm.getString("coyoteRequest.setAttribute.namenull"));
        }

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        // Special attributes
        SpecialAttributeAdapter adapter = specialAttributes.get(name);
        if (adapter != null) {
            adapter.set(this, name, value);
            return;
        }

        // Add or replace the specified attribute
        Object oldValue = attributes.put(name, value);

        // Pass special attributes to the native layer
        if (name.startsWith("org.apache.tomcat.")) {
            coyoteRequest.setAttribute(name, value);
        }

        // Notify interested application event listeners
        notifyAttributeAssigned(name, value, oldValue);
    }


    /**
     * Notify interested listeners that attribute has been assigned a value.
     *
     * @param name     Attribute name
     * @param value    New attribute value
     * @param oldValue Old attribute value
     */
    private void notifyAttributeAssigned(String name, Object value, Object oldValue) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null || listeners.length == 0) {
            return;
        }
        boolean replaced = (oldValue != null);
        ServletRequestAttributeEvent event = null;
        if (replaced) {
            event = new ServletRequestAttributeEvent(context.getServletContext(), getRequest(), name, oldValue);
        } else {
            event = new ServletRequestAttributeEvent(context.getServletContext(), getRequest(), name, value);
        }

        for (Object o : listeners) {
            if (!(o instanceof ServletRequestAttributeListener)) {
                continue;
            }
            ServletRequestAttributeListener listener = (ServletRequestAttributeListener) o;
            try {
                if (replaced) {
                    listener.attributeReplaced(event);
                } else {
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // Error valve will pick this exception up and display it to user
                attributes.put(RequestDispatcher.ERROR_EXCEPTION, t);
                context.getLogger().error(sm.getString("coyoteRequest.attributeEvent"), t);
            }
        }
    }


    /**
     * Notify interested listeners that attribute has been removed.
     *
     * @param name  Attribute name
     * @param value Attribute value
     */
    private void notifyAttributeRemoved(String name, Object value) {
        Context context = getContext();
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null || listeners.length == 0) {
            return;
        }
        ServletRequestAttributeEvent event =
                new ServletRequestAttributeEvent(context.getServletContext(), getRequest(), name, value);
        for (Object o : listeners) {
            if (!(o instanceof ServletRequestAttributeListener)) {
                continue;
            }
            ServletRequestAttributeListener listener = (ServletRequestAttributeListener) o;
            try {
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // Error valve will pick this exception up and display it to user
                attributes.put(RequestDispatcher.ERROR_EXCEPTION, t);
                context.getLogger().error(sm.getString("coyoteRequest.attributeEvent"), t);
            }
        }
    }


    @Override
    public void setCharacterEncoding(String enc) throws UnsupportedEncodingException {

        if (usingReader) {
            return;
        }

        CharsetHolder charsetHolder = CharsetHolder.getInstance(enc);
        charsetHolder.validate();

        // Save the validated encoding
        coyoteRequest.setCharsetHolder(charsetHolder);
    }


    @Override
    public void setCharacterEncoding(Charset charset) {

        if (usingReader) {
            return;
        }

        // Save the validated encoding
        coyoteRequest.setCharsetHolder(CharsetHolder.getInstance(charset));
    }


    @Override
    public ServletContext getServletContext() {
        return getContext().getServletContext();
    }

    @Override
    public AsyncContext startAsync() {
        return startAsync(getRequest(), response.getResponse());
    }

    @Override
    public AsyncContext startAsync(ServletRequest request, ServletResponse response) {
        if (!isAsyncSupported()) {
            IllegalStateException ise = new IllegalStateException(sm.getString("request.asyncNotSupported"));
            log.warn(sm.getString("coyoteRequest.noAsync", StringUtils.join(getNonAsyncClassNames())), ise);
            throw ise;
        }

        if (asyncContext == null) {
            asyncContext = new AsyncContextImpl(this);
        }

        asyncContext.setStarted(getContext(), request, response,
                request == getRequest() && response == getResponse().getResponse());
        asyncContext.setTimeout(getConnector().getAsyncTimeout());

        return asyncContext;
    }


    private Set<String> getNonAsyncClassNames() {
        Set<String> result = new HashSet<>();

        Wrapper wrapper = getWrapper();
        if (!wrapper.isAsyncSupported()) {
            result.add(wrapper.getServletClass());
        }

        FilterChain filterChain = getFilterChain();
        if (filterChain instanceof ApplicationFilterChain) {
            ((ApplicationFilterChain) filterChain).findNonAsyncFilters(result);
        } else {
            result.add(sm.getString("coyoteRequest.filterAsyncSupportUnknown"));
        }

        Container c = wrapper;
        while (c != null) {
            c.getPipeline().findNonAsyncValves(result);
            c = c.getParent();
        }

        return result;
    }

    @Override
    public boolean isAsyncStarted() {
        if (asyncContext == null) {
            return false;
        }

        return asyncContext.isStarted();
    }

    public boolean isAsyncDispatching() {
        if (asyncContext == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(false);
        coyoteRequest.action(ActionCode.ASYNC_IS_DISPATCHING, result);
        return result.get();
    }

    public boolean isAsyncCompleting() {
        if (asyncContext == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(false);
        coyoteRequest.action(ActionCode.ASYNC_IS_COMPLETING, result);
        return result.get();
    }

    public boolean isAsync() {
        if (asyncContext == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(false);
        coyoteRequest.action(ActionCode.ASYNC_IS_ASYNC, result);
        return result.get();
    }

    @Override
    public boolean isAsyncSupported() {
        if (this.asyncSupported == null) {
            return true;
        }

        return asyncSupported.booleanValue();
    }

    @Override
    public AsyncContext getAsyncContext() {
        if (!isAsyncStarted()) {
            throw new IllegalStateException(sm.getString("request.notAsync"));
        }
        return asyncContext;
    }

    public AsyncContextImpl getAsyncContextInternal() {
        return asyncContext;
    }

    @Override
    public DispatcherType getDispatcherType() {
        if (internalDispatcherType == null) {
            return DispatcherType.REQUEST;
        }

        return this.internalDispatcherType;
    }


    @Override
    public String getRequestId() {
        return coyoteRequest.getRequestId();
    }


    @Override
    public String getProtocolRequestId() {
        return coyoteRequest.getProtocolRequestId();
    }


    @Override
    public ServletConnection getServletConnection() {
        return coyoteRequest.getServletConnection();
    }


    // ---------------------------------------------------- HttpRequest Methods

    /**
     * Add a Cookie to the set of Cookies associated with this Request.
     *
     * @param cookie The new cookie
     */
    public void addCookie(Cookie cookie) {

        if (!cookiesConverted) {
            convertCookies();
        }

        int size = 0;
        if (cookies != null) {
            size = cookies.length;
        }

        Cookie[] newCookies = new Cookie[size + 1];
        if (cookies != null) {
            System.arraycopy(cookies, 0, newCookies, 0, size);
        }
        newCookies[size] = cookie;

        cookies = newCookies;

    }


    /**
     * Add a Locale to the set of preferred Locales for this Request. The first added Locale will be the first one
     * returned by getLocales().
     *
     * @param locale The new preferred Locale
     */
    public void addLocale(Locale locale) {
        locales.add(locale);
    }


    /**
     * Clear the collection of Cookies associated with this Request.
     */
    public void clearCookies() {
        cookiesParsed = true;
        cookiesConverted = true;
        cookies = null;
    }


    /**
     * Clear the collection of Locales associated with this Request.
     */
    public void clearLocales() {
        locales.clear();
    }


    /**
     * Set the authentication type used for this request, if any; otherwise set the type to <code>null</code>. Typical
     * values are "BASIC", "DIGEST", or "SSL".
     *
     * @param type The authentication type used
     */
    public void setAuthType(String type) {
        this.authType = type;
    }


    /**
     * Set the path information for this Request. This will normally be called when the associated Context is mapping
     * the Request to a particular Wrapper.
     *
     * @param path The path information
     */
    public void setPathInfo(String path) {
        mappingData.pathInfo.setString(path);
    }


    /**
     * Set a flag indicating whether or not the requested session ID for this request came in through a cookie. This is
     * normally called by the HTTP Connector, when it parses the request headers.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionCookie(boolean flag) {

        this.requestedSessionCookie = flag;

    }


    /**
     * Set the requested session ID for this request. This is normally called by the HTTP Connector, when it parses the
     * request headers.
     *
     * @param id The new session id
     */
    public void setRequestedSessionId(String id) {

        this.requestedSessionId = id;

    }


    /**
     * Set a flag indicating whether or not the requested session ID for this request came in through a URL. This is
     * normally called by the HTTP Connector, when it parses the request headers.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionURL(boolean flag) {

        this.requestedSessionURL = flag;

    }


    /**
     * Set a flag indicating whether or not the requested session ID for this request came in through SSL. This is
     * normally called by the HTTP Connector, when it parses the request headers.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionSSL(boolean flag) {

        this.requestedSessionSSL = flag;

    }


    /**
     * Get the decoded request URI.
     *
     * @return the URL decoded request URI
     */
    public String getDecodedRequestURI() {
        return coyoteRequest.decodedURI().toString();
    }


    /**
     * Get the decoded request URI.
     *
     * @return the URL decoded request URI
     */
    public MessageBytes getDecodedRequestURIMB() {
        return coyoteRequest.decodedURI();
    }


    /**
     * Set the Principal who has been authenticated for this Request. This value is also used to calculate the value to
     * be returned by the <code>getRemoteUser()</code> method.
     *
     * @param principal The user Principal
     */
    public void setUserPrincipal(final Principal principal) {
        userPrincipal = principal;
    }


    // --------------------------------------------- HttpServletRequest Methods

    @Override
    public boolean isTrailerFieldsReady() {
        return coyoteRequest.isTrailerFieldsReady();
    }


    @Override
    public Map<String,String> getTrailerFields() {
        if (!isTrailerFieldsReady()) {
            throw new IllegalStateException(sm.getString("coyoteRequest.trailersNotReady"));
        }
        // No need for a defensive copy since a new Map is returned for every call.
        return coyoteRequest.getTrailerFields();
    }


    @SuppressWarnings("unchecked")
    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> httpUpgradeHandlerClass)
            throws IOException, ServletException {
        T handler;
        InstanceManager instanceManager = null;
        try {
            // Do not go through the instance manager for internal Tomcat classes since they don't
            // need injection
            if (InternalHttpUpgradeHandler.class.isAssignableFrom(httpUpgradeHandlerClass)) {
                handler = httpUpgradeHandlerClass.getConstructor().newInstance();
            } else {
                instanceManager = getContext().getInstanceManager();
                handler = (T) instanceManager.newInstance(httpUpgradeHandlerClass);
            }
        } catch (ReflectiveOperationException | NamingException | IllegalArgumentException | SecurityException e) {
            throw new ServletException(e);
        }
        UpgradeToken upgradeToken = new UpgradeToken(handler, getContext(), instanceManager,
                getUpgradeProtocolName(httpUpgradeHandlerClass));

        coyoteRequest.action(ActionCode.UPGRADE, upgradeToken);

        // Output required by RFC2616. Protocol specific headers should have
        // already been set.
        response.setStatus(HttpServletResponse.SC_SWITCHING_PROTOCOLS);

        return handler;
    }


    private String getUpgradeProtocolName(Class<? extends HttpUpgradeHandler> httpUpgradeHandlerClass) {
        // Ideal - the caller has already explicitly set the selected protocol
        // on the response
        String result = response.getHeader(HTTP_UPGRADE_HEADER_NAME);

        if (result == null) {
            // If the request's upgrade header contains a single protocol that
            // is the protocol that must have been selected
            List<Upgrade> upgradeProtocols = Upgrade.parse(getHeaders(HTTP_UPGRADE_HEADER_NAME));
            if (upgradeProtocols != null && upgradeProtocols.size() == 1) {
                result = upgradeProtocols.get(0).toString();
            }
        }

        if (result == null) {
            // Ugly but use the class name - it is better than nothing
            result = httpUpgradeHandlerClass.getName();
        }
        return result;
    }


    @Override
    public String getAuthType() {
        return authType;
    }


    @Override
    public String getContextPath() {
        int lastSlash = mappingData.contextSlashCount;
        // Special case handling for the root context
        if (lastSlash == 0) {
            return "";
        }

        String canonicalContextPath = getServletContext().getContextPath();

        String uri = getRequestURI();
        int pos = 0;
        if (!getContext().getAllowMultipleLeadingForwardSlashInPath()) {
            // Ensure that the returned value only starts with a single '/'.
            // This prevents the value being misinterpreted as a protocol-
            // relative URI if used with sendRedirect().
            do {
                pos++;
            } while (pos < uri.length() && uri.charAt(pos) == '/');
            pos--;
            uri = uri.substring(pos);
        }

        char[] uriChars = uri.toCharArray();
        // Need at least the number of slashes in the context path
        while (lastSlash > 0) {
            pos = nextSlash(uriChars, pos + 1);
            if (pos == -1) {
                break;
            }
            lastSlash--;
        }
        // Now allow for path parameters, normalization and/or encoding.
        // Essentially, keep extending the candidate path up to the next slash
        // until the decoded and normalized candidate path (with the path
        // parameters removed) is the same as the canonical path.
        String candidate;
        if (pos == -1) {
            candidate = uri;
        } else {
            candidate = uri.substring(0, pos);
        }
        candidate = removePathParameters(candidate);
        candidate = UDecoder.URLDecode(candidate, connector.getURICharset());
        candidate = org.apache.tomcat.util.http.RequestUtil.normalize(candidate);
        boolean match = canonicalContextPath.equals(candidate);
        while (!match && pos != -1) {
            pos = nextSlash(uriChars, pos + 1);
            if (pos == -1) {
                candidate = uri;
            } else {
                candidate = uri.substring(0, pos);
            }
            candidate = removePathParameters(candidate);
            candidate = UDecoder.URLDecode(candidate, connector.getURICharset());
            candidate = org.apache.tomcat.util.http.RequestUtil.normalize(candidate);
            match = canonicalContextPath.equals(candidate);
        }
        if (match) {
            if (pos == -1) {
                return uri;
            } else {
                return uri.substring(0, pos);
            }
        } else {
            // Should never happen
            throw new IllegalStateException(
                    sm.getString("coyoteRequest.getContextPath.ise", canonicalContextPath, uri));
        }
    }


    private String removePathParameters(String input) {
        int nextSemiColon = input.indexOf(';');
        // Shortcut
        if (nextSemiColon == -1) {
            return input;
        }
        StringBuilder result = new StringBuilder(input.length());
        result.append(input.substring(0, nextSemiColon));
        while (true) {
            int nextSlash = input.indexOf('/', nextSemiColon);
            if (nextSlash == -1) {
                break;
            }
            nextSemiColon = input.indexOf(';', nextSlash);
            if (nextSemiColon == -1) {
                result.append(input.substring(nextSlash));
                break;
            } else {
                result.append(input.substring(nextSlash, nextSemiColon));
            }
        }

        return result.toString();
    }


    private int nextSlash(char[] uri, int startPos) {
        int len = uri.length;
        int pos = startPos;
        while (pos < len) {
            if (uri[pos] == '/') {
                return pos;
            } else if (connector.getEncodedSolidusHandlingInternal() == EncodedSolidusHandling.DECODE &&
                    uri[pos] == '%' && pos + 2 < len && uri[pos + 1] == '2' &&
                    (uri[pos + 2] == 'f' || uri[pos + 2] == 'F')) {
                return pos;
            }
            pos++;
        }
        return -1;
    }


    @Override
    public Cookie[] getCookies() {
        if (!cookiesConverted) {
            convertCookies();
        }
        return cookies;
    }


    /**
     * Return the server representation of the cookies associated with this request. Triggers parsing of the Cookie HTTP
     * headers (but not conversion to Cookie objects) if the headers have not yet been parsed.
     *
     * @return the server cookies
     */
    public ServerCookies getServerCookies() {
        parseCookies();
        return coyoteRequest.getCookies();
    }


    @Override
    public long getDateHeader(String name) {

        String value = getHeader(name);
        if (value == null) {
            return -1L;
        }

        // Attempt to convert the date header in a variety of formats
        long result = FastHttpDateFormat.parseDate(value);
        if (result != (-1L)) {
            return result;
        }
        throw new IllegalArgumentException(value);

    }


    @Override
    public String getHeader(String name) {
        return coyoteRequest.getHeader(name);
    }


    @Override
    public Enumeration<String> getHeaders(String name) {
        return coyoteRequest.getMimeHeaders().values(name);
    }


    @Override
    public Enumeration<String> getHeaderNames() {
        return coyoteRequest.getMimeHeaders().names();
    }


    @Override
    public int getIntHeader(String name) {

        String value = getHeader(name);
        if (value == null) {
            return -1;
        }

        return Integer.parseInt(value);
    }


    @Override
    public HttpServletMapping getHttpServletMapping() {
        return applicationMapping.getHttpServletMapping();
    }


    @Override
    public String getMethod() {
        return coyoteRequest.method().toStringType();
    }


    @Override
    public String getPathInfo() {
        return mappingData.pathInfo.toStringType();
    }


    @Override
    public String getPathTranslated() {

        Context context = getContext();
        if (context == null) {
            return null;
        }

        if (getPathInfo() == null) {
            return null;
        }

        return context.getServletContext().getRealPath(getPathInfo());
    }


    @Override
    public String getQueryString() {
        return coyoteRequest.queryString().toString();
    }


    @Override
    public String getRemoteUser() {

        if (userPrincipal == null) {
            return null;
        }

        return userPrincipal.getName();
    }


    /**
     * Get the request path.
     *
     * @return the request path
     */
    public MessageBytes getRequestPathMB() {
        return mappingData.requestPath;
    }


    @Override
    public String getRequestedSessionId() {
        return requestedSessionId;
    }


    @Override
    public String getRequestURI() {
        return coyoteRequest.requestURI().toStringType();
    }


    @Override
    public StringBuffer getRequestURL() {
        return RequestUtil.getRequestURL(this);
    }


    @Override
    public String getServletPath() {
        return mappingData.wrapperPath.toStringType();
    }


    @Override
    public HttpSession getSession() {
        return getSession(true);
    }


    @Override
    public HttpSession getSession(boolean create) {
        Session session = doGetSession(create);
        if (session == null) {
            return null;
        }

        return session.getSession();
    }


    @Override
    public boolean isRequestedSessionIdFromCookie() {

        if (requestedSessionId == null) {
            return false;
        }

        return requestedSessionCookie;
    }


    @Override
    public boolean isRequestedSessionIdFromURL() {

        if (requestedSessionId == null) {
            return false;
        }

        return requestedSessionURL;
    }


    @Override
    public boolean isRequestedSessionIdValid() {

        if (requestedSessionId == null) {
            return false;
        }

        Context context = getContext();
        if (context == null) {
            return false;
        }

        /*
         * As per PR #594, the manager could be provided by the web application and calls to findSession() could trigger
         * class loading so set the thread context class loader appropriately to avoid ClassNotFoundException.
         */
        ClassLoader originalClassLoader = context.bind(null);
        try {
            Manager manager = context.getManager();
            if (manager == null) {
                return false;
            }

            Session session = null;
            try {
                session = manager.findSession(requestedSessionId);
            } catch (IOException e) {
                // Can't find the session
            }

            if ((session == null) || !session.isValid()) {
                // Check for parallel deployment contexts
                if (getMappingData().contexts == null) {
                    return false;
                } else {
                    for (int i = (getMappingData().contexts.length); i > 0; i--) {
                        Context ctxt = getMappingData().contexts[i - 1];
                        try {
                            if (ctxt.getManager().findSession(requestedSessionId) != null) {
                                return true;
                            }
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                    return false;
                }
            }

            return true;
        } finally {
            context.unbind(originalClassLoader);
        }
    }


    @Override
    public boolean isUserInRole(String role) {

        // Have we got an authenticated principal at all?
        if (userPrincipal == null) {
            return false;
        }

        // Identify the Realm we will use for checking role assignments
        Context context = getContext();
        if (context == null) {
            return false;
        }

        // If the role is "*" then the return value must be false
        // Servlet 31, section 13.3
        if ("*".equals(role)) {
            return false;
        }

        // If the role is "**" then, unless the application defines a role with
        // that name, only check if the user is authenticated
        if ("**".equals(role) && !context.findSecurityRole("**")) {
            return userPrincipal != null;
        }

        Realm realm = context.getRealm();
        if (realm == null) {
            return false;
        }

        // Check for a role defined directly as a <security-role>
        return realm.hasRole(getWrapper(), userPrincipal, role);
    }


    /**
     * @return the principal that has been authenticated for this Request.
     */
    public Principal getPrincipal() {
        return userPrincipal;
    }


    @Override
    public Principal getUserPrincipal() {
        if (userPrincipal instanceof TomcatPrincipal) {
            GSSCredential gssCredential = ((TomcatPrincipal) userPrincipal).getGssCredential();
            if (gssCredential != null) {
                int left = -1;
                try {
                    // Concurrent calls to this method from an expired session
                    // can trigger an ISE. If one thread calls logout() below
                    // before another thread calls getRemainingLifetime() then
                    // then since logout() eventually calls
                    // GSSCredential.dispose(), the subsequent call to
                    // GSSCredential.getRemainingLifetime() will throw an ISE.
                    // Avoiding the ISE would require locking in this method to
                    // protect against concurrent access to the GSSCredential.
                    // That would have a small performance impact. The ISE is
                    // rare so it is caught and handled rather than avoided.
                    left = gssCredential.getRemainingLifetime();
                } catch (GSSException | IllegalStateException e) {
                    log.warn(sm.getString("coyoteRequest.gssLifetimeFail", userPrincipal.getName()), e);
                }
                // zero is expired. Exception above will mean left == -1
                // Treat both as expired.
                if (left <= 0) {
                    // GSS credential has expired. Need to re-authenticate.
                    try {
                        logout();
                    } catch (ServletException e) {
                        // Should never happen (no code called by logout()
                        // throws a ServletException
                    }
                    return null;
                }
            }
            return ((TomcatPrincipal) userPrincipal).getUserPrincipal();
        }

        return userPrincipal;
    }


    /**
     * @return the session associated with this Request, creating one if necessary.
     */
    public Session getSessionInternal() {
        return doGetSession(true);
    }


    /**
     * Change the ID of the session that this request is associated with. There are several things that may trigger an
     * ID change. These include moving between nodes in a cluster and session fixation prevention during the
     * authentication process.
     *
     * @param newSessionId The session to change the session ID for
     */
    public void changeSessionId(String newSessionId) {
        // This should only ever be called if there was an old session ID but
        // double check to be sure
        if (requestedSessionId != null && requestedSessionId.length() > 0) {
            requestedSessionId = newSessionId;
        }

        Context context = getContext();
        if (context != null &&
                !context.getServletContext().getEffectiveSessionTrackingModes().contains(SessionTrackingMode.COOKIE)) {
            return;
        }

        if (response != null) {
            Cookie newCookie = ApplicationSessionCookieConfig.createSessionCookie(context, newSessionId, isSecure());
            response.addSessionCookieInternal(newCookie);
        }
    }


    @Override
    public String changeSessionId() {

        Session session = this.getSessionInternal(false);
        if (session == null) {
            throw new IllegalStateException(sm.getString("coyoteRequest.changeSessionId"));
        }

        Manager manager = this.getContext().getManager();

        String newSessionId = manager.rotateSessionId(session);
        this.changeSessionId(newSessionId);

        return newSessionId;
    }

    /**
     * @return the session associated with this Request, creating one if necessary and requested.
     *
     * @param create Create a new session if one does not exist
     */
    public Session getSessionInternal(boolean create) {
        return doGetSession(create);
    }


    /**
     * @return <code>true</code> if we have parsed parameters
     */
    public boolean isParametersParsed() {
        return parametersParsed;
    }


    /**
     * @return <code>true</code> if an attempt has been made to read the request body and all of the request body has
     *             been read.
     */
    public boolean isFinished() {
        return coyoteRequest.isFinished();
    }


    /**
     * Check the configuration for aborted uploads and if configured to do so, disable the swallowing of any remaining
     * input and close the connection once the response has been written.
     */
    protected void checkSwallowInput() {
        Context context = getContext();
        if (context != null && !context.getSwallowAbortedUploads()) {
            coyoteRequest.action(ActionCode.DISABLE_SWALLOW_INPUT, null);
        }
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        if (response.isCommitted()) {
            throw new IllegalStateException(sm.getString("coyoteRequest.authenticate.ise"));
        }

        return getContext().getAuthenticator().authenticate(this, response);
    }

    @Override
    public void login(String username, String password) throws ServletException {
        if (getAuthType() != null || getRemoteUser() != null || getUserPrincipal() != null) {
            throw new ServletException(sm.getString("coyoteRequest.alreadyAuthenticated"));
        }

        getContext().getAuthenticator().login(username, password, this);
    }

    @Override
    public void logout() throws ServletException {
        getContext().getAuthenticator().logout(this);
    }


    @Override
    public Collection<Part> getParts() throws IOException, IllegalStateException, ServletException {

        parseParts();

        if (partsParseException != null) {
            if (partsParseException instanceof IOException) {
                throw (IOException) partsParseException;
            } else if (partsParseException instanceof IllegalStateException) {
                throw (IllegalStateException) partsParseException;
            } else if (partsParseException instanceof ServletException) {
                throw (ServletException) partsParseException;
            }
        }

        return parts;
    }


    private void parseParts() {

        // Return immediately if the parts have already been parsed
        if (parts != null || partsParseException != null) {
            return;
        }

        Context context = getContext();
        MultipartConfigElement mce = getWrapper().getMultipartConfigElement();

        if (mce == null) {
            if (context.getAllowCasualMultipartParsing()) {
                mce = new MultipartConfigElement(null, connector.getMaxPostSize(), connector.getMaxPostSize(),
                        connector.getMaxPostSize());
            } else {
                partsParseException = new IllegalStateException(sm.getString("coyoteRequest.noMultipartConfig"));
                return;
            }
        }

        int maxParameterCount = getConnector().getMaxParameterCount();
        Parameters parameters = coyoteRequest.getParameters();
        parameters.setLimit(maxParameterCount);

        File location;
        String locationStr = mce.getLocation();
        if (locationStr == null || locationStr.length() == 0) {
            location = ((File) context.getServletContext().getAttribute(ServletContext.TEMPDIR));
        } else {
            // If relative, it is relative to TEMPDIR
            location = new File(locationStr);
            if (!location.isAbsolute()) {
                location =
                        new File((File) context.getServletContext().getAttribute(ServletContext.TEMPDIR), locationStr)
                                .getAbsoluteFile();
            }
        }

        if (!location.exists() && context.getCreateUploadTargets()) {
            log.warn(sm.getString("coyoteRequest.uploadCreate", location.getAbsolutePath(),
                    getMappingData().wrapper.getName()));
            if (!location.mkdirs()) {
                log.warn(sm.getString("coyoteRequest.uploadCreateFail", location.getAbsolutePath()));
            }
        }

        if (!location.isDirectory()) {
            partsParseException = new IOException(sm.getString("coyoteRequest.uploadLocationInvalid", location));
            return;
        }

        // Create a new file upload handler
        DiskFileItemFactory factory = new DiskFileItemFactory();
        try {
            factory.setRepository(location.getCanonicalFile());
        } catch (IOException ioe) {
            partsParseException = ioe;
            return;
        }
        factory.setSizeThreshold(mce.getFileSizeThreshold());

        FileUpload upload = new FileUpload();
        upload.setFileItemFactory(factory);
        upload.setFileSizeMax(mce.getMaxFileSize());
        upload.setSizeMax(mce.getMaxRequestSize());
        if (maxParameterCount > -1) {
            // There is a limit. The limit for parts needs to be reduced by
            // the number of parameters we have already parsed.
            // Must be under the limit else parsing parameters would have
            // triggered an exception.
            upload.setFileCountMax(maxParameterCount - parameters.size());
        }

        parts = new ArrayList<>();
        try {
            List<FileItem> items = upload.parseRequest(new ServletRequestContext(this));
            int maxPostSize = getConnector().getMaxPostSize();
            int postSize = 0;
            Charset charset = getCharset();
            for (FileItem item : items) {
                ApplicationPart part = new ApplicationPart(item, location);
                parts.add(part);
                if (part.getSubmittedFileName() == null) {
                    String name = part.getName();
                    if (maxPostSize >= 0) {
                        // Have to calculate equivalent size. Not completely
                        // accurate but close enough.
                        postSize += name.getBytes(charset).length;
                        // Equals sign
                        postSize++;
                        // Value length
                        postSize += part.getSize();
                        // Value separator
                        postSize++;
                        if (postSize > maxPostSize) {
                            throw new IllegalStateException(sm.getString("coyoteRequest.maxPostSizeExceeded"));
                        }
                    }
                    String value = null;
                    try {
                        value = part.getString(charset.name());
                    } catch (UnsupportedEncodingException uee) {
                        // Not possible
                    }
                    parameters.addParameter(name, value);
                }
            }
        } catch (InvalidContentTypeException e) {
            partsParseException = new ServletException(e);
            return;
        } catch (SizeException e) {
            checkSwallowInput();
            partsParseException = new InvalidParameterException(e, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        } catch (IOException e) {
            partsParseException = e;
            return;
        } catch (IllegalStateException e) {
            checkSwallowInput();
            partsParseException = e;
            return;
        }
    }


    @Override
    public Part getPart(String name) throws IOException, IllegalStateException, ServletException {
        for (Part part : getParts()) {
            if (name.equals(part.getName())) {
                return part;
            }
        }
        return null;
    }


    // ------------------------------------------------------ Protected Methods

    protected Session doGetSession(boolean create) {

        // There cannot be a session if no context has been assigned yet
        Context context = getContext();
        if (context == null) {
            return null;
        }

        // Return the current session if it exists and is valid
        if ((session != null) && !session.isValid()) {
            session = null;
        }
        if (session != null) {
            return session;
        }

        // Return the requested session if it exists and is valid
        Manager manager = context.getManager();
        if (manager == null) {
            return null; // Sessions are not supported
        }
        if (requestedSessionId != null) {
            try {
                session = manager.findSession(requestedSessionId);
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("request.session.failed", requestedSessionId, e.getMessage()), e);
                } else {
                    log.info(sm.getString("request.session.failed", requestedSessionId, e.getMessage()));
                }
                session = null;
            }
            if ((session != null) && !session.isValid()) {
                session = null;
            }
            if (session != null) {
                session.access();
                return session;
            }
        }

        // Create a new session if requested and the response is not committed
        if (!create) {
            return null;
        }
        boolean trackModesIncludesCookie =
                context.getServletContext().getEffectiveSessionTrackingModes().contains(SessionTrackingMode.COOKIE);
        if (trackModesIncludesCookie && response.getResponse().isCommitted()) {
            throw new IllegalStateException(sm.getString("coyoteRequest.sessionCreateCommitted"));
        }

        // Re-use session IDs provided by the client in very limited
        // circumstances.
        String sessionId = getRequestedSessionId();
        if (requestedSessionSSL) {
            // If the session ID has been obtained from the SSL handshake then
            // use it.
        } else if (("/".equals(context.getSessionCookiePath()) && isRequestedSessionIdFromCookie())) {
            /*
             * This is the common(ish) use case: using the same session ID with multiple web applications on the same
             * host. Typically this is used by Portlet implementations. It only works if sessions are tracked via
             * cookies. The cookie must have a path of "/" else it won't be provided for requests to all web
             * applications.
             *
             * Any session ID provided by the client should be for a session that already exists somewhere on the host.
             * Check if the context is configured for this to be confirmed.
             */
            if (context.getValidateClientProvidedNewSessionId()) {
                boolean found = false;
                for (Container container : getHost().findChildren()) {
                    Manager m = ((Context) container).getManager();
                    if (m != null) {
                        try {
                            if (m.findSession(sessionId) != null) {
                                found = true;
                                break;
                            }
                        } catch (IOException e) {
                            // Ignore. Problems with this manager will be
                            // handled elsewhere.
                        }
                    }
                }
                if (!found) {
                    sessionId = null;
                }
            }
        } else {
            sessionId = null;
        }
        session = manager.createSession(sessionId);

        // Creating a new session cookie based on that session
        if (session != null && trackModesIncludesCookie) {
            Cookie cookie =
                    ApplicationSessionCookieConfig.createSessionCookie(context, session.getIdInternal(), isSecure());

            response.addSessionCookieInternal(cookie);
        }

        if (session == null) {
            return null;
        }

        session.access();
        return session;
    }

    protected String unescape(String s) {
        if (s == null) {
            return null;
        }
        if (s.indexOf('\\') == -1) {
            return s;
        }
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                buf.append(c);
            } else {
                if (++i >= s.length()) {
                    throw new IllegalArgumentException();// invalid escape, hence invalid cookie
                }
                c = s.charAt(i);
                buf.append(c);
            }
        }
        return buf.toString();
    }

    private CookieProcessor getCookieProcessor() {
        Context context = getContext();
        if (context == null) {
            // No context. Possible call from Valve before a Host level
            // context rewrite when no ROOT content is configured. Use the
            // default CookieProcessor.
            return new Rfc6265CookieProcessor();
        } else {
            return context.getCookieProcessor();
        }
    }

    /**
     * Parse cookies. This only parses the cookies into the memory efficient ServerCookies structure. It does not
     * populate the Cookie objects.
     */
    protected void parseCookies() {
        if (cookiesParsed) {
            return;
        }

        cookiesParsed = true;

        ServerCookies serverCookies = coyoteRequest.getCookies();
        serverCookies.setLimit(connector.getMaxCookieCount());
        getCookieProcessor().parseCookieHeader(coyoteRequest.getMimeHeaders(), serverCookies);
    }

    /**
     * Converts the parsed cookies (parsing the Cookie headers first if they have not been parsed) into Cookie objects.
     */
    protected void convertCookies() {
        if (cookiesConverted) {
            return;
        }

        cookiesConverted = true;

        parseCookies();

        ServerCookies serverCookies = coyoteRequest.getCookies();

        int count = serverCookies.getCookieCount();
        if (count <= 0) {
            return;
        }

        cookies = new Cookie[count];

        int idx = 0;
        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            try {
                // We must unescape the '\\' escape character
                Cookie cookie = new Cookie(scookie.getName().toString(), null);
                scookie.getValue().getByteChunk().setCharset(getCookieProcessor().getCharset());
                cookie.setValue(unescape(scookie.getValue().toString()));
                cookies[idx++] = cookie;
            } catch (IllegalArgumentException e) {
                // Ignore bad cookie
            }
        }
        if (idx < count) {
            Cookie[] ncookies = new Cookie[idx];
            System.arraycopy(cookies, 0, ncookies, 0, idx);
            cookies = ncookies;
        }
    }


    /**
     * Parse request parameters.
     */
    protected void parseParameters() {
        doParseParameters();

        if (parametersParseException != null) {
            throw parametersParseException;
        }
    }


    protected void doParseParameters() {
        if (parametersParsed) {
            return;
        }
        parametersParsed = true;

        Parameters parameters = coyoteRequest.getParameters();

        // Set this every time in case limit has been changed via JMX
        int maxParameterCount = getConnector().getMaxParameterCount();
        if (parts != null && maxParameterCount > 0) {
            maxParameterCount -= parts.size();
        }
        parameters.setLimit(maxParameterCount);

        // getCharacterEncoding() may have been overridden to search for
        // hidden form field containing request encoding
        Charset charset = getCharset();

        boolean useBodyEncodingForURI = connector.getUseBodyEncodingForURI();
        parameters.setCharset(charset);
        if (useBodyEncodingForURI) {
            parameters.setQueryStringCharset(charset);
        }
        // Note: If !useBodyEncodingForURI, the query string encoding is
        // that set towards the start of CoyoteAdapter.service()

        parameters.handleQueryParameters();

        if (usingInputStream || usingReader) {
            return;
        }

        String contentType = getContentType();
        if (contentType == null) {
            contentType = "";
        }
        int semicolon = contentType.indexOf(';');
        if (semicolon >= 0) {
            contentType = contentType.substring(0, semicolon).trim();
        } else {
            contentType = contentType.trim();
        }

        if ("multipart/form-data".equals(contentType)) {
            parseParts();
            if (partsParseException instanceof IllegalStateException) {
                parametersParseException = (IllegalStateException) partsParseException;
            } else if (partsParseException != null) {
                parametersParseException = new InvalidParameterException(partsParseException);
            }
            return;
        }

        if (!getConnector().isParseBodyMethod(getMethod())) {
            return;
        }

        if (!("application/x-www-form-urlencoded".equals(contentType))) {
            return;
        }

        int len = getContentLength();

        if (len > 0) {
            int maxPostSize = connector.getMaxPostSize();
            if ((maxPostSize >= 0) && (len > maxPostSize)) {
                String message = sm.getString("coyoteRequest.postTooLarge");
                Context context = getContext();
                if (context != null && context.getLogger().isDebugEnabled()) {
                    context.getLogger().debug(message);
                }
                checkSwallowInput();
                parametersParseException =
                        new InvalidParameterException(message, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return;
            }
            byte[] formData = null;
            if (len < CACHED_POST_LEN) {
                if (postData == null) {
                    postData = new byte[CACHED_POST_LEN];
                }
                formData = postData;
            } else {
                formData = new byte[len];
            }
            try {
                readPostBodyFully(formData, len);
            } catch (IOException e) {
                Context context = getContext();
                if (context != null && context.getLogger().isDebugEnabled()) {
                    context.getLogger().debug(sm.getString("coyoteRequest.parseParameters"), e);
                }
                if (e instanceof ClientAbortException) {
                    // Client has disconnected. Close immediately.
                    response.getCoyoteResponse().action(ActionCode.CLOSE_NOW, null);
                }
                if (e instanceof BadRequestException) {
                    parametersParseException = new InvalidParameterException(e);
                } else {
                    parametersParseException = new InvalidParameterException(new BadRequestException(e));
                }
                return;
            }
            parameters.processParameters(formData, 0, len);
        } else if ("chunked".equalsIgnoreCase(coyoteRequest.getHeader("transfer-encoding"))) {
            byte[] formData = null;
            try {
                formData = readChunkedPostBody();
            } catch (IllegalStateException ise) {
                parametersParseException = ise;
                return;
            } catch (IOException e) {
                Context context = getContext();
                if (context != null && context.getLogger().isDebugEnabled()) {
                    context.getLogger().debug(sm.getString("coyoteRequest.parseParameters"), e);
                }
                if (e instanceof ClientAbortException) {
                    // Client has disconnected. Close immediately.
                    response.getCoyoteResponse().action(ActionCode.CLOSE_NOW, null);
                }
                if (e instanceof BadRequestException) {
                    parametersParseException = new InvalidParameterException(e);
                } else {
                    parametersParseException = new InvalidParameterException(new BadRequestException(e));
                }
                return;
            }
            if (formData != null) {
                parameters.processParameters(formData, 0, formData.length);
            }
        }
    }


    /**
     * Read post body into an array.
     *
     * @param body The bytes array in which the body will be read
     * @param len  The body length
     *
     * @throws IOException if an IO exception occurred or EOF is reached before the body has been fully read
     */
    protected void readPostBodyFully(byte[] body, int len) throws IOException {
        int offset = 0;
        do {
            int inputLen = getStream().read(body, offset, len - offset);
            if (inputLen <= 0) {
                throw new EOFException();
            }
            offset += inputLen;
        } while ((len - offset) > 0);
    }


    /**
     * Read chunked post body.
     *
     * @return the post body as a bytes array
     *
     * @throws IOException if an IO exception occurred
     */
    protected byte[] readChunkedPostBody() throws IOException {
        ByteChunk body = new ByteChunk();

        byte[] buffer = new byte[CACHED_POST_LEN];

        int len = 0;
        while (len > -1) {
            len = getStream().read(buffer, 0, CACHED_POST_LEN);
            if (connector.getMaxPostSize() >= 0 && (body.getLength() + len) > connector.getMaxPostSize()) {
                // Too much data
                checkSwallowInput();
                throw new InvalidParameterException(sm.getString("coyoteRequest.chunkedPostTooLarge"),
                        HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            }
            if (len > 0) {
                body.append(buffer, 0, len);
            }
        }
        if (body.getLength() == 0) {
            return null;
        }
        if (body.getLength() < body.getBuffer().length) {
            int length = body.getLength();
            byte[] result = new byte[length];
            System.arraycopy(body.getBuffer(), 0, result, 0, length);
            return result;
        }

        return body.getBuffer();
    }


    /**
     * Parse request locales.
     */
    protected void parseLocales() {

        localesParsed = true;

        // Store the accumulated languages that have been requested in
        // a local collection, sorted by the quality value (so we can
        // add Locales in descending order). The values will be ArrayLists
        // containing the corresponding Locales to be added
        TreeMap<Double,ArrayList<Locale>> locales = new TreeMap<>();

        Enumeration<String> values = getHeaders("accept-language");

        while (values.hasMoreElements()) {
            String value = values.nextElement();
            parseLocalesHeader(value, locales);
        }

        // Process the quality values in highest->lowest order (due to
        // negating the Double value when creating the key)
        for (ArrayList<Locale> list : locales.values()) {
            for (Locale locale : list) {
                addLocale(locale);
            }
        }
    }


    /**
     * Parse accept-language header value.
     *
     * @param value   the header value
     * @param locales the map that will hold the result
     */
    protected void parseLocalesHeader(String value, TreeMap<Double,ArrayList<Locale>> locales) {

        List<AcceptLanguage> acceptLanguages;
        try {
            acceptLanguages = AcceptLanguage.parse(new StringReader(value));
        } catch (IOException e) {
            // Mal-formed headers are ignore. Do the same in the unlikely event
            // of an IOException.
            return;
        }

        for (AcceptLanguage acceptLanguage : acceptLanguages) {
            // Add a new Locale to the list of Locales for this quality level
            Double key = Double.valueOf(-acceptLanguage.getQuality()); // Reverse the order
            locales.computeIfAbsent(key, k -> new ArrayList<>()).add(acceptLanguage.getLocale());
        }
    }


    // ----------------------------------------------------- Special attributes handling

    private interface SpecialAttributeAdapter {
        Object get(Request request, String name);

        void set(Request request, String name, Object value);

        // None of special attributes support removal
        // void remove(Request request, String name);
    }

    private static final Map<String,SpecialAttributeAdapter> specialAttributes = new HashMap<>();

    static {
        specialAttributes.put(Globals.DISPATCHER_TYPE_ATTR, new SpecialAttributeAdapter() {
            @Override
            public Object get(Request request, String name) {
                return (request.internalDispatcherType == null) ? DispatcherType.REQUEST :
                        request.internalDispatcherType;
            }

            @Override
            public void set(Request request, String name, Object value) {
                request.internalDispatcherType = (DispatcherType) value;
            }
        });
        specialAttributes.put(Globals.DISPATCHER_REQUEST_PATH_ATTR, new SpecialAttributeAdapter() {
            @Override
            public Object get(Request request, String name) {
                return (request.requestDispatcherPath == null) ? request.getRequestPathMB().toString() :
                        request.requestDispatcherPath.toString();
            }

            @Override
            public void set(Request request, String name, Object value) {
                request.requestDispatcherPath = value;
            }
        });
        specialAttributes.put(Globals.ASYNC_SUPPORTED_ATTR, new SpecialAttributeAdapter() {
            @Override
            public Object get(Request request, String name) {
                return request.asyncSupported;
            }

            @Override
            public void set(Request request, String name, Object value) {
                Boolean oldValue = request.asyncSupported;
                request.asyncSupported = (Boolean) value;
                request.notifyAttributeAssigned(name, value, oldValue);
            }
        });
        specialAttributes.put(Globals.GSS_CREDENTIAL_ATTR, new SpecialAttributeAdapter() {
            @Override
            public Object get(Request request, String name) {
                if (request.userPrincipal instanceof TomcatPrincipal) {
                    return ((TomcatPrincipal) request.userPrincipal).getGssCredential();
                }
                return null;
            }

            @Override
            public void set(Request request, String name, Object value) {
                // NO-OP
            }
        });
        specialAttributes.put(Globals.SENDFILE_SUPPORTED_ATTR, new SpecialAttributeAdapter() {
            @Override
            public Object get(Request request, String name) {
                return Boolean.valueOf(request.getConnector().getProtocolHandler().isSendfileSupported() &&
                        request.getCoyoteRequest().getSendfile());
            }

            @Override
            public void set(Request request, String name, Object value) {
                // NO-OP
            }
        });
        specialAttributes.put(Globals.REMOTE_IP_FILTER_SECURE, new SpecialAttributeAdapter() {
            @Override
            public Object get(Request request, String name) {
                return Boolean.valueOf(request.isSecure());
            }

            @Override
            public void set(Request request, String name, Object value) {
                if (value instanceof Boolean) {
                    request.setSecure(((Boolean) value).booleanValue());
                }
            }
        });
    }
}

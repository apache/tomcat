/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.servlet.http;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.GenericServlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;


/**
 * Provides an abstract class to be subclassed to create
 * an HTTP servlet suitable for a Web site. A subclass of
 * <code>HttpServlet</code> must override at least
 * one method, usually one of these:
 *
 * <ul>
 * <li> <code>doGet</code>, if the servlet supports HTTP GET requests
 * <li> <code>doPost</code>, for HTTP POST requests
 * <li> <code>doPut</code>, for HTTP PUT requests
 * <li> <code>doDelete</code>, for HTTP DELETE requests
 * <li> <code>init</code> and <code>destroy</code>,
 * to manage resources that are held for the life of the servlet
 * <li> <code>getServletInfo</code>, which the servlet uses to
 * provide information about itself
 * </ul>
 *
 * <p>There's almost no reason to override the <code>service</code>
 * method. <code>service</code> handles standard HTTP
 * requests by dispatching them to the handler methods
 * for each HTTP request type (the <code>do</code><i>Method</i>
 * methods listed above).
 *
 * <p>Likewise, there's almost no reason to override the
 * <code>doOptions</code> and <code>doTrace</code> methods.
 *
 * <p>Servlets typically run on multithreaded servers,
 * so be aware that a servlet must handle concurrent
 * requests and be careful to synchronize access to shared resources.
 * Shared resources include in-memory data such as
 * instance or class variables and external objects
 * such as files, database connections, and network
 * connections.
 * See the
 * <a href="http://java.sun.com/Series/Tutorial/java/threads/multithreaded.html">
 * Java Tutorial on Multithreaded Programming</a> for more
 * information on handling multiple threads in a Java program.
 */
public abstract class HttpServlet extends GenericServlet {

    private static final long serialVersionUID = 1L;

    private static final String METHOD_DELETE = "DELETE";
    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_OPTIONS = "OPTIONS";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_TRACE = "TRACE";

    private static final String HEADER_IFMODSINCE = "If-Modified-Since";
    private static final String HEADER_LASTMOD = "Last-Modified";

    private static final String LSTRING_FILE = "jakarta.servlet.http.LocalStrings";
    private static final ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

    private static final Set<String> SENSITIVE_HTTP_HEADERS = new HashSet<>();

    /**
     * @deprecated May be removed in a future release
     *
     * @since Servlet 6.0
     */
    @Deprecated(forRemoval = true, since = "Servlet 6.0")
    public static final String LEGACY_DO_HEAD = "jakarta.servlet.http.legacyDoHead";

    private final transient Object cachedAllowHeaderValueLock = new Object();

    /**
     * Cached value of the HTTP {@code Allow} header for this servlet.
     */
    private volatile String cachedAllowHeaderValue = null;

    /**
     * Cached value read from {@link HttpServlet#LEGACY_DO_HEAD} system
     * property.
     */
    private volatile boolean cachedUseLegacyDoHead;

    static {
        SENSITIVE_HTTP_HEADERS.add("cookie");
        SENSITIVE_HTTP_HEADERS.add("authorization");
    }


    /**
     * Does nothing, because this is an abstract class.
     */
    public HttpServlet() {
        // NOOP
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        cachedUseLegacyDoHead = Boolean.parseBoolean(config.getInitParameter(LEGACY_DO_HEAD));
    }


    /**
     * Called by the server (via the <code>service</code> method) to
     * allow a servlet to handle a GET request.
     *
     * <p>Overriding this method to support a GET request also
     * automatically supports an HTTP HEAD request. A HEAD
     * request is a GET request that returns no body in the
     * response, only the request header fields.
     *
     * <p>When overriding this method, read the request data,
     * write the response headers, get the response's noBodyWriter or
     * output stream object, and finally, write the response data.
     * It's best to include content type and encoding. When using
     * a <code>PrintWriter</code> object to return the response,
     * set the content type before accessing the
     * <code>PrintWriter</code> object.
     *
     * <p>The servlet container must write the headers before
     * committing the response, because in HTTP the headers must be sent
     * before the response body.
     *
     * <p>Where possible, set the Content-Length header (with the
     * {@link jakarta.servlet.ServletResponse#setContentLength} method),
     * to allow the servlet container to use a persistent connection
     * to return its response to the client, improving performance.
     * The content length is automatically set if the entire response fits
     * inside the response buffer.
     *
     * <p>When using HTTP 1.1 chunked encoding (which means that the response
     * has a Transfer-Encoding header), do not set the Content-Length header.
     *
     * <p>The GET method should be safe, that is, without
     * any side effects for which users are held responsible.
     * For example, most form queries have no side effects.
     * If a client request is intended to change stored data,
     * the request should use some other HTTP method.
     *
     * <p>The GET method should also be idempotent, meaning
     * that it can be safely repeated. Sometimes making a
     * method safe also makes it idempotent. For example,
     * repeating queries is both safe and idempotent, but
     * buying a product online or modifying data is neither
     * safe nor idempotent.
     *
     * <p>If the request is incorrectly formatted, <code>doGet</code>
     * returns an HTTP "Bad Request" message.
     *
     * @param req   an {@link HttpServletRequest} object that
     *                  contains the request the client has made
     *                  of the servlet
     *
     * @param resp  an {@link HttpServletResponse} object that
     *                  contains the response the servlet sends
     *                  to the client
     *
     * @exception IOException   if an input or output error is
     *                              detected when the servlet handles
     *                              the GET request
     *
     * @exception ServletException  if the request for the GET
     *                                  could not be handled
     *
     * @see jakarta.servlet.ServletResponse#setContentType
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        String msg = lStrings.getString("http.method_get_not_supported");
        sendMethodNotAllowed(req, resp, msg);
    }


    /**
     * Returns the time the <code>HttpServletRequest</code>
     * object was last modified,
     * in milliseconds since midnight January 1, 1970 GMT.
     * If the time is unknown, this method returns a negative
     * number (the default).
     *
     * <p>Servlets that support HTTP GET requests and can quickly determine
     * their last modification time should override this method.
     * This makes browser and proxy caches work more effectively,
     * reducing the load on server and network resources.
     *
     * @param req   the <code>HttpServletRequest</code>
     *                  object that is sent to the servlet
     *
     * @return  a <code>long</code> integer specifying
     *              the time the <code>HttpServletRequest</code>
     *              object was last modified, in milliseconds
     *              since midnight, January 1, 1970 GMT, or
     *              -1 if the time is not known
     */
    protected long getLastModified(HttpServletRequest req) {
        return -1;
    }


    /**
     * <p>Receives an HTTP HEAD request from the protected
     * <code>service</code> method and handles the
     * request.
     * The client sends a HEAD request when it wants
     * to see only the headers of a response, such as
     * Content-Type or Content-Length. The HTTP HEAD
     * method counts the output bytes in the response
     * to set the Content-Length header accurately.
     *
     * <p>If you override this method, you can avoid computing
     * the response body and just set the response headers
     * directly to improve performance. Make sure that the
     * <code>doHead</code> method you write is both safe
     * and idempotent (that is, protects itself from being
     * called multiple times for one HTTP HEAD request).
     *
     * <p>If the HTTP HEAD request is incorrectly formatted,
     * <code>doHead</code> returns an HTTP "Bad Request"
     * message.
     *
     * @param req   the request object that is passed to the servlet
     *
     * @param resp  the response object that the servlet
     *                  uses to return the headers to the client
     *
     * @exception IOException   if an input or output error occurs
     *
     * @exception ServletException  if the request for the HEAD
     *                                  could not be handled
     */
    protected void doHead(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (DispatcherType.INCLUDE.equals(req.getDispatcherType()) || !cachedUseLegacyDoHead) {
            doGet(req, resp);
        } else {
            NoBodyResponse response = new NoBodyResponse(resp);
            doGet(req, response);
            if (req.isAsyncStarted()) {
                req.getAsyncContext().addListener(new NoBodyAsyncContextListener(response));
            } else {
                response.setContentLength();
            }
        }
    }


    /**
     * Called by the server (via the <code>service</code> method)
     * to allow a servlet to handle a POST request.
     *
     * The HTTP POST method allows the client to send
     * data of unlimited length to the Web server a single time
     * and is useful when posting information such as
     * credit card numbers.
     *
     * <p>When overriding this method, read the request data,
     * write the response headers, get the response's noBodyWriter or output
     * stream object, and finally, write the response data. It's best
     * to include content type and encoding. When using a
     * <code>PrintWriter</code> object to return the response, set the
     * content type before accessing the <code>PrintWriter</code> object.
     *
     * <p>The servlet container must write the headers before committing the
     * response, because in HTTP the headers must be sent before the
     * response body.
     *
     * <p>Where possible, set the Content-Length header (with the
     * {@link jakarta.servlet.ServletResponse#setContentLength} method),
     * to allow the servlet container to use a persistent connection
     * to return its response to the client, improving performance.
     * The content length is automatically set if the entire response fits
     * inside the response buffer.
     *
     * <p>When using HTTP 1.1 chunked encoding (which means that the response
     * has a Transfer-Encoding header), do not set the Content-Length header.
     *
     * <p>This method does not need to be either safe or idempotent.
     * Operations requested through POST can have side effects for
     * which the user can be held accountable, for example,
     * updating stored data or buying items online.
     *
     * <p>If the HTTP POST request is incorrectly formatted,
     * <code>doPost</code> returns an HTTP "Bad Request" message.
     *
     *
     * @param req   an {@link HttpServletRequest} object that
     *                  contains the request the client has made
     *                  of the servlet
     *
     * @param resp  an {@link HttpServletResponse} object that
     *                  contains the response the servlet sends
     *                  to the client
     *
     * @exception IOException   if an input or output error is
     *                              detected when the servlet handles
     *                              the request
     *
     * @exception ServletException  if the request for the POST
     *                                  could not be handled
     *
     * @see jakarta.servlet.ServletOutputStream
     * @see jakarta.servlet.ServletResponse#setContentType
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String msg = lStrings.getString("http.method_post_not_supported");
        sendMethodNotAllowed(req, resp, msg);
    }


    /**
     * Called by the server (via the <code>service</code> method)
     * to allow a servlet to handle a PUT request.
     *
     * The PUT operation allows a client to
     * place a file on the server and is similar to
     * sending a file by FTP.
     *
     * <p>When overriding this method, leave intact
     * any content headers sent with the request (including
     * Content-Length, Content-Type, Content-Transfer-Encoding,
     * Content-Encoding, Content-Base, Content-Language, Content-Location,
     * Content-MD5, and Content-Range). If your method cannot
     * handle a content header, it must issue an error message
     * (HTTP 501 - Not Implemented) and discard the request.
     * For more information on HTTP 1.1, see RFC 2616
     * <a href="http://www.ietf.org/rfc/rfc2616.txt"></a>.
     *
     * <p>This method does not need to be either safe or idempotent.
     * Operations that <code>doPut</code> performs can have side
     * effects for which the user can be held accountable. When using
     * this method, it may be useful to save a copy of the
     * affected URL in temporary storage.
     *
     * <p>If the HTTP PUT request is incorrectly formatted,
     * <code>doPut</code> returns an HTTP "Bad Request" message.
     *
     * @param req   the {@link HttpServletRequest} object that
     *                  contains the request the client made of
     *                  the servlet
     *
     * @param resp  the {@link HttpServletResponse} object that
     *                  contains the response the servlet returns
     *                  to the client
     *
     * @exception IOException   if an input or output error occurs
     *                              while the servlet is handling the
     *                              PUT request
     *
     * @exception ServletException  if the request for the PUT
     *                                  cannot be handled
     */
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String msg = lStrings.getString("http.method_put_not_supported");
        sendMethodNotAllowed(req, resp, msg);
    }


    /**
     * Called by the server (via the <code>service</code> method)
     * to allow a servlet to handle a DELETE request.
     *
     * The DELETE operation allows a client to remove a document
     * or Web page from the server.
     *
     * <p>This method does not need to be either safe
     * or idempotent. Operations requested through
     * DELETE can have side effects for which users
     * can be held accountable. When using
     * this method, it may be useful to save a copy of the
     * affected URL in temporary storage.
     *
     * <p>If the HTTP DELETE request is incorrectly formatted,
     * <code>doDelete</code> returns an HTTP "Bad Request"
     * message.
     *
     * @param req   the {@link HttpServletRequest} object that
     *                  contains the request the client made of
     *                  the servlet
     *
     *
     * @param resp  the {@link HttpServletResponse} object that
     *                  contains the response the servlet returns
     *                  to the client
     *
     * @exception IOException   if an input or output error occurs
     *                              while the servlet is handling the
     *                              DELETE request
     *
     * @exception ServletException  if the request for the
     *                                  DELETE cannot be handled
     */
    protected void doDelete(HttpServletRequest req,
                            HttpServletResponse resp)
        throws ServletException, IOException {

        String msg = lStrings.getString("http.method_delete_not_supported");
        sendMethodNotAllowed(req, resp, msg);
    }


    private void sendMethodNotAllowed(HttpServletRequest req, HttpServletResponse resp, String msg) throws IOException {
        String protocol = req.getProtocol();
        // Note: Tomcat reports "" for HTTP/0.9 although some implementations
        //       may report HTTP/0.9
        if (protocol.length() == 0 || protocol.endsWith("0.9") || protocol.endsWith("1.0")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        } else {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, msg);
        }
    }


    private String getCachedAllowHeaderValue() {
        if (cachedAllowHeaderValue == null) {
            synchronized (cachedAllowHeaderValueLock) {
                if (cachedAllowHeaderValue == null) {

                    Method[] methods = getAllDeclaredMethods(this.getClass());

                    // RFC 7230 does not define an order for this header
                    // This code aims to retain, broadly, the order of method
                    // tokens returned in earlier versions of this code. If that
                    // constraint is dropped then the code can be simplified
                    // further.

                    boolean allowGet = false;
                    boolean allowHead = false;
                    boolean allowPost = false;
                    boolean allowPut = false;
                    boolean allowDelete = false;

                    for (Method method : methods) {
                        switch (method.getName()) {
                            case "doGet": {
                                allowGet = true;
                                allowHead = true;
                                break;
                            }
                            case "doPost": {
                                allowPost = true;
                                break;
                            }
                            case "doPut": {
                                allowPut = true;
                                break;
                            }
                            case "doDelete": {
                                allowDelete = true;
                                break;
                            }
                            default:
                                // NO-OP
                        }

                    }

                    StringBuilder allow = new StringBuilder();

                    if (allowGet) {
                        allow.append(METHOD_GET);
                        allow.append(", ");
                    }

                    if (allowHead) {
                        allow.append(METHOD_HEAD);
                        allow.append(", ");
                    }

                    if (allowPost) {
                        allow.append(METHOD_POST);
                        allow.append(", ");
                    }

                    if (allowPut) {
                        allow.append(METHOD_PUT);
                        allow.append(", ");
                    }

                    if (allowDelete) {
                        allow.append(METHOD_DELETE);
                        allow.append(", ");
                    }

                    // Options is always allowed
                    allow.append(METHOD_OPTIONS);

                    cachedAllowHeaderValue = allow.toString();
                }
            }
        }

        return cachedAllowHeaderValue;
    }


    private static Method[] getAllDeclaredMethods(Class<?> c) {

        if (c.equals(jakarta.servlet.http.HttpServlet.class)) {
            return null;
        }

        Method[] parentMethods = getAllDeclaredMethods(c.getSuperclass());
        Method[] thisMethods = c.getDeclaredMethods();

        if ((parentMethods != null) && (parentMethods.length > 0)) {
            Method[] allMethods = new Method[parentMethods.length + thisMethods.length];
            System.arraycopy(parentMethods, 0, allMethods, 0, parentMethods.length);
            System.arraycopy(thisMethods, 0, allMethods, parentMethods.length, thisMethods.length);
            thisMethods = allMethods;
        }

        return thisMethods;
    }


    /**
     * Called by the server (via the <code>service</code> method)
     * to allow a servlet to handle an OPTIONS request.
     *
     * The OPTIONS request determines which HTTP methods
     * the server supports and
     * returns an appropriate header. For example, if a servlet
     * overrides <code>doGet</code>, this method returns the
     * following header:
     *
     * <p><code>Allow: GET, HEAD, TRACE, OPTIONS</code>
     *
     * <p>There's no need to override this method unless the
     * servlet implements new HTTP methods, beyond those
     * implemented by HTTP 1.1.
     *
     * @param req   the {@link HttpServletRequest} object that
     *                  contains the request the client made of
     *                  the servlet
     *
     * @param resp  the {@link HttpServletResponse} object that
     *                  contains the response the servlet returns
     *                  to the client
     *
     * @exception IOException   if an input or output error occurs
     *                              while the servlet is handling the
     *                              OPTIONS request
     *
     * @exception ServletException  if the request for the
     *                                  OPTIONS cannot be handled
     */
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String allow = getCachedAllowHeaderValue();

        // Tomcat specific hack to see if TRACE is allowed
        if (TomcatHack.getAllowTrace(req)) {
            if (allow.length() == 0) {
                allow = METHOD_TRACE;
            } else {
                allow = allow + ", " + METHOD_TRACE;
            }
        }

        resp.setHeader("Allow", allow);
    }


    /**
     * Called by the server (via the <code>service</code> method)
     * to allow a servlet to handle a TRACE request.
     *
     * A TRACE returns the headers sent with the TRACE
     * request to the client, so that they can be used in
     * debugging. There's no need to override this method.
     *
     * @param req   the {@link HttpServletRequest} object that
     *                  contains the request the client made of
     *                  the servlet
     *
     * @param resp  the {@link HttpServletResponse} object that
     *                  contains the response the servlet returns
     *                  to the client
     *
     * @exception IOException   if an input or output error occurs
     *                              while the servlet is handling the
     *                              TRACE request
     *
     * @exception ServletException  if the request for the
     *                                  TRACE cannot be handled
     */
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        int responseLength;

        String CRLF = "\r\n";
        StringBuilder buffer =
                new StringBuilder("TRACE ").append(req.getRequestURI()).append(" ").append(req.getProtocol());

        Enumeration<String> reqHeaderNames = req.getHeaderNames();

        while (reqHeaderNames.hasMoreElements()) {
            String headerName = reqHeaderNames.nextElement();
            // RFC 7231, 4.3.8 - skip 'sensitive' headers
            if (!SENSITIVE_HTTP_HEADERS.contains(headerName.toLowerCase(Locale.ENGLISH))) {
                Enumeration<String> headerValues = req.getHeaders(headerName);
                while (headerValues.hasMoreElements()) {
                    String headerValue = headerValues.nextElement();
                    buffer.append(CRLF).append(headerName).append(": ").append(headerValue);
                }
            }
        }

        buffer.append(CRLF);

        responseLength = buffer.length();

        resp.setContentType("message/http");
        resp.setContentLength(responseLength);
        ServletOutputStream out = resp.getOutputStream();
        out.print(buffer.toString());
        out.close();
    }


    /**
     * Receives standard HTTP requests from the public
     * <code>service</code> method and dispatches
     * them to the <code>do</code><i>Method</i> methods defined in
     * this class. This method is an HTTP-specific version of the
     * {@link jakarta.servlet.Servlet#service} method. There's no
     * need to override this method.
     *
     * @param req   the {@link HttpServletRequest} object that
     *                  contains the request the client made of
     *                  the servlet
     *
     * @param resp  the {@link HttpServletResponse} object that
     *                  contains the response the servlet returns
     *                  to the client
     *
     * @exception IOException   if an input or output error occurs
     *                              while the servlet is handling the
     *                              HTTP request
     *
     * @exception ServletException  if the HTTP request
     *                                  cannot be handled
     *
     * @see jakarta.servlet.Servlet#service
     */
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String method = req.getMethod();

        if (method.equals(METHOD_GET)) {
            long lastModified = getLastModified(req);
            if (lastModified == -1) {
                // servlet doesn't support if-modified-since, no reason
                // to go through further expensive logic
                doGet(req, resp);
            } else {
                long ifModifiedSince;
                try {
                    ifModifiedSince = req.getDateHeader(HEADER_IFMODSINCE);
                } catch (IllegalArgumentException iae) {
                    // Invalid date header - proceed as if none was set
                    ifModifiedSince = -1;
                }
                if (ifModifiedSince < (lastModified / 1000 * 1000)) {
                    // If the servlet mod time is later, call doGet()
                    // Round down to the nearest second for a proper compare
                    // A ifModifiedSince of -1 will always be less
                    maybeSetLastModified(resp, lastModified);
                    doGet(req, resp);
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                }
            }

        } else if (method.equals(METHOD_HEAD)) {
            long lastModified = getLastModified(req);
            maybeSetLastModified(resp, lastModified);
            doHead(req, resp);

        } else if (method.equals(METHOD_POST)) {
            doPost(req, resp);

        } else if (method.equals(METHOD_PUT)) {
            doPut(req, resp);

        } else if (method.equals(METHOD_DELETE)) {
            doDelete(req, resp);

        } else if (method.equals(METHOD_OPTIONS)) {
            doOptions(req,resp);

        } else if (method.equals(METHOD_TRACE)) {
            doTrace(req,resp);

        } else {
            //
            // Note that this means NO servlet supports whatever
            // method was requested, anywhere on this server.
            //

            String errMsg = lStrings.getString("http.method_not_implemented");
            Object[] errArgs = new Object[1];
            errArgs[0] = method;
            errMsg = MessageFormat.format(errMsg, errArgs);

            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, errMsg);
        }
    }


    /*
     * Sets the Last-Modified entity header field, if it has not
     * already been set and if the value is meaningful.  Called before
     * doGet, to ensure that headers are set before response data is
     * written.  A subclass might have set this header already, so we
     * check.
     */
    private void maybeSetLastModified(HttpServletResponse resp,
                                      long lastModified) {
        if (resp.containsHeader(HEADER_LASTMOD)) {
            return;
        }
        if (lastModified >= 0) {
            resp.setDateHeader(HEADER_LASTMOD, lastModified);
        }
    }


    /**
     * Dispatches client requests to the protected
     * <code>service</code> method. There's no need to
     * override this method.
     *
     * @param req   the {@link HttpServletRequest} object that
     *                  contains the request the client made of
     *                  the servlet
     *
     * @param res   the {@link HttpServletResponse} object that
     *                  contains the response the servlet returns
     *                  to the client
     *
     * @exception IOException   if an input or output error occurs
     *                              while the servlet is handling the
     *                              HTTP request
     *
     * @exception ServletException  if the HTTP request cannot
     *                                  be handled
     *
     * @see jakarta.servlet.Servlet#service
     */
    @Override
    public void service(ServletRequest req, ServletResponse res)
        throws ServletException, IOException {

        HttpServletRequest  request;
        HttpServletResponse response;

        try {
            request = (HttpServletRequest) req;
            response = (HttpServletResponse) res;
        } catch (ClassCastException e) {
            throw new ServletException(lStrings.getString("http.non_http"));
        }
        service(request, response);
    }


    private static class TomcatHack {

        private static final Class<?> REQUEST_FACADE_CLAZZ;
        private static final Method GET_ALLOW_TRACE;


        static {
            Method m1 = null;
            Class<?> c1 = null;
            try {
                c1 = Class.forName("org.apache.catalina.connector.RequestFacade");
                m1 = c1.getMethod("getAllowTrace", (Class<?>[]) null);
            } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
                // Ignore. Not running on Tomcat. TRACE is always allowed.
            }
            REQUEST_FACADE_CLAZZ = c1;
            GET_ALLOW_TRACE = m1;
        }

        public static boolean getAllowTrace(HttpServletRequest req) {
            if (REQUEST_FACADE_CLAZZ != null && GET_ALLOW_TRACE != null) {
                if (REQUEST_FACADE_CLAZZ.isAssignableFrom(req.getClass())) {
                    try {
                        return ((Boolean) GET_ALLOW_TRACE.invoke(req, (Object[]) null)).booleanValue();
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        // Should never happen given the checks in place.
                        // Ignore
                    }
                }
            }
            return true;
        }
    }


    /*
     * A response wrapper for use in (dumb) "HEAD" support.
     * This just swallows that body, counting the bytes in order to set
     * the content length appropriately.  All other methods delegate to the
     * wrapped HTTP Servlet Response object.
     */
    private static class NoBodyResponse extends HttpServletResponseWrapper {
        private final NoBodyOutputStream noBodyOutputStream;
        private ServletOutputStream originalOutputStream;
        private NoBodyPrintWriter noBodyWriter;
        private boolean didSetContentLength;

        private NoBodyResponse(HttpServletResponse r) {
            super(r);
            noBodyOutputStream = new NoBodyOutputStream(this);
        }

        private void setContentLength() {
            if (!didSetContentLength) {
                if (noBodyWriter != null) {
                    noBodyWriter.flush();
                }
                super.setContentLengthLong(noBodyOutputStream.getWrittenByteCount());
            }
        }


        @Override
        public void setContentLength(int len) {
            super.setContentLength(len);
            didSetContentLength = true;
        }

        @Override
        public void setContentLengthLong(long len) {
            super.setContentLengthLong(len);
            didSetContentLength = true;
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, value);
            checkHeader(name);
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, value);
            checkHeader(name);
        }

        @Override
        public void setIntHeader(String name, int value) {
            super.setIntHeader(name, value);
            checkHeader(name);
        }

        @Override
        public void addIntHeader(String name, int value) {
            super.addIntHeader(name, value);
            checkHeader(name);
        }

        private void checkHeader(String name) {
            if ("content-length".equalsIgnoreCase(name)) {
                didSetContentLength = true;
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            originalOutputStream = getResponse().getOutputStream();
            return noBodyOutputStream;
        }

        @Override
        public PrintWriter getWriter() throws UnsupportedEncodingException {

            if (noBodyWriter == null) {
                noBodyWriter = new NoBodyPrintWriter(noBodyOutputStream, getCharacterEncoding());
            }
            return noBodyWriter;
        }

        @Override
        public void reset() {
            super.reset();
            resetBuffer();
            originalOutputStream = null;
        }

        @Override
        public void resetBuffer() {
            noBodyOutputStream.resetBuffer();
            if (noBodyWriter != null) {
                noBodyWriter.resetBuffer();
            }
        }
    }


    /*
     * Servlet output stream that gobbles up all its data.
     */
    private static class NoBodyOutputStream extends ServletOutputStream {

        private static final String LSTRING_FILE = "jakarta.servlet.http.LocalStrings";
        private static final ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

        private final NoBodyResponse response;
        private boolean flushed = false;
        private long writtenByteCount = 0;

        private NoBodyOutputStream(NoBodyResponse response) {
            this.response = response;
        }

        private long getWrittenByteCount() {
            return writtenByteCount;
        }

        @Override
        public void write(int b) throws IOException {
            writtenByteCount++;
            checkCommit();
        }

        @Override
        public void write(byte buf[], int offset, int len) throws IOException {
            if (buf == null) {
                throw new NullPointerException(
                        lStrings.getString("err.io.nullArray"));
            }

            if (offset < 0 || len < 0 || offset+len > buf.length) {
                String msg = lStrings.getString("err.io.indexOutOfBounds");
                Object[] msgArgs = new Object[3];
                msgArgs[0] = Integer.valueOf(offset);
                msgArgs[1] = Integer.valueOf(len);
                msgArgs[2] = Integer.valueOf(buf.length);
                msg = MessageFormat.format(msg, msgArgs);
                throw new IndexOutOfBoundsException(msg);
            }

            writtenByteCount += len;
            checkCommit();
        }

        @Override
        public boolean isReady() {
            // Will always be ready as data is swallowed.
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            response.originalOutputStream.setWriteListener(listener);
        }

        private void checkCommit() throws IOException {
            if (!flushed && writtenByteCount > response.getBufferSize()) {
                response.flushBuffer();
                flushed = true;
            }
        }

        private void resetBuffer() {
            if (flushed) {
                throw new IllegalStateException(lStrings.getString("err.state.commit"));
            }
            writtenByteCount = 0;
        }
    }


    /*
     * On reset() and resetBuffer() need to clear the data buffered in the
     * OutputStreamWriter. No easy way to do that so NoBodyPrintWriter wraps a
     * PrintWriter than can be thrown away on reset()/resetBuffer() and a new
     * one constructed while the application retains a reference to the
     * NoBodyPrintWriter instance.
     */
    private static class NoBodyPrintWriter extends PrintWriter {

        private final NoBodyOutputStream out;
        private final String encoding;
        private PrintWriter pw;

        public NoBodyPrintWriter(NoBodyOutputStream out, String encoding) throws UnsupportedEncodingException {
            super(out);
            this.out = out;
            this.encoding = encoding;

            Writer osw = new OutputStreamWriter(out, encoding);
            pw = new PrintWriter(osw);
        }

        private void resetBuffer() {
            out.resetBuffer();

            Writer osw = null;
            try {
                osw = new OutputStreamWriter(out, encoding);
            } catch (UnsupportedEncodingException e) {
                // Impossible.
                // The same values were used in the constructor. If this method
                // gets called then the constructor must have succeeded so the
                // above call must also succeed.
            }
            pw = new PrintWriter(osw);
        }

        @Override
        public void flush() {
            pw.flush();
        }

        @Override
        public void close() {
            pw.close();
        }

        @Override
        public boolean checkError() {
            return pw.checkError();
        }

        @Override
        public void write(int c) {
            pw.write(c);
        }

        @Override
        public void write(char[] buf, int off, int len) {
            pw.write(buf, off, len);
        }

        @Override
        public void write(char[] buf) {
            pw.write(buf);
        }

        @Override
        public void write(String s, int off, int len) {
            pw.write(s, off, len);
        }

        @Override
        public void write(String s) {
            pw.write(s);
        }

        @Override
        public void print(boolean b) {
            pw.print(b);
        }

        @Override
        public void print(char c) {
            pw.print(c);
        }

        @Override
        public void print(int i) {
            pw.print(i);
        }

        @Override
        public void print(long l) {
            pw.print(l);
        }

        @Override
        public void print(float f) {
            pw.print(f);
        }

        @Override
        public void print(double d) {
            pw.print(d);
        }

        @Override
        public void print(char[] s) {
            pw.print(s);
        }

        @Override
        public void print(String s) {
            pw.print(s);
        }

        @Override
        public void print(Object obj) {
            pw.print(obj);
        }

        @Override
        public void println() {
            pw.println();
        }

        @Override
        public void println(boolean x) {
            pw.println(x);
        }

        @Override
        public void println(char x) {
            pw.println(x);
        }

        @Override
        public void println(int x) {
            pw.println(x);
        }

        @Override
        public void println(long x) {
            pw.println(x);
        }

        @Override
        public void println(float x) {
            pw.println(x);
        }

        @Override
        public void println(double x) {
            pw.println(x);
        }

        @Override
        public void println(char[] x) {
            pw.println(x);
        }

        @Override
        public void println(String x) {
            pw.println(x);
        }

        @Override
        public void println(Object x) {
            pw.println(x);
        }
    }


    /*
     * Calls NoBodyResponse.setContentLength() once the async request is
     * complete.
     */
    private static class NoBodyAsyncContextListener implements AsyncListener {

        private final NoBodyResponse noBodyResponse;

        public NoBodyAsyncContextListener(NoBodyResponse noBodyResponse) {
            this.noBodyResponse = noBodyResponse;
        }

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            noBodyResponse.setContentLength();
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            // NO-OP
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            // NO-OP
        }
    }
}

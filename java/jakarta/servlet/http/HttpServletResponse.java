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
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.servlet.ServletResponse;

/**
 * Extends the {@link ServletResponse} interface to provide HTTP-specific functionality in sending a response. For
 * example, it has methods to access HTTP headers and cookies.
 * <p>
 * The servlet container creates an <code>HttpServletResponse</code> object and passes it as an argument to the
 * servlet's service methods (<code>doGet</code>, <code>doPost</code>, etc).
 *
 * @see jakarta.servlet.ServletResponse
 */
public interface HttpServletResponse extends ServletResponse {

    /**
     * Adds the specified cookie to the response. This method can be called multiple times to set more than one cookie.
     *
     * @param cookie the Cookie to return to the client
     */
    void addCookie(Cookie cookie);

    /**
     * Returns a boolean indicating whether the named response header has already been set.
     *
     * @param name the header name
     *
     * @return <code>true</code> if the named response header has already been set; <code>false</code> otherwise
     */
    boolean containsHeader(String name);

    /**
     * Encodes the specified URL by including the session ID in it, or, if encoding is not needed, returns the URL
     * unchanged. The implementation of this method includes the logic to determine whether the session ID needs to be
     * encoded in the URL. For example, if the browser supports cookies, or session tracking is turned off, URL encoding
     * is unnecessary.
     * <p>
     * For robust session tracking, all URLs emitted by a servlet should be run through this method. Otherwise, URL
     * rewriting cannot be used with browsers which do not support cookies.
     *
     * @param url the url to be encoded.
     *
     * @return the encoded URL if encoding is needed; the unchanged URL otherwise.
     */
    String encodeURL(String url);

    /**
     * Encodes the specified URL for use in the <code>sendRedirect</code> method or, if encoding is not needed, returns
     * the URL unchanged. The implementation of this method includes the logic to determine whether the session ID needs
     * to be encoded in the URL. Because the rules for making this determination can differ from those used to decide
     * whether to encode a normal link, this method is separated from the <code>encodeURL</code> method.
     * <p>
     * All URLs sent to the <code>HttpServletResponse.sendRedirect</code> method should be run through this method.
     * Otherwise, URL rewriting cannot be used with browsers which do not support cookies.
     *
     * @param url the url to be encoded.
     *
     * @return the encoded URL if encoding is needed; the unchanged URL otherwise.
     *
     * @see #sendRedirect
     */
    String encodeRedirectURL(String url);

    /**
     * Sends an error response to the client using the specified status code and clears the output buffer. The server
     * defaults to creating the response to look like an HTML-formatted server error page containing the specified
     * message, setting the content type to "text/html", leaving cookies and other headers unmodified. If an error-page
     * declaration has been made for the web application corresponding to the status code passed in, it will be served
     * back in preference to the suggested msg parameter.
     * <p>
     * If the response has already been committed, this method throws an IllegalStateException. After using this method,
     * the response should be considered to be committed and should not be written to.
     *
     * @param sc  the error status code
     * @param msg the descriptive message
     *
     * @exception IOException           If an input or output exception occurs
     * @exception IllegalStateException If the response was committed
     */
    void sendError(int sc, String msg) throws IOException;

    /**
     * Sends an error response to the client using the specified status code and clears the buffer. This is equivalent
     * to calling {@link #sendError(int, String)} with the same status code and <code>null</code> for the message.
     *
     * @param sc the error status code
     *
     * @exception IOException           If an input or output exception occurs
     * @exception IllegalStateException If the response was committed before this method call
     */
    void sendError(int sc) throws IOException;

    /**
     * Sends a redirect response to the client using the specified redirect location URL with the status code
     * {@link #SC_FOUND} 302 (Found), clears the response buffer and commits the response. The response buffer will be
     * replaced with a short hypertext note as per RFC 9110.
     * <p>
     * This method has no effect if called from an include.
     *
     * @param location the redirect location URL (may be absolute or relative)
     *
     * @exception IOException              If an input or output exception occurs
     * @exception IllegalArgumentException If a relative URL is given and cannot be converted into an absolute URL
     * @exception IllegalStateException    If the response was already committed when this method was called
     *
     * @see #sendRedirect(String, int, boolean)
     */
    default void sendRedirect(String location) throws IOException {
        sendRedirect(location, SC_FOUND, true);
    }

    /**
     * Sends a redirect response to the client using the specified redirect location URL with the status code
     * {@link #SC_FOUND} 302 (Found), optionally clears the response buffer and commits the response. If the response
     * buffer is cleared, it will be replaced with a short hypertext note as per RFC 9110.
     * <p>
     * This method has no effect if called from an include.
     *
     * @param location    the redirect location URL (may be absolute or relative)
     * @param clearBuffer if {@code true}, clear the buffer and replace it with the data set by this method otherwise
     *                        retain the existing buffer
     *
     * @exception IOException              If an input or output exception occurs
     * @exception IllegalArgumentException If a relative URL is given and cannot be converted into an absolute URL
     * @exception IllegalStateException    If the response was already committed when this method was called
     *
     * @see #sendRedirect(String, int, boolean)
     *
     * @since Servlet 6.1
     */
    default void sendRedirect(String location, boolean clearBuffer) throws IOException {
        sendRedirect(location, SC_FOUND, clearBuffer);
    }

    /**
     * Sends a redirect response to the client using the specified redirect location URL and status code, clears the
     * response buffer and commits the response. The response buffer will be replaced with a short hypertext note as per
     * RFC 9110.
     * <p>
     * This method has no effect if called from an include.
     *
     * @param location the redirect location URL (may be absolute or relative)
     * @param sc       the status code to use for the redirect
     *
     * @exception IOException              If an input or output exception occurs
     * @exception IllegalArgumentException If a relative URL is given and cannot be converted into an absolute URL
     * @exception IllegalStateException    If the response was already committed when this method was called
     *
     * @see #sendRedirect(String, int, boolean)
     *
     * @since Servlet 6.1
     */
    default void sendRedirect(String location, int sc) throws IOException {
        sendRedirect(location, sc, true);
    }

    /**
     * Sends a redirect response to the client using the specified redirect location URL and status code, optionally
     * clears the response buffer and commits the response. If the response buffer is cleared, it will be replaced with
     * a short hypertext note as per RFC 9110.
     * <p>
     * This method has no effect if called from an include.
     * <p>
     * This method accepts both relative and absolute URLs. Absolute URLs passed to this method are used as provided as
     * the redirect location URL. Relative URLs are converted to absolute URLs unless a container specific
     * feature/option is provided that controls whether relative URLs passed to this method are converted to absolute
     * URLs or used as provided for the redirect location URL. If converting a relative URL to an absolute URL then:
     * <ul>
     * <li>If the location is relative without a leading '/' the container interprets it as relative to the current
     * request URI.</li>
     * <li>If the location is relative with a leading '/' the container interprets it as relative to the servlet
     * container root.</li>
     * <li>If the location is relative with two leading '/' the container interprets it as a network-path reference (see
     * <a href="http://www.ietf.org/rfc/rfc3986.txt"> RFC 3986: Uniform Resource Identifier (URI): Generic Syntax</a>,
     * section 4.2 &quot;Relative Reference&quot;).</li>
     * </ul>
     * <p>
     * If the response has already been committed, this method throws an IllegalStateException. After using this method,
     * the response should be considered to be committed and should not be written to.
     *
     * @param location    the redirect location URL (may be absolute or relative)
     * @param sc          the status code to use for the redirect
     * @param clearBuffer if {@code true}, clear the buffer and replace it with the data set by this method otherwise
     *                        retain the existing buffer
     *
     * @exception IOException              If an input or output exception occurs
     * @exception IllegalArgumentException If a relative URL is given and cannot be converted into an absolute URL
     * @exception IllegalStateException    If the response was already committed when this method was called
     *
     * @since Servlet 6.1
     */
    void sendRedirect(String location, int sc, boolean clearBuffer) throws IOException;

    /**
     * Sends a 103 response to the client using the current response headers. This method does not commit the response
     * and may be called multiple times before the response is committed. The current response headers may include some
     * headers that have been added automatically by the container.
     * <p>
     * This method has no effect if called after the response has been committed.
     *
     * @since Servlet 6.2
     */
    void sendEarlyHints();

    /**
     * Sets a response header with the given name and date-value. The date is specified in terms of milliseconds since
     * the epoch. If the header had already been set, the new value overwrites the previous one. The
     * <code>containsHeader</code> method can be used to test for the presence of a header before setting its value.
     *
     * @param name the name of the header to set
     * @param date the assigned date value
     *
     * @see #containsHeader
     * @see #addDateHeader
     */
    void setDateHeader(String name, long date);

    /**
     * Adds a response header with the given name and date-value. The date is specified in terms of milliseconds since
     * the epoch. This method allows response headers to have multiple values.
     *
     * @param name the name of the header to set
     * @param date the additional date value
     *
     * @see #setDateHeader
     */
    void addDateHeader(String name, long date);

    /**
     * Sets a response header with the given name and value. If the header had already been set, the new value
     * overwrites the previous one. The <code>containsHeader</code> method can be used to test for the presence of a
     * header before setting its value.
     *
     * @param name  the name of the header
     * @param value the header value If it contains octet string, it should be encoded according to RFC 2047
     *                  (http://www.ietf.org/rfc/rfc2047.txt)
     *
     * @see #containsHeader
     * @see #addHeader
     */
    void setHeader(String name, String value);

    /**
     * Adds a response header with the given name and value. This method allows response headers to have multiple
     * values.
     *
     * @param name  the name of the header
     * @param value the additional header value If it contains octet string, it should be encoded according to RFC 2047
     *                  (http://www.ietf.org/rfc/rfc2047.txt)
     *
     * @see #setHeader
     */
    void addHeader(String name, String value);

    /**
     * Sets a response header with the given name and integer value. If the header had already been set, the new value
     * overwrites the previous one. The <code>containsHeader</code> method can be used to test for the presence of a
     * header before setting its value.
     *
     * @param name  the name of the header
     * @param value the assigned integer value
     *
     * @see #containsHeader
     * @see #addIntHeader
     */
    void setIntHeader(String name, int value);

    /**
     * Adds a response header with the given name and integer value. This method allows response headers to have
     * multiple values.
     *
     * @param name  the name of the header
     * @param value the assigned integer value
     *
     * @see #setIntHeader
     */
    void addIntHeader(String name, int value);

    /**
     * Sets the status code for this response. This method is used to set the return status code when there is no error
     * (for example, for the status codes SC_OK or SC_MOVED_TEMPORARILY). If there is an error, and the caller wishes to
     * invoke an error-page defined in the web application, the <code>sendError</code> method should be used instead.
     * <p>
     * The container clears the buffer and sets the Location header, preserving cookies and other headers.
     *
     * @param sc the status code
     *
     * @see #sendError
     */
    void setStatus(int sc);

    /**
     * Get the HTTP status code for this Response.
     *
     * @return The HTTP status code for this Response
     *
     * @since Servlet 3.0
     */
    int getStatus();

    /**
     * Return the value for the specified header, or <code>null</code> if this header has not been set. If more than one
     * value was added for this name, only the first is returned; use {@link #getHeaders(String)} to retrieve all of
     * them.
     *
     * @param name Header name to look up
     *
     * @return The first value for the specified header. This is the raw value so if multiple values are specified in
     *             the first header then they will be returned as a single header value .
     *
     * @since Servlet 3.0
     */
    String getHeader(String name);

    /**
     * Return a Collection of all the header values associated with the specified header name.
     *
     * @param name Header name to look up
     *
     * @return The values for the specified header. These are the raw values so if multiple values are specified in a
     *             single header that will be returned as a single header value.
     *
     * @since Servlet 3.0
     */
    Collection<String> getHeaders(String name);

    /**
     * Get the header names set for this HTTP response.
     *
     * @return The header names set for this HTTP response.
     *
     * @since Servlet 3.0
     */
    Collection<String> getHeaderNames();

    /**
     * Configure the supplier of the trailer headers. The supplier will be called in the scope of the thread that
     * completes the response. <br>
     * Trailers that don't meet the requirements of RFC 7230, section 4.1.2 will be ignored. <br>
     * The default implementation is a NO-OP.
     *
     * @param supplier The supplier for the trailer headers
     *
     * @throws IllegalStateException if this method is called when the underlying protocol does not support trailer
     *                                   headers or if using HTTP/1.1 and the response has already been committed
     *
     * @since Servlet 4.0
     */
    default void setTrailerFields(Supplier<Map<String,String>> supplier) {
        // NO-OP
    }

    /**
     * Obtain the supplier of the trailer headers. <br>
     * The default implementation returns null.
     *
     * @return The supplier for the trailer headers
     *
     * @since Servlet 4.0
     */
    default Supplier<Map<String,String>> getTrailerFields() {
        return null;
    }

    /*
     * Server status codes; see RFC 7231.
     */

    /**
     * Status code (100) indicating the client can continue.
     */
    int SC_CONTINUE = 100;

    /**
     * Status code (101) indicating the server is switching protocols according to Upgrade header.
     */
    int SC_SWITCHING_PROTOCOLS = 101;

    /**
     * Status code (103) indicating that the server is likely to send a final response containing the headers present in
     * this informational response.
     *
     * @since Servlet 6.2
     */
    int SC_EARLY_HINTS = 103;

    /**
     * Status code (200) indicating the request succeeded normally.
     */
    int SC_OK = 200;

    /**
     * Status code (201) indicating the request succeeded and created a new resource on the server.
     */
    int SC_CREATED = 201;

    /**
     * Status code (202) indicating that a request was accepted for processing, but was not completed.
     */
    int SC_ACCEPTED = 202;

    /**
     * Status code (203) indicating that the meta information presented by the client did not originate from the server.
     */
    int SC_NON_AUTHORITATIVE_INFORMATION = 203;

    /**
     * Status code (204) indicating that the request succeeded but that there was no new information to return.
     */
    int SC_NO_CONTENT = 204;

    /**
     * Status code (205) indicating that the agent <em>SHOULD</em> reset the document view which caused the request to
     * be sent.
     */
    int SC_RESET_CONTENT = 205;

    /**
     * Status code (206) indicating that the server has fulfilled the partial GET request for the resource.
     */
    int SC_PARTIAL_CONTENT = 206;

    /**
     * Status code (300) indicating that the requested resource corresponds to any one of a set of representations, each
     * with its own specific location.
     */
    int SC_MULTIPLE_CHOICES = 300;

    /**
     * Status code (301) indicating that the resource has permanently moved to a new location, and that future
     * references should use a new URI with their requests.
     */
    int SC_MOVED_PERMANENTLY = 301;

    /**
     * Status code (302) indicating that the resource has temporarily moved to another location, but that future
     * references should still use the original URI to access the resource. This definition is being retained for
     * backwards compatibility. SC_FOUND is now the preferred definition.
     */
    int SC_MOVED_TEMPORARILY = 302;

    /**
     * Status code (302) indicating that the resource reside temporarily under a different URI. Since the redirection
     * might be altered on occasion, the client should continue to use the Request-URI for future requests.(HTTP/1.1) To
     * represent the status code (302), it is recommended to use this variable.
     */
    int SC_FOUND = 302;

    /**
     * Status code (303) indicating that the response to the request can be found under a different URI.
     */
    int SC_SEE_OTHER = 303;

    /**
     * Status code (304) indicating that a conditional GET operation found that the resource was available and not
     * modified.
     */
    int SC_NOT_MODIFIED = 304;

    /**
     * Status code (305) indicating that the requested resource <em>MUST</em> be accessed through the proxy given by the
     * <code><em>Location</em></code> field.
     */
    int SC_USE_PROXY = 305;

    /**
     * Status code (307) indicating that the requested resource resides temporarily under a different URI. The temporary
     * URI <em>SHOULD</em> be given by the <code><em>Location</em></code> field in the response.
     */
    int SC_TEMPORARY_REDIRECT = 307;

    /**
     * Status code (308) indicating that the requested resource resides permanently under a different URI. The new URI
     * <em>SHOULD</em> be given by the <code><em>Location</em></code> field in the response.
     *
     * @since Servlet 6.1
     */
    int SC_PERMANENT_REDIRECT = 308;

    /**
     * Status code (400) indicating the request sent by the client was syntactically incorrect.
     */
    int SC_BAD_REQUEST = 400;

    /**
     * Status code (401) indicating that the request requires HTTP authentication.
     */
    int SC_UNAUTHORIZED = 401;

    /**
     * Status code (402) reserved for future use.
     */
    int SC_PAYMENT_REQUIRED = 402;

    /**
     * Status code (403) indicating the server understood the request but refused to fulfill it.
     */
    int SC_FORBIDDEN = 403;

    /**
     * Status code (404) indicating that the requested resource is not available.
     */
    int SC_NOT_FOUND = 404;

    /**
     * Status code (405) indicating that the method specified in the <code><em>Request-Line</em></code> is not allowed
     * for the resource identified by the <code><em>Request-URI</em></code>.
     */
    int SC_METHOD_NOT_ALLOWED = 405;

    /**
     * Status code (406) indicating that the resource identified by the request is only capable of generating response
     * entities which have content characteristics not acceptable according to the accept headers sent in the request.
     */
    int SC_NOT_ACCEPTABLE = 406;

    /**
     * Status code (407) indicating that the client <em>MUST</em> first authenticate itself with the proxy.
     */
    int SC_PROXY_AUTHENTICATION_REQUIRED = 407;

    /**
     * Status code (408) indicating that the client did not produce a request within the time that the server was
     * prepared to wait.
     */
    int SC_REQUEST_TIMEOUT = 408;

    /**
     * Status code (409) indicating that the request could not be completed due to a conflict with the current state of
     * the resource.
     */
    int SC_CONFLICT = 409;

    /**
     * Status code (410) indicating that the resource is no longer available at the server and no forwarding address is
     * known. This condition <em>SHOULD</em> be considered permanent.
     */
    int SC_GONE = 410;

    /**
     * Status code (411) indicating that the request cannot be handled without a defined
     * <code><em>Content-Length</em></code>.
     */
    int SC_LENGTH_REQUIRED = 411;

    /**
     * Status code (412) indicating that the precondition given in one or more of the request-header fields evaluated to
     * false when it was tested on the server.
     */
    int SC_PRECONDITION_FAILED = 412;

    /**
     * Status code (413) indicating that the server is refusing to process the request because the request entity is
     * larger than the server is willing or able to process.
     */
    int SC_REQUEST_ENTITY_TOO_LARGE = 413;

    /**
     * Status code (414) indicating that the server is refusing to service the request because the
     * <code><em>Request-URI</em></code> is longer than the server is willing to interpret.
     */
    int SC_REQUEST_URI_TOO_LONG = 414;

    /**
     * Status code (415) indicating that the server is refusing to service the request because the entity of the request
     * is in a format not supported by the requested resource for the requested method.
     */
    int SC_UNSUPPORTED_MEDIA_TYPE = 415;

    /**
     * Status code (416) indicating that the server cannot serve the requested byte range.
     */
    int SC_REQUESTED_RANGE_NOT_SATISFIABLE = 416;

    /**
     * Status code (417) indicating that the server could not meet the expectation given in the Expect request header.
     */
    int SC_EXPECTATION_FAILED = 417;

    /**
     * Status code (421) indicating that the server is unwilling or unable to produce an authoritative response for the
     * target URI.
     *
     * @since Servlet 6.1
     */
    int SC_MISDIRECTED_REQUEST = 421;

    /**
     * Status code (422) indicating that the server understands the content type of the request but is unable to process
     * the contained instructions.
     *
     * @since Servlet 6.1
     */
    int SC_UNPROCESSABLE_CONTENT = 422;

    /**
     * Status code (426) indicating that the server refuses to perform the request using the current protocol but may be
     * willing to do so after the client upgrades to a different protocol. The server must include an appropriate
     * {@code Upgrade} header in the response.
     *
     * @since Servlet 6.1
     */
    int SC_UPGRADE_REQUIRED = 426;

    /**
     * Status code (500) indicating an error inside the HTTP server which prevented it from fulfilling the request.
     */
    int SC_INTERNAL_SERVER_ERROR = 500;

    /**
     * Status code (501) indicating the HTTP server does not support the functionality needed to fulfill the request.
     */
    int SC_NOT_IMPLEMENTED = 501;

    /**
     * Status code (502) indicating that the HTTP server received an invalid response from a server it consulted when
     * acting as a proxy or gateway.
     */
    int SC_BAD_GATEWAY = 502;

    /**
     * Status code (503) indicating that the HTTP server is temporarily overloaded, and unable to handle the request.
     */
    int SC_SERVICE_UNAVAILABLE = 503;

    /**
     * Status code (504) indicating that the server did not receive a timely response from the upstream server while
     * acting as a gateway or proxy.
     */
    int SC_GATEWAY_TIMEOUT = 504;

    /**
     * Status code (505) indicating that the server does not support or refuses to support the HTTP protocol version
     * that was used in the request message.
     */
    int SC_HTTP_VERSION_NOT_SUPPORTED = 505;
}

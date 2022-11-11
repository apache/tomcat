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
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;

/**
 * Extends the {@link jakarta.servlet.ServletRequest} interface to provide request
 * information for HTTP servlets.
 * <p>
 * The servlet container creates an <code>HttpServletRequest</code> object and
 * passes it as an argument to the servlet's service methods
 * (<code>doGet</code>, <code>doPost</code>, etc).
 */
public interface HttpServletRequest extends ServletRequest {

    /**
     * String identifier for Basic authentication. Value "BASIC"
     */
    public static final String BASIC_AUTH = "BASIC";
    /**
     * String identifier for Form authentication. Value "FORM"
     */
    public static final String FORM_AUTH = "FORM";
    /**
     * String identifier for Client Certificate authentication. Value
     * "CLIENT_CERT"
     */
    public static final String CLIENT_CERT_AUTH = "CLIENT_CERT";
    /**
     * String identifier for Digest authentication. Value "DIGEST"
     */
    public static final String DIGEST_AUTH = "DIGEST";

    /**
     * Returns the name of the authentication scheme used to protect the
     * servlet. All servlet containers support basic, form and client
     * certificate authentication, and may additionally support digest
     * authentication. If the servlet is not authenticated <code>null</code> is
     * returned.
     * <p>
     * Same as the value of the CGI variable AUTH_TYPE.
     *
     * @return one of the static members BASIC_AUTH, FORM_AUTH, CLIENT_CERT_AUTH,
     *         DIGEST_AUTH (suitable for == comparison) or the
     *         container-specific string indicating the authentication scheme,
     *         or <code>null</code> if the request was not authenticated.
     */
    public String getAuthType();

    /**
     * Returns an array containing all of the <code>Cookie</code> objects the
     * client sent with this request. This method returns <code>null</code> if
     * no cookies were sent.
     *
     * @return an array of all the <code>Cookies</code> included with this
     *         request, or <code>null</code> if the request has no cookies
     */
    public Cookie[] getCookies();

    /**
     * Returns the value of the specified request header as a <code>long</code>
     * value that represents a <code>Date</code> object. Use this method with
     * headers that contain dates, such as <code>If-Modified-Since</code>.
     * <p>
     * The date is returned as the number of milliseconds since January 1, 1970
     * GMT. The header name is case insensitive.
     * <p>
     * If the request did not have a header of the specified name, this method
     * returns -1. If the header can't be converted to a date, the method throws
     * an <code>IllegalArgumentException</code>.
     *
     * @param name
     *            a <code>String</code> specifying the name of the header
     * @return a <code>long</code> value representing the date specified in the
     *         header expressed as the number of milliseconds since January 1,
     *         1970 GMT, or -1 if the named header was not included with the
     *         request
     * @exception IllegalArgumentException
     *                If the header value can't be converted to a date
     */
    public long getDateHeader(String name);

    /**
     * Returns the value of the specified request header as a
     * <code>String</code>. If the request did not include a header of the
     * specified name, this method returns <code>null</code>. If there are
     * multiple headers with the same name, this method returns the first head
     * in the request. The header name is case insensitive. You can use this
     * method with any request header.
     *
     * @param name
     *            a <code>String</code> specifying the header name
     * @return a <code>String</code> containing the value of the requested
     *         header, or <code>null</code> if the request does not have a
     *         header of that name
     */
    public String getHeader(String name);

    /**
     * Returns all the values of the specified request header as an
     * <code>Enumeration</code> of <code>String</code> objects.
     * <p>
     * Some headers, such as <code>Accept-Language</code> can be sent by clients
     * as several headers each with a different value rather than sending the
     * header as a comma separated list.
     * <p>
     * If the request did not include any headers of the specified name, this
     * method returns an empty <code>Enumeration</code>. The header name is case
     * insensitive. You can use this method with any request header.
     *
     * @param name
     *            a <code>String</code> specifying the header name
     * @return an <code>Enumeration</code> containing the values of the requested
     *         header. If the request does not have any headers of that name
     *         return an empty enumeration. If the container does not allow
     *         access to header information, return null
     */
    public Enumeration<String> getHeaders(String name);

    /**
     * Returns an enumeration of all the header names this request contains. If
     * the request has no headers, this method returns an empty enumeration.
     * <p>
     * Some servlet containers do not allow servlets to access headers using
     * this method, in which case this method returns <code>null</code>
     *
     * @return an enumeration of all the header names sent with this request; if
     *         the request has no headers, an empty enumeration; if the servlet
     *         container does not allow servlets to use this method,
     *         <code>null</code>
     */
    public Enumeration<String> getHeaderNames();

    /**
     * Returns the value of the specified request header as an <code>int</code>.
     * If the request does not have a header of the specified name, this method
     * returns -1. If the header cannot be converted to an integer, this method
     * throws a <code>NumberFormatException</code>.
     * <p>
     * The header name is case insensitive.
     *
     * @param name
     *            a <code>String</code> specifying the name of a request header
     * @return an integer expressing the value of the request header or -1 if the
     *         request doesn't have a header of this name
     * @exception NumberFormatException
     *                If the header value can't be converted to an
     *                <code>int</code>
     */
    public int getIntHeader(String name);

    /**
     * Obtain the mapping information for this request.
     *
     * @return the mapping information for this request
     */
    public default HttpServletMapping getHttpServletMapping() {
        return new HttpServletMapping() {

            @Override
            public String getMatchValue() {
                return "";
            }

            @Override
            public String getPattern() {
                return "";
            }

            @Override
            public String getServletName() {
                return "";
            }

            @Override
            public MappingMatch getMappingMatch() {
                return null;
            }
        };
    }

    /**
     * Returns the name of the HTTP method with which this request was made, for
     * example, GET, POST, or PUT. Same as the value of the CGI variable
     * REQUEST_METHOD.
     *
     * @return a <code>String</code> specifying the name of the method with
     *         which this request was made
     */
    public String getMethod();

    /**
     * Returns any extra path information associated with the URL the client
     * sent when it made this request. The extra path information follows the
     * servlet path but precedes the query string and will start with a "/"
     * character.
     * <p>
     * This method returns <code>null</code> if there was no extra path
     * information.
     * <p>
     * The URL will be canonicalized as per section 3.5 of the specification
     * before the path information, if any, is extracted.
     *
     * @return a <code>String</code>, canonicalized by the web container,
     *         specifying extra path information that comes after the servlet
     *         path but before the query string in the request URL; or
     *         {@code null} if the URL does not have any extra path information
     */
    public String getPathInfo();

    /**
     * Returns any extra path information after the servlet name but before the
     * query string, and translates it to a real path. Same as the value of the
     * CGI variable PATH_TRANSLATED.
     * <p>
     * If the URL does not have any extra path information, this method returns
     * <code>null</code> or the servlet container cannot translate the virtual
     * path to a real path for any reason (such as when the web application is
     * executed from an archive). The web container does not decode this string.
     *
     * @return a <code>String</code> specifying the real path, or
     *         <code>null</code> if the URL does not have any extra path
     *         information
     */
    public String getPathTranslated();

    /**
     * Obtain a builder for generating push requests. {@link PushBuilder}
     * documents how this request will be used as the basis for a push request.
     * Each call to this method will return a new instance, independent of any
     * previous instance obtained.
     *
     * @return A builder that can be used to generate push requests based on
     *         this request or {@code null} if push is not supported. Note that
     *         even if a PushBuilder instance is returned, by the time that
     *         {@link PushBuilder#push()} is called, it may no longer be valid
     *         to push a request and the push request will be ignored.
     *
     * @since Servlet 4.0
     */
    public default PushBuilder newPushBuilder() {
        return null;
    }

    /**
     * Returns the portion of the request URI that indicates the context of the
     * request. The context path always comes first in a request URI. The path
     * starts with a "/" character but does not end with a "/" character. For
     * servlets in the default (root) context, this method returns "". The
     * container does not decode this string.
     *
     * @return a <code>String</code> specifying the portion of the request URI
     *         that indicates the context of the request
     */
    public String getContextPath();

    /**
     * Returns the query string that is contained in the request URL after the
     * path. This method returns <code>null</code> if the URL does not have a
     * query string. Same as the value of the CGI variable QUERY_STRING.
     *
     * @return a <code>String</code> containing the query string or
     *         <code>null</code> if the URL contains no query string. The value
     *         is not decoded by the container.
     */
    public String getQueryString();

    /**
     * Returns the login of the user making this request, if the user has been
     * authenticated, or <code>null</code> if the user has not been
     * authenticated. Whether the user name is sent with each subsequent request
     * depends on the browser and type of authentication. Same as the value of
     * the CGI variable REMOTE_USER.
     *
     * @return a <code>String</code> specifying the login of the user making
     *         this request, or <code>null</code> if the user login is not known
     */
    public String getRemoteUser();

    /**
     * Returns a boolean indicating whether the authenticated user is included
     * in the specified logical "role". Roles and role membership can be defined
     * using deployment descriptors. If the user has not been authenticated, the
     * method returns <code>false</code>.
     *
     * @param role
     *            a <code>String</code> specifying the name of the role
     * @return a <code>boolean</code> indicating whether the user making this
     *         request belongs to a given role; <code>false</code> if the user
     *         has not been authenticated
     */
    public boolean isUserInRole(String role);

    /**
     * Returns a <code>java.security.Principal</code> object containing the name
     * of the current authenticated user. If the user has not been
     * authenticated, the method returns <code>null</code>.
     *
     * @return a <code>java.security.Principal</code> containing the name of the
     *         user making this request; <code>null</code> if the user has not
     *         been authenticated
     */
    public java.security.Principal getUserPrincipal();

    /**
     * Returns the session ID specified by the client. This may not be the same
     * as the ID of the current valid session for this request. If the client
     * did not specify a session ID, this method returns <code>null</code>.
     *
     * @return a <code>String</code> specifying the session ID, or
     *         <code>null</code> if the request did not specify a session ID
     * @see #isRequestedSessionIdValid
     */
    public String getRequestedSessionId();

    /**
     * Returns the part of this request's URL from the protocol name up to the
     * query string in the first line of the HTTP request. The web container
     * does not decode this String. For example:
     * <table>
     * <caption>Examples of Returned Values</caption>
     * <tr>
     * <th>First line of HTTP request</th>
     * <th>Returned Value</th>
     * <tr>
     * <td>POST /some/path.html HTTP/1.1
     * <td>
     * <td>/some/path.html
     * <tr>
     * <td>GET http://foo.bar/a.html HTTP/1.0
     * <td>
     * <td>/a.html
     * <tr>
     * <td>HEAD /xyz?a=b HTTP/1.1
     * <td>
     * <td>/xyz
     * </table>
     * <p>
     * To reconstruct a URL with a scheme and host, use
     * {@link #getRequestURL}.
     *
     * @return a <code>String</code> containing the part of the URL from the
     *         protocol name up to the query string
     * @see #getRequestURL
     */
    public String getRequestURI();

    /**
     * Reconstructs the URL the client used to make the request. The returned
     * URL contains a protocol, server name, port number, and server path, but
     * it does not include query string parameters.
     * <p>
     * Because this method returns a <code>StringBuffer</code>, not a string,
     * you can modify the URL easily, for example, to append query parameters.
     * <p>
     * This method is useful for creating redirect messages and for reporting
     * errors.
     *
     * @return a <code>StringBuffer</code> object containing the reconstructed
     *         URL
     */
    public StringBuffer getRequestURL();

    /**
     * Returns the part of this request's URL that calls the servlet. This path
     * starts with a "/" character and includes either the servlet name or a
     * path to the servlet, but does not include any extra path information or a
     * query string. Same as the value of the CGI variable SCRIPT_NAME.
     * <p>
     * The URL will be canonicalized as per section 3.5 of the specification
     * before the path information, if any, is extracted.
     * <p>
     * This method will return an empty string ("") if the servlet used to
     * process this request was matched using the "/*" pattern.
     *
     * @return a <code>String</code>, canonicalized by the web container,
     *         containing the name or path of the servlet being called, as
     *         specified in the request URL, or an empty string if the servlet
     *         used to process the request is matched using the "/*" pattern.
     */
    public String getServletPath();

    /**
     * Returns the current <code>HttpSession</code> associated with this request
     * or, if there is no current session and <code>create</code> is true,
     * returns a new session.
     * <p>
     * If <code>create</code> is <code>false</code> and the request has no valid
     * <code>HttpSession</code>, this method returns <code>null</code>.
     * <p>
     * To make sure the session is properly maintained, you must call this
     * method before the response is committed. If the container is using
     * cookies to maintain session integrity and is asked to create a new
     * session when the response is committed, an IllegalStateException is
     * thrown.
     *
     * @param create
     *            <code>true</code> to create a new session for this request if
     *            necessary; <code>false</code> to return <code>null</code> if
     *            there's no current session
     * @return the <code>HttpSession</code> associated with this request or
     *         <code>null</code> if <code>create</code> is <code>false</code>
     *         and the request has no valid session
     * @see #getSession()
     */
    public HttpSession getSession(boolean create);

    /**
     * Returns the current session associated with this request, or if the
     * request does not have a session, creates one.
     *
     * @return the <code>HttpSession</code> associated with this request
     * @see #getSession(boolean)
     */
    public HttpSession getSession();

    /**
     * Changes the session ID of the session associated with this request. This
     * method does not create a new session object it only changes the ID of the
     * current session.
     *
     * @return the new session ID allocated to the session
     * @see HttpSessionIdListener
     * @since Servlet 3.1
     */
    public String changeSessionId();

    /**
     * Checks whether the requested session ID is still valid.
     *
     * @return <code>true</code> if this request has an id for a valid session
     *         in the current session context; <code>false</code> otherwise
     * @see #getRequestedSessionId
     * @see #getSession
     */
    public boolean isRequestedSessionIdValid();

    /**
     * Checks whether the requested session ID came in as a cookie.
     *
     * @return <code>true</code> if the session ID came in as a cookie;
     *         otherwise, <code>false</code>
     * @see #getSession
     */
    public boolean isRequestedSessionIdFromCookie();

    /**
     * Checks whether the requested session ID came in as part of the request
     * URL.
     *
     * @return <code>true</code> if the session ID came in as part of a URL;
     *         otherwise, <code>false</code>
     * @see #getSession
     */
    public boolean isRequestedSessionIdFromURL();

    /**
     * Triggers the same authentication process as would be triggered if the
     * request is for a resource that is protected by a security constraint.
     *
     * @param response  The response to use to return any authentication
     *                  challenge
     * @return <code>true</code> if the user is successfully authenticated and
     *         <code>false</code> if not
     *
     * @throws IOException if the authentication process attempted to read from
     *         the request or write to the response and an I/O error occurred
     * @throws IllegalStateException if the authentication process attempted to
     *         write to the response after it had been committed
     * @throws ServletException if the authentication failed and the caller is
     *         expected to handle the failure
     * @since Servlet 3.0
     */
    public boolean authenticate(HttpServletResponse response)
            throws IOException, ServletException;

    /**
     * Authenticate the provided user name and password and then associated the
     * authenticated user with the request.
     *
     * @param username  The user name to authenticate
     * @param password  The password to use to authenticate the user
     *
     * @throws ServletException
     *             If any of {@link #getRemoteUser()},
     *             {@link #getUserPrincipal()} or {@link #getAuthType()} are
     *             non-null, if the configured authenticator does not support
     *             user name and password authentication or if the
     *             authentication fails
     * @since Servlet 3.0
     */
    public void login(String username, String password) throws ServletException;

    /**
     * Removes any authenticated user from the request.
     *
     * @throws ServletException
     *             If the logout fails
     * @since Servlet 3.0
     */
    public void logout() throws ServletException;

    /**
     * Return a collection of all uploaded Parts.
     *
     * @return A collection of all uploaded Parts.
     * @throws IOException
     *             if an I/O error occurs
     * @throws IllegalStateException
     *             if size limits are exceeded or no multipart configuration is
     *             provided
     * @throws ServletException
     *             if the request is not multipart/form-data
     * @since Servlet 3.0
     */
    public Collection<Part> getParts() throws IOException,
            ServletException;

    /**
     * Gets the named Part or null if the Part does not exist. Triggers upload
     * of all Parts.
     *
     * @param name The name of the Part to obtain
     *
     * @return The named Part or null if the Part does not exist
     * @throws IOException
     *             if an I/O error occurs
     * @throws IllegalStateException
     *             if size limits are exceeded
     * @throws ServletException
     *             if the request is not multipart/form-data
     * @since Servlet 3.0
     */
    public Part getPart(String name) throws IOException,
            ServletException;

    /**
     * Start the HTTP upgrade process and create and instance of the provided
     * protocol handler class. The connection will be passed this instance once
     * the current request/response pair has completed processing. Calling this
     * method sets the response status to
     * {@link HttpServletResponse#SC_SWITCHING_PROTOCOLS}.
     *
     * @param <T>                     The type of the upgrade handler
     * @param httpUpgradeHandlerClass The class that implements the upgrade
     *                                handler
     *
     * @return A newly created instance of the specified upgrade handler type
     *
     * @throws IOException
     *             if an I/O error occurred during the upgrade
     * @throws ServletException
     *             if the given httpUpgradeHandlerClass fails to be instantiated
     * @since Servlet 3.1
     */
    public <T extends HttpUpgradeHandler> T upgrade(
            Class<T> httpUpgradeHandlerClass) throws java.io.IOException, ServletException;

    /**
     * Obtain a Map of the trailer fields that is not backed by the request
     * object.
     *
     * @return A Map of the received trailer fields with all keys lower case
     *         or an empty Map if no trailers are present
     *
     * @since Servlet 4.0
     */
    public default Map<String,String> getTrailerFields() {
        return Collections.emptyMap();
    }

    /**
     * Are trailer fields ready to be read (there may still be no trailers to
     * read). This method always returns {@code true} if the underlying protocol
     * does not support trailer fields. Otherwise, {@code true} is returned once
     * all of the following are true:
     * <ul>
     * <li>The application has ready all the request data and an EOF has been
     *     received or the content-length is zero</li>
     * <li>All trailer fields, if any, have been received</li>
     * </ul>
     *
     * @return {@code true} if trailers are ready to be read
     *
     * @since Servlet 4.0
     */
    public default boolean isTrailerFieldsReady() {
        return true;
    }
}

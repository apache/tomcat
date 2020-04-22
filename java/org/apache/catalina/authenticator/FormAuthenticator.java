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
package org.apache.catalina.authenticator;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.coyote.ActionCode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.http.MimeHeaders;

/**
 * An <b>Authenticator</b> and <b>Valve</b> implementation of FORM BASED
 * Authentication, as described in the Servlet API Specification.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class FormAuthenticator
    extends AuthenticatorBase {

    private final Log log = LogFactory.getLog(FormAuthenticator.class); // must not be static


    // ----------------------------------------------------- Instance Variables

    /**
     * Character encoding to use to read the username and password parameters
     * from the request. If not set, the encoding of the request body will be
     * used.
     */
    protected String characterEncoding = null;

    /**
     * Landing page to use if a user tries to access the login page directly or
     * if the session times out during login. If not set, error responses will
     * be sent instead.
     */
    protected String landingPage = null;


    // ------------------------------------------------------------- Properties

    /**
     * Return the character encoding to use to read the user name and password.
     *
     * @return The name of the character encoding
     */
    public String getCharacterEncoding() {
        return characterEncoding;
    }


    /**
     * Set the character encoding to be used to read the user name and password.
     *
     * @param encoding The name of the encoding to use
     */
    public void setCharacterEncoding(String encoding) {
        characterEncoding = encoding;
    }


    /**
     * Return the landing page to use when FORM auth is mis-used.
     *
     * @return The path to the landing page relative to the web application root
     */
    public String getLandingPage() {
        return landingPage;
    }


    /**
     * Set the landing page to use when the FORM auth is mis-used.
     *
     * @param landingPage The path to the landing page relative to the web
     *                    application root
     */
    public void setLandingPage(String landingPage) {
        this.landingPage = landingPage;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Authenticate the user making this request, based on the specified
     * login configuration.  Return <code>true</code> if any specified
     * constraint has been satisfied, or <code>false</code> if we have
     * created a response challenge already.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response)
            throws IOException {

        // References to objects we will need later
        Session session = null;
        Principal principal = null;

        // Have we authenticated this user before but have caching disabled?
        if (!cache) {
            session = request.getSessionInternal(true);
            if (log.isDebugEnabled()) {
                log.debug("Checking for reauthenticate in session " + session);
            }
            String username = (String) session.getNote(Constants.SESS_USERNAME_NOTE);
            String password = (String) session.getNote(Constants.SESS_PASSWORD_NOTE);
            if (username != null && password != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Reauthenticating username '" + username + "'");
                }
                principal = context.getRealm().authenticate(username, password);
                if (principal != null) {
                    register(request, response, principal, HttpServletRequest.FORM_AUTH, username, password);
                    if (!matchRequest(request)) {
                        return true;
                    }
                }
                if (log.isDebugEnabled()) {
                    log.debug("Reauthentication failed, proceed normally");
                }
            }
        }

        // Is this the re-submit of the original request URI after successful
        // authentication?  If so, forward the *original* request instead.
        if (matchRequest(request)) {
            session = request.getSessionInternal(true);
            if (log.isDebugEnabled()) {
                log.debug("Restore request from session '" + session.getIdInternal() + "'");
            }
            if (restoreRequest(request, session)) {
                if (log.isDebugEnabled()) {
                    log.debug("Proceed to restored request");
                }
                return true;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Restore of original request failed");
                }
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return false;
            }
        }

        // This check has to be after the previous check for a matching request
        // because that matching request may also include a cached Principal.
        if (checkForCachedAuthentication(request, response, true)) {
            return true;
        }

        // Acquire references to objects we will need to evaluate
        String contextPath = request.getContextPath();
        String requestURI = request.getDecodedRequestURI();

        // Is this the action request from the login page?
        boolean loginAction = requestURI.startsWith(contextPath) && requestURI.endsWith(Constants.FORM_ACTION);

        LoginConfig config = context.getLoginConfig();

        // No -- Save this request and redirect to the form login page
        if (!loginAction) {
            // If this request was to the root of the context without a trailing
            // '/', need to redirect to add it else the submit of the login form
            // may not go to the correct web application
            if (request.getServletPath().length() == 0 && request.getPathInfo() == null) {
                StringBuilder location = new StringBuilder(requestURI);
                location.append('/');
                if (request.getQueryString() != null) {
                    location.append('?');
                    location.append(request.getQueryString());
                }
                response.sendRedirect(response.encodeRedirectURL(location.toString()));
                return false;
            }

            session = request.getSessionInternal(true);
            if (log.isDebugEnabled()) {
                log.debug("Save request in session '" + session.getIdInternal() + "'");
            }
            try {
                saveRequest(request, session);
            } catch (IOException ioe) {
                log.debug("Request body too big to save during authentication");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, sm.getString("authenticator.requestBodyTooBig"));
                return false;
            }
            forwardToLoginPage(request, response, config);
            return false;
        }

        // Yes -- Acknowledge the request, validate the specified credentials
        // and redirect to the error page if they are not correct
        request.getResponse().sendAcknowledgement();
        Realm realm = context.getRealm();
        if (characterEncoding != null) {
            request.setCharacterEncoding(characterEncoding);
        }
        String username = request.getParameter(Constants.FORM_USERNAME);
        String password = request.getParameter(Constants.FORM_PASSWORD);
        if (log.isDebugEnabled()) {
            log.debug("Authenticating username '" + username + "'");
        }
        principal = realm.authenticate(username, password);
        if (principal == null) {
            forwardToErrorPage(request, response, config);
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("Authentication of '" + username + "' was successful");
        }

        if (session == null) {
            session = request.getSessionInternal(false);
        }
        if (session != null && getChangeSessionIdOnAuthentication()) {
            // Does session id match?
            String expectedSessionId = (String) session.getNote(Constants.SESSION_ID_NOTE);
            if (expectedSessionId == null || !expectedSessionId.equals(request.getRequestedSessionId())) {
                session.expire();
                session = null;
            }
        }
        if (session == null) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug("User took so long to log on the session expired");
            }
            if (landingPage == null) {
                response.sendError(
                        HttpServletResponse.SC_REQUEST_TIMEOUT, sm.getString("authenticator.sessionExpired"));
            } else {
                // Make the authenticator think the user originally requested
                // the landing page
                String uri = request.getContextPath() + landingPage;
                SavedRequest saved = new SavedRequest();
                saved.setMethod("GET");
                saved.setRequestURI(uri);
                saved.setDecodedRequestURI(uri);
                request.getSessionInternal(true).setNote(Constants.FORM_REQUEST_NOTE, saved);
                response.sendRedirect(response.encodeRedirectURL(uri));
            }
            return false;
        }

        register(request, response, principal, HttpServletRequest.FORM_AUTH, username, password);

        // Redirect the user to the original request URI (which will cause
        // the original request to be restored)
        requestURI = savedRequestURL(session);
        if (log.isDebugEnabled()) {
            log.debug("Redirecting to original '" + requestURI + "'");
        }
        if (requestURI == null) {
            if (landingPage == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, sm.getString("authenticator.formlogin"));
            } else {
                // Make the authenticator think the user originally requested
                // the landing page
                String uri = request.getContextPath() + landingPage;
                SavedRequest saved = new SavedRequest();
                saved.setMethod("GET");
                saved.setRequestURI(uri);
                saved.setDecodedRequestURI(uri);
                session.setNote(Constants.FORM_REQUEST_NOTE, saved);
                response.sendRedirect(response.encodeRedirectURL(uri));
            }
        } else {
            // Until the Servlet API allows specifying the type of redirect to
            // use.
            Response internalResponse = request.getResponse();
            String location = response.encodeRedirectURL(requestURI);
            if ("HTTP/1.1".equals(request.getProtocol())) {
                internalResponse.sendRedirect(location, HttpServletResponse.SC_SEE_OTHER);
            } else {
                internalResponse.sendRedirect(location, HttpServletResponse.SC_FOUND);
            }
        }
        return false;
    }


    @Override
    protected boolean isContinuationRequired(Request request) {
        // Special handling for form-based logins to deal with the case
        // where the login form (and therefore the "j_security_check" URI
        // to which it submits) might be outside the secured area
        String contextPath = this.context.getPath();
        String decodedRequestURI = request.getDecodedRequestURI();
        if (decodedRequestURI.startsWith(contextPath) &&
                decodedRequestURI.endsWith(Constants.FORM_ACTION)) {
            return true;
        }

        // Special handling for form-based logins to deal with the case where
        // a resource is protected for some HTTP methods but not protected for
        // GET which is used after authentication when redirecting to the
        // protected resource.
        // TODO: This is similar to the FormAuthenticator.matchRequest() logic
        // Is there a way to remove the duplication?
        Session session = request.getSessionInternal(false);
        if (session != null) {
            SavedRequest savedRequest = (SavedRequest) session.getNote(Constants.FORM_REQUEST_NOTE);
            if (savedRequest != null &&
                    decodedRequestURI.equals(savedRequest.getDecodedRequestURI())) {
                return true;
            }
        }

        return false;
    }


    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.FORM_AUTH;
    }


    @Override
    protected void register(Request request, HttpServletResponse response,
            Principal principal, String authType, String username,
            String password, boolean alwaysUseSession, boolean cache) {

        super.register(request, response, principal, authType, username, password, alwaysUseSession, cache);

        // If caching an authenticated Principal is turned off,
        // store username and password as session notes to use them for re-authentication.
        if (!cache) {
            Session session = request.getSessionInternal(false);
            if (session != null) {
                if (username != null) {
                    session.setNote(Constants.SESS_USERNAME_NOTE, username);
                } else {
                    session.removeNote(Constants.SESS_USERNAME_NOTE);
                }
                if (password != null) {
                    session.setNote(Constants.SESS_PASSWORD_NOTE, password);
                } else {
                    session.removeNote(Constants.SESS_PASSWORD_NOTE);
                }
            }
        }
    }


    /**
     * Called to forward to the login page
     *
     * @param request Request we are processing
     * @param response Response we are populating
     * @param config    Login configuration describing how authentication
     *              should be performed
     * @throws IOException  If the forward to the login page fails and the call
     *                      to {@link HttpServletResponse#sendError(int, String)}
     *                      throws an {@link IOException}
     */
    protected void forwardToLoginPage(Request request,
            HttpServletResponse response, LoginConfig config)
            throws IOException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("formAuthenticator.forwardLogin",
                    request.getRequestURI(), request.getMethod(),
                    config.getLoginPage(), context.getName()));
        }

        String loginPage = config.getLoginPage();
        if (loginPage == null || loginPage.length() == 0) {
            String msg = sm.getString("formAuthenticator.noLoginPage",
                    context.getName());
            log.warn(msg);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    msg);
            return;
        }

        if (getChangeSessionIdOnAuthentication()) {
            Session session = request.getSessionInternal(false);
            if (session != null) {
                String newSessionId = changeSessionID(request, session);
                session.setNote(Constants.SESSION_ID_NOTE, newSessionId);
            }
        }

        // Always use GET for the login page, regardless of the method used
        String oldMethod = request.getMethod();
        request.getCoyoteRequest().method().setString("GET");

        RequestDispatcher disp =
            context.getServletContext().getRequestDispatcher(loginPage);
        try {
            if (context.fireRequestInitEvent(request.getRequest())) {
                disp.forward(request.getRequest(), response);
                context.fireRequestDestroyEvent(request.getRequest());
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            String msg = sm.getString("formAuthenticator.forwardLoginFail");
            log.warn(msg, t);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    msg);
        } finally {
            // Restore original method so that it is written into access log
            request.getCoyoteRequest().method().setString(oldMethod);
        }
    }


    /**
     * Called to forward to the error page
     *
     * @param request Request we are processing
     * @param response Response we are populating
     * @param config    Login configuration describing how authentication
     *              should be performed
     * @throws IOException  If the forward to the error page fails and the call
     *                      to {@link HttpServletResponse#sendError(int, String)}
     *                      throws an {@link IOException}
     */
    protected void forwardToErrorPage(Request request,
            HttpServletResponse response, LoginConfig config)
            throws IOException {

        String errorPage = config.getErrorPage();
        if (errorPage == null || errorPage.length() == 0) {
            String msg = sm.getString("formAuthenticator.noErrorPage",
                    context.getName());
            log.warn(msg);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    msg);
            return;
        }

        RequestDispatcher disp =
                context.getServletContext().getRequestDispatcher(config.getErrorPage());
        try {
            if (context.fireRequestInitEvent(request.getRequest())) {
                disp.forward(request.getRequest(), response);
                context.fireRequestDestroyEvent(request.getRequest());
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            String msg = sm.getString("formAuthenticator.forwardErrorFail");
            log.warn(msg, t);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    msg);
        }
    }


    /**
     * Does this request match the saved one (so that it must be the redirect
     * we signaled after successful authentication?
     *
     * @param request The request to be verified
     * @return <code>true</code> if the requests matched the saved one
     */
    protected boolean matchRequest(Request request) {
        // Has a session been created?
        Session session = request.getSessionInternal(false);
        if (session == null) {
            return false;
        }

        // Is there a saved request?
        SavedRequest sreq = (SavedRequest) session.getNote(Constants.FORM_REQUEST_NOTE);
        if (sreq == null) {
            return false;
        }

        // Is there a saved principal?
        if (cache && session.getPrincipal() == null || !cache && request.getPrincipal() == null) {
            return false;
        }

        // Does session id match?
        if (getChangeSessionIdOnAuthentication()) {
            String expectedSessionId = (String) session.getNote(Constants.SESSION_ID_NOTE);
            if (expectedSessionId == null || !expectedSessionId.equals(request.getRequestedSessionId())) {
                return false;
            }
        }

        // Does the request URI match?
        String decodedRequestURI = request.getDecodedRequestURI();
        if (decodedRequestURI == null) {
            return false;
        }
        return decodedRequestURI.equals(sreq.getDecodedRequestURI());
    }


    /**
     * Restore the original request from information stored in our session.
     * If the original request is no longer present (because the session
     * timed out), return <code>false</code>; otherwise, return
     * <code>true</code>.
     *
     * @param request The request to be restored
     * @param session The session containing the saved information
     * @return <code>true</code> if the request was successfully restored
     * @throws IOException if an IO error occurred during the process
     */
    protected boolean restoreRequest(Request request, Session session)
            throws IOException {

        // Retrieve and remove the SavedRequest object from our session
        SavedRequest saved = (SavedRequest) session.getNote(Constants.FORM_REQUEST_NOTE);
        session.removeNote(Constants.FORM_REQUEST_NOTE);
        session.removeNote(Constants.SESSION_ID_NOTE);
        if (saved == null) {
            return false;
        }

        // Swallow any request body since we will be replacing it
        // Need to do this before headers are restored as AJP connector uses
        // content length header to determine how much data needs to be read for
        // request body
        byte[] buffer = new byte[4096];
        InputStream is = request.createInputStream();
        while (is.read(buffer) >= 0) {
            // Ignore request body
        }

        // Modify our current request to reflect the original one
        request.clearCookies();
        Iterator<Cookie> cookies = saved.getCookies();
        while (cookies.hasNext()) {
            request.addCookie(cookies.next());
        }

        String method = saved.getMethod();
        MimeHeaders rmh = request.getCoyoteRequest().getMimeHeaders();
        rmh.recycle();
        boolean cacheable = "GET".equalsIgnoreCase(method) ||
                           "HEAD".equalsIgnoreCase(method);
        Iterator<String> names = saved.getHeaderNames();
        while (names.hasNext()) {
            String name = names.next();
            // The browser isn't expecting this conditional response now.
            // Assuming that it can quietly recover from an unexpected 412.
            // BZ 43687
            if(!("If-Modified-Since".equalsIgnoreCase(name) ||
                 (cacheable && "If-None-Match".equalsIgnoreCase(name)))) {
                Iterator<String> values = saved.getHeaderValues(name);
                while (values.hasNext()) {
                    rmh.addValue(name).setString(values.next());
                }
            }
        }

        request.clearLocales();
        Iterator<Locale> locales = saved.getLocales();
        while (locales.hasNext()) {
            request.addLocale(locales.next());
        }

        request.getCoyoteRequest().getParameters().recycle();

        ByteChunk body = saved.getBody();

        if (body != null) {
            request.getCoyoteRequest().action
                (ActionCode.REQ_SET_BODY_REPLAY, body);

            // Set content type
            MessageBytes contentType = MessageBytes.newInstance();

            // If no content type specified, use default for POST
            String savedContentType = saved.getContentType();
            if (savedContentType == null && "POST".equalsIgnoreCase(method)) {
                savedContentType = "application/x-www-form-urlencoded";
            }

            contentType.setString(savedContentType);
            request.getCoyoteRequest().setContentType(contentType);
        }

        request.getCoyoteRequest().method().setString(method);
        // The method, URI, queryString and protocol are normally stored as
        // bytes in the HttpInputBuffer and converted lazily to String. At this
        // point, the method has already been set as String in the line above
        // but the URI, queryString and protocol are still in byte form in the
        // HttpInputBuffer. Processing the saved request body will overwrite
        // these bytes. Configuring the HttpInputBuffer to retain these bytes as
        // it would in a normal request would require some invasive API changes.
        // Therefore force the conversion to String now so the correct values
        // are presented if the application requests them.
        request.getRequestURI();
        request.getQueryString();
        request.getProtocol();

        return true;
    }


    /**
     * Save the original request information into our session.
     *
     * @param request The request to be saved
     * @param session The session to contain the saved information
     * @throws IOException if an IO error occurred during the process
     */
    protected void saveRequest(Request request, Session session)
        throws IOException {

        // Create and populate a SavedRequest object for this request
        SavedRequest saved = new SavedRequest();
        Cookie cookies[] = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                saved.addCookie(cookie);
            }
        }
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                String value = values.nextElement();
                saved.addHeader(name, value);
            }
        }
        Enumeration<Locale> locales = request.getLocales();
        while (locales.hasMoreElements()) {
            Locale locale = locales.nextElement();
            saved.addLocale(locale);
        }

        // May need to acknowledge a 100-continue expectation
        request.getResponse().sendAcknowledgement();

        int maxSavePostSize = request.getConnector().getMaxSavePostSize();
        if (maxSavePostSize != 0) {
            ByteChunk body = new ByteChunk();
            body.setLimit(maxSavePostSize);

            byte[] buffer = new byte[4096];
            int bytesRead;
            InputStream is = request.getInputStream();

            while ( (bytesRead = is.read(buffer) ) >= 0) {
                body.append(buffer, 0, bytesRead);
            }

            // Only save the request body if there is something to save
            if (body.getLength() > 0) {
                saved.setContentType(request.getContentType());
                saved.setBody(body);
            }
        }

        saved.setMethod(request.getMethod());
        saved.setQueryString(request.getQueryString());
        saved.setRequestURI(request.getRequestURI());
        saved.setDecodedRequestURI(request.getDecodedRequestURI());

        // Stash the SavedRequest in our session for later use
        session.setNote(Constants.FORM_REQUEST_NOTE, saved);
    }


    /**
     * Return the request URI (with the corresponding query string, if any)
     * from the saved request so that we can redirect to it.
     *
     * @param session Our current session
     * @return the original request URL
     */
    protected String savedRequestURL(Session session) {
        SavedRequest saved =
            (SavedRequest) session.getNote(Constants.FORM_REQUEST_NOTE);
        if (saved == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(saved.getRequestURI());
        if (saved.getQueryString() != null) {
            sb.append('?');
            sb.append(saved.getQueryString());
        }
        return sb.toString();
    }
}

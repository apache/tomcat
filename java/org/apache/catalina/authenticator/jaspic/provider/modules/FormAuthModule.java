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
package org.apache.catalina.authenticator.jaspic.provider.modules;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.MessagePolicy;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;
import javax.security.auth.message.callback.PasswordValidationCallback;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.authenticator.SavedRequest;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.coyote.ActionCode;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.http.MimeHeaders;

/**
 * This class implements JASPIC FORM-based authentication.
 */
public class FormAuthModule extends TomcatAuthModule {
    private static final Log log = LogFactory.getLog(FormAuthModule.class);

    private Class<?>[] supportedMessageTypes = new Class[] { HttpServletRequest.class,
            HttpServletResponse.class };

    private String landingPage;

    private Realm realm;
    private LoginConfig loginConfig;


    public FormAuthModule(Context context) {
        super(context);
        this.realm = context.getRealm();
        this.loginConfig = context.getLoginConfig();
    }


    @SuppressWarnings("rawtypes")
    @Override
    public void initializeModule(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
            CallbackHandler handler, Map options) throws AuthException {
    }


    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException {
        if (!isMandatory(messageInfo)) {
            return AuthStatus.SUCCESS;
        }
        try {
            return validate(messageInfo, clientSubject);
        } catch (Exception e) {
            throw new AuthException(e.getMessage());
        }
    }


    private AuthStatus validate(MessageInfo messageInfo, Subject clientSubject) throws IOException,
            UnsupportedCallbackException {
        Request request = (Request) messageInfo.getRequestMessage();
        HttpServletResponse response = (HttpServletResponse) messageInfo.getResponseMessage();

        // Have we authenticated this user before but have caching disabled?
        if (!isCache()) {
            Session session = request.getSessionInternal(true);
            if (log.isDebugEnabled()) {
                log.debug("Checking for reauthenticate in session " + session);
            }
            String username = (String) session.getNote(Constants.SESS_USERNAME_NOTE);
            String password = (String) session.getNote(Constants.SESS_PASSWORD_NOTE);
            if ((username != null) && (password != null)) {
                if (log.isDebugEnabled()) {
                    log.debug("Reauthenticating username '" + username + "'");
                }
                PasswordValidationCallback passwordCallback = new PasswordValidationCallback(
                        clientSubject, username, password.toCharArray());
                handler.handle(new Callback[] { passwordCallback });

                if (!passwordCallback.getResult()) {
                    forwardToErrorPage(request, response);
                }
                Principal principal = getPrincipal(passwordCallback);
                if (principal != null) {
                    session.setNote(Constants.FORM_PRINCIPAL_NOTE, principal);
                    if (!isMatchingSavedRequest(request)) {
                        handlePrincipalCallbacks(clientSubject, principal);
                        return AuthStatus.SUCCESS;
                    }
                }
                if (log.isDebugEnabled()) {
                    log.debug("Reauthentication failed, proceed normally");
                }
            }
        }

        // Is this the re-submit of the original request URI after
        // successful
        // authentication? If so, forward the *original* request instead.
        if (isMatchingSavedRequest(request)) {
            return submitSavedRequest(clientSubject, request, response);
        }

        String contextPath = request.getContextPath();
        String requestURI = request.getDecodedRequestURI();

        // Is this the action request from the login page?
        boolean loginAction = requestURI.startsWith(contextPath)
                && requestURI.endsWith(Constants.FORM_ACTION);

        if (!loginAction) {
            return handleNoLoginAction(request, response);
        }

        return handleLoginAction(request, response);
    }


    private AuthStatus submitSavedRequest(Subject clientSubject, Request request,
            HttpServletResponse response) throws IOException, UnsupportedCallbackException {
        Session session = request.getSessionInternal(true);
        if (log.isDebugEnabled()) {
            log.debug("Restore request from session '" + session.getIdInternal() + "'");
        }
        Principal principal = (Principal) session.getNote(Constants.FORM_PRINCIPAL_NOTE);
        handlePrincipalCallbacks(clientSubject, principal);

        // If we're caching principals we no longer need getPrincipal the
        // username
        // and password in the session, so remove them
        if (isCache()) {
            session.removeNote(Constants.SESS_USERNAME_NOTE);
            session.removeNote(Constants.SESS_PASSWORD_NOTE);
        }
        if (restoreRequest(request, session)) {
            if (log.isDebugEnabled()) {
                log.debug("Proceed to restored request");
            }
            return AuthStatus.SUCCESS;
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Restore of original request failed");
            }
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return AuthStatus.FAILURE;
        }
    }


    /**
     * Save this request and redirect to the form login page
     *
     * @param request
     * @param response
     * @return
     * @throws IOException
     */
    private AuthStatus handleNoLoginAction(Request request, HttpServletResponse response)
            throws IOException {
        Session session = request.getSessionInternal(true);
        if (log.isDebugEnabled()) {
            log.debug("Save request in session '" + session.getIdInternal() + "'");
        }
        try {
            saveRequest(request, session);
        } catch (IOException ioe) {
            log.debug("Request body too big to save during authentication");
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    sm.getString("authenticator.requestBodyTooBig"));
            return AuthStatus.FAILURE;
        }

        forwardToLoginPage(request, response);
        return AuthStatus.SEND_CONTINUE;
    }


    /**
     * Acknowledge the request, validate the specified and redirect to the error
     * page if they are not correct
     *
     * @param request
     * @param response
     * @return
     * @throws IOException
     */
    private AuthStatus handleLoginAction(Request request, HttpServletResponse response)
            throws IOException {

        request.getResponse().sendAcknowledgement();

        // TODO fix character encoding
        // if (characterEncoding != null) {
        // request.setCharacterEncoding(characterEncoding);
        // }

        String username = request.getParameter(Constants.FORM_USERNAME);
        String password = request.getParameter(Constants.FORM_PASSWORD);
        if (log.isDebugEnabled()) {
            log.debug("Authenticating username '" + username + "'");
        }
        Principal principal = realm.authenticate(username, password);
        if (principal == null) {
            forwardToErrorPage(request, response);
            return AuthStatus.FAILURE;
        }

        if (log.isDebugEnabled()) {
            log.debug("Authentication of '" + username + "' was successful");
        }

        Session session = request.getSessionInternal(false);
        if (session == null) {
            handleSessionExpired(request, response);
            return AuthStatus.FAILURE;
        }

        // Save the authenticated Principal in our session
        session.setNote(Constants.FORM_PRINCIPAL_NOTE, principal);

        // Save the username and password as well
        session.setNote(Constants.SESS_USERNAME_NOTE, username);
        session.setNote(Constants.SESS_PASSWORD_NOTE, password);

        // Redirect the user to the original request URI (which will cause
        // the original request to be restored)
        String savedRequestUrl = savedRequestURL(session);
        if (log.isDebugEnabled()) {
            log.debug("Redirecting to original '" + savedRequestUrl + "'");
        }
        if (savedRequestUrl == null) {
            if (landingPage == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        sm.getString("authenticator.formlogin"));
            } else {
                // Make the authenticator think the user originally
                // requested
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
            // Until the Servlet API allows specifying the type of redirect
            // to
            // use.
            Response internalResponse = request.getResponse();
            String location = response.encodeRedirectURL(savedRequestUrl);
            if ("HTTP/1.1".equals(request.getProtocol())) {
                internalResponse.sendRedirect(location, HttpServletResponse.SC_SEE_OTHER);
            } else {
                internalResponse.sendRedirect(location, HttpServletResponse.SC_FOUND);
            }
        }
        return AuthStatus.FAILURE;
    }


    private void handleSessionExpired(Request request, HttpServletResponse response)
            throws IOException {
        if (landingPage == null) {
            response.sendError(HttpServletResponse.SC_REQUEST_TIMEOUT,
                    sm.getString("authenticator.sessionExpired"));
            return;
        }
        // Make the authenticator think the user originally
        // requested
        // the landing page
        String uri = request.getContextPath() + landingPage;
        SavedRequest saved = new SavedRequest();
        saved.setMethod("GET");
        saved.setRequestURI(uri);
        saved.setDecodedRequestURI(uri);
        request.getSessionInternal(true).setNote(Constants.FORM_REQUEST_NOTE, saved);
    }


    private void handlePrincipalCallbacks(Subject clientSubject, Principal principal)
            throws IOException, UnsupportedCallbackException {
        CallerPrincipalCallback principalCallback = new CallerPrincipalCallback(clientSubject,
                principal);
        GroupPrincipalCallback groupCallback = new GroupPrincipalCallback(clientSubject, context
                .getRealm().getRoles(principal));
        handler.handle(new Callback[] { principalCallback, groupCallback });
    }


    private boolean isCache() {
        return true;
    }


    @Override
    public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject)
            throws AuthException {
        return null;
    }


    @Override
    public void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException {
    }


    @Override
    public Class<?>[] getSupportedMessageTypes() {
        return supportedMessageTypes;
    }


    private GenericPrincipal getPrincipal(PasswordValidationCallback passwordCallback) {
        Iterator<Object> credentials = passwordCallback.getSubject().getPrivateCredentials()
                .iterator();
        return (GenericPrincipal) credentials.next();
    }


    /**
     * Called to forward to the login page
     *
     * @param request Request we are processing
     * @param response Response we are populating
     * @throws IOException If the forward to the login page fails and the call
     *             to {@link HttpServletResponse#sendError(int, String)} throws
     *             an {@link IOException}
     */
    protected void forwardToLoginPage(Request request, HttpServletResponse response)
            throws IOException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("formAuthenticator.forwardLogin", request.getRequestURI(),
                    request.getMethod(), loginConfig.getLoginPage(), context.getName()));
        }

        String loginPage = loginConfig.getLoginPage();
        if (loginPage == null || loginPage.length() == 0) {
            String msg = sm.getString("formAuthenticator.noLoginPage", context.getName());
            log.warn(msg);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
            return;
        }

        if (getChangeSessionIdOnAuthentication()) {
            Session session = request.getSessionInternal(false);
            if (session != null) {
                Manager manager = request.getContext().getManager();
                manager.changeSessionId(session);
                request.changeSessionId(session.getId());
            }
        }

        // Always use GET for the login page, regardless of the method used
        String oldMethod = request.getMethod();
        request.getCoyoteRequest().method().setString("GET");

        RequestDispatcher disp = context.getServletContext().getRequestDispatcher(loginPage);
        try {
            if (context.fireRequestInitEvent(request)) {
                disp.forward(request.getRequest(), response);
                context.fireRequestDestroyEvent(request);
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            String msg = sm.getString("formAuthenticator.forwardLoginFail");
            log.warn(msg, t);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
        } finally {
            // Restore original method so that it is written into access log
            request.getCoyoteRequest().method().setString(oldMethod);
        }
    }


    private boolean getChangeSessionIdOnAuthentication() {
        return true;        // FIXME
    }


    /**
     * Called to forward to the error page
     *
     * @param request Request we are processing
     * @param response Response we are populating @throws IOException If the
     *            forward to the error page fails and the call to
     *            {@link HttpServletResponse#sendError(int, String)} throws an
     *            {@link IOException}
     */
    protected void forwardToErrorPage(Request request, HttpServletResponse response)
            throws IOException {

        String errorPage = loginConfig.getErrorPage();
        if (errorPage == null || errorPage.length() == 0) {
            String msg = sm.getString("formAuthenticator.noErrorPage", context.getName());
            log.warn(msg);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
            return;
        }

        RequestDispatcher disp = context.getServletContext().getRequestDispatcher(
                loginConfig.getErrorPage());
        try {
            if (context.fireRequestInitEvent(request)) {
                disp.forward(request.getRequest(), response);
                context.fireRequestDestroyEvent(request);
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            String msg = sm.getString("formAuthenticator.forwardErrorFail");
            log.warn(msg, t);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, t);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
        }
    }


    /**
     * Does this request match the saved one (so that it must be the redirect we
     * signaled after successful authentication?
     *
     * @param request The request to be verified
     */
    protected boolean isMatchingSavedRequest(Request request) {
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
        if (session.getNote(Constants.FORM_PRINCIPAL_NOTE) == null) {
            return false;
        }

        // Does the request URI match?
        String decodedRequestURI = request.getDecodedRequestURI();
        if (decodedRequestURI == null) {
            return false;
        }
        return decodedRequestURI.equals(sreq.getDecodedRequestURI());
    }


    /**
     * Restore the original request from information stored in our session. If
     * the original request is no longer present (because the session timed
     * out), return <code>false</code>; otherwise, return <code>true</code>.
     *
     * @param request The request to be restored
     * @param session The session containing the saved information
     */
    protected boolean restoreRequest(Request request, Session session) throws IOException {

        // Retrieve and remove the SavedRequest object from our session
        SavedRequest saved = (SavedRequest) session.getNote(Constants.FORM_REQUEST_NOTE);
        session.removeNote(Constants.FORM_REQUEST_NOTE);
        session.removeNote(Constants.FORM_PRINCIPAL_NOTE);
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
        boolean cachable = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        Iterator<String> names = saved.getHeaderNames();
        while (names.hasNext()) {
            String name = names.next();
            // The browser isn't expecting this conditional response now.
            // Assuming that it can quietly recover from an unexpected 412.
            // BZ 43687
            if (!("If-Modified-Since".equalsIgnoreCase(name) || (cachable && "If-None-Match"
                    .equalsIgnoreCase(name)))) {
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
        request.getCoyoteRequest().getParameters()
                .setQueryStringEncoding(request.getConnector().getURIEncoding());

        ByteChunk body = saved.getBody();

        if (body != null) {
            request.getCoyoteRequest().action(ActionCode.REQ_SET_BODY_REPLAY, body);

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

        return true;
    }


    /**
     * Save the original request information into our session.
     *
     * @param request The request to be saved
     * @param session The session to contain the saved information
     * @throws IOException
     */
    protected void saveRequest(Request request, Session session) throws IOException {

        // Create and populate a SavedRequest object for this request
        SavedRequest saved = new SavedRequest();
        Cookie cookies[] = request.getCookies();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {
                saved.addCookie(cookies[i]);
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

        ByteChunk body = new ByteChunk();
        body.setLimit(request.getConnector().getMaxSavePostSize());

        byte[] buffer = new byte[4096];
        int bytesRead;
        InputStream is = request.getInputStream();

        while ((bytesRead = is.read(buffer)) >= 0) {
            body.append(buffer, 0, bytesRead);
        }

        // Only save the request body if there is something to save
        if (body.getLength() > 0) {
            saved.setContentType(request.getContentType());
            saved.setBody(body);
        }

        saved.setMethod(request.getMethod());
        saved.setQueryString(request.getQueryString());
        saved.setRequestURI(request.getRequestURI());
        saved.setDecodedRequestURI(request.getDecodedRequestURI());

        // Stash the SavedRequest in our session for later use
        session.setNote(Constants.FORM_REQUEST_NOTE, saved);
    }


    /**
     * Return the request URI (with the corresponding query string, if any) from
     * the saved request so that we can redirect to it.
     *
     * @param session Our current session
     */
    protected String savedRequestURL(Session session) {

        SavedRequest saved = (SavedRequest) session.getNote(Constants.FORM_REQUEST_NOTE);
        if (saved == null) {
            return (null);
        }
        StringBuilder sb = new StringBuilder(saved.getRequestURI());
        if (saved.getQueryString() != null) {
            sb.append('?');
            sb.append(saved.getQueryString());
        }
        return (sb.toString());

    }
}

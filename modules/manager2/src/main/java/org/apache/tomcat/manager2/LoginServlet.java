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
package org.apache.tomcat.manager2;

import java.io.IOException;
import java.lang.reflect.Field;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.apache.catalina.Valve;
import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.authenticator.FormAuthenticator;
import org.apache.catalina.authenticator.SavedRequest;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.RequestFacade;


/**
 * Renders the login page (the FORM authentication {@code form-login-page}) and makes sure that a successful login
 * always sends the browser back to the SPA root.
 * <p>
 * FORM authentication remembers the request that triggered the redirect to the login page (the "saved request") and,
 * after a successful login, redirects the browser to it. That saved request is whatever the browser requested last
 * while unauthenticated - which, for a single-page application, is frequently a CSS or JS file or a JSON API call. To
 * keep the post-login redirect pointing at the application, the saved request is replaced with a GET of the context
 * root ({@code /}) every time the login page is rendered.
 * <p>
 * The {@code landingPage} of the {@link FormAuthenticator} is set as well, as a safety net for logins where no request
 * was saved at all.
 */
public class LoginServlet extends HttpServlet {


    private static final long serialVersionUID = 1L;

    private static final String TEMPLATE = "/login.html";
    private static final String APP_ROOT = "/";

    private volatile boolean landingConfigured = false;

    // The servlet request chain ends in a RequestFacade (which wraps the
    // container Request but is not an HttpServletRequestWrapper). The
    // container Request is only reachable through this protected field, so
    // it is resolved once via reflection.
    private static final Field REQUEST_FACADE_REQUEST_FIELD = resolveRequestFacadeField();

    private static Field resolveRequestFacadeField() {
        try {
            Field field = RequestFacade.class.getDeclaredField("request");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        renderLogin(request, response);
    }


    /**
     * The form-error-page forward (after a failed login) keeps the POST method of the login form submission, so the
     * login page is rendered for POST requests as well.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        renderLogin(request, response);
    }


    private void renderLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Request containerRequest = unwrap(request);

        if (containerRequest != null) {
            pointSavedRequestAtAppRoot(containerRequest);
            if (!landingConfigured) {
                setLandingPage(containerRequest);
            }
        }

        String template = Html.readTemplate(getServletContext(), TEMPLATE);
        if (template == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    Strings.manager(request).getString("manager2.loginTemplateMissing"));
            return;
        }
        if (request.getParameter("error") != null) {
            template = template.replace("id=\"login-error\" hidden", "id=\"login-error\"");
        }
        Html.render(request, response, template);
    }


    /**
     * Normalize the FORM authentication saved request, if any, so that the post-login redirect lands in the
     * application.
     * <p>
     * When this servlet renders the login page on behalf of HomeServlet for an SPA route (the user's browser was on,
     * e.g., the Applications page when the session expired and re-navigated to that page, which the server then gated),
     * the forwarded request still carries the route URI and the saved request is pointed at that route, so that a
     * successful login returns the user to exactly the page they were on.
     * <p>
     * Otherwise, a saved request that already points at an SPA route is kept as-is and any other saved request (an API
     * call, a static asset, ...) is replaced with a GET of the SPA root.
     * <p>
     * A session is created (if the browser does not have one yet) so that the session cookie is issued with the login
     * page and the subsequent submission of the login form is tied to this session.
     */
    private void pointSavedRequestAtAppRoot(Request request) {
        Session session = request.getSessionInternal(true);
        // Record the current session ID. FormAuthenticator verifies the
        // session ID against this note during login and expires the session
        // when the note is missing (it is normally written when the
        // authenticator changes the session ID while forwarding to the
        // login page, which does not happen when this servlet renders the
        // login page on behalf of HomeServlet).
        session.setNote(Constants.SESSION_ID_NOTE, session.getIdInternal());

        String contextPath = request.getContextPath();

        // This login page was rendered for a page route (deep-link gate by
        // HomeServlet): point the saved request at that route so that a
        // successful login lands the user back on that page.
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith(contextPath) && isSpaRoute(requestUri.substring(contextPath.length()))) {
            session.setNote(Constants.FORM_REQUEST_NOTE, savedRequest(requestUri));
            return;
        }

        Object note = session.getNote(Constants.FORM_REQUEST_NOTE);
        if (note instanceof SavedRequest saved && "GET".equals(saved.getMethod())) {
            String uri = saved.getRequestURI();
            if (uri != null && uri.startsWith(contextPath) && isSpaRoute(uri.substring(contextPath.length()))) {
                // Keep the saved request: it points at a page of this
                // application, so restoring it after login lands the user
                // back on that page.
                return;
            }
        }

        session.setNote(Constants.FORM_REQUEST_NOTE, savedRequest(contextPath + APP_ROOT));
    }


    private static SavedRequest savedRequest(String uri) {
        SavedRequest saved = new SavedRequest();
        saved.setMethod("GET");
        saved.setRequestURI(uri);
        saved.setDecodedRequestURI(uri);
        return saved;
    }


    /**
     * The routes that the SPA renders client-side and that HomeServlet serves as the application shell.
     */
    private static boolean isSpaRoute(String path) {
        if (path == null) {
            return false;
        }
        if (path.isEmpty() || "/".equals(path)) {
            return true;
        }
        return "/apps".equals(path) || path.startsWith("/apps/") || "/hosts".equals(path) ||
                "/configuration".equals(path) || "/monitoring".equals(path) || "/diagnostics".equals(path) ||
                "/logs".equals(path) || "/access-log".equals(path) || "/users".equals(path);
    }


    /**
     * Set the landing page of the FORM authenticator so that a successful login without a saved request still ends up
     * at the SPA root instead of a 400.
     */
    private void setLandingPage(Request request) {
        synchronized (this) {
            if (landingConfigured) {
                return;
            }
            Context context = request.getContext();
            if (context != null) {
                for (Valve valve : context.getPipeline().getValves()) {
                    if (valve instanceof FormAuthenticator formAuthenticator &&
                            formAuthenticator.getLandingPage() == null) {
                        formAuthenticator.setLandingPage(APP_ROOT);
                    }
                }
            }
            landingConfigured = true;
        }
    }


    /**
     * Unwrap the servlet request wrappers to reach the container request.
     *
     * @param request the servlet request
     * 
     * @return the container request, or {@code null} if the chain does not end in one (should not happen in a standard
     *             Tomcat deployment)
     */
    private static Request unwrap(HttpServletRequest request) {
        HttpServletRequest current = request;
        int depth = 0;
        while (current instanceof HttpServletRequestWrapper wrapper) {
            current = (HttpServletRequest) wrapper.getRequest();
            if (++depth > 10) {
                return null;
            }
        }
        if (current instanceof Request containerRequest) {
            return containerRequest;
        }
        if (current instanceof RequestFacade && REQUEST_FACADE_REQUEST_FIELD != null) {
            try {
                return (Request) REQUEST_FACADE_REQUEST_FIELD.get(current);
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }
}

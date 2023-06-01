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
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import javax.servlet.http.PushBuilder;

import org.apache.catalina.Globals;
import org.apache.catalina.security.SecurityUtil;
import org.apache.tomcat.util.res.StringManager;

/**
 * Facade class that wraps a Coyote request object. All methods are delegated to the wrapped request.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
@SuppressWarnings("deprecation")
public class RequestFacade implements HttpServletRequest {


    private final class GetAttributePrivilegedAction implements PrivilegedAction<Enumeration<String>> {

        @Override
        public Enumeration<String> run() {
            return request.getAttributeNames();
        }
    }


    private final class GetParameterMapPrivilegedAction implements PrivilegedAction<Map<String,String[]>> {

        @Override
        public Map<String,String[]> run() {
            return request.getParameterMap();
        }
    }


    private final class GetRequestDispatcherPrivilegedAction implements PrivilegedAction<RequestDispatcher> {

        private final String path;

        GetRequestDispatcherPrivilegedAction(String path) {
            this.path = path;
        }

        @Override
        public RequestDispatcher run() {
            return request.getRequestDispatcher(path);
        }
    }


    private final class GetParameterPrivilegedAction implements PrivilegedAction<String> {

        public String name;

        GetParameterPrivilegedAction(String name) {
            this.name = name;
        }

        @Override
        public String run() {
            return request.getParameter(name);
        }
    }


    private final class GetParameterNamesPrivilegedAction implements PrivilegedAction<Enumeration<String>> {

        @Override
        public Enumeration<String> run() {
            return request.getParameterNames();
        }
    }


    private final class GetParameterValuePrivilegedAction implements PrivilegedAction<String[]> {

        public String name;

        GetParameterValuePrivilegedAction(String name) {
            this.name = name;
        }

        @Override
        public String[] run() {
            return request.getParameterValues(name);
        }
    }


    private final class GetCookiesPrivilegedAction implements PrivilegedAction<Cookie[]> {

        @Override
        public Cookie[] run() {
            return request.getCookies();
        }
    }


    private final class GetCharacterEncodingPrivilegedAction implements PrivilegedAction<String> {

        @Override
        public String run() {
            return request.getCharacterEncoding();
        }
    }


    private final class GetHeadersPrivilegedAction implements PrivilegedAction<Enumeration<String>> {

        private final String name;

        GetHeadersPrivilegedAction(String name) {
            this.name = name;
        }

        @Override
        public Enumeration<String> run() {
            return request.getHeaders(name);
        }
    }


    private final class GetHeaderNamesPrivilegedAction implements PrivilegedAction<Enumeration<String>> {

        @Override
        public Enumeration<String> run() {
            return request.getHeaderNames();
        }
    }


    private final class GetLocalePrivilegedAction implements PrivilegedAction<Locale> {

        @Override
        public Locale run() {
            return request.getLocale();
        }
    }


    private final class GetLocalesPrivilegedAction implements PrivilegedAction<Enumeration<Locale>> {

        @Override
        public Enumeration<Locale> run() {
            return request.getLocales();
        }
    }

    private final class GetSessionPrivilegedAction implements PrivilegedAction<HttpSession> {

        private final boolean create;

        GetSessionPrivilegedAction(boolean create) {
            this.create = create;
        }

        @Override
        public HttpSession run() {
            return request.getSession(create);
        }
    }


    private static final StringManager sm = StringManager.getManager(RequestFacade.class);


    /**
     * The wrapped request.
     */
    protected Request request = null;


    /**
     * Construct a wrapper for the specified request.
     *
     * @param request The request to be wrapped
     */
    public RequestFacade(Request request) {
        this.request = request;
    }


    /**
     * Clear facade.
     */
    public void clear() {
        request = null;
    }


    /**
     * Prevent cloning the facade.
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }


    // ------------------------------------------------- ServletRequest Methods

    @Override
    public Object getAttribute(String name) {
        checkFacade();
        return request.getAttribute(name);
    }


    @Override
    public Enumeration<String> getAttributeNames() {
        checkFacade();

        if (Globals.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(new GetAttributePrivilegedAction());
        } else {
            return request.getAttributeNames();
        }
    }


    @Override
    public String getCharacterEncoding() {
        checkFacade();

        if (Globals.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(new GetCharacterEncodingPrivilegedAction());
        } else {
            return request.getCharacterEncoding();
        }
    }


    @Override
    public void setCharacterEncoding(String env) throws java.io.UnsupportedEncodingException {
        checkFacade();
        request.setCharacterEncoding(env);
    }


    @Override
    public int getContentLength() {
        checkFacade();
        return request.getContentLength();
    }


    @Override
    public String getContentType() {
        checkFacade();
        return request.getContentType();
    }


    @Override
    public ServletInputStream getInputStream() throws IOException {
        checkFacade();
        return request.getInputStream();
    }


    @Override
    public String getParameter(String name) {
        checkFacade();

        if (Globals.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(new GetParameterPrivilegedAction(name));
        } else {
            return request.getParameter(name);
        }
    }


    @Override
    public Enumeration<String> getParameterNames() {
        checkFacade();

        if (Globals.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(new GetParameterNamesPrivilegedAction());
        } else {
            return request.getParameterNames();
        }
    }


    @Override
    public String[] getParameterValues(String name) {
        checkFacade();

        String[] ret = null;

        /*
         * Clone the returned array only if there is a security manager in place, so that performance won't suffer in
         * the non-secure case
         */
        if (SecurityUtil.isPackageProtectionEnabled()) {
            ret = AccessController.doPrivileged(new GetParameterValuePrivilegedAction(name));
            if (ret != null) {
                ret = ret.clone();
            }
        } else {
            ret = request.getParameterValues(name);
        }

        return ret;
    }


    @Override
    public Map<String,String[]> getParameterMap() {
        checkFacade();

        if (Globals.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(new GetParameterMapPrivilegedAction());
        } else {
            return request.getParameterMap();
        }
    }


    @Override
    public String getProtocol() {
        checkFacade();
        return request.getProtocol();
    }


    @Override
    public String getScheme() {
        checkFacade();
        return request.getScheme();
    }


    @Override
    public String getServerName() {
        checkFacade();
        return request.getServerName();
    }


    @Override
    public int getServerPort() {
        checkFacade();
        return request.getServerPort();
    }


    @Override
    public BufferedReader getReader() throws IOException {
        checkFacade();
        return request.getReader();
    }


    @Override
    public String getRemoteAddr() {
        checkFacade();
        return request.getRemoteAddr();
    }


    @Override
    public String getRemoteHost() {
        checkFacade();
        return request.getRemoteHost();
    }


    @Override
    public void setAttribute(String name, Object o) {
        checkFacade();
        request.setAttribute(name, o);
    }


    @Override
    public void removeAttribute(String name) {
        checkFacade();
        request.removeAttribute(name);
    }


    @Override
    public Locale getLocale() {
        checkFacade();

        if (Globals.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(new GetLocalePrivilegedAction());
        } else {
            return request.getLocale();
        }
    }


    @Override
    public Enumeration<Locale> getLocales() {
        checkFacade();

        if (Globals.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(new GetLocalesPrivilegedAction());
        } else {
            return request.getLocales();
        }
    }


    @Override
    public boolean isSecure() {
        checkFacade();
        return request.isSecure();
    }


    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        checkFacade();

        if (Globals.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(new GetRequestDispatcherPrivilegedAction(path));
        } else {
            return request.getRequestDispatcher(path);
        }
    }

    @Override
    public String getRealPath(String path) {

        if (request == null) {
            throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
        }

        return request.getRealPath(path);
    }


    @Override
    public String getAuthType() {
        checkFacade();
        return request.getAuthType();
    }


    @Override
    public Cookie[] getCookies() {
        checkFacade();

        Cookie[] ret = null;

        /*
         * Clone the returned array only if there is a security manager in place, so that performance won't suffer in
         * the non-secure case
         */
        if (SecurityUtil.isPackageProtectionEnabled()) {
            ret = AccessController.doPrivileged(new GetCookiesPrivilegedAction());
            if (ret != null) {
                ret = ret.clone();
            }
        } else {
            ret = request.getCookies();
        }

        return ret;
    }


    @Override
    public long getDateHeader(String name) {
        checkFacade();
        return request.getDateHeader(name);
    }


    @Override
    public String getHeader(String name) {
        checkFacade();
        return request.getHeader(name);
    }


    @Override
    public Enumeration<String> getHeaders(String name) {
        checkFacade();

        if (Globals.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(new GetHeadersPrivilegedAction(name));
        } else {
            return request.getHeaders(name);
        }
    }


    @Override
    public Enumeration<String> getHeaderNames() {
        checkFacade();

        if (Globals.IS_SECURITY_ENABLED) {
            return AccessController.doPrivileged(new GetHeaderNamesPrivilegedAction());
        } else {
            return request.getHeaderNames();
        }
    }


    @Override
    public int getIntHeader(String name) {
        checkFacade();
        return request.getIntHeader(name);
    }


    @Override
    public HttpServletMapping getHttpServletMapping() {
        checkFacade();
        return request.getHttpServletMapping();
    }


    @Override
    public String getMethod() {
        checkFacade();
        return request.getMethod();
    }


    @Override
    public String getPathInfo() {
        checkFacade();
        return request.getPathInfo();
    }


    @Override
    public String getPathTranslated() {
        checkFacade();
        return request.getPathTranslated();
    }


    @Override
    public String getContextPath() {
        checkFacade();
        return request.getContextPath();
    }


    @Override
    public String getQueryString() {
        checkFacade();
        return request.getQueryString();
    }


    @Override
    public String getRemoteUser() {
        checkFacade();
        return request.getRemoteUser();
    }


    @Override
    public boolean isUserInRole(String role) {
        checkFacade();
        return request.isUserInRole(role);
    }


    @Override
    public java.security.Principal getUserPrincipal() {
        checkFacade();
        return request.getUserPrincipal();
    }


    @Override
    public String getRequestedSessionId() {
        checkFacade();
        return request.getRequestedSessionId();
    }


    @Override
    public String getRequestURI() {
        checkFacade();
        return request.getRequestURI();
    }


    @Override
    public StringBuffer getRequestURL() {
        checkFacade();
        return request.getRequestURL();
    }


    @Override
    public String getServletPath() {
        checkFacade();
        return request.getServletPath();
    }


    @Override
    public HttpSession getSession(boolean create) {
        checkFacade();

        if (SecurityUtil.isPackageProtectionEnabled()) {
            return AccessController.doPrivileged(new GetSessionPrivilegedAction(create));
        } else {
            return request.getSession(create);
        }
    }

    @Override
    public HttpSession getSession() {
        checkFacade();
        return getSession(true);
    }

    @Override
    public String changeSessionId() {
        checkFacade();
        return request.changeSessionId();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        checkFacade();
        return request.isRequestedSessionIdValid();
    }


    @Override
    public boolean isRequestedSessionIdFromCookie() {
        checkFacade();
        return request.isRequestedSessionIdFromCookie();
    }


    @Override
    public boolean isRequestedSessionIdFromURL() {
        checkFacade();
        return request.isRequestedSessionIdFromURL();
    }


    @Override
    public boolean isRequestedSessionIdFromUrl() {

        if (request == null) {
            throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
        }

        return request.isRequestedSessionIdFromURL();
    }


    @Override
    public String getLocalAddr() {
        checkFacade();
        return request.getLocalAddr();
    }


    @Override
    public String getLocalName() {
        checkFacade();
        return request.getLocalName();
    }


    @Override
    public int getLocalPort() {
        checkFacade();
        return request.getLocalPort();
    }


    @Override
    public int getRemotePort() {
        checkFacade();
        return request.getRemotePort();
    }


    @Override
    public ServletContext getServletContext() {
        checkFacade();
        return request.getServletContext();
    }


    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        checkFacade();
        return request.startAsync();
    }


    @Override
    public AsyncContext startAsync(ServletRequest request, ServletResponse response) throws IllegalStateException {
        checkFacade();
        return this.request.startAsync(request, response);
    }


    @Override
    public boolean isAsyncStarted() {
        checkFacade();
        return request.isAsyncStarted();
    }


    @Override
    public boolean isAsyncSupported() {
        checkFacade();
        return request.isAsyncSupported();
    }


    @Override
    public AsyncContext getAsyncContext() {
        checkFacade();
        return request.getAsyncContext();
    }


    @Override
    public DispatcherType getDispatcherType() {
        checkFacade();
        return request.getDispatcherType();
    }


    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        checkFacade();
        return request.authenticate(response);
    }

    @Override
    public void login(String username, String password) throws ServletException {
        checkFacade();
        request.login(username, password);
    }

    @Override
    public void logout() throws ServletException {
        checkFacade();
        request.logout();
    }

    @Override
    public Collection<Part> getParts() throws IllegalStateException, IOException, ServletException {
        checkFacade();
        return request.getParts();
    }


    @Override
    public Part getPart(String name) throws IllegalStateException, IOException, ServletException {
        checkFacade();
        return request.getPart(name);
    }


    public boolean getAllowTrace() {
        checkFacade();
        return request.getConnector().getAllowTrace();
    }


    @Override
    public long getContentLengthLong() {
        checkFacade();
        return request.getContentLengthLong();
    }


    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> httpUpgradeHandlerClass)
            throws java.io.IOException, ServletException {
        checkFacade();
        return request.upgrade(httpUpgradeHandlerClass);
    }


    @Override
    public PushBuilder newPushBuilder() {
        checkFacade();
        return request.newPushBuilder();
    }


    public PushBuilder newPushBuilder(HttpServletRequest request) {
        checkFacade();
        return this.request.newPushBuilder(request);
    }


    @Override
    public boolean isTrailerFieldsReady() {
        checkFacade();
        return request.isTrailerFieldsReady();
    }


    @Override
    public Map<String,String> getTrailerFields() {
        checkFacade();
        return request.getTrailerFields();
    }


    private void checkFacade() {
        if (request == null) {
            throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
        }
    }
}

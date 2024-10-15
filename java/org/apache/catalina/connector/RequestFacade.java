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
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

import org.apache.tomcat.util.res.StringManager;

/**
 * Facade class that wraps a Coyote request object. All methods are delegated to the wrapped request.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public class RequestFacade implements HttpServletRequest {


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
        return request.getAttributeNames();
    }


    @Override
    public String getCharacterEncoding() {
        checkFacade();
        return request.getCharacterEncoding();
    }


    @Override
    public void setCharacterEncoding(String encoding) throws java.io.UnsupportedEncodingException {
        checkFacade();
        request.setCharacterEncoding(encoding);
    }


    @Override
    public void setCharacterEncoding(Charset encoding) {
        checkFacade();
        request.setCharacterEncoding(encoding);
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
        return request.getParameter(name);
    }


    @Override
    public Enumeration<String> getParameterNames() {
        checkFacade();
        return request.getParameterNames();
    }


    @Override
    public String[] getParameterValues(String name) {
        checkFacade();
        return request.getParameterValues(name);
    }


    @Override
    public Map<String,String[]> getParameterMap() {
        checkFacade();
        return request.getParameterMap();
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
        return request.getLocale();
    }


    @Override
    public Enumeration<Locale> getLocales() {
        checkFacade();
        return request.getLocales();
    }


    @Override
    public boolean isSecure() {
        checkFacade();
        return request.isSecure();
    }


    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        checkFacade();
        return request.getRequestDispatcher(path);
    }

    @Override
    public String getAuthType() {
        checkFacade();
        return request.getAuthType();
    }


    @Override
    public Cookie[] getCookies() {
        checkFacade();
        return request.getCookies();
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
        return request.getHeaders(name);
    }


    @Override
    public Enumeration<String> getHeaderNames() {
        checkFacade();
        return request.getHeaderNames();
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
        return request.getSession(create);
    }

    @Override
    public HttpSession getSession() {
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
            throws IOException, ServletException {
        checkFacade();
        return request.upgrade(httpUpgradeHandlerClass);
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


    @Override
    public String getRequestId() {
        checkFacade();
        return request.getRequestId();
    }


    @Override
    public String getProtocolRequestId() {
        checkFacade();
        return request.getProtocolRequestId();
    }


    @Override
    public ServletConnection getServletConnection() {
        checkFacade();
        return request.getServletConnection();
    }


    private void checkFacade() {
        if (request == null) {
            throw new IllegalStateException(sm.getString("requestFacade.nullRequest"));
        }
    }
}

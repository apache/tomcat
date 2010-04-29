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


package org.apache.catalina.core;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Response;
import org.apache.tomcat.util.buf.MessageBytes;


/**
 * Dummy request object, used for request dispatcher mapping, as well as
 * JSP precompilation.
 *
 * @author Remy Maucherat
 * @version $Id$
 */

public class DummyRequest
    implements HttpServletRequest {

    public DummyRequest() {
    }

    public DummyRequest(String contextPath, String decodedURI,
                        String queryString) {
        this.contextPath = contextPath;
        this.decodedURI = decodedURI;
        this.queryString = queryString;
    }

    protected String contextPath = null;
    protected String decodedURI = null;
    protected String queryString = null;

    protected String pathInfo = null;
    protected String servletPath = null;
    protected Wrapper wrapper = null;

    protected FilterChain filterChain = null;
    
    private static Enumeration<String> dummyEnum = new Enumeration<String>(){
        public boolean hasMoreElements(){
            return false;
        }
        public String nextElement(){
            return null;
        }
    };

    public String getContextPath() {
        return (contextPath);
    }

    public MessageBytes getContextPathMB() {
        return null;
    }

    public ServletRequest getRequest() {
        return (this);
    }

    public String getDecodedRequestURI() {
        return decodedURI;
    }

    public MessageBytes getDecodedRequestURIMB() {
        return null;
    }

    public FilterChain getFilterChain() {
        return (this.filterChain);
    }

    public void setFilterChain(FilterChain filterChain) {
        this.filterChain = filterChain;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String query) {
        queryString = query;
    }

    public String getPathInfo() {
        return pathInfo;
    }

    public void setPathInfo(String path) {
        pathInfo = path;
    }

    public MessageBytes getPathInfoMB() {
        return null;
    }

    public MessageBytes getRequestPathMB() {
        return null;
    }

    public String getServletPath() {
        return servletPath;
    }

    public void setServletPath(String path) {
        servletPath = path;
    }

    public MessageBytes getServletPathMB() {
        return null;
    }

    public Wrapper getWrapper() {
        return (this.wrapper);
    }

    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
    }

    public String getAuthorization() { return null; }
    public void setAuthorization(
            @SuppressWarnings("unused") String authorization) {}
    public Connector getConnector() { return null; }
    public void setConnector(@SuppressWarnings("unused") Connector connector) {}
    public Context getContext() { return null; }
    public void setContext(@SuppressWarnings("unused") Context context) {}
    public Host getHost() { return null; }
    public void setHost(@SuppressWarnings("unused") Host host) {}
    public String getInfo() { return null; }
    public Response getResponse() { return null; }
    public void setResponse(@SuppressWarnings("unused") Response response) {}
    public Socket getSocket() { return null; }
    public void setSocket(@SuppressWarnings("unused") Socket socket) {}
    public InputStream getStream() { return null; }
    public void setStream(@SuppressWarnings("unused") InputStream input) {}
    public void addLocale(@SuppressWarnings("unused") Locale locale) {}
    public ServletInputStream createInputStream() throws IOException {
        return null;
    }
    public void finishRequest() throws IOException {}
    public Object getNote(@SuppressWarnings("unused") String name) {
        return null;
    }
    public Iterator<String> getNoteNames() { return null; }
    public void removeNote(@SuppressWarnings("unused") String name) {}
    public void setContentType(@SuppressWarnings("unused") String type) {}
    public void setNote(@SuppressWarnings("unused") String name,
            @SuppressWarnings("unused") Object value) {}
    public void setProtocol(@SuppressWarnings("unused") String protocol) {}
    public void setRemoteAddr(@SuppressWarnings("unused") String remoteAddr) {}
    public void setRemoteHost(@SuppressWarnings("unused") String remoteHost) {}
    public void setScheme(@SuppressWarnings("unused") String scheme) {}
    public void setServerName(@SuppressWarnings("unused") String name) {}
    public void setServerPort(@SuppressWarnings("unused") int port) {}
    public Object getAttribute(String name) { return null; }
    public Enumeration<String> getAttributeNames() { return null; }
    public String getCharacterEncoding() { return null; }
    public int getContentLength() { return -1; }
    public void setContentLength(@SuppressWarnings("unused") int length) {}
    public String getContentType() { return null; }
    public ServletInputStream getInputStream() throws IOException {
        return null;
    }
    public Locale getLocale() { return null; }
    public Enumeration<Locale> getLocales() { return null; }
    public String getProtocol() { return null; }
    public BufferedReader getReader() throws IOException { return null; }
    /** @deprecated */
    @Deprecated
    public String getRealPath(String path) { return null; }
    public String getRemoteAddr() { return null; }
    public String getRemoteHost() { return null; }
    public String getScheme() { return null; }
    public String getServerName() { return null; }
    public int getServerPort() { return -1; }
    public boolean isSecure() { return false; }
    public void removeAttribute(String name) {}
    public void setAttribute(String name, Object value) {}
    public void setCharacterEncoding(String enc)
        throws UnsupportedEncodingException {}
    public void addCookie(@SuppressWarnings("unused") Cookie cookie) {}
    public void addHeader(@SuppressWarnings("unused") String name,
            @SuppressWarnings("unused") String value) {}
    public void addParameter(@SuppressWarnings("unused") String name,
            @SuppressWarnings("unused") String values[]) {}
    public void clearCookies() {}
    public void clearHeaders() {}
    public void clearLocales() {}
    public void clearParameters() {}
    public void recycle() {}
    public void setAuthType(@SuppressWarnings("unused") String authType) {}
    public void setContextPath(@SuppressWarnings("unused") String path) {}
    public void setMethod(@SuppressWarnings("unused") String method) {}
    public void setRequestedSessionCookie(
            @SuppressWarnings("unused") boolean flag) {}
    public void setRequestedSessionId(@SuppressWarnings("unused") String id) {}
    public void setRequestedSessionURL(
            @SuppressWarnings("unused") boolean flag) {}
    public void setRequestURI(@SuppressWarnings("unused") String uri) {}
    public void setSecure(@SuppressWarnings("unused") boolean secure) {}
    public void setUserPrincipal(
            @SuppressWarnings("unused") Principal principal) {}
    public String getParameter(String name) { return null; }
    public Map<String,String[]> getParameterMap() { return null; }
    public Enumeration<String> getParameterNames() { return dummyEnum; }
    public String[] getParameterValues(String name) { return null; }
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }
    public String getAuthType() { return null; }
    public Cookie[] getCookies() { return null; }
    public long getDateHeader(String name) { return -1; }
    public String getHeader(String name) { return null; }
    public Enumeration<String> getHeaders(String name) { return null; }
    public Enumeration<String> getHeaderNames() { return null; }
    public int getIntHeader(String name) { return -1; }
    public String getMethod() { return null; }
    public String getPathTranslated() { return null; }
    public String getRemoteUser() { return null; }
    public String getRequestedSessionId() { return null; }
    public String getRequestURI() { return null; }
    public void setDecodedRequestURI(@SuppressWarnings("unused") String uri) {}
    public StringBuffer getRequestURL() { return null; }
    public HttpSession getSession() { return null; }
    public HttpSession getSession(boolean create) { return null; }
    public boolean isRequestedSessionIdFromCookie() { return false; }
    public boolean isRequestedSessionIdFromURL() { return false; }
    /** @deprecated */
    @Deprecated
    public boolean isRequestedSessionIdFromUrl() { return false; }
    public boolean isRequestedSessionIdValid() { return false; }
    public boolean isUserInRole(String role) { return false; }
    public Principal getUserPrincipal() { return null; }
    public String getLocalAddr() { return null; }    
    public String getLocalName() { return null; }
    public int getLocalPort() { return -1; }
    public int getRemotePort() { return -1; }
    public ServletContext getServletContext() { return null; }
    public boolean isAsyncStarted() { return false; }
    public boolean isAsyncSupported() { return false; }
    public AsyncContext startAsync() throws IllegalStateException {
        return null;
    }
    public Part getPart(String name) { return null; }
    public Collection<Part> getParts() { return null; }
    public boolean authenticate(HttpServletResponse response)
    throws IOException, ServletException { return false; }
    public void login(String username, String password)
    throws ServletException {}
    public void logout() throws ServletException {}
    public AsyncContext getAsyncContext() { return null; }
    public DispatcherType getDispatcherType() { return null; }
    public AsyncContext startAsync(ServletRequest servletRequest,
            ServletResponse servletResponse) { return null; }
}


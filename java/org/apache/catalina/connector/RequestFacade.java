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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import org.apache.catalina.Globals;
import org.apache.catalina.security.SecurityUtil;
import org.apache.tomcat.util.res.StringManager;

/**
 * Facade class that wraps a Coyote request object.  
 * All methods are delegated to the wrapped request.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @author Jean-Francois Arcand
 * @version $Id$
 */

@SuppressWarnings("deprecation")
public class RequestFacade implements HttpServletRequest {
        
        
    // ----------------------------------------------------------- DoPrivileged
    
    private final class GetAttributePrivilegedAction
            implements PrivilegedAction<Enumeration<String>> {
        
        public Enumeration<String> run() {
            return request.getAttributeNames();
        }            
    }
     
    
    private final class GetParameterMapPrivilegedAction
            implements PrivilegedAction<Map<String,String[]>> {
        
        public Map<String,String[]> run() {
            return request.getParameterMap();
        }        
    }    
    
    
    private final class GetRequestDispatcherPrivilegedAction
            implements PrivilegedAction<RequestDispatcher> {

        private String path;

        public GetRequestDispatcherPrivilegedAction(String path){
            this.path = path;
        }
        
        public RequestDispatcher run() {   
            return request.getRequestDispatcher(path);
        }           
    }    
    
    
    private final class GetParameterPrivilegedAction
            implements PrivilegedAction<String> {

        public String name;

        public GetParameterPrivilegedAction(String name){
            this.name = name;
        }

        public String run() {       
            return request.getParameter(name);
        }           
    }    
    
     
    private final class GetParameterNamesPrivilegedAction
            implements PrivilegedAction<Enumeration<String>> {
        
        public Enumeration<String> run() {          
            return request.getParameterNames();
        }           
    } 
    
    
    private final class GetParameterValuePrivilegedAction
            implements PrivilegedAction<String[]> {

        public String name;

        public GetParameterValuePrivilegedAction(String name){
            this.name = name;
        }

        public String[] run() {       
            return request.getParameterValues(name);
        }           
    }    
  
    
    private final class GetCookiesPrivilegedAction
            implements PrivilegedAction<Cookie[]> {
        
        public Cookie[] run() {       
            return request.getCookies();
        }           
    }      
    
    
    private final class GetCharacterEncodingPrivilegedAction
            implements PrivilegedAction<String> {
        
        public String run() {       
            return request.getCharacterEncoding();
        }           
    }   
        
    
    private final class GetHeadersPrivilegedAction
            implements PrivilegedAction<Enumeration<String>> {

        private String name;

        public GetHeadersPrivilegedAction(String name){
            this.name = name;
        }
        
        public Enumeration<String> run() {       
            return request.getHeaders(name);
        }           
    }    
        
    
    private final class GetHeaderNamesPrivilegedAction
            implements PrivilegedAction<Enumeration<String>> {

        public Enumeration<String> run() {       
            return request.getHeaderNames();
        }           
    }  
            
    
    private final class GetLocalePrivilegedAction
            implements PrivilegedAction<Locale> {

        public Locale run() {       
            return request.getLocale();
        }           
    }    
            
    
    private final class GetLocalesPrivilegedAction
            implements PrivilegedAction<Enumeration<Locale>> {

        public Enumeration<Locale> run() {       
            return request.getLocales();
        }           
    }    
    
    private final class GetSessionPrivilegedAction
            implements PrivilegedAction<HttpSession> {

        private boolean create;
        
        public GetSessionPrivilegedAction(boolean create){
            this.create = create;
        }
                
        public HttpSession run() {  
            return request.getSession(create);
        }           
    }

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a wrapper for the specified request.
     *
     * @param request The request to be wrapped
     */
    public RequestFacade(Request request) {

        this.request = request;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The wrapped request.
     */
    protected Request request = null;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods


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
    protected Object clone()
        throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }


    // ------------------------------------------------- ServletRequest Methods


    public Object getAttribute(String name) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getAttribute(name);
    }


    public Enumeration<String> getAttributeNames() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetAttributePrivilegedAction());        
        } else {
            return request.getAttributeNames();
        }
    }


    public String getCharacterEncoding() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetCharacterEncodingPrivilegedAction());
        } else {
            return request.getCharacterEncoding();
        }         
    }


    public void setCharacterEncoding(String env)
            throws java.io.UnsupportedEncodingException {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        request.setCharacterEncoding(env);
    }


    public int getContentLength() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getContentLength();
    }


    public String getContentType() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getContentType();
    }


    public ServletInputStream getInputStream() throws IOException {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getInputStream();
    }


    public String getParameter(String name) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetParameterPrivilegedAction(name));
        } else {
            return request.getParameter(name);
        }
    }


    public Enumeration<String> getParameterNames() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetParameterNamesPrivilegedAction());
        } else {
            return request.getParameterNames();
        }
    }


    public String[] getParameterValues(String name) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        String[] ret = null;

        /*
         * Clone the returned array only if there is a security manager
         * in place, so that performance won't suffer in the non-secure case
         */
        if (SecurityUtil.isPackageProtectionEnabled()){
            ret = AccessController.doPrivileged(
                new GetParameterValuePrivilegedAction(name));
            if (ret != null) {
                ret = ret.clone();
            }
        } else {
            ret = request.getParameterValues(name);
        }

        return ret;
    }


    public Map<String,String[]> getParameterMap() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetParameterMapPrivilegedAction());        
        } else {
            return request.getParameterMap();
        }
    }


    public String getProtocol() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getProtocol();
    }


    public String getScheme() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getScheme();
    }


    public String getServerName() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getServerName();
    }


    public int getServerPort() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getServerPort();
    }


    public BufferedReader getReader() throws IOException {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getReader();
    }


    public String getRemoteAddr() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRemoteAddr();
    }


    public String getRemoteHost() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRemoteHost();
    }


    public void setAttribute(String name, Object o) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        request.setAttribute(name, o);
    }


    public void removeAttribute(String name) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        request.removeAttribute(name);
    }


    public Locale getLocale() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetLocalePrivilegedAction());
        } else {
            return request.getLocale();
        }        
    }


    public Enumeration<Locale> getLocales() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetLocalesPrivilegedAction());
        } else {
            return request.getLocales();
        }        
    }


    public boolean isSecure() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.isSecure();
    }


    public RequestDispatcher getRequestDispatcher(String path) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetRequestDispatcherPrivilegedAction(path));
        } else {
            return request.getRequestDispatcher(path);
        }
    }

    public String getRealPath(String path) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRealPath(path);
    }


    public String getAuthType() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getAuthType();
    }


    public Cookie[] getCookies() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        Cookie[] ret = null;

        /*
         * Clone the returned array only if there is a security manager
         * in place, so that performance won't suffer in the non-secure case
         */
        if (SecurityUtil.isPackageProtectionEnabled()){
            ret = AccessController.doPrivileged(
                new GetCookiesPrivilegedAction());
            if (ret != null) {
                ret = ret.clone();
            }
        } else {
            ret = request.getCookies();
        }

        return ret;
    }


    public long getDateHeader(String name) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getDateHeader(name);
    }


    public String getHeader(String name) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getHeader(name);
    }


    public Enumeration<String> getHeaders(String name) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetHeadersPrivilegedAction(name));
        } else {
            return request.getHeaders(name);
        }         
    }


    public Enumeration<String> getHeaderNames() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (Globals.IS_SECURITY_ENABLED){
            return AccessController.doPrivileged(
                new GetHeaderNamesPrivilegedAction());
        } else {
            return request.getHeaderNames();
        }             
    }


    public int getIntHeader(String name) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getIntHeader(name);
    }


    public String getMethod() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getMethod();
    }


    public String getPathInfo() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getPathInfo();
    }


    public String getPathTranslated() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getPathTranslated();
    }


    public String getContextPath() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getContextPath();
    }


    public String getQueryString() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getQueryString();
    }


    public String getRemoteUser() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRemoteUser();
    }


    public boolean isUserInRole(String role) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.isUserInRole(role);
    }


    public java.security.Principal getUserPrincipal() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getUserPrincipal();
    }


    public String getRequestedSessionId() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRequestedSessionId();
    }


    public String getRequestURI() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRequestURI();
    }


    public StringBuffer getRequestURL() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRequestURL();
    }


    public String getServletPath() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getServletPath();
    }


    public HttpSession getSession(boolean create) {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        if (SecurityUtil.isPackageProtectionEnabled()){
            return AccessController.
                doPrivileged(new GetSessionPrivilegedAction(create));
        } else {
            return request.getSession(create);
        }
    }

    public HttpSession getSession() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return getSession(true);
    }


    public boolean isRequestedSessionIdValid() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.isRequestedSessionIdValid();
    }


    public boolean isRequestedSessionIdFromCookie() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.isRequestedSessionIdFromCookie();
    }


    public boolean isRequestedSessionIdFromURL() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.isRequestedSessionIdFromURL();
    }


    public boolean isRequestedSessionIdFromUrl() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.isRequestedSessionIdFromURL();
    }


    public String getLocalAddr() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getLocalAddr();
    }


    public String getLocalName() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getLocalName();
    }


    public int getLocalPort() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getLocalPort();
    }


    public int getRemotePort() {

        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getRemotePort();
    }


    public ServletContext getServletContext() {
        if (request == null) {
            throw new IllegalStateException(
                            sm.getString("requestFacade.nullRequest"));
        }

        return request.getServletContext();
    }


    public AsyncContext startAsync() throws IllegalStateException {
        return request.startAsync();
    }


    public AsyncContext startAsync(ServletRequest request, ServletResponse response)
    throws IllegalStateException {
        return request.startAsync(request, response);
    }


    public boolean isAsyncStarted() {
        return request.isAsyncStarted();
    }


    public boolean isAsyncSupported() {
        return request.isAsyncSupported();
    }

    
    public AsyncContext getAsyncContext() {
        return request.getAsyncContext();
    }

    public DispatcherType getDispatcherType() {
        return request.getDispatcherType();
    }
    
    public boolean authenticate(HttpServletResponse response)
    throws IOException, ServletException {
        return request.authenticate(response);
    }

    public void login(String username, String password)
    throws ServletException {
        request.login(username, password);
    }
    
    public void logout() throws ServletException {
        request.logout();
    }
    
    public Collection<Part> getParts() throws IllegalStateException,
            IOException, ServletException {
        return request.getParts();
    }
    
    public Part getPart(String name) throws IllegalStateException, IOException,
            ServletException {
        return request.getPart(name);
    }

    public boolean getAllowTrace() {
        return request.getConnector().getAllowTrace();
    }
    
}

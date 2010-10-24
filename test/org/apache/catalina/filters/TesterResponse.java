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


package org.apache.catalina.filters;


import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;


/**
 * Dummy response object, used for JSP precompilation.
 *
 * @author Remy Maucherat
 * @version $Id$
 */

public class TesterResponse
    implements HttpServletResponse {

    public TesterResponse() {
    }


    public void setAppCommitted(
            @SuppressWarnings("unused") boolean appCommitted) {}
    public boolean isAppCommitted() { return false; }
    public Connector getConnector() { return null; }
    public void setConnector(@SuppressWarnings("unused") Connector connector) {}
    public int getContentCount() { return -1; }
    public Context getContext() { return null; }
    public void setContext(@SuppressWarnings("unused") Context context) {}
    public boolean getIncluded() { return false; }
    public void setIncluded(@SuppressWarnings("unused") boolean included) {}
    public String getInfo() { return null; }
    public Request getRequest() { return null; }
    public void setRequest(@SuppressWarnings("unused") Request request) {}
    public ServletResponse getResponse() { return null; }
    public OutputStream getStream() { return null; }
    public void setStream(@SuppressWarnings("unused") OutputStream stream) {}
    public void setSuspended(@SuppressWarnings("unused") boolean suspended) {}
    public boolean isSuspended() { return false; }
    public void setError() {}
    public boolean isError() { return false; }
    public ServletOutputStream createOutputStream() throws IOException {
        return null;
    }
    public void finishResponse() throws IOException {}
    public int getContentLength() { return -1; }
    public String getContentType() { return null; }
    public PrintWriter getReporter() { return null; }
    public void recycle() {}
    public void write(@SuppressWarnings("unused") int b) throws IOException {}
    public void write(@SuppressWarnings("unused") byte b[]) throws IOException {
    }
    public void write(@SuppressWarnings("unused") byte b[],
            @SuppressWarnings("unused") int off,
            @SuppressWarnings("unused") int len) throws IOException {}
    public void flushBuffer() throws IOException {}
    public int getBufferSize() { return -1; }
    public String getCharacterEncoding() { return null; }
    public void setCharacterEncoding(String charEncoding) {}
    public ServletOutputStream getOutputStream() throws IOException {
        return null;
    }
    public Locale getLocale() { return null; }
    public PrintWriter getWriter() throws IOException { return null; }
    public boolean isCommitted() { return false; }
    public void reset() {}
    public void resetBuffer() {}
    public void setBufferSize(int size) {}
    public void setContentLength(int length) {}
    public void setContentType(String type) {}
    public void setLocale(Locale locale) {}

    public String getHeader(String name) { return null; }
    public Collection<String> getHeaderNames() { return null; }
    public Collection<String> getHeaders(String name) { return null; }
    public String getMessage() { return null; }
    public int getStatus() { return -1; }
    public void reset(@SuppressWarnings("unused") int status,
            @SuppressWarnings("unused") String message) {}
    public void addCookie(Cookie cookie) {}
    public void addDateHeader(String name, long value) {}
    public void addHeader(String name, String value) {}
    public void addIntHeader(String name, int value) {}
    public boolean containsHeader(String name) { return false; }
    public String encodeRedirectURL(String url) { return null; }
    /** @deprecated */
    @Deprecated
    public String encodeRedirectUrl(String url) { return null; }
    public String encodeURL(String url) { return null; }
    /** @deprecated */
    @Deprecated
    public String encodeUrl(String url) { return null; }
    public void sendAcknowledgement() throws IOException {}
    public void sendError(int status) throws IOException {}
    public void sendError(int status, String message) throws IOException {}
    public void sendRedirect(String location) throws IOException {}
    public void setDateHeader(String name, long value) {}
    public void setHeader(String name, String value) {}
    public void setIntHeader(String name, int value) {}
    public void setStatus(int status) {}
    /** @deprecated */
    @Deprecated
    public void setStatus(int status, String message) {}
}

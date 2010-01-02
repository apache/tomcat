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


package org.apache.tomcat.lite.servlet;


import java.io.IOException;
import java.util.Locale;

import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;


/**
 * Wrapper around the response object received as parameter to 
 * RequestDispatcher.include().
 * 
 * @author Costin Manolache
 */
public class ServletResponseIncludeWrapper extends HttpServletResponseWrapper {
    public ServletResponseIncludeWrapper(ServletResponse current) {
        super((HttpServletResponse) current);
    }

    // Not overriden:
    /*
    public boolean containsHeader(String name)
    public String encodeRedirectUrl(String url)
    public String encodeRedirectURL(String url)
    public String encodeUrl(String url)
    public String encodeURL(String url)
    public void flushBuffer() throws IOException
    public int getBufferSize()
    public String getCharacterEncoding()
    public String getContentType()
    public Locale getLocale()
    public ServletOutputStream getOutputStream() throws IOException
    public ServletResponse getResponse()
    public PrintWriter getWriter() throws IOException
    public boolean isCommitted()
    public void resetBuffer()
    public void setCharacterEncoding(String charset)
    public void setResponse(ServletResponse response)
     */
    
    public void reset() {
        if (getResponse().isCommitted())
            getResponse().reset(); 
        else
            throw new IllegalStateException();
    }

    public void setContentLength(int len) {
    }

    public void setContentType(String type) {
    }

    public void setLocale(Locale loc) {
    }

    public void setBufferSize(int size) {
    }

    public void addCookie(Cookie cookie) {
    }

    public void addDateHeader(String name, long value) {
    }

    public void addHeader(String name, String value) {
    }

    public void addIntHeader(String name, int value) {
    }

    public void sendError(int sc) throws IOException {
    }

    public void sendError(int sc, String msg) throws IOException {
    }

    public void sendRedirect(String location) throws IOException {
    }

    public void setDateHeader(String name, long value) {
    }

    public void setHeader(String name, String value) {
    }

    public void setIntHeader(String name, int value) {
    }

    public void setStatus(int sc) {
    }

    public void setStatus(int sc, String msg) {
    }
}

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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.res.StringManager;

/**
 * Facade class that wraps a Coyote response object. All methods are delegated to the wrapped response.
 *
 * @author Remy Maucherat
 */
public class ResponseFacade implements HttpServletResponse {

    // ----------------------------------------------------------- Constructors

    /**
     * Construct a wrapper for the specified response.
     *
     * @param response The response to be wrapped
     */
    public ResponseFacade(Response response) {
        this.response = response;
    }


    // ----------------------------------------------- Class/Instance Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(ResponseFacade.class);


    /**
     * The wrapped response.
     */
    protected Response response = null;


    // --------------------------------------------------------- Public Methods

    /**
     * Clear facade.
     */
    public void clear() {
        response = null;
    }


    /**
     * Prevent cloning the facade.
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }


    public void finish() {
        checkFacade();
        response.setSuspended(true);
    }


    public boolean isFinished() {
        checkFacade();
        return response.isSuspended();
    }


    public long getContentWritten() {
        checkFacade();
        return response.getContentWritten();
    }


    // ------------------------------------------------ ServletResponse Methods

    @Override
    public String getCharacterEncoding() {
        checkFacade();
        return response.getCharacterEncoding();
    }


    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (isFinished()) {
            response.setSuspended(true);
        }
        return response.getOutputStream();
    }


    @Override
    public PrintWriter getWriter() throws IOException {
        if (isFinished()) {
            response.setSuspended(true);
        }
        return response.getWriter();
    }


    @Override
    public void setContentLength(int len) {
        if (isCommitted()) {
            return;
        }
        response.setContentLength(len);
    }


    @Override
    public void setContentLengthLong(long length) {
        if (isCommitted()) {
            return;
        }
        response.setContentLengthLong(length);
    }


    @Override
    public void setContentType(String type) {
        if (isCommitted()) {
            return;
        }
        response.setContentType(type);
    }


    @Override
    public void setBufferSize(int size) {
        checkCommitted("coyoteResponse.setBufferSize.ise");
        response.setBufferSize(size);
    }


    @Override
    public int getBufferSize() {
        checkFacade();
        return response.getBufferSize();
    }


    @Override
    public void flushBuffer() throws IOException {
        if (isFinished()) {
            return;
        }
        response.setAppCommitted(true);
        response.flushBuffer();
    }


    @Override
    public void resetBuffer() {
        checkCommitted("coyoteResponse.resetBuffer.ise");
        response.resetBuffer();
    }


    @Override
    public boolean isCommitted() {
        checkFacade();
        return response.isAppCommitted();
    }


    @Override
    public void reset() {
        checkCommitted("coyoteResponse.reset.ise");
        response.reset();
    }


    @Override
    public void setLocale(Locale loc) {
        if (isCommitted()) {
            return;
        }
        response.setLocale(loc);
    }


    @Override
    public Locale getLocale() {
        checkFacade();
        return response.getLocale();
    }


    @Override
    public void addCookie(Cookie cookie) {
        if (isCommitted()) {
            return;
        }
        response.addCookie(cookie);
    }


    @Override
    public boolean containsHeader(String name) {
        checkFacade();
        return response.containsHeader(name);
    }


    @Override
    public String encodeURL(String url) {
        checkFacade();
        return response.encodeURL(url);
    }


    @Override
    public String encodeRedirectURL(String url) {
        checkFacade();
        return response.encodeRedirectURL(url);
    }


    @Override
    public void sendEarlyHints() {
        response.sendEarlyHints();
    }

    /**
     * {@inheritDoc}
     * <p>
     * <i>Deprecated functionality</i>: calling <code>sendError</code> with a status code of 103 differs from the usual
     * behavior. Sending 103 will trigger the container to send a "103 Early Hints" informational response including all
     * current headers. The application can continue to use the request and response after calling sendError with a 103
     * status code, including triggering a more typical response of any type.
     * <p>
     * Starting with Tomcat 12, applications should use {@link #sendEarlyHints}.
     */
    @Override
    public void sendError(int sc, String msg) throws IOException {
        checkCommitted("coyoteResponse.sendError.ise");
        if (HttpServletResponse.SC_EARLY_HINTS == sc) {
            sendEarlyHints();
        } else {
            response.setAppCommitted(true);
            response.sendError(sc, msg);
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * <i>Deprecated functionality</i>: calling <code>sendError</code> with a status code of 103 differs from the usual
     * behavior. Sending 103 will trigger the container to send a "103 Early Hints" informational response including all
     * current headers. The application can continue to use the request and response after calling sendError with a 103
     * status code, including triggering a more typical response of any type.
     * <p>
     * Starting with Tomcat 12, applications should use {@link #sendEarlyHints}.
     */
    @Override
    public void sendError(int sc) throws IOException {
        checkCommitted("coyoteResponse.sendError.ise");
        if (HttpServletResponse.SC_EARLY_HINTS == sc) {
            sendEarlyHints();
        } else {
            response.setAppCommitted(true);
            response.sendError(sc);
        }
    }


    @Override
    public void sendRedirect(String location, int sc, boolean clearBuffer) throws IOException {
        checkCommitted("coyoteResponse.sendRedirect.ise");
        response.setAppCommitted(true);
        response.sendRedirect(location, sc, clearBuffer);
    }


    @Override
    public void setDateHeader(String name, long date) {
        if (isCommitted()) {
            return;
        }
        response.setDateHeader(name, date);
    }


    @Override
    public void addDateHeader(String name, long date) {
        if (isCommitted()) {
            return;
        }
        response.addDateHeader(name, date);
    }


    @Override
    public void setHeader(String name, String value) {
        if (isCommitted()) {
            return;
        }
        response.setHeader(name, value);
    }


    @Override
    public void addHeader(String name, String value) {
        if (isCommitted()) {
            return;
        }
        response.addHeader(name, value);
    }


    @Override
    public void setIntHeader(String name, int value) {
        if (isCommitted()) {
            return;
        }
        response.setIntHeader(name, value);
    }


    @Override
    public void addIntHeader(String name, int value) {
        if (isCommitted()) {
            return;
        }
        response.addIntHeader(name, value);
    }


    @Override
    public void setStatus(int sc) {
        if (isCommitted()) {
            return;
        }
        response.setStatus(sc);
    }


    @Override
    public String getContentType() {
        checkFacade();
        return response.getContentType();
    }


    @Override
    public void setCharacterEncoding(String encoding) {
        checkFacade();
        response.setCharacterEncoding(encoding);
    }

    @Override
    public void setCharacterEncoding(Charset charset) {
        checkFacade();
        response.setCharacterEncoding(charset);
    }

    @Override
    public int getStatus() {
        checkFacade();
        return response.getStatus();
    }

    @Override
    public String getHeader(String name) {
        checkFacade();
        return response.getHeader(name);
    }

    @Override
    public Collection<String> getHeaderNames() {
        checkFacade();
        return response.getHeaderNames();
    }

    @Override
    public Collection<String> getHeaders(String name) {
        checkFacade();
        return response.getHeaders(name);
    }


    @Override
    public void setTrailerFields(Supplier<Map<String,String>> supplier) {
        checkFacade();
        response.setTrailerFields(supplier);
    }


    @Override
    public Supplier<Map<String,String>> getTrailerFields() {
        checkFacade();
        return response.getTrailerFields();
    }


    private void checkFacade() {
        if (response == null) {
            throw new IllegalStateException(sm.getString("responseFacade.nullResponse"));
        }
    }


    private void checkCommitted(String messageKey) {
        if (isCommitted()) {
            throw new IllegalStateException(sm.getString(messageKey));
        }
    }
}

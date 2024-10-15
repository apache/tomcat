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
package org.apache.catalina.ssi;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Locale;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.apache.tomcat.util.http.FastHttpDateFormat;

/**
 * An HttpServletResponseWrapper, used from <code>SSIServletExternalResolver</code>
 *
 * @author Bip Thelin
 * @author David Becker
 */
public class ResponseIncludeWrapper extends HttpServletResponseWrapper {
    /**
     * The names of some headers we want to capture.
     */
    private static final String LAST_MODIFIED = "last-modified";

    protected long lastModified = -1;

    /**
     * Our ServletOutputStream
     */
    protected final ServletOutputStream captureServletOutputStream;
    protected ServletOutputStream servletOutputStream;
    protected PrintWriter printWriter;

    /**
     * Initialize our wrapper with the current HttpServletResponse and ServletOutputStream.
     *
     * @param response                   The response to use
     * @param captureServletOutputStream The ServletOutputStream to use
     */
    public ResponseIncludeWrapper(HttpServletResponse response, ServletOutputStream captureServletOutputStream) {
        super(response);
        this.captureServletOutputStream = captureServletOutputStream;
    }


    /**
     * Flush the servletOutputStream or printWriter ( only one will be non-null ) This must be called after a
     * requestDispatcher.include, since we can't assume that the included servlet flushed its stream.
     *
     * @throws IOException an IO error occurred
     */
    public void flushOutputStreamOrWriter() throws IOException {
        if (servletOutputStream != null) {
            servletOutputStream.flush();
        }
        if (printWriter != null) {
            printWriter.flush();
        }
    }


    /**
     * Return a printwriter, throws an exception if an OutputStream already been returned.
     *
     * @return a PrintWriter object
     *
     * @exception java.io.IOException if the outputstream already been called
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        if (servletOutputStream == null) {
            if (printWriter == null) {
                setCharacterEncoding(getCharacterEncoding());
                printWriter =
                        new PrintWriter(new OutputStreamWriter(captureServletOutputStream, getCharacterEncoding()));
            }
            return printWriter;
        }
        throw new IllegalStateException();
    }


    /**
     * Return an OutputStream, throws an exception if a printwriter already been returned.
     *
     * @return an OutputStream object
     *
     * @exception java.io.IOException if the printwriter already been called
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (printWriter == null) {
            if (servletOutputStream == null) {
                servletOutputStream = captureServletOutputStream;
            }
            return servletOutputStream;
        }
        throw new IllegalStateException();
    }


    /**
     * Returns the value of the <code>last-modified</code> header field. The result is the number of milliseconds since
     * January 1, 1970 GMT.
     *
     * @return the date the resource referenced by this <code>ResponseIncludeWrapper</code> was last modified, or -1 if
     *             not known.
     */
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public void addDateHeader(String name, long value) {
        super.addDateHeader(name, value);
        String lname = name.toLowerCase(Locale.ENGLISH);
        if (lname.equals(LAST_MODIFIED)) {
            lastModified = value;
        }
    }

    @Override
    public void addHeader(String name, String value) {
        super.addHeader(name, value);
        String lname = name.toLowerCase(Locale.ENGLISH);
        if (lname.equals(LAST_MODIFIED)) {
            long lastModified = FastHttpDateFormat.parseDate(value);
            if (lastModified != -1) {
                this.lastModified = lastModified;
            }
        }
    }

    @Override
    public void setDateHeader(String name, long value) {
        super.setDateHeader(name, value);
        String lname = name.toLowerCase(Locale.ENGLISH);
        if (lname.equals(LAST_MODIFIED)) {
            lastModified = value;
        }
    }

    @Override
    public void setHeader(String name, String value) {
        super.setHeader(name, value);
        String lname = name.toLowerCase(Locale.ENGLISH);
        if (lname.equals(LAST_MODIFIED)) {
            long lastModified = FastHttpDateFormat.parseDate(value);
            if (lastModified != -1) {
                this.lastModified = lastModified;
            }
        }
    }
}

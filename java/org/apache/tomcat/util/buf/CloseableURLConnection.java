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
package org.apache.tomcat.util.buf;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.apache.tomcat.util.ExceptionUtils;


/**
 * AutoCloseable wrapper for {@link URLConnection} that ensures all resources are released on close.
 * <p>
 * Different URLConnection subclasses require different cleanup:
 * </p>
 * <ul>
 * <li>{@link HttpURLConnection} (including {@code HttpsURLConnection}) requires {@code disconnect()} to release the
 * underlying socket.</li>
 * <li>{@link JarURLConnection} requires the input stream to be closed to release the underlying
 * {@code java.util.jar.JarFile}. When {@code setUseCaches(false)} is set, closing the input stream closes the
 * {@code JarFile}, and calling {@code getInputStream()} again will throw {@link IllegalStateException}.</li>
 * <li>Other URLConnection types only require any obtained streams to be closed.</li>
 * </ul>
 * <p>
 * This wrapper sets {@code setUseCaches(false)} on the wrapped connection, tracks whether {@code getInputStream()} was
 * called, and performs the appropriate cleanup in {@code close()}.
 * </p>
 */
public final class CloseableURLConnection extends URLConnection implements AutoCloseable {


    private final URLConnection connection;
    private InputStream trackedStream;


    /**
     * Creates a new wrapper for the given URL by opening a connection.
     * <p>
     * {@code setUseCaches(false)} is called on the connection immediately.
     * </p>
     *
     * @param url the URL to connect to
     * @throws IOException if an I/O error occurs while opening the connection
     */
    public CloseableURLConnection(URL url) throws IOException {
        this(url.openConnection());
    }


    /**
     * Creates a new wrapper for the given URLConnection.
     * <p>
     * {@code setUseCaches(false)} is called on the connection immediately.
     * </p>
     *
     * @param connection the URLConnection to wrap
     */
    public CloseableURLConnection(URLConnection connection) {
        super(connection.getURL());
        this.connection = connection;
        connection.setUseCaches(false);
    }


    /**
     * Returns the wrapped URLConnection.
     *
     * @return the wrapped URLConnection
     */
    public URLConnection getConnection() {
        return connection;
    }


    @Override
    public java.io.OutputStream getOutputStream() throws IOException {
        return connection.getOutputStream();
    }


    @Override
    public int getConnectTimeout() {
        return connection.getConnectTimeout();
    }


    @Override
    public void setConnectTimeout(int timeout) {
        connection.setConnectTimeout(timeout);
    }


    @Override
    public int getReadTimeout() {
        return connection.getReadTimeout();
    }


    @Override
    public void setReadTimeout(int timeout) {
        connection.setReadTimeout(timeout);
    }


    @Override
    public void connect() throws IOException {
        connection.connect();
    }


    @Override
    public void close() {
        if (trackedStream != null) {
            try {
                trackedStream.close();
            } catch (IOException e) {
                // Ignore
            }
        } else if (connection instanceof JarURLConnection) {
            try (InputStream is = connection.getInputStream()) {
                // Open and immediately close to release the JarFile
            } catch (Exception e) {
                ExceptionUtils.handleThrowable(e);
            }
        }

        if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection) connection).disconnect();
        }
    }


    /**
     * Returns the input stream for this URL connection. The stream is tracked and will be closed automatically when
     * {@link #close()} is called.
     *
     * @return the input stream
     * @throws IOException if an I/O error occurs
     */
    @Override
    public InputStream getInputStream() throws IOException {
        trackedStream = connection.getInputStream();
        return trackedStream;
    }


    @Override
    public String getContentType() {
        return connection.getContentType();
    }


    @Override
    public int getContentLength() {
        return connection.getContentLength();
    }


    @Override
    public long getContentLengthLong() {
        return connection.getContentLengthLong();
    }


    @Override
    public long getLastModified() {
        return connection.getLastModified();
    }


    @Override
    public String getHeaderField(String name) {
        return connection.getHeaderField(name);
    }

}

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
import java.security.Permission;
import java.util.List;
import java.util.Map;

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
     * Returns the wrapped URLConnection. Some subclasses can have additional
     * methods, in which case the wrapped URLConnection needs to be accessed.
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
            } catch (Exception e) {
                ExceptionUtils.handleThrowable(e);
            }
        } else if (connection instanceof JarURLConnection) {
            try (@SuppressWarnings("unused")
                java.util.jar.JarFile jarFile = ((JarURLConnection) connection).getJarFile()) {
                // Explicitly close the JarFile to release its native resources.
                // As setUseCaches(false) is set, this should not cause side effects on other streams.
            } catch (Exception e) {
                ExceptionUtils.handleThrowable(e);
            }
        } else if (!(connection instanceof HttpURLConnection)) {
            // Other cases like FileURLConnection could have used a stream as a side effect,
            // possibly causing file locking.
            try (@SuppressWarnings("unused") InputStream is = connection.getInputStream()) {
                // Explicitly close the InputStream to release its native resources.
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


    @Override
    public URL getURL() {
        return connection.getURL();
    }


    @Override
    public String getContentEncoding() {
        return connection.getContentEncoding();
    }


    @Override
    public long getExpiration() {
        return connection.getExpiration();
    }


    @Override
    public long getDate() {
        return connection.getDate();
    }


    @Override
    public Map<String, List<String>> getHeaderFields() {
        return connection.getHeaderFields();
    }


    @Override
    public int getHeaderFieldInt(String name, int defaultValue) {
        return connection.getHeaderFieldInt(name, defaultValue);
    }


    @Override
    public long getHeaderFieldLong(String name, long defaultValue) {
        return connection.getHeaderFieldLong(name, defaultValue);
    }


    @Override
    public long getHeaderFieldDate(String name, long defaultValue) {
        return connection.getHeaderFieldDate(name, defaultValue);
    }


    @Override
    public String getHeaderFieldKey(int n) {
        return connection.getHeaderFieldKey(n);
    }


    @Override
    public String getHeaderField(int n) {
        return connection.getHeaderField(n);
    }


    @Override
    public Object getContent() throws IOException {
        return connection.getContent();
    }


    @Override
    public Object getContent(Class<?>[] classes) throws IOException {
        return connection.getContent(classes);
    }


    @Override
    @Deprecated
    public Permission getPermission() throws IOException {
        /*
         * This method is deprecated for removal in Java 25. If it isn't overridden the superclass will return {@code
         * java.security.AllPermission} which would be acceptable but, for consistency, it is better to oveerride the
         * method. Calling {@code getPermission()} on the wrapped connection would work until the method is removed.
         * Throwing {@code UnsupportedOperationException} works now since Tomcat never calls the method and will
         * continue to work once the method is removed.
         */
        throw new UnsupportedOperationException();
    }


    @Override
    public String toString() {
        return connection.toString();
    }


    @Override
    public void setDoInput(boolean doinput) {
        connection.setDoInput(doinput);
    }


    @Override
    public boolean getDoInput() {
        return connection.getDoInput();
    }


    @Override
    public void setDoOutput(boolean dooutput) {
        connection.setDoOutput(dooutput);
    }


    @Override
    public boolean getDoOutput() {
        return connection.getDoOutput();
    }


    @Override
    public void setAllowUserInteraction(boolean allowuserinteraction) {
        connection.setAllowUserInteraction(allowuserinteraction);
    }


    @Override
    public boolean getAllowUserInteraction() {
        return connection.getAllowUserInteraction();
    }


    @Override
    public void setUseCaches(boolean usecaches) {
        connection.setUseCaches(usecaches);
    }


    @Override
    public boolean getUseCaches() {
        return connection.getUseCaches();
    }


    @Override
    public void setIfModifiedSince(long ifmodifiedsince) {
        connection.setIfModifiedSince(ifmodifiedsince);
    }


    @Override
    public long getIfModifiedSince() {
        return connection.getIfModifiedSince();
    }


    @Override
    public boolean getDefaultUseCaches() {
        return connection.getDefaultUseCaches();
    }


    @Override
    public void setDefaultUseCaches(boolean defaultusecaches) {
        connection.setDefaultUseCaches(defaultusecaches);
    }


    @Override
    public void setRequestProperty(String key, String value) {
        connection.setRequestProperty(key, value);
    }


    @Override
    public void addRequestProperty(String key, String value) {
        connection.addRequestProperty(key, value);
    }


    @Override
    public String getRequestProperty(String key) {
        return connection.getRequestProperty(key);
    }


    @Override
    public Map<String, List<String>> getRequestProperties() {
        return connection.getRequestProperties();
    }


    @Override
    public int hashCode() {
        return connection.hashCode();
    }


    @Override
    public boolean equals(Object obj) {
        return connection.equals(obj);
    }

}

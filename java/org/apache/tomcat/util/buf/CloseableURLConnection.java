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
 * <li>{@link JarURLConnection} requires its resources to be released. When the stock JDK handler is used with
 * {@code setUseCaches(false)}, {@code getJarFile()} returns a JarFile private to this connection, which is closed
 * directly. Otherwise the {@code JarFile} may be shared - the JDK caches one instance while caches are enabled, and
 * a custom jar handler (such as nested JAR support) may share one regardless of the use of caches - so only the
 * input stream is closed, leaving any shared {@code JarFile} open for its other users.</li>
 * <li>Other URLConnection types only require any obtained streams to be closed.</li>
 * </ul>
 * <p>
 * This wrapper sets {@code setUseCaches(false)} on the wrapped connection, tracks whether {@code getInputStream()} was
 * called, and performs the appropriate cleanup in {@code close()}.
 * </p>
 */
public final class CloseableURLConnection extends URLConnection implements AutoCloseable {

    /*
     * The class name of the stock JDK handler for jar URLs. A connection of exactly this type opened with caches
     * disabled hands out, via getJarFile(), a JarFile that is private to the connection and therefore safe to close.
     * The name is matched rather than the class referenced directly because the handler is in a non-exported package.
     */
    private static final String JDK_JAR_URL_CONNECTION = "sun.net.www.protocol.jar.JarURLConnection";

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
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            }
        } else if (connection instanceof JarURLConnection jarConn) {
            // Most JarFile cannot be closed
            if (isPrivateJdkJarFile(jarConn)) {
                try (@SuppressWarnings("unused")
                    java.util.jar.JarFile jarFile = jarConn.getJarFile()) {
                    // Explicitly close the private JarFile to release its native resources.
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                }
            } else {
                try (@SuppressWarnings("unused") InputStream is = connection.getInputStream()) {
                    // Explicitly close the InputStream to release native resources.
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                }
            }
        } else if (!(connection instanceof HttpURLConnection)) {
            /*
             * sun.net.www.protocol.file.FileURLConnection is known to open an InputStream for files.
             *
             * Other cases could have used a stream as a side effect, possibly causing file locking.
             */
            try (@SuppressWarnings("unused") InputStream is = connection.getInputStream()) {
                // Explicitly close the InputStream to release its native resources.
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            }
        }

        if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection) connection).disconnect();
        }
    }


    /**
     * Determines whether the JarFile returned by the given connection is a private instance owned by this
     * connection and therefore safe to close directly. This is only the case for the stock JDK jar handler opened
     * with caches disabled. For any other connection - a custom handler, which may share the JarFile regardless of
     * the use of caches, or a stock connection with caches enabled, which shares a cached JarFile - the JarFile must
     * be left open and the connection released through its input stream instead.
     *
     * @param connection the jar connection to test
     *
     * @return {@code true} if {@link JarURLConnection#getJarFile()} may be closed directly
     */
    private static boolean isPrivateJdkJarFile(JarURLConnection connection) {
        return !connection.getUseCaches() && JDK_JAR_URL_CONNECTION.equals(connection.getClass().getName());
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
        if (trackedStream == null) {
            trackedStream = connection.getInputStream();
        }
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
         * java.security.AllPermission} which would be acceptable but, for consistency, it is better to override the
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
}

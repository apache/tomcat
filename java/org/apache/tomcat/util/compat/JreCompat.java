/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.compat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.jar.JarFile;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Resource;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;

import org.apache.tomcat.util.res.StringManager;

/**
 * This is the base implementation class for JRE compatibility and provides an
 * implementation based on Java 6. Sub-classes may extend this class and provide
 * alternative implementations for later JRE versions
 */
public class JreCompat {

    private static final int RUNTIME_MAJOR_VERSION = 6;

    private static final JreCompat instance;
    private static StringManager sm =
            StringManager.getManager(JreCompat.class.getPackage().getName());
    private static final boolean jre9Available;
    private static final boolean jre8Available;
    private static final boolean jre7Available;


    static {
        // This is Tomcat 7 with a minimum Java version of Java 6. The latest
        // Java version the optional features require is Java 9.
        // Look for the highest supported JVM first
        if (Jre9Compat.isSupported()) {
            instance = new Jre9Compat();
            jre9Available = true;
            jre8Available = true;
            jre7Available = true;
        } else if (Jre8Compat.isSupported()) {
            instance = new Jre8Compat();
            jre9Available = false;
            jre8Available = true;
            jre7Available = true;
        } else if (Jre7Compat.isSupported()) {
            instance = new Jre7Compat();
            jre9Available = false;
            jre8Available = false;
            jre7Available = true;
        } else {
            instance = new JreCompat();
            jre9Available = false;
            jre8Available = false;
            jre7Available = false;
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    // Java 6 implementation of Java 7 methods

    public static boolean isJre7Available() {
        return jre7Available;
    }


    public Locale forLanguageTag(String languageTag) {
        // Extract the language and country for this entry
        String language = null;
        String country = null;
        String variant = null;
        int dash = languageTag.indexOf('-');
        if (dash < 0) {
            language = languageTag;
            country = "";
            variant = "";
        } else {
            language = languageTag.substring(0, dash);
            country = languageTag.substring(dash + 1);
            int vDash = country.indexOf('-');
            if (vDash > 0) {
                String cTemp = country.substring(0, vDash);
                variant = country.substring(vDash + 1);
                country = cTemp;
            } else {
                variant = "";
            }
        }
        if (!isAlpha(language) || !isAlpha(country) || !isAlpha(variant)) {
            return null;
        }

        return new Locale(language, country, variant);
    }


    private static final boolean isAlpha(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }
        return true;
    }


    @SuppressWarnings("unused")
    public GZIPOutputStream getFlushableGZipOutputStream(OutputStream os) {
        throw new UnsupportedOperationException(
                sm.getString("jreCompat.noFlushableGzipOutputStream"));
    }


    @SuppressWarnings("unused")
    public <T> T getObject(CallableStatement callableStatement, int parameterIndex, Class<T> type)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public <T> T getObject(CallableStatement callableStatement, String parameterName, Class<T> type)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public void setSchema(Connection connection, String schema) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public String getSchema(Connection connection) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public void abort(Connection connection, Executor executor) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public void setNetworkTimeout(Connection connection, Executor executor, int milliseconds)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public int getNetworkTimeout(Connection connection) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public ResultSet getPseudoColumns(DatabaseMetaData databaseMetaData, String catalog,
            String schemaPattern, String tableNamePattern, String columnNamePattern)
                    throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public boolean generatedKeyAlwaysReturned(DatabaseMetaData databaseMetaData) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public <T> T getObject(ResultSet resultSet, int parameterIndex, Class<T> type)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public <T> T getObject(ResultSet resultSet, String parameterName, Class<T> type)
            throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public void closeOnCompletion(Statement statement) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    @SuppressWarnings("unused")
    public boolean isCloseOnCompletion(Statement statement) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }


    public InetAddress getLoopbackAddress() {
        // Javadoc for getByName() states that calling with null will return one
        // of the loopback addresses
        InetAddress result = null;
        try {
            result = InetAddress.getByName(null);
        } catch (UnknownHostException e) {
            // This would be unusual but ignore it in this case.
        }
        if (result == null) {
            // Fallback to default IPv4 loopback address.
            // Not perfect but good enough and if the address is not valid the
            // bind will fail later with an appropriate error message
            try {
                result = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                // Unreachable.
                // For text representations of IP addresses only the format is
                // checked.
            }
        }

        return result;
    }


    // Java 6 implementation of Java 8 methods

    public static boolean isJre8Available() {
        return jre8Available;
    }


    @SuppressWarnings("unused")
    public void setUseServerCipherSuitesOrder(SSLServerSocket socket,
            boolean useCipherSuitesOrder) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noServerCipherSuiteOrder"));
    }


    @SuppressWarnings("unused")
    public void setUseServerCipherSuitesOrder(SSLEngine engine,
            boolean useCipherSuitesOrder) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noServerCipherSuiteOrder"));
    }


    // Java 6 implementation of Java 9 methods

    public static boolean isJre9Available() {
        return jre9Available;
    }


    /**
     * Test if the provided exception is an instance of
     * java.lang.reflect.InaccessibleObjectException.
     *
     * @param t The exception to test
     *
     * @return {@code true} if the exception is an instance of
     *         InaccessibleObjectException, otherwise {@code false}
     */
    public boolean isInstanceOfInaccessibleObjectException(Throwable t) {
        // Exception does not exist prior to Java 9
        return false;
    }


    /**
     * Disables caching for JAR URL connections. For Java 8 and earlier, this also disables
     * caching for ALL URL connections.
     *
     * @throws IOException If a dummy JAR URLConnection can not be created
     */
    public void disableCachingForJarUrlConnections() throws IOException {
        // Doesn't matter that this JAR doesn't exist - just as
        // long as the URL is well-formed
        URL url = new URL("jar:file://dummy.jar!/");
        URLConnection uConn = url.openConnection();
        uConn.setDefaultUseCaches(false);
    }


    /**
     * Obtains the URls for all the JARs on the module path when the JVM starts
     * and adds them to the provided Deque.
     *
     * @param classPathUrlsToProcess    The Deque to which the modules should be
     *                                  added
     */
    public void addBootModulePath(Deque<URL> classPathUrlsToProcess) {
        // NO-OP. There is no module path prior to Java 9.
    }


    /**
     * Creates a new JarFile instance. When running on Java 9 and later, the
     * JarFile will be multi-release JAR aware.
     *
     * @param f The JAR file to open
     *
     * @return A JarFile instance based on the provided file
     *
     * @throws IOException  If an I/O error occurs creating the JarFile instance
     */
    public JarFile jarFileNewInstance(File f) throws IOException {
        return new JarFile(f);
    }


    /**
     * Is this JarFile a multi-release JAR file.
     *
     * @param jarFile   The JarFile to test
     *
     * @return {@code true} If it is a multi-release JAR file and is configured
     *         to behave as such.
     */
    public boolean jarFileIsMultiRelease(JarFile jarFile) {
        // There is no multi-release JAR support prior to Java 9
        return false;
    }


    public int jarFileRuntimeMajorVersion() {
        return RUNTIME_MAJOR_VERSION;
    }


    public boolean isCommonsAnnotations1_1Available() {
        Class<Resource> clazz = Resource.class;
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals("lookup")) {
                return true;
            }
        }
        return false;
    }


    /**
     * Is the accessibleObject accessible (as a result of appropriate module
     * exports) on the provided instance?
     *
     * @param base  The specific instance to be tested.
     * @param accessibleObject  The method/field/constructor to be tested.
     *
     * @return {code true} if the AccessibleObject can be accessed otherwise
     *         {code false}
     */
    public boolean canAcccess(Object base, AccessibleObject accessibleObject) {
        // Java 8 doesn't support modules so default to true
        return true;
    }


    /**
     * Is the given class in an exported package?
     *
     * @param type  The class to test
     *
     * @return Always {@code true} for Java 8. {@code true} if the enclosing
     *         package is exported for Java 9+
     */
    public boolean isExported(Class<?> type) {
        return true;
    }
}

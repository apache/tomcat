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
import java.lang.reflect.AccessibleObject;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore.LoadStoreParameter;
import java.util.Deque;
import java.util.jar.JarFile;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.tomcat.util.res.StringManager;

/**
 * This is the base implementation class for JRE compatibility and provides an
 * implementation based on Java 7. Sub-classes may extend this class and provide
 * alternative implementations for later JRE versions
 */
public class JreCompat {

    private static final int RUNTIME_MAJOR_VERSION = 7;

    private static final JreCompat instance;
    private static StringManager sm =
            StringManager.getManager(JreCompat.class.getPackage().getName());
    private static final boolean jre9Available;
    private static final boolean jre8Available;


    static {
        // This is Tomcat 8 with a minimum Java version of Java 7. The latest
        // Java version the optional features require is Java 9.
        // Look for the highest supported JVM first
        if (Jre9Compat.isSupported()) {
            instance = new Jre9Compat();
            jre9Available = true;
            jre8Available = true;
        }
        else if (Jre8Compat.isSupported()) {
            instance = new Jre8Compat();
            jre9Available = false;
            jre8Available = true;
        } else {
            instance = new JreCompat();
            jre9Available = false;
            jre8Available = false;
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    // Java 7 implementation of Java 8 methods

    public static boolean isJre8Available() {
        return jre8Available;
    }


    @SuppressWarnings("unused")
    public void setUseServerCipherSuitesOrder(SSLParameters engine, boolean useCipherSuitesOrder) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noServerCipherSuiteOrder"));
    }


    @SuppressWarnings("unused")
    public LoadStoreParameter getDomainLoadStoreParameter(URI uri) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noDomainLoadStoreParameter"));
    }


    // Java 7 implementation of Java 9 methods

    public static boolean isAlpnSupported() {
        return isJre9Available() || (isJre8Available() && Jre8Compat.isAlpnSupported());
    }


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
     * Set the application protocols the server will accept for ALPN
     *
     * @param sslParameters    The SSL parameters for a connection
     * @param protocols        The application protocols to be allowed for that
     *                         connection
     */
    public void setApplicationProtocols(SSLParameters sslParameters, String[] protocols) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noApplicationProtocols"));
    }


    /**
     * Get the application protocol that has been negotiated for connection
     * associated with the given SSLEngine.
     *
     * @param sslEngine The SSLEngine for which to obtain the negotiated
     *                  protocol
     *
     * @return The name of the negotiated protocol
     */
    public String getApplicationProtocol(SSLEngine sslEngine) {
        throw new UnsupportedOperationException(sm.getString("jreCompat.noApplicationProtocol"));
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
     * Obtains the URLs for all the JARs on the module path when the JVM starts
     * and adds them to the provided Deque.
     *
     * @param classPathUrlsToProcess    The Deque to which the modules should be
     *                                  added
     */
    public void addBootModulePath(Deque<URL> classPathUrlsToProcess) {
        // NO-OP for Java 7. There is no module path.
    }


    /**
     * Creates a new JarFile instance. When running on Java 9 and later, the
     * JarFile will be multi-release JAR aware. While this isn't strictly
     * required to be in this package, it is provided as a convenience method.
     *
     * @param s The JAR file to open
     *
     * @return A JarFile instance based on the provided path
     *
     * @throws IOException  If an I/O error occurs creating the JarFile instance
     */
    public final JarFile jarFileNewInstance(String s) throws IOException {
        return jarFileNewInstance(new File(s));
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
        // Java 8 doesn't support multi-release so default to false
        return false;
    }


    public int jarFileRuntimeMajorVersion() {
        return RUNTIME_MAJOR_VERSION;
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

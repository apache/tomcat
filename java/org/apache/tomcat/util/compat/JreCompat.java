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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.tomcat.util.res.StringManager;

/**
 * This is the base implementation class for JRE compatibility and provides an
 * implementation based on Java 8. Sub-classes may extend this class and provide
 * alternative implementations for later JRE versions
 */
public class JreCompat {

    private static final JreCompat instance;
    private static final boolean jre9Available;
    private static final StringManager sm = StringManager.getManager(JreCompat.class);

    static {
        // This is Tomcat 9 with a minimum Java version of Java 8.
        // Look for the highest supported JVM first
        if (Jre9Compat.isSupported()) {
            instance = new Jre9Compat();
            jre9Available = true;
        } else {
            instance = new JreCompat();
            jre9Available = false;
        }
    }


    public static JreCompat getInstance() {
        return instance;
    }


    public static boolean isJre9Available() {
        return jre9Available;
    }


    // Java 8 implementation of Java 9 methods

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
     * @param sslParameters The SSL parameters for a connection
     * @param protocols     The application protocols to be allowed for that
     *                      connection
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
}

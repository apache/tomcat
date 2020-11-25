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

package org.apache.tomcat.util.net;

import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSession;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provides a factory and base implementation for the Tomcat specific mechanism
 * that allows alternative SSL/TLS implementations to be used without requiring
 * the implementation of a full JSSE provider.
 */
public abstract class SSLImplementation {

    private static final Log logger = LogFactory.getLog(SSLImplementation.class);
    private static final StringManager sm = StringManager.getManager(SSLImplementation.class);

    /**
     * Obtain an instance (not a singleton) of the implementation with the given
     * class name.
     *
     * @param className The class name of the required implementation or null to
     *                  use the default (currently {@link JSSEImplementation}.
     *
     * @return An instance of the required implementation
     *
     * @throws ClassNotFoundException If an instance of the requested class
     *         cannot be created
     */
    public static SSLImplementation getInstance(String className)
            throws ClassNotFoundException {
        if (className == null)
            return new JSSEImplementation();

        try {
            Class<?> clazz = Class.forName(className);
            return (SSLImplementation) clazz.getConstructor().newInstance();
        } catch (Exception e) {
            String msg = sm.getString("sslImplementation.cnfe", className);
            if (logger.isDebugEnabled()) {
                logger.debug(msg, e);
            }
            throw new ClassNotFoundException(msg, e);
        }
    }

    /**
     * Obtain an instance of SSLSupport.
     *
     * @param session   The SSL session
     * @param additionalAttributes  Additional SSL attributes that are not
     *                              available from the session.
     *
     * @return An instance of SSLSupport based on the given session and the
     *         provided additional attributes
     */
    public SSLSupport getSSLSupport(SSLSession session, Map<String,List<String>> additionalAttributes) {
        return getSSLSupport(session);
    }

    /**
     * Obtain an instance of SSLSupport.
     *
     * @param session   The TLS session
     *
     * @return An instance of SSLSupport based on the given session.
     *
     * @deprecated This will be removed in Tomcat 10.1.x onwards.
     *             Use {@link #getSSLSupport(SSLSession, Map)}.
     */
    @Deprecated
    public abstract SSLSupport getSSLSupport(SSLSession session);

    public abstract SSLUtil getSSLUtil(SSLHostConfigCertificate certificate);

    public abstract boolean isAlpnSupported();
}

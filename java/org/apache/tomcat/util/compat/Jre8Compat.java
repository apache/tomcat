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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.KeyStore.LoadStoreParameter;
import java.util.Collections;
import java.util.Map;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

class Jre8Compat extends JreCompat {

    private static final Log log = LogFactory.getLog(Jre8Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre8Compat.class);

    private static final int RUNTIME_MAJOR_VERSION = 8;

    private static final Method setUseCipherSuitesOrderMethod;
    private static final Constructor<?> domainLoadStoreParameterConstructor;

    protected static final Method setApplicationProtocolsMethod;
    protected static final Method getApplicationProtocolMethod;


    static {
        Method m1 = null;
        Constructor<?> c2 = null;
        try {
            // Order is important for the error handling below.
            // Must look up m1 first.

            // The class is Java6+...
            Class<?> clazz1 = Class.forName("javax.net.ssl.SSLParameters");
            // ...but this method is Java8+
            m1 = clazz1.getMethod("setUseCipherSuitesOrder", boolean.class);
            Class<?> clazz2 = Class.forName("java.security.DomainLoadStoreParameter");
            c2 = clazz2.getConstructor(URI.class, Map.class);
        } catch (SecurityException e) {
            // Should never happen
            log.error(sm.getString("jre8Compat.unexpected"), e);
        } catch (NoSuchMethodException e) {
            if (m1 == null) {
                // Must be pre-Java 8
                log.debug(sm.getString("jre8Compat.javaPre8"), e);
            } else {
                // Should never happen - signature error in lookup?
                log.error(sm.getString("jre8Compat.unexpected"), e);
            }
        } catch (ClassNotFoundException e) {
            // Should never happen
            log.error(sm.getString("jre8Compat.unexpected"), e);
        }
        setUseCipherSuitesOrderMethod = m1;
        domainLoadStoreParameterConstructor = c2;
        Method m2 = null;
        Method m3 = null;
        try {
            m2 = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
            m3 = SSLEngine.class.getMethod("getApplicationProtocol");
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            // Only the newest Java 8 have the ALPN API, so ignore
        }
        setApplicationProtocolsMethod = m2;
        getApplicationProtocolMethod = m3;
    }


    static boolean isSupported() {
        return setUseCipherSuitesOrderMethod != null;
    }


    @Override
    public void setUseServerCipherSuitesOrder(SSLParameters sslParameters,
            boolean useCipherSuitesOrder) {
        try {
            setUseCipherSuitesOrderMethod.invoke(sslParameters,
                    Boolean.valueOf(useCipherSuitesOrder));
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException(e);
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(e);
        } catch (InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public LoadStoreParameter getDomainLoadStoreParameter(URI uri) {
        try {
            return (LoadStoreParameter) domainLoadStoreParameterConstructor.newInstance(
                    uri, Collections.EMPTY_MAP);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public int jarFileRuntimeMajorVersion() {
        return RUNTIME_MAJOR_VERSION;
    }

    @Override
    public void setApplicationProtocols(SSLParameters sslParameters, String[] protocols) {
        if (setApplicationProtocolsMethod != null) {
            try {
                setApplicationProtocolsMethod.invoke(sslParameters, (Object) protocols);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new UnsupportedOperationException(e);
            }
        } else {
            super.setApplicationProtocols(sslParameters, protocols);
        }
    }


    @Override
    public String getApplicationProtocol(SSLEngine sslEngine) {
        if (getApplicationProtocolMethod != null) {
            try {
                return (String) getApplicationProtocolMethod.invoke(sslEngine);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new UnsupportedOperationException(e);
            }
        } else {
            return super.getApplicationProtocol(sslEngine);
        }
    }


    public static boolean isAlpnSupported() {
        return setApplicationProtocolsMethod != null && getApplicationProtocolMethod != null;
    }


}

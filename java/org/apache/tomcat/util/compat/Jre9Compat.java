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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLConnection;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

class Jre9Compat extends JreCompat {

    private static final Class<?> inaccessibleObjectExceptionClazz;
    private static final Method setApplicationProtocolsMethod;
    private static final Method getApplicationProtocolMethod;
    private static final Method setDefaultUseCaches;

    static {
        Class<?> c1 = null;
        Method m2 = null;
        Method m3 = null;
        Method m4 = null;

        try {
            c1 = Class.forName("java.lang.reflect.InaccessibleObjectException");
            m2 = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
            m3 = SSLEngine.class.getMethod("getApplicationProtocol");
            m4 = URLConnection.class.getMethod("setDefaultUseCaches", String.class, boolean.class);
        } catch (SecurityException | NoSuchMethodException e) {
            // Should never happen
        } catch (ClassNotFoundException e) {
            // Must be Java 8
        }
        inaccessibleObjectExceptionClazz = c1;
        setApplicationProtocolsMethod = m2;
        getApplicationProtocolMethod = m3;
        setDefaultUseCaches = m4;
    }


    static boolean isSupported() {
        return inaccessibleObjectExceptionClazz != null;
    }


    @Override
    public boolean isInstanceOfInaccessibleObjectException(Throwable t) {
        if (t == null) {
            return false;
        }

        return inaccessibleObjectExceptionClazz.isAssignableFrom(t.getClass());
    }


    @Override
    public void setApplicationProtocols(SSLParameters sslParameters, String[] protocols) {
        try {
            setApplicationProtocolsMethod.invoke(sslParameters, (Object) protocols);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public String getApplicationProtocol(SSLEngine sslEngine) {
        try {
            return (String) getApplicationProtocolMethod.invoke(sslEngine);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public void disableCachingForJarUrlConnections() throws IOException {
        try {
            setDefaultUseCaches.invoke(null, "JAR", Boolean.FALSE);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}

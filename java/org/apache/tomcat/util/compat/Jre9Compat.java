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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Deque;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

class Jre9Compat extends JreCompat {

    private static final Log log = LogFactory.getLog(Jre9Compat.class);
    private static final StringManager sm = StringManager.getManager(Jre9Compat.class);

    private static final Class<?> inaccessibleObjectExceptionClazz;
    private static final Method setApplicationProtocolsMethod;
    private static final Method getApplicationProtocolMethod;
    private static final Method setDefaultUseCachesMethod;
    private static final Method bootMethod;
    private static final Method configurationMethod;
    private static final Method modulesMethod;
    private static final Method referenceMethod;
    private static final Method locationMethod;
    private static final Method isPresentMethod;
    private static final Method getMethod;

    static {
        Class<?> moduleLayerClazz = null;
        Class<?> configurationClazz = null;
        Class<?> resolvedModuleClazz = null;
        Class<?> moduleReferenceClazz = null;
        Class<?> optionalClazz = null;

        Class<?> c1 = null;
        Method m2 = null;
        Method m3 = null;
        Method m4 = null;
        Method m5 = null;
        Method m6 = null;
        Method m7 = null;
        Method m8 = null;
        Method m9 = null;
        Method m10 = null;
        Method m11 = null;

        try {
            moduleLayerClazz = Class.forName("java.lang.ModuleLayer");
            configurationClazz = Class.forName("java.lang.module.Configuration");
            resolvedModuleClazz = Class.forName("java.lang.module.ResolvedModule");
            moduleReferenceClazz = Class.forName("java.lang.module.ModuleReference");
            optionalClazz = Class.forName("java.util.Optional");

            c1 = Class.forName("java.lang.reflect.InaccessibleObjectException");
            m2 = SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
            m3 = SSLEngine.class.getMethod("getApplicationProtocol");
            m4 = URLConnection.class.getMethod("setDefaultUseCaches", String.class, boolean.class);
            m5 = moduleLayerClazz.getMethod("boot");
            m6 = moduleLayerClazz.getMethod("configuration");
            m7 = configurationClazz.getMethod("modules");
            m8 = resolvedModuleClazz.getMethod("reference");
            m9 = moduleReferenceClazz.getMethod("location");
            m10 = optionalClazz.getMethod("isPresent");
            m11 = optionalClazz.getMethod("get");
        } catch (SecurityException | NoSuchMethodException e) {
            // Should never happen
        } catch (ClassNotFoundException e) {
            // Must be Java 8
        }
        inaccessibleObjectExceptionClazz = c1;
        setApplicationProtocolsMethod = m2;
        getApplicationProtocolMethod = m3;
        setDefaultUseCachesMethod = m4;
        bootMethod = m5;
        configurationMethod = m6;
        modulesMethod = m7;
        referenceMethod = m8;
        locationMethod = m9;
        isPresentMethod = m10;
        getMethod = m11;
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
            setDefaultUseCachesMethod.invoke(null, "JAR", Boolean.FALSE);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }


    @Override
    public void addBootModulePath(Deque<URL> classPathUrlsToProcess) {
        try {
            Object bootLayer = bootMethod.invoke(null);
            Object bootConfiguration = configurationMethod.invoke(bootLayer);
            Set<?> resolvedModules = (Set<?>) modulesMethod.invoke(bootConfiguration);
            for (Object resolvedModule : resolvedModules) {
                Object moduleReference = referenceMethod.invoke(resolvedModule);
                Object optionalURI = locationMethod.invoke(moduleReference);
                Boolean isPresent = (Boolean) isPresentMethod.invoke(optionalURI);
                if (isPresent.booleanValue()) {
                    URI uri = (URI) getMethod.invoke(optionalURI);
                    try {
                        URL url = uri.toURL();
                        classPathUrlsToProcess.add(url);
                    } catch (MalformedURLException e) {
                        log.warn(sm.getString("jre9Compat.invalidModuleUri", uri), e);
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}

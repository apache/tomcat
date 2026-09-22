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
package org.apache.catalina.tribes.group;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

import javax.net.ssl.SSLEngine;

/**
 * The pair (client and server) of TLS contexts used by a Tribes channel.
 */
public class TribesSslContext implements AutoCloseable {

    private static final String FFM_CONTEXT = "org.apache.tomcat.util.net.openssl.panama.OpenSSLContext";
    private static final String NATIVE_CONTEXT = "org.apache.tomcat.util.net.openssl.OpenSSLContext";

    private final Object clientContext;
    private final Object serverContext;
    private final boolean ffm;

    public TribesSslContext(String identity, String key) throws Exception {
        Object[] contexts = createFfmContexts(identity, key);
        if (contexts != null) {
            ffm = true;
        } else {
            contexts = createNativeContexts(identity, key);
            ffm = false;
        }
        if (contexts == null) {
            throw new IllegalStateException("Neither OpenSSL FFM nor Tomcat Native is available");
        }
        clientContext = contexts[0];
        serverContext = contexts[1];
    }

    public SSLEngine createClientEngine() {
        return createEngine(clientContext, true);
    }

    public SSLEngine createServerEngine() {
        return createEngine(serverContext, false);
    }

    private static Object[] createFfmContexts(String identity, String key) throws Exception {
        /*
         * Tribes may be used stand-alone so don't assume there is an OpenSSLLifecycleListener that Tomcat is already
         * using but do use the library in a manner that is compatible if Tomcat is using such an instance.
         */
        Class<?> library;
        try {
            library = Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary");
            library.getMethod("init").invoke(null);
        } catch (Throwable t) {
            return null;
        }
        Class<?> status = Class.forName("org.apache.tomcat.util.net.openssl.OpenSSLStatus");
        if (!((Boolean) status.getMethod("isAvailable").invoke(null)).booleanValue()) {
            library.getMethod("destroy").invoke(null);
            return null;
        }
        return createContexts(FFM_CONTEXT, identity, key);
    }

    private static Object[] createNativeContexts(String identity, String key) throws Exception {
        /*
         * Tribes may be used stand-alone so don't assume there is an AprLifecycleListener that Tomcat is already using
         * but do use the library in a manner that is compatible if Tomcat is using such an instance.
         */
        Class<?> listener = Class.forName("org.apache.catalina.core.AprLifecycleListener");
        listener.getConstructor().newInstance();
        if (!((Boolean) listener.getMethod("isAprAvailable").invoke(null)).booleanValue()) {
            return null;
        }
        return createContexts(NATIVE_CONTEXT, identity, key);
    }

    private static Object[] createContexts(String className, String identity, String key) throws Exception {
        Class<?> clazz = Class.forName(className);
        Class<?> certificateClass = Class.forName("org.apache.tomcat.util.net.SSLHostConfigCertificate");
        Constructor<?> constructor = clazz.getConstructor(certificateClass, java.util.List.class, boolean.class);
        Object client = constructor.newInstance(createCertificate(identity, key), null, Boolean.TRUE);
        Object server = constructor.newInstance(createCertificate(identity, key), null, Boolean.FALSE);
        Method init = clazz.getMethod("init", javax.net.ssl.KeyManager[].class, javax.net.ssl.TrustManager[].class,
                java.security.SecureRandom.class);
        init.invoke(client, null, null, null);
        init.invoke(server, null, null, null);
        return new Object[] { client, server };
    }

    private static Object createCertificate(String identity, String key) throws Exception {
        Class<?> configClass = Class.forName("org.apache.tomcat.util.net.SSLHostConfig");
        Object config = configClass.getConstructor().newInstance();
        configClass.getMethod("setProtocols", String.class).invoke(config, "TLSv1.2");
        configClass.getMethod("setEnabledProtocols", String[].class).invoke(config,
                (Object) new String[] { "TLSv1.2" });
        configClass.getMethod("setCiphers", String.class).invoke(config, "PSK-AES128-GCM-SHA256");
        Class<?> pskClass = Class.forName("org.apache.tomcat.util.net.SSLHostConfigPreSharedKey");
        Object psk = pskClass.getConstructor(configClass).newInstance(config);
        pskClass.getMethod("setIdentity", String.class).invoke(psk, identity);
        pskClass.getMethod("setKey", String.class).invoke(psk, key);
        configClass.getMethod("addPreSharedKey", pskClass).invoke(config, psk);
        Set<?> certificates =
                (Set<?>) configClass.getMethod("getCertificates", boolean.class).invoke(config, Boolean.TRUE);
        return certificates.iterator().next();
    }

    private static SSLEngine createEngine(Object context, boolean clientMode) {
        try {
            return (SSLEngine) context.getClass().getMethod("createSSLEngine", boolean.class).invoke(context,
                    Boolean.valueOf(clientMode));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        try {
            clientContext.getClass().getMethod("destroy").invoke(clientContext);
        } catch (ReflectiveOperationException e) {
            // The contexts may already have been destroyed during channel shutdown.
        }
        try {
            serverContext.getClass().getMethod("destroy").invoke(serverContext);
        } catch (ReflectiveOperationException e) {
            // The contexts may already have been destroyed during channel shutdown.
        }
        if (ffm) {
            try {
                Class.forName("org.apache.tomcat.util.net.openssl.panama.OpenSSLLibrary").getMethod("destroy")
                        .invoke(null);
            } catch (ReflectiveOperationException e) {
                // The contexts have already released their native resources.
            }
        }
    }
}

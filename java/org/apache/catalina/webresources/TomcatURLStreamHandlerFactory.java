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
package org.apache.catalina.webresources;

import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TomcatURLStreamHandlerFactory implements URLStreamHandlerFactory {

    private static final String WAR_PROTOCOL = "war";
    private static final String CLASSPTH_PROTOCOL = "classpath";

    // Singleton instance
    private static volatile TomcatURLStreamHandlerFactory instance = null;

    /**
     * Obtain a reference to the singleton instance. It is recommended that
     * callers check the value of {@link #isRegistered()} before using the
     * returned instance.
     *
     * @return A reference to the singleton instance
     */
    public static TomcatURLStreamHandlerFactory getInstance() {
        getInstanceInternal(true);
        return instance;
    }


    private static TomcatURLStreamHandlerFactory getInstanceInternal(boolean register) {
        // Double checked locking. OK because instance is volatile.
        if (instance == null) {
            synchronized (TomcatURLStreamHandlerFactory.class) {
                if (instance == null) {
                    instance = new TomcatURLStreamHandlerFactory(register);
                }
            }
        }
        return instance;
    }


    private final boolean registered;

    // List of factories for application defined stream handler factories.
    private final List<URLStreamHandlerFactory> userFactories =
            new CopyOnWriteArrayList<>();

    /**
     * Register this factory with the JVM. May be called more than once. The
     * implementation ensures that registration only occurs once.
     *
     * @return <code>true</code> if the factory is already registered with the
     *         JVM or was successfully registered as a result of this call.
     *         <code>false</code> if the factory was disabled prior to this
     *         call.
     */
    public static boolean register() {
        return getInstanceInternal(true).isRegistered();
    }


    /**
     * Prevent this this factory from registering with the JVM. May be called
     * more than once.
     *
     * @return <code>true</code> if the factory is already disabled or was
     *         successfully disabled as a result of this call.
     *         <code>false</code> if the factory was already registered prior
     *         to this call.

     */
    public static boolean disable() {
        return !getInstanceInternal(false).isRegistered();
    }


    /**
     * Release references to any user provided factories that have been loaded
     * using the provided class loader. Called during web application stop to
     * prevent memory leaks.
     *
     * @param classLoader The class loader to release
     */
    public static void release(ClassLoader classLoader) {
        Iterator<URLStreamHandlerFactory> iter = instance.userFactories.iterator();
        while (iter.hasNext()) {
            ClassLoader factoryLoader = iter.next().getClass().getClassLoader();
            while (factoryLoader != null) {
                if (classLoader.equals(factoryLoader)) {
                    iter.remove();
                    break;
                }
                factoryLoader = factoryLoader.getParent();
            }
        }
    }


    private TomcatURLStreamHandlerFactory(boolean register) {
        // Hide default constructor
        // Singleton pattern to ensure there is only one instance of this
        // factory
        this.registered = register;
        if (register) {
            URL.setURLStreamHandlerFactory(this);
        }
    }


    public boolean isRegistered() {
        return registered;
    }


    /**
     * Since the JVM only allows a single call to
     * {@link URL#setURLStreamHandlerFactory(URLStreamHandlerFactory)} and
     * Tomcat needs to register a handler, provide a mechanism to allow
     * applications to register their own handlers.
     *
     * @param factory The user provided factory to add to the factories Tomcat
     *                has alredy registered
     */
    public void addUserFactory(URLStreamHandlerFactory factory) {
        userFactories.add(factory);
    }


    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {

        // Tomcat's handler always takes priority so applications can't override
        // it.
        if (WAR_PROTOCOL.equals(protocol)) {
            return new WarURLStreamHandler();
        } else if (CLASSPTH_PROTOCOL.equals(protocol)) {
            return new ClasspathURLStreamHandler();
        }

        // Application handlers
        for (URLStreamHandlerFactory factory : userFactories) {
            URLStreamHandler handler =
                factory.createURLStreamHandler(protocol);
            if (handler != null) {
                return handler;
            }
        }

        // Unknown protocol
        return null;
    }
}

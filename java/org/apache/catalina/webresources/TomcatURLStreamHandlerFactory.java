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

// TODO Add hook to enable user registered factories to be unloaded on web
// application stop.
public class TomcatURLStreamHandlerFactory implements URLStreamHandlerFactory{

    private static final String WAR_PROTOCOL = "war";

    // Singleton instance
    private static TomcatURLStreamHandlerFactory instance =
            new TomcatURLStreamHandlerFactory();

    /**
     * Obtain a reference to the singleton instance,
     */
    public static TomcatURLStreamHandlerFactory getInstance() {
        return instance;
    }


    // List of factories for application defined stream handler factories.
    private List<URLStreamHandlerFactory> userFactories =
            new CopyOnWriteArrayList<>();


    /**
     * Register this factory with the JVM. May be called more than once. The
     * implementation ensures that registration only occurs once.
     */
    public static void register() {
        // Calling this method loads this class which in turn triggers all the
        // necessary registration.
    }


    /**
     * Since the JVM only allows a single call to
     * {@link URL#setURLStreamHandlerFactory(URLStreamHandlerFactory)} and
     * Tomcat needs to register a handler, provide a mechanism to allow
     * applications to register their own handlers.
     */
    public static void addUserFactory(URLStreamHandlerFactory factory) {
        instance.userFactories.add(factory);
    }


    /**
     * Release references to any user provided factories that have been loaded
     * using the provided class loader. Called during web application stop to
     * prevent memory leaks.
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


    private TomcatURLStreamHandlerFactory() {
        // Hide default constructor
        // Singleton pattern to ensure there is only one instance of this
        // factory
        URL.setURLStreamHandlerFactory(this);
    }


    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {

        // Tomcat's handler always takes priority so applications can't override
        // it.
        if (WAR_PROTOCOL.equals(protocol)) {
            return new WarURLStreamHandler();
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

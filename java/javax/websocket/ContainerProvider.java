/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.websocket;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Provides access to the implementation. This version of the API is hard-coded
 * to use the Apache Tomcat WebSocket implementation.
 */
public class ContainerProvider {

    // Needs to be a WeakHashMap to prevent memory leaks when a context is
    // stopped
    private static Map<ClassLoader,WebSocketContainer> classLoaderContainerMap =
            new WeakHashMap<>();
    private static Object classLoaderContainerMapLock = new Object();

    private static final String DEFAULT_PROVIDER_CLASS_NAME =
            "org.apache.tomcat.websocket.WsWebSocketContainer";

    private static final Class<WebSocketContainer> clazz;

    static {
        try {
            clazz = (Class<WebSocketContainer>) Class.forName(
                    DEFAULT_PROVIDER_CLASS_NAME);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Obtain a reference to the per class loader ClientContainer used to create
     * outgoing WebSocket connections.
     */
    public static WebSocketContainer getClientContainer() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        WebSocketContainer result = null;
        synchronized (classLoaderContainerMapLock) {
            result = classLoaderContainerMap.get(tccl);
            if (result == null) {
                try {
                    result = clazz.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new IllegalArgumentException(e);
                }
                classLoaderContainerMap.put(tccl, result);
            }
        }
        return result;
    }
}

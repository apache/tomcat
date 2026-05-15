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
package org.apache.tomcat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for binding {@link InstanceManager} instances to class loaders.
 */
public final class InstanceManagerBindings {

    private static final Map<ClassLoader,InstanceManager> bindings = new ConcurrentHashMap<>();

    /**
     * Constructs a new InstanceManagerBindings. This constructor is private to prevent instantiation.
     */
    private InstanceManagerBindings() {
        // Hide constructor
    }

    /**
     * Bind an InstanceManager to the given class loader.
     *
     * @param classLoader the class loader
     * @param instanceManager the instance manager to bind
     */
    public static void bind(ClassLoader classLoader, InstanceManager instanceManager) {
        bindings.put(classLoader, instanceManager);
    }

    /**
     * Unbind the InstanceManager associated with the given class loader.
     *
     * @param classLoader the class loader
     */
    public static void unbind(ClassLoader classLoader) {
        bindings.remove(classLoader);
    }

    /**
     * Get the InstanceManager bound to the given class loader.
     *
     * @param classLoader the class loader
     * @return the bound InstanceManager, or {@code null} if none
     */
    public static InstanceManager get(ClassLoader classLoader) {
        return bindings.get(classLoader);
    }
}

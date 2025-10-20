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
package org.apache.tomcat.websocket;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.websocket.Extension;

public class TransformationFactory {

    private static final TransformationFactory factory = new TransformationFactory();

    private Map<String,TransformationBuilder> builders = new HashMap<>();


    private TransformationFactory() {
        // Hide default constructor

        // Configure the built-in transformations
        builders.put(PerMessageDeflate.NAME, PerMessageDeflate.BUILDER);
    }


    public static TransformationFactory getInstance() {
        return factory;
    }


    public Transformation create(String name, List<List<Extension.Parameter>> preferences, boolean isServer) {
        TransformationBuilder builder = builders.get(name);
        if (builder != null) {
            return builder.build(preferences, isServer);
        }
        return null;
    }


    public void registerExtension(String name, TransformationBuilder builder) {
        builders.put(name, builder);
    }


    public Set<String> getInstalledExtensionNames() {
        return new HashSet<>(builders.keySet());
    }


    public Set<Extension> getInstalledExtensions() {
        Set<Extension> result = new HashSet<>();
        for (String extensionName : builders.keySet()) {
            result.add(new WsExtension(extensionName));
        }
        return Collections.unmodifiableSet(result);
    }
}

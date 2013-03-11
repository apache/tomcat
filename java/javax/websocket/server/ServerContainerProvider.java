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
package javax.websocket.server;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Provides a standard mechanism for obtaining a reference to the
 * {@link ServerContainer}.
 */
public abstract class ServerContainerProvider {

    private static final String DEFAULT_PROVIDER_CLASS_NAME =
            "org.apache.tomcat.websocket.ServerContainerImpl";

    /**
     * Use the service loader API to obtain a reference to the
     * {@link ServerContainer}.
     */
    public static ServerContainer getServerContainer() {
        ServerContainer result = null;

        ServiceLoader<ServerContainerProvider> serviceLoader =
                ServiceLoader.load(ServerContainerProvider.class);
        Iterator<ServerContainerProvider> iter = serviceLoader.iterator();
        while (result == null && iter.hasNext()) {
            result = iter.next().getContainer();
        }

        // Fall-back. Also used by unit tests
        if (result == null) {
            try {
                Class<ServerContainer> clazz =
                        (Class<ServerContainer>) Class.forName(
                                DEFAULT_PROVIDER_CLASS_NAME);
                result = clazz.newInstance();
            } catch (ClassNotFoundException | InstantiationException |
                    IllegalAccessException e) {
                // No options left. Just return null.
            }
        }
        return result;

    }

    protected abstract ServerContainer getContainer();
}

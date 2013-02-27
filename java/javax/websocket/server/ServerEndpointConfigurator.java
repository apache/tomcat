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

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;

public abstract class ServerEndpointConfigurator {

    private static volatile ServerEndpointConfigurator defaultImpl = null;
    private static final Object defaultImplLock = new Object();

    private static final String DEFAULT_IMPL_CLASSNAME =
            "org.apache.tomcat.websocket.server.DefaultServerEndpointConfigurator";

    static ServerEndpointConfigurator getDefault() {
        if (defaultImpl == null) {
            synchronized (defaultImplLock) {
                if (defaultImpl == null) {
                    defaultImpl = loadDefault();
                }
            }
        }
        return defaultImpl;
    }


    private static ServerEndpointConfigurator loadDefault() {
        ServerEndpointConfigurator result = null;

        ServiceLoader<ServerEndpointConfigurator> serviceLoader =
                ServiceLoader.load(ServerEndpointConfigurator.class);

        Iterator<ServerEndpointConfigurator> iter = serviceLoader.iterator();
        while (result == null && iter.hasNext()) {
            result = iter.next();
        }

        // Fall-back. Also used by unit tests
        if (result == null) {
            try {
                Class<ServerEndpointConfigurator> clazz =
                        (Class<ServerEndpointConfigurator>) Class.forName(
                                DEFAULT_IMPL_CLASSNAME);
                result = clazz.newInstance();
            } catch (ClassNotFoundException | InstantiationException |
                    IllegalAccessException e) {
                // No options left. Just return null.
            }
        }
        return result;
    }

    public String getNegotiatedSubprotocol(List<String> supported,
            List<String> requested) {
        return getDefault().getNegotiatedSubprotocol(supported, requested);
    }


    public List<Extension> getNegotiatedExtensions(List<Extension> installed,
            List<Extension> requested) {
        return getDefault().getNegotiatedExtensions(installed, requested);
    }


    public boolean checkOrigin(String originHeaderValue) {
        return getDefault().checkOrigin(originHeaderValue);
    }


    public boolean matchesURI(String path, URI requestUri,
            Map<String, String> templateExpansion) {
        return getDefault().matchesURI(path, requestUri, templateExpansion);
    }


    public void modifyHandshake(ServerEndpointConfiguration sec,
            HandshakeRequest request, HandshakeResponse response) {
        getDefault().modifyHandshake(sec, request, response);
    }
}

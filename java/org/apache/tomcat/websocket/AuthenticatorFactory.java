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
package org.apache.tomcat.websocket;

import java.util.ServiceLoader;

/**
 * Utility method to return the appropriate authenticator according to
 * the scheme that the server uses.
 */
public class AuthenticatorFactory {

    /**
     * Return a new authenticator instance.
     * @param authScheme The scheme used
     * @return the authenticator
     */
    public static Authenticator getAuthenticator(String authScheme) {

        Authenticator auth = null;
        switch (authScheme.toLowerCase()) {

        case BasicAuthenticator.schemeName:
            auth = new BasicAuthenticator();
            break;

        case DigestAuthenticator.schemeName:
            auth = new DigestAuthenticator();
            break;

        default:
            auth = loadAuthenticators(authScheme);
            break;
        }

        return auth;

    }

    private static Authenticator loadAuthenticators(String authScheme) {
        ServiceLoader<Authenticator> serviceLoader = ServiceLoader.load(Authenticator.class);

        for (Authenticator auth : serviceLoader) {
            if (auth.getSchemeName().equalsIgnoreCase(authScheme)) {
                return auth;
            }
        }

        return null;
    }

}

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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for the authentication methods used by the websocket client.
 */
public abstract class Authenticator {
    private static final Pattern pattern = Pattern
            .compile("(\\w+)\\s*=\\s*(\"([^\"]+)\"|([^,=\"]+))\\s*,?");

    /**
     * Generate the authentication header that will be sent to the server.
     * @param requestUri The request URI
     * @param WWWAuthenticate The server auth challenge
     * @param UserProperties The user information
     * @return The auth header
     * @throws AuthenticationException When an error occurs
     */
    public abstract String getAuthorization(String requestUri, String WWWAuthenticate,
            Map<String, Object> UserProperties) throws AuthenticationException;

    /**
     * Get the authentication method.
     * @return the auth scheme
     */
    public abstract String getSchemeName();

    /**
     * Utility method to parse the authentication header.
     * @param WWWAuthenticate The server auth challenge
     * @return the parsed header
     */
    public Map<String, String> parseWWWAuthenticateHeader(String WWWAuthenticate) {

        Matcher m = pattern.matcher(WWWAuthenticate);
        Map<String, String> challenge = new HashMap<>();

        while (m.find()) {
            String key = m.group(1);
            String qtedValue = m.group(3);
            String value = m.group(4);

            challenge.put(key, qtedValue != null ? qtedValue : value);

        }

        return challenge;

    }

}

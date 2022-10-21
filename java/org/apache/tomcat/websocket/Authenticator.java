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

import org.apache.tomcat.util.res.StringManager;

/**
 * Base class for the authentication methods used by the WebSocket client.
 */
public abstract class Authenticator {

    private static final StringManager sm = StringManager.getManager(Authenticator.class);

    private static final Pattern pattern = Pattern.compile("(\\w+)\\s*=\\s*(\"([^\"]+)\"|([^,=\"]+))\\s*,?");


    /**
     * Generate the authorization header value that will be sent to the server.
     *
     * @param requestUri            The request URI
     * @param authenticateHeader    The server authentication header received
     * @param userName              The user name
     * @param userPassword          The user password
     * @param userRealm             The realm for which the provided user name
     *                                  and password are valid. {@code null} to
     *                                  indicate all realms.
     *
     * @return The generated authorization header value
     *
     * @throws AuthenticationException When an error occurs
     */
    public abstract String getAuthorization(String requestUri, String authenticateHeader, String userName,
            String userPassword, String userRealm) throws AuthenticationException;


    /**
     * Get the authentication method.
     *
     * @return the authentication scheme
     */
    public abstract String getSchemeName();


    /**
     * Utility method to parse the authentication header.
     *
     * @param authenticateHeader The server authenticate header received
     *
     * @return a map of authentication parameter names and values
     */
    public Map<String, String> parseAuthenticateHeader(String authenticateHeader) {

        Matcher m = pattern.matcher(authenticateHeader);
        Map<String, String> parameterMap = new HashMap<>();

        while (m.find()) {
            String key = m.group(1);
            String qtedValue = m.group(3);
            String value = m.group(4);

            parameterMap.put(key, qtedValue != null ? qtedValue : value);

        }

        return parameterMap;
    }


    protected void validateUsername(String userName) throws AuthenticationException {
        if (userName == null) {
            throw new AuthenticationException(sm.getString("authenticator.nullUserName"));
        }
    }


    protected void validatePassword(String password) throws AuthenticationException {
        if (password == null) {
            throw new AuthenticationException(sm.getString("authenticator.nullPassword"));
        }
    }


    protected void validateRealm(String userRealm, String serverRealm) throws AuthenticationException {
        if (userRealm == null) {
            return;
        }

        userRealm = userRealm.trim();
        if (userRealm.length() == 0) {
            return;
        }

        /*
         * User has configured a realm. Only allow authentication to proceed if
         * the realm in the authentication challenge matches (both BASIC and
         * DIGEST are required to include a realm).
         */
        if (serverRealm != null) {
            serverRealm = serverRealm.trim();
            if (userRealm.equals(serverRealm)) {
                return;
            }
        }

        throw new AuthenticationException(sm.getString("authenticator.realmMismatch", userRealm, serverRealm));
    }
}

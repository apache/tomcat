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

/**
 * Defines the authentication types supported by the WebSocket client.
 */
public enum AuthenticationType {

    /**
     * HTTP Basic authentication over WebSocket.
     */
    WWW(Constants.AUTHORIZATION_HEADER_NAME, Constants.WWW_AUTHENTICATE_HEADER_NAME,
            Constants.WS_AUTHENTICATION_USER_NAME, Constants.WS_AUTHENTICATION_PASSWORD,
            Constants.WS_AUTHENTICATION_REALM),

    /**
     * HTTP Proxy authentication over WebSocket.
     */
    PROXY(Constants.PROXY_AUTHORIZATION_HEADER_NAME, Constants.PROXY_AUTHENTICATE_HEADER_NAME,
            Constants.WS_AUTHENTICATION_PROXY_USER_NAME, Constants.WS_AUTHENTICATION_PROXY_PASSWORD,
            Constants.WS_AUTHENTICATION_PROXY_REALM);

    private final String authorizationHeaderName;
    private final String authenticateHeaderName;
    private final String userNameProperty;
    private final String userPasswordProperty;
    private final String userRealmProperty;

    /**
     * Constructs an AuthenticationType with the given header and property names.
     *
     * @param authorizationHeaderName the name of the authorization header
     * @param authenticateHeaderName the name of the authenticate header
     * @param userNameProperty the property name for the user name
     * @param userPasswordProperty the property name for the user password
     * @param userRealmProperty the property name for the user realm
     */
    AuthenticationType(String authorizationHeaderName, String authenticateHeaderName, String userNameProperty,
            String userPasswordProperty, String userRealmProperty) {
        this.authorizationHeaderName = authorizationHeaderName;
        this.authenticateHeaderName = authenticateHeaderName;
        this.userNameProperty = userNameProperty;
        this.userPasswordProperty = userPasswordProperty;
        this.userRealmProperty = userRealmProperty;
    }

    /**
     * Returns the name of the authorization header.
     *
     * @return the authorization header name
     */
    public String getAuthorizationHeaderName() {
        return authorizationHeaderName;
    }

    /**
     * Returns the name of the authenticate header.
     *
     * @return the authenticate header name
     */
    public String getAuthenticateHeaderName() {
        return authenticateHeaderName;
    }

    /**
     * Returns the property name for the user name.
     *
     * @return the user name property name
     */
    public String getUserNameProperty() {
        return userNameProperty;
    }

    /**
     * Returns the property name for the user password.
     *
     * @return the user password property name
     */
    public String getUserPasswordProperty() {
        return userPasswordProperty;
    }

    /**
     * Returns the property name for the user realm.
     *
     * @return the user realm property name
     */
    public String getUserRealmProperty() {
        return userRealmProperty;
    }
}

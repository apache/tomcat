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

public enum AuthenticationType {

    WWW(Constants.AUTHORIZATION_HEADER_NAME,
            Constants.WWW_AUTHENTICATE_HEADER_NAME,
            Constants.WS_AUTHENTICATION_USER_NAME,
            Constants.WS_AUTHENTICATION_PASSWORD,
            Constants.WS_AUTHENTICATION_REALM),

    PROXY(Constants.PROXY_AUTHORIZATION_HEADER_NAME,
            Constants.PROXY_AUTHENTICATE_HEADER_NAME,
            Constants.WS_AUTHENTICATION_PROXY_USER_NAME,
            Constants.WS_AUTHENTICATION_PROXY_PASSWORD,
            Constants.WS_AUTHENTICATION_PROXY_REALM);

    private final String authorizationHeaderName;
    private final String authenticateHeaderName;
    private final String userNameProperty;
    private final String userPasswordProperty;
    private final String userRealmProperty;

    private AuthenticationType(String authorizationHeaderName, String authenticateHeaderName, String userNameProperty,
            String userPasswordProperty, String userRealmProperty) {
        this.authorizationHeaderName = authorizationHeaderName;
        this.authenticateHeaderName = authenticateHeaderName;
        this.userNameProperty = userNameProperty;
        this.userPasswordProperty = userPasswordProperty;
        this.userRealmProperty = userRealmProperty;
    }

    public String getAuthorizationHeaderName() {
        return authorizationHeaderName;
    }

    public String getAuthenticateHeaderName() {
        return authenticateHeaderName;
    }

    public String getUserNameProperty() {
        return userNameProperty;
    }

    public String getUserPasswordProperty() {
        return userPasswordProperty;
    }

    public String getUserRealmProperty() {
        return userRealmProperty;
    }
}

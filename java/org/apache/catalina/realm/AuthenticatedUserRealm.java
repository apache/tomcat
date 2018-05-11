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
package org.apache.catalina.realm;

import java.security.Principal;

/**
 * This Realm is intended for use with Authenticator implementations
 * ({@link org.apache.catalina.authenticator.SSLAuthenticator},
 * {@link org.apache.catalina.authenticator.SpnegoAuthenticator}) that
 * authenticate the user as well as obtain the user credentials. An
 * authenticated Principal is always created from the user name presented to
 * without further validation.
 * <p>
 * <strong>Note:</strong> It is unsafe to use this Realm with Authenticator
 * implementations that do not validate the provided credentials.
 */
public class AuthenticatedUserRealm extends RealmBase {

    @Override
    protected String getPassword(String username) {
        // Passwords never need validating so always return null
        return null;
    }

    @Override
    protected Principal getPrincipal(String username) {
        // The authentication mechanism has authenticated the user so create
        // the Principal directly
        return new GenericPrincipal(username, null, null);
    }
}

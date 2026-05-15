/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jakarta.security.auth.message.config;

import java.util.Map;

import javax.security.auth.Subject;

import jakarta.security.auth.message.AuthException;

/**
 * Client-side authentication configuration obtained from an {@link AuthConfigProvider}. A ClientAuthConfig provides
 * the configuration needed to create a {@link ClientAuthContext} for authenticating outbound requests.
 */
public interface ClientAuthConfig extends AuthConfig {

    /**
     * Creates a {@link ClientAuthContext} for the specified authentication context ID.
     *
     * @param authContextID the authentication context ID
     * @param clientSubject the subject representing the client, to be populated upon successful authentication
     * @param properties    additional configuration properties
     *
     * @return the ClientAuthContext, or {@code null} if the authContextID is not recognized
     *
     * @throws AuthException if an error occurs while creating the context
     */
    ClientAuthContext getAuthContext(String authContextID, Subject clientSubject, Map<String,Object> properties)
            throws AuthException;
}

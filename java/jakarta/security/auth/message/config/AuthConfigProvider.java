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

import javax.security.auth.callback.CallbackHandler;

import jakarta.security.auth.message.AuthException;

/**
 * Provides {@link ClientAuthConfig} and {@link ServerAuthConfig} instances to JASPIC authentication modules. An
 * AuthConfigProvider is responsible for loading authentication configuration from a source (e.g., a configuration
 * file, database, or service) and making it available to the authentication modules.
 */
public interface AuthConfigProvider {

    /**
     * Returns the client-side authentication configuration for the specified layer and application context.
     *
     * @param layer      the message layer
     * @param appContext the application context
     * @param handler    the callback handler for obtaining sensitive configuration data
     *
     * @return the ClientAuthConfig, or {@code null} if no configuration is available
     *
     * @throws AuthException if an error occurs while retrieving the configuration
     */
    ClientAuthConfig getClientAuthConfig(String layer, String appContext, CallbackHandler handler) throws AuthException;

    /**
     * Returns the server-side authentication configuration for the specified layer and application context.
     *
     * @param layer      the message layer
     * @param appContext the application context
     * @param handler    the callback handler for obtaining sensitive configuration data
     *
     * @return the ServerAuthConfig, or {@code null} if no configuration is available
     *
     * @throws AuthException if an error occurs while retrieving the configuration
     */
    ServerAuthConfig getServerAuthConfig(String layer, String appContext, CallbackHandler handler) throws AuthException;

    /**
     * Refreshes the configuration, reloading any changed settings from the underlying source.
     */
    void refresh();
}

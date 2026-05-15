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
package org.apache.catalina.authenticator.jaspic;

import java.util.Map;

import javax.security.auth.callback.CallbackHandler;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.security.auth.message.config.AuthConfigProvider;
import jakarta.security.auth.message.config.ClientAuthConfig;
import jakarta.security.auth.message.config.ServerAuthConfig;

/**
 * Basic implementation primarily intended for use when using third-party
 * {@link jakarta.security.auth.message.module.ServerAuthModule} implementations that only provide the module.
 */
public class SimpleAuthConfigProvider implements AuthConfigProvider {

    private final Map<String,Object> properties;

    private volatile ServerAuthConfig serverAuthConfig;

    /**
     * Creates a new SimpleAuthConfigProvider.
     *
     * @param properties Properties to pass to the ServerAuthConfig
     * @param factory AuthConfigFactory to register this provider with, or {@code null}
     */
    public SimpleAuthConfigProvider(Map<String,Object> properties, AuthConfigFactory factory) {
        this.properties = properties;
        if (factory != null) {
            factory.registerConfigProvider(this, null, null, "Automatic registration");
        }
    }


    /**
     * {@inheritDoc}
     * <p>
     * This implementation does not support client-side authentication and therefore always returns {@code null}.
     */
    @Override
    public ClientAuthConfig getClientAuthConfig(String layer, String appContext, CallbackHandler handler)
            throws AuthException {
        return null;
    }


    /**
     * {@inheritDoc}
     * <p>
     * The returned ServerAuthConfig is created lazily and cached.
     */
    @Override
    public ServerAuthConfig getServerAuthConfig(String layer, String appContext, CallbackHandler handler)
            throws AuthException {
        ServerAuthConfig serverAuthConfig = this.serverAuthConfig;
        if (serverAuthConfig == null) {
            synchronized (this) {
                if (this.serverAuthConfig == null) {
                    this.serverAuthConfig = createServerAuthConfig(layer, appContext, handler, properties);
                }
                serverAuthConfig = this.serverAuthConfig;
            }
        }
        return serverAuthConfig;
    }


    /**
     * Creates the ServerAuthConfig. Can be overridden by subclasses to provide a custom implementation.
     *
     * @param layer Message layer
     * @param appContext Application context
     * @param handler Callback handler
     * @param properties Configuration properties
     * @return The ServerAuthConfig instance
     */
    protected ServerAuthConfig createServerAuthConfig(String layer, String appContext, CallbackHandler handler,
            Map<String,Object> properties) {
        return new SimpleServerAuthConfig(layer, appContext, handler, properties);
    }


    /**
     * {@inheritDoc}
     * <p>
     * Delegates refresh to the cached ServerAuthConfig if one has been created.
     */
    @Override
    public void refresh() {
        ServerAuthConfig serverAuthConfig = this.serverAuthConfig;
        if (serverAuthConfig != null) {
            serverAuthConfig.refresh();
        }
    }
}

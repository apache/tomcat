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
package org.apache.catalina.authenticator.jaspic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.message.config.AuthConfigFactory;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.RegistrationListener;

public class AuthConfigFactoryImpl extends AuthConfigFactory {

    private Map<String, ConfigProviderInfo> configProviders = new HashMap<>();


    @Override
    public AuthConfigProvider getConfigProvider(String layer, String appContext,
            RegistrationListener listener) {

        String registrationKey = getRegistrationKey(layer, appContext);

        ConfigProviderInfo provider = configProviders.get(registrationKey);
        if (provider == null) {
            provider = configProviders.get(getRegistrationKey(null, appContext));
        }
        if (provider == null) {
            provider = configProviders.get(getRegistrationKey(layer, null));
        }
        if (provider == null) {
            provider = configProviders.get(getRegistrationKey(null, null));
        }
        if (provider == null) {
            return null;
        }

        if (listener != null) {
            provider.addListener(listener);
        }

        return provider.getAuthConfigProvider();
    }


    @Override
    @SuppressWarnings("rawtypes") // JASPIC API uses raw types
    public String registerConfigProvider(String className, Map properties, String layer,
            String appContext, String description) {
        throw new IllegalStateException("Not implemented yet!");
    }


    @Override
    public String registerConfigProvider(AuthConfigProvider provider, String layer,
            String appContext, String description) {

        String registrationId = getRegistrationKey(layer, appContext);
        ConfigProviderInfo providerInfo =
                new ConfigProviderInfo(provider, true, layer, appContext, description);
        configProviders.put(registrationId, providerInfo);
        return registrationId;
    }


    @Override
    public boolean removeRegistration(String registrationID) {
        return configProviders.remove(registrationID) != null;
    }


    @Override
    public String[] detachListener(RegistrationListener listener, String layer, String appContext) {
        return null;
    }


    @Override
    public String[] getRegistrationIDs(AuthConfigProvider provider) {
        return null;
    }


    @Override
    public RegistrationContext getRegistrationContext(String registrationID) {
        return configProviders.get(registrationID);
    }


    @Override
    public void refresh() {

    }


    private String getRegistrationKey(String layer, String appContext) {
        return layer + "/" + appContext;
    }


    private static class ConfigProviderInfo implements AuthConfigFactory.RegistrationContext {
        private final AuthConfigProvider authConfigProvider;
        private String appContext;
        private String description;
        private String messageLayer;
        private final boolean persistent;
        private final List<RegistrationListener> listeners = new ArrayList<>();

        private ConfigProviderInfo(AuthConfigProvider authConfigProvider, boolean persistent,
                String layer, String appContext, String description) {
            this.authConfigProvider = authConfigProvider;
            this.persistent = persistent;
            this.messageLayer = layer;
            this.appContext = appContext;
            this.description = description;
        }

        private ConfigProviderInfo(AuthConfigProvider authConfigProvider,
                List<RegistrationListener> listeners, boolean persistent) {
            this.authConfigProvider = authConfigProvider;
            this.persistent = persistent;
        }

        public AuthConfigProvider getAuthConfigProvider() {
            return authConfigProvider;
        }

        public List<RegistrationListener> getListeners() {
            return listeners;
        }

        public void addListener(RegistrationListener listener) {
            listeners.add(listener);
        }

        @Override
        public String getAppContext() {
            return appContext;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getMessageLayer() {
            return messageLayer;
        }

        @Override
        public boolean isPersistent() {
            return persistent;
        }
    }
}

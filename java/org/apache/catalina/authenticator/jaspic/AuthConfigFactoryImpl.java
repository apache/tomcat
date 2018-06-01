/**
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

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.security.auth.message.config.AuthConfigFactory;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.RegistrationListener;

import org.apache.catalina.Globals;
import org.apache.catalina.authenticator.jaspic.PersistentProviderRegistrations.Provider;
import org.apache.catalina.authenticator.jaspic.PersistentProviderRegistrations.Providers;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class AuthConfigFactoryImpl extends AuthConfigFactory {

    private final Log log = LogFactory.getLog(AuthConfigFactoryImpl.class); // must not be static
    private static final StringManager sm = StringManager.getManager(AuthConfigFactoryImpl.class);

    private static final String CONFIG_PATH = "conf/jaspic-providers.xml";
    private static final File CONFIG_FILE =
            new File(System.getProperty(Globals.CATALINA_BASE_PROP), CONFIG_PATH);
    private static final Object CONFIG_FILE_LOCK = new Object();

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static String DEFAULT_REGISTRATION_ID = getRegistrationID(null, null);

    private final Map<String,RegistrationContextImpl> layerAppContextRegistrations =
            new ConcurrentHashMap<>();
    private final Map<String,RegistrationContextImpl> appContextRegistrations =
            new ConcurrentHashMap<>();
    private final Map<String,RegistrationContextImpl> layerRegistrations =
            new ConcurrentHashMap<>();
    // Note: Although there will only ever be a maximum of one entry in this
    //       Map, use a ConcurrentHashMap for consistency
    private final Map<String,RegistrationContextImpl> defaultRegistration =
            new ConcurrentHashMap<>(1);


    public AuthConfigFactoryImpl() {
        loadPersistentRegistrations();
    }


    @Override
    public AuthConfigProvider getConfigProvider(String layer, String appContext,
            RegistrationListener listener) {
        RegistrationContextImpl registrationContext =
                findRegistrationContextImpl(layer, appContext);
        if (registrationContext != null) {
            if (listener != null) {
                RegistrationListenerWrapper wrapper = new RegistrationListenerWrapper(
                        layer, appContext, listener);
                registrationContext.addListener(wrapper);
            }
            return registrationContext.getProvider();
        }
        return null;
    }


    @Override
    public String registerConfigProvider(String className,
            @SuppressWarnings("rawtypes") Map properties, String layer, String appContext,
            String description) {
        String registrationID =
                doRegisterConfigProvider(className, properties, layer, appContext, description);
        savePersistentRegistrations();
        return registrationID;
    }


    @SuppressWarnings("unchecked")
    private String doRegisterConfigProvider(String className,
            @SuppressWarnings("rawtypes") Map properties, String layer, String appContext,
            String description) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("authConfigFactoryImpl.registerClass",
                    className, layer, appContext));
        }

        AuthConfigProvider provider = null;
        if (className != null) {
            provider = createAuthConfigProvider(className, properties);
        }

        String registrationID = getRegistrationID(layer, appContext);
        RegistrationContextImpl registrationContextImpl = new RegistrationContextImpl(
                layer, appContext, description, true, provider, properties);
        addRegistrationContextImpl(layer, appContext, registrationID, registrationContextImpl);
        return registrationID;
    }


    private AuthConfigProvider createAuthConfigProvider(String className,
            @SuppressWarnings("rawtypes") Map properties) throws SecurityException {
        Class<?> clazz = null;
        AuthConfigProvider provider = null;
        try {
            clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            // Ignore so the re-try below can proceed
        }
        try {
            if (clazz == null) {
                clazz = Class.forName(className);
            }
            Constructor<?> constructor = clazz.getConstructor(Map.class, AuthConfigFactory.class);
            provider = (AuthConfigProvider) constructor.newInstance(properties, null);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new SecurityException(e);
        }
        return provider;
    }


    @Override
    public String registerConfigProvider(AuthConfigProvider provider, String layer,
            String appContext, String description) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("authConfigFactoryImpl.registerInstance",
                    provider.getClass().getName(), layer, appContext));
        }
        String registrationID = getRegistrationID(layer, appContext);
        RegistrationContextImpl registrationContextImpl = new RegistrationContextImpl(
                layer, appContext, description, false, provider, null);
        addRegistrationContextImpl(layer, appContext, registrationID, registrationContextImpl);
        return registrationID;
    }


    private void addRegistrationContextImpl(String layer, String appContext,
            String registrationID, RegistrationContextImpl registrationContextImpl) {
        RegistrationContextImpl previous = null;

        // Add the registration, noting any registration it replaces
        if (layer != null && appContext != null) {
            previous = layerAppContextRegistrations.put(registrationID, registrationContextImpl);
        } else if (layer == null && appContext != null) {
            previous = appContextRegistrations.put(registrationID, registrationContextImpl);
        } else if (layer != null && appContext == null) {
            previous = layerRegistrations.put(registrationID, registrationContextImpl);
        } else {
            previous = defaultRegistration.put(registrationID, registrationContextImpl);
        }

        if (previous == null) {
            // No match with previous registration so need to check listeners
            // for all less specific registrations to see if they need to be
            // notified of this new registration. That there is no exact match
            // with a previous registration allows a few short-cuts to be taken
            if (layer != null && appContext != null) {
                // Need to check existing appContext registrations
                // (and layer and default)
                // appContext must match
                RegistrationContextImpl registration =
                        appContextRegistrations.get(getRegistrationID(null, appContext));
                if (registration != null) {
                    for (RegistrationListenerWrapper wrapper : registration.listeners) {
                        if (layer.equals(wrapper.getMessageLayer()) &&
                                appContext.equals(wrapper.getAppContext())) {
                            registration.listeners.remove(wrapper);
                            wrapper.listener.notify(wrapper.messageLayer, wrapper.appContext);
                        }
                    }
                }
            }
            if (appContext != null) {
                // Need to check existing layer registrations
                // (and default)
                // Need to check registrations for all layers
                for (RegistrationContextImpl registration : layerRegistrations.values()) {
                    for (RegistrationListenerWrapper wrapper : registration.listeners) {
                        if (appContext.equals(wrapper.getAppContext())) {
                            registration.listeners.remove(wrapper);
                            wrapper.listener.notify(wrapper.messageLayer, wrapper.appContext);
                        }
                    }
                }
            }
            if (layer != null || appContext != null) {
                // Need to check default
                for (RegistrationContextImpl registration : defaultRegistration.values()) {
                    for (RegistrationListenerWrapper wrapper : registration.listeners) {
                        if (appContext != null && appContext.equals(wrapper.getAppContext()) ||
                                layer != null && layer.equals(wrapper.getMessageLayer())) {
                            registration.listeners.remove(wrapper);
                            wrapper.listener.notify(wrapper.messageLayer, wrapper.appContext);
                        }
                    }
                }
            }
        } else {
            // Replaced an existing registration so need to notify those listeners
            for (RegistrationListenerWrapper wrapper : previous.listeners) {
                previous.listeners.remove(wrapper);
                wrapper.listener.notify(wrapper.messageLayer, wrapper.appContext);
            }
        }
    }


    @Override
    public boolean removeRegistration(String registrationID) {
        RegistrationContextImpl registration = null;
        if (DEFAULT_REGISTRATION_ID.equals(registrationID)) {
            registration = defaultRegistration.remove(registrationID);
        }
        if (registration == null) {
            registration = layerAppContextRegistrations.remove(registrationID);
        }
        if (registration == null) {
            registration =  appContextRegistrations.remove(registrationID);
        }
        if (registration == null) {
            registration = layerRegistrations.remove(registrationID);
        }

        if (registration == null) {
            return false;
        } else {
            for (RegistrationListenerWrapper wrapper : registration.listeners) {
                wrapper.getListener().notify(wrapper.getMessageLayer(), wrapper.getAppContext());
            }
            if (registration.isPersistent()) {
                savePersistentRegistrations();
            }
            return true;
        }
    }


    @Override
    public String[] detachListener(RegistrationListener listener, String layer, String appContext) {
        String registrationID = getRegistrationID(layer, appContext);
        RegistrationContextImpl registrationContext = findRegistrationContextImpl(layer, appContext);
        if (registrationContext != null && registrationContext.removeListener(listener)) {
            return new String[] { registrationID };
        }
        return EMPTY_STRING_ARRAY;
    }


    @Override
    public String[] getRegistrationIDs(AuthConfigProvider provider) {
        List<String> result = new ArrayList<>();
        if (provider == null) {
            result.addAll(layerAppContextRegistrations.keySet());
            result.addAll(appContextRegistrations.keySet());
            result.addAll(layerRegistrations.keySet());
            if (!defaultRegistration.isEmpty()) {
                result.add(DEFAULT_REGISTRATION_ID);
            }
        } else {
            findProvider(provider, layerAppContextRegistrations, result);
            findProvider(provider, appContextRegistrations, result);
            findProvider(provider, layerRegistrations, result);
            findProvider(provider, defaultRegistration, result);
        }
        return result.toArray(EMPTY_STRING_ARRAY);
    }


    private void findProvider(AuthConfigProvider provider,
            Map<String,RegistrationContextImpl> registrations, List<String> result) {
        for (Entry<String,RegistrationContextImpl> entry : registrations.entrySet()) {
            if (provider.equals(entry.getValue().getProvider())) {
                result.add(entry.getKey());
            }
        }
    }


    @Override
    public RegistrationContext getRegistrationContext(String registrationID) {
        RegistrationContext result = defaultRegistration.get(registrationID);
        if (result == null) {
            result = layerAppContextRegistrations.get(registrationID);
        }
        if (result == null) {
            result = appContextRegistrations.get(registrationID);
        }
        if (result == null) {
            result = layerRegistrations.get(registrationID);
        }
        return result;
    }


    @Override
    public void refresh() {
        loadPersistentRegistrations();
    }


    private static String getRegistrationID(String layer, String appContext) {
        if (layer != null && layer.length() == 0) {
            throw new IllegalArgumentException(
                    sm.getString("authConfigFactoryImpl.zeroLengthMessageLayer"));
        }
        if (appContext != null && appContext.length() == 0) {
            throw new IllegalArgumentException(
                    sm.getString("authConfigFactoryImpl.zeroLengthAppContext"));
        }
        return (layer == null ? "" : layer) + ":" + (appContext == null ? "" : appContext);
    }


    private void loadPersistentRegistrations() {
        synchronized (CONFIG_FILE_LOCK) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("authConfigFactoryImpl.load",
                        CONFIG_FILE.getAbsolutePath()));
            }
            if (!CONFIG_FILE.isFile()) {
                return;
            }
            Providers providers = PersistentProviderRegistrations.loadProviders(CONFIG_FILE);
            for (Provider provider : providers.getProviders()) {
                doRegisterConfigProvider(provider.getClassName(), provider.getProperties(),
                        provider.getLayer(), provider.getAppContext(), provider.getDescription());
            }
        }
    }


    private void savePersistentRegistrations() {
        synchronized (CONFIG_FILE_LOCK) {
            Providers providers = new Providers();
            savePersistentProviders(providers, layerAppContextRegistrations);
            savePersistentProviders(providers, appContextRegistrations);
            savePersistentProviders(providers, layerRegistrations);
            savePersistentProviders(providers, defaultRegistration);
            PersistentProviderRegistrations.writeProviders(providers, CONFIG_FILE);
        }
    }


    private void savePersistentProviders(Providers providers,
            Map<String,RegistrationContextImpl> registrations) {
        for (Entry<String,RegistrationContextImpl> entry : registrations.entrySet()) {
            savePersistentProvider(providers, entry.getValue());
        }
    }


    private void savePersistentProvider(Providers providers,
            RegistrationContextImpl registrationContextImpl) {
        if (registrationContextImpl != null && registrationContextImpl.isPersistent()) {
            Provider provider = new Provider();
            provider.setAppContext(registrationContextImpl.getAppContext());
            if (registrationContextImpl.getProvider() != null) {
                provider.setClassName(registrationContextImpl.getProvider().getClass().getName());
            }
            provider.setDescription(registrationContextImpl.getDescription());
            provider.setLayer(registrationContextImpl.getMessageLayer());
            for (Entry<String,String> property : registrationContextImpl.getProperties().entrySet()) {
                provider.addProperty(property.getKey(), property.getValue());
            }
            providers.addProvider(provider);
        }
    }


    private RegistrationContextImpl findRegistrationContextImpl(String layer, String appContext) {
        RegistrationContextImpl result;
        result = layerAppContextRegistrations.get(getRegistrationID(layer, appContext));
        if (result == null) {
            result = appContextRegistrations.get(getRegistrationID(null, appContext));
        }
        if (result == null) {
            result = layerRegistrations.get(getRegistrationID(layer, null));
        }
        if (result == null) {
            result = defaultRegistration.get(DEFAULT_REGISTRATION_ID);
        }
        return result;
    }


    private static class RegistrationContextImpl implements RegistrationContext {

        private RegistrationContextImpl(String messageLayer, String appContext, String description,
                boolean persistent, AuthConfigProvider provider, Map<String,String> properties) {
            this.messageLayer = messageLayer;
            this.appContext = appContext;
            this.description = description;
            this.persistent = persistent;
            this.provider = provider;
            Map<String,String> propertiesCopy = new HashMap<>();
            if (properties != null) {
                propertiesCopy.putAll(properties);
            }
            this.properties = Collections.unmodifiableMap(propertiesCopy);
        }

        private final String messageLayer;
        private final String appContext;
        private final String description;
        private final boolean persistent;
        private final AuthConfigProvider provider;
        private final Map<String,String> properties;
        private final List<RegistrationListenerWrapper> listeners = new CopyOnWriteArrayList<>();

        @Override
        public String getMessageLayer() {
            return messageLayer;
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
        public boolean isPersistent() {
            return persistent;
        }


        private AuthConfigProvider getProvider() {
            return provider;
        }


        private void addListener(RegistrationListenerWrapper listener) {
            if (listener != null) {
                listeners.add(listener);
            }
        }


        private Map<String,String> getProperties() {
            return properties;
        }


        private boolean removeListener(RegistrationListener listener) {
            boolean result = false;
            for (RegistrationListenerWrapper wrapper : listeners) {
                if (wrapper.getListener().equals(listener)) {
                    listeners.remove(wrapper);
                    result = true;
                }
            }
            return result;
        }
    }


    private static class RegistrationListenerWrapper {

        private final String messageLayer;
        private final String appContext;
        private final RegistrationListener listener;


        public RegistrationListenerWrapper(String messageLayer, String appContext,
                RegistrationListener listener) {
            this.messageLayer = messageLayer;
            this.appContext = appContext;
            this.listener = listener;
        }


        public String getMessageLayer() {
            return messageLayer;
        }


        public String getAppContext() {
            return appContext;
        }


        public RegistrationListener getListener() {
            return listener;
        }
    }
}

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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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

    private static final Log log = LogFactory.getLog(AuthConfigFactoryImpl.class);
    private static final StringManager sm = StringManager.getManager(AuthConfigFactoryImpl.class);

    private static final String CONFIG_PATH = "conf/jaspic-providers.xml";
    private static final File CONFIG_FILE =
            new File(System.getProperty(Globals.CATALINA_BASE_PROP), CONFIG_PATH);
    private static final Object CONFIG_FILE_LOCK = new Object();

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private final Map<String,RegistrationContextImpl> registrations = new ConcurrentHashMap<>();


    public AuthConfigFactoryImpl() {
        loadPersistentRegistrations();
    }


    @Override
    public AuthConfigProvider getConfigProvider(String layer, String appContext,
            RegistrationListener listener) {
        String registrationID = getRegistrarionID(layer, appContext);
        RegistrationContextImpl registrationContext = registrations.get(registrationID);
        if (registrationContext != null) {
            registrationContext.addListener(null);
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
        Class<?> clazz;
        AuthConfigProvider provider = null;
        try {
            clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            // Ignore so the re-try below can proceed
        }
        try {
            clazz = Class.forName(className);
            Constructor<?> constructor = clazz.getConstructor(Map.class, AuthConfigFactory.class);
            provider = (AuthConfigProvider) constructor.newInstance(properties, null);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new SecurityException(e);
        }

        String registrationID = getRegistrarionID(layer, appContext);
        registrations.put(registrationID,
                new RegistrationContextImpl(layer, appContext, description, true, provider, properties));
        return registrationID;
    }


    @Override
    public String registerConfigProvider(AuthConfigProvider provider, String layer,
            String appContext, String description) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("authConfigFactoryImpl.registerInstance",
                    provider.getClass().getName(), layer, appContext));
        }
        String registrationID = getRegistrarionID(layer, appContext);
        registrations.put(registrationID,
                new RegistrationContextImpl(layer, appContext, description, false, provider, null));
        return registrationID;
    }


    @Override
    public boolean removeRegistration(String registrationID) {
        return registrations.remove(registrationID) != null;
    }


    @Override
    public String[] detachListener(RegistrationListener listener, String layer, String appContext) {
        String registrationID = getRegistrarionID(layer, appContext);
        RegistrationContextImpl registrationContext = registrations.get(registrationID);
        if (registrationContext.removeListener(listener)) {
            return new String[] { registrationID };
        }
        return EMPTY_STRING_ARRAY;
    }


    @Override
    public String[] getRegistrationIDs(AuthConfigProvider provider) {
        if (provider == null) {
            return registrations.keySet().toArray(EMPTY_STRING_ARRAY);
        } else {
            List<String> results = new ArrayList<>();
            for (Entry<String,RegistrationContextImpl> entry : registrations.entrySet()) {
                if (provider.equals(entry.getValue().getProvider())) {
                    results.add(entry.getKey());
                }
            }
            return results.toArray(EMPTY_STRING_ARRAY);
        }
    }


    @Override
    public RegistrationContext getRegistrationContext(String registrationID) {
        return registrations.get(registrationID);
    }


    @Override
    public void refresh() {
        loadPersistentRegistrations();
    }


    private String getRegistrarionID(String layer, String appContext) {
        return layer + ":" + appContext;
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
            for (Entry<String,RegistrationContextImpl> entry : registrations.entrySet()) {
                if (entry.getValue().isPersistent()) {
                    Provider provider = new Provider();
                    provider.setAppContext(entry.getValue().getAppContext());
                    provider.setClassName(entry.getValue().getProvider().getClass().getName());
                    provider.setDescription(entry.getValue().getDescription());
                    provider.setLayer(entry.getValue().getMessageLayer());
                    for (Entry<String,String> property : entry.getValue().getProperties().entrySet()) {
                        provider.addProperty(property.getKey(), property.getValue());
                    }
                    providers.addProvider(provider);
                }
            }
            PersistentProviderRegistrations.writeProviders(providers, CONFIG_FILE);
        }
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
        private final List<RegistrationListener> listeners = new CopyOnWriteArrayList<>();

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


        private void addListener(RegistrationListener listener) {
            if (listener != null) {
                listeners.add(listener);
            }
        }


        private Map<String,String> getProperties() {
            return properties;
        }


        private boolean removeListener(RegistrationListener listener) {
            boolean result = false;
            Iterator<RegistrationListener> iter = listeners.iterator();
            while (iter.hasNext()) {
                if (iter.next().equals(listener)) {
                    iter.remove();
                }
            }
            return result;
        }
    }
}

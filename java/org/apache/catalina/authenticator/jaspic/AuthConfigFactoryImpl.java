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

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.AuthStatus;
import jakarta.security.auth.message.MessageInfo;
import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.security.auth.message.config.AuthConfigProvider;
import jakarta.security.auth.message.config.ClientAuthConfig;
import jakarta.security.auth.message.config.RegistrationListener;
import jakarta.security.auth.message.config.ServerAuthConfig;
import jakarta.security.auth.message.config.ServerAuthContext;
import jakarta.security.auth.message.module.ServerAuthModule;
import jakarta.servlet.ServletContext;

import org.apache.catalina.Globals;
import org.apache.catalina.authenticator.jaspic.PersistentProviderRegistrations.Provider;
import org.apache.catalina.authenticator.jaspic.PersistentProviderRegistrations.Providers;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Implementation of {@link AuthConfigFactory} for the JASPIC authentication framework.
 */
public class AuthConfigFactoryImpl extends AuthConfigFactory {

    private final Log log = LogFactory.getLog(AuthConfigFactoryImpl.class); // must not be static
    private static final StringManager sm = StringManager.getManager(AuthConfigFactoryImpl.class);

    private static final String CONFIG_PATH = "conf/jaspic-providers.xml";
    private static final File CONFIG_FILE = new File(System.getProperty(Globals.CATALINA_BASE_PROP), CONFIG_PATH);
    private static final Object CONFIG_FILE_LOCK = new Object();

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final String SERVLET_LAYER_ID = "HttpServlet";

    private static final String DEFAULT_REGISTRATION_ID = getRegistrationID(null, null);

    private final ReentrantLock registrationLock = new ReentrantLock();
    private final Map<String,RegistrationContextImpl> registrationToRegistrationContextMap = new HashMap<>();
    private final Map<String,Set<RegistrationListenerWrapper>> registrationToWrappersMap = new HashMap<>();
    private final Map<RegistrationListenerWrapper,Set<String>> wrapperToRegistrationsMap = new HashMap<>();


    /**
     * Constructs a new AuthConfigFactoryImpl and loads any persistent registrations.
     */
    public AuthConfigFactoryImpl() {
        loadPersistentRegistrations();
    }


    @Override
    public AuthConfigProvider getConfigProvider(String layer, String appContext, RegistrationListener listener) {
        RegistrationListenerWrapper wrapper;
        if (listener == null) {
            wrapper = null;
        } else {
            wrapper = new RegistrationListenerWrapper(layer, appContext, listener);
        }
        RegistrationContextImpl registrationContext;

        registrationLock.lock();
        try {
            // First check for a layer and appContext match
            String fullID = getRegistrationID(layer, appContext);
            registerWrapper(wrapper, fullID);
            registrationContext = registrationToRegistrationContextMap.get(fullID);

            if (registrationContext == null) {
                String appContextID = getRegistrationID(null, appContext);
                registerWrapper(wrapper, appContextID);
                registrationContext = registrationToRegistrationContextMap.get(appContextID);
            }
            if (registrationContext == null) {
                String layerID = getRegistrationID(layer, null);
                registerWrapper(wrapper, layerID);
                registrationContext = registrationToRegistrationContextMap.get(layerID);
            }
            if (registrationContext == null) {
                registerWrapper(wrapper, DEFAULT_REGISTRATION_ID);
                registrationContext = registrationToRegistrationContextMap.get(DEFAULT_REGISTRATION_ID);
            }
        } finally {
            registrationLock.unlock();
        }

        if (registrationContext != null) {
            return registrationContext.getProvider();
        }
        return null;
    }


    private void registerWrapper(RegistrationListenerWrapper wrapper, String registrationID) {
        if (wrapper != null) {
            Set<RegistrationListenerWrapper> wrappersForRegistrationID =
                    registrationToWrappersMap.computeIfAbsent(registrationID, s -> new HashSet<>());
            wrappersForRegistrationID.add(wrapper);

            Set<String> registrationIDsForWrapper =
                    wrapperToRegistrationsMap.computeIfAbsent(wrapper, w -> new HashSet<>());
            registrationIDsForWrapper.add(registrationID);
        }
    }


    @Override
    public String registerConfigProvider(String className, Map<String,String> properties, String layer,
            String appContext, String description) {
        String registrationID;
        Set<RegistrationListenerWrapper> wrappersToNotify = new HashSet<>();
        registrationLock.lock();
        try {
            registrationID =
                    doRegisterConfigProvider(className, properties, layer, appContext, description, wrappersToNotify);
            savePersistentRegistrations();
        } finally {
            registrationLock.unlock();
            notifyWrappers(wrappersToNotify);
        }
        return registrationID;
    }


    private String doRegisterConfigProvider(String className, Map<String,String> properties, String layer,
            String appContext, String description, Set<RegistrationListenerWrapper> wrappersToNotify) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("authConfigFactoryImpl.registerClass", className, layer, appContext));
        }

        AuthConfigProvider provider = null;
        if (className != null) {
            provider = createAuthConfigProvider(className, properties);
        }

        String registrationID = getRegistrationID(layer, appContext);
        RegistrationContextImpl registrationContextImpl =
                new RegistrationContextImpl(layer, appContext, description, true, provider, properties);
        addRegistrationContextImpl(registrationID, registrationContextImpl, wrappersToNotify);

        return registrationID;
    }


    private AuthConfigProvider createAuthConfigProvider(String className, Map<String,String> properties)
            throws SecurityException {
        Class<?> clazz = null;
        AuthConfigProvider provider;
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
    public String registerConfigProvider(AuthConfigProvider provider, String layer, String appContext,
            String description) {
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("authConfigFactoryImpl.registerInstance", provider.getClass().getName(), layer,
                    appContext));
        }
        String registrationID = getRegistrationID(layer, appContext);
        RegistrationContextImpl registrationContextImpl =
                new RegistrationContextImpl(layer, appContext, description, false, provider, null);

        Set<RegistrationListenerWrapper> wrappersToNotify = new HashSet<>();
        addRegistrationContextImpl(registrationID, registrationContextImpl, wrappersToNotify);
        notifyWrappers(wrappersToNotify);

        return registrationID;
    }


    /*
     * Always notify outside of any locks to avoid potential deadlocks if the notification is processed/actioned on a
     * different thread.
     */
    private void notifyWrappers(Set<RegistrationListenerWrapper> wrappersToNotify) {
        for (RegistrationListenerWrapper wrapper : wrappersToNotify) {
            wrapper.listener.notify(wrapper.messageLayer, wrapper.appContext());
        }
    }

    private void addRegistrationContextImpl(String registrationID, RegistrationContextImpl registrationContextImpl,
            Set<RegistrationListenerWrapper> wrappersToNotify) {

        registrationLock.lock();
        try {
            // Add the new registration
            registrationToRegistrationContextMap.put(registrationID, registrationContextImpl);

            wrappersToNotify.addAll(detachListenersForRegistrationID(registrationID));
        } finally {
            registrationLock.unlock();
        }
    }


    private Set<RegistrationListenerWrapper> detachListenersForRegistrationID(String registrationID) {

        Set<RegistrationListenerWrapper> wrappersToNotify = new HashSet<>();

        // Check for listeners for this registration ID
        Set<RegistrationListenerWrapper> wrappersForRegistrationID = registrationToWrappersMap.get(registrationID);
        if (wrappersForRegistrationID != null) {
            wrappersToNotify.addAll(wrappersForRegistrationID);

            // Detach each listener that was attached to this registration ID
            for (RegistrationListenerWrapper wrapper : wrappersToNotify) {
                // Find all the registrationIDs the listener was attached to
                Set<String> listenerRegistrationIDs = wrapperToRegistrationsMap.remove(wrapper);
                // Remove the listener from each of the registrationIDs to which it was attached
                for (String listenerRegistrationID : listenerRegistrationIDs) {
                    registrationToWrappersMap.get(listenerRegistrationID).remove(wrapper);
                    if (registrationToWrappersMap.get(listenerRegistrationID).isEmpty()) {
                        registrationToWrappersMap.remove(listenerRegistrationID);
                    }
                }
            }
        }

        return wrappersToNotify;
    }


    @Override
    public boolean removeRegistration(String registrationID) {

        RegistrationContextImpl registration;
        Set<RegistrationListenerWrapper> wrappersToNotify = new HashSet<>();

        registrationLock.lock();
        try {
            registration = registrationToRegistrationContextMap.remove(registrationID);
            if (registration != null) {
                wrappersToNotify.addAll(detachListenersForRegistrationID(registrationID));
            }
            if (registration != null && registration.isPersistent()) {
                savePersistentRegistrations();
            }
        } finally {
            registrationLock.unlock();
            notifyWrappers(wrappersToNotify);
        }

        return registration != null;
    }


    @Override
    public String[] detachListener(RegistrationListener listener, String layer, String appContext) {
        List<String> results = new ArrayList<>();

        // Validate the inputs
        getRegistrationID(layer, appContext);
        if (listener == null) {
            return results.toArray(EMPTY_STRING_ARRAY);
        }

        registrationLock.lock();
        try {
            /*
             * The listener may have been attached multiple times under different combinations of layer and appContext.
             * Each combination of listener, layer and appContext is represented by a Wrapper. The detach call may match
             * more than one wrapper if the values passed in for layer and/or appContext are left as null.
             *
             * Iterate over all the wrappers to check for matches.
             */
            Iterator<Map.Entry<RegistrationListenerWrapper,Set<String>>> iter =
                    wrapperToRegistrationsMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<RegistrationListenerWrapper,Set<String>> entry = iter.next();
                RegistrationListenerWrapper wrapper = entry.getKey();
                if (listener.equals(wrapper.listener) && (layer == null || layer.equals(wrapper.messageLayer)) &&
                        (appContext == null || appContext.equals(wrapper.appContext))) {
                    // The wrapper matches. Add the original registration ID to the results.
                    results.add(getRegistrationID(wrapper.messageLayer, wrapper.appContext));
                    // The wrapper may have been attached to multiple registration IDs. Remove them all.
                    for (String listenerRegistrationID : entry.getValue()) {
                        registrationToWrappersMap.get(listenerRegistrationID).remove(wrapper);
                        if (registrationToWrappersMap.get(listenerRegistrationID).isEmpty()) {
                            registrationToWrappersMap.remove(listenerRegistrationID);
                        }
                    }
                    iter.remove();
                }
            }
        } finally {
            registrationLock.unlock();
        }

        return results.toArray(EMPTY_STRING_ARRAY);
    }


    @Override
    public String[] getRegistrationIDs(AuthConfigProvider provider) {
        List<String> result = new ArrayList<>();

        registrationLock.lock();
        try {
            for (Entry<String,RegistrationContextImpl> entry : registrationToRegistrationContextMap.entrySet()) {
                if (provider == null || provider.equals(entry.getValue().getProvider())) {
                    result.add(entry.getKey());
                }
            }
        } finally {
            registrationLock.unlock();
        }

        return result.toArray(EMPTY_STRING_ARRAY);
    }


    @Override
    public RegistrationContext getRegistrationContext(String registrationID) {
        registrationLock.lock();
        try {
            return registrationToRegistrationContextMap.get(registrationID);
        } finally {
            registrationLock.unlock();
        }
    }


    @Override
    public void refresh() {
        loadPersistentRegistrations();
    }


    @Override
    public String registerServerAuthModule(ServerAuthModule serverAuthModule, Object context) {
        if (context == null) {
            throw new IllegalArgumentException(sm.getString("authConfigFactoryImpl.nullContext"));
        }

        if (context instanceof ServletContext servletContext) {
            String appContext = servletContext.getVirtualServerName() + " " + servletContext.getContextPath();

            AuthConfigProvider authConfigProvider = new SingleConfigAuthConfigProvider(serverAuthModule, appContext);

            return registerConfigProvider(authConfigProvider, SERVLET_LAYER_ID, appContext, "");
        }

        // Unsupported context type
        throw new IllegalArgumentException(
                sm.getString("authConfigFactoryImpl.unsupportedContextType", context.getClass().getName()));
    }


    @Override
    public void removeServerAuthModule(Object context) {
        if (context == null) {
            throw new IllegalArgumentException(sm.getString("authConfigFactoryImpl.nullContext"));
        }

        if (context instanceof ServletContext servletContext) {
            String layer = "HttpServlet";
            String appContextID = servletContext.getVirtualServerName() + " " + servletContext.getContextPath();

            removeRegistration(getRegistrationID(layer, appContextID));
            return;
        }

        // Unsupported context type
        throw new IllegalArgumentException(
                sm.getString("authConfigFactoryImpl.unsupportedContextType", context.getClass().getName()));
    }


    private static String getRegistrationID(String layer, String appContext) {
        if (layer != null && layer.isEmpty()) {
            throw new IllegalArgumentException(sm.getString("authConfigFactoryImpl.zeroLengthMessageLayer"));
        }
        if (appContext != null && appContext.isEmpty()) {
            throw new IllegalArgumentException(sm.getString("authConfigFactoryImpl.zeroLengthAppContext"));
        }
        return (layer == null ? "" : layer) + ":" + (appContext == null ? "" : appContext);
    }


    private void loadPersistentRegistrations() {
        // To avoid deadlock, always obtain registrationLock then CONFIG_FILE_LOCK
        Set<RegistrationListenerWrapper> wrappersToNotify = new HashSet<>();
        registrationLock.lock();
        try {
            synchronized (CONFIG_FILE_LOCK) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("authConfigFactoryImpl.load", CONFIG_FILE.getAbsolutePath()));
                }
                if (!CONFIG_FILE.isFile()) {
                    return;
                }
                Providers providers = PersistentProviderRegistrations.loadProviders(CONFIG_FILE);
                for (Provider provider : providers.getProviders()) {
                    doRegisterConfigProvider(provider.getClassName(), provider.getProperties(), provider.getLayer(),
                            provider.getAppContext(), provider.getDescription(), wrappersToNotify);
                }
            }
        } finally {
            registrationLock.unlock();
            notifyWrappers(wrappersToNotify);
        }
    }


    private void savePersistentRegistrations() {
        // To avoid deadlock, always obtain registrationLock then CONFIG_FILE_LOCK
        registrationLock.lock();
        try {
            synchronized (CONFIG_FILE_LOCK) {
                Providers providers = new Providers();
                for (Entry<String,RegistrationContextImpl> entry : registrationToRegistrationContextMap.entrySet()) {
                    RegistrationContextImpl registrationContextImpl = entry.getValue();
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
                PersistentProviderRegistrations.writeProviders(providers, CONFIG_FILE);
            }
        } finally {
            registrationLock.unlock();
        }
    }


    private static class RegistrationContextImpl implements RegistrationContext {

        private RegistrationContextImpl(String messageLayer, String appContext, String description, boolean persistent,
                AuthConfigProvider provider, Map<String,String> properties) {
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


        private Map<String,String> getProperties() {
            return properties;
        }
    }


    private record RegistrationListenerWrapper(String messageLayer, String appContext, RegistrationListener listener) {
    }


    private record SingleModuleServerAuthContext(ServerAuthModule module) implements ServerAuthContext {
        @Override
        public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject, Subject serviceSubject)
                throws AuthException {
            return module.validateRequest(messageInfo, clientSubject, serviceSubject);
        }

        @Override
        public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject) throws AuthException {
            return module.secureResponse(messageInfo, serviceSubject);
        }

        @Override
        public void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException {
            module.cleanSubject(messageInfo, subject);
        }
    }


    private static class SingleContextServerAuthConfig implements ServerAuthConfig {

        private final ServerAuthModule serverAuthModule;
        private final String appContext;
        private final CallbackHandler handler;
        private final Object serverAuthContextLock = new Object();
        private volatile ServerAuthContext serverAuthContext;

        SingleContextServerAuthConfig(ServerAuthModule serverAuthModule, String appContext, CallbackHandler handler) {
            this.serverAuthModule = serverAuthModule;
            this.appContext = appContext;
            this.handler = handler;
        }

        @Override
        public String getMessageLayer() {
            return SERVLET_LAYER_ID;
        }

        @Override
        public String getAppContext() {
            return appContext;
        }

        @Override
        public String getAuthContextID(MessageInfo messageInfo) {
            return messageInfo.toString();
        }

        @Override
        public void refresh() {
            // NO-OP
        }

        @Override
        public boolean isProtected() {
            return false;
        }

        @Override
        public ServerAuthContext getAuthContext(String authContextID, Subject serviceSubject,
                Map<String,Object> properties) throws AuthException {
            /*
             * Lazy initialization since we need to pass in the properties which aren't available until this point.
             */
            if (serverAuthContext == null) {
                synchronized (serverAuthContextLock) {
                    if (serverAuthContext == null) {
                        serverAuthContext = new SingleModuleServerAuthContext(serverAuthModule);
                        serverAuthModule.initialize(null, null, handler, properties);
                    }
                }
            }
            return serverAuthContext;
        }
    }


    private static class SingleConfigAuthConfigProvider implements AuthConfigProvider {

        private final ServerAuthModule serverAuthModule;
        private final String appContext;
        private final Object serverAuthConfigLock = new Object();
        private volatile ServerAuthConfig serverAuthConfig;

        SingleConfigAuthConfigProvider(ServerAuthModule serverAuthModule, String appContext) {
            this.serverAuthModule = serverAuthModule;
            this.appContext = appContext;
        }

        @Override
        public ClientAuthConfig getClientAuthConfig(String layer, String appContext, CallbackHandler handler)
                throws AuthException {
            // Should never be called
            throw new UnsupportedOperationException();
        }

        @Override
        public ServerAuthConfig getServerAuthConfig(String layer, String appContext, CallbackHandler handler)
                throws AuthException {
            /*
             * Lazy initialization since we need to pass in the CallbackHandler which isn't available until this point.
             */
            if (serverAuthConfig == null) {
                synchronized (serverAuthConfigLock) {
                    if (serverAuthConfig == null) {
                        serverAuthConfig =
                                new SingleContextServerAuthConfig(serverAuthModule, this.appContext, handler);
                    }
                }
            }
            return serverAuthConfig;
        }

        @Override
        public void refresh() {
            // NO-OP
        }
    }
}

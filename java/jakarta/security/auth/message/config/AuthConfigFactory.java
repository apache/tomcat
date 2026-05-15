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

import java.security.Security;
import java.util.Map;

import jakarta.security.auth.message.module.ServerAuthModule;

/**
 * Factory for obtaining and managing {@link AuthConfigProvider} instances. The AuthConfigFactory is responsible for
 * discovering, registering, and providing configuration providers for JASPIC authentication modules. Use
 * {@link #getFactory()} to obtain the singleton instance.
 */
public abstract class AuthConfigFactory {

    /**
     * The name of the {@link java.security.Security} property used to specify the AuthConfigFactory implementation
     * class.
     */
    public static final String DEFAULT_FACTORY_SECURITY_PROPERTY = "authconfigprovider.factory";

    private static final String DEFAULT_JASPI_AUTHCONFIGFACTORYIMPL =
            "org.apache.catalina.authenticator.jaspic.AuthConfigFactoryImpl";

    private static volatile AuthConfigFactory factory;

    /**
     * Protected constructor for subclasses.
     */
    public AuthConfigFactory() {
    }

    /**
     * Returns the singleton AuthConfigFactory instance. If no instance has been set via {@link #setFactory}, the
     * factory class is discovered from the {@link java.security.Security} property
     * {@value #DEFAULT_FACTORY_SECURITY_PROPERTY}, or falls back to a default implementation.
     *
     * @return the AuthConfigFactory instance
     */
    public static AuthConfigFactory getFactory() {
        if (factory != null) {
            return factory;
        }

        synchronized (AuthConfigFactory.class) {
            if (factory == null) {
                final String className = getFactoryClassName();
                try {
                    // Load this class with the same class loader as used for
                    // this class. Note that the Thread context class loader
                    // should not be used since that would trigger a memory leak
                    // in container environments.
                    if (className.equals("org.apache.catalina.authenticator.jaspic.AuthConfigFactoryImpl")) {
                        factory = new org.apache.catalina.authenticator.jaspic.AuthConfigFactoryImpl();
                    } else {
                        Class<?> clazz = Class.forName(className);
                        factory = (AuthConfigFactory) clazz.getConstructor().newInstance();
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("AuthConfigFactory error:" + e.getCause().getMessage(), e.getCause());
                }
            }
        }

        return factory;
    }

    /**
     * Programmatically sets the AuthConfigFactory instance. This method is typically used by containers to provide
     * their own implementation.
     *
     * @param factory the AuthConfigFactory instance to set
     */
    public static synchronized void setFactory(AuthConfigFactory factory) {
        AuthConfigFactory.factory = factory;
    }

    /**
     * Returns the {@link AuthConfigProvider} for the specified message layer and application context.
     *
     * @param layer      the message layer
     * @param appContext the application context
     * @param listener   the registration listener to attach
     *
     * @return the AuthConfigProvider, or {@code null} if none is available
     */
    public abstract AuthConfigProvider getConfigProvider(String layer, String appContext,
            RegistrationListener listener);

    /**
     * Registers an AuthConfigProvider by class name with the given properties.
     *
     * @param className  the fully qualified class name of the AuthConfigProvider
     * @param properties the configuration properties
     * @param layer      the message layer
     * @param appContext the application context
     * @param description a description of the registration
     *
     * @return a unique registration ID
     */
    public abstract String registerConfigProvider(String className, Map<String,String> properties, String layer,
            String appContext, String description);

    /**
     * Registers an AuthConfigProvider instance.
     *
     * @param provider   the AuthConfigProvider to register
     * @param layer      the message layer
     * @param appContext the application context
     * @param description a description of the registration
     *
     * @return a unique registration ID
     */
    public abstract String registerConfigProvider(AuthConfigProvider provider, String layer, String appContext,
            String description);

    /**
     * Removes a registration by its ID.
     *
     * @param registrationID the registration ID to remove
     *
     * @return {@code true} if the registration was removed, {@code false} if it did not exist
     */
    public abstract boolean removeRegistration(String registrationID);

    /**
     * Detaches a listener from all registrations matching the given layer and application context, returning the
     * affected registration IDs.
     *
     * @param listener   the listener to detach
     * @param layer      the message layer
     * @param appContext the application context
     *
     * @return an array of registration IDs from which the listener was detached
     */
    public abstract String[] detachListener(RegistrationListener listener, String layer, String appContext);

    /**
     * Returns the registration IDs associated with the given AuthConfigProvider.
     *
     * @param provider the AuthConfigProvider
     *
     * @return an array of registration IDs
     */
    public abstract String[] getRegistrationIDs(AuthConfigProvider provider);

    /**
     * Returns the {@link RegistrationContext} for the given registration ID.
     *
     * @param registrationID the registration ID
     *
     * @return the RegistrationContext, or {@code null} if the ID is not found
     */
    public abstract RegistrationContext getRegistrationContext(String registrationID);

    /**
     * Refreshes all registered AuthConfigProviders, causing them to reload their configuration.
     */
    public abstract void refresh();

    /**
     * Convenience method for registering a {@link ServerAuthModule} that should have the same effect as calling
     * {@link #registerConfigProvider(AuthConfigProvider, String, String, String)} with the implementation providing the
     * appropriate {@link AuthConfigProvider} generated from the provided context.
     *
     * @param serverAuthModule The {@link ServerAuthModule} to register
     * @param context          The associated application context
     *
     * @return A string identifier for the created registration
     *
     * @since Authentication 3.0
     */
    public abstract String registerServerAuthModule(ServerAuthModule serverAuthModule, Object context);

    /**
     * Convenience method for deregistering a {@link ServerAuthModule} that should have the same effect as calling
     * {@link AuthConfigFactory#removeRegistration(String)}.
     *
     * @param context The associated application context
     *
     * @since Authentication 3.0
     */
    public abstract void removeServerAuthModule(Object context);

    private static String getFactoryClassName() {
        String className = Security.getProperty(DEFAULT_FACTORY_SECURITY_PROPERTY);

        if (className != null) {
            return className;
        }

        return DEFAULT_JASPI_AUTHCONFIGFACTORYIMPL;
    }

    /**
     * Provides information about a registered {@link AuthConfigProvider}.
     */
    public interface RegistrationContext {

        /**
         * Returns the message layer for this registration.
         *
         * @return the message layer
         */
        String getMessageLayer();

        /**
         * Returns the application context for this registration.
         *
         * @return the application context
         */
        String getAppContext();

        /**
         * Returns the description of this registration.
         *
         * @return the description
         */
        String getDescription();

        /**
         * Indicates whether this registration persists beyond the current container lifecycle.
         *
         * @return {@code true} if the registration is persistent, {@code false} otherwise
         */
        boolean isPersistent();
    }
}

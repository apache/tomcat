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
package jakarta.security.auth.message.config;

import java.security.Security;
import java.util.Map;

import jakarta.security.auth.message.module.ServerAuthModule;

public abstract class AuthConfigFactory {

    public static final String DEFAULT_FACTORY_SECURITY_PROPERTY = "authconfigprovider.factory";

    private static final String DEFAULT_JASPI_AUTHCONFIGFACTORYIMPL =
            "org.apache.catalina.authenticator.jaspic.AuthConfigFactoryImpl";

    private static volatile AuthConfigFactory factory;

    public AuthConfigFactory() {
    }

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

    public static synchronized void setFactory(AuthConfigFactory factory) {
        AuthConfigFactory.factory = factory;
    }

    public abstract AuthConfigProvider getConfigProvider(String layer, String appContext,
            RegistrationListener listener);

    public abstract String registerConfigProvider(String className, Map<String,String> properties, String layer,
            String appContext, String description);

    public abstract String registerConfigProvider(AuthConfigProvider provider, String layer, String appContext,
            String description);

    public abstract boolean removeRegistration(String registrationID);

    public abstract String[] detachListener(RegistrationListener listener, String layer, String appContext);

    public abstract String[] getRegistrationIDs(AuthConfigProvider provider);

    public abstract RegistrationContext getRegistrationContext(String registrationID);

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

    public interface RegistrationContext {

        String getMessageLayer();

        String getAppContext();

        String getDescription();

        boolean isPersistent();
    }
}

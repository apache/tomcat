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
package javax.security.auth.message.config;

import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.Security;
import java.security.SecurityPermission;
import java.util.Map;

public abstract class AuthConfigFactory {

    public static final String DEFAULT_FACTORY_SECURITY_PROPERTY =
            "authconfigprovider.factory";
    public static final String GET_FACTORY_PERMISSION_NAME =
            "getProperty.authconfigprovider.factory";
    public static final String SET_FACTORY_PERMISSION_NAME =
            "setProperty.authconfigprovider.factory";
    public static final String PROVIDER_REGISTRATION_PERMISSION_NAME =
            "setProperty.authconfigfactory.provider";

    public static final SecurityPermission getFactorySecurityPermission =
            new SecurityPermission(GET_FACTORY_PERMISSION_NAME);

    public static final SecurityPermission setFactorySecurityPermission =
            new SecurityPermission(SET_FACTORY_PERMISSION_NAME);

    public static final SecurityPermission providerRegistrationSecurityPermission =
            new SecurityPermission(PROVIDER_REGISTRATION_PERMISSION_NAME);

    private static final String DEFAULT_JASPI_AUTHCONFIGFACTORYIMPL =
            "org.apache.catalina.authenticator.jaspic.AuthConfigFactoryImpl";

    private static volatile AuthConfigFactory factory;

    public AuthConfigFactory() {
    }

    public static AuthConfigFactory getFactory() {
        checkPermission(getFactorySecurityPermission);
        if (factory != null) {
            return factory;
        }

        synchronized (AuthConfigFactory.class) {
            if (factory == null) {
                final String className = getFactoryClassName();
                try {
                    factory = AccessController.doPrivileged(
                            new PrivilegedExceptionAction<AuthConfigFactory>() {
                        @Override
                        public AuthConfigFactory run() throws ReflectiveOperationException,
                                IllegalArgumentException, SecurityException {
                            // Load this class with the same class loader as used for
                            // this class. Note that the Thread context class loader
                            // should not be used since that would trigger a memory leak
                            // in container environments.
                            Class<?> clazz = Class.forName(className);
                            return (AuthConfigFactory) clazz.getConstructor().newInstance();
                        }
                    });
                } catch (PrivilegedActionException e) {
                    Exception inner = e.getException();
                    if (inner instanceof InstantiationException) {
                        throw new SecurityException("AuthConfigFactory error:" +
                                inner.getCause().getMessage(), inner.getCause());
                    } else {
                        throw new SecurityException("AuthConfigFactory error: " + inner, inner);
                    }
                }
            }
        }

        return factory;
    }

    public static synchronized void setFactory(AuthConfigFactory factory) {
        checkPermission(setFactorySecurityPermission);
        AuthConfigFactory.factory = factory;
    }

    public abstract AuthConfigProvider getConfigProvider(String layer, String appContext,
            RegistrationListener listener);

    @SuppressWarnings("rawtypes") // JASPIC API uses raw types
    public abstract String registerConfigProvider(String className, Map properties, String layer,
            String appContext, String description);

    public abstract String registerConfigProvider(AuthConfigProvider provider, String layer,
            String appContext, String description);

    public abstract boolean removeRegistration(String registrationID);

    public abstract String[] detachListener(RegistrationListener listener, String layer,
            String appContext);

    public abstract String[] getRegistrationIDs(AuthConfigProvider provider);

    public abstract RegistrationContext getRegistrationContext(String registrationID);

    public abstract void refresh();

    private static void checkPermission(Permission permission) {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager != null) {
            securityManager.checkPermission(permission);
        }
    }

    private static String getFactoryClassName() {
        String className = AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return Security.getProperty(DEFAULT_FACTORY_SECURITY_PROPERTY);
            }
        });

        if (className != null) {
            return className;
        }

        return DEFAULT_JASPI_AUTHCONFIGFACTORYIMPL;
    }

    public static interface RegistrationContext {

        String getMessageLayer();

        String getAppContext();

        String getDescription();

        boolean isPersistent();
    }
}

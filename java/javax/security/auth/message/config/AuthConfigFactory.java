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

import java.security.PrivilegedActionException;
import java.util.Map;

import javax.security.auth.AuthPermission;

/**
 * @version $Rev$ $Date$
 */
public abstract class AuthConfigFactory {

    public static final String DEFAULT_FACTORY_SECURITY_PROPERTY = "authconfigprovider.factory";
    private static final String DEFAULT_JASPI_AUTHCONFIGFACTORYIMPL = "org.apache.geronimo.components.jaspi.AuthConfigFactoryImpl";

    private static AuthConfigFactory factory;
    private static ClassLoader contextClassLoader;

    static {
        contextClassLoader = (ClassLoader) java.security.AccessController
                .doPrivileged(new java.security.PrivilegedAction() {
                    @Override
                    public Object run() {
                        return Thread.currentThread().getContextClassLoader();
                    }
                });
    }

    public static AuthConfigFactory getFactory() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new AuthPermission("getAuthConfigFactory"));
        }
        if (factory == null) {
            String className = (String) java.security.AccessController
                    .doPrivileged(new java.security.PrivilegedAction() {
                        @Override
                        public Object run() {
                            return java.security.Security.getProperty(DEFAULT_FACTORY_SECURITY_PROPERTY);
                        }
                    });
            if (className == null) {
                className = DEFAULT_JASPI_AUTHCONFIGFACTORYIMPL;
            }
            try {
                final String finalClassName = className;
                factory = (AuthConfigFactory) java.security.AccessController
                        .doPrivileged(new java.security.PrivilegedExceptionAction() {
                            @Override
                            public Object run() throws ClassNotFoundException, InstantiationException,
                                    IllegalAccessException {
                                // TODO Review this
                                Class clazz = Class.forName(finalClassName, true, contextClassLoader);
                                return clazz.newInstance();
                            }
                        });
            } catch (PrivilegedActionException e) {
                Exception inner = e.getException();
                if (inner instanceof InstantiationException) {
                    throw (SecurityException) new SecurityException("AuthConfigFactory error:"
                            + inner.getCause().getMessage()).initCause(inner.getCause());
                } else {
                    throw (SecurityException) new SecurityException("AuthConfigFactory error: " + inner).initCause(inner);
                }
            }
        }
        return factory;
    }

    public static void setFactory(AuthConfigFactory factory) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new AuthPermission("setAuthConfigFactory"));
        }
        AuthConfigFactory.factory = factory;
    }


    public AuthConfigFactory() {
    }

    public abstract String[] detachListener(RegistrationListener listener, String layer, String appContext);

    public abstract AuthConfigProvider getConfigProvider(String layer, String appContext, RegistrationListener listener);

    public abstract RegistrationContext getRegistrationContext(String registrationID);

    public abstract String[] getRegistrationIDs(AuthConfigProvider provider);

    public abstract void refresh();

    public abstract String registerConfigProvider(AuthConfigProvider provider, String layer, String appContext, String description);

    public abstract String registerConfigProvider(String className, Map properties, String layer, String appContext, String description);

    public abstract boolean removeRegistration(String registrationID);

    public static interface RegistrationContext {

        String getAppContext();

        String getDescription();

        String getMessageLayer();

        boolean isPersistent();

    }

}

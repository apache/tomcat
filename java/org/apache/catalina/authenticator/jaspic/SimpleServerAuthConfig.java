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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.MessageInfo;
import jakarta.security.auth.message.config.ServerAuthConfig;
import jakarta.security.auth.message.config.ServerAuthContext;
import jakarta.security.auth.message.module.ServerAuthModule;

import org.apache.tomcat.util.res.StringManager;

/**
 * Basic implementation primarily intended for use when using third-party
 * {@link ServerAuthModule} implementations that only provide the module. This
 * implementation supports configuring the {@link ServerAuthContext} with
 * multiple modules.
 */
public class SimpleServerAuthConfig implements ServerAuthConfig {

    private static StringManager sm = StringManager.getManager(SimpleServerAuthConfig.class);

    private static final String SERVER_AUTH_MODULE_KEY_PREFIX =
            "org.apache.catalina.authenticator.jaspic.ServerAuthModule.";

    private final String layer;
    private final String appContext;
    private final CallbackHandler handler;
    private final Map<String,String> properties;

    private volatile ServerAuthContext serverAuthContext;

    public SimpleServerAuthConfig(String layer, String appContext, CallbackHandler handler,
            Map<String,String> properties) {
        this.layer = layer;
        this.appContext = appContext;
        this.handler = handler;
        this.properties = properties;
    }


    @Override
    public String getMessageLayer() {
        return layer;
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
        serverAuthContext = null;
    }


    @Override
    public boolean isProtected() {
        return false;
    }


    @SuppressWarnings({"rawtypes", "unchecked"}) // JASPIC API uses raw types
    @Override
    public ServerAuthContext getAuthContext(String authContextID, Subject serviceSubject,
            Map properties) throws AuthException {
        ServerAuthContext serverAuthContext = this.serverAuthContext;
        if (serverAuthContext == null) {
            synchronized (this) {
                if (this.serverAuthContext == null) {
                    Map<String,String> mergedProperties = new HashMap<>();
                    if (this.properties != null) {
                        mergedProperties.putAll(this.properties);
                    }
                    if (properties != null) {
                        mergedProperties.putAll(properties);
                    }

                    List<ServerAuthModule> modules = new ArrayList<>();
                    int moduleIndex = 1;
                    String key = SERVER_AUTH_MODULE_KEY_PREFIX + moduleIndex;
                    String moduleClassName = mergedProperties.get(key);
                    while (moduleClassName != null) {
                        try {
                            Class<?> clazz = Class.forName(moduleClassName);
                            ServerAuthModule module =
                                    (ServerAuthModule) clazz.getConstructor().newInstance();
                            module.initialize(null, null, handler, mergedProperties);
                            modules.add(module);
                        } catch (ReflectiveOperationException | IllegalArgumentException |
                                SecurityException e) {
                            AuthException ae = new AuthException();
                            ae.initCause(e);
                            throw ae;
                        }

                        // Look for the next module
                        moduleIndex++;
                        key = SERVER_AUTH_MODULE_KEY_PREFIX + moduleIndex;
                        moduleClassName = mergedProperties.get(key);
                    }

                    if (modules.size() == 0) {
                        throw new AuthException(sm.getString("simpleServerAuthConfig.noModules"));
                    }

                    this.serverAuthContext = createServerAuthContext(modules);
                }
                serverAuthContext = this.serverAuthContext;
            }
        }

        return serverAuthContext;
    }


    protected ServerAuthContext createServerAuthContext(List<ServerAuthModule> modules) {
        return new SimpleServerAuthContext(modules);
    }
}

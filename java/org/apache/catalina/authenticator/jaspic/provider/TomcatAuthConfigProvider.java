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
package org.apache.catalina.authenticator.jaspic.provider;

import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.config.AuthConfigFactory;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.ClientAuthConfig;
import javax.security.auth.message.config.ServerAuthConfig;

import org.apache.catalina.Context;
import org.apache.catalina.Realm;
import org.apache.tomcat.util.descriptor.web.LoginConfig;

/**
 * Tomcat's context based JASPIC authentication provider. It returns authentication
 * modules depending on context login-config setup.
 */
public class TomcatAuthConfigProvider implements AuthConfigProvider {

    private Map<String, String> providerProperties;
    private ServerAuthConfig serverAuthConfig;
    private Realm realm;
    private LoginConfig loginConfig;


    public TomcatAuthConfigProvider(Context context) {
        this.realm = context.getRealm();
        this.loginConfig = context.getLoginConfig();
    }


    public TomcatAuthConfigProvider(Map<String, String> properties, AuthConfigFactory factory) {
        this.providerProperties = properties;
        if (factory != null) {
            factory.registerConfigProvider(this, null, null, "Auto registration");
        }
    }


    @Override
    public ClientAuthConfig getClientAuthConfig(String layer, String appContext,
            CallbackHandler handler) throws AuthException {
        return null;
    }


    @Override
    public synchronized ServerAuthConfig getServerAuthConfig(String layer, String appContext,
            CallbackHandler handler) throws AuthException {
        if (this.serverAuthConfig == null) {
            this.serverAuthConfig = new TomcatAuthConfig(layer, appContext, handler, realm, loginConfig);
        }
        return this.serverAuthConfig;
    }


    @Override
    public void refresh() {
        serverAuthConfig.refresh();
    }
}

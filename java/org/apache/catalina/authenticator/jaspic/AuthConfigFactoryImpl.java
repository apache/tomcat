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

import java.util.Map;

import javax.security.auth.message.config.AuthConfigFactory;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.RegistrationListener;

public class AuthConfigFactoryImpl extends AuthConfigFactory {

    @Override
    public AuthConfigProvider getConfigProvider(String layer, String appContext,
            RegistrationListener listener) {
        return null;
    }

    @Override
    @SuppressWarnings("rawtypes") // JASPIC API uses raw types
    public String registerConfigProvider(String className, Map properties, String layer,
            String appContext, String description) {
        return null;
    }

    @Override
    public String registerConfigProvider(AuthConfigProvider provider, String layer,
            String appContext, String description) {
        return null;
    }

    @Override
    public boolean removeRegistration(String registrationID) {
        return false;
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
        return null;
    }

    @Override
    public void refresh() {

    }
}

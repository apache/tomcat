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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;

import org.apache.catalina.Realm;
import org.apache.catalina.authenticator.jaspic.provider.modules.BasicAuthModule;
import org.apache.catalina.authenticator.jaspic.provider.modules.TomcatAuthModule;

public class TomcatAuthConfig implements ServerAuthConfig {

    private String messageLayer;
    private String appContext;
    private CallbackHandler handler;
    private TomcatServerAuthContext tomcatServerAuthContext;
    private Realm realm;


    public TomcatAuthConfig(String layer, String appContext, CallbackHandler callbackHandler,
            Realm realm) {
        this.messageLayer = layer;
        this.appContext = appContext;
        this.handler = callbackHandler;
        this.realm = realm;
    }


    @Override
    public String getMessageLayer() {
        return messageLayer;
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

    }


    @Override
    public boolean isProtected() {
        return false;
    }


    @Override
    @SuppressWarnings("rawtypes")
    public synchronized ServerAuthContext getAuthContext(String authContextID,
            Subject serviceSubject, Map properties) throws AuthException {
        if (this.tomcatServerAuthContext == null) {
            this.tomcatServerAuthContext = new TomcatServerAuthContext(handler, getModules());
        }
        return tomcatServerAuthContext;
    }


    private Collection<TomcatAuthModule> getModules() {
        List<TomcatAuthModule> modules = new ArrayList<>();
        modules.add(new BasicAuthModule());
        return modules;
    }
}

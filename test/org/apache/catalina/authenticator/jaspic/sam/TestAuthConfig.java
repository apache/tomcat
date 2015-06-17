/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.catalina.authenticator.jaspic.sam;

import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;
import javax.security.auth.message.module.ServerAuthModule;

public class TestAuthConfig implements ServerAuthConfig {
    private CallbackHandler callbackHandler;
    private ServerAuthModule authModule;

    public TestAuthConfig(CallbackHandler callbackHandler, ServerAuthModule authModule) {
        this.callbackHandler = callbackHandler;
        this.authModule = authModule;
    }

    @Override
    public String getMessageLayer() {
        return "HttpServlet";
    }

    @Override
    public String getAppContext() {
        return null;
    }

    @Override
    public String getAuthContextID(MessageInfo messageInfo) {
        return null;
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
    public ServerAuthContext getAuthContext(String authContextID, Subject serviceSubject,
            Map properties) throws AuthException {
        return new TestServerAuthContext(callbackHandler, authModule);
    }

}
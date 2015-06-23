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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.ServerAuthContext;
import javax.security.auth.message.module.ServerAuthModule;

import org.apache.catalina.authenticator.jaspic.MessageInfoImpl;
import org.apache.catalina.authenticator.jaspic.provider.modules.TomcatAuthModule;
import org.apache.tomcat.util.res.StringManager;

/**
 * This class contains references to different JASPIC modules.
 */
public class TomcatServerAuthContext implements ServerAuthContext {

    protected static final StringManager sm = StringManager.getManager(TomcatServerAuthContext.class);

    private Map<String, ServerAuthModule> serverAuthModules = new HashMap<>();


    public TomcatServerAuthContext(CallbackHandler handler, Collection<TomcatAuthModule> modules)
            throws AuthException {
        for (TomcatAuthModule module : modules) {
            // TODO discuss message policies
            module.initialize(null, null, handler, Collections.emptyMap());
            serverAuthModules.put(getAuthType(module), module);
        }
    }


    private String getAuthType(TomcatAuthModule module) {
        // TODO temporary workaround. In future JASPIC prefix will be removed
        return "JASPIC-" + module.getAuthenticationType();
    }


    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException {
        ServerAuthModule module = getAuthModule(messageInfo);
        return module.validateRequest(messageInfo, clientSubject, serviceSubject);
    }


    @Override
    public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject)
            throws AuthException {
        ServerAuthModule module = getAuthModule(messageInfo);
        return module.secureResponse(messageInfo, serviceSubject);
    }


    @Override
    public void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException {
        ServerAuthModule module = getAuthModule(messageInfo);
        module.cleanSubject(messageInfo, subject);
    }


    @SuppressWarnings("rawtypes")
    private ServerAuthModule getAuthModule(MessageInfo messageInfo) throws AuthException {
        Map properties = messageInfo.getMap();
        String authenticationType = (String) properties.get(MessageInfoImpl.AUTH_METHOD);
        ServerAuthModule module = serverAuthModules.get(authenticationType);
        if (module == null) {
            throw new AuthException(sm.getString("authenticator.jaspic.unknownAuthType",
                    authenticationType));
        }
        return module;
    }
}

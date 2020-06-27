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

import java.util.List;

import javax.security.auth.Subject;

import jakarta.security.auth.message.AuthException;
import jakarta.security.auth.message.AuthStatus;
import jakarta.security.auth.message.MessageInfo;
import jakarta.security.auth.message.config.ServerAuthContext;
import jakarta.security.auth.message.module.ServerAuthModule;

/**
 * Basic implementation primarily intended for use when using third-party
 * {@link ServerAuthModule} implementations that only provide the module. This
 * implementation supports multiple modules and will treat the user as
 * authenticated if any one module is able to authenticate the user.
 */
public class SimpleServerAuthContext implements ServerAuthContext {

    private final List<ServerAuthModule> modules;


    public SimpleServerAuthContext(List<ServerAuthModule> modules) {
        this.modules = modules;
    }


    @SuppressWarnings("unchecked") // JASPIC API uses raw types
    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException {
        for (int moduleIndex = 0; moduleIndex < modules.size(); moduleIndex++) {
            ServerAuthModule module = modules.get(moduleIndex);
            AuthStatus result = module.validateRequest(messageInfo, clientSubject, serviceSubject);
            if (result != AuthStatus.SEND_FAILURE) {
                messageInfo.getMap().put("moduleIndex", Integer.valueOf(moduleIndex));
                return result;
            }
        }
        return AuthStatus.SEND_FAILURE;
    }


    @Override
    public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject)
            throws AuthException {
        ServerAuthModule module = modules.get(((Integer) messageInfo.getMap().get("moduleIndex")).intValue());
        return module.secureResponse(messageInfo, serviceSubject);
    }


    @Override
    public void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException {
        for (ServerAuthModule module : modules) {
            module.cleanSubject(messageInfo, subject);
        }
    }
}

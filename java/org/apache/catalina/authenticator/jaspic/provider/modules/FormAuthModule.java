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
package org.apache.catalina.authenticator.jaspic.provider.modules;

import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.MessagePolicy;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * This class implements JASPIC FORM-based authentication.
 */
public class FormAuthModule extends TomcatAuthModule {
    private static final Log log = LogFactory.getLog(FormAuthModule.class);

    private Class<?>[] supportedMessageTypes = new Class[] { HttpServletRequest.class,
            HttpServletResponse.class };

    private String landingPage;


    public FormAuthModule() {
    }


    public FormAuthModule(String landingPage) {
        this.landingPage = landingPage;
    }


    @SuppressWarnings("rawtypes")
    @Override
    public void initializeModule(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
            CallbackHandler handler, Map options) throws AuthException {
    }


    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException {
        if (!isMandatory(messageInfo)) {
            return AuthStatus.SUCCESS;
        }
        return AuthStatus.FAILURE;

    }


    @Override
    public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject)
            throws AuthException {
        return null;
    }


    @Override
    public void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException {
    }


    @Override
    public Class<?>[] getSupportedMessageTypes() {
        return supportedMessageTypes;
    }
}

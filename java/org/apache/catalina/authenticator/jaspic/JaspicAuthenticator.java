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

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.config.AuthConfigFactory;
import javax.security.auth.message.config.AuthConfigProvider;
import javax.security.auth.message.config.ServerAuthConfig;
import javax.security.auth.message.config.ServerAuthContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.authenticator.AuthenticatorBase;
import org.apache.catalina.connector.Request;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class JaspicAuthenticator extends AuthenticatorBase {

    private static final Log log = LogFactory.getLog(JaspicAuthenticator.class);

    private static final String AUTH_TYPE = "JASPIC";
    private static final String MESSAGE_LAYER = "HttpServlet";

    private JaspicCallbackHandler callbackHandler;
    private Subject serviceSubject;

    @SuppressWarnings("rawtypes")
    private Map authProperties = null;


    @Override
    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();
        callbackHandler = new JaspicCallbackHandler(container.getRealm());
        serviceSubject = new Subject();
    }


    @Override
    public boolean authenticate(Request request, HttpServletResponse response) throws IOException {
        MessageInfo messageInfo = new MessageInfoImpl(request, response, true);
        AuthConfigFactory factory = AuthConfigFactory.getFactory();
        String appContext = request.getLocalName() + " " + request.getContextPath();

        AuthConfigProvider configProvider =
                factory.getConfigProvider(MESSAGE_LAYER, appContext, null);
        ServerAuthConfig authConfig = getAuthConfig(appContext, configProvider);
        String authContextId = authConfig.getAuthContextID(messageInfo);

        ServerAuthContext authContext = null;
        authContext = getAuthContext(authConfig, authContextId, authProperties, authContext);
        AuthStatus authStatus = validateRequest(messageInfo, authContext);

        if (authStatus == AuthStatus.SUCCESS) {
            Principal principal = callbackHandler.getPrincipal();
            if (principal != null) {
                register(request, response, principal, AUTH_TYPE, null, null);
            }
            return true;
        }

        return false;
    }


    private AuthStatus validateRequest(MessageInfo messageInfo, ServerAuthContext authContext) {
        Subject clientSubject = new Subject();
        try {
            return authContext.validateRequest(messageInfo, clientSubject, serviceSubject);
        } catch (AuthException e) {
            throw new IllegalStateException(e);
        }
    }


    @SuppressWarnings("rawtypes")
    private ServerAuthContext getAuthContext(ServerAuthConfig authConfig, String authContextId,
            Map authProperties, ServerAuthContext authContext) {
        try {
            return authConfig.getAuthContext(authContextId, serviceSubject, authProperties);
        } catch (AuthException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public void login(String userName, String password, Request request) throws ServletException {
        throw new IllegalStateException("not implemented yet!");
    }


    @Override
    public void logout(Request request) {
        throw new IllegalStateException("not implemented yet!");
    }


    private ServerAuthConfig getAuthConfig(String appContext, AuthConfigProvider configProvider) {
        try {
            return configProvider.getServerAuthConfig(MESSAGE_LAYER, appContext, callbackHandler);
        } catch (AuthException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    protected String getAuthMethod() {
        return AUTH_TYPE;
    }
}

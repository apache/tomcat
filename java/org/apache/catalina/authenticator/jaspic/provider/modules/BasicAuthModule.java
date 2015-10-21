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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.MessagePolicy;
import javax.security.auth.message.callback.PasswordValidationCallback;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.BasicAuthenticator.BasicCredentials;
import org.apache.catalina.connector.Request;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;

/**
 * This class implements JASPIC based HTTP BASIC authentication.
 */
public class BasicAuthModule extends TomcatAuthModule {

    private Class<?>[] supportedMessageTypes = new Class[] { HttpServletRequest.class,
            HttpServletResponse.class };


    public BasicAuthModule(Context context) {
        super(context);
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

        Request request = (Request) messageInfo.getRequestMessage();
        HttpServletResponse response = (HttpServletResponse) messageInfo.getResponseMessage();
        MessageBytes authorization = request.getCoyoteRequest().getMimeHeaders()
                .getValue(AUTHORIZATION_HEADER);

        String realmName = getRealmName();

        if (authorization == null) {
            return sendUnauthorizedError(response, realmName);
        }

        authorization.toBytes();
        ByteChunk authorizationBC = authorization.getByteChunk();
        BasicCredentials credentials = null;
        try {
            credentials = new BasicCredentials(authorizationBC);
            String username = credentials.getUsername();
            char[] password = credentials.getPassword().toCharArray();

            PasswordValidationCallback passwordCallback = new PasswordValidationCallback(
                    clientSubject, username, password);
            handler.handle(new Callback[] { passwordCallback });

            if (!passwordCallback.getResult()) {
                return sendUnauthorizedError(response, realmName);
            }
            handlePrincipalCallbacks(clientSubject, getPrincipal(passwordCallback));
            return AuthStatus.SUCCESS;
        } catch (Exception e) {
            throw new AuthException(e.getMessage());
        }
    }


    private AuthStatus sendUnauthorizedError(HttpServletResponse response, String realmName)
            throws AuthException {
        String authHeader = MessageFormat.format("Basic realm=\"{0}\"", realmName);
        response.setHeader(AUTH_HEADER_NAME, authHeader);
        try {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } catch (IOException e) {
            throw new AuthException(e.getMessage());
        }
        return AuthStatus.SEND_CONTINUE;
    }


    private GenericPrincipal getPrincipal(PasswordValidationCallback passwordCallback) {
        Iterator<Object> credentials = passwordCallback.getSubject().getPrivateCredentials()
                .iterator();
        return (GenericPrincipal) credentials.next();
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

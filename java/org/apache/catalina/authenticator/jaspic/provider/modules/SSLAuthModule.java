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

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.MessagePolicy;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.connector.Request;
import org.apache.coyote.ActionCode;

/**
 * This class implements JASPIC based HTTP BASIC authentication.
 */
public class SSLAuthModule extends TomcatAuthModule {

    public SSLAuthModule(Context context) {
        super(context);
    }


    @Override
    public void initializeModule(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
            CallbackHandler handler, Map<String, String> options) throws AuthException {
    }


    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException {
        if (!isMandatory(messageInfo)) {
            return AuthStatus.SUCCESS;
        }

        Request request = (Request) messageInfo.getRequestMessage();
        HttpServletResponse response = (HttpServletResponse) messageInfo.getResponseMessage();
        try {
            X509Certificate certs[] = getRequestCertificates(request);

            if ((certs == null) || (certs.length < 1)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        sm.getString("authenticator.certificates"));
                return AuthStatus.FAILURE;
            }

            // Authenticate the specified certificate chain
            Principal principal = context.getRealm().authenticate(certs);
            if (principal == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        sm.getString("authenticator.unauthorized"));
                return AuthStatus.FAILURE;
            }
            handlePrincipalCallbacks(clientSubject, principal);
            return AuthStatus.SUCCESS;
        } catch (Exception e) {
            throw new AuthException(e.getMessage());
        }

    }


    /**
     * Look for the X509 certificate chain in the Request under the key
     * <code>javax.servlet.request.X509Certificate</code>. If not found, trigger
     * extracting the certificate chain from the Coyote request.
     *
     * @param request   Request to be processed
     *
     * @return          The X509 certificate chain if found, <code>null</code>
     *                  otherwise.
     */
    protected X509Certificate[] getRequestCertificates(final Request request)
            throws IllegalStateException {

        X509Certificate certs[] =
                (X509Certificate[]) request.getAttribute(Globals.CERTIFICATES_ATTR);

        if ((certs == null) || (certs.length < 1)) {
            try {
                request.getCoyoteRequest().action(ActionCode.REQ_SSL_CERTIFICATE, null);
                certs = (X509Certificate[]) request.getAttribute(Globals.CERTIFICATES_ATTR);
            } catch (IllegalStateException ise) {
                // Request body was too large for save buffer
                // Return null which will trigger an auth failure
            }
        }

        return certs;
    }

}

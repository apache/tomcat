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


package org.apache.catalina.authenticator;


import java.io.IOException;
import java.security.Principal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.codec.binary.Base64;



/**
 * An <b>Authenticator</b> and <b>Valve</b> implementation of HTTP BASIC
 * Authentication, as outlined in RFC 2617:  "HTTP Authentication: Basic
 * and Digest Access Authentication."
 *
 * @author Craig R. McClanahan
 */
public class BasicAuthenticator
    extends AuthenticatorBase {
    private static final Log log = LogFactory.getLog(BasicAuthenticator.class);

   // ----------------------------------------------------- Instance Variables


    /**
     * Descriptive information about this implementation.
     */
    protected static final String info =
        "org.apache.catalina.authenticator.BasicAuthenticator/1.0";


    // ------------------------------------------------------------- Properties


    /**
     * Return descriptive information about this Valve implementation.
     */
    @Override
    public String getInfo() {

        return (info);

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Authenticate the user making this request, based on the specified
     * login configuration.  Return <code>true</code> if any specified
     * constraint has been satisfied, or <code>false</code> if we have
     * created a response challenge already.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param config    Login configuration describing how authentication
     *              should be performed
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public boolean authenticate(Request request,
                                HttpServletResponse response,
                                LoginConfig config)
        throws IOException {

        if (checkForCachedAuthentication(request, response, true)) {
            return true;
        }

        // Validate any credentials already included with this request
        String username = null;
        String password = null;

        MessageBytes authorization = 
            request.getCoyoteRequest().getMimeHeaders()
            .getValue("authorization");
        
        if (authorization != null) {
            authorization.toBytes();
            ByteChunk authorizationBC = authorization.getByteChunk();
            if (authorizationBC.startsWithIgnoreCase("basic ", 0)) {
                authorizationBC.setOffset(authorizationBC.getOffset() + 6);
                
                byte[] decoded = Base64.decodeBase64(
                        authorizationBC.getBuffer(),
                        authorizationBC.getOffset(),
                        authorizationBC.getLength());
                
                // Get username and password
                int colon = -1;
                for (int i = 0; i < decoded.length; i++) {
                    if (decoded[i] == ':') {
                        colon = i;
                        break;
                    }
                }

                if (colon < 0) {
                    username = new String(decoded, B2CConverter.ISO_8859_1);
                } else {
                    username = new String(
                            decoded, 0, colon, B2CConverter.ISO_8859_1);
                    password = new String(
                            decoded, colon + 1, decoded.length - colon - 1,
                            B2CConverter.ISO_8859_1);
                }
                
                authorizationBC.setOffset(authorizationBC.getOffset() - 6);
            }

            Principal principal = context.getRealm().authenticate(username, password);
            if (principal != null) {
                register(request, response, principal,
                        HttpServletRequest.BASIC_AUTH, username, password);
                return (true);
            }
        }
        
        StringBuilder value = new StringBuilder(16);
        value.append("Basic realm=\"");
        if (config.getRealmName() == null) {
            value.append(REALM_NAME);
        } else {
            value.append(config.getRealmName());
        }
        value.append('\"');        
        response.setHeader(AUTH_HEADER_NAME, value.toString());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return (false);

    }


    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.BASIC_AUTH;
    }
}

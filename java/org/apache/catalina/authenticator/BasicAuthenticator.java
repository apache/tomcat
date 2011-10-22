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

import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.util.Base64;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;



/**
 * An <b>Authenticator</b> and <b>Valve</b> implementation of HTTP BASIC
 * Authentication, as outlined in RFC 2617:  "HTTP Authentication: Basic
 * and Digest Access Authentication."
 *
 * @author Craig R. McClanahan
 * @version $Id$
 */

public class BasicAuthenticator
    extends AuthenticatorBase {
    private static final Log log = LogFactory.getLog(BasicAuthenticator.class);


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

        // Have we already authenticated someone?
        Principal principal = request.getUserPrincipal();
        String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (principal != null) {
            if (log.isDebugEnabled()) {
                log.debug("Already authenticated '" + principal.getName() + "'");
            }
            // Associate the session with any existing SSO session
            if (ssoId != null) {
                associate(ssoId, request.getSessionInternal(true));
            }
            return (true);
        }

        // Is there an SSO session against which we can try to reauthenticate?
        if (ssoId != null) {
            if (log.isDebugEnabled()) {
                log.debug("SSO Id " + ssoId + " set; attempting " +
                          "reauthentication");
            }
            /* Try to reauthenticate using data cached by SSO.  If this fails,
               either the original SSO logon was of DIGEST or SSL (which
               we can't reauthenticate ourselves because there is no
               cached username and password), or the realm denied
               the user's reauthentication for some reason.
               In either case we have to prompt the user for a logon */
            if (reauthenticateFromSSO(ssoId, request)) {
                return true;
            }
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
                // FIXME: Add trimming
                // authorizationBC.trim();

                CharChunk authorizationCC = authorization.getCharChunk();
                Base64.decode(authorizationBC, authorizationCC);

                // Get username and password
                int colon = authorizationCC.indexOf(':');
                if (colon < 0) {
                    username = authorizationCC.toString();
                } else {
                    char[] buf = authorizationCC.getBuffer();
                    username = new String(buf, 0, colon);
                    password = new String(buf, colon + 1,
                            authorizationCC.getEnd() - colon - 1);
                }

                authorizationBC.setOffset(authorizationBC.getOffset() - 6);
            }

            principal = context.getRealm().authenticate(username, password);
            if (principal != null) {
                register(request, response, principal, Constants.BASIC_METHOD,
                         username, password);
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
        return Constants.BASIC_METHOD;
    }
}

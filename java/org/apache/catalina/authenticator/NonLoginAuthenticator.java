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

import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.LoginConfig;



/**
 * An <b>Authenticator</b> and <b>Valve</b> implementation that checks
 * only security constraints not involving user authentication.
 *
 * @author Craig R. McClanahan
 * @version $Id$
 */
public final class NonLoginAuthenticator extends AuthenticatorBase {


    // --------------------------------------------------------- Public Methods


    /**
     * Authenticate the user making this request, based on the specified
     * login configuration.  Return <code>true</code> if any specified
     * constraint has been satisfied, or <code>false</code> if we have
     * created a response challenge already.
     *
     * @param request Request we are processing
     * @param response Response we are populating
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

        /*  Associating this request's session with an SSO would allow
            coordinated session invalidation, but should the session for
            a webapp that the user didn't log into be invalidated when
            another session is logged out?
        String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (ssoId != null)
            associate(ssoId, getSession(request, true));
        */

        if (containerLog.isDebugEnabled()) {
            containerLog.debug("User authentication is not required");
        }
        return (true);


    }


    @Override
    protected String getAuthMethod() {
        return "NONE";
    }
}

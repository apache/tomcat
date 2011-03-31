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

import java.io.File;
import java.io.IOException;
import java.security.Principal;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.startup.Bootstrap;
import org.apache.catalina.util.Base64;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;


/**
 * A SPNEGO authenticator that uses the SPENGO/Kerberos support built in to Java
 * 6. Successful Kerberos authentication depends on the correct configuration of
 * multiple components. If the configuration is invalid, the error messages are
 * often cryptic although a Google search will usually point you in the right
 * direction.
 * <p>
 * TODO:
 * <ul>
 * <li>Add support for delegating credentials? Need this if we want to
 *     authenticate to a realm as the user. This is likely to result in a fair
 *     amount of internal refactoring.</li>
 * </ul>
 * <p>
 * TBDs:
 * <ul>
 * <li>Does the domain name have to be in upper case?</li>
 * <li>Does the SPN have to start with HTTP/...?</li>
 * <li>Can a port number be appended to the end of the host in the SPN?</li>
 * <li>Can the domain be left off the user in the ktpass command?</li>
 * <li>What are the limitations on the account that Tomcat can run as? SPN
 *     associated account works, domain admin works, local admin doesn't
 *     work</li>
 * </ul>
 */
public class SpnegoAuthenticator extends AuthenticatorBase {

    private static final Log log = LogFactory.getLog(SpnegoAuthenticator.class);
    
    private String loginConfigName = Constants.DEFAULT_LOGIN_MODULE_NAME;
    public String getLoginConfigName() {
        return loginConfigName;
    }
    public void setLoginConfigName(String loginConfigName) {
        this.loginConfigName = loginConfigName;
    }


    @Override
    protected String getAuthMethod() {
        return Constants.SPNEGO_METHOD;
    }


    @Override
    public String getInfo() {
        return "org.apache.catalina.authenticator.SpnegoAuthenticator/1.0";
    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        // Kerberos configuration file location
        String krb5Conf = System.getProperty(Constants.KRB5_CONF_PROPERTY);
        if (krb5Conf == null) {
            // System property not set, use the Tomcat default
            File krb5ConfFile = new File(Bootstrap.getCatalinaBase(),
                    Constants.DEFAULT_KRB5_CONF);
            System.setProperty(Constants.KRB5_CONF_PROPERTY,
                    krb5ConfFile.getAbsolutePath());
        }

        // JAAS configuration file location
        String jaasConf = System.getProperty(Constants.JAAS_CONF_PROPERTY);
        if (jaasConf == null) {
            // System property not set, use the Tomcat default
            File jaasConfFile = new File(Bootstrap.getCatalinaBase(),
                    Constants.DEFAULT_JAAS_CONF);
            System.setProperty(Constants.JAAS_CONF_PROPERTY,
                    jaasConfFile.getAbsolutePath());
        }
        
        // This property must be false for SPNEGO to work
        System.setProperty(Constants.USE_SUBJECT_CREDS_ONLY_PROPERTY, "false");
    }


    @Override
    public boolean authenticate(Request request, HttpServletResponse response,
            LoginConfig config) throws IOException {

        // Have we already authenticated someone?
        Principal principal = request.getUserPrincipal();
        String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (principal != null) {
            if (log.isDebugEnabled())
                log.debug("Already authenticated '" + principal.getName() + "'");
            // Associate the session with any existing SSO session
            if (ssoId != null)
                associate(ssoId, request.getSessionInternal(true));
            return true;
        }

        // Is there an SSO session against which we can try to reauthenticate?
        if (ssoId != null) {
            if (log.isDebugEnabled())
                log.debug("SSO Id " + ssoId + " set; attempting " +
                          "reauthentication");
            /* Try to reauthenticate using data cached by SSO.  If this fails,
               either the original SSO logon was of DIGEST or SSL (which
               we can't reauthenticate ourselves because there is no
               cached username and password), or the realm denied
               the user's reauthentication for some reason.
               In either case we have to prompt the user for a logon */
            if (reauthenticateFromSSO(ssoId, request))
                return true;
        }

        MessageBytes authorization = 
            request.getCoyoteRequest().getMimeHeaders()
            .getValue("authorization");
        
        if (authorization == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("authenticator.noAuthHeader"));
            }
            response.setHeader("WWW-Authenticate", "Negotiate");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        
        authorization.toBytes();
        ByteChunk authorizationBC = authorization.getByteChunk();

        if (!authorizationBC.startsWithIgnoreCase("negotiate ", 0)) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString(
                        "spnegoAuthenticator.authHeaderNotNego"));
            }
            response.setHeader("WWW-Authenticate", "Negotiate");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        authorizationBC.setOffset(authorizationBC.getOffset() + 10);
        // FIXME: Add trimming
        // authorizationBC.trim();
                
        ByteChunk decoded = new ByteChunk();
        Base64.decode(authorizationBC, decoded);

        if (decoded.getLength() == 0) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString(
                        "spnegoAuthenticator.authHeaderNoToken"));
            }
            response.setHeader("WWW-Authenticate", "Negotiate");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        LoginContext lc = null;
        GSSContext gssContext = null;
        byte[] outToken = null;
        try {
            try {
                lc = new LoginContext(loginConfigName);
                lc.login();
            } catch (LoginException e) {
                log.error(sm.getString("spnegoAuthenticator.serviceLoginFail"),
                        e);
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return false;
            }
            // Assume the GSSContext is stateless
            // TODO: Confirm this assumption
            GSSManager manager = GSSManager.getInstance();
            gssContext = manager.createContext(manager.createCredential(null,
                    GSSCredential.DEFAULT_LIFETIME,
                    new Oid("1.3.6.1.5.5.2"),
                    GSSCredential.ACCEPT_ONLY));

            outToken = gssContext.acceptSecContext(decoded.getBytes(),
                    decoded.getOffset(), decoded.getLength());

            if (outToken == null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                            "spnegoAuthenticator.ticketValidateFail"));
                }
                // Start again
                response.setHeader("WWW-Authenticate", "Negotiate");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }

            principal = context.getRealm().authenticate(gssContext);
        } catch (GSSException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("spnegoAuthenticator.ticketValidateFail",
                        e));
            }
            response.setHeader("WWW-Authenticate", "Negotiate");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        } finally {
            if (gssContext != null) {
                try {
                    gssContext.dispose();
                } catch (GSSException e) {
                    // Ignore
                }
            }
            if (lc != null) {
                try {
                    lc.logout();
                } catch (LoginException e) {
                    // Ignore
                }
            }
        }

        // Send response token on success and failure
        response.setHeader("WWW-Authenticate", "Negotiate "
                + Base64.encode(outToken));

        if (principal != null) {
            register(request, response, principal, Constants.SPNEGO_METHOD,
                    principal.getName(), null);
            return true;
        }

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}

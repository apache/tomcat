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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
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
 * <li>Can -Djava.security.krb5.conf be used to change the location of krb5.ini?
 *     </li>
 * <li>What are the limitations on the account that Tomcat can run as? SPN
 *     associated account works, domain admin works, local admin doesn't
 *     work</li>
 * </ul>
 */
public class SpnegoAuthenticator extends AuthenticatorBase {

    private static final Log log = LogFactory.getLog(SpnegoAuthenticator.class);
    
    protected String serviceKeyTab = Constants.DEFAULT_KEYTAB;
    protected String spn = null;

    protected Subject serviceSubject = null;

    
    @Override
    protected String getAuthMethod() {
        return Constants.SPNEGO_METHOD;
    }


    @Override
    public String getInfo() {
        return "org.apache.catalina.authenticator.SpnegoAuthenticator/1.0";
    }


    public String getServiceKeyTab() {
        return serviceKeyTab;
    }


    public void setServiceKeyTab(String serviceKeyTab) {
        this.serviceKeyTab = serviceKeyTab;
    }


    public String getSpn() {
        return spn;
    }


    public void setSpn(String spn) {
        this.spn = spn;
    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        // Service keytab needs to be an absolute file name
        File serviceKeyTabFile = new File(serviceKeyTab);
        if (!serviceKeyTabFile.isAbsolute()) {
            serviceKeyTabFile =
                new File(Bootstrap.getCatalinaBase(), serviceKeyTab);
        }

        // SPN is HTTP/hostname
        String serviceProvideName;
        if (spn == null || spn.length() == 0) {
            // Construct default
            StringBuilder name = new StringBuilder(Constants.DEFAULT_SPN_CLASS);
            name.append('/');
            try {
                name.append(InetAddress.getLocalHost().getCanonicalHostName());
            } catch (UnknownHostException e) {
                throw new LifecycleException(
                        sm.getString("spnegoAuthenticator.hostnameFail"), e);
            }
            serviceProvideName = name.toString();
        } else {
            serviceProvideName = spn;
        }

        LoginContext lc;
        try {
            lc = new LoginContext("", null, null,
                    new JaasConfig(serviceKeyTabFile.getAbsolutePath(),
                            serviceProvideName, log.isDebugEnabled()));
            lc.login();
            serviceSubject = lc.getSubject();
        } catch (LoginException e) {
            throw new LifecycleException(
                    sm.getString("spnegoAuthenticator.serviceLoginFail"), e);
        }
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
        
        if (authorization != null) {
            authorization.toBytes();
            ByteChunk authorizationBC = authorization.getByteChunk();
            if (authorizationBC.startsWithIgnoreCase("negotiate ", 0)) {
                authorizationBC.setOffset(authorizationBC.getOffset() + 10);
                // FIXME: Add trimming
                // authorizationBC.trim();
                
                ByteChunk decoded = new ByteChunk();
                Base64.decode(authorizationBC, decoded);

                try {
                    principal = Subject.doAs(serviceSubject,
                            new KerberosAuthAction(decoded.getBytes(),
                                    response, context));
                } catch (PrivilegedActionException e) {
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString(
                                "spnegoAuthenticator.ticketValidateFail"));
                    }
                }
                
                if (principal != null) {
                    register(request, response, principal, Constants.SPNEGO_METHOD,
                            principal.getName(), null);
                    return true;
                }
            } else {
                response.setHeader("WWW-Authenticate", "Negotiate");
            }
        } else {
            response.setHeader("WWW-Authenticate", "Negotiate");
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }


    private static class KerberosAuthAction
            implements PrivilegedExceptionAction<Principal> {

        private byte[] inToken;
        private HttpServletResponse resp;
        private Context context;

        public KerberosAuthAction(byte[] inToken, HttpServletResponse resp,
                Context context) {
            this.inToken = inToken;
            this.resp = resp;
            this.context = context;
        }

        @Override
        public Principal run() throws Exception {

            // Assume the GSSContext is stateless
            // TODO: Confirm this assumption
            GSSContext gssContext =
                GSSManager.getInstance().createContext((GSSCredential) null);

            Principal principal = null;

            if (inToken == null) {
                throw new IllegalArgumentException("inToken cannot be null");
            }

            byte[] outToken =
                gssContext.acceptSecContext(inToken, 0, inToken.length);

            if (outToken == null) {
                throw new GSSException(GSSException.DEFECTIVE_TOKEN);
            }

            principal = context.getRealm().authenticate(gssContext);

            // Send response token on success and failure
            resp.setHeader("WWW-Authenticate", "Negotiate "
                    + Base64.encode(outToken));

            gssContext.dispose();
            return principal;
        }
    }


    /**
     * Provides the JAAS login configuration required to create
     */
    private static class JaasConfig extends Configuration {

        private String keytab;
        private String spn;
        private boolean debug;

        public JaasConfig(String keytab, String spn, boolean debug) {
            this.keytab = keytab;
            this.spn = spn;
            this.debug = debug;
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            Map<String, String> options = new HashMap<String, String>();
            options.put("useKeyTab", "true");
            options.put("keyTab", keytab);
            options.put("principal", spn);
            options.put("storeKey", "true");
            options.put("doNotPrompt", "true");
            options.put("isInitiator", "false");
            options.put("debug", Boolean.toString(debug));

            return new AppConfigurationEntry[] {
                    new AppConfigurationEntry(
                        "com.sun.security.auth.module.Krb5LoginModule",
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        options) };
        }
    }
}

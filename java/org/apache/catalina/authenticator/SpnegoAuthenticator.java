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
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.regex.Pattern;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.startup.Bootstrap;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.codec.binary.Base64;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;


/**
 * A SPNEGO authenticator that uses the SPNEGO/Kerberos support built in to Java
 * 6. Successful Kerberos authentication depends on the correct configuration of
 * multiple components. If the configuration is invalid, the error messages are
 * often cryptic although a Google search will usually point you in the right
 * direction.
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

    private boolean storeDelegatedCredential = true;
    public boolean isStoreDelegatedCredential() {
        return storeDelegatedCredential;
    }
    public void setStoreDelegatedCredential(
            boolean storeDelegatedCredential) {
        this.storeDelegatedCredential = storeDelegatedCredential;
    }

    private Pattern noKeepAliveUserAgents = null;
    public String getNoKeepAliveUserAgents() {
        Pattern p = noKeepAliveUserAgents;
        if (p == null) {
            return null;
        } else {
            return p.pattern();
        }
    }
    public void setNoKeepAliveUserAgents(String noKeepAliveUserAgents) {
        if (noKeepAliveUserAgents == null ||
                noKeepAliveUserAgents.length() == 0) {
            this.noKeepAliveUserAgents = null;
        } else {
            this.noKeepAliveUserAgents = Pattern.compile(noKeepAliveUserAgents);
        }
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
    }


    @Override
    public boolean authenticate(Request request, HttpServletResponse response,
            LoginConfig config) throws IOException {

        if (checkForCachedAuthentication(request, true)) {
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
                
        byte[] decoded = Base64.decodeBase64(authorizationBC.getBuffer(),
                authorizationBC.getOffset(),
                authorizationBC.getLength());

        if (decoded.length == 0) {
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
        Principal principal = null;
        try {
            try {
                lc = new LoginContext(getLoginConfigName());
                lc.login();
            } catch (LoginException e) {
                log.error(sm.getString("spnegoAuthenticator.serviceLoginFail"),
                        e);
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return false;
            }

            Subject subject = lc.getSubject();

            // Assume the GSSContext is stateless
            // TODO: Confirm this assumption
            final GSSManager manager = GSSManager.getInstance();
            // IBM JDK only understands indefinite lifetime
            final int credentialLifetime;
            if (Globals.IS_IBM_JVM) {
                credentialLifetime = GSSCredential.INDEFINITE_LIFETIME;
            } else {
                credentialLifetime = GSSCredential.DEFAULT_LIFETIME;
            }
            final PrivilegedExceptionAction<GSSCredential> action =
                new PrivilegedExceptionAction<GSSCredential>() {
                    @Override
                    public GSSCredential run() throws GSSException {
                        return manager.createCredential(null,
                                credentialLifetime,
                                new Oid("1.3.6.1.5.5.2"),
                                GSSCredential.ACCEPT_ONLY);
                    }
                };
            gssContext = manager.createContext(Subject.doAs(subject, action));

            outToken = Subject.doAs(lc.getSubject(), new AcceptAction(gssContext, decoded));

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

            principal = Subject.doAs(subject, new AuthenticateAction(
                    context.getRealm(), gssContext, storeDelegatedCredential));

        } catch (GSSException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("spnegoAuthenticator.ticketValidateFail"), e);
            }
            response.setHeader("WWW-Authenticate", "Negotiate");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        } catch (PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof GSSException) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("spnegoAuthenticator.serviceLoginFail"), e);
                }
            } else {
                log.error(sm.getString("spnegoAuthenticator.serviceLoginFail"), e);
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
                + Base64.encodeBase64String(outToken));

        if (principal != null) {
            register(request, response, principal, Constants.SPNEGO_METHOD,
                    principal.getName(), null);

            Pattern p = noKeepAliveUserAgents;
            if (p != null) {
                MessageBytes ua =
                        request.getCoyoteRequest().getMimeHeaders().getValue(
                                "user-agent");
                if (ua != null && p.matcher(ua.toString()).matches()) {
                    response.setHeader("Connection", "close");
                }
            }
            return true;
        }

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }


    /**
     * This class gets a gss credential via a privileged action.
     */
    private static class AcceptAction implements PrivilegedExceptionAction<byte[]> {

        GSSContext gssContext;

        byte[] decoded;

        AcceptAction(GSSContext context, byte[] decodedToken) {
            this.gssContext = context;
            this.decoded = decodedToken;
        }

        @Override
        public byte[] run() throws GSSException {
            return gssContext.acceptSecContext(decoded,
                    0, decoded.length);
        }
    }


    private static class AuthenticateAction implements PrivilegedAction<Principal> {

        private final Realm realm;
        private final GSSContext gssContext;
        private final boolean storeDelegatedCredential;

        public AuthenticateAction(Realm realm, GSSContext gssContext,
                boolean storeDelegatedCredential) {
            this.realm = realm;
            this.gssContext = gssContext;
            this.storeDelegatedCredential = storeDelegatedCredential;
        }

        @Override
        public Principal run() {
            return realm.authenticate(gssContext, storeDelegatedCredential);
        }
    }
}

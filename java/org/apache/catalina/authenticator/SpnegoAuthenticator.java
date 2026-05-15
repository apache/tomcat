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
import java.util.Base64;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.compat.JreVendor;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

/**
 * A SPNEGO authenticator that uses the SPNEGO/Kerberos support built in to Java 6. Successful Kerberos authentication
 * depends on the correct configuration of multiple components. If the configuration is invalid, the error messages are
 * often cryptic although a Google search will usually point you in the right direction.
 */
public class SpnegoAuthenticator extends AuthenticatorBase {

    /**
     * Default constructor.
     */
    public SpnegoAuthenticator() {
    }

    private final Log log = LogFactory.getLog(SpnegoAuthenticator.class); // must not be static
    private static final String AUTH_HEADER_VALUE_NEGOTIATE = "Negotiate";

    private String loginConfigName = Constants.DEFAULT_LOGIN_MODULE_NAME;

    /**
     * Returns the JAAS login configuration name.
     *
     * @return the login configuration name
     */
    public String getLoginConfigName() {
        return loginConfigName;
    }

    /**
     * Sets the JAAS login configuration name.
     *
     * @param loginConfigName the login configuration name
     */
    public void setLoginConfigName(String loginConfigName) {
        this.loginConfigName = loginConfigName;
    }

    private boolean storeDelegatedCredential = true;

    /**
     * Returns whether delegated credentials should be stored in the subject.
     *
     * @return {@code true} if delegated credentials are stored
     */
    public boolean isStoreDelegatedCredential() {
        return storeDelegatedCredential;
    }

    /**
     * Sets whether delegated credentials should be stored in the subject.
     *
     * @param storeDelegatedCredential {@code true} to store delegated credentials
     */
    public void setStoreDelegatedCredential(boolean storeDelegatedCredential) {
        this.storeDelegatedCredential = storeDelegatedCredential;
    }

    private Pattern noKeepAliveUserAgents = null;

    /**
     * Returns the regex pattern for user agents that should not use keep-alive connections.
     *
     * @return the regex pattern, or {@code null} if not configured
     */
    public String getNoKeepAliveUserAgents() {
        Pattern p = noKeepAliveUserAgents;
        if (p == null) {
            return null;
        } else {
            return p.pattern();
        }
    }

    /**
     * Sets the regex pattern for user agents that should not use keep-alive connections.
     *
     * @param noKeepAliveUserAgents the regex pattern, or {@code null} to disable
     */
    public void setNoKeepAliveUserAgents(String noKeepAliveUserAgents) {
        if (noKeepAliveUserAgents == null || noKeepAliveUserAgents.isEmpty()) {
            this.noKeepAliveUserAgents = null;
        } else {
            this.noKeepAliveUserAgents = Pattern.compile(noKeepAliveUserAgents);
        }
    }

    /**
     * This attribute is now hard-coded to {@code false} as the work-around this attribute enabled is no longer
     * required.
     *
     * @return Always {@code false}
     *
     * @deprecated This method will be removed from Tomcat 12 onwards.
     */
    @Deprecated
    public boolean getApplyJava8u40Fix() {
        return false;
    }

    /**
     * This method is now a NO-OP as the work-around this attribute enabled is no longer required.
     *
     * @param applyJava8u40Fix Ignored
     *
     * @deprecated This method will be removed from Tomcat 12 onwards.
     */
    @Deprecated
    public void setApplyJava8u40Fix(boolean applyJava8u40Fix) {
        // NO-OP
    }


    @Override
    protected String getAuthMethod() {
        return Constants.SPNEGO_METHOD;
    }


    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();

        // Kerberos configuration file location
        String krb5Conf = System.getProperty(Constants.KRB5_CONF_PROPERTY);
        if (krb5Conf == null) {
            // System property not set, use the Tomcat default
            File krb5ConfFile = new File(container.getCatalinaBase(), Constants.DEFAULT_KRB5_CONF);
            System.setProperty(Constants.KRB5_CONF_PROPERTY, krb5ConfFile.getAbsolutePath());
        }

        // JAAS configuration file location
        String jaasConf = System.getProperty(Constants.JAAS_CONF_PROPERTY);
        if (jaasConf == null) {
            // System property not set, use the Tomcat default
            File jaasConfFile = new File(container.getCatalinaBase(), Constants.DEFAULT_JAAS_CONF);
            System.setProperty(Constants.JAAS_CONF_PROPERTY, jaasConfFile.getAbsolutePath());
        }
    }


    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response) throws IOException {

        /*
         * Reauthentication using the cached user name and password (if any) is not enabled for SPNEGO authentication.
         * This is because the delegated credentials will nto be available unless a normal SPNEGO authentication takes
         * place.
         *
         * Reauthentication was introduced to handle the case where the Realm took additional actions on authentication.
         * Reauthenticating with the cached user name and password may not be sufficient for SPNEGO since it will not
         * make the delegated credentials available that a web application may depend on. Therefore, the
         * reauthentication behaviour for SPNEGO is to perform a normal SPNEGO authentication.
         */
        if (checkForCachedAuthentication(request, response, false)) {
            return true;
        }

        MessageBytes authorization = request.getCoyoteRequest().getMimeHeaders().getValue("authorization");

        if (authorization == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("authenticator.noAuthHeader"));
            }
            response.setHeader(AUTH_HEADER_NAME, AUTH_HEADER_VALUE_NEGOTIATE);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        authorization.toBytes();
        ByteChunk authorizationBC = authorization.getByteChunk();

        if (!authorizationBC.startsWithIgnoreCase("negotiate ", 0)) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("spnegoAuthenticator.authHeaderNotNego"));
            }
            response.setHeader(AUTH_HEADER_NAME, AUTH_HEADER_VALUE_NEGOTIATE);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        authorizationBC.setStart(authorizationBC.getStart() + 10);

        byte[] encoded = new byte[authorizationBC.getLength()];
        System.arraycopy(authorizationBC.getBuffer(), authorizationBC.getStart(), encoded, 0,
                authorizationBC.getLength());
        byte[] decoded = Base64.getDecoder().decode(encoded);

        if (decoded.length == 0) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("spnegoAuthenticator.authHeaderNoToken"));
            }
            response.setHeader(AUTH_HEADER_NAME, AUTH_HEADER_VALUE_NEGOTIATE);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        LoginContext lc = null;
        GSSContext gssContext = null;
        byte[] outToken;
        Principal principal;
        try {
            try {
                lc = new LoginContext(getLoginConfigName());
                lc.login();
            } catch (LoginException e) {
                log.error(sm.getString("spnegoAuthenticator.serviceLoginFail"), e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return false;
            }

            Subject subject = lc.getSubject();

            // Assume the GSSContext is stateless
            // TODO: Confirm this assumption
            final GSSManager manager = GSSManager.getInstance();
            // IBM JDK only understands indefinite lifetime
            final int credentialLifetime;
            if (JreVendor.IS_IBM_JVM) {
                credentialLifetime = GSSCredential.INDEFINITE_LIFETIME;
            } else {
                credentialLifetime = GSSCredential.DEFAULT_LIFETIME;
            }
            gssContext = manager.createContext(Subject.callAs(subject, () -> manager.createCredential(null,
                    credentialLifetime, new Oid("1.3.6.1.5.5.2"), GSSCredential.ACCEPT_ONLY)));

            final GSSContext gssContextFinal = gssContext;
            outToken = Subject.callAs(subject, () -> gssContextFinal.acceptSecContext(decoded, 0, decoded.length));

            if (outToken == null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("spnegoAuthenticator.ticketValidateFail"));
                }
                // Start again
                response.setHeader(AUTH_HEADER_NAME, AUTH_HEADER_VALUE_NEGOTIATE);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }

            principal = Subject.callAs(subject,
                    () -> context.getRealm().authenticate(gssContextFinal, storeDelegatedCredential));
        } catch (GSSException e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("spnegoAuthenticator.ticketValidateFail"), e);
            }
            response.setHeader(AUTH_HEADER_NAME, AUTH_HEADER_VALUE_NEGOTIATE);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof GSSException) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("spnegoAuthenticator.serviceLoginFail"), e);
                }
            } else {
                log.error(sm.getString("spnegoAuthenticator.serviceLoginFail"), e);
            }
            response.setHeader(AUTH_HEADER_NAME, AUTH_HEADER_VALUE_NEGOTIATE);
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
        response.setHeader(AUTH_HEADER_NAME,
                AUTH_HEADER_VALUE_NEGOTIATE + " " + Base64.getEncoder().encodeToString(outToken));

        if (principal != null) {
            register(request, response, principal, Constants.SPNEGO_METHOD, principal.getName(), null);

            Pattern p = noKeepAliveUserAgents;
            if (p != null) {
                MessageBytes ua = request.getCoyoteRequest().getMimeHeaders().getValue("user-agent");
                if (ua != null && p.matcher(ua.toString()).matches()) {
                    response.setHeader("Connection", "close");
                }
            }
            return true;
        }

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }


    @Override
    protected boolean isPreemptiveAuthPossible(Request request) {
        MessageBytes authorizationHeader = request.getCoyoteRequest().getMimeHeaders().getValue("authorization");
        return authorizationHeader != null && authorizationHeader.startsWithIgnoreCase("negotiate ", 0);
    }
}

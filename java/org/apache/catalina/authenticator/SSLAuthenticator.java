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
import java.security.cert.X509Certificate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.coyote.ActionCode;
import org.apache.coyote.UpgradeProtocol;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.Constants;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfig.CertificateVerification;

/**
 * An <b>Authenticator</b> and <b>Valve</b> implementation of authentication that utilizes SSL certificates to identify
 * client users.
 *
 * @author Craig R. McClanahan
 */
public class SSLAuthenticator extends AuthenticatorBase {

    private final Log log = LogFactory.getLog(SSLAuthenticator.class); // must not be static

    private boolean allowSsoReauthentication = false;

    public boolean getAllowSsoReauthentication() {
        return allowSsoReauthentication;
    }

    public void setAllowSsoReauthentication(boolean allowSsoReauthentication) {
        this.allowSsoReauthentication = allowSsoReauthentication;
    }


    /**
     * Authenticate the user by checking for the existence of a certificate chain, validating it against the trust
     * manager for the connector and then validating the user's identity against the configured Realm.
     *
     * @param request  Request we are processing
     * @param response Response we are creating
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response) throws IOException {

        // NOTE: We don't try to reauthenticate using any existing SSO session,
        // because that will only work if the original authentication was
        // BASIC or FORM, which are less secure than the CLIENT-CERT auth-type
        // specified for this webapp. This behaviour may be modified by setting
        // the allowSsoReauthentication property.
        if (checkForCachedAuthentication(request, response, allowSsoReauthentication)) {
            return true;
        }

        // Retrieve the certificate chain for this client
        if (containerLog.isTraceEnabled()) {
            containerLog.trace(" Looking up certificates");
        }

        X509Certificate[] certs = getRequestCertificates(request);

        if ((certs == null) || (certs.length < 1)) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("sslAuthenticatorValve.noCertificates"));
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, sm.getString("authenticator.certificates"));
            return false;
        }

        // Authenticate the specified certificate chain
        Principal principal = context.getRealm().authenticate(certs);
        if (principal == null) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug(sm.getString("sslAuthenticatorValve.authFailed"));
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, sm.getString("authenticator.unauthorized"));
            return false;
        }

        // Cache the principal (if requested) and record this authentication
        register(request, response, principal, HttpServletRequest.CLIENT_CERT_AUTH, null, null);
        return true;

    }


    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.CLIENT_CERT_AUTH;
    }


    @Override
    protected boolean isPreemptiveAuthPossible(Request request) {
        X509Certificate[] certs = getRequestCertificates(request);
        return certs != null && certs.length > 0;
    }


    /**
     * Look for the X509 certificate chain in the Request under the key
     * <code>jakarta.servlet.request.X509Certificate</code>. If not found, trigger extracting the certificate chain from
     * the Coyote request.
     *
     * @param request Request to be processed
     *
     * @return The X509 certificate chain if found, <code>null</code> otherwise.
     */
    protected X509Certificate[] getRequestCertificates(final Request request) throws IllegalStateException {

        X509Certificate[] certs = (X509Certificate[]) request.getAttribute(Globals.CERTIFICATES_ATTR);

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


    @Override
    protected void startInternal() throws LifecycleException {

        super.startInternal();

        /*
         * This Valve should only ever be added to a Context and if the Context is started there should always be a Host
         * and an Engine but test at each stage to be safe.
         */
        Container container = getContainer();
        if (!(container instanceof Context context)) {
            return;
        }
        container = context.getParent();
        if (!(container instanceof Host host)) {
            return;
        }
        container = host.getParent();
        if (!(container instanceof Engine engine)) {
            return;
        }

        Connector[] connectors = engine.getService().findConnectors();

        for (Connector connector : connectors) {
            /*
             * There are two underlying issues here.
             *
             * 1. JSSE does not implement post-handshake authentication (PHA) for TLS 1.3. That means CLIENT-CERT
             * authentication will only work if the virtual host requires a certificate OR the client never requests a
             * protected resource.
             *
             * 2. HTTP/2 does not permit re-negotiation nor PHA. That means CLIENT-CERT authentication will only work if
             * the virtual host requires a certificate OR the client never requests a protected resource.
             *
             * We can't rely on the client never requesting a protected resource but we can check if all the virtual
             * hosts are configured to require a certificate.
             */
            boolean allHostsRequireCertificate = true;
            for (SSLHostConfig sslHostConfig : connector.findSslHostConfigs()) {
                if (sslHostConfig.getCertificateVerification() != CertificateVerification.REQUIRED) {
                    allHostsRequireCertificate = false;
                    break;
                }
            }

            // Only need to check for use of HTTP/2 or TLS 1.3 if one or more hosts doesn't require a certificate
            if (!allHostsRequireCertificate) {
                // Check if the Connector is configured to support upgrade to HTTP/2
                UpgradeProtocol[] upgradeProtocols = connector.findUpgradeProtocols();
                for (UpgradeProtocol upgradeProtocol : upgradeProtocols) {
                    if ("h2".equals(upgradeProtocol.getAlpnName())) {
                        log.warn(sm.getString("sslAuthenticatorValve.http2", context.getName(), host.getName(),
                                connector));
                        break;
                    }
                }

                // Check if any of the virtual hosts support TLS 1.3 without supporting PHA
                for (SSLHostConfig sslHostConfig : connector.findSslHostConfigs()) {
                    if (!sslHostConfig.isTls13RenegotiationAvailable()) {
                        String[] enabledProtocols = sslHostConfig.getEnabledProtocols();
                        if (enabledProtocols == null) {
                            // Possibly boundOnInit is used, so use the less accurate protocols
                            enabledProtocols = sslHostConfig.getProtocols().toArray(new String[0]);
                        }
                        for (String enabledProtocol : enabledProtocols) {
                            if (Constants.SSL_PROTO_TLSv1_3.equals(enabledProtocol)) {
                                log.warn(sm.getString("sslAuthenticatorValve.tls13", context.getName(), host.getName(),
                                        connector));
                            }
                        }
                    }
                }
            }
        }
    }
}

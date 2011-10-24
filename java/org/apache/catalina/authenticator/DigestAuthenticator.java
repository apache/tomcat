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
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.util.MD5Encoder;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;



/**
 * An <b>Authenticator</b> and <b>Valve</b> implementation of HTTP DIGEST
 * Authentication (see RFC 2069).
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * @version $Id$
 */

public class DigestAuthenticator extends AuthenticatorBase {

    private static final Log log = LogFactory.getLog(DigestAuthenticator.class);


    // -------------------------------------------------------------- Constants

    /**
     * The MD5 helper object for this class.
     */
    protected static final MD5Encoder md5Encoder = new MD5Encoder();


    /**
     * Tomcat's DIGEST implementation only supports auth quality of protection.
     */
    protected static final String QOP = "auth";

    // ----------------------------------------------------------- Constructors


    public DigestAuthenticator() {
        super();
        try {
            if (md5Helper == null) {
                md5Helper = MessageDigest.getInstance("MD5");
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * MD5 message digest provider.
     */
    protected static volatile MessageDigest md5Helper;


    /**
     * List of client nonce values currently being tracked
     */
    protected Map<String,NonceInfo> cnonces;


    /**
     * Maximum number of client nonces to keep in the cache. If not specified,
     * the default value of 1000 is used.
     */
    protected int cnonceCacheSize = 1000;


    /**
     * Private key.
     */
    protected String key = null;


    /**
     * How long server nonces are valid for in milliseconds. Defaults to 5
     * minutes.
     */
    protected long nonceValidity = 5 * 60 * 1000;


    /**
     * Opaque string.
     */
    protected String opaque;


    /**
     * Should the URI be validated as required by RFC2617? Can be disabled in
     * reverse proxies where the proxy has modified the URI.
     */
    protected boolean validateUri = true;

    // ------------------------------------------------------------- Properties

    public int getCnonceCacheSize() {
        return cnonceCacheSize;
    }


    public void setCnonceCacheSize(int cnonceCacheSize) {
        this.cnonceCacheSize = cnonceCacheSize;
    }


    public String getKey() {
        return key;
    }


    public void setKey(String key) {
        this.key = key;
    }


    public long getNonceValidity() {
        return nonceValidity;
    }


    public void setNonceValidity(long nonceValidity) {
        this.nonceValidity = nonceValidity;
    }


    public String getOpaque() {
        return opaque;
    }


    public void setOpaque(String opaque) {
        this.opaque = opaque;
    }


    public boolean isValidateUri() {
        return validateUri;
    }


    public void setValidateUri(boolean validateUri) {
        this.validateUri = validateUri;
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

        // Have we already authenticated someone?
        Principal principal = request.getUserPrincipal();
        //String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (principal != null) {
            if (log.isDebugEnabled()) {
                log.debug("Already authenticated '" + principal.getName() + "'");
            }
            // Associate the session with any existing SSO session in order
            // to get coordinated session invalidation at logout
            String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
            if (ssoId != null) {
                associate(ssoId, request.getSessionInternal(true));
            }
            return (true);
        }

        // NOTE: We don't try to reauthenticate using any existing SSO session,
        // because that will only work if the original authentication was
        // BASIC or FORM, which are less secure than the DIGEST auth-type
        // specified for this webapp
        //
        // Uncomment below to allow previous FORM or BASIC authentications
        // to authenticate users for this webapp
        // TODO make this a configurable attribute (in SingleSignOn??)
        /*
        // Is there an SSO session against which we can try to reauthenticate?
        if (ssoId != null) {
            if (log.isDebugEnabled())
                log.debug("SSO Id " + ssoId + " set; attempting " +
                          "reauthentication");
            // Try to reauthenticate using data cached by SSO.  If this fails,
            // either the original SSO logon was of DIGEST or SSL (which
            // we can't reauthenticate ourselves because there is no
            // cached username and password), or the realm denied
            // the user's reauthentication for some reason.
            // In either case we have to prompt the user for a logon
            if (reauthenticateFromSSO(ssoId, request))
                return true;
        }
        */

        // Validate any credentials already included with this request
        String authorization = request.getHeader("authorization");
        DigestInfo digestInfo = new DigestInfo(getOpaque(), getNonceValidity(),
                getKey(), cnonces, isValidateUri());
        if (authorization != null) {
            if (digestInfo.validate(request, authorization, config)) {
                principal = digestInfo.authenticate(context.getRealm());
            }

            if (principal != null) {
                String username = parseUsername(authorization);
                register(request, response, principal,
                        HttpServletRequest.DIGEST_AUTH, username, null);
                return (true);
            }
        }

        // Send an "unauthorized" response and an appropriate challenge

        // Next, generate a nonce token (that is a token which is supposed
        // to be unique).
        String nonce = generateNonce(request);

        setAuthenticateHeader(request, response, config, nonce,
                digestInfo.isNonceStale());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        //      hres.flushBuffer();
        return (false);

    }


    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.DIGEST_AUTH;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Parse the username from the specified authorization string.  If none
     * can be identified, return <code>null</code>
     *
     * @param authorization Authorization string to be parsed
     */
    protected String parseUsername(String authorization) {

        // Validate the authorization credentials format
        if (authorization == null) {
            return (null);
        }
        if (!authorization.startsWith("Digest ")) {
            return (null);
        }
        authorization = authorization.substring(7).trim();

        StringTokenizer commaTokenizer =
            new StringTokenizer(authorization, ",");

        while (commaTokenizer.hasMoreTokens()) {
            String currentToken = commaTokenizer.nextToken();
            int equalSign = currentToken.indexOf('=');
            if (equalSign < 0) {
                return null;
            }
            String currentTokenName =
                currentToken.substring(0, equalSign).trim();
            String currentTokenValue =
                currentToken.substring(equalSign + 1).trim();
            if ("username".equals(currentTokenName)) {
                return (removeQuotes(currentTokenValue));
            }
        }

        return (null);

    }


    /**
     * Removes the quotes on a string. RFC2617 states quotes are optional for
     * all parameters except realm.
     */
    protected static String removeQuotes(String quotedString,
                                         boolean quotesRequired) {
        //support both quoted and non-quoted
        if (quotedString.length() > 0 && quotedString.charAt(0) != '"' &&
                !quotesRequired) {
            return quotedString;
        } else if (quotedString.length() > 2) {
            return quotedString.substring(1, quotedString.length() - 1);
        } else {
            return "";
        }
    }

    /**
     * Removes the quotes on a string.
     */
    protected static String removeQuotes(String quotedString) {
        return removeQuotes(quotedString, false);
    }

    /**
     * Generate a unique token. The token is generated according to the
     * following pattern. NOnceToken = Base64 ( MD5 ( client-IP ":"
     * time-stamp ":" private-key ) ).
     *
     * @param request HTTP Servlet request
     */
    protected String generateNonce(Request request) {

        long currentTime = System.currentTimeMillis();


        String ipTimeKey =
            request.getRemoteAddr() + ":" + currentTime + ":" + getKey();

        byte[] buffer;
        synchronized (md5Helper) {
            buffer = md5Helper.digest(
                    ipTimeKey.getBytes(Charset.defaultCharset()));
        }

        return currentTime + ":" + md5Encoder.encode(buffer);
    }


    /**
     * Generates the WWW-Authenticate header.
     * <p>
     * The header MUST follow this template :
     * <pre>
     *      WWW-Authenticate    = "WWW-Authenticate" ":" "Digest"
     *                            digest-challenge
     *
     *      digest-challenge    = 1#( realm | [ domain ] | nonce |
     *                  [ digest-opaque ] |[ stale ] | [ algorithm ] )
     *
     *      realm               = "realm" "=" realm-value
     *      realm-value         = quoted-string
     *      domain              = "domain" "=" <"> 1#URI <">
     *      nonce               = "nonce" "=" nonce-value
     *      nonce-value         = quoted-string
     *      opaque              = "opaque" "=" quoted-string
     *      stale               = "stale" "=" ( "true" | "false" )
     *      algorithm           = "algorithm" "=" ( "MD5" | token )
     * </pre>
     *
     * @param request HTTP Servlet request
     * @param response HTTP Servlet response
     * @param config    Login configuration describing how authentication
     *              should be performed
     * @param nonce nonce token
     */
    protected void setAuthenticateHeader(HttpServletRequest request,
                                         HttpServletResponse response,
                                         LoginConfig config,
                                         String nonce,
                                         boolean isNonceStale) {

        // Get the realm name
        String realmName = config.getRealmName();
        if (realmName == null) {
            realmName = REALM_NAME;
        }

        String authenticateHeader;
        if (isNonceStale) {
            authenticateHeader = "Digest realm=\"" + realmName + "\", " +
            "qop=\"" + QOP + "\", nonce=\"" + nonce + "\", " + "opaque=\"" +
            getOpaque() + "\", stale=true";
        } else {
            authenticateHeader = "Digest realm=\"" + realmName + "\", " +
            "qop=\"" + QOP + "\", nonce=\"" + nonce + "\", " + "opaque=\"" +
            getOpaque() + "\"";
        }

        response.setHeader(AUTH_HEADER_NAME, authenticateHeader);

    }


    // ------------------------------------------------------- Lifecycle Methods

    @Override
    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();

        // Generate a random secret key
        if (getKey() == null) {
            setKey(sessionIdGenerator.generateSessionId());
        }

        // Generate the opaque string the same way
        if (getOpaque() == null) {
            setOpaque(sessionIdGenerator.generateSessionId());
        }

        cnonces = new LinkedHashMap<String, DigestAuthenticator.NonceInfo>() {

            private static final long serialVersionUID = 1L;
            private static final long LOG_SUPPRESS_TIME = 5 * 60 * 1000;

            private long lastLog = 0;

            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<String,NonceInfo> eldest) {
                // This is called from a sync so keep it simple
                long currentTime = System.currentTimeMillis();
                if (size() > getCnonceCacheSize()) {
                    if (lastLog < currentTime &&
                            currentTime - eldest.getValue().getTimestamp() <
                            getNonceValidity()) {
                        // Replay attack is possible
                        log.warn(sm.getString(
                                "digestAuthenticator.cacheRemove"));
                        lastLog = currentTime + LOG_SUPPRESS_TIME;
                    }
                    return true;
                }
                return false;
            }
        };
    }

    private static class DigestInfo {

        private final String opaque;
        private final long nonceValidity;
        private final String key;
        private final Map<String,NonceInfo> cnonces;
        private boolean validateUri = true;

        private String userName = null;
        private String method = null;
        private String uri = null;
        private String response = null;
        private String nonce = null;
        private String nc = null;
        private String cnonce = null;
        private String realmName = null;
        private String qop = null;

        private boolean nonceStale = false;


        public DigestInfo(String opaque, long nonceValidity, String key,
                Map<String,NonceInfo> cnonces, boolean validateUri) {
            this.opaque = opaque;
            this.nonceValidity = nonceValidity;
            this.key = key;
            this.cnonces = cnonces;
            this.validateUri = validateUri;
        }

        public boolean validate(Request request, String authorization,
                LoginConfig config) {
            // Validate the authorization credentials format
            if (authorization == null) {
                return false;
            }
            if (!authorization.startsWith("Digest ")) {
                return false;
            }
            authorization = authorization.substring(7).trim();

            // Bugzilla 37132: http://issues.apache.org/bugzilla/show_bug.cgi?id=37132
            String[] tokens = authorization.split(",(?=(?:[^\"]*\"[^\"]*\")+$)");

            method = request.getMethod();
            String opaque = null;

            for (int i = 0; i < tokens.length; i++) {
                String currentToken = tokens[i];
                if (currentToken.length() == 0) {
                    continue;
                }

                int equalSign = currentToken.indexOf('=');
                if (equalSign < 0) {
                    return false;
                }
                String currentTokenName =
                    currentToken.substring(0, equalSign).trim();
                String currentTokenValue =
                    currentToken.substring(equalSign + 1).trim();
                if ("username".equals(currentTokenName)) {
                    userName = removeQuotes(currentTokenValue);
                }
                if ("realm".equals(currentTokenName)) {
                    realmName = removeQuotes(currentTokenValue, true);
                }
                if ("nonce".equals(currentTokenName)) {
                    nonce = removeQuotes(currentTokenValue);
                }
                if ("nc".equals(currentTokenName)) {
                    nc = removeQuotes(currentTokenValue);
                }
                if ("cnonce".equals(currentTokenName)) {
                    cnonce = removeQuotes(currentTokenValue);
                }
                if ("qop".equals(currentTokenName)) {
                    qop = removeQuotes(currentTokenValue);
                }
                if ("uri".equals(currentTokenName)) {
                    uri = removeQuotes(currentTokenValue);
                }
                if ("response".equals(currentTokenName)) {
                    response = removeQuotes(currentTokenValue);
                }
                if ("opaque".equals(currentTokenName)) {
                    opaque = removeQuotes(currentTokenValue);
                }
            }

            if ( (userName == null) || (realmName == null) || (nonce == null)
                 || (uri == null) || (response == null) ) {
                return false;
            }

            // Validate the URI - should match the request line sent by client
            if (validateUri) {
                String uriQuery;
                String query = request.getQueryString();
                if (query == null) {
                    uriQuery = request.getRequestURI();
                } else {
                    uriQuery = request.getRequestURI() + "?" + query;
                }
                if (!uri.equals(uriQuery)) {
                    return false;
                }
            }

            // Validate the Realm name
            String lcRealm = config.getRealmName();
            if (lcRealm == null) {
                lcRealm = REALM_NAME;
            }
            if (!lcRealm.equals(realmName)) {
                return false;
            }

            // Validate the opaque string
            if (!this.opaque.equals(opaque)) {
                return false;
            }

            // Validate nonce
            int i = nonce.indexOf(":");
            if (i < 0 || (i + 1) == nonce.length()) {
                return false;
            }
            long nonceTime;
            try {
                nonceTime = Long.parseLong(nonce.substring(0, i));
            } catch (NumberFormatException nfe) {
                return false;
            }
            String md5clientIpTimeKey = nonce.substring(i + 1);
            long currentTime = System.currentTimeMillis();
            if ((currentTime - nonceTime) > nonceValidity) {
                nonceStale = true;
                return false;
            }
            String serverIpTimeKey =
                request.getRemoteAddr() + ":" + nonceTime + ":" + key;
            byte[] buffer = null;
            synchronized (md5Helper) {
                buffer = md5Helper.digest(
                        serverIpTimeKey.getBytes(Charset.defaultCharset()));
            }
            String md5ServerIpTimeKey = md5Encoder.encode(buffer);
            if (!md5ServerIpTimeKey.equals(md5clientIpTimeKey)) {
                return false;
            }

            // Validate qop
            if (qop != null && !QOP.equals(qop)) {
                return false;
            }

            // Validate cnonce and nc
            // Check if presence of nc and nonce is consistent with presence of qop
            if (qop == null) {
                if (cnonce != null || nc != null) {
                    return false;
                }
            } else {
                if (cnonce == null || nc == null) {
                    return false;
                }
                if (nc.length() != 8) {
                    return false;
                }
                long count;
                try {
                    count = Long.parseLong(nc, 16);
                } catch (NumberFormatException nfe) {
                    return false;
                }
                NonceInfo info;
                synchronized (cnonces) {
                    info = cnonces.get(cnonce);
                }
                if (info == null) {
                    info = new NonceInfo();
                } else {
                    if (count <= info.getCount()) {
                        return false;
                    }
                }
                info.setCount(count);
                info.setTimestamp(currentTime);
                synchronized (cnonces) {
                    cnonces.put(cnonce, info);
                }
            }
            return true;
        }

        public boolean isNonceStale() {
            return nonceStale;
        }

        public Principal authenticate(Realm realm) {
            // Second MD5 digest used to calculate the digest :
            // MD5(Method + ":" + uri)
            String a2 = method + ":" + uri;

            byte[] buffer;
            synchronized (md5Helper) {
                buffer = md5Helper.digest(a2.getBytes(Charset.defaultCharset()));
            }
            String md5a2 = md5Encoder.encode(buffer);

            return realm.authenticate(userName, response, nonce, nc, cnonce,
                    qop, realmName, md5a2);
        }

    }

    private static class NonceInfo {
        private volatile long count;
        private volatile long timestamp;

        public void setCount(long l) {
            count = l;
        }

        public long getCount() {
            return count;
        }

        public void setTimestamp(long l) {
            timestamp = l;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}

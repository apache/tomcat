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

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.MessagePolicy;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Realm;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.parser.Authorization;
import org.apache.tomcat.util.security.ConcurrentMessageDigest;
import org.apache.tomcat.util.security.MD5Encoder;

public class DigestAuthModule extends TomcatAuthModule {
    private static final Log log = LogFactory.getLog(DigestAuthModule.class);
    /**
     * Tomcat's DIGEST implementation only supports auth quality of protection.
     */
    protected static final String QOP = "auth";

    private Class<?>[] supportedMessageTypes = new Class[] { HttpServletRequest.class,
            HttpServletResponse.class };

    private CallbackHandler handler;

    private Realm realm;

    /**
     * List of server nonce values currently being tracked
     */
    protected Map<String, NonceInfo> nonces;

    /**
     * The last timestamp used to generate a nonce. Each nonce should get a
     * unique timestamp.
     */
    protected long lastTimestamp = 0;
    protected final Object lastTimestampLock = new Object();

    /**
     * Maximum number of server nonces to keep in the cache. If not specified,
     * the default value of 1000 is used.
     */
    protected int nonceCacheSize = 1000;

    /**
     * The window size to use to track seen nonce count values for a given
     * nonce. If not specified, the default of 100 is used.
     */
    protected int nonceCountWindowSize = 100;

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
    private StandardSessionIdGenerator sessionIdGenerator;


    // ------------------------------------------------------------- Properties

    public DigestAuthModule(Realm realm) {
        this.realm = realm;
    }


    public int getNonceCountWindowSize() {
        return nonceCountWindowSize;
    }


    public void setNonceCountWindowSize(int nonceCountWindowSize) {
        this.nonceCountWindowSize = nonceCountWindowSize;
    }


    public int getNonceCacheSize() {
        return nonceCacheSize;
    }


    public void setNonceCacheSize(int nonceCacheSize) {
        this.nonceCacheSize = nonceCacheSize;
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


    public void setRealm(Realm realm) {
        this.realm = realm;
    }


    @Override
    @SuppressWarnings("rawtypes")
    public void initializeModule(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
            CallbackHandler handler, Map options) throws AuthException {
        this.handler = handler;
        startInternal();
    }


    protected synchronized void startInternal() {
        this.sessionIdGenerator = new StandardSessionIdGenerator();

        // Generate a random secret key
        if (getKey() == null) {
            setKey(sessionIdGenerator.generateSessionId());
        }

        // Generate the opaque string the same way
        if (getOpaque() == null) {
            setOpaque(sessionIdGenerator.generateSessionId());
        }

        nonces = new LinkedHashMap<String, NonceInfo>() {

            private static final long serialVersionUID = 1L;
            private static final long LOG_SUPPRESS_TIME = 5 * 60 * 1000;

            private long lastLog = 0;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, NonceInfo> eldest) {
                // This is called from a sync so keep it simple
                long currentTime = System.currentTimeMillis();
                if (size() > getNonceCacheSize()) {
                    if (lastLog < currentTime
                            && currentTime - eldest.getValue().getTimestamp() < getNonceValidity()) {
                        // Replay attack is possible
                        log.warn(sm.getString("digestAuthenticator.cacheRemove"));
                        lastLog = currentTime + LOG_SUPPRESS_TIME;
                    }
                    return true;
                }
                return false;
            }
        };
    }


    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException {

        Principal principal = null;
        HttpServletRequest request = (HttpServletRequest) messageInfo.getRequestMessage();
        HttpServletResponse response = (HttpServletResponse) messageInfo.getResponseMessage();
        String authorization = request.getHeader(AUTHORIZATION_HEADER);

        DigestInfo digestInfo = new DigestInfo(getOpaque(), getNonceValidity(), getKey(), nonces,
                isValidateUri(), getRealmName());
        if (authorization == null) {

            String nonce = generateNonce(request);

            String authenticateHeader = getAuthenticateHeader(nonce, false);
            return sendUnauthorizedError(response, authenticateHeader);
        }

        if (!digestInfo.parse(request, authorization)) {
            return AuthStatus.SEND_FAILURE;
        }

        if (digestInfo.validate(request)) {
            principal = digestInfo.authenticate(realm);
        }

        if (principal == null || digestInfo.isNonceStale()) {
            String nonce = generateNonce(request);
            boolean isNoncaneStale = principal != null && digestInfo.isNonceStale();
            String authenticateHeader = getAuthenticateHeader(nonce, isNoncaneStale);
            return sendUnauthorizedError(response, authenticateHeader);
        }

        try {
            CallerPrincipalCallback principalCallback = new CallerPrincipalCallback(clientSubject,
                    principal);
            String[] roles = realm.getRoles(principal);
            GroupPrincipalCallback groupCallback = new GroupPrincipalCallback(clientSubject, roles);
            handler.handle(new Callback[] { principalCallback, groupCallback });
        } catch (IOException | UnsupportedCallbackException e) {
            throw new AuthException(e.getMessage());
        }
        return AuthStatus.SUCCESS;
    }


    private AuthStatus sendUnauthorizedError(HttpServletResponse response, String authenticateHeader)
            throws AuthException {
        response.setHeader(AUTH_HEADER_NAME, authenticateHeader);
        try {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } catch (IOException e) {
            throw new AuthException(e.getMessage());
        }
        return AuthStatus.SEND_CONTINUE;
    }


    @Override
    public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject)
            throws AuthException {
        return null;
    }


    @Override
    public void cleanSubject(MessageInfo messageInfo, Subject subject) throws AuthException {

    }


    @Override
    public Class<?>[] getSupportedMessageTypes() {
        return supportedMessageTypes;
    }


    /**
     * Removes the quotes on a string. RFC2617 states quotes are optional for
     * all parameters except realm.
     */
    protected static String removeQuotes(String quotedString, boolean quotesRequired) {
        // support both quoted and non-quoted
        if (quotedString.length() > 0 && quotedString.charAt(0) != '"' && !quotesRequired) {
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
     * following pattern. NOnceToken = Base64 ( MD5 ( client-IP ":" time-stamp
     * ":" private-key ) ).
     *
     * @param request HTTP Servlet request
     */
    protected String generateNonce(HttpServletRequest request) {

        long currentTime = System.currentTimeMillis();

        synchronized (lastTimestampLock) {
            if (currentTime > lastTimestamp) {
                lastTimestamp = currentTime;
            } else {
                currentTime = ++lastTimestamp;
            }
        }

        String ipTimeKey = request.getRemoteAddr() + ":" + currentTime + ":" + getKey();

        byte[] buffer = ConcurrentMessageDigest.digestMD5(ipTimeKey
                .getBytes(StandardCharsets.ISO_8859_1));
        String nonce = currentTime + ":" + MD5Encoder.encode(buffer);

        NonceInfo info = new NonceInfo(currentTime, getNonceCountWindowSize());
        synchronized (nonces) {
            nonces.put(nonce, info);
        }

        return nonce;
    }


    /**
     * Generates the WWW-Authenticate header.
     * <p>
     * The header MUST follow this template :
     *
     * <pre>
     *      WWW-Authenticate    = "WWW-Authenticate" ":" "Digest"
     *                            digest-challenge
     *
     *      digest-challenge    = 1#( realm | [ domain ] | nonce |
     *                  [ digest-opaque ] |[ stale ] | [ algorithm ] )
     *      realm               = "realm" "=" realm-value
     *      realm-value         = quoted-string
     *      domain              = "domain" "=" &lt;"&gt; 1#URI &lt;"&gt;
     *      nonce               = "nonce" "=" nonce-value
     *      nonce-value         = quoted-string
     *      opaque              = "opaque" "=" quoted-string
     *      stale               = "stale" "=" ( "true" | "false" )
     *      algorithm           = "algorithm" "=" ( "MD5" | token )
     * </pre>
     *
     * @param nonce nonce token
     * @return
     */
    protected String getAuthenticateHeader(String nonce, boolean isNonceStale) {

        String realmName = getRealmName();

        String template = "Digest realm=\"{0}\", qop=\"{1}\", nonce=\"{2}\", opaque=\"{3}\"";
        String authenticateHeader = MessageFormat.format(template, realmName, QOP, nonce,
                getOpaque());
        if (!isNonceStale) {
            return authenticateHeader;
        }
        return authenticateHeader + ", stale=true";
    }


    private static class DigestInfo {

        private final String opaque;
        private final long nonceValidity;
        private final String key;
        private final Map<String, NonceInfo> nonces;
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
        private String opaqueReceived = null;

        private boolean nonceStale = false;

        private String contextRealmName;

        public DigestInfo(String opaque, long nonceValidity, String key,
                Map<String, NonceInfo> nonces, boolean validateUri, String contextRealmName) {
            this.opaque = opaque;
            this.nonceValidity = nonceValidity;
            this.key = key;
            this.nonces = nonces;
            this.validateUri = validateUri;
            this.contextRealmName = contextRealmName;
        }

        public String getUsername() {
            return userName;
        }

        public boolean parse(HttpServletRequest request, String authorization) {
            // Validate the authorization credentials format
            if (authorization == null) {
                return false;
            }

            Map<String, String> directives;
            try {
                directives = Authorization.parseAuthorizationDigest(
                        new StringReader(authorization));
            } catch (IOException e) {
                return false;
            }

            if (directives == null) {
                return false;
            }

            method = request.getMethod();
            userName = directives.get("username");
            realmName = directives.get("realm");
            nonce = directives.get("nonce");
            nc = directives.get("nc");
            cnonce = directives.get("cnonce");
            qop = directives.get("qop");
            uri = directives.get("uri");
            response = directives.get("response");
            opaqueReceived = directives.get("opaque");

            return true;
        }

        public boolean validate(HttpServletRequest request) {
            if ((userName == null) || (realmName == null) || (nonce == null) || (uri == null)
                    || (response == null)) {
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
                    // Some clients (older Android) use an absolute URI for
                    // DIGEST but a relative URI in the request line.
                    // request. 2.3.5 < fixed Android version <= 4.0.3
                    String host = request.getHeader("host");
                    String scheme = request.getScheme();
                    if (host != null && !uriQuery.startsWith(scheme)) {
                        StringBuilder absolute = new StringBuilder();
                        absolute.append(scheme);
                        absolute.append("://");
                        absolute.append(host);
                        absolute.append(uriQuery);
                        if (!uri.equals(absolute.toString())) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }

            // Validate the Realm name
            if (!contextRealmName.equals(realmName)) {
                return false;
            }

            // Validate the opaque string
            if (!opaque.equals(opaqueReceived)) {
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
                synchronized (nonces) {
                    nonces.remove(nonce);
                }
            }
            String serverIpTimeKey = request.getRemoteAddr() + ":" + nonceTime + ":" + key;
            byte[] buffer = ConcurrentMessageDigest.digestMD5(serverIpTimeKey
                    .getBytes(StandardCharsets.ISO_8859_1));
            String md5ServerIpTimeKey = MD5Encoder.encode(buffer);
            if (!md5ServerIpTimeKey.equals(md5clientIpTimeKey)) {
                return false;
            }

            // Validate qop
            if (qop != null && !QOP.equals(qop)) {
                return false;
            }

            // Validate cnonce and nc
            // Check if presence of nc and Cnonce is consistent with presence of
            // qop
            if (qop == null) {
                if (cnonce != null || nc != null) {
                    return false;
                }
            } else {
                if (cnonce == null || nc == null) {
                    return false;
                }
                // RFC 2617 says nc must be 8 digits long. Older Android clients
                // use 6. 2.3.5 < fixed Android version <= 4.0.3
                if (nc.length() < 6 || nc.length() > 8) {
                    return false;
                }
                long count;
                try {
                    count = Long.parseLong(nc, 16);
                } catch (NumberFormatException nfe) {
                    return false;
                }
                NonceInfo info;
                synchronized (nonces) {
                    info = nonces.get(nonce);
                }
                if (info == null) {
                    // Nonce is valid but not in cache. It must have dropped out
                    // of the cache - force a re-authentication
                    nonceStale = true;
                } else {
                    if (!info.nonceCountValid(count)) {
                        return false;
                    }
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

            byte[] buffer = ConcurrentMessageDigest.digestMD5(a2
                    .getBytes(StandardCharsets.ISO_8859_1));
            String md5a2 = MD5Encoder.encode(buffer);

            return realm.authenticate(userName, response, nonce, nc, cnonce, qop, realmName, md5a2);
        }

    }


    private static class NonceInfo {
        private final long timestamp;
        private final boolean seen[];
        private final int offset;
        private int count = 0;

        public NonceInfo(long currentTime, int seenWindowSize) {
            this.timestamp = currentTime;
            seen = new boolean[seenWindowSize];
            offset = seenWindowSize / 2;
        }

        public synchronized boolean nonceCountValid(long nonceCount) {
            if ((count - offset) >= nonceCount || (nonceCount > count - offset + seen.length)) {
                return false;
            }
            int checkIndex = (int) ((nonceCount + offset) % seen.length);
            if (seen[checkIndex]) {
                return false;
            } else {
                seen[checkIndex] = true;
                seen[count % seen.length] = false;
                count++;
                return true;
            }
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}

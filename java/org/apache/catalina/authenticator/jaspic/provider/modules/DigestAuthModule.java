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
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.MessagePolicy;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Realm;
import org.apache.catalina.authenticator.DigestAuthenticator.DigestInfo;
import org.apache.catalina.authenticator.DigestAuthenticator.NonceInfo;
import org.apache.catalina.connector.Request;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
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

    public DigestAuthModule(Context context) {
        super(context);
        this.realm = context.getRealm();
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
    public synchronized void initializeModule(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
            CallbackHandler handler, Map<String, String> options) throws AuthException {
        this.handler = handler;
        this.sessionIdGenerator = new StandardSessionIdGenerator();

        // Get properties from options
        this.key = options.get("key");
        String nonceCacheSizeValue = options.get("nonceCacheSize");
        if (nonceCacheSizeValue != null) {
            this.nonceCacheSize = Integer.parseInt(nonceCacheSizeValue);
        }
        String nonceCountWindowSizeValue = options.get("nonceCountWindowSize");
        if (nonceCountWindowSizeValue != null) {
            this.nonceCountWindowSize = Integer.parseInt(nonceCountWindowSizeValue);
        }
        String nonceValidityValue = options.get("nonceValidity");
        if (nonceValidityValue != null) {
            this.nonceValidity = Long.parseLong(nonceValidityValue);
        }
        this.opaque = options.get("opaque");
        String validateUriValue = options.get("validateUri");
        if (validateUriValue != null) {
            this.validateUri = Boolean.parseBoolean(validateUriValue);
        }

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
        Request request = (Request) messageInfo.getRequestMessage();
        HttpServletResponse response = (HttpServletResponse) messageInfo.getResponseMessage();
        String authorization = request.getHeader(AUTHORIZATION_HEADER);

        DigestInfo digestInfo = new DigestInfo(getOpaque(), getNonceValidity(), getKey(), nonces,
                isValidateUri());

        if (authorization == null || !digestInfo.parse(request, authorization)) {
            String nonce = generateNonce(request);
            String authenticateHeader = getAuthenticateHeader(nonce, false);
            return sendUnauthorizedError(response, authenticateHeader);
        }

        if (digestInfo.validate(request)) {
            // FIXME: maybe use a custom callback handler instead
            principal = digestInfo.authenticate(realm);
        }

        if (principal == null || digestInfo.isNonceStale()) {
            String nonce = generateNonce(request);
            boolean isNoncaneStale = principal != null && digestInfo.isNonceStale();
            String authenticateHeader = getAuthenticateHeader(nonce, isNoncaneStale);
            return sendUnauthorizedError(response, authenticateHeader);
        }

        try {
            handlePrincipalCallbacks(clientSubject, principal);
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


}

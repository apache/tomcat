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
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.MessagePolicy;
import javax.security.auth.message.callback.PasswordValidationCallback;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.codec.binary.Base64;

/**
 * This class implements JASPIC based HTTP BASIC authentication.
 */
public class BasicAuthModule extends TomcatAuthModule {

    private Class<?>[] supportedMessageTypes = new Class[] { HttpServletRequest.class,
            HttpServletResponse.class };


    public BasicAuthModule(Context context) {
        super(context);
    }


    @SuppressWarnings("rawtypes")
    @Override
    public void initializeModule(MessagePolicy requestPolicy, MessagePolicy responsePolicy,
            CallbackHandler handler, Map options) throws AuthException {
    }


    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject,
            Subject serviceSubject) throws AuthException {
        if (!isMandatory(messageInfo)) {
            return AuthStatus.SUCCESS;
        }

        HttpServletRequest request = (HttpServletRequest) messageInfo.getRequestMessage();
        HttpServletResponse response = (HttpServletResponse) messageInfo.getResponseMessage();
        String authorization = request.getHeader(AUTHORIZATION_HEADER);

        String realmName = getRealmName();

        if (authorization == null) {
            return sendUnauthorizedError(response, realmName);
        }

        BasicCredentials credentials = parseAuthorizationString(authorization);
        String username = credentials.getUsername();
        char[] password = credentials.getPassword().toCharArray();

        try {
            PasswordValidationCallback passwordCallback = new PasswordValidationCallback(
                    clientSubject, username, password);
            handler.handle(new Callback[] { passwordCallback });

            if (!passwordCallback.getResult()) {
                return sendUnauthorizedError(response, realmName);
            }
            handlePrincipalCallbacks(clientSubject, getPrincipal(passwordCallback));
            return AuthStatus.SUCCESS;

        } catch (Exception e) {
            throw new AuthException(e.getMessage());
        }
    }


    private AuthStatus sendUnauthorizedError(HttpServletResponse response, String realmName)
            throws AuthException {
        String authHeader = MessageFormat.format("Basic realm=\"{0}\"", realmName);
        response.setHeader(AUTH_HEADER_NAME, authHeader);
        try {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } catch (IOException e) {
            throw new AuthException(e.getMessage());
        }
        return AuthStatus.SEND_CONTINUE;
    }


    private GenericPrincipal getPrincipal(PasswordValidationCallback passwordCallback) {
        Iterator<Object> credentials = passwordCallback.getSubject().getPrivateCredentials()
                .iterator();
        return (GenericPrincipal) credentials.next();
    }


    private BasicCredentials parseAuthorizationString(String authorization) {
        MessageBytes authorizationBytes = MessageBytes.newInstance();
        authorizationBytes.setString(authorization);
        authorizationBytes.toBytes();
        ByteChunk authorizationBC = authorizationBytes.getByteChunk();
        return new BasicCredentials(authorizationBC);
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
     * Parser for an HTTP Authorization header for BASIC authentication as per
     * RFC 2617 section 2, and the Base64 encoded credentials as per RFC 2045
     * section 6.8.
     */
    protected static class BasicCredentials {

        // the only authentication method supported by this parser
        // note: we include single white space as its delimiter
        private static final String METHOD = "basic ";

        private ByteChunk authorization;
        private int initialOffset;
        private int base64blobOffset;
        private int base64blobLength;

        private String username = null;
        private String password = null;


        /**
         * Parse the HTTP Authorization header for BASIC authentication as per
         * RFC 2617 section 2, and the Base64 encoded credentials as per RFC
         * 2045 section 6.8.
         *
         * @param input The header value to parse in-place
         * @throws IllegalArgumentException If the header does not conform to
         *             RFC 2617
         */
        public BasicCredentials(ByteChunk input) throws IllegalArgumentException {
            authorization = input;
            initialOffset = input.getOffset();
            parseMethod();
            byte[] decoded = parseBase64();
            parseCredentials(decoded);
        }


        /**
         * Trivial accessor.
         *
         * @return the decoded username token as a String, which is never be
         *         <code>null</code>, but can be empty.
         */
        public String getUsername() {
            return username;
        }


        /**
         * Trivial accessor.
         *
         * @return the decoded password token as a String, or <code>null</code>
         *         if no password was found in the credentials.
         */
        public String getPassword() {
            return password;
        }


        /*
         * The authorization method string is case-insensitive and must have at
         * least one space character as a delimiter.
         */
        private void parseMethod() throws IllegalArgumentException {
            if (authorization.startsWithIgnoreCase(METHOD, 0)) {
                // step past the auth method name
                base64blobOffset = initialOffset + METHOD.length();
                base64blobLength = authorization.getLength() - METHOD.length();
            } else {
                // is this possible, or permitted?
                throw new IllegalArgumentException("Authorization header method is not \"Basic\"");
            }
        }


        /*
         * Decode the base64-user-pass token, which RFC 2617 states can be
         * longer than the 76 characters per line limit defined in RFC 2045. The
         * base64 decoder will ignore embedded line break characters as well as
         * surplus surrounding white space.
         */
        private byte[] parseBase64() throws IllegalArgumentException {
            byte[] decoded = Base64.decodeBase64(authorization.getBuffer(), base64blobOffset,
                    base64blobLength);
            // restore original offset
            authorization.setOffset(initialOffset);
            if (decoded == null) {
                throw new IllegalArgumentException("Basic Authorization credentials are not Base64");
            }
            return decoded;
        }


        /*
         * Extract the mandatory username token and separate it from the
         * optional password token. Tolerate surplus surrounding white space.
         */
        private void parseCredentials(byte[] decoded) throws IllegalArgumentException {

            int colon = -1;
            for (int i = 0; i < decoded.length; i++) {
                if (decoded[i] == ':') {
                    colon = i;
                    break;
                }
            }

            if (colon < 0) {
                username = new String(decoded, StandardCharsets.ISO_8859_1);
                // password will remain null!
            } else {
                username = new String(decoded, 0, colon, StandardCharsets.ISO_8859_1);
                password = new String(decoded, colon + 1, decoded.length - colon - 1,
                        StandardCharsets.ISO_8859_1);
                // tolerate surplus white space around credentials
                if (password.length() > 1) {
                    password = password.trim();
                }
            }
            // tolerate surplus white space around credentials
            if (username.length() > 1) {
                username = username.trim();
            }
        }
    }
}

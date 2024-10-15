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
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;

/**
 * An <b>Authenticator</b> and <b>Valve</b> implementation of HTTP BASIC Authentication, as outlined in RFC 7617: "The
 * 'Basic' HTTP Authentication Scheme"
 *
 * @author Craig R. McClanahan
 */
public class BasicAuthenticator extends AuthenticatorBase {

    private final Log log = LogFactory.getLog(BasicAuthenticator.class); // must not be static

    private Charset charset = StandardCharsets.UTF_8;
    private String charsetString = "UTF-8";


    public String getCharset() {
        return charsetString;
    }


    public void setCharset(String charsetString) {
        // Only acceptable options are null, "" or "UTF-8" (case insensitive)
        if (charsetString == null || charsetString.isEmpty()) {
            charset = StandardCharsets.ISO_8859_1;
        } else if ("UTF-8".equalsIgnoreCase(charsetString)) {
            charset = StandardCharsets.UTF_8;
        } else {
            throw new IllegalArgumentException(sm.getString("basicAuthenticator.invalidCharset"));
        }
        this.charsetString = charsetString;
    }


    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response) throws IOException {

        if (checkForCachedAuthentication(request, response, true)) {
            return true;
        }

        // Validate any credentials already included with this request
        MessageBytes authorization = request.getCoyoteRequest().getMimeHeaders().getValue("authorization");

        if (authorization != null) {
            authorization.toBytes();
            ByteChunk authorizationBC = authorization.getByteChunk();
            BasicCredentials credentials = null;
            try {
                credentials = new BasicCredentials(authorizationBC, charset);
                String username = credentials.getUsername();
                String password = credentials.getPassword();

                Principal principal = context.getRealm().authenticate(username, password);
                if (principal != null) {
                    register(request, response, principal, HttpServletRequest.BASIC_AUTH, username, password);
                    return true;
                }
            } catch (IllegalArgumentException iae) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("basicAuthenticator.invalidAuthorization", iae.getMessage()));
                }
            }
        }

        // the request could not be authenticated, so reissue the challenge
        StringBuilder value = new StringBuilder(16);
        value.append("Basic realm=\"");
        value.append(getRealmName(context));
        value.append('\"');
        if (charsetString != null && !charsetString.isEmpty()) {
            value.append(", charset=");
            value.append(charsetString);
        }
        response.setHeader(AUTH_HEADER_NAME, value.toString());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;

    }


    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.BASIC_AUTH;
    }


    @Override
    protected boolean isPreemptiveAuthPossible(Request request) {
        MessageBytes authorizationHeader = request.getCoyoteRequest().getMimeHeaders().getValue("authorization");
        return authorizationHeader != null && authorizationHeader.startsWithIgnoreCase("basic ", 0);
    }


    /**
     * Parser for an HTTP Authorization header for BASIC authentication as per RFC 2617 section 2, and the Base64
     * encoded credentials as per RFC 2045 section 6.8.
     */
    public static class BasicCredentials {

        // the only authentication method supported by this parser
        // note: we include single white space as its delimiter
        private static final String METHOD = "basic ";

        private final Charset charset;
        private final ByteChunk authorization;
        private final int initialOffset;
        private int base64blobOffset;
        private int base64blobLength;

        private String username = null;
        private String password = null;

        /**
         * Parse the HTTP Authorization header for BASIC authentication as per RFC 7617.
         *
         * @param input   The header value to parse in-place
         * @param charset The character set to use to convert the bytes to a string
         *
         * @throws IllegalArgumentException If the header does not conform to RFC 7617
         */
        public BasicCredentials(ByteChunk input, Charset charset) throws IllegalArgumentException {
            authorization = input;
            initialOffset = input.getStart();
            this.charset = charset;

            parseMethod();
            byte[] decoded = parseBase64();
            parseCredentials(decoded);
        }

        /**
         * Trivial accessor.
         *
         * @return the decoded username token as a String, which is never be <code>null</code>, but can be empty.
         */
        public String getUsername() {
            return username;
        }

        /**
         * Trivial accessor.
         *
         * @return the decoded password token as a String, or <code>null</code> if no password was found in the
         *             credentials.
         */
        public String getPassword() {
            return password;
        }

        /*
         * The authorization method string is case-insensitive and must have exactly one space character as a delimiter.
         */
        private void parseMethod() throws IllegalArgumentException {
            if (authorization.startsWithIgnoreCase(METHOD, 0)) {
                // step past the auth method name
                base64blobOffset = initialOffset + METHOD.length();
                base64blobLength = authorization.getLength() - METHOD.length();
            } else {
                // is this possible, or permitted?
                throw new IllegalArgumentException(sm.getString("basicAuthenticator.notBasic"));
            }
        }

        /*
         * Decode the base64-user-pass token, which RFC 2617 states can be longer than the 76 characters per line limit
         * defined in RFC 2045. The base64 decoder will ignore embedded line break characters as well as surplus
         * surrounding white space.
         */
        private byte[] parseBase64() throws IllegalArgumentException {
            byte[] encoded = new byte[base64blobLength];
            System.arraycopy(authorization.getBuffer(), base64blobOffset, encoded, 0, base64blobLength);
            byte[] decoded = Base64.getDecoder().decode(encoded);
            // restore original offset
            authorization.setStart(initialOffset);
            if (decoded == null) {
                throw new IllegalArgumentException(sm.getString("basicAuthenticator.notBase64"));
            }
            return decoded;
        }

        /*
         * Extract the mandatory username token and separate it from the optional password token. Tolerate surplus
         * surrounding white space.
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
                username = new String(decoded, charset);
                // password will remain null!
            } else {
                username = new String(decoded, 0, colon, charset);
                password = new String(decoded, colon + 1, decoded.length - colon - 1, charset);
            }
        }
    }
}

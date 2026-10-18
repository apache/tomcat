/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.apache.tomcat.util.res.StringManager;

/**
 * Authenticator supporting the BASIC authentication method.
 */
public class BasicAuthenticator extends Authenticator {

    private static final StringManager sm = StringManager.getManager(BasicAuthenticator.class);

    /** Default constructor. */
    public BasicAuthenticator() { }

    /** The name of the Basic authentication scheme. */
    public static final String schemeName = "basic";
    /** The charset parameter name in the authenticate header. */
    public static final String charsetparam = "charset";

    @Override
    public String getAuthorization(String method, String requestUri, String authenticateHeader, String userName,
            String userPassword, String userRealm) throws AuthenticationException {

        validateUsername(userName);
        validatePassword(userPassword);

        Map<String,String> parameterMap = parseAuthenticateHeader(authenticateHeader);
        String realm = parameterMap.get("realm");

        validateRealm(userRealm, realm);

        String userPass = userName + ":" + userPassword;

        /*
         * RFC 7617 section 2.1 leaves the default character encoding undefined. Use ISO-8859-1, the default used by
         * Tomcat servers of this branch, when the challenge does not specify a charset. UTF-8 is the only value the
         * RFC allows for the charset parameter, reserving any other value for future use, so use UTF-8 when the
         * challenge asks for it and reject challenges that ask for any other encoding rather than silently guessing
         * one.
         */
        String charsetParam = parameterMap.get(charsetparam);
        Charset charset = StandardCharsets.ISO_8859_1;
        if (charsetParam != null) {
            if (charsetParam.equalsIgnoreCase("UTF-8")) {
                charset = StandardCharsets.UTF_8;
            } else {
                throw new AuthenticationException(sm.getString("basicAuthenticator.invalidCharset", charsetParam));
            }
        }

        String base64 = Base64.getEncoder().encodeToString(userPass.getBytes(charset));

        return " Basic " + base64;
    }

    @Override
    public String getSchemeName() {
        return schemeName;
    }

}

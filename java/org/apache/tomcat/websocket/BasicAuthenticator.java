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
         * RFC 7617 section 2.1 leaves the default character encoding undefined and only allows UTF-8 as the value of
         * the charset parameter, reserving any other value for future use. Use UTF-8 by default and reject challenges
         * that ask for any other encoding rather than silently guessing one.
         */
        String charsetParam = parameterMap.get(charsetparam);
        if (charsetParam != null && !charsetParam.equalsIgnoreCase("UTF-8")) {
            throw new AuthenticationException(sm.getString("basicAuthenticator.invalidCharset", charsetParam));
        }

        String base64 = Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.UTF_8));

        return " Basic " + base64;
    }

    @Override
    public String getSchemeName() {
        return schemeName;
    }

}

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

/**
 * Authenticator supporting the BASIC authentication method.
 */
public class BasicAuthenticator extends Authenticator {

    public static final String schemeName = "basic";
    public static final String charsetparam = "charset";

    @Override
    public String getAuthorization(String requestUri, String authenticateHeader, String userName, String userPassword,
            String userRealm) throws AuthenticationException {

        validateUsername(userName);
        validatePassword(userPassword);

        Map<String, String> parameterMap = parseAuthenticateHeader(authenticateHeader);
        String realm = parameterMap.get("realm");

        validateRealm(userRealm, realm);

        String userPass = userName + ":" + userPassword;
        Charset charset;

        if (parameterMap.get(charsetparam) != null
                && parameterMap.get(charsetparam).equalsIgnoreCase("UTF-8")) {
            charset = StandardCharsets.UTF_8;
        } else {
            charset = StandardCharsets.ISO_8859_1;
        }

        String base64 = Base64.getEncoder().encodeToString(userPass.getBytes(charset));

        return " Basic " + base64;
    }

    @Override
    public String getSchemeName() {
        return schemeName;
    }

}

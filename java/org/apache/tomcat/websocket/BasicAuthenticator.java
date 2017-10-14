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
import java.util.Map;

import org.apache.tomcat.util.codec.binary.Base64;

/**
 * Authenticator supporting the BASIC auth method.
 */
public class BasicAuthenticator extends Authenticator {

    public static final String schemeName = "basic";
    public static final String charsetparam = "charset";

    @Override
    public String getAuthorization(String requestUri, String WWWAuthenticate,
            Map<String, Object> userProperties) throws AuthenticationException {

        String userName = (String) userProperties.get(Constants.WS_AUTHENTICATION_USER_NAME);
        String password = (String) userProperties.get(Constants.WS_AUTHENTICATION_PASSWORD);

        if (userName == null || password == null) {
            throw new AuthenticationException(
                    "Failed to perform Basic authentication due to  missing user/password");
        }

        Map<String, String> wwwAuthenticate = parseWWWAuthenticateHeader(WWWAuthenticate);

        String userPass = userName + ":" + password;
        Charset charset;

        if (wwwAuthenticate.get(charsetparam) != null
                && wwwAuthenticate.get(charsetparam).equalsIgnoreCase("UTF-8")) {
            charset = StandardCharsets.UTF_8;
        } else {
            charset = StandardCharsets.ISO_8859_1;
        }

        String base64 = Base64.encodeBase64String(userPass.getBytes(charset));

        return " Basic " + base64;
    }

    @Override
    public String getSchemeName() {
        return schemeName;
    }

}

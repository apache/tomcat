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

import jakarta.servlet.ServletException;
import org.apache.catalina.connector.Request;

/**
 * A base class for the <b>Authenticator</b> and <b>Valve</b> implementations that do not allow login via username and
 * password. Logout operation for those implementations is also forbidden.
 */
public abstract class LoginlessAuthenticatorBase extends AuthenticatorBase {

    @Override
    public void login(String username, String password, Request request) throws ServletException {
        throw new ServletException("Authenticator does not support login operation");
    }

    @Override
    public void logout(Request request) {
        throw new UnsupportedOperationException("Authenticator does not support logout operation");
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.web.tomcat;

import java.io.IOException;
import java.security.Principal;

import jakarta.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;


/**
 * Valve which sets the UserPrincipal into a ThreadLocal
 * to make it injectable via a CDI Producer.
 */
public class OpenWebBeansSecurityValve extends ValveBase {

    private static ThreadLocal<Principal> principal = new ThreadLocal<>();

    public static Principal getPrincipal() {
        return principal.get();
    }

    @Override
    public void invoke(Request request, Response response)
            throws IOException, ServletException {
        Principal p = request.getUserPrincipal();
        try {
            if (p != null) {
                principal.set(p);
            }
            getNext().invoke(request, response);
        } finally {
            if (p != null) {
                principal.remove();
            }
        }
    }

}

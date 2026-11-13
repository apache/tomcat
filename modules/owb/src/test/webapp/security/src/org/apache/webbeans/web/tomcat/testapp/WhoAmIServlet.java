/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.webbeans.web.tomcat.testapp;

import java.io.IOException;
import java.security.Principal;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.spi.SecurityService;
import org.apache.webbeans.web.tomcat.OpenWebBeansSecurityValve;

@WebServlet(urlPatterns = { "/whoami", "/security-service-principal-type", "/security-service-principal-custom" })
public class WhoAmIServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private Principal principal;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getServletPath();
        response.setContentType("text/plain");

        if ("/security-service-principal-type".equals(path)) {
            Principal current = securityService().getCurrentPrincipal();
            response.getWriter().println(current.getClass().getName());
            return;
        }
        if ("/security-service-principal-custom".equals(path)) {
            Principal current = securityService().getCurrentPrincipal();
            response.getWriter().println(current instanceof CustomPrincipal ? "custom" : "not-custom");
            return;
        }

        Principal valvePrincipal = OpenWebBeansSecurityValve.getPrincipal();
        String valveName = valvePrincipal == null ? "null" : valvePrincipal.getName();
        response.getWriter().println(principal.getName() + "|" + valveName);
    }

    private static SecurityService securityService() {
        return WebBeansContext.currentInstance().getSecurityService();
    }
}

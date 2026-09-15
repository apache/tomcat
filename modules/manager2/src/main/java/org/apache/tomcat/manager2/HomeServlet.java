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
package org.apache.tomcat.manager2;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Serves the SPA entry point and the SPA deep-link routes (e.g. {@code /apps}, {@code /hosts}, {@code /monitoring},
 * {@code /diagnostics}) which have no server-side resource of their own.
 * <p>
 * Unauthenticated visitors are forwarded to the login page. Authenticated users are forwarded to the SPA shell
 * ({@code /index.html}) so that the browser keeps the requested URL (deep links survive a reload).
 * <p>
 * The SPA shell itself is deliberately <em>not</em> protected with a security constraint: a constraint on {@code /}
 * would match every request in the context (including the CSS and JS that the login page needs) and would poison the
 * FORM authentication "saved request" with asset URLs. Access control for the data is enforced by the constraints on
 * {@code /api/*}.
 */
public class HomeServlet extends HttpServlet {


    private static final long serialVersionUID = 1L;


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (request.getServletPath().isEmpty() && !request.getRequestURI().endsWith("/")) {
            // The request targets the context root without a trailing slash
            // (e.g. /manager2). Redirect to the trailing-slash form so that
            // the browser resolves this application's relative URLs (CSS,
            // JS, images) against the context instead of the server root.
            // The mapper's own context root redirect
            // (Context#setMapperContextRootRedirectEnabled) cannot do this:
            // it only applies when no servlet is mapped to the context
            // root, but this servlet is (via the empty URL pattern, which
            // the mapper registers as the exact match "/").
            response.sendRedirect(request.getContextPath() + "/");
            return;
        }

        if (request.getUserPrincipal() != null) {
            request.getRequestDispatcher("/index.html").forward(request, response);
        } else {
            request.getRequestDispatcher("/login").forward(request, response);
        }
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}

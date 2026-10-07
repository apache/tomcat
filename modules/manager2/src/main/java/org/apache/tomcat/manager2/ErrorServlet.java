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

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Renders the 403 and 404 error pages. The pages are rendered from their templates (instead of being served as static
 * resources) so that a {@code <base>} element can be injected: error pages can be displayed at arbitrary URLs (the
 * browser keeps the URL of the failed request) and the relative link to the CSS would otherwise break.
 */
public class ErrorServlet extends HttpServlet {


    private static final long serialVersionUID = 1L;

    private static final String TEMPLATE_403 = "/error-403.html";
    private static final String TEMPLATE_404 = "/error-404.html";


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        int status = HttpServletResponse.SC_NOT_FOUND;
        Object errorStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (errorStatus instanceof Integer code) {
            status = code;
        }

        String template = Html.readTemplate(getServletContext(),
                status == HttpServletResponse.SC_FORBIDDEN ? TEMPLATE_403 : TEMPLATE_404);
        if (template == null) {
            return; // Let the container render the default error page.
        }

        Html.render(request, response, template);
    }
}

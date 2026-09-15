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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * Renders the static HTML pages that are served by a servlet (the login page and the error pages) instead of by the
 * default servlet. The pages are rendered from their templates so that a {@code <base>} element can be injected. The
 * base element is required because these pages can be displayed at arbitrary URLs (the browser keeps the URL of the
 * request that triggered the forward to the page), which would break the relative links to the CSS.
 */
final class Html {


    /**
     * Placeholder for the {@code <base>} element in the HTML templates. It must be on a line of its own inside the
     * {@code <head>} element.
     */
    static final String BASE_PLACEHOLDER = "<!-- MANAGER2_BASE -->";


    private Html() {
        // Utility class
    }


    /**
     * Read a template from the web application.
     *
     * @param context the servlet context
     * @param path    the context-relative template path, e.g. {@code /login.html}
     * 
     * @return the template content, or {@code null} if it is missing
     */
    static String readTemplate(ServletContext context, String path) {
        try (InputStream is = context.getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }


    /**
     * Render a template: inject the base element and write the result to the response.
     *
     * @param request  the current request (used for the context path)
     * @param response the response to write
     * @param template the template content
     * 
     * @throws IOException if writing the response fails
     */
    static void render(HttpServletRequest request, HttpServletResponse response, String template) throws IOException {
        String html = template.replace(BASE_PLACEHOLDER, "<base href=\"" + request.getContextPath() + "/\">");
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(html);
    }
}

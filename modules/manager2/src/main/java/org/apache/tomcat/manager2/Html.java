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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.res.StringManager;


/**
 * Renders the static HTML pages that are served by a servlet (the SPA shell, the login page and the error pages)
 * instead of by the default servlet. The pages are rendered from their templates so that a {@code <base>} element can
 * be injected. The base element is required because these pages can be displayed at arbitrary URLs (the browser keeps
 * the URL of the request that triggered the forward to the page), which would break the relative links to the CSS.
 * <p>
 * Rendering also localizes the templates: {@code #{key}} tokens are replaced with the messages of the
 * {@link StringManager} matching the locales sent by the client, and the {@code {{lang}}} token with the language tag
 * of that {@link StringManager}'s locale.
 */
final class Html {


    /**
     * Placeholder for the {@code <base>} element in the HTML templates. It must be on a line of its own inside the
     * {@code <head>} element.
     */
    static final String BASE_PLACEHOLDER = "<!-- MANAGER2_BASE -->";

    /**
     * Token in the HTML templates that is replaced with the localized messages identified by the key inside the
     * braces, e.g. {@code #{manager2.ui.login.title}}.
     */
    static final Pattern MESSAGE_TOKEN = Pattern.compile("#\\{([^{}\\s]+)\\}");

    /**
     * Token in the HTML templates that is replaced with the language tag of the locale of the response, for the
     * {@code lang} attribute of the {@code <html>} element.
     */
    static final String LANG_TOKEN = "{{lang}}";


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
     * Render a template: localize it with the locales of the request, inject the base element and write the result to
     * the response.
     *
     * @param request  the current request (used for the context path and the locales)
     * @param response the response to write
     * @param template the template content
     *
     * @throws IOException if writing the response fails
     */
    static void render(HttpServletRequest request, HttpServletResponse response, String template) throws IOException {
        render(request, response, template, Strings.manager(request));
    }


    /**
     * Render a template: localize it with the given string manager, inject the base element and write the result to
     * the response.
     *
     * @param request  the current request (used for the context path)
     * @param response the response to write
     * @param template the template content
     * @param sm       the string manager holding the localized messages
     *
     * @throws IOException if writing the response fails
     */
    static void render(HttpServletRequest request, HttpServletResponse response, String template,
            StringManager sm) throws IOException {

        String html = localize(template, sm);
        html = html.replace(BASE_PLACEHOLDER, "<base href=\"" + request.getContextPath() + "/\">");
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(html);
    }


    /**
     * Replace the message and language tokens of a template with localized values.
     *
     * @param template the template content
     * @param sm       the string manager holding the localized messages
     *
     * @return the localized template
     */
    static String localize(String template, StringManager sm) {
        Matcher matcher = MESSAGE_TOKEN.matcher(template);
        StringBuilder result = new StringBuilder(template.length());
        while (matcher.find()) {
            String message = sm.getString(matcher.group(1));
            // Unknown keys are left as-is so that they stand out in the page
            String replacement = message == null ? matcher.group() : escapeHtml(message);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString().replace(LANG_TOKEN, sm.getLocale().toLanguageTag());
    }


    private static String escapeHtml(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '"' -> result.append("&quot;");
                default -> result.append(c);
            }
        }
        return result.toString();
    }
}

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
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.res.StringManager;


/**
 * Serves the client-side messages of this web application (the {@code manager2.ui.*} keys of the
 * {@code LocalStrings} bundle) as JSON, localized with the locales the browser sends in its {@code Accept-Language}
 * header, so that the scripts localize the interface with the same bundle as the server side.
 * <p>
 * The endpoint is deliberately outside {@code /api/*}: it is not protected by a security constraint (the login page
 * needs it too) and it is not covered by the CSRF filter. It exposes only message templates from the bundle, never
 * server data, and it answers GET requests only.
 */
public class I18nServlet extends HttpServlet implements Servlet {


    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * Prefix of the keys of the {@code LocalStrings} bundle that the client renders.
     */
    static final String CLIENT_KEY_PREFIX = "manager2.ui.";


    /**
     * The keys to serve, sorted, read once from the base bundle.
     */
    private volatile List<String> keys;

    /**
     * The rendered JSON payloads, keyed by the (internally cached) string managers. Bounded, since the string manager
     * cache itself is bounded.
     */
    private final Map<StringManager,String> payloadCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Serial
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<StringManager,String> eldest) {
                    return size() > 16;
                }
            });


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        StringManager sm = Strings.manager(request);
        String body = payloadCache.computeIfAbsent(sm, this::buildPayload);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json; charset=" + Constants.CHARSET);
        response.setCharacterEncoding(Constants.CHARSET);
        response.setHeader("Cache-Control", "private, max-age=3600");
        response.setHeader("Vary", "Accept-Language");
        response.setContentLength(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        response.getWriter().print(body);
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }


    private String buildPayload(StringManager sm) {
        Map<String, Object> messages = new LinkedHashMap<>();
        for (String key : clientKeys()) {
            String message = sm.getString(key);
            if (message != null) {
                messages.put(key, message);
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("locale", sm.getLocale().toLanguageTag());
        payload.put("messages", messages);
        return Json.write(payload);
    }


    private List<String> clientKeys() {
        List<String> result = keys;
        if (result == null) {
            result = new ArrayList<>(Strings.keys(CLIENT_KEY_PREFIX));
            result.sort(String::compareTo);
            keys = result;
        }
        return result;
    }
}

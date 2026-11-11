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
package org.apache.coyote;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responds with status 304, a Content-Security-Policy header that is listed in the request attribute
 * {@link Constants#NOT_MODIFIED_EXCLUDED_HEADERS_ATTR}, and an X-Kept header that is not listed.
 */
public class TesterNotModifiedExcludedHeaderServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String POLICY = "script-src 'nonce-a'";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        resp.setHeader("Content-Security-Policy", POLICY);
        resp.setHeader("X-Kept", "kept");
        req.setAttribute(Constants.NOT_MODIFIED_EXCLUDED_HEADERS_ATTR,
                List.of(Map.entry("Content-Security-Policy", POLICY)));
        resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
    }
}

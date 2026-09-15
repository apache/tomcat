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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;


/**
 * Shared helpers for writing the JSON responses of the Manager2 API.
 */
public final class Api {


    /**
     * Write an arbitrary JSON payload (a {@code Map} produced by the API layer).
     *
     * @param response the servlet response
     * @param payload  the JSON payload
     * 
     * @throws IOException if a write error occurs
     */
    public static void json(HttpServletResponse response, Object payload) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        headers(response);
        String body = Json.write(payload);
        // Set the content length so that the response is never sent with
        // chunked transfer encoding, which breaks simple HTTP clients.
        response.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
        response.getWriter().print(body);
    }


    /**
     * Write a successful mutation result envelope.
     *
     * @param response the servlet response
     * @param message  the (localized) result message
     * 
     * @throws IOException if a write error occurs
     */
    public static void ok(HttpServletResponse response, String message) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", Boolean.TRUE);
        payload.put("message", message);
        json(response, payload);
    }


    /**
     * Write an error envelope.
     *
     * @param response the servlet response
     * @param status   the HTTP status code
     * @param code     the machine readable error code
     * @param message  the (localized) error message
     * 
     * @throws IOException if a write error occurs
     */
    public static void error(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        headers(response);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", Boolean.FALSE);
        payload.put("error", code);
        payload.put("message", message);
        String body = Json.write(payload);
        response.setContentLength(body.getBytes(StandardCharsets.UTF_8).length);
        response.getWriter().print(body);
    }


    /**
     * Write a 404 error envelope.
     *
     * @param response the servlet response
     * 
     * @throws IOException if a write error occurs
     */
    public static void notFound(HttpServletResponse response) throws IOException {
        error(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Resource not found");
    }


    private static void headers(HttpServletResponse response) {
        response.setContentType("application/json; charset=" + Constants.CHARSET);
        response.setCharacterEncoding(Constants.CHARSET);
        response.setHeader("Cache-Control", "no-store");
    }


    private Api() {
        // Utility class, do not instantiate
    }
}

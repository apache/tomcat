/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.websocket.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.HandshakeRequest;

/**
 * Represents the request that this session was opened under.
 */
public class WsHandshakeRequest implements HandshakeRequest {

    private volatile HttpServletRequest request;

    public WsHandshakeRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public URI getRequestURI() {
        validate();
        // Calculate every time as only likely to be zero or one calls
        String queryString = request.getQueryString();

        StringBuffer sb = request.getRequestURL();
        if (queryString != null) {
            sb.append("?");
            sb.append(queryString);
        }
        URI requestURI;
        try {
            requestURI = new URI(sb.toString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        return requestURI;
    }

    @Override
    public Map<String,List<String>> getParameterMap() {
        validate();

        Map<String,String[]> originalParameters = request.getParameterMap();
        Map<String,List<String>> newParameters =
                new HashMap<>(originalParameters.size());
        for (Entry<String,String[]> entry : originalParameters.entrySet()) {
            newParameters.put(entry.getKey(),
                    Collections.unmodifiableList(
                            Arrays.asList(entry.getValue())));
        }

        return Collections.unmodifiableMap(newParameters);
    }

    @Override
    public String getQueryString() {
        validate();
        return request.getQueryString();
    }

    @Override
    public Principal getUserPrincipal() {
        validate();
        return request.getUserPrincipal();
    }

    @Override
    public Map<String,List<String>> getHeaders() {
        validate();

        Map<String,List<String>> newHeaders = new HashMap<>();

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            newHeaders.put(headerName, Collections.unmodifiableList(
                    Collections.list(request.getHeaders(headerName))));
        }

        return Collections.unmodifiableMap(newHeaders);
    }

    @Override
    public boolean isUserInRole(String role) {
        validate();
        return request.isUserInRole(role);
    }

    @Override
    public Object getHttpSession() {
        validate();
        return request.getSession(false);
    }

    /**
     * Called when the HandshakeRequest is no longer required. Since an instance
     * of this class retains a reference to the current HttpServletRequest that
     * reference needs to be cleared as the HttpServletRequest may be reused.
     *
     * There is no reason for instances of this class to be accessed once the
     * handshake has been completed.
     */
    void finished() {
        request = null;
    }

    private void validate() {
        if (request == null) {
            throw new IllegalStateException();
        }
    }
}

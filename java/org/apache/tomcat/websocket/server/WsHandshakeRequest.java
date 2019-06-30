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

import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.CaseInsensitiveKeyMap;

/**
 * Represents the request that this session was opened under.
 */
public class WsHandshakeRequest implements HandshakeRequest {

    private static final StringManager sm = StringManager.getManager(WsHandshakeRequest.class);

    private final URI requestUri;
    private final Map<String,List<String>> parameterMap;
    private final String queryString;
    private final Principal userPrincipal;
    private final Map<String,List<String>> headers;
    private final Object httpSession;

    private volatile HttpServletRequest request;


    public WsHandshakeRequest(HttpServletRequest request, Map<String,String> pathParams) {

        this.request = request;

        queryString = request.getQueryString();
        userPrincipal = request.getUserPrincipal();
        httpSession = request.getSession(false);
        requestUri = buildRequestUri(request);

        // ParameterMap
        Map<String,String[]> originalParameters = request.getParameterMap();
        Map<String,List<String>> newParameters =
                new HashMap<String, List<String>>(originalParameters.size());
        for (Entry<String,String[]> entry : originalParameters.entrySet()) {
            newParameters.put(entry.getKey(),
                    Collections.unmodifiableList(
                            Arrays.asList(entry.getValue())));
        }
        for (Entry<String,String> entry : pathParams.entrySet()) {
            newParameters.put(entry.getKey(), Collections.singletonList(entry.getValue()));
        }
        parameterMap = Collections.unmodifiableMap(newParameters);

        // Headers
        Map<String,List<String>> newHeaders = new CaseInsensitiveKeyMap<List<String>>();

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            newHeaders.put(headerName, Collections.unmodifiableList(
                    Collections.list(request.getHeaders(headerName))));
        }

        headers = Collections.unmodifiableMap(newHeaders);
    }

    @Override
    public URI getRequestURI() {
        return requestUri;
    }

    @Override
    public Map<String,List<String>> getParameterMap() {
        return parameterMap;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    @Override
    public Map<String,List<String>> getHeaders() {
        return headers;
    }

    @Override
    public boolean isUserInRole(String role) {
        if (request == null) {
            throw new IllegalStateException();
        }

        return request.isUserInRole(role);
    }

    @Override
    public Object getHttpSession() {
        return httpSession;
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


    /*
     * See RequestUtil.getRequestURL()
     */
    private static URI buildRequestUri(HttpServletRequest req) {

        StringBuffer uri = new StringBuffer();
        String scheme = req.getScheme();
        int port = req.getServerPort();
        if (port < 0) {
            // Work around java.net.URL bug
            port = 80;
        }

        if ("http".equals(scheme)) {
            uri.append("ws");
        } else if ("https".equals(scheme)) {
            uri.append("wss");
        } else {
            // Should never happen
            throw new IllegalArgumentException(
                    sm.getString("wsHandshakeRequest.unknownScheme", scheme));
        }

        uri.append("://");
        uri.append(req.getServerName());

        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            uri.append(':');
            uri.append(port);
        }

        uri.append(req.getRequestURI());

        if (req.getQueryString() != null) {
            uri.append("?");
            uri.append(req.getQueryString());
        }

        try {
            return new URI(uri.toString());
        } catch (URISyntaxException e) {
            // Should never happen
            throw new IllegalArgumentException(
                    sm.getString("wsHandshakeRequest.invalidUri", uri.toString()), e);
        }
    }
}

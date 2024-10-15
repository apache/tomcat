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

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.GenericFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles the initial HTTP connection for WebSocket connections.
 */
public class WsFilter extends GenericFilter {

    private static final long serialVersionUID = 1L;

    private transient WsServerContainer sc;


    @Override
    public void init() throws ServletException {
        sc = (WsServerContainer) getServletContext().getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // This filter only needs to handle WebSocket upgrade requests
        if (!sc.areEndpointsRegistered() || !UpgradeUtil.isWebSocketUpgradeRequest(request, response)) {
            chain.doFilter(request, response);
            return;
        }

        // HTTP request with an upgrade header for WebSocket present
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // Check to see if this WebSocket implementation has a matching mapping
        String path;
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            path = req.getServletPath();
        } else {
            path = req.getServletPath() + pathInfo;
        }
        WsMappingResult mappingResult = sc.findMapping(path);

        if (mappingResult == null) {
            // No endpoint registered for the requested path. Let the
            // application handle it (it might redirect or forward for example)
            chain.doFilter(request, response);
            return;
        }

        UpgradeUtil.doUpgrade(sc, req, resp, mappingResult.getConfig(), mappingResult.getPathParams());
    }
}

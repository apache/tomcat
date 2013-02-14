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
package org.apache.tomcat.websocket;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Represents the request that this session was opened under.
 */
public class WsRequest {

    private final URI requestURI;
    private final Map<String,List<String>> parameterMap;
    private final String queryString;
    private final Principal userPrincipal;

    public WsRequest(URI requestURI, Map<String,List<String>> parameterMap,
            String queryString, Principal userPrincipal) {
        this.requestURI = requestURI;
        this.parameterMap = parameterMap;
        this.queryString = queryString;
        this.userPrincipal = userPrincipal;
    }

    public URI getRequestURI() {
        return requestURI;
    }

    public Map<String,List<String>> getRequestParameterMap() {
        return parameterMap;
    }

    public String getQueryString() {
        return queryString;
    }

    public Principal getUserPrincipal() {
        return userPrincipal;
    }
}

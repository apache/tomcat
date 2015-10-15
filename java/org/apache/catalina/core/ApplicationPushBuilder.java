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
package org.apache.catalina.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.PushBuilder;

import org.apache.catalina.connector.Request;
import org.apache.coyote.ActionCode;
import org.apache.tomcat.util.collections.CaseInsensitiveKeyMap;
import org.apache.tomcat.util.res.StringManager;

public class ApplicationPushBuilder implements PushBuilder {

    private static final StringManager sm = StringManager.getManager(ApplicationPushBuilder.class);

    private final HttpServletRequest baseRequest;
    private final org.apache.coyote.Request coyoteRequest;

    private String method = "GET";
    private String path;
    private Map<String,List<String>> headers = new CaseInsensitiveKeyMap<>();

    public ApplicationPushBuilder(HttpServletRequest request) {
        baseRequest = request;
        // Need a reference to the CoyoteRequest in order to process the push
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper) {
            current = ((ServletRequestWrapper) current).getRequest();
        }
        if (current instanceof Request) {
            coyoteRequest = ((Request) current).getCoyoteRequest();
        } else {
            throw new UnsupportedOperationException(sm.getString(
                    "applicationPushBuilder.noCoyoteRequest", current.getClass().getName()));
        }

        // Populate the initial list of HTTP headers
        // TODO Servlet 4.0
        //      Filter headers as required by Servlet spec
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            List<String> values = new ArrayList<>();
            headers.put(headerName, values);
            Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                values.add(headerValues.nextElement());
            }
        }
    }


    @Override
    public PushBuilder path(String path) {
        if (path.startsWith("/")) {
            this.path = path;
        } else {
            String contextPath = baseRequest.getContextPath();
            int len = contextPath.length() + path.length() + 1;
            StringBuilder sb = new StringBuilder(len);
            sb.append(contextPath);
            sb.append('/');
            sb.append(path);
            this.path = sb.toString();
        }
        return this;
    }


    @Override
    public PushBuilder method(String method) {
        this.method = method;
        return this;
    }


    @Override
    public String getMethod() {
        return method;
    }


    @Override
    public PushBuilder addHeader(String name, String value) {
        List<String> values = headers.get(name);
        if (values == null) {
            values = new ArrayList<>();
            headers.put(name, values);
        }
        values.add(value);

        return this;
    }


    @Override
    public PushBuilder setHeader(String name, String value) {
        List<String> values = headers.get(name);
        if (values == null) {
            values = new ArrayList<>();
            headers.put(name, values);
        } else {
            values.clear();
        }
        values.add(value);

        return this;
    }


    @Override
    public PushBuilder removeHeader(String name) {
        headers.remove(name);

        return this;
    }


    @Override
    public Set<String> getHeaderNames() {
        return Collections.unmodifiableSet(headers.keySet());
    }


    @Override
    public String getHeader(String name) {
        List<String> values = headers.get(name);
        if (values == null) {
            return null;
        } else {
            return values.get(0);
        }
    }


    @Override
    public void push() {
        if (path == null) {
            throw new IllegalStateException(sm.getString("pushBuilder.noPath"));
        }

        org.apache.coyote.Request pushTarget = new org.apache.coyote.Request();

        pushTarget.method().setString(method);
        // The next three are implied by the Javadoc getPath()
        pushTarget.serverName().setString(baseRequest.getServerName());
        pushTarget.setServerPort(baseRequest.getServerPort());
        pushTarget.scheme().setString(baseRequest.getScheme());

        pushTarget.requestURI().setString(path);
        pushTarget.decodedURI().setString(path);

        // TODO Copy headers
        // TODO Implement other required attributes
        // TODO Copy across / set other required attributes

        coyoteRequest.action(ActionCode.PUSH_REQUEST, pushTarget);

        // Reset for next call to this method
        pushTarget = null;
        path = null;
    }
}

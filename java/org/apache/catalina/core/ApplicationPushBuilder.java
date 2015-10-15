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

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.PushBuilder;

import org.apache.catalina.connector.Request;
import org.apache.coyote.ActionCode;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.collections.CaseInsensitiveKeyMap;
import org.apache.tomcat.util.res.StringManager;

public class ApplicationPushBuilder implements PushBuilder {

    private static final StringManager sm = StringManager.getManager(ApplicationPushBuilder.class);

    private final HttpServletRequest baseRequest;
    private final org.apache.coyote.Request coyoteRequest;

    private String method = "GET";
    private Map<String,List<String>> headers = new CaseInsensitiveKeyMap<>();
    private String path;
    private String etag;
    private String lastModified;
    private String queryString;
    private String sessionId;
    private boolean addSessionCookie;
    private boolean addSessionPathParameter;
    private boolean conditional;

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

        // Remove the headers
        headers.remove("if-match");
        headers.remove("if-none-match");
        headers.remove("if-modified-since");
        headers.remove("if-unmodified-since");
        headers.remove("if-range");
        headers.remove("range");
        headers.remove("expect");
        headers.remove("authorization");
        headers.remove("referer");

        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionId = session.getId();
        }
        if (sessionId == null) {
            sessionId = request.getRequestedSessionId();
        }
        addSessionCookie = request.isRequestedSessionIdFromCookie();
        addSessionPathParameter = request.isRequestedSessionIdFromURL();
        if (!addSessionCookie && !addSessionPathParameter && sessionId != null) {
            Set<SessionTrackingMode> sessionTrackingModes =
                    request.getServletContext().getEffectiveSessionTrackingModes();
            addSessionCookie = sessionTrackingModes.contains(SessionTrackingMode.COOKIE);
            addSessionPathParameter = sessionTrackingModes.contains(SessionTrackingMode.URL);
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
    public String getPath() {
        return path;
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
    public PushBuilder etag(String etag) {
        this.etag = etag;
        return this;
    }


    @Override
    public String getEtag() {
        return etag;
    }


    @Override
    public PushBuilder lastModified(String lastModified) {
        this.lastModified = lastModified;
        return this;
    }


    @Override
    public String getLastModified() {
        return lastModified;
    }


    @Override
    public PushBuilder queryString(String queryString) {
        this.queryString = queryString;
        return this;
    }


    @Override
    public String getQueryString() {
        return queryString;
    }


    @Override
    public PushBuilder sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }


    @Override
    public String getSessionId() {
        return sessionId;
    }


    @Override
    public PushBuilder conditional(boolean conditional) {
        this.conditional = conditional;
        return this;
    }


    @Override
    public boolean isConditional() {
        return conditional;
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

        // Copy headers
        for (Map.Entry<String,List<String>> header : headers.entrySet()) {
            for (String value : header.getValue()) {
                pushTarget.getMimeHeaders().addValue(header.getKey()).setString(value);
            }
        }

        // Path and query string
        int queryIndex = path.indexOf('?');
        String pushPath;
        String pushQueryString = null;
        if (queryIndex > -1) {
            pushPath = path.substring(0, queryIndex);
            if (queryIndex + 1 < path.length()) {
                pushQueryString = path.substring(queryIndex + 1);
            }
        } else {
            pushPath = path;
        }

        // Session ID (do this before setting the path since it may change it)
        if (sessionId != null) {
            if (addSessionPathParameter) {
                // TODO: Update pushPath for client's benefit
                // TODO: Figure out how to get this into the CoyoteRequest
            }
            if (addSessionCookie) {
                // TODO: add this
            }
        }

        // Undecoded path - just %nn encoded
        pushTarget.requestURI().setString(pushPath);
        pushTarget.decodedURI().setString(decode(pushPath, baseRequest.getCharacterEncoding()));

        // Query string
        if (pushQueryString == null && queryString != null) {
            pushTarget.queryString().setString(queryString);
        } else if (pushQueryString != null && queryString == null) {
            pushTarget.queryString().setString(pushQueryString);
        } else if (pushQueryString != null && queryString != null) {
            pushTarget.queryString().setString(pushQueryString + "&" +queryString);
        }

        if (conditional) {
            // TODO conditional
        }

        coyoteRequest.action(ActionCode.PUSH_REQUEST, pushTarget);

        // Reset for next call to this method
        pushTarget = null;
        path = null;
        etag = null;
        lastModified = null;
    }


    private static String decode(String input, String charsetName) {
        Charset charset;
        try {
            charset = B2CConverter.getCharset(charsetName);
        } catch (UnsupportedEncodingException uee) {
            // Impossible since original request would have triggered an error
            // before reaching here
            throw new IllegalStateException(uee);
        }

        // TODO implement %nn decoding
        return input;
    }
}

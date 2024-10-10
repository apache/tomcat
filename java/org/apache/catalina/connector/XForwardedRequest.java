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

package org.apache.catalina.connector;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.apache.catalina.Globals;
import org.apache.catalina.util.RequestUtil;
import org.apache.tomcat.util.http.FastHttpDateFormat;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XForwardedRequest extends HttpServletRequestWrapper {

    protected final Map<String, List<String>> headers;

    protected String localName;

    protected int localPort;

    protected String remoteAddr;

    protected String remoteHost;

    protected String scheme;

    protected boolean secure;

    protected String serverName;

    protected int serverPort;

    protected String requestId;

    public XForwardedRequest(HttpServletRequest request) {
        super(request);
        this.localName = request.getLocalName();
        this.localPort = request.getLocalPort();
        this.remoteAddr = request.getRemoteAddr();
        this.remoteHost = request.getRemoteHost();
        this.scheme = request.getScheme();
        this.secure = request.isSecure();
        this.serverName = request.getServerName();
        this.serverPort = request.getServerPort();

        headers = new HashMap<>();
        for (Enumeration<String> headerNames = request.getHeaderNames(); headerNames.hasMoreElements();) {
            String header = headerNames.nextElement();
            headers.put(header, Collections.list(request.getHeaders(header)));
        }
    }

    @Override
    public long getDateHeader(String name) {
        String value = getHeader(name);
        if (value == null) {
            return -1;
        }
        long date = FastHttpDateFormat.parseDate(value);
        if (date == -1) {
            throw new IllegalArgumentException(value);
        }
        return date;
    }

    @Override
    public String getHeader(String name) {
        Map.Entry<String,List<String>> header = getHeaderEntry(name);
        if (header == null || header.getValue() == null || header.getValue().isEmpty()) {
            return null;
        }
        return header.getValue().get(0);
    }

    protected Map.Entry<String,List<String>> getHeaderEntry(String name) {
        for (Map.Entry<String,List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    public int getHeaderCount() {
        return headers.size();
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        Map.Entry<String,List<String>> header = getHeaderEntry(name);
        if (header == null || header.getValue() == null) {
            return Collections.enumeration(Collections.<String>emptyList());
        }
        return Collections.enumeration(header.getValue());
    }

    @Override
    public int getIntHeader(String name) {
        String value = getHeader(name);
        if (value == null) {
            return -1;
        }
        return Integer.parseInt(value);
    }

    @Override
    public String getLocalName() {
        return localName;
    }

    @Override
    public int getLocalPort() {
        return localPort;
    }

    @Override
    public String getRemoteAddr() {
        return this.remoteAddr;
    }

    @Override
    public String getRemoteHost() {
        return this.remoteHost;
    }

    @Override
    public String getRequestId() {
        if (this.requestId != null) {
            return this.requestId;
        }

        return super.getRequest().getRequestId();
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public int getServerPort() {
        return serverPort;
    }

    public void removeHeader(String name) {
        Map.Entry<String,List<String>> header = getHeaderEntry(name);
        if (header != null) {
            headers.remove(header.getKey());
        }
    }

    public void setHeader(String name, String value) {
        List<String> values = Collections.singletonList(value);
        Map.Entry<String,List<String>> header = getHeaderEntry(name);
        if (header == null) {
            headers.put(name, values);
        } else {
            header.setValue(values);
        }

    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public void setSecure(boolean secure) {
        super.getRequest().setAttribute(Globals.REMOTE_IP_FILTER_SECURE, Boolean.valueOf(secure));
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    @Override
    public StringBuffer getRequestURL() {
        return RequestUtil.getRequestURL(this);
    }
}
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
package org.apache.catalina.valves.rewrite;

import java.util.Calendar;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.Request;

import org.apache.tomcat.util.http.FastHttpDateFormat;

public class ResolverImpl extends Resolver {

    protected Request request = null;

    public ResolverImpl(Request request) {
        this.request = request;
    }

    /**
     * The following are not implemented:
     * - SERVER_ADMIN
     * - API_VERSION
     * - IS_SUBREQ
     */
    @Override
    public String resolve(String key) {
        if (key.equals("HTTP_USER_AGENT")) {
            return request.getHeader("user-agent");
        } else if (key.equals("HTTP_REFERER")) {
            return request.getHeader("referer");
        } else if (key.equals("HTTP_COOKIE")) {
            return request.getHeader("cookie");
        } else if (key.equals("HTTP_FORWARDED")) {
            return request.getHeader("forwarded");
        } else if (key.equals("HTTP_HOST")) {
            String host = request.getHeader("host");
            if (host != null) {
                int index = host.indexOf(':');
                if (index != -1) {
                    host = host.substring(0, index);
                }
            }
            return host;
        } else if (key.equals("HTTP_PROXY_CONNECTION")) {
            return request.getHeader("proxy-connection");
        } else if (key.equals("HTTP_ACCEPT")) {
            return request.getHeader("accept");
        } else if (key.equals("REMOTE_ADDR")) {
            return request.getRemoteAddr();
        } else if (key.equals("REMOTE_HOST")) {
            return request.getRemoteHost();
        } else if (key.equals("REMOTE_PORT")) {
            return String.valueOf(request.getRemotePort());
        } else if (key.equals("REMOTE_USER")) {
            return request.getRemoteUser();
        } else if (key.equals("REMOTE_IDENT")) {
            return request.getRemoteUser();
        } else if (key.equals("REQUEST_METHOD")) {
            return request.getMethod();
        } else if (key.equals("SCRIPT_FILENAME")) {
            return request.getServletContext().getRealPath(request.getServletPath());
        } else if (key.equals("REQUEST_PATH")) {
            return request.getRequestPathMB().toString();
        } else if (key.equals("CONTEXT_PATH")) {
            return request.getContextPath();
        } else if (key.equals("SERVLET_PATH")) {
            return emptyStringIfNull(request.getServletPath());
        } else if (key.equals("PATH_INFO")) {
            return emptyStringIfNull(request.getPathInfo());
        } else if (key.equals("QUERY_STRING")) {
            return emptyStringIfNull(request.getQueryString());
        } else if (key.equals("AUTH_TYPE")) {
            return request.getAuthType();
        } else if (key.equals("DOCUMENT_ROOT")) {
            return request.getServletContext().getRealPath("/");
        } else if (key.equals("SERVER_NAME")) {
            return request.getLocalName();
        } else if (key.equals("SERVER_ADDR")) {
            return request.getLocalAddr();
        } else if (key.equals("SERVER_PORT")) {
            return String.valueOf(request.getLocalPort());
        } else if (key.equals("SERVER_PROTOCOL")) {
            return request.getProtocol();
        } else if (key.equals("SERVER_SOFTWARE")) {
            return "tomcat";
        } else if (key.equals("THE_REQUEST")) {
            return request.getMethod() + " " + request.getRequestURI()
            + " " + request.getProtocol();
        } else if (key.equals("REQUEST_URI")) {
            return request.getRequestURI();
        } else if (key.equals("REQUEST_FILENAME")) {
            return request.getPathTranslated();
        } else if (key.equals("HTTPS")) {
            return request.isSecure() ? "on" : "off";
        } else if (key.equals("TIME_YEAR")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        } else if (key.equals("TIME_MON")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.MONTH));
        } else if (key.equals("TIME_DAY")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        } else if (key.equals("TIME_HOUR")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        } else if (key.equals("TIME_MIN")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.MINUTE));
        } else if (key.equals("TIME_SEC")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.SECOND));
        } else if (key.equals("TIME_WDAY")) {
            return String.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
        } else if (key.equals("TIME")) {
            return FastHttpDateFormat.getCurrentDate();
        }
        return null;
    }

    @Override
    public String resolveEnv(String key) {
        Object result = request.getAttribute(key);
        return (result != null) ? result.toString() : System.getProperty(key);
    }

    @Override
    public String resolveSsl(String key) {
        // FIXME: Implement SSL environment variables
        return null;
    }

    @Override
    public String resolveHttp(String key) {
        String header = request.getHeader(key);
        if (header == null) {
            return "";
        } else {
            return header;
        }
    }

    @Override
    public boolean resolveResource(int type, String name) {
        WebResourceRoot resources = request.getContext().getResources();
        WebResource resource = resources.getResource(name);
        if (!resource.exists()) {
            return false;
        } else {
            switch (type) {
            case 0:
                return (resource.isDirectory());
            case 1:
                return (resource.isFile());
            case 2:
                return (resource.isFile() && resource.getContentLength() > 0);
            default:
                return false;
            }
        }
    }

    private static final String emptyStringIfNull(String value) {
        if (value == null) {
            return "";
        } else {
            return value;
        }
    }
}

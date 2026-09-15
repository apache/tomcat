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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Host;
import org.apache.catalina.manager.host.HostManagerServlet;
import org.apache.tomcat.util.json.JSONParser;
import org.apache.tomcat.util.res.StringManager;


/**
 * The Manager2 virtual host API. Delegates the actual operations to the protected methods of
 * {@link HostManagerServlet}.
 */
public class HostsApiServlet extends HostManagerServlet {


    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = Strings.manager();


    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = path(request);

        if (path.equals("/api/hosts")) {
            Api.json(response, hosts());
        } else {
            Api.notFound(response);
        }
    }


    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = path(request);

        if (path.equals("/api/hosts")) {
            try {
                handleAdd(request, response);
            } catch (IllegalArgumentException e) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_JSON", e.getMessage());
            }
        } else if (path.equals("/api/hosts/persist")) {
            sendResult(response, invoke(pw -> super.persist(pw, clientSm(request))));
        } else if (path.matches("/api/hosts/[^/]+/start")) {
            sendResult(response, invoke(pw -> super.start(pw, nameOf(path), clientSm(request))));
        } else if (path.matches("/api/hosts/[^/]+/stop")) {
            sendResult(response, invoke(pw -> super.stop(pw, nameOf(path), clientSm(request))));
        } else {
            Api.notFound(response);
        }
    }


    @Override
    public void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = path(request);

        if (path.matches("/api/hosts/[^/]+")) {
            String name = nameOf(path);
            sendResult(response, invoke(pw -> super.remove(pw, name, clientSm(request))));
        } else {
            Api.notFound(response);
        }
    }


    private void handleAdd(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> body = readJson(request);

        String name = string(body.get("name"));
        String aliases = aliasesOf(body.get("aliases"));
        String appBase = string(body.get("appBase"));
        boolean manager = booleanValue(body.get("manager"), false);
        boolean autoDeploy = booleanValue(body.get("autoDeploy"), true);
        boolean deployOnStartup = booleanValue(body.get("deployOnStartup"), true);
        boolean deployXML = booleanValue(body.get("deployXML"), true);
        boolean unpackWARs = booleanValue(body.get("unpackWARs"), true);
        boolean copyXML = booleanValue(body.get("copyXML"), false);

        sendResult(response, invoke(pw -> super.add(pw, name, aliases, appBase, manager, autoDeploy, deployOnStartup,
                deployXML, unpackWARs, copyXML, clientSm(request))));
    }


    private List<Map<String, Object>> hosts() {
        List<Map<String, Object>> result = new ArrayList<>();
        Container[] children = engine.findChildren();
        for (Container child : children) {
            Host h = (Host) child;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", h.getName());
            entry.put("aliases", List.of(h.findAliases()));
            entry.put("appBase", h.getAppBaseFile() != null ? h.getAppBaseFile().getPath() : null);
            entry.put("state", h.getState().toString());
            // Whether the host is up and accepting applications: the same
            // test the classic host manager uses for start/stop.
            entry.put("started", h.getState().isAvailable());
            entry.put("self", Boolean.valueOf(h == installedHost));
            result.add(entry);
        }
        result.sort((a, b) -> String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name"))));
        return result;
    }


    /**
     * The servlet path plus the path info, so the mapping can be either a path mapping ({@code /api/hosts/*}) or an
     * exact mapping.
     */
    private static String path(HttpServletRequest request) {
        String path = request.getServletPath();
        String info = request.getPathInfo();
        if (info != null && !info.isEmpty()) {
            path = path + info;
        }
        if (path == null || path.isEmpty()) {
            path = "/api/hosts";
        }
        return path;
    }


    private static String nameOf(String path) {
        String[] parts = path.split("/");
        // path is /api/hosts/{name} or /api/hosts/{name}/{action}
        if (parts.length >= 3 && ("start".equals(parts[parts.length - 1]) || "stop".equals(parts[parts.length - 1]))) {
            return parts[parts.length - 2];
        }
        return parts[parts.length - 1];
    }


    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }


    /**
     * Convert the {@code aliases} JSON value (array or comma separated string) to the comma separated string expected
     * by {@link HostManagerServlet#add}.
     */
    private static String aliasesOf(Object value) {
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                parts.add(String.valueOf(item));
            }
            return String.join(",", parts);
        }
        if (value instanceof String s && !s.isEmpty()) {
            return s;
        }
        return null;
    }


    private static boolean booleanValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }


    private static Map<String, Object> readJson(HttpServletRequest request) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new JSONParser(body).parseObject();
        } catch (Exception e) {
            throw new IllegalArgumentException(sm.getString("manager2.invalidJson", e.getMessage()), e);
        }
    }


    /**
     * Run one of the inherited text-based operations, capturing the result message.
     */
    private String invoke(Operation operation) {
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter writer = new PrintWriter(stringWriter)) {
            operation.run(writer);
        }
        return stringWriter.toString().trim();
    }


    /**
     * A StringManager for the legacy host manager message bundle. All the inherited operations report their results
     * using the {@code org.apache.catalina.manager.host} strings.
     */
    private static StringManager clientSm(HttpServletRequest request) {
        return StringManager.getManager("org.apache.catalina.manager.host", request.getLocales());
    }


    @FunctionalInterface
    private interface Operation {
        void run(PrintWriter writer);
    }


    private void sendResult(HttpServletResponse response, String message) throws IOException {
        if (message.startsWith("FAIL -")) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "OPERATION_FAILED", message);
        } else {
            Api.ok(response, message);
        }
    }
}

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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Serial;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import org.apache.catalina.Context;
import org.apache.catalina.Container;
import org.apache.catalina.Session;
import org.apache.catalina.manager.HTMLManagerServlet;
import org.apache.catalina.manager.JspHelper;
import org.apache.catalina.util.ContextName;
import org.apache.tomcat.util.json.JSONParser;
import org.apache.tomcat.util.res.StringManager;


/**
 * The Manager2 web application API. Delegates the actual operations to the inherited implementation from
 * {@link HTMLManagerServlet} and {@link org.apache.catalina.manager.ManagerServlet}, and exposes them as a JSON API.
 */
public class AppsApiServlet extends HTMLManagerServlet {


    @Serial
    private static final long serialVersionUID = 1L;


    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            doGetInternal(request, response);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", e.getMessage());
        }
    }


    private void doGetInternal(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = path(request);

        if (path.equals("/api/apps")) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("apps", apps());
            Api.json(response, payload);
        } else if (path.matches("/api/apps/.+/sessions")) {
            handleSessionsList(request, response, contextName(request));
        } else if (path.matches("/api/apps/.+/sessions/[^/]+")) {
            handleSessionDetail(request, response, contextName(request));
        } else if (path.equals("/api/ssl/ciphers")) {
            Api.json(response, getConnectorCiphers(legacySm(request)));
        } else if (path.equals("/api/ssl/certs")) {
            Api.json(response, getConnectorCerts(legacySm(request)));
        } else if (path.equals("/api/ssl/trusted")) {
            Api.json(response, getConnectorTrustedCerts(legacySm(request)));
        } else if (path.equals("/api/leaks")) {
            StringWriter writer = new StringWriter();
            try (PrintWriter pw = new PrintWriter(writer)) {
                super.findleaks(false, pw, legacySm(request));
            }
            List<String> leaks = new ArrayList<>();
            for (String line : writer.toString().split("\\R")) {
                if (!line.isEmpty()) {
                    leaks.add(line);
                }
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("leaks", leaks);
            Api.json(response, payload);
        } else if (path.equals("/api/resources")) {
            StringWriter writer = new StringWriter();
            try (PrintWriter pw = new PrintWriter(writer)) {
                super.resources(pw, request.getParameter("type"), legacySm(request));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            // The legacy resources() method writes a human readable status
            // message as its first line (for example "OK - Listed global
            // resources of all types") before the resource entries. That
            // line is a presentation artifact of the classic manager's HTML
            // page, not a resource, so it is dropped from the payload.
            payload.put("resources", dropFirstLine(writer.toString()));
            Api.json(response, payload);
        } else if (path.equals("/api/diagnostics/vminfo")) {
            StringWriter writer = new StringWriter();
            try (PrintWriter pw = new PrintWriter(writer)) {
                super.vmInfo(pw, legacySm(request), request.getLocales());
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("info", writer.toString());
            Api.json(response, payload);
        } else if (path.equals("/api/diagnostics/threaddump")) {
            StringWriter writer = new StringWriter();
            try (PrintWriter pw = new PrintWriter(writer)) {
                super.threadDump(pw, legacySm(request), request.getLocales());
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dump", writer.toString());
            Api.json(response, payload);
        } else {
            Api.notFound(response);
        }
    }


    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            doPostInternal(request, response);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", e.getMessage());
        }
    }


    private void doPostInternal(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = path(request);

        if (path.equals("/api/apps/deploy")) {
            handleDeploy(request, response);
        } else if (path.equals("/api/apps/upload")) {
            handleUpload(request, response);
        } else if (path.matches("/api/apps/.+/start")) {
            handleLifecycle(request, response, contextName(request), "start");
        } else if (path.matches("/api/apps/.+/stop")) {
            handleLifecycle(request, response, contextName(request), "stop");
        } else if (path.matches("/api/apps/.+/reload")) {
            handleLifecycle(request, response, contextName(request), "reload");
        } else if (path.matches("/api/apps/.+/expire")) {
            handleExpire(request, response, contextName(request));
        } else if (path.matches("/api/apps/.+/sessions/invalidate")) {
            handleInvalidate(request, response, contextName(request));
        } else if (path.equals("/api/ssl/reload")) {
            handleSslReload(request, response);
        } else {
            Api.notFound(response);
        }
    }


    @Override
    public void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            doDeleteInternal(request, response);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", e.getMessage());
        }
    }


    private void doDeleteInternal(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String path = path(request);

        if (path.matches("/api/apps/.+/sessions/[^/]+/attributes/[^/]+")) {
            handleRemoveAttribute(request, response, contextName(request));
        } else if (path.matches("/api/apps/.+")) {
            handleUndeploy(request, response, contextName(request));
        } else {
            Api.notFound(response);
        }
    }


    // -------------------------------------------------------------- Actions


    private List<Map<String, Object>> apps() {
        List<Map<String, Object>> result = new ArrayList<>();
        Container[] children = host.findChildren();
        for (Container child : children) {
            Context context = (Context) child;
            Map<String, Object> app = new LinkedHashMap<>();
            app.put("host", host.getName());
            app.put("path", context.getPath());
            app.put("displayName", context.getDisplayName());
            app.put("version", context.getWebappVersion());
            app.put("docBase", context.getDocBase());
            app.put("available", Boolean.valueOf(context.getState().isAvailable()));
            org.apache.catalina.Manager manager = context.getManager();
            if (manager != null) {
                app.put("sessions", Integer.valueOf(manager.getActiveSessions()));
                app.put("sessionTimeout", Integer.valueOf(context.getSessionTimeout()));
            } else {
                app.put("sessions", 0);
                app.put("sessionTimeout", null);
            }
            try {
                app.put("deployed", Boolean.valueOf(isDeployed(context.getName())));
            } catch (Exception e) {
                app.put("deployed", Boolean.FALSE);
            }
            app.put("self", Boolean.valueOf(context.getName().equals(this.context.getName())));
            result.add(app);
        }
        result.sort((a, b) -> String.valueOf(a.get("path")).compareTo(String.valueOf(b.get("path"))));
        return result;
    }


    private void handleLifecycle(HttpServletRequest request, HttpServletResponse response, ContextName cn,
            String action) throws IOException {
        try {
            if (cn == null) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                        Strings.sm().getString("manager2.missingPath"));
                return;
            }
            String message = invoke(pw -> {
                switch (action) {
                    case "start" -> super.start(pw, cn, legacySm(request));
                    case "stop" -> super.stop(pw, cn, legacySm(request));
                    case "reload" -> super.reload(pw, cn, legacySm(request));
                    default -> {
                    }
                }
            });
            sendResult(response, message);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", e.getMessage());
        }
    }


    private void handleUndeploy(HttpServletRequest request, HttpServletResponse response, ContextName cn)
            throws IOException {
        try {
            if (cn == null) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                        Strings.sm().getString("manager2.missingPath"));
                return;
            }
            String message = invoke(pw -> super.undeploy(pw, cn, legacySm(request)));
            sendResult(response, message);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", e.getMessage());
        }
    }


    private void handleExpire(HttpServletRequest request, HttpServletResponse response, ContextName cn)
            throws IOException {
        try {
            if (cn == null) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                        Strings.sm().getString("manager2.missingPath"));
                return;
            }
            Map<String, Object> body = readJson(request);
            int idle = -1;
            Object idleValue = body.get("idle");
            if (idleValue instanceof Number n) {
                idle = n.intValue();
            }
            int idleFinal = idle;
            String message = invoke(pw -> super.sessions(pw, cn, idleFinal, legacySm(request)));
            sendResult(response, message);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", e.getMessage());
        }
    }


    private void handleDeploy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> body = readJson(request);
            StringManager smClient = legacySm(request);

            String config = string(body.get("config"));
            String war = string(body.get("war"));
            boolean replace = booleanValue(body.get("replace"), false);

            ContextName cn;
            String pathValue = string(body.get("path"));
            if (pathValue != null && !pathValue.isEmpty()) {
                cn = new ContextName(decode(pathValue), string(body.get("version")));
            } else if (config != null && !config.isEmpty()) {
                cn = ContextName.extractFromPath(config);
            } else if (war != null && !war.isEmpty()) {
                cn = ContextName.extractFromPath(war);
            } else {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                        Strings.sm().getString("manager2.missingPath"));
                return;
            }

            String message = invoke(pw -> super.deploy(pw, config, cn, war, replace, smClient));
            sendResult(response, message);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_JSON", e.getMessage());
        }
    }


    private void handleUpload(HttpServletRequest request, HttpServletResponse response) throws IOException {
        StringManager smClient = legacySm(request);

        try {
            Part warPart = request.getPart("war");
            if (warPart == null) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "UPLOAD_NO_FILE",
                        Strings.sm().getString("manager2.uploadNoFile"));
                return;
            }
            String filename = warPart.getSubmittedFileName();
            if (filename == null || !filename.toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "UPLOAD_NOT_WAR",
                        Strings.sm().getString("manager2.uploadNotWar", filename));
                return;
            }
            int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
            if (slash >= 0) {
                filename = filename.substring(slash + 1);
            }

            String pathParam = request.getParameter("path");
            ContextName cn;
            if (pathParam != null && !pathParam.isEmpty()) {
                cn = new ContextName(decode(pathParam), request.getParameter("version"));
            } else {
                cn = new ContextName(filename, true);
            }

            StringWriter validation = new StringWriter();
            try (PrintWriter pw = new PrintWriter(validation)) {
                if (!validateContextName(cn, pw, smClient)) {
                    Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                            validation.toString().trim());
                    return;
                }
            }

            String name = cn.getName();
            boolean replace = "true".equals(request.getParameter("replace"));
            Context existing = (Context) host.findChild(name);
            if (existing != null && !replace) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "ALREADY_DEPLOYED",
                        Strings.sm().getString("manager2.alreadyDeployed", cn.getDisplayName()));
                return;
            }

            File appBase = host.getAppBaseFile();
            File deployedWar = new File(appBase, cn.getBaseName() + ".war");
            if (!pathCheck(deployedWar, appBase, response)) {
                return;
            }
            File target = replace ? new File(deployedWar.getAbsolutePath() + ".tmp") : deployedWar;
            if (!replace && target.exists()) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "WAR_EXISTS",
                        Strings.sm().getString("manager2.uploadWarExists", filename));
                return;
            }

            if (tryAddServiced(name)) {
                try {
                    warPart.write(target.getAbsolutePath());
                    if (replace) {
                        if (deployedWar.exists() && !deployedWar.delete()) {
                            Api.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "DELETE_FAILED",
                                    Strings.sm().getString("manager2.uploadDeleteFailed", deployedWar));
                            return;
                        }
                        if (!target.renameTo(deployedWar)) {
                            Api.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "RENAME_FAILED",
                                    Strings.sm().getString("manager2.uploadRenameFailed", target, deployedWar));
                            return;
                        }
                    }
                } finally {
                    removeServiced(name);
                }
                check(name);
            } else {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "IN_SERVICE",
                        Strings.sm().getString("manager2.contextInService", cn.getDisplayName()));
                return;
            }

            Context deployed = (Context) host.findChild(name);
            String message;
            if (deployed != null && deployed.getConfigured() && deployed.getState().isAvailable()) {
                message = Strings.sm().getString("manager2.deployed", cn.getDisplayName());
            } else if (deployed != null && !deployed.getState().isAvailable()) {
                message = Strings.sm().getString("manager2.deployedNotStarted", cn.getDisplayName());
            } else {
                message = Strings.sm().getString("manager2.deployFailed", cn.getDisplayName());
            }
            Api.ok(response, message);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_JSON", e.getMessage());
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.upload"), e);
            Api.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "UPLOAD_FAILED",
                    Strings.sm().getString("manager2.uploadFailed", e.getMessage()));
        }
    }


    private void handleSessionsList(HttpServletRequest request, HttpServletResponse response, ContextName cn)
            throws IOException {
        try {
            if (cn == null) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                        Strings.sm().getString("manager2.missingPath"));
                return;
            }
            StringManager smClient = legacySm(request);
            List<Session> sessions = getSessionsForName(cn, smClient);

            String sortBy = request.getParameter("sort");
            String orderBy = null;
            if (sortBy != null && !sortBy.trim().isEmpty()) {
                Comparator<Session> comparator = getComparator(sortBy);
                if (comparator != null) {
                    boolean asc = !"DESC".equalsIgnoreCase(request.getParameter("order"));
                    if (!asc) {
                        comparator = Collections.reverseOrder(comparator);
                    }
                    try {
                        sessions.sort(comparator);
                    } catch (IllegalStateException ise) {
                        // At least one session was invalidated while sorting
                    }
                    orderBy = asc ? "ASC" : "DESC";
                }
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (Session session : sessions) {
                result.add(sessionToJson(session));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("path", cn.getPath());
            payload.put("sort", sortBy);
            payload.put("order", orderBy);
            payload.put("sessions", result);
            Api.json(response, payload);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", e.getMessage());
        }
    }


    private void handleSessionDetail(HttpServletRequest request, HttpServletResponse response, ContextName cn)
            throws IOException {
        try {
            if (cn == null) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                        Strings.sm().getString("manager2.missingPath"));
                return;
            }
            String path = path(request);
            int index = path.lastIndexOf("/sessions/");
            if (index < 0) {
                Api.notFound(response);
                return;
            }
            String sessionId = path.substring(index + "/sessions/".length());

            Session session = getSessionForNameAndId(cn, sessionId, legacySm(request));
            if (session == null) {
                Api.error(response, HttpServletResponse.SC_NOT_FOUND, "SESSION_NOT_FOUND",
                        Strings.sm().getString("manager2.sessionNotFound", sessionId));
                return;
            }
            Map<String, Object> payload = sessionToJson(session);
            payload.put("attributes", attributesToJson(session));
            Api.json(response, payload);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", e.getMessage());
        }
    }


    private void handleInvalidate(HttpServletRequest request, HttpServletResponse response, ContextName cn)
            throws IOException {
        try {
            if (cn == null) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                        Strings.sm().getString("manager2.missingPath"));
                return;
            }
            Map<String, Object> body = readJson(request);
            List<String> ids = new ArrayList<>();
            if (body.get("ids") instanceof List<?> list) {
                for (Object item : list) {
                    ids.add(String.valueOf(item));
                }
            }
            int count = invalidateSessions(cn, ids.toArray(new String[0]), legacySm(request));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", Boolean.TRUE);
            payload.put("count", Integer.valueOf(count));
            payload.put("message", Strings.sm().getString("manager2.sessionsInvalidated", Integer.valueOf(count)));
            Api.json(response, payload);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", e.getMessage());
        }
    }


    private void handleRemoveAttribute(HttpServletRequest request, HttpServletResponse response, ContextName cn)
            throws IOException {
        try {
            if (cn == null) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH",
                        Strings.sm().getString("manager2.missingPath"));
                return;
            }
            String path = path(request);
            String attributesPrefix = "/attributes/";
            int index = path.lastIndexOf(attributesPrefix);
            if (index < 0) {
                Api.notFound(response);
                return;
            }
            String before = path.substring(0, index);
            String sessionId = before.substring(before.lastIndexOf('/') + 1);
            String attributeName = decode(path.substring(index + attributesPrefix.length()));

            boolean removed = removeSessionAttribute(cn, sessionId, attributeName, legacySm(request));
            if (removed) {
                Api.ok(response, Strings.sm().getString("manager2.attributeRemoved", attributeName));
            } else {
                Api.error(response, HttpServletResponse.SC_NOT_FOUND, "ATTRIBUTE_NOT_FOUND",
                        Strings.sm().getString("manager2.attributeNotFound", attributeName));
            }
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", e.getMessage());
        }
    }


    private void handleSslReload(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> body = readJson(request);
            String tlsHostName = string(body.get("tlsHostName"));
            String message = invoke(pw -> super.sslReload(pw, tlsHostName, legacySm(request)));
            sendResult(response, message);
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_JSON", e.getMessage());
        }
    }


    // ------------------------------------------------------------- Helpers


    private Map<String, Object> sessionToJson(Session session) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", session.getId());
        result.put("creationTime", Long.valueOf(session.getCreationTime()));
        result.put("lastAccessedTime", Long.valueOf(session.getLastAccessedTime()));
        result.put("maxInactiveInterval", Integer.valueOf(session.getMaxInactiveInterval()));
        HttpSession httpSession = session.getSession();
        result.put("active", Boolean.valueOf(httpSession != null));
        result.put("isNew", Boolean.valueOf(httpSession != null && httpSession.isNew()));
        result.put("locale", JspHelper.guessDisplayLocaleFromSession(session));
        result.put("user", JspHelper.guessDisplayUserFromSession(session));
        return result;
    }


    private List<Map<String, Object>> attributesToJson(Session session) {
        List<Map<String, Object>> result = new ArrayList<>();
        HttpSession httpSession = session.getSession();
        if (httpSession == null) {
            return result;
        }
        for (String name : Collections.list(httpSession.getAttributeNames())) {
            Object value = httpSession.getAttribute(name);
            Map<String, Object> attribute = new LinkedHashMap<>();
            attribute.put("name", name);
            attribute.put("class", value != null ? value.getClass().getName() : null);
            String text = value == null ? null : String.valueOf(value);
            if (text != null && text.length() > 500) {
                text = text.substring(0, 500) + "...";
            }
            attribute.put("value", text);
            result.add(attribute);
        }
        result.sort((a, b) -> String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name"))));
        return result;
    }


    private static String path(HttpServletRequest request) {
        String path = request.getServletPath();
        String info = request.getPathInfo();
        if (info != null && !info.isEmpty()) {
            path = path + info;
        }
        return path;
    }


    /**
     * Decode the context from the request. The context path is taken from the {@code path} query parameter when present
     * (which also supports context paths containing slashes), otherwise from the first path segment after
     * {@code /api/apps}. Returns {@code null} when no path is present.
     */
    private ContextName contextName(HttpServletRequest request) {
        String version = request.getParameter("version");
        // The client always sends the (possibly empty) context path as the
        // "path" query parameter. Note that getParameter() returns "" when
        // the parameter is present with an empty value (ROOT context) and
        // null when it is absent.
        String pathParam = request.getParameter("path");
        if (pathParam != null) {
            return new ContextName(decode(pathParam), version);
        }
        String path = path(request);
        String prefix = "/api/apps/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String rest = path.substring(prefix.length());
        int slash = rest.indexOf('/');
        String encoded = slash >= 0 ? rest.substring(0, slash) : rest;
        if (encoded.isEmpty()) {
            return null;
        }
        String decoded = decode(encoded);
        if (decoded.equals("root")) {
            // URL segment convention for the ROOT context
            decoded = "";
        }
        return new ContextName(decoded, version);
    }


    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }


    /**
     * Everything after the first line of the given text (or an empty string when the text has at most one line).
     */
    private static String dropFirstLine(String text) {
        int index = text.indexOf('\n');
        return index < 0 ? "" : text.substring(index + 1);
    }


    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
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
            throw new IllegalArgumentException(Strings.sm().getString("manager2.invalidJson", e.getMessage()), e);
        }
    }


    private String invoke(Operation operation) {
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter writer = new PrintWriter(stringWriter)) {
            operation.run(writer);
        }
        return stringWriter.toString().trim();
    }


    /**
     * A StringManager for the legacy manager message bundle. All the inherited operations report their results using
     * the {@code org.apache.catalina.manager} strings, so that bundle has to be used for localized output.
     */
    private static StringManager legacySm(HttpServletRequest request) {
        return StringManager.getManager("org.apache.catalina.manager", request.getLocales());
    }


    /**
     * Ensure the file is contained in the expected directory (canonical path containment, same check as the legacy
     * manager servlets).
     */
    private static boolean pathCheck(File input, File expected, HttpServletResponse response) throws IOException {
        try {
            if (!input.getCanonicalFile().toPath().startsWith(expected.getCanonicalFile().toPath())) {
                Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "PATH_CHECK_FAILED",
                        Strings.sm().getString("manager2.pathCheckFail", input, expected));
                return false;
            }
        } catch (IOException ioe) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "PATH_CHECK_ERROR",
                    Strings.sm().getString("manager2.pathCheckError", input, expected, ioe.getMessage()));
            return false;
        }
        return true;
    }


    private void sendResult(HttpServletResponse response, String message) throws IOException {
        if (message.startsWith("FAIL -")) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "OPERATION_FAILED", message);
        } else {
            Api.ok(response, message);
        }
    }


    @FunctionalInterface
    private interface Operation {
        void run(PrintWriter writer);
    }
}

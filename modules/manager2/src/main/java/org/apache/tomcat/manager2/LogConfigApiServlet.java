/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.manager2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.startup.Bootstrap;
import org.apache.catalina.startup.Catalina;
import org.apache.juli.ClassLoaderLogManager;
import org.apache.tomcat.util.json.JSONParser;


/**
 * The Manager2 logging configuration API. Exposes the JULI logging configuration of the server: the raw text of
 * {@code conf/logging.properties} and the live state of the loggers of the server class loaders (the system class
 * loader, which owns the configuration when the server is started with the standard launcher, and the container class
 * loader, which holds the loggers that the container classes reference statically).
 * <p>
 * <b>Endpoints.</b> {@code GET /api/logs/config} returns the file and the live logger state. {@code POST
 * /api/logs/config/file} saves the file (a timestamped backup of the previous file is kept next to it). {@code POST
 * /api/logs/config/apply} makes the saved file take effect on the running server. {@code POST /api/logs/config/level}
 * changes the level of one logger without touching the file (a quick, in-memory change that a restart or a later
 * {@code apply} discards).
 * <p>
 * <b>Live application.</b> Applying replaces the properties, closes the handlers of the old configuration, creates the
 * handlers of the new one and re-applies the levels and handler lists to the loggers that exist already, so the change
 * is visible to the container classes that hold a static reference to a logger (a {@code LogManager.reset()} would
 * leave those references wired to unconfigured loggers). Applying does not restart anything; the loggers of web
 * application class loaders keep their own per-webapp wiring (their levels and handlers resolve to this configuration
 * through the class loader fallback as long as a web application does not define its own {@code logging.properties}).
 * Applying requires a {@code ClassLoaderLogManager} that provides the {@code reconfigure} and {@code refreshLoggers}
 * methods; without them the API still reads and saves the file, and the change takes effect on restart.
 * <p>
 * <b>Levels.</b> The valid level names are the {@code java.util.logging} names ({@code SEVERE}, {@code WARNING},
 * {@code INFO}, {@code CONFIG}, {@code FINE}, {@code FINER}, {@code FINEST}, {@code ALL}, {@code OFF}) plus
 * {@code INHERIT}, which clears the level of a logger so it inherits from its parent again. Note that a record is only
 * written when both the logger and the handler are enabled for its level.
 * <p>
 * <b>Privilege.</b> The configuration can name handler classes, which are instantiated (at startup and on apply) by
 * the server: this API therefore requires the same {@code manager-gui} role that can deploy applications (which
 * executes arbitrary code), a valid CSRF token, and every save, apply and level change is written to the manager log
 * as an audit entry.
 */
public class LogConfigApiServlet extends HttpServlet {


    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * The class loader contexts this API can address. {@code system} is the system class loader (the standard launcher
     * loads {@code conf/logging.properties} into its context through {@code java.util.logging.config.file}),
     * {@code container} is the class loader of the Catalina classes (whose statics hold the container loggers).
     */
    private static final String CONTEXT_SYSTEM = "system";

    private static final String CONTEXT_CONTAINER = "container";

    /**
     * The level names accepted by the level endpoint, plus {@code INHERIT} which clears the level.
     */
    private static final Map<String, Level> LEVELS = Map.of("SEVERE", Level.SEVERE, "WARNING", Level.WARNING,
            "INFO", Level.INFO, "CONFIG", Level.CONFIG, "FINE", Level.FINE, "FINER", Level.FINER, "FINEST",
            Level.FINEST, "ALL", Level.ALL, "OFF", Level.OFF);

    /**
     * The maximum accepted length of the configuration file text.
     */
    private static final int MAX_TEXT_LENGTH = 64 * 1024;

    /**
     * The reconfigure and refreshLoggers methods of a {@code ClassLoaderLogManager} that supports live reconfiguration,
     * or {@code null} when the running JULI does not provide them. Looked up reflectively so that this class links
     * against any tomcat-juli version.
     */
    private static final Method RECONFIGURE = findLiveMethod("reconfigure", InputStream.class, ClassLoader.class);

    private static final Method REFRESH_LOGGERS = findLiveMethod("refreshLoggers", ClassLoader.class);


    // ------------------------------------------------------------ Request API


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            if ("/api/logs/config".equals(path(request))) {
                Api.json(response, config());
            } else {
                Api.notFound(response);
            }
        } catch (LogConfigException e) {
            Api.error(response, e.status, e.code, e.getMessage());
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.logconfig"), e);
            throw new ServletException(e);
        }
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = path(request);
        Map<String, Object> body = readJson(request);

        try {
            if ("/api/logs/config/file".equals(path)) {
                saveFile(response, body);
            } else if ("/api/logs/config/apply".equals(path)) {
                apply(response);
            } else if ("/api/logs/config/level".equals(path)) {
                setLevel(response, body);
            } else {
                Api.notFound(response);
            }
        } catch (LogConfigException e) {
            Api.error(response, e.status, e.code, e.getMessage());
        } catch (IllegalArgumentException e) {
            Api.error(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_JSON", e.getMessage());
        } catch (Exception e) {
            log(Strings.sm().getString("manager2.error.logconfig"), e);
            throw new ServletException(e);
        }
    }


    // ------------------------------------------------------------------ State


    /**
     * The configuration file and the live loggers of the server class loader contexts.
     */
    private Map<String, Object> config() throws Exception {

        Map<String, Object> payload = new LinkedHashMap<>();

        File file = configFile();
        Map<String, Object> fileEntry = new LinkedHashMap<>();
        fileEntry.put("path", "conf/logging.properties");
        fileEntry.put("exists", Boolean.valueOf(file.isFile()));
        fileEntry.put("modified", Long.valueOf(file.lastModified()));
        fileEntry.put("text", file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                : "");
        payload.put("file", fileEntry);

        payload.put("liveApply", Boolean.valueOf(isLiveApplySupported()));
        if (!isLiveApplySupported()) {
            payload.put("note", Strings.sm().getString("manager2.logConfigUnsupported"));
        }

        List<Map<String, Object>> contexts = new ArrayList<>();
        ClassLoader container = containerClassLoader();
        contexts.add(contextState(CONTEXT_SYSTEM, ClassLoader.getSystemClassLoader()));
        if (container != ClassLoader.getSystemClassLoader()) {
            contexts.add(contextState(CONTEXT_CONTAINER, container));
        }
        payload.put("contexts", contexts);
        return payload;
    }


    private static Map<String, Object> contextState(String id, ClassLoader classLoader) throws Exception {

        return withClassLoader(classLoader, () -> {
            LogManager manager = LogManager.getLogManager();
            List<Map<String, Object>> loggers = new ArrayList<>();
            Enumeration<String> names = manager.getLoggerNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                Logger logger = manager.getLogger(name);
                if (logger == null) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", name);
                entry.put("level", logger.getLevel() == null ? null : logger.getLevel().getName());
                entry.put("effectiveLevel", effectiveLevel(logger));
                entry.put("useParentHandlers", Boolean.valueOf(logger.getUseParentHandlers()));
                List<Map<String, Object>> handlers = new ArrayList<>();
                for (Handler handler : logger.getHandlers()) {
                    Map<String, Object> handlerEntry = new LinkedHashMap<>();
                    handlerEntry.put("class", handler.getClass().getName());
                    handlerEntry.put("level", handler.getLevel() == null ? null : handler.getLevel().getName());
                    handlers.add(handlerEntry);
                }
                entry.put("handlers", handlers);
                loggers.add(entry);
            }
            loggers.sort((a, b) -> {
                String an = String.valueOf(a.get("name"));
                String bn = String.valueOf(b.get("name"));
                // The root logger (the empty name) first.
                if (an.isEmpty() != bn.isEmpty()) {
                    return an.isEmpty() ? -1 : 1;
                }
                return an.compareTo(bn);
            });

            Map<String, Object> context = new LinkedHashMap<>();
            context.put("id", id);
            context.put("classLoader", classLoader == null ? "bootstrap" : classLoader.toString());
            context.put("loggers", loggers);
            return context;
        });
    }


    /**
     * The effective level of a logger: the first level configured on the logger or one of its parents. The JULI root
     * loggers always carry a level (the manager defaults them to {@code INFO}), so the walk normally finds one; as a
     * final fallback (a logger detached from its root) {@code INFO} is reported, the level the root would default to.
     */
    private static String effectiveLevel(Logger logger) {
        try {
            for (Logger node = logger; node != null; node = node.getParent()) {
                Level level = node.getLevel();
                if (level != null) {
                    return level.getName();
                }
            }
            return Level.INFO.getName();
        } catch (RuntimeException e) {
            return null;
        }
    }


    // --------------------------------------------------------------- Mutations


    /**
     * Save the configuration file, keeping a timestamped backup of the previous file. The saved text is validated as
     * {@code java.util.Properties} syntax; the handler classes it names are only instantiated by a later apply (or at
     * startup), which requires the {@code manager-gui} role as well.
     */
    private void saveFile(HttpServletResponse response, Map<String, Object> body) throws Exception {

        Object value = body.get("text");
        if (!(value instanceof String text)) {
            throw new LogConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_BODY",
                    Strings.sm().getString("manager2.logConfigInvalidBody"));
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new LogConfigException(HttpServletResponse.SC_BAD_REQUEST, "TOO_LONG",
                    Strings.sm().getString("manager2.logConfigTooLong"));
        }
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(text));
        } catch (IOException e) {
            throw new LogConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_PROPERTIES",
                    Strings.sm().getString("manager2.logConfigInvalidProperties", String.valueOf(e.getMessage())));
        }

        File file = configFile();
        File directory = file.getParentFile();
        if (directory == null || !directory.isDirectory()) {
            throw new LogConfigException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "CONF_DIR_MISSING",
                    Strings.sm().getString("manager2.logConfigConfMissing"));
        }

        String backup = null;
        File temporary = new File(directory, "logging.properties.writing");
        try {
            if (file.isFile()) {
                backup = file.getName() + "." + System.currentTimeMillis();
                Files.copy(file.toPath(), new File(directory, backup).toPath());
            }
            Files.write(temporary.toPath(), text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log(Strings.sm().getString("manager2.error.logconfig"), e);
            throw new LogConfigException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SAVE_FAILED",
                    Strings.sm().getString("manager2.logConfigSaveFailed", String.valueOf(e.getMessage())));
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                log("manager2: could not remove the temporary file " + temporary);
            }
        }

        log(Strings.sm().getString("manager2.logConfigAuditSave", backup == null ? "-" : backup));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", Boolean.TRUE);
        payload.put("message", Strings.sm().getString("manager2.logConfigSaved", "conf/logging.properties",
                backup == null ? "-" : backup));
        payload.put("file", "conf/logging.properties");
        payload.put("backup", backup);
        Api.json(response, payload);
    }


    /**
     * Make the saved file take effect on the running server. The class loader context that owns the configuration is
     * reconfigured (its handlers are replaced by new ones built from the new properties) and the other server context
     * gets its loggers rewired against it. Never call {@code reset()} or {@code readConfiguration()} here: the
     * container classes hold static references to logger instances that a reset would leave without handlers.
     */
    private void apply(HttpServletResponse response) throws Exception {

        File file = configFile();
        if (!file.isFile()) {
            throw new LogConfigException(HttpServletResponse.SC_NOT_FOUND, "FILE_MISSING",
                    Strings.sm().getString("manager2.logConfigFileMissing"));
        }
        if (!isLiveApplySupported()) {
            throw new LogConfigException(HttpServletResponse.SC_NOT_IMPLEMENTED, "LIVE_UNSUPPORTED",
                    Strings.sm().getString("manager2.logConfigUnsupported"));
        }

        ClassLoaderLogManager manager = (ClassLoaderLogManager) LogManager.getLogManager();
        ClassLoader system = ClassLoader.getSystemClassLoader();
        ClassLoader container = containerClassLoader();

        try {
            if (container != system && contextIsEmpty(system)) {
                // The configuration is owned by the container class loader (a non-standard launcher setup):
                // reconfiguring the unused system context would create a second set of handlers.
                reconfigure(manager, file, container);
            } else {
                reconfigure(manager, file, system);
                if (container != system) {
                    withClassLoader(container, () -> {
                        invoke(REFRESH_LOGGERS, manager, container);
                        return null;
                    });
                }
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log(Strings.sm().getString("manager2.error.logconfig"), cause);
            throw new LogConfigException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "APPLY_FAILED",
                    Strings.sm().getString("manager2.logConfigApplyFailed", String.valueOf(cause.getMessage())));
        }

        log(Strings.sm().getString("manager2.logConfigAuditApply"));
        Api.ok(response, Strings.sm().getString("manager2.logConfigApplied"));
    }


    /**
     * The quick level change: sets the level of one logger of one context, in memory only (the file is not written and
     * a restart or a later apply discards it).
     */
    private void setLevel(HttpServletResponse response, Map<String, Object> body) throws Exception {

        String contextId = stringValue(body.get("context"));
        if (!CONTEXT_SYSTEM.equals(contextId) && !CONTEXT_CONTAINER.equals(contextId)) {
            throw new LogConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_CONTEXT",
                    Strings.sm().getString("manager2.logConfigBadContext"));
        }
        String name = stringValue(body.get("name"));
        if (name == null || name.isEmpty() || name.length() > 200 || name.chars().anyMatch(c -> c < 0x20)) {
            throw new LogConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_NAME",
                    Strings.sm().getString("manager2.logConfigBadName"));
        }
        String levelName = stringValue(body.get("level"));
        Level level = "INHERIT".equalsIgnoreCase(levelName) ? null
                : (levelName == null ? null : LEVELS.get(levelName.toUpperCase(Locale.ROOT)));
        if (levelName == null || (!"INHERIT".equalsIgnoreCase(levelName) && level == null)) {
            throw new LogConfigException(HttpServletResponse.SC_BAD_REQUEST, "INVALID_LEVEL",
                    Strings.sm().getString("manager2.logConfigBadLevel"));
        }

        ClassLoader classLoader = CONTEXT_CONTAINER.equals(contextId) ? containerClassLoader()
                : ClassLoader.getSystemClassLoader();
        Level applied = level;
        Logger logger = withClassLoader(classLoader,
                () -> LogManager.getLogManager().getLogger(name));
        if (logger == null) {
            throw new LogConfigException(HttpServletResponse.SC_NOT_FOUND, "LOGGER_NOT_FOUND",
                    Strings.sm().getString("manager2.logConfigLoggerNotFound", name, contextId));
        }
        logger.setLevel(applied);

        log(Strings.sm().getString("manager2.logConfigAuditLevel", contextId, name,
                levelName.toUpperCase(Locale.ROOT)));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", Boolean.TRUE);
        payload.put("message", Strings.sm().getString("manager2.logConfigLevelSet", name,
                applied == null ? "INHERIT" : applied.getName()));
        payload.put("level", applied == null ? null : applied.getName());
        payload.put("effectiveLevel", effectiveLevel(logger));
        Api.json(response, payload);
    }


    // ----------------------------------------------------------------- Helpers


    /**
     * The servlet path plus the path info, so the mapping can be either a path mapping or an exact mapping.
     */
    private static String path(HttpServletRequest request) {
        String path = request.getServletPath();
        String info = request.getPathInfo();
        if (info != null && !info.isEmpty()) {
            path = path + info;
        }
        return path;
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


    /**
     * {@code conf/logging.properties} of this server. The {@code manager2.store.base} system property overrides the
     * base directory (the same override the configuration store uses; the integration tests use it to redirect the
     * write to a throw-away directory).
     */
    private static File configFile() {
        String base = System.getProperty("manager2.store.base");
        if (base == null || base.isEmpty()) {
            base = Bootstrap.getCatalinaBase();
        }
        return new File(new File(base, "conf"), "logging.properties");
    }


    /**
     * The class loader of the Catalina classes (the container class loader, see {@code Bootstrap.initClassLoaders()}
     * and {@code Bootstrap.init()}): the JULI context that holds the loggers the container classes reference
     * statically. Note that {@code Bootstrap} itself lives on the system class path, so it cannot be used to locate
     * this class loader.
     */
    private static ClassLoader containerClassLoader() {
        return Catalina.class.getClassLoader();
    }


    private static boolean isLiveApplySupported() {
        return LogManager.getLogManager() instanceof ClassLoaderLogManager && RECONFIGURE != null
                && REFRESH_LOGGERS != null;
    }


    private static Method findLiveMethod(String name, Class<?>... parameters) {
        try {
            return ClassLoaderLogManager.class.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }


    private static void reconfigure(ClassLoaderLogManager manager, File file, ClassLoader classLoader)
            throws Exception {
        withClassLoader(classLoader, () -> {
            try (InputStream is = new FileInputStream(file)) {
                invoke(RECONFIGURE, manager, is, classLoader);
            }
            return null;
        });
    }


    /**
     * Whether the class loader context has nothing configured beyond an empty (lazily created) root: such a context
     * does not own the server configuration.
     */
    private static boolean contextIsEmpty(ClassLoader classLoader) throws Exception {
        return withClassLoader(classLoader, () -> {
            LogManager manager = LogManager.getLogManager();
            int count = 0;
            Enumeration<String> names = manager.getLoggerNames();
            while (names.hasMoreElements()) {
                names.nextElement();
                count++;
            }
            if (count > 1) {
                return Boolean.FALSE;
            }
            Logger root = manager.getLogger("");
            return Boolean.valueOf(root == null || root.getHandlers().length == 0);
        });
    }


    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }


    /**
     * Run an action with the thread context class loader set to the given class loader: JULI associates its logger
     * contexts and its property lookups with the thread context class loader.
     */
    private static <T> T withClassLoader(ClassLoader classLoader, Callable<T> action) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return action.call();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }


    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }


    /**
     * A controlled API error, already carrying the status and the localized message.
     */
    private static final class LogConfigException extends Exception {

        @Serial
        private static final long serialVersionUID = 1L;

        private final int status;

        private final String code;

        LogConfigException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }


    @Override
    public String getServletInfo() {
        return "Manager2 Logging Configuration API";
    }
}

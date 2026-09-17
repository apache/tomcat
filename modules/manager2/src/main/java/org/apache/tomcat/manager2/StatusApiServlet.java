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
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.util.ServerInfo;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;


/**
 * The Manager2 status API. Serves the compact live snapshot, the instant CPU and memory snapshot, the live worker
 * table and the detailed per-application state. The MBean queries mirror
 * {@link org.apache.catalina.manager.StatusManagerServlet}.
 */
public class StatusApiServlet extends HttpServlet implements ContainerServlet, NotificationListener {


    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = Strings.manager();


    private transient MBeanServer mBeanServer = null;

    private final List<ObjectName> threadPools = Collections.synchronizedList(new ArrayList<>());

    private final List<ObjectName> globalRequestProcessors = Collections.synchronizedList(new ArrayList<>());

    private final List<ObjectName> requestProcessors = Collections.synchronizedList(new ArrayList<>());

    private transient Wrapper wrapper = null;

    private transient Context context = null;

    private transient Host host = null;

    private transient StatusHistory history = null;

    private transient ScheduledFuture<?> historyTask = null;

    private transient ScheduledExecutorService ownExecutor = null;


    // ------------------------------------------------ ContainerServlet API


    @Override
    public Wrapper getWrapper() {
        return wrapper;
    }


    @Override
    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
        if (wrapper == null) {
            context = null;
            host = null;
        } else {
            context = (Context) wrapper.getParent();
            host = (Host) context.getParent();
        }
    }


    // -------------------------------------------------------- Lifecycle API


    @Override
    public void init() throws ServletException {

        mBeanServer = Registry.getRegistry(null).getMBeanServer();

        try {
            threadPools.addAll(queryNames(mBeanServer, "*:type=ThreadPool,*"));
            globalRequestProcessors.addAll(queryNames(mBeanServer, "*:type=GlobalRequestProcessor,*"));
            requestProcessors.addAll(queryNames(mBeanServer, "*:type=RequestProcessor,*"));

            mBeanServer.addNotificationListener(new ObjectName("JMImplementation:type=MBeanServerDelegate"), this, null,
                    null);
        } catch (Exception e) {
            log(sm.getString("manager2.error.jmx"), e);
        }

        // Start the background collection of status samples for
        // /api/status/history. The collection period and the window of
        // history are configurable through the "tickMs" and "windowMs"
        // init parameters.
        long tickMs = getLongInitParameter("tickMs", StatusHistory.DEFAULT_TICK_MS, 500);
        long windowMs = getLongInitParameter("windowMs", StatusHistory.DEFAULT_WINDOW_MS, tickMs);
        history = new StatusHistory(tickMs, windowMs);
        // The server wide utility executor is exposed as a ServletContext
        // attribute (see StandardContext.configStart). Only if it is not
        // available (should not happen) a private executor is created and
        // shut down again on destroy.
        ScheduledExecutorService executor = (ScheduledExecutorService) getServletConfig().getServletContext()
                .getAttribute(org.apache.tomcat.util.threads.ScheduledThreadPoolExecutor.class.getName());
        if (executor == null) {
            ownExecutor = new ScheduledThreadPoolExecutor(1);
            executor = ownExecutor;
        }
        historyTask = executor.scheduleAtFixedRate(this::recordHistory, 0, tickMs, TimeUnit.MILLISECONDS);
    }


    @Override
    public void destroy() {
        if (historyTask != null) {
            historyTask.cancel(false);
            historyTask = null;
        }
        if (ownExecutor != null) {
            ownExecutor.shutdown();
            ownExecutor = null;
        }
        try {
            mBeanServer.removeNotificationListener(new ObjectName("JMImplementation:type=MBeanServerDelegate"), this,
                    null, null);
        } catch (Exception e) {
            log(sm.getString("manager2.error.jmx"), e);
        }
    }


    // ------------------------------------------------------------ Request API


    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String path = request.getServletPath();
        String info = request.getPathInfo();
        if (info != null && !info.isEmpty()) {
            path = path + info;
        }

        try {
            if (path.startsWith("/api/info")) {
                Api.json(response, info());
            } else if (path.startsWith("/api/csrf")) {
                String token = (String) request.getSession(false).getAttribute(Constants.CSRF_TOKEN_SESSION_KEY);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("token", token);
                Api.json(response, payload);
            } else if (path.startsWith("/api/status/apps/")) {
                // The context path is taken from the "path" query parameter
                // when present (consistent with the rest of the API, where
                // an empty value means the ROOT context), otherwise from
                // the path segment.
                String contextParam = request.getParameter("path");
                String hostParam = request.getParameter("host");
                String contextPath;
                String hostName;
                if (contextParam != null) {
                    contextPath = contextParam;
                    hostName = hostParam != null ? hostParam : host.getName();
                } else {
                    String rest = path.substring("/api/status/apps/".length());
                    int slash = rest.lastIndexOf('/');
                    String segment = slash >= 0 ? rest.substring(0, slash) : rest;
                    hostName = slash >= 0 ? rest.substring(slash + 1) : host.getName();
                    contextPath = segment.equals("root") ? "" : segment;
                    if (!contextPath.isEmpty() && !contextPath.startsWith("/")) {
                        contextPath = "/" + contextPath;
                    }
                }
                if (!hostName.equals(host.getName())) {
                    Api.notFound(response);
                    return;
                }
                Map<String, Object> detail = StatusSnapshot.application(mBeanServer, hostName, contextPath);
                if (detail == null) {
                    Api.notFound(response);
                } else {
                    Api.json(response, detail);
                }
            } else if (path.startsWith("/api/status/workers")) {
                Api.json(response, StatusSnapshot.workers(mBeanServer, requestProcessors));
            } else if (path.startsWith("/api/status/history")) {
                if (history == null) {
                    Api.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "NOT_AVAILABLE",
                            "The status history is not available.");
                } else {
                    Api.json(response, history.payload());
                }
            } else if (path.startsWith("/api/status/system")) {
                Api.json(response, StatusSnapshot.system());
            } else if (path.startsWith("/api/status")) {
                Api.json(response, StatusSnapshot.snapshot(mBeanServer, threadPools, host));
            } else {
                Api.notFound(response);
            }
        } catch (Exception e) {
            log(sm.getString("manager2.error.status"), e);
            throw new ServletException(e);
        }
    }


    private Map<String, Object> info() {

        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("info", ServerInfo.getServerInfo());
        server.put("javaRuntimeVersion", System.getProperty("java.runtime.version"));
        server.put("javaVmVendor", System.getProperty("java.vm.vendor"));
        result.put("server", server);

        Map<String, Object> os = new LinkedHashMap<>();
        os.put("name", System.getProperty("os.name"));
        os.put("version", System.getProperty("os.version"));
        os.put("arch", System.getProperty("os.arch"));
        result.put("os", os);

        Map<String, Object> runtime = new LinkedHashMap<>();
        long startTime = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        runtime.put("startTime", Long.valueOf(startTime));
        runtime.put("uptimeMs", Long.valueOf(System.currentTimeMillis() - startTime));
        runtime.put("availableProcessors", Integer.valueOf(Runtime.getRuntime().availableProcessors()));
        result.put("runtime", runtime);

        Map<String, Object> hostInfo = new LinkedHashMap<>();
        hostInfo.put("name", host != null ? host.getName() : null);
        try {
            java.net.InetAddress address = java.net.InetAddress.getLocalHost();
            hostInfo.put("hostName", address.getHostName());
            hostInfo.put("ipAddress", address.getHostAddress());
        } catch (java.net.UnknownHostException e) {
            hostInfo.put("hostName", "-");
            hostInfo.put("ipAddress", "-");
        }
        result.put("host", hostInfo);

        return result;
    }


    /**
     * One tick of the background collection: take a snapshot of the current runtime state and add it to the history. A
     * failure must never escape: with {@code scheduleAtFixedRate} an exception in one execution would suppress all
     * following ones.
     */
    private void recordHistory() {
        try {
            history.record(StatusSnapshot.snapshot(mBeanServer, threadPools, host));
        } catch (Exception e) {
            log(sm.getString("manager2.error.status"), e);
        }
    }


    /**
     * Read a positive {@code long} init parameter, falling back to the given default (with a warning) when the
     * parameter is missing, not a number or below the given minimum.
     *
     * @param name         the init parameter name
     * @param defaultValue the value to use when the parameter is absent or invalid
     * @param minimum      the smallest acceptable value (inclusive)
     * 
     * @return the effective value
     */
    private long getLongInitParameter(String name, long defaultValue, long minimum) {
        String value = getServletConfig().getInitParameter(name);
        if (value != null && !value.isEmpty()) {
            try {
                long parsed = Long.parseLong(value.trim());
                if (parsed >= minimum) {
                    return parsed;
                }
            } catch (NumberFormatException e) {
                // Fall through to the default
            }
            log(sm.getString("manager2.history.invalidParameter", name, value));
        }
        return defaultValue;
    }


    // -------------------------------------------- NotificationListener API


    @Override
    public void handleNotification(Notification notification, Object handback) {

        if (notification instanceof MBeanServerNotification) {
            ObjectName objectName = ((MBeanServerNotification) notification).getMBeanName();
            String type = objectName.getKeyProperty("type");
            if (type == null) {
                return;
            }
            if (MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(notification.getType())) {
                switch (type) {
                    case "ThreadPool" -> threadPools.add(objectName);
                    case "GlobalRequestProcessor" -> globalRequestProcessors.add(objectName);
                    case "RequestProcessor" -> requestProcessors.add(objectName);
                    default -> {
                    }
                }
            } else if (MBeanServerNotification.UNREGISTRATION_NOTIFICATION.equals(notification.getType())) {
                switch (type) {
                    case "ThreadPool" -> threadPools.remove(objectName);
                    case "GlobalRequestProcessor" -> globalRequestProcessors.remove(objectName);
                    case "RequestProcessor" -> requestProcessors.remove(objectName);
                    default -> {
                    }
                }
            }
        }
    }


    private static List<ObjectName> queryNames(MBeanServer mBeanServer, String query) throws Exception {
        List<ObjectName> result = new ArrayList<>();
        Set<ObjectInstance> instances = mBeanServer.queryMBeans(new ObjectName(query), null);
        for (ObjectInstance instance : instances) {
            result.add(instance.getObjectName());
        }
        return result;
    }


    @Override
    public String getServletInfo() {
        return "Manager2 Status API";
    }
}

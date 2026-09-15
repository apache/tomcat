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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Context;
import org.apache.catalina.Container;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;


/**
 * Collects runtime statistics from the platform and JMX MBeans and turns them into {@code Map}/{@code List} structures
 * that are JSON-serialized by {@link Json}. The MBean queries mirror the ones used by
 * {@link org.apache.catalina.manager.StatusTransformer}.
 */
public final class StatusSnapshot {


    /**
     * Build the compact live snapshot served by {@code GET /api/status}.
     *
     * @param mBeanServer the MBean server
     * @param threadPools ObjectNames of the connector thread pools
     * @param host        the Host the API is installed in (may be null, in which case the application summary is empty)
     * 
     * @return the snapshot
     */
    public static Map<String, Object> snapshot(MBeanServer mBeanServer, List<ObjectName> threadPools, Host host)
            throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ts", Long.valueOf(System.currentTimeMillis()));
        result.put("jvm", jvm());
        result.put("connectors", connectors(mBeanServer, threadPools));
        result.put("apps", apps(host));
        return result;
    }


    /**
     * Build the live per-socket (RequestProcessor) table served by {@code GET /api/status/workers}.
     *
     * @param mBeanServer       the MBean server
     * @param requestProcessors ObjectNames of the request processors
     * 
     * @return the worker table
     */
    public static List<Map<String, Object>> workers(MBeanServer mBeanServer, List<ObjectName> requestProcessors)
            throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ObjectName oname : requestProcessors) {
            Map<String, Object> worker = new LinkedHashMap<>();
            Integer stageValue;
            try {
                stageValue = (Integer) mBeanServer.getAttribute(oname, "stage");
            } catch (Exception e) {
                // Processor went away while we were reading it
                continue;
            }
            int stage = stageValue.intValue();
            String stageStr;
            boolean fullStatus = true;
            boolean showRequest = true;
            switch (stage) {
                case org.apache.coyote.Constants.STAGE_PARSE:
                case org.apache.coyote.Constants.STAGE_PREPARE:
                    stageStr = "P";
                    fullStatus = false;
                    break;
                case org.apache.coyote.Constants.STAGE_SERVICE:
                    stageStr = "S";
                    break;
                case org.apache.coyote.Constants.STAGE_ENDINPUT:
                case org.apache.coyote.Constants.STAGE_ENDOUTPUT:
                    stageStr = "F";
                    break;
                case org.apache.coyote.Constants.STAGE_ENDED:
                case org.apache.coyote.Constants.STAGE_NEW:
                    stageStr = "R";
                    fullStatus = false;
                    break;
                case org.apache.coyote.Constants.STAGE_KEEPALIVE:
                    stageStr = "K";
                    showRequest = false;
                    break;
                default:
                    stageStr = "?";
                    fullStatus = false;
            }
            worker.put("stage", stageStr);
            if (fullStatus) {
                worker.put("time", number(mBeanServer.getAttribute(oname, "requestProcessingTime")));
                worker.put("bytesSent",
                        showRequest ? number(mBeanServer.getAttribute(oname, "requestBytesSent")) : null);
                worker.put("bytesReceived",
                        showRequest ? number(mBeanServer.getAttribute(oname, "requestBytesReceived")) : null);
                worker.put("remoteAddrForwarded", string(mBeanServer.getAttribute(oname, "remoteAddrForwarded")));
                worker.put("remoteAddr", string(mBeanServer.getAttribute(oname, "remoteAddr")));
                worker.put("virtualHost", string(mBeanServer.getAttribute(oname, "virtualHost")));
                if (showRequest) {
                    worker.put("method", string(mBeanServer.getAttribute(oname, "method")));
                    worker.put("uri", string(mBeanServer.getAttribute(oname, "currentUri")));
                    worker.put("queryString", string(mBeanServer.getAttribute(oname, "currentQueryString")));
                    worker.put("protocol", string(mBeanServer.getAttribute(oname, "protocol")));
                }
            }
            result.add(worker);
        }
        return result;
    }


    /**
     * Build the detailed per-application state served by {@code GET /api/status/apps/{path}}.
     *
     * @param mBeanServer the MBean server
     * @param hostName    the name of the host the context is installed in
     * @param contextPath the context path (empty string for ROOT)
     * 
     * @return the detailed state, or {@code null} if the context is not (any longer) deployed
     */
    public static Map<String, Object> application(MBeanServer mBeanServer, String hostName, String contextPath)
            throws Exception {
        // The WebModule MBean is keyed by "name" = "//" + host + context
        // (see StandardContext.getObjectNameKeyProperties()).
        String webModuleKey = "//" + hostName +
                (contextPath.startsWith("/") ? contextPath : (contextPath.isEmpty() ? "/" : "/" + contextPath));
        ObjectName contextOn = findWebModule(mBeanServer, webModuleKey);
        if (contextOn == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", hostName + contextPath);
        result.put("state", string(mBeanServer.getAttribute(contextOn, "stateName")));
        Long startTime = (Long) mBeanServer.getAttribute(contextOn, "startTime");
        result.put("startTime", startTime);
        result.put("startupTime", number(mBeanServer.getAttribute(contextOn, "startupTime")));
        result.put("tldScanTime", number(mBeanServer.getAttribute(contextOn, "tldScanTime")));

        ObjectName managerOn = findUnique(mBeanServer,
                contextOn.getDomain() + ":type=Manager,context=" + contextPath + ",host=" + hostName + ",*");
        if (managerOn != null) {
            Map<String, Object> manager = new LinkedHashMap<>();
            manager.put("activeSessions", number(mBeanServer.getAttribute(managerOn, "activeSessions")));
            manager.put("sessionCounter", number(mBeanServer.getAttribute(managerOn, "sessionCounter")));
            manager.put("maxActive", number(mBeanServer.getAttribute(managerOn, "maxActive")));
            manager.put("rejectedSessions", number(mBeanServer.getAttribute(managerOn, "rejectedSessions")));
            manager.put("expiredSessions", number(mBeanServer.getAttribute(managerOn, "expiredSessions")));
            manager.put("sessionMaxAliveTime", number(mBeanServer.getAttribute(managerOn, "sessionMaxAliveTime")));
            manager.put("sessionAverageAliveTime",
                    number(mBeanServer.getAttribute(managerOn, "sessionAverageAliveTime")));
            manager.put("processingTime", number(mBeanServer.getAttribute(managerOn, "processingTime")));
            result.put("manager", manager);
        }

        Set<ObjectName> jspMonitorOns = mBeanServer.queryNames(
                new ObjectName(contextOn.getDomain() + ":type=JspMonitor,WebModule=" + webModuleKey + ",*"), null);
        long jspCount = 0;
        long jspReloadCount = 0;
        for (ObjectName jspMonitorOn : jspMonitorOns) {
            jspCount += ((Integer) mBeanServer.getAttribute(jspMonitorOn, "jspCount")).intValue();
            jspReloadCount += ((Integer) mBeanServer.getAttribute(jspMonitorOn, "jspReloadCount")).intValue();
        }
        if (jspMonitorOns.size() > 0) {
            Map<String, Object> jsp = new LinkedHashMap<>();
            jsp.put("jspCount", Long.valueOf(jspCount));
            jsp.put("jspReloadCount", Long.valueOf(jspReloadCount));
            result.put("jsp", jsp);
        }

        String servletQuery = contextOn.getDomain() + ":j2eeType=Servlet,WebModule=" + webModuleKey + ",*";
        Set<javax.management.ObjectInstance> servlets = mBeanServer.queryMBeans(new ObjectName(servletQuery), null);
        List<Map<String, Object>> wrappers = new ArrayList<>();
        for (javax.management.ObjectInstance oi : servlets) {
            ObjectName wrapperOn = oi.getObjectName();
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("name", oi.getObjectName().getKeyProperty("name"));
            String[] mappings = (String[]) mBeanServer.invoke(wrapperOn, "findMappings", null, null);
            if (mappings != null && mappings.length > 0) {
                wrapper.put("mappings", List.of(mappings));
            }
            wrapper.put("processingTime", number(mBeanServer.getAttribute(wrapperOn, "processingTime")));
            wrapper.put("maxTime", number(mBeanServer.getAttribute(wrapperOn, "maxTime")));
            wrapper.put("requestCount", number(mBeanServer.getAttribute(wrapperOn, "requestCount")));
            wrapper.put("errorCount", number(mBeanServer.getAttribute(wrapperOn, "errorCount")));
            wrapper.put("loadTime", number(mBeanServer.getAttribute(wrapperOn, "loadTime")));
            wrapper.put("classLoadTime", number(mBeanServer.getAttribute(wrapperOn, "classLoadTime")));
            wrappers.add(wrapper);
        }
        wrappers.sort((a, b) -> String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name"))));
        result.put("wrappers", wrappers);

        return result;
    }


    private static Map<String, Object> jvm() {
        Map<String, Object> result = new LinkedHashMap<>();

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("used", Long.valueOf(heap.getUsed()));
        memory.put("committed", Long.valueOf(heap.getCommitted()));
        memory.put("max", Long.valueOf(heap.getMax()));
        result.put("memory", memory);
        result.put("nonHeapUsed", Long.valueOf(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed()));

        Map<String, MemoryPoolMXBean> pools = new TreeMap<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            pools.put(pool.getType().toString() + ":" + pool.getName(), pool);
        }
        List<Map<String, Object>> poolList = new ArrayList<>();
        for (MemoryPoolMXBean pool : pools.values()) {
            MemoryUsage usage = pool.getUsage();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", pool.getName());
            entry.put("type", pool.getType().toString());
            entry.put("init", Long.valueOf(usage.getInit()));
            entry.put("committed", Long.valueOf(usage.getCommitted()));
            entry.put("max", Long.valueOf(usage.getMax()));
            entry.put("used", Long.valueOf(usage.getUsed()));
            poolList.add(entry);
        }
        result.put("pools", poolList);
        return result;
    }


    private static List<Map<String, Object>> connectors(MBeanServer mBeanServer, List<ObjectName> threadPools)
            throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ObjectName pool : threadPools) {
            String name = pool.getKeyProperty("name");

            Map<String, Object> connector = new LinkedHashMap<>();
            connector.put("name", name);

            Map<String, Object> threads = new LinkedHashMap<>();
            threads.put("max", number(mBeanServer.getAttribute(pool, "maxThreads")));
            threads.put("current", number(mBeanServer.getAttribute(pool, "currentThreadCount")));
            threads.put("busy", number(mBeanServer.getAttribute(pool, "currentThreadsBusy")));
            threads.put("keepAlive", number(mBeanServer.getAttribute(pool, "keepAliveCount")));
            connector.put("threads", threads);

            ObjectName group = findRequestGroup(mBeanServer, name);
            if (group != null) {
                Map<String, Object> requests = new LinkedHashMap<>();
                requests.put("maxTime", number(mBeanServer.getAttribute(group, "maxTime")));
                requests.put("processingTime", number(mBeanServer.getAttribute(group, "processingTime")));
                requests.put("count", number(mBeanServer.getAttribute(group, "requestCount")));
                requests.put("errors", number(mBeanServer.getAttribute(group, "errorCount")));
                requests.put("bytesReceived", number(mBeanServer.getAttribute(group, "bytesReceived")));
                requests.put("bytesSent", number(mBeanServer.getAttribute(group, "bytesSent")));
                connector.put("requests", requests);
            }
            result.add(connector);
        }
        return result;
    }


    private static List<Map<String, Object>> apps(Host host) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (host == null) {
            return result;
        }
        Container[] children = host.findChildren();
        for (Container child : children) {
            Context context = (Context) child;
            Map<String, Object> app = new LinkedHashMap<>();
            app.put("path", context.getPath());
            app.put("host", host.getName());
            app.put("state", context.getState().toString());
            Manager manager = context.getManager();
            app.put("activeSessions", manager != null ? Long.valueOf(manager.getActiveSessions()) : 0L);
            result.add(app);
        }
        return result;
    }


    private static ObjectName findWebModule(MBeanServer mBeanServer, String webModuleKey) throws Exception {
        Set<ObjectName> names = mBeanServer
                .queryNames(new ObjectName("*:j2eeType=WebModule,name=" + webModuleKey + ",*"), null);
        for (ObjectName name : names) {
            return name;
        }
        return null;
    }


    private static ObjectName findUnique(MBeanServer mBeanServer, String query) throws Exception {
        ObjectName result = null;
        for (ObjectName name : mBeanServer.queryNames(new ObjectName(query), null)) {
            result = name;
        }
        return result;
    }


    private static ObjectName findRequestGroup(MBeanServer mBeanServer, String connectorName) throws Exception {
        for (ObjectName name : mBeanServer.queryNames(new ObjectName("*:type=GlobalRequestProcessor,*"), null)) {
            if (connectorName.equals(name.getKeyProperty("name")) && name.getKeyProperty("Upgrade") == null) {
                return name;
            }
        }
        return null;
    }


    private static Long number(Object value) {
        if (value instanceof Number n) {
            return Long.valueOf(n.longValue());
        }
        return 0L;
    }


    private static String string(Object value) {
        return value == null ? null : value.toString();
    }


    private StatusSnapshot() {
        // Utility class, do not instantiate
    }
}

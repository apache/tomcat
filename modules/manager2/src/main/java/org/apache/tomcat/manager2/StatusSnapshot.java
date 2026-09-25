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
import java.lang.reflect.Method;
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


    /*
     * The CPU loads, the physical memory and the swap of the machine are only
     * exposed by the HotSpot specific MBean
     * com.sun.management.OperatingSystemMXBean (jdk.management module). It is
     * accessed reflectively rather than directly, for two reasons:
     * <ul>
     * <li>JVMs without the module (embedded JVMs, stripped runtime images)
     * are handled without any linkage error: the metrics are simply reported
     * as unavailable;</li>
     * <li>the same code backports cleanly to older Tomcat branches running on
     * older Java releases: each accessor names the methods newest first, with
     * the names of older releases as fallbacks (the getters for the total /
     * free physical memory and the swap space have existed since Java 6,
     * getProcessCpuLoad since Java 10, getCpuLoad since Java 14 where it
     * replaced getSystemCpuLoad).</li>
     * </ul>
     * A resolved method is null when neither name exists on the running JVM,
     * which the callers report as an unavailable metric.
     */

    private static final Class<?> SUN_OS_MXBEAN = findSunOsMxBean();

    private static final Method SUN_CPU_LOAD = sunMethod("getCpuLoad", "getSystemCpuLoad");
    private static final Method SUN_PROCESS_CPU_LOAD = sunMethod("getProcessCpuLoad");
    private static final Method SUN_PHYSICAL_TOTAL = sunMethod("getTotalMemorySize",
            "getTotalPhysicalMemorySize");
    private static final Method SUN_PHYSICAL_FREE = sunMethod("getFreeMemorySize", "getFreePhysicalMemorySize");
    private static final Method SUN_SWAP_TOTAL = sunMethod("getTotalSwapSpaceSize");
    private static final Method SUN_SWAP_FREE = sunMethod("getFreeSwapSpaceSize");


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
     * Build the instant CPU and memory snapshot served by {@code GET /api/status/system}. Unlike the compact live
     * snapshot this is not collected into the history; it is computed on demand, so the values reflect the moment of
     * the request.
     *
     * @return the snapshot
     */
    public static Map<String, Object> system() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ts", Long.valueOf(System.currentTimeMillis()));
        result.put("cpu", cpu());
        result.put("memory", memory());
        return result;
    }


    private static Map<String, Object> cpu() {
        Map<String, Object> result = new LinkedHashMap<>();

        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        result.put("availableProcessors", Integer.valueOf(os.getAvailableProcessors()));

        // The load average is not available on all platforms (negative = not available).
        double loadAverage = os.getSystemLoadAverage();
        result.put("loadAverage", loadAverage >= 0 ? Double.valueOf(loadAverage) : null);

        // The CPU loads are only exposed by the HotSpot specific MBean. They
        // are negative before the first monitoring interval completes.
        Number systemLoad = sunInvoke(SUN_CPU_LOAD, os);
        result.put("systemLoad", systemLoad != null && systemLoad.doubleValue() >= 0
                ? Double.valueOf(systemLoad.doubleValue()) : null);
        Number processLoad = sunInvoke(SUN_PROCESS_CPU_LOAD, os);
        result.put("processLoad", processLoad != null && processLoad.doubleValue() >= 0
                ? Double.valueOf(processLoad.doubleValue()) : null);

        java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        result.put("threads", Integer.valueOf(threads.getThreadCount()));
        result.put("daemonThreads", Integer.valueOf(threads.getDaemonThreadCount()));
        result.put("peakThreads", Integer.valueOf(threads.getPeakThreadCount()));

        return result;
    }


    private static Map<String, Object> memory() {
        Map<String, Object> result = new LinkedHashMap<>();

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Map<String, Object> heapMap = new LinkedHashMap<>();
        heapMap.put("used", Long.valueOf(heap.getUsed()));
        heapMap.put("committed", Long.valueOf(heap.getCommitted()));
        heapMap.put("max", Long.valueOf(heap.getMax()));
        result.put("heap", heapMap);

        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        Map<String, Object> nonHeapMap = new LinkedHashMap<>();
        nonHeapMap.put("used", Long.valueOf(nonHeap.getUsed()));
        nonHeapMap.put("committed", Long.valueOf(nonHeap.getCommitted()));
        result.put("nonHeap", nonHeapMap);

        // The physical memory and swap of the machine are only exposed by the
        // HotSpot specific MBean.
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Number physicalTotal = sunInvoke(SUN_PHYSICAL_TOTAL, os);
        Number physicalFree = sunInvoke(SUN_PHYSICAL_FREE, os);
        if (physicalTotal != null && physicalFree != null) {
            Map<String, Object> physical = new LinkedHashMap<>();
            physical.put("total", Long.valueOf(physicalTotal.longValue()));
            physical.put("free", Long.valueOf(physicalFree.longValue()));
            result.put("physical", physical);
        } else {
            result.put("physical", null);
        }

        Number swapTotal = sunInvoke(SUN_SWAP_TOTAL, os);
        Number swapFree = sunInvoke(SUN_SWAP_FREE, os);
        if (swapTotal != null && swapFree != null) {
            Map<String, Object> swap = new LinkedHashMap<>();
            swap.put("total", Long.valueOf(swapTotal.longValue()));
            swap.put("free", Long.valueOf(swapFree.longValue()));
            result.put("swap", swap);
        } else {
            result.put("swap", null);
        }

        result.put("pools", memoryPools());
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

        result.put("pools", memoryPools());
        return result;
    }


    private static List<Map<String, Object>> memoryPools() {
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
        return poolList;
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
            // A connector may report a negative number of busy threads on startup in particular,
            // this is not a bug
            long busy = number(mBeanServer.getAttribute(pool, "currentThreadsBusy")).longValue();
            threads.put("busy", Long.valueOf(Math.max(0L, busy)));
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


    private static Class<?> findSunOsMxBean() {
        try {
            return Class.forName("com.sun.management.OperatingSystemMXBean");
        } catch (ClassNotFoundException | LinkageError e) {
            // No HotSpot management MBean on this JVM
            return null;
        }
    }


    private static Method sunMethod(String... names) {
        if (SUN_OS_MXBEAN == null) {
            return null;
        }
        for (String name : names) {
            try {
                return SUN_OS_MXBEAN.getMethod(name);
            } catch (ReflectiveOperationException | LinkageError e) {
                // Not in this Java release; try the next (older) name
            }
        }
        return null;
    }


    private static Number sunInvoke(Method method, Object target) {
        if (method == null || target == null || !SUN_OS_MXBEAN.isInstance(target)) {
            return null;
        }
        try {
            return (Number) method.invoke(target);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
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

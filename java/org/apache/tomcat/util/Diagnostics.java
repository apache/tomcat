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

// XXX TODO: Source code line length
// XXX TODO: StringManager
// XXX TODO: Sort logger names and system property keys in getVMInfo()
// XXX TODO: Add memory and GC MBeans to getVMInfo()
// XXX Optional: Wire setters to the manager:
//               log level setter, verbose class loading setter,
//               and threadMXBean setters.

package org.apache.tomcat.util;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
//import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
//import java.lang.management.MemoryMXBean;
//import java.lang.management.MemoryManagerMXBean;
//import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MonitorInfo;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
//import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.LoggingMXBean;

public class Diagnostics {

    private static final String INDENT1 = "  ";
    private static final String INDENT2 = "\t";
    private static final String INDENT3 = "   ";
    private static final String CRLF = "\r\n";
    private static final String vminfoSystemProperty = "java.vm.info";

    private static final org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog(Diagnostics.class);

    private static final SimpleDateFormat timeformat =
        new SimpleDateFormat("yyyy-MM-DD HH:mm:ss.SSS");

    /* Some platform MBeans */
    private static final ClassLoadingMXBean classLoadingMXBean =
        ManagementFactory.getClassLoadingMXBean();
    private static final CompilationMXBean compilationMXBean =
        ManagementFactory.getCompilationMXBean();
    private static final OperatingSystemMXBean operatingSystemMXBean =
        ManagementFactory.getOperatingSystemMXBean();
    private static final RuntimeMXBean runtimeMXBean =
        ManagementFactory.getRuntimeMXBean();
    private static final ThreadMXBean threadMXBean =
        ManagementFactory.getThreadMXBean();

    // XXX Not sure whether the following MBeans should better
    // be retrieved on demand, i.e. whether they can change
    // dynamically in the MBeanServer.
    private static final LoggingMXBean loggingMXBean =
        LogManager.getLoggingMXBean();
    // TODO - Use these
    /*
    private static final MemoryMXBean memoryMXBeans =
        ManagementFactory.getMemoryMXBean();
    private static final List<GarbageCollectorMXBean> garbageCollectorMXBean =
        ManagementFactory.getGarbageCollectorMXBeans();
    private static final List<MemoryManagerMXBean> memoryManagerMXBeans =
        ManagementFactory.getMemoryManagerMXBeans();
    private static final List<MemoryPoolMXBean> memoryPoolMXBeans =
        ManagementFactory.getMemoryPoolMXBeans();
    */

    /**
     * Check whether thread contention monitoring is enabled.
     *
     * @return true if thread contention monitoring is enabled
     */
    public static boolean isThreadContentionMonitoringEnabled() {
        return threadMXBean.isThreadContentionMonitoringEnabled();
    }

    /**
     * Enable or disable thread contention monitoring via the ThreadMxMXBean.
     *
     * @param enable whether to enable thread contention monitoring
     */
    public static void setThreadContentionMonitoringEnabled(boolean enable) {
        threadMXBean.setThreadContentionMonitoringEnabled(enable);
        boolean checkValue = threadMXBean.isThreadContentionMonitoringEnabled();
        if (enable != checkValue) {
            log.error("Could not set threadContentionMonitoringEnabled to " +
                      enable + ", got " + checkValue + " instead");
        }
    }

    /**
     * Check whether thread cpu time measurement is enabled.
     *
     * @return true if thread cpu time measurement is enabled
     */
    public static boolean isThreadCpuTimeEnabled() {
        return threadMXBean.isThreadCpuTimeEnabled();
    }

    /**
     * Enable or disable thread cpu time measurement via the ThreadMxMXBean.
     *
     * @param enable whether to enable thread cpu time measurement
     */
    public static void setThreadCpuTimeEnabled(boolean enable) {
        threadMXBean.setThreadCpuTimeEnabled(enable);
        boolean checkValue = threadMXBean.isThreadCpuTimeEnabled();
        if (enable != checkValue) {
            log.error("Could not set threadCpuTimeEnabled to " + enable +
                      ", got " + checkValue + " instead");
        }
    }

    /**
     * Reset peak thread count in ThreadMXBean
     */
    public static void resetPeakThreadCount() {
        threadMXBean.resetPeakThreadCount();
    }

    /**
     * Set verbose class loading
     *
     * @param verbose whether to enable verbose class loading
     */
    public static void setVerboseClassLoading(boolean verbose) {
        classLoadingMXBean.setVerbose(verbose);
        boolean checkValue = classLoadingMXBean.isVerbose();
        if (verbose != checkValue) {
            log.error("Could not set verbose class loading to " + verbose +
                      ", got " + checkValue + " instead");
        }
    }

    /**
     * Set logger level
     *
     * @param loggerName the name of the logger
     * @param levelName the level to set
     */
    public static void setLoggerLevel(String loggerName, String levelName) {
        loggingMXBean.setLoggerLevel(loggerName, levelName);
        String checkValue = loggingMXBean.getLoggerLevel(loggerName);
        if (!checkValue.equals(levelName)) {
            log.error("Could not set logger level for logger '" +
                      loggerName + "' to '" + levelName +
                      "', got '" + checkValue + "' instead");
        }
    }

    /**
     * Formats the thread dump header for one thread.
     *
     * @param ti the ThreadInfo describing the thread
     * @return the formatted thread dump header
     */
    private static String getThreadDumpHeader(ThreadInfo ti) {
        StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"");
        sb.append(" Id=" + ti.getThreadId());
        sb.append(" cpu=" + threadMXBean.getThreadCpuTime(ti.getThreadId()) +
                  " ns");
        sb.append(" usr=" + threadMXBean.getThreadUserTime(ti.getThreadId()) +
                  " ns");
        sb.append(" blocked " + ti.getBlockedCount() + " for " +
                  ti.getBlockedTime() + " ms");
        sb.append(" waited " + ti.getWaitedCount() + " for " +
                  ti.getWaitedTime() + " ms");

        if (ti.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (ti.isInNative()) {
            sb.append(" (running in native)");
        }
        sb.append(CRLF);
        sb.append(INDENT3 + "java.lang.Thread.State: " + ti.getThreadState());
        sb.append(CRLF);
        return sb.toString();
    }

    /**
     * Formats the thread dump for one thread.
     *
     * @param ti the ThreadInfo describing the thread
     * @return the formatted thread dump
     */
    private static String getThreadDump(ThreadInfo ti) {
        StringBuilder sb = new StringBuilder(getThreadDumpHeader(ti));
        for (LockInfo li : ti.getLockedSynchronizers()) {
            sb.append(INDENT2 + "locks " +
                      li.toString() + CRLF);
        }
        boolean start = true;
        StackTraceElement[] stes = ti.getStackTrace();
        Object[] monitorDepths = new Object[stes.length];
        MonitorInfo[] mis = ti.getLockedMonitors();
        for (int i = 0; i < mis.length; i++) {
            monitorDepths[mis[i].getLockedStackDepth()] = mis[i];
        }
        for (int i = 0; i < stes.length; i++) {
            StackTraceElement ste = stes[i];
            sb.append(INDENT2 +
                      "at " + ste.toString() + CRLF);
            if (start) {
                if (ti.getLockName() != null) {
                    sb.append(INDENT2 + "- waiting on (a " +
                              ti.getLockName() + ")");
                    if (ti.getLockOwnerName() != null) {
                        sb.append(" owned by " + ti.getLockOwnerName() +
                                  " Id=" + ti.getLockOwnerId());
                    }
                    sb.append(CRLF);
                }
                start = false;
            }
            if (monitorDepths[i] != null) {
                MonitorInfo mi = (MonitorInfo)monitorDepths[i];
                sb.append(INDENT2 +
                          "- locked (a " + mi.toString() + ")"+
                          " index " + mi.getLockedStackDepth() +
                          " frame " + mi.getLockedStackFrame().toString());
                sb.append(CRLF);

            }
        }
        return sb.toString();
    }

    /**
     * Formats the thread dump for a list of threads.
     *
     * @param tinfos the ThreadInfo array describing the thread list
     * @return the formatted thread dump
     */
    private static String getThreadDump(ThreadInfo[] tinfos) {
        StringBuilder sb = new StringBuilder();
        for (ThreadInfo tinfo : tinfos) {
            sb.append(getThreadDump(tinfo));
            sb.append(CRLF);
        }
        return sb.toString();
    }

    /**
     * Check if any threads are deadlocked. If any, print
     * the thread dump for those threads.
     *
     * @return a deadlock message and the formatted thread dump
     *         of the deadlocked threads
     */
    public static String findDeadlock() {
        ThreadInfo[] tinfos = null;
        long[] ids = threadMXBean.findDeadlockedThreads();
        if (ids != null) {
            tinfos = threadMXBean.getThreadInfo(threadMXBean.findDeadlockedThreads(),
                                                true, true);
            if (tinfos != null) {
                StringBuilder sb =
                    new StringBuilder("Deadlock found between the following threads:");
                sb.append(CRLF);
                sb.append(getThreadDump(tinfos));
                return sb.toString();
            }
        }
        return "";
    }

    /**
     * Retrieve a formatted JVM thread dump.
     * @return the thread dump
     */
    public static String getThreadDump() {
        StringBuilder sb = new StringBuilder();

        synchronized(timeformat) {
            sb.append(timeformat.format(new Date()));
        }
        sb.append(CRLF);

        sb.append("Full thread dump ");
        sb.append(runtimeMXBean.getVmName());
        sb.append(" (");
        sb.append(runtimeMXBean.getVmVersion());
        String vminfo = System.getProperty(vminfoSystemProperty);
        if (vminfo != null) {
            sb.append(" " + vminfo);
        }
        sb.append("):" + CRLF);
        sb.append(CRLF);

        ThreadInfo[] tis = threadMXBean.dumpAllThreads(true, true);
        sb.append(getThreadDump(tis));

        sb.append(findDeadlock());
        return sb.toString();
    }

    /**
     * Retrieve a formatted JVM thread dump.
     * @return the thread dump
     */
    public static String getVMInfo() {
        StringBuilder sb = new StringBuilder();

        synchronized(timeformat) {
            sb.append(timeformat.format(new Date()));
        }
        sb.append(CRLF);

        sb.append("Runtime information:" + CRLF);
        sb.append(INDENT1 + "vmName: " + runtimeMXBean.getVmName() + CRLF);
        sb.append(INDENT1 + "vmVersion: " + runtimeMXBean.getVmVersion() + CRLF);
        sb.append(INDENT1 + "vmVendor: " + runtimeMXBean.getVmVendor() + CRLF);
        sb.append(INDENT1 + "specName: " + runtimeMXBean.getSpecName() + CRLF);
        sb.append(INDENT1 + "specVersion: " + runtimeMXBean.getSpecVersion() + CRLF);
        sb.append(INDENT1 + "specVendor: " + runtimeMXBean.getSpecVendor() + CRLF);
        sb.append(INDENT1 + "managementSpecVersion: " +
                  runtimeMXBean.getManagementSpecVersion() + CRLF);
        sb.append(INDENT1 + "name: " + runtimeMXBean.getName() + CRLF);
        sb.append(INDENT1 + "startTime: " + runtimeMXBean.getStartTime() + CRLF);
        sb.append(INDENT1 + "uptime: " + runtimeMXBean.getUptime() + CRLF);
        sb.append(INDENT1 + "isBootClassPathSupported: " +
                  runtimeMXBean.isBootClassPathSupported() + CRLF);
        sb.append(CRLF);

        sb.append("OS information:" + CRLF);
        sb.append(INDENT1 + "name: " + operatingSystemMXBean.getName() + CRLF);
        sb.append(INDENT1 + "version: " + operatingSystemMXBean.getVersion() + CRLF);
        sb.append(INDENT1 + "architecture: " + operatingSystemMXBean.getArch() + CRLF);
        sb.append(INDENT1 + "availableProcessors: " +
                  operatingSystemMXBean.getAvailableProcessors() + CRLF);
        sb.append(INDENT1 + "systemLoadAverage: " +
                  operatingSystemMXBean.getSystemLoadAverage() + CRLF);
        sb.append(CRLF);

        sb.append("ThreadMXBean capabilities:" + CRLF);
        sb.append(INDENT1 + "isCurrentThreadCpuTimeSupported: " +
                  threadMXBean.isCurrentThreadCpuTimeSupported() + CRLF);
        sb.append(INDENT1 + "isThreadCpuTimeSupported: " +
                  threadMXBean.isThreadCpuTimeSupported() + CRLF);
        sb.append(INDENT1 + "isThreadCpuTimeEnabled: " +
                  threadMXBean.isThreadCpuTimeEnabled() + CRLF);
        sb.append(INDENT1 + "isObjectMonitorUsageSupported: " +
                  threadMXBean.isObjectMonitorUsageSupported() + CRLF);
        sb.append(INDENT1 + "isSynchronizerUsageSupported: " +
                  threadMXBean.isSynchronizerUsageSupported() + CRLF);
        sb.append(INDENT1 + "isThreadContentionMonitoringSupported: " +
                  threadMXBean.isThreadContentionMonitoringSupported() + CRLF);
        sb.append(INDENT1 + "isThreadContentionMonitoringEnabled: " +
                  threadMXBean.isThreadContentionMonitoringEnabled() + CRLF);
        sb.append(CRLF);

        sb.append("Thread counts:" + CRLF);
        sb.append(INDENT1 + "daemon: " + threadMXBean.getDaemonThreadCount() + CRLF);
        sb.append(INDENT1 + "total: " + threadMXBean.getThreadCount() + CRLF);
        sb.append(INDENT1 + "peak: " + threadMXBean.getPeakThreadCount() + CRLF);
        sb.append(INDENT1 + "totalStarted: " +
                  threadMXBean.getTotalStartedThreadCount() + CRLF);
        sb.append(CRLF);

        sb.append("Class loading:" + CRLF);
        sb.append(INDENT1 + "loaded: " +
                  classLoadingMXBean.getLoadedClassCount() + CRLF);
        sb.append(INDENT1 + "unloaded: " +
                  classLoadingMXBean.getUnloadedClassCount() + CRLF);
        sb.append(INDENT1 + "totalLoaded: " +
                  classLoadingMXBean.getTotalLoadedClassCount() + CRLF);
        sb.append(INDENT1 + "isVerbose: " +
                  classLoadingMXBean.isVerbose() + CRLF);
        sb.append(CRLF);

        sb.append("Class compilation:" + CRLF);
        sb.append(INDENT1 + "name: " + compilationMXBean.getName() + CRLF);
        sb.append(INDENT1 + "totalCompilationTime: " +
                  compilationMXBean.getTotalCompilationTime() + CRLF);
        sb.append(INDENT1 + "isCompilationTimeMonitoringSupported: " +
                  compilationMXBean.isCompilationTimeMonitoringSupported() + CRLF);
        sb.append(CRLF);

        sb.append("Path information:" + CRLF);
        sb.append(INDENT1 + "bootClassPath: " + runtimeMXBean.getBootClassPath() + CRLF);
        sb.append(INDENT1 + "classPath: " + runtimeMXBean.getClassPath() + CRLF);
        sb.append(INDENT1 + "libraryPath: " + runtimeMXBean.getLibraryPath() + CRLF);
        sb.append(CRLF);

        sb.append("Startup arguments:" + CRLF);
        for (String arg: runtimeMXBean.getInputArguments()) {
            sb.append(INDENT1 + arg + CRLF);
        }
        sb.append(CRLF);

        sb.append("System properties:" + CRLF);
        Map<String,String> props = runtimeMXBean.getSystemProperties();
        for (String prop: props.keySet()) {
            sb.append(INDENT1 + prop + ": " + props.get(prop) + CRLF);
        }
        sb.append(CRLF);

        sb.append("Logger information:" + CRLF);
        for (String logger: loggingMXBean.getLoggerNames()) {
            sb.append(INDENT1 + logger +
                      ": level=" + loggingMXBean.getLoggerLevel(logger) +
                      ", parent=" + loggingMXBean.getParentLoggerName(logger) + CRLF);
        }
        sb.append(CRLF);

        return sb.toString();
    }
}

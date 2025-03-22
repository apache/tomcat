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
// XXX TODO: More JavaDoc
// XXX Optional: Add support for com.sun.management specific mbean
//               (http://docs.oracle.com/javase/7/docs/jre/api/management/extension/index.html)
// XXX Optional: Wire additional public static methods implemented here
//               to the manager (think about manager access roles!)
//                 setLoggerLevel(),
//                 setVerboseClassLoading(),
//                 setThreadContentionMonitoringEnabled(),
//                 setThreadCpuTimeEnabled(),
//                 resetPeakThreadCount(),
//                 setVerboseGarbageCollection()
//                 gc(),
//                 resetPeakUsage(),
//                 setUsageThreshold(),
//                 setCollectionUsageThreshold()

package org.apache.tomcat.util;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MonitorInfo;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.PlatformLoggingMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class Diagnostics {

    private static final String PACKAGE = "org.apache.tomcat.util";
    private static final StringManager sm = StringManager.getManager(PACKAGE);

    private static final String INDENT1 = "  ";
    private static final String INDENT2 = "\t";
    private static final String INDENT3 = "   ";
    private static final String CRLF = "\r\n";
    private static final String vminfoSystemProperty = "java.vm.info";

    private static final Log log = LogFactory.getLog(Diagnostics.class);

    private static final SimpleDateFormat timeformat =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

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
    private static final PlatformLoggingMXBean loggingMXBean =
        ManagementFactory.getPlatformMXBean(PlatformLoggingMXBean.class);
    private static final MemoryMXBean memoryMXBean =
        ManagementFactory.getMemoryMXBean();
    private static final List<GarbageCollectorMXBean> garbageCollectorMXBeans =
        ManagementFactory.getGarbageCollectorMXBeans();
    private static final List<MemoryManagerMXBean> memoryManagerMXBeans =
        ManagementFactory.getMemoryManagerMXBeans();
    private static final List<MemoryPoolMXBean> memoryPoolMXBeans =
        ManagementFactory.getMemoryPoolMXBeans();

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
            log.error(sm.getString("diagnostics.setPropertyFail", "threadContentionMonitoringEnabled",
                    Boolean.valueOf(enable), Boolean.valueOf(checkValue)));
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
            log.error(sm.getString("diagnostics.setPropertyFail", "threadCpuTimeEnabled",
                    Boolean.valueOf(enable), Boolean.valueOf(checkValue)));
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
            log.error(sm.getString("diagnostics.setPropertyFail", "verboseClassLoading",
                    Boolean.valueOf(verbose), Boolean.valueOf(checkValue)));
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
            String propertyName = "loggerLevel[" + loggerName + "]";
            log.error(sm.getString("diagnostics.setPropertyFail", propertyName,
                    levelName, checkValue));
        }
    }

    /**
     * Set verbose garbage collection logging
     *
     * @param verbose whether to enable verbose gc logging
     */
    public static void setVerboseGarbageCollection(boolean verbose) {
        memoryMXBean.setVerbose(verbose);
        boolean checkValue = memoryMXBean.isVerbose();
        if (verbose != checkValue) {
            log.error(sm.getString("diagnostics.setPropertyFail", "verboseGarbageCollection",
                    Boolean.valueOf(verbose), Boolean.valueOf(checkValue)));
        }
    }

    /**
     * Initiate garbage collection via MX Bean
     */
    public static void gc() {
        memoryMXBean.gc();
    }

    /**
     * Reset peak memory usage data in MemoryPoolMXBean
     *
     * @param name name of the MemoryPoolMXBean or "all"
     */
    public static void resetPeakUsage(String name) {
        for (MemoryPoolMXBean mbean: memoryPoolMXBeans) {
            if (name.equals("all") || name.equals(mbean.getName())) {
                mbean.resetPeakUsage();
            }
        }
    }

    /**
     * Set usage threshold in MemoryPoolMXBean
     *
     * @param name name of the MemoryPoolMXBean
     * @param threshold the threshold to set
     * @return true if setting the threshold succeeded
     */
    public static boolean setUsageThreshold(String name, long threshold) {
        for (MemoryPoolMXBean mbean: memoryPoolMXBeans) {
            if (name.equals(mbean.getName())) {
                try {
                    mbean.setUsageThreshold(threshold);
                    return true;
                } catch (IllegalArgumentException | UnsupportedOperationException ex) {
                    // IGNORE
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Set collection usage threshold in MemoryPoolMXBean
     *
     * @param name name of the MemoryPoolMXBean
     * @param threshold the collection threshold to set
     * @return true if setting the threshold succeeded
     */
    public static boolean setCollectionUsageThreshold(String name, long threshold) {
        for (MemoryPoolMXBean mbean: memoryPoolMXBeans) {
            if (name.equals(mbean.getName())) {
                try {
                    mbean.setCollectionUsageThreshold(threshold);
                    return true;
                } catch (IllegalArgumentException | UnsupportedOperationException ex) {
                    // IGNORE
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Formats the thread dump header for one thread.
     *
     * @param ti the ThreadInfo describing the thread
     * @return the formatted thread dump header
     */
    private static String getThreadDumpHeader(ThreadInfo ti) {
        StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"");
        sb.append(" Id=").append(ti.getThreadId());
        sb.append(" cpu=").append(threadMXBean.getThreadCpuTime(ti.getThreadId())).append(" ns");
        sb.append(" usr=").append(threadMXBean.getThreadUserTime(ti.getThreadId())).append(" ns");
        sb.append(" blocked ").append(ti.getBlockedCount()).append(" for ").append(ti.getBlockedTime()).append(" ms");
        sb.append(" waited ").append(ti.getWaitedCount()).append(" for ").append(ti.getWaitedTime()).append(" ms");

        if (ti.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (ti.isInNative()) {
            sb.append(" (running in native)");
        }
        sb.append(CRLF);
        sb.append(INDENT3 + "java.lang.Thread.State: ").append(ti.getThreadState());
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
            sb.append(INDENT2 + "locks ").append(li.toString()).append(CRLF);
        }
        boolean start = true;
        StackTraceElement[] stes = ti.getStackTrace();
        Object[] monitorDepths = new Object[stes.length];
        MonitorInfo[] mis = ti.getLockedMonitors();
        for (MonitorInfo monitorInfo : mis) {
            monitorDepths[monitorInfo.getLockedStackDepth()] = monitorInfo;
        }
        for (int i = 0; i < stes.length; i++) {
            StackTraceElement ste = stes[i];
            sb.append(INDENT2 + "at ").append(ste.toString()).append(CRLF);
            if (start) {
                if (ti.getLockName() != null) {
                    sb.append(INDENT2 + "- waiting on (a ").append(ti.getLockName()).append(")");
                    if (ti.getLockOwnerName() != null) {
                        sb.append(" owned by ").append(ti.getLockOwnerName()).append(" Id=").append(ti.getLockOwnerId());
                    }
                    sb.append(CRLF);
                }
                start = false;
            }
            if (monitorDepths[i] != null) {
                MonitorInfo mi = (MonitorInfo)monitorDepths[i];
                sb.append(INDENT2 + "- locked (a ").append(mi.toString()).append(")").append(" index ");
                sb.append(mi.getLockedStackDepth()).append(" frame ").append(mi.getLockedStackFrame().toString());
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
        long[] ids = threadMXBean.findDeadlockedThreads();
        if (ids != null) {
            ThreadInfo[] tinfos = threadMXBean.getThreadInfo(threadMXBean.findDeadlockedThreads(),
                                                true, true);
            if (tinfos != null) {
                StringBuilder sb =
                    new StringBuilder(sm.getString("diagnostics.deadlockFound"));
                sb.append(CRLF);
                sb.append(getThreadDump(tinfos));
                return sb.toString();
            }
        }
        return "";
    }

    /**
     * Retrieves a formatted JVM thread dump.
     * The default StringManager will be used.
     *
     * @return the formatted JVM thread dump
     */
    public static String getThreadDump() {
        return getThreadDump(sm);
    }

    /**
     * Retrieves a formatted JVM thread dump.
     * The given list of locales will be used
     * to retrieve a StringManager.
     *
     * @param requestedLocales list of locales to use
     * @return the formatted JVM thread dump
     */
    public static String getThreadDump(Enumeration<Locale> requestedLocales) {
        return getThreadDump(
                StringManager.getManager(PACKAGE, requestedLocales));
    }

    /**
     * Retrieve a JVM thread dump formatted
     * using the given StringManager.
     *
     * @param requestedSm the StringManager to use
     * @return the formatted JVM thread dump
     */
    public static String getThreadDump(StringManager requestedSm) {
        StringBuilder sb = new StringBuilder();

        synchronized(timeformat) {
            sb.append(timeformat.format(new Date()));
        }
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.threadDumpTitle"));
        sb.append(' ');
        sb.append(runtimeMXBean.getVmName());
        sb.append(" (");
        sb.append(runtimeMXBean.getVmVersion());
        String vminfo = System.getProperty(vminfoSystemProperty);
        if (vminfo != null) {
            sb.append(" ").append(vminfo);
        }
        sb.append("):" + CRLF);
        sb.append(CRLF);

        ThreadInfo[] tis = threadMXBean.dumpAllThreads(true, true);
        sb.append(getThreadDump(tis));

        sb.append(findDeadlock());
        return sb.toString();
    }

    /**
     * Format contents of a MemoryUsage object.
     * @param name a text prefix used in formatting
     * @param usage the MemoryUsage object to format
     * @return the formatted contents
     */
    private static String formatMemoryUsage(String name, MemoryUsage usage) {
        if (usage != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(INDENT1).append(name).append(" init: ").append(usage.getInit()).append(CRLF);
            sb.append(INDENT1).append(name).append(" used: ").append(usage.getUsed()).append(CRLF);
            sb.append(INDENT1).append(name).append(" committed: ").append(usage.getCommitted()).append(CRLF);
            sb.append(INDENT1).append(name).append(" max: ").append(usage.getMax()).append(CRLF);
            return sb.toString();
        }
        return "";
    }

    /**
     * Retrieves a formatted JVM information text.
     * The default StringManager will be used.
     *
     * @return the formatted JVM information text
     */
    public static String getVMInfo() {
        return getVMInfo(sm);
    }

    /**
     * Retrieves a formatted JVM information text.
     * The given list of locales will be used
     * to retrieve a StringManager.
     *
     * @param requestedLocales list of locales to use
     * @return the formatted JVM information text
     */
    public static String getVMInfo(Enumeration<Locale> requestedLocales) {
        return getVMInfo(StringManager.getManager(PACKAGE, requestedLocales));
    }

    /**
     * Retrieve a JVM information text formatted
     * using the given StringManager.
     *
     * @param requestedSm the StringManager to use
     * @return the formatted JVM information text
     */
    @SuppressWarnings("deprecation")
    public static String getVMInfo(StringManager requestedSm) {
        StringBuilder sb = new StringBuilder();

        synchronized(timeformat) {
            sb.append(timeformat.format(new Date()));
        }
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoRuntime"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "vmName: ").append(runtimeMXBean.getVmName()).append(CRLF);
        sb.append(INDENT1 + "vmVersion: ").append(runtimeMXBean.getVmVersion()).append(CRLF);
        sb.append(INDENT1 + "vmVendor: ").append(runtimeMXBean.getVmVendor()).append(CRLF);
        sb.append(INDENT1 + "specName: ").append(runtimeMXBean.getSpecName()).append(CRLF);
        sb.append(INDENT1 + "specVersion: ").append(runtimeMXBean.getSpecVersion()).append(CRLF);
        sb.append(INDENT1 + "specVendor: ").append(runtimeMXBean.getSpecVendor()).append(CRLF);
        sb.append(INDENT1 + "managementSpecVersion: ").append(runtimeMXBean.getManagementSpecVersion()).append(CRLF);
        sb.append(INDENT1 + "name: ").append(runtimeMXBean.getName()).append(CRLF);
        sb.append(INDENT1 + "startTime: ").append(runtimeMXBean.getStartTime()).append(CRLF);
        sb.append(INDENT1 + "uptime: ").append(runtimeMXBean.getUptime()).append(CRLF);
        sb.append(INDENT1 + "isBootClassPathSupported: ").append(runtimeMXBean.isBootClassPathSupported()).append(CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoOs"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "name: ").append(operatingSystemMXBean.getName()).append(CRLF);
        sb.append(INDENT1 + "version: ").append(operatingSystemMXBean.getVersion()).append(CRLF);
        sb.append(INDENT1 + "architecture: ").append(operatingSystemMXBean.getArch()).append(CRLF);
        sb.append(INDENT1 + "availableProcessors: ").append(operatingSystemMXBean.getAvailableProcessors()).append(CRLF);
        sb.append(INDENT1 + "systemLoadAverage: ").append(operatingSystemMXBean.getSystemLoadAverage()).append(CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoThreadMxBean"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "isCurrentThreadCpuTimeSupported: ").append(threadMXBean.isCurrentThreadCpuTimeSupported()).append(CRLF);
        sb.append(INDENT1 + "isThreadCpuTimeSupported: ").append(threadMXBean.isThreadCpuTimeSupported()).append(CRLF);
        sb.append(INDENT1 + "isThreadCpuTimeEnabled: ").append(threadMXBean.isThreadCpuTimeEnabled()).append(CRLF);
        sb.append(INDENT1 + "isObjectMonitorUsageSupported: ").append(threadMXBean.isObjectMonitorUsageSupported()).append(CRLF);
        sb.append(INDENT1 + "isSynchronizerUsageSupported: ").append(threadMXBean.isSynchronizerUsageSupported()).append(CRLF);
        sb.append(INDENT1 + "isThreadContentionMonitoringSupported: ").append(threadMXBean.isThreadContentionMonitoringSupported()).append(CRLF);
        sb.append(INDENT1 + "isThreadContentionMonitoringEnabled: ").append(threadMXBean.isThreadContentionMonitoringEnabled()).append(CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoThreadCounts"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "daemon: ").append(threadMXBean.getDaemonThreadCount()).append(CRLF);
        sb.append(INDENT1 + "total: ").append(threadMXBean.getThreadCount()).append(CRLF);
        sb.append(INDENT1 + "peak: ").append(threadMXBean.getPeakThreadCount()).append(CRLF);
        sb.append(INDENT1 + "totalStarted: ").append(threadMXBean.getTotalStartedThreadCount()).append(CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoStartup"));
        sb.append(":" + CRLF);
        for (String arg: runtimeMXBean.getInputArguments()) {
            sb.append(INDENT1).append(arg).append(CRLF);
        }
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoPath"));
        sb.append(":" + CRLF);
        if (runtimeMXBean.isBootClassPathSupported()) {
            sb.append(INDENT1 + "bootClassPath: ").append(runtimeMXBean.getBootClassPath()).append(CRLF);
        }
        sb.append(INDENT1 + "classPath: ").append(runtimeMXBean.getClassPath()).append(CRLF);
        sb.append(INDENT1 + "libraryPath: ").append(runtimeMXBean.getLibraryPath()).append(CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoClassLoading"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "loaded: ").append(classLoadingMXBean.getLoadedClassCount()).append(CRLF);
        sb.append(INDENT1 + "unloaded: ").append(classLoadingMXBean.getUnloadedClassCount()).append(CRLF);
        sb.append(INDENT1 + "totalLoaded: ").append(classLoadingMXBean.getTotalLoadedClassCount()).append(CRLF);
        sb.append(INDENT1 + "isVerbose: ").append(classLoadingMXBean.isVerbose()).append(CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoClassCompilation"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "name: ").append(compilationMXBean.getName()).append(CRLF);
        sb.append(INDENT1 + "totalCompilationTime: ").append(compilationMXBean.getTotalCompilationTime()).append(CRLF);
        sb.append(INDENT1 + "isCompilationTimeMonitoringSupported: ").append(compilationMXBean.isCompilationTimeMonitoringSupported()).append(CRLF);
        sb.append(CRLF);

        for (MemoryManagerMXBean mbean: memoryManagerMXBeans) {
            sb.append(requestedSm.getString("diagnostics.vmInfoMemoryManagers", mbean.getName()));
            sb.append(":" + CRLF);
            sb.append(INDENT1 + "isValid: ").append(mbean.isValid()).append(CRLF);
            sb.append(INDENT1 + "mbean.getMemoryPoolNames: " + CRLF);
            String[] names = mbean.getMemoryPoolNames();
            Arrays.sort(names);
            for (String name: names) {
                sb.append(INDENT2).append(name).append(CRLF);
            }
            sb.append(CRLF);
        }

        for (GarbageCollectorMXBean mbean: garbageCollectorMXBeans) {
            sb.append(requestedSm.getString("diagnostics.vmInfoGarbageCollectors", mbean.getName()));
            sb.append(":" + CRLF);
            sb.append(INDENT1 + "isValid: ").append(mbean.isValid()).append(CRLF);
            sb.append(INDENT1 + "mbean.getMemoryPoolNames: " + CRLF);
            String[] names = mbean.getMemoryPoolNames();
            Arrays.sort(names);
            for (String name: names) {
                sb.append(INDENT2).append(name).append(CRLF);
            }
            sb.append(INDENT1 + "getCollectionCount: ").append(mbean.getCollectionCount()).append(CRLF);
            sb.append(INDENT1 + "getCollectionTime: ").append(mbean.getCollectionTime()).append(CRLF);
            sb.append(CRLF);
        }

        sb.append(requestedSm.getString("diagnostics.vmInfoMemory"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "isVerbose: ").append(memoryMXBean.isVerbose()).append(CRLF);
        sb.append(INDENT1 + "getObjectPendingFinalizationCount: ").append(memoryMXBean.getObjectPendingFinalizationCount()).append(CRLF);
        sb.append(formatMemoryUsage("heap", memoryMXBean.getHeapMemoryUsage()));
        sb.append(formatMemoryUsage("non-heap", memoryMXBean.getNonHeapMemoryUsage()));
        sb.append(CRLF);

        for (MemoryPoolMXBean mbean: memoryPoolMXBeans) {
            sb.append(requestedSm.getString("diagnostics.vmInfoMemoryPools", mbean.getName()));
            sb.append(":" + CRLF);
            sb.append(INDENT1 + "isValid: ").append(mbean.isValid()).append(CRLF);
            sb.append(INDENT1 + "getType: ").append(mbean.getType()).append(CRLF);
            sb.append(INDENT1 + "mbean.getMemoryManagerNames: " + CRLF);
            String[] names = mbean.getMemoryManagerNames();
            Arrays.sort(names);
            for (String name: names) {
                sb.append(INDENT2).append(name).append(CRLF);
            }
            sb.append(INDENT1 + "isUsageThresholdSupported: ").append(mbean.isUsageThresholdSupported()).append(CRLF);
            try {
                sb.append(INDENT1 + "isUsageThresholdExceeded: ").append(mbean.isUsageThresholdExceeded()).append(CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            sb.append(INDENT1 + "isCollectionUsageThresholdSupported: ").append(mbean.isCollectionUsageThresholdSupported()).append(CRLF);
            try {
                sb.append(INDENT1 + "isCollectionUsageThresholdExceeded: ").append(mbean.isCollectionUsageThresholdExceeded()).append(CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            try {
                sb.append(INDENT1 + "getUsageThreshold: ").append(mbean.getUsageThreshold()).append(CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            try {
                sb.append(INDENT1 + "getUsageThresholdCount: ").append(mbean.getUsageThresholdCount()).append(CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            try {
                sb.append(INDENT1 + "getCollectionUsageThreshold: ").append(mbean.getCollectionUsageThreshold()).append(CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            try {
                sb.append(INDENT1 + "getCollectionUsageThresholdCount: ").append(mbean.getCollectionUsageThresholdCount()).append(CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            sb.append(formatMemoryUsage("current", mbean.getUsage()));
            sb.append(formatMemoryUsage("collection", mbean.getCollectionUsage()));
            sb.append(formatMemoryUsage("peak", mbean.getPeakUsage()));
            sb.append(CRLF);
        }


        sb.append(requestedSm.getString("diagnostics.vmInfoSystem"));
        sb.append(":" + CRLF);
        Map<String,String> props = runtimeMXBean.getSystemProperties();
        ArrayList<String> keys = new ArrayList<>(props.keySet());
        Collections.sort(keys);
        for (String prop: keys) {
            sb.append(INDENT1).append(prop).append(": ").append(props.get(prop)).append(CRLF);
        }
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoLogger"));
        sb.append(":" + CRLF);
        List<String> loggers = loggingMXBean.getLoggerNames();
        Collections.sort(loggers);
        for (String logger: loggers) {
            sb.append(INDENT1).append(logger).append(": level=").append(loggingMXBean.getLoggerLevel(logger));
            sb.append(", parent=").append(loggingMXBean.getParentLoggerName(logger)).append(CRLF);
        }
        sb.append(CRLF);

        return sb.toString();
    }
}

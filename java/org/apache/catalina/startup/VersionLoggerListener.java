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
package org.apache.catalina.startup;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Logs version information on startup.
 * <p>
 * This listener must only be nested within {@link Server} elements and should be the first listener defined.
 */
public class VersionLoggerListener implements LifecycleListener {

    /**
     * Constructs a new VersionLoggerListener.
     */
    public VersionLoggerListener() {
    }

    private static final Log log = LogFactory.getLog(VersionLoggerListener.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * Whether to log JVM arguments.
     */
    private boolean logArgs = true;
    /**
     * Whether to log environment variables.
     */
    private boolean logEnv = false;
    /**
     * Whether to log system properties.
     */
    private boolean logProps = false;

    /**
     * Returns whether JVM arguments will be logged.
     *
     * @return {@code true} if JVM arguments will be logged
     */
    public boolean getLogArgs() {
        return logArgs;
    }


    /**
     * Sets whether JVM arguments will be logged.
     *
     * @param logArgs Whether to log JVM arguments
     */
    public void setLogArgs(boolean logArgs) {
        this.logArgs = logArgs;
    }


    /**
     * Returns whether environment variables will be logged.
     *
     * @return {@code true} if environment variables will be logged
     */
    public boolean getLogEnv() {
        return logEnv;
    }


    /**
     * Sets whether environment variables will be logged.
     *
     * @param logEnv Whether to log environment variables
     */
    public void setLogEnv(boolean logEnv) {
        this.logEnv = logEnv;
    }


    /**
     * Returns whether system properties will be logged.
     *
     * @return {@code true} if system properties will be logged
     */
    public boolean getLogProps() {
        return logProps;
    }


    /**
     * Sets whether system properties will be logged.
     *
     * @param logProps Whether to log system properties
     */
    public void setLogProps(boolean logProps) {
        this.logProps = logProps;
    }


    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
            if (!(event.getLifecycle() instanceof Server)) {
                log.warn(sm.getString("listener.notServer", event.getLifecycle().getClass().getSimpleName()));
            }
            log();
        }
    }


    private void log() {
        log.info(sm.getString("versionLoggerListener.serverInfo.server.version", ServerInfo.getServerInfo()));
        log.info(sm.getString("versionLoggerListener.serverInfo.server.built", ServerInfo.getServerBuilt()));
        log.info(sm.getString("versionLoggerListener.serverInfo.server.number", ServerInfo.getServerNumber()));
        log.info(sm.getString("versionLoggerListener.os.name", System.getProperty("os.name")));
        log.info(sm.getString("versionLoggerListener.os.version", System.getProperty("os.version")));
        log.info(sm.getString("versionLoggerListener.os.arch", System.getProperty("os.arch")));
        log.info(sm.getString("versionLoggerListener.java.home", System.getProperty("java.home")));
        log.info(sm.getString("versionLoggerListener.vm.version", System.getProperty("java.runtime.version")));
        log.info(sm.getString("versionLoggerListener.vm.vendor", System.getProperty("java.vm.vendor")));
        log.info(sm.getString("versionLoggerListener.catalina.base", System.getProperty(Constants.CATALINA_BASE_PROP)));
        log.info(sm.getString("versionLoggerListener.catalina.home", System.getProperty(Constants.CATALINA_HOME_PROP)));

        if (logArgs) {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                log.info(sm.getString("versionLoggerListener.arg", arg));
            }
        }

        if (logEnv) {
            SortedMap<String,String> sortedMap = new TreeMap<>(System.getenv());
            for (Map.Entry<String,String> e : sortedMap.entrySet()) {
                log.info(sm.getString("versionLoggerListener.env", e.getKey(), e.getValue()));
            }
        }

        if (logProps) {
            SortedMap<String,String> sortedMap = new TreeMap<>();
            for (Map.Entry<Object,Object> e : System.getProperties().entrySet()) {
                sortedMap.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
            for (Map.Entry<String,String> e : sortedMap.entrySet()) {
                log.info(sm.getString("versionLoggerListener.prop", e.getKey(), e.getValue()));
            }
        }
    }
}

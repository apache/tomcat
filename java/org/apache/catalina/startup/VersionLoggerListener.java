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
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.ServerInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Logs version information on startup.
 */
public class VersionLoggerListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(VersionLoggerListener.class);

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private boolean logArgs = true;
    private boolean logEnv = false;


    public boolean getLogArgs() {
        return logArgs;
    }


    public void setLogArgs(boolean logArgs) {
        this.logArgs = logArgs;
    }


    public boolean getLogEnv() {
        return logEnv;
    }


    public void setLogEnv(boolean logEnv) {
        this.logEnv = logEnv;
    }


    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        if (Lifecycle.BEFORE_INIT_EVENT.equals(event.getType())) {
            log();
        }
    }


    private void log() {
        log.info(sm.getString("versionLoggerListener.serverInfo.server.version",
                ServerInfo.getServerInfo()));
        log.info(sm.getString("versionLoggerListener.serverInfo.server.built",
                ServerInfo.getServerBuilt()));
        log.info(sm.getString("versionLoggerListener.serverInfo.server.number",
                ServerInfo.getServerNumber()));
        log.info(sm.getString("versionLoggerListener.os.name",
                System.getProperty("os.name")));
        log.info(sm.getString("versionLoggerListener.os.version",
                System.getProperty("os.version")));
        log.info(sm.getString("versionLoggerListener.os.arch",
                System.getProperty("os.arch")));
        log.info(sm.getString("versionLoggerListener.java.home",
                System.getProperty("java.home")));
        log.info(sm.getString("versionLoggerListener.vm.version",
                System.getProperty("java.runtime.version")));
        log.info(sm.getString("versionLoggerListener.vm.vendor",
                System.getProperty("java.vm.vendor")));
        log.info(sm.getString("versionLoggerListener.catalina.base",
                System.getProperty("catalina.base")));
        log.info(sm.getString("versionLoggerListener.catalina.home",
                System.getProperty("catalina.home")));

        if (logArgs) {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                log.info(sm.getString("versionLoggerListener.arg", arg));
            }
        }

        if (logEnv) {
            Map<String,String> envs = System.getenv();
            SortedSet<String> keys = new TreeSet<>(envs.keySet());
            for (String key : keys) {
                log.info(sm.getString("versionLoggerListener.env", key, envs.get(key)));
            }
        }
    }
}

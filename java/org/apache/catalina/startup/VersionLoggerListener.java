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


    private boolean logEnv = true;


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
        log.info(sm.getString("versionLoggerListener.serverInfo.os.name",
                System.getProperty("os.name")));
        log.info(sm.getString("versionLoggerListener.serverInfo.os.version",
                System.getProperty("os.version")));
        log.info(sm.getString("versionLoggerListener.serverInfo.os.arch",
                System.getProperty("os.arch")));
        log.info(sm.getString("versionLoggerListener.serverInfo.vm.version",
                System.getProperty("java.runtime.version")));
        log.info(sm.getString("versionLoggerListener.serverInfo.vm.vendor",
                System.getProperty("java.vm.vendor")));

        if (logEnv) {
            log.info(sm.getString("versionLoggerListener.env.catalina.base",
                    System.getenv("CATALINA_BASE")));
            log.info(sm.getString("versionLoggerListener.env.catalina.home",
                    System.getenv("CATALINA_HOME")));
            log.info(sm.getString("versionLoggerListener.env.catalina.tmpdir",
                    System.getenv("CATALINA_TMPDIR")));
            log.info(sm.getString("versionLoggerListener.env.java.home",
                    System.getenv("JAVA_HOME")));
            log.info(sm.getString("versionLoggerListener.env.jre.home",
                    System.getenv("JRE_HOME")));
            log.info(sm.getString("versionLoggerListener.env.runjava",
                    System.getenv("_RUNJAVA")));
            log.info(sm.getString("versionLoggerListener.env.java.opts",
                    System.getenv("JAVA_OPTS")));
            log.info(sm.getString("versionLoggerListener.env.catalina.opts",
                    System.getenv("CATALINA_OPTS")));
            log.info(sm.getString("versionLoggerListener.env.java.endorsed",
                    System.getenv("JAVA_ENDORSED_DIRS")));
            log.info(sm.getString("versionLoggerListener.env.classpath",
                    System.getenv("CLASSPATH")));
        }
    }
}
